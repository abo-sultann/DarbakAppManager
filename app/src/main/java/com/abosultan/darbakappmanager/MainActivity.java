package com.abosultan.darbakappmanager;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQ_STORAGE = 901;
    private static final int BG = Color.rgb(11, 13, 16);
    private static final int PANEL = Color.rgb(21, 25, 31);
    private static final int PANEL_ALT = Color.rgb(29, 35, 43);
    private static final int GOLD = Color.rgb(201, 164, 75);
    private static final int WHITE = Color.rgb(247, 247, 247);
    private static final int MUTED = Color.rgb(174, 182, 194);
    private static final int SAFE = Color.rgb(88, 196, 136);
    private static final int DANGER = Color.rgb(228, 90, 90);

    private FrameLayout content;
    private AppRepository repository;
    private SafeCleaner cleaner;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        repository = new AppRepository(this);
        cleaner = new SafeCleaner(this);
        buildShell();
        showHome();
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
        header.setPadding(dp(24), dp(8), dp(24), dp(8));
        header.setBackgroundColor(PANEL);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("دربك للتطبيقات", 25, WHITE, true);
        TextView subtitle = text("إدارة آمنة لتطبيقات المستخدم", 14, MUTED, false);
        titleBox.addView(title);
        titleBox.addView(subtitle);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView shield = text("● حماية النظام مفعلة", 14, SAFE, true);
        shield.setGravity(Gravity.CENTER);
        shield.setPadding(dp(16), dp(9), dp(16), dp(9));
        shield.setBackground(round(PANEL_ALT, 18, SAFE, 1));
        header.addView(shield);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(74)));

        content = new FrameLayout(this);
        content.setPadding(dp(18), dp(14), dp(18), dp(10));
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(10), dp(8), dp(10), dp(8));
        nav.setBackgroundColor(PANEL);
        addNav(nav, "الرئيسية", new View.OnClickListener() { @Override public void onClick(View v) { showHome(); }});
        addNav(nav, "التطبيقات", new View.OnClickListener() { @Override public void onClick(View v) { showApps(); }});
        addNav(nav, "التنظيف", new View.OnClickListener() { @Override public void onClick(View v) { showCleaner(); }});
        addNav(nav, "الأدوات", new View.OnClickListener() { @Override public void onClick(View v) { showTools(); }});
        addNav(nav, "الإعدادات", new View.OnClickListener() { @Override public void onClick(View v) { showSettings(); }});
        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));

        setContentView(root);
        applyImmersive();
    }

    private void addNav(LinearLayout nav, String label, View.OnClickListener listener) {
        Button b = button(label, PANEL_ALT, WHITE);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(dp(5), 0, dp(5), 0);
        nav.addView(b, lp);
    }

    private void showHome() {
        setPageTitle("الرئيسية", "ملخص آمن بدون تعديل تطبيقات النظام");
        final LinearLayout body = vertical();
        body.addView(infoBanner("تطبيقات النظام والمصنع خارج نطاق الإدارة والتنظيف بشكل دائم.", SAFE));
        TextView loading = text("جاري قراءة التطبيقات…", 18, MUTED, false);
        loading.setPadding(0, dp(24), 0, 0);
        body.addView(loading);
        content.addView(body);

        new Thread(new Runnable() {
            @Override public void run() {
                final int userCount = repository.getUserApps().size();
                final int protectedCount = repository.countProtectedSystemApps();
                final int backups = hasStoragePermission() ? BackupManager.countBackups() : 0;
                final int quarantine = hasStoragePermission() ? cleaner.quarantineItemCount() : 0;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        body.removeAllViews();
                        body.addView(infoBanner("تطبيقات النظام والمصنع خارج نطاق الإدارة والتنظيف بشكل دائم.", SAFE));
                        LinearLayout cards = new LinearLayout(MainActivity.this);
                        cards.setOrientation(LinearLayout.HORIZONTAL);
                        cards.addView(metricCard("تطبيقات المستخدم", String.valueOf(userCount), GOLD), weighted());
                        cards.addView(metricCard("تطبيقات محمية", String.valueOf(protectedCount), SAFE), weighted());
                        cards.addView(metricCard("نسخ APK", String.valueOf(backups), WHITE), weighted());
                        cards.addView(metricCard("في العزل", String.valueOf(quarantine), quarantine > 0 ? GOLD : MUTED), weighted());
                        body.addView(cards);
                        body.addView(storageCard());

                        LinearLayout actions = new LinearLayout(MainActivity.this);
                        actions.setOrientation(LinearLayout.HORIZONTAL);
                        Button apps = button("إدارة التطبيقات", GOLD, BG);
                        apps.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showApps(); }});
                        Button clean = button("فحص البقايا", PANEL_ALT, WHITE);
                        clean.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showCleaner(); }});
                        actions.addView(apps, weightedMargin());
                        actions.addView(clean, weightedMargin());
                        body.addView(actions);
                    }
                });
            }
        }).start();
    }

    private void showApps() {
        setPageTitle("التطبيقات غير الأصلية", "تطبيقات المستخدم فقط — النظام مخفي ومحمي");
        final LinearLayout body = vertical();
        final EditText search = new EditText(this);
        search.setHint("ابحث باسم التطبيق أو الحزمة…");
        search.setHintTextColor(MUTED);
        search.setTextColor(WHITE);
        search.setSingleLine(true);
        search.setTextSize(17);
        search.setPadding(dp(18), 0, dp(18), 0);
        search.setBackground(round(PANEL_ALT, 16, Color.TRANSPARENT, 0));
        body.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        final TextView status = text("جاري تحميل التطبيقات…", 15, MUTED, false);
        status.setPadding(dp(4), dp(8), dp(4), dp(5));
        body.addView(status);

        final ListView list = new ListView(this);
        list.setDividerHeight(0);
        list.setBackgroundColor(BG);
        body.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(body);

        new Thread(new Runnable() {
            @Override public void run() {
                final List<AppEntry> all = repository.getUserApps();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
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
                    }
                });
            }
        }).start();
    }

    private void showAppDialog(final AppEntry app) {
        if (app == null || ProtectionPolicy.isProtectedPackage(app.packageName)) return;
        String details = app.packageName
                + "\nالإصدار: " + app.versionName + " (" + app.versionCode + ")"
                + "\nحجم APK: " + formatBytes(app.apkBytes)
                + "\nالتثبيت: " + formatDate(app.firstInstallTime)
                + "\nآخر تحديث: " + formatDate(app.lastUpdateTime);
        String[] actions = new String[]{"فتح التطبيق", "إنشاء نسخة احتياطية APK", "معلومات التطبيق في النظام", "حذف التطبيق"};
        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setMessage(details)
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
        toast("جاري إنشاء النسخة…");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final File file = BackupManager.backup(MainActivity.this, app);
                    runOnUiThread(() -> toast("تم حفظ النسخة: " + file.getName()));
                } catch (Exception e) {
                    runOnUiThread(() -> toast("تعذر إنشاء النسخة الاحتياطية"));
                }
            }
        }).start();
    }

    private void openSystemAppInfo(AppEntry app) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.packageName));
            startActivity(i);
        } catch (Exception e) { toast("تعذر فتح معلومات التطبيق"); }
    }

    private void requestUninstall(final AppEntry app) {
        if (ProtectionPolicy.isProtectedPackage(app.packageName)) return;
        new AlertDialog.Builder(this)
                .setTitle("حذف " + app.label)
                .setMessage("سيتم فتح شاشة الحذف الرسمية في Android. التطبيق لا يملك صلاحية حذف أي برنامج بصمت.")
                .setPositiveButton("متابعة", (dialog, which) -> {
                    OperationLog.add(MainActivity.this, "REQUEST_UNINSTALL " + app.packageName);
                    Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName));
                    startActivity(i);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showCleaner() {
        setPageTitle("التنظيف الآمن", "الفحص محصور في بقايا التطبيقات على التخزين الخارجي");
        final LinearLayout body = vertical();
        if (!hasStoragePermission()) {
            body.addView(infoBanner("يلزم إذن التخزين لفحص البقايا. لا يمنح هذا الإذن حق تعديل /system.", GOLD));
            Button grant = button("منح إذن التخزين", GOLD, BG);
            grant.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE));
            body.addView(grant, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
            content.addView(body);
            return;
        }

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        final TextView status = text("لم يبدأ الفحص", 16, MUTED, false);
        Button scan = button("فحص الآن", GOLD, BG);
        top.addView(status, new LinearLayout.LayoutParams(0, dp(54), 1f));
        top.addView(scan, new LinearLayout.LayoutParams(dp(180), dp(54)));
        body.addView(top);

        final ListView list = new ListView(this);
        list.setDividerHeight(0);
        body.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        final Button quarantine = button("نقل المحدد إلى العزل", PANEL_ALT, WHITE);
        quarantine.setEnabled(false);
        body.addView(quarantine, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        content.addView(body);

        final CleanerAdapter[] holder = new CleanerAdapter[1];
        scan.setOnClickListener(v -> {
            status.setText("جاري الفحص…");
            scan.setEnabled(false);
            new Thread(() -> {
                final List<SafeCleaner.LeftoverItem> items = cleaner.scan();
                runOnUiThread(() -> {
                    holder[0] = new CleanerAdapter(items);
                    list.setAdapter(holder[0]);
                    int confirmed = 0;
                    for (SafeCleaner.LeftoverItem item : items) if (item.confidence == SafeCleaner.CONFIRMED) confirmed++;
                    status.setText(items.size() + " نتيجة — " + confirmed + " مؤكدة");
                    quarantine.setEnabled(!items.isEmpty());
                    scan.setEnabled(true);
                    if (items.isEmpty()) toast("لا توجد بقايا في المسارات الآمنة");
                });
            }).start();
        });

        quarantine.setOnClickListener(v -> {
            if (holder[0] == null || holder[0].selectedCount() == 0) {
                toast("لم يتم تحديد أي بقايا");
                return;
            }
            final List<SafeCleaner.LeftoverItem> items = holder[0].items;
            new AlertDialog.Builder(this)
                    .setTitle("نقل إلى العزل")
                    .setMessage("سيتم نقل " + holder[0].selectedCount() + " عنصر إلى مجلد عزل قابل للاستعادة. لن يتم الحذف النهائي الآن.")
                    .setPositiveButton("نقل", (dialog, which) -> new Thread(() -> {
                        final int moved = cleaner.quarantine(items);
                        runOnUiThread(() -> {
                            toast("تم نقل " + moved + " عنصر إلى العزل");
                            showCleaner();
                        });
                    }).start())
                    .setNegativeButton("إلغاء", null)
                    .show();
        });
    }

    private void showTools() {
        setPageTitle("الأدوات", "النسخ الاحتياطية والعزل وسجل العمليات");
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = vertical();
        scroll.addView(body);

        if (!hasStoragePermission()) {
            body.addView(infoBanner("أدوات التخزين تحتاج إذن التخزين.", GOLD));
        } else {
            body.addView(infoBanner("الملفات في العزل لم تُحذف نهائيًا بعد ويمكن استعادة آخر دفعة.", SAFE));
            body.addView(metricWide("نسخ APK", BackupManager.countBackups() + " ملف — " + formatBytes(BackupManager.backupBytes())));
            body.addView(metricWide("العزل", cleaner.quarantineItemCount() + " عنصر — " + formatBytes(cleaner.quarantineBytes())));

            Button restore = button("استعادة آخر عملية عزل", PANEL_ALT, WHITE);
            restore.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("استعادة آخر عملية")
                    .setMessage("ستتم إعادة الملفات إلى مساراتها الأصلية فقط إذا كانت تلك المسارات ما زالت فارغة وآمنة.")
                    .setPositiveButton("استعادة", (d, w) -> new Thread(() -> {
                        final int n = cleaner.restoreLatestBatch();
                        runOnUiThread(() -> { toast("تمت استعادة " + n + " عنصر"); showTools(); });
                    }).start())
                    .setNegativeButton("إلغاء", null).show());
            body.addView(restore, marginTop(dp(54), 8));

            Button clear = button("تفريغ العزل نهائيًا — ضغط مطول", DANGER, WHITE);
            clear.setOnClickListener(v -> toast("للحماية: اضغط مطولًا للحذف النهائي"));
            clear.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("حذف نهائي")
                        .setMessage("سيتم حذف محتويات مجلد العزل فقط. لا يمكن التراجع بعد هذه الخطوة.")
                        .setPositiveButton("حذف نهائي", (d, w) -> new Thread(() -> {
                            final boolean ok = cleaner.clearQuarantinePermanently();
                            runOnUiThread(() -> { toast(ok ? "تم تفريغ العزل" : "تعذر حذف بعض الملفات"); showTools(); });
                        }).start())
                        .setNegativeButton("إلغاء", null).show();
                return true;
            });
            body.addView(clear, marginTop(dp(54), 8));
        }

        TextView logTitle = text("آخر العمليات", 19, GOLD, true);
        logTitle.setPadding(0, dp(18), 0, dp(8));
        body.addView(logTitle);
        List<String> logs = OperationLog.recent(this, 8);
        if (logs.isEmpty()) body.addView(text("لا توجد عمليات مسجلة بعد.", 15, MUTED, false));
        else for (String line : logs) body.addView(metricWide("سجل", line));
        content.addView(scroll);
    }

    private void showSettings() {
        setPageTitle("الإعدادات", "إعدادات أمان ثابتة لهوية دربك");
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = vertical();
        scroll.addView(body);

        CheckBox protection = new CheckBox(this);
        protection.setText("حماية تطبيقات النظام والمصنع — مفعلة دائمًا");
        protection.setTextColor(WHITE);
        protection.setTextSize(18);
        protection.setChecked(true);
        protection.setEnabled(false);
        protection.setPadding(dp(16), dp(14), dp(16), dp(14));
        protection.setBackground(round(PANEL, 16, SAFE, 1));
        body.addView(protection);

        body.addView(metricWide("إذن التخزين", hasStoragePermission() ? "ممنوح" : "غير ممنوح"));
        body.addView(metricWide("التنظيف التلقائي", "متوقف — لا يوجد حذف دون مراجعة المستخدم"));
        body.addView(metricWide("Root", "غير مستخدم"));
        body.addView(metricWide("الاتصال بالإنترنت", "غير مطلوب"));

        TextView about = text("دربك للتطبيقات\nتصميم وتطوير\nأبوسلطان\n\nالإصدار 1.0.0\ncom.abosultan.darbakappmanager", 18, WHITE, true);
        about.setGravity(Gravity.CENTER);
        about.setPadding(dp(20), dp(22), dp(20), dp(22));
        about.setBackground(round(PANEL, 18, GOLD, 1));
        LinearLayout.LayoutParams lp = marginTop(ViewGroup.LayoutParams.WRAP_CONTENT, 12);
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        body.addView(about, lp);
        content.addView(scroll);
    }

    private void setPageTitle(String title, String subtitle) {
        content.removeAllViews();
        // Header remains global; pages intentionally avoid duplicated title bars to maximize 1024x600 space.
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return l;
    }

    private View infoBanner(String message, int accent) {
        TextView t = text(message, 15, WHITE, false);
        t.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        t.setPadding(dp(16), dp(11), dp(16), dp(11));
        t.setBackground(round(PANEL, 14, accent, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.setMargins(0, 0, 0, dp(10));
        t.setLayoutParams(lp);
        return t;
    }

    private View metricCard(String label, String value, int accent) {
        LinearLayout card = vertical();
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setBackground(round(PANEL, 16, Color.TRANSPARENT, 0));
        card.addView(text(value, 28, accent, true));
        card.addView(text(label, 14, MUTED, false));
        return card;
    }

    private View storageCard() {
        File root = Environment.getExternalStorageDirectory();
        StatFs stat = new StatFs(root.getAbsolutePath());
        long total = stat.getTotalBytes();
        long free = stat.getAvailableBytes();
        TextView t = text("التخزين: متاح " + formatBytes(free) + " من " + formatBytes(total), 17, WHITE, true);
        t.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        t.setPadding(dp(18), dp(12), dp(18), dp(12));
        t.setBackground(round(PANEL, 16, GOLD, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        lp.setMargins(0, dp(12), 0, dp(12));
        t.setLayoutParams(lp);
        return t;
    }

    private View metricWide(String title, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.setBackground(round(PANEL, 14, Color.TRANSPARENT, 0));
        TextView a = text(title, 16, WHITE, true);
        TextView b = text(value, 14, MUTED, false);
        b.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.addView(a, new LinearLayout.LayoutParams(0, dp(44), 1f));
        row.addView(b, new LinearLayout.LayoutParams(0, dp(44), 2f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        lp.setMargins(0, 0, 0, dp(7));
        row.setLayoutParams(lp);
        return row;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return t;
    }

    private Button button(String value, int bg, int fg) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(fg);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setBackground(round(bg, 14, Color.TRANSPARENT, 0));
        return b;
    }

    private GradientDrawable round(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), stroke);
        return d;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(102), 1f);
        lp.setMargins(dp(5), 0, dp(5), 0);
        return lp;
    }

    private LinearLayout.LayoutParams weightedMargin() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(58), 1f);
        lp.setMargins(dp(5), 0, dp(5), 0);
        return lp;
    }

    private LinearLayout.LayoutParams marginTop(int height, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        lp.setMargins(0, dp(topDp), 0, 0);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean hasStoragePermission() {
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean ensureStoragePermission() {
        if (hasStoragePermission()) return true;
        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        toast("امنح إذن التخزين ثم أعد العملية");
        return false;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE) toast(hasStoragePermission() ? "تم منح إذن التخزين" : "لم يتم منح إذن التخزين");
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    private static String formatDate(long value) {
        if (value <= 0) return "-";
        return new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(new Date(value));
    }

    private final class AppAdapter extends BaseAdapter {
        private final List<AppEntry> all;
        private final List<AppEntry> shown = new ArrayList<>();

        AppAdapter(List<AppEntry> items) { all = new ArrayList<>(items); shown.addAll(items); }
        void filter(String q) {
            shown.clear();
            String needle = q == null ? "" : q.trim().toLowerCase(Locale.US);
            for (AppEntry a : all) {
                if (needle.isEmpty() || a.label.toLowerCase(Locale.US).contains(needle) || a.packageName.toLowerCase(Locale.US).contains(needle)) shown.add(a);
            }
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
            row.setPadding(dp(14), dp(7), dp(14), dp(7));
            row.setBackground(round(PANEL, 14, Color.TRANSPARENT, 0));
            ImageView icon = new ImageView(MainActivity.this);
            icon.setImageDrawable(app.icon);
            row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
            LinearLayout texts = vertical();
            texts.setPadding(dp(14), 0, dp(14), 0);
            texts.addView(text(app.label, 17, WHITE, true));
            texts.addView(text(app.packageName, 12, MUTED, false));
            row.addView(texts, new LinearLayout.LayoutParams(0, dp(58), 1f));
            TextView size = text(formatBytes(app.apkBytes), 14, GOLD, true);
            size.setGravity(Gravity.CENTER);
            row.addView(size, new LinearLayout.LayoutParams(dp(110), dp(58)));
            LinearLayout wrapper = new LinearLayout(MainActivity.this);
            wrapper.setPadding(0, dp(3), 0, dp(3));
            wrapper.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));
            return wrapper;
        }
    }

    private final class CleanerAdapter extends BaseAdapter {
        final List<SafeCleaner.LeftoverItem> items;
        CleanerAdapter(List<SafeCleaner.LeftoverItem> items) { this.items = items; }
        int selectedCount() {
            int n = 0;
            for (SafeCleaner.LeftoverItem i : items) if (i.selected) n++;
            return n;
        }
        @Override public int getCount() { return items.size(); }
        @Override public SafeCleaner.LeftoverItem getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            final SafeCleaner.LeftoverItem item = getItem(position);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(6), dp(12), dp(6));
            row.setBackground(round(PANEL, 14, item.confidence == SafeCleaner.CONFIRMED ? SAFE : Color.TRANSPARENT, item.confidence == SafeCleaner.CONFIRMED ? 1 : 0));
            CheckBox check = new CheckBox(MainActivity.this);
            check.setChecked(item.selected);
            check.setOnCheckedChangeListener((buttonView, isChecked) -> item.selected = isChecked);
            row.addView(check, new LinearLayout.LayoutParams(dp(54), dp(54)));
            LinearLayout texts = vertical();
            texts.addView(text(item.packageName, 16, WHITE, true));
            texts.addView(text(item.sourceLabel + " • " + (item.confidence == SafeCleaner.CONFIRMED ? "بقايا مؤكدة" : "مرشح — يحتاج مراجعة"), 13, item.confidence == SafeCleaner.CONFIRMED ? SAFE : GOLD, false));
            row.addView(texts, new LinearLayout.LayoutParams(0, dp(58), 1f));
            TextView size = text(formatBytes(item.bytes), 14, MUTED, true);
            size.setGravity(Gravity.CENTER);
            row.addView(size, new LinearLayout.LayoutParams(dp(110), dp(58)));
            LinearLayout wrapper = new LinearLayout(MainActivity.this);
            wrapper.setPadding(0, dp(3), 0, dp(3));
            wrapper.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));
            return wrapper;
        }
    }
}
