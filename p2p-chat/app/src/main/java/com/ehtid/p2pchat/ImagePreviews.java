package com.ehtid.p2pchat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Room-scoped memory-cached previews. Never decodes network data on the UI thread.
 * Caller requests only photos visible in ScrollView; encrypted originals are
 * downloaded through MediaTransfer and stored in private application files.
 */
public final class ImagePreviews {
    public interface Callback { void onLoaded(Bitmap image,String error); }
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService workers=Executors.newFixedThreadPool(2);
    private final Map<String,List<Callback>> pending=new HashMap<>();
    private boolean closed;
    private final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(12*1024*1024){
        @Override protected int sizeOf(String key,Bitmap image){
            return image.getByteCount();
        }
    };

    public void load(Context context,String room,JSONObject meta,Callback cb){
        if(closed)return;
        String key=RoomAccess.topic(room)+"/"+meta.optString("id");
        Bitmap cached=memory.get(key);
        if(cached!=null){cb.onLoaded(cached,null);return;}
        synchronized(pending){
            List<Callback> callbacks=pending.get(key);
            if(callbacks!=null){callbacks.add(cb);return;}
            callbacks=new ArrayList<>();
            callbacks.add(cb);
            pending.put(key,callbacks);
        }
        workers.execute(()->{
            Bitmap bitmap=null;
            String error=null;
            try{
                File local=MediaTransfer.download(context,room,meta,null);
                bitmap=decode(local,640,500);
            }catch(Exception ex){
                error=ex.getMessage()==null?"Ошибка фото":ex.getMessage();
            }
            final Bitmap result=bitmap;
            final String failure=error;
            main.post(()->{
                if(result!=null&&!closed)memory.put(key,result);
                List<Callback> listeners;
                synchronized(pending){listeners=pending.remove(key);}
                if(closed||listeners==null)return;
                for(Callback c:listeners)c.onLoaded(result,failure);
            });
        });
    }

    /** Hard pixel bounds defend against oversized/damaged image headers. */
    public static Bitmap decode(File file,int maxWidth,int maxHeight) throws IOException{
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0||
                bounds.outWidth>40000||bounds.outHeight>40000)
            throw new IOException("Неподдерживаемое изображение");
        int sample=1;
        while(bounds.outWidth/sample>maxWidth||bounds.outHeight/sample>maxHeight)
            sample*=2;
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inSampleSize=sample;
        Bitmap decoded=BitmapFactory.decodeFile(file.getAbsolutePath(),opts);
        if(decoded==null)throw new IOException("Не удалось прочитать изображение");
        return decoded;
    }
    public void close(){
        closed=true;
        workers.shutdownNow();
        synchronized(pending){pending.clear();}
        memory.evictAll();
    }
}
