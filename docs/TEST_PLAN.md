# Stability test plan

The module is in a **stability phase**: no new features until everything below has been tested on
the phone and every failure found has been fixed. This file is the list. Work through it in order,
one part at a time, and report each result by its id (for example `A3 pass`, `D2 fail`).

## How to test

1. **Use only CI builds.** Install the build from GitHub Actions, not a local one. Write down the
   module version and the Nothing OS build (Settings → About phone).
2. **Start clean.** Launcher only is **off**, Diagnostic logging is **on**, and every other
   LSPosed module is noted down (Nothing Glass especially). If a result looks strange, test it
   again with the other modules turned off.
3. **Change one thing at a time.** Turn one setting on or off, test it, write the result, then
   go on. Two changes at once make a failure impossible to place.
4. **Every test has an expected result.** Anything else is a fail, even if it is small or only
   happens once. Note how often it happens (always, sometimes, once).
5. **What to send for a fail:**
   - a screen recording of it happening (from before the problem starts);
   - the Debug report (Diagnostics → Capture debug report), or the LSPosed log;
   - send logs **privately only**, never in a public issue: they contain account details.

## Scenarios

Most features are tested in several situations. The codes below are listed under each test.

| Code | Scenario |
|---|---|
| **P / L** | Portrait, then landscape (turn the phone while the feature is on screen) |
| **RL** | After *Restart Nothing Launcher* |
| **RB** | After a reboot |
| **SV** | Battery saver on |
| **DK** | Dark theme, then light theme |
| **OFF** | Turned off again: Nothing's own look comes back fully, nothing left over |
| **SUI** | After a System UI restart |

---

## A. Safety first

These come before everything else, because they protect the phone and the user's data.

| Id | Test | Expected |
|---|---|---|
| A1 | Install the CI build over the previous one, reboot | The phone boots normally; the launcher opens; folders and settings are kept |
| A2 | Turn the module off in LSPosed, reboot | Nothing Launcher, System UI and Settings look and work exactly as stock |
| A3 | Turn it back on, reboot | Everything comes back as it was set, no folders lost |
| A4 | Launcher only on, Restart Nothing Launcher | Only the launcher and widgets change; status bar, quick settings, lock screen, fonts are stock |
| A5 | Backup → Export, then change a folder, then Import the file | The setup returns exactly as exported, covers included |
| A6 | Restore missing home items | Missing icons come back; nothing is duplicated, nothing is removed |
| A7 | Diagnostics → Hook health | All hooks report as working; note any that do not |
| A8 | Show app icon off, reboot, then on | The icon leaves the drawer and home screen, the module still opens from LSPosed and Settings; it comes back when turned on |

## B. App drawer folders

| Id | Test | Scenarios | Expected |
|---|---|---|---|
| B1 | Create a folder, add apps, rename it, delete it | RL, RB | The drawer shows the change at once and keeps it after a restart |
| B2 | Reorder folders, and apps inside a folder, by dragging | RL | The drawer order matches the module's list |
| B3 | Custom folders first | OFF | Your folders come before Nothing's categories |
| B4 | Hide automatic categories | OFF | Apps without a folder go to Others; no app disappears |
| B5 | Apps without a folder as icons | OFF | Others is replaced by plain icons; every app is still reachable |
| B6 | Install and uninstall an app | | A new app appears; an uninstalled one leaves its folder without errors |
| B7 | Drawer background opacity slider at 0, 50, 100 | DK | The wallpaper shows through as set; search area matches, no black bar |
| B8 | Drawer search | P / L | Search works, results open, the search sheet matches the drawer |
| B9 | Folder cover image: set, change, remove | RL, RB | The image shows square and sharp; removing it brings back the apps |

## C. Covers and dot matrix (beta)

| Id | Test | Scenarios | Expected |
|---|---|---|---|
| C1 | Emoji covers on, pick an emoji for a folder | RL, RB | The emoji shows on that folder only |
| C2 | Animate emoji covers on and off | SV | Moves when on, still when off; no stutter scrolling the drawer |
| C3 | No emoji covers in battery saver | SV | Folders show their apps while saver is on, emoji when it is off |
| C4 | Dot-matrix emoji on, Green dot screen on | OFF | Covers turn into dots; green only when green is on |
| C5 | Each Dot screen style (Classic, 3 × LED, VFD) | | Each looks as its name says; details visible in VFD |
| C6 | Touch a folder | | The emoji reacts at once, then the folder opens |
| C7 | Animation test, play ten different emoji | | Each plays without cut-off edges or freezes |
| C8 | Leave the drawer open 10 minutes | | The phone does not warm up more than normal; no slowdown |

## D. Dot icons

| Id | Test | Scenarios | Expected |
|---|---|---|---|
| D1 | Pick the dot icon pack in Customisation → Icon Pack | RL, RB | Every icon on home, dock and drawer turns into dots |
| D2 | Each Dot icon style | | Each style applies to all icons |
| D3 | Install a new app | | Its icon also turns into dots |
| D4 | Switch back to Nothing's icons | OFF | All icons return to normal |

## E. Glass

