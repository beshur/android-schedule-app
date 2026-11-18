package net.buznik.schedule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        int delay = intent.getIntExtra("in_seconds", 0);
        if (delay > 0) {
            WakeService.schedule(ctx, System.currentTimeMillis() + delay * 1000L);
        } else if (WakeService.instance != null) {
            WakeService.instance.run();
        }
    }
}
