# To do — Duster head unit

## GOAL (owner, 2026-11-08): full exploration of the head unit, in every aspect
So it can later be **modified, fine-tuned, and the findings exploited** — while keeping the car functional and simple.
Exploration phase = **read-only**: pull, decompile, document. Changes come later, one at a time, with the owner's OK.
Results go into `docs/HEADUNIT-INTEL.md` (summary, evidence-tagged) + `docs/rom-analysis/<area>.md` (deep dives).

### Exploration plan (ignition on; ≈ 30–60 min of unit time, split over sessions)
Pull everything once to the laptop (`adb pull`, read-only), then analyse offline (JADX / REA) — battery-friendly.

| # | Area | Pull / read-only commands | Questions to answer |
|---|---|---|---|
| E1 | **Launcher + app drawer** | `/system/app/K1201Z001_NWD_GB_Carkit_Launcher_4.2/`, `…_All_App/` | home layout, widgets, source/NAVI/MODE keys, boot app (`NwdBootUtils.startBootApp`), what is themable/replaceable safely |
| E2 | **Framework NWD hooks** | `/system/framework/framework.jar`, `/system/framework/arm/boot*.oat`, `services.odex` (have) | `com.nwd.app.*` (NwdPackageChecker, Sleep/WakeAppSelfStart, NwdTaskWhitelist, NwdKeyManager, NwdManagerService), who reads `whitelist_packagename`, "process is bad" exact rule |
| E3 | **SystemUI** | `/system/priv-app/SystemUI/` | status bar, Recents kill path (`ACTION_KILL_OTHER_APP`), nav bar, volume panel (`com.nwd.volumeview`) |
| E4 | **All other NWD apps** | every `/system/app/*` + `/system/priv-app/*` (list in `diag/debloat-*/pkgs-all.txt`) | role of each (radio, BT, DVR, TV, DVD, OBD, factory, memory, reboot, update…) → one-line table; which are safe to disable later |
| E5 | **Config files** | `ls -R /config`, `cat /config/app/*.ini *.xml`, `ls /mnt/sdcard/backups` | UartConfig, UpdateConfig, FactoryConfig, OneClearConfig, CAN config — all tunables + defaults |
| E6 | **Boot & init** | `/init*.rc`, `/system/etc/init*`, `getprop | grep init.svc`, `/system/bin` custom binaries | startup services (`startgps`, `boot_init`, `bootclone`…), boot logo/animation, what runs as root |
| E7 | **Kernel & hardware** | `uname -a`, `/proc/version`, `/proc/cmdline`, `/sys/devices/soc.0/*` tree, `/dev` list, `lsmod`, `/proc/partitions`, `ls /dev/block/by-name` | partition map (backup strategy!), drivers (reverse, CVBS, tuner, BT, GPS UART), GPIO/sysfs control points |
| E8 | **Partitions backup** (read-only dd to SD) | `dd if=/dev/block/by-name/<boot|recovery|env|bootloader…> of=/sdcard/backup/…` then pull | full restore image before any future modification |
| E9 | **Audio** | `dumpsys media.audio_flinger`, `/system/etc/audio_policy.conf`, SourceMgr logs | routing radio/BT/Android, EQ/DSP, why volume resets to max on wake |
| E10 | **Display/GPU** | `dumpsys SurfaceFlinger`, `dumpsys display`, `/system/build.prop` lcd density | resolution/density tuning, overlay z-order (camera type 2033 vs others) |
| E11 | **Inputs/keys** | `getevent -lp`, `dumpsys input`, CAN debug `can_config_debug_is_open=1` (needs OK) | steering-wheel → key path, touch, panel knobs → media-key bridge design |
| E12 | **Network/BT stack** | `dumpsys wifi`, `dumpsys bluetooth_manager`, `com.bt.bc03` + `gocsdk` analysis | BC03 module protocol (calls/music), Wi-Fi 2.4 limits, hotspot auto-join |
| E13 | **GPS/time** | `dumpsys location`, `/system/etc/gps.conf`, HAL choice (`gps.4752.so` vs others) | first-fix speed, A-GPS without Google, time sync |
| E14 | **Security surface** | exported components of every NWD app (`dumpsys package` / REA `inspect-android-package`), open ports (`netstat`), debuggable apps | what any app can trigger; risks of root ADB on 5555 |
| E15 | **Update paths** | `com.nwd.rk.quickupdate`, `factory.copy.autoupdate`, `onekey.installapk`, USB update files | how firmware/CAN updates are applied (never run them — document only) |

