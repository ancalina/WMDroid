package me.ancal.wmdroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import me.ancal.wmdroid.sdk.WmControls;
import me.ancal.wmdroid.sdk.WmFonts;
import me.ancal.wmdroid.sdk.WmIconCaption;

/** Programs shell. Package metadata and visible icons never load on the UI thread. */
final class ProgramsPage extends LinearLayout implements WmControls.RestyleListener {
    interface Host { void launch(ResolveInfo info); void details(ResolveInfo info); default void builtin(String command) {} }
    private static final int ROW_HEIGHT=132;
    private static AppCache appCache;
    private final AppCache cache;
    private Typeface font;
    private int selection;
    private final ExecutorService worker;
    private final PackageManager packages;
    private final SharedPreferences prefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener iconPreferences=(p,key)->{if(key!=null&&key.startsWith("android_icons_"))refreshIconMode();else if("hidden_apps".equals(key))filter();else if("language".equals(key))refreshLanguage();};
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final EditText search;
    private final GridView grid;
    private final TextView empty;
    private final ClassicScrollBar bar;
    private final Rect barTouchBounds=new Rect();
    private boolean barTouch;
    private final AppsAdapter adapter=new AppsAdapter();
    private final List<App> all=new ArrayList<>(),shown=new ArrayList<>();
    private final LruCache<String,Bitmap> icons;
    private final Map<String,Future<?>> iconTasks=new HashMap<>();
    private final int iconPixels;
    private Future<?> listTask;
    private volatile int generation,iconGeneration;
    private boolean attached,loaded;
    private me.ancal.wmdroid.sdk.WmIconPolicy iconPolicy;

