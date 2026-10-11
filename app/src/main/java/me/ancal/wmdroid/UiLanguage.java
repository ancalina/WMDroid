package me.ancal.wmdroid;

import android.content.Context;
import java.util.Locale;
import me.ancal.wmdroid.sdk.WmStrings;

/** App-authored UI only. Exact catalog keys never rewrite user or provider data. */
final class UiLanguage {
    private UiLanguage() {}

    static String language(Context context) {
        return WmStrings.language(context,context.getSharedPreferences("today",Context.MODE_PRIVATE).getString("language","ko"));
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
