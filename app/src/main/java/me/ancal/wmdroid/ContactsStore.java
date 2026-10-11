package me.ancal.wmdroid;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import me.ancal.wmdroid.sdk.WmContactsView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import android.telephony.PhoneNumberUtils;

/** Read-only provider mapping; the list uses batched queries and Summary reads one aggregate. */
final class ContactsStore {
    static final class PhoneField {
        final String key,number,customLabel;final int type;
        PhoneField(String key,String number,int type,String customLabel){this.key=key;this.number=number;this.type=type;this.customLabel=customLabel;}
    }
    static final class LinkField {
        final String key,value,mime,customLabel;final int type;
        LinkField(String key,String value,String mime,int type,String customLabel){this.key=key;this.value=value;this.mime=mime;this.type=type;this.customLabel=customLabel;}
    }
    static final class AddressField {
        final String key,value,customLabel;final int type;
        AddressField(String key,String value,int type,String customLabel){this.key=key;this.value=value;this.type=type;this.customLabel=customLabel;}
    }
    static final class Details {
        final String name,notes,company,jobTitle;final PhoneField[] phones;final LinkField[] links;final AddressField[] addresses;
        Details(String name,PhoneField[] phones,LinkField[] links,AddressField[] addresses,String notes,String company,String jobTitle){this.name=name;this.phones=phones;this.links=links;this.addresses=addresses;this.notes=notes;this.company=company;this.jobTitle=jobTitle;}
    }
    static Details summary(ContentResolver resolver,String key){
        Uri contact=uri(key);
        try(Cursor person=resolver.query(contact,new String[]{ContactsContract.Contacts._ID,ContactsContract.Contacts.DISPLAY_NAME_PRIMARY},null,null,null);
            Cursor data=resolver.query(ContactsContract.Data.CONTENT_URI,new String[]{ContactsContract.Data._ID,ContactsContract.Data.MIMETYPE,ContactsContract.Data.DATA1,ContactsContract.Data.DATA2,ContactsContract.Data.DATA3,ContactsContract.Data.IS_SUPER_PRIMARY,ContactsContract.Data.IS_PRIMARY,ContactsContract.Data.DATA4,ContactsContract.Data.DATA5,ContactsContract.Data.DATA6,ContactsContract.Data.DATA7,ContactsContract.Data.DATA8,ContactsContract.Data.DATA9,ContactsContract.Data.DATA10},ContactsContract.Data.CONTACT_ID+"=? AND ("+ContactsContract.Data.MIMETYPE+"=? OR "+ContactsContract.Data.MIMETYPE+"=? OR "+ContactsContract.Data.MIMETYPE+"=? OR "+ContactsContract.Data.MIMETYPE+"=? OR "+ContactsContract.Data.MIMETYPE+"=? OR "+ContactsContract.Data.MIMETYPE+"=?)",new String[]{key,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE,ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE},ContactsContract.Data.IS_SUPER_PRIMARY+" DESC,"+ContactsContract.Data.IS_PRIMARY+" DESC,"+ContactsContract.Data._ID+" ASC")){
            return readSummary(key,person,data);
        }
    }
    /** Pure mapping; caller owns both cursors. No account data is changed. */
    static Details readSummary(String key,Cursor contact,Cursor data){
        long id=ContentUris.parseId(uri(key));
        if(contact==null||data==null||!contact.moveToNext())throw new IllegalStateException("연락처를 읽을 수 없습니다.");
        if(contact.isNull(0)||contact.getLong(0)!=id)throw new IllegalArgumentException("Mismatched contact ID");
        String name=contact.isNull(1)?"":contact.getString(1);
        if(contact.moveToNext())throw new IllegalArgumentException("Multiple contact records");
        ArrayList<PhoneField> phones=new ArrayList<>();ArrayList<LinkField> links=new ArrayList<>();ArrayList<AddressField> addresses=new ArrayList<>();HashSet<Long> ids=new HashSet<>();StringBuilder notes=new StringBuilder();String company="",jobTitle="";boolean organizationChosen=false;
        while(data.moveToNext()){
            if(data.isNull(0)||data.getLong(0)<=0||!ids.add(data.getLong(0)))throw new IllegalArgumentException("Invalid contact data ID");
            String mime=data.isNull(1)?"":data.getString(1),value=data.isNull(2)?"":data.getString(2);
            if(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mime)){
                if(value.trim().isEmpty())continue;
                phones.add(new PhoneField(Long.toString(data.getLong(0)),value,data.isNull(3)?ContactsContract.CommonDataKinds.Phone.TYPE_OTHER:data.getInt(3),data.isNull(4)?"":data.getString(4)));
            }else if(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE.equals(mime)||ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE.equals(mime)){
                if(value.trim().isEmpty())continue;
                links.add(new LinkField(Long.toString(data.getLong(0)),value,mime,data.isNull(3)?0:data.getInt(3),data.isNull(4)?"":data.getString(4)));
            }else if(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE.equals(mime)){
                String address=value.trim().isEmpty()?postalAddress(data):value;if(address.trim().isEmpty())continue;
                addresses.add(new AddressField(Long.toString(data.getLong(0)),address,data.isNull(3)?ContactsContract.CommonDataKinds.StructuredPostal.TYPE_OTHER:data.getInt(3),data.isNull(4)?"":data.getString(4)));
            }else if(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE.equals(mime)&&!organizationChosen){
                int titleColumn=data.getColumnIndex(ContactsContract.Data.DATA4);String title=titleColumn<0||data.isNull(titleColumn)?"":data.getString(titleColumn);
                if(!value.trim().isEmpty()||!title.trim().isEmpty()){company=value;jobTitle=title;organizationChosen=true;}
            }else if(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE.equals(mime)&&!value.isEmpty()){
                if(notes.length()>0)notes.append("\n\n");notes.append(value);
            }
        }
        return new Details(name,phones.toArray(new PhoneField[0]),links.toArray(new LinkField[0]),addresses.toArray(new AddressField[0]),notes.toString(),company,jobTitle);
    }
    private static String column(Cursor data,String name){int index=data.getColumnIndex(name);return index<0||data.isNull(index)?"":data.getString(index);}
    // Android adaptation: prefer literal FORMATTED_ADDRESS; otherwise join supplied
    // components in postal order. Country remains visible; legacy cursors may omit components.
    private static String postalAddress(Cursor data){
        String locality=join(", ",column(data,ContactsContract.CommonDataKinds.StructuredPostal.CITY),join(" ",column(data,ContactsContract.CommonDataKinds.StructuredPostal.REGION),column(data,ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE)));
        return join("\n",column(data,ContactsContract.CommonDataKinds.StructuredPostal.STREET),column(data,ContactsContract.CommonDataKinds.StructuredPostal.POBOX),column(data,ContactsContract.CommonDataKinds.StructuredPostal.NEIGHBORHOOD),locality,column(data,ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY));
    }
    private static String join(String separator,String... values){StringBuilder result=new StringBuilder();for(String value:values)if(!value.trim().isEmpty()){if(result.length()>0)result.append(separator);result.append(value);}return result.toString();}
    static WmContactsView.Entry[] read(ContentResolver resolver){
        WmContactsView.Entry[] contacts;
        try(Cursor rows=resolver.query(ContactsContract.Contacts.CONTENT_URI,new String[]{ContactsContract.Contacts._ID,ContactsContract.Contacts.DISPLAY_NAME_ALTERNATIVE,ContactsContract.Contacts.DISPLAY_NAME_PRIMARY},null,null,ContactsContract.Contacts.SORT_KEY_ALTERNATIVE+" ASC,"+ContactsContract.Contacts._ID+" ASC")){contacts=readRows(rows);}
        // Two batched queries, never one Phone query per contact. Native File-as and
        // preferred-phone semantics differ from Android aggregate metadata.
        try(Cursor phones=resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,new String[]{ContactsContract.CommonDataKinds.Phone.CONTACT_ID,ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,ContactsContract.CommonDataKinds.Phone.IS_PRIMARY},null,null,ContactsContract.CommonDataKinds.Phone._ID+" ASC")){return withPhones(contacts,phones);}
    }
    /** Synthetic cursor entry point also enforces the provider boundary. */
    static WmContactsView.Entry[] readRows(Cursor rows){
        if(rows==null)throw new IllegalStateException("연락처를 읽을 수 없습니다.");
        ArrayList<WmContactsView.Entry> result=new ArrayList<>();HashSet<Long> ids=new HashSet<>();
        while(rows.moveToNext()){
            if(rows.isNull(0))throw new IllegalStateException("Invalid contact ID");long id=rows.getLong(0);
            if(id<=0||!ids.add(id))throw new IllegalStateException("Invalid contact ID");
            String name=rows.isNull(1)?(rows.getColumnCount()>2&&!rows.isNull(2)?rows.getString(2):""):rows.getString(1);result.add(new WmContactsView.Entry(Long.toString(id),name));
        }return result.toArray(new WmContactsView.Entry[0]);
    }
    /** Pure preferred-phone mapping; callers retain cursor ownership. */
    static WmContactsView.Entry[] withPhones(WmContactsView.Entry[] contacts,Cursor rows){
        if(contacts==null||rows==null)throw new IllegalStateException("연락처를 읽을 수 없습니다.");
        HashMap<String,String> numbers=new HashMap<>();HashMap<String,Integer> ranks=new HashMap<>();
        while(rows.moveToNext()){
            if(rows.isNull(0)||rows.getLong(0)<=0)throw new IllegalStateException("Invalid contact ID");
            String key=Long.toString(rows.getLong(0)),number=rows.isNull(1)?"":rows.getString(1);if(number.trim().isEmpty())continue;
            int rank=rows.getInt(2)!=0?2:rows.getInt(3)!=0?1:0;Integer old=ranks.get(key);if(old!=null&&old>=rank)continue;
            ranks.put(key,rank);String formatted=PhoneNumberUtils.formatNumber(number);numbers.put(key,formatted==null?number:formatted);
        }
        WmContactsView.Entry[] result=new WmContactsView.Entry[contacts.length];
        for(int i=0;i<contacts.length;i++){WmContactsView.Entry e=contacts[i];if(e==null)throw new IllegalArgumentException("Contact required");result[i]=new WmContactsView.Entry(e.key,e.name,numbers.get(e.key));}return result;
    }
    static Uri uri(String key){
        long id;try{id=Long.parseLong(key);}catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid contact ID",invalid);}
        if(id<=0||!Long.toString(id).equals(key))throw new IllegalArgumentException("Invalid contact ID");
        return ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,id);
    }
}
