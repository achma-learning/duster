// DECOMPILED from DaciaClock.apk v16 (androguard) - approximate, not original source
package ma.aymane.daciaclock;
 class InactivityService$1 implements java.lang.Runnable {
    final synthetic ma.aymane.daciaclock.InactivityService this$0;

    InactivityService$1(ma.aymane.daciaclock.InactivityService p1)
    {
        this.this$0 = p1;
        return;
    }

    public void run()
    {
        try {
            ma.aymane.daciaclock.InactivityService.access$000(this.this$0);
        } catch (android.os.Handler v0_4) {
            ma.aymane.daciaclock.Settings.d(new StringBuilder().append("tick: ").append(v0_4).toString());
        }
        ma.aymane.daciaclock.InactivityService.access$100(this.this$0).postDelayed(this, 15000);
        return;
    }
}
