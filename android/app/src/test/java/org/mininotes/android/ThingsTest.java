// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** Notes and collections nested as deep as anybody likes, and how that looks to a device that knows three levels. */
public class ThingsTest {
    /** Home ▸ Work ▸ Meetings ▸ Monday (a note); Home ▸ Work ▸ Deep ▸ Deeper ▸ Deepest; Home ▸ Loose (a note). */
    private static Map<String,String> tree() {
        Map<String,String> parents=new HashMap<>();
        parents.put("work",Things.HOME);
        parents.put("meetings","work");
        parents.put("monday","meetings");
        parents.put("deep","work");
        parents.put("deeper","deep");
        parents.put("deepest","deeper");
        parents.put("loose",Things.HOME);
        parents.put("in-work","work");
        return parents;
    }

    @Test public void aThingKnowsWhatIsAboveItFromHomeDown() {
        Map<String,String> t=tree();
        assertEquals(List.of("work","meetings"),Things.above(t,"monday"));
        assertEquals(List.of("work","meetings","monday"),Things.path(t,"monday"));
        assertEquals(List.of(),Things.above(t,"loose"));
        assertEquals(List.of("loose"),Things.path(t,"loose"));
        assertEquals(List.of("work","deep","deeper"),Things.above(t,"deepest"));
        assertEquals(1,Things.depth(t,"work"));
        assertEquals(3,Things.depth(t,"monday"));
        assertEquals(4,Things.depth(t,"deepest"));
    }

    @Test public void aParentNobodyKnowsEndsTheWalkAndALoopNeverRepeats() {
        Map<String,String> t=new HashMap<>();
        t.put("orphan","gone-book");
        assertEquals(List.of("gone-book"),Things.above(t,"orphan"));
        t.put("a","b");t.put("b","c");t.put("c","a");
        List<String> up=Things.above(t,"a");
        assertEquals("each once, never the thing itself",List.of("b","c").size(),up.size());
        assertFalse(up.contains("a"));
    }

    @Test public void aCollectionNeverGoesInsideItself() {
        Map<String,String> t=tree();
        assertTrue(Things.mayGoInto(t,"deep","meetings",true));
        assertTrue(Things.mayGoInto(t,"deep",Things.HOME,true));
        assertFalse("into itself",Things.mayGoInto(t,"deep","deep",true));
        assertFalse("into what it holds",Things.mayGoInto(t,"deep","deepest",true));
        assertFalse("into a note",Things.mayGoInto(t,"loose","monday",false));
        assertTrue(Things.mayGoInto(t,"monday","deepest",true));
        assertTrue(Things.within(t,"deepest","work"));
        assertFalse(Things.within(t,"work","deepest"));
    }

    @Test public void onlyANoteTwoCollectionsDownIsANoteInABookInACollection() {
        Map<String,String> t=tree();
        assertArrayEquals(new String[]{"work","meetings"},Things.threeLevels(Things.above(t,"monday")));
        assertNull("a note on Home",Things.threeLevels(Things.above(t,"loose")));
        assertNull("a note straight in a collection",Things.threeLevels(Things.above(t,"in-work")));
        assertTrue(Things.fitsThreeLevels(Things.above(t,"monday"),true));
        assertFalse(Things.fitsThreeLevels(Things.above(t,"in-work"),true));
        assertTrue("a collection on Home",Things.fitsThreeLevels(Things.above(t,"work"),false));
        assertTrue("a book",Things.fitsThreeLevels(Things.above(t,"meetings"),false));
        assertFalse("two down",Things.fitsThreeLevels(Things.above(t,"deeper"),false));
        assertEquals(Sharing.Scope.COLLECTION,Things.oldScope(Things.above(t,"work"),false));
        assertEquals(Sharing.Scope.BOOK,Things.oldScope(Things.above(t,"meetings"),false));
        assertEquals(Sharing.Scope.PAGE,Things.oldScope(Things.above(t,"monday"),true));
        assertNull(Things.oldScope(Things.above(t,"deepest"),false));
        assertNull(Things.oldScope(Things.above(t,"loose"),true));
    }

    @Test public void theTwoOldFieldsCarryTheNearestTwoCollections() {
        Map<String,String> t=tree();
        assertArrayEquals(new String[]{"work","meetings"},Things.nearestTwo(Things.above(t,"monday")));
        assertArrayEquals(new String[]{"deep","deeper"},Things.nearestTwo(Things.above(t,"deepest")));
        assertArrayEquals(new String[]{"","work"},Things.nearestTwo(Things.above(t,"in-work")));
        assertArrayEquals(new String[]{"",""},Things.nearestTwo(Things.above(t,"loose")));
    }

