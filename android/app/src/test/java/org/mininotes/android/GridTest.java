// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import org.junit.Test;

import static org.junit.Assert.*;

/** Home's grid, its pop-ups and its dock, decided without a screen (docs/HOME.md, step 2). */
public class GridTest {
    private static final NoteStore.Branch.Kind NOTE=NoteStore.Branch.Kind.PAGE,COLLECTION=NoteStore.Branch.Kind.COLLECTION,
        FILE=NoteStore.Branch.Kind.FILE,FAVOURITES=NoteStore.Branch.Kind.FAVOURITES,ARCHIVE=NoteStore.Branch.Kind.ARCHIVE,
        BIN=NoteStore.Branch.Kind.BIN;

    @Test public void fourAcrossOnAPhoneHeldUprightAndThreeOnANarrowOne() {
        // Phones held upright, from the smallest usual width to the largest: four (decision 18).
        assertEquals(4,Grid.columns(340));
        assertEquals(4,Grid.columns(360));
        assertEquals(4,Grid.columns(411));
        assertEquals(4,Grid.columns(480));
        // Narrower than 340dp, four names do not fit under four icons.
        assertEquals(3,Grid.columns(339.5f));
        assertEquals(3,Grid.columns(280));
        assertEquals(3,Grid.columns(0));
        assertEquals(3,Grid.columns(Float.NaN));
        // Turned sideways, or a tablet: more across, never past eight.
        assertEquals(7,Grid.columns(800));
        assertEquals(8,Grid.columns(2000));
    }

    @Test public void aNoteOnANoteMakesACollectionAndAnythingOnACollectionGoesIn() {
        assertEquals(Grid.Onto.MERGE,Grid.onto(NOTE,NOTE));
        assertEquals(Grid.Onto.INTO,Grid.onto(NOTE,COLLECTION));
        assertEquals(Grid.Onto.INTO,Grid.onto(COLLECTION,COLLECTION));
        assertEquals(Grid.Onto.INTO,Grid.onto(FILE,COLLECTION));
        // A file let go on a note is kept with the note.
        assertEquals(Grid.Onto.INTO,Grid.onto(FILE,NOTE));
    }

    @Test public void whatHasNoMeaningIsAPlaceInTheOrderInstead() {
        // Nothing goes into a note but a file, and nothing goes into a file.
        assertEquals(Grid.Onto.NONE,Grid.onto(COLLECTION,NOTE));
        assertEquals(Grid.Onto.NONE,Grid.onto(NOTE,FILE));
        assertEquals(Grid.Onto.NONE,Grid.onto(FILE,FILE));
        assertEquals(Grid.Onto.NONE,Grid.onto(COLLECTION,FILE));
        // The Favourites collection is a place: nothing is let go in it, and it goes into nothing.
        assertEquals(Grid.Onto.NONE,Grid.onto(NOTE,FAVOURITES));
        assertEquals(Grid.Onto.NONE,Grid.onto(FAVOURITES,COLLECTION));
        assertEquals(Grid.Onto.NONE,Grid.onto(NOTE,null));
        assertFalse(Grid.moves(FAVOURITES));
        assertTrue(Grid.moves(FILE));
    }

    @Test public void theArchiveAndTheBinTakeWhatTheirMenusWouldPutInThem() {
        // Decision 41: let go on the Archive, a note or a collection is archived; on the Bin, it goes in the bin.
        assertEquals(Grid.Onto.AWAY,Grid.onto(NOTE,ARCHIVE));
        assertEquals(Grid.Onto.AWAY,Grid.onto(COLLECTION,ARCHIVE));
        assertEquals(Grid.Onto.AWAY,Grid.onto(NOTE,BIN));
        assertEquals(Grid.Onto.AWAY,Grid.onto(COLLECTION,BIN));
        // A file has no archive, and on the Bin it is deleted, after the question its menu asks.
        assertEquals(Grid.Onto.NONE,Grid.onto(FILE,ARCHIVE));
        assertEquals(Grid.Onto.AWAY,Grid.onto(FILE,BIN));
        // A place goes into nothing, not even another place.
        assertEquals(Grid.Onto.NONE,Grid.onto(BIN,ARCHIVE));
        assertEquals(Grid.Onto.NONE,Grid.onto(ARCHIVE,COLLECTION));
        assertEquals(Grid.Onto.NONE,Grid.onto(FAVOURITES,BIN));
    }

