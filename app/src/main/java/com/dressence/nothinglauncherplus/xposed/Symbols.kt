package com.dressence.nothinglauncherplus.xposed

import java.io.File
import java.lang.reflect.Method
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData

/**
 * Finds a hooked app's obfuscated methods again after an update.
 *
 * Nothing's apps are shrunk by R8, so many of the methods the module needs have short names
 * (Nothing Launcher 4.0.20: p7.e.e, h7.d.a, d7.h.i) that the next build may give to other
 * methods. Each one is looked for in three steps, the cheapest first:
 *
 * 1. **The known name**, with its exact parameter and return types: on the build the module was
 *    made against, this is all that ever runs (no scan, nothing loaded).
 * 2. **The cache**: what DexKit found for this exact build of the app before (a file in the
 *    app's own cache folder, keyed by its APK's size and time).
 * 3. **DexKit** (https://github.com/LuckyPray/DexKit), which reads the app's dex files and
 *    finds the method by what does not change between builds: AOSP class names in its
 *    signature, the AOSP method that calls it. It is a native library, so it runs only when the
 *    known name no longer fits, once per build of the app, and its answer is cached.
 *
 * If all three fail, the feature that asked is skipped (Hooks.install) and Hook health says
 * why. Every query was run against the phone's Nothing Launcher 4.0.20 before it was shipped,
 * and finds exactly the known name there (0.70.26).
 */
object Symbols {

    private var apk: String? = null
    private var cacheFile: File? = null
    private var classLoader: ClassLoader? = null
    private var bridge: DexKitBridge? = null
    private var cache: JSONObject? = null
    private var buildKey = ""

    /** Whether DexKit's native library loads in this process (null: not tried yet). */
    @Volatile var dexKitLoads: Boolean? = null
        private set

    /** For the app now starting (handleLoadPackage): its APK, class loader and cache folder. */
    @JvmStatic
    fun begin(apkPath: String?, dataDir: String?, loader: ClassLoader) {
        apk = apkPath
        classLoader = loader
        cacheFile = dataDir?.let { File(it, "cache/nlplus_symbols.json") }
        buildKey = apkPath?.let { File(it).let { f -> "${f.length()}-${f.lastModified()}" } } ?: ""
        Hooks.install("dexkit library") { loadDexKit() }
    }

    /** Releases DexKit's memory once the app's hooks are in (it is needed only while they go in). */
    @JvmStatic
    fun end() {
        try {
            bridge?.close()
        } catch (t: Throwable) {
            Hooks.log("dexkit close: $t")
        }
        bridge = null
    }

    /**
     * The method [key] names: [known] if it fits (exactly [params] and [returns]), else what
     * this build's cache or DexKit's [query] finds. Throws if none is found, so the feature that
     * needs it is skipped cleanly.
     */
    @JvmStatic
    fun method(key: String, known: String, name: String, params: Array<Class<*>>,
            returns: Class<*>, query: (DexKitBridge) -> MethodData?): Method {
        val loader = classLoader ?: throw IllegalStateException("symbols: no app")
        // 1. The known name.
        try {
            val m = Class.forName(known, false, loader).getDeclaredMethod(name, *params)
            if (m.returnType == returns) return m.also { it.isAccessible = true }
        } catch (e: ReflectiveOperationException) {
            // Not this build's name: looked for below.
        } catch (e: LinkageError) {
            // Same.
        }
        // 2. This build's cache.
        cached(key)?.let { descriptor ->
            resolve(descriptor, loader, params, returns)?.let { return it }
        }
        // 3. DexKit.
        val dexKit = bridge() ?: throw NoSuchMethodException(
            "$key: $known.$name no longer fits and DexKit is not available")
        val found = query(dexKit) ?: throw NoSuchMethodException(
            "$key: $known.$name no longer fits and DexKit found nothing")
        val m = found.getMethodInstance(loader)
        if (m !is Method || !m.parameterTypes.contentEquals(params) || m.returnType != returns) {
            throw NoSuchMethodException("$key: DexKit found ${found.descriptor}, of another shape")
        }
        m.isAccessible = true
        remember(key, found.descriptor)
        Hooks.log("symbols: $key is ${found.descriptor} in this build (was $known.$name)")
        return m
    }

    /** "Lp7/e;->e(Landroid/content/Context;)I" back to the method, if it still has this shape. */
    private fun resolve(descriptor: String, loader: ClassLoader, params: Array<Class<*>>,
            returns: Class<*>): Method? = try {
        val owner = descriptor.substringBefore(";->").removePrefix("L").replace('/', '.')
        val name = descriptor.substringAfter(";->").substringBefore('(')
        Class.forName(owner, false, loader).getDeclaredMethod(name, *params)
            .takeIf { it.returnType == returns }?.also { it.isAccessible = true }
    } catch (t: Throwable) {
        null
    }

    private fun loadDexKit() {
        // Loaded at every start (a library mapped, nothing scanned) so Hook health shows
        // whether DexKit can run in this app at all, before an update ever needs it.
        dexKitLoads = try {
            System.loadLibrary("dexkit")
            true
        } catch (t: Throwable) {
            Hooks.skipped("dexkit", "its native library does not load in this app: $t")
            false
        }
    }

    private fun bridge(): DexKitBridge? {
        bridge?.let { return it }
        if (dexKitLoads != true) return null
        val path = apk ?: return null
        return try {
            DexKitBridge.create(path).also { bridge = it }
        } catch (t: Throwable) {
            Hooks.skipped("dexkit", "could not read this app: $t")
            null
        }
    }

    private fun cacheJson(): JSONObject {
        cache?.let { return it }
        val read = try {
            cacheFile?.takeIf { it.exists() }?.readText()?.let { JSONObject(it) }
        } catch (t: Throwable) {
            null
        }
        // Only what was found for this very build; another build starts empty.
        val fresh = if (read != null && read.optString("build") == buildKey) read
        else JSONObject().put("build", buildKey)
        cache = fresh
        return fresh
    }

    private fun cached(key: String): String? = cacheJson().optString(key, "").ifEmpty { null }

    private fun remember(key: String, descriptor: String) {
        try {
            val json = cacheJson().put(key, descriptor)
            cacheFile?.let {
                it.parentFile?.mkdirs()
                it.writeText(json.toString())
            }
        } catch (t: Throwable) {
            Hooks.log("symbols: cache not written: $t")
        }
    }
}
