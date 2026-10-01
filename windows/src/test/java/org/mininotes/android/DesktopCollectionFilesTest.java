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
 * Files kept with a collection travel "exactly as a note's are, with the collection id where the note id goes"
 * (docs/HOME.md, step 4): listed in its carton, sent up, fetched on the far side and kept with the collection there, said
 * to be had, and taken out when the list no longer names them. Two synthetic devices, no node and no network: each message
 * is sealed and handed to the device it is for, and the pieces are left on one shelf both can reach, as a relay holds them.
 */
public class DesktopCollectionFilesTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final List<NoteStore> open=new ArrayList<>();
    @After public void close(){for(NoteStore one:open)one.close();NoteStore.fileKey=NoteStore::key;}

    private static final String OWNER="MxOwnerShelf@127.0.0.1:9501",WRITER="MxWriterShelf@127.0.0.1:9502";

    private final class Device {
        final Context context;final NoteStore store;final Keys keys;
        Device(String name) throws Exception {
            context=new Context(temp.newFolder(name));store=new NoteStore(context);store.getWritableDatabase();open.add(store);
            keys=new Keys(context);store.mySigningKey=Base64.getEncoder().encodeToString(keys.signing().getPublic().getEncoded());
        }
        void pair(String address,String name,Device other) throws Exception {
            store.pairedWith(address,name,false,other.keys.agreement().getPublic().getEncoded(),other.keys.signing().getPublic().getEncoded());
        }
        Post.Landed hear(byte[] sealed){return Post.arrived(context,store,keys,sealed);}
        String keep(String collection,byte[] bytes,String name) throws Exception {
            String id=UUID.randomUUID().toString();
            Files.write(store.fileFor(id).toPath(),bytes);
            store.keep(new NoteStore.Held(id,collection,name,"application/pdf",bytes.length,System.currentTimeMillis(),NoteStore.Branch.Kind.COLLECTION));
            return id;
        }
    }

    private static byte[] page(String id){UUID u=UUID.fromString(id);return java.nio.ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();}

    /** The collection as it goes to the writer now, its files listed, not asking to be answered: that would start a node. */
    private byte[] carton(Device from,Device to,String collection) throws Exception {
        Carton.Sent c=from.store.carton(collection,WRITER,true);
        Carton.Sent quiet=new Carton.Sent(c.id,c.name,c.icon,c.image,c.tint,c.ordinal,c.path,c.writes,c.scope,c.target,c.members,false,c.files,c.filesAsOf);
        return Envelope.seal(Things.envelopeId(collection),from.store.revisionOf(collection),1,Carton.wrap(quiet,Envelope.MAX_TEXT),
            from.keys.signing(),to.keys.agreement().getPublic());
    }

    private static byte[] said(Device from,Device to,String file,int what) throws Exception {
        return Envelope.seal(page(file),0,1,Receipt.wrap(what),from.keys.signing(),to.keys.agreement().getPublic());
    }

    private Device owner,writer;private String trip;private File shelf;
    @Before public void two() throws Exception {
        Hashes.setSha3(Sha3::of);
        owner=new Device("owner");writer=new Device("writer");
        owner.pair(WRITER,"Writer",writer);writer.pair(OWNER,"Owner",owner);
        trip=owner.store.addCollection("Trip").id;
        owner.store.addShare(new Sharing.Rule(Sharing.Scope.COLLECTION,trip,WRITER,true));
        shelf=temp.newFolder("relay");
    }

    private void goesUp(Device from,String file) throws Exception {
        MediaManifest made=new MediaService(null,new BlobStore(shelf)).publish(from.store.bytesOf(from.store.file(file)),"application/pdf");
        from.store.published(file,made.encode());
    }

    private int fetches(Device to) throws Exception {
        int kept=0;
        for(NoteStore.Incoming one:to.store.toFetch(System.currentTimeMillis())) {
            byte[] plain=new MediaService(null,new BlobStore(shelf)).fetch(MediaManifest.decode(one.manifest));
            if(to.store.fileArrived(one,plain))kept++;
        }
        return kept;
    }

    /** The writer's own id for the owner's collection, once it has arrived. */
    private String there() {
        for(String id:writer.store.collectionIds())if("Trip".equals(writer.store.nameOf(id,true)))return id;
        return null;
    }

    @Test public void aFileKeptWithACollectionGoesInItsCartonIsFetchedThereAndTakenOutWhenUnlisted() throws Exception {
        byte[] map=new byte[40_000];new Random(7).nextBytes(map);
        String file=owner.keep(trip,map,"map.pdf");
        // Shared, so it is to go up - as a collection's, and it says so.
        List<NoteStore.Going> up=owner.store.toPublish(System.currentTimeMillis());
        assertEquals(1,up.size());assertEquals(NoteStore.Branch.Kind.COLLECTION,up.get(0).file.held);
        goesUp(owner,file);
        assertTrue(owner.store.toPublish(System.currentTimeMillis()).isEmpty());
        // Its carton is owed, and lists it, saying where it is; until the writer says it has it, it is told again.
        assertEquals(1,owner.store.cartonsOwed(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING).size());
        Carton.Sent listed=owner.store.carton(trip,WRITER,true);
        assertEquals(1,listed.files.size());assertEquals(file,listed.files.get(0).id);assertTrue(listed.files.get(0).fetchable());
        long now=System.currentTimeMillis();
        assertEquals(Set.of(WRITER),owner.store.toTell(now).get(trip));
        assertEquals(NoteStore.Branch.Kind.COLLECTION,owner.store.keptWith(trip));

        // It arrives, and the file waits to be fetched there, for the writer's own collection.
        writer.hear(carton(owner,writer,trip));
        String here=there();
        assertNotNull("the collection is built there",here);
        List<NoteStore.Incoming> coming=writer.store.toFetch(System.currentTimeMillis());
        assertEquals(1,coming.size());assertEquals(here,coming.get(0).note);
        long revision=writer.store.revisionOf(here);

        // Fetched and kept with the collection, sealed with the writer's own key, from the owner.
        byte[] key=new byte[32];new Random(9).nextBytes(key);NoteStore.fileKey=()->key.clone();
        assertEquals(1,fetches(writer));
        List<NoteStore.Held> kept=writer.store.filesOf(NoteStore.Branch.Kind.COLLECTION,here);
        assertEquals(1,kept.size());assertEquals(file,kept.get(0).id);assertEquals("map.pdf",kept.get(0).name);
        assertEquals(NoteStore.Branch.Kind.COLLECTION,kept.get(0).held);
        assertEquals(OWNER,writer.store.originOf(file));
        assertArrayEquals(map,writer.store.bytesOf(kept.get(0)));
        assertEquals("fetched, not changed: nothing is owed back for it",revision,writer.store.revisionOf(here));
        boolean shown=false;for(NoteStore.Branch one:writer.store.contents(here))if(one.kind==NoteStore.Branch.Kind.FILE&&one.id.equals(file))shown=true;
        assertTrue("among what the collection holds",shown);
        assertTrue(writer.store.toFetch(System.currentTimeMillis()).isEmpty());
        // What is said back to the owner about it, at its next list.
        assertEquals(List.of(file),writer.store.haveHere(here,listed.files));

        // The writer says it has it: it is told no more.
        owner.hear(said(writer,owner,file,Receipt.FILE_HERE));
        assertNull(owner.store.toTell(now+3_600_000).get(trip));

        // The owner takes it out: the collection has changed, its next carton lists nothing, and the writer's copy goes.
        long before=owner.store.revisionOf(trip);
        owner.store.drop(file);
        assertEquals(before+1,owner.store.revisionOf(trip));
        assertTrue(owner.store.carton(trip,WRITER,true).files.isEmpty());
        Thread.sleep(2);
        writer.hear(carton(owner,writer,trip));
        assertTrue(writer.store.filesOf(NoteStore.Branch.Kind.COLLECTION,here).isEmpty());
        assertFalse(writer.store.fileFor(file).exists());
    }

    @Test public void whatTheWriterTookOutOfACollectionIsNotFetchedBack() throws Exception {
        String file=owner.keep(trip,"a plan".getBytes(),"plan.pdf");goesUp(owner,file);
        writer.hear(carton(owner,writer,trip));
        assertEquals(1,fetches(writer));
        String here=there();
        writer.store.drop(file);
        assertNull(writer.store.file(file));
        Thread.sleep(2);
        writer.hear(carton(owner,writer,trip));
        assertTrue(writer.store.toFetch(System.currentTimeMillis()).isEmpty());
        assertTrue(writer.store.filesOf(NoteStore.Branch.Kind.COLLECTION,here).isEmpty());
    }

    @Test public void aReadersListIsNotTakenAndHomesOwnFilesGoNowhere() throws Exception {
        // Home's files - those that came, kept on Home - are this device's: never gone up, never listed.
        String onHome=UUID.randomUUID().toString();
        Files.write(owner.store.fileFor(onHome).toPath(),"received".getBytes());
        owner.store.keep(new NoteStore.Held(onHome,Things.HOME,"received.txt","text/plain",8,System.currentTimeMillis(),NoteStore.Branch.Kind.COLLECTION));
        for(NoteStore.Going one:owner.store.toPublish(System.currentTimeMillis()))assertNotEquals(onHome,one.file.id);
        assertTrue(owner.store.enclosed(Things.HOME).isEmpty());
        assertTrue(owner.store.goesWith(Things.HOME).isEmpty());
        assertFalse(owner.store.listed(Things.HOME,WRITER,1,List.of(new Enclosure.Listed(UUID.randomUUID().toString(),"x.txt","text/plain",1,"{}"))));
        // A collection somebody may only read: they are sent it, and fetch its files.
        owner.store.setLevel(Sharing.Scope.THING,trip,WRITER,Sharing.Level.READ,null);
        String file=owner.keep(trip,"a map".getBytes(),"map.pdf");goesUp(owner,file);
        writer.hear(carton(owner,writer,trip));
        String here=there();
        assertNotNull(here);
        assertEquals(1,fetches(writer));
        // Reading only, as its list says: it sends no carton of it, and puts none of its files up, since both would be refused.
        writer.store.stands(Sharing.Scope.THING,here,Sharing.Level.READ,2);
        assertEquals(Sharing.Level.READ,writer.store.myLevel(Sharing.Scope.THING,here));
        assertTrue(writer.store.goesWith(here).isEmpty());
        String own=writer.keep(here,"mine".getBytes(),"mine.pdf");
        for(NoteStore.Going one:writer.store.toPublish(System.currentTimeMillis()))assertNotEquals(own,one.file.id);
        assertTrue(writer.store.cartonsOwed(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING).isEmpty());
    }
}
