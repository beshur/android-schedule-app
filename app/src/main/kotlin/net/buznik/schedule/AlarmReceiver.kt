package net.buznik.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** Alarm target, plus the adb interface (see README). Only adb (DUMP permission) or this app can send to it. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Scheduler.ALARM) return Scheduler.onAlarm(ctx, intent)
        try {
            resultData = command(ctx, intent)
        } catch (e: IllegalArgumentException) {
            resultCode = 1
            resultData = e.message
        } catch (e: java.time.DateTimeException) {
            resultCode = 1
            resultData = e.message
        }
    }

    private fun command(ctx: Context, intent: Intent): String {
        val list = Store.load(ctx)
        val id = intent.getStringExtra("id")
        fun find() = requireNotNull(list.find { it.id == id }) { "no schedule with id $id" }
        return when (val action = intent.getStringExtra("action") ?: "list") {
            "list" -> JSONArray(list.map { it.toJson() }).toString()
            "add" -> {
                val s = intent.applyTo(ctx, Schedule(UUID.randomUUID().toString().take(8)))
                Store.save(ctx, list + s)
                s.id
            }
            "update" -> {
                val s = intent.applyTo(ctx, find())
                Store.save(ctx, list.map { if (it.id == s.id) s else it })
                s.id
            }
            "delete" -> {
                Store.save(ctx, list - find())
                "deleted $id"
            }
            "run" -> {
                val s = if (id != null) find() else requireNotNull(list.firstOrNull()) { "no schedules" }
                requireNotNull(WakeService.instance) { "accessibility service is off" }.run(s.app)
                "running ${s.id}"
            }
            "test" -> {
                val seconds = intent.getIntExtra("in_seconds", 120)
                val s = if (id != null) find() else requireNotNull(list.firstOrNull()) { "no schedules" }
                Scheduler.test(ctx, System.currentTimeMillis() + seconds * 1000L, s.id)
                "alarm in ${seconds}s"
            }
            else -> throw IllegalArgumentException("unknown action $action")
        }
    }

    private fun Intent.applyTo(ctx: Context, s: Schedule): Schedule {
        val app = getStringExtra("app") ?: s.app
        require(app.isEmpty() || WakeService.launchIntent(ctx, app) != null) { "app not installed: $app" }
        return s.copy(
            enabled = if (hasExtra("enabled")) getBooleanExtra("enabled", true) else s.enabled,
            app = app,
            date = getStringExtra("date")?.let(LocalDate::parse) ?: s.date,
            time = LocalTime.of(int("hour", s.time.hour), int("minute", s.time.minute)),
            rule = if (hasExtra("rrule")) Rule.parse(getStringExtra("rrule").orEmpty()) else s.rule,
        )
    }

    @Suppress("DEPRECATION")
    private fun Intent.int(key: String, fallback: Int): Int =
        if (!hasExtra(key)) fallback
        else requireNotNull(extras?.get(key) as? Int) { "$key must be passed with --ei" }
}
