package com.ehtid.p2pchat;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ReleaseVersionTest {
    @Test public void acceptsOfficialBetaTagsWithPatchOrWithoutPatch(){
        assertEquals("1.2.0",ReleaseVersion.fromTag("ehtid-p2p-chat-v1.2").toString());
        assertEquals("1.2.1",ReleaseVersion.fromTag("ehtid-p2p-chat-v1.2.1").toString());
        assertEquals("1.1.0",ReleaseVersion.fromApp("1.1.0").toString());
    }
    @Test public void choosesHighestNumericVersion(){
        assertTrue(ReleaseVersion.fromTag("ehtid-p2p-chat-v1.12")
                .compareTo(ReleaseVersion.fromApp("1.9.0"))>0);
        assertTrue(ReleaseVersion.fromTag("ehtid-p2p-chat-v2.0")
                .compareTo(ReleaseVersion.fromApp("1.99.0"))>0);
        assertTrue(ReleaseVersion.fromTag("ehtid-p2p-chat-v1.2.1")
                .compareTo(ReleaseVersion.fromApp("1.2.0"))>0);
        assertEquals(0,ReleaseVersion.fromTag("ehtid-p2p-chat-v1.2")
                .compareTo(ReleaseVersion.fromApp("1.2.0")));
    }
    @Test public void rejectsExternalProjectsOrInjectedPaths(){
        assertNull(ReleaseVersion.fromTag("bluearchive-v1.3"));
        assertNull(ReleaseVersion.fromTag("ehtid-p2p-chat-v1.2.0/../../bad"));
        assertNull(ReleaseVersion.fromTag("ehtid-p2p-chat-v1.2-rc1"));
        assertNull(ReleaseVersion.fromTag("ehtid-p2p-chat-v1.2.apk"));
        assertNull(ReleaseVersion.fromTag("ehtid-p2p-chat-v2147483648.0"));
        assertNull(ReleaseVersion.fromTag("ehtid-p2p-chat-v1.100001"));
        assertNull(ReleaseVersion.fromApp("1.2.0
"));
        assertNull(ReleaseVersion.fromApp("bad"));
    }
}
