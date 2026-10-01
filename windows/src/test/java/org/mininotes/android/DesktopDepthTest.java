package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.util.*;
import org.mininotes.desktop.platform.content.Context;

/**
 * The PC's screens on collections nested as deep as anybody likes (docs/HOME.md, step 1): what + makes at each depth,
 * where Move to… offers to put a thing and where it refuses to, the rules a Share box finds along a path, the line over
 * a title, and the marks read to any depth. A synthetic notebook, no window and no network.
 */
public class DesktopDepthTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private NoteStore store;
    private String kitchen,recipes,soups,work,deep,loose;
    private static final String A="MxDepthA@127.0.0.1:9401",B="MxDepthB@127.0.0.1:9402",C="MxDepthC@127.0.0.1:9403",D="MxDepthD@127.0.0.1:9404";

    /** Kitchen › Recipes › Soups, with a note in Soups; Work beside Kitchen; a note on the top by itself. */
    @Before public void open() throws Exception {
        store=new NoteStore(new Context(temp.newFolder("pad")));store.getWritableDatabase();
        kitchen=store.addCollection("Kitchen").id;recipes=store.addBook(kitchen,"Recipes").id;soups=store.addBook(recipes,"Soups").id;
        work=store.addCollection("Work").id;
        NoteStore.Note leek=new NoteStore.Note();leek.book=soups;leek.title="Leek";leek.body="Synthetic leek soup";store.save(leek);deep=leek.id;
        NoteStore.Note top=new NoteStore.Note();top.book=Things.HOME;top.title="Loose";top.body="Synthetic loose note";store.save(top);loose=top.id;
    }
    @After public void close(){store.close();}

    private NoteStore.Branch line(String id){for(NoteStore.Branch one:store.wholeTree())if(one.id.equals(id))return one;throw new AssertionError(id);}

    @Test public void plusMakesACollectionOnTheTopAndOneDownAndANoteDeeper() {
        List<NoteStore.Branch> tree=store.wholeTree();
        // Every collection is a collection now, however deep, and each line knows how deep it sits.
        for(NoteStore.Branch one:tree)assertNotEquals(NoteStore.Branch.Kind.BOOK,one.kind);
        assertEquals(0,line(kitchen).depth);assertEquals(2,line(soups).depth);assertEquals(3,line(deep).depth);
        // What + makes at each depth is no longer the depth's to say: Home's + offers a note and a collection
        // wherever it is (docs/HOME.md, step 3), so the old rule is not asked here.
        // A collection made one down is inside what it was made in.
        String shelf=store.addBook(kitchen,"Bread").id;
        assertEquals(List.of(kitchen),store.above(shelf));
    }

    @Test public void aCollectionIsNeverMovedIntoItsOwnInside() {
        // The notebook refuses it, in words to show.
        IllegalArgumentException refused=assertThrows(IllegalArgumentException.class,()->store.moveBook(kitchen,soups));
        assertEquals("A collection cannot go inside itself, or inside anything it holds.",refused.getMessage());
        assertEquals(List.of(),store.above(kitchen));
        // Move to… never offers it: not Kitchen itself, nothing inside it, and not the top it is already on.
        List<String> offered=new ArrayList<>();
        for(NoteStore.Branch one:Desktop.places(store,line(kitchen),Sharing.EVERYTHING))offered.add(one.id);
        assertTrue(offered.contains(work));
        for(String never:new String[]{kitchen,recipes,soups,Sharing.EVERYTHING})assertFalse(never,offered.contains(never));
        // Soups, deep down: the top and every collection but itself and where it is.
        List<NoteStore.Branch> forSoups=Desktop.places(store,line(soups),recipes);
        List<String> ids=new ArrayList<>();for(NoteStore.Branch one:forSoups)ids.add(one.id);
        assertTrue(ids.contains(Sharing.EVERYTHING));assertTrue(ids.contains(kitchen));assertTrue(ids.contains(work));
        assertFalse(ids.contains(recipes));assertFalse(ids.contains(soups));
        assertEquals("Home",Desktop.placeName(forSoups.get(0)));
        // A note: every collection but the one it is in, and not the top. Each named with where it is.
        List<String> names=new ArrayList<>();
        for(NoteStore.Branch one:Desktop.places(store,line(deep),soups)){assertNotEquals(NoteStore.Branch.Kind.LIBRARY,one.kind);names.add(Desktop.placeName(one));}
        assertTrue(names.toString(),names.contains("Kitchen"));assertTrue(names.toString(),names.contains("Recipes   ·   Kitchen"));
        assertFalse(names.toString(),names.contains("Soups   ·   Kitchen › Recipes"));
        // The drag says the same: onto its own inside, nowhere; onto the top or beside, yes.
        Map<String,String> parents=DesktopMoving.parents(store.wholeTree());
        assertFalse(DesktopMoving.holds(line(soups),line(kitchen),parents));
        assertFalse(DesktopMoving.holds(line(kitchen),line(kitchen),parents));
        assertTrue(DesktopMoving.holds(line(work),line(kitchen),parents));
        assertTrue(DesktopMoving.holds(DesktopMoving.top(),line(soups),parents));
        // And a move that may go, goes, and comes back with its undo.
        store.moveBook(soups,work);assertEquals(List.of(work,soups),store.pathOf(soups));
        store.moveBook(soups,recipes);assertEquals(List.of(kitchen,recipes,soups,deep),store.pathOf(deep));
    }

    @Test public void theShareBoxFindsEveryRuleAlongThePath() {
        // Kitchen shared since collections nest (THING), Recipes as a 0.1 book, the note by itself, and everything;
        // and Work, which is not above it, shared too.
        store.addShare(new Sharing.Rule(Sharing.Scope.COLLECTION,kitchen,A,true));
        store.getWritableDatabase().execSQL("INSERT INTO shares(scope,target,address,mine,added,level,changed) VALUES('BOOK',?,?,0,2,1,5)",new Object[]{recipes,B});
        store.addShare(new Sharing.Rule(Sharing.Scope.PAGE,deep,C,false));
        store.addShare(new Sharing.Rule(Sharing.Scope.LIBRARY,Sharing.EVERYTHING,D,true));
        store.addShare(new Sharing.Rule(Sharing.Scope.COLLECTION,work,C,true));
        assertEquals(Sharing.Scope.THING,store.membership(Sharing.Scope.COLLECTION,kitchen).get(0).scope);
        // The note, three collections down: every one of them, the old three levels would have missed Kitchen.
        assertEquals(Set.of(A+" THING "+kitchen,B+" BOOK "+recipes,C+" PAGE "+deep,D+" LIBRARY *"),said(Desktop.reaching(store,line(deep))));
        // Soups reaches Kitchen's and Recipes' and everything's, not the note's own.
        assertEquals(Set.of(A+" THING "+kitchen,B+" BOOK "+recipes,D+" LIBRARY *"),said(Desktop.reaching(store,line(soups))));
        // Kitchen: its own rule, and everything's.
        assertEquals(Set.of(A+" THING "+kitchen,D+" LIBRARY *"),said(Desktop.reaching(store,line(kitchen))));
        assertEquals(Set.of(D+" LIBRARY *"),said(Desktop.reaching(store,Desktop.library())));
        // A note on the top by itself: everything's only.
        assertEquals(Set.of(D+" LIBRARY *"),said(Desktop.reaching(store,line(loose))));
    }
    /** Each rule as who, at what level, on what: one line each, so a rule found twice would show. */
    private static Set<String> said(List<Sharing.Rule> rules){Set<String> out=new HashSet<>();for(Sharing.Rule one:rules)assertTrue("twice: "+one,out.add(one.address+" "+one.scope+" "+one.target));return out;}

    @Test public void theLineOverATitleNamesEveryCollectionAbove() {
        assertEquals("Kitchen  /  Recipes  /  Soups",Desktop.shelfLine(store,deep));
        assertEquals("Home",Desktop.shelfLine(store,loose));
        store.renameCollection(recipes,"Cooking");
        assertEquals("Kitchen  /  Cooking  /  Soups",Desktop.shelfLine(store,deep));
    }

    @Test public void marksAreReadToAnyDepth() {
        NoteStore.Note plain=new NoteStore.Note();plain.book=work;plain.title="Plain";plain.body="Synthetic";store.save(plain);
        store.addShare(new Sharing.Rule(Sharing.Scope.COLLECTION,kitchen,A,false));
        Map<String,SyncMark> marks=DesktopMark.read(store);
        // Every collection and note has one, three down and on the top by itself too.
        for(String id:new String[]{kitchen,recipes,soups,work,deep,loose,plain.id})assertNotNull(id,marks.get(id));
        // Shared on Kitchen: what is three down says so, and so does everything holding it; the rest is here only.
        for(String id:new String[]{kitchen,recipes,soups,deep})assertNotEquals(id,SyncMark.HERE,marks.get(id));
        assertEquals(SyncMark.HERE,marks.get(plain.id));assertEquals(SyncMark.HERE,marks.get(loose));
    }
}
