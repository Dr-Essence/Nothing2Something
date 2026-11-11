package com.dressence.nothinglauncherplus.anim;

import java.util.List;

/**
 * The pieces DotEffect's effects share: timing (cycle, fade), finding parts of an emoji (eyes,
 * drops, balls, feet, jets), and the drawing several effects reuse (blink, glow, water, glints).
 * Moved out of DotEffect unchanged (0.70.12, cleanup step 4); DotEffect imports them statically,
 * so its effects call them by their short names as before.
 */
final class DotEffectParts {

    private DotEffectParts() {
    }

    // -------------------------------------------------------------------------------------
    // Shared pieces
    // -------------------------------------------------------------------------------------

    /** Position in a repeating life cycle of {@code period} seconds, 0..1. */
    static float cycle(Scene s, float period, float offset) {
        float v = ((s.t + offset + (s.seed & 0xFF) * 0.013f) % period) / period;
        return v < 0 ? v + 1 : v;
    }

    /** Fades in quickly, out slowly: for particles. */
    static float fade(float life) {
        return life < 0.15f ? life / 0.15f : 1 - (life - 0.15f) / 0.85f;
    }

    /** Overwrites the output dot at resting position (x, y), even over brighter ink. */
    static void setRest(Scene s, float x, float y, int color) {
        float ox = s.a * (x + 0.5f) + s.b * (y + 0.5f) + s.c;
        float oy = s.d * (x + 0.5f) + s.e * (y + 0.5f) + s.f;
        int ix = (int) Math.floor(ox);
        int iy = (int) Math.floor(oy);
        if (ix >= 0 && iy >= 0 && ix < s.size && iy < s.size) {
            s.dots[iy * s.size + ix] = color;
        }
    }

    /**
     * Bodies of water at least this tall (in dots) are streams that flow; shorter ones are drops
     * that slide. Measured: tears and sweat beads are 8-11 tall, a sobbing face's rivers 17.
     */
    static final int STREAM_HEIGHT = 14;

    static boolean isDrop(Regions r, int body) {
        return body >= 0 && r.bodyHeight[body] < STREAM_HEIGHT;
    }

    /**
     * Animates the emoji's own water. Small bodies (a tear, a bead of sweat) slide down,
     * glisten, fade at the end of their run and form again, leaving behind what was under them;
     * large bodies (rivers of tears) flow, with drops forming at their lowest point and falling.
     */
    static void animateWater(Scene s, float pace) {
        Regions r = s.regions;
        boolean anyDrop = false;
        for (int k = 0; k < r.bodySize.length; k++) {
            anyDrop |= isDrop(r, k);
        }
        if (anyDrop) {
            // Uncover what the drops were hiding, then draw each drop further down its path.
            for (int i = 0; i < s.dots.length; i++) {
                int ri = s.restIndex(i);
                if (ri >= 0 && (isDrop(r, r.waterBody[ri]) || isDrop(r, r.haloBody[ri]))) {
                    int behind = r.behindWater[ri];
                    s.dots[i] = behind == 0 ? 0 : Scene.alpha(behind, s.dots[i] >>> 24);
                }
            }
            for (int i = 0; i < r.size * r.size; i++) {
                int body = r.waterBody[i];
                if (!isDrop(r, body)) {
                    continue;
                }
                float life = cycle(s, 2.6f / pace, body * 0.7f);
                // Runs down the skin and fades where the skin ends; never off into the void.
                float slide = life * life * Math.min(7, Math.max(0, r.bodyRoom[body] - 1));
                float y = i / r.size + slide;
                int c = r.rest[i];
                float shine = (float) Math.max(0, Math.sin((i / r.size) * 1.1f - s.t * 7));
                c = Scene.mix(c, 0xFFFFFFFF, 0.45f * shine * shine);
                // Forms and vanishes dot by dot, the way an LED panel shows it: no colour
                // blending, so blue water never smears green into yellow skin.
                float visible = life < 0.75f ? 1 : (1 - life) / 0.25f;
                float formed = Math.min(1, life / 0.1f);
                if (Scene.hash(i, body, s.seed) >= visible * formed) {
                    continue;
                }
                // Water that overhangs the outline only shows where it started: once the drop
                // runs, it stays on the emoji instead of leaving dots floating beside it.
                int ty = Math.round(y);
                if (slide >= 0.5f && !r.onEmoji(i % r.size, ty)) {
                    continue;
                }
                setRest(s, i % r.size, y, c);
            }
        }
        flowWater(s, 6 * pace);
        for (int k = 0; k < r.bodySize.length && k < 4; k++) {
            if (isDrop(r, k)) {
                continue;
            }
            float life = cycle(s, 1.3f / pace, k * 0.41f);
            float y = r.bodyLowY[k] + 1 + life * life * (r.size - r.bodyLowY[k]);
            s.plotRest(r.bodyLowX[k], y, Scene.alpha(0xFF8FDBFF, Math.round(240 * fade(life))));
        }
    }

    /**
     * Beads of sweat, each lifted whole off the face: it swells out of the skin, hangs a moment,
     * runs down as far as the skin goes, gathering speed, and fades, leaving the skin under it.
     */
    static void sweatDrops(Scene s) {
        Regions r = s.regions;
        int bodies = r.bodySize.length;
        if (r.waterCount == 0 || bodies == 0 || bodies > Scene.MAX_PARTS) {
            return;
        }
        for (int k = 0; k < bodies; k++) {
            if (!isDrop(r, k)) {
                return;
            }
        }
        int[] box = new int[bodies * 4];
        for (int k = 0; k < bodies; k++) {
            box[k * 4] = r.size;
            box[k * 4 + 1] = r.size;
        }
        for (int i = 0; i < r.size * r.size; i++) {
            int k = r.waterBody[i];
            if (k >= 0) {
                int x = i % r.size;
                int y = i / r.size;
                box[k * 4] = Math.min(box[k * 4], x);
                box[k * 4 + 1] = Math.min(box[k * 4 + 1], y);
                box[k * 4 + 2] = Math.max(box[k * 4 + 2], x + 1);
                box[k * 4 + 3] = Math.max(box[k * 4 + 3], y + 1);
            }
        }
        // Only the clear sweat blue: a pale or bluish face is not taken along with it.
        s.liftHue(199, 22, 0.4f);
        float period = 3.6f / (1 + 0.6f * s.boost);
        for (int k = 0; k < bodies; k++) {
            float life = cycle(s, period, k * 1.3f);
            float x0 = box[k * 4] - 1;
            float y0 = box[k * 4 + 1] - 1;
            float x1 = box[k * 4 + 2] + 1;
            float y1 = box[k * 4 + 3] + 1;
            // Down the skin as far as it goes; a bead at the jaw drips off it.
            float run = Math.min(6, Math.max(3, r.bodyRoom[k] - 1));
            float grow;
            float slide;
            float alpha;
            if (life < 0.15f) {
                grow = ObjectEffects.easeOut(life / 0.15f);
                slide = 0;
                alpha = Math.min(1, life / 0.08f);
            } else if (life < 0.35f) {
                grow = 1;
                slide = 0;
                alpha = 1;
            } else if (life < 0.85f) {
                float q = (life - 0.35f) / 0.5f;
                grow = 1;
                slide = run * (float) Math.pow(q, 1.7);
                alpha = q < 0.7f ? 1 : 1 - (q - 0.7f) / 0.3f;
            } else {
                continue;
            }
            s.addLayerPiece(1, x0, y0, x1, y1, 0, slide);
            int p = s.partCount - 1;
            if (p >= 0 && s.partLayer[p] == 1) {
                // It swells from where it clings, at its top.
                s.partPivot[p * 2] = (x0 + x1) / 2;
                s.partPivot[p * 2 + 1] = y0 + 1;
                s.partScale[p] = 0.45f + 0.55f * grow;
                s.partAlpha[p] = alpha;
            }
        }
        s.busy = true;
    }

    static void blowKiss(Scene s) {
        float x = MusicEffects.ax(s, 0);
        float y = MusicEffects.ay(s, 1);
        float life = cycle(s, 3.2f, 0);
        if (life > 0.75f) {
            return;
        }
        float q = life / 0.75f;
        float rise = ObjectEffects.easeOut(q);
        // Out from the lips, up and to the side, swaying a little as it floats.
        float hx = x + 1 + 8 * rise + 0.7f * (float) Math.sin(q * Math.PI * 2.4f);
        float hy = y - 8 * rise;
        // Fades out before it would reach the frame's edge.
        hx = Math.min(hx, s.size - 3.5f);
        float across = 2.4f + 2.6f * ObjectEffects.easeOut(Math.min(1, q / 0.3f));
        float strength = q < 0.12f ? q / 0.12f : q > 0.6f ? (1 - q) / 0.4f : 1;
        s.heart(hx, hy, across, 0xFFF2384F, strength);
        s.busy = true;
    }

