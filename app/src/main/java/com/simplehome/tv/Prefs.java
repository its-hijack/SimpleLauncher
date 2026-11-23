package com.simplehome.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.text.format.DateFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Everything the launcher remembers. */
final class Prefs {

    static final String FAVOURITES = "favourites";
    static final String SHOW_APPS = "show_apps_row";
    static final String SHOW_PLEX = "show_plex_row";
    static final String CLOCK_24 = "clock_24";
    static final String BACKGROUND = "background";
    static final String HOOK = "hook_home_button";
    static final String PLEX_TOKEN = "plex_token";
    static final String PLEX_USER = "plex_user";
    static final String PLEX_CLIENT_ID = "plex_client_id";
    static final String PLEX_SERVERS = "plex_servers";
    static final String PLEX_ITEMS = "plex_items";
    static final String PLEX_OPEN_DETAILS = "plex_open_details";
    static final String SHOW_AMPM = "show_ampm";
    static final String SHOW_WEEKDAY = "show_weekday";
    static final String SHOW_DAYMONTH = "show_daymonth";
    static final String SHOW_YEAR = "show_year";
    static final String ACCENT = "accent";
    static final String PASS_UNTIL_HOME = "amazon_pass_until_home";
    static final String SETUP_DONE = "setup_done";
    static final String MODE_OVERRIDE = "mode_override";
    static final String LAYOUT_GRID = "home_layout_grid";
    static final String APP_SIZE = "home_app_size";
    static final String CARD_SIZE = "home_card_size";
    static final String PLEX_IN_ROW = "plex_in_row";
    static final String JELLYFIN_IN_ROW = "jellyfin_in_row";
    static final String SETUP_PLEX = "setup_connect_plex";
    static final String SETUP_JELLYFIN = "setup_connect_jellyfin";

    static final int DEFAULT_BACKGROUND = 0xFF0C0F15;

    private Prefs() {
    }

    static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences("simplehome", Context.MODE_PRIVATE);
    }

    // ---- home screen apps, in order
    static List<String> favourites(Context c) {
        List<String> list = new ArrayList<>();
        for (String s : get(c).getString(FAVOURITES, "").split(",")) {
            if (!s.isEmpty() && !list.contains(s)) list.add(s);
        }
        return list;
    }

    static void setFavourites(Context c, List<String> favs) {
        get(c).edit().putString(FAVOURITES, TextUtils.join(",", favs)).apply();
    }

    // ---- switches
    static boolean showApps(Context c) {
        return get(c).getBoolean(SHOW_APPS, true);
    }

    static boolean showPlex(Context c) {
        return get(c).getBoolean(SHOW_PLEX, true);
    }

    static boolean clock24(Context c) {
        return get(c).getBoolean(CLOCK_24, DateFormat.is24HourFormat(c));
    }

    static boolean showAmPm(Context c) {
        return get(c).getBoolean(SHOW_AMPM, true);
    }

    static boolean showWeekday(Context c) {
        return get(c).getBoolean(SHOW_WEEKDAY, true);
    }

    static boolean showDayMonth(Context c) {
        return get(c).getBoolean(SHOW_DAYMONTH, true);
    }

    static boolean showYear(Context c) {
        return get(c).getBoolean(SHOW_YEAR, false);
    }

    static int accent(Context c) {
        return get(c).getInt(ACCENT, 0xFF2F8CFF);
    }

    static void setAccent(Context c, int color) {
        get(c).edit().putInt(ACCENT, color).apply();
    }

    /** True: Amazon Home pauses the catcher until you open SimpleLauncher again. */
    static boolean passUntilHome(Context c) {
        return get(c).getBoolean(PASS_UNTIL_HOME, false);
    }

    // ---- home screen layout and sizes (0 = smallest … 4 = largest)
    static boolean gridLayout(Context c) {
        return get(c).getBoolean(LAYOUT_GRID, false);
    }

    static int appSize(Context c) {
        return get(c).getInt(APP_SIZE, 2);
    }

    static int cardSize(Context c) {
        return get(c).getInt(CARD_SIZE, 2);
    }

    static void setInt(Context c, String key, int value) {
        get(c).edit().putInt(key, value).apply();
    }

    // ---- which services fill Continue Watching
    static boolean plexInRow(Context c) {
        return get(c).getBoolean(PLEX_IN_ROW, true);
    }

    static boolean jellyfinInRow(Context c) {
        return get(c).getBoolean(JELLYFIN_IN_ROW, true);
    }

    static boolean setupDone(Context c) {
        return get(c).getBoolean(SETUP_DONE, false);
    }

    static boolean hook(Context c) {
        return get(c).getBoolean(HOOK, true);
    }

    static void setBool(Context c, String key, boolean value) {
        get(c).edit().putBoolean(key, value).apply();
    }

    // ---- background colour
    static int background(Context c) {
        return get(c).getInt(BACKGROUND, DEFAULT_BACKGROUND);
    }

    static void setBackground(Context c, int color) {
        get(c).edit().putInt(BACKGROUND, color).apply();
    }

    // ---- Plex
    static String plexToken(Context c) {
        return get(c).getString(PLEX_TOKEN, null);
    }

    static String plexUser(Context c) {
        return get(c).getString(PLEX_USER, null);
    }

    static void setPlex(Context c, String token, String user) {
        SharedPreferences.Editor e = get(c).edit().putString(PLEX_TOKEN, token).putString(PLEX_USER, user);
        // A new sign-in (or signing out) forgets the old servers and row.
        e.remove(PLEX_SERVERS).remove(PLEX_ITEMS);
        e.apply();
    }

    /** False: selecting a show plays it. True: opens its page in Plex. */
    static boolean plexOpenDetails(Context c) {
        return get(c).getBoolean(PLEX_OPEN_DETAILS, false);
    }

    static String plexClientId(Context c) {
        String id = get(c).getString(PLEX_CLIENT_ID, null);
        if (id == null) {
            id = "simplehome-" + UUID.randomUUID();
            get(c).edit().putString(PLEX_CLIENT_ID, id).apply();
        }
        return id;
    }

    /** Remembered working address for a Plex server, so we try it first next time. */
    static String plexServerUri(Context c, String machineId) {
        return get(c).getString("plex_uri_" + machineId, null);
    }

    static void setPlexServerUri(Context c, String machineId, String uri) {
        get(c).edit().putString("plex_uri_" + machineId, uri).apply();
    }
}
