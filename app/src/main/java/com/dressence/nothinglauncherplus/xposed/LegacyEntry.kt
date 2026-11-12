package com.dressence.nothinglauncherplus.xposed

import com.dressence.nothinglauncherplus.Config
import com.dressence.nothinglauncherplus.ui.ModuleService
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * The module's entry on a framework with only the old Xposed API (LSPosed 1.x, EdXposed...),
 * named in assets/xposed_init. A framework with the modern API loads [ModernEntry] instead and
 * never this one (Vector's FileSystem.loadModule: a module.prop target of 101 or more wins).
 *
 * The settings come from the settings app's shared file ([ConfigStore.file]).
 */
class LegacyEntry : IXposedHookLoadPackage {

    /** This process's settings, read on first use. */
    private val store by lazy { ConfigStore.file() }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == Config.MODULE_PKG) {
            // The settings app learns that this API loaded the module, so it shares its
            // settings the old way (ui/SettingsStore).
            XposedHelpers.findAndHookMethod(ModuleService::class.java.name, lpparam.classLoader,
                "isLegacyActive", XC_MethodReplacement.returnConstant(true))
            return
        }
        HookLoader.load(HookLoader.Start(lpparam.packageName, lpparam.classLoader,
            lpparam.isFirstApplication, lpparam.appInfo)) { store }
    }
}
