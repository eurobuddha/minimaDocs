package org.mininotes.android;

import static org.junit.Assert.*;

import com.eurobuddha.maxima.core.MaximaNode;
import com.eurobuddha.maxima.core.codec.MiniData;
import com.eurobuddha.maxima.core.contacts.Contact;
import com.eurobuddha.maxima.core.crypto.Hashes;
import com.eurobuddha.maxima.core.identity.MaximaIdentity;
import com.eurobuddha.maxima.core.identity.MxAddress;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Three real nodes on this machine, door to door with no relay: two devices of one owner, bonded, say they know about
 * persons, send each other their cards, and end as one person, each calling the other by its own name. A third device,
 * somebody else's and paired with both, hears that they know about persons and is sent no card. Everything is sealed
 * as the app seals it; what each end decides is {@link Persons}', as {@code Post} asks it. See docs/SHARING.md, One
 * person on every device.
 */
public class PersonsDoorTest {
    private static final String APPLICATION="mininotes.v1";

    /** One device: its node, its keys, who it thinks it is, and what it has written down about the others. */
    private static final class Device {
        final MaximaNode node; final MaximaIdentity id; final KeyPair sign,agree;
        final BlockingQueue<byte[]> at=new LinkedBlockingQueue<>();
        int door;
        Persons.Me me;
        /** By key in one form: marked as the owner's here, said it reads cards, said this device is its owner's too. */
        final Map<String,Boolean> mine=new HashMap<>(),reads=new HashMap<>(),mineThere=new HashMap<>();
        final Map<String,String> names=new HashMap<>();final Map<String,Long> named=new HashMap<>();
        Device(int seed,Persons.Me me) throws Exception {
            sign=Envelope.keys();agree=Envelope.keys();
            byte[] s=new byte[32];Arrays.fill(s,(byte)seed);
            id=MaximaIdentity.fromSeed(new MiniData(s));node=new MaximaNode(id,"test",2);this.me=me;
            node.setMessageListener((m,unused)->{if(m!=null&&m.mApplication!=null&&APPLICATION.equals(m.mApplication.toString()))at.add(m.mData.getBytes());});
        }
        String key(){return Persons.key(sign.getPublic().getEncoded());}
        Persons.Device self(){return new Persons.Device(Point.shorten(sign.getPublic()),Point.shorten(agree.getPublic()),
            MxAddress.make(id.publicKeyData())+"@127.0.0.1:"+door,me.device,Persons.ACTIVE,me.deviceNamed);}
    }

    private Device a,b,c;

    @Before public void threeNodes() throws Exception {
        Hashes.setSha3(Sha3::of);
        // A phone and a PC of one owner - the phone's id made later - and somebody else's laptop.
        a=new Device(31,new Persons.Me("ffffffffffffffffffffffffffffff01",5_000,null,"Pixel test",0,"Test phone",0));
        b=new Device(32,new Persons.Me("0000000000000000000000000000000b",1_000,null,"Ana",900,"DESKTOP-TEST",0));
        c=new Device(33,new Persons.Me("77777777777777777777777777777777",2_000,null,"Bo",10,"Bo's laptop",0));
        for(Device one:List.of(a,b,c)){one.door=one.node.startDirect(0);assertTrue(one.door>0);}
        for(Device one:List.of(a,b,c))for(Device other:List.of(a,b,c))if(one!=other)one.node.storeContact(known(other));
        // A and B bonded as one owner's devices ("Connect my other device"), each marking the other as theirs;
        // both paired with C, which is nobody's device of theirs.
        a.mine.put(b.key(),true);b.mine.put(a.key(),true);
        a.mine.put(c.key(),false);b.mine.put(c.key(),false);c.mine.put(a.key(),false);c.mine.put(b.key(),false);
        // Each wrote down the other's name as its code gave it: the owner's name, which is what a code carries.
        a.names.put(b.key(),"Ana");a.named.put(b.key(),0L);b.names.put(a.key(),"Pixel test");b.named.put(a.key(),0L);
    }

    private static Contact known(Device who) {
        Contact them=new Contact(who.id.publicKeyHex());
        them.setAddresses(List.of(MxAddress.make(who.id.publicKeyData())+"@127.0.0.1:"+who.door));
        return them;
    }

    @After public void stop() throws Exception {for(Device one:new Device[]{a,b,c})if(one!=null)one.node.stop();}

    private static void send(Device from,Device to,byte[] page,long revision,byte[] plain) throws Exception {
        byte[] sealed=Envelope.seal(page,revision,System.currentTimeMillis(),plain,from.sign,to.agree.getPublic());
        assertTrue(Direct.send(from.node,from.node.contact(to.id.publicKeyHex()),APPLICATION,sealed).isOk());
    }

