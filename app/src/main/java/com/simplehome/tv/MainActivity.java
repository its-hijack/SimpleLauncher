package com.simplehome.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The home screen: logo and clock, three buttons, your apps across the middle,
 * and Continue Watching (Plex) underneath.
 */
public class MainActivity extends Activity {

    private static final long PLEX_REFRESH_MS = 60_000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Apps.Entry> byPkg = new HashMap<>();
    private int loadGeneration = 0;

    private View root;
    private TextClock clock;
    private TextClock ampm;
    private TextClock date;
    private View appsSection;
    private LinearLayout appsRow;
    private View plexSection;
    private View plexScroll;
    private LinearLayout plexRow;
    private TextView plexStatus;

    private boolean plexLoading;

    private FocusRing ring;
    private Object savedFocusTag;
    private boolean firstRender = true;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reloadApps();
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        root = findViewById(R.id.root);
        clock = findViewById(R.id.clock);
        ampm = findViewById(R.id.ampm);
        date = findViewById(R.id.date);
        appsSection = findViewById(R.id.apps_section);
        appsRow = findViewById(R.id.apps_row);
        plexSection = findViewById(R.id.plex_section);
        plexScroll = findViewById(R.id.plex_scroll);
        plexRow = findViewById(R.id.plex_row);
        plexStatus = findViewById(R.id.plex_status);

        findViewById(R.id.btn_amazon).setOnClickListener(v -> {
            if (Mode.fireTv(this)) {
                HomeButtonService.allowAmazonHome();
                Apps.openAmazonHome(this);
            } else {
                Mode.openOriginalHome(this);
            }
        });
        findViewById(R.id.btn_apps).setOnClickListener(v -> Screen.open(this, new Intent(this, AppSelectionActivity.class)));
        findViewById(R.id.btn_settings).setOnClickListener(v -> Screen.open(this, new Intent(this, SettingsActivity.class)));

