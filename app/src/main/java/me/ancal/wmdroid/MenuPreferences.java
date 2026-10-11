package me.ancal.wmdroid;

import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Stable IDs and ordered folders. Existing v1 string arrays remain readable. */
final class MenuPreferences {
    static final String[] DEFAULT={"today","calendar","contacts","notes","messages","phone","browser","media"};
    static List<String> read(SharedPreferences prefs){return read(prefs,"");}
    static List<String> read(SharedPreferences prefs,String parent){
        List<String> result=new ArrayList<>();if(parent==null)return result;String raw=parent.isEmpty()?prefs.getString("start_items",null):null;
        if(parent.isEmpty()&&raw==null){Collections.addAll(result,DEFAULT);return result;}
        try{JSONObject folder=parent.isEmpty()?null:folder(prefs,parent);JSONArray a=parent.isEmpty()?new JSONArray(raw):folder==null?null:folder.optJSONArray("items");
            result.addAll(items(a,parent));
        }catch(JSONException invalid){if(parent.isEmpty())Collections.addAll(result,DEFAULT);}
        return result;
    }
    static JSONObject folders(SharedPreferences prefs){try{return new JSONObject(prefs.getString("start_folders","{}"));}catch(JSONException invalid){return new JSONObject();}}
    static JSONObject folder(SharedPreferences prefs,String id){return folders(prefs).optJSONObject(id);}
    static boolean isFolder(String id){return id!=null&&id.startsWith("folder:");}
    static void save(SharedPreferences prefs,List<String> rows){save(prefs,"",rows);}
    static void save(SharedPreferences prefs,String parent,List<String> rows){
        if(parent.isEmpty()){prefs.edit().putString("start_items",new JSONArray(rows).toString()).apply();return;}
        JSONObject all=folders(prefs),folder=all.optJSONObject(parent);if(folder==null)return;
        try{folder.put("items",new JSONArray(rows));prefs.edit().putString("start_folders",all.toString()).apply();}catch(JSONException impossible){throw new IllegalStateException(impossible);}
    }
    static String addFolder(SharedPreferences prefs,String parent,String title,String icon){
        String id="folder:"+UUID.randomUUID().toString();JSONObject all=folders(prefs),folder=new JSONObject();
        try{folder.put("title",title).put("icon",icon).put("items",new JSONArray());all.put(id,folder);prefs.edit().putString("start_folders",all.toString()).apply();}
        catch(JSONException impossible){throw new IllegalStateException(impossible);}
        List<String> rows=read(prefs,parent);rows.add(id);save(prefs,parent,rows);return id;
    }
    static void updateFolder(SharedPreferences prefs,String id,String title,String icon){JSONObject all=folders(prefs),f=all.optJSONObject(id);if(f==null)return;
        try{if(title!=null&&!title.trim().isEmpty())f.put("title",title);if(icon!=null)f.put("icon",icon);prefs.edit().putString("start_folders",all.toString()).apply();}catch(JSONException impossible){throw new IllegalStateException(impossible);}}
    static List<String> destinations(SharedPreferences prefs,String sourceParent,String id){return destinations(read(prefs),folders(prefs),sourceParent,id);}
    private static List<String> destinations(List<String> root,JSONObject all,String sourceParent,String id){
        List<String> result=new ArrayList<>();Set<String> excluded=new HashSet<>();
        if(isFolder(id))excluded.addAll(reachable(Arrays.asList(id),all));
        if(!"".equals(sourceParent))result.add("");
        for(String folder:reachable(root,all))if(!folder.equals(sourceParent)&&!excluded.contains(folder))result.add(folder);
        return result;
    }
    private static List<String> reachable(List<String> initial,JSONObject all){
        List<String> result=new ArrayList<>();Set<String> visited=new HashSet<>();ArrayDeque<String> pending=new ArrayDeque<>();
        for(int i=initial.size()-1;i>=0;i--)pending.push(initial.get(i));
        while(!pending.isEmpty()){
            String id=pending.pop();JSONObject folder=all.optJSONObject(id);
            if(!isFolder(id)||folder==null||!visited.add(id))continue;
            result.add(id);List<String> children=items(folder.optJSONArray("items"),id);
            for(int i=children.size()-1;i>=0;i--)pending.push(children.get(i));
        }
        return result;
    }
    private static List<String> items(JSONArray array,String parent){
        List<String> result=new ArrayList<>();if(array!=null)for(int i=0;i<array.length();i++){Object value=array.opt(i);if(!(value instanceof String))continue;String id=(String)value;if(!id.isEmpty()&&!id.equals(parent)&&!result.contains(id))result.add(id);}return result;
    }
    static boolean move(SharedPreferences prefs,String sourceParent,String id,String destination){
        if(sourceParent==null||destination==null||id==null||id.isEmpty()||sourceParent.equals(destination))return false;
        List<String> root=read(prefs);JSONObject all=folders(prefs),source=sourceParent.isEmpty()?null:all.optJSONObject(sourceParent),target=destination.isEmpty()?null:all.optJSONObject(destination);
        if((!sourceParent.isEmpty()&&source==null)||(!destination.isEmpty()&&target==null)||(isFolder(id)&&all.optJSONObject(id)==null))return false;
        List<String> from=sourceParent.isEmpty()?root:items(source.optJSONArray("items"),sourceParent);
        if(!from.contains(id)||!destinations(root,all,sourceParent,id).contains(destination))return false;
        List<String> to=destination.isEmpty()?root:items(target.optJSONArray("items"),destination);
        from.remove(id);to.remove(id);to.add(id);
        try{
            if(source!=null)source.put("items",new JSONArray(from));if(target!=null)target.put("items",new JSONArray(to));
            prefs.edit().putString("start_items",new JSONArray(root).toString()).putString("start_folders",all.toString()).apply();return true;
        }catch(JSONException impossible){throw new IllegalStateException(impossible);}
    }
}
