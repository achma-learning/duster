package ma.aymane.daciaclock;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;

/** WebView configured exactly as in v16 (offline, no zoom, no cache, dark background). */
public class ClockView extends WebView {
    public ClockView(Context ctx, String url) {
        super(ctx);
        setBackgroundColor(Color.parseColor("#0C0E0F"));
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(true);
        s.setDomStorageEnabled(false);
        s.setDatabaseEnabled(false);
        s.setLoadsImagesAutomatically(true);
        s.setUseWideViewPort(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        loadUrl(url);
    }
}
