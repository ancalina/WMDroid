package me.ancal.wmdroid;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.os.Handler;
import android.provider.ContactsContract;
import android.widget.FrameLayout;
import me.ancal.wmdroid.sdk.WmContactsView;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** Applet-owned asynchronous provider lifecycle; SDK client owns presentation. */
final class ContactsPage extends FrameLayout {
    final WmContactsView client;
    private final Executor worker;
    private final Handler handler=new Handler();
    private final ContentObserver observer=new ContentObserver(handler){@Override public void onChange(boolean self){handler.removeCallbacks(refresh);handler.postDelayed(refresh,100);}};
    private final Runnable refresh=this::reload;
    private boolean attached,listening,loading,dirty,loaded;
    private int generation;
    ContactsPage(Context context,Typeface regular,Typeface bold,int selection,Executor worker,WmContactsView.Listener listener){
        super(context);this.worker=worker;client=new WmContactsView(context,regular,bold,selection,listener);addView(client,new LayoutParams(-1,-1));
    }
    void reload(){
        if(!attached)return;
        if(DeviceApi.permission(getContext(),Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){++generation;loading=false;dirty=false;loaded=false;client.setMessage("연락처 읽기 권한이 필요합니다.");stopObserving();return;}
        if(!listening)try{getContext().getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI,true,observer);listening=true;}catch(SecurityException denied){client.setMessage("연락처 읽기 권한이 필요합니다.");return;}
        if(loading){dirty=true;return;}loading=true;dirty=false;final int ticket=++generation;
        if(!loaded)client.setMessage("연락처를 읽는 중입니다.");
        try{worker.execute(()->{
            WmContactsView.Entry[] result=null;String error=null;
            try{result=ContactsStore.read(getContext().getContentResolver());}catch(SecurityException denied){error="연락처 읽기 권한이 필요합니다.";}catch(RuntimeException unavailable){error="연락처를 읽을 수 없습니다.";}
            final WmContactsView.Entry[] entries=result;final String failure=error;
            handler.post(()->{if(!attached||ticket!=generation)return;loading=false;if(failure==null){client.setEntries(entries);client.setMessage(null);loaded=true;}else{loaded=false;client.setMessage(failure);}if(dirty)reload();});
        });}catch(RejectedExecutionException stopped){loading=false;client.setMessage("연락처를 읽을 수 없습니다.");}
    }
    private void stopObserving(){if(listening){getContext().getContentResolver().unregisterContentObserver(observer);listening=false;}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;reload();}
    @Override protected void onDetachedFromWindow(){attached=false;++generation;loading=false;dirty=false;handler.removeCallbacks(refresh);stopObserving();super.onDetachedFromWindow();}
}
