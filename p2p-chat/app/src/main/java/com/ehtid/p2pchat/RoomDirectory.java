package com.ehtid.p2pchat;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Room index only. All encrypted chat history, pending messages and the
 * participant cache remain in their existing per-room SharedPreferences keys.
 * Forgetting a room here MUST NOT delete its messages or private media cache.
 */
public final class RoomDirectory {
    private static final String PREFS="ehtid_room_index";
    private static final String DATA="rooms_json";
    private static final String MIGRATED="migrated_legacy_rooms_v1";
    private static final int MAX_ROOMS=200;
    private final SharedPreferences index;
    private final SharedPreferences history;

    public static final class Room {
        public final String code, title;
        public final long lastUsed;
        Room(String code,String title,long lastUsed){
            this.code=code;this.title=title;this.lastUsed=lastUsed;
        }
        public String displayName() {
            return title.isEmpty() ? "Комната • "+code.substring(Math.max(0,code.length()-5)) : title;
        }
    }

    public RoomDirectory(Context app) {
        index=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        history=app.getSharedPreferences("internet_p2p",Context.MODE_PRIVATE);
    }

    public static boolean validCode(String code) {
        return code!=null&&code.matches("[A-Z2-9]{16,32}");
    }

    private static String cleanTitle(String title) {
        if(title==null)return "";
        String result=title.replaceAll("[\\p{Cntrl}\\n\\r]"," ").trim();
        return result.length()>32?result.substring(0,32):result;
    }

    private synchronized List<Room> load() {
        List<Room> rooms=new ArrayList<>();
        try {
            JSONArray array=new JSONArray(index.getString(DATA,"[]"));
            for(int i=0;i<array.length()&&i<MAX_ROOMS;i++){
                JSONObject row=array.optJSONObject(i);
                if(row==null)continue;
                String code=row.optString("code","");
                if(!validCode(code)||contains(rooms,code))continue;
                rooms.add(new Room(code,cleanTitle(row.optString("title","")),
                        Math.max(0,row.optLong("used",0))));
            }
        }catch(Exception ignored){}
        return rooms;
    }

    private static boolean contains(List<Room> rooms,String code) {
        for(Room r:rooms)if(r.code.equals(code))return true;
        return false;
    }

    private void save(List<Room> rooms) {
        JSONArray rows=new JSONArray();
        for(Room room:rooms){
            if(rows.length()>=MAX_ROOMS)break;
            JSONObject row=new JSONObject();
            try {
                row.put("code",room.code);
                row.put("title",room.title);
                row.put("used",room.lastUsed);
            }catch(Exception ignored){}
            rows.put(row);
        }
        if(!index.edit().putString(DATA,rows.toString()).commit())
            throw new IllegalStateException("Не удалось сохранить список комнат");
    }

    /** Recover rooms used by old versions, without altering messages. */
    public synchronized void migrateLegacy(String activeCode) {
        if(index.getBoolean(MIGRATED,false)) {
            if(validCode(activeCode))remember(activeCode);
            return;
        }
        List<Room> existing=load();
        for(String key:history.getAll().keySet()){
            String found="";
            if(key.startsWith("msgs_"))found=key.substring(5);
            else if(key.startsWith("pending_"))found=key.substring(8);
            else if(key.startsWith("role_"))found=key.substring(5);
            if(validCode(found)&&!contains(existing,found)&&existing.size()<MAX_ROOMS)
                existing.add(new Room(found,"",0));
        }
        if(validCode(activeCode)&&!contains(existing,activeCode)&&existing.size()<MAX_ROOMS)
            existing.add(new Room(activeCode,"",System.currentTimeMillis()));
        save(existing);
        index.edit().putBoolean(MIGRATED,true).commit();
    }

    public synchronized List<Room> list() {
        List<Room> result=load();
        Collections.sort(result,(left,right)->{
            int recency=Long.compare(right.lastUsed,left.lastUsed);
            return recency!=0?recency:left.code.compareTo(right.code);
        });
        return result;
    }

    public synchronized void remember(String code) {
        if(!validCode(code))throw new IllegalArgumentException("Некорректный код комнаты");
        List<Room> rooms=load();
        for(int i=0;i<rooms.size();i++) {
            Room r=rooms.get(i);
            if(r.code.equals(code)) {
                rooms.set(i,new Room(code,r.title,System.currentTimeMillis()));
                save(rooms);return;
            }
        }
        if(rooms.size()>=MAX_ROOMS)
            throw new IllegalStateException("Список комнат переполнен");
        rooms.add(new Room(code,"",System.currentTimeMillis()));
        save(rooms);
    }

    public synchronized void rename(String code,String title) {
        List<Room> rooms=load();
        for(int i=0;i<rooms.size();i++)if(rooms.get(i).code.equals(code)) {
            Room r=rooms.get(i);
            rooms.set(i,new Room(code,cleanTitle(title),r.lastUsed));
            save(rooms);return;
        }
    }

    /** Only hides an entry from the room chooser; chat history is not removed. */
    public synchronized void forget(String code) {
        List<Room> rooms=load();
        rooms.removeIf(room->room.code.equals(code));
        save(rooms);
    }

    public synchronized String title(String code) {
        for(Room room:load())if(room.code.equals(code))return room.displayName();
        return "Комната • "+code.substring(Math.max(0,code.length()-5));
    }
}
