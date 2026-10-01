package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import org.mininotes.desktop.platform.content.Context;

public class DesktopSyncStatusTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private NoteStore store;
    private Context context;
    private NoteStore.Note note;
    @Before public void open() throws Exception {
        context=new Context(temp.newFolder("sync-times").toPath().toFile());
        store=new NoteStore(context);note=new NoteStore.Note();note.book=store.someBook();
        note.body="Synthetic note";note.revision=1;store.save(note);
    }
    @After public void close(){store.close();}
    private void share(String address){store.setLevel(Sharing.Scope.PAGE,note.id,address,Sharing.Level.WRITE,null);}
    @Test public void relayAcceptanceIsNotConfirmationAndConfirmationSurvivesReopen() {
        assertEquals("Only here",SyncStatus.read(store,note.id).status());
        share("peer-a");store.handedOver("peer-a",note.id,note.revision);
        assertEquals("Waiting for delivery",SyncStatus.read(store,note.id).status());
        store.acknowledged("peer-a",note.id,note.revision,true);
        var confirmed=SyncStatus.read(store,note.id);
        assertEquals("Sync confirmed",confirmed.status());assertTrue(confirmed.confirmed()>0);
        store.close();store=new NoteStore(context);
        assertEquals(confirmed,SyncStatus.read(store,note.id));
    }
    @Test public void newerEditsAndLateReceiptsDoNotWearOldConfirmation() {
        share("peer-a");store.acknowledged("peer-a",note.id,1,true);
        note=DesktopEdits.save(store,note,"","Changed locally");
        store.acknowledged("peer-a",note.id,1,true);
        var state=SyncStatus.read(store,note.id);
        assertEquals("Waiting for delivery",state.status());assertEquals(0,state.confirmed());
        assertEquals(note.updated,state.saved());
    }
    @Test public void everyCurrentRecipientMustConfirmMatchingVersion() {
        share("peer-a");share("peer-b");store.agreedOn("peer-a",note.id,1);
        assertEquals("Waiting for delivery",SyncStatus.read(store,note.id).status());
        store.acknowledged("peer-b",note.id,1,false);
        assertEquals("Waiting for matching versions",SyncStatus.read(store,note.id).status());
        store.acknowledged("peer-b",note.id,1,true);
        assertEquals("Sync confirmed",SyncStatus.read(store,note.id).status());
        share("peer-c");assertEquals("Waiting for delivery",SyncStatus.read(store,note.id).status());
    }
    @Test public void oneShortLineAndTheWholeTimeOnAsking() {
        var now=java.time.ZonedDateTime.of(2026,9,25,15,0,0,0,java.time.ZoneId.of("Europe/Istanbul"));
        long today=now.withHour(9).withMinute(5).toInstant().toEpochMilli(),earlier=now.minusDays(2).toInstant().toEpochMilli();
        long lastYear=now.minusYears(1).toInstant().toEpochMilli();
        assertEquals("Only on this PC · saved 23 Sep, 15:00",new SyncStatus.State(earlier,0,SyncStatus.ONLY_HERE).brief("this PC",now));
        assertEquals("✓ Synced 09:05",new SyncStatus.State(earlier,today,SyncStatus.CONFIRMED).brief("this PC",now));
        assertEquals("↑ Saved 09:05 · waiting to sync",new SyncStatus.State(today,0,SyncStatus.WAITING).brief("this PC",now));
        assertEquals("↑ Saved 25 Sep 2025 · waiting for the others",new SyncStatus.State(lastYear,0,SyncStatus.MATCHING).brief("this PC",now));
        String detail=new SyncStatus.State(earlier,today,SyncStatus.CONFIRMED).detail("this PC");
        assertTrue(detail.startsWith("Saved on this PC: "));assertTrue(detail.contains("Everyone it is shared with has this version: "));
    }

    private SyncMark markOf(){var who=SyncStatus.who(store,note.id);return SyncStatus.mark(store,note.id,who,false);}
    private SyncMark personOf(String address) {
        for(var one:SyncStatus.who(store,note.id))if(one.address().equals(address))return one.mark();
        return null;
    }

    @Test public void theMarkUnderTheTitleFollowsEachPersonFromTheNotebook() {
        assertEquals(SyncMark.HERE,markOf());assertTrue(SyncStatus.who(store,note.id).isEmpty());
        share("peer-a");share("peer-b");
        assertEquals(SyncMark.WAITING,markOf());assertEquals(SyncMark.WAITING,personOf("peer-a"));
        // Delivered, not yet said to match: sent, amber dots. Matching: the green tick, for that one person only.
        store.acknowledged("peer-a",note.id,1,false);
        assertEquals(SyncMark.SENT,personOf("peer-a"));assertEquals(SyncMark.WAITING,markOf());
        store.acknowledged("peer-a",note.id,1,true);store.acknowledged("peer-b",note.id,1,true);
        assertEquals(SyncMark.GONE,personOf("peer-a"));assertEquals(SyncMark.GONE,markOf());
        // Words on the screen the notebook has not had: waiting, whatever the notebook says.
        assertEquals(SyncMark.WAITING,SyncStatus.mark(store,note.id,SyncStatus.who(store,note.id),true));
        // Paused here outranks the rest, and the tiles say what the title line says.
        store.refuse("peer-a",note.id,NoteStore.Branch.Kind.PAGE);
        assertEquals(SyncMark.PAUSED,markOf());
        for(NoteStore.Branch line:store.inside(NoteStore.Branch.Kind.BOOK,note.book).holds)
            if(line.id.equals(note.id))assertEquals(SyncMark.PAUSED,line.mark());
    }

    @Test public void aFileStillGoingKeepsThePersonWaitingAndOneTooBigDoesNot() throws Exception {
        share("peer-a");store.acknowledged("peer-a",note.id,1,true);
        assertEquals(SyncMark.GONE,markOf());
        String big=java.util.UUID.randomUUID().toString();
        store.keep(new NoteStore.Held(big,note.id,"film.mov","video/quicktime",Enclosure.MOST+1,System.currentTimeMillis()));
        assertEquals("a file too big to go never holds the note back",SyncMark.GONE,markOf());
        String small=java.util.UUID.randomUUID().toString();java.nio.file.Files.writeString(store.fileFor(small).toPath(),"synthetic");
        store.keep(new NoteStore.Held(small,note.id,"list.txt","text/plain",9,System.currentTimeMillis()));
        assertEquals(SyncMark.WAITING,personOf("peer-a"));assertEquals(SyncMark.WAITING,markOf());
        for(NoteStore.Branch line:store.inside(NoteStore.Branch.Kind.BOOK,note.book).holds)
            if(line.id.equals(note.id))assertEquals("the note's tile says what its title line says",SyncMark.WAITING,line.mark());
    }

    @Test public void owedForDaysToADeviceNeverHeardFromSinceIsRed() {
        share("peer-a");share("peer-b");
        store.acknowledged("peer-b",note.id,1,true);
        // Changed four days ago, and never answered by peer-a since.
        long fourDays=System.currentTimeMillis()-4L*24*60*60*1000;
        store.getWritableDatabase().execSQL("UPDATE notes SET updated=? WHERE id=?",new Object[]{fourDays,note.id});
        assertEquals(SyncMark.STUCK,personOf("peer-a"));assertEquals(SyncMark.GONE,personOf("peer-b"));
        assertEquals(SyncMark.STUCK,markOf());
        assertEquals("typing does not hide it",SyncMark.STUCK,SyncStatus.mark(store,note.id,SyncStatus.who(store,note.id),true));
        for(NoteStore.Branch line:store.inside(NoteStore.Branch.Kind.BOOK,note.book).holds)
            if(line.id.equals(note.id))assertEquals(SyncMark.STUCK,line.mark());
        // Heard from about anything since, and it is only on its way.
        NoteStore.Note other=new NoteStore.Note();other.book=note.book;other.body="Another synthetic note";other.revision=1;store.save(other);
        store.setLevel(Sharing.Scope.PAGE,other.id,"peer-a",Sharing.Level.WRITE,null);store.acknowledged("peer-a",other.id,1,true);
        assertEquals(SyncMark.WAITING,personOf("peer-a"));
    }

    @Test public void somebodyOnlyListedIsNamedByTheListAndDrawnAsNotLinked() {
        note.title="Transfer list";store.save(note);
        // Named in a list that came with the note, never paired here: the name the list gave, kept.
        store.mergeMembership(Sharing.Scope.PAGE,note.id,java.util.List.of(new Sharing.Rule(Sharing.Scope.PAGE,note.id,"peer-x",Sharing.Level.WRITE,5L,"key-x")),
            java.util.Map.of("key-x","Test phone"));
        SyncStatus.Person x=SyncStatus.who(store,note.id).get(0);
        assertEquals("Test phone",x.name());assertFalse(x.linked());
        assertEquals("Transfer list",x.listedIn());assertEquals("peer-x",x.listing().address);
        assertEquals("Test phone",store.nameFor("peer-x"));
        // An older copy of the list, without a name, takes none of it away; a later decision without one keeps it.
        store.mergeMembership(Sharing.Scope.PAGE,note.id,java.util.List.of(new Sharing.Rule(Sharing.Scope.PAGE,note.id,"peer-x",Sharing.Level.READ,9L,"key-x")),java.util.Map.of());
        assertEquals("Test phone",SyncStatus.who(store,note.id).get(0).name());
        // Taken off, it keeps its key: a list still naming it finds the row and does not put it back.
        store.removeShare(x.listing());
        store.mergeMembership(Sharing.Scope.PAGE,note.id,java.util.List.of(new Sharing.Rule(Sharing.Scope.PAGE,note.id,"peer-x",Sharing.Level.WRITE,5L,"key-x")),java.util.Map.of());
        assertTrue(SyncStatus.who(store,note.id).isEmpty());
    }

    @Test public void aNameFromAListIsFilledInWhereTheDecisionIsNotNew() {
        // A row written by a build that dropped the name: the next list that says it fills it in.
        store.mergeMembership(Sharing.Scope.PAGE,note.id,java.util.List.of(new Sharing.Rule(Sharing.Scope.PAGE,note.id,"peer-y",Sharing.Level.WRITE,5L,"key-y")),java.util.Map.of());
        assertTrue(SyncStatus.who(store,note.id).get(0).name().startsWith("device "));
        store.mergeMembership(Sharing.Scope.PAGE,note.id,java.util.List.of(new Sharing.Rule(Sharing.Scope.PAGE,note.id,"peer-y",Sharing.Level.WRITE,5L,"key-y")),
            java.util.Map.of("key-y","Test tablet"));
        assertEquals("Test tablet",SyncStatus.who(store,note.id).get(0).name());
    }

    @Test public void aPairedDeviceIsLinkedAndCalledWhatItWasPairedAs() {
        store.pairedWith("peer-z","Test laptop",false,new byte[]{1,2,3},new byte[]{4,5,6});
        share("peer-z");
        SyncStatus.Person z=SyncStatus.who(store,note.id).get(0);
        assertTrue(z.linked());assertEquals("Test laptop",z.name());assertNull(z.listing());
        // Known here by address only, keys never came: named, and not linked.
        store.addAddress("peer-w","Test reader",false);share("peer-w");
        for(SyncStatus.Person one:SyncStatus.who(store,note.id))if(one.address().equals("peer-w")){assertFalse(one.linked());assertEquals("Test reader",one.name());}
    }
}
