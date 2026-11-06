package com.ehtid.p2pchat;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The local list is not a server account and is never uploaded.
 * Removing a room shortcut does NOT delete its SQLite messages or media. */
public final class SavedRooms {
    public static final class Room {
        public final String code, title;
        public final long lastOpened;
        Room(String code,String title,long time){
            this.code=code;
            this.title=title;
            this.lastOpened=time;
        }
    }
    private static final int LIMIT=200;
    private final SharedPreferences prefs;
    public SavedRooms(Context context){
        prefs=context.getApplicationContext().getSharedPreferences(
                "ehtid_rooms",Context.MODE_PRIVATE);
    }
    private static boolean valid(String room){
        return room!=null&&room.matches("[A-Z2-9]{16,32}");
    }
    private List<Room> parse(){
        List<Room> rooms=new ArrayList<>();
        try{
            JSONArray data=new JSONArray(prefs.getString("saved_rooms_v1","[]"));
            for(int i=0;i<data.length()&&rooms.size()<LIMIT;i++){
                JSONObject obj=data.optJSONObject(i);
                if(obj==null)continue;
                String code=obj.optString("code");
                if(!valid(code))continue;
                boolean duplicate=false;
                for(Room previous:rooms)
                    if(previous.code.equals(code)){duplicate=true;break;}
                if(duplicate)continue;
                rooms.add(new Room(code,safeTitle(obj.optString("title")),
                        obj.optLong("opened",0)));
            }
        }catch(Exception ignored){}
        rooms.sort(Comparator.comparingLong((Room room)->room.lastOpened).reversed());
        return rooms;
    }
    private static String safeTitle(String value){
        if(value==null)return "";
        String title=value.replaceAll("[\\p{Cntrl}]","").trim();
        return title.length()>36?title.substring(0,36):title;
    }
    private void save(List<Room> rooms){
        JSONArray data=new JSONArray();
        for(Room room:rooms){
            if(data.length()>=LIMIT)break;
            JSONObject item=new JSONObject();
            try{
                item.put("code",room.code);
                item.put("title",room.title);
                item.put("opened",room.lastOpened);
                data.put(item);
            }catch(Exception ignored){}
        }
        if(!prefs.edit().putString("saved_rooms_v1",data.toString()).commit())
            throw new IllegalStateException("Не удалось сохранить список комнат");
    }
    public synchronized List<Room> list(){return parse();}
    public synchronized void remember(String room) {
        if(!valid(room))throw new IllegalArgumentException("Недействительный код комнаты");
        List<Room> old=parse(),updated=new ArrayList<>();
        String title="";
        for(Room r:old)if(r.code.equals(room)){title=r.title;break;}
        updated.add(new Room(room,title,System.currentTimeMillis()));
        for(Room r:old)if(!r.code.equals(room)&&updated.size()<LIMIT)updated.add(r);
        save(updated);
    }
    public synchronized void rename(String room,String title){
        if(!valid(room))return;
        List<Room> old=parse(),updated=new ArrayList<>();
        for(Room r:old){
            updated.add(r.code.equals(room)?new Room(room,safeTitle(title),r.lastOpened):r);
        }
        save(updated);
    }
    /** Removes only the shortcut; chat history and cached attachments remain. */
    public synchronized void hide(String room){
        if(!valid(room))return;
        List<Room> updated=new ArrayList<>();
        for(Room r:parse())if(!r.code.equals(room))updated.add(r);
        save(updated);
    }
    public synchronized boolean contains(String room){
        if(!valid(room))return false;
        for(Room r:parse())if(r.code.equals(room))return true;
        return false;
    }
}
