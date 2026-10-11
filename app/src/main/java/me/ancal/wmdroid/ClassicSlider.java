package me.ancal.wmdroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/** Discrete GDI trackbar, painted in VGA coordinates. */
final class ClassicSlider extends View implements me.ancal.wmdroid.sdk.WmControls.RestyleListener,me.ancal.wmdroid.sdk.WmControls.LanguageListener {
    interface Change {void set(int value);}
    private final Paint p=new Paint();private final Change change;private int value;private int face;private String language;
    ClassicSlider(Context c,int initial,Change change){super(c);this.value=initial;this.change=change;language=UiLanguage.language(c);face=me.ancal.wmdroid.sdk.WmPalette.face(LauncherTheme.selection(c,false));setFocusable(true);describe();}
    public void onWmRestyle(android.graphics.Typeface regular,android.graphics.Typeface bold,int selection,int tabColor){face=me.ancal.wmdroid.sdk.WmPalette.face(selection);invalidate();}
    public void onWmLanguageChanged(String language){this.language=language;describe();}
    void value(int value){this.value=Math.max(0,Math.min(100,value));describe();invalidate();}
    private void describe(){setContentDescription(String.format(me.ancal.wmdroid.sdk.WmStrings.locale(language),me.ancal.wmdroid.sdk.WmStrings.text(getContext(),language,"화면 밝기 %d%%"),value));}
    private void fill(Canvas c,int color,float l,float t,float r,float b){p.setColor(color);c.drawRect(l,t,r,b,p);}
    @Override protected void onDraw(Canvas c){float y=getHeight()/2f,l=18,r=getWidth()-18,x=l+(r-l)*value/100f;
        fill(c,0xff808080,l,y-3,r,y+3);fill(c,0xffffffff,l,y+1,r,y+3);
        for(int i=0;i<=10;i++)fill(c,0xff000000,l+(r-l)*i/10f,y+18,l+(r-l)*i/10f+2,y+24);
        fill(c,isEnabled()?face:0xffcccccc,x-12,y-14,x+12,y+14);fill(c,0xffffffff,x-12,y-14,x+10,y-12);fill(c,0xffffffff,x-12,y-14,x-10,y+12);fill(c,0xff000000,x+10,y-14,x+12,y+14);fill(c,0xff000000,x-12,y+12,x+12,y+14);
    }
    private void choose(float x){if(!isEnabled())return;int next=Math.round(Math.max(0,Math.min(1,(x-18)/(getWidth()-36)))*20)*5;if(next!=value){value(next);change.set(next);}}
    @Override public boolean onTouchEvent(MotionEvent e){if(!isEnabled())return false;switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:case MotionEvent.ACTION_MOVE:case MotionEvent.ACTION_UP:getParent().requestDisallowInterceptTouchEvent(true);choose(e.getX());if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;case MotionEvent.ACTION_CANCEL:return true;}return true;}
    @Override public boolean onKeyDown(int key,KeyEvent e){if(isEnabled()&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)){int next=Math.max(0,Math.min(100,value+(key==KeyEvent.KEYCODE_DPAD_RIGHT?5:-5)));value(next);change.set(next);return true;}return super.onKeyDown(key,e);}
    @Override public boolean performClick(){super.performClick();return true;}
}