Rules: read-only during exploration; never write `/system`; dangerous MCU debug broadcasts must not be sent (see INTEL §2 A6).
Pulled files → laptop `scratchpad/rom-pull/` (temporary) — **copy to a permanent folder** (e.g. `duster-headunit/rom-dump/`, git-ignored) before analysis so nothing is lost.

## Upload findings to GitHub as backup + guide  (repo: https://github.com/achma-learning/duster)

Nothing has been committed or pushed yet.
**Owner decision 2026-11-08: push the findings tomorrow morning, personal data included** (Carlytics reports, network names
etc. stay in). Owner decision (same day): push **everything**, including the signing key (`headunit/keys/`), vendor dumps (`headunit/rom-dump/`) and raw logs (`headunit/diag/`) — public repo, risk accepted.
Before pushing:

- [ ] Back up `keys/dacia-v17.jks` + `keys/dacia-v17.pass` **outside** the repo (USB stick / cloud). Never commit them (`keys/` is git-ignored).
- [ ] Check `duster/carlytics/*.pdf` and `duster/Carlytics Report*.pdf` for VIN / plate / personal data before pushing them publicly.
- [ ] Remove personal data from the docs if the repo stays public: hotspot names (`YASSER`, `DESKTOP-7JKU08S 6911`), its password, home router name.
- [ ] Add to the repo (suggested layout):
  - `apk/DaciaClock-v17.apk` — current clock (opens only over the home screen, tap to leave, reverse-aware)
  - `apk/clock-auto-3min.apk` — v16, old behaviour (opens every 3 min, even over apps)
  - `daciaclock-src/` — v17 source + `build.sh` + README (exclude `daciaclock-src/build/`)
  - `docs/DEVICE-PROFILE.md`, `docs/DACIACLOCK-V16-ANALYSIS.md`
  - `docs/HEADUNIT-GUIDE.md` — new guide from this session (see below)
  - `fix-reverse.md`
  - `scripts/` (diag, acc-cycle, reverse-props, set-idle)
- [ ] Write `docs/HEADUNIT-GUIDE.md` covering:
  - ADB over Wi-Fi: unit is root on port 5555; laptop hotspot works, home router isolates clients
  - ROM quirks: Recents swipe → "process is bad" (no background restart until opened on screen); ignition = sleep, not reboot; OneKeyClear memory cleaner; ROM whitelist + "navigation app = Dacia Clock" + `arm_auto_start_navi_when_acc_on` for ignition-on launch
  - Debloat done (pm disable, reversible): Google GMS/GSF/login/Play Store/ConfigUpdater/Maps Go; 3D wallpaper, music visualizer, live wallpaper picker — see `diag/debloat-log.md`
  - ISRG Root X1 user CA (`/data/misc/user/0/cacerts-added/6187b673.0`) so F-Droid works on Android 6
  - Verified working apps + versions: F-Droid 1.23.2, OsmAnd 4.7.10 (+ car settings), VLC 3.7.2, AndrOBD 2.7.10, Open Headunit 3.5.0
  - Wireless Android Auto: phone AA "Start head unit server" + Open Headunit **Wireless Mode = Headunit Server** (Native needs Bluetooth, which this unit's Android stack can't start); 2.4 GHz only; never probe port 5277 first
  - Reverse: broadcast `ACTION_BACKCAR_STATE_CHANGE` / `extra_back_state`, kernel node `car_reverse.14/state`, `backcar_can_control`
  - GPS: no fix indoors; first fix slow (no RTC, no A-GPS)
- [ ] Commit from `duster/` on a branch, push, open a PR (or push to main if you prefer).

## Still open on the unit
- [ ] Reverse camera not triggered — see `fix-reverse.md` (test with engine running first).
- [ ] Re-test ignition-on clock launch with v17.1.
- [ ] GPS outdoor test.
- [ ] On the head unit, forget the laptop hotspot `DESKTOP-7JKU08S 6911` once the phone hotspot is the daily network.
- [ ] Optional: disable ADB-over-network after each session (root on 5555).
