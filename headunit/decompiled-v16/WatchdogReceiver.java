// DECOMPILED from DaciaClock.apk v16 (androguard) - approximate, not original source
package ma.aymane.daciaclock;
public class WatchdogReceiver extends android.content.BroadcastReceiver {

    public WatchdogReceiver()
    {
        return;
    }

    public void onReceive(android.content.Context p3, android.content.Intent p4)
    {
        try {
            p3.startService(new android.content.Intent(p3, ma.aymane.daciaclock.InactivityService));
        } catch (Throwable v0) {
        }
        return;
    }
}
