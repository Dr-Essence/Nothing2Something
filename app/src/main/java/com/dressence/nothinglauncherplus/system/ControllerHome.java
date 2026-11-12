package com.dressence.nothinglauncherplus.system;

import com.dressence.nothinglauncherplus.Controllers;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.KeyEvent;

import com.dressence.nothinglauncherplus.Config;
import com.dressence.nothinglauncherplus.xposed.ConfigStore;
import com.dressence.nothinglauncherplus.xposed.Hooks;

import java.util.List;
import java.util.function.Consumer;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Gaming launcher while a controller is connected: the home app (Android's HOME role) becomes
 * the launcher picked in the settings when a game controller connects (USB or Bluetooth), and
 * goes back to the one it was when the last controller disconnects, or the setting is turned
 * off. It is Android's own home choice that changes, as when the user picks a home app, so the
 * home button, the home gesture (Launcher3's Quickstep follows the default home and hands it
 * over) and everything else that opens home agree. Nothing is changed while no controller is
 * connected; the home app it replaced is kept in a global setting, so a restart with no
 * controller puts it back, and it is put back only while the gaming launcher is still home (a
 * home app the user picked meanwhile stays).
 *
 * <p>The controller's own home button (PS, Xbox, Guide: {@code KEYCODE_BUTTON_MODE}), which
 * Android's window policy passes to the app in front, opens home while it is on.
 */
public final class ControllerHome {

    private static final String CLS_POLICY = "com.android.server.policy.PhoneWindowManager";
    private static final String ROLE_HOME = "android.app.role.HOME";
    /** The home app the gaming launcher replaced, while it is home. */
    private static final String SAVED = "nlplus_home_before_controller";
    /** A controller's connection settles (Bluetooth controllers can drop and come back). */
    private static final long SETTLE_MS = 1500;
    /** Uids per user (UserHandle.PER_USER_RANGE). */
    private static final int PER_USER_RANGE = 100000;

    private static Context context;
    private static Handler handler;
    private static ConfigStore store;
    private static volatile boolean active;

    private ControllerHome() {
    }

    public static void install(ClassLoader cl, ConfigStore configStore) {
        store = configStore;
        XposedHelpers.findAndHookMethod(CLS_POLICY, cl, "systemReady",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (context != null) {
                            return;
                        }
                        Hooks.guard("controller home start", () -> start((Context)
                                XposedHelpers.getObjectField(param.thisObject, "mContext")))
                                .run();
                    }
                });
        // Android 16: (IBinder, KeyEvent, int policyFlags) returning a long, -1 = consumed.
        // Android 17 (Nothing OS 5): (IBinder, KeyEvent) returning a boolean, true = consumed
        // (services.jar: KeyGestureController.interceptShortcutsBeforeKeyCapture returns it as
        // "handled"). Every version is hooked; homeButton answers in the method's own type.
        XposedBridge.hookAllMethods(XposedHelpers.findClass(CLS_POLICY, cl),
                "interceptKeyBeforeDispatching", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        KeyEvent event = (KeyEvent) param.args[1];
                        if (!active || event.getKeyCode() != KeyEvent.KEYCODE_BUTTON_MODE) {
                            return;
                        }
                        try {
                            homeButton(param, event);
                        } catch (Throwable t) {
                            Hooks.log("controller home button failed: " + t);
                        }
                    }
                });
        // The PS / Xbox / Guide button in an app that does not use it: home. Android asks the
        // window policy about a key the app in front left unhandled (interceptUnhandledKey);
        // a game that uses the button keeps it, and Nothing's home screen uses it itself (the
        // app drawer, launcher Gamepad). With the gaming launcher on, it is taken earlier.
        XposedBridge.hookAllMethods(XposedHelpers.findClass(CLS_POLICY, cl),
                "interceptUnhandledKey", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            KeyEvent event = null;
                            for (Object arg : param.args) {
                                if (arg instanceof KeyEvent) {
                                    event = (KeyEvent) arg;
                                }
                            }
                            if (event == null || event.getKeyCode() != KeyEvent.KEYCODE_BUTTON_MODE
                                    || !store.get().controllerNav) {
                                return;
                            }
                            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                                Object policy = param.thisObject;
                                int display = (Integer) XposedHelpers.callMethod(event,
                                        "getDisplayId");
                                ((Handler) XposedHelpers.getObjectField(policy, "mHandler")).post(
                                        Hooks.guard("controller home button in an app", () ->
                                                XposedHelpers.callMethod(policy,
                                                        "launchHomeFromHotKey", display)));
                            }
                            param.setResult(true);
                        } catch (Throwable t) {
                            Hooks.log("controller home button in an app failed: " + t);
                        }
                    }
                });
        Hooks.log("controller home: ready");
    }

    private static void homeButton(XC_MethodHook.MethodHookParam param, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            Object policy = param.thisObject;
            int display = (Integer) XposedHelpers.callMethod(event, "getDisplayId");
            Handler policyHandler = (Handler) XposedHelpers.getObjectField(policy, "mHandler");
            policyHandler.post(Hooks.guard("controller home button", () ->
                    XposedHelpers.callMethod(policy, "launchHomeFromHotKey", display)));
        }
        // Consumed: the app in front does not get it.
        boolean answersBoolean = ((java.lang.reflect.Method) param.method).getReturnType()
                == boolean.class;
        param.setResult(answersBoolean ? (Object) Boolean.TRUE : (Object) (-1L));
    }

    private static void start(Context system) {
        context = system;
        HandlerThread thread = new HandlerThread("nlplus-controller-home");
        thread.start();
        handler = new Handler(thread.getLooper());
        InputManager input = context.getSystemService(InputManager.class);
        input.registerInputDeviceListener(new InputManager.InputDeviceListener() {
            @Override
            public void onInputDeviceAdded(int deviceId) {
                settle();
            }

            @Override
            public void onInputDeviceRemoved(int deviceId) {
                settle();
            }

            @Override
            public void onInputDeviceChanged(int deviceId) {
                settle();
            }
        }, handler);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                Hooks.guard("controller home settings", () -> {
                    if (Config.ACTION_RELOAD.equals(intent.getAction())) {
                        store.afterSave(intent, ControllerHome::settle);
                    } else {
                        settle(); // Booted, or another user: the devices and home again.
                    }
                }).run();
            }
        };
        IntentFilter settings = new IntentFilter(Config.ACTION_RELOAD);
        context.registerReceiver(receiver, settings, null, handler, Context.RECEIVER_EXPORTED);
        IntentFilter boot = new IntentFilter(Intent.ACTION_BOOT_COMPLETED);
        boot.addAction("android.intent.action.USER_SWITCHED"); // Intent.ACTION_USER_SWITCHED
        context.registerReceiver(receiver, boot, null, handler, Context.RECEIVER_NOT_EXPORTED);
    }

    /** The one apply waiting to run, so settle() takes back only its own task. */
    private static final Runnable APPLY = Hooks.guard("controller home", ControllerHome::apply);

    /**
     * Applies once the devices have settled. Mistake recorded (found in the 0.70.14 audit):
     * this cleared every message on the handler, and Android also delivers this class's
     * receivers through it (registerReceiver with this handler), so an input-device event
     * could drop a queued BOOT_COMPLETED, USER_SWITCHED or settings delivery; an ordered
     * broadcast whose delivery is dropped is never finished and waits for its timeout.
     */
    private static void settle() {
        handler.removeCallbacks(APPLY);
        handler.postDelayed(APPLY, SETTLE_MS);
    }

    /** The home app for whether a controller is connected now (on the handler's thread). */
    private static void apply() {
        Config config = store.get();
        String gaming = config.controllerHomeApp;
        // Never in "Launcher only": there it only puts the user's own home back.
        boolean want = !config.launcherOnly && config.controllerHome && !gaming.isEmpty()
                && controllerConnected();
        active = want;
        int userId = (Integer) XposedHelpers.callStaticMethod(ActivityManager.class,
                "getCurrentUser");
        UserHandle user = UserHandle.getUserHandleForUid(userId * PER_USER_RANGE);
        Object roles = context.getSystemService("role");
        String home = holder(roles, user);
        // "previous home/gaming launcher set in its place", while the gaming launcher is home.
        String key = savedKey(userId);
        String saved = Settings.Global.getString(context.getContentResolver(), key);
        String previous = saved == null ? null : saved.substring(0, saved.indexOf('/'));
        String placed = saved == null ? null : saved.substring(saved.indexOf('/') + 1);
        if (home == null) {
            return;
        }
        if (want) {
            if (home.equals(gaming)) {
                return; // Already home (set here, or the user's own choice).
            }
            if (previous == null || !home.equals(placed)) {
                previous = home; // Not one set here: the user's home, to put back.
            }
            save(key, previous + "/" + gaming);
            setHome(roles, gaming, user, home);
        } else if (saved != null) {
            save(key, null);
            if (home.equals(placed) && !home.equals(previous)) {
                setHome(roles, previous, user, home);
            }
        }
    }

    private static void save(String key, String value) {
        Settings.Global.putString(context.getContentResolver(), key, value);
    }

    /**
     * The record's key for one user: the home role is held per user, and one record for all
     * of them let a switch to another user overwrite the first user's home to put back (found
     * in the 0.70.16 audit). User 0 keeps the original key, so a record saved before stays
     * found.
     */
    private static String savedKey(int userId) {
        return userId == 0 ? SAVED : SAVED + "_u" + userId;
    }

    @SuppressWarnings("unchecked")
    private static String holder(Object roles, UserHandle user) {
        List<String> holders = (List<String>) XposedHelpers.callMethod(roles,
                "getRoleHoldersAsUser", ROLE_HOME, user);
        return holders == null || holders.isEmpty() ? null : holders.get(0);
    }

    private static void setHome(Object roles, String pkg, UserHandle user, String was) {
        Consumer<Boolean> done = ok -> Hooks.log("controller home: " + was + " -> " + pkg
                + (Boolean.TRUE.equals(ok) ? "" : " refused"));
        XposedHelpers.callMethod(roles, "addRoleHolderAsUser", ROLE_HOME, pkg, 0, user,
                context.getMainExecutor(), done);
    }

    private static boolean controllerConnected() {
        return Controllers.connected(context);
    }
}
