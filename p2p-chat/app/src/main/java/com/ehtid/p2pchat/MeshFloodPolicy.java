package com.ehtid.p2pchat;

import java.util.Set;

/** Deterministic bounded gossip rule, independent of Android/WebRTC. */
public final class MeshFloodPolicy {
    public static final int MAX_HOPS=8;
    private MeshFloodPolicy() {}

    /** True only for a first-seen valid packet within the forwarding horizon. */
    public static boolean accept(Set<String> seen, String key, int hops) {
        return key!=null && key.length()>2 && key.length()<=100 &&
                hops>=0 && hops<MAX_HOPS && seen.add(key);
    }
}
