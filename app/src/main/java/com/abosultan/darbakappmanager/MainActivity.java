package com.abosultan.darbakappmanager;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Darbak App Manager — native lightweight UI for the Allwinner T3 car screen.
 * The UI intentionally manages user-installed packages only; system packages never enter action lists.
 */
public final class MainActivity extends Activity {
    private static final int REQ_STORAGE = 901;

    // Darbak Identity System 2.0
    private static final int BG = Color.rgb(10, 22, 51);          // #0A1633
    private static final int SURFACE = Color.rgb(16, 32, 64);     // #102040
    private static final int CARD = Color.rgb(16, 43, 92);        // #102B5C
    private static final int ACTIVE = Color.rgb(23, 59, 108);     // #173B6C
    private static final int BORDER = Color.rgb(45, 85, 115);     // #2D5573
    private static final int CYAN = Color.rgb(25, 181, 255);      // #19B5FF
    private static final int BUTTON_BLUE = Color.rgb(25, 118, 185);
    private static final int TEXT = Color.rgb(243, 248, 255);
    private static final int TEXT2 = Color.rgb(208, 226, 241);
    private static final int MUTED = Color.rgb(161, 184, 208);
    private static final int GOLD = Color.rgb(201, 164, 75);
    private static final int SAFE = Color.rgb(78, 201, 133);
    private static final int WARN = Color.rgb(244, 174, 66);
    private static final int DANGER = Color.rgb(236, 88, 88);

