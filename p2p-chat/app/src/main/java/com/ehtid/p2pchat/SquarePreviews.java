package com.ehtid.p2pchat;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** One-at-a-time, only-on-screen previews of encrypted square video messages. */
public final class SquarePreviews {
    public interface Callback{void onLoaded(Bitmap bitmap);}
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<String,List<Callback>> pending=new HashMap<>();
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(8*1024*1024){
        @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}
    };
    private volatile boolean closed;
    public void load(Context context,String room,JSONObject media,Callback callback){
        if(closed)return;
        String key=RoomAccess.topic(room)+"/"+media.optString("id");
        Bitmap cached=cache.get(key);
        if(cached!=null){callback.onLoaded(cached);return;}
        synchronized(pending){
            List<Callback> callbacks=pending.get(key);
            if(callbacks!=null){callbacks.add(callback);return;}
            callbacks=new ArrayList<>();
            callbacks.add(callback);
            pending.put(key,callbacks);
        }
        worker.execute(()->{
            Bitmap result=null;
            try{
                if(!"square".equals(media.optString("kind")) ||
                        media.optInt("size")>3*1024*1024)
                    throw new IllegalArgumentException("Large video");
                File file=MediaTransfer.download(context,room,media,null);
                MediaMetadataRetriever retriever=new MediaMetadataRetriever();
                try{
                    retriever.setDataSource(file.getAbsolutePath());
                    if(Build.VERSION.SDK_INT>=27){
                        result=retriever.getScaledFrameAtTime(0,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,320,320);
                    }else{
                        Bitmap original=retriever.getFrameAtTime(0,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                        if(original!=null){
                            result=Bitmap.createScaledBitmap(original,320,320,true);
                            if(result!=original)original.recycle();
                        }
                    }
                }finally{retriever.release();}
            }catch(Exception ignored){}
            final Bitmap bitmap=result;
            main.post(()->{
                List<Callback> list;
                synchronized(pending){list=pending.remove(key);}
                if(closed || list==null)return;
                if(bitmap!=null)cache.put(key,bitmap);
                for(Callback cb:list)cb.onLoaded(bitmap);
            });
        });
    }
    public void close(){
        closed=true;worker.shutdownNow();
        synchronized(pending){pending.clear();}
        cache.evictAll();
    }
}