    @Test public void thePlacesAreCarriedButAreNotThings() {
        for(NoteStore.Branch.Kind place:new NoteStore.Branch.Kind[]{FAVOURITES,ARCHIVE,BIN}) {
            assertTrue(Grid.place(place));
            assertTrue("moved to another cell of Home (decision 42)",Grid.carried(place));
            assertFalse("not a thing that goes into a collection",Grid.moves(place));
            assertFalse(Grid.docks(place));
        }
        for(NoteStore.Branch.Kind thing:new NoteStore.Branch.Kind[]{NOTE,COLLECTION,FILE}){assertFalse(Grid.place(thing));assertTrue(Grid.carried(thing));}
        assertFalse(Grid.place(null));assertFalse(Grid.carried(NoteStore.Branch.Kind.LIBRARY));
    }

    @Test public void onlyNotesAndCollectionsGoInTheDock() {
        assertTrue(Grid.docks(NOTE));
        assertTrue(Grid.docks(COLLECTION));
        assertFalse(Grid.docks(FILE));
        assertFalse(Grid.docks(FAVOURITES));
    }

    @Test public void theDockPlaceIsOnePastEveryIconPassed() {
        float[] three={40,120,200};
        assertEquals(1,Grid.dockSlot(10,three,-1));
        assertEquals(2,Grid.dockSlot(60,three,-1));
        assertEquals(3,Grid.dockSlot(150,three,-1));
        assertEquals(4,Grid.dockSlot(300,three,-1));
        // An empty dock: the first place.
        assertEquals(1,Grid.dockSlot(300,new float[0],-1));
    }

    @Test public void aDockIconMovedAlongTheDockIsNotCountedAgainstItself() {
        float[] three={40,120,200};
        // The first icon held where it is stays first, and dragged past the second takes its place.
        assertEquals(1,Grid.dockSlot(50,three,0));
        assertEquals(2,Grid.dockSlot(130,three,0));
        assertEquals(3,Grid.dockSlot(300,three,0));
        // The last dragged to the front.
        assertEquals(1,Grid.dockSlot(5,three,2));
    }

    @Test public void notesAndCollectionsStayBeforeTheFilesAndFilesAfterThem() {
        // Five that move: three collections and notes, then two files.
        assertEquals(0,Grid.within(0,3,5,false));
        assertEquals(2,Grid.within(2,3,5,false));
        assertEquals(2,Grid.within(4,3,5,false));
        assertEquals(3,Grid.within(0,3,5,true));
        assertEquals(4,Grid.within(4,3,5,true));
        assertEquals(4,Grid.within(9,3,5,true));
        // No files at all, or nothing but files.
        assertEquals(3,Grid.within(7,4,4,false));
        assertEquals(0,Grid.within(-1,0,3,true));
    }

    @Test public void aSwipeUpIsFarEnoughAndMoreUpThanAcross() {
        assertTrue(Grid.swipedUp(0,-60,48));
        assertTrue(Grid.swipedUp(30,-60,48));
        assertFalse(Grid.swipedUp(0,-30,48));
        assertFalse(Grid.swipedUp(0,60,48));
        // Sideways along the dock is not a swipe up.
        assertFalse(Grid.swipedUp(80,-60,48));
    }

    @Test public void aCardThrownAQuarterOfItsHeightIsClosed() {
        assertTrue(Grid.thrownUp(-101,400));
        assertFalse(Grid.thrownUp(-99,400));
        assertFalse(Grid.thrownUp(150,400));
        assertFalse(Grid.thrownUp(-500,0));
    }

