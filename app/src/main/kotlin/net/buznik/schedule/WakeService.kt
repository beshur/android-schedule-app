package net.buznik.schedule

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent

class WakeService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        instance = this
        Scheduler.reschedule(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        handler.removeCallbacksAndMessages(null)
        return super.onUnbind(intent)
    }

    fun run(app: String) {
        @Suppress("DEPRECATION")
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "schedule:wake")
            .acquire(15_000)
        // A Home key press is what makes the box send HDMI-CEC "Text View On" to the TV.
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 2_000)
        handler.postDelayed({
            launchIntent(this, app)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }, 5_000)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    companion object {
        var instance: WakeService? = null

        fun launchIntent(ctx: Context, app: String): Intent? {
            if (app.isEmpty()) return null
            val pm = ctx.packageManager
            return pm.getLaunchIntentForPackage(app) ?: pm.getLeanbackLaunchIntentForPackage(app)
        }
    }
}
