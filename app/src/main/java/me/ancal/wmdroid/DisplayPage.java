package me.ancal.wmdroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import me.ancal.wmdroid.sdk.WmControls;
import me.ancal.wmdroid.sdk.WmPalette;

/** Immediate launcher display preferences; control geometry is provisional. */
final class DisplayPage extends LinearLayout implements WmControls.RestyleListener {
    // Keep at least logical VGA width480: cap enlargement at100%, while50..99%
    // provides the requested smaller UI. The shared display surface applies it.
    private static final int MIN_SCALE=50,MAX_SCALE=100;
    private static final String[] MODES={"full","black","status"};
    private final SharedPreferences prefs;
    private final SeekBar scale;
    private final TextView percentage;

    DisplayPage(Context context,SharedPreferences prefs,Typeface font){
        super(context);this.prefs=prefs;setOrientation(VERTICAL);setPadding(16,16,16,0);setBackgroundColor(ClassicUi.SURFACE);
        addView(caption(font,"화면 배율"),new LayoutParams(-1,ClassicUi.ROW_HEIGHT));
        int initial=Math.max(MIN_SCALE,Math.min(MAX_SCALE,prefs.getInt("ui_scale_percent",MAX_SCALE)));
        percentage=ClassicUi.text(context,font,initial+"%");percentage.setTag("scale_percent");addView(percentage,new LayoutParams(-1,ClassicUi.ROW_HEIGHT));
        scale=new SeekBar(context);scale.setTag("scale_seek");scale.setMax(MAX_SCALE-MIN_SCALE);scale.setProgress(initial-MIN_SCALE);WmControls.bindDescription(scale,UiLanguage.language(context),"화면 배율");addView(scale,new LayoutParams(-1,48));
        scale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){int value=MIN_SCALE+progress;percentage.setText(value+"%");DisplayPage.this.prefs.edit().putInt("ui_scale_percent",value).apply();}
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
        addView(caption(font,"화면 모드"),new LayoutParams(-1,ClassicUi.ROW_HEIGHT));
        RadioGroup modes=new RadioGroup(context);modes.setOrientation(VERTICAL);String[] labels={"전체 화면","검은 여백","상태 표시줄"};String selected=prefs.getString("screen_mode","full");int checked=34001;
        for(int i=0;i<MODES.length;i++){RadioButton radio=new RadioButton(context);radio.setId(34001+i);radio.setTag("screen_mode_"+MODES[i]);WmControls.radio(radio,font);WmControls.bindText(radio,UiLanguage.language(context),labels[i]);modes.addView(radio,new RadioGroup.LayoutParams(-1,ClassicUi.ROW_HEIGHT));if(MODES[i].equals(selected))checked=radio.getId();}
        modes.check(checked);modes.setOnCheckedChangeListener((group,id)->{int index=id-34001;if(index>=0&&index<MODES.length)DisplayPage.this.prefs.edit().putString("screen_mode",MODES[index]).apply();});addView(modes,new LayoutParams(-1,-2));
        onWmRestyle(font,font,LauncherTheme.selection(context,prefs.getBoolean("rose",false)),0);
    }
    private TextView caption(Typeface font,String authored){TextView view=ClassicUi.text(getContext(),font,"");WmControls.bindText(view,UiLanguage.language(getContext()),authored);return view;}
    @Override public void onWmRestyle(Typeface regular,Typeface bold,int selection,int tabColor){
        LayerDrawable track=new LayerDrawable(new Drawable[]{new ColorDrawable(ClassicUi.SHADOW),new ClipDrawable(new ColorDrawable(selection),Gravity.LEFT,ClipDrawable.HORIZONTAL)});track.setId(0,android.R.id.background);track.setId(1,android.R.id.progress);scale.setProgressDrawable(track);
        GradientDrawable thumb=new GradientDrawable();thumb.setColor(WmPalette.face(selection));thumb.setStroke(ClassicUi.BORDER,Color.BLACK);thumb.setSize(20,36);scale.setThumb(thumb);
    }
}
