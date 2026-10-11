package me.ancal.wmdroid;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import me.ancal.wmdroid.sdk.WmMediaQueue;

/** Private named playlists, not a Windows Media WPL/ASX file engine. */
final class MediaPlaylistStore extends SQLiteOpenHelper implements java.io.Closeable {
    static final class Item {final String id,name;Item(String id,String name){this.id=id;this.name=name;}}
    MediaPlaylistStore(Context context){super(context.getApplicationContext(),"wmdroid-media.db",null,2);}
    public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL COLLATE NOCASE UNIQUE,sources TEXT NOT NULL)");librarySchema(db);}
    public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){if(oldVersion!=1||newVersion!=2)throw new IllegalStateException("Unsupported playlist database version");librarySchema(db);}
    private static void librarySchema(SQLiteDatabase db){db.execSQL("CREATE TABLE library (uri TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,mime TEXT NOT NULL,artist TEXT NOT NULL,album TEXT NOT NULL,genre TEXT NOT NULL)");}
    static final class Source {final WmMediaQueue.Entry entry;final String mime,artist,album,genre;Source(WmMediaQueue.Entry entry,String mime,String artist,String album,String genre){this.entry=entry;this.mime=mime;this.artist=artist;this.album=album;this.genre=genre;}}
    private static String bounded(String value){return value==null?"":value.length()>256?value.substring(0,256):value;}
    synchronized void remember(Context context,WmMediaQueue.Entry[] entries){for(WmMediaQueue.Entry entry:entries){if(!"content".equals(entry.uri.getScheme()))continue;String mime="",artist="",album="",genre="";boolean parsed=false;try{mime=bounded(context.getContentResolver().getType(entry.uri));}catch(RuntimeException denied){}android.media.MediaMetadataRetriever metadata=new android.media.MediaMetadataRetriever();try{metadata.setDataSource(context,entry.uri);artist=bounded(metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST));album=bounded(metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM));genre=bounded(metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE));if(mime.isEmpty())mime=bounded(metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE));parsed=true;}catch(RuntimeException unreadable){}finally{try{metadata.release();}catch(Exception unavailable){}}
        if(!parsed)try(Cursor previous=getReadableDatabase().query("library",new String[]{"mime","artist","album","genre"},"uri=?",new String[]{entry.uri.toString()},null,null,null)){if(previous.moveToFirst()){if(mime.isEmpty())mime=previous.getString(0);artist=previous.getString(1);album=previous.getString(2);genre=previous.getString(3);}}
        ContentValues values=new ContentValues();values.put("uri",entry.uri.toString());values.put("title",entry.title);values.put("mime",mime);values.put("artist",artist);values.put("album",album);values.put("genre",genre);getWritableDatabase().insertWithOnConflict("library",null,values,SQLiteDatabase.CONFLICT_REPLACE);
    }}
    synchronized Source[] sources(){try(Cursor cursor=getReadableDatabase().query("library",new String[]{"uri","title","mime","artist","album","genre"},null,null,null,null,"title COLLATE NOCASE,uri")){java.util.ArrayList<Source> rows=new java.util.ArrayList<>();while(cursor.moveToNext())rows.add(new Source(new WmMediaQueue.Entry(Uri.parse(cursor.getString(0)),cursor.getString(1)),cursor.getString(2),cursor.getString(3),cursor.getString(4),cursor.getString(5)));return rows.toArray(new Source[0]);}}
    synchronized void forget(String uri){getWritableDatabase().delete("library","uri=?",new String[]{uri});}
    synchronized String save(String name,WmMediaQueue.Entry[] entries)throws Exception {
        if(name==null||name.trim().isEmpty()||name.trim().length()>128)throw new IllegalArgumentException("Playlist name required, at most 128 characters");
        if(entries==null||entries.length==0)throw new IllegalArgumentException("Playlist sources required");
        JSONArray array=new JSONArray();for(WmMediaQueue.Entry entry:entries){if(entry==null)throw new IllegalArgumentException("Null source");array.put(new JSONObject().put("uri",entry.uri.toString()).put("title",entry.title));}
        String encoded=array.toString();if(encoded.getBytes("UTF-8").length>1024*1024)throw new IllegalArgumentException("Playlist exceeds storage bound");
        String id=java.util.UUID.randomUUID().toString();ContentValues values=new ContentValues();values.put("id",id);values.put("name",name.trim());values.put("sources",encoded);getWritableDatabase().insertOrThrow("playlists",null,values);return id;
    }
    synchronized Item[] list(){try(Cursor cursor=getReadableDatabase().query("playlists",new String[]{"id","name"},null,null,null,null,"name COLLATE NOCASE,id")){java.util.ArrayList<Item> items=new java.util.ArrayList<>();while(cursor.moveToNext())items.add(new Item(cursor.getString(0),cursor.getString(1)));return items.toArray(new Item[0]);}}
    synchronized WmMediaQueue.Entry[] load(String id)throws Exception {if(id==null)throw new IllegalArgumentException("Playlist ID required");try(Cursor cursor=getReadableDatabase().query("playlists",new String[]{"sources"},"id=?",new String[]{id},null,null,null)){if(!cursor.moveToFirst())throw new IllegalStateException("Playlist no longer exists");String text=cursor.getString(0);if(text.getBytes("UTF-8").length>1024*1024)throw new IllegalArgumentException("Playlist exceeds storage bound");JSONArray array=new JSONArray(text);WmMediaQueue.Entry[] entries=new WmMediaQueue.Entry[array.length()];for(int n=0;n<entries.length;n++){JSONObject row=array.getJSONObject(n);entries[n]=new WmMediaQueue.Entry(Uri.parse(row.getString("uri")),row.getString("title"));}if(entries.length==0)throw new IllegalArgumentException("Empty stored playlist");return entries;}}
    synchronized void delete(String id){if(id==null)throw new IllegalArgumentException("Playlist ID required");getWritableDatabase().delete("playlists","id=?",new String[]{id});}
}
