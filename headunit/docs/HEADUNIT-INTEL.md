# HEADUNIT-INTEL — Dacia Duster aftermarket head unit (K2001M / NWD ROM)

> **Audience: AI coding agents.** Read this before touching the unit. It maps what the system is,
> every access channel and its privilege level, how each subsystem works, what has already been
> changed, and the rules for modernising it **without breaking car functions**.
> Compiled 2026-11-08 from a live root-ADB session + offline decompilation (JADX 1.5.6, REA 6.8.0).

**Evidence tags** — `[LIVE]` observed on the running unit · `[STATIC]` read in decompiled ROM code ·
`[INFERRED]` reasoned, not confirmed · `[UNKNOWN]` open question. Prefer `[LIVE]` > `[STATIC]` > `[INFERRED]`.
Deep dives: `docs/rom-analysis/{backcar,can,mcu-protocol,process-rules,signing}.md`. Device constraints: `docs/DEVICE-PROFILE.md`.

---

## 0. Ground rules for agents (non-negotiable)

1. **Car functions first.** Never touch: launcher `com.android.launcher`, radio (`com.nwd.radio*`), BT phone
   (`com.bt.bc03`, `com.nwd.android.phone`, `com.nwd.bt.music`), CAN (`com.nwd.can.setting`), reverse camera
   (`com.nwd.backcar`), MCU/kernel (`com.nwd.kernel`, `com.nwd.setting.service`), SystemUI, Settings, WebView.
2. **Never write `/system`** (it is reachable as root — do not). Never flash. No cross-branch firmware (K2001N/Q bootloop).
3. **Read-only freely; ask the owner before** any `settings put`, `setprop`, `pm disable/uninstall/clear`, `appops set`,
   file write/delete, install, reboot. Log every change + its undo in `diag/debloat-log.md`.
4. **Reverse camera is safety-critical.** Never draw persistent overlays; never write a camera app; reverse tests only
   parked + handbrake, owner performs the physical action.
5. **Prefer reversible mechanisms**: `pm disable` (undo `pm enable`) over uninstall; settings with recorded old value.
6. **The unit only has power with ignition on** — keep sessions short; owner may need the engine running.

---

## 1. Identity & hardware `[LIVE]`

| Item | Value |
|---|---|
| Model / ROM | `QUAD-CORE T3 K2001M`, APPVER `K20M_K2001M_NWD_GB-20180427.18.48`, build 2018-04-27, NWD ("nwdapp_*") ROM |
| SoC | Allwinner T3 (= R40, `sun8iw11p1`), 4× Cortex-A7 1.2 GHz, **armeabi-v7a only** |
| GPU / decoder | Mali-400 MP2, **GLES 2.0 max**; HW H.264 `OMX.allwinner.video.decoder.avc` (720p@30 decoded fine) |
| RAM | 1 GB (770 MB usable) + 350 MB zram swap |
| Storage | `/data` 1.9 GB (≈940 MB free after cleanup), `/sdcard` 9.9 GB |
| Screen | 1024×600 landscape, ~171 dpi |
| Android | 6.0.1 / API 23, WebView ≈ Chromium 44, SELinux **disabled** (`ro.boot.selinux=disabled`) |
| Radios | Wi-Fi **2.4 GHz only** (no 5 GHz band); Wi-Fi Direct supported; no modem; GPS via `gps.*.so` HALs |
| Bluetooth | Android BT stack exists but **cannot start** (`waitForOnOff time out`, fake addr `22:22:DE:D4:C6:66`). Phone/music BT = separate **BC03 module** via `com.bt.bc03` / `gocsdk` |
| RTC | none — time from NTP (works ~25 s after boot when online) / GPS (`com.nwd.gps.sync`) |
| CAN box | Xinpo (欣朴) "simple" protocol, profile Renault → Dacia → "全系-通用 all models generic" |
| Car | 2016 Dacia Duster mk1 ph2, 1.5 dCi |

---

## 2. Access channels & privilege levels

