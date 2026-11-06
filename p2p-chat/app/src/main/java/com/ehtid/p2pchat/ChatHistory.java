package com.ehtid.p2pchat;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Private, persistent on-device chat history. SQLite survives ordinary APK
 * updates and room switching. No expiry policy or automatic truncation.
 * Android uninstall or Clear app storage still deletes this database.
 */
public final class ChatHistory extends SQLiteOpenHelper {
    private static final int VERSION=1;
    private static ChatHistory singleton;
    public static synchronized ChatHistory get(Context context){
        if(singleton==null) singleton=new ChatHistory(context.getApplicationContext());
        return singleton;
    }
    private ChatHistory(Context context){super(context,"ehtid_history.db",null,VERSION);}

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS chat_messages ("+
                "room TEXT NOT NULL, id TEXT NOT NULL, sent_at INTEGER NOT NULL, "+
                "payload TEXT NOT NULL, PRIMARY KEY(room,id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_room_time "+
                "ON chat_messages(room,sent_at DESC)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        // Future schema migrations must preserve user messages; never DROP TABLE.
    }

    private static boolean validRoom(String room){
        return room!=null&&room.matches("[A-Z2-9]{16,32}");
    }
    private static boolean valid(JSONObject m){
        return m!=null&&m.optString("id").length()>0&&m.optString("id").length()<=80
                &&m.optString("text").length()>0&&m.optString("text").length()<=1000
                &&m.optString("from").length()>0&&m.optString("from").length()<=80;
    }
    private static ContentValues values(String room,JSONObject m){
        ContentValues row=new ContentValues();
        row.put("room",room);
        row.put("id",m.optString("id"));
        row.put("sent_at",m.optLong("at",System.currentTimeMillis()));
        row.put("payload",m.toString());
        return row;
    }

    public synchronized boolean insert(String room,JSONObject m){
        if(!validRoom(room)||!valid(m))return false;
        return getWritableDatabase().insertWithOnConflict("chat_messages",null,
                values(room,m),SQLiteDatabase.CONFLICT_IGNORE)!=-1;
    }
    /** Delivery state changes never create a second copy of the message. */
    public synchronized void update(String room,JSONObject m){
        if(!validRoom(room)||!valid(m))return;
        getWritableDatabase().update("chat_messages",values(room,m),
                "room=? AND id=?",new String[]{room,m.optString("id")});
    }
    public synchronized int count(String room){
        if(!validRoom(room))return 0;
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM chat_messages WHERE room=?",new String[]{room})){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    /**
     * Newest limit rows ordered oldest to newest. No history rows are deleted.
     * Older pages can be requested by increasing limit.
     */
    public synchronized List<JSONObject> recent(String room,int limit){
        List<JSONObject> messages=new ArrayList<>();
        if(!validRoom(room))return messages;
        int safeLimit=Math.max(1,Math.min(limit,5000));
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT payload FROM chat_messages WHERE room=? "+
                "ORDER BY sent_at DESC, rowid DESC LIMIT ?",
                new String[]{room,Integer.toString(safeLimit)})){
            while(c.moveToNext()){
                try{messages.add(new JSONObject(c.getString(0)));}
                catch(Exception ignored){}
            }
        }
        Collections.reverse(messages);
        return messages;
    }
    public synchronized String preview(String room){
        List<JSONObject> list=recent(room,1);
        return list.isEmpty()?"Пока нет сообщений":list.get(0).optString("text","Сообщение");
    }

    /** One-time, non-destructive migration from pre-v1.2 SharedPreferences.
     * Write and commit the SQLite transaction before setting the marker. */
    public synchronized void migrateLegacy(SharedPreferences prefs,String room){
        if(!validRoom(room))return;
        String key="history_sqlite_migrated_v1_"+room;
        if(prefs.getBoolean(key,false))return;
        String legacy=prefs.getString("msgs_"+room,"[]");
        JSONArray messages;
        try{messages=new JSONArray(legacy);}catch(Exception e){return;}
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            for(int i=0;i<messages.length();i++){
                JSONObject msg=messages.optJSONObject(i);
                if(valid(msg))
                    db.insertWithOnConflict("chat_messages",null,values(room,msg),
                            SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        // Retain the previous preference copy for recovery/downgrade.
        prefs.edit().putBoolean(key,true).apply();
    }
}
