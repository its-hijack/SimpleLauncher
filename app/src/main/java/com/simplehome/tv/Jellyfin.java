package com.simplehome.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Jellyfin: finds servers on your network, signs in (Quick Connect, or username and password),
 * and fetches what you're part-way through for the Continue Watching row.
 */
final class Jellyfin {

    static final String TV_APP = "org.jellyfin.androidtv";
    static final String PHONE_APP = "org.jellyfin.mobile";
    private static final int MAX_ITEMS = 12;

    // Saved sign-in
    private static final String SERVER = "jf_server";            // e.g. http://192.168.1.10:8096
    private static final String SERVER_NAME = "jf_server_name";
    private static final String USER_ID = "jf_user_id";
    private static final String USER = "jf_user";
    private static final String TOKEN = "jf_token";
    private static final String DEVICE_ID = "jf_device_id";
    private static final String ITEMS = "jf_items";

    static final class Server {
        String name;
        String address;
        String id;
        String version;
    }

    static final class QuickConnect {
        String code;
        String secret;
    }

    static final class Item {
        String id;
        String title;
        String meta;
        String imageUrl;
        int progress;
        long lastViewed;
    }

    static final class Result {
        final List<Item> items = new ArrayList<>();
        String error;
    }

    static List<Item> cache;
    static long cacheTime;

    private Jellyfin() {
    }

    // ------------------------------------------------------------------ the app and the saved sign-in

    static boolean installed(Context c) {
        return Mode.isInstalled(c, TV_APP) || Mode.isInstalled(c, PHONE_APP);
    }

    static String appVersion(Context c) {
        for (String pkg : new String[]{TV_APP, PHONE_APP}) {
            try {
                String v = c.getPackageManager().getPackageInfo(pkg, 0).versionName;
                return v != null ? v : "Installed";
            } catch (Exception ignored) {
            }
        }
        return "Not installed";
    }

    static boolean signedIn(Context c) {
        return token(c) != null && server(c) != null;
    }

    static String server(Context c) {
        return Prefs.get(c).getString(SERVER, null);
    }

    static String serverName(Context c) {
        return Prefs.get(c).getString(SERVER_NAME, null);
    }

    static String user(Context c) {
        return Prefs.get(c).getString(USER, null);
    }

    private static String token(Context c) {
        return Prefs.get(c).getString(TOKEN, null);
    }

    private static String userId(Context c) {
        return Prefs.get(c).getString(USER_ID, null);
    }

    private static String deviceId(Context c) {
        String id = Prefs.get(c).getString(DEVICE_ID, null);
        if (id == null) {
            id = "simplelauncher-" + UUID.randomUUID();
            Prefs.get(c).edit().putString(DEVICE_ID, id).apply();
        }
        return id;
    }

    static void signOut(Context c) {
        final String server = server(c), token = token(c);
        if (server != null && token != null) {
            final Context app = c.getApplicationContext();
            new Thread(() -> {
                try {
                    request(app, "POST", server + "/Sessions/Logout", token, null, 4000);
                } catch (Exception ignored) {
                }
            }).start();
        }
        Prefs.get(c).edit().remove(SERVER).remove(SERVER_NAME).remove(USER_ID).remove(USER)
                .remove(TOKEN).remove(ITEMS).apply();
        clearCache();
    }

    static void clearCache() {
        cache = null;
        cacheTime = 0;
    }

    // ------------------------------------------------------------------ finding servers

