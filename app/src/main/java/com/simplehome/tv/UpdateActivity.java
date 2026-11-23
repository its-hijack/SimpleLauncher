package com.simplehome.tv;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Settings → Information → Software update. Checks GitHub when opened (never on its own), then shows
 * one of: up to date, update available, the one-time "allow updates" step, downloading, or a problem.
 */
public class UpdateActivity extends Screen {

    static final String EXTRA_INSTALL_RESULT = "install_result";
    static final String EXTRA_INSTALL_MESSAGE = "install_message";

    private static final int GREEN = 0xFF4ADE80;
    private static final int RED = 0xFFF87171;
    private static final int AMBER = 0xFFFBBF24;
    private static final int BLUE = 0xFF6FB1FF;
    private static final int PANEL = 0xFF151922;
    private static final int INSET = 0xFF0A0C11;
    private static final int LINE = 0xFF232836;
    private static final int LABEL = 0xFF8A93A6;

    private enum State { CHECKING, UP_TO_DATE, AVAILABLE, PERMISSION, DOWNLOADING, WAITING, ERROR }

    private LinearLayout root;
    private LinearLayout card;
    private LinearLayout notesPanel;
    private TextView notesText;
    private TextView hint;
    private TextView footer;
    private LinearLayout buttons;

    private State state;
    private Updater.Release release;
    private volatile boolean cancelled;
    private boolean destroyed;
    private int generation;        // ignores results from a check or download that was replaced

