package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.*;
import org.mininotes.desktop.platform.content.Context;

/**
 * What the PC holds for its owner's phones, kept in the notebook (schema 25): kept once, replaced when sealed
 * again, bounded, let go when collected or too old, and still there when the notebook is opened again. The
 * network is not used; see HomeDoorTest for that.
 */
public class DesktopHomeTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final List<NoteStore> open=new ArrayList<>();
    @After public void close(){for(NoteStore one:open)one.close();}

    private NoteStore store(java.io.File folder){NoteStore made=new NoteStore(new Context(folder));made.getWritableDatabase();open.add(made);return made;}

    private static final String PHONE="aa".repeat(32), OTHER="bb".repeat(32), PAGE="0".repeat(32);
    /** Something held, with as much sealed inside it as it is long less a little, as a note's envelope has. */
    private static Home.Held held(String recipient,long revision,long kept,byte[] bytes){return new Home.Held(OTHER,recipient,PAGE,revision,Math.max(0,bytes.length-4),kept,bytes);}
    private static byte[] bytes(int size,int fill){byte[] out=new byte[size];Arrays.fill(out,(byte)fill);return out;}
    /** Ten bytes no other number makes. */
    private static byte[] unique(int which){return java.nio.ByteBuffer.allocate(10).putInt(which).array();}

    @Test public void keptOnceReplacedWhenSealedAgainAndLetGoOnlyByWhoItIsFor() throws Exception {
        NoteStore store=store(temp.newFolder("pc"));
        long now=System.currentTimeMillis();
        Home.Held first=held(PHONE,3,now,bytes(40,1));
        assertTrue(store.holdFor(first));assertTrue(store.holdFor(first));
        assertEquals(1,store.countHeldFor(PHONE));
        // The same note at the same revision, sealed again: as much inside, other bytes - and a byte more of them,
        // as a signature can be. It takes the place of the first.
        Home.Held again=new Home.Held(OTHER,PHONE,PAGE,3,36,now+1,bytes(41,2));
        assertTrue(store.holdFor(again));
        assertEquals(1,store.countHeldFor(PHONE));
        assertArrayEquals(again.bytes,store.heldFor(PHONE,16,Home.PULL_BYTES).get(0).bytes);
        // A different message about the same note - an answer is smaller - is kept beside it.
        assertTrue(store.holdFor(held(PHONE,3,now+2,bytes(5,3))));
        assertEquals(2,store.countHeldFor(PHONE));
        assertEquals(0,store.countHeldFor(OTHER));
        assertEquals("only the device it is for can say it has it",0,store.letGoFor(OTHER,List.of(again.id)));
        assertEquals(1,store.letGoFor(PHONE,List.of(again.id)));
        assertEquals(1,store.countHeldFor(null));
    }

    @Test public void thereIsRoomForSoManyAndNothingIsKeptForEver() throws Exception {
        NoteStore store=store(temp.newFolder("pc"));
        long now=System.currentTimeMillis();
        for(int one=0;one<Home.MOST_FOR_ONE;one++)assertTrue(store.holdFor(held(PHONE,one,now,unique(one))));
        assertFalse("full, for that phone",store.holdFor(held(PHONE,9999,now,unique(9999))));
        assertTrue("another has room of its own",store.holdFor(held(OTHER,1,now,unique(10_000))));
        assertEquals(Home.PULL_MOST,store.heldFor(PHONE,Home.PULL_MOST,Home.PULL_BYTES).size());
        assertEquals("oldest first",0,store.heldFor(PHONE,1,Home.PULL_BYTES).get(0).revision);
        // Kept a fortnight ago and a moment: gone as soon as the shelf is looked at.
        NoteStore other=store(temp.newFolder("other"));
        assertTrue(other.holdFor(held(PHONE,1,now-Home.KEPT_FOR-60_000,bytes(10,1))));
        assertTrue(other.heldFor(PHONE,16,Home.PULL_BYTES).isEmpty());
        assertEquals(0,other.countHeldFor(null));
    }

    @Test public void whatIsHeldIsStillThereWhenTheNotebookIsOpenedAgain() throws Exception {
        java.io.File folder=temp.newFolder("pc");
        NoteStore store=store(folder);
        Home.Held one=held(PHONE,1,System.currentTimeMillis(),bytes(30,4));
        assertTrue(store.holdFor(one));
        store.close();open.remove(store);
        NoteStore reopened=store(folder);
        List<Home.Held> kept=reopened.heldFor(PHONE,16,Home.PULL_BYTES);
        assertEquals(1,kept.size());assertEquals(one.id,kept.get(0).id);assertArrayEquals(one.bytes,kept.get(0).bytes);
        assertEquals(OTHER,kept.get(0).sender);assertEquals(PAGE,kept.get(0).page);
    }

    /** A notebook written at version 24, opened by this build: it takes the step, keeps its notes, and has a shelf. */
    @Test public void aNotebookFromBeforeTakesTheStepAndHasAShelf() throws Exception {
        Context old=new Context(temp.newFolder("old"));
        try(java.sql.Connection db=java.sql.DriverManager.getConnection("jdbc:sqlite:"+old.getFilesDir().toPath().resolve("mininotes.db"));
            java.sql.Statement run=db.createStatement()) {
            for(String sql:SchemaMigrations.create().subList(0,2))run.execute(sql);
            for(String sql:SchemaMigrations.upgrade(1,24))run.execute(sql);
            run.execute("INSERT INTO notes(id,title,body,notebook,pinned,deleted,updated) VALUES('n-1','Groceries','Bread','',0,0,1)");
            run.execute("PRAGMA user_version=24");
        }
        NoteStore store=new NoteStore(old);open.add(store);
        try(org.mininotes.desktop.platform.database.Cursor row=store.getReadableDatabase().rawQuery("PRAGMA user_version",null)){row.moveToFirst();assertEquals(SchemaMigrations.VERSION,row.getInt(0));}
        assertEquals("Bread",store.get("n-1").body);
        assertEquals(0,store.countHeldFor(null));
        assertTrue(store.holdFor(held(PHONE,1,System.currentTimeMillis(),bytes(20,5))));
        assertEquals(1,store.countHeldFor(PHONE));
    }

    @Test public void thePcsHomeKeepsInItsNotebook() throws Exception {
        NoteStore store=store(temp.newFolder("pc"));
        KeyPair agreement=Envelope.keys(),phone=Envelope.keys(),writer=Envelope.keys(),phoneAgreement=Envelope.keys();
        String phoneKey=Courier.hex(Envelope.fingerprint(phone.getPublic())),writerKey=Courier.hex(Envelope.fingerprint(writer.getPublic()));
        Home.Host host=new Home.Host("0x"+"CD".repeat(20));
        host.notebook(new Home.Notebook(agreement.getPrivate(),Set.of(phoneKey,writerKey),Set.of(phoneKey),store));
        long now=System.currentTimeMillis();
        assertEquals(Home.OK,Home.answered(host.said(Envelope.seal(new byte[16],0,now,Home.pull(4),phone,agreement.getPublic()),new byte[0],now)).status);
        byte[] note=Envelope.seal(new byte[16],2,now,"Bread".getBytes(StandardCharsets.UTF_8),writer,phoneAgreement.getPublic());
        byte[] whose=Envelope.fingerprint(phone.getPublic());
        assertEquals(Home.OK,Home.answered(host.said(Envelope.seal(new byte[16],0,now,Home.deposit(whose,note),writer,agreement.getPublic()),note,now)).status);
        assertEquals(1,store.countHeldFor(phoneKey));
        assertEquals("Holding 1 message for your phones until they collect it.",Home.said(host.holding(),host.collectingNow(now)));
        // Locked: the round that says what the notebook is says nothing (Node.everyBeat(null)), and the home
        // says "not now" to everybody, not anything worse.
        host.notebook(null);
        assertEquals(-1,host.holding());
        assertEquals(Home.BUSY,Home.answered(host.said(Envelope.seal(new byte[16],0,now,Home.pull(4),phone,agreement.getPublic()),new byte[0],now)).status);
    }
}
