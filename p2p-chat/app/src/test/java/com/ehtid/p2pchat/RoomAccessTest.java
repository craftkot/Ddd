package com.ehtid.p2pchat;

import org.junit.Test;
import static org.junit.Assert.*;

public final class RoomAccessTest {
    @Test public void sameRoomGivesSameTokenAndTopic() {
        String room="EHTIDTESTROOM88Z99";
        assertEquals("9dc9cb2b91ec0307ddbee78dabaf0b167226b8da8d8fc8d2c5901f5bee25fe3a",
                RoomAccess.bearer(room));
        assertEquals("ehtidp2p-d09b81a540e20abb3df12bfc5f50f852d99e6912",
                RoomAccess.topic(room));
        assertEquals(RoomAccess.topic(room),RoomAccess.topic(room));
    }

    @Test public void distinctRoomsCannotShareAnAccessCapability() {
        assertNotEquals(RoomAccess.bearer("EHTIDTESTROOM88Z99"),
                RoomAccess.bearer("EHTIDTESTROOM88Z98"));
        assertNotEquals(RoomAccess.topic("EHTIDTESTROOM88Z99"),
                RoomAccess.topic("EHTIDTESTROOM88Z98"));
    }

    @Test public void roomCapabilityDoesNotExposeLegacyAesKey() {
        assertEquals(64,RoomAccess.bearer("EHTIDTESTROOM88Z99").length());
        assertTrue(RoomAccess.topic("EHTIDTESTROOM88Z99")
                .matches("ehtidp2p-[0-9a-f]{40}"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void rejectsInvalidCodes() { RoomAccess.bearer("too short"); }
}
