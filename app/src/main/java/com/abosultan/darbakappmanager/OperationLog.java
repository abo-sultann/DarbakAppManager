package com.abosultan.darbakappmanager;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class OperationLog {
    private static final String FILE_NAME = "operations.log";
    private OperationLog() {}

    public static synchronized void add(Context context, String message) {
        try {
            File f = new File(context.getFilesDir(), FILE_NAME);
            FileWriter writer = new FileWriter(f, true);
            String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            writer.write(stamp + "\t" + message.replace('\n', ' ') + "\n");
            writer.close();
            trim(f, 120);
        } catch (Exception ignored) {}
    }

    public static synchronized List<String> recent(Context context, int max) {
        List<String> lines = new ArrayList<>();
        File f = new File(context.getFilesDir(), FILE_NAME);
        if (!f.isFile()) return lines;
        try {
            BufferedReader br = new BufferedReader(new FileReader(f));
            String line;
            while ((line = br.readLine()) != null) lines.add(line);
            br.close();
        } catch (Exception ignored) {}
        Collections.reverse(lines);
        if (lines.size() > max) return new ArrayList<>(lines.subList(0, max));
        return lines;
    }

    private static void trim(File f, int keep) {
        try {
            List<String> lines = new ArrayList<>();
            BufferedReader br = new BufferedReader(new FileReader(f));
            String line;
            while ((line = br.readLine()) != null) lines.add(line);
            br.close();
            if (lines.size() <= keep) return;
            FileWriter fw = new FileWriter(f, false);
            for (int i = lines.size() - keep; i < lines.size(); i++) fw.write(lines.get(i) + "\n");
            fw.close();
        } catch (Exception ignored) {}
    }
}
