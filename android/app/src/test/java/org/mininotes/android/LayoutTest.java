// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** Icons stay in the cell they were put in, with empty cells left empty, as on a phone's own home screen. */
public class LayoutTest {
    private static final int N=Layout.NONE;
    private static Map<String,int[]> arrange(int columns,Object... idAndCell) {
        List<String> ids=new java.util.ArrayList<>();List<Integer> cells=new java.util.ArrayList<>();
        for(int at=0;at<idAndCell.length;at+=2){ids.add((String)idAndCell[at]);cells.add((Integer)idAndCell[at+1]);}
        return Layout.arrange(ids,cells,columns);
    }
    private static String where(Map<String,int[]> drawn,String id){int[] at=drawn.get(id);return at[0]+","+at[1];}

    @Test public void withNoCellsTheGridIsDrawnAsItAlwaysWasOneAfterTheOther() {
        Map<String,int[]> drawn=arrange(4,"a",N,"b",N,"c",N,"d",N,"e",N);
        assertEquals("0,0",where(drawn,"a"));assertEquals("0,3",where(drawn,"d"));assertEquals("1,0",where(drawn,"e"));
        assertEquals(2,Layout.rows(drawn));
        assertEquals(List.of("a","b","c","d","e"),List.copyOf(drawn.keySet()));
    }

    @Test public void aThingPutInACellStaysThereAndTheCellsAroundItStayEmpty() {
        Map<String,int[]> drawn=arrange(4,"a",Layout.cell(0,0),"b",Layout.cell(2,3),"c",Layout.cell(0,2));
        assertEquals("2,3",where(drawn,"b"));
        assertNull("nothing packs into the gap",Layout.at(drawn,0,1));
        assertNull(Layout.at(drawn,1,0));
        assertEquals(3,Layout.rows(drawn));
    }

    @Test public void somethingNewTakesTheFirstFreeCellFromTheTop() {
        Map<String,int[]> drawn=arrange(4,"a",Layout.cell(0,0),"b",Layout.cell(0,2),"new",N);
        assertEquals("0,1",where(drawn,"new"));
    }

    @Test public void aGridTooNarrowForACellMovesThatIconToTheNextFreeCellAfterItsRow() {
        // Put in column 6 on a wide PC window; the phone's grid is four across.
        Map<String,int[]> drawn=arrange(4,"a",Layout.cell(0,3),"wide",Layout.cell(0,6),"b",Layout.cell(1,0));
        assertEquals("0,3",where(drawn,"a"));
        assertEquals("1,1",where(drawn,"wide"));
        // And two that claim the same cell do not sit on each other.
        Map<String,int[]> twice=arrange(4,"a",Layout.cell(0,1),"b",Layout.cell(0,1));
        assertEquals("0,1",where(twice,"a"));assertEquals("0,2",where(twice,"b"));
    }

    @Test public void lettingGoInAnEmptyCellPinsEveryIconWhereItIsAndMovesThatOne() {
        Map<String,int[]> drawn=arrange(4,"a",N,"b",N,"c",N);
        Map<String,Integer> kept=Layout.moveTo(drawn,"a",3,2);
        assertEquals(Integer.valueOf(Layout.cell(3,2)),kept.get("a"));
        assertEquals("the others stay where they were drawn",Integer.valueOf(Layout.cell(0,1)),kept.get("b"));
        assertEquals(Integer.valueOf(Layout.cell(0,2)),kept.get("c"));
        // Drawn again from what was kept: a gap where a was, and a down in its cell.
        Map<String,int[]> again=Layout.arrange(Arrays.asList("a","b","c"),Arrays.asList(kept.get("a"),kept.get("b"),kept.get("c")),4);
        assertNull(Layout.at(again,0,0));
        assertEquals("a",Layout.at(again,3,2));
    }

    @Test public void lettingGoOnAnotherIconIsNotAMoveToAPlace() {
        Map<String,int[]> drawn=arrange(4,"a",N,"b",N);
        assertNull(Layout.moveTo(drawn,"a",0,1));
        assertNotNull("its own cell is fine",Layout.moveTo(drawn,"a",0,0));
        assertNull(Layout.moveTo(drawn,"a",-1,0));
    }

    /** Home's places, by id, each with its cell, in the order they are shown. */
    private static Map<String,Integer> places(Object... idAndCell) {
        Map<String,Integer> places=new java.util.LinkedHashMap<>();
        for(int at=0;at<idAndCell.length;at+=2)places.put((String)idAndCell[at],(Integer)idAndCell[at+1]);
        return places;
    }

