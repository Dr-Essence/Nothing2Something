# Nothing Dot Matrix Display — research for the new emoji-cover engine

> **Status (0.70.20):** research for the roadmap's Nothing Dot Matrix Display; nothing built
> yet. First evidence to collect (the debug report now records it): whether Nothing's drawer
> redraws its tiles on every frame (`gfxinfo_start.txt` / `gfxinfo_end.txt`, "Total frames
> rendered", with the drawer open and still), then the renderer (`getprop
> debug.hwui.renderer`) and the emoji font's tables (COLRv1 or CBDT).

Research only; nothing in the module was changed. Every claim is marked:

- **[code]**: read in the decompiled Nothing Launcher / System UI or in the module's source (path given).
- **[phone]**: read from a dump made on the phone that is already in the scratchpad (path given).
- **[proposal]**: a design idea or a platform fact from Android documentation that has **not** been tested on the CMF Phone 1. Each one names the evidence that would settle it.

Paths used below:
- `L` = `(local working file)` (Nothing Launcher, jadx)
- `LR` = `.../scratchpad/out/resources` (the launcher's resources and assets)
- `S` = `(local working file)` (System UI, jadx)
- `M` = `app/src/main/java/com/dressence/nothinglauncherplus`

---

## 0. How the current engine works, and what it costs [code]

`M/anim/DotMatrix.java`, `AnimatedEmojiView.java`, `MotionDriver.java`, `M/launcher/FolderCovers.java`, `SmartDrawer.java:692-711`.

**Setup.** `SmartDrawer.hookFolderTile` hooks `SmartFolderIcon.bind(e.b)` (after the method runs) and calls `FolderCovers.apply`. In dot mode this adds a `DotPanel` (an `AnimatedEmojiView`, which extends `TextView`). It goes in at index 0 of the tile, MATCH_PARENT. The panel sets Nothing's preview container to `INVISIBLE` and makes the folder name's ink transparent. It places itself from the outline and from layout-change listeners on the previews and the name.

**Once per emoji.** `DotMatrix.glyph()` draws the emoji with the default typeface into a 192x192 ARGB_8888 software bitmap (`GLYPH = RENDER*2`). The default typeface falls back to the system emoji font. The bitmap goes into `LruCache GLYPHS(48)`. `rest()` rasterises the resting frame. Effect regions (`Regions`) are analysed lazily. Splits and detaches (`Detach`, `SPLITS`) are several 192x192 bitmaps per emoji, built with `getPixels` on the main thread.

**Per changed frame, per tile, on the main thread:**
1. The glyph is drawn into a shared 96x96 software `Canvas`, with Matrix pose, filtering and sometimes parts, sphere, flame or bend. This is Skia CPU raster.
2. `render.getPixels(9216 ints)`, then `quantize()` 3x3 supersampling into 32x32, then effects (`DotEffect.apply` on `int[1024]`).
3. `Arrays.equals` against the previous frame. If it changed: `frame.setPixels` (34x34), `updateGlow()` (18x18 `setPixels`), then `invalidate()`.

Two mutable bitmaps change, so HWUI has to upload them as textures again on the next draw.

**Per draw (GPU).** `DotMatrix.draw` draws the glass (round rect plus rim) and the unlit pass, which is the tile-sized `ALPHA_8` dot mask. Then come two glow `drawBitmap`s with filtering. Then the lit pass draws the mask again through a nearest-neighbour `BitmapShader` of the frame, clipped to the emoji square. Then the label (`DotLabel`) and the red LED. The mask is shared through `MASKS(8)`.

**Scheduling.** There is one `Choreographer` callback for the whole process (`MotionDriver`):
- It runs at the display rate while any visible cover is moving.
- Effects run at 24 fps (`EFFECT_FPS`).
- Off-screen tiles get a 250 ms poll.
- It sleeps when nothing is visible.
- `requestFrameRate(true)` asks for `FrameRates.target()`, which is up to **120 Hz**, while moving (`AnimatedEmojiView.java:445`).

**Where the cost goes:**
- All rasterisation and analysis is on the UI thread: Skia CPU, `getPixels`, int loops.
- About 9.2k pixel reads plus 1k dot ops per tile per changed frame, at up to 120 Hz, times every moving tile.
- Two texture uploads per changed frame per tile.
- The panel asks for 120 Hz while moving.
- The GPU fills the whole tile area 3-4 times per redraw (glass, mask, glow x2, lit mask).

Each of these is something the new engine can remove.

---

## 1. How Nothing draws its own dot-matrix things

### 1.1 Nothing Launcher [code]

| What | Where | How it draws |
|---|---|---|
| **Home-screen folder emoji cover** (Nothing's own feature) | `L/com/nothing/launcher/folder/CoverView.java` | `AppCompatTextView` with `R.font.noto_emoji_medium`, text size `0.465 x width`, tinted with `p7.e.i(context)`, value from `FolderInfo.coverValue`. Static: no animation, no shader. |
| Emoji picker for that cover | `L/com/nothing/launcher/folder/emoji/EmojiSelectPanelLayout.java`, `customisation/FolderCustomisationView.java`, layouts `LR/res/layout/folder_cover_emoji_item.xml`, `folder_customisation_emoji_recyclerview_item_grid_view.xml` | Monochrome Noto Emoji fonts `noto_emoji_medium.ttf` / `noto_emoji_semibold.ttf` (`LR/res/font/`, `LR/assets/fonts/`). |
| Text avatars | `L/ta/o.java:90-103` | `Canvas.drawText` into a Bitmap with `noto_emoji_semibold` or `Typeface.create("ndot57")`. |
| NDot font in the launcher | `L/com/nothing/launcher/ossupport/utils/NTFontUtils.java:59-73` | `Typeface.create("NDot57", 0)` if `NothingUtils.isSupportNdot(lang)`. It is a system font, not a renderer. |
| Dot widgets hosted *in* the launcher (pedometer, progress dots, page dots) | `L/com/nothing/cardwidget/pedometer/PedometerCalendarView.java:94-106`, `PointsProgressView.java` (`onDraw`: one `drawCircle` per dot), `NTPageIndicatorDot.java:279-308` | Plain `Canvas.drawCircle` per dot. No shader, no bitmap engine. |
| Media-player widget start-up animation | `L/com/nothing/cardwidget/mediaplayer/view/MediaPlayerContainerView.java:97,161-210` | `LottieAnimationView` (Airbnb Lottie is bundled: `L/com/airbnb/lottie/`). |
| AI widgets | `L/h6/c.java:138`, `L/r6/j0.java` | Remote `SurfaceView` (SurfacePackage) with corner radius set on its SurfaceControl. |
| Lottie elsewhere | `LR/res/raw/*.json`, `LR/assets/anim_private_space_guide_*.lottie` | Tutorials and guides only. |

**Search results** over all of `L`:
- `RuntimeShader` appears only in the bundled AOSP `com/android/systemui/surfaceeffects/{ripple,turbulencenoise}` (`L/com/android/systemui/surfaceeffects/ripple/a.java`, `turbulencenoise/b.java`). No Nothing launcher code references them.
- `createRuntimeShaderEffect`: 0 hits.
- `GLSurfaceView`, `android.opengl`, GLES, Vulkan: 0 hits.
- `RenderEffect` appears only in AOSP blur and shadow drawables (`DoubleShadowIconDrawable`, `BlurredBitmapDrawable`).
- `TextureView`: 2 files, AOSP.

**The launcher has no GPU dot-matrix renderer of its own.** Nothing's dot screens in the launcher are drawn with plain Canvas.

### 1.2 System UI [code]

| What | Where | How it draws |
|---|---|---|
| **NDot lock-screen clock** | `S/com/nothing/systemui/shared/clocks/view/NDotClockView.java:323-600` (and `_16B`) | Each digit is a vector drawable (`R.drawable.ndot_0 .. ndot_9`, plus `_light` variants for AOD), turned into a Bitmap **once** (lazy `toBitmap`). `drawClock` draws it with `drawBitmap` + `Matrix` + `PorterDuffColorFilter(SRC_IN)`. `NTClockView.invalidate` runs on time ticks only. Pre-made dot art, coloured at draw time, redrawn once a minute. |
| Glyph Matrix (Phone 3 back panel) | `S/com/nothing/ketchum/GlyphMatrixUtils.java:148-245`, `S/com/nothing/glyph/GlyphMatrixHelper.java` | CPU: scales a bitmap to 25x25, reads pixels with `getPixel`, outputs grey levels 0..2047. Hardware LEDs, not a screen. Not relevant to drawing on the CMF screen. |
| AOSP surface effects | `S/com/android/systemui/surfaceeffects/{shaders/SparkleShader,turbulencenoise/TurbulenceNoiseShader,loadingeffect/LoadingEffect,ripple/RippleShader,glowboxeffect/GlowBoxShader}.java` | **AGSL `RuntimeShader`**, used as a Paint shader or through `RenderEffect.createRuntimeShaderEffect(shader, "in_src")` (`LoadingEffect.java:149`), animated by a `ValueAnimator` that only sets uniforms. `TurbulenceNoiseShader` has `in_gridNum`, `in_pixelDensity` and a "SIMPLEX_NOISE_SPARKLE" type. `SparkleShader` has `in_pixelate`. The AOSP media-player effect is a dot/pixel grid drawn by a runtime shader. |
| Lottie, AVD | many (biometrics, `ScreenDecorations`, QS) | Standard. |

`S/com/android/keyguard/KeyguardPINView.java:73` shows `GLSurfaceView`. This is almost certainly a jadx type-inference artefact: the loop is over `View[][] mViews`. Do not take it as Nothing using GL.

### 1.3 Which Nothing mechanisms the engine should plug into

1. **The NDot clock's model: pre-baked dot art, coloured and placed at draw time, redrawn only when content changes** [code]. This is exactly "as little power as possible". The engine should bake everything it can per emoji: masks, depth, part layers. A frame should then be only placement and colour parameters. The difference from the clock is that the engine applies those parameters in a GPU shader instead of a `ColorFilter`.
2. **AOSP's own AGSL shader effects** [code], shipped in System UI and copied into the launcher APK. A runtime-shader grid driven by uniforms from a `ValueAnimator` is the platform-sanctioned pattern. The engine uses the same API.
3. **`Launcher.pauseExpensiveViewUpdates()` / `resumeExpensiveViewUpdates()`** [code] (`L/com/android/launcher3/Launcher.java:2055,2090`). Nothing calls them during its app-start boost (`L/d6/b.java:59-71`, "NtAppStartBoostListenerAdapter", used by `QuickstepTransitionManager.java:872-876` and `Folder.java:271`) and when the content animator starts (`QuickstepTransitionManager.java:1271`). Inside they pause the page-indicator animations and defer widget updates. Hooking them (after the original method) to pause the engine joins Nothing's own "stop expensive views now" signal instead of guessing transitions.
4. **The app-launch hardware layer** [code]. When an app opens from the drawer, Nothing calls `hiddenPagedView.setLayerType(LAYER_TYPE_HARDWARE)` on `NTHiddenAppsPagedView` for 217 ms (`QuickstepTransitionManager.java:1278-1296`), and `view.setLayerType(2)` on workspace/hotseat views (`:448`). Any `invalidate()` from a tile in that time re-renders the whole layer. Item 3 pauses the engine exactly then.
5. **Nothing's own folder-cover concept** (`CoverView` + monochrome Noto Emoji) [code]. Nothing already treats "a folder shows an emoji" as a feature. The monochrome Noto Emoji outline fonts are in the launcher's own resources (see §4), so the engine can take emoji *shapes* from Nothing's own assets.
6. **LauncherState** (`onStateSetStart`, already hooked in `SmartDrawer.java:126-128`): stop every cover when the state is not ALL_APPS.

---

## 2. The drawer folder tile's drawing path [code]

**Class and layout.**
- `L/com/nothing/launcher/allapps/smart/SmartFolderIcon.java` is a `FrameLayout` inflated from `LR/res/layout/smart_folder_all_app_icon.xml`.
- The layout sets background `@drawable/all_app_icon_container_panel_bg`: a `<shape>` round rect with `nt_all_apps_list_background_radius` and `color_all_apps_content_background_color`.
- Its children are `TextView folder_name` (top) and `SmartFolderContainer folder_icon_container` (bottom).
- The module's `DotPanel` is added at child index 0.

**Creation.**
- `SmartFolderIcon.a.b(activityContext, parent)` (`:44-75`) sets the size from `DeviceProfile.z()` (`i()` width, `c()` height, `e()` preview height, `h()` name height), `setClipToPadding(false)`, and a click listener that opens `SmartFullSheet`.
- The adapter is `com.nothing.launcher.allapps.smart.e` (`L/.../smart/e.java:110-151`), a subclass of `AllAppsGridAdapter` with view type `131072` and 2 per row.

**Binding.**
- `e.onBindViewHolder` calls `SmartFolderIcon.bind(e.b folderData)` (`SmartFolderIcon.java:92-99`).
- `bind` sets the name, then calls `getFolderIconContainer().bind(apps)`, then `setTag`.
- `SmartFolderContainer.bind` (`SmartFolderContainer.java:117-146`) does `removeAllViews()` and **inflates up to 7 new `NTBubbleTextView`s on every bind**, then `applyFromApplicationInfo` and `verifyHighRes`.
- This happens even when the module hides the previews, so a covered tile still pays for 7 inflations per bind. This is Nothing's cost, not ours.

**Recycling.**
- `AllAppsRecyclerView.java:340-350`: `setMaxRecycledViews(131072, i.f14480b.size())`, so one pooled view per smart category.
- `ActivityAllAppsContainerView.java:273-274`: `setHasFixedSize(true)` and `setItemAnimator(null)`, so there are no item animations.
- Tiles are recycled and rebound. The module's `DotPanel` stays in the tile (found by tag) and `show()` is called again from the bind hook.

**Drawing.**
- `SmartFolderIcon.dispatchDraw` (`:101-108`) calls `setBackground(p7.e.l(context))` **on every draw**, then `super.dispatchDraw`.
- `p7.e.l` (`L/p7/e.java:108-115`) is `context.getDrawable(R.drawable.all_app_icon_container_panel_bg)`, with `setAlpha(178)` in one mode. `getDrawable` returns a **new Drawable instance** each call.
- In AOSP, `View.setBackgroundDrawable` returns early only when it is given the *same* object. Otherwise it calls `invalidate(true)` and `invalidateOutline()`.
- **Suspected (not verified):** calling this inside `dispatchDraw` dirties the tile again in the middle of drawing it. That would make every visible smart tile ask for the next frame, a continuous redraw loop while the drawer shows, whatever the module does.
  - This matters a great deal for power: if true, the drawer never idles and the "redraw only on change" design is lost at the tile level.
  - **Evidence needed:** with the drawer open and still, all covers off (or the module off), run `adb shell dumpsys gfxinfo com.nothing.launcher reset`, wait 10 s, then run `dumpsys gfxinfo com.nothing.launcher`. Check "Total frames rendered". Alternatively turn on Developer options → Profile HWUI rendering → On screen as bars and see whether bars keep coming.
  - If it loops, the CLAUDE.md-compliant fix is to switch off that one piece. Hook `p7.e.l` or `SmartFolderIcon.dispatchDraw` so the same Drawable is reused per tile and the theme is still honoured (a new drawable only when the theme or alpha changes). Do not fight it afterwards.
- No layer type is set on the tile, the container or `AllAppsRecyclerView` (grep `setLayerType` in `L/com/android/launcher3/allapps`, `L/com/nothing/launcher`: none). Tiles are plain display-list children. The only hardware layers are the transition ones in §1.3.

**Where an engine lives.**
- A child view at index 0, as `DotPanel` is now. It has its own RenderNode, so invalidating it re-records only that node: the parents' display lists are reused, but the window still redraws the damaged region.
- Alternatively, a `Drawable` set as the tile's *foreground* would avoid an extra view. But the tile resets its background on every draw, and the foreground is drawn over the name. The child view is the cleaner place.
- The panel's size and placement follow the outline and layout listeners. No re-layout is needed per frame.

**When the launcher redraws it.**
- On bind (recycle or scroll), on layout, on press (`setDuplicateParentStateEnabled` → `drawableStateChanged` → `react()`), on theme change.
- The module's own `invalidate()` per changed frame.
- Possibly every frame because of the `dispatchDraw` behaviour above (unverified).

---

## 3. GPU options in the launcher's process on Android 16

Context: code runs inside `com.nothing.launcher` through LSPosed. compileSdk/targetSdk 36, minSdk 31 (`app/build.gradle`). It needs no new permissions, and nothing may throw into the host.

| Option | Per-frame work | Cost for ~20 small tiles | Risk / constraints | Nothing / AOSP use |
|---|---|---|---|---|
| **A. AGSL `RuntimeShader` as the `Paint` shader of one `drawRect` in the panel's `onDraw`** (API 33+) | UI thread: set a few uniforms and `invalidate()`; re-record one op. GPU: one fragment pass over the tile. | One draw per tile, with no offscreen buffer. Fragment cost is proportional to tile area × taps per pixel. With immutable child textures (hardware bitmaps) there is **no per-frame upload**. | `new RuntimeShader(src)` throws `IllegalArgumentException` on a compile error: construct once, guarded, and fall back to the CPU engine. Skia compiles the shader to GL/Vulkan at **first draw** on the RenderThread, a one-time hitch; warm it by drawing once off-screen or accept one slow frame. RenderThread errors are not thrown into Java. | AOSP surface effects (`SparkleShader`, `TurbulenceNoiseShader`, `RippleShader`) [code]. |
| B. `View.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader,"in_src"))` | Same uniforms; the view's content is rendered to an **offscreen texture** and then filtered. | An extra full-tile render target per tile per frame: more bandwidth and memory than A. | Same compile caveats. It is only needed when the shader must filter *existing view content*. | `LoadingEffect.java:149` [code]. |
| C. Hardware `Bitmap` (`Bitmap.copy(HARDWARE,false)`) as `BitmapShader` children of A | None: an immutable GPU texture. | Saves the per-frame `setPixels` and re-upload of today's design. | Pixels cannot be read back; the module already copies `HARDWARE` bitmaps before reading them (`DotMatrix.setImage`, README 0.55.9). | Launcher icons are often HARDWARE [code/README]. |
| D. `HardwareRenderer` + `RenderNode` to own surfaces (API 29) | Draws a RenderNode into a separate Surface. | For offscreen baking only (e.g. bake per-emoji atlases on the GPU once). Not needed per frame. | More moving parts. | Quickstep `SurfaceTransactionApplier` [code]. |
| E. `TextureView` + GLES | Own EGL context and thread; draws to a SurfaceTexture, which is then composited by HWUI as a texture. | **20 TextureViews means 20 buffer queues** (3 buffers each, tile-sized) plus an EGL context, extra composition and memory. Poor fit for many small tiles. | Lifecycle with RecyclerView recycling is fragile; any EGL error must be caught; no permission issues. | The module's `DotVideoView` uses TextureView for video [code]. Nothing does not in the launcher. |
| F. `SurfaceView` + GLES/Vulkan | Separate SurfaceFlinger layers. | 20 layers is bad: HWC overlay limits, then GPU composition fallback. It cannot be clipped by the tile's rounded outline without SurfaceControl tricks (`r6/j0` does corners for AI widgets). | Z-order and transitions (the drawer slides and fades) clash with Nothing's animations. That would be fighting the launcher. | Nothing AI widget only [code]. |
| G. `AnimatedVectorDrawable` | Runs on the RenderThread with no UI-thread work per frame. | Cheap. | It cannot quantise to dots or do per-dot shading. VectorDrawables cannot be built from code (XML resources only). Not suitable for the look. | Wide AOSP use. |

**Recommendation: A, with C for the textures.**

GPU cost estimate [proposal]:
- A tile is about 500x560 px (2 columns on 1080 px). 20 tiles is about 5.6 Mpx per full redraw.
- A shader at 1 glyph tap per pixel (dot-centre sampling, see §5) plus a few ALU ops is light work for a Mali-G615 MC2 at 60 Hz.
- Today's path already fills each tile 3-4 times with bitmap draws. A single shader pass should cost no more GPU and removes all the CPU raster work.
- **Needs a phone test:** `dumpsys gfxinfo` (jank, 90th-percentile GPU time) and `adb shell dumpsys batterystats` / Perfetto power rails, comparing the CPU engine with the shader engine on the same drawer page.

**Unknowns that need phone evidence:**
- **HWUI backend.** Run `getprop debug.hwui.renderer`, or `dumpsys gfxinfo com.nothing.launcher`, which prints "Pipeline=Skia (OpenGL)" or "(Vulkan)". AGSL works on both.
- **Partial updates (damage regions).** Does one moving tile repaint only its rect? Use `adb shell setprop debug.hwui.show_dirty_regions true`, restart the launcher, and record the screen.
- **Display frame rate.** `setRequestedFrameRate(120)` keeps the panel at 120 Hz while any cover moves (`AnimatedEmojiView.java:445-451`). Dot motion on a 32-dot grid gains little above 60. Consider `REQUESTED_FRAME_RATE_CATEGORY_NORMAL` (60) for idle loops and 120 only during a reaction. Test it on video for smoothness (CLAUDE.md: judge from the pictures), and use `dumpsys SurfaceFlinger | grep -i refresh` for the rate actually chosen.

---

## 4. Emoji assets

### 4.1 System emoji font on the phone [phone]

From `.../scratchpad/log34/8673/maps` (a process memory map captured on the phone):

```
72864d0000-728677f000 r--s /system/fonts/NotoColorEmoji.ttf       (0x2AF000 = 2.75 MB mapped)
72863f0000-72864d0000 r--s /system/fonts/NotoColorEmojiFlags.ttf  (0xE0000  = 0.9 MB)
```

The tombstones in the same dump list the same two files.

**Inference (strong, but the table tags were not read):**
- A CBDT (bitmap) Noto Color Emoji is about 10–24 MB. For example, the CBDT `NotoColorEmojiCompat.ttf` bundled in the launcher is 10.77 MB and has tables `CBDT, CBLC, …` (read from `LR/assets/NotoColorEmojiCompat.ttf`).
- A 2.75 MB `NotoColorEmoji.ttf` with flags split into a separate `NotoColorEmojiFlags.ttf` matches AOSP's Android 13+ layout: a **COLRv1** vector main font and a bitmap flags font.

**To verify:** `adb shell "head -c 512 /system/fonts/NotoColorEmoji.ttf" | xxd | head -20` and look for the `COLR`/`CPAL` tags, or read the table directory in a diagnostic using `android.graphics.fonts.Font#getBuffer()`. `/system/fonts` is world-readable, so no permission is needed.

**Other emoji fonts in the launcher APK** [code/read]:
- `LR/assets/NotoColorEmojiCompat.ttf`: **CBDT/CBLC** bitmaps, tables read. Used by androidx `BundledEmojiCompatConfig`.
- `LR/res/font/noto_emoji_medium.ttf` and `noto_emoji_semibold.ttf`: **monochrome Noto Emoji with `glyf` outlines** (tables read: `GSUB OS/2 STAT cmap gasp glyf head hhea hmtx loca maxp name post vhea vmtx`). These are what Nothing's own folder `CoverView` uses.

### 4.2 Can vector layers give per-part understanding without hand-made assets? [proposal, with the API facts]

Yes, in two layers of increasing richness.

1. **COLRv1 paint graph (colour, parts, gradients).**
   - Get the glyph ID and font with `TextRunShaper.shapeTextRun` (API 31). It returns `PositionedGlyphs`, and each entry gives `getGlyphId(i)` and `getFont(i)`. `Font.getBuffer()` (API 29) gives the font bytes.
   - Parse the `COLR` v1 table (BaseGlyphList → Paint graph: PaintColrLayers, PaintGlyph + PaintSolid/LinearGradient/RadialGradient/SweepGradient, PaintTransform, …) and the `CPAL` palette.
   - Parse the `glyf` quadratic outlines into `android.graphics.Path`.
   - This gives the emoji as an ordered list of **filled shapes with their own paints**: face disc with radial gradient, eye whites, pupils, mouth, tear, cheek blush, highlight ellipses. Each layer becomes a part mask.
   - Heuristics on colour, position and size can then label parts: dark small symmetric pairs in the upper half are eyes; a shape below them is the mouth; blue drops are tears; white or light gradients near the top-left are specular highlights. Those highlights are where "reflections" come from.
   - The parser is a few hundred lines of Kotlin over a ByteBuffer. It runs **once per emoji**, off the main thread, and is cached.
   - It must be fully guarded. Fall back to the raster path for the CBDT flags font, or if the font turns out not to be COLRv1.
   - Android has **no public API** to enumerate COLR layers; Canvas only draws the whole glyph. So parsing the font bytes is the way. It is read-only, needs no permission, and is cheap.
2. **Depth and 3D without models.**
   - From the base silhouette (the alpha of layer 0, or the monochrome `glyf` outline), compute a signed distance field once.
   - Height = smooth function of distance (a "puffy" or dome profile), plus per-part offsets: eyes and mouth slightly inset, tears and hearts raised.
   - From height, compute normals once, packed into an RGBA texture.
   - Per frame the shader lights each dot from a light direction uniform, giving shading, a moving specular highlight (the "reflection") and a rim. A 3D tilt is done by parallax or offset sampling with the height map, and a full turn by a sphere/cylinder mapping (the existing `SphereSurface` idea moved into the shader).
   - "Emotion" is metadata, not pixels. The module already has an emoji catalog (`M/ui/EmojiCatalog.java`; `scratchpad/emoji/emojibase-data-16.0.3.tgz` holds emojibase data with groups, subgroups and tags). Map subgroups such as `face-smiling`, `face-concerned` or `face-affection` to emotion classes, and those classes to animation sets.

**Precise per-part 3D needs, per emoji, reworked by hand** (the roadmap's "every emoji reworked one by one"): a small per-emoji JSON *override* on top of the automatic analysis. It holds part labels, depth tweaks, animation choices and particle emitters. It ships in the module APK, and the automatic analysis fills in every emoji that has no override.

---

## 5. Recommended architecture

### 5.1 Layers

```
EmojiAsset (once per emoji, background thread, cached on disk in module's cache dir of the launcher process or in memory)
  ├─ source: COLRv1 layers parsed from /system/fonts/NotoColorEmoji.ttf   (fallback: raster glyph like today)
  ├─ analysis: part masks + labels, SDF, height, normals, highlight spots, emotion class (catalog), override JSON
  └─ atlas: one immutable HARDWARE Bitmap, e.g. 256x256 x 2 pages:
        page 0 = premultiplied colour (RGBA), page 1 = normal.xy + height + partId
EmojiScene (per emoji: which animations/particles exist; pure data, Kotlin)
DotRenderer (one compiled RuntimeShader program per process; one shader instance per tile)
DotTileView (replaces DotPanel's AnimatedEmojiView use inside the tile; draws glass + one drawRect with the shader + label)
Clock (one Choreographer callback for the process, as MotionDriver now)
```

### 5.2 Once per emoji [proposal]

On a background `Executor`, never on the main thread, and wrapped in `Hooks.guard`:
1. Shape the emoji and parse COLR/CPAL/glyf into layers. On any failure, rasterise like today.
2. Rasterise the layers at 256 px into an ARGB bitmap. Compute SDF, height and normals and the part ids. Pack the atlas, then `copy(HARDWARE)` and recycle the software copies.
3. Build the scene: an animation list from the catalog's emotion class plus the override.
4. Post the result to the main thread. The tile shows the static resting dots until it arrives, as the NDot clock shows a pre-baked bitmap.

Memory: 2 × 256² × 4 B = 512 KB per emoji on the GPU, with an LRU of about 24 emojis (12 MB). That is the same budget as `FolderCovers.cache` (24 MB).

### 5.3 Per frame [proposal]

UI thread, per *moving* tile:
- Evaluate the animation curve. The existing `Motion`/`Pose` maths (`M/anim/Motion.java`, `Pose.java`) can be reused as is: it is only a few floats.
- Set the uniforms: pose matrix (3x3), tilt and light (vec3), time, effect parameters (a few vec4s), particle state (a fixed array of up to 16 particles, or a particle seed with the motion computed in the shader).
- `invalidate()` the tile view.

There is no Bitmap, no Canvas raster, no `getPixels` and no upload.

Shader, per fragment:
1. Find the dot cell (`pitch`, grid origin). If the fragment is outside the dot disc, output the glass or unlit colour with analytic anti-aliasing from distance to the dot centre. This replaces the ALPHA_8 mask bitmap and the separate unlit pass.
2. Map the dot **centre** through the inverse pose to atlas UV. Take one sample from a **mip-mapped** atlas at the level that matches the dot pitch; this replaces the 3x3 supersample. Alternatively take a 2x2 tap.
3. Light it with the normal, height and light uniform: diffuse, a specular highlight that moves with tilt, and rim. Apply effects by part id (blink: eye parts' coverage scaled; tears: a particle) and quantise to the brightness steps (`LEVEL_ALPHA`).
4. Glow: add a second term from a blurred mip level at the same UV. This replaces the two glow bitmap draws.

**Change detection:** the UI side compares the uniform vector with the previous one. If it is equal, there is no `invalidate()`. This keeps today's "frames whose dots don't change aren't redrawn" rule.

### 5.4 Scheduling and stopping

- Keep `MotionDriver`'s single Choreographer callback [code] with its visibility, alpha, window and durationScale gates.
- Add these stop conditions [proposal]:
  - a hook on `Launcher.pauseExpensiveViewUpdates` / `resumeExpensiveViewUpdates` (§1.3), a Nothing signal;
  - LauncherState not ALL_APPS (already hooked);
  - the battery-saver rule (already exists);
  - screen off (window visibility, already there).
- Frame rate [proposal, needs a video test]: run idle loops at 30–60 Hz using `setRequestedFrameRate` categories, and request 120 only for reactions.
- Stagger loops so that only a few tiles animate at once (the "ripple" `reactAll` already staggers). Most tiles rest at zero cost.

### 5.5 Hooking into the tile

- Keep the hook point: after `SmartFolderIcon.bind` → `FolderCovers.apply` [code].
- Replace `DotPanel extends AnimatedEmojiView` with a `DotTileView` (Kotlin) that holds a `DotRenderer`, keeping the same tag, index 0, outline-based placement and name-label handling.
- On recycle, `show()` swaps only the asset reference and the uniforms, not a new view.
- Settle the suspected `setBackground` redraw loop first (§2, evidence first). If it is real, reuse Nothing's drawable per tile instead of fighting the redraw.

### 5.6 Migration from `anim/`

1. **Measure first** (no code change):
   - gfxinfo and frames on the drawer while idle (settles the §2 loop);
   - the HWUI pipeline;
   - dirty regions;
   - the emoji font tables (§4.1);
   - a Perfetto trace of the current engine with about 10 covers moving, to get a CPU baseline.
2. **Step 1: shader drawing, CPU frame.**
   - Keep `DotMatrix.render` producing the 32x32 frame, but draw with one RuntimeShader pass: the frame as a nearest `BitmapShader` child, dots, glass, unlit and glow analytic.
   - This removes the tile-sized ALPHA_8 masks and the 4 passes. It can be verified on a phone against the current look (screenshots side by side).
   - Fall back to `DotMatrix.draw` when `RuntimeShader` construction fails.
3. **Step 2: GPU sampling.** Move pose sampling into the shader using the HARDWARE glyph atlas (the raster glyph, as today). This deletes `rasterize`/`getPixels`/`quantize` for the plain motions. Effects that still need CPU (`DotEffect` on `int[1024]`) keep running at 24 fps on the CPU, feeding a small overlay texture.
4. **Step 3: COLRv1 parser plus analysis** (SDF, normals, parts) → lighting and reflections, and part-driven effects (blink, tears) moved from `DotEffect` into the shader by part id.
5. **Step 4: per-emoji scenes and overrides**: emotion classes and several animations per emoji. `DotEffect`/`ObjectEffects`/`MusicEffects` are retired family by family, and dead code is removed in the same change (CLAUDE.md).
6. Shared users of `DotMatrix` (the module's emoji picker and folder list in `ui/`, Gaming Space's `DotScreen.kt`, `Gamepad.java`) keep the CPU path until each one is moved. `DotScreen.kt` already copies the same look and could share the shader.

### 5.7 Verified vs proposal summary

| Item | Status |
|---|---|
| Nothing's launcher has no GPU dot renderer; its dot widgets use `drawCircle`; the NDot clock uses pre-baked bitmaps + ColorFilter | **verified in code** |
| AOSP AGSL shader effects are in System UI and copied into the launcher APK | **verified in code** |
| Tile drawing path, bind/recycle, `SmartFolderContainer.bind` inflating 7 views per bind, no layer on tiles, HW layer on `NTHiddenAppsPagedView` during app launch, `pause/resumeExpensiveViewUpdates` | **verified in code** |
| `SmartFolderIcon.dispatchDraw` → `setBackground(new drawable)` causes a continuous redraw | **suspected from code + AOSP behaviour; needs gfxinfo on the phone** |
| System emoji font is COLRv1 | **strong inference from the phone's memory map (file sizes, flags split); table tags not yet read** |
| Launcher's bundled emoji-compat font is CBDT; Nothing's monochrome Noto Emoji has `glyf` outlines | **verified (table directories read)** |
| AGSL single-pass engine cheaper than the current CPU engine; frame-rate policy; partial updates | **proposal; needs Perfetto, gfxinfo and video on the phone** |
| COLRv1 parsing for parts and the SDF/normal 3D | **proposal; parser must be tested against the phone's font file** |
