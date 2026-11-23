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
 * First-time setup: Welcome (with the detected device and mode), Pick your apps, Make it yours,
 * Connect your services (only if Plex or Jellyfin is installed), Connect Plex and Connect Jellyfin
 * (only the ones you chose), and All set. Can be re-run from Settings › Danger Zone.
 */
public class SetupActivity extends Screen {

    private static final int WELCOME = 0, APPS = 1, LOOK = 2, SERVICES = 3, PLEX = 4, JELLYFIN = 5, DONE = 6;

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout root;
    private FrameLayout topBox;      // step bar (hidden on Welcome)
    private FrameLayout stage;       // the screens slide across in here
    private FrameLayout content;     // the screen being built / shown
    private LinearLayout buttons;
    private int step = WELCOME;
    private int plexGeneration = 0;   // stops an old Plex sign-in loop when you leave that step
    private boolean wantPlex;         // Connect your services: what you chose
    private boolean wantJellyfin;
    private JellyfinLogin jellyfinLogin;
    private boolean redrawing;        // redrawing the same step: no animations

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

        // Connect your services starts on "both" (or whichever one is installed).
        wantPlex = Plex.installed(this);
        wantJellyfin = Jellyfin.installed(this);

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
        if (jellyfinLogin != null) jellyfinLogin.cancel();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (step == WELCOME) return;           // nothing before Welcome
        show(previous(step), -1);
    }

    // ------------------------------------------------------------------ steps

    /** The steps after Welcome, skipping whatever doesn't apply. */
    private List<Integer> steps() {
        List<Integer> list = new java.util.ArrayList<>();
        list.add(APPS);
        list.add(LOOK);
        if (Watching.available(this)) list.add(SERVICES);
        if (wantPlex && Plex.installed(this)) list.add(PLEX);
        if (wantJellyfin && Jellyfin.installed(this)) list.add(JELLYFIN);
        list.add(DONE);
        return list;
    }

    private int next(int s) {
        List<Integer> list = steps();
        int i = list.indexOf(s);
        return i >= 0 && i + 1 < list.size() ? list.get(i + 1) : DONE;
    }

    private int previous(int s) {
        List<Integer> list = steps();
        int i = list.indexOf(s);
        return i > 0 ? list.get(i - 1) : WELCOME;
    }

    private int totalSteps() {
        return steps().size();
    }

    private int stepNumber(int s) {
        return steps().indexOf(s) + 1;
    }

    private void show(int s) {
        show(s, 0);
    }

    /** dir: 1 = forward (new screen comes in from the right), -1 = back, 0 = no animation. */
    private void show(int s, int dir) {
        plexGeneration++;   // leaving the Plex step cancels its sign-in loop
        if (s != JELLYFIN && jellyfinLogin != null) {
            jellyfinLogin.cancel();
            jellyfinLogin = null;
        }
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
            case SERVICES: services(); break;
            case PLEX: plex(); break;
            case JELLYFIN: jellyfin(); break;
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
                ? "SimpleLauncher will run in Fire TV mode, with HomeCatch and the Amazon Home button."
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
        box.addView(heading("Connect Plex",
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

        addButton("Back", false, () -> show(previous(PLEX), -1));
        String user = Prefs.plexUser(this);
        if (Prefs.plexToken(this) != null) {
            info.setText("You're signed in to Plex" + (user != null ? " as " + user : "") + ".");
            code.setVisibility(View.GONE);
            addButton("Continue", true, () -> show(next(PLEX), 1)).requestFocus();
            return;
        }
        addButton("Skip for now", true, () -> show(next(PLEX), 1)).requestFocus();
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
                            if (generation == plexGeneration) show(next(PLEX), 1);
                        });
                        return;
                    }
                }
                main.post(() -> {
                    if (generation == plexGeneration) status.setText("That code expired. Go back and forward again to get a new one.");
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (generation == plexGeneration) info.setText("Couldn't reach Plex. You can sign in later in Settings \u203A External Services.");
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------ Connect your services

    private void services() {
        LinearLayout box = column();
        box.addView(heading("Connect your services",
                "SimpleLauncher can show what you're part-way through on Plex and Jellyfin in a Continue Watching row. Which would you like to connect?"));

        boolean pi = Plex.installed(this), ji = Jellyfin.installed(this);
        final List<String[]> options = new java.util.ArrayList<>();   // id, title, description, marks
        if (pi) options.add(new String[]{"plex", "Plex", "Continue Watching from your Plex server.", "P"});
        if (ji) options.add(new String[]{"jellyfin", "Jellyfin", "Continue Watching from your Jellyfin server.", "J"});
        if (pi && ji) options.add(new String[]{"both", "Both", "Shows from both, mixed together, most recent first.", "PJ"});
        options.add(new String[]{"none", "Neither", "No Continue Watching row. Just your apps.", "\u2013"});

        final TextView nextLine = text("", 14, Ui.TEXT_DIM);
        final List<View> cards = new java.util.ArrayList<>();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setPadding(Ui.dp(this, 4), Ui.dp(this, 22), 0, 0);
        final View[] continueBtn = new View[1];
        for (final String[] o : options) {
            final View card = serviceCard(o[1], o[2], o[3]);
            card.setTag(o[0]);
            card.setOnClickListener(v -> {
                choose(o[0]);
                markChosen(cards, nextLine);
                if (continueBtn[0] != null) continueBtn[0].requestFocus();
            });
            cards.add(card);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 190), Ui.dp(this, 124));
            lp.setMargins(0, 0, Ui.dp(this, 14), 0);
            row.addView(card, lp);
        }
        box.addView(row);
        nextLine.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), 0, 0);
        box.addView(nextLine);
        TextView note = text("You can connect or disconnect them later in Settings \u203A External Services.", 12, 0xFF8A93A6);
        note.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), 0, 0);
        box.addView(note);
        content.addView(box);
        markChosen(cards, nextLine);

        addButton("Back", false, () -> show(previous(SERVICES), -1));
        continueBtn[0] = addButton("Continue", true, () -> show(next(SERVICES), 1));
        View chosen = null;
        for (View c : cards) if (c.isSelected()) chosen = c;
        (chosen != null ? chosen : cards.get(0)).requestFocus();
    }

    private void choose(String id) {
        boolean pi = Plex.installed(this), ji = Jellyfin.installed(this);
        wantPlex = pi && ("plex".equals(id) || "both".equals(id));
        wantJellyfin = ji && ("jellyfin".equals(id) || "both".equals(id));
        // With only one app installed, its card means "that one".
    }

    private String chosenId() {
        if (wantPlex && wantJellyfin) return "both";
        if (wantPlex) return "plex";
        if (wantJellyfin) return "jellyfin";
        return "none";
    }

    private void markChosen(List<View> cards, TextView nextLine) {
        String id = chosenId();
        for (View c : cards) c.setSelected(id.equals(c.getTag()));
        String line;
        if (wantPlex && wantJellyfin) line = "Next: Connect Plex, then Connect Jellyfin.";
        else if (wantPlex) line = "Next: Connect Plex, then you're all set.";
        else if (wantJellyfin) line = "Next: Connect Jellyfin, then you're all set.";
        else line = "Next: you're all set. No services to connect.";
        nextLine.setText(line);
        // The step counter changes with the choice.
        topBox.removeAllViews();
        redrawing = true;
        topBox.addView(topBar(SERVICES));
        redrawing = false;
    }

    /** One choice: white when highlighted, with an accent ring when it's the one chosen. */
    private View serviceCard(String title, String description, String marks) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 12));
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, Ui.rounded(this, Color.WHITE, 12, 0, 0));
        bg.addState(new int[]{android.R.attr.state_selected}, Ui.rounded(this, 0xFF151922, 12, 3, Ui.accent(this)));
        bg.addState(new int[]{}, Ui.rounded(this, 0xFF151922, 12, 0, 0));
        card.setBackground(bg);
        card.setFocusable(true);
        card.setClickable(true);

        LinearLayout markRow = new LinearLayout(this);
        markRow.setOrientation(LinearLayout.HORIZONTAL);
        markRow.setDuplicateParentStateEnabled(true);
        for (char m : marks.toCharArray()) {
            TextView t = text(String.valueOf(m), 13, Color.WHITE);
            t.setGravity(Gravity.CENTER);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setBackground(Ui.rounded(this, 0xFF2A3040, 7, 0, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26));
            lp.setMargins(0, 0, Ui.dp(this, 6), 0);
            markRow.addView(t, lp);
        }
        card.addView(markRow);
        TextView t = text(title, 16, 0);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextColor(Ui.focusColors(0xFF0C0F15, Color.WHITE));
        t.setDuplicateParentStateEnabled(true);
        t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
        card.addView(t);
        TextView d = text(description, 12, 0);
        d.setTextColor(Ui.focusColors(0xFF4A5160, Ui.TEXT_DIM));
        d.setDuplicateParentStateEnabled(true);
        d.setLineSpacing(0, 1.15f);
        card.addView(d);
        return card;
    }

    // ------------------------------------------------------------------ Connect Jellyfin

    private void jellyfin() {
        LinearLayout box = column();
        box.addView(heading("Connect Jellyfin",
                Jellyfin.signedIn(this) ? "You're ready to go."
                        : "Choose your Jellyfin server, then sign in to show what you're part-way through in Continue Watching. "
                        + "Selecting a Jellyfin show opens the Jellyfin app, where it's first in Continue Watching."));
        content.addView(box);
        addButton("Back", false, () -> show(previous(JELLYFIN), -1));

        if (Jellyfin.signedIn(this)) {
            String user = Jellyfin.user(this);
            box.addView(panel("You're signed in to Jellyfin" + (user != null ? " as " + user : "")
                    + " on " + Jellyfin.serverName(this) + "."));
            addButton("Continue", true, () -> show(next(JELLYFIN), 1)).requestFocus();
            return;
        }
        if (jellyfinLogin == null) {
            jellyfinLogin = new JellyfinLogin(this, new JellyfinLogin.Listener() {
                @Override
                public void changed() {
                    if (step == JELLYFIN && !isDestroyed()) redraw();
                }

                @Override
                public void signedIn() {
                    Jellyfin.clearCache();
                    if (step == JELLYFIN && !isDestroyed()) show(next(JELLYFIN), 1);
                }
            });
            jellyfinLogin.search();   // redraws when it has news
        }
        final JellyfinLogin login = jellyfinLogin;
        View focus = null;
        switch (login.stage) {
            case SEARCHING:
                box.addView(panel("Looking for Jellyfin servers on your network\u2026"));
                break;
            case SERVERS: {
                LinearLayout list = card("SERVERS FOUND ON YOUR NETWORK");
                if (login.servers.isEmpty()) list.addView(text("None found. Check the server is on, or type its address.", 14, Ui.TEXT_DIM));
                for (final Jellyfin.Server srv : login.servers) {
                    View r = choiceRow(srv.name, srv.address.replaceFirst("^https?://", ""), () -> login.choose(srv));
                    list.addView(r);
                    if (focus == null) focus = r;
                }
                View type = choiceRow("Type the server's address", "If yours isn't listed, e.g. 192.168.1.10:8096", login::typeAddress);
                list.addView(type);
                list.addView(choiceRow("Search again", null, login::search));
                if (focus == null) focus = type;
                box.addView(list);
                break;
            }
            case CONNECTING:
                box.addView(panel(login.message));
                break;
            case CODE: {
                LinearLayout c = card(null);
                if (login.code != null) {
                    c.addView(text("Signing in to " + login.server.name, 13, Ui.TEXT_DIM));
                    TextView how = text("On your phone or computer, open Jellyfin and go to your profile \u203A Quick Connect, then enter this code:", 16, Color.WHITE);
                    how.setPadding(0, Ui.dp(this, 8), 0, 0);
                    c.addView(how);
                    TextView digits = text(login.code.code, 44, Color.WHITE);
                    digits.setLetterSpacing(0.3f);
                    digits.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
                    c.addView(digits);
                    c.addView(text("Waiting for you to approve it\u2026", 13, Ui.TEXT_DIM));
                } else {
                    c.addView(text("Sign in to " + login.server.name + " with your username and password.", 16, Color.WHITE));
                }
                box.addView(c);
                focus = addButton(login.code != null ? "Use a password instead" : "Sign in with a password", false,
                        () -> login.askPassword("Sign in with your Jellyfin username and password."));
                break;
            }
            case ERROR:
                box.addView(panel(login.message));
                focus = addButton("Try again", false, () -> {
                    if (login.server != null) login.choose(login.server);
                    else login.search();
                });
                break;
            case DONE:
                break;
        }
        View skip = addButton("Skip for now", focus == null, () -> show(next(JELLYFIN), 1));
        (focus != null ? focus : skip).requestFocus();
    }

    /** Same step, new contents (no slide). */
    private void redraw() {
        redrawing = true;
        show(step, 0);
        redrawing = false;
    }

    private LinearLayout card(String label) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.rounded(this, 0xFF151922, 12, 0, 0));
        c.setPadding(Ui.dp(this, 22), Ui.dp(this, 16), Ui.dp(this, 22), Ui.dp(this, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 20), Ui.dp(this, 200), 0);
        c.setLayoutParams(lp);
        if (label != null) {
            TextView l = text(label, 11, 0xFF8A93A6);
            l.setLetterSpacing(0.15f);
            l.setPadding(0, 0, 0, Ui.dp(this, 6));
            c.addView(l);
        }
        return c;
    }

    private View panel(String message) {
        LinearLayout c = card(null);
        c.addView(text(message, 16, Color.WHITE));
        return c;
    }

    private View choiceRow(String title, String sub, Runnable onClick) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setBackground(Ui.rowBackground(this));
        r.setPadding(Ui.dp(this, 16), Ui.dp(this, 9), Ui.dp(this, 16), Ui.dp(this, 9));
        r.setFocusable(true);
        r.setClickable(true);
        r.setOnClickListener(v -> onClick.run());
        TextView t = text(title, 15, 0);
        t.setTextColor(Ui.focusColors(0xFF0C0F15, Ui.TEXT));
        t.setDuplicateParentStateEnabled(true);
        r.addView(t);
        if (sub != null) {
            TextView s = text(sub, 12, 0);
            s.setTextColor(Ui.focusColors(0xFF4A5160, Ui.TEXT_DIM));
            s.setDuplicateParentStateEnabled(true);
            r.addView(s);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 5), 0, 0);
        r.setLayoutParams(lp);
        return r;
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
        TextView sub = text("Everything can be changed later in Settings. To run setup again, go to Settings → Danger Zone → Run setup again.", 15, Ui.TEXT_DIM);
        sub.setPadding(0, Ui.dp(this, 8), Ui.dp(this, 24), 0);
        left.addView(sub);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int appCount = 0;
        for (String pkg : Prefs.favourites(this)) if (Mode.isInstalled(this, pkg)) appCount++;
        List<String> connected = new java.util.ArrayList<>();
        if (Plex.installed(this) && Prefs.plexToken(this) != null) connected.add("Plex");
        if (Jellyfin.installed(this) && Jellyfin.signedIn(this)) connected.add("Jellyfin");
        String servicesLine = !Watching.available(this) ? "Plex and Jellyfin aren't installed"
                : connected.isEmpty() ? "None (Settings \u203A External Services)"
                : android.text.TextUtils.join(" and ", connected);
        String homeLine;
        if (Mode.fireTv(this)) {
            homeLine = HomeButtonService.isEnabled(this) && Prefs.hook(this) ? "Opens SimpleLauncher"
                    : "Not set up yet: set it up from your computer over ADB";
        } else {
            homeLine = Mode.isDefaultHome(this) ? "SimpleLauncher is your home app" : "Choose it in Settings → Danger Zone";
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
                {"Services", servicesLine},
                {Mode.fireTv(this) ? "HomeCatch" : "Home app", homeLine}};
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
            if (currentSeg && !redrawing) {
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
