package org.mininotes.android;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** The owner's relays as typed, as kept, and the rules of choosing: see Relays. Documentation addresses only. */
public class RelaysTest {
    private static final List<String> SHIPPED=Arrays.asList("203.0.113.1:9501","203.0.113.2:9501");
    private static final List<String> MINE=Arrays.asList("relay.example.org:9001","[2001:db8::7]:9001");

    @Test public void anAddressIsKeptInOneForm() {
        assertEquals("relay.example.org:9001",Relays.parse("relay.example.org:9001"));
        assertEquals("relay.example.org:9001",Relays.parse("  Relay.Example.ORG:9001 "));
        assertEquals("203.0.113.5:9001",Relays.parse("203.0.113.5:9001"));
        assertEquals("[2001:db8::1]:9001",Relays.parse("[2001:DB8::1]:9001"));
        assertEquals("a-b.example.org:1",Relays.parse("a-b.example.org:1"));
        assertEquals("relay.example.org:65535",Relays.parse("relay.example.org:65535"));
        // A Parlons relay's shared text names the same relay.
        assertEquals("203.0.113.5:9001",Relays.parse("parlons-relay:203.0.113.5:9001"));
        assertEquals("203.0.113.5:9001",Relays.parse("PARLONS-RELAY://203.0.113.5:9001"));
        assertNull(Relays.problem("relay.example.org:9001"));
    }

    @Test public void whatIsNotAnAddressIsSaidPlainly() {
        for(String typed:new String[]{null,"","   ","relay.example.org","relay.example.org:","relay.example.org:0",
                "relay.example.org:65536","relay.example.org:99999","relay.example.org:-1","relay.example.org:90a",
                "relay.example.org:+9001",":9001","a b.example.org:9001","203.0.113.5:9001,203.0.113.6:9001",
                "2001:db8::1:9001","[2001:db8::1]9001","[2001:db8::1]","[2001:db8::1:9001","[zz::1]:9001","[]:9001",
                "[203.0.113.5]:9001","1.2.3:9001","256.1.1.1:9001","-bad.example.org:9001","bad-.example.org:9001",
                "bad..example.org:9001","relay.example.org.:9001","under_score.example.org:9001","relay/x.example.org:9001"}) {
            assertNull(typed,Relays.parse(typed));
            String why=Relays.problem(typed);
            assertNotNull(typed,why);
            assertTrue(typed+": "+why,why.endsWith("."));
        }
        assertTrue(Relays.problem("2001:db8::1:9001").contains("square brackets"));
        assertTrue(Relays.problem("relay.example.org").contains("port"));
        assertTrue(Relays.problem("relay.example.org:0").contains("1 to 65535"));
        String longest="a".repeat(63)+"."+"b".repeat(63)+"."+"c".repeat(63)+"."+"d".repeat(61)+".org";
        assertTrue(longest.length()>253);
        assertNull(Relays.parse(longest+":9001"));
        assertNull(Relays.parse("a".repeat(64)+".example.org:9001"));
    }

    @Test public void aHomeNetworkAddressIsNotARelay() {
        for(String typed:new String[]{"10.0.0.1:9001","127.0.0.1:9001","192.168.1.2:9001","172.16.0.1:9001","172.31.255.1:9001",
                "169.254.1.1:9001","100.64.0.1:9001","0.0.0.0:9001","localhost:9001","mypc:9001","nas.local:9001","box.lan:9001",
                "[::1]:9001","[::]:9001","[fe80::1]:9001","[fd00::1]:9001","[fc00::1]:9001"})
            assertTrue(typed,Relays.problem(typed).contains("home network"));
        // Not every 172 is a home one.
        assertFalse(Relays.problem("172.32.0.1:9001").contains("home network"));
    }

    @Test public void anAddressTheTransportWouldNeverHandOutIsRefusedAndSaysWhy() {
        // The transport takes every address starting 198. for a private one, and never names it to anybody.
        String why=Relays.problem("198.51.100.7:9001");
        assertNotNull(why);
        assertTrue(why,why.contains("198."));
        assertFalse(why,why.contains("home network"));
        assertNull(Relays.parse("198.51.100.7:9001"));
    }

    @Test public void keptOneALineAndReadBackForgivingly() {
        List<String> own=new ArrayList<>(MINE);
        assertEquals(own,Relays.fromKept(Relays.toKept(own)));
        assertEquals(Collections.emptyList(),Relays.fromKept(null));
        assertEquals(Collections.emptyList(),Relays.fromKept(""));
        // What cannot be read is dropped, the same relay twice is one, and the most there can be is kept to.
        assertEquals(Arrays.asList("203.0.113.5:9001","relay.example.org:9001"),
            Relays.fromKept("junk\n203.0.113.5:9001\r\n\n203.0.113.5:9001\nRelay.example.org:9001\n10.0.0.1:9001"));
        StringBuilder many=new StringBuilder();
        for(int i=1;i<=20;i++)many.append("203.0.113.").append(i).append(":9001\n");
        assertEquals(Relays.MOST,Relays.fromKept(many.toString()).size());
        // The switch: only "off" is off, so a device that never saw it is on.
        assertTrue(Relays.publicOn("on"));
        assertTrue(Relays.publicOn(""));
        assertTrue(Relays.publicOn(null));
        assertFalse(Relays.publicOn("off"));
    }

