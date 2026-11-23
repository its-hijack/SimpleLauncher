package com.simplehome.tv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finding, opening and describing installed apps. */
final class Apps {

    static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    static final String FIRE_SETTINGS = "com.amazon.tv.settings.v2";

    /** Never shown as tiles. */
    private static final Set<String> EXCLUDED = new HashSet<>(Arrays.asList(
            AMAZON_LAUNCHER,
            "com.amazon.firehomestarter"
    ));

    static final class Entry {
        String pkg;
        String activity;
        boolean leanback;
        String label;
        Drawable banner;
        Drawable icon;
    }

    private Apps() {
    }

    /** Every app you can open, A–Z. Slow-ish: call off the main thread. */
    static List<Entry> load(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, Entry> found = new LinkedHashMap<>();
        collect(c, pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true, found);
        collect(c, pm, Intent.CATEGORY_LAUNCHER, false, found);
        List<Entry> list = new ArrayList<>(found.values());
        Collections.sort(list, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return list;
    }

    private static void collect(Context c, PackageManager pm, String category, boolean leanback, Map<String, Entry> out) {
        List<ResolveInfo> results;
        try {
            results = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0);
        } catch (RuntimeException e) {
            return;
        }
        for (ResolveInfo ri : results) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null) continue;
            String pkg = ai.packageName;
            if (pkg.equals(c.getPackageName()) || EXCLUDED.contains(pkg) || out.containsKey(pkg)) continue;
            Entry e = new Entry();
            e.pkg = pkg;
            e.activity = ai.name;
            e.leanback = leanback;
            CharSequence label = ri.loadLabel(pm);
            e.label = label != null ? label.toString() : pkg;
            try {
                e.banner = ai.loadBanner(pm);
                if (e.banner == null) e.banner = ai.applicationInfo.loadBanner(pm);
            } catch (RuntimeException ignored) {
            }
            if (e.banner == null) e.icon = ri.loadIcon(pm);
            out.put(pkg, e);
        }
    }

    static void launch(Context c, Entry e) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(e.leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER);
        intent.setClassName(e.pkg, e.activity);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (tryStart(c, intent)) return;
        if (!launchPackage(c, e.pkg)) toast(c, "Couldn't open " + e.label);
    }

    static boolean launchPackage(Context c, String pkg) {
        Intent intent = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) intent = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) return false;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return tryStart(c, intent);
    }

    /** The "Amazon Home" button: Amazon's normal home screen (with its Settings, Appstore and so on). */
    static void openAmazonHome(Context c) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(AMAZON_LAUNCHER);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (tryStart(c, home)) return;
        if (launchPackage(c, AMAZON_LAUNCHER)) return;
        openFireSettings(c);
    }

    /** Fire TV's settings. */
    static void openFireSettings(Context c) {
        if (launchPackage(c, FIRE_SETTINGS)) return;
        if (!tryStart(c, new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            toast(c, "Couldn't open Fire TV settings");
        }
    }

    static void openAppInfo(Context c, String pkg) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!tryStart(c, intent)) toast(c, "Couldn't open app info");
    }

    static boolean tryStart(Context c, Intent intent) {
        try {
            c.startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static void toast(Context c, String message) {
        Toast.makeText(c, message, Toast.LENGTH_SHORT).show();
    }
}
