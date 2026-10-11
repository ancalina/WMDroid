package me.ancal.wmdroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import java.io.IOException;
import java.io.InputStream;

/** Theme hue follows the ROM policy: the chosen color supplies hue, while each
 * source color keeps its own saturation/brightness. This does not replace the
 * source shades with a flat tint or use the chosen color's saturation/brightness.
 * Bitmap hue rotation is an Android adaptation, retaining the source hue offsets;
 * a gray selection instead uses AYGSHELL 3854A8C's 299/587/114 grayscale weights.
 */
final class LauncherTheme {
    private static final int DEFAULT_COLOR=0xff15afbc;
    private static final java.util.Map<Context,Wallpaper[]> wallpapers=new java.util.WeakHashMap<>();
    private static final class Wallpaper {
        final boolean rose,custom,tint;
        final me.ancal.wmdroid.sdk.WmResourcePack pack;
        final int color;
        final Bitmap bitmap;
        Wallpaper(boolean rose,boolean custom,boolean tint,int color,Bitmap bitmap,me.ancal.wmdroid.sdk.WmResourcePack pack){
            this.pack=pack;
            this.rose=rose;this.custom=custom;this.tint=tint;this.color=color;this.bitmap=bitmap;
        }
        boolean matches(boolean rose,boolean custom,boolean tint,int color,me.ancal.wmdroid.sdk.WmResourcePack pack){
            return this.pack==pack&&this.rose==rose&&this.custom==custom&&this.tint==tint&&this.color==color;
        }
    }
    private LauncherTheme(){}
    private static SharedPreferences prefs(Context context){return context.getSharedPreferences("today",Context.MODE_PRIVATE);}
    private static int originalBase(boolean rose){return rose?0xff94010c:DEFAULT_COLOR;}
    static int base(Context context,boolean rose){
        SharedPreferences settings=prefs(context);
        return settings.getBoolean("theme_custom",false)?settings.getInt("theme_color",DEFAULT_COLOR)|0xff000000:originalBase(rose);
    }
    static int selection(Context context,boolean rose){return color(context,rose?0xff94010c:0xff149ca7,rose);}
    static int divider(Context context,boolean rose){return color(context,rose?0xffa73798:0xff15afbc,rose);}
    static int darkSelection(Context context,boolean rose){return color(context,0xff00143c,rose);}

    static int color(Context context,int original,boolean rose){
        SharedPreferences settings=prefs(context);
        if(!settings.getBoolean("theme_custom",false)||achromatic(original))return original;
        int chosen=settings.getInt("theme_color",DEFAULT_COLOR);
        if(isGray(chosen))return gray(original);
        float[] hsv=new float[3],target=new float[3];
        Color.colorToHSV(original,hsv);Color.colorToHSV(chosen,target);hsv[0]=target[0];
        return Color.HSVToColor(original>>>24,hsv);
    }
    static Bitmap tint(Context context,Bitmap source,boolean rose){
        SharedPreferences settings=prefs(context);
        if(!settings.getBoolean("theme_custom",false)||!settings.getBoolean("wallpaper_tint",true))return source;
        return tint(source,rose,settings.getInt("theme_color",DEFAULT_COLOR));
    }
    /** One current setting per known asset; retained views can keep older bitmaps. */
    static synchronized Bitmap wallpaper(Context context,String asset,boolean rose) throws IOException {
        int slot=asset.equals("backgrounds/classic.gif")?0:asset.equals("wallpaper_mugunghwa.gif")?1:asset.equals("start_mugunghwa.gif")?2:-1;
        SharedPreferences settings=prefs(context);
        boolean custom=settings.getBoolean("theme_custom",false),tint=settings.getBoolean("wallpaper_tint",true);
        int color=settings.getInt("theme_color",DEFAULT_COLOR);
        Context app=context.getApplicationContext();if(app==null)app=context;
        me.ancal.wmdroid.sdk.WmResourcePack pack=me.ancal.wmdroid.sdk.WmResources.prepare(app);
        Wallpaper[] entries=wallpapers.get(app);if(entries==null){entries=new Wallpaper[3];wallpapers.put(app,entries);}
        Wallpaper cached=slot<0?null:entries[slot];
        if(cached!=null&&cached.matches(rose,custom,tint,color,pack))return cached.bitmap;
        if(slot<0)throw new IOException("Unknown wallpaper");
        Bitmap decoded=me.ancal.wmdroid.sdk.WmResources.bitmap(context,asset,slot==2?40:480,slot==2?588:640);
        Bitmap bitmap=custom&&tint?tint(decoded,rose,color):decoded;
        if(slot>=0)entries[slot]=new Wallpaper(rose,custom,tint,color,bitmap,pack);
        return bitmap;
    }
    private static Bitmap tint(Bitmap source,boolean rose,int chosen){return me.ancal.wmdroid.sdk.WmColor.tint(source,originalBase(rose),chosen);}
    /** Saturation <= 1% treats nearly neutral user input as a gray theme. */
    static boolean isGray(int color){
        int r=(color>>>16)&255,g=(color>>>8)&255,b=color&255;
        int max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));
        return max==0||(max-min)*100<=max;
    }
    static int gray(int color){
        int value=(299*((color>>>16)&255)+587*((color>>>8)&255)+114*(color&255))/1000;
        return (color&0xff000000)|(value<<16)|(value<<8)|value;
    }
    private static boolean achromatic(int color){return ((color>>>16)&255)==((color>>>8)&255)&&((color>>>8)&255)==(color&255);}
}
