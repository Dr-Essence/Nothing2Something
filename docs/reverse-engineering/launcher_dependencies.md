# Nothing2Something (Nothing-Launcher-Plus) — every dependency on Nothing Launcher 4.0.20

> **Status (0.70.21):** acted on: F1 (the landscape grid installs only after its safety parts),
> F2 (folder categories only once the order hook is in, 0.70.24), F3 (the loader's idle wait finds its names at install and falls back to Nothing's own), F4
> (the icon pack entry takes the class of an existing entry), F6 (the folder menu checks the
> database method before adding). Still open: the resolver work (DexKit or reflection by
> signature) for the obfuscated names, F5 (types checked in 0.70.23; same-type swaps not), F7 and the per-unit all-or-nothing installs.

Scope: the code that runs inside `com.nothing.launcher` 4.0.20 (versionCode 40020216): `launcher/*`,
`xposed/ModuleEntry.java` (launcher branch; `xposed/HookLoader.kt` since 0.70.35), `xposed/Hooks.java` (install semantics),
`wallpaper/HomeShown.java`, `widgets/WidgetApps.java`. Checked against the jadx sources
(`.../scratchpad/out/sources`) and the decoded resources (`re/nlres/resources/res`, decoded for this
report).

Snapshot: module sources as of 2026-11-11 ~22:50 UTC (versionName 0.70.20 in build.gradle; the
tree was being edited while this was written — `Hooks.skipped`/`HookReport` appeared mid-audit — so
line numbers can drift by a few lines). No module file was edited.

Not in scope / no launcher dependency (checked):
- `anim/*`: no reflective access and no launcher class names at all (grep for `XposedHelpers`,
  `launcher3`, `com.nothing`, `getIdentifier`, `getClass().getName` is empty). Covers are plain Views
  the launcher hosts; nothing to resolve.
- `widgets/WidgetApps.java` runs in `com.nothing.hearthstone` / `com.nothing.cardservice`, hooks only
  framework `Activity.onCreate/onConfigurationChanged`. No launcher dependency.
- `wallpaper/HomeShown.java` runs in the wallpaper apps; only the broadcast action
  `com.dressence.nothinglauncherplus.HOME_SHOWN` sent by `Launcher.hookHomeShown`. No launcher symbol.
- `sound/LauncherSounds*` does not exist; it is `launcher/LauncherSounds.java` (covered).

Legend for "Obf?": **OBF** = R8-renamed name used literally (breaks on any rebuild);
**struct** = obfuscated class/member reached structurally (by signature/probe, no literal name);
**kt** = Nothing Kotlin member whose source name happens to survive R8 in 4.0.20 (keep rule not
guaranteed); **aosp** = AOSP Launcher3 name (stable within an Android release, moves across
QPRs); **nt** = Nothing-added but readable name; **res** = resource name; **str** = string match;
**const** = literal constant mirrored in the module.

"Present?" = verified in 4.0.20 jadx output (signature and, where relevant, semantics). Every row
below is present and matching unless marked MISMATCH/NOTE.

---------------------------------------------------------------------------------------------------

## 0. How a missing name fails today

`Hooks.install(name, installer)` (xposed/Hooks.java ~l.42) catches any Throwable from the installer,
logs it and records it for HookReport. Consequences that matter for future-proofing:

1. **Units are not atomic.** An installer that hooks A, then B, then fails on C leaves A and B
   installed. Many units interleave "find" and "hook" (listed per row as *half-applied*).
2. **Separate units that only make sense together are not linked.** E.g. the landscape cell mapper
   and its delete/DB guards; the Smart category rewrite and the category order hook (§2).
3. **Runtime-only lookups** (`XposedHelpers.callMethod/getXField` inside callbacks) are not
   validated at install. Inside an `XC_MethodHook` before/after callback LSPosed catches and logs the
   error and the original runs (clean). Inside an `XC_MethodReplacement` the throwable is *set on the
   call* (the launcher sees the exception). Inside code handed to the launcher (Runnables, listeners)
   it is caught only where `Hooks.guard` wraps it.
4. `Launcher.install` itself is not wrapped: the constructors `new SmartDrawer(..)`, `new
   FolderCovers()`, `new Landscape(..)`, `new LandscapeGrid(..)`, `new LandscapeSurfaces(..)`,
   `LauncherSounds.install` run unguarded, but none of them touch launcher symbols (checked), so this
   is fine today. `DotIconPack.install` (called from inside the "dot-matrix icons" unit) hooks
   `Launcher.onCreate` unguarded — a failure there only loses that unit.

---------------------------------------------------------------------------------------------------

## 1. Dependency table

Columns: module file:line | what it uses | Obf? | present in 4.0.20 & matching | stable anchor
to re-find it | what breaks if missing (skip vs half-applied/crash)

### 1.1 Launcher.java (module) — lifecycle, home-shown, loader idle, reload receiver

| module file:line | uses | Obf? | 4.0.20 | stable anchor | if missing |
|---|---|---|---|---|---|
| Config.java:27 / everywhere | class `com.android.launcher3.Launcher` | aosp | yes | AOSP name; `Launcher.ACTIVITY_TRACKER`; activity in manifest via `NTLauncher extends Launcher` | every launcher unit skips (clean, each unit fails on findClass) |
| Config.java:28; Landscape.java:264 | class `com.nothing.launcher.NTLauncher` | nt | yes | launcher activity in AndroidManifest (`android.intent.category.HOME`) | "landscape config change" skips (clean) |
| Launcher.java:67 | `Launcher.onHandleConfigurationChanged()` (protected, no args) | aosp | yes | AOSP; called from `StatefulActivity.handleConfigurationChanged` | "lifecycle after a turn" skips (clean) |
| Launcher.java:73-90 | `getLifecycle()` → `LifecycleRegistry.getCurrentState()/setCurrentState(State)` | aosp/androidx | yes (`BaseActivity.getLifecycle` returns `LifecycleRegistry`) | `BaseActivity implements LifecycleOwner`; androidx class kept un-obfuscated in this APK | runtime inside guard → logged (clean) |
| Launcher.java:108 | `Launcher.onResume()`, `onPause()` | aosp | yes | Activity framework | "home shown" skips |
| Launcher.java:131 | `com.android.launcher3.model.LoaderTask.waitForIdle()` (protected synchronized, no args) **replaced** | aosp | yes | AOSP; body `mLauncherBinder.newIdleLock(this)` loop `awaitLocked(1000)` | install skips cleanly if method missing |
| Launcher.java:137-141 | fields `LoaderTask.mLauncherBinder`, `LoaderTask.mStopped`; `BaseLauncherBinder.newIdleLock(Object)` → `util.LooperIdleLock.awaitLocked(long)` | aosp | yes | AOSP names; `LooperIdleLock` is the only return type of `newIdleLock` | **CRASH PATH**: looked up only at run time inside an `XC_MethodReplacement`; if any is renamed the exception is thrown into `LoaderTask.run` on every load → home never finishes loading. Not validated at install (see §2 F3) |
| Launcher.java:153 | `Launcher.onCreate(Bundle)` | aosp | yes | framework | "reload receiver" skips; also HomeRestore not registered |

### 1.2 SmartDrawer.java — custom Smart-drawer folders, loose apps, covers

