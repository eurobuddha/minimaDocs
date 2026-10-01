package org.mininotes.android;

import org.junit.*;
import static org.junit.Assert.*;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The PC's Home, worked out without a window (docs/HOME.md, step 3): how many icons go across a width and how many
 * favourites the dock holds, what a key does on an icon and where an arrow takes the keyboard, where Ctrl+Tab starts and
 * how it goes round the overview's cards, and the tabs a 0.1 build kept read once into the overview. And, since icons
 * stay where they are put (decision 39): the cell under the pointer, the arrows over empty cells, Ctrl+arrow moving an
 * icon a cell, and what the dock draws once a favourite has been taken out of it.
 */
public class DesktopHomeRulesTest {
    private static final int CELL=DesktopHome.CELL,SIDE=DesktopHome.SIDE;

    @Test public void asManyColumnsAsFitAndOneAtTheLeast() {
        assertEquals(1,DesktopHome.columns(0));
        assertEquals(1,DesktopHome.columns(2*SIDE+CELL-1));
        assertEquals(1,DesktopHome.columns(2*SIDE+CELL));
        assertEquals(1,DesktopHome.columns(2*SIDE+2*CELL-1));
        assertEquals(2,DesktopHome.columns(2*SIDE+2*CELL));
        // The window as it opens, 1120 wide: nine across; with the tree beside it (280 and its grip), six.
        assertEquals(9,DesktopHome.columns(1120));
        assertEquals(6,DesktopHome.columns(1120-280-9));
        // No most: a wide screen shows as many as fit, as a PC's desktop does (decision 3's "as many as fit").
        assertEquals((3840-2*SIDE)/CELL,DesktopHome.columns(3840));
    }

    @Test public void theDockHoldsAsManyFavouritesAsFit() {
        assertEquals(1,DesktopHome.dockRoom(0));
        assertEquals(1,DesktopHome.dockRoom(2*DesktopHome.DOCK_ENDS+DesktopHome.DOCKED));
        assertEquals(5,DesktopHome.dockRoom(2*DesktopHome.DOCK_ENDS+5*DesktopHome.DOCKED));
        assertEquals(4,DesktopHome.dockRoom(2*DesktopHome.DOCK_ENDS+5*DesktopHome.DOCKED-1));
        // The narrowest window still holds more than the phone's five.
        assertTrue(DesktopHome.dockRoom(820-32)>Things.DOCK_PHONE);
    }

    @Test public void whatAKeyDoesOnAnIcon() {
        assertEquals(DesktopHome.Does.OPEN,DesktopHome.does(KeyEvent.VK_ENTER,false,false));
        assertEquals(DesktopHome.Does.OPEN,DesktopHome.does(KeyEvent.VK_SPACE,false,false));
        assertEquals(DesktopHome.Does.RENAME,DesktopHome.does(KeyEvent.VK_F2,false,false));
        assertEquals(DesktopHome.Does.BIN,DesktopHome.does(KeyEvent.VK_DELETE,false,false));
        assertEquals(DesktopHome.Does.MENU,DesktopHome.does(KeyEvent.VK_CONTEXT_MENU,false,false));
        assertEquals("Shift+F10 is the Menu key",DesktopHome.Does.MENU,DesktopHome.does(KeyEvent.VK_F10,true,false));
        assertEquals("F10 alone is Windows' own",DesktopHome.Does.NONE,DesktopHome.does(KeyEvent.VK_F10,false,false));
        assertEquals(DesktopHome.Does.LEFT,DesktopHome.does(KeyEvent.VK_LEFT,false,false));
        assertEquals(DesktopHome.Does.DOWN,DesktopHome.does(KeyEvent.VK_DOWN,false,false));
        assertEquals(DesktopHome.Does.FIRST,DesktopHome.does(KeyEvent.VK_HOME,false,false));
        assertEquals(DesktopHome.Does.LAST,DesktopHome.does(KeyEvent.VK_END,false,false));
        // With Ctrl or Alt held the key is the window's: Ctrl+N, Ctrl+Tab, Ctrl+Enter.
        assertEquals(DesktopHome.Does.NONE,DesktopHome.does(KeyEvent.VK_ENTER,false,true));
        assertEquals(DesktopHome.Does.NONE,DesktopHome.does(KeyEvent.VK_N,false,false));
        assertEquals(DesktopHome.Does.NONE,DesktopHome.does(KeyEvent.VK_TAB,false,false));
    }

