package com.dressence.nothinglauncherplus.systemui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.format.DateFormat;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dressence.nothinglauncherplus.xposed.ConfigStore;
import com.dressence.nothinglauncherplus.xposed.Hooks;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * The status bar on two rows: everything on the top row as Nothing lays it out, the
 * notifications (and the date, in full, when the date is on) on a row under it.
 *
 * <p>Nothing moves to another parent: System UI rebinds its notification icons on every
 * notification, and views moved out of their containers are what breaks that. Instead, read
 * from Nothing's System UI 16:
 * <ul>
 * <li>Every part of System UI takes the bar's height from {@code SystemBarUtils}
 *     ({@code getStatusBarHeight(Context)} for the bar, the shade and heads-up;
 *     {@code getStatusBarHeightForRotation} for the window's insets, which apps lay out
 *     under). One icon row more there makes all of it, apps included, follow through
 *     Nothing's own updates.</li>
 * <li>The clock and the notification area already share one row container of Nothing's
 *     ({@code status_bar_start_side_except_heads_up}); it is turned vertical: the notification
 *     area one icon row high at the bottom, the clock taking the rest (it centres its text in
 *     its box, so it stays where it was). The end side gets one row of bottom padding, so its
 *     icons (centred in it) stay level with the clock. System UI sets neither padding nor
 *     these sizes itself; they are set when the bar is built and on rotation, not after its
 *     layout passes.</li>
 * <li>The date is drawn on the notification area's overlay, in the clock's own paint (font,
 *     colour, glow), in the room its start padding makes; the icon container itself is not
 *     touched.</li>
 * </ul>
 *
 * <p>Read at System UI's start (a change needs a restart, as the other status bar options).
 * A guard turns it off if System UI starts three times within three minutes with it on, until
 * a setting is saved again.
 */
final class DualStatusBar {

    private static final String SYSUI = "com.android.systemui";
    private static final String CLS_UTILS = "com.android.internal.policy.SystemBarUtils";
    private static final String CLS_BAR = "com.android.systemui.statusbar.phone.PhoneStatusBarView";
    private static final String CLS_CLOCK = "com.android.systemui.statusbar.policy.Clock";

    /** System UI's own data folder (user 0, device storage): only System UI writes here. */
    private static final File DIR = new File("/data/user_de/0/" + SYSUI + "/files");
    private static final File STARTS = new File(DIR, "nlplus_dual_starts");
    private static final File FAILED = new File(DIR, "nlplus_dual_failed");
    private static final long LOOP_MS = 3 * 60_000;
    private static final int LOOP_STARTS = 3;

    /** On for this run of System UI (set once, at its start). */
    private static volatile boolean on;
    private static volatile boolean landscape;
    private static volatile boolean date;
    private static int rowDimen;

    /** The date drawn under the clock, with the clock it takes its look from. */
    private static final List<WeakReference<DateRow>> ROWS = new ArrayList<>();

    /*
     * What the module keeps for a view of System UI's, held here rather than in the view's
     * tags: a tag key must be an id the app owns (Android refuses its own ids, seen on the
     * phone), and any id of System UI's could be one it uses itself. Weak, so a view System UI
     * drops is let go. Main thread only.
     */
    private static final Map<View, Saved> SAVED = new WeakHashMap<>();
    private static final Map<View, DateRow> DATES = new WeakHashMap<>();

    private DualStatusBar() {
    }

