package com.simplehome.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The launcher's own settings: categories on the left, the selected category's options
 * on the right (like Fire TV's own settings).
 */
public class SettingsActivity extends Screen {

    private static final int DARK_TEXT = 0xFF0C0F15;
    private static final int DARK_HINT = 0xFF4A5160;
    private static final int ROW = 0xFF151922;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<String> categories = new ArrayList<>();
    private final List<View> categoryViews = new ArrayList<>();
    private View root;
    private LinearLayout list;
    private String current = "Home screen";
    private boolean rendering;
    private String returnFocusTo;   // option to re-select after coming back from a picker
    private boolean modeOpen;        // "Running in" drop-down
    private String lastCategory;     // for the fade/slide when switching category

    private interface Toggle {
        void set(boolean on);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        root = findViewById(R.id.root);
        list = findViewById(R.id.list);
        buildCategories();
        categoryViews.get(0).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildCategories();   // Plex may have been installed or removed, or the mode changed
        render();
    }

    // ------------------------------------------------------------------ categories

    /** Which categories to show: the Plex one only while Plex is installed. */
    private List<String> wantedCategories() {
        List<String> list = new ArrayList<>();
        list.add("Home screen");
        list.add("Appearance");
        if (Plex.installed(this)) list.add("Plex");
        list.add("Danger Zone");
        list.add("Information");
        return list;
    }

    private void buildCategories() {
        List<String> wanted = wantedCategories();
        if (wanted.equals(categories)) return;
        boolean hadFocus = getCurrentFocus() != null && categoryViews.contains(getCurrentFocus());

        LinearLayout box = findViewById(R.id.categories);
        rendering = true;
        box.removeAllViews();
        categories.clear();
        categoryViews.clear();
        categories.addAll(wanted);
        if (!categories.contains(current)) current = categories.get(0);

        for (final String category : categories) {
            LinearLayout row = new LinearLayout(this);
            row.setId(View.generateViewId());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(this, 38));
            row.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
            row.setBackground(categoryBackground());
            row.setFocusable(true);
            row.setClickable(true);

            TextView name = new TextView(this);
            name.setText(category);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            name.setTextColor(categoryColors(DARK_TEXT, Color.WHITE, Ui.TEXT_DIM));
            name.setDuplicateParentStateEnabled(true);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView chevron = new TextView(this);
            chevron.setText("\u203A");
            chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            chevron.setTextColor(categoryColors(DARK_TEXT, Color.WHITE, 0xFF5A6275));
            chevron.setDuplicateParentStateEnabled(true);
            row.addView(chevron);

            row.setOnFocusChangeListener((v, hasFocus) -> {
                // While the options are being redrawn, focus can briefly land here: ignore that.
                if (hasFocus && !rendering && !category.equals(current)) {
                    current = category;
                    modeOpen = false;
                    render();
                }
            });
            row.setOnClickListener(v -> focusFirstOption());

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, Ui.dp(this, 4));
            box.addView(row, lp);
            categoryViews.add(row);
        }
        rendering = false;
        if (hadFocus) currentCategoryView().requestFocus();
    }

    private View currentCategoryView() {
        int i = categories.indexOf(current);
        return categoryViews.get(i >= 0 ? i : 0);
    }

    private StateListDrawable categoryBackground() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, Ui.rounded(this, Color.WHITE, 8, 0, 0));
        s.addState(new int[]{android.R.attr.state_activated}, Ui.rounded(this, 0xFF1E2430, 8, 0, 0));
        s.addState(new int[]{}, Ui.rounded(this, Color.TRANSPARENT, 8, 0, 0));
        s.setEnterFadeDuration(150);
        s.setExitFadeDuration(150);
        return s;
    }

    private static ColorStateList categoryColors(int focused, int activated, int normal) {
        return new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {android.R.attr.state_activated}, {}},
                new int[]{focused, activated, normal});
    }

    private void focusFirstOption() {
        for (int i = 0; i < list.getChildCount(); i++) {
            View v = list.getChildAt(i);
            if (v.isFocusable()) {
                v.requestFocus();
                return;
            }
        }
    }

    // ------------------------------------------------------------------ drawing the right-hand side

    private void render() {
        Ui.applyBackground(this, root);
        for (int i = 0; i < categoryViews.size(); i++) categoryViews.get(i).setActivated(categories.get(i).equals(current));

        View focused = getCurrentFocus();
        Object focusTag = focused != null && isInside(focused, list) ? focused.getTag() : null;
        if (returnFocusTo != null) {
            focusTag = returnFocusTo;
            returnFocusTo = null;
        }

        rendering = true;
        try {
            list.removeAllViews();
            switch (current) {
                case "Home screen": homeScreen(); break;
                case "Appearance": appearance(); break;
                case "Plex": plex(); break;
                case "Danger Zone": dangerZone(); break;
                default: information(); break;
            }
            // Switching category: the options fade and slide into place.
            if (lastCategory != null && !lastCategory.equals(current)) {
                int dir = categories.indexOf(current) > categories.indexOf(lastCategory) ? 1 : -1;
                list.setAlpha(0f);
                list.setTranslationY(dir * Ui.dp(this, 16));
                list.animate().alpha(1f).translationY(0f).setDuration(280)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
            }
            lastCategory = current;
            if (focusTag != null) {
                View target = list.findViewWithTag(focusTag);
                if (target != null) target.requestFocus();
                else focusFirstOption();
            }
        } finally {
            rendering = false;
        }
    }

    private void homeScreen() {
        section("HOME SCREEN ROWS", true);
        toggleRow("apps", "Your apps", null, Prefs.showApps(this), on -> Prefs.setBool(this, Prefs.SHOW_APPS, on));
        if (Plex.installed(this)) {
            toggleRow("plex_row", "Continue Watching (Plex)",
                    Prefs.plexToken(this) == null ? "Sign in to Plex in the Plex category to fill this row" : null,
                    Prefs.showPlex(this), on -> Prefs.setBool(this, Prefs.SHOW_PLEX, on));
        } else {
            ToggleView off = new ToggleView(this);
            off.setOn(false);
            View row = addRow("plex_row", "Continue Watching (Plex)", "Install Plex to use this row", off,
                    () -> Apps.toast(this, "Install the Plex app to use this row"));
            row.setAlpha(0.45f);
        }
        section("YOUR APPS", false);
        valueRow("choose", "Choose your apps", "Pick and order the apps on your home screen", "Open",
                () -> Screen.open(this, new Intent(this, AppSelectionActivity.class)));
    }

    private void appearance() {
        section("COLOURS", true);
        valueRow("background", "Background colour", "Behind every SimpleLauncher screen", backgroundName(), this::pickBackground);
        accentRow();

        section("CLOCK", false);
        toggleRow("clock24", "24-hour clock", null, Prefs.clock24(this), on -> Prefs.setBool(this, Prefs.CLOCK_24, on));
        if (!Prefs.clock24(this)) {
            toggleRow("ampm", "Show AM / PM", "Next to the time", Prefs.showAmPm(this),
                    on -> Prefs.setBool(this, Prefs.SHOW_AMPM, on));
        }

        section("DATE", false);
        String preview = Ui.datePattern(this);
        String sample = preview != null
                ? new java.text.SimpleDateFormat(preview, java.util.Locale.UK).format(new java.util.Date())
                : "Date hidden";
        toggleRow("weekday", "Show the day of the week", "Now showing: " + sample, Prefs.showWeekday(this),
                on -> Prefs.setBool(this, Prefs.SHOW_WEEKDAY, on));
        toggleRow("daymonth", "Show the day and month", null, Prefs.showDayMonth(this),
                on -> Prefs.setBool(this, Prefs.SHOW_DAYMONTH, on));
        toggleRow("year", "Show the year", null, Prefs.showYear(this),
                on -> Prefs.setBool(this, Prefs.SHOW_YEAR, on));
    }

    /** HomeCatch: the Fire TV Home-button catcher. */
    private void homeCatch() {
        boolean serviceOn = HomeButtonService.isEnabled(this);
        section("HOMECATCH", true);
        toggleRow("hook", "Open SimpleLauncher when Home is pressed",
                serviceOn ? "Replaces Amazon's home screen with this one"
                        : "Not set up yet: run firestick.sh on your Mac and choose 3",
                Prefs.hook(this) && serviceOn, this::setHook);
        final boolean untilHome = Prefs.passUntilHome(this);
        valueRow("pass", "Amazon Home button",
                untilHome ? "Amazon's menus stay until you open SimpleLauncher again"
                        : "SimpleLauncher comes back when you leave Amazon's menus",
                untilHome ? "Stay in Amazon" : "Come back", () -> {
                    Prefs.setBool(this, Prefs.PASS_UNTIL_HOME, !untilHome);
                    render();
                });
        String status = !serviceOn ? "Not set up" : Prefs.hook(this) ? "Running" : "Switched off";
        valueRow("status", "Status", null, status, null);
    }

    private void homeApp() {
        section("HOME APP", true);
        boolean isHome = Mode.isDefaultHome(this);
        valueRow("default_home", "Use SimpleLauncher as your home screen",
                isHome ? "SimpleLauncher opens when you press Home" : "Choose SimpleLauncher in Android's home app settings",
                isHome ? "On" : "Set it up", () -> Mode.openHomeAppPicker(this));
        valueRow("return_home", "Return home", "Opens your device's original home screen", "Try it",
                () -> Mode.openOriginalHome(this));
    }

    private void plex() {
        boolean signedIn = Prefs.plexToken(this) != null;
        String user = Prefs.plexUser(this);
        section("ACCOUNT", true);
        if (signedIn) {
            valueRow("plex", "Plex account", "Signed in" + (user != null ? " as " + user : ""), "Sign out", this::plexSignOut);
        } else {
            valueRow("plex", "Plex account", "Needed for the Continue Watching (Plex) row", "Sign in", this::plexSignIn);
        }
        if (signedIn) plexContinueWatching();
        String servers = Plex.serverNames(this);
        infoGroup("PLEX STATUS", false, new String[][]{
                {"Plex app", Plex.appVersion(this)},
                {"Account", !signedIn ? "Not signed in" : "Signed in" + (user != null ? " as " + user : "")},
                {"Server", !signedIn ? "Sign in first" : servers != null ? servers : "Not connected yet"}});
    }

    private void plexContinueWatching() {
        section("CONTINUE WATCHING", false);
        final boolean details = Prefs.plexOpenDetails(this);
        valueRow("plex_open", "When you select a show", "Switch this if Plex opens the wrong place",
                details ? "Open its page" : "Play it", () -> {
                    Prefs.setBool(this, Prefs.PLEX_OPEN_DETAILS, !details);
                    render();
                });
        valueRow("plex_refresh", "Refresh now", "Fetch the latest from your Plex server", "Refresh", this::plexRefresh);
    }

    /** Danger Zone: HomeCatch (Fire TV) or Home app (generic), shortcuts and reset. */
    private void dangerZone() {
        if (Mode.fireTv(this)) homeCatch(); else homeApp();
        section("SHORTCUTS", false);
        if (Mode.fireTv(this)) {
            valueRow("fire_settings", "Fire TV settings", "Opens Amazon's settings", "Open", () -> {
                HomeButtonService.allowAmazonHome();
                Apps.openFireSettings(this);
            });
            valueRow("amazon_home", "Amazon home screen", "Same as the Amazon Home button", "Open", () -> {
                HomeButtonService.allowAmazonHome();
                Apps.openAmazonHome(this);
            });
        } else {
            valueRow("device_settings", "Device settings", "Opens Android's settings", "Open", () -> {
                if (!Apps.tryStart(this, new Intent(android.provider.Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) Apps.toast(this, "Couldn't open settings");
            });
            valueRow("original_home", "Original home screen", "Same as the Return home button", "Open",
                    () -> Mode.openOriginalHome(this));
        }
        valueRow("app_info", "SimpleLauncher app info", "Force stop or clear SimpleLauncher if it misbehaves", "Open",
                () -> Apps.openAppInfo(this, getPackageName()));
        valueRow("setup_again", "Run setup again", "Go through first-time setup with your current choices", "Start",
                () -> Screen.open(this, new Intent(this, SetupActivity.class)));
        section("RESET", false);
        valueRow("reset", "Reset SimpleLauncher", "Clears your apps, colours, Plex sign-in and every setting, then starts setup", "Reset",
                this::confirmReset);
    }

    private void confirmReset() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Reset SimpleLauncher?")
                .setMessage("This clears your home-screen apps, colours, clock and date settings, Plex sign-in and mode, "
                        + "then starts first-time setup. Your other apps aren't affected.")
                .setPositiveButton("Reset", (d, w) -> {
                    Prefs.get(this).edit().clear().commit();
                    Plex.clearCache();
                    Intent setup = new Intent(this, SetupActivity.class);
                    setup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(setup);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void information() {
        section("MODE", true);
        int choice = Mode.override(this);
        valueRow("mode_running", "Running in",
                modeOpen ? "Choose below. Only change this if your device was detected wrongly" : "Select to change",
                Mode.name(this) + (choice == Mode.AUTO ? " (automatic)" : ""), () -> {
                    modeOpen = !modeOpen;
                    returnFocusTo = "mode_running";
                    render();
                });
        if (modeOpen) {
            String autoName = Mode.isFireTvDevice(this) ? "Fire TV mode" : "Generic mode";
            String[] labels = {"Automatic (" + autoName + " on this device)", "Fire TV mode", "Generic mode"};
            for (int i = 0; i < labels.length; i++) {
                final int which = i;
                View opt = valueRowView("mode_opt_" + i, labels[i], null, i == choice ? "Selected" : "", () -> {
                    Mode.setOverride(this, which);
                    modeOpen = false;
                    returnFocusTo = "mode_running";
                    buildCategories();
                    render();
                });
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) opt.getLayoutParams();
                lp.setMargins(Ui.dp(this, 22), Ui.dp(this, 3), 0, 0);
                opt.setLayoutParams(lp);
            }
        }
        valueRow("mode_detected", "Detected as", null, Mode.detected(this), null);

        infoGroup("DEVICE", false, new String[][]{
                {"Device name", DeviceInfo.deviceName(this)},
                {"Model", DeviceInfo.model()},
                {Mode.isFireTvDevice(this) ? "Fire OS" : "Android", Mode.isFireTvDevice(this) ? DeviceInfo.fireOs() : android.os.Build.VERSION.RELEASE},
                {"Running for", DeviceInfo.upTime()}});
        infoGroup("NETWORK", false, new String[][]{
                {"IP address", DeviceInfo.ipAddress(this)},
                {"Connection", DeviceInfo.connection(this)},
                {"ADB debugging", DeviceInfo.adb(this)}});
        infoGroup("STORAGE & MEMORY", false, new String[][]{
                {"Storage", DeviceInfo.storage()},
                {"Memory in use", DeviceInfo.memory(this)}});
        infoGroup("SIMPLELAUNCHER", false, new String[][]{
                {"Version", BuildConfig.RELEASE_VERSION},
                {"Build", BuildConfig.BUILD_TAG},
                {"Made by", "Cherry Universe"}});
    }

    // ------------------------------------------------------------------ building blocks

    private void section(String title, boolean first) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.setLetterSpacing(0.15f);
        t.setTextColor(0xFF8A93A6);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, first ? 4 : 16), 0, Ui.dp(this, 2));
        list.addView(t, lp);
    }

    private void toggleRow(String tag, String label, String hint, final boolean on, final Toggle change) {
        ToggleView toggle = new ToggleView(this);
        toggle.setOn(on);
        addRow(tag, label, hint, toggle, () -> {
            change.set(!on);
            render();
        });
    }

    private void valueRow(String tag, String label, String hint, String value, Runnable onClick) {
        valueRowView(tag, label, hint, value, onClick);
    }

    private View valueRowView(String tag, String label, String hint, String value, Runnable onClick) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        v.setTextColor(Ui.focusColors(DARK_TEXT, Ui.TEXT_DIM));
        v.setDuplicateParentStateEnabled(true);
        return addRow(tag, label, hint, v, onClick);
    }

    /** Accent colour: shows the current colour; Select opens a menu to choose another. */
    private void accentRow() {
        final int currentAccent = Prefs.accent(this);
        int idx = 0;
        for (int i = 0; i < Ui.ACCENT_COLORS.length; i++) if (Ui.ACCENT_COLORS[i] == currentAccent) idx = i;

        LinearLayout value = new LinearLayout(this);
        value.setOrientation(LinearLayout.HORIZONTAL);
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setDuplicateParentStateEnabled(true);
        View dot = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(currentAccent);
        d.setStroke(Ui.dp(this, 1), 0xFF5A6275);
        dot.setBackground(d);
        value.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16)));
        TextView name = new TextView(this);
        name.setText(Ui.ACCENT_NAMES[idx]);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setTextColor(Ui.focusColors(DARK_TEXT, Ui.TEXT_DIM));
        name.setDuplicateParentStateEnabled(true);
        name.setPadding(Ui.dp(this, 8), 0, 0, 0);
        value.addView(name);

        addRow("accent", "Accent colour", "Progress bars, switches and number badges", value, () -> {
            returnFocusTo = "accent";
            Screen.open(this, new Intent(this, AccentPickerActivity.class));
        });
    }

    private View addRow(String tag, String label, String hint, View right, final Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(this, 44));
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 7), Ui.dp(this, 14), Ui.dp(this, 7));
        row.setBackground(Ui.rowBackground(this));
        row.setFocusable(true);
        row.setClickable(true);
        row.setTag(tag);
        row.setNextFocusLeftId(currentCategoryView().getId());

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setDuplicateParentStateEnabled(true);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        l.setTextColor(Ui.focusColors(DARK_TEXT, Ui.TEXT));
        l.setDuplicateParentStateEnabled(true);
        texts.addView(l);
        if (hint != null) {
            TextView h = new TextView(this);
            h.setText(hint);
            h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            h.setTextColor(Ui.focusColors(DARK_HINT, Ui.TEXT_DIM));
            h.setDuplicateParentStateEnabled(true);
            texts.addView(h);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.setMargins(Ui.dp(this, 16), 0, 0, 0);
        row.addView(right, rp);

        if (onClick != null) row.setOnClickListener(v -> onClick.run());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 5), 0, 0);
        list.addView(row, lp);
        return row;
    }

    /** A read-only block of label/value lines (focusable so the remote can scroll to it). */
    private void infoGroup(String title, boolean first, String[][] rows) {
        section(title, first);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 3), Ui.dp(this, 14), Ui.dp(this, 3));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, Ui.rounded(this, ROW, 8, 2, 0xFF5A6275));
        bg.addState(new int[]{}, Ui.rounded(this, ROW, 8, 0, 0));
        card.setBackground(bg);
        card.setFocusable(true);
        card.setTag("info:" + title);
        card.setNextFocusLeftId(currentCategoryView().getId());

        for (int i = 0; i < rows.length; i++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setMinimumHeight(Ui.dp(this, 28));
            TextView l = new TextView(this);
            l.setText(rows[i][0]);
            l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            l.setTextColor(Ui.TEXT_DIM);
            line.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView v = new TextView(this);
            v.setText(rows[i][1]);
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            v.setTextColor(Ui.TEXT);
            v.setGravity(Gravity.RIGHT);
            line.addView(v);
            card.addView(line);
            if (i < rows.length - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(0xFF232836);
                card.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            }
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 5), 0, 0);
        list.addView(card, lp);
    }

    private static boolean isInside(View v, ViewGroup parent) {
        ViewParent p = v.getParent();
        while (p != null) {
            if (p == parent) return true;
            p = p.getParent();
        }
        return false;
    }

    // ------------------------------------------------------------------ actions

    private String backgroundName() {
        int bg = Prefs.background(this);
        for (int i = 0; i < Ui.BG_COLORS.length; i++) {
            if (Ui.BG_COLORS[i] == bg) return Ui.BG_NAMES[i];
        }
        return "Custom";
    }

    private void pickBackground() {
        returnFocusTo = "background";
        Screen.open(this, new Intent(this, BackgroundPickerActivity.class));
    }

    private void setHook(boolean on) {
        Prefs.setBool(this, Prefs.HOOK, on);
        if (on && !HomeButtonService.isEnabled(this) && !HomeButtonService.enable(this)) {
            Apps.toast(this, "Run firestick.sh on your Mac and choose 3 to allow this");
        }
    }

    private void plexRefresh() {
        Apps.toast(this, "Refreshing from Plex…");
        final Context app = getApplicationContext();
        new Thread(() -> {
            final Plex.Result r = Plex.continueWatching(app);
            main.post(() -> {
                if (r.error != null && r.items.isEmpty()) {
                    Apps.toast(app, r.error);
                } else {
                    Apps.toast(app, "Plex: " + r.items.size() + (r.items.size() == 1 ? " show" : " shows") + " to continue");
                }
                if (!isDestroyed()) render();
            });
        }).start();
    }

    private void plexSignIn() {
        final Context app = getApplicationContext();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 24);
        box.setPadding(pad, Ui.dp(this, 8), pad, Ui.dp(this, 8));
        final TextView info = new TextView(this);
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        info.setTextColor(Ui.TEXT);
        info.setText("Getting a code from Plex…");
        final TextView code = new TextView(this);
        code.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        code.setLetterSpacing(0.2f);
        code.setTextColor(Color.WHITE);
        code.setGravity(Gravity.CENTER);
        code.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 8));
        box.addView(info);
        box.addView(code);

        final AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Sign in to Plex")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .show();
        final boolean[] closed = {false};
        dialog.setOnDismissListener(d -> closed[0] = true);

        new Thread(() -> {
            try {
                final Plex.Pin pin = Plex.createPin(app);
                main.post(() -> {
                    info.setText("On your phone or computer, go to plex.tv/link and enter this code:");
                    code.setText(pin.code);
                });
                long deadline = SystemClock.elapsedRealtime() + 15 * 60_000;
                while (!closed[0] && SystemClock.elapsedRealtime() < deadline) {
                    Thread.sleep(2000);
                    String token = Plex.checkPin(app, pin.id);
                    if (token != null) {
                        Prefs.setPlex(app, token, Plex.username(app, token));
                        Plex.clearCache();
                        main.post(() -> {
                            if (dialog.isShowing()) dialog.dismiss();
                            Apps.toast(app, "Signed in to Plex");
                            render();
                        });
                        return;
                    }
                }
                main.post(() -> {
                    info.setText("That code has expired. Close this and try again.");
                    code.setText("");
                });
            } catch (Exception e) {
                main.post(() -> {
                    info.setText("Couldn't reach Plex. Check the stick's internet connection and try again.");
                    code.setText("");
                });
            }
        }).start();
    }

    private void plexSignOut() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Sign out of Plex?")
                .setMessage("The Continue Watching (Plex) row will be empty until you sign in again.")
                .setPositiveButton("Sign out", (d, w) -> {
                    Prefs.setPlex(this, null, null);
                    Plex.clearCache();
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
