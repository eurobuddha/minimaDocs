package org.mininotes.android;

import static org.junit.Assert.*;

import com.eurobuddha.maxima.core.MaximaNode;
import com.eurobuddha.maxima.core.MaximaSender;
import com.eurobuddha.maxima.core.codec.MiniData;
import com.eurobuddha.maxima.core.contacts.Contact;
import com.eurobuddha.maxima.core.crypto.Hashes;
import com.eurobuddha.maxima.core.identity.MaximaIdentity;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Three real devices on this machine, no relay and no internet: a PC whose door has a home behind it, and two
 * of its owner's phones that cannot be reached. One phone's note is left at the PC and the other collects it,
 * and the stand-in relay each phone knows the other by is never dialled. See Home and Direct.
 */
public class HomeDoorTest {
    private static final String APPLICATION="mininotes.v1";
    private MaximaNode a, pc, b;
    private MaximaIdentity idA, idPc, idB;
    private KeyPair signA, signB, signPc, agreePc, agreeB, signStranger;
    private String fpA, fpB;
    private int door;
    private HomeTest.Memory shelf;
    private Home.Host home;
    private ServerSocket relay;
    private final AtomicInteger relayDialled=new AtomicInteger();
    private Contact bAsAKnowsThem;

    @Before public void threeDevices() throws Exception {
        Hashes.setSha3(Sha3::of);
        // Made-up seeds and made-up keys: nobody's identity.
        idA=MaximaIdentity.fromSeed(new MiniData(seed(21)));idPc=MaximaIdentity.fromSeed(new MiniData(seed(22)));idB=MaximaIdentity.fromSeed(new MiniData(seed(23)));
        a=new MaximaNode(idA,"test",2);pc=new MaximaNode(idPc,"test",2);b=new MaximaNode(idB,"test",2);
        signA=Envelope.keys();signB=Envelope.keys();signPc=Envelope.keys();agreePc=Envelope.keys();agreeB=Envelope.keys();signStranger=Envelope.keys();
        fpA=Courier.hex(Envelope.fingerprint(signA.getPublic()));fpB=Courier.hex(Envelope.fingerprint(signB.getPublic()));
        door=pc.startDirect(0);
        assertTrue(door>0);
        shelf=new HomeTest.Memory();shelf.now=System.currentTimeMillis();
        home=openHome(pc,idPc);
        // Only the PC listens. A stand-in for the relay phone B would otherwise be reached through: it counts who
        // dials it, and says nothing.
        relay=new ServerSocket();relay.bind(new InetSocketAddress("127.0.0.1",0));
        Thread counting=new Thread(()->{
            while(!relay.isClosed())try(Socket one=relay.accept()){relayDialled.incrementAndGet();}catch(Exception closed){/* done */}
        });counting.setDaemon(true);counting.start();
        bAsAKnowsThem=new Contact(idB.publicKeyHex());
        bAsAKnowsThem.setAddresses(List.of(idB.contactAddress("127.0.0.1:"+relay.getLocalPort())));
        a.storeContact(bAsAKnowsThem);
    }

    /** The PC's home: paired with both phones, and both are "My device". */
    private Home.Host openHome(MaximaNode node,MaximaIdentity id) {
        Home.Host made=new Home.Host(id.publicKeyHex());
        made.notebook(new Home.Notebook(agreePc.getPrivate(),Set.of(fpA,fpB),Set.of(fpA,fpB),shelf));
        made.open();
        return made;
    }

    @After public void stop() throws Exception {
        if(home!=null)home.close();
        if(relay!=null)relay.close();
        for(MaximaNode one:new MaximaNode[]{a,pc,b})if(one!=null)one.stop();
    }

    private static byte[] seed(int fill){byte[] s=new byte[32];Arrays.fill(s,(byte)fill);return s;}
    private String pcDoor(){return "127.0.0.1:"+door;}

    /** What phone B collects from the PC's door, and how the PC answered. */
    private Home.Collected collectB(List<byte[]> into) throws Exception {
        return Home.collect(pcDoor(),idPc.publicKeyHex(),agreePc.getPublic(),signB,into::add);
    }

    /** Phone A's way of leaving something for B at the PC, as Post makes it. */
    private Direct.Leave atThePc(KeyPair by) {
        return data->Home.deposit(pcDoor(),idPc.publicKeyHex(),agreePc.getPublic(),by,Envelope.fingerprint(signB.getPublic()),data)==Home.OK;
    }

    @Test public void aNoteLeftAtThePcIsCollectedByTheOtherPhoneWithNoRelay() throws Exception {
        List<byte[]> got=new ArrayList<>();
        // B asks once and there is nothing: from now on it is collecting.
        assertEquals(Home.OK,collectB(got).status);
        assertTrue(got.isEmpty());

        byte[] page=new byte[16];page[3]=5;
        byte[] note=Envelope.seal(page,4,System.currentTimeMillis(),"A synthetic note".getBytes(StandardCharsets.UTF_8),signA,agreeB.getPublic());
        MaximaSender.Result went=Direct.send(a,a.contact(idB.publicKeyHex()),APPLICATION,note,atThePc(signA));
        assertTrue(went.isOk());
        assertEquals("no relay was given a copy",0,relayDialled.get());
        assertEquals(1,home.holding());

        Home.Collected collected=collectB(got);
        assertEquals(Home.OK,collected.status);assertEquals(1,collected.items);
        assertArrayEquals(note,got.get(0));
        // B opens it as if it had come straight from A: it is A's, sealed for B, and the PC could not read it.
        Envelope.Opened opened=Envelope.open(got.get(0),agreeB.getPrivate());
        assertEquals("A synthetic note",new String(opened.text,StandardCharsets.UTF_8));
        assertEquals(fpA,Courier.hex(opened.sender));
        assertEquals("collected, and let go",0,home.holding());
        got.clear();
        assertEquals(0,collectB(got).items);
        assertEquals(0,relayDialled.get());
    }

