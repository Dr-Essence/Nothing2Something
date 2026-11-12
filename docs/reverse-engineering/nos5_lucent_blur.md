# Nothing OS 5's translucent ("Lucent") surfaces

Found by comparing the Phone (2a)'s Nothing OS 5.0 (Pacman_C5.0-261015-1824, Android 17) with
its Nothing OS 4.1 (Pacman_B4.1-260928-1157, Android 16), both from the firmware workflow
(`.github/workflows/firmware.yml`). The resources were read with `aapt2 dump resources`, the
code references with the dex string pools. Nothing's code is not copied into the module: only
these measurements, applied with Android's own blur APIs.

## What Nothing OS 5 adds

The resources are a design system Nothing calls **Lucent** (`lucent_fallback_*` colours,
`color_surface_translucent_*`, `ref_blur_*`). NOS 4.1 has none of them (System UI: 25
blur-related resources in 4.1, 63 in 5.0; Launcher: 14 and 50).

Blur steps (`ref_blur_*`, plain numbers): soft 8, default 16, medium 24, strong 32,
frosted 128.

Translucent surfaces (neutral colour with alpha): darken subtle 4 %, default 16 %, strong 40 %
(black); lighten subtle 8 %, default 16 %, strong 40 % (white); border 4 %; overlay gradient
60 %. Frosted backdrop `#66FFFFFF` (light) / `#66000000` (dark), 40 %. Launcher's arrange
glass fill `#33FFFFFF` (20 %).

| Surface | Blur radius | App |
|---|---|---|
| Notifications (background) | 25 dp | System UI |
| Volume dialog surface | 23 dp | System UI |
| Power menu (global actions) | 34 dp, `config_translucentStandalonePowerMenu` true | System UI |
| Bottom sheets | 30 dp | System UI |
| Lock screen shortcuts | 15 dp | System UI |
| Fingerprint icon | 15 dp | System UI |
| Shade window (max) | 34 dp; content up to `max_window_blur_radius` 23 px | System UI |
| Folders | 20 dp (`FolderBlurStyle`, with `FolderBlurFallbackStyle`) | Launcher |
| Pop-up menus | 20 dp | Launcher |
| Home organizer | 30 dp | Launcher |
| Recents depth blur | up to 30 dp (`max_depth_blur_radius_enhanced`) | Launcher |
| App drawer, Recents actions, widget picker, drop targets | styles `AllAppsBlurStyle`, `OverviewBlurStyle`, `widget_picker_blur_*`, `nt_drop_target_blur_clip_background` | Launcher |

Every surface has a **fallback** colour (`*_blur_fallback`, `lucent_fallback_*`): what Nothing
draws when the phone cannot blur.

## How it is drawn

The APIs it calls all exist on Android 16 (NOS 4.1), so the effect can run there:

- `ViewRootImpl.createBackgroundBlurDrawable` → `BackgroundBlurDrawable` (a view's background
  blurring what is behind its window), Android 12+, hidden API.
- `Window.setBackgroundBlurRadius`, `WindowManager.LayoutParams.setBlurBehindRadius`
  (Android 12+).
- `View.setRenderEffect(RenderEffect.createBlurEffect(...))` (Android 12+).
- `CrossWindowBlurListeners` / `isCrossWindowBlurEnabled`: whether the device allows blur
  now (off with battery saver, or when `ro.surface_flinger.supports_background_blur` is 0).

Nothing's own classes, new in 5.0 System UI: `com.nothing.widget.ui.blur.BackdropBlurController`,
`NtBlurTarget`, `com.nothing.cardhost.frosted.FrostedBlurUtils`, `BackdropBlurSource`,
`com.nothing.compose.ui.NtBackdropBlurState`, `com.nothing.systemui.wallpaper.NTWallpaperBlurController`.
The 5.0 Launcher uses the Android APIs directly (no Nothing blur classes).

## Correction: what Nothing OS 5's Launcher really draws (0.70.28)

The Launcher rows of the table above come from resources. Reading the code shows most of them
never run: `com.android.launcher3.Flags.blurOnMoreSurfaces()` returns a constant false in the
5.0 Launcher, so Launcher3's folder, pop-up, drawer and Recents blur (`QuickstepBackgroundBlurHelper`,
`folder_blur_radius` 20 dp, `popup_blur_radius`, `AllAppsBlurStyle`, `OverviewBlurStyle`) is
off. Nothing's glass is its own **frosted backdrop** (`com.nothing.commBase.frosted`):

