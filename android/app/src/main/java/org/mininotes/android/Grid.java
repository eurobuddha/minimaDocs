// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

/**
 * What Home's grid, its pop-ups and its dock do with a finger, worked out without a screen (docs/HOME.md, step 2): how
 * many icons go across, what letting one thing go on another does, which place in the dock a drop takes, where among
 * the others a dragged icon may stand, what letting go over a cell does and where files from another app go (step 5),
 * and when a finger going up is a swipe. HomeScreen measures; this decides, so
 * the rules are unit tested rather than only ever seen on a device.
 *
 * <p>Holds no Android types.
 */
final class Grid {
    /** Narrower than this, in dp, and four icons are too tight to read their names: three go across instead. */
    static final float NARROW=340f;
    /** How wide one icon's column is at the least, in dp, once the screen is wide enough for four. */
    static final float COLUMN=110f;
    /** The most icons across, however wide the screen: past this a grid is a list read sideways. */
    static final int WIDEST=8;

    private Grid(){}

    /**
     * How many icons go across a width, in dp: four on a phone held upright, as a phone's own home screen has
     * (decision 18); three where that is narrower than {@link #NARROW}; and more on a screen turned sideways or a
     * tablet, one for every {@link #COLUMN}, up to {@link #WIDEST}.
     */
    static int columns(float widthDp) {
        if(!(widthDp>=NARROW))return 3;
        return Math.max(4,Math.min(WIDEST,(int)(widthDp/COLUMN)));
    }

    /** What letting a dragged thing go on the middle of another does. */
    enum Onto {
        /** Two notes: a new collection, Untitled, holding both, where the one let go on was. */
        MERGE,
        /** Into it: anything into a collection, or a file into a note, whose attachment it then is. */
        INTO,
        /**
         * Put away, as its menu puts it away (decision 41): a note or a collection let go on the Archive is archived, on the
         * Bin it goes in the bin; a file let go on the Bin is deleted, after the one question its menu asks.
         */
        AWAY,
        /** Nothing of its own: what is let go there is not put into it (on the phone's grid it goes back; see {@link #letGo}). */
        NONE
    }

    /**
     * What a thing of one kind let go on a thing of another does. Only notes, collections and files are moved about;
     * of the places, the archive and the bin take what their menus would put in them, and the Favourites collection takes
     * nothing.
     */
    static Onto onto(NoteStore.Branch.Kind dragged,NoteStore.Branch.Kind target) {
        if(!moves(dragged)||target==null)return Onto.NONE;
        // A file has no archive: it is kept, or it is deleted.
        if(target==NoteStore.Branch.Kind.BIN)return Onto.AWAY;
        if(target==NoteStore.Branch.Kind.ARCHIVE)return docks(dragged)?Onto.AWAY:Onto.NONE;
        if(dragged==NoteStore.Branch.Kind.PAGE&&target==NoteStore.Branch.Kind.PAGE)return Onto.MERGE;
        if(target==NoteStore.Branch.Kind.COLLECTION)return Onto.INTO;
        if(dragged==NoteStore.Branch.Kind.FILE&&target==NoteStore.Branch.Kind.PAGE)return Onto.INTO;
        return Onto.NONE;
    }

    /** Whether a thing is one that is picked up and moved on the grid: a note, a collection or a file. */
    static boolean moves(NoteStore.Branch.Kind kind) {
        return kind==NoteStore.Branch.Kind.PAGE||kind==NoteStore.Branch.Kind.COLLECTION||kind==NoteStore.Branch.Kind.FILE;
    }

    /**
     * Whether an icon is one of Home's places rather than a thing: the Favourites collection, the archive or the bin
     * (decision 41). It has no row in the notebook, holds nothing that is carried about, and keeps a cell of its own on Home.
     */
    static boolean place(NoteStore.Branch.Kind kind) {
        return kind==NoteStore.Branch.Kind.FAVOURITES||kind==NoteStore.Branch.Kind.ARCHIVE||kind==NoteStore.Branch.Kind.BIN;
    }

    /**
     * Whether an icon is picked up and carried: a thing, anywhere it is drawn; a place, to another empty cell of Home and
     * nowhere else, as every icon on a phone's home screen is moved (decision 42).
     */
    static boolean carried(NoteStore.Branch.Kind kind){return moves(kind)||place(kind);}

    /** Whether a thing can be a favourite, and so go in the dock: a note or a collection. A file is neither. */
    static boolean docks(NoteStore.Branch.Kind kind) {
        return kind==NoteStore.Branch.Kind.PAGE||kind==NoteStore.Branch.Kind.COLLECTION;
    }

