package me.ancal.wmdroid;

import android.content.Context;
import android.graphics.drawable.Drawable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import me.ancal.wmdroid.sdk.WmAssets;

/** Host adapter for the shared folder catalog available to SDK apps. */
final class FolderIcons {
    static final String DEFAULT="";
    static final class Icon {
        final String id,title,asset;final int size;
        Icon(WmAssets.Entry row,java.util.Locale locale){id=row.id;title=row.label(locale);asset=row.asset;size=row.width;}
    }
    static List<Icon> all(Context c){
        List<Icon> icons=new ArrayList<>();
        try{for(WmAssets.Entry entry:WmAssets.list(c,"folder"))icons.add(new Icon(entry,UiLanguage.locale(c)));}
        catch(IOException unavailable){}
        return icons;
    }
    static String asset(Context c,String id){
        try{WmAssets.Entry entry=WmAssets.byId(c,id);if(entry!=null&&"folder".equals(entry.type))return entry.asset;}
        catch(IOException unavailable){}
        return "launcher/icons/start-programs.png";
    }
    static Drawable load(Context c,String id){
        try{WmAssets.Entry entry=WmAssets.byId(c,id);if(entry!=null&&"folder".equals(entry.type))return WmAssets.loadDrawable(c,id,64);}
        catch(IOException absent){}
        return LauncherArtwork.load(c,R.drawable.wm_start_programs);
    }
}
