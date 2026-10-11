package me.ancal.wmdroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;

/** Explicit local font catalog. No synthesized Samsung bold.
 * Samsung entries come only from the user-specified M490 WM6.1 firmware.
 */
final class LauncherFonts {
    static final class Family {
        final String id,label,regular,bold;
        Family(String id,String label,String regular,String bold){this.id=id;this.label=label;this.regular=regular;this.bold=bold;}
        Typeface rawRegular(Context c){return me.ancal.wmdroid.sdk.WmFonts.load(c,regular,false,false);}
        Typeface rawBold(Context c){return me.ancal.wmdroid.sdk.WmFonts.load(c,bold,false,true);}
        Typeface regular(Context c){return regular(c,c.getSharedPreferences("today",Context.MODE_PRIVATE).getBoolean("font_hybrid",true));}
        Typeface regular(Context c,boolean hybrid){return me.ancal.wmdroid.sdk.WmFonts.load(c,regular,hybrid,false);}
        Typeface bold(Context c,Typeface normal){return me.ancal.wmdroid.sdk.WmFonts.load(c,bold,c.getSharedPreferences("today",Context.MODE_PRIVATE).getBoolean("font_hybrid",true),true);}
    }
    static final Family[] FAMILIES={
        new Family("gulim","굴림",me.ancal.wmdroid.sdk.WmFonts.DEFAULT_SOURCE,me.ancal.wmdroid.sdk.WmFonts.DEFAULT_SOURCE),
        new Family("system","시스템 글꼴",me.ancal.wmdroid.sdk.WmFonts.SYSTEM_SOURCE,me.ancal.wmdroid.sdk.WmFonts.SYSTEM_SOURCE),
        new Family("malgun","맑은 고딕","fonts/malgun/regular.ttf","fonts/malgun/bold.ttf"),
        new Family("samsung","삼성 고딕체","fonts/samsung/samsung-gothic.ttf","fonts/samsung/samsung-gothic.ttf"),
        new Family("samsung_message","삼성 고딕체 메시지","fonts/samsung/ssgd.ttf","fonts/samsung/ssgd.ttf")
    };
    static java.util.List<Family> all(SharedPreferences prefs){
        java.util.List<Family> list=new java.util.ArrayList<>(java.util.Arrays.asList(FAMILIES));
        try{org.json.JSONArray items=new org.json.JSONArray(prefs.getString("font_custom_families","[]"));java.util.Set<String> ids=new java.util.HashSet<>();
            for(int i=0;i<items.length();i++){org.json.JSONObject item=items.optJSONObject(i);if(item==null)continue;String source=item.optString("regular"),heavy=item.optString("bold",source);if(!me.ancal.wmdroid.sdk.WmFontFile.isSource(source)||!me.ancal.wmdroid.sdk.WmFontFile.isSource(heavy))continue;String id="custom_"+android.net.Uri.parse(source).getLastPathSegment();if(ids.add(id))list.add(new Family(id,item.optString("label","가져온 글꼴"),source,heavy));}
        }catch(org.json.JSONException invalid){}return list;
    }
    static Family selected(SharedPreferences prefs){String id=prefs.getString("font_family","gulim");for(Family family:all(prefs))if(family.id.equals(id))return family;return FAMILIES[0];}
    static synchronized void install(SharedPreferences prefs,java.io.File file,String label,String boldFor)throws org.json.JSONException {
        String source=me.ancal.wmdroid.sdk.WmFontFile.source(file),id="custom_"+android.net.Uri.parse(source).getLastPathSegment();org.json.JSONArray items=new org.json.JSONArray();boolean found=false;
        for(Family family:all(prefs))if(family.id.startsWith("custom_")){
            org.json.JSONObject item=new org.json.JSONObject().put("label",family.label).put("regular",family.regular).put("bold",family.bold);
            if(boldFor!=null&&family.id.equals(boldFor)){item.put("bold",source);id=family.id;found=true;}else if(boldFor==null&&family.id.equals(id))found=true;
            items.put(item);
        }
        if(boldFor!=null&&!found)throw new IllegalArgumentException("Selected imported family no longer exists");
        if(boldFor==null&&!found)items.put(new org.json.JSONObject().put("label",label).put("regular",source).put("bold",source));
        prefs.edit().putString("font_custom_families",items.toString()).putString("font_family",id).apply();
    }
}
