package org.mininotes.android;

import static org.junit.Assert.*;

import com.eurobuddha.maxima.core.MaximaNode;
import com.eurobuddha.maxima.core.MaximaSender;
import com.eurobuddha.maxima.core.codec.MiniData;
import com.eurobuddha.maxima.core.contacts.Contact;
import com.eurobuddha.maxima.core.crypto.Hashes;
import com.eurobuddha.maxima.core.identity.MaximaIdentity;
import com.eurobuddha.maxima.core.identity.MxAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Two real nodes on this machine, no relay and no internet: a note goes from one's send straight into the
 * other's door, and the stand-in relay each knows the other by is never dialled. Then the door is shut, and
 * the same send goes the long way. See Direct and Nearby.
 *
 * <p>The door is the transport's own ({@link MaximaNode#startDirect}). It listens on every local address, as
 * it does in the app; everything here connects to it over loopback.
 */
public class DoorToDoorTest {
    private static final String APPLICATION="mininotes.v1";
    private static final byte[] LOOPBACK={127,0,0,1};
    private MaximaNode a, b;
    private MaximaIdentity idA, idB;
    private ServerSocket relay;
    private final AtomicInteger relayDialled=new AtomicInteger();
    private final BlockingQueue<byte[]> arrivedAtB=new LinkedBlockingQueue<>();
    private int doorB;
    private Contact bAsAKnowsThem;
    private Nearby nearA;

    @Before public void twoNodes() throws Exception {
        Hashes.setSha3(Sha3::of);
        // Made-up seeds: nobody's identity.
        idA=MaximaIdentity.fromSeed(new MiniData(seed(7)));idB=MaximaIdentity.fromSeed(new MiniData(seed(8)));
        a=new MaximaNode(idA,"test",2);b=new MaximaNode(idB,"test",2);
        b.setMessageListener((message,id)->{
            if(message!=null&&message.mApplication!=null&&APPLICATION.equals(message.mApplication.toString()))
                arrivedAtB.add(message.mData.getBytes());
        });
        doorB=b.startDirect(0);
        assertTrue(doorB>0);
        // A stand-in for the relay B would otherwise be reached through: it counts who dials it, and says nothing.
        relay=new ServerSocket();relay.bind(new InetSocketAddress("127.0.0.1",0));
        Thread counting=new Thread(()->{
            while(!relay.isClosed())try(Socket one=relay.accept()){relayDialled.incrementAndGet();}catch(Exception closed){/* done */}
        });counting.setDaemon(true);counting.start();
        bAsAKnowsThem=new Contact(idB.publicKeyHex());
        bAsAKnowsThem.name="B";
        bAsAKnowsThem.setAddresses(List.of(idB.contactAddress("127.0.0.1:"+relay.getLocalPort())));
        a.storeContact(bAsAKnowsThem);
        nearA=new Nearby(a,()->-1,"127.0.0.1",0);
        nearA.pairedWith(Set.of(Direct.key(idB.publicKeyHex())));
    }

    @After public void stop() throws Exception {
        if(nearA!=null)nearA.close();
        if(relay!=null)relay.close();
        if(a!=null)a.stop();
        if(b!=null)b.stop();
    }

    private static byte[] seed(int fill){byte[] s=new byte[32];Arrays.fill(s,(byte)fill);return s;}

    @Test public void heardOnTheLocalNetworkAndDeliveredToTheDoorWithNoRelay() throws Exception {
        // B says where its door is; A hears it on its own socket, as it would a broadcast.
        assertTrue(nearA.open());
        byte[] said=Direct.announce(idB.keyPair(),LOOPBACK,doorB,System.currentTimeMillis());
        try(DatagramSocket from=new DatagramSocket(new InetSocketAddress("127.0.0.1",0))) {
            from.send(new DatagramPacket(said,said.length,InetAddress.getByAddress(LOOPBACK),nearA.hearing()));
        }
        long until=System.currentTimeMillis()+5000;
        while(a.lanAddressFor(idB.publicKeyHex())==null&&System.currentTimeMillis()<until)Thread.sleep(20);
        assertEquals(MxAddress.make(idB.publicKeyData())+"@127.0.0.1:"+doorB,a.lanAddressFor(idB.publicKeyHex()));
        assertEquals(1,nearA.near());

        byte[] note="a synthetic sealed note".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MaximaSender.Result went=Direct.send(a,a.contact(idB.publicKeyHex()),APPLICATION,note);
        assertTrue(went.isOk());
        assertArrayEquals(note,arrivedAtB.poll(10,TimeUnit.SECONDS));
        assertEquals("no relay was given a copy",0,relayDialled.get());
    }

