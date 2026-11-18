package com.ehtid.p2pchat;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded lazy loading: downloads only GIFs visible in the chat feed. */
public final class GifPreviews {
    public interface Callback {void loaded(File file);}
    private final ExecutorService workers=Executors.newFixedThreadPool(2);
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean closed;
    public void load(Context context,String room,JSONObject meta,Callback cb) {
        if(closed || meta.optInt("size")>3*1024*1024)return;
        workers.execute(()->{
            File file=null;
            try{file=MediaTransfer.download(context,room,meta,null);}
            catch(Exception ignored){}
            File result=file;
            main.post(()->{if(!closed)cb.loaded(result);});
        });
    }
    public void close(){closed=true;workers.shutdownNow();}
}