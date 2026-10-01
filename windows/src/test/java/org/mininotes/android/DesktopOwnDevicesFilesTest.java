package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import com.eurobuddha.maxima.core.crypto.Hashes;
import com.eurobuddha.maxima.core.media.MediaManifest;
import com.eurobuddha.maxima.core.media.MediaService;
import com.eurobuddha.maxima.core.store.BlobStore;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import org.mininotes.desktop.platform.content.Context;

/**
 * Three of the owner's own devices - a PC and two phones - with a note and its files, as seen on 0.1.030: every log
 * said the note was the same on all three, and the mark was amber on all three. No node and no network: each message
 * is sealed and handed to the device it is for, a message that did not get through is simply not handed, and the
 * pieces of a file are left on one shelf all can reach, as the PC's door holds them.
 */
public class DesktopOwnDevicesFilesTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final List<NoteStore> open=new ArrayList<>();
    @After public void close(){for(NoteStore one:open)one.close();}

    private static final String PC="MxPcFixture@127.0.0.1:9301",GRAPHENE="MxGrapheneFixture@127.0.0.1:9302",PRO="MxProFixture@127.0.0.1:9303";

    private final class Device {
        final Context context;final NoteStore store;final Keys keys;
        Device(String name) throws Exception {
            context=new Context(temp.newFolder(name));store=new NoteStore(context);store.getWritableDatabase();open.add(store);
            keys=new Keys(context);store.mySigningKey=Base64.getEncoder().encodeToString(keys.signing().getPublic().getEncoded());
        }
        void pair(String address,String name,Device other) throws Exception {
            store.pairedWith(address,name,true,other.keys.agreement().getPublic().getEncoded(),other.keys.signing().getPublic().getEncoded());
        }
        Post.Landed hear(byte[] sealed){return Post.arrived(context,store,keys,sealed);}
        String attach(String note,byte[] bytes,String name) throws Exception {
            String id=UUID.randomUUID().toString();
            Files.write(store.fileFor(id).toPath(),bytes);
            store.keep(new NoteStore.Held(id,note,name,"application/octet-stream",bytes.length,System.currentTimeMillis()));
            return id;
        }
        SyncMark mark(){return SyncStatus.mark(store,note,SyncStatus.who(store,note),false);}
        String said(){return SyncStatus.waits(store,NoteStore.Branch.Kind.PAGE,note);}
        /** The writing, answered as the same by the other two: what "answered: revision 57, and they now say the same" writes. */
        void textAgreedWith(String... addresses){for(String one:addresses)assertTrue(store.acknowledged(one,note,store.get(note).revision,true));}
    }

    private static byte[] page(String id){UUID u=UUID.fromString(id);return java.nio.ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();}

    private byte[] noteFrom(Device from,Device to,long asOf) throws Exception {
        byte[] parcel=Parcel.wrap(new Parcel.Sent("c","Home","b","Lists","Transfer","Synthetic",true,
            Collections.<Parcel.Member>emptyList(),"PAGE",note,false,-1L,false,from.store.enclosed(note),asOf),Envelope.MAX_TEXT);
        return Envelope.seal(page(note),from.store.get(note).revision,asOf,parcel,from.keys.signing(),to.keys.agreement().getPublic());
    }

    private static byte[] have(Device from,Device to,String file) throws Exception {
        return Envelope.seal(page(file),0,1,Receipt.wrap(Receipt.FILE_HERE),from.keys.signing(),to.keys.agreement().getPublic());
    }

    /** Fetched the way Post does it, from the shelf; the one given is left waiting. */
    private int fetches(Device to,String... notYet) throws Exception {
        int kept=0;
        for(NoteStore.Incoming one:to.store.toFetch(System.currentTimeMillis())) {
            if(Arrays.asList(notYet).contains(one.id))continue;
            byte[] plain=new MediaService(null,new BlobStore(shelf)).fetch(MediaManifest.decode(one.manifest));
            if(to.store.fileArrived(one,plain))kept++;
        }
        return kept;
    }

    private Device pc,graphene,pro;private String note;private File shelf;private final List<String> files=new ArrayList<>();

    @Before public void three() throws Exception {
        Hashes.setSha3(Sha3::of);
        pc=new Device("pc");graphene=new Device("graphene");pro=new Device("pro");
        pc.pair(GRAPHENE,"Graphene",graphene);pc.pair(PRO,"Pro",pro);
        graphene.pair(PC,"PC",pc);graphene.pair(PRO,"Pro",pro);
        pro.pair(PC,"PC",pc);pro.pair(GRAPHENE,"Graphene",graphene);
        note=UUID.randomUUID().toString();
        NoteStore.Note n=new NoteStore.Note();n.id=note;n.book=pc.store.someBook();n.title="Transfer";n.body="Synthetic";n.revision=57;pc.store.save(n);
        pc.store.setLevel(Sharing.Scope.PAGE,note,GRAPHENE,Sharing.Level.WRITE,null);
        pc.store.setLevel(Sharing.Scope.PAGE,note,PRO,Sharing.Level.WRITE,null);
        graphene.store.addShare(new Sharing.Rule(Sharing.Scope.PAGE,note,PC,true));graphene.store.addShare(new Sharing.Rule(Sharing.Scope.PAGE,note,PRO,true));
        pro.store.addShare(new Sharing.Rule(Sharing.Scope.PAGE,note,PC,true));pro.store.addShare(new Sharing.Rule(Sharing.Scope.PAGE,note,GRAPHENE,true));
        shelf=temp.newFolder("pc-door");
        Random bytes=new Random(7);
        for(int i=0;i<3;i++){byte[] one=new byte[2000+i];bytes.nextBytes(one);files.add(pc.attach(note,one,"scan"+i+".pdf"));}
        // Too big to go: it holds nothing back, on any device.
        String big=UUID.randomUUID().toString();
        pc.store.keep(new NoteStore.Held(big,note,"film.mov","video/quicktime",Enclosure.MOST+1,System.currentTimeMillis()));
        // Offered from the PC's door, only between the owner's devices: its pieces stay with the PC.
        for(String one:files) {
            MediaManifest made=new MediaService(null,new BlobStore(shelf)).publish(pc.store.bytesOf(pc.store.file(one)),"application/octet-stream");
            pc.store.published(one,made.encode());
        }
        graphene.hear(noteFrom(pc,graphene,10));pro.hear(noteFrom(pc,pro,10));
        pc.textAgreedWith(GRAPHENE,PRO);graphene.textAgreedWith(PC,PRO);pro.textAgreedWith(PC,GRAPHENE);
    }

    @Test public void allSyncedWithFilesBetweenThreeOwnDevicesIsATickOnEach() throws Exception {
        String last=files.get(2);
        // The Pro fetches all three first and says so to both others - while Graphene is still fetching the last.
        assertEquals(3,fetches(pro));
        assertEquals(2,fetches(graphene,last));
        for(String one:files){pc.hear(have(pro,pc,one));graphene.hear(have(pro,graphene,one));}
        assertTrue("said while it was still being fetched here, and believed",graphene.store.holders(last).contains(PRO));
        assertEquals(1,fetches(graphene));
        // Graphene says so to the PC; its word to the Pro does not get through - the Pro out of reach just then.
        for(String one:files)pc.hear(have(graphene,pc,one));

        assertEquals(SyncMark.GONE,pc.mark());
        assertEquals(SyncMark.GONE,graphene.mark());
        assertEquals("the one word that was lost keeps the Pro amber",SyncMark.WAITING,pro.mark());
        assertEquals("Waiting for Graphene to confirm 3 files.",pro.said());
        for(SyncStatus.Person one:SyncStatus.who(pro.store,note))
            if(one.address().equals(GRAPHENE))assertEquals("Graphene has the text; 3 of 3 files still going.",one.standing());

        // The Pro's note comes to Graphene with its list, as any send of it does, and Graphene answers it with the
        // files it has. Only those it did not add itself.
        List<String> answered=graphene.store.haveHere(note,pro.store.enclosed(note));
        assertEquals(new HashSet<>(files),new HashSet<>(answered));
        for(String one:answered)pro.hear(have(graphene,pro,one));
        assertEquals(SyncMark.GONE,pro.mark());
        assertEquals(Waits.UNSAVED,pro.said());
        for(SyncStatus.Person one:SyncStatus.who(pro.store,note))
            assertEquals(one.address().equals(PC)?"Has this version.":"Has this version, files included.",one.standing());
    }

    @Test public void aDeviceAnswersAListOnlyWithFilesItDidNotAddItself() throws Exception {
        assertEquals(3,fetches(graphene));
        assertTrue("the PC added them: whoever lists them got them from it",pc.store.haveHere(note,graphene.store.enclosed(note)).isEmpty());
        assertEquals(3,graphene.store.haveHere(note,pc.store.enclosed(note)).size());
        assertTrue("not had yet, not said",pro.store.haveHere(note,pc.store.enclosed(note)).isEmpty());
    }

    /** A file added on one device, offered from its door the way the PC's are. */
    private String offered(Device on,String name) throws Exception {
        byte[] one=new byte[1500];new Random(name.hashCode()).nextBytes(one);
        String id=on.attach(note,one,name);
        on.store.published(id,new MediaService(null,new BlobStore(shelf)).publish(one,"application/octet-stream").encode());
        return id;
    }

    @Test public void aDeviceThatListsAFileHasItThoughItNeverSaysSoOfItsOwn() throws Exception {
        // Graphene's own file reaches the Pro through the PC, so on the Pro it came from the PC, and the Pro waits for
        // Graphene to say it has it - which Graphene never says of a file it added itself. Seen on 0.1.032: "1 of 12
        // files still going" on the Pro for ever.
        String mine=offered(graphene,"graphene.pdf");
        pc.hear(noteFrom(graphene,pc,20));
        assertEquals(1,fetches(pc));
        pro.hear(noteFrom(pc,pro,30));
        assertEquals(4,fetches(pro));
        assertFalse(pro.store.holders(mine).contains(GRAPHENE));
        assertEquals("Graphene has the text; 4 of 4 files still going.",standing(pro,GRAPHENE));
        assertTrue("it added it itself, and says nothing of it",graphene.store.haveHere(note,pro.store.enclosed(note)).stream().noneMatch(mine::equals));

        // Graphene's note reaches the Pro with its list, as any sending of it does: that list is its word.
        pro.hear(noteFrom(graphene,pro,40));
        assertTrue(pro.store.holders(mine).contains(GRAPHENE));
        assertEquals("only the PC's three, which Graphene has not fetched","Graphene has the text; 3 of 4 files still going.",
            standing(pro,GRAPHENE));
    }

    private String standing(Device on,String address) {
        for(SyncStatus.Person one:SyncStatus.who(on.store,note))if(one.address().equals(address))return one.standing();
        return null;
    }

    @Test public void aFileWaitedForFromADeviceOutOfReachIsHadFromTheOneWhoseListNamesIt() throws Exception {
        // The Pro adds a file; the PC takes it and passes on its list first, so Graphene waits for it from the PC. The PC
        // locks, and Graphene's tries at its door come to nothing. The Pro, whose file it is, never says it has it.
        String theirs=offered(pro,"pro.pdf");
        pc.hear(noteFrom(pro,pc,20));
        assertEquals(1,fetches(pc));
        graphene.hear(noteFrom(pc,graphene,30));
        for(int i=0;i<3;i++)graphene.store.fetchFailed(theirs);
        assertTrue("waits out the PC's door",graphene.store.toFetch(System.currentTimeMillis()).stream().noneMatch(one->one.id.equals(theirs)));
        assertFalse(graphene.store.holders(theirs).contains(PRO));

        // The Pro's list arrives: its door is somewhere to have it from, and it is tried now.
        graphene.hear(noteFrom(pro,graphene,40));
        assertTrue(graphene.store.holders(theirs).contains(PRO));
        assertTrue(graphene.store.toFetch(System.currentTimeMillis()).stream().anyMatch(one->one.id.equals(theirs)));
    }

    private static byte[] word(Device from,Device to,int what,long moment) throws Exception {
        return Envelope.seal(new byte[16],0,moment,Receipt.wrap(what),from.keys.signing(),to.keys.agreement().getPublic());
    }

    @Test public void aPcThatSaysItIsLockedIsGreyNotAmberAndOpenAgainWhenItSpeaks() throws Exception {
        // The Pro writes; Graphene has it, and the PC locks before it can take it in.
        NoteStore.Note n=pro.store.get(note);n.body="Synthetic, and more";n.revision=58;pro.store.save(n);
        assertTrue(pro.store.acknowledged(GRAPHENE,note,58,true));
        assertEquals(SyncMark.WAITING,pro.mark());
        assertEquals("Your changes have not gone to them yet.",standing(pro,PC));

        long locked=System.currentTimeMillis();
        pro.hear(word(pc,pro,Receipt.LOCKED,locked));
        assertEquals("PC is locked; it takes it in when opened.",standing(pro,PC));
        for(SyncStatus.Person one:SyncStatus.who(pro.store,note))
            if(one.address().equals(PC))assertEquals(SyncMark.PAUSED,one.mark());
        assertEquals("nothing anybody here can do keeps the note amber",SyncMark.GONE,pro.mark());
        assertEquals("PC is locked; it takes it in when opened.",pro.said());

        // A word from before the lock, carried the long way round, does not open it; the PC opening again does.
        pro.hear(word(pc,pro,Receipt.OPENED,locked-60_000));
        assertEquals("PC is locked; it takes it in when opened.",standing(pro,PC));
        pro.hear(word(pc,pro,Receipt.OPENED,locked+60_000));
        assertEquals("Your changes have not gone to them yet.",standing(pro,PC));
        assertEquals(SyncMark.WAITING,pro.mark());

        // Locked again, and then simply heard from - an answer, a note - which a locked notebook never sends.
        pro.hear(word(pc,pro,Receipt.LOCKED,locked+120_000));
        assertEquals(SyncMark.GONE,pro.mark());
        pro.hear(Envelope.seal(page(note),58,locked+180_000,Receipt.wrap(Receipt.TOOK),pc.keys.signing(),pro.keys.agreement().getPublic()));
        assertEquals(SyncMark.GONE,pro.mark());
        assertEquals("Has this version.",standing(pro,PC));
        assertTrue(pro.store.lockedThere().isEmpty());
    }

    @Test public void aWordAboutAFileNobodyHereKnowsChangesNothing() throws Exception {
        assertFalse(pc.store.fileReached(UUID.randomUUID().toString(),PRO));
        assertTrue(pro.store.fileReached(files.get(0),GRAPHENE));
    }
}
