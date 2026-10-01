package org.mininotes.android;

import org.junit.*;
import static org.junit.Assert.*;
import java.awt.Rectangle;
import java.util.*;

/** Where a dragged thing lands, from the lines and cards as drawn: the rule, without a window. */
public class DesktopMovingTest {
    private static final NoteStore.Branch.Kind C=NoteStore.Branch.Kind.COLLECTION,P=NoteStore.Branch.Kind.PAGE;
    private static NoteStore.Branch b(NoteStore.Branch.Kind kind,String id,String parent,int depth){return new NoteStore.Branch(kind,id,parent,id,"",depth,0,kind!=P);}

    // Home holds House (Errands (Soups (f), a, b, c), Trips (d)) and Work (Plans (e)), in the order the tree walks them:
    // each collection, then what it holds, then its notes. Each line 30 high, indented by how deep it sits.
    private final List<NoteStore.Branch> everything=List.of(
        b(C,"house",Sharing.EVERYTHING,0),b(C,"errands","house",1),b(C,"soups","errands",2),b(P,"f","soups",3),
        b(P,"a","errands",2),b(P,"b","errands",2),b(P,"c","errands",2),
        b(C,"trips","house",1),b(P,"d","trips",2),
        b(C,"work",Sharing.EVERYTHING,0),b(C,"plans","work",1),b(P,"e","plans",2));
    private List<DesktopMoving.Line> lines() {
        List<DesktopMoving.Line> out=new ArrayList<>();int y=30;
        for(NoteStore.Branch one:everything){out.add(new DesktopMoving.Line(one,y,30,20+20*one.depth));y+=30;}
        return out;
    }
    private NoteStore.Branch at(String id){for(NoteStore.Branch one:everything)if(one.id.equals(id))return one;throw new AssertionError(id);}
    /** The middle of a line, a little up or down. */
    private float y(String id,int by){for(DesktopMoving.Line l:lines())if(l.branch().id.equals(id))return l.top()+15+by;throw new AssertionError(id);}

    @Test public void placedTakesItOutAndPutsItBack() {
        assertEquals(List.of("b","a","c"),DesktopMoving.placed(List.of("a","b","c"),"a",1));
        assertEquals(List.of("b","c","a"),DesktopMoving.placed(List.of("a","b","c"),"a",9));
        assertEquals(List.of("x","a","b"),DesktopMoving.placed(List.of("a","b"),"x",0));
    }

    @Test public void aNoteBetweenItsOwnKindTakesThatPlace() {
        // a, held under the middle of c: after c.
        DesktopMoving.Landing l=DesktopMoving.overTree(at("a"),at("c"),y("c",5),lines(),everything);
        assertNull(l.into());assertEquals(List.of("b","c","a"),l.order());assertEquals(List.of("a","b","c"),l.before());
        // Over its own line, or just under the one above it: where it already is, so nothing.
        assertNull(DesktopMoving.overTree(at("b"),at("b"),y("b",0),lines(),everything));
        assertNull(DesktopMoving.overTree(at("b"),at("a"),y("a",5),lines(),everything));
    }

    @Test public void aNoteOntoAnyCollectionGoesInsideAtTheTop() {
        DesktopMoving.Landing l=DesktopMoving.overTree(at("a"),at("trips"),y("trips",0),lines(),everything);
        assertEquals("trips",l.into().id);assertNull("at the top, as Move puts it",l.order());
        // Anywhere on the line: a note is never put between collections.
        assertEquals("trips",DesktopMoving.overTree(at("a"),at("trips"),y("trips",-13),lines(),everything).into().id);
        // A collection on the top takes notes now, and one three down does too.
        assertEquals("work",DesktopMoving.overTree(at("a"),at("work"),y("work",0),lines(),everything).into().id);
        assertEquals("soups",DesktopMoving.overTree(at("a"),at("soups"),y("soups",0),lines(),everything).into().id);
        // Its own collection: to the top of it, which for the first note is nothing at all.
        assertEquals(List.of("c","a","b"),DesktopMoving.overTree(at("c"),at("errands"),y("errands",0),lines(),everything).order());
        assertNull(DesktopMoving.overTree(at("a"),at("errands"),y("errands",0),lines(),everything));
    }

    @Test public void betweenTheNotesOfAnotherCollectionMovesItThereAtThatPlace() {
        DesktopMoving.Landing l=DesktopMoving.overTree(at("a"),at("e"),y("e",-5),lines(),everything);
        assertEquals("plans",l.into().id);assertEquals(List.of("a","e"),l.order());
    }

    @Test public void aCollectionGoesOntoTheMiddleOfALineAndBetweenAtItsEdges() {
        // Onto the middle of another collection's line: inside it, at the top - a sibling, or one anywhere else.
        assertEquals("trips",DesktopMoving.overTree(at("errands"),at("trips"),y("trips",0),lines(),everything).into().id);
        assertEquals("plans",DesktopMoving.overTree(at("soups"),at("plans"),y("plans",2),lines(),everything).into().id);
        // On the bottom edge of Work's line: after it, among the collections on the top.
        DesktopMoving.Landing after=DesktopMoving.overTree(at("house"),at("work"),y("work",12),lines(),everything);
        assertNull(after.into());assertEquals(List.of("work","house"),after.order());
        // On the top edge: before it, which is where House already is.
        assertNull(DesktopMoving.overTree(at("house"),at("work"),y("work",-12),lines(),everything));
        // Soups on the bottom edge of House's line: out onto the top, after House.
        DesktopMoving.Landing out=DesktopMoving.overTree(at("soups"),at("house"),y("house",12),lines(),everything);
        assertEquals(NoteStore.Branch.Kind.LIBRARY,out.into().kind);assertEquals(Sharing.EVERYTHING,out.parent());
        assertEquals(List.of("house","soups","work"),out.order());
    }

