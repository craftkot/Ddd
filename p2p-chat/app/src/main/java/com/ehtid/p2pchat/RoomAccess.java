package com.ehtid.p2pchat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Access capability derived only from the secret room code, not from an
 * embedded app-wide secret. Cloudflare validates SHA-256(token) against the
 * topic without learning the room code or SHA-256(roomCode) AES key.
 *
 * v1.0 uses a new topic namespace; older cloud history remains locally
 * on each phone but is not retrievable from the legacy v0.9 server topic.
 */
public final class RoomAccess {
    private static final char[] HEX="0123456789abcdef".toCharArray();
    private static final String ACCESS_CONTEXT="EHTID-RELAY-ACCESS-V1:";
    private RoomAccess() {}

    public static String bearer(String roomCode) {
        if (roomCode==null || !roomCode.matches("[A-Z2-9]{16,32}"))
            throw new IllegalArgumentException("Invalid room code");
        return hex(sha256((ACCESS_CONTEXT+roomCode).getBytes(StandardCharsets.UTF_8)));
    }

    public static String topic(String roomCode) {
        String token=bearer(roomCode);
        byte[] topicDigest=sha256(token.getBytes(StandardCharsets.US_ASCII));
        return "ehtidp2p-"+hex(topicDigest).substring(0,40);
    }

    private static byte[] sha256(byte[] input) {
        try { return MessageDigest.getInstance("SHA-256").digest(input); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static String hex(byte[] input) {
        char[] result=new char[input.length*2];
        for (int i=0;i<input.length;i++) {
            int value=input[i]&0xff;
            result[2*i]=HEX[value>>>4];
            result[2*i+1]=HEX[value&15];
        }
        return new String(result);
    }
}
