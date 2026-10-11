package me.ancal.wmdroid;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import me.ancal.wmdroid.sdk.WmDateField;
import me.ancal.wmdroid.sdk.WmDateTimeField;
import me.ancal.wmdroid.sdk.WmShell;

/** One pending alarm for the earliest undelivered task. The receiver is private. */
public final class TaskReminders extends BroadcastReceiver {
    static final String ACTION="me.ancal.wmdroid.TASK_REMINDER",CHANNEL="task_reminders";
    static final int NOTIFICATION=20007,PERMISSION_REQUEST=18;
    private static final String DELIVERED="reminder_delivered";

    static WmDateTimeField.Value read(JSONObject task){
        Object raw=task.opt("reminder");if(raw==null||raw==JSONObject.NULL)return null;
        if(!(raw instanceof JSONArray)||((JSONArray)raw).length()!=5)throw new IllegalArgumentException("Task reminder requires five civil parts");
        JSONArray parts=(JSONArray)raw;int[] values=new int[5];
        for(int i=0;i<5;i++){Object part=parts.opt(i);if(!(part instanceof Integer)&&!(part instanceof Long)||((Number)part).longValue()<Integer.MIN_VALUE||((Number)part).longValue()>Integer.MAX_VALUE)throw new IllegalArgumentException("Task reminder integer parts required");values[i]=((Number)part).intValue();}
        WmDateTimeField.Value value=new WmDateTimeField.Value(values[0],values[1],values[2],values[3],values[4]);WmDateField.formatValue(value);return value;
    }
    static void write(JSONObject task,WmDateTimeField.Value value)throws JSONException{
        if(value!=null)WmDateField.formatValue(value);
        JSONArray parts=value==null?null:new JSONArray().put(value.year).put(value.month).put(value.day).put(value.hour).put(value.minute);Object previous=task.opt("reminder");
        boolean unchanged=parts==null?(previous==null||previous==JSONObject.NULL):previous instanceof JSONArray&&parts.toString().equals(previous.toString());
        if(!unchanged)task.remove(DELIVERED);task.put("reminder",parts==null?JSONObject.NULL:parts);
    }
    static JSONArray latest(SharedPreferences prefs,JSONArray snapshot)throws JSONException{
        JSONArray current=new JSONArray(prefs.getString("tasks","[]"));
        if(current.length()!=snapshot.length())throw new JSONException("Task list changed while open");
        // A receiver may add delivery markers while the user holds an editor or list snapshot.
        for(int i=0;i<current.length();i++){
            JSONObject before=new JSONObject(snapshot.getJSONObject(i).toString()),after=new JSONObject(current.getJSONObject(i).toString());
            before.remove(DELIVERED);after.remove(DELIVERED);
            if(!same(before,after))throw new JSONException("Task changed while open");
        }
        return current;
    }
    private static boolean same(Object before,Object after)throws JSONException{
        if(before instanceof JSONObject&&after instanceof JSONObject){
            JSONObject left=(JSONObject)before,right=(JSONObject)after;if(left.length()!=right.length())return false;
            java.util.Iterator<String> keys=left.keys();while(keys.hasNext()){String key=keys.next();if(!right.has(key)||!same(left.get(key),right.get(key)))return false;}return true;
        }
        if(before instanceof JSONArray&&after instanceof JSONArray){
            JSONArray left=(JSONArray)before,right=(JSONArray)after;if(left.length()!=right.length())return false;
            for(int i=0;i<left.length();i++)if(!same(left.get(i),right.get(i)))return false;return true;
        }
        if(before instanceof Number&&after instanceof Number)return new java.math.BigDecimal(before.toString()).compareTo(new java.math.BigDecimal(after.toString()))==0;
        return before.equals(after);
    }
    static long at(WmDateTimeField.Value value,TimeZone zone){
        GregorianCalendar date=new GregorianCalendar(zone);date.setGregorianChange(new java.util.Date(Long.MIN_VALUE));date.clear();
        // Civil dates are validated; Calendar resolves a DST gap forward and an overlap to standard time.
        date.set(value.year,value.month-1,value.day,value.hour,value.minute,0);return date.getTimeInMillis();
    }
    private static String marker(JSONObject task){return task.optJSONArray("reminder").toString();}
    private static boolean active(JSONObject task){return task!=null&&!task.optBoolean("done")&&task.opt("title") instanceof String&&!((String)task.opt("title")).trim().isEmpty();}
    static long next(JSONArray tasks,TimeZone zone){
        long next=Long.MAX_VALUE;
        for(int i=0;i<tasks.length();i++){JSONObject task=tasks.optJSONObject(i);if(!active(task))continue;
            try{WmDateTimeField.Value reminder=read(task);if(reminder!=null&&!marker(task).equals(task.opt(DELIVERED)))next=Math.min(next,at(reminder,zone));}catch(IllegalArgumentException invalid){/* Retain malformed data; do not schedule it. */}
        }return next;
    }
    private static JSONArray tasks(SharedPreferences prefs){try{return new JSONArray(prefs.getString("tasks","[]"));}catch(JSONException invalid){return null;}}
    private static PendingIntent alarm(Context context){return PendingIntent.getBroadcast(context,0,new Intent(context,TaskReminders.class).setAction(ACTION),PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));}
    static boolean allowed(Context context){
        if(Build.VERSION.SDK_INT>=33&&DeviceApi.permission(context,"android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)return false;
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(manager==null)return false;
        if(Build.VERSION.SDK_INT>=24&&!manager.areNotificationsEnabled())return false;
        if(Build.VERSION.SDK_INT>=26){android.app.NotificationChannel channel=manager.getNotificationChannel(CHANNEL);if(channel!=null&&channel.getImportance()==NotificationManager.IMPORTANCE_NONE)return false;}
        return true;
    }
    static boolean exact(Context context){AlarmManager manager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);return manager!=null&&(Build.VERSION.SDK_INT<31||manager.canScheduleExactAlarms());}
    static void schedule(Context context){
        AlarmManager manager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);if(manager==null)return;PendingIntent operation=alarm(context);manager.cancel(operation);
        JSONArray tasks=tasks(context.getSharedPreferences("today",0));if(tasks==null)return;
        boolean delivered=false;for(int i=0;i<tasks.length();i++){JSONObject task=tasks.optJSONObject(i);if(!active(task))continue;try{if(read(task)!=null&&marker(task).equals(task.opt(DELIVERED))){delivered=true;break;}}catch(IllegalArgumentException invalid){/* Invalid delivery markers cannot retain a notification. */}}
        if(!delivered){NotificationManager notifications=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(notifications!=null)notifications.cancel(NOTIFICATION);}
        if(!allowed(context))return;long next=next(tasks,TimeZone.getDefault());if(next==Long.MAX_VALUE)return;
        long trigger=Math.max(System.currentTimeMillis()+1000,next);
        try{if(Build.VERSION.SDK_INT>=23&&exact(context))manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,operation);
            else if(Build.VERSION.SDK_INT>=23)manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,operation);
            else if(Build.VERSION.SDK_INT>=19)manager.setExact(AlarmManager.RTC_WAKEUP,trigger,operation);
            else manager.set(AlarmManager.RTC_WAKEUP,trigger,operation);
        }catch(SecurityException revoked){if(Build.VERSION.SDK_INT>=23)manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,operation);else manager.set(AlarmManager.RTC_WAKEUP,trigger,operation);}
    }
    static int deliver(Context context,long now){
        if(!allowed(context))return 0;SharedPreferences prefs=context.getSharedPreferences("today",0);JSONArray tasks=tasks(prefs);if(tasks==null)return 0;
        JSONArray due=new JSONArray();StringBuilder names=new StringBuilder();
        for(int i=0;i<tasks.length();i++){JSONObject task=tasks.optJSONObject(i);if(!active(task))continue;
            try{WmDateTimeField.Value reminder=read(task);if(reminder!=null&&!marker(task).equals(task.opt(DELIVERED))&&at(reminder,TimeZone.getDefault())<=now){due.put(task);if(due.length()<=3){if(names.length()>0)names.append("\n");names.append(task.optString("title"));}}}catch(IllegalArgumentException invalid){/* Preserve invalid tasks. */}
        }if(due.length()==0)return 0;
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        String title=UiLanguage.t(context,"작업 미리 알림");
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new android.app.NotificationChannel(CHANNEL,title,NotificationManager.IMPORTANCE_HIGH));
        Intent open=new Intent(context,TodayActivity.class).setAction(WmShell.ACTION).putExtra(WmShell.COMMAND,"tasks").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content=PendingIntent.getActivity(context,NOTIFICATION,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,CHANNEL):new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(title).setContentText(names.toString()).setContentIntent(content).setAutoCancel(true).setWhen(now).setDefaults(Notification.DEFAULT_SOUND);
        if(Build.VERSION.SDK_INT>=16)builder.setStyle(new Notification.BigTextStyle().bigText(names.toString())).setNumber(due.length());
        if(Build.VERSION.SDK_INT>=21)builder.setVisibility(Notification.VISIBILITY_PRIVATE);
        try{manager.notify(NOTIFICATION,Build.VERSION.SDK_INT>=16?builder.build():builder.getNotification());}catch(SecurityException denied){return 0;}
        try{for(int i=0;i<due.length();i++){JSONObject task=due.getJSONObject(i);task.put(DELIVERED,marker(task));}}catch(JSONException invalid){throw new IllegalStateException(invalid);}
        if(!prefs.edit().putString("tasks",tasks.toString()).commit())return -1;
        return due.length();
    }
    static void requestAccess(Activity activity){
        if(Build.VERSION.SDK_INT>=33&&DeviceApi.permission(activity,"android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED){DeviceApi.request(activity,new String[]{"android.permission.POST_NOTIFICATIONS"},PERMISSION_REQUEST);return;}
        Intent intent;
        if(Build.VERSION.SDK_INT>=31&&!exact(activity))intent=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+activity.getPackageName()));
        else if(Build.VERSION.SDK_INT>=26)intent=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.getPackageName());
        else intent=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+activity.getPackageName()));
        if(intent.resolveActivity(activity.getPackageManager())!=null)activity.startActivity(intent);
    }
    @Override public void onReceive(Context context,Intent intent){
        String action=intent==null?null:intent.getAction();
        if(ACTION.equals(action))deliver(context,System.currentTimeMillis());
        else if(!Intent.ACTION_BOOT_COMPLETED.equals(action)&&!Intent.ACTION_TIME_CHANGED.equals(action)&&!Intent.ACTION_TIMEZONE_CHANGED.equals(action)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)&&!AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action))return;
        schedule(context);
    }
}
