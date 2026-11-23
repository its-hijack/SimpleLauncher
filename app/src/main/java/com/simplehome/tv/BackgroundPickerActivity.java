package com.simplehome.tv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen background picker: each box is a miniature home screen in that colour.
 *
 * Built with tabs in mind. Today there's only "Colours", so the tab bar is hidden.
 * To add pre-loaded pictures later: put the images in res/drawable-nodpi, add a
 * "Pictures" tab to TABS with its own list of options, and the tab bar appears
 * automatically.
 */
public class BackgroundPickerActivity extends Screen {

    private static final String[] TABS = {"Colours"};
    private static final int GAP = 18;

    private GridLayout grid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int bg = Prefs.background(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(bg));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(Ui.dp(this, 42), Ui.dp(this, 28), Ui.dp(this, 42), Ui.dp(this, 20));
        root.setClipChildren(false);
        root.setClipToPadding(false);

        root.addView(Ui.pickerHeader(this, "Background", "Each box shows your home screen in that colour."));

        if (TABS.length > 1) {
            // Tab bar: only shown once there's more than one kind of background.
            LinearLayout tabs = new LinearLayout(this);
            tabs.setOrientation(LinearLayout.HORIZONTAL);
            for (String name : TABS) {
                TextView tab = new TextView(this);
                tab.setText(name);
                tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                tab.setPadding(Ui.dp(this, 18), Ui.dp(this, 7), Ui.dp(this, 18), Ui.dp(this, 7));
                tabs.addView(tab);
            }
            root.addView(tabs);
        }

        grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), 0, 0);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.setMargins(0, Ui.dp(this, 16), 0, 0);
        root.addView(grid, glp);

        setContentView(root);
        showColours();
    }

    private void showColours() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        int boxW = Math.min(200, (widthDp - 84 - 6) / 4 - GAP);
        int boxH = boxW * 9 / 16;
        int current = Prefs.background(this);

        List<View> tiles = new ArrayList<>();
        View currentTile = null;
        grid.removeAllViews();
        for (int i = 0; i < Ui.BG_COLORS.length; i++) {
            final int color = Ui.BG_COLORS[i];
            View tile = colourTile(Ui.BG_NAMES[i], color, color == current, boxW, boxH);
            tile.setOnClickListener(v -> {
                Prefs.setBackground(this, color);
                finish();
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = Ui.dp(this, boxW);
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.setMargins(0, 0, Ui.dp(this, GAP), Ui.dp(this, 14));
            grid.addView(tile, lp);
            tiles.add(tile);
            if (color == current) currentTile = tile;
        }
        (currentTile != null ? currentTile : tiles.get(0)).requestFocus();
    }

    private View colourTile(String name, int color, boolean isCurrent, int boxW, int boxH) {
        LinearLayout tile = Ui.baseTile(this);

        FrameLayout box = new FrameLayout(this);
        StateListDrawable frame = new StateListDrawable();
        frame.addState(new int[]{android.R.attr.state_focused}, Ui.rounded(this, color, 8, 3, Color.WHITE));
        frame.addState(new int[]{}, Ui.rounded(this, color, 8, 3, 0x1FFFFFFF));
        box.setBackground(frame);
        box.setDuplicateParentStateEnabled(true);
        box.setPadding(Ui.dp(this, 11), Ui.dp(this, 9), Ui.dp(this, 11), Ui.dp(this, 9));
        box.addView(homePreview(this, Prefs.accent(this)), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (isCurrent) {
            ImageView check = new ImageView(this);
            check.setImageResource(R.drawable.ic_check);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Color.WHITE);
            check.setBackground(circle);
            int p = Ui.dp(this, 3);
            check.setPadding(p, p, p, p);
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18), Gravity.TOP | Gravity.RIGHT);
            box.addView(check, clp);
        }
        tile.addView(box, new LinearLayout.LayoutParams(Ui.dp(this, boxW), Ui.dp(this, boxH)));

        TextView label = Ui.label(this, name, boxW);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tile.addView(label);
        Ui.scaleOnFocus(tile, box);
        return tile;
    }

    /** A miniature home screen: logo and clock, buttons, an app row and Continue Watching cards. */
    static View homePreview(Context c, int accent) {
        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(c);
        logo.setImageResource(R.drawable.logo);
        logo.setAdjustViewBounds(true);
        top.addView(logo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 12)));
        TextView time = new TextView(c);
        time.setText(Prefs.clock24(c) ? "18:42" : "6:42");
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setTextColor(Color.WHITE);
        time.setIncludeFontPadding(false);
        time.setPadding(Ui.dp(c, 5), 0, 0, 0);
        top.addView(time, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        for (int i = 0; i < 3; i++) {
            View pill = new View(c);
            pill.setBackground(Ui.rounded(c, 0x29FFFFFF, 3, 0, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 15), Ui.dp(c, 5));
            lp.setMargins(Ui.dp(c, 3), 0, 0, 0);
            top.addView(pill, lp);
        }
        p.addView(top);

        LinearLayout apps = new LinearLayout(c);
        apps.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 4; i++) {
            View tile = new View(c);
            tile.setBackground(i == 0 ? Ui.rounded(c, 0x24FFFFFF, 2, 1, Color.WHITE) : Ui.rounded(c, 0x1AFFFFFF, 2, 0, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 18), 1f);
            lp.setMargins(0, 0, i < 3 ? Ui.dp(c, 4) : 0, 0);
            apps.addView(tile, lp);
        }
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.setMargins(0, Ui.dp(c, 12), 0, 0);
        p.addView(apps, alp);

        LinearLayout cards = new LinearLayout(c);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        int[] progress = {60, 35, 80};
        for (int i = 0; i < 3; i++) {
            FrameLayout card = new FrameLayout(c);
            card.setBackground(Ui.rounded(c, 0x14FFFFFF, 2, 0, 0));
            LinearLayout bar = new LinearLayout(c);
            View done = new View(c);
            done.setBackgroundColor(accent);
            bar.addView(done, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, progress[i]));
            bar.addView(new View(c), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 100 - progress[i]));
            card.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 2), Gravity.BOTTOM));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 15), 1f);
            lp.setMargins(0, 0, i < 2 ? Ui.dp(c, 4) : 0, 0);
            cards.addView(card, lp);
        }
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, Ui.dp(c, 7), 0, 0);
        p.addView(cards, clp);
        return p;
    }
}
