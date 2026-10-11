package me.ancal.wmdroid;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.provider.Settings;

/** Version and permission gates; older devices retain real legacy backend paths. */
final class DeviceApi {
    static boolean airplane(Context c){return me.ancal.wmdroid.sdk.WmDevice.airplane(c);}
    static int permission(Context c,String name){return Build.VERSION.SDK_INT>=23?c.checkSelfPermission(name):c.checkCallingOrSelfPermission(name);}
    static void request(Activity a,String[] names,int code){if(Build.VERSION.SDK_INT>=23)a.requestPermissions(names,code);}
    static boolean canWrite(Context c){return Build.VERSION.SDK_INT>=23?Settings.System.canWrite(c):permission(c,android.Manifest.permission.WRITE_SETTINGS)==PackageManager.PERMISSION_GRANTED;}
    static final class Network {
        final boolean connected,wifi,cellular,ethernet,validated;
        Network(boolean connected,boolean wifi,boolean cellular,boolean ethernet,boolean validated){this.connected=connected;this.wifi=wifi;this.cellular=cellular;this.ethernet=ethernet;this.validated=validated;}
        boolean hasTransport(int transport){return transport==1?wifi:transport==0?cellular:transport==3&&ethernet;}
        boolean hasCapability(int capability){return capability==16&&validated;}
    }
    static Network network(Context c){
        me.ancal.wmdroid.sdk.WmDevice.Network n=me.ancal.wmdroid.sdk.WmDevice.network(c);
        return n==null||!n.connected?null:new Network(n.connected,n.wifi,n.cellular,n.ethernet,n.validated);
    }
}
