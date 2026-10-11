package me.ancal.wmdroid;
import android.content.Context;
import android.view.View;
/** Theme adapter to the exact same SDK scroll surface used by external applets. */
final class ClassicScrollView extends me.ancal.wmdroid.sdk.WmScrollView {
    ClassicScrollView(Context c,View contents){this(c,contents,LayoutParams.WRAP_CONTENT);}
    ClassicScrollView(Context c,View contents,int height){super(c,contents,height,LauncherTheme.color(c,0xffc9e7e9,c.getSharedPreferences("today",0).getBoolean("rose",false)));}
}
