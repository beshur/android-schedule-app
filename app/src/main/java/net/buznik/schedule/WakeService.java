package net.buznik.schedule;

import android.accessibilityservice.AccessibilityService;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.Calendar;

public class WakeService extends AccessibilityService {
    static final int HOUR = 8;
    static final int MINUTE = 0;
    static final String TARGET_PACKAGE = "com.beshur.daybook.tv";

    static WakeService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        instance = this;
        schedule(this, nextRun());
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
        schedule(this, nextRun());
    }

    private void launchTarget() {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLaunchIntentForPackage(TARGET_PACKAGE);
        if (i == null) i = pm.getLeanbackLaunchIntentForPackage(TARGET_PACKAGE);
        if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    static long nextRun() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, HOUR);
        c.set(Calendar.MINUTE, MINUTE);
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