        // The white outline that glides between tiles.
        ring = new FocusRing(this);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> onFocusMoved(newFocus));
        root.getViewTreeObserver().addOnScrollChangedListener(() -> ring.follow());

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);

        // First launch (or first launch after updating): run setup.
        if (!Prefs.setupDone(this)) Screen.open(this, new Intent(this, SetupActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        HomeButtonService.launcherShown();
        applySettings();
        reloadApps();
        refreshPlex(false);
    }

    @Override
    protected void onPause() {
        View f = getCurrentFocus();
        savedFocusTag = f != null ? f.getTag() : null;
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already here: back to the first app.
        savedFocusTag = null;
        firstRender = true;
        focusFirstApp();
    }

    @Override
    public void onBackPressed() {
        // This is the home screen; there's nowhere to go back to.
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(packageReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        super.onDestroy();
    }

    private void applySettings() {
        Ui.applyBackground(this, root);
        Ui.applyClock(this, clock, ampm, date);
        appsSection.setVisibility(Prefs.showApps(this) ? View.VISIBLE : View.GONE);
        plexSection.setVisibility(Prefs.showPlex(this) && Plex.installed(this) ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.btn_amazon)).setText(Mode.homeButtonLabel(this));
    }

    // ------------------------------------------------------------------ your apps row

    private void reloadApps() {
        final int generation = ++loadGeneration;
        final Context app = getApplicationContext();
        new Thread(() -> {
            final List<Apps.Entry> list = Apps.load(app);
            main.post(() -> {
                if (generation != loadGeneration || isDestroyed()) return;
                byPkg.clear();
                for (Apps.Entry e : list) byPkg.put(e.pkg, e);
                renderApps();
            });
        }).start();
    }

    private List<String> visibleFavourites() {
        List<String> visible = new ArrayList<>();
        for (String pkg : Prefs.favourites(this)) {
            if (byPkg.containsKey(pkg)) visible.add(pkg);
        }
        return visible;
    }

    private void renderApps() {
        View focused = getCurrentFocus();
        Object tag = focused != null && isInside(focused, appsRow) ? focused.getTag() : savedFocusTag;

        appsRow.removeAllViews();
        for (String pkg : visibleFavourites()) {
            final Apps.Entry e = byPkg.get(pkg);
            Ui.Tile t = Ui.appTile(this, e);
            t.card.setBackground(Ui.plainCardBackground(this));
            t.root.setTag("app:" + pkg);
            t.root.setOnClickListener(v -> Apps.launch(this, e));
            t.root.setOnLongClickListener(v -> {
                showAppOptions(e);
                return true;
            });
            t.root.setOnKeyListener((v, keyCode, event) -> onMenuKey(keyCode, event, () -> showAppOptions(e)));
            appsRow.addView(t.root, Ui.rowItemParams(this));
        }
        if (appsRow.getChildCount() == 0) {
            Ui.Tile t = Ui.textTile(this, "+", "Choose your apps");
            t.card.setBackground(Ui.plainCardBackground(this));
            t.root.setTag("choose");
            t.root.setOnClickListener(v -> Screen.open(this, new Intent(this, AppSelectionActivity.class)));
            appsRow.addView(t.root, Ui.rowItemParams(this));
        }

        View target = tag != null ? appsRow.findViewWithTag(tag) : null;
        if (target != null) {
            target.requestFocus();
        } else if (firstRender || getCurrentFocus() == null || (tag != null && tag.toString().startsWith("app:"))) {
            focusFirstApp();
        }
        firstRender = false;
        savedFocusTag = null;
    }

    private void focusFirstApp() {
        if (appsSection.getVisibility() == View.VISIBLE && appsRow.getChildCount() > 0) {
            appsRow.getChildAt(0).requestFocus();
        } else if (plexSection.getVisibility() == View.VISIBLE && plexRow.getChildCount() > 0) {
            plexRow.getChildAt(0).requestFocus();
        } else {
            findViewById(R.id.btn_apps).requestFocus();
        }
    }

    private void showAppOptions(final Apps.Entry e) {
        final List<String> visible = visibleFavourites();
        final int index = visible.indexOf(e.pkg);
        List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        if (index > 0) {
            labels.add("Move left");
            actions.add(() -> moveFavourite(e.pkg, -1));
        }
        if (index >= 0 && index < visible.size() - 1) {
            labels.add("Move right");
            actions.add(() -> moveFavourite(e.pkg, 1));
        }
        labels.add("Remove from home screen");
        actions.add(() -> {
            List<String> favs = Prefs.favourites(this);
            favs.remove(e.pkg);
            Prefs.setFavourites(this, favs);
            renderApps();
        });
        labels.add("App info");
        actions.add(() -> Apps.openAppInfo(this, e.pkg));
        dialog(e.label, labels, actions);
    }

    private void moveFavourite(String pkg, int direction) {
        List<String> favs = Prefs.favourites(this);
        List<String> visible = visibleFavourites();
        int from = visible.indexOf(pkg);
        int to = from + direction;
        if (from < 0 || to < 0 || to >= visible.size()) return;
        java.util.Collections.swap(favs, favs.indexOf(pkg), favs.indexOf(visible.get(to)));
        Prefs.setFavourites(this, favs);
        savedFocusTag = "app:" + pkg;
        renderApps();
        View moved = appsRow.findViewWithTag("app:" + pkg);
        if (moved != null) moved.requestFocus();
    }

    // ------------------------------------------------------------------ Continue Watching (Plex)

    private void refreshPlex(boolean force) {
        if (!Prefs.showPlex(this) || !Plex.installed(this)) return;
        if (Prefs.plexToken(this) == null) {
            showPlexStatus("Sign in to Plex in Settings to see what you're watching here.");
            return;
        }
        if (Plex.cache == null) Plex.cache = Plex.loadSaved(this);
        if (Plex.cache != null) {
            renderPlex(Plex.cache);
        } else {
            showPlexStatus("Loading from Plex…");
        }
        if (plexLoading) return;
        if (!force && Plex.cache != null && SystemClock.elapsedRealtime() - Plex.cacheTime < PLEX_REFRESH_MS) return;

        plexLoading = true;
        final Context app = getApplicationContext();
        new Thread(() -> {
            final Plex.Result result = Plex.continueWatching(app);
            main.post(() -> {
                plexLoading = false;
                if (isDestroyed()) return;
                if (result.error != null && result.items.isEmpty()) {
                    if (Plex.cache == null) showPlexStatus(result.error);
                    return;
                }
                Plex.cache = result.items;
                Plex.cacheTime = SystemClock.elapsedRealtime();
                renderPlex(result.items);
            });
        }).start();
    }

    private void showPlexStatus(String message) {
        boolean hadFocus = getCurrentFocus() != null && isInside(getCurrentFocus(), plexRow);
        plexRow.removeAllViews();
        plexScroll.setVisibility(View.GONE);
        plexStatus.setText(message);
        plexStatus.setVisibility(View.VISIBLE);
        if (hadFocus) focusFirstApp();
    }

    private void renderPlex(List<Plex.Item> items) {
        if (items.isEmpty()) {
            showPlexStatus("Nothing in progress on Plex right now.");
            return;
        }
        View focused = getCurrentFocus();
        Object tag = focused != null && isInside(focused, plexRow) ? focused.getTag() : savedFocusTag;

        plexStatus.setVisibility(View.GONE);
        plexScroll.setVisibility(View.VISIBLE);
        plexRow.removeAllViews();
        for (Plex.Item item : items) {
            plexRow.addView(plexCard(item), Ui.rowItemParams(this));
        }

        if (tag != null && tag.toString().startsWith("plex:")) {
            View target = plexRow.findViewWithTag(tag);
            if (target == null) target = plexRow.getChildAt(0);
            target.requestFocus();
        }
    }

    private View plexCard(final Plex.Item item) {
        final int w = 195;
        final int h = 110;
        LinearLayout tile = Ui.baseTile(this);
        tile.setTag("plex:" + item.machineId + ":" + item.ratingKey);

        FrameLayout card = Ui.card(this, w, h);
        card.setBackground(Ui.plainCardBackground(this));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Ui.CARD);
        card.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Progress bar along the bottom
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(0x2EFFFFFF);
        View done = new View(this);
        done.setBackgroundColor(Ui.accent(this));
        int pct = Math.max(0, Math.min(100, item.progress));
        bar.addView(done, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, pct));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 100 - pct));
        card.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3), Gravity.BOTTOM));
        tile.addView(card, new LinearLayout.LayoutParams(Ui.dp(this, w), Ui.dp(this, h)));

        TextView title = Ui.label(this, item.title, w);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setTextColor(Ui.focusColors(Color.WHITE, Ui.TEXT));
        tile.addView(title);
        TextView meta = Ui.label(this, item.meta, w);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        meta.setPadding(Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2), 0);
        tile.addView(meta);

        Ui.scaleOnFocus(tile, card);
        tile.setOnClickListener(v -> Plex.open(this, item));
        tile.setOnKeyListener((v, keyCode, event) -> onMenuKey(keyCode, event, () -> refreshPlex(true)));
        Plex.loadImage(item.imageUrl, image);
        return tile;
    }

    // ------------------------------------------------------------------ helpers

    /** Move the gliding outline to the newly selected tile, or hide it for buttons. */
    private void onFocusMoved(View v) {
        Object tag = v != null ? v.getTag() : null;
        if (tag instanceof String && (((String) tag).startsWith("app:") || ((String) tag).startsWith("plex:")
                || tag.equals("choose")) && v instanceof ViewGroup && ((ViewGroup) v).getChildCount() > 0) {
            ring.moveTo(v, ((ViewGroup) v).getChildAt(0));
        } else {
            ring.hide();
        }
    }

    private boolean onMenuKey(int keyCode, KeyEvent event, Runnable action) {
        if (keyCode != KeyEvent.KEYCODE_MENU) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) action.run();
        return true;
    }

    private static boolean isInside(View v, ViewGroup parent) {
        ViewParent p = v.getParent();
        while (p != null) {
            if (p == parent) return true;
            p = p.getParent();
        }
        return false;
    }

    private void dialog(String title, List<String> labels, final List<Runnable> actions) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }
}
