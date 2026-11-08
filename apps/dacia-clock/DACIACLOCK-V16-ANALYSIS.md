# DaciaClock v16 — what the installed APK actually does

Decompiled from `apk/clock-auto-3min.apk` (versionCode 16, package
`ma.aymane.daciaclock`). Approximate Java is in `decompiled-v16/`. The original
source is NOT in the repo — only the APK.

## Components

| Component | Role |
|---|---|
| `.ClockActivity` | The clock (WebView, `assets/clock.html`). `singleTask`, `FLAG_KEEP_SCREEN_ON`. Starts `InactivityService` in `onCreate`. Sets `isForeground` in onResume/onPause, pokes the idle timer on pause. |
| `.InactivityService` | Idle detector. Every **15 s** tick compares `elapsedRealtime() - lastActivity` to `Settings.idleMs`. If idle, not foreground and **not in reverse** → `startActivity(ClockActivity)` (flags NEW_TASK + SINGLE_TOP). |
| touch probe | A **1×1 px invisible** window, `TYPE_SYSTEM_ALERT (2003)`, flags `NOT_FOCUSABLE \| NOT_TOUCH_MODAL \| WATCH_OUTSIDE_TOUCH`. Every touch anywhere sends it `ACTION_OUTSIDE` → `poke()`. It draws nothing visible, so it does not mask the reverse camera. If the overlay is refused, log line `SONDE REFUSEE` and idle detection degrades (it will fire even while you use the screen). |
| `.WatchdogReceiver` | `AlarmManager.setRepeating(ELAPSED_REALTIME_WAKEUP, 60 s)` restarts the service if killed. |
| `.BootReceiver` | On `BOOT_COMPLETED`: alarm → launch clock after `bootMs` (20 s), and start the service. |
| `.ClockDreamService` | Android Daydream entry (optional path). |
| `Reverse` | Reads system props via reflection: `persist.sys.reverse`, `sys.backcar`, `persist.sys.backcar`, `sys.reverse.state`; "1"/"true" = reverse. **Not yet verified on this unit** — the real property may be another one. |

## Runtime config file — no rebuild needed for the idle delay

`Settings.load()` reads **`/sdcard/daciaclock.txt`** (`Environment.getExternalStorageDirectory()`), once per process:

```
idle=60     # seconds of inactivity before the clock opens (default 180)
boot=20     # seconds after BOOT_COMPLETED before the clock opens (default 20)
log=1       # logcat tag "DaciaClock"
```

After editing: `am force-stop ma.aymane.daciaclock` then `am start -n ma.aymane.daciaclock/.ClockActivity`
(the force-stop puts the app in "stopped" state; the `am start` clears it).

## Why it can silently stop working (ranked hypotheses)

1. **ACC = sleep, not reboot.** If `/proc/uptime` keeps growing across an ignition
   cycle, `BOOT_COMPLETED` never fires. The service must then survive in RAM or be
   restarted by the watchdog alarm. → check with `scripts/acc-cycle.sh`.
2. **ROM kills / force-stops 3rd-party apps on ACC off** ("memory clean"). A
   force-stop cancels alarms AND sets `stopped=true` → no broadcast reaches the app
   until it is opened by hand. Fix: ROM whitelist (Car settings → General →
   Whitelist / factory menu `16176699`), and/or listen to the vendor's ACC-on
   broadcast (find it in logcat).
3. **App never opened manually since install** (`stopped=true` in `dumpsys package`).
4. **Overlay probe refused** → appops `SYSTEM_ALERT_WINDOW` denied.
5. Reverse property wrong → clock may launch during reverse (safety!) or never.

## Likely code changes (v17)

- Receivers for `SCREEN_ON` (must be registered at runtime by the service),
  `USER_PRESENT`, `ACTION_POWER_CONNECTED`, plus the vendor ACC-on intent found in logs.
- Replace the hardcoded reverse list with the property measured by `scripts/reverse-props.sh`.
- Default `IDLE_MS` → 60000.
- Bump versionCode to 17. Signing key (`dacia.jks`, alias `dacia`) is NOT in the
  repo: if it is lost, a new key means `adb uninstall` first
  (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), then the app must be opened once by hand again.
