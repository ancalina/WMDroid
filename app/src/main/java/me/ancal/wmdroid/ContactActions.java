package me.ancal.wmdroid;

import android.content.Intent;
import android.net.Uri;
import java.util.Locale;

/** Pure explicit-action intents; constructing these never sends mail or opens a site. */
final class ContactActions {
    static Intent email(String address){
        String value=required(address);int at=value.indexOf('@');
        if(at<=0||at!=value.lastIndexOf('@')||at==value.length()-1)throw new IllegalArgumentException("Email address required");
        return new Intent(Intent.ACTION_SENDTO,Uri.fromParts("mailto",value,null));
    }
    static Intent website(String address){
        String value=required(address);Uri uri=Uri.parse(value);
        if(uri.getScheme()==null||value.matches("(?i)(?:[^:/?#]+\\.[^:/?#]+|localhost):[0-9]+(?:[/?#].*)?"))uri=Uri.parse("https://"+value);
        String scheme=uri.getScheme();
        if(scheme==null||!(scheme.equalsIgnoreCase("http")||scheme.equalsIgnoreCase("https"))||uri.getHost()==null||uri.getHost().isEmpty())throw new IllegalArgumentException("HTTP website required");
        return new Intent(Intent.ACTION_VIEW,uri.buildUpon().scheme(scheme.toLowerCase(Locale.ROOT)).build());
    }
    private static String required(String input){
        if(input==null)throw new IllegalArgumentException("Contact address required");String value=input.trim();
        if(value.isEmpty())throw new IllegalArgumentException("Contact address required");
        for(int i=0;i<value.length();i++)if(Character.isWhitespace(value.charAt(i))||Character.isISOControl(value.charAt(i)))throw new IllegalArgumentException("Invalid contact address");
        return value;
    }
}
