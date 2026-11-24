package com.simplehome.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * The little home screen in Settings › Home screen. Redraws as you change the layout or either size.
 */
final class LayoutPreview extends View {

    private static final float SCREEN_W = 960f;   // the TV's width in dp
    private static final float SCREEN_H = 540f;
    private static final int[] CARD_COLOURS = {0xFF2B3550, 0xFF3A2B44, 0xFF24403A, 0xFF44392B, 0xFF2B3F4A, 0xFF3A2F2B};

    boolean grid;
    int appSize = 2;
    int cardSize = 2;
    boolean showApps = true;
    boolean showCards = true;
    int background = 0xFF0A0C11;   // the screen behind it all
    int accent = 0;                // progress bars on the Continue Watching cards (0 = none)

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    LayoutPreview(Context c) {
        super(c);
    }

    void set(boolean grid, int appSize, int cardSize, boolean showApps, boolean showCards) {
        this.grid = grid;
        this.appSize = appSize;
        this.cardSize = cardSize;
        this.showApps = showApps;
        this.showCards = showCards;
        invalidate();
    }

    /** Use your background and accent colours (first-time setup chooses them on the same screen). */
    void colours(int background, int accent) {
        this.background = background;
        this.accent = accent;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float s = w / SCREEN_W;          // dp on the TV → pixels here
        float corner = Ui.dp(getContext(), 8);

        rect(canvas, 0, 0, w, h, corner, background);

        // Top bar: logo and clock on the left, three buttons on the right
        float pad = 42 * s, top = 32 * s;
        rect(canvas, pad, top, pad + 110 * s, top + 34 * s, 4 * s, 0xFF3A4150);
        float bx = w - pad;
        for (int i = 0; i < 3; i++) {
            rect(canvas, bx - 90 * s, top + 6 * s, bx, top + 28 * s, 11 * s, 0xFF2A3040);
            bx -= 100 * s;
        }

        float y = top + 60 * s;
        if (grid) {
            if (showCards) y = cards(canvas, s, pad, y + 20 * s, w);
            if (showApps) apps(canvas, s, pad, y + 14 * s, w, h);
        } else {
            if (showApps) y = apps(canvas, s, pad, y + 36 * s, w, h);
            if (showCards) cards(canvas, s, pad, y + 18 * s, w);
        }
    }

    /** Draws the apps; returns the y below them. */
    private float apps(Canvas canvas, float s, float x0, float y, float w, float h) {
        float tw = Watching.appTileWidth(grid, appSize) * s;
        float th = tw * Ui.TILE_H / Ui.TILE_W;
        float gap = Ui.GAP * s, rowGap = 22 * s + th;   // tile plus its name underneath
        y += 14 * s;                                     // the "YOUR APPS" label
        int index = 0;
        float bottom = y;
        do {
            float x = x0;
            while (x + tw <= w - x0 + 1) {
                rect(canvas, x, y, x + tw, y + th, 4 * s, index == 0 ? 0xFFE6E9EF : 0xFF232836);
                x += tw + gap;
                index++;
            }
            bottom = y + th + 14 * s;
            y += rowGap;
        } while (grid && y + th < h);
        return bottom;
    }

    /** Draws the Continue Watching row; returns the y below it. */
    private float cards(Canvas canvas, float s, float x0, float y, float w) {
        float cw = Watching.cardWidth(cardSize) * s;
        float ch = cw * 110 / 195f;
        float gap = Ui.GAP * s;
        y += 14 * s;
        float x = x0;
        int i = 0;
        while (x < w - x0) {
            float right = Math.min(x + cw, w);
            rect(canvas, x, y, right, y + ch, 4 * s, CARD_COLOURS[i % CARD_COLOURS.length]);
            if (accent != 0) {
                float done = (right - x) * (0.25f + 0.15f * (i % 4));
                rect(canvas, x, y + ch - 3 * s, x + done, y + ch, 0, accent);
            }
            x += cw + gap;
            i++;
        }
        return y + ch + 30 * s;
    }

    private void rect(Canvas canvas, float l, float t, float rr, float b, float radius, int colour) {
        paint.setColor(colour);
        r.set(l, t, rr, b);
        canvas.drawRoundRect(r, radius, radius, paint);
    }
}