    static void install(ClassLoader cl, ConfigStore store) {
        if (!store.get().dualStatusBar || !guardAllows(store.stamp())) {
            return;
        }
        landscape = store.get().dualStatusBarLandscape;
        date = store.get().statusBarDate;
        on = true;

        Class<?> utils = XposedHelpers.findClass(CLS_UTILS, null);
        XposedHelpers.findAndHookMethod(utils, "getStatusBarHeight", Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Context ctx = (Context) param.args[0];
                        addRow(param, ctx, isLandscape(ctx));
                    }
                });
        XposedHelpers.findAndHookMethod(utils, "getStatusBarHeightForRotation", Context.class,
                int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        int rot = (int) param.args[1];
                        addRow(param, (Context) param.args[0],
                                rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270);
                    }
                });

        XC_MethodHook arrange = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    arrange((ViewGroup) param.thisObject);
                } catch (Throwable t) {
                    Hooks.log("dual status bar: " + t);
                }
            }
        };
        Class<?> bar = XposedHelpers.findClass(CLS_BAR, cl);
        XposedBridge.hookAllMethods(bar, "onFinishInflate", arrange);
        XposedBridge.hookAllMethods(bar, "onConfigurationChanged", arrange);

        // The date follows the clock: its minute, its colour (light or dark over an app).
        XC_MethodHook refresh = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    refreshDates();
                } catch (Throwable t) {
                    Hooks.log("dual status bar date: " + t);
                }
            }
        };
        Class<?> clock = XposedHelpers.findClass(CLS_CLOCK, cl);
        XposedBridge.hookAllMethods(clock, "updateClock", refresh);
        XposedBridge.hookAllMethods(clock, "onDarkChanged", refresh);
        Hooks.log("dual status bar on" + (landscape ? ", landscape too" : ""));
    }

    /** True when the date is shown under {@code clock} instead of before the time. */
    static boolean dateBelow(View clock) {
        return on && date && (landscape || !isLandscape(clock.getContext()));
    }

    private static boolean isLandscape(Context ctx) {
        return ctx.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    /** The bar's height, one icon row taller where the second row is shown. */
    private static void addRow(XC_MethodHook.MethodHookParam param, Context ctx, boolean land) {
        if (!on || (land && !landscape) || !(param.getResult() instanceof Integer)) {
            return;
        }
        int row = rowHeight(ctx);
        if (row > 0) {
            param.setResult((Integer) param.getResult() + row);
        }
    }

    /** Nothing's status bar icon row (status_bar_system_icons_height), or 0 if not found. */
    private static int rowHeight(Context ctx) {
        try {
            if (rowDimen == 0) {
                rowDimen = ctx.getResources().getIdentifier(
                        "status_bar_system_icons_height", "dimen", SYSUI);
            }
            return rowDimen == 0 ? 0 : ctx.getResources().getDimensionPixelSize(rowDimen);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static View find(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", SYSUI);
        return id == 0 ? null : root.findViewById(id);
    }

    /** Lays the bar out on one row or two, for the orientation it is in now. */
    private static void arrange(ViewGroup bar) {
        View startView = find(bar, "status_bar_start_side_except_heads_up");
        View end = find(bar, "status_bar_end_side_content");
        View area = find(bar, "notification_icon_area");
        View clockView = find(bar, "clock");
        if (!(startView instanceof LinearLayout) || end == null || area == null
                || !(clockView instanceof TextView)
                || !(clockView.getLayoutParams() instanceof LinearLayout.LayoutParams)
                || !(area.getLayoutParams() instanceof LinearLayout.LayoutParams)) {
            Hooks.log("dual status bar: the status bar is not laid out as expected; left as is");
            return;
        }
        LinearLayout start = (LinearLayout) startView;
        TextView clock = (TextView) clockView;
        Saved saved = Saved.of(start, clock, area, end);
        int row = rowHeight(bar.getContext());
        boolean two = row > 0 && (landscape || !isLandscape(bar.getContext()));

        LinearLayout.LayoutParams clockLp = (LinearLayout.LayoutParams) clock.getLayoutParams();
        LinearLayout.LayoutParams areaLp = (LinearLayout.LayoutParams) area.getLayoutParams();
        if (two) {
            start.setOrientation(LinearLayout.VERTICAL);
            clockLp.height = 0;
            clockLp.weight = 1;
            areaLp.height = row;
            areaLp.weight = 0;
            end.setPadding(end.getPaddingLeft(), end.getPaddingTop(), end.getPaddingRight(),
                    saved.endBottom + row);
        } else {
            start.setOrientation(saved.orientation);
            clockLp.height = saved.clockHeight;
            clockLp.weight = saved.clockWeight;
            areaLp.height = saved.areaHeight;
            areaLp.weight = saved.areaWeight;
            end.setPadding(end.getPaddingLeft(), end.getPaddingTop(), end.getPaddingRight(),
                    saved.endBottom);
        }
        clock.setLayoutParams(clockLp);
        area.setLayoutParams(areaLp);
        DateRow.attach(area, clock, saved, two && date);
    }

    /** What {@link #arrange} changes, as Nothing set it: put back for one row. */
    private static final class Saved {
        final int orientation;
        final int clockHeight;
        final float clockWeight;
        final int areaHeight;
        final float areaWeight;
        final int endBottom;
        final int areaStart;
        final int areaEnd;

        private Saved(LinearLayout start, View clock, View area, View end) {
            orientation = start.getOrientation();
            LinearLayout.LayoutParams c = (LinearLayout.LayoutParams) clock.getLayoutParams();
            clockHeight = c.height;
            clockWeight = c.weight;
            LinearLayout.LayoutParams a = (LinearLayout.LayoutParams) area.getLayoutParams();
            areaHeight = a.height;
            areaWeight = a.weight;
            endBottom = end.getPaddingBottom();
            areaStart = area.getPaddingStart();
            areaEnd = area.getPaddingEnd();
        }

        /** Saved once per bar, the first time it is arranged (still Nothing's values). */
        static Saved of(LinearLayout start, View clock, View area, View end) {
            Saved s = SAVED.get(start);
            if (s == null) {
                s = new Saved(start, clock, area, end);
                SAVED.put(start, s);
            }
            return s;
        }
    }

    private static void refreshDates() {
        synchronized (ROWS) {
            for (int i = ROWS.size() - 1; i >= 0; i--) {
                DateRow r = ROWS.get(i).get();
                if (r == null) {
                    ROWS.remove(i);
                } else {
                    r.refresh();
                }
            }
        }
    }

    /**
     * The date ("Friday, 7 November", the locale's order) drawn at the start of the
     * notification area, in the room its start padding makes, in the clock's paint.
     */
    private static final class DateRow extends Drawable implements View.OnLayoutChangeListener {

        private final WeakReference<View> area;
        private final WeakReference<TextView> clock;
        private final Saved saved;
        private final TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        private Locale patternLocale;
        private String pattern;
        private String text = "";
        private int width = -1;

        private DateRow(View area, TextView clock, Saved saved) {
            this.area = new WeakReference<>(area);
            this.clock = new WeakReference<>(clock);
            this.saved = saved;
        }

        static void attach(View area, TextView clock, Saved saved, boolean show) {
            DateRow row = DATES.get(area);
            if (show && row == null) {
                row = new DateRow(area, clock, saved);
                DATES.put(area, row);
                area.getOverlay().add(row);
                area.addOnLayoutChangeListener(row);
                synchronized (ROWS) {
                    ROWS.add(new WeakReference<>(row));
                }
                row.refresh();
            } else if (!show && row != null) {
                area.getOverlay().remove(row);
                area.removeOnLayoutChangeListener(row);
                DATES.remove(area);
                area.setPaddingRelative(saved.areaStart, area.getPaddingTop(), saved.areaEnd,
                        area.getPaddingBottom());
            }
        }

        private String pattern() {
            Locale locale = Locale.getDefault();
            if (!locale.equals(patternLocale)) {
                patternLocale = locale;
                pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM");
            }
            return pattern;
        }

        /** New text or look: the room for it is made again only when its width changed. */
        void refresh() {
            View a = area.get();
            TextView c = clock.get();
            if (a == null || c == null) {
                return;
            }
            text = DateFormat.format(pattern(), new Date()).toString();
            paint.set(c.getPaint());
            paint.setColor(c.getCurrentTextColor());
            int gap = Math.round(c.getTextSize() * 0.6f);
            int w = Math.round(paint.measureText(text)) + gap;
            if (w != width) {
                width = w;
                boolean rtl = a.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                a.setPaddingRelative(saved.areaStart + w, a.getPaddingTop(), saved.areaEnd,
                        a.getPaddingBottom());
                setBounds(rtl ? a.getWidth() - w : 0, 0, rtl ? a.getWidth() : w, a.getHeight());
            }
            invalidateSelf();
        }

        @Override
        public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or,
                int ob) {
            boolean rtl = v.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            int w = Math.max(width, 0);
            setBounds(rtl ? v.getWidth() - w : 0, 0, rtl ? v.getWidth() : w, v.getHeight());
        }

        @Override
        public void draw(Canvas canvas) {
            if (text.isEmpty()) {
                return;
            }
            Rect b = getBounds();
            TextPaint.FontMetrics fm = paint.getFontMetrics();
            float y = b.exactCenterY() - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(text, b.left, y, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * False if System UI has started {@value #LOOP_STARTS} times within three minutes with the
     * second row on (a crash loop), until a setting is saved again ({@code stamp} changes).
     * Each start is recorded with the settings' stamp; a start with other settings resets it.
     */
    private static boolean guardAllows(long stamp) {
        try {
            String failed = read(FAILED);
            if (failed.equals(Long.toString(stamp))) {
                Hooks.log("dual status bar left off: System UI kept restarting with it on;"
                        + " save any setting to try again");
                return false;
            }
            long now = System.currentTimeMillis();
            StringBuilder keep = new StringBuilder();
            int recent = 0;
            for (String line : read(STARTS).split("\n")) {
                String[] f = line.trim().split(" ");
                if (f.length == 2 && f[1].equals(Long.toString(stamp))
                        && now - Long.parseLong(f[0]) < LOOP_MS) {
                    keep.append(line.trim()).append('\n');
                    recent++;
                }
            }
            if (recent + 1 >= LOOP_STARTS) {
                write(FAILED, Long.toString(stamp));
                write(STARTS, "");
                Hooks.log("dual status bar turned off: System UI started " + (recent + 1)
                        + " times within " + LOOP_MS / 60_000 + " minutes with it on");
                return false;
            }
            keep.append(now).append(' ').append(stamp).append('\n');
            write(STARTS, keep.toString());
            return true;
        } catch (Throwable t) {
            // Without a guard it stays off.
            Hooks.log("dual status bar guard unavailable, left off: " + t);
            return false;
        }
    }

    private static String read(File f) {
        try {
            return f.exists() ? new String(Files.readAllBytes(f.toPath()),
                    StandardCharsets.UTF_8) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static void write(File f, String s) throws Exception {
        if (!DIR.isDirectory() && !DIR.mkdirs()) {
            throw new IllegalStateException("no " + DIR);
        }
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }
}