    @Test public void theArrowsMoveAlongTheRowsAndDownTheColumns() {
        // Ten icons, four across: 0-3, 4-7, 8-9.
        assertEquals(1,DesktopHome.step(0,10,4,DesktopHome.Does.RIGHT));
        assertEquals("along the end of a row onto the next",4,DesktopHome.step(3,10,4,DesktopHome.Does.RIGHT));
        assertEquals("at the last it stays",9,DesktopHome.step(9,10,4,DesktopHome.Does.RIGHT));
        assertEquals(0,DesktopHome.step(0,10,4,DesktopHome.Does.LEFT));
        assertEquals(5,DesktopHome.step(1,10,4,DesktopHome.Does.DOWN));
        assertEquals(9,DesktopHome.step(5,10,4,DesktopHome.Does.DOWN));
        assertEquals("nothing under it on a lower row: the last icon",9,DesktopHome.step(6,10,4,DesktopHome.Does.DOWN));
        assertEquals("on the last row it stays",8,DesktopHome.step(8,10,4,DesktopHome.Does.DOWN));
        assertEquals(2,DesktopHome.step(6,10,4,DesktopHome.Does.UP));
        assertEquals("on the first row it stays",2,DesktopHome.step(2,10,4,DesktopHome.Does.UP));
        assertEquals(0,DesktopHome.step(7,10,4,DesktopHome.Does.FIRST));
        assertEquals(9,DesktopHome.step(2,10,4,DesktopHome.Does.LAST));
        assertEquals(-1,DesktopHome.step(0,0,4,DesktopHome.Does.RIGHT));
    }

    @Test public void ctrlTabStartsOnTheOneBeforeAndGoesRound() {
        // What is on the screen is the newest card: one Ctrl+Tab chooses the one before it, as Alt+Tab does.
        assertEquals(1,DesktopOverview.firstChosen(4,true));
        // On Home with nothing of the list on the screen: the newest.
        assertEquals(0,DesktopOverview.firstChosen(4,false));
        assertEquals(0,DesktopOverview.firstChosen(1,true));
        assertEquals(-1,DesktopOverview.firstChosen(0,false));
        // Along, round from the last to the first, and back with Shift the other way.
        assertEquals(2,DesktopOverview.along(1,4,false));
        assertEquals(0,DesktopOverview.along(3,4,false));
        assertEquals(3,DesktopOverview.along(0,4,true));
        assertEquals(0,DesktopOverview.along(-1,4,false));
        assertEquals(-1,DesktopOverview.along(0,0,false));
    }

    @Test public void the01TabsAreReadOnceIntoTheOverview() {
        // A note, a book, All collections, the drop box and a collection, with the collection's tab in front.
        Overview open=DesktopOverview.fromTabs("PAGE:n1|BOOK:b1|LIBRARY:*|DROPS:drops|COLLECTION:c1#4");
        List<Overview.Open> all=open.all();
        assertEquals(3,all.size());
        assertEquals("the one in front first",new Overview.Open(Overview.Kind.COLLECTION,"c1"),all.get(0));
        assertEquals(new Overview.Open(Overview.Kind.NOTE,"n1"),all.get(1));
        assertEquals("a book is a collection inside a collection",new Overview.Open(Overview.Kind.COLLECTION,"b1"),all.get(2));
        // Nothing kept, or a line from something else: nothing open.
        assertTrue(DesktopOverview.fromTabs("").isEmpty());
        assertTrue(DesktopOverview.fromTabs(null).isEmpty());
        assertEquals(1,DesktopOverview.fromTabs("PAGE:n1#x").size());
        // No front said: the first tab is the front.
        assertEquals("n2",DesktopOverview.fromTabs("PAGE:n2|PAGE:n3").all().get(0).id);
    }

    @Test public void aCollectionsFaceShowsHowMuchItHolds() {
        assertEquals(5,DesktopHome.countIn("2 collections · 3 notes"));
        assertEquals(1,DesktopHome.countIn("1 note"));
        assertEquals(0,DesktopHome.countIn("Empty"));
        assertEquals(0,DesktopHome.countIn(null));
    }

    @Test public void ctrlOrAltWithAnArrowMovesTheIconItself() {
        assertEquals(DesktopHome.Does.MOVE_LEFT,DesktopHome.does(KeyEvent.VK_LEFT,false,true));
        assertEquals(DesktopHome.Does.MOVE_RIGHT,DesktopHome.does(KeyEvent.VK_RIGHT,false,true));
        assertEquals(DesktopHome.Does.MOVE_UP,DesktopHome.does(KeyEvent.VK_UP,false,true));
        assertEquals(DesktopHome.Does.MOVE_DOWN,DesktopHome.does(KeyEvent.VK_DOWN,false,true));
        assertEquals("with Shift too it is not ours",DesktopHome.Does.NONE,DesktopHome.does(KeyEvent.VK_LEFT,true,true));
        assertEquals("any other key with Ctrl is the window's",DesktopHome.Does.NONE,DesktopHome.does(KeyEvent.VK_HOME,false,true));
    }

