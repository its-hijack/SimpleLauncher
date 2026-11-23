package com.simplehome.tv;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Software update: asks GitHub for the latest SimpleLauncher release, compares its build with the one
 * running, downloads the APK and hands it to Android's installer. Only ever runs when you ask it to.
 */
final class Updater {

    static final String REPO = "its-hijack/SimpleLauncher";
    static final String REPO_URL = "github.com/" + REPO;
    static final String ISSUES_URL = REPO_URL + "/issues";
    private static final String LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";

    static final String PREF_CHECKED = "update_last_checked";
    static final String PREF_LATEST = "update_latest_build";
    static final String PREF_RELAUNCH = "update_relaunch";

    /** What GitHub has. */
    static final class Release {
        String build;        // e.g. 231126-2.9 (the tag)
        String version;      // e.g. V1, from the release name when it has one
        String notes;        // tidied release notes
        String apkUrl;
        long apkSize;
        String published;    // e.g. 24 Nov 2026
    }

    /** A failure with words fit for the update screen. */
    static final class UpdateException extends Exception {
        final String title;

        UpdateException(String title, String message) {
            super(message);
            this.title = title;
        }
    }

    interface Progress {
        void onProgress(long done, long total);

        boolean cancelled();
    }

    private Updater() {
    }

    // ------------------------------------------------------------------ checking

