package com.ehtid.p2pchat;
import org.junit.Test;
import static org.junit.Assert.*;
public final class ReleasePolicyTest {
    @Test public void betaVersionComparison(){
        assertTrue(ReleasePolicy.newer("ehtid-p2p-chat-v1.2","1.1.0"));
        assertTrue(ReleasePolicy.newer("ehtid-p2p-chat-v1.2.1","1.2.0"));
        assertFalse(ReleasePolicy.newer("ehtid-p2p-chat-v1.1","1.1.0"));
        assertFalse(ReleasePolicy.newer("ehtid-p2p-chat-v1.0","1.1.0"));
    }
    @Test public void unsafeNamesRejected(){
        assertNull(ReleasePolicy.versionFromTag("../unknown-v3.0"));
        assertNull(ReleasePolicy.assetName("ehtid-p2p-chat-v1.2.apk"));
        assertEquals("EHTID-P2P-Chat-v1.2.apk",ReleasePolicy.assetName("ehtid-p2p-chat-v1.2"));
        assertEquals("EHTID-P2P-Chat-v1.2.3.apk",ReleasePolicy.assetName("ehtid-p2p-chat-v1.2.3"));
    }
    @Test public void checksumRequired(){
        assertTrue(ReleasePolicy.safeDigest("sha256:"+"a".repeat(64)));
        assertFalse(ReleasePolicy.safeDigest("sha1:"+"a".repeat(40)));
        assertFalse(ReleasePolicy.safeDigest("sha256:bad"));
    }
}