package org.mininotes.android;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

/**
 * The icon picker's rules, without a window (docs/HOME.md, decision 32): Default first and then the set in its order,
 * or what a search finds; and the grid painting only the rows that show, however long the set is.
 */
public class DesktopIconPickerTest {
    @Test public void defaultFirstThenTheWholeSetInItsOrder() {
        List<String> all=DesktopIconPicker.shown("");
        assertEquals(DesktopIconPicker.DEFAULT,all.get(0));
        assertEquals(Icons.all().size()+1,all.size());
        assertEquals(new java.util.ArrayList<>(Icons.all().keySet()).get(0),all.get(1));
        assertEquals(all,DesktopIconPicker.shown("   "));assertEquals(all,DesktopIconPicker.shown(null));
    }

    @Test public void typedTheSetIsFoundAndDefaultStandsAside() {
        List<String> heart=DesktopIconPicker.shown("heart");
        assertEquals("heart",heart.get(0));assertFalse(heart.contains(DesktopIconPicker.DEFAULT));
        assertTrue(heart.size()<=Looks.FOUND_MOST);
        assertTrue(DesktopIconPicker.shown("zzqqxxnothing").isEmpty());
    }

    @Test public void theGridPaintsOnlyTheRowsThatShow() {
        assertEquals(9,DesktopIconPicker.columns(DesktopIconPicker.CELL*9));assertEquals(9,DesktopIconPicker.columns(DesktopIconPicker.CELL*9+51));
        assertEquals(1,DesktopIconPicker.columns(10));
        int cell=DesktopIconPicker.CELL,rows=DesktopIconPicker.rows(Icons.all().size()+1,9);
        assertEquals((Icons.all().size()+1+8)/9,rows);assertEquals(0,DesktopIconPicker.rows(0,9));assertEquals(1,DesktopIconPicker.rows(1,9));
        // The box's first sight: seven rows of the two hundred.
        assertArrayEquals(new int[]{0,6},DesktopIconPicker.visibleRows(0,7*cell,cell,rows));
        // Scrolled part way into a row: that row and the ones it reaches into.
        assertArrayEquals(new int[]{1,8},DesktopIconPicker.visibleRows(cell+10,7*cell,cell,rows));
        // One row repainted under the pointer.
        assertArrayEquals(new int[]{10,10},DesktopIconPicker.visibleRows(10*cell,cell,cell,rows));
        // The end of the grid: nothing past the last row.
        int[] end=DesktopIconPicker.visibleRows((rows-2)*cell,7*cell,cell,rows);assertEquals(rows-2,end[0]);assertEquals(rows-1,end[1]);
        // No band, or no rows: nothing painted.
        int[] none=DesktopIconPicker.visibleRows(0,0,cell,rows);assertTrue(none[1]<none[0]);
        none=DesktopIconPicker.visibleRows(0,300,cell,0);assertTrue(none[1]<none[0]);
        none=DesktopIconPicker.visibleRows(rows*cell+100,300,cell,rows);assertTrue(none[1]<none[0]);
    }

    @Test public void aNoteOrACollectionWearsALookAndNothingElseDoes() {
        assertEquals(NoteStore.Branch.Kind.PAGE,DesktopIconPicker.kindOf(line(NoteStore.Branch.Kind.PAGE)));
        assertEquals(NoteStore.Branch.Kind.COLLECTION,DesktopIconPicker.kindOf(line(NoteStore.Branch.Kind.COLLECTION)));
        assertEquals(NoteStore.Branch.Kind.COLLECTION,DesktopIconPicker.kindOf(line(NoteStore.Branch.Kind.BOOK)));
        assertNull(DesktopIconPicker.kindOf(line(NoteStore.Branch.Kind.FILE)));
        assertNull(DesktopIconPicker.kindOf(line(NoteStore.Branch.Kind.FAVOURITES)));
        assertNull(DesktopIconPicker.kindOf(null));
    }

    @Test public void anIconIsSaidInWordsAndDefaultSaysWhatItIs() {
        assertEquals("sticky note",DesktopIconPicker.said("sticky-note",true));
        assertEquals("Default: the note icon",DesktopIconPicker.said(DesktopIconPicker.DEFAULT,true));
        assertEquals("Default: what is inside it",DesktopIconPicker.said(DesktopIconPicker.DEFAULT,false));
    }

    private static NoteStore.Branch line(NoteStore.Branch.Kind kind){return new NoteStore.Branch(kind,"x","","Synthetic","",0,0,false);}
}