    /** A grid laid out as Layout does it: ids and their cells, NONE for one never put anywhere. */
    private static Map<String,int[]> grid(int columns,Object... idAndCell) {
        List<String> ids=new ArrayList<>();List<Integer> cells=new ArrayList<>();
        for(int at=0;at<idAndCell.length;at+=2){ids.add((String)idAndCell[at]);cells.add((Integer)idAndCell[at+1]);}
        return Layout.arrange(ids,cells,columns);
    }

    @Test public void theArrowsStepOverTheEmptyCells() {
        //   a . . b
        //   . . . .
        //   . c . .
        //   e . . d
        Map<String,int[]> drawn=grid(4,"a",Layout.cell(0,0),"b",Layout.cell(0,3),"c",Layout.cell(2,1),"d",Layout.cell(3,3),"e",Layout.cell(3,0));
        assertEquals("along the row, over the gap","b",DesktopHome.stepTo(drawn,"a",DesktopHome.Does.RIGHT));
        assertEquals("on to the next icon below, in reading order","c",DesktopHome.stepTo(drawn,"b",DesktopHome.Does.RIGHT));
        assertEquals("b",DesktopHome.stepTo(drawn,"c",DesktopHome.Does.LEFT));
        assertEquals("at the first it stays","a",DesktopHome.stepTo(drawn,"a",DesktopHome.Does.LEFT));
        assertEquals("at the last it stays","d",DesktopHome.stepTo(drawn,"d",DesktopHome.Does.RIGHT));
        assertEquals("straight down wins over nearer but to the side","e",DesktopHome.stepTo(drawn,"a",DesktopHome.Does.DOWN));
        assertEquals("d",DesktopHome.stepTo(drawn,"b",DesktopHome.Does.DOWN));
        assertEquals("b",DesktopHome.stepTo(drawn,"d",DesktopHome.Does.UP));
        assertEquals("as near either way: the one on the left","a",DesktopHome.stepTo(grid(4,"a",Layout.cell(0,0),"b",Layout.cell(0,2),"c",Layout.cell(1,1)),"c",DesktopHome.Does.UP));
        assertEquals("nothing above it: it stays","a",DesktopHome.stepTo(drawn,"a",DesktopHome.Does.UP));
        assertEquals("nothing below it: it stays","d",DesktopHome.stepTo(drawn,"d",DesktopHome.Does.DOWN));
        assertEquals("a",DesktopHome.stepTo(drawn,"d",DesktopHome.Does.FIRST));
        assertEquals("d",DesktopHome.stepTo(drawn,"a",DesktopHome.Does.LAST));
        assertEquals("not on the grid: nowhere to go","x",DesktopHome.stepTo(drawn,"x",DesktopHome.Does.RIGHT));
    }

    @Test public void aGridNobodyHasMovedAnythingOnStepsAsItAlwaysDid() {
        // Ten icons, four across, with no cells: 0-3, 4-7, 8-9, as the dock's step() has them.
        Object[] ten=new Object[20];for(int i=0;i<10;i++){ten[2*i]=Integer.toString(i);ten[2*i+1]=Layout.NONE;}
        Map<String,int[]> drawn=grid(4,ten);
        for(DesktopHome.Does move:new DesktopHome.Does[]{DesktopHome.Does.LEFT,DesktopHome.Does.RIGHT,DesktopHome.Does.UP,DesktopHome.Does.DOWN,DesktopHome.Does.FIRST,DesktopHome.Does.LAST})
            for(int at=0;at<10;at++)
                assertEquals(move+" from "+at,Integer.toString(DesktopHome.step(at,10,4,move)),DesktopHome.stepTo(drawn,Integer.toString(at),move));
    }

    @Test public void ctrlArrowMovesAnIconOneCellIntoAnEmptyOne() {
        Map<String,int[]> drawn=grid(4,"a",Layout.cell(0,0),"b",Layout.cell(0,3),"c",Layout.cell(2,1),"f",Layout.cell(2,2),"e",Layout.cell(3,0));
        assertArrayEquals(new int[]{0,1},DesktopHome.nudged(drawn,"a",DesktopHome.Does.MOVE_RIGHT,4));
        assertArrayEquals(new int[]{1,0},DesktopHome.nudged(drawn,"a",DesktopHome.Does.MOVE_DOWN,4));
        assertNull("the left edge",DesktopHome.nudged(drawn,"a",DesktopHome.Does.MOVE_LEFT,4));
        assertNull("the top",DesktopHome.nudged(drawn,"a",DesktopHome.Does.MOVE_UP,4));
        assertNull("the right edge of a grid four across",DesktopHome.nudged(drawn,"b",DesktopHome.Does.MOVE_RIGHT,4));
        assertNull("another icon in the way",DesktopHome.nudged(drawn,"c",DesktopHome.Does.MOVE_RIGHT,4));
        assertArrayEquals("under the last row: the empty row the grid keeps",new int[]{4,0},DesktopHome.nudged(drawn,"e",DesktopHome.Does.MOVE_DOWN,4));
        assertNull("an arrow alone moves the keyboard, not the icon",DesktopHome.nudged(drawn,"a",DesktopHome.Does.RIGHT,4));
        assertNull("not on the grid",DesktopHome.nudged(drawn,"x",DesktopHome.Does.MOVE_RIGHT,4));
        // The cell it goes to is written as Layout writes a move: every icon pinned, that one in its new cell.
        int[] to=DesktopHome.nudged(drawn,"a",DesktopHome.Does.MOVE_RIGHT,4);
        Map<String,Integer> kept=Layout.moveTo(drawn,"a",to[0],to[1]);
        assertEquals(Integer.valueOf(Layout.cell(0,1)),kept.get("a"));assertEquals(Integer.valueOf(Layout.cell(2,2)),kept.get("f"));
    }

