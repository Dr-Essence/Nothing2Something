package com.dressence.nothinglauncherplus.xposed;

import java.lang.reflect.Member;
import java.lang.reflect.Method;

import de.robv.android.xposed.XposedBridge;

/**
 * What every hook shares: logging, installing a feature so its failure is its own, guarding
 * code the hooked app calls back into, and deoptimizing methods whose callees are hooked.
 *
 * <p>Exceptions thrown inside an Xposed hook callback are caught by the framework, but code
 * the module hands to the app (a Runnable it posts, a listener, a receiver, a Drawable it
 * draws) runs as the app's own: an exception there crashes the app. Such code goes through
 * {@link #guard}.
 */
public final class Hooks {

    private static final String TAG = "Nothing2Something: ";

    public interface Installer {
        void run() throws Throwable;
    }

    private Hooks() {
    }

    public static void log(String message) {
        try {
            XposedBridge.log(TAG + message);
        } catch (Throwable notHooked) {
            // In the module's own app (the overlay) there is no Xposed: Android's log.
            android.util.Log.i("Nothing2Something", message);
        }
    }

    /**
     * Each feature's result in this process, for the hook health report ({@link HookReport}):
     * {name, ""} when it installed, {name, why} when it did not or skipped a part.
     */
    private static final java.util.List<String[]> RESULTS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Installs one feature; if it fails it is logged and skipped, the others still work. */
    public static void install(String name, Installer installer) {
        try {
            installer.run();
            RESULTS.add(new String[] {name, ""});
        } catch (Throwable t) {
            log("failed to install " + name + ": " + t);
            RESULTS.add(new String[] {name, t.toString()});
        }
    }

    /**
     * A part of a feature left out because what it needs is not in this build of the app (a
     * class, method or view not found): logged, and listed in the hook health report, so an
     * update of Nothing OS that moves something shows which part stopped.
     */
    public static void skipped(String what, String why) {
        log(what + ": " + why);
        String reason = "skipped: " + why;
        // Listed once: some parts check again on every layout, and the list must not grow.
        for (String[] r : RESULTS) {
            if (r[0].equals(what) && r[1].equals(reason)) {
                return;
            }
        }
        RESULTS.add(new String[] {what, reason});
    }

    /** This process's results so far (a copy). */
    static java.util.List<String[]> results() {
        return new java.util.ArrayList<>(RESULTS);
    }

    /** {@code task}, made safe to hand to the app: what it throws is logged, not raised. */
    public static Runnable guard(String what, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable t) {
                log(what + " failed: " + t);
            }
        };
    }

    private static Method deoptimizeMethod;
    private static boolean deoptimizeLookedUp;

    /**
     * Forces {@code member} to run interpreted so callees it inlined (small getters we hook) go
     * through their hooks again. LSPosed exposes this outside the public API 82 surface.
     */
    public static synchronized void deoptimize(Member member) {
        if (!deoptimizeLookedUp) {
            deoptimizeLookedUp = true;
            try {
                deoptimizeMethod = XposedBridge.class.getDeclaredMethod(
                        "deoptimizeMethod", Member.class);
            } catch (NoSuchMethodException e) {
                log("deoptimizeMethod unavailable; hooks may be bypassed if ART inlined them");
            }
        }
        if (deoptimizeMethod == null) {
            return;
        }
        try {
            deoptimizeMethod.invoke(null, member);
        } catch (Throwable t) {
            log("deoptimize " + member + " failed: " + t);
        }
    }

    public static void deoptimizeAll(Class<?> cls, String methodName) {
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                deoptimize(m);
            }
        }
    }
}