    /** Asks the network "who is JellyfinServer?" and collects the answers for a couple of seconds. */
    static List<Server> discover(int waitMs) {
        List<Server> found = new ArrayList<>();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            byte[] ask = "who is JellyfinServer?".getBytes("UTF-8");
            socket.send(new DatagramPacket(ask, ask.length, InetAddress.getByName("255.255.255.255"), 7359));
            long end = System.currentTimeMillis() + waitMs;
            byte[] buf = new byte[4096];
            while (System.currentTimeMillis() < end) {
                socket.setSoTimeout((int) Math.max(50, end - System.currentTimeMillis()));
                DatagramPacket reply = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(reply);
                } catch (SocketTimeoutException e) {
                    break;
                }
                try {
                    JSONObject o = new JSONObject(new String(reply.getData(), 0, reply.getLength(), "UTF-8"));
                    Server s = new Server();
                    s.name = o.optString("Name", "Jellyfin");
                    s.address = trimSlash(o.optString("Address", ""));
                    s.id = o.optString("Id", "");
                    if (s.address.isEmpty()) continue;
                    boolean dupe = false;
                    for (Server f : found) dupe |= f.id.equals(s.id) || f.address.equals(s.address);
                    if (!dupe) found.add(s);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return found;
    }

    /** Turns what someone typed ("192.168.1.10", "jelly.local:8096/") into a full address. */
    static String normalise(String typed) {
        String s = typed.trim();
        if (s.isEmpty()) return null;
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        s = trimSlash(s);
        String hostPart = s.substring(s.indexOf("://") + 3);
        if (!hostPart.contains(":") && !hostPart.contains("/") && s.startsWith("http://")) s += ":8096";
        return s;
    }

    /** Checks an address really is a Jellyfin server (and gets its name). Off the main thread. */
    static Server check(Context c, String address) throws IOException {
        try {
            JSONObject o = new JSONObject(request(c, "GET", address + "/System/Info/Public", null, null, 5000));
            Server s = new Server();
            s.address = address;
            s.name = o.optString("ServerName", "Jellyfin");
            s.id = o.optString("Id", "");
            s.version = o.optString("Version", "");
            return s;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Not a Jellyfin server");
        }
    }

    // ------------------------------------------------------------------ signing in

    static boolean quickConnectEnabled(Context c, String address) {
        try {
            return "true".equalsIgnoreCase(request(c, "GET", address + "/QuickConnect/Enabled", null, null, 5000).trim());
        } catch (Exception e) {
            return false;
        }
    }

    static QuickConnect startQuickConnect(Context c, String address) throws Exception {
        String body;
        try {
            body = request(c, "POST", address + "/QuickConnect/Initiate", null, "", 6000);
        } catch (IOException e) {
            body = request(c, "GET", address + "/QuickConnect/Initiate", null, null, 6000);   // older servers
        }
        JSONObject o = new JSONObject(body);
        QuickConnect q = new QuickConnect();
        q.code = o.getString("Code");
        q.secret = o.getString("Secret");
        return q;
    }

    /** True once you've approved the code in Jellyfin. */
    static boolean quickConnectApproved(Context c, String address, QuickConnect q) {
        try {
            JSONObject o = new JSONObject(request(c, "GET", address + "/QuickConnect/Connect?secret="
                    + URLEncoder.encode(q.secret, "UTF-8"), null, null, 6000));
            return o.optBoolean("Authenticated", false);
        } catch (Exception e) {
            return false;
        }
    }

    static void finishQuickConnect(Context c, Server server, QuickConnect q) throws Exception {
        JSONObject body = new JSONObject().put("Secret", q.secret);
        save(c, server, new JSONObject(request(c, "POST", server.address + "/Users/AuthenticateWithQuickConnect",
                null, body.toString(), 8000)));
    }

    static void signInWithPassword(Context c, Server server, String user, String password) throws Exception {
        JSONObject body = new JSONObject().put("Username", user).put("Pw", password);
        save(c, server, new JSONObject(request(c, "POST", server.address + "/Users/AuthenticateByName",
                null, body.toString(), 8000)));
    }

    private static void save(Context c, Server server, JSONObject auth) throws Exception {
        JSONObject user = auth.getJSONObject("User");
        Prefs.get(c).edit()
                .putString(SERVER, server.address)
                .putString(SERVER_NAME, server.name)
                .putString(TOKEN, auth.getString("AccessToken"))
                .putString(USER_ID, user.getString("Id"))
                .putString(USER, user.optString("Name", null))
                .remove(ITEMS)
                .apply();
        clearCache();
    }

    // ------------------------------------------------------------------ Continue Watching

    static Result continueWatching(Context c) {
        Result result = new Result();
        String server = server(c), token = token(c), uid = userId(c);
        if (server == null || token == null || uid == null) {
            result.error = "Sign in to Jellyfin in Settings › External Services.";
            return result;
        }
        String fields = "&fields=ParentId&enableImageTypes=Primary,Thumb,Backdrop&mediaTypes=Video&limit=" + MAX_ITEMS;
        String body;
        try {
            try {
                body = request(c, "GET", server + "/UserItems/Resume?userId=" + uid + fields, token, null, 8000);
            } catch (IOException e) {
                body = request(c, "GET", server + "/Users/" + uid + "/Items/Resume?" + fields.substring(1), token, null, 8000);
            }
        } catch (IOException e) {
            result.error = e.getMessage() != null && e.getMessage().contains("401")
                    ? "Jellyfin signed you out. Sign in again in Settings › External Services."
                    : "Couldn't reach your Jellyfin server.";
            return result;
        }
        try {
            JSONArray arr = new JSONObject(body).optJSONArray("Items");
            for (int i = 0; arr != null && i < arr.length(); i++) {
                Item it = toItem(arr.getJSONObject(i), server, token);
                if (it != null) result.items.add(it);
            }
        } catch (Exception e) {
            result.error = "Jellyfin sent something unexpected.";
        }
        Collections.sort(result.items, (a, b) -> Long.compare(b.lastViewed, a.lastViewed));
        saveItems(c, result.items);
        return result;
    }

    static Item toItem(JSONObject m, String server, String token) throws Exception {
        Item it = new Item();
        it.id = m.optString("Id", "");
        if (it.id.isEmpty()) return null;
        JSONObject ud = m.optJSONObject("UserData");
        long runtime = m.optLong("RunTimeTicks", 0);
        long position = ud != null ? ud.optLong("PlaybackPositionTicks", 0) : 0;
        double pct = ud != null ? ud.optDouble("PlayedPercentage", Double.NaN) : Double.NaN;
        it.progress = !Double.isNaN(pct) ? (int) Math.round(pct) : runtime > 0 ? (int) (100 * position / runtime) : 0;
        it.lastViewed = ud != null ? parseDate(ud.optString("LastPlayedDate", "")) : 0;
        String left = runtime > position && runtime > 0 ? timeLeft((runtime - position) / 10_000) : "";

        String imageId, imageType;
        JSONObject tags = m.optJSONObject("ImageTags");
        if ("Episode".equals(m.optString("Type"))) {
            it.title = m.optString("SeriesName", m.optString("Name", ""));
            String se = "S" + m.optInt("ParentIndexNumber", 0) + " · E" + m.optInt("IndexNumber", 0);
            it.meta = left.isEmpty() ? se : se + " · " + left;
            if (!m.optString("ParentThumbItemId", "").isEmpty()) {
                imageId = m.optString("ParentThumbItemId");
                imageType = "Thumb";
            } else if (tags != null && tags.has("Primary")) {
                imageId = it.id;           // the episode's own still
                imageType = "Primary";
            } else if (!m.optString("ParentBackdropItemId", "").isEmpty()) {
                imageId = m.optString("ParentBackdropItemId");
                imageType = "Backdrop";
            } else {
                imageId = m.optString("SeriesId", it.id);
                imageType = "Primary";
            }
        } else {
            it.title = m.optString("Name", "");
            it.meta = left;
            JSONArray backdrops = m.optJSONArray("BackdropImageTags");
            if (tags != null && tags.has("Thumb")) {
                imageType = "Thumb";
            } else if (backdrops != null && backdrops.length() > 0) {
                imageType = "Backdrop";
            } else {
                imageType = "Primary";
            }
            imageId = it.id;
        }
        it.imageUrl = server + "/Items/" + imageId + "/Images/" + imageType
                + "?fillWidth=520&fillHeight=292&quality=85&api_key=" + URLEncoder.encode(token, "UTF-8");
        return it;
    }

    /** Opens the Jellyfin app. Its TV app can't be opened straight to one show, but that show is first there. */
    static void open(Context c, Item item) {
        if (Apps.launchPackage(c, TV_APP) || Apps.launchPackage(c, PHONE_APP)) return;
        Apps.toast(c, "Couldn't open Jellyfin");
    }

    // ------------------------------------------------------------------ saved row (shows instantly on start)

    private static void saveItems(Context c, List<Item> items) {
        try {
            JSONArray arr = new JSONArray();
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("id", it.id);
                o.put("title", it.title);
                o.put("meta", it.meta);
                o.put("image", it.imageUrl);
                o.put("progress", it.progress);
                o.put("lastViewed", it.lastViewed);
                arr.put(o);
            }
            Prefs.get(c).edit().putString(ITEMS, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static List<Item> loadSaved(Context c) {
        String s = Prefs.get(c).getString(ITEMS, null);
        if (s == null) return null;
        try {
            JSONArray arr = new JSONArray(s);
            List<Item> items = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Item it = new Item();
                it.id = o.optString("id");
                it.title = o.optString("title");
                it.meta = o.optString("meta");
                it.imageUrl = o.optString("image", null);
                it.progress = o.optInt("progress");
                it.lastViewed = o.optLong("lastViewed");
                items.add(it);
            }
            return items;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ HTTP

    private static String authHeader(Context c, String token) {
        String h = "MediaBrowser Client=\"SimpleLauncher\", Device=\"" + (Mode.fireTv(c) ? "Fire TV" : "Android TV")
                + "\", DeviceId=\"" + deviceId(c) + "\", Version=\"" + BuildConfig.BUILD_TAG + "\"";
        return token != null ? h + ", Token=\"" + token + "\"" : h;
    }

    private static String request(Context c, String method, String url, String token, String body, int timeoutMs) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setConnectTimeout(timeoutMs);
            con.setReadTimeout(timeoutMs * 2);
            con.setRequestMethod(method);
            con.setRequestProperty("Accept", "application/json");
            String auth = authHeader(c, token);
            con.setRequestProperty("Authorization", auth);
            con.setRequestProperty("X-Emby-Authorization", auth);
            if (body != null) {
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/json");
                byte[] bytes = body.getBytes("UTF-8");
                try (OutputStream out = con.getOutputStream()) {
                    out.write(bytes);
                }
            }
            int code = con.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
            if (code == 204) return "";
            try (InputStream in = con.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString("UTF-8");
            }
        } finally {
            con.disconnect();
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String trimSlash(String s) {
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** Jellyfin dates look like 2026-11-23T18:42:10.1234567Z. */
    static long parseDate(String s) {
        if (s == null || s.length() < 19) return 0;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(s.substring(0, 19)).getTime() / 1000;   // seconds, like Plex's lastViewedAt
        } catch (Exception e) {
            return 0;
        }
    }

    private static String timeLeft(long ms) {
        long mins = Math.max(1, Math.round(ms / 60000.0));
        if (mins < 60) return mins + " min left";
        long h = mins / 60;
        long m = mins % 60;
        return m == 0 ? h + " hr left" : h + " hr " + String.format(Locale.UK, "%02d", m) + " min left";
    }
}