| Id | Test | Scenarios | Expected |
|---|---|---|---|
| E1 | Glass open folders (home screen), tint slider | P / L, DK, OFF | The open folder shows the blurred wallpaper |
| E2 | Glass closed folders | DK, OFF | Folder icons on home and dock are glass; pressed state still visible |
| E3 | Glass search bar | OFF | The dock's search bar is glass |
| E4 | Glass widgets | RL, OFF | Nothing's widgets are glass and still respond to touch |
| E5 | Glass drawer background, look: Nothing OS 5 frosted | P / L, DK, RL, OFF | Frosted wallpaper behind the drawer, never solid black; bar icons readable |
| E6 | Glass drawer look: Live blur | SV, RL | The blur follows the drawer opening; after a restart it still works |
| E7 | Glass open drawer folders | OFF | An opened drawer folder shows the glass behind it |
| E8 | Glass settings screens (Module page) | DK, OFF | This app's screens are frosted at once, no force stop needed |
| E9 | Each glass option with a live wallpaper | | Works or falls back cleanly; no black or frozen picture |

## F. Rotation and landscape

| Id | Test | Scenarios | Expected |
|---|---|---|---|
| F1 | Home screen rotation on, turn the phone | RL, RB | Home and drawer turn with auto-rotate; icons are not lost or moved |
| F2 | Turn back to portrait | | The portrait home screen is exactly as before |
| F3 | Long-press menus in landscape | | Menus open fully on screen, not cut off |
| F4 | Widget picker in landscape | | Opens and places a widget |
| F5 | Open a folder in landscape | | Opens whole and centred |
| F6 | Recents in landscape | | Turns properly |
| F7 | System screens in landscape | OFF | Nothing's portrait-only screens turn |
| F8 | Lock screen rotation (test), Always-on display rotation | RB | Lock screen and AOD turn; fingerprint and PIN still work |
| F9 | Check orientation on wake and unlock | | The screen opens in the way the phone is held |
| F10 | Living Worlds / Palette Cycle in landscape, each framing | | The chosen part of the scene shows; turning is smooth |

## G. Status bar and quick settings (System UI)

| Id | Test | Scenarios | Expected |
|---|---|---|---|
| G1 | Ndot status bar | SUI, RB, OFF | Clock in Ndot |
| G2 | Date in status bar | SUI | Date before the clock |
| G3 | Dual status bar, and in landscape, with each date option | P / L, SUI | Second row shows; month number, year and end positions as set |
| G4 | OLED saver: move the status bar (1 minute) | | The bar shifts slightly over time, never off screen |
| G5 | OLED saver: hide the status bar | | Hides after the time set, comes back on a pull-down |
| G6 | Full-screen apps (each mode, pick two apps) | OFF | Those apps go full screen as set; others do not |
| G7 | Green quick settings | SUI, OFF | Icons glow green; editing the tiles has no green blobs |
| G8 | Amber dashboard tiles | OFF | Tiles turn amber |
| G9 | Quick settings with Nothing Glass on, then off | SUI | The glass panel keeps working after a System UI restart |

## H. System

| Id | Test | Scenarios | Expected |
|---|---|---|---|
| H1 | System font, each choice | RB, OFF | The font changes without reboot and stays after one |
| H2 | Full refresh rate in battery saver | SV | The screen keeps its full rate in saver |
| H3 | An app switches battery saver (e.g. EnforceDoze), then tap the saver tile | | The tile always works; never refused as "already on" |
| H4 | Memory in recents | | Free/total memory beside Screenshot; tapping opens it |
| H5 | Task manager in Running services | | CPU and temperatures show; nothing freezes |
| H6 | Running services shortcut | | The icon opens Running services |

## I. Sound

| Id | Test | Scenarios | Expected |
|---|---|---|---|
| I1 | Interface sounds | OFF | Chimes on drawer, folders and apps; silent when off or on silent mode |
| I2 | Background music, each style, volume slider | | Plays only while home or the drawer is in front, fades in and out, never pauses your own music, goes quiet during a call or other media |
| I3 | Living Worlds sounds, Palette Cycle sounds | | Play only while that wallpaper is visible |

## J. Keyboard and controller

| Id | Test | Scenarios | Expected |
|---|---|---|---|
| J1 | Game controller navigation on the home screen | | Moves over icons with a focus ring; A opens |
| J2 | Controller typing in HeliBoard | | Moves over keys and suggestions, types |
| J3 | HeliBoard across the whole width | P / L | Full width in landscape only |
| J4 | Gaming launcher with a controller | RB | With a controller connected, home (button, gesture, controller's PS/Xbox button) opens the chosen launcher; disconnecting returns Nothing Launcher |

## K. Performance (CMF Phone 1)

| Id | Test | Scenarios | Expected |
|---|---|---|---|
| K1 | Ndot overlay | P / L | Shows over apps, readable, can be closed |
| K2 | PerfMTK profile, each choice | | Applies; the phone stays stable |
| K3 | 120 fps unlock for games | | The game reports a higher rate; turning it off restores stock |

## L. Gaming Space (experimental demo)

Lowest priority: its look will be replaced. Tested only for crashes and data safety.

| Id | Test | Expected |
|---|---|---|
| L1 | Open Gaming Space, scroll, open a game | No crash, no freeze |
| L2 | Artwork, trailers, SteamGridDB key check | Loads or fails with a clear message, never a crash |

## M. Diagnostics

| Id | Test | Expected |
|---|---|---|
| M1 | Capture debug report, then stop | A report file is saved |
| M2 | Post test notifications, then clear | Each kind shows; clearing removes them |
| M3 | Diagnostic logging off | No more module lines in the log |
