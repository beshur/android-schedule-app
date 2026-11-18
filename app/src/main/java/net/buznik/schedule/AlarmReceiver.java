package net.buznik.schedule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        SharedPreferences p = WakeService.prefs(ctx);
        boolean config = intent.hasExtra("hour") || intent.hasExtra("minute") || intent.hasExtra("app");
        if (config) {
            int hour = intExtra(intent, "hour", p.getInt("hour", 8));
            int minute = intExtra(intent, "minute", p.getInt("minute", 0));
            String app = intent.hasExtra("app") ? intent.getStringExtra("app") : p.getString("app", "");
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59 || app == null
                    || (!app.isEmpty() && WakeService.launchIntent(ctx, app) == null)) {
                setResultCode(1);
                return;
            }
            p.edit().putInt("hour", hour).putInt("minute", minute).putString("app", app).apply();
            WakeService.schedule(ctx, WakeService.nextRun(ctx));
        }
        if (config || intent.hasExtra("show")) {
            setResultData(String.format("%02d:%02d app=%s",
                    p.getInt("hour", 8), p.getInt("minute", 0), p.getString("app", "")));
            return;
        }
        int delay = intent.getIntExtra("in_seconds", 0);
        if (delay > 0) {
            WakeService.schedule(ctx, System.currentTimeMillis() + delay * 1000L);
        } else if (WakeService.instance != null) {
            WakeService.instance.run();
        }
    }

    private static int intExtra(Intent intent, String key, int fallback) {
        if (!intent.hasExtra(key)) return fallback;
        Object v = intent.getExtras().get(key);
        return v instanceof Integer ? (Integer) v : -1;
    }
}