| module file:line | uses | Obf? | 4.0.20 | stable anchor | if missing |
|---|---|---|---|---|---|
| SmartDrawer.java:52-53 | package `com.nothing.launcher.allapps.smart.` + `SmartFolderIcon` | nt | yes | layout `smart_folder_all_app_icon` root view class; XML-inflated views keep class names | most Smart units skip |
| :54 | `SmartFullSheet` | nt | yes | layout root (`R$id.folder_name` inside); `AbstractFloatingView` subclass | "sheet title" skips |
| :55, :148 | `model.data.ItemInfo.mStatusInfo` (public field, type `x7.c` = AppStatusInfo) | nt field / **struct** type | yes | field of `ItemInfo` whose type's `toString()` starts `"AppStatusInfo("`; `AppInfo.getStatusInfo()` | "group category" skips (clean, nothing rewritten) |
| :154 | `x7.c()` no-arg constructor (Kotlin default ctor) | struct | yes (`public c()`) | defaults `intentUriString=""`, `category="others"`, `categorySource="local"` | IllegalState → skip |
| :156-170 | x7.c String fields found by probe value: `"others"` → category (`f26227f`), `""` → intent URI (`f26222a`) | struct | yes | values set by the default ctor | skip (throws "layout not recognised") |
| :174-189 | x7.c group getter found by probe: the only no-arg String getter returning "others" when category="unknown" (`n()`) | struct | yes (`n()`: `y.d(f, "unknown") ? "others" : f`) | `EnvironmentCompat.MEDIA_UNKNOWN` + `"others"` in body; called from `smart.h.updateAdapterItems` | skip; **but see §2 F2** (category rewrite without the order hook hides apps) |
| :64-66 | strings `"others"`, `"unknown"`, `"hidden icons"` | str | yes (`x7.c`, `smart.h:233`) | category keys of AppStatusInfo / smart list | wrong grouping, silent |
| :56, :241 | `allapps.AlphabeticalAppsList` (all ctors hooked) | aosp | yes | AOSP | "smart list" skips |
| :246, :472, :536 | subclass detection `getClass().getName().startsWith(SMART_PKG)` → `smart.h` (list), `smart.e` (adapter) | struct | yes | package prefix | silently no-op if Nothing moves the classes out of the package |
| :262-277 | order method on `smart.h`: the only private, non-static, no-arg method returning `List` (`e()`) | struct | yes (unique) | reads `OrderEvent.getMainOrder/getWorkOrder().getLastOrder()`, removes `"hidden icons"` | returns null → **list not processed, silently**; with "group category" active the custom-folder apps disappear (§2 F2) |
| :287 | `AlphabeticalAppsList.onAppsUpdated()` declared on `smart.h` | aosp override | yes | AOSP | thrown inside a constructor callback (logged); order hook already installed → half |
| :295-306 | `updateAdapterItems()` (declared on `smart.h`) | aosp override | yes | AOSP | "loose apps" sub-unit skips |
| :339-352 | `BaseAllAppsAdapter$AdapterItem`; inner class of `smart.h` with ctor `(List, List)` (DiffUtil.Callback, `smart.h$a`) | aosp / struct | yes | `extends DiffUtil.Callback` | "loose apps" skips cleanly (resolved before hooking — good pattern) |
| :360-392 | fields `mAdapterItems`, `mAccessibilityResultsCount`, `mAdapter`; `AdapterItem.viewType`, `AdapterItem.asApp(AppInfo)`, `isCountedForAccessibility()` | aosp | yes | AOSP | runtime in hook (logged); list restored only partly → guarded by the early `apps == null` return |
| :69-71 | view types `256` (spacer), `65536` (hidden tile), `131072` (folder tile) | const | yes (`BaseAllAppsAdapter.onCreateViewHolder` 256 = blank `View`; `smart.h` 131072/65536; `smart.e` binds 131072/65536) | literals inside `smart.h.updateAdapterItems` / `smart.e.onCreateViewHolder` | silently wrong spans / no loose apps |
| :397-399 | `androidx.recyclerview.widget.DiffUtil.calculateDiff(Callback, boolean)`, `DiffResult.dispatchUpdatesTo(Adapter)` | androidx | yes (not obfuscated) | androidx public API | runtime |
| :467-481 | `allapps.AllAppsGridAdapter` (ctors), `setAppsPerRow(int)` | aosp | yes | AOSP | "smart adapter" skips |
| :487 | `getAppCountPerRow()` declared on `smart.e` (returns 2) | aosp override | yes | AOSP | "smart grid columns" skips (inside ctor callback) |
| :502 | `BaseAllAppsAdapter.mActivityContext` + `ActivityContext.getDeviceProfile()` | aosp | yes | AOSP | caught → null → 2 columns |
| :511 | class-name test `.SmartEntranceIcon` | str | yes (`com.nothing.launcher.hiddenapps.view.SmartEntranceIcon`) | layout class | spacing wrong, silent |
| :517-549 | `AllAppsGridAdapter$GridSpanSizer.getSpanSize(int)`; its outer-adapter field (by type); `mApps.getAdapterItems()`; `mGridLayoutMgr.getSpanCount()` | aosp | yes | AOSP | "loose app spans" skips |
| :563-591 | `NTLauncherAllAppsContainerView.gridDirection` field → its type `smart.c` (ItemDecoration): the int field (gap), boolean field (rtl), `getItemOffsets(Rect, View, RecyclerView, State)` | kt field / struct | yes (`private final smart.c gridDirection`; `c(int,boolean)`) | `smart.c extends RecyclerView.ItemDecoration`, ctor `(int, boolean)`; instantiated in `NTLauncherAllAppsContainerView` | "loose app spacing" skips (clean, all resolved before hook) |
| :635-641 | `SmartFolderIcon.bind(x)` with one param → group item class `smart.e$b` | nt + struct | yes (`bind(e.b folderData)`) | `e$b extends BaseAllAppsAdapter.AdapterItem`, one String + one List field | "folder app order"/"folder tile" skip |
| GroupItems.java:31-46 | `e$b` fields: the only String instance field (category), the only List field (apps) | struct | yes (`f14472a` String default "others", `f14473b` List) | as above | returns null → callers return early (clean) |
| :647-660 | `e$b$a.a(int, String, List)` → `e$b` (Companion factory) | struct | yes | only (int,String,List)→e$b method; called with 131072/65536 from `smart.h` | "folder app order" skips |
| :686 | `AppInfo.componentName` | aosp | yes | AOSP | rank = MAX (unordered), silent |
| :699-705 | `SmartFolderIcon.getFolderName()`, `getFolderIconContainer()` | kt | yes | Kotlin property getters of `folderName` / `folderIconContainer` | runtime in hook |
| :721-727 | **`DeviceProfile` (v6.h) `.z()` → smart profile `smart.z`; `.i()`, `.c()`, `.e()`** (tile width, tile height, preview-container height) | **OBF** | yes (`SmartFolderIcon$a.b`: width=`z().i()`, height=`z().c()`, name=`h()`, pad=`g()`, container=`e()`) | `smart.z` = type of `SmartFolderContainer.smartProfile`; log tag `"SmartProfile"`, strings `"FolderWidth = "`, `"FolderHeight = "`; v6.h method returning `smart.z` | caught → keeps creation size (clean) **unless renamed onto other int getters — `smart.z` has 13 `int a()..m()`; a reshuffle silently gives wrong tile sizes (no exception)** |
| :744-770 | `SmartFullSheet$a` method `(ActivityContext, SmartFolderIcon) → SmartFullSheet` (`b`) | struct | yes | only 2-param companion method returning `SmartFullSheet` with 2nd param `SmartFolderIcon`; called from `SmartFolderIcon$a.c` (click listener) | "sheet title" skips |
| :765 | `SmartFullSheet.folderName` (TextView) | kt | yes (`private TextView folderName`, `R$id.folder_name`) | view id `folder_name` (`findViewById` would avoid the field) | runtime in hook |
| :115, :128 | `Launcher.dispatchDeviceProfileChanged()`, `onStateSetStart(LauncherState)`, `LauncherState.ALL_APPS` | aosp | yes | AOSP | units skip |

### 1.3 FolderCovers.java / FolderMenu.java / GroupItems.java

