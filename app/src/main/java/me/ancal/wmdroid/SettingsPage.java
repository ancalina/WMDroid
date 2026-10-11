package me.ancal.wmdroid;

import android.content.Context;
import android.graphics.Typeface;
import me.ancal.wmdroid.sdk.WmPaint;
import me.ancal.wmdroid.sdk.WmSettingsView;
import me.ancal.wmdroid.sdk.WmResources;

/** App-owned settings metadata, artwork and presentation preferences. */
final class SettingsPage extends WmSettingsView {
    private String iconRevision;
    interface Open {void open(String key);}
    private static final String[][] keys={{"owner","today","menu","applet_targets","home_commands","sound","fonts","theme","resource_pack","display","app_icons"},{"power","backlight","about","language","experience","finger_mouse","home","notifications","platform"},{"wireless","wifi","bluetooth","connection_module","bluetooth_state"}};
    private static final String[][] labels={{"소유자 정보","오늘","메뉴","열 앱 설정","오늘 하단바","소리 및 알림","글꼴","테마 색","리소스팩","화면 표시","Android 앱 아이콘"},{"전원","조명","정보","언어","옛 기기 동작","핑거마우스","기본 홈","알림 연결","Android 설정"},{"연결","Wi-Fi","Bluetooth","연결 모듈","Bluetooth 상태 연결"}};
    /** Keyed artwork cannot drift when tab items are inserted or reordered. */
    static int iconFor(String key){switch(key){
        case "owner":return R.drawable.wm_owner;
        case "today":case "home":return R.drawable.wm_cpl_today;
        case "applet_targets":case "home_commands":case "menu":return R.drawable.wm_cpl_menu;
        case "sound":case "notifications":return R.drawable.wm_cpl_sounds;
        case "fonts":return R.drawable.wm_settings_fonts;
        case "resource_pack":case "theme":return R.drawable.wm_settings_theme;
        case "app_icons":return R.drawable.wm_settings_app_icons;
        case "power":return R.drawable.wm_cpl_power;
        case "backlight":case "display":return R.drawable.wm_cpl_backlight;
        case "about":return R.drawable.wm_cpl_about;
        case "language":return R.drawable.wm_cpl_language;
        case "experience":return R.drawable.wm_cpl_performance;
        case "finger_mouse":return R.drawable.wm_settings_mouse;
        case "wireless":return R.drawable.wm_cpl_wireless;
        case "wifi":return R.drawable.wm_cpl_wifi;
        case "bluetooth":case "bluetooth_state":return R.drawable.wm_cpl_bluetooth;
        case "connection_module":return R.drawable.wm_cpl_connections;
        default:return R.drawable.wm_start_settings;
    }}
    SettingsPage(Context c,Typeface font,boolean rose,Runnable onUserTab,Open open){
        super(c,font,UiLanguage.language(c),LauncherTheme.selection(c,rose),LauncherTheme.color(c,0xffc9e7e9,rose),categories(c),onUserTab,open::open);
        iconRevision=WmResources.revision(c);
    }
    private static Category[] categories(Context c){
        String[] names={"개인","시스템","연결"};Category[] categories=new Category[names.length];
        for(int group=0;group<categories.length;group++){
            Item[] items=new Item[keys[group].length];
            for(int i=0;i<items.length;i++)items[i]=new Item(keys[group][i],labels[group][i],LauncherArtwork.load(c,iconFor(keys[group][i])));
            categories[group]=new Category(names[group],items);
        }
        return categories;
    }
    @Override protected WmPaint createSurface(Context c){return new RetroSurface(c,true,RetroSurface.Role.GRID);}
    void refreshIcons(){String revision=WmResources.revision(getContext());if(revision.equals(iconRevision))return;for(String[] group:keys)for(String key:group)setIcon(key,LauncherArtwork.load(getContext(),iconFor(key)));iconRevision=revision;}
    void refreshAppearance(Typeface font){super.refreshAppearance(font,UiLanguage.language(getContext()));}
}
