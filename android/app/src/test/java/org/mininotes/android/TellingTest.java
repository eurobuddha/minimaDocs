package org.mininotes.android;

import static org.junit.Assert.*;

import com.eurobuddha.maxima.core.MaximaNode;
import com.eurobuddha.maxima.core.codec.MiniData;
import com.eurobuddha.maxima.core.contacts.Contact;
import com.eurobuddha.maxima.core.crypto.Hashes;
import com.eurobuddha.maxima.core.identity.MaximaIdentity;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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
 * A PC started again somewhere new, and a phone that still has it where it was - what was seen on 0.1.028, when the
 * phone's answers went to the PC's old relay and the PC never heard one. Two real nodes on this machine, no relay and
 * no internet: each knows the other only at a stand-in for a relay it has left, which takes nothing. The PC hears the
 * phone on the local network, tells it at its door where it is now and asks for its card back, and the phone's answer
 * then reaches the PC at its new address. See {@link Node#tell}.
 *
 * <p>The PC's new address is its door, proved as a PC's public door is (the transport's own rule against handing out
 * a home-network address is lifted for the test, as in OnlyMineTest): it stands for the relay it moved to.
 */
public class TellingTest {
    private static final String APPLICATION="mininotes.v1";
    private static final byte[] LOOPBACK={127,0,0,1};
    private MaximaNode phone,pc;
    private MaximaIdentity idPhone,idPc;
    private ServerSocket left;
    private final AtomicInteger leftDialled=new AtomicInteger();
    private final BlockingQueue<byte[]> arrivedAtPc=new LinkedBlockingQueue<>();
    private int doorPhone,doorPc;
    private Nearby nearPc;
    private boolean allowed;

    @Before public void twoNodes() throws Exception {
        Hashes.setSha3(Sha3::of);
        allowed=MaximaNode.ALLOW_ALL_IP;MaximaNode.ALLOW_ALL_IP=true;
        // Made-up seeds: nobody's identity.
        idPhone=MaximaIdentity.fromSeed(new MiniData(seed(21)));idPc=MaximaIdentity.fromSeed(new MiniData(seed(22)));
        phone=new MaximaNode(idPhone,"test",2);pc=new MaximaNode(idPc,"test",2);
        pc.setMessageListener((message,id)->{
            if(message!=null&&message.mApplication!=null&&APPLICATION.equals(message.mApplication.toString()))
                arrivedAtPc.add(message.mData.getBytes());
        });
        doorPhone=phone.startDirect(0);doorPc=pc.startDirect(0);
        assertTrue(doorPhone>0&&doorPc>0);
        phone.setDirectAddress("127.0.0.1:"+doorPhone);
        // The relay both used to be reached through: it counts who dials it, and takes nothing.
        left=new ServerSocket();left.bind(new InetSocketAddress("127.0.0.1",0));
        Thread counting=new Thread(()->{
            while(!left.isClosed())try(Socket one=left.accept()){leftDialled.incrementAndGet();}catch(Exception closed){/* done */}
        });counting.setDaemon(true);counting.start();
        phone.storeContact(knownAt(idPc,"PC"));
        pc.storeContact(knownAt(idPhone,"Phone"));
        nearPc=new Nearby(pc,()->doorPc,"127.0.0.1",0);
        nearPc.pairedWith(Set.of(Direct.key(idPhone.publicKeyHex())));
    }

    @After public void stop() throws Exception {
        MaximaNode.ALLOW_ALL_IP=allowed;
        if(nearPc!=null)nearPc.close();
        if(left!=null)left.close();
        if(phone!=null)phone.stop();
        if(pc!=null)pc.stop();
    }

    private Contact knownAt(MaximaIdentity who,String name) {
        Contact them=new Contact(who.publicKeyHex());them.name=name;
        them.setAddresses(List.of(who.contactAddress("127.0.0.1:"+left.getLocalPort())));
        return them;
    }

    private static byte[] seed(int fill){byte[] s=new byte[32];Arrays.fill(s,(byte)fill);return s;}

    /** The phone's announcement, heard by the PC as it would hear a broadcast. */
    private boolean pcHearsThePhone() throws Exception {
        byte[] said=Direct.announce(idPhone.keyPair(),LOOPBACK,doorPhone,System.currentTimeMillis());
        return nearPc.heard(said,said.length,InetAddress.getByAddress(LOOPBACK));
    }

    private static void await(java.util.concurrent.Callable<Boolean> condition) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(System.nanoTime()<until){if(condition.call())return;Thread.sleep(20);}
        fail("did not happen in time");
    }

    @Test public void heardOnTheNetworkThePcTellsThePhoneAndTheAnswerReachesItsNewAddress() throws Exception {
        // Started again somewhere new. The phone still has it at the relay it left, so its answer goes nowhere.
        pc.setDirectAddress("127.0.0.1:"+doorPc);
        byte[] answer="a synthetic answer".getBytes(StandardCharsets.UTF_8);
        try{Direct.send(phone,phone.contact(idPc.publicKeyHex()),APPLICATION,answer);}catch(IllegalStateException nowhere){/* as expected */}
        assertNull("nothing reached the PC",arrivedAtPc.poll(200,TimeUnit.MILLISECONDS));
        int dialledBefore=leftDialled.get();
        assertTrue(dialledBefore>0);

        // The PC hears the phone on the local network, anew, and tells it at its door.
        int[] told={-1};AtomicInteger met=new AtomicInteger();
        nearPc.onMet(key->{met.incrementAndGet();told[0]=Node.tell(pc,pc.contact(key),true);});
        assertTrue(pcHearsThePhone());
        assertEquals(Node.TOLD_AT_ITS_DOOR,told[0]);
        await(()->phone.contact(idPc.publicKeyHex()).addresses.contains(pc.directAddress()));
        // Asked back, the phone said where it is, at the PC's new address.
        await(()->pc.contact(idPhone.publicKeyHex()).addresses.contains(phone.directAddress()));

        // The answer, sent again, reaches the PC - and the relay it left is not dialled.
        assertTrue(Direct.send(phone,phone.contact(idPc.publicKeyHex()),APPLICATION,answer).isOk());
        assertArrayEquals(answer,arrivedAtPc.poll(10,TimeUnit.SECONDS));
        assertEquals(dialledBefore,leftDialled.get());

        // Every announcement after the first is not news: told once, not every half minute.
        assertTrue(pcHearsThePhone());
        assertEquals(1,met.get());
        // Moved networks, or gone quiet and back: heard anew, and told again.
        nearPc.forgetAll();
        assertTrue(pcHearsThePhone());
        assertEquals(2,met.get());
    }

    @Test public void tellingEverybodyGoesToADoorHeardHereWhenEveryAddressIsOld() throws Exception {
        pc.setDirectAddress("127.0.0.1:"+doorPc);
        pc.noteLanPeer(idPhone.publicKeyHex(),"127.0.0.1:"+doorPhone);
        int[] told=Node.tellAll(pc);
        assertArrayEquals("told the one device, at its door",new int[]{1,1,1},told);
        assertTrue("its old addresses were tried too",leftDialled.get()>0);
        await(()->phone.contact(idPc.publicKeyHex()).addresses.contains(pc.directAddress()));
    }

    @Test public void aDeviceWithNoAddressTellsNobody() throws Exception {
        // No relay and no proved door: a card would leave the phone with no address for the PC at all.
        pc.noteLanPeer(idPhone.publicKeyHex(),"127.0.0.1:"+doorPhone);
        assertTrue(pc.myAddresses().isEmpty());
        assertEquals(Node.NOT_TOLD,Node.tell(pc,pc.contact(idPhone.publicKeyHex()),false));
        Thread.sleep(300);
        assertEquals(List.of(idPc.contactAddress("127.0.0.1:"+left.getLocalPort())),phone.contact(idPc.publicKeyHex()).addresses);
        assertEquals(0,leftDialled.get());
    }

    @Test public void aDoorThatWillNotTakeTheCardIsLetGoOf() throws Exception {
        pc.setDirectAddress("127.0.0.1:"+doorPc);
        pc.noteLanPeer(idPhone.publicKeyHex(),"127.0.0.1:"+doorPhone);
        phone.stopDirect();
        int shut=Direct.SHUT_HERE.get();
        assertEquals(Node.NOT_TOLD,Node.tell(pc,pc.contact(idPhone.publicKeyHex()),true));
        assertNull(pc.lanAddressFor(idPhone.publicKeyHex()));
        assertTrue(Direct.shutLately(idPhone.publicKeyHex(),"127.0.0.1:"+doorPhone,System.currentTimeMillis()));
        assertEquals(shut+1,Direct.SHUT_HERE.get());
    }
}