    // Download progress views (only while downloading)
    private View progressFill;
    private View progressRest;
    private TextView progressBytes;
    private TextView progressPercent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildLayout();
        if (!handleInstallResult(getIntent())) check();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleInstallResult(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from the "Install unknown apps" setting: carry straight on once it's allowed.
        if (state == State.PERMISSION && Updater.canInstall(this)) download();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        cancelled = true;
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && state == State.DOWNLOADING) {
            cancelDownload();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ------------------------------------------------------------------ flow

    private void check() {
        final int gen = ++generation;
        show(State.CHECKING);
        new Thread(() -> {
            Updater.Release r = null;
            Updater.UpdateException error = null;
            try {
                r = Updater.latest(this);
            } catch (Updater.UpdateException e) {
                error = e;
            }
            final Updater.Release found = r;
            final Updater.UpdateException failed = error;
            runOnUiThread(() -> {
                if (destroyed || gen != generation) return;
                if (failed != null) {
                    showError(failed.title, failed.getMessage());
                    return;
                }
                release = found;
                show(Updater.isNewer(found.build, BuildConfig.BUILD_TAG) ? State.AVAILABLE : State.UP_TO_DATE);
            });
        }, "update-check").start();
    }

    private void startUpdate() {
        if (!Updater.canInstall(this)) {
            show(State.PERMISSION);
            return;
        }
        download();
    }

    private void download() {
        final int gen = ++generation;
        cancelled = false;
        show(State.DOWNLOADING);
        final Updater.Release r = release;
        new Thread(() -> {
            boolean started = false;
            Updater.UpdateException error = null;
            try {
                started = Updater.downloadAndInstall(this, r, new Updater.Progress() {
                    @Override
                    public void onProgress(long done, long total) {
                        runOnUiThread(() -> {
                            if (!destroyed && gen == generation) setProgress(done, total);
                        });
                    }

                    @Override
                    public boolean cancelled() {
                        return cancelled || gen != generation;
                    }
                });
            } catch (Updater.UpdateException e) {
                error = e;
            }
            final boolean ok = started;
            final Updater.UpdateException failed = error;
            runOnUiThread(() -> {
                if (destroyed || gen != generation) return;
                if (failed != null) showError(failed.title, failed.getMessage());
                else if (ok) show(State.WAITING);
                else show(State.AVAILABLE);
            });
        }, "update-download").start();
    }

    private void cancelDownload() {
        cancelled = true;
        generation++;
        show(State.AVAILABLE);
    }

    /** Android's installer reported back (see InstallReceiver). */
    private boolean handleInstallResult(Intent intent) {
        String result = intent != null ? intent.getStringExtra(EXTRA_INSTALL_RESULT) : null;
        if (result == null) return false;
        intent.removeExtra(EXTRA_INSTALL_RESULT);
        generation++;
        if ("cancelled".equals(result)) {
            if (release != null) {
                show(State.AVAILABLE);
                Apps.toast(this, "Update cancelled. Nothing was changed.");
            } else {
                check();
            }
        } else {
            String why = intent.getStringExtra(EXTRA_INSTALL_MESSAGE);
            showError("The update couldn't be installed",
                    "Your TV refused the new build" + (why != null && !why.isEmpty() ? " (" + why + ")" : "")
                            + ". Your current SimpleLauncher is still installed and working.");
        }
        return true;
    }

    // ------------------------------------------------------------------ screens

    private String errorTitle;
    private String errorMessage;

    private void showError(String title, String message) {
        errorTitle = title;
        errorMessage = message;
        show(State.ERROR);
    }

    private void show(State s) {
        state = s;
        card.removeAllViews();
        buttons = null;
        progressFill = progressRest = null;
        notesPanel.setVisibility(View.GONE);
        hint.setText("Back to return");
        footer.setText("Updates come from " + Updater.REPO_URL);
        String running = BuildConfig.BUILD_TAG;

        switch (s) {
            case CHECKING:
                label("CHECKING", BLUE);
                heading("Checking GitHub…", "Looking for a newer build than " + running + ".");
                break;

            case UP_TO_DATE: {
                LinearLayout top = new LinearLayout(this);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setGravity(Gravity.CENTER_VERTICAL);
                TextView tick = new TextView(this);
                tick.setText("✓");
                tick.setGravity(Gravity.CENTER);
                tick.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                tick.setTextColor(GREEN);
                GradientDrawable circle = new GradientDrawable();
                circle.setShape(GradientDrawable.OVAL);
                circle.setColor(0xFF16331F);
                tick.setBackground(circle);
                top.addView(tick, new LinearLayout.LayoutParams(dp(48), dp(48)));
                LinearLayout texts = new LinearLayout(this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(dp(14), 0, 0, 0);
                texts.addView(text("You're up to date", 26, Color.WHITE, false));
                TextView sub = text(BuildConfig.RELEASE_VERSION + " · build " + running
                        + " is the newest release on GitHub", 13, Ui.TEXT_DIM, false);
                sub.setPadding(0, dp(4), 0, 0);
                texts.addView(sub);
                top.addView(texts);
                card.addView(top);
                versions(running, release.build);
                gap(14);
                card.addView(text(Updater.lastChecked(this), 12, Ui.TEXT_DIM, false));
                spacer();
                buttonRow();
                button("Done", true, this::finish);
                button("Check again", false, this::check);
                break;
            }

            case AVAILABLE: {
                footer.setText("Updates come from " + Updater.REPO_URL + " · Your apps and settings are kept");
                label("UPDATE AVAILABLE", BLUE);
                StringBuilder sub = new StringBuilder("Build ").append(release.build);
                if (Updater.size(release.apkSize) != null) sub.append(" · ").append(Updater.size(release.apkSize));
                if (release.published != null) sub.append(" · Released ").append(release.published);
                heading("SimpleLauncher " + release.version, sub.toString());
                versions(running, release.build);
                showNotes();
                spacer();
                buttonRow();
                button("Download and install", true, this::startUpdate);
                button("Not now", false, this::finish);
                break;
            }

            case PERMISSION: {
                footer.setText("Fire TV keeps this under Settings › My Fire TV › Developer options › Install unknown apps");
                label("ONE-TIME STEP", AMBER);
                heading("Allow SimpleLauncher to install updates",
                        "Your TV needs your OK once before an app can install its own updates.");
                step("1", "Select Open settings below");
                step("2", "Find SimpleLauncher in the list");
                step("3", "Switch it On, then press Back");
                gap(10);
                card.addView(text("This is done for you when you install with firestick.sh option 3.", 12, LABEL, false));
                spacer();
                buttonRow();
                button("Open settings", true, () -> {
                    if (!Updater.openInstallSetting(this)) Apps.toast(this, "Couldn't open your TV's settings");
                });
                button("Cancel", false, () -> show(State.AVAILABLE));
                break;
            }

            case DOWNLOADING: {
                hint.setText("Back to cancel");
                footer.setText("Updates come from " + Updater.REPO_URL + " · Your apps and settings are kept");
                label("DOWNLOADING", BLUE);
                heading("Getting build " + release.build, "Keep this screen open. It only takes a few seconds.");
                progressBar();
                infoBox("When it's downloaded, your TV asks you to confirm. Select Install. "
                        + "SimpleLauncher closes for a moment, then opens again on the new build.");
                showNotes();
                spacer();
                buttonRow();
                button("Cancel", true, this::cancelDownload);
                break;
            }

            case WAITING: {
                label("ALMOST DONE", BLUE);
                heading("Select Install on your TV's screen",
                        "SimpleLauncher closes for a moment, then opens again on build " + release.build + ".");
                infoBox("Don't see it? Press Back, select Download and install again.");
                spacer();
                buttonRow();
                button("Back", true, () -> show(State.AVAILABLE));
                break;
            }

            case ERROR: {
                label("COULDN'T UPDATE", RED);
                heading(errorTitle, errorMessage);
                infoBox("If this keeps happening, please leave a bug report at " + Updater.ISSUES_URL
                        + ", then reinstall SimpleLauncher from your computer with firestick.sh (option 3).");
                spacer();
                buttonRow();
                button("Try again", true, release != null && "The download didn't finish".equals(errorTitle)
                        ? this::startUpdate : this::check);
                button("Back", false, this::finish);
                break;
            }
        }

        card.setAlpha(0f);
        card.animate().alpha(1f).setDuration(220).start();
        if (buttons != null && buttons.getChildCount() > 0) buttons.getChildAt(0).requestFocus();
    }

    // ------------------------------------------------------------------ layout

    private void buildLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(42), dp(30), dp(42), dp(24));
        Ui.applyBackground(this, root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        logo.setAdjustViewBounds(true);
        header.addView(logo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)));
        TextView title = text("Software update", 22, Color.WHITE, false);
        title.setPadding(dp(11), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hint = text("", 12, Ui.TEXT_DIM, false);
        header.addView(hint);
        root.addView(header);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        bodyLp.setMargins(0, dp(20), 0, dp(16));
        root.addView(body, bodyLp);

        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(this, PANEL, 10, 0, 0));
        card.setPadding(dp(26), dp(24), dp(26), dp(24));
        body.addView(card, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        notesPanel = new LinearLayout(this);
        notesPanel.setOrientation(LinearLayout.VERTICAL);
        notesPanel.setBackground(Ui.rounded(this, PANEL, 10, 0, 0));
        notesPanel.setPadding(dp(22), dp(20), dp(22), dp(20));
        notesPanel.addView(sectionLabel("WHAT'S NEW", LABEL));
        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        notesText = text("", 13, Ui.TEXT, false);
        notesText.setLineSpacing(0, 1.3f);
        notesText.setPadding(0, dp(10), 0, 0);
        scroll.addView(notesText);
        notesPanel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout.LayoutParams notesLp = new LinearLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.MATCH_PARENT);
        notesLp.setMargins(dp(16), 0, 0, 0);
        body.addView(notesPanel, notesLp);

