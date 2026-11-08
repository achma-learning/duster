// DECOMPILED from DaciaClock.apk v16 (androguard) - approximate, not original source
package ma.aymane.daciaclock;
public final class Reverse {
    private static reflect.Method get;

    static Reverse()
    {
        try {
            Throwable v0_2 = Class.forName("android.os.SystemProperties");
            Class[] v2_1 = new Class[2];
            v2_1[0] = String;
            v2_1[1] = String;
            ma.aymane.daciaclock.Reverse.get = v0_2.getMethod("get", v2_1);
        } catch (Throwable v0) {
        }
        return;
    }

    private Reverse()
    {
        return;
    }

    public static boolean engaged()
    {
        int v0 = 0;
        String[] v2 = ma.aymane.daciaclock.Config.REVERSE_PROPS;
        int v3 = v2.length;
        int v1 = 0;
        while (v1 < v3) {
            boolean v4_2 = ma.aymane.daciaclock.Reverse.prop(v2[v1]);
            if ((!"1".equals(v4_2)) && (!"true".equalsIgnoreCase(v4_2))) {
                v1++;
            } else {
                v0 = 1;
                break;
            }
        }
        return v0;
    }

    private static String prop(String p5)
    {
        String v0_2;
        if (ma.aymane.daciaclock.Reverse.get != null) {
            try {
                Object[] v2_1 = new Object[2];
                v2_1[0] = p5;
                v2_1[1] = "";
                String v0_1 = ma.aymane.daciaclock.Reverse.get.invoke(0, v2_1);
            } catch (String v0) {
                v0_2 = "";
            }
            if (v0_1 != null) {
                v0_2 = v0_1.toString();
            } else {
                v0_2 = "";
            }
        } else {
            v0_2 = "";
        }
        return v0_2;
    }
}
