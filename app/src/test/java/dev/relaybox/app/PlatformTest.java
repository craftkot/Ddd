package dev.relaybox.app;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PlatformTest {
    @Test public void mapsIdsCorrectly() {
        assertEquals(Platform.AVITO, Platform.from("avito"));
        assertEquals(Platform.FUNPAY, Platform.from("funpay"));
        assertEquals(Platform.PLAYEROK, Platform.from("playerok"));
        assertNull(Platform.from("unknown"));
        assertNull(Platform.from(null));
    }
    @Test public void officialDomainsRequireHttpsAndCorrectHost() {
        for (Platform p : Platform.values()) {
            assertTrue(p.id, Platform.isOfficial(p,p.url));
            assertTrue(Platform.isOfficial(p,"https://www."+p.host+"/"));
            assertFalse(Platform.isOfficial(p,"http://"+p.host+"/"));
            assertFalse(Platform.isOfficial(p,"https://"+p.host+".evil.example/"));
            assertFalse(Platform.isOfficial(p,"https://evil.example/?next=https://"+p.host));
            assertFalse(Platform.isOfficial(p,"https://"+p.host+"@evil.example/"));
            assertFalse(Platform.isOfficial(p,"https://"+p.host+":444/"));
            assertFalse(Platform.isOfficial(p,"javascript:alert(1)"));
        }
    }
    @Test public void linksAreNotConfusedAcrossPlatforms() {
        assertTrue(Platform.chatLink(Platform.AVITO,"https://www.avito.ru/profile/messenger"));
        assertTrue(Platform.chatLink(Platform.FUNPAY,"https://funpay.com/chat/"));
        assertTrue(Platform.chatLink(Platform.PLAYEROK,"https://playerok.com/chats"));
        assertFalse(Platform.chatLink(Platform.FUNPAY,"https://playerok.com/chats"));
        assertFalse(Platform.chatLink(Platform.PLAYEROK,"https://funpay.com/chat/"));
        assertFalse(Platform.chatLink(Platform.AVITO,"https://www.avito.ru/"));
    }
    @Test public void savesOnlyDistinctConversationUrls() {
        assertFalse(Platform.specificLink(Platform.AVITO, Platform.AVITO.url));
        assertFalse(Platform.specificLink(Platform.FUNPAY, Platform.FUNPAY.url));
        assertFalse(Platform.specificLink(Platform.PLAYEROK, Platform.PLAYEROK.url));
        assertTrue(Platform.specificLink(Platform.AVITO,"https://www.avito.ru/profile/messenger?chat_id=42"));
        assertTrue(Platform.specificLink(Platform.FUNPAY,"https://funpay.com/chat/?node=42"));
        assertTrue(Platform.specificLink(Platform.PLAYEROK,"https://playerok.com/chats/42"));
    }
    @Test public void malformedUrlsDoNotCrash() {
        assertFalse(Platform.isOfficial(Platform.FUNPAY,"https://["));
        assertFalse(Platform.chatLink(Platform.FUNPAY,null));
        assertFalse(Platform.specificLink(Platform.FUNPAY,""));
    }
}
