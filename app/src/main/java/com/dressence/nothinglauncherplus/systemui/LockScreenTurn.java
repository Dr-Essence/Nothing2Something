package com.dressence.nothinglauncherplus.systemui;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import com.dressence.nothinglauncherplus.xposed.ConfigStore;
import com.dressence.nothinglauncherplus.xposed.Hooks;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Test: the lock screen turns with the device while "Lock screen rotation (test)" is on, and
 * its landscape layout is written to the LSPosed log, for making it fit.
 *
 * <p>SystemUI's notification shade window (which holds the lock screen) follows the device
 * while the lock screen shows only if {@code KeyguardStateController
 * .isKeyguardScreenRotationAllowed()}; that is SystemUI's own switch for it (also set by the
 * lockscreen.rot_override property and config_enableLockScreenRotation), answered yes here.
 *
 * <p>In landscape SystemUI's resources (values-land) turn on split shade, and with it the lock
 * screen would swap Nothing's own layout ("default" blueprint) for AOSP's "split-shade" one.
 * Nothing added its pieces (date, widgets, depth-effect wallpaper) to its default layout only,
 * so the swapped one showed just a clock across the top with the notifications over it (seen
 * in the logged layout). That swap is switched off: Nothing's layout stays. SystemUI still
 * puts the notifications on the right half in landscape, so turned, Nothing's clock, date and
 * widgets end at the middle in the same layout pass, as AOSP's split layout does with its
 * clock: they center on one half, with notifications or without, and the charging line on the
 * other. Each landscape layout is still logged (views, where they are, which lie off screen).
 *
 * <p>The fingerprint sensor (under the screen) is where it is whichever way the screen turns.
 * SystemUI's sensor overlay (UdfpsControllerOverlay, which lights the sensor and reads the
 * finger) turns the sensor's spot with the screen ({@code RotationUtils.rotateBounds}) except
 * on the lock screen ({@code shouldRotate()}), which AOSP assumes never turns; so in landscape
 * it sat at the portrait spot, off the screen. That one exception is lifted while the lock
 * screen turns (not while going to sleep, as before). The lock icon over the sensor
 * (DefaultDeviceEntrySection) is placed from the sensor's unturned spot; it is given the spot
 * turned the same way, in the lock screen's own coordinates.
 *
 * <p>The overlay turns its {@code sensorBounds} in place on every layout (twice when it shows),
 * and after {@code updateOverlayParams} that Rect is the overlay parameters' own, so the
 * sensor's spot was turned again and again (the scanning animation landed near the top left).
 * While the lock screen turns, each layout starts {@code sensorBounds} from a fresh copy of the
 * unturned spot, so it is turned exactly once and the parameters stay unturned.
 *
 * <p>The PIN / pattern / password screen (bouncer): Nothing's layouts for it are portrait
 * only; in landscape the PIN pad was squeezed under the lock icon. SystemUI carries AOSP's own
 * landscape bouncer (the keyguard_*_motion_layout layouts, which jump to "split_constraints" in
 * landscape: entry beside the keys), switched on by the unreleased flag
 * lockscreen.enable_landscape, and update_bouncer_constraints is on for phones. That flag is
 * answered yes only inside the bouncer's own code (choosing and setting up its layout, and its
 * turn listener), nowhere else (not the shade or quick settings). Should that code ever fail,
 * the error is kept from System UI and the bouncer goes back to Nothing's layouts. The
 * bouncer's fingerprint icon is placed over the sensor as on the lock screen.
 */
final class LockScreenTurn {

    private static final String CLS_KEYGUARD_STATE =
            "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl";
    private static final String CLS_SHADE_WINDOW =
            "com.android.systemui.shade.NotificationShadeWindowView";
    private static final String CLS_BLUEPRINTS =
            "com.android.systemui.keyguard.data.repository.KeyguardBlueprintRepository";
    private static final String CLS_CLOCK_SECTION =
            "com.android.systemui.keyguard.ui.view.layout.sections.ClockSection";
    private static final String CLS_UDFPS_OVERLAY =
            "com.android.systemui.biometrics.UdfpsControllerOverlay";
    static final String CLS_ENTRY_SECTION =
            "com.android.systemui.keyguard.ui.view.layout.sections.DefaultDeviceEntrySection";
    private static final String CLS_NSSL_SECTION = "com.android.systemui.keyguard.ui.view.layout"
            + ".sections.DefaultNotificationStackScrollLayoutSection";
    private static final String CLS_WIDGET_SECTION =
            "com.nothing.systemui.keyguard.ui.view.layout.sections.NTWidgetContainerSection";
    private static final String CLS_BLUEPRINT_INTERACTOR =
            "com.android.systemui.keyguard.domain.interactor.KeyguardBlueprintInteractor";
    private static final String CLS_TRANSITION_TYPE = "com.android.systemui.keyguard.ui.view"
            + ".layout.blueprints.transitions.IntraBlueprintTransition$Type";
    /** Nothing's lock screen pieces kept to the left half beside the notifications. */
    /** The lock screen's charging / status line (Nothing's indication area). */
    private static final String INDICATION = "keyguard_indication_area";
    /** ConstraintSet's MATCH_PARENT width, as Nothing gives the indication area. */
    private static final int MATCH_PARENT = -1;
    private static final String[] LEFT_HALF = {"lockscreen_clock_view", "keyguard_slice_view",
            "nt_widget_host_view_container"};
    /** ConstraintSet sides and guideline orientation. */
    private static final int START = 6;
    private static final int END = 7;
    private static final int VERTICAL = 1;
    private static final int PARENT = 0;
    private static final int TOP = 3;

    /**
     * The fingerprint spot on the screen as last turned: its center, the rotation it is for
     * and the lock icon's radius around it (Nothing's centerIcon).
     */
    static volatile int[] turnedSpot;

