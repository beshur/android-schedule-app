package net.buznik.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
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
        if (next == null) alarms(ctx).cancel(pending(ctx, null)) else set(ctx, next.millis(), null)
    }

    fun set(ctx: Context, at: Long, id: String?) =
        alarms(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(at, null), pending(ctx, id))

    fun onAlarm(ctx: Context, id: String?) {
        val now = LocalDateTime.now()
        val list = Store.load(ctx)
        val due = if (id != null) list.find { it.id == id }
        else list.filter { it.enabled }.firstOrNull { s ->
            s.next(now.minusMinutes(10))?.let { it <= now.plusMinutes(1) } == true
        }
        due?.let { WakeService.instance?.run(it.app) }
        // Base the next alarm past this minute so an alarm delivered a little early can't repeat the same run.
        reschedule(ctx, now.plusMinutes(1))
    }

    private fun pending(ctx: Context, id: String?) = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, AlarmReceiver::class.java).setAction(ALARM).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun alarms(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    private fun LocalDateTime.millis() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
