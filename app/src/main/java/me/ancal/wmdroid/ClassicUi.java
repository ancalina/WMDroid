package me.ancal.wmdroid;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import me.ancal.wmdroid.sdk.WmControls;

/** Immediate, opaque GDI-style selection; no Android ripple or fade. */
final class ClassicUi {
    static final int VGA_WIDTH=me.ancal.wmdroid.sdk.WmMetrics.WIDTH, BAR_HEIGHT=me.ancal.wmdroid.sdk.WmMetrics.BAR, TEXT_SIZE=me.ancal.wmdroid.sdk.WmMetrics.FONT, INSET=me.ancal.wmdroid.sdk.WmMetrics.INSET, BORDER=me.ancal.wmdroid.sdk.WmMetrics.BORDER;
    static final int ROW_HEIGHT=WmControls.ROW_HEIGHT, TAB_HEIGHT=WmControls.TAB_HEIGHT, TAB_STRIP_HEIGHT=me.ancal.wmdroid.sdk.WmMetrics.TAB;
    static final int SURFACE=WmControls.SURFACE, FACE=WmControls.FACE, SHADOW=WmControls.SHADOW;

    static TextView text(Context context,Typeface font,String value){
        return WmControls.text(context,font,value);
    }
    static void textStyle(TextView view,Typeface font){
        WmControls.textStyle(view,font);
    }
    static void input(EditText view,Typeface font){
        WmControls.input(view,font);
    }
    static void check(CheckBox view,Typeface font){
        WmControls.check(view,font);
    }
    static int tabWidth(TextView view){return WmControls.tabWidth(view);}
    static void tab(TextView view,boolean selected){
        WmControls.tab(view,selected,LauncherTheme.color(view.getContext(),0xffc9e7e9,false));
    }
    static StateListDrawable background(int normal,int selected){
        return WmControls.background(normal,selected);
    }
    static void entry(TextView view,boolean rose){
        WmControls.entry(view,LauncherTheme.selection(view.getContext(),rose));
    }
    static void entryBackground(View view,boolean rose){WmControls.entryBackground(view,LauncherTheme.selection(view.getContext(),rose));}
    static void button(TextView view,boolean rose){WmControls.button(view,LauncherTheme.selection(view.getContext(),rose));}
    static void select(View container,View chosen){
        WmControls.select(container,chosen);
    }
}
