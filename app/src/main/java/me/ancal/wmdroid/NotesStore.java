package me.ancal.wmdroid;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.UUID;

/** Private text notes; unrelated to Windows Mobile PWI files. */
final class NotesStore extends SQLiteOpenHelper {
    // Keep UTF-8 rows below older Android CursorWindow limits, including non-ASCII text.
    static final int MAX_BODY=256*1024;
    private static final String[] COLUMNS={"id","title","body","modified","revision","length(CAST(body AS BLOB))","folder"};

    static final class Entry {
        final String id,title,body,folder;
        final long modified,revision,size;
        Entry(String id,String title,String body,long modified,long revision,long size){
            this(id,title,body,modified,revision,size,"");
        }
        Entry(String id,String title,String body,long modified,long revision,long size,String folder){
            this.id=id;this.title=title;this.body=body;this.modified=modified;this.revision=revision;this.size=size;this.folder=folder;
        }
    }
    static final class Folder {
        final String id,name;
        Folder(String id,String name){this.id=id;this.name=name;}
    }

    NotesStore(Context context){super(context.getApplicationContext(),"wmdroid-notes.db",null,2);}

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE notes (id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,body TEXT NOT NULL,modified INTEGER NOT NULL,revision INTEGER NOT NULL,folder TEXT NOT NULL DEFAULT '')");
        createFolders(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion!=1||newVersion!=2)throw new IllegalStateException("Unsupported notes database version");
        db.execSQL("ALTER TABLE notes ADD COLUMN folder TEXT NOT NULL DEFAULT ''");createFolders(db);
    }
    private static void createFolders(SQLiteDatabase db){
        // SQLite NOCASE folds ASCII only; non-ASCII case variants remain distinct folder names.
        db.execSQL("CREATE TABLE notes_folders (id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL COLLATE NOCASE UNIQUE)");
        db.execSQL("CREATE INDEX notes_folder ON notes(folder)");
    }

    /** List metadata only; bodies are empty until get() opens the note. */
    synchronized Entry[] list(){
        Cursor cursor=getReadableDatabase().query("notes",new String[]{"id","title","'' AS body","modified","revision","length(CAST(body AS BLOB))","folder"},null,null,null,null,"modified DESC,id ASC");
        try{
            ArrayList<Entry> entries=new ArrayList<Entry>();
            while(cursor.moveToNext())entries.add(read(cursor));
            return entries.toArray(new Entry[entries.size()]);
        }finally{cursor.close();}
    }

    synchronized Entry get(String id){
        validateId(id);
        Cursor cursor=getReadableDatabase().query("notes",COLUMNS,"id=?",new String[]{id},null,null,null);
        try{return cursor.moveToFirst()?read(cursor):null;}finally{cursor.close();}
    }

    synchronized Entry save(String id,long expectedRevision,String title,String body){
        return save(id,expectedRevision,title,body,"");
    }
    /** Folder applies only to creation; existing body updates always retain their current folder. */
    synchronized Entry save(String id,long expectedRevision,String title,String body,String folder){
        validateId(id);
        if(expectedRevision<0||expectedRevision==Long.MAX_VALUE)throw new IllegalArgumentException("Invalid note revision");
        if(title==null||title.length()>256)throw new IllegalArgumentException("Note title must be at most 256 characters");
        if(body==null||body.length()>MAX_BODY)throw new IllegalArgumentException("Note body must be at most "+MAX_BODY+" characters");
        long modified=System.currentTimeMillis(),revision=expectedRevision+1;
        ContentValues values=new ContentValues();
        values.put("title",title);values.put("body",body);values.put("modified",modified);values.put("revision",revision);
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            if(expectedRevision==0){
                requireFolder(db,folder);values.put("id",id);values.put("folder",folder);
                try{db.insertOrThrow("notes",null,values);}
                catch(SQLiteConstraintException conflict){throw new IllegalStateException("Note already exists",conflict);}
            }else if(db.update("notes",values,"id=? AND revision=?",new String[]{id,Long.toString(expectedRevision)})!=1){
                throw new IllegalStateException("Note changed or no longer exists");
            }
            Entry saved=get(id);db.setTransactionSuccessful();return saved;
        }finally{db.endTransaction();}
    }

    /** Every successful rename advances revision, including an unchanged title; content time is preserved. */
    synchronized Entry rename(String id,long expectedRevision,String title){
        return renameChecked(id,expectedRevision,title,null);
    }
    synchronized Entry rename(String id,long expectedRevision,String title,String folder){
        if(folder==null)throw new IllegalArgumentException("Folder required");
        return renameChecked(id,expectedRevision,title,folder);
    }
    private Entry renameChecked(String id,long expectedRevision,String title,String folder){
        validateId(id);
        if(expectedRevision<=0||expectedRevision==Long.MAX_VALUE)throw new IllegalArgumentException("Invalid note revision");
        if(title==null||title.length()>256)throw new IllegalArgumentException("Note title must be nonblank and at most 256 characters");
        boolean nonblank=false;
        for(int i=0;i<title.length();i++){char c=title.charAt(i);if(c=='\n'||c=='\r'||c==0)throw new IllegalArgumentException("Note title cannot contain line breaks or NUL");if(!Character.isWhitespace(c)&&!Character.isSpaceChar(c))nonblank=true;}
        if(!nonblank)throw new IllegalArgumentException("Note title must be nonblank");
        ContentValues values=new ContentValues();values.put("title",title);values.put("revision",expectedRevision+1);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            if(folder!=null){requireFolder(db,folder);values.put("folder",folder);}
            if(db.update("notes",values,"id=? AND revision=?",new String[]{id,Long.toString(expectedRevision)})!=1)throw new IllegalStateException("Note changed or no longer exists");
            Entry renamed=get(id);db.setTransactionSuccessful();return renamed;
        }finally{db.endTransaction();}
    }

    /** Deletion accepts any positive revision, including Long.MAX_VALUE: it never increments it. */
    synchronized void delete(String id,long expectedRevision){
        validateId(id);
        if(expectedRevision<=0)throw new IllegalArgumentException("Invalid note revision");
        if(getWritableDatabase().delete("notes","id=? AND revision=?",new String[]{id,Long.toString(expectedRevision)})!=1)throw new IllegalStateException("Note changed or no longer exists");
    }

    synchronized Folder[] folders(){
        Cursor cursor=getReadableDatabase().query("notes_folders",new String[]{"id","name"},null,null,null,null,"name COLLATE NOCASE,id");
        try{ArrayList<Folder> folders=new ArrayList<Folder>();while(cursor.moveToNext())folders.add(new Folder(cursor.getString(0),cursor.getString(1)));return folders.toArray(new Folder[folders.size()]);}finally{cursor.close();}
    }
    synchronized Folder createFolder(String name){
        validateFolderName(name);String id=UUID.randomUUID().toString();ContentValues values=new ContentValues();values.put("id",id);values.put("name",name);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            try{db.insertOrThrow("notes_folders",null,values);}catch(SQLiteConstraintException conflict){throw new IllegalStateException("Folder name already exists",conflict);}
            db.setTransactionSuccessful();return new Folder(id,name);
        }finally{db.endTransaction();}
    }
    synchronized Folder renameFolder(String id,String name){
        validateFolderId(id);validateFolderName(name);ContentValues values=new ContentValues();values.put("name",name);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            try{if(db.update("notes_folders",values,"id=?",new String[]{id})!=1)throw new IllegalStateException("Folder no longer exists");}catch(SQLiteConstraintException conflict){throw new IllegalStateException("Folder name already exists",conflict);}
            db.setTransactionSuccessful();return new Folder(id,name);
        }finally{db.endTransaction();}
    }
    synchronized void deleteFolder(String id){
        validateFolderId(id);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            Cursor cursor=db.query("notes",new String[]{"id"},"folder=?",new String[]{id},null,null,null,"1");
            try{if(cursor.moveToFirst())throw new IllegalStateException("Folder is not empty");}finally{cursor.close();}
            if(db.delete("notes_folders","id=?",new String[]{id})!=1)throw new IllegalStateException("Folder no longer exists");db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private static void validateFolderName(String name){
        if(!me.ancal.wmdroid.sdk.WmDocumentProperties.validName(name)||name.length()>64)throw new IllegalArgumentException("Invalid folder name");
        // Reserve both authored languages so a later language change cannot hide a real folder.
        for(String reserved:new String[]{"None","All Folders","Templates","없음","모든 폴더","서식 파일"})if(reserved.equalsIgnoreCase(name))throw new IllegalArgumentException("Reserved folder name");
    }
    private static void validateFolderId(String id){
        if(id==null||!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException("Invalid folder ID");
    }
    private static void requireFolder(SQLiteDatabase db,String id){
        if("".equals(id))return;validateFolderId(id);
        Cursor cursor=db.query("notes_folders",new String[]{"id"},"id=?",new String[]{id},null,null,null);
        try{if(!cursor.moveToFirst())throw new IllegalStateException("Folder no longer exists");}finally{cursor.close();}
    }

    private static void validateId(String id){
        if(id==null||id.length()==0||id.length()>128)throw new IllegalArgumentException("Invalid note ID");
    }

    private static Entry read(Cursor cursor){
        return new Entry(cursor.getString(0),cursor.getString(1),cursor.getString(2),cursor.getLong(3),cursor.getLong(4),cursor.getLong(5),cursor.getString(6));
    }
}
