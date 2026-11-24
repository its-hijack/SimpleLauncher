package com.simplehome.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import java.util.List;

/**
 * Fire TV mode, Google TV mode or generic mode.
 *
 * Fire TV mode: HomeCatch (the Home-button catcher), the "Amazon Home" button, Fire OS details.
 * Google TV mode (Google TV / Android TV, which ignore the home app setting): HomeCatch, switched
 * on in Accessibility, and a "Google Home" button.
 * Generic mode (any other Android device): no catcher, "Return home" opens the
 * device's original home screen, and Settings offers Android's home-app picker.
 *
 * Detected automatically; Settings → Information → Choose mode can override it.
 */
final class Mode {

    static final int AUTO = 0;
    static final int FIRE_TV = 1;
    static final int GENERIC = 2;
    static final int GOOGLE_TV = 3;   // added later, so the saved numbers above stay the same

    private Mode() {
    }

    /** True on Amazon Fire TV hardware. */
    static boolean isFireTvDevice(Context c) {
        PackageManager pm = c.getPackageManager();
        try {
            if (pm.hasSystemFeature("amazon.hardware.fire_tv")) return true;
        } catch (RuntimeException ignored) {
        }
        if ("Amazon".equalsIgnoreCase(Build.MANUFACTURER) && Build.MODEL != null && Build.MODEL.startsWith("AFT")) return true;
        return isInstalled(c, Apps.AMAZON_LAUNCHER);
    }

    static int override(Context c) {
        return Prefs.get(c).getInt(Prefs.MODE_OVERRIDE, AUTO);
    }

    static void setOverride(Context c, int mode) {
        Prefs.get(c).edit().putInt(Prefs.MODE_OVERRIDE, mode).apply();
    }

    /** The mode SimpleLauncher is actually running in. */
    static boolean fireTv(Context c) {
        int o = override(c);
        if (o == FIRE_TV) return true;
        if (o != AUTO) return false;
        return isFireTvDevice(c);
    }

    /** True on a Google TV or Android TV with Google's own home screen installed. */
    static boolean isGoogleTvDevice(Context c) {
        for (String pkg : Apps.GOOGLE_LAUNCHERS) if (isInstalled(c, pkg)) return true;
        return false;
    }

    /** True when running in Google TV mode. */
    static boolean googleTv(Context c) {
        int o = override(c);
        if (o == GOOGLE_TV) return true;
        if (o != AUTO) return false;
        return !isFireTvDevice(c) && isGoogleTvDevice(c);
    }

    static String name(int mode) {
        switch (mode) {
            case FIRE_TV: return "Fire TV mode";
            case GOOGLE_TV: return "Google TV mode";
            default: return "Generic mode";
        }
    }

    /** The mode automatic detection picks on this device. */
    static int detectedMode(Context c) {
        if (isFireTvDevice(c)) return FIRE_TV;
        if (isGoogleTvDevice(c)) return GOOGLE_TV;
        return GENERIC;
    }

    static String name(Context c) {
        return fireTv(c) ? name(FIRE_TV) : googleTv(c) ? name(GOOGLE_TV) : name(GENERIC);
    }

    /** What the device looks like, e.g. "Fire TV Stick HD / Lite (AFTSS)" or "Samsung SM-X200". */
    static String detected(Context c) {
        if (isFireTvDevice(c)) return DeviceInfo.model();
        String maker = Build.MANUFACTURER != null ? Build.MANUFACTURER : "";
        String model = Build.MODEL != null ? Build.MODEL : "";
        if (!maker.isEmpty()) maker = Character.toUpperCase(maker.charAt(0)) + maker.substring(1);
        return (maker + " " + model).trim();
    }

    /** The label for the top-right button on the home screen. */
    static String homeButtonLabel(Context c) {
        return fireTv(c) ? "Amazon Home" : googleTv(c) ? "Google Home" : "Return home";
    }

    static boolean isInstalled(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ generic mode: home apps

    /** True if Android currently uses SimpleLauncher as the home screen. */
    static boolean isDefaultHome(Context c) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = c.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && c.getPackageName().equals(ri.activityInfo.packageName);
    }

    /** Opens Android's "choose your home app" screen. */
    static void openHomeAppPicker(Context c) {
        Intent picker = new Intent(android.provider.Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Apps.tryStart(c, picker)) return;
        if (!Apps.tryStart(c, new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            Apps.toast(c, "Couldn't open the home app settings");
        }
    }

    /** "Return home": opens the device's original home screen (any home app that isn't SimpleLauncher). */
    static void openOriginalHome(Context c) {
        if (googleTv(c)) {   // straight to Google's home screen, in case another launcher is installed
            for (String pkg : Apps.GOOGLE_LAUNCHERS) {
                if (!isInstalled(c, pkg)) continue;
                Intent g = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (Apps.tryStart(c, g)) return;
            }
        }
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> homes = c.getPackageManager().queryIntentActivities(home, 0);
        for (ResolveInfo ri : homes) {
            if (ri.activityInfo == null || c.getPackageName().equals(ri.activityInfo.packageName)) continue;
            Intent open = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            open.setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Apps.tryStart(c, open)) return;
        }
        Apps.toast(c, "No other home screen found on this device");
    }
}
