package org.mininotes.android;

import static org.junit.Assert.*;

import com.eurobuddha.maxima.core.MaximaNode;
import com.eurobuddha.maxima.core.codec.MiniData;
import com.eurobuddha.maxima.core.contacts.Contact;
import com.eurobuddha.maxima.core.crypto.Hashes;
import com.eurobuddha.maxima.core.identity.MaximaIdentity;
import com.eurobuddha.maxima.core.identity.MxAddress;
import com.eurobuddha.maxima.core.media.MediaManifest;
import com.eurobuddha.maxima.core.media.MediaService;
import com.eurobuddha.maxima.core.media.MediaWire;
import com.eurobuddha.maxima.core.store.BlobStore;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Two real nodes on this machine, no relay and no internet: a file sent on its own goes from one device to the
 * other through their doors - the offer to the receiver's door, the pieces from the sender's, the answer back to
 * the sender's - and the stand-in relay each knows the other by is never dialled. Everything is sealed as the app
 * seals it; what each end decides is {@link Drop}'s, as {@code Post} asks it. See docs/SHARING.md, Sending files.
 */
public class DropDoorTest {
    private static final String APPLICATION="mininotes.v1";
    private MaximaNode a,b;
    private MaximaIdentity idA,idB;
    private BlobStore shelfA,shelfB;
    private int doorA,doorB;
    private ServerSocket relay;
    private final AtomicInteger relayDialled=new AtomicInteger();
    private final BlockingQueue<byte[]> atA=new LinkedBlockingQueue<>(),atB=new LinkedBlockingQueue<>();
    /** The keys each device seals and signs with: made up, nobody's. */
    private KeyPair signA,agreeA,signB,agreeB;
    private File folder;

    @Before public void twoNodes() throws Exception {
        Hashes.setSha3(Sha3::of);
        signA=Envelope.keys();agreeA=Envelope.keys();signB=Envelope.keys();agreeB=Envelope.keys();
        folder=Files.createTempDirectory("drop-door").toFile();
        idA=MaximaIdentity.fromSeed(new MiniData(seed(21)));idB=MaximaIdentity.fromSeed(new MiniData(seed(22)));
        a=new MaximaNode(idA,"test",2);b=new MaximaNode(idB,"test",2);
        // Each shelf before its door, as the app does: the door hands out only what is on it.
        shelfA=new BlobStore(new File(folder,"a"));shelfB=new BlobStore(new File(folder,"b"));
        a.setLocalBlobs(shelfA);b.setLocalBlobs(shelfB);
        a.setMessageListener((m,id)->{if(m!=null&&m.mApplication!=null&&APPLICATION.equals(m.mApplication.toString()))atA.add(m.mData.getBytes());});
        b.setMessageListener((m,id)->{if(m!=null&&m.mApplication!=null&&APPLICATION.equals(m.mApplication.toString()))atB.add(m.mData.getBytes());});
        doorA=a.startDirect(0);doorB=b.startDirect(0);
        assertTrue(doorA>0&&doorB>0);
        relay=new ServerSocket();relay.bind(new InetSocketAddress("127.0.0.1",0));
        Thread counting=new Thread(()->{
            while(!relay.isClosed())try(Socket one=relay.accept()){relayDialled.incrementAndGet();}catch(Exception closed){/* done */}
        });counting.setDaemon(true);counting.start();
        // Each knows the other by a relay first and its door after, as a PC that proved its port is known.
        a.storeContact(known(idB,doorB));b.storeContact(known(idA,doorA));
    }

    private Contact known(MaximaIdentity who,int door) {
        Contact them=new Contact(who.publicKeyHex());
        them.setAddresses(List.of(who.contactAddress("127.0.0.1:"+relay.getLocalPort()),door(who,door)));
        return them;
    }
    private static String door(MaximaIdentity who,int port){return MxAddress.make(who.publicKeyData())+"@127.0.0.1:"+port;}

    @After public void stop() throws Exception {
        if(relay!=null)relay.close();
        if(a!=null)a.stop();
        if(b!=null)b.stop();
    }

    private static byte[] seed(int fill){byte[] s=new byte[32];Arrays.fill(s,(byte)fill);return s;}
    private static byte[] page(String id){UUID u=UUID.fromString(id);return java.nio.ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();}

