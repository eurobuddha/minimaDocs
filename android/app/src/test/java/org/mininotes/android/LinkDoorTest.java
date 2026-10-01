// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import static org.junit.Assert.*;

import com.eurobuddha.maxima.core.MaximaNode;
import com.eurobuddha.maxima.core.codec.MiniData;
import com.eurobuddha.maxima.core.crypto.Hashes;
import com.eurobuddha.maxima.core.identity.MaximaIdentity;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Four real nodes on this machine, door to door with no relay. A shares a note with B and C, having paired with
 * both; B and C never paired with each other. The list that comes with the note carries the key to seal for each of
 * them, so B links with C through it: meets C's door and asks to be answered. C's copy came too long for the keys to
 * ride, so C cannot reach B by itself; it takes B's hello because its own list from A names B's key, and answers. B's edit then goes straight to C, sealed for C, and is taken as B's. A fourth
 * device, D, that knows C's address and even C's key to seal for, is a member of nothing C has: its hello is not taken,
 * nothing goes back, and what it sends is not written down. Everything is sealed as the app seals it; what each end
 * decides is {@link Linking}'s, {@link Parcel}'s and {@link Arriving}'s, as {@code Post} asks them. See docs/SHARING.md,
 * Linked through what you share.
 */
public class LinkDoorTest {
    private static final String APPLICATION="mininotes.v1";
    private static final byte[] NOTE=new byte[16];
    static{Arrays.fill(NOTE,(byte)7);}

    /** One device: its node, its keys, and what its notebook says about the note and the devices it knows. */
    private static final class Device {
        final String name; final MaximaNode node; final MaximaIdentity id; final KeyPair sign,agree;
        final BlockingQueue<byte[]> at=new LinkedBlockingQueue<>();
        int door;
        /** Devices it can seal for, by key as lists write them; and those linked through a list not answered yet. */
        final Map<String,Device> linked=new HashMap<>(); final Set<String> waiting=new HashSet<>();
        /** The note's list of people here, and the note. */
        final List<Sharing.Rule> rows=new ArrayList<>();
        String text; long revision; boolean owner;
        Device(String name,int seed) throws Exception {
            this.name=name;sign=Envelope.keys();agree=Envelope.keys();
            byte[] s=new byte[32];Arrays.fill(s,(byte)seed);
            id=MaximaIdentity.fromSeed(new MiniData(s));node=new MaximaNode(id,"test",2);
            node.setMessageListener((m,unused)->{if(m!=null&&m.mApplication!=null&&APPLICATION.equals(m.mApplication.toString()))at.add(m.mData.getBytes());});
        }
        String key(){return Base64.getEncoder().encodeToString(sign.getPublic().getEncoded());}
        String address(){return Direct.door(id.publicKeyHex(),"127.0.0.1",door);}
        Parcel.Member member(int level,long changed){return new Parcel.Member(key(),address(),name,level,changed,Point.shorten(agree.getPublic()));}
    }

    private Device a,b,c,d;

    @Before public void fourNodes() throws Exception {
        Hashes.setSha3(Sha3::of);
        a=new Device("Test phone",41);b=new Device("Test tablet",42);c=new Device("Test PC",43);d=new Device("Somebody else",44);
        for(Device one:List.of(a,b,c,d)){one.door=one.node.startDirect(0);assertTrue(one.door>0);}
        // A paired with B and with C, by their codes, both ways round. B and C never met.
        pair(a,b);pair(a,c);
    }

    @After public void stop() throws Exception {for(Device one:new Device[]{a,b,c,d})if(one!=null)one.node.stop();}

    private static void pair(Device one,Device other) throws Exception {
        meet(one,other);meet(other,one);
        one.linked.put(other.key(),other);other.linked.put(one.key(),one);
    }

    /** Met at its door on this network, as Node.introduce does while notes go only between the owner's devices. */
    private static void meet(Device from,Device to) throws Exception {
        assertEquals(Direct.key(to.id.publicKeyHex()),Node.introduceHere(from.node,to.address()));
    }

