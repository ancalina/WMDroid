package me.ancal.wmdroid;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import me.ancal.wmdroid.sdk.WmIconPolicy;
import me.ancal.wmdroid.sdk.WmControls;

/** Multi-select application list. Includes hidden apps so every choice can be undone. */
final class AppSelectionPage extends LinearLayout implements WmControls.RestyleListener {
    @Override public void onWmRestyle(Typeface regular,Typeface bold,int selection,int tabColor){this.font=regular;}
    private Consumer<String> picked;
    void singleSelection(Consumer<String> picked){this.picked=picked;render();}
    private Typeface font;private final ExecutorService worker;private final Consumer<List<String>> changed;
    private final LinkedHashSet<String> selected=new LinkedHashSet<>();private final List<App> apps=new ArrayList<>();
    private final LinearLayout rows;private final EditText search;private final Handler handler=new Handler(Looper.getMainLooper());
    private Future<?> pending;private int generation;private boolean attached,loaded;
    AppSelectionPage(Context c,Typeface font,ExecutorService worker,List<String> initial,Consumer<List<String>> changed){
        super(c);this.font=font;this.worker=worker;this.changed=changed;for(String id:initial)selected.add(WmIconPolicy.canonical(id));setOrientation(VERTICAL);setBackgroundColor(0xffffffff);
        search=new EditText(c);ClassicUi.input(search,font);search.setSingleLine(true);WmControls.bindHint(search,UiLanguage.language(c),"프로그램 검색");addView(search,new LayoutParams(-1,44));
        rows=new LinearLayout(c);rows.setOrientation(VERTICAL);addView(new ClassicScrollView(c,rows),new LayoutParams(-1,0,1));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int n){}public void afterTextChanged(Editable s){}public void onTextChanged(CharSequence s,int a,int b,int n){render();}});render();
    }
    List<String> selectedIds(){return new ArrayList<>(selected);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;if(loaded){render();return;}final int ticket=++generation;
        try{pending=worker.submit(()->{List<App> found=new ArrayList<>();boolean failed=false;
            try{for(ResolveInfo info:getContext().getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)){
                if(Thread.currentThread().isInterrupted())return;if(!info.activityInfo.exported||!info.activityInfo.enabled)continue;
                String id=new ComponentName(info.activityInfo.packageName,info.activityInfo.name).flattenToString();if("me.ancal.wmdroid".equals(info.activityInfo.packageName))continue;
                found.add(new App(id,info.loadLabel(getContext().getPackageManager()).toString()));}
                Collator order=Collator.getInstance(UiLanguage.locale(getContext()));found.sort((a,b)->order.compare(a.label,b.label));
            }catch(RuntimeException unavailable){failed=true;}
            final boolean error=failed;handler.post(()->{if(!attached||ticket!=generation)return;pending=null;loaded=true;apps.clear();apps.addAll(found);render();if(error)notice("프로그램 목록을 읽지 못했습니다.");});
        });}catch(java.util.concurrent.RejectedExecutionException closed){notice("프로그램 목록을 읽지 못했습니다.");}
    }
    @Override protected void onDetachedFromWindow(){attached=false;generation++;if(pending!=null)pending.cancel(true);pending=null;super.onDetachedFromWindow();}
    private void notice(String message){rows.removeAllViews();TextView t=ClassicUi.text(getContext(),font,UiLanguage.t(getContext(),message));WmControls.bindText(t,UiLanguage.language(getContext()),message);t.setPadding(8,4,8,4);rows.addView(t,new LayoutParams(-1,48));}
    private void render(){rows.removeAllViews();if(!loaded){notice("프로그램을 불러오는 중…");return;}String query=search.getText().toString().toLowerCase(UiLanguage.locale(getContext()));
        for(App app:apps)if(app.label.toLowerCase(UiLanguage.locale(getContext())).contains(query)){
            if(picked!=null){TextView choice=ClassicUi.text(getContext(),font,app.label);ClassicUi.entry(choice,false);choice.setSingleLine(true);choice.setEllipsize(android.text.TextUtils.TruncateAt.END);choice.setPadding(16,0,16,0);choice.setTag(app.id);choice.setOnClickListener(v->picked.accept(app.id));rows.addView(choice,new LayoutParams(-1,48));continue;}
            CheckBox choice=new CheckBox(getContext());ClassicUi.check(choice,font);choice.setText(app.label);choice.setTag(app.id);choice.setSingleLine(true);choice.setChecked(selected.contains(app.id));
            choice.setOnCheckedChangeListener((v,on)->{if(on)selected.add(app.id);else selected.remove(app.id);if(changed!=null)changed.accept(selectedIds());});rows.addView(choice,new LayoutParams(-1,48));
        }
        if(rows.getChildCount()==0)notice("일치하는 프로그램이 없습니다.");
    }
    private static final class App {final String id,label;App(String id,String label){this.id=id;this.label=label;}}
}
