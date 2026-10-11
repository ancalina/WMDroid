package me.ancal.wmdroid;

import android.content.SharedPreferences;
import me.ancal.wmdroid.sdk.WmSpeedDialView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.ArrayList;

/** User-created shortcuts only; slot1 belongs to the real SIM voicemail service. */
final class PhoneSpeedDialStore {
    static WmSpeedDialView.Entry[] read(SharedPreferences prefs){
        try{JSONArray saved=new JSONArray(prefs.getString("phone_speed_dial","[]"));ArrayList<WmSpeedDialView.Entry> result=new ArrayList<>();boolean[] slots=new boolean[100];
            for(int i=0;i<saved.length();i++){JSONObject item=saved.getJSONObject(i);int slot=item.getInt("slot");if(slot<2||slot>99||slots[slot])throw new IllegalArgumentException("Invalid speed dial slot");slots[slot]=true;String number=item.getString("number");if(number.isEmpty())throw new IllegalArgumentException("Empty speed dial number");WmSpeedDialView.Entry entry=new WmSpeedDialView.Entry(slot,item.getString("name"),number);PhoneActions.call(entry.number);result.add(entry);}
            java.util.Collections.sort(result,(a,b)->a.slot-b.slot);return result.toArray(new WmSpeedDialView.Entry[0]);
        }catch(JSONException|IllegalArgumentException malformed){throw new IllegalStateException("단축 다이얼을 읽을 수 없습니다.",malformed);}
    }
    static int freeSlot(SharedPreferences prefs){boolean[] used=new boolean[100];for(WmSpeedDialView.Entry entry:read(prefs))used[entry.slot]=true;for(int i=2;i<100;i++)if(!used[i])return i;throw new IllegalStateException("빈 단축 다이얼 위치가 없습니다.");}
    static void put(SharedPreferences prefs,int slot,String name,String number){
        if(slot<2||slot>99)throw new IllegalArgumentException("Invalid speed dial slot");WmSpeedDialView.Entry next=new WmSpeedDialView.Entry(slot,name,number);PhoneActions.call(next.number);
        ArrayList<WmSpeedDialView.Entry> result=new ArrayList<>();for(WmSpeedDialView.Entry old:read(prefs))if(old.slot!=slot)result.add(old);result.add(next);write(prefs,result);
    }
    static void remove(SharedPreferences prefs,int slot){if(slot<2||slot>99)throw new IllegalArgumentException("Cannot remove voicemail");ArrayList<WmSpeedDialView.Entry> result=new ArrayList<>();for(WmSpeedDialView.Entry old:read(prefs))if(old.slot!=slot)result.add(old);write(prefs,result);}
    private static void write(SharedPreferences prefs,ArrayList<WmSpeedDialView.Entry> entries){
        JSONArray saved=new JSONArray();try{for(WmSpeedDialView.Entry entry:entries){JSONObject item=new JSONObject();item.put("slot",entry.slot);item.put("name",entry.name);item.put("number",entry.number);saved.put(item);}}catch(JSONException invalid){throw new IllegalArgumentException("Invalid speed dial entry",invalid);}prefs.edit().putString("phone_speed_dial",saved.toString()).apply();
    }
}
