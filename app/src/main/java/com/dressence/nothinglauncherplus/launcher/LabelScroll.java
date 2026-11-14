package com.dressence.nothinglauncherplus.launcher;

import android.graphics.Canvas;
import android.text.Layout;
import android.text.TextUtils;
import android.widget.TextView;

import com.dressence.nothinglauncherplus.xposed.Hooks;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * With dot-matrix icons on, an app name too long for its icon scrolls through its place like
 * a drawer folder's name: a dot's width at a time, pausing at the start, and back to the start
 * once its end has shown. Only the text moves; the icon above it stays where it is.
 */
final class LabelScroll {

    private static final String CLS_BUBBLE = "com.android.launcher3.BubbleTextView";
    /** One step: a dot of NDot (a tenth of the text size). */
    private static final float STEP_EM = 0.1f;
    private static final long STEP_MS = 90;
    private static final long PAUSE_START_MS = 1800;
    private static final long PAUSE_END_MS = 1200;

    /** The labels scrolling now, and how far each has to go. */
    private static final Map<TextView, Scroller> SCROLLING =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LabelScroll() {
    }

    static void install(ClassLoader cl) {
        XposedHelpers.findAndHookMethod(CLS_BUBBLE, cl, "onDraw", Canvas.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        update((TextView) param.thisObject, DotIconPack.active());
                    }
                });
    }

    /** Starts or stops {@code label} scrolling, as its text, width and the option say. */
    private static void update(TextView label, boolean on) {
        if (!on && !SCROLLING.containsKey(label)) {
            return; // The pack is off and this label does not scroll: nothing to measure.
        }
        CharSequence text = label.getText();
        int room = label.getWidth() - label.getCompoundPaddingLeft()
                - label.getCompoundPaddingRight();
        float width = text == null ? 0 : label.getPaint().measureText(text, 0, text.length());
        boolean wanted = on && room > 0 && width > room + 1 && label.isAttachedToWindow();
        Scroller scroller = SCROLLING.get(label);
        if (!wanted) {
            if (scroller != null) {
                scroller.stop();
                SCROLLING.remove(label);
            }
            return;
        }
        // The two are always set together: no ellipsis means it already scrolls.
        if (label.getEllipsize() != null) {
            label.setEllipsize(null);
            label.setHorizontallyScrolling(true);
        }
        int travel = (int) Math.ceil(width - room);
        if (scroller == null) {
            scroller = new Scroller(label);
            SCROLLING.put(label, scroller);
        }
        scroller.start(travel);
    }

    /** Steps one label's text along while it is shown. */
    private static final class Scroller implements Runnable {
        private final TextView label;
        private int travel;
        private int at;
        private boolean posted;
        private final android.graphics.Rect onScreen = new android.graphics.Rect();

        Scroller(TextView label) {
            this.label = label;
        }

        void start(int travel) {
            this.travel = travel;
            if (!posted) {
                posted = true;
                label.postDelayed(Hooks.guard("label scroll", this), PAUSE_START_MS);
            }
        }

        void stop() {
            posted = false;
            at = 0;
            label.setScrollX(0);
            if (TextUtils.TruncateAt.END != label.getEllipsize()) {
                label.setHorizontallyScrolling(false);
                label.setEllipsize(TextUtils.TruncateAt.END);
            }
        }

        /**
         * Where the text starts: a centred line too long for its view is laid out centred on
         * a very wide line, and the view scrolled to its start; steps count from there.
         */
        private int start() {
            Layout layout = label.getLayout();
            return layout == null || layout.getLineCount() == 0 ? 0
                    : (int) layout.getLineLeft(0);
        }

        @Override
        public void run() {
            if (!posted) {
                return; // Stopped meanwhile.
            }
            // Also when no part of it is on screen: the home screen keeps every page visible,
            // so a label on a page swiped past stayed "shown" and stepped on, redrawing the
            // launcher about 11 times a second (2026-11 audit). Drawn on screen again, it
            // starts over.
            if (!label.isAttachedToWindow() || !label.isShown()
                    || !label.getGlobalVisibleRect(onScreen)) {
                posted = false; // Drawn again later, it starts over.
                at = 0;
                label.setScrollX(start());
                // Out of the map: this Scroller holds its label, the map's key, so an entry
                // left behind kept the label and its launcher (with all its views) after the
                // launcher was made again (0.70.14 audit). Hidden too, not only detached: a
                // closed drawer's labels stay attached and get no more draws or steps, so
                // their entries stayed (found by the audit's fourth pass). Stopped first, its
                // ellipsis back; drawn again, update makes a new one.
                stop();
                SCROLLING.remove(label);
                return;
            }
            int step = Math.max(1, Math.round(label.getTextSize() * STEP_EM));
            long next;
            if (at >= travel) {
                at = 0; // Back to the start, and a pause there.
                next = PAUSE_START_MS;
            } else {
                at = Math.min(travel, at + step);
                next = at >= travel ? PAUSE_END_MS : STEP_MS;
            }
            label.setScrollX(start() + at);
            label.postDelayed(Hooks.guard("label scroll", this), next);
        }
    }
}
