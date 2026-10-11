package me.ancal.wmdroid;

import android.Manifest;
import android.app.Activity;

import android.app.admin.DevicePolicyManager;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import me.ancal.wmdroid.sdk.WmToday;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android HOME host for the statically recovered ROM Today behavior and resources. */
public final class TodayActivity extends Activity {
    private static final Locale KOREAN = Locale.KOREAN;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private Typeface regular, bold;
    private String loadedHostFontKey;private int hostFontRevision;
    private final java.util.Set<String> loadingHostFonts=new java.util.HashSet<>();
    private LinearLayout fontChoices;private String pendingFontFamily;private boolean fontImportBusy;
    private boolean resourcePackBusy;private TextView resourcePackInfo;
    private final java.util.Set<String> loadingFontPreviews=new java.util.HashSet<>(),failedFontPreviews=new java.util.HashSet<>();
    private TodayDateView date;
    private TodayModules modules;
    private java.util.List<TodayModules.Row> moduleRows=new ArrayList<>();
    private TextView status, owner, messages, tasks, calendar;
    private int calendarRequest;
    private java.time.LocalDate calendarLaunchDay;
    private CalendarStore.Occurrence calendarLaunchOccurrence;
    private me.ancal.wmdroid.sdk.WmCommandBar homeCommands;
    private TodayConnectionsView wireless;
    private int statusBubble=-1;
    private StatusPanel activeStatusPanel;
    private LinearLayout homeTitle;
    private static final class TrayBar {
        final ImageView[] icons;
        final me.ancal.wmdroid.sdk.WmTrayClock clock;
        TrayBar(ImageView[] icons,me.ancal.wmdroid.sdk.WmTrayClock clock){this.icons=icons;this.clock=clock;}
        View hit(int kind){return kind==2&&clock!=null?clock:(View)icons[kind].getParent();}
    }
    private boolean trayReady;
    private int trayBattery=-1,trayRinger,phoneQueryGeneration;
    private boolean trayExternalPower,trayCharging;
    private me.ancal.wmdroid.sdk.WmDevice.Network trayNetwork;
    private me.ancal.wmdroid.sdk.WmDevice.Phone trayPhone;
    private final java.util.Map<LinearLayout,TrayBar> trayBars=new java.util.LinkedHashMap<>();
    private FrameLayout screen;
    private LinearLayout body;
    private WmToday.Layout todayList;
    private java.util.Map<String, View[]> todayRows = java.util.Collections.emptyMap();
    private FrameLayout startMenu;
    private me.ancal.wmdroid.sdk.WmPointer pointer;
    private View selectedStart;
    private me.ancal.wmdroid.sdk.WmStartMenu preparedStart;
    private Runnable pendingApplet;
    private View pendingHighlight;
    private final Runnable clockTimeout=()->{if(statusBubble==4)closeStart();};
    private LinearLayout page;
    private boolean receiving, appearanceRefreshPending, delayedPresentation;
    private boolean ownerWakePending, ownerWakeListening;
    private final BroadcastReceiver ownerWake = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { ownerWakePending=true; }
    };
    private int accent;
    private boolean pendingAppearance,pendingLanguage,rebuildingAppearance;
    private final Runnable applySettings=()->applyLiveSettings();
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsChanged=(store,key)->{
        if(key!=null){pendingAppearance|=key.startsWith("theme_")||key.startsWith("font_")||key.startsWith("item_")||key.startsWith("module_")||key.equals("today_order")||key.equals("wallpaper_tint")||key.equals("rose")||key.equals("language")||key.equals("title_clock")||key.equals("analog_clock");pendingLanguage|=key.equals("language");}
        if("calendar_flags".equals(key))loadCalendar();
        if("tasks".equals(key))TaskReminders.schedule(this);
        handler.removeCallbacks(applySettings);handler.postDelayed(applySettings,16);
        if(key!=null&&(key.startsWith("touch_sound_")||key.equals("ui_scale_percent")||key.equals("screen_mode")||key.startsWith("theme_")||key.startsWith("font_")||key.startsWith("start_")||key.startsWith("android_icons_")||key.equals("hidden_apps")||key.startsWith("retro_")||key.equals("applet_delay")||key.equals("language")||key.equals("rose")||key.equals("wallpaper_tint")||key.equals("title_clock")||key.equals("analog_clock")))
            getContentResolver().notifyChange(me.ancal.wmdroid.sdk.WmShell.STATE,null);
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() { refresh(); loadCalendar(); if(modules!=null)modules.refresh();handler.postDelayed(this,30000); }
    };
    private final BroadcastReceiver updates = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            refresh();String action=intent.getAction();
            if(MessageNotifications.CHANGED.equals(action))refreshMessageNotifications();
            if(Intent.ACTION_TIME_TICK.equals(action)||Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action)||Intent.ACTION_DATE_CHANGED.equals(action)){
                loadCalendar();
                for(UiDialog d=currentDialog;d!=null;d=d.previous)if(d.spec.view instanceof NotesPage)((NotesPage)d.spec.view).client.refreshMetadata();
            }
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);pendingFontFamily=saved==null?null:saved.getString("font_import_family");
        prefs = getSharedPreferences("today", MODE_PRIVATE);
        // Retire the alternate theme without changing the user's custom accent.
        if(prefs.getBoolean("rose",false))prefs.edit().putBoolean("rose",false).apply();
        modules=new TodayModules(this,rows->{moduleRows=rows;renderModules();});
        loadFonts();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        getWindow().setWindowAnimations(0);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        overridePendingTransition(0, 0);
        if(Build.VERSION.SDK_INT>=21)getWindow().setNavigationBarColor(Color.rgb(20,117,126));
        buildHome();BrightnessPage.apply(this,prefs);prefs.registerOnSharedPreferenceChangeListener(settingsChanged);ProgramsPage.prewarm(this,worker);boolean restoredNotes=restoreNotes(saved),restoredContact=restoreContactEditor(saved);boolean restoredAppointment=restoreAppointment(saved,!restoredNotes&&!restoredContact);boolean restoredSettings=!restoredNotes&&!restoredContact&&!restoredAppointment&&restoreSettingsGrid(saved);if(!restoredNotes&&!restoredContact&&!restoredAppointment&&!restoredSettings)handleShellCommand(getIntent());
        IntentFilter wake=new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(ownerWake,wake,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(ownerWake,wake);
        ownerWakeListening=true;
    }

    @Override protected void onResume() {
        super.onResume();TaskReminders.schedule(this);refreshContacts();preparedStart=null;BrightnessPage.apply(this,prefs);
        if (!receiving) {
            IntentFilter filter = new IntentFilter(MessageNotifications.CHANGED);
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);filter.addAction(android.media.AudioManager.RINGER_MODE_CHANGED_ACTION);filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);filter.addAction(android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION);filter.addAction(android.net.wifi.WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);filter.addAction(Intent.ACTION_TIME_TICK);filter.addAction(Intent.ACTION_TIME_CHANGED);filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);filter.addAction(Intent.ACTION_DATE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(updates, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(updates, filter);
            receiving = true;
        }
        handler.removeCallbacks(tick); tick.run();refreshMessageNotifications();
        if(currentDialog!=null&&currentDialog.spec.view instanceof me.ancal.wmdroid.sdk.WmBrowserView)((me.ancal.wmdroid.sdk.WmBrowserView)currentDialog.spec.view).resume();
        if(ownerWakePending){ownerWakePending=false;showOwnerOnWake();}
    }

    @Override protected void onPause() {
        cancelApplet();
        if(modules!=null)modules.pause();
        if(currentDialog!=null&&currentDialog.spec.onClose!=null)currentDialog.spec.onClose.run();
        handler.removeCallbacks(tick);
        for(TrayBar bar:trayBars.values())((me.ancal.wmdroid.sdk.WmTraySlot)bar.icons[0].getParent()).stopConnectionAnimation();
        if (receiving) { unregisterReceiver(updates); receiving = false; }
        closeStart();
        super.onPause();
    }

    private boolean destroyed;
    @Override protected void onDestroy() { destroyed=true;for(UiDialog d=currentDialog;d!=null;d=d.previous)if(d.spec.view instanceof me.ancal.wmdroid.sdk.WmBrowserView)((me.ancal.wmdroid.sdk.WmBrowserView)d.spec.view).close();for(UiDialog d=currentDialog;d!=null;d=d.previous)if(d.spec.view instanceof me.ancal.wmdroid.sdk.WmMediaView)((me.ancal.wmdroid.sdk.WmMediaView)d.spec.view).close();mediaPicker=null;prefs.unregisterOnSharedPreferenceChangeListener(settingsChanged);handler.removeCallbacks(applySettings); if(ownerWakeListening)unregisterReceiver(ownerWake);worker.shutdownNow();if(modules!=null)modules.close();super.onDestroy(); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); closeStart(); if(me.ancal.wmdroid.sdk.WmShell.ACTION.equals(intent.getAction())&&"notes".equals(intent.getStringExtra(me.ancal.wmdroid.sdk.WmShell.COMMAND))){runShellCommand("notes");return;} while (currentDialog != null) if(!currentDialog.dismiss())break;handleShellCommand(intent); }
    private void handleShellCommand(Intent intent){
        if(!me.ancal.wmdroid.sdk.WmShell.ACTION.equals(intent.getAction()))return;
        String command=intent.getStringExtra(me.ancal.wmdroid.sdk.WmShell.COMMAND);
        if(command!=null)handler.post(()->runShellCommand(command));
    }
    private void runShellCommand(String command){if(contactSaving())return;switch(command){
        case "tasks":showTasks();break;
        case "today":while(currentDialog!=null)if(!currentDialog.dismiss())break;break;
        case "calendar":openCalendar();break;case "contacts":openContacts();break;case "notes":openNotes(null);break;
        case "messages":openMessages();break;case "phone":openPhone();break;
        case "browser":openBrowser();break;
        case "media":openMedia();break;
        case "programs":programs();break;case "settings":settings();break;case "help":help();break;
    }}
    @Override public void onBackPressed() { if(pendingApplet!=null){cancelApplet();return;} if (startMenu != null) {if(!me.ancal.wmdroid.sdk.WmControls.back(startMenu))closeStart();} else if(currentDialog!=null&&currentDialog.spec.view instanceof CalendarPage&&((CalendarPage)currentDialog.spec.view).backPicker())return;else if(currentDialog!=null&&me.ancal.wmdroid.sdk.WmControls.back(currentDialog.spec.view))return;else if(currentDialog!=null&&currentDialog.spec.onBack!=null)currentDialog.spec.onBack.run();else closePage(); }
    @Override public boolean onKeyDown(int key,android.view.KeyEvent event){
        if(key==android.view.KeyEvent.KEYCODE_VOLUME_UP||key==android.view.KeyEvent.KEYCODE_VOLUME_DOWN){
            android.media.AudioManager audio=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);
            audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,key==android.view.KeyEvent.KEYCODE_VOLUME_UP?android.media.AudioManager.ADJUST_RAISE:android.media.AudioManager.ADJUST_LOWER,0);
            if(statusBubble!=1)showStatus(1);
            if(startMenu!=null&&statusBubble==1&&activeStatusPanel!=null)activeStatusPanel.refresh();
            refresh();return true;
        }
        return super.onKeyDown(key,event);
    }
    @Override public boolean onKeyUp(int key,android.view.KeyEvent event){if(key==android.view.KeyEvent.KEYCODE_VOLUME_UP||key==android.view.KeyEvent.KEYCODE_VOLUME_DOWN)return true;return super.onKeyUp(key,event);}

    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LauncherBar softBar() { return new LauncherBar(this,true,prefs.getBoolean("rose",false)); }
    private ImageView icon(int resource) {
        ImageView image = new ImageView(this); image.setImageDrawable(LauncherArtwork.load(this,resource));
        image.setScaleType(ImageView.ScaleType.FIT_CENTER); return image;
    }

    /** ROM VGA coordinates. Android density and user font scale never change this surface. */
    private String ui(String authored){return UiLanguage.t(this,authored);}
    private String[] words(String[] authored){String[] out=authored.clone();for(int i=0;i<out.length;i++)out[i]=ui(out[i]);return out;}
    private TextView vgaText(String value, int pixels, boolean heavy) {
        TextView view = ClassicUi.text(this,heavy?bold:regular,value);view.setTextColor(Color.WHITE);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, pixels);
        view.setIncludeFontPadding(false);
        if(Build.VERSION.SDK_INT>=21)view.setFontFeatureSettings("kern");
        if(Build.VERSION.SDK_INT>=23)view.setBreakStrategy(0);
        return view;
    }
    private String hostFontKey(LauncherFonts.Family family,boolean hybrid){return family.regular+"\n"+family.bold+"\n"+hybrid+"\n"+me.ancal.wmdroid.sdk.WmResources.revision(this);}
    private void loadFonts(){me.ancal.wmdroid.sdk.WmFonts.setSmoothing(prefs.getBoolean("font_smoothing",true));LauncherFonts.Family family=LauncherFonts.selected(prefs);String key=hostFontKey(family,prefs.getBoolean("font_hybrid",true));try{regular=family.regular(this);bold=family.bold(this,regular);}catch(RuntimeException unavailable){family=LauncherFonts.FAMILIES[0];regular=family.regular(this);bold=family.bold(this,regular);}loadedHostFontKey=key;}
    private void loadLiveFonts(){
        if(destroyed)return;
        me.ancal.wmdroid.sdk.WmFonts.setSmoothing(prefs.getBoolean("font_smoothing",true));
        LauncherFonts.Family family=LauncherFonts.selected(prefs);boolean hybrid=prefs.getBoolean("font_hybrid",true);
        String key=hostFontKey(family,hybrid);if(key.equals(loadedHostFontKey))return;
        if(me.ancal.wmdroid.sdk.WmFonts.isCached(this,family.regular,hybrid,false)&&me.ancal.wmdroid.sdk.WmFonts.isCached(this,family.bold,hybrid,true)){
            regular=family.regular(this,hybrid);bold=me.ancal.wmdroid.sdk.WmFonts.load(this,family.bold,hybrid,true);loadedHostFontKey=key;return;
        }
        // ponytail: shared worker can queue behind PIM/import work; isolate only if measured latency requires it.
        int revision=hostFontRevision;String request=key+"\n"+revision;
        if(loadingHostFonts.add(request))worker.execute(()->{
            Typeface normal=null,heavy=null;
            try{normal=family.regular(this,hybrid);heavy=me.ancal.wmdroid.sdk.WmFonts.load(this,family.bold,hybrid,true);}
            catch(RuntimeException unavailable){
                normal=null;heavy=null;
                try{LauncherFonts.Family fallback=LauncherFonts.FAMILIES[0];normal=fallback.regular(this,hybrid);heavy=me.ancal.wmdroid.sdk.WmFonts.load(this,fallback.bold,hybrid,true);}
                catch(RuntimeException missingFallback){normal=null;heavy=null;}
            }
            final Typeface nextRegular=normal,nextBold=heavy;
            handler.post(()->{
                loadingHostFonts.remove(request);
                if(destroyed||revision!=hostFontRevision||!key.equals(hostFontKey(LauncherFonts.selected(prefs),prefs.getBoolean("font_hybrid",true))))return;
                loadedHostFontKey=key; // Throttle failure until selection changes or an explicit retry.
                if(nextRegular==null||nextBold==null)return;
                regular=nextRegular;bold=nextBold;pendingAppearance=true;
                handler.removeCallbacks(applySettings);handler.post(applySettings);
            });
        });
    }
    private void retryLiveFonts(){
        Typeface previousRegular=regular,previousBold=bold;
        hostFontRevision++;loadedHostFontKey=null;loadLiveFonts();
        if(regular!=previousRegular||bold!=previousBold){pendingAppearance=true;handler.removeCallbacks(applySettings);handler.post(applySettings);}
    }
    private void fontSettings(){
        failedFontPreviews.clear();retryLiveFonts();
        LinearLayout choices=column();fontChoices=choices;choices.setPadding(8,8,8,0);populateFontChoices(choices);
        show(dialog("글꼴",new ClassicScrollView(this,choices)).setPropertySheet(null).setCommands("가져오기",()->startFontImport(false),"굵은 글꼴",()->startFontImport(true)).setOnLanguageChanged(()->{for(int i=0;i<choices.getChildCount();i++){View item=choices.getChildAt(i);if(item.getTag() instanceof LauncherFonts.Family){LauncherFonts.Family f=(LauncherFonts.Family)item.getTag();item.setContentDescription(ui(f.label)+(item.isSelected()?ui(" 선택됨"):""));}}}).create());
    }
    private void populateFontChoices(LinearLayout choices){
        choices.removeAllViews();String selected=LauncherFonts.selected(prefs).id;
        android.widget.CheckBox hybrid=new android.widget.CheckBox(this);ClassicUi.check(hybrid,regular);me.ancal.wmdroid.sdk.WmControls.bindText(hybrid,UiLanguage.language(this),"영문·숫자·기호에 원본 Tahoma 사용");hybrid.setChecked(prefs.getBoolean("font_hybrid",true));hybrid.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("font_hybrid",on).apply();});choices.addView(hybrid,new LinearLayout.LayoutParams(-1,44));
        android.widget.CheckBox smoothing=new android.widget.CheckBox(this);ClassicUi.check(smoothing,regular);me.ancal.wmdroid.sdk.WmControls.bindText(smoothing,UiLanguage.language(this),"글꼴 가장자리 부드럽게");smoothing.setChecked(prefs.getBoolean("font_smoothing",true));smoothing.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("font_smoothing",on).apply());choices.addView(smoothing,new LinearLayout.LayoutParams(-1,44));
        for(LauncherFonts.Family family:LauncherFonts.all(prefs)){
            LinearLayout choice=column();choice.setPadding(8,4,8,4);ClassicUi.entryBackground(choice,prefs.getBoolean("rose",false));choice.setSelected(family.id.equals(selected));choice.setTag(family);choice.setContentDescription(ui(family.label)+(family.id.equals(selected)?ui(" 선택됨"):""));
            android.content.res.ColorStateList colors=new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_selected},{android.R.attr.state_pressed},{}},new int[]{Color.WHITE,Color.WHITE,Color.BLACK});
            TextView label=authoredText(family.label);label.setDuplicateParentStateEnabled(true);label.setTextColor(colors);choice.addView(label,new LinearLayout.LayoutParams(-1,36));
            TextView preview=ClassicUi.text(this,previewFont(family),"다람쥐 헌 쳇바퀴에 타고파\n0123456789  ABC abc");me.ancal.wmdroid.sdk.WmControls.lockFont(preview);preview.setDuplicateParentStateEnabled(true);preview.setTextColor(colors);choice.addView(preview,new LinearLayout.LayoutParams(-1,72));
            choice.setSoundEffectsEnabled(false);choice.setOnClickListener(v->{
                if(family.id.equals(LauncherFonts.selected(prefs).id))return;
                prefs.edit().putString("font_family",family.id).apply();
                for(int i=0;i<choices.getChildCount();i++){View item=choices.getChildAt(i);if(item instanceof LinearLayout){item.setSelected(item==choice);LauncherFonts.Family f=(LauncherFonts.Family)item.getTag();item.setContentDescription(ui(f.label)+(item==choice?ui(" 선택됨"):""));}}
            });
            LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(-1,-2);position.bottomMargin=8;choices.addView(choice,position);
        }
    }

    private Typeface previewFont(LauncherFonts.Family family){
        boolean hybrid=prefs.getBoolean("font_hybrid",true);
        if(me.ancal.wmdroid.sdk.WmFonts.isCached(this,family.regular,hybrid,false))return family.regular(this,hybrid);
        String key=family.regular+"|"+hybrid;
        if(!destroyed&&!failedFontPreviews.contains(key)&&loadingFontPreviews.add(key))worker.execute(()->{
            boolean ok=true;try{family.regular(this,hybrid);}catch(RuntimeException unavailable){ok=false;}
            final boolean success=ok;handler.post(()->{
                loadingFontPreviews.remove(key);if(destroyed)return;if(!success)failedFontPreviews.add(key);
                if(prefs.getBoolean("font_hybrid",true)==hybrid)updateFontPreviews();
            });
        });
        return regular;
    }
    private void updateFontPreviews(){
        if(fontChoices==null||fontChoices.getWindowToken()==null)return;
        for(int i=0;i<fontChoices.getChildCount();i++){View item=fontChoices.getChildAt(i);if(item instanceof LinearLayout&&item.getTag() instanceof LauncherFonts.Family){TextView preview=(TextView)((LinearLayout)item).getChildAt(1);Typeface face=previewFont((LauncherFonts.Family)item.getTag());preview.setTypeface(face);me.ancal.wmdroid.sdk.WmFonts.attach(preview,face);}}
    }
    private void resourcePackSettings(){
        LinearLayout panel=column();panel.setPadding(12,8,12,8);
        resourcePackInfo=authoredText("리소스팩 정보를 읽는 중입니다.");panel.addView(resourcePackInfo,new LinearLayout.LayoutParams(-1,-2));
        TextView help=authoredText("아이콘·글꼴·소리·배경 ZIP을 가져옵니다.");panel.addView(help,new LinearLayout.LayoutParams(-1,-2));
        show(dialog("리소스팩",new ClassicScrollView(this,panel)).setPropertySheet(null).setCommands("가져오기",this::startResourcePackImport,"기본값",()->changeResourcePack(null,true)).setOnLanguageChanged(this::refreshResourcePackInfo).create());
        refreshResourcePackInfo();
    }
    private void refreshResourcePackInfo(){
        final TextView info=resourcePackInfo;if(info==null)return;
        worker.execute(()->{String status;
            try{me.ancal.wmdroid.sdk.WmResourcePack pack=me.ancal.wmdroid.sdk.WmResources.prepare(this);status=pack==null?"기본 리소스 사용 중":"리소스팩 적용 중";final String label=status;
                handler.post(()->{if(!destroyed&&resourcePackInfo==info)info.setText(ui(label));});
            }catch(IOException failure){handler.post(()->{if(!destroyed&&resourcePackInfo==info)info.setText(ui("리소스팩 정보를 읽을 수 없습니다."));});}
        });
    }
    private void startResourcePackImport(){
        if(resourcePackBusy){toast("리소스팩을 적용하는 중입니다.");return;}
        Intent picker=new Intent(Build.VERSION.SDK_INT>=19?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(picker,102);overridePendingTransition(0,0);}catch(RuntimeException absent){toast("파일 선택기를 열 수 없습니다.");}
    }
    private void changeResourcePack(Uri uri,boolean reset){
        if(resourcePackBusy){toast("리소스팩을 적용하는 중입니다.");return;}
        if(!reset&&(uri==null||!"content".equals(uri.getScheme())&&!"file".equals(uri.getScheme()))){toast("리소스팩을 읽을 수 없습니다.");return;}
        resourcePackBusy=true;toast("리소스팩을 적용하는 중입니다.");
        worker.execute(()->{
            try{
                if(reset)me.ancal.wmdroid.sdk.WmResources.reset(this);
                else {
                    boolean installed=false;
                    try(InputStream input=getContentResolver().openInputStream(uri)){
                        me.ancal.wmdroid.sdk.WmResources.install(this,input);installed=true;
                    }catch(IOException|RuntimeException failure){if(!installed)throw failure;}
                }
                handler.post(()->{resourcePackBusy=false;if(destroyed)return;closeStart();preparedStart=null;failedFontPreviews.clear();retryLiveFonts();pendingAppearance=true;applyLiveSettings();refreshResourcePackInfo();toast("리소스팩 설정을 적용했습니다.");});
            }catch(IOException|RuntimeException failure){handler.post(()->{resourcePackBusy=false;if(!destroyed)toast("리소스팩을 적용하지 못했습니다. ZIP 형식을 확인하세요.");});}
        });
    }
    private void startFontImport(boolean heavy){
        if(fontImportBusy){toast("글꼴을 가져오는 중입니다.");return;}
        LauncherFonts.Family selected=LauncherFonts.selected(prefs);if(heavy&&!selected.id.startsWith("custom_")){toast("가져온 글꼴을 먼저 선택하세요.");return;}
        pendingFontFamily=heavy?selected.id:null;
        Intent picker=new Intent(Build.VERSION.SDK_INT>=19?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(picker,heavy?101:100);overridePendingTransition(0,0);}catch(RuntimeException absent){pendingFontFamily=null;toast("파일 선택기를 열 수 없습니다.");}
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(pendingAppointmentState!=null)out.putBundle("appointment_editor",pendingAppointmentState);else for(UiDialog owner=currentDialog;owner!=null;owner=owner.previous)if(owner.spec.view instanceof AppointmentPage){out.putBundle("appointment_editor",appointmentState((AppointmentPage)owner.spec.view,owner));break;}if(currentDialog!=null&&currentDialog.spec.view instanceof SettingsPage)out.putBundle("settings_grid",((SettingsPage)currentDialog.spec.view).saveInteractionState());if(pendingFontFamily!=null)out.putString("font_import_family",pendingFontFamily);for(UiDialog dialog=currentDialog;dialog!=null;dialog=dialog.previous)if(dialog.spec.view instanceof ContactEditorPage){out.putBundle("contact_editor",((ContactEditorPage)dialog.spec.view).saveState());break;}for(UiDialog d=currentDialog;d!=null;d=d.previous)if(d.spec.view instanceof NotesPage){Bundle note=((NotesPage)d.spec.view).saveState();if(note!=null)out.putBundle("notes_draft",note);else out.putBundle("notes_list",((NotesPage)d.spec.view).client.saveInteractionState());break;}if(notesPropertiesForm!=null&&notesPropertiesState!=null){Bundle properties=new Bundle(notesPropertiesState);properties.putString("name",notesPropertiesForm.name());String[] ids=properties.getStringArray("folder_ids");if(ids!=null&&notesPropertiesForm.folderIndex()<ids.length)properties.putString("chosen_folder",ids[notesPropertiesForm.folderIndex()]);properties.putInt("caret_start",notesPropertiesForm.nameField().getSelectionStart());properties.putInt("caret_end",notesPropertiesForm.nameField().getSelectionEnd());out.putBundle("notes_properties",properties);}}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==103){final me.ancal.wmdroid.sdk.WmMediaView target=mediaPicker;final boolean append=mediaPickerAppend;mediaPicker=null;if(result==RESULT_OK&&data!=null&&target!=null&&target.state()!=me.ancal.wmdroid.sdk.WmMediaView.State.CLOSED)mediaPickerResult(target,data,append);return;}
        if(request==102){if(result==RESULT_OK&&data!=null&&data.getData()!=null)changeResourcePack(data.getData(),false);return;}
        if(request!=100&&request!=101)return;
        String boldFor=request==101?pendingFontFamily:null;pendingFontFamily=null;if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request==101&&boldFor==null){toast("가져온 글꼴을 먼저 선택하세요.");return;}
        Uri uri=data.getData();if(!"content".equals(uri.getScheme())&&!"file".equals(uri.getScheme())){toast("글꼴을 읽을 수 없습니다.");return;}
        fontImportBusy=true;toast("글꼴을 가져오는 중입니다.");worker.execute(()->{
            try(InputStream input=getContentResolver().openInputStream(uri)){
                String name="가져온 글꼴";try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst()&&!cursor.isNull(0))name=cursor.getString(0);}catch(RuntimeException absent){}
                name=name.replaceAll("[\\p{Cntrl}]","").trim();if(name.isEmpty())name="가져온 글꼴";if(name.length()>80)name=name.substring(0,80);final String label=name;
                java.io.File file=me.ancal.wmdroid.sdk.WmFontFile.importFont(this,input);
                // The imported catalog belongs to the application, even if its picker Activity was recreated.
                LauncherFonts.install(getApplicationContext().getSharedPreferences("today",0),file,label,boldFor);
                handler.post(()->{fontImportBusy=false;if(destroyed)return;retryLiveFonts();if(fontChoices!=null&&fontChoices.getWindowToken()!=null)populateFontChoices(fontChoices);toast("글꼴을 적용했습니다.");});
            }catch(IOException|org.json.JSONException|RuntimeException failure){handler.post(()->{fontImportBusy=false;if(!destroyed)toast("글꼴을 읽을 수 없습니다. TTF·OTF, 최대 16MB를 확인하세요.");});}
        });
    }

    /** Repaint chrome, preserve all live editors, navigation, focus and scrolling. */
    private void applyLiveSettings(){
        if(destroyed||screen==null)return;
        me.ancal.wmdroid.sdk.WmFonts.setSmoothing(prefs.getBoolean("font_smoothing",true));
        refreshHomeCommands();
        if(!prefs.getBoolean("retro_enabled",true))me.ancal.wmdroid.sdk.WmPaint.stopReplay(screen);
        if(screen.getParent() instanceof me.ancal.wmdroid.sdk.WmViewport)((me.ancal.wmdroid.sdk.WmViewport)screen.getParent()).setTouchSoundLevel(prefs.getBoolean("touch_sound_enabled",false)?Math.max(1,Math.min(2,prefs.getInt("touch_sound_level",2))):0);
        if(screen.getParent() instanceof VgaViewport)((VgaViewport)screen.getParent()).applyDisplay(this,prefs.getInt("ui_scale_percent",100),prefs.getString("screen_mode","full"));
        me.ancal.wmdroid.sdk.WmStartMenu openMenu=visibleStartMenu();
        if(!pendingAppearance){if(openMenu!=null)restyleStart(openMenu);BrightnessPage.apply(this,prefs);refresh();return;}
        pendingAppearance=false;UiDialog active=currentDialog;FrameLayout attachedScreen=screen;
        FrameLayout attachedStart=openMenu==null?null:startMenu;
        // Keep the shared Start surface attached while replacing Today chrome.
        // Status and command popups retain their normal close/reopen lifecycle.
        if(attachedStart!=null){startMenu=null;if(selectedStart!=null)selectedStart.setSelected(false);selectedStart=null;}
        me.ancal.wmdroid.sdk.WmPointer attachedPointer=pointer;
        loadLiveFonts();rebuildingAppearance=true;
        try{buildHome();}finally{rebuildingAppearance=false;}
        FrameLayout freshHome=screen;screen=attachedScreen;
        // Leave the active editor attached: replacing the Activity content loses
        // focus/IME state even when the same EditText object is added back.
        for(int i=screen.getChildCount()-1;i>=0;i--){View child=screen.getChildAt(i);if((active==null||child!=active.window)&&child!=attachedPointer&&child!=attachedStart)screen.removeViewAt(i);}
        int index=0;while(freshHome.getChildCount()>0){View child=freshHome.getChildAt(0);freshHome.removeViewAt(0);screen.addView(child,index++);}
        screen.setBackgroundDrawable(freshHome.getBackground());pointer=attachedPointer;BrightnessPage.apply(this,prefs);
        if(active!=null){
            for(UiDialog dialog=active;dialog!=null;dialog=dialog.previous)dialog.restyle();
            currentDialog=active;page=active.window;
        }
        if(attachedStart!=null){
            startMenu=attachedStart;preparedStart=openMenu;restyleStart(openMenu);
            me.ancal.wmdroid.sdk.WmTitleBar title=(me.ancal.wmdroid.sdk.WmTitleBar)(active==null?homeTitle:active.title);
            selectedStart=title.start;selectedStart.setSelected(true);attachedStart.bringToFront();
        }
        updateFontPreviews();pendingLanguage=false;appearanceRefreshPending=false;refresh();if(modules!=null)modules.refresh();if(pointer!=null)pointer.bringToFront();
    }

    private void buildHome() {
        appearanceRefreshPending=false;
        closeStart();page = null; currentDialog = null; trayBars.clear();
        accent = LauncherTheme.selection(this,prefs.getBoolean("rose",false));
        screen = new FrameLayout(this); screen.setBackgroundColor(accent);
        try {
            screen.addView(new ThemeWallpaper(this,prefs.getBoolean("rose",false)?"wallpaper_mugunghwa.gif":"backgrounds/classic.gif"),new FrameLayout.LayoutParams(-1,-1));
        } catch (IOException error) { screen.setBackgroundColor(LauncherTheme.base(this,prefs.getBoolean("rose",false))); }
        LinearLayout main = column(); screen.addView(main, new FrameLayout.LayoutParams(-1, -1));
        homeTitle=titleBar("시작",null,false);main.addView(homeTitle,new LinearLayout.LayoutParams(-1,52));
        status=vgaText("",24,false);status.setVisibility(View.GONE);
        ScrollView scroll = new ScrollView(this); scroll.setOverScrollMode(View.OVER_SCROLL_NEVER); scroll.setFillViewport(true);
        todayList=new WmToday.Layout(this,todayStyle());body=todayList;todayRows=todayList.rows();
        scroll.setVerticalScrollBarEnabled(false); scroll.addView(body);
        main.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        date = new TodayDateView(this, regular, prefs.getBoolean("rose",false));
        date.setOnClickListener(view -> openApplet(()->launch(new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)),date));
        todayList.register("날짜",date,date.rowHeight(),WmToday.Divider.BETWEEN);
        wireless=new TodayConnectionsView(this,regular,prefs.getBoolean("rose",false));wireless.setOnClickListener(v->openApplet(()->showStatus(0),wireless));
        todayList.register("무선",wireless,wireless.rowHeight(),WmToday.Divider.BETWEEN);
        owner = row(R.drawable.wm_owner, "소유자 정보", this::editOwner);
        messages = row(R.drawable.wm_mail, "받은 편지함", this::openMessageNotifications);
        tasks = row(R.drawable.wm_tasks, "작업", this::showTasks);
        calendarLaunchDay=null;calendarLaunchOccurrence=null;
        calendar = row(R.drawable.wm_calendar, "일정", this::openTodayCalendar);
        row(R.drawable.wm_lock_hd, "장치 잠금 해제됨", this::lockScreen);
        renderModules();
        body.setOnLongClickListener(view -> {if(!prefs.getBoolean("finger_mouse_long_press",false))return false;togglePointer();return true;});
        me.ancal.wmdroid.sdk.WmCommandBar soft=new me.ancal.wmdroid.sdk.WmCommandBar(this,regular,softBar());
        homeCommands=soft;refreshHomeCommands();
        main.addView(soft, new LinearLayout.LayoutParams(-1, 52));
        if(!rebuildingAppearance){VgaViewport viewport=new VgaViewport(this);viewport.addView(screen);viewport.applyDisplay(this,prefs.getInt("ui_scale_percent",100),prefs.getString("screen_mode","full"));viewport.setTouchSoundLevel(prefs.getBoolean("touch_sound_enabled",false)?Math.max(1,Math.min(2,prefs.getInt("touch_sound_level",2))):0);setContentView(viewport);pointer=null;if(prefs.getBoolean("finger_mouse",false))attachPointer();}
        refresh();loadCalendar();
    }

    private void attachPointer(){pointer=new me.ancal.wmdroid.sdk.WmPointer(this,screen,this::togglePointer);screen.addView(pointer,new FrameLayout.LayoutParams(-1,-1));}
    private void togglePointer(){boolean enabled=pointer==null;prefs.edit().putBoolean("finger_mouse",enabled).apply();if(enabled)attachPointer();else{screen.removeView(pointer);pointer=null;}}
    private LinearLayout titleBar(String caption,Runnable close,boolean closeIsOk){
        LauncherBar titlePaint=new LauncherBar(this,false,prefs.getBoolean("rose",false));
        // SHCreateSystemFont -> SHPreProcessLogFont -> ossvcs.#31: Korean LCID
        // makes the title's requested 700 weight become 400 in this ROM.
        me.ancal.wmdroid.sdk.WmTitleBar title=new me.ancal.wmdroid.sdk.WmTitleBar(this,vgaText(ui(caption),24,false),titlePaint);
        title.start.setContentDescription(ui("시작 메뉴"));title.start.setOnClickListener(v->showStart(title.start));
        ImageView[] icons={icon(R.drawable.wm_signal_hd),icon(R.drawable.wm_volume_hd),icon(R.drawable.wm_battery_full_hd),icon(R.drawable.wm_cellular_off_hd)};
        for(int kind:new int[]{0,3,1})trayIcon(title,icons[kind],kind);
        // Today keeps the battery. Applet clocks default on for the user's Omnia
        // reference; the optional setting retains the emulator-style hidden slot.
        me.ancal.wmdroid.sdk.WmTrayClock clock=null;
        if(close==null)trayIcon(title,icons[2],2);
        else if(prefs.getBoolean("title_clock",true)){
            clock=new me.ancal.wmdroid.sdk.WmTrayClock(this,regular,titlePaint.color(39));clock.language(UiLanguage.language(this));
            clock.refresh(new Date(),prefs.getBoolean("analog_clock",false));
            clock.setSoundEffectsEnabled(false);clock.setHapticFeedbackEnabled(false);
            clock.setOnClickListener(v->showClock(v.getLeft()+v.getWidth()/2));
            clock.setOnLongClickListener(v->{showClockMenu(v.getRight());return true;});
            title.setClock(clock);
        }
        trayBars.put(title,new TrayBar(icons,clock));
        if(close!=null){
            ImageView button=title.closeButton(closeIsOk);button.setContentDescription(ui(closeIsOk?"확인":"닫기"));button.setOnClickListener(v->close.run());
        }
        else title.reserveCloseSpace();
        return title;
    }
    private void trayIcon(me.ancal.wmdroid.sdk.WmTitleBar title,ImageView image,int kind){
        me.ancal.wmdroid.sdk.WmTraySlot hit=title.addTray(kind,image);
        hit.setContentDescription(ui(kind==0?"연결 메뉴":kind==1?"볼륨 메뉴":kind==2?"전원 메뉴":"전화 메뉴"));
        hit.setOnClickListener(v->showStatus(kind,hit.getLeft()+hit.getWidth()/2));
    }
    private void showStatus(int kind){
        TrayBar bar=trayBars.get(currentDialog==null?homeTitle:currentDialog.title);
        View hit=bar==null?null:bar.hit(kind);showStatus(kind,hit==null?480-19-(2-kind)*38:hit.getLeft()+hit.getWidth()/2);
    }
    private void showStatus(int kind,int anchor){
        boolean same=startMenu!=null&&statusBubble==kind;closeStart();if(same)return;
        statusBubble=kind;startMenu=surface();startMenu.setOnClickListener(v->closeStart());
        StatusPanel panel=new StatusPanel(this,regular,bold,kind,new StatusPanel.Host(){
            public void launch(Intent intent){closeStart();TodayActivity.this.launch(intent);}
            public void changed(){refresh();}
            public void backlight(){closeStart();openApplet(TodayActivity.this::backlight);}
            public void hide(){closeStart();}
            public void settings(){closeStart();openApplet(()->TodayActivity.this.settings(2));}
        });activeStatusPanel=panel;panel.setOnClickListener(v->{});
        // SHBoxEx wide bubbles:222*DPI/96. Volume placement offset90 is
        // measured from the VGA capture; its exact shell placement rule is pending.
        int width=kind==1?154:444;
        int left=me.ancal.wmdroid.sdk.WmPopup.leftForAnchor(width,anchor,Math.max(480,screen.getWidth()));
        FrameLayout bubble=popupBubble(kind==0?"연결":kind==1?"볼륨":kind==2?"전원":"전화",anchor-left,panel);
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(width,-2);position.leftMargin=left;startMenu.addView(bubble,position);
        FrameLayout.LayoutParams overlay=new FrameLayout.LayoutParams(-1,-1);overlay.topMargin=52;screen.addView(startMenu,overlay);if(pointer!=null)pointer.bringToFront();
    }
    private void showClock(int anchor){
        boolean same=startMenu!=null&&statusBubble==4;closeStart();if(same)return;
        statusBubble=4;startMenu=surface();startMenu.setOnClickListener(v->closeStart());
        // SHBoxEx / 3855B9C: 222*DPI/96, centered with an anchor-edge adjustment.
        int width=444,left=me.ancal.wmdroid.sdk.WmPopup.leftForAnchor(width,anchor,Math.max(480,screen.getWidth()));
        ClockPanel panel=new ClockPanel(this,regular,bold,worker);panel.setOnClickListener(v->{});
        FrameLayout bubble=popupBubble("시간 및 다음 약속",anchor-left,panel);
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(width,-2);position.leftMargin=left;startMenu.addView(bubble,position);
        FrameLayout.LayoutParams overlay=new FrameLayout.LayoutParams(-1,-1);overlay.topMargin=52;screen.addView(startMenu,overlay);if(pointer!=null)pointer.bringToFront();
        handler.postDelayed(clockTimeout,7000); // shell32 214FC -> SHBoxEx timer
    }
    private FrameLayout popupBubble(String caption,int anchor,View panel){
        boolean rose=prefs.getBoolean("rose",false);
        return new me.ancal.wmdroid.sdk.WmPopup(this,regular,ui(caption),anchor,
                LauncherTheme.color(this,rose?0xffc72c76:0xff15afbc,rose),LauncherTheme.color(this,rose?0xffd25691:0xff149ca7,rose),
                paintContent(panel,RetroSurface.Role.POPUP));
    }

    private void showClockMenu(int right){
        closeStart();startMenu=surface();startMenu.setOnClickListener(v->closeStart());
        me.ancal.wmdroid.sdk.WmRadioMenu menu=new me.ancal.wmdroid.sdk.WmRadioMenu(this,regular,words(new String[]{"아날로그","디지털"}),prefs.getBoolean("analog_clock",false)?0:1,ui(" 선택됨"),LauncherTheme.selection(this,prefs.getBoolean("rose",false)),index->{prefs.edit().putBoolean("analog_clock",index==0).apply();closeStart();refresh();});
        int width=menu.menuWidth();
        // TPM_RIGHTALIGN. Native menu row/padding metrics remain provisional.
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(width,-2);position.leftMargin=Math.max(0,right-width);position.topMargin=52;startMenu.addView(menuFrame(menu),position);screen.addView(startMenu,new FrameLayout.LayoutParams(-1,-1));
    }
    private WmToday.Style todayStyle(){
        boolean rose=prefs.getBoolean("rose",false);
        return new WmToday.Style(rose?Color.BLACK:Color.WHITE,LauncherTheme.color(this,0xff00f0ff,rose),LauncherTheme.darkSelection(this,rose),LauncherTheme.divider(this,rose));
    }
    private TextView row(int image, String value, Runnable action) {
        WmToday.Row row=new WmToday.Row(this,icon(image),regular,todayStyle(),ui(value),
            image==R.drawable.wm_owner||image==R.drawable.wm_tasks||image==R.drawable.wm_calendar
                ?WmToday.TextStyle.NATIVE_LINES:WmToday.TextStyle.STANDARD);
        row.setOnClickListener(view -> openApplet(action,row));
        String key=image==R.drawable.wm_wireless_hd?"무선":image==R.drawable.wm_owner?"소유자 정보":image==R.drawable.wm_mail?"받은 편지함":image==R.drawable.wm_tasks?"작업":image==R.drawable.wm_calendar?"일정":"장치 잠금";
        todayList.register(key,row,row.getLayoutParams().height,WmToday.Divider.BETWEEN);
        return row.caption;
    }

    private void refresh() {
        if (date == null) return;
        Date now = new Date(); date.refresh(now);
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        status.setText(new SimpleDateFormat("h:mm", KOREAN).format(now));
        int percent=level>=0&&scale>0?level*100/scale:-1;
        boolean externalPower=battery!=null&&battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;
        boolean charging=battery!=null&&battery.getIntExtra(BatteryManager.EXTRA_STATUS,-1)==BatteryManager.BATTERY_STATUS_CHARGING;
        status.setContentDescription(ui("시각"));
        String name=prefs.getString("owner", ""),company=prefs.getString("owner_company", ""),phone=prefs.getString("owner_phone", "");
        String identity=name.isEmpty()?company:name;
        String ownerPrefix=UiLanguage.format(this,"소유자: %s","");
        if(identity.isEmpty()){owner.setText(ui("소유자 정보를 설정합니다."));owner.setTypeface(regular);}
        else {owner.setTypeface(regular);owner.setText(ownerPrefix+identity+(phone.isEmpty()?"":"\n"+phone));}
        ((WmToday.Row)owner.getParent()).setCaptionLines(owner.getText());
        int count = prefs.getInt("message_count", -1);
        messages.setTypeface(regular);
        fitCaption(messages,count < 0 ? ui("받은 편지함") : UiLanguage.format(this,"메시지 알림 %d개",count));
        updateTaskSummary();
        trayBattery=percent;trayExternalPower=externalPower;trayCharging=charging;
        trayNetwork=me.ancal.wmdroid.sdk.WmDevice.network(this);
        trayPhone=me.ancal.wmdroid.sdk.WmDevice.phone(this);
        updatePhoneClient();
        trayRinger=((android.media.AudioManager)getSystemService(AUDIO_SERVICE)).getRingerMode();
        trayReady=true;
        for(TrayBar bar:trayBars.values())refreshTray(bar,now);
        wireless.refresh();
    }

    // Opening another WM window reuses live tray state; system events and the tick refresh it.
    private void refreshTray(TrayBar bar,Date now){
        ImageView[] icons=bar.icons;
        if(bar.clock!=null)bar.clock.refresh(now,prefs.getBoolean("analog_clock",false));
        icons[2].setImageDrawable(LauncherArtwork.load(this,BatteryIconState.icon(BatteryIconState.state(trayBattery,trayExternalPower,trayCharging))));
        icons[2].setContentDescription(UiLanguage.format(this,"배터리 %s",trayBattery<0?ui("잔량 알 수 없음"):trayBattery+"%")+(trayCharging?ui(", 충전 중"):trayExternalPower?ui(", 외부 전원"):""));
        icons[0].setVisibility(View.VISIBLE);icons[0].setAlpha(1f);
        ((me.ancal.wmdroid.sdk.WmTraySlot)icons[0].getParent()).connectionState(trayNetwork);
        icons[3].setImageDrawable(me.ancal.wmdroid.sdk.WmIcons.load(this,trayPhone.icon()));
        icons[3].setContentDescription(ui(trayPhone.caption())+ui(" — 전화 메뉴"));
        icons[0].setContentDescription(ui(trayNetwork==null?"연결 안 됨 — 연결 메뉴":trayNetwork.connecting?"연결 중 — 연결 메뉴":"연결됨 — 연결 메뉴"));
        icons[1].setContentDescription(ui("볼륨 — ")+ui(trayRinger==android.media.AudioManager.RINGER_MODE_SILENT?"끄기":trayRinger==android.media.AudioManager.RINGER_MODE_VIBRATE?"진동":"켜기"));
        icons[1].setImageDrawable(LauncherArtwork.load(this,trayRinger==android.media.AudioManager.RINGER_MODE_NORMAL?R.drawable.wm_volume_hd:trayRinger==android.media.AudioManager.RINGER_MODE_VIBRATE?R.drawable.wm_volume_vibrate_hd:R.drawable.wm_volume_muted_hd));
    }

    private void refreshPhoneAfterOpen(){
        final int ticket=++phoneQueryGeneration;
        final me.ancal.wmdroid.sdk.WmDevice.Phone before=trayPhone;
        try{worker.execute(()->{
            final me.ancal.wmdroid.sdk.WmDevice.Phone current=me.ancal.wmdroid.sdk.WmDevice.phone(this);
            handler.post(()->{
                // A newer event/tick snapshot wins over this background query.
                if(destroyed||isFinishing()||ticket!=phoneQueryGeneration||trayPhone!=before)return;
                trayPhone=current;
                updatePhoneClient();
                for(TrayBar bar:trayBars.values()){
                    bar.icons[3].setImageDrawable(me.ancal.wmdroid.sdk.WmIcons.load(this,current.icon()));
                    bar.icons[3].setContentDescription(ui(current.caption())+ui(" — 전화 메뉴"));
                }
            });
        });}catch(java.util.concurrent.RejectedExecutionException stopped){/* Activity is closing. */}
    }

    private void arrangeToday() {
        java.util.List<String> order=todayOrder();
        java.util.Set<String> hidden=new java.util.HashSet<>();
        for(String key:todayRows.keySet())if(!prefs.getBoolean("item_"+key,true))hidden.add(key);
        todayList.arrange(order,hidden);
    }
    private java.util.List<String> todayOrder(){
        java.util.List<String> order = new ArrayList<>(); order.add("날짜");
        try { JSONArray saved=new JSONArray(prefs.getString("today_order","[]")); for(int i=0;i<saved.length();i++) { String key=saved.optString(i); if(todayRows.containsKey(key)&&!order.contains(key)) order.add(key); } } catch(JSONException ignored) { }
        for(String key:todayRows.keySet())if(!order.contains(key))order.add(key);
        return order;
    }
    private void fitCaption(TextView label, CharSequence value) {
        ((WmToday.Row)label.getParent()).setCaption(value);
    }
    private void updateTaskSummary() {
        java.time.LocalDate today=java.time.LocalDate.now(); int total=0,high=0,due=0,overdue=0;
        String category=prefs.getString("task_category",""); JSONArray list=readTasks();
        for(int i=0;i<list.length();i++) { JSONObject item=list.optJSONObject(i); if(item.optBoolean("done")||!category.isEmpty()&&!taskHasCategory(item.optString("category"),category)) continue;
            total++; if(item.optInt("priority")==1) high++;
            try { java.time.LocalDate day=java.time.LocalDate.parse(item.optString("due")); if(day.equals(today))due++;else if(day.isBefore(today))overdue++; } catch(java.time.format.DateTimeParseException ignored) { }
        }
        tasks.setTypeface(regular); StringBuilder caption=new StringBuilder(total==0?ui("작업 없음"):UiLanguage.format(this,"진행 중인 작업 %d개",total)); int flags=prefs.getInt("task_flags",1);
        if(total>0) { if((flags&1)!=0&&high>0)caption.append("\n").append(UiLanguage.format(this,"높은 우선 순위 작업 %d개",high)); if((flags&4)!=0&&due>0)caption.append("\n").append(UiLanguage.format(this,"오늘 만기되는 작업 %d개",due)); if((flags&2)!=0&&overdue>0)caption.append("\n").append(UiLanguage.format(this,"지연된 작업 %d개",overdue)); }
        ((WmToday.Row)tasks.getParent()).setCaptionLines(caption.toString());
    }

    private void closeStart() { handler.removeCallbacks(clockTimeout);cancelApplet();if (startMenu != null) { screen.removeView(startMenu); startMenu = null; } if(selectedStart!=null){selectedStart.setSelected(false);selectedStart=null;} statusBubble=-1;activeStatusPanel=null; }
    private void closePage() { if (currentDialog != null) currentDialog.dismiss(); }
    private UiDialog currentDialog;

    private me.ancal.wmdroid.sdk.WmStartMenu visibleStartMenu(){
        if(startMenu!=null)for(int i=0;i<startMenu.getChildCount();i++)
            if(startMenu.getChildAt(i) instanceof me.ancal.wmdroid.sdk.WmStartMenu)
                return (me.ancal.wmdroid.sdk.WmStartMenu)startMenu.getChildAt(i);
        return null;
    }
    private void restyleStart(me.ancal.wmdroid.sdk.WmStartMenu menu){
        menu.refresh(ShellProvider.entries(this,prefs),regular,accent,new StartMenuBackground(this,prefs.getBoolean("rose",false)));
        menu.setIconPolicy(me.ancal.wmdroid.sdk.WmIconPolicy.read(prefs));
        menu.setTextColor(prefs.getBoolean("rose",false)?Color.BLACK:Color.WHITE);
        menu.setBackLabel(ui("뒤로"));menu.setMaxHeight(screen.getHeight()-104);
    }

    private void showStart(View anchor) {
        if(contactSaving())return;
        if (startMenu != null) { closeStart(); return; }
        cancelApplet();
        selectedStart=anchor;anchor.setSelected(true);
        startMenu = surface();
        startMenu.setOnClickListener(view -> closeStart());
        if(preparedStart==null)preparedStart=new me.ancal.wmdroid.sdk.WmStartMenu(this,ShellProvider.entries(this,prefs),regular,accent,
            new StartMenuBackground(this,prefs.getBoolean("rose",false)),entry->{
                openApplet(()->{closeStart();if(!entry.command.isEmpty())runShellCommand(entry.command);else{ComponentName app=ComponentName.unflattenFromString(entry.component);if(app!=null)launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app));}});
            });
        else preparedStart.refresh(ShellProvider.entries(this,prefs),regular,accent,new StartMenuBackground(this,prefs.getBoolean("rose",false)));
        me.ancal.wmdroid.sdk.WmStartMenu menu=preparedStart;
        if(menu.getParent() instanceof ViewGroup)((ViewGroup)menu.getParent()).removeView(menu);
        menu.dismissFolders();
        menu.setIconPolicy(me.ancal.wmdroid.sdk.WmIconPolicy.read(prefs));menu.setTextColor(prefs.getBoolean("rose",false)?Color.BLACK:Color.WHITE);menu.setBackLabel(ui("뒤로"));menu.setMaxHeight(screen.getHeight()-104);
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(menu.menuWidth(),-2);position.topMargin=52;startMenu.addView(menu,position);screen.addView(startMenu,new FrameLayout.LayoutParams(-1,-1));if(pointer!=null)pointer.bringToFront();
    }

    private void startItem(LinearLayout menu,String id){
        switch(id){
            case "today":menuItem(menu,ui("오늘"),R.drawable.wm_start_today,()->{while(currentDialog!=null)if(!currentDialog.dismiss())break;});return;
            case "calendar":menuItem(menu,ui("일정"),R.drawable.wm_calendar,this::openCalendar);return;
            case "contacts":menuItem(menu,ui("연락처"),R.drawable.wm_contacts,this::openContacts);return;
            case "messages":menuItem(menu,ui("메시지"),R.drawable.wm_mail,this::openMessages);return;
            case "phone":menuItem(menu,ui("전화"),R.drawable.wm_phone_hd,this::openPhone);return;
            case "browser":menuItem(menu,"Internet Explorer",R.drawable.wm_browser,this::openBrowser);return;
            case "media":menuItem(menu,"Windows Media",R.drawable.wm_media,this::openMedia);return;
        }
        ComponentName app=ComponentName.unflattenFromString(id);if(app==null)return;
        try{android.content.pm.ActivityInfo info=getPackageManager().getActivityInfo(app,0);if(!info.exported||!info.enabled)return;
            menuItem(menu,info.loadLabel(getPackageManager()).toString(),R.drawable.wm_apps,()->launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app)));
            FrameLayout item=(FrameLayout)menu.getChildAt(menu.getChildCount()-1);((ImageView)item.getChildAt(0)).setImageDrawable(info.loadIcon(getPackageManager()));
        }catch(PackageManager.NameNotFoundException removed){}
    }
    private void renderModules(){
        if(body==null)return;
        java.util.Map<String,View[]> previous=new java.util.HashMap<>(todayRows);
        java.util.Set<String> missing=new java.util.HashSet<>();
        for(String id:previous.keySet())if(id.startsWith("module:"))missing.add(id);
        for(TodayModules.Row module:moduleRows){
            String key="module:"+module.module.authority+":"+module.id;
            View[] saved=previous.get(key);missing.remove(key);
            if(module.view!=null&&!(saved!=null&&saved[0] instanceof WmToday.Row&&saved[0].getTag()==module.view)){
                try{
                    FrameLayout custom;
                    if(saved!=null&&saved[0] instanceof FrameLayout&&saved[0].getTag()==module.view&&saved[0].getLayoutParams().height==module.height)custom=(FrameLayout)saved[0];
                    else {
                        custom=new FrameLayout(this);View content=module.view.apply(this,custom);
                        custom.addView(content,new FrameLayout.LayoutParams(me.ancal.wmdroid.sdk.TodayContract.WIDTH,-1));custom.setTag(module.view);
                    }
                    custom.setContentDescription(module.title+", "+module.detail);Intent action=modules.action(module);custom.setOnClickListener(action==null?null:v->openApplet(()->launch(action),custom));
                    todayList.register("module:"+module.module.authority+":"+module.id,custom,module.height,WmToday.Divider.BETWEEN);continue;
                }catch(RuntimeException unavailable){/* A stale layout after app replacement must not hide the text fallback. */}
            }
            String iconKey=module.module.pkg+":"+module.module.revision+":"+module.icon;
            WmToday.Row row;
            if(saved!=null&&saved[0] instanceof WmToday.Row&&iconKey.equals(((WmToday.Row)saved[0]).getChildAt(0).getTag()))row=(WmToday.Row)saved[0];
            else {
                ImageView image=icon(R.drawable.wm_apps);try{if(module.icon!=0)image.setImageDrawable(getPackageManager().getResourcesForApplication(module.module.pkg).getDrawable(module.icon));}catch(Exception removed){}
                image.setTag(iconKey);row=new WmToday.Row(this,image,regular,todayStyle(),"");
            }
            String caption=module.title+(module.detail.isEmpty()?"":"\n"+module.detail);
            row.setTag(module.view);
            if(!caption.contentEquals(row.caption.getText())||row.caption.getTag()!=null)row.setCaption(caption);
            // Keep the previous widget's space while its public preview is refreshed.
            int previewHeight=module.view==null?module.height:0;
            row.caption.setTag(previewHeight>0?Boolean.TRUE:null);
            row.setContentDescription(module.module.label+", "+module.title);Intent action=modules.action(module);row.setOnClickListener(action==null?null:v->openApplet(()->launch(action),row));
            todayList.register("module:"+module.module.authority+":"+module.id,row,Math.max(row.getLayoutParams().height,previewHeight),WmToday.Divider.BETWEEN);
        }
        for(String id:missing)todayList.remove(id);
        arrangeToday();
        for(UiDialog dialog=currentDialog;dialog!=null;dialog=dialog.previous)if(dialog.spec.view instanceof TodaySettingsPage){
            java.util.List<String> order=todayOrder();
            ((TodaySettingsPage)dialog.spec.view).updateItems(order,todayLabels(order.toArray(new String[0])));
        }
    }
    private void menuItem(LinearLayout menu, String label, int image, Runnable action) {
        menuItem(menu,label,image,action,true);
    }
    private void menuItem(LinearLayout menu, String label, int image, Runnable action,boolean delay) {
        FrameLayout item = new FrameLayout(this);
        StateListDrawable state=new StateListDrawable();state.addState(new int[]{android.R.attr.state_pressed},new android.graphics.drawable.ColorDrawable(LauncherTheme.selection(this,prefs.getBoolean("rose",false))));state.addState(new int[]{android.R.attr.state_selected},new android.graphics.drawable.ColorDrawable(LauncherTheme.selection(this,prefs.getBoolean("rose",false))));state.addState(new int[]{},new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));item.setBackgroundDrawable(state);
        FrameLayout.LayoutParams imagePosition=new FrameLayout.LayoutParams(32,32);imagePosition.leftMargin=8;imagePosition.topMargin=2;item.addView(icon(image),imagePosition);
        TextView text=vgaText(label,24,false);text.setTextColor(Color.WHITE);text.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.Paint.FontMetricsInt metrics=text.getPaint().getFontMetricsInt();int height=36;
        FrameLayout.LayoutParams caption=new FrameLayout.LayoutParams(-1,height-4);caption.leftMargin=48;caption.topMargin=2;item.addView(text,caption);
        item.setOnClickListener(view -> {if(delay)openApplet(()->{closeStart();action.run();},item);else{closeStart();action.run();}}); menu.addView(item,new LinearLayout.LayoutParams(-1,height));
    }
    private StateListDrawable menuPress() {return ClassicUi.background(Color.WHITE,LauncherTheme.selection(this,prefs.getBoolean("rose",false)));}
    private void clearPendingHighlight(){if(pendingHighlight!=null){pendingHighlight.setSelected(false);pendingHighlight=null;}}
    private void cancelApplet(){if(pendingApplet!=null){handler.removeCallbacks(pendingApplet);pendingApplet=null;}clearPendingHighlight();}
    private FrameLayout surface(){return new FrameLayout(this);}
    private RetroSurface paintContent(View content,RetroSurface.Role role){
        RetroSurface paint=new RetroSurface(this,delayedPresentation,role);
        paint.addView(content,new FrameLayout.LayoutParams(-1,-2));return paint;
    }
    /** Window chrome appears first; its contents have their own paint schedule. */
    private FrameLayout menuFrame(LinearLayout menu){
        android.graphics.drawable.Drawable background=menu.getBackground();
        if(background instanceof StartMenuBackground){
            // Native Start captures retain the old Today pixels below the painted
            // items. Reveal texture together with the items, with the frame first.
            GradientDrawable border=new GradientDrawable();border.setColor(Color.TRANSPARENT);border.setStroke(2,Color.BLACK);background=border;
        }else menu.setBackgroundDrawable(null);
        if(background instanceof android.graphics.drawable.ColorDrawable&&((android.graphics.drawable.ColorDrawable)background).getColor()==0xff808080){
            GradientDrawable border=new GradientDrawable();border.setColor(Color.WHITE);border.setStroke(2,0xff808080);background=border;
        }
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundDrawable(background);frame.setOnClickListener(v->{});
        frame.addView(paintContent(menu,RetroSurface.Role.MENU),new FrameLayout.LayoutParams(-1,-2));return frame;
    }
    private void openApplet(Runnable action){openApplet(action,null);}
    private void openApplet(Runnable action,View highlight){
        cancelApplet();int delay=RetroSurface.enabled(this)?RetroSurface.delayMillis(this):0;
        if(delay==0){action.run();return;}
        pendingHighlight=highlight;if(highlight!=null)highlight.setSelected(true);
        pendingApplet=()->{pendingApplet=null;clearPendingHighlight();if(!isFinishing()&&!destroyed){delayedPresentation=true;try{action.run();}finally{delayedPresentation=false;}}};handler.postDelayed(pendingApplet,delay);
    }

    private void programs() {
        ProgramsPage panel=new ProgramsPage(this,regular,prefs.getBoolean("rose",false),worker,new ProgramsPage.Host(){
            public void launch(ResolveInfo info){
                openApplet(()->{closePage();
                    TodayActivity.this.launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(new ComponentName(info.activityInfo.packageName,info.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));});
            }
            public void builtin(String command){openApplet(()->{closePage();runShellCommand(command);});}
            public void details(ResolveInfo info){
                TodayActivity.this.launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+info.activityInfo.packageName)));
            }
        });
        UiDialog window=dialog("프로그램",panel).setPositiveButton("찾기",null).setNegativeButton("닫기",null).create();
        show(window);window.getButton(UiDialog.BUTTON_POSITIVE).setOnClickListener(v->panel.showSearch());
    }

    private EditText input(String hint, String value) {
        EditText edit = new EditText(this);ClassicUi.input(edit,regular);edit.setHintTextColor(0xff707070);
        edit.setPadding(6,4,6,4);me.ancal.wmdroid.sdk.WmControls.bindHint(edit,UiLanguage.language(this),hint);edit.setText(value);edit.setSingleLine(true);edit.setSelectAllOnFocus(true);return edit;
    }
    private UiDialog.Builder dialog(String title, View view) { return new UiDialog.Builder(this).setTitle(title).setView(view); }
    private void show(UiDialog dialog) { if(!contactSaving())dialog.show(); }
    private void editOwner() {
        OwnerPage editor=new OwnerPage(this,prefs,regular,this::refresh);
        show(dialog("소유자 정보",editor).setPropertySheet(editor::save).create());
    }

    private void showOwnerOnWake() {
        StringBuilder info=new StringBuilder();
        if(prefs.getBoolean("owner_show_identity",false)){
            String[] keys={"owner","owner_company","owner_address","owner_phone","owner_email"};
            for(String key:keys){String value=prefs.getString(key,"");if(!value.isEmpty()){if(info.length()>0)info.append("\n");info.append(value);}}
        }
        if(prefs.getBoolean("owner_show_notes",false)){String note=prefs.getString("owner_notes","");if(!note.isEmpty()){if(info.length()>0)info.append("\n\n");info.append(note);}}
        if(info.length()>0)show(new UiDialog.Builder(this).setTitle("소유자 정보").setMessage(info.toString()).setNegativeButton("닫기",null).create());
    }

    private JSONArray readTasks() {
        try {
            JSONArray stored = new JSONArray(prefs.getString("tasks", "[]"));
            JSONArray valid = new JSONArray();
            for (int i = 0; i < stored.length(); i++) {
                JSONObject item = stored.optJSONObject(i);
                if (item != null && !item.optString("title").trim().isEmpty()) valid.put(item);
            }
            return valid;
        } catch (JSONException invalid) { return new JSONArray(); }
    }
    private void saveTasks(JSONArray tasks) { prefs.edit().putString("tasks", tasks.toString()).apply(); refresh(); }
    private void showTasks() {
        // Replacing an edited list must not expose its stale JSON snapshot on Back.
        if (currentDialog != null && currentDialog.spec.title.equals("작업")) currentDialog.dismiss();
        JSONArray saved = readTasks(); String[] labels = new String[saved.length()]; boolean[] checked = new boolean[saved.length()];
        for (int i = 0; i < saved.length(); i++) { JSONObject item=saved.optJSONObject(i); labels[i] = item.optString("title") + (!item.optString("due").isEmpty()?"\n"+item.optString("due"):"") + (!item.optString("category").isEmpty()?" · "+item.optString("category"):"") + (item.optInt("priority")!=0?" · "+priorityName(item.optInt("priority")):""); checked[i] = saved.optJSONObject(i).optBoolean("done"); }
        UiDialog.Builder builder = new UiDialog.Builder(this).setTitle("작업").setMultiChoiceItems(labels, checked, (d, which, done) -> {
            try { JSONArray latest=TaskReminders.latest(prefs,saved);latest.getJSONObject(which).put("done",done);saveTasks(latest);saved.getJSONObject(which).put("done",done); } catch (JSONException invalid) { toast("작업을 저장하지 못했습니다.");showTasks(); }
        }).setLongClick((d,w)->editTask(w)).setPositiveButton("새 작업", (d, which) -> addTask()).setNeutralButton("완료된 작업 삭제", (d, which) -> {
            try{JSONArray latest=TaskReminders.latest(prefs,saved),keep=new JSONArray();for(int i=0;i<latest.length();i++)if(!latest.getJSONObject(i).optBoolean("done"))keep.put(latest.getJSONObject(i));saveTasks(keep);}catch(JSONException invalid){toast("작업을 저장하지 못했습니다.");}showTasks();
        }).setNegativeButton("닫기", null);
        show(builder.create());
    }
    private void addTask() { editTask(-1); }
    private void editTask(int index) {
        JSONArray saved=readTasks(); JSONObject old=index>=0?saved.optJSONObject(index):new JSONObject();
        final me.ancal.wmdroid.sdk.WmTaskEditor form;
        try {form=new me.ancal.wmdroid.sdk.WmTaskEditor(this,regular,UiLanguage.locale(this),new me.ancal.wmdroid.sdk.WmTaskEditor.Value(old.optString("title"),old.optInt("priority"),old.optBoolean("done"),me.ancal.wmdroid.sdk.WmDateField.parseValue(old.optString("start")),me.ancal.wmdroid.sdk.WmDateField.parseValue(old.optString("due")),old.optString("category"),old.optInt("sensitivity"),old.optString("notes"),readTaskReminder(old)),accent,LauncherTheme.color(this,0xffc9e7e9,prefs.getBoolean("rose",false)));}
        catch(IllegalArgumentException invalid){toast("작업의 날짜 또는 상태가 올바르지 않습니다.");return;}
        final String[][] categoryNames;try{categoryNames=new String[][]{taskCategoryNames(saved)};}catch(IllegalArgumentException invalid){toast("작업 범주를 읽을 수 없습니다.");return;}
        form.setOnCategoriesOpen(()->taskCategories(form,categoryNames));
        UiDialog.Builder builder=dialog(index<0?"새 작업":"작업 편집",form).setPositiveButton("확인",null).setNegativeButton("취소",null);
        if(index>=0)builder.setNeutralButton("작업 삭제",(d,w)->{try{JSONArray latest=TaskReminders.latest(prefs,saved),keep=new JSONArray();for(int i=0;i<latest.length();i++)if(i!=index)keep.put(latest.getJSONObject(i));saveTasks(keep);}catch(JSONException invalid){toast("작업을 저장하지 못했습니다.");}showTasks();});
        UiDialog dialog=builder.create();show(dialog);
        dialog.getButton(UiDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            form.closePopup();me.ancal.wmdroid.sdk.WmTaskEditor.Value draft;try{draft=form.value();}catch(IllegalArgumentException invalid){toast("작업을 저장하지 못했습니다.");return;}String name=draft.subject.trim();if(name.isEmpty()){toast("작업 이름을 입력하세요");return;}
            try { JSONArray latest=TaskReminders.latest(prefs,saved);JSONObject target=index<0?new JSONObject():latest.getJSONObject(index);target.put("title",name).put("start",me.ancal.wmdroid.sdk.WmDateField.formatValue(draft.start)).put("due",me.ancal.wmdroid.sdk.WmDateField.formatValue(draft.due)).put("category",draft.categories).put("priority",draft.priority).put("done",draft.completed).put("sensitivity",draft.sensitivity).put("notes",draft.notes);TaskReminders.write(target,draft.reminder);if(index<0)latest.put(target);prefs.edit().putString("task_categories",new JSONArray(java.util.Arrays.asList(categoryNames[0])).toString()).putString("tasks",latest.toString()).apply();refresh();dialog.dismiss();showTasks();if(draft.reminder!=null&&!TaskReminders.allowed(this))toast("작업 알림을 받으려면 작업 옵션에서 알림을 허용하세요."); } catch(JSONException invalid){toast("작업을 저장하지 못했습니다.");}
        });
    }
    private boolean taskHasCategory(String selected,String name){try{return me.ancal.wmdroid.sdk.WmCategoryPicker.contains(selected,name,UiLanguage.locale(this));}catch(IllegalArgumentException invalid){return false;}}
    private String[] taskCategoryNames(JSONArray tasks){
        java.util.LinkedHashSet<String> names=new java.util.LinkedHashSet<String>();JSONArray stored;
        try{stored=new JSONArray(prefs.getString("task_categories","[]"));}catch(JSONException invalid){throw new IllegalArgumentException("Task category catalog requires an array",invalid);}
        for(int i=0;i<stored.length();i++){Object value=stored.opt(i);if(!(value instanceof String)||((String)value).isEmpty()||((String)value).indexOf('\0')>=0||((String)value).indexOf(',')>=0||((String)value).indexOf(';')>=0)throw new IllegalArgumentException("Task category catalog requires names");names.add((String)value);}
        for(int i=0;i<tasks.length();i++){JSONObject task=tasks.optJSONObject(i);if(task==null)continue;String raw=task.optString("category");if(raw.indexOf('\0')>=0)continue;
            for(String part:raw.split("[,;]")){int left=0,right=part.length();while(left<right&&part.charAt(left)==' ')left++;while(right>left&&part.charAt(right-1)==' ')right--;if(right>left)names.add(part.substring(left,right));}}
        return names.toArray(new String[names.size()]);
    }
    private void taskCategories(me.ancal.wmdroid.sdk.WmTaskEditor form,String[][] catalog){
        me.ancal.wmdroid.sdk.WmCategoryPicker picker=new me.ancal.wmdroid.sdk.WmCategoryPicker(this,regular,UiLanguage.locale(this),catalog[0],form.categories());
        final boolean[] cancelled={false};final UiDialog[] owner=new UiDialog[1];Runnable cancel=()->{cancelled[0]=true;owner[0].dismiss();};
        owner[0]=dialog("작업",picker).setPropertySheet(()->{if(!cancelled[0]){if(picker.result()!=null)form.setCategories(picker.result());catalog[0]=picker.categoryNames();}}).setOnBack(cancel).setCommands("새로 만들기",()->newTaskCategory(picker),"취소",cancel).create();show(owner[0]);
    }
    private void newTaskCategory(me.ancal.wmdroid.sdk.WmCategoryPicker picker){
        me.ancal.wmdroid.sdk.WmCategoryNameForm panel=new me.ancal.wmdroid.sdk.WmCategoryNameForm(this,regular,bold,accent);EditText name=panel.nameField();
        final boolean[] cancelled={false};final UiDialog[] owner=new UiDialog[1];Runnable cancel=()->{if(me.ancal.wmdroid.sdk.WmControls.blocksOwnerCommands(panel))return;cancelled[0]=true;owner[0].dismiss();};
        owner[0]=dialog("작업",panel).setCloseIsOk().setOnBack(cancel).setCloseValidator(()->{if(cancelled[0])return true;try{String raw=name.getText().toString();if(!raw.replace(" ","").isEmpty())picker.addCategory(raw);return true;}catch(IllegalArgumentException invalid){panel.showNameWarning(null);return false;}}).setCommands("category.new.done",()->owner[0].dismiss(),"취소",cancel).create();show(owner[0]);
    }

    private me.ancal.wmdroid.sdk.WmDateTimeField.Value readTaskReminder(JSONObject task){return TaskReminders.read(task);}
    private String priorityName(int value) {return ui(value>0?"높음":value<0?"낮음":"보통");}
    private void taskOptions() {
        int flags=prefs.getInt("task_flags",1);boolean[] checked={(flags&1)!=0,(flags&2)!=0,(flags&4)!=0}; EditText category=input("모든 작업 (빈 값) 또는 분류",prefs.getString("task_category",""));
        LinearLayout panel=column();panel.setPadding(8,8,8,0);String[] labels={"우선 순위 높은 작업","지연된 작업","오늘로 예정된 작업"};
        for(int i=0;i<3;i++){final int n=i;android.widget.CheckBox check=new android.widget.CheckBox(this);ClassicUi.check(check,regular);ClassicUi.entry(check,prefs.getBoolean("rose",false));me.ancal.wmdroid.sdk.WmControls.bindText(check,UiLanguage.language(this),labels[i]);check.setTypeface(regular);check.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,24);check.setChecked(checked[i]);check.setOnCheckedChangeListener((v,c)->{checked[n]=c;prefs.edit().putInt("task_flags",(checked[0]?1:0)|(checked[1]?2:0)|(checked[2]?4:0)).apply();});panel.addView(check,new LinearLayout.LayoutParams(-1,44));}panel.addView(category,new LinearLayout.LayoutParams(-1,44));
        category.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){prefs.edit().putString("task_category",s.toString().trim()).apply();}public void afterTextChanged(android.text.Editable s){}});
        show(dialog("작업 옵션",panel).setPropertySheet(null).setNeutralButton("알림 설정",(d,w)->TaskReminders.requestAccess(this)).create());
    }
    private String[] todayLabels(String[] keys){String[] names=keys.clone();for(int i=0;i<keys.length;i++){if(keys[i].startsWith("module:")){for(TodayModules.Row row:moduleRows)if(keys[i].equals("module:"+row.module.authority+":"+row.id)){names[i]=row.module.label+": "+row.title;break;}}else names[i]=ui(keys[i]);}return names;}
    private void todayItems() {
        todaySettings(1);
    }

    private Bundle notesPropertiesState;
    private me.ancal.wmdroid.sdk.WmDocumentProperties notesPropertiesForm;
    private boolean restoreNotes(Bundle state){
        if(state==null||state.getBundle("notes_draft")==null&&state.getBundle("notes_properties")==null&&state.getBundle("notes_list")==null)return false;
        openNotes(state.getBundle("notes_draft"));final Bundle properties=state.getBundle("notes_properties");
        if(currentDialog!=null&&currentDialog.spec.view instanceof NotesPage){NotesPage page=(NotesPage)currentDialog.spec.view;
            if(properties!=null)page.whenLoaded(()->{if(state.getBundle("notes_list")!=null)page.restoreList(state.getBundle("notes_list"));String key=properties.getString("id"),title=properties.getString("title"),name=properties.getString("name");long revision=properties.getLong("revision");
                if(key!=null&&!key.isEmpty()&&key.length()<=128&&title!=null&&title.length()<=256&&name!=null&&name.length()<=256&&revision>0)noteProperties(page,new NotesStore.Entry(key,title,"",properties.getLong("modified"),revision,properties.getLong("size"),properties.getString("folder","")),properties);
            });else if(state.getBundle("notes_list")!=null)page.restoreList(state.getBundle("notes_list"));
        }return true;
    }
    private me.ancal.wmdroid.sdk.WmMediaView mediaPicker;
    private boolean mediaPickerAppend;
    private MediaLibraryPage mediaLibrary;
    private void openMedia(){
        if(openAppletTarget("media"))return;
        for(UiDialog d=currentDialog;d!=null;d=d.previous)if(d.spec.view instanceof me.ancal.wmdroid.sdk.WmMediaView){while(currentDialog!=d)if(!currentDialog.dismiss())return;return;}
        final me.ancal.wmdroid.sdk.WmMediaView view=new me.ancal.wmdroid.sdk.WmMediaView(this,regular,bold);
        final MediaLibraryPage library=new MediaLibraryPage(this,view,worker,handler,this::toast);mediaLibrary=library;
        final UiDialog[] owner=new UiDialog[1];owner[0]=dialog("Windows Media",view).setCloseValidator(()->!view.back()).setCommands("지금 재생",()->{if(view.isPropertiesVisible())view.back();else if(view.isLibraryVisible())library.open();else if(view.isPlaylistVisible())view.playSelection();else view.showPlaylist(true);},"메뉴",()->mediaCommands(view)).setCommandMenus(false,true).create();
        view.setListener(player->{boolean ok=player.isPropertiesVisible()||player.isPlaylistVisible()||player.isLibraryVisible();String caption=player.isPropertiesVisible()?"확인":ok?"재생":"지금 재생";if(owner[0].spec.closeIsOk!=ok||!caption.equals(owner[0].spec.leftCaption)){owner[0].spec.closeIsOk=ok;owner[0].spec.leftCaption=caption;owner[0].restyle();}});
        view.setPlaylistCommandListener(id->{if(id==30009)library.show();else if(id==20001)library.open();else if(id==-1)library.back();else if(id==-2)commandMenu(true,new String[]{ui("라이브러리")},-1,n->library.show());else if(id==30006)mediaSavePlaylist(view);else if(id==30010)mediaProperties(view,true);else if(id==0)mediaCommands(view);});
        owner[0].spec.onClose=()->{if(owner[0].dismissed){if(mediaPicker==view)mediaPicker=null;view.close();}else view.pause();};show(owner[0]);library.show();
    }
    private void mediaOpenFile(me.ancal.wmdroid.sdk.WmMediaView view){
        mediaPicker=view;mediaPickerAppend=view.isPlaylistVisible();Intent picker=new Intent(Build.VERSION.SDK_INT>=19?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if(Build.VERSION.SDK_INT>=18)picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        if(Build.VERSION.SDK_INT>=19)picker.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION).putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/*","video/*"});
        try{startActivityForResult(picker,103);overridePendingTransition(0,0);}catch(RuntimeException absent){mediaPicker=null;toast("파일 선택기를 열 수 없습니다.");}
    }
    private void mediaPickerResult(me.ancal.wmdroid.sdk.WmMediaView target,Intent data,boolean append){mediaPickerResult(target,data,append,this);}
    private void mediaPickerResult(me.ancal.wmdroid.sdk.WmMediaView target,Intent data,boolean append,android.content.Context libraryContext){
        java.util.ArrayList<Uri> uris=new java.util.ArrayList<>();android.content.ClipData clip=Build.VERSION.SDK_INT>=16?data.getClipData():null;if(clip!=null){for(int n=0;n<clip.getItemCount();n++)uris.add(clip.getItemAt(n).getUri());}else if(data.getData()!=null)uris.add(data.getData());if(uris.isEmpty())return;
        // Validate every URI before permissions, provider queries or queue mutation.
        try{for(Uri uri:uris)new me.ancal.wmdroid.sdk.WmMediaQueue.Entry(uri,"");}catch(IllegalArgumentException invalid){toast("미디어를 열 수 없습니다.");return;}
        worker.execute(()->{
            final me.ancal.wmdroid.sdk.WmMediaQueue.Entry[] entries=new me.ancal.wmdroid.sdk.WmMediaQueue.Entry[uris.size()];boolean temporary=false;
            try{for(int n=0;n<uris.size();n++){Uri selected=uris.get(n);String name=selected.getLastPathSegment();try(android.database.Cursor cursor=getContentResolver().query(selected,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst()&&!cursor.isNull(0))name=cursor.getString(0);}catch(RuntimeException unavailable){}
                if(Build.VERSION.SDK_INT>=19&&"content".equals(selected.getScheme())){try{if((data.getFlags()&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)==0)throw new SecurityException("Temporary grant");getContentResolver().takePersistableUriPermission(selected,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(RuntimeException denied){temporary=true;}}else temporary=true;
                entries[n]=new me.ancal.wmdroid.sdk.WmMediaQueue.Entry(selected,name);
            }}catch(RuntimeException invalid){handler.post(()->toast("미디어를 열 수 없습니다."));return;}
            final boolean transientGrant=temporary;handler.post(()->{if(destroyed||target.state()==me.ancal.wmdroid.sdk.WmMediaView.State.CLOSED)return;if(append){boolean empty=target.playlist().length==0;target.enqueue(entries);if(empty)target.playIndex(0);}else {target.setPlaylist(entries,0,true);target.showLibrary(false);}if(transientGrant)toast("일부 파일은 다시 열 때 선택 권한이 필요할 수 있습니다.");});
            try(MediaPlaylistStore store=new MediaPlaylistStore(libraryContext)){store.remember(libraryContext,entries);}catch(RuntimeException unavailable){handler.post(()->toast("라이브러리를 갱신할 수 없습니다."));}
        });
    }
    private void mediaSavePlaylist(me.ancal.wmdroid.sdk.WmMediaView view){
        final me.ancal.wmdroid.sdk.WmMediaQueue.Entry[] entries=view.playlist();if(entries.length==0)return;
        if(me.ancal.wmdroid.sdk.WmControls.blocksOwnerCommands(view))return;
        final me.ancal.wmdroid.sdk.WmInputDialog[] owner=new me.ancal.wmdroid.sdk.WmInputDialog[1];
        owner[0]=new me.ancal.wmdroid.sdk.WmInputDialog(this,regular,bold,"재생 목록 이름 입력:",accent,128,value->{
            owner[0].setBusy(true);worker.execute(()->{Exception failure=null;try(MediaPlaylistStore store=new MediaPlaylistStore(this)){store.save(value,entries);}catch(Exception error){failure=error;}final Exception error=failure;
                handler.post(()->{if(destroyed||owner[0].isClosed())return;owner[0].setBusy(false);if(error==null)owner[0].dismiss();else toast(error instanceof android.database.sqlite.SQLiteConstraintException?"같은 이름의 재생 목록이 있습니다.":"재생 목록을 저장할 수 없습니다.");});});
        });
        final UiDialog host=currentDialog;
        host.footer.setVisibility(View.GONE);
        ((me.ancal.wmdroid.sdk.WmTitleBar)host.title).closeButton(true);
        owner[0].onDismiss(()->{host.footer.setVisibility(View.VISIBLE);((me.ancal.wmdroid.sdk.WmTitleBar)host.title).closeButton(host.spec.closeIsOk);});
        owner[0].onWmLanguageChanged(UiLanguage.language(this));view.addView(owner[0],new FrameLayout.LayoutParams(-1,-1));
    }
    private void mediaProperties(me.ancal.wmdroid.sdk.WmMediaView view,boolean selected){
        int index=selected?view.selectedIndex():view.currentIndex();me.ancal.wmdroid.sdk.WmMediaQueue.Entry[] entries=view.playlist();if(index<0||index>=entries.length)return;
        view.showProperties(index);
    }
    private void mediaSavedPlaylists(me.ancal.wmdroid.sdk.WmMediaView view){if(mediaLibrary!=null)mediaLibrary.playlists();}
    private void mediaCommands(me.ancal.wmdroid.sdk.WmMediaView view){
        if(view.isPropertiesVisible()){commandMenu(true,view.properties().menuItems(),id->{});return;}
        if(view.isLibraryVisible()){
            MediaLibraryPage library=mediaLibrary;boolean selected=view.library().selected()!=null;
            commandMenu(true,new me.ancal.wmdroid.sdk.WmMenuItem[]{
                me.ancal.wmdroid.sdk.WmMenuItem.command(20001,ui("재생")).withEnabled(selected),
                me.ancal.wmdroid.sdk.WmMenuItem.command(20002,ui("지금 재생에 추가")).withEnabled(library.canDelete()),
                me.ancal.wmdroid.sdk.WmMenuItem.command(20010,ui("라이브러리에서 삭제")).withEnabled(library.canDelete()),
                me.ancal.wmdroid.sdk.WmMenuItem.command(20011,ui("지금 재생")),
                me.ancal.wmdroid.sdk.WmMenuItem.command(20007,ui("라이브러리 업데이트...")),
                me.ancal.wmdroid.sdk.WmMenuItem.command(20008,ui("파일 열기..."))
            },id->{if(id==20001)library.open();else if(id==20002)library.queueSelected();else if(id==20010)library.deleteSelected();else if(id==20011)view.showPlaylist(true);else if(id==20007)library.update();else if(id==20008)mediaOpenFile(view);});return;
        }
        if(view.isPlaylistVisible()){
            int selected=view.selectedIndex(),count=view.playlist().length;
            commandMenu(true,new me.ancal.wmdroid.sdk.WmMenuItem[]{
                me.ancal.wmdroid.sdk.WmMenuItem.command(30001,ui("재생")).withEnabled(selected>=0),
                me.ancal.wmdroid.sdk.WmMenuItem.command(30009,ui("라이브러리...")),
                me.ancal.wmdroid.sdk.WmMenuItem.command(30002,ui("위로 이동")).withEnabled(selected>0),
                me.ancal.wmdroid.sdk.WmMenuItem.command(30003,ui("아래로 이동")).withEnabled(selected>=0&&selected<count-1),
                me.ancal.wmdroid.sdk.WmMenuItem.command(30004,ui("제거")).withEnabled(selected>=0),
                me.ancal.wmdroid.sdk.WmMenuItem.command(30006,ui("재생 목록 저장...")).withEnabled(count>0),
                me.ancal.wmdroid.sdk.WmMenuItem.command(-30006,ui("내 재생 목록")),
                me.ancal.wmdroid.sdk.WmMenuItem.checked(30007,ui("임의 재생"),view.shuffle()),
                me.ancal.wmdroid.sdk.WmMenuItem.checked(30008,ui("반복"),view.repeat()),
                me.ancal.wmdroid.sdk.WmMenuItem.command(30005,ui("지우기")).withEnabled(count>0),
                me.ancal.wmdroid.sdk.WmMenuItem.command(30010,ui("속성")).withEnabled(selected>=0)
            },id->{if(id==30001)view.playSelection();else if(id==30009)mediaLibrary.show();else if(id==30002||id==30003){int to=selected+(id==30002?-1:1);view.moveSource(selected,to);view.selectPlaylist(to);}else if(id==30006)mediaSavePlaylist(view);else if(id==-30006)mediaSavedPlaylists(view);else if(id==30004)view.removeSource(selected);else if(id==30007)view.setShuffle(!view.shuffle());else if(id==30008)view.setRepeat(!view.repeat());else if(id==30005)view.clearPlaylist();else if(id==30010)mediaProperties(view,true);});return;
        }
        boolean ready=view.state()==me.ancal.wmdroid.sdk.WmMediaView.State.READY||view.state()==me.ancal.wmdroid.sdk.WmMediaView.State.PLAYING||view.state()==me.ancal.wmdroid.sdk.WmMediaView.State.PAUSED||view.state()==me.ancal.wmdroid.sdk.WmMediaView.State.ENDED;
        commandMenu(true,new me.ancal.wmdroid.sdk.WmMenuItem[]{
            me.ancal.wmdroid.sdk.WmMenuItem.command(10005,ui("라이브러리...")),
            me.ancal.wmdroid.sdk.WmMenuItem.command(20008,ui("파일 열기...")),
            me.ancal.wmdroid.sdk.WmMenuItem.command(-30006,ui("내 재생 목록")),
            me.ancal.wmdroid.sdk.WmMenuItem.command(10002,ui("재생/일시 중지")).withEnabled(ready),
            me.ancal.wmdroid.sdk.WmMenuItem.command(10004,ui("정지")).withEnabled(ready),
            me.ancal.wmdroid.sdk.WmMenuItem.checked(10003,ui("반복"),view.repeat()),
            me.ancal.wmdroid.sdk.WmMenuItem.checked(10011,ui("전체 화면"),view.fullScreen()).withEnabled(view.hasVideo()),
            me.ancal.wmdroid.sdk.WmMenuItem.command(10008,ui("속성")).withEnabled(ready)
        },id->{if(id==10005)mediaLibrary.show();else if(id==20008)mediaOpenFile(view);else if(id==-30006)mediaSavedPlaylists(view);else if(id==10002)view.toggle();else if(id==10004)view.stop();else if(id==10003)view.setRepeat(!view.repeat());else if(id==10011)view.setFullScreen(!view.fullScreen());else if(id==10008)mediaProperties(view,false);});
    }
    private void openBrowser(){
        if(openAppletTarget("browser"))return;
        for(UiDialog d=currentDialog;d!=null;d=d.previous)if(d.spec.view instanceof me.ancal.wmdroid.sdk.WmBrowserView){while(currentDialog!=d)if(!currentDialog.dismiss())return;return;}
        final me.ancal.wmdroid.sdk.WmBrowserView view;
        try{view=new me.ancal.wmdroid.sdk.WmBrowserView(this,regular);}catch(RuntimeException unavailable){show(dialog("Internet Explorer",null).setAuthoredMessage("Android WebView를 사용할 수 없습니다.").create());return;}
        final UiDialog[] owner=new UiDialog[1];owner[0]=dialog("Internet Explorer",view).setCommands("뒤로",view::back,"메뉴",()->browserCommands(view)).setCommandMenus(false,true).create();
        owner[0].spec.onClose=()->{if(owner[0].dismissed)view.close();else view.pause();};
        final String[] recorded={""};
        view.setListener(new me.ancal.wmdroid.sdk.WmBrowserView.Listener(){public void changed(me.ancal.wmdroid.sdk.WmBrowserView browser){
            if(browser.isLoading()){recorded[0]="";return;}final String url=browser.url(),title=browser.pageTitle();
            if(!browserWebAddress(url))return;
            String signature=url+"\n"+title;if(signature.equals(recorded[0]))return;recorded[0]=signature;
            worker.execute(()->{try(BrowserStore store=new BrowserStore(TodayActivity.this)){store.visit(title,url);}catch(RuntimeException failure){handler.post(()->{if(!destroyed){if(signature.equals(recorded[0]))recorded[0]="";toast("열어 본 페이지 목록을 저장하지 못했습니다.");}});}});
        }public void invalidAddress(String address){toast("올바른 웹 주소를 입력하십시오.");}});
        show(owner[0]);view.navigate("about:blank");
    }
    private static boolean browserWebAddress(String url){return url.regionMatches(true,0,"http://",0,7)||url.regionMatches(true,0,"https://",0,8);}
    private void browserCommands(me.ancal.wmdroid.sdk.WmBrowserView view){
        commandMenu(true,new me.ancal.wmdroid.sdk.WmMenuItem[]{
            me.ancal.wmdroid.sdk.WmMenuItem.command(2206,ui("홈")),
            me.ancal.wmdroid.sdk.WmMenuItem.command(2013,ui("주소 표시줄")),
            me.ancal.wmdroid.sdk.WmMenuItem.command(2000,ui("즐겨찾기...")),
            me.ancal.wmdroid.sdk.WmMenuItem.command(2009,ui("즐겨찾기에 추가...")).withEnabled(browserWebAddress(view.url())),
            me.ancal.wmdroid.sdk.WmMenuItem.command(2205,ui("앞으로")).withEnabled(view.canForward()),
            me.ancal.wmdroid.sdk.WmMenuItem.command(2203,ui("새로 고침")),
            me.ancal.wmdroid.sdk.WmMenuItem.command(2201,ui("열어 본 페이지 목록...")),
            me.ancal.wmdroid.sdk.WmMenuItem.command(-1,ui("정지")).withEnabled(view.isLoading())
        },id->{if(id==2206)view.navigate("about:blank");else if(id==2013)view.showAddress(!view.isAddressVisible());else if(id==2205)view.forward();else if(id==2203)view.refresh();else if(id==-1)view.stop();else if(id==2000)browserRecords(view,false);else if(id==2009)browserAddFavorite(view);else if(id==2201)browserRecords(view,true);});
    }
    private void browserAddFavorite(me.ancal.wmdroid.sdk.WmBrowserView browser){browserAddFavorite(browser,0,null);}
    private String[] browserFolderNames(java.util.List<BrowserStore.Folder> folders){
        String[] names=new String[folders.size()+1];names[0]=ui("즐겨찾기");java.util.Map<Long,BrowserStore.Folder> byId=new java.util.HashMap<>();for(BrowserStore.Folder folder:folders)byId.put(folder.id,folder);
        for(int n=0;n<folders.size();n++){String path="";BrowserStore.Folder folder=folders.get(n);java.util.HashSet<Long> seen=new java.util.HashSet<>();
            while(folder!=null){if(!seen.add(folder.id))throw new IllegalStateException("Cyclic favorite folder");path=folder.name+(path.isEmpty()?"":" / "+path);long parent=folder.parent;folder=parent==0?null:byId.get(parent);if(parent!=0&&folder==null)throw new IllegalStateException("Favorite parent missing");}names[n+1]=path;
        }return names;
    }
    private void browserAddFavorite(me.ancal.wmdroid.sdk.WmBrowserView browser,long destination,Runnable saved){browserFavoriteForm(browser,destination,null,saved);}
    private void browserNameCommand(UiDialog owner,EditText name,boolean[] saving){
        name.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence text,int start,int count,int after){}public void onTextChanged(CharSequence text,int start,int before,int count){owner.positive.setEnabled(!saving[0]&&name.length()>0);}public void afterTextChanged(android.text.Editable text){}});
        owner.positive.setEnabled(!saving[0]&&name.length()>0);
    }
    private void browserFavoriteForm(me.ancal.wmdroid.sdk.WmBrowserView browser,long destination,BrowserStore.Entry editing,Runnable saved){
        final UiDialog origin=currentDialog;
        worker.execute(()->{try(BrowserStore store=new BrowserStore(this)){final java.util.List<BrowserStore.Folder> folders=store.folders();
            handler.post(()->{if(destroyed||currentDialog!=origin)return;String[] labels=browserFolderNames(folders);int selected=0;for(int n=0;n<folders.size();n++)if(folders.get(n).id==destination)selected=n+1;
                me.ancal.wmdroid.sdk.WmBrowserFavoriteForm form=new me.ancal.wmdroid.sdk.WmBrowserFavoriteForm(this,regular,editing==null?browser.pageTitle():editing.name,editing==null?browser.url():editing.url,labels,selected,accent);
                EditText name=form.nameField(),url=form.urlField();form.locationField().setEnabled(true);
                final boolean[] saving={false};final UiDialog[] owner={null};
                owner[0]=dialog(editing==null?"즐겨찾기 추가":"즐겨찾기 편집",form).setCloseValidator(()->!saving[0]).setCommands(editing==null?"추가":"확인",()->{
                    if(saving[0])return;final String title=name.getText().toString().trim(),address;final int index=form.locationField().selectedIndex();final long folder=index==0?0:folders.get(index-1).id;
                    try{address=me.ancal.wmdroid.sdk.WmBrowserView.normalizeAddress(url.getText().toString());if("about:blank".equals(address))throw new IllegalArgumentException();}catch(IllegalArgumentException invalid){toast("올바른 웹 주소를 입력하십시오.");url.requestFocus();return;}
                    saving[0]=true;owner[0].positive.setEnabled(false);name.setEnabled(false);url.setEnabled(false);form.locationField().setEnabled(false);
                    worker.execute(()->{try(BrowserStore changes=new BrowserStore(this)){if(editing==null)changes.add(title,address,folder);else changes.updateFavorite(editing.id,title,address,folder);handler.post(()->{if(destroyed)return;saving[0]=false;owner[0].positive.setEnabled(name.length()>0);name.setEnabled(true);url.setEnabled(true);form.locationField().setEnabled(true);if(currentDialog==owner[0]){owner[0].dismiss();if(saved!=null)saved.run();}});}
                        catch(RuntimeException failure){handler.post(()->{if(destroyed)return;saving[0]=false;owner[0].positive.setEnabled(name.length()>0);name.setEnabled(true);url.setEnabled(true);form.locationField().setEnabled(true);toast("즐겨찾기를 저장하지 못했습니다.");});}});
                },"취소",()->owner[0].dismiss()).setOnLanguageChanged(()->form.locationField().setChoices(browserFolderNames(folders),form.locationField().selectedIndex())).create();show(owner[0]);browserNameCommand(owner[0],name,saving);if(editing!=null){name.requestFocus();name.selectAll();}
            });
        }catch(RuntimeException failure){handler.post(()->{if(!destroyed&&currentDialog==origin)toast("브라우저 항목을 읽을 수 없습니다.");});}});
    }
    private void browserNewFolder(long parent,Runnable saved){browserFolderForm(parent,0,"",saved);}
    private void browserFolderForm(long parent,long editing,String draft,Runnable saved){
        me.ancal.wmdroid.sdk.WmBrowserFolderForm form=new me.ancal.wmdroid.sdk.WmBrowserFolderForm(this,regular,draft);final boolean[] saving={false};final UiDialog[] owner={null};
        owner[0]=dialog(editing==0?"새 폴더":"폴더 편집",form).setCloseValidator(()->!saving[0]).setCommands(editing==0?"추가":"확인",()->{
            if(saving[0])return;String title=form.nameField().getText().toString().trim();if(!me.ancal.wmdroid.sdk.WmDocumentProperties.validName(title)){toast("올바른 이름을 입력하세요(최대 128자).");form.nameField().requestFocus();return;}
            saving[0]=true;owner[0].positive.setEnabled(false);form.nameField().setEnabled(false);worker.execute(()->{try(BrowserStore store=new BrowserStore(this)){if(editing==0)store.createFolder(parent,title);else store.renameFolder(editing,title);handler.post(()->{if(destroyed)return;saving[0]=false;owner[0].positive.setEnabled(form.nameField().length()>0);form.nameField().setEnabled(true);if(currentDialog==owner[0]){owner[0].dismiss();saved.run();}});}catch(RuntimeException failure){handler.post(()->{if(destroyed)return;saving[0]=false;owner[0].positive.setEnabled(form.nameField().length()>0);form.nameField().setEnabled(true);toast("즐겨찾기를 저장하지 못했습니다.");});}});
        },"취소",()->owner[0].dismiss()).create();show(owner[0]);browserNameCommand(owner[0],form.nameField(),saving);form.nameField().requestFocus();if(editing!=0)form.nameField().selectAll();
    }
    private void browserFavorites(me.ancal.wmdroid.sdk.WmBrowserView browser,long folder){
        final UiDialog origin=currentDialog;
        worker.execute(()->{try(BrowserStore store=new BrowserStore(this)){
            final java.util.List<BrowserStore.Folder> folders=store.folders();final java.util.List<BrowserStore.Entry> favorites=store.folderEntries(folder);
            handler.post(()->{if(destroyed||currentDialog!=origin)return;java.util.ArrayList<me.ancal.wmdroid.sdk.WmBrowserFavoritesView.Entry> rows=new java.util.ArrayList<>();long parent=0;String title="즐겨찾기",folderName="";
                for(BrowserStore.Folder child:folders){if(child.id==folder){parent=child.parent;folderName=child.name;title=ui("즐겨찾기")+" - "+child.name;}if(child.parent==folder)rows.add(new me.ancal.wmdroid.sdk.WmBrowserFavoritesView.Entry("folder:"+child.id,child.name,true));}
                for(BrowserStore.Entry entry:favorites)rows.add(new me.ancal.wmdroid.sdk.WmBrowserFavoritesView.Entry("favorite:"+entry.id,entry.name,false));
                final UiDialog[] owner={null};final long up=parent;final String currentName=folderName;Runnable refresh=()->{if(currentDialog==owner[0]){owner[0].dismiss();browserFavorites(browser,folder);}};
                me.ancal.wmdroid.sdk.WmBrowserFavoritesView view=new me.ancal.wmdroid.sdk.WmBrowserFavoritesView(this,regular,accent,entry->{
                    if(entry.folder){owner[0].dismiss();browserFavorites(browser,Long.parseLong(entry.key.substring(7)));}else{for(BrowserStore.Entry link:favorites)if(entry.key.equals("favorite:"+link.id)){owner[0].dismiss();browser.navigate(link.url);break;}}
                });view.setEntries(rows.toArray(new me.ancal.wmdroid.sdk.WmBrowserFavoritesView.Entry[rows.size()]));
                owner[0]=dialog(title,view).setCommands(folder==0?"열기":"뒤로",folder==0?view::openSelection:()->{owner[0].dismiss();browserFavorites(browser,up);},"메뉴",()->{
                    me.ancal.wmdroid.sdk.WmBrowserFavoritesView.Entry selected=view.selected();
                    commandMenu(true,new me.ancal.wmdroid.sdk.WmMenuItem[]{
                        me.ancal.wmdroid.sdk.WmMenuItem.command(314,ui("즐겨찾기 추가")),me.ancal.wmdroid.sdk.WmMenuItem.command(312,ui("새 폴더")),
                        me.ancal.wmdroid.sdk.WmMenuItem.command(313,ui("삭제")).withEnabled(selected!=null),me.ancal.wmdroid.sdk.WmMenuItem.command(315,ui("편집")).withEnabled(selected!=null),me.ancal.wmdroid.sdk.WmMenuItem.command(2,ui("취소"))
                    },id->{if(id==314)browserAddFavorite(browser,folder,refresh);else if(id==312)browserNewFolder(folder,refresh);else if(id==315&&selected!=null){
                        if(selected.folder){long key=Long.parseLong(selected.key.substring(7));for(BrowserStore.Folder child:folders)if(child.id==key){browserFolderForm(child.parent,child.id,child.name,refresh);break;}}
                        else{long key=Long.parseLong(selected.key.substring(9));for(BrowserStore.Entry entry:favorites)if(entry.id==key){browserFavoriteForm(browser,folder,entry,refresh);break;}}
                    }else if(id==313&&selected!=null){
                        final me.ancal.wmdroid.sdk.WmMessageBox[] confirm={null};confirm[0]=me.ancal.wmdroid.sdk.WmMessageBox.confirm(this,regular,UiLanguage.locale(this),"즐겨찾기 삭제",selected.folder?"폴더와 폴더에 포함된 즐겨찾기가 영구적으로 삭제됩니다. 계속하시겠습니까?":"즐겨찾기가 영구적으로 삭제됩니다. 계속하시겠습니까?",accent,true,me.ancal.wmdroid.sdk.WmMessageBox.Icon.QUESTION,answer->{view.removeView(confirm[0]);if(answer!=0){view.setEnabled(true);owner[0].positive.setEnabled(true);owner[0].negative.setEnabled(true);return;}
                            worker.execute(()->{try(BrowserStore changes=new BrowserStore(this)){if(selected.folder)changes.deleteFolder(Long.parseLong(selected.key.substring(7)));else changes.delete(Long.parseLong(selected.key.substring(9)));handler.post(()->{if(!destroyed&&currentDialog==owner[0]){view.setEnabled(true);refresh.run();}});}catch(RuntimeException failure){handler.post(()->{if(!destroyed){view.setEnabled(true);owner[0].positive.setEnabled(true);owner[0].negative.setEnabled(true);toast("즐겨찾기를 삭제하지 못했습니다.");}});}});
                        });confirm[0].onWmRestyle(regular,bold,accent,0);view.setEnabled(false);owner[0].positive.setEnabled(false);owner[0].negative.setEnabled(false);view.addView(confirm[0],new FrameLayout.LayoutParams(-1,-1));
                    }});
                }).setCloseValidator(view::isEnabled).setOnLanguageChanged(()->{if(folder!=0){owner[0].spec.title=ui("즐겨찾기")+" - "+currentName;((me.ancal.wmdroid.sdk.WmTitleBar)owner[0].title).caption.setText(owner[0].spec.title);}}).setCommandMenus(false,true).create();show(owner[0]);
            });
        }catch(RuntimeException failure){handler.post(()->{if(!destroyed&&currentDialog==origin)toast("브라우저 항목을 읽을 수 없습니다.");});}});
    }
    private void browserRecords(me.ancal.wmdroid.sdk.WmBrowserView browser,boolean history){
        if(!history){browserFavorites(browser,0);return;}
        final UiDialog origin=currentDialog;
        worker.execute(()->{try(BrowserStore store=new BrowserStore(this)){
            final java.util.List<BrowserStore.Entry> entries=store.entries(true);
            handler.post(()->{if(destroyed||currentDialog!=origin)return;
                String[] names=new String[entries.size()];for(int n=0;n<names.length;n++)names[n]=entries.get(n).name;
                UiDialog.Builder builder=dialog("열어 본 페이지 목록",null);
                if(names.length==0)builder.setAuthoredMessage("항목이 없습니다.");else builder.setItems(names,(dialog,index)->browser.navigate(entries.get(index).url));
                if(!entries.isEmpty())builder.setCommands("삭제",()->{
                    if(currentDialog==origin||!currentDialog.dismiss())return;
                    show(dialog("열어 본 페이지 목록",null).setAuthoredMessage("열어 본 페이지 목록을 모두 삭제하시겠습니까?").setPositiveButton("예",(dialog,index)->{
                        worker.execute(()->{try(BrowserStore changes=new BrowserStore(this)){changes.clearHistory();handler.post(()->{if(!destroyed&&currentDialog==origin)browserRecords(browser,true);});}catch(RuntimeException failure){handler.post(()->{if(!destroyed)toast("열어 본 페이지 목록을 삭제하지 못했습니다.");});}});
                    }).setNegativeButton("아니요",null).create());
                },"",null);
                show(builder.create());
            });
        }catch(RuntimeException failure){handler.post(()->{if(!destroyed&&currentDialog==origin)toast("브라우저 항목을 읽을 수 없습니다.");});}});
    }
    private void openNotes(Bundle restored){
        for(UiDialog d=currentDialog;d!=null;d=d.previous)if(d.spec.view instanceof NotesPage){while(currentDialog!=d)if(!currentDialog.dismiss())return;return;}
        final UiDialog[] owner=new UiDialog[1];final NotesPage[] content=new NotesPage[1];
        NotesPage page=new NotesPage(this,regular,bold,accent,worker,()->{if(owner[0]!=null){owner[0].spec.closeIsOk=content[0].editing();owner[0].restyle();}});content[0]=page;page.client.setOnFolderListener(()->noteFolders(page));page.client.setOnActionListener((entry,command)->noteAction(page,entry,command));
        me.ancal.wmdroid.sdk.WmControls.localizeTree(page,UiLanguage.language(this));
        owner[0]=dialog("메모",page).setCloseValidator(()->{if(page.busy())return false;if(!page.editing())return true;page.save(page::list);return false;})
            .setCommands("새로 만들기",page::fresh,"메뉴",()->notesCommands(page)).setCommandMenus(false,true).create();
        show(owner[0]);page.restore(restored);
    }
    private void notesCommands(NotesPage page){
        if(page.busy())return;
        if(!page.editing()){
            me.ancal.wmdroid.sdk.WmNotesView.Entry selected=page.client.selected();
            commandMenu(true,new me.ancal.wmdroid.sdk.WmMenuItem[]{
                me.ancal.wmdroid.sdk.WmMenuItem.command(me.ancal.wmdroid.sdk.WmNotesView.RENAME,ui("이름 바꾸기/이동...")).withEnabled(selected!=null),
                me.ancal.wmdroid.sdk.WmMenuItem.command(me.ancal.wmdroid.sdk.WmNotesView.DELETE,ui("삭제")).withEnabled(selected!=null),
                me.ancal.wmdroid.sdk.WmMenuItem.separator(),me.ancal.wmdroid.sdk.WmMenuItem.command(0,ui("새로 고침"))
            },id->{if(id==0)page.reload();else noteAction(page,selected,id);});return;
        }
        commandMenu(true,new String[]{ui("확대/축소"),ui("취소")},-1,id->{
            if(id==0){int[] values={75,100,150,200,300};String[] names={"75%","100%","150%","200%","300%"};int selected=-1;for(int i=0;i<values.length;i++)if(values[i]==page.client.zoomPercent())selected=i;commandMenu(true,names,selected,n->page.client.setZoomPercent(values[n]));}
            else show(dialog("메모",null).setAuthoredMessage("이 메모의 변경 사항을 버리시겠습니까?").setPositiveButton("확인",(d,n)->page.cancel()).setNegativeButton("취소",null).create());
        });
    }

    private void noteAction(NotesPage page,me.ancal.wmdroid.sdk.WmNotesView.Entry selected,int command){
        if(page.busy()||page.editing()||selected==null)return;NotesStore.Entry expected=page.snapshot(selected.key);if(expected==null)return;
        if(command==me.ancal.wmdroid.sdk.WmNotesView.DELETE){
            final me.ancal.wmdroid.sdk.WmMessageBox[] box=new me.ancal.wmdroid.sdk.WmMessageBox[1];
            box[0]=me.ancal.wmdroid.sdk.WmMessageBox.confirm(this,regular,UiLanguage.locale(this),"메모","선택한 항목이 영구적으로 삭제됩니다. 계속하시겠습니까?",accent,false,answer->{page.removeView(box[0]);if(answer==0)page.delete(expected,null);});
            box[0].onWmRestyle(regular,bold,accent,0);page.addView(box[0],new FrameLayout.LayoutParams(-1,-1));return;
        }
        if(command==me.ancal.wmdroid.sdk.WmNotesView.RENAME)noteProperties(page,expected,null);
    }
    private void noteProperties(NotesPage page,NotesStore.Entry expected,Bundle restored){
        final String[] folderIds=page.folderIds();String chosen=restored==null?expected.folder:restored.getString("chosen_folder",expected.folder);int folderIndex=0;for(int i=0;i<folderIds.length;i++)if(folderIds[i].equals(chosen))folderIndex=i;
        me.ancal.wmdroid.sdk.WmDocumentProperties form=new me.ancal.wmdroid.sdk.WmDocumentProperties(this,regular,UiLanguage.locale(this),restored==null?expected.title:restored.getString("name"),page.folderNames(),folderIndex,new String[]{ui("주 메모리")},0,me.ancal.wmdroid.sdk.WmNotesView.formatSize(expected.size),java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT,UiLanguage.locale(this)).format(new Date(expected.modified)),"txt (.txt)",accent);
        final UiDialog[] owner=new UiDialog[1];final boolean[] done={false};
        Runnable cancel=()->{if(page.busy())return;owner[0].dismiss();};
        owner[0]=dialog("메모",form).setPropertySheet(()->{if(owner[0].dismissed&&notesPropertiesForm==form){notesPropertiesForm=null;notesPropertiesState=null;}}).setOnBack(cancel).setCloseValidator(()->{
            if(done[0])return true;if(page.busy())return false;
            String name=form.name();int first=0,last=name.length();while(first<last&&name.charAt(first)==' ')first++;while(last>first&&name.charAt(last-1)==' ')last--;String candidate=name.substring(first,last);
            String destination=folderIds[form.folderIndex()];if(candidate.equals(expected.title)&&destination.equals(expected.folder))return true;
            if(!me.ancal.wmdroid.sdk.WmDocumentProperties.validName(candidate)){toast(ui("올바른 이름을 입력하세요(최대 128자)."));form.nameField().requestFocus();return false;}
            form.nameField().setEnabled(false);form.folderField().setEnabled(false);page.rename(expected,candidate,destination,()->{owner[0].dismiss();},()->{form.nameField().setEnabled(true);form.folderField().setEnabled(folderIds.length>1);});return false;
        }).setOnLanguageChanged(()->{form.folderField().setChoices(page.folderNames(),form.folderIndex());form.locationField().setChoices(new String[]{ui("주 메모리")},0);}).create();
        notesPropertiesForm=form;notesPropertiesState=new Bundle();notesPropertiesState.putString("id",expected.id);notesPropertiesState.putString("title",expected.title);notesPropertiesState.putString("folder",expected.folder);notesPropertiesState.putStringArray("folder_ids",folderIds);notesPropertiesState.putLong("revision",expected.revision);notesPropertiesState.putLong("modified",expected.modified);notesPropertiesState.putLong("size",expected.size);
        show(owner[0]);form.nameField().requestFocus();if(restored==null)form.nameField().selectAll();else{int length=form.nameField().length();form.nameField().setSelection(Math.max(0,Math.min(length,restored.getInt("caret_start"))),Math.max(0,Math.min(length,restored.getInt("caret_end"))));}
    }

    private void noteFolders(NotesPage page){
        if(page.busy()||page.editing())return;final UiDialog[] owner=new UiDialog[1];final boolean[] cancelled={false};final me.ancal.wmdroid.sdk.WmDocumentFolders[] view=new me.ancal.wmdroid.sdk.WmDocumentFolders[1];
        java.util.function.Consumer<NotesStore.Folder> completed=folder->{view[0].setBusy(false);view[0].setFolders(page.folderModels(),page.client.folderId());if(folder!=null)view[0].editName(folder.id);};
        view[0]=new me.ancal.wmdroid.sdk.WmDocumentFolders(this,regular,bold,accent,new me.ancal.wmdroid.sdk.WmDocumentFolders.Listener(){
            public void command(int command,String id){
                if(page.busy())return;
                if(command==me.ancal.wmdroid.sdk.WmDocumentFolders.DELETE){if(id==null)return;String name="";for(me.ancal.wmdroid.sdk.WmNotesView.Folder folder:page.folderModels())if(folder.id.equals(id))name=folder.name;
                    final me.ancal.wmdroid.sdk.WmMessageBox[] box=new me.ancal.wmdroid.sdk.WmMessageBox[1];
                    if(!page.folderEmpty(id)){box[0]=new me.ancal.wmdroid.sdk.WmMessageBox(TodayActivity.this,regular,UiLanguage.locale(TodayActivity.this),"메모","빈 폴더만 삭제할 수 있습니다. 폴더 내용을 삭제하거나 이동하여 폴더를 비우십시오.",accent,()->view[0].removeView(box[0]));}
                    else{String pattern="%s 폴더가 영구적으로 삭제됩니다. 계속하시겠습니까?";box[0]=me.ancal.wmdroid.sdk.WmMessageBox.confirm(TodayActivity.this,regular,UiLanguage.locale(TodayActivity.this),"메모","",accent,true,answer->{view[0].removeView(box[0]);if(answer==0){view[0].setBusy(true);page.folderChange(command,id,null,completed,()->view[0].setBusy(false));}});box[0].setMessageFormat(pattern,name);}
                    box[0].onWmRestyle(regular,bold,accent,0);view[0].addView(box[0],new FrameLayout.LayoutParams(-1,-1));return;
                }
                view[0].setBusy(true);page.folderChange(command,id,null,completed,()->view[0].setBusy(false));
            }
            public void rename(String id,String name){view[0].setBusy(true);page.folderChange(me.ancal.wmdroid.sdk.WmDocumentFolders.RENAME,id,name,folder->{view[0].setBusy(false);view[0].setFolders(page.folderModels(),page.client.folderId());},()->view[0].setBusy(false));}
        });
        view[0].setFolders(page.folderModels(),page.client.folderId());me.ancal.wmdroid.sdk.WmControls.localizeTree(view[0],UiLanguage.language(this));
        owner[0]=dialog("메모",view[0]).setPropertySheet(()->{}).setOnBack(()->{if(page.busy())return;cancelled[0]=true;owner[0].dismiss();}).setCloseValidator(()->cancelled[0]||!page.busy()&&view[0].commitEditing()).create();show(owner[0]);
    }

    private void openContacts(){
        ContactsPage page=new ContactsPage(this,regular,bold,accent,worker,new me.ancal.wmdroid.sdk.WmContactsView.Listener(){
            public void selected(me.ancal.wmdroid.sdk.WmContactsView.Entry entry){}
            public void open(me.ancal.wmdroid.sdk.WmContactsView.Entry entry){openContactSummary(entry.key);}
        });me.ancal.wmdroid.sdk.WmControls.localizeTree(page,UiLanguage.language(this));
        // ponytail: native write editor/account mapping is pending; Android owns new/edit transactions.
        show(dialog("연락처",page).setCommands("새로 만들기",()->launch(new Intent(Intent.ACTION_INSERT,android.provider.ContactsContract.Contacts.CONTENT_URI)),"메뉴",()->contactsCommands(page)).setCommandMenus(false,true).create());
    }
    private void contactsCommands(ContactsPage page){
        boolean denied=DeviceApi.permission(this,Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED;
        me.ancal.wmdroid.sdk.WmContactsView.Entry selected=page.client.selected();
        commandMenu(true,new me.ancal.wmdroid.sdk.WmMenuItem[]{
            me.ancal.wmdroid.sdk.WmMenuItem.command(1,ui("연락처 읽기 권한")).withEnabled(denied),
            me.ancal.wmdroid.sdk.WmMenuItem.command(2,ui("새로 고침")),
            me.ancal.wmdroid.sdk.WmMenuItem.command(3,ui("편집")).withEnabled(!denied&&selected!=null),
            me.ancal.wmdroid.sdk.WmMenuItem.separator(),
            me.ancal.wmdroid.sdk.WmMenuItem.command(4,ui("Android 연락처"))
        },id->{if(id==1)DeviceApi.request(this,new String[]{Manifest.permission.READ_CONTACTS},13);else if(id==2)page.reload();else if(id==3&&selected!=null)editContact(selected.key);else if(id==4)launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS));});
    }
    private void refreshContacts(){for(UiDialog d=currentDialog;d!=null;d=d.previous){if(d.spec.view instanceof ContactsPage)((ContactsPage)d.spec.view).reload();else if(d.spec.view instanceof ContactsSummaryPage)((ContactsSummaryPage)d.spec.view).reload();}}
    private String pendingContactEditKey;
    private UiDialog pendingContactEditOrigin;
    private int contactEditGeneration;
    private boolean contactSaving(){return currentDialog!=null&&currentDialog.spec.view instanceof ContactEditorPage&&((ContactEditorPage)currentDialog.spec.view).blocksOwnerCommands();}
    private void editContact(String key){
        try{ContactsStore.uri(key);}catch(IllegalArgumentException invalid){toast("연락처를 읽을 수 없습니다.");return;}
        if(DeviceApi.permission(this,Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){pendingContactEditKey=key;pendingContactEditOrigin=currentDialog;DeviceApi.request(this,new String[]{Manifest.permission.READ_CONTACTS},14);toast("연락처 읽기 권한을 허용하세요.");return;}
        final UiDialog origin=currentDialog;final int ticket=++contactEditGeneration;ContactEditStore store=new ContactEditStore(this);
        worker.execute(()->{try{ContactEditStore.Snapshot snapshot=store.snapshot(key);ArrayList<ContactEditStore.Account> accounts=new ArrayList<>();for(ContactEditStore.Account account:snapshot.accounts())if(!account.readOnly)accounts.add(account);
            handler.post(()->{if(destroyed||ticket!=contactEditGeneration||currentDialog!=origin)return;
                if(accounts.isEmpty()){toast("쓰기 가능한 연락처 계정이 없습니다.");return;}
                if(accounts.size()==1){showContactEditor(store,snapshot,accounts.get(0).id);return;}
                String[] labels=new String[accounts.size()];for(int i=0;i<labels.length;i++){ContactEditStore.Account account=accounts.get(i);labels[i]=(account.name==null?ui("장치"):account.name)+(account.type==null?"":" — "+account.type)+(account.dataSet==null?"":" / "+account.dataSet)+" ["+account.id+"]";}
                show(dialog("연락처 계정",null).setItems(labels,(d,index)->showContactEditor(store,snapshot,accounts.get(index).id)).create());
            });
        }catch(RuntimeException unavailable){handler.post(()->{if(!destroyed&&ticket==contactEditGeneration&&currentDialog==origin)toast("연락처를 읽을 수 없습니다. 권한과 계정을 확인하세요.");});}});
    }
    private void showContactEditor(ContactEditStore store,ContactEditStore.Snapshot snapshot,long rawId){
        showContactEditor(store,snapshot,rawId,null);
    }
    private boolean restoreContactEditor(Bundle saved){
        if(saved==null||!saved.containsKey("contact_editor"))return false;
        try{Bundle state=saved.getBundle("contact_editor");if(state==null)throw new IllegalArgumentException("Contact draft required");ContactEditStore.Snapshot snapshot=ContactEditStore.restoreSnapshot(state.getBundle("snapshot"));showContactEditor(new ContactEditStore(this),snapshot,state.getLong("raw_id"),state);}
        catch(RuntimeException invalid){toast("이전 연락처 초안을 복원하지 못했습니다.");}
        return true;
    }
    private void showContactEditor(ContactEditStore store,ContactEditStore.Snapshot snapshot,long rawId,Bundle restored){
        final ContactEditorPage page;
        try{page=new ContactEditorPage(this,regular,snapshot,rawId);if(restored!=null)page.restoreState(restored);}catch(RuntimeException invalid){toast(restored==null?"연락처를 읽을 수 없습니다. 권한과 계정을 확인하세요.":"이전 연락처 초안을 복원하지 못했습니다.");return;}
        me.ancal.wmdroid.sdk.WmControls.localizeTree(page,UiLanguage.language(this));
        me.ancal.wmdroid.sdk.WmControls.restyleTree(page,regular,bold,accent,LauncherTheme.color(this,0xffc9e7e9,prefs.getBoolean("rose",false)));
        final boolean[] cancelled={false},saved={false};final UiDialog[] owner=new UiDialog[1];
        Runnable cancel=()->{if(page.blocksOwnerCommands())return;cancelled[0]=true;page.editor.closeSubcard(false);owner[0].dismiss();};
        owner[0]=dialog("연락처",page).setCloseIsOk().setOnBack(cancel).setCloseValidator(()->{
            if(page.blocksOwnerCommands())return false;if(cancelled[0]||saved[0])return true;
            if(page.saveOutcomeUnknown()){toast("이전 저장 결과를 확인할 수 없습니다. 연락처를 다시 열어 확인하세요.");return false;}
            final ContactEditStore.Update[] updates;final ContactEditStore.Insert[] inserts;
            try{page.prepare();updates=page.updates();inserts=page.inserts();if(ContactEditStore.operations(snapshot,rawId,updates,inserts).isEmpty())return true;}
            catch(RuntimeException invalid){toast("연락처 입력값을 확인하세요.");return false;}
            if(DeviceApi.permission(this,Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED||DeviceApi.permission(this,Manifest.permission.WRITE_CONTACTS)!=PackageManager.PERMISSION_GRANTED){DeviceApi.request(this,new String[]{Manifest.permission.READ_CONTACTS,Manifest.permission.WRITE_CONTACTS},15);toast("연락처 저장 권한 허용 뒤 확인을 다시 누르세요.");return false;}
            page.setSaving(true);((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(screen.getWindowToken(),0);
            worker.execute(()->{try{store.save(snapshot,rawId,updates,inserts);handler.post(()->{if(destroyed)return;saved[0]=true;page.setSaving(false);if(currentDialog==owner[0]){owner[0].dismiss();refreshContacts();}});}
                catch(RuntimeException failed){handler.post(()->{if(destroyed)return;page.setSaving(false);toast(failed instanceof SecurityException?"연락처 저장 권한을 확인하세요.":"연락처를 저장하지 못했습니다. 다른 변경이나 계정 상태를 확인하세요.");});}});
            return false;
        }).setCommands("",null,"메뉴",()->commandMenu(true,new me.ancal.wmdroid.sdk.WmMenuItem[]{
            me.ancal.wmdroid.sdk.WmMenuItem.command(1,ui("취소")),
            me.ancal.wmdroid.sdk.WmMenuItem.command(2,ui("Android에서 편집..."))
        },id->{if(id==1)cancel.run();else if(id==2){if(page.blocksOwnerCommands())return;page.prepare();show(dialog("Android에서 편집...",null).setAuthoredMessage("현재 초안을 버리고 Android 편집기를 여시겠습니까?").setPositiveButton("확인",(d,n)->{cancel.run();launch(new Intent(Intent.ACTION_EDIT,ContactsStore.uri(Long.toString(snapshot.accounts()[0].contactId))));}).setNegativeButton("취소",null).create());}})).setCommandMenus(false,true).create();
        show(owner[0]);
        if(page.saveOutcomeUnknown())toast("이전 저장 결과를 확인할 수 없습니다. 연락처를 다시 열어 확인하세요.");
    }
    private void openContactSummary(String key){
        final ContactsSummaryPage[] holder=new ContactsSummaryPage[1];
        ContactsSummaryPage page=new ContactsSummaryPage(this,regular,bold,accent,key,worker,new me.ancal.wmdroid.sdk.WmContactSummaryView.Listener(){
            public void selected(me.ancal.wmdroid.sdk.WmContactSummaryView.Phone phone){updateContactSummaryCaption(holder[0]);}
            public void dial(me.ancal.wmdroid.sdk.WmContactSummaryView.Phone phone){dialPhone(phone.number);}
            public void message(me.ancal.wmdroid.sdk.WmContactSummaryView.Phone phone){messagePhone(phone.number);}
            public void selectedLink(me.ancal.wmdroid.sdk.WmContactSummaryView.Link link){updateContactSummaryCaption(holder[0]);}
            public void openLink(me.ancal.wmdroid.sdk.WmContactSummaryView.Link link){openContactLink(link);}
            public void selectedAddress(me.ancal.wmdroid.sdk.WmContactSummaryView.Address address){updateContactSummaryCaption(holder[0]);}
            public void editAddress(me.ancal.wmdroid.sdk.WmContactSummaryView.Address address){editContact(key);}
        });holder[0]=page;page.setOnUpdated(()->updateContactSummaryCaption(page));page.client.setOnTabChangedListener(tab->updateContactSummaryCaption(page));
        me.ancal.wmdroid.sdk.WmControls.localizeTree(page,UiLanguage.language(this));
        show(dialog("연락처",page).setCloseIsOk().setCommands("통화",()->{
            if(page.client.selectedTab()==1||page.selectedAddress()!=null){editContact(page.contactKey);return;}
            me.ancal.wmdroid.sdk.WmContactSummaryView.Link link=page.selectedLink();if(link!=null){openContactLink(link);return;}
            me.ancal.wmdroid.sdk.WmContactSummaryView.Phone phone=page.selected();if(phone!=null){if(phone.action==me.ancal.wmdroid.sdk.WmContactSummaryView.SMS)messagePhone(phone.number);else dialPhone(phone.number);}
        },"메뉴",()->contactSummaryCommands(page)).setOnLanguageChanged(()->updateContactSummaryCaption(page)).setCommandMenus(false,true).create());
    }
    private void updateContactSummaryCaption(ContactsSummaryPage page){
        if(page==null||currentDialog==null||currentDialog.spec.view!=page)return;
        me.ancal.wmdroid.sdk.WmContactSummaryView.Phone phone=page.selected();me.ancal.wmdroid.sdk.WmContactSummaryView.Link link=page.selectedLink();
        currentDialog.leftCaption(page.client.selectedTab()==1||page.selectedAddress()!=null?"편집":link!=null?link.kind==me.ancal.wmdroid.sdk.WmContactSummaryView.EMAIL?"전자 메일":"찾아보기":phone!=null&&phone.action==me.ancal.wmdroid.sdk.WmContactSummaryView.SMS?"문자 보내기":"통화");
    }
    private void openContactLink(me.ancal.wmdroid.sdk.WmContactSummaryView.Link link){
        try{launch(link.kind==me.ancal.wmdroid.sdk.WmContactSummaryView.EMAIL?ContactActions.email(link.value):ContactActions.website(link.value));}catch(IllegalArgumentException invalid){toast("연락처 주소를 확인하세요.");}
    }
    private void contactSummaryCommands(ContactsSummaryPage page){
        me.ancal.wmdroid.sdk.WmContactSummaryView.Phone phone=page.selected();
        boolean permitted=DeviceApi.permission(this,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED;
        if(page.client.selectedTab()==1){
            commandMenu(true,new me.ancal.wmdroid.sdk.WmMenuItem[]{
                me.ancal.wmdroid.sdk.WmMenuItem.command(1,ui("편집")).withEnabled(permitted),
                me.ancal.wmdroid.sdk.WmMenuItem.command(5,ui("연락처 보내기")).withEnabled(false),
                me.ancal.wmdroid.sdk.WmMenuItem.command(6,ui("삭제")).withEnabled(false),
                me.ancal.wmdroid.sdk.WmMenuItem.command(8,ui("확대/축소")).withEnabled(false)
            },id->{if(id==1)editContact(page.contactKey);});return;
        }
        // Native command order retained. Unsupported SIM/share/delete commands
        // stay disabled pending complete account-aware write/provider contracts.
        commandMenu(true,new me.ancal.wmdroid.sdk.WmMenuItem[]{
            me.ancal.wmdroid.sdk.WmMenuItem.command(1,ui("편집")).withEnabled(permitted),
            me.ancal.wmdroid.sdk.WmMenuItem.command(2,ui("모든 통화 보기")).withEnabled(false),
            me.ancal.wmdroid.sdk.WmMenuItem.command(3,ui("SIM에 저장")).withEnabled(false),
            me.ancal.wmdroid.sdk.WmMenuItem.command(4,ui("문자 메시지 보내기...")).withEnabled(phone!=null),
            me.ancal.wmdroid.sdk.WmMenuItem.command(5,ui("연락처 보내기")).withEnabled(false),
            me.ancal.wmdroid.sdk.WmMenuItem.command(6,ui("연락처 삭제")).withEnabled(false),
            me.ancal.wmdroid.sdk.WmMenuItem.command(7,ui("단축 다이얼에 추가...")).withEnabled(phone!=null)
        },id->{if(id==1)editContact(page.contactKey);else if(id==4&&phone!=null)messagePhone(phone.number);else if(id==7&&phone!=null)editSpeedDial(null,page.contactName(),phone.number,()->{});});
    }
    private String phoneVoicemail="";
    private int voicemailQueryGeneration;
    private void openPhone(){
        me.ancal.wmdroid.sdk.WmPhoneView view=new me.ancal.wmdroid.sdk.WmPhoneView(this,regular,bold,accent,new me.ancal.wmdroid.sdk.WmPhoneView.Listener(){
            public void dial(String number){dialPhone(number);}public void voicemail(){dialVoicemail();}public void speedDial(){phoneSpeedDial();}public void history(){phoneHistory();}
        });
        view.setStatus(trayPhone==null?"전화 상태 확인 필요":trayPhone.caption());view.setVoicemailAvailable(!phoneVoicemail.isEmpty());
        me.ancal.wmdroid.sdk.WmControls.localizeTree(view,UiLanguage.language(this));
        final UiDialog[] holder=new UiDialog[1];UiDialog dialog=dialog("전화",view).setCommands("키패드 숨기기",()->{view.setKeypadVisible(!view.isKeypadVisible());holder[0].leftCaption(view.isKeypadVisible()?"키패드 숨기기":"키패드 표시");},"메뉴",()->phoneCommands(view)).setCommandMenus(false,true).create();holder[0]=dialog;show(dialog);
        final int ticket=++voicemailQueryGeneration;
        try{worker.execute(()->{String value="",country="";android.telephony.TelephonyManager manager=(android.telephony.TelephonyManager)getSystemService(TELEPHONY_SERVICE);
            if(manager!=null)try{String found=manager.getVoiceMailNumber();if(found!=null)value=me.ancal.wmdroid.sdk.WmPhoneView.dialable(found);}catch(SecurityException|UnsupportedOperationException|IllegalArgumentException unavailable){}
            if(manager!=null)try{country=manager.getNetworkCountryIso();if(country==null||country.isEmpty())country=manager.getSimCountryIso();}catch(SecurityException|UnsupportedOperationException unavailable){}
            final String current=value,region=country;handler.post(()->{if(destroyed||ticket!=voicemailQueryGeneration)return;phoneVoicemail=current;if(view.getParent()!=null){if(region!=null&&region.matches("[A-Za-z]{2}"))view.setCountryIso(region);view.setVoicemailAvailable(!current.isEmpty());}});
        });}catch(java.util.concurrent.RejectedExecutionException stopped){/* Activity is closing. */}
    }
    private void updatePhoneClient(){for(UiDialog dialog=currentDialog;dialog!=null;dialog=dialog.previous)if(dialog.spec.view instanceof me.ancal.wmdroid.sdk.WmPhoneView)((me.ancal.wmdroid.sdk.WmPhoneView)dialog.spec.view).setStatus(trayPhone.caption());}
    private void dialPhone(String number){
        final Intent call;try{call=PhoneActions.call(number);}catch(IllegalArgumentException invalid){toast("전화번호를 확인하세요.");return;}
        if(DeviceApi.permission(this,Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED){
            show(new UiDialog.Builder(this).setTitle("전화").setMessage(ui("전화 권한을 허용한 다음 통화 버튼을 다시 누르세요."))
                .setPositiveButton("권한 허용",(d,w)->DeviceApi.request(this,new String[]{Manifest.permission.CALL_PHONE},12)).setNegativeButton("취소",null).create());return;
        }launch(call);
    }
    private void messagePhone(String number){try{launch(PhoneActions.message(number));}catch(IllegalArgumentException invalid){toast("전화번호를 확인하세요.");}}
    private void dialVoicemail(){if(phoneVoicemail.isEmpty()){toast("음성 사서함 번호를 확인할 수 없습니다.");return;}dialPhone(phoneVoicemail);}
    private void phoneHistory(){
        // ponytail: system history adapter until a verified native history view and
        // the Android default-dialer/CallLog permission contract are implemented.
        launch(new Intent(Intent.ACTION_VIEW,android.provider.CallLog.Calls.CONTENT_URI));
    }
    private void phoneCommands(me.ancal.wmdroid.sdk.WmPhoneView view){
        android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        me.ancal.wmdroid.sdk.WmMenuItem[] items={
            me.ancal.wmdroid.sdk.WmMenuItem.command(1,ui("단축 다이얼 열기")),
            me.ancal.wmdroid.sdk.WmMenuItem.command(2,ui("SMS 메시지 보내기")).withEnabled(!view.number().isEmpty()),
            me.ancal.wmdroid.sdk.WmMenuItem.command(3,ui("붙여넣기")).withEnabled(clipboard!=null&&clipboard.hasPrimaryClip()),
            me.ancal.wmdroid.sdk.WmMenuItem.separator(),
            me.ancal.wmdroid.sdk.WmMenuItem.command(4,ui("Android 전화")),
            me.ancal.wmdroid.sdk.WmMenuItem.submenu(ui("보기"),me.ancal.wmdroid.sdk.WmMenuItem.command(5,ui("통화 기록")),me.ancal.wmdroid.sdk.WmMenuItem.command(6,ui(view.isKeypadVisible()?"키패드 숨기기":"키패드 표시")))
        };
        commandMenu(true,items,id->{if(id==1)phoneSpeedDial();else if(id==2)messagePhone(view.number());
            else if(id==3){try{android.content.ClipData clip=clipboard.getPrimaryClip();if(clip!=null&&clip.getItemCount()>0){CharSequence value=clip.getItemAt(0).coerceToText(this);if(value!=null)view.setNumber(view.number()+value);}}catch(SecurityException|IllegalArgumentException invalid){toast("전화번호를 확인하세요.");}}
            else if(id==4)launch(new Intent(Intent.ACTION_DIAL));else if(id==5)phoneHistory();else if(id==6){view.setKeypadVisible(!view.isKeypadVisible());currentDialog.leftCaption(view.isKeypadVisible()?"키패드 숨기기":"키패드 표시");}
        });
    }
    private me.ancal.wmdroid.sdk.WmSpeedDialView.Entry[] phoneShortcuts(){
        me.ancal.wmdroid.sdk.WmSpeedDialView.Entry[] saved=PhoneSpeedDialStore.read(prefs),rows=new me.ancal.wmdroid.sdk.WmSpeedDialView.Entry[saved.length+1];rows[0]=new me.ancal.wmdroid.sdk.WmSpeedDialView.Entry(1,"음성 사서함",phoneVoicemail);System.arraycopy(saved,0,rows,1,saved.length);return rows;
    }
    private void phoneSpeedDial(){
        final me.ancal.wmdroid.sdk.WmSpeedDialView view;try{view=new me.ancal.wmdroid.sdk.WmSpeedDialView(this,regular,bold,accent,phoneShortcuts());}catch(IllegalStateException malformed){toast("단축 다이얼을 읽을 수 없습니다.");return;}
        me.ancal.wmdroid.sdk.WmControls.localizeTree(view,UiLanguage.language(this));
        show(dialog("전화",view).setCloseIsOk().setCommands("통화",()->{me.ancal.wmdroid.sdk.WmSpeedDialView.Entry chosen=view.selected();if(chosen!=null){if(chosen.slot==1)dialVoicemail();else dialPhone(chosen.number);}},"메뉴",()->phoneSpeedCommands(view)).setCommandMenus(false,true).create());
    }
    private void phoneSpeedCommands(me.ancal.wmdroid.sdk.WmSpeedDialView view){
        me.ancal.wmdroid.sdk.WmSpeedDialView.Entry chosen=view.selected();
        commandMenu(true,new me.ancal.wmdroid.sdk.WmMenuItem[]{me.ancal.wmdroid.sdk.WmMenuItem.command(1,ui("새로 만들기")),me.ancal.wmdroid.sdk.WmMenuItem.command(2,ui("편집...")).withEnabled(chosen!=null),me.ancal.wmdroid.sdk.WmMenuItem.command(3,ui("삭제")).withEnabled(chosen!=null&&chosen.slot!=1)},id->{
            if(id==1||id==2){if(id==2&&chosen!=null&&chosen.slot==1){show(new UiDialog.Builder(this).setTitle("음성 사서함").setMessage(ui("SIM 음성 사서함 번호는 Android 전화 앱에서 변경합니다.")).setPositiveButton("Android 전화",(d,w)->launch(new Intent(Intent.ACTION_DIAL))).setNegativeButton("닫기",null).create());return;}editSpeedDial(view,id==1?null:chosen);}
            else if(id==3&&chosen!=null)show(new UiDialog.Builder(this).setTitle("단축 다이얼").setMessage(ui("선택한 단축 다이얼을 삭제할까요?")).setPositiveButton("삭제",(d,w)->{try{PhoneSpeedDialStore.remove(prefs,chosen.slot);view.setEntries(phoneShortcuts());}catch(IllegalStateException malformed){toast("단축 다이얼을 읽을 수 없습니다.");}}).setNegativeButton("취소",null).create());
        });
    }
    private void editSpeedDial(me.ancal.wmdroid.sdk.WmSpeedDialView view,me.ancal.wmdroid.sdk.WmSpeedDialView.Entry previous){editSpeedDial(previous,"","",()->view.setEntries(phoneShortcuts()));}
    private void editSpeedDial(me.ancal.wmdroid.sdk.WmSpeedDialView.Entry previous,String seedName,String seedNumber,Runnable saved){
        final int slot;try{slot=previous==null?PhoneSpeedDialStore.freeSlot(prefs):previous.slot;}catch(IllegalStateException full){toast("빈 단축 다이얼 위치가 없습니다.");return;}
        LinearLayout form=column();form.setPadding(12,12,12,0);EditText name=input("이름",previous==null?seedName:previous.name),number=input("전화번호",previous==null?seedNumber:previous.number);number.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        TextView position=ClassicUi.text(this,regular,UiLanguage.format(this,"위치: %d",slot));form.addView(position,new LinearLayout.LayoutParams(-1,44));form.addView(name,new LinearLayout.LayoutParams(-1,44));form.addView(number,new LinearLayout.LayoutParams(-1,44));
        final boolean[] cancelled={false};
        // Native New chooses a Contacts record; this manual editor is an explicit
        // Android adaptation until the shared Contacts applet/provider is available.
        show(dialog("단축 다이얼",form).setCloseValidator(()->{if(cancelled[0])return true;try{PhoneSpeedDialStore.put(prefs,slot,name.getText().toString().trim(),number.getText().toString());saved.run();return true;}catch(IllegalArgumentException invalid){toast("이름과 전화번호를 확인하세요.");return false;}catch(IllegalStateException malformed){toast("단축 다이얼을 읽을 수 없습니다.");return false;}}).setPropertySheet(null)
            .setOnLanguageChanged(()->position.setText(UiLanguage.format(this,"위치: %d",slot)))
            .setCommands("",null,"메뉴",()->commandMenu(true,new String[]{ui("취소")},-1,id->{cancelled[0]=true;closePage();})).setCommandMenus(false,true).create());
    }

    private void openMessageNotifications(){
        if(Build.VERSION.SDK_INT<21||!MessageNotificationPage.available()){
            show(new UiDialog.Builder(this).setTitle("받은 편지함 연결").setMessage(ui("Android의 메시지·메일 알림 개수를 표시합니다. 알림 접근을 직접 허용할 수 있습니다."))
                    .setPositiveButton("알림 연결",(d,w)->launch(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))).setNegativeButton("취소",null).create());return;
        }
        show(dialog("메시지 알림",new MessageNotificationPage(this)).create());
    }
    private void refreshMessageNotifications(){
        // Keep NotificationListenerService and its nested model off pre-21 paths.
        if(Build.VERSION.SDK_INT<21)return;
        for(UiDialog dialog=currentDialog;dialog!=null;dialog=dialog.previous)if(dialog.spec.view instanceof MessageNotificationPage)((MessageNotificationPage)dialog.spec.view).refresh();
    }
    private static final class MessageNotificationPage extends LinearLayout implements me.ancal.wmdroid.sdk.WmControls.LanguageListener {
        final TodayActivity owner;final LinearLayout rows;final ClassicScrollView scroll;
        static boolean available(){return MessageNotifications.snapshot()!=null;}
        MessageNotificationPage(TodayActivity owner){
            super(owner);this.owner=owner;setOrientation(VERTICAL);rows=owner.column();scroll=new ClassicScrollView(owner,rows);scroll.setBackgroundColor(Color.WHITE);addView(scroll,new LayoutParams(-1,-1));refresh();
        }
        void refresh(){
            int position=scroll.getRequestedScrollPosition();rows.removeAllViews();List<MessageNotifications.Entry> entries=MessageNotifications.snapshot();
            if(entries==null){
                TextView notice=owner.vgaText(owner.ui("알림 접근이 연결되지 않았습니다."),24,false);notice.setTextColor(Color.BLACK);notice.setPadding(12,12,12,12);rows.addView(notice,new LayoutParams(-1,-2));
                TextView connect=entry(owner.ui("알림 연결"));connect.setOnClickListener(v->{if(connect.getParent()==rows&&isShown())owner.launch(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));});
            }else if(entries.isEmpty()){
                TextView notice=owner.vgaText(owner.ui("메시지·메일 알림이 없습니다."),24,false);notice.setTextColor(Color.BLACK);notice.setPadding(12,12,12,12);rows.addView(notice,new LayoutParams(-1,-2));
            }else for(MessageNotifications.Entry notification:entries){
                TextView row=entry(notification.title);row.setContentDescription(notification.title+", "+notification.packageName);row.setEnabled(notification.contentIntent!=null);
                if(notification.contentIntent!=null)row.setOnClickListener(v->{
                    if(row.getParent()!=rows||!isShown())return;
                    List<MessageNotifications.Entry> current=MessageNotifications.snapshot();if(current==null||!current.contains(notification)){refresh();return;}
                    try{notification.contentIntent.send();}catch(android.app.PendingIntent.CanceledException canceled){owner.toast("알림을 열 수 없습니다. 알림 목록을 새로 고칩니다.");refresh();}
                });
            }
            scroll.scrollToPosition(position);
        }
        private TextView entry(String title){
            TextView row=owner.vgaText(title,24,false);ClassicUi.entry(row,owner.prefs.getBoolean("rose",false));row.setPadding(12,0,12,0);row.setSingleLine(true);row.setEllipsize(android.text.TextUtils.TruncateAt.END);rows.addView(row,new LayoutParams(-1,44));return row;
        }
        @Override public void onWmLanguageChanged(String language){refresh();}
    }
    private void openMessages() {
        if (prefs.getInt("message_count", -1) < 0) {
            show(new UiDialog.Builder(this).setTitle("받은 편지함 연결").setMessage(ui("Android의 메시지·메일 알림 개수를 표시합니다. 알림 접근을 직접 허용할 수 있습니다."))
                    .setPositiveButton("알림 연결", (d, w) -> launch(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                    .setNeutralButton("메시지 열기", (d, w) -> launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING))).setNegativeButton("취소", null).create());
        } else launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING));
    }
    private void openTodayCalendar(){
        java.time.LocalDate today=java.time.LocalDate.now();
        openCalendar(calendarLaunchDay==null||calendarLaunchDay.isBefore(today)?today:calendarLaunchDay,calendarLaunchOccurrence);
    }
    private void openCalendar() {openCalendar(null);}
    private void openCalendar(java.time.LocalDate day) {openCalendar(day,null);}
    private void openCalendar(java.time.LocalDate day,CalendarStore.Occurrence occurrence) {
        if(pendingAppointmentState!=null){
            for(UiDialog dialog=currentDialog;dialog!=null;dialog=dialog.previous)if(dialog==pendingAppointmentOwner){while(currentDialog!=dialog)if(!currentDialog.dismiss())return;retryAppointment(dialog);return;}
            Bundle state=new Bundle();state.putBundle("appointment_editor",pendingAppointmentState);restoreAppointment(state,true);return;
        }
        if (DeviceApi.permission(this,Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            show(new UiDialog.Builder(this).setTitle("오늘의 일정").setMessage(ui("기기 캘린더의 약속을 일정·월 보기에 표시할까요?"))
                    .setPositiveButton("일정 연결", (d, w) -> DeviceApi.request(this,new String[]{Manifest.permission.READ_CALENDAR}, 10))
                    .setNeutralButton("캘린더 열기", (d, w) -> launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))).setNegativeButton("취소", null).create());
        } else {
            final CalendarPage[] holder=new CalendarPage[1];
            CalendarPage view=new CalendarPage(this,regular,bold,worker,item->appointmentDetails(item,holder[0]),day,day==null?-1:CalendarPreferences.read(this).startView,occurrence);holder[0]=view;
            showCalendar(view);
        }
    }
    private void showCalendar(CalendarPage view){
            UiDialog dialog=new UiDialog.Builder(this).setTitle("일정").setView(view)
                    .setCommands(view.nextViewCaption(),view::nextView,"메뉴",()->calendarCommands(view)).setCommandMenus(false,true).create();
            view.setOnViewChanged(()->dialog.leftCaption(view.nextViewCaption()));show(dialog);
    }
    private void calendarCommands(CalendarPage view){
        java.util.ArrayList<me.ancal.wmdroid.sdk.WmMenuItem> items=new java.util.ArrayList<>();
        items.add(me.ancal.wmdroid.sdk.WmMenuItem.command(10,ui("새 약속")));
        // Native11012 omits the Go To group in Month/Year.
        if(view.viewMode()!=CalendarPage.MONTH&&view.viewMode()!=CalendarPage.YEAR){
            items.add(me.ancal.wmdroid.sdk.WmMenuItem.separator());
            items.add(me.ancal.wmdroid.sdk.WmMenuItem.command(11020,ui("오늘 날짜로 이동")));
            items.add(me.ancal.wmdroid.sdk.WmMenuItem.command(11021,ui("날짜 이동")));
        }
        items.add(me.ancal.wmdroid.sdk.WmMenuItem.separator());
        String[] labels=words(new String[]{"일정","일","주","월","연도"});boolean lunar=view.hasLunarCalendar();me.ancal.wmdroid.sdk.WmMenuItem[] views=new me.ancal.wmdroid.sdk.WmMenuItem[labels.length+(lunar?1:0)];
        for(int i=0;i<labels.length;i++)views[i]=me.ancal.wmdroid.sdk.WmMenuItem.checked(40080+i,labels[i],view.viewMode()==i&&(i!=CalendarPage.MONTH||!view.isLunarMonth()));
        if(lunar)views[labels.length]=me.ancal.wmdroid.sdk.WmMenuItem.checked(40085,ui("음력"),view.isLunarMonth());
        items.add(me.ancal.wmdroid.sdk.WmMenuItem.submenu(ui("도구"),me.ancal.wmdroid.sdk.WmMenuItem.command(40050,ui("옵션"))));
        items.add(me.ancal.wmdroid.sdk.WmMenuItem.submenu(ui("보기"),views));
        commandMenu(true,items.toArray(new me.ancal.wmdroid.sdk.WmMenuItem[0]),id->{if(id==10)editAppointment(null,view);else if(id==11020)view.go(java.time.LocalDate.now());else if(id==11021)calendarDate(view);else if(id==40050)calendarOptions(view);else if(id==40085)view.setLunarMonth();else if(id>=40080&&id<=40084)view.setView(id-40080);});
    }
    private void calendarOptions(CalendarPage calendarView){
        calendarView.closePicker();
        me.ancal.wmdroid.sdk.WmCalendarGeneralOptions general=new me.ancal.wmdroid.sdk.WmCalendarGeneralOptions(this,regular,bold,UiLanguage.locale(this),CalendarPreferences.read(this),accent,next->{CalendarPreferences.save(this,next);calendarView.applyOptions(next);});
        me.ancal.wmdroid.sdk.WmPropertySheet sheet=new me.ancal.wmdroid.sdk.WmPropertySheet(this,regular,LauncherTheme.color(this,0xffc9e7e9,prefs.getBoolean("rose",false)));
        sheet.addPage(ui("일반"),general);
        me.ancal.wmdroid.sdk.WmCalendarAppointmentsOptions appointments=new me.ancal.wmdroid.sdk.WmCalendarAppointmentsOptions(this,regular,bold,UiLanguage.locale(this),CalendarPreferences.readAppointments(this),accent,next->{CalendarPreferences.saveAppointments(this,next);calendarView.applyAppointments(next);});
        sheet.addPage(ui("약속"),appointments);
        show(dialog("옵션",sheet).setPropertySheet(null).setOnLanguageChanged(()->{sheet.setPageTitle(0,ui("일반"));sheet.setPageTitle(1,ui("약속"));general.setLocale(UiLanguage.locale(this));appointments.setLocale(UiLanguage.locale(this));}).create());
    }
    private void calendarMenu(String[] labels,java.util.function.IntConsumer action){
        commandMenu(true,labels,-1,action);
    }
    private void commandMenu(boolean right,String[] labels,int selected,java.util.function.IntConsumer action){
        if(currentDialog==null)return;closeStart();
        startMenu=new me.ancal.wmdroid.sdk.WmCommandMenu(this,currentDialog.footer,right,regular,labels,selected,ui(" 선택됨"),LauncherTheme.selection(this,prefs.getBoolean("rose",false)),action::accept,()->startMenu=null);
        screen.addView(startMenu,new FrameLayout.LayoutParams(-1,-1));if(pointer!=null)pointer.bringToFront();
    }
    private void commandMenu(boolean right,me.ancal.wmdroid.sdk.WmMenuItem[] items,java.util.function.IntConsumer action){
        if(currentDialog==null)return;closeStart();
        startMenu=new me.ancal.wmdroid.sdk.WmCommandMenu(this,currentDialog.footer,right,regular,items,ui(" 선택됨"),LauncherTheme.selection(this,prefs.getBoolean("rose",false)),action::accept,()->startMenu=null);
        screen.addView(startMenu,new FrameLayout.LayoutParams(-1,-1));if(pointer!=null)pointer.bringToFront();
    }
    private void appointmentDetails(CalendarStore.Occurrence item,CalendarPage calendarView){
        CalendarStore store=new CalendarStore(this);worker.execute(()->{try{
            CalendarStore.Event event=store.event(item.id);java.time.format.DateTimeFormatter f=java.time.format.DateTimeFormatter.ofPattern(ui("yyyy년 M월 d일 a h:mm"),UiLanguage.locale(this));
            String when=item.allDay?item.startDay()+" — "+item.lastDay()+ui(" (종일)"):java.time.Instant.ofEpochMilli(item.begin).atZone(java.time.ZoneId.systemDefault()).format(f)+"\n"+java.time.Instant.ofEpochMilli(item.end).atZone(java.time.ZoneId.systemDefault()).format(f);
            handler.post(()->{if(destroyed)return;String message=item.title+"\n"+when+(item.location.isEmpty()?"":"\n"+item.location)+(event.draft.notes.isEmpty()?"":"\n\n"+event.draft.notes);UiDialog.Builder d=new UiDialog.Builder(this).setTitle("약속").setMessage(message);
                if(event.writable&&!event.repeating)d.setPositiveButton("편집",(w,n)->editAppointment(event,calendarView)).setNeutralButton("약속 삭제",(w,n)->deleteAppointment(event,calendarView));
                else d.setMessage(message+"\n\n"+ui(event.repeating?"되풀이 약속은 편집할 수 없습니다.":"읽기 전용 캘린더입니다."));show(d.setNegativeButton("닫기",null).create());});
        }catch(RuntimeException failure){handler.post(()->toast("약속을 읽을 수 없습니다."));}});
    }
    private Bundle pendingAppointmentState;
    private boolean pendingAppointmentLoading;
    private UiDialog pendingAppointmentOwner;
    private int appointmentRequest,appointmentEditRequest;
    private static java.time.LocalDate appointmentDay(Bundle state){
        if(state==null||state.size()!=5||!(state.get("schema") instanceof Integer)||state.getInt("schema")!=1||!(state.get("page") instanceof Bundle)||!(state.get("day") instanceof String)||!(state.get("mode") instanceof Integer)||state.getInt("mode")<0||state.getInt("mode")>4||!(state.get("saving") instanceof Boolean))throw new IllegalArgumentException("Invalid appointment owner state");
        AppointmentPage.restoredOrigin(state.getBundle("page"));java.time.LocalDate day;
        try{day=java.time.LocalDate.parse(state.getString("day"));}catch(RuntimeException bad){throw new IllegalArgumentException("Invalid calendar return date",bad);}
        if(day.getYear()<me.ancal.wmdroid.sdk.WmYearChooser.MIN_YEAR||day.getYear()>me.ancal.wmdroid.sdk.WmYearChooser.MAX_YEAR)throw new IllegalArgumentException("Calendar return date outside range");return day;
    }
    private Bundle appointmentState(AppointmentPage page,UiDialog owner){
        Bundle state=new Bundle();state.putInt("schema",1);state.putBundle("page",page.saveState());java.time.LocalDate day=AppointmentPage.restoredOrigin(state.getBundle("page")).startDate;int mode=0;
        for(UiDialog parent=owner.previous;parent!=null;parent=parent.previous)if(parent.spec.view instanceof CalendarPage){CalendarPage calendar=(CalendarPage)parent.spec.view;day=calendar.selectedDay();mode=calendar.viewMode();break;}
        state.putString("day",day.toString());state.putInt("mode",mode);state.putBoolean("saving",page.isSaving()||page.saveOutcomeUnknown());return state;
    }
    private boolean restoreAppointment(Bundle saved,boolean visible){
        if(saved==null||!saved.containsKey("appointment_editor"))return false;
        try{pendingAppointmentState=saved.getBundle("appointment_editor");appointmentDay(pendingAppointmentState);}
        catch(RuntimeException invalid){pendingAppointmentState=null;toast("이전 일정 초안을 복원하지 못했습니다.");return true;}
        if(!visible)return false;
        ++appointmentRequest;pendingAppointmentLoading=false;
        UiDialog recovery=new UiDialog.Builder(this).setTitle("약속").setAuthoredMessage("이전 일정 초안이 보관되어 있습니다. 권한과 캘린더를 확인한 뒤 다시 시도하세요.").setPositiveButton("다시 시도",null).setNegativeButton("취소",null).setCloseValidator(()->{if(pendingAppointmentLoading)return false;pendingAppointmentState=null;pendingAppointmentOwner=null;return true;}).create();show(recovery);pendingAppointmentOwner=recovery;
        recovery.getButton(UiDialog.BUTTON_POSITIVE).setOnClickListener(v->retryAppointment(recovery));retryAppointment(recovery);return true;
    }
    private void retryAppointment(UiDialog origin){
        if(pendingAppointmentLoading||pendingAppointmentState==null||currentDialog!=origin||pendingAppointmentOwner!=origin)return;
        if(DeviceApi.permission(this,Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED){DeviceApi.request(this,new String[]{Manifest.permission.READ_CALENDAR},16);return;}
        final Bundle state=pendingAppointmentState;final int request=++appointmentRequest;pendingAppointmentLoading=true;origin.getButton(UiDialog.BUTTON_POSITIVE).setEnabled(false);CalendarStore store=new CalendarStore(this);
        worker.execute(()->{try{Bundle snapshot=state.getBundle("page");long eventId=snapshot.getLong("event_id");CalendarStore.Event existing=eventId<0?null:store.event(eventId);java.util.List<CalendarStore.CalendarTarget> targets=store.writableCalendars();
            handler.post(()->{if(destroyed||request!=appointmentRequest)return;pendingAppointmentLoading=false;origin.getButton(UiDialog.BUTTON_POSITIVE).setEnabled(true);if(currentDialog!=origin||pendingAppointmentState!=state)return;
                final CalendarPage[] holder=new CalendarPage[1];CalendarPage view=new CalendarPage(this,regular,bold,worker,item->appointmentDetails(item,holder[0]),appointmentDay(state),state.getInt("mode"));holder[0]=view;
                if(!origin.dismiss())return;showCalendar(view);showAppointmentEditor(existing,view,targets,state);
            });
        }catch(RuntimeException unavailable){handler.post(()->{if(destroyed||request!=appointmentRequest)return;pendingAppointmentLoading=false;origin.getButton(UiDialog.BUTTON_POSITIVE).setEnabled(true);if(currentDialog==origin&&pendingAppointmentState==state)toast("캘린더 목록을 읽을 수 없습니다.");});}});
    }
    private void editAppointment(CalendarStore.Event existing,CalendarPage calendarView){
        final UiDialog origin=currentDialog;final int request=++appointmentEditRequest;CalendarStore store=new CalendarStore(this);worker.execute(()->{try{
            java.util.List<CalendarStore.CalendarTarget> targets=store.writableCalendars();handler.post(()->{if(!destroyed&&request==appointmentEditRequest&&currentDialog==origin)showAppointmentEditor(existing,calendarView,targets,null);});
        }catch(RuntimeException unavailable){handler.post(()->{if(!destroyed&&request==appointmentEditRequest&&currentDialog==origin)toast("캘린더 목록을 읽을 수 없습니다.");});}});
    }
    private void showAppointmentEditor(CalendarStore.Event existing,CalendarPage calendarView,java.util.List<CalendarStore.CalendarTarget> targets,Bundle restored){
        if(restored!=null)appointmentDay(restored);
        Bundle snapshot=restored==null?null:restored.getBundle("page");boolean unknown=restored!=null&&restored.getBoolean("saving");boolean unchanged=snapshot==null||AppointmentPage.matchesEvent(snapshot,existing);boolean writable=unchanged&&(existing==null||existing.writable&&!existing.repeating);final AppointmentPage[] page=new AppointmentPage[1];
        CalendarDraft initial=snapshot==null?(existing==null?calendarView.newDraft():existing.draft):AppointmentPage.restoredOrigin(snapshot);
        int startYear=initial.startDate.getYear(),endYear=initial.endDate.getYear();if(startYear<1900||startYear>2999||endYear<1900||endYear>2999){toast("날짜는 1900년부터 2999년까지 선택할 수 있습니다.");return;}
        AppointmentPage.Choose choose=()->{if(!writable||unknown){page[0].error("이 약속은 현재 편집할 수 없습니다.");return;}if(existing!=null){page[0].error("현재 다른 캘린더로 이동할 수 없습니다.");return;}if(targets.isEmpty()){page[0].error("쓰기 가능한 캘린더가 없습니다.");return;}calendarMenu(targets.stream().map(t->t.name).toArray(String[]::new),n->page[0].calendar(targets.get(n)));};
        try{page[0]=snapshot==null?new AppointmentPage(this,regular,initial,choose,existing):new AppointmentPage(this,regular,snapshot,choose);}catch(RuntimeException invalid){toast("이전 일정 초안을 복원하지 못했습니다.");return;}
        page[0].allowRecurrence(existing==null);page[0].allowReminder(existing==null||existing.reminderEditable);page[0].saveOutcomeUnknown(unknown);
        UiDialog dialog=new UiDialog.Builder(this).setTitle(existing==null?"새 약속":"약속 편집").setView(page[0]).setPositiveButton("확인",null).setNegativeButton("취소",null).create();show(dialog);
        if(snapshot!=null)page[0].restoreState(snapshot);
        long targetId=snapshot!=null?snapshot.getLong("calendar"):existing==null?(targets.isEmpty()?-1:targets.get(0).id):existing.calendarId;
        for(CalendarStore.CalendarTarget target:targets)if(target.id==targetId)page[0].calendar(target);
        dialog.getButton(UiDialog.BUTTON_POSITIVE).setEnabled(writable&&!unknown);
        if(unknown)page[0].error("이전 저장 결과를 확인할 수 없습니다. 일정을 다시 열어 확인하세요.");else if(!writable)page[0].error("약속이 변경되었습니다. 초안을 보관했으니 일정을 다시 확인하세요.");else if(page[0].calendarId()<0)page[0].error("쓰기 가능한 캘린더가 없습니다.");
        CalendarStore store=new CalendarStore(this);
        dialog.getButton(UiDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(!writable||page[0].saveOutcomeUnknown()||me.ancal.wmdroid.sdk.WmControls.blocksOwnerCommands(page[0]))return;CalendarDraft draft;try{draft=page[0].draft();if(page[0].calendarId()<0)throw new IllegalArgumentException("쓰기 가능한 캘린더가 없습니다.");}catch(IllegalArgumentException bad){page[0].error(bad.getMessage());return;}
            if(existing!=null&&page[0].calendarId()==existing.calendarId&&draft.sameNonrecurringContent(existing.draft,java.time.ZoneId.systemDefault())){dialog.dismiss();return;}
            if(DeviceApi.permission(this,Manifest.permission.WRITE_CALENDAR)!=PackageManager.PERMISSION_GRANTED){DeviceApi.request(this,new String[]{Manifest.permission.WRITE_CALENDAR},12);page[0].error("일정 저장 권한 허용 뒤 확인을 다시 누르세요.");return;}
            final long targetCalendarId=page[0].calendarId();page[0].setSaving(true);dialog.getButton(UiDialog.BUTTON_POSITIVE).setEnabled(false);
            worker.execute(()->{try{final long savedId=store.save(existing,targetCalendarId,draft);handler.post(()->{if(destroyed)return;page[0].setSaving(false);if(currentDialog==dialog){dialog.dismiss();calendarView.refreshAfterSave(savedId,draft.startDate);loadCalendar();}});}catch(RuntimeException failure){handler.post(()->{if(destroyed)return;page[0].setSaving(false);boolean uncertain=!(failure instanceof IllegalArgumentException)&&!(failure instanceof SecurityException);page[0].saveOutcomeUnknown(uncertain);dialog.getButton(UiDialog.BUTTON_POSITIVE).setEnabled(!uncertain);page[0].error(uncertain?"이전 저장 결과를 확인할 수 없습니다. 일정을 다시 열어 확인하세요.":failure instanceof IllegalArgumentException?failure.getMessage():"약속을 저장하지 못했습니다. 캘린더 권한과 계정을 확인하세요.");});}});
        });
    }
    private void deleteAppointment(CalendarStore.Event event,CalendarPage calendarView){
        show(new UiDialog.Builder(this).setTitle("약속 삭제").setMessage(UiLanguage.format(this,"'%s' 약속을 삭제하시겠습니까?",event.draft.title)).setPositiveButton("삭제",(d,n)->{
            if(DeviceApi.permission(this,Manifest.permission.WRITE_CALENDAR)!=PackageManager.PERMISSION_GRANTED){DeviceApi.request(this,new String[]{Manifest.permission.WRITE_CALENDAR},12);toast("권한 허용 뒤 약속 삭제를 다시 선택하세요.");return;}
            final CalendarPage.DeletionSelection selection=calendarView.captureDeletionSelection(event.id);
            worker.execute(()->{try{new CalendarStore(this).delete(event);handler.post(()->{calendarView.refreshAfterDelete(selection);loadCalendar();});}catch(RuntimeException denied){handler.post(()->toast("약속을 삭제하지 못했습니다."));}});
        }).setNegativeButton("취소",null).create());
    }
    private void calendarDate(CalendarPage view){view.datePicker();}

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] result) { super.onRequestPermissionsResult(request, permissions, result); if(request==TaskReminders.PERMISSION_REQUEST)TaskReminders.schedule(this); if(request==16&&pendingAppointmentState!=null&&currentDialog==pendingAppointmentOwner&&DeviceApi.permission(this,Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED)retryAppointment(currentDialog); if(request==14){String key=pendingContactEditKey;UiDialog origin=pendingContactEditOrigin;pendingContactEditKey=null;pendingContactEditOrigin=null;if(key!=null&&currentDialog==origin&&DeviceApi.permission(this,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED)editContact(key);} if(request==13)refreshContacts(); if(request==11)refresh(); if (request == 10) {loadCalendar();if(result.length>0&&result[0]==PackageManager.PERMISSION_GRANTED)openCalendar();} }
    private void loadCalendar() {
        final int request=++calendarRequest;
        if(calendar==null)return;
        if(DeviceApi.permission(this,Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED){calendarLaunchDay=null;calendarLaunchOccurrence=null;fitCaption(calendar,ui("일정 연결"));return;}
        final int flags=prefs.getInt("calendar_flags",3);
        worker.execute(()->{
            String value=ui("예정된 약속 없음");java.time.LocalDate launchDay=null;CalendarStore.Occurrence launchOccurrence=null;java.util.List<WmToday.Appointment> appointments=new java.util.ArrayList<>();
            try{
                long now=System.currentTimeMillis();java.time.ZoneId zone=java.time.ZoneId.systemDefault();
                java.time.LocalDate today=java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
                java.util.List<WmToday.CalendarEntry> entries=new java.util.ArrayList<>();
                java.util.IdentityHashMap<WmToday.CalendarEntry,CalendarStore.Occurrence> occurrenceByEntry=new java.util.IdentityHashMap<>();
                java.text.DateFormat clock=android.text.format.DateFormat.getTimeFormat(this);
                for(CalendarStore.Occurrence occurrence:new CalendarStore(this).between(today,today.plusDays(6),false)){
                    if(occurrence.end<occurrence.begin)continue;
                    int offset=(int)Math.max(0,java.time.temporal.ChronoUnit.DAYS.between(today,occurrence.startDay()));
                    java.time.LocalTime time=java.time.Instant.ofEpochMilli(occurrence.begin).atZone(zone).toLocalTime();
                    String subject=occurrence.title;
                    if(offset>0){String day=offset==1?ui("내일"):new java.text.SimpleDateFormat("EEEE",UiLanguage.locale(this)).format(new java.util.Date(occurrence.begin));subject=UiLanguage.format(this,"%1$s: %2$s",day,subject);}
                    java.time.ZonedDateTime ending=java.time.Instant.ofEpochMilli(occurrence.end).atZone(zone);
                    java.time.LocalDate startDay=occurrence.startDay(),endDay=ending.toLocalDate();
                    boolean multiDay=endDay.isAfter(startDay.plusDays(1))||(endDay.equals(startDay.plusDays(1))&&(ending.getHour()!=0||ending.getMinute()!=0));
                    int displayFlags=(occurrence.allDay?2:0)|(multiDay?4:0)|(occurrence.begin<=now?8:16)|(startDay.equals(today)?32:0)|(endDay.equals(today)?64:0);
                    String detail=WmToday.appointmentDetails(occurrence.begin,occurrence.end,displayFlags,occurrence.location,clock,ui("며칠에 걸친 행사"));
                    WmToday.CalendarEntry entry=new WmToday.CalendarEntry(occurrence.begin,occurrence.end,occurrence.allDay,offset,time.getHour()*60+time.getMinute(),new WmToday.Appointment(subject,detail));
                    entries.add(entry);occurrenceByEntry.put(entry,occurrence);
                }
                for(WmToday.CalendarEntry entry:WmToday.selectAppointments(entries,now,flags)){
                    if(appointments.isEmpty()){launchDay=today.plusDays(entry.dayOffset);launchOccurrence=occurrenceByEntry.get(entry);}
                    appointments.add(entry.appointment);
                }
            }catch(RuntimeException unavailable){appointments.clear();value=ui("일정을 읽을 수 없습니다");}
            final String result=value;
            final java.time.LocalDate selectedDay=appointments.isEmpty()?null:launchDay;
            final CalendarStore.Occurrence selectedOccurrence=appointments.isEmpty()?null:launchOccurrence;
            handler.post(()->{if(!destroyed&&request==calendarRequest&&calendar!=null){
                calendarLaunchDay=selectedDay;calendarLaunchOccurrence=selectedOccurrence;
                if(appointments.isEmpty())fitCaption(calendar,result);
                else ((WmToday.Row)calendar.getParent()).setAppointments(appointments);
            }});
        });
    }

    private int valueLineCount(CharSequence value) { int lines=1; for(int i=0;i<value.length();i++) if(value.charAt(i)=='\n') lines++; return lines; }

    private void help() {
        show(new UiDialog.Builder(this).setTitle("도움말").setMessage(ui("시작에서 프로그램과 설정을 엽니다.\n오늘의 소유자·작업·일정 항목을 눌러 사용합니다.")).setNegativeButton("닫기",null).create());
    }

    private void settings(){settings(0);}
    private SettingsPage settingsPage(){
        return new SettingsPage(this,regular,prefs.getBoolean("rose",false),this::cancelApplet,key->openApplet(()->openSetting(key)));
    }
    private void settings(int initialGroup) {
        SettingsPage settings=settingsPage();
        settings.select(initialGroup);show(dialog("설정",settings).setPropertySheet(null).create());
    }
    private boolean restoreSettingsGrid(Bundle saved){
        if(saved==null||!saved.containsKey("settings_grid"))return false;
        try{
            Object state=saved.get("settings_grid");
            if(!(state instanceof Bundle))throw new IllegalArgumentException("Invalid Settings state");
            SettingsPage settings=settingsPage();
            settings.restoreInteractionState((Bundle)state);
            show(dialog("설정",settings).setPropertySheet(null).create());
            return true;
        }catch(RuntimeException invalid){
            toast("설정 화면을 복원하지 못했습니다.");
            return false;
        }
    }
    private void touchSoundSettings(){
        me.ancal.wmdroid.sdk.WmTouchSoundOptions panel=new me.ancal.wmdroid.sdk.WmTouchSoundOptions(this,regular,UiLanguage.language(this),prefs.getBoolean("touch_sound_enabled",false),Math.max(1,Math.min(2,prefs.getInt("touch_sound_level",2))),(enabled,level)->prefs.edit().putBoolean("touch_sound_enabled",enabled).putInt("touch_sound_level",level).apply());
        show(dialog("소리 및 알림",panel).setPropertySheet(null).setCommands("음량",()->showStatus(1),"Android 설정",()->launch(new Intent(Settings.ACTION_SOUND_SETTINGS))).create());
    }
    private void openSetting(String key){
        switch(key){
                case "wmdroid":launcherSettings();break;
                case "owner":editOwner();break;
                case "today":todaySettings();break;
                case "sound":touchSoundSettings();break;
                case "home":chooseHome();break;
                case "menu":menuSettings();break;
                case "applet_targets":launchSettings(true);break;
                case "home_commands":launchSettings(false);break;
                case "connection_module":connectionSettings();break;
                case "language":languageSettings();break;
                case "notifications":launch(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));break;
                case "power":showStatus(2);break;
                case "backlight":backlight();break;
                case "fonts":fontSettings();break;
                case "display":displaySettings();break;
                case "experience":experience();break;
                case "finger_mouse":fingerMouseSettings();break;
                case "app_icons":appIconSettings();break;
                case "theme":themeSettings();break;
                case "resource_pack":resourcePackSettings();break;
                case "about":show(new UiDialog.Builder(this).setTitle("정보").setMessage(ui("WMDroid")).create());break;
                case "wireless":showStatus(0);break;
                case "wifi":launch(new Intent(Settings.ACTION_WIFI_SETTINGS));break;
                case "bluetooth":launch(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));break;
                case "phone":openPhone();break;
                case "airplane":launch(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS));break;
                case "bluetooth_state":if(Build.VERSION.SDK_INT>=31&&DeviceApi.permission(this,Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)DeviceApi.request(this,new String[]{Manifest.permission.BLUETOOTH_CONNECT},11);else refresh();break;
                case "platform":launch(new Intent(Settings.ACTION_SETTINGS));break;
        }
    }
    private TextView authoredText(String authored){TextView view=ClassicUi.text(this,regular,"");me.ancal.wmdroid.sdk.WmControls.bindText(view,UiLanguage.language(this),authored);return view;}
    private void launcherSettings(){
        LinearLayout rows=column();String[][] groups={{"모양","fonts","display","theme","resource_pack","language","experience","finger_mouse","app_icons"},{"오늘","today","connection_module"},{"Android 연결","home","notifications","bluetooth_state","platform"}};
        java.util.Map<String,String> names=new java.util.HashMap<>();String[][] pairs={{"fonts","글꼴"},{"display","화면 표시"},{"theme","테마 색"},{"resource_pack","리소스팩"},{"language","언어"},{"experience","옛 기기 동작"},{"finger_mouse","핑거마우스"},{"app_icons","Android 앱 아이콘"},{"today","오늘"},{"connection_module","연결 모듈"},{"home","기본 홈"},{"notifications","알림 연결"},{"bluetooth_state","Bluetooth 상태 연결"},{"platform","Android 설정"}};
        for(String[] pair:pairs)names.put(pair[0],pair[1]);
        for(String[] group:groups){TextView header=authoredText(group[0]);header.setPadding(8,0,8,0);me.ancal.wmdroid.sdk.WmControls.faceBackground(header,LauncherTheme.selection(this,false));rows.addView(header,new LinearLayout.LayoutParams(-1,36));
            for(int i=1;i<group.length;i++){String key=group[i];TextView entry=authoredText(names.get(key));ClassicUi.entry(entry,prefs.getBoolean("rose",false));entry.setPadding(16,0,16,0);entry.setOnClickListener(v->openApplet(()->openSetting(key)));rows.addView(entry,new LinearLayout.LayoutParams(-1,44));}
        }
        show(dialog("WMDroid",new ClassicScrollView(this,rows)).setPropertySheet(null).create());
    }
    private void appIconSettings(){
        LinearLayout panel=column();panel.setPadding(8,8,8,0);
        android.widget.CheckBox background=new android.widget.CheckBox(this);ClassicUi.check(background,regular);me.ancal.wmdroid.sdk.WmControls.bindText(background,UiLanguage.language(this),"Adaptive Icon 배경판 제거");background.setChecked(prefs.getBoolean("android_icons_unframed",false));
        background.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("android_icons_unframed",on).apply());panel.addView(background,new LinearLayout.LayoutParams(-1,48));
        android.widget.RadioGroup modes=new android.widget.RadioGroup(this);modes.setOrientation(LinearLayout.VERTICAL);String[] keys={"all","blacklist","whitelist"},labels={"전체","선택 앱 제외","선택 앱만"};
        for(int i=0;i<keys.length;i++){final String key=keys[i];android.widget.RadioButton choice=new android.widget.RadioButton(this);me.ancal.wmdroid.sdk.WmControls.radio(choice,regular);choice.setId(700+i);me.ancal.wmdroid.sdk.WmControls.bindText(choice,UiLanguage.language(this),labels[i]);choice.setTag(key);choice.setChecked(key.equals(prefs.getString("android_icons_mode","all")));modes.addView(choice,new LinearLayout.LayoutParams(-1,44));}
        modes.setOnCheckedChangeListener((group,id)->{View selected=group.findViewById(id);if(selected!=null)prefs.edit().putString("android_icons_mode",(String)selected.getTag()).apply();});panel.addView(modes,new LinearLayout.LayoutParams(-1,-2));
        TextView choose=authoredText("아이콘 적용 목록...");ClassicUi.button(choose,false);choose.setOnClickListener(v->applicationListSetting("아이콘 적용 목록","android_icons_list"));panel.addView(choose,new LinearLayout.LayoutParams(-1,44));
        TextView hidden=authoredText("앱 숨기기...");ClassicUi.button(hidden,false);hidden.setOnClickListener(v->applicationListSetting("앱 숨기기","hidden_apps"));panel.addView(hidden,new LinearLayout.LayoutParams(-1,44));
        TextView note=authoredText("분리할 수 없는 아이콘은 원본을 표시합니다.");note.setPadding(0,8,0,0);panel.addView(note,new LinearLayout.LayoutParams(-1,-2));
        show(dialog("Android 앱 아이콘",new ClassicScrollView(this,panel)).setPropertySheet(null).create());
    }
    private void applicationListSetting(String title,String key){
        java.util.List<String> initial=new java.util.ArrayList<>();try{JSONArray saved=new JSONArray(prefs.getString(key,"[]"));for(int i=0;i<saved.length();i++)if(saved.opt(i) instanceof String)initial.add(saved.getString(i));}catch(JSONException invalid){}
        AppSelectionPage apps=new AppSelectionPage(this,regular,worker,initial,chosen->prefs.edit().putString(key,new JSONArray(chosen).toString()).apply());
        show(dialog(title,apps).setPropertySheet(null).create());
    }
    private void chooseMenuApps(java.util.function.Consumer<String> chosen){
        AppSelectionPage apps=new AppSelectionPage(this,regular,worker,java.util.Collections.emptyList(),null);
        final UiDialog[] picker=new UiDialog[1];picker[0]=dialog("앱 선택",apps).setCommands("추가",()->{java.util.List<String> selected=apps.selectedIds();if(selected.isEmpty())return;if(!picker[0].dismiss())return;for(String id:selected)chosen.accept(id);},"취소",()->picker[0].dismiss()).create();show(picker[0]);
    }
    private void fingerMouseSettings(){
        LinearLayout panel=column();panel.setPadding(8,8,8,0);
        android.widget.CheckBox gesture=new android.widget.CheckBox(this);ClassicUi.check(gesture,regular);
        me.ancal.wmdroid.sdk.WmControls.bindText(gesture,UiLanguage.language(this),"오늘 화면을 길게 눌러 활성화");gesture.setChecked(prefs.getBoolean("finger_mouse_long_press",false));
        gesture.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("finger_mouse_long_press",on).apply());panel.addView(gesture,new LinearLayout.LayoutParams(-1,48));
        TextView enable=authoredText("핑거마우스 시작");enable.setGravity(Gravity.CENTER);me.ancal.wmdroid.sdk.WmControls.button(enable);
        enable.setOnClickListener(v->{if(pointer==null)togglePointer();});panel.addView(enable,new LinearLayout.LayoutParams(-1,44));
        TextView help=authoredText("이동: 드래그 · 클릭: 탭 · 종료: 길게 누르기");help.setPadding(0,12,0,0);panel.addView(help,new LinearLayout.LayoutParams(-1,-2));
        show(dialog("핑거마우스",new ClassicScrollView(this,panel)).setPropertySheet(null).create());
    }
    private void menuSettings(){
        MenuSettingsPage panel=new MenuSettingsPage(this,prefs,regular,new MenuSettingsPage.Choose(){
            public void apps(java.util.function.Consumer<String> chosen){chooseMenuApps(chosen);}
            public void options(MenuSettingsPage page,String id){menuOptions(page,id);}
        });
        show(dialog("시작 메뉴",panel).setPropertySheet(null).setCommands("새로 만들기",()->commandMenu(false,words(new String[]{"응용 프로그램","폴더"}),-1,n->{if(n==0)panel.addApp();else newMenuFolder(panel);}),"메뉴",()->commandMenu(true,words(new String[]{"열 앱 설정","오늘 하단바","기본값","시작 메뉴로"}),-1,n->{if(n==0)launchSettings(true);else if(n==1)launchSettings(false);else if(n==2)panel.reset();else panel.root();})).setCommandMenus(true,true).create());
    }
    private void refreshHomeCommands(){
        if(homeCommands==null)return;
        String left=prefs.getString("home_left_command","phone"),right=prefs.getString("home_right_command","contacts");
        homeCommands.setCommands(MenuSettingsPage.label(this,left),()->runShortcut(left),MenuSettingsPage.label(this,right),()->runShortcut(right));
    }
    private void runShortcut(String id){
        ComponentName app=ComponentName.unflattenFromString(id);
        if(app!=null)launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app));else runShellCommand(id);
    }
    private boolean openAppletTarget(String command){
        String target=prefs.getString("applet_"+command+"_target",command);
        if(ComponentName.unflattenFromString(target)==null)return false;
        runShortcut(target);return true;
    }
    private void launchSettings(boolean applets){
        LaunchSettingsPage panel=new LaunchSettingsPage(this,prefs,regular,applets,this::chooseLaunchTarget);
        show(dialog(applets?"열 앱 설정":"오늘 하단바",panel).setPropertySheet(null).create());
    }
    private void chooseLaunchTarget(String key,String fallback,boolean applet){
        String[] ids=applet?new String[]{fallback}:MenuPreferences.DEFAULT;
        String[] labels=new String[ids.length+1];for(int i=0;i<ids.length;i++)labels[i]=applet?ui("내장 애플릿"):MenuSettingsPage.label(this,ids[i]);labels[ids.length]=ui("Android 앱 선택...");
        show(new UiDialog.Builder(this).setTitle(applet?MenuSettingsPage.label(this,fallback):key.equals("home_left_command")?"왼쪽 항목":"오른쪽 항목").setItems(labels,(d,n)->{
            if(n<ids.length){prefs.edit().putString(key,ids[n]).apply();return;}
            AppSelectionPage apps=new AppSelectionPage(this,regular,worker,java.util.Collections.emptyList(),null);
            final UiDialog[] picker=new UiDialog[1];apps.singleSelection(id->{if(!picker[0].dismiss())return;prefs.edit().putString(key,id).apply();});picker[0]=dialog("앱 선택",apps).setPropertySheet(null).create();show(picker[0]);
        }).create());
    }
    private void newMenuFolder(MenuSettingsPage panel){
        EditText name=new EditText(this);ClassicUi.input(name,regular);name.setSingleLine(true);name.setText(ui("새 폴더"));
        show(dialog("폴더",name).setPositiveButton("추가",(d,w)->{String title=name.getText().toString().trim();if(!title.isEmpty()){java.util.List<FolderIcons.Icon> icons=FolderIcons.all(this);panel.addFolder(title,icons.isEmpty()?FolderIcons.DEFAULT:icons.get(0).id);}}).setNegativeButton("취소",null).create());
    }
    private void menuOptions(MenuSettingsPage panel,String id){
        String[] choices=MenuPreferences.isFolder(id)?new String[]{"열기","이름 바꾸기","아이콘","이동...","삭제"}:("browser".equals(id)||"media".equals(id))?new String[]{"열 앱 설정","이동...","삭제"}:new String[]{"이동...","삭제"};
        show(new UiDialog.Builder(this).setTitle(MenuSettingsPage.label(this,id)).setItems(words(choices),(d,n)->{
            String action=choices[n];
            if("열 앱 설정".equals(action)){chooseLaunchTarget("applet_"+id+"_target",id,true);return;}
            if("이동...".equals(action)){moveMenuItem(panel,id);return;}
            if("삭제".equals(action)){panel.remove(id);return;}
            if("열기".equals(action))panel.enter(id);else if("아이콘".equals(action))folderIconPicker(id,panel);else{
                EditText name=new EditText(this);ClassicUi.input(name,regular);name.setSingleLine(true);name.setText(MenuSettingsPage.label(this,id));
                name.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){MenuPreferences.updateFolder(prefs,id,s.toString(),null);panel.updated();}public void afterTextChanged(android.text.Editable value){}});
                show(dialog("이름 바꾸기",name).setPropertySheet(null).create());
            }
        }).create());
    }
    private void moveMenuItem(MenuSettingsPage panel,String id){
        java.util.List<String> targets=MenuPreferences.destinations(prefs,panel.parentId(),id);
        if(targets.isEmpty()){toast("이동할 폴더가 없습니다.");return;}
        String[] labels=new String[targets.size()];for(int i=0;i<labels.length;i++)labels[i]=targets.get(i).isEmpty()?ui("시작 메뉴로"):MenuSettingsPage.label(this,targets.get(i));
        show(new UiDialog.Builder(this).setTitle("이동할 위치").setItems(labels,(d,n)->panel.moveTo(id,targets.get(n))).create());
    }
    private void folderIconPicker(String id,MenuSettingsPage panel){
        class FolderChoices extends LinearLayout implements me.ancal.wmdroid.sdk.WmControls.RestyleListener {
            final java.util.List<View> choices=new java.util.ArrayList<>();
            String revision;
            FolderChoices(){super(TodayActivity.this);setOrientation(VERTICAL);refresh();}
            void refresh(){
                String current=me.ancal.wmdroid.sdk.WmResources.revision(TodayActivity.this);
                if(current.equals(revision))return;
                revision=current;
                removeAllViews();choices.clear();LinearLayout row=null;int index=0;
                String selectedIcon=MenuPreferences.folder(prefs,id).optString("icon",FolderIcons.DEFAULT);
                for(FolderIcons.Icon folder:FolderIcons.all(TodayActivity.this)){
                    if(index++%4==0){row=new LinearLayout(TodayActivity.this);addView(row,new LinearLayout.LayoutParams(-1,112));}
                    LinearLayout cell=column();cell.setGravity(Gravity.CENTER);ClassicUi.entryBackground(cell,prefs.getBoolean("rose",false));cell.setTag(folder.id);cell.setContentDescription(folder.title);cell.setSelected(folder.id.equals(selectedIcon));choices.add(cell);
                    ImageView art=new ImageView(TodayActivity.this);art.setImageDrawable(FolderIcons.load(TodayActivity.this,folder.id));art.setScaleType(ImageView.ScaleType.FIT_CENTER);cell.addView(art,new LinearLayout.LayoutParams(64,64));
                    TextView label=ClassicUi.text(TodayActivity.this,regular,folder.title);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setGravity(Gravity.CENTER);cell.addView(label,new LinearLayout.LayoutParams(-1,40));
                    cell.setOnClickListener(v->{MenuPreferences.updateFolder(prefs,id,null,folder.id);for(View choice:choices)choice.setSelected(choice==cell);panel.updated();});row.addView(cell,new LinearLayout.LayoutParams(120,112));
                }
                if(choices.isEmpty())addView(authoredText("항목이 없습니다."),new LinearLayout.LayoutParams(-1,-2));
            }
            public void onWmRestyle(Typeface regular,Typeface bold,int selection,int tabColor){
                String previous=revision;refresh();if(previous.equals(revision))return;
                for(int i=0;i<getChildCount();i++)me.ancal.wmdroid.sdk.WmControls.restyleTree(getChildAt(i),regular,bold,selection,tabColor);
            }
        }
        FolderChoices icons=new FolderChoices();
        show(dialog("폴더 아이콘",new ClassicScrollView(this,icons)).setPropertySheet(null).setOnLanguageChanged(()->{
            for(FolderIcons.Icon folder:FolderIcons.all(this)){
                View cell=icons.findViewWithTag(folder.id);if(cell instanceof LinearLayout){((TextView)((LinearLayout)cell).getChildAt(1)).setText(folder.title);cell.setContentDescription(folder.title);}
            }
        }).create());
    }
    private void connectionSettings(){
        LinearLayout panel=column();panel.setPadding(8,8,8,0);
        String[] keys={"connection_ssid","connection_bt_names"},labels={"SSID 표시","연결된 Bluetooth 기기 표시"};
        for(int i=0;i<keys.length;i++){String key=keys[i];android.widget.CheckBox check=new android.widget.CheckBox(this);ClassicUi.check(check,regular);me.ancal.wmdroid.sdk.WmControls.bindText(check,UiLanguage.language(this),labels[i]);check.setChecked(prefs.getBoolean(key,false));panel.addView(check,new LinearLayout.LayoutParams(-1,44));
            check.setOnCheckedChangeListener((v,enabled)->{prefs.edit().putBoolean(key,enabled).apply();
                String permission=key.equals("connection_ssid")?Manifest.permission.ACCESS_FINE_LOCATION:Build.VERSION.SDK_INT>=31?Manifest.permission.BLUETOOTH_CONNECT:null;
                if(enabled&&permission!=null&&DeviceApi.permission(this,permission)!=PackageManager.PERMISSION_GRANTED)DeviceApi.request(this,new String[]{permission},11);
            });
        }
        TextView phoneAccess=authoredText("전화 상태 접근");ClassicUi.button(phoneAccess,false);phoneAccess.setOnClickListener(v->{if(Build.VERSION.SDK_INT>=23&&DeviceApi.permission(this,Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED)DeviceApi.request(this,new String[]{Manifest.permission.READ_PHONE_STATE},11);else refresh();});panel.addView(phoneAccess,new LinearLayout.LayoutParams(-1,44));
        TextView note=authoredText("SSID는 위치 권한과 위치 서비스가 필요할 수 있습니다.");panel.addView(note,new LinearLayout.LayoutParams(-1,-2));
        show(dialog("연결 모듈",panel).setPropertySheet(null).create());
    }
    private void languageSettings(){
        String[] languages=me.ancal.wmdroid.sdk.WmStrings.languages(this),names=new String[languages.length];
        for(int i=0;i<languages.length;i++){Locale locale=me.ancal.wmdroid.sdk.WmStrings.locale(languages[i]);names[i]=locale.getDisplayName(locale);}
        show(new UiDialog.Builder(this).setTitle("언어").setItems(names,(d,n)->{prefs.edit().putString("language",languages[n]).apply();modules.refresh();}).create());
    }
    private void backlight(){show(dialog("조명",new BrightnessPage(this,prefs,regular)).setPropertySheet(null).create());}
    private void displaySettings(){
        show(dialog("화면 표시",new DisplayPage(this,prefs,regular)).setPropertySheet(null).create());
    }
    private void experience(){
        ExperiencePage panel=new ExperiencePage(this,prefs,regular);
        show(dialog("옛 기기 동작",panel).setPropertySheet(null).setCloseValidator(panel::save).create());
    }
    private void themeSettings(){
        ThemePage panel=new ThemePage(this,prefs,regular);
        show(dialog("테마 색",panel).setPropertySheet(null).setCloseValidator(panel::save).create());
    }
    private void todaySettings(){todaySettings(0);}
    private void todaySettings(int initialTab){
        java.util.List<String> order=todayOrder();
        TodaySettingsPage panel=new TodaySettingsPage(this,prefs,regular,order,todayLabels(order.toArray(new String[0])),initialTab,new TodaySettingsPage.Options(){
            public boolean available(String id){return id.equals("작업")||id.equals("일정")||id.equals("소유자 정보")||id.equals("무선")||id.equals("날짜");}
            public void open(String id){switch(id){case "작업":taskOptions();break;case "일정":calendarOptions();break;case "소유자 정보":editOwner();break;case "무선":connectionSettings();break;case "날짜":titleClockSettings();break;}}
        },this::themeSettings,this::titleClockSettings);
        show(dialog("오늘",panel).setPropertySheet(null).create());
        worker.execute(()->{java.util.List<TodayModules.Module> found=TodayModules.discover(this);handler.post(()->{
            if(destroyed)return;
            for(UiDialog dialog=currentDialog;dialog!=null;dialog=dialog.previous)if(dialog.spec.view==panel){panel.updateProviders(found);break;}
        });});
    }
    private void calendarOptions(){
        int flags=prefs.getInt("calendar_flags",3);LinearLayout panel=column();panel.setPadding(8,8,8,0);
        android.widget.RadioGroup modes=new android.widget.RadioGroup(this);String[] labels={"다음 약속만","다가오는 약속"};
        for(int i=0;i<2;i++){android.widget.RadioButton choice=new android.widget.RadioButton(this);choice.setId(710+i);choice.setTag(i==0?"calendar_next":"calendar_upcoming");me.ancal.wmdroid.sdk.WmControls.radio(choice,regular);me.ancal.wmdroid.sdk.WmControls.bindText(choice,UiLanguage.language(this),labels[i]);modes.addView(choice,new LinearLayout.LayoutParams(-1,ClassicUi.ROW_HEIGHT));}
        modes.check((flags&2)!=0?710:711);panel.addView(modes);
        android.widget.CheckBox allDay=new android.widget.CheckBox(this);allDay.setTag("calendar_all_day");ClassicUi.check(allDay,regular);me.ancal.wmdroid.sdk.WmControls.bindText(allDay,UiLanguage.language(this),"종일 행사 표시");allDay.setChecked((flags&1)!=0);panel.addView(allDay,new LinearLayout.LayoutParams(-1,ClassicUi.ROW_HEIGHT));
        Runnable save=()->prefs.edit().putInt("calendar_flags",(allDay.isChecked()?1:0)|(modes.getCheckedRadioButtonId()==710?2:0)).apply();
        modes.setOnCheckedChangeListener((group,id)->save.run());allDay.setOnCheckedChangeListener((button,on)->save.run());
        show(dialog("오늘: 일정 옵션",panel).setPropertySheet(null).create());
    }
    private void titleClockSettings(){
        LinearLayout panel=column();panel.setPadding(8,8,8,0);
        android.widget.CheckBox clock=new android.widget.CheckBox(this);ClassicUi.check(clock,regular);
        me.ancal.wmdroid.sdk.WmControls.bindText(clock,UiLanguage.language(this),"모든 프로그램의 제목 표시줄에 시계 표시");clock.setChecked(prefs.getBoolean("title_clock",true));
        panel.addView(clock,new LinearLayout.LayoutParams(-1,-2));
        clock.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("title_clock",on).apply());
        show(dialog("시계 표시",panel).setPropertySheet(null).setCloseValidator(()->true).create());
    }
    private void chooseHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager manager = getSystemService(RoleManager.class);
            if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_HOME) && !manager.isRoleHeld(RoleManager.ROLE_HOME)) { startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_HOME), 20); return; }
        }
        launch(new Intent(Settings.ACTION_HOME_SETTINGS));
    }
    private void lockScreen() {
        DevicePolicyManager manager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE); ComponentName admin = new ComponentName(this, ScreenLock.class);
        if (manager.isAdminActive(admin)) { try { manager.lockNow(); } catch (SecurityException unavailable) { toast("화면 잠금 권한을 확인하세요."); } return; }
        show(new UiDialog.Builder(this).setTitle("장치 잠금").setMessage(ui("화면을 끄고 잠그려면 Android 기기 관리자에서 ‘화면 잠금’ 권한을 허용해야 합니다."))
                .setPositiveButton("잠금 활성화", (d, w) -> launch(new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin).putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, ui("오늘 런처의 장치 잠금 항목에서 화면을 잠급니다."))))
                .setNegativeButton("취소", null).create());
    }
    private void launch(Intent intent) {
        if(!delayedPresentation&&RetroSurface.delayMillis(this)>0){openApplet(()->launch(intent));return;}
        try { me.ancal.wmdroid.sdk.WmShell.launchWithoutAnimation(this,intent); } catch (android.content.ActivityNotFoundException | SecurityException unavailable) { toast("이 기능을 열 수 있는 앱이 없습니다."); }
    }
    /** App-owned WM windows: no Android dialogs, dim layer, elevation, ripple or transitions. */
    private static final class UiDialog {
        static final int BUTTON_POSITIVE = -1, BUTTON_NEGATIVE = -2;
        interface Click { void run(UiDialog dialog, int which); }
        interface Toggle { void run(UiDialog dialog, int which, boolean checked); }
        private final Builder spec;
        private UiDialog previous;
        private me.ancal.wmdroid.sdk.WmWindow window;
        private LinearLayout title;
        private me.ancal.wmdroid.sdk.WmCommandBar footer;
        private TextView positive, negative;
        private boolean dismissed;
        private UiDialog(Builder spec) { this.spec = spec; }
        TextView getButton(int which) { return which == BUTTON_POSITIVE ? positive : negative; }
        void leftCaption(String caption){spec.leftCaption=caption;if(positive!=null)positive.setText(spec.owner.ui(caption));}
        boolean dismiss() {
            if (dismissed) return true;
            TodayActivity a = spec.owner;
            if (a.currentDialog != this) return false;
            me.ancal.wmdroid.sdk.WmInputDialog input=window==null?null:me.ancal.wmdroid.sdk.WmInputDialog.find(window.client);
            if(input!=null){input.accept();return false;}
            if(window!=null&&!window.ownerCommandsAllowed())return false;
            if(spec.validateClose!=null&&!spec.validateClose.getAsBoolean())return false;
            dismissed = true;
            a.cancelApplet();a.closeStart();
            if(spec.onClose!=null)spec.onClose.run();
            ((android.view.inputmethod.InputMethodManager)a.getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(a.screen.getWindowToken(), 0);
            a.screen.removeView(window); a.trayBars.remove(title); a.currentDialog = previous;
            a.page = previous == null ? null : previous.window;
            for(View[] pair:a.todayRows.values())pair[0].setSelected(false);
            if (a.page != null) { if(previous.spec.view instanceof ContactEditorPage)((ContactEditorPage)previous.spec.view).resume(); a.screen.addView(a.page, new FrameLayout.LayoutParams(-1, -1)); }
            else if(a.appearanceRefreshPending)a.buildHome();
            return true;
        }
        private boolean complete() { return !dismissed && dismiss(); }
        void show() {
            TodayActivity a = spec.owner; a.closeStart();
            previous = a.currentDialog;
            if(previous!=null&&previous.spec.view instanceof ContactEditorPage)((ContactEditorPage)previous.spec.view).suspend();
            if (a.page != null) a.screen.removeView(a.page);
            window = new me.ancal.wmdroid.sdk.WmWindow(a);
            title=a.titleBar(spec.title,this::dismiss,spec.closeIsOk||me.ancal.wmdroid.sdk.WmInputDialog.find(window.client)!=null);window.title(title);
            FrameLayout content = spec.view instanceof SettingsPage||spec.view instanceof CalendarPage?new FrameLayout(a):new RetroSurface(a,a.delayedPresentation,spec.view instanceof ProgramsPage?RetroSurface.Role.GRID:RetroSurface.Role.FORM);
            window.client.addView(content,new FrameLayout.LayoutParams(-1,-1));
            View provided = spec.view;
            if (provided != null) {
                FrameLayout.LayoutParams fit = new FrameLayout.LayoutParams(-1, provided instanceof EditText ? 44 : -1);
                if (provided instanceof EditText) { fit.leftMargin=8; fit.rightMargin=8; fit.topMargin=16; }
                content.addView(provided, fit);
            } else {
                LinearLayout entries = a.column();
                if (spec.message != null) {
                    TextView message = a.vgaText(spec.message, 24, false); if(spec.authoredMessage)me.ancal.wmdroid.sdk.WmControls.bindText(message,UiLanguage.language(a),spec.message); message.setTextColor(Color.BLACK); message.setGravity(Gravity.TOP); message.setPadding(12, 12, 12, 12);
                    entries.addView(message, new LinearLayout.LayoutParams(-1, -2));
                }
                if (spec.items != null) for (int i=0;i<spec.items.length;i++) {
                    final int index=i;
                    if (spec.checked != null) {
                        android.widget.CheckBox check = new android.widget.CheckBox(a);
                        ClassicUi.check(check,a.regular); ClassicUi.entry(check,a.prefs.getBoolean("rose",false)); check.setPadding(8,0,8,0);
                        check.setText(spec.items[i]); check.setTypeface(a.regular);
                        check.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,24); check.setChecked(spec.checked[i]);
                        check.setOnCheckedChangeListener((button,checked) -> {spec.checked[index]=checked; spec.toggle.run(this,index,checked);});
                        if(spec.onLongClick!=null)check.setOnLongClickListener(v->{spec.onLongClick.run(this,index);return true;});
                        entries.addView(check,new LinearLayout.LayoutParams(-1,Math.max(44,a.valueLineCount(spec.items[i])*32+8)));
                    } else {
                        TextView entry = a.vgaText(spec.items[i],24,false); entry.setTextColor(Color.BLACK); entry.setPadding(8,0,8,0); ClassicUi.entry(entry,a.prefs.getBoolean("rose",false));
                        entry.setOnClickListener(v -> { if(complete())spec.choice.run(this,index); }); entries.addView(entry,new LinearLayout.LayoutParams(-1,44));
                    }
                }
                ClassicScrollView scroll = new ClassicScrollView(a,entries);scroll.setBackgroundColor(Color.WHITE);
                content.addView(scroll, new FrameLayout.LayoutParams(-1,-1));
            }
            footer=new me.ancal.wmdroid.sdk.WmCommandBar(a,a.regular,a.softBar());positive=footer.key(false);negative=footer.key(true);
            footer.setCommand(false,spec.positive==null?"":spec.positive,spec.positive==null?null:()->{if(!complete())return;if(spec.onPositive!=null)spec.onPositive.run(this,BUTTON_POSITIVE);});
            if(spec.neutral==null)footer.setCommand(true,spec.negative==null?a.ui("닫기"):spec.negative,()->{if(!complete())return;if(spec.onNegative!=null)spec.onNegative.run(this,BUTTON_NEGATIVE);});else footer.setMenu(true,a.ui("메뉴"),this::options);
            window.commands(footer);
            me.ancal.wmdroid.sdk.WmControls.localizeTree(footer,UiLanguage.language(a));
            if(spec.propertySheet){positive.setVisibility(View.INVISIBLE);negative.setVisibility(View.INVISIBLE);}
            if(spec.leftAction!=null||spec.rightAction!=null){positive.setVisibility(View.VISIBLE);negative.setVisibility(View.VISIBLE);if(spec.leftMenu)footer.setMenu(false,a.ui(spec.leftCaption),spec.leftAction);else footer.setCommand(false,a.ui(spec.leftCaption),spec.leftAction);if(spec.rightMenu)footer.setMenu(true,a.ui(spec.rightCaption),spec.rightAction);else footer.setCommand(true,a.ui(spec.rightCaption),spec.rightAction);}
            a.currentDialog=this; a.page=window; a.screen.addView(window,new FrameLayout.LayoutParams(-1,-1));if(a.trayReady)a.refreshTray(a.trayBars.get(title),new Date());else a.refresh();a.refreshPhoneAfterOpen();if(a.pointer!=null)a.pointer.bringToFront();
        }
        void restyle(){
            TodayActivity a=spec.owner;if(spec.view instanceof SettingsPage)((SettingsPage)spec.view).refreshIcons();a.trayBars.remove(title);
            title=a.titleBar(spec.title,this::dismiss,spec.closeIsOk||me.ancal.wmdroid.sdk.WmInputDialog.find(window.client)!=null);window.title(title);
            if(a.pendingLanguage)me.ancal.wmdroid.sdk.WmControls.localizeTree(window.client,UiLanguage.language(a));
            me.ancal.wmdroid.sdk.WmControls.restyleTree(window.client,a.regular,a.bold,a.accent,LauncherTheme.color(a,0xffc9e7e9,a.prefs.getBoolean("rose",false)));
            footer.restyle(a.regular,a.softBar());
            me.ancal.wmdroid.sdk.WmControls.localizeTree(footer,UiLanguage.language(a));
            if(spec.leftAction!=null||spec.rightAction!=null){positive.setText(a.ui(spec.leftCaption));negative.setText(a.ui(spec.rightCaption));}
            if(a.pendingLanguage&&spec.onLanguageChanged!=null)spec.onLanguageChanged.run();
        }
        private void options() {
            TodayActivity a=spec.owner;a.commandMenu(true,new String[]{spec.neutral,spec.negative==null?a.ui("닫기"):spec.negative},-1,index->{if(!complete())return;if(index==0){if(spec.onNeutral!=null)spec.onNeutral.run(this,0);}else if(spec.onNegative!=null)spec.onNegative.run(this,BUTTON_NEGATIVE);});
        }
        static final class Builder {
            private final TodayActivity owner;
            private String title="", message, positive, negative, neutral;
            private View view;
            private boolean propertySheet,closeIsOk,leftMenu,rightMenu,authoredMessage;
            private Runnable onClose,leftAction,rightAction,onLanguageChanged,onBack;
            private String leftCaption="",rightCaption="";
            private java.util.function.BooleanSupplier validateClose;
            private String[] items;
            private boolean[] checked;
            private Click choice, onPositive, onNegative, onNeutral, onLongClick;
            private Toggle toggle;
            Builder(TodayActivity owner){this.owner=owner;}
            Builder setTitle(String title){this.title=title;return this;}
            Builder setView(View view){this.view=view;return this;}
            Builder setCloseIsOk(){closeIsOk=true;return this;}
            Builder setPropertySheet(Runnable save){propertySheet=true;closeIsOk=true;onClose=save;return this;}
            Builder setOnLanguageChanged(Runnable action){onLanguageChanged=action;return this;}
            Builder setCommands(String left,Runnable l,String right,Runnable r){leftCaption=left;leftAction=l;rightCaption=right;rightAction=r;return this;}
            Builder setCommandMenus(boolean left,boolean right){leftMenu=left;rightMenu=right;return this;}
            Builder setCloseValidator(java.util.function.BooleanSupplier validate){validateClose=validate;return this;}
            Builder setOnBack(Runnable action){onBack=action;return this;}
            Builder hideSoftkeys(){propertySheet=true;return this;}
            Builder setMessage(String message){this.message=message;authoredMessage=false;return this;}
            Builder setAuthoredMessage(String message){this.message=message;authoredMessage=true;return this;}
            Builder setLongClick(Click action){onLongClick=action;return this;}
            Builder setItems(String[] items,Click choice){this.items=items;this.choice=choice;return this;}
            Builder setMultiChoiceItems(String[] items,boolean[] checked,Toggle toggle){this.items=items;this.checked=checked;this.toggle=toggle;return this;}
            Builder setPositiveButton(String label,Click action){positive=owner.ui(label);onPositive=action;return this;}
            Builder setNegativeButton(String label,Click action){negative=owner.ui(label);onNegative=action;return this;}
            Builder setNeutralButton(String label,Click action){neutral=owner.ui(label);onNeutral=action;return this;}
            UiDialog create(){return new UiDialog(this);}
        }
    }

    private void toast(String message) {
        if(screen==null)return;
        TextView notice=vgaText(ui(message),24,false);notice.setTextColor(Color.BLACK);notice.setGravity(Gravity.CENTER);notice.setPadding(8,8,8,8);
        GradientDrawable frame=new GradientDrawable();frame.setColor(0xffffffcc);frame.setStroke(2,0xff808080);notice.setBackgroundDrawable(frame);
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(440,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);position.bottomMargin=104;
        FrameLayout target=screen;target.addView(notice,position);handler.postDelayed(()->target.removeView(notice),2500);
    }
}
