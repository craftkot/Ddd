package com.ehtid.p2pchat;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import java.nio.ByteBuffer;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Discord-style searchable grid, per-source tabs, favorites and paging. */
public final class GifPicker {
    public interface OnSelected { void select(GifSources.Item item); }
    private static final ExecutorService IO=Executors.newFixedThreadPool(3);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Activity activity;
    private final OnSelected callback;
    private final int violet=0xff8b5cf6, surface=0xff211a32, foreground=0xfff5f1ff;
    private AlertDialog dialog;
    private EditText search;
    private GridLayout tiles;
    private ScrollView galleryScroll;
    private final List<PosterSlot> posterSlots=new ArrayList<>();
    private static final class PosterSlot {
        final String url;
        final ImageView target;
        boolean started;
        PosterSlot(String url,ImageView target){this.url=url;this.target=target;}
    }
    private TextView label;
    private TextView more;
    private LinearLayout toolbar;
    private String source="Commons", query="";
    private int page,generation;
    private Runnable debounce;
    private boolean loading;
    private final List<GifSources.Item> results=new ArrayList<>();
    public GifPicker(Activity activity,OnSelected callback){
        this.activity=activity;this.callback=callback;
    }
    private TextView text(String value,int size){
        TextView view=new TextView(activity);
        view.setText(value);view.setTextSize(size);
        view.setTextColor(foreground);view.setGravity(Gravity.CENTER);
        view.setPadding(dp(9),dp(11),dp(9),dp(11));
        return view;
    }
    private int dp(int v){
        return (int)(v*activity.getResources().getDisplayMetrics().density+0.5f);
    }
    public void show(){
        LinearLayout root=new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10),dp(8),dp(10),dp(5));
        root.setBackgroundColor(0xff100d1b);
        search=new EditText(activity);
        search.setSingleLine(true);search.setTextSize(16);
        search.setTextColor(foreground);search.setHintTextColor(0xffb0a5c2);
        search.setHint("Поиск GIF: эмоции, персонажи, мемы…");
        search.setPadding(dp(12),dp(9),dp(12),dp(9));
        root.addView(search,new LinearLayout.LayoutParams(-1,dp(52)));
        toolbar=new LinearLayout(activity);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(toolbar);
        addTab("Commons");addTab("GIPHY");addTab("KLIPY");addTab("★");
        LinearLayout categories=new LinearLayout(activity);
        String[] presets={"😂","❤️","😭","👍","💀"};
        String[] queries={"laugh","love","cry","thumbs up","skull"};
        for(int i=0;i<presets.length;i++){
            final String q=queries[i];
            TextView chip=text(presets[i],18);
            categories.addView(chip,new LinearLayout.LayoutParams(0,dp(48),1));
            chip.setOnClickListener(v->search.setText(q));
        }
        root.addView(categories);
        label=text("Выбери GIF для отправки",12);
        label.setTextColor(0xffb0a5c2);
        root.addView(label);
        galleryScroll=new ScrollView(activity);
        galleryScroll.setFillViewport(false);
        tiles=new GridLayout(activity);
        tiles.setColumnCount(2);
        galleryScroll.addView(tiles);
        root.addView(galleryScroll,new LinearLayout.LayoutParams(-1,0,1));
        galleryScroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->checkPosters());
        more=text("Ещё результаты",14);
        more.setBackgroundColor(surface);
        root.addView(more,new LinearLayout.LayoutParams(-1,dp(46)));
        more.setOnClickListener(v->load(false));
        TextView note=text("Commons без ключа · GIPHY/KLIPY через собственный API-ключ",11);
        note.setTextColor(0xffaaa0bb);
        root.addView(note);
        note.setOnClickListener(v->configureSources());
        dialog=new AlertDialog.Builder(activity)
                .setTitle("GIF-поиск EHTID")
                .setView(root)
                .setNegativeButton("Закрыть",null)
                .setNeutralButton("Источники",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(w->configureSources()));
        dialog.show();
        dialog.getWindow().setLayout(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*0.86));
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                if(debounce!=null)main.removeCallbacks(debounce);
                debounce=()->load(true);
                main.postDelayed(debounce,350);
            }
            @Override public void afterTextChanged(Editable e){}
        });
        search.setText("happy");
    }
    private void addTab(String name){
        TextView tab=text(name,13);
        tab.setBackgroundColor(name.equals(source)?violet:surface);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);
        p.setMargins(dp(1),0,dp(1),0);
        toolbar.addView(tab,p);
        tab.setOnClickListener(v->{
            source=name;
            for(int i=0;i<toolbar.getChildCount();i++)
                toolbar.getChildAt(i).setBackgroundColor(
                        toolbar.getChildAt(i)==tab?violet:surface);
            load(true);
        });
    }
    private void configureSources(){
        LinearLayout box=new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),0,dp(20),0);
        EditText giphy=new EditText(activity),klipy=new EditText(activity);
        giphy.setSingleLine(true);klipy.setSingleLine(true);
        giphy.setHint("GIPHY API key");klipy.setHint("KLIPY API key");
        giphy.setText(GifSources.key(activity,"GIPHY"));
        klipy.setText(GifSources.key(activity,"KLIPY"));
        box.addView(giphy);box.addView(klipy);
        new AlertDialog.Builder(activity).setTitle("GIF-каталоги")
                .setMessage("Commons доступен сразу. Для GIPHY и KLIPY нужны ключи из их кабинетов разработчика. Ключи остаются на устройстве. Tenor API закрыт.")
                .setView(box)
                .setPositiveButton("Сохранить",(d,w)->{
                    GifSources.setKey(activity,"GIPHY",giphy.getText().toString());
                    GifSources.setKey(activity,"KLIPY",klipy.getText().toString());
                    load(true);
                }).setNegativeButton("Отмена",null).show();
    }
    private SharedPreferences pref(){return activity.getSharedPreferences("gif_favorites",Context.MODE_PRIVATE);}
    private List<GifSources.Item> favorites(){
        List<GifSources.Item> list=new ArrayList<>();
        try{
            JSONArray data=new JSONArray(pref().getString("items","[]"));
            for(int i=0;i<data.length();i++){
                JSONObject item=data.optJSONObject(i);
                if(item!=null)list.add(GifSources.Item.fromJson(item));
            }
        }catch(Exception ignored){}
        return list;
    }
    private void toggleFavorite(GifSources.Item item){
        List<GifSources.Item> list=favorites();
        boolean exists=false;
        for(int i=0;i<list.size();i++){
            if(item.original.equals(list.get(i).original)){
                list.remove(i);exists=true;break;
            }
        }
        if(!exists)list.add(0,item);
        JSONArray array=new JSONArray();
        for(int i=0;i<Math.min(80,list.size());i++)array.put(list.get(i).json());
        pref().edit().putString("items",array.toString()).apply();
        if(source.equals("★"))load(true);
        else label.setText(exists?"Убрано из избранного":"Добавлено в избранное");
    }
    private void load(boolean first){
        if(dialog==null||!dialog.isShowing())return;
        if(first){
            ++generation;page=0;results.clear();tiles.removeAllViews();
            posterSlots.clear();
            galleryScroll.scrollTo(0,0);
            loading=false;
        }
        if(loading)return;
        int requestGeneration=generation;
        if("★".equals(source)){
            results.clear();results.addAll(favorites());
            tiles.removeAllViews();showResults(results);
            label.setText("Избранное · "+results.size());
            more.setVisibility(View.GONE);
            return;
        }
        loading=true;
        String current=source, term=search.getText().toString();
        int offset=page;
        label.setText("Ищем «"+term+"» в "+current+"…");
        more.setEnabled(false);
        IO.execute(()->{
            List<GifSources.Item> items=null;
            String error=null;
            try{items=GifSources.search(activity,current,term,offset);}
            catch(Exception e){error=e.getMessage();}
            final List<GifSources.Item> found=items;
            final String failure=error;
            main.post(()->{
                if(requestGeneration!=generation || dialog==null||!dialog.isShowing())return;
                loading=false;more.setEnabled(true);
                if(failure!=null){
                    label.setText("Ошибка "+current+": "+failure);
                    more.setVisibility(View.GONE);return;
                }
                if(found==null || found.isEmpty()){
                    label.setText(results.isEmpty()?"Ничего не найдено · попробуй другое слово":"Результаты закончились");
                    more.setVisibility(View.GONE);return;
                }
                page++;
                results.addAll(found);
                showResults(found);
                label.setText(current+" · найдено "+results.size());
                more.setVisibility(View.VISIBLE);
            });
        });
    }
    private void showResults(List<GifSources.Item> batch){
        int width=(activity.getResources().getDisplayMetrics().widthPixels-dp(54))/2;
        for(GifSources.Item item:batch){
            LinearLayout card=new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(surface);
            GridLayout.LayoutParams params=new GridLayout.LayoutParams();
            params.width=width;params.height=dp(155);
            params.setMargins(dp(3),dp(3),dp(3),dp(3));
            tiles.addView(card,params);
            ImageView preview=new ImageView(activity);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setImageResource(R.drawable.ic_ehtid_image);
            card.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout actions=new LinearLayout(activity);
            TextView title=text(item.provider+" · Отправить",11);
            title.setSingleLine(true);
            actions.addView(title,new LinearLayout.LayoutParams(0,dp(37),1));
            TextView star=text("☆",17);
            actions.addView(star,new LinearLayout.LayoutParams(dp(40),dp(37)));
            card.addView(actions);
            preview.setOnClickListener(v->select(item));
            title.setOnClickListener(v->select(item));
            star.setOnClickListener(v->toggleFavorite(item));
            posterSlots.add(new PosterSlot(item.preview,preview));
        }
        tiles.post(this::checkPosters);
    }
    private void checkPosters(){
        Rect rect=new Rect();
        for(PosterSlot slot:posterSlots){
            if(!slot.target.isAttachedToWindow())continue;
            boolean visible=slot.target.getGlobalVisibleRect(rect) &&
                    rect.height()>32 && rect.width()>32;
            Drawable d=slot.target.getDrawable();
            if(d instanceof Animatable){
                if(visible)((Animatable)d).start();
                else ((Animatable)d).stop();
            }
            if(!visible || slot.started)continue;
            slot.started=true;
            loadPoster(slot.url,slot.target);
        }
    }
    private void select(GifSources.Item item) {
        if(dialog!=null)dialog.dismiss();
        callback.select(item);
    }
    private void loadPoster(String url,ImageView target){
        if(!GifSources.safeMediaUrl(url))return;
        IO.execute(()->{
            try{
                byte[] bytes=GifSources.download(url,1024*1024);
                main.post(()->{
                    if(!target.isAttachedToWindow())return;
                    try {
                        if(Build.VERSION.SDK_INT>=28){
                            Drawable drawable=ImageDecoder.decodeDrawable(
                                ImageDecoder.createSource(ByteBuffer.wrap(bytes)),
                                (decoder,info,source)->{
                                    int w=info.getSize().getWidth(),h=info.getSize().getHeight();
                                    if(w<=0 || h<=0 || w>4096 || h>4096)
                                        throw new IllegalArgumentException("Unsafe GIF dimensions");
                                    float scale=Math.min(1f,240f/Math.max(w,h));
                                    decoder.setTargetSize(Math.max(1,(int)(w*scale)),
                                            Math.max(1,(int)(h*scale)));
                                });
                            target.setImageDrawable(drawable);
                            if(drawable instanceof Animatable)((Animatable)drawable).start();
                        }else{
                            Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                            if(bitmap!=null)target.setImageBitmap(bitmap);
                        }
                    }catch(Exception ignored){}
                });
            }catch(Exception ignored){}
        });
    }
}
