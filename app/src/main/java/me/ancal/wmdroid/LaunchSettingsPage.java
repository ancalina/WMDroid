package me.ancal.wmdroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;
import me.ancal.wmdroid.sdk.WmControls;

/** Launch destinations use the same command IDs as Start and the shared shell. */
final class LaunchSettingsPage extends LinearLayout implements WmControls.LanguageListener {
    interface Choose {void open(String key,String fallback,boolean applet);}
    private final SharedPreferences prefs;
    private final boolean applets;
    private final String[] keys,defaults;
    private final TextView[] choices=new TextView[2];
    private final SharedPreferences.OnSharedPreferenceChangeListener changed;
    LaunchSettingsPage(Context context,SharedPreferences prefs,Typeface font,boolean applets,Choose choose){
        super(context);this.prefs=prefs;this.applets=applets;
        keys=applets?new String[]{"applet_browser_target","applet_media_target"}:new String[]{"home_left_command","home_right_command"};
        defaults=applets?new String[]{"browser","media"}:new String[]{"phone","contacts"};
        changed=(p,key)->{if(key!=null&&(key.equals(keys[0])||key.equals(keys[1])||key.equals("language")))refresh();};
        setOrientation(VERTICAL);setPadding(16,8,16,0);setBackgroundColor(ClassicUi.SURFACE);
        for(int i=0;i<2;i++){
            final int index=i;TextView caption=ClassicUi.text(context,font,"");
            WmControls.bindText(caption,UiLanguage.language(context),applets?(i==0?"Internet Explorer":"Windows Media"):(i==0?"왼쪽 항목":"오른쪽 항목"));
            addView(caption,new LayoutParams(-1,40));
            TextView button=ClassicUi.text(context,font,"");ClassicUi.button(button,false);button.setSingleLine(true);button.setEllipsize(android.text.TextUtils.TruncateAt.END);button.setOnClickListener(v->choose.open(keys[index],defaults[index],applets));choices[i]=button;addView(button,new LayoutParams(-1,48));
        }
        refresh();
    }
    private void refresh(){onWmLanguageChanged(UiLanguage.language(getContext()));}
    @Override public void onWmLanguageChanged(String language){
        for(int i=0;i<2;i++){String id=prefs.getString(keys[i],defaults[i]);choices[i].setText(applets&&id.equals(defaults[i])?me.ancal.wmdroid.sdk.WmStrings.text(getContext(),language,"내장 애플릿"):MenuSettingsPage.label(getContext(),id,language));}
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();prefs.registerOnSharedPreferenceChangeListener(changed);refresh();}
    @Override protected void onDetachedFromWindow(){prefs.unregisterOnSharedPreferenceChangeListener(changed);super.onDetachedFromWindow();}
}