    /** Fetches the latest release from GitHub. Call off the main thread. */
    static Release latest(Context c) throws UpdateException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(LATEST).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "SimpleLauncher/" + BuildConfig.BUILD_TAG);
            int code = conn.getResponseCode();
            if (code == 404) {
                throw new UpdateException("No release on GitHub yet",
                        "There isn't a published SimpleLauncher release to check against. Nothing on your TV was changed.");
            }
            if (code == 403 || code == 429) {
                throw new UpdateException("GitHub asked us to wait",
                        "GitHub allows 60 checks an hour from one network. Try again a little later. Nothing on your TV was changed.");
            }
            if (code != 200) throw new IOException("HTTP " + code);

            JSONObject json = new JSONObject(readAll(conn.getInputStream()));
            Release r = new Release();
            r.build = json.optString("tag_name", "").trim();
            String name = json.optString("name", "");
            Matcher v = Pattern.compile("\\b(V\\d+)\\b").matcher(name);
            r.version = v.find() ? v.group(1) : BuildConfig.RELEASE_VERSION;
            r.notes = tidyNotes(json.optString("body", ""));
            r.published = niceDate(json.optString("published_at", ""));
            JSONArray assets = json.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if (a.optString("name", "").toLowerCase(Locale.ROOT).endsWith(".apk")) {
                        r.apkUrl = a.optString("browser_download_url", null);
                        r.apkSize = a.optLong("size", 0);
                        break;
                    }
                }
            }
            if (parse(r.build) == null) {
                throw new UpdateException("Couldn't read the release",
                        "The latest release on GitHub is tagged \"" + r.build + "\", which isn't a build number like 231126-2.9.");
            }
            Prefs.get(c).edit()
                    .putLong(PREF_CHECKED, System.currentTimeMillis())
                    .putString(PREF_LATEST, r.build)
                    .apply();
            return r;
        } catch (UpdateException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("GitHub couldn't be reached",
                    "Check your TV is online, then try again. Nothing on your TV was changed.");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Is GitHub's build newer than the one running? */
    static boolean isNewer(String github, String running) {
        int[] a = parse(github), b = parse(running);
        if (a == null || b == null) return false;
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    /**
     * Turns "231126-2.9" (day, month, year, then the build) into numbers that sort properly:
     * {26, 11, 23, 2, 9}. Returns null for anything else.
     */
    static int[] parse(String build) {
        if (build == null) return null;
        Matcher m = Pattern.compile("^v?(\\d{2})(\\d{2})(\\d{2})-(\\d+(?:\\.\\d+)*)$", Pattern.CASE_INSENSITIVE)
                .matcher(build.trim());
        if (!m.matches()) return null;
        String[] parts = m.group(4).split("\\.");
        int[] out = new int[3 + parts.length];
        out[0] = Integer.parseInt(m.group(3));   // year
        out[1] = Integer.parseInt(m.group(2));   // month
        out[2] = Integer.parseInt(m.group(1));   // day
        for (int i = 0; i < parts.length; i++) out[3 + i] = Integer.parseInt(parts[i]);
        return out;
    }

    /** "Last checked" wording for Settings. */
    static String lastChecked(Context c) {
        long t = Prefs.get(c).getLong(PREF_CHECKED, 0);
        if (t == 0) return "Last checked: never";
        long mins = (System.currentTimeMillis() - t) / 60000;
        if (mins < 1) return "Last checked: just now";
        if (mins < 60) return "Last checked: " + mins + (mins == 1 ? " minute ago" : " minutes ago");
        long hours = mins / 60;
        if (hours < 24) return "Last checked: " + hours + (hours == 1 ? " hour ago" : " hours ago");
        long days = hours / 24;
        return "Last checked: " + days + (days == 1 ? " day ago" : " days ago");
    }

    /** The newer build the last check found, if it's still newer than this one. */
    static String knownUpdate(Context c) {
        String latest = Prefs.get(c).getString(PREF_LATEST, null);
        return latest != null && isNewer(latest, BuildConfig.BUILD_TAG) ? latest : null;
    }

    static String size(long bytes) {
        if (bytes <= 0) return null;
        if (bytes < 1024 * 1024) return Math.max(1, Math.round(bytes / 1024.0)) + " KB";
        return String.format(Locale.UK, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // ------------------------------------------------------------------ permission

    /** Has this TV allowed SimpleLauncher to install updates? */
    static boolean canInstall(Context c) {
        if (Build.VERSION.SDK_INT >= 26) return c.getPackageManager().canRequestPackageInstalls();
        try {
            return Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS, 0) == 1;
        } catch (Exception e) {
            return true;   // can't tell: let Android's installer decide
        }
    }

    /** Opens the "Install unknown apps" setting, as close to SimpleLauncher's own switch as this TV allows. */
    static boolean openInstallSetting(Context c) {
        HomeButtonService.allowAmazonHome();   // Fire TV shows its settings inside Amazon's home screen
        if (Build.VERSION.SDK_INT >= 26) {
            if (Apps.tryStart(c, new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return true;
            if (Apps.tryStart(c, new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return true;
        }
        if (Apps.tryStart(c, new Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return true;
        if (Apps.tryStart(c, new Intent(Settings.ACTION_SECURITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return true;
        return Apps.tryStart(c, new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    // ------------------------------------------------------------------ download and install

    /**
     * Downloads the release straight into an Android install session, then asks Android to install it.
     * Android shows its own "Install" confirmation; {@link InstallReceiver} hears how it went.
     * Returns false if cancelled. Call off the main thread.
     */
    static boolean downloadAndInstall(Context c, Release r, Progress progress) throws UpdateException {
        if (r.apkUrl == null) {
            throw new UpdateException("No app file in this release",
                    "Release " + r.build + " on GitHub doesn't have an APK attached.");
        }
        PackageInstaller installer = c.getPackageManager().getPackageInstaller();
        PackageInstaller.Session session = null;
        int sessionId = -1;
        HttpURLConnection conn = null;
        try {
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(c.getPackageName());
            if (r.apkSize > 0) params.setSize(r.apkSize);
            sessionId = installer.createSession(params);
            session = installer.openSession(sessionId);

            conn = (HttpURLConnection) new URL(r.apkUrl).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", "SimpleLauncher/" + BuildConfig.BUILD_TAG);
            if (conn.getResponseCode() != 200) throw new IOException("HTTP " + conn.getResponseCode());
            long total = conn.getContentLength() > 0 ? conn.getContentLength() : r.apkSize;

            long done = 0;
            try (InputStream in = conn.getInputStream();
                 OutputStream out = session.openWrite("SimpleLauncher.apk", 0, total > 0 ? total : -1)) {
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (progress.cancelled()) {
                        session.abandon();
                        return false;
                    }
                    out.write(buf, 0, n);
                    done += n;
                    progress.onProgress(done, total);
                }
                session.fsync(out);
            }
            if (total > 0 && done != total) throw new IOException("Short download");
            if (progress.cancelled()) {
                session.abandon();
                return false;
            }

            Intent status = new Intent(c, InstallReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= 0x02000000;   // FLAG_MUTABLE: the installer adds its result
            PendingIntent pi = PendingIntent.getBroadcast(c, sessionId, status, flags);
            // If Android restarts SimpleLauncher on the new build, open it again straight away.
            Prefs.get(c).edit().putBoolean(PREF_RELAUNCH, true).commit();
            session.commit(pi.getIntentSender());
            session.close();
            session = null;
            return true;
        } catch (Exception e) {
            if (session != null) {
                try {
                    session.abandon();
                } catch (Exception ignored) {
                }
            }
            throw new UpdateException("The download didn't finish",
                    "Check your TV is online, then try again. Nothing on your TV was changed.");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String readAll(InputStream in) throws IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    /** GitHub's notes are Markdown: keep the words, drop the symbols and links. */
    static String tidyNotes(String body) {
        StringBuilder out = new StringBuilder();
        for (String raw : body.replace("\r", "").split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.contains("Full Changelog") || line.startsWith("<!--")) continue;
            line = line.replaceAll("^#+\\s*", "");
            line = line.replaceAll("^[*-]\\s+", "• ");
            line = line.replaceAll("\\s+by @\\S+ in https?://\\S+$", "");
            line = line.replaceAll("\\[([^\\]]+)]\\([^)]+\\)", "$1");
            line = line.replace("**", "").replace("`", "");
            if (line.equalsIgnoreCase("What's Changed")) continue;
            if (out.length() > 0) out.append('\n');
            out.append(line);
        }
        return out.length() > 0 ? out.toString() : null;
    }

    private static String niceDate(String iso) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = in.parse(iso);
            return new SimpleDateFormat("d MMM yyyy", Locale.UK).format(d);
        } catch (Exception e) {
            return null;
        }
    }
}