    @Test public void letGoInAnEmptyCellItStaysThere() {
        assertEquals(Grid.LetGo.PLACE,Grid.letGo(NOTE,null,false));
        assertEquals(Grid.LetGo.PLACE,Grid.letGo(COLLECTION,null,false));
        assertEquals(Grid.LetGo.PLACE,Grid.letGo(FILE,null,false));
        // A place too (decision 42): the Favourites collection, the archive and the bin go to any empty cell of Home.
        assertEquals(Grid.LetGo.PLACE,Grid.letGo(FAVOURITES,null,false));
        assertEquals(Grid.LetGo.PLACE,Grid.letGo(ARCHIVE,null,false));
        assertEquals(Grid.LetGo.PLACE,Grid.letGo(BIN,null,false));
    }

    @Test public void aPlaceLetGoOnAnIconGoesBack() {
        assertEquals(Grid.LetGo.BACK,Grid.letGo(BIN,NOTE,false));
        assertEquals(Grid.LetGo.BACK,Grid.letGo(BIN,COLLECTION,false));
        assertEquals(Grid.LetGo.BACK,Grid.letGo(ARCHIVE,BIN,false));
        assertEquals(Grid.LetGo.BACK,Grid.letGo(FAVOURITES,FAVOURITES,true));
    }

    @Test public void letGoOnAnIconItGoesOntoItOrBackButNeverPushesItAside() {
        assertEquals(Grid.LetGo.MERGE,Grid.letGo(NOTE,NOTE,false));
        assertEquals(Grid.LetGo.INTO,Grid.letGo(NOTE,COLLECTION,false));
        assertEquals(Grid.LetGo.INTO,Grid.letGo(COLLECTION,COLLECTION,false));
        assertEquals(Grid.LetGo.INTO,Grid.letGo(FILE,NOTE,false));
        // What takes nothing from it: back where it was, and the icon there stays in its cell.
        assertEquals(Grid.LetGo.BACK,Grid.letGo(COLLECTION,NOTE,false));
        assertEquals(Grid.LetGo.BACK,Grid.letGo(NOTE,FILE,false));
        assertEquals(Grid.LetGo.BACK,Grid.letGo(NOTE,FAVOURITES,false));
        // On the archive or the bin: put away there.
        assertEquals(Grid.LetGo.AWAY,Grid.letGo(NOTE,BIN,false));
        assertEquals(Grid.LetGo.AWAY,Grid.letGo(COLLECTION,ARCHIVE,false));
        assertEquals(Grid.LetGo.AWAY,Grid.letGo(FILE,BIN,false));
        assertEquals(Grid.LetGo.BACK,Grid.letGo(FILE,ARCHIVE,false));
        // Over its own cell, nothing moves: not even the first pinning of the others.
        assertEquals(Grid.LetGo.BACK,Grid.letGo(NOTE,NOTE,true));
        assertEquals(Grid.LetGo.BACK,Grid.letGo(COLLECTION,COLLECTION,true));
    }

    @Test public void filesFromAnotherAppGoWithTheNoteOrCollectionUnderThemElseWithWhatTheGridShows() {
        assertEquals(Grid.Files.NOTE,Grid.filesOnto(NOTE));
        assertEquals(Grid.Files.COLLECTION,Grid.filesOnto(COLLECTION));
        // A file's icon, the Favourites collection, an empty cell: Home, or the card's collection.
        assertEquals(Grid.Files.HERE,Grid.filesOnto(FILE));
        assertEquals(Grid.Files.HERE,Grid.filesOnto(FAVOURITES));
        assertEquals(Grid.Files.HERE,Grid.filesOnto(null));
    }

    @Test public void theDockComesFirstThenThePopUpThenWhatIsAroundIt() {
        assertEquals(Grid.Zone.DOCK,Grid.zone(true,true,false,false));
        assertEquals(Grid.Zone.DOCK,Grid.zone(true,false,false,true));
        assertEquals(Grid.Zone.CARD,Grid.zone(false,true,true,true));
        // Out of the card onto the dimmed grid: one level up.
        assertEquals(Grid.Zone.UP,Grid.zone(false,true,false,true));
        assertEquals(Grid.Zone.NONE,Grid.zone(false,true,false,false));
        assertEquals(Grid.Zone.HOME,Grid.zone(false,false,false,true));
        assertEquals(Grid.Zone.NONE,Grid.zone(false,false,false,false));
    }
}
