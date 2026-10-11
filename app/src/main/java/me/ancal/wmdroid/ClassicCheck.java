package me.ancal.wmdroid;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Plain GDI-style checkbox; no platform tint, ripple or animation. */
final class ClassicCheck extends Drawable {
    private final Paint paint = new Paint();
    private boolean checked;
    @Override public boolean isStateful() { return true; }
    @Override protected boolean onStateChange(int[] states) {
        boolean next = false;
        if(states!=null) for (int state : states) if (state == android.R.attr.state_checked) next = true;
        if (next == checked) return false;
        checked = next; invalidateSelf(); return true;
    }
    @Override public void draw(Canvas canvas) {
        Rect r = getBounds();
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xffffffff); canvas.drawRect(r, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2); paint.setColor(0xff707070);
        canvas.drawRect(r.left+1, r.top+1, r.right-1, r.bottom-1, paint);
        paint.setColor(0xff202020);
        canvas.drawLine(r.left+2, r.top+2, r.right-2, r.top+2, paint);
        canvas.drawLine(r.left+2, r.top+2, r.left+2, r.bottom-2, paint);
        if (checked) {
            paint.setColor(0xff000000); paint.setStrokeWidth(3);
            Path path = new Path(); path.moveTo(r.left+5,r.top+13); path.lineTo(r.left+10,r.top+19); path.lineTo(r.left+21,r.top+7);
            canvas.drawPath(path,paint);
        }
    }
    @Override public int getIntrinsicWidth() { return 26; }
    @Override public int getIntrinsicHeight() { return 26; }
    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