        footer = text("", 11, LABEL, false);
        root.addView(footer);
        setContentView(root);
    }

    private void showNotes() {
        notesText.setText(release.notes != null ? release.notes : "No notes were written for this release.");
        notesPanel.setVisibility(View.VISIBLE);
    }

    private void label(String s, int color) {
        card.addView(sectionLabel(s, color));
        gap(12);
    }

    private TextView sectionLabel(String s, int color) {
        TextView t = text(s, 11, color, true);
        t.setLetterSpacing(0.15f);
        return t;
    }

    private void heading(String title, String sub) {
        card.addView(text(title, 26, Color.WHITE, false));
        TextView s = text(sub, 13, Ui.TEXT_DIM, false);
        s.setPadding(0, dp(5), 0, 0);
        s.setLineSpacing(0, 1.2f);
        card.addView(s);
        gap(16);
    }

    /** ON THIS TV → ON GITHUB */
    private void versions(String running, String github) {
        LinearLayout box = insetBox();
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(versionColumn("ON THIS TV", running, false));
        TextView arrow = text("→", 20, LABEL, false);
        arrow.setPadding(dp(16), 0, dp(16), 0);
        box.addView(arrow);
        box.addView(versionColumn("ON GITHUB", github, true));
        card.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout versionColumn(String label, String build, boolean strong) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView l = sectionLabel(label, LABEL);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        col.addView(l);
        TextView v = text(build, 15, strong ? Color.WHITE : Ui.TEXT, strong);
        v.setPadding(0, dp(3), 0, 0);
        col.addView(v);
        return col;
    }

    private void progressBar() {
        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(Ui.rounded(this, 0xFF2A3040, 4, 0, 0));
        progressFill = new View(this);
        progressFill.setBackground(Ui.rounded(this, Ui.accent(this), 4, 0, 0));
        progressRest = new View(this);
        track.addView(progressFill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0f));
        track.addView(progressRest, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        card.addView(track, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        progressBytes = text("Starting…", 12, Ui.TEXT_DIM, false);
        row.addView(progressBytes, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        progressPercent = text("0%", 12, Color.WHITE, true);
        row.addView(progressPercent);
        card.addView(row);
        gap(16);
    }

    private void setProgress(long done, long total) {
        if (progressFill == null) return;
        float p = total > 0 ? Math.min(1f, done / (float) total) : 0f;
        ((LinearLayout.LayoutParams) progressFill.getLayoutParams()).weight = p;
        ((LinearLayout.LayoutParams) progressRest.getLayoutParams()).weight = 1f - p;
        progressFill.requestLayout();
        String d = Updater.size(done), t = Updater.size(total);
        progressBytes.setText(t != null ? (d != null ? d : "0 KB") + " of " + t : (d != null ? d : ""));
        progressPercent.setText(Math.round(p * 100) + "%");
    }

    private void infoBox(String message) {
        LinearLayout box = insetBox();
        box.setOrientation(LinearLayout.HORIZONTAL);
        TextView i = text("i", 12, BLUE, true);
        i.setGravity(Gravity.CENTER);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(dp(1.5f), BLUE);
        i.setBackground(ring);
        box.addView(i, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView t = text(message, 13, Ui.TEXT, false);
        t.setLineSpacing(0, 1.25f);
        t.setPadding(dp(12), 0, 0, 0);
        box.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(box);
    }

    private void step(String number, String words) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(10));
        TextView n = text(number, 13, Color.WHITE, true);
        n.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Ui.CARD_FOCUSED);
        n.setBackground(circle);
        row.addView(n, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView w = text(words, 15, Ui.TEXT, false);
        w.setPadding(dp(12), 0, 0, 0);
        row.addView(w);
        card.addView(row);
    }

    private LinearLayout insetBox() {
        LinearLayout box = new LinearLayout(this);
        box.setBackground(Ui.rounded(this, INSET, 8, 1, LINE));
        box.setPadding(dp(16), dp(12), dp(16), dp(12));
        return box;
    }

    private void buttonRow() {
        buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(buttons);
    }

    private void button(String label, boolean primary, Runnable onClick) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(38));
        b.setPadding(dp(22), dp(8), dp(22), dp(8));
        b.setBackgroundResource(R.drawable.pill_bg);
        b.setTextColor(getResources().getColorStateList(R.color.pill_text, getTheme()));
        if (primary) b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (buttons.getChildCount() > 0) lp.setMargins(dp(10), 0, 0, 0);
        buttons.addView(b, lp);
    }

    private void spacer() {
        card.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
    }

    private void gap(int dpHeight) {
        card.addView(new View(this), new LinearLayout.LayoutParams(1, dp(dpHeight)));
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