    @Test public void onAHomeNobodyMovedFavouritesStandsFirstAndTheArchiveAndTheBinLast() {
        Map<String,int[]> drawn=Layout.home(Arrays.asList("a","b","c"),Arrays.asList(N,N,N),
            places("favourites",N,"archive",N,"bin",N),"favourites",4);
        assertEquals("0,0",where(drawn,"favourites"));
        assertEquals("0,1",where(drawn,"a"));assertEquals("0,3",where(drawn,"c"));
        assertEquals("after everything, where a phone keeps its bin","1,0",where(drawn,"archive"));
        assertEquals("1,1",where(drawn,"bin"));
        // Without the Favourites collection, the things start the grid.
        Map<String,int[]> bare=Layout.home(Arrays.asList("a"),Arrays.asList(N),places("archive",N,"bin",N),"favourites",4);
        assertEquals("0,0",where(bare,"a"));assertEquals("0,1",where(bare,"archive"));assertEquals("0,2",where(bare,"bin"));
    }

    @Test public void onAHomeWithGapsTheArchiveAndTheBinGoAfterTheLastIconNotIntoAGap() {
        // As seen on a real phone: the first row left empty on purpose, things in rows 1 and 4.
        Map<String,int[]> drawn=Layout.home(Arrays.asList("perso","photos","more"),
            Arrays.asList(Layout.cell(1,1),Layout.cell(1,2),Layout.cell(4,1)),places("archive",N,"bin",N),"favourites",4);
        assertNull("the empty first row stays empty",Layout.at(drawn,0,0));
        assertEquals("4,2",where(drawn,"archive"));
        assertEquals("4,3",where(drawn,"bin"));
        // And past the end of the row, on to the next.
        Map<String,int[]> full=Layout.home(Arrays.asList("a"),Arrays.asList(Layout.cell(2,3)),places("archive",N,"bin",N),null,4);
        assertEquals("3,0",where(full,"archive"));assertEquals("3,1",where(full,"bin"));
        // Something new with no cell still takes the first free cell from the top, as a new app does.
        Map<String,int[]> fresh=Layout.home(Arrays.asList("a","new"),Arrays.asList(Layout.cell(2,3),N),places("bin",N),null,4);
        assertEquals("0,0",where(fresh,"new"));assertEquals("3,0",where(fresh,"bin"));
    }

    @Test public void aPlaceKeepsItsCellAndAThingPutThereWhileItWasHiddenKeepsItToo() {
        Map<String,int[]> drawn=Layout.home(Arrays.asList("a","b"),Arrays.asList(Layout.cell(0,0),Layout.cell(0,3)),
            places("archive",Layout.cell(2,1),"bin",Layout.cell(0,3)),"favourites",4);
        assertEquals("2,1",where(drawn,"archive"));
        assertEquals("b was let go in the bin's cell while the bin was not shown","0,3",where(drawn,"b"));
        assertEquals("the next free cell after its row","1,0",where(drawn,"bin"));
        // Favourites with a cell stands there, not first.
        Map<String,int[]> fav=Layout.home(Arrays.asList("a"),Arrays.asList(N),places("favourites",Layout.cell(1,2)),"favourites",4);
        assertEquals("1,2",where(fav,"favourites"));assertEquals("0,0",where(fav,"a"));
    }

    @Test public void aPlaceMovedPinsEveryIconAndTheOtherPlacesToo() {
        Map<String,int[]> drawn=Layout.home(Arrays.asList("a","b"),Arrays.asList(N,N),places("archive",N,"bin",N),null,4);
        Map<String,Integer> kept=Layout.moveTo(drawn,"bin",3,3);
        assertEquals(Integer.valueOf(Layout.cell(3,3)),kept.get("bin"));
        assertEquals(Integer.valueOf(Layout.cell(0,2)),kept.get("archive"));
        assertEquals(Integer.valueOf(Layout.cell(0,1)),kept.get("b"));
        Map<String,int[]> again=Layout.home(Arrays.asList("a","b"),Arrays.asList(kept.get("a"),kept.get("b")),
            places("archive",kept.get("archive"),"bin",kept.get("bin")),null,4);
        assertEquals("bin",Layout.at(again,3,3));assertNull("its old cell stays empty",Layout.at(again,0,3));
    }

    // ---- Home's pages (decisions 45-50) ------------------------------------------------------------------------------

