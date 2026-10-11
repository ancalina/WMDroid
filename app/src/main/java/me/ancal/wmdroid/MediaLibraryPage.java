package me.ancal.wmdroid;

import android.content.Context;
import android.os.Handler;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import me.ancal.wmdroid.sdk.WmMediaView;
import me.ancal.wmdroid.sdk.WmMediaLibraryView;
import me.ancal.wmdroid.sdk.WmMediaQueue;

/** Cached user-selected media only; native whole-device/Storage Card indexing remains separate. */
final class MediaLibraryPage {
    private final Context context;private final WmMediaView player;private final Executor worker;private final Handler handler;private final java.util.function.Consumer<String> notice;
    private MediaPlaylistStore.Source[] sources=new MediaPlaylistStore.Source[0];private MediaPlaylistStore.Item[] playlists=new MediaPlaylistStore.Item[0];
    private String page="root",group="";private int request;private boolean updating;
    MediaLibraryPage(Context context,WmMediaView player,Executor worker,Handler handler,java.util.function.Consumer<String> notice){this.context=context;this.player=player;this.worker=worker;this.handler=handler;this.notice=notice;}
    private boolean alive(){return player.state()!=WmMediaView.State.CLOSED;}
    void show(){page="root";group="";render();player.showLibrary(true);refresh();}
    void playlists(){page="playlists";group="";render();player.showLibrary(true);refresh();}
    void refresh(){final int token=++request;worker.execute(()->{try(MediaPlaylistStore store=new MediaPlaylistStore(context)){MediaPlaylistStore.Source[] next=store.sources();MediaPlaylistStore.Item[] saved=store.list();handler.post(()->{if(!alive()||request!=token)return;sources=next;playlists=saved;render();});}catch(Exception error){handler.post(()->{if(alive()&&request==token)notice.accept("라이브러리를 열 수 없습니다.");});}});}
    void update(){if(updating)return;updating=true;worker.execute(()->{try(MediaPlaylistStore store=new MediaPlaylistStore(context)){MediaPlaylistStore.Source[] rows=store.sources();WmMediaQueue.Entry[] entries=new WmMediaQueue.Entry[rows.length];for(int n=0;n<rows.length;n++)entries[n]=rows[n].entry;store.remember(context,entries);}catch(Exception error){handler.post(()->{if(alive())notice.accept("라이브러리를 갱신할 수 없습니다.");});}finally{handler.post(()->{updating=false;if(alive())refresh();});}});}
    void back(){if(page.equals("root")){player.showLibrary(false);return;}if(page.startsWith("group:")){page=page.substring(6);group="";}else if(page.equals("all")||page.equals("artist")||page.equals("album")||page.equals("genre"))page="music";else page="root";render();}
    void open(){WmMediaLibraryView.Row row=player.library().selected();if(row==null)return;
        if(page.equals("root")){if(row.key.equals("now")){player.showPlaylist(true);return;}page=row.key;render();return;}
        if(page.equals("music")){page=row.key;render();return;}
        if(page.equals("artist")||page.equals("album")||page.equals("genre")){group=row.key;page="group:"+page;render();return;}
        if(page.equals("playlists")){final String id=row.key;worker.execute(()->{try(MediaPlaylistStore store=new MediaPlaylistStore(context)){WmMediaQueue.Entry[] entries=store.load(id);handler.post(()->{if(alive()){player.setPlaylist(entries,0,true);player.showLibrary(false);}});}catch(Exception error){handler.post(()->{if(alive())notice.accept("재생 목록을 열 수 없습니다.");});}});return;}
        for(MediaPlaylistStore.Source source:sources)if(source.entry.uri.toString().equals(row.key)){player.open(source.entry.uri,source.entry.title);return;}
    }
    boolean canDelete(){WmMediaLibraryView.Row row=player.library().selected();return row!=null&&(page.equals("playlists")||page.equals("all")||page.equals("videos")||page.startsWith("group:"));}
    void deleteSelected(){if(!canDelete())return;String key=player.library().selected().key;boolean playlist=page.equals("playlists");worker.execute(()->{try(MediaPlaylistStore store=new MediaPlaylistStore(context)){if(playlist)store.delete(key);else store.forget(key);handler.post(()->{if(alive())refresh();});}catch(Exception error){handler.post(()->{if(alive())notice.accept("항목을 제거할 수 없습니다.");});}});}
    void queueSelected(){if(page.equals("playlists")){WmMediaLibraryView.Row selected=player.library().selected();if(selected==null)return;worker.execute(()->{try(MediaPlaylistStore store=new MediaPlaylistStore(context)){WmMediaQueue.Entry[] entries=store.load(selected.key);handler.post(()->{if(alive())player.enqueue(entries);});}catch(Exception error){handler.post(()->{if(alive())notice.accept("재생 목록을 열 수 없습니다.");});}});}else{WmMediaLibraryView.Row row=player.library().selected();if(row!=null)for(MediaPlaylistStore.Source source:sources)if(source.entry.uri.toString().equals(row.key)){player.enqueue(new WmMediaQueue.Entry[]{source.entry});return;}}}
    private static WmMediaLibraryView.Row row(String key,String title,int icon,boolean authored){return new WmMediaLibraryView.Row(key,title,icon,authored);}
    private String field(MediaPlaylistStore.Source source,String kind){return kind.equals("artist")?source.artist:kind.equals("album")?source.album:source.genre;}
    private void render(){
        ArrayList<WmMediaLibraryView.Row> rows=new ArrayList<>();String title="라이브러리";int icon=0;
        if(page.equals("root")){rows.add(row("music","내 음악",1,true));rows.add(row("videos","내 비디오",2,true));rows.add(row("tv","내 TV",3,true));rows.add(row("playlists","내 재생 목록",4,true));rows.add(row("now","지금 재생",5,true));}
        else if(page.equals("music")){title="내 음악";icon=1;rows.add(row("all","모든 음악",1,true));rows.add(row("artist","음악가",6,true));rows.add(row("album","앨범",7,true));rows.add(row("genre","장르",8,true));}
        else if(page.equals("playlists")){title="내 재생 목록";icon=4;for(MediaPlaylistStore.Item item:playlists)rows.add(row(item.id,item.name,4,false));}
        else if(page.equals("tv")){title="내 TV";icon=3;/* Native recorded-TV/DRM sources are not indexed by this content-picker library. */}
        else if(page.equals("artist")||page.equals("album")||page.equals("genre")){title=page.equals("artist")?"음악가":page.equals("album")?"앨범":"장르";icon=page.equals("artist")?6:page.equals("album")?7:8;java.util.TreeSet<String> groups=new java.util.TreeSet<>();for(MediaPlaylistStore.Source source:sources)if(source.mime.startsWith("audio/"))groups.add(field(source,page));for(String value:groups)rows.add(row(value.isEmpty()?"\u0000":value,value.isEmpty()?"알 수 없음":value,icon,value.isEmpty()));}
        else {boolean video=page.equals("videos");title=video?"내 비디오":"모든 음악";icon=video?2:1;if(page.startsWith("group:")){title=group.equals("\u0000")?"알 수 없음":group;}
            for(MediaPlaylistStore.Source source:sources){if(!(video?source.mime.startsWith("video/"):source.mime.startsWith("audio/")))continue;if(page.startsWith("group:")&&!field(source,page.substring(6)).equals(group.equals("\u0000")?"":group))continue;rows.add(row(source.entry.uri.toString(),source.entry.title,icon,false));}
        }
        player.library().setPage(title,icon,rows.toArray(new WmMediaLibraryView.Row[0]),!page.startsWith("group:")||group.equals("\u0000"));
    }
}
