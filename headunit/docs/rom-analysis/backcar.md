# Reverse camera service (`com.nwd.backcar`): static analysis

Source: `A20CarkitBackcarService.apk` (versionName 1.0.5, versionCode 105, targetSdk 19, `persistent=true`, `debuggable=true`), decompiled with JADX 1.5.6 into
`C:/Users/tool/AppData/Local/Temp/claude/6b7652970bbc1e95/43be6918-0825-4022-ac3c-f7133a29ab37/scratchpad/rom-analysis/backcar/`.
I also cross-checked the already decompiled `8925_Carkit_KernelService` (`rom-analysis/mcu/kernel`), CanAllInOne (`rom-analysis/can`) and ran a string scan of `framework/services.odex`.
This is static analysis only. Nothing was run on the device.

Confidence tags: **[code]** means read directly in decompiled code. **[xref]** means read in another pulled APK. **[inferred]** means a reasoned guess that has not been verified.

---

## TL;DR

* The reverse state comes from the **MCU over UART**. The CAN box is not involved. KernelService parses public protocol data type `5`, then calls `DeviceState.saveBackCarState()`. That method writes `Settings.System mcu_backcar_state` and broadcasts `com.android.action.ACTION_BACKCAR_STATE_CHANGE` with a **byte** extra `extra_back_state`. **[xref]**
* While the camera is showing, the service polls `android.os.Hardware.getReverseCarState()` every 10 ms. This is a framework/native call, most likely backed by the kernel `car_reverse` driver. When it reads 0, the service puts a **black mask** over the image right away and **closes the camera** after about `backcar_delay_exit_time` (5) seconds. **[code]** So a faked broadcast alone gives you a black screen for about 5 s and nothing more.
* The receiver reads the extra with `getByteExtra`. On Android 6, `am broadcast --ei extra_back_state 1` sends an **int**, and `Bundle.getByte` falls back to the default 0, which logs `backcarState = 0`. `am` cannot send a byte extra at all. **[code]**
* `backcar_can_control` has **no effect on reverse detection**. It only stops `FactoryConfig.ini` from overwriting `factory_backcar_track_switch` (the dynamic guide lines). The CAN settings UI sets it to 1 whenever you change a CAN config option. **[code+xref]**
* `persist.sys.backcar.di.enable` does not appear in any pulled APK or in services.odex. Its effect is unknown. **[uncertain]**
* The camera is shown in a **WindowManager overlay**, not an Activity. The window type is `2033` (NWD "TYPE_TOP_LAYER_3"), it is MATCH_PARENT, it is focusable, and it holds a `SurfaceView`. On T3 with `camera_ccd=3` the service calls `Camera.open(6)`.
* Both the MCU state and the kernel node read 0, and the CAN profile has no reverse support. The most likely cause is **physical**: the head unit's reverse input wire is not getting +12 V from the reverse lamp circuit. **[inferred]**

---

## 1. Where the reverse state comes from

### 1.1 Primary source: MCU, then KernelService, then broadcast  [xref: KernelService]

```
PublicProtocalUtil (data type switch)
    case 5: getMCUBackCarState(protocal, callback);       // MCU UART frame, public protocol, type 5
getMCUBackCarState(): byte state = data[offset]; callback.onGetBackcarState(state);
MCUDeviceManager.onGetBackcarState(byte) -> AbsDeviceManager.onBackcarStateChange(byte)
    -> DeviceState.saveBackCarState(ctx, state)
```

`DeviceState.saveBackCarState` (the shared library copy, the same in every NWD APK):

```java
if (mBackcarState != b) { mBackcarState = b;
    SettingTableKey.writeDataToTable(cr, "mcu_backcar_state", b); }
if (mMcuState != 3 && mMcuState != 0) {               // MCU must be "running"
    BACK_CAR_STATE_CHANGE_INTENT.putExtra("extra_back_state", b);   // <-- byte
    context.sendBroadcast(BACK_CAR_STATE_CHANGE_INTENT);
} else if (mBackcarState == 0) { ...broadcast 0 only... }
```

So `mcu_backcar_state=0` means **the MCU itself reports "not reversing"**. The MCU reads the harness reverse/"BACK" input. Whether the Xinpo CAN box can also feed reverse to the MCU depends on the CAN protocol. This profile says `has_reverse_control_system=false`, so assume it does not. **[inferred]**

