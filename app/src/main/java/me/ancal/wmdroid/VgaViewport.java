package me.ancal.wmdroid;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

/** Fixed VGA width; the usable height expands without changing any UI pixel metric. */
final class VgaViewport extends me.ancal.wmdroid.sdk.WmViewport {
    private final HoldIndicator hold;

    VgaViewport(Context context) {
        super(context);
        hold = new HoldIndicator(this,context.getSharedPreferences("today",Context.MODE_PRIVATE));
        observeImeInsets();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        // Children may transform or dispatch the event synchronously. Observe
        // a separate root-coordinate copy; the original follows Android's path.
        MotionEvent observed = MotionEvent.obtain(event);
        try {
            observed.offsetLocation(0,-getPaddingTop());
            hold.observe(observed,getScale(),getUsableHeight());
            boolean handled = super.dispatchTouchEvent(event);
            if (!handled) hold.cancel(); // An unclaimed DOWN need not receive UP.
            return handled;
        } finally {
            observed.recycle();
        }
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        int saved = canvas.save();
        canvas.clipRect(0,getPaddingTop(),getWidth(),getPaddingTop()+getUsableHeight());
        canvas.translate(0,getPaddingTop());
        float scale = getScale();
        canvas.scale(scale,scale);
        hold.draw(canvas);
        canvas.restoreToCount(saved);
    }

    @Override protected void onDetachedFromWindow() {
        hold.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onViewportBoundsChanged() {
        if (hold != null) hold.cancel();
    }

    @Override protected void onVisibilityChanged(View changedView,int visibility) {
        super.onVisibilityChanged(changedView,visibility);
        if (hold != null && visibility != VISIBLE) hold.cancel();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (hold != null && visibility != VISIBLE) hold.cancel();
    }

    @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hold != null && !hasWindowFocus) hold.cancel();
    }

}