    @Test public void theCellUnderThePointerCountsTheScrollAndTheMargins() {
        int left=56,top=DesktopHome.TOP,high=DesktopHome.HIGH;Point none=new Point(0,0);
        assertArrayEquals(new int[]{0,0},DesktopHome.cellUnder(new Point(left+5,top+5),none,left,top,9));
        assertArrayEquals("row 3, column 5",new int[]{2,4},DesktopHome.cellUnder(new Point(left+4*CELL+1,top+2*high+1),none,left,top,9));
        assertArrayEquals(new int[]{1,8},DesktopHome.cellUnder(new Point(left+9*CELL-1,top+2*high-1),none,left,top,9));
        // Scrolled down 300: a point 20 from the top of what shows is 320 down the grid.
        assertArrayEquals(new int[]{(20+300-top)/high,0},DesktopHome.cellUnder(new Point(left+1,20),new Point(0,300),left,top,9));
        assertArrayEquals("scrolled by exactly a row: the row below",new int[]{3,4},DesktopHome.cellUnder(new Point(left+4*CELL+1,top+2*high+1),new Point(0,high),left,top,9));
        // In the room round the grid: the nearest cell.
        assertArrayEquals("left of the first column",new int[]{0,0},DesktopHome.cellUnder(new Point(3,top+5),none,left,top,9));
        assertArrayEquals("right of the last column",new int[]{0,8},DesktopHome.cellUnder(new Point(left+9*CELL+30,top+5),none,left,top,9));
        assertArrayEquals("above the first row",new int[]{0,2},DesktopHome.cellUnder(new Point(left+2*CELL+5,2),none,left,top,9));
        assertArrayEquals("a grid of no width has one column",new int[]{0,0},DesktopHome.cellUnder(new Point(left+500,top+5),none,left,top,0));
    }

    private static NoteStore.Branch fave(String id){return new NoteStore.Branch(NoteStore.Branch.Kind.PAGE,id,Sharing.EVERYTHING,id,"",0,0,false);}
    private static List<String> ids(List<NoteStore.Branch> lines){List<String> out=new ArrayList<>();for(NoteStore.Branch one:lines)out.add(one.id);return out;}

    @Test public void theDockDrawsTheNotebooksDockThenTheFavouritesPastItButNoneTakenOut() {
        List<NoteStore.Branch> dock=List.of(fave("a"),fave("b")),beyond=List.of(fave("c"),fave("d"),fave("e"));
        DesktopHome.Docked wide=DesktopHome.docked(dock,beyond,Set.of(),10);
        assertEquals(List.of("a","b","c","d","e"),ids(wide.shown()));assertTrue(wide.rest().isEmpty());
        // d taken out of the dock here: it waits in the Favourites icon, though there is room for it.
        DesktopHome.Docked out=DesktopHome.docked(dock,beyond,Set.of("d"),10);
        assertEquals(List.of("a","b","c","e"),ids(out.shown()));assertEquals(List.of("d"),ids(out.rest()));
        assertTrue("every icon it draws can be taken out",out.shows("e"));assertFalse(out.shows("d"));
        // Narrow: as many as fit, the rest in the order they were given.
        DesktopHome.Docked narrow=DesktopHome.docked(dock,beyond,Set.of("d"),3);
        assertEquals(List.of("a","b","c"),ids(narrow.shown()));assertEquals(List.of("d","e"),ids(narrow.rest()));
        assertEquals(List.of("b","c","d","e"),ids(DesktopHome.docked(dock,beyond,Set.of(),1).rest()));
        // One in the notebook's own dock is drawn whatever is kept here: taking it out takes it out of that dock too.
        assertEquals(List.of("a","b","c"),ids(DesktopHome.docked(dock,beyond,Set.of("a"),3).shown()));
    }
}
