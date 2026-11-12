# Nothing-Launcher-Plus: System UI / Settings / HeliBoard / framework dependency map

> **Status (0.70.21):** acted on: the lock screen rotation installs its fingerprint parts first
> and turns only with them in, each further part on its own; System UI's newer designs
> (sceneContainer, statusBarRootModernization, composeBouncer, qsUiRefactor/qsNewTiles) are
> read at start and the features built for the older ones are left out while one is on.
> Still open: the Bluetooth tile colour, the Battery filter on Nothing's card widgets (phone
> check), the Task manager's obfuscated Settings names (resolver), the other half-installed
> units, the dead Battery attach hook and Areas' SystemIcons check.

Read-only audit. No module file was edited. Date: 2026-11-11.

Module source: `app/src/main/java/com/dressence/nothinglauncherplus`

## How it was checked

- **Dex as ground truth.** Every class, method and field was checked against the dex files themselves, not only against jadx. jadx's `sui2/full` decompile is missing classes: it contains no `com/android/systemui/qs/tileimpl/` at all. `dexdump` (build-tools 36) was run over all 15 System UI dexes, the 3 Settings dexes, the HeliBoard 4.2 release dex and the framework dumps (`audit_fw_classes*.dex.txt`). The results were written to signature tables (`re/sig/{sui,settings,hb,fw}.tsv`: class, superclass, interfaces, fields and methods with descriptors). `re/chk.py` resolves each dependency up the superclass chain. The query lists are `re/q_sui.txt`, `re/q_fw.txt` and `re/q_hb.txt`.
- **Resource names** were checked against `sui_res/dec/resources/res/values/public.xml` and `layout/status_bar.xml`.
- **Behaviour** (what a method does, overrides, flag reads) was read in jadx: `sui2/full/sources`, `settings/out/sources` and `si/out/sources` (Settings Intelligence), and in the HeliBoard source tree (4.3-beta1).
- **Not verifiable here:**
  - `com.nothing.ntgamedashboard` (GameBar): no APK or decompile is in the scratchpads.
  - `RoleManager` and `DeviceConfig`: they live in mainline APEX jars (framework-permission-s and configinfrastructure), not in framework.jar.
  - Anything that needs the phone (how things look, runtime ordering).

Column key for the tables:

- **obf?**: the name is obfuscated or compiler-generated (R8 short names, `$N` anonymous classes, `lambda$x$N`, Kotlin `$internal` mangling, Kotlin backing fields).
- **match**:
  - OK: present with the expected signature.
  - OK-inh: inherited, found on a superclass.
  - MISS: not present.
  - SEM: present, but behaves differently from what the module assumes.
- **anchor**: a stable way to find the dependency again if it is renamed.
- **failure** (how the module degrades if the dependency is missing):
  - SKIP: clean skip. `Hooks.install` logs it and the feature is simply off.
  - SILENT: `hookAllMethods` matches nothing, returns an empty set and nothing is logged.
  - HALF: half-applied. Earlier hooks of the same feature stay installed and later ones do not.
  - RT-LOG: a runtime throw inside a guard or hook. It is logged on every call.

---

## 1. Which process runs what (from `xposed/ModuleEntry.java`)