    /** A made-up file, offered from A's own shelf with nothing sent up: what Post does with no relay to use. */
    private byte[] offered(byte[] plain,String wire) throws Exception {
        MediaManifest made=new MediaService(null,shelfA).publish(plain,"application/octet-stream");
        assertTrue("nothing went anywhere",made.sources.isEmpty());
        byte[] offer=Drop.wrap(Collections.singletonList(new Enclosure.Listed(UUID.randomUUID().toString(),"ferry.bin",
            "application/octet-stream",plain.length,made.encode())));
        byte[] sealed=Envelope.seal(page(wire),1,System.currentTimeMillis(),offer,signA,agreeB.getPublic());
        assertTrue(Direct.send(a,a.contact(idB.publicKeyHex()),APPLICATION,sealed).isOk());
        return atB.poll(10,TimeUnit.SECONDS);
    }

    /** What B makes of it: opened, checked as A's, and listed. */
    private List<Enclosure.Listed> opened(byte[] came,String wire) throws Exception {
        assertNotNull("the offer came to B's door",came);
        Envelope.Opened opened=Envelope.open(came,agreeB.getPrivate());
        assertArrayEquals(Envelope.fingerprint(signA.getPublic()),opened.sender);
        assertArrayEquals(page(wire),opened.page);
        List<Enclosure.Listed> files=Drop.open(opened.text);
        assertNotNull(files);assertEquals(1,files.size());
        return files;
    }

    /** B's answer, sealed for A and sent to A's door, as A reads it. */
    private int answered(String wire,int what) throws Exception {
        byte[] sealed=Envelope.seal(page(wire),1,System.currentTimeMillis(),Receipt.wrap(what),signB,agreeA.getPublic());
        assertTrue(Direct.send(b,b.contact(idA.publicKeyHex()),APPLICATION,sealed).isOk());
        byte[] came=atA.poll(10,TimeUnit.SECONDS);
        assertNotNull("the answer came to A's door",came);
        Envelope.Opened answer=Envelope.open(came,agreeA.getPrivate());
        assertArrayEquals(Envelope.fingerprint(signB.getPublic()),answer.sender);
        return Receipt.open(answer.text);
    }

    @Test public void fromOneOfTheOwnersDevicesAFileGoesDoorToDoorWithNoRelay() throws Exception {
        byte[] plain=new byte[700_000];new Random(5).nextBytes(plain);
        String wire=UUID.randomUUID().toString();
        List<Enclosure.Listed> files=opened(offered(plain,wire),wire);
        // Marked "My device" at B: taken at once, nobody asked.
        assertEquals(Drop.FETCHING,Drop.arriving(true,true));
        // Fetched as Post fetches it: A's door asked whether it has the first piece, then every piece from there.
        MediaManifest said=MediaManifest.decode(files.get(0).manifest);
        assertEquals(plain.length,said.size);assertTrue(said.chunkIds.size()>1);
        String there=door(idA,doorA);
        assertTrue(MediaWire.has(b,there,said.chunkIds.get(0)));
        byte[] got=new MediaService(b,shelfB).fetch(new MediaManifest(said.mime,said.size,said.keyHex,said.nonceHex,said.sha3Hex,said.chunkIds,List.of(there)));
        assertArrayEquals(plain,got);
        // Every piece here, so B says so, and only that makes it delivered at A.
        int answer=answered(wire,Receipt.DROP_HAVE);
        assertEquals(Receipt.DROP_HAVE,answer);
        assertEquals(Drop.DELIVERED,Drop.after(Drop.WAITING,answer));
        assertEquals("no relay was given a copy of anything",0,relayDialled.get());
    }

    @Test public void fromAnybodyElseNothingIsFetchedUntilAskedAndARefusalReachesTheSender() throws Exception {
        byte[] plain=new byte[50_000];new Random(6).nextBytes(plain);
        String wire=UUID.randomUUID().toString();
        List<Enclosure.Listed> files=opened(offered(plain,wire),wire);
        // Paired, not one of the owner's: asked, and until then not one piece comes.
        assertEquals(Drop.ASKING,Drop.arriving(true,false));
        MediaManifest said=MediaManifest.decode(files.get(0).manifest);
        for(String piece:said.chunkIds)assertFalse(shelfB.has(piece));
        // Refused: A hears it at its door, and the sending is refused there for good.
        int answer=answered(wire,Receipt.DROP_REFUSED);
        assertEquals(Drop.REFUSED,Drop.after(Drop.WAITING,answer));
        assertEquals(Drop.REFUSED,Drop.after(Drop.after(Drop.WAITING,answer),Receipt.DROP_HAVE));
        // Offered again - A had not heard - it is answered the same, not asked about again.
        assertEquals(Receipt.DROP_REFUSED,Drop.answerAgain(Drop.REFUSED));
        for(String piece:said.chunkIds)assertFalse(shelfB.has(piece));
        assertEquals(0,relayDialled.get());
    }

