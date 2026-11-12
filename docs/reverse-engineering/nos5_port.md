# Nothing OS 5 (Android 17): what the module meets, and what was done (0.70.32)

Checked against the Nothing OS 5 tester's own files (Phone (3a) "Asteroids", model A059,
build C5.0-261023-2146, Android 17; Debug-reports release DebuG25) and, for Settings, Settings
search and Gaming, the firmware archive's Asteroids_C5.0-261106-1007, each against the CMF
Phone 1's Nothing OS 4.1 (Tetris_B4.1-260928-1140) as the build known to work.

## How it was checked

`tools/hookcheck.py` (run here and by `.github/workflows/firmware-inspect.yml`):

1. `index`: every class, method (with its parameter types) and field of a build, with the
   Android SDK's dexdump, and every resource name.
2. `refs`: every name the module's source asks for (findAndHookMethod with its parameter
   types, hookAllMethods, findClass, callMethod, get/set*Field, getIdentifier, class-name
   literals), with the `CLS_*` constants resolved.
3. `check`: what the new build lacks that the working one has. A name missing in both is
   listed apart: the tool misread it, or it belongs to an app not in the index (HeliBoard,
   Living Worlds, the module's own extra fields); that list was read and holds no break.

What it cannot see, checked by hand: names built at run time, a changed return type behind
the same name (`getWorkspaceScrimColor`), compile-time flags, and anything only the phone
shows. The tester's Hook health (Launcher 6 not installed and 1 left out, System UI 8 left
out, android 1 not installed, Settings 1 not installed) is explained by the findings below.

## Findings and fixes

| Where | Nothing OS 4.1 | Nothing OS 5 | Fix |
|---|---|---|---|
| system_server, controller Home | `PhoneWindowManager.interceptKeyBeforeDispatching(IBinder, KeyEvent, int)` → long, -1 consumed | `(IBinder, KeyEvent)` → boolean, true consumed (`KeyGestureController.interceptShortcutsBeforeKeyCapture` returns it as handled) | every overload hooked; the answer in the method's own type |
| System UI, performance overlay | application `com.android.systemui.SystemUIApplication` | `com.android.systemui.application.impl.SystemUIApplicationImpl` (manifest) | `Instrumentation.callApplicationOnCreate`, whatever the class |
| System UI, 8 features | `sceneContainer`, `statusBarRootModernization`, `qsNewTiles` read at run time | compiled to true (`composeBouncer` false); `QSTileViewImpl` gone, tiles are Compose (`qs.panels.ui.compose.infinitegrid`) | already left out by design (`SystemUi.installUnless`); each needs a new version for the new UI |
| Settings, task manager | `RunningProcessesView.G` list, `A1` row, `J1` item, `K1` merged item, `M1` process | list `F` (`G` is now a View), `x4`, `f5`, `g5`, `i5`; `a()` still sets the page up | the list found by its type; each name set tried through the shape check |
| Launcher, live icons | `BubbleTextView.setNonPendingIcon(ItemInfoWithIcon)` | renamed `setStandardIcon` | whichever exists |
| Launcher, loader idle wait | `LoaderTask.waitForIdle`, `LooperIdleLock.awaitLocked` | gone (the loader no longer waits) | left out when absent |
| Launcher, dot icon pack | entry class `k7.b` (4.0.20) | renamed | the class of an entry already in the map; `k7.b` only if present |
| Launcher, folder menu | `LauncherModel.addAndBindAddedWorkspaceItems(List)` | gone; `enqueueModelUpdateTask(new model.tasks.AddWorkspaceItemsTask(list, spaceFinderFactory.get()))` | the same steps when the method is absent |
| Launcher, drawer background | `AllAppsState.getWorkspaceScrimColor` → int | → `ScrimColors` | colour faded at its source (ThemeColors, DexKit: `ja.e.d`); the int paths only for an int |
| Launcher, landscape resize | `AppWidgetResizeFrame.mMaxHSpan/mMaxVSpan` | `maxHSpan/maxVSpan` | whichever exists |
| Launcher, landscape layout, icon fit, smart drawer, hotseat log, folders, folder sheet | `DeviceProfile` fields and methods (`hotseatBarSizePx`, `updateHotseatSizes`, `updateWorkspacePadding`, `getFixedCellWidth`...) | split into immutable profiles (`mHotseatProfile`, `mFolderProfile`, `mAllAppsProfile`, `mWorkspaceProfile`, `mDeviceProperties`) built by factories; 9 of the replaced methods gone | left out whole on Nothing OS 5 (`Landscape.profilesSplit`), never half applied; `isPhoneLandscape` reads `mDeviceProperties` |
| Launcher, landscape widget picker | `WidgetsFullSheet`, `NtWidgetsFullSheet`, `za.a0` | the picker is Android 17's Compose `widgetpicker.WidgetPickerActivity` | left out on Nothing OS 5 |
| Launcher, glass | the module draws it | Nothing draws its own frosted glass (`com.nothing.commBase.frosted`, theme style) | the module's glass is left out, so the two never draw over each other |

DexKit queries (`LauncherSymbols`), checked against the 5.0 Launcher by shape: the icon pack
icon is `x9.h.j` and the pack list `ba.d.a`, each the only match; the drawer scrim colour is
`ja.e.d`, the only (Context) → int called by `getWorkspaceScrimColor` (4.0.15: `e7.h.i`,
`i7.d.a`; 4.0.20: `d7.h.i`, `h7.d.a`, `p7.e.e`).

Unchanged in 5.0 and checked: every other system_server hook with its parameter types, Settings
search, Gaming, the glass hooks' names, the folder menu's other calls (`addItemToDatabase`
with 4 ints, `makeWorkspaceItem`, `FolderInfo.add`, `MODEL_EXECUTOR`).

## Still to do for Nothing OS 5

- Landscape home on the new `DeviceProfile` profiles and the Compose widget picker.
- The 8 System UI features on the new System UI (Compose tiles, the new status bar root, the
  scene container lock screen and shade).
- On the phone: everything above, with Hook health and a debug report from a Nothing OS 5
  tester.
