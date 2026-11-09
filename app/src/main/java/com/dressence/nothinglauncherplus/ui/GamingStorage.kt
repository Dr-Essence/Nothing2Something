package com.dressence.nothinglauncherplus.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * Where Nothing Gaming Space keeps the pictures and videos it saves: a folder of the phone's
 * own storage the user can open in any file manager,
 *
 *     Nothing Gaming Space/
 *         SteamGridDB/   artwork, one file per app (<package>.png, .jpg, .webp or .gif)
 *         Videos/        trailers, one clip per app (<package>.mp4)
 *
 * or, without the permission for it, this app's private files as before.
 *
 * Why a permission: since Android 11 (scoped storage) an app may write by path only into its own
 * folders and the standard ones (Pictures, Movies, Download...); a folder of its own at the top
 * of the storage needs "All files access" (`MANAGE_EXTERNAL_STORAGE`, which the user grants in
 * Settings, checked with [Environment.isExternalStorageManager]). Pictures/ and Movies/ through
 * MediaStore need none, but would split the files in two folders and mix them into the gallery,
 * which is not what was asked.
 *
 * Files are named by the app's package, so a picture or video the user drops in under that
 * name is used as it is. A `.nomedia` file keeps the folder out of the gallery.
 */
internal object GamingStorage {

    const val ROOT = "Nothing Gaming Space"
    const val ART = "SteamGridDB"
    const val VIDEOS = "Videos"

    /** True when the shared folder can be used (All files access granted). */
    fun shared(): Boolean = try {
        Environment.isExternalStorageManager()
    } catch (t: Throwable) {
        false
    }

    /** The shared folder's path, for the settings. */
    fun rootPath(): String = File(Environment.getExternalStorageDirectory(), ROOT).path

    /**
     * The folder for [sub] ([ART] or [VIDEOS]): the shared one when it can be used, else this
     * app's private [privateName] folder. Files already saved privately are moved over the
     * first time the shared folder is used, so nothing is downloaded twice.
     */
    fun folder(context: Context, sub: String, privateName: String): File {
        val own = File(context.filesDir, privateName).also { it.mkdirs() }
        if (!shared()) return own
        return try {
            val root = File(Environment.getExternalStorageDirectory(), ROOT)
            val dir = File(root, sub)
            if (!dir.isDirectory && !dir.mkdirs()) return own
            File(root, ".nomedia").let { if (!it.exists()) it.createNewFile() }
            moveAll(own, dir)
            dir
        } catch (t: Throwable) {
            own // storage not mounted, or refused: keep working from the private files
        }
    }

    /**
     * Moves every file of [from] into [to]. `renameTo` cannot cross from /data to the shared
     * storage (two file systems), so each file is copied, then deleted; a half copy is removed.
     */
    private fun moveAll(from: File, to: File) {
        for (file in from.listFiles() ?: return) {
            if (!file.isFile || file.name.endsWith(".part")) continue
            val target = File(to, file.name)
            try {
                if (!target.exists()) file.copyTo(target)
                file.delete()
            } catch (t: Throwable) {
                target.delete()
            }
        }
    }

    /** Opens Settings at this app's "All files access" switch. */
    fun askAccess(context: Context) {
        val page = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:" + context.packageName))
        try {
            context.startActivity(page)
        } catch (t: Throwable) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
}
