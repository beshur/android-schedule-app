package net.buznik.schedule;

import android.accessibilityservice.AccessibilityService;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.Calendar;

public class WakeService extends AccessibilityService {
    static WakeService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        instance = this;
        schedule(this, nextRun(this));
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        handler.removeCallbacksAndMessages(null);
        return super.onUnbind(intent);
    }

    void run() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        @SuppressWarnings("deprecation")
        PowerManager.WakeLock lock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "schedule:wake");
        lock.acquire(15_000);
        // A Home key press is what makes the box send HDMI-CEC "Text View On" to the TV.
        handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_HOME), 2_000);
        handler.postDelayed(this::launchTarget, 5_000);
        schedule(this, nextRun(this));
    }

    private void launchTarget() {
        Intent i = launchIntent(this, prefs(this).getString("app", ""));
        if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    static Intent launchIntent(Context ctx, String app) {
        if (app.isEmpty()) return null;
        PackageManager pm = ctx.getPackageManager();
        Intent i = pm.getLaunchIntentForPackage(app);
        return i != null ? i : pm.getLeanbackLaunchIntentForPackage(app);
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("schedule", MODE_PRIVATE);
    }

    static long nextRun(Context ctx) {
        SharedPreferences p = prefs(ctx);
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, p.getInt("hour", 8));
        c.set(Calendar.MINUTE, p.getInt("minute", 0));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }

    static void schedule(Context ctx, long at) {
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0,
                new Intent(ctx, AlarmReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(ALARM_SERVICE);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, null), pi);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
