// DECOMPILED from DaciaClock.apk v16 (androguard) - approximate, not original source
package ma.aymane.daciaclock;
public final class Settings {
    public static final String TAG = "DaciaClock";
    public static long bootMs;
    public static long idleMs;
    private static boolean loaded;
    public static boolean log;

    static Settings()
    {
        ma.aymane.daciaclock.Settings.idleMs = 180000;
        ma.aymane.daciaclock.Settings.bootMs = 20000;
        ma.aymane.daciaclock.Settings.log = 1;
        ma.aymane.daciaclock.Settings.loaded = 0;
        return;
    }

    private Settings()
    {
        return;
    }

    public static void d(String p1)
    {
        if (ma.aymane.daciaclock.Settings.log) {
            android.util.Log.i("DaciaClock", p1);
        }
        return;
    }

    public static declared_synchronized void load()
    {
        try {
            if (!ma.aymane.daciaclock.Settings.loaded) {
                ma.aymane.daciaclock.Settings.loaded = 1;
                String v1_10 = 0;
                try {
                    String v0_8 = new java.io.File(android.os.Environment.getExternalStorageDirectory(), "daciaclock.txt");
                } catch (String v0_10) {
                    try {
                        android.util.Log.w("DaciaClock", new StringBuilder().append("lecture daciaclock.txt impossible : ").append(v0_10).toString());
                    } catch (String v0_2) {
                        int v2_1 = v1_10;
                        if (v2_1 != 0) {
                            try {
                                v2_1.close();
                            } catch (String v1) {
                            }
                        }
                        throw v0_2;
                    }
                    if (v1_10 != null) {
                        try {
                            v1_10.close();
                        } catch (String v0) {
                        }
                    }
                } catch (String v0_2) {
                    v2_1 = 0;
                }
                if (v0_8.exists()) {
                    v2_1 = new java.io.BufferedReader(new java.io.FileReader(v0_8));
                    try {
                        do {
                            String v0_11 = v2_1.readLine();
                        } while(String v1_6 <= null);
                        String v6_11 = v0_11.substring(0, v1_6).trim().toLowerCase();
                        String v0_23 = v0_11.substring((v1_6 + 1)).trim();
                        if (!"idle".equals(v6_11)) {
                            if (!"boot".equals(v6_11)) {
                                if ("log".equals(v6_11)) {
                                    String v0_26;
                                    if ("0".equals(v0_23)) {
                                        v0_26 = 0;
                                    } else {
                                        v0_26 = 1;
                                    }
                                    ma.aymane.daciaclock.Settings.log = v0_26;
                                }
                            } else {
                                ma.aymane.daciaclock.Settings.bootMs = (Long.parseLong(v0_23) * 1000);
                            }
                        } else {
                            ma.aymane.daciaclock.Settings.idleMs = (Long.parseLong(v0_23) * 1000);
                        }
                    } catch (String v0_10) {
                        v1_10 = v2_1;
                    } catch (String v0_2) {
                    }
                    if (v0_11 == null) {
                        ma.aymane.daciaclock.Settings.d(new StringBuilder().append("daciaclock.txt lu : idle=").append((ma.aymane.daciaclock.Settings.idleMs / 1000)).append("s boot=").append((ma.aymane.daciaclock.Settings.bootMs / 1000)).append("s").toString());
                        if (v2_1 != 0) {
                            try {
                                v2_1.close();
                            } catch (String v0) {
                            }
                        }
                    } else {
                        String v1_4 = v0_11.indexOf(35);
                        if (v1_4 >= null) {
                            v0_11 = v0_11.substring(0, v1_4);
                        }
                        v1_6 = v0_11.indexOf(61);
                        if (v1_6 > null) {
                        }
                    }
                } else {
                    ma.aymane.daciaclock.Settings.d("pas de daciaclock.txt, valeurs par defaut");
                    if (0 != 0) {
                        try {
                            0.close();
                        } catch (String v0) {
                        }
                    }
                }
            }
        } catch (String v0_9) {
            throw v0_9;
        }
        return;
    }
}
