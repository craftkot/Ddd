package com.ehtid.p2pchat;

import org.junit.Test;
import static org.junit.Assert.*;

public class HomeServerUrlTest {
    @Test public void acceptsTunnelAddress() {
        assertEquals("https://ehtid.example.ts.net",
                HomeServerUrl.normalize(" https://EHTID.example.ts.net/ "));
        assertEquals("https://quiet.trycloudflare.com",
                HomeServerUrl.normalize("https://quiet.trycloudflare.com"));
    }
    @Test public void rejectsInsecureOrWrongEndpoints() {
        assertEquals("",HomeServerUrl.normalize("http://1.2.3.4:8765"));
        assertEquals("",HomeServerUrl.normalize("https://example.com/publish"));
        assertEquals("",HomeServerUrl.normalize("https://user:pass@example.com"));
        assertEquals("",HomeServerUrl.normalize("https://example.com?x=1"));
        assertEquals("",HomeServerUrl.normalize("not an address"));
        assertEquals("",HomeServerUrl.normalize(null));
    }
    @Test public void keepsNondefaultSecurePort() {
        assertEquals("https://relay.example.org:8443",
                HomeServerUrl.normalize("https://relay.example.org:8443/"));
    }
}
