package com.ehtid.p2pchat;

import org.junit.Test;
import static org.junit.Assert.*;

public final class UpdateVersionTest {
    @Test public void prereleaseTagsAreConsidered() {
        assertNotNull(UpdateVersion.fromReleaseTag("ehtid-p2p-chat-v1.2"));
        assertEquals("1.2.0",UpdateVersion.fromReleaseTag("ehtid-p2p-chat-v1.2").toString());
        assertEquals("EHTID-P2P-Chat-v1.2.apk",
                UpdateVersion.expectedApkName("ehtid-p2p-chat-v1.2"));
        assertEquals("EHTID-P2P-Chat-v1.2.1.apk",
                UpdateVersion.expectedApkName("ehtid-p2p-chat-v1.2.1"));
    }

    @Test public void comparisonsAreNumericAndIgnoreEquivalentPatchZeros() {
        assertTrue(UpdateVersion.parse("1.10.0").compareTo(UpdateVersion.parse("1.9"))>0);
        assertTrue(UpdateVersion.parse("2.0").compareTo(UpdateVersion.parse("1.99"))>0);
        assertEquals(0,UpdateVersion.parse("1.2").compareTo(UpdateVersion.parse("1.2.0")));
        assertTrue(UpdateVersion.parse("1.2.1").compareTo(UpdateVersion.parse("1.2"))>0);
    }

    @Test public void refuseMaliciousOrNonAppReleaseTags() {
        assertNull(UpdateVersion.fromReleaseTag("ehtid-chat-relay-v2.0"));
        assertNull(UpdateVersion.fromReleaseTag("ehtid-p2p-chat-v1.2-rc1"));
        assertNull(UpdateVersion.fromReleaseTag("ehtid-p2p-chat-v1.2.apk/../../other"));
        assertNull(UpdateVersion.fromReleaseTag("v1.2"));
        assertNull(UpdateVersion.parse("1.2.3.4"));
        assertNull(UpdateVersion.parse("999999999999999999.1"));
        assertEquals("",UpdateVersion.expectedApkName("random"));
    }
}
