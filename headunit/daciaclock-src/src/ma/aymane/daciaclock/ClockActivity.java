package ma.aymane.daciaclock;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * The clock (WebView, assets/clock.html). v17: a tap anywhere sends it to the back,
 * so you are returned to where you were; the service also sends ACTION_CLOSE when
 * reverse gear is engaged.
 */
public class ClockActivity extends Activity {
    public static final String ACTION_CLOSE = "ma.aymane.daciaclock.CLOSE";
    public static volatile boolean isForeground = false;

    private ClockView web;

    private final BroadcastReceiver closer = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            hide();
        }
    };

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void hide() {
        // Back to the previous screen without destroying the WebView (fast to show again).
        moveTaskToBack(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new ClockView(this, "file:///android_asset/clock.html");
        setContentView(web);
        hideSystemUi();
        Settings.load();
        startService(new Intent(this, InactivityService.class));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // Tap anywhere: leave the clock. Consume the gesture so the page never sees it.
        if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
            Settings.d("appui -> retour");
            hide();
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isForeground = true;
        registerReceiver(closer, new IntentFilter(ACTION_CLOSE));
        if (Reverse.engaged(this)) hide();
    }

    @Override
    protected void onPause() {
        isForeground = false;
        try { unregisterReceiver(closer); } catch (Throwable ignored) {}
        InactivityService.poke();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }
}
