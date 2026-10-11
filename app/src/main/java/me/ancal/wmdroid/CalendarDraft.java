package me.ancal.wmdroid;

import java.time.*;

/** Appointment validation independent of Android. Civil all-day end is exclusive. */
final class CalendarDraft {
    String title="",location="",notes="";LocalDate startDate,endDate;LocalTime startTime=LocalTime.of(9,0),endTime=LocalTime.of(10,0);boolean allDay;
    ZoneOffset startOffset,endOffset;
    /** Null means no alert. Unsupported existing reminder sets stay provider-owned. */
    Integer reminderMinutes;
    me.ancal.wmdroid.sdk.WmRecurrenceRule recurrenceRule=me.ancal.wmdroid.sdk.WmRecurrenceRule.none();
    LocalDate recurrenceUntil;
    CalendarDraft(LocalDate day){startDate=day;endDate=day;}
    boolean sameNonrecurringContent(CalendarDraft other,ZoneId zone){
        if(other==null||recurrenceRule.pattern!=me.ancal.wmdroid.sdk.WmRecurrenceRule.NONE||other.recurrenceRule.pattern!=me.ancal.wmdroid.sdk.WmRecurrenceRule.NONE)return false;
        return title.equals(other.title)&&location.equals(other.location)&&notes.equals(other.notes)&&allDay==other.allDay
            &&(reminderMinutes==null?other.reminderMinutes==null:reminderMinutes.equals(other.reminderMinutes))
            &&java.util.Arrays.equals(range(zone),other.range(zone));
    }
    long[] range(ZoneId zone){
        if(startDate==null||endDate==null)throw new IllegalArgumentException("시작·종료 날짜를 입력하세요.");
        if(allDay){if(endDate.isBefore(startDate))throw new IllegalArgumentException("종료 날짜는 시작 날짜보다 빠를 수 없습니다.");return new long[]{startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()};}
        LocalDateTime start=LocalDateTime.of(startDate,startTime),end=LocalDateTime.of(endDate,endTime);
        if(zone.getRules().getValidOffsets(start).isEmpty()||zone.getRules().getValidOffsets(end).isEmpty())throw new IllegalArgumentException("일광 절약 시간 전환으로 존재하지 않는 시각입니다.");
        long a=instant(start,zone,startOffset),b=instant(end,zone,endOffset);if(b<a)throw new IllegalArgumentException("종료 시각은 시작 시각보다 늦어야 합니다.");return new long[]{a,b};
    }
    private static long instant(LocalDateTime civil,ZoneId zone,ZoneOffset preferred){
        java.util.List<ZoneOffset> offsets=zone.getRules().getValidOffsets(civil);
        if(offsets.size()>1&&(preferred==null||!offsets.contains(preferred)))throw new IllegalArgumentException("일광 절약 시간 전환으로 중복되는 시각입니다. 다른 시각을 선택하세요.");
        return civil.toInstant(offsets.size()==1?offsets.get(0):preferred).toEpochMilli();
    }
}
