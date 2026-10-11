package me.ancal.wmdroid;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/** Visual-only SHRecognizeGesture feedback, in the root's VGA coordinates. */
final class HoldIndicator {
    private static final int[][] OFFSETS = {
        {0,-28},{21,-21},{28,0},{21,21},{0,28},{-21,21},{-28,0},{-21,-21}
    };
    private static final int START_MS = 100, STEP_MS = 37, MOVE_LIMIT = 14;
    private final View host;
    private final SharedPreferences prefs;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable tick = this::advance;
    private boolean tracking;
    private int pointerId, dots;
    private float downX, downY;
    private long downTime;

    HoldIndicator(View host, SharedPreferences prefs) {
        this.host = host;
        this.prefs = prefs;
        // AYGSHELL 03877084 requests commctrl ordinal77(0x10000028).
        // Its RGB is unresolved; use the current dark accent provisionally.
        paint.setColor(0xff00143c);
    }

    void observe(MotionEvent event, float scale, int usableHeight) {
        if (scale <= 0) { cancel(); return; }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancel();
                if (!enabled() || event.getPointerCount() != 1
                        || event.getY() < 0 || event.getY() >= usableHeight) return;
                paint.setColor(LauncherTheme.darkSelection(host.getContext(),prefs.getBoolean("rose",false)));
                pointerId = event.getPointerId(0);
                downX = event.getX() / scale;
                downY = event.getY() / scale;
                downTime = event.getEventTime();
                tracking = true;
                scheduleNext();
                break;
            case MotionEvent.ACTION_MOVE:
                if (!tracking) return;
                int index = event.findPointerIndex(pointerId);
                if (index < 0 || event.getPointerCount() != 1) { cancel(); return; }
                for (int h = 0; h < event.getHistorySize(); h++) {
                    if (moved(event.getHistoricalX(index,h) / scale,
                            event.getHistoricalY(index,h) / scale)) { cancel(); return; }
                }
                if (moved(event.getX(index) / scale,event.getY(index) / scale)) cancel();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancel();
                break;
        }
    }

    private boolean enabled() {
        return prefs.getBoolean("retro_enabled",true) && prefs.getBoolean("retro_hold",true);
    }

    private boolean moved(float x, float y) {
        // 03876FC4 compares each axis separately, not the radial distance.
        return Math.abs(x-downX) > MOVE_LIMIT || Math.abs(y-downY) > MOVE_LIMIT;
    }

    private void advance() {
        if (!tracking) return;
        if (!enabled() || host.getWindowToken()==null || !host.isShown()
                || host.getWindowVisibility() != View.VISIBLE) { cancel(); return; }
        // 03877084: clamp((elapsed-100)/37,0,8); first dot137ms, last396ms.
        // Native recognition completes after400ms (038773F0). Android keeps its
        // own long-click deadline/action; this helper never clicks or captures.
        int count = (int)Math.min(8,Math.max(0,(SystemClock.uptimeMillis()-downTime-START_MS)/STEP_MS));
        if (dots != count) { dots = count; host.invalidate(); }
        if (dots < OFFSETS.length) scheduleNext();
    }

    private void scheduleNext() {
        host.postDelayed(tick,Math.max(0,downTime+START_MS+(dots+1)*STEP_MS-SystemClock.uptimeMillis()));
    }

    void draw(Canvas canvas) {
        for (int i = 0; i < dots; i++)
            canvas.drawCircle(downX+OFFSETS[i][0],downY+OFFSETS[i][1],7,paint);
    }

    void cancel() {
        host.removeCallbacks(tick);
        tracking = false;
        if (dots != 0) { dots = 0; host.invalidate(); }
    }
}
