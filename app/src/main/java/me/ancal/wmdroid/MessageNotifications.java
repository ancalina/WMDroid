package me.ancal.wmdroid;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Persists only a count. Active app labels/actions stay in memory; no message
 * titles, bodies, senders or notification history are read or retained. */
public final class MessageNotifications extends NotificationListenerService {
    public static final String CHANGED = "me.ancal.wmdroid.NOTIFICATIONS_CHANGED";
    public static final class Entry {
        public final String title,packageName;
        public final PendingIntent contentIntent;
        public Entry(String title,String packageName,PendingIntent contentIntent){
            if(title==null||packageName==null||packageName.isEmpty())throw new IllegalArgumentException("Notification app label and package required");
            this.title=title;this.packageName=packageName;this.contentIntent=contentIntent;
        }
    }
    private static volatile List<Entry> active;
    private boolean connected;
    /** Null means unavailable; an immutable empty list means connected with no messages. */
    public static List<Entry> snapshot(){return active;}

    @Override public void onListenerConnected() { connected=true;update(); }
    @Override public void onNotificationPosted(StatusBarNotification notification) { update(); }
    @Override public void onNotificationRemoved(StatusBarNotification notification) { update(); }
    @Override public void onListenerDisconnected() { connected=false;publish(null); }
    @Override public void onDestroy() { connected=false;publish(null);super.onDestroy(); }

    private static boolean matches(Notification notification){
        return android.os.Build.VERSION.SDK_INT>=21&&notification!=null&&(notification.flags&Notification.FLAG_GROUP_SUMMARY)==0
                &&(Notification.CATEGORY_MESSAGE.equals(notification.category)||Notification.CATEGORY_EMAIL.equals(notification.category));
    }

    private void update() {
        try {
            if(!connected||android.os.Build.VERSION.SDK_INT<21){publish(null);return;}
            List<Entry> entries=new ArrayList<>();
            StatusBarNotification[] notifications = getActiveNotifications();
            if (notifications == null) { publish(null); return; }
            for (StatusBarNotification item : notifications) {
                Notification n = item.getNotification();
                if(!matches(n))continue;
                String pkg=item.getPackageName(),title=pkg;
                try{CharSequence label=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0));if(label!=null)title=label.toString();}
                catch(android.content.pm.PackageManager.NameNotFoundException removed){/* Keep the originating package as its label. */}
                entries.add(new Entry(title,pkg,n.contentIntent));
            }
            publish(entries);
        } catch (SecurityException unavailable) { publish(null); }
    }

    private void publish(List<Entry> entries) {
        active=entries==null?null:Collections.unmodifiableList(new ArrayList<>(entries));
        getSharedPreferences("today", MODE_PRIVATE).edit().putInt("message_count", entries==null?-1:entries.size()).apply();
        sendBroadcast(new Intent(CHANGED).setPackage(getPackageName()));
    }
}
