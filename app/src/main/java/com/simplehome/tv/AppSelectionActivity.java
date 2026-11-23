package com.simplehome.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Choose which apps appear on the home screen, and in what order. */
public class AppSelectionActivity extends Screen {

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Ui.Tile> tiles = new LinkedHashMap<>();
    private List<Apps.Entry> apps = new ArrayList<>();
    private GridLayout grid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_selection);
        Ui.applyBackground(this, findViewById(R.id.root));
        grid = findViewById(R.id.grid);
        findViewById(R.id.done).setOnClickListener(v -> finish());

        int widthDp = getResources().getConfiguration().screenWidthDp;
        grid.setColumnCount(Math.max(3, (widthDp - 90) / (Ui.TILE_W + Ui.GAP)));

        final Context app = getApplicationContext();
        new Thread(() -> {
            final List<Apps.Entry> list = Apps.load(app);
            main.post(() -> {
                if (isDestroyed()) return;
                apps = list;
                render();
            });
        }).start();
    }

    private void render() {
        grid.removeAllViews();
        tiles.clear();
        for (final Apps.Entry e : apps) {
            Ui.Tile t = Ui.appTile(this, e);
            Ui.addBadge(this, t);
            t.root.setTag("sel:" + e.pkg);
            t.root.setOnClickListener(v -> toggle(e.pkg));
            t.root.setOnLongClickListener(v -> {
                showOptions(e);
                return true;
            });
            t.root.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode != KeyEvent.KEYCODE_MENU) return false;
                if (event.getAction() == KeyEvent.ACTION_UP) showOptions(e);
                return true;
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = Ui.dp(this, Ui.TILE_W);
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.setMargins(0, 0, Ui.dp(this, Ui.GAP), Ui.dp(this, 14));
            grid.addView(t.root, lp);
            tiles.put(e.pkg, t);
        }
        updateBadges();
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
    }

    /** Home-screen apps that are installed, in order. */
    private List<String> selected() {
        List<String> out = new ArrayList<>();
        for (String pkg : Prefs.favourites(this)) {
            if (tiles.containsKey(pkg)) out.add(pkg);
        }
        return out;
    }

    private void updateBadges() {
        List<String> sel = selected();
        for (Map.Entry<String, Ui.Tile> entry : tiles.entrySet()) {
            int index = sel.indexOf(entry.getKey());
            entry.getValue().badge.setVisibility(index >= 0 ? View.VISIBLE : View.GONE);
            entry.getValue().badge.setText(String.valueOf(index + 1));
        }
    }

    private void toggle(String pkg) {
        List<String> favs = Prefs.favourites(this);
        if (favs.contains(pkg)) {
            favs.remove(pkg);
        } else {
            favs.add(pkg);
        }
        Prefs.setFavourites(this, favs);
        updateBadges();
    }

    private void move(String pkg, int direction) {
        List<String> favs = Prefs.favourites(this);
        List<String> sel = selected();
        int from = sel.indexOf(pkg);
        int to = from + direction;
        if (from < 0 || to < 0 || to >= sel.size()) return;
        Collections.swap(favs, favs.indexOf(pkg), favs.indexOf(sel.get(to)));
        Prefs.setFavourites(this, favs);
        updateBadges();
    }

    private void showOptions(final Apps.Entry e) {
        List<String> sel = selected();
        int index = sel.indexOf(e.pkg);
        List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        labels.add("Open " + e.label);
        actions.add(() -> Apps.launch(this, e));
        if (index > 0) {
            labels.add("Move earlier (to " + index + ")");
            actions.add(() -> move(e.pkg, -1));
        }
        if (index >= 0 && index < sel.size() - 1) {
            labels.add("Move later (to " + (index + 2) + ")");
            actions.add(() -> move(e.pkg, 1));
        }
        labels.add(index >= 0 ? "Remove from home screen" : "Add to home screen");
        actions.add(() -> toggle(e.pkg));
        labels.add("App info");
        actions.add(() -> Apps.openAppInfo(this, e.pkg));

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(e.label)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }
}
