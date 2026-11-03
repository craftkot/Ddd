package com.ehtid.p2pchat;

/** Pure-Java retry policy: never hammer a relay that returned HTTP 429. */
public final class RelayRetryPolicy {
    private RelayRetryPolicy() {}
    public static final long MAX_RETRY_MS = 24L * 60 * 60 * 1000;
    public static long retryDelayMs(int status, String retryAfter, int failures) {
        if (status == 429 && retryAfter != null) {
            try {
                long seconds = Long.parseLong(retryAfter.trim());
                if (seconds >= 0) return Math.max(1000L,Math.min(MAX_RETRY_MS, seconds * 1000L));
            } catch (NumberFormatException ignored) {}
        }
        int pow = Math.min(6,Math.max(0,failures));
        long base = status == 429 ? 60000L : 5000L;
        return Math.min(MAX_RETRY_MS,base * (1L<<pow));
    }
}
