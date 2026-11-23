package com.simplehome.tv;

import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;

import java.net.Inet4Address;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Facts about the Fire TV for the Information page. Every lookup falls back to "Unknown". */
final class DeviceInfo {

    private static final String UNKNOWN = "Unknown";

    private static final Map<String, String> MODELS = new HashMap<>();

    static {
        MODELS.put("AFTSS", "Fire TV Stick HD / Lite");
        MODELS.put("AFTSSS", "Fire TV Stick (3rd gen)");
        MODELS.put("AFTMM", "Fire TV Stick 4K (1st gen)");
        MODELS.put("AFTKA", "Fire TV Stick 4K Max (1st gen)");
        MODELS.put("AFTKM", "Fire TV Stick 4K (2nd gen)");
        MODELS.put("AFTKRT", "Fire TV Stick 4K Max (2nd gen)");
        MODELS.put("AFTR", "Fire TV Cube (2nd gen)");
        MODELS.put("AFTGAZL", "Fire TV Cube (3rd gen)");
    }

    private DeviceInfo() {
    }

    static String deviceName(Context c) {
        String name = null;
        try {
            name = Settings.Global.getString(c.getContentResolver(), "device_name");
        } catch (RuntimeException ignored) {
        }
        return name != null && !name.isEmpty() ? name : Build.MODEL;
    }

    static String model() {
        String friendly = MODELS.get(Build.MODEL);
        return friendly != null ? friendly + " (" + Build.MODEL + ")" : Build.MODEL;
    }

    /** e.g. "7.7.1.7 (PS7717/5746)". */
    static String fireOs() {
        String name = systemProperty("ro.build.version.name");
        if (name != null && !name.isEmpty()) return name.replace("Fire OS ", "");
        return Build.DISPLAY;
    }

    static String upTime() {
        long mins = SystemClock.elapsedRealtime() / 60000;
        long days = mins / (60 * 24);
        long hours = (mins / 60) % 24;
        long m = mins % 60;
        if (days > 0) return days + (days == 1 ? " day, " : " days, ") + hours + (hours == 1 ? " hour" : " hours");
        if (hours > 0) return hours + (hours == 1 ? " hour, " : " hours, ") + m + " min";
        return m + " min";
    }

    static String ipAddress(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm.getActiveNetwork();
            if (net == null) return "Not connected";
            LinkProperties props = cm.getLinkProperties(net);
            if (props == null) return UNKNOWN;
            for (LinkAddress la : props.getLinkAddresses()) {
                if (la.getAddress() instanceof Inet4Address) return la.getAddress().getHostAddress();
            }
        } catch (RuntimeException ignored) {
        }
        return UNKNOWN;
    }

    /** "Wi-Fi · Signal: Good", "Ethernet" or "Not connected". */
    static String connection(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm.getActiveNetwork();
            NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
            if (caps == null) return "Not connected";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                if (info == null) return "Wi-Fi";
                String[] words = {"Very weak", "Weak", "Fair", "Good", "Excellent"};
                int level = WifiManager.calculateSignalLevel(info.getRssi(), words.length);
                return "Wi-Fi · Signal: " + words[Math.max(0, Math.min(words.length - 1, level))];
            }
            return "Connected";
        } catch (RuntimeException e) {
            return UNKNOWN;
        }
    }

    static String adb(Context c) {
        try {
            return Settings.Global.getInt(c.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1 ? "On" : "Off";
        } catch (RuntimeException e) {
            return UNKNOWN;
        }
    }

    static String storage() {
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            return gb(fs.getAvailableBytes()) + " free of " + gb(fs.getTotalBytes());
        } catch (RuntimeException e) {
            return UNKNOWN;
        }
    }

    static String memory(Context c) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return gb(mi.totalMem - mi.availMem) + " of " + gb(mi.totalMem);
        } catch (RuntimeException e) {
            return UNKNOWN;
        }
    }

    static String version(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return UNKNOWN;
        }
    }

    private static String gb(long bytes) {
        return String.format(Locale.UK, "%.1f GB", bytes / 1_000_000_000.0);
    }

    private static String systemProperty(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (String) sp.getMethod("get", String.class).invoke(null, key);
        } catch (Exception e) {
            return null;
        }
    }
}
