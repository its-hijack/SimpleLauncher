package com.simplehome.tv;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared look: tiles, cards, colours and focus effects. */
final class Ui {

    static final String[] ACCENT_NAMES = {"Blue", "Amber", "Purple", "Green", "White"};
    static final int[] ACCENT_COLORS = {0xFF2F8CFF, 0xFFE5A00D, 0xFF8B5CF6, 0xFF22C55E, 0xFFF2F4F7};
    static final int TEXT = 0xFFE6E9EF;
    static final int TEXT_DIM = 0xFFA3ABBA;
    static final int CARD = 0xFF181C25;
    static final int CARD_FOCUSED = 0xFF1E2430;

    // Sizes in dp (the mockup at TV scale)
    static final int TILE_W = 156;
    static final int TILE_H = 88;
    static final int GAP = 15;
    static final float FOCUS_SCALE = 1.07f;

    /** Background colour choices in Settings. */
    static final String[] BG_NAMES = {"Dark", "Midnight blue", "Charcoal", "Deep purple", "Forest green", "Slate", "Black"};
    static final int[] BG_COLORS = {0xFF0C0F15, 0xFF0B1426, 0xFF17191D, 0xFF160F24, 0xFF0D1A14, 0xFF1B2230, 0xFF000000};

    private Ui() {
    }

    static int accent(Context c) {
        return Prefs.accent(c);
    }

    /** Clock pattern from Settings (AM/PM is shown separately, smaller). */
    static String timePattern(Context c) {
        return Prefs.clock24(c) ? "HH:mm" : "h:mm";
    }

    static boolean showAmPm(Context c) {
        return !Prefs.clock24(c) && Prefs.showAmPm(c);
    }

    /** Date pattern from Settings, or null when every part is switched off. */
    static String datePattern(Context c) {
        StringBuilder p = new StringBuilder();
        if (Prefs.showWeekday(c)) p.append("EEEE");
        if (Prefs.showDayMonth(c)) p.append(p.length() > 0 ? " " : "").append("d MMMM");
        if (Prefs.showYear(c)) p.append(p.length() > 0 ? " " : "").append("yyyy");
        return p.length() > 0 ? p.toString() : null;
    }

