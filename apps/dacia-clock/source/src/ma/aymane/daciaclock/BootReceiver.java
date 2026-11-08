package ma.aymane.daciaclock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/** Real reboot: open the clock after Settings.bootMs and start the idle service (as v16). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction();
        if (!"android.intent.action.BOOT_COMPLETED".equals(a)
                && !"android.intent.action.LOCKED_BOOT_COMPLETED".equals(a)) return;
        Settings.load();
        Settings.d("BOOT_COMPLETED recu");
        Intent clock = new Intent(ctx, ClockActivity.class);
        clock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + Settings.bootMs,
                PendingIntent.getActivity(ctx, 0, clock, PendingIntent.FLAG_UPDATE_CURRENT));
        ctx.startService(new Intent(ctx, InactivityService.class));
    }
}
