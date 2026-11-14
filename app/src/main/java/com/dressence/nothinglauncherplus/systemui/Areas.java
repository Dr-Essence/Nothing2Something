package com.dressence.nothinglauncherplus.systemui;

import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where in System UI a view is: in the status bar (or the status row over the pulled-down
 * quick settings), on a quick settings tile, or elsewhere. Told by the classes of its parents,
 * once the view is in its window, and remembered.
 */
final class Areas {

    static final int OTHER = 0;
    static final int STATUS_BAR = 1;
    static final int TILE = 2;

    private static final Map<View, Integer> KNOWN =
            Collections.synchronizedMap(new WeakHashMap<>());

    private Areas() {
    }

    static int of(View view) {
        Integer known = KNOWN.get(view);
        if (known != null) {
            return known;
        }
        if (!view.isAttachedToWindow()) {
            return OTHER; // Not placed yet: asked again when it is.
        }
        int area = find(view);
        KNOWN.put(view, area);
        return area;
    }

    private static int find(View view) {
        if (view instanceof TextView && view.getClass().getSimpleName().contains("DateView")) {
            return STATUS_BAR; // The date over the pulled-down quick settings.
        }
        int depth = 0;
        for (ViewParent p = view.getParent(); p instanceof View && depth < 14;
                p = p.getParent(), depth++) {
            String name = p.getClass().getSimpleName();
            if (name.contains("Battery")) {
                return OTHER; // Lit by its own filter (Battery).
            }
            if (isAppBadge(view, p, name)) {
                return OTHER;
            }
            if (name.contains("QSTile") || name.contains("QSIconView")
                    || name.contains("TileView")) {
                return TILE;
            }
            if (name.contains("StatusBar") || name.contains("StatusIcon")
                    || name.contains("ShadeHeader")
                    || name.contains("QuickStatusBarHeader")
                    || name.contains("NotificationIcon")) {
                return STATUS_BAR;
            }
        }
        return OTHER;
    }

    /**
     * The app's own icon in the corner of a tile in the quick settings editor: Nothing's
     * NTCustomizeTileView keeps it in {@code ivApp} (setAppIcon, from NTTileAdapter.getAppIcon,
     * shown only for apps' tiles). It is an app icon in its colours, not a tile icon: lit like
     * one, its whole shape turned into a solid green blob (Dr.Essence's recording, 0.70.45:
     * green circles, and a pentagon for Select to Speak, on every app's tile).
     */
    private static boolean isAppBadge(View view, ViewParent parent, String name) {
        if (!name.equals("NTCustomizeTileView")) {
            return false;
        }
        try {
            return de.robv.android.xposed.XposedHelpers.getObjectField(parent, "ivApp") == view;
        } catch (Throwable t) {
            return false; // Renamed by an update: the badge is lit as before.
        }
    }
}
