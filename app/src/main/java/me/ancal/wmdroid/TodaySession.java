package me.ancal.wmdroid;

import android.content.SharedPreferences;
import android.os.Bundle;

/** Suspend/resume session uses local civil time, not input inactivity. */
final class TodaySession {
    static final String KEY="today_session_timeout";
    private boolean stamped;
    private long stamp;

    static int hours(SharedPreferences prefs){
        try{int value=prefs.getInt(KEY,4);return value>=0&&value<=12?value:4;}
        catch(ClassCastException malformed){return 4;}
    }
    static long localMillis(){
        return java.time.LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
    }
    void suspend(long now){stamp=now;stamped=true;}
    Bundle save(){Bundle state=new Bundle();state.putBoolean("stamped",stamped);state.putLong("stamp",stamp);return state;}
    void restore(Bundle state){if(state!=null){stamped=state.getBoolean("stamped",false);stamp=state.getLong("stamp",0);}}
    boolean resume(int hours,long now){
        if(hours<0||hours>12)throw new IllegalArgumentException("Session hours must be 0..12");
        long duration=hours*3600000L;
        boolean expired=hours!=0&&stamped&&stamp<=Long.MAX_VALUE-duration&&stamp+duration<now;
        suspend(now);
        return expired;
    }
}
