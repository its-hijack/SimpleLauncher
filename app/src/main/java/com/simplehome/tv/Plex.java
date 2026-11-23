package com.simplehome.tv;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Plex: sign in with a plex.tv/link code, find your servers, read Continue Watching,
 * and open an item in the Plex app.
 */
final class Plex {

    static final String PLEX_APP = "com.plexapp.android";
    private static final String PRODUCT = "SimpleLauncher";
    private static final int MAX_ITEMS = 12;

    static final class Pin {
        long id;
        String code;
    }

    static final class Item {
        String title;
        String meta;
        String imageUrl;
        String machineId;
        String ratingKey;
        String showKey;      // the series, for episodes
        boolean episode;
        int progress;
        long lastViewed;
    }

    static final class Result {
        List<Item> items = new ArrayList<>();
        String error;
    }

    // Last result, shared by every screen until the next refresh.
    static List<Item> cache;
    static long cacheTime;

    private Plex() {
    }

    /** Is the Plex app on this device? (Uses the same app list the launcher already reads.) */
    static boolean installed(Context c) {
        return Mode.isInstalled(c, PLEX_APP);
    }

    /** The installed Plex app's version, e.g. "10.24.0.1234", for Settings → Plex. */
    static String appVersion(Context c) {
        try {
            String v = c.getPackageManager().getPackageInfo(PLEX_APP, 0).versionName;
            return v != null ? v : "Installed";
        } catch (Exception e) {
            return "Not installed";
        }
    }

    static void clearCache() {
        cache = null;
        cacheTime = 0;
    }

    // ------------------------------------------------------------------ HTTP

