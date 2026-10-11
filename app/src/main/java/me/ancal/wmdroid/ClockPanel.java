package me.ancal.wmdroid;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/** Host-owned calendar adapter for the shared SDK clock body. */
final class ClockPanel extends me.ancal.wmdroid.sdk.WmClockPanel {
    private final ExecutorService worker;
    private final CalendarStore store;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Future<?> pending;
    private boolean attached;
    private int request;
    ClockPanel(Context context, Typeface regular, Typeface bold, ExecutorService worker) {
        super(context,regular,UiLanguage.language(context));
        this.worker=worker;store=new CalendarStore(context);
    }
    private boolean hasCalendarPermission() {
        return DeviceApi.permission(getContext(),Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED;
    }
    private void loadAppointment() {
        final int token = ++request;
        if (!hasCalendarPermission()) {
            setAppointment(UiLanguage.t(getContext(),"일정 연결 필요"), "");
            return;
        }
        setAppointment(UiLanguage.t(getContext(),"일정 확인 중…"), "");
        try {
            pending = worker.submit(() -> {
                CalendarStore.Occurrence next = null;
                String error = null;
                try {
                    ZoneId zone = ZoneId.systemDefault();
                    long now = System.currentTimeMillis();
                    LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
                    List<CalendarStore.Occurrence> items = store.between(today, today.plusDays(14));
                    long earliest = Long.MAX_VALUE;
                    // Android mapping: earliest visible, not-ended occurrence. ROM selects PIM
                    // flags&0x10; exact flag/selection equivalence has not been established.
                    for (CalendarStore.Occurrence item : items) {
                        if (item.allDay ? item.lastDay().isBefore(today) : item.end <= now) continue;
                        // Provider all-day UTC millis encode civil dates, not local instants.
                        long start = item.allDay ? item.startDay().atStartOfDay(zone).toInstant().toEpochMilli() : item.begin;
                        if (start < earliest) { earliest = start; next = item; }
                    }
                } catch (SecurityException denied) {
                    error = UiLanguage.t(getContext(),"일정 연결 필요");
                } catch (RuntimeException unavailable) {
                    error = UiLanguage.t(getContext(),"일정을 읽을 수 없습니다.");
                }
                final CalendarStore.Occurrence result = next;
                final String failure = error;
                handler.post(() -> {
                    if (!attached || token != request) return;
                    pending = null;
                    if (!hasCalendarPermission()) setAppointment(UiLanguage.t(getContext(),"일정 연결 필요"), "");
                    else if (failure != null) setAppointment(failure, "");
                    else if (result == null) setAppointment(UiLanguage.t(getContext(),"예정된 약속 없음"), "");
                    else setAppointment(result.title, result.allDay ? UiLanguage.t(getContext(),"종일") : time(result.begin));
                });
            });
        } catch (RejectedExecutionException stopped) {
            setAppointment(UiLanguage.t(getContext(),"일정을 읽을 수 없습니다."), "");
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        loadAppointment();
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        request++;
        if (pending != null) { pending.cancel(true); pending = null; }
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
}