    @Test public void thePcRefusesStrangersAndDevicesThatAreNotItsOwnersAndThenTheRelaysAreTried() throws Exception {
        List<byte[]> got=new ArrayList<>();
        byte[] note=Envelope.seal(new byte[16],1,System.currentTimeMillis(),"x".getBytes(StandardCharsets.UTF_8),signA,agreeB.getPublic());
        // B has not collected yet: the PC will not take it, and the send goes the long way.
        assertEquals(Home.NOT_COLLECTING,Home.deposit(pcDoor(),idPc.publicKeyHex(),agreePc.getPublic(),signA,Envelope.fingerprint(signB.getPublic()),note));
        assertThrows(IllegalStateException.class,()->Direct.send(a,a.contact(idB.publicKeyHex()),APPLICATION,note,atThePc(signA)));
        assertTrue("the relays were tried",relayDialled.get()>0);
        collectB(got);
        // A device the PC is not paired with.
        byte[] theirs=Envelope.seal(new byte[16],1,System.currentTimeMillis(),"x".getBytes(StandardCharsets.UTF_8),signStranger,agreeB.getPublic());
        assertEquals(Home.REFUSED,Home.deposit(pcDoor(),idPc.publicKeyHex(),agreePc.getPublic(),signStranger,Envelope.fingerprint(signB.getPublic()),theirs));
        assertEquals(Home.REFUSED,Home.collect(pcDoor(),idPc.publicKeyHex(),agreePc.getPublic(),signStranger,got::add).status);
        // For a device that is not one of the owner's.
        assertEquals(Home.REFUSED,Home.deposit(pcDoor(),idPc.publicKeyHex(),agreePc.getPublic(),signA,Envelope.fingerprint(signStranger.getPublic()),note));
        assertEquals(0,home.holding());
        assertTrue(got.isEmpty());
    }

    @Test public void aDoorWithNoHomeBehindItSaysSoAtOnce() throws Exception {
        int phoneDoor=b.startDirect(0);
        assertTrue(phoneDoor>0);
        assertEquals(Home.NO_HOME,Home.collect("127.0.0.1:"+phoneDoor,idB.publicKeyHex(),agreeB.getPublic(),signA,bytes->{}).status);
        // And a home asked for by the wrong key is no home either: the label is made from the PC's own.
        assertEquals(Home.NO_HOME,Home.collect(pcDoor(),idA.publicKeyHex(),agreePc.getPublic(),signB,bytes->{}).status);
    }

    @Test public void whatThePcHoldsIsStillThereWhenItsNodeStartsAgain() throws Exception {
        List<byte[]> got=new ArrayList<>();
        collectB(got);
        byte[] note=Envelope.seal(new byte[16],2,System.currentTimeMillis(),"kept".getBytes(StandardCharsets.UTF_8),signA,agreeB.getPublic());
        assertTrue(Direct.send(a,a.contact(idB.publicKeyHex()),APPLICATION,note,atThePc(signA)).isOk());
        // The PC goes, and comes back with the same identity and the same notebook.
        home.close();pc.stop();
        pc=new MaximaNode(idPc,"test",2);door=pc.startDirect(0);
        assertTrue(door>0);
        home=openHome(pc,idPc);
        assertEquals(1,home.holding());
        assertEquals(1,collectB(got).items);
        assertArrayEquals(note,got.get(0));
        assertEquals(0,relayDialled.get());
    }

    @Test public void theDevicesOwnDoorComesBeforeItsHome() throws Exception {
        int phoneDoor=b.startDirect(0);
        java.util.concurrent.BlockingQueue<byte[]> arrived=new java.util.concurrent.LinkedBlockingQueue<>();
        b.setMessageListener((message,id)->{if(message!=null&&message.mData!=null)arrived.add(message.mData.getBytes());});
        Contact known=a.contact(idB.publicKeyHex());
        known.setAddresses(List.of(idB.contactAddress("127.0.0.1:"+relay.getLocalPort()),
            com.eurobuddha.maxima.core.identity.MxAddress.make(idB.publicKeyData())+"@127.0.0.1:"+phoneDoor));
        AtomicInteger askedHome=new AtomicInteger();
        byte[] note="straight to the phone".getBytes(StandardCharsets.UTF_8);
        assertTrue(Direct.send(a,known,APPLICATION,note,data->{askedHome.incrementAndGet();return true;}).isOk());
        assertArrayEquals(note,arrived.poll(10,java.util.concurrent.TimeUnit.SECONDS));
        assertEquals("its home was not needed",0,askedHome.get());
        assertEquals(0,relayDialled.get());
    }

    @Test public void moreThanOneAnswersWorthIsCollectedOverOneConnection() throws Exception {
        List<byte[]> got=new ArrayList<>();
        collectB(got);
        int many=Home.PULL_MOST+3;
        for(int one=0;one<many;one++) {
            byte[] note=Envelope.seal(new byte[16],one,System.currentTimeMillis(),("note "+one).getBytes(StandardCharsets.UTF_8),signA,agreeB.getPublic());
            assertEquals(Home.OK,Home.deposit(pcDoor(),idPc.publicKeyHex(),agreePc.getPublic(),signA,Envelope.fingerprint(signB.getPublic()),note));
        }
        Home.Collected collected=collectB(got);
        assertEquals(many,collected.items);assertEquals(many,got.size());
        assertEquals(0,home.holding());
    }
}
