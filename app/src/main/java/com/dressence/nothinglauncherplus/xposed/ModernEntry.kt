package com.dressence.nothinglauncherplus.xposed

import com.dressence.nothinglauncherplus.Config
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * The module's entry on a framework with the modern Xposed API (libxposed API 101: LSPosed 2,
 * Vector), named in META-INF/xposed/java_init.list (0.70.35).
 *
 * Why it targets API 101 and not 102 (module.prop): a module targeting 102 or more may not call
 * the old API (Vector's VectorModuleManager: blockLegacyApi = targetApiVersion >= 102; LSPosed
 * 2.0.3's release notes say the same), and every hook of the module is written with it. At 101
 * the hooks stay as they are and only the way in and the settings change.
 *
 * The settings come from the framework's own copy ([getRemotePreferences], see ConfigStore),
 * which the settings app writes through the framework's service (ui/ModuleService): no file is
 * shared, which is what LSPosed 2.2 warned about.
 */
class ModernEntry : XposedModule() {

    /**
     * This process's settings, read on first use. If the framework cannot give its copy (an
     * embedded framework has none: UnsupportedOperationException), the old shared file, so the
     * hooks still install with what there is.
     */
    private val store by lazy {
        try {
            ConfigStore.remote(getRemotePreferences(Config.PREFS_NAME))
        } catch (t: Throwable) {
            Hooks.log("settings: the framework's copy is not available ($t), the old file instead")
            ConfigStore.file()
        }
    }

    /** system_server: the old API's "android" package. */
    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        HookLoader.load(HookLoader.Start("android", param.classLoader, true, null)) { store }
    }

    /**
     * An app's code is ready, before its Application is made: the moment the old API's
     * handleLoadPackage stood for.
     */
    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName == "android") {
            return // system_server: installed once, in onSystemServerStarting.
        }
        HookLoader.load(HookLoader.Start(param.packageName, param.classLoader,
            param.isFirstPackage, param.applicationInfo)) { store }
    }
}
