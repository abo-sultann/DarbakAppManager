package com.abosultan.darbakappmanager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class ProtectionPolicy {
    private ProtectionPolicy() {}

    private static final Set<String> EXACT = new HashSet<>(Arrays.asList(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.packageinstaller",
            "com.android.providers.settings",
            "com.android.providers.media",
            "com.android.providers.downloads",
            "com.android.externalstorage",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.webview",
            "com.abosultan.darbakappmanager"
    ));

    private static final String[] PREFIXES = new String[]{
            "com.android.internal.",
            "com.allwinner.",
            "com.softwinner.",
            "com.autochips.",
            "com.microntek.",
            "com.syu.",
            "com.zjinnova.",
            "com.mediatek."
    };

    public static boolean isProtectedPackage(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return true;
        if (EXACT.contains(packageName)) return true;
        for (String prefix : PREFIXES) {
            if (packageName.startsWith(prefix)) return true;
        }
        return false;
    }
}