### 1.2 The consumer: `BackcarManager.mReceiver`  [code]

```java
if (ACTION_BACKCAR_STATE_CHANGE.equals(action)) {
    if (mbSdMount) {                                  // gate 1: /mnt/sdcard present (or 30 s timeout)
        if (is360BackCar(mContext)) { hideBackcar(); return; }   // gate 2
        byte backcarState = intent.getByteExtra("extra_back_state", (byte) 0);   // BYTE!
        LOG.print("backcarState = " + backcarState);
        if (backcarState == 1) { ...; showBackcar(); return; }
        if (isBakcarAutoSwitchFront(ctx)) { if (isBackcarShowing) switchFrontView(true,true); return; }
        hideBackcar();
    }
}
```

Gates that can suppress the camera even when the state is 1:

| Gate | Where | Effect |
|---|---|---|
| `mbSdMount` | `MSG_CHECK_SDCARD` (8) / timeout (9) | The receiver is only registered after `ro.nwd.internal.path` (`/mnt/sdcard`) has a listing, or after 30 s. |
| `is360BackCar()` | `FeatureSettingUtil.isFeatureOn(ctx,16)` **and** `/system/app/com.nwd.carkit360camera[.apk]` exists (unless listed in `/cache/Carkit_Uninstall.ini` or `/config/app/Carkit_Uninstall.ini`) | The service always hides and leaves reverse to the 360 app. `factory_feature & 16`. |
| `mbHDBackCarRecord` | `loadConfig()`: `hdtype == 4 && camera_rightview != 6` | `showBackcar()` returns early ("is need hd backcar record"). |
| `ACTION_MCU_STATE_CHANGE` with `extra_mcu_state` 0 or 3 | receiver | `hideBackcar()` |
| `com.nwd.action.360.state` with `extra_show_state>0` | receiver | `hideBackcar()` |
| `getPowerOnReverseState()==1` | `openCamera()` | "os backcar, later retry": the kernel fast-reverse is still holding the camera, so the service retries every 500 ms. |

At startup `checkBackcarStateWhenFirstStart()` also reads `Settings.System mcu_backcar_state` and shows the camera if it is 1.

### 1.3 Secondary check: `Hardware.getReverseCarState()` (kernel)  [code]

`showBackcar()` starts the `mrunnable` thread on T3 (`mPlatform==3`):

```java
int counttime = BackcarSettingUtil.getBakcarDelayExitTime(ctx) * 100;   // 5 -> 500
while (isBackcarShowing) {
    int state = 1;
    Object obj = ReflectUtil.invokeStatic(Hardware.class, "getReverseCarState", null, null);
    if (obj != null) state = (Integer) obj;
    if (state != 0) { hide black mask (MSG 7); }
    else {
        if (!autoSwitchFront) show black mask (MSG_SHOW_BLACKVIEW 6);
        count++;
        if (count >= counttime) mHandler.sendMessage(obtainMessage(5, (byte)0));  // -> hideBackcar()
    }
    Thread.sleep(10);
}
```

* `android.os.Hardware` is an NWD framework class. It is not in the APK, and framework.jar was not pulled. **[inferred]** `getReverseCarState` very probably reads the `car_reverse` driver (`/sys/devices/soc.0/car_reverse.14/state`). services.odex also contains `/sys/class/disp/disp/attr/reverse_status`, `readReverseState`, `android.hardware.car.reverse.state` and `"MSG_START_OTHER_APP_SERVICE, call Hardware.disableCarReverse()"`, which means the system server turns off the kernel fast-reverse once apps start.
* The consequence: **the MCU broadcast opens the window, but the kernel GPIO decides whether it stays open.** If the node reads 0, the image is blacked out at once and the window closes after about 5 s plus loop overhead.
* Other `Hardware` methods it calls by reflection: `getPowerOnReverseState`, `setBackCarExit` (subPlatform 4 only), `setReverseAuto` (SDK<21 only), `setAGCConfig`, `setSmoothLevel`, `setModeFmt`, `getDisplayChipType`, `getT3CVBSSignalState`. It also calls `NwdDeviceConfig.setAnalogSwitch`, `Camera.getCameraOpenState` and `Camera.setAnalogInputColor`.

