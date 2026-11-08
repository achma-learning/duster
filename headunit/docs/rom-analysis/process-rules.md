# NWD ROM: process-killing and self-start rules (K2001M / Allwinner T3, Android 6.0.1)

Static analysis only, of local files. No device was contacted.

Sources:
- `OneKeyClear.apk` (`com.nwd.android.onekeyclear`, sharedUserId `android.uid.system`). Decompiled cleanly with JADX 1.5.6.
- `services.odex`, converted to `classes.dex`. Decompiled with JADX; 472 methods had decode errors. `ProcessList.<clinit>` was recovered with `-m fallback`.
- Working output: `%TEMP%\claude\...\scratchpad\rom-analysis\process\{okc,svc,pl-fb}`

Confidence tags: **[CODE]** = read directly in decompiled code. **[INFER]** = deduced from code plus logs. **[UNKNOWN]** = the logic is in a file we do not have.

> **Key gap.** The NWD policy classes `com.nwd.app.NwdSleepAppSelfStart`, `NwdWakeAppSelfStart`, `NwdTaskWhitelist`, `NwdPackageChecker`, `NwdNetworkBlackWhitelist` and `NwdSettingsObserver` are **not in services.odex**. services only imports them. They are in `/system/framework/framework.jar` (and the matching `boot*.oat`), which has not been pulled. The log strings `Don't start proc X for broadcast` and `Set status WAKE -> SLEEP` are not in any dex we have, so they come from those classes. Any statement below about their internal rules is [UNKNOWN]/[INFER]. Next step: pull `framework.jar` + `/system/framework/arm/boot*.oat`, de-oat, and decompile `com.nwd.app.*`.

---

## 1. When a process becomes "bad", and what clears it

### 1a. The log message is misleading [CODE]
`BroadcastQueue.processNextBroadcast` and `ActiveServices.bringUpServiceLocked` print
`Unable to launch app P/uid for broadcast|service ...: process is bad` whenever
`ActivityManagerService.startProcessLocked(String, ApplicationInfo, ...)` returns `null`. In this ROM that happens in four cases:

| # | Path (AMS) | Condition | Cleared by |
|---|---|---|---|
| A | AOSP `mBadProcesses` | Start carries `FLAG_FROM_BACKGROUND (4)`. Broadcasts always add it: `r.intent.getFlags() \| 4`. The process is in `mBadProcesses`. | Any start **without** flag 4, e.g. an activity launch or a plain `startService`: `mBadProcesses.remove(...)`, `app.bad=false`, `am_proc_good` |
| B | **NWD gate** in the private `startProcessLocked(ProcessRecord, hostingType, ...)` | `NwdSleepAppSelfStart.isSleepIng() && !allowSleepAppSelfStart(info, isolated, hostingType)`, **or** `NwdWakeAppSelfStart.isWakeIng() && !allowWakeAppSelfStart(...)` and the package is not the current IME (`sys.cur-method.package`) | The gate state ending (see 3), or a hostingType the gate allows (presumably `activity`) [INFER] |
| C | Boot hold | Package in the hardcoded `otherApps` list (iflytek, kuwo, kugou, gms/gsf/vending, chrome, settings, latin IME, com.nwd.assistivetouch, ...) and `!mBootAnimationComplete` | Started automatically 6 s after `android.intent.action.UI_BOOT_FINISH ` (note the trailing space in the action) by `delayStartApp()` |
| D | Package frozen | `isPackageFrozen` | install finishes |

In case B the method simply returns without calling `Process.start`. `app.pid` stays 0, so the caller gets `null` and prints "process is bad".

Excerpt (AMS `startProcessLocked(ProcessRecord,String,String,String,String,String[])`):
```java
NwdSleepAppSelfStart sInstance = NwdSleepAppSelfStart.getInstance();
if (sInstance == null || !sInstance.isSleepIng() || sInstance.allowSleepAppSelfStart(app.info, app.isolated, hostingType)) {
    String pakageName = SystemProperties.get("sys.cur-method.package", "");
    if (pakageName.equals(app.processName) || !NwdWakeAppSelfStart.isWakeIng()
            || NwdWakeAppSelfStart.allowWakeAppSelfStart(app.info, app.isolated, hostingType)) {
        ... Process.start(...) ...
    }
}   // else: silently nothing -> caller logs "process is bad"
```

### 1b. Which case matches the observed behaviour [INFER]
The observed failures include `for service ...: process is bad`. Under case A a service start without flag 4 *clears* the bad state, so A alone does not fit. Case **B (the NWD self-start gate)** is the most likely cause. The companion log `Don't start proc X for broadcast` comes from the same `com.nwd.app` classes.

