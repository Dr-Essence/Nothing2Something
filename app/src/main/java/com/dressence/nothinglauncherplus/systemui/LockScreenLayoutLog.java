package com.dressence.nothinglauncherplus.systemui;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import com.dressence.nothinglauncherplus.xposed.Hooks;

/**
 * Diagnostic logging for the turned lock screen: each landscape layout (its views, where they
 * are, which lie off screen) written to the LSPosed log when it changes. Moved out of
 * LockScreenTurn unchanged (0.70.13, cleanup step 4).
 */
final class LockScreenLayoutLog {

    /** The lock screen's views, by resource name. */
    private static final String[] ROOTS = {"keyguard_root_view", "shared_notification_container",
            "keyguard_message_area_container", "keyguard_bouncer_container", "nt_bouncer_icon_area"};
    private static final int MAX_DEPTH = 9;
    private static final int MAX_LINES = 160;
    /** Layouts logged per System UI run at most, so the log stays readable. */
    private static final int MAX_DUMPS = 60;

    private static int dumps;
    private static String lastLayout;

    private LockScreenLayoutLog() {
    }

    /** Logs the lock screen's layout if it shows in landscape and differs from the last one. */
    static void record(View window) {
        if (dumps >= MAX_DUMPS || window.getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_LANDSCAPE) {
            return;
        }
        StringBuilder out = new StringBuilder();
        int[] lines = {0};
        for (String name : ROOTS) {
            int id = window.getResources().getIdentifier(name, "id",
                    window.getContext().getPackageName());
            View v = id != 0 ? window.findViewById(id) : null;
            if (v != null && v.isShown()) {
                dump(window, v, 0, out, lines);
            }
        }
        if (out.length() == 0) {
            return; // No lock screen showing.
        }
        String layout = "window " + window.getWidth() + "x" + window.getHeight() + "\n" + out;
        if (layout.equals(lastLayout)) {
            return;
        }
        lastLayout = layout;
        dumps++;
        for (String line : layout.split("\n")) {
            Hooks.log("lock screen layout: " + line);
        }
    }

    private static void dump(View window, View v, int depth, StringBuilder out, int[] lines) {
        if (v.getVisibility() != View.VISIBLE || v.getWidth() <= 0 || v.getHeight() <= 0
                || v.getAlpha() == 0f || lines[0] >= MAX_LINES) {
            return;
        }
        Rect r = new Rect(0, 0, v.getWidth(), v.getHeight());
        try {
            ((ViewGroup) window).offsetDescendantRectToMyCoords(v, r);
        } catch (IllegalArgumentException notInside) {
            return;
        }
        boolean off = r.left < 0 || r.top < 0 || r.right > window.getWidth()
                || r.bottom > window.getHeight();
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
        out.append(name(v)).append(' ').append(r.toShortString())
                .append(off ? " OFF-SCREEN" : "")
                .append(v.getScaleX() != 1f ? " scale " + v.getScaleX() : "")
                .append('\n');
        lines[0]++;
        if (depth < MAX_DEPTH && v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                dump(window, g.getChildAt(i), depth + 1, out, lines);
            }
        }
    }

    private static String name(View v) {
        String cls = v.getClass().getSimpleName();
        if (v.getId() == View.NO_ID) {
            return cls;
        }
        try {
            return v.getResources().getResourceEntryName(v.getId()) + ":" + cls;
        } catch (Resources.NotFoundException e) {
            return cls;
        }
    }
}
