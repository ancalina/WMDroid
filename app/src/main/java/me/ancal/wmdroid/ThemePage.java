package me.ancal.wmdroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import me.ancal.wmdroid.sdk.WmControls;
import me.ancal.wmdroid.sdk.WmStrings;

/** Local theme preview; valid edits apply immediately; malformed hex never overwrites the last valid color. */
final class ThemePage extends LinearLayout implements WmControls.LanguageListener {
    private static final int DEFAULT_COLOR=0xff15afbc;
    private static final int[] PRESETS={DEFAULT_COLOR,0xff246ab5,0xff7b46a8,0xffb03070,0xff3b9b56,0xffb58732,0xff808080};
    private static final String[] NAMES={"청록","파랑","보라","자주","초록","황토","회색"};
    private static final String COLOR_ERROR="색상은 #RRGGBB 형식의 6자리 16진수로 입력하세요.";
    private final SharedPreferences prefs;
    private final CheckBox custom,tint;
    private final EditText hex;
    private final ThemePreview preview;
    private final LinearLayout[] presetCells=new LinearLayout[PRESETS.length];

    ThemePage(Context context,SharedPreferences prefs,Typeface font){
        super(context);this.prefs=prefs;setOrientation(VERTICAL);setBackgroundColor(ClassicUi.SURFACE);
        LinearLayout rows=new LinearLayout(context);rows.setOrientation(VERTICAL);
        rows.setPadding(ClassicUi.INSET,ClassicUi.INSET,ClassicUi.INSET,ClassicUi.INSET);
        custom=check(context,font,"사용자 색 사용",prefs.getBoolean("theme_custom",false));
        rows.addView(custom,new LayoutParams(LayoutParams.MATCH_PARENT,ClassicUi.ROW_HEIGHT));
        LinearLayout colorRow=new LinearLayout(context);colorRow.setGravity(Gravity.CENTER_VERTICAL);colorRow.setBaselineAligned(false);
        colorRow.addView(caption(context,font,"색상 (#RRGGBB)"),new LayoutParams(232,ClassicUi.ROW_HEIGHT));
        hex=new EditText(context);hex.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hex.setSingleLine(true);ClassicUi.input(hex,font);hex.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);WmControls.bindDescription(hex,UiLanguage.language(context),"색상 코드");
        int initial=prefs.getInt("theme_color",DEFAULT_COLOR)|0xff000000;hex.setText(format(initial));
        colorRow.addView(hex,new LayoutParams(0,ClassicUi.ROW_HEIGHT-4,1));rows.addView(colorRow,new LayoutParams(LayoutParams.MATCH_PARENT,ClassicUi.ROW_HEIGHT));
        rows.addView(caption(context,font,"기본 색"),new LayoutParams(LayoutParams.MATCH_PARENT,ClassicUi.ROW_HEIGHT));
        LinearLayout presetRow=null;
        for(int i=0;i<PRESETS.length;i++){
            if(i%4==0){presetRow=new LinearLayout(context);rows.addView(presetRow,new LayoutParams(LayoutParams.MATCH_PARENT,ClassicUi.ROW_HEIGHT));}
            final int index=i;LinearLayout cell=new LinearLayout(context);presetCells[i]=cell;
            cell.setGravity(Gravity.CENTER_VERTICAL);cell.setPadding(4,0,4,0);ClassicUi.entryBackground(cell,prefs.getBoolean("rose",false));
            cell.setContentDescription(UiLanguage.t(context,NAMES[i])+" "+format(PRESETS[i]));cell.setSoundEffectsEnabled(false);
            View swatch=new View(context);GradientDrawable chip=new GradientDrawable();chip.setColor(PRESETS[i]);chip.setStroke(ClassicUi.BORDER,ClassicUi.SHADOW);swatch.setBackgroundDrawable(chip);
            LayoutParams chipLayout=new LayoutParams(24,24);chipLayout.rightMargin=4;cell.addView(swatch,chipLayout);
            TextView label=caption(context,font,NAMES[i]);ClassicUi.entry(label,prefs.getBoolean("rose",false));label.setDuplicateParentStateEnabled(true);cell.addView(label,new LayoutParams(0,ClassicUi.ROW_HEIGHT,1));
            cell.setOnClickListener(v->hex.setText(format(PRESETS[index])));presetRow.addView(cell,new LayoutParams(0,ClassicUi.ROW_HEIGHT,1));
        }
        if(presetRow!=null)for(int i=PRESETS.length%4;i>0&&i<4;i++)presetRow.addView(new View(context),new LayoutParams(0,ClassicUi.ROW_HEIGHT,1));
        tint=check(context,font,"배경에도 색조 적용",prefs.getBoolean("wallpaper_tint",true));
        rows.addView(tint,new LayoutParams(LayoutParams.MATCH_PARENT,ClassicUi.ROW_HEIGHT));
        rows.addView(caption(context,font,"미리보기"),new LayoutParams(LayoutParams.MATCH_PARENT,ClassicUi.ROW_HEIGHT));
        preview=new ThemePreview(context,prefs,font);
        rows.addView(preview,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        hex.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){}
            public void afterTextChanged(Editable text){Integer color=parse(text.toString());if(color!=null){hex.setError(null);save();showColor(color);}}
        });
        custom.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("theme_custom",on).apply();preview.refresh();});tint.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("wallpaper_tint",on).apply();preview.refresh();});
        showColor(initial);addView(new ClassicScrollView(context,rows),new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
    }
    private static TextView caption(Context context,Typeface font,String authored){TextView view=ClassicUi.text(context,font,"");WmControls.bindText(view,UiLanguage.language(context),authored);return view;}
    private static CheckBox check(Context context,Typeface font,String title,boolean checked){
        CheckBox view=new CheckBox(context);ClassicUi.check(view,font);WmControls.bindText(view,UiLanguage.language(context),title);view.setGravity(Gravity.CENTER_VERTICAL);view.setChecked(checked);return view;
    }
    @Override public void onWmLanguageChanged(String language){
        for(int i=0;i<PRESETS.length;i++)presetCells[i].setContentDescription(WmStrings.text(getContext(),language,NAMES[i])+" "+format(PRESETS[i]));
        if(hex.getError()!=null)hex.setError(WmStrings.text(getContext(),language,COLOR_ERROR));
    }
    private static String format(int color){return String.format(Locale.ROOT,"#%06X",color&0xffffff);}
    private static Integer parse(String value){
        String digits=value.trim();if(digits.startsWith("#"))digits=digits.substring(1);
        return digits.matches("[0-9a-fA-F]{6}")?0xff000000|Integer.parseInt(digits,16):null;
    }
    private void showColor(int color){
        preview.refresh();
        for(int i=0;i<PRESETS.length;i++)presetCells[i].setSelected(PRESETS[i]==color);
    }
    public boolean save(){
        Integer color=parse(hex.getText().toString());
        if(color==null){hex.setError(UiLanguage.t(getContext(),COLOR_ERROR));hex.requestFocus();return false;}
        hex.setError(null);prefs.edit().putBoolean("theme_custom",custom.isChecked()).putInt("theme_color",color)
            .putBoolean("wallpaper_tint",tint.isChecked()).apply();return true;
    }
}
