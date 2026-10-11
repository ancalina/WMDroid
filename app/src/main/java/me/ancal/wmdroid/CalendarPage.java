package me.ancal.wmdroid;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** ROM Calendar commands 40080 agenda / 40083 month, in the launcher's VGA surface.
 * Shared SDK controls own presentation; full native editing and font roles remain partial.
 */
final class CalendarPage extends LinearLayout implements me.ancal.wmdroid.sdk.WmControls.RestyleListener {
    interface Open { void show(CalendarStore.Occurrence occurrence); }
    public void onWmRestyle(Typeface regular,Typeface bold,int selection,int tabColor){
        this.regular=regular;this.bold=bold;
        refreshLunarCalendar();
        if(!UiLanguage.locale(getContext()).equals(renderedLocale)){
            if(mode==YEAR&&yearView!=null)refreshYearAppearance();else{if(timelineView!=null)restoreTimelineMinute=timelineView.getScrollMinute();reload();return;}
        }
        header.setColors(selection);invalidateTree(surface);
    }
    private void invalidateTree(View view){if(view instanceof me.ancal.wmdroid.sdk.WmCalendarView)((me.ancal.wmdroid.sdk.WmCalendarView)view).setColors(LauncherTheme.selection(getContext(),false),me.ancal.wmdroid.sdk.WmPalette.face(LauncherTheme.selection(getContext(),false)),Color.WHITE);view.invalidate();if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)invalidateTree(group.getChildAt(i));}}
    private java.util.Locale renderedLocale;
    private Typeface regular,bold;
    private final CalendarStore store;
    private final Executor worker;
    // API14 View.post before attachment queues work on the calling thread.
    private final android.os.Handler ui=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Open open;
    private final me.ancal.wmdroid.sdk.WmCalendarHeader header;
    private final FrameLayout content;private View pickerOverlay,pickerContent;private int pickerGeneration;
    private final RetroSurface surface;
    private boolean renderedOnce;
    private LocalDate selected=LocalDate.now();
    private LocalDate monthAnchor;
    static final int AGENDA=0,DAY=1,WEEK=2,MONTH=3,YEAR=4;
    private me.ancal.wmdroid.sdk.WmYearView yearView;
    private me.ancal.wmdroid.sdk.WmMonthView monthGrid;
    private me.ancal.wmdroid.sdk.WmLunarCalendar lunarCalendar;
    private String lunarRevision,loadingLunarRevision;
    private int lunarRequest;
    private boolean lunarMonth;
    private boolean yearAtJanuary;
    private int mode;
    private me.ancal.wmdroid.sdk.WmCalendarGeneralOptions.Options options;
    private me.ancal.wmdroid.sdk.WmCalendarView timelineView;
    private me.ancal.wmdroid.sdk.WmAgendaView agendaView;
    private Float restoreTimelineMinute;
    private int[] timeCursorRows;
    private boolean initialWeekSelection,populateWeekSelection;
    static final class DeletionSelection {
        final int request;final LocalDate day;final CalendarStore.Occurrence successor;
        DeletionSelection(int request,LocalDate day,CalendarStore.Occurrence successor){this.request=request;this.day=day;this.successor=successor;}
    }
    private me.ancal.wmdroid.sdk.WmCalendarView.WeekNavigation pendingWeekNavigation;
    private LocalDate pendingWeekFirst;
    private Runnable onViewChanged;
    private int detailMode=AGENDA;
    private int request;
    private List<CalendarStore.Occurrence> occurrences=new ArrayList<>();
    private CalendarStore.Occurrence launchOccurrence;
    private long savedEventId=-1;
    CalendarPage(Context context,Typeface regular,Typeface bold,Executor worker,Open open){this(context,regular,bold,worker,open,null,-1);}
    CalendarPage(Context context,Typeface regular,Typeface bold,Executor worker,Open open,LocalDate day,int restoredMode){
        this(context,regular,bold,worker,open,day,restoredMode,null);
    }
    CalendarPage(Context context,Typeface regular,Typeface bold,Executor worker,Open open,LocalDate day,int restoredMode,CalendarStore.Occurrence launchOccurrence){
        super(context);if(day!=null&&(day.getYear()<me.ancal.wmdroid.sdk.WmYearChooser.MIN_YEAR||day.getYear()>me.ancal.wmdroid.sdk.WmYearChooser.MAX_YEAR||restoredMode<AGENDA||restoredMode>YEAR))throw new IllegalArgumentException("Invalid calendar restore state");if(day!=null)selected=day;this.regular=regular;this.bold=bold;this.worker=worker;this.open=open;store=new CalendarStore(context);timeCursorRows=CalendarPreferences.readCursorRows(context);options=CalendarPreferences.read(context);mode=day==null?options.startView:restoredMode;initialWeekSelection=mode==WEEK;if(mode==DAY)detailMode=DAY;setOrientation(VERTICAL);setBackgroundColor(Color.WHITE);
        content=new FrameLayout(context);addView(content,new LayoutParams(-1,0,1));LinearLayout layout=new LinearLayout(context);layout.setOrientation(VERTICAL);content.addView(layout,new FrameLayout.LayoutParams(-1,-1));
        header=new me.ancal.wmdroid.sdk.WmCalendarHeader(context,regular,UiLanguage.locale(context));header.setColors(LauncherTheme.selection(context,false));header.setOnCommandListener(this::headerCommand);layout.addView(header,new LayoutParams(-1,me.ancal.wmdroid.sdk.WmCalendarHeader.HEIGHT));
        surface=new RetroSurface(context,true,RetroSurface.Role.CALENDAR);layout.addView(surface,new LayoutParams(-1,0,1));this.launchOccurrence=launchOccurrence;refreshLunarCalendar();reload();
    }
    LocalDate selectedDay(){return selected;}
    CalendarDraft newDraft(){
        int[] time=timelineView!=null&&(mode==DAY||mode==WEEK)?timelineView.getTimeSelection():null;
        if(time==null)return CalendarPreferences.newDraft(getContext(),selected);
        int[] date=timelineView.dateAt(time[0]);LocalDate day=LocalDate.of(date[0],date[1],date[2]);CalendarDraft draft=CalendarPreferences.newDraft(getContext(),day);
        draft.startTime=java.time.LocalTime.of(time[1]/60,time[1]%60);draft.endDate=time[2]==1440?day.plusDays(1):day;draft.endTime=java.time.LocalTime.of(time[2]%1440/60,time[2]%60);return draft;
    }

    boolean monthView(){return mode==MONTH;}
    // Original40085 is Month+flag. Optional user-pack data replaces native registry/table capability.
    boolean hasLunarCalendar(){refreshLunarCalendar();return lunarCalendar!=null;}
    boolean isLunarMonth(){return mode==MONTH&&lunarMonth&&hasLunarCalendar();}
    void setLunarMonth(){if(!hasLunarCalendar())return;lunarMonth=true;switchView(MONTH);}
    private void applyLunarCalendar(){if(monthGrid!=null)monthGrid.setLunarCalendar(mode==MONTH&&lunarMonth?lunarCalendar:null);}
    private void refreshLunarCalendar(){
        String current=me.ancal.wmdroid.sdk.WmResources.revision(getContext());
        if(current.equals(lunarRevision)||current.equals(loadingLunarRevision))return;
        final int token=++lunarRequest;loadingLunarRevision=current;
        lunarRevision=null;lunarCalendar=null;applyLunarCalendar();
        try{worker.execute(()->{
            String revision=current;me.ancal.wmdroid.sdk.WmLunarCalendar loaded=null;
            try{
                me.ancal.wmdroid.sdk.WmResourcePack pack;
                // Pair the immutable snapshot with its identity; parsing does not hold the install lock.
                synchronized(me.ancal.wmdroid.sdk.WmResources.class){pack=me.ancal.wmdroid.sdk.WmResources.prepare(getContext());revision=me.ancal.wmdroid.sdk.WmResources.revision(getContext());}
                loaded=me.ancal.wmdroid.sdk.WmLunarCalendar.load(pack);
            }catch(java.io.IOException|RuntimeException unavailable){loaded=null;}
            final String resolvedRevision=revision;final me.ancal.wmdroid.sdk.WmLunarCalendar result=loaded;
            ui.post(()->{
                if(token!=lunarRequest)return;
                loadingLunarRevision=null;
                if(!resolvedRevision.equals(me.ancal.wmdroid.sdk.WmResources.revision(getContext()))){refreshLunarCalendar();return;}
                lunarRevision=resolvedRevision;lunarCalendar=result;applyLunarCalendar();
            });
        });}catch(java.util.concurrent.RejectedExecutionException unavailable){loadingLunarRevision=null;lunarRevision=current;}
    }
    int viewMode(){return mode;}
    void applyOptions(me.ancal.wmdroid.sdk.WmCalendarGeneralOptions.Options next){
        boolean rangeChanged=options.firstDayOfWeek!=next.firstDayOfWeek||options.weekDays!=next.weekDays;
        options=next;header.setFirstDayOfWeek(next.firstDayOfWeek);header.setWeekNumbersVisible(next.weekNumbers);
        if(yearView!=null)yearView.setFirstDayOfWeek(next.firstDayOfWeek);
        if(monthGrid!=null){monthGrid.setFirstDayOfWeek(next.firstDayOfWeek);monthGrid.setWeekNumbersVisible(next.weekNumbers);}
        if(mode==WEEK&&pickerContent instanceof me.ancal.wmdroid.sdk.WmCalendarPicker)((me.ancal.wmdroid.sdk.WmCalendarPicker)pickerContent).setWeekNumbersVisible(next.weekNumbers);
        // Start view controls the next opening, never hijacks the current view.
        if(rangeChanged&&(mode==WEEK||mode==MONTH)){if(timelineView!=null)restoreTimelineMinute=timelineView.getScrollMinute();closePicker();reload();}
        else if(timelineView!=null)timelineView.setInterval(next.halfHour?30:60);
    }
    void applyAppointments(me.ancal.wmdroid.sdk.WmCalendarAppointmentsOptions.Options next){if(timelineView!=null)timelineView.setDisplayIconMask(next.displayMask);if(agendaView!=null)agendaView.setDisplayIconMask(next.displayMask);}
    String nextViewCaption(){return new String[]{"일","주","월","연도","일정"}[mode];}
    void nextView(){setView((mode+1)%5);}
    void setOnViewChanged(Runnable action){onViewChanged=action;}
    void setMonth(boolean enabled){setView(enabled?MONTH:AGENDA);}
    void setView(int mode){if(mode<AGENDA||mode>YEAR)throw new IllegalArgumentException("Unsupported Calendar view");if(mode==MONTH)lunarMonth=false;switchView(mode);}
    private void switchView(int mode){launchOccurrence=null;savedEventId=-1;restoreTimelineMinute=null;pendingWeekNavigation=null;pendingWeekFirst=null;monthAnchor=null;closePicker();if(mode==AGENDA||mode==DAY)detailMode=mode;this.mode=mode;initialWeekSelection=mode==WEEK;populateWeekSelection=false;reload();if(onViewChanged!=null)onViewChanged.run();}
    DeletionSelection captureDeletionSelection(long removedId){
        if(mode!=WEEK||timelineView==null)return null;
        me.ancal.wmdroid.sdk.WmCalendarView.Event current=timelineView.getSelectedEvent(),next=null;
        if(current!=null&&((CalendarStore.Occurrence)current.tag).id==removedId)next=timelineView.getWeekRemovalSuccessor();
        return new DeletionSelection(request,selected,next==null?null:(CalendarStore.Occurrence)next.tag);
    }
    void refreshAfterDelete(DeletionSelection snapshot){
        if(snapshot!=null&&mode==WEEK&&snapshot.request==request&&snapshot.day.equals(selected)){populateWeekSelection=true;launchOccurrence=snapshot.successor;}
        refresh();
    }
    void refreshAfterSave(long eventId,LocalDate day){
        if(eventId<0||day==null||day.getYear()<me.ancal.wmdroid.sdk.WmYearChooser.MIN_YEAR||day.getYear()>me.ancal.wmdroid.sdk.WmYearChooser.MAX_YEAR)throw new IllegalArgumentException("Invalid saved appointment");
        launchOccurrence=null;savedEventId=eventId;pendingWeekNavigation=null;pendingWeekFirst=null;monthAnchor=null;initialWeekSelection=mode==WEEK;populateWeekSelection=false;selected=day;closePicker();if(mode==YEAR)reload();else refresh();
    }
    void refresh(){if(mode==YEAR&&yearView!=null)refreshYearAppearance();else{if(timelineView!=null)restoreTimelineMinute=timelineView.getScrollMinute();reload();}}
    void go(LocalDate day){if(day.getYear()<me.ancal.wmdroid.sdk.WmYearChooser.MIN_YEAR||day.getYear()>me.ancal.wmdroid.sdk.WmYearChooser.MAX_YEAR)return;launchOccurrence=null;savedEventId=-1;restoreTimelineMinute=null;pendingWeekNavigation=null;pendingWeekFirst=null;monthAnchor=null;initialWeekSelection=false;populateWeekSelection=false;yearAtJanuary=false;selected=day;reload();}
    private void chooseYear(int year){launchOccurrence=null;savedEventId=-1;monthAnchor=null;selected=selected.withYear(year);yearAtJanuary=mode==YEAR;reload();}
    private void move(int direction){if(mode==YEAR&&yearView!=null){yearView.movePage(direction);return;}go(mode==MONTH?selected.plusMonths(direction):selected.plusDays(mode==WEEK?direction*7:direction));}
    private TextView text(String value,boolean heavy){return ClassicUi.text(getContext(),heavy?bold:regular,value);}

    private void headerCommand(int command){
        if(command==me.ancal.wmdroid.sdk.WmCalendarHeader.TODAY){closePicker();go(LocalDate.now());return;}
        if(command==me.ancal.wmdroid.sdk.WmCalendarHeader.PREVIOUS||command==me.ancal.wmdroid.sdk.WmCalendarHeader.NEXT){closePicker();move(command==me.ancal.wmdroid.sdk.WmCalendarHeader.PREVIOUS?-1:1);return;}
        if(command>=me.ancal.wmdroid.sdk.WmCalendarHeader.FIRST_WEEKDAY&&command<me.ancal.wmdroid.sdk.WmCalendarHeader.FIRST_WEEKDAY+7){go(regionalWeekFirstDay(selected).plusDays(command-me.ancal.wmdroid.sdk.WmCalendarHeader.FIRST_WEEKDAY));return;}
        // caldll028FD868: Week40082 month command20012 opens SysMonthCal32.
        if(mode==WEEK&&command==me.ancal.wmdroid.sdk.WmCalendarHeader.MONTH_PICKER){datePicker();return;}
        if(command==me.ancal.wmdroid.sdk.WmCalendarHeader.DATE_PICKER){datePicker();return;}
        boolean years=command==me.ancal.wmdroid.sdk.WmCalendarHeader.YEAR_PICKER,months=command==me.ancal.wmdroid.sdk.WmCalendarHeader.MONTH_PICKER;
        final int[] nearby=years?me.ancal.wmdroid.sdk.WmYearChooser.nearbyYears(selected.getYear()):null;
        final List<LocalDate> weekDates=new ArrayList<>();
        String[] labels;int chosen;
        if(years){labels=new String[nearby.length+1];for(int i=0;i<nearby.length;i++)labels[i]=String.valueOf(nearby[i]);labels[nearby.length]=me.ancal.wmdroid.sdk.WmYearChooser.otherLabel(getContext(),UiLanguage.locale(getContext()));chosen=selected.getYear()-nearby[0];}
        else if(months){labels=new java.text.DateFormatSymbols(UiLanguage.locale(getContext())).getMonths();labels=java.util.Arrays.copyOf(labels,12);chosen=selected.getMonthValue()-1;}
        else{
            List<String> names=new ArrayList<>();chosen=-1;java.util.Locale locale=UiLanguage.locale(getContext());
            // caldll028F9B0C numbers the Monday within each configured week.
            LocalDate reference=regionalWeekFirstDay(selected).plusDays(options.firstDayOfWeek==java.util.Calendar.SUNDAY?1:0);
            for(int offset=-2;offset<=2;offset++){LocalDate day=selected.plusWeeks(offset),anchor=reference.plusWeeks(offset);if(anchor.getYear()<1900||anchor.getYear()>2999||day.getYear()<1900||day.getYear()>2999)continue;if(offset==0)chosen=names.size();weekDates.add(day);names.add(String.valueOf(me.ancal.wmdroid.sdk.WmCalendarHeader.toolbarWeekNumber(day.getYear(),day.getMonthValue(),day.getDayOfMonth(),options.firstDayOfWeek))+("ko".equals(locale.getLanguage())?"주":""));}labels=names.toArray(new String[0]);
        }
        final int generation=pickerGeneration+1;me.ancal.wmdroid.sdk.WmRadioMenu choices=new me.ancal.wmdroid.sdk.WmRadioMenu(getContext(),regular,labels,chosen,UiLanguage.t(getContext()," 선택됨"),LauncherTheme.selection(getContext(),false),index->{if(generation!=pickerGeneration)return;if(years&&index==nearby.length){yearEditor();return;}closePicker();if(years)chooseYear(nearby[index]);else go(months?selected.withMonth(index+1):weekDates.get(index));});
        me.ancal.wmdroid.sdk.WmScrollView scroll=new me.ancal.wmdroid.sdk.WmScrollView(getContext(),choices);presentPicker(scroll,Math.max(160,choices.menuWidth()),Math.min(labels.length*44+4,300),header.commandBounds(command).left);
    }
    void datePicker(){final int generation=pickerGeneration+1;me.ancal.wmdroid.sdk.WmCalendarPicker picker=new me.ancal.wmdroid.sdk.WmCalendarPicker(getContext(),regular,bold,UiLanguage.locale(getContext()),selected.getYear(),selected.getMonthValue(),selected.getDayOfMonth(),LauncherTheme.selection(getContext(),false),(year,month,day)->{if(generation!=pickerGeneration)return;closePicker();go(LocalDate.of(year,month,day));});if(mode==AGENDA||mode==DAY)picker.setOnDateChangeListener((year,month,day)->{if(generation==pickerGeneration)header.setDatePreview(year,month,day);});picker.setFirstDayOfWeek(options.firstDayOfWeek);picker.setWeekNumbersVisible(mode==WEEK&&options.weekNumbers);android.graphics.Point size=picker.naturalSize();presentPicker(picker,size.x+4,size.y+4,0);}
    private void yearEditor(){
        final int generation=pickerGeneration+1;
        me.ancal.wmdroid.sdk.WmYearChooser editor=new me.ancal.wmdroid.sdk.WmYearChooser(getContext(),regular,UiLanguage.locale(getContext()),selected.getYear(),LauncherTheme.selection(getContext(),false),new me.ancal.wmdroid.sdk.WmYearChooser.Listener(){
            public void chosen(int year){if(generation!=pickerGeneration)return;closePicker();chooseYear(year);}
            public void cancel(){if(generation==pickerGeneration)closePicker();}
        });presentPicker(editor,360,260,0);
    }
    boolean backPicker(){if(me.ancal.wmdroid.sdk.WmControls.back(pickerContent))return true;return finishPicker();}
    private boolean finishPicker(){int[] date=pickerContent instanceof me.ancal.wmdroid.sdk.WmCalendarPicker?((me.ancal.wmdroid.sdk.WmCalendarPicker)pickerContent).date():null;boolean closed=closePicker();if(closed&&date!=null){LocalDate chosen=LocalDate.of(date[0],date[1],date[2]);if(!chosen.equals(selected))go(chosen);}return closed;}
    boolean closePicker(){pickerGeneration++;header.clearDatePreview();if(pickerOverlay==null)return false;content.removeView(pickerOverlay);pickerOverlay=null;pickerContent=null;return true;}
    private void presentPicker(View body,int width,int height,int left){
        closePicker();FrameLayout overlay=new FrameLayout(getContext()){@Override protected void onMeasure(int w,int h){super.onMeasure(w,h);if(body instanceof me.ancal.wmdroid.sdk.WmCalendarPicker&&getChildCount()>0){android.graphics.Point size=((me.ancal.wmdroid.sdk.WmCalendarPicker)body).naturalSize();View frame=getChildAt(0);FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)frame.getLayoutParams();lp.width=Math.min(getMeasuredWidth(),size.x+4);lp.height=Math.min(Math.max(0,getMeasuredHeight()-44),size.y+4);lp.leftMargin=Math.max(0,Math.min(getMeasuredWidth()-lp.width,left));frame.measure(MeasureSpec.makeMeasureSpec(lp.width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(lp.height,MeasureSpec.EXACTLY));}}};overlay.setOnClickListener(v->{if(!me.ancal.wmdroid.sdk.WmControls.back(pickerContent))finishPicker();});FrameLayout frame=new FrameLayout(getContext());android.graphics.drawable.GradientDrawable border=new android.graphics.drawable.GradientDrawable();border.setColor(Color.WHITE);border.setStroke(2,Color.BLACK);frame.setBackgroundDrawable(border);frame.setPadding(2,2,2,2);frame.setOnClickListener(v->{});frame.addView(body,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(Math.min(480,width),Math.min(Math.max(44,content.getHeight()-44),height));position.leftMargin=Math.max(0,Math.min(480-position.width,left));position.topMargin=44;overlay.addView(frame,position);pickerOverlay=overlay;pickerContent=body;content.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();refreshLunarCalendar();}
    @Override protected void onDetachedFromWindow(){rememberTimeCursor();CalendarPreferences.saveCursorRows(getContext(),timeCursorRows);closePicker();super.onDetachedFromWindow();}


    private void updateLocale(){
        java.util.Locale locale=UiLanguage.locale(getContext());if(renderedLocale!=null&&!renderedLocale.equals(locale)){
            if(pickerContent instanceof me.ancal.wmdroid.sdk.WmCalendarPicker)((me.ancal.wmdroid.sdk.WmCalendarPicker)pickerContent).setLocale(locale);
            else if(pickerContent instanceof me.ancal.wmdroid.sdk.WmYearChooser)((me.ancal.wmdroid.sdk.WmYearChooser)pickerContent).setLocale(locale);
            else closePicker();
        }renderedLocale=locale;
    }
    private void refreshYearAppearance(){
        updateLocale();header.setLocale(renderedLocale);yearView.setLocale(renderedLocale);yearView.setFirstDayOfWeek(options.firstDayOfWeek);
        yearView.setColors(LauncherTheme.selection(getContext(),false));yearView.setContentDescription(UiLanguage.t(getContext(),"연도 보기"));
        updateYearHeader(yearView);
    }
    private void rememberTimeCursor(){if(timelineView!=null)timeCursorRows=timelineView.getTimeCursorRows();}
    private void reload(){
        rememberTimeCursor();
        updateLocale();
        final int token=++request;final LocalDate day=selected;final int activeMode=mode;final boolean monthly=mode==MONTH;
        header.setLocale(renderedLocale);header.setFirstDayOfWeek(options.firstDayOfWeek);header.setWeekNumbersVisible(options.weekNumbers);header.setMode(mode);header.setDate(selected.getYear(),selected.getMonthValue(),selected.getDayOfMonth());header.setColors(LauncherTheme.selection(getContext(),false));if((mode==AGENDA||mode==DAY)&&pickerContent instanceof me.ancal.wmdroid.sdk.WmCalendarPicker){int[] preview=((me.ancal.wmdroid.sdk.WmCalendarPicker)pickerContent).date();header.setDatePreview(preview[0],preview[1],preview[2]);}
        // Keep queued keyboard input on the new month while its provider query is pending.
        if(monthly&&monthGrid!=null&&monthAnchor==null)monthGrid.setMonth(day.getYear(),day.getMonthValue());
        surface.cancel();if(mode!=MONTH||monthGrid==null){surface.removeAllViews();yearView=null;monthGrid=null;timelineView=null;agendaView=null;if(mode==YEAR){render();launchOccurrence=null;savedEventId=-1;return;}TextView loading=text("",false);surface.addView(loading);}
        LocalDate first=monthly?monthWindowFirst(day):activeMode==WEEK?weekFirstDay(day):day,last=monthly?first.plusDays(42):first.plusDays(activeMode==WEEK?options.weekDays:1);
        worker.execute(()->{List<CalendarStore.Occurrence> loaded=null;String error=null;try{loaded=store.between(first,last);}catch(SecurityException denied){error="일정 읽기 권한이 필요합니다.";}catch(RuntimeException unavailable){error="일정을 읽을 수 없습니다.";}
            final List<CalendarStore.Occurrence> result=loaded;final String failure=error;ui.post(()->{if(token!=request)return;if(failure!=null){surface.removeAllViews();monthGrid=null;savedEventId=-1;if(populateWeekSelection){populateWeekSelection=false;launchOccurrence=null;}pendingWeekNavigation=null;pendingWeekFirst=null;TextView message=text(UiLanguage.t(getContext(),failure),false);message.setPadding(8,8,8,8);surface.addView(message);surface.replay(!renderedOnce);renderedOnce=true;return;}occurrences=result;render();launchOccurrence=null;savedEventId=-1;});});
    }
    private void render(){
        boolean restoreMonthFocus=mode==MONTH&&monthGrid!=null&&monthGrid.hasFocus()&&pickerOverlay==null;
        surface.replay(!renderedOnce);renderedOnce=true;
        surface.removeAllViews();LinearLayout body=new LinearLayout(getContext());body.setOrientation(VERTICAL);surface.addView(body,new FrameLayout.LayoutParams(-1,-1));
        if(mode==YEAR){renderYear(body);return;}
        if(mode==DAY||mode==WEEK){renderTimeline(body);return;}
        if(mode==MONTH){
            refreshLunarCalendar();
            me.ancal.wmdroid.sdk.WmMonthView grid=new me.ancal.wmdroid.sdk.WmMonthView(getContext(),regular,bold,UiLanguage.locale(getContext()));
            monthGrid=grid;grid.setFirstDayOfWeek(options.firstDayOfWeek);grid.setWeekNumbersVisible(options.weekNumbers);
            grid.setMonth(selected.getYear(),selected.getMonthValue());grid.setColors(LauncherTheme.selection(getContext(),false),me.ancal.wmdroid.sdk.WmPalette.face(LauncherTheme.selection(getContext(),false)));
            if(monthAnchor!=null)grid.setWindowAnchor(monthAnchor.getYear(),monthAnchor.getMonthValue(),monthAnchor.getDayOfMonth());
            applyLunarCalendar();
            LocalDate first=monthWindowFirst(selected);
            for(int index=0;index<42;index++){
                LocalDate day=first.plusDays(index);int flags=0;
                long begin=day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),end=day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                for(CalendarStore.Occurrence item:occurrences)if(item.on(day)){
                    // Android allDay maps to the native outline role; the ROM record field identity remains unproven.
                    if(item.allDay){flags|=0x1000000;continue;}
                    // Native CALSTORE 028EB4B8 stores the24 occupied hour bits.
                    // End is exclusive: an event ending12:00 does not mark PM.
                    long clippedStart=Math.max(begin,item.begin),clippedEnd=Math.min(end,item.end);
                    if(clippedEnd<=clippedStart)continue;
                    int firstHour=java.time.Instant.ofEpochMilli(clippedStart).atZone(ZoneId.systemDefault()).getHour();
                    int lastHour=java.time.Instant.ofEpochMilli(clippedEnd-1).atZone(ZoneId.systemDefault()).getHour();
                    for(int hour=firstHour;hour<=lastHour;hour++)flags|=1<<hour;
                }
                if(flags!=0)grid.setDayFlags(day.getYear(),day.getMonthValue(),day.getDayOfMonth(),flags);
            }
            grid.setContentDescription(UiLanguage.t(getContext(),"월 보기"));
            grid.setOnCommandListener(this::headerCommand);
            grid.setOnRangeChangedListener((year,month)->{
                if(monthGrid!=grid||mode!=MONTH)return;
                int[] anchor=grid.getWindowAnchor();monthAnchor=LocalDate.of(anchor[0],anchor[1],anchor[2]);
                // Native owner adjusts year before month; each step can clamp the day.
                selected=selected.withYear(year).withMonth(month);launchOccurrence=null;savedEventId=-1;closePicker();reload();
            });
            grid.setOnDayClickListener((year,month,day)->{if(year<me.ancal.wmdroid.sdk.WmYearChooser.MIN_YEAR||year>me.ancal.wmdroid.sdk.WmYearChooser.MAX_YEAR)return;selected=LocalDate.of(year,month,day);setView(detailMode);});
            grid.setOnWeekClickListener((year,month,day)->{if(year<me.ancal.wmdroid.sdk.WmYearChooser.MIN_YEAR||year>me.ancal.wmdroid.sdk.WmYearChooser.MAX_YEAR)return;selected=LocalDate.of(year,month,day);setView(WEEK);});
            body.addView(grid,new LayoutParams(-1,-1));if(restoreMonthFocus){grid.requestFocus();restoreMonthFocus=false;}savedEventId=-1;return;
        }
        renderAgenda(body);
    }

    private void renderYear(LinearLayout body){
        final me.ancal.wmdroid.sdk.WmYearView grid=new me.ancal.wmdroid.sdk.WmYearView(getContext(),regular,UiLanguage.locale(getContext()));
        grid.setFirstDayOfWeek(options.firstDayOfWeek);
        yearView=grid;grid.setDate(selected.getYear(),selected.getMonthValue(),selected.getDayOfMonth());grid.setColors(LauncherTheme.selection(getContext(),false));
        if(yearAtJanuary){grid.showYearStart();yearAtJanuary=false;}
        grid.setOnRangeChangedListener((year,month,day)->{if(yearView!=grid||mode!=YEAR)return;selected=LocalDate.of(year,month,day);updateYearHeader(grid);});
        grid.setOnDayClickListener((year,month,day)->{if(yearView!=grid||mode!=YEAR)return;selected=LocalDate.of(year,month,day);setView(detailMode);});
        grid.setContentDescription(UiLanguage.t(getContext(),"연도 보기"));body.addView(grid,new LayoutParams(-1,-1));updateYearHeader(grid);
    }
    private void updateYearHeader(me.ancal.wmdroid.sdk.WmYearView grid){
        header.setDate(selected.getYear(),selected.getMonthValue(),selected.getDayOfMonth());int offset=grid.getMonthOffset(),count=grid.getVisibleMonthCount(),year=selected.getYear();
        // caldll02916904: native cross-year flags0x302/0x502, not a fixed twelve-month page.
        header.setYearRange(offset<0&&offset+count>=0?year-1:year,offset>0&&offset+count>12?year+1:year);
    }

    private void renderAgenda(LinearLayout body){
        me.ancal.wmdroid.sdk.WmTimeFormat.Agenda clock=new me.ancal.wmdroid.sdk.WmTimeFormat.Agenda(UiLanguage.locale(getContext()),android.text.format.DateFormat.is24HourFormat(getContext()),UiLanguage.t(getContext(),"여러 날"));
        me.ancal.wmdroid.sdk.WmDateTimeField.Value visibleDay=new me.ancal.wmdroid.sdk.WmDateTimeField.Value(selected.getYear(),selected.getMonthValue(),selected.getDayOfMonth(),0,0);
        me.ancal.wmdroid.sdk.WmAgendaView agenda=new me.ancal.wmdroid.sdk.WmAgendaView(getContext(),regular,UiLanguage.locale(getContext()));
        agendaView=agenda;
        agenda.setDate(selected.getYear(),selected.getMonthValue(),selected.getDayOfMonth());agenda.setColors(LauncherTheme.selection(getContext(),false));
        agenda.setDisplayIconMask(CalendarPreferences.readAppointments(getContext()).displayMask);
        List<me.ancal.wmdroid.sdk.WmAgendaView.Event> events=new ArrayList<>();
        ZoneId zone=ZoneId.systemDefault();long midnight=selected.atStartOfDay(zone).toInstant().toEpochMilli(),next=selected.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),now=System.currentTimeMillis();
        for(CalendarStore.Occurrence item:occurrences)if(item.on(selected)){
            int begin=0,end=1440;String time="";
            if(!item.allDay){
                long start=Math.max(midnight,item.begin),finish=Math.min(next,item.end);if(finish<start)continue;
                java.time.ZonedDateTime a=java.time.Instant.ofEpochMilli(start).atZone(zone),b=java.time.Instant.ofEpochMilli(finish).atZone(zone);
                begin=a.getHour()*60+a.getMinute();end=finish==next?1440:b.getHour()*60+b.getMinute()+(b.getSecond()>0||b.getNano()>0?1:0);end=Math.max(begin,Math.min(1440,end));
                // Strip occupancy stays clipped; native307 captions use the original civil endpoints.
                time=clock.format(visibleDay,agendaCivil(item.begin,zone),agendaCivil(item.end,zone));
            }
            events.add(new me.ancal.wmdroid.sdk.WmAgendaView.Event(begin,end,item.allDay,item.title,time,item.location,0,item,item.displaySymbols(now),item.recurrenceException,item.begin,item.end,item.busyStatus));
        }
        agenda.setEvents(events);agenda.setContentDescription(UiLanguage.t(getContext(),"일정"));
        agenda.setOnEventListener(new me.ancal.wmdroid.sdk.WmAgendaView.OnEventListener(){public void onSelected(me.ancal.wmdroid.sdk.WmAgendaView.Event event){}public void onOpen(me.ancal.wmdroid.sdk.WmAgendaView.Event event){open.show((CalendarStore.Occurrence)event.tag);}});
        body.addView(agenda,new LayoutParams(-1,-1));
        if(launchOccurrence!=null||savedEventId>=0)for(me.ancal.wmdroid.sdk.WmAgendaView.Event event:events)if(launchMatches((CalendarStore.Occurrence)event.tag)){agenda.selectEvent(event);break;}
        savedEventId=-1;
    }
    private static me.ancal.wmdroid.sdk.WmDateTimeField.Value agendaCivil(long millis,ZoneId zone){java.time.ZonedDateTime value=java.time.Instant.ofEpochMilli(millis).atZone(zone);return new me.ancal.wmdroid.sdk.WmDateTimeField.Value(value.getYear(),value.getMonthValue(),value.getDayOfMonth(),value.getHour(),value.getMinute());}

    /** Only this adapter touches CalendarStore. No PIM records cross the SDK bridge. */
    private void renderTimeline(LinearLayout body){
        me.ancal.wmdroid.sdk.WmTimeFormat.Agenda clock=new me.ancal.wmdroid.sdk.WmTimeFormat.Agenda(UiLanguage.locale(getContext()),android.text.format.DateFormat.is24HourFormat(getContext()),UiLanguage.t(getContext(),"여러 날"));
        int days=mode==WEEK?options.weekDays:1;
        LocalDate first=mode==WEEK?weekFirstDay(selected):selected;
        me.ancal.wmdroid.sdk.WmCalendarView timeline=new me.ancal.wmdroid.sdk.WmCalendarView(getContext(),regular,UiLanguage.locale(getContext()));
        timeline.setRange(first.getYear(),first.getMonthValue(),first.getDayOfMonth(),days);
        timeline.setInterval(options.halfHour?30:60);timelineView=timeline;
        timeline.setDisplayIconMask(CalendarPreferences.readAppointments(getContext()).displayMask);
        timeline.setColors(LauncherTheme.selection(getContext(),false),me.ancal.wmdroid.sdk.WmPalette.face(LauncherTheme.selection(getContext(),false)),Color.WHITE);
        List<me.ancal.wmdroid.sdk.WmCalendarView.Event> events=new ArrayList<>();
        for(int column=0;column<days;column++){
            LocalDate day=first.plusDays(column);
            me.ancal.wmdroid.sdk.WmDateTimeField.Value visibleDay=new me.ancal.wmdroid.sdk.WmDateTimeField.Value(day.getYear(),day.getMonthValue(),day.getDayOfMonth(),0,0);
            long midnight=day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),next=day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            for(CalendarStore.Occurrence item:occurrences)if(item.on(day)){
                int begin=0,end=1440;
                if(!item.allDay){
                    long clippedStart=Math.max(midnight,item.begin),clippedEnd=Math.min(next,item.end);
                    if(clippedEnd<clippedStart)continue;
                    java.time.ZonedDateTime start=java.time.Instant.ofEpochMilli(clippedStart).atZone(ZoneId.systemDefault());
                    java.time.ZonedDateTime finish=java.time.Instant.ofEpochMilli(clippedEnd).atZone(ZoneId.systemDefault());
                    begin=start.getHour()*60+start.getMinute();
                    end=clippedEnd==next?1440:finish.getHour()*60+finish.getMinute()+(finish.getSecond()>0||finish.getNano()>0?1:0);
                    end=Math.max(begin,Math.min(1440,end));
                }
                String when=item.allDay?UiLanguage.t(getContext(),"종일"):clock.format(visibleDay,agendaCivil(item.begin,ZoneId.systemDefault()),agendaCivil(item.end,ZoneId.systemDefault()));
                events.add(new me.ancal.wmdroid.sdk.WmCalendarView.Event(column,begin,end,item.allDay,item.title,me.ancal.wmdroid.sdk.WmTimeFormat.calendarDetails(when,item.location),item,item.displaySymbols(System.currentTimeMillis()),item.recurrenceException,item.busyStatus,"event:"+item.seriesId,item.begin,item.end));
            }
        }
        timeline.setEvents(events);
        if(timeCursorRows!=null)timeline.setTimeCursorRows(timeCursorRows[0],timeCursorRows[1]);
        timeline.setContentDescription(UiLanguage.t(getContext(),mode==WEEK?"주 보기":"일 보기"));
        timeline.setOnTimeListener((column,start,end)->{if(timelineView!=timeline||(mode!=DAY&&mode!=WEEK))return;int[] date=timeline.dateAt(column);selected=LocalDate.of(date[0],date[1],date[2]);});
        timeline.setOnEventListener(new me.ancal.wmdroid.sdk.WmCalendarView.OnEventListener(){
            public void onSelected(me.ancal.wmdroid.sdk.WmCalendarView.Event event){if(timelineView!=timeline||(mode!=DAY&&mode!=WEEK))return;int[] date=timeline.dateAt(event.day);selected=LocalDate.of(date[0],date[1],date[2]);}
            public void onOpen(me.ancal.wmdroid.sdk.WmCalendarView.Event event){if(timelineView!=timeline||(mode!=DAY&&mode!=WEEK))return;open.show((CalendarStore.Occurrence)event.tag);}
        });
        timeline.setOnNavigateListener(direction->{
            if(timelineView!=timeline)return;
            if(mode==DAY){move(direction);return;}
            if(mode!=WEEK)return;
            int[] date=timeline.dateAt(0);LocalDate target=LocalDate.of(date[0],date[1],date[2]).plusDays(direction*7);
            if(target.getYear()<me.ancal.wmdroid.sdk.WmYearChooser.MIN_YEAR||target.getYear()>me.ancal.wmdroid.sdk.WmYearChooser.MAX_YEAR)return;
            pendingWeekNavigation=timeline.captureWeekNavigation(direction);pendingWeekFirst=target;restoreTimelineMinute=timeline.getScrollMinute();
            launchOccurrence=null;savedEventId=-1;populateWeekSelection=false;selected=target;closePicker();reload();
        });
        timeline.setOnDateListener((year,month,day)->{if(timelineView!=timeline||mode!=WEEK)return;selected=LocalDate.of(year,month,day);setView(DAY);});
        final float minute=restoreTimelineMinute==null?8*60:restoreTimelineMinute;restoreTimelineMinute=null;
        body.addView(timeline,new LayoutParams(-1,-1));timeline.scrollToMinute(minute);
        if(pendingWeekNavigation!=null){
            me.ancal.wmdroid.sdk.WmCalendarView.WeekNavigation navigation=pendingWeekNavigation;LocalDate target=pendingWeekFirst;pendingWeekNavigation=null;pendingWeekFirst=null;
            if(mode==WEEK&&first.equals(target))timeline.restoreWeekNavigation(navigation);
        }
        me.ancal.wmdroid.sdk.WmCalendarView.Event requested=null;
        if(launchOccurrence!=null||savedEventId>=0)for(me.ancal.wmdroid.sdk.WmCalendarView.Event event:events)if(launchMatches((CalendarStore.Occurrence)event.tag)){
            if(requested==null)requested=event;
            if(first.plusDays(event.day).equals(selected)){requested=event;break;}
        }
        if(requested!=null){timeline.selectEvent(requested);selected=first.plusDays(requested.day);}
        if(mode==WEEK&&(initialWeekSelection||populateWeekSelection)){
            boolean initial=initialWeekSelection;initialWeekSelection=false;populateWeekSelection=false;launchOccurrence=null;savedEventId=-1;
            if(requested==null){int column=(int)java.time.temporal.ChronoUnit.DAYS.between(first,selected);column=Math.max(0,Math.min(days-1,column));if(initial)timeline.selectInitialWeek(column,System.currentTimeMillis());else timeline.selectPopulatedWeek(column);}
        }
        savedEventId=-1;
    }
    private boolean launchMatches(CalendarStore.Occurrence item){return savedEventId>=0?item.id==savedEventId:launchOccurrence!=null&&item.id==launchOccurrence.id&&item.begin==launchOccurrence.begin;}
    private LocalDate weekFirstDay(LocalDate day){
        int weekday=options.weekDays==7?(options.firstDayOfWeek==java.util.Calendar.MONDAY?1:7):1;
        return day.minusDays((day.getDayOfWeek().getValue()-weekday+7)%7);
    }
    private LocalDate regionalWeekFirstDay(LocalDate day){int weekday=options.firstDayOfWeek==java.util.Calendar.MONDAY?1:7;return day.minusDays((day.getDayOfWeek().getValue()-weekday+7)%7);}
    private LocalDate monthWindowFirst(LocalDate day){return monthAnchor==null?monthFirstDay(day):regionalWeekFirstDay(monthAnchor);}
    private LocalDate monthFirstDay(LocalDate day){
        LocalDate first=day.withDayOfMonth(1);int weekday=options.firstDayOfWeek==java.util.Calendar.MONDAY?1:7;
        return first.minusDays((first.getDayOfWeek().getValue()-weekday+7)%7);
    }
}
