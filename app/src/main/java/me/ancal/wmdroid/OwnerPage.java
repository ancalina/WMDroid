package me.ancal.wmdroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import me.ancal.wmdroid.sdk.WmControls;
import me.ancal.wmdroid.sdk.WmPropertySheet;
import me.ancal.wmdroid.sdk.WmStrings;

/** CPL Owner pages 14363/14364/14365, callbacks 12/13/170.
 * Coordinates retain the template's 240-wide control surface at 2x VGA.
 * Original WinCE dialog-unit/font mapping still requires runtime comparison.
 */
final class OwnerPage extends LinearLayout implements WmControls.RestyleListener,WmControls.LanguageListener {
    public void onWmRestyle(Typeface regular,Typeface bold,int selection,int tabColor){this.font=regular;}
    private final SharedPreferences prefs;
    private final Runnable changed;
    private Typeface font;
    private final EditText name, company, address, telephone, email, notes;
    private final CheckBox showIdentity, showNotes;
    private final WmPropertySheet sheet;
    private final View[] pages = new View[3];

    OwnerPage(Context context, SharedPreferences prefs, Typeface font, Runnable changed) {
        super(context); this.prefs=prefs; this.font=font; this.changed=changed;
        setOrientation(VERTICAL);setBackgroundColor(ClassicUi.SURFACE);setFocusableInTouchMode(true);
        sheet=new WmPropertySheet(context,font,LauncherTheme.color(context,0xffc9e7e9,false));
        FrameLayout identity=new FrameLayout(context);
        name=field("owner",35,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS,false);
        company=field("owner_company",35,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS,false);
        address=field("owner_address",128,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,true);
        telephone=field("owner_phone",24,InputType.TYPE_CLASS_PHONE,false);
        email=field("owner_email",35,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,false);
        label(identity,"이름:",8,6,60,18);place(identity,name,72,2,159,20);
        label(identity,"회사:",8,27,60,18);place(identity,company,72,23,159,20);
        label(identity,"주소:",8,48,60,18);place(identity,address,72,44,159,54);
        label(identity,"전화 번호:",8,103,60,18);place(identity,telephone,72,99,159,20);
        label(identity,"전자 메일:",8,124,60,18);place(identity,email,72,120,159,20);
        pages[0]=scroll(identity,290);
        FrameLayout notePage=new FrameLayout(context);label(notePage,"메모:",8,6,60,16);
        notes=field("owner_notes",192,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,true);
        place(notePage,notes,8,25,224,120);pages[1]=scroll(notePage,306);
        FrameLayout options=new FrameLayout(context);label(options,"장치를 켤 때 표시:",8,6,224,18);
        showIdentity=check("신원 정보");showNotes=check("메모");
        place(options,showIdentity,16,25,216,18);place(options,showNotes,16,50,216,18);
        pages[2]=scroll(options,145);
        String[] titles={"신원 정보","메모","옵션"};
        for(int i=0;i<3;i++)sheet.addPage(14363+i,UiLanguage.t(context,titles[i]),pages[i]);
        sheet.setPageNotificationListener((index,notification)->{
            if(notification==WmPropertySheet.PSN_KILLACTIVE)save();return WmPropertySheet.PSNRET_NOERROR;
        });
        sheet.setOnPageChangedListener(index->{if(index==2)updateOptions();replayPresentation();});
        addView(sheet,new LayoutParams(-1,-1));updateOptions();requestFocus();
        android.text.TextWatcher watch=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(android.text.Editable value){save();}};
        for(EditText field:new EditText[]{name,company,address,telephone,email,notes})field.addTextChangedListener(watch);
        showIdentity.setOnCheckedChangeListener((v,on)->save());showNotes.setOnCheckedChangeListener((v,on)->save());
    }
    @Override public void onWmLanguageChanged(String language){
        String[] titles={"신원 정보","메모","옵션"};
        for(int i=0;i<titles.length;i++)sheet.setPageTitle(i,WmStrings.text(getContext(),language,titles[i]));
    }
    private TextView caption(String text){TextView view=ClassicUi.text(getContext(),font,"");WmControls.bindText(view,UiLanguage.language(getContext()),text);return view;}
    private void label(FrameLayout panel,String text,int x,int y,int w,int h){TextView v=caption(text);v.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);if(text.equals("메모:")||text.equals("장치를 켤 때 표시:"))v.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);place(panel,v,x,y,w,h);}
    private EditText field(String key,int limit,int type,boolean multiline){
        EditText v=new EditText(getContext());v.setInputType(type);v.setSingleLine(!multiline);ClassicUi.input(v,font);
        v.setGravity(multiline?Gravity.TOP|Gravity.LEFT:Gravity.CENTER_VERTICAL);
        v.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});v.setText(prefs.getString(key,""));v.setSelectAllOnFocus(false);
        return v;
    }
    private CheckBox check(String text){CheckBox v=new CheckBox(getContext());ClassicUi.check(v,font);WmControls.bindText(v,UiLanguage.language(getContext()),text);return v;}
    private static void place(FrameLayout parent,View child,int x,int y,int w,int h){boolean fit=child instanceof EditText||(child instanceof CheckBox&&w>=216);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(fit?LayoutParams.MATCH_PARENT:w*2,h*2);p.leftMargin=x*2;p.topMargin=y*2;if(fit)p.rightMargin=ClassicUi.VGA_WIDTH-(x+w)*2;parent.addView(child,p);}
    private ClassicScrollView scroll(FrameLayout contents,int height){return new ClassicScrollView(getContext(),contents,height);}
    private void replayPresentation(){
        for(ViewParent parent=getParent();parent!=null;parent=parent.getParent())
            if(parent instanceof RetroSurface){((RetroSurface)parent).replay(sheet.getClientView());return;}
    }
    private boolean identityPresent(){return name.length()>0||company.length()>0||address.length()>0||telephone.length()>0||email.length()>0;}
    private void updateOptions(){boolean identity=identityPresent(),note=notes.length()>0;showIdentity.setEnabled(identity);showIdentity.setChecked(identity&&prefs.getBoolean("owner_show_identity",false));showNotes.setEnabled(note);showNotes.setChecked(note&&prefs.getBoolean("owner_show_notes",false));}
    void save(){
        // Native pages commit on deactivate and OK. Preserve leading/trailing input.
        SharedPreferences.Editor edit=prefs.edit().putString("owner",name.getText().toString()).putString("owner_company",company.getText().toString()).putString("owner_address",address.getText().toString()).putString("owner_phone",telephone.getText().toString()).putString("owner_email",email.getText().toString()).putString("owner_notes",notes.getText().toString());
        if(sheet.selectedPage()==2){edit.putBoolean("owner_show_identity",identityPresent()&&showIdentity.isChecked()).putBoolean("owner_show_notes",notes.length()>0&&showNotes.isChecked());}
        if(!identityPresent())edit.putBoolean("owner_show_identity",false);if(notes.length()==0)edit.putBoolean("owner_show_notes",false);
        edit.apply();changed.run();
    }
}
