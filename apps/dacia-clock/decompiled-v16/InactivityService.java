// DECOMPILED from DaciaClock.apk v16 (androguard) - approximate, not original source
package ma.aymane.daciaclock;
public class InactivityService extends android.app.Service {
    private static final long TICK_MS = 15000;
    private static final long WATCHDOG_MS = 60000;
    private static volatile long lastActivity;
    private final android.os.Handler h;
    private android.view.View probe;
    private boolean probeOk;
    private final Runnable tick;
    private android.view.WindowManager wm;

    static InactivityService()
    {
        ma.aymane.daciaclock.InactivityService.lastActivity = android.os.SystemClock.elapsedRealtime();
        return;
    }

    public InactivityService()
    {
        this.probeOk = 0;
        this.h = new android.os.Handler();
        this.tick = new ma.aymane.daciaclock.InactivityService$1(this);
        return;
    }

    static synthetic void access$000(ma.aymane.daciaclock.InactivityService p0)
    {
        p0.check();
        return;
    }

    static synthetic android.os.Handler access$100(ma.aymane.daciaclock.InactivityService p1)
    {
        return p1.h;
    }

    private void check()
    {
        String v0_0 = ma.aymane.daciaclock.Settings.idleMs;
        if (v0_0 > 0) {
            String v2_5 = (android.os.SystemClock.elapsedRealtime() - ma.aymane.daciaclock.InactivityService.lastActivity);
            if (v2_5 >= v0_0) {
                if (!ma.aymane.daciaclock.ClockActivity.isForeground) {
                    if (!ma.aymane.daciaclock.Reverse.engaged()) {
                        ma.aymane.daciaclock.Settings.d(new StringBuilder().append("inactif depuis ").append((v2_5 / 1000)).append("s -> ouverture de l\'horloge").toString());
                        String v0_10 = new android.content.Intent(this, ma.aymane.daciaclock.ClockActivity);
                        v0_10.addFlags(805306368);
                        try {
                            this.startActivity(v0_10);
                        } catch (String v0_11) {
                            ma.aymane.daciaclock.Settings.d(new StringBuilder().append("startActivity: ").append(v0_11).toString());
                        }
                        ma.aymane.daciaclock.InactivityService.poke();
                    } else {
                        ma.aymane.daciaclock.Settings.d("marche arriere detectee, on ne lance rien");
                        ma.aymane.daciaclock.InactivityService.poke();
                    }
                } else {
                    ma.aymane.daciaclock.InactivityService.poke();
                }
            }
        }
        return;
    }

    public static void poke()
    {
        ma.aymane.daciaclock.InactivityService.lastActivity = android.os.SystemClock.elapsedRealtime();
        return;
    }

    private void watchdog(boolean p8)
    {
        android.app.AlarmManager v0_2 = ((android.app.AlarmManager) this.getSystemService("alarm"));
        android.app.PendingIntent v6 = android.app.PendingIntent.getBroadcast(this, 7, new android.content.Intent(this, ma.aymane.daciaclock.WatchdogReceiver), 134217728);
        if (!p8) {
            v0_2.cancel(v6);
        } else {
            v0_2.setRepeating(2, (android.os.SystemClock.elapsedRealtime() + long v4), 60000, v6);
        }
        return;
    }

    public android.os.IBinder onBind(android.content.Intent p2)
    {
        return 0;
    }

    public void onCreate()
    {
        super.onCreate();
        ma.aymane.daciaclock.Settings.load();
        this.wm = ((android.view.WindowManager) this.getSystemService("window"));
        this.probe = new android.view.View(this);
        this.probe.setOnTouchListener(new ma.aymane.daciaclock.InactivityService$2(this));
        String v0_2 = new android.view.WindowManager$LayoutParams(1, 1, 2003, 262184, -3);
        v0_2.gravity = 8388659;
        try {
            this.wm.addView(this.probe, v0_2);
            this.probeOk = 1;
            ma.aymane.daciaclock.Settings.d("sonde tactile posee");
        } catch (String v0_6) {
            this.probeOk = 0;
            ma.aymane.daciaclock.Settings.d(new StringBuilder().append("SONDE REFUSEE (").append(v0_6).append(") - autorisation de superposition probablement absente ; detection d\'activite degradee").toString());
        }
        ma.aymane.daciaclock.InactivityService.poke();
        this.h.postDelayed(this.tick, 15000);
        this.watchdog(1);
        ma.aymane.daciaclock.Settings.d(new StringBuilder().append("service demarre, idle=").append((ma.aymane.daciaclock.Settings.idleMs / 1000)).append("s, sonde=").append(this.probeOk).toString());
        return;
    }

    public void onDestroy()
    {
        this.h.removeCallbacks(this.tick);
        if (this.probe != null) {
            try {
                this.wm.removeView(this.probe);
            } catch (Throwable v0) {
            }
        }
        ma.aymane.daciaclock.Settings.d("service arrete");
        super.onDestroy();
        return;
    }

    public int onStartCommand(android.content.Intent p2, int p3, int p4)
    {
        return 1;
    }
}