    /** Set by the clock's constraints, used by the widgets' in the same pass (main thread). */
    private static android.content.Context halfContext;
    /** Whether the half layout was put on Nothing's pieces (to be taken off again). */
    private static boolean halfApplied;
    /** The notification container and its last updateConstraints args (main thread). */
    private static Object notifications;
    private static Object[] notificationArgs;
    private static ConfigStore settings;
    /** System UI's KeyguardBlueprintInteractor, once started (main thread). */
    private static Object blueprints;
    /** The rotation the lock screen's constraints were last applied for here (main thread). */
    private static int appliedRotation = -1;
    /**
     * While the pass runs here, the rotation it is for (else -1): the turn's own rotation is
     * known for certain there, whichever of configuration and display arrived first.
     */
    private static int passRotation = -1;
    /** The lock screen's window (NotificationShadeWindowView), once attached. */
    private static java.lang.ref.WeakReference<View> shadeWindow =
            new java.lang.ref.WeakReference<>(null);

    private LockScreenTurn() {
    }

    static void install(ClassLoader cl, ConfigStore store) {
        Class<?> state = XposedHelpers.findClassIfExists(CLS_KEYGUARD_STATE, cl);
        if (state == null) {
            Hooks.log("lock screen rotation: no " + CLS_KEYGUARD_STATE);
            return;
        }
        int hooked = XposedBridge.hookAllMethods(state, "isKeyguardScreenRotationAllowed",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (Boolean.TRUE.equals(param.getResult())) {
                            return;
                        }
                        store.reloadIfChanged();
                        if (store.get().lockRotation) {
                            param.setResult(true);
                        }
                    }
                }).size();
        keepNothingLayout(cl, store);
        turnFingerprint(cl, store);
        LandscapeBouncer.install(cl, store);
        settings = store;
        mirrorNotifications(cl, store);
        layOutOnFlip(cl, store);
        layOutWithTurn(cl, store);
        logFingerprint(cl, store);
        sensorTouches(cl, store);
        Class<?> window = XposedHelpers.findClassIfExists(CLS_SHADE_WINDOW, cl);
        if (window != null) {
            XposedHelpers.findAndHookMethod(window, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    View root = (View) param.thisObject;
                    shadeWindow = new java.lang.ref.WeakReference<>(root);
                    // Once per window (its tree observer): attached again to the same window,
                    // it added another listener, never removed, so the layout log ran once
                    // more on every layout (0.70.14 audit). A new window has a new observer.
                    android.view.ViewTreeObserver observer = root.getViewTreeObserver();
                    if (XposedHelpers.getAdditionalInstanceField(observer, "nlplus.layoutLog")
                            != null) {
                        return;
                    }
                    XposedHelpers.setAdditionalInstanceField(observer, "nlplus.layoutLog", true);
                    observer.addOnGlobalLayoutListener(
                            Hooks.guard("lock screen layout", () -> {
                                if (store.get().lockRotation
                                        && store.get().diagnosticLogging) {
                                    LockScreenLayoutLog.record(root);
                                }
                            })::run);
                }
            });
        }
        Hooks.log("lock screen rotation: " + hooked + " hooks, layout log "
                + (window != null ? "on" : "off"));
    }

    private static void keepNothingLayout(ClassLoader cl, ConfigStore store) {
        Class<?> blueprints = XposedHelpers.findClassIfExists(CLS_BLUEPRINTS, cl);
        Class<?> clock = XposedHelpers.findClassIfExists(CLS_CLOCK_SECTION, cl);
        Class<?> widgets = XposedHelpers.findClassIfExists(CLS_WIDGET_SECTION, cl);
        if (blueprints == null || clock == null || widgets == null) {
            Hooks.log("lock screen layout: classes not found, layout left to SystemUI");
            return;
        }
        XposedBridge.hookAllMethods(blueprints, "applyBlueprint", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if ("split-shade".equals(param.args[0]) && store.get().lockRotation) {
                    param.args[0] = "default";
                }
            }
        });
        // Nothing's layout stays, but its notification section is the portrait one: it starts
        // the notifications below the clock's column (logged nssl_placeholder top 1026 of a
        // 1080 high lock screen), so in landscape they were laid out off the screen and only
        // their icons showed. Turned with split shade, the placeholder's top is where AOSP's
        // split-shade section puts it (the screen's top, keyguard_split_shade_top_margin
        // down): the notifications fill their half from the top, as the split-shade layout
        // that was swapped out would have them.
        Class<?> notifications = XposedHelpers.findClassIfExists(CLS_NSSL_SECTION, cl);
        if (notifications != null) {
            XposedBridge.hookAllMethods(notifications, "applyConstraints", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (!store.get().lockRotation) {
                            return;
                        }
                        android.content.Context context = (android.content.Context)
                                XposedHelpers.callMethod(param.thisObject, "getContext");
                        Resources res = context.getResources();
                        String pkg = context.getPackageName();
                        int split = res.getIdentifier("config_use_split_notification_shade",
                                "bool", pkg);
                        int holder = res.getIdentifier("nssl_placeholder", "id", pkg);
                        int margin = res.getIdentifier("keyguard_split_shade_top_margin",
                                "dimen", pkg);
                        if (split == 0 || holder == 0 || margin == 0 || !res.getBoolean(split)
                                || res.getConfiguration().orientation
                                        != Configuration.ORIENTATION_LANDSCAPE) {
                            return;
                        }
                        XposedHelpers.callMethod(param.args[0], "connect", holder, TOP, PARENT,
                                TOP, res.getDimensionPixelSize(margin));
                    } catch (Throwable t) {
                        Hooks.log("lock screen notifications: " + t);
                    }
                }
            });
        }
        XposedBridge.hookAllMethods(clock, "applyConstraints", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                halfContext = null;
                try {
                    android.content.Context context = (android.content.Context)
                            XposedHelpers.getObjectField(param.thisObject, "context");
                    Object model = XposedHelpers.getObjectField(param.thisObject,
                            "keyguardClockViewModel");
                    Object centered = XposedHelpers.callMethod(XposedHelpers.callMethod(model,
                            "getClockShouldBeCentered"), "getValue");
                    Resources res = context.getResources();
                    int split = res.getIdentifier("config_use_split_notification_shade", "bool",
                            context.getPackageName());
                    // Halves whenever turned, with notifications or not: Nothing's own
                    // centered layout put its clock on the left over the sensor's icon with
                    // the sensor on the left (report at rotation 3), and with the sensor on
                    // the right it spread the widgets across the middle (718..1650), where
                    // the charging line under them ran into them (DebuG report: always-on
                    // display, widgets moved down to the bottom over "57% • Charging"). The
                    // halves keep Nothing's pieces on one side and the charging line on the
                    // other, the same in both turns.
                    boolean landscape = res.getConfiguration().orientation
                            == Configuration.ORIENTATION_LANDSCAPE;
                    boolean splitShade = split != 0 && res.getBoolean(split);
                    boolean left = sensorOnLeft(context);
                    boolean half = store.get().lockRotation && landscape && splitShade;
                    if (store.get().diagnosticLogging) {
                        logHalves("lock screen halves: centered " + centered + ", sensor "
                                + (left ? "left" : "right") + ", landscape " + landscape
                                + ", split shade " + splitShade + " -> "
                                + (half ? "half" : halfApplied ? "whole" : "Nothing's"));
                    }
                    if (half) {
                        halfContext = context;
                        // SystemUI also lays the clock out on its own (clock changes), cloning
                        // the lock screen's constraints and applying only the clock's: the
                        // clock kept the half's end but got its full width back (logged at
                        // -1008..1180, gone). So the half applies here too.
                        leftHalf(context, param.args[0]);
                        halfApplied = true;
                    } else if (halfApplied) {
                        // Back to Nothing's (portrait): the clock's edges
                        // back on the screen's, as Nothing has them. SystemUI's clock-only
                        // pass copies the lock screen's constraints, so the half's end stayed
                        // and the full-width clock sat half off the screen (-461..1727).
                        wholeWidth(context, param.args[0]);
                    }
                } catch (Throwable t) {
                    Hooks.log("lock screen layout: " + t);
                }
            }
        });
        XposedBridge.hookAllMethods(widgets, "applyConstraints", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                android.content.Context context = halfContext;
                halfContext = null;
                if (context == null) {
                    return;
                }
                try {
                    leftHalf(context, param.args[0]);
                } catch (Throwable t) {
                    Hooks.log("lock screen layout: " + t);
                }
            }
        });
    }

    /** The last halves decision logged, so a pass that decides the same writes nothing. */
    private static String lastHalves;

    private static void logHalves(String line) {
        if (!line.equals(lastHalves)) {
            lastHalves = line;
            Hooks.log(line);
        }
    }

    private static void turnFingerprint(ClassLoader cl, ConfigStore store) {
        Class<?> overlay = XposedHelpers.findClassIfExists(CLS_UDFPS_OVERLAY, cl);
        if (overlay != null) {
            XposedBridge.hookAllMethods(overlay, "shouldRotate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (Boolean.TRUE.equals(param.getResult()) || !store.get().lockRotation) {
                        return;
                    }
                    try {
                        Object monitor = XposedHelpers.getObjectField(param.thisObject,
                                "keyguardUpdateMonitor");
                        if (!(Boolean) XposedHelpers.callMethod(monitor, "isGoingToSleep")) {
                            param.setResult(true);
                        }
                    } catch (Throwable t) {
                        Hooks.log("lock screen fingerprint: " + t);
                    }
                }
            });
        }
        if (overlay != null) {
            XposedBridge.hookAllMethods(overlay, "updateDimensions", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!store.get().lockRotation) {
                        return;
                    }
                    try {
                        Object params = XposedHelpers.getObjectField(param.thisObject,
                                "overlayParams");
                        Rect natural = (Rect) XposedHelpers.callMethod(params,
                                "getSensorBounds");
                        XposedHelpers.setObjectField(param.thisObject, "sensorBounds",
                                new Rect(natural));
                    } catch (Throwable t) {
                        Hooks.log("lock screen fingerprint: " + t);
                    }
                }
            });
        }
        Class<?> entry = XposedHelpers.findClassIfExists(CLS_ENTRY_SECTION, cl);
        if (entry == null) {
            Hooks.log("lock screen fingerprint: no " + CLS_ENTRY_SECTION);
            return;
        }
        for (java.lang.reflect.Method m : entry.getDeclaredMethods()) {
            if (!m.getName().startsWith("centerIcon")) {
                continue;
            }
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!store.get().lockRotation) {
                        return;
                    }
                    try {
                        turnIcon(param, store.get().diagnosticLogging);
                    } catch (Throwable t) {
                        Hooks.log("lock screen fingerprint: " + t);
                    }
                }
            });
        }
        Hooks.log("lock screen fingerprint: " + (overlay != null ? "overlay turns" : "no overlay"));
    }

    /**
     * The lock icon over the fingerprint sensor: its spot (natural, unturned display
     * coordinates) turned as the sensor overlay turns it, then in the lock screen's coordinates.
     */
    private static void turnIcon(XC_MethodHook.MethodHookParam param, boolean log) {
        Object section = param.thisObject;
        Object model = XposedHelpers.callMethod(
                XposedHelpers.getObjectField(section, "deviceEntryIconViewModel"), "get");
        Object udfps = XposedHelpers.callMethod(XposedHelpers.callMethod(model,
                "isUdfpsSupported"), "getValue");
        if (!Boolean.TRUE.equals(udfps)) {
            return; // No sensor: the icon is placed from the screen's own size already.
        }
        android.view.WindowManager wm = (android.view.WindowManager)
                XposedHelpers.getObjectField(section, "windowManager");
        android.view.Display display = wm.getDefaultDisplay();
        int rotation = layoutRotation((android.content.Context)
                XposedHelpers.getObjectField(section, "context"));
        if (rotation != android.view.Surface.ROTATION_90
                && rotation != android.view.Surface.ROTATION_270) {
            return;
        }
        android.graphics.Point size = new android.graphics.Point();
        display.getRealSize(size);
        int naturalW = size.y; // Turned a quarter: the natural width is the height now.
        int naturalH = size.x;
        android.graphics.Point center = (android.graphics.Point) param.args[0];
        int x = center.x;
        int y = center.y;
        // RotationUtils.rotateBounds, as the sensor overlay turns the sensor's spot.
        int tx = rotation == android.view.Surface.ROTATION_90 ? y : naturalH - y;
        int ty = rotation == android.view.Surface.ROTATION_90 ? naturalW - x : x;
        int[] origin = rootOrigin(section, wm);
        param.args[0] = new android.graphics.Point(tx - origin[0], ty - origin[1]);
        int[] spot = {tx, ty, rotation, (int) (float) (Float) param.args[1]};
        int[] last = turnedSpot;
        turnedSpot = spot;
        if (!java.util.Arrays.equals(spot, last)) {
            // The notifications keep clear of the icon (mirrorNotifications): placed again
            // for its new spot, after this layout pass.
            new android.os.Handler(android.os.Looper.getMainLooper()).post(
                    Hooks.guard("lock screen notifications", LockScreenTurn::layOutNotifications));
        }
        if (!log) {
            return;
        }
        Hooks.log("lock screen fingerprint: icon at " + tx + "," + ty + " on screen (sensor "
                + x + "," + y + " unturned, rotation " + rotation + ", lock screen at "
                + origin[0] + "," + origin[1] + ")");
    }

    /**
     * Where the lock screen (the icon's ConstraintLayout) starts on the screen: as laid out if
     * it already is for this orientation, else the window's system bar and cutout insets.
     */
    private static int[] rootOrigin(Object section, android.view.WindowManager wm) {
        View panel = (View) XposedHelpers.getObjectField(section, "notificationPanelView");
        Rect bounds = wm.getCurrentWindowMetrics().getBounds();
        if (panel != null) {
            int id = panel.getResources().getIdentifier("keyguard_root_view", "id",
                    panel.getContext().getPackageName());
            View root = id != 0 ? panel.getRootView().findViewById(id) : null;
            if (root != null && root.getWidth() > 0
                    && (root.getWidth() > root.getHeight())
                            == (bounds.width() > bounds.height())) {
                int[] at = new int[2];
                root.getLocationOnScreen(at);
                return at;
            }
        }
        android.graphics.Insets insets = wm.getCurrentWindowMetrics().getWindowInsets()
                .getInsets(android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout());
        return new int[] {insets.left, 0};
    }

    /**
     * What the fingerprint does in landscape, in the LSPosed log: finger down / up on the
     * sensor overlay, where the overlay window is, and what the fingerprint service answers
     * (acquired, help, failed, error, authenticated). For finding why a read fails.
     */
    private static void logFingerprint(ClassLoader cl, ConfigStore store) {
        XC_MethodHook log = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if ((!store.get().lockRotation || !store.get().diagnosticLogging)) {
                    return;
                }
                StringBuilder args = new StringBuilder();
                for (Object a : param.args) {
                    if (!(a instanceof View)) {
                        args.append(args.length() == 0 ? "" : ", ").append(a);
                    }
                }
                Hooks.log("fingerprint: " + param.method.getName() + "(" + args + ")");
            }
        };
        Class<?> controller = XposedHelpers.findClassIfExists(
                "com.android.systemui.biometrics.UdfpsController", cl);
        if (controller != null) {
            for (String m : new String[] {"onFingerDown", "onFingerUp", "onFingerDownFromTP",
                    "onFingerUpFromTP"}) {
                XposedBridge.hookAllMethods(controller, m, log);
            }
            // "UI ready" to the fingerprint service (the light is up).
            for (java.lang.reflect.Method m : controller.getDeclaredMethods()) {
                if (m.getName().equals("lambda$onFingerDown$6")
                        || m.getName().equals("dispatchOnUiReady")) {
                    XposedBridge.hookMethod(m, log);
                }
            }
        }
        Class<?> monitor = XposedHelpers.findClassIfExists(
                "com.android.keyguard.KeyguardUpdateMonitor", cl);
        if (monitor != null) {
            for (String m : new String[] {"handleFingerprintAcquired", "handleFingerprintHelp",
                    "handleFingerprintAuthFailed", "handleFingerprintError",
                    "handleFingerprintAuthenticated"}) {
                XposedBridge.hookAllMethods(monitor, m, log);
            }
        }
        Class<?> overlay = XposedHelpers.findClassIfExists(CLS_UDFPS_OVERLAY, cl);
        if (overlay != null) {
            XposedBridge.hookAllMethods(overlay, "updateDimensions", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object lp = param.getResult();
                    if ((!store.get().lockRotation || !store.get().diagnosticLogging) || !(lp instanceof
                            android.view.WindowManager.LayoutParams)) {
                        return;
                    }
                    android.view.WindowManager.LayoutParams w =
                            (android.view.WindowManager.LayoutParams) lp;
                    Hooks.log("fingerprint: overlay window at " + w.x + "," + w.y + " size "
                            + w.width + "x" + w.height + ", sensor " + XposedHelpers
                            .getObjectField(param.thisObject, "sensorBounds"));
                }
            });
        }
    }

    /**
     * Whether a touch is on the sensor: Nothing's touch handling asks UdfpsUtils with the
     * touch as it is on the screen against the sensor's unturned bounds ("rotate to portrait"
     * false), right only in portrait. In landscape a finger on the sensor was not on it, so
     * the sensor window did not take the touch (pilferPointers) and the same finger pulled
     * the shade down. While the screen is turned the touch is turned to portrait first, with
     * UdfpsUtils' own getPortraitTouch, as AOSP does by default.
     */
    private static void sensorTouches(ClassLoader cl, ConfigStore store) {
        Class<?> utils = XposedHelpers.findClassIfExists(
                "com.android.systemui.biometrics.UdfpsUtils", cl);
        if (utils == null) {
            Hooks.log("fingerprint touch: no UdfpsUtils");
            return;
        }
        XC_MethodHook turnTouch = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Object[] a = param.args;
                if (a.length != 4 || !Boolean.FALSE.equals(a[3]) || !store.get().lockRotation) {
                    return;
                }
                try {
                    int rotation = (Integer) XposedHelpers.callMethod(a[2], "getRotation");
                    if (rotation == android.view.Surface.ROTATION_90
                            || rotation == android.view.Surface.ROTATION_270) {
                        a[3] = Boolean.TRUE;
                    }
                } catch (Throwable t) {
                    Hooks.log("fingerprint touch: " + t);
                }
            }
        };
        int n = XposedBridge.hookAllMethods(utils, "isWithinSensorArea", turnTouch).size()
                + XposedBridge.hookAllMethods(utils, "getTouchInNativeCoordinates", turnTouch)
                        .size();
        Hooks.log("fingerprint touch: " + n + " hooks");
    }

    /** Nothing's clock and charging line across the whole lock screen, as Nothing has them. */
    private static void wholeWidth(android.content.Context context, Object set) {
        Resources res = context.getResources();
        int id = res.getIdentifier("lockscreen_clock_view", "id", context.getPackageName());
        int[] known = (int[]) XposedHelpers.callMethod(set, "getKnownIds");
        if (id == 0 || !contains(known, id)) {
            return;
        }
        XposedHelpers.callMethod(set, "connect", id, START, PARENT, START);
        XposedHelpers.callMethod(set, "connect", id, END, PARENT, END);
        int indication = res.getIdentifier(INDICATION, "id", context.getPackageName());
        if (indication != 0 && contains(known, indication)) {
            // Nothing's: across the screen, as wide as it (DefaultIndicationAreaSection).
            XposedHelpers.callMethod(set, "constrainWidth", indication, MATCH_PARENT);
            XposedHelpers.callMethod(set, "connect", indication, START, PARENT, START);
            XposedHelpers.callMethod(set, "connect", indication, END, PARENT, END);
        }
    }

    /** Whether the screen is turned so the fingerprint sensor is on the left (270 degrees). */
    private static boolean sensorOnLeft(android.content.Context context) {
        return layoutRotation(context) == android.view.Surface.ROTATION_270;
    }

    /**
     * The rotation the lock screen is being laid out for. A turn reaches SystemUI twice: as
     * the new configuration, which starts the layout pass (its resources say landscape), and
     * as a display change, which updates the Display object; they can arrive in either order.
     * Read from the Display alone, a turn from portrait to the sensor-left side was laid out
     * for the old rotation (sensor right: Nothing's pieces on the left, no ring) and mirrored
     * a pass later, about 0.25 s on (DebuG18 video, 28.9 s). So: the Display's rotation when
     * it is already a landscape one (a flip from one side to the other changes no
     * orientation and is laid out on the display change: layOutOnFlip), otherwise the
     * rotation of the configuration being laid out.
     */
    static int layoutRotation(android.content.Context context) {
        if (passRotation >= 0) {
            return passRotation; // Laid out here with the turn: its own rotation.
        }
        int display = 0;
        android.view.WindowManager wm = context == null ? null
                : (android.view.WindowManager) context.getSystemService(
                        android.content.Context.WINDOW_SERVICE);
        if (wm != null) {
            display = wm.getDefaultDisplay().getRotation();
        }
        if (isLandscapeRotation(display) || context == null) {
            return display;
        }
        try {
            Object window = XposedHelpers.getObjectField(
                    context.getResources().getConfiguration(), "windowConfiguration");
            int configured = (Integer) XposedHelpers.callMethod(window, "getDisplayRotation");
            if (isLandscapeRotation(configured)) {
                return configured;
            }
        } catch (Throwable t) {
            // Hidden API missing: the Display's rotation.
        }
        return display;
    }

    /** The StatusBarState of the lock screen itself (not the shade pulled over it). */
    private static final int STATE_KEYGUARD = 1;

    /**
     * With the sensor on the left, the lock screen's notifications go to the left half, beside
     * Nothing's pieces on the right. SystemUI's SharedNotificationContainer puts them from its
     * middle guideline to the right edge ("MiddleToEdge"); while the lock screen itself shows
     * (not the shade pulled over it, where quick settings take the left half) they go from the
     * left edge to that guideline instead, otherwise as SystemUI places them. On either side
     * the column keeps clear of the fingerprint icon (clearOfSensor). Laid out again when that
     * state changes.
     */
    private static void mirrorNotifications(ClassLoader cl, ConfigStore store) {
        Class<?> container = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.notification.stack.ui.view"
                        + ".SharedNotificationContainer", cl);
        Class<?> dependency = XposedHelpers.findClassIfExists("com.android.systemui.Dependency",
                cl);
        Class<?> stateClass = XposedHelpers.findClassIfExists(
                "com.android.systemui.plugins.statusbar.StatusBarStateController", cl);
        Class<?> listenerClass = XposedHelpers.findClassIfExists(
                "com.android.systemui.plugins.statusbar.StatusBarStateController$StateListener",
                cl);
        if (container == null || dependency == null || stateClass == null
                || listenerClass == null) {
            Hooks.log("lock screen notifications: classes not found");
            return;
        }
        boolean[] listening = {false};
        XposedBridge.hookAllMethods(container, "updateConstraints", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length != 6) {
                    return;
                }
                notifications = param.thisObject;
                notificationArgs = param.args.clone();
                if (!store.get().lockRotation) {
                    return;
                }
                try {
                    Object state = XposedHelpers.callStaticMethod(dependency, "get", stateClass);
                    if (!listening[0] && state != null) {
                        listening[0] = true;
                        XposedHelpers.callMethod(state, "addCallback", java.lang.reflect.Proxy
                                .newProxyInstance(cl, new Class<?>[] {listenerClass},
                                        (proxy, method, args) -> {
                                            if (method.getName().equals("onStateChanged")) {
                                                // Each state: into the lock screen the
                                                // placement here applies; out of it (the
                                                // shade pulled down, unlocked) SystemUI's own
                                                // comes back. SystemUI places them again only
                                                // when its values change, and turned they
                                                // often do not: the lock screen's placement
                                                // stayed in the shade (DebuG19: the stack
                                                // over quick settings over an app, 419 px
                                                // short of the edge on the lock screen).
                                                // Guarded: inside SystemUI's state dispatch.
                                                Hooks.guard("lock screen notifications",
                                                        LockScreenTurn::layOutNotifications)
                                                        .run();
                                            }
                                            // Object's own methods answered as an
                                            // object would: null for hashCode would throw
                                            // in System UI if it ever kept its listeners
                                            // in a set (0.70.14 audit).
                                            switch (method.getName()) {
                                                case "hashCode":
                                                    return System.identityHashCode(proxy);
                                                case "equals":
                                                    return args != null && proxy == args[0];
                                                case "toString":
                                                    return "Nothing2Something lock screen"
                                                            + " listener";
                                                default:
                                                    break;
                                            }
                                            return method.getReturnType() == boolean.class
                                                    ? Boolean.FALSE : null;
                                        }));
                    }
                    View view = (View) param.thisObject;
                    if (!param.args[0].getClass().getSimpleName().equals("MiddleToEdge")
                            || state == null
                            || (Integer) XposedHelpers.callMethod(state, "getState")
                                    != STATE_KEYGUARD) {
                        return;
                    }
                    boolean left = sensorOnLeft(view.getContext());
                    int clear = clearOfSensor(view, left);
                    if (!left) {
                        // Sensor on the right: SystemUI's own end margin, widened to end
                        // the notifications before the fingerprint icon.
                        param.args[3] = Math.max((Integer) param.args[3], clear);
                        return;
                    }
                    param.args[1] = Math.max((Integer) param.args[1], clear);
                    Resources res = view.getResources();
                    String pkg = view.getContext().getPackageName();
                    int nssl = res.getIdentifier("notification_stack_scroller", "id", pkg);
                    int guide = res.getIdentifier("nssl_guideline", "id", pkg);
                    Object base = XposedHelpers.getObjectField(view, "baseConstraintSet");
                    if (nssl == 0 || guide == 0 || base == null) {
                        return;
                    }
                    Object set = XposedHelpers.newInstance(base.getClass());
                    XposedHelpers.callMethod(set, "clone", base);
                    XposedHelpers.callMethod(set, "setAlpha", nssl, (Float) param.args[5]);
                    XposedHelpers.callMethod(set, "connect", nssl, START, PARENT, START,
                            (Integer) param.args[1]);
                    XposedHelpers.callMethod(set, "connect", nssl, END, guide, END,
                            (Integer) param.args[3]);
                    XposedHelpers.callMethod(set, "connect", nssl, 4, PARENT, 4,
                            (Integer) param.args[4]);
                    XposedHelpers.callMethod(set, "connect", nssl, 3, PARENT, 3,
                            (Integer) param.args[2]);
                    XposedHelpers.callMethod(set, "applyTo", view);
                    param.setResult(null);
                } catch (Throwable t) {
                    Hooks.log("lock screen notifications: " + t);
                }
            }
        });
    }

    /**
     * How far from the notification container's edge on the fingerprint's side the
     * notifications keep (px): past the turned lock icon over the sensor and a gap, so no
     * notification, however many or however tall, reaches the circle (the column's top half
     * ran over it: icon at x 198..384 under a column from 85). 0 when the icon's spot for
     * this rotation is not known yet; placed again once it is (turnIcon).
     */
    static int clearOfSensor(View container, boolean left) {
        int[] spot = turnedSpot;
        if (spot == null || spot.length < 4 || container.getWidth() <= 0) {
            return 0;
        }
        if (layoutRotation(container.getContext()) != spot[2]) {
            return 0;
        }
        int[] at = new int[2];
        container.getLocationOnScreen(at);
        int gap = Math.round(16 * container.getResources().getDisplayMetrics().density);
        int clear = left ? spot[0] + spot[3] + gap - at[0]
                : at[0] + container.getWidth() - (spot[0] - spot[3] - gap);
        // Never more than half the container: the notifications keep a usable width.
        return Math.max(0, Math.min(clear, container.getWidth() / 2));
    }

    /**
     * SystemUI's notification placement, done again (the fingerprint icon's spot changed, or
     * the shade's state changed: on the lock screen the clearance is placed for it, elsewhere
     * SystemUI's own placement is restored): with its last margins,
     * which change only with the configuration (and a new configuration places them anew),
     * but the notifications' alpha as it is now, never an earlier one (SystemUI fades them
     * through this same call). Only while the turned lock screen is in use, in landscape.
     */
    private static void layOutNotifications() {
        Object container = notifications;
        Object[] args = notificationArgs;
        ConfigStore store = settings;
        if (container == null || args == null || args.length != 6 || store == null
                || !store.get().lockRotation) {
            return;
        }
        View view = (View) container;
        if (view.getResources().getConfiguration().orientation
                != android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            return;
        }
        Object[] now = args.clone();
        int nssl = view.getResources().getIdentifier("notification_stack_scroller", "id",
                view.getContext().getPackageName());
        View stack = nssl != 0 ? view.findViewById(nssl) : null;
        if (stack != null) {
            now[5] = stack.getAlpha();
        }
        XposedHelpers.callMethod(container, "updateConstraints", now);
    }

    /**
     * Turning straight from one landscape side to the other (sensor right, sensor left) keeps
     * the screen's size and orientation, so Android reports no configuration change to System
     * UI and Nothing never lays its lock screen out again: the lock icon stayed over the
     * sensor's old side (logged: no new icon placement after rotation 1 to 3) and the clock and
     * notifications kept their old halves. On that turn System UI is asked for the same
     * relayout it does on a configuration change (KeyguardBlueprintInteractor.refreshBlueprint,
     * which places the icon, clock and widgets again) and the notifications are placed again.
     */
    private static void layOutOnFlip(ClassLoader cl, ConfigStore store) {
        Class<?> interactor = XposedHelpers.findClassIfExists(CLS_BLUEPRINT_INTERACTOR, cl);
        Class<?> type = XposedHelpers.findClassIfExists(CLS_TRANSITION_TYPE, cl);
        if (interactor == null || type == null) {
            Hooks.log("lock screen flip: classes not found");
            return;
        }
        XposedBridge.hookAllMethods(interactor, "start", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                blueprints = param.thisObject;
                android.app.Application app = android.app.AndroidAppHelper.currentApplication();
                android.hardware.display.DisplayManager displays = app == null ? null
                        : app.getSystemService(android.hardware.display.DisplayManager.class);
                if (displays == null) {
                    Hooks.log("lock screen flip: no display manager");
                    return;
                }
                android.view.Display display = displays.getDisplay(
                        android.view.Display.DEFAULT_DISPLAY);
                int[] last = {display != null ? display.getRotation() : 0};
                displays.registerDisplayListener(new android.hardware.display.DisplayManager
                        .DisplayListener() {
                    @Override
                    public void onDisplayChanged(int displayId) {
                        if (displayId != android.view.Display.DEFAULT_DISPLAY) {
                            return;
                        }
                        try {
                            android.view.Display d = displays.getDisplay(displayId);
                            int rotation = d != null ? d.getRotation() : last[0];
                            boolean flipped = rotation != last[0]
                                    && isLandscapeRotation(rotation)
                                    && isLandscapeRotation(last[0]);
                            last[0] = rotation;
                            if (!flipped || !store.get().lockRotation) {
                                return;
                            }
                            if (rotation == appliedRotation) {
                                return; // Already laid out with the turn (layOutWithTurn).
                            }
                            if (!applyNow(cl, rotation)) {
                                @SuppressWarnings({"unchecked", "rawtypes"})
                                Object none = Enum.valueOf((Class) type, "NoTransition");
                                XposedHelpers.callMethod(blueprints, "refreshBlueprint", none);
                            }
                            layOutNotifications();
                            Hooks.log("lock screen flip: laid out again for rotation "
                                    + rotation);
                        } catch (Throwable t) {
                            Hooks.log("lock screen flip: " + t);
                        }
                    }

                    @Override
                    public void onDisplayAdded(int displayId) {
                    }

                    @Override
                    public void onDisplayRemoved(int displayId) {
                    }
                }, new android.os.Handler(android.os.Looper.getMainLooper()));
                Hooks.log("lock screen flip: watching turns");
            }
        });
    }

    /**
     * The lock screen laid out for a turn in the frame the turn is drawn in.
     *
     * <p>How it was found (DebuG23, a recording on battery saving with the log beside it): after
     * each turn, the first landscape frames of the lock screen were drawn with the portrait
     * constraints (weather pills across the middle, no notifications, no fingerprint ring, the
     * clock on the wrong side), and 0.35 to 0.8 s later everything jumped into its halves, in
     * the very pass the log shows as "lock screen halves". That pass is System UI's own:
     * a configuration change reaches the lock screen as a coroutine (KeyguardBlueprintInteractor
     * on onAnyConfigurationChange), then a handler post (KeyguardBlueprintRepository
     * .refreshBlueprint), then a flow the view binder collects (KeyguardBlueprintViewBinder:
     * clone the constraints, KeyguardBlueprint.applyConstraints, applyTo). Each hop waits its
     * turn on the main thread, and the frame for the new size is drawn first (Android blocks
     * ordinary messages until it is). Portrait-only Nothing OS never shows it: there both
     * orientations would have the same layout.
     *
     * <p>So the module does not move anything afterwards: as the new configuration reaches the
     * lock screen's window (NotificationShadeWindowView.onConfigurationChanged, before that
     * frame is laid out), it runs that same constraint pass of System UI's at once. System UI's
     * own pass still comes and finds the constraints as it would set them.
     */
    private static void layOutWithTurn(ClassLoader cl, ConfigStore store) {
        Class<?> window = XposedHelpers.findClassIfExists(CLS_SHADE_WINDOW, cl);
        if (window == null) {
            Hooks.log("lock screen turn: no " + CLS_SHADE_WINDOW);
            return;
        }
        XposedHelpers.findAndHookMethod(window, "onConfigurationChanged", Configuration.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!store.get().lockRotation) {
                            return;
                        }
                        Hooks.guard("lock screen turn", () -> {
                            View window = (View) param.thisObject;
                            shadeWindow = new java.lang.ref.WeakReference<>(window);
                            int rotation = configuredRotation((Configuration) param.args[0]);
                            if (rotation >= 0 && rotation != appliedRotation) {
                                applyNow(cl, rotation);
                            }
                        }).run();
                    }
                });
    }

    /**
     * System UI's lock screen constraint pass, done now, as its view binder does it on a
     * refresh without a transition. False if it could not be done here (not started yet).
     */
    private static boolean applyNow(ClassLoader cl, int rotation) {
        Object interactor = blueprints;
        if (interactor == null) {
            return false;
        }
        try {
            Object blueprint = XposedHelpers.callMethod(interactor, "getCurrentBlueprint");
            View window = shadeWindow.get();
            if (blueprint == null || window == null) {
                return false;
            }
            int id = window.getResources().getIdentifier("keyguard_root_view", "id",
                    window.getContext().getPackageName());
            View root = id != 0 ? window.findViewById(id) : null;
            if (root == null) {
                return false;
            }
            Class<?> layout = cl.loadClass("androidx.constraintlayout.widget.ConstraintLayout");
            Class<?> setClass = cl.loadClass("androidx.constraintlayout.widget.ConstraintSet");
            Object set = setClass.getConstructor().newInstance();
            setClass.getMethod("clone", layout).invoke(set, root);
            passRotation = rotation;
            try {
                cl.loadClass("com.android.systemui.keyguard.shared.model.KeyguardBlueprint")
                        .getMethod("applyConstraints", setClass).invoke(blueprint, set);
            } finally {
                passRotation = -1;
            }
            setClass.getMethod("applyTo", layout).invoke(set, root);
            appliedRotation = rotation;
            if (settings != null && settings.get().diagnosticLogging) {
                Hooks.log("lock screen turn: laid out with the turn to rotation " + rotation);
            }
            return true;
        } catch (Throwable t) {
            Hooks.log("lock screen turn: " + t);
            return false;
        }
    }

    /** The display rotation a configuration is for, or -1 if it does not say. */
    private static int configuredRotation(Configuration config) {
        try {
            Object window = XposedHelpers.getObjectField(config, "windowConfiguration");
            return (Integer) XposedHelpers.callMethod(window, "getDisplayRotation");
        } catch (Throwable t) {
            return -1; // Hidden API missing: System UI's own pass places it.
        }
    }

    private static boolean isLandscapeRotation(int rotation) {
        return rotation == android.view.Surface.ROTATION_90
                || rotation == android.view.Surface.ROTATION_270;
    }

    /** Ends Nothing's clock, date and widgets at the middle of the lock screen. */
    private static void leftHalf(android.content.Context context, Object set) {
        Resources res = context.getResources();
        String pkg = context.getPackageName();
        int guide = res.getIdentifier("split_shade_guideline", "id", pkg);
        if (guide == 0) {
            return;
        }
        int[] known = (int[]) XposedHelpers.callMethod(set, "getKnownIds");
        XposedHelpers.callMethod(set, "create", guide, VERTICAL);
        XposedHelpers.callMethod(set, "setGuidelinePercent", guide, 0.5f);
        boolean mirrored = sensorOnLeft(context);
        for (String name : LEFT_HALF) {
            int id = res.getIdentifier(name, "id", pkg);
            if (id != 0 && contains(known, id)) {
                if (mirrored) {
                    // The fingerprint sensor is on the left: Nothing's pieces on the right
                    // half, the notifications on the left (mirrorNotifications).
                    XposedHelpers.callMethod(set, "connect", id, START, guide, END);
                    XposedHelpers.callMethod(set, "connect", id, END, PARENT, END);
                } else {
                    XposedHelpers.callMethod(set, "connect", id, START, PARENT, START);
                    XposedHelpers.callMethod(set, "connect", id, END, guide, START);
                }
                if (name.equals("lockscreen_clock_view")) {
                    // Nothing measures its clock as wide as the screen; kept that wide it
                    // stuck out past the left edge (logged at -1008..1180): the half's width.
                    XposedHelpers.callMethod(set, "constrainWidth", id, 0);
                }
            }
        }
        // The charging / status line under it all: Nothing centers it across the screen
        // (DefaultIndicationAreaSection), which turned put it over the widgets' bottom
        // (DebuG6: "37% • Charging rapidly" at 975..1384 x 952..1056, the widgets to y 999
        // in the half 85..1180), on the lock screen and the always-on display. It goes to
        // the other half, under the notifications and the fingerprint icon, which leave the
        // bottom free; its width follows the half (Nothing's is the parent's).
        int indication = res.getIdentifier(INDICATION, "id", pkg);
        if (indication != 0 && contains(known, indication)) {
            XposedHelpers.callMethod(set, "constrainWidth", indication, 0);
            if (mirrored) {
                XposedHelpers.callMethod(set, "connect", indication, START, PARENT, START);
                XposedHelpers.callMethod(set, "connect", indication, END, guide, START);
            } else {
                XposedHelpers.callMethod(set, "connect", indication, START, guide, END);
                XposedHelpers.callMethod(set, "connect", indication, END, PARENT, END);
            }
        }
    }

    private static boolean contains(int[] ids, int id) {
        for (int i : ids) {
            if (i == id) {
                return true;
            }
        }
        return false;
    }
}
