package me.ancal.wmdroid;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import me.ancal.wmdroid.sdk.WmContactDetailsView;
import me.ancal.wmdroid.sdk.WmContactSubcardView;
import me.ancal.wmdroid.sdk.WmControls;
import me.ancal.wmdroid.sdk.WmDateField;
import me.ancal.wmdroid.sdk.WmStrings;

/** Local sparse editor. Native unsupported fields/pickers are omitted, rather than fabricated.
 * Every writable supported row in the selected raw contact retains its own binding.
 * The owner requests permissions, loads snapshots and saves the batch. */
final class ContactEditorPage extends FrameLayout implements WmControls.LanguageListener,WmControls.RestyleListener,WmControls.BackHandler,WmControls.ModalOwner {
    final WmContactDetailsView editor;
    private final ContactEditStore.Snapshot snapshot;
    private final long rawId;
    private boolean uncertainSave;
    private Bundle suspendedState;
    private final ArrayList<Draft> drafts=new ArrayList<>();
    private final ArrayList<Binding> bindings=new ArrayList<>();
    private final HashMap<String,Binding> byKey=new HashMap<>();
    private Draft notesDraft,nickname;
    private ContactEditStore.Row readOnlyNickname;
    private Binding activeCard;
    private FrameLayout savingOverlay;
    private HashMap<String,String> frozenFields;
    private String frozenNotes,language;
    private Typeface regular;
    private int selection;
    private static final class Draft {
        final ContactEditStore.Row source;
        final String key,mime;
        final ContentValues original,delta=new ContentValues();
        Draft(ContactEditStore.Row source,String key,String mime,Integer type){this.source=source;this.key=key;this.mime=mime;original=source==null?new ContentValues():source.values();if(source==null&&type!=null)original.put("data2",type);}
        String text(String column){Object value=delta.containsKey(column)?delta.get(column):original.get(column);return value==null?"":value.toString();}
        void change(String column,String value){String initial=original.getAsString(column);if(value.equals(initial==null?"":initial))delta.remove(column);else delta.put(column,value);}
    }
    private static final class Binding {
        final Draft draft;
        final String key,column,caption;
        final int rank,kind,input;
        Binding(Draft draft,String column,String caption,int rank,int kind,int input){this.draft=draft;this.column=column;this.caption=caption;this.rank=rank;this.kind=kind;this.input=input;key=draft.key+":"+column;}
    }
    ContactEditorPage(Context context,Typeface regular,ContactEditStore.Snapshot snapshot,long rawId){
        super(context);if(regular==null||snapshot==null)throw new IllegalArgumentException("Editor font and snapshot required");
        ContactEditStore.Account selected=null;for(ContactEditStore.Account account:snapshot.accounts())if(account.id==rawId)selected=account;
        if(selected==null||selected.readOnly)throw new IllegalArgumentException("Writable selected account required");
        this.snapshot=snapshot;this.rawId=rawId;this.regular=regular;language=UiLanguage.language(context);selection=LauncherTheme.selection(context,false);setFocusableInTouchMode(true);
        ContactEditStore.Row[] rows=snapshot.rows();
        boolean blockedReadonlyName=false;
        for(ContactEditStore.Row row:rows)if(row.rawId==rawId&&row.readOnly&&CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE.equals(row.mime))blockedReadonlyName=true;
        for(ContactEditStore.Row row:rows)if(row.rawId==rawId&&CommonDataKinds.Nickname.CONTENT_ITEM_TYPE.equals(row.mime)&&row.readOnly&&readOnlyNickname==null)readOnlyNickname=row;
        for(ContactEditStore.Row row:rows){if(row.rawId!=rawId||row.readOnly||!supported(row.mime))continue;Draft draft=new Draft(row,"row:"+row.id,row.mime,null);drafts.add(draft);addDraft(draft);}
        if(!blockedReadonlyName)missing(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,null,"name");missing(CommonDataKinds.Organization.CONTENT_ITEM_TYPE,null,"organization");
        missing(CommonDataKinds.Phone.CONTENT_ITEM_TYPE,CommonDataKinds.Phone.TYPE_WORK,"phone-work");missing(CommonDataKinds.Phone.CONTENT_ITEM_TYPE,CommonDataKinds.Phone.TYPE_MOBILE,"phone-mobile");missing(CommonDataKinds.Phone.CONTENT_ITEM_TYPE,CommonDataKinds.Phone.TYPE_HOME,"phone-home");
        missing(CommonDataKinds.Email.CONTENT_ITEM_TYPE,null,"email");missing(CommonDataKinds.Website.CONTENT_ITEM_TYPE,null,"website");
        missing(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,CommonDataKinds.StructuredPostal.TYPE_WORK,"postal-work");missing(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,CommonDataKinds.StructuredPostal.TYPE_HOME,"postal-home");missing(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,CommonDataKinds.StructuredPostal.TYPE_OTHER,"postal-other");
        missingEvent(CommonDataKinds.Event.TYPE_BIRTHDAY,"birthday");missingEvent(CommonDataKinds.Event.TYPE_ANNIVERSARY,"anniversary");
        if(notesDraft==null){notesDraft=new Draft(null,"missing:note",CommonDataKinds.Note.CONTENT_ITEM_TYPE,null);drafts.add(notesDraft);}
        if(nickname==null&&readOnlyNickname==null){nickname=new Draft(null,"missing:nickname",CommonDataKinds.Nickname.CONTENT_ITEM_TYPE,CommonDataKinds.Nickname.TYPE_DEFAULT);drafts.add(nickname);}
        Collections.sort(bindings,(a,b)->a.rank-b.rank);
        editor=new WmContactDetailsView(context,regular,fields(),notesDraft.text("data1"),selection,new WmContactDetailsView.Listener(){
            @Override public void open(WmContactDetailsView.Field field){Binding binding=byKey.get(field.key);if(binding!=null&&binding.kind==WmContactDetailsView.SUBCARD)openCard(binding);}
            @Override public void changed(WmContactDetailsView.Field field){restoreFrozenField(field);}
            @Override public void notesChanged(String value){restoreFrozenNotes();}
        });addView(editor,new LayoutParams(-1,-1));editor.onWmLanguageChanged(language);
    }
    WmContactDetailsView editor(){return editor;}
    // A retained owner window may detach while another applet is in front.
    void suspend(){if(suspendedState==null)suspendedState=saveState();}
    void resume(){if(suspendedState!=null){Bundle state=suspendedState;restoreState(state);suspendedState=null;}}
    /** Saving never applies an open card: its pending components remain separate from the local draft. */
    Bundle saveState(){
        if(suspendedState!=null)return new Bundle(suspendedState);
        sync();Bundle state=new Bundle();state.putInt("schema",1);state.putLong("raw_id",rawId);state.putBundle("snapshot",ContactEditStore.saveSnapshot(snapshot));
        ArrayList<Bundle> changes=new ArrayList<>();for(Draft draft:drafts){Bundle value=new Bundle();value.putString("key",draft.key);value.putParcelable("delta",new ContentValues(draft.delta));changes.add(value);}state.putParcelableArrayList("drafts",changes);
        state.putBundle("interaction",editor.saveInteractionState());state.putBoolean("saving",isSaving()||uncertainSave);
        WmContactSubcardView card=editor.subcard();state.putString("card",card==null||activeCard==null?"":activeCard.key);state.putStringArray("card_values",card==null?new String[0]:card.values());
        int focus=-1,start=0,end=0;if(card!=null)for(int i=0;i<card.values().length;i++){EditText input=card.editor(i);if(input.hasFocus()){focus=i;start=Math.max(0,input.getSelectionStart());end=Math.max(0,input.getSelectionEnd());break;}}
        state.putInt("card_focus",focus);state.putInt("card_start",start);state.putInt("card_end",end);return state;
    }
    void restoreState(Bundle state){
        if(state==null||!(state.get("schema") instanceof Integer)||state.getInt("schema")!=1||!(state.get("raw_id") instanceof Long)||state.getLong("raw_id")!=rawId||!(state.get("saving") instanceof Boolean))throw new IllegalArgumentException("Contact draft schema1 required");
        Object entries=state.get("drafts");if(!(entries instanceof ArrayList)||((ArrayList<?>)entries).size()!=drafts.size())throw new IllegalArgumentException("Complete contact drafts required");
        HashMap<String,ContentValues> deltas=new HashMap<>();for(Object item:(ArrayList<?>)entries){if(!(item instanceof Bundle))throw new IllegalArgumentException("Contact draft required");Bundle value=(Bundle)item;Object key=value.get("key"),delta=value.get("delta");if(!(key instanceof String)||!(delta instanceof ContentValues)||deltas.put((String)key,new ContentValues((ContentValues)delta))!=null)throw new IllegalArgumentException("Unique contact draft required");}
        for(Draft draft:drafts){ContentValues delta=deltas.get(draft.key);if(delta==null)throw new IllegalArgumentException("Missing contact draft");for(String column:delta.keySet())if(!(delta.get(column) instanceof String)||!editableColumn(draft,column))throw new IllegalArgumentException("Invalid contact draft column");}
        Object cardKey=state.get("card"),cardValues=state.get("card_values");if(!(cardKey instanceof String)||!(cardValues instanceof String[]))throw new IllegalArgumentException("Contact subcard state required");
        Binding binding=((String)cardKey).isEmpty()?null:byKey.get(cardKey);String[] values=(String[])cardValues;
        if(binding==null&&!((String)cardKey).isEmpty()||binding!=null&&binding.kind!=WmContactDetailsView.SUBCARD||values.length!=(binding==null?0:CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE.equals(binding.draft.mime)?6:5))throw new IllegalArgumentException("Invalid contact subcard binding");
        for(String value:values)if(value==null)throw new IllegalArgumentException("Literal subcard components required");
        int focus=stateInt(state,"card_focus"),start=stateInt(state,"card_start"),end=stateInt(state,"card_end");if(focus< -1||focus>=values.length||start<0||end<0||focus==-1&&(start!=0||end!=0))throw new IllegalArgumentException("Invalid subcard focus");
        // Validate prospective values before changing drafts, focus or popup state.
        WmContactDetailsView.Field[] candidates=fields(deltas);Bundle interaction=state.getBundle("interaction");
        WmContactDetailsView.validateInteractionState(interaction,candidates);
        if(binding!=null&&!interaction.getString("date_popup","").isEmpty())throw new IllegalArgumentException("Mutually exclusive contact popups required");
        for(Draft draft:drafts){draft.delta.clear();draft.delta.putAll(deltas.get(draft.key));}editor.setFields(candidates);editor.setNotes(notesDraft.text("data1"));
        if(binding!=null){openCard(binding);editor.subcard().setValues(values);if(focus>=0){EditText input=editor.subcard().editor(focus);input.requestFocus();input.setSelection(Math.min(start,input.length()),Math.min(end,input.length()));}}
        editor.restoreInteractionState(state.getBundle("interaction"));uncertainSave=state.getBoolean("saving");
    }
    private boolean editableColumn(Draft draft,String column){for(Binding binding:bindings)if(binding.draft==draft&&binding.column.equals(column))return true;if(draft==notesDraft||draft==nickname)return "data1".equals(column);if(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE.equals(draft.mime))return "data2".equals(column)||"data3".equals(column)||"data4".equals(column)||"data5".equals(column)||"data6".equals(column);if(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE.equals(draft.mime))return "data4".equals(column)||"data7".equals(column)||"data8".equals(column)||"data9".equals(column)||"data10".equals(column);return false;}
    private static int stateInt(Bundle state,String key){Object value=state.get(key);if(!(value instanceof Integer))throw new IllegalArgumentException("Integer contact state required");return (Integer)value;}
    boolean saveOutcomeUnknown(){return uncertainSave;}
    private static boolean supported(String mime){return CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE.equals(mime)||CommonDataKinds.Organization.CONTENT_ITEM_TYPE.equals(mime)||CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mime)||CommonDataKinds.Email.CONTENT_ITEM_TYPE.equals(mime)||CommonDataKinds.Website.CONTENT_ITEM_TYPE.equals(mime)||CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE.equals(mime)||CommonDataKinds.Note.CONTENT_ITEM_TYPE.equals(mime)||CommonDataKinds.Nickname.CONTENT_ITEM_TYPE.equals(mime)||CommonDataKinds.Event.CONTENT_ITEM_TYPE.equals(mime);}
    private int type(Draft draft){Integer type=draft.original.getAsInteger("data2");return type==null?0:type;}
    private void bind(Draft draft,String column,String caption,int rank,int kind,int input){Binding binding=new Binding(draft,column,caption,rank,kind,input);bindings.add(binding);byKey.put(binding.key,binding);}
    private void text(Draft draft,String column,String caption,int rank){bind(draft,column,caption,rank,WmContactDetailsView.TEXT,InputType.TYPE_CLASS_TEXT);}
    private void addDraft(Draft draft){
        if(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE.equals(draft.mime)){bind(draft,"data1","이름",0,WmContactDetailsView.SUBCARD,InputType.TYPE_CLASS_TEXT);return;}
        if(CommonDataKinds.Organization.CONTENT_ITEM_TYPE.equals(draft.mime)){text(draft,"data1","회사",1);text(draft,"data5","부서",2);text(draft,"data4","직함",3);return;}
        if(CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(draft.mime)){int type=type(draft),rank=30;String caption="전화";
            switch(type){case CommonDataKinds.Phone.TYPE_WORK:caption="회사 전화";rank=6;break;case CommonDataKinds.Phone.TYPE_FAX_WORK:caption="회사 팩스";rank=7;break;case CommonDataKinds.Phone.TYPE_MOBILE:caption="휴대폰";rank=11;break;case CommonDataKinds.Phone.TYPE_HOME:caption="집 전화";rank=15;break;case CommonDataKinds.Phone.TYPE_PAGER:caption="호출기";rank=19;break;case CommonDataKinds.Phone.TYPE_CAR:caption="카폰";rank=20;break;case CommonDataKinds.Phone.TYPE_FAX_HOME:caption="집 팩스";rank=21;break;case CommonDataKinds.Phone.TYPE_COMPANY_MAIN:caption="회사 전화";rank=22;break;case CommonDataKinds.Phone.TYPE_RADIO:caption="무선 전화";rank=25;break;}
            if(type==CommonDataKinds.Phone.TYPE_CUSTOM&&!draft.text("data3").isEmpty())caption=draft.text("data3");bind(draft,"data1",caption,rank,WmContactDetailsView.TEXT,InputType.TYPE_CLASS_PHONE);return;}
        if(CommonDataKinds.Email.CONTENT_ITEM_TYPE.equals(draft.mime)){bind(draft,"data1","전자 메일",10,WmContactDetailsView.TEXT,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);return;}
        if(CommonDataKinds.Website.CONTENT_ITEM_TYPE.equals(draft.mime)){bind(draft,"data1","웹 페이지",13,WmContactDetailsView.TEXT,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);return;}
        if(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE.equals(draft.mime)){int type=type(draft);String caption=type==CommonDataKinds.StructuredPostal.TYPE_WORK?"회사 주소":type==CommonDataKinds.StructuredPostal.TYPE_HOME?"집 주소":"기타 주소";int rank=type==CommonDataKinds.StructuredPostal.TYPE_WORK?8:type==CommonDataKinds.StructuredPostal.TYPE_HOME?16:18;if(type==CommonDataKinds.StructuredPostal.TYPE_CUSTOM&&!draft.text("data3").isEmpty())caption=draft.text("data3");bind(draft,"data1",caption,rank,WmContactDetailsView.SUBCARD,InputType.TYPE_CLASS_TEXT);return;}
        if(CommonDataKinds.Event.CONTENT_ITEM_TYPE.equals(draft.mime)){int type=type(draft);String caption=type==CommonDataKinds.Event.TYPE_BIRTHDAY?"생일":type==CommonDataKinds.Event.TYPE_ANNIVERSARY?"기념일":"기타 일정";if(type==CommonDataKinds.Event.TYPE_CUSTOM&&!draft.text("data3").isEmpty())caption=draft.text("data3");int kind=WmContactDetailsView.DATE;try{WmDateField.parseValue(draft.text("data1"));}catch(IllegalArgumentException literal){kind=WmContactDetailsView.TEXT;}bind(draft,"data1",caption,type==CommonDataKinds.Event.TYPE_BIRTHDAY?36:37,kind,InputType.TYPE_CLASS_TEXT);return;}
        if(CommonDataKinds.Note.CONTENT_ITEM_TYPE.equals(draft.mime)){if(notesDraft==null)notesDraft=draft;else text(draft,"data1","메모",40);return;}
        if(CommonDataKinds.Nickname.CONTENT_ITEM_TYPE.equals(draft.mime)){if(nickname==null)nickname=draft;text(draft,"data1","애칭",35);}
    }
    private void missingEvent(int type,String slot){for(ContactEditStore.Row row:snapshot.rows())if(row.rawId==rawId&&row.readOnly&&CommonDataKinds.Event.CONTENT_ITEM_TYPE.equals(row.mime)){Integer existing=row.values().getAsInteger("data2");if(existing!=null&&existing==type)return;}missing(CommonDataKinds.Event.CONTENT_ITEM_TYPE,type,slot);}
    private void missing(String mime,Integer requestedType,String slot){for(Draft draft:drafts)if(draft.mime.equals(mime)&&(requestedType==null||type(draft)==requestedType))return;Integer type=requestedType;if(type==null){if(CommonDataKinds.Organization.CONTENT_ITEM_TYPE.equals(mime))type=CommonDataKinds.Organization.TYPE_WORK;else if(CommonDataKinds.Email.CONTENT_ITEM_TYPE.equals(mime))type=CommonDataKinds.Email.TYPE_OTHER;else if(CommonDataKinds.Website.CONTENT_ITEM_TYPE.equals(mime))type=CommonDataKinds.Website.TYPE_OTHER;}Draft draft=new Draft(null,"missing:"+slot,mime,type);drafts.add(draft);addDraft(draft);}
    private String caption(Binding binding){if((CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(binding.draft.mime)||CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE.equals(binding.draft.mime)||CommonDataKinds.Event.CONTENT_ITEM_TYPE.equals(binding.draft.mime))&&type(binding.draft)==0&&binding.caption.equals(binding.draft.text("data3")))return binding.caption+":";String key=CommonDataKinds.Organization.CONTENT_ITEM_TYPE.equals(binding.draft.mime)&&"data4".equals(binding.column)?"contacts.details.job_title":CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(binding.draft.mime)&&type(binding.draft)==CommonDataKinds.Phone.TYPE_COMPANY_MAIN?"contacts.details.company_tel":null;return (key==null?WmStrings.text(getContext(),language,binding.caption):WmStrings.text(getContext(),language,binding.caption,key))+":";}
    private WmContactDetailsView.Field[] fields(){return fields(null);}
    private WmContactDetailsView.Field[] fields(HashMap<String,ContentValues> prospective){WmContactDetailsView.Field[] fields=new WmContactDetailsView.Field[bindings.size()];for(int i=0;i<fields.length;i++){Binding binding=bindings.get(i);String value=binding.draft.text(binding.column);if(prospective!=null){ContentValues delta=prospective.get(binding.draft.key);Object raw=delta.containsKey(binding.column)?delta.get(binding.column):binding.draft.original.get(binding.column);value=raw==null?"":raw.toString();}fields[i]=new WmContactDetailsView.Field(binding.key,caption(binding),value,binding.kind,binding.input);}return fields;}
    private void sync(){for(Binding binding:bindings)binding.draft.change(binding.column,editor.field(binding.key).value);notesDraft.change("data1",editor.notes());}
    /** Top-level OK applies a pending component card once, then completes inline editing. */
    void prepare(){editor.closeDatePopup();editor.closeSubcard(true);editor.clearFocus();sync();}
    ContactEditStore.Update[] updates(){sync();ArrayList<ContactEditStore.Update> result=new ArrayList<>();for(Draft draft:drafts)if(draft.source!=null&&draft.delta.size()>0)result.add(new ContactEditStore.Update(draft.source.id,draft.delta));return result.toArray(new ContactEditStore.Update[0]);}
    ContactEditStore.Insert[] inserts(){sync();ArrayList<ContactEditStore.Insert> result=new ArrayList<>();for(Draft draft:drafts)if(draft.source==null&&draft.delta.size()>0){ContentValues values=new ContentValues(draft.original);values.putAll(draft.delta);boolean entered=false;for(String column:draft.delta.keySet())if(!draft.text(column).isEmpty())entered=true;if(entered)result.add(new ContactEditStore.Insert(draft.mime,values));}return result.toArray(new ContactEditStore.Insert[0]);}
    private String[] cardLabels(boolean name){String[] captions=name?new String[]{"직함","이름","중간 이름","성","애칭","호칭(한글)"}:new String[]{"읍/면/동","구/군/시","시/도","우편번호","국가/지역"};for(int i=0;i<captions.length;i++)captions[i]=(name&&i==1?WmStrings.text(getContext(),language,captions[i],"contacts.name.first"):WmStrings.text(getContext(),language,captions[i]))+":";return captions;}
    private void openCard(Binding binding){
        sync();boolean name=CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE.equals(binding.draft.mime);String[] columns=name?new String[]{"data4","data2","data5","data3",null,"data6"}:new String[]{"data4","data7","data8","data9","data10"};String[] baseline=new String[columns.length];for(int i=0;i<columns.length;i++)baseline[i]=columns[i]==null?nickname!=null?nickname.text("data1"):readOnlyNickname==null?"":normal(readOnlyNickname.values().getAsString("data1")):binding.draft.text(columns[i]);
        boolean emptyMain=true;for(int i=0;i<baseline.length;i++)if(columns[i]!=null&&!baseline[i].isEmpty())emptyMain=false;
        // Literal-only Android rows have no proved component parser. Seed one editable
        // component for display; applying an unchanged card writes nothing.
        if(emptyMain&&!binding.draft.text("data1").isEmpty())baseline[name?1:0]=binding.draft.text("data1");
        WmContactSubcardView card=new WmContactSubcardView(getContext(),regular,name?WmContactSubcardView.NAME:WmContactSubcardView.ADDRESS,cardLabels(name),baseline,selection);if(name&&nickname==null)card.editor(4).setEnabled(false);
        editor.showSubcard(card,()->{String[] values=card.values();boolean mainChanged=false,nicknameChanged=false;for(int i=0;i<values.length;i++)if(!values[i].equals(baseline[i])){if(columns[i]!=null){binding.draft.change(columns[i],values[i]);mainChanged=true;}else if(nickname!=null){nickname.change("data1",values[i]);nicknameChanged=true;if(nickname.source!=null)editor.setFieldValue(nickname.key+":data1",values[i]);}}
            boolean blank=true;for(int i=0;i<values.length;i++)if(columns[i]!=null&&!values[i].isEmpty())blank=false;
            if(mainChanged||name&&blank&&nicknameChanged){String preview=name?namePreview(values):addressPreview(values);binding.draft.change("data1",preview);editor.setFieldValue(binding.key,preview);}activeCard=null;},()->activeCard=null);activeCard=binding;
    }
    private static String normal(String value){return value==null?"":value;}
    // Explicit Android owner adaptation; unchanged components retain original formatted DATA1.
    private static String namePreview(String[] values){String result=join(" ",values[0],values[1],values[2],values[3],values[5]);return result.isEmpty()?values[4]:result;}
    private static String addressPreview(String[] values){return join("\n",values[0],join(", ",values[1],join(" ",values[2],values[3])),values[4]);}
    private static String join(String separator,String... values){StringBuilder text=new StringBuilder();for(String value:values)if(!value.isEmpty()){if(text.length()>0)text.append(separator);text.append(value);}return text.toString();}
    private void restoreFrozenField(WmContactDetailsView.Field field){if(savingOverlay!=null&&frozenFields!=null&&frozenFields.containsKey(field.key))editor.setFieldValue(field.key,frozenFields.get(field.key));}
    private void restoreFrozenNotes(){if(savingOverlay!=null)editor.setNotes(frozenNotes);}
    void setSaving(boolean saving){if(saving==(savingOverlay!=null))return;if(saving){editor.closeDatePopup();sync();frozenFields=new HashMap<>();for(WmContactDetailsView.Field field:editor.fields())frozenFields.put(field.key,field.value);frozenNotes=editor.notes();savingOverlay=new FrameLayout(getContext());savingOverlay.setClickable(true);savingOverlay.setFocusableInTouchMode(true);addView(savingOverlay,new LayoutParams(-1,-1));savingOverlay.requestFocus();}else{removeView(savingOverlay);savingOverlay=null;frozenFields=null;frozenNotes=null;}}
    boolean isSaving(){return savingOverlay!=null;}
    @Override public boolean blocksOwnerCommands(){return savingOverlay!=null;}
    @Override public boolean back(){return savingOverlay!=null||editor.back();}
    @Override public void onWmLanguageChanged(String language){this.language=language;WmContactDetailsView.Field[] fields=editor.fields();for(int i=0;i<fields.length;i++){WmContactDetailsView.Field old=fields[i];fields[i]=new WmContactDetailsView.Field(old.key,caption(byKey.get(old.key)),old.value,old.kind,old.inputType);}editor.setFields(fields);editor.onWmLanguageChanged(language);if(activeCard!=null&&editor.subcard()!=null)editor.subcard().setLabels(cardLabels(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE.equals(activeCard.draft.mime)));}
    @Override public void onWmRestyle(Typeface regular,Typeface bold,int selection,int tabColor){if(regular==null)throw new IllegalArgumentException("Editor font required");this.regular=regular;this.selection=selection;editor.onWmRestyle(regular,bold,selection,tabColor);}
}
