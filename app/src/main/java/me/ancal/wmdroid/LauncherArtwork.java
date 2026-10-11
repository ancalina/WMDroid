package me.ancal.wmdroid;
import android.content.Context;
import android.graphics.drawable.Drawable;
/** Resource-ID adapter; vector artwork is shared with independent SDK apps. */
final class LauncherArtwork {
 static Drawable load(Context context,int id){switch(id){
  case R.drawable.wm_network_connected_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"network_connected");
  case R.drawable.wm_network_disconnected_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"network_disconnected");
  case R.drawable.wm_cellular_off_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"cellular_off");
  case R.drawable.wm_cellular_ready_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"cellular_ready");
  case R.drawable.wm_radio_off_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"radio_off");
  case R.drawable.wm_start:
  case R.drawable.wm_start_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"start");
  case R.drawable.wm_phone_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"phone");
  case R.drawable.wm_volume_system_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"volume_system");
  case R.drawable.wm_volume_ring_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"volume_ring");
  case R.drawable.wm_volume_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"volume");
  case R.drawable.wm_volume_muted_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"volume_muted");
  case R.drawable.wm_volume_vibrate_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"volume_vibrate");
  case R.drawable.wm_signal_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"signal");
  case R.drawable.wm_battery_charge_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"battery_charge");
  case R.drawable.wm_battery_full_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"battery_full");
  case R.drawable.wm_battery_medium_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"battery_medium");
  case R.drawable.wm_battery_low_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"battery_low");
  case R.drawable.wm_battery_half_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"battery_half");
  case R.drawable.wm_battery_critical_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"battery_critical");
  case R.drawable.wm_wireless_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"wireless");
  case R.drawable.wm_lock_hd:return me.ancal.wmdroid.sdk.WmIcons.load(context,"lock");
  default:return raster(context,id);
 }}

 private static final java.util.Map<Context,Images> IMAGES=new java.util.WeakHashMap<>();
 private static final class Images {
  final me.ancal.wmdroid.sdk.WmResourcePack pack;final java.util.Map<Integer,android.graphics.Bitmap> entries=new java.util.HashMap<>();
  Images(me.ancal.wmdroid.sdk.WmResourcePack pack){this.pack=pack;}
 }
 private static synchronized Drawable raster(Context context,int id){
  Context app=context.getApplicationContext();if(app==null)app=context;
  me.ancal.wmdroid.sdk.WmResourcePack pack=null;try{pack=me.ancal.wmdroid.sdk.WmResources.prepare(app);}catch(java.io.IOException missing){}
  Images cache=IMAGES.get(app);if(cache==null||cache.pack!=pack){cache=new Images(pack);IMAGES.put(app,cache);}
  android.graphics.Bitmap bitmap=cache.entries.get(id);
  if(bitmap==null){String path,symbol;int width,height;switch(id){
   case R.drawable.wm_apps:path="launcher/icons/apps.png";width=64;height=64;symbol="📱";break;
   case R.drawable.wm_battery:path="launcher/icons/battery.png";width=64;height=64;symbol="🔋";break;
   case R.drawable.wm_battery_charge:path="status/battery/charging.png";width=32;height=32;symbol="⚡";break;
   case R.drawable.wm_battery_full:path="status/battery/full.png";width=32;height=32;symbol="🔋";break;
   case R.drawable.wm_battery_low:path="status/battery/low.png";width=32;height=32;symbol="🔋";break;
   case R.drawable.wm_battery_medium:path="status/battery/medium.png";width=32;height=32;symbol="🔋";break;
   case R.drawable.wm_bluetooth_status:path="status/bluetooth.png";width=32;height=32;symbol="🔵";break;
   case R.drawable.wm_browser:path="launcher/icons/browser.png";width=64;height=64;symbol="🌐";break;
   case R.drawable.wm_calendar:path="launcher/icons/calendar.png";width=64;height=64;symbol="📅";break;
   case R.drawable.wm_clock:path="launcher/icons/clock.png";width=64;height=64;symbol="🕒";break;
   case R.drawable.wm_contacts:path="launcher/icons/contacts.png";width=64;height=64;symbol="👥";break;
   case R.drawable.wm_cpl_about:path="settings/about.png";width=64;height=64;symbol="ℹ️";break;
   case R.drawable.wm_cpl_backlight:path="settings/backlight.png";width=64;height=64;symbol="💡";break;
   case R.drawable.wm_cpl_bluetooth:path="settings/bluetooth.png";width=64;height=64;symbol="🔵";break;
   case R.drawable.wm_cpl_connections:path="settings/connections.png";width=64;height=64;symbol="🔗";break;
   case R.drawable.wm_cpl_language:path="settings/language.png";width=64;height=64;symbol="🌐";break;
   case R.drawable.wm_cpl_lock:path="settings/lock.png";width=64;height=64;symbol="🔒";break;
   case R.drawable.wm_cpl_menu:path="settings/menu.png";width=64;height=64;symbol="📋";break;
   case R.drawable.wm_cpl_performance:path="settings/performance.png";width=64;height=64;symbol="⚙️";break;
   case R.drawable.wm_cpl_power:path="settings/power.png";width=64;height=64;symbol="🔋";break;
   case R.drawable.wm_cpl_sounds:path="settings/sounds.png";width=64;height=64;symbol="🔊";break;
   case R.drawable.wm_cpl_today:path="settings/today.png";width=64;height=64;symbol="📅";break;
   case R.drawable.wm_cpl_wifi:path="settings/wifi.png";width=64;height=64;symbol="📶";break;
   case R.drawable.wm_cpl_wireless:path="settings/wireless.png";width=64;height=64;symbol="📶";break;
   case R.drawable.wm_help:path="launcher/icons/help.png";width=64;height=64;symbol="❓";break;
   case R.drawable.wm_lock:path="library/today/lock.png";width=32;height=32;symbol="🔓";break;
   case R.drawable.wm_mail:path="launcher/icons/mail.png";width=64;height=64;symbol="✉️";break;
   case R.drawable.wm_media:path="launcher/icons/media.png";width=64;height=64;symbol="🎵";break;
   case R.drawable.wm_notes:path="launcher/icons/notes.png";width=64;height=64;symbol="📝";break;
   case R.drawable.wm_ok:path="titlebar/ok.png";width=46;height=52;symbol="✔️";break;
   case R.drawable.wm_owner:path="launcher/icons/owner.png";width=64;height=64;symbol="👤";break;
   case R.drawable.wm_phone:path="launcher/icons/phone.png";width=64;height=64;symbol="📞";break;
   case R.drawable.wm_settings_app_icons:path="settings/app-icons.png";width=256;height=256;symbol="📱";break;
   case R.drawable.wm_settings_fonts:path="settings/fonts.png";width=256;height=256;symbol="🔤";break;
   case R.drawable.wm_settings_mouse:path="settings/mouse.png";width=256;height=256;symbol="🖱️";break;
   case R.drawable.wm_settings_theme:path="settings/theme.png";width=256;height=256;symbol="🎨";break;
   case R.drawable.wm_signal:path="status/cellular-signal.png";width=32;height=32;symbol="📶";break;
   case R.drawable.wm_start_programs:path="launcher/icons/start-programs.png";width=64;height=64;symbol="📁";break;
   case R.drawable.wm_start_settings:path="launcher/icons/start-settings.png";width=64;height=64;symbol="⚙️";break;
   case R.drawable.wm_start_today:path="launcher/icons/start-today.png";width=64;height=64;symbol="📅";break;
   case R.drawable.wm_tasks:path="launcher/icons/tasks.png";width=64;height=64;symbol="☑️";break;
   case R.drawable.wm_volume:path="status/volume.png";width=32;height=32;symbol="🔊";break;
   case R.drawable.wm_wireless:path="library/today/wireless.png";width=32;height=32;symbol="📶";break;
   default:throw new IllegalArgumentException("Unknown launcher artwork resource: "+id);
  }
  bitmap=me.ancal.wmdroid.sdk.WmResources.iconBitmap(app,path,width,height,symbol);cache.entries.put(id,bitmap);
  }
  return new android.graphics.drawable.BitmapDrawable(context.getResources(),bitmap);
 }
}