### 1.4 What `backcar_can_control` does (0 vs 1)  [code+xref]

`BackcarManager` never reads it. It is used only in `BackcarSettingUtil`:

```java
public static void loadCurSetting(Context c) {
    int track = factoryConfig.getPropertiesInteger(KEY_BACKCAR_TRACK);
    mbCanControl = getIntValue(cr, "backcar_can_control") > 0;
    if (track != -1 && !mbCanControl) writeDataToTable(cr, "factory_backcar_track_switch", track);
    ...
}
public static void setBackcarTrackSwitch(Context c, int i) {
    ... if (mbCanControl) return;      // CAN app owns the track switch
    writeDataToTable(cr, "factory_backcar_track_switch", i); sendBackcarSettingBroadcast(c);
}
```

CanAllInOne `ConfigSettingActivity` (the checkbox listener) and `AliosCarFunctionActivity` call `CanConfigUtil.setBackcarCanControl(ctx, true)` after **any** CAN config toggle.

The keys behave like this:
* **1:** the CAN app owns `factory_backcar_track_switch` (the dynamic trajectory lines driven by `com.nwd.action.steerwheel_angle`), and the factory INI and factory UI cannot overwrite it.
* **0:** the factory INI and factory UI may set it.
* Neither value affects whether reverse is detected.

### 1.5 `persist.sys.backcar.di.enable`  [uncertain]

This string does not appear in the backcar APK, KernelService, SettingService, CanAllInOne, OneKeyClear or `services.odex`. The only related symbol is the constant `FactorySettingConstant.CMD_SET_BACKCAR_DI_ENABLE = "CMD_SET_BACKCAR_DI_ENABLE"`, which is a factory-service command whose handler is in an APK that was not pulled (probably the factory settings app). It might be a native, HAL or `car_reverse` driver switch ("DI" could mean de-interlace or digital input). That is unverified. **Do not change it blind.** If you want to know more, pull `/system/framework/framework.jar` (or its odex) and the factory APK and grep them for the string.

---

## 2. How the camera is shown

### 2.1 Window, not activity  [code: `BackcarManager.initWindowAttr()` / `showBackcar()`]

```java
mWindowLayoutParam.width  = -1;                        // MATCH_PARENT
mWindowLayoutParam.height = isLongScreen() ? mWindowHeight /*415*/ : -1;   // long = height>=1024
mWindowLayoutParam.format = 1;                         // PixelFormat.RGBA_8888
mWindowLayoutParam.type   = KernelConstant.TopWindowType.TYPE_TOP_LAYER_3();   // 2033
mWindowLayoutParam.flags  = 201328128;                 // 0x0C000600
mBackcarView = View.inflate(ctx, R.layout.view_backcar, null);   // SurfaceView @id/backcar_video_show
...
mWindowManager.addView(mBackcarView, mWindowLayoutParam);        // in showBackcar()
mWindowManager.removeView(mBackcarView);                         // in hideBackcar()
```

* Flags `0x0C000600` = `FLAG_LAYOUT_NO_LIMITS | FLAG_FULLSCREEN | FLAG_TRANSLUCENT_STATUS | FLAG_TRANSLUCENT_NAVIGATION`. `FLAG_NOT_FOCUSABLE` is **not** set, so the window takes key focus and all touches. A tap toggles the channel UI, and a long press opens the brightness/contrast panel.
* The activation dialog (`ActivateManager`, HD cameras only) uses type `2035` (TYPE_TOP_LAYER_5).
* NWD's `KernelConstant.TopWindowType` defines 2031–2035 as TYPE_TOP_LAYER_1..5, and 2036/2037 as TOP/BOTTOM_MENU_LAYER.
* Preview path: `Camera.setPreviewDisplay(mSurfaceHolder)`, then `stopPreview()` and `startPreview()` (T3 implementation). The image goes to a SurfaceView, which is a separate HW overlay layer.
* `TestActivity` is the only activity. It is a developer test screen (`Camera.open(1)` plus `setAnalogInputState`), exported through a MAIN/DEFAULT intent filter. Normal operation does not use it.