    private static int iconPixels(Context context){return Math.max(64,Math.round(64*context.getResources().getDisplayMetrics().widthPixels/480f));}
    private static AppCache prepareCache(Context context,ExecutorService worker,int pixels,me.ancal.wmdroid.sdk.WmIconPolicy policy){
        if(appCache==null)appCache=new AppCache(context.getApplicationContext());
        appCache.setWorker(worker);appCache.configure(context,pixels,policy);return appCache;
    }
    static void prewarm(Context context,ExecutorService worker){
        SharedPreferences prefs=context.getSharedPreferences("today",Context.MODE_PRIVATE);
        AppCache cache=prepareCache(context,worker,iconPixels(context),me.ancal.wmdroid.sdk.WmIconPolicy.read(prefs));
        if(!cache.ready&&cache.warming==null)cache.warm();
    }
    ProgramsPage(Context context,Typeface regular,boolean rose,ExecutorService worker,Host host){
        super(context);this.font=regular;this.selection=LauncherTheme.selection(context,rose);this.worker=worker;packages=context.getPackageManager();prefs=context.getSharedPreferences("today",Context.MODE_PRIVATE);iconPolicy=me.ancal.wmdroid.sdk.WmIconPolicy.read(prefs);
        iconPixels=iconPixels(context);cache=prepareCache(context,worker,iconPixels,iconPolicy);icons=cache.icons;
        setOrientation(VERTICAL);setBackgroundColor(Color.WHITE);setPadding(0,0,0,0);
        search=new EditText(context);search.setTypeface(font);WmFonts.attach(search,font);search.setTextSize(TypedValue.COMPLEX_UNIT_PX,24);
        search.setTextColor(Color.BLACK);search.setHintTextColor(0xff707070);search.setPadding(6,4,6,4);
        GradientDrawable border=new GradientDrawable();border.setColor(Color.WHITE);border.setStroke(2,0xff808080);search.setBackgroundDrawable(border);
        WmControls.bindHint(search,UiLanguage.language(getContext()),"프로그램 검색");search.setSingleLine(true);search.setSelectAllOnFocus(true);search.setVisibility(GONE);
        addView(search,new LayoutParams(-1,44));
        LinearLayout content=new LinearLayout(context){
            @Override protected void onMeasure(int widthSpec,int heightSpec){
                int height=MeasureSpec.getSize(heightSpec);
                if(bar!=null&&MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED&&height>0)bar.setVisibility(range()>height?VISIBLE:GONE);
                super.onMeasure(widthSpec,heightSpec);
            }
        };addView(content,new LayoutParams(-1,0,1));
        FrameLayout listHost=new FrameLayout(context);content.addView(listHost,new LayoutParams(0,-1,1));
        grid=new GridView(context);grid.setNumColumns(3);grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setColumnWidth(148);
        grid.setHorizontalSpacing(0);grid.setVerticalSpacing(0);grid.setPadding(0,0,0,0);grid.setOverScrollMode(OVER_SCROLL_NEVER);
        grid.setVerticalScrollBarEnabled(false);grid.setHorizontalScrollBarEnabled(false);
        // GridView retains selection after a touch. Touch feedback belongs only to
        // ACTION_DOWN..UP/CANCEL; the focus selector remains available for a keyboard.
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        listHost.addView(grid,new FrameLayout.LayoutParams(-1,-1));
        empty=text("");notice("프로그램을 불러오는 중…");empty.setGravity(Gravity.CENTER);listHost.addView(empty,new FrameLayout.LayoutParams(-1,-1));grid.setEmptyView(empty);
        bar=new ClassicScrollBar(context,new ClassicScrollBar.Host(){
            public int extent(){return ProgramsPage.this.extent();}
            public int range(){return ProgramsPage.this.range();}
            public int offset(){return ProgramsPage.this.offset();}
            public int lineStep(){return ROW_HEIGHT;}
            public void scrollTo(int offset){int target=Math.max(0,Math.min(Math.max(0,range()-extent()),offset));if(Build.VERSION.SDK_INT>=19)grid.scrollListBy(target-offset());else grid.smoothScrollToPositionFromTop((target/ROW_HEIGHT)*3,-target%ROW_HEIGHT,0);}
            public void scrollBy(int delta){scrollTo(Math.max(0,offset()+delta));}
            public void stopFling(){grid.smoothScrollBy(0,0);}
        });content.addView(bar,new LayoutParams(26,-1));bar.setVisibility(GONE);
        grid.setOnScrollListener(new AbsListView.OnScrollListener(){
            public void onScrollStateChanged(AbsListView view,int state){}
            public void onScroll(AbsListView view,int first,int visible,int total){cancelOffscreenIconTasks();updateScrollBar();updateKeyboardSelection();}
        });
        grid.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updateScrollBar());
        grid.setAdapter(adapter);
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){updateKeyboardSelection();}
            public void onNothingSelected(AdapterView<?> parent){updateKeyboardSelection();}
        });
        grid.setOnFocusChangeListener((v,focused)->updateKeyboardSelection());
        grid.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN)for(int i=0;i<grid.getChildCount();i++)grid.getChildAt(i).setActivated(false);return false;});
        grid.setOnItemClickListener((parent,view,position,id)->{clearTouch(view);App app=shown.get(position);if(app.command.isEmpty())host.launch(app.info);else host.builtin(app.command);});
        grid.setOnItemLongClickListener((parent,view,position,id)->{clearTouch(view);App app=shown.get(position);if(app.command.isEmpty())host.details(app.info);return true;});
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){filter();}
            public void afterTextChanged(Editable s){}
        });
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
            barTouch=false;
            if(bar!=null&&bar.getVisibility()==VISIBLE){
                barTouchBounds.set(0,0,bar.getWidth(),bar.getHeight());offsetDescendantRectToMyCoords(bar,barTouchBounds);
                // Finger allowance is invisible and limited to the thumb vicinity.
                // The rest of the grid keeps its normal swipe, tap and long-press paths.
                barTouch=event.getX()<barTouchBounds.left
                        &&bar.hitThumb(event.getX()-barTouchBounds.left,event.getY()-barTouchBounds.top);
            }
        }
        if(!barTouch)return super.dispatchTouchEvent(event);
        MotionEvent local=MotionEvent.obtain(event);local.offsetLocation(-barTouchBounds.left,-barTouchBounds.top);
        boolean handled=bar.dispatchTouchEvent(local);local.recycle();
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)barTouch=false;
        return handled;
    }
    void showSearch(){search.setVisibility(VISIBLE);search.requestFocus();((InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(search,InputMethodManager.SHOW_IMPLICIT);}
    public void onWmRestyle(Typeface regular,Typeface bold,int selection,int tabColor){
        this.font=regular;this.selection=selection;
        search.setTypeface(font);WmFonts.attach(search,font);empty.setTypeface(font);WmFonts.attach(empty,font);
        for(int i=0;i<grid.getChildCount();i++)if(grid.getChildAt(i) instanceof LinearLayout)styleCell((LinearLayout)grid.getChildAt(i));
        refreshLanguage();
        cache.configure(getContext(),iconPixels,iconPolicy);
    }
    private void notice(String authored){WmControls.bindText(empty,UiLanguage.language(getContext()),authored);}
    private TextView text(String label){TextView view=new TextView(getContext());view.setText(label);view.setTypeface(font);WmFonts.attach(view,font);view.setTextSize(TypedValue.COMPLEX_UNIT_PX,22);view.setIncludeFontPadding(false);view.setTextColor(Color.BLACK);return view;}
    private void styleCell(LinearLayout cell){
        WmIconCaption caption=(WmIconCaption)cell.getChildAt(1);if(caption.getTypeface()!=font){caption.setTypeface(font);WmFonts.attach(caption,font);}
        caption.setSelectionColor(selection);cell.setBackgroundColor(Color.WHITE);
    }
    private void clearTouch(View child){child.setPressed(false);child.setSelected(false);grid.setPressed(false);}
    private void updateKeyboardSelection(){for(int i=0;i<grid.getChildCount();i++)grid.getChildAt(i).setActivated(grid.isFocused()&&!grid.isInTouchMode()&&grid.getFirstVisiblePosition()+i==grid.getSelectedItemPosition());}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;generation++;iconGeneration++;cache.setWorker(worker);cache.configure(getContext(),iconPixels,iconPolicy);cache.pages.add(this);prefs.registerOnSharedPreferenceChangeListener(iconPreferences);refreshIconMode();if(cache.ready){installApps(cache.apps,null);return;}if(!cache.apps.isEmpty())installApps(cache.apps,null);if(cache.warming==null)loadApps();}
    @Override protected void onDetachedFromWindow(){attached=false;generation++;iconGeneration++;cache.pages.remove(this);prefs.unregisterOnSharedPreferenceChangeListener(iconPreferences);if(listTask!=null)listTask.cancel(true);listTask=null;cancelIconTasks();barTouch=false;bar.stop();super.onDetachedFromWindow();}
    private void cancelIconTasks(){for(Future<?> task:iconTasks.values())task.cancel(true);iconTasks.clear();}
    private void cancelOffscreenIconTasks(){
        if(grid.getChildCount()==0||iconTasks.isEmpty())return;
        HashSet<String> visible=new HashSet<>();
        for(int i=0;i<grid.getChildCount();i++){View cell=grid.getChildAt(i);if(cell instanceof LinearLayout){Object key=((LinearLayout)cell).getChildAt(0).getTag();if(key instanceof String)visible.add((String)key);}}
        if(visible.isEmpty())return;
        Iterator<Map.Entry<String,Future<?>>> tasks=iconTasks.entrySet().iterator();
        while(tasks.hasNext()){Map.Entry<String,Future<?>> task=tasks.next();if(!visible.contains(task.getKey())){task.getValue().cancel(true);tasks.remove();}}
    }
    private void refreshIconMode(){
        me.ancal.wmdroid.sdk.WmIconPolicy next=me.ancal.wmdroid.sdk.WmIconPolicy.read(prefs);if(next.enabled==iconPolicy.enabled&&next.mode.equals(iconPolicy.mode)&&next.json.equals(iconPolicy.json))return;iconPolicy=next;cache.configure(getContext(),iconPixels,next);iconGeneration++;cancelIconTasks();icons.evictAll();for(App app:all)app.iconFailed=false;if(attached)adapter.notifyDataSetChanged();
    }
    private void refreshLanguage(){
        if(UiLanguage.locale(getContext()).equals(cache.locale)&&getResources().getConfiguration().locale.toString().equals(cache.localeKey))return;
        int before=cache.revision;cache.configure(getContext(),iconPixels,iconPolicy);
        WmControls.bindHint(search,UiLanguage.language(getContext()),"프로그램 검색");
        if(before!=cache.revision){if(cache.pages.isEmpty())cache.warm();else for(ProgramsPage page:new ArrayList<>(cache.pages))page.refreshPackages();}
    }
    private RetroSurface presentationSurface(){
        for(android.view.ViewParent parent=getParent();parent!=null;parent=parent.getParent())if(parent instanceof RetroSurface)return (RetroSurface)parent;
        return null;
    }
    private static List<App> readApps(PackageManager packages,Locale locale){
        List<App> result=new ArrayList<>();
        List<ResolveInfo> found=packages.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
        for(ResolveInfo info:found){if(Thread.currentThread().isInterrupted())return null;CharSequence label=info.loadLabel(packages);result.add(new App(info,label==null?info.activityInfo.name:label.toString(),locale));}
        Collator collator=Collator.getInstance(locale);result.sort((a,b)->collator.compare(a.label,b.label));return result;
    }
    private static List<App> orderApps(List<App> source,Locale locale){
        List<App> result=new ArrayList<>(source.size());for(App old:source){App app=new App(old.info,old.label,locale);app.iconFailed=old.iconFailed;result.add(app);}
        Collator collator=Collator.getInstance(locale);result.sort((a,b)->collator.compare(a.label,b.label));return result;
    }
    private void installApps(List<App> result,String error){installApps(result,error,true);}
    private void installApps(List<App> result,String error,boolean resetScroll){
        loaded=error==null;replaceApps(result);filter(resetScroll);if(error!=null)notice(error);
    }
    private void installLanguageApps(List<App> result){WmControls.bindHint(search,UiLanguage.language(getContext()),"프로그램 검색");replaceApps(result);filter(false,true);}
    /** Only external package metadata lives in AppCache; local clients need no PM query. */
    private void replaceApps(List<App> external){
        all.clear();all.addAll(external);Locale locale=UiLanguage.locale(getContext());
        String[] commands={"calendar","contacts","notes","phone","messages","browser","media"};
        int[] resources={R.drawable.wm_calendar,R.drawable.wm_contacts,R.drawable.wm_notes,R.drawable.wm_phone_hd,R.drawable.wm_mail,R.drawable.wm_browser,R.drawable.wm_media};
        for(int i=0;i<commands.length;i++)all.add(new App(commands[i],MenuSettingsPage.label(getContext(),commands[i]),resources[i],locale));
        Collator collator=Collator.getInstance(locale);all.sort((a,b)->collator.compare(a.label,b.label));
    }
    private void refreshPackages(){generation++;iconGeneration++;if(listTask!=null)listTask.cancel(true);cancelIconTasks();loadApps();}
    private void loadApps(){
        final int ticket=generation,revision=cache.revision;final Locale locale=UiLanguage.locale(getContext());final boolean resetScroll=!loaded;
        final RetroSurface surface=presentationSurface();if(surface!=null)surface.cancel();
        try{listTask=worker.submit(()->{
            List<App> result=new ArrayList<>();String failure=null;
            try{result=readApps(packages,locale);if(result==null)return;}
            catch(RuntimeException unavailable){failure="프로그램 목록을 읽지 못했습니다.";}
            final List<App> apps=result;final String error=failure;ui.post(()->{if(!attached||ticket!=generation||revision!=cache.revision)return;listTask=null;if(error==null){cache.apps=locale.equals(cache.locale)?apps:orderApps(apps,cache.locale);cache.ready=true;}installApps(error==null?cache.apps:apps,error,resetScroll);if(surface!=null)surface.replay(true);});
        });}catch(RejectedExecutionException stopped){notice("프로그램 목록을 읽지 못했습니다.");if(surface!=null)surface.replay(true);}
    }
    /** Process cache owns only application Context; attached pages unregister on detach. */
    private static final class AppCache {
        final Context context;final Handler ui=new Handler(Looper.getMainLooper());final BroadcastReceiver changes;
        final List<ProgramsPage> pages=new ArrayList<>();
        final LruCache<String,Bitmap> icons=new LruCache<String,Bitmap>((int)Math.max(4L*1024*1024,Math.min(32L*1024*1024,Runtime.getRuntime().maxMemory()/8))){
            @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}
            // Eviction must not recycle bitmaps still displayed by visible cells.
        };
        List<App> apps=new ArrayList<>();ExecutorService worker;Future<?> warming;
        boolean ready;int revision,iconRevision,pixels;String localeKey="",policyKey="",resourceKey="";Locale locale=Locale.KOREAN;
        AppCache(Context context){
            this.context=context;
            changes=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){
                SharedPreferences prefs=AppCache.this.context.getSharedPreferences("today",Context.MODE_PRIVATE);
                configure(AppCache.this.context,pixels,me.ancal.wmdroid.sdk.WmIconPolicy.read(prefs));
                invalidate();if(pages.isEmpty())warm();else for(ProgramsPage page:new ArrayList<>(pages))page.refreshPackages();
            }};
            IntentFilter packages=new IntentFilter();packages.addAction(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);packages.addAction(Intent.ACTION_PACKAGE_REPLACED);packages.addDataScheme("package");
            IntentFilter storage=new IntentFilter();storage.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);storage.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);storage.addAction(Intent.ACTION_LOCALE_CHANGED);
            if(Build.VERSION.SDK_INT>=33){context.registerReceiver(changes,packages,Context.RECEIVER_NOT_EXPORTED);context.registerReceiver(changes,storage,Context.RECEIVER_NOT_EXPORTED);}else{context.registerReceiver(changes,packages);context.registerReceiver(changes,storage);}
        }
        void setWorker(ExecutorService next){if(warming!=null&&(worker!=next||next.isShutdown()||warming.isCancelled())){warming.cancel(true);warming=null;revision++;}worker=next;}
        void configure(Context owner,int pixels,me.ancal.wmdroid.sdk.WmIconPolicy policy){
            Locale next=UiLanguage.locale(owner);String key=owner.getResources().getConfiguration().locale.toString();
            if(!key.equals(localeKey)){locale=next;localeKey=key;invalidate();}
            else if(!next.equals(locale)){locale=next;apps=orderApps(apps,next);for(ProgramsPage page:new ArrayList<>(pages))if(ready||!apps.isEmpty()||!page.all.isEmpty())page.installLanguageApps(apps);}
            String nextPolicy=policy.enabled+"/"+policy.mode+"/"+policy.json;
            if(this.pixels!=pixels||!nextPolicy.equals(policyKey)){this.pixels=pixels;policyKey=nextPolicy;iconRevision++;icons.evictAll();for(App app:apps)app.iconFailed=false;}
            String nextResources=me.ancal.wmdroid.sdk.WmResources.revision(owner);
            if(!nextResources.equals(resourceKey)){
                resourceKey=nextResources;iconRevision++;
                for(String iconKey:icons.snapshot().keySet())if(iconKey.startsWith("builtin:"))icons.remove(iconKey);
                for(ProgramsPage page:new ArrayList<>(pages))if(page.attached){
                    for(App app:page.all)if(!app.command.isEmpty())app.iconFailed=false;
                    page.adapter.notifyDataSetChanged();
                }
            }
        }
        void invalidate(){ready=false;revision++;iconRevision++;icons.evictAll();for(App app:apps)app.iconFailed=false;if(warming!=null)warming.cancel(true);warming=null;}
        void warm(){
            if(ready||warming!=null||worker==null||worker.isShutdown())return;
            final int ticket=revision;final Locale order=locale;
            try{warming=worker.submit(()->{
                List<App> result=null;try{result=readApps(context.getPackageManager(),order);}catch(RuntimeException unavailable){/* Retry on the next open; retain the last snapshot. */}
                final List<App> found=result;
                ui.post(()->{if(ticket!=revision)return;warming=null;if(found==null){for(ProgramsPage page:new ArrayList<>(pages))page.loadApps();return;}apps=order.equals(locale)?found:orderApps(found,locale);ready=true;for(ProgramsPage page:new ArrayList<>(pages))page.installApps(apps,null,!page.loaded);});
            });}catch(RejectedExecutionException stopped){warming=null;}
        }
    }
    private void filter(){filter(true);}
    private void filter(boolean resetScroll){filter(resetScroll,false);}
    private void filter(boolean resetScroll,boolean onlyChanged){
        String query=search.getText().toString().toLowerCase(UiLanguage.locale(getContext()));List<App> matches=new ArrayList<>();
        HashSet<String> hidden=new HashSet<>();
        try{JSONArray ids=new JSONArray(prefs.getString("hidden_apps","[]"));for(int i=0;i<ids.length();i++)if(ids.opt(i) instanceof String)hidden.add(me.ancal.wmdroid.sdk.WmIconPolicy.canonical(ids.getString(i)));}catch(JSONException invalid){}
        for(App app:all)if(app.searchLabel.contains(query)&&(hidden.isEmpty()||!hidden.contains(me.ancal.wmdroid.sdk.WmIconPolicy.canonical(app.key))))matches.add(app);
        boolean changed=shown.size()!=matches.size();
        if(!changed)for(int i=0;i<shown.size();i++)if(!shown.get(i).key.equals(matches.get(i).key)||!shown.get(i).label.equals(matches.get(i).label)){changed=true;break;}
        shown.clear();shown.addAll(matches);
        if(loaded)notice(query.isEmpty()?"프로그램이 없습니다.":"일치하는 프로그램이 없습니다.");
        if(!onlyChanged||changed){bar.stop();adapter.notifyDataSetChanged();if(resetScroll)grid.setSelection(0);updateScrollBar();}
    }
    private void requestIcon(App app,ImageView image){
        Bitmap cached=icons.get(app.key);image.setTag(app.key);image.setImageBitmap(cached);
        if(cached!=null||!attached||iconTasks.containsKey(app.key)||app.iconFailed)return;
        final int ticket=iconGeneration,revision=cache.iconRevision;final boolean mode=iconPolicy.allows(app.key);
        final Future<?>[] pending=new Future<?>[1];
        try{Future<?> task=worker.submit(()->{
            Bitmap bitmap=null;
            if(Thread.currentThread().isInterrupted()||ticket!=iconGeneration)return;
            try{Drawable source=app.command.isEmpty()?app.info.loadIcon(packages):LauncherArtwork.load(getContext(),app.iconResource);bitmap=me.ancal.wmdroid.sdk.WmAppIcons.render(source,iconPixels,app.command.isEmpty()&&mode);}
            catch(RuntimeException unavailable){/* A removed package may disappear between query and draw. */}
            final Bitmap result=bitmap;
            ui.post(()->{
                if(iconTasks.get(app.key)!=pending[0]){if(result!=null)result.recycle();return;}
                iconTasks.remove(app.key);
                if(!attached||ticket!=iconGeneration||revision!=cache.iconRevision){
                    if(result!=null)result.recycle();
                    if(attached&&ticket==iconGeneration)for(int i=0;i<grid.getChildCount();i++){View cell=grid.getChildAt(i);if(cell instanceof LinearLayout){ImageView target=(ImageView)((LinearLayout)cell).getChildAt(0);if(app.key.equals(target.getTag()))requestIcon(app,target);}}
                    return;
                }
                app.iconFailed=result==null;if(result==null)return;icons.put(app.key,result);
                // A recycled cell can already show another app. Update matching
                // visible cells only; do not invalidate the entire grid per icon.
                for(int i=0;i<grid.getChildCount();i++){View cell=grid.getChildAt(i);if(cell instanceof LinearLayout){ImageView target=(ImageView)((LinearLayout)cell).getChildAt(0);if(app.key.equals(target.getTag()))target.setImageBitmap(result);}}
            });
        });pending[0]=task;iconTasks.put(app.key,task);}catch(RejectedExecutionException stopped){/* Activity is being destroyed. */}
    }
    private final class AppsAdapter extends BaseAdapter {
        public int getCount(){return shown.size();}public App getItem(int position){return shown.get(position);}public long getItemId(int position){return position;}
        public View getView(int position,View recycled,ViewGroup parent){
            LinearLayout cell;
            if(recycled instanceof LinearLayout)cell=(LinearLayout)recycled;
            else{
                cell=new LinearLayout(getContext());cell.setOrientation(VERTICAL);cell.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);cell.setPadding(0,8,0,0);
                ImageView icon=new ImageView(getContext());icon.setScaleType(ImageView.ScaleType.FIT_CENTER);cell.addView(icon,new LayoutParams(64,64));
                WmIconCaption caption=new WmIconCaption(getContext(),font,selection);caption.setDuplicateParentStateEnabled(true);cell.addView(caption,new LayoutParams(-1,56));
            }
            styleCell(cell);App app=getItem(position);cell.setSelected(false);cell.setActivated(grid.isFocused()&&!grid.isInTouchMode()&&position==grid.getSelectedItemPosition());((TextView)cell.getChildAt(1)).setText(app.label);cell.setContentDescription(app.label);requestIcon(app,(ImageView)cell.getChildAt(0));cell.setLayoutParams(new AbsListView.LayoutParams(-1,ROW_HEIGHT));return cell;
        }
    }
    private static final class App {
        final ResolveInfo info;final String label,searchLabel,key,command;final int iconResource;boolean iconFailed;
        App(ResolveInfo info,String label){this(info,label,Locale.KOREAN);}
        App(ResolveInfo info,String label,Locale locale){this.info=info;this.label=label;searchLabel=label.toLowerCase(locale);key=info.activityInfo.packageName+"/"+info.activityInfo.name;command="";iconResource=0;}
        App(String command,String label,int iconResource,Locale locale){this.info=null;this.command=command;this.iconResource=iconResource;this.label=label;searchLabel=label.toLowerCase(locale);key="builtin:"+command;}
    }
    private int extent(){return grid.getHeight();}
    private int range(){return ((shown.size()+2)/3)*ROW_HEIGHT;}
    private int offset(){View first=grid.getChildAt(0);return first==null?0:Math.max(0,(grid.getFirstVisiblePosition()/3)*ROW_HEIGHT-first.getTop());}
    private void updateScrollBar(){if(bar!=null)bar.refresh();}

}
