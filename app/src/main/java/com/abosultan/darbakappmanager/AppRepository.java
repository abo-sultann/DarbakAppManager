package com.abosultan.darbakappmanager;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AppRepository {
    private final PackageManager pm;

    public AppRepository(Context context) {
        this.pm = context.getApplicationContext().getPackageManager();
    }

    public List<AppEntry> getUserApps() {
        List<AppEntry> result = new ArrayList<>();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo ai : apps) {
            if (isSystem(ai) || ProtectionPolicy.isProtectedPackage(ai.packageName)) continue;
            try {
                PackageInfo pi = pm.getPackageInfo(ai.packageName, 0);
                String label = String.valueOf(pm.getApplicationLabel(ai));
                String version = pi.versionName == null ? "-" : pi.versionName;
                long bytes = ai.sourceDir == null ? 0L : new File(ai.sourceDir).length();
                result.add(new AppEntry(label, ai.packageName, version, pi.versionCode, bytes,
                        pi.firstInstallTime, pi.lastUpdateTime, ai.sourceDir, pm.getApplicationIcon(ai)));
            } catch (Exception ignored) {
                // Package changed during enumeration; skip it safely.
            }
        }
        Collections.sort(result, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return result;
    }

    public Set<String> getAllInstalledPackageNames() {
        Set<String> out = new HashSet<>();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) out.add(ai.packageName);
        return out;
    }

    public int countProtectedSystemApps() {
        int count = 0;
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if (isSystem(ai) || ProtectionPolicy.isProtectedPackage(ai.packageName)) count++;
        }
        return count;
    }

    private static boolean isSystem(ApplicationInfo ai) {
        return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                || (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }
}
