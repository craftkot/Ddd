package com.ehtid.p2pchat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.os.SystemClock;
import android.view.View;
import java.io.File;

/** Inline animated GIF on Android 7+, avoids WebView and remote HTML execution. */
public final class GifAnimationView extends View {
    private Movie movie;
    private long started;
    private boolean playing;
    public GifAnimationView(Context context){super(context);}
    public boolean open(File gif){
        if(gif==null||!gif.isFile())return false;
        Movie decoded=Movie.decodeFile(gif.getAbsolutePath());
        if(decoded==null||decoded.width()<1||decoded.height()<1||
                decoded.width()>4096||decoded.height()>4096)return false;
        movie=decoded;started=0;
        playing=true;invalidate();
        return true;
    }
    public void stop(){playing=false;movie=null;invalidate();}
    @Override protected void onDetachedFromWindow(){
        playing=false;super.onDetachedFromWindow();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        canvas.drawColor(Color.TRANSPARENT);
        Movie item=movie;
        if(item==null)return;
        int duration=item.duration()>0?item.duration():1000;
        long uptime=SystemClock.uptimeMillis();
        if(started==0)started=uptime;
        item.setTime((int)((uptime-started)%duration));
        float scale=Math.min((float)getWidth()/item.width(),(float)getHeight()/item.height());
        if(scale<=0)return;
        float left=(getWidth()-item.width()*scale)/2f;
        float top=(getHeight()-item.height()*scale)/2f;
        int save=canvas.save();
        canvas.translate(left,top);
        canvas.scale(scale,scale);
        item.draw(canvas,0,0);
        canvas.restoreToCount(save);
        if(playing && isAttachedToWindow())postInvalidateDelayed(65);
    }
}