package com.ehtid.p2pchat;

import org.junit.Test;
import static org.junit.Assert.*;

public class RoomPolicyTest {
    @Test public void activeRtcCannotBeStolenByNewGuest() {
        assertFalse(RoomPolicy.mayNegotiate(true,true,600000L));
        assertFalse(RoomPolicy.mayNegotiate(true,false,10000L));
        assertTrue(RoomPolicy.mayNegotiate(true,false,30000L));
        assertTrue(RoomPolicy.mayNegotiate(false,false,0L));
    }

    @Test public void historyCooldownIsPerGuest() {
        assertTrue(RoomPolicy.shouldSyncHistory(null,1));
        assertFalse(RoomPolicy.shouldSyncHistory(100L,200L));
        assertTrue(RoomPolicy.shouldSyncHistory(100L,60100L));
    }

    @Test public void newGuestGetsImmediatePresenceReplyWithoutLoop() {
        assertTrue(RoomPolicy.shouldReplyPresence(false,false,true));
        assertTrue(RoomPolicy.shouldReplyPresence(true,false,true));
        assertFalse(RoomPolicy.shouldReplyPresence(true,true,true));
        assertFalse(RoomPolicy.shouldReplyPresence(true,false,false));
    }

    @Test public void backgroundDoesNotMeanDisconnected() {
        assertTrue(RoomPolicy.isPresent(5*60*1000L,0L,true));
        assertFalse(RoomPolicy.isPresent(9*60*1000L,0L,true));
        assertTrue(RoomPolicy.isPresent(20*60*1000L,0L,false));
        assertFalse(RoomPolicy.isPresent(26*60*1000L,0L,false));
    }
}
