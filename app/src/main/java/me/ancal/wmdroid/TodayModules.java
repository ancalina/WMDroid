package me.ancal.wmdroid;

import android.content.*;
import android.content.pm.*;
import android.database.*;
import android.os.*;
import android.net.Uri;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import me.ancal.wmdroid.sdk.TodayContract;

/** External providers are opt-in, queried outside UI, rendered by this host. */
final class TodayModules {
    private static final int CACHE_BYTES=256*1024,CACHE_ROWS=256;
    static final class Module {
        final String authority,pkg,label;
        final boolean customViews;final long revision;
        private Module(String authority,String pkg,String label,boolean customViews,long revision){this.authority=authority;this.pkg=pkg;this.label=label;this.customViews=customViews;this.revision=revision;}
        Module(ProviderInfo p,PackageManager pm){authority=p.authority;pkg=p.packageName;customViews=p.metaData!=null&&p.metaData.getBoolean(TodayContract.VIEW_META,false);CharSequence name=p.loadLabel(pm);label=name==null?pkg:name.toString();long updated=0;try{updated=pm.getPackageInfo(pkg,0).lastUpdateTime;}catch(PackageManager.NameNotFoundException removed){}revision=updated;}
    }
    static final class Row {
        final Module module;final String id,title,detail,activity;final int icon;
        final android.widget.RemoteViews view;final int height;
        Row(Module m,String id,String title,String detail,int icon,String activity){this(m,id,title,detail,icon,activity,null,0);}
        Row(Module m,String id,String title,String detail,int icon,String activity,android.widget.RemoteViews view,int height){module=m;this.id=id;this.title=title;this.detail=detail;this.icon=icon;this.activity=activity;this.view=view;this.height=height;}
    }
    private final Context context;private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();private final java.util.function.Consumer<List<Row>> delivered;
    private java.util.function.Consumer<List<Module>> providersChanged;
    // Binder widget calls cannot be canceled; keep public queries on their own worker.
    private final ThreadPoolExecutor widgets=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<Runnable>());
    private List<Row> latest=Collections.emptyList();
    private final List<Uri> observed=new ArrayList<>();private Future<?> pending,pendingWidget;private Query signal;private int generation;private boolean stopped;
    private final ContentObserver observer=new ContentObserver(main){@Override public void onChange(boolean self){refresh();}};
    TodayModules(Context c,java.util.function.Consumer<List<Row>> delivered){context=c;this.delivered=delivered;latest=restoreRows();if(!latest.isEmpty())delivered.accept(latest);}
    void setProviderListener(java.util.function.Consumer<List<Module>> listener){providersChanged=listener;}
    private List<Row> restoreRows(){
        SharedPreferences cache=context.getSharedPreferences("today_modules_cache",0);
        try{
            String raw=cache.getString("snapshot","");if(raw.isEmpty())return Collections.emptyList();
            if(raw.length()>CACHE_BYTES||raw.getBytes("UTF-8").length>CACHE_BYTES)throw new JSONException("Cache too large");
            JSONTokener input=new JSONTokener(raw);Object document=input.nextValue();if(!(document instanceof JSONObject)||input.nextClean()!=0)throw new JSONException("Invalid document");
            JSONObject saved=(JSONObject)document;if(integer(saved,"version")!=1)throw new JSONException("Cache version");
            if(!UiLanguage.language(context).equals(string(saved,"language")))return Collections.emptyList();
            JSONArray rows=saved.getJSONArray("rows");if(rows.length()>CACHE_ROWS)throw new JSONException("Too many rows");
            List<Row> ready=new ArrayList<>();Set<String> ids=new HashSet<>();Map<String,Module> modules=new HashMap<>();Map<String,Integer> counts=new HashMap<>();SharedPreferences prefs=context.getSharedPreferences("today",0);
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);String authority=string(row,"authority"),pkg=string(row,"pkg"),label=string(row,"label"),id=string(row,"id"),title=string(row,"title"),detail=string(row,"detail");
                long revision=integer(row,"revision"),height=integer(row,"height");Object custom=row.get("custom");
                if(!authority.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*")||!pkg.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")||revision<0||id.isEmpty()||title.isEmpty()||!(custom instanceof Boolean)||height!=0&&(height<TodayContract.MIN_HEIGHT||height>TodayContract.MAX_HEIGHT)||!ids.add(authority+"\n"+id))throw new JSONException("Invalid row");
                int count=counts.containsKey(authority)?counts.get(authority)+1:1;if(count>TodayContract.MAX_ROWS)throw new JSONException("Too many provider rows");counts.put(authority,count);
                Module module=modules.get(authority);
                if(module==null){module=new Module(authority,pkg,label,(Boolean)custom,revision);modules.put(authority,module);}
                else if(!pkg.equals(module.pkg)||revision!=module.revision||!label.equals(module.label)||(Boolean)custom!=module.customViews)throw new JSONException("Conflicting module");
                if(prefs.getBoolean("module_"+authority,false))ready.add(new Row(module,id,title,detail,0,"",null,module.customViews?(int)height:0));
            }
            return Collections.unmodifiableList(ready);
        }catch(Exception invalid){cache.edit().remove("snapshot").apply();return Collections.emptyList();}
    }
    private static String string(JSONObject value,String key)throws JSONException{Object raw=value.get(key);if(!(raw instanceof String)||((String)raw).length()>TodayContract.MAX_TEXT||((String)raw).indexOf('\0')>=0)throw new JSONException("Invalid text");return (String)raw;}
    private static long integer(JSONObject value,String key)throws JSONException{Object raw=value.get(key);if(!(raw instanceof Integer)&&!(raw instanceof Long))throw new JSONException("Invalid integer");return ((Number)raw).longValue();}
    // Persist text and the last custom height only; RemoteViews stay in memory.
    private void persistRows(List<Row> rows,String language){
        SharedPreferences cache=context.getSharedPreferences("today_modules_cache",0);
        try{
            JSONArray saved=new JSONArray();
            for(Row row:rows){
                if(saved.length()==CACHE_ROWS)break;
                int height=row.module.customViews&&row.height>=TodayContract.MIN_HEIGHT&&row.height<=TodayContract.MAX_HEIGHT?row.height:0;
                JSONObject value=new JSONObject().put("authority",row.module.authority).put("pkg",row.module.pkg).put("label",bounded(row.module.label)).put("custom",row.module.customViews).put("revision",row.module.revision).put("id",bounded(row.id)).put("title",bounded(row.title)).put("detail",bounded(row.detail)).put("height",height);
                saved.put(value);
            }
            String raw=new JSONObject().put("version",1).put("language",language).put("rows",saved).toString();
            if(raw.getBytes("UTF-8").length>CACHE_BYTES){cache.edit().remove("snapshot").apply();return;}
            if(!raw.equals(cache.getString("snapshot","")))cache.edit().putString("snapshot",raw).apply();
        }catch(Exception invalid){cache.edit().remove("snapshot").apply();}
    }
    private static String bounded(String text){return text.substring(0,Math.min(text.length(),TodayContract.MAX_TEXT));}
    static List<Module> discover(Context c){
        List<Module> result=new ArrayList<>();PackageManager pm=c.getPackageManager();
        List<ProviderInfo> providers=new ArrayList<>();
        try{if(Build.VERSION.SDK_INT>=19){for(ResolveInfo info:pm.queryIntentContentProviders(new Intent(TodayContract.DISCOVER),PackageManager.GET_META_DATA))if(info.providerInfo!=null)providers.add(info.providerInfo);}else{List<ProviderInfo> legacy=pm.queryContentProviders(null,0,PackageManager.GET_META_DATA);if(legacy!=null)providers.addAll(legacy);}}catch(RuntimeException unavailable){return result;}
        for(ProviderInfo p:providers){if(p==null||!p.enabled||!p.exported||p.authority==null||p.authority.contains(";")||p.metaData==null||p.metaData.getInt(TodayContract.VERSION_META,0)!=TodayContract.VERSION||p.readPermission!=null)continue;
            result.add(new Module(p,pm));
        }
        result.sort((a,b)->a.label.compareToIgnoreCase(b.label));return result;
    }
    void refresh(){
        if(stopped)return;cancel();int ticket=++generation;signal=Build.VERSION.SDK_INT>=16?new ModernQuery(UiLanguage.language(context)):new Query(UiLanguage.language(context));final Query querySignal=signal;
        pending=io.submit(()->{
            List<Module> found=Collections.unmodifiableList(discover(context));
            List<Module> selected=new ArrayList<>();for(Module module:found)if(context.getSharedPreferences("today",Context.MODE_PRIVATE).getBoolean("module_"+module.authority,false))selected.add(module);
            main.post(()->{
                if(stopped||ticket!=generation||querySignal.isCanceled())return;
                if(providersChanged!=null)providersChanged.accept(found);
                context.getContentResolver().unregisterContentObserver(observer);observed.clear();
                for(Module module:selected){Uri uri=TodayContract.rows(module.authority);try{context.getContentResolver().registerContentObserver(uri,false,observer);observed.add(uri);}catch(RuntimeException removed){}}
                List<Row> retained=latest;
                pending=io.submit(()->load(selected,retained,ticket,querySignal));
            });
        });
    }
    private void load(List<Module> selected,List<Row> retained,int ticket,Query querySignal){
        Map<String,List<Row>> batches=new LinkedHashMap<>();
        for(Module module:selected){
            List<Row> batch=new ArrayList<>();
            for(Row row:retained)if(module.authority.equals(row.module.authority)&&module.pkg.equals(row.module.pkg)&&module.revision==row.module.revision)
                batch.add(new Row(module,row.id,row.title,row.detail,row.icon,row.activity,row.view,row.height));
            batches.put(module.authority,batch);
        }
        List<Row> rows=new ArrayList<>();
        for(List<Row> batch:batches.values())rows.addAll(batch);
        deliver(rows,ticket,querySignal);
        for(Module module:selected){
            List<Row> batch=new ArrayList<>();
            if(querySignal.isCanceled())return;
            Uri uri=TodayContract.rows(module.authority);
            try(Cursor cursor=querySignal.query(context.getContentResolver(),uri.buildUpon().appendQueryParameter("language",querySignal.language).build())){
                if(cursor!=null){int[] columns=new int[TodayContract.COLUMNS.length];for(int i=0;i<columns.length;i++)columns[i]=cursor.getColumnIndexOrThrow(TodayContract.COLUMNS[i]);Set<String> ids=new HashSet<>();
                for(int i=0;i<TodayContract.MAX_ROWS&&cursor.moveToNext();i++){
                    String id=text(cursor,columns[0]),title=text(cursor,columns[1]);if(id.isEmpty()||title.isEmpty()||!ids.add(id))continue;
                    Row row=new Row(module,id,title,text(cursor,columns[2]),cursor.getInt(columns[3]),text(cursor,columns[4]));
                    if(module.customViews)for(Row cached:batches.get(module.authority))if(id.equals(cached.id)){
                        row=new Row(module,id,title,row.detail,row.icon,row.activity,cached.view,cached.height);break;
                    }
                    batch.add(row);
                }}
            }catch(RuntimeException unavailable){/* A disabled/uninstalled/failing module cannot replace other modules. */}
            batches.put(module.authority,batch);rows.clear();
            for(List<Row> current:batches.values())rows.addAll(current);
            deliver(rows,ticket,querySignal);
        }
        deliver(rows,ticket,querySignal,true);
        main.post(()->{
            if(stopped||ticket!=generation||querySignal.isCanceled())return;
            for(Row row:rows)if(row.module.customViews){pendingWidget=widgets.submit(()->enrich(rows,ticket,querySignal));break;}
        });
    }
    private void enrich(List<Row> rows,int ticket,Query querySignal){
        // Publish every provider's public rows before optional widget calls can block.
        for(int i=0;i<rows.size();i++){
            if(querySignal.isCanceled())return;
            Row row=rows.get(i);if(!row.module.customViews)continue;
            rows.set(i,readWidget(row,querySignal.language));
            if(i+1==rows.size()||rows.get(i+1).module!=row.module)deliver(rows,ticket,querySignal);
        }
        deliver(rows,ticket,querySignal,true);
    }
    private void deliver(List<Row> rows,int ticket,Query querySignal){deliver(rows,ticket,querySignal,false);}
    private void deliver(List<Row> rows,int ticket,Query querySignal,boolean persist){
        if(querySignal.isCanceled())return;
        List<Row> snapshot=new ArrayList<>(rows.size());
        for(Row row:rows)snapshot.add(new Row(row.module,row.id,row.title,row.detail,row.icon,row.activity,row.view,row.height));
        // The worker only assigns RemoteViews references; it never mutates a published view.
        List<Row> ready=Collections.unmodifiableList(snapshot);
        main.post(()->{if(!stopped&&ticket==generation&&!querySignal.isCanceled()&&querySignal.language.equals(UiLanguage.language(context))){latest=ready;if(persist)persistRows(ready,querySignal.language);delivered.accept(ready);}});
    }
    private Row readWidget(Row row,String language){
        Bundle options=new Bundle();options.putInt("width",TodayContract.WIDTH);options.putInt("pixel_width",Math.max(480,Math.min(1440,context.getResources().getDisplayMetrics().widthPixels)));
        options.putString("language",language);options.putInt("accent",LauncherTheme.base(context,false));options.putInt("text_color",android.graphics.Color.WHITE);
        android.content.SharedPreferences preferences=context.getSharedPreferences("today",0);options.putString("font",LauncherFonts.selected(preferences).regular);options.putString("font_bold",LauncherFonts.selected(preferences).bold);options.putBoolean("hybrid",preferences.getBoolean("font_hybrid",true));
        Row fallback=new Row(row.module,row.id,row.title,row.detail,row.icon,row.activity);
        try{
            Bundle result=context.getContentResolver().call(TodayContract.rows(row.module.authority),TodayContract.VIEW_METHOD,row.id,options);
            if(result==null)return fallback;result.setClassLoader(android.widget.RemoteViews.class.getClassLoader());
            Object view=result.getParcelable(TodayContract.VIEW_KEY);int height=result.getInt(TodayContract.HEIGHT_KEY,0);
            if(view instanceof android.widget.RemoteViews&&row.module.pkg.equals(((android.widget.RemoteViews)view).getPackage())&&height>=TodayContract.MIN_HEIGHT&&height<=TodayContract.MAX_HEIGHT)return new Row(row.module,row.id,row.title,row.detail,row.icon,row.activity,(android.widget.RemoteViews)view,height);
        }catch(RuntimeException unavailable){/* Optional custom content falls back to the same public text row. */}
        return fallback;
    }
    private static class Query {
        final String language;Query(String language){this.language=language;}
        private volatile boolean canceled;
        void cancel(){canceled=true;}boolean isCanceled(){return canceled;}
        Cursor query(ContentResolver resolver,Uri uri){return resolver.query(uri,TodayContract.COLUMNS,null,null,null);}
    }
    private static final class ModernQuery extends Query {
        ModernQuery(String language){super(language);}
        private final CancellationSignal nativeSignal=new CancellationSignal();
        @Override void cancel(){super.cancel();nativeSignal.cancel();}
        @Override Cursor query(ContentResolver resolver,Uri uri){return resolver.query(uri,TodayContract.COLUMNS,null,null,null,nativeSignal);}
    }
    private static String text(Cursor c,int column){String s=c.getString(column);return s==null?"":s.substring(0,Math.min(s.length(),TodayContract.MAX_TEXT));}
    Intent action(Row row){
        if(row.activity.isEmpty())return null;ComponentName target=new ComponentName(row.module.pkg,row.activity.startsWith(".")?row.module.pkg+row.activity:row.activity);
        try{ActivityInfo info=context.getPackageManager().getActivityInfo(target,0);if(!info.enabled||!info.exported||info.permission!=null)return null;return new Intent(Intent.ACTION_VIEW).setComponent(target).putExtra("me.ancal.wmdroid.sdk.ROW",row.id).putExtra(me.ancal.wmdroid.sdk.WmApplet.LANGUAGE,UiLanguage.language(context)).putExtra(me.ancal.wmdroid.sdk.WmApplet.ACCENT,LauncherTheme.base(context,false)).putExtra(me.ancal.wmdroid.sdk.WmApplet.FONT,LauncherFonts.selected(context.getSharedPreferences("today",Context.MODE_PRIVATE)).regular).putExtra(me.ancal.wmdroid.sdk.WmApplet.HYBRID,context.getSharedPreferences("today",Context.MODE_PRIVATE).getBoolean("font_hybrid",true)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);}catch(PackageManager.NameNotFoundException removed){return null;}
    }
    private void cancel(){if(signal!=null)signal.cancel();if(pending!=null)pending.cancel(true);if(pendingWidget!=null)pendingWidget.cancel(true);widgets.purge();}
    void pause(){generation++;cancel();context.getContentResolver().unregisterContentObserver(observer);observed.clear();}
    void close(){stopped=true;pause();io.shutdownNow();widgets.shutdownNow();}
}
