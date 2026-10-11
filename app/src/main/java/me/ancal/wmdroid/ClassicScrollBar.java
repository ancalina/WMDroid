package me.ancal.wmdroid;
import android.content.Context;
/** ROM theme adapter; all geometry, paint and gestures belong to the SDK. */
final class ClassicScrollBar extends me.ancal.wmdroid.sdk.WmScrollBar {
    ClassicScrollBar(Context c,Host host){super(c,host,LauncherTheme.color(c,0xffc9e7e9,c.getSharedPreferences("today",0).getBoolean("rose",false)));setContentDescription(UiLanguage.t(c,"세로 스크롤"));}
}
