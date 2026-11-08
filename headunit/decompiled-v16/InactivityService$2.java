// DECOMPILED from DaciaClock.apk v16 (androguard) - approximate, not original source
package ma.aymane.daciaclock;
 class InactivityService$2 implements android.view.View$OnTouchListener {
    final synthetic ma.aymane.daciaclock.InactivityService this$0;

    InactivityService$2(ma.aymane.daciaclock.InactivityService p1)
    {
        this.this$0 = p1;
        return;
    }

    public boolean onTouch(android.view.View p3, android.view.MotionEvent p4)
    {
        if (p4.getAction() == 4) {
            ma.aymane.daciaclock.InactivityService.poke();
        }
        return 0;
    }
}