    private FrameLayout content;
    private TextView pageTitle;
    private TextView pageSubtitle;
    private TextView updateBadge;
    private AppRepository repository;
    private SafeCleaner cleaner;
    private DarbakUpdateManager updater;
    private DarbakUpdateManager.UpdateInfo availableUpdate;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        repository = new AppRepository(this);
        cleaner = new SafeCleaner(this);
        updater = new DarbakUpdateManager(this);
        buildShell();
        showHome();
        checkForUpdate(false);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    private void applyImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(8), dp(18), dp(8));
        header.setBackgroundColor(SURFACE);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(54), dp(54));
        logoLp.setMargins(dp(12), 0, 0, 0);
        header.addView(logo, logoLp);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        pageTitle = text("دربك للتطبيقات", 25, TEXT, true);
        pageSubtitle = text("إدارة آمنة لتطبيقات المستخدم", 14, MUTED, false);
        titles.addView(pageTitle);
        titles.addView(pageSubtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        updateBadge = text("النظام محمي", 13, SAFE, true);
        updateBadge.setGravity(Gravity.CENTER);
        updateBadge.setPadding(dp(14), dp(8), dp(14), dp(8));
        updateBadge.setBackground(round(ACTIVE, 16, BORDER, 1));
        updateBadge.setOnClickListener(v -> {
            if (availableUpdate != null) showUpdateDialog(availableUpdate);
            else checkForUpdate(true);
        });
        header.addView(updateBadge, new LinearLayout.LayoutParams(dp(150), dp(42)));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));

        content = new FrameLayout(this);
        content.setPadding(dp(16), dp(12), dp(16), dp(10));
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(10), dp(7), dp(10), dp(7));
        nav.setBackgroundColor(SURFACE);
        addNav(nav, "⌂  الرئيسية", v -> showHome());
        addNav(nav, "▦  التطبيقات", v -> showApps());
        addNav(nav, "✦  التنظيف", v -> showCleaner());
        addNav(nav, "▣  الأدوات", v -> showTools());
        addNav(nav, "⚙  الإعدادات", v -> showSettings());
        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));

        setContentView(root);
        applyImmersive();
    }

    private void addNav(LinearLayout nav, String label, View.OnClickListener listener) {
        Button b = button(label, ACTIVE, TEXT);
        b.setTextSize(15);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        nav.addView(b, lp);
    }

    private void setPage(String title, String subtitle) {
        pageTitle.setText(title);
        pageSubtitle.setText(subtitle);
        content.removeAllViews();
    }

    private void showHome() {
        setPage("دربك للتطبيقات", "الصفحة الرئيسية");
        final LinearLayout body = vertical();
        body.addView(infoBanner("✓ تطبيقات النظام والمصنع خارج الإدارة والحذف والتنظيف", SAFE));
        TextView loading = text("جاري قراءة حالة الجهاز…", 17, TEXT2, false);
        loading.setPadding(dp(8), dp(18), dp(8), 0);
        body.addView(loading);
        content.addView(body);

        new Thread(() -> {
            final int userCount = repository.getUserApps().size();
            final int protectedCount = repository.countProtectedSystemApps();
            final int backups = hasStoragePermission() ? BackupManager.countBackups() : 0;
            final int quarantine = hasStoragePermission() ? cleaner.quarantineItemCount() : 0;
            runOnUiThread(() -> {
                body.removeAllViews();
                body.addView(infoBanner("✓ تطبيقات النظام والمصنع خارج الإدارة والحذف والتنظيف", SAFE));

                LinearLayout top = new LinearLayout(this);
                top.setOrientation(LinearLayout.HORIZONTAL);

                LinearLayout hero = new LinearLayout(this);
                hero.setOrientation(LinearLayout.VERTICAL);
                hero.setGravity(Gravity.CENTER);
                hero.setPadding(dp(14), dp(10), dp(14), dp(10));
                hero.setBackground(round(CARD, 24, CYAN, 1));
                TextView number = text(String.valueOf(userCount), 54, TEXT, true);
                number.setGravity(Gravity.CENTER);
                TextView label = text("تطبيق غير أصلي", 18, TEXT2, true);
                label.setGravity(Gravity.CENTER);
                TextView sub = text("موجود على الشاشة", 13, MUTED, false);
                sub.setGravity(Gravity.CENTER);
                hero.addView(number);
                hero.addView(label);
                hero.addView(sub);
                LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(dp(250), dp(180));
                heroLp.setMargins(dp(5), dp(10), dp(5), dp(6));
                top.addView(hero, heroLp);

                LinearLayout actions = new LinearLayout(this);
                actions.setOrientation(LinearLayout.HORIZONTAL);
                actions.setGravity(Gravity.CENTER);
                View manage = actionCard("▦", "إدارة التطبيقات", "فتح • نسخة APK • حذف", CYAN, v -> showApps());
                View clean = actionCard("✦", "تنظيف البقايا", "فحص آمن ثم عزل", WARN, v -> showCleaner());
                actions.addView(manage, weightedMargin());
                actions.addView(clean, weightedMargin());
                top.addView(actions, new LinearLayout.LayoutParams(0, dp(190), 1f));
                body.addView(top);

                LinearLayout stats = new LinearLayout(this);
                stats.setOrientation(LinearLayout.HORIZONTAL);
                stats.addView(metricCard("محمية", String.valueOf(protectedCount), SAFE), weightedMargin());
                stats.addView(metricCard("نسخ APK", String.valueOf(backups), CYAN), weightedMargin());
                stats.addView(metricCard("في العزل", String.valueOf(quarantine), quarantine > 0 ? WARN : MUTED), weightedMargin());
                body.addView(stats, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92)));
                body.addView(storageCard());
            });
        }, "HomeStats").start();
    }

    private View actionCard(String symbol, String title, String subtitle, int accent, View.OnClickListener listener) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.setBackground(round(CARD, 24, BORDER, 1));
        TextView icon = text(symbol, 34, accent, true);
        icon.setGravity(Gravity.CENTER);
        TextView t = text(title, 20, TEXT, true);
        t.setGravity(Gravity.CENTER);
        TextView s = text(subtitle, 13, MUTED, false);
        s.setGravity(Gravity.CENTER);
        box.addView(icon);
        box.addView(t);
        box.addView(s);
        box.setOnClickListener(listener);
        return box;
    }

    private void showApps() {
        setPage("التطبيقات غير الأصلية", "تطبيقات المستخدم فقط — النظام مخفي ومحمي");
        LinearLayout body = vertical();

        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        final EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("ابحث باسم التطبيق أو الحزمة…");
        search.setHintTextColor(MUTED);
        search.setTextColor(TEXT);
        search.setTextSize(16);
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackground(round(CARD, 17, BORDER, 1));
        final Button sort = button("الأكبر", ACTIVE, TEXT2);
        searchBar.addView(search, new LinearLayout.LayoutParams(0, dp(52), 1f));
        LinearLayout.LayoutParams sortLp = new LinearLayout.LayoutParams(dp(126), dp(52));
        sortLp.setMargins(dp(8), 0, 0, 0);
        searchBar.addView(sort, sortLp);
        body.addView(searchBar);

        final TextView status = text("جاري تحميل التطبيقات…", 14, MUTED, false);
        status.setPadding(dp(4), dp(6), dp(4), dp(4));
        body.addView(status);
        final ListView list = new ListView(this);
        list.setDividerHeight(0);
        list.setBackgroundColor(BG);
        body.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(body);

        new Thread(() -> {
            final List<AppEntry> all = repository.getUserApps();
            runOnUiThread(() -> {
                final AppAdapter adapter = new AppAdapter(all);
                list.setAdapter(adapter);
                status.setText(all.size() + " تطبيق مستخدم");
                list.setOnItemClickListener((parent, view, position, id) -> showAppDialog(adapter.getItem(position)));
                search.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        adapter.filter(s == null ? "" : s.toString());
                        status.setText(adapter.getCount() + " نتيجة");
                    }
                    @Override public void afterTextChanged(Editable s) {}
                });
                sort.setOnClickListener(v -> {
                    adapter.toggleSort();
                    sort.setText(adapter.sortBySize ? "الاسم" : "الأكبر");
                });
            });
        }, "AppsLoad").start();
    }

    private void showAppDialog(final AppEntry app) {
        if (app == null || ProtectionPolicy.isProtectedPackage(app.packageName)) return;
        LinearLayout box = vertical();
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        head.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(app.label, 22, Color.DKGRAY, true);
        TextView pkg = text(app.packageName, 12, Color.GRAY, false);
        names.addView(name); names.addView(pkg);
        head.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(head);
        TextView details = text("الإصدار: " + app.versionName + " (" + app.versionCode + ")\n"
                + "حجم APK: " + formatBytes(app.apkBytes) + "\n"
                + "التثبيت: " + formatDate(app.firstInstallTime) + "\n"
                + "آخر تحديث: " + formatDate(app.lastUpdateTime), 15, Color.DKGRAY, false);
        details.setPadding(0, dp(8), 0, dp(8));
        box.addView(details);
        String[] actions = {"فتح التطبيق", "إنشاء نسخة احتياطية APK", "معلومات التطبيق في Android", "حذف التطبيق"};
        new AlertDialog.Builder(this)
                .setTitle("تفاصيل التطبيق")
                .setView(box)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) openApp(app);
                    else if (which == 1) backupApp(app);
                    else if (which == 2) openSystemAppInfo(app);
                    else if (which == 3) requestUninstall(app);
                })
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void openApp(AppEntry app) {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(app.packageName);
            if (i == null) toast("لا توجد واجهة تشغيل لهذا التطبيق");
            else startActivity(i);
        } catch (Exception e) { toast("تعذر فتح التطبيق"); }
    }

    private void backupApp(final AppEntry app) {
        if (!ensureStoragePermission()) return;
        toast("جاري إنشاء نسخة APK…");
        new Thread(() -> {
            try {
                final File f = BackupManager.backup(this, app);
                runOnUiThread(() -> toast("تم حفظ النسخة: " + f.getName()));
            } catch (Exception e) {
                runOnUiThread(() -> toast("تعذر إنشاء النسخة الاحتياطية"));
            }
        }, "ApkBackup").start();
    }

    private void openSystemAppInfo(AppEntry app) {
        try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.packageName))); }
        catch (Exception e) { toast("تعذر فتح معلومات التطبيق"); }
    }

    private void requestUninstall(final AppEntry app) {
        if (ProtectionPolicy.isProtectedPackage(app.packageName)) return;
        new AlertDialog.Builder(this)
                .setTitle("حذف " + app.label)
                .setMessage("سيتم فتح نافذة الحذف الرسمية في Android. تطبيقات النظام لا تدخل هذه القائمة أصلًا.")
                .setPositiveButton("متابعة", (d, w) -> {
                    OperationLog.add(this, "REQUEST_UNINSTALL " + app.packageName);
                    startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName)));
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showCleaner() {
        setPage("تنظيف البقايا", "فحص محدود وآمن — لا وصول إلى /system");
        LinearLayout body = vertical();
        if (!hasStoragePermission()) {
            body.addView(infoBanner("يلزم إذن التخزين لفحص Android/data وAndroid/obb وAndroid/media فقط.", WARN));
            Button grant = button("منح إذن التخزين", BUTTON_BLUE, TEXT);
            grant.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE));
            body.addView(grant, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
            content.addView(body);
            return;
        }

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        final TextView status = text("اضغط «فحص الآن» لقراءة البقايا", 15, TEXT2, false);
        status.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        final Button scan = button("فحص الآن", BUTTON_BLUE, TEXT);
        controls.addView(status, new LinearLayout.LayoutParams(0, dp(54), 1f));
        controls.addView(scan, new LinearLayout.LayoutParams(dp(170), dp(54)));
        body.addView(controls);

        body.addView(infoBanner("المؤكد ✓ يُحدد تلقائيًا. المرشح ؟ لا يُحدد إلا بقرارك.", CYAN));
        final ListView list = new ListView(this);
        list.setDividerHeight(0);
        body.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        final Button quarantine = button("نقل المحدد إلى العزل", ACTIVE, MUTED);
        quarantine.setEnabled(false);
        body.addView(quarantine, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        content.addView(body);

        final CleanerAdapter[] holder = new CleanerAdapter[1];
        scan.setOnClickListener(v -> {
            scan.setEnabled(false);
            status.setText("جاري الفحص…");
            new Thread(() -> {
                final List<SafeCleaner.LeftoverItem> items = cleaner.scan();
                runOnUiThread(() -> {
                    holder[0] = new CleanerAdapter(items);
                    list.setAdapter(holder[0]);
                    long total = 0L; int confirmed = 0;
                    for (SafeCleaner.LeftoverItem item : items) {
                        total += item.bytes;
                        if (item.confidence == SafeCleaner.CONFIRMED) confirmed++;
                    }
                    status.setText(items.size() + " نتيجة • " + confirmed + " مؤكدة • " + formatBytes(total));
                    quarantine.setEnabled(!items.isEmpty());
                    quarantine.setTextColor(TEXT);
                    scan.setEnabled(true);
                });
            }, "SafeScan").start();
        });

        quarantine.setOnClickListener(v -> {
            if (holder[0] == null) return;
            final List<SafeCleaner.LeftoverItem> selected = holder[0].selected();
            if (selected.isEmpty()) { toast("لم تحدد أي بقايا"); return; }
            new AlertDialog.Builder(this)
                    .setTitle("العزل الآمن")
                    .setMessage("سيتم نقل " + selected.size() + " عنصرًا إلى عزل دربك أولًا. يمكن استعادة آخر دفعة قبل الحذف النهائي.")
                    .setPositiveButton("نقل إلى العزل", (d, w) -> new Thread(() -> {
                        final int moved = cleaner.quarantine(selected);
                        runOnUiThread(() -> { toast("تم عزل " + moved + " عنصر"); showCleaner(); });
                    }, "Quarantine").start())
                    .setNegativeButton("إلغاء", null)
                    .show();
        });
    }

    private void showTools() {
        setPage("الأدوات", "نسخ APK • العزل • السجل");
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = vertical();
        scroll.addView(body);
        if (!hasStoragePermission()) body.addView(infoBanner("امنح إذن التخزين لاستخدام النسخ والعزل.", WARN));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(metricCard("نسخ APK", hasStoragePermission() ? String.valueOf(BackupManager.countBackups()) : "—", CYAN), weightedMargin());
        stats.addView(metricCard("حجم النسخ", hasStoragePermission() ? formatBytes(BackupManager.backupBytes()) : "—", TEXT2), weightedMargin());
        stats.addView(metricCard("العزل", hasStoragePermission() ? formatBytes(cleaner.quarantineBytes()) : "—", WARN), weightedMargin());
        body.addView(stats, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(104)));

        Button restore = button("↶  استعادة آخر دفعة من العزل", ACTIVE, TEXT);
        restore.setOnClickListener(v -> {
            if (!ensureStoragePermission()) return;
            new Thread(() -> {
                final int count = cleaner.restoreLatestBatch();
                runOnUiThread(() -> toast(count > 0 ? "تمت استعادة " + count + " عنصر" : "لا توجد دفعة قابلة للاستعادة"));
            }, "RestoreQuarantine").start();
        });
        body.addView(restore, buttonLp());

        Button clear = button("حذف العزل نهائيًا — ضغط مطول", CARD, DANGER);
        clear.setOnClickListener(v -> toast("للحماية: استخدم ضغطًا مطولًا"));
        clear.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("حذف نهائي")
                    .setMessage("سيتم حذف محتويات عزل DarbakAppManager فقط. لا يمكن التراجع عن هذه الخطوة.")
                    .setPositiveButton("حذف نهائي", (d, w) -> new Thread(() -> {
                        final boolean ok = cleaner.clearQuarantinePermanently();
                        runOnUiThread(() -> toast(ok ? "تم تنظيف العزل" : "تعذر حذف بعض الملفات"));
                    }, "ClearQuarantine").start())
                    .setNegativeButton("إلغاء", null).show();
            return true;
        });
        body.addView(clear, buttonLp());

        Button logs = button("سجل العمليات", ACTIVE, TEXT);
        logs.setOnClickListener(v -> showLogs());
        body.addView(logs, buttonLp());
        content.addView(scroll);
    }

    private void showLogs() {
        setPage("سجل العمليات", "آخر العمليات المحلية داخل دربك للتطبيقات");
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = vertical();
        scroll.addView(body);
        List<String> logs = OperationLog.recent(this, 60);
        if (logs.isEmpty()) body.addView(infoBanner("لا توجد عمليات مسجلة بعد.", CYAN));
        else for (String line : logs) body.addView(wideCard("سجل", line, MUTED));
        content.addView(scroll);
    }

    private void showSettings() {
        setPage("الإعدادات", "التحديث • الحماية • حول");
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = vertical();
        scroll.addView(body);

        LinearLayout update = cardBox();
        TextView uTitle = text("تحديث دربك", 21, TEXT, true);
        TextView uState = text("الإصدار المثبت " + BuildConfig.VERSION_NAME + " • البناء " + BuildConfig.VERSION_CODE, 14, TEXT2, false);
        Button check = button(availableUpdate == null ? "البحث عن تحديث" : "تثبيت " + availableUpdate.versionName, BUTTON_BLUE, TEXT);
        check.setOnClickListener(v -> {
            if (availableUpdate != null) showUpdateDialog(availableUpdate); else checkForUpdate(true);
        });
        update.addView(uTitle); update.addView(uState);
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        up.topMargin = dp(10); update.addView(check, up);
        body.addView(update, sectionLp());

        body.addView(wideCard("حماية النظام", "مفعلة دائمًا • تطبيقات النظام والمصنع لا تدخل قوائم الإدارة", SAFE));
        body.addView(wideCard("التنظيف", "لا يوجد حذف تلقائي • البقايا تنتقل إلى العزل أولًا", CYAN));
        body.addView(wideCard("Root", "غير مستخدم", MUTED));

        Button about = button("حول • الهوية والملكية", ACTIVE, TEXT);
        about.setOnClickListener(v -> showAbout());
        about.setOnLongClickListener(v -> { showDiagnostics(); return true; });
        body.addView(about, buttonLp());
        content.addView(scroll);
    }

    private void showAbout() {
        setPage("حول", "هوية دربك والملكية");
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = vertical();
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(body);

        LinearLayout owner = cardBox();
        owner.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView signature = new ImageView(this);
        signature.setImageResource(R.drawable.darbak_owner_signature);
        signature.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        owner.addView(signature, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(245)));
        TextView brand = text("دربك", 23, TEXT, true); brand.setGravity(Gravity.CENTER);
        TextView ownerLine = text("تصميم وتطوير  •  أبوسلطان", 17, GOLD, true); ownerLine.setGravity(Gravity.CENTER);
        owner.addView(brand); owner.addView(ownerLine);
        body.addView(owner, sectionLp());
        body.addView(wideCard("دربك للتطبيقات", "الإصدار " + BuildConfig.VERSION_NAME + " • البناء " + BuildConfig.VERSION_CODE, CYAN));
        body.addView(wideCard("الهوية والملكية", "دربك • تصميم وتطوير • أبوسلطان", GOLD));
        body.addView(wideCard("الاستخدام", "تطبيق خاص — جميع الحقوق محفوظة", TEXT2));
        body.addView(wideCard("الحزمة", getPackageName(), MUTED));
        content.addView(scroll);
    }

    private void showDiagnostics() {
        setPage("التشخيص الفني", "صفحة مخفية للدعم والاختبار");
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = vertical();
        scroll.addView(body);
        body.addView(wideCard("Android", android.os.Build.VERSION.RELEASE + " • API " + android.os.Build.VERSION.SDK_INT, CYAN));
        body.addView(wideCard("إذن التخزين", hasStoragePermission() ? "ممنوح" : "غير ممنوح", hasStoragePermission() ? SAFE : WARN));
        body.addView(wideCard("تطبيقات المستخدم", String.valueOf(repository.getUserApps().size()), TEXT2));
        body.addView(wideCard("تطبيقات محمية", String.valueOf(repository.countProtectedSystemApps()), SAFE));
        body.addView(wideCard("مسارات الفحص", "Android/data • Android/obb • Android/media", MUTED));
        body.addView(wideCard("مصدر التحديث", DarbakUpdateManager.UPDATE_MANIFEST_URL, MUTED));
        content.addView(scroll);
    }

    private void checkForUpdate(final boolean interactive) {
        if (interactive) toast("جاري فحص التحديث…");
        updater.checkAsync((info, available, message) -> runOnUiThread(() -> {
            if (available && info != null) {
                availableUpdate = info;
                updateBadge.setText("تحديث " + info.versionName);
                updateBadge.setTextColor(TEXT);
                updateBadge.setBackground(round(BUTTON_BLUE, 16, CYAN, 1));
                if (interactive) showUpdateDialog(info);
            } else {
                if (interactive) new AlertDialog.Builder(this).setTitle("تحديث دربك").setMessage(message).setPositiveButton("حسنًا", null).show();
                if (info != null) {
                    updateBadge.setText("محدّث " + BuildConfig.VERSION_NAME);
                    updateBadge.setTextColor(SAFE);
                }
            }
        }));
    }

    private void showUpdateDialog(final DarbakUpdateManager.UpdateInfo info) {
        String notes = info.notes == null || info.notes.trim().isEmpty() ? "تحسينات وإصلاحات دربك." : info.notes;
        new AlertDialog.Builder(this)
                .setTitle("يتوفر تحديث " + info.versionName)
                .setMessage(notes + "\n\nالحجم: " + (info.size > 0 ? formatBytes(info.size) : "يحدد أثناء التنزيل")
                        + "\nسيتم التحقق من SHA-256 وتوقيع التطبيق قبل التثبيت.")
                .setPositiveButton("تنزيل وتثبيت", (d, w) -> downloadUpdate(info))
                .setNegativeButton("لاحقًا", null)
                .show();
    }

    private void downloadUpdate(final DarbakUpdateManager.UpdateInfo info) {
        final ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("تحديث دربك");
        progress.setMessage("بدء التنزيل…");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setCancelable(false);
        progress.show();
        updater.downloadAsync(info, new DarbakUpdateManager.DownloadCallback() {
            @Override public void onProgress(int percent, String message) {
                runOnUiThread(() -> { progress.setProgress(percent); progress.setMessage(message); });
            }
            @Override public void onFinished(boolean ok, File apk, String message) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    if (!ok) {
                        new AlertDialog.Builder(MainActivity.this).setTitle("فشل التحديث").setMessage(message).setPositiveButton("حسنًا", null).show();
                        return;
                    }
                    OperationLog.add(MainActivity.this, "UPDATE_READY " + info.versionName);
                    boolean opened = updater.installDownloaded();
                    if (!opened) toast("ملف التحديث جاهز. اسمح بتثبيت التطبيقات غير المعروفة ثم أعد المحاولة.");
                });
            }
        });
    }

    private View storageCard() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
        long total = stat.getBlockCountLong() * stat.getBlockSizeLong();
        long free = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        long used = Math.max(0L, total - free);
        int percent = total > 0 ? (int)Math.min(100L, used * 100L / total) : 0;
        LinearLayout box = cardBox();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = text("التخزين", 16, TEXT, true);
        TextView amount = text(formatBytes(used) + " مستخدم من " + formatBytes(total), 13, MUTED, false);
        amount.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(amount, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(row);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100); bar.setProgress(percent);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12));
        bp.topMargin = dp(7); box.addView(bar, bp);
        LinearLayout.LayoutParams out = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(74));
        out.setMargins(dp(5), dp(6), dp(5), 0);
        box.setLayoutParams(out);
        return box;
    }

    private LinearLayout cardBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));
        box.setBackground(round(CARD, 23, BORDER, 1));
        return box;
    }

    private View metricCard(String label, String value, int accent) {
        LinearLayout box = cardBox();
        box.setGravity(Gravity.CENTER);
        TextView v = text(value, 24, accent, true); v.setGravity(Gravity.CENTER);
        TextView l = text(label, 13, TEXT2, false); l.setGravity(Gravity.CENTER);
        box.addView(v); box.addView(l);
        return box;
    }

    private View wideCard(String title, String value, int accent) {
        LinearLayout box = cardBox();
        TextView t = text(title, 14, accent, true);
        TextView v = text(value, 15, TEXT, false);
        v.setPadding(0, dp(4), 0, 0);
        box.addView(t); box.addView(v);
        box.setLayoutParams(sectionLp());
        return box;
    }

    private View infoBanner(String value, int accent) {
        TextView t = text(value, 14, TEXT2, true);
        t.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        t.setBackground(round(SURFACE, 16, accent, 1));
        return t;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        l.setBackgroundColor(BG);
        return l;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private Button button(String value, int background, int foreground) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextSize(16);
        b.setTextColor(foreground);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setBackground(round(background, 17, BORDER, 1));
        return b;
    }

    private GradientDrawable round(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), stroke);
        return d;
    }

    private LinearLayout.LayoutParams weightedMargin() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        p.setMargins(dp(5), dp(5), dp(5), dp(5));
        return p;
    }

    private LinearLayout.LayoutParams sectionLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(4), dp(6), dp(4), dp(6));
        return p;
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        p.setMargins(dp(4), dp(6), dp(4), dp(6));
        return p;
    }

    private boolean hasStoragePermission() {
        return android.os.Build.VERSION.SDK_INT < 23
                || (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    private boolean ensureStoragePermission() {
        if (hasStoragePermission()) return true;
        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        return false;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE) {
            toast(hasStoragePermission() ? "تم منح إذن التخزين" : "إذن التخزين مطلوب لهذه الوظيفة");
            showHome();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String formatDate(long millis) {
        if (millis <= 0) return "—";
        return new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(new Date(millis));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.1f GB", mb / 1024.0);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private final class AppAdapter extends BaseAdapter {
        private final List<AppEntry> source = new ArrayList<>();
        private final List<AppEntry> shown = new ArrayList<>();
        private String query = "";
        boolean sortBySize = false;

        AppAdapter(List<AppEntry> input) { source.addAll(input); rebuild(); }
        void filter(String q) { query = q == null ? "" : q.trim().toLowerCase(Locale.US); rebuild(); }
        void toggleSort() { sortBySize = !sortBySize; rebuild(); }
        private void rebuild() {
            shown.clear();
            for (AppEntry a : source) {
                String hay = (a.label + " " + a.packageName).toLowerCase(Locale.US);
                if (query.isEmpty() || hay.contains(query)) shown.add(a);
            }
            if (sortBySize) Collections.sort(shown, (a, b) -> Long.compare(b.apkBytes, a.apkBytes));
            else Collections.sort(shown, (a, b) -> a.label.compareToIgnoreCase(b.label));
            notifyDataSetChanged();
        }
        @Override public int getCount() { return shown.size(); }
        @Override public AppEntry getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            AppEntry app = getItem(position);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(8), dp(14), dp(8));
            row.setBackground(round(CARD, 18, BORDER, 1));
            ImageView icon = new ImageView(MainActivity.this);
            icon.setImageDrawable(app.icon);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            row.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
            LinearLayout labels = new LinearLayout(MainActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(dp(12), 0, dp(12), 0);
            labels.addView(text(app.label, 18, TEXT, true));
            labels.addView(text(app.packageName + "  •  v" + app.versionName, 12, MUTED, false));
            row.addView(labels, new LinearLayout.LayoutParams(0, dp(58), 1f));
            TextView size = text(formatBytes(app.apkBytes), 14, CYAN, true);
            size.setGravity(Gravity.CENTER);
            row.addView(size, new LinearLayout.LayoutParams(dp(110), dp(52)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70));
            lp.setMargins(dp(2), dp(4), dp(2), dp(4));
            row.setLayoutParams(lp);
            return row;
        }
    }

    private final class CleanerAdapter extends BaseAdapter {
        private final List<SafeCleaner.LeftoverItem> items;
        CleanerAdapter(List<SafeCleaner.LeftoverItem> items) { this.items = items == null ? new ArrayList<SafeCleaner.LeftoverItem>() : items; }
        List<SafeCleaner.LeftoverItem> selected() {
            List<SafeCleaner.LeftoverItem> out = new ArrayList<>();
            for (SafeCleaner.LeftoverItem i : items) if (i.selected) out.add(i);
            return out;
        }
        @Override public int getCount() { return items.size(); }
        @Override public SafeCleaner.LeftoverItem getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            final SafeCleaner.LeftoverItem item = getItem(position);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(7), dp(12), dp(7));
            row.setBackground(round(CARD, 18, item.confidence == SafeCleaner.CONFIRMED ? SAFE : BORDER, 1));
            CheckBox check = new CheckBox(MainActivity.this);
            check.setChecked(item.selected);
            check.setOnCheckedChangeListener((buttonView, isChecked) -> item.selected = isChecked);
            row.addView(check, new LinearLayout.LayoutParams(dp(52), dp(52)));
            LinearLayout labels = new LinearLayout(MainActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(text((item.confidence == SafeCleaner.CONFIRMED ? "✓ مؤكد  " : "؟ مرشح  ") + item.packageName, 16, TEXT, true));
            labels.addView(text(item.sourceLabel + "  •  " + formatBytes(item.bytes), 12, MUTED, false));
            row.addView(labels, new LinearLayout.LayoutParams(0, dp(54), 1f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68));
            lp.setMargins(dp(2), dp(4), dp(2), dp(4));
            row.setLayoutParams(lp);
            return row;
        }
    }
}
