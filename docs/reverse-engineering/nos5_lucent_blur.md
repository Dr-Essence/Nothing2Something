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

## Still to find before building it

- Whether the CMF Phone 1 allows blur at all: `getprop ro.surface_flinger.supports_background_blur`
  and `ro.sf.blurs_are_expensive` from the phone (asked for).
- Which NOS 4.1 views each surface maps to (the decompiled 4.1 System UI and Launcher), so the
  blur is added where Nothing's own view is created, not drawn over it afterwards.
- The animation and timing of each blur (the 5.0 classes above, decompiled).
