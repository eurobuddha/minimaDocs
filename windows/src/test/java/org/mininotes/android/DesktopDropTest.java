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
 * Files sent on their own, through the real notebook and the real arrival path ({@code Post.arrived}), between
 * synthetic devices with no node and no network: each message is sealed and handed to the device it is for, and a
 * file's pieces are left on a shelf both can reach, as the sender's door would hand them out. See Drop.
 */
public class DesktopDropTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final List<NoteStore> open=new ArrayList<>();
    @After public void close(){for(NoteStore one:open)one.close();NoteStore.fileKey=NoteStore::key;}

    private static final String SENDER="MxSenderFixture@127.0.0.1:9301",RECEIVER="MxReceiverFixture@127.0.0.1:9302",STRANGER="MxStrangerFixture@127.0.0.1:9303";

    private final class Device {
        final Context context;final NoteStore store;final Keys keys;
        Device(String name) throws Exception {
            context=new Context(temp.newFolder(name));store=new NoteStore(context);store.getWritableDatabase();open.add(store);
            keys=new Keys(context);store.mySigningKey=Base64.getEncoder().encodeToString(keys.signing().getPublic().getEncoded());
        }
        void pair(String address,String name,boolean mine,Device other) throws Exception {
            store.pairedWith(address,name,mine,other.keys.agreement().getPublic().getEncoded(),other.keys.signing().getPublic().getEncoded());
        }
        Post.Landed hear(byte[] sealed){return Post.arrived(context,store,keys,sealed);}
    }

    private static byte[] page(String id){UUID u=UUID.fromString(id);return java.nio.ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();}

    private Device sender,receiver;private File shelf;
    @Before public void two() throws Exception {
        Hashes.setSha3(Sha3::of);
        sender=new Device("sender");receiver=new Device("receiver");
        sender.pair(RECEIVER,"Receiver",false,receiver);
        shelf=temp.newFolder("door");
    }

    /** An offer of these bytes from {@code from} to {@code to}, their pieces put where both can reach them. */
    private byte[] offer(Device from,Device to,String wire,long revision,byte[]... files) throws Exception {
        List<Enclosure.Listed> listed=new ArrayList<>();
        for(byte[] plain:files) {
            MediaManifest made=new MediaService(null,new BlobStore(shelf)).publish(plain,"application/octet-stream");
            listed.add(new Enclosure.Listed(UUID.randomUUID().toString(),"photo-"+listed.size()+".jpg","image/jpeg",plain.length,made.encode()));
        }
        return Envelope.seal(page(wire),revision,System.currentTimeMillis(),Drop.wrap(listed),from.keys.signing(),to.keys.agreement().getPublic());
    }

    private static byte[] said(Device from,Device to,String wire,long revision,int what) throws Exception {
        return Envelope.seal(page(wire),revision,1,Receipt.wrap(what),from.keys.signing(),to.keys.agreement().getPublic());
    }

    /** Every file of one received sending fetched as Post fetches it, from the shelf standing in for the sender's door. */
    private void fetchAll(NoteStore.Transfer one) throws Exception {
        for(NoteStore.Loose file:one.files) {
            byte[] plain=new MediaService(null,new BlobStore(shelf)).fetch(MediaManifest.decode(file.manifest));
            assertTrue(receiver.store.looseArrived(file,plain));
        }
    }

    private static byte[] bytes(int size,int seed){byte[] b=new byte[size];new Random(seed).nextBytes(b);return b;}

    @Test public void fromOneOfTheOwnersOwnDevicesFilesAreTakenAtOnceAndKeptApartFromEveryNote() throws Exception {
        receiver.pair(SENDER,"Work laptop",true,sender);
        String wire=UUID.randomUUID().toString();
        Post.Landed landed=receiver.hear(offer(sender,receiver,wire,1,bytes(3000,1),bytes(5000,2)));
        assertTrue(landed.files);assertNull("nobody is asked",landed.asking);
        List<NoteStore.Transfer> all=receiver.store.transfers();
        assertEquals(1,all.size());NoteStore.Transfer one=all.get(0);
        assertFalse(one.out);assertEquals(Drop.FETCHING,one.state);assertEquals("Work laptop",one.name);assertEquals(2,one.files.size());
        assertFalse(one.allHere());assertEquals(8000,one.bytes());
        // Its files get names made here, never the sender's: a name the sender chose could land on a file kept here.
        for(NoteStore.Loose file:one.files){assertNotEquals(file.id,file.theirs);assertTrue(Enclosure.plainId(file.id));}
        fetchAll(one);
        NoteStore.Transfer now=receiver.store.transfer(one.id);
        assertTrue(now.allHere());
        receiver.store.settle(one.id,Drop.HERE);
        // Kept on Home, belonging to no note (docs/HOME.md, step 2): not a note's file, in a backup now, kept by the sweep,
        // sealed with the rest and only once, and new on Home until opened rather than new in the drop box.
        NoteStore.Loose first=now.files.get(0);
        assertEquals(Things.HOME,receiver.store.file(first.id).note);
        assertEquals(NoteStore.Branch.Kind.COLLECTION,receiver.store.file(first.id).held);
        assertNotNull(receiver.store.keptFile(first.id));
        assertEquals(2,receiver.store.everyFile().size());
        assertEquals(2,receiver.store.everyFileKept().size());
        receiver.store.sweep();
        assertTrue(receiver.store.fileFor(first.id).isFile());
        assertEquals(8000,receiver.store.weight());
        assertEquals(0,receiver.store.freshTransfers());
        assertEquals(2,receiver.store.freshOnHome());
        for(NoteStore.Loose file:now.files)receiver.store.opened(file.id);
        assertEquals(0,receiver.store.freshOnHome());
        assertTrue("nothing left in the drop box",receiver.store.transfers().isEmpty());
    }

    @Test public void putInANoteAFileBecomesThatNotesAttachmentAndLeavesTheReceivedFiles() throws Exception {
        receiver.pair(SENDER,"Work laptop",true,sender);
        String wire=UUID.randomUUID().toString();
        receiver.hear(offer(sender,receiver,wire,1,bytes(3000,3)));
        NoteStore.Transfer one=receiver.store.transfers().get(0);
        fetchAll(one);receiver.store.settle(one.id,Drop.HERE);
        NoteStore.Note note=new NoteStore.Note();note.book=receiver.store.someBook();note.title="Holiday";receiver.store.save(note);
        String file=one.files.get(0).id;
        receiver.store.intoNote(file,note.id);
        assertEquals(1,receiver.store.filesOf(NoteStore.Branch.Kind.PAGE,note.id).size());
        assertEquals(file,receiver.store.filesOf(NoteStore.Branch.Kind.PAGE,note.id).get(0).id);
        assertNull(receiver.store.loose(file));
        assertTrue("the same bytes, not a copy",receiver.store.fileFor(file).isFile());
        assertTrue("nothing left to show",receiver.store.transfers().isEmpty());
        // Offered again - the sender had not heard - it is answered, not fetched again.
        assertEquals(Drop.HERE,receiver.store.transferOnTheWire(wire,SENDER,false).state);
        receiver.hear(offer(sender,receiver,wire,1,bytes(3000,3)));
        assertTrue(receiver.store.transfers().isEmpty());
    }

    @Test public void fromAnybodyElseThePersonIsAskedAndARefusalFetchesNothing() throws Exception {
        receiver.pair(SENDER,"Ana",false,sender);
        String wire=UUID.randomUUID().toString();
        Post.Landed landed=receiver.hear(offer(sender,receiver,wire,1,bytes(2048,4),bytes(1024,5),bytes(1024,6)));
        assertTrue(landed.files);assertNotNull(landed.asking);
        assertEquals("Ana wants to send you 3 files (4 KB).",landed.said);
        NoteStore.Transfer one=receiver.store.transfer(landed.asking);
        assertEquals(Drop.ASKING,one.state);assertEquals(1,receiver.store.freshTransfers());
        assertTrue("nothing fetched while asked",receiver.store.transfersDue(System.currentTimeMillis()).isEmpty());
        // A file is not taken in while it is only asked about.
        assertFalse(receiver.store.looseArrived(one.files.get(0),bytes(2048,4)));
        Post.refuseSending(receiver.context,receiver.store,receiver.keys,one.id);
        assertEquals(Drop.REFUSED,receiver.store.transferOnTheWire(wire,SENDER,false).state);
        assertTrue("a refused sending is not shown",receiver.store.transfers().isEmpty());
        assertEquals(0,receiver.store.freshTransfers());
        // Offered again, it is not asked about again.
        Post.Landed again=receiver.hear(offer(sender,receiver,wire,1,bytes(2048,4)));
        assertNull(again.asking);assertTrue(receiver.store.transfers().isEmpty());
    }

    @Test public void acceptedAStrangersFilesAreFetched() throws Exception {
        receiver.pair(SENDER,"Ana",false,sender);
        Post.Landed landed=receiver.hear(offer(sender,receiver,UUID.randomUUID().toString(),1,bytes(2048,7)));
        Post.takeSending(receiver.context,receiver.store,receiver.keys,landed.asking);
        NoteStore.Transfer one=receiver.store.transfer(landed.asking);
        assertEquals(Drop.FETCHING,one.state);
        assertEquals(1,receiver.store.transfersDue(System.currentTimeMillis()).size());
        fetchAll(one);
        assertTrue(receiver.store.transfer(one.id).allHere());
    }

    @Test public void aDeviceNotPairedHereSendsNothing() throws Exception {
        Device stranger=new Device("stranger");
        Post.Landed landed=receiver.hear(offer(stranger,receiver,UUID.randomUUID().toString(),1,bytes(100,8)));
        assertFalse(landed.files);assertNull(landed.said);
        assertTrue(receiver.store.transfers().isEmpty());
    }

    @Test public void aLaterOfferSaysWhereTheFilesAreNowAndAnEarlierOneChangesNothing() throws Exception {
        receiver.pair(SENDER,"Work laptop",true,sender);
        String wire=UUID.randomUUID().toString();byte[] plain=bytes(4000,9);
        receiver.hear(offer(sender,receiver,wire,1,plain));
        NoteStore.Transfer one=receiver.store.transfers().get(0);
        String first=one.files.get(0).manifest;
        receiver.store.looseFailed(one.files.get(0).id);
        // The same file under the same name, somewhere new: offer 2 lists it again.
        List<Enclosure.Listed> again=List.of(new Enclosure.Listed(one.files.get(0).theirs,"photo-0.jpg","image/jpeg",plain.length,
            new MediaService(null,new BlobStore(shelf)).publish(plain,"application/octet-stream").encode()));
        receiver.hear(Envelope.seal(page(wire),2,2,Drop.wrap(again),sender.keys.signing(),receiver.keys.agreement().getPublic()));
        NoteStore.Loose now=receiver.store.transfer(one.id).files.get(0);
        assertNotEquals(first,now.manifest);assertEquals(0,now.tries);
        assertEquals(2,receiver.store.transfer(one.id).revision);
        // Offer 1 arriving late changes nothing.
        receiver.hear(offer(sender,receiver,wire,1,plain));
        assertEquals(now.manifest,receiver.store.transfer(one.id).files.get(0).manifest);
        assertEquals(1,receiver.store.transfers().size());
    }

    /** A sending made here, as the Send box makes one: the file copied in, then the rows. */
    private String sending(Device from,String to,String name,byte[] plain) throws Exception {
        NoteStore.Held held=from.store.opening(NoteStore.Branch.Kind.PAGE,"","ferry.png","image/png",plain.length);
        Files.write(from.store.fileFor(held.id).toPath(),plain);
        return from.store.sendFiles(to,name,List.of(held));
    }

    @Test public void onlyTheReceiversAnswerDeliversASendingAndThenItsCopiesGo() throws Exception {
        receiver.pair(SENDER,"Work laptop",true,sender);
        String id=sending(sender,RECEIVER,"Receiver",bytes(1000,10));
        NoteStore.Transfer made=sender.store.transfer(id);
        assertTrue(made.out);assertEquals(Drop.SENDING,made.state);assertTrue(made.files.get(0).here);
        assertEquals(1,sender.store.transfersDue(System.currentTimeMillis()).size());
        String file=made.files.get(0).id;
        sender.store.sweep();assertTrue("kept to be sent",sender.store.fileFor(file).isFile());
        sender.store.offered(id);
        assertEquals(Drop.WAITING,sender.store.transfer(id).state);
        // An answer from somebody it did not go to changes nothing.
        Device other=new Device("other");sender.pair(STRANGER,"Other",false,other);
        assertNull(sender.hear(said(other,sender,made.wire,1,Receipt.DROP_HAVE)).said);
        assertEquals(Drop.WAITING,sender.store.transfer(id).state);
        Post.Landed landed=sender.hear(said(receiver,sender,made.wire,1,Receipt.DROP_HAVE));
        assertTrue(landed.files);assertEquals("The file reached Receiver.",landed.said);
        assertEquals(Drop.DELIVERED,sender.store.transfer(id).state);
        assertFalse("the copy kept to send it is gone",sender.store.fileFor(file).isFile());
        assertEquals("still listed, as sent",1,sender.store.transfers().size());
        // A refusal arriving late does not undo it.
        sender.hear(said(receiver,sender,made.wire,1,Receipt.DROP_REFUSED));
        assertEquals(Drop.DELIVERED,sender.store.transfer(id).state);
    }

    @Test public void aRefusalReachesTheSenderAndACannotGetSendsThePiecesUp() throws Exception {
        String refused=sending(sender,RECEIVER,"Receiver",bytes(1000,11));
        sender.store.offered(refused);
        Post.Landed landed=sender.hear(said(receiver,sender,sender.store.transfer(refused).wire,1,Receipt.DROP_REFUSED));
        assertEquals("Receiver refused the file.",landed.said);
        assertEquals(Drop.REFUSED,sender.store.transfer(refused).state);
        String missing=sending(sender,RECEIVER,"Receiver",bytes(1000,12));
        sender.store.loosePublished(sender.store.transfer(missing).files.get(0).id,
            new MediaService(null,new BlobStore(shelf)).publish(bytes(1000,12),"application/octet-stream").encode());
        sender.store.offered(missing);
        NoteStore.Transfer one=sender.store.transfer(missing);
        // About an offer older than the latest: nothing.
        sender.hear(said(receiver,sender,one.wire,0,Receipt.DROP_MISSING));
        assertEquals(1,sender.store.transfer(missing).revision);
        sender.hear(said(receiver,sender,one.wire,1,Receipt.DROP_MISSING));
        NoteStore.Transfer now=sender.store.transfer(missing);
        assertEquals("offered again, as a later offer",2,now.revision);
        assertTrue("its pieces go up to a relay",now.files.get(0).relay);
        assertEquals(0,now.tries);
        // Taken off the list: not offered again, and its copy goes.
        String file=now.files.get(0).id;
        Post.stopSending(sender.context,sender.store,missing);
        assertNull(sender.store.transfer(missing));
        sender.store.sweep();
        assertFalse(sender.store.fileFor(file).isFile());
    }

    @Test public void aDeviceSaysItTakesFilesInItsHelloAndInFiveBytes() throws Exception {
        receiver.pair(SENDER,"Work laptop",true,sender);
        NoteStore.Contact them=null;for(NoteStore.Contact one:receiver.store.addresses())if(one.address.equals(SENDER))them=one;
        assertFalse(Post.takesFiles(receiver.context,them));
        receiver.hear(said(sender,receiver,"00000000-0000-0000-0000-000000000000",0,Receipt.TAKES_FILES));
        assertTrue(Post.takesFiles(receiver.context,them));
        // And in a hello, from a device paired since.
        Device fresh=new Device("fresh");fresh.pair(RECEIVER,"Receiver",false,receiver);
        receiver.pair(STRANGER,"Fresh",false,fresh);
        NoteStore.Contact freshOne=null;for(NoteStore.Contact one:receiver.store.addresses())if(one.address.equals(STRANGER))freshOne=one;
        assertFalse(Post.takesFiles(receiver.context,freshOne));
        byte[] hello=Hello.wrap(new Hello.Said("Fresh",STRANGER,Point.shorten(fresh.keys.agreement().getPublic()),Point.shorten(fresh.keys.signing().getPublic()),"","",false));
        receiver.hear(Envelope.seal(new byte[16],0,1,hello,fresh.keys.signing(),receiver.keys.agreement().getPublic()));
        assertTrue(Post.takesFiles(receiver.context,freshOne));
    }

    @Test public void aReceivedFileIsSealedWhenTheLockGoesOnAndOpenedWhenItComesOff() throws Exception {
        receiver.pair(SENDER,"Work laptop",true,sender);
        byte[] plain=bytes(3000,13);
        receiver.hear(offer(sender,receiver,UUID.randomUUID().toString(),1,plain));
        NoteStore.Transfer one=receiver.store.transfers().get(0);
        fetchAll(one);
        java.nio.file.Path kept=receiver.store.fileFor(one.files.get(0).id).toPath();
        assertFalse(DesktopFiles.sealed(kept));
        byte[] key=new byte[32];new Random(14).nextBytes(key);
        DesktopFiles.every(receiver.store,key,true);
        assertTrue(DesktopFiles.sealed(kept));
        DesktopFiles.every(receiver.store,key,false);
        assertArrayEquals(plain,Files.readAllBytes(kept));
    }

    /** A notebook written at version 25, opened by this build: it takes the step, keeps its notes, and has somewhere for sendings. */
    @Test public void aNotebookFromBeforeTakesTheStep() throws Exception {
        Context old=new Context(temp.newFolder("old"));
        try(java.sql.Connection db=java.sql.DriverManager.getConnection("jdbc:sqlite:"+old.getFilesDir().toPath().resolve("mininotes.db"));
            java.sql.Statement run=db.createStatement()) {
            for(String sql:SchemaMigrations.create().subList(0,2))run.execute(sql);
            for(String sql:SchemaMigrations.upgrade(1,25))run.execute(sql);
            run.execute("INSERT INTO notes(id,title,body,notebook,pinned,deleted,updated) VALUES('n-1','Groceries','Bread','',0,0,1)");
            run.execute("PRAGMA user_version=25");
        }
        NoteStore store=new NoteStore(old);open.add(store);
        try(org.mininotes.desktop.platform.database.Cursor row=store.getReadableDatabase().rawQuery("PRAGMA user_version",null)){row.moveToFirst();assertEquals(SchemaMigrations.VERSION,row.getInt(0));}
        assertEquals("Bread",store.get("n-1").body);
        assertTrue(store.transfers().isEmpty());assertEquals(0,store.freshTransfers());
    }
}
