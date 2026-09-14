package com.abosultan.darbakappmanager;

import android.graphics.drawable.Drawable;

public final class AppEntry {
    public final String label;
    public final String packageName;
    public final String versionName;
    public final int versionCode;
    public final long apkBytes;
    public final long firstInstallTime;
    public final long lastUpdateTime;
    public final String sourceDir;
    public final Drawable icon;

    public AppEntry(String label, String packageName, String versionName, int versionCode,
                    long apkBytes, long firstInstallTime, long lastUpdateTime,
                    String sourceDir, Drawable icon) {
        this.label = label;
        this.packageName = packageName;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.apkBytes = apkBytes;
        this.firstInstallTime = firstInstallTime;
        this.lastUpdateTime = lastUpdateTime;
        this.sourceDir = sourceDir;
        this.icon = icon;
    }
}
