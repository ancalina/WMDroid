package me.ancal.wmdroid;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Calendar Provider data only. Rendering belongs to the WM application surface.
 * Instances expands provider RRULE/RDATE/EXDATE and exception occurrences.
 */
final class CalendarStore {
    static final class Occurrence {
        final long id,begin,end,seriesId;final Long originalId;final String originalSyncId; final String title,location; final boolean allDay;
        final int symbolMask,busyStatus;final boolean recurrenceException;Integer reminderLeadMinutes;
        Occurrence(long id,long begin,long end,String title,String location,boolean allDay){this(id,begin,end,title,location,allDay,0,false);}
        Occurrence(long id,long begin,long end,String title,String location,boolean allDay,int symbolMask,boolean exception){this(id,begin,end,title,location,allDay,symbolMask,exception,2);}
        Occurrence(long id,long begin,long end,String title,String location,boolean allDay,int symbolMask,boolean exception,int busyStatus){this(id,begin,end,title,location,allDay,symbolMask,exception,busyStatus,null,null);}
        Occurrence(long id,long begin,long end,String title,String location,boolean allDay,int symbolMask,boolean exception,int busyStatus,Long originalId,String originalSyncId){this.originalId=originalId;this.originalSyncId=originalSyncId;seriesId=originalId==null?id:originalId.longValue();this.busyStatus=busyStatus;this.id=id;this.begin=begin;this.end=end;this.title=title==null?"":title;this.location=location==null?"":location;this.allDay=allDay;this.symbolMask=symbolMask;recurrenceException=exception;}
        int displaySymbols(long now){return symbolMask|(reminderLeadMinutes!=null&&begin-reminderLeadMinutes*60000L>now?1:0);}
        LocalDate startDay(){return java.time.Instant.ofEpochMilli(begin).atZone(allDay?ZoneOffset.UTC:ZoneId.systemDefault()).toLocalDate();}
        LocalDate lastDay(){return java.time.Instant.ofEpochMilli(Math.max(begin,end-1)).atZone(allDay?ZoneOffset.UTC:ZoneId.systemDefault()).toLocalDate();}
        boolean on(LocalDate day){return !day.isBefore(startDay())&&!day.isAfter(lastDay());}
    }
    private final ContentResolver resolver;
    private final Context context;
    CalendarStore(Context context){this.context=context.getApplicationContext();resolver=context.getContentResolver();}
    List<Occurrence> between(LocalDate start,LocalDate exclusiveEnd){return between(start,exclusiveEnd,true);}
    List<Occurrence> between(LocalDate start,LocalDate exclusiveEnd,boolean reminders){
        if(!exclusiveEnd.isAfter(start))throw new IllegalArgumentException("End date must follow start");
        ZoneId zone=ZoneId.systemDefault();long from=start.atStartOfDay(zone).toInstant().toEpochMilli(),to=exclusiveEnd.atStartOfDay(zone).toInstant().toEpochMilli();
        // UTC all-day dates need a margin in extreme local time zones. Filter by civil date.
        Uri.Builder uri=CalendarContract.Instances.CONTENT_URI.buildUpon();ContentUris.appendId(uri,from-86400000L);ContentUris.appendId(uri,to+86400000L);
        String[] fields={CalendarContract.Instances.EVENT_ID,CalendarContract.Instances.BEGIN,CalendarContract.Instances.END,CalendarContract.Instances.TITLE,CalendarContract.Instances.EVENT_LOCATION,CalendarContract.Instances.ALL_DAY,CalendarContract.Instances.DESCRIPTION,CalendarContract.Instances.RRULE,CalendarContract.Instances.RDATE,CalendarContract.Instances.ORIGINAL_ID,CalendarContract.Instances.ACCESS_LEVEL,CalendarContract.Instances.HAS_ATTENDEE_DATA,CalendarContract.Instances.HAS_ALARM,CalendarContract.Instances.AVAILABILITY,CalendarContract.Instances.ORIGINAL_SYNC_ID};
        List<Occurrence> result=new ArrayList<>();
        java.util.Set<Long> alarmIds=new java.util.LinkedHashSet<>();
        try(Cursor cursor=resolver.query(uri.build(),fields,CalendarContract.Instances.VISIBLE+"=1",null,CalendarContract.Instances.BEGIN+" ASC,"+CalendarContract.Instances.END+" ASC")){
            if(cursor==null)throw new IllegalStateException("Calendar Provider unavailable");
            while(cursor.moveToNext()){
                Occurrence item=readOccurrence(cursor);if(item.lastDay().isBefore(start)||!item.startDay().isBefore(exclusiveEnd))continue;result.add(item);if(cursor.getInt(12)!=0)alarmIds.add(item.id);
            }
        }
        if(reminders)loadReminderLeads(result,alarmIds);
        return result;
    }
    /** Same positioned Instances row as between(); no provider work. */
    static Occurrence readOccurrence(Cursor cursor){
        // Android metadata adapter, not a WinCE record-layout equivalence.
        boolean exception=!cursor.isNull(9)||textPresent(cursor,14);
        int mask=(textPresent(cursor,7)||textPresent(cursor,8)||exception?2:0)|(textPresent(cursor,6)?4:0)|(textPresent(cursor,4)?8:0)|(cursor.getInt(11)!=0?16:0)|(cursor.getInt(10)==CalendarContract.Events.ACCESS_PRIVATE?32:0);
        return new Occurrence(cursor.getLong(0),cursor.getLong(1),cursor.getLong(2),cursor.getString(3),cursor.getString(4),cursor.getInt(5)!=0,mask,exception,agendaBusyStatus(cursor.getInt(13)),cursor.isNull(9)?null:Long.valueOf(cursor.getLong(9)),cursor.isNull(14)?null:cursor.getString(14));
    }
    private static boolean textPresent(Cursor cursor,int column){return !cursor.isNull(column)&&!cursor.getString(column).isEmpty();}
    static int agendaBusyStatus(int availability){return availability==CalendarContract.Events.AVAILABILITY_FREE?0:availability==CalendarContract.Events.AVAILABILITY_TENTATIVE?1:2;}
    private void loadReminderLeads(List<Occurrence> records,java.util.Set<Long> alarmIds){
        ArrayList<Long> ids=new ArrayList<>(alarmIds);java.util.Map<Long,Integer> leads=new java.util.HashMap<>();
        // Batch at most100 event IDs per query; never one provider call per occurrence.
        for(int offset=0;offset<ids.size();offset+=100){int count=Math.min(100,ids.size()-offset);StringBuilder where=new StringBuilder(CalendarContract.Reminders.EVENT_ID+" IN (");String[] args=new String[count];
            for(int i=0;i<count;i++){if(i>0)where.append(',');where.append('?');args[i]=String.valueOf(ids.get(offset+i));}where.append(") AND "+CalendarContract.Reminders.MINUTES+">=0 AND "+CalendarContract.Reminders.METHOD+"="+CalendarContract.Reminders.METHOD_ALERT);
            try(Cursor c=resolver.query(CalendarContract.Reminders.CONTENT_URI,new String[]{CalendarContract.Reminders.EVENT_ID,CalendarContract.Reminders.MINUTES},where.toString(),args,null)){
                if(c==null)continue;while(c.moveToNext()){long id=c.getLong(0);int minute=c.getInt(1);Integer previous=leads.get(id);if(previous==null||minute<previous)leads.put(id,minute);}
            }
        }
        for(Occurrence record:records)record.reminderLeadMinutes=leads.get(record.id);
    }
    static final class CalendarTarget {
        final long id;final String name;CalendarTarget(long id,String name){this.id=id;this.name=name;}
    }
    static final class Event {
        final long id,calendarId;final CalendarDraft draft;final boolean repeating,writable;final String timezone;
        private final ContentValues original;
        boolean reminderEditable;long reminderId=-1;
        Event(long id,long calendarId,CalendarDraft draft,boolean repeating,boolean writable,String timezone){this(id,calendarId,draft,repeating,writable,timezone,null);}
        private Event(long id,long calendarId,CalendarDraft draft,boolean repeating,boolean writable,String timezone,ContentValues original){this.id=id;this.calendarId=calendarId;this.draft=draft;this.repeating=repeating;this.writable=writable;this.timezone=timezone;this.original=original==null?null:new ContentValues(original);}
    }
    private static final String[] EVENT_FIELDS={CalendarContract.Events.CALENDAR_ID,CalendarContract.Events.TITLE,CalendarContract.Events.EVENT_LOCATION,CalendarContract.Events.DESCRIPTION,CalendarContract.Events.DTSTART,CalendarContract.Events.DTEND,CalendarContract.Events.ALL_DAY,CalendarContract.Events.RRULE,CalendarContract.Events.RDATE,CalendarContract.Events.ORIGINAL_ID,CalendarContract.Events.CALENDAR_ACCESS_LEVEL,CalendarContract.Events.EVENT_TIMEZONE,CalendarContract.Events.DURATION,CalendarContract.Events.ORIGINAL_SYNC_ID,CalendarContract.Events.ORIGINAL_INSTANCE_TIME,CalendarContract.Events.ORIGINAL_ALL_DAY,CalendarContract.Events.EXDATE,CalendarContract.Events.EXRULE};
    List<CalendarTarget> writableCalendars(){
        List<CalendarTarget> result=new ArrayList<>();String[] columns={CalendarContract.Calendars._ID,CalendarContract.Calendars.CALENDAR_DISPLAY_NAME};
        try(Cursor c=resolver.query(CalendarContract.Calendars.CONTENT_URI,columns,CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL+">=?",new String[]{String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)},CalendarContract.Calendars.CALENDAR_DISPLAY_NAME+" ASC")){
            if(c==null)throw new IllegalStateException("캘린더 저장소를 열 수 없습니다.");while(c.moveToNext())result.add(new CalendarTarget(c.getLong(0),c.getString(1)));
        }return result;
    }
    Event event(long id){
        Event event;
        try(Cursor c=resolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,id),EVENT_FIELDS,null,null,null)){
            if(c==null||!c.moveToFirst())throw new IllegalStateException("약속이 삭제되었거나 읽을 수 없습니다.");
            event=readEvent(id,c);
        }
        try(Cursor c=resolver.query(CalendarContract.Reminders.CONTENT_URI,new String[]{CalendarContract.Reminders._ID,CalendarContract.Reminders.MINUTES,CalendarContract.Reminders.METHOD},CalendarContract.Reminders.EVENT_ID+"=?",new String[]{String.valueOf(id)},null)){readReminders(event,c);}
        return event;
    }
    /** Native editor has one alert. Other provider reminder forms remain intact. */
    static void readReminders(Event event,Cursor rows){
        if(rows==null)throw new IllegalStateException("약속 알림을 읽을 수 없습니다.");
        event.reminderEditable=false;event.reminderId=-1;event.draft.reminderMinutes=null;
        if(rows.getCount()==0){event.reminderEditable=true;return;}
        if(rows.getCount()!=1||!rows.moveToFirst()||rows.isNull(0)||rows.getLong(0)<0||rows.isNull(1)||rows.isNull(2))return;
        long minutes=rows.getLong(1);
        if(rows.getInt(2)!=CalendarContract.Reminders.METHOD_ALERT||minutes<0||minutes>me.ancal.wmdroid.sdk.WmReminder.MAX_MINUTES)return;
        try{validateReminder((int)minutes);}catch(IllegalArgumentException unsupported){return;}
        event.reminderEditable=true;event.reminderId=rows.getLong(0);event.draft.reminderMinutes=(int)minutes;
    }
    /** Positioned synthetic/provider cursor mapping; no resolver work or writes. */
    static Event readEvent(long id,Cursor c){
        boolean allDay=c.getInt(6)!=0;ZoneId zone=allDay?ZoneOffset.UTC:ZoneId.systemDefault();long begin=c.getLong(4),end;try{end=me.ancal.wmdroid.sdk.WmCalendarDuration.eventEnd(begin,c.isNull(5)?null:c.getLong(5),c.getString(12));}catch(IllegalArgumentException invalid){throw new IllegalStateException("약속의 지속 시간을 읽을 수 없습니다.",invalid);}
        java.time.ZonedDateTime a=java.time.Instant.ofEpochMilli(begin).atZone(zone),b=java.time.Instant.ofEpochMilli(allDay?Math.max(begin,end-1):end).atZone(zone);
        CalendarDraft d=new CalendarDraft(a.toLocalDate());d.title=c.getString(1)==null?"":c.getString(1);d.location=c.getString(2)==null?"":c.getString(2);d.notes=c.getString(3)==null?"":c.getString(3);d.allDay=allDay;d.endDate=b.toLocalDate();d.startTime=a.toLocalTime();d.endTime=b.toLocalTime();d.startOffset=a.getOffset();d.endOffset=b.getOffset();
        ContentValues original=new ContentValues();for(int i=0;i<EVENT_FIELDS.length;i++){if(i==10)continue;String field=EVENT_FIELDS[i];if(c.isNull(i))original.putNull(field);else if(i==0||i==4||i==5||i==6||i==9||i==14||i==15)original.put(field,c.getLong(i));else original.put(field,c.getString(i));}
        return new Event(id,c.getLong(0),d,!c.isNull(7)&&!c.getString(7).isEmpty()||!c.isNull(8)&&!c.getString(8).isEmpty()||!c.isNull(9)||textPresent(c,13),c.getInt(10)>=CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,c.getString(11),original);
    }
    long save(Event existing,long calendarId,CalendarDraft draft){
        if(existing!=null&&(existing.repeating||!existing.writable))throw new IllegalArgumentException("이 약속은 현재 편집할 수 없습니다.");
        if(existing!=null&&draft.recurrenceRule.pattern!=me.ancal.wmdroid.sdk.WmRecurrenceRule.NONE)throw new IllegalArgumentException("기존 약속의 되풀이 변경은 아직 지원되지 않습니다.");
        if(existing!=null&&calendarId!=existing.calendarId)throw new IllegalArgumentException("현재 다른 캘린더로 이동할 수 없습니다.");
        ContentValues v=newEventValues(draft,ZoneId.systemDefault(),UiLanguage.t(context,"제목 없음"));boolean writable=false;for(CalendarTarget c:writableCalendars())if(c.id==calendarId)writable=true;if(!writable)throw new IllegalArgumentException("쓰기 가능한 캘린더를 선택하세요.");
        if(existing!=null&&!draft.allDay&&!existing.draft.allDay&&existing.timezone!=null)v.put(CalendarContract.Events.EVENT_TIMEZONE,existing.timezone);
        try{
            if(existing!=null){ArrayList<ContentProviderOperation> operations=existingEventOperations(existing,v,draft.reminderMinutes);ContentProviderResult[] results=resolver.applyBatch(CalendarContract.AUTHORITY,operations);int update=operations.size()==3?1:0;if(results==null||results.length!=operations.size()||results[update]==null||results[update].count==null||results[update].count!=1)throw new IllegalStateException("약속을 저장하지 못했습니다.");return existing.id;}
            // One provider batch binds the reminder to the newly inserted event;
            // CalendarProvider owns transaction/rollback semantics. No later separate insert.
            ContentProviderResult[] results=resolver.applyBatch(CalendarContract.AUTHORITY,newEventOperations(calendarId,v,draft.reminderMinutes));
            if(results==null||results.length==0||results[0].uri==null)throw new IllegalStateException("약속을 저장하지 못했습니다.");
            return ContentUris.parseId(results[0].uri);
        }catch(android.os.RemoteException|android.content.OperationApplicationException failed){throw new IllegalStateException("약속을 저장하지 못했습니다.",failed);}
    }
    /** No provider calls. Original event columns and changed alerts must still match. */
    static ArrayList<ContentProviderOperation> existingEventOperations(Event existing,ContentValues values,Integer minutes){
        if(existing==null||existing.repeating||!existing.writable||values==null||minutes!=null&&(minutes<0||minutes>me.ancal.wmdroid.sdk.WmReminder.MAX_MINUTES))throw new IllegalArgumentException("Invalid appointment update");
        validateReminder(minutes);
        if(existing.original==null)throw new IllegalArgumentException("Appointment provider snapshot required");
        Integer previous=existing.draft.reminderMinutes;
        boolean changed=existing.reminderEditable&&(previous==null?minutes!=null:!previous.equals(minutes));
        ContentValues event=new ContentValues(values);if(changed)event.put(CalendarContract.Events.HAS_ALARM,minutes==null?0:1);
        ContentProviderOperation update=eventMutation(existing,false).withValues(event).build();
        ArrayList<ContentProviderOperation> operations=new ArrayList<>();
        if(!changed)operations.add(update);
        if(!changed)return operations;
        ContentProviderOperation.Builder snapshot=ContentProviderOperation.newAssertQuery(CalendarContract.Reminders.CONTENT_URI).withSelection(CalendarContract.Reminders.EVENT_ID+"=?",new String[]{String.valueOf(existing.id)}).withExpectedCount(existing.reminderId<0?0:1);
        if(existing.reminderId>=0)snapshot.withValue(CalendarContract.Reminders._ID,existing.reminderId).withValue(CalendarContract.Reminders.MINUTES,existing.draft.reminderMinutes).withValue(CalendarContract.Reminders.METHOD,CalendarContract.Reminders.METHOD_ALERT);
        operations.add(snapshot.build());operations.add(update);
        if(existing.reminderId<0)operations.add(ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI).withValue(CalendarContract.Reminders.EVENT_ID,existing.id).withValue(CalendarContract.Reminders.MINUTES,minutes).withValue(CalendarContract.Reminders.METHOD,CalendarContract.Reminders.METHOD_ALERT).build());
        else{
            String where=CalendarContract.Reminders._ID+"=? AND "+CalendarContract.Reminders.EVENT_ID+"=?";String[] args={String.valueOf(existing.reminderId),String.valueOf(existing.id)};
            ContentProviderOperation.Builder reminderUpdate=minutes==null?ContentProviderOperation.newDelete(CalendarContract.Reminders.CONTENT_URI):ContentProviderOperation.newUpdate(CalendarContract.Reminders.CONTENT_URI).withValue(CalendarContract.Reminders.MINUTES,minutes);
            operations.add(reminderUpdate.withSelection(where,args).withExpectedCount(1).build());
        }
        return operations;
    }
    /** Pure column construction; inserts still require permission and a writable calendar. */
    static ContentValues newEventValues(CalendarDraft draft,ZoneId zone){
        return newEventValues(draft,zone,"제목 없음");
    }
    static ContentValues newEventValues(CalendarDraft draft,ZoneId zone,String noSubject){
        if(draft==null)throw new IllegalArgumentException("Appointment required");
        CalendarRecurrence.Prepared ready=CalendarRecurrence.prepare(draft,draft.recurrenceRule,draft.recurrenceUntil,zone);
        // Static API14 evidence shows these combinations can expand incorrectly.
        // SDK_INT alone cannot certify a vendor provider; keep an explicit gate
        // until expansion/capability evidence exists for the installed provider.
        if(ready.requiresGroupedYearlySupport||ready.requiresMultiweekSupport)
            throw new IllegalArgumentException("이 되풀이 유형의 캘린더 저장 지원은 아직 확인되지 않았습니다.");
        ContentValues values=new ContentValues();values.put(CalendarContract.Events.TITLE,me.ancal.wmdroid.sdk.WmAppointmentText.subjectForSave(draft.title,noSubject));values.put(CalendarContract.Events.EVENT_LOCATION,draft.location);values.put(CalendarContract.Events.DESCRIPTION,draft.notes);values.put(CalendarContract.Events.DTSTART,ready.dtstart);values.put(CalendarContract.Events.ALL_DAY,ready.allDay?1:0);values.put(CalendarContract.Events.EVENT_TIMEZONE,ready.timezone);
        if(ready.rrule==null)values.put(CalendarContract.Events.DTEND,ready.dtend);
        else{values.put(CalendarContract.Events.RRULE,ready.rrule);values.put(CalendarContract.Events.DURATION,ready.duration);}
        return values;
    }
    /** Pure batch construction: can be checked without touching personal Calendar data. */
    static ArrayList<ContentProviderOperation> newEventOperations(long calendarId,ContentValues values,Integer reminderMinutes){
        if(calendarId<0||values==null||reminderMinutes!=null&&(reminderMinutes<0||reminderMinutes>me.ancal.wmdroid.sdk.WmReminder.MAX_MINUTES))throw new IllegalArgumentException("Invalid new appointment defaults");
        validateReminder(reminderMinutes);
        ContentValues event=new ContentValues(values);event.put(CalendarContract.Events.CALENDAR_ID,calendarId);event.put(CalendarContract.Events.STATUS,CalendarContract.Events.STATUS_CONFIRMED);event.put(CalendarContract.Events.HAS_ALARM,reminderMinutes==null?0:1);
        ArrayList<ContentProviderOperation> operations=new ArrayList<>();operations.add(ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI).withValues(event).build());
        if(reminderMinutes!=null)operations.add(ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI).withValueBackReference(CalendarContract.Reminders.EVENT_ID,0).withValue(CalendarContract.Reminders.MINUTES,reminderMinutes).withValue(CalendarContract.Reminders.METHOD,CalendarContract.Reminders.METHOD_ALERT).build());
        return operations;
    }
    private static void validateReminder(Integer minutes){if(minutes!=null)me.ancal.wmdroid.sdk.WmReminder.fromMinutes(true,minutes);}
    private static ContentProviderOperation.Builder eventMutation(Event existing,boolean deleting){
        if(existing==null||existing.repeating||!existing.writable||existing.original==null)throw new IllegalArgumentException("Appointment provider snapshot required");
        StringBuilder editable=new StringBuilder(CalendarContract.Events._ID+"=? AND "+CalendarContract.Events.CALENDAR_ID+"=? AND ("+CalendarContract.Events.RRULE+" IS NULL OR "+CalendarContract.Events.RRULE+"='') AND ("+CalendarContract.Events.RDATE+" IS NULL OR "+CalendarContract.Events.RDATE+"='') AND "+CalendarContract.Events.ORIGINAL_ID+" IS NULL AND ("+CalendarContract.Events.ORIGINAL_SYNC_ID+" IS NULL OR "+CalendarContract.Events.ORIGINAL_SYNC_ID+"='')");
        ArrayList<String> eventArgs=new ArrayList<>();eventArgs.add(String.valueOf(existing.id));eventArgs.add(String.valueOf(existing.calendarId));
        for(java.util.Map.Entry<String,Object> column:existing.original.valueSet()){editable.append(" AND ").append(column.getKey());if(column.getValue()==null)editable.append(" IS NULL");else{editable.append("=?");eventArgs.add(column.getValue().toString());}}
        // API14 event-ID URIs ignore selection; use the table for both mutations.
        ContentProviderOperation.Builder operation=deleting?ContentProviderOperation.newDelete(CalendarContract.Events.CONTENT_URI):ContentProviderOperation.newUpdate(CalendarContract.Events.CONTENT_URI);
        return operation.withSelection(editable.toString(),eventArgs.toArray(new String[0])).withExpectedCount(1);
    }
    static ContentProviderOperation existingEventDeletion(Event event){return eventMutation(event,true).build();}
    void delete(Event event){
        ArrayList<ContentProviderOperation> operations=new ArrayList<>();operations.add(existingEventDeletion(event));
        try{ContentProviderResult[] results=resolver.applyBatch(CalendarContract.AUTHORITY,operations);if(results==null||results.length!=1||results[0]==null||results[0].count==null||results[0].count!=1)throw new IllegalStateException("약속을 삭제하지 못했습니다.");}
        catch(android.os.RemoteException|android.content.OperationApplicationException failed){throw new IllegalStateException("약속을 삭제하지 못했습니다.",failed);}
    }

}
