package org.mininotes.android;

import static org.junit.Assert.*;

import com.eurobuddha.maxima.core.MaximaNode;
import com.eurobuddha.maxima.core.codec.MiniData;
import com.eurobuddha.maxima.core.contacts.Contact;
import com.eurobuddha.maxima.core.crypto.Hashes;
import com.eurobuddha.maxima.core.identity.MaximaIdentity;
import com.eurobuddha.maxima.core.store.FileStore;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Notes only between the owner's devices (Relays.ONLY_MINE), with real nodes on this machine and no internet:
 * a note goes door to door; with the door shut nothing at all is dialled - not the stand-in relay the other
 * device is known by, not the transport's fan-out - and the send fails as a send that waits; pairing on the same
 * network makes each device known to the other with no relay between; and a node's relays are let go of and
 * taken up again as the choice is switched while it runs. The addresses a relay could be at are documentation
 * ones where they are never dialled, and loopback where a stand-in counts who dials it.
 */
public class OnlyMineTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final String APPLICATION="mininotes.v1";
    private static final byte[] LOOPBACK={127,0,0,1};
    private static final List<String> SHIPPED=Arrays.asList("203.0.113.1:9501","203.0.113.2:9501");
    private final List<MaximaNode> nodes=new ArrayList<>();
    private final List<ServerSocket> sockets=new ArrayList<>();
    private MaximaIdentity idA, idB;
    private MaximaNode a, b;
    private int doorB;
    private final AtomicInteger relayDialled=new AtomicInteger();
    private String relayAddressOfB;
    private final BlockingQueue<byte[]> arrivedAtA=new LinkedBlockingQueue<>(), arrivedAtB=new LinkedBlockingQueue<>();
    private Nearby nearA;

    @Before public void twoDevices() throws Exception {
        Hashes.setSha3(Sha3::of);
        idA=identity(41);idB=identity(42);
        a=node(idA,arrivedAtA);b=node(idB,arrivedAtB);
        doorB=b.startDirect(0);
        assertTrue(doorB>0);
        // A stand-in for the relay B is known by: it counts who dials it, and says nothing.
        ServerSocket relay=listening();
        Thread counting=new Thread(()->{
            while(!relay.isClosed())try(Socket one=relay.accept()){relayDialled.incrementAndGet();}catch(Exception closed){/* done */}
        });counting.setDaemon(true);counting.start();
        relayAddressOfB=idB.contactAddress("127.0.0.1:"+relay.getLocalPort());
        nearA=new Nearby(a,()->-1,"127.0.0.1",0);
        nearA.pairedWith(Set.of(Direct.key(idB.publicKeyHex())));
    }

    @After public void stop() throws Exception {
        if(nearA!=null)nearA.close();
        for(ServerSocket one:sockets)one.close();
        for(MaximaNode one:nodes)try{one.stop();}catch(RuntimeException ignored){/* its store only */}
    }

    private static MaximaIdentity identity(int fill){byte[] s=new byte[32];Arrays.fill(s,(byte)fill);return MaximaIdentity.fromSeed(new MiniData(s));}

    private MaximaNode node(MaximaIdentity id,BlockingQueue<byte[]> arrived) {
        MaximaNode made=new MaximaNode(id,"test",2);
        made.setMessageListener((message,msgid)->{
            if(message!=null&&message.mApplication!=null&&APPLICATION.equals(message.mApplication.toString()))
                arrived.add(message.mData.getBytes());
        });
        nodes.add(made);
        return made;
    }

    private ServerSocket listening() throws Exception {
        ServerSocket one=new ServerSocket();one.bind(new InetSocketAddress("127.0.0.1",0));sockets.add(one);return one;
    }

    /** B as A knows it after pairing in the other way: by its relay. */
    private Contact bByItsRelay() {
        Contact known=new Contact(idB.publicKeyHex());known.name="B";
        known.setAddresses(List.of(relayAddressOfB));
        a.storeContact(known);
        return a.contact(idB.publicKeyHex());
    }

    /** A hears B say where its door is, as it would a broadcast at home. */
    private void heardAtHome() throws Exception {
        byte[] said=Direct.announce(idB.keyPair(),LOOPBACK,doorB,System.currentTimeMillis());
        assertTrue(nearA.heard(said,said.length,InetAddress.getByAddress(LOOPBACK)));
    }

    private static byte[] note(String words){return words.getBytes(StandardCharsets.UTF_8);}

    @Test public void aNoteGoesDoorToDoorAndNoRelayIsGivenACopy() throws Exception {
        Contact them=bByItsRelay();
        heardAtHome();
        byte[] sent=note("a synthetic sealed note");
        assertTrue(Direct.send(a,them,APPLICATION,sent,null,false).isOk());
        assertArrayEquals(sent,arrivedAtB.poll(10,TimeUnit.SECONDS));
        assertEquals("no relay was dialled",0,relayDialled.get());
    }

    @Test public void withTheDoorShutNothingIsDialledAndTheSendWaits() throws Exception {
        Contact them=bByItsRelay();
        heardAtHome();
        b.stopDirect();
        AtomicInteger homesAsked=new AtomicInteger();
        Direct.Leave noHome=data->{homesAsked.incrementAndGet();return false;};
        IllegalStateException waits=assertThrows(IllegalStateException.class,
            ()->Direct.send(a,them,APPLICATION,note("waits for the devices to meet"),noHome,false));
        // What Post shows, and why nothing is marked as handed over: a send that throws counts as not sent, so
        // the note stays owed and the outbox tries it again as it does anything nobody answered.
        assertEquals(Direct.WAITS,waits.getMessage());
        assertEquals("its home was asked, being one of the owner's doors",1,homesAsked.get());
        Thread.sleep(300);
        assertEquals("the relay it is known by was never dialled",0,relayDialled.get());
        assertNull(arrivedAtB.poll(200,TimeUnit.MILLISECONDS));
        // The same send with helpers goes the long way, which is what the stand-in is there to count.
        assertThrows(IllegalStateException.class,()->Direct.send(a,them,APPLICATION,note("the long way"),noHome,true));
        assertTrue("with helpers, the relay is tried",relayDialled.get()>0);
    }

    /**
     * What a phone's log showed as "could not answer: IllegalStateException", twice for every note: an answer to a
     * device whose door is not heard here - the PC that dials the phone at its door while the phone hears nothing
     * of it, and the same again for the copy a carrier brought. It is a wait, not a failure: kept, and said at the
     * first round that finds a way, with no relay dialled meanwhile.
     */
    @Test public void anAnswerWithNoWayIsKeptAndGoesWhenTheirDoorIsHeard() throws Exception {
        Contact them=bByItsRelay();
        Direct.Leave noHome=data->false;
        byte[] answer=note("a synthetic answer");
        IllegalStateException noWay=assertThrows(IllegalStateException.class,
            ()->Direct.send(a,them,APPLICATION,answer,noHome,false));
        assertEquals(Unsent.Why.NOT_IN_REACH,Unsent.of(noWay.getMessage()));
        assertEquals("the log says the kind of reason, not the exception's name","not in reach",Unsent.reason(noWay));
        Outbox.Answers kept=new Outbox.Answers();
        long now=System.currentTimeMillis();
        byte[] page=new byte[16];page[15]=7;
        assertEquals(1,kept.keep(idB.publicKeyHex(),page,39,true,now));
        assertEquals("the copy a carrier brought is answered again: still one",1,kept.keep(idB.publicKeyHex(),page,39,true,now));
        List<byte[]> said=new ArrayList<>();
        Outbox.Answers.Say say=one->{
            if(!Direct.send(a,them,APPLICATION,answer,noHome,false).isOk())throw new IllegalStateException("not taken");
            said.add(one.page);
        };
        assertArrayEquals("nothing found a way yet, so it is kept",new int[]{0,1},kept.sayAll(say,now));
        heardAtHome();
        assertArrayEquals("their door heard: it goes, and nothing is kept",new int[]{1,0},kept.sayAll(say,now));
        assertArrayEquals(page,said.get(0));
        assertArrayEquals(answer,arrivedAtB.poll(10,TimeUnit.SECONDS));
        Thread.sleep(200);
        assertEquals("no relay was dialled, waiting or going",0,relayDialled.get());
    }

    @Test public void pairingOnTheSameNetworkMakesEachKnownWithNoRelay() throws Exception {
        int doorA=a.startDirect(0);
        assertTrue(doorA>0);
        // B's pairing code, made while notes go only between its owner's devices, carries its door here.
        String codeOfB=Direct.door(idB.publicKeyHex(),"127.0.0.1",doorB);
        assertTrue(Pairing.reachable(codeOfB));
        String key=Node.introduceHere(a,codeOfB);
        assertEquals(Direct.key(idB.publicKeyHex()),key);
        assertNotNull("A knows B at once",a.contact(key));
        assertNotNull("and where B's door is",a.lanAddressFor(key));
        // B learnt A from the card A left at its door.
        long until=System.currentTimeMillis()+5000;
        while(b.contact(idA.publicKeyHex())==null&&System.currentTimeMillis()<until)Thread.sleep(20);
        assertNotNull(b.contact(idA.publicKeyHex()));
        // A note, A to B, straight away.
        byte[] there=note("from A, just paired");
        assertTrue(Direct.send(a,a.contact(key),APPLICATION,there,null,false).isOk());
        assertArrayEquals(there,arrivedAtB.poll(10,TimeUnit.SECONDS));
        // And back, once B has answered at A's door as pairing back does.
        String back=Node.introduceHere(b,Direct.door(idA.publicKeyHex(),"127.0.0.1",doorA));
        assertEquals(Direct.key(idA.publicKeyHex()),back);
        byte[] reply=note("from B, paired back");
        assertTrue(Direct.send(b,b.contact(back),APPLICATION,reply,null,false).isOk());
        assertArrayEquals(reply,arrivedAtA.poll(10,TimeUnit.SECONDS));
        assertEquals(0,relayDialled.get());
    }

    @Test public void aCodeFromElsewhereIsNotDialledAtAll() throws Exception {
        long began=System.currentTimeMillis();
        IllegalStateException refused=assertThrows(IllegalStateException.class,
            ()->Node.introduceHere(a,Direct.door(idB.publicKeyHex(),"203.0.113.5",9601)));
        assertEquals(Node.ON_THE_SAME_WIFI,refused.getMessage());
        assertThrows(IllegalStateException.class,()->Node.introduceHere(a,relayAddressOfB.replace("127.0.0.1","198.51.100.7")));
        assertTrue("refused before anything was dialled",System.currentTimeMillis()-began<1000);
        assertTrue(a.contacts().isEmpty());
    }

    @Test public void noRelayIsEverACandidateNotTheShippedNorSavedNorGossiped() throws Exception {
        MaximaNode up=node(identity(43),new LinkedBlockingQueue<>());
        // Everything else says yes - the owner's relay, the public relays on - and none of it counts.
        Relays.Choice chosen=new Relays.Choice(up,Collections.singletonList("203.0.113.7:9001"),true,true,2,500,SHIPPED);
        FileStore kept=new FileStore(temp.newFolder());kept.put("peers","203.0.113.9:9001","0");kept.flush();
        up.setStore(kept);
        assertFalse(chosen.apply(System.currentTimeMillis()));
        assertTrue(up.pool().knownByScore().isEmpty());
        assertTrue(up.pool().activeHosts().isEmpty());
        up.discovery().addPeer("203.0.113.10:9001");
        assertEquals("discovery is stopped",0,up.discovery().unverifiedCount());
        assertTrue(up.myAddresses().isEmpty());
    }

    @Test public void switchedLiveFromHelpersToOnlyMineAndBack() throws Exception {
        // The owner's relay: another node's door, which greets as a relay does and is attached to as one.
        MaximaNode relayLike=node(identity(44),new LinkedBlockingQueue<>());
        String mine="127.0.0.1:"+relayLike.startDirect(0);
        MaximaNode up=node(identity(45),new LinkedBlockingQueue<>());
        boolean was=MaximaNode.ALLOW_ALL_IP;
        MaximaNode.ALLOW_ALL_IP=true;
        try {
            Relays.Choice chosen=new Relays.Choice(up,Collections.singletonList(mine),false,false,2,3000,SHIPPED);
            assertTrue(chosen.apply(System.currentTimeMillis()));
            assertTrue(chosen.attached(mine));
            assertFalse(up.myAddresses().isEmpty());
            // Only between my devices, while running: let go of at once, the owner's relay too, and not taken up
            // again round after round - but still kept as theirs, for coming back to.
            chosen.choose(Collections.singletonList(mine),false,true);
            assertTrue(chosen.none());
            assertTrue("it changed, so contacts would be told",chosen.apply(System.currentTimeMillis()));
            assertTrue(up.pool().activeHosts().isEmpty());
            assertTrue(up.pool().knownByScore().isEmpty());
            for(int round=1;round<=3;round++)assertFalse(chosen.apply(System.currentTimeMillis()+round*Relays.REST));
            assertTrue(up.pool().activeHosts().isEmpty());
            assertTrue(up.myAddresses().isEmpty());
            assertEquals(Collections.singletonList(mine),chosen.own());
            // Helpers again: the owner's relay is attached at the next apply.
            chosen.choose(Collections.singletonList(mine),false,false);
            assertTrue(chosen.apply(System.currentTimeMillis()));
            assertTrue(chosen.attached(mine));
        } finally {MaximaNode.ALLOW_ALL_IP=was;}
    }
}
