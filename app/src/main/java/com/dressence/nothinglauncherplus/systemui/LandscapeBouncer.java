package com.dressence.nothinglauncherplus.systemui;

import android.view.View;
import android.view.ViewGroup;

import com.dressence.nothinglauncherplus.xposed.ConfigStore;
import com.dressence.nothinglauncherplus.xposed.Hooks;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * The PIN / pattern / password screen (bouncer) in landscape, while the lock screen turns
 * (LockScreenTurn): AOSP's own landscape bouncer, switched on by answering the unreleased flag
 * lockscreen.enable_landscape yes only inside the bouncer's own code, with Nothing's layouts
 * back should that code ever fail; the bouncer's fingerprint icon over the sensor's turned spot,
 * and Nothing's lock glyph and message margin out of the way in landscape. Moved out of
 * LockScreenTurn unchanged (0.70.13, cleanup step 4); LockScreenTurn's description has the
 * whole story.
 */
final class LandscapeBouncer {

    private static final String CLS_FLAGS = "com.android.systemui.flags.Flags";
    private static final String[] CLS_FEATURE_FLAGS = {
            "com.android.systemui.flags.FeatureFlagsClassicRelease",
            "com.android.systemui.flags.FeatureFlagsClassicDebug"};
    private static final String CLS_FLIPPER =
            "com.android.keyguard.KeyguardSecurityViewFlipperController";
    private static final String CLS_SECURITY_CONTAINER =
            "com.android.keyguard.KeyguardSecurityContainerController";

    /** Inside the bouncer's own code, where the landscape flag is answered yes. */
    private static final ThreadLocal<int[]> BOUNCER = ThreadLocal.withInitial(() -> new int[1]);
    /** Set if the bouncer's landscape code failed once: Nothing's layouts from then on. */
    private static volatile boolean bouncerFailed;

    private LandscapeBouncer() {
    }

    /**
     * The bouncer's fingerprint icon (Nothing centers it across and sets only its top, for
     * portrait): over the sensor's turned spot, as the lock screen's icon.
     */
    private static void turnBouncerIcon(Object section, boolean on) {
        int[] spot = LockScreenTurn.turnedSpot;
        View icon = (View) XposedHelpers.getObjectField(section, "bouncerUdfpsIconContainer");
        if (icon == null || !(icon.getLayoutParams()
                instanceof android.widget.FrameLayout.LayoutParams)) {
            return;
        }
        android.widget.FrameLayout.LayoutParams lp =
                (android.widget.FrameLayout.LayoutParams) icon.getLayoutParams();
        if (!on || spot == null || LockScreenTurn.layoutRotation((android.content.Context)
                XposedHelpers.getObjectField(section, "context")) != spot[2]) {
            // Nothing's own placement (also when the lock screen no longer turns) (centered across, its top set): Nothing never sets the
            // left margin, so the one set here for landscape goes (it had pushed the icon
            // off the screen, logged at x 3017).
            if (lp.leftMargin != 0) {
                lp.leftMargin = 0;
                icon.setLayoutParams(lp);
            }
            return;
        }
        int[] origin = {0, 0};
        if (icon.getParent() instanceof View && ((View) icon.getParent()).getWidth() > 0) {
            ((View) icon.getParent()).getLocationOnScreen(origin);
        }
        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
        lp.leftMargin = spot[0] - lp.width / 2 - origin[0];
        lp.topMargin = spot[1] - lp.height / 2 - origin[1];
        icon.setLayoutParams(lp);
    }