**Would something cover it?**
* A **fullscreen activity: no.** Any system window type above 2000 sits above the application layers.
* A **TYPE_SYSTEM_ALERT (2003) window: uncertain.** In stock AOSP 6.0, 2033 is `TYPE_VOICE_INTERACTION_STARTING`, which ranks **below** TYPE_SYSTEM_ALERT in `PhoneWindowManager.windowTypeToLayerLw`. NWD names these types "TOP_LAYER", which suggests they changed the layer mapping in PhoneWindowManager. I could not confirm that from the odex strings. **[uncertain]** If you need a guaranteed cover, test it, or use type 2034/2035, which are probably higher on this ROM.

### 2.2 Video input selection  [code]

`loadConfig()` takes these values from **UpdateConfig.ini**, not from Settings: first `/mnt/sdcard/backups/UpdateConfig.ini` if it exists and is non-empty, otherwise `/config/app/UpdateConfig.ini`. It then **writes** them into `Settings.System`:

```java
mCameraId      = props.camera_ccd        ?: 3;   // 2 => mis6502=true
mCheckSignalId = props.video_signal_ccd  ?: 3;
hdtype = FactoryConfigSetting.getPropertiesInteger("persist.sys.nwd.hacamera.ctrl"); // key in FactoryConfig.ini
if (hdtype > 0) { mCameraId = 5; mCheckSignalId = 5; mbHDBackCar = true; }
...
writeDataToTable(cr, "camera_ccd", mCameraId); writeDataToTable(cr, "video_signal_ccd", mCheckSignalId); ...
```

This means **editing `Settings.System camera_ccd` or `video_signal_ccd` does nothing lasting**, because they are overwritten on every service start. The one exception is `switchFrontView()`, which reads them back from Settings at runtime.

T3 mapping (`T3CvbsInCameraManager`, `T3VideoSignalChecker`):

| camera_ccd / video_signal_ccd | `Camera.open(n)` | signal check `Hardware.getT3CVBSSignalState(n)` | meaning (per checker constants) |
|---|---|---|---|
| 0 | 5 | – | |
| 1 | 4 | 4 | CVBS AUX |
| 2 | 7 | 5 | (6502 mode) |
| **3 (current)** | **6** | **6** | **CVBS BACKCAR** |
| 4 | 4 | 7 | CVBS DVR |
| 5 | 2 | 2 | HD BACKCAR |
| 6 | 3 | 3 | HD FRONT |

Defaults for the front camera are `camera_ccd_front=1` and `video_signal_ccd_front=1`. The right-view defaults are `camera_rightview=2` and `video_signal_rightview=4`. Also on T3: `resumeDefaultChannel()` calls `NwdDeviceConfig.setAnalogSwitch(1)` at startup, unless `getPowerOnReverseState()==1`.

Image settings come from `/config/app/UartConfig.ini`: `key_brightness[_avm]`, `key_contrast`, `key_color`, `key_saturation`, `key_hd_*`, `key_window_height`, `key_window_y`, `assistline_bottom`, `backcar_fmt`, `show_angle`, `backcar_setting_enable`, `backcar_show_black_background`, `is_show_change_video_channel`, `backcar_delay_exit_time`, `backcar_auto_switch_front`, `backcar_auto_switch_front_fun`. `FactoryConfig.ini` supplies `key_agc_enable`, `smooth_level`, `key_backcar_video_mode` and `key_avm_enable`.

Note: JADX decompiles the loops in `getBackcarVideoType()` badly. As written it always returns 0. The real logic maps `persist.sys.nwd.hacamera.ctrl` (none/pr1000/nvp6114/nvp6124/nvp-two/pr2000) to an index.

---

## 3. All settings keys, broadcasts and intents

### Settings.System keys read (R) / written (W) by this APK

