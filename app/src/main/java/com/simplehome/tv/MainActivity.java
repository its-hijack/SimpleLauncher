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
 * The home screen: logo and clock, three buttons, then your apps and Continue Watching.
 * Default layout: one row of apps with Continue Watching underneath.
 * Grid layout: Continue Watching on top, with your apps in a grid underneath.
 */
public class MainActivity extends Activity {

    private static final long WATCH_REFRESH_MS = 60_000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Apps.Entry> byPkg = new HashMap<>();
    private int loadGeneration = 0;

    private View root;
    private TextClock clock;
    private TextClock ampm;
    private TextClock date;
    private LinearLayout body;
    private View appsSection;
    private View appsScroll;
    private LinearLayout appsRow;
    private LinearLayout appsGrid;
    private View plexSection;
    private View plexScroll;
    private LinearLayout plexRow;
    private TextView plexStatus;
    private TextView watchLabel;

    private boolean watchLoading;
    private boolean grid;
    private String layoutKey;   // layout and sizes last drawn, to redraw when they change

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
        body = findViewById(R.id.body);
        appsSection = findViewById(R.id.apps_section);
        appsScroll = findViewById(R.id.apps_scroll);
        appsRow = findViewById(R.id.apps_row);
        appsGrid = findViewById(R.id.apps_grid);
        watchLabel = findViewById(R.id.watch_label);
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
        refreshWatching(false);
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
        plexSection.setVisibility(Watching.shown(this) ? View.VISIBLE : View.GONE);
        watchLabel.setText(Watching.title(this));
        ((TextView) findViewById(R.id.btn_amazon)).setText(Mode.homeButtonLabel(this));