How it is cleared:
- Opening the app on screen starts its process with hostingType `activity`, which the gate evidently allows. The activity-resume path also calls `NwdPackageChecker.checkIsOtherApp(next.intent, ctx)` in `ActivityStack.completeResumeLocked`, which may mark the package as user-used [UNKNOWN].
- `NwdWakeAppSelfStart.setKeyCode(event)` is called for **every key event** in `InputManagerService.interceptKeyBeforeDispatching`. So the wake gate also tracks user key input, possibly to end the "waking" window on first user interaction [UNKNOWN exact rule].
- `PowerManagerService.wakeUpNoUpdateLocked` calls `NwdSleepAppSelfStart.setSystemStatus(false)` and `NwdWakeAppSelfStart.setSystemStatus(false)` only when waking from **dozing** (wakefulness 3). The code that sets them to `true` is not in services, so it is presumably a `com.nwd.app` receiver of `com.nwd.action.ACTION_MCU_STATE_CHANGE` [INFER].

Why a Recents swipe leads to it: SystemUI sends `com.nwd.ACTION_KILL_OTHER_APP` and MemorySettingService kills the app. The package is then probably recorded as "user-killed" by `NwdTaskWhitelist`/`NwdWakeAppSelfStart`, and later background self-starts are refused until the user opens the app [INFER; MemorySettingService and framework.jar not analysed].

### 1c. Ways to be exempt
- **Current input method** (`sys.cur-method.package`) bypasses the *wake* gate, but not the sleep gate [CODE].
- `allowSleepAppSelfStart` / `allowWakeAppSelfStart` receive `ApplicationInfo`, `isolated` and `hostingType`. They likely allow system/NWD apps and possibly a configured list (`whitelist_packagename`?) [UNKNOWN].
- Binder services `whitelist` (`NwdTaskWhitelist.Service`) and `appselfstart` (`NwdWakeAppSelfStart.Service`) are registered in `SystemServer`. They might expose list management or `dumpsys` output [UNKNOWN; worth `adb shell dumpsys appselfstart` / `dumpsys whitelist` and `service list`].
- `NwdTaskWhitelist.checkRestartServicesAndProcessAfterKilled(proc, pid, ...)` in `cleanUpApplicationRecordLocked` decides whether a killed app's services/process are **restarted at all** (`Slog.v("Whitelist","Don't restart process[...]")`) [CODE call site; rule UNKNOWN]. A non-whitelisted app therefore gets no sticky-service restart after a kill.

### 1d. Other kill hooks found in services [CODE]
- `ActiveServices.ServiceRestarter/rescheduleDelayedStarts`: while `sys.nwd.mcu.state == "0"` (asleep), a background service start that waited too long has its process `Process.killProcess`'d (`=====xh====kill process:`).
- `BroadcastQueue.broadcastTimeoutLocked`: if `sys.nwd.mcu.state` is `0` or `3`, the receiving process is killed on broadcast **timeout**, unless `NwdPackageChecker.checkIsNwdService(proc)`, systemui or launcher. Receivers must finish fast while asleep.
- `PhoneWindowManager.dispatchUnhandledKey`: if the top app leaves **Back unhandled 3 times in a row**, it is `Process.killProcess`'d and `com.nwd.ACTION_KILL_OTHER_APP` is broadcast. This is the same path as a Recents swipe.
- `ActivityStackSupervisor.startActivityLocked`: `NwdPackageChecker.checkIsRestrictActivity` can block activity starts (err 4) [rule UNKNOWN].

## 2. "nwd protect package" list [CODE]
`ProcessList.isNwdProtectPackage(name)`. The name is cut at `:`, then:
```java
protect_apps = { "com.android.bluetooth", "com.nwd.miscapp", "system_server", "system" };   // <clinit>, hardcoded
... return NwdPackageChecker.checkIsNwdService(pkg);   // framework.jar, rule UNKNOWN
```
For protected packages AMS skips `killBackgroundProcesses`, `killAllBackgroundProcesses`, `forceStopPackageLocked` (logs "skip forceStopPackage() 1/2"), `killPackageProcessesLocked`, and the empty-process trim in `updateOomAdjLocked`. `setOomAdjByPackageName` also pins them to `SYSTEM_ADJ (-16)`.

- Source: hardcoded array in services.odex plus `NwdPackageChecker.checkIsNwdService`. No Settings key or file is read in services. `checkIsNwdService` is probably a list or prefix check on NWD service packages (`com.nwd.*service*`, `com.nwd.kernel`, ...) [UNKNOWN].
- **Adding a package**: not possible through configuration as far as the available code shows. It would require patching services.odex/framework.jar, which is high risk on this device and not recommended. Note: `removeTask`-driven kills (Recents/ClearAllTask) and `Process.killProcess` do **not** go through `isNwdProtectPackage`.