    @Test public void aStrangersAnnouncementIsNotTakenAndOwnIsNotNews() throws Exception {
        MaximaIdentity stranger=MaximaIdentity.fromSeed(new MiniData(seed(9)));
        byte[] theirs=Direct.announce(stranger.keyPair(),LOOPBACK,doorB,System.currentTimeMillis());
        assertFalse(nearA.heard(theirs,theirs.length,InetAddress.getByAddress(LOOPBACK)));
        byte[] own=Direct.announce(idA.keyPair(),LOOPBACK,doorB,System.currentTimeMillis());
        assertFalse(nearA.heard(own,own.length,InetAddress.getByAddress(LOOPBACK)));
        assertNull(a.lanAddressFor(stranger.publicKeyHex()));
        assertEquals(0,nearA.near());
        // Counted by why, for the log; its own is not a refusal.
        assertEquals("1 not paired ("+Direct.tag(stranger.publicKeyHex())+")",nearA.refusals(true));
        assertEquals("",nearA.refusals(false));
        // A paired device's door found shut lately is not believed either, and says so.
        Direct.shut(idB.publicKeyHex(),"127.0.0.1:"+doorB,System.currentTimeMillis());
        byte[] shut=Direct.announce(idB.keyPair(),LOOPBACK,doorB,System.currentTimeMillis());
        assertFalse(nearA.heard(shut,shut.length,InetAddress.getByAddress(LOOPBACK)));
        assertEquals("1 shut ("+Direct.tag(idB.publicKeyHex())+")",nearA.refusals(true));
        Direct.opened(idB.publicKeyHex(),"127.0.0.1:"+doorB);
    }

    @Test public void aProvedDoorOfTheirOwnIsTriedBeforeAnyRelay() throws Exception {
        // B's own address, as a PC that proved its port would hand it out - listed after its relay.
        Contact known=a.contact(idB.publicKeyHex());
        known.setAddresses(List.of(idB.contactAddress("127.0.0.1:"+relay.getLocalPort()),
            MxAddress.make(idB.publicKeyData())+"@127.0.0.1:"+doorB));
        byte[] note="another synthetic note".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(Direct.send(a,known,APPLICATION,note).isOk());
        assertArrayEquals(note,arrivedAtB.poll(10,TimeUnit.SECONDS));
        assertEquals(0,relayDialled.get());
    }

    @Test public void aShutDoorFallsBackToTheRelaysAndIsNotBelievedAgainForAWhile() throws Exception {
        byte[] said=Direct.announce(idB.keyPair(),LOOPBACK,doorB,System.currentTimeMillis());
        assertTrue(nearA.heard(said,said.length,InetAddress.getByAddress(LOOPBACK)));
        b.stopDirect();
        byte[] note="not delivered".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // The stand-in relay takes nothing, so the long way fails too - but it is the way that was tried.
        assertThrows(IllegalStateException.class,()->Direct.send(a,a.contact(idB.publicKeyHex()),APPLICATION,note));
        assertTrue("the relays were tried",relayDialled.get()>0);
        assertNull(a.lanAddressFor(idB.publicKeyHex()));
        assertFalse("heard again at the same shut door, not believed",
            nearA.heard(said,said.length,InetAddress.getByAddress(LOOPBACK)));
        assertNull(arrivedAtB.poll(200,TimeUnit.MILLISECONDS));
    }
}
