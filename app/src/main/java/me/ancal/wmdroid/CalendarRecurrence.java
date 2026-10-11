package me.ancal.wmdroid;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import me.ancal.wmdroid.sdk.WmCalendarDuration;
import me.ancal.wmdroid.sdk.WmRecurrenceRule;

/** Pure new-record storage contract; never reads or writes CalendarProvider.
 * This is not an existing-series parser or an expansion capability certificate.
 * Callers must check the capability flags before enabling provider writes.
 */
final class CalendarRecurrence {
    static final class Prepared {
        final long dtstart;
        final Long dtend;
        final String duration,rrule,timezone;
        final boolean allDay,requiresGroupedYearlySupport,requiresMultiweekSupport;
        Prepared(long begin,Long end,String duration,String rule,String zone,boolean allDay,
                 boolean groupedYearly,boolean multiweek){
            dtstart=begin;dtend=end;this.duration=duration;rrule=rule;timezone=zone;
            this.allDay=allDay;requiresGroupedYearlySupport=groupedYearly;
            requiresMultiweekSupport=multiweek;
        }
    }
    private CalendarRecurrence(){}

    static Prepared prepare(CalendarDraft draft,WmRecurrenceRule rule,LocalDate until,ZoneId zone){
        if(draft==null||rule==null||zone==null)throw new IllegalArgumentException("Appointment, rule and timezone required");
        if(draft.startDate==null||draft.endDate==null||draft.startDate.getYear()<1||draft.startDate.getYear()>9999
                ||draft.endDate.getYear()<1||draft.endDate.getYear()>9999)
            throw new IllegalArgumentException("Appointment dates must be in years0001..9999");
        if(!draft.allDay&&zone instanceof ZoneOffset&&!zone.equals(ZoneOffset.UTC))
            throw new IllegalArgumentException("이 시간대는 캘린더 저장소에서 사용할 수 없습니다.");
        String timezone=draft.allDay||zone.equals(ZoneOffset.UTC)?"UTC":zone.getId();
        if(!draft.allDay&&(draft.startTime==null||draft.endTime==null))
            throw new IllegalArgumentException("Appointment times required");
        long[] range=draft.range(zone);
        if(rule.pattern==WmRecurrenceRule.NONE){
            if(!draft.allDay&&(draft.startTime.getNano()%1000000!=0||draft.endTime.getNano()%1000000!=0))throw new IllegalArgumentException("Millisecond appointment times required");
            if(until!=null)throw new IllegalArgumentException("No recurrence cannot have an UNTIL date");
            return new Prepared(range[0],range[1],null,null,timezone,draft.allDay,false,false);
        }
        if(!draft.allDay&&(draft.startTime.getNano()!=0||draft.endTime.getNano()!=0))throw new IllegalArgumentException("Whole-second recurring appointment times required");
        // Years0001..9999 bound this arithmetic safely; compare civil time, not DST elapsed time.
        long civilMillis=draft.allDay?ChronoUnit.DAYS.between(draft.startDate,draft.endDate.plusDays(1))*86400000L:
                ChronoUnit.SECONDS.between(draft.startDate.atTime(draft.startTime),draft.endDate.atTime(draft.endTime))*1000L;
        // Native4205 replaces4203 when both constraints fail.
        rule.validateAppointmentReminder(draft.reminderMinutes!=null,draft.reminderMinutes==null?0:draft.reminderMinutes);
        rule.validateAppointmentDuration(civilMillis);
        if(!matches(rule,draft.startDate))throw new IllegalArgumentException("첫 번째 약속 날짜가 되풀이 유형과 일치해야 합니다.");
        String limit=null;
        if(rule.endMode==WmRecurrenceRule.UNTIL){
            if(until==null||until.isBefore(draft.startDate)||until.getYear()<1||until.getYear()>9999)
                throw new IllegalArgumentException("올바른 되풀이 종료 날짜를 선택하세요.");
            if(draft.allDay)limit=until.format(DateTimeFormatter.BASIC_ISO_DATE);
            else{
                // An inclusive civil DATE means all starts on that date, not
                // a copy of DTSTART's clock that may fall in a DST gap/overlap.
                // atStartOfDay follows ZoneRules when midnight is skipped.
                java.time.Instant boundary=until.plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1);
                if(boundary.toEpochMilli()<range[0])throw new IllegalArgumentException("되풀이 종료 날짜는 첫 번째 약속보다 빠를 수 없습니다.");
                limit=DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'",java.util.Locale.US)
                        .withZone(ZoneOffset.UTC).format(boundary);
            }
        }else if(until!=null)throw new IllegalArgumentException("UNTIL supplied for another end mode");
        String rrule=draft.allDay?rule.toAllDayRrule(limit):rule.toRrule(limit);
        String duration;
        if(draft.allDay){
            long days=ChronoUnit.DAYS.between(draft.startDate,draft.endDate.plusDays(1));
            if(days<1||days>Integer.MAX_VALUE)throw new IllegalArgumentException("All-day duration outside supported range");
            duration=WmCalendarDuration.allDay((int)days);
        }else{
            long elapsed=range[1]-range[0];
            if(elapsed<0)throw new IllegalArgumentException("유효한 지속 시간을 입력하십시오.");
            duration=WmCalendarDuration.timed(elapsed);
        }
        boolean grouped=rule.pattern==WmRecurrenceRule.YEARLY_ORDINAL&&Integer.bitCount(rule.dayMask)>1;
        boolean multiweek=rule.pattern==WmRecurrenceRule.WEEKLY&&rule.interval>1&&Integer.bitCount(rule.dayMask)>1;
        return new Prepared(range[0],null,duration,rrule,timezone,draft.allDay,grouped,multiweek);
    }

    /** Matching only; never silently moves DTSTART to another day. */
    static boolean matches(WmRecurrenceRule rule,LocalDate date){
        if(rule==null||date==null)throw new IllegalArgumentException("Pattern and date required");
        switch(rule.pattern){
            case WmRecurrenceRule.NONE:return true;
            case WmRecurrenceRule.DAILY:return rule.dayMask==0||contains(rule.dayMask,date);
            case WmRecurrenceRule.WEEKLY:return contains(rule.dayMask,date);
            case WmRecurrenceRule.MONTHLY_DAY:return date.getDayOfMonth()==rule.day;
            case WmRecurrenceRule.YEARLY_DAY:return date.getMonthValue()==rule.month&&date.getDayOfMonth()==rule.day;
            case WmRecurrenceRule.YEARLY_ORDINAL:if(date.getMonthValue()!=rule.month)return false;
                // Fall through: ordinal selection is within BYMONTH's month.
            case WmRecurrenceRule.MONTHLY_ORDINAL:
                if(!contains(rule.dayMask,date))return false;
                int position=0,total=0;
                for(int day=1;day<=date.lengthOfMonth();day++)if(contains(rule.dayMask,date.withDayOfMonth(day))){
                    total++;if(day==date.getDayOfMonth())position=total;
                }
                return rule.ordinal>0?position==rule.ordinal:position==total+rule.ordinal+1;
            default:throw new IllegalArgumentException("Unknown recurrence pattern");
        }
    }
    private static boolean contains(int mask,LocalDate date){return (mask&(1<<(date.getDayOfWeek().getValue()%7)))!=0;}
}