    private static void send(Device from,Device to,long revision,byte[] plain) throws Exception {
        byte[] sealed=Envelope.seal(NOTE,revision,System.currentTimeMillis(),plain,from.sign,to.agree.getPublic());
        assertTrue(Direct.send(from.node,from.node.contact(Direct.key(to.id.publicKeyHex())),APPLICATION,sealed,null,false).isOk());
    }

    /** The note as it goes from one device: its words, its list of people with their keys, and a list of files. */
    private static byte[] parcel(Device from,List<Parcel.Member> members) throws Exception {return parcel(from,members,true);}

    /** @param files whether a list of files goes, which the keys ride behind: a note too long for it goes without either */
    private static byte[] parcel(Device from,List<Parcel.Member> members,boolean files) throws Exception {
        List<Parcel.Member> all=new ArrayList<>(members);
        // And the sender, an admin of anything it sends, as NoteStore.travelling writes it.
        all.add(from.member(Sharing.Level.ADMIN.said(),1L));
        return Parcel.wrap(new Parcel.Sent("","","","","Plans",from.text,true,all,"PAGE","note-1",true,-1L,true,files?List.of():null,1L));
    }

    /** One thing arriving, opened, and acted on as Post acts on it; what it came from, or null for nobody known. */
    private Device hear(Device at) throws Exception {
        byte[] came=at.at.poll(10,TimeUnit.SECONDS);
        assertNotNull("something came to "+at.name+"'s door",came);
        Envelope.Opened opened=Envelope.open(came,at.agree.getPrivate());
        Hello.Said hello=Hello.open(opened.text);
        if(hello!=null) {
            assertArrayEquals(Envelope.fingerprint(Keys.publicKey(hello.signing)),opened.sender);
            assertTrue(Linking.isLink(hello));
            String key=Persons.key(hello.signing);
            Device known=at.linked.get(key);
            if(known==null) {
                // A device not linked here: taken only where a list here names its key.
                Sharing.Rule named=Linking.names(at.rows,hello.signing);
                if(named==null)return null;
                known=byAddress(named.address);
                at.linked.put(key,known);
                meet(at,known);
            }
            at.waiting.remove(key);
            if(Linking.asks(hello))send(at,known,0,Hello.wrap(Linking.hello(at.name,at.address(),Point.shorten(at.agree.getPublic()),Point.shorten(at.sign.getPublic()),false)));
            return known;
        }
        Device from=null;
        for(Device one:at.linked.values())if(Arrays.equals(Envelope.fingerprint(one.sign.getPublic()),opened.sender))from=one;
        // Signed by no device linked here, or linked and not answered yet: nothing is written down.
        if(from==null||at.waiting.contains(from.key()))return null;
        Parcel.Sent parcel=Parcel.open(opened.text);
        assertNotNull(parcel);
        boolean say=from.owner||hasASay(at,from);
        fold(at,parcel);
        // Whoever has a say, and only they, links this device with everybody on their list.
        for(Parcel.Member one:Linking.toLink(parcel.members,say,at.key(),at.linked.keySet())) {
            Device them=byAddress(one.address);
            assertArrayEquals("the key to seal for them came on the list",Point.shorten(them.agree.getPublic()),one.agreement);
            at.linked.put(one.key,them);at.waiting.add(one.key);
            meet(at,them);
            send(at,them,0,Hello.wrap(Linking.hello(at.name,at.address(),Point.shorten(at.agree.getPublic()),Point.shorten(at.sign.getPublic()),true)));
        }
        // What they may do with it here decides whether their words are written down.
        Sharing.Rule may=null;
        for(Sharing.Rule rule:at.rows)if(rule.key.equals(from.key()))may=rule;
        if(!from.owner&&(may==null||!may.level.writes()))return from;
        Arriving.Decision said=Arriving.weigh(at.text,at.revision,null,0,parcel.body,opened.revision);
        if(said.text!=null){at.text=said.text;at.revision=said.revision;}
        return from;
    }

    /** Per device on the list, the later decision stands; this device is never on its own list. */
    private static void fold(Device at,Parcel.Sent parcel) {
        for(Parcel.Member one:parcel.members) {
            if(one.key.equals(at.key()))continue;
            Sharing.Rule here=null;
            for(Sharing.Rule rule:at.rows)if(rule.key.equals(one.key))here=rule;
            if(here!=null&&here.changed>=one.changed)continue;
            at.rows.remove(here);
            at.rows.add(new Sharing.Rule(Sharing.Scope.PAGE,"note-1",one.address,Sharing.Level.of(one.level),one.changed,one.key));
        }
    }

