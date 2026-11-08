package ma.aymane.daciaclock;

import android.content.Context;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Method;

/**
 * Reverse-gear state on the K2001M (NWD ROM). Any source saying "reverse" wins:
 *  1. kernel switch  /sys/devices/soc.0/car_reverse.14/state  ("1" = reverse)
 *  2. last broadcast com.android.action.ACTION_BACKCAR_STATE_CHANGE (fed by InactivityService)
 *  3. Settings.System "mcu_backcar_state"
 *  4. legacy system properties checked by v16 (kept, harmless)
 * See fix-reverse.md: on 2026-11-08 the unit did not report reverse at all yet.
 */
public final class Reverse {
    public static final String ACTION_BACKCAR = "com.android.action.ACTION_BACKCAR_STATE_CHANGE";
    private static final String NODE = "/sys/devices/soc.0/car_reverse.14/state";
    private static final String[] PROPS = {
            "persist.sys.reverse", "sys.backcar", "persist.sys.backcar", "sys.reverse.state"
    };

    /** -1 unknown, 0 not in reverse, 1 reverse (from the ROM broadcast). */
    public static volatile int broadcastState = -1;

    private static Method getProp;

    static {
        try {
            getProp = Class.forName("android.os.SystemProperties").getMethod("get", String.class, String.class);
        } catch (Throwable ignored) {
            getProp = null;
        }
    }

    private Reverse() {}

    public static boolean engaged(Context ctx) {
        if ("1".equals(readNode())) return true;
        if (broadcastState == 1) return true;
        try {
            if ("1".equals(android.provider.Settings.System.getString(ctx.getContentResolver(), "mcu_backcar_state"))) {
                return true;
            }
        } catch (Throwable ignored) {}
        for (String p : PROPS) {
            String v = prop(p);
            if ("1".equals(v) || "true".equalsIgnoreCase(v)) return true;
        }
        return false;
    }

    private static String readNode() {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(NODE));
            String s = in.readLine();
            return s == null ? "" : s.trim();
        } catch (Throwable e) {
            return "";
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static String prop(String key) {
        if (getProp == null) return "";
        try {
            Object v = getProp.invoke(null, key, "");
            return v == null ? "" : v.toString();
        } catch (Throwable e) {
            return "";
        }
    }
}
