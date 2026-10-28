package dev.relaybox.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class MessageStore extends SQLiteOpenHelper {
    public static final class Entry {
        public final long id, time;
        public final Platform platform;
        public final String sender, message;
        public final boolean seen;
        Entry(long id, long time, Platform platform, String sender, String message, boolean seen) {
            this.id=id; this.time=time; this.platform=platform; this.sender=sender; this.message=message; this.seen=seen;
        }
    }
    MessageStore(Context ctx) { super(ctx,"relaybox.db",null,1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT,fingerprint TEXT UNIQUE NOT NULL,platform TEXT NOT NULL,sender BLOB NOT NULL,body BLOB NOT NULL,ts INTEGER NOT NULL,seen INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX ts_index ON messages(ts DESC)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { }
    private static synchronized SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        String alias="relaybox_message_key";
        if(!store.containsAlias(alias)) {
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build());
            gen.generateKey();
        }
        return (SecretKey)store.getKey(alias,null);
    }
    private static byte[] encrypt(String text) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] iv=cipher.getIV(), data=cipher.doFinal(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] all=new byte[iv.length+data.length];
        System.arraycopy(iv,0,all,0,iv.length);
        System.arraycopy(data,0,all,iv.length,data.length);
        return all;
    }
    private static String decrypt(byte[] all) throws Exception {
        if(all==null||all.length<29) return "";
        byte[] iv=java.util.Arrays.copyOfRange(all,0,12);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
        return new String(cipher.doFinal(all,12,all.length-12),java.nio.charset.StandardCharsets.UTF_8);
    }
    long add(String fingerprint,Platform platform,String sender,String message,long ts) throws Exception {
        ContentValues values=new ContentValues();
        values.put("fingerprint",fingerprint);
        values.put("platform",platform.id);
        values.put("sender",encrypt(sender));
        values.put("body",encrypt(message));
        values.put("ts",ts);
        long id=getWritableDatabase().insertWithOnConflict("messages",null,values,SQLiteDatabase.CONFLICT_IGNORE);
        if(id>0) getWritableDatabase().execSQL("DELETE FROM messages WHERE id NOT IN (SELECT id FROM messages ORDER BY id DESC LIMIT 1000)");
        return id;
    }
    List<Entry> recent() {
        List<Entry> list=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,ts,platform,sender,body,seen FROM messages ORDER BY ts DESC,id DESC LIMIT 300",null)) {
            while(c.moveToNext()) {
                try {
                    Platform p=Platform.from(c.getString(2));
                    if(p!=null) list.add(new Entry(c.getLong(0),c.getLong(1),p,decrypt(c.getBlob(3)),decrypt(c.getBlob(4)),c.getInt(5)!=0));
                } catch(Exception ignored) { }
            }
        }
        return list;
    }
    Entry byId(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,ts,platform,sender,body,seen FROM messages WHERE id=?",new String[]{Long.toString(id)})) {
            if(c.moveToFirst()) return new Entry(c.getLong(0),c.getLong(1),Platform.from(c.getString(2)),decrypt(c.getBlob(3)),decrypt(c.getBlob(4)),c.getInt(5)!=0);
        } catch(Exception ignored) {}
        return null;
    }
    void markRead(long id) { getWritableDatabase().execSQL("UPDATE messages SET seen=1 WHERE id="+id); }
    void allRead() { getWritableDatabase().execSQL("UPDATE messages SET seen=1"); }
    void clearMessages() { getWritableDatabase().delete("messages",null,null); }
}
