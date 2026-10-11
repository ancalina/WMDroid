package me.ancal.wmdroid;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.View;

/** netui 24D7268: phone first row, Wi-Fi + Bluetooth second, BT reserves 75*DPI/96. */
final class TodayConnectionsView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Typeface regular;private final int color;private final Drawable wireless;private final Drawable bluetooth;private final int lineHeight;
    private String phone="",wifi="",bt="";private final ConnectionInfo info;
    TodayConnectionsView(Context c,Typeface regular,boolean rose){super(c);this.regular=regular;this.color=rose?0xff000000:0xffffffff;p.setTypeface(regular);p.setTextSize(24);lineHeight=me.ancal.wmdroid.sdk.WmMetrics.LINE;wireless=LauncherArtwork.load(c,R.drawable.wm_wireless_hd);bluetooth=me.ancal.wmdroid.sdk.WmIcons.load("bluetooth");info=new ConnectionInfo(c,this::refresh);refresh();}
    int rowHeight(){return 2*lineHeight+18;}
    void refresh(){Context c=getContext();boolean airplane=DeviceApi.airplane(c);
        phone=UiLanguage.t(c,me.ancal.wmdroid.sdk.WmDevice.phone(c).caption());
        ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);DeviceApi.Network caps=DeviceApi.network(c);WifiManager wm=(WifiManager)c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifi="Wi-Fi: "+(!wm.isWifiEnabled()?UiLanguage.t(getContext(),"해제"):caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?UiLanguage.t(getContext(),"연결됨"):UiLanguage.t(getContext(),"사용할 수 없음"));
        if(c.getSharedPreferences("today",Context.MODE_PRIVATE).getBoolean("connection_ssid",false)&&caps!=null&&caps.wifi){String ssid=info.ssid();if(!ssid.isEmpty())wifi="Wi-Fi: "+ssid;}
        boolean allowed=info.bluetoothAllowed();
        try{BluetoothAdapter a=ConnectionInfo.adapter(c);bt=a==null?UiLanguage.t(getContext(),"없음"):!allowed?UiLanguage.t(getContext(),"확인 필요"):a.isEnabled()?UiLanguage.t(getContext(),"설정"):UiLanguage.t(getContext(),"해제");}catch(SecurityException denied){bt=UiLanguage.t(getContext(),"확인 필요");}
        if(c.getSharedPreferences("today",Context.MODE_PRIVATE).getBoolean("connection_bt_names",false)&&allowed){String names=info.connectedNames();if(!names.isEmpty())bt=names;}
        setContentDescription(phone+", "+wifi+", Bluetooth: "+bt);invalidate();
    }
    @Override protected void onDraw(Canvas c){super.onDraw(c);wireless.setBounds(4,4,36,36);wireless.draw(c);p.setColor(isSelected()||isPressed()?LauncherTheme.color(getContext(),0xff00f0ff,color==0xff000000):color);p.setTypeface(regular);float first=8+me.ancal.wmdroid.sdk.WmFonts.centerBaseline(p,0,lineHeight,phone);me.ancal.wmdroid.sdk.WmFonts.draw(c,me.ancal.wmdroid.sdk.WmFonts.ellipsize(phone,p,Math.max(0,getWidth()-60)),56,first,p);
        p.setTypeface(regular);float second=first+lineHeight+2;me.ancal.wmdroid.sdk.WmFonts.draw(c,me.ancal.wmdroid.sdk.WmFonts.ellipsize(wifi,p,Math.max(0,getWidth()-214)),56,second,p);
        // netui 24D7268: row top +4, square of measured text height,
        // then a4px caption gap. Android font height approximates GDI cy.
        int y=8+lineHeight+2+4;
        int rightStart=getWidth()-150;bluetooth.setBounds(rightStart,y,rightStart+lineHeight,y+lineHeight);bluetooth.draw(c);me.ancal.wmdroid.sdk.WmFonts.draw(c,me.ancal.wmdroid.sdk.WmFonts.ellipsize(": "+bt,p,150-lineHeight-8),rightStart+4+lineHeight,second,p);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(getContext().getSharedPreferences("today",Context.MODE_PRIVATE).getBoolean("connection_bt_names",false))info.start();}
    @Override protected void onDetachedFromWindow(){info.stop();super.onDetachedFromWindow();}
    @Override public void setSelected(boolean selected){super.setSelected(selected);invalidate();}
}
