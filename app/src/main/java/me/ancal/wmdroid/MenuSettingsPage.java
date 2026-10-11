package me.ancal.wmdroid;

import android.content.*;
import android.content.pm.*;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.util.*;
import me.ancal.wmdroid.sdk.WmControls;
import me.ancal.wmdroid.sdk.WmListSettingsPane;
import me.ancal.wmdroid.sdk.WmStrings;

/** Native list/select/move relationships; extended folders have no seven-row cap.
 * New/Menu are commands in the window's command bar, never a second footer.
 */
final class MenuSettingsPage extends LinearLayout implements WmControls.RestyleListener,WmControls.LanguageListener,WmControls.BackHandler {
    public void onWmRestyle(Typeface regular,Typeface bold,int selection,int tabColor){this.font=regular;}
    public void onWmLanguageChanged(String language){
        updateHeading(language);
        for(int i=0;i<rows.getChildCount();i++){
            LinearLayout row=(LinearLayout)rows.getChildAt(i);String id=(String)row.getTag(),caption=label(getContext(),id,language);
            ((TextView)row.getChildAt(1)).setText(caption);row.getChildAt(0).setContentDescription(caption+WmStrings.text(getContext(),language," 표시"));
            if(row.getChildCount()>2)row.getChildAt(2).setContentDescription(WmStrings.text(getContext(),language,"열기")+": "+caption);
        }
    }