        // Layout: Grid puts Continue Watching first and the apps in a grid underneath.
        grid = Prefs.gridLayout(this);
        String key = grid + "/" + Prefs.appSize(this) + "/" + Prefs.cardSize(this);
        if (!key.equals(layoutKey)) {
            layoutKey = key;
            body.removeView(appsSection);
            body.removeView(plexSection);
            body.addView(grid ? plexSection : appsSection);
            body.addView(grid ? appsSection : plexSection);
            setTopMargin(grid ? plexSection : appsSection, grid ? 26 : 50);
            setTopMargin(grid ? appsSection : plexSection, grid ? 14 : 18);
            appsScroll.setVisibility(grid ? View.GONE : View.VISIBLE);
            appsGrid.setVisibility(grid ? View.VISIBLE : View.GONE);
            if (!byPkg.isEmpty()) renderApps();   // otherwise the app list is still loading
            if (Plex.cache != null || Jellyfin.cache != null) renderWatching(cards());
        }
    }

    private void setTopMargin(View v, int dp) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.topMargin = Ui.dp(this, dp);
        v.setLayoutParams(lp);
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
        Object tag = focused != null && (isInside(focused, appsRow) || isInside(focused, appsGrid))
                ? focused.getTag() : savedFocusTag;

        int w = Watching.appTileWidth(grid, Prefs.appSize(this));
        List<View> tiles = new ArrayList<>();
        for (String pkg : visibleFavourites()) {
            final Apps.Entry e = byPkg.get(pkg);
            if (e == null) continue;
            Ui.Tile t = Ui.appTile(this, e, w);
            t.card.setBackground(Ui.plainCardBackground(this));
            t.root.setTag("app:" + pkg);
            t.root.setOnClickListener(v -> Apps.launch(this, e));
            t.root.setOnLongClickListener(v -> {
                showAppOptions(e);
                return true;
            });
            t.root.setOnKeyListener((v, keyCode, event) -> onMenuKey(keyCode, event, () -> showAppOptions(e)));
            tiles.add(t.root);
        }
        if (tiles.isEmpty()) {
            Ui.Tile t = Ui.textTile(this, "+", "Choose your apps", w);
            t.card.setBackground(Ui.plainCardBackground(this));
            t.root.setTag("choose");
            t.root.setOnClickListener(v -> Screen.open(this, new Intent(this, AppSelectionActivity.class)));
            tiles.add(t.root);
        }

        appsRow.removeAllViews();
        appsGrid.removeAllViews();
        if (grid) {
            // As many across as fit the screen at this size.
            float widthDp = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density;
            int across = Math.max(1, (int) ((widthDp - 84 - 12 + Ui.GAP) / (w + Ui.GAP)));
            LinearLayout line = null;
            for (int i = 0; i < tiles.size(); i++) {
                if (i % across == 0) {
                    line = new LinearLayout(this);
                    line.setOrientation(LinearLayout.HORIZONTAL);
                    line.setClipChildren(false);
                    line.setClipToPadding(false);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.setMargins(0, i == 0 ? 0 : Ui.dp(this, 12), 0, 0);
                    appsGrid.addView(line, lp);
                }
                line.addView(tiles.get(i), Ui.rowItemParams(this));
            }
        } else {
            for (View t : tiles) appsRow.addView(t, Ui.rowItemParams(this));
        }

        ViewGroup holder = grid ? appsGrid : appsRow;
        View target = tag != null ? holder.findViewWithTag(tag) : null;
        if (target != null) {
            target.requestFocus();
        } else if (firstRender || getCurrentFocus() == null || (tag != null && tag.toString().startsWith("app:"))) {
            focusFirstApp();
        }
        firstRender = false;
        savedFocusTag = null;
    }

    private View firstTile() {
        if (grid) {
            return appsGrid.getChildCount() > 0 ? ((ViewGroup) appsGrid.getChildAt(0)).getChildAt(0) : null;
        }
        return appsRow.getChildCount() > 0 ? appsRow.getChildAt(0) : null;
    }

    private void focusFirstApp() {
        View first = firstTile();
        if (appsSection.getVisibility() == View.VISIBLE && first != null) {
            first.requestFocus();
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
            labels.add(grid ? "Move earlier" : "Move left");
            actions.add(() -> moveFavourite(e.pkg, -1));
        }
        if (index >= 0 && index < visible.size() - 1) {
            labels.add(grid ? "Move later" : "Move right");
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
        View moved = (grid ? appsGrid : appsRow).findViewWithTag("app:" + pkg);
        if (moved != null) moved.requestFocus();
    }

    // ------------------------------------------------------------------ Continue Watching (Plex and Jellyfin)

    private List<Watching.Card> cards() {
        return Watching.merge(Watching.plexOn(this) ? Plex.cache : null,
                Watching.jellyfinOn(this) ? Jellyfin.cache : null);
    }

    private void refreshWatching(boolean force) {
        if (!Watching.shown(this)) return;
        final boolean plex = Watching.plexOn(this), jf = Watching.jellyfinOn(this);
        if (!plex && !jf) {
            showPlexStatus(Watching.emptyMessage(this));
            return;
        }
        if (plex && Plex.cache == null) Plex.cache = Plex.loadSaved(this);
        if (jf && Jellyfin.cache == null) Jellyfin.cache = Jellyfin.loadSaved(this);
        boolean haveSaved = (plex && Plex.cache != null) || (jf && Jellyfin.cache != null);
        if (haveSaved) {
            renderWatching(cards());
        } else {
            showPlexStatus("Loading\u2026");
        }
        if (watchLoading) return;
        boolean fresh = (!plex || (Plex.cache != null && SystemClock.elapsedRealtime() - Plex.cacheTime < WATCH_REFRESH_MS))
                && (!jf || (Jellyfin.cache != null && SystemClock.elapsedRealtime() - Jellyfin.cacheTime < WATCH_REFRESH_MS));
        if (!force && fresh) return;

        watchLoading = true;
        final Context app = getApplicationContext();
        new Thread(() -> {
            final Plex.Result pr = plex ? Plex.continueWatching(app) : null;
            final Jellyfin.Result jr = jf ? Jellyfin.continueWatching(app) : null;
            main.post(() -> {
                watchLoading = false;
                if (isDestroyed()) return;
                long now = SystemClock.elapsedRealtime();
                if (pr != null && !(pr.error != null && pr.items.isEmpty())) {
                    Plex.cache = pr.items;
                    Plex.cacheTime = now;
                }
                if (jr != null && !(jr.error != null && jr.items.isEmpty())) {
                    Jellyfin.cache = jr.items;
                    Jellyfin.cacheTime = now;
                }
                List<Watching.Card> list = cards();
                if (list.isEmpty()) {
                    String error = pr != null && pr.error != null ? pr.error : jr != null ? jr.error : null;
                    boolean anyCache = (plex && Plex.cache != null) || (jf && Jellyfin.cache != null);
                    showPlexStatus(error != null && !anyCache ? error : "Nothing in progress right now.");
                    return;
                }
                renderWatching(list);
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

    private void renderWatching(List<Watching.Card> items) {
        watchLabel.setText(Watching.title(this));
        if (items.isEmpty()) {
            showPlexStatus(Watching.emptyMessage(this));
            return;
        }
        View focused = getCurrentFocus();
        Object tag = focused != null && isInside(focused, plexRow) ? focused.getTag() : savedFocusTag;

        plexStatus.setVisibility(View.GONE);
        plexScroll.setVisibility(View.VISIBLE);
        plexRow.removeAllViews();
        for (Watching.Card item : items) {
            plexRow.addView(watchCard(item), Ui.rowItemParams(this));
        }

        if (tag != null && (tag.toString().startsWith("plex:") || tag.toString().startsWith("jf:"))) {
            View target = plexRow.findViewWithTag(tag);
            if (target == null) target = plexRow.getChildAt(0);
            target.requestFocus();
        }
    }

    private View watchCard(final Watching.Card item) {
        final int w = Watching.cardWidth(Prefs.cardSize(this));
        final int h = Math.round(w * 110 / 195f);
        LinearLayout tile = Ui.baseTile(this);
        tile.setTag(item.key);

        FrameLayout card = Ui.card(this, w, h);
        card.setBackground(Ui.plainCardBackground(this));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Ui.CARD);
        card.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // PLEX or JELLYFIN in the corner
        TextView badge = new TextView(this);
        badge.setText("plex".equals(item.source) ? "PLEX" : "JELLYFIN");
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        badge.setLetterSpacing(0.08f);
        badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        badge.setTextColor(0xFF0C0F15);
        badge.setBackground(Ui.rounded(this, 0xD9FFFFFF, 3, 0, 0));
        badge.setPadding(Ui.dp(this, 5), Ui.dp(this, 1), Ui.dp(this, 5), Ui.dp(this, 1));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
        bl.setMargins(Ui.dp(this, 6), Ui.dp(this, 6), 0, 0);
        card.addView(badge, bl);

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
        tile.setOnClickListener(v -> Watching.open(this, item));
        tile.setOnKeyListener((v, keyCode, event) -> onMenuKey(keyCode, event, () -> refreshWatching(true)));
        Plex.loadImage(item.imageUrl, image);
        return tile;
    }

    // ------------------------------------------------------------------ helpers

    /** Move the gliding outline to the newly selected tile, or hide it for buttons. */
    private void onFocusMoved(View v) {
        Object tag = v != null ? v.getTag() : null;
        if (tag instanceof String && (((String) tag).startsWith("app:") || ((String) tag).startsWith("plex:")
                || ((String) tag).startsWith("jf:") || tag.equals("choose")) && v instanceof ViewGroup && ((ViewGroup) v).getChildCount() > 0) {
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