    /** Applies the clock, AM/PM and date settings to a set of TextClocks. */
    static void applyClock(Context c, android.widget.TextClock clock, android.widget.TextClock ampm, android.widget.TextClock date) {
        String time = timePattern(c);
        clock.setFormat12Hour(time);
        clock.setFormat24Hour(time);
        if (ampm != null) {
            ampm.setFormat12Hour("a");
            ampm.setFormat24Hour("a");
            ampm.setVisibility(showAmPm(c) ? View.VISIBLE : View.GONE);
        }
        if (date != null) {
            String d = datePattern(c);
            if (d != null) {
                date.setFormat12Hour(d);
                date.setFormat24Hour(d);
            }
            date.setVisibility(d != null ? View.VISIBLE : View.GONE);
        }
    }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static ColorStateList focusColors(int focused, int normal) {
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{focused, normal});
    }

    static GradientDrawable rounded(Context c, int fill, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) d.setStroke(dp(c, strokeDp), strokeColor);
        return d;
    }

    /** Card frame: white outline when its tile is selected. */
    static Drawable cardBackground(Context c) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, rounded(c, CARD_FOCUSED, 8, 3, Color.WHITE));
        s.addState(new int[]{}, rounded(c, CARD, 8, 3, Color.TRANSPARENT));
        s.setEnterFadeDuration(150);
        s.setExitFadeDuration(150);
        return s;
    }

    /** Settings row: white when selected. */
    static Drawable rowBackground(Context c) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, rounded(c, Color.WHITE, 8, 0, 0));
        s.addState(new int[]{}, rounded(c, 0xFF151922, 8, 0, 0));
        s.setEnterFadeDuration(150);
        s.setExitFadeDuration(150);
        return s;
    }

    /** Applies the window and root background colour chosen in Settings. */
    static void applyBackground(Activity a, View root) {
        int color = Prefs.background(a);
        a.getWindow().setBackgroundDrawable(new ColorDrawable(color));
        root.setBackgroundColor(color);
    }

    /** Grow the card slightly while its tile is selected. */
    static void scaleOnFocus(View tile, final View card) {
        tile.setOnFocusChangeListener((v, hasFocus) -> {
            float s = hasFocus ? FOCUS_SCALE : 1f;
            card.animate().scaleX(s).scaleY(s).setDuration(240)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
        });
    }

    /** Card background without its own outline (the home screen's gliding outline draws it instead). */
    static Drawable plainCardBackground(Context c) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, rounded(c, CARD_FOCUSED, 8, 0, 0));
        s.addState(new int[]{}, rounded(c, CARD, 8, 0, 0));
        s.setEnterFadeDuration(150);
        s.setExitFadeDuration(150);
        return s;
    }

    /** Title, subtitle and "Select to apply · Back to cancel" across the top of a picker. */
    static LinearLayout pickerHeader(Context c, String title, String subtitle) {
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.BOTTOM);
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        t.setTextColor(Color.WHITE);
        texts.addView(t);
        TextView s = new TextView(c);
        s.setText(subtitle);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        s.setTextColor(TEXT_DIM);
        s.setPadding(0, dp(c, 3), 0, 0);
        texts.addView(s);
        header.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView hint = new TextView(c);
        hint.setText("Select to apply \u00B7 Back to cancel");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setTextColor(TEXT_DIM);
        header.addView(hint);
        return header;
    }

    /** Holds the parts of a tile the screens need to reach. */
    static final class Tile {
        LinearLayout root;
        FrameLayout card;
        TextView badge;
    }

    /** An app tile: banner (or icon) in a card, name underneath. */
    static Tile appTile(Context c, Apps.Entry e) {
        return appTile(c, e, TILE_W);
    }

    /** An app tile at a given width in dp (the height keeps the TV banner shape). */
    static Tile appTile(Context c, Apps.Entry e, int w) {
        int h = Math.round(w * TILE_H / (float) TILE_W);
        Tile t = new Tile();
        t.root = baseTile(c);
        t.card = card(c, w, h);
        ImageView image = new ImageView(c);
        if (e.banner != null) {
            image.setImageDrawable(copyOf(e.banner));
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            t.card.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            image.setImageDrawable(copyOf(e.icon));
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int icon = Math.round(44f * w / TILE_W);
            t.card.addView(image, new FrameLayout.LayoutParams(dp(c, icon), dp(c, icon), Gravity.CENTER));
        }
        t.root.addView(t.card, new LinearLayout.LayoutParams(dp(c, w), dp(c, h)));
        t.root.addView(label(c, e.label, w));
        scaleOnFocus(t.root, t.card);
        return t;
    }

    /** A tile with text instead of a banner (e.g. "Choose apps"). */
    static Tile textTile(Context c, String big, String caption) {
        return textTile(c, big, caption, TILE_W);
    }

    static Tile textTile(Context c, String big, String caption, int w) {
        int h = Math.round(w * TILE_H / (float) TILE_W);
        Tile t = new Tile();
        t.root = baseTile(c);
        t.card = card(c, w, h);
        TextView text = new TextView(c);
        text.setText(big);
        text.setGravity(Gravity.CENTER);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        text.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        text.setTextColor(focusColors(Color.WHITE, TEXT_DIM));
        text.setDuplicateParentStateEnabled(true);
        t.card.addView(text, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        t.root.addView(t.card, new LinearLayout.LayoutParams(dp(c, w), dp(c, h)));
        t.root.addView(label(c, caption, w));
        scaleOnFocus(t.root, t.card);
        return t;
    }

    /** Numbered blue circle in the corner of a tile (App Selection). */
    static TextView addBadge(Context c, Tile t) {
        TextView b = new TextView(c);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(accent(c));
        b.setBackground(circle);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(c, 22), dp(c, 22), Gravity.TOP | Gravity.RIGHT);
        lp.setMargins(0, dp(c, 4), dp(c, 4), 0);
        t.card.addView(b, lp);
        t.badge = b;
        return b;
    }

    static LinearLayout baseTile(Context c) {
        LinearLayout tile = new LinearLayout(c);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setClipChildren(false);
        tile.setClipToPadding(false);
        return tile;
    }

    static FrameLayout card(Context c, int wDp, int hDp) {
        FrameLayout card = new FrameLayout(c);
        card.setBackground(cardBackground(c));
        card.setDuplicateParentStateEnabled(true);
        int pad = dp(c, 3);
        card.setPadding(pad, pad, pad, pad);
        return card;
    }

    static TextView label(Context c, String text, int widthDp) {
        TextView label = new TextView(c);
        label.setText(text);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setTextColor(focusColors(Color.WHITE, TEXT_DIM));
        label.setDuplicateParentStateEnabled(true);
        label.setPadding(dp(c, 2), dp(c, 8), dp(c, 2), 0);
        label.setLayoutParams(new LinearLayout.LayoutParams(dp(c, widthDp), ViewGroup.LayoutParams.WRAP_CONTENT));
        return label;
    }

    /** Each tile needs its own drawable instance. */
    static Drawable copyOf(Drawable d) {
        if (d == null) return null;
        Drawable.ConstantState state = d.getConstantState();
        return state != null ? state.newDrawable().mutate() : d;
    }

    static LinearLayout.LayoutParams rowItemParams(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(c, GAP), 0);
        return lp;
    }
}
