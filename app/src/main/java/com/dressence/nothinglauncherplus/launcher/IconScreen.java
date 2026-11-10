package com.dressence.nothinglauncherplus.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * An app icon made into a little dot-matrix screen, as Nothing would make one: a bezel round
 * a pane of glass, and on the glass a grid of round dots, lit in the icon's own colours with a
 * dim glow, the rest unlit.
 *
 * <p>The screen keeps the icon's shape. An icon in the launcher's shape (a circle) is a round
 * screen: dark glass inside a bezel ring, the artwork filling the glass. An icon with a shape
 * of its own keeps it: the glass is its outline, dark as the round one's, with the bezel
 * following the outline round it. (Until 0.55.5 that glass and its unlit dots were green,
 * which showed as a green edge round the dots; Dr.Essence found it did not look good.) An old-style icon the launcher put on a white plate is taken
 * off the plate first and shown in its own shape, filling the space.
 *
 * <p>Made once per icon bitmap, off the main thread (see DotIcons).
 */
public final class IconScreen {

    /**
     * Dots across the icon. 34 (until 0.55.9, 22): Dr.Essence asked for more and smaller dots;
     * picked from renders at 22, 28, 34 and 40, where 34 kept the shapes crisp and still read as
     * a dot screen at icon size.
     */
    private static final int GRID = 34;
    /** Dot diameter, in dot spacings. */
    private static final float DOT = 0.8f;
    /** Below this a dot's cell counts as dark; above it, one of these opacities. */
    private static final float OFF_BELOW = 0.55f;
    private static final int[] LEVEL_ALPHA = {150, 200, 235, 255};
    /** A shape this close to a circle is the launcher's circle (adaptive icons). */
    private static final float CIRCLE_MATCH = 0.93f;
    /** The bezel's width, in icon widths: round screens, and screens in their own shape. */
    private static final float BEZEL_ROUND = 0.06f;
    private static final float BEZEL_OWN = 0.05f;
    /**
     * How far, in dot spacings, the bezel reaches in over the dot grid. The grid runs out to the
     * glass's edge and the bezel covers the dots there, so the dots end in the glass's own
     * outline, whatever its shape. (A square grid that stops at the last whole dot ends in
     * steps that do not follow a curved bezel, seen on the phone; Dr.Essence picked this over
     * rings and a plain thicker bezel, for every shape.)
     */
    private static final float EDGE_COVER = 0.35f;
    /** The dark glass and its unlit dots, for every shape. */
    private static final int GLASS = 0xFF050605;
    private static final int UNLIT = 0xFF1A1B1A;
    /** The hairline rim where glass meets bezel: what makes a dot grid read as a screen. */
    private static final float RIM_ALPHA = 0.18f;
    private static final int GLOW_SCALE = 4;

    /** The finished screen, its parts for the power-on, and every lit dot. */
    Bitmap full;
    /** Bezel, glass and unlit dots: what shows before any dot lights. */
    Bitmap plate;
    /** The light the lit dots throw on the glass. */
    Bitmap glow;
    /** The glass's shape (alpha): the dots are cut to it, also while they power on. */
    Bitmap mask;
    /** The lit dots alone, for the scan line to tint. */
    Bitmap lit;
    float[] x;
    float[] y;
    int[] color;
    float radius;
    float size;

    private IconScreen() {
    }

    /**
     * The finished dot screen of {@code icon}, or null if it cannot be made into one. For the
     * module's own pages (Nothing Gaming Space's tiles), which draw it as a picture. Slow: call
     * it off the main thread.
     */
    public static Bitmap dots(Bitmap icon) {
        IconScreen screen = of(icon);
        return screen.full;
    }

    /** An empty screen: marks an icon that could not be made into one. */
    static IconScreen none() {
        return new IconScreen();
    }

