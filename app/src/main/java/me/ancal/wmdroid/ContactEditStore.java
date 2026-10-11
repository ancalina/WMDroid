package me.ancal.wmdroid;

import android.Manifest;
import android.content.Context;
import android.content.ContentResolver;
import android.content.ContentProviderResult;
import android.content.ContentProviderOperation;
import android.content.OperationApplicationException;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.Bundle;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.provider.ContactsContract;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** Account-aware snapshots and sparse batches; permission-gated execution stays in one batch. */
final class ContactEditStore {
    private static final String[] ACCOUNT_COLUMNS={ContactsContract.RawContacts._ID,ContactsContract.RawContacts.CONTACT_ID,ContactsContract.RawContacts.VERSION,ContactsContract.RawContacts.ACCOUNT_NAME,ContactsContract.RawContacts.ACCOUNT_TYPE,ContactsContract.RawContacts.DATA_SET,ContactsContract.RawContacts.DELETED,ContactsContract.RawContacts.RAW_CONTACT_IS_READ_ONLY};
    private static final String[] ROW_COLUMNS={ContactsContract.Data._ID,ContactsContract.Data.RAW_CONTACT_ID,ContactsContract.Data.DATA_VERSION,ContactsContract.Data.MIMETYPE,ContactsContract.Data.IS_READ_ONLY};
    private final Context context;
    private final ContentResolver resolver;
    ContactEditStore(Context context){if(context==null)throw new IllegalArgumentException("Context required");Context application=context.getApplicationContext();this.context=application==null?context:application;resolver=context.getContentResolver();}
    Snapshot snapshot(String aggregateKey){
        long contactId=ContentUris.parseId(ContactsStore.uri(aggregateKey));permission(Manifest.permission.READ_CONTACTS);
        try(Cursor accounts=resolver.query(ContactsContract.RawContacts.CONTENT_URI,ACCOUNT_COLUMNS,ContactsContract.RawContacts.CONTACT_ID+"=? AND "+ContactsContract.RawContacts.DELETED+"=0",new String[]{aggregateKey},ContactsContract.RawContacts._ID+" ASC")){
            if(accounts==null)throw new IllegalStateException("Contact accounts unavailable");int idColumn=accounts.getColumnIndexOrThrow(ContactsContract.RawContacts._ID),contactColumn=accounts.getColumnIndexOrThrow(ContactsContract.RawContacts.CONTACT_ID);ArrayList<String> ids=new ArrayList<>();HashSet<Long> unique=new HashSet<>();
            while(accounts.moveToNext()){long id=integer(accounts,idColumn,1);if(!unique.add(id)||integer(accounts,contactColumn,1)!=contactId)throw new IllegalArgumentException("Invalid contact account selection");ids.add(Long.toString(id));}
            if(ids.isEmpty())throw new IllegalStateException("Contact no longer exists");accounts.moveToPosition(-1);
            StringBuilder where=new StringBuilder(ContactsContract.Data.CONTACT_ID+"=? AND "+ContactsContract.Data.RAW_CONTACT_ID+" IN (");String[] arguments=new String[ids.size()+1];arguments[0]=aggregateKey;
            for(int i=0;i<ids.size();i++){if(i>0)where.append(',');where.append('?');arguments[i+1]=ids.get(i);}where.append(')');String[] projection=new String[19];System.arraycopy(ROW_COLUMNS,0,projection,0,ROW_COLUMNS.length);for(int i=1;i<=14;i++)projection[i+4]="data"+i;
            try(Cursor rows=resolver.query(ContactsContract.Data.CONTENT_URI,projection,where.toString(),arguments,ContactsContract.Data._ID+" ASC")){if(rows==null)throw new IllegalStateException("Contact data unavailable");return readSnapshot(accounts,rows);}
        }
    }
    void save(Snapshot snapshot,long rawId,Update[] updates,Insert[] inserts){
        permission(Manifest.permission.READ_CONTACTS);permission(Manifest.permission.WRITE_CONTACTS);ArrayList<ContentProviderOperation> prepared=operations(snapshot,rawId,updates,inserts);if(prepared.isEmpty())return;
        try{ContentProviderResult[] results=resolver.applyBatch(ContactsContract.AUTHORITY,prepared);if(results==null||results.length!=prepared.size())throw new IllegalStateException("Incomplete contact edit results");
            for(int i=0;i<results.length;i++){ContentProviderResult result=results[i];if(result==null)throw new IllegalStateException("Missing contact edit result");if(i>=prepared.size()-inserts.length){if(result.uri==null||!ContactsContract.AUTHORITY.equals(result.uri.getAuthority()))throw new IllegalStateException("Invalid inserted contact URI");}else if(result.count==null||result.count!=1)throw new IllegalStateException("Invalid contact edit count");}
        }catch(RemoteException|OperationApplicationException failure){throw new IllegalStateException("Contact edit failed",failure);}
    }
    private void permission(String name){if(DeviceApi.permission(context,name)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Contact permission required: "+name);}
    static final class Account {
        final long id,contactId,version;
        final String name,type,dataSet;
        final boolean readOnly;
        Account(long id,long contactId,long version,String name,String type,String dataSet,boolean readOnly){
            if(id<=0||contactId<=0||version<0)throw new IllegalArgumentException("Invalid raw contact metadata");
            this.id=id;this.contactId=contactId;this.version=version;this.name=name;this.type=type;this.dataSet=dataSet;this.readOnly=readOnly;
        }
    }
    static final class Row {
        final long id,rawId,version;
        final String mime;
        final boolean readOnly;
        private final ContentValues values;
        Row(long id,long rawId,long version,String mime,boolean readOnly,ContentValues values){
            if(id<=0||rawId<=0||version<0||mime==null||mime.isEmpty())throw new IllegalArgumentException("Invalid contact data metadata");
            this.id=id;this.rawId=rawId;this.version=version;this.mime=mime;this.readOnly=readOnly;this.values=copy(values);
        }
        ContentValues values(){return copy(values);}
    }
    static final class Snapshot {
        private final Account[] accounts;
        private final Row[] rows;
        Snapshot(Account[] accounts,Row[] rows){
            if(accounts==null||rows==null)throw new IllegalArgumentException("Snapshot arrays required");this.accounts=accounts.clone();this.rows=rows.clone();
            HashSet<Long> rawIds=new HashSet<>(),rowIds=new HashSet<>();long contact=0;
            for(Account account:this.accounts){if(account==null||!rawIds.add(account.id)||contact!=0&&contact!=account.contactId)throw new IllegalArgumentException("Invalid aggregate accounts");contact=account.contactId;}
            for(Row row:this.rows)if(row==null||!rowIds.add(row.id)||!rawIds.contains(row.rawId))throw new IllegalArgumentException("Invalid aggregate data rows");
        }
        Account[] accounts(){return accounts.clone();}
        Row[] rows(){return rows.clone();}
    }
    static final class Update {
        final long rowId;
        private final ContentValues values;
        Update(long rowId,ContentValues values){this.rowId=rowId;this.values=copy(values);}
        ContentValues values(){return copy(values);}
    }
    static final class Insert {
        final String mime;
        private final ContentValues values;
        Insert(String mime,ContentValues values){this.mime=mime;this.values=copy(values);}
        ContentValues values(){return copy(values);}
    }
    static Bundle saveSnapshot(Snapshot snapshot){
        if(snapshot==null)throw new IllegalArgumentException("Snapshot required");Bundle result=new Bundle();result.putInt("schema",1);ArrayList<Bundle> accounts=new ArrayList<>(),rows=new ArrayList<>();
        for(Account account:snapshot.accounts){Bundle value=new Bundle();value.putLong("id",account.id);value.putLong("contactId",account.contactId);value.putLong("version",account.version);value.putString("name",account.name);value.putString("type",account.type);value.putString("dataSet",account.dataSet);value.putBoolean("readOnly",account.readOnly);accounts.add(value);}
        for(Row row:snapshot.rows){ContentValues data=row.values();stateValues(data);Bundle value=new Bundle();value.putLong("id",row.id);value.putLong("rawId",row.rawId);value.putLong("version",row.version);value.putString("mime",row.mime);value.putBoolean("readOnly",row.readOnly);value.putParcelable("values",data);rows.add(value);}
        result.putParcelableArrayList("accounts",accounts);result.putParcelableArrayList("rows",rows);return result;
    }
    static Snapshot restoreSnapshot(Bundle saved){
        shape(saved,"schema","accounts","rows");if((Integer)state(saved,"schema",Integer.class,false)!=1)throw new IllegalArgumentException("Unknown snapshot schema");
        ArrayList<?> accounts=(ArrayList<?>)state(saved,"accounts",ArrayList.class,false),rows=(ArrayList<?>)state(saved,"rows",ArrayList.class,false);Account[] raw=new Account[accounts.size()];Row[] data=new Row[rows.size()];
        for(int i=0;i<raw.length;i++){Bundle value=stateBundle(accounts.get(i));shape(value,"id","contactId","version","name","type","dataSet","readOnly");raw[i]=new Account((Long)state(value,"id",Long.class,false),(Long)state(value,"contactId",Long.class,false),(Long)state(value,"version",Long.class,false),(String)state(value,"name",String.class,true),(String)state(value,"type",String.class,true),(String)state(value,"dataSet",String.class,true),(Boolean)state(value,"readOnly",Boolean.class,false));}
        for(int i=0;i<data.length;i++){Bundle value=stateBundle(rows.get(i));shape(value,"id","rawId","version","mime","readOnly","values");ContentValues contents=(ContentValues)state(value,"values",ContentValues.class,false);stateValues(contents);data[i]=new Row((Long)state(value,"id",Long.class,false),(Long)state(value,"rawId",Long.class,false),(Long)state(value,"version",Long.class,false),(String)state(value,"mime",String.class,false),(Boolean)state(value,"readOnly",Boolean.class,false),contents);}
        return new Snapshot(raw,data);
    }
    private static void shape(Bundle value,String... keys){if(value==null||value.size()!=keys.length)throw new IllegalArgumentException("Invalid snapshot state shape");for(String key:keys)if(!value.containsKey(key))throw new IllegalArgumentException("Missing snapshot state key");}
    private static Object state(Bundle value,String key,Class<?> type,boolean nullable){Object item=value.get(key);if(item==null? !nullable:!type.isInstance(item))throw new IllegalArgumentException("Invalid snapshot state type: "+key);return item;}
    private static Bundle stateBundle(Object value){if(!(value instanceof Bundle))throw new IllegalArgumentException("Snapshot entry Bundle required");return (Bundle)value;}
    private static void stateValues(ContentValues values){dataColumns(values);for(Map.Entry<String,Object> entry:values.valueSet()){Object value=entry.getValue();if(value!=null&&!(value instanceof String||value instanceof Boolean||integral(value)||value instanceof Float||value instanceof Double||value instanceof byte[]))throw new IllegalArgumentException("Invalid snapshot data value");}}
    /** Caller retains cursor ownership. Required metadata is strictly typed, not coerced. */
    static Snapshot readSnapshot(Cursor accounts,Cursor rows){
        if(accounts==null||rows==null)throw new IllegalArgumentException("Snapshot cursors required");
        int[] a=columns(accounts,ACCOUNT_COLUMNS),r=columns(rows,ROW_COLUMNS),data=new int[14];for(int i=0;i<data.length;i++)data[i]=rows.getColumnIndexOrThrow("data"+(i+1));
        ArrayList<Account> raw=new ArrayList<>();ArrayList<Row> entries=new ArrayList<>();
        while(accounts.moveToNext()){
            if(flag(accounts,a[6]))throw new IllegalArgumentException("Deleted raw contact");
            raw.add(new Account(integer(accounts,a[0],1),integer(accounts,a[1],1),integer(accounts,a[2],0),accounts.isNull(a[3])?null:accounts.getString(a[3]),accounts.isNull(a[4])?null:accounts.getString(a[4]),accounts.isNull(a[5])?null:accounts.getString(a[5]),flag(accounts,a[7])));
        }
        while(rows.moveToNext()){
            if(rows.getType(r[3])!=Cursor.FIELD_TYPE_STRING)throw new IllegalArgumentException("Data MIME required");ContentValues values=new ContentValues();
            for(int i=0;i<data.length;i++)put(values,"data"+(i+1),rows,data[i]);
            entries.add(new Row(integer(rows,r[0],1),integer(rows,r[1],1),integer(rows,r[2],0),rows.getString(r[3]),flag(rows,r[4]),values));
        }
        return new Snapshot(raw.toArray(new Account[0]),entries.toArray(new Row[0]));
    }
    // ponytail: DATA15 blobs and native picker properties are excluded; extend only with an explicit owner contract.
    static ArrayList<ContentProviderOperation> operations(Snapshot snapshot,long rawId,Update[] updates,Insert[] inserts){
        if(snapshot==null||rawId<=0||updates==null||inserts==null)throw new IllegalArgumentException("Edit snapshot and changes required");
        Account selected=null;for(Account account:snapshot.accounts)if(account.id==rawId)selected=account;
        if(selected==null||selected.readOnly)throw new IllegalArgumentException("Writable raw contact required");
        HashMap<Long,Row> originals=new HashMap<>();for(Row row:snapshot.rows)originals.put(row.id,row);
        HashSet<Long> requested=new HashSet<>();ArrayList<Row> changedRows=new ArrayList<>();ArrayList<ContentValues> changes=new ArrayList<>();
        for(Update update:updates){
            if(update==null||update.rowId<=0||!requested.add(update.rowId))throw new IllegalArgumentException("Null or duplicate row update");Row row=originals.get(update.rowId);
            if(row==null||row.rawId!=rawId||row.readOnly)throw new IllegalArgumentException("Writable data row in selected account required");editable(row.mime);dataColumns(update.values);
            ContentValues delta=copy(update.values);for(String key:update.values.keySet())if(equal(update.values.get(key),row.values.get(key)))delta.remove(key);
            if(delta.size()>0){changedRows.add(row);changes.add(delta);}
        }
        for(Insert insert:inserts){if(insert==null)throw new IllegalArgumentException("Insert required");editable(insert.mime);dataColumns(insert.values);}
        ArrayList<ContentProviderOperation> operations=new ArrayList<>();if(changedRows.isEmpty()&&inserts.length==0)return operations;
        ContentValues raw=new ContentValues();raw.put(ContactsContract.RawContacts._ID,selected.id);raw.put(ContactsContract.RawContacts.CONTACT_ID,selected.contactId);raw.put(ContactsContract.RawContacts.VERSION,selected.version);raw.put(ContactsContract.RawContacts.ACCOUNT_NAME,selected.name);raw.put(ContactsContract.RawContacts.ACCOUNT_TYPE,selected.type);raw.put(ContactsContract.RawContacts.DATA_SET,selected.dataSet);raw.put(ContactsContract.RawContacts.DELETED,0);raw.put(ContactsContract.RawContacts.RAW_CONTACT_IS_READ_ONLY,0);
        operations.add(ContentProviderOperation.newAssertQuery(ContactsContract.RawContacts.CONTENT_URI).withSelection(ContactsContract.RawContacts._ID+"=?",new String[]{Long.toString(rawId)}).withValues(raw).withExpectedCount(1).build());
        for(Row row:changedRows){ContentValues expected=new ContentValues();expected.put(ContactsContract.Data._ID,row.id);expected.put(ContactsContract.Data.RAW_CONTACT_ID,row.rawId);expected.put(ContactsContract.Data.MIMETYPE,row.mime);expected.put(ContactsContract.Data.DATA_VERSION,row.version);expected.put(ContactsContract.Data.IS_READ_ONLY,0);
            operations.add(ContentProviderOperation.newAssertQuery(ContactsContract.Data.CONTENT_URI).withSelection(ContactsContract.Data._ID+"=?",new String[]{Long.toString(row.id)}).withValues(expected).withExpectedCount(1).build());
        }
        for(int i=0;i<changedRows.size();i++){Row row=changedRows.get(i);String where=ContactsContract.Data.RAW_CONTACT_ID+"=? AND "+ContactsContract.Data.MIMETYPE+"=? AND "+ContactsContract.Data.IS_READ_ONLY+"=0";
            operations.add(ContentProviderOperation.newUpdate(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI,row.id)).withValues(changes.get(i)).withSelection(where,new String[]{Long.toString(rawId),row.mime}).withExpectedCount(1).build());
        }
        for(Insert insert:inserts){ContentValues values=copy(insert.values);values.put(ContactsContract.Data.RAW_CONTACT_ID,rawId);values.put(ContactsContract.Data.MIMETYPE,insert.mime);operations.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValues(values).build());}
        return operations;
    }
    private static int[] columns(Cursor cursor,String[] names){int[] indexes=new int[names.length];for(int i=0;i<names.length;i++)indexes[i]=cursor.getColumnIndexOrThrow(names[i]);return indexes;}
    private static long integer(Cursor cursor,int index,long minimum){if(cursor.getType(index)!=Cursor.FIELD_TYPE_INTEGER)throw new IllegalArgumentException("Integer metadata required");long value=cursor.getLong(index);if(value<minimum)throw new IllegalArgumentException("Invalid integer metadata");return value;}
    private static boolean flag(Cursor cursor,int index){long value=integer(cursor,index,0);if(value>1)throw new IllegalArgumentException("Boolean flag required");return value==1;}
    private static void put(ContentValues values,String key,Cursor cursor,int index){switch(cursor.getType(index)){
        case Cursor.FIELD_TYPE_NULL:values.putNull(key);break;
        case Cursor.FIELD_TYPE_INTEGER:values.put(key,cursor.getLong(index));break;
        case Cursor.FIELD_TYPE_FLOAT:values.put(key,cursor.getDouble(index));break;
        case Cursor.FIELD_TYPE_STRING:values.put(key,cursor.getString(index));break;
        case Cursor.FIELD_TYPE_BLOB:values.put(key,cursor.getBlob(index).clone());break;
        default:throw new IllegalArgumentException("Unsupported cursor value");
    }}
    private static ContentValues copy(ContentValues source){if(source==null)throw new IllegalArgumentException("Values required");ContentValues result=new ContentValues(source);for(Map.Entry<String,Object> entry:source.valueSet())if(entry.getValue() instanceof byte[])result.put(entry.getKey(),((byte[])entry.getValue()).clone());return result;}
    private static boolean equal(Object a,Object b){if(a instanceof byte[]&&b instanceof byte[])return Arrays.equals((byte[])a,(byte[])b);if(integral(a)&&integral(b))return ((Number)a).longValue()==((Number)b).longValue();if((a instanceof Float||a instanceof Double)&&(b instanceof Float||b instanceof Double))return Double.compare(((Number)a).doubleValue(),((Number)b).doubleValue())==0;return a==null?b==null:a.equals(b);}
    private static boolean integral(Object value){return value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long;}
    private static void dataColumns(ContentValues values){for(String key:values.keySet()){boolean valid=false;for(int i=1;i<=14;i++)if(("data"+i).equals(key)){valid=true;break;}if(!valid)throw new IllegalArgumentException("Only data1..data14 may change");}}
    private static void editable(String mime){if(!ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE.equals(mime)&&!ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mime)&&!ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE.equals(mime)&&!ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE.equals(mime)&&!ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE.equals(mime)&&!ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE.equals(mime)&&!ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE.equals(mime)&&!ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE.equals(mime)&&!ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE.equals(mime))throw new IllegalArgumentException("Unsupported editable MIME");}
}
