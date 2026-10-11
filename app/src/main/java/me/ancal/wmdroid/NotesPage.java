package me.ancal.wmdroid;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.util.UUID;
import java.util.concurrent.Executor;
import me.ancal.wmdroid.sdk.WmNotesView;

/** Launcher storage adapter. SDK owns presentation; SQLite work stays off the UI thread. */
final class NotesPage extends FrameLayout {
    final WmNotesView client;
    private final Executor worker;
    private final Handler handler=new Handler();
    private final Runnable changed;
    private String id,title,draftFolder="",original="";
    private NotesStore.Folder[] folders=new NotesStore.Folder[0];
    private NotesStore.Entry[] snapshots=new NotesStore.Entry[0];
    private long revision;
    private boolean busy;
    private Runnable loaded;
    private Bundle pendingUi;
    private int generation;
    NotesPage(Context context,Typeface regular,Typeface bold,int selection,Executor worker,Runnable changed){
        super(context);this.worker=worker;this.changed=changed;client=new WmNotesView(context,regular,bold,selection,e->open(e.key));addView(client,new LayoutParams(-1,-1));client.editor().setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(NotesStore.MAX_BODY)});
    }
    void list(){if(busy)return;generation++;id=null;title=null;revision=0;original="";client.showList();changed.run();reload();}
    void reload(){reload(null,null);}
    private void reload(Runnable done,Runnable error){
        if(busy||client.isEditing())return;busy=true;final int ticket=++generation;client.setMessage(UiLanguage.t(getContext(),"불러오는 중…"));
        boolean accepted=execute(()->{NotesStore store=new NotesStore(getContext());try{NotesStore.Entry[] notes=store.list();NotesStore.Folder[] catalog=store.folders();WmNotesView.Entry[] entries=new WmNotesView.Entry[notes.length];for(int i=0;i<notes.length;i++)entries[i]=new WmNotesView.Entry(notes[i].id,notes[i].title,notes[i].modified,notes[i].size,"txt",notes[i].folder);
            handler.post(()->{if(ticket!=generation)return;busy=false;snapshots=notes;folders=catalog;client.setFolders(folderModels());client.setEntries(entries);client.setMessage(null);if(done!=null)done.run();if(loaded!=null){Runnable ready=loaded;loaded=null;ready.run();}});}catch(RuntimeException failure){failed(ticket,error);}finally{store.close();}});if(!accepted&&error!=null)error.run();
    }
    void fresh(){if(busy)return;if(client.isEditing()){save(this::fresh);return;}draftFolder=client.folderId()==null?"":client.folderId();begin(UUID.randomUUID().toString(),0,"",null,null);}
    private void begin(String key,long version,String body,String name,Bundle state){id=key;title=name;revision=version;original=body;client.edit(body);client.restoreInteractionState(state);changed.run();}
    private void open(String key){
        if(busy)return;busy=true;final int ticket=++generation;
        execute(()->{NotesStore store=new NotesStore(getContext());try{NotesStore.Entry note=store.get(key);handler.post(()->{if(ticket!=generation)return;busy=false;if(note==null){notice();reload();return;}draftFolder=note.folder;begin(note.id,note.revision,note.body,note.title,null);});}catch(RuntimeException failure){failed(ticket);}finally{store.close();}});
    }
    NotesStore.Entry snapshot(String key){for(NotesStore.Entry entry:snapshots)if(entry.id.equals(key))return entry;return null;}
    void rename(NotesStore.Entry expected,String name,Runnable done,Runnable error){rename(expected,name,expected.folder,done,error);}
    void rename(NotesStore.Entry expected,String name,String folder,Runnable done,Runnable error){change(expected,name,folder,false,done,error);}
    void delete(NotesStore.Entry expected,Runnable done){change(expected,null,null,true,done,null);}
    private void change(NotesStore.Entry expected,String name,String folder,boolean delete,Runnable done,Runnable error){
        if(busy||editing()||expected==null)return;busy=true;final int ticket=++generation;
        boolean accepted=execute(()->{NotesStore store=new NotesStore(getContext());try{if(delete)store.delete(expected.id,expected.revision);else store.rename(expected.id,expected.revision,name,folder);
            handler.post(()->{if(ticket!=generation)return;busy=false;if(done!=null)done.run();reload();});}catch(RuntimeException failure){failed(ticket,error);}finally{store.close();}});if(!accepted&&error!=null)error.run();
    }
    void whenLoaded(Runnable action){if(busy)loaded=action;else action.run();}
    void restoreList(Bundle state){whenLoaded(()->{if(state==null)return;Bundle safe=new Bundle(state);String id=safe.getString("folder");if(id!=null&&!id.isEmpty()&&!hasFolder(id))safe.remove("folder");client.restoreInteractionState(safe);});}
    boolean folderEmpty(String id){for(NotesStore.Entry entry:snapshots)if(entry.folder.equals(id))return false;return true;}
    WmNotesView.Folder[] folderModels(){WmNotesView.Folder[] models=new WmNotesView.Folder[folders.length];for(int i=0;i<models.length;i++)models[i]=new WmNotesView.Folder(folders[i].id,folders[i].name);return models;}
    private boolean hasFolder(String id){for(NotesStore.Folder folder:folders)if(folder.id.equals(id))return true;return false;}
    String[] folderIds(){String[] ids=new String[folders.length+1];ids[0]="";for(int i=0;i<folders.length;i++)ids[i+1]=folders[i].id;return ids;}
    String[] folderNames(){String[] names=new String[folders.length+1];names[0]=UiLanguage.t(getContext(),"없음");for(int i=0;i<folders.length;i++)names[i+1]=folders[i].name;return names;}
    void folderChange(int command,String id,String name,java.util.function.Consumer<NotesStore.Folder> done,Runnable error){
        if(busy||editing())return;busy=true;final int ticket=++generation;
        boolean accepted=execute(()->{NotesStore store=new NotesStore(getContext());try{NotesStore.Folder result=null;
            if(command==me.ancal.wmdroid.sdk.WmDocumentFolders.NEW){String base=UiLanguage.t(getContext(),"새 폴더");NotesStore.Folder[] known=store.folders();int suffix=0;String candidate=base;while(true){boolean exists=false;for(NotesStore.Folder f:known)if(f.name.equalsIgnoreCase(candidate)){exists=true;break;}if(!exists)break;candidate=base+(++suffix);}result=store.createFolder(candidate);}
            else if(command==me.ancal.wmdroid.sdk.WmDocumentFolders.RENAME)result=store.renameFolder(id,name);
            else if(command==me.ancal.wmdroid.sdk.WmDocumentFolders.DELETE)store.deleteFolder(id);else throw new IllegalArgumentException("Folder command required");
            final NotesStore.Folder changed=result;handler.post(()->{if(ticket!=generation)return;busy=false;reload(()->done.accept(changed),error);});
        }catch(RuntimeException failure){failed(ticket,error);}finally{store.close();}});if(!accepted&&error!=null)error.run();
    }
    boolean editing(){return client.isEditing();}
    boolean busy(){return busy;}
    @Override protected void onDetachedFromWindow(){for(int i=getChildCount()-1;i>=1;i--)if(getChildAt(i) instanceof me.ancal.wmdroid.sdk.WmMessageBox)removeViewAt(i);super.onDetachedFromWindow();}
    void cancel(){if(!busy)list();}
    /** Non-null completion leaves the editor only after durable save, or an unchanged draft. */
    void save(Runnable completion){
        if(busy||!editing())return;final String body=client.text();
        if(body.equals(original)){if(completion!=null){client.showList();completion.run();}return;}
        final String key=id,folder=draftFolder;final long version=revision;final int ticket=++generation;
        String first=body.trim();int newline=first.indexOf('\n');if(newline>=0)first=first.substring(0,newline);if(first.isEmpty())first=UiLanguage.t(getContext(),"메모");
        int end=Math.min(256,first.length());if(end>0&&end<first.length()&&Character.isHighSurrogate(first.charAt(end-1)))end--;final String savedTitle=version==0||title==null?first.substring(0,end):title;
        busy=true;client.editor().setEnabled(false);
        execute(()->{NotesStore store=new NotesStore(getContext());try{NotesStore.Entry note=store.save(key,version,savedTitle,body,folder);handler.post(()->{if(ticket!=generation)return;busy=false;client.editor().setEnabled(true);revision=note.revision;title=note.title;original=body;if(completion!=null&&client.text().equals(body)){client.showList();completion.run();}});}catch(RuntimeException failure){failed(ticket);}finally{store.close();}});
    }
    Bundle saveState(){if(!editing())return null;Bundle state=new Bundle();state.putString("id",id);state.putString("title",title);state.putString("folder",draftFolder);state.putLong("revision",revision);state.putString("body",client.text());state.putBoolean("dirty",!client.text().equals(original));Bundle ui=client.saveInteractionState();if(pendingUi!=null)ui.putString("folder",pendingUi.getString("folder"));state.putBundle("ui",ui);return state;}
    void restore(Bundle state){
        if(state==null){reload();return;}final String key=state.getString("id"),body=state.getString("body");final long version=state.getLong("revision",-1);
        if(key==null||key.isEmpty()||body==null||body.length()>NotesStore.MAX_BODY||version<0){notice();reload();return;}
        pendingUi=state.getBundle("ui");Bundle initial=pendingUi;if(initial!=null){initial=new Bundle(initial);initial.remove("folder");}draftFolder=state.getString("folder","");begin(key,version,body,state.getString("title"),initial);original=state.getBoolean("dirty")?null:body;busy=true;client.editor().setEnabled(false);final int ticket=++generation;
        // A save may have committed after the Activity captured its draft. Read back before retrying.
        execute(()->{NotesStore store=new NotesStore(getContext());try{NotesStore.Entry note=store.get(key);NotesStore.Folder[] catalog=store.folders();handler.post(()->{if(ticket!=generation)return;busy=false;folders=catalog;client.setFolders(folderModels());Bundle ui=state.getBundle("ui");if(ui!=null){ui=new Bundle(ui);String selected=ui.getString("folder");if(selected!=null&&!selected.isEmpty()&&!hasFolder(selected))ui.remove("folder");}draftFolder=note!=null?note.folder:state.getString("folder","");if(!draftFolder.isEmpty()&&!hasFolder(draftFolder))draftFolder="";client.restoreInteractionState(ui);pendingUi=null;client.editor().setEnabled(true);if(note!=null)title=note.title;if(note!=null&&note.body.equals(body)){revision=note.revision;title=note.title;original=body;}else if(note!=null&&note.revision!=version||note==null&&version!=0)notice();});}catch(RuntimeException failure){failed(ticket);}finally{store.close();}});
    }
    private boolean execute(Runnable action){try{worker.execute(action);return true;}catch(java.util.concurrent.RejectedExecutionException stopped){busy=false;client.editor().setEnabled(true);client.setMessage(null);notice();return false;}}
    private void failed(int ticket){failed(ticket,null);}
    private void failed(int ticket,Runnable error){handler.post(()->{if(ticket!=generation)return;busy=false;client.editor().setEnabled(true);client.setMessage(null);notice();if(error!=null)error.run();});}
    private void notice(){Toast.makeText(getContext(),UiLanguage.t(getContext(),"메모를 변경하지 못했습니다. 입력은 유지됩니다. 저장 공간과 다른 변경을 확인하세요."),Toast.LENGTH_LONG).show();}
}
