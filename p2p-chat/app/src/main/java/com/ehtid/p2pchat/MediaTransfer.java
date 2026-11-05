package com.ehtid.p2pchat;

import android.content.Context;
import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * End-to-end encrypted attachments on D1. The Cloudflare Worker stores
 * base64url slices of AES-GCM ciphertext only, never plaintext files.
 *
 * All calls are blocking: callers MUST use a background thread.
 */
public final class MediaTransfer {
    public static final int MAX_FILE_BYTES=10*1024*1024;
    public static final int CHUNK_CHARS=196608;
    public static final int MAX_PARTS=72;
    private static final int MAX_ENCODED=(int)Math.ceil((MAX_FILE_BYTES+28)*4.0/3)+3;
    private static final String HOST="https://ehtid-chat-relay.craftkot404.workers.dev";
    private static final int TIMEOUT=25000;
    private static final SecureRandom RANDOM=new SecureRandom();

    public interface Progress { void update(int done, int total); }
    private MediaTransfer() {}

    /** Throws for files > 10MiB rather than silently truncating content. */
    public static byte[] readLimited(InputStream source) throws Exception {
        try(InputStream input=source; ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] chunk=new byte[16384];
            int total=0, n;
            while((n=input.read(chunk))!=-1){
                total+=n;
                if(total>MAX_FILE_BYTES)throw new IllegalArgumentException("Файл больше 10 МБ");
                out.write(chunk,0,n);
            }
            return out.toByteArray();
        }
    }

    private static byte[] aesKey(String room) throws Exception {
        return MessageDigest.getInstance("SHA-256")
                .digest(room.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(byte[] bytes){
        return Base64.encodeToString(bytes,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
    }

    private static byte[] decode(String data){
        return Base64.decode(data,Base64.URL_SAFE|Base64.NO_WRAP);
    }

    private static byte[] encrypt(byte[] plain,String room) throws Exception {
        byte[] iv=new byte[12];
        RANDOM.nextBytes(iv);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(aesKey(room),"AES"),
                new GCMParameterSpec(128,iv));
        byte[] ciphertext=cipher.doFinal(plain);
        byte[] result=new byte[iv.length+ciphertext.length];
        System.arraycopy(iv,0,result,0,iv.length);
        System.arraycopy(ciphertext,0,result,iv.length,ciphertext.length);
        return result;
    }

    private static byte[] decrypt(byte[] encrypted,String room) throws Exception {
        if(encrypted.length<29 || encrypted.length>MAX_FILE_BYTES+28)
            throw new IllegalArgumentException("Некорректный размер вложения");
        byte[] iv=new byte[12];
        System.arraycopy(encrypted,0,iv,0,12);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(aesKey(room),"AES"),
                new GCMParameterSpec(128,iv));
        return cipher.doFinal(encrypted,12,encrypted.length-12);
    }

    private static String call(String method,String room,String route,String body) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(HOST+"/"+RoomAccess.topic(room)+route).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(TIMEOUT);
            c.setReadTimeout(TIMEOUT);
            c.setRequestProperty("Authorization","Bearer "+RoomAccess.bearer(room));
            c.setRequestProperty("Accept","application/json, text/plain");
            if(body!=null) {
                c.setRequestProperty("Content-Type","text/plain; charset=utf-8");
                c.setDoOutput(true);
                byte[] bytes=body.getBytes(StandardCharsets.US_ASCII);
                c.setFixedLengthStreamingMode(bytes.length);
                try(OutputStream out=c.getOutputStream()){out.write(bytes);}
            }
            int code=c.getResponseCode();
            if(code<200||code>=300)throw new java.io.IOException(
                    "Cloudflare HTTP "+code+" для вложения");
            try(InputStream input=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] chunk=new byte[16384];
                int count,total=0;
                while((count=input.read(chunk))!=-1){
                    total+=count;
                    if(total>CHUNK_CHARS+4096)throw new java.io.IOException("Некорректный ответ сервера");
                    out.write(chunk,0,count);
                }
                return out.toString("UTF-8");
            }
        } finally {c.disconnect();}
    }

    private static String safeName(String name) {
        if(name==null) return "file";
        String cleaned=name.replaceAll("[\\\\/\\p{Cntrl}]+","_").trim();
        if(cleaned.length()>80)cleaned=cleaned.substring(cleaned.length()-80);
        return cleaned.isEmpty()?"file":cleaned;
    }

    private static String safeMime(String mime) {
        if(mime==null || mime.length()>90 || !mime.matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+"))
            return "application/octet-stream";
        return mime;
    }

    /**
     * Upload all encrypted parts BEFORE sending the encrypted chat manifest.
     * Throws on a failed write, leaving local chat message unsent rather than
     * incorrectly showing a broken attachment.
     */
    public static JSONObject upload(byte[] plain,String room,String filename,
                                    String mime,String kind,long durationMs,
                                    Progress progress) throws Exception {
        if(plain==null||plain.length<1||plain.length>MAX_FILE_BYTES)
            throw new IllegalArgumentException("Допустимо от 1 байта до 10 МБ");
        byte[] encrypted=encrypt(plain,room);
        String wire=encode(encrypted);
        if(wire.length()>MAX_ENCODED)throw new IllegalArgumentException("Вложение превышает лимит");
        int total=(wire.length()+CHUNK_CHARS-1)/CHUNK_CHARS;
        if(total<1||total>MAX_PARTS)throw new IllegalArgumentException("Слишком много частей");
        String id=UUID.randomUUID().toString().replace("-","");
        for(int i=0;i<total;i++) {
            String data=wire.substring(i*CHUNK_CHARS,Math.min((i+1)*CHUNK_CHARS,wire.length()));
            JSONObject reply=new JSONObject(call("POST",room,"/files/"+id+"/"+i,data));
            if(!reply.optBoolean("ok"))throw new java.io.IOException("Файл не принят сервером");
            if(progress!=null)progress.update(i+1,total);
        }
        JSONObject manifest=new JSONObject();
        manifest.put("parts",total);
        manifest.put("encodedBytes",wire.length());
        JSONObject reply=new JSONObject(call("POST",room,"/files/"+id+"/complete",manifest.toString()));
        if(!reply.optBoolean("complete"))throw new java.io.IOException("Файл не подтверждён Cloudflare");
        JSONObject meta=new JSONObject();
        meta.put("id",id);
        meta.put("parts",total);
        meta.put("size",plain.length);
        meta.put("mime",safeMime(mime));
        meta.put("name",safeName(filename));
        meta.put("kind","voice".equals(kind)?"voice":mime!=null&&mime.startsWith("image/")?"image":"file");
        if(durationMs>0)meta.put("duration",durationMs);
        return meta;
    }

    public static File saveLocal(Context ctx,String room,JSONObject meta,byte[] bytes) throws Exception {
        if(bytes==null||bytes.length!=meta.optInt("size")||bytes.length>MAX_FILE_BYTES)
            throw new IllegalArgumentException("Некорректный файл");
        File target=localPath(ctx,room,meta);
        try(FileOutputStream out=new FileOutputStream(target)){out.write(bytes);}
        return target;
    }

    private static File localPath(Context ctx,String room,JSONObject meta) throws Exception {
        String id=meta.optString("id");
        if(!id.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("Некорректный ID");
        File cache=new File(ctx.getFilesDir(),"media/"+RoomAccess.topic(room));
        if(!cache.exists()&&!cache.mkdirs())throw new java.io.IOException("Нет доступа к памяти");
        String mime=safeMime(meta.optString("mime"));
        String ext=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        if(ext==null||!ext.matches("[a-zA-Z0-9]{1,8}"))ext="bin";
        return new File(cache,id+"."+ext);
    }

    /** Explicit user opt-in is mandatory: this sends RAW audio to Cloudflare
     * Workers AI for transcription, unlike encrypted private file uploads. */
    public static String transcribe(byte[] audio,String room) throws Exception {
        if(audio==null||audio.length<100||audio.length>2*1024*1024)
            throw new IllegalArgumentException("Расшифровка доступна для записей до 2 МБ");
        HttpURLConnection c=(HttpURLConnection)new URL(
                HOST+"/"+RoomAccess.topic(room)+"/transcribe").openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(90000);
            c.setRequestProperty("Authorization","Bearer "+RoomAccess.bearer(room));
            c.setRequestProperty("Content-Type","audio/mp4");
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(audio.length);
            try(OutputStream out=c.getOutputStream()){out.write(audio);}
            int status=c.getResponseCode();
            InputStream incoming=status>=200&&status<300?c.getInputStream():c.getErrorStream();
            String text;
            try(InputStream in=incoming;ByteArrayOutputStream b=new ByteArrayOutputStream()){
                byte[] buf=new byte[1024];int n,total=0;
                while((n=in.read(buf))!=-1){
                    if((total+=n)>8192)throw new java.io.IOException("Слишком длинный ответ");
                    b.write(buf,0,n);
                }
                text=b.toString("UTF-8");
            }
            JSONObject res=new JSONObject(text);
            if(status!=200||!res.optBoolean("ok"))
                throw new java.io.IOException("Whisper: "+res.optString("error","HTTP "+status));
            String transcript=res.optString("text","").trim();
            if(transcript.isEmpty())throw new java.io.IOException("Речь не распознана");
            return transcript.length()>3000?transcript.substring(0,3000):transcript;
        } finally {c.disconnect();}
    }

    /**
     * Returns private local file. Validates the encrypted manifest, authenticated
     * GCM tag and original length before opening any untrusted content.
     */
    public static File download(Context ctx,String room,JSONObject meta,Progress progress) throws Exception {
        String id=meta.optString("id");
        int parts=meta.optInt("parts");
        int bytes=meta.optInt("size");
        if(!id.matches("[a-f0-9]{32}")||parts<1||parts>MAX_PARTS||
                bytes<1||bytes>MAX_FILE_BYTES)
            throw new IllegalArgumentException("Некорректное вложение");
        File local=localPath(ctx,room,meta);
        if(local.isFile()&&local.length()==bytes)return local;
        JSONObject serverMeta=new JSONObject(call("GET",room,"/files/"+id+"/meta",null));
        if(serverMeta.optInt("parts")!=parts || serverMeta.optInt("encodedBytes")>MAX_ENCODED)
            throw new java.io.IOException("Неверная информация о файле");
        StringBuilder encrypted=new StringBuilder(Math.min(serverMeta.optInt("encodedBytes"),MAX_ENCODED));
        for(int i=0;i<parts;i++){
            String data=call("GET",room,"/files/"+id+"/"+i,null);
            if(data.length()>CHUNK_CHARS||encrypted.length()+data.length()>MAX_ENCODED)
                throw new java.io.IOException("Размер вложения превышен");
            encrypted.append(data);
            if(progress!=null)progress.update(i+1,parts);
        }
        if(encrypted.length()!=serverMeta.optInt("encodedBytes"))
            throw new java.io.IOException("Повреждённое вложение");
        byte[] clear=decrypt(decode(encrypted.toString()),room);
        if(clear.length!=bytes)throw new java.io.IOException("Размер не совпадает");
        File temp=new File(cache,id+".tmp");
        try(FileOutputStream out=new FileOutputStream(temp)){out.write(clear);}
        if(!temp.renameTo(local)){
            temp.delete();
            throw new java.io.IOException("Не удалось сохранить вложение");
        }
        return local;
    }
}