    /** Light gliding along the front of a lifted halo now and then. */
    static void haloGlint(Scene s) {
        Regions r = s.regions;
        float x0 = r.size, x1 = -1, y0 = r.size, y1 = -1;
        for (int i = 0; i < r.size * r.size; i++) {
            if (r.ink[i] && r.sat[i] > 0.3f && Math.abs(r.hue[i] - 197) < 25) {
                x0 = Math.min(x0, i % r.size);
                x1 = Math.max(x1, i % r.size + 1);
                y0 = Math.min(y0, i / r.size);
                y1 = Math.max(y1, i / r.size + 1);
            }
        }
        float local = (s.t + 1.1f) % 4.2f;
        if (x1 < 0 || local > 1.4f) {
            return;
        }
        float q = ObjectEffects.ease(local / 1.4f);
        float a = (float) Math.PI * (1 - q);
        float bob = (float) Math.sin(s.t * Math.PI * 2 / 2.8f);
        float gx = (x0 + x1) / 2 + (float) Math.cos(a) * ((x1 - x0) / 2 - 1);
        float gy = (y0 + y1) / 2 + (float) Math.sin(a) * ((y1 - y0) / 2 - 0.8f) - 0.6f - 0.9f * bob;
        s.glint(gx, gy, 1.6f, 0xFFFFFFFF, (float) Math.sin(q * Math.PI));
        s.busy = true;
    }

    /** Where the eyes look, in turn: ahead, left, ahead, right, a little up; seconds each. */
    static final int[][] GAZE = {{0, 0}, {-1, 0}, {0, 0}, {1, 0}, {0, -1}, {1, 0}};
    static final float[] GAZE_HOLD = {1.4f, 0.9f, 0.7f, 1.0f, 0.8f, 0.6f};

    /**
     * Eyes with whites (👀): the pupils dart from one place to the next as real eyes do, a
     * whole dot or two at a time, never leaving the white. False if the eyes have no whites.
     */
    static boolean glance(Scene s) {
        Regions r = s.regions;
        int[][] eyes = (int[][]) r.memo.get("eyeWhites");
        if (eyes == null) {
            eyes = eyeWhites(r);
            r.memo.put("eyeWhites", eyes);
        }
        if (eyes.length == 0) {
            return false;
        }
        float total = 0;
        for (float h : GAZE_HOLD) {
            total += h;
        }
        float t = (s.t + Scene.hash(s.seed, 3, 3) * total) % total;
        int step = 0;
        // Never past the last step: float rounding can leave t a hair above the last hold.
        while (step < GAZE_HOLD.length - 1 && t > GAZE_HOLD[step]) {
            t -= GAZE_HOLD[step];
            step++;
        }
        int[] want = GAZE[step];
        // As far as every pupil can go that way, one or two dots.
        int dx = 0;
        int dy = 0;
        for (int reach = 2; reach >= 1 && dx == 0 && dy == 0; reach--) {
            if (fits(r, eyes, want[0] * reach, want[1] * reach)) {
                dx = want[0] * reach;
                dy = want[1] * reach;
            }
        }
        // eyes[0]: per dot, which eye's white it is in (or -1); eyes[1]: pupil dots; eyes[2]:
        // the colour of each eye's white.
        for (int i = 0; i < s.dots.length; i++) {
            int ri = s.restIndex(i);
            if (ri < 0 || eyes[0][ri] < 0) {
                continue;
            }
            int x = ri % r.size;
            int y = ri / r.size;
            int sx = x - dx;
            int sy = y - dy;
            int from = sx >= 0 && sy >= 0 && sx < r.size && sy < r.size ? sy * r.size + sx : -1;
            int c;
            if (from >= 0 && eyes[1][from] == 1 && eyes[0][from] == eyes[0][ri]) {
                c = r.rest[from];
            } else if (eyes[1][ri] == 1) {
                c = eyes[2][eyes[0][ri]];
            } else {
                continue;
            }
            s.dots[i] = (c & 0xFFFFFF) | (s.dots[i] & 0xFF000000);
        }
        return true;
    }

    /** True if every pupil moved by dx, dy stays inside its white. */
    static boolean fits(Regions r, int[][] eyes, int dx, int dy) {
        for (int i = 0; i < r.size * r.size; i++) {
            if (eyes[1][i] != 1) {
                continue;
            }
            int x = i % r.size + dx;
            int y = i / r.size + dy;
            if (x < 0 || y < 0 || x >= r.size || y >= r.size
                    || eyes[0][y * r.size + x] != eyes[0][i]) {
                return false;
            }
        }
        return true;
    }

    /** The whites round the dark pupils (see glance); empty if a pupil has none. */
    static int[][] eyeWhites(Regions r) {
        int n = r.size * r.size;
        int[] owner = new int[n];
        int[] pupil = new int[n];
        java.util.Arrays.fill(owner, -1);
        java.util.List<Integer> whites = new java.util.ArrayList<>();
        boolean[] seen = new boolean[n];
        int count = 0;
        for (int start = 0; start < n; start++) {
            if (!r.eyes[start] || seen[start]) {
                continue;
            }
            java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
            java.util.List<Integer> comp = new java.util.ArrayList<>();
            queue.add(start);
            seen[start] = true;
            while (!queue.isEmpty()) {
                int i = queue.poll();
                comp.add(i);
                for (int j : around(r, i)) {
                    if (r.eyes[j] && !seen[j]) {
                        seen[j] = true;
                        queue.add(j);
                    }
                }
            }
            // The white: light dots joined to the pupil.
            java.util.List<Integer> white = new java.util.ArrayList<>();
            boolean[] in = new boolean[n];
            for (int i : comp) {
                in[i] = true;
            }
            queue.clear();
            for (int i : comp) {
                for (int j : around(r, i)) {
                    if (!in[j] && r.ink[j] && r.luma[j] > 0.7f && r.sat[j] < 0.25f) {
                        in[j] = true;
                        queue.add(j);
                    }
                }
            }
            while (!queue.isEmpty()) {
                int i = queue.poll();
                white.add(i);
                for (int j : around(r, i)) {
                    if (!in[j] && r.ink[j] && r.luma[j] > 0.6f && r.sat[j] < 0.25f) {
                        in[j] = true;
                        queue.add(j);
                    }
                }
            }
            if (white.size() < comp.size()) {
                return new int[0][];
            }
            // The light caught in the pupil goes with it: light dots with pupil on both sides.
            java.util.List<Integer> caught = new java.util.ArrayList<>();
            for (int i : white) {
                boolean left = false, right = false, up = false, down = false;
                int x = i % r.size;
                int y = i / r.size;
                for (int d = 1; d <= 3; d++) {
                    left |= x - d >= 0 && comp.contains(y * r.size + x - d);
                    right |= x + d < r.size && comp.contains(y * r.size + x + d);
                    up |= y - d >= 0 && comp.contains((y - d) * r.size + x);
                    down |= y + d < r.size && comp.contains((y + d) * r.size + x);
                }
                if (left && right || up && down) {
                    caught.add(i);
                }
            }
            white.removeAll(caught);
            comp.addAll(caught);
            if (white.isEmpty()) {
                return new int[0][]; // All of it was light in the pupil: no white to look in.
            }
            long cr = 0, cg = 0, cb = 0;
            for (int i : white) {
                int c = r.rest[i];
                cr += (c >> 16) & 0xFF;
                cg += (c >> 8) & 0xFF;
                cb += c & 0xFF;
            }
            int m = white.size();
            whites.add(0xFF000000 | (int) (cr / m) << 16 | (int) (cg / m) << 8 | (int) (cb / m));
            for (int i : white) {
                owner[i] = count;
            }
            for (int i : comp) {
                owner[i] = count;
                pupil[i] = 1;
            }
            count++;
        }
        if (count == 0) {
            return new int[0][];
        }
        int[] colours = new int[count];
        for (int k = 0; k < count; k++) {
            colours[k] = whites.get(k);
        }
        return new int[][] {owner, pupil, colours};
    }

