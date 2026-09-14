package com.abosultan.darbakappmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.HashSet;
import java.util.Set;

public final class PackageRemovedReceiver extends BroadcastReceiver {
    public static final String PREFS = "darbak_app_manager";
    public static final String REMOVED_PACKAGES = "removed_packages";

    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) return;
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
        Uri data = intent.getData();
        if (data == null) return;
        String pkg = data.getSchemeSpecificPart();
        if (pkg == null || ProtectionPolicy.isProtectedPackage(pkg)) return;

        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> copy = new HashSet<>(sp.getStringSet(REMOVED_PACKAGES, new HashSet<String>()));
        copy.add(pkg);
        sp.edit().putStringSet(REMOVED_PACKAGES, copy).apply();
    }
}
