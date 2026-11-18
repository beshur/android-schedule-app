package net.buznik.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object Store {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("schedule", Context.MODE_PRIVATE)

    fun load(ctx: Context): List<Schedule> {
        val a = JSONArray(prefs(ctx).getString("schedules", "[]"))
        return (0 until a.length()).map { Schedule.fromJson(a.getJSONObject(it)) }
    }

    fun save(ctx: Context, list: List<Schedule>) {
        prefs(ctx).edit().putString("schedules", JSONArray(list.map { it.toJson() }).toString()).commit()
        Scheduler.reschedule(ctx)
    }
}

object Scheduler {
    const val ALARM = "net.buznik.schedule.ALARM"

    fun reschedule(ctx: Context, after: LocalDateTime = LocalDateTime.now()) {
        val next = Store.load(ctx).filter { it.enabled }.mapNotNull { it.next(after) }.minOrNull()
        val pi = pending(ctx, 0, Intent().apply { next?.let { putExtra("at", it.millis()) } })
        if (next == null) alarms(ctx).cancel(pi) else alarms(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(next.millis(), null), pi)
    }

    fun test(ctx: Context, at: Long, id: String) =
        alarms(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(at, null), pending(ctx, 1, Intent().putExtra("id", id)))

    fun onAlarm(ctx: Context, intent: Intent) {
        val list = Store.load(ctx)
        val id = intent.getStringExtra("id")
        if (id != null) {
            list.find { it.id == id }?.let(::run)
            return
        }
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(intent.getLongExtra("at", 0)), ZoneId.systemDefault())
        // Several schedules at the same minute: the first one in the list wins.
        list.firstOrNull { it.enabled && it.next(at.minusSeconds(1)) == at }?.let { run(it) }
        reschedule(ctx, maxOf(at, LocalDateTime.now()))
    }

    private fun run(s: Schedule) = WakeService.instance?.run(s.app)

    private fun pending(ctx: Context, code: Int, extras: Intent) = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, AlarmReceiver::class.java).setAction(ALARM).putExtras(extras),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun alarms(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    private fun LocalDateTime.millis() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
