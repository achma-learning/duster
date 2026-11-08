package ma.aymane.daciaclock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * v17 idle logic: the clock opens after Settings.idleMs without touch ONLY when the
 * stock launcher (home screen / app drawer) is in front. Any other app in front - or an
 * unknown front app - restarts the countdown, so the clock never covers Android Auto,
 * navigation, video or anything else. Never opens in reverse; closes the clock when
 * reverse is engaged.
 */
public class InactivityService extends Service {
    private static final long TICK_MS = 15000L;
    private static final long WATCHDOG_MS = 60000L;

    private static volatile long lastActivity = SystemClock.elapsedRealtime();

    private final Handler h = new Handler();
    private WindowManager wm;
    private View probe;
    private boolean probeOk = false;
    private String lastFg = "";
    private boolean loggedExtras = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                check();
            } catch (Throwable t) {
                Settings.d("tick: " + t);
            }
            h.postDelayed(this, TICK_MS);
        }
    };

    private final BroadcastReceiver backcar = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Bundle b = i.getExtras();
            int s = -1;
            StringBuilder dump = new StringBuilder();
            if (b != null) {
                for (String k : b.keySet()) {
                    Object v = b.get(k);
                    dump.append(k).append('=').append(v).append(' ');
                    String lk = k.toLowerCase();
                    if (s == -1 && (lk.contains("back") || lk.contains("state") || lk.contains("reverse"))) {
                        if (v instanceof Integer) s = ((Integer) v) != 0 ? 1 : 0;
                        else if (v instanceof Boolean) s = ((Boolean) v) ? 1 : 0;
                        else if (v != null) s = ("1".equals(v.toString()) || "true".equalsIgnoreCase(v.toString())) ? 1 : 0;
                    }
                }
            }
            if (!loggedExtras || s == 1) {
                loggedExtras = true;
                Settings.d("ACTION_BACKCAR extras: " + dump.toString().trim());
            }
            Reverse.broadcastState = s;
            Settings.d("marche arriere (broadcast) = " + s);
            if (s == 1) closeClock("marche arriere");
            poke();
        }
    };

    public static void poke() {
        lastActivity = SystemClock.elapsedRealtime();
    }

    private void closeClock(String why) {
        if (ClockActivity.isForeground) {
            Settings.d(why + " -> fermeture de l'horloge");
            sendBroadcast(new Intent(ClockActivity.ACTION_CLOSE).setPackage(getPackageName()));
        }
    }

    private void check() {
        if (Reverse.engaged(this)) {
            closeClock("marche arriere");
            poke();
            return;
        }
        long idle = Settings.idleMs;
        if (idle <= 0) return;

        String fg = Foreground.current(this);
        String fgName = fg == null ? "?" : fg;
        if (!fgName.equals(lastFg)) {
            Settings.d("premier plan : " + fgName);
            lastFg = fgName;
        }
        if (ClockActivity.isForeground || !Settings.isHome(fg)) {
            // An app (or the clock itself, or something unknown) is in front: stay out of the way.
            poke();
            return;
        }
        long since = SystemClock.elapsedRealtime() - lastActivity;
        if (since < idle) return;

        Settings.d("accueil inactif depuis " + since / 1000 + "s -> ouverture de l'horloge");
        Intent i = new Intent(this, ClockActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(i);
        } catch (Throwable t) {
            Settings.d("startActivity: " + t);
        }
        poke();
    }

    private void watchdog(boolean on) {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(this, 7, new Intent(this, WatchdogReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        if (on) {
            am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + WATCHDOG_MS,
                    WATCHDOG_MS, pi);
        } else {
            am.cancel(pi);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Settings.load();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        // Invisible 1x1 touch probe: every touch anywhere arrives as ACTION_OUTSIDE.
        probe = new View(this);
        probe.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_OUTSIDE) poke();
                return false;
            }
        });
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(1, 1,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        try {
            wm.addView(probe, lp);
            probeOk = true;
            Settings.d("sonde tactile posee");
        } catch (Throwable t) {
            probeOk = false;
            Settings.d("SONDE REFUSEE (" + t + ")");
        }

        registerReceiver(backcar, new IntentFilter(Reverse.ACTION_BACKCAR));

        poke();
        h.postDelayed(tick, TICK_MS);
        watchdog(true);
        Settings.d("v17 service demarre, idle=" + Settings.idleMs / 1000 + "s, sonde=" + probeOk
                + ", accueil=" + Settings.homePackages);
    }

    @Override
    public void onDestroy() {
        h.removeCallbacks(tick);
        try { unregisterReceiver(backcar); } catch (Throwable ignored) {}
        if (probe != null) {
            try { wm.removeView(probe); } catch (Throwable ignored) {}
        }
        Settings.d("service arrete");
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
