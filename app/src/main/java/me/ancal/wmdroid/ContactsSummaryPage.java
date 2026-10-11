package me.ancal.wmdroid;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.os.Handler;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.widget.FrameLayout;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import me.ancal.wmdroid.sdk.WmContactSummaryView;
import me.ancal.wmdroid.sdk.WmControls;
import me.ancal.wmdroid.sdk.WmStrings;

/** Read-only aggregate adapter; SDK owns summary presentation and retained state. */
final class ContactsSummaryPage extends FrameLayout implements WmControls.LanguageListener {
    final WmContactSummaryView client;
    final String contactKey;
    private final Executor worker;
    private final Handler handler=new Handler();
    private final ContentObserver observer=new ContentObserver(handler){@Override public void onChange(boolean self){handler.removeCallbacks(refresh);handler.postDelayed(refresh,100);}};
    private final Runnable refresh=this::reload;
    private ContactsStore.Details details;
    private Runnable updated;
    private boolean attached,listening,loading,dirty,loaded;
    private int generation;
    private String language="ko";
    ContactsSummaryPage(Context context,Typeface regular,Typeface bold,int selection,String key,Executor worker,WmContactSummaryView.Listener listener){
        super(context);ContactsStore.uri(key);contactKey=key;this.worker=worker;
        client=new WmContactSummaryView(context,regular,bold,selection,listener);addView(client,new LayoutParams(-1,-1));
    }
    WmContactSummaryView.Phone selected(){return loaded?client.selected():null;}
    WmContactSummaryView.Link selectedLink(){return loaded?client.selectedLink():null;}
    WmContactSummaryView.Address selectedAddress(){return loaded?client.selectedAddress():null;}
    String contactName(){return loaded&&details!=null?details.name:"";}
    void setOnUpdated(Runnable updated){this.updated=updated;}
    void reload(){
        if(!attached)return;
        if(DeviceApi.permission(getContext(),Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){++generation;loading=false;dirty=false;loaded=false;client.setMessage("연락처 읽기 권한이 필요합니다.");stopObserving();return;}
        if(!listening)try{
            getContext().getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI,true,observer);
            getContext().getContentResolver().registerContentObserver(ContactsContract.Data.CONTENT_URI,true,observer);listening=true;
        }catch(SecurityException denied){getContext().getContentResolver().unregisterContentObserver(observer);loaded=false;client.setMessage("연락처 읽기 권한이 필요합니다.");return;}
        if(loading){dirty=true;return;}loading=true;dirty=false;final int ticket=++generation;
        if(!loaded)client.setMessage("연락처를 읽는 중입니다.");
        try{worker.execute(()->{
            ContactsStore.Details result=null;String error=null;
            try{result=ContactsStore.summary(getContext().getContentResolver(),contactKey);}catch(SecurityException denied){error="연락처 읽기 권한이 필요합니다.";}catch(RuntimeException unavailable){error="연락처를 읽을 수 없습니다.";}
            final ContactsStore.Details value=result;final String failure=error;
            handler.post(()->{
                if(!attached||ticket!=generation)return;loading=false;
                if(DeviceApi.permission(getContext(),Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){reload();return;}
                if(failure==null){details=value;loaded=true;publish();client.setMessage(null);}else{loaded=false;client.setMessage(failure);}
                if(dirty)reload();
            });
        });}catch(RejectedExecutionException stopped){loading=false;loaded=false;client.setMessage("연락처를 읽을 수 없습니다.");}
    }
    private void publish(){
        if(details==null)return;
        ContactsStore.PhoneField[] sorted=details.phones.clone();java.util.Arrays.sort(sorted,(a,b)->orderRank(a.type)-orderRank(b.type));
        java.util.ArrayList<WmContactSummaryView.Phone> actions=new java.util.ArrayList<>();
        for(ContactsStore.PhoneField phone:sorted)actions.add(action(phone,false));
        for(ContactsStore.PhoneField phone:sorted)if(phone.type==ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)actions.add(action(phone,true));
        java.util.ArrayList<WmContactSummaryView.Link> links=new java.util.ArrayList<>();int emailSlot=0;
        for(ContactsStore.LinkField field:details.links)if(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE.equals(field.mime)){
            ++emailSlot;String authored="전자 메일 보내기"+(emailSlot==1?"":" "+emailSlot);
            String label=emailSlot<=3?WmStrings.text(getContext(),language,authored):WmStrings.text(getContext(),language,"전자 메일 보내기")+" "+emailSlot;
            links.add(new WmContactSummaryView.Link("email:"+field.key,label,field.value,WmContactSummaryView.EMAIL));
        }
        for(ContactsStore.LinkField field:details.links)if(ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE.equals(field.mime))links.add(new WmContactSummaryView.Link("web:"+field.key,WmStrings.text(getContext(),language,"웹 페이지 찾아보기"),field.value,WmContactSummaryView.WEB));
        ContactsStore.AddressField[] postal=details.addresses.clone();java.util.Arrays.sort(postal,(a,b)->addressRank(a.type)-addressRank(b.type));
        java.util.ArrayList<WmContactSummaryView.Address> addresses=new java.util.ArrayList<>();
        for(ContactsStore.AddressField field:postal)addresses.add(new WmContactSummaryView.Address("address:"+field.key,addressLabel(field),field.value));
        client.setContact(details.name,details.company,details.jobTitle,actions.toArray(new WmContactSummaryView.Phone[0]),links.toArray(new WmContactSummaryView.Link[0]),addresses.toArray(new WmContactSummaryView.Address[0]),details.notes);if(updated!=null)updated.run();
    }
    private static int addressRank(int type){return type==ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK?0:type==ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME?1:type==ContactsContract.CommonDataKinds.StructuredPostal.TYPE_OTHER?2:3;}
    private String addressLabel(ContactsStore.AddressField address){
        if(address.type==ContactsContract.CommonDataKinds.StructuredPostal.TYPE_CUSTOM&&!address.customLabel.trim().isEmpty())return address.customLabel;
        String authored=address.type==ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK?"회사 주소":address.type==ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME?"집 주소":"기타 주소";
        return WmStrings.text(getContext(),language,authored);
    }
    // Native Mobile/Work/Home/SMS order is proved for one synthetic case.
    // Other Android phone kinds follow those rows in their provider order.
    private static int orderRank(int type){return type==ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE?0:type==ContactsContract.CommonDataKinds.Phone.TYPE_WORK?1:type==ContactsContract.CommonDataKinds.Phone.TYPE_HOME?2:3;}
    private WmContactSummaryView.Phone action(ContactsStore.PhoneField phone,boolean sms){
        String formatted=PhoneNumberUtils.formatNumber(phone.number);
        int icon=sms?WmContactSummaryView.ICON_SMS:phone.type==ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE?WmContactSummaryView.ICON_MOBILE:phone.type==ContactsContract.CommonDataKinds.Phone.TYPE_WORK?WmContactSummaryView.ICON_WORK:phone.type==ContactsContract.CommonDataKinds.Phone.TYPE_HOME?WmContactSummaryView.ICON_HOME:WmContactSummaryView.ICON_NONE;
        return new WmContactSummaryView.Phone((sms?"sms:":"call:")+phone.key,sms?WmStrings.text(getContext(),language,"문자 보내기"):label(phone),formatted==null?phone.number:formatted,sms?WmContactSummaryView.SMS:WmContactSummaryView.CALL,icon);
    }
    private String label(ContactsStore.PhoneField phone){
        String authored;
        switch(phone.type){
            case ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM:if(!phone.customLabel.isEmpty())return phone.customLabel;authored="통화";break;
            case ContactsContract.CommonDataKinds.Phone.TYPE_HOME:authored="집으로 전화 걸기";break;
            case ContactsContract.CommonDataKinds.Phone.TYPE_WORK:case ContactsContract.CommonDataKinds.Phone.TYPE_COMPANY_MAIN:authored="회사로 전화 걸기";break;
            case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE:case ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE:authored="휴대폰으로 전화 걸기";break;
            case ContactsContract.CommonDataKinds.Phone.TYPE_CAR:authored="카폰에 전화 걸기";break;
            case ContactsContract.CommonDataKinds.Phone.TYPE_ASSISTANT:authored="비서 호출";break;
            case ContactsContract.CommonDataKinds.Phone.TYPE_PAGER:case ContactsContract.CommonDataKinds.Phone.TYPE_WORK_PAGER:authored="호출기 호출";break;
            case ContactsContract.CommonDataKinds.Phone.TYPE_RADIO:authored="무선 호출";break;
            default:authored="통화";break;
        }return WmStrings.text(getContext(),language,authored);
    }
    @Override public void onWmLanguageChanged(String language){this.language=language;if(loaded)publish();}
    private void stopObserving(){if(listening){getContext().getContentResolver().unregisterContentObserver(observer);listening=false;}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;reload();}
    @Override protected void onDetachedFromWindow(){attached=false;++generation;loading=false;dirty=false;handler.removeCallbacks(refresh);stopObserving();super.onDetachedFromWindow();}
}
