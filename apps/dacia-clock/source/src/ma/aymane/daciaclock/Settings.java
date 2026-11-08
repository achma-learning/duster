package ma.aymane.daciaclock;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;

/**
 * Runtime settings, read once per process from /sdcard/daciaclock.txt (optional).
 *
 *   idle=180                      seconds without touch on the home screen before the clock opens (0 = never)
 *   boot=20                       seconds after BOOT_COMPLETED before the clock opens
 *   log=1                         logcat tag "DaciaClock"
 *   home=com.android.launcher     comma-separated packages that count as "home" (launcher + app drawer)
 */
public final class Settings {
    public static final String TAG = "DaciaClock";

    public static volatile long idleMs = 180000L;
    public static volatile long bootMs = 20000L;
    public static volatile boolean log = true;
    public static final Set<String> homePackages = new HashSet<String>();

    private static boolean loaded = false;

    static {
        homePackages.add("com.android.launcher");
    }

    private Settings() {}

    public static void d(String msg) {
        if (log) Log.i(TAG, msg);
    }

    public static boolean isHome(String pkg) {
        return pkg != null && homePackages.contains(pkg);
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        File f = new File(Environment.getExternalStorageDirectory(), "daciaclock.txt");
        if (!f.exists()) {
            d("pas de daciaclock.txt, valeurs par defaut (idle=" + idleMs / 1000 + "s)");
            return;
        }
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(f));
            String line;
            while ((line = in.readLine()) != null) {
                int hash = line.indexOf('#');
                if (hash >= 0) line = line.substring(0, hash);
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim().toLowerCase();
                String val = line.substring(eq + 1).trim();
                try {
                    if ("idle".equals(key)) {
                        idleMs = Long.parseLong(val) * 1000L;
                    } else if ("boot".equals(key)) {
                        bootMs = Long.parseLong(val) * 1000L;
                    } else if ("log".equals(key)) {
                        log = !"0".equals(val);
                    } else if ("home".equals(key)) {
                        homePackages.clear();
                        for (String p : val.split(",")) {
                            if (p.trim().length() > 0) homePackages.add(p.trim());
                        }
                    }
                } catch (NumberFormatException e) {
                    Log.w(TAG, "valeur invalide pour " + key + ": " + val);
                }
            }
            d("daciaclock.txt lu : idle=" + idleMs / 1000 + "s boot=" + bootMs / 1000 + "s home=" + homePackages);
        } catch (Exception e) {
            Log.w(TAG, "lecture daciaclock.txt impossible : " + e);
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) {}
            }
        }
    }
}