(0.70.35: `ModuleEntry.java` became `xposed/HookLoader.kt`, called by both API entries; the line
numbers below are the old file's.)

| Host process | Entry (ModuleEntry line) | Module code | Condition |
|---|---|---|---|
| `com.android.systemui` | `SystemUi.install` (line 101) | all of `systemui/` except FullScreenApps' app half, plus `perf/HudWindows` | not "Launcher only"; HudWindows only on `Device.isCmfPhone1()` (SystemUi.java:30) |
| `com.android.settings` | lines 79-85 | `SettingsTile` (always), `TaskManager` (not "Launcher only") | first application |
| `com.android.settings.intelligence` | lines 86-89 | `SearchHook` | always |
| `helium314.keyboard` | lines 107-111 | `HeliBoardGamepad`, `HeliBoardFullWidth` | not "Launcher only" |
| `com.nothing.ntgamedashboard` | lines 112-113 | `perf/GameBar` | **CMF Phone 1 only**. Runs in Nothing's Game Dashboard app, not in System UI. |
| any other scoped app | lines 114-125 | `FullScreenApps.install` (the app half), `GameUnlock` | per-app setting |

`HudWindows` hooks `com.android.systemui.SystemUIApplication.onCreate()` (HudWindows.java:82). It only acts when `Application.getProcessName()` is `com.android.systemui`, so it does not act in System UI's secondary processes (`:screenshot` and others).

---

## 2. System UI dependencies

The decompiled build is Nothing OS, MTK flavour. Kotlin `internal` names end in `$vendor__noth__packages__apps__NothingSystemUI__android_common__MtkSystemUI_core`.

### 2.1 Status bar texts and icons (Led, Clock, Battery, GreenIcons, GreenTexts, NetworkSpeed, Areas)

| Module file:line | Uses | obf? | Match | Stable anchor | If missing |
|---|---|---|---|---|---|
| Led.java:54 | `TextView.setTextColor(int)` (framework) | no | OK | SDK | SKIP ("lit texts") |
| Clock.java:21,31 | class `com.android.systemui.statusbar.policy.Clock` | no | OK | inflated from `status_bar.xml` id `clock`; extends TextView, implements DarkReceiver | SKIP |
| Clock.java:43-45 | `Clock.onAttachedToWindow()`, `onDarkChanged(ArrayList,float,int)`, `updateClock()` | no | OK (declared) | DarkIconDispatcher.DarkReceiver.onDarkChanged | SILENT per method |
| Clock.java:46 | `Clock.getSmallTime()` returning CharSequence | no | OK | AOSP name; the only CharSequence-returning no-arg method of Clock | SILENT (no date before the time) |
| Clock.java:73-90 | parent class or id names containing "header", ".qs.", "shade" | heuristic | OK | `quick_status_bar_expanded_header.xml` / `combined_qs_header.xml` | wrong guess: the date shows in the header |
| Battery.java:19,37 | class `com.android.systemui.battery.BatteryMeterView` (extends LinearLayout) | no | OK | `battery_meter_view` in status_bar_system_icons; AOSP | HALF: the View.onAttachedToWindow filter is installed first (line 36), then findClass throws, so the green layer applies with no percent styling |
| Battery.java:39 | `updateColors(int,int,int)`, arg index 1 = background | no | OK (III)V | AOSP | SILENT |
| Battery.java:61-63 | `updateShowPercent()`, `updatePercentText()`, `onDarkChanged(ArrayList,float,int)` | no | OK | AOSP | SILENT |
| **Battery.java:64** | **`BatteryMeterView.onAttachedToWindow`** | no | **MISS: not declared** (superclass LinearLayout); hookAllMethods only sees declared methods | none needed; the View.onAttachedToWindow hook at line 149 already sees it | SILENT: no percent lighting at attach (it is lit at the next `onDarkChanged`) |
| Battery.java:70 | `onPowerSaveChanged(boolean)` | no | OK | BatteryController.BatteryStateChangeCallback | SILENT |
| Battery.java:87,103,129 | fields `mBatteryPercentView` (TextView), `mPowerSaveEnabled` (boolean) | no | OK | find by type: the only TextView field, the boolean set in onPowerSaveChanged | RT-LOG / guarded (NoSuchFieldError caught) |
| Battery.java:149-165 | `View.onAttachedToWindow` on every view whose class name contains "Battery" | heuristic | **SEM, probable**: also matches `com.nothing.cardwidget.battery.view.Battery{Cell,Container,SmallContainer}View` and `com.nothing.cardparser...SimulatedBatteryContainerView` (Nothing's battery card widgets, `battery_container.xml`). It also matches `BatteryStatusChip` and AOSP `statusbar.pipeline.battery.shared.ui.BatteryFrame/Glyph` once they are used | restrict to `BatteryMeterView` and its subclasses (e.g. `com.nothing.systemui.battery.BatteryMeterViewEx`), or to views under `status_bar_contents` / the QS header | the green filter on Nothing's widget battery (needs a phone check) |
| GreenIcons.java:51 | `ImageView.onDraw(Canvas)` (framework) | no | OK | SDK | SKIP |
| GreenTexts.java:26 | `TextView.onDraw(Canvas)` | no | OK | SDK | SKIP |
| NetworkSpeed.java:26 | parent class name contains "NetworkSpeed" | heuristic | OK: `com.nothing.systemui.statusbar.policy.networkspeed.NetworkSpeedView` | Nothing class, in a layout | text drawn through a glow (cosmetic) |
| Areas.java:41-63 | class simple names "DateView", "Battery", "QSTile", "QSIconView", "TileView", "StatusBar", "StatusIcon", "SystemIcons", "ShadeHeader", "QuickStatusBarHeader", "NotificationIcon" | heuristic | OK: `VariableDateView`, `QSIconViewImpl`, `QSTileViewImpl` and subclasses, `QuickStatusBarHeader`, `StatusIconContainer`, `NTStatusBarContentExt`, `PhoneStatusBarView`. (No class contains "SystemIcons"; that check is dead but harmless) | resource ids of the roots: `status_bar_contents`, `quick_status_bar_system_icons`, `qs_header`… | items not lit or wrongly lit (cosmetic) |

### 2.2 Quick settings (QuickSettings, TileBackground)

| Module file:line | Uses | obf? | Match | Stable anchor | If missing |
|---|---|---|---|---|---|
| QuickSettings.java:29,57 | `com.android.systemui.qs.tileimpl.QSTileViewImpl.getBackgroundColorForState(int,boolean)` | no (Kotlin; `$default` static twin exists and is not hooked) | OK (IZ)I. **SEM:** see below | AOSP name; superclass `plugins.qs.QSTileView`; sibling `getLabelColorForState(IZ)I` | SILENT (count logged). The TileBackground fallback takes over while `litAtSource` is false |
| QuickSettings.java:81 | `AnimatedVectorDrawable.forceAnimationOnUI()` (hidden, @UnsupportedAppUsage) | no | OK ()V | framework, unchanged since API 24 | SKIP ("tile animations"): icons freeze at their end frame |
| QuickSettings.java:82 | `ImageView.setImageDrawable(Drawable)` | no | OK | SDK | SKIP |
| QuickSettings.java:129 | parent simple name contains "TileView" | heuristic | OK | | |

**Current mismatch (Bluetooth tile).** `com.nothing.systemui.qs.tiles.bt.BluetoothTileViewImpl.getBackgroundColorForState(IZ)I` overrides the base method and does not call `super`. It returns `qs_tile_active_background_color` itself (jadx `BluetoothTileViewImpl.java:2331`), so the hook never sees it.

`RingerModeTileViewImpl` calls `super` only when it is 1x1. The global `litAtSource` flag goes true on the first ordinary tile, and that also switches off the TileBackground fallback for every tile. As a result an active Bluetooth tile stays Nothing's white in the green or amber styles.

Fix: also hook the subclasses (find them by `S\t…\tQSTileViewImpl;`), or make `litAtSource` per view.

### 2.3 Dual status bar (DualStatusBar.kt) and status bar saver (StatusBarSaver.kt, FullScreenApps.kt)

| Module file:line | Uses | obf? | Match | Stable anchor | If missing |
|---|---|---|---|---|---|
| DualStatusBar.kt:116-125 | framework `com.android.internal.policy.SystemBarUtils.getStatusBarHeight(Context)` and `getStatusBarHeightForRotation(Context,int)` (findMethodExact) | no | OK | internal framework class (moved from System UI in Android 14) | throws inside `install`. **HALF:** see below |
| DualStatusBar.kt:64,141-142,163 | `com.android.systemui.statusbar.phone.PhoneStatusBarView`: `onFinishInflate()`, `onConfigurationChanged(Configuration)` | no | OK | root of `status_bar.xml` (id `status_bar`) | **HALF** |
| DualStatusBar.kt:144 | `FrameLayout.onMeasure(int,int)` | no | OK | SDK | **HALF** |
| DualStatusBar.kt:172-174 | `Clock.updateClock()`, `onDarkChanged(...)` | no | OK | | SILENT |
| DualStatusBar.kt:181 | `TextView.onVisibilityChanged(View,int)` | no | OK (declared in TextView) | SDK | SILENT |
| DualStatusBar.kt:214-215 | dimens `status_bar_system_icons_height`, `status_bar_clock_size` (getIdentifier) | n/a | OK | public.xml | no growth (clean) |
| DualStatusBar.kt:264-268,332,341,354 | ids `status_bar_start_side_except_heads_up` (LinearLayout), `status_bar_end_side_container`, `status_bar_end_side_content`, `notification_icon_area` (AlphaOptimizedFrameLayout in that LinearLayout), `clock`, `status_bar_contents` (Nothing's `NTStatusBarContentExt`), `notificationIcons` (NotificationIconContainer) | n/a | OK; the layout types match `status_bar.xml` | status_bar.xml | logged "not laid out as expected", left as is (clean) |
| DualStatusBar.kt:584-588 | `ActivityManager.getService()` (hidden), `IActivityManager.getHistoricalProcessExitReasons(String,int,int,int)`, `ParceledListSlice.getList()` | no | OK | there is a **public** `ActivityManager.getHistoricalProcessExitReasons(pkg,pid,max)`, and System UI is asking about its own package, so the public call works | `runCatching` returns null and the guard treats it as "maybe a crash" |
| DualStatusBar.kt:537 | path `/data/user_de/0/com.android.systemui/files` | n/a | OK | use `context.getDataDir()`, from device-protected storage | the guard stays off, so the feature stays off (clean) |
| StatusBarSaver.kt:63,114-126 | `PhoneStatusBarView.onAttachedToWindow()`, `onDetachedFromWindow()`, `onInterceptTouchEvent(MotionEvent)` | no | OK (all declared) | | **HALF** (next row) |
| StatusBarSaver.kt:64,137 | `com.android.systemui.statusbar.NotificationListener.onNotificationPosted(SBN,RankingMap)` | no | OK | extends `NotificationListenerWithPlugins` / NotificationListenerService | **HALF:** findClass throws after the bar hooks, so the bar hides but never wakes on notifications or the shade (a tap still wakes it) |
| StatusBarSaver.kt:65,145 | `com.android.systemui.shade.ShadeExpansionStateManager.onPanelExpansionChanged(float,boolean,boolean)` | no | OK | AOSP; ShadeExpansionListener dispatcher | **HALF** (same as above) |
| StatusBarSaver.kt:66-69,170-194 | `statusbar.data.repository.StatusBarModePerDisplayRepositoryImpl.showTransient()`/`clearTransient()`, Kotlin backing field `_isTransientShown` (MutableStateFlow), anonymous `$commandQueueCallback$1.showTransient(IIZ)`/`abortTransient(II)` with `this$0` | Kotlin-generated (`$commandQueueCallback$1`, `_isTransientShown`) | OK | the callback implements `com.android.systemui.statusbar.CommandQueue$Callbacks`. Public getter `isTransientShown()` (StateFlow) instead of the backing field | findClassIfExists, logged (clean) |
| StatusBarSaver.kt:207,391 | ids `status_bar_contents`, `notification_icon_area` | n/a | OK | | logged (clean) |
| FullScreenApps.kt:61 | permission `android.permission.STATUS_BAR_SERVICE` (System UI holds it) | n/a | OK | platform permission | broadcasts dropped (clean) |

**HALF failures in DualStatusBar.** `install()` sets `on = true` (line 113) before any hook.

- If `SystemBarUtils` throws, nothing is hooked, but `on` stays true. `Clock.getSmallTime` then skips the inline date (`dateBelow()` true), so the date disappears with no second row.
- If `PhoneStatusBarView` is missing after the height hooks are in, the status bar becomes taller with a single row.

Set `on` last, or put each step in its own guard.

### 2.4 Lock screen rotation (LockScreenTurn, LandscapeBouncer, LockScreenLayoutLog)

| Module file:line | Uses | obf? | Match | Stable anchor | If missing |
|---|---|---|---|---|---|
| LockScreenTurn.java:63,133 | `statusbar.policy.KeyguardStateControllerImpl.isKeyguardScreenRotationAllowed()` | no | OK ()Z | implements `KeyguardStateController`; reads sysprop `lockscreen.rot_override` and `config_enableLockScreenRotation` (line 318) | SILENT (count 0): the lock screen never turns, the rest installs uselessly |
| LockScreenTurn.java:67,193 | `keyguard.data.repository.KeyguardBlueprintRepository.applyBlueprint(String)` and the ids "split-shade" / "default" | no | OK (String)Z; `SplitShadeKeyguardBlueprint.ID="split-shade"`, `DefaultKeyguardBlueprint.DEFAULT="default"` | the static `ID` / `DEFAULT` fields: read them instead of literals | findClassIfExists (clean) |
| LockScreenTurn.java:75,210-232 | `DefaultNotificationStackScrollLayoutSection.applyConstraints(ConstraintSet)`, `getContext()` (OK-inh from `NotificationStackScrollLayoutSection`). Resources: bool `config_use_split_notification_shade`, id `nssl_placeholder`, dimen `keyguard_split_shade_top_margin` | no | OK | superclass `KeyguardSection` | logged per call |
| LockScreenTurn.java:69,239-249 | `ClockSection.applyConstraints(ConstraintSet)`, fields `context` (Context) and `keyguardClockViewModel`, `KeyguardClockViewModel.getClockShouldBeCentered()` returning StateFlow, then `getValue` | Kotlin property backing fields | OK | `KeyguardSection` subclass; find fields by type (Context, KeyguardClockViewModel) | logged per call (`centered` is diagnostic only) |
| LockScreenTurn.java:77,292 | `com.nothing.systemui.keyguard.ui.view.layout.sections.NTWidgetContainerSection.applyConstraints(ConstraintSet)` | no | OK | Nothing class; `KeyguardSection` subclass; uses id `nt_widget_host_view_container` | findClassIfExists (clean: the whole of keepNothingLayout is skipped) |
| LockScreenTurn.java:71,322-358 | `biometrics.UdfpsControllerOverlay.shouldRotate()` (**private final**), `updateDimensions(WindowManager.LayoutParams, UdfpsAnimationViewController)` (**private final**), fields `keyguardUpdateMonitor`, `overlayParams` (UdfpsOverlayParams), `sensorBounds` (Rect). `KeyguardUpdateMonitor.isGoingToSleep()`, `UdfpsOverlayParams.getSensorBounds()` | no, but private final, so **R8 may inline them** in a later build | OK | AOSP; the log tag "UdfpsControllerOverlay"; fields by type | SILENT |
| LockScreenTurn.java:73,365-382 | `DefaultDeviceEntrySection.centerIcon$vendor__noth__packages__apps__NothingSystemUI__android_common__MtkSystemUI_core(Point,float,ConstraintSet)`, matched by `startsWith("centerIcon")` | **Kotlin internal mangling**: the suffix is build-module specific (MTK vs Qualcomm builds) | OK | prefix match (already used); params (Point,float,ConstraintSet) | logged "no class" or SILENT |
| LockScreenTurn.java:393,400,403,442 | fields `deviceEntryIconViewModel` (dagger.Lazy, then `.get()`), `windowManager`, `context`, `notificationPanelView`. `DeviceEntryIconViewModel.isUdfpsSupported()` returning StateFlow | Kotlin backing fields | OK | by type | logged per call |
| LockScreenTurn.java:483-505 | `UdfpsController.onFingerDown/onFingerUp/onFingerDownFromTP/onFingerUpFromTP`, **`lambda$onFingerDown$6`**, `dispatchOnUiReady(long)`. `KeyguardUpdateMonitor.handleFingerprint{Acquired,Help,AuthFailed,Error,Authenticated}` | `lambda$onFingerDown$6` is a javac lambda index | OK (`$5` and `$6` both exist; `$6` is (J)V) | diagnostics only; hook `dispatchOnUiReady` alone | SILENT (logging only) |
| LockScreenTurn.java:536-562 | `biometrics.UdfpsUtils.isWithinSensorArea(int,MotionEvent,UdfpsOverlayParams,boolean)`, `getTouchInNativeCoordinates(int,MotionEvent,UdfpsOverlayParams,boolean)`, `UdfpsOverlayParams.getRotation()` | no | OK (4-arg overloads present) | AOSP | logged (clean) |
| LockScreenTurn.java:641-751 | `statusbar.notification.stack.ui.view.SharedNotificationContainer.updateConstraints(HorizontalPosition,int start,int top,int end,int bottom,float alpha)`, field `baseConstraintSet`. Arg 0's simple name "MiddleToEdge" (`SharedNotificationContainerViewModel$HorizontalPosition$MiddleToEdge`) | Kotlin sealed-class name | OK (argument order confirmed in jadx) | AOSP | logged (clean) |
| LockScreenTurn.java:644-712 | `com.android.systemui.Dependency.get(Class)` with `plugins.statusbar.StatusBarStateController`: `addCallback(StateListener)`, `getState()`, `$StateListener.onStateChanged(int)` (Proxy) | no | OK (Dependency still maps StatusBarStateController, Dependency.java:466) | plugin interface (versioned); `Dependency` is deprecated in AOSP and will go | logged |
| LockScreenTurn.java:79-82,817-881 | `keyguard.domain.interactor.KeyguardBlueprintInteractor.start()`, `refreshBlueprint(IntraBlueprintTransition$Type)`, `getCurrentBlueprint()`. Enum `IntraBlueprintTransition$Type.NoTransition` | no | OK | CoreStartable | logged (clean) |
| LockScreenTurn.java:66,155-179,906-927 | `shade.NotificationShadeWindowView.onAttachedToWindow()`, `onConfigurationChanged(Configuration)` via **findAndHookMethod** (declared only) | no | OK | AOSP; root of `super_notification_shade.xml` | **throws, so HALF** (below) |
| LockScreenTurn.java:951-962 | `androidx.constraintlayout.widget.ConstraintLayout`/`ConstraintSet` (`<init>`, `clone(ConstraintLayout)`, `applyTo`, `connect` with 4 and 5 ints, `getKnownIds`, `constrainWidth`, `create`, `setGuidelinePercent`, `setAlpha`). `keyguard.shared.model.KeyguardBlueprint.applyConstraints(ConstraintSet)` | no (androidx kept) | OK | library API | logged |
| LockScreenTurn.java:616-618,977-978 | framework `Configuration.windowConfiguration` (hidden field), `WindowConfiguration.getDisplayRotation()` (hidden) | no | OK | @TestApi | returns -1; System UI's own pass (clean) |
| ids (LockScreenTurn, LockScreenLayoutLog:18-19,41) | `keyguard_root_view`, `keyguard_indication_area`, `lockscreen_clock_view`, `keyguard_slice_view`, `nt_widget_host_view_container`, `split_shade_guideline`, `notification_stack_scroller`, `nssl_guideline`, `shared_notification_container`, `keyguard_message_area_container`, `keyguard_bouncer_container`, `nt_bouncer_icon_area` | n/a | all OK | public.xml | the step is skipped |
| LandscapeBouncer.java:24,136 | **flag object** `com.android.systemui.flags.Flags.LOCKSCREEN_ENABLE_LANDSCAPE` (UnreleasedFlag, name "lockscreen.enable_landscape") via `getStaticObjectField` | no | OK | the flag's string name "lockscreen.enable_landscape". Resolve it by scanning `Flags`' static fields for a `Flag` whose `getName()` equals that | **throws inside LockScreenTurn.install, so HALF** (below) |
| LandscapeBouncer.java:25-27,143 | `flags.FeatureFlagsClassicRelease` / `FeatureFlagsClassicDebug.isEnabled(UnreleasedFlag)` (and the other overloads) | no | OK | implement `FeatureFlagsClassic` | SILENT (count logged) |
| LandscapeBouncer.java:28,179-185 | `com.android.keyguard.KeyguardSecurityViewFlipperController.asynchronouslyInflateView(SecurityMode,KeyguardSecurityCallback)` and `lambda$asynchronouslyInflateView$*` | lambda name | OK; the flag is read at jadx lines 102 and 172 | AOSP | SILENT |
| LandscapeBouncer.java:30,187-197 | `KeyguardSecurityContainerController$N.onOrientationChanged(int)` scanned over N=1..30; it is **`$5`** in this build | **anonymous `$N`** | OK | `$5` implements `statusbar.policy.ConfigurationController$ConfigurationListener`: scan by interface instead | SILENT |
| LandscapeBouncer.java:47,81,201 | `DefaultDeviceEntrySection.updateBouncerLockIconContainer(Point,int)` (**Nothing-added**), fields `bouncerUdfpsIconContainer`, `bouncerlockIconContainer` (ViewGroup, lowercase "l" as in Nothing's code), `context` | no | OK | Nothing code, no AOSP twin | SILENT / logged |
| LandscapeBouncer.java:216-235 | `com.android.keyguard.KeyguardPINView` / `KeyguardPatternView` / `KeyguardPasswordView.updateConstraints(boolean)`. Id `bouncer_message_area` | no | OK (Z)V | AOSP | SILENT |

**HALF failure inside LockScreenTurn (the most important one).** `LockScreenTurn.install` is a single `Hooks.install` unit, and its steps run in this order:

1. rotation allowed
2. `keepNothingLayout`
3. `turnFingerprint`
4. **`LandscapeBouncer.install`**: `getStaticObjectField` can throw `NoSuchFieldError`
5. `mirrorNotifications`
6. `layOutOnFlip`
7. **`layOutWithTurn`**: `findAndHookMethod` can throw
8. `logFingerprint`
9. `sensorTouches`
10. shade window `onAttachedToWindow` (`findAndHookMethod` can throw)

A throw at step 4 or 7 leaves the lock screen turning, with the halves and the turned fingerprint overlay, but without `sensorTouches`. In landscape, a finger on the sensor then pulls the shade instead of being read: **fingerprint unlock breaks in landscape**. It also loses notification mirroring and clearance, flip relayout and the same-frame layout.

Wrap each step in its own `Hooks.install`, and install `sensorTouches` before anything optional.

### 2.5 Fingerprint sensor layers (LandscapeFingerprint)

| Module file:line | Uses | obf? | Match | Stable anchor | If missing |
|---|---|---|---|---|---|
| :42,63 | `com.nothing.systemui.biometrics.NTUdfpsDimLayerController`: constructor (Context,UdfpsController), `createSurface(float,boolean)`, `showSurface(float,boolean)`, `updateAlpha(float)`. Fields `mContext`, `mSurfaceControl`, `mDisplayWidth`, `mDisplayHeight` | no | OK | layer name string **"NTFingerprintDimLayer"** (also the BLASTBufferQueue name); z **1073741827** | findClass throws before any hook, so SKIP |
| :43,64 | `com.nothing.systemui.biometrics.NTUdfpsIlluminationController`: constructor (Context), `createSurface(boolean)`, `showSurface()`, same fields | no | OK | z 1073741828; its TAG string | SKIP |
| :90 | `SurfaceControl.Builder.setName(String)` | no | OK | SDK | (installed after the controllers) |
| :111 | `SurfaceControl.Transaction.setLayer(SurfaceControl,int)` | no | OK | SDK | |
| :201,208-215,237-243 | `Transaction.reparent` (SDK), `Builder.<init>()`, **`Builder.setContainerLayer()` (hidden)**, `build()`, `Transaction.setVisibility` (SDK), **`Transaction.setMatrix(SurfaceControl,float,float,float,float)` (hidden, @UnsupportedAppUsage)**, `setPosition` (SDK) | no | all OK | the public `setMatrix(SurfaceControl, Matrix, float[])` overload exists as a fallback | logged per call |
| :278 | `Settings.Secure "accessibility_display_inversion_enabled"` | n/a | OK | public Secure key | |

### 2.6 Landscape shade (LandscapeShade)

| Module file:line | Uses | obf? | Match | Stable anchor | If missing |
|---|---|---|---|---|---|
| :38,54,84 | `classifier.FalsingDataProvider.getWidthPixels()`, `getHeightPixels()`, `getXdpi()`, `getYdpi()` (findAndHookMethod); fields `mWidthPixels`, `mHeightPixels`, `mXdpi`, `mYdpi` | no | OK | AOSP; log line "width, height: " | throws (SKIP if first) |
| :39,96-98,270-293 | `DistanceClassifier.calculateFalsingResult(int,double,double)`, `isLongSwipe()`. Inherited `getWidthPixels`/`getHeightPixels`/`getXdpi`/`getYdpi` (OK-inh from `FalsingClassifier`). Fields `mHorizontalFlingThresholdPx`, `mVerticalFlingThresholdPx`, `mHorizontalSwipeThresholdPx`, `mVerticalSwipeThresholdPx` | no | OK | AOSP; DeviceConfig keys `brightline_falsing_distance_*` | **HALF:** the provider's width/height swap stays in place without matching thresholds |
| :40,105-115 | `ProximityClassifier.calculateFalsingResult`; static `FalsingClassifier$Result.passed(double)` | no | OK | AOSP | HALF |
| :41,123-140 | `shade.NotificationPanelViewController.closeQsIfPossible()`, `onPanelStateChanged(int)` (findAndHookMethod); fields `mBarState`, `mSplitShadeEnabled`, `mQsController` (QuickSettingsControllerImpl): `getExpanded()`, `closeQs()` | no | OK | AOSP (this class is being dismantled upstream: `sceneContainer`) | HALF (pinPage and pullFromAnywhere skipped) |
| :42,161-176 | enum `statusbar.phone.ScrimState.BOUNCER`, whose body class is `ScrimState$3`, which declares `prepare(ScrimState)`. Fields `mClipQsScrim`, `mBehindAlpha`, `mNotifAlpha`, `mBehindTint`, `mNotifTint` | enum body `$3` (resolved from the constant, so it is robust) | OK | `ScrimState.valueOf("BOUNCER").getClass()` (already done) | HALF |
| :199-226 | **`shade.NotificationShadeWindowViewController$1`** `.handleTouchEvent(MotionEvent)`, `shouldInterceptTouchEvent(MotionEvent)`, `didIntercept(MotionEvent)`, field `mLastInterceptWasDragDownHelper`. Outer `this$0` and its `mShadeViewController`; `NotificationPanelViewController.handleExternalTouch(MotionEvent)` | **anonymous `$1`** | OK | `$1` implements **`shade.NotificationShadeWindowView$InteractionEventHandler`**: find it by interface | throws (last step) |
| :313 | `android.provider.DeviceConfig.getFloat("systemui", …)` (System API, mainline) | no | not checked locally (APEX) | public System API | falls back to the defaults |

### 2.7 Always-on display and widgets fit (AodLandscape, LockScreenFit)

| Module file:line | Uses | obf? | Match | Stable anchor | If missing |
|---|---|---|---|---|---|
| AodLandscape:29,41 / LockScreenFit:27,35 | `keyguard.ui.view.KeyguardRootView` (constructor Context,AttributeSet) | no | OK | id `keyguard_root_view` | SKIP |
| AodLandscape:28,47 | `keyguard.shared.model.BurnInModel.<init>(int,int,float,boolean)` (exact). A Kotlin data class: translationX, translationY, scale, scaleClockOnly | no, but **an exact data-class constructor** | OK (`(IIFZ)V` plus the default-args constructor) | `hookAllConstructors`, then take the constructor whose first two params are int | throws after the root hook, so effectively SKIP |
| AodLandscape:31-32 / LockScreenFit:28 | ids `lockscreen_clock_view`, `keyguard_slice_view`, `aod_notification_icon_container`, `nt_widget_host_view_container` (Nothing) | n/a | OK | public.xml; `burn_in_prevention_offset_y` exists | clean |

### 2.8 Module panels (perf/HudWindows; System UI process, CMF Phone 1 only)

| Module file:line | Uses | obf? | Match | Stable anchor | If missing |
|---|---|---|---|---|---|
| HudWindows.java:82 | `com.android.systemui.SystemUIApplication.onCreate()` | no | OK | the manifest `<application android:name>` (read it with `context.getApplicationInfo().className`) | SKIP |
| :50,102 | window type 2024 (`TYPE_NAVIGATION_BAR_PANEL`), `BroadcastReceiver.getSentFromPackage()` (API 34) | no | OK | SDK | |

### 2.9 Architectural flags that would remove whole groups at once (`com.android.systemui.FeatureFlagsImpl`)

These are values from the decompiled build. Each flag is a single switch that would take out many classes the module depends on.

| Flag | Current value | Module features it would remove |
|---|---|---|
| `sceneContainer()` | false | KeyguardRootView, the blueprints and sections, NotificationPanelViewController, ScrimState usage, the NotificationShadeWindowViewController touch path: LockScreenTurn, AodLandscape, LockScreenFit, LandscapeShade |
| `statusBarRootModernization()` | false | PhoneStatusBarView as the root, the status_bar.xml ids: DualStatusBar, StatusBarSaver, parts of Areas |
| `composeBouncer()` | false | KeyguardSecurityViewFlipperController and the PIN/Pattern/Password views: LandscapeBouncer |
| `qsUiRefactor()`, `qsUiRefactorComposeFragment()`, `qsNewTiles()` | false | QSTileViewImpl: QuickSettings, TileBackground, Areas.TILE |
| `deviceEntryUdfpsRefactor()` | **true** already | the module's DefaultDeviceEntrySection path assumes it |
| `statusBarChipsModernization()`, `shadeWindowGoesAround()` | false | |

The classic flag `Flags.LOCKSCREEN_ENABLE_LANDSCAPE` is also read outside the bouncer: `SplitShadeStateControllerImpl`, `TileLayout`, `SideLabelTileLayout`, `TileAdapter`, `NotificationStackScrollLayout`, `CommunalLockSection`, `LockSection` and `KeyguardStateControllerImpl:318`. The module's thread-local scoping is what keeps those unaffected.

---

## 3. Settings (`com.android.settings`, R8-obfuscated; the retrace hash is in each class header)

| Module file:line | Uses | obf? | Match | Stable anchor | If missing |
|---|---|---|---|---|---|
| TaskManager.java:58,86 | class `com.android.settings.applications.RunningProcessesView` (kept: it is inflated from XML), method **`a()`** = AOSP `doCreate()` (inflates the layout and sets the adapter) | method **yes** | OK (dex `a ()V`) | the only no-arg method that calls `findViewById(android.R.id.list)` / `ListView.setAdapter`. Alternative: hook `ListView.setAdapter` and check the parent class | throws, so SKIP |
| TaskManager.java:119 | field **`G`** (ListView) | **yes** | OK: the **only ListView-typed field** | resolve by type: `findFirstFieldByExactType(RunningProcessesView, ListView.class)` | RT-LOG (ClassCastException or NoSuchField inside guard) |
| TaskManager.java:60,96 | class **`com.android.settings.applications.A1`** (AOSP `RunningProcessesView.ViewHolder`), method **`a(S1,J1,StringBuilder)`**, which returns `y1` (ActiveItem) | **yes, class and method** | OK | a class in `applications` with constructor `(View)` that calls `findViewById(android.R.id.icon/title/summary)` and `view.setTag(this)`; 3-param method whose last param is StringBuilder. Runtime alternative: hook `RunningProcessesView`'s adapter `getView` (`z1`) | **HALF:** the page hook is already in, so the header and ticker show but rows are not enriched. If the name now means another class, the hook runs on unrelated code and logs on every call |
| TaskManager.java:180 | holder field **`d`** (summary TextView) | **yes** | OK | at runtime, pick the TextView field `== holder.rootView.findViewById(android.R.id.summary)` | RT-LOG |
| TaskManager.java:220 | item field **`n`** (`K1` = MergedItem, `n` is `M1` = ProcessItem) | **yes** | OK. When the item is an `M1` itself, `n` is a HashMap and the guarded call throws | the field of `K1` whose type is the process-item class | RT-LOG |
| TaskManager.java:187-188,239-242 | `M1` fields **`r`** (pid, int), **`p`** (uid, final int), **`q`** (processName, final String), **`c`** (PackageItemInfo, from `J1`) | **yes** | OK | **best anchor:** `M1.v` is an `ActivityManager.RunningAppProcessInfo`. Find it by type and read `.pid`, `.uid`, `.processName`, which are public SDK fields. PackageItemInfo by type | RT-LOG |
| TaskManager.java:244 | item field **`d`** (label CharSequence, from `J1`) | **yes** | OK | the only CharSequence field of the base class | RT-LOG |
| TaskManager.java:320 | `ActivityManager.forceStopPackage(String)` (hidden; Settings holds FORCE_STOP_PACKAGES) | no | OK | framework | RT-LOG (toast not shown) |
| TaskStats.java:175-176 | `android.os.ServiceManager.getService("cpuinfo")` (hidden), then `IBinder.dumpAsync` | no | OK | framework; service name "cpuinfo" | logged once; CPU column empty |
| TaskStats.java:157-161,226 | `HardwarePropertiesManager.getDeviceTemperatures(int,int)`, `NetworkStatsManager.querySummary` | no | OK | SDK / System API | logged once |
| SettingsTile.kt:38-48 | framework `android.app.ApplicationPackageManager.queryIntentActivitiesAsUser` (hidden; both overloads hooked; Settings calls `(Intent,int,int)` from `settingslib.drawer.j.n`, which is TileUtils.loadActivityTiles). Hidden field **`ResolveInfo.system`** | no | OK; `TileUtils` (`settingslib/drawer/j.java:220,229`) reads `resolveInfo.system` | the actions `com.android.settings.action.EXTRA_SETTINGS` / `IA_SETTINGS` | SILENT. Note that content-provider tiles (`queryIntentContentProvidersAsUser`) are not covered |
| SearchHook.kt:33 (Settings Intelligence) | `com.android.settings.intelligence.search.indexing.PreIndexDataCollector.isWellKnownProvider(ResolveInfo)` | no (SI is not obfuscated) | OK (package-private, would be at risk if R8 inlined it) | log tag "IndexableDataCollector"; `READ_SEARCH_INDEXABLES` check | throws, so SKIP |
| SearchHook.kt:45 | `IndexDatabaseHelper.buildProviderVersionedNames(Context,List)` (static) | no | OK | the pref key "indexed_providers", SHARED_PREFS_TAG "indexing_manager" | **HALF:** the module's provider is indexed but never re-indexed when its settings change (stale search) |

---

## 4. HeliBoard (`helium314.keyboard`, 4.2 release; `-dontobfuscate`, but R8 still shrinks and merges)

| Module file:line | Uses | obf? | Match | Stable anchor | If missing |
|---|---|---|---|---|---|
| HeliBoardGamepad:69,136-175 / FullWidth:47,68-76 | `helium314.keyboard.latin.LatinIME`: `onKeyDown(int,KeyEvent)`, `onKeyUp(int,KeyEvent)`, `onStartInputView(EditorInfo,boolean)`, `onFinishInputView(boolean)`, `onWindowHidden()`, `onCreate()` | no | OK | InputMethodService overrides | SKIP |
| Gamepad:70,127 | `keyboard.KeyboardView` (class) | no | OK | | SKIP (the constructor runs before any hook) |
| Gamepad:71,128 | `keyboard.PopupKeysPanel` (interface) | no | OK | | SKIP |
| Gamepad:72,129-131 | `event.HapticEvent.KEY_PRESS` / `KEY_REPEAT` (enum constants) | no | OK | `HapticEvent.valueOf(...)` | SKIP |
| Gamepad:295 | `dismissPopupKeysPanel*()` found by prefix on the panel instance | no | OK (`PopupKeysKeyboardView.dismissPopupKeysPanel()V`) | the interface method of PopupKeysPanel | logged |
| Gamepad:379,385-389 | `LatinIME.mKeyboardActionListener` (`KeyboardActionListenerImpl`); `onPressKey(int,int,int,HapticEvent)`, `onCodeInput(int,int,int,boolean)`, `onReleaseKey(int,boolean)` | no | OK | interface `keyboard.KeyboardActionListener` | the hook throws and the key goes to the app |
| Gamepad:403-404 | `LatinIME.mKeyboardSwitcher`, then `KeyboardSwitcher.mKeyboardView` (MainKeyboardView) | no | OK | `KeyboardSwitcher.getInstance()` / `getMainKeyboardView()` | as above |
| Gamepad:484-505,563 | `KeyboardView.mKeyboard`; `Keyboard.getSortedKeys()`; `Key.mWidth`, `Key.mHeight`, `getX()`, `getY()` | no | OK | public getters `getKeyboard()`, `getWidth()`/`getHeight()` | as above |
| Gamepad:75-82 | key codes DELETE -7, SHIFT -11, ARROW_LEFT -21, ARROW_RIGHT -22, ALPHA -201, `Constants.NOT_A_COORDINATE` -1 | n/a | OK (KeyCode.kt, Constants.java) | `KeyCode` object constants: read them by reflection | a wrong key is typed |
| FullWidth:54-57,148 | `getDefaultKeyboardWidth(Context)` in `latin.utils.ResourceUtils` **or** `latin.utils.KtxKt` | R8 **class merging** | `ResourceUtils` is **MISS** in the 4.2 APK; `KtxKt` is OK. The 4.3-beta1 source has it in `ResourceUtils.java` again. R8 moved the static method between classes | the method name and signature (Context)I in any class: scan with DexKit, or hook the caller `ResourceUtils.getKeyboardWidth` / `KeyboardLayoutParams` | logged "left off" (clean) |

---

## 5. Framework hidden APIs used by reflection (module-wide)

| Module file:line | API | Hidden? | Match (framework dex) | Fallback or anchor | If missing |
|---|---|---|---|---|---|
| systemui/QuickSettings.java:81 | `AnimatedVectorDrawable.forceAnimationOnUI()` | yes (@UnsupportedAppUsage) | OK | none | SKIP (animations frozen) |
| systemui/LandscapeFingerprint.java:211 | `SurfaceControl.Builder.setContainerLayer()` | yes | OK | already tolerated (try/catch): an effect layer without a buffer | it draws nothing either way |
| systemui/LandscapeFingerprint.java:242 | `SurfaceControl.Transaction.setMatrix(SC,float,float,float,float)` | yes | OK | public `setMatrix(SC, Matrix, float[])` | RT-LOG |
| systemui/LockScreenTurn.java:616-618,977-978 | `Configuration.windowConfiguration`, `WindowConfiguration.getDisplayRotation()` | yes (@TestApi) | OK | `Display.getRotation()` (already the fallback) | clean |
| systemui/DualStatusBar.kt:116-125 | `com.android.internal.policy.SystemBarUtils.getStatusBarHeight(Context)`, `getStatusBarHeightForRotation(Context,int)` | internal | OK (plus a `(Resources,DisplayCutout)` overload) | the dimen `status_bar_height_default` | **HALF** (see 2.3) |
| systemui/DualStatusBar.kt:584-588 | `ActivityManager.getService()`, `IActivityManager.getHistoricalProcessExitReasons(String,int,int,int)`, `ParceledListSlice.getList()` | yes | OK | **public** `ActivityManager.getHistoricalProcessExitReasons` for the caller's own package | null (guarded) |
| systemui/LandscapeShade.java:313 | `android.provider.DeviceConfig.getFloat` | System API (APEX) | not checkable locally | default values | clean |
| settings/SettingsTile.kt:38-48 | `ApplicationPackageManager.queryIntentActivitiesAsUser` (2 overloads), `ResolveInfo.system` | yes | OK | | SILENT |
| settings/TaskManager.java:320 | `ActivityManager.forceStopPackage(String)` | yes (System API) | OK | | RT-LOG |
| settings/TaskStats.java:175 | `android.os.ServiceManager.getService(String)` | yes | OK | | logged |
| system/ControllerHome.java:129 | `KeyEvent.getDisplayId()` | yes (@TestApi) | OK | | |
| system/ControllerHome.java:244-253 | `RoleManager.getRoleHoldersAsUser(String,UserHandle)`, `addRoleHolderAsUser(String,String,int,UserHandle,Executor,Consumer)` | System API (permission APEX) | not checkable locally (not in framework.jar) | | logged |
| system/SystemShortcuts.java:158,179 | `Context.startActivityAsUser(Intent,UserHandle)` | yes | OK (ContextImpl / ContextWrapper) | | |
| wallpaper/LivingWorldsView.java:366 | `Surface.hwuiDestroy()` | yes | OK | | logged |
| perf/GameUnlock.java:63 | `android.os.SystemProperties.get` (hooked) | yes | OK | | findClassIfExists (clean) |
| xposed/Hooks.java:69 | `XposedBridge.deoptimizeMethod(Member)` (LSPosed, outside API 82) | LSPosed-specific | not a framework API | | logged once |
| perf/GameBar.java:140 | `BroadcastOptions.setShareIdentityEnabled(boolean)` (API 34) | no | OK | | RT-LOG |

The SDK methods that are hooked globally in System UI all exist and are declared on the hooked class: `TextView.setTextColor(int)`, `TextView.onDraw`, `ImageView.onDraw`, `ImageView.setImageDrawable`, `View.onAttachedToWindow`, `FrameLayout.onMeasure(int,int)`, `TextView.onVisibilityChanged(View,int)`, `SurfaceControl.Builder.setName`, `SurfaceControl.Transaction.setLayer`.

System-server internals (`com.android.server.*` in `system/`) are a separate host and are not mapped here.

---

## 6. Game Dashboard (`perf/GameBar.java`, process `com.nothing.ntgamedashboard`, CMF Phone 1)

**Not verifiable:** there is no APK or decompile in the scratchpads.

| Line | Uses | Notes |
|---|---|---|
| 32-34,45-80 | `com.nothing.ntgamedashboard.view.adapter.GamePanelPagerAdapter` (all constructors, field `gridSettings` = array of `kotlin.Pair`), `.view.viewholder.GameSettingViewHolder.setData(String, SettingViewType)` (exact), field `binding`, then `labelView`, `iconView`, `containerLayout` (ViewBinding: stable names generated from the layout ids), `.struct.SettingViewType.BUTTON`, `kotlin.Pair` (constructor String,Object) | **HALF risk:** the pager constructor hook adds three `nlplus_*` keys before the `setData` hook is installed. If `setData`'s signature changes, Nothing's own binder receives unknown keys. Its handling of an unknown key is not known (a crash is possible). Install the `setData` hook first and add the grid entries only if it succeeded. |

---

## 7. Current mismatches (this build)

1. **Bluetooth QS tile.** `BluetoothTileViewImpl` (and `RingerModeTileViewImpl` when not 1x1) override `getBackgroundColorForState` without calling `super`. The active BT tile stays white, and the global `litAtSource` flag also disables the fallback for it. (QuickSettings.java:57, 66-69)
2. **`BatteryMeterView.onAttachedToWindow`** is not declared (the superclass is LinearLayout), so the hook at Battery.java:64 installs nothing. Harmless, but it is dead code.
3. **The "Battery" class-name filter** (Battery.java:149-165) also matches Nothing's card-widget battery views (`com.nothing.cardwidget.battery.view.*`, `cardparser.SimulatedBatteryContainerView`) and AOSP's new `BatteryStatusChip` / `pipeline.battery` views. The likely result is a green tint on Nothing's lock-screen or shade battery widget while "NDot status bar" is on. Needs a phone check.
4. **HeliBoard `ResourceUtils.getDefaultKeyboardWidth`** is absent in 4.2. It is already handled by the `KtxKt` fallback (the log names the class used).
5. **`Areas` "SystemIcons"** matches no class. It is dead but harmless.
6. **Half-applied structure** (not a mismatch today, but a guaranteed bad failure mode):
   - LockScreenTurn: steps 4, 7 and 10 throw mid-install.
   - DualStatusBar: `on=true` is set before the hooks.
   - StatusBarSaver: the listener and shade hooks come after the bar hooks.
   - LandscapeShade: the provider hooks come before the classifiers.
   - TaskManager: the page hook comes before the row hook.
   - SearchHook: two hooks with nothing tying them together.
   - GameBar: the grid entries are added before the binder hook.

Everything else listed above is present, with matching signatures, in the decompiled build.

---

## 8. Ranking of the most fragile dependencies, with a resolver for each

| Rank | Dependency | Why it is fragile | Resolver |
|---|---|---|---|
| 1 | Settings `A1.a`, `RunningProcessesView.a`/`G`, `K1.n`, `M1.r/p/q`, `J1.c/d` | R8 short names change on any Settings rebuild, and the same name can then point at a different member | **By type and structure at runtime:** the ListView field by type; the holder class = the class in `com.android.settings.applications` with a (View) constructor and 4+ TextView fields and a method `(…, …, StringBuilder)`; the summary = the TextView field equal to `findViewById(android.R.id.summary)`; the process data via the `ActivityManager.RunningAppProcessInfo`-typed field of the process item (public `.pid/.uid/.processName`). Verify the types before hooking; otherwise skip and log. Better still: **DexKit** (method finder by strings, types and calls) with the result cached per `versionCode`. |
| 2 | `LandscapeBouncer` `Flags.LOCKSCREEN_ENABLE_LANDSCAPE` plus the scoping methods | the field can be renamed, or the flag moved to aconfig. A throw inside LockScreenTurn half-applies the feature | Find the flag by its `name` "lockscreen.enable_landscape" (iterate `Flags`' static fields, call `getName()`); also check `com.android.systemui.Flags` / aconfig for a `lockscreenEnableLandscape()`. Install it with its own `Hooks.install`. |
| 3 | `NotificationShadeWindowViewController$1`, `KeyguardSecurityContainerController$5`, `$commandQueueCallback$1` | javac / Kotlin anonymous index | Scan `$1..$40` and select by **interface**: `NotificationShadeWindowView$InteractionEventHandler`, `ConfigurationController$ConfigurationListener`, `CommandQueue$Callbacks` (the bouncer scan already uses a name-scan pattern; add the interface check). |
| 4 | Whole-architecture aconfig flags (`sceneContainer`, `statusBarRootModernization`, `composeBouncer`, `qsUiRefactor`/`qsNewTiles`) | one flag flip removes dozens of classes, and the module would silently install nothing or half-install | At install time, read `com.android.systemui.Flags.<flag>()` via reflection. If it is on, log "Nothing switched to X; feature off" and skip the whole group cleanly (a guard per group). |
| 5 | `UdfpsControllerOverlay.shouldRotate()` / `updateDimensions()` (private final), `NotificationPanelViewController.closeQsIfPossible()` | R8 can inline private or final methods in a later build: the hook installs but never fires | Assert that the hook count is greater than 0 and log it. For `shouldRotate`, the effect can be had by also hooking its callers (`UdfpsControllerOverlay.show`/`updateOverlayParams`) or `RotationUtils.rotateBounds`; prefer state-level hooks (`KeyguardStateController.isOccluded` scoped by a ThreadLocal) as the fallback. |
| 6 | Kotlin `internal` mangling `centerIcon$vendor__noth__…__MtkSystemUI_core` | the suffix differs per build module (MTK vs Qualcomm Nothing phones) | Already a prefix match. Also require the `(Point, float, ConstraintSet)` parameter types. |
| 7 | `BurnInModel(int,int,float,boolean)` exact | a data class gains a field, so the exact constructor is gone | `hookAllConstructors` and act on constructors whose params start (int,int,…). |
| 8 | HeliBoard `getDefaultKeyboardWidth` | R8 class merging relocates static utilities between releases | Scan the classes in `helium314.keyboard.latin.utils.*` for a static `(Context)I` method with that name; or DexKit; or hook `ResourceUtils.getKeyboardWidth`/`KeyboardLayoutParams` (the caller, which is more stable). |
| 9 | Class-name heuristics (Areas, Battery "Battery", NetworkSpeed, Clock.inHeader) | false positives when Nothing adds views (already happening with card-widget batteries) | Anchor on resource ids of the known roots (`status_bar_contents`, `quick_status_bar_system_icons`, `qs_header`, `battery`) and on `isAssignableFrom(BatteryMeterView)` instead of substring matches. |
| 10 | Kotlin backing fields (`_isTransientShown`, ClockSection `context`/`keyguardClockViewModel`, DefaultDeviceEntrySection fields) | renamed when Kotlin properties change | Use the public getters (`isTransientShown()`), or resolve by field type. |
| 11 | `Dependency.get(StatusBarStateController)` | `Dependency` is deprecated upstream | Capture the `StatusBarStateController` instance via a constructor hook on `StatusBarStateControllerImpl`, or from `NotificationPanelViewController`'s field by type. |
| 12 | Settings Intelligence `isWellKnownProvider` / `buildProviderVersionedNames` | currently un-obfuscated, but package-private or static, so they could be inlined | The log tags "IndexableDataCollector" / "IndexDatabaseHelper"; the pref key "indexed_providers". Fall back to an invalidation broadcast or a provider-version bump. |

**General recommendations** (they keep each feature either fully on or cleanly off):

- **One `Hooks.install` per independent step.** Within a step, resolve every member first and only then hook, all-or-nothing. A small `Resolver` that looks everything up and returns null on any miss would give "clean skip" everywhere.
- **Log the host build at each System UI and Settings start** (`versionCode`, `Build.FINGERPRINT`, plus the count of resolved and missing dependencies). An OTA then shows up in the LSPosed log on the first boot.
- **Cache resolved names per `versionCode`** (DexKit results) in the module's ConfigStore. This avoids rescanning on every start.

---

## 9. What the module could build on later (brief)

- **Lock-screen rotation without a hook.** `KeyguardStateControllerImpl:318` honours the sysprop `lockscreen.rot_override` and the overlay bool `config_enableLockScreenRotation`. Setting the property from root, or adding an RRO, avoids the `isKeyguardScreenRotationAllowed` hook entirely.
- **Split-shade lock screen.** `SplitShadeStateControllerImpl` combines `LOCKSCREEN_ENABLE_LANDSCAPE` with `force_config_use_split_notification_shade`, so this is AOSP's own switch for the split-shade lock screen in landscape.
- **AOSP landscape quick settings.** `TileLayout`, `SideLabelTileLayout` and `TileAdapter` read the same flag (`mIsSmallLandscapeLockscreenEnabled`), so AOSP's small-landscape QS layout could be enabled, scoped, for the turned lock screen.
- **Nothing tile types to theme or extend.** `GlyphsTile`, `GlyphsTorchTile`, `BatteryShareTile`, `FlipToRecordTile`, `RingerModeTile`, and the 2x1 `TwinButtonsTileView` / `BluetoothTileViewPager`. These are natural targets for Dot-matrix tiles.
- **Network speed.** `com.nothing.systemui.statusbar.policy.networkspeed.NetworkSpeedView` is a ready speed source for an NDot status-bar widget.
- **Card widgets.** Nothing's card-widget host (`com.nothing.cardwidget.*`, `cardparser`) is in System UI. Lock-screen widgets can be read or styled there, which is relevant to the Dot Matrix Display engine.
- **Status-bar insets.** `StatusBarContentInsetsProvider` (decompiled in `sui2/one`) is the clean place to reserve space for a module status-bar row, instead of `SystemBarUtils` heights.
- **Settings.**
  - SettingsLib tile injection (`EXTRA_SETTINGS` / `IA_SETTINGS`) also supports provider tiles (`queryIntentContentProvidersAsUser`, `resolveInfo.system`), which give dynamic summaries and switches on the Settings home page.
  - The running-services pages (`RunningServices`, `RunningProcessesView`) already expose a per-process `RunningAppProcessInfo` that more Task Manager data can hang off.
