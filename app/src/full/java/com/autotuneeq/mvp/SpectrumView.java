package com.autotuneeq.mvp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Lightweight custom view, no third-party graphics libraries. */
public final class SpectrumView extends View {
    private final float[] levels = new float[5];
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String[] labels = {"BASS", "LOW MID", "MID", "PRESENCE", "TREBLE"};
    private final int[] colors = {0xff35bce0, 0xff35bce0, 0xff41cdaa, 0xff41cdaa, 0xffbb9cf8};
    public SpectrumView(Context context) { super(context); setMinimumHeight(dp(150)); }
    private int dp(float d) { return (int)(d*getResources().getDisplayMetrics().density); }
    public void setLevels(float[] values) {
        if (values != null && values.length == 5) System.arraycopy(values, 0, levels, 0, 5);
        invalidate();
    }
    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), dp(165)); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float width=getWidth(), base=getHeight()-dp(34), top=dp(12);
        float step=width/5f, bw=step*.48f;
        for (int i=0;i<5;i++) {
            float x=step*(i+.5f), height=base-top;
            p.setColor(0xff283749);
            c.drawRoundRect(x-bw/2, top, x+bw/2, base, dp(5),dp(5),p);
            p.setColor(colors[i]);
            float bar=Math.max(dp(3), Math.min(height, levels[i]*height));
            c.drawRoundRect(x-bw/2, base-bar, x+bw/2, base, dp(5),dp(5),p);
            p.setColor(0xffa4b5c5); p.setTextSize(dp(9)); p.setTextAlign(Paint.Align.CENTER);
            c.drawText(labels[i], x, getHeight()-dp(12), p);
        }
    }
}
