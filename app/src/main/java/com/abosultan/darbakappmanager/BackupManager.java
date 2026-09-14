package com.abosultan.darbakappmanager;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public final class BackupManager {
    private BackupManager() {}

    public static File backup(Context context, AppEntry app) throws Exception {
        if (app.sourceDir == null) throw new IllegalArgumentException("Missing APK source");
        File source = new File(app.sourceDir);
        if (!source.isFile()) throw new IllegalArgumentException("APK source not found");
        File base = new File(Environment.getExternalStorageDirectory(), "DarbakAppManager/Backups");
        if (!base.mkdirs() && !base.isDirectory()) throw new IllegalStateException("Cannot create backup folder");
        String safeLabel = sanitize(app.label);
        String safeVersion = sanitize(app.versionName);
        File dest = new File(base, safeLabel + "__" + app.packageName + "__v" + safeVersion + ".apk");
        copy(source, dest);
        OperationLog.add(context, "BACKUP " + app.packageName + " -> " + dest.getName());
        return dest;
    }

    public static int countBackups() {
        File base = new File(Environment.getExternalStorageDirectory(), "DarbakAppManager/Backups");
        File[] files = base.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File f : files) if (f.isFile() && f.getName().toLowerCase().endsWith(".apk")) count++;
        return count;
    }

    public static long backupBytes() {
        File base = new File(Environment.getExternalStorageDirectory(), "DarbakAppManager/Backups");
        File[] files = base.listFiles();
        if (files == null) return 0L;
        long total = 0L;
        for (File f : files) if (f.isFile() && f.getName().toLowerCase().endsWith(".apk")) total += f.length();
        return total;
    }

    private static void copy(File source, File dest) throws Exception {
        InputStream in = new FileInputStream(source);
        OutputStream out = new FileOutputStream(dest);
        try {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            out.flush();
        } finally {
            try { in.close(); } catch (Exception ignored) {}
            try { out.close(); } catch (Exception ignored) {}
        }
    }

    private static String sanitize(String value) {
        if (value == null || value.trim().isEmpty()) return "app";
        String out = value.replaceAll("[^A-Za-z0-9._\\-\\u0600-\\u06FF]+", "_");
        if (out.length() > 48) out = out.substring(0, 48);
        return out;
    }
}
