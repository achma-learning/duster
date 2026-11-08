// DECOMPILED from DaciaClock.apk v16 (androguard) - approximate, not original source
package ma.aymane.daciaclock;
public class ClockDreamService extends android.service.dreams.DreamService {
    private ma.aymane.daciaclock.ClockView web;

    public ClockDreamService()
    {
        return;
    }

    public void onAttachedToWindow()
    {
        super.onAttachedToWindow();
        this.setInteractive(0);
        this.setFullscreen(1);
        this.setScreenBright(0);
        this.web = new ma.aymane.daciaclock.ClockView(this, "file:///android_asset/clock.html#idle");
        this.setContentView(this.web);
        return;
    }

    public void onDetachedFromWindow()
    {
        if (this.web != null) {
            this.web.loadUrl("about:blank");
            this.web.destroy();
            this.web = 0;
        }
        super.onDetachedFromWindow();
        return;
    }
}
