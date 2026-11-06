package com.ehtid.p2pchat;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Local-only room bookmarks. Removing a bookmark NEVER deletes message history,
 * cached media, queued messages or the room's encryption secret from storage.
 *
 * Room codes are credentials: do not log, upload or include them in telemetry.
 */
public final class RoomRegistry {
    private static final String PREFS="ehtid_rooms";
    private static final String KEY="rooms_v1";
    private static final int MAX_ROOMS=100;
    private final SharedPreferences store;

    public static final class Room {
        public final String code, name;
        public final long lastUsed;
        Room(String code,String name,long lastUsed){
            this.code=code;this.name=name;this.lastUsed=lastUsed;
        }
    }

    public RoomRegistry(Context context){
        this.store=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }

    public synchronized List<Room> list() {
        List<Room> out=new ArrayList<>();
        try {
            JSONArray all=new JSONArray(store.getString(KEY,"[]"));
            for(int i=0;i<all.length()&&out.size()<MAX_ROOMS;i++){
                JSONObject item=all.optJSONObject(i);
                if(item==null)continue;
                String code=item.optString("code","");
                if(!code.matches("[A-Z2-9]{16,32}"))continue;
                String name=cleanName(item.optString("name",""));
                if(name.isEmpty())name="Комната "+(i+1);
                if(find(out,code)<0)
                    out.add(new Room(code,name,Math.max(0,item.optLong("lastUsed",0))));
            }
        }catch(Exception ignored){}
        Collections.sort(out,(a,b)->Long.compare(b.lastUsed,a.lastUsed));
        return out;
    }

    private static int find(List<Room> rooms,String code) {
        for(int i=0;i<rooms.size();i++)if(rooms.get(i).code.equals(code))return i;
        return -1;
    }

    private synchronized void save(List<Room> rooms) {
        JSONArray arr=new JSONArray();
        for(Room room:rooms){
            JSONObject j=new JSONObject();
            try {
                j.put("code",room.code);
                j.put("name",room.name);
                j.put("lastUsed",room.lastUsed);
            }catch(Exception ignored){}
            arr.put(j);
        }
        // commit synchronously: a switch followed by process termination
        // must not lose the list of rooms.
        if(!store.edit().putString(KEY,arr.toString()).commit())
            throw new IllegalStateException("Не удалось сохранить список комнат");
    }

    public synchronized void remember(String code) {
        if(code==null||!code.matches("[A-Z2-9]{16,32}"))
            throw new IllegalArgumentException("Некорректный код комнаты");
        List<Room> current=list();
        int index=find(current,code);
        String name=index>=0?current.remove(index).name:"Комната "+(current.size()+1);
        if(index<0&&current.size()>=MAX_ROOMS)
            throw new IllegalStateException("Сохранено максимум "+MAX_ROOMS+" комнат");
        current.add(new Room(code,name,System.currentTimeMillis()));
        save(current);
    }

    public synchronized void rename(String code,String title) {
        List<Room> current=list();
        int index=find(current,code);
        if(index<0)throw new IllegalArgumentException("Комната не найдена");
        String name=cleanName(title);
        if(name.isEmpty())throw new IllegalArgumentException("Введите название комнаты");
        Room old=current.get(index);
        current.set(index,new Room(code,name,old.lastUsed));
        save(current);
    }

    public synchronized void forgetBookmarkOnly(String code) {
        List<Room> current=list();
        if(find(current,code)<0)return;
        current.removeIf(item->item.code.equals(code));
        save(current);
    }

    public static String cleanName(String name) {
        if(name==null)return "";
        String n=name.replaceAll("[\\p{Cntrl}]","").trim();
        return n.length()>36?n.substring(0,36):n;
    }
}
