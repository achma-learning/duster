// DECOMPILED from DaciaClock.apk v16 (androguard) - approximate, not original source
package ma.aymane.daciaclock;
public final class Config {
    public static final long BOOT_DELAY_MS = 20000;
    public static final long IDLE_MS = 180000;
    public static final String[] REVERSE_PROPS = None;
    public static final boolean USE_OVERLAY_FALLBACK = True;

    static Config()
    {
        String[] v0_1 = new String[4];
        v0_1[0] = "persist.sys.reverse";
        v0_1[1] = "sys.backcar";
        v0_1[2] = "persist.sys.backcar";
        v0_1[3] = "sys.reverse.state";
        ma.aymane.daciaclock.Config.REVERSE_PROPS = v0_1;
        return;
    }

    private Config()
    {
        return;
    }
}
