package me.ancal.wmdroid;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Calendar;
import me.ancal.wmdroid.sdk.WmCalendarGeneralOptions.Options;

/** Android-owned Calendar preferences; never changes the device's global locale. */
final class CalendarPreferences {
    private CalendarPreferences(){}
    static int[] readCursorRows(Context context){
        int packed=context.getSharedPreferences("today",Context.MODE_PRIVATE).getInt("calendar_cursor_rows",-1);
        int start=packed&65535,end=packed>>>16;
        return start<=end&&end<48?new int[]{start,end}:null;
    }
    static void saveCursorRows(Context context,int[] rows){
        if(rows==null)return;
        int packed=rows[0]|(rows[1]<<16);
        SharedPreferences prefs=context.getSharedPreferences("today",Context.MODE_PRIVATE);
        if(prefs.getInt("calendar_cursor_rows",-1)!=packed)prefs.edit().putInt("calendar_cursor_rows",packed).apply();
    }
    static Options read(Context context){
        SharedPreferences prefs=context.getSharedPreferences("today",Context.MODE_PRIVATE);
        int start=prefs.getInt("calendar_start_view",0),first=prefs.getInt("calendar_first_day",Calendar.getInstance(UiLanguage.locale(context)).getFirstDayOfWeek()),days=prefs.getInt("calendar_week_days",5);
        return new Options(start>=0&&start<=4?start:0,first==Calendar.MONDAY?Calendar.MONDAY:Calendar.SUNDAY,days>=5&&days<=7?days:5,prefs.getBoolean("calendar_half_hour",false),prefs.getBoolean("calendar_week_numbers",false));
    }
    static void save(Context context,Options options){
        context.getSharedPreferences("today",Context.MODE_PRIVATE).edit().putInt("calendar_start_view",options.startView).putInt("calendar_first_day",options.firstDayOfWeek).putInt("calendar_week_days",options.weekDays).putBoolean("calendar_half_hour",options.halfHour).putBoolean("calendar_week_numbers",options.weekNumbers).apply();
    }
    static me.ancal.wmdroid.sdk.WmCalendarAppointmentsOptions.Options readAppointments(Context context){
        SharedPreferences p=context.getSharedPreferences("today",Context.MODE_PRIVATE);
        int quantity=p.getInt("calendar_reminder_quantity",15),unit=p.getInt("calendar_reminder_unit",0),mask=p.getInt("calendar_icon_mask",63);
        return new me.ancal.wmdroid.sdk.WmCalendarAppointmentsOptions.Options(p.getBoolean("calendar_reminder_enabled",true),quantity>=0&&quantity<=99?quantity:15,unit>=0&&unit<=3?unit:0,mask>=0&&mask<=63?mask:63);
    }
    static void saveAppointments(Context context,me.ancal.wmdroid.sdk.WmCalendarAppointmentsOptions.Options options){
        context.getSharedPreferences("today",Context.MODE_PRIVATE).edit().putBoolean("calendar_reminder_enabled",options.enabled).putInt("calendar_reminder_quantity",options.quantity).putInt("calendar_reminder_unit",options.unit).putInt("calendar_icon_mask",options.displayMask).apply();
    }
    static CalendarDraft newDraft(Context context,java.time.LocalDate date){
        CalendarDraft draft=new CalendarDraft(date);me.ancal.wmdroid.sdk.WmCalendarAppointmentsOptions.Options options=readAppointments(context);
        draft.reminderMinutes=options.enabled?options.minutes():null;return draft;
    }
}