    static int[] around(Regions r, int i) {
        int x = i % r.size;
        int y = i / r.size;
        int[] out = new int[8];
        int k = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < r.size && ny < r.size) {
                    out[k++] = ny * r.size + nx;
                }
            }
        }
        return java.util.Arrays.copyOf(out, k);
    }

    static final float HIT_PERIOD = 3.0f;

    static void hitPose(Scene s) {
        float[] a = s.anchor;
        if (a == null || a.length < 8) {
            return;
        }
        float bx = MusicEffects.ax(s, 0);
        float by = MusicEffects.ay(s, 1);
        float gx = MusicEffects.ax(s, 2);
        float gy = MusicEffects.ay(s, 3);
        float t = s.since < HIT_PERIOD ? s.since : (s.t + 0.4f) % HIT_PERIOD;
        // The swing: back, fast through the ball, follow through, settle.
        float back = a[4];
        float through = a[5];
        float angle;
        final float wind = 0.55f;
        final float strike = 0.14f;
        final float contact = wind + strike * 0.6f;
        if (t < wind) {
            angle = back * ObjectEffects.ease(t / wind);
        } else if (t < wind + strike) {
            float q = (t - wind) / strike;
            angle = back + (through - back) * q * q;
        } else if (t < wind + strike + 0.9f) {
            angle = through * (1 - ObjectEffects.ease((t - wind - strike) / 0.9f));
        } else {
            angle = 0;
        }
        s.liftPiece(bx, by);
        s.addPart(0, 0, s.size, s.size, gx, gy, angle, 0, 0);
        // The ball: still until struck, then away, then a new one appears in its place.
        float fly = t < contact ? 0 : Math.min(1, (t - contact) / 0.55f);
        if (fly < 1) {
            float go = ObjectEffects.easeOut(fly);
            float alpha = fly < 0.6f ? 1 : 1 - (fly - 0.6f) / 0.4f;
            s.addLayer(1, bx, by, 0, a[6] * go, a[7] * go + 3 * fly * fly, alpha);
            int k = s.partCount - 1;
            if (k >= 0 && s.partLayer[k] == 1) {
                s.partScale[k] = 1 - 0.3f * go;
            }
        } else {
            float back2 = t - (wind + strike + 1.0f);
            if (back2 > 0) {
                float q = Math.min(1, back2 / 0.35f);
                s.addLayer(1, bx, by, 0, 0, 0, q);
                int k = s.partCount - 1;
                if (k >= 0 && s.partLayer[k] == 1) {
                    s.partScale[k] = 0.6f + 0.4f * ObjectEffects.easeOut(q);
                }
            }
        }
        s.busy = t < wind + strike + 1.4f;
    }

    static void shower(Scene s) {
        Regions r = s.regions;
        float[][] jets = (float[][]) r.memo.get("jets");
        int[] owner = (int[]) r.memo.get("jetOwner");
        if (jets == null) {
            owner = new int[r.size * r.size];
            jets = findJets(r, owner);
            r.memo.put("jets", jets);
            r.memo.put("jetOwner", owner);
        }
        if (jets.length == 0) {
            DotEffect.RAIN.apply(s);
            return;
        }
        for (int i = 0; i < s.dots.length; i++) {
            int ri = s.restIndex(i);
            if (ri < 0 || owner[ri] < 0) {
                continue;
            }
            float[] j = jets[owner[ri]];
            int k = owner[ri];
            float dx = j[2] - j[0];
            float dy = j[3] - j[1];
            float len = Math.max(1, (float) Math.sqrt(dx * dx + dy * dy));
            float f = ((ri % r.size - j[0]) * dx + (ri / r.size - j[1]) * dy) / (len * len);
            // The water breaks up at the end, the jet now longer, now shorter.
            float reach = 0.78f + 0.2f * (float) Math.sin(s.t * 17 + k * 2.1f)
                    * (float) Math.sin(s.t * 6.3f + k);
            float a = f < reach ? 1 : Math.max(0, 1 - (f - reach) / 0.15f);
            // Pulses of light rushing out along it.
            float w = (float) Math.sin(f * len * 1.1f - s.t * 18 + k * 1.7f);
            int c = s.dots[i];
            if (w > 0.4f) {
                c = Scene.mix(c, 0xFFE8FBFF, 0.55f * (w - 0.4f) / 0.6f);
            }
            s.dots[i] = Scene.alpha(c, Math.round((s.dots[i] >>> 24) * a));
        }
        // Drops thrown off each jet's end, on the way the water goes, falling.
        for (int k = 0; k < jets.length; k++) {
            float[] j = jets[k];
            float dx = j[2] - j[0];
            float dy = j[3] - j[1];
            float len = Math.max(1, (float) Math.sqrt(dx * dx + dy * dy));
            if (j[5] < 6) {
                continue; // A broken-off bit of spray, not a jet.
            }
            for (int n = 0; n < 1; n++) {
                float life = cycle(s, 0.55f, k * 0.19f + n * 0.27f);
                float go = 1.5f + 4 * life;
                float x = j[2] + dx / len * go;
                float y = j[3] + dy / len * go + 2.5f * life * life;
                s.blob(x + 0.5f, y + 0.5f, 0.55f, 0xFF000000 | (int) j[4], 0.85f * (1 - life * life));
            }
        }
        s.busy = true;
    }

    /**
     * The jets of water: thin blue streaks. Per jet its start (nearest the head, the side of
     * the drawing's middle), its end and its colour; {@code owner} gets each dot's jet.
     */
    static float[][] findJets(Regions r, int[] owner) {
        int n = r.size * r.size;
        java.util.Arrays.fill(owner, -1);
        boolean[] seen = new boolean[n];
        float cx = (r.minX + r.maxX) / 2f;
        float cy = (r.minY + r.maxY) / 2f;
        java.util.List<float[]> jets = new java.util.ArrayList<>();
        for (int start = 0; start < n; start++) {
            if (seen[start] || !jetDot(r, start)) {
                continue;
            }
            java.util.List<Integer> comp = new java.util.ArrayList<>();
            java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
            queue.add(start);
            seen[start] = true;
            while (!queue.isEmpty()) {
                int i = queue.poll();
                comp.add(i);
                for (int j : around(r, i)) {
                    if (!seen[j] && jetDot(r, j)) {
                        seen[j] = true;
                        queue.add(j);
                    }
                }
            }
            if (comp.size() < 3) {
                continue;
            }
            int bright = comp.get(0);
            for (int p : comp) {
                if (r.luma[p] * r.sat[p] > r.luma[bright] * r.sat[bright]) {
                    bright = p;
                }
            }
            // Its two ends: the dots farthest apart; the start is the one nearer the middle.
            int a = comp.get(0);
            int b = a;
            float best = -1;
            for (int p : comp) {
                for (int q : comp) {
                    float ddx = p % r.size - q % r.size;
                    float ddy = p / r.size - q / r.size;
                    if (ddx * ddx + ddy * ddy > best) {
                        best = ddx * ddx + ddy * ddy;
                        a = p;
                        b = q;
                    }
                }
            }
            float da = (a % r.size - cx) * (a % r.size - cx) + (a / r.size - cy) * (a / r.size - cy);
            float db = (b % r.size - cx) * (b % r.size - cx) + (b / r.size - cy) * (b / r.size - cy);
            if (db < da) {
                int t = a;
                a = b;
                b = t;
            }
            for (int p : comp) {
                owner[p] = jets.size();
            }
            jets.add(new float[] {a % r.size, a / r.size, b % r.size, b / r.size,
                    r.rest[bright] & 0xFFFFFF, comp.size()});
        }
        return jets.toArray(new float[0][]);
    }

    static boolean jetDot(Regions r, int i) {
        return r.ink[i] && r.hue[i] > 180 && r.hue[i] < 215 && r.sat[i] > 0.4f;
    }

    static void fizz(Scene s, int count) {
        Regions r = s.regions;
        // Per column, the run of drink (coloured, not glass or foam): lowest and highest row.
        int[] runs = (int[]) r.memo.get("drink");
        if (runs == null) {
            runs = new int[r.size * 2];
            for (int x = 0; x < r.size; x++) {
                // The longest unbroken run in the column.
                int lo = -1;
                int hi = -1;
                for (int y = 0; y < r.size; y++) {
                    if (!drink(r, y * r.size + x)) {
                        continue;
                    }
                    int end = y;
                    while (end + 1 < r.size && drink(r, (end + 1) * r.size + x)) {
                        end++;
                    }
                    if (end - y > lo - hi) {
                        hi = y;
                        lo = end;
                    }
                    y = end;
                }
                runs[x * 2] = lo - hi >= 3 ? lo : -1;
                runs[x * 2 + 1] = hi;
            }
            r.memo.put("drink", runs);
        }
        int columns = 0;
        for (int x = 0; x < r.size; x++) {
            columns += runs[x * 2] >= 0 ? 1 : 0;
        }
        if (columns == 0) {
            DotEffect.SHINE.apply(s);
            return;
        }
        for (int k = 0; k < count; k++) {
            float period = 1.4f + 0.5f * Scene.hash(k, 3, s.seed);
            float life = cycle(s, period, k * 0.37f);
            int cyc = (int) ((s.t + k * 0.37f + (s.seed & 0xFF) * 0.013f) / period);
            int pick = (int) (Scene.hash(k, cyc, s.seed) * columns);
            int x = 0;
            while (runs[x * 2] < 0 || pick-- > 0) {
                x++;
            }
            int bottom = runs[x * 2] - 1;
            int top = runs[x * 2 + 1];
            int y = Math.round(bottom - (bottom - top) * life * life);
            float env = Math.min(1, life / 0.15f) * (life > 0.9f ? (1 - life) / 0.1f : 1);
            // A dot of light, on the drink only: never spilling past the glass.
            if (y >= top && drink(r, y * r.size + x)) {
                s.paintRest(x, y, Scene.alpha(0xFFFFFFF0, Math.round(230 * env)));
            }
        }
        s.busy = true;
    }

    /** A dot of the drink itself: clearly coloured, unlike glass, foam or a stand. */
    static boolean drink(Regions r, int i) {
        return r.ink[i] && r.sat[i] > 0.4f && r.luma[i] > 0.2f;
    }

    static void juggle(Scene s) {
        Regions r = s.regions;
        int[][] balls = (int[][]) r.memo.get("balls");
        if (balls == null) {
            balls = findBalls(r);
            r.memo.put("balls", balls);
        }
        if (balls.length < 2) {
            DotEffect.SHINE.apply(s);
            return;
        }
        // Take the balls out of the picture: what they hid is open air.
        for (int i = 0; i < s.dots.length; i++) {
            int ri = s.restIndex(i);
            if (ri >= 0 && balls[0][ri] >= 0) {
                s.dots[i] = 0;
            }
        }
        int count = balls.length - 1;
        float top = r.size;
        float low = 0;
        for (int k = 1; k <= count; k++) {
            top = Math.min(top, balls[k][1] / 10f);
            low = Math.max(low, balls[k][1] / 10f);
        }
        float cx = (r.minX + r.maxX + 1) / 2f;
        float rx = (r.maxX - r.minX + 1) / 2f - 2.5f;
        float cy = (top + low + 3) / 2;
        float ry = Math.max(3, (low + 3 - top) / 2);
        float turn = (s.t / (2.4f / (1 + 0.8f * s.boost))) % 1;
        for (int k = 1; k <= count; k++) {
            float a = (float) (Math.PI * 2 * (turn + (k - 1f) / count));
            // Up the right, over the top, down the left: counter-clockwise on screen.
            float x = cx + rx * (float) Math.cos(a);
            float y = cy - ry * (float) Math.sin(a);
            boolean behind = Math.sin(a) < 0;
            float radius = balls[k][2] / 10f;
            s.ball(x, y, radius, balls[k][3], behind);
            if (!behind) {
                // The shine on it, up and to the left.
                s.ball(x - radius * 0.35f, y - radius * 0.35f, 0.6f, 0xFFFFFFFF, false);
            }
        }
        s.busy = true;
    }

    /**
     * The balls in the air: small, saturated, not skin-coloured blobs. Row 0: per dot, which
     * ball it is (or -1); then per ball: x, y (tenths of a dot), radius (tenths), colour.
     */
    static int[][] findBalls(Regions r) {
        int n = r.size * r.size;
        int[] owner = new int[n];
        java.util.Arrays.fill(owner, -1);
        boolean[] seen = new boolean[n];
        java.util.List<int[]> found = new java.util.ArrayList<>();
        for (int start = 0; start < n; start++) {
            if (seen[start] || !ballDot(r, start)) {
                continue;
            }
            java.util.List<Integer> comp = new java.util.ArrayList<>();
            java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
            queue.add(start);
            seen[start] = true;
            while (!queue.isEmpty()) {
                int i = queue.poll();
                comp.add(i);
                for (int j : around(r, i)) {
                    if (!seen[j] && ballDot(r, j)
                            && Math.abs(r.hue[j] - r.hue[start]) < 30) {
                        seen[j] = true;
                        queue.add(j);
                    }
                }
            }
            if (comp.size() < 3 || comp.size() > 16) {
                continue;
            }
            float sx = 0, sy = 0;
            int best = comp.get(0);
            for (int i : comp) {
                sx += i % r.size + 0.5f;
                sy += i / r.size + 0.5f;
                if (r.sat[i] * (1 - Math.abs(r.luma[i] - 0.5f)) > r.sat[best]
                        * (1 - Math.abs(r.luma[best] - 0.5f))) {
                    best = i;
                }
            }
            int k = found.size() + 1;
            for (int i : comp) {
                owner[i] = k - 1;
            }
            // Its own pure colour (not the highlight), a radius from its area.
            found.add(new int[] {Math.round(sx / comp.size() * 10), Math.round(sy / comp.size()
                    * 10), Math.round((float) Math.sqrt(comp.size() / Math.PI) * 10 + 2),
                    r.rest[best] | 0xFF000000});
        }
        // The rim and shine round each ball go with it: whatever touches a ball and is not
        // part of the juggler (the largest piece of the picture without the balls).
        int[] body = new int[n];
        int biggest = 0;
        int label = 0;
        int bodyLabel = -1;
        for (int start = 0; start < n; start++) {
            if (body[start] != 0 || !r.ink[start] || owner[start] >= 0) {
                continue;
            }
            label++;
            int size = 0;
            java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
            queue.add(start);
            body[start] = label;
            while (!queue.isEmpty()) {
                int i = queue.poll();
                size++;
                for (int j : around(r, i)) {
                    if (body[j] == 0 && r.ink[j] && owner[j] < 0) {
                        body[j] = label;
                        queue.add(j);
                    }
                }
            }
            if (size > biggest) {
                biggest = size;
                bodyLabel = label;
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            int[] was = owner.clone();
            for (int i = 0; i < n; i++) {
                if (was[i] >= 0 || !r.ink[i] || body[i] == bodyLabel) {
                    continue;
                }
                for (int j : around(r, i)) {
                    if (was[j] >= 0) {
                        owner[i] = was[j];
                        break;
                    }
                }
            }
        }
        int[][] out = new int[found.size() + 1][];
        out[0] = owner;
        for (int k = 0; k < found.size(); k++) {
            out[k + 1] = found.get(k);
        }
        // Ball k's dots carry owner k - 1; rows from 1 on are balls in the same order.
        return out;
    }

    static boolean ballDot(Regions r, int i) {
        return r.ink[i] && r.sat[i] > 0.45f && (r.hue[i] < 25 || r.hue[i] > 65)
                && r.luma[i] > 0.15f;
    }

    static int hsvToRgb(float[] hsv) {
        float h = hsv[0] / 60;
        float c = hsv[2] * hsv[1];
        float x = c * (1 - Math.abs(h % 2 - 1));
        float m = hsv[2] - c;
        float r, g, b;
        int k = (int) h;
        r = k == 0 || k == 5 ? c : k == 1 || k == 4 ? x : 0;
        g = k == 1 || k == 2 ? c : k == 0 || k == 3 ? x : 0;
        b = k == 3 || k == 4 ? c : k == 2 || k == 5 ? x : 0;
        return 0xFF000000 | Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8
                | Math.round((b + m) * 255);
    }

    /**
     * A walker's feet below row {@code hipY}: the two biggest blue pieces, as the left one's
     * x, y and the right one's (lowest point of each, where it meets the ground); null if
     * there are not two.
     */
    static float[] feet(Regions r, float hipY) {
        float[] cached = (float[]) r.memo.get("feet");
        if (cached != null) {
            return cached.length == 0 ? null : cached;
        }
        int n = r.size * r.size;
        boolean[] seen = new boolean[n];
        java.util.List<float[]> found = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (seen[i] || !foot(r, i, hipY)) {
                continue;
            }
            java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
            q.add(i);
            seen[i] = true;
            float sx = 0;
            float sy = 0;
            int count = 0;
            while (!q.isEmpty()) {
                int j = q.poll();
                sx += j % r.size + 0.5f;
                sy += j / r.size + 0.5f;
                count++;
                for (int k : around(r, j)) {
                    if (!seen[k] && foot(r, k, hipY)) {
                        seen[k] = true;
                        q.add(k);
                    }
                }
            }
            found.add(new float[] {sx / count, sy / count, count});
        }
        found.sort((p, q) -> Float.compare(q[2], p[2]));
        float[] result = new float[0];
        if (found.size() >= 2) {
            float[] p = found.get(0);
            float[] q = found.get(1);
            if (q[0] < p[0]) {
                float[] t = p;
                p = q;
                q = t;
            }
            result = new float[] {p[0], p[1], q[0], q[1]};
        }
        r.memo.put("feet", result);
        return result.length == 0 ? null : result;
    }

    static boolean foot(Regions r, int i, float hipY) {
        return r.ink[i] && i / r.size > hipY && r.hue[i] > 190 && r.hue[i] < 250
                && r.sat[i] > 0.3f;
    }

    /** Light running down large water (small drops get their own shine as they slide). */
    static void flowWater(Scene s, float speed) {
        Regions r = s.regions;
        for (int i = 0; i < s.dots.length; i++) {
            int ri = s.restIndex(i);
            if (ri < 0 || r.waterBody[ri] < 0 || isDrop(r, r.waterBody[ri])) {
                continue;
            }
            int x = ri % r.size;
            int y = ri / r.size;
            float w = (float) Math.sin(y * 0.85f - s.t * speed + x * 0.35f);
            if (w > 0.35f) {
                float k = (w - 0.35f) / 0.65f;
                s.dots[i] = Scene.alpha(Scene.mix(s.dots[i], 0xFFE8FBFF, 0.65f * k * k),
                        s.dots[i] >>> 24);
            }
        }
    }

    /** Drops forming at the lowest point of each body of water and falling. */
    static void drips(Scene s, float period) {
        Regions r = s.regions;
        for (int k = 0; k < r.bodySize.length && k < 4; k++) {
            float life = cycle(s, period, k * 0.41f);
            float y = r.bodyLowY[k] + 1 + life * life * (r.size - r.bodyLowY[k]);
            s.plotRest(r.bodyLowX[k], y, Scene.alpha(0xFF8FDBFF, Math.round(240 * fade(life))));
        }
    }

    /** A face without drawn water: a tear runs from an eye. */
    static void fallbackTear(Scene s) {
        Regions r = s.regions;
        float[][] eyes = eyeCentres(r);
        float life = cycle(s, 2.2f, 0);
        float y = eyes[1][1] + 1 + life * (r.size - eyes[1][1]);
        s.plotRest(eyes[1][0], y, Scene.alpha(0xFF66CCFF, Math.round(230 * fade(life))));
    }

    static final float BLINK_TIME = 0.15f;

    static void blink(Scene s) {
        Regions r = s.regions;
        if (r.openEyes.isEmpty() || r.lidColor == 0) {
            return;
        }
        float period = 3.4f + 2.2f * Scene.hash(s.seed, 5, 5);
        // A reaction is a quick double blink; otherwise one now and then.
        float p = s.since < 0.45f ? s.since % 0.28f : s.t % period;
        if (p >= BLINK_TIME) {
            return;
        }
        // Lid half-way, shut, half-way: a real blink, not a cut.
        float q = p / BLINK_TIME;
        float shut = q < 0.25f || q > 0.75f ? 0.5f : 1;
        for (int i = 0; i < s.dots.length; i++) {
            int ri = s.restIndex(i);
            if (ri < 0) {
                continue;
            }
            int x = ri % r.size;
            int y = ri / r.size;
            for (int[] eye : r.openEyes) {
                if (x < eye[0] - 1 || x > eye[2] + 1 || y < eye[1] - 1 || y > eye[3] + 1) {
                    continue;
                }
                int lid = eye[5] != 0 ? eye[5] : r.lidColor;
                float lidLuma = Regions.luma(lid);
                int h = eye[3] - eye[1] + 1;
                int w = eye[2] - eye[0] + 1;
                float cy = (eye[1] + eye[3]) / 2f;
                if (shut >= 1) {
                    // Shut: a lash line a little wider than the eye, curving down at the ends
                    // like a closed eye drawn as "‿".
                    int lash = Math.round(cy + h * 0.2f);
                    int from = eye[0] - (w >= 3 ? 1 : 0);
                    int to = eye[2] + (w >= 3 ? 1 : 0);
                    boolean end = x == from || x == to;
                    if (x >= from && x <= to && y == (end ? lash - 1 : lash)) {
                        s.dots[i] = Scene.alpha(eye[4], 255);
                        break;
                    }
                }
                // The eye, its soft rim and its highlight: everything that isn't plain skin.
                boolean part = r.eyes[ri] || (r.ink[ri]
                        && Math.abs(r.luma[ri] - lidLuma) > 0.08f);
                // Half-way the lid covers the top 40%; shut, all of it.
                if (part && (shut >= 1 || y < eye[1] + h * 0.4f)) {
                    s.dots[i] = Scene.alpha(lid, 255);
                }
                break;
            }
        }
    }

    /**
     * A soft halo of the emoji's own light round its outline, and its bright parts lifted a
     * little, by {@code k} (0..1); nothing is washed out.
     */
    static void glow(Scene s, float k) {
        Regions r = s.regions;
        k = Math.min(1, k + s.boost);
        // Its own light: the brightest colour it has, lifted toward white.
        int light = Scene.mix(r.brightest != 0 ? r.brightest : 0xFFFFFFFF, 0xFFFFFFFF, 0.25f);
        float[] near = haloDistance(r);
        for (int i = 0; i < s.dots.length; i++) {
            int ri = s.restIndex(i);
            if (ri < 0) {
                continue;
            }
            if (r.ink[ri]) {
                if (r.luma[ri] > 0.55f) {
                    s.dots[i] = Scene.alpha(Scene.mix(s.dots[i], 0xFFFFFFFF, 0.14f * k),
                            s.dots[i] >>> 24);
                }
            } else if (near[ri] < 2.6f) {
                float f = 1 - near[ri] / 2.6f;
                s.light(i, light, 0.42f * k * f * f);
            }
        }
    }

    /** Per resting dot: how far it is from the nearest inked dot (up to 3). */
    static float[] haloDistance(Regions r) {
        float[] cached = (float[]) r.memo.get("haloDistance");
        if (cached != null) {
            return cached;
        }
        int n = r.size * r.size;
        float[] d = new float[n];
        for (int i = 0; i < n; i++) {
            if (r.ink[i]) {
                continue;
            }
            float best = 9;
            int x = i % r.size;
            int y = i / r.size;
            for (int oy = -3; oy <= 3; oy++) {
                for (int ox = -3; ox <= 3; ox++) {
                    if (r.in(x + ox, y + oy) && r.ink[(y + oy) * r.size + x + ox]) {
                        best = Math.min(best, (float) Math.sqrt(ox * ox + oy * oy));
                    }
                }
            }
            d[i] = best;
        }
        r.memo.put("haloDistance", d);
        return d;
    }

    /** Star glints just outside the emoji's outline, fading in and out. */
    static void twinkleAround(Scene s, int count) {
        Regions r = s.regions;
        int color = r.brightest != 0 ? Scene.mix(r.brightest, 0xFFFFFFFF, 0.6f) : 0xFFFFFFFF;
        for (int k = 0; k < count; k++) {
            float life = cycle(s, 1.9f, k * 0.71f);
            int cyc = (int) ((s.t + k * 0.71f + (s.seed & 0xFF) * 0.013f) / 1.9f);
            float rx = (r.maxX - r.minX) / 2f + 1.5f;
            float ry = (r.maxY - r.minY) / 2f + 1.5f;
            // Only where a whole star fits, in the clear: never cut off at the frame's edge.
            float x = -1;
            float y = -1;
            for (int tryNo = 0; tryNo < 8 && x < 0; tryNo++) {
                float angle = Scene.hash(k + 7 * tryNo, cyc, s.seed) * (float) (Math.PI * 2);
                float cx = (r.minX + r.maxX) / 2f + (float) Math.cos(angle) * rx;
                float cy = (r.minY + r.maxY) / 2f + (float) Math.sin(angle) * ry;
                int gx = Math.round(cx - 0.5f);
                int gy = Math.round(cy - 0.5f);
                boolean clear = gx >= 2 && gy >= 2 && gx < r.size - 2 && gy < r.size - 2;
                for (int dy = -2; dy <= 2 && clear; dy++) {
                    for (int dx = -2; dx <= 2 && clear; dx++) {
                        clear = !r.ink[(gy + dy) * r.size + gx + dx];
                    }
                }
                if (clear) {
                    x = cx;
                    y = cy;
                }
            }
            if (x < 0) {
                continue;
            }
            float env = (float) Math.sin(life * Math.PI);
            env *= env;
            s.glint(x, y, 0.6f + 1.4f * env, color, env);
        }
    }

    /** Glints on the emoji itself, where light would catch it: its brightest points. */
    static void glints(Scene s, int count) {
        Regions r = s.regions;
        int[] spots = glintSpots(r);
        if (spots.length == 0) {
            return;
        }
        for (int k = 0; k < count; k++) {
            float life = cycle(s, 1.6f, k * 0.83f);
            int cyc = (int) ((s.t + k * 0.83f + (s.seed & 0xFF) * 0.013f) / 1.6f);
            int spot = spots[(int) (Scene.hash(k, cyc, s.seed) * spots.length)];
            float env = (float) Math.sin(life * Math.PI);
            env *= env;
            s.glint(spot % r.size, spot / r.size, 0.5f + 1.6f * env, 0xFFFFFFFF, env);
        }
    }

    /** The emoji's highlights: its brightest inked dots, at most one per 3 x 3 block. */
    static int[] glintSpots(Regions r) {
        int[] cached = (int[]) r.memo.get("glintSpots");
        if (cached != null) {
            return cached;
        }
        java.util.ArrayList<Integer> list = new java.util.ArrayList<>();
        for (int by = 0; by < r.size; by += 3) {
            for (int bx = 0; bx < r.size; bx += 3) {
                int best = -1;
                float bestLuma = 0.6f;
                for (int y = by; y < Math.min(r.size, by + 3); y++) {
                    for (int x = bx; x < Math.min(r.size, bx + 3); x++) {
                        int i = y * r.size + x;
                        if (r.ink[i] && r.luma[i] > bestLuma) {
                            bestLuma = r.luma[i];
                            best = i;
                        }
                    }
                }
                if (best >= 0) {
                    list.add(best);
                }
            }
        }
        // Prefer the brightest third: the true highlights.
        list.sort((p, q) -> Float.compare(r.luma[q], r.luma[p]));
        int keep = Math.max(1, Math.min(list.size(), (list.size() + 2) / 3));
        int[] spots = new int[list.isEmpty() ? 0 : keep];
        for (int i = 0; i < spots.length; i++) {
            spots[i] = list.get(i);
        }
        r.memo.put("glintSpots", spots);
        return spots;
    }

    /** Pale blue pieces: a sleeper's drawn Zs, or the bubble at a sleepy nose. */
    static boolean sleepBlue(Regions r, int i) {
        return r.ink[i] && r.sat[i] > 0.2f && r.hue[i] > 185 && r.hue[i] < 260;
    }

    /** The drawn Zs, smallest first (they grow as they rise). */
    @SuppressWarnings("unchecked")
    static List<int[]> drawnZs(Regions r) {
        Object cached = r.memo.get("drawnZs");
        if (cached != null) {
            return (List<int[]>) cached;
        }
        boolean[] mask = new boolean[r.size * r.size];
        for (int i = 0; i < mask.length; i++) {
            mask[i] = sleepBlue(r, i);
        }
        List<int[]> zs = new java.util.ArrayList<>();
        for (int[] c : MusicEffects.components(r, mask, true)) {
            if (c.length >= 2) {
                zs.add(c);
            }
        }
        zs.sort((a, b) -> a.length - b.length);
        r.memo.put("drawnZs", zs);
        return zs;
    }

    static int[] zOf(Regions r, List<int[]> zs) {
        Object cached = r.memo.get("zOf");
        if (cached != null) {
            return (int[]) cached;
        }
        int[] out = new int[r.size * r.size];
        java.util.Arrays.fill(out, -1);
        for (int k = 0; k < zs.size(); k++) {
            for (int i : zs.get(k)) {
                out[i] = k;
            }
        }
        r.memo.put("zOf", out);
        return out;
    }

    /** A sleepy face's bubble: one big round blue piece low on the face. */
    static boolean hasBubble(Regions r) {
        List<int[]> zs = drawnZs(r);
        if (zs.size() != 1) {
            return false;
        }
        int[] c = zs.get(0);
        int x0 = r.size, y0 = r.size, x1 = 0, y1 = 0;
        for (int i : c) {
            x0 = Math.min(x0, i % r.size);
            x1 = Math.max(x1, i % r.size);
            y0 = Math.min(y0, i / r.size);
            y1 = Math.max(y1, i / r.size);
        }
        float w = x1 - x0 + 1;
        float h = y1 - y0 + 1;
        // Round, filled and small: not a blanket or a cap.
        return c.length >= 12 && w / h > 0.6f && w / h < 1.7f && c.length > 0.55f * w * h
                && w < (r.maxX - r.minX) * 0.5f && MusicEffects.cy(r, c) > (r.minY + r.maxY) / 2f;
    }

    /** The bubble at a sleeping nose swells and shrinks with each slow breath. */
    static void snoreBubble(Scene s) {
        Regions r = s.regions;
        if (!hasBubble(r)) {
            return;
        }
        int[] c = drawnZs(r).get(0);
        int x0 = r.size, y0 = r.size, x1 = 0, y1 = 0;
        for (int i : c) {
            x0 = Math.min(x0, i % r.size);
            x1 = Math.max(x1, i % r.size);
            y0 = Math.min(y0, i / r.size);
            y1 = Math.max(y1, i / r.size);
        }
        float breath = 0.5f - 0.5f * (float) Math.cos(s.t * Math.PI * 2 / 3.2f);
        // It grows out of the nostril, at its upper inner corner.
        s.liftHue(215);
        s.scaleLayerPiece(1, x0 - 1, y0 - 1, x1 + 2, y1 + 2, x0 + 0.5f, y0 + 0.5f,
                0.55f + 0.5f * breath);
        s.busy = true;
    }

    /** How far a tapped button is pressed in (0..1): quick down, a held beat, a springy rise. */
    static float press(Scene s) {
        float period = 3.4f;
        float t = s.since < 0.6f ? s.since
                : (s.t + Scene.hash(s.seed, 8, 8) * period) % period;
        if (t < 0.07f) {
            return ObjectEffects.easeOut(t / 0.07f);
        }
        if (t < 0.14f) {
            return 1;
        }
        return t < 0.6f ? 1 - ObjectEffects.spring((t - 0.14f) / 0.46f) : 0;
    }

    /** A letter Z, {@code size} dots tall, centred at resting (x, y). */
    static void zed(Scene s, float x, float y, float size, int color, float strength) {
        float half = size / 2;
        s.strip(x, y - half, size, 0.6f, 0, color, strength);
        s.strip(x, y + half, size, 0.6f, 0, color, strength);
        s.strip(x, y, size * 1.3f, 0.6f, (float) (Math.PI * 0.75), color, strength);
    }

    /** The heart reds: a hue near red or pink, strongly coloured. */
    static boolean heartRed(Regions r, int i) {
        float h = r.hue[i];
        return r.ink[i] && r.sat[i] > 0.45f && r.luma[i] > 0.25f && (h > 318 || h < 18);
    }

    /** The emoji's hearts (red or pink pieces of a fair size): per heart x0, y0, x1, y1. */
    static float[][] heartBoxes(Regions r) {
        float[][] cached = (float[][]) r.memo.get("heartBoxes");
        if (cached != null) {
            return cached;
        }
        boolean[] mask = new boolean[r.size * r.size];
        for (int i = 0; i < mask.length; i++) {
            mask[i] = heartRed(r, i);
        }
        java.util.List<float[]> boxes = new java.util.ArrayList<>();
        for (int[] comp : MusicEffects.components(r, mask, true)) {
            if (comp.length < 4) {
                continue;
            }
            int x0 = r.size, y0 = r.size, x1 = 0, y1 = 0;
            for (int i : comp) {
                x0 = Math.min(x0, i % r.size);
                x1 = Math.max(x1, i % r.size);
                y0 = Math.min(y0, i / r.size);
                y1 = Math.max(y1, i / r.size);
            }
            boxes.add(new float[] {x0 - 0.6f, y0 - 0.6f, x1 + 1.6f, y1 + 1.6f, comp.length});
        }
        // The biggest few, so every heart can beat as a part of its own.
        boxes.sort((a, b) -> Float.compare(b[4], a[4]));
        cached = boxes.subList(0, Math.min(Scene.MAX_PARTS - 1, boxes.size()))
                .toArray(new float[0][]);
        r.memo.put("heartBoxes", cached);
        return cached;
    }

    /** One beat: a strong swell and a softer second, then rest (0..1). */
    static float beat(float t) {
        float lub = t < 0.14f ? (float) Math.sin(Math.PI * t / 0.14f) : 0;
        float dub = t > 0.22f && t < 0.36f ? 0.6f * (float) Math.sin(Math.PI * (t - 0.22f) / 0.14f)
                : 0;
        return lub + dub;
    }

    /** The emoji's hearts beat, each a moment after the last. */
    static void heartbeat(Scene s) {
        Regions r = s.regions;
        float[][] hearts = heartBoxes(r);
        if (hearts.length == 0) {
            return;
        }
        s.liftHue(350);
        // Every red piece where it rests (small ones, lips), the beating hearts over them.
        s.addLayer(1, 0, 0, 0, 0, 0, 1);
        float period = 1.25f - 0.35f * s.boost;
        for (int k = 0; k < hearts.length; k++) {
            float[] h = hearts[k];
            float t = (s.t + period - k * 0.12f) % period;
            float swell = beat(t);
            s.busy |= swell > 0;
            s.scaleLayerPiece(1, h[0], h[1], h[2], h[3], (h[0] + h[2]) / 2, (h[1] + h[3]) / 2,
                    1 + (0.22f + 0.1f * s.boost) * swell);
        }
    }
    /** Stars circling round the head, brighter as they pass in front. */
    static void dizzy(Scene s) {
        Regions r = s.regions;
        float cx = (r.minX + r.maxX + 1) / 2f;
        float cy = r.minY + (r.maxY - r.minY) * 0.08f;
        float rx = (r.maxX - r.minX) * 0.44f;
        float ry = (r.maxY - r.minY) * 0.11f;
        float speed = 2.2f + 2.5f * s.boost;
        for (int k = 0; k < 3; k++) {
            double a = s.t * speed + k * Math.PI * 2 / 3;
            float front = 0.5f + 0.5f * (float) Math.sin(a);
            s.glint(cx + (float) Math.cos(a) * rx, cy + (float) Math.sin(a) * ry,
                    1 + 1.2f * front, 0xFFFFFFFF, 0.55f + 0.45f * front);
        }
    }

    /** Streaks of rain falling from beneath the emoji's underside. */
    static void rain(Scene s, int drops) {
        Regions r = s.regions;
        for (int k = 0; k < drops; k++) {
            float period = 0.75f;
            float life = cycle(s, period, k * (period / drops) * 1.7f);
            int cyc = (int) ((s.t + k * (period / drops) * 1.7f + (s.seed & 0xFF) * 0.013f) / period);
            float fx = r.minX + 1 + Scene.hash(k, cyc, s.seed) * Math.max(1, r.maxX - r.minX - 2);
            int col = Math.max(0, Math.min(r.size - 1, Math.round(fx)));
            float from = r.bottom[col] >= 0 ? r.bottom[col] + 1 : r.maxY + 1;
            float y = from + life * (r.size + 2 - from);
            float env = Math.min(1, life / 0.15f);
            s.strip(fx - life * 0.6f, y, 1.9f, 0.45f, (float) Math.toRadians(80), 0xFF8FD3FF,
                    0.85f * env);
        }
    }

    /**
     * Rain falling from above onto the emoji (an umbrella): each drop splashes where it meets
     * the top of it, or falls on past its edge.
     */
    static void rainOn(Scene s, int drops) {
        Regions r = s.regions;
        for (int k = 0; k < drops; k++) {
            float period = 0.7f;
            float off = k * (period / drops) * 1.7f;
            float life = cycle(s, period, off);
            int cyc = (int) ((s.t + off + (s.seed & 0xFF) * 0.013f) / period);
            float fx = Scene.hash(k, cyc, s.seed) * (s.size - 1);
            int col = Math.max(0, Math.min(r.size - 1, Math.round(fx)));
            float stop = r.top[col] >= 0 ? r.top[col] - 0.5f : s.size + 2;
            float y = -2 + life * (s.size + 4);
            if (y < stop) {
                s.strip(fx, y, 1.9f, 0.45f, (float) Math.toRadians(82), 0xFF8FD3FF, 0.85f);
            } else if (y < stop + 2.5f) {
                // The splash: a little burst of spray, gone at once.
                float k2 = (y - stop) / 2.5f;
                s.blob(fx - 0.9f * k2, stop - 0.6f * k2, 0.45f, 0xFFCFEFFF, 0.8f * (1 - k2));
                s.blob(fx + 0.9f * k2, stop - 0.6f * k2, 0.45f, 0xFFCFEFFF, 0.8f * (1 - k2));
            }
        }
    }

    /** Soft flakes drifting down over the whole panel. */
    static void snow(Scene s, int flakes) {
        for (int k = 0; k < flakes; k++) {
            float period = 4f;
            float life = cycle(s, period, k * (period / flakes));
            int cyc = (int) ((s.t + k * (period / flakes) + (s.seed & 0xFF) * 0.013f) / period);
            float x = Scene.hash(k, cyc, s.seed) * s.size
                    + (float) Math.sin(s.t * 1.3f + k * 1.7f) * 1.3f;
            float big = 0.45f + 0.35f * Scene.hash(cyc, k, s.seed);
            s.blob(x, -1.5f + life * (s.size + 3), big + 0.4f, 0xFFFFFFFF,
                    0.9f * Math.min(1, life * 6) * Math.min(1, (1 - life) * 6));
        }
    }

    /** Steam: soft puffs rise from the top in curling strands, widening and thinning. */
    static void steam(Scene s) {
        Regions r = s.regions;
        float cx = (r.minX + r.maxX + 1) / 2f;
        float w = r.maxX - r.minX;
        int strands = w > 14 ? 3 : 2;
        float every = 0.3f;
        float span = 2.1f;
        for (int k = 0; k < strands; k++) {
            float sx = cx + (k - (strands - 1) / 2f) * Math.min(4.5f, w * 0.22f);
            int col = Math.max(0, Math.min(r.size - 1, Math.round(sx)));
            // Off the top of the food, or, when it fills the panel to the top, off its upper
            // part, so the steam is always seen rising.
            float top = Math.max(r.top[col] >= 0 ? r.top[col] : r.minY, 7);
            float phase = Scene.hash(k, 7, s.seed) * 6;
            for (int j = 0; j * every < span; j++) {
                float life = ((s.t + phase + j * every) % span) / span;
                float y = top - 0.5f - ObjectEffects.easeOut(life) * 8;
                float x = sx + (float) Math.sin(life * 4.5f + phase + s.t * 0.8f) * 1.6f * life;
                float env = Math.min(1, life / 0.12f) * (1 - life);
                s.blob(x, y, 0.7f + 1.5f * life, 0xFFFFFFFF, 0.7f * env);
            }
        }
    }

    /** Bubbles rising through and above the emoji, wobbling and growing, popping at the top. */
    static void bubbles(Scene s, int count) {
        Regions r = s.regions;
        for (int k = 0; k < count; k++) {
            float period = 2.6f;
            float life = cycle(s, period, k * (period / count));
            int cyc = (int) ((s.t + k * (period / count) + (s.seed & 0xFF) * 0.013f) / period);
            // Each leaves the creature's top edge and rises through the clear water above it,
            // never across the creature itself.
            float x0 = -1;
            float y0 = 0;
            for (int tryNo = 0; tryNo < 6 && x0 < 0; tryNo++) {
                // Clamped: a narrow emoji near the edge would put minX + 1 past the grid.
                int col = Math.min(r.size - 1, r.minX + 1 + (int) (Scene.hash(k + 5 * tryNo,
                        cyc, s.seed) * Math.max(1, r.maxX - r.minX - 2)));
                int top = 0;
                while (top < r.size && !r.ink[top * r.size + col]) {
                    top++;
                }
                if (top >= 7 && top < r.size) {
                    x0 = col + 0.5f;
                    y0 = top - 0.5f;
                }
            }
            if (x0 < 0) {
                continue;
            }
            float y = y0 - Math.min(9, y0 - 1.5f) * (float) Math.pow(life, 1.3);
            float x = x0 + (float) Math.sin(life * 9 + k) * 0.6f;
            float radius = 0.6f + 0.5f * life;
            float env = Math.min(1, life / 0.1f);
            if (life > 0.9f) {
                // It pops: a quick swell, gone.
                float pop = (life - 0.9f) / 0.1f;
                radius += pop * 0.8f;
                env *= 1 - pop;
            }
            s.ring(x, y, radius, 0.45f, 0xFFCFF6FF, 0.75f * env);
            s.blob(x - radius * 0.45f, y - radius * 0.45f, 0.35f, 0xFFFFFFFF, 0.8f * env);
        }
    }

    /** Confetti strips falling over the panel, each tumbling so it flashes wide and thin. */
    static void confetti(Scene s, int count) {
        for (int k = 0; k < count; k++) {
            float period = 3f;
            float life = cycle(s, period, k * (period / count));
            int cyc = (int) ((s.t + k * (period / count) + (s.seed & 0xFF) * 0.013f) / period);
            float x = Scene.hash(k, cyc, s.seed) * s.size
                    + (float) Math.sin(s.t * 2.6f + k) * 1.4f;
            float y = -2 + life * (s.size + 4);
            float flip = (float) Math.abs(Math.cos(s.t * (5 + k % 3) + k));
            int color = Scene.hsv(Scene.hash(cyc, k, 5) * 360, 0.75f, 1);
            s.strip(x, y, 0.4f + 1.5f * flip, 0.7f, s.t * 2 + k, color,
                    0.6f + 0.4f * flip);
        }
    }

    /** Two bursts at a time: a flash, sparks flying out and falling, trailing, fading. */
    /** One firework: the shell rises, then the burst blooms, droops and burns out. */
    static final float SHELL = 0.5f;
    static final float BLOOM_TIME = 2.0f;
    static final float FIREWORK = SHELL + BLOOM_TIME + 0.5f;

    /**
     * The drawn burst going off (see {@link Burst}). Anchors: mode (0 a firework, 1 crackling),
     * the centre x, y and reach (fractions of the emoji's width), and the kind of spark (see
     * {@link Burst}).
     */
    static void burstPose(Scene s) {
        Regions r = s.regions;
        float[] a = s.anchor;
        if (a == null || a.length < 4) {
            return;
        }
        float w = r.maxX - r.minX + 1;
        s.burstMode = Math.round(a[0]);
        s.burstSpec[0] = r.minX + a[1] * w;
        s.burstSpec[1] = r.minY + a[2] * (r.maxY - r.minY + 1);
        s.burstSpec[2] = a[3] * w;
        s.burstSpec[3] = a.length > 4 ? a[4] : 0;
        float t = (s.t + (s.seed & 0xFF) * 0.013f) % FIREWORK;
        s.burstLife = t < SHELL ? 0 : (t - SHELL) / BLOOM_TIME;
        s.burstT = s.t;
        s.busy = true;
    }

    /** The shell of a firework climbing to where it bursts, its trail glowing behind it. */
    static void shell(Scene s) {
        float t = (s.t + (s.seed & 0xFF) * 0.013f) % FIREWORK;
        float x = s.burstSpec[0];
        float y1 = s.burstSpec[1];
        float y0 = s.regions.maxY - 1;
        if (t < SHELL) {
            float q = ObjectEffects.easeOut(t / SHELL);
            float y = y0 + (y1 - y0) * q;
            s.blob(x, y, 0.8f, 0xFFFFF0C0, 1);
            s.blob(x, y + 1.6f, 0.6f, 0xFFFFB060, 0.5f * (1 - q));
        } else if (t < SHELL + 0.12f) {
            // The flash of the burst.
            s.blob(x, y1, 2.2f, 0xFFFFFFFF, 1 - (t - SHELL) / 0.12f);
        }
    }

    static void fireworks(Scene s) {
        Regions r = s.regions;
        for (int k = 0; k < 2; k++) {
            float period = 2f;
            float life = cycle(s, period, k * period / 2);
            int cyc = (int) ((s.t + k * period / 2 + (s.seed & 0xFF) * 0.013f) / period);
            float cx = r.minX + 2 + Scene.hash(k, cyc, s.seed) * Math.max(1, r.maxX - r.minX - 4);
            float cy = r.minY + 2 + Scene.hash(cyc, k, s.seed) * (r.maxY - r.minY) * 0.5f;
            int color = Scene.hsv(Scene.hash(cyc, 3, k) * 360, 0.6f, 1);
            if (life < 0.12f) {
                s.blob(cx, cy, 1.8f, 0xFFFFFFFF, 1 - life / 0.12f);
            }
            float reach = 6 * ObjectEffects.easeOut(life);
            float fall = 2.5f * life * life;
            float env = (float) Math.pow(1 - life, 1.4);
            for (int ray = 0; ray < 10; ray++) {
                double a = ray * Math.PI / 5 + k;
                float dx = (float) Math.cos(a);
                float dy = (float) Math.sin(a);
                s.blob(cx + dx * reach, cy + dy * reach + fall, 0.6f, color, env);
                s.blob(cx + dx * reach * 0.7f, cy + dy * reach * 0.7f + fall * 0.6f, 0.45f,
                        color, 0.45f * env);
            }
        }
    }

    /**
     * Soft grey puffs drifting away, growing and thinning: from where the emoji table says
     * ({@code anchor}: per source x, y and the drift direction dx, dy), or else off its left.
     */
    static void smoke(Scene s) {
        Regions r = s.regions;
        float mid = (r.minY + r.maxY) / 2f;
        if (s.anchor != null && s.anchor.length >= 4) {
            int sources = s.anchor.length / 4;
            for (int k = 0; k < 5; k++) {
                int src = k % sources;
                float period = 2.8f;
                float life = cycle(s, period, k * period / 5);
                float x0 = ObjectEffects.edgeX(r, s.anchor[src * 4]);
                float y0 = ObjectEffects.edgeY(r, s.anchor[src * 4 + 1]);
                float dx = s.anchor[src * 4 + 2];
                float dy = s.anchor[src * 4 + 3];
                float go = ObjectEffects.easeOut(life) * 7;
                // Curling a little across its way as it goes.
                float curl = (float) Math.sin(life * 5 + k) * 0.8f * life;
                float x = x0 + dx * go - dy * curl;
                float y = y0 + dy * go + dx * curl;
                float env = Math.min(1, life / 0.15f) * (1 - life);
                s.blob(x, y, 0.8f + 1.5f * life, 0xFFEDEDED, 0.8f * env);
            }
            return;
        }
        for (int k = 0; k < 5; k++) {
            float period = 3f;
            float life = cycle(s, period, k * period / 5);
            float x = r.minX - 0.5f - ObjectEffects.easeOut(life) * 7;
            float y = mid + (k % 3 - 1) * 2.5f - life * 3 + (float) Math.sin(life * 5 + k) * 0.8f;
            float env = Math.min(1, life / 0.15f) * (1 - life);
            s.blob(x, y, 0.9f + 1.4f * life, 0xFFD8D8D8, 0.55f * env);
        }
    }

    /** Centres of the eye blobs, left to right (falls back to two guesses on a face). */
    static float[][] eyeCentres(Regions r) {
        float[][] cached = (float[][]) r.memo.get("eyeCentres");
        if (cached == null) {
            cached = findEyeCentres(r);
            r.memo.put("eyeCentres", cached);
        }
        return cached;
    }

    static float[][] findEyeCentres(Regions r) {
        float lx = 0, ly = 0, rx = 0, ry = 0;
        int ln = 0, rn = 0;
        float mid = (r.minX + r.maxX) / 2f;
        for (int i = 0; i < r.size * r.size; i++) {
            if (!r.eyes[i]) {
                continue;
            }
            if (i % r.size < mid) {
                lx += i % r.size;
                ly += i / r.size;
                ln++;
            } else {
                rx += i % r.size;
                ry += i / r.size;
                rn++;
            }
        }
        float h = r.minY + (r.maxY - r.minY) * 0.4f;
        float w = (r.maxX - r.minX) * 0.25f;
        return new float[][] {
                ln > 0 ? new float[] {lx / ln, ly / ln} : new float[] {mid - w, h},
                rn > 0 ? new float[] {rx / rn, ry / rn} : new float[] {mid + w, h}};
    }
}
