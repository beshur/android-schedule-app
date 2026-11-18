package net.buznik.schedule

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import java.util.Calendar

class WakeService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        instance = this
        schedule(this, nextRun(this))
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        handler.removeCallbacksAndMessages(null)
        return super.onUnbind(intent)
    }

    fun run() {
        @Suppress("DEPRECATION")
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "schedule:wake")
            .acquire(15_000)
        // A Home key press is what makes the box send HDMI-CEC "Text View On" to the TV.
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 2_000)
        handler.postDelayed({
            launchIntent(this, prefs(this).getString("app", "").orEmpty())
                ?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }, 5_000)
        schedule(this, nextRun(this))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    companion object {
        var instance: WakeService? = null

        fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences("schedule", Context.MODE_PRIVATE)

        fun launchIntent(ctx: Context, app: String): Intent? {
            if (app.isEmpty()) return null
            val pm = ctx.packageManager
            return pm.getLaunchIntentForPackage(app) ?: pm.getLeanbackLaunchIntentForPackage(app)
        }

        fun nextRun(ctx: Context): Long {
            val p = prefs(ctx)
            val c = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, p.getInt("hour", 8))
                set(Calendar.MINUTE, p.getInt("minute", 0))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
            return c.timeInMillis
        }

        fun schedule(ctx: Context, at: Long) {
            val pi = PendingIntent.getBroadcast(
                ctx, 0, Intent(ctx, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            ctx.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(at, null), pi)
        }
    }
}