| # | Channel | How | Level | Notes |
|---|---|---|---|---|
| A1 | **ADB over TCP** | `adb connect <ip>:5555` — already listening, no prompt | **uid 0 (root)**, no `su` binary needed | `[LIVE]` Full control. Exposed to anyone on the same Wi-Fi → only use password-protected networks you own. |
| A2 | Settings provider | `settings get/put system|global|secure <k>` | system-wide config | `[LIVE]` Most ROM behaviour is keyed here (§5). |
| A3 | System properties | `getprop` / `setprop` | root | `[LIVE]` `persist.*` survive reboot. |
| A4 | sysfs nodes | e.g. `/sys/devices/soc.0/car_reverse.14/state` | root read; apps can read some | `[LIVE]` |
| A5 | Broadcasts | `am broadcast -a …` | root / any app | `[STATIC]` Many ROM actions accept unprotected intents (§5, mcu-protocol.md). **`am` cannot send byte extras on Android 6** — ROM uses byte extras (e.g. `extra_back_state`), so some need a helper app. |
| A6 | **Exported `com.nwd.kernel` KernelService** (AIDL `IKernelFeature`: `request(byte[])`, `registCallback`) | bind from any app | **raw MCU frames, read & write** | `[STATIC]` Powerful and dangerous; both kernel/setting APKs are `debuggable=true`. Debug actions `com.nwd.test.ACTION_EMU_NOT_COMMUNICAT_WITH_MCU` / `com.nwd.ACTION_EMU_NO_COMMUNICATION` **stop the MCU link — never send**. |
| A7 | ROM config files | `/config/app/*.ini|*.xml` (`ro.nwd.config.path`), `/mnt/sdcard/backups/` overrides | root write | `[STATIC]` e.g. `OneClearConfig.xml`, `UartConfig.ini`, `UpdateConfig.ini`, `FactoryConfig.ini`. Back up before any edit. |
| A8 | Package manager | `pm disable/enable`, `install-existing` (no), `appops set` | root | `[LIVE]` Used for debloat + usage-access grant. |
| A9 | User CA store | `/data/misc/user/0/cacerts-added/<hash>.0` | root | `[LIVE]` Android 6 lacks ISRG Root X1 (Let's Encrypt). Was added then **removed on owner request**. |
| A10 | Signing | ROM APKs signed by **private vendor key** `715eb4d7…` (AOSP-looking subject, NOT AOSP test keys); CAN app by "Android Debug" `726921…` | — | `[STATIC]` → cannot sign apps as platform/system. Privileged ops = A1 only. See `rom-analysis/signing.md`. |
| A11 | UI | factory menu code `16176699` (not found in decompiled APKs — lives elsewhere `[UNKNOWN]`); "Car model" activity `com.nwd.can.setting/.ui.canfactory.CanFactoryActivity` (exported) | owner | Re-saving the CAN car model runs `resetCarConfig()` (resets radar/toggles) — avoid. |

### Connectivity recipes `[LIVE]`
- **Home router `ZTE_*` isolates Wi-Fi clients** → laptop cannot reach the unit through it.
- **Working:** Windows Mobile Hotspot on the laptop (`192.168.137.0/24`; unit got .201/.211/.157/.252 — IP changes on rejoin).
  Windows auto-turns the hotspot off when idle ("power saving") — re-enable via WinRT `NetworkOperatorTetheringManager.StartTetheringAsync`.
- **Daily (car):** unit joins the **phone hotspot** (must be 2.4 GHz). Android 11+ phones randomise the hotspot subnet each time.
- Git Bash on Windows: set `MSYS_NO_PATHCONV=1` or `/sdcard/...` gets rewritten to a Windows path.
- adb.exe: `%LOCALAPPDATA%\Microsoft\WinGet\Packages\Google.PlatformTools_*\platform-tools`.

---

## 3. System architecture

```
 Car wiring (ACC, ILL, reverse lead, handbrake, keys) ─┐
 CAN bus ── Xinpo CAN box ──UART 38400 (0x2E frames)──►│ com.nwd.can.setting (doors, radar, wheel keys, temp)
                                                       ▼
                     MCU ──UART /dev/ttyS3 57600 (F0 frames)──► com.nwd.kernel (KernelService, DeviceState)
                                                       │            │ writes Settings.System mcu_* , broadcasts
                                                       ▼            ▼
             system_server (+ NWD hooks: NwdPackageChecker, SleepAppSelfStart/WakeAppSelfStart,
                            NwdManagerService, ProcessList.isNwdProtectPackage)  [framework.jar / services.odex]
                                                       │
     launcher (com.android.launcher, NWD Carkit 4.2) ─ apps ─ com.nwd.backcar (camera overlay type 2033)
     OneKeyClear (sleep-time killer) · MemorySettingService (Recents kill) · SourceMgr (audio source arbitration)
```

### 3.1 MCU protocol `[STATIC]` (mcu-protocol.md)
Frame `F0 LEN TYPE DTYPE 00 DATA… CS` (LEN = total−3, CS = sum(bytes1..n−2) & 0xFF). ACK = type 0/0x0F; resend every 3 s.
Key MCU→ARM messages (type 0): `0x00` power state `[state, src]`, `0x04` handbrake, **`0x05` reverse**, `0x06` headlights/ILL,
`0x16` OS-sleep handshake. Keys: type 1/dtype 1 `[keyType, long, keyValue]` (NEXT 5, PREV 6, PLAY 7, VOL 14/15, MUTE 2, MODE 23,
NAVI 11, PHONE 10). Heartbeat seen in logs `F005000200000007` = ARM volume report.

### 3.2 Power / ignition `[LIVE]+[STATIC]`
- **Ignition off = sleep, not reboot** (`[LIVE]` uptime 66 days). `BOOT_COMPLETED` almost never fires.
- MCU states: `1` ACC on · `3` ACC off (delay) · `0` shutdown/sleep · `2` soft-off.
- Broadcasts: ACC off → `com.nwd.action.ACTION_MCU_STATE_CHANGE` (`extra_mcu_state` byte) → `com.nwd.action.ACTION_MCU_POWER_OFF`
  → `com.nwd.ACTION_FRAMEWORK_CLEAN_SUCCESS` (`extra_clean_value` bits) → `com.nwd.android.ACTION_REQUEST_SYSTEM_GOTO_SLEEP`.
  ACC on → `com.nwd.ACTION_TURNON_DEVICE_POWER` → state 1 → `com.android.action.ACTION_INIT_FINISH`.
- On sleep: Wi-Fi forced off, GPS mode off, media cleared; on wake Wi-Fi re-enabled, volume reset to max.
- **ACC-on launch hook:** `arm_auto_start_navi_when_acc_on=1` launches `navigation_packagename/navigation_classname` on screen
  (currently Dacia Clock — this is how the clock opens every ignition-on `[LIVE]`).

### 3.3 Process / app-survival rules `[LIVE]+[STATIC]` (process-rules.md)
- **Swiping an app from Recents** → `com.nwd.ACTION_KILL_OTHER_APP` → killed → afterwards every background start fails
  `Unable to launch app X … process is bad` **until the app is opened on screen** `[LIVE]`. Same after `adb install -r` `[LIVE]`.
  Mechanism `[STATIC/INFERRED]`: NWD self-start gate in `startProcessLocked` (SleepAppSelfStart/WakeAppSelfStart) refuses → null → "bad".
- **While asleep** (`sys.nwd.mcu.state` 0/3): background starts refused (`SleepAppSelfStart: Don't start proc`), slow broadcast
  receivers/services are killed.
- **Protected packages** (hardcoded `ProcessList.isNwdProtectPackage`: bluetooth, `com.nwd.miscapp`, system + `NwdPackageChecker.checkIsNwdService`)
  — not extensible without patching.
- **OneKeyClear** (`com.nwd.android.onekeyclear`): on MCU state 0/3 (after `delay_time_kill_app`, UartConfig.ini) force-stops apps
  (→ stopped state, alarms cancelled) and clears tasks. **Exemption file `/config/app/OneClearConfig.xml`**, re-read each run:
  `<ProcessItem PackName="powernotkill_<pkg>">` survives both kill and task-clear. Spares `navigation_packagename` in state 3 only.
- `whitelist_packagename` / `whitelist_appname` (Car settings → Whitelist UI) — **consumer not found** in analysed code `[UNKNOWN]`
  (likely framework.jar `NwdTaskWhitelist`).
- `kill_background_services=1` (Allwinner): only force-stops packages listed in `kill_background_services_list` under low memory.

### 3.4 Reverse camera `[STATIC]+[LIVE]` (backcar.md, fix-reverse.md)
- Source of truth: **MCU frame 0/5** → `DeviceState.saveBackCarState()` → `Settings.System mcu_backcar_state` +
  broadcast `com.android.action.ACTION_BACKCAR_STATE_CHANGE` (**byte** `extra_back_state`; suppressed while ACC off unless 0).
- `com.nwd.backcar` shows a **WindowManager overlay type 2033** ("TOP_LAYER_3", fullscreen, SurfaceView, `Camera.open(6)` CVBS),
  then polls `android.os.Hardware.getReverseCarState()` (≈ `car_reverse.14/state`) every 10 ms; 0 → mask + close after
  `backcar_delay_exit_time` (5 s). A fullscreen activity cannot cover it; TYPE_SYSTEM_ALERT ordering vs 2033 `[UNKNOWN]`.
- **`backcar_can_control` does NOT affect detection** (guide-line config only) — keep 1. CAN box decodes **no** reverse for any
  Renault/Dacia profile. `camera_ccd`/`video_signal_ccd` are rewritten from UpdateConfig.ini each start (don't edit).
- **Current fault `[LIVE]`:** reverse never reported (node 0, `mcu_backcar_state` 0, all broadcasts 0) with ignition-only.
  Most likely physical: reverse lead gets no +12 V. Next: engine-on test, check reverse lamps, read both values, then harness.
- `am broadcast --ei extra_back_state 1` cannot simulate reverse (byte extra).

### 3.5 CAN box `[STATIC]` (can.md)
Profile `renault.simple.CanManager`, UART 38400, frames `0x2E|type|len|data|cks`. Decodes wheel keys (0x20), A/C (0x21, hidden),
rear radar ×4 (0x22), doors/boot/bonnet (0x24), outside temp (0x27), steering angle (0x29, reverse only), version (0x30).
No speed/RPM/gear/handbrake. No Duster-specific profile exists; current config is the only valid Dacia one.
`can_next_previous_back=1` swaps wheel direction. `can_config_debug_is_open=1` → every wheel key broadcast as
`com.nwd.action.keycode.state` (`extra_keycode`, `extra_status`) — usable for a media-key bridge.

### 3.6 Display, audio, misc `[LIVE]`
- Backlight via MCU (`mcu_back_light_day/night`=100/25, ILL-driven); screen never sleeps (`screen_off_timeout` = max).
- Screensaver (Dream) supported (`mDreamsSupportedConfig=true`), currently configured to a non-existent Google desk clock; unused.
- Audio source arbitration via `nwdapp_SourceMgr` / SourceKeeper (every app launch = "changeSource").
- Dashcam service `com.nwd.dvr.service` polls USB camera every 0.5 s (log spam `m_tag`).
- GPS: no fix indoors; HAL repeats last fix without accuracy → Android drops it ("incomplete location"). First outdoor fix slow (no A-GPS).

---

## 4. Current modifications (all logged with undo in `diag/debloat-log.md`) `[LIVE]`

| Change | Undo |
|---|---|
| `pm disable` Google: gms, gsf, gsf.login, vending, configupdater, apps.mapslite (+≈77 MB RAM) | `pm enable <pkg>` |
| `pm disable` com.bizhi.mogudongtai, com.android.musicvis, com.android.wallpaper.livepicker | `pm enable <pkg>` |
| Dacia Clock **v17.1** (new key) replaces v16; `appops GET_USAGE_STATS allow` | reinstall `duster/clock-auto-3min.apk` (uninstall first) |
| ROM: `navigation_packagename=ma.aymane.daciaclock` (+ class) → clock on every ignition-on; clock in Whitelist | set back via Car settings |
| OsmAnd prefs: car profile, text 1.25, Weather/Wikipedia/Terrain plugins off | restore `diag/osmand-prefs-exact-*` |
| Animations 0.5× (window/transition/animator) | set to 1.0 |
| Caches trimmed, tombstones/ANR removed | — |
| F-Droid + ISRG Root X1 CA **installed then removed** (owner request) | — |
| Installed apps: OsmAnd 4.7.10 (+Morocco map), VLC 3.7.2, AndrOBD 2.7.10, Open Headunit 3.5.0 | `adb uninstall` |

---

## 5. Verified recipes `[LIVE]`

- **Dacia Clock v17.1** (source `daciaclock-src/`, `build.sh`): opens after 180 s idle **only when `com.android.launcher` is in front**
  (UsageStats), tap → back, closes in reverse (`extra_back_state` / `mcu_backcar_state` / sysfs). After install/update: open
  `ma.aymane.daciaclock/.ClockActivity` once (clears "process is bad").
- **Wireless Android Auto:** phone runs AA → dev settings → "Start head unit server"; Open Headunit **Wireless Mode = Headunit Server**
  (Native mode needs Bluetooth = impossible here) or `am start -a android.intent.action.VIEW -d "headunit://connect?ip=<PHONE_IP>"`.
  Result 720p30 HW-decoded, ~67 MB RAM, 11 % CPU. **Never probe port 5277 before connecting** (AA treats it as a car and hangs).
  AA first-run setup on the phone needs Speech Services + Google app installed/up to date.
- **APK compatibility check:** minSdk ≤ 23, has `armeabi-v7a` or no native libs, GLES ≤ 2.0 (`scratchpad/tools/apkcheck.py` pattern).
- **Old F-Droid client for Android 6:** 1.23.2 (2.x needs API 24); OsmAnd ≤ 4.7.10 (`471001` = armeabi-v7a build).

---

## 6. Modernisation opportunities (keep it functional and simple) — ranked

| Idea | Value | Risk | Mechanism |
|---|---|---|---|
| Fix reverse camera (wiring) | **safety** | low (physical) | §3.4 |
| `powernotkill_` entries in `OneClearConfig.xml` for clock / OsmAnd / Open Headunit | apps survive sleep | low (backup file) | §3.3 |
| Open Headunit "Autostart on WiFi" + phone hotspot auto-join | AA in seconds | low | app settings |
| Wheel-key → media-key bridge app (listen `ACTION_KEY_VALUE` / `keycode.state`, inject KEYCODE_MEDIA_*) | wheel controls VLC/AA | low (own app) | §3.5 |
| Dacia Clock v17.2: skip idle launch unless MCU state = 1; listen to ACC-on broadcast | polish | low | §3.2 |
| Custom launcher look | — | **high** (launcher carries radio/BT/CAN) | **avoid**; theme via wallpaper/clock instead |
| Custom OS / Linux distro | — | very high | rejected: loses MCU, CAN, radio, camera, BT integration |

---

## 7. Open questions → how to resolve

| Question | Resolve with |
|---|---|
| Does the MCU take reverse from the wire or CAN? | engine-on R test; `settings get system mcu_backcar_state`, `cat …/car_reverse.14/state`; harness lead ~12 V in R |
| Who consumes `whitelist_packagename`? Sleep/self-start rules exact | pull `/system/framework/framework.jar` + `/system/framework/arm/boot*.oat`, decompile `com.nwd.app.*` |
| Launcher internals (home screen, app drawer, boot app, source keys) | pull `/system/app/K1201Z001_NWD_GB_Carkit_Launcher_4.2`, `…All_App`, `/system/priv-app/SystemUI` |
| TYPE_SYSTEM_ALERT vs camera overlay 2033 z-order | `dumpsys window windows` while camera shows (after reverse works) |
| Is `715eb4d7…` the platform key? | `dumpsys package android | grep -A3 signatures` |
| Factory menu `16176699` handler | search framework/launcher after pull |
| Read-only checks suggested by analysis | `dumpsys appselfstart`, `dumpsys whitelist`, `cat /config/app/OneClearConfig.xml`, `cat /config/app/UartConfig.ini` |

---

## 8. File index

- `docs/HEADUNIT-INTEL.md` — this report · `docs/DEVICE-PROFILE.md` — hardware/WebView/app constraints (note: its "Android Auto
  out of reach" is superseded — AA works via Open Headunit receiver).
- `docs/rom-analysis/backcar.md`, `can.md`, `mcu-protocol.md`, `process-rules.md`, `signing.md` — evidence-level deep dives.
- `fix-reverse.md` — reverse camera plan · `TODO.md` — GitHub upload checklist · `diag/debloat-log.md` — every change + undo.
- `daciaclock-src/` — Dacia Clock v17.1 source/build · `duster/DaciaClock-v17.apk`, `duster/clock-auto-3min.apk` (v16).
- Decompiled ROM sources (temporary, laptop scratchpad): `scratchpad/rom-analysis/{backcar,can,mcu,process}/`; raw APKs `scratchpad/rom-pull/`.
