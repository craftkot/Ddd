package com.ehtid.p2pchat;

/** Network-independent room rules, tested without Android or live relay. */
public final class RoomPolicy {
    private RoomPolicy() {}
    public static final long ONLINE_TTL_MS = 8 * 60 * 1000L;
    public static final long BACKGROUND_TTL_MS = 25 * 60 * 1000L;
    public static final long OFFER_TIMEOUT_MS = 30000L;
    public static final long HISTORY_COOLDOWN_MS = 60000L;

    /** A connected WebRTC participant must never be replaced by a new visitor. */
    public static boolean mayNegotiate(boolean hasPeer, boolean directOpen, long attemptAge) {
        return !hasPeer || (!directOpen && attemptAge >= OFFER_TIMEOUT_MS);
    }

    /** Returning guests should receive history, independently of other guests. */
    public static boolean shouldSyncHistory(Long previous, long now) {
        return previous == null || now - previous >= HISTORY_COOLDOWN_MS;
    }

    /** Foreground and background users have different heartbeat expiry windows. */
    public static boolean isPresent(long now, long lastSeen, boolean foreground) {
        long ttl = foreground ? ONLINE_TTL_MS : BACKGROUND_TTL_MS;
        return now >= lastSeen && now - lastSeen < ttl;
    }

    /** Reply to initial presence once; never get into a ping-pong loop. */
    public static boolean shouldReplyPresence(boolean known, boolean previousOnline, boolean nowOnline) {
        return !known || (!previousOnline && nowOnline);
    }
}