- `com.nothing.cardhost.frosted.FrostedBackdropProvider` reads the wallpaper
  (`WallpaperManager`; the 4.1 Launcher holds `READ_WALLPAPER_INTERNAL` too), makes it 3 times
  smaller (`createScaledBitmap`, filtered), blurs it with a box blur of radius 3 along rows then
  columns (`i7.l.a/b/c`) on a background thread, and rebuilds it when the wallpaper changes.
  It is on when `Settings.Secure nt_theme_style` is 2 (Nothing OS 5's theme style).
- `FrostedBackdropConfig` defaults: downscaleFactor 3, blurRadius 3.0, saturationFactor 1.15,
  tint `container_frosted_backdrop` (Lucent: `#66FFFFFF` light, `#66000000` dark).
- `FrostedBackdropDrawable` draws its piece through a BitmapShader (CLAMP) placed with
  scale = 3 x wallpaperScale / contentScale and
  translate = ((screenW/2)(1 - wallpaperScale) - offsetX x wallpaperScale - x,
  (screenH/2)(1 - wallpaperScale) - (max(fullH - screenH, 0)/2 + offsetY) x wallpaperScale - y)
  / contentScale, where (x, y) is the surface's place on screen (`getLocationOnScreen`), offsetX
  the wallpaper parallax (`Workspace.syncFrostedBackdropOffsets`:
  `WallpaperOffsetInterpolator.wallpaperOffsetForScroll(scrollX) x max(fullW - width, 0)`) and
  contentScale the workspace scale (`syncFrostedBackdropScale`). Colour filter
  (`Companion.buildColorFilter`): saturation 1.15, then out = in (1 - a) + tint x a. No picture:
  the tint alone ("tint only"). A new picture fades in over 300 ms.

Frosted surfaces in the 5.0 Launcher (every place a `FrostedBackdropDrawable` is made or
placed): the open folder's background (`folder.l`: frosted instead of its solid colour), folder
icons (`folder.PreviewBackground.refreshFrostedBackdrop`), a folder being dragged or opening
(`DragView`/`ClipIconView.bindFolderFrostedLayer`), the dock (`Hotseat.updateFrostedBackdropOffset`),
the dock's search bar (`layout/nt_universal_search_bar_in_hotseat`, an `NTFrostedRelativeLayout`)
and Nothing's widgets (`FrostedCardWidgetHostView`). Pop-up menus, the drawer and Recents are
not frosted.

## Built so far

- 0.70.27: glass folders with Launcher3's blur (not what Nothing OS 5 runs; replaced).
- 0.70.28: frosted folders with Nothing's recipe (`launcher/Frosted.kt`, `launcher/FolderGlass.kt`), with a tint slider.
- 0.70.29: live wallpapers captured as Nothing OS 5 does (`setup.d0`: mirrorWallpaperSurface + captureLayers), closed folders and the dock's search bar (`launcher/FrostedSurfaces.kt`). To do: the dock, System UI.
- 0.70.30: Nothing's widgets: in the 5.0 card apps (Card Lab etc., `tools/firmware-inspect.sh`) the root container of almost every widget is an `NTFrosted*` layout, frosted by `FrostedCardWidgetHostView.onParseFinished`; on 4.1 the card's root background gets the glass at `CardWidgetHostView.onParseFinished`.

## Still to find before building it

- Whether the CMF Phone 1 allows blur at all. Its own NOS 4.1 System UI and Launcher
  (Tetris_B4.1-260928-1140) decide with `com.android.systemui.shared.system.BlurUtils
  .supportsBlursOnWindows()`: `CrossWindowBlurListeners.CROSS_WINDOW_BLUR_SUPPORTED` (from
  `ro.surface_flinger.supports_background_blur`) and `ActivityManager.isHighEndGfx()` and not
  `persist.sysui.disableBlur`. The phone reports `supports_background_blur` 1 and
  `ro.sf.blurs_are_expensive` 1 (Dr.Essence, 12 Nov 2026); the second is read by no Java code in
  framework.jar, services.jar, System UI or Launcher, only by SurfaceFlinger (it schedules blurred
  frames earlier), so it does not switch blur off. Still to read on the phone:
  `persist.sysui.disableBlur`, `ro.config.low_ram`, the `disable_window_blurs` setting and
  `dumpsys window` (whether system_server's blur is on now).
- Which NOS 4.1 views each surface maps to (the decompiled 4.1 System UI and Launcher), so the
  blur is added where Nothing's own view is created, not drawn over it afterwards.
- The animation and timing of each blur (the 5.0 classes above, decompiled).
