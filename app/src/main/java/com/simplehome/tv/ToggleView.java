package com.simplehome.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** A small on/off switch drawn in the launcher's style. */
public class ToggleView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private boolean on;
    private Integer color;   // set by the accent preview; otherwise the saved accent

    public ToggleView(Context context) {
        super(context);
        setFocusable(false);
    }

    void setOn(boolean on) {
        this.on = on;
        invalidate();
    }

    void setColor(int c) {
        color = c;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(Ui.dp(getContext(), 40), Ui.dp(getContext(), 24));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;
        paint.setColor(on ? (color != null ? color : Ui.accent(getContext())) : 0xFF3A4150);
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, paint);
        float knob = r - Ui.dp(getContext(), 3);
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(on ? w - r : r, r, knob, paint);
    }
}
