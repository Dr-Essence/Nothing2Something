<p align="center"><img src="https://raw.githubusercontent.com/Dr-Essence/Nothing2Something/claude/nothing-os-xposed-module-65lsca/docs/images/banner.png?v=15" alt="Nothing2Something: the three-eyed dot-matrix face and the name in Nothing's NDot" width="100%"></p>

# Nothing2Something

An LSPosed module for **Nothing and CMF phones** that adds to Nothing Launcher and Nothing OS:
custom app drawer folders with animated dot-matrix covers, a landscape home screen and lock
screen, game controller support, live wallpaper sounds and more.

> **Heavily experimental alpha.** Expect bugs, and expect things to change from build to build.
> Tested on one phone so far (CMF Phone 1, Nothing OS 4.1, Android 16).
>
> An unofficial community module, not affiliated with or endorsed by Nothing. Formerly
> Nothing Launcher+; it grew beyond the launcher, hence the name: from Nothing, to something.

**What it adds**

- **App drawer**: your own folders beside Nothing's Smart categories, image and emoji covers.
- **Dot-matrix look**: animated emoji covers, glowing dot-matrix app icons, an Ndot status bar,
  and live icons (CMF Stereo's speaker pulses, like Nothing's clock).
- **Landscape**: home screen, app drawer, recents, widget cards, lock screen and always-on display.
- **Controllers**: navigate the launcher and type in HeliBoard with a gamepad; a gaming launcher.
- **System extras**: memory in recents, a Running services task manager, system fonts, full
  refresh rate in battery saver, sounds and music.
- **Live wallpapers**: sounds and smooth turning for Living Worlds and Palette Cycle.

<p align="center">
  <img src="https://raw.githubusercontent.com/Dr-Essence/Nothing2Something/claude/nothing-os-xposed-module-65lsca/docs/images/home-landscape.png" alt="Landscape home screen" width="100%">
</p>
<p align="center">
  <img src="https://raw.githubusercontent.com/Dr-Essence/Nothing2Something/claude/nothing-os-xposed-module-65lsca/docs/images/drawer-landscape.png" alt="App drawer folders with dot-matrix covers" width="100%">
</p>
<p align="center">
  <img src="https://raw.githubusercontent.com/Dr-Essence/Nothing2Something/claude/nothing-os-xposed-module-65lsca/docs/images/drawer-folders-landscape.png" alt="App drawer with normal folders" width="100%">
</p>
<p align="center">
  <img src="https://raw.githubusercontent.com/Dr-Essence/Nothing2Something/claude/nothing-os-xposed-module-65lsca/docs/images/lockscreen-landscape.png" alt="Landscape lock screen" width="100%">
</p>
<p align="center">
  <img src="https://raw.githubusercontent.com/Dr-Essence/Nothing2Something/claude/nothing-os-xposed-module-65lsca/docs/images/aod-landscape.png" alt="Landscape always-on display" width="100%">
</p>
<p align="center">
  <img src="https://raw.githubusercontent.com/Dr-Essence/Nothing2Something/claude/nothing-os-xposed-module-65lsca/docs/images/home-portrait.png" alt="Portrait home screen" width="32%">
  <img src="https://raw.githubusercontent.com/Dr-Essence/Nothing2Something/claude/nothing-os-xposed-module-65lsca/docs/images/drawer-portrait.png" alt="Portrait app drawer with emoji covers" width="32%">
  <img src="https://raw.githubusercontent.com/Dr-Essence/Nothing2Something/claude/nothing-os-xposed-module-65lsca/docs/images/drawer-folders-portrait.png" alt="Portrait app drawer with normal folders" width="32%">
</p>

*Screenshots from the test phone, alpha builds. Captions use Nothing's NDot 77 JP Extended
as drawn on the phone; the font itself is not part of this repository.*

Each section below opens when tapped.

## Status

<details open>
<summary><b>Current state</b> · version 0.70.0, what is pending, what still needs testing</summary>

**Current build: 0.70.0** (CMF Phone 1, Nothing OS 4.1, Android 16): Steam microtrailers as Gaming Space's game clips (new), Gaming Space's lower half as on the XMB, with big category cards and a big dot box for the selected app (new), a Show app icon switch (new), a search field for the module's own settings (new), Nothing Gaming Space (alpha, demo), the module's own gaming launcher in dot-matrix style, with one dot screen on top, a cross menu below, turning 3D dot boxes, an animation for each category and game artwork from SteamGridDB (new, third part; Dr.Essence has more fixes to discuss), HeliBoard across the whole width in landscape, beside the camera too (new, a switch, verified), Full-screen apps, picked apps that hide the status bar and use its space, always or while the OLED saver hides it (new), every setting of the module findable in Settings search (new), finer dot icons (34 dots across), dot icons whose grid runs under a wider bezel (clean edges in every shape), dot icons on dark glass in every shape (no green edge), the dot-matrix icons as a "Dot matrix" icon pack in Nothing's picker (new), CMF Stereo's live icon with white waves and left alone in Nothing's icon style, an app icon in Nothing's dot-matrix style (new), a Nothing2Something row in Settings (new), a Launcher only mode (new), the crash-loop guard counts only crashes (System UI restarts from other modules or LSPosed no longer switch the dual status bar off), the dual status bar's date hidden with the clock (no cut date on the lock screen), the dock turned with the profile (no squeezed dock under load), Check orientation on wake and unlock (new), the turned lock screen laid out in the frame it turns in, the dual status bar with its options and tighter rows, the OLED saver (also shown when a swipe brings the bar back over a full-screen app), and Restart System UI in the module.