    @Test public void aCollectionCountsANotesLineAsItsCollections() {
        // Errands, held over the note in Trips: past Trips, so last in House.
        DesktopMoving.Landing l=DesktopMoving.overTree(at("errands"),at("d"),y("d",0),lines(),everything);
        assertNull(l.into());assertEquals(List.of("trips","errands"),l.order());
        // Over the note in Plans: among Work's collections, so into Work, after Plans.
        DesktopMoving.Landing in=DesktopMoving.overTree(at("errands"),at("e"),y("e",0),lines(),everything);
        assertEquals("work",in.into().id);assertEquals(List.of("plans","errands"),in.order());
    }

    @Test public void aCollectionNeverGoesInsideItself() {
        Map<String,String> parents=DesktopMoving.parents(everything);
        assertEquals(Things.HOME,parents.get("house"));assertEquals("errands",parents.get("soups"));
        // Not into itself, not into anything it holds however deep, not anywhere among what it holds.
        assertFalse(DesktopMoving.holds(at("house"),at("house"),parents));
        assertFalse(DesktopMoving.holds(at("soups"),at("house"),parents));
        assertNull(DesktopMoving.overTree(at("house"),at("soups"),y("soups",0),lines(),everything));
        assertNull(DesktopMoving.overTree(at("house"),at("f"),y("f",0),lines(),everything));
        assertNull(DesktopMoving.overTree(at("house"),at("a"),y("a",0),lines(),everything));
        assertNull("over its own middle it stays where it is",DesktopMoving.overTree(at("house"),at("house"),y("house",0),lines(),everything));
        // Anywhere else, and onto the top, it may.
        assertTrue(DesktopMoving.holds(at("soups"),at("trips"),parents));
        assertTrue(DesktopMoving.holds(DesktopMoving.top(),at("soups"),parents));
        // A note goes into any collection, never onto the top or into a note.
        assertTrue(DesktopMoving.holds(at("house"),at("a"),parents));
        assertFalse(DesktopMoving.holds(DesktopMoving.top(),at("a"),parents));
        assertFalse(DesktopMoving.holds(at("b"),at("a"),parents));
    }

    @Test public void nowhereItCannotGo() {
        assertNull(DesktopMoving.overTree(at("a"),null,0,lines(),everything));
        NoteStore.Branch favourites=new NoteStore.Branch(NoteStore.Branch.Kind.FAVOURITES,NoteStore.FAVOURITES,"","Favourites","",0,0,true);
        assertNull(DesktopMoving.overTree(favourites,at("a"),y("a",0),lines(),everything));
        // A note on the top is not among collections, and a note is not put onto the top from a collection.
        List<NoteStore.Branch> loose=new ArrayList<>(everything);loose.add(b(P,"g",Sharing.EVERYTHING,0));
        List<DesktopMoving.Line> drawn=new ArrayList<>(lines());drawn.add(new DesktopMoving.Line(loose.get(loose.size()-1),30*(everything.size()+1),30,20));
        assertNull(DesktopMoving.overTree(at("a"),loose.get(loose.size()-1),30*(everything.size()+1)+15,drawn,loose));
    }

    @Test public void cardsAreReadAcrossThenDownAmongTheirOwnKind() {
        // Two collections first, then five notes: a b c / d e, three to a row, under the row of collections.
        List<NoteStore.Branch> shown=new ArrayList<>();List<Rectangle> boxes=new ArrayList<>();
        shown.add(b(C,"x1","book",2));shown.add(b(C,"x2","book",2));
        boxes.add(new Rectangle(0,0,210,128));boxes.add(new Rectangle(226,0,210,128));
        String[] ids={"a","b","c","d","e"};
        for(int i=0;i<ids.length;i++){shown.add(b(P,ids[i],"book",2));boxes.add(new Rectangle((i%3)*226,144+(i/3)*144,210,128));}
        NoteStore.Branch book=b(C,"book","house",1);
        List<NoteStore.Branch> tree=new ArrayList<>(List.of(b(C,"house",Sharing.EVERYTHING,0),book,b(C,"other","house",1)));tree.addAll(shown);
        // a, held just past the middle of d: after d, among the notes only.
        DesktopMoving.Landing l=DesktopMoving.overCards(shown.get(2),book,120,354,shown,boxes,tree);
        assertEquals(List.of("b","c","d","a","e"),l.order());assertNull(l.into());
        // e, held left of the middle of a: first.
        assertEquals(List.of("e","a","b","c","d"),DesktopMoving.overCards(shown.get(6),book,20,204,shown,boxes,tree).order());
        // A note from another collection comes in among the notes.
        NoteStore.Branch stranger=b(P,"s","other",2);
        DesktopMoving.Landing in=DesktopMoving.overCards(stranger,book,500,204,shown,boxes,tree);
        assertEquals("book",in.into().id);assertEquals(List.of("a","b","s","c","d","e"),in.order());
        // A collection comes in among the collections; one that holds this one does not.
        DesktopMoving.Landing other=DesktopMoving.overCards(tree.get(2),book,400,60,shown,boxes,tree);
        assertEquals("book",other.into().id);assertEquals(List.of("x1","x2","other"),other.order());
        assertNull(DesktopMoving.overCards(tree.get(0),book,20,60,shown,boxes,tree));
        // The top takes a collection, not a note.
        assertNotNull(DesktopMoving.overCards(book,DesktopMoving.top(),20,60,List.of(tree.get(0)),List.of(new Rectangle(0,0,210,128)),tree));
        assertNull(DesktopMoving.overCards(stranger,DesktopMoving.top(),20,60,List.of(tree.get(0)),List.of(new Rectangle(0,0,210,128)),tree));
    }
}