    /**
     * Nothing's bouncer lock icon (a lock glyph above the PIN, 348 px from the top for
     * portrait) sat between the landscape bouncer's two halves, over its message: left out in
     * landscape, back as Nothing had it when the screen turns back.
     */
    private static void landscapeBouncerLock(Object section, boolean on) {
        View lock = (View) XposedHelpers.getObjectField(section, "bouncerlockIconContainer");
        if (lock == null) {
            return;
        }
        int rotation = LockScreenTurn.layoutRotation((android.content.Context)
                XposedHelpers.getObjectField(section, "context"));
        boolean turned = on && (rotation == android.view.Surface.ROTATION_90
                || rotation == android.view.Surface.ROTATION_270);
        String key = "nlplus.hiddenLock";
        if (turned && lock.getVisibility() == View.VISIBLE) {
            lock.setVisibility(View.GONE);
            XposedHelpers.setAdditionalInstanceField(lock, key, Boolean.TRUE);
        } else if (!turned && XposedHelpers.removeAdditionalInstanceField(lock, key) != null) {
            lock.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Nothing places the bouncer's message 448 px down (nt_message_area_margin_top), under its
     * portrait lock icon; in the landscape bouncer that pushed the PIN entry below the
     * bouncer's bottom (logged 902..1039 against 976). In landscape the message starts at the
     * top of its half; Nothing's margin comes back in portrait.
     */
    private static void messageMargin(View bouncer, boolean landscape) {
        int id = bouncer.getResources().getIdentifier("bouncer_message_area", "id",
                bouncer.getContext().getPackageName());
        View message = id != 0 ? bouncer.findViewById(id) : null;
        if (message == null
                || !(message.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) message.getLayoutParams();
        String key = "nlplus.messageTop";
        Object nothing = XposedHelpers.getAdditionalInstanceField(message, key);
        if (landscape) {
            if (nothing == null) {
                XposedHelpers.setAdditionalInstanceField(message, key, lp.topMargin);
            }
            lp.topMargin = 0;
        } else if (nothing != null) {
            lp.topMargin = (Integer) nothing;
            XposedHelpers.removeAdditionalInstanceField(message, key);
        } else {
            return;
        }
        message.setLayoutParams(lp);
    }

    static void install(ClassLoader cl, ConfigStore store) {
        Class<?> flags = XposedHelpers.findClassIfExists(CLS_FLAGS, cl);
        Class<?> flipper = XposedHelpers.findClassIfExists(CLS_FLIPPER, cl);
        if (flags == null || flipper == null) {
            Hooks.skipped("lock screen bouncer", "classes not found, Nothing's layouts kept");
            return;
        }
        Object flag = XposedHelpers.getStaticObjectField(flags, "LOCKSCREEN_ENABLE_LANDSCAPE");
        int answered = 0;
        for (String name : CLS_FEATURE_FLAGS) {
            Class<?> impl = XposedHelpers.findClassIfExists(name, cl);
            if (impl == null) {
                continue;
            }
            answered += XposedBridge.hookAllMethods(impl, "isEnabled", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length == 1 && param.args[0] == flag
                            && BOUNCER.get()[0] > 0 && !bouncerFailed) {
                        param.setResult(true);
                    }
                }
            }).size();
        }
        XC_MethodHook scope = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (store.get().lockRotation) {
                    BOUNCER.get()[0]++;
                    param.setObjectExtra("nlplus.scoped", Boolean.TRUE);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.getObjectExtra("nlplus.scoped") == null) {
                    return;
                }
                BOUNCER.get()[0]--;
                Throwable failed = param.getThrowable();
                if (failed != null) {
                    // Kept from System UI: the bouncer goes back to Nothing's layouts.
                    bouncerFailed = true;
                    param.setResult(null);
                    Hooks.log("lock screen bouncer: landscape layout failed, Nothing's kept: "
                            + android.util.Log.getStackTraceString(failed));
                }
            }
        };
        int scoped = 0;
        for (java.lang.reflect.Method m : flipper.getDeclaredMethods()) {
            if (m.getName().equals("asynchronouslyInflateView")
                    || m.getName().startsWith("lambda$asynchronouslyInflateView")) {
                XposedBridge.hookMethod(m, scope);
                scoped++;
            }
        }
        // The bouncer's turn listener (an inner class of the security container controller).
        for (int i = 1; i <= 30; i++) {
            Class<?> inner = XposedHelpers.findClassIfExists(CLS_SECURITY_CONTAINER + "$" + i, cl);
            if (inner == null) {
                continue;
            }
            for (java.lang.reflect.Method m : inner.getDeclaredMethods()) {
                if (m.getName().equals("onOrientationChanged")) {
                    XposedBridge.hookMethod(m, scope);
                    scoped++;
                }
            }
        }
        Class<?> entry = XposedHelpers.findClassIfExists(LockScreenTurn.CLS_ENTRY_SECTION, cl);
        if (entry != null) {
            XposedBridge.hookAllMethods(entry, "updateBouncerLockIconContainer",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            // Off: what was changed for landscape goes back to Nothing's.
                            boolean on = store.get().lockRotation;
                            try {
                                turnBouncerIcon(param.thisObject, on);
                                landscapeBouncerLock(param.thisObject, on);
                            } catch (Throwable t) {
                                Hooks.log("lock screen bouncer: " + t);
                            }
                        }
                    });
        }
        for (String view : new String[] {"com.android.keyguard.KeyguardPINView",
                "com.android.keyguard.KeyguardPatternView",
                "com.android.keyguard.KeyguardPasswordView"}) {
            Class<?> c = XposedHelpers.findClassIfExists(view, cl);
            if (c != null) {
                XposedBridge.hookAllMethods(c, "updateConstraints", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.args.length != 1) {
                            return;
                        }
                        try {
                            // Off: Nothing's margin back (messageMargin restores it).
                            messageMargin((View) param.thisObject, store.get().lockRotation
                                    && Boolean.TRUE.equals(param.args[0]));
                        } catch (Throwable t) {
                            Hooks.log("lock screen bouncer: " + t);
                        }
                    }
                });
            }
        }
        Hooks.log("lock screen bouncer: flag answered in " + answered + ", bouncer code "
                + scoped);
    }
}
