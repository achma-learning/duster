// DECOMPILED from DaciaClock.apk v16 (androguard) - approximate, not original source
package ma.aymane.daciaclock;
public class ClockView extends android.webkit.WebView {

    public ClockView(android.content.Context p1, String p2)
    {
        super(p1);
        super.init(p2);
        return;
    }

    private void init(String p5)
    {
        this.setBackgroundColor(android.graphics.Color.parseColor("#0C0E0F"));
        this.setVerticalScrollBarEnabled(0);
        this.setHorizontalScrollBarEnabled(0);
        this.setOverScrollMode(2);
        android.webkit.WebSettings v0_0 = this.getSettings();
        v0_0.setJavaScriptEnabled(1);
        v0_0.setAllowFileAccess(1);
        v0_0.setDomStorageEnabled(0);
        v0_0.setDatabaseEnabled(0);
        v0_0.setLoadsImagesAutomatically(1);
        v0_0.setUseWideViewPort(0);
        v0_0.setSupportZoom(0);
        v0_0.setBuiltInZoomControls(0);
        v0_0.setCacheMode(2);
        this.loadUrl(p5);
        return;
    }
}
