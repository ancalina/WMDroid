package me.ancal.wmdroid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

/** Release checks run on a worker, never the UI thread. */
final class LauncherUpdates {
    private static final String ENDPOINT="https://api.github.com/repos/ancalina/WMDroid/releases/latest";
    private static final String RELEASES="https://github.com/ancalina/WMDroid/releases/tag/";
    private static final int LIMIT=256*1024;
    private static final Pattern VERSION=Pattern.compile("v?[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}");
    private LauncherUpdates(){}
    static final long CHECK_INTERVAL=60*60*1000L;
    static boolean due(long now,long last){return last<=0||now<last||now-last>=CHECK_INTERVAL;}
    static boolean shouldNotify(Result result,String notified){return result!=null&&result.newer&&!result.version.equals(notified);}

    static final class Result {
        final String version,url;
        final boolean newer;
        Result(String tag,boolean newer){version=tag.startsWith("v")?tag.substring(1):tag;url=RELEASES+tag;this.newer=newer;}
    }

    static Result check(String installedVersion)throws IOException {
        components(installedVersion);
        HttpURLConnection connection=(HttpURLConnection)new URL(ENDPOINT).openConnection();
        connection.setConnectTimeout(8000);connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept","application/vnd.github+json");
        connection.setRequestProperty("User-Agent","WMDroid-release-check");
        try{
            int status=connection.getResponseCode();
            if(status==404)return null;
            if(status!=200)throw new IOException("Release endpoint HTTP "+status);
            if(connection.getContentLength()>LIMIT)throw new IOException("Release response too large");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream input=connection.getInputStream()){
                byte[] buffer=new byte[4096];int count;
                while((count=input.read(buffer))!=-1){
                    if(bytes.size()>LIMIT-count)throw new IOException("Release response too large");
                    bytes.write(buffer,0,count);
                }
            }
            try{
                JSONObject release=new JSONObject(new String(bytes.toByteArray(),"UTF-8"));
                Object raw=release.get("tag_name");
                if(!(raw instanceof String))throw new IOException("Invalid release version");
                String tag=(String)raw;
                return new Result(tag,compareVersions(tag,installedVersion)>0);
            }catch(JSONException invalid){throw new IOException("Invalid release response",invalid);}
        }finally{
            InputStream error=connection.getErrorStream();
            if(error!=null)try{error.close();}catch(IOException ignored){}
            connection.disconnect();
        }
    }

    static int compareVersions(String left,String right)throws IOException {
        long[] a=components(left),b=components(right);
        for(int i=0;i<3;i++){if(a[i]<b[i])return -1;if(a[i]>b[i])return 1;}
        return 0;
    }
    private static long[] components(String value)throws IOException {
        if(value==null||!VERSION.matcher(value).matches())throw new IOException("Invalid release version");
        if(value.startsWith("v"))value=value.substring(1);
        String[] parts=value.split("\\.");
        return new long[]{Long.parseLong(parts[0]),Long.parseLong(parts[1]),Long.parseLong(parts[2])};
    }
}