## 3. SleepAppSelfStart / WakeAppSelfStart and the settings
- **Gate semantics** [CODE call site / UNKNOWN rule]. While "sleeping" (ACC off, MCU state 0), every process start goes through `allowSleepAppSelfStart`. While "waking", every start goes through `allowWakeAppSelfStart`. Both include `hostingType` (`broadcast`, `service`, `activity`, `content provider`, `restart`), so the rules can differ per start reason. The logs show this pattern: `Set status WAKE -> SLEEP`, then `Don't start proc X for broadcast`.
- **`whitelist_packagename` / `whitelist_appname`**: these strings appear in **none** of services.dex, OneKeyClear, KernelService, SettingService, BackcarService or CanAllInOne. They are probably read by `com.nwd.app.NwdTaskWhitelist`/`NwdWakeAppSelfStart` (framework.jar) or by the MemorySettingService/SystemUI recents [UNKNOWN]. The name and format (`[pkg,...]`) suggest the "whitelist" UI in the memory-clean or Recents settings, i.e. packages that survive kill-from-recents, restart, and self-start. Treat it as a probable exemption list, unconfirmed.
- **`kill_background_services`** [CODE]. This is an Allwinner AMS feature, unrelated to the NWD gate. It is read **once at boot** in `retrieveSettings()`, so a change needs a reboot.
  - When `1`: every activity pause/resume calls `checkPackage(pkg, ts)`, which tracks packages not matching the framework-res whitelist string-array (substring match; the resource name could not be resolved by JADX).
  - Every 60 s, if free+cached memory is below 9/8 of `getMemLevel(6)`, packages that are idle longer than a memory-scaled limit **and are listed in `Settings.System kill_background_services_list`** (comma-separated) are `forceStopPackage`'d.
  - Unlisted packages only have their timestamp refreshed. So with an empty list it does nothing apart from a "background cleanup" notification under very low memory (`android.settings.BACKGROUND_CLEANUP_LIST_SETTINGS`).
- `arm_auto_start_navi_when_acc_on` / `navigation_packagename` are used by KernelService, SettingService, Backcar, CAN and OneKeyClear. `navigation_packagename` is the navigation app auto-started on ACC on, and it is spared by OneKeyClear in state 3 (below). `persist.nwd.autowakeup` (SettingService and others) turns on the OneKeyClear `ForceSleepKill` list.

## 4. OneKeyClear (`com.nwd.android.onekeyclear`) [CODE]
Receiver `CustomMessageReceiver`:
- `com.nwd.ACTION_MEMORY_CLEAN` (user "one-key clean") → `ClearAllTask(1)` + `OneKeyClear(1)`.
- `com.nwd.action.ACTION_MCU_STATE_CHANGE` with `extra_mcu_state` (byte) ∉ {1,2} → wait `delay_time_kill_app` ms from `<config>/app/UartConfig.ini` (cap 3000; aborts if `Settings mcu_state` returns to 1) → `killAllApp(state)`:
  - state 0 (sleep / ACC off): `ClearAllTask(0)`, `OneKeyClear(0)`
  - state 3: `ClearAllTask(3)`, `OneKeyClear(3)`
- `ACTION_KILL_OTHER_APP` is defined as a constant but **not handled** here. The Recents kill is done by MemorySettingService.

Config file: `SystemProperties ro.nwd.config.path` (default `/config`) + `/app/OneClearConfig.xml`, **re-parsed on every broadcast**, so edits apply without a reboot. Tags (attribute `PackName`):
- `<ProcessItem PackName="..."/>` is the keep list. Name forms:
  - `pkg` matches exactly
  - `pkg*.*` matches by prefix (`processName.startsWith(pkg)`)
  - `powernotkill` + **one separator char** + name (the code strips the first 13 chars, e.g. `powernotkill_net.osmand`)
- `<ForceSleepKill PackName="..."/>`: killed on sleep anyway, only if `persist.nwd.autowakeup=enable`.
- `<NeedSleepKill PackName="..."/>`: always killed on sleep.

**`OneKeyClear(state)`** walks `getRunningAppProcesses()`, strips `:suffix`, and calls `ActivityManager.forceStopPackage(name)` via reflection unless kept. A force-stop puts the package in *stopped* state: no implicit broadcasts, alarms cancelled, no restart, until the user launches it.
- Top app is kept when state==1.
- Kept if it matches any `ProcessItem` entry (with or without `powernotkill`).
- state 0: the NWD source apps in `powertokill` (AUX, rear AUX, BT music, radio, `com.nwd.tv`, video) are killed even if listed. `NeedSleepKill`/`ForceSleepKill` entries are killed.
- state 3: anything whose name **contains `nwd`** is kept, and so is `Settings.System navigation_packagename`. Not in state 0!
- In AMS, protected packages (section 2) survive because forceStop is skipped.

