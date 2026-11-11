package com.dressence.nothinglauncherplus.xposed

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.dressence.nothinglauncherplus.Broadcasts
import com.dressence.nothinglauncherplus.Config
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

/**
 * The hook health report: each app the module hooks tells the module app which of its
 * features installed and which did not (with why), so that after an update of Nothing OS or
 * of an app the Diagnostics page shows exactly what stopped working, without reading the
 * LSPosed log. (0.70.19: the first piece of making the module ready for updates; until then a
 * renamed method only left a line in that log.)
 *
 * Sent once the app has a context (after its Application.onCreate; in system_server at
 * systemReady), and once more a little later, for features installed after the start.
 */
object HookReport {

    const val ACTION = "${Config.MODULE_PKG}.HOOK_REPORT"
    const val EXTRA_PACKAGE = "package"
    const val EXTRA_PROCESS = "process"
    const val EXTRA_VERSION = "version"
    const val EXTRA_NAMES = "names"
    const val EXTRA_REASONS = "reasons"

    /** Late installs (inside other hooks) are in the second report. */
    private const val AGAIN_MS = 20_000L

    @Volatile private var hooked = false

    /** In an app the module hooks: reports once its Application has started. */
    @JvmStatic
    fun install() {
        if (hooked) return
        hooked = true
        // Instrumentation.callApplicationOnCreate: the one place every app's start passes,
        // whether or not its Application calls super.onCreate.
        XposedHelpers.findAndHookMethod(Instrumentation::class.java, "callApplicationOnCreate",
            Application::class.java, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val app = param.args[0] as? Application ?: return
                    schedule(app)
                }
            })
    }

    /** From system_server, which has no Application: at systemReady, with its context. */
    @JvmStatic
    fun schedule(context: Context) {
        val main = Handler(Looper.getMainLooper())
        main.post(Hooks.guard("hook report") { send(context) })
        main.postDelayed(Hooks.guard("hook report") { send(context) }, AGAIN_MS)
    }

    private fun send(context: Context) {
        val results = Hooks.results()
        if (results.isEmpty()) return
        val pkg = context.packageName
        val version = try {
            context.packageManager.getPackageInfo(pkg, 0).versionName ?: ""
        } catch (t: Throwable) {
            ""
        }
        val intent = Intent(ACTION).setPackage(Config.MODULE_PKG)
            // Also when the module app was never opened since it was installed (or was force
            // stopped): Android otherwise holds broadcasts back from a stopped app.
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            .putExtra(EXTRA_PACKAGE, pkg)
            .putExtra(EXTRA_PROCESS, Application.getProcessName() ?: pkg)
            .putExtra(EXTRA_VERSION, version)
            .putExtra(EXTRA_NAMES, results.map { it[0] }.toTypedArray())
            .putExtra(EXTRA_REASONS, results.map { it[1] }.toTypedArray())
        Broadcasts.send(context, intent)
    }
}
