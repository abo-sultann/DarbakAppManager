package com.abosultan.darbakappmanager;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/** Lightweight Darbak updater for API 25 car screens. */
public final class DarbakUpdateManager {
    public static final String UPDATE_MANIFEST_URL =
            "https://raw.githubusercontent.com/abo-sultann/DarbakAppManager/main/update.json";
    private static final String APK_MIME = "application/vnd.android.package-archive";
    private static final long MIN_APK_BYTES = 20_000L;

    public interface CheckCallback {
        void onResult(UpdateInfo info, boolean updateAvailable, String message);
    }
    public interface DownloadCallback {
        void onProgress(int percent, String message);
        void onFinished(boolean ok, File apk, String message);
    }

    public static final class UpdateInfo {
        public final int versionCode;
        public final String versionName;
        public final String apkUrl;
        public final String notes;
        public final String sha256;
        public final long size;

        UpdateInfo(int versionCode, String versionName, String apkUrl, String notes, String sha256, long size) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.notes = notes == null ? "" : notes;
            this.sha256 = sha256 == null ? "" : sha256.trim().toLowerCase(Locale.US);
            this.size = Math.max(0L, size);
        }
    }

    private final Context context;
    private final File updateDir;
    private final File apkFile;

    public DarbakUpdateManager(Context context) {
        this.context = context.getApplicationContext();
        this.updateDir = new File(this.context.getFilesDir(), "updates");
        if (!updateDir.exists()) updateDir.mkdirs();
        this.apkFile = new File(updateDir, "DarbakAppManager-update.apk");
    }

    public File getDownloadedApk() { return apkFile; }

    public void checkAsync(final CheckCallback callback) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String raw = readText(UPDATE_MANIFEST_URL);
                    JSONObject json = new JSONObject(raw);
                    UpdateInfo info = new UpdateInfo(
                            json.getInt("versionCode"),
                            json.optString("versionName", String.valueOf(json.getInt("versionCode"))),
                            json.getString("apkUrl"),
                            json.optString("notes", ""),
                            json.optString("sha256", ""),
                            json.optLong("size", 0L));
                    boolean available = info.versionCode > BuildConfig.VERSION_CODE;
                    callback.onResult(info, available, available ? "يتوفر إصدار " + info.versionName : "لديك أحدث إصدار");
                } catch (Exception e) {
                    callback.onResult(null, false, friendly("تعذر فحص التحديث", e));
                }
            }
        }, "DarbakUpdateCheck").start();
    }

    public void downloadAsync(final UpdateInfo info, final DownloadCallback callback) {
        new Thread(new Runnable() {
            @Override public void run() {
                File temp = new File(updateDir, "DarbakAppManager-update.tmp");
                temp.delete();
                try {
                    download(info, temp, callback);
                    validate(temp, info);
                    if (apkFile.exists() && !apkFile.delete()) throw new IllegalStateException("تعذر استبدال ملف التحديث القديم");
                    if (!temp.renameTo(apkFile)) {
                        copyFile(temp, apkFile);
                        temp.delete();
                    }
                    callback.onFinished(true, apkFile, "التحديث جاهز للتثبيت");
                } catch (Exception e) {
                    temp.delete();
                    callback.onFinished(false, null, friendly("تعذر تنزيل التحديث", e));
                }
            }
        }, "DarbakUpdateDownload").start();
    }

    public boolean installDownloaded() {
        try {
            if (!apkFile.isFile() || apkFile.length() < MIN_APK_BYTES) return false;
            if (Build.VERSION.SDK_INT >= 26 && !context.getPackageManager().canRequestPackageInstalls()) {
                Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.getPackageName()));
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(settings);
                return false;
            }
            Uri uri = Uri.parse("content://" + context.getPackageName() + ".updates/update.apk");
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(uri, APK_MIME);
            view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            view.setClipData(ClipData.newRawUri("Darbak update", uri));
            grantInstallerPermissions(view, uri);
            context.startActivity(view);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void download(UpdateInfo info, File target, DownloadCallback callback) throws Exception {
        HttpURLConnection connection = open(info.apkUrl);
        long total = connection.getContentLengthLong();
        if (info.size > 0L) total = info.size;
        BufferedInputStream input = new BufferedInputStream(connection.getInputStream(), 64 * 1024);
        FileOutputStream output = new FileOutputStream(target);
        byte[] buffer = new byte[64 * 1024];
        long done = 0L;
        int last = -1;
        try {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                output.write(buffer, 0, read);
                done += read;
                int percent = total > 0 ? (int)Math.min(100L, (done * 100L) / total) : 0;
                if (percent != last) {
                    last = percent;
                    callback.onProgress(percent, total > 0 ? "تنزيل التحديث " + percent + "%" : "جارٍ تنزيل التحديث…");
                }
            }
            output.flush();
            output.getFD().sync();
        } finally {
            try { input.close(); } catch (Exception ignored) {}
            try { output.close(); } catch (Exception ignored) {}
            connection.disconnect();
        }
    }

    private void validate(File file, UpdateInfo info) throws Exception {
        if (!file.isFile() || file.length() < MIN_APK_BYTES) throw new IllegalStateException("ملف APK غير مكتمل");
        if (info.size > 0L && file.length() != info.size) throw new IllegalStateException("حجم APK لا يطابق الإصدار المنشور");
        FileInputStream input = new FileInputStream(file);
        try {
            int a = input.read();
            int b = input.read();
            if (a != 0x50 || b != 0x4B) throw new IllegalStateException("الملف ليس APK صالحًا");
        } finally { input.close(); }
        if (!info.sha256.isEmpty() && !sha256(file).equalsIgnoreCase(info.sha256)) {
            throw new IllegalStateException("فشل التحقق SHA-256");
        }
        PackageManager pm = context.getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        PackageInfo installed = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
        if (archive == null || archive.packageName == null || !context.getPackageName().equals(archive.packageName)) {
            throw new IllegalStateException("حزمة التحديث لا تخص هذا التطبيق");
        }
        if (!sameSignature(installed.signatures, archive.signatures)) {
            throw new IllegalStateException("توقيع APK لا يطابق التطبيق المثبت");
        }
    }

    private static boolean sameSignature(Signature[] a, Signature[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) return false;
        for (int i = 0; i < a.length; i++) if (!a[i].equals(b[i])) return false;
        return true;
    }

    private void grantInstallerPermissions(Intent intent, Uri uri) {
        java.util.List<android.content.pm.ResolveInfo> resolved = context.getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (android.content.pm.ResolveInfo result : resolved) {
            if (result.activityInfo != null) {
                try { context.grantUriPermission(result.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                catch (Exception ignored) {}
            }
        }
    }

    private static HttpURLConnection open(String rawUrl) throws Exception {
        String current = rawUrl;
        for (int i = 0; i < 8; i++) {
            HttpURLConnection c = (HttpURLConnection)new URL(current).openConnection();
            c.setConnectTimeout(15_000);
            c.setReadTimeout(45_000);
            c.setInstanceFollowRedirects(false);
            c.setUseCaches(false);
            c.setRequestProperty("User-Agent", "DarbakAppManager/" + BuildConfig.VERSION_NAME);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String next = c.getHeaderField("Location");
                c.disconnect();
                if (next == null) throw new IllegalStateException("رابط تحويل غير صالح");
                current = new URL(new URL(current), next).toString();
                continue;
            }
            if (code < 200 || code >= 300) {
                c.disconnect();
                throw new IllegalStateException("HTTP " + code);
            }
            return c;
        }
        throw new IllegalStateException("تحويلات كثيرة في رابط التحديث");
    }

    private static String readText(String url) throws Exception {
        HttpURLConnection c = open(url);
        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder b = new StringBuilder();
        try {
            String line;
            while ((line = r.readLine()) != null) b.append(line).append('\n');
        } finally { r.close(); c.disconnect(); }
        return b.toString();
    }

    private static void copyFile(File from, File to) throws Exception {
        FileInputStream in = new FileInputStream(from);
        FileOutputStream out = new FileOutputStream(to);
        try {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        } finally { in.close(); out.close(); }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        FileInputStream in = new FileInputStream(file);
        try {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) d.update(buf, 0, n);
        } finally { in.close(); }
        StringBuilder out = new StringBuilder();
        for (byte x : d.digest()) out.append(String.format(Locale.US, "%02x", x & 0xff));
        return out.toString();
    }

    private static String friendly(String prefix, Exception e) {
        String m = e.getMessage();
        if (m == null || m.trim().isEmpty()) m = e.getClass().getSimpleName();
        return prefix + ": " + m;
    }
}
