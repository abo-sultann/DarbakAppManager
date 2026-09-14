package com.abosultan.darbakappmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class SafeCleaner {
    public static final int CONFIRMED = 2;
    public static final int CANDIDATE = 1;
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$");
    private static final String META_FILE = ".darbak_original_path";
    private final Context context;
    private final AppRepository repository;

    public SafeCleaner(Context context) {
        this.context = context.getApplicationContext();
        this.repository = new AppRepository(this.context);
    }

    public static final class LeftoverItem {
        public final File path;
        public final String packageName;
        public final String sourceLabel;
        public final long bytes;
        public final int confidence;
        public boolean selected;

        LeftoverItem(File path, String packageName, String sourceLabel, long bytes, int confidence) {
            this.path = path;
            this.packageName = packageName;
            this.sourceLabel = sourceLabel;
            this.bytes = bytes;
            this.confidence = confidence;
            this.selected = confidence == CONFIRMED;
        }
    }

    public List<LeftoverItem> scan() {
        List<LeftoverItem> out = new ArrayList<>();
        Set<String> installed = repository.getAllInstalledPackageNames();
        SharedPreferences sp = context.getSharedPreferences(PackageRemovedReceiver.PREFS, Context.MODE_PRIVATE);
        Set<String> removed = new HashSet<>(sp.getStringSet(PackageRemovedReceiver.REMOVED_PACKAGES, new HashSet<String>()));

        File storage = Environment.getExternalStorageDirectory();
        scanRoot(new File(storage, "Android/data"), "Android/data", installed, removed, out);
        scanRoot(new File(storage, "Android/obb"), "Android/obb", installed, removed, out);
        scanRoot(new File(storage, "Android/media"), "Android/media", installed, removed, out);
        return out;
    }

    private void scanRoot(File root, String label, Set<String> installed, Set<String> removed, List<LeftoverItem> out) {
        if (!root.isDirectory()) return;
        File[] children = root.listFiles();
        if (children == null) return;
        String rootCanonical = canonical(root);
        for (File child : children) {
            if (!child.isDirectory()) continue;
            String pkg = child.getName();
            if (!PACKAGE_PATTERN.matcher(pkg).matches()) continue;
            if (installed.contains(pkg) || ProtectionPolicy.isProtectedPackage(pkg)) continue;
            if (!isImmediateChild(rootCanonical, child)) continue;
            int confidence = removed.contains(pkg) ? CONFIRMED : CANDIDATE;
            out.add(new LeftoverItem(child, pkg, label, safeSize(child, canonical(child)), confidence));
        }
    }

    public int quarantine(List<LeftoverItem> items) {
        File base = quarantineBase();
        File batch = new File(base, new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()));
        if (!batch.mkdirs() && !batch.isDirectory()) return 0;
        int moved = 0;
        for (LeftoverItem item : items) {
            if (!item.selected) continue;
            if (!isAllowedSource(item.path)) continue;
            File dest = new File(batch, item.sourceLabel.replace('/', '_') + "__" + item.packageName);
            if (moveSafely(item.path, dest)) {
                try {
                    FileWriter fw = new FileWriter(new File(dest, META_FILE), false);
                    fw.write(item.path.getAbsolutePath());
                    fw.close();
                } catch (Exception ignored) {}
                moved++;
                OperationLog.add(context, "QUARANTINE " + item.packageName + " from " + item.sourceLabel + " (" + item.bytes + " bytes)");
            }
        }
        return moved;
    }

    public int restoreLatestBatch() {
        File batch = latestBatch();
        if (batch == null) return 0;
        File[] entries = batch.listFiles();
        if (entries == null) return 0;
        int restored = 0;
        for (File entry : entries) {
            if (!entry.isDirectory()) continue;
            File meta = new File(entry, META_FILE);
            if (!meta.isFile()) continue;
            String original = readFirstLine(meta);
            if (original == null) continue;
            File target = new File(original);
            if (!isAllowedSource(target) || target.exists()) continue;
            meta.delete();
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (moveSafely(entry, target)) {
                restored++;
                OperationLog.add(context, "RESTORE " + target.getAbsolutePath());
            } else {
                try {
                    FileWriter fw = new FileWriter(meta, false);
                    fw.write(original);
                    fw.close();
                } catch (Exception ignored) {}
            }
        }
        File[] remains = batch.listFiles();
        if (remains != null && remains.length == 0) batch.delete();
        return restored;
    }

    public boolean clearQuarantinePermanently() {
        File base = quarantineBase();
        if (!base.exists()) return true;
        String allowed = canonical(base);
        boolean ok = deleteTree(base, allowed);
        if (ok) OperationLog.add(context, "DELETE_QUARANTINE permanent cleanup");
        return ok;
    }

    public long quarantineBytes() {
        File base = quarantineBase();
        return base.exists() ? safeSize(base, canonical(base)) : 0L;
    }

    public int quarantineItemCount() {
        File base = quarantineBase();
        if (!base.isDirectory()) return 0;
        int count = 0;
        File[] batches = base.listFiles();
        if (batches == null) return 0;
        for (File batch : batches) {
            File[] entries = batch.listFiles();
            if (entries == null) continue;
            for (File entry : entries) if (entry.isDirectory()) count++;
        }
        return count;
    }

    private File quarantineBase() {
        return new File(Environment.getExternalStorageDirectory(), "DarbakAppManager/Quarantine");
    }

    private File latestBatch() {
        File base = quarantineBase();
        File[] batches = base.listFiles();
        if (batches == null || batches.length == 0) return null;
        File latest = null;
        for (File b : batches) {
            if (!b.isDirectory()) continue;
            if (latest == null || b.lastModified() > latest.lastModified()) latest = b;
        }
        return latest;
    }

    private boolean isAllowedSource(File file) {
        try {
            File storage = Environment.getExternalStorageDirectory();
            File data = new File(storage, "Android/data");
            File obb = new File(storage, "Android/obb");
            File media = new File(storage, "Android/media");
            String parent = file.getParentFile() == null ? "" : file.getParentFile().getCanonicalPath();
            String path = file.getCanonicalPath();
            String pkg = file.getName();
            if (!PACKAGE_PATTERN.matcher(pkg).matches() || ProtectionPolicy.isProtectedPackage(pkg)) return false;
            return (parent.equals(data.getCanonicalPath()) || parent.equals(obb.getCanonicalPath()) || parent.equals(media.getCanonicalPath()))
                    && !path.equals(data.getCanonicalPath()) && !path.equals(obb.getCanonicalPath()) && !path.equals(media.getCanonicalPath());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isImmediateChild(String rootCanonical, File child) {
        try {
            File parent = child.getCanonicalFile().getParentFile();
            return parent != null && parent.getCanonicalPath().equals(rootCanonical);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean moveSafely(File source, File dest) {
        if (dest.exists()) return false;
        if (source.renameTo(dest)) return true;
        String sourceRoot = canonical(source);
        if (!copyTree(source, dest, sourceRoot)) {
            deleteTree(dest, canonical(dest));
            return false;
        }
        return deleteTree(source, sourceRoot);
    }

    private static boolean copyTree(File src, File dst, String sourceRootCanonical) {
        try {
            String current = src.getCanonicalPath();
            if (!(current.equals(sourceRootCanonical) || current.startsWith(sourceRootCanonical + File.separator))) return false;
            if (src.isDirectory()) {
                if (!dst.mkdirs() && !dst.isDirectory()) return false;
                File[] children = src.listFiles();
                if (children == null) return true;
                for (File child : children) {
                    if (!copyTree(child, new File(dst, child.getName()), sourceRootCanonical)) return false;
                }
                return true;
            }
            InputStream in = new FileInputStream(src);
            OutputStream out = new FileOutputStream(dst);
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            in.close();
            out.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static long safeSize(File file, String rootCanonical) {
        try {
            String current = file.getCanonicalPath();
            if (!(current.equals(rootCanonical) || current.startsWith(rootCanonical + File.separator))) return 0L;
            if (file.isFile()) return file.length();
            long total = 0L;
            File[] children = file.listFiles();
            if (children != null) for (File child : children) total += safeSize(child, rootCanonical);
            return total;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static boolean deleteTree(File file, String allowedRootCanonical) {
        try {
            String current = file.getCanonicalPath();
            if (!(current.equals(allowedRootCanonical) || current.startsWith(allowedRootCanonical + File.separator))) return false;
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) if (!deleteTree(child, allowedRootCanonical)) return false;
                }
            }
            return !file.exists() || file.delete();
        } catch (Exception e) {
            return false;
        }
    }

    private static String readFirstLine(File f) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(f));
            String s = br.readLine();
            br.close();
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    private static String canonical(File f) {
        try { return f.getCanonicalPath(); }
        catch (Exception e) { return f.getAbsolutePath(); }
    }
}
