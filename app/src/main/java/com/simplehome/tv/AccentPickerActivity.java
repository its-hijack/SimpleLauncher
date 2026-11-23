package com.simplehome.tv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen accent picker: coloured dots across the top, and a live preview of
 * Settings and the home screen underneath that updates as you move between dots.
 */
public class AccentPickerActivity extends Screen {

    private static final int PANEL = 0xFF0A0C11;
    private static final int PANEL_EDGE = 0xFF232836;

    private final List<View> items = new ArrayList<>();
    private final List<View> dots = new ArrayList<>();
    private final List<TextView> dotLabels = new ArrayList<>();
    private final List<ToggleView> toggles = new ArrayList<>();
    private View progress;
    private GradientDrawable badge;
    private TextView badgeText;
    private int selected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int bg = Prefs.background(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(bg));

        int current = Prefs.accent(this);
        for (int i = 0; i < Ui.ACCENT_COLORS.length; i++) if (Ui.ACCENT_COLORS[i] == current) selected = i;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(Ui.dp(this, 42), Ui.dp(this, 28), Ui.dp(this, 42), Ui.dp(this, 28));
        root.addView(Ui.pickerHeader(this, "Accent colour", "Move left and right to try a colour. The preview updates as you go."));

        // Dots across the top
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        for (int i = 0; i < Ui.ACCENT_COLORS.length; i++) row.addView(dot(i));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 78));
        rlp.setMargins(0, Ui.dp(this, 12), 0, 0);
        root.addView(row, rlp);

        // Live preview
        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.HORIZONTAL);
        preview.addView(settingsPreview(), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(Ui.dp(this, 200), ViewGroup.LayoutParams.MATCH_PARENT);
        hlp.setMargins(Ui.dp(this, 16), 0, 0, 0);
        preview.addView(homePreview(), hlp);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        plp.setMargins(0, Ui.dp(this, 12), 0, 0);
        root.addView(preview, plp);

        setContentView(root);
        update();
        items.get(selected).requestFocus();
    }

    private View dot(final int index) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setFocusable(true);
        item.setClickable(true);

        View circle = new View(this);
        item.addView(circle, new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34)));
        TextView name = new TextView(this);
        name.setText(Ui.ACCENT_NAMES[index]);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, Ui.dp(this, 8), 0, 0);
        item.addView(name);

        item.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                selected = index;
                update();
            }
        });
        item.setOnClickListener(v -> {
            Prefs.setAccent(this, Ui.ACCENT_COLORS[index]);
            finish();
        });
        items.add(item);
        dots.add(circle);
        dotLabels.add(name);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 86), ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        item.setLayoutParams(lp);
        return item;
    }

    /** Restyle the dots and recolour the preview for the dot that's selected. */
    private void update() {
        int accent = Ui.ACCENT_COLORS[selected];
        for (int i = 0; i < dots.size(); i++) {
            boolean on = i == selected;
            View circle = dots.get(i);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(Ui.ACCENT_COLORS[i]);
            if (on) d.setStroke(Ui.dp(this, 3), Color.WHITE);
            circle.setBackground(d);
            ViewGroup.LayoutParams lp = circle.getLayoutParams();
            lp.width = lp.height = Ui.dp(this, on ? 46 : 34);
            circle.setLayoutParams(lp);
            dotLabels.get(i).setTextColor(on ? Color.WHITE : 0xFF8A93A6);
        }
        for (ToggleView t : toggles) t.setColor(accent);
        progress.setBackgroundColor(accent);
        badge.setColor(accent);
        badgeText.setTextColor(accent == 0xFFF2F4F7 ? 0xFF0C0F15 : Color.WHITE);
    }

    // ------------------------------------------------------------------ preview panels

    private LinearLayout panel() {
        LinearLayout p = new LinearLayout(this);
        p.setBackground(Ui.rounded(this, PANEL, 12, 1, PANEL_EDGE));
        int pad = Ui.dp(this, 16);
        p.setPadding(pad, pad, pad, pad);
        return p;
    }

    private View settingsPreview() {
        LinearLayout p = panel();
        p.setOrientation(LinearLayout.HORIZONTAL);

        // Mini category list
        LinearLayout cats = new LinearLayout(this);
        cats.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        logo.setAdjustViewBounds(true);
        head.addView(logo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 18)));
        TextView title = text("Settings", 14, Color.WHITE);
        title.setPadding(Ui.dp(this, 7), 0, 0, 0);
        head.addView(title);
        cats.addView(head);
        String[] names = {"Home screen", "Appearance", "External Services", "Danger Zone", "Information"};
        for (String n : names) {
            boolean active = n.equals("Appearance");
            TextView t = text(n, 11, active ? Color.WHITE : 0xFF8A93A6);
            t.setPadding(Ui.dp(this, 8), Ui.dp(this, 5), Ui.dp(this, 8), Ui.dp(this, 5));
            if (active) t.setBackground(Ui.rounded(this, 0xFF1E2430, 6, 0, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, Ui.dp(this, n.equals("Home screen") ? 10 : 2), 0, 0);
            cats.addView(t, lp);
        }
        p.addView(cats, new LinearLayout.LayoutParams(Ui.dp(this, 120), ViewGroup.LayoutParams.WRAP_CONTENT));

        // Mini rows
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.addView(miniSection("CLOCK", true));
        rows.addView(miniRow("24-hour clock", true, false));
        rows.addView(miniSection("DATE", false));
        rows.addView(miniRow("Show the day of the week", true, true));
        rows.addView(miniRow("Show the day and month", true, false));
        rows.addView(miniRow("Show the year", false, false));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rlp.setMargins(Ui.dp(this, 16), 0, 0, 0);
        p.addView(rows, rlp);
        return p;
    }

    private View homePreview() {
        LinearLayout p = panel();
        p.setOrientation(LinearLayout.VERTICAL);
        p.addView(miniSection("ALSO ON YOUR HOME SCREEN", true));

        FrameLayout card = new FrameLayout(this);
        GradientDrawable art = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF2B3550, 0xFF151A26});
        art.setCornerRadius(Ui.dp(this, 6));
        card.setBackground(art);
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(0x2EFFFFFF);
        progress = new View(this);
        bar.addView(progress, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 62));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 38));
        card.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3), Gravity.BOTTOM));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 84));
        clp.setMargins(0, Ui.dp(this, 8), 0, 0);
        p.addView(card, clp);
        p.addView(caption("Continue Watching progress"));

        LinearLayout badgeRow = new LinearLayout(this);
        badgeRow.setOrientation(LinearLayout.HORIZONTAL);
        badgeRow.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(Ui.rounded(this, Ui.CARD, 6, 0, 0));
        badgeText = text("1", 11, Color.WHITE);
        badgeText.setGravity(Gravity.CENTER);
        badgeText.setTypeface(Typeface.DEFAULT_BOLD);
        badge = new GradientDrawable();
        badge.setShape(GradientDrawable.OVAL);
        badgeText.setBackground(badge);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18), Gravity.TOP | Gravity.RIGHT);
        blp.setMargins(0, Ui.dp(this, 4), Ui.dp(this, 4), 0);
        tile.addView(badgeText, blp);
        badgeRow.addView(tile, new LinearLayout.LayoutParams(Ui.dp(this, 76), Ui.dp(this, 43)));
        TextView cap = text("App Selection numbers", 11, Ui.TEXT);
        cap.setPadding(Ui.dp(this, 10), 0, 0, 0);
        badgeRow.addView(cap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brp.setMargins(0, Ui.dp(this, 14), 0, 0);
        p.addView(badgeRow, brp);
        return p;
    }

    private TextView miniSection(String title, boolean first) {
        TextView t = text(title, 9, 0xFF8A93A6);
        t.setLetterSpacing(0.15f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(0, Ui.dp(this, first ? 0 : 10), 0, Ui.dp(this, 2));
        return t;
    }

    private View miniRow(String label, boolean on, boolean focused) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        r.setBackground(Ui.rounded(this, focused ? Color.WHITE : 0xFF151922, 6, 0, 0));
        r.addView(text(label, 12, focused ? 0xFF0C0F15 : Ui.TEXT), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ToggleView t = new ToggleView(this);
        t.setOn(on);
        r.addView(t);
        if (on) toggles.add(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 4), 0, 0);
        r.setLayoutParams(lp);
        return r;
    }

    private TextView caption(String s) {
        TextView t = text(s, 11, Ui.TEXT);
        t.setPadding(0, Ui.dp(this, 5), 0, 0);
        return t;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }
}