| Key | R/W | Purpose |
|---|---|---|
| `mcu_backcar_state` | R | Shows the camera at service start if 1 (written by KernelService). Also read by ActivateManager. |
| `mcu_state` | R | Camera error 100 handling: only reopens if not 0. |
| `mcu_System_setting_state` | R | Bit `0x200` makes the camera use `setDisplayOrientation(360)` on channel 0 (mirroring). |
| `nwd_cvbs_out_type` | R | Restores CVBS-out after reverse. |
| `camera_ccd`, `video_signal_ccd`, `camera_ccd_front`, `video_signal_ccd_front` | W (start), R (front switch) | Video channel selection, see 2.2. |
| `camera_rightview`, `video_signal_rightview` | W | Right-view camera. |
| `backcar_delay_exit_time` | R/W | Grace time in seconds after the kernel reverse reads 0. If -1 it is taken from UartConfig.ini (default 5). |
| `backcar_auto_switch_front`, `backcar_auto_switch_front_fun` | R/W | After reverse, switch to the front camera instead of closing. |
| `backcar_fmt_mode` | R/W | HD format auto/AHD/TVI (Qualcomm platform only). |
| `key_angle_display`, `key_angle_state` | R | Multi-angle buttons. |
| `backcar_assist_line_bottom` | R/W | Position of the guide lines. |
| `factory_feature` | R | Bit 16 enables 360-camera mode, which suppresses this service. |
| `factory_backcar_track_switch` | R/W | Dynamic trajectory lines (steering angle). |
| `factory_backcar_assist_line_switch` | R/W | Static guide lines. |
| `backcar_can_control` | R/W | Only protects the track switch, see 1.4. |
| `factory_backcar_radar_switch` | (util) | Radar overlay. |
| `mcu_hand_break` (`HAND_BREAK`) | (lib) | Not used for reverse. |

`car_config_door_need_reverse` and `persist.sys.backcar.di.enable` are **not referenced** by this APK.

### Broadcasts received
* `com.android.action.ACTION_BACKCAR_STATE_CHANGE`: extra `extra_back_state` (**byte**) 0/1
* `com.nwd.action.ACTION_MCU_STATE_CHANGE`: `extra_mcu_state` (byte). 0 or 3 hides the camera.
* `com.nwd.ACTION_OS_SLEEP` (registered, no handler branch)
* `com.nwd.ACTION_VIDEO_CHANNEL_CHANGE`: `extra_video_channel` (byte)
* `com.nwd.action.360.state`: `extra_show_state` (int). A value >0 hides the camera.
* `action.backcar.angle.state`: `extral_angle_value`, `extral_angle_assistline_open`
* `action.backcar.activation.mode`
* AssistLineManager: `com.nwd.action.steerwheel_angle`, `com.nwd.ACTION_INIT_FACTORY_SETTING_SUCCESS`, `com.android.action_update_backcar_setup`
* RadarManager: `com.nwd.can.action_show_radar`
* Signal checker: `android.hardware.tvd.state.change`
* Boot: `android.intent.action.BOOT_COMPLETED` starts `BackcarService`. You can also start it explicitly with action `com.nwd.ACTION_START_BACKCAR_SERVICE` (the service is exported).

### Broadcasts sent
* `com.nwd.backcar.action_disable_fast_reverse_car` (on service create)
* `com.nwd.ACTION_CANCEL_MCU_UPDATE` (on show)
* `com.nwd.ACTION_SET_CVBS_OUT_STATE` with `extra_cvbsout_state` 0/1
* `com.nwd.action.ACTION_SET_SYSTEM_PROP` with `extra_prop_name`/`extra_prop_value`: `camera_in_backcar` (subPlatform 4), `sys.hwc.forcegpu` (subPlatform 6), `persist.sys.nwd.hacamera.ctrl`
* `com.nwd.ACTION_CHANGE_VIDEO_CHANNEL`, `action.backcar.closecamera`, `action.backcar.angle`
* `com.nwd.ACTION_BACKCAR_VIDEO_SIGNAL_STATE` (`state` boolean)
* `com.nwd.can.setting.service.ACTION_CAR360_CAMERA_POINT` (touch coordinates)

---

## 4. Steps to make reverse work, ranked by risk

1. **Check the reverse input wire. This is the likely root cause and has no software risk.**
   The MCU (`mcu_backcar_state`) and the kernel (`car_reverse.14/state`) both read 0, and the CAN profile has no reverse support. The head unit therefore needs +12 V on its "BACK"/"REVERSE"/"REV" harness pin. On many Chinese harnesses this wire is pink or purple, but **check against the unit's own harness label**. Feed it from the reverse lamp supply. Many Dusters without a factory camera have no reverse signal at the radio connector, so you may have to run a wire, or use a relay or diode from the reverse lamp feed.
   Verify, read-only: watch `settings get system mcu_backcar_state` and `cat /sys/devices/soc.0/car_reverse.14/state` while in reverse. Both should become 1.
   Undo: disconnect the wire.