**`ClearAllTask(state)`** walks `getRecentTasks(64, 2)` and calls `removeTask(persistentId)`, which kills the task's processes. It keeps **only `ProcessItem` entries containing `powernotkill`** (prefix match), plus the top app when state==1. In state 3 the "contains nwd" branch only logs and does not set `bFind`, so NWD tasks are removed too. This looks like an OEM bug. Afterwards it broadcasts `com.nwd.ACTION_FRAMEWORK_CLEAN_SUCCESS` (`extra_clean_value=4`).

OneKeyClear does **not** read `whitelist_packagename`: the string is absent from its dex. Its only exemption sources are the XML, "contains nwd" (state 3), `navigation_packagename` (state 3) and the AMS protect list.

## 5. Practical guidance for Dacia Clock, OsmAnd, Open Headunit

Read-only checks first (none run yet):
```sh
adb shell cat /config/app/OneClearConfig.xml          # path = getprop ro.nwd.config.path
adb shell cat /config/app/UartConfig.ini | grep -i delay_time_kill_app
adb shell settings get system whitelist_packagename
adb shell settings get system whitelist_appname
adb shell settings get system kill_background_services_list
adb shell settings get system navigation_packagename
adb shell getprop persist.nwd.autowakeup
adb shell service list | grep -iE "whitelist|appselfstart"
adb shell dumpsys appselfstart ; adb shell dumpsys whitelist   # may print nothing
adb pull /system/framework/framework.jar ; adb pull /system/framework/arm/   # for com.nwd.app.* analysis
```

Low-risk changes, each with an undo. Per the workspace rules, every `settings put` or file write on the unit needs the user's OK first. Back up first: `cp /config/app/OneClearConfig.xml /sdcard/OneClearConfig.xml.bak`.

1. **OneClearConfig.xml keep list** (strongest evidence; covers both ClearAllTask and OneKeyClear). Add one line per app, copying the separator style the file already uses for `powernotkill` (13-char prefix):
   ```xml
   <ProcessItem PackName="powernotkill_net.osmand"/>
   <ProcessItem PackName="powernotkill_ma.aymane.daciaclock"/>
   <ProcessItem PackName="powernotkill_<openheadunit.package>"/>
   ```
   `/config` may be a read-only or separate partition. Remount rw as root, edit, then remount ro. No reboot needed.
   Undo: restore the `.bak` copy.
   Caveat: the `ForceSleepKill`/`NeedSleepKill` lists win on sleep. Do not list the apps there.
2. **`navigation_packagename=net.osmand`** (`settings put system navigation_packagename net.osmand`). OsmAnd is then auto-started on ACC on when `arm_auto_start_navi_when_acc_on=1`, and spared by OneKeyClear state 3. It may also be honoured by the NWD self-start gate [UNKNOWN].
   Undo: put back the previous value (record it first).
3. **`whitelist_packagename` / `whitelist_appname`**: append the packages in the existing `[a,b,...]` format, keeping both lists index-aligned. This is probably the Recents/memory-clean whitelist used by MemorySettingService/NwdTaskWhitelist [UNKNOWN; check by swiping afterwards and looking for "process is bad"].
   Undo: restore the saved string.
4. **`kill_background_services`**: only matters if `kill_background_services_list` contains these apps. If it does, remove them from that list (no reboot needed; there is a ContentObserver). Leave `kill_background_services` as is.

Behavioural rules for the apps (no config needed):
- **Do not swipe them from Recents**, and do not let Back go unhandled three times in a row (PhoneWindowManager kills the app). Either one leads to the "bad until opened" state.
- After a kill, recover by launching an **activity** (`am start -n pkg/.Activity` or the launcher). Broadcasts and `startService` are refused by the gate until then.
- While asleep (`sys.nwd.mcu.state` 0/3): keep `BroadcastReceiver`s short (timeouts kill the process), avoid long `startService` chains, and expect no process start for `broadcast`/`service` from non-whitelisted apps.
- Prefer a foreground service plus an ongoing notification in a process that is already running, rather than relying on BOOT_COMPLETED or alarms to start a dead process.
- Re-arm alarms and jobs on `com.nwd.ACTION_AFTER_BOOT_COMPLETED` / ACC-on, because a force-stop (OneKeyClear) cancels them.
- For Open Headunit (USB/AA) and Dacia Clock: listen for `com.nwd.action.ACTION_MCU_STATE_CHANGE` (`extra_mcu_state` byte: 0 = sleep, 1 = wake, 3 = ?) only while the process is alive. It cannot wake a dead process during the gate.

Not recommended: patching `ProcessList.protect_apps` or framework classes, or disabling OneKeyClear (it also handles CVBS out and source switching on sleep). See the debloat policy.