    @Test public void theOwnersComeFirstAndThePublicOnesOnlyWhenOn() {
        assertEquals(Arrays.asList("relay.example.org:9001","[2001:db8::7]:9001","203.0.113.1:9501","203.0.113.2:9501"),Relays.seeds(MINE,true,SHIPPED));
        assertEquals(MINE,Relays.seeds(MINE,false,SHIPPED));
        assertEquals(Collections.emptyList(),Relays.seeds(Collections.emptyList(),false,SHIPPED));
        // Somebody's gossip is listened to only while the public relays are on.
        assertTrue(Relays.mayUse("203.0.113.99:9001",MINE,true));
        assertFalse(Relays.mayUse("203.0.113.99:9001",MINE,false));
        assertTrue(Relays.mayUse("relay.example.org:9001",MINE,false));
        // Discovery letting go never takes the owner's, nor the shipped ones while they are in use.
        assertTrue(Relays.keep("relay.example.org:9001",MINE,false,SHIPPED));
        assertTrue(Relays.keep("203.0.113.1:9501",MINE,true,SHIPPED));
        assertFalse(Relays.keep("203.0.113.1:9501",MINE,false,SHIPPED));
        assertFalse(Relays.keep("203.0.113.99:9001",MINE,true,SHIPPED));
        // Switched off: everything that is not the owner's is let go; on, nothing.
        List<String> known=Arrays.asList("203.0.113.1:9501","relay.example.org:9001","203.0.113.99:9001");
        assertEquals(Arrays.asList("203.0.113.1:9501","203.0.113.99:9001"),Relays.letGo(known,MINE,false));
        assertEquals(Collections.emptyList(),Relays.letGo(known,MINE,true));
    }

    @Test public void roomIsMadeByTheLowestThatIsNotTheOwners() {
        assertNull(Relays.makeRoom(Arrays.asList("relay.example.org:9001","203.0.113.1:9501"),MINE,2));
        assertEquals("203.0.113.2:9501",Relays.makeRoom(Arrays.asList("203.0.113.1:9501","relay.example.org:9001","203.0.113.2:9501"),MINE,2));
        assertEquals("203.0.113.1:9501",Relays.makeRoom(Arrays.asList("203.0.113.1:9501","relay.example.org:9001","[2001:db8::7]:9001"),MINE,2));
        assertNull(Relays.makeRoom(Arrays.asList("relay.example.org:9001","[2001:db8::7]:9001","relay.example.org:9002"),
            Arrays.asList("relay.example.org:9001","[2001:db8::7]:9001","relay.example.org:9002"),2));
    }

    @Test public void theAddressesHandedOutNameTheOwnersRelaysFirst() {
        List<String> handed=Arrays.asList("MxAAA@203.0.113.1:9501","MxBBB@relay.example.org:9001","MxCCC@203.0.113.2:9501","MxDDD@[2001:db8::7]:9001");
        assertEquals(Arrays.asList("MxBBB@relay.example.org:9001","MxDDD@[2001:db8::7]:9001","MxAAA@203.0.113.1:9501","MxCCC@203.0.113.2:9501"),
            Relays.ownFirst(handed,MINE));
        assertEquals(handed,Relays.ownFirst(handed,Collections.emptyList()));
    }

    @Test public void whatItSays() {
        assertEquals("",Relays.state(false,false));
        assertEquals("Connected",Relays.state(true,true));
        assertEquals("Not answering",Relays.state(true,false));
        assertTrue(Relays.line(true,0,0,false).contains("shipped with Mininotes"));
        assertTrue(Relays.line(true,1,1,false).startsWith("Your relays are used first"));
        assertEquals("Only your relays are used.",Relays.line(false,2,1,true));
        // Off with none of theirs answering: what does happen, on each app.
        assertTrue(Relays.line(false,0,0,false).startsWith("Notes go only directly"));
        assertTrue(Relays.line(false,1,0,false).contains("through your PC"));
        assertTrue(Relays.line(false,1,0,true).contains("reach this PC"));
    }

    @Test public void howNotesTravelIsKeptAndSaid() {
        // Only the word kept for it means only between the owner's devices: a device that never saw this uses helpers.
        assertTrue(Relays.onlyMine(Relays.kept(true)));
        assertFalse(Relays.onlyMine(Relays.kept(false)));
        for(String other:new String[]{null,"","helpers","MINE","on","off"})assertFalse(String.valueOf(other),Relays.onlyMine(other));
        assertEquals("Only between my devices",Relays.ONLY_MINE);
        assertEquals("Also through helpers when needed",Relays.HELPERS);
        String mine=Relays.travelLine(true);
        assertTrue(mine.contains("Phones away from home cannot reach each other directly"));
        assertTrue(mine.contains("same Wi-Fi"));
        assertTrue(mine.contains("wait until the devices meet"));
        assertTrue(Relays.travelLine(false).contains("relays"));
    }
}