    private static String spot(Map<String,Layout.Spot> drawn,String id){Layout.Spot at=drawn.get(id);return at.x()+","+at.y()+" "+at.row()+","+at.column();}
    private static Map<String,Layout.Spot> pages(int columns,int rows,java.util.Set<String> after,Object... idPageCell) {
        List<String> ids=new java.util.ArrayList<>();List<Integer> pages=new java.util.ArrayList<>(),cells=new java.util.ArrayList<>();
        for(int at=0;at<idPageCell.length;at+=3){ids.add((String)idPageCell[at]);pages.add((Integer)idPageCell[at+1]);cells.add((Integer)idPageCell[at+2]);}
        return Layout.pages(ids,pages,cells,after,columns,rows);
    }

    @Test public void aPageIsOneNumberBothWaysAndTheCentreIsNoughtNought() {
        for(int[] xy:new int[][]{{0,0},{1,0},{-1,0},{0,-3},{7,-5},{-256,255}}) {
            int page=Layout.page(xy[0],xy[1]);
            assertEquals(xy[0],Layout.pageX(page));assertEquals(xy[1],Layout.pageY(page));
            assertNotEquals(Layout.NO_PAGE,page);
        }
        assertEquals(Layout.page(0,0),Layout.CENTRE);
        assertEquals("far off the edge is the edge",Layout.page(255,0),Layout.page(9999,0));
    }

    @Test public void aLongHomeFromBeforePagesBecomesAColumnOfPages() {
        // Kept as one long grid four across; pages of four by three.
        Map<String,Layout.Spot> drawn=pages(4,3,null,"a",0,Layout.cell(0,0),"b",0,Layout.cell(2,3),"c",0,Layout.cell(3,1),"d",0,Layout.cell(7,2));
        assertEquals("0,0 0,0",spot(drawn,"a"));
        assertEquals("0,0 2,3",spot(drawn,"b"));
        assertEquals("row 3 is the first row of the page below","0,1 0,1",spot(drawn,"c"));
        assertEquals("0,2 1,2",spot(drawn,"d"));
        assertEquals(java.util.Set.of(List.of(0,0),List.of(0,1),List.of(0,2)),Layout.active(drawn));
    }

    @Test public void aThingPutOnAPageStaysThereAndPagesExistOnlyWhereSomethingStands() {
        Map<String,Layout.Spot> drawn=pages(4,3,null,"a",Layout.CENTRE,Layout.cell(1,1),"right",Layout.page(1,0),Layout.cell(2,0),"up",Layout.page(0,-1),Layout.cell(0,3));
        assertEquals("1,0 2,0",spot(drawn,"right"));assertEquals("0,-1 0,3",spot(drawn,"up"));
        assertEquals(java.util.Set.of(List.of(0,0),List.of(1,0),List.of(0,-1)),Layout.active(drawn));
        // A swipe goes to the nearest page there is that way, and nowhere where there is none.
        assertArrayEquals(new int[]{1,0},Layout.next(Layout.active(drawn),0,0,1,0));
        assertArrayEquals(new int[]{0,-1},Layout.next(Layout.active(drawn),0,0,0,-1));
        assertNull(Layout.next(Layout.active(drawn),0,0,-1,0));
        assertNull(Layout.next(Layout.active(drawn),0,0,0,1));
        Map<String,Layout.Spot> far=pages(4,3,null,"far",Layout.page(3,0),Layout.cell(0,0));
        assertArrayEquals("over the empty ones between",new int[]{3,0},Layout.next(Layout.active(far),0,0,1,0));
        // The dots: the pages there are, and the one in view even while it is empty (a thing being carried onto it).
        assertEquals(java.util.Set.of(List.of(0,0),List.of(1,0),List.of(0,-1)),Layout.dotted(drawn,1,0));
        assertEquals(java.util.Set.of(List.of(0,0),List.of(1,0),List.of(0,-1),List.of(-1,0)),Layout.dotted(drawn,-1,0));
        assertEquals("only the main page: one dot, so none are drawn",java.util.Set.of(List.of(0,0)),Layout.dotted(Map.of(),0,0));
    }

    @Test public void aWindowTooSmallForACellMovesThatIconOnItsOwnPageAndWritesNothing() {
        // Put in row 2, column 6 of a big window; the window now holds four by three.
        Map<String,Layout.Spot> drawn=pages(4,3,null,"wide",Layout.CENTRE,Layout.cell(2,6),"a",Layout.CENTRE,Layout.cell(2,3));
        assertEquals("the next free cell after where it was, round to the start of its page","0,0 0,0",spot(drawn,"wide"));
        Map<String,Layout.Spot> deep=pages(4,3,null,"deep",Layout.page(1,0),Layout.cell(5,1));
        assertEquals("on its own page, not the centre","1,0 2,1",spot(deep,"deep"));
        // A page full to the last cell: the page below.
        List<Object> full=new java.util.ArrayList<>();
        for(int at=0;at<12;at++){full.add("t"+at);full.add(Layout.CENTRE);full.add(Layout.cell(at/4,at%4));}
        full.add("over");full.add(Layout.CENTRE);full.add(Layout.cell(5,5));
        assertEquals("0,1 0,0",spot(pages(4,3,null,full.toArray()),"over"));
    }

