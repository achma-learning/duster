// DECOMPILED from DaciaClock.apk v16 (androguard) - approximate, not original source
package ma.aymane.daciaclock;
public class BootReceiver extends android.content.BroadcastReceiver {

    public BootReceiver()
    {
        return;
    }

    public void onReceive(android.content.Context p9, android.content.Intent p10)
    {
        android.content.Intent v0_0 = p10.getAction();
        if ((v0_0 != null) && (("android.intent.action.BOOT_COMPLETED".equals(v0_0)) || ("android.intent.action.LOCKED_BOOT_COMPLETED".equals(v0_0)))) {
            ma.aymane.daciaclock.Settings.load();
            ma.aymane.daciaclock.Settings.d("BOOT_COMPLETED recu");
            android.content.Intent v0_3 = new android.content.Intent(p9, ma.aymane.daciaclock.ClockActivity);
            v0_3.addFlags(268435456);
            ((android.app.AlarmManager) p9.getSystemService("alarm")).set(2, (android.os.SystemClock.elapsedRealtime() + ma.aymane.daciaclock.Settings.bootMs), android.app.PendingIntent.getActivity(p9, 0, v0_3, 134217728));
            p9.startService(new android.content.Intent(p9, ma.aymane.daciaclock.InactivityService));
        }
        return;
    }
}
