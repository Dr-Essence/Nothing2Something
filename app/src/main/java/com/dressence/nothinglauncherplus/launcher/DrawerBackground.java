package com.dressence.nothinglauncherplus.launcher;

import com.dressence.nothinglauncherplus.Config;
import android.content.Context;
import android.graphics.Color;
import android.view.View;

import com.dressence.nothinglauncherplus.xposed.ConfigStore;
import com.dressence.nothinglauncherplus.xposed.Hooks;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * On phones the app drawer's background is the launcher scrim tinted with Nothing's drawer
 * scrim colour ({@code AllAppsState#getWorkspaceScrimColor}); scaling that colour's alpha lets
 * the wallpaper show through while the folder tiles stay opaque.
 */
final class DrawerBackground {

    private static final String CLS_ALL_APPS_STATE =
            "com.android.launcher3.uioverrides.states.AllAppsState";
    private static final String CLS_LAUNCHER_STATE = "com.android.launcher3.LauncherState";
    /** Nothing's launcher colours (ThemeColors), and the drawer scrim colour it gives. */
    private static final String CLS_THEME_COLOURS = "p7.e";
    private static final String SCRIM_COLOUR = "nt_all_apps_scrim_color_nt_mono";

    static void install(ClassLoader cl, ConfigStore store) {
        Class<?> allAppsState = XposedHelpers.findClass(CLS_ALL_APPS_STATE, cl);
        Class<?> launcherClass = XposedHelpers.findClass(Config.CLS_LAUNCHER, cl);
        boolean atSource = hookScrimColour(cl, store);
        XposedHelpers.findAndHookMethod(allAppsState, "getWorkspaceScrimColor",
                launcherClass, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        // An int up to Nothing OS 4; Nothing OS 5 returns ScrimColors (two
                        // colours), whose colour comes from ThemeColors, faded at the source
                        // below (tools/hookcheck.py, 0.70.32).
                        if (!(param.getResult() instanceof Integer)) {
                            return;
                        }
                        Config config = store.get();
                        if (DrawerGlass.drawerIsGlass(config)) {
                            // The one tint over the drawer's live blur (DrawerGlass).
                            param.setResult(DrawerGlass.tint((Context) param.args[0], config));
                        } else if (!atSource && config.drawerOpacity < 100) {
                            param.setResult(faded((Integer) param.getResult(),
                                    config.drawerOpacity));
                        }
                    }
                });

        // The drawer's search (Nothing's NTLauncherAllAppsContainerView) paints its own colours
        // over that scrim: its sheet (foregroundPaint), its scrim and header protection, and the
        // search results' background, all picked in updateCustomColor (and its constructor).
        // They take the drawer's opacity too, so the search does not rise as a solid black
        // panel over a see-through drawer.
        Class<?> search = XposedHelpers.findClassIfExists(SmartDrawer.CLS_ALL_APPS_CONTAINER, cl);
        if (search != null) {
            XC_MethodHook colours = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Hooks.guard("drawer search colours", () -> searchColours(param.thisObject,
                            opacity(store.get()), atSource)).run();
                }
            };
            XposedBridge.hookAllConstructors(search, colours);
            XposedBridge.hookAllMethods(search, "updateCustomColor", colours);
        }

        // A new opacity only reaches the scrim on the next state change; repaint an open drawer.
        Object allApps = XposedHelpers.getStaticObjectField(
                XposedHelpers.findClass(CLS_LAUNCHER_STATE, cl), "ALL_APPS");
        store.addChangeListener(() -> {
            Object launcher = XposedHelpers.getStaticObjectField(launcherClass, "ACTIVITY_TRACKER");
            launcher = XposedHelpers.callMethod(launcher, "getCreatedContext");
            if (launcher == null) {
                return;
            }
            Object state = XposedHelpers.callMethod(
                    XposedHelpers.callMethod(launcher, "getStateManager"), "getState");
            if (state == allApps) {
                View scrim = (View) XposedHelpers.callMethod(launcher, "getScrimView");
                Object colour = XposedHelpers.callMethod(allApps, "getWorkspaceScrimColor",
                        launcher);
                // Nothing OS 5's ScrimColors: the new opacity shows at the next state change.
                if (colour instanceof Integer) {
                    scrim.setBackgroundColor((Integer) colour);
                }
            }
            // The search's colours too, through Nothing's own updateCustomColor (what an icon
            // pack or theme change calls): fresh colours, faded once by the hook above. They
            // kept the old opacity until the launcher was made again (0.70.16 audit).
            Object apps = XposedHelpers.callMethod(launcher, "getAppsView");
            if (apps != null && search != null && search.isInstance(apps)) {
                XposedHelpers.callMethod(apps, "updateCustomColor");
            }
        });
    }

    /**
     * Nothing's drawer search colours at the drawer's opacity ({@code opacity} percent).
     *
     * <p>Mistake recorded (found in the 0.70.14 audit): the sheet (foregroundPaint) and the
     * scrim are set from ThemeColors e(), which {@link #hookScrimColour} already fades, and
     * were faded again here, so at 50% they showed at 25%. With {@code atSource} they are left
     * as e() gave them. The constructors call each other and both are hooked, so each value
     * faded here is remembered and never faded twice; updateCustomColor sets new ones.
     */
    private static void searchColours(Object container, int opacity, boolean atSource) {
        if (opacity >= 100) {
            return;
        }
        android.graphics.Paint paint = (android.graphics.Paint) XposedHelpers.getObjectField(
                container, "foregroundPaint");
        if (paint != null && !atSource && !isFaded(container, "foregroundPaint",
                paint.getColor())) {
            paint.setColor(fadeOnce(container, "foregroundPaint", paint.getColor(), opacity));
        }
        for (String name : atSource ? new String[] {"mHeaderProtectionColor"}
                : new String[] {"mScrimColor", "mHeaderProtectionColor"}) {
            int colour = XposedHelpers.getIntField(container, name);
            if (!isFaded(container, name, colour)) {
                XposedHelpers.setIntField(container, name,
                        fadeOnce(container, name, colour, opacity));
            }
        }
        View results = (View) XposedHelpers.callMethod(container, "getSearchRecyclerView");
        if (results != null && results.getBackground() != null) {
            results.getBackground().mutate().setAlpha(255 * opacity / 100);
        }
        ((View) container).invalidate();
    }

    /**
     * The drawer's scrim colour where Nothing reads it (ThemeColors p7.e#e(Context):
     * nt_all_apps_scrim_color_nt_mono). Besides the drawer's background (AllAppsState), its
     * Compose search reads it directly: the search bar (DrawerSearchBar) paints it from the
     * bar's middle to its bottom, a band under the bar that rises with the keyboard (it is
     * padded by the keyboard's height), and the search page's theme background is that colour.
     * Faded here, all of them follow the drawer's opacity. The method is taken only when it
     * really gives that colour; otherwise the drawer's background is faded where
     * AllAppsState gives it, as before. False when not hooked.
     */
    private static boolean hookScrimColour(ClassLoader cl, ConfigStore store) {
        // Found by its shape when an update renames it (LauncherSymbols, 4.0.20: p7.e.e).
        java.lang.reflect.Method method;
        try {
            method = LauncherSymbols.scrimColour(cl);
        } catch (Throwable t) {
            method = null;
        }
        if (method == null) {
            Hooks.skipped("drawer scrim colour", "not found, background faded by its state");
            return false;
        }
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                int opacity = opacity(store.get());
                Context context = (Context) param.args[0];
                if (opacity >= 100 || context == null) {
                    return;
                }
                Hooks.guard("drawer scrim colour", () -> {
                    int id = context.getResources().getIdentifier(SCRIM_COLOUR, "color",
                            context.getPackageName());
                    int colour = (Integer) param.getResult();
                    if (id != 0 && context.getColor(id) == colour) {
                        param.setResult(faded(colour, opacity));
                    }
                }).run();
            }
        });
        return true;
    }

    /**
     * How opaque the drawer's own colours are, in percent: the Drawer background slider, or
     * none at all while the drawer is glass (its tint is the scrim's alone, DrawerGlass), so
     * the search's band under its bar and its page never lie over the blur as a dark strip.
     */
    private static int opacity(Config config) {
        return DrawerGlass.drawerIsGlass(config) ? 0 : config.drawerOpacity;
    }

    /** True if {@code colour} is the one this class last set in {@code name}. */
    private static boolean isFaded(Object container, String name, int colour) {
        Object set = XposedHelpers.getAdditionalInstanceField(container, "nlplus.faded." + name);
        return set instanceof Integer && (Integer) set == colour;
    }

    private static int fadeOnce(Object container, String name, int colour, int opacity) {
        int result = faded(colour, opacity);
        XposedHelpers.setAdditionalInstanceField(container, "nlplus.faded." + name, result);
        return result;
    }

    private static int faded(int color, int opacity) {
        return (color & 0x00FFFFFF) | ((Color.alpha(color) * opacity / 100) << 24);
    }

    private DrawerBackground() {
    }
}
