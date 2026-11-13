package com.dressence.nothinglauncherplus.launcher;

import com.dressence.nothinglauncherplus.Config;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;

import com.dressence.nothinglauncherplus.xposed.ConfigStore;
import com.dressence.nothinglauncherplus.xposed.Hooks;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Dot-matrix app icons, while "Dot matrix" is the picked pack in Nothing's Icon Pack picker
 * ({@link DotIconPack}): every icon the launcher draws (home screen, app drawer, icons
 * in drawer folders and their tile previews, search) is shown as a little dot-matrix screen of
 * its own (IconScreen): round in the launcher's circle, or in the icon's own shape.
 *
 * <p>All of them are {@code FastBitmapDrawable}s, so one hook on its {@code draw} covers every
 * surface ({@code drawInternal} is compiled into {@code draw} on some paths, so it is not
 * enough). Each icon bitmap is converted once and cached.
 *
 * <p>When icons come into view (the drawer opening, going home, a folder opening) each one powers
 * on like a little screen: its dots light up from the centre outwards on a spring, each with a
 * brief white LED flash settling into its colour, while the glow fades up underneath. Icons
 * start a few milliseconds apart, so a page shimmers on rather than snapping. At rest an icon is
 * one bitmap draw.
 */
final class DotIcons {

    private static final String CLS_FAST_BITMAP =
            "com.android.launcher3.icons.FastBitmapDrawable";
    /** Icons smaller than this are not worth a screen (placeholders): drawn as they are. */
    private static final int MIN_SIZE = 44;
    /** An icon not drawn for this long powers on again when it next appears. */
    private static final long ASLEEP_MS = 800;
    private static final float POWER_ON_MS = 560;
    private static final long MAX_STAGGER_MS = 110;

