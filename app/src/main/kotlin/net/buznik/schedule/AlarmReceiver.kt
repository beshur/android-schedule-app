package net.buznik.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val p = WakeService.prefs(ctx)
        val config = listOf("hour", "minute", "app").any(intent::hasExtra)
        if (config) {
            val hour = intent.intExtra("hour", p.getInt("hour", 8))
            val minute = intent.intExtra("minute", p.getInt("minute", 0))
            val app = if (intent.hasExtra("app")) intent.getStringExtra("app") else p.getString("app", "")
            if (hour !in 0..23 || minute !in 0..59 || app == null ||
                (app.isNotEmpty() && WakeService.launchIntent(ctx, app) == null)
            ) {
                resultCode = 1
                return
            }
            p.edit().putInt("hour", hour).putInt("minute", minute).putString("app", app).apply()
            WakeService.schedule(ctx, WakeService.nextRun(ctx))
        }
        if (config || intent.hasExtra("show")) {
            resultData = "%02d:%02d app=%s".format(p.getInt("hour", 8), p.getInt("minute", 0), p.getString("app", ""))
            return
        }
        val delay = intent.getIntExtra("in_seconds", 0)
        if (delay > 0) WakeService.schedule(ctx, System.currentTimeMillis() + delay * 1000L)
        else WakeService.instance?.run()
    }

    @Suppress("DEPRECATION")
    private fun Intent.intExtra(key: String, fallback: Int): Int =
        if (!hasExtra(key)) fallback else extras?.get(key) as? Int ?: -1
}