| module file:line | uses | Obf? | 4.0.20 | stable anchor | if missing |
|---|---|---|---|---|---|
| FolderCovers.java | none (adds own child views into the tile's FrameLayout) | — | — | — | — |
| FolderMenu.java:30,52,53 | `views.OptionsPopupView`, `$OptionItem(Context,int,int,EventEnum,OnLongClickListener)` | aosp | yes | AOSP | "folder menu" skips |
| :32,54 | `logging.StatsLogManager$LauncherEvent.IGNORE` | aosp | yes (enum `IGNORE(-1)`) | AOSP | skip |
| :33,34,35,36 | `model.data.FolderInfo`, `model.data.AppInfo`, `util.Executors.MODEL_EXECUTOR`, `LauncherState.NORMAL` | aosp | yes | AOSP | skip |
| :60 | `SmartFolderIcon.onFinishInflate()` (replaces the long-click listener) | nt | yes | View override | skip |
| :87-115 | `getFolderName()`, `Launcher.getLauncher(Context)`, `getDragLayer().getDescendantRectRelativeToSelf(View,Rect)`, `OptionsPopupView.show(ActivityContext, RectF, List, boolean)` | aosp/kt | yes | AOSP | caught in safeShow (clean) |
| :98-102,136-145 | resources `string/add_to_home_screen`, `drawable/ic_install_no_shadow`, `string/share_card_drawer_detail_edit`, `drawable/ic_setting` | res | yes (public.xml) | resource names | item omitted (clean) |
| :157-193 | `FolderInfo()` ctor, `.title`, `.add(ItemInfo)`, `AppInfo.makeWorkspaceItem(Context)`, `ItemInfo.rank`, `.id`, `Launcher.getModel()/getModelWriter()`, `LauncherModel.addAndBindAddedWorkspaceItems(List)`, `ModelWriter.addItemToDatabase(ItemInfo,int,int,int,int)`, `getStateManager().goToState(state)` | aosp | yes (`ItemInfo.id` shows as `f9650id` in jadx, real name `id`) | AOSP | **half-applied**: the folder is added and bound to the workspace *before* `addItemToDatabase` is looked up on the model thread; a rename leaves an empty folder on the home screen (§2 F6) |

### 1.4 SettingsEntry.java

| module file:line | uses | Obf? | 4.0.20 | stable anchor | if missing |
|---|---|---|---|---|---|
| :22,32 | `com.nothing.launcher.setting.allapp.AllAppSettingsFragment.onCreatePreferences(Bundle,String)` | nt | yes | `PreferenceFragmentCompat` subclass that loads `xml/all_app_settings_preferences` | "settings entry" skips |
| :24 | preference key `pref_category_all_app_type` | str/res | yes (in `res/xml/all_app_settings_preferences.xml`) | XML key | falls back to the screen (clean) |
| :27,69 | `com.nothing.launcher.setting.homesettings.LauncherSettingsFragment.onCreatePreferences(Bundle,String)` | nt | yes | fragment class name in settings activity | "settings module row" skips |
| :23,31-91 | `androidx.preference.Preference` + `setKey/setPersistent/setIconSpaceReserved/setTitle/setSummary/setIntent`, `PreferenceGroup.addPreference/findPreference`, `PreferenceFragmentCompat.findPreference/getPreferenceScreen/requireContext` | androidx | yes (not obfuscated) | androidx public API | clean (inside the after-hook) |

### 1.5 DrawerBackground.java

| module file:line | uses | Obf? | 4.0.20 | stable anchor | if missing |
|---|---|---|---|---|---|
| :23,34 | `uioverrides.states.AllAppsState.getWorkspaceScrimColor(Launcher)` | aosp | yes | AOSP | unit fails (whole "drawer background") |
| :26,135-155 | **`p7.e.e(Context) → int`** (instance method on Kotlin `object`, returns `context.getColor(R.color.nt_all_apps_scrim_color_nt_mono)`) | **OBF** | yes | caller `AllAppsState.getWorkspaceScrimColor` → `p7.e.f21550a.e(launcher)`; body reads color `nt_all_apps_scrim_color_nt_mono`; `p7.e` has `public static final p7.e` INSTANCE field; Kotlin param name `"context"` | **clean**: `Hooks.skipped`, falls back to the state hook. Wrong-class risk guarded: result faded only if it equals the scrim colour |
| :27,151 | color `nt_all_apps_scrim_color_nt_mono` | res | yes | resource | no fading (clean) |
| :51-61 | `NTLauncherAllAppsContainerView` ctors + `updateCustomColor()` (private final) | nt/kt | yes | Nothing's container (`allapps.search`) | `findClassIfExists` → skipped; `hookAllMethods` on a renamed method hooks nothing (silent) |
| :68-85 | `Launcher.ACTIVITY_TRACKER.getCreatedContext()`, `getStateManager().getState()`, `getScrimView()`, `getAppsView()` | aosp | yes | AOSP | change listener throws; `ConfigStore.reloadAndNotify` catches each listener (clean, logged) |
| :103-117 | `foregroundPaint` (kt, private final Paint), `mScrimColor`, `mHeaderProtectionColor` (AOSP `ActivityAllAppsContainerView`), `getSearchRecyclerView()` | kt/aosp | yes | Paint field is the only `Paint` instance field of the class | inside guard (clean, partial fade possible) |

### 1.6 Landscape.java — rotation, profiles, landscape geometry, Smart profile

| module file:line | uses | Obf? | 4.0.20 | stable anchor | if missing |
|---|---|---|---|---|---|
| :39,93-99 | `states.RotationHelper.notifyChange()` (private), `mIgnoreAutoRotateSettings`, `mHomeRotationEnabled` | aosp | yes | AOSP | "home rotation" skips |
| :104-110 | `Launcher.ACTIVITY_TRACKER`, `ContextTracker.getCreatedContext()`, `getRotationHelper()` | aosp | yes | AOSP | listener runtime |
| :140,172-174 | `Launcher.onCreate`, `getDeviceProfile()`, `DeviceProfile.widthPx/heightPx` | aosp | yes | AOSP | caught in onPreDraw (returns true) |
| :40,209-216 | `InvariantDeviceProfile.initGrid(...)` (4-arg private: Context, DisplayController.Info, DisplayOption, boolean) | aosp | yes | AOSP | hookAllMethods: if renamed, silently nothing hooked |
| :218-225 | `InvariantDeviceProfile.getDeviceProfile(Context)`, `getRecentActivityDeviceProfile(Context)` | aosp / **nt** | yes | `getRecentActivityDeviceProfile` is Nothing's (returns from `recentActivitySupportedProfiles`) | runtime in before-hook (clean) |
| :230 | `Launcher.initDeviceProfile` (deopt) | aosp | yes | AOSP | — |
| :231 | `dispatchDeviceProfileChanged()` | aosp | yes | | |
| :264-288 | `NTLauncher.handleConfigurationChanged(Configuration)`; `StatefulActivity.mOldConfig`, `mOldRotation`; `onHandleConfigurationChanged()` | nt/aosp | yes; Nothing's body logs `"Skip to handle the config change for the landscape orientation."` | log string; AOSP base `StatefulActivity.handleConfigurationChanged` | before-hook throws → original runs (clean). If `mOldRotation` renamed after `onHandleConfigurationChanged()` ran: double relayout, no corruption |
| :301-316 | `InvariantDeviceProfile.recentActivitySupportedProfiles` (Nothing), `DeviceProfile.copy(Context)` | nt / aosp | yes | Nothing field next to AOSP `supportedProfiles` | runtime |
| :327-341 | `SmartFolderContainer.smartProfile` field → `smart.z`; its only method `(Context, X)` → X = Nothing DeviceProfile `v6.h` | kt / struct | yes (`z.n(Context, v6.h)`) | `v6.h extends DeviceProfile` (the only concrete subclass); z.n strings `"deviceProfile"`, `"SmartProfile"` | "landscape layout"/"landscape smart drawer" skip |
| :38,344 | `Utilities.calculateTextHeight(float)` | aosp | yes | | skip |
| :346 | `DeviceProfile.getIconSizeWithOverlap(int)` (private) | aosp | yes | | skip |
| :350-439 | on **v6.h** (findAndHookMethod = declared there): `earlyInit(Resources)`, `getDesiredIconDrawablePadding()`, `getFixedCellWidth()`, `getFixedCellHeight()`, `getConfiguredGridHorizontalMargin(Resources)`, `getQsbOffsetY()`, `adjustWorkspaceLabelPadding(boolean)`, `updateCellLayoutPadding(Resources)`, `updateHotseatSizes(int)`, `updateHotseatDsbWidth(Context)`, `updateWorkspacePadding()`, `getHotseatLayoutPadding(Context)`, `getCalculatedWorkspaceSpringLoadScale(Context)` | aosp (abstract in DeviceProfile, overridden in v6.h) | all yes, all declared in v6.h | AOSP abstract method names on `DeviceProfile` | each its own unit → **half-applied geometry** possible (some replaced, some not; visual only) |
| :350-437 | DeviceProfile fields `availableRatio`, `mIconDrawablePaddingOriginalPx`, `iconScale`, `iconSizePx`, `iconDrawablePaddingPx`, `iconTextSizePx`, `cellWidthPx`, `cellHeightPx`, `cellYPaddingPx`, `hotseatCellHeightPx`, `hotseatBarSizePx`, `mHotseatBarEdgePaddingPx`, `mHotseatBarWorkspaceSpacePx`, `workspacePadding`, `edgeMarginPx`, `mInsets`, `cellLayoutPaddingPx`; methods `getCellSize()`, `getCellLayoutSpringLoadShrunkBottom(Context)`, `getCellLayoutSpringLoadShrunkTop()`, `getCellLayoutHeight()` | aosp | all yes | AOSP | caught per replace (falls back to Nothing's) |
| :383 | dimen `cell_layout_padding` | res | yes | | 0 padding |
| :501-507 | `isLandscape`, `isPhone`, `isVerticalBarLayout()`, `isSeascape()` | aosp | yes | | throws inside every callback → each falls back |
| :553-554 | `DeviceProfile.updateIconSize(float, Context)` | aosp | yes | | skip |
| :585-622 | `updateFolderIconSize()`, `folderIconOffsetYPx`, `folderIconSizePx` | aosp | yes | | caught |
| :627-634 | `Hotseat.resetLayout(boolean)`, `getCountX/Y()` | aosp | yes | | log only |
| :648-649 | `availableWidthPx`, `availableHeightPx` | aosp | yes (final, written by reflection) | | |
| :660-697 | **`smart.z.n(Context, v6.h)` hooked by the literal name `"n"`** | **OBF** | yes | same as above: the only `(Context, <DeviceProfile subclass>)` method of `smart.z` — the module already finds it structurally at :333 but then hooks by name | "landscape smart drawer" skips (clean: unique signature) |
| :671-672 | dimens `smart_all_app_padding_h`, `all_apps_panel_gap` | res | yes (read by `z.n` too) | | 0 |

### 1.7 LandscapeGrid.java — turned home grid, collision/DB guards

| module file:line | uses | Obf? | 4.0.20 | stable anchor | if missing |
|---|---|---|---|---|---|
| :59-60,200 | `celllayout.CellPosMapper`, `$CellPos(int,int,int)` | aosp | yes | | "landscape grid" skips |
| :73,199,303,1253 | `views.ActivityContext.lookupContext(Context)` (static interface method) | aosp | yes | | runtime |
| :236-250 | `DeviceProfile.inv`, `InvariantDeviceProfile.numColumns/numRows`, `cellLayoutPaddingPx`, `getCellLayoutWidth()`, `getCellLayoutHeight()` | aosp | yes | | runtime |
| :313 | `Launcher.ACTIVITY_TRACKER.getCreatedContext()` | aosp | yes | | |
| :326-336 | `ItemInfo.cellX/cellY/spanX/spanY/container/screenId/id` | aosp | yes | | |
| :80 | `CONTAINER_DESKTOP = -100` | const | yes (`Launcher.bindInflatedItems`: `itemInfo.container == -100`) | `LauncherSettings.Favorites.CONTAINER_DESKTOP` | silent |
| :81 | `CELL_LAYOUT_WORKSPACE = 0` vs `CellLayout.mContainerType` | const | yes (`CellLayout.WORKSPACE = 0`, `mContainerType` from styleable) | `CellLayout.WORKSPACE` static | silent |
| :353 | `CellPosMapper.mapModelToPresenter(ItemInfo)` | aosp | yes | | unit skip |
| :363 | `Launcher.getCellPosMapper()` | aosp | yes | | |
| :405-410 | `getWorkspace().getScreenWithId(int)`, `CellLayout.getShortcutsAndWidgets()`, `CellLayoutLayoutParams.getCellX/getCellY/cellHSpan/cellVSpan/setCellX/setCellY` | aosp | yes | | |
| :434-447 | `Launcher.bindInflatedItems` (2 overloads), `bindItems(List,boolean)`, `bindAppWidget(LauncherAppWidgetInfo)`, `finishBindingItems(IntSet)` — `hookAllMethods` | aosp | yes | | hookAllMethods returns empty silently on rename (no exception, no log) |
| :454 | `CellLayout.isOccupied(int,int)` | aosp | yes | | throws → **delete guard below not installed** |
| :87,465-476 | `ModelWriter.deleteItemFromDatabase(ItemInfo, String)` + reason prefix `"Collision while binding"` | aosp + str | yes (`Launcher.bindInflatedItems`: `"Collision while binding workspace item: "`) | AOSP log text | **item loss path** (§2 F1) |
| :508-516 | `Workspace.mWorkspaceScreens` (`IntSparseArrayMap`, `size()/valueAt()`), `BubbleTextView.getIconSize()` | aosp | yes | | runtime |
| :637-652 | `Launcher.rebindCallbacks()`, `finishBindingItems` | aosp | yes | | skip |
| :679 | `dispatchDeviceProfileChanged()` | aosp | yes | | |
| :708-747 | `getHotseat()`, `Hotseat.mHasVerticalHotseat`, `getCountY()`, `markCellsAsUnoccupiedForView/OccupiedForView(View)`, `DeviceProfile.numShownHotseatIcons`, `resetCellSize(DeviceProfile)`, `setGridSize(int,int)`, `getCellXFromOrder/getCellYFromOrder(int)` | aosp | yes | | inside guard |
| :768-804 | `CellLayout.mCountX/mCountY`, `setGridSize`, `applyDisplaySpan(int,int)` | aosp/nt | yes | | |
| :985, 1597, 1641, 1773 | `BgDataModel.itemsIdMap` (`IntSparseArrayMap`, iterable, `get(int)`) | aosp | yes | | |
| :1011 | `inv.recentActivitySupportedProfiles` | nt | yes | | |
| :1086 | `DeviceProfile.getCellSize(Point)` | aosp | yes | | unit skip |
| :1104-1115 | DeviceProfile ctors, `cellLayoutBorderSpacePx` | aosp | yes | | |
| :1136-1150 | **v6.h widget padding: the only public `Rect m(int,int)` (`o(int,int)`)** | struct | yes (unique) | used by `NavigableAppWidgetHostView/NavigableCardWidgetHostView/BaseLauncherCardHostView.applyDisplaySpan` | `Hooks.skipped` (clean) |
| :75-78,1190-1196 | `widget.NavigableAppWidgetHostView`, `com.nothing.launcher.card.NavigableCardWidgetHostView`, `BaseLauncherCardHostView` `.applyDisplaySpan(int,int)` | aosp/nt | yes | | per class, silent |
| :1243-1273 | `CellLayout.onMeasure(int,int)`, `mBorderSpace` | aosp | yes | | |
| :1306 | `Workspace.insertNewWorkspaceScreen(int,int)` | aosp | yes | | |
| :1324 | `CellLayout.addViewToCellLayout(View,int,int,CellLayoutLayoutParams,boolean)` | aosp | yes | | |
| :1369-1380 | `CellLayout.mOccupied` (`GridOccupancy.markCells(int,int,int,int,boolean)`), `markCellsAsOccupiedForView/UnoccupiedForView` | aosp | yes | | |
| :1392-1431 | `AppWidgetResizeFrame.setupForWidget(...)` — 1st param is **Nothing's obfuscated `com.nothing.launcher.widget.b`** (interface with `getView()`) — hooked with hookAllMethods; `mMaxHSpan`, `mMaxVSpan` | aosp + **struct** | yes | only uses `args[0].getView()` | hookAllMethods: silent if renamed |
| :1450-1500 | `ModelWriter` ctors, `mBgDataModel`, `updateItemInfoProps(ItemInfo,int,int,int,int)` (private), `modifyItemInDatabase(ItemInfo,int×6)` | aosp | yes | | "landscape grid safety" skips → **§2 F1** |
| :64,1515-1550 | `ModelDbController.update(ContentValues,String,String[])`, `insert(ContentValues)`; where-clause regex `_id=(\d+)` (`LauncherDbUtils.itemIdMatch`); columns `container`,`screen`,`cellX`,`cellY`,`spanX`,`spanY`,`_id` | aosp + str | yes (`x7.r extends ModelDbController` does not override update/insert) | `LauncherSettings.Favorites` column names; `LauncherDbUtils.itemIdMatch` | "landscape portrait guard" skips → **§2 F1** |
| :1755 | `ModelWriter.moveItemInDatabase(ItemInfo,int,int,int,int)` | aosp | yes | | |
| LandscapeMemory.java:14 | own file `nlplus_landscape_places` in the launcher's data | — | — | — | — |
| PortraitGeometry.java:38-83 | `getCellSize(Point)`, `cellLayoutBorderSpacePx`, `iconSizePx`, …, `inv.supportedProfiles` | aosp | yes | | |

### 1.8 LandscapeSurfaces.java

| module file:line | uses | Obf? | 4.0.20 | stable anchor | if missing |
|---|---|---|---|---|---|
| :27,64-94 | `com.android.quickstep.util.RecentsOrientedState` ctors, `setFlag(int,boolean)` (private), `mContext` | aosp | yes; `updateHomeRotationSetting()` is emptied by Nothing (confirmed) | AOSP | runtime, caught |
| :35 | **constant `FLAG_HOME_ROTATION_ALLOWED = 4`** | const | **NOTE**: in 4.0.20 the field is `FLAG_HOME_ROTATION_ALLOWED_IN_PREFS = 4` (value matches) | read `getStaticIntField(RecentsOrientedState, "FLAG_HOME_ROTATION_ALLOWED_IN_PREFS")`, fallback 4 | silent wrong flag if AOSP renumbers |
| :28,92-94 | `com.android.quickstep.SystemUiProxy.INSTANCE` (`DaggerSingletonObject.get(Context)`), `setHomeRotationEnabled(boolean)` | aosp | yes | | caught |
| :29,123-155 | `folder.Folder.centerAboutIcon()` (private), `mFolderMarginTop`, `mHeaderHeight`, `mActivityContext`, `mFolderName`, `getFolderHeight()`, `animateOpen` | aosp | yes | | "landscape folders" partly applied (DP ctor hook first) |
| :113,167-180 | DeviceProfile `folderCellHeightPx`, `numFolderRows` (final), `mInsets`, `heightPx`, `folderContentPaddingTop`, `folderFooterHeightPx` | aosp | yes | | |
| :193-219 | `taskbar.TaskbarViewController.onRotationChanged(DeviceProfile)`, `taskbar.TaskbarActivityContext.setTaskbarWindowSize(int)`, `isPhoneGestureNavMode()` | aosp | yes | | hookAllMethods silent |
| :227 | `Settings.Secure "hide_navigation_bar"` | str | yes (read by `RotationTouchHelper`, `a8.h`, `z6.a`) | Nothing setting key | silent |

### 1.9 FolderSheetLandscape.java

| module file:line | uses | Obf? | 4.0.20 | stable anchor | if missing |
|---|---|---|---|---|---|
| :30,41 | `SmartFullSheet` | nt | yes | | skip |
| :43-55 | `SmartFullSheet.adapter` → `smart.x` (AllAppsGridAdapter), `.appsList` → `smart.y` (AlphabeticalAppsList); `getAppCountPerRow()` on each | kt field / struct | yes | the sheet's only `AllAppsGridAdapter` / `AlphabeticalAppsList` fields (by type, not name) | **half-applied**: both column hooks installed before `getDisplayRows` is looked up; if that fails, columns change but rows not |
| :56 | `setupSheet` (deopt) | kt | yes | | |
| :59-68 | `getDisplayRows(List)` (private, float) | kt | yes | only `(List)→float` method of the sheet | throws after the column hooks |
| :71-95 | `matchedDeviceProfile` (v6.h), `allAppsCellHeightPx`, `heightPx`, `titleContainer`, `folderName`, `DeviceProfile.getInsets()` | kt/aosp | yes | `matchedDeviceProfile` = only DeviceProfile-typed field | runtime in hook |

### 1.10 WidgetPickerLandscape.java — the most fragile file

| module file:line | uses | Obf? | 4.0.20 | stable anchor | if missing |
|---|---|---|---|---|---|
| :35,50 | **class `za.a0`** (widget picker profile) | **OBF** | yes | `NtWidgetsFullSheet.widgetsDeviceProfile` field type (and `za.a0.L.c(context)` at NtWidgetsFullSheet:2063); float constants `0.122826084f`, `0.052173913f`, `0.45410627f`, `0.88297874f`, `0.3326087f`, `0.2657005f`, `0.647343f`; reads dimens `nt_widget_row_padding`, `widget_preview_margin`, `widget_list_horizontal_margin`, `workspace_horizontal_padding`; calls `WindowManager.getMaximumWindowMetrics()` | unit skips (clean) |
| :51,53 | **`za.a0$a.c(Context)`** (Companion cache getter) | **OBF** | yes | the only method of the nested class `(Context) → a0` | hookAllMethods by name: rename → silently nothing hooked (or, worse, a different `c`) |
| :61,71,98 | **static `a0.N`** (cached profile) | **OBF** | yes (`private static a0 N`) | the only static field of type `a0` (besides Companion `L` of type `a0$a`) | caught per call |
| :93 | `new a0(Context)` | struct | yes | only ctor | |
| :107-125 | all `a0` ctors | struct | yes | | |
| :128-140 | framework `WindowMetrics.getBounds()` (hooked process-wide, active only while BUILDING) | framework | — | — | — |
| :253-288 | **fields `c`(width) `d`(height) `e`(Insets) `i`(top, private) `I`(Rect) `l`(float handle) `G`(float) `m`(sheet h) `n`(float) `B`,`C`,`D`(Rect) `E`,`H`,`J`,`K`(int) `r`(card) + method `e()` (recompute cells)** | **OBF ×17** | yes, all match the ctor formulas | value fingerprints (§3 A) | **HALF-APPLIED + SILENT**: `turned()` writes the fields one by one; a missing name throws midway (caught/logged) and leaves a half-turned profile cached in `a0.N` for the process life. A *renamed-but-existing* name (same type) is written without error → silently wrong picker layout |
| :36,144-170 | `com.nothing.launcher.widgets.view.NtWidgetsFullSheet.setInsets(Rect)`, `getPageIndicator()`, `mBottomPadding` (AOSP `WidgetsFullSheet`), `pageIndicatorDisappearedDistance`, `indicatorHeight` | nt/kt | yes | Kotlin names | caught |
| :183-198 | `widget.picker.WidgetsFullSheet.shouldRecreateLayout(DeviceProfile, DeviceProfile)` (private static), `isLandscape` | aosp | yes | | |
| :223-238 | `isInPickerDetails()`, `animationPageIndicatorByState(int,long)`, `setContentViewVisible(boolean)`, `resetPageIndicator()` (all private, Kotlin) | kt | yes | | hookAllMethods silent |

Order inside `install()`: cache hook → ctor hook → getBounds hook → setInsets → shouldRecreateLayout →
page-indicator hooks, all in one unit: any failure after the first leaves the earlier ones on.

### 1.11 PopupLandscape.java

| module file:line | uses | Obf? | 4.0.20 | stable anchor | if missing |
|---|---|---|---|---|---|
| :27,67 | `com.nothing.launcher.popup.NTPopupContainer` | nt | yes | | unit skips |
| :31,75 | `ArrowPopup.getPopupContainer()` (BaseDragLayer) | aosp | yes | | |
| :35,88 | `PopupContainerWithArrow.mContainerWidth` | aosp | yes | | |
| :83 | dimen `popup_container_margin` | res | yes | | 0 id → `getDimensionPixelSize(0)` throws inside guard (clean) |
| :102-105 | `NTPopupContainer.addDeepShortcutEntries(int,int)`, `addSystemShortcutEntries(int,List,boolean)` (private, first arg = width), `populateAndShowForView` | nt | yes | | hookAllMethods silent on rename |
| :59,107-197 | `NTArrowPopup.setupForDisplay()`, `orientAboutObject()`, `createHighlightView(BubbleTextView)`, `ArrowPopup.mTempRect`, `BaseDragLayer.getInsets()` | nt/aosp | yes | | half-applied possible (sequential in one unit) |
| :60,117 | class name equality `com.android.launcher3.views.OptionsPopupView` | str | yes | | silent |

### 1.12 Gamepad.java / FocusRing.java

| module file:line | uses | Obf? | 4.0.20 | stable anchor | if missing |
|---|---|---|---|---|---|
| Gamepad.java:55-63,92-99 | `LauncherState.NORMAL/ALL_APPS`, `AbstractFloatingView`, `BubbleTextView`, `folder.BaseFolderIcon`, `popup.PopupContainerWithArrow`, `com.nothing.launcher.popup.NTPopupContainer` (optional), `keyboard.ItemFocusIndicatorHelper` | aosp/nt | yes | | "controller" skips |
| :106 | `Launcher.dispatchKeyEvent(KeyEvent)` | aosp | yes | | |
| :118 | `ItemFocusIndicatorHelper.draw(Canvas)` | aosp | yes | | **half**: key hook already on, focus watcher not |
| :132 | `NTArrowPopup.closeComplete()` | nt | yes | | half |
| :297-399 | `getStateManager().getState()/goToState()`, `RecyclerView.smoothScrollBy/getAdapter/getItemCount/smoothScrollToPosition`, `Workspace.scrollRight/scrollLeft/getPageCount/snapToPage(int)`, `Launcher.showDefaultOptions(float,float)`, `AbstractFloatingView.closeAllOpenViews(ActivityContext)`, `getAppsView().getActiveRecyclerView()`, `NTPopupContainer.showForView(View)` (static; falls back on NoSuchMethodError), `PopupContainerWithArrow.showForIcon(BubbleTextView)` | aosp/nt | yes | | runtime in key hook |
| :477,526-534 | class-name tests `"RecyclerView"`, `.SmartEntranceIcon`, `SmartFolderIcon` | str | yes | | silent |
| FocusRing.java:150 | `BubbleTextView.getIconBounds(Rect)` | aosp | yes | | |
| FocusRing.java:399-424 | class names `android.appwidget.AppWidgetHostView`, `com.nothing.cardhost.CardWidgetHostView`, contains `"Popup"` | str | yes (`com/nothing/cardhost/CardWidgetHostView`) | | silent |

### 1.13 RecentsRam.java

| module file:line | uses | Obf? | 4.0.20 | stable anchor | if missing |
|---|---|---|---|---|---|
| :39,49 | `com.android.quickstep.views.OverviewActionsView.onFinishInflate()` | aosp | yes | | skip |
| :61-92 | id `action_buttons` (LinearLayout in `layout/overview_actions_container.xml`), style `OverviewActionButton`, style `OverviewActionButtonHighlightColor`, id `action_screenshot`, dimen `overview_actions_button_spacing` | res | yes (all in public.xml) | | logged, button not added (clean) |

### 1.14 DotIcons.java / DotIconPack.kt / LiveIcons.java / LabelScroll.java / IconScreen.java

| module file:line | uses | Obf? | 4.0.20 | stable anchor | if missing |
|---|---|---|---|---|---|
| DotIcons.java:57,147-158 | `icons.FastBitmapDrawable` fields `mBitmapInfo`, `mPaint`, `mBadge`, `mScale`; `draw(Canvas)` (final), `drawInternal(Canvas,Rect)`; `icons.BitmapInfo.icon`, static `LOW_RES_ICON` | aosp | yes | | "dot-matrix icons" skips (fields resolved first — good) |
| DotIcons.java:276-278 | drawable class-name substrings `Clock`, `Preload`, `PlaceHolder` | str | yes (`ClockDrawableWrapper`, `PreloadIconDrawable`, `PlaceHolderIconDrawable`) | `extends FastBitmapDrawable` | silent |
| DotIcons.java:168-175 | `Launcher.onCreate/onResume` | aosp | yes | | |
| DotIconPack.kt:60 | `Launcher.onCreate` (not in its own unit) | aosp | yes | | loses the "dot-matrix icons" unit's tail |
| DotIconPack.kt:73,80 | **`k7.b(String packageName, String appName)`** (CachedIconEntity) | **OBF** | yes | log tag `"CachedIconEntity"`; strings `"appfilter"`, `"appfilter.xml"`, `"No appfilter.xml file. IOException is "`; Kotlin param names `"packageName"`,`"appName"`; three `private static final` fields of its own type (`"Nothing Icon"`, `"Themed Icon"`, `"Themed Icon Nothing"`) | findClass fails → entry skipped (clean). **If `k7.b` exists but is another class with a (String,String) ctor, the module puts a foreign object into Nothing's `ConcurrentHashMap<String,k7.b>` → ClassCastException in Nothing's picker (`la.e.w`) → Customisation crashes** (§2 F4) |
| DotIconPack.kt:74 | **`h7.d.a(Context, ConcurrentHashMap)`** (IconPackageReader: fills the pack map) | **OBF** | yes | log tag `"IconPackageReader"`, `"loadIconIfNeed: resolveInfo "`, Kotlin param name `"entityMap"`; `h7.d` is a Kotlin object (static `INSTANCE`-like field of own type) | entry unit skips (clean, exact signature) |
| DotIconPack.kt:93 | **`d7.h.i(String, BaseIconFactory, IconProvider) → Bitmap`** (IconPackManager preview) | **OBF** | yes | unique signature; log tag `"IconPackManager"`, `"tryRemovePackages: reset icon pack to SYSTEM"`, `"com.nothing.icon"`; companion `d7.h.f16772p` | preview unit skips (clean) |
| DotIconPack.kt:45 | `Settings.System "nothing_icon_pack"` | str | yes (`h7.b`, `PackageUpdatedTask`, `RestoreDbTask`) | | pack never seen as picked (feature off, clean) |
| LiveIcons.java:96-127 | FastBitmapDrawable fields, `isThemed()` (optional), `BubbleTextView.setNonPendingIcon(ItemInfoWithIcon)` (private), `BubbleTextView.mIcon`, `ItemInfo.getTargetPackage()` | aosp | yes | | skip |
| LabelScroll.java:39 | `BubbleTextView.onDraw(Canvas)` | aosp | yes | | skip |
| IconScreen.java | none | — | — | — | — |

### 1.15 LauncherSounds.java

| module file:line | uses | Obf? | 4.0.20 | stable anchor | if missing |
|---|---|---|---|---|---|
| :72-86 | `Launcher.onCreate/onResume/onPause` | aosp | yes | | per unit |
| :116-119 | `LauncherState.NORMAL/ALL_APPS`, `Launcher.onStateSetStart(LauncherState)` | aosp | yes | | |
| :135 | `Launcher.startActivitySafely(View, Intent, ItemInfo)` (returns RunnableList) | aosp | yes | | |
| :159-183 | `Folder.animateOpen()`, `Folder.handleClose(boolean)`, `AbstractFloatingView.mIsOpen`, `SmartFullSheet.handleClose(boolean)` | aosp/nt | yes | | half: folder hooks before sheet hook |
| :187-192 | `Workspace.notifyPageSwitchListener(int)`, `PagedView.mCurrentPage` | aosp | yes | | |
| :201 | `popup.ArrowPopup.show()` | aosp | yes | | |

### 1.16 HomeRestore.java

| module file:line | uses | Obf? | 4.0.20 | stable anchor | if missing |
|---|---|---|---|---|---|
| :49,84-88 | `LauncherAppState.getInstance(Context)`, `.getModel()`, `LauncherModel.getModelDbController()` (returns **`x7.r`**, obfuscated subclass of `ModelDbController`), `ModelDbController.getDb()` | aosp (+struct) | yes | AOSP names on base class | guarded, nothing written |
| :51 | DB file regex `launcher_(\d+)_by_(\d+)\.db` | str | yes (`LauncherFiles.GRID_DB_FILES`) | | message only |
| :50,73 | `Executors.MODEL_EXECUTOR` | aosp | yes | | |
| :125,236,243,323 | `LauncherModel.forceReload()`, `ModelDbController.generateNewItemId()`, `insert(ContentValues)`; columns `_id`, `container`, … | aosp | yes | | **partial restore** if a name fails mid-loop (rows already inserted stay; no forceReload) — not a loss |

### 1.17 Launcher-side constants and broadcast strings (module-defined, no launcher symbol)

`Config.ACTION_RELOAD`, `ACTION_RESTORE_ITEMS`, `ACTION_HOME_SHOWN`, `ACTION_OPEN_MEDIA_APP`,
`ACTION_EXPAND_NOTIFICATIONS`, `ACTION_RUNNING_SERVICES` — own strings, no dependency.

---------------------------------------------------------------------------------------------------

## 2. Flags: mismatches and half-applied / crashing failure modes

No name used by the module mismatches 4.0.20 (every class, method signature, field, resource and
string above was found; jadx's `f9650id` for `ItemInfo.id` is a jadx rename, the dex name is `id`).
One cosmetic: LandscapeSurfaces' `FLAG_HOME_ROTATION_ALLOWED = 4` mirrors AOSP's
`FLAG_HOME_ROTATION_ALLOWED_IN_PREFS = 4` (value matches; read it instead of hard-coding).

What a renamed/moved symbol would turn into, worst first:

**F1 — Landscape grid can make the launcher delete home-screen items (violates "never lose the
user's items").** `LandscapeGrid.install` (l.196-215) installs the cell mapper
("landscape grid": `mapModelToPresenter` returns turned/clamped cells) as its own unit, *before*
and independent of:
- "landscape grid arrangement" — `CellLayout.isOccupied` → false while binding, and the
  `deleteItemFromDatabase` veto for `"Collision while binding"` (l.454-476, isOccupied hooked first,
  the veto last in the same unit);
- "landscape grid safety" (ModelWriter) and "landscape portrait guard" (ModelDbController
  update/insert) that keep landscape cells out of the database.
`Launcher.bindInflatedItems` deletes any item whose mapped cell is occupied
(`getModelWriter().deleteItemFromDatabase(itemInfo, "Collision while binding workspace item: ...")`).
If `isOccupied(int,int)` or `deleteItemFromDatabase(ItemInfo,String)` is renamed in an update, the
mapper stays active, clamped cells collide (`Math.min(c, landscapeColumns-1)`) and items are deleted
from the DB on the first landscape bind. If only the DB guards fail, landscape positions are
written to the portrait DB, and the next portrait bind can collide and delete there (the veto is
only active while `current() != null`, i.e. in landscape).
Fix: resolve every member of all five units first; install the mapper only if the veto, the
isOccupied hook, ModelWriter and DB guards all installed (one "landscape grid" transaction), and
uninstall (`XC_MethodHook.Unhook`) whatever was hooked if a later step fails. Also make the veto
independent of the reason string (veto any `deleteItemFromDatabase` reached from
`bindInflatedItems` while the mapper is active), since the string is AOSP log text.

**F2 — Smart drawer: category rewrite without the order hook hides apps.** "group category"
(x7.c.n() → `nlplus…` keys) and "smart list" (order method merge) are separate units, and the order
method is found lazily in an `AlphabeticalAppsList` constructor callback (`findOrderMethod` returns
null silently if a second private no-arg `List` method appears). `smart.h.updateAdapterItems` only
emits groups whose key is in the order list, so apps assigned to custom folders vanish from the
Smart drawer (still in search; nothing deleted). Fix: have the category hook return its rewrite
only after the order hook is confirmed on that list class (flag set in `hookSmartListClass`), and
`Hooks.skipped` when `findOrderMethod` returns null (today it returns silently).

**F3 — `LoaderTask.waitForIdle` replacement can break loading.** `mLauncherBinder`, `newIdleLock`,
`mStopped`, `awaitLocked` are resolved at call time inside an `XC_MethodReplacement`; a rename
throws into the loader on every load (home screen and drawer never finish). Fix: resolve the two
fields and two methods (exact signatures) at install, skip the unit if any is missing, and fall back
to `XposedBridge.invokeOriginalMethod` on any Throwable at run time.

**F4 — DotIconPack can crash Nothing's Icon Pack picker.** `findClass("k7.b")` + `newInstance(entity,
PACK_ID, LABEL)` puts whatever class is named `k7.b` into Nothing's `ConcurrentHashMap` of
`k7.b`; after a rebuild `k7.b` can be any class with a `(String,String)` ctor (or fail). Fix: take the
entity class from the map itself (class of any existing value) or validate the candidate (≥1 static
field of its own type, `(String,String)` ctor, a `String` getter returning the package), and only then
insert. Same for `h7.d.a`: verify the declared map value type at first call.

**F5 — WidgetPickerLandscape writes 17 obfuscated fields of `za.a0` in sequence.** Missing name →
half-turned profile cached in static `N` for the process; renamed-onto-existing name → silent wrong
layout. Fix: fingerprint-validate the field map once (§3 A) before the first write, and write into a
fresh instance that replaces `N` only after all writes succeeded.

**F6 — FolderMenu "Add to home screen".** The folder is bound (`addAndBindAddedWorkspaceItems`)
before `ModelWriter.addItemToDatabase` is resolved on the model thread; a rename leaves an empty
folder icon. Fix: resolve `addItemToDatabase(ItemInfo,int,int,int,int)` and `makeWorkspaceItem`
before binding.

**F7 — SmartDrawer.resizeTile calls `z()`, `i()`, `c()`, `e()` by obfuscated name.** `smart.z` has
13 `int` getters `a()`…`m()`; a reshuffle keeps all names valid, so wrong sizes are applied without
any exception. Also `Landscape.hookSmartProfile` hooks `"n"` by name although the same method was just
found structurally (l.333) — pass the `Method` object instead.

**F8 — Units that hook in sequence (half-applied, visual only):** FolderSheetLandscape (columns
before rows), Gamepad (key hook before focus-indicator/menu hooks), PopupLandscape, LauncherSounds
"folders", LandscapeSurfaces "landscape folders", Landscape `replace()` set (each method its own
unit, so landscape geometry can be a mix of Nothing's and the module's), DrawerBackground (state
hook vs. source hook).

**F9 — `hookAllMethods` / `hookAllConstructors` / `deoptimizeAll` on a renamed name succeed with
zero members, silently** (LandscapeGrid bind methods, `initGrid`, `setupForWidget`,
`onRotationChanged`, `setTaskbarWindowSize`, `addDeepShortcutEntries`, `updateCustomColor`,
`setContentViewVisible`, `a0$a.c`, …). Fix: a helper `hookAllRequired(cls, name, hook)` that throws
(or `Hooks.skipped`) when the returned set is empty, and `deoptimizeAll` that reports when nothing
matched.

---------------------------------------------------------------------------------------------------

## 3. Most fragile dependencies, ranked, with resolver strategies

Ranking: literal obfuscated names first (break on any R8 rebuild, even a same-version hotfix),
then structural lookups that hinge on one Kotlin name, then Nothing-specific readable names, then
AOSP.

### Rank 1 — `za.a0` widget-picker profile (WidgetPickerLandscape) — 1 class + nested + 18 members OBF

A. Class:
- Reflection (no DexKit): `NtWidgetsFullSheet` → field whose type T satisfies: T has a private
  static field of type T, a nested class with a method `(Context) → T`, a public ctor `(Context)`,
  and exactly one `android.graphics.Insets` instance field. (Today the field is
  `widgetsDeviceProfile`; match by these properties, not the name.)
- DexKit: `findClass { matcher { usingNumbers(0.122826084f, 0.052173913f, 0.45410627f, 0.3326087f) } }`
  or `findMethod { matcher { name = "<init>"; paramTypes(Context); invokeMethods { add { name =
  "getMaximumWindowMetrics" } } ; usingNumbers(0.45410627f) } }` → declaringClass.
B. Cache getter / static: nested class's only method with `(Context)` param returning T; static
   field = the only static field of type T.
C. Fields by value fingerprint on the first instance made while BUILDING (W = bounds width, H =
   height, known to the hook): `c` = final int == W; `d` = final int == H; `e` = the Insets field;
   `i` = private int == round(H*0.122826084); `I` = Rect with `top == i` and `left == right ==
   dimen(workspace_horizontal_padding)`; `l` = float == H*0.052173913; `m` = int ==
   (int)((H+l)*0.5); `n` = float == m/(H-l); `G` = float == H*0.3326087; card `r` = int ==
   round(round(W*0.45410627)*0.88297874); `D` = Rect with `bottom == insets.bottom` and `left ==
   (W - 2*round(W*0.45410627))/2`; `B` = Rect with `left == (W - round(W*0.45410627 + previewMargin))/2`;
   `C` = Rect with `left == (W - round(W*0.90821254))/2`; `E` = int == (round(W*.45410627) - r)/2 +
   D.left; `H` = int == (W - 2E)/2; `J` (final) / `K` (non-final) = ints == W - (I.left+I.right);
   recompute method = the only private no-arg `void` method. Accept only if every role matches
   exactly one field; otherwise `Hooks.skipped`.
D. Better long-term (CLAUDE.md "don't fight Nothing"): instead of rewriting a finished profile,
   let Nothing's constructor compute it from the turned bounds for the placement fields — the module
   already intercepts `WindowMetrics.getBounds` while BUILDING; feed the turned rect for a second,
   throwaway construction and copy only the placement roles found by fingerprint. Fewer formulas
   mirrored in the module.
DexKit fit: good (numbers are unique), but the reflection route is enough here.

### Rank 2 — Smart profile accessors `DeviceProfile.z()`, `z.i()/c()/e()`, `z.n(Context, v6.h)` (SmartDrawer.resizeTile, Landscape.hookSmartProfile)

- `smart.z` class: type of `SmartFolderContainer.smartProfile` (Kotlin name); fallback: the only
  instance field of `SmartFolderContainer` whose type declares a method `(Context, D)` with
  `D extends DeviceProfile`. DexKit: `findClass { matcher { usingStrings("SmartProfile", "FolderWidth = ") } }`.
- `n`: the only `(Context, D extends DeviceProfile) → void` method of `smart.z` — the module
  already finds it (Landscape.java:333); hook that `Method` (no `"n"`).
- `DeviceProfile.z()`: on `D`, the only no-arg method whose return type == `smart.z`.
- `i/c/e` (tile width, tile height, previews height):
  - DexKit (exact): `findMethod { matcher { declaredClass(SmartFolderIcon$Companion) ; paramTypes(ActivityContext, ViewGroup) ; returnType(SmartFolderIcon) } }`
    then `getInvokes()` filtered to methods declared on `smart.z`, in call order: [0] width,
    [1] height, [2] name height, [3] name padding, [4] container height (4.0.20: i, c, h, g, e).
  - Reflection fingerprint (after `n` ran, all 13 getters read): width W = the getter with
    `Y == ceil(W*1.1111112)` (±1) for some getter Y (height); name N = getter == (int)(Y*0.22857143);
    container = getter == Y - N. Requires uniqueness; else skip.
  - Or no accessor at all (Nothing's mechanism): on a profile change, build one tile with Nothing's own
    factory (`SmartFolderIcon.Companion` `(ActivityContext, ViewGroup) → SmartFolderIcon`, found
    structurally) and copy its LayoutParams sizes; recycled tiles get exactly what Nothing gives new ones.

### Rank 3 — Icon pack internals `k7.b`, `h7.d.a`, `d7.h.i` (DotIconPack)

- `h7.d.a(Context, ConcurrentHashMap)`: DexKit `findMethod { matcher { paramTypes(Context, ConcurrentHashMap) ; usingStrings("IconPackageReader") } }`
  (or `usingStrings("entityMap")`, the Kotlin null-check message). No reflection anchor from a
  named class (it is reached only through obfuscated code) — enumerate is impractical without DexKit.
- `k7.b`: take it from the map at hook time: `packs.values.firstOrNull()?.javaClass`; if the map is
  empty, use DexKit `findClass { matcher { usingStrings("CachedIconEntity", "appfilter.xml") } }`, or the
  declaring class of the ctor `(String, String)` invoked inside `h7.d.b(Context, ResolveInfo,
  ConcurrentHashMap)` (DexKit `getInvokes`). Validate: has static fields of its own type.
- `d7.h.i(String, BaseIconFactory, IconProvider) → Bitmap`: DexKit `findMethod { matcher {
  paramTypes(String, BaseIconFactory, IconProvider) ; returnType(Bitmap) } }` — signature is unique in
  the APK; the declaring class also uses strings `"IconPackManager"`, `"com.nothing.icon"`.
- Alternative without DexKit for the preview: hook the AOSP-named caller side instead
  (`BaseIconFactory`/`IconProvider` are kept) — not available for the map, so DexKit is the clean
  answer here.

### Rank 4 — `p7.e.e(Context)` drawer scrim colour (DrawerBackground)

- DexKit (caller anchor): `findMethod { matcher { declaredClass("com.android.launcher3.uioverrides.states.AllAppsState") ; name("getWorkspaceScrimColor") } }.getInvokes()` → the one with
  signature `(Context) → int` declared outside AOSP packages. Or `findMethod { matcher {
  paramTypes(Context) ; returnType(int) ; usingNumbers(resId(nt_all_apps_scrim_color_nt_mono)) } }`
  (resolve the id at run time with `getIdentifier`, pass as number; ids change per build).
- Reflection fallback (no bytecode): none from AllAppsState. Today's fallback (state hook) already
  works and the colour-equality guard keeps a wrong match harmless, so this is low risk; just
  remove the literal `"p7.e"`/`"e"` and use DexKit or keep the fallback only.

### Rank 5 — Structural lookups that hinge on one Kotlin field name

| item | today | stronger resolver |
|---|---|---|
| `smart.c` grid decoration | `NTLauncherAllAppsContainerView.gridDirection` type | the only declared field whose type extends `RecyclerView.ItemDecoration` and declares ctor `(int, boolean)` |
| `smart.x` / `smart.y` | `SmartFullSheet.adapter` / `.appsList` | the only field of `SmartFullSheet` assignable to `AllAppsGridAdapter` / `AlphabeticalAppsList` |
| SmartFullSheet `folderName`, `titleContainer` | Kotlin names | `findViewById(getIdentifier("folder_name","id"))`, and the title container = its parent |
| SmartFullSheet `matchedDeviceProfile` | Kotlin name | only field assignable to `DeviceProfile` |
| `smart.z` via `SmartFolderContainer.smartProfile` | Kotlin name | see Rank 2 |
| `NTLauncherAllAppsContainerView.foregroundPaint` | Kotlin name | the only `Paint` instance field declared on that class |
| `NtWidgetsFullSheet.pageIndicatorDisappearedDistance` / `indicatorHeight` / page-indicator methods | Kotlin names | `getPageIndicator()` = only method returning `NtWidgetsPageIndicator`; ints by elimination are fragile → keep names but `Hooks.skipped` per member |
| `x7.c` AppStatusInfo | `ItemInfo.mStatusInfo` + value probe | already robust; additionally accept `AppInfo.getStatusInfo()` return type |
| smart list order method | the only private no-arg List method | add a semantic check: invoke on the live list, result contains `"others"` |

### Rank 6 — Nothing-specific readable names (survive R8, change with Nothing's refactors)

`NTLauncher.handleConfigurationChanged`, `InvariantDeviceProfile.recentActivitySupportedProfiles` /
`getRecentActivityDeviceProfile`, `NTPopupContainer.addDeepShortcutEntries/addSystemShortcutEntries/
showForView`, `NTArrowPopup.*`, `SmartFolderIcon.bind/getFolderName/getFolderIconContainer`,
`AllAppSettingsFragment`, `LauncherSettingsFragment`, `card.*HostView.applyDisplaySpan`. Anchor:
class names are referenced from XML layouts / manifest (kept); log tags and Kotlin
`Intrinsics.checkNotNullParameter` names (`y.i(x, "name")`) give DexKit strings for each method.

### Rank 7 — AOSP Launcher3 names

Stable within Android 16 builds of 4.0.x; at risk on an Android-version jump: `LoaderTask.waitForIdle`,
`Launcher.bindInflatedItems`, `CellPosMapper`, `ModelDbController`, `DeviceProfile` private methods
(`getIconSizeWithOverlap`, `updateHotseatDsbWidth`, `adjustWorkspaceLabelPadding` — AOSP is moving
DeviceProfile pieces into separate Kotlin classes), `RecentsOrientedState.setFlag` and its flag value,
`AppWidgetResizeFrame.setupForWidget`. Anchor: AOSP source of the matching Android tag; method
names are kept by Launcher3's proguard rules.

### Is DexKit a fit, and what does it cost?

DexKit (LuckyPray/DexKit, `org.luckypray:dexkit`) fits exactly the Rank 1-4 cases: it finds classes
and methods by string constants (log tags, Kotlin parameter-name strings), number constants
(float factors, resource ids), signatures, and by caller→callee edges (`getInvokes`), without loading
classes. Cost:
- Size: one native library per ABI (arm64-v8a only is enough for these phones, roughly 1 MB) plus a
  small Kotlin/Java API; the module must keep `extractNativeLibs`/uncompressed `.so` loading working
  from the hooked process (`System.load` from the module APK path — standard for LSPosed modules).
- Time: the launcher APK carries ~41 MB of dex (classes.dex 16.8 MB + 3 × ~8 MB + 0.1 MB). A batch of
  ~8 string/number queries is typically a few hundred ms on a modern SoC, **on the launcher's cold
  start**, so it must not run on the main thread and must be cached.
- Caching: key = launcher `versionCode` (40020216) + APK `lastUpdateTime` (+ module version). Store the
  resolved names (`"za.a0"`, field roles, `"p7.e#e"`, …) in a small file in the launcher's own data
  dir (the module already keeps `nlplus_landscape_places` there) or ship them precomputed for known
  builds. With a cache hit, startup cost is a file read; a miss (launcher update) costs one scan in a
  background thread, after which the dependent features install late (they must tolerate that —
  install on first `Launcher.onCreate` instead of `handleLoadPackage`).
- Without DexKit: everything in Rank 1, 2, 5 is reachable by reflection from kept class names
  (field types, signatures, value fingerprints — the pattern the module already uses for AppStatusInfo).
  Only `h7.d`/`k7.b`/`d7.h` (icon pack) and `p7.e` have no reflective entry point; for those, an
  alternative is enumerating class names of the launcher's dex via `BaseDexClassLoader.pathList
  .dexElements[].dexFile.entries()` filtered by short package names and loading candidates with
  `Class.forName(name, false, cl)` (no static init) — viable but slower and needs the obfuscated
  package to stay single-segment, so DexKit is the better tool if those two features are to survive.

Recommended shape (Kotlin, teachable): `launcher/LauncherSymbols.kt` — one object per feature
(`WidgetPickerProfile`, `SmartProfile`, `IconPackHooks`, `ScrimColour`) with `resolve(cl, ctx):
Resolved?`, each validated by a semantic probe and reported to HookReport; features install only
from a fully-resolved object (two-phase: resolve everything, then hook; on a mid-install failure
`unhook()` what was hooked). Literal 4.0.20 names stay as the fast path, verified before use.

---------------------------------------------------------------------------------------------------

## 4. Nothing Launcher features/APIs the module could build on (seen while auditing, not used yet)

- **Nothing's own emoji folder covers on the home screen**: `com.nothing.launcher.folder.CoverView`
  (an `AppCompatTextView` with font `noto_emoji_medium`), `FolderInfo.coverValue` (default
  `"custom:smile"`, persisted in the favorites table column `coverValue`), `FolderInfo.folderType` /
  `isCoverType()`, parsing in `q7.i`, picker `folder/emoji/EmojiSelectPanelLayout`,
  `folder/customisation/FolderCustomisationView`, `BigFolderIconContainer` (big folders, `SCALE_BIG
  0.41f`). The module's Smart-drawer covers and the future Dot Matrix Display renderer could render
  *these* covers too (hook `CoverView.updateValue()`), and reuse Nothing's picker/persistence instead of
  the module's own folder store.
- **NDot fonts**: `ossupport/utils/NTFontUtils.getNdotExpectTextTypeface(language)` /
  `isNdotFontSupportLanguage` (`Typeface.create("NDot57", 0)`) — Nothing's own language check for the
  dot font; the DotLabel/DotFont code could defer to it for non-Latin labels.
- **Smart drawer internals**: usage ranking `apppredict/OrderEvent`, `OrderData.getLastOrder()`,
  `AppEvent` scores (`getScore/getLastScore/hasValidScore`), `AppStatusInfo` (`x7.c`) with category
  source/version; `PredictionRowView.updateFolderAppList` (first 3-4 apps of the first 6 folders feed
  the prediction row). A "sort custom folder by usage" option or usage-weighted covers can read these
  directly.
- **Hidden apps / Private space**: `hiddenapps` (`NTHiddenAppsPagedView`, `SmartEntranceIcon`, the
  `"hidden icons"` category), `privatespace/*` — e.g. a custom folder that is also hidden.
- **Icon packs and theming**: `d7.h` IconPackManager (`refreshAndReloadLauncher` via ThemeManager,
  `SYSTEM_ICONS` reset, `"com.nothing.icon"` Nothing pack), `k7.b` CachedIconEntity (appfilter
  parsing), `NTMonoIconUpdateTask`, `bigicon/NTBigIconCachingLogic`, `util/Themes`. The dot-matrix pack
  could become a real appfilter-backed pack served by the module APK (no in-launcher map injection,
  so F4 disappears).
- **Widget hosts**: `card/LauncherCardWidgetHostView`, `NavigableCardWidgetHostView`,
  `BaseLauncherCardHostView`, `card/ai/LauncherAIWidgetHostView` + `com.nothing.aiwidget.IAIWidgetView`
  (AI widget surface), `com.nothing.cardhost/*`, `widget/NTQsbWidgetHostView`, `NTDockSearchBarLayout`,
  `widget/LocalWallpaperColorsExtractor` (wallpaper colours for theming dot renderers).
- **Search**: `search/SearchBoardActivity`, `com.nothing.essential.search`, `com.nothing.search.*`
  (Essential Space search provider), `search/es/DebugESFeatureActivity` (a debug entry point worth a look
  for hidden flags), the drawer's Compose search (`NTLauncherAllAppsContainerView`).
- **Home previews**: `graphics/NTLauncherPreviewRenderer` (renders the home screen for settings/wallpaper
  pickers) — the place to make previews show landscape or dot-matrix icons consistently.
- **Recents**: `recent/TaskViewLongPressTipView`, `OverviewActionsView` (already used by RecentsRam).