    /** "This device takes files", sealed as Post seals it (the envelope names nothing), from one node's door to the other's. */
    private byte[] takesFiles(MaximaNode from,MaximaIdentity to,KeyPair sign,KeyPair theirs,BlockingQueue<byte[]> at) throws Exception {
        byte[] sealed=Envelope.seal(new byte[16],0,System.currentTimeMillis(),Receipt.wrap(Receipt.TAKES_FILES),sign,theirs.getPublic());
        assertTrue(Direct.send(from,from.contact(to.publicKeyHex()),APPLICATION,sealed).isOk());
        return at.poll(10,TimeUnit.SECONDS);
    }

    @Test public void aDeviceThatSaidItTakesFilesWhileTheOtherCouldNotHearSaysItAgainAndTheOfferGoes() throws Exception {
        // B, updated first, says it takes files. A is still a build from before: what came is read as a later build's
        // answer, which is to say not at all. B has said it this run.
        assertNotNull(takesFiles(b,idA,signB,agreeA,atA));
        long saidByB=System.currentTimeMillis()-2*60*60*1000L;   // hours ago, when B started
        boolean aKnows=false;
        // A is updated and says it at its start. B hears it at its door, and - by the rule Post answers with - says it back.
        byte[] toB=takesFiles(a,idB,signA,agreeB,atB);
        assertNotNull("A's word came to B's door",toB);
        Envelope.Opened heard=Envelope.open(toB,agreeB.getPrivate());
        assertEquals(Receipt.TAKES_FILES,Receipt.open(heard.text));
        assertTrue("said back: B last said it long ago, to a build that could not hear",Drop.sayBack(saidByB,System.currentTimeMillis()));
        byte[] toA=takesFiles(b,idA,signB,agreeA,atA);
        assertNotNull("B's word came back to A's door",toA);
        Envelope.Opened back=Envelope.open(toA,agreeA.getPrivate());
        assertArrayEquals(Envelope.fingerprint(signB.getPublic()),back.sender);
        if(Receipt.open(back.text)==Receipt.TAKES_FILES)aKnows=true;
        assertTrue("A now knows B takes files",aKnows);
        // And A does not say it back again: it said it a moment ago. Nothing goes round.
        assertFalse(Drop.sayBack(System.currentTimeMillis()-1_000,System.currentTimeMillis()));
        // So A's sending, which was waiting for B to "update", is offered, and reaches B.
        byte[] plain=new byte[30_000];new Random(8).nextBytes(plain);
        String wire=UUID.randomUUID().toString();
        assertEquals(1,opened(offered(plain,wire),wire).size());
        assertEquals(0,relayDialled.get());
    }

    @Test public void aSenderWhoseDoorIsShutIsToldTheFilesCannotBeHad() throws Exception {
        byte[] plain=new byte[20_000];new Random(7).nextBytes(plain);
        String wire=UUID.randomUUID().toString();
        List<Enclosure.Listed> files=opened(offered(plain,wire),wire);
        a.stopDirect();
        MediaManifest said=MediaManifest.decode(files.get(0).manifest);
        // Asked at the door that is shut, the first piece is not there: nowhere to fetch from, and the offer names no relay.
        assertFalse(MediaWire.has(b,door(idA,doorA),said.chunkIds.get(0)));
        assertTrue(said.sources.isEmpty());
        // So the sender is told at once - with helpers it then sends the pieces up; only between the owner's devices it waits.
        assertTrue(Drop.sayMissing(1,true));assertFalse(Drop.sayMissing(1,false));
        assertEquals(Drop.WAITING,Drop.after(Drop.WAITING,Receipt.DROP_MISSING));
        assertTrue(Drop.upToRelays(true,false,false));
    }
}