    /** "This build knows about persons", said as Post says it: the one that says so, to a device marked as the owner's. */
    private static void tellPersons(Device from,Device to) throws Exception {
        send(from,to,new byte[16],0,Receipt.wrap(from.mine.get(to.key())?Receipt.PERSONS_MINE:Receipt.PERSONS));
    }

    /** What arrives at a device, opened, checked as the sender's, and acted on as Post acts on it. */
    private static void hear(Device at,Device from) throws Exception {
        byte[] came=at.at.poll(10,TimeUnit.SECONDS);
        assertNotNull("something came to the door",came);
        Envelope.Opened opened=Envelope.open(came,at.agree.getPrivate());
        assertArrayEquals(Envelope.fingerprint(from.sign.getPublic()),opened.sender);
        int about=Receipt.open(opened.text);
        if(about==Receipt.PERSONS||about==Receipt.PERSONS_MINE) {
            at.reads.put(from.key(),true);at.mineThere.put(from.key(),about==Receipt.PERSONS_MINE);return;
        }
        assertTrue("only a card is left to be",Persons.isCard(opened.text));
        Persons.Taken taken=Persons.take(at.me,Persons.open(opened.text),at.mine.getOrDefault(from.key(),false),
            from.sign.getPublic().getEncoded(),at.sign.getPublic().getEncoded(),at.named);
        assertNotNull("a card from a device that is the owner's at both ends is believed",taken);
        at.me=taken.me;
        for(Map.Entry<String,Persons.Device> one:taken.names.entrySet()){at.names.put(one.getKey(),one.getValue().name);at.named.put(one.getKey(),one.getValue().decided);}
    }

    /** The cards a device sends this round, as Post decides: only where Persons says they go. */
    private static List<Device> sendCards(Device from,Device... others) throws Exception {
        List<Device> went=new ArrayList<>();
        List<Persons.Device> own=new ArrayList<>();
        for(Device one:others)if(from.mine.getOrDefault(one.key(),false))own.add(one.self());
        Persons.Card card=Persons.card(from.me,from.self(),own);
        for(Device to:others) {
            if(!Persons.goesTo(from.mine.getOrDefault(to.key(),false),from.reads.getOrDefault(to.key(),false),from.mineThere.getOrDefault(to.key(),false)))continue;
            send(from,to,Persons.idBytes(from.me.id),card.newest(),Persons.wrap(card));
            went.add(to);
        }
        return went;
    }

    @Test public void twoDevicesOfOneOwnerEndAsOnePersonAndSomebodyElsesIsSentNoCard() throws Exception {
        // Everybody says what they know about persons to everybody paired with them.
        tellPersons(a,b);hear(b,a);tellPersons(b,a);hear(a,b);
        tellPersons(a,c);hear(c,a);tellPersons(b,c);hear(c,b);
        tellPersons(c,a);hear(a,c);tellPersons(c,b);hear(b,c);
        assertTrue(a.mineThere.get(b.key()));assertTrue(b.mineThere.get(a.key()));
        assertFalse(c.mineThere.get(a.key()));assertFalse(a.mineThere.get(c.key()));

        // The cards: from each of the two to the other, and to nobody else.
        assertEquals(List.of(b),sendCards(a,b,c));
        assertEquals(List.of(a),sendCards(b,a,c));
        hear(b,a);hear(a,b);
        // C paired with both and was told they know about persons, and nothing more came to it.
        assertNull("no card reached somebody else's device",c.at.poll(1500,TimeUnit.MILLISECONDS));
        assertTrue(sendCards(c,a,b).isEmpty());

        // One person: the PC's id was made first, so the phone took it and kept its own as an alias.
        assertEquals(b.me.id,a.me.id);
        assertEquals(List.of("ffffffffffffffffffffffffffffff01"),a.me.aliases);
        // Each calls the other by the other's own name, not by the owner's name its code carried.
        assertEquals("DESKTOP-TEST",a.names.get(b.key()));
        assertEquals("Test phone",b.names.get(a.key()));
        // And the owner's name chosen on the PC is their name on the phone too.
        assertEquals("Ana",a.me.name);assertEquals(900,a.me.named);

        // The phone is renamed; its next card changes the name on the PC, and a card from before changes nothing.
        byte[] before=Persons.wrap(Persons.card(a.me,a.self(),List.of(b.self())));
        a.me=new Persons.Me(a.me.id,a.me.made,a.me.aliases,a.me.name,a.me.named,"Kitchen phone",7_000);
        assertEquals(List.of(b),sendCards(a,b,c));hear(b,a);
        assertEquals("Kitchen phone",b.names.get(a.key()));
        send(a,b,Persons.idBytes(a.me.id),0,before);hear(b,a);
        assertEquals("Kitchen phone",b.names.get(a.key()));
        assertEquals(b.me.id,a.me.id);
    }
}
