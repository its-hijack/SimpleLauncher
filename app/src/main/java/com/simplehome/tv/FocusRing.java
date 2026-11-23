package com.simplehome.tv;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * The white outline on the home screen that glides from tile to tile as you move,
 * instead of jumping. It sits above the whole screen and follows the selected card.
 */
final class FocusRing {

    private static final long GLIDE_MS = 260;

    private final View ring;
    private final FrameLayout.LayoutParams lp;
    private ValueAnimator anim;
    private boolean shown;
    private View tile;
    private View card;
    private float curX, curY, curW, curH;

    FocusRing(Activity a) {
        ring = new View(a);
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.TRANSPARENT);
        d.setStroke(Ui.dp(a, 3), Color.WHITE);
        d.setCornerRadius(Ui.dp(a, 9));
        ring.setBackground(d);
        ring.setAlpha(0f);
        ring.setFocusable(false);
        ring.setClickable(false);
        lp = new FrameLayout.LayoutParams(0, 0);
        ((ViewGroup) a.getWindow().getDecorView()).addView(ring, lp);
    }

    /** Glide (or jump, the first time) to the card inside this tile. */
    void moveTo(final View tile, final View card) {
        this.tile = tile;
        this.card = card;
        if (card.getWidth() == 0) {           // not laid out yet: try again once it is
            tile.post(() -> {
                if (this.tile == tile) moveTo(tile, card);
            });
            return;
        }
        final float[] t = target();
        if (anim != null) anim.cancel();
        if (!shown) {
            set(t[0], t[1], t[2], t[3]);
            ring.animate().alpha(1f).setDuration(150).start();
            shown = true;
            return;
        }
        final float fx = curX, fy = curY, fw = curW, fh = curH;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(GLIDE_MS);
        anim.setInterpolator(new DecelerateInterpolator(2f));
        anim.addUpdateListener(va -> {
            float f = (float) va.getAnimatedValue();
            set(fx + (t[0] - fx) * f, fy + (t[1] - fy) * f, fw + (t[2] - fw) * f, fh + (t[3] - fh) * f);
        });
        anim.start();
    }

    /** Keep up when the row scrolls. */
    void follow() {
        if (!shown || tile == null || card == null || card.getWidth() == 0) return;
        if (anim != null && anim.isRunning()) return;
        float[] t = target();
        set(t[0], t[1], t[2], t[3]);
    }

    void hide() {
        if (anim != null) anim.cancel();
        shown = false;
        tile = null;
        card = null;
        ring.animate().alpha(0f).setDuration(120).start();
    }

    /** The card's area once it has grown to its selected size. */
    private float[] target() {
        int[] loc = new int[2];
        tile.getLocationInWindow(loc);
        float w = card.getWidth();
        float h = card.getHeight();
        float cx = loc[0] + card.getLeft() + w / 2f;
        float cy = loc[1] + card.getTop() + h / 2f;
        float sw = w * Ui.FOCUS_SCALE;
        float sh = h * Ui.FOCUS_SCALE;
        return new float[]{cx - sw / 2f, cy - sh / 2f, sw, sh};
    }

    private void set(float x, float y, float w, float h) {
        curX = x;
        curY = y;
        curW = w;
        curH = h;
        ring.setX(x);
        ring.setY(y);
        lp.width = Math.round(w);
        lp.height = Math.round(h);
        ring.setLayoutParams(lp);
    }
}
