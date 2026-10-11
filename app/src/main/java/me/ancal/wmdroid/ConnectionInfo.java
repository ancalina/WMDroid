package me.ancal.wmdroid;

import android.Manifest;
import android.bluetooth.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import java.util.*;

/** Only names of actually connected devices. Never label bonded devices connected. */
final class ConnectionInfo {
    private final Context context;private final Runnable changed;private BluetoothAdapter adapter;private boolean closed;private int generation;
    private final Map<Integer,BluetoothProfile> profiles=new HashMap<>();
    ConnectionInfo(Context c,Runnable changed){context=c;this.changed=changed;}
    boolean bluetoothAllowed(){return Build.VERSION.SDK_INT<31||DeviceApi.permission(context,Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
    String ssid(){
        if(DeviceApi.permission(context,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return "";
        try{String name=((WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).getConnectionInfo().getSSID();if(name==null||name.equals("<unknown ssid>"))return "";return name.startsWith("\"")&&name.endsWith("\"")?name.substring(1,name.length()-1):name;}catch(SecurityException unavailable){return "";}
    }
    void start(){
        if(!bluetoothAllowed()||(!closed&&adapter!=null))return;closed=false;final int ticket=++generation;
        try{adapter=adapter(context);if(adapter==null)return;final BluetoothAdapter requested=adapter;
            for(int profile:new int[]{BluetoothProfile.HEADSET,BluetoothProfile.A2DP})requested.getProfileProxy(context,new BluetoothProfile.ServiceListener(){
                public void onServiceConnected(int kind,BluetoothProfile proxy){if(closed||ticket!=generation){close(requested,kind,proxy);return;}BluetoothProfile previous=profiles.put(kind,proxy);if(previous!=null&&previous!=proxy)close(requested,kind,previous);changed.run();}
                public void onServiceDisconnected(int kind){if(closed||ticket!=generation)return;profiles.remove(kind);changed.run();}
            },profile);
        }catch(SecurityException denied){stop();}
    }
    String connectedNames(){
        if(!bluetoothAllowed())return "";Set<String> names=new LinkedHashSet<>();
        try{for(BluetoothProfile profile:profiles.values())for(BluetoothDevice device:profile.getConnectedDevices()){String name=device.getName();if(name!=null&&!name.isEmpty())names.add(name);}
            if(Build.VERSION.SDK_INT>=18)Api18.addGattNames(context,names);
        }catch(SecurityException denied){return "";}
        return android.text.TextUtils.join(", ",names);
    }
    static BluetoothAdapter adapter(Context context){return Build.VERSION.SDK_INT>=18?Api18.adapter(context):BluetoothAdapter.getDefaultAdapter();}
    private static void close(BluetoothAdapter adapter,int kind,BluetoothProfile proxy){try{adapter.closeProfileProxy(kind,proxy);}catch(SecurityException denied){/* Permission may be revoked while a profile callback is pending. */}}
    void stop(){closed=true;generation++;BluetoothAdapter old=adapter;adapter=null;if(old!=null)for(Map.Entry<Integer,BluetoothProfile> profile:profiles.entrySet())close(old,profile.getKey(),profile.getValue());profiles.clear();}
    /** Keep classes introduced in API18 out of the legacy execution path. */
    private static final class Api18 {
        static BluetoothAdapter adapter(Context context){BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);return manager==null?null:manager.getAdapter();}
        static void addGattNames(Context context,Set<String> names){BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);if(manager!=null&&manager.getAdapter()!=null)for(int profile:new int[]{BluetoothProfile.GATT,BluetoothProfile.GATT_SERVER})for(BluetoothDevice device:manager.getConnectedDevices(profile)){String name=device.getName();if(name!=null&&!name.isEmpty())names.add(name);}}
    }
}
