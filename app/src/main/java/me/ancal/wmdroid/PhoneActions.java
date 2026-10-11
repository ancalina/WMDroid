package me.ancal.wmdroid;

import android.content.Intent;
import android.net.Uri;
import me.ancal.wmdroid.sdk.WmPhoneView;

/** Pure intent construction. Only an explicit host command may launch these. */
final class PhoneActions {
    static Intent call(String number){return new Intent(Intent.ACTION_CALL,Uri.fromParts("tel",required(number),null));}
    static Intent message(String number){return new Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",required(number),null));}
    private static String required(String number){String value=WmPhoneView.dialable(number);boolean digit=false;for(int i=0;i<value.length();i++)digit|=value.charAt(i)>='0'&&value.charAt(i)<='9';if(!digit)throw new IllegalArgumentException("Phone number required");return value;}
}
