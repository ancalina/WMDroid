package me.ancal.wmdroid;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import me.ancal.wmdroid.sdk.WmBrowserView;

/** App-private browser records; call on the host worker, never during drawing. */
final class BrowserStore extends SQLiteOpenHelper implements java.io.Closeable {
    static final class Entry {
        final long id;final String name,url;
        Entry(long id,String name,String url){this.id=id;this.name=name;this.url=url;}
    }
    static final class Folder {
        final long id,parent;final String name;
        Folder(long id,long parent,String name){this.id=id;this.parent=parent;this.name=name;}
    }
    BrowserStore(Context context){super(context.getApplicationContext(),"wmdroid-browser.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE favorites (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,url TEXT NOT NULL)");
        db.execSQL("CREATE TABLE visits (url TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,visited INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new IllegalStateException("Unsupported browser database version");}
    @Override public void onOpen(SQLiteDatabase db){
        super.onOpen(db);
        // Additive tables keep version1 readers/writers and their original records valid.
        if(db.isReadOnly())return;
        db.beginTransaction();try{
            db.execSQL("CREATE TABLE IF NOT EXISTS favorite_folders (id INTEGER PRIMARY KEY AUTOINCREMENT,parent INTEGER NOT NULL,name TEXT COLLATE NOCASE NOT NULL,UNIQUE(parent,name))");
            db.execSQL("CREATE TABLE IF NOT EXISTS favorite_locations (favorite INTEGER PRIMARY KEY,folder INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS favorite_location_folder ON favorite_locations(folder)");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private static String address(String value){String url=WmBrowserView.normalizeAddress(value);if("about:blank".equals(url))throw new IllegalArgumentException("Web address required");return url;}
    private static String name(String value,String url){String text=value==null?"":value.trim();return text.isEmpty()?url:text.length()>256?text.substring(0,256):text;}
    long add(String title,String value){String url=address(value);ContentValues data=new ContentValues();data.put("name",name(title,url));data.put("url",url);return getWritableDatabase().insertOrThrow("favorites",null,data);}
    long add(String title,String value,long folder){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            requireFolder(db,folder);long id=add(title,value);
            if(folder!=0){ContentValues location=new ContentValues();location.put("favorite",id);location.put("folder",folder);db.insertOrThrow("favorite_locations",null,location);}
            db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    List<Folder> folders(){
        List<Folder> result=new ArrayList<Folder>();
        try(Cursor rows=getReadableDatabase().query("favorite_folders",new String[]{"id","parent","name"},null,null,null,null,"name COLLATE NOCASE,id")){
            while(rows.moveToNext())result.add(new Folder(rows.getLong(0),rows.getLong(1),rows.getString(2)));
        }return result;
    }
    long createFolder(long parent,String title){
        if(!me.ancal.wmdroid.sdk.WmDocumentProperties.validName(title))throw new IllegalArgumentException("Invalid folder name");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            requireFolder(db,parent);ContentValues data=new ContentValues();data.put("parent",parent);data.put("name",title);
            long id=db.insertOrThrow("favorite_folders",null,data);db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    void renameFolder(long id,String title){
        if(id==0||!me.ancal.wmdroid.sdk.WmDocumentProperties.validName(title))throw new IllegalArgumentException("Invalid folder name/id");
        ContentValues data=new ContentValues();data.put("name",title);
        if(getWritableDatabase().update("favorite_folders",data,"id=?",new String[]{Long.toString(id)})!=1)throw new IllegalArgumentException("Favorite folder missing");
    }
    void updateFavorite(long id,String title,String value,long folder){
        String url=address(value);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            requireFolder(db,folder);ContentValues data=new ContentValues();data.put("name",name(title,url));data.put("url",url);
            if(db.update("favorites",data,"id=?",new String[]{Long.toString(id)})!=1)throw new IllegalArgumentException("Favorite missing");
            db.delete("favorite_locations","favorite=?",new String[]{Long.toString(id)});
            if(folder!=0){ContentValues location=new ContentValues();location.put("favorite",id);location.put("folder",folder);db.insertOrThrow("favorite_locations",null,location);}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    void deleteFolder(long id){
        if(id==0)throw new IllegalArgumentException("Cannot delete favorites root");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            requireFolder(db,id);List<Long> subtree=new ArrayList<Long>();java.util.HashSet<Long> seen=new java.util.HashSet<Long>();subtree.add(id);seen.add(id);
            for(int n=0;n<subtree.size();n++)try(Cursor children=db.query("favorite_folders",new String[]{"id"},"parent=?",new String[]{Long.toString(subtree.get(n))},null,null,null)){
                while(children.moveToNext()){long child=children.getLong(0);if(!seen.add(child))throw new IllegalStateException("Cyclic favorite folders");subtree.add(child);}
            }
            for(long folder:subtree){String[] args={Long.toString(folder)};db.delete("favorites","id IN (SELECT favorite FROM favorite_locations WHERE folder=?)",args);db.delete("favorite_locations","folder=?",args);}
            for(int n=subtree.size()-1;n>=0;n--)db.delete("favorite_folders","id=?",new String[]{Long.toString(subtree.get(n))});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private static void requireFolder(SQLiteDatabase db,long folder){
        if(folder==0)return;
        try(Cursor row=db.query("favorite_folders",new String[]{"id"},"id=?",new String[]{Long.toString(folder)},null,null,null)){
            if(!row.moveToFirst())throw new IllegalArgumentException("Favorite folder missing");
        }
    }
    List<Entry> folderEntries(long folder){
        SQLiteDatabase db=getReadableDatabase();requireFolder(db,folder);List<Entry> result=new ArrayList<Entry>();
        String query=folder==0?"SELECT f.id,f.name,f.url FROM favorites f LEFT JOIN favorite_locations l ON l.favorite=f.id WHERE l.favorite IS NULL OR l.folder=0":"SELECT f.id,f.name,f.url FROM favorite_locations l JOIN favorites f ON f.id=l.favorite WHERE l.folder=?";
        try(Cursor rows=db.rawQuery(query+" ORDER BY f.name COLLATE NOCASE,f.id",folder==0?null:new String[]{Long.toString(folder)})){
            while(rows.moveToNext())result.add(new Entry(rows.getLong(0),rows.getString(1),rows.getString(2)));
        }return result;
    }
    void visit(String title,String value){String url=address(value);ContentValues data=new ContentValues();data.put("name",name(title,url));data.put("url",url);data.put("visited",System.currentTimeMillis());if(getWritableDatabase().insertWithOnConflict("visits",null,data,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new android.database.sqlite.SQLiteException("Visit write failed");}
    List<Entry> entries(boolean history){
        List<Entry> entries=new ArrayList<Entry>();
        try(Cursor cursor=getReadableDatabase().query(history?"visits":"favorites",new String[]{history?"rowid":"id","name","url"},null,null,null,null,history?"visited DESC,rowid DESC":"name COLLATE NOCASE,id")){
            while(cursor.moveToNext())entries.add(new Entry(cursor.getLong(0),cursor.getString(1),cursor.getString(2)));
        }
        return entries;
    }
    void delete(long id){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("favorite_locations","favorite=?",new String[]{Long.toString(id)});db.delete("favorites","id=?",new String[]{Long.toString(id)});db.setTransactionSuccessful();}finally{db.endTransaction();}}
    void clearHistory(){getWritableDatabase().delete("visits",null,null);}
}
