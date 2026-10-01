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
import org.mininotes.desktop.platform.database.Cursor;

/**
 * Two synthetic devices and a file kept with a note they share. No node and no network: each message is
 * sealed and handed to the device it is for, and the pieces of a file are left on one shelf both can reach,
 * as a relay would hold them.
 */
public class DesktopFilesTravelTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final List<NoteStore> open=new ArrayList<>();
    @After public void close(){for(NoteStore one:open)one.close();NoteStore.fileKey=NoteStore::key;}

    private static final String OWNER="MxOwnerFixture@127.0.0.1:9201",WRITER="MxWriterFixture@127.0.0.1:9202";

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
        String attach(String note,byte[] bytes,String name) throws Exception {
            String id=UUID.randomUUID().toString();
            Files.write(store.fileFor(id).toPath(),bytes);
            store.keep(new NoteStore.Held(id,note,name,"application/octet-stream",bytes.length,System.currentTimeMillis()));
            return id;
        }
    }

    private static byte[] page(String id){UUID u=UUID.fromString(id);return java.nio.ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();}

    /** The owner's note, as it goes to the writer: with the list its files make now, written at {@code asOf}. */
    private static byte[] listFor(Device from,Device to,String note,long revision,long asOf,List<Enclosure.Listed> files) throws Exception {
        byte[] parcel=Parcel.wrap(new Parcel.Sent("c","Home","b","Lists","Groceries","Bread\nMilk",true,
            Collections.<Parcel.Member>emptyList(),"PAGE",note,false,-1L,false,files,asOf),Envelope.MAX_TEXT);
        return Envelope.seal(page(note),revision,asOf,parcel,from.keys.signing(),to.keys.agreement().getPublic());
    }

    private static byte[] said(Device from,Device to,String file,int what) throws Exception {
        return Envelope.seal(page(file),0,1,Receipt.wrap(what),from.keys.signing(),to.keys.agreement().getPublic());
    }

    private Device owner,writer;private String note;private File shelf;
    @Before public void two() throws Exception {
        Hashes.setSha3(Sha3::of);
        owner=new Device("owner");writer=new Device("writer");
        owner.pair(WRITER,"Writer",writer);writer.pair(OWNER,"Owner",owner);
        note=UUID.randomUUID().toString();
        NoteStore.Note n=new NoteStore.Note();n.id=note;n.book=owner.store.someBook();n.body="Bread\nMilk";n.revision=1;owner.store.save(n);
        owner.store.setLevel(Sharing.Scope.PAGE,note,WRITER,Sharing.Level.WRITE,null);
        writer.store.addShare(new Sharing.Rule(Sharing.Scope.PAGE,note,OWNER,true));
        shelf=temp.newFolder("relay");
    }

    /** Sent up the way Post does it, onto the shelf both devices reach. */
    private void goesUp(Device from,String file) throws Exception {
        MediaManifest made=new MediaService(null,new BlobStore(shelf)).publish(from.store.bytesOf(from.store.file(file)),"application/octet-stream");
        from.store.published(file,made.encode());
    }

    /** Fetched the way Post does it, from the same shelf. */
    private int fetches(Device to) throws Exception {
        int kept=0;
        for(NoteStore.Incoming one:to.store.toFetch(System.currentTimeMillis())) {
            byte[] plain=new MediaService(null,new BlobStore(shelf)).fetch(MediaManifest.decode(one.manifest));
            if(to.store.fileArrived(one,plain))kept++;
        }
        return kept;
    }

    @Test public void aFileGoesWithTheNoteIsKeptSealedAndIsSaidToBeHad() throws Exception {
        byte[] scan=new byte[300_000];new Random(3).nextBytes(scan);
        String file=owner.attach(note,scan,"scan.pdf");
        assertEquals(Enclosure.NOT_SENT,owner.store.fileStates(note).get(file));
        assertArrayEquals(new int[]{0,1,0},owner.store.fileCounts(note));
        assertEquals("shared, so it is to go up",1,owner.store.toPublish(System.currentTimeMillis()).size());

        goesUp(owner,file);
        assertTrue(owner.store.toPublish(System.currentTimeMillis()).isEmpty());
        assertEquals(Enclosure.NOT_EVERYBODY,owner.store.fileStates(note).get(file));

        // The note arrives with its list; the file waits to be fetched, and the line says so.
        writer.hear(listFor(owner,writer,note,1,10,owner.store.enclosed(note)));
        assertEquals("Bread\nMilk",writer.store.get(note).body);
        assertArrayEquals(new int[]{0,0,1},writer.store.fileCounts(note));

        // Fetched, and kept sealed with the writer's own key, as anything kept on a locked notebook is.
        byte[] key=new byte[32];new Random(9).nextBytes(key);NoteStore.fileKey=()->key.clone();
        assertEquals(1,fetches(writer));
        List<NoteStore.Held> there=writer.store.filesOf(NoteStore.Branch.Kind.PAGE,note);
        assertEquals(1,there.size());assertEquals(file,there.get(0).id);assertEquals("scan.pdf",there.get(0).name);
        assertEquals(OWNER,writer.store.originOf(file));
        byte[] head=Arrays.copyOf(Files.readAllBytes(writer.store.fileFor(file).toPath()),Sealed.MAGIC.length);
        assertTrue("sealed on the way in",Sealed.is(head));
        assertArrayEquals(scan,writer.store.bytesOf(there.get(0)));
        assertArrayEquals("nothing still coming, nothing going: its only other holder is where it came from",
            new int[]{0,0,0},writer.store.fileCounts(note));
        assertTrue(writer.store.toFetch(System.currentTimeMillis()).isEmpty());

        // The writer says it has it; only then does the owner's card stop saying it is still going.
        owner.hear(said(writer,owner,file,Receipt.FILE_HERE));
        assertEquals("",owner.store.fileStates(note).get(file));
        assertArrayEquals(new int[]{0,0,0},owner.store.fileCounts(note));
    }

    @Test public void takenOutByWhoeverPutItInAndByNobodyElse() throws Exception {
        String theirs=owner.attach(note,"a list".getBytes(),"list.txt");goesUp(owner,theirs);
        writer.hear(listFor(owner,writer,note,1,10,owner.store.enclosed(note)));
        assertEquals(1,fetches(writer));
        // The writer adds one of its own.
        String mine=writer.attach(note,"my own".getBytes(),"mine.txt");

        // A list without the owner's file, but written before the one already taken: changes nothing.
        writer.hear(listFor(owner,writer,note,1,5,Collections.<Enclosure.Listed>emptyList()));
        assertNotNull(writer.store.file(theirs));
        // A list that says nothing about files - an older build - changes nothing either.
        writer.hear(listFor(owner,writer,note,1,20,null));
        assertNotNull(writer.store.file(theirs));

        // The owner takes it out; its next list goes without it; the writer's copy goes, and its own stays.
        owner.store.drop(theirs);
        writer.hear(listFor(owner,writer,note,1,30,owner.store.enclosed(note)));
        assertNull(writer.store.file(theirs));assertFalse(writer.store.fileFor(theirs).exists());
        assertNotNull(writer.store.file(mine));assertTrue(writer.store.fileFor(mine).exists());

        // And the owner, having taken it out, does not fetch it back from a list that still names it.
        List<Enclosure.Listed> stale=Collections.singletonList(new Enclosure.Listed(theirs,"list.txt","text/plain",6,"{\"v\":\"1\"}"));
        owner.hear(listFor(writer,owner,note,1,40,stale));
        assertTrue(owner.store.toFetch(System.currentTimeMillis()).isEmpty());
    }

    @Test public void whatTheWriterTookOutItselfIsNotFetchedBack() throws Exception {
        String file=owner.attach(note,"a map".getBytes(),"map.png");goesUp(owner,file);
        writer.hear(listFor(owner,writer,note,1,10,owner.store.enclosed(note)));
        assertEquals(1,fetches(writer));
        writer.store.drop(file);
        writer.hear(listFor(owner,writer,note,1,20,owner.store.enclosed(note)));
        assertTrue(writer.store.toFetch(System.currentTimeMillis()).isEmpty());
        assertNull(writer.store.file(file));
    }

    @Test public void aFileTooBigToGoSaysSoAndIsNeverFetched() throws Exception {
        // Only its row: the bytes of seventeen megabytes are not needed to say it will not go.
        String big=UUID.randomUUID().toString();
        owner.store.keep(new NoteStore.Held(big,note,"film.mov","video/quicktime",Enclosure.MOST+1,System.currentTimeMillis()));
        assertEquals(Enclosure.TOO_BIG,owner.store.fileStates(note).get(big));
        assertArrayEquals(new int[]{1,0,0},owner.store.fileCounts(note));
        assertTrue(owner.store.toPublish(System.currentTimeMillis()).isEmpty());
        List<Enclosure.Listed> listed=owner.store.enclosed(note);
        assertEquals(1,listed.size());assertFalse(listed.get(0).fetchable());
        writer.hear(listFor(owner,writer,note,1,10,listed));
        assertTrue(writer.store.toFetch(System.currentTimeMillis()).isEmpty());
    }

    @Test public void somebodyWhoCannotGetItAsksForItToGoUpAgainButNotAtOnce() throws Exception {
        String file=owner.attach(note,"a receipt".getBytes(),"receipt.jpg");goesUp(owner,file);
        // Gone up a moment ago: more likely a relay's fault than a shelf that let it go.
        owner.hear(said(writer,owner,file,Receipt.FILE_MISSING));
        assertTrue(owner.store.toPublish(System.currentTimeMillis()).isEmpty());
        // Gone up long ago: it goes up again.
        owner.store.getWritableDatabase().execSQL("UPDATE published SET at=1 WHERE id=?",new Object[]{file});
        owner.hear(said(writer,owner,file,Receipt.FILE_MISSING));
        List<NoteStore.Going> again=owner.store.toPublish(System.currentTimeMillis());
        assertEquals(1,again.size());assertFalse("what it was is kept, to be let go once it is up again",again.get(0).manifest.isEmpty());
    }

    @Test public void theListGoesAgainOnlyToWhoeverHasNotSaidTheyHaveIt() throws Exception {
        String file=owner.attach(note,"a plan".getBytes(),"plan.txt");goesUp(owner,file);
        long now=System.currentTimeMillis();
        assertEquals(Collections.singleton(WRITER),owner.store.toTell(now).get(note));
        owner.store.told(Collections.singletonList(file),WRITER);
        assertNull("told a moment ago",owner.store.toTell(now+1000).get(note));
        assertNotNull("told again after a minute",owner.store.toTell(now+61_000).get(note));
        owner.hear(said(writer,owner,file,Receipt.FILE_HERE));
        assertTrue(owner.store.toTell(now+3_600_000).isEmpty());
    }

    /** A notebook written at version 23, opened by this build: every file it kept is still kept, and is its own. */
    @Test public void aNotebookFromBeforeTakesTheStepAndKeepsItsFiles() throws Exception {
        Context old=new Context(temp.newFolder("old"));
        try(java.sql.Connection db=java.sql.DriverManager.getConnection("jdbc:sqlite:"+old.getFilesDir().toPath().resolve("mininotes.db"));
            java.sql.Statement run=db.createStatement()) {
            for(String sql:SchemaMigrations.create().subList(0,2))run.execute(sql);
            for(String sql:SchemaMigrations.upgrade(1,23))run.execute(sql);
            run.execute("INSERT INTO files(id,note,name,kind,bytes,added,place,held) VALUES('f-1','n-1','old.txt','text/plain',3,1,0,'note')");
            run.execute("PRAGMA user_version=23");
        }
        try(NoteStore store=new NoteStore(old)) {
            try(Cursor row=store.getReadableDatabase().rawQuery("PRAGMA user_version",null)){row.moveToFirst();assertEquals(SchemaMigrations.VERSION,row.getInt(0));}
            assertEquals("old.txt",store.file("f-1").name);
            assertEquals("",store.originOf("f-1"));
            assertEquals("",store.manifestOf("f-1"));
            assertTrue(store.toFetch(System.currentTimeMillis()).isEmpty());
        }
    }
}
