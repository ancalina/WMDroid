package me.ancal.wmdroid;

import android.content.*;
import android.content.pm.*;
import android.database.*;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.content.res.AssetFileDescriptor;
import java.io.*;
import java.util.*;
import me.ancal.wmdroid.sdk.WmShell;

/** Appearance/menu queries, a cosmetic clock-mode setter and constrained audio memory. No PIM. */
public final class ShellProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public android.os.Bundle call(String method,String arg,android.os.Bundle extras){
        if("clock_mode".equals(method)){
            if(!"analog".equals(arg)&&!"digital".equals(arg))throw new IllegalArgumentException("Clock mode must be analog or digital");
            // Public SDK cosmetic preference only. No arbitrary preference keys or system settings.
            boolean saved=getContext().getSharedPreferences("today",0).edit().putBoolean("analog_clock","analog".equals(arg)).commit();
            if(saved)getContext().getContentResolver().notifyChange(WmShell.STATE,null);
            android.os.Bundle result=new android.os.Bundle();result.putBoolean("saved",saved);return result;
        }
        if(!"remember_music".equals(method)&&!"saved_music".equals(method))throw new IllegalArgumentException("Unknown shell call");
        Context c=getContext();c.enforceCallingOrSelfPermission(android.Manifest.permission.MODIFY_AUDIO_SETTINGS,"Audio memory requires MODIFY_AUDIO_SETTINGS");
        android.media.AudioManager audio=(android.media.AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        SharedPreferences p=c.getSharedPreferences("today",0);
        synchronized(ShellProvider.class){
            // Ignore caller data. Only capture the device's current audible level
            // before the requesting app changes its own permitted audio state.
            if("remember_music".equals(method)&&audio.getRingerMode()==android.media.AudioManager.RINGER_MODE_NORMAL)p.edit().putInt("volume_saved_music",audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)).commit();
            int max=audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
            int level=p.getInt("volume_saved_music",audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC));
            android.os.Bundle result=new android.os.Bundle();result.putInt("level",Math.max(0,Math.min(max,level)));return result;
        }
    }
    @Override public String getType(Uri uri){return uri.getPath().startsWith("/resources/")?"application/zip":me.ancal.wmdroid.sdk.WmFontFile.isSource(uri.toString())?"application/font-sfnt":WmShell.START_BACKGROUND.equals(uri)||uri.getPath().startsWith("/icons/")?"image/png":"vnd.android.cursor.dir/vnd.me.ancal.wmdroid.shell";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        Context c=getContext();SharedPreferences p=c.getSharedPreferences("today",0);
        if(WmShell.RESOURCES.equals(uri)){
            MatrixCursor rows=new MatrixCursor(new String[]{"generation"});
            try{me.ancal.wmdroid.sdk.WmResourcePack pack=me.ancal.wmdroid.sdk.WmResources.prepare(c);rows.addRow(new Object[]{pack==null?"":pack.generation()});}
            catch(IOException invalid){throw new IllegalStateException("Resource pack unavailable",invalid);}
            rows.setNotificationUri(c.getContentResolver(),WmShell.STATE);return rows;
        }
        if("/state".equals(uri.getPath())){MatrixCursor rows=new MatrixCursor(WmShell.STATE_COLUMNS);rows.addRow(new Object[]{LauncherFonts.selected(p).regular,p.getBoolean("font_hybrid",true)?1:0,LauncherTheme.selection(c,p.getBoolean("rose",false)),UiLanguage.language(c),p.getBoolean("title_clock",true)?1:0,p.getBoolean("analog_clock",false)?1:0,p.getBoolean("retro_enabled",true)?1:0,p.getInt("applet_delay",120),p.getBoolean("retro_scan",true)?1:0,p.getInt("retro_band_lines",64),p.getInt("retro_delay_scale",1000),p.getBoolean("retro_repaint",false)?1:0,p.getBoolean("retro_hold",true)?1:0,p.getBoolean("android_icons_unframed",false)?1:0,p.getString("android_icons_mode","all"),p.getString("android_icons_list","[]"),p.getBoolean("rose",false)?Color.BLACK:Color.WHITE,me.ancal.wmdroid.sdk.WmViewport.scalePercent(p.getInt("ui_scale_percent",100)),me.ancal.wmdroid.sdk.WmViewport.screenMode(p.getString("screen_mode","full")),LauncherFonts.selected(p).bold,p.getBoolean("touch_sound_enabled",false)?Math.max(1,Math.min(2,p.getInt("touch_sound_level",2))):0,p.getBoolean("rose",false)?"mugunghwa":"classic",p.getBoolean("theme_custom",false)&&p.getBoolean("wallpaper_tint",true)?1:0,p.getInt("theme_color",0xff15afbc),p.getBoolean("font_smoothing",true)?1:0});rows.setNotificationUri(c.getContentResolver(),WmShell.STATE);return rows;}
        if("/menu".equals(uri.getPath())){MatrixCursor rows=new MatrixCursor(WmShell.MENU_COLUMNS);for(WmShell.Entry e:entries(c,p))rows.addRow(new Object[]{e.id,e.parent,e.title,e.icon,e.component,e.command,e.folder?1:0});rows.setNotificationUri(c.getContentResolver(),WmShell.STATE);return rows;}throw new IllegalArgumentException("Unknown shell resource");
    }
    static List<WmShell.Entry> entries(Context c,SharedPreferences p){List<WmShell.Entry> rows=new ArrayList<>();append(c,p,"",rows,new HashSet<String>());rows.add(entry(c,p,"programs",""));rows.add(entry(c,p,"settings",""));rows.add(entry(c,p,"help",""));return rows;}
    private static void append(Context c,SharedPreferences p,String parent,List<WmShell.Entry> result,Set<String> visited){if(!visited.add(parent))return;for(String id:MenuPreferences.read(p,parent)){WmShell.Entry e=entry(c,p,id,parent);if(e!=null){result.add(e);if(e.folder)append(c,p,id,result,visited);}}}
    private static WmShell.Entry entry(Context c,SharedPreferences p,String id,String parent){
        if(MenuPreferences.isFolder(id)){org.json.JSONObject f=MenuPreferences.folder(p,id);if(f==null)return null;String icon=f.optString("icon",FolderIcons.DEFAULT);return new WmShell.Entry(id,parent,MenuSettingsPage.label(c,id),icon.isEmpty()?"":new Uri.Builder().scheme("content").authority("me.ancal.wmdroid.shell").appendPath("icons").appendPath(icon).build().toString(),"","",true);}
        int icon=0;String title=null;switch(id){case "today":icon=R.drawable.wm_start_today;break;case "calendar":icon=R.drawable.wm_calendar;break;case "contacts":icon=R.drawable.wm_contacts;break;case "notes":icon=R.drawable.wm_notes;break;case "messages":icon=R.drawable.wm_mail;break;case "phone":icon=R.drawable.wm_phone_hd;break;case "browser":icon=R.drawable.wm_browser;break;case "media":icon=R.drawable.wm_media;break;case "programs":icon=R.drawable.wm_start_programs;title=UiLanguage.t(c,"프로그램");break;case "settings":icon=R.drawable.wm_start_settings;title=UiLanguage.t(c,"설정");break;case "help":icon=R.drawable.wm_help;title=UiLanguage.t(c,"도움말");break;}
        if(icon!=0)return new WmShell.Entry(id,parent,title==null?MenuSettingsPage.label(c,id):title,"android.resource://"+c.getPackageName()+"/"+icon,"",id,false);
        ComponentName component=ComponentName.unflattenFromString(id);if(component==null||me.ancal.wmdroid.sdk.WmIconPolicy.hidden(p,id))return null;
        try{ActivityInfo info=c.getPackageManager().getActivityInfo(component,0);if(!info.exported||!info.enabled)return null;int image=info.getIconResource();return new WmShell.Entry(id,parent,info.loadLabel(c.getPackageManager()).toString(),image==0?"":"android.resource://"+info.packageName+"/"+image,component.flattenToString(),"",false);}catch(PackageManager.NameNotFoundException absent){return null;}
    }
    @Override public AssetFileDescriptor openAssetFile(Uri uri,String mode)throws FileNotFoundException{
        if(uri.getPath().startsWith("/resources/")){
            if(!"r".equals(mode)||uri.getPathSegments().size()!=2||uri.getQuery()!=null||uri.getFragment()!=null)throw new FileNotFoundException("Read-only resource pack snapshot");
            try{
                final me.ancal.wmdroid.sdk.WmResourcePack pack=me.ancal.wmdroid.sdk.WmResources.prepare(getContext());
                if(pack==null||!pack.generation().equals(uri.getLastPathSegment()))throw new FileNotFoundException("Resource pack changed; query generation again");
                ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
                new Thread(()->{try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){pack.writeZip(out);}catch(IOException gone){}},"WMDroid-resource-pack").start();
                return new AssetFileDescriptor(pipe[0],0,AssetFileDescriptor.UNKNOWN_LENGTH);
            }catch(IOException error){throw new FileNotFoundException("Resource pack unavailable");}
        }
        if(me.ancal.wmdroid.sdk.WmFontFile.isSource(uri.toString())){
            if(!"r".equals(mode))throw new FileNotFoundException("Read-only shell font");
            boolean published=false;for(LauncherFonts.Family family:LauncherFonts.all(getContext().getSharedPreferences("today",0)))if(uri.toString().equals(family.regular)||uri.toString().equals(family.bold)){published=true;break;}
            if(!published||!me.ancal.wmdroid.sdk.WmFontFile.available(getContext(),uri.toString()))throw new FileNotFoundException("Unknown shell font");
            java.io.File file=me.ancal.wmdroid.sdk.WmFontFile.file(getContext(),uri.getLastPathSegment());return new AssetFileDescriptor(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY),0,file.length());
        }
        if(WmShell.START_BACKGROUND.equals(uri)){
            if(!"r".equals(mode))throw new FileNotFoundException("Read-only Start background");
            try{
                Context context=getContext();boolean rose=context.getSharedPreferences("today",0).getBoolean("rose",false);
                // Share the host bitmap/fallback path; never recycle its cached texture.
                final Bitmap bitmap=StartMenuBackground.bitmap(context,rose);
                ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
                new Thread(()->{try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Start background encoding failed");}catch(IOException gone){}},"WMDroid-start-background").start();
                return new AssetFileDescriptor(pipe[0],0,AssetFileDescriptor.UNKNOWN_LENGTH);
            }catch(IOException error){throw new FileNotFoundException("Start background unavailable");}
        }
        if(!"r".equals(mode)||uri.getPathSegments().size()!=2||!"icons".equals(uri.getPathSegments().get(0)))throw new FileNotFoundException("Read-only shell icons");
        String id=uri.getLastPathSegment();boolean known=false;for(FolderIcons.Icon icon:FolderIcons.all(getContext()))if(icon.id.equals(id)){known=true;break;}if(!known)throw new FileNotFoundException("Unknown icon");
        try{final InputStream source=me.ancal.wmdroid.sdk.WmResources.openAsset(getContext(),FolderIcons.asset(getContext(),id));ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();new Thread(()->{try(InputStream in=source;OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}catch(IOException gone){}},"WMDroid-icon").start();return new AssetFileDescriptor(pipe[0],0,AssetFileDescriptor.UNKNOWN_LENGTH);}catch(IOException error){throw new FileNotFoundException("Icon unavailable");}
    }
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException("Read-only shell");}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException("Read-only shell");}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException("Read-only shell");}
}
