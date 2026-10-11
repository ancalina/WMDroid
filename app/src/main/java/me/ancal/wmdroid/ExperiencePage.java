package me.ancal.wmdroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import me.ancal.wmdroid.sdk.WmControls;
import me.ancal.wmdroid.sdk.WmStrings;

/** Launcher-only paint timing options; valid timing edits apply immediately. */
final class ExperiencePage extends LinearLayout implements WmControls.LanguageListener {
    private static final String INTEGER_ERROR=" 범위의 정수를 입력하세요.";
    private static final String MULTIPLIER_ERROR="0.25~4.0 범위의 수를 입력하세요.";
    private final SharedPreferences prefs;
    private final Typeface font;
    private final LinearLayout rows;
    private final CheckBox enabled,scan,repaint,hold;
    private final EditText delay,lines,multiplier;

    ExperiencePage(Context context,SharedPreferences prefs,Typeface font){
        super(context);this.prefs=prefs;this.font=font;
        setOrientation(VERTICAL);setBackgroundColor(ClassicUi.SURFACE);
        rows=new LinearLayout(context);rows.setOrientation(VERTICAL);
        rows.setPadding(ClassicUi.INSET,ClassicUi.INSET,ClassicUi.INSET,ClassicUi.INSET);
        enabled=check("옛 기기 동작 사용","retro_enabled",true);
        delay=number("진입 지연(ms)","applet_delay",120,0,1500,false);
        scan=check("위에서 아래로 그리기","retro_scan",true);
        lines=number("한 번에 그릴 줄 수","retro_band_lines",64,8,256,false);
        multiplier=number("지연 배수","retro_delay_scale",1000,250,4000,true);
        repaint=check("한 번 재그리기","retro_repaint",false);
        hold=check("길게 누르기 고리","retro_hold",true);
        CheckBox[] checks={enabled,scan,repaint,hold};String[] keys={"retro_enabled","retro_scan","retro_repaint","retro_hold"};for(int i=0;i<checks.length;i++){final String key=keys[i];checks[i].setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean(key,on).apply());}
        android.text.TextWatcher watch=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int c){}public void afterTextChanged(android.text.Editable value){save();}};
        for(EditText field:new EditText[]{delay,lines,multiplier})field.addTextChangedListener(watch);
        addView(new ClassicScrollView(context,rows),new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
    }
    private CheckBox check(String title,String key,boolean fallback){
        CheckBox view=new CheckBox(getContext());ClassicUi.check(view,font);WmControls.bindText(view,UiLanguage.language(getContext()),title);
        view.setGravity(Gravity.CENTER_VERTICAL);view.setChecked(prefs.getBoolean(key,fallback));
        rows.addView(view,new LayoutParams(LayoutParams.MATCH_PARENT,ClassicUi.ROW_HEIGHT));return view;
    }
    private EditText number(String title,String key,int fallback,int min,int max,boolean decimal){
        LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);row.setBaselineAligned(false);
        TextView label=ClassicUi.text(getContext(),font,"");WmControls.bindText(label,UiLanguage.language(getContext()),title);row.addView(label,new LayoutParams(232,ClassicUi.ROW_HEIGHT));
        EditText view=new EditText(getContext());view.setInputType(InputType.TYPE_CLASS_NUMBER|(decimal?InputType.TYPE_NUMBER_FLAG_DECIMAL:0));view.setSingleLine(true);ClassicUi.input(view,font);
        view.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);WmControls.bindDescription(view,UiLanguage.language(getContext()),title);
        int stored=Math.max(min,Math.min(max,prefs.getInt(key,fallback)));
        view.setText(decimal?Float.toString(stored/1000f):Integer.toString(stored));
        row.addView(view,new LayoutParams(0,ClassicUi.ROW_HEIGHT-4,1));
        rows.addView(row,new LayoutParams(LayoutParams.MATCH_PARENT,ClassicUi.ROW_HEIGHT));return view;
    }
    @Override public void onWmLanguageChanged(String language){
        if(delay.getError()!=null)delay.setError("0~1500"+WmStrings.text(getContext(),language,INTEGER_ERROR));
        if(lines.getError()!=null)lines.setError("8~256"+WmStrings.text(getContext(),language,INTEGER_ERROR));
        if(multiplier.getError()!=null)multiplier.setError(WmStrings.text(getContext(),language,MULTIPLIER_ERROR));
    }
    private Integer value(EditText field,int min,int max){
        field.setError(null);
        try{
            int value=Integer.parseInt(field.getText().toString().trim());
            if(value>=min&&value<=max)return value;
        }catch(NumberFormatException ignored){}
        field.setError(min+"~"+max+UiLanguage.t(getContext(),INTEGER_ERROR));return null;
    }
    private Integer multiplierValue(){
        multiplier.setError(null);
        try{
            double value=Double.parseDouble(multiplier.getText().toString().trim());
            if(value>=0.25&&value<=4.0)return (int)Math.round(value*1000);
        }catch(NumberFormatException ignored){}
        multiplier.setError(UiLanguage.t(getContext(),MULTIPLIER_ERROR));return null;
    }
    public boolean save(){
        Integer delayValue=value(delay,0,1500),linesValue=value(lines,8,256),scaleValue=multiplierValue();
        if(delayValue==null||linesValue==null||scaleValue==null){
            (delayValue==null?delay:linesValue==null?lines:multiplier).requestFocus();return false;
        }
        prefs.edit().putBoolean("retro_enabled",enabled.isChecked()).putInt("applet_delay",delayValue)
            .putBoolean("retro_scan",scan.isChecked()).putInt("retro_band_lines",linesValue).putInt("retro_delay_scale",scaleValue).remove("retro_paint_ms")
            .putBoolean("retro_repaint",repaint.isChecked()).putBoolean("retro_hold",hold.isChecked()).apply();return true;
    }
}
