package me.ancal.wmdroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Date;

/** shell32 409D8/40C14/3FF3C: date decisions and VGA geometry, not a photo trace.
 * ROM Shell settings are absent; ShowTitleBarClock is zero-initialized and
 * AutoSingleLineDatePlugin defaults to that value (475C0).
 * Locale formatting uses the launcher language; metrics use the shared font renderer.
 */
final class TodayDateView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable clock;
    private final int lineHeight;
    private final int foreground;
    private Date now = new Date();
    private boolean titleClock, autoSingle;
    TodayDateView(Context context, Typeface face, boolean rose) {
        super(context);foreground=rose?Color.BLACK:Color.WHITE; paint.setTypeface(face); paint.setTextSize(24); paint.setColor(foreground);
        lineHeight=me.ancal.wmdroid.sdk.WmMetrics.LINE;
        clock=LauncherArtwork.load(context,R.drawable.wm_clock);
        refresh(now);
    }
    private String format(String pattern) { return new SimpleDateFormat(UiLanguage.t(getContext(),pattern),UiLanguage.locale(getContext())).format(now); }
    private String time() { return format("en".equals(UiLanguage.language(getContext()))?"h:mm a":"a h:mm"); }
    private boolean singleLine() {
        return autoSingle && (titleClock || me.ancal.wmdroid.sdk.WmFonts.measure(paint,format("yyyy년 M월 d일 EEEE")+",") + me.ancal.wmdroid.sdk.WmFonts.measure(paint,time()) <= Math.max(0,getWidth()-60));
    }
    int rowHeight() { return singleLine()?40:2*(8+lineHeight); }
    void refresh(Date value) { now=value; setContentDescription(format("yyyy년 M월 d일 EEEE a h:mm")); invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);paint.setColor(isSelected()||isPressed()?LauncherTheme.color(getContext(),0xff00f0ff,foreground==Color.BLACK):foreground); clock.setBounds(4,4,36,36); clock.draw(canvas);
        float baseline=4+me.ancal.wmdroid.sdk.WmFonts.centerBaseline(paint,0,lineHeight,format("EEEE"));
        if(singleLine()) me.ancal.wmdroid.sdk.WmFonts.draw(canvas,format("yyyy년 M월 d일 EEEE"),56,baseline,paint);
        else {
            me.ancal.wmdroid.sdk.WmFonts.draw(canvas,format("EEEE"),56,baseline,paint);
            me.ancal.wmdroid.sdk.WmFonts.draw(canvas,format("yyyy년 M월 d일"),56,baseline+lineHeight,paint);
        }
        if(!titleClock) { String time=time(); me.ancal.wmdroid.sdk.WmFonts.draw(canvas,time,getWidth()-4-me.ancal.wmdroid.sdk.WmFonts.measure(paint,time),baseline,paint); }
    }
}
