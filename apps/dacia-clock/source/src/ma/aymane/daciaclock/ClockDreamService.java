package ma.aymane.daciaclock;

import android.service.dreams.DreamService;

/** Android screensaver ("Daydream") entry, unchanged from v16. Not used by default. */
public class ClockDreamService extends DreamService {
    private ClockView web;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        web = new ClockView(this, "file:///android_asset/clock.html#idle");
        setContentView(web);
    }

    @Override
    public void onDetachedFromWindow() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDetachedFromWindow();
    }
}
