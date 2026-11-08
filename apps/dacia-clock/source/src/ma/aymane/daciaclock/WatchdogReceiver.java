package ma.aymane.daciaclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Repeating alarm target: restarts InactivityService if the ROM killed it. */
public class WatchdogReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            ctx.startService(new Intent(ctx, InactivityService.class));
        } catch (Throwable ignored) {}
    }
}