    private static boolean hasASay(Device at,Device from) {
        for(Sharing.Rule rule:at.rows)if(rule.key.equals(from.key()))return rule.level.shares();
        return false;
    }

    private Device byAddress(String address) {
        for(Device one:List.of(a,b,c,d))if(one.address().equals(address))return one;
        throw new AssertionError("nobody at that address");
    }

    @Test public void twoDevicesOnOneListLinkThroughItAndAStrangerIsNotTaken() throws Exception {
        // A's note, shared with B and C to write in: the list says so, with the key to seal for each.
        a.owner=true;a.text="Plans\nfirst line";a.revision=1;
        List<Parcel.Member> list=List.of(b.member(Sharing.Level.WRITE.said(),5_000L),c.member(Sharing.Level.WRITE.said(),5_000L));
        // C's copy went without a list of files - too long for one, say - and so without the keys to seal for: C's list
        // names B, by the key B signs with, and C cannot reach B by itself.
        send(a,b,1,parcel(a,list));send(a,c,1,parcel(a,list,false));

        // B hears it from the owner, and is linked with C through the list: C is written down, met, and asked.
        assertSame(a,hear(b));
        assertEquals(a.text,b.text);
        assertTrue(b.linked.containsKey(c.key()));assertTrue(b.waiting.contains(c.key()));

        // C hears A's note and links nobody, having no key to seal for B; then B's hello, which it takes because its
        // own list names B, and answers.
        assertSame(a,hear(c));
        assertFalse(c.linked.containsKey(b.key()));
        assertSame("C took B's hello: its own list names B",b,hear(c));
        assertTrue(c.linked.containsKey(b.key()));assertFalse(c.waiting.contains(b.key()));

        // B hears the answer, and C is linked at both ends: nothing waits any more, and nothing more is said.
        assertSame(c,hear(b));
        assertFalse(b.waiting.contains(c.key()));
        assertNull("an answer is not answered",c.at.poll(500,TimeUnit.MILLISECONDS));

        // B writes, and it goes straight to C, sealed for C: C takes it as B's, a writer on the list.
        b.text=b.text+"\nfrom the tablet";b.revision=2;
        send(b,c,2,parcel(b,List.of(c.member(Sharing.Level.WRITE.said(),5_000L))));
        assertSame(b,hear(c));
        assertEquals("Plans\nfirst line\nfrom the tablet",c.text);
        assertNull("nothing went by way of A",a.at.poll(500,TimeUnit.MILLISECONDS));

        // A writer's list links nobody: B names a stranger it made up, as a changed build could, and C links nothing.
        Device e=new Device("Made up",45);
        try {
            e.door=9;
            b.text=b.text+"\nagain";b.revision=3;
            send(b,c,3,parcel(b,List.of(e.member(Sharing.Level.ADMIN.said(),9_000L))));
            assertSame(b,hear(c));
            assertFalse(c.linked.containsKey(e.key()));
            assertEquals("Plans\nfirst line\nfrom the tablet\nagain",c.text);
        } finally {e.node.stop();}

        // D knows C's address, and even C's key to seal for - it once saw C's code. It meets C's door (the transport
        // takes anybody who introduces themselves) and asks to link. No list at C names D: not taken, no answer.
        meet(d,c);
        send(d,c,0,Hello.wrap(Linking.hello(d.name,d.address(),Point.shorten(d.agree.getPublic()),Point.shorten(d.sign.getPublic()),true)));
        assertNull("D's hello was not taken",hear(c));
        assertFalse(c.linked.containsKey(d.key()));
        assertNull("nothing went back to D",d.at.poll(1500,TimeUnit.MILLISECONDS));
        // And a note from D, however it names itself, is written down nowhere.
        String before=c.text;
        d.text="Plans\nwritten over";
        send(d,c,9,parcel(d,List.of(c.member(Sharing.Level.WRITE.said(),9_000L))));
        assertNull(hear(c));
        assertEquals(before,c.text);
    }
}
