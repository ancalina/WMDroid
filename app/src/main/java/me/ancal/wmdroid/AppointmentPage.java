package me.ancal.wmdroid;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewParent;
import android.widget.FrameLayout;
import java.time.*;
import me.ancal.wmdroid.sdk.WmAppointmentEditor;
import me.ancal.wmdroid.sdk.WmControls;
import me.ancal.wmdroid.sdk.WmDateTimeField;

/** Host adapter: the public SDK owns the appointment UI and popups; this class
 * owns Calendar provider identity and DST-aware draft validation. */
final class AppointmentPage extends FrameLayout implements WmControls.RestyleListener,WmControls.BackHandler,WmControls.ModalOwner {
    interface Choose {void calendar();}
    private final WmAppointmentEditor editor;
    private final CalendarDraft initial;
    private final long eventId;
    private final String[] eventBaseline;
    private long calendarId=-1;
    private FrameLayout savingOverlay;
    private Bundle savingState;
    // ponytail: no cross-Activity write receipt; unknown outcomes require checking Calendar.
    private boolean uncertainSave;
    AppointmentPage(Context context,Typeface font,CalendarDraft draft,Choose choose){
        this(context,font,draft,choose,null);
    }
    AppointmentPage(Context context,Typeface font,CalendarDraft draft,Choose choose,CalendarStore.Event existing){
        this(context,font,draft,choose,existing==null?-1:existing.id,baseline(existing));
    }
    AppointmentPage(Context context,Typeface font,Bundle state,Choose choose){
        this(context,font,restoredOrigin(state),choose,state.getLong("event_id"),state.getStringArray("event_baseline"));editor.validateInteractionState(state.getBundle("editor"));
    }
    private AppointmentPage(Context context,Typeface font,CalendarDraft draft,Choose choose,long id,String[] baseline){
        super(context);initial=copyOrigin(draft);eventId=id;eventBaseline=baseline==null?null:baseline.clone();
        editor=new WmAppointmentEditor(context,font,UiLanguage.locale(context),new WmAppointmentEditor.Value(draft.title,draft.location,draft.notes,value(draft.startDate,draft.startTime),value(draft.endDate,draft.endTime),draft.allDay,draft.recurrenceRule,value(draft.recurrenceUntil==null?draft.endDate:draft.recurrenceUntil,draft.startTime),WmAppointmentEditor.BUSY,WmAppointmentEditor.NORMAL,draft.reminderMinutes==null?new me.ancal.wmdroid.sdk.WmReminder(false,15,0):me.ancal.wmdroid.sdk.WmReminder.fromMinutes(true,draft.reminderMinutes)),LauncherTheme.selection(context,false),LauncherTheme.color(context,0xffc9e7e9,false));
        // CalendarProvider cannot round-trip all four native busy/sensitivity values yet.
        editor.setStatusFieldsVisible(false);
        editor.setOnCalendarOpen(choose::calendar);editor.setOnPageChanged(()->replayPresentation());addView(editor,new LayoutParams(-1,-1));
    }
    private static WmDateTimeField.Value value(LocalDate date,LocalTime time){return new WmDateTimeField.Value(date.getYear(),date.getMonthValue(),date.getDayOfMonth(),time.getHour(),time.getMinute());}
    private static LocalDate date(WmDateTimeField.Value value){return LocalDate.of(value.year,value.month,value.day);}
    private static LocalTime time(WmDateTimeField.Value value){return LocalTime.of(value.hour,value.minute);}
    private void replayPresentation(){for(ViewParent parent=getParent();parent!=null;parent=parent.getParent())if(parent instanceof RetroSurface){((RetroSurface)parent).replay(editor.propertySheet().getClientView());return;}}
    void calendar(CalendarStore.CalendarTarget target){calendarId=target.id;editor.setCalendarName(target.name);}
    long calendarId(){return calendarId;}
    void error(String message){editor.setError(message);}
    void allowReminder(boolean enabled){editor.setReminderVisible(enabled);}
    void allowRecurrence(boolean enabled){editor.setRecurrenceEnabled(enabled);}
    void setSaving(boolean saving){
        if(saving==isSaving())return;
        if(saving){editor.closePopup();savingState=saveState();savingOverlay=new FrameLayout(getContext());savingOverlay.setClickable(true);savingOverlay.setFocusableInTouchMode(true);addView(savingOverlay,new LayoutParams(-1,-1));savingOverlay.requestFocus();}
        else{removeView(savingOverlay);savingOverlay=null;savingState=null;}
    }
    boolean isSaving(){return savingOverlay!=null;}
    void saveOutcomeUnknown(boolean unknown){uncertainSave=unknown;}
    boolean saveOutcomeUnknown(){return uncertainSave;}
    @Override public boolean blocksOwnerCommands(){return isSaving()||editor.blocksOwnerCommands();}
    @Override public android.view.View inputMethodOwner(){return isSaving()?null:editor.inputMethodOwner();}
    @Override public boolean back(){return isSaving()||editor.back();}
    @Override public void onWmRestyle(Typeface regular,Typeface bold,int selection,int tabColor){editor.setLocale(UiLanguage.locale(getContext()));}
    /** Calendar identity is host-owned. Restoration never selects a provider target. */
    Bundle saveState(){
        if(savingState!=null)return new Bundle(savingState);
        Bundle state=new Bundle();state.putInt("schema",2);state.putBoolean("origin_all_day",initial.allDay);state.putBundle("editor",editor.saveInteractionState());state.putLong("calendar",calendarId);state.putLong("event_id",eventId);
        state.putStringArray("origin",new String[]{initial.startDate.toString(),initial.endDate.toString(),initial.startTime.toString(),initial.endTime.toString(),initial.startOffset==null?null:initial.startOffset.toString(),initial.endOffset==null?null:initial.endOffset.toString()});state.putStringArray("event_baseline",eventBaseline==null?null:eventBaseline.clone());return state;
    }
    static CalendarDraft restoredOrigin(Bundle state){
        if(state==null||!(state.get("schema") instanceof Integer)||(state.getInt("schema")==1?state.size()!=6:state.getInt("schema")!=2||state.size()!=7||!(state.get("origin_all_day") instanceof Boolean))||!(state.get("editor") instanceof Bundle)||!(state.get("calendar") instanceof Long)||state.getLong("calendar")< -1||!(state.get("event_id") instanceof Long)||state.getLong("event_id")< -1||!(state.get("origin") instanceof String[]))throw new IllegalArgumentException("Invalid host appointment state");
        String[] origin=state.getStringArray("origin");if(origin.length!=6)throw new IllegalArgumentException("Appointment origin required");
        CalendarDraft draft;
        try{draft=new CalendarDraft(LocalDate.parse(origin[0]));draft.endDate=LocalDate.parse(origin[1]);draft.startTime=LocalTime.parse(origin[2]);draft.endTime=LocalTime.parse(origin[3]);draft.startOffset=origin[4]==null?null:ZoneOffset.of(origin[4]);draft.endOffset=origin[5]==null?null:ZoneOffset.of(origin[5]);}catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid appointment origin",invalid);}
        if(draft.startDate.getYear()<1900||draft.startDate.getYear()>2999||draft.endDate.getYear()<1900||draft.endDate.getYear()>2999)throw new IllegalArgumentException("Appointment origin outside editor range");
        Object raw=state.get("event_baseline");long id=state.getLong("event_id");if(!state.containsKey("event_baseline")||(id<0?raw!=null:!(raw instanceof String[])||((String[])raw).length!=18||!String.valueOf(id).equals(((String[])raw)[0])))throw new IllegalArgumentException("Invalid appointment provider baseline");
        if(id>=0){int[] fields={10,11,12,13,15,16};for(int i=0;i<fields.length;i++)if(origin[i]==null?((String[])raw)[fields[i]]!=null:!origin[i].equals(((String[])raw)[fields[i]]))throw new IllegalArgumentException("Inconsistent appointment origin");}
        boolean providerAllDay=id>=0&&"true".equals(((String[])raw)[14]);
        draft.allDay=state.getInt("schema")==2?state.getBoolean("origin_all_day"):providerAllDay;
        if(id>=0&&draft.allDay!=providerAllDay)throw new IllegalArgumentException("Inconsistent appointment all-day origin");
        return draft;
    }
    static boolean matchesEvent(Bundle state,CalendarStore.Event existing){restoredOrigin(state);return state.getLong("event_id")== (existing==null?-1:existing.id)&&java.util.Arrays.equals(state.getStringArray("event_baseline"),baseline(existing));}
    void restoreState(Bundle state){
        if(isSaving())throw new IllegalStateException("Cannot restore while saving");
        CalendarDraft origin=restoredOrigin(state);if(state.getLong("event_id")!=eventId||!java.util.Arrays.equals(state.getStringArray("event_baseline"),eventBaseline))throw new IllegalArgumentException("Appointment provider changed");
        editor.validateInteractionState(state.getBundle("editor"));editor.restoreInteractionState(state.getBundle("editor"));
        initial.startDate=origin.startDate;initial.endDate=origin.endDate;initial.startTime=origin.startTime;initial.endTime=origin.endTime;initial.startOffset=origin.startOffset;initial.endOffset=origin.endOffset;initial.allDay=origin.allDay;calendarId=-1;editor.setCalendarName("");
    }
    private static CalendarDraft copyOrigin(CalendarDraft draft){CalendarDraft copy=new CalendarDraft(draft.startDate);copy.endDate=draft.endDate;copy.startTime=draft.startTime;copy.endTime=draft.endTime;copy.startOffset=draft.startOffset;copy.endOffset=draft.endOffset;copy.allDay=draft.allDay;return copy;}
    private static String[] baseline(CalendarStore.Event event){
        if(event==null)return null;CalendarDraft draft=event.draft;
        return new String[]{String.valueOf(event.id),String.valueOf(event.calendarId),event.timezone,String.valueOf(event.repeating),String.valueOf(event.writable),String.valueOf(event.reminderEditable),String.valueOf(event.reminderId),draft.title,draft.location,draft.notes,draft.startDate.toString(),draft.endDate.toString(),draft.startTime.toString(),draft.endTime.toString(),String.valueOf(draft.allDay),draft.startOffset==null?null:draft.startOffset.toString(),draft.endOffset==null?null:draft.endOffset.toString(),draft.reminderMinutes==null?null:draft.reminderMinutes.toString()};
    }
    /** Minute-only SDK fields must not discard untouched provider precision or DST choice. */
    private static void preserveClockOrigin(CalendarDraft draft,CalendarDraft origin){
        if(draft.allDay||origin.allDay)return;
        if(draft.startDate.equals(origin.startDate)&&draft.startTime.getHour()==origin.startTime.getHour()&&draft.startTime.getMinute()==origin.startTime.getMinute()){draft.startTime=origin.startTime;draft.startOffset=origin.startOffset;}
        if(draft.endDate.equals(origin.endDate)&&draft.endTime.getHour()==origin.endTime.getHour()&&draft.endTime.getMinute()==origin.endTime.getMinute()){draft.endTime=origin.endTime;draft.endOffset=origin.endOffset;}
    }
    CalendarDraft draft(){
        WmAppointmentEditor.Value input=editor.value();
        CalendarDraft draft=new CalendarDraft(date(input.start));draft.endDate=date(input.end);draft.reminderMinutes=input.reminder.enabled?input.reminder.minutes():null;draft.allDay=input.allDay;
        if(!draft.allDay){draft.startTime=time(input.start);draft.endTime=time(input.end);preserveClockOrigin(draft,initial);}
        draft.title=input.subject;draft.location=input.location;draft.notes=input.notes;draft.recurrenceRule=input.recurrenceRule;draft.recurrenceUntil=input.recurrenceRule.endMode==me.ancal.wmdroid.sdk.WmRecurrenceRule.UNTIL?date(input.recurrenceUntil):null;CalendarRecurrence.prepare(draft,draft.recurrenceRule,draft.recurrenceUntil,ZoneId.systemDefault());return draft;
    }
}
