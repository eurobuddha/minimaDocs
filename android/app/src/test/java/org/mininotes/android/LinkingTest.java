// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import static org.junit.Assert.*;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * Linked through what you share: who a list links with, which hello from a stranger is taken, the keys riding on
 * the list, and the words. See {@link Linking} and docs/SHARING.md, Linked through what you share.
 */
public class LinkingTest {
    private static final String B_AT="MxB0000@192.0.2.2:9601", C_AT="MxC0000@192.0.2.3:9601";

    private static String key(KeyPair pair){return Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());}
    private static byte[] seal(KeyPair pair){return Point.shorten(pair.getPublic());}

    private static Parcel.Member member(KeyPair sign,KeyPair agree,String at,int level) {
        return new Parcel.Member(key(sign),at,"Test device",level,5_000L,agree==null?null:seal(agree));
    }

    @Test public void aListFromADeviceWithASayLinksEverybodyNotLinkedYet() throws Exception {
        KeyPair me=Envelope.keys(),b=Envelope.keys(),bSeal=Envelope.keys(),c=Envelope.keys(),cSeal=Envelope.keys();
        List<Parcel.Member> list=List.of(member(me,Envelope.keys(),"MxSelf@192.0.2.1:9601",Sharing.Level.ADMIN.said()),
            member(b,bSeal,B_AT,Sharing.Level.WRITE.said()),member(c,cSeal,C_AT,Sharing.Level.READ.said()));
        List<Parcel.Member> linking=Linking.toLink(list,true,key(me),Set.of());
        assertEquals(2,linking.size());
        assertEquals(key(b),linking.get(0).key);assertArrayEquals(seal(bSeal),linking.get(0).agreement);
        assertArrayEquals(b.getPublic().getEncoded(),Linking.signing(linking.get(0)));
        // Already linked here: nobody new.
        assertEquals(List.of(key(c)),keys(Linking.toLink(list,true,key(me),Set.of(key(b)))));
        // From somebody with no say in who has the thing - a writer, a reader - nobody at all.
        assertTrue(Linking.toLink(list,false,key(me),Set.of()).isEmpty());
        assertTrue(Linking.toLink(null,true,key(me),Set.of()).isEmpty());
    }

    @Test public void nobodyTakenOffNowhereToReachOrNoKeyToSealForIsLinked() throws Exception {
        KeyPair me=Envelope.keys(),one=Envelope.keys(),seal=Envelope.keys();
        assertTrue(Linking.toLink(List.of(member(one,seal,B_AT,Sharing.Level.GONE.said())),true,key(me),Set.of()).isEmpty());
        assertTrue(Linking.toLink(List.of(member(one,seal,"  ",Sharing.Level.WRITE.said())),true,key(me),Set.of()).isEmpty());
        // A list from before keys travelled, or a member the sender never had the key of.
        assertTrue(Linking.toLink(List.of(member(one,null,B_AT,Sharing.Level.WRITE.said())),true,key(me),Set.of()).isEmpty());
        // A key to seal for that is not a key, and a signing key that is not one.
        assertTrue(Linking.toLink(List.of(new Parcel.Member(key(one),B_AT,"",2,1L,new byte[]{1,2,3})),true,key(me),Set.of()).isEmpty());
        assertTrue(Linking.toLink(List.of(new Parcel.Member("not a key",B_AT,"",2,1L,seal(seal))),true,key(me),Set.of()).isEmpty());
        // Named twice, linked once.
        assertEquals(1,Linking.toLink(List.of(member(one,seal,B_AT,2),member(one,seal,C_AT,2)),true,key(me),Set.of()).size());
    }

    @Test public void aHelloFromADeviceNotLinkedIsTakenOnlyWhereAListHereNamesItsKey() throws Exception {
        KeyPair b=Envelope.keys(),stranger=Envelope.keys();
        List<Sharing.Rule> here=new ArrayList<>(List.of(
            new Sharing.Rule(Sharing.Scope.PAGE,"note-1",B_AT,Sharing.Level.WRITE,5_000L,key(b))));
        assertNotNull(Linking.names(here,b.getPublic().getEncoded()));
        // Its key in the short form a hello carries is the same device.
        assertEquals(B_AT,Linking.names(here,Point.shorten(b.getPublic())).address);
        assertNull("somebody who only knows an address and a key to seal for is a member of nothing here",
            Linking.names(here,stranger.getPublic().getEncoded()));
        // Taken off: no longer somebody the thing links.
        here.set(0,new Sharing.Rule(Sharing.Scope.PAGE,"note-1",B_AT,Sharing.Level.GONE,6_000L,key(b)));
        assertNull(Linking.names(here,b.getPublic().getEncoded()));
        assertNull(Linking.names(List.of(),b.getPublic().getEncoded()));
        assertNull(Linking.names(here,new byte[0]));
    }

    @Test public void aLinkingHelloNamesNothingABuildFromBeforeWouldGiveOrPairBack() throws Exception {
        KeyPair sign=Envelope.keys(),seal=Envelope.keys();
        Hello.Said asking=Hello.open(Hello.wrap(Linking.hello("Test",C_AT,seal(seal),seal(sign),true)));
        assertTrue(Linking.isLink(asking));assertTrue(Linking.asks(asking));
        Hello.Said answer=Hello.open(Hello.wrap(Linking.hello("Test",C_AT,seal(seal),seal(sign),false)));
        assertTrue(Linking.isLink(answer));assertFalse(Linking.asks(answer));
        // A build from before pairs back, unasked, a hello that names nothing; and gives what one names only where the
        // scope is one of its own and it offered it. This names something, and no scope any build shares.
        assertFalse(asking.target.isEmpty());
        for(Sharing.Scope one:Sharing.Scope.values())assertNotEquals(one.name(),asking.scope);
        assertFalse(Linking.isLink(new Hello.Said("Test",C_AT,seal(seal),seal(sign),"","",false)));
    }

    @Test public void askedAgainLessAndLessOften() {
        long at=1_000_000L;
        assertTrue(Linking.due(0,0,at));
        assertFalse(Linking.due(at,1,at+59_000));assertTrue(Linking.due(at,1,at+60_000));
        assertFalse(Linking.due(at,3,at+3*60_000));assertTrue(Linking.due(at,3,at+4*60_000));
        assertFalse(Linking.due(at,9,at+29*60_000));assertTrue(Linking.due(at,9,at+30*60_000));
        assertTrue("a clock put back",Linking.due(at,4,at-1));
    }

    @Test public void theKeysToSealForRideBehindTheFilesAndOnlyThere() throws Exception {
        KeyPair b=Envelope.keys(),bSeal=Envelope.keys(),c=Envelope.keys();
        List<Parcel.Member> keyed=List.of(member(b,bSeal,B_AT,2),member(c,null,C_AT,1));
        List<Parcel.Member> bare=List.of(new Parcel.Member(key(b),B_AT,"Test device",2,5_000L),new Parcel.Member(key(c),C_AT,"Test device",1,5_000L));
        List<Enclosure.Listed> files=List.of(new Enclosure.Listed("f-1","a.txt","text/plain",3,""));
        Parcel.Sent with=sent(keyed,files), without=sent(bare,files);
        byte[] out=Parcel.wrap(with);
        // What comes before the keys is byte for byte what a build that carries files writes, and it stops reading there.
        byte[] before=Parcel.wrap(without);
        assertArrayEquals(before,Arrays.copyOf(out,before.length));
        assertTrue("and with a key to give, more after it",out.length>before.length);
        Parcel.Sent in=Parcel.open(out);
        assertArrayEquals(seal(bSeal),in.members.get(0).agreement);
        assertEquals(0,in.members.get(1).agreement.length);
        assertEquals(1,in.files.size());
        // No list of files, no keys: a build from before would read them as a list of files, fail, and take the
        // note for bare text written over somebody's words.
        assertArrayEquals(Parcel.wrap(sent(bare,null)),Parcel.wrap(sent(keyed,null)));
        assertEquals(0,Parcel.open(Parcel.wrap(sent(keyed,null))).members.get(0).agreement.length);
        // Keys that do not read are left out alone: the note, its list and its files are whole without them.
        Parcel.Sent cut=Parcel.open(Arrays.copyOf(out,out.length-10));
        assertNotNull(cut);assertEquals("Body",cut.body);assertEquals(2,cut.members.size());assertEquals(1,cut.files.size());
        assertEquals(0,cut.members.get(0).agreement.length);
    }

    private static Parcel.Sent sent(List<Parcel.Member> members,List<Enclosure.Listed> files) {
        return new Parcel.Sent("c","C","b","B","Title","Body",true,members,"PAGE","note-1",true,1L,true,files,42L);
    }

    private static List<String> keys(List<Parcel.Member> members) {
        List<String> out=new ArrayList<>();for(Parcel.Member one:members)out.add(one.key);return out;
    }

    @Test public void whatIsSaid() {
        Unsent.Problem waiting=new Unsent.Problem(Unsent.Why.LINKING,"Graphene","Transfer","Transfer","",B_AT);
        assertEquals("Linking with Graphene… it goes once they answer.",Unsent.said(waiting,"this PC"));
        assertEquals("Link with Graphene",Unsent.button(waiting));
        assertEquals(Unsent.Fix.PAIR,Unsent.fix(waiting));
        assertTrue(Unsent.listed(waiting));
        assertEquals("Take them off “Transfer”",Unsent.takeOff(waiting));
        assertEquals("Linking with Graphene",Unsent.title(List.of(waiting),0,1));
        assertEquals("Linked through “Transfer”",Linking.through("Transfer"));
        assertEquals("Linked through something it no longer has. Nothing is sent to it.",Linking.through(""));
        SyncStatus.Person linking=new SyncStatus.Person(B_AT,"Graphene",SyncMark.GONE,false,"Transfer",null,true);
        assertEquals("Linking with Graphene… it goes once they answer.",linking.notLinked("this phone"));
        SyncStatus.Person listed=new SyncStatus.Person(B_AT,"Graphene",SyncMark.GONE,false,"Transfer",null);
        assertEquals(Unsent.notLinked("Graphene","Transfer","this phone"),listed.notLinked("this phone"));
    }
}
