package me.ancal.wmdroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import me.ancal.wmdroid.sdk.WmControls;
import me.ancal.wmdroid.sdk.WmChoice;
import me.ancal.wmdroid.sdk.WmListSettingsPane;
import me.ancal.wmdroid.sdk.WmStrings;
import me.ancal.wmdroid.sdk.WmPropertySheet;
import me.ancal.wmdroid.sdk.WmRadioMenu;
import me.ancal.wmdroid.sdk.WmScrollView;

/** shellres 14421: item list 901; Move Up/Down 902/903; Options 904.
 * Control relationships and captions follow the ROM. Dimensions retain the
 * provisional VGA adaptation; native DLU conversion still needs comparison.
 */
final class TodaySettingsPage extends LinearLayout implements WmControls.BackHandler,WmControls.RestyleListener,WmControls.LanguageListener {
    interface Options { boolean available(String id); void open(String id); }
    public void onWmRestyle(Typeface regular,Typeface bold,int selection,int tabColor){closePopup();this.font=regular;}
    public void onWmLanguageChanged(String language){
        closePopup();
        sheet.setPageTitle(0,WmStrings.text(getContext(),language,"모양"));
        sheet.setPageTitle(1,WmStrings.text(getContext(),language,"항목"));
        for(String id:order)if(isBuiltIn(id))names.put(id,WmStrings.text(getContext(),language,id));
        refreshRows(language);
        refreshSessionLabels(language);
    }
    private static boolean isBuiltIn(String id){return "날짜".equals(id)||"무선".equals(id)||"소유자 정보".equals(id)||"받은 편지함".equals(id)||"작업".equals(id)||"일정".equals(id)||"장치 잠금".equals(id)||"장치 잠금 해제됨".equals(id);}

    private final SharedPreferences prefs;
    private Typeface font;
    private final Options options;
    private final List<String> order;
    private final List<String> available=new ArrayList<>();
    private final Map<String,String> names=new HashMap<>();
    private final Map<String,Boolean> enabled=new HashMap<>();
    private final List<TodayModules.Module> providers=new ArrayList<>();
    private final FrameLayout content;
    private final FrameLayout contentFrame;
    private final WmPropertySheet sheet;
    private String selected;
    private boolean rose;
    private LinearLayout rows;
    private ClassicScrollView itemScroll;
    private TextView up,down,configure;
    private CheckBox sessionEnabled;
    private WmChoice sessionHours;
    private FrameLayout session;
    private WmListSettingsPane pane;
    private View popup;
    private int popupGeneration;
    private boolean syncing;
    private volatile boolean listening;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable sync=()->{if(listening)syncPreferences();};
    private final SharedPreferences.OnSharedPreferenceChangeListener changes=(p,key)->{
        if(!listening||key!=null&&!("today_order".equals(key)||TodaySession.KEY.equals(key)||"rose".equals(key)||key.startsWith("item_")||key.startsWith("module_")))return;
        if(Looper.myLooper()==main.getLooper())syncPreferences();else{main.removeCallbacks(sync);main.post(sync);}
    };

