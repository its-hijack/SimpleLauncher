package com.simplehome.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * First-time setup: Welcome (with the detected device and mode), Pick your apps,
 * Make it yours, Connect Plex (only if Plex is installed), and All set.
 * Runs on first launch and once after updating; can be re-run from Settings → Shortcuts.
 */
public class SetupActivity extends Screen {

    private static final int WELCOME = 0, APPS = 1, LOOK = 2, PLEX = 3, DONE = 4;

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout root;
    private FrameLayout topBox;      // step bar (hidden on Welcome)
    private FrameLayout stage;       // the screens slide across in here
    private FrameLayout content;     // the screen being built / shown
    private LinearLayout buttons;
    private int step = WELCOME;
    private int plexGeneration = 0;   // stops an old Plex sign-in loop when you leave that step

    // Pick your apps
    private List<Apps.Entry> apps;
    private final Map<String, Ui.Tile> tiles = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 42), Ui.dp(this, 26), Ui.dp(this, 42), Ui.dp(this, 24));
        root.setClipChildren(false);
        root.setClipToPadding(false);
        topBox = new FrameLayout(this);
        root.addView(topBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        stage = new FrameLayout(this);
        stage.setClipChildren(false);
        root.addView(stage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);

        final Context app = getApplicationContext();
        new Thread(() -> {
            final List<Apps.Entry> list = Apps.load(app);
            main.post(() -> {
                apps = list;
                if (step == APPS && !isDestroyed()) show(APPS);
            });
        }).start();
        show(WELCOME);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back from the background or accent picker: refresh the preview.
        if (step == LOOK) show(LOOK);
    }

    @Override
    protected void onDestroy() {
        plexGeneration++;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (step == WELCOME) return;           // nothing before Welcome
        show(previous(step), -1);
    }

    // ------------------------------------------------------------------ steps

    private boolean plexStep() {
        return Plex.installed(this);
    }

    private int next(int s) {
        if (s == LOOK && !plexStep()) return DONE;
        return Math.min(DONE, s + 1);
    }

    private int previous(int s) {
        if (s == DONE && !plexStep()) return LOOK;
        return Math.max(WELCOME, s - 1);
    }

    private int totalSteps() {
        return plexStep() ? 4 : 3;
    }

    private int stepNumber(int s) {
        if (s == DONE) return totalSteps();
        return s;   // APPS = 1, LOOK = 2, PLEX = 3
    }

    private void show(int s) {
        show(s, 0);
    }

    /** dir: 1 = forward (new screen comes in from the right), -1 = back, 0 = no animation. */
    private void show(int s, int dir) {
        plexGeneration++;   // leaving the Plex step cancels its sign-in loop
        step = s;
        int bg = Prefs.background(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(bg));
        root.setBackgroundColor(bg);

        topBox.removeAllViews();
        if (s != WELCOME) topBox.addView(topBar(s));

        final View old = content;
        content = new FrameLayout(this);
        content.setClipChildren(false);
        stage.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        buttons.removeAllViews();
        buttons.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

        switch (s) {
            case WELCOME: welcome(); break;
            case APPS: pickApps(); break;
            case LOOK: look(); break;
            case PLEX: plex(); break;
            default: done(); break;
        }

        if (old == null) return;
        if (old instanceof ViewGroup) ((ViewGroup) old).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (dir == 0) {
            stage.removeView(old);
            return;
        }
        // The wipe: the new screen slides in while the old one slides away and fades.
        float width = stage.getWidth() > 0 ? stage.getWidth() : getResources().getDisplayMetrics().widthPixels;
        content.setTranslationX(dir * width);
        content.setAlpha(0f);
        content.animate().translationX(0f).alpha(1f).setDuration(550)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2.2f)).start();
        old.animate().translationX(-dir * width * 0.35f).alpha(0f).setDuration(450)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f))
                .withEndAction(() -> stage.removeView(old)).start();
    }

    // ------------------------------------------------------------------ Welcome

    private void welcome() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        logo.setAdjustViewBounds(true);
        box.addView(logo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 100)));

        TextView title = text("Welcome to SimpleLauncher", 32, Color.WHITE);
        title.setPadding(0, Ui.dp(this, 16), 0, 0);
        box.addView(title);
        TextView maker = text("by Cherry Universe", 13, 0xFF8A93A6);
        maker.setLetterSpacing(0.1f);
        maker.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 8));
        box.addView(maker);
        TextView sub = text("A clean, simple home screen. Setup takes about a minute.", 15, Ui.TEXT_DIM);
        box.addView(sub);

        // What we detected
        boolean fire = Mode.fireTv(this);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(this, 0xFF151922, 10, 0, 0));
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
        card.addView(text(Mode.detected(this) + " detected", 15, Color.WHITE));
        card.addView(text(fire
                ? "SimpleLauncher will run in Fire TV mode, with the Home-button catcher and the Amazon Home button."
                : "SimpleLauncher will run in generic mode, with a Return home button for your device's own home screen.",
                13, Ui.TEXT_DIM));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, Ui.dp(this, 22), 0, 0);
        box.addView(card, clp);

        content.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buttons.setGravity(Gravity.CENTER);
        addButton(fire ? "Use generic mode instead" : "Use Fire TV mode instead", false, () -> {
            Mode.setOverride(this, fire ? Mode.GENERIC : Mode.FIRE_TV);
            show(WELCOME);
        });
        View start = addButton("Get started", true, () -> show(APPS, 1));
        start.requestFocus();
    }

    // ------------------------------------------------------------------ Pick your apps

    private void pickApps() {
        LinearLayout box = column();
        box.addView(heading("Pick your apps",
                "Select the apps you want across the middle of your home screen. The numbers show their order. You can change this any time in App Selection."));

        if (apps == null) {
            box.addView(text("Finding your apps…", 15, Ui.TEXT_DIM));
        } else {
            ScrollView scroll = new ScrollView(this);
            scroll.setClipChildren(false);
            scroll.setClipToPadding(false);
            scroll.setVerticalScrollBarEnabled(false);
            GridLayout grid = new GridLayout(this);
            grid.setColumnCount(5);
            grid.setClipChildren(false);
            grid.setClipToPadding(false);
            grid.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), 0, 0);
            tiles.clear();
            for (final Apps.Entry e : apps) {
                Ui.Tile t = Ui.appTile(this, e);
                Ui.addBadge(this, t);
                t.root.setOnClickListener(v -> {
                    List<String> favs = Prefs.favourites(this);
                    if (favs.contains(e.pkg)) favs.remove(e.pkg); else favs.add(e.pkg);
                    Prefs.setFavourites(this, favs);
                    updateBadges();
                });
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = Ui.dp(this, Ui.TILE_W);
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                lp.setMargins(0, 0, Ui.dp(this, Ui.GAP), Ui.dp(this, 12));
                grid.addView(t.root, lp);
                tiles.put(e.pkg, t);
            }
            scroll.addView(grid);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            slp.setMargins(0, Ui.dp(this, 12), 0, 0);
            box.addView(scroll, slp);
            updateBadges();
            if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
        }
        content.addView(box);
        addButton("Back", false, () -> show(WELCOME, -1));
        View next = addButton("Continue", true, () -> show(next(APPS), 1));
        if (apps == null) next.requestFocus();
    }

    private void updateBadges() {
        List<String> order = new ArrayList<>();
        for (String pkg : Prefs.favourites(this)) if (tiles.containsKey(pkg)) order.add(pkg);
        for (Map.Entry<String, Ui.Tile> entry : tiles.entrySet()) {
            int index = order.indexOf(entry.getKey());
            entry.getValue().badge.setVisibility(index >= 0 ? View.VISIBLE : View.GONE);
            entry.getValue().badge.setText(String.valueOf(index + 1));
        }
    }

    // ------------------------------------------------------------------ Make it yours

    private void look() {
        LinearLayout box = column();
        box.addView(heading("Make it yours",
                "Pick a background, an accent colour and how the clock looks. More options are in Settings → Appearance."));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        View first = option("Background", colourName(Ui.BG_NAMES, Ui.BG_COLORS, Prefs.background(this)),
                () -> Screen.open(this, new Intent(this, BackgroundPickerActivity.class)));
        options.addView(first);
        options.addView(option("Accent colour", colourName(Ui.ACCENT_NAMES, Ui.ACCENT_COLORS, Prefs.accent(this)),
                () -> Screen.open(this, new Intent(this, AccentPickerActivity.class))));
        final boolean clock24 = Prefs.clock24(this);
        options.addView(option("Clock", clock24 ? "24-hour" : "12-hour", () -> {
            Prefs.setBool(this, Prefs.CLOCK_24, !clock24);
            show(LOOK);
            View again = content.findViewWithTag("Clock");
            if (again != null) again.requestFocus();
        }));
        row.addView(options, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Preview of the home screen
        LinearLayout previewBox = new LinearLayout(this);
        previewBox.setOrientation(LinearLayout.VERTICAL);
        previewBox.addView(text("PREVIEW", 11, 0xFF8A93A6));
        FrameLayout screen = new FrameLayout(this);
        screen.setBackground(Ui.rounded(this, Prefs.background(this), 10, 1, 0xFF2A3040));
        screen.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        screen.addView(BackgroundPickerActivity.homePreview(this, Prefs.accent(this)));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(Ui.dp(this, 280), Ui.dp(this, 158));
        sp.setMargins(0, Ui.dp(this, 6), 0, 0);
        previewBox.addView(screen, sp);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.setMargins(Ui.dp(this, 32), 0, 0, 0);
        row.addView(previewBox, pp);

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, Ui.dp(this, 18), 0, 0);
        box.addView(row, rlp);
        content.addView(box);

        addButton("Back", false, () -> show(APPS, -1));
        addButton("Continue", true, () -> show(next(LOOK), 1));
        first.requestFocus();
    }

    private View option(String label, String value, Runnable onClick) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(this, 46));
        r.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        r.setBackground(Ui.rowBackground(this));
        r.setFocusable(true);
        r.setClickable(true);
        r.setTag(label);
        TextView l = text(label, 15, 0);
        l.setTextColor(Ui.focusColors(0xFF0C0F15, Ui.TEXT));
        l.setDuplicateParentStateEnabled(true);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = text(value, 14, 0);
        v.setTextColor(Ui.focusColors(0xFF0C0F15, Ui.TEXT_DIM));
        v.setDuplicateParentStateEnabled(true);
        r.addView(v);
        r.setOnClickListener(x -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Ui.dp(this, 6));
        r.setLayoutParams(lp);
        return r;
    }

    private static String colourName(String[] names, int[] colours, int value) {
        for (int i = 0; i < colours.length; i++) if (colours[i] == value) return names[i];
        return "Custom";
    }

    // ------------------------------------------------------------------ Connect Plex

    private void plex() {
        LinearLayout box = column();
        box.addView(heading("Connect Plex (optional)",
                "Sign in to show the shows and films you're part-way through in a Continue Watching row. Tip: turn on automatic sign-in in the Plex app so shows open straight away."));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(Ui.rounded(this, 0xFF151922, 12, 0, 0));
        card.setPadding(Ui.dp(this, 24), Ui.dp(this, 18), Ui.dp(this, 24), Ui.dp(this, 18));
        final TextView info = text("", 16, Color.WHITE);
        info.setGravity(Gravity.CENTER);
        final TextView code = text("", 44, Color.WHITE);
        code.setLetterSpacing(0.25f);
        code.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));
        final TextView status = text("", 13, Ui.TEXT_DIM);
        card.addView(info);
        card.addView(code);
        card.addView(status);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, Ui.dp(this, 20), 0, 0);
        box.addView(card, clp);
        content.addView(box);

        addButton("Back", false, () -> show(LOOK, -1));
        String user = Prefs.plexUser(this);
        if (Prefs.plexToken(this) != null) {
            info.setText("You're signed in to Plex" + (user != null ? " as " + user : "") + ".");
            code.setVisibility(View.GONE);
            addButton("Continue", true, () -> show(DONE, 1)).requestFocus();
            return;
        }
        addButton("Skip for now", true, () -> show(DONE, 1)).requestFocus();
        info.setText("Getting a code from Plex…");
        startPlexSignIn(info, code, status);
    }

    private void startPlexSignIn(final TextView info, final TextView code, final TextView status) {
        final int generation = plexGeneration;
        final Context app = getApplicationContext();
        new Thread(() -> {
            try {
                final Plex.Pin pin = Plex.createPin(app);
                main.post(() -> {
                    if (generation != plexGeneration) return;
                    info.setText("On your phone or computer, go to plex.tv/link and enter this code:");
                    code.setText(pin.code);
                    status.setText("Waiting for you to enter the code…");
                });
                long deadline = SystemClock.elapsedRealtime() + 15 * 60_000;
                while (generation == plexGeneration && SystemClock.elapsedRealtime() < deadline) {
                    Thread.sleep(2000);
                    String token = Plex.checkPin(app, pin.id);
                    if (token != null) {
                        Prefs.setPlex(app, token, Plex.username(app, token));
                        Plex.clearCache();
                        main.post(() -> {
                            if (generation == plexGeneration) show(DONE, 1);
                        });
                        return;
                    }
                }
                main.post(() -> {
                    if (generation == plexGeneration) status.setText("That code expired. Go back and forward again to get a new one.");
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (generation == plexGeneration) info.setText("Couldn't reach Plex. You can sign in later in Settings → Plex.");
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------ All set

    private void done() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        ImageView tick = new ImageView(this);
        tick.setImageResource(R.drawable.ic_check);
        tick.setScaleType(ImageView.ScaleType.FIT_CENTER);
        android.graphics.drawable.GradientDrawable green = new android.graphics.drawable.GradientDrawable();
        green.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        green.setColor(0xFF22C55E);
        tick.setBackground(green);
        int tp = Ui.dp(this, 14);
        tick.setPadding(tp, tp, tp, tp);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56));
        tlp.setMargins(0, 0, 0, Ui.dp(this, 14));
        left.addView(tick, tlp);
        tick.setScaleX(0.2f);
        tick.setScaleY(0.2f);
        tick.animate().scaleX(1f).scaleY(1f).setStartDelay(250).setDuration(500)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
        TextView big = text("You're all set", 32, Color.WHITE);
        left.addView(big);
        TextView sub = text("Everything can be changed later in Settings. To run setup again, go to Settings → Shortcuts → Run setup again.", 15, Ui.TEXT_DIM);
        sub.setPadding(0, Ui.dp(this, 8), Ui.dp(this, 24), 0);
        left.addView(sub);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int appCount = 0;
        for (String pkg : Prefs.favourites(this)) if (Mode.isInstalled(this, pkg)) appCount++;
        String plexLine = !Plex.installed(this) ? "Plex isn't installed"
                : Prefs.plexToken(this) != null ? "Signed in" + (Prefs.plexUser(this) != null ? " as " + Prefs.plexUser(this) : "")
                : "Not signed in (Settings → Plex)";
        String homeLine;
        if (Mode.fireTv(this)) {
            homeLine = HomeButtonService.isEnabled(this) && Prefs.hook(this) ? "Opens SimpleLauncher"
                    : "Not set up: run firestick.sh on a computer, option 3";
        } else {
            homeLine = Mode.isDefaultHome(this) ? "SimpleLauncher is your home app" : "Choose it in Settings → Home app";
        }

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setBackground(Ui.rounded(this, 0xFF151922, 12, 0, 0));
        summary.setPadding(Ui.dp(this, 18), Ui.dp(this, 6), Ui.dp(this, 18), Ui.dp(this, 6));
        String[][] lines = {
                {"Mode", Mode.name(this) + " · " + Mode.detected(this)},
                {"Your apps", appCount + (appCount == 1 ? " app" : " apps") + " on your home screen"},
                {"Look", colourName(Ui.BG_NAMES, Ui.BG_COLORS, Prefs.background(this)) + " · "
                        + colourName(Ui.ACCENT_NAMES, Ui.ACCENT_COLORS, Prefs.accent(this)) + " · "
                        + (Prefs.clock24(this) ? "24-hour clock" : "12-hour clock")},
                {"Plex", plexLine},
                {Mode.fireTv(this) ? "Home button" : "Home app", homeLine}};
        for (int i = 0; i < lines.length; i++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setMinimumHeight(Ui.dp(this, 32));
            line.addView(text(lines[i][0], 13, Ui.TEXT_DIM), new LinearLayout.LayoutParams(Ui.dp(this, 100), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView v = text(lines[i][1], 13, Ui.TEXT);
            v.setGravity(Gravity.RIGHT);
            line.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            summary.addView(line);
            if (i < lines.length - 1) {
                View d = new View(this);
                d.setBackgroundColor(0xFF232836);
                summary.addView(d, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            }
        }
        row.addView(summary, new LinearLayout.LayoutParams(Ui.dp(this, 400), ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        addButton("Back", false, () -> show(previous(DONE), -1));
        addButton("Go to my home screen", true, () -> {
            Prefs.setBool(this, Prefs.SETUP_DONE, true);
            Intent home = new Intent(this, MainActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
            finish();
        }).requestFocus();
    }

    // ------------------------------------------------------------------ building blocks

    private View topBar(int s) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        logo.setAdjustViewBounds(true);
        bar.addView(logo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 24)));
        TextView name = text("SimpleLauncher setup", 14, Ui.TEXT);
        name.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        bar.addView(name);
        TextView chip = text(Mode.name(this), 11, Ui.TEXT_DIM);
        chip.setBackground(Ui.rounded(this, 0x14FFFFFF, 10, 0, 0));
        chip.setPadding(Ui.dp(this, 8), Ui.dp(this, 3), Ui.dp(this, 8), Ui.dp(this, 3));
        bar.addView(chip);

        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        int total = totalSteps();
        int n = stepNumber(s);
        TextView count = text("Step " + n + " of " + total, 12, Ui.TEXT_DIM);
        count.setPadding(0, 0, Ui.dp(this, 10), 0);
        bar.addView(count);
        for (int i = 0; i < total; i++) {
            View seg = new View(this);
            seg.setBackground(Ui.rounded(this, i < n ? Ui.accent(this) : 0xFF2A3040, 2, 0, 0));
            boolean currentSeg = i == n - 1;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, currentSeg ? 40 : 26), Ui.dp(this, 3));
            lp.setMargins(Ui.dp(this, 4), 0, 0, 0);
            bar.addView(seg, lp);
            if (currentSeg) {
                seg.setPivotX(0f);
                seg.setScaleX(26f / 40f);
                seg.animate().scaleX(1f).setDuration(450)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
            }
        }
        return bar;
    }

    private LinearLayout column() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        box.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 12));
        return box;
    }

    private View heading(String title, String sub) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.addView(text(title, 26, Color.WHITE));
        TextView s = text(sub, 14, Ui.TEXT_DIM);
        s.setPadding(0, Ui.dp(this, 6), Ui.dp(this, 120), 0);
        s.setLineSpacing(0, 1.25f);
        h.addView(s);
        return h;
    }

    private View addButton(String label, boolean primary, Runnable onClick) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(Ui.dp(this, 38));
        b.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), Ui.dp(this, 8));
        b.setBackgroundResource(R.drawable.pill_bg);
        b.setTextColor(getResources().getColorStateList(R.color.pill_text, getTheme()));
        if (primary) b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(this, 10), 0, 0, 0);
        buttons.addView(b, lp);
        return b;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (color != 0) t.setTextColor(color);
        return t;
    }
}
