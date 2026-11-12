package com.dressence.nothinglauncherplus.launcher

import android.content.Context
import android.graphics.Bitmap
import com.dressence.nothinglauncherplus.xposed.Symbols
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

/**
 * Nothing Launcher's obfuscated methods the module needs, each with its name in 4.0.20 and the
 * DexKit query that finds it again when an update renames it (see Symbols). Each query was run
 * against the phone's Nothing Launcher 4.0.20 APK with DexKit 2.3.0 and finds exactly the
 * 4.0.20 name, alone (0.70.26).
 */
internal object LauncherSymbols {

    private const val BASE_ICON_FACTORY = "com.android.launcher3.icons.BaseIconFactory"
    private const val ICON_PROVIDER = "com.android.launcher3.icons.IconProvider"
    private const val ALL_APPS_STATE = "com.android.launcher3.uioverrides.states.AllAppsState"

    /**
     * An icon pack's icon for an app (4.0.20: d7.h.i): the only method taking a package name,
     * Launcher3's BaseIconFactory and IconProvider and giving a Bitmap. Those two are AOSP
     * Launcher3 names, which R8 keeps.
     */
    @JvmStatic
    fun iconForPack(cl: ClassLoader): Method = Symbols.method("launcher.iconForPack", "d7.h", "i",
        arrayOf(String::class.java, cl.loadClass(BASE_ICON_FACTORY), cl.loadClass(ICON_PROVIDER)),
        Bitmap::class.java) { bridge ->
        bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
            .paramTypes(String::class.java.name, BASE_ICON_FACTORY, ICON_PROVIDER)
            .returnType(Bitmap::class.java.name))).singleOrNull()
    }

    /**
     * Fills the map of installed icon packs (4.0.20: h7.d.a): the only method taking a Context
     * and a ConcurrentHashMap and giving nothing back.
     */
    @JvmStatic
    fun readPacks(cl: ClassLoader): Method = Symbols.method("launcher.readPacks", "h7.d", "a",
        arrayOf(Context::class.java, ConcurrentHashMap::class.java), Void.TYPE) { bridge ->
        bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
            .paramTypes(Context::class.java.name, ConcurrentHashMap::class.java.name)
            .returnType("void"))).singleOrNull()
    }

    /**
     * The drawer's scrim colour (4.0.20: ThemeColors p7.e.e): the (Context) -> int method that
     * AOSP's AllAppsState.getWorkspaceScrimColor calls; its caller keeps its AOSP name.
     */
    @JvmStatic
    fun scrimColour(cl: ClassLoader): Method = Symbols.method("launcher.scrimColour", "p7.e", "e",
        arrayOf(Context::class.java), Int::class.javaPrimitiveType!!) { bridge ->
        bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
            .declaredClass(ALL_APPS_STATE).name("getWorkspaceScrimColor")))
            .flatMap { it.invokes }
            .filter { it.paramTypeNames == listOf(Context::class.java.name) && it.returnTypeName == "int" }
            .distinctBy { it.descriptor }
            .singleOrNull()
    }
}
