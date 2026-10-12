package me.ancal.wmdroid;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import java.util.Locale;
import me.ancal.wmdroid.sdk.WmStrings;

/** App-authored UI only. Exact catalog keys never rewrite user or provider data. */
final class UiLanguage {
    private UiLanguage() {}

    static String language(Context context) {
        String selected=context.getSharedPreferences("today",Context.MODE_PRIVATE).getString("language",null);
        if(selected!=null&&!"system".equals(selected))return WmStrings.language(context,selected);
        Configuration config=context.getResources().getConfiguration();
        String[] available=WmStrings.languages(context);
        if(Build.VERSION.SDK_INT>=24){
            for(int i=0;i<config.getLocales().size();i++){
                String match=matchingLanguage(config.getLocales().get(i),available);
                if(match!=null)return match;
            }
        }else{
            String match=matchingLanguage(config.locale,available);
            if(match!=null)return match;
        }
        return "en";
    }

    private static String matchingLanguage(Locale locale,String[] available){
        if(locale==null)return null;
        String tag=Build.VERSION.SDK_INT>=21?locale.toLanguageTag():locale.toString().replace('_','-');
        for(String candidate=tag.toLowerCase(Locale.US);!candidate.isEmpty();){
            for(String name:available)if(name.equals(candidate))return name;
            int dash=candidate.lastIndexOf('-');
            if(dash<0)break;
            candidate=candidate.substring(0,dash);
        }
        return null;
    }

    static Locale locale(Context context) {
        return WmStrings.locale(language(context));
    }

    static String t(Context context,String authoredText){return me.ancal.wmdroid.sdk.WmStrings.text(context,language(context),authoredText);}

    /** Translate the complete authored template; arguments retain their original values. */
    static String format(Context context, String koreanPattern, Object... values) {
        return String.format(locale(context), t(context, koreanPattern), values);
    }

}