2. **Rule out the software gates (read-only checks):**
   * `factory_feature & 16` must be 0, or the 360 app must not be installed.
   * `persist.sys.nwd.hacamera.ctrl` in `/config/app/FactoryConfig.ini` or `/mnt/sdcard/backups/FactoryConfig.ini` must be absent or 0 for a CVBS camera. A value of 4 (HD record mode) suppresses the display completely.
   * `camera_ccd` in UpdateConfig.ini must be 3 or absent (CVBS backcar, `Camera.open(6)`).
   * Logcat should show `initbackcar()` (sdcard gate passed) and `mMcuState` not 0 or 3.
   Undo: nothing to undo.
3. **Test the display path without a reverse signal (low risk, temporary):**
   * You cannot use `am broadcast --ei`. It sends an int and is read as 0.
   * Use a tiny helper app that sends `ACTION_BACKCAR_STATE_CHANGE` with `putExtra("extra_back_state", (byte)1)`. It needs no special permission because the broadcast is unprotected. **[inferred, check that it is not a protected broadcast on this ROM]** Expect the overlay to appear. Because `getReverseCarState()==0`, you will get a black mask and an automatic close after about 5 s. That still proves the window and service path works.
   * Installing an APK needs the user's approval (workspace rule). Do **not** write a camera-preview test app: the workspace rules forbid writing a reverse-camera app. The built-in `com.nwd.backcar/.TestActivity` opens `Camera.open(1)`, which is not the backcar channel on T3, so it does not prove the reverse input either.
   Undo: uninstall the helper app. Send `extra_back_state=(byte)0` or wait 5 s.
4. **Raise `backcar_delay_exit_time` only for testing** (for example `settings put system backcar_delay_exit_time 30`). This only extends the black-mask grace period. It cannot keep a live image open while the kernel reads 0.
   Undo: `settings put system backcar_delay_exit_time 5`.
5. **Do not change `backcar_can_control`.** It has no effect on detection. 1 is the correct value after CAN config.
   **Do not change `persist.sys.backcar.di.enable`** until its consumer is identified. It is not in any analysed code.
   **Do not edit `Settings.System camera_ccd`/`video_signal_ccd`**. They are overwritten from UpdateConfig.ini at every start. To change them, edit UpdateConfig.ini after backing it up. Undo: restore the backup.
6. **Ruled out:** a software-only reverse (your own overlay or camera app triggered by CAN/OBD data). The workspace rules forbid it, the CAN profile has no reverse support, and the stock service fights any injected state through its kernel poll.

### Consequence for DaciaClock (reverse check)

The `Reverse` class in DaciaClock v16 polls `persist.sys.reverse`, `sys.backcar`, `persist.sys.backcar` and `sys.reverse.state`. None of these appears in the backcar service or in KernelService. The authoritative state on this ROM is **`Settings.System mcu_backcar_state`** (1 = reverse), written by KernelService. The live event is the broadcast `com.android.action.ACTION_BACKCAR_STATE_CHANGE` (byte extra `extra_back_state`). A secondary signal is `/sys/devices/soc.0/car_reverse.14/state`. **[code+xref]** A v17 should read `Settings.System.getInt(cr, "mcu_backcar_state", 0)` (no permission needed), and could also check the sysfs node. Verify both with `scripts/reverse-props.sh` once the reverse wire works.

The camera overlay is type 2033. DaciaClock's 1×1 invisible `TYPE_SYSTEM_ALERT` probe draws nothing, so even if it ranks above 2033 it does not hide the camera.

### Open items
* Confirm what `android.os.Hardware.getReverseCarState` and `getPowerOnReverseState` read. Pull `/system/framework/framework.jar` (or its odex/oat) and decompile it.
* Find out which component uses `persist.sys.backcar.di.enable` and `CMD_SET_BACKCAR_DI_ENABLE`. Pull the factory settings APK and grep `/system/lib*` and `/vendor` for the string.
* Confirm the layer order of window type 2033 against TYPE_SYSTEM_ALERT on this ROM (PhoneWindowManager in `services.odex` or `android.policy`).
