package com.ehtid.p2pchat;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public final class MeshFloodPolicyTest {
    @Test public void chainOfPhonesForwardsOnlyOnce() {
        Set<String> phoneA=new HashSet<>();
        Set<String> phoneB=new HashSet<>();
        Set<String> phoneC=new HashSet<>();
        String event="msg:one-common-message";
        assertTrue(MeshFloodPolicy.accept(phoneA,event,0));
        assertTrue(MeshFloodPolicy.accept(phoneB,event,0));
        assertTrue(MeshFloodPolicy.accept(phoneC,event,1));
        assertFalse(MeshFloodPolicy.accept(phoneB,event,2)); // C must not loop to B
        assertFalse(MeshFloodPolicy.accept(phoneA,event,2)); // duplicates never redeliver
    }
    @Test public void hopLimitAndMalformedEventsCannotFloodMesh() {
        Set<String> seen=new HashSet<>();
        assertFalse(MeshFloodPolicy.accept(seen,"msg:a",8));
        assertFalse(MeshFloodPolicy.accept(seen,"msg:a",-1));
        assertFalse(MeshFloodPolicy.accept(seen,"",0));
        assertTrue(MeshFloodPolicy.accept(seen,"msg:a",7));
        assertFalse(MeshFloodPolicy.accept(seen,"msg:a",7));
    }
}
