package ma.aymane.daciaclock;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

/**
 * Which app is in front, from UsageStatsManager events (needs usage access:
 *   adb shell appops set ma.aymane.daciaclock GET_USAGE_STATS allow).
 * Reads events incrementally so each 15 s tick stays cheap on the Cortex-A7.
 * Returns null when unknown (no permission / no events yet) - callers must treat
 * null as "an app might be open" and NOT open the clock.
 */
public final class Foreground {
    private static final int MOVE_TO_FOREGROUND = 1; // UsageEvents.Event.MOVE_TO_FOREGROUND
    private static final long FIRST_LOOKBACK_MS = 24L * 60 * 60 * 1000;

    private static String lastPkg = null;
    private static long lastQuery = 0;
    private static boolean warned = false;

    private Foreground() {}

    public static synchronized String current(Context ctx) {
        try {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService("usagestats");
            if (usm == null) return null;
            long now = System.currentTimeMillis();
            long from = (lastQuery == 0 || now < lastQuery) ? now - FIRST_LOOKBACK_MS : lastQuery - 2000;
            UsageEvents ev = usm.queryEvents(from, now);
            UsageEvents.Event e = new UsageEvents.Event();
            while (ev != null && ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() == MOVE_TO_FOREGROUND) lastPkg = e.getPackageName();
            }
            lastQuery = now;
            if (lastPkg == null && !warned) {
                warned = true;
                Settings.d("app au premier plan inconnue (acces aux statistiques d'usage absent ?) - l'horloge ne s'ouvrira pas seule");
            }
            return lastPkg;
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                Settings.d("Foreground: " + t);
            }
            return null;
        }
    }
}
