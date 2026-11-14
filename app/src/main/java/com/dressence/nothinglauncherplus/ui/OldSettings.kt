package com.dressence.nothinglauncherplus.ui

import android.content.Context
import android.content.SharedPreferences
import android.util.Xml
import com.dressence.nothinglauncherplus.Config
import com.dressence.nothinglauncherplus.Root
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * Moves the settings from the old Xposed API's shared file into the app's own file, once, the
 * first time the modern API's service connects (ModuleService), so an update to 0.70.35 keeps
 * every setting and folder.
 *
 * Where the old file is: with "xposedsharedprefs", LSPosed and Vector redirect the module app's
 * settings folder to a folder of their own, /data/misc/<random>/prefs/<package> (Vector's
 * LegacyDelegateImpl.hookNewXSP; the module's debug report has read it there since DebuG23). On
 * the modern API that redirect is no longer made (Vector makes it only for modules it loads the
 * old way), so the app sees its own empty folder and the old file has to be fetched, with root.
 *
 * The old files are deleted afterwards ([removeOldFiles]): a world-readable settings file is
 * what LSPosed 2.2 warns about, and the hooks stop reading it once the framework has the
 * settings (xposed/ConfigStore).
 */
internal object OldSettings {

    private const val STATE = "nlplus_settings_move"
    private const val MOVED = "moved"
    private const val REMOVED = "removed"
    private const val FOLDERS = "/data/misc/*/prefs/${Config.MODULE_PKG}"

    /**
     * Copies the old settings into [local] unless that was done before. Returns why it could
     * not (shown on the settings page), or null. Call off the main thread: it asks for root.
     */
    fun moveInto(context: Context, local: SharedPreferences): String? {
        val state = context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
        if (state.getBoolean(MOVED, false)) {
            return null
        }
        // A setup already made again with this version (its own folders) is kept: the move is
        // over. Settings saved meanwhile without folders (the defaults and a switch or two,
        // while root was refused) do not count: the old file, the user's real setup, still
        // replaces them once root is allowed (2026-11 audit: 0.70.54 let the old file replace
        // folders made since; 0.70.55 skipped the old file at the first save of any kind).
        val ownFolders = local.getString(Config.KEY_FOLDERS, "[]")
        if (local.contains(Config.KEY_SAVED) && !ownFolders.isNullOrBlank() &&
                ownFolders.trim() != "[]") {
            state.edit().putBoolean(MOVED, true).commit()
            return null
        }
        // Read from init's mount namespace, as the debug report does: root's own shell may not
        // see every folder. The newest file, if a reinstall of the framework left two.
        val xml = Root.read("nsenter -t 1 -m -- sh -c 'f=$(ls -t $FOLDERS/${Config.PREFS_NAME}.xml" +
            " 2>/dev/null | head -n 1); [ -n \"\$f\" ] && cat \"\$f\"; true'")
            ?: return "The settings from before this version could not be moved: root was" +
                " refused. Allow root for Nothing2Something and open it again."
        if (xml.isNotBlank()) {
            val values = try {
                parse(xml)
            } catch (e: Exception) {
                return "The settings from before this version could not be read ($e)."
            }
            val editor = local.edit().clear()
            values.forEach { (key, value) -> putValue(editor, key, value) }
            editor.commit()
        }
        state.edit().putBoolean(MOVED, true).commit()
        return null
    }

    /** Deletes the old files once their settings were moved and copied to the framework. */
    fun removeOldFiles(context: Context) {
        val state = context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
        if (!state.getBoolean(MOVED, false) || state.getBoolean(REMOVED, false)) {
            return
        }
        if (Root.run("nsenter -t 1 -m -- sh -c 'rm -f $FOLDERS/*.xml'") == null) {
            state.edit().putBoolean(REMOVED, true).commit()
        }
    }

    /**
     * Android's settings file format: `<map>` holding `<string name>text</string>`, `<int|long|
     * float|boolean name value/>` and `<set name><string>...</string></set>`.
     */
    private fun parse(xml: String): Map<String, Any> {
        val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
        val values = LinkedHashMap<String, Any>()
        var setName: String? = null
        var set: MutableSet<String>? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.name == "set") {
                setName?.let { name -> set?.let { values[name] = it } }
                setName = null
                set = null
                continue
            }
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            val name = parser.getAttributeValue(null, "name")
            val value = parser.getAttributeValue(null, "value")
            when (parser.name) {
                "string" -> if (set != null) set.add(parser.nextText())
                    else if (name != null) values[name] = parser.nextText()
                "int" -> if (name != null) values[name] = value.toInt()
                "long" -> if (name != null) values[name] = value.toLong()
                "float" -> if (name != null) values[name] = value.toFloat()
                "boolean" -> if (name != null) values[name] = value.toBoolean()
                "set" -> if (name != null) {
                    setName = name
                    set = LinkedHashSet()
                }
            }
        }
        return values
    }
}
