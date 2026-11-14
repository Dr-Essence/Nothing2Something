package com.dressence.nothinglauncherplus.ui;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import com.dressence.nothinglauncherplus.Config;
import com.dressence.nothinglauncherplus.CoverProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Single-file backup of all folders, options and cover images, so the setup survives a
 * reinstall (for example when switching to a differently signed build).
 */
final class Backup {

    private static final int FORMAT = 1;

    private Backup() {
    }

    static void export(Context context, Config config, Uri target)
            throws IOException, JSONException {
        JSONObject covers = new JSONObject();
        for (Config.Folder folder : config.folders) {
            File file = CoverProvider.coverFile(context, folder.id);
            if (folder.coverImageVersion != 0 && file.isFile()) {
                covers.put(folder.id, Base64.encodeToString(
                        Files.readAllBytes(file.toPath()), Base64.NO_WRAP));
            }
        }
        JSONObject root = new JSONObject()
                .put("format", FORMAT)
                .put("config", config.toJson())
                .put("covers", covers);
        try (OutputStream out = context.getContentResolver().openOutputStream(target, "wt")) {
            if (out == null) {
                throw new IOException("cannot write " + target);
            }
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * The largest backup read: far above a real one (the settings and a few cover images), so
     * a wrong file picked (a video) is refused instead of filling the memory and crashing the
     * app (0.70.14 audit).
     */
    private static final int MAX_BACKUP_BYTES = 64 * 1024 * 1024;

    /** Restores cover files and returns the imported configuration (not yet saved). */
    static Config restore(Context context, Uri source) throws IOException, JSONException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            if (in == null) {
                throw new IOException("cannot read " + source);
            }
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) > 0; ) {
                if (bytes.size() + n > MAX_BACKUP_BYTES) {
                    throw new IOException("not a backup (over 64 MB)");
                }
                bytes.write(buffer, 0, n);
            }
        }
        JSONObject root = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
        Config config = Config.fromJson(root.getJSONObject("config"));
        JSONObject covers = root.optJSONObject("covers");
        // Every cover decoded first, and only then written: a bad one found halfway used to
        // leave the current folders showing some of the backup's images while the import
        // reported it failed (0.70.14 audit).
        java.util.Map<Config.Folder, byte[]> images = new java.util.LinkedHashMap<>();
        for (Config.Folder folder : config.folders) {
            // An id is a file name under covers/ (CoverProvider): one from a hand-edited or
            // foreign backup must not reach outside it (2026-11 audit).
            if (!CoverProvider.isFolderId(folder.id)) {
                throw new IOException("not a backup of this app (folder id " + folder.id + ")");
            }
            String data = covers != null ? covers.optString(folder.id, "") : "";
            if (data.isEmpty()) {
                folder.coverImageVersion = 0;
                continue;
            }
            try {
                images.put(folder, Base64.decode(data, Base64.NO_WRAP));
            } catch (IllegalArgumentException e) {
                throw new IOException("the cover of " + folder.name + " is damaged");
            }
        }
        for (java.util.Map.Entry<Config.Folder, byte[]> e : images.entrySet()) {
            Config.Folder folder = e.getKey();
            File file = CoverProvider.coverFile(context, folder.id);
            File dir = file.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("cannot create " + dir);
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(e.getValue());
            }
            // New version so the launcher does not reuse a cached bitmap.
            folder.coverImageVersion = System.currentTimeMillis();
        }
        return config;
    }
}
