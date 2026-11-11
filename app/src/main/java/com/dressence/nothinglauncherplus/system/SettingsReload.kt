package com.dressence.nothinglauncherplus.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import com.dressence.nothinglauncherplus.Config
import com.dressence.nothinglauncherplus.xposed.ConfigStore
import com.dressence.nothinglauncherplus.xposed.Hooks
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

/**
 * Keeps system_server's copy of the settings current: every save in the settings app sends
 * [Config.ACTION_RELOAD] to "android", and this reads the settings again on a thread of its
 * own, so the hooks that run inside the window manager's lock (RotationSensor) only ever read
 * the copy, never the file.
 *
 * Why a class of its own (0.70.17): from 0.70.15 RotationSensor relied on ControllerHome's
 * receiver for this, but ControllerHome is left out in "Launcher only" (and could fail to
 * install), and then a changed rotation setting did not reach the sensor until something else
 * reloaded (found by the audit's fourth pass). This is installed whatever the mode.
 */
internal object SettingsReload {

    private const val CLS_POLICY = "com.android.server.policy.PhoneWindowManager"

    @JvmStatic
    fun install(cl: ClassLoader, store: ConfigStore) {
        // At systemReady: the first moment the window manager's context can register receivers.
        XposedHelpers.findAndHookMethod(CLS_POLICY, cl, "systemReady", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                Hooks.guard("settings reload start") {
                    val context = XposedHelpers.getObjectField(param.thisObject, "mContext")
                        as Context
                    listen(context, store)
                }.run()
            }
        })
    }

    private fun listen(context: Context, store: ConfigStore) {
        // Off system_server's main thread: a reload reads and parses the settings file.
        val thread = HandlerThread("nlplus-settings").apply { start() }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                Hooks.guard("settings reload") { store.reloadIfChanged() }.run()
            }
        }
        context.registerReceiver(receiver, IntentFilter(Config.ACTION_RELOAD), null,
            Handler(thread.looper), Context.RECEIVER_EXPORTED)
        Hooks.log("settings reload: listening")
    }
}
