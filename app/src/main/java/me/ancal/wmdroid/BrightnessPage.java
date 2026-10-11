package me.ancal.wmdroid;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import me.ancal.wmdroid.sdk.WmControls;
import me.ancal.wmdroid.sdk.WmStrings;

/** In-launcher backlight control. Global writes occur only after explicit opt-in. */
final class BrightnessPage extends LinearLayout implements WmControls.LanguageListener {
    private final Activity activity;private final SharedPreferences prefs;private final TextView status;private final CheckBox automatic;private final ClassicSlider slider;
    BrightnessPage(Activity a,SharedPreferences prefs,Typeface font){
        super(a);this.activity=a;this.prefs=prefs;setOrientation(VERTICAL);setPadding(16,16,16,0);setBackgroundColor(ClassicUi.SURFACE);
        addView(caption(font,"화면 밝기"),new LayoutParams(-1,40));
        status=ClassicUi.text(a,font,"");addView(status,new LayoutParams(-1,72));
        int initial=prefs.getInt("backlight_percent",Math.round(Settings.System.getInt(a.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,128)*100f/255));
        automatic=new CheckBox(a);ClassicUi.check(automatic,font);WmControls.bindText(automatic,UiLanguage.language(a),"시스템 밝기 사용");automatic.setChecked(!prefs.getBoolean("backlight_override",false));
        addView(automatic,new LayoutParams(-1,ClassicUi.ROW_HEIGHT));
        slider=new ClassicSlider(a,initial,value->{prefs.edit().putInt("backlight_percent",value).putBoolean("backlight_override",true).apply();apply(a,prefs);writeGlobal();refresh();});slider.setEnabled(!automatic.isChecked());addView(slider,new LayoutParams(-1,80));
        automatic.setOnCheckedChangeListener((v,checked)->{prefs.edit().putBoolean("backlight_override",!checked).apply();slider.setEnabled(!checked);apply(a,prefs);writeGlobal();refresh();});
        TextView global=caption(font,"기기 전체에도 적용");ClassicUi.entry(global,false);global.setPadding(ClassicUi.INSET,0,ClassicUi.INSET,0);global.setOnClickListener(v->{
            if(DeviceApi.canWrite(a)){prefs.edit().putBoolean("backlight_global",!prefs.getBoolean("backlight_global",false)).apply();writeGlobal();refresh();}
            else a.startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:"+a.getPackageName())));
        });addView(global,new LayoutParams(-1,48));
        addView(caption(font,"기기 전체 적용은 시스템 설정 변경 권한이 필요합니다.\n권한 허용 뒤 위 항목을 다시 누르세요."),new LayoutParams(-1,-2));refresh();
    }
    static void apply(Activity a,SharedPreferences prefs){WindowManager.LayoutParams p=a.getWindow().getAttributes();p.screenBrightness=prefs.getBoolean("backlight_override",false)?Math.max(.02f,prefs.getInt("backlight_percent",50)/100f):WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;a.getWindow().setAttributes(p);}
    private void writeGlobal(){if(!prefs.getBoolean("backlight_global",false)||!DeviceApi.canWrite(activity))return;
        try{Settings.System.putInt(activity.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,automatic.isChecked()?Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC:Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);if(!automatic.isChecked())Settings.System.putInt(activity.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,Math.max(1,Math.round(prefs.getInt("backlight_percent",50)*255f/100)));}catch(SecurityException revoked){prefs.edit().putBoolean("backlight_global",false).apply();}}
    private TextView caption(Typeface font,String authored){TextView view=ClassicUi.text(getContext(),font,"");WmControls.bindText(view,UiLanguage.language(getContext()),authored);return view;}
    @Override public void onWmLanguageChanged(String language){refresh(language);}
    private void refresh(){refresh(UiLanguage.language(getContext()));}
    private String text(String language,String authored){return WmStrings.text(getContext(),language,authored);}
    private void refresh(String language){status.setText((automatic.isChecked()?text(language,"시스템 설정에 따릅니다."):text(language,"런처 밝기: ")+prefs.getInt("backlight_percent",50)+"%")+text(language,"\n기기 전체 적용: ")+(prefs.getBoolean("backlight_global",false)&&DeviceApi.canWrite(activity)?text(language,"설정"):text(language,"해제")));}
}
