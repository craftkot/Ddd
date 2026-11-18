package com.ehtid.p2pchat;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Provider adapters. No scraping, no shared credentials; Commons works without keys. */
public final class GifSources {
    private GifSources() {}
    public static final int MAX_GIF=MediaTransfer.MAX_FILE_BYTES;
    public static final class Item {
        public final String provider,title,preview,original,page,credit;
        public Item(String provider,String title,String preview,String original,String page,String credit) {
            this.provider=provider;this.title=title;this.preview=preview;
            this.original=original;this.page=page;this.credit=credit;
        }
        public JSONObject json(){
            JSONObject obj=new JSONObject();
            try {
                obj.put("provider",provider);obj.put("title",title);
                obj.put("preview",preview);obj.put("original",original);
                obj.put("page",page);obj.put("credit",credit);
            }catch(Exception ignored){}
            return obj;
        }
        public static Item fromJson(JSONObject j) {
            return new Item(j.optString("provider"),j.optString("title"),
                    j.optString("preview"),j.optString("original"),
                    j.optString("page"),j.optString("credit"));
        }
    }
    public static String key(Context context,String source) {
        return context.getSharedPreferences("gif_sources",Context.MODE_PRIVATE)
                .getString(source+"_key","").trim();
    }
    public static void setKey(Context context,String source,String key) {
        // Keys are credentials belonging to the end user. Never embed them into public APK.
        context.getSharedPreferences("gif_sources",Context.MODE_PRIVATE)
                .edit().putString(source+"_key",key==null?"":key.trim()).apply();
    }
    public static List<Item> search(Context context,String source,String text,int page) throws Exception {
        String query=text==null?"":text.trim();
        if(query.length()>60)query=query.substring(0,60);
        if(query.isEmpty())query="happy";
        if("GIPHY".equals(source))return giphy(context,query,page);
        if("KLIPY".equals(source))return klipy(context,query,page);
        return commons(query,page);
    }
    private static List<Item> commons(String q,int page) throws Exception {
        String url="https://commons.wikimedia.org/w/api.php?action=query&format=json"
                +"&generator=search&gsrnamespace=6&gsrlimit=28&gsroffset="+(page*28)
                +"&gsrsearch="+enc(q+" filemime:image/gif")
                +"&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata";
        JSONObject root=new JSONObject(text(url));
        JSONObject pages=root.optJSONObject("query");
        List<Item> items=new ArrayList<>();
        if(pages==null)return items;
        JSONObject entries=pages.optJSONObject("pages");
        if(entries==null)return items;
        JSONArray keys=entries.names();
        if(keys==null)return items;
        for(int i=0;i<keys.length();i++) {
            JSONObject entry=entries.optJSONObject(keys.optString(i));
            if(entry==null)continue;
            JSONArray info=entry.optJSONArray("imageinfo");
            JSONObject image=info==null?null:info.optJSONObject(0);
            if(image==null||image.optLong("size")>MAX_GIF||image.optLong("size")<30)continue;
            String original=image.optString("url");
            if(!safeMediaUrl(original))continue;
            String filename=entry.optString("title","GIF").replaceFirst("^File:","");
            String pageUrl=image.optString("descriptionurl");
            JSONObject meta=image.optJSONObject("extmetadata");
            String credit=meta!=null && meta.optJSONObject("LicenseShortName")!=null?
                    meta.optJSONObject("LicenseShortName").optString("value","Commons"):
                    "Wikimedia Commons";
            items.add(new Item("Commons",filename,original,original,pageUrl,credit));
        }
        return items;
    }
    private static List<Item> giphy(Context context,String q,int page) throws Exception {
        String key=key(context,"GIPHY");
        if(key.isEmpty())throw new IOException("Для GIPHY нужен API-ключ в настройках GIF");
        String url="https://api.giphy.com/v1/gifs/search?api_key="+enc(key)+
                "&q="+enc(q)+"&limit=24&offset="+(page*24)+"&rating=pg-13";
        JSONObject root=new JSONObject(text(url));
        JSONArray data=root.optJSONArray("data");
        List<Item> items=new ArrayList<>();
        if(data==null)return items;
        for(int i=0;i<data.length();i++){
            JSONObject gif=data.optJSONObject(i);
            if(gif==null)continue;
            JSONObject variants=gif.optJSONObject("images");
            if(variants==null)continue;
            JSONObject original=variants.optJSONObject("original");
            JSONObject down=variants.optJSONObject("fixed_width");
            if(original==null)continue;
            String full=original.optString("url");
            String preview=down==null?full:down.optString("url",full);
            if(!safeMediaUrl(full)||!safeMediaUrl(preview))continue;
            // Content may exceed 10 MiB; actual fetch is bounded before upload.
            items.add(new Item("GIPHY",gif.optString("title","GIF"),preview,
                    full,gif.optString("url"),"GIPHY"));
        }
        return items;
    }
    private static List<Item> klipy(Context context,String q,int page) throws Exception {
        String key=key(context,"KLIPY");
        if(key.isEmpty())throw new IOException("Для KLIPY нужен API-ключ в настройках GIF");
        // KLIPY partner v1 API, response variants may differ between API plans.
        String url="https://api.klipy.com/api/v1/"+enc(key)+"/gifs/search?"
                +"q="+enc(q)+"&per_page=24&page="+(page+1)+"&locale=ru_RU";
        JSONObject root=new JSONObject(text(url));
        JSONObject nested=root.optJSONObject("data");
        JSONArray data=nested==null?root.optJSONArray("data"):nested.optJSONArray("data");
        if(data==null && nested!=null)data=nested.optJSONArray("results");
        List<Item> items=new ArrayList<>();
        if(data==null)return items;
        for(int i=0;i<data.length();i++){
            JSONObject gif=data.optJSONObject(i);
            if(gif==null)continue;
            JSONObject files=gif.optJSONObject("file");
            if(files==null)files=gif.optJSONObject("files");
            JSONObject gifFile=files==null?null:files.optJSONObject("gif");
            if(gifFile==null)continue;
            String original=gifFile.optString("url");
            JSONObject previewFile=files.optJSONObject("gif_small");
            String preview=previewFile==null?original:previewFile.optString("url",original);
            if(!safeMediaUrl(original)||!safeMediaUrl(preview))continue;
            items.add(new Item("KLIPY",gif.optString("title","GIF"),preview,original,
                    gif.optString("url"),"KLIPY"));
        }
        return items;
    }
    public static boolean safeMediaUrl(String raw) {
        try{
            URL url=new URL(raw);
            if(!"https".equalsIgnoreCase(url.getProtocol()) || url.getUserInfo()!=null ||
                    url.getPort()!=-1)return false;
            String host=url.getHost().toLowerCase(Locale.ROOT);
            return host.equals("upload.wikimedia.org") ||
                    host.equals("static.klipy.com") ||
                    host.equals("media.giphy.com") ||
                    host.matches("media[0-9]+\\.giphy\\.com") ||
                    host.equals("i.giphy.com");
        }catch(Exception ignored){return false;}
    }
    private static boolean safeApiUrl(URL url) {
        String host=url.getHost().toLowerCase(Locale.ROOT);
        return "https".equals(url.getProtocol()) && url.getPort()==-1
                && url.getUserInfo()==null && (
                host.equals("commons.wikimedia.org") ||
                host.equals("api.giphy.com") || host.equals("api.klipy.com"));
    }
    private static String enc(String s) throws Exception {
        return java.net.URLEncoder.encode(s,"UTF-8");
    }
    private static String text(String url) throws Exception {
        URL address=new URL(url);
        if(!safeApiUrl(address))throw new IOException("Недоверенный GIF API");
        HttpURLConnection conn=(HttpURLConnection)address.openConnection();
        try {
            conn.setConnectTimeout(9000);conn.setReadTimeout(14000);
            conn.setRequestProperty("User-Agent","EHTID-Chat/1.6 (Android GIF picker)");
            conn.setRequestProperty("Accept","application/json");
            if(conn.getResponseCode()!=200)
                throw new IOException("GIF сервис: HTTP "+conn.getResponseCode());
            byte[] bytes=readLimited(conn.getInputStream(),1024*1024);
            return new String(bytes,StandardCharsets.UTF_8);
        }finally{conn.disconnect();}
    }
    public static byte[] download(String raw,int maxBytes) throws Exception {
        if(!safeMediaUrl(raw))throw new IOException("Этот GIF-хост не разрешён");
        HttpURLConnection conn=(HttpURLConnection)new URL(raw).openConnection();
        try {
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(10000);conn.setReadTimeout(24000);
            conn.setRequestProperty("User-Agent","EHTID-Chat/1.6");
            if(conn.getResponseCode()!=200)throw new IOException("GIF HTTP "+conn.getResponseCode());
            byte[] bytes=readLimited(conn.getInputStream(),maxBytes);
            if(bytes.length<6 || bytes[0]!='G' || bytes[1]!='I'||bytes[2]!='F' ||
                    bytes[3]!='8' || (bytes[4]!='7' && bytes[4]!='9') || bytes[5]!='a')
                throw new IOException("Источник вернул не GIF-анимацию");
            return bytes;
        }finally{conn.disconnect();}
    }
    private static byte[] readLimited(InputStream in,int max) throws Exception {
        try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[16384];int n;
            while((n=input.read(buf))!=-1){
                if(out.size()+n>max)throw new IOException("GIF превышает лимит размера");
                out.write(buf,0,n);
            }
            return out.toByteArray();
        }
    }
}