    static IconScreen of(Bitmap icon) {
        if (icon.getConfig() == Bitmap.Config.HARDWARE) {
            icon = icon.copy(Bitmap.Config.ARGB_8888, false); // Pixels can't be read on the GPU.
        }
        int w = icon.getWidth();
        int h = icon.getHeight();
        Bitmap art = withoutWhitePlate(icon);
        boolean ownShape = art != icon || !isCircle(icon);
        IconScreen screen = new IconScreen();
        screen.size = w;
        Layout layout = ownShape ? ownShape(art != icon ? art : icon, w, h)
                : round(icon, w, h);
        screen.build(layout, w, h);
        return screen;
    }

    /**
     * Where the screen is, pixel by pixel: how much of each pixel is the screen at all (bezel
     * included), how much is glass, and how far inside the glass it is; and the artwork as
     * shown on the glass.
     */
    private static final class Layout {
        float[] body;
        float[] glass;
        float[] depth;
        int[] art;
        /** The screen's centre: the dot grid is laid out from it, the same on every side. */
        float cx;
        float cy;
    }

    /** The launcher's circle: dark glass in a bezel ring, the artwork scaled into the glass. */
    private static Layout round(Bitmap icon, int w, int h) {
        int[] px = pixels(icon);
        Rect box = opaqueBounds(px, w, h);
        float cx = box.exactCenterX();
        float cy = box.exactCenterY();
        float r = Math.max(box.width(), box.height()) / 2f;
        float bezel = w * BEZEL_ROUND + EDGE_COVER * w / GRID;
        float inner = r - bezel;
        Layout l = new Layout();
        l.cx = cx;
        l.cy = cy;
        int n = w * h;
        l.body = new float[n];
        l.glass = new float[n];
        l.depth = new float[n];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float d = (float) Math.hypot(x + 0.5f - cx, y + 0.5f - cy);
                int i = y * w + x;
                l.body[i] = clamp(r - d + 0.5f);
                l.glass[i] = clamp(inner - d + 0.5f);
                l.depth[i] = inner - d;
            }
        }
        // The whole artwork on the glass: scaled down by the bezel, about the centre.
        Matrix m = new Matrix();
        m.setScale(inner / r, inner / r, cx, cy);
        l.art = pixels(transformed(icon, m));
        return l;
    }

    /**
     * An icon in a shape of its own: the glass is the shape (scaled a little so the bezel
     * round it stays inside the icon's space), the bezel follows its outline outside it.
     */
    private static Layout ownShape(Bitmap icon, int w, int h) {
        int[] px = pixels(icon);
        Rect box = opaqueBounds(px, w, h);
        float bezel = w * BEZEL_OWN + EDGE_COVER * w / GRID;
        float scale = Math.max(0.5f, 1 - 2 * bezel / Math.max(1, Math.max(box.width(),
                box.height())));
        Matrix m = new Matrix();
        m.setScale(scale, scale, box.exactCenterX(), box.exactCenterY());
        int[] art = pixels(transformed(icon, m));
        int n = w * h;
        boolean[] shape = new boolean[n];
        boolean[] outside = new boolean[n];
        for (int i = 0; i < n; i++) {
            shape[i] = (art[i] >>> 24) >= 128;
            outside[i] = !shape[i];
        }
        float[] fromShape = Distance.to(shape, w, h);
        float[] fromOutside = Distance.to(outside, w, h);
        Layout l = new Layout();
        l.cx = box.exactCenterX();
        l.cy = box.exactCenterY();
        l.body = new float[n];
        l.glass = new float[n];
        l.depth = new float[n];
        for (int i = 0; i < n; i++) {
            float a = (art[i] >>> 24) / 255f;
            l.body[i] = shape[i] ? 1 : clamp(bezel - fromShape[i] + 0.5f);
            l.glass[i] = a;
            l.depth[i] = shape[i] ? fromOutside[i] - 0.5f : -fromShape[i];
        }
        l.art = art;
        return l;
    }

    /** Draws the screen: bezel, glass and rim, the dots on the grid, and their glow. */
    private void build(Layout l, int w, int h) {
        int n = w * h;
        float pitch = w / (float) GRID;
        radius = pitch * DOT / 2;
        int[] platePx = new int[n];
        for (int y = 0; y < h; y++) {
            // The bezel lit a little from above, like a device's frame.
            int shade = 34 + Math.round(14 * (1 - y / (float) h));
            int bezel = Color.rgb(shade, shade, shade + 2);
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (l.body[i] <= 0) {
                    continue;
                }
                int c = mix(bezel, GLASS, l.glass[i]);
                float rim = clamp(1 - Math.abs(l.depth[i] - 0.5f) / 1.2f) * RIM_ALPHA;
                c = mix(c, Color.WHITE, rim);
                platePx[i] = (Math.round(255 * l.body[i]) << 24) | (c & 0xFFFFFF);
            }
        }
        plate = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        plate.setPixels(platePx, 0, w, 0, 0, w, h);

        // The glass's shape: the dots and their glow are cut to it.
        int[] glassPx = new int[n];
        for (int i = 0; i < n; i++) {
            glassPx[i] = Math.round(255 * l.glass[i] * l.body[i]) << 24;
        }
        Bitmap glassMask = Bitmap.createBitmap(glassPx, w, h, Bitmap.Config.ARGB_8888);
        Paint keep = new Paint(Paint.FILTER_BITMAP_FLAG);
        keep.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

        // The dots: the square grid out to the glass's edge, the bezel covering the edge ones.
        List<float[]> spots = grid(l, w, h, pitch);
        int[] cells = new int[spots.size()];
        int count = 0;
        for (int i = 0; i < cells.length; i++) {
            float[] spot = spots.get(i);
            cells[i] = colourAt(l.art, l.glass, w, h, spot[0], spot[1], pitch);
            if (cells[i] != 0) {
                count++;
            }
        }
        x = new float[count];
        y = new float[count];
        color = new int[count];
        Bitmap unlitBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas unlitCanvas = new Canvas(unlitBitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(UNLIT);
        int k = 0;
        for (int i = 0; i < cells.length; i++) {
            float[] spot = spots.get(i);
            if (cells[i] != 0) {
                x[k] = spot[0];
                y[k] = spot[1];
                color[k] = cells[i];
                k++;
            }
            unlitCanvas.drawCircle(spot[0], spot[1], radius, paint); // every dot, lit or not
        }
        unlitCanvas.drawBitmap(glassMask, 0, 0, keep);
        new Canvas(plate).drawBitmap(unlitBitmap, 0, 0, null);
        unlitBitmap.recycle();

        // The glow: each lit dot's light falling dimly on the glass round it, and a faint wide
        // bloom where many lit dots are together; only on the glass.
        Bitmap glowBitmap = Bitmap.createBitmap(Math.max(1, w / GLOW_SCALE),
                Math.max(1, h / GLOW_SCALE), Bitmap.Config.ARGB_8888);
        Canvas glowCanvas = new Canvas(glowBitmap);
        glowCanvas.save();
        glowCanvas.scale(glowBitmap.getWidth() / (float) w, glowBitmap.getHeight() / (float) h);
        Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < x.length; i++) {
            int rgb = color[i] & 0xFFFFFF;
            halo.setShader(new RadialGradient(x[i], y[i], pitch * 1.25f,
                    new int[] {rgb | 0x46000000, rgb | 0x16000000, rgb},
                    new float[] {0, 0.55f, 1}, Shader.TileMode.CLAMP));
            glowCanvas.drawCircle(x[i], y[i], pitch * 1.25f, halo);
        }
        glowCanvas.restore();
        Bitmap wide = bloom(w, h, 12, pitch * 1.5f);
        Paint add = new Paint(Paint.FILTER_BITMAP_FLAG);
        add.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        add.setAlpha(55);
        glowCanvas.drawBitmap(wide, null, new Rect(0, 0, glowBitmap.getWidth(),
                glowBitmap.getHeight()), add);
        wide.recycle();
        glowCanvas.drawBitmap(glassMask, null, new Rect(0, 0, glowBitmap.getWidth(),
                glowBitmap.getHeight()), keep);

        Bitmap litBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas litCanvas = new Canvas(litBitmap);
        Bitmap fullBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas fullCanvas = new Canvas(fullBitmap);
        fullCanvas.drawBitmap(plate, 0, 0, null);
        fullCanvas.drawBitmap(glowBitmap, null, new Rect(0, 0, w, h),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        for (int i = 0; i < x.length; i++) {
            paint.setColor(color[i]);
            litCanvas.drawCircle(x[i], y[i], radius, paint);
        }
        litCanvas.drawBitmap(glassMask, 0, 0, keep);
        fullCanvas.drawBitmap(litBitmap, 0, 0, null);
        // Kept on the GPU: no upload when drawn, and off the app's Java heap.
        mask = toHardware(glassMask);
        full = toHardware(fullBitmap);
        plate = toHardware(plate);
        lit = toHardware(litBitmap);
        glow = toHardware(glowBitmap);
    }

    /**
     * The square grid's dot centres where any of the dot shows on the glass. The grid is laid
     * out from the screen's centre, so it meets the bezel the same way on every side. (Until
     * 0.55.10 it started at the bitmap's corner and a dot was kept by the depth at the pixel
     * its centre fell in, with a margin: at 34 dots across, the left edge of a rounded-square
     * icon ended in a dark strip before the bezel while the right edge ran under it, seen in
     * a render.)
     */
    private static List<float[]> grid(Layout l, int w, int h, float pitch) {
        List<float[]> spots = new ArrayList<>();
        float reach = radiusFor(pitch);
        // The first column and row at or after the bitmap's edge, on the centre's lattice.
        float x0 = l.cx - (float) Math.floor(l.cx / pitch) * pitch;
        float y0 = l.cy - (float) Math.floor(l.cy / pitch) * pitch;
        for (float dy = y0; dy < h; dy += pitch) {
            for (float dx = x0; dx < w; dx += pitch) {
                if (depthAt(l, w, h, dx, dy) > -reach) {
                    spots.add(new float[] {dx, dy});
                }
            }
        }
        return spots;
    }

    /** The depth into the glass at a point between pixel centres (bilinear). */
    private static float depthAt(Layout l, int w, int h, float x, float y) {
        float fx = Math.max(0, Math.min(w - 1.001f, x - 0.5f));
        float fy = Math.max(0, Math.min(h - 1.001f, y - 0.5f));
        int ix = (int) fx;
        int iy = (int) fy;
        float tx = fx - ix;
        float ty = fy - iy;
        float top = l.depth[iy * w + ix] * (1 - tx) + l.depth[iy * w + ix + 1] * tx;
        float bottom = l.depth[(iy + 1) * w + ix] * (1 - tx) + l.depth[(iy + 1) * w + ix + 1] * tx;
        return top * (1 - ty) + bottom * ty;
    }

    private static float radiusFor(float pitch) {
        return pitch * DOT / 2;
    }

    /**
     * The colour of the dot centred at ({@code cx}, {@code cy}): the artwork's average colour
     * over a dot spacing round it, at one of a few LED levels by how much of that it covers;
     * 0 for a dark dot. Coverage counts only the glass there, so a dot half under the bezel is
     * lit as fully as the artwork beside it.
     */
    private static int colourAt(int[] art, float[] glass, int w, int h, float cx, float cy,
            float pitch) {
        int x0 = Math.max(0, Math.round(cx - pitch / 2));
        int x1 = Math.min(w, Math.round(cx + pitch / 2));
        int y0 = Math.max(0, Math.round(cy - pitch / 2));
        int y1 = Math.min(h, Math.round(cy + pitch / 2));
        long a = 0, r = 0, g = 0, b = 0;
        float n = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                n += glass[y * w + x];
                int c = art[y * w + x];
                int ca = c >>> 24;
                a += ca;
                r += ((c >> 16) & 0xFF) * ca;
                g += ((c >> 8) & 0xFF) * ca;
                b += (c & 0xFF) * ca;
            }
        }
        if (n < 0.5f || a == 0) {
            return 0;
        }
        float coverage = Math.min(1, a / (255f * n));
        if (coverage < OFF_BELOW) {
            return 0;
        }
        int level = Math.min(LEVEL_ALPHA.length,
                Math.max(1, Math.round(coverage * LEVEL_ALPHA.length)));
        return Color.argb(LEVEL_ALPHA[level - 1], (int) (r / a), (int) (g / a), (int) (b / a));
    }

    /** The lit dots drawn at 1/{@code down} size, {@code r} each: blurred when stretched. */
    private Bitmap bloom(int w, int h, int down, float r) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, w / down), Math.max(1, h / down),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(b.getWidth() / (float) w, b.getHeight() / (float) h);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < x.length; i++) {
            int col = color[i];
            // Bright colours bloom more than dim ones.
            float luma = (0.3f * ((col >> 16) & 0xFF) + 0.59f * ((col >> 8) & 0xFF)
                    + 0.11f * (col & 0xFF)) / 255f;
            p.setColor(col & 0xFFFFFF | Math.round(255 * (0.35f + 0.65f * luma)) << 24);
            c.drawCircle(x[i], y[i], r, p);
        }
        return b;
    }

    /**
     * True if the icon's silhouette is a circle: the launcher's shape (adaptive icons). Also
     * used by Nothing Gaming Space, on icons that are not adaptive, to find the round ones that
     * become dot balls (ui.DotBallView).
     */
    public static boolean isCircle(Bitmap icon) {
        int w = icon.getWidth();
        int h = icon.getHeight();
        int[] px = pixels(icon);
        Rect box = opaqueBounds(px, w, h);
        if (box.isEmpty()) {
            return false;
        }
        float cx = box.exactCenterX();
        float cy = box.exactCenterY();
        float r = Math.max(box.width(), box.height()) / 2f;
        int both = 0;
        int either = 0;
        for (int y = box.top; y < box.bottom; y++) {
            for (int x = box.left; x < box.right; x++) {
                boolean in = (px[y * w + x] >>> 24) >= 128;
                boolean round = Math.hypot(x + 0.5f - cx, y + 0.5f - cy) <= r;
                both += in && round ? 1 : 0;
                either += in || round ? 1 : 0;
            }
        }
        return either > 0 && both / (float) either >= CIRCLE_MATCH;
    }

    /**
     * An old-style icon the launcher drew shrunk onto a white plate, taken off it: the white
     * joined to the plate's edge (not white inside the icon itself) cleared, and the icon
     * scaled up to fill the space in its own shape. Any other icon is returned as it is.
     */
    private static Bitmap withoutWhitePlate(Bitmap icon) {
        int w = icon.getWidth();
        int h = icon.getHeight();
        int n = w * h;
        int[] px = pixels(icon);
        // The plate's edge: solid pixels next to clear ones. A white plate is white all round.
        int edge = 0;
        int white = 0;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                if ((px[i] >>> 24) >= 200 && touchesClear(px, i, w)) {
                    edge++;
                    white += nearWhite(px[i]) ? 1 : 0;
                }
            }
        }
        if (edge < w || white < edge * 0.85f) {
            return icon;
        }
        // The plate: near-white joined to the edge.
        boolean[] plate = new boolean[n];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            int x = i % w;
            int y = i / w;
            boolean border = x == 0 || y == 0 || x == w - 1 || y == h - 1
                    || touchesClear(px, i, w);
            if (border && (px[i] >>> 24) >= 100 && nearWhite(px[i])) {
                plate[i] = true;
                queue.add(i);
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = i % w;
            int[] next = {x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, i - w, i + w};
            for (int j : next) {
                if (j >= 0 && j < n && !plate[j] && (px[j] >>> 24) >= 100
                        && nearWhite(px[j])) {
                    plate[j] = true;
                    queue.add(j);
                }
            }
        }
        for (int i = 0; i < n; i++) {
            if (plate[i]) {
                px[i] = 0;
            }
        }
        Rect box = opaqueBounds(px, w, h);
        if (box.isEmpty() || Math.max(box.width(), box.height()) > w * 0.85f) {
            return icon;
        }
        // Whatever is left outside the icon's own box is the plate's soft edge.
        for (int i = 0; i < n; i++) {
            if (!box.contains(i % w, i / w)) {
                px[i] = 0;
            }
        }
        // The icon, in its own shape, scaled to fill the icon's space (as the launcher would
        // have shown it without the plate), centred.
        float fill = 0.92f * w / Math.max(box.width(), box.height());
        Bitmap bare = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        float cw = box.width() * fill;
        float ch = box.height() * fill;
        new Canvas(out).drawBitmap(bare, box, new RectF((w - cw) / 2, (h - ch) / 2,
                (w + cw) / 2, (h + ch) / 2), new Paint(Paint.FILTER_BITMAP_FLAG));
        bare.recycle();
        return out;
    }

    private static boolean touchesClear(int[] px, int i, int w) {
        return (px[i - 1] >>> 24) < 100 || (px[i + 1] >>> 24) < 100
                || (px[i - w] >>> 24) < 100 || (px[i + w] >>> 24) < 100;
    }

    private static boolean nearWhite(int c) {
        return ((c >> 16) & 0xFF) > 228 && ((c >> 8) & 0xFF) > 228 && (c & 0xFF) > 228;
    }

    private static Rect opaqueBounds(int[] px, int w, int h) {
        int x0 = w, y0 = h, x1 = -1, y1 = -1;
        for (int i = 0; i < px.length; i++) {
            if ((px[i] >>> 24) >= 128) {
                x0 = Math.min(x0, i % w);
                x1 = Math.max(x1, i % w);
                y0 = Math.min(y0, i / w);
                y1 = Math.max(y1, i / w);
            }
        }
        return x1 < 0 ? new Rect() : new Rect(x0, y0, x1 + 1, y1 + 1);
    }

    private static int[] pixels(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] px = new int[w * h];
        bitmap.getPixels(px, 0, w, 0, 0, w, h);
        return px;
    }

    private static Bitmap transformed(Bitmap bitmap, Matrix m) {
        Bitmap out = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
                Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(bitmap, m, new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    private static Bitmap toHardware(Bitmap bitmap) {
        Bitmap hw = bitmap.copy(Bitmap.Config.HARDWARE, false);
        if (hw == null) {
            return bitmap;
        }
        bitmap.recycle();
        return hw;
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static int mix(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
        int bl = Math.round((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    /**
     * Exact Euclidean distance transform (Felzenszwalb and Huttenlocher): for every pixel, how
     * far it is from the nearest marked pixel.
     */
    private static final class Distance {
        private static final float FAR = 1e10f;

        static float[] to(boolean[] marked, int w, int h) {
            float[] d = new float[w * h];
            for (int i = 0; i < d.length; i++) {
                d[i] = marked[i] ? 0 : FAR;
            }
            int longest = Math.max(w, h);
            float[] f = new float[longest];
            float[] out = new float[longest];
            int[] v = new int[longest];
            float[] z = new float[longest + 1];
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    f[y] = d[y * w + x];
                }
                pass(f, h, out, v, z);
                for (int y = 0; y < h; y++) {
                    d[y * w + x] = out[y];
                }
            }
            for (int y = 0; y < h; y++) {
                System.arraycopy(d, y * w, f, 0, w);
                pass(f, w, out, v, z);
                for (int x = 0; x < w; x++) {
                    d[y * w + x] = (float) Math.sqrt(out[x]);
                }
            }
            return d;
        }

        /** One dimension: squared distances along a line. */
        private static void pass(float[] f, int n, float[] d, int[] v, float[] z) {
            int k = 0;
            v[0] = 0;
            z[0] = -FAR;
            z[1] = FAR;
            for (int q = 1; q < n; q++) {
                float s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2f * q - 2f * v[k]);
                while (s <= z[k]) {
                    k--;
                    s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2f * q - 2f * v[k]);
                }
                k++;
                v[k] = q;
                z[k] = s;
                z[k + 1] = FAR;
            }
            k = 0;
            for (int q = 0; q < n; q++) {
                while (z[k + 1] < q) {
                    k++;
                }
                d[q] = (q - v[k]) * (q - v[k]) + f[v[k]];
            }
        }
    }
}