    private static HttpURLConnection open(Context c, String url, String token, int timeoutMs) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(timeoutMs);
        con.setReadTimeout(timeoutMs * 2);
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("X-Plex-Product", PRODUCT);
        con.setRequestProperty("X-Plex-Version", "2.0");
        con.setRequestProperty("X-Plex-Client-Identifier", Prefs.plexClientId(c));
        con.setRequestProperty("X-Plex-Platform", "Android");
        con.setRequestProperty("X-Plex-Device", "Fire TV");
        con.setRequestProperty("X-Plex-Device-Name", "Fire TV Stick (SimpleLauncher)");
        if (token != null) con.setRequestProperty("X-Plex-Token", token);
        return con;
    }

    private static String read(HttpURLConnection con) throws IOException {
        int code = con.getResponseCode();
        if (code < 200 || code >= 300) {
            con.disconnect();
            throw new IOException("HTTP " + code);
        }
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            con.disconnect();
        }
    }

    private static String get(Context c, String url, String token, int timeoutMs) throws IOException {
        return read(open(c, url, token, timeoutMs));
    }

    // ------------------------------------------------------------------ sign-in (plex.tv/link)

    static Pin createPin(Context c) throws Exception {
        HttpURLConnection con = open(c, "https://plex.tv/api/v2/pins?strong=false", null, 8000);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.getOutputStream().close();
        JSONObject json = new JSONObject(read(con));
        Pin pin = new Pin();
        pin.id = json.getLong("id");
        pin.code = json.getString("code");
        return pin;
    }

    /** The sign-in token once the code has been entered at plex.tv/link, otherwise null. */
    static String checkPin(Context c, long id) {
        try {
            JSONObject json = new JSONObject(get(c, "https://plex.tv/api/v2/pins/" + id, null, 8000));
            if (json.isNull("authToken")) return null;
            String token = json.getString("authToken");
            return token.isEmpty() ? null : token;
        } catch (Exception e) {
            return null;
        }
    }

    static String username(Context c, String token) {
        try {
            JSONObject json = new JSONObject(get(c, "https://plex.tv/api/v2/user", token, 8000));
            String name = json.optString("username", "");
            if (name.isEmpty()) name = json.optString("title", "");
            return name.isEmpty() ? null : name;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ Continue Watching

    /** A Plex server that worked last time: address, token and id. */
    private static final class Server {
        String id;
        String name;
        String uri;
        String token;
    }

    /** Reads Continue Watching from every Plex server you can reach. Call off the main thread. */
    static Result continueWatching(Context c) {
        Result result = new Result();
        String token = Prefs.plexToken(c);
        if (token == null) {
            result.error = "Sign in to Plex in Settings to see what you're watching here.";
            return result;
        }

        // Fast path: go straight to the servers that worked last time.
        boolean anyAnswered = false;
        for (Server server : savedServers(c)) {
            List<Item> items = itemsFrom(c, server.uri, server.token, server.id);
            if (items != null) {
                anyAnswered = true;
                result.items.addAll(items);
            }
        }

        // Otherwise ask plex.tv for your servers and find a working address for each.
        if (!anyAnswered) {
            JSONArray resources;
            try {
                resources = new JSONArray(get(c, "https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1", token, 8000));
            } catch (Exception e) {
                result.error = "Couldn't reach Plex. Check the stick's internet connection.";
                return result;
            }
            List<Server> working = new ArrayList<>();
            boolean foundServer = false;
            for (int i = 0; i < resources.length(); i++) {
                JSONObject res = resources.optJSONObject(i);
                if (res == null || !res.optString("provides", "").contains("server")) continue;
                foundServer = true;
                Server server = new Server();
                server.id = res.optString("clientIdentifier", "");
                server.name = res.optString("name", "Plex server");
                server.token = res.isNull("accessToken") ? token : res.optString("accessToken", token);
                server.uri = pickConnection(c, server.id, res.optJSONArray("connections"));
                if (server.uri == null) continue;
                List<Item> items = itemsFrom(c, server.uri, server.token, server.id);
                if (items == null) continue;
                working.add(server);
                result.items.addAll(items);
            }
            if (!foundServer) {
                result.error = "No Plex servers found on your account.";
                return result;
            }
            if (working.isEmpty()) {
                result.error = "Couldn't reach your Plex server. Is it switched on?";
                return result;
            }
            saveServers(c, working);
        }

        Collections.sort(result.items, (a, b) -> Long.compare(b.lastViewed, a.lastViewed));
        if (result.items.size() > MAX_ITEMS) result.items = new ArrayList<>(result.items.subList(0, MAX_ITEMS));
        cache = result.items;
        cacheTime = android.os.SystemClock.elapsedRealtime();
        saveItems(c, result.items);
        return result;
    }

    /** Tries every address for a server at once and keeps the first that answers. */
    private static String pickConnection(Context c, String machineId, JSONArray connections) {
        List<String> order = new ArrayList<>();
        String remembered = Prefs.plexServerUri(c, machineId);
        if (remembered != null) order.add(remembered);
        if (connections != null) {
            for (int pass = 0; pass < 3; pass++) {
                for (int i = 0; i < connections.length(); i++) {
                    JSONObject conn = connections.optJSONObject(i);
                    if (conn == null) continue;
                    boolean local = conn.optBoolean("local", false);
                    boolean relay = conn.optBoolean("relay", false);
                    boolean match = pass == 0 ? (local && !relay) : pass == 1 ? (!local && !relay) : relay;
                    String uri = conn.optString("uri", "");
                    if (match && !uri.isEmpty() && !order.contains(uri)) order.add(uri);
                }
            }
        }
        if (order.isEmpty()) return null;
        ExecutorService tester = Executors.newFixedThreadPool(Math.min(order.size(), 6));
        try {
            List<java.util.concurrent.Callable<String>> tries = new ArrayList<>();
            for (final String uri : order) {
                tries.add(() -> {
                    get(c, uri + "/identity", null, 4000);
                    return uri;
                });
            }
            String uri = tester.invokeAny(tries, 10, java.util.concurrent.TimeUnit.SECONDS);
            Prefs.setPlexServerUri(c, machineId, uri);
            return uri;
        } catch (Exception e) {
            return null;
        } finally {
            tester.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ remembering between restarts

    private static List<Server> savedServers(Context c) {
        List<Server> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(Prefs.get(c).getString(Prefs.PLEX_SERVERS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Server s = new Server();
                s.id = o.optString("id", "");
                s.name = o.optString("name", "Plex server");
                s.uri = o.optString("uri", "");
                s.token = o.optString("token", "");
                if (!s.uri.isEmpty()) list.add(s);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private static void saveServers(Context c, List<Server> servers) {
        try {
            JSONArray arr = new JSONArray();
            for (Server s : servers) {
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("name", s.name == null ? "Plex server" : s.name);
                o.put("uri", s.uri);
                o.put("token", s.token);
                arr.put(o);
            }
            Prefs.get(c).edit().putString(Prefs.PLEX_SERVERS, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static void saveItems(Context c, List<Item> items) {
        try {
            JSONArray arr = new JSONArray();
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("title", it.title);
                o.put("meta", it.meta);
                o.put("image", it.imageUrl == null ? "" : it.imageUrl);
                o.put("machineId", it.machineId);
                o.put("ratingKey", it.ratingKey);
                o.put("showKey", it.showKey == null ? "" : it.showKey);
                o.put("episode", it.episode);
                o.put("progress", it.progress);
                o.put("lastViewed", it.lastViewed);
                arr.put(o);
            }
            Prefs.get(c).edit().putString(Prefs.PLEX_ITEMS, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Names of the servers SimpleLauncher is using, e.g. "Living Room PC", or null if none yet. */
    static String serverNames(Context c) {
        List<Server> servers = savedServers(c);
        if (servers.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (Server s : servers) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s.name != null ? s.name : "Plex server");
        }
        return sb.toString();
    }

    /** The row as it was last time, so it shows instantly after a restart. */
    static List<Item> loadSaved(Context c) {
        String json = Prefs.get(c).getString(Prefs.PLEX_ITEMS, null);
        if (json == null) return null;
        try {
            JSONArray arr = new JSONArray(json);
            List<Item> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Item it = new Item();
                it.title = o.optString("title", "");
                it.meta = o.optString("meta", "");
                String image = o.optString("image", "");
                it.imageUrl = image.isEmpty() ? null : image;
                it.machineId = o.optString("machineId", "");
                it.ratingKey = o.optString("ratingKey", "");
                String show = o.optString("showKey", "");
                it.showKey = show.isEmpty() ? null : show;
                it.episode = o.optBoolean("episode", false);
                it.progress = o.optInt("progress", 0);
                it.lastViewed = o.optLong("lastViewed", 0);
                list.add(it);
            }
            return list;
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Item> itemsFrom(Context c, String uri, String token, String machineId) {
        String[] paths = {"/hubs/continueWatching/items", "/hubs/home/continueWatching", "/library/onDeck"};
        for (String path : paths) {
            try {
                String body = get(c, uri + path + "?X-Plex-Container-Start=0&X-Plex-Container-Size=20", token, 5000);
                JSONObject container = new JSONObject(body).optJSONObject("MediaContainer");
                if (container == null) continue;
                JSONArray metadata = container.optJSONArray("Metadata");
                if (metadata == null) {
                    JSONArray hubs = container.optJSONArray("Hub");
                    if (hubs != null && hubs.length() > 0) {
                        JSONObject hub = hubs.optJSONObject(0);
                        if (hub != null) metadata = hub.optJSONArray("Metadata");
                    }
                }
                if (metadata == null) {
                    if (path.equals("/library/onDeck")) return new ArrayList<>();
                    continue;
                }
                List<Item> items = new ArrayList<>();
                for (int i = 0; i < metadata.length(); i++) {
                    JSONObject m = metadata.optJSONObject(i);
                    if (m != null) items.add(toItem(m, uri, token, machineId));
                }
                return items;
            } catch (Exception ignored) {
                // try the next endpoint (older servers)
            }
        }
        return null;
    }

    private static Item toItem(JSONObject m, String uri, String token, String machineId) throws Exception {
        Item it = new Item();
        it.machineId = machineId;
        it.ratingKey = m.optString("ratingKey", "");
        it.lastViewed = m.optLong("lastViewedAt", 0);
        long duration = m.optLong("duration", 0);
        long offset = m.optLong("viewOffset", 0);
        it.progress = duration > 0 ? (int) (100 * offset / duration) : 0;
        String left = duration > 0 ? timeLeft(duration - offset) : "";

        String image;
        it.episode = "episode".equals(m.optString("type"));
        String show = m.optString("grandparentRatingKey", "");
        it.showKey = show.isEmpty() ? null : show;
        if (it.episode) {
            it.title = m.optString("grandparentTitle", m.optString("title", ""));
            String se = "S" + m.optInt("parentIndex", 0) + " · E" + m.optInt("index", 0);
            it.meta = left.isEmpty() ? se : se + " · " + left;
            image = firstOf(m, "thumb", "grandparentArt", "art", "grandparentThumb");
        } else {
            it.title = m.optString("title", "");
            it.meta = left;
            image = firstOf(m, "art", "thumb");
        }
        if (image != null) {
            it.imageUrl = uri + "/photo/:/transcode?width=520&height=292&minSize=1&upscale=1&url="
                    + URLEncoder.encode(image, "UTF-8") + "&X-Plex-Token=" + URLEncoder.encode(token, "UTF-8");
        }
        return it;
    }

    private static String firstOf(JSONObject m, String... keys) {
        for (String k : keys) {
            String v = m.optString(k, "");
            if (!v.isEmpty()) return v;
        }
        return null;
    }

    private static String timeLeft(long ms) {
        long mins = Math.max(1, Math.round(ms / 60000.0));
        if (mins < 60) return mins + " min left";
        long h = mins / 60;
        long m = mins % 60;
        return m == 0 ? h + " hr left" : h + " hr " + String.format(java.util.Locale.UK, "%02d", m) + " min left";
    }

    // ------------------------------------------------------------------ opening in Plex

    static void open(Context c, Item item) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(Prefs.plexOpenDetails(c) ? detailsLink(item) : playLink(item)));
        intent.setPackage(PLEX_APP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Apps.tryStart(c, intent)) return;
        if (!Apps.launchPackage(c, PLEX_APP)) Apps.toast(c, "Couldn't open Plex");
    }

    /** Starts playing (and resumes) the item: the format Plex uses for its own home-screen cards. */
    private static String playLink(Item item) {
        String inner = "server://" + item.machineId + "/com.plexapp.plugins.library/library/metadata/" + item.ratingKey;
        return "plex://watch/video?uri=" + Uri.encode(inner);
    }

    /** Opens the film's page, or the series' page for an episode. */
    private static String detailsLink(Item item) {
        if (item.episode && item.showKey != null) {
            return "plex://libraries/" + item.machineId + "/unmatched/tv/" + Uri.encode("/library/metadata/" + item.showKey);
        }
        return "plex://libraries/" + item.machineId + "/unmatched/" + Uri.encode("/library/metadata/" + item.ratingKey);
    }

    // ------------------------------------------------------------------ artwork

    private static final LruCache<String, Bitmap> images = new LruCache<>(24);
    private static final ExecutorService pool = Executors.newFixedThreadPool(3);
    private static final Handler main = new Handler(Looper.getMainLooper());

    static void loadImage(final String url, final ImageView view) {
        view.setTag(url);
        if (url == null) return;
        Bitmap cached = images.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        pool.execute(() -> {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(5000);
                con.setReadTimeout(10000);
                final Bitmap bmp;
                try (InputStream in = con.getInputStream()) {
                    bmp = BitmapFactory.decodeStream(in);
                } finally {
                    con.disconnect();
                }
                if (bmp == null) return;
                images.put(url, bmp);
                main.post(() -> {
                    if (url.equals(view.getTag())) view.setImageBitmap(bmp);
                });
            } catch (Exception ignored) {
            }
        });
    }
}