    private final SharedPreferences prefs;private Typeface font;private List<String> selected;
    private final LinearLayout rows;private final ClassicScrollView itemScroll;private final TextView heading,up,down,open,options;private String parent="",selection,renderedParent="";private final List<String> parents=new ArrayList<>();
    private boolean syncing,saving;private volatile boolean listening;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable sync=()->{if(listening)updated();};
    private final SharedPreferences.OnSharedPreferenceChangeListener changes=(store,key)->{
        if(!listening||saving||key!=null&&!"start_items".equals(key)&&!"start_folders".equals(key))return;
        if(Looper.myLooper()==main.getLooper())updated();else{main.removeCallbacks(sync);main.post(sync);}
    };
    interface Choose {void apps(java.util.function.Consumer<String> chosen);void options(MenuSettingsPage page,String id);}
    private final Choose chooser;
    MenuSettingsPage(Context c,SharedPreferences prefs,Typeface font,Choose chooser){super(c);this.prefs=prefs;this.font=font;this.chooser=chooser;selected=MenuPreferences.read(prefs);setOrientation(VERTICAL);setBackgroundColor(ClassicUi.SURFACE);
        heading=ClassicUi.text(c,font,"");heading.setPadding(16,0,16,0);heading.setSingleLine(true);heading.setEllipsize(android.text.TextUtils.TruncateAt.START);heading.setOnClickListener(v->back());addView(heading,new LayoutParams(-1,44));
        rows=new LinearLayout(c);rows.setOrientation(VERTICAL);
        up=button("위로 이동",()->move(-1));down=button("아래로 이동",()->move(1));open=button("열기",()->enter(selection));options=button("옵션...",()->{if(selection!=null)chooser.options(this,selection);});
        itemScroll=new ClassicScrollView(c,rows);WmListSettingsPane pane=new WmListSettingsPane(c,itemScroll,up,down,open,options);addView(pane,new LayoutParams(-1,0,1));render();
    }
    private TextView button(String value,Runnable action){TextView t=ClassicUi.text(getContext(),font,UiLanguage.t(getContext(),value));WmControls.bindText(t,UiLanguage.language(getContext()),value);ClassicUi.button(t,false);t.setOnClickListener(v->action.run());return t;}
    static String label(Context c,String id){return label(c,id,UiLanguage.language(c));}
    static String label(Context c,String id,String language){if(MenuPreferences.isFolder(id)){org.json.JSONObject folder=MenuPreferences.folder(c.getSharedPreferences("today",0),id);return folder==null?WmStrings.text(c,language,"폴더"):folder.optString("title",WmStrings.text(c,language,"폴더"));}
        switch(id){case "today":return WmStrings.text(c,language,"오늘");case "calendar":return WmStrings.text(c,language,"일정");case "contacts":return WmStrings.text(c,language,"연락처");case "notes":return WmStrings.text(c,language,"메모");case "messages":return WmStrings.text(c,language,"메시지");case "phone":return WmStrings.text(c,language,"전화");case "browser":return "Internet Explorer";case "media":return "Windows Media";}
        ComponentName app=ComponentName.unflattenFromString(id);if(app==null)return id;try{return c.getPackageManager().getActivityInfo(app,0).loadLabel(c.getPackageManager()).toString();}catch(PackageManager.NameNotFoundException removed){return WmStrings.text(c,language,"설치되지 않은 앱");}}
    void root(){parents.clear();parent="";selected=MenuPreferences.read(prefs);selection=null;render();}
    void enter(String id){updated();if(!MenuPreferences.isFolder(id)||!selected.contains(id)||id.equals(parent)||parents.contains(id)||MenuPreferences.folder(prefs,id)==null)return;parents.add(parent);parent=id;selected=MenuPreferences.read(prefs,id);selection=null;render();}
    @Override public boolean back(){if(parent.isEmpty())return false;do{parent=parents.isEmpty()?"":parents.remove(parents.size()-1);}while(!parent.isEmpty()&&MenuPreferences.folder(prefs,parent)==null);selected=MenuPreferences.read(prefs,parent);selection=null;render();return true;}
    String parentId(){return parent;}
    void moveTo(String id,String destination){updated();saving=true;boolean moved;try{moved=MenuPreferences.move(prefs,parent,id,destination);}finally{saving=false;}if(moved){selection=null;updated();}}
    void addApp(){chooser.apps(this::add);}
    void add(String id){updated();if(!selected.contains(id)){selected.add(id);selection=id;save();render();}}
    void addFolder(String title,String icon){updated();saving=true;String id;try{id=MenuPreferences.addFolder(prefs,parent,title,icon);}finally{saving=false;}selected=MenuPreferences.read(prefs,parent);selection=id;render();}
    void remove(String id){updated();selected.remove(id);selection=null;save();render();}
    void reset(){updated();selected.clear();if(parent.isEmpty())Collections.addAll(selected,MenuPreferences.DEFAULT);selection=null;save();render();}
    // Nested editors can change the menu while this page is detached.
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();listening=true;prefs.registerOnSharedPreferenceChangeListener(changes);updated();}
    @Override protected void onDetachedFromWindow(){listening=false;prefs.unregisterOnSharedPreferenceChangeListener(changes);main.removeCallbacks(sync);super.onDetachedFromWindow();}
    void updated(){main.removeCallbacks(sync);while(!parent.isEmpty()&&MenuPreferences.folder(prefs,parent)==null){parent=parents.isEmpty()?"":parents.remove(parents.size()-1);selection=null;}selected=MenuPreferences.read(prefs,parent);render();}
    private void move(int delta){updated();int index=selected.indexOf(selection),other=index+delta;if(index<0||other<0||other>=selected.size())return;Collections.swap(selected,index,other);save();render();}
    private void updateHeading(String language){String path=WmStrings.text(getContext(),language,"시작 메뉴 항목");if(!parent.isEmpty()){StringBuilder trail=new StringBuilder("< "+WmStrings.text(getContext(),language,"시작 메뉴"));for(String ancestor:parents)if(!ancestor.isEmpty())trail.append(" / ").append(label(getContext(),ancestor,language));path=trail.append(" / ").append(label(getContext(),parent,language)).toString();}heading.setText(path);heading.setContentDescription(path);}
    private void render(){if(!parent.isEmpty()&&MenuPreferences.folder(prefs,parent)==null){updated();return;}int position=itemScroll.getRequestedScrollPosition();updateHeading(UiLanguage.language(getContext()));
        List<String> available=new ArrayList<>(selected);if(parent.isEmpty())for(String id:MenuPreferences.DEFAULT)if(!available.contains(id))available.add(id);
        if(selection!=null&&!available.contains(selection))selection=null;
        Map<String,View> retained=new HashMap<>();boolean same=parent.equals(renderedParent)&&rows.getChildCount()==available.size();
        if(parent.equals(renderedParent))for(int i=0;i<rows.getChildCount();i++){View row=rows.getChildAt(i);retained.put((String)row.getTag(),row);if(i>=available.size()||!available.get(i).equals(row.getTag()))same=false;}
        if(!same)rows.removeAllViews();
        syncing=true;try{for(String id:available){LinearLayout row=(LinearLayout)retained.get(id);
            if(row==null){row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);ClassicUi.entryBackground(row,false);row.setTag(id);final LinearLayout owner=row;final String ownerParent=parent;
                CheckBox enabled=new CheckBox(getContext());WmControls.listCheck(enabled,font);enabled.setOnCheckedChangeListener((v,on)->{if(syncing||!listening||owner.getParent()!=rows||!ownerParent.equals(parent))return;if(on)add(id);else remove(id);});row.addView(enabled,new LayoutParams(32,40));
                TextView caption=ClassicUi.text(getContext(),font,"");caption.setSingleLine(true);caption.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.setDuplicateParentStateEnabled(true);caption.setTextColor(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_selected},{}},new int[]{0xffffffff,0xff000000}));row.addView(caption,new LayoutParams(0,40,1));
                if(MenuPreferences.isFolder(id)){TextView arrow=button(">",()->{if(listening&&owner.getParent()==rows&&ownerParent.equals(parent))enter(id);});arrow.setPadding(0,0,0,0);row.addView(arrow,new LayoutParams(40,40));}
                row.setOnClickListener(v->{if(!listening||owner.getParent()!=rows||!ownerParent.equals(parent))return;selection=id;updateSelection();});row.setOnLongClickListener(v->{if(!listening||owner.getParent()!=rows||!ownerParent.equals(parent))return false;if(MenuPreferences.isFolder(id))enter(id);else chooser.options(this,id);return true;});
            }
            String caption=label(getContext(),id);((CheckBox)row.getChildAt(0)).setChecked(selected.contains(id));row.getChildAt(0).setContentDescription(caption+UiLanguage.t(getContext()," 표시"));((TextView)row.getChildAt(1)).setText(caption);
            if(row.getChildCount()>2)row.getChildAt(2).setContentDescription(UiLanguage.t(getContext(),"열기")+": "+caption);
            if(!same)rows.addView(row,new LayoutParams(-1,40));
        }}finally{syncing=false;}
        renderedParent=parent;updateSelection();if(!same)itemScroll.scrollToPosition(position);
    }
    private void updateSelection(){
        for(int i=0;i<rows.getChildCount();i++){View row=rows.getChildAt(i);row.setSelected(row.getTag().equals(selection));}
        int index=selected.indexOf(selection);up.setEnabled(index>0);down.setEnabled(index>=0&&index<selected.size()-1);open.setEnabled(index>=0&&MenuPreferences.isFolder(selection)&&MenuPreferences.folder(prefs,selection)!=null);options.setEnabled(index>=0);
    }
    void save(){saving=true;try{MenuPreferences.save(prefs,parent,selected);}finally{saving=false;}}
}