**Verified on the phone**
- 0.63.1, SteamGridDB artwork on the big dot screen: shown, without the crash of 0.62.0 and
  0.63.0 (screenshot: Sonic Forces' banner through the dots). Asked next, in 0.64.1: the
  screen bigger, matching by hand, choosing the trailer.
- 0.59.0, Nothing Gaming Space opens, plays the intro and lists the categories and apps
  (screenshots: Games with 25 apps, 8 Ball Pool selected). Dr.Essence's changes, in 0.59.1:
  the whole top half one dot screen (it was a small display box), the categories like the
  drawer's folders (one dot card, the name lit in NDot), and apps moved through as on the
  PlayStation's cross menu. The turning 3D dot boxes for the apps come in the next part.
- 0.58.1, HeliBoard across the whole width (switch in Landscape & controllers): the keyboard
  runs edge to edge in landscape (Dr.Essence: "heliboard fixed"). How it was found: Android
  keeps a window out of the camera's strip unless it asks for cutout mode ALWAYS
  (`WindowLayout.computeFrames`; the keyboard window asked for nothing, dump
  `frame=[106,103][2400,1080]`); HeliBoard sized its keys to the screen minus the cutout
  (`getDefaultKeyboardWidth`, moved by R8 into `KtxKt` in the 4.3-beta1 APK); and in 0.57.0,
  with both changed, the keys were still 106 px in and cut off on the right, because the
  phone's `InsetsState` counts the cutout in the views' legacy system window insets. The
  keyboard window now asks for ALWAYS, HeliBoard's width leaves out only the bars, and the
  window root hands its views the insets without the cutout. The diagnostic line of 0.58.1
  is removed.
- 0.58.0, Full-screen apps: a picked app goes full screen once it is also ticked in the
  module's LSPosed scope (Dr.Essence: "if i tick the app i want in lsposed it works"); before
  ticking, nothing happened, as expected. Asked next: no ticking. Checked and left out: LSPosed
  hands its "request scope" service only to modules on the modern API (`LSPModuleService`:
  `!module.file.legacy`, also in the Vector fork), and the phone's LSPosed 2.2.1 `lspctl`
  says it is not to be used by apps; so the scope stays ticked by hand. Which modes and the
  swipe were tried is not reported yet.
- 0.53.1, dual status bar: two rows in portrait and landscape, the clock alone on top with
  the other icons, the date in full ("SUNDAY, 8 NOVEMBER") and the notifications under it,
  apps under the taller bar (screenshots). The gap between the rows was too large; see 0.54.0.
- 0.54.0, dual status bar: the tighter gap (the second row just under the clock, the top row
  where it was), the year option, and in landscape the notifications at the end of the left
  half under the right icons (screenshots). The OLED saver hid the bar (instantly) and moved
  it (log: "move every 60 s by 2 dp").
- 0.55.2, app icon: the dot-matrix face in the launcher (Dr.Essence: "perfect").
- 0.55.1, Nothing2Something in Settings: the row shows and opens the app (Dr.Essence: "I
  think it works"; the icon's tint in light mode and the LSPosed log line not checked yet).
- 0.54.10, the dock turning with the home screen: the squeezed dock not seen again in use
  (Dr.Essence: "seems fixed"; not yet recorded after a reboot or on battery saving).
- 0.54.9, Check orientation on wake and unlock: the screen came on and unlocked in the way
  the phone was held (Dr.Essence: "seems to work"; the LSPosed log not checked yet).

- 0.56.0, the module's settings in Settings search: searching in Nothing's Settings lists the
  module's settings, its folders too (they are rows of the App drawer page, which the list is
  made from); Dr.Essence: "even folders are searchable". Opening a result at its row not
  reported separately.
**Not tested on a phone yet**
- **0.70.0, Gaming Space: Steam's microtrailers, and your own clip.** Asked by Dr.Essence:
  more video sources, ideally short animated clips.
  - **Microtrailers.** Steam keeps one beside every trailer, in the same folder as its
    playlists (`.../store_trailers/<app>/<movie>/<hash>/<time>/microtrailer.mp4`): the few
    seconds of highlights its store plays when a game is hovered. Not listed in the store's
    data, found by name in that folder. Checked: Portal 2's is one plain MP4, H.264,
    854 x 480, 7.2 s, no sound, 2.4 MB, frames looked at (gameplay, not logos); Hades,
    Stardew Valley, Baldur's Gate 3, Hollow Knight and Terraria have one for every trailer
    tried (1.9 to 3.3 MB). A trailer's clip is now its microtrailer, in one download; the
    clip made from the first 15 s of the stream (often logos and black) is the fallback.
    Clips saved before stay until the trailer is chosen again.
  - **"Use my own clip…"** at the end of the trailer list says where to put a video of your
    own (Videos/<package>.mp4): for games not on Steam, a screen recording of the game.
  - **Not done, pending network access:** Play Store pictures as an artwork source, and
    other sources (Steam's animated Points Shop items, RAWG's clips). This session's network
    does not reach play.google.com, api.steampowered.com or api.rawg.io, and they are not
    built without seeing their real answers.
  Compiled; the microtrailers checked on Steam's servers as above. Not run on the phone yet.
  To test: "Choose trailer" on a Steam game, pick one, and see the clip on the big screen.
- **0.69.1, the dot balls of 0.69.0 removed.** Dr.Essence: "a sphere of NDot doesn't look
  good at all". Round icons are boxes again, as every other icon; the code is back as it was
  in 0.68.1 (`DotBallView` and the `TurningIcon` interface deleted, `IconScreen.isCircle`
  private again). The lesson kept for the course: a render of the idea, shown before
  building it into the app, would have settled this sooner.
- **0.69.0, Gaming Space: round icons are dot balls.** Asked by Dr.Essence: an icon that is
  only a circle leaves a box side's corners empty, so it turns as a ball instead
  (`DotBallView`); every other icon stays a box.
  - Which icons: only one that is not adaptive and whose outline is a circle
    (`IconScreen.isCircle`, now public). On this phone the launcher's mask is a circle, so
    every adaptive icon is drawn round too; the test is on the icon's kind first.
  - How it is drawn, without OpenGL: about 1,300 dots fixed on the ball in rows of latitude,
    each row with as many as fit round it (64 round the equator, 32 rows). Each takes its
    colour once from the icon laid straight on onto the front (a circle seen straight on is
    exactly the ball's outline) and mirrored onto the back, so it reads the right way round
    from both sides. Each frame they are turned and leant as the boxes are and projected; only
    the half facing the viewer is drawn (a point of the ball faces the eye when
    1 + EYE * z < 0), smaller and darker towards the outline. Sized to its outline seen in
    perspective, so it never leaves its view.
  - The box and the ball share a small `TurningIcon` interface (lit = selected); the app's
    place below holds whichever its icon gets, chosen off the main thread.
  Compiled; checked by drawing the same dot layout and projection on a round icon at five
  angles (40 by 20 dots, the first try, was too coarse to read). Not seen on the phone yet.
- **0.68.1, Gaming Space: renaming really saves, and the boxes stay whole.** Dr.Essence on
  0.68.0: SAVE did nothing to a tap, a box was cut off at some points of its turn, and its top
  was still empty.
  - **Renaming never saved, by touch or controller, since it was added.** The name sheet set
    its field and what to do with the name, then opened its card; opening starts by closing
    any sheet before, which cleared both. SAVE, Enter and A then found nothing to save (the
    0.68.0 button called the same cleared step). They are now set after the card opens. This
    also fixes "Search another name" for artwork and trailers.
  - **The box went past its view and was cut off.** It was sized for a reach of 1.55 half
    sides every way; turned and leant with perspective it reaches 1.60 across and 1.81 up and
    down (measured over every degree of the turn). The reach is now measured once and the box
    sized and centred to fit it at every angle.
  - **The top is a dot screen too**, a little darker as lit from the front (it was a plain
    dark lid).
  Compiled; the turning box checked by drawing the same projection at six angles in a frame
  the view's size (no corner leaves it, the top shows the dots). Not seen on the phone yet.
- **0.68.0, Gaming Space: the lower half as on the XMB, and typing that saves by touch.**
  Asked by Dr.Essence: the categories much bigger; going down into a category, the categories
  go away and the selected app is a big box; the whole box a dot screen (the round dot icon on
  a black plate left most of it empty); and with touch, Rename and the artwork and trailer
  searches never took effect, because the keyboard's key did nothing.
  - Two levels. The categories: dot cards 78% of the lower half's height (they were 92 dp),
    the selected one in a fixed place, the next ones smaller beside it. The apps: the cards
    fade out, the category's name stays small at the top (a tap goes back), the selected app's
    box is half the lower half's height with its name in large green NDot, the next apps
    follow below at half that size. The big screen shows the category on the first level and
    the app on the second.
  - Controls: on the categories, left and right move, A or down opens; in the apps, up and
    down move, A plays, B (or up from the first app) goes back, left and right or L1 and R1
    change the category, Y opens the menu. Touch: a swipe up opens the category, a swipe down
    past the first app goes back. Hidden cards and apps take no taps (the apps above the
    selected one reach into the big screen).
  - Every side of a box is now a dot screen edge to edge (`DotFace`): the icon without the
    launcher's mask (an adaptive icon's two layers drawn as Android draws them before masking,
    25% beyond the bounds on each side), 28 dots across, each the mean colour of its cell (by
    hand: a filtered `createScaledBitmap` samples only a few pixels per cell at 384 to 28),
    unlit grey dots where it is dark. `plate()` is removed.
  - The name field (Rename, "Search another name") has a SAVE or SEARCH button beside it, and
    takes the keyboard's key whatever it sends: until now only the "Done" action was taken,
    while a keyboard may send a plain Enter (it reaches the field as IME_NULL) or another
    action. It also asks the keyboard for no full-screen field in landscape, which hid the
    card. Which of these the phone's keyboard did is not known (no log); the button works in
    every case.
  Compiled; the dot face checked by rendering the same steps on a real icon from a
  screenshot. Not seen on the phone yet: the sizes and the motion need Dr.Essence's eyes.
  To test: open Gaming Space, move through the categories, go down into Games and back;
  rename a game by touch and tap SAVE; "Choose artwork" › "Search another name" › SEARCH.
- **0.67.0, Show app icon (Module section).** Off hides the module's icon from the drawer and
  home screen; the settings stay reachable from the Nothing2Something row in Settings, Settings
  search and LSPosed. How it was found:
  - Android 10+ replaces a hidden icon with a stand-in that opens the app's info page. In the
    phone's services.jar, `LauncherAppsImpl.shouldShowSyntheticActivity` shows it for any app
    that asks for permissions and has a launcher entry enabled *in its manifest* (it reads
    `ActivityInfo.enabled` with MATCH_DISABLED_COMPONENTS, so switching the entry off at
    runtime does not count). The module now answers false there for its own package only
    (`HiddenAppIcon`, system scope, so it works after a reboot; before that the stand-in shows).
  - The icon is now its own entry, an alias, so switching it off leaves the settings activity
    (renamed `SettingsActivity`) enabled for LSPosed and Settings. The alias keeps the old name
    `ui.MainActivity`: Nothing Launcher removes a home-screen icon whose component no longer
    exists (`PackageUpdatedTask`, "Removing shortcut that no longer points to valid
    component"), so a home-screen icon from before stays.
  - The choice is Android's own component state (`AppIcon`), not a setting of the module.
  Compiled, manifest checked in the built APK. To test: after updating, the home-screen icon is
  still there; switch off, reboot, and the icon is gone with no app info icon in its place;
  the Settings row and LSPosed still open the module; switch on brings it back.
- **0.66.0, search inside the module.** A "Search settings" field at the top of the main menu
  lists, while typing, every setting whose title, summary, page or section holds all the
  typed words (title matches first, at most 40). It reads the same list Settings search uses
  (`search_index.json`, rebuilt each time the app opens), and a result opens the same way a
  Settings search result does (page, scrolled to the row, flashed), so the field, Settings
  search and the launcher's search bar always agree. Compiled; not run on the phone yet.
  To test: type "saver" and tap a result. Also pending: whether the launcher's search bar
  already lists the module's settings (Nothing Search asks Settings Intelligence for its top
  three settings of the whole index): type "OLED saver" there.
- **0.65.3, first cleanup from the code audit (four bugs).** A review of the whole code (four
  parts read in parallel, each claim checked against the code) found these, fixed here:
  - **The font restarted System UI without the safety limit.** Choosing a system font ran its
    own `pkill` of System UI, without the 90-second limit and the crash guard's "planned
    restart" note of `SystemUiRestart`, so a few fonts in a row could put LSPosed into safe
    mode (the 0.54.1 mistake). The font now goes through `SystemUiRestart` (the one way System
    UI is restarted) and restarts the launcher as before; within the 90 s, the toast says when
    the status bar will follow.
  - **System UI broadcast the saver's state to every app, on every fade and tap,** even with
    no app picked in Full-screen apps; Android logged "Sending non-protected broadcast
    STATUS_BAR_FADED from system" each time. Now it goes by name to the picked apps only, in
    the "while the saver hides it" mode, only when the state changes, and a starting app's
    query is answered only if it is picked. Android's check (`checkBroadcastFromSystem`,
    phone's services.jar) is silent for a broadcast to one package whose receiver requires a
    permission, as the apps' does: the log line should be gone.
  - `SteamGridArt.options()`, left unused by the game chooser of 0.64.1, removed.
  - The trailer switch said "streams over the internet"; it downloads a short clip once.
  - Also: the LSPosed description (still "custom folders for the app drawer") describes the
    module now, and `.kotlin/` (Kotlin's build cache) is ignored by git.
  Compiled; needs a font change, and Full-screen apps in the saver mode, on the phone.
- **0.65.2, no squeezed home screen on a turn.** Dr.Essence's log (9 Nov, 10:28): swiping home
  from Nothing Gaming Space (landscape) with the phone upright, Android had already given the
  launcher a portrait window for the gesture (`Relayout ... requestedWidth = 2400
  requestedHeight = 1080 outFrames = frame=[0,0][1080,2400]`), but the launcher drew its
  landscape layout into it at once (25.279) and got its portrait profile only 1.6 s later
  (26.856, after a stale landscape one at 26.112): the squeezed icons and widgets. Now, before
  every frame, the launcher's profile (wide or tall) is compared with its window, and a frame
  of the wrong shape is not drawn: Android keeps the previous frame until the right layout is
  ready (two seconds at most, so drawing never stops for good). This covers every way the
  shapes can disagree (home gesture, a turn, an app turning the screen), not one case. The
  log shows `rotation shape hold: profile … in a … window, not drawn` and `… drawn after N ms`.
  0.65.1 (home rotation only with auto-rotate on) was a wrong reading of the same log, made
  before the `Relayout` line was read, and is undone: the window had already been turned, so
  asking for portrait would not have helped, and it took the rotate button away.
  Compiled; needs the same swipe home from Gaming Space, and a few turns on the home screen.
- **0.65.0, a "Nothing Gaming Space" folder for the artwork and trailers.** A new row in the
  gaming settings asks for All files access; with it, pictures go to
  `Nothing Gaming Space/SteamGridDB` and trailers to `Nothing Gaming Space/Videos` at the top
  of the phone's storage, and what was saved inside the app before is moved there (copied,
  then deleted: the two are different file systems). Why the permission: since Android 11 an
  app may write by path only to its own folders and the standard ones (Pictures, Movies,
  Download); a folder of its own at the top needs `MANAGE_EXTERNAL_STORAGE`. Without it,
  everything stays inside the app as before. Files are named by package with their real
  extension (`.png`, `.jpg`, `.webp`, `.gif`, from the picture's first bytes; `.mp4`), so a
  picture or video put there under an app's package name is used for it; a `.nomedia` keeps
  them out of the gallery. Compiled; the folder, the move and a dropped-in file not tried on
  the phone yet.
- **0.64.1, the whole top a dot screen; games and trailers chosen by hand.** The top half is
  now one dot screen from edge to edge: no title over it, no bezel or glass around it (the
  name strip of the folders' engine is switched off with an empty label, and the panel's
  bezel is pushed outside the view), and while a picture or trailer shows, the dot screen
  behind it is hidden. Choose artwork now first lists the games SteamGridDB finds for the
  app's name (up to 12, best match first) with "Search another name…" to type one, then that
  game's pictures; the new Choose trailer (with trailers on) does the same with Steam's store,
  then lists that game's trailers, highlighted first, and makes a clip of the one picked.
  Compiled; the look and both choosers not seen on the phone yet.
- **0.64.0, Steam trailers as a saved clip.** With Steam's store now reachable from here, its
  live answer was read (Portal 2): each trailer has `hls_h264` (HLS master playlist),
  `dash_h264` and `dash_av1`, no mp4; the HLS is version 7 with fragmented-MP4 segments
  (`EXT-X-MAP` init, 3 s `.m4s` chunks) and the sound separate. Whether Android's own player
  streams every such HLS is not certain, so the trailer is now made into a short clip once:
  the smallest variant's init segment and first five chunks joined, a plain fragmented MP4
  (checked here on the real trailer: 640 x 360, 15 s, 1.8 MB, valid H.264, a frame of the
  trailer), saved in the app's files and played from there, looping and muted; at most a few MB
  per game. Steam's search can pick another game of a similar name (Crazy Taxi gave "Crazy
  Taxi: World Tour"): Rename fixes it. Checked here: it compiles; the clip-making was done by
  hand on the live playlists. Needs: a trailer playing on the phone.
- **0.63.1, Gaming Space no longer crashes on saved artwork.** Dr.Essence: "the gaming
  launcher seems to crash". Found in the LSPosed log (FATAL EXCEPTION in
  com.dressence.nothinglauncherplus): "Software rendering doesn't support hardware bitmaps" at
  `DotAnimationView.fitPicture`. Since 0.62.0 saved artwork is read with `ImageDecoder`, whose
  default puts a still picture in a GPU (hardware) bitmap, and fitting it to the dots draws it
  into an ordinary bitmap, which cannot draw one; so Gaming Space crashed as soon as a game had
  saved artwork, every time. Now read into ordinary memory, a hardware bitmap is copied before
  fitting, and a picture that still fails leaves the icon instead of crashing. Checked here: it
  compiles. Needs: Gaming Space opening with a game that has saved artwork.
- **0.63.0, Gaming Space: Steam trailers, a bigger banner, adjusting the artwork.** Asked by
  Dr.Essence. Trailers: "Game trailers from Steam (video)" (off by default; no key): for a
  game or emulator, Steam's store search finds it and its store data gives its trailers
  (`SteamTrailers`); the highlighted one plays muted and looping on the big dot screen while
  the game is selected (`DotVideoView`: Android's own player into a TextureView, drawn through
  the round dots' mask), and stops when a game is opened. Since 2025 Steam lists trailers as
  streams (HLS, DASH) and short microtrailer mp4s, not plain files (LaunchBox's report,
  SteamDB's records); the trailer's entry is searched for an HLS link first, then an mp4.
  Steam's store could not be reached from here (the session's network), so this is unread
  against Steam's live answer; a trailer that fails to play leaves the artwork. The banner is
  bigger (half the page; slimmer name strip; cards 92 x 96 dp, app rows 52 dp, to keep room for
  the column). "Adjust artwork" (in the app's menu when artwork or a trailer shows): the
  D-pad moves the picture, L1/R1 (or L2/R2) zoom, A saves, B cancels; by touch, drag and pinch,
  a tap saves; kept per game ("x;y;zoom", `Framing`) and used for still and animated artwork
  and the trailer. For ROMs (asked: a source of real animations): ScreenScraper's video snaps,
  which need a user account and developer credentials it grants to apps on request; Gaming
  Space lists apps, not ROM files, so that would be its own feature (reading ROM folders).
  Checked here: it compiles. Needs: a trailer playing on the phone, adjusting artwork.
- **0.62.0, Gaming Space: its own menus, choosing artwork, animated banners, touch.**
  Asked by Dr.Essence on 0.61.1. Menus in Gaming Space's look instead of Android's dialogs
  (`NothingSheet`: a black card with a grey rim over the dimmed screen, the title glowing green
  in NDot, choices in NDot, the selected one green with a dot; controller: up/down or
  left/right, A, B; touch: tap, tap outside to close). The app menu (Y, X, menu button, a long
  press, or a long press on the big screen): Play, Move to another category, Rename (typed in
  the same look), Choose artwork, Hide. Choose artwork lists what SteamGridDB has for the game
  (up to 4 animated banners first, then 8 still banners, then wide covers, each with its
  preview); the pick is saved and shown. "Animated artwork first" (on by default): when a game
  has an animated banner it is taken first and plays on the big screen (an animated picture is
  drawn under the dots' mask each frame; a still one keeps one colour per dot). Asked for
  with SteamGridDB's `types=animated` / `types=static` (not tried with a key from here). Touch:
  swipe left/right for categories, up/down for apps, tap the big screen to play. Checked here:
  it compiles. Needs: each menu with the controller and by touch, choosing artwork, an
  animated banner playing, the swipes. Next asked: real game footage (Steam's trailers) as a
  further source.
- **0.61.2, the artwork shows.** Dr.Essence: "Check the key" says the key is correct, yet
  no artwork. Cause, found in the code: the artwork's view started hidden (GONE), and Android
  never measures a hidden view; the picture could only be fitted to the dots once the view had
  a size, and the view was shown only once the picture was fitted, so it never was. It is now
  shown as soon as there is a picture and fits it once laid out. Checked here: it compiles.
  Needs: artwork on the phone.
- **0.61.1, Nothing Gaming Space: renaming, and the artwork's lookups shown.** On 0.61.0
  Dr.Essence set a key and saw no artwork, with no way to tell why (failed lookups were
  silent). Now: "Check the key" tries one search and says in words what happened (the key
  works, refused with HTTP 401/403, or the network error); the key's row shows the last
  lookup ("Found …", "No match for …", "… has no artwork yet", or the error). Asked by
  Dr.Essence: renaming, so SteamGridDB finds everything: Y (or a long press) on an app, then
  Rename; the name is shown in Gaming Space and searched on SteamGridDB, and the app's saved
  artwork and any "no match" are forgotten so it is looked up again (stored URL-encoded, as a
  name can hold commas; empty goes back to the app's own). SteamGridDB itself now answers
  from here: without a key `{"success":false,"errors":["Authentication Required"]}`, HTTP
  401, as the code expects. Checked here: it compiles. Needs: "Check the key" on the phone,
  and the last lookup shown after selecting a game.
- **0.61.0, Nothing Gaming Space, third part: game artwork; marked (alpha) (demo).** Dr.Essence
  tried 0.60.0: "many things need fixing", to be discussed; the option now says "(alpha)
  (demo)" (its settings row and the name in the gaming launcher picker). Artwork: a switch
  "Game artwork (SteamGridDB)" (off by default) and "SteamGridDB API key" (the user's own key,
  typed in on the phone, kept in this app's private storage only, not in the shared settings).
  For an app in Games or Emulators, `SteamGridArt` searches SteamGridDB by the app's name
  (also without a subtitle after a colon or dash, and without the trademark sign), takes the
  first match's wide banner (hero), or else a landscape cover, asked with nsfw, humor and
  epilepsy off; the thumbnail is saved in this app's files, so each game is looked up once; an
  app with no match is not asked again for a week. The API as SteamGridDB's own client uses it
  (`/api/v2/search/autocomplete`, `/heroes/game/<id>`, `/grids/game/<id>`, the key as a Bearer
  header; read from github.com/SteamGridDB/node-steamgriddb, as their site cannot be reached
  from here). The artwork goes on the big screen's dots under the name's strip (the category
  animations' view, with a still picture: cropped from the middle to the screen's shape, one
  pixel per dot). The app now asks for the internet permission, used only for this. Checked
  here: it compiles; the lookups could not be tried from here. Needs: a key set and artwork on
  a few games; what a game without artwork and the phone offline do.
- **0.60.0, Nothing Gaming Space, second part: 3D boxes and category animations.** Each app
  in the column is a small box turning slowly, its dot icon (the launcher's dot icons) on a
  dark plate on each of its four sides; the selected one turns faster and is fully lit
  (`DotCubeView`: the corners turned in 3D and projected with perspective, each side facing
  the viewer drawn with `Matrix.setPolyToPoly`, far sides first, darker as they turn away).
  When a category is selected, a short animation plays over the whole big screen, at the
  screen's own dot size (`DotAnimationView`: one pixel per dot, shown through a mask of round
  dots over the unlit dots, as the folders' engine does): a Pong rally (Games), marching
  invaders (Emulators), a turning globe (Browser), equaliser bars (Music), a running film
  strip with a play sign (Video), static rolling into colour bars (TV), a wave of lit squares
  (Apps); then the category's emoji and name, then the selected app. Checked here: it
  compiles, and the cube's projection and every animation were rendered off the phone from the
  same formulas: the cube's sides turn the right way round with their pictures upright; the
  first renders showed the lean the wrong way (the box's bottom, fixed: 24 degrees, top
  showing), an oval globe (its width doubled on square dots, fixed) and too few meridians,
  and a tiny Pong ball and paddles (made bigger). Needs: how both look and run on the phone.
- **0.59.1, Nothing Gaming Space: one dot screen and the cross menu.** The top part is one
  dot screen edge to edge (the drawer folders' dot engine, `AnimatedEmojiView` with a panel
  the page's width): the selected category's emoji or the selected app's icon on the left,
  its name lit in NDot on the right. App icons go onto the dots the way the emoji do (new
  `DotMatrix.setImage`). Below, the categories are dot cards like the drawer's folders (emoji
  and NDot name on one dot card, the folders' 0.86 emoji share and corner), across, the
  selected one held in place and the bar moving under it; its apps go down beneath it (dot
  icon and name), the selected one first and brightest. Left/right (or L1/R1) change category,
  up/down move through its apps, A plays, Y moves or hides; on a new category its emoji plays
  on the screen for 1.4 s, then the selected app comes up. **0.59.2:** found by reading the
  folders' engine before testing (`DotMatrix.layout`): a card's name always gets a strip at
  the top and the picture's screen is fitted below it, so 0.59.1's names (under the cards,
  beside the picture on the big screen) would have left the cards empty and squeezed the big
  screen. The names are now on top, as on the drawer's folders, the picture centred below.
  Checked here: it compiles. Needs: screenshots of a category and of an app selected, and how
  moving feels.
- **0.59.0, Nothing Gaming Space (first part of three).** Asked by Dr.Essence: with a
  controller connected and Nothing Launcher as home, a gaming launcher in Nothing's style.
  It is a home screen in the module app (`ui/GamingSpaceActivity`), so it can be picked as the
  gaming launcher that "Gaming launcher with a controller" makes Android's home while a
  controller is connected; Nothing Launcher comes back when it disconnects (the screen hands
  over once Android's home is no longer it). On start: black, "NOTHING GAMING SPACE" glowing
  in green NDot (NDot 77 from the phone if found), and the first time only a photosensitivity
  warning that goes on after 8 s (A skips). Then, in landscape: a big dot screen on top
  (a category's animated emoji, or the selected app's icon as a dot screen, with its name), and
  below a row of categories with animated NDot emoji covers (the folder covers' engine): Games,
  Emulators, Browser, Music, Video, TV, Apps. Sorting (`GamingLibrary`): the user's choice,
  then a list of known emulators, Android's app category (game, audio, video, or the old "is a
  game" flag), apps that open web links, apps made for TV (leanback), the rest under Apps. Y,
  X or the menu button (or a long press) moves an app or hides it; "Show hidden apps again" in
  the settings. Controller: left/right, A or down opens, B or up goes back, L1/R1 change
  category; the stick moves through Android's own stick-to-D-pad for apps that do not read it.
  Checked here: it compiles. Needs: opened from its row in the settings, then as the gaming
  launcher with a controller (intro, warning once, both halves, every control, an app opened
  and back, moving and hiding an app, the controller disconnected). Next parts: turning 3D
  boxes for the tiles and animations matching each category on the big screen; then game
  artwork from SteamGridDB (Dr.Essence's own API key, typed in on the phone; off by default).
- Found while checking HeliBoard (not the module): HeliBoard 4.2 never splits the keyboard
  (its split flag is never passed to the keyboard builder); fixed in HeliBoard 4.3-beta1
  (commit e1a981b). Taking HeliBoard out of the scope did not change it, and 4.3-beta1 splits.
- **0.55.10, finer dot icons.** Asked by Dr.Essence: more and smaller dots. The grid is 34
  dots across instead of 22 (about 2.4 times the dots, each two thirds the size); the bezel's
  reach over the grid, the glow and the power-on all follow the dot spacing. Checked here:
  renders at 22, 28, 34 and 40 (34 keeps the artwork's shapes crisp and still reads as a dot
  screen at icon size); the class compiles. Needs on the phone: the icons at 34, and whether
  the drawer or the home screen stutters while icons power on (each draws 2.4 times the dots
  during the 0.56 s power-on; if it does, the density or the power-on is the place to look).
- **0.55.6, no green edge on dot icons.** Seen on the phone: icons in a shape of their own
  showed a green outline, which Dr.Essence found did not look good. Their glass was lit green
  (`GLASS_GREEN`) with green unlit dots, and the glass between the outermost dots and the bezel
  read as a green edge. Every shape now has the round icons' dark glass and unlit dots (the
  green colours and the per-shape choice removed). The faint green scan line that now and
  then rolls down an icon stays. Checked here: the class compiles against Android 36. Needs on
  the phone: icons of every shape with no green, the rim and bezel as before.
- **0.55.7, dot icons end in the bezel's outline, every shape** (0.55.5 put round icons'
  dots in rings; Dr.Essence preferred the square grid as before with a bigger bezel, for all
  shapes). Seen on the phone: the dots stopped at the last whole dot that fit the glass, and a
  square grid ends in steps that did not follow the bezel. The bezel is now 0.35 of a dot
  spacing wider (`EDGE_COVER`), the grid runs out to the glass's edge (every dot any of which
  shows), and the dots are cut to the glass: the bezel covers the edge ones, so they end in its
  outline, round or any shape of its own. A half-covered dot is lit as fully as the artwork
  beside it (coverage counts only the glass). The cut also applies while icons power on (the
  dots drawn in a layer, cut with the glass's mask). Picked from three renders (square grid,
  thicker bezel only, bezel covering the edge dots). Checked here: both classes compile against
  Android 36; a round and a rounded-square icon drawn with the same numbers and rules. Needs on
  the phone: round and other shapes with clean edges, the power-on cut the same, no slower.
- **0.55.4, "Dot matrix" in Nothing's Icon Pack picker.** Asked by Dr.Essence: the dot icons
  as a choice next to Nothing's own packs, not something that takes over every icon. The
  module's "Dot-matrix app icons" switch is gone (a row on the Home screen page opens
  Nothing's picker instead, `com.nothing.launcher.icon_pack_picker`); the icons are dot screens
  only while "Dot matrix" is the picked pack. Found in the launcher's code (this build's short
  names): the picker lists every entry of the launcher's map of packs besides Default and
  Nothing (`la.e.w`), filled from apps answering the ADW icon-pack intent (`h7.d.a`); the module
  adds its own entry there (`k7.b("com.dressence.nothinglauncherplus", "Dot matrix")`), so no
  other launcher sees a pack. Its preview circle is the module's icon as a dot screen
  (`d7.h.i`, after). A tap is Nothing's own: `Settings.System nothing_icon_pack` is written and
  the launcher reloads its icons; for this id it finds no appfilter in the module and loads the
  Default icons, which the module draws as screens. The module reads and watches that setting.
  After updating, anyone who had the switch on picks "Dot matrix" once. Checked here: the new
  Kotlin class and the changed Java compile against Android 36 with stand-ins for the Xposed
  calls; the hooked methods and the picker's activity checked in the decompiled launcher.
  Needs on the phone: "Dot matrix" in the picker with a dot preview; picking it turns every
  icon into a screen at once, picking Default or Nothing turns them back; long names scroll
  only with it; LSPosed log "dot matrix icon pack: picked / left".
- **0.55.3, CMF Stereo's live icon.** Seen on the phone (screenshots, both CMF Stereo
  editions): with the Default icon style the two sound waves rested grey (at 0.28 of white)
  and only lit up as the pulse passed, so the icon looked grey; Dr.Essence asked for white
  waves. They now rest at full white, like the app's own icon, and the pulse is a dip to 0.3
  passing the inner wave, then the outer. With Nothing's "Nothing" icon style, the module drew
  its waves, found in the coloured bitmap, over the launcher's themed drawing of the app's
  monochrome layer, which is smaller: bigger dots beside the real ones. A themed drawable
  (`FastBitmapDrawable.isThemed()`, true for `ThemedIconDrawable`) is now drawn as the
  launcher draws it. Checked here: the class compiles against Android 36 with stand-ins for
  the Xposed calls. Needs on the phone: the waves white with a dip passing through, in Default;
  in the Nothing style, the icon as the launcher draws it (no extra dots).
- **0.55.0, Launcher only** (main page, Mode; off by default). With it on, the module changes
  only Nothing Launcher and Nothing's widget apps (Nothing Widgets, NothingCardService), and in
  the system only home screen rotation's part (`CardOrientation`: the home screen and widget
  cards opening in the screen's orientation; the rotation sensor fix; Check orientation on wake
  and unlock; the orientation log, which only reads). Left out: System UI (status bar, lock
  screen, quick settings), the system font, controller home, system shortcuts, battery saver
  refresh rate and sync, Settings' task manager, the wallpapers, HeliBoard, the game bar and
  the 120 fps unlock; "System screens in landscape" then covers the launcher's own screens
  only. The main page hides the System and CMF Phone 1 sections while it is on. Read as each
  process starts: the launcher's part with a launcher restart, the system's after a reboot.
  Needs on the phone: on, after a reboot, home screen rotation as before (turns, the home
  screen opening turned, widget cards) and System UI stock (one-row status bar, portrait lock
  screen); the LSPosed log's "launcher only: the system's other changes left out"; off again,
  everything back after a reboot.
- **0.54.12, the crash-loop guard counts crashes only.** Seen on the phone: setting Nothing
  Tweaks' LSPosed scope put the dual status bar back to one row. The LSPosed log (05:03 to
  05:07) showed no conflict between the modules: System UI was restarted from outside three
  times within three minutes (Nothing Tweaks' and the module's own restarts; `am_proc_died`, no
  exception, no tombstone), and the guard, which counted every start, took that for a crash
  loop and left the dual status bar and the OLED saver off ("left off: System UI kept
  restarting"). At each start the guard now reads why the previous System UI ended, as Android
  recorded it (`ApplicationExitInfo` through `IActivityManager.getHistoricalProcessExitReasons`,
  System UI asking about its own package), and counts the start only after a crash, native
  crash, ANR or failed start (or when that cannot be read). Needs on the phone: a few restarts
  in a row (Nothing Tweaks, LSPosed scope, Restart System UI) keeping both rows; once, after
  updating, save any setting so the off state from before clears.
- **0.54.11, the dual status bar's date hides with the clock.** DebuG24 (recording, frame by
  frame): on the landscape lock screen a line of green text, the date ("SUNDAY, 8 NOVEMBER
  2026"), showed cut in half along the screen's top edge (15.0, 23.8 and 26.0 s). System UI
  takes the status bar clock out of the layout (`GONE`) on the lock screen, while dozing and
  with the shade closed (`CollapsedStatusBarFragment.clockHiddenMode`); the date, drawn on
  the second row, stayed, and with the clock's row gone that row rose to the top. The date is
  the clock's and is now drawn only while the clock is shown (TextView's
  `onVisibilityChanged` on the clock redraws it). Needs on the phone: the landscape and
  portrait lock screen with nothing at the top edge; the date back with the clock once
  unlocked. The debug report also copies the module's settings again (LSPosed keeps them in
  its own folder; the copy came out empty in DebuG23 and DebuG24).
- **Fingerprint on the turned lock screen and always-on display: fails at times, cause not
  found.** Failing (DebuG24 and the module log, 04:15 to 04:22): in landscape the touch panel's
  firmware reported no finger on the sensor (`KEY_GESTURE_FOD`), and each session ended
  cancelled; every unlock then came in portrait. Working (LSPosed log at 04:49 to 04:50): every
  finger the panel reported unlocked, in both landscape sides, on the always-on display and the
  lock screen (one took 1.1 s instead of about 0.25 s). Ruled out: Nothing's edge mode
  (`NtWindowManagerService.updateRotation`, `sys.touch.edge_mode.enable`): it was turned on
  at every landscape turn in the working period too. Needs: a debug report (or LSPosed log)
  started within a minute or two of a failure, so the system log still covers it, and what
  the failure looked like (no reaction, "not recognised", or slow).
- **0.54.8, the lock screen turns straight into its layout.** DebuG23 (a recording on
  perfmtk battery saving, with the log beside it, looked at frame by frame): after each turn
  the landscape lock screen was first drawn with the portrait layout (weather pills across the
  middle, no notifications, no fingerprint ring, the clock on the wrong side) and jumped into
  its halves 0.35 to 0.8 s later (59.65 to 60.3 s and 66.3 to 66.7 s in the recording), in the
  pass the log shows as "lock screen halves". That pass is System UI's own and reaches the lock
  screen through a coroutine, a handler post and a flow, each waiting on the main thread, while
  Android draws the turned frame first. The module now runs that same pass of System UI's
  (`KeyguardBlueprint.applyConstraints`, as its view binder does) as the new configuration
  reaches the lock screen's window, before that frame is laid out; System UI's own pass
  follows and finds it as it would set it. Turning straight from one side to the other does the
  same pass at once instead of asking System UI for one. Not seen in DebuG23: the home screen
  turns went straight to their layout (only about two frames of the dock with four icons at the
  end of a turn to portrait, the launcher's own rebuild), and waking from the always-on display
  was Nothing's own fade. Needs on the phone: a recording of the lock screen turning both ways
  and from side to side, on battery saving, with diagnostic logging ("lock screen turn: laid
  out with the turn to rotation …"); the notifications may still arrive a moment later (their
  container is placed by its own System UI flow).
- **0.54.7, OLED saver over full-screen apps:** with Hide the status bar on, a swipe from the
  top over a full-screen app (a game, a video) brought back an empty strip, and a tap was
  needed on top. The saver now shows the bar for as long as Android shows it after that swipe
  (System UI's `showTransient`, until `abortTransient` / `clearTransient`), then starts the
  set time again. Apps that keep their status bar keep the set time as before. Needs on the
  phone: a swipe over a full-screen app with the bar hidden (the bar shows at once, LSPosed
  log "status bar saver: shown again (full-screen swipe)"), and the start-up line "full-screen
  swipes watched (4 hooks)".
- **0.54.3, dual status bar: Notifications at the end and Date at the end**, two switches
  that work in every combination. Notifications at the end puts the notification icons at
  the bar's right end, under the Wi-Fi/signal/battery icons, in both orientations: the
  notification area (still in the left half, nothing moves) measures as wide as the whole
  bar's content, and since every container on its path has clipChildren off in Nothing's
  layout, it draws past the left half into the empty second row of the right half; Nothing's
  split of the bar is untouched. (0.54.2 put them at the end of the left half: in landscape
  they stopped well short of the end, in portrait the half ends at the camera and only the
  overflow dot fit; seen on the phone.) Date at the end draws the date at the right end of
  the second row (on the end side's overlay); with both on, the icons stop just before it.
  Month as a number and Year in the date as before (the year now in full, see Fixed). Needs
  on the phone: both orientations with each of the four combinations, with notifications; still
  from 0.53: the bar over a light app (dark text and date), during a call or screen recording
  (its chip sits between the rows), and the lock screen's status bar and clock.
- **0.54.1, OLED saver** (Status bar & quick settings → OLED saver; `StatusBarSaver.kt`):
  Move the status bar (never, every 30 s … 30 min), How far it moves (1-6 dp) and Hide the
  status bar (never, instantly with 5 s shown, after 1-30 min). It moves and fades only
  `status_bar_contents`, which neither System UI nor Nothing moves or fades themselves;
  before each move it measures the room the visible content has inside the bar and stays
  within it. Back on screen for a notification not seen before (or an update that alerts
  again, like a new message), a shade pull starting, or the screen turning on; a tap on the
  bar (0.54.6) shows it for 10 s whatever the set time (PhoneStatusBarView's
  onInterceptTouchEvent, which sees every finger down on the bar, watched only); each such
  wake is logged ("status bar saver: shown again (…)", at most every 10 s, until confirmed).
  Needs on the phone: hiding that stays hidden while a download runs, coming back on each
  trigger including a tap, for 10 s after a tap (LSPosed log), the moves over a few intervals (a recording), nothing cut at the
  edges.
- **0.54.1, Restart System UI** (top of Status bar & quick settings and of Lock screen):
  applies the options marked "restart System UI" at once (root). It first leaves a "planned
  restart" note in System UI's folder, so the crash-loop guards do not count that start.
  Needs on the phone: the options applying after it; pressing it three times within three
  minutes leaves the dual status bar and the saver on.
- **0.52.5, dot-matrix icons powering on in step again:** icons drawn while their view was still
  hidden (the first frames of the drawer or a folder opening, when Android fades and slides the
  page without redrawing icons) waited for a 1 s recheck, so they lit up late and one by one. They
  are now checked every frame for 1.2 s after (a visibility check, nothing drawn) and power on in
  the frame they come into view, together, in step with the opening, as the first dot icons did;
  after that, one check a second, so a closed drawer leaves the launcher idle. Converted icons are
  shown together (when the queue empties, or within 150 ms), and the converter no longer runs at
  the lowest priority. Needs on the phone: opening the drawer and a folder with dot icons on, all
  icons powering on together with the opening; the widget list and apps loading as before.
- **0.52.5, live icons:** CMF Stereo's dot-matrix speaker icon animates its two sound waves while
  it is on screen (home, drawer, search), the way Nothing Launcher animates its clock (which it
  gives to one package only). Checked here: the class compiles against Android 36 with stand-ins
  for the Xposed calls; its dot analysis, ported to Python, finds the icon's dots at 128-252 px
  and falls back to the still icon below that; the animation was rendered frame by frame. Needs
  on the phone: the icon pulsing on home and in the drawer, stopping when an app is open or the
  screen is off (`dumpsys gfxinfo com.nothing.launcher` frame counts idle), and the launcher's
  frame time unchanged.
- **0.52.4, controller:** an item's menu (A held, or the menu button) opens Nothing's own
  long-press menu instead of the module's. Needs a debug report with a controller.
- **0.52.3, controller:** focus rings on widgets in their true shape, drawer tiles staying in
  place when focused, focus returning to the item after a menu closes. Built from the DebuG22
  report; needs a new report to confirm.

**Pending**
- **Signing key move (0.52.5):** the release key is private now; CI builds only once its three
  secrets are added to the repository. Updating from 0.52.4 needs one switch: Backup → Export,
  uninstall, install, re-enable in LSPosed if asked, Backup → Import.
- **Landscape lock screen and the Customisation page layout:** in progress.
- **PS2-style interface sounds:** waiting for recordings of the cursor, confirm, back, error and
  memory-card sounds.
- **Nothing Dot Matrix Display:** the engine that replaces the emoji-cover beta (see Roadmap).
- **README pictures:** all views are in (home, app drawer with emoji covers and with normal
  folders, both orientations; landscape lock screen and AOD). They come from alpha builds and
  will be retaken for the public release.

**Fixed recently**
- 0.56.1: the Launcher only switch's description, and the README, present it as the way to keep
  the launcher's features if the others are buggy on a phone, not as a mode for other phones
  (Dr.Essence).
- 0.55.11: at 34 dots across, a rounded-square icon's left edge ended in a dark strip before
  the bezel while its right edge ran under it (Dr.Essence spotted it in the render sent with
  0.55.10; I had not). The grid started at the bitmap's corner, so the columns met the bezel
  differently on each side, and a dot was kept by the depth at the pixel its centre fell in,
  with a margin. The grid is now laid out from the screen's centre (a dot on the centre), a
  dot is kept if any of it touches the glass, and the depth is read at its exact centre.
  Checked by running the real `IconScreen.grid` on a circle and a rounded square: every dot
  mirrored left-right and top-bottom, the widest dark gap at the circle's edge 0.49 px on both
  sides (under the 1.13 px between dots), and the rounded square's dots under the bezel on
  both sides.
- 0.55.9: CMF Stereo's live icon stayed still at times: the LSPosed log (16:08) showed "live
  icons analyse failed: … pixel access is not supported on Config#HARDWARE bitmaps" 9 times.
  The launcher keeps some icon bitmaps on the GPU; the analysis now reads a copy, as the dot
  icons already did (`IconScreen.of`).
- 0.55.8: with 0.55.7 every dot icon was an empty dark screen, no dot lit (seen on the phone,
  screenshot). Mistake recorded: when the dot colour started counting only the glass under a
  dot (0.55.7), the new count was added but the old `n++` was left in the same loop, so each
  cell counted its area twice, coverage never passed 50% and every dot stayed under the 55%
  that lights it. The 0.55.7 check drew the rules again in Python instead of running the Java,
  so it could not see this. Now checked by running the real `IconScreen.colourAt` on the
  computer (reflection, Android 36 library): a covered dot lit in the art's colour, a dot half
  under the bezel lit as fully, an empty one dark; the 0.55.7 code gave all three dark.
- 0.54.5: Restart System UI put LSPosed into safe mode when pressed three times within 30 s
  (seen on the phone: three System UI deaths with no crash report, then LSPosed's safe-mode
  notification: LSPosed cannot tell a restart from a crash). It now restarts at most once
  every 90 s and says how long to wait. The module itself did not crash.
- 0.54.5: with Notifications at the end, the notification row reached the bar's end (no
  overflow dot in portrait any more) but its icons stayed at its start (seen on the phone).
  System UI puts the icon container into the row after the bar is built, where the module
  set it at the end, so it was not there yet; it is now set at the end at each measure of
  the row, before that pass lays it out.
- Renamed **Nothing2Something** (was Nothing Launcher+): the app's name, its pages and
  notifications, the LSPosed log's tag (`Nothing2Something: …`) and the build's artifact. The
  package stays `com.dressence.nothinglauncherplus`, so it installs as an update: settings,
  LSPosed scope and signing key carry over.
- 0.54.1: the dual status bar's year showed as "26" (android.text.format.DateFormat writes a
  lone "y" as two digits); the date is made with ICU's formatter, which writes it in full.
- 0.54.1: the OLED saver's hidden bar came back every 2 s on the home screen (seen on the
  phone): every notification update counted as a new notification, and a download posts its
  progress every second or two. Only new notifications, or updates that alert again, wake it
  now, and the shade only when a pull starts.
- 0.53.1: the dual status bar did nothing but take the date off the clock (seen on the phone:
  one row, no date). Its layout step stored what it changes on System UI's views under an
  Android-owned tag id, which Android refuses ("The key must be an application-specific
  resource id", in the LSPosed log on every layout), so it stopped before changing anything;
  the guard kept System UI running. What it keeps per view is now held in the module's own
  weak maps, never in System UI's view tags.
- 0.52.4: controller menus are Nothing's own, as on a long-press.
- 0.52.3: rings on widgets no longer treat them as containers or squash them; the ring's
  shape ignores the dot badge; focused tiles no longer push their neighbours; focus comes back
  to the item after a menu.
- 0.52.2: drawer folder tiles lift inside the drawer again, and their emoji reacts on focus.
- 0.52.1: the landscape home menu keeps its portrait width; Nothing's settings pages rotate.
- 0.52.0: one focus ring in each item's own shape; widgets act on A (clock opens the clock app,
  L1/R1/X/Start drive the music widget).

</details>

## Getting started

<details>
<summary><b>Who this is for</b> · Nothing / CMF phones, tested devices, feedback</summary>

- **Nothing and CMF phones only.** The module hooks Nothing's own apps (Nothing Launcher,
  Nothing's System UI, Nothing Widgets, NothingCardService). It does nothing useful on other
  phones and is not meant for them.
- **Tested on one phone so far:** a CMF Phone 1 on Nothing OS 4.1 (Android 16). Other Nothing
  devices and Nothing OS versions are untested.
- **Launcher only mode** is there if the other features are buggy on your Nothing phone (the
  main page's first switch): the module then changes only Nothing Launcher and Nothing's widget
  apps, with home screen rotation, and leaves System UI, the lock screen, the system font,
  Settings and every other app as Nothing made them. In LSPosed, Nothing Launcher (with System
  framework for rotation, and Nothing Widgets / NothingCardService for their cards) is then all
  the scope it needs.
- **Why it exists:** it started as a project for my own CMF Phone 1, which I don't use as my main
  phone but as an Android "iPod" (an *Andropod*): music, games with a controller, the home
  screen in landscape. Much of what it does comes from that.
- **Feedback from other Nothing devices is very welcome, and a huge help.** If you try it on
  another phone, please open an issue with:
  - your device and Nothing OS version;
  - what works and what doesn't;
  - an LSPosed log (LSPosed → Logs → save), and a screen recording if something looks wrong;

  - for a screen that looks or turns wrong, a debug report (see *Diagnostics and debug report* under Features).

</details>

<details>
<summary><b>Before you install: protect your phone</b> · safe mode, backups, one feature at a time</summary>

- **Don't use it on a phone you depend on.** It is alpha software that hooks System UI and the
  system framework; a phone you can afford to have misbehave for a while is the right place for it.
- **Have a way out before you need one.** Know how to start your phone with all modules
  disabled (Magisk and KernelSU have a safe mode started by pressing volume down during boot;
  check your root manager's documentation), and how to turn the module off in LSPosed.
  If System UI keeps crashing, Android may offer a factory reset ("Rescue Party"); don't accept
  it, boot into your root manager's safe mode instead.
- **Back up first.** Use the module's **Export** to save your folders and options. Keep a
  backup of your own data as well, as you would before any system modification.
- **Scope only what you use.** In LSPosed, tick only the apps for the features you want (see
  *Install*). System UI and System framework hooks only load after restarting System UI or
  rebooting.
- **Turn on one feature at a time**, and use it for a while before the next, so you know what
  caused a problem if one appears.
- **Leave the test switches off** unless you are testing them: *Lock screen rotation (test)*
  changes the lock screen, PIN screen, fingerprint overlay and always-on display.
- **Keep the module on internal storage** (it moves itself there; see *Install*).
- **Turning a feature off, or uninstalling the module, puts Nothing's behaviour back.** If
  something stays wrong after that, restart System UI or reboot. Check other LSPosed modules
  too: a burn-in protection module once moved the status bar in a way that looked like this
  module's doing.

- **Defaults**: features that depend on one phone's hardware or one build's internals start off
(task manager, lock screen rotation, landscape system screens, performance tools, status bar
and quick settings Ndot, diagnostic logging); the ones that only act on apps you add to the
scope, or find their place by name and stand aside when it is missing, start on.

</details>

<details>
<summary><b>Requirements and install</b> · LSPosed, scope, updates and signing</summary>

#### Requirements

- Nothing Launcher **4.0.20**. Later versions will probably work too, because the hooks avoid
  obfuscated names (see below).
- LSPosed (or another Xposed implementation with API 93+ and `xposedsharedprefs` support).

#### Install

1. Build the APK (`./gradlew assembleRelease`) or download it from the *Build module* GitHub
   Actions run.
2. Install it, enable it in LSPosed and select **Nothing Launcher** as the scope, plus only
   what you use:
   - **System UI**: the lit status bar, quick settings and *Lock screen rotation (test)*;
   - **System Framework** (`android`, needs a reboot): system font and widget cards in landscape;
   - **Nothing Widgets** and **NothingCardService**: widget cards in landscape;
   - **HeliBoard**: typing with a controller.
   The module installs on internal storage only: LSPosed reads it at boot, before a memory
   card used as internal storage (adopted storage, `/mnt/expand/…`) is mounted, and a module
   there was not loaded after a reboot ("Failed to open APK … No such file"), and Android,
   checking its apps before the card is mounted, deleted the module's data (its settings and
   folders) as belonging to a missing app. An install can still land there whatever the APK
   asks for (the "Force allow apps on external" developer option, or an installer that picks
   the card), so after an update, and whenever its page is opened, the module moves itself
   back to the phone's storage (root; `pm move-package com.dressence.nothinglauncherplus
   internal`), and its page says so.
3. Force-stop Nothing Launcher, or use "Restart Nothing Launcher" (root) on the app's Home screen page.
4. In Nothing Launcher settings, set *Drawer layout* to **Smart (Beta)** and tap **Custom folders**.

#### Updates and signing

Every release build is signed with one private key, kept in this repository's GitHub secrets
(`SIGNING_KEYSTORE_B64`, `SIGNING_PASSWORD`, `SIGNING_KEY_ALIAS`), so a new APK installs
straight over the old one and keeps your folders. After an update the module restarts Nothing
Launcher by itself (root), and LSPosed loads the new code right away: no uninstall, no reboot.
A local or fork build without the key is debug-signed and cannot update an installed release.

Builds up to v0.52.4 were signed with a key committed to this repository. A public key lets
anyone build an APK that installs as an update and inherits the module's LSPosed scope, so
from v0.52.5 the key is private. **Switch once, without losing anything:** open the module,
**Backup → Export** (folders, options and covers to a file), uninstall the old version,
install the new one, enable it in LSPosed again if asked, then **Backup → Import**.

Builds from before v0.5.0 were signed with throwaway keys: switch the same way.

</details>

<details>
<summary><b>Recommended companion projects</b> · PerfMTK, HeliBoard, NothingTweaks</summary>

These are separate projects, not part of this module. Each has its own risks, so read its
instructions first.

- **[PerfMTK](https://github.com/JUANIMAN/PerfMTK)** (Magisk / KernelSU module; **only for
  Nothing phones with a MediaTek chip**, such as the CMF Phone 1 and CMF Phone 2 Pro, not for
  Snapdragon ones): Powersave / Balanced / Performance profiles, CPU
  and GPU frequency tuning and thermal limits. The module's planned game options will set CPU
  speeds through PerfMTK rather than on their own.
- **[PerfMTK Manager](https://github.com/JUANIMAN/PerfMTK-Manager)** (same phones): PerfMTK's
  app, for picking a profile, the thermal limits and per-app profiles. PerfMTK's README also
  describes its optional **PerfMTK-Hook** LSPosed module, for switching profiles automatically
  per app.

- **Shout-out: [HeliBoard](https://github.com/Helium314/HeliBoard)**, the best Android keyboard
  there is: open source, private (no internet access), and endlessly customisable. With it in
  the module's LSPosed scope, you can type with a game controller (see *Typing with a
  controller (HeliBoard)*).
- **Shout-out: [NothingTweaks](https://github.com/RevealedSoulEven/NothingTweaks)** (LSPosed
  module, root): system tweaks for Nothing OS, such as status bar and clock options, a network
  speed indicator, navigation bar options, lock screen extras (double tap to sleep, scrambled
  PIN), screenshot and screen recording options, in-display fingerprint colours and a charging
  limit. It goes well with this module.

</details>

## Features

Everything new is opt-in: with the defaults the drawer looks exactly like stock Smart (Beta)
plus your folders. Changes apply right away; the launcher does not need a restart.

<details>
<summary><b>App drawer: custom folders</b> · folders, covers, ordering, opacity, search, export</summary>

Nothing Launcher's **Smart (Beta)** drawer layout sorts apps into folders by category ("Games",
"Tools", …). The categories come from Nothing's server, and you can't change them. This module
adds your own folders next to them. Apps you put in a custom folder leave their automatic one.

- Create, rename and delete drawer folders and pick the apps in each, on a page in the module's
  Nothing look: every app with its icon, a search, and a dot that fills in for the apps chosen
  (apps in another folder say which, and move here when chosen).
- **Reorder the folders** by dragging them in the module's folder list. The drawer fills its
  rows two folders at a time in that order, so you choose which folders share a row and which
  row comes first.
- **Reorder apps inside a folder** by dragging them in the folder editor. The drawer tile and
  the opened folder follow that order.
- **Folder covers**: give a folder an image (centre-cropped to a square, stored at 1024 px) or an
  emoji. The cover replaces the app previews on the drawer tile. It stays square and never
  stretches.
- **Animated emoji covers**: every emoji (1,906, Unicode 16) can be a cover, picked from a
  searchable picker, and plays a motion that suits it — see *Emoji animations* below.
- **Apps without a folder as icons**: instead of an "Others" folder, the remaining apps are listed
  as normal app icons below the folders.
- **Drawer background opacity** slider, so the wallpaper shows through behind the folders.
- Show custom folders before or after Nothing's categories, or hide the automatic categories.
- A **Custom folders** row in *Nothing Launcher settings → App drawer & Search → Drawer layout*
  opens the module's App drawer page. A **Nothing2Something** row at the end of *Nothing
  Launcher settings* opens the module's main menu.
- Long-press a drawer folder for the launcher's own popup: **Add to home screen** creates a real
  home-screen folder with the same name and apps; **Edit** (custom folders) opens the folder editor.
- **Export / Import** saves folders, options and cover images to one file, so the setup survives
  a reinstall.
- Changes apply right away through a reload broadcast. You don't need to restart the launcher.

- **Drawer search** follows the drawer's opacity: Nothing paints its search's sheet, scrim,
  header and results' background itself (`updateCustomColor`), so it rose as a solid black panel
  over a see-through drawer. Its Compose search bar paints a band in the drawer's scrim colour
  from the bar's middle down, which rises with the keyboard, and its search page uses that colour
  as its background: the colour is faded where Nothing reads it, so they follow the opacity too.

</details>

<details>
<summary><b>Emoji covers (beta)</b> · animated dot-matrix emoji on folder tiles</summary>

> **Beta.** Emoji covers are a beta. They will be replaced by the **Nothing Dot Matrix
> Display** engine (see [Roadmap](#roadmap)).

- **Emoji covers (beta)** (on by default; App drawer page): off, every folder shows its apps as
  Nothing draws them, and the emoji chosen for each folder stays saved for when it is switched
  back on (image covers stay). **No emoji covers in battery saver** (off by default) does the
  same only while battery saver is on: the tiles change as it turns on and back off. Hidden
  covers do not animate.
- **Dot matrix** (on by default, switch in the module): the whole folder tile becomes a Nothing
  dot screen. The dots fill the card the launcher actually draws (read from the tile's own
  outline, so they follow its shape and rounded corners and never spill past it), as many whole
  dots as fit with equal margins on every side, and the emoji is lit in colour as a 32 x 32 dot
  image snapped onto the same grid. Motion is rendered into the grid each
  frame (3 x 3 supersampled, four solid brightness steps per dot), so an emoji moves across the
  dots like an image on an LED panel, with edge dots dimming in between for smooth motion.
  Frames whose dots don't change aren't redrawn, and a frame is two draws of one shared dot
  mask. The module's folder list and the emoji picker show the dot versions too.
- **Living dots**: on top of its motion, every emoji plays an effect inside its own dots,
  chosen for what it is. A TV fills with static (and now and then colour bars), a crystal ball
  swirls with magic, faces blink (a real blink: half-closed, shut into a "‿" lash line, open),
  a bead of sweat or a tear swells, hangs and runs down whole, fire flickers, clouds rain,
  coffee steams, buildings light their windows one by one, speakers pulse, notes rise, stars
  twinkle, clocks tick, sirens flash, fish blow bubbles into the open water, beer fizzes
  inside the glass, and so on, mapped to every keyboard
  emoji by the same generator as the motions.
- **Glowing screens**: the lit dots on the emoji screen throw a soft halo of their own colour
  into the glass (the frame stretched with smooth filtering, plus a half-resolution copy for
  the wider falloff), redone every frame so the glow moves with the animation.

- **Folders open and shut**: 📁's front panel tips open on its bottom hinge, showing the
  shadowed inside and the papers in it, then closes again (📂 closes up and reopens). It is
  drawn from the emoji's own dots, so it never changes size; tap it and it opens at once.
- **Objects do what the real thing does**: 🎮 buttons are pressed in combos and the d-pad
  clicks; 🕹️'s stick and ball, lifted by their own shapes, tilt in and out of the boot with spring physics and the fire button flashes;
  📷 📸 press the shutter, close iris blades over the glass only and fire a flash bloom;
  📹 🎥 pulse their red REC lamp; 📽️'s reels turn under their arms; 🎬 snaps shut; 🔒 opens
  like a real padlock, the free leg popping out while the long leg stays in the body, then
  snaps shut; 🎯 has a dart thrown into the bull, quivering as it lands with its shadow, then
  pulled out; ⚽ 🏀 ⚾ 🏐 roll with their shading held still and 🏈 🏉 tumble end over end;
  🎾's ball is bounced on the racket; 🎲 tumbles; 🌍 🌎 🌏 turn as globes and 🌐 is drawn afresh as a see-through wire globe whose meridians go right round, bright in front and dim behind; ⌛ ⏳ drain and
  are turned over; 🎠's horse rides its pole; 🎡 turns behind its legs; ☯️ ☸️ 🔁 turn on
  their tiles, as do 🔃 🔄; 🚓 🚑 🚒 flash their roof lights; 🚘 🚖 🚍 flash their headlights; 🛸's rim
  lights chase; 🚲's spoked wheels turn; 🚦 🚥 cycle with a glow; ☎️ rattles its handset,
  📞 ⏰ ring, 🛎️'s plunger is struck; 🧰's clasps flip down, its lid swings open on its hinge showing the shadowed inside, drops shut and the clasps snap back with a glint; 🎛️'s knobs are turned by hand one after another; 🔌 sparks between its pins; ⚛️'s electrons orbit;
  ⚡ 🌩️ 💥 flash; 📶 🛜 fill bar by bar; buttons and keycaps are tapped; 👾 marches; 🎚️'s
  fader slides; 🕰️ ⌚ and the clocks tick their second hand; 💬 types; 📖 📝 📅 📈 📊 turn,
  write, flip, draw and grow; 🏭 🚂 smoke from the chimney, ✈️ trails; 🚀 throws sparks;
  ☂️ ☔ catch the rain; 🔥 🕯️ 🪔 ❤️‍🔥 and the birthday candles burn, the flame itself
  licking and swaying up from its root; 🎆 sends up a shell that bursts, blooms, droops and
  burns out, 🎇 🧨 crackle; 🌍 🌎 🌏 turn as the whole Earth, put together from all three
  globes, so the far side is really there; 🚿's jets rush and spray drops; 🏏 🏑 🏒 🏸 swing
  and knock the ball away, 🏓 🥍 keep it up, ⛹️ dribbles, 🏌️ drives; ⚽ 🏀 ⚾ spin as they
  are kicked up and 🏈 🏉 are tossed end over end; 💿 📀 hold still while their rainbow
  shimmers; 🚶 🏃 stride, legs scissoring about the hip; arrows nudge the way they point;
  🍿 pops; 🌫️ 🌁 roll; 🚬's smoke wavers; 🍳 sizzles; 🌡️ 🔋 💈 🧭 🎰 🖨️ ⌨️ 🖱️
  do theirs.
- **Faces and feelings**: 😍 😻 🥰 😘 💌's own hearts beat lub-dub; 😵 😵‍💫 have stars
  circling the head; 😴's drawn Zs light one after another, 😪's bubble breathes, sleepers
  without drawn Zs give off real Z letters; 😤's clouds burst out with each snort; 😅 😰 😓
  😥 🥵's sweat and 😢 🥲 😿's tears are lifted off whole and slide down, the skin under
  them patched and the outline redrawn; 🥹's tears well and shimmer; 😗 😚 😙 😽 blow a little
  heart; 😇 👼's halo hovers; 😈 👿 🧐 flash a glint; 😎 catches the light on its lenses; 👀
  glance about within their whites; 💔 cracks apart and mends; 🤖's antenna lamp blinks; 🤹
  juggles, the balls passing behind the head; only round open eyes blink, never ^ ^ or
  brows; angry faces flush from the chin up.

- **Animation test**: in the module's settings, play any emoji's animation on a big dot
  screen without making a folder for it: step through emoji one by one, replay its reaction,
  slow it to half or a quarter speed, and compare the dots with the plain emoji.
- **Nothing is cut off**: a hop, lean or swell that would carry an emoji past the edge of
  the panel is eased back so it comes up to the edge smoothly.
- **Particles are smooth**: steam, smoke, snow, rain, bubbles, confetti, sparks, hearts,
  glints and musical notes are drawn soft and anti-aliased between the dots, so they glide
  rather than jump; glows are a halo of the emoji's own light and never wash it out.
- **Moving parts are drawn, not shuffled dot by dot**: a stick, needle, dart, shackle, reel or
  ball is cut out of the emoji (what it covered filled in from the drawing itself) and moved
  before the emoji becomes dots, in the same supersampled frame, so it moves with smooth
  anti-aliased edges. While something moves the cover renders at the display's full rate (up
  to 120 Hz) and drops back to the panel's 24 fps once it rests.
- **Music plays by its own logic**, all to one shared 96 BPM beat, so the music folders in the
  drawer groove together: 🔊 lights its own drawn sound waves inner to outer like a volume
  meter, 🎵 🎶 light each note head as it sounds, 🎼 runs a playhead along the staff, 🎹 plays a
  melody on its keys (split where the black keys sit), 🥁 🪘 are hit and ripple from the head,
  🎸 🎻 🪕 strings vibrate after each strum, 🎷 🎺 📯 blow notes out of the bell, 🎧 shows an
  equaliser between the cups and 📻 in its display, 🎤 🎙️ glint with a voice, 🔔 rings
  ding-ding from the clapper, 📢 speaks syllables through the horn, 🔇 🔕 flare their red sign,
  🪇 rattle, 🎛️ turn their knobs. Effects find their parts of the emoji by
  analysing its resting dots (screens, glass balls, eyes, water, window panes), so they work on
  any emoji with no hand-drawn assets, follow the emoji as it moves, and play at 24 fps like a
  real LED panel.
- **Reactions**: touch a folder and its emoji reacts at once (its motion plus a flare of its
  effect, a double blink for faces); opening the drawer makes the covers on screen greet you,
  one after another.
- **Each folder is a little device**: the tile is the bezel, and inside sit two screens of
  black glass with a hairline rim. The emoji has the main screen; the name has its own
  pill-shaped display of finer dots (right out to its round ends) with a red status dot, lit
  as a green LED panel, each letter dot glowing softly into the glass, in Nothing's own NDot 57
  letters
  at the launcher's own name size (NDot puts its dots on an exact grid, one every tenth of the
  font size, so every letter dot is one of the display's dots). The font is
  not shipped with the module: it is loaded from the phone (the system fonts, or Nothing's
  widgets app, which bundles it). Without it, the name is lit on the panel's dots in a compact
  3 x 5 dot font. A name too long for the tile scrolls through it a dot at a time.

- **98 hand-tuned motions** (wave, heartbeat, hop, laugh, ring, launch, coin flip, flag wave,
  swim, flicker, …) built on spring physics: anticipation, squash and stretch, overshoot and
  settle. Each starts and ends at rest; `MotionQa`-style checks during development verify there
  are no jumps at 240 fps and no out-of-range poses.
- **Every emoji is mapped** by `tools/gen_emoji_motions.py` to a motion and an effect: exact
  CLDR-name overrides, then name keywords, then its Unicode subgroup. The folder editor shows the
  chosen motion and lets you pick any other, or none.
- **Unique per folder**: speed, strength, pause length and phase come from the folder id, so two
  folders with the same motion never move in sync.
- **Cheap**: one shared vsync callback drives all covers, and only GPU-composited transform
  properties change (no redraw, no layout). Nothing runs while a cover is off-screen, the drawer
  is closed or the launcher is in the background, and plays are separated by pauses during which
  no frames are requested.
- **Frame rate**: while moving, a cover asks the panel for its highest refresh rate at the current
  resolution, capped at 120 Hz (Android 15+), and releases the request when it comes to rest.
  Motions are time-based, so they look the same at 60, 90 or 120 Hz. The system animation-scale
  setting is honoured (0 turns animations off).

Emoji names and groups come from [emojibase-data](https://github.com/milesj/emojibase) (MIT),
based on Unicode CLDR.

</details>

<details>
<summary><b>Dot-matrix app icons</b> · every launcher icon as a little dot screen</summary>

- **Dot-matrix app icons**, an icon pack in Nothing's own picker (Customisation → Icon Pack →
  **Dot matrix**, beside Default, Nothing and any installed packs; the module's Home screen
  page opens that picker). While it is the picked pack, every app icon the launcher draws, on
  the home screen, in
  the drawer, in folders and in search, is a little dot-matrix screen of its own: a bezel
  round a pane of glass with a hairline rim, and on it round dots lit in the icon's own
  colours, each casting a dim halo on the glass. An icon in the launcher's circle is a round
  screen of dark glass; the dot grid runs out to the glass's edge and the bezel covers the edge
  dots, so the dots end in the bezel's own outline, round or any other shape; an icon with a shape of its own keeps it, on the same
  dark glass, the bezel following its outline. Nothing is cut to a circle, and an
  old icon the launcher put on a white plate is shown off the plate, in its own shape. Now and
  then, each icon on its own timing, a faint green scan line rolls down its dots. When icons
  come into view each powers on like a little screen: dots light from the centre outwards on
  a spring with a brief white LED flash, icons a few milliseconds apart. App names too long
  for their icon scroll like the folder names, a dot at a time. One hook on the launcher's
  icon drawable covers every surface; each icon is made into a screen once and cached. The
  pack is the module's own entry in the launcher's list of packs (`DotIconPack.kt`): picking it
  is Nothing's own (its setting, its icon reload), the launcher loads the Default icons for it,
  and the module draws those as screens; any other pack draws as it always does.

</details>

<details>
<summary><b>Home screen in landscape</b> · grid, folders, menus, recents, restoring items</summary>

Nothing's DeviceProfile replaces AOSP's sizing with portrait-only math: cell width is derived
from the screen *height*, everything is scaled against a 1080×2392 portrait reference and the
hotseat is always assumed at the bottom. Nothing also made ten of AOSP's layout methods abstract,
deleting their landscape branches. Forcing rotation therefore squashes everything.

With **Home screen rotation** on, the launcher follows auto-rotate (through its own
`RotationHelper`, which Android 16 otherwise keeps off on phones). In phone landscape, and only
there, the module answers Nothing's sizing methods with AOSP's vertical-bar layout:

- hotseat as a bar on the side (left in seascape), workspace padding to match;
- cells filling the workspace, icon and label sizes from the grid, scaled to fit, labels hidden
  by AOSP's own logic when rows get tight;
- the 1080×2392 reference transposed, so icons keep their size;
- the Smart drawer gets 3–6 tile columns (4 on most phones) instead of two giant tiles.

Around the home screen:

- **Recents** rotates for real. Nothing emptied `RecentsOrientedState.updateHomeRotationSetting`,
  so recents drew its portrait layout turned sideways (thin cards, sideways menus); the module
  restores AOSP's flag and tells SystemUI.
- **Folders** open whole and centred between the system bars: the header is just the name's
  line and the folder has as many rows as fit under it (more apps go to further pages, as in
  portrait). Nothing places them 346 px down under a 399 px header, which left room for about
  one row of apps on a landscape screen and cut the rest off.
- **The launcher's own screens** (home and drawer settings, customization, icon packs) keep
  Nothing's portrait lock: the home screen layout preview there is only drawn right in
  portrait.

#### Home screen opens in its orientation

Nothing's manifest declares the home screen portrait, and Nothing's own RotationHelper lifts
that (`setRequestedOrientation(-1)`, auto-rotate respected) only once the home screen runs.
So each time the launcher started (a reboot, a restart) with the phone on its side, Android
opened it in portrait and the launcher filled its pages for portrait; the screen then turned,
and the launcher threw that away, loaded everything again and laid it out for landscape
(DebuG16, a forced restart: opened at rotation 0, loading restarted four times, pages filled
for portrait at +3.0 s, shown at +3.5 s, landscape ready at +4.1 s). With Home screen rotation
on, the home screen's activity record is given, in system_server as it is made, the value
Nothing's helper asks for: Android opens it in the orientation it ends in, and it is loaded and
laid out once.

#### Landscape grid

The landscape page is the portrait page turned: a C×R portrait grid becomes R columns by as
many of the C rows as the landscape page holds at the portrait cell size (5×9 → 9×4 on a
1080 px tall phone, the portrait cells exactly), so every page fits on any grid, screen size or
density.
Each item sits at its portrait place turned (its portrait row is its landscape column, its
portrait column its landscape row) with its own spans: icons, folders and square widgets turned
that way never meet; a widget of another shape whose spot is taken goes to the nearest free
spot that holds it whole; items of a portrait column that does not fit on the landscape page
go to the nearest free spot. The cells are the portrait cells turned (a portrait row's height
is a landscape column's width, a portrait column's width a landscape row's height), at their
portrait size unless the portrait rows do not fit across (then scaled the same both ways), so
the page keeps Nothing's sizes and spacing; the grid is centred on the page. Icons, folders and widget contents keep their portrait shape
and are as large as their landscape cells hold them, never larger than in portrait: a widget
keeps the least margin Nothing gives it in portrait, and where a landscape row is too low for
an icon and its name at near portrait size (5-column grids) icons and folders show at their
portrait size without names, as Nothing's own landscape profile does. A widget as wide as the portrait page (a
calendar) fills the landscape page's width and is as tall as in portrait, as far as the page
allows; it is placed after the other items, in the room they leave. The grid in use is logged
(`landscape grid 9x5 cells … gap … in … px`).

**Crowded pages.** A landscape page with portrait-size cells holds fewer cells than the
portrait page (36 against 45 on 5×9). If any home page holds more than that (a page full of
apps), the landscape grid gets a row for every portrait column instead, with cells as much
smaller as that needs (9×5 cells of 167 px on 5×9), so every item keeps a cell of its own and
nothing overlaps. This is decided from the launcher's items as the landscape pages are built
(and by the arrangement, should a page still not fit), and goes back to portrait-size cells
on the next turn once no page is crowded. Icons are refitted to the cells either way.

**Arranging landscape on its own.** Icons, folders and widgets can be moved and resized on the
landscape home screen like in portrait. Each keeps that landscape place and size (remembered
per item and per grid, in the launcher's own data), while the launcher's database, and so the
portrait home screen, keeps its portrait place and size. Items never arranged in landscape
are placed by turning the portrait page, around the ones that were. Only a move or resize of
an item already shown counts: the launcher builds new views whenever it binds its pages again
(a rotation, a grid or label change), and their first places are not edits. Places are kept per
portrait grid, so they stay when the landscape page's rows change; a place that no longer fits
the landscape page is left for the item's turned place.

Portrait is guarded at the database itself: every item write ends in one call
(`ModelDbController.update`), whichever launcher method made it (the small methods that set an
item's cells are inlined by ART into the drop, reorder and resize code, past hooks on them; a
landscape drop with a reorder wrote landscape cells into portrait this way). In landscape, a
write that keeps an item on its page keeps its portrait cells and spans in the row, and its
landscape place is remembered; a move to another page or into a folder is written as it is.
Back in portrait, items the launcher changed in memory while in landscape get their portrait
cells back before the pages are built.

The landscape grid only acts on the launcher's own home pages: Nothing draws its home screen
previews (the layout preview in its settings) with the same page classes, and those are left
exactly as Nothing lays them out. The portrait page is folded onto it:
cut into bands as tall as the landscape page and laid side by side, each item where it is in
its band (a 5×9 page on a 4-row landscape page: portrait rows 0–3 on the left, rows 4–7 next
to them). Larger items are placed first; an item whose spot is taken or off the page goes to
the nearest free spot that holds it whole, so nothing overlaps, and is only shown smaller if
no spot on the page holds it. A widget as wide as the portrait page (a calendar) is as wide
as the landscape page.

Nothing insets each widget in its cells by a padding its profile works out per span
(`Rect o(spanX, spanY)` on its DeviceProfile subclass, found by signature); app widgets, its
card widgets and the sizes widgets are told all use it. It works that out from its own idea of
the cell, which in landscape is not the cell in use, and came out negative, so widgets grew
past their cells into their neighbours. Landscape takes the portrait profile's padding, as its
cells are the portrait ones.

Pages mark a widget's cells from its item (its model cell and portrait spans; a Nothing card
even its portrait cell), not from where it is shown, so on a landscape page the cells it was
shown on read free while its portrait ones read taken: drags and resizes met phantom widgets
and went over real ones. On the launcher's landscape pages every view marks the cells it is
shown at, as Nothing already does for icons and folders. Nothing's resize frame also set a
widget up from its item, putting it back to its portrait spans (the calendar shrank to five
columns the moment it was touched) and freeing only those cells; while it sets up a widget on
a landscape page the item reads as shown there, and a widget that may be as wide (or tall) as
the portrait page may be resized as wide (or tall) as the landscape one.

The launcher makes its profiles one after another and keeps a copy of each, which it copies
its own from and Nothing sizes its widgets with. A landscape profile made before the portrait
one had none to take its cell from (stock 396×92 cells, 85 px icons), so the portrait cell is
remembered as the launcher measures it, and the landscape profiles are made again once all
are there. Each page's items are laid out on it in their portrait
reading order, left to right and top to bottom: widgets first, then apps and folders in the
room left. A widget taller than the landscape page is shown as tall as the page.

The arrangement is done on each page's own views once the launcher has put its items there,
never by reading the launcher's model while it loads (which held the model lock on the main
thread and froze rotation for seconds). Each item is first put in its turned portrait cell,
which is always on the page, so the launcher never drops one for being off the grid.

The launcher deletes any item it binds onto a cell that is already taken ("Collision while
binding workspace item"). Turned cells can overlap until the page is arranged, so in landscape
this deleted widgets for good, in portrait too. While a landscape page is bound no cell counts
as taken, and a collision delete in landscape is refused and logged
(`Nothing2Something: landscape grid: kept …`).

A rotation relays the launcher out and rebinds its pages, which are filled a moment later: the
first frames on the turned screen were the old pages stretched to it, then empty pages. Android
keeps showing the last frame while it turns the screen until the app draws, so drawing is held
from the rebind until the pages are filled and arranged (as the launcher does at startup), for
one second at most, and only while the launcher has focus: an app opening over it in
landscape turns it too, and the app's opening animation must keep drawing.

The launcher's loader waits for an idle main thread between its steps (home screen, then apps,
shortcuts and widgets), with no time limit. A view redrawn every frame keeps the main thread
from ever going idle, and the widget list then stayed empty ("Widgets and shortcuts aren't
available") until the launcher went to the background. Dot-matrix icons animate only while
they are in view (the closed drawer and other pages get one still frame, rechecked once a
second), and the loader waits for idle at most three seconds per step.

- The launcher database keeps the portrait layout: moving or resizing an item within its page
  in landscape is remembered for landscape only and leaves its portrait place as it is.
- An item added in landscape, or moved onto another page there, goes to a free spot of the
  portrait page: the launcher deletes items that overlap or leave the grid when it next loads,
  so every landscape write is checked against the portrait page. The page is then arranged
  again in place, without reloading the launcher. The launcher reads an item's cells for its
  database row later, on its own thread, so the row itself is kept inside the portrait page
  where it is written (moves and new items): a landscape cell past the portrait grid never
  reaches the database, whichever thread runs first.

#### Long-press menus

Nothing makes each row of its long-press menus (an app icon's App info / Remove / Hide icon, a
folder's Customise / Rename: `NTPopupContainer`) as wide as the screen less its margins, a
phone-wide list in portrait. Turned, that is the long side, and the rows ran past the screen's
edge (DebuG18). Nothing hands that width to its own row builders; in landscape they get the
width the screen's short side gives, as in portrait, and the menu itself is that wide (its
layout made it, and its shortcut grid's background, as wide as the screen). Nothing places the
menu at the screen's left inset, right for a full-width list; in landscape its placement puts
it centred on the item held, inside the screen, above or below as Nothing decides. The held
icon's highlighted copy is placed less the drag layer's top inset only, though the layer adds
its left inset too; turned, that inset is the camera cutout's side and the copy showed beside
the icon as a second one: the left inset is taken back the same way (no change in portrait).

#### Widget picker, drawer search and drawer folders

- **Widget picker** in landscape: Nothing sizes its cards and previews as fractions of the
  screen's width and height (a card is 45% of the width), so on the turned screen a profile made
  for it drew every card and preview about 1090 px wide on a 1080 px high screen, cut off at the
  sheet's edges; the profile was also made once and kept, so one orientation's sizes reached the
  other. Each orientation keeps its own profile. Turned, Nothing's constructor works on the
  portrait screen, so cards, previews and preview cells are their portrait size, and only what
  places them is worked out for the turned screen: its size, the options sheet (the height under
  the status bar), the heights' positions and the side margins that center the content. The
  list's page indicator sits 16 px above the screen's bottom, as in portrait. Turned while
  open, the sheet is rebuilt for the new orientation by Launcher3's own rebuild
  (`WidgetsFullSheet.onDeviceProfileChanged`, which on its own rebuilds only between one and
  two panels), so it takes the new orientation's sizes.
  On a widget's options the list's page dots go away, as in portrait: Nothing hides them there
  by sliding the whole sheet down so its bottom leaves the screen, which the turned options page
  (the screen's height) barely does, so turned they are hidden with Nothing's own dots animation
  and shown again by its own reset when the list comes back.
- **Drawer search after a turn**: turned in place, Launcher3 saves its state
  (`onSaveInstanceState` in `onHandleConfigurationChanged`) and androidx drops the launcher's
  lifecycle to "created" while it stays on screen; Nothing's Compose search only updates while
  "started", so it took typing but showed no results until the launcher was left. The lifecycle
  is put back where it was after the turn.
- **Drawer folders** open in landscape as in portrait, name over apps: the folder sheet gets as
  many columns as the drawer has tiles and only as many rows as leave its name room (Nothing's
  sheet gives the list at least four rows, which squeezed the name off a landscape screen).

#### Other landscape details

- **Hidden navigation bar.** With Nothing's "hide navigation bar" on, the taskbar window
  (which draws the gesture pill on phones) is made 0 px tall when it is created; when the
  home screen turned, the taskbar's `onRotationChanged` sized it again to the pill's height
  without that check, so the pill came back in landscape. That one resize is skipped while
  the bar is hidden.
- A **live wallpaper** that turns with the home screen draws its first frames after a turn
  itself: one that rebuilds its renderer for the new size shows black, or a white frame, until
  it has drawn. That is the wallpaper's own redraw (Living Worlds' is replaced by the module's
  framing, see *Living Worlds smooth turning*).
- **Smart drawer tiles** are sized for about one and a half rows, with icons scaled to the tile.

Nothing also blocks landscape outright: `NTLauncher.handleConfigurationChanged` ignores every
change to landscape ("Skip to handle the config change for the landscape orientation"), and the
landscape profiles are filtered out of the list the launcher picks from. With rotation on, the
module handles the change the way AOSP does and lets the home screen activity, and only it,
pick from the full list. Nothing's list is left as it is: the taskbar, which draws the gesture
handle and hides it when "Hide navigation bar" is on, and the settings previews keep the
profiles Nothing made them for (given landscape ones, the hidden handle came back in
landscape).

Each landscape profile is logged (`Nothing2Something: landscape profile …`) with its geometry, so
LSPosed logs show exactly what was laid out. Portrait is untouched.

#### Restore missing home items

*Launcher → Restore missing home items*. Nothing Launcher keeps one database per grid size
(`launcher_5_by_8.db`, `launcher_5_by_9.db`, …); when the grid changes it moves the items into
the new grid's database and drops the ones it cannot place, while the old grid's database keeps
them (DebuG16: two folders, two icons and four widgets, all from row 4 down, were only in the
5×8 database). Inside the launcher, on its own model thread, the most recently changed other
grid database of the same width (and no more rows) is read, read-only; its home screen icons,
shortcuts and folders (with their contents) that the grid in use lacks are added through
Nothing's own database insert at their own page and cells where those are free, and the launcher
reloads. Nothing is removed or moved and no app is added twice. Widgets are not brought back
(their ids belong to the widget service, which released them); the launcher names the ones to
add again.

</details>

<details>
<summary><b>Widget cards and system screens in landscape</b> · Calm, battery and other cards, Settings pages</summary>

- **Nothing's widget cards** (Calm / breathing, battery, photos, …) and their settings open in
  landscape. Nothing Widgets (`com.nothing.hearthstone`) and NothingCardService declare every
  screen portrait-only, and Android applies that as a card launches, so cards opened from a
  landscape home screen showed on their side. The lock is lifted in system_server as the card's
  activity is created (System Framework in scope), on that activity's own copy of its info, and
  again in the apps themselves (tick both apps in the module's LSPosed scope). Cards also turn
  without being restarted, so a breathing session keeps going. Nothing lays its cards out for a
  portrait screen only (it ships no landscape layout for any of them), so in landscape each
  card (any translucent, portrait-only screen of those apps, Calm and Relax included, and any
  Nothing adds later) is laid out on a canvas the size of the portrait screen, exactly as in
  portrait, and what it shows there (its views that draw, close button included) is centred
  in the room the system bars leave, scaled down evenly only if it does not fit. The card's
  layout and code see a portrait screen, so it looks and works as in portrait on any screen
  size, aspect ratio or density. The canvas is put in place as the turn is told to the card,
  before it is laid out for the turned screen, and is exactly the size the card has in
  portrait, so turning never lays the card out again: it only moves and scales, settled
  before the first turned frame is drawn (it used to show small for a moment, or for seconds
  when its views settled late). A card darkens the screen behind it with the system's dim
  layer, which the system takes away for a turn and fades back in after it (the home screen
  flashed grey behind the card); from its first turn on, the card draws the same darkening
  itself as its window's background, which turns with it, while its opening stays as Nothing
  made it. (Stretched across the landscape screen, the breathing card
  was taller than the screen: opened in landscape it showed as a full-screen gradient with no
  corners or close button, and after a turn it filled the height with its close button cut
  off.)
- **Nothing's system screens built for any orientation follow auto-rotate** (switch: *Status
  bar & quick settings → System screens in landscape*; it starts as Home screen rotation was
  set), where Nothing declares them portrait-only: SystemUI's quick settings widget picker
  (`QSWidgetActivity`, a scrolling list) and the Settings app's pages (lists; the orientation
  log showed `SettingsHomepageActivity` and `SubSettings` asking for portrait), except
  PIN / password / lock confirmation, fingerprint and face set-up, first set-up and the
  start-up home, which stay portrait. Each portrait-only screen of
  a system app you open is written to the LSPosed log once (`portrait-only screen: …`, with
  diagnostic logging on), so
  others (Settings pages and the like) can be checked in Nothing's code and added. Screens
  that are only a notice (the overheating warning, the AI screenshot tip) stay as they are.
  System framework scope and a reboot are needed for this.
- **The lock screen and Nothing's Customisation page stay portrait** by default, as Nothing
  made them: neither has a landscape design (the lock screen left its clock in a corner and its
  notifications in a narrow column, the Customisation page fell back to its foldable layout,
  tiny on a phone).

</details>

<details>
<summary><b>Lock screen and always-on display in landscape (test)</b> · off by default; fingerprint, notifications, shade</summary>

- **Lock screen rotation (test)**, off by default. When it is on, the lock screen follows
  auto-rotate: SystemUI's own switch for that, `KeyguardStateController
  .isKeyguardScreenRotationAllowed()` (also set by the `lockscreen.rot_override` property and
  `config_enableLockScreenRotation`), answers yes. In landscape, SystemUI's resources
  (`values-land`) turn split shade on, and the lock screen would swap Nothing's own layout
  (the `default` keyguard blueprint) for AOSP's `split-shade` one. Nothing added its pieces
  (date, widgets, depth-effect wallpaper) to its default layout only, so the logged landscape
  layout showed just the clock across the top with the notifications over it. That swap
  (`KeyguardBlueprintRepository.applyBlueprint`) is switched off, so Nothing's layout stays.
  SystemUI still puts the notifications on the right half in landscape, so turned, Nothing's
  clock, date and widgets end at the middle in the same layout pass (`split_shade_guideline`
  at 50%, as AOSP's split layout does with its clock) and center on one half, with or without
  notifications (Nothing's centered layout spread its widgets across the middle, where the
  charging line under them ran into them). Each landscape layout is still logged (`lock screen layout: …`).
  The fingerprint sensor under the screen stays where it is however the screen turns.
  SystemUI's sensor overlay (`UdfpsControllerOverlay`, which lights the sensor and reads the
  finger) turns the sensor's spot with the screen (`RotationUtils.rotateBounds`) everywhere
  except on the lock screen (`shouldRotate()`), which AOSP assumes never turns, so in landscape
  it stayed at the portrait spot, off the screen. That one exception is lifted while the lock
  screen turns (not while going to sleep). The lock icon over the sensor
  (`DefaultDeviceEntrySection`) is placed from the sensor's unturned spot; it is given the spot
  turned the same way, in the lock screen's own coordinates (logged as
  `lock screen fingerprint: icon at …`). The overlay turns its `sensorBounds` Rect in place on
  every layout (twice when it shows), and after `updateOverlayParams` that Rect is the overlay
  parameters' own, so the spot was turned again and again and the scanning animation landed
  near the top left; while the lock screen turns, each layout starts from a fresh copy of the
  unturned spot, so it is turned exactly once. The PIN / pattern / password screen (bouncer)
  is blacked out in screen recordings (a secure window), so its layout is logged too: in
  landscape Nothing's portrait-only PIN layout was squeezed under its lock icon. SystemUI
  carries AOSP's own landscape bouncer (`keyguard_*_motion_layout`, which jumps to
  `split_constraints` in landscape: entry beside the keys), switched on by the unreleased flag
  `lockscreen.enable_landscape` (`update_bouncer_constraints` is already on for phones). That
  flag is answered yes only inside the bouncer's own code (choosing and setting up its layout,
  and its turn listener), not for the shade or quick settings; so while the test switch is
  on, the bouncer uses that layout in portrait too. Should that code ever fail, the error is
  kept from System UI and the bouncer goes back to Nothing's layouts. The bouncer's
  fingerprint icon is placed over the sensor as on the lock screen. Restart System UI after
  turning the switch on, so the bouncer is built again. In the landscape bouncer, Nothing's
  bouncer lock glyph (placed 348 px from the top, for portrait) is left out, and the message
  above the PIN starts at the top of its half instead of 448 px down
  (`nt_message_area_margin_top`), which had pushed the PIN entry below the bouncer; both come
  back as Nothing made them in portrait. Nothing's clock, measured as wide as the screen, gets
  the half's width, also in the pass where SystemUI lays out the clock alone (kept at full
  width it stuck out past the screen's edge and vanished). With the fingerprint sensor on the
  left (the screen turned 270°), Nothing's clock, date and widgets go to the right half
  (Nothing's own layout put its clock on the left over the sensor's icon), and the lock screen's notifications to the left half (only while the lock screen itself shows;
  pulled down, the shade is laid out as SystemUI makes it). Turning straight from one
  landscape side to the other keeps the screen's size and orientation, so Android tells System
  UI of no configuration change and Nothing never laid its lock screen out again: the lock
  icon stayed over the sensor's old side and the clock and notifications kept their old
  halves. On that turn System UI is asked for the relayout it does on a configuration change
  (`KeyguardBlueprintInteractor.refreshBlueprint`) and the notifications are placed again
  (logged as `lock screen flip: …`). The always-on display follows the
  screen's turn like the lock screen (its landscape layout is logged for fitting it). Fingerprint events in landscape are logged
  (`fingerprint: …`: finger down / up, the overlay window's place, what the fingerprint
  service answers). **Fingerprint in landscape**: the Goodix driver reads only with the
  panel's high-brightness mode (HBM) on, and MediaTek's display composer
  (`hwcomposer.mtk_common.so`) switches HBM on for a frame only when it holds a buffer named
  `NTFingerprintDimLayer` (`HWCLayer::setupPrivateHandle` finds the name). Nothing's
  SurfaceFlinger leaves the layer *named* `NTFingerprintDimLayer` out of composition whenever
  the display's transform hint is ROT_90 or ROT_270: its Layer constructor tags the layer by
  name, and the per-layer step that builds the composition list returns early for the tag when
  `mActiveDisplayTransformHint` is 4 or 7. (Found with kernel uprobes on CompositionEngine's
  checks: in landscape the layer never reached them, while other layers did.) So in landscape
  HBM never came on and every touch timed out (`GF_ERROR_UI_READY_TIMEOUT`). Nothing's two
  sensor layers, the dim and the illumination dot, are also drawn in the panel's portrait
  coordinates, so the dot fell off screen when turned. With the lock screen turning, the module
  keeps Nothing's mechanism and makes it work turned (logged as `landscape fingerprint: …`):
  the dim layer's layer name leaves out the tag (its buffer, which the composer reads, keeps
  Nothing's name), and both layers are placed in the panel's portrait coordinates in every
  rotation (AOSP's `rotateBounds`; identity in portrait) through a parent container layer they
  move into in Nothing's own show transactions, turned again after each turn: their own matrix
  cannot place them, as Nothing's `BLASTBufferQueue` gives them a destination frame, and
  SurfaceFlinger then takes their geometry from that frame alone. With colour inversion on, the dim keeps Nothing's name, as SurfaceFlinger
  exempts the tagged layer from colour transforms.
  **Notifications, turned**: the module keeps Nothing's lock screen layout ("default"
  blueprint) where landscape would switch to AOSP's "split-shade" one, but the default layout's
  notification section is the portrait one: it starts the notifications below the clock's
  column (logged `nssl_placeholder` top 1026 of a 1080 high lock screen), so in landscape they
  were laid out off the screen and only their icons showed. Turned with split shade, that
  placeholder's top is where AOSP's split-shade section puts it (the screen's top,
  `keyguard_split_shade_top_margin` down). Their column then ran over the fingerprint icon
  (column from x 85, icon at 198..384): on the lock screen itself SystemUI's own margin on the
  sensor's side (`SharedNotificationContainer.updateConstraints`) is widened to end the column
  16dp short of the turned icon, so no notification, however many or however tall, reaches
  the circle; placed again whenever the icon's spot changes.
  **PIN page, turned**: Nothing draws the PIN page's backdrop on the scrim that covers the
  screen. In portrait that is the notifications scrim, see-through (0.54) in Nothing's bouncer
  colour with the blur behind it; turned (split shade) the behind scrim covers the screen and
  the notifications scrim only the notification column, so the backdrop was the behind
  scrim's opaque black, and with an opaque scrim System UI drew no blur. While the lock screen
  turns, the PIN page's backdrop goes on the behind scrim with the portrait values, and the
  blur comes back.
  **Widgets, turned**: Nothing stacks its date, clock and widgets down from the top, sized for
  portrait; with the clock set to its normal size the widgets ran off the bottom of the turned
  lock screen and always-on display (to y 1114 of 1080). Whatever the clock's size and the
  widgets, the widget container is drawn as large as fits above the bottom edge (scaled on its
  top, its place and Nothing's layout unchanged), full size when it fits; the always-on
  display's burn-in movement counts the widgets as drawn.
  **Charging line, turned**: Nothing centers the lock screen's charging / status line across
  the screen; turned, it ran over the bottom of Nothing's widgets (charging text at y 952, the
  widgets to 999), on the lock screen and the always-on display. With the halves, it is
  centered in the other half, under the notifications and the fingerprint icon.
  **Rotation sensor (MediaTek `dev_orient`)**: it reports only changes, and the window manager
  started it again at each wake clearing the rotation it knew, so a phone held as it was gave
  nothing to report and the screen kept its old rotation until it was turned; for such a sensor
  it now starts again keeping what it knew (Android's own `enable(false)`). The always-on display
  turns with the phone too (setting, on by default): the window manager keeps the sensor on while
  dreaming when `shouldStayEnabledWhileDreaming()` says so, which Android answers only for a
  sensor that can wake the phone; with the setting it answers yes, and the screen's turning off
  still stops it.
  **Pull down from anywhere, turned**: System UI's drag-down helper sees a pull only through the
  shade window's intercept, which Android asks about a gesture's moves only while a view in the
  window has its touch. In portrait the notification stack covers the lock screen's middle and
  takes it; turned it is one half, the middle has no view that takes a touch, and pulls from
  there never reached the helper (only pulls from the status bar's clock worked). While the lock
  screen turns, a move of a gesture the window holds itself goes to the same intercept; when
  the helper takes it, the pull goes on as an intercepted one and the panel is told it ended.
  **Shade over the turned lock screen**: System UI's falsing check (BrightLine) keeps the
  screen's size and dpi from when it started (portrait: 1080 x 2400) and its distance check
  sets its thresholds from them once, so turned, vertical swipes were measured against the
  wrong axis (a swipe of 530 or 606 px rejected against 617 px; the turned screen's own
  threshold is 496). The pull down on the lock screen was aborted as a false touch, System UI
  opened quick settings over the lock screen instead, and they were hard to leave: Nothing's
  `closeQsIfPossible` leaves them open in split shade, so a swipe up opened the bouncer and,
  once it was put away, the lock screen came back with them still open. While the lock screen
  turns, the falsing check's size and dpi follow the rotation (its thresholds worked out from
  them with System UI's own settings and sums). Its distance check works in inches (1.5"), which
  turned is almost half the screen's height (496 of 1080 px, against 617 of 2400 in portrait),
  so a pull down from the middle of the lock screen was rejected; turned, the vertical
  thresholds take the share of the height they take in portrait (278 px). Its proximity check took the hand holding the
  turned phone (over the sensor at the top edge in portrait) for a pocket and threw away every
  swipe shorter than 80% of the screen (`percentInProximity=1.0` on full swipes down and up to
  the PIN page); while turned that check passes. Quick settings over the lock screen close
  on back or when the panel closes, as Nothing closes them when split shade returns to the
  lock screen.
  **Always-on display, turned**: its burn-in movement (`AodBurnInViewModel`, applied as
  translationY to the clock, date, AOD notification icons and Nothing's widget container) moves
  them within Nothing's `burn_in_prevention_offset_y` (100dp), sized for portrait; turned, the
  lock screen's content already fills the height, so they went about 416 px down and the
  widgets were cut off. In landscape the movement's vertical offset is kept, where its
  `BurnInModel` is made, within the room those views leave on the lock screen (logged as
  `always-on display: …`); they keep drifting against burn-in. Portrait is unchanged.
  A finger on the sensor also pulled the shade down: Nothing's touch handling asks
  `UdfpsUtils.isWithinSensorArea` with the touch as it is on screen against the sensor's
  unturned bounds ("rotate to portrait" off), so in landscape the sensor window never took the
  touch. While turned, the touch is turned to portrait first with UdfpsUtils' own
  `getPortraitTouch`, as AOSP does by default.
  Not from this module: "Remove anything covering this area" is Nothing's accidental-touch
  protection, shown when its proximity sensor (top edge in portrait, a side in landscape) is
  covered, e.g. by a hand holding the phone sideways.

#### Shade over a turned screen

On the lock screen the module places the notifications on one half, clear of the fingerprint
icon. SystemUI places them again only when its own values change, and turned they often do not
when the shade's state changes, so that placement stayed in the pulled-down shade (DebuG19:
the stack 419 px short of the edge on the lock screen, over quick settings above an app). At
each state change SystemUI's placement is done again with its own latest values: the module's
on the lock screen, SystemUI's own everywhere else.

</details>

<details>
<summary><b>Status bar and quick settings</b> · Ndot clock, green icons, dashboard tiles</summary>

- **Ndot status bar** (option): System UI is in the module's scope; the status bar is lit
  like the drawer's folder screens on any wallpaper: the clock and date in Nothing's NDot in
  the green of the folder name panels with a soft glow, and every icon (network type,
  signal, Wi-Fi, airplane mode, headphones, alarm...), the battery and the network speed in
  the same green, their own shapes untouched. Another option puts the date before the time. In battery
  saver the battery shows Nothing's own yellow saver fill (its outline and percentage stay green),
  and turns all green again when saver goes off.
- **Green quick settings** (option): the tiles' icons and labels glow green, and a tile that
  is on lights deep retro-display green where it was white, or, with **Amber dashboard
  tiles**, the warm amber of a retro car dashboard. The icons' own animations still play.

- **Status bar.** The clock pressed against the screen's edge, investigated in 0.29.8–0.30.0,
  came from another LSPosed module (OLED burn-in protection shifting the status bar), not from
  this one; the guard and the diagnostic hooks added for it are removed again.

</details>

<details>
<summary><b>Recents and Running services</b> · memory in recents, tile, shortcut, task manager</summary>

#### Memory in recents

*Home screen → Memory in recents* (on by default): recents shows how much memory is free of the
phone's total (rounded up to the size it is sold as, 8 GB not 7.4), and below it the swap
(zram) free of its size when the phone has swap, as crDroid's and Infinity X's recents do. It
is one more button in recents' row of action buttons (`OverviewActionsView`'s
`action_buttons`, beside Screenshot), made with Nothing's own `OverviewActionButton` style and
taking the Screenshot button's background, colours, padding and focus, so it looks like it and
fades, hides and moves with the row by Nothing's own animation. The figures are read off the
main thread every 3 s, only while the button is on screen.

A tap opens Running services. In Nothing's Settings it is only a fragment
(`com.android.settings.applications.RunningServices`) that Developer options opens in
`SubSettings`: there is no `Settings$DevRunningServicesActivity` (crDroid's way in, START
result -92), and `Settings$RunningServicesActivity` shows the app list. `SubSettings` needs
`LAUNCH_SUBSETTINGS` (preinstalled|signature), which Nothing Launcher does not ask for, so the
launcher asks system_server (`SystemShortcuts`), which is not held to it, to open the fragment
there as Developer options does. With no answer from the system (it loads its part at a reboot
after updating), developer options opens instead.

#### Running services tile and shortcut

Running services also opens from a quick settings tile (*Running services*, in the tile
editor) and from a shortcut: the module is listed where launchers offer shortcuts (Launcher3's
widget list), as Settings offers its own *Settings shortcut*, and placing it puts a Running
services icon on the home screen; the module icon's long-press has it too. *Home screen →
Running services shortcut* turns the shortcuts off (the shortcut entry is disabled, the
long-press shortcut removed, placed icons greyed). Each opens an invisible activity
(`ui.RunningServices`) that asks system_server for the page, as recents' memory button does,
or opens developer options when the system does not answer.

#### Task manager in Running services

*Home screen → Task manager in Running services* (off by default: it hooks Nothing Settings'
internal classes, whose names change from build to build; Settings in the module's LSPosed
scope). Nothing's own Developer options → Running services page becomes a task
manager; nothing of it is replaced:

- Under Nothing's memory bar, a header in Nothing's dot-matrix look (NDot 57, dot meters lit
  red past 80%): CPU load and the two clusters' clocks, memory and swap in use, network speed
  down and up, temperatures (battery; CPU and GPU where the thermal HAL answers Settings) and
  the five processes using the most CPU, system processes included.
- On each row, under Nothing's summary: the process's CPU share and its app's network use
  today.
- A long-press on a row: package, process, PID/UID, memory, CPU, network today, and *Stop
  background* or *Force stop*. Stopping is rated: Android's own processes (UID under 10000),
  persistent apps, the home screen, the keyboard, telephony, Bluetooth and Google Play
  services are *dangerous to stop* and ask again; other system apps warn that a feature may
  stop until they start again. Android itself and Settings cannot be force stopped from here.

What Settings' process may read decided the sources (a probe on the phone): it runs as the
system user and holds FORCE_STOP_PACKAGES, KILL_BACKGROUND_PROCESSES, DUMP and
READ_NETWORK_USAGE_HISTORY, and reads /proc/stat, /proc/meminfo and cpufreq; GPU load, the
thermal zones, /proc/net and other processes' /proc files are closed to it. So per-process CPU
comes from Android's CPU tracker (the `cpuinfo` service's dump, allowed by DUMP), network use
from NetworkStatsManager, speeds from the traffic counters. Figures refresh every 2 s off the
main thread while the page is open. The hooks use Settings 16.0.0's obfuscated names
(`RunningProcessesView.a`, row holder `A1`); a Settings update that renames them turns the
task manager off and leaves Nothing's page as it is.

</details>

<details>
<summary><b>Game controllers</b> · launcher navigation, gaming launcher, HeliBoard typing</summary>

Android already maps the D-pad and left stick to arrow keys, A to select and B to back, and
Launcher3 moves its keyboard focus with them. The module adds:

| Button | Home screen | App drawer |
| --- | --- | --- |
| Start | Open drawer | Close drawer |
| PS / Xbox / Guide | Open drawer (in an app that does not use it: home) | Close drawer |
| Y (triangle) / X (square) | Shortcuts / menu of the focused item | same |
| L1 / R1 | Previous / next page | Scroll up / down |
| L2 / R2 | First / last page | Top / bottom |
| L3 / R3 (stick click) | Home options (wallpaper, widgets, settings) | Close drawer |
| Select | Notifications | Notifications |

On a focused **widget**, A (cross) taps it, so a clock opens Clock and a Calm widget starts a
breath (Launcher3's own keyboard path ended in the widget's long-press menu). On a **music
widget**, A plays or pauses, L1 / R1 skip back / forward and Start opens the app playing (found
by the system, which may list media sessions; the launcher may not).

The focused item shows one thin ring in its own shape: the module reads the item's outline from
what it draws (an icon's shape, a folder's disc, a widget's card or circle, a menu row's pill),
leaving out small marks such as notification dots, and rings it just outside. A widget that
draws no solid card (a calendar's text, a clock's digits) is ringed round its whole cell, with
Android's widget corners. A folder's or menu's whole sheet, when it takes focus, gets no ring:
the next press moves into its items. Closing a menu with B gives focus back to the item it was
opened from (Android would otherwise give it to the first thing in the window: a folder's name,
opening the keyboard, or the drawer's Private Space). Launcher3's own
keyboard-focus rectangle (drawn round the item's whole box) and Android's default highlight are
off while controller navigation is on, so there is only ever one indicator. Home screen icons
and folders, and the drawer's folder tiles, also lift on a spring (scale + elevation), each
growing away from the drawer's edges so nothing is cut off; a folder tile's emoji cover reacts as
the ring reaches it, as it does to a press. All settle back the moment the screen is touched. Select opens the
notifications through the system (the launcher may not expand the panel itself). The PS / Xbox /
Guide button in an app that does not use it goes home: Android asks the system about a key the
app left unhandled (`interceptUnhandledKey`), and a game that uses the button keeps it.

#### Gaming launcher with a controller

*Home screen → Gaming launcher with a controller* (off by default) and *Gaming launcher* (any
installed home app). While a game controller is connected, by USB or Bluetooth, Android's home
app is the gaming launcher: the module changes the home choice itself (the HOME role, from
system_server), as picking a home app in Android's settings does, so the home button, the home
gesture (Launcher3's Quickstep follows the default home and hands over to it) and everything
else that opens home go there. The controller's own home button (PS, Xbox, Guide:
`KEYCODE_BUTTON_MODE`, which Android passes to the app in front) opens home too.

When the last controller disconnects, or the switch is turned off, the home app it replaced
comes back, only if the gaming launcher is still home (a home app picked meanwhile stays). The
replaced app is kept in a global setting (`nlplus_home_before_controller`), so a restart with
no controller connected puts it back as well. A controller counts when it is an external,
non-virtual input device with a gamepad or joystick source; changes wait 1.5 s to settle, so a
Bluetooth controller dropping and coming back does not switch home twice.

#### Typing with a controller (HeliBoard)

For the best experience use **[HeliBoard](https://github.com/Helium314/HeliBoard)**
(`helium314.keyboard`, open source; tick it in the module's LSPosed scope; switch: *Home
screen → Controller typing in HeliBoard*, on by default). With the keyboard up, the controller types on it the way a PlayStation's on-screen keyboard works:

| Button | Keyboard |
| --- | --- |
| D-pad / left stick | Move the highlight over everything HeliBoard shows: keys, suggestions, the toolbar (open or closed) and pinned keys, the emoji pages with their categories and bottom row, the clipboard and its buttons, the one-handed mode buttons, and the panels that open over the keys. Rows wrap left and right; lists and the toolbar scroll as it moves |
| Cross (A) | Touch what is highlighted, for as long as it is held: a tap types or picks; held, it does what a held finger does (backspace and arrows repeat; a key opens its accents / long-press keys, a suggestion opens more suggestions, an emoji its skin tones, a toolbar key pins). While held with a panel open, the d-pad slides over the panel and letting go picks |
| Square (X) | Backspace (held, it repeats) |
| Triangle (Y) | Space |
| L1 / R1 | Move the text cursor left / right |
| L2 | Shift (twice quickly: caps lock) |
| R2 | Done (the keyboard's enter / action key) |
| Circle (B) | Back: close an open panel, else from emoji / clipboard back to the letters, else close the keyboard |

Cross is a finger: a touch is given to HeliBoard's window at the highlight (down when Cross
goes down, up when it comes up), so HeliBoard's own code handles every press, repeat,
long-press, popup and pick exactly as with touch. The highlight is drawn in that window's
overlay, above all of HeliBoard's views, and stays on what it is on when the keys change
(shift, symbols, emoji), or moves to what is now nearest. Only controller buttons are taken,
and only while the keyboard is up: a hardware keyboard's arrows stay HeliBoard's, and with the
keyboard closed every button goes to the app. The hooks use HeliBoard's own names, kept in its
release builds (`-dontobfuscate`), and were checked against HeliBoard 4.2's release APK.

</details>

<details>
<summary><b>Sound</b> · Nothing's breathing sounds as background music, interface sounds</summary>

Both off by default; switch them on in the module's **Sound** page.

- **Background music** built on Nothing's own meditation widgets: each style plays that widget's
  breathing loop, read from Nothing Widgets on the phone, and layers sound on it that breathes
  with it (following the loop's own loudness), in the loop's key (A minor pentatonic,
  A = 440 Hz) so nothing clashes. The layers follow measurements of the PS4 and PS2 menu music:
  open chord swells under a filter that opens on the in-breath, a low sub, single ringing bells
  near the top of each breath (some gliding down), air that opens and closes, a deep rumble, a
  small fluttering cluster that flares every 15 to 30 s, and rare glints, all in a soft hall.
  Almost nothing above 2 kHz.
  - *Calm* (the default): Calm's loop under PS4-style chord swells and bells.
  - *Focus*: Focus's box breathing under PS2-style air, rumble and flutter.
  - *Relax*: Relax's long, slow breaths under one deep chord per breath and rare chimes.
- Without Nothing Widgets the layers still play, breathing at the style's pace.
- Tapping an app lets the music recede at once: over 1.6 s it fades and closes its filter, like a
  console dimming its menu as a game starts. A new style fades the old one out first.
- It plays only while the home screen or drawer is in front, fades in and out, never takes audio
  focus (so it can't pause your music), and goes quiet while another app plays media or a call
  is active. It runs on one audio thread at 24 kHz; the loop is decoded once, off that thread.
- **Interface sounds**: console-style chimes for opening and closing the drawer and folders,
  launching apps, turning pages, menus and controller focus, in tune with the music. Silent on
  silent or vibrate.
- **Preview** the selected style in the module; music volume has its own slider.

</details>

<details>
<summary><b>Live wallpapers</b> · Living Worlds and Palette Cycle: sounds, turning, framing</summary>

#### Living Worlds sounds on the home screen

*Home screen → Living Worlds sounds* (on by default; Living Worlds in the module's LSPosed
scope). Living Worlds 2.12.74 plays each of its 12 worlds' ambient sounds in the app only: its
live wallpaper's JavaScript runs with fixed settings that have no *Ambient Sounds*
(`Storage.wallpaperItems`), so it asks for none, and the wallpaper's message handler
(`MyLWPService$JSInterface.postMessage`) only handles `setSceneKey`. The module turns Ambient
Sounds on in the wallpaper's JavaScript (as the app's switch does, again every few seconds since
a world change reloads it) and hands its `playSound` messages to a `SoundManager` of the app's
own. The sounds therefore come from the app's own sound code for the world and time the
wallpaper draws, with its fades and volumes, as in the app.

They play only while the wallpaper is seen on the home screen or in the app drawer: they stop
when an app covers it, on the lock screen until the phone is unlocked, with the screen off, and
when the wallpaper is replaced; the wallpaper picker's preview is silent. With another wallpaper
set no Living Worlds engine runs, so nothing plays. Should two engines run at once, only the
last shown is heard.

#### Living Worlds smooth turning

*Home screen → Living Worlds smooth turning* (on by default). Living Worlds' default wallpaper
drawing ("Alt wallpaper tech", from Android 8) shows its page in a WebView on a virtual display
the size of the wallpaper's surface, and at every surface change (each turn)
`createPresentation` tore both down and built them again: the page reloaded and started over.
Even resized in place, the page lays itself out again a few frames after its display changed
size, so each turn first showed the old layout in the new shape (DebuG15's video: a black side
going to landscape, the bottom half black for about half a second coming back).

So the page no longer changes size at all. It is given a display of exactly its scene's size
(345×455 at density 160: one page pixel per display pixel, so Living Worlds' own `App.resize`
fits the scene at scale 1), and the module draws its frames on the wallpaper's surface with
`App.resize`'s own framing worked out for the surface: zoomed to fill ("Zoom to fill screen",
Living Worlds' switch) or fitted, the same sideways anchor it gives February, July and
December, and pixels kept sharp as its `image-rendering: pixelated` keeps them (bitmap
filtering off: a new Paint filters by default since Android 9). A turn draws
the last frame again at once for the new size, before Android shows the wallpaper: the first
frame in landscape is already the landscape picture, filling the screen, with no black and no
reload. The page draws about 16× fewer pixels than at screen size; scaling it up is one GPU
draw. Should a phone be unable to draw the page's frames, the log says so and Living Worlds'
own drawing is used. With the switch off, Living Worlds draws as it always has.

*Home screen → Living Worlds in landscape* (with smooth turning on): a slider from the top of
the scene (sky) to the bottom (water, ground), 50 by default, which is Living Worlds' own
centring. Every world ships only as a 345×455 crop of the desktop program's 640×480 art (the
`*.LBM.cropped.json` scenes; the full pictures are not in the app), so filling a landscape
screen's width takes about 7× the scene, which shows a third of its height; showing more would
leave bars at the sides, as the art is no wider.

#### Palette Cycle downloads

Palette Cycle (`rak.pixellwp`, F-Droid 2.3.1; in the module's LSPosed scope) shows Mark
Ferrari's full 640×480 worlds, downloaded on the phone from effectgames.com. Its addresses
(`http://…/scene.php`, `http://…/image.php`) now answer with a short "moved" notice, which it
cannot read, so every download failed. Its author fixed this on 2026-10-31 (commit `b88f93a`,
not yet released); the module applies the same change to the installed app: the current
`https://www.effectgames.com/demos/worlds/cache/…js` and `…/canvascycle/images/….LBM.json`
addresses, read as they are now served.

#### Palette Cycle sounds

*Home screen → Palette Cycle sounds* (on by default). Palette Cycle has no sound; the two web
demos its pictures come from give them looping ambient sounds, played the same way by both
(`main.js`: `audio/{sound}.mp3`, looping, 2 s fade in to the scene's volume, 0.5 by default,
half a second out):

- its 22 worlds (time of day and weather) take the Living Worlds demo's (`demos/worlds/
  scenes.js`; March has none there);
- its 48 single pictures: 35 take the Canvas Cycle demo's (`demos/canvascycle/scenes.js`;
  V01 writes `maxVolumd`, which the demo does not read, so it plays at 0.5); V05AM, V25 and
  V26 are not in that list but are the same paintings the Living Worlds demo gives V13,
  V25HEAT and V13; V05PM, V25PM, V26PM and V26NIGHT, the same paintings at other times, are
  given those (not from either demo); V12, V18 and V21–V24 have no sound in either demo and
  stay silent.

The module plays the sound of what Palette Cycle shows (its `changeImage`), downloaded on the
phone once into Palette Cycle's cache. Like Living Worlds' sounds it plays only while Nothing
Launcher (home, app drawer, recents) is on screen: the launcher says so (its `onResume` /
`onPause`, broadcast to both wallpaper apps), as a wallpaper engine's own visibility is not told
reliably in every case and sounds went on over apps. Each loops with no gap (`SeamlessLoop`): replayed as a
file, an MP3 stops, plays its padding and trailing silence and starts again; here it is decoded
once, the silence at both ends cut, its last 0.3 s cross-faded into its start, and played by an
`AudioTrack` looping over its own buffer, end into start sample for sample. It is and heard only while the wallpaper shows on the home
screen or in the app drawer, as Living Worlds' sounds are.

#### Palette Cycle framing and cycling

- *Home screen → Palette Cycle in landscape*: a slider from the top of the picture (sky) to
  the bottom (water, ground), 50 by default. Palette Cycle keeps one pan for both
  orientations, so landscape showed the portrait pan's height, usually the top. The window it
  draws (`getOffsetImage`) is moved to the slider's height as it is drawn; its own pan and
  settings are left as they are, and portrait is its own.
- Monolith Plains and Winter Manor (`VW3BASIC`, `V12BASIC`) are only 345×455 scenes on a
  640×480 sheet in the web demo ("missing 640x480 LBM for december", its `scenes.js`): the
  rest is flat beige and black filler. The window is kept inside the real scene, zooming in as
  little as needed, so the filler is never drawn.
- Palette Cycle 2.3.1 works its colour cycles' time in single-precision floats, which lose
  steps after a few hours of running, so the colours stutter and stop. Its author moved it to
  doubles on 2026-10-31 (commit `8fbddbb`, unreleased); the module does the same
  (`Cycle.getCycleAmount`).

</details>

<details>
<summary><b>System</b> · system font, full refresh rate in battery saver</summary>

#### System font

Choose any of Nothing's fonts already on the phone (NDot, NType, and the
  ones inside Nothing's widgets app), each previewed in the chooser on a dot-matrix screen
  made for it, in its own letters. It is applied the way Android applies its own fonts: with
  System Framework in the module's LSPosed scope, the font map system_server gives every app
  is rewritten with the chosen font as soon as you pick it, so every app opened from then on
  uses it, no reboot; System UI and the launcher are restarted at once. A font only the
  widgets app has is first added to the system's fonts as a file (one restart). If the system
  ever crashes while a new font is in use, the next start leaves it off. Phone default
  undoes it.

#### Full refresh rate in battery saver

*System → Full refresh rate in battery saver* (off by default). Android's display mode director
caps the refresh rate while battery saver is on (`DisplayModeDirector$SettingsObserver`: with
`low_power` set, a vote at the low-power-mode priority for 0..60 Hz, and one for the display's
low-power modes where it lists them). With the setting on, those two votes are not cast: the
screen keeps the refresh rate set in Display settings (120 Hz), and everything else battery
saver does stays. Turning it on or off applies at once.

#### Battery saver switched by other apps

Android decides a tap of the battery saver tile or switch from a state it keeps beside the saver
(off, on by you, on automatically). An app that switches battery saver by writing its setting
(`settings put global low_power …`, as EnforceDoze does around screen off and on) changes the
saver but not that state, so after it turned saver off, every tap was refused as "already on"
until a reboot. The module makes the state agree whenever the setting changes the saver; Android's
own switching is untouched.

</details>

<details>
<summary><b>Performance (CMF Phone 1 only, experimental, root)</b> · Ndot overlay, PerfMTK, 120 fps unlock, Game Bar</summary>

A **Performance** page appears in the module's menu on a CMF Phone 1 (`ro.product.device`
Tetris) and nowhere else. Everything on it was built from what this phone reports (its
clock, GPU and thermal files, Android's display and game services, PerfMTK's own files).

- **Ndot performance overlay**: a small panel over every app and the lock screen, updated
  twice a second,
  in MangoHud's style (its row colours and a frame time graph) with
  Scene's contents and size, drawn by the module (it is
  not MangoHud's code: MangoHud is a Linux Vulkan / OpenGL layer drawn inside the game, which
  Android only loads into debuggable apps):
  - CPU and GPU load rings with their clocks;
  - FPS and frame time in any app (the focused window found on whichever display has one), read
    four times a second on their own: the frames presented in the last half second up to now
    (MangoHud's default sampling period), and each new frame's time on a Nothing dot
    screen that scrolls as frames come (an empty column for a read with none, or a rest of
    200 ms or more) (SurfaceFlinger's present times for the focused window's layer: its
    SurfaceView in a game or video, else the window itself; with no focused window, as on the
    lock screen, System UI's; a still screen falls to 0);
  - GPU load, clock and temperature; CPU load and hottest core;
  - each cluster's set clock, then every core's load and real clock, as Scene lists them
    ("off" when a core is parked). On this phone every clock file reads the cluster's set
    clock (PerfMTK's limit), so a core's real clock is measured as Scene does: its CPU cycles
    over half a second (`simpleperf stat -a --per-core -e cpu-cycles`), else the cluster's;
  - RAM and swap use, DDR clock;
  - SoC temperature, battery temperature and the power drawn from the battery, each on its
    own row.

  Sensors that report a value below −40 °C are off: a powered-down GPU reports −274 °C, so
  the GPU's last real temperature is shown, marked "idle"; other sensors show "—". The files are root-only, so the module app reads them in one root shell and
  sends each reading to System UI, which draws the panel: Android hides an app's overlay on
  the lock screen (its layer, 111000, is below the lock screen's, 171000, and the keyguard
  policy hides windows below it), while a System UI panel window above it shows, as Nothing's
  fingerprint overlay does. Drag the panel to move it; it stays where it is left and takes
  only the touches on itself. No "Display over other apps" permission is needed. After a
  reboot it comes back when the Performance page is opened.
- **PerfMTK profile**: switches [PerfMTK](https://github.com/JUANIMAN/PerfMTK) to Performance,
  Balanced, Powersave or Powersave+ (`perfmtk <profile>`). PerfMTK's own per-app profiles
  still switch it as apps change. PerfMTK also sets the refresh rate with each profile, so
  the module leaves the refresh rate to it.
- **Thermal limits**: PerfMTK's thermal throttling on or off (`perfmtk --thermal
  enable|disable`, from its own help). Off runs hotter: for short sessions.
- **120 fps unlock (per game, off by default)**: some games pick their frame rate from a list
  of known phones (the reports showed two Unreal games asking the display for 60 Hz
  themselves, while Android, MediaTek and PerfMTK all allowed 120). Inside each game you pick,
  and nowhere else, the phone presents itself as a Samsung Galaxy S24 Ultra (Snapdragon 8
  Gen 3): Android's `Build` fields and the matching `ro.product.*` / `ro.soc.*` properties
  read through Java. The hardware and board names stay the phone's own. Tick each game in
  the module's LSPosed scope too, then restart the game. **Use it only for offline or
  single-player games:** games with online play or anti-cheat can treat a changed device as
  cheating and ban the account, and nothing can rule that out. It does not help games that
  choose by their graphics chip, which the driver still reports as Mali.

- **In Nothing's Game Bar** (tick *Game Bar* / `com.nothing.ntgamedashboard` in the module's
  LSPosed scope): three buttons after Nothing's own in its settings grid: **Perf overlay**
  (each tap on or off), **PerfMTK** profile (each tap the next: performance, balanced,
  powersave; PerfMTK's own per-app profiles still switch it as apps change) and **Thermal**
  limits on / off, showing its state. The Game Bar's grid is a fixed list of setting keys
  that it binds by name and leaves empty for a key it does not know; the module adds its keys,
  fills their rows. The overlay is started by the Game Bar itself: Android lets the system
  start a foreground service for an app in the background, not that app itself (asked from
  the module's own receiver it crashed with `ForegroundServiceStartNotAllowedException`). For
  the PerfMTK buttons it asks the module app, which has root and only listens to the system,
  which the Game Bar runs as.

Planned next: refresh rate options through PerfMTK's profiles (`--reload`).

</details>

<details>
<summary><b>Diagnostics and debug report</b> · what to send when something is wrong</summary>

#### Debug report

In the module's app, Diagnostics →
**Capture debug report** (root). Choose how long it waits first (10 s to 10 min: time to
set the problem up, such as locking the phone, turning it or opening the screen that
misbehaves) and how long it captures (15 s to 10 min). Meanwhile it records the screen
and, every few seconds, takes a screenshot, the screen composer's layers, the windows and
the view layout of every visible window (System UI's included) and how System UI draws
what is behind the lock screen, PIN page and shade (scrims and blur) and the busiest
processes (`top`); each time the screen wakes it records the rotation and its sensor a
second and three seconds after (and on the always-on display); logcat runs throughout. At the end it adds the memory of the module's
app, Nothing Launcher and System UI (`dumpsys meminfo`).
It adds System UI's state, input, display, the system settings, the game service, battery,
where the phone keeps its CPU / GPU clocks and temperatures, Nothing's Game Bar app, the
PerfMTK module's scripts, settings and `--help` (if installed), SurfaceFlinger and the
composer libraries it has loaded and an 8-second SurfaceFlinger layer trace (Perfetto;
for the landscape fingerprint) and the LSPosed log, and saves one
`NLPlus-report-….tar.gz` in Download; a notification says when it is ready. While it
runs you can tell what it is doing: one buzz when capturing starts and two when the
report is saved (felt on the lock screen too), an ongoing notification counting down
("Capture starts in…", "Capturing…", "Saving the report…"), and a small "● REC" pill
with the seconds left, drawn by System UI, so it shows on the lock screen too. **Stop
capture** ends it early and still saves it. It can hold personal information
(screenshots, the recording, notifications, logs), so look through it before posting it
publicly.

#### Diagnostic logging

The **Diagnostic logging** switch on the same page (off by default; turn it on
before capturing, it applies within a minute without a reboot, and off again after: it
logs every lock screen layout pass) writes the module's diagnostic lines to the LSPosed
log: orientation changes, rotation sensor reports, lock screen layout, the always-on
display's position, fingerprint events and portrait-only system screens.

#### Test notifications

**Test notifications** on the same page posts 1, 3, 6 or 12 sample notifications (short,
long, conversation, progress, picture and reminder), now or in 10 seconds so the phone can
be locked first, to check how the lock screen lays them out; **Clear test notifications**
removes them. It asks for the notification permission the first time.

</details>

## Roadmap

- **Nothing Dot Matrix Display**: a new engine for the emoji covers, which are a beta until
  it lands. It is planned as:
  - **Built for Nothing Launcher:** it works directly with Nothing Launcher.
  - **Very low power:** heavily optimized to render every emoji with as little power as
    possible.
  - **Understands each emoji:** its emotion, reflections, particles and 3D shape.
  - **Every emoji reworked one by one:** each one rendered in 3D, with several animations made
    for that emoji alone.

## For developers

<details>
<summary><b>Code layout</b> · packages by where the code runs</summary>

The module's code is grouped by where it runs:

| Package | What it is |
| --- | --- |
| `xposed` | The entry point (`ModuleEntry`, named in `assets/xposed_init`), hook helpers (`Hooks`: installing a feature so its failure is its own, guarding code the apps call back into), the settings as hooked apps read them (`ConfigStore`) and Nothing's NDot font (`NdotFont`). |
| `launcher` | Everything in Nothing Launcher: the Smart drawer's folders and covers, the folder menu, landscape, controllers, sounds, dot-matrix icons (`DotIcons`, `IconScreen`) and scrolling names. |
| `systemui` | The status bar and quick settings: `Clock`, `Battery`, `GreenIcons`, `GreenTexts`, `QuickSettings`, `NetworkSpeed`, all in the lit look of `Led`; the second status bar row (`DualStatusBar.kt`) and the OLED saver (`StatusBarSaver.kt`), with their shared System UI crash-loop guard (`CrashGuard`). |
| `perf` | CMF Phone 1 only: the device check and root helper (`Device`), the Ndot performance overlay (`OverlayService` reads, `PerfHud` draws) and the per-game 120 fps unlock (`GameUnlock`), the Game Bar's buttons (`GameBar`, in the Game Bar; `GameBarReceiver`, in the module app). |
| `system` | The system font, set in the font map system_server gives every app (`SystemFonts`), the gaming launcher while a controller is connected (`ControllerHome`), Settings screens opened on a feature's behalf (`SystemShortcuts`) and a probe writing to the LSPosed log which window decides the screen's orientation each time it changes (`OrientationProbe`, `orientation: requested … by …`, naming the activity or window at the end of the chain). |
| `widgets` | Nothing's widget apps in landscape. |
| `keyboard` | Typing in HeliBoard with a game controller (`HeliBoardGamepad`). |
| `wallpaper` | Living Worlds' live wallpaper: its sounds (`LivingWorlds`) and its framing and turns (`LivingWorldsView`); Palette Cycle's downloads and sounds (`PaletteCycle`, `PaletteCycleSounds`), its framing and cycling (`PaletteCycleView`). |
| `settings` | Developer options → Running services as a task manager, in Settings (`TaskManager`, `TaskHeader`, `TaskStats`). |
| `anim`, `sound` | The emoji animations and dot-matrix screens, and the music and interface sounds. |
| `ui` | The module's settings app: a menu of pages (App drawer, Home screen, Sound, Status bar & quick settings, Lock screen, System font, Backup, Diagnostics). |

**Languages:** the module is Java, and new features are written in **Kotlin** (from 0.53.0
on; the build has both). An existing Java file becomes Kotlin only when it is reworked anyway,
and is tested again then; files that work stay as they are. Kotlin's null safety suits hook
code, where a class, field or view may be missing on another Nothing OS version.

Code the module hands to an app (a posted runnable, a listener, a view or drawable it draws)
runs as that app's own, so it is guarded: a fault costs that one feature, never the app. A
font for the system font map must be a real font file every app can open, or every app
drawing text with it would crash.

</details>

<details>
<summary><b>How it works</b> · reverse-engineering notes, Nothing Launcher 4.0.20</summary>

The launcher is built on AOSP Launcher3 (`com.android.launcher3.*`, unobfuscated). Nothing's own
code is in `com.nothing.launcher.*`, and R8 has obfuscated most of it.

| Role | Class in 4.0.20 | How the module finds it |
| --- | --- | --- |
| Per-app category state (`AppStatusInfo`) | `x7.c` | Type of `ItemInfo.mStatusInfo` |
| Group category getter (`"unknown"` → `"others"`) | `x7.c.n()` | Behavioural probe on a fresh instance |
| Smart drawer list (groups apps, builds folders) | `com.nothing.launcher.allapps.smart.h` | `AlphabeticalAppsList` subclass in the smart package |
| Category order (`OrderEvent.lastOrder` or default) | `h.e()` | Only private no-arg method returning `List` |
| Default categories + label string IDs | `com.nothing.launcher.allapps.smart.i` | Not hooked |
| Folder tile | `SmartFolderIcon.bind(GroupAdapterItem)` | Name kept |
| Opened folder sheet | `SmartFullSheet.Companion.b(ActivityContext, SmartFolderIcon)` | Signature match |
| Drawer settings screen | `AllAppSettingsFragment.onCreatePreferences` (`all_app_settings_preferences.xml`) | Name kept |
| Group item factory (`GroupAdapterItem.Companion.create`) | `smart.e$b$a.a(int, String, List)` | Signature match |
| Smart grid decoration | `smart.c` | Type of `NTLauncherAllAppsContainerView.gridDirection` |
| Grid spans | `AllAppsGridAdapter$GridSpanSizer.getSpanSize` | Name kept |
| Drawer background | `AllAppsState.getWorkspaceScrimColor` | Name kept |
| Drawer → home folder | `OptionsPopupView.show`, `LauncherModel.addAndBindAddedWorkspaceItems`, `ModelWriter.addItemToDatabase` | Names kept |
| Nothing DeviceProfile | `v6.h` (overrides sizing; base methods made abstract) | Parameter type of the Smart profile's `n(Context, …)` |
| Home rotation | `RotationHelper.notifyChange` / `mHomeRotationEnabled` | Names kept |
| Smart drawer profile | `smart.z.n(Context, v6.h)` | Type of `SmartFolderContainer.smartProfile` |
| Landscape grid | `CellPosMapper`, `Workspace.insertNewWorkspaceScreen`, `DeviceProfile.getCellSize`, `ModelWriter.updateItemInfoProps`, `Launcher.bindInflatedItems`/`finishBindingItems`, `CellLayout.markCellsAsOccupiedForView`/`isOccupied`, `ModelWriter.deleteItemFromDatabase` (reason text "Collision while binding") | Names kept |
| Controller keys | `Launcher.dispatchKeyEvent` | Name kept |
| Display style pref | `pref_all_app_display_style` (false = Smart) | — |

`h.updateAdapterItems()` groups `mApps` by `mStatusInfo.n()` and adds one folder for each
category key in `h.e()`. Keys that aren't in that list never appear. The module therefore:

1. Makes `n()` return `nlplus:<folderId>` for apps the user assigned. It finds the app's
   component through the status info's intent URI field. The "hidden icons" entry is left alone.
2. Adds those keys to the list returned by `h.e()`.
3. Replaces the label in `SmartFolderIcon.bind` and in the full-sheet title. The launcher looks
   labels up from a fixed `category → string resource` map, so a custom key would otherwise show
   as "Others".
4. Calls LSPosed's `XposedBridge.deoptimizeMethod` on `updateAdapterItems` and on the adapter's
   `onBindViewHolder`. Without this, ART could inline the small hooked getters and skip the hooks.

The module never writes custom categories to the launcher's database. Nothing's category
assignments stay untouched, so disabling the module restores the stock drawer.

The config is stored as world-readable shared prefs (`config.xml`) through LSPosed's
`xposedsharedprefs`. The hook reads it with `XSharedPreferences`.

</details>

## Licence

© 2026 Dr.Essence, under [CC BY-NC-SA 4.0](LICENSE): use, copy, change and
share it, as long as you credit Dr.Essence, do not use it commercially
(including selling it or anything built from it), and share changed versions
under the same licence. Nothing's fonts and other Nothing assets are read from the
phone at runtime and are not part of this work.


## Credits

- **[Dr.Essence](https://github.com/Dr-Essence)**: author and maintainer. Ideas, design decisions, and every test on a real phone (logs, recordings, measurements) that the work here rests on.
- Code written with [Claude Code](https://claude.com/claude-code).

### Thank you, Nothing

This project exists because [Nothing](https://nothing.tech) builds phones that can be opened up:
owners may unlock the bootloader, and Nothing publishes its phones' kernel and device sources
([NothingOSS](https://github.com/NothingOSS) on GitHub). And Nothing OS itself, its dot-matrix
design and its NDot font, is what every feature here sets out to extend rather than replace. Nothing2Something is an independent project, not made or endorsed by Nothing.

The tools this is built on: [LSPosed](https://github.com/LSPosed/LSPosed) (hooking framework)
and [Magisk](https://github.com/topjohnwu/Magisk) (root).
