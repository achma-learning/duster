// DECOMPILED from DaciaClock.apk v16 (androguard) - approximate, not original source
package ma.aymane.daciaclock;
public class ClockActivity extends android.app.Activity {
    public static volatile boolean isForeground;
    private ma.aymane.daciaclock.ClockView web;

    static ClockActivity()
    {
        ma.aymane.daciaclock.ClockActivity.isForeground = 0;
        return;
    }

    public ClockActivity()
    {
        return;
    }

    private void hideSystemUi()
    {
        this.getWindow().getDecorView().setSystemUiVisibility(5894);
        return;
    }

    protected void onCreate(android.os.Bundle p3)
    {
        super.onCreate(p3);
        this.getWindow().addFlags(128);
        this.web = new ma.aymane.daciaclock.ClockView(this, "file:///android_asset/clock.html");
        this.setContentView(this.web);
        this.hideSystemUi();
        ma.aymane.daciaclock.Settings.load();
        this.startService(new android.content.Intent(this, ma.aymane.daciaclock.InactivityService));
        return;
    }

    protected void onDestroy()
    {
        if (this.web != null) {
            this.web.loadUrl("about:blank");
            this.web.destroy();
            this.web = 0;
        }
        super.onDestroy();
        return;
    }

    protected void onPause()
    {
        ma.aymane.daciaclock.ClockActivity.isForeground = 0;
        ma.aymane.daciaclock.InactivityService.poke();
        super.onPause();
        return;
    }

    protected void onResume()
    {
        super.onResume();
        ma.aymane.daciaclock.ClockActivity.isForeground = 1;
        return;
    }

    public void onWindowFocusChanged(boolean p1)
    {
        super.onWindowFocusChanged(p1);
        if (p1) {
            this.hideSystemUi();
        }
        return;
    }
}
