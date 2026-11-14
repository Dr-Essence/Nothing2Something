# Hook check on every Nothing OS build (2026-11-14)

`.github/workflows/hookcheck-all.yml`, run 43595792525, module at commit 00452b0 (0.70.54's
hooks). Every build in github.com/spike0en/nothing_archive (232 builds of 13 phones) was
unpacked in GitHub Actions, the apps the module hooks and Android's framework indexed, and
every class, method, field and resource the module names (1237 references that resolve on the
CMF Phone 1's Nothing OS 4.1, Tetris_B4.1-260928-1140) looked up in its own app
(`tools/hookcheck.py report`). A break: the baseline has it and that build does not. No build
failed to unpack.

Not covered: names built at run time, methods found by their shape (xposed/Symbols.kt,
LauncherSymbols.kt: DexKit), and behaviour that changed under the same names.

## By phone and Nothing OS version (newest build of each)

| Phone (codename) | 2.x | 3.x | 4.0 | 4.1 | 5.0 |
|---|---|---|---|---|---|
| Phone (1) (Spacewar) | 102-150 | 44 | - | - | - |
| Phone (2) (Pong) | 102-137 | 44 | 1 | **0** | - |
| Phone (2a) (Pacman) | 100 | 44 | 1 | **0** | 41 |
| Phone (2a) Plus (PacmanPro) | 100 | 44 | 1 | **0** | 41 |
| Phone (3a) / (3a) Pro (Asteroids) | - | 44 | 1 | **0** | 42 |
| CMF Phone 1 (Tetris) | 100 | 44 | 1 | **0** (baseline) | - |
| CMF Phone 2 Pro (Galaga) | - | 44 | 1 | **0** | - |
| Phone (3) (Metroid) | - | 38 | 1 | **0** | 41 |
| Galaxian | - | 38 | 1 | **0** | - |
| Frogger, FroggerPro, SuperContra | - | - | - | **0** (older builds 1) | 42 |

(Models by codename as widely known; Galaxian, Frogger, FroggerPro and SuperContra are
newer phones whose model names are not confirmed here. A dash: no such build in the archive.)

## The breaks

- **Nothing OS 4.0 (newest builds) and the older 4.1 builds of Frogger, FroggerPro and
  SuperContra:** one, `launcher/WidgetPickerLandscape.java` names the launcher's obfuscated
  profile class `za.a0`, which those launcher builds (4.0.10, early 4.0.2x) name otherwise.
  It is installed inside `Hooks.install`, so only the landscape widget picker is left out
  there, with its reason in Hook health. To do: find the class by its shape (Symbols), as
  the drawer scrim colour is.
- **Nothing OS 5.0 (41-42):** the launcher 5.0.6's renames: the widget picker
  (NtWidgetsFullSheet, WidgetsFullSheet), the landscape profile fields (hotseat and folder
  sizes, updateIconSize, updateHotseatSizes), the landscape grid's `mMaxHSpan` and
  `updateItemInfoProps`, the loader's `awaitLocked`, folder menu `addAndBindAddedWorkspace
  Items`, dot and live icons' `mBitmapInfo`/`setNonPendingIcon`, the dot icon pack class
  `k7.b`, `BaseDepthController` (the drawer's live blur; the glass is left to Nothing on
  5.0 anyway), and in System UI `QSTileViewImpl` (green tiles) and the tile editor's
  `ivApp`. Each is installed apart, so each feature is left out on its own; a Nothing OS 5
  port needs these found again (docs/reverse-engineering/nos5_port.md).
- **Nothing OS 3.x and older (Android 15 and before):** 38 to 150, as expected: the module is
  made for Nothing OS 4 (Android 16).