    TodaySettingsPage(Context c,SharedPreferences prefs,Typeface font,List<String> order,
            String[] labels,int initialTab,Options options,Runnable theme,Runnable clock) {
        super(c);this.prefs=prefs;this.font=font;this.options=options;this.order=new ArrayList<>(order);available.addAll(order);
        for(int i=0;i<order.size();i++){String id=order.get(i);names.put(id,labels[i]);enabled.put(id,prefs.getBoolean("item_"+id,true));}
        rose=prefs.getBoolean("rose",false);setOrientation(VERTICAL);setBackgroundColor(Color.WHITE);
        content=new FrameLayout(c);
        contentFrame=new FrameLayout(c);
        sheet=new WmPropertySheet(c,font,LauncherTheme.color(c,0xffc9e7e9,rose));
        sheet.addPage(UiLanguage.t(c,"모양"),appearance(theme,clock));
        buildItems();sheet.addPage(UiLanguage.t(c,"항목"),content);
        contentFrame.addView(sheet,new FrameLayout.LayoutParams(-1,-1));addView(contentFrame,new LayoutParams(-1,-1));sheet.setOnPageChangedListener(index->closePopup());sheet.selectPage(initialTab);
    }
    private TextView text(String value){TextView view=ClassicUi.text(getContext(),font,UiLanguage.t(getContext(),value));WmControls.bindText(view,UiLanguage.language(getContext()),value);return view;}
    private TextView button(String value,Runnable action){
        TextView view=text(value);view.setGravity(Gravity.CENTER);view.setSoundEffectsEnabled(false);
        ClassicUi.button(view,false);
        view.setOnClickListener(v->action.run());return view;
    }
    private ClassicScrollView appearance(Runnable theme,Runnable clock){
        LinearLayout choices=new LinearLayout(getContext());choices.setOrientation(VERTICAL);choices.setPadding(16,10,16,0);
        choices.addView(text("배경 화면 설정:"),new LayoutParams(-1,40));
        TextView background=text("배경: Windows 기본값");ClassicUi.entry(background,false);background.setSelected(true);
        background.setOnClickListener(v->{rose=false;prefs.edit().putBoolean("rose",false).apply();});choices.addView(background,new LayoutParams(-1,44));
        choices.addView(button("테마 색",theme),new LayoutParams(-1,44));
        choices.addView(button("프로그램 시계 표시",clock),new LayoutParams(-1,44));
        return new ClassicScrollView(getContext(),choices);
    }
    private void buildItems(){
        TextView note=text("선택한 항목이 [오늘] 화면에 나타납니다.");
        FrameLayout.LayoutParams noteBounds=new FrameLayout.LayoutParams(-1,38);noteBounds.leftMargin=14;noteBounds.rightMargin=18;noteBounds.topMargin=10;content.addView(note,noteBounds);
        rows=new LinearLayout(getContext());rows.setOrientation(VERTICAL);
        itemScroll=new ClassicScrollView(getContext(),rows);
        up=button("위로 이동",()->move(-1));down=button("아래로 이동",()->move(1));configure=button("옵션...",()->{if(selected!=null)options.open(selected);});
        pane=new WmListSettingsPane(getContext(),itemScroll,up,down,configure);pane.setPadding(16,0,16,0);
        FrameLayout.LayoutParams paneBounds=new FrameLayout.LayoutParams(-1,-1);paneBounds.topMargin=48;paneBounds.bottomMargin=112;content.addView(pane,paneBounds);
        session=new FrameLayout(getContext());session.setPadding(16,0,16,0);
        sessionEnabled=new CheckBox(getContext());ClassicUi.check(sessionEnabled,font);
        sessionEnabled.setGravity(Gravity.TOP|Gravity.LEFT);
        WmControls.bindText(sessionEnabled,UiLanguage.language(getContext()),"다음 시간 동안 장치를 사용하지 않으면 [오늘] 화면 표시:");
        session.addView(sessionEnabled,new FrameLayout.LayoutParams(-1,-2));
        sessionHours=new WmChoice(getContext(),font,sessionLabels(UiLanguage.language(getContext())),3,LauncherTheme.selection(getContext(),rose));
        session.addView(sessionHours,new FrameLayout.LayoutParams(144,40,Gravity.RIGHT|Gravity.TOP));
        FrameLayout.LayoutParams sessionBounds=new FrameLayout.LayoutParams(-1,96,Gravity.BOTTOM);sessionBounds.bottomMargin=16;content.addView(session,sessionBounds);
        layoutSession(UiLanguage.language(getContext()));
        sessionEnabled.setOnCheckedChangeListener((button,on)->{if(syncing)return;closePopup();sessionHours.setEnabled(on);if(!on)sessionHours.setSelectedIndex(3);prefs.edit().putInt(TodaySession.KEY,on?sessionHours.selectedIndex()+1:0).apply();});
        sessionHours.setOnChoiceListener(index->{if(!syncing&&sessionEnabled.isChecked())prefs.edit().putInt(TodaySession.KEY,index+1).apply();});
        sessionHours.setOnOpenListener(this::openSessionHours);
        syncSession();render();
    }
    private String[] sessionLabels(String language){
        String[] labels=new String[12];String pattern=WmStrings.text(getContext(),language,"%d시간");
        for(int i=0;i<labels.length;i++)labels[i]=String.format(java.util.Locale.ROOT,pattern,i+1);return labels;
    }
    private void refreshSessionLabels(String language){
        if(sessionEnabled!=null)WmControls.bindText(sessionEnabled,language,"다음 시간 동안 장치를 사용하지 않으면 [오늘] 화면 표시:");
        if(sessionHours!=null)sessionHours.setChoices(sessionLabels(language),sessionHours.selectedIndex());
        layoutSession(language);
    }
    private void layoutSession(String language){
        if(session==null)return;
        // English WM6.1.4 and Korean WM6 captures pin these owner offsets; shared font/row metrics remain provisional.
        boolean english="en".equals(language);
        FrameLayout.LayoutParams footer=(FrameLayout.LayoutParams)session.getLayoutParams();
        footer.height=english?42:143;session.setLayoutParams(footer);
        FrameLayout.LayoutParams list=(FrameLayout.LayoutParams)pane.getLayoutParams();
        list.bottomMargin=english?64:165;pane.setLayoutParams(list);
        FrameLayout.LayoutParams check=(FrameLayout.LayoutParams)sessionEnabled.getLayoutParams();
        check.rightMargin=english?248:144;check.topMargin=english?2:4;sessionEnabled.setLayoutParams(check);
        FrameLayout.LayoutParams choice=(FrameLayout.LayoutParams)sessionHours.getLayoutParams();
        choice.width=english?94:144;choice.height=english?34:40;choice.rightMargin=english?152:0;sessionHours.setLayoutParams(choice);
    }
    private void syncSession(){
        int hours=TodaySession.hours(prefs);if(hours==0)closePopup();
        syncing=true;try{sessionEnabled.setChecked(hours!=0);sessionHours.setEnabled(hours!=0);sessionHours.setSelectedIndex(hours==0?3:hours-1);}finally{syncing=false;}
    }
    private void openSessionHours(WmChoice source){
        if(!sessionHours.isEnabled()||!sessionHours.isShown()||contentFrame.getWidth()<=0||contentFrame.getHeight()<=0)return;
        closePopup();final int generation=popupGeneration;
        WmRadioMenu menu=new WmRadioMenu(getContext(),font,sessionHours.choices(),sessionHours.selectedIndex(),UiLanguage.t(getContext()," 선택됨"),LauncherTheme.selection(getContext(),rose),index->{if(generation!=popupGeneration)return;closePopup();sessionHours.choose(index);});
        menu.enableChoiceKeyboard(sessionHours.selectedIndex(),index->{if(generation==popupGeneration)sessionHours.choose(index);},()->{if(generation==popupGeneration){closePopup();if(sessionHours.isShown())sessionHours.requestFocus();}});
        menu.enableChoiceCharacterSearch(sessionHours.displaySortLocale(),index->{if(generation==popupGeneration&&sessionHours.isEnabled())sessionHours.chooseCharacterDisplayIndex(index);});
        Rect anchor=new Rect(0,0,sessionHours.getWidth(),sessionHours.getHeight());contentFrame.offsetDescendantRectToMyCoords(sessionHours,anchor);
        int width=Math.min(contentFrame.getWidth(),Math.max(anchor.width(),menu.menuWidth())),desired=menu.choiceDropdownHeight(222);
        int below=Math.max(0,contentFrame.getHeight()-anchor.bottom),above=Math.max(0,anchor.top);boolean down=below>=desired||below>=above;
        int height=Math.min(desired,Math.max(1,down?below:above)),top=down?anchor.bottom:Math.max(0,anchor.top-height);
        FrameLayout overlay=new FrameLayout(getContext());overlay.setOnClickListener(view->closePopup());WmScrollView scroll=new WmScrollView(getContext(),menu);
        FrameLayout.LayoutParams fit=new FrameLayout.LayoutParams(width,height);fit.leftMargin=Math.max(0,Math.min(contentFrame.getWidth()-width,anchor.left));fit.topMargin=Math.max(0,Math.min(contentFrame.getHeight()-height,top));overlay.addView(scroll,fit);
        popup=overlay;contentFrame.addView(overlay,new FrameLayout.LayoutParams(-1,-1));WmChoice.bindDropdown(sessionHours,overlay);menu.requestFocus();
    }
    private boolean closePopup(){popupGeneration++;if(popup==null)return false;contentFrame.removeView(popup);popup=null;return true;}
    @Override public boolean back(){return closePopup();}
    private void render(){
        Map<String,View> retained=new HashMap<>();
        for(int i=0;i<rows.getChildCount();i++){View row=rows.getChildAt(i);retained.put((String)row.getTag(),row);}
        rows.removeAllViews();
        for(String id:order){
            View existing=retained.get(id);
            if(existing!=null){rows.addView(existing);continue;}
            LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);ClassicUi.entryBackground(row,rose);
            row.setSelected(id.equals(selected));row.setOnClickListener(v->choose(id));
            CheckBox check=new CheckBox(getContext());WmControls.listCheck(check,font);check.setContentDescription(names.get(id)+UiLanguage.t(getContext()," 표시"));check.setChecked(enabled.get(id));
            check.setOnCheckedChangeListener((v,on)->{if(syncing||row.getParent()!=rows)return;enabled.put(id,on);prefs.edit().putBoolean("item_"+id,on).apply();choose(id);});row.addView(check,new LayoutParams(32,40));
            TextView label=ClassicUi.text(getContext(),font,names.get(id));label.setDuplicateParentStateEnabled(true);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setTextColor(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_selected},{}},new int[]{Color.WHITE,Color.BLACK}));
            row.addView(label,new LayoutParams(0,40,1));rows.addView(row,new LayoutParams(-1,40));row.setTag(id);
        }
        for(TodayModules.Module provider:providers){
            String id="provider:"+provider.authority;View existing=retained.get(id);
            if(existing!=null){((TextView)((ViewGroup)existing).getChildAt(1)).setText(provider.label);rows.addView(existing);continue;}
            LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);ClassicUi.entryBackground(row,rose);row.setTag(id);row.setSelected(id.equals(selected));
            CheckBox check=new CheckBox(getContext());WmControls.listCheck(check,font);check.setChecked(prefs.getBoolean("module_"+provider.authority,false));check.setContentDescription(provider.label+UiLanguage.t(getContext()," 표시"));
            check.setOnCheckedChangeListener((button,on)->{if(syncing||row.getParent()!=rows)return;prefs.edit().putBoolean("module_"+provider.authority,on).apply();choose(id);});row.addView(check,new LayoutParams(32,40));
            TextView label=ClassicUi.text(getContext(),font,provider.label);label.setDuplicateParentStateEnabled(true);label.setTextColor(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_selected},{}},new int[]{Color.WHITE,Color.BLACK}));label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(label,new LayoutParams(0,40,1));row.setOnClickListener(v->choose(id));rows.addView(row,new LayoutParams(-1,40));
        }
        refreshRows();updateButtons();
    }
    void updateProviders(List<TodayModules.Module> values){
        boolean same=providers.size()==values.size();
        for(int i=0;same&&i<values.size();i++)same=providers.get(i).authority.equals(values.get(i).authority)&&providers.get(i).label.equals(values.get(i).label);
        if(same)return;
        int position=itemScroll.getRequestedScrollPosition();providers.clear();providers.addAll(values);
        if(selected!=null&&selected.startsWith("provider:")){
            boolean present=false;for(TodayModules.Module provider:providers)if(selected.equals("provider:"+provider.authority)){present=true;break;}
            if(!present)selected=null;
        }
        render();itemScroll.scrollToPosition(position);
    }
    private void choose(String id){selected=id;for(int i=0;i<rows.getChildCount();i++)rows.getChildAt(i).setSelected(id.equals(rows.getChildAt(i).getTag()));updateButtons();}
    private void updateButtons(){int index=order.indexOf(selected);up.setEnabled(index>1);down.setEnabled(index>0&&index<order.size()-1);configure.setEnabled(selected!=null&&options.available(selected));}
    private void move(int delta){int from=order.indexOf(selected),to=from+delta;if(from<=0||to<1||to>=order.size())return;Collections.swap(order,from,to);prefs.edit().putString("today_order",new JSONArray(order).toString()).apply();render();itemScroll.post(()->itemScroll.scrollToPosition(Math.max(0,(to-2)*40)));}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();listening=true;prefs.registerOnSharedPreferenceChangeListener(changes);syncPreferences();}
    @Override protected void onDetachedFromWindow(){closePopup();listening=false;prefs.unregisterOnSharedPreferenceChangeListener(changes);main.removeCallbacks(sync);super.onDetachedFromWindow();}
    void updateItems(List<String> availableOrder,String[] labels){
        if(availableOrder==null||labels==null||availableOrder.size()!=labels.length)throw new IllegalArgumentException("Invalid Today items");
        Map<String,String> nextNames=new HashMap<>();
        for(int i=0;i<availableOrder.size();i++){String id=availableOrder.get(i);if(id==null||id.isEmpty()||labels[i]==null||nextNames.put(id,labels[i])!=null)throw new IllegalArgumentException("Invalid Today item");}
        available.clear();available.addAll(availableOrder);names.clear();names.putAll(nextNames);syncPreferences();
    }
    private void syncPreferences(){
        List<String> next=new ArrayList<>();String fixed=available.contains("날짜")?"날짜":available.isEmpty()?null:available.get(0);if(fixed!=null)next.add(fixed);
        try{JSONArray saved=new JSONArray(prefs.getString("today_order","[]"));for(int i=0;i<saved.length();i++){Object value=saved.opt(i);if(value instanceof String&&names.containsKey(value)&&!next.contains(value))next.add((String)value);}}
        catch(JSONException|ClassCastException malformed){/* Keep every available item when persisted order is malformed. */}
        for(String id:order)if(names.containsKey(id)&&!next.contains(id))next.add(id);
        for(String id:available)if(!next.contains(id))next.add(id);
        enabled.clear();for(String id:next)enabled.put(id,prefs.getBoolean("item_"+id,true));rose=prefs.getBoolean("rose",false);
        if(selected!=null&&!names.containsKey(selected)){
            boolean present=false;for(TodayModules.Module provider:providers)if(selected.equals("provider:"+provider.authority)){present=true;break;}
            if(!present)selected=null;
        }
        if(!order.equals(next)){int position=itemScroll.getRequestedScrollPosition();order.clear();order.addAll(next);render();itemScroll.scrollToPosition(position);}
        else{refreshRows();updateButtons();}
        syncSession();
    }
    private void refreshRows(){refreshRows(UiLanguage.language(getContext()));}
    private void refreshRows(String language){
        if(rows==null)return;syncing=true;
        try{for(int i=0;i<rows.getChildCount();i++){
            LinearLayout row=(LinearLayout)rows.getChildAt(i);String id=(String)row.getTag(),name=names.get(id);row.setSelected(id.equals(selected));CheckBox check=(CheckBox)row.getChildAt(0);TextView label=(TextView)row.getChildAt(1);
            if(id.startsWith("provider:")){check.setChecked(prefs.getBoolean("module_"+id.substring(9),false));check.setContentDescription(label.getText()+WmStrings.text(getContext(),language," 표시"));continue;}
            check.setChecked(enabled.get(id));check.setContentDescription(name+WmStrings.text(getContext(),language," 표시"));if(!TextUtils.equals(label.getText(),name))label.setText(name);
        }}finally{syncing=false;}
    }
}
