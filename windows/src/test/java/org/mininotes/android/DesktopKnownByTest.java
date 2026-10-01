package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import com.eurobuddha.maxima.core.MaximaNode;
import com.eurobuddha.maxima.core.codec.MiniData;
import com.eurobuddha.maxima.core.contacts.Contact;
import com.eurobuddha.maxima.core.crypto.Hashes;
import com.eurobuddha.maxima.core.identity.MaximaIdentity;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.mininotes.desktop.platform.content.Context;

/**
 * Seen on 0.1.034: a PC linked with a phone through a shared note's list, later marked as the owner's. The phone sends
 * only between its owner's devices, so the relay address the list gave was never dialled and the PC's key on the
 * network never written down; the phone did not believe the PC's announcements, and kept every answer to it. Here, in
 * the real notebook and through {@code Post.arrived}, the key is written down from the first thing the PC sends, and
 * its announcement is believed from then. Synthetic devices, no network.
 */
public class DesktopKnownByTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final List<NoteStore> open=new ArrayList<>();
    @After public void close(){for(NoteStore one:open)one.close();}

    private static final String PHONE_AT="MxKnownByPhoneFixture@127.0.0.1:9321",PC_AT="MxKnownByRelayFixture@192.0.2.40:9001";
    private static final byte[] HOME={(byte)192,0,2,16};

    private final class Device {
        final Context context;final NoteStore store;final Keys keys;final String at;
        Device(String name,String at) throws Exception {
            this.at=at;
            context=new Context(temp.newFolder(name));store=new NoteStore(context);store.getWritableDatabase();open.add(store);
            keys=new Keys(context);
            store.mySigningKey=Base64.getEncoder().encodeToString(keys.signing().getPublic().getEncoded());
            store.myAgreement=Point.shorten(keys.agreement().getPublic());store.myName=name;store.myAddress=at;
        }
        byte[] hello(Device to) throws Exception {
            byte[] said=Hello.wrap(Linking.hello(store.myName,at,Point.shorten(keys.agreement().getPublic()),Point.shorten(keys.signing().getPublic()),false));
            return Envelope.seal(new byte[16],0,System.currentTimeMillis(),said,keys.signing(),to.keys.agreement().getPublic());
        }
    }

    private static MaximaIdentity identity(int fill){byte[] s=new byte[32];Arrays.fill(s,(byte)fill);return MaximaIdentity.fromSeed(new MiniData(s));}

    @Test public void aDeviceLinkedThroughAListIsKnownByWhatItSendsAndHeardFromThen() throws Exception {
        Hashes.setSha3(Sha3::of);
        Device phone=new Device("Test phone",PHONE_AT),pc=new Device("Test PC",PC_AT);
        MaximaIdentity pcOnTheNetwork=identity(41),phoneOnTheNetwork=identity(42);
        assertTrue(phone.store.linkThrough(PC_AT,"Test PC",Point.shorten(pc.keys.agreement().getPublic()),pc.keys.signing().getPublic().getEncoded(),false));
        phone.store.setMine(PC_AT,true);
        NoteStore.Contact before=phone.store.address(PC_AT);
        assertTrue(before.paired());assertTrue(before.listed());assertTrue(before.mine);assertEquals("",before.contact);

        byte[] said=Direct.announce(pcOnTheNetwork.keyPair(),HOME,9601,System.currentTimeMillis());
        List<Direct.Refused> why=new ArrayList<>();
        assertNull(Direct.heard(said,said.length,HOME,phoneOnTheNetwork.publicKeyHex(),Direct.paired(phone.store.addresses()),System.currentTimeMillis(),(r,who)->why.add(r)));
        assertEquals(List.of(Direct.Refused.NOT_PAIRED),why);

        // Something from the PC, which the transport says came from its key: the key is written down against its row.
        byte[] hello=pc.hello(phone);
        Node.sent(hello,pcOnTheNetwork.publicKeyHex());
        Post.arrived(phone.context,phone.store,phone.keys,hello);
        assertEquals(Direct.key(pcOnTheNetwork.publicKeyHex()),Direct.key(phone.store.address(PC_AT).contact));
        Direct.Heard heard=Direct.heard(said,said.length,HOME,phoneOnTheNetwork.publicKeyHex(),Direct.paired(phone.store.addresses()),System.currentTimeMillis());
        assertNotNull(heard);assertEquals("192.0.2.16:9601",heard.where());

        // The same again changes nothing; and a key said for somebody else's signature is written against nobody.
        assertFalse(Post.knownBy(phone.context,phone.store,Envelope.fingerprint(pc.keys.signing().getPublic()),pcOnTheNetwork.publicKeyHex()));
        Device stranger=new Device("Somebody else","MxKnownByStrangerFixture@127.0.0.1:9322");
        assertFalse(Post.knownBy(phone.context,phone.store,Envelope.fingerprint(stranger.keys.signing().getPublic()),identity(43).publicKeyHex()));
        assertEquals(Direct.key(pcOnTheNetwork.publicKeyHex()),Direct.key(phone.store.address(PC_AT).contact));
    }

    /**
     * The same through the real path, as the phones run it: the PC's own node sends its hello to the phone's door, the
     * phone's node hands it to {@code Node.arrived} and on to {@code Post.arrived}, and the PC's own announcement - made
     * with the key its node signs everything with - is refused before and believed after, at once, without waiting for
     * the next one. The key learnt and the key refused are the same, and so are the tags the log lines carry.
     */
    @Test public void theKeyLearntFromAMessageIsTheKeyTheAnnouncementIsCheckedAgainst() throws Exception {
        Hashes.setSha3(Sha3::of);
        Device phone=new Device("Test phone",PHONE_AT),pc=new Device("Test PC",PC_AT);
        MaximaNode pcNode=new MaximaNode(identity(51),"test",2),phoneNode=new MaximaNode(identity(52),"test",2);
        Nearby near=new Nearby(phoneNode,()->-1,"127.0.0.1",0);
        try {
            int door=phoneNode.startDirect(0);
            assertTrue(door>0);
            // What the PC's card does at the phone's door: the phone's node knows it as a contact, by its key.
            phoneNode.storeContact(new Contact(pcNode.publicKeyHex()));
            assertTrue(phone.store.linkThrough(PC_AT,"Test PC",Point.shorten(pc.keys.agreement().getPublic()),pc.keys.signing().getPublic().getEncoded(),false));
            phone.store.setMine(PC_AT,true);
            near.pairedWith(Direct.paired(phone.store.addresses()));

            // The PC announces itself: not believed, for want of its key, and the log's tag is its key's.
            byte[] loopback={127,0,0,1};
            byte[] said=Direct.announce(pcNode.identity().keyPair(),loopback,9601,System.currentTimeMillis());
            assertFalse(near.heard(said,said.length,InetAddress.getByAddress(loopback)));
            assertEquals("1 not paired ("+Direct.tag(pcNode.publicKeyHex())+")",near.refusals(true));
            assertNull(phoneNode.lanAddressFor(pcNode.publicKeyHex()));

            // Its hello comes through the phone's door, as the phone's node brings it.
            CountDownLatch landed=new CountDownLatch(1);
            phoneNode.setMessageListener((message,id)->Node.arrived(message,bytes->{Post.arrived(phone.context,phone.store,phone.keys,bytes);landed.countDown();}));
            assertTrue(pcNode.sendRaw(Direct.door(phoneNode.publicKeyHex(),"127.0.0.1",door),Post.APPLICATION,pc.hello(phone)).isOk());
            assertTrue(landed.await(10,TimeUnit.SECONDS));
            String learnt=phone.store.address(PC_AT).contact;
            assertEquals(Direct.key(pcNode.publicKeyHex()),Direct.key(learnt));
            assertEquals(Direct.tag(pcNode.publicKeyHex()),Direct.tag(learnt));

            // The round's paired set now holds it: the announcement refused a moment ago is believed at once.
            near.pairedWith(Direct.paired(phone.store.addresses()));
            assertEquals(Direct.door(pcNode.publicKeyHex(),"127.0.0.1",9601),phoneNode.lanAddressFor(pcNode.publicKeyHex()));
            assertEquals(1,near.near());
            // And the next one too.
            byte[] again=Direct.announce(pcNode.identity().keyPair(),loopback,9601,System.currentTimeMillis());
            assertTrue(near.heard(again,again.length,InetAddress.getByAddress(loopback)));
            assertEquals("",near.refusals(false));
        } finally {
            near.close();phoneNode.stop();pcNode.stop();
        }
    }
}
