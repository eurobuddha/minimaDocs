package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.util.*;
import org.mininotes.desktop.platform.content.Context;

/**
 * What a device from before trees cannot take - a note that does not fit three levels, a collection going on its own with
 * its look and its files - is counted as waiting for it, and said as "Name needs to update Mininotes to receive this" in the
 * box an amber mark opens, on the thing and on everything holding it; the moment that device says it knows about trees, the
 * words go. Synthetic notebooks only; nothing is sent - no node is ever started.
 */
public class DesktopNeedsUpdateTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final List<NoteStore> open=new ArrayList<>();
    @After public void close(){for(NoteStore one:open)one.close();}

    private static final String OLD="MxOldPhone@127.0.0.1:9601";
    private static final String NEEDS="Old phone needs to update Mininotes to receive this.";

    private Context context;private NoteStore store;private Keys keys,theirs;

    @Before public void pairedWithADeviceFromBefore() throws Exception {
        context=new Context(temp.newFolder("here"));store=new NoteStore(context);store.getWritableDatabase();open.add(store);
        keys=new Keys(context);store.mySigningKey=Base64.getEncoder().encodeToString(keys.signing().getPublic().getEncoded());
        theirs=new Keys(new Context(temp.newFolder("old")));
        store.pairedWith(OLD,"Old phone",false,theirs.agreement().getPublic().getEncoded(),theirs.signing().getPublic().getEncoded());
        store.addShare(new Sharing.Rule(Sharing.Scope.LIBRARY,Sharing.EVERYTHING,OLD,true));
    }

    private NoteStore.Note write(String book,String body) {
        NoteStore.Note n=new NoteStore.Note();n.book=book;n.body=body;n.revision=1;store.save(n);return n;
    }

    private static NoteStore.Branch line(List<NoteStore.Branch> lines,String id) {
        for(NoteStore.Branch one:lines)if(one.id.equals(id))return one;
        return null;
    }

    @Test public void whatAnOldDeviceCannotTakeWaitsForItToBeUpdatedAndGoesWhenItSaysTrees() throws Exception {
        // Two collections down fits three levels; on Home it does not; nor does a collection with nothing in it, on its own.
        NoteStore.Note fits=write(store.someBook(),"Synthetic, two down");
        NoteStore.Note loose=write(Things.HOME,"Synthetic, on Home");
        String empty=store.addCollection("Plans").id;
        assertEquals(Set.of(OLD),store.beforeTrees());

        // The note that fits is owed as ever; the one that does not waits for them to update, and says so.
        assertEquals("Waiting to reach Old phone with your changes.",SyncStatus.waits(store,NoteStore.Branch.Kind.PAGE,fits.id));
        assertEquals(NEEDS,SyncStatus.waits(store,NoteStore.Branch.Kind.PAGE,loose.id));
        Waits.Where where=store.whereEach(store.pagesUnder(NoteStore.Branch.Kind.PAGE,loose.id)).get(loose.id).get(OLD);
        assertEquals(1,where.update());assertEquals(0,where.owed());assertEquals(SyncMark.WAITING,where.mark());
        // Their round under the note's title says it too.
        SyncStatus.Person round=SyncStatus.who(store,loose.id).get(0);
        assertEquals(SyncMark.WAITING,round.mark());assertEquals(NEEDS,round.standing());

        // The collection with nothing in it cannot go on its own: amber on its line, and on Home's mark, and its box says why.
        assertEquals(Set.of(OLD),store.cartonsForUpdate(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING).get(empty));
        assertEquals(SyncMark.WAITING,line(store.contents(Things.HOME),empty).mark());
        assertEquals(SyncMark.WAITING,store.markOf(NoteStore.Branch.Kind.COLLECTION,empty));
        assertTrue(store.marksUnder(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING).contains(SyncMark.WAITING));
        assertEquals(NEEDS,SyncStatus.waits(store,NoteStore.Branch.Kind.COLLECTION,empty));
        // Home's box: once for the device, however much waits for it, beside what is simply owed.
        String home=SyncStatus.waits(store,NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING);
        assertTrue(home,home.startsWith("Old phone needs to update Mininotes to receive this.\nWaiting to reach Old phone"));
        // And sending it: not sent, and said - no node asked for, since nothing can go.
        Post.Done done=Post.cartons(context,store,keys,NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING,null,false);
        assertEquals(0,done.sent);assertEquals(1,done.failed);
        assertEquals(Unsent.Why.NEEDS_UPDATE,done.problems.get(0).why);assertEquals(NEEDS,done.why);

        // They say their build knows about trees: from then on nothing waits for them to update.
        byte[] tree=Envelope.seal(new byte[16],0,System.currentTimeMillis(),Receipt.wrap(Receipt.TREE),theirs.signing(),keys.agreement().getPublic());
        Post.Landed heard=Post.arrived(context,store,keys,tree);
        assertTrue("the marks are asked again at once",heard.answered);
        assertTrue(store.beforeTrees().isEmpty());
        assertTrue(store.cartonsForUpdate(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING).isEmpty());
        assertEquals("Waiting to reach Old phone with your changes.",SyncStatus.waits(store,NoteStore.Branch.Kind.PAGE,loose.id));
        where=store.whereEach(store.pagesUnder(NoteStore.Branch.Kind.PAGE,loose.id)).get(loose.id).get(OLD);
        assertEquals(0,where.update());assertEquals(1,where.owed());
        assertFalse(SyncStatus.waits(store,NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING).contains("needs to update"));
        assertFalse(SyncStatus.waits(store,NoteStore.Branch.Kind.COLLECTION,empty).contains("needs to update"));
        // Said again, it is nothing new.
        assertFalse(Post.arrived(context,store,keys,Envelope.seal(new byte[16],0,System.currentTimeMillis(),Receipt.wrap(Receipt.TREE),
            theirs.signing(),keys.agreement().getPublic())).answered);
    }

    @Test public void aDeviceNeverPairedIsNotToldToUpdate() throws Exception {
        // Named in a list and never paired here: what it lacks is a pairing, and that is what it is told.
        store.addShare(new Sharing.Rule(Sharing.Scope.LIBRARY,Sharing.EVERYTHING,"MxListedOnly@127.0.0.1:9602",true));
        NoteStore.Note loose=write(Things.HOME,"Synthetic, on Home");
        assertEquals(Set.of(OLD),store.beforeTrees());
        Waits.Where listed=store.whereEach(store.pagesUnder(NoteStore.Branch.Kind.PAGE,loose.id)).get(loose.id).get("MxListedOnly@127.0.0.1:9602");
        assertEquals(0,listed.update());assertEquals(1,listed.owed());
    }
}
