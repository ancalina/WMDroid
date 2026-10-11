package me.ancal.wmdroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import me.ancal.wmdroid.sdk.*;

/** Read-only Today and applet samples built with the same shared controls.
 * Each sample keeps logical VGA geometry; the complete surface is scaled once.
 */
final class ThemePreview extends ViewGroup implements WmControls.RestyleListener,WmControls.LanguageListener {
    private static final int HOME_HEIGHT=232,APPLET_HEIGHT=264,GAP=16,HEIGHT=HOME_HEIGHT+GAP+APPLET_HEIGHT;
    private final SharedPreferences prefs;
    private Typeface font;
    private final FrameLayout surface;
    private float scale=1;

    ThemePreview(Context context,SharedPreferences prefs,Typeface font){
        super(context);this.prefs=prefs;this.font=font;surface=new FrameLayout(context);addView(surface);
        setContentDescription(ui("미리보기"));refresh();
    }
    public void onWmRestyle(Typeface regular,Typeface bold,int selection,int tabColor){font=regular;refresh();}
    public void onWmLanguageChanged(String language){setContentDescription(ui("미리보기"));refresh();}
    void refresh(){
        surface.removeAllViews();boolean rose=prefs.getBoolean("rose",false);
        WmWindow home=window(true,rose);surface.addView(home,new FrameLayout.LayoutParams(WmMetrics.WIDTH,HOME_HEIGHT));
        WmWindow applet=window(false,rose);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(WmMetrics.WIDTH,APPLET_HEIGHT);p.topMargin=HOME_HEIGHT+GAP;surface.addView(applet,p);
        requestLayout();invalidate();
    }
    private WmWindow window(boolean home,boolean rose){
        Context c=getContext();WmWindow window=new WmWindow(c);window.title(title(home,rose));
        if(home){
            try{window.client.addView(new ThemeWallpaper(c,rose?"wallpaper_mugunghwa.gif":"backgrounds/classic.gif"),new FrameLayout.LayoutParams(-1,-1));}
            catch(IOException missing){window.client.setBackgroundColor(LauncherTheme.base(c,rose));}
            WmToday.Style style=new WmToday.Style(rose?Color.BLACK:Color.WHITE,LauncherTheme.color(c,0xff00f0ff,rose),LauncherTheme.darkSelection(c,rose),LauncherTheme.divider(c,rose));
            WmToday.Layout today=new WmToday.Layout(c,style);
            addToday(today,style,"messages",R.drawable.wm_mail,"받은 편지함",false);
            addToday(today,style,"tasks",R.drawable.wm_tasks,"작업",true);
            addToday(today,style,"lock",R.drawable.wm_lock_hd,"장치 잠금 해제됨",false);
            today.arrange(Collections.emptyList(),Collections.emptySet());window.client.addView(today,new FrameLayout.LayoutParams(-1,-2));
        }else{
            LinearLayout body=new LinearLayout(c);body.setOrientation(LinearLayout.VERTICAL);
            TextView selected=ClassicUi.text(c,font,ui("사용자 색 사용"));ClassicUi.entry(selected,rose);selected.setSelected(true);selected.setPadding(208,0,8,0);body.addView(selected,new LinearLayout.LayoutParams(-1,WmMetrics.TODAY_MIN));
            body.addView(new View(c),new LinearLayout.LayoutParams(-1,0,1));
            int tabColor=LauncherTheme.color(c,0xffc9e7e9,rose);WmTabStrip tabs=new WmTabStrip(c,tabColor);
            TextView personal=tabs.addTab(ui("개인"),font,()->{});WmControls.tab(personal,true,tabColor);personal.setSelected(true);
            tabs.addTab(ui("시스템"),font,()->{});tabs.addTab(ui("연결"),font,()->{});body.addView(tabs,new LinearLayout.LayoutParams(-1,WmMetrics.TAB));
            window.client.addView(body,new FrameLayout.LayoutParams(-1,-1));
            WmStartMenu menu=new WmStartMenu(c,Arrays.asList(menuEntry("programs","프로그램",R.drawable.wm_start_programs),menuEntry("settings","설정",R.drawable.wm_start_settings)),font,LauncherTheme.selection(c,rose),new StartMenuBackground(c,rose),entry->{});menu.setTextColor(rose?Color.BLACK:Color.WHITE);
            menu.setBackLabel(ui("뒤로"));menu.setMaxHeight(APPLET_HEIGHT-2*WmMetrics.BAR-WmMetrics.TAB);window.client.addView(menu,new FrameLayout.LayoutParams(menu.menuWidth(),-2));
        }
        WmCommandBar commands=new WmCommandBar(c,font,new LauncherBar(c,true,rose));commands.setCommands(ui(home?"전화":"확인"),()->{},ui(home?"연락처":"메뉴"),()->{});window.commands(commands);return window;
    }
    private void addToday(WmToday.Layout list,WmToday.Style style,String id,int resource,String label,boolean selected){
        ImageView icon=new ImageView(getContext());icon.setImageDrawable(LauncherArtwork.load(getContext(),resource));icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        WmToday.Row row=new WmToday.Row(getContext(),icon,font,style,ui(label));row.setSelected(selected);list.register(id,row,row.getLayoutParams().height,WmToday.Divider.BETWEEN);
    }
    private WmShell.Entry menuEntry(String id,String label,int icon){return new WmShell.Entry(id,"",ui(label),"android.resource://"+getContext().getPackageName()+"/"+icon,"",id,false);}
    private LinearLayout title(boolean home,boolean rose){
        Context c=getContext();LauncherBar bar=new LauncherBar(c,false,rose);
        WmTitleBar title=new WmTitleBar(c,ClassicUi.text(c,font,ui(home?"시작":"설정")),bar);title.start.setSelected(!home);
        for(int kind:new int[]{WmTraySlot.CONNECTION,WmTraySlot.PHONE,WmTraySlot.VOLUME})addTray(title,kind);
        if(home)addTray(title,WmTraySlot.BATTERY);
        else if(prefs.getBoolean("title_clock",true)){
            WmTrayClock clock=new WmTrayClock(c,font,bar.color(39));clock.language(UiLanguage.language(c));clock.refresh(new Date(),prefs.getBoolean("analog_clock",false));title.setClock(clock);
        }
        if(home)title.reserveCloseSpace();else title.closeButton(true);
        return title;
    }
    private void addTray(WmTitleBar title,int kind){
        ImageView icon=new ImageView(getContext());icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        WmTraySlot slot=title.addTray(kind,icon);
        if(kind==WmTraySlot.CONNECTION)slot.connectionIcon("signal");else icon.setImageDrawable(WmIcons.load(getContext(),kind==WmTraySlot.PHONE?"cellular_ready":kind==WmTraySlot.VOLUME?"volume":"battery_full"));
    }
    private String ui(String authored){return UiLanguage.t(getContext(),authored);}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){return true;}
    @Override public boolean onTouchEvent(MotionEvent event){return true;}
    @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec);scale=width/(float)WmMetrics.WIDTH;setMeasuredDimension(width,Math.round(HEIGHT*scale));surface.measure(MeasureSpec.makeMeasureSpec(WmMetrics.WIDTH,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(HEIGHT,MeasureSpec.EXACTLY));}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){surface.layout(0,0,WmMetrics.WIDTH,HEIGHT);surface.setPivotX(0);surface.setPivotY(0);surface.setScaleX(scale);surface.setScaleY(scale);}
}
