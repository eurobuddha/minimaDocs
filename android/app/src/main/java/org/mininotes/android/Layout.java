// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where each icon stands on a grid: in the cell it was put in, with empty cells left empty, as a phone's own home
 * screen keeps them (the owner's ask, 2026-09-30: "move the assets freely on the desktop, not sorted one after the
 * other"; docs/HOME.md, decision 39).
 *
 * <p>A thing's cell is kept on this device, as its order always was, as one number: its row times {@link #WIDEST} and
 * then its column. Nothing has one until something on its grid is first moved - until then the grid is drawn as it
 * always was, one after the other in the owner's order - and at that first move every icon is pinned where it is
 * drawn, so nothing jumps into the gap the move leaves. A grid drawn narrower than the one a cell was chosen on moves
 * that icon to the first free cell after the end of its row; a thing with no cell, one made or moved in since, takes
 * the first free cell from the top, as a new app does on a phone.
 *
 * <p>Holds no Android types: the rules are unit tested, and both apps draw by them.
 */
final class Layout {
    /** No row is wider than this, so a cell is one number; a grid is drawn at most this many icons across. */
    static final int WIDEST=64;
    /** A thing with no cell of its own yet. */
    static final int NONE=-1;

    private Layout(){}

    static int cell(int row,int column){return row*WIDEST+Math.max(0,Math.min(WIDEST-1,column));}
    static int row(int cell){return cell<0?-1:cell/WIDEST;}
    static int column(int cell){return cell<0?-1:cell%WIDEST;}

    /**
     * Where each thing is drawn on a grid {@code columns} wide, as {row, column}, in the order given.
     *
     * @param ids   the things on the grid, in the owner's order
     * @param cells the cell each one was put in, or {@link #NONE}
     */
    static Map<String,int[]> arrange(List<String> ids,List<Integer> cells,int columns) {
        int wide=Math.max(1,Math.min(WIDEST,columns));
        Map<String,int[]> drawn=new LinkedHashMap<>();
        Set<Integer> taken=new HashSet<>();
        List<Integer> away=new ArrayList<>();
        // Where each was put, where the grid is wide enough and nothing got there first.
        for(int at=0;at<ids.size();at++) {
            int cell=cells.get(at)==null?NONE:cells.get(at);
            if(cell<0){away.add(at);continue;}
            int row=row(cell),column=column(cell);
            if(column<wide&&taken.add(row*wide+column))drawn.put(ids.get(at),new int[]{row,column});
            else away.add(at);
        }
        // Then those that were put somewhere this grid cannot show: the first free cell after the end of their row.
        for(int at:away) {
            int cell=cells.get(at)==null?NONE:cells.get(at);
            if(cell<0)continue;
            int from=row(cell)*wide+Math.min(column(cell),wide-1);
            int free=freeFrom(taken,from);
            taken.add(free);drawn.put(ids.get(at),new int[]{free/wide,free%wide});
        }
        // And those with no cell yet, in the owner's order, into the first free cells from the top.
        int next=0;
        for(int at:away) {
            int cell=cells.get(at)==null?NONE:cells.get(at);
            if(cell>=0)continue;
            next=freeFrom(taken,next);
            taken.add(next);drawn.put(ids.get(at),new int[]{next/wide,next%wide});
        }
        // Keep the order the things were given in, whatever order they were placed in.
        Map<String,int[]> ordered=new LinkedHashMap<>();
        for(String id:ids)ordered.put(id,drawn.get(id));
        return ordered;
    }

    private static int freeFrom(Set<Integer> taken,int from){int at=Math.max(0,from);while(taken.contains(at))at++;return at;}

    /**
     * Home's icons, with its places among them (decision 41): the Favourites collection, the archive and the bin, which have
     * no row in the notebook and keep their cells in each device's own settings. A place with no cell yet stands where it
     * always has - {@code first} (the Favourites collection) before everything, the others after everything, where a phone
     * keeps its bin: in the first free cells after the last icon, never in a gap the owner left (seen on a real Home, whose
     * first row was left empty on purpose and was taken by the archive and the bin until this). A place with a cell is put
     * after the things, so a thing let go in that cell while the place was not shown keeps it, and the place takes the next
     * free one.
     *
     * @param ids    the things on Home, in the owner's order
     * @param cells  the cell each was put in, or {@link #NONE}
     * @param places each place shown, by id, with its cell or {@link #NONE}, in the order they take free cells
     * @param first  the place that stands first while it has no cell, or null
     */
    static Map<String,int[]> home(List<String> ids,List<Integer> cells,Map<String,Integer> places,String first,int columns) {
        List<String> all=new ArrayList<>();List<Integer> at=new ArrayList<>();
        Integer firstCell=first==null?null:places.get(first);
        boolean leads=firstCell!=null&&firstCell<0;
        if(leads){all.add(first);at.add(NONE);}
        all.addAll(ids);at.addAll(cells);
        List<String> after=new ArrayList<>();
        for(Map.Entry<String,Integer> one:places.entrySet()) {
            if(leads&&one.getKey().equals(first))continue;
            int cell=one.getValue()==null?NONE:one.getValue();
            if(cell<0){after.add(one.getKey());continue;}
            all.add(one.getKey());at.add(cell);
        }
        Map<String,int[]> drawn=arrange(all,at,columns);
        if(after.isEmpty())return drawn;
        // The places with no cell, after the last icon drawn, in the order given.
        int wide=Math.max(1,Math.min(WIDEST,columns)),last=-1;
        Set<Integer> taken=new HashSet<>();
        for(int[] one:drawn.values())if(one!=null){int index=one[0]*wide+one[1];taken.add(index);last=Math.max(last,index);}
        Map<String,int[]> placed=new LinkedHashMap<>(drawn);
        int next=last+1;
        for(String id:after){next=freeFrom(taken,next);taken.add(next);placed.put(id,new int[]{next/wide,next%wide});}
        return placed;
    }

    /** How many rows a grid drawn this way uses: one past the lowest icon, none for an empty grid. */
    static int rows(Map<String,int[]> drawn) {
        int most=-1;
        for(int[] at:drawn.values())if(at!=null)most=Math.max(most,at[0]);
        return most+1;
    }

    /** What is in a cell of the drawn grid, or null for an empty cell. */
    static String at(Map<String,int[]> drawn,int row,int column) {
        for(Map.Entry<String,int[]> one:drawn.entrySet())if(one.getValue()!=null&&one.getValue()[0]==row&&one.getValue()[1]==column)return one.getKey();
        return null;
    }

    /**
     * The cells to keep when {@code moved} is let go in an empty cell: every icon pinned where it is drawn now, and the
     * moved one in its new cell. Null where that cell is not empty - letting go on an icon is not a move to a place but
     * something else (a new collection of the two, or into it; see {@link Grid}).
     */
    static Map<String,Integer> moveTo(Map<String,int[]> drawn,String moved,int row,int column) {
        if(row<0||column<0||column>=WIDEST)return null;
        String there=at(drawn,row,column);
        if(there!=null&&!there.equals(moved))return null;
        Map<String,Integer> kept=new LinkedHashMap<>();
        for(Map.Entry<String,int[]> one:drawn.entrySet())
            if(one.getValue()!=null)kept.put(one.getKey(),cell(one.getValue()[0],one.getValue()[1]));
        kept.put(moved,cell(row,column));
        return kept;
    }

    // ---- Home's pages, in every direction (docs/HOME.md, decisions 45-50) ----------------------------------------------

    /**
     * A page of Home, as one number kept beside a cell: {@link #NO_PAGE} for a cell from before pages - one long grid -
     * else its place across and down from the centre page, each from -{@link #MIDDLE} to {@link #MIDDLE}-1.
     */
    static final int NO_PAGE=0,SPAN=512,MIDDLE=256;
    static int page(int x,int y){return 1+(clampAt(x)+MIDDLE)*SPAN+(clampAt(y)+MIDDLE);}
    static int pageX(int page){return page<=NO_PAGE?0:(page-1)/SPAN-MIDDLE;}
    static int pageY(int page){return page<=NO_PAGE?0:(page-1)%SPAN-MIDDLE;}
    private static int clampAt(int at){return Math.max(-MIDDLE,Math.min(MIDDLE-1,at));}
    /** The centre page: where Home opens, and where the favourites and the search are. */
    static final int CENTRE=page(0,0);

    /** Where an icon stands on Home: its page, across and down from the centre one, and its cell on that page. */
    record Spot(int x,int y,int row,int column) {
        int page(){return Layout.page(x,y);}
        int cell(){return Layout.cell(row,column);}
        boolean on(int pageX,int pageY){return x==pageX&&y==pageY;}
    }

    /**
     * Where each of Home's icons stands, on pages {@code columns} by {@code rows} (decisions 46, 48, 50), in the order
     * given:
     * <ul>
     * <li>where it was put, if that page is as big as the cell;
     * <li>a cell kept before pages - one long grid - on the centre page while it fits, its rows past the page on the pages
     *     below, so a long Home becomes a column of pages;
     * <li>one whose cell the page is too small for now - a narrower window - or another got first: the next free cell on
     *     its own page after where it was, else before it, else the pages below; nothing is written, so a bigger page
     *     puts it back;
     * <li>one with no cell yet: the first free cell of the centre page, then of the pages below - and one of {@code
     *     after} (the archive, the bin) after the last icon of the centre page instead, never in a gap.
     * </ul>
     */
    static Map<String,Spot> pages(List<String> ids,List<Integer> pages,List<Integer> cells,Set<String> after,int columns,int rows) {
        int wide=Math.max(1,Math.min(WIDEST,columns)),high=Math.max(1,rows);
        Map<String,Spot> placed=new java.util.HashMap<>();Set<Long> taken=new HashSet<>();
        Map<Integer,Spot> wanted=new LinkedHashMap<>();
        for(int at=0;at<ids.size();at++) {
            int cell=cells.get(at)==null?NONE:cells.get(at);
            if(cell<0)continue;
            int page=pages.get(at)==null?NO_PAGE:pages.get(at);
            Spot want=page==NO_PAGE?new Spot(0,row(cell)/high,row(cell)%high,column(cell))
                :new Spot(pageX(page),pageY(page),row(cell),column(cell));
            if(want.column<wide&&want.row<high&&taken.add(key(want,wide)))placed.put(ids.get(at),want);
            else wanted.put(at,want);
        }
        for(Map.Entry<Integer,Spot> one:wanted.entrySet()) {
            Spot want=one.getValue();
            int from=Math.min(want.row,high-1)*wide+Math.min(want.column,wide-1);
            Spot got=free(taken,want.x,want.y,from,wide,high,true);
            taken.add(key(got,wide));placed.put(ids.get(one.getKey()),got);
        }
        List<Integer> trailing=new ArrayList<>();
        for(int at=0;at<ids.size();at++) {
            int cell=cells.get(at)==null?NONE:cells.get(at);
            if(cell>=0)continue;
            if(after!=null&&after.contains(ids.get(at))){trailing.add(at);continue;}
            Spot got=free(taken,0,0,0,wide,high,false);
            taken.add(key(got,wide));placed.put(ids.get(at),got);
        }
        if(!trailing.isEmpty()) {
            int last=-1;
            for(Spot one:placed.values())if(one.on(0,0))last=Math.max(last,one.row*wide+one.column);
            for(int at:trailing) {
                // Never into a gap before the last icon: past the end of the centre page, the pages below.
                Spot got=free(taken,0,0,last+1,wide,high,false);
                taken.add(key(got,wide));placed.put(ids.get(at),got);
                if(got.on(0,0))last=got.row*wide+got.column;
            }
        }
        // In the order the things were given in, whatever order they were placed in.
        Map<String,Spot> ordered=new LinkedHashMap<>();
        for(String id:ids)ordered.put(id,placed.get(id));
        return ordered;
    }

    private static long key(Spot at,int wide){return ((long)at.page()<<20)+at.row*(long)wide+at.column;}

    /**
     * The first free cell of page (x, y) from {@code from} in reading order; with {@code round}, then from its start up to
     * {@code from}; then the first free cell of each page below it.
     */
    private static Spot free(Set<Long> taken,int x,int y,int from,int wide,int high,boolean round) {
        for(int at=Math.max(0,from);at<wide*high;at++){Spot one=new Spot(x,y,at/wide,at%wide);if(!taken.contains(key(one,wide)))return one;}
        if(round)for(int at=0;at<Math.min(from,wide*high);at++){Spot one=new Spot(x,y,at/wide,at%wide);if(!taken.contains(key(one,wide)))return one;}
        for(int down=y+1;down<MIDDLE;down++)
            for(int at=0;at<wide*high;at++){Spot one=new Spot(x,down,at/wide,at%wide);if(!taken.contains(key(one,wide)))return one;}
        return new Spot(x,MIDDLE-1,0,0);
    }

    /**
     * The first free cell of page (x, y), else of the pages below it: where something made with + on that page stands
     * (decision 46: what + makes takes the first free cell of the page in view).
     */
    static Spot freeOn(Map<String,Spot> drawn,int x,int y,int columns,int rows) {
        int wide=Math.max(1,Math.min(WIDEST,columns)),high=Math.max(1,rows);
        Set<Long> taken=new HashSet<>();
        for(Spot one:drawn.values())if(one!=null)taken.add(key(one,wide));
        return free(taken,x,y,0,wide,high,false);
    }

    /** The pages something stands on, as {x, y}, and the centre page always: the pages there are (decision 46). */
    static Set<List<Integer>> active(Map<String,Spot> drawn) {
        Set<List<Integer>> pages=new java.util.LinkedHashSet<>();
        pages.add(List.of(0,0));
        for(Spot one:drawn.values())if(one!=null)pages.add(List.of(one.x,one.y));
        return pages;
    }

    /**
     * The pages the dots under Home's grid stand for: every page there is, and the one in view even while nothing is on
     * it yet - a thing carried onto a new page shows that page at once, not only once it is let go there.
     */
    static Set<List<Integer>> dotted(Map<String,Spot> drawn,int x,int y) {
        Set<List<Integer>> pages=active(drawn);
        pages.add(List.of(x,y));
        return pages;
    }

    /**
     * The page a swipe or a turn of the wheel goes to from page (x, y), that way ({@code dx}, {@code dy} each -1, 0 or 1):
     * the nearest page there is in that line; null where there is none, and the page stays.
     */
    static int[] next(Set<List<Integer>> active,int x,int y,int dx,int dy) {
        for(int step=1;step<SPAN;step++) {
            int nx=x+dx*step,ny=y+dy*step;
            if(nx<-MIDDLE||nx>=MIDDLE||ny<-MIDDLE||ny>=MIDDLE)return null;
            if(active.contains(List.of(nx,ny)))return new int[]{nx,ny};
        }
        return null;
    }

    /**
     * The spots to keep when {@code moved} is let go in an empty cell of a page: every icon pinned where it stands now, on
     * whatever page, and the moved one in its new place. Null where that cell is taken by another.
     */
    static Map<String,Spot> moveTo(Map<String,Spot> drawn,String moved,Spot to) {
        for(Map.Entry<String,Spot> one:drawn.entrySet())
            if(one.getValue()!=null&&!one.getKey().equals(moved)&&one.getValue().equals(to))return null;
        Map<String,Spot> kept=new LinkedHashMap<>(drawn);
        kept.put(moved,to);
        return kept;
    }

    /**
     * A whole page carried to another place, zoomed out (the owner's ask, 2026-10-01: "grab and move the pages, just as
     * with the other assets"): everything on page (fx, fy) goes to (tx, ty), each in the same cell, and whatever stood on
     * (tx, ty) goes to where the carried page was - the two change places; onto an empty place, the page simply moves
     * there. The main page's place stays the main one: carried onto it, a page's icons are the main page's from then on.
     */
    static Map<String,Spot> movePage(Map<String,Spot> drawn,int fx,int fy,int tx,int ty) {
        Map<String,Spot> moved=new LinkedHashMap<>();
        for(Map.Entry<String,Spot> one:drawn.entrySet()) {
            Spot at=one.getValue();
            if(at!=null&&at.on(fx,fy))at=new Spot(tx,ty,at.row,at.column);
            else if(at!=null&&at.on(tx,ty))at=new Spot(fx,fy,at.row,at.column);
            moved.put(one.getKey(),at);
        }
        return moved;
    }

    /** What stands in a cell of a page, or null for an empty one. */
    static String at(Map<String,Spot> drawn,int x,int y,int row,int column) {
        for(Map.Entry<String,Spot> one:drawn.entrySet()) {
            Spot at=one.getValue();
            if(at!=null&&at.x==x&&at.y==y&&at.row==row&&at.column==column)return one.getKey();
        }
        return null;
    }

    /** The cell under a point, in a grid of cells this big starting at the origin; null outside the grid's columns. */
    static int[] under(float x,float y,float cellWidth,float cellHeight,int columns) {
        if(x<0||y<0||cellWidth<=0||cellHeight<=0)return null;
        int column=(int)(x/cellWidth),row=(int)(y/cellHeight);
        return column<columns?new int[]{row,column}:null;
    }
}