    @Test public void somethingNewGoesOnTheCentrePageAndTheBinAfterItsLastIcon() {
        java.util.Set<String> after=java.util.Set.of("archive","bin");
        Map<String,Layout.Spot> drawn=pages(4,3,after,"a",Layout.CENTRE,Layout.cell(1,1),"b",Layout.CENTRE,Layout.cell(1,2),
            "new",Layout.NO_PAGE,N,"archive",Layout.NO_PAGE,N,"bin",Layout.NO_PAGE,N,"elsewhere",Layout.page(-1,0),Layout.cell(0,0));
        assertEquals("the first free cell of the centre page","0,0 0,0",spot(drawn,"new"));
        assertEquals("after the last icon, not in the empty first row","0,0 1,3",spot(drawn,"archive"));
        assertEquals("0,0 2,0",spot(drawn,"bin"));
        // The centre page full after its last icon: the page below.
        Map<String,Layout.Spot> end=pages(2,1,after,"a",Layout.CENTRE,Layout.cell(0,1),"bin",Layout.NO_PAGE,N);
        assertEquals("0,1 0,0",spot(end,"bin"));
    }

    @Test public void lettingGoOnAnotherPagePinsEveryIconAndMakesThatPage() {
        Map<String,Layout.Spot> drawn=pages(4,3,null,"a",Layout.NO_PAGE,N,"b",Layout.NO_PAGE,N);
        Map<String,Layout.Spot> kept=Layout.moveTo(drawn,"b",new Layout.Spot(1,0,2,2));
        assertEquals("1,0 2,2",spot(kept,"b"));assertEquals("0,0 0,0",spot(kept,"a"));
        assertTrue(Layout.active(kept).contains(List.of(1,0)));
        assertNull("not onto another icon",Layout.moveTo(kept,"b",new Layout.Spot(0,0,0,0)));
        assertEquals("a",Layout.at(kept,0,0,0,0));assertNull(Layout.at(kept,1,0,0,0));
        // Drawn again from what was kept: there, and the page it left is the centre one, still there.
        Map<String,Layout.Spot> again=pages(4,3,null,"a",kept.get("a").page(),kept.get("a").cell(),"b",kept.get("b").page(),kept.get("b").cell());
        assertEquals("1,0 2,2",spot(again,"b"));
    }

    @Test public void aWholePageCarriedChangesPlacesWithTheOneItIsLetGoOnOrMovesToAnEmptyPlace() {
        Map<String,Layout.Spot> drawn=pages(4,3,null,"main",Layout.CENTRE,Layout.cell(0,0),
            "right",Layout.page(1,0),Layout.cell(1,2),"also right",Layout.page(1,0),Layout.cell(2,3),"below",Layout.page(0,1),Layout.cell(0,1));
        // The page to the right let go on the main one: the two change places, every icon in its own cell.
        Map<String,Layout.Spot> swapped=Layout.movePage(drawn,1,0,0,0);
        assertEquals("0,0 1,2",spot(swapped,"right"));assertEquals("0,0 2,3",spot(swapped,"also right"));
        assertEquals("1,0 0,0",spot(swapped,"main"));
        assertEquals("untouched","0,1 0,1",spot(swapped,"below"));
        // Onto an empty place: it moves there, and the place it left is gone.
        Map<String,Layout.Spot> moved=Layout.movePage(drawn,0,1,-1,0);
        assertEquals("-1,0 0,1",spot(moved,"below"));
        assertFalse(Layout.active(moved).contains(List.of(0,1)));
        assertTrue(Layout.active(moved).contains(List.of(-1,0)));
        // Onto itself: nothing changes.
        assertEquals(drawn,Layout.movePage(drawn,1,0,1,0));
    }

    @Test public void aCellIsOneNumberBothWays() {
        int cell=Layout.cell(7,5);
        assertEquals(7,Layout.row(cell));assertEquals(5,Layout.column(cell));
        assertEquals(-1,Layout.row(Layout.NONE));
        assertArrayEquals(new int[]{2,1},Layout.under(130,250,100,100,4));
        assertNull("past the last column",Layout.under(450,10,100,100,4));
        assertNull(Layout.under(-1,10,100,100,4));
    }
}
