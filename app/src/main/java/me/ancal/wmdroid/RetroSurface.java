package me.ancal.wmdroid;
import android.content.Context;
import android.content.SharedPreferences;
/** Local preferences adapter to SDK paint scheduling. Costs are synthetic. */
final class RetroSurface extends me.ancal.wmdroid.sdk.WmPaint {
    RetroSurface(Context c,boolean served){super(c,served);}
    RetroSurface(Context c,boolean served,Role role){super(c,served,role);}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("today",0);}
    static boolean enabled(Context c){return prefs(c).getBoolean("retro_enabled",true);}
    static int delayMillis(Context c){SharedPreferences p=prefs(c);return p.getBoolean("retro_enabled",true)?Math.round(Math.max(0,Math.min(1500,p.getInt("applet_delay",120)))*Math.max(250,Math.min(4000,p.getInt("retro_delay_scale",1000)))/1000f):0;}
    @Override protected Options readOptions(){SharedPreferences p=prefs(getContext());Options o=new Options();o.enabled=p.getBoolean("retro_enabled",true);o.scan=p.getBoolean("retro_scan",true);o.repaint=p.getBoolean("retro_repaint",false);o.delay=p.getInt("applet_delay",120);o.lines=p.getInt("retro_band_lines",64);o.scale=p.getInt("retro_delay_scale",1000);return o;}
}