    @Test public void aNoteIsNamedByItsOwnBytesAndAnythingElseByAHash() {
        String note="1404353f-7b77-49fc-ad55-d6cb34eb390b";
        byte[] named=Things.envelopeId(note);
        assertEquals(16,named.length);
        java.nio.ByteBuffer back=java.nio.ByteBuffer.wrap(named);
        assertEquals(java.util.UUID.fromString(note),new java.util.UUID(back.getLong(),back.getLong()));
        byte[] first=Things.envelopeId(SchemaMigrations.FIRST_COLLECTION);
        assertEquals(16,first.length);
        assertArrayEquals("the same every time",first,Things.envelopeId(SchemaMigrations.FIRST_COLLECTION));
        assertFalse(java.util.Arrays.equals(first,Things.envelopeId(SchemaMigrations.FIRST_BOOK)));
        assertEquals(SchemaMigrations.FIRST_BOOK,Things.named(Things.envelopeId(SchemaMigrations.FIRST_BOOK),
            List.of(SchemaMigrations.FIRST_COLLECTION,SchemaMigrations.FIRST_BOOK,note)));
        assertEquals(note,Things.named(named,List.of(SchemaMigrations.FIRST_BOOK,note)));
        assertNull(Things.named(new byte[16],List.of(note)));
        assertNull(Things.named(new byte[3],List.of(note)));
    }

    @Test public void theDockTakesTheFirstFavourites() {
        assertEquals(List.of("a","b"),Things.dock(List.of("a","b"),Things.DOCK_PHONE));
        assertEquals(List.of("1","2","3","4","5"),Things.dock(List.of("1","2","3","4","5","6","7"),Things.DOCK_PHONE));
        assertTrue(Things.dock(List.of("a"),0).isEmpty());
        assertTrue(Things.dock(null,5).isEmpty());
    }

    @Test public void aThingPutInTheDockMovesTheRestAlongAndTheLastFallsOut() {
        List<String> dock=List.of("a","b","c");
        // A new one at a place: that place and everything after it moves along one.
        assertEquals(List.of("a","x","b","c"),Things.intoDock(dock,"x",2,Things.DOCK_PHONE));
        assertEquals(List.of("x","a","b","c"),Things.intoDock(dock,"x",1,Things.DOCK_PHONE));
        // Past the end, or before the start, is the end or the start.
        assertEquals(List.of("a","b","c","x"),Things.intoDock(dock,"x",9,Things.DOCK_PHONE));
        assertEquals(List.of("x","a","b","c"),Things.intoDock(dock,"x",0,Things.DOCK_PHONE));
        // One already in the dock is taken from where it was, never there twice.
        assertEquals(List.of("c","a","b"),Things.intoDock(dock,"c",1,Things.DOCK_PHONE));
        assertEquals(List.of("b","c","a"),Things.intoDock(dock,"a",3,Things.DOCK_PHONE));
        // A full dock: the last is pushed out, and only it.
        List<String> full=List.of("1","2","3","4","5");
        assertEquals(List.of("1","x","2","3","4"),Things.intoDock(full,"x",2,Things.DOCK_PHONE));
        assertEquals(List.of("1","2","3","4","x"),Things.intoDock(full,"x",5,Things.DOCK_PHONE));
        assertEquals("moving within a full dock pushes nobody out",List.of("5","1","2","3","4"),Things.intoDock(full,"5",1,Things.DOCK_PHONE));
        assertEquals(List.of("x"),Things.intoDock(null,"x",3,Things.DOCK_PHONE));
        assertTrue(Things.intoDock(dock,"x",1,0).isEmpty());
    }

    @Test public void aThingTakenOutOfTheDockLetsTheRestCloseUp() {
        assertEquals(List.of("a","c"),Things.outOfDock(List.of("a","b","c"),"b"));
        assertEquals(List.of("a","b","c"),Things.outOfDock(List.of("a","b","c"),"x"));
        assertTrue(Things.outOfDock(null,"x").isEmpty());
    }

    @Test public void whatACollectionHoldsIsSaidInAFewWords() {
        assertEquals("Empty",Things.holds(0,0));
        assertEquals("1 note",Things.holds(0,1));
        assertEquals("5 notes",Things.holds(0,5));
        assertEquals("3 collections",Things.holds(3,0));
        assertEquals("1 collection · 2 notes",Things.holds(1,2));
    }
}
