package com.ehtid.p2pchat;

import org.junit.Test;
import static org.junit.Assert.*;

public final class RoomAccessTest {
    @Test public void sameRoomGivesSameTokenAndTopic() {
        String room="U497ZANYDLWPKQJT5RWLL";
        assertEquals("82e62e4545c68cd22848aade6d657db5f4738bd2719cebf5f65de3b6d053c6f3",
                RoomAccess.bearer(room));
        assertEquals("ehtidp2p-f6e195650e3d8d0b599b39b4adc15cddd67082e5",
                RoomAccess.topic(room));
        assertEquals(RoomAccess.topic(room),RoomAccess.topic(room));
    }

    @Test public void distinctRoomsCannotShareAnAccessCapability() {
        assertNotEquals(RoomAccess.bearer("U497ZANYDLWPKQJT5RWLL"),
                RoomAccess.bearer("U497ZANYDLWPKQJT5RWLK"));
        assertNotEquals(RoomAccess.topic("U497ZANYDLWPKQJT5RWLL"),
                RoomAccess.topic("U497ZANYDLWPKQJT5RWLK"));
    }

    @Test public void roomCapabilityDoesNotExposeLegacyAesKey() {
        String oldAesKeyDigest="7";
        assertEquals(64,RoomAccess.bearer("U497ZANYDLWPKQJT5RWLL").length());
        assertTrue(RoomAccess.topic("U497ZANYDLWPKQJT5RWLL")
                .matches("ehtidp2p-[0-9a-f]{40}"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void rejectsInvalidCodes() { RoomAccess.bearer("too short"); }
}