    /** Marks an icon that could not be converted. */
    private static final IconScreen FAILED = IconScreen.none();
    private static final Map<Bitmap, IconScreen> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** Per drawable: {last drawn, power-on start} in uptime millis. */
    private static final Map<Object, long[]> SEEN = new WeakHashMap<>();
    /** Icons being converted, and the drawables to redraw when each is ready (main thread). */
    private static final Map<Bitmap, List<WeakReference<Drawable>>> PENDING =
            new WeakHashMap<>();
    private static final ExecutorService CONVERTER = Executors.newSingleThreadExecutor(r -> {
                // Background priority, not the lowest: at the lowest, conversions starved
                // behind the drawer's own animation and icons lit up one by one.
                Thread t = new Thread(() -> {
                    android.os.Process.setThreadPriority(
                            android.os.Process.THREAD_PRIORITY_BACKGROUND);
                    r.run();
                }, "nlplus-dot-icons");
                return t;
            });
    /** Conversions queued and not yet finished (main thread). */
    private static int converting;
    /** Icons converted and waiting to be shown together (main thread). */
    private static final List<WeakReference<Drawable>> READY = new ArrayList<>();
    private static boolean readyFlushPosted;
    /** Finished conversions are shown at once when the queue empties, or after this long. */
    private static final long READY_BATCH_MS = 150;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Scratch for onScreen; main thread. */
    private static final Rect VISIBLE = new Rect();
    /** Icons last drawn out of view (main thread). */
    private static final Set<Drawable> OUT_OF_VIEW =
            Collections.newSetFromMap(new WeakHashMap<>());
    /**
     * For this long after an icon was drawn out of view, every frame checks whether it has come
     * into view. Opening the drawer or a folder fades and slides it in as a whole, which Android
     * does without redrawing the icons, so this check is what lets them power on together, in
     * step with it (as the first dot icons did), instead of whenever they next happen to be
     * redrawn. A visibility check, nothing drawn.
     */
    private static final long WATCH_MS = 1200;
    /** After the watch, the rare case: one check a second, which leaves the launcher idle. */
    private static final long RECHECK_MS = 1000;
    private static long watchUntil;
    private static boolean watching;
    private static boolean recheckPosted;
    private static final Choreographer.FrameCallback WATCH = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            Hooks.guard("dot icons watch", () -> {
                wakeInView();
                if (!OUT_OF_VIEW.isEmpty() && SystemClock.uptimeMillis() < watchUntil) {
                    Choreographer.getInstance().postFrameCallback(WATCH);
                } else {
                    watching = false;
                    if (!OUT_OF_VIEW.isEmpty()) {
                        recheckSoon();
                    }
                }
            }).run();
        }
    };
    /** FastBitmapDrawable and BitmapInfo fields, looked up once. */
    private static Field fBitmapInfo;
    private static Field fIcon;
    private static Field fPaint;
    private static Field fBadge;
    private static Field fScale;
    private static Bitmap lowRes;
    private static final Paint DOT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint GLOW_PAINT = new Paint(Paint.FILTER_BITMAP_FLAG);
    /** Keeps what is under the glass's shape: the powering-on dots are cut to it. */
    private static final Paint CUT_PAINT = new Paint(Paint.FILTER_BITMAP_FLAG);

    static {
        CUT_PAINT.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }
    private static WeakReference<Activity> launcher = new WeakReference<>(null);

    private DotIcons() {
    }

    /** How the icons' screens light: Config's iconDotStyle (see IconScreen#of(Bitmap, int)). */
    private static volatile int style = Config.DOT_STYLE_CLASSIC;

    /** The icons' style now, for the icon pack's preview (DotIconPack). */
    static int style() {
        return style;
    }

    static void install(ClassLoader cl, ConfigStore store) {
        style = store.get().iconDotStyle;
        // A new style: every icon is made again in it, and redrawn (main thread).
        store.addChangeListener(() -> {
            int chosen = store.get().iconDotStyle;
            if (chosen == style) {
                return;
            }
            style = chosen;
            CACHE.clear();
            Activity activity = launcher.get();
            if (activity != null) {
                invalidateAll(activity.getWindow().getDecorView());
            }
        });
        Class<?> fast = XposedHelpers.findClass(CLS_FAST_BITMAP, cl);
        Class<?> bitmapInfo = XposedHelpers.findClass("com.android.launcher3.icons.BitmapInfo", cl);
        fBitmapInfo = field(fast, "mBitmapInfo");
        fPaint = field(fast, "mPaint");
        fBadge = field(fast, "mBadge");
        fScale = field(fast, "mScale");
        fIcon = field(bitmapInfo, "icon");
        lowRes = (Bitmap) XposedHelpers.getStaticObjectField(bitmapInfo, "LOW_RES_ICON");
        // Keep draw and drawInternal un-inlined so every caller goes through the hook.
        Hooks.deoptimizeAll(fast, "draw");
        Hooks.deoptimizeAll(fast, "drawInternal");
        XposedHelpers.findAndHookMethod(fast, "draw", Canvas.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (DotIconPack.active() && draw((Drawable) param.thisObject,
                                (Canvas) param.args[0])) {
                            param.setResult(null);
                        }
                    }
                });
        XposedHelpers.findAndHookMethod(Config.CLS_LAUNCHER, cl, "onCreate",
                Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        launcher = new WeakReference<>((Activity) param.thisObject);
                    }
                });
        XposedHelpers.findAndHookMethod(Config.CLS_LAUNCHER, cl, "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!OUT_OF_VIEW.isEmpty() && DotIconPack.active()) {
                            recheckSoon(); // Paused while the launcher was away.
                        }
                    }
                });
        // Picking the pack or leaving it redraws every icon on screen at once (the launcher
        // reloads its icons too, a moment later).
        DotIconPack.install(cl, () -> {
            Activity activity = launcher.get();
            if (activity != null) {
                invalidateAll(activity.getWindow().getDecorView());
            }
        });
    }

    /**
     * {@code d} was drawn out of view: it is redrawn, and powers on, in the frame it comes into
     * view. Icons that come into view in the same frame start together.
     */
    private static void recheckLater(Drawable d) {
        OUT_OF_VIEW.add(d);
        watchUntil = SystemClock.uptimeMillis() + WATCH_MS;
        if (!watching) {
            watching = true;
            Choreographer.getInstance().postFrameCallback(WATCH);
        }
    }

    /** Redraws every icon waiting out of view that is in view now. */
    private static void wakeInView() {
        if (OUT_OF_VIEW.isEmpty()) {
            return;
        }
        List<Drawable> now = new ArrayList<>();
        for (Drawable d : OUT_OF_VIEW) {
            if (d != null && onScreen(d)) {
                now.add(d);
            }
        }
        for (Drawable d : now) {
            OUT_OF_VIEW.remove(d);
            d.invalidateSelf();
        }
    }

    private static void recheckSoon() {
        if (recheckPosted) {
            return;
        }
        recheckPosted = true;
        MAIN.postDelayed(Hooks.guard("dot icons recheck", () -> {
            recheckPosted = false;
            if (!DotIconPack.active()) {
                OUT_OF_VIEW.clear(); // The pack was left: nothing waits to power on.
                return;
            }
            wakeInView();
            // Paused while the launcher is not in front (its icons cannot come into view; it
            // checked once a second for as long as other apps were open, 0.70.14 audit);
            // onResume starts it again.
            if (!OUT_OF_VIEW.isEmpty() && !watching && launcherInFront()) {
                recheckSoon();
            }
        }), RECHECK_MS);
    }

    /** True while the launcher's window shows (it is not behind another app). */
    private static boolean launcherInFront() {
        Activity activity = launcher.get();
        return activity != null && activity.getWindow().getDecorView().getWindowVisibility()
                == View.VISIBLE;
    }

    /** True if {@code d}'s view is on the display and not faded out; true if it has no view. */
    private static boolean onScreen(Drawable d) {
        Drawable.Callback callback = d.getCallback();
        if (!(callback instanceof View)) {
            return true;
        }
        View view = (View) callback;
        if (!view.isShown() || !view.getGlobalVisibleRect(VISIBLE)) {
            return false;
        }
        float alpha = 1;
        for (View v = view; ; v = (View) v.getParent()) {
            alpha *= v.getAlpha();
            if (alpha < 0.01f) {
                return false;
            }
            if (!(v.getParent() instanceof View)) {
                return true;
            }
        }
    }

    /** Draws {@code d} as dots, as FastBitmapDrawable#draw would; false to draw it normally. */
    private static boolean draw(Drawable d, Canvas canvas) {
        String cls = d.getClass().getName();
        // The live clock, install progress and loading placeholders draw extras of their own.
        if (cls.contains("Clock") || cls.contains("Preload") || cls.contains("PlaceHolder")) {
            return false;
        }
        Paint paint;
        Drawable badge;
        float scale;
        Bitmap icon;
        try {
            Object info = fBitmapInfo.get(d);
            icon = (Bitmap) fIcon.get(info);
            if (icon == null || icon.getWidth() < MIN_SIZE || icon == lowRes) {
                return false;
            }
            paint = (Paint) fPaint.get(d);
            badge = (Drawable) fBadge.get(d);
            scale = fScale.getFloat(d);
        } catch (IllegalAccessException e) {
            return false;
        }
        Rect bounds = d.getBounds();

        long now = SystemClock.uptimeMillis();
        long[] seen = SEEN.get(d);
        if (seen == null) {
            seen = new long[3];
            SEEN.put(d, seen);
        }
        IconScreen dots = screen(icon, d);
        if (dots == FAILED) {
            return false;
        }
        if (dots == null) {
            // Still being converted: stay dark, and power on as soon as it is ready.
            seen[0] = now;
            seen[1] = Long.MAX_VALUE;
            return true;
        }
        if (!onScreen(d)) {
            // Laid out but not in view (the closed drawer, a page aside): one still frame. A
            // view redrawn every frame keeps the launcher from ever going idle, and its loader
            // waits for idle before loading apps and widgets.
            int save = canvas.save();
            if (scale != 1) {
                canvas.scale(scale, scale, bounds.exactCenterX(), bounds.exactCenterY());
            }
            canvas.drawBitmap(dots.full, null, bounds, paint);
            if (badge != null) {
                badge.draw(canvas);
            }
            canvas.restoreToCount(save);
            recheckLater(d);
            return true;
        }
        if (seen[1] == Long.MAX_VALUE || now - seen[0] > ASLEEP_MS) {
            seen[1] = now + (System.identityHashCode(d) & 0x7FFFFFFF) % MAX_STAGGER_MS;
        }
        seen[0] = now;
        float t = (now - seen[1]) / POWER_ON_MS;

        int save = canvas.save();
        if (scale != 1) {
            canvas.scale(scale, scale, bounds.exactCenterX(), bounds.exactCenterY());
        }
        if (t >= 1) {
            drawWithScan(canvas, bounds, dots, paint, d, icon, now, seen);
        } else {
            powerOn(canvas, bounds, dots, Math.max(0, t), paint.getAlpha());
            d.invalidateSelf();
        }
        if (badge != null) {
            badge.draw(canvas);
        }
        canvas.restoreToCount(save);
        return true;
    }

    /** How often (seconds, varied per icon) the scan line passes, and how long it takes. */
    private static final float SCAN_EVERY = 11;
    private static final float SCAN_TIME = 1.5f;
    /** The scan line's colour (no alpha): the green of an old phosphor screen. */
    private static final int SCAN_GREEN = 0x4CFF8A;
    private static final Paint SCAN = new Paint();
    /**
     * The scan line's band, made once over 0..1 and moved and stretched onto each frame's band
     * by its matrix; its strength is the paint's alpha. The same pixels as a new gradient with
     * the peak's alpha each frame (the edges are clear, so alpha only scales), without one
     * new gradient per scanning icon per frame (0.70.14 audit).
     */
    private static final LinearGradient SCAN_BAND = new LinearGradient(0, 0, 0, 1,
            new int[] {SCAN_GREEN, 0xFF000000 | SCAN_GREEN, SCAN_GREEN}, null,
            Shader.TileMode.CLAMP);
    private static final android.graphics.Matrix SCAN_PLACE = new android.graphics.Matrix();
    static {
        SCAN.setXfermode(new PorterDuffXfermode(
                PorterDuff.Mode.SRC_IN));
    }

    private static float hash(int a, int b) {
        int h = a * 0x27d4eb2d ^ b * 0x165667b1;
        h ^= h >>> 15;
        h *= 0x2c1b3c6d;
        h ^= h >>> 12;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    /**
     * The icon at rest. Now and then, each icon on its own timing, a faint green scan line
     * rolls down its lit dots, as on an old dot screen; between passes it is one still draw.
     */
    private static void drawWithScan(Canvas canvas, Rect bounds, IconScreen dots, Paint paint,
            Drawable d, Bitmap icon, long now, long[] seen) {
        canvas.drawBitmap(dots.full, null, bounds, paint);
        int seed = System.identityHashCode(icon);
        float t = (now % 3_600_000L) / 1000f;
        float period = SCAN_EVERY * (0.7f + 0.6f * hash(seed, 1));
        float local = (t + hash(seed, 2) * period) % period;
        if (local < SCAN_TIME) {
            float scan = local / SCAN_TIME;
            // Only the lit dots take on the line's green.
            int layer = canvas.saveLayer(bounds.left, bounds.top, bounds.right, bounds.bottom,
                    null);
            canvas.drawBitmap(dots.lit, null, bounds, paint);
            float band = bounds.height() * 0.22f;
            float e = scan * scan * (3 - 2 * scan);
            float y = bounds.top - band + e * (bounds.height() + band * 2);
            SCAN_PLACE.setScale(1, band);
            SCAN_PLACE.postTranslate(0, y - band / 2);
            SCAN_BAND.setLocalMatrix(SCAN_PLACE);
            SCAN.setShader(SCAN_BAND);
            SCAN.setAlpha(Math.round(150 * (float) Math.sin(scan * Math.PI)));
            canvas.drawRect(bounds.left, y - band / 2, bounds.right, y + band / 2, SCAN);
            canvas.restoreToCount(layer);
            d.invalidateSelf();
            return;
        }
        // Asleep until the next pass: one redraw is booked for it.
        long at = now + Math.max(16, Math.round((period - local) * 1000));
        if (seen[2] > now && seen[2] <= at) {
            return;
        }
        seen[2] = at;
        WeakReference<Drawable> ref = new WeakReference<>(d);
        MAIN.postAtTime(() -> {
            Drawable drawable = ref.get();
            long[] mark = SEEN.get(drawable);
            if (drawable != null && mark != null) {
                // Our own wake-up, not the icon coming into view: it must not power on again.
                mark[0] = SystemClock.uptimeMillis();
                drawable.invalidateSelf();
            }
        }, at);
    }

    /**
     * One frame of an icon powering on ({@code t} 0..1): the glow fades up, and each dot, from
     * the centre outwards, grows in on a spring with a white flash that settles into its colour.
     */
    private static void powerOn(Canvas canvas, Rect bounds, IconScreen dots, float t, int alpha) {
        float k = bounds.width() / dots.size;
        // The glass is there from the start; only the dots power on.
        GLOW_PAINT.setAlpha(alpha);
        canvas.drawBitmap(dots.plate, null, bounds, GLOW_PAINT);
        GLOW_PAINT.setAlpha(Math.round(alpha * ease(t)));
        canvas.drawBitmap(dots.glow, null, bounds, GLOW_PAINT);
        float half = dots.size / 2;
        // The edge dots run under the bezel: drawn in a layer, then cut to the glass.
        int layer = canvas.saveLayer(bounds.left, bounds.top, bounds.right, bounds.bottom, null);
        for (int i = 0; i < dots.x.length; i++) {
            float dx = dots.x[i] - half;
            float dy = dots.y[i] - half;
            float reach = (float) Math.sqrt(dx * dx + dy * dy) / (half * 1.42f);
            float local = (t - reach * 0.45f) / 0.55f;
            if (local <= 0) {
                continue;
            }
            float s = spring(Math.min(1, local));
            int c = dots.color[i];
            float flash = Math.max(0, 1 - local * 1.6f);
            int rgb = mix(c, 0xFFFFFF, 0.65f * flash);
            DOT_PAINT.setColor(rgb);
            DOT_PAINT.setAlpha(Math.round((c >>> 24) * Math.min(1, s * 1.15f) * alpha / 255f));
            canvas.drawCircle(bounds.left + dots.x[i] * k, bounds.top + dots.y[i] * k,
                    dots.radius * k * (0.35f + 0.65f * Math.min(1.08f, s)), DOT_PAINT);
        }
        canvas.drawBitmap(dots.mask, null, bounds, CUT_PAINT);
        canvas.restoreToCount(layer);
    }

    /** A declared field of {@code cls} or a superclass, made accessible. */
    /**
     * The field {@code name} of {@code cls} or a superclass. Nothing OS 5's Launcher3 rewrote
     * FastBitmapDrawable in Kotlin, so its fields lost the "m" (mBitmapInfo is bitmapInfo,
     * mPaint paint, mBadge badge, mScale scale; same types): that name is tried second
     * (Nothing OS 5 tester's Hook health, 0.70.33).
     */
    private static Field field(Class<?> cls, String name) {
        String kotlinName = name.length() > 1 && name.charAt(0) == 'm'
                && Character.isUpperCase(name.charAt(1))
                ? Character.toLowerCase(name.charAt(1)) + name.substring(2) : null;
        for (String candidate : kotlinName == null ? new String[] {name}
                : new String[] {name, kotlinName}) {
            for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField(candidate);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException e) {
                    // Try the superclass.
                }
            }
        }
        throw new IllegalStateException("no field " + name + " in " + cls.getName());
    }

    /** A critically damped spring from 0 to 1, with a whisper of overshoot. */
    private static float spring(float x) {
        double w = 9;
        return (float) (1 - (1 + w * x * 0.85) * Math.exp(-w * x));
    }

    private static float ease(float x) {
        float inv = 1 - Math.min(1, x);
        return 1 - inv * inv * inv;
    }

    private static int mix(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
        int bl = Math.round((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static void invalidateAll(View view) {
        view.invalidate();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                invalidateAll(group.getChildAt(i));
            }
        }
    }

    /**
     * Shows the icons converted so far, all in the same frame so they power on together: at
     * once when nothing is left to convert ({@code now}), else at most READY_BATCH_MS later.
     */
    private static void showReady(boolean now) {
        if (READY.isEmpty()) {
            return;
        }
        if (!now) {
            if (!readyFlushPosted) {
                readyFlushPosted = true;
                MAIN.postDelayed(Hooks.guard("dot icons ready batch", () -> {
                    readyFlushPosted = false;
                    showReady(true);
                }), READY_BATCH_MS);
            }
            return;
        }
        List<WeakReference<Drawable>> due = new ArrayList<>(READY);
        READY.clear();
        for (WeakReference<Drawable> ref : due) {
            Drawable drawable = ref.get();
            if (drawable != null) {
                drawable.invalidateSelf();
            }
        }
    }

    /**
     * {@code icon} as dots, or null while it is being made: converted once per bitmap, off the
     * main thread, after which every drawable that asked for it is redrawn.
     */
    private static IconScreen screen(Bitmap icon, Drawable asking) {
        IconScreen cached = CACHE.get(icon);
        if (cached != null) {
            return cached;
        }
        List<WeakReference<Drawable>> waiting = PENDING.get(icon);
        if (waiting != null) {
            waiting.add(new WeakReference<>(asking));
            return null;
        }
        waiting = new ArrayList<>();
        waiting.add(new WeakReference<>(asking));
        PENDING.put(icon, waiting);
        WeakReference<Bitmap> source = new WeakReference<>(icon);
        converting++;
        int madeIn = style;
        CONVERTER.execute(() -> {
            Bitmap bitmap = source.get();
            IconScreen made;
            try {
                made = bitmap != null && !bitmap.isRecycled() ? IconScreen.of(bitmap, madeIn)
                        : FAILED;
            } catch (Throwable t) {
                // Out of memory included: this one icon is drawn as it is from now on.
                Hooks.log("dot icon failed: " + t);
                made = FAILED;
            }
            IconScreen result = made;
            MAIN.post(Hooks.guard("dot icon ready", () -> {
                converting--;
                Bitmap key = source.get();
                if (key != null) {
                    List<WeakReference<Drawable>> asked = PENDING.remove(key);
                    // Made in a style changed since: not kept; its icons ask again when redrawn.
                    if (madeIn == style) {
                        CACHE.put(key, result);
                    }
                    if (asked != null) {
                        READY.addAll(asked);
                    }
                }
                showReady(converting <= 0);
            }));
        });
        return null;
    }
}
