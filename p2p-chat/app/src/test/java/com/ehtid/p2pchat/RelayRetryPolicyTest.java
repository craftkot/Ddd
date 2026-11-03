package com.ehtid.p2pchat;

import org.junit.Test;
import static org.junit.Assert.*;

public class RelayRetryPolicyTest {
    @Test public void respectsRetryAfter() {
        assertEquals(300000L,RelayRetryPolicy.retryDelayMs(429,"300",0));
        assertEquals(86400000L,RelayRetryPolicy.retryDelayMs(429,"999999999",0));
    }
    @Test public void neverRetries429InTightLoop() {
        assertTrue(RelayRetryPolicy.retryDelayMs(429,null,0)>=60000L);
        assertTrue(RelayRetryPolicy.retryDelayMs(429,null,2)>=240000L);
    }
    @Test public void retriesOrdinaryFailureWithBackoff() {
        assertEquals(5000L,RelayRetryPolicy.retryDelayMs(503,null,0));
        assertEquals(20000L,RelayRetryPolicy.retryDelayMs(503,null,2));
    }
}