    /**
     * The place in the dock, 1 the first, that a thing let go at {@code x} takes: one past every icon whose middle it
     * has passed. The dragged thing's own icon is not counted where it came from the dock, so the place is counted as
     * the dock would show it without it - which is how the notebook counts it (see NoteStore.toDock).
     *
     * @param middles the middle of each icon in the dock, left to right
     * @param dragged which of them is the one being dragged, or -1 for a thing brought from outside the dock
     */
    static int dockSlot(float x,float[] middles,int dragged) {
        int passed=0;
        for(int at=0;at<middles.length;at++)if(at!=dragged&&x>middles[at])passed++;
        return passed+1;
    }

    /**
     * Where among a grid's icons a dragged one may stand. The grid shows collections and notes in one order and the
     * files after them (see NoteStore.contents), so a note or a collection stays among the first {@code things}, and
     * a file among the rest: a file dragged in among the notes would only jump back once the grid is read again.
     *
     * @param want   the place the finger asks for, counted among every icon that moves
     * @param things how many of them are collections and notes, the dragged one included if it is one
     * @param total  how many icons move in all
     */
    static int within(int want,int things,int total,boolean file) {
        int first=file?things:0,last=file?total-1:things-1;
        if(last<first)return Math.max(0,Math.min(want,total-1));
        return Math.max(first,Math.min(want,last));
    }

    /**
     * Whether a finger has gone up far enough, and more up than across, to be a swipe up: what opens the overview
     * from the dock, as a phone's own gesture bar does. {@code dy} is negative going up.
     */
    static boolean swipedUp(float dx,float dy,float far) {
        return -dy>=far&&Math.abs(dx)< -dy;
    }

    /** Whether a card lifted this far, of its height, has been thrown away rather than let fall back. */
    static boolean thrownUp(float dy,float height) {
        return height>0&&-dy>height*0.25f;
    }

    /** Where a dragged thing is over, as far as what a drop does goes. */
    enum Zone {
        /** The dock: a favourite, at the place it is let go. */
        DOCK,
        /** The open pop-up's card: its grid, for another place in its order or a thing to go into. */
        CARD,
        /** The dimmed grid around an open pop-up: one level up, out of it. */
        UP,
        /** Home's grid, with no pop-up over it. */
        HOME,
        /** Anywhere else - the bar, the search bar: nothing happens there. */
        NONE
    }

    /** Which zone a point is in, from what it is over; the dock first, since it is never under the pop-up. */
    static Zone zone(boolean overDock,boolean cardOpen,boolean overCard,boolean overDesk) {
        if(overDock)return Zone.DOCK;
        if(cardOpen){if(overCard)return Zone.CARD;return overDesk?Zone.UP:Zone.NONE;}
        return overDesk?Zone.HOME:Zone.NONE;
    }

    // ---- icons where they are put (docs/HOME.md, decision 39) ----------------------------------------------------------

    /** What letting a carried icon go over one cell of the grid it came out of does. */
    enum LetGo {
        /** An empty cell: it stays there, and every other icon stays where it is drawn (see Layout.moveTo). */
        PLACE,
        /** A note on a note: a new collection, Untitled, holding both, where the one let go on was. */
        MERGE,
        /** Into what is there: anything into a collection, a file into a note. */
        INTO,
        /** Put away by the place it is let go on: the archive or the bin (see {@link Onto#AWAY}). */
        AWAY,
        /** Its own cell, or something that takes nothing from it - a file, the Favourites collection: nothing moves. */
        BACK
    }

    /**
     * What letting a thing go over a cell does, as a phone's own home screen has it: in an empty cell it is put there, and
     * stays; over another icon, what {@link #onto} says, and where that is nothing, it goes back where it was - an icon is
     * never pushed out of its cell to make room. A place goes to an empty cell or back: it goes into nothing.
     *
     * @param there  what is in the cell, or null for an empty one
     * @param itself whether what is in the cell is the carried thing itself
     */
    static LetGo letGo(NoteStore.Branch.Kind carried,NoteStore.Branch.Kind there,boolean itself) {
        if(!carried(carried)||itself)return LetGo.BACK;
        if(there==null)return LetGo.PLACE;
        if(place(carried))return LetGo.BACK;
        switch(onto(carried,there)) {
            case MERGE: return LetGo.MERGE;
            case INTO: return LetGo.INTO;
            case AWAY: return LetGo.AWAY;
            default: return LetGo.BACK;
        }
    }

    /** What files dragged in from another app are kept with, by what they are let go over. */
    enum Files {
        /** A note's icon: kept with the note, as dropped on its page. */
        NOTE,
        /** A collection's icon: kept with that collection. */
        COLLECTION,
        /** An empty cell, a file, the Favourites collection, anywhere else: with what the grid shows - Home, or the card's collection. */
        HERE
    }

    /** Files let go over an icon of a kind, or over no icon (null), as the PC aims what Explorer drops (DesktopDrops.aim). */
    static Files filesOnto(NoteStore.Branch.Kind there) {
        if(there==NoteStore.Branch.Kind.PAGE)return Files.NOTE;
        if(there==NoteStore.Branch.Kind.COLLECTION)return Files.COLLECTION;
        return Files.HERE;
    }
}
