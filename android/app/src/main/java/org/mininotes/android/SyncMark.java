// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.util.Collection;

/**
 * Where a thing stands with the devices it reaches, as one of six marks: the same six on the phone and the PC,
 * on the line under a note's title, on tiles and cards, and on the lines of the tree. Which one is worked out
 * here and nowhere else, so the two apps cannot come to say different things; each only draws it.
 *
 * <p>The line under a title used to say it in words beside a mark - "Shared with Ana · not sent yet – send now"
 * - and the mark was green while the words said nothing had gone. Now the mark is the whole of it, and its
 * colour means what its drawing means: grey is nothing to do, amber is on its way, green is done, red is wrong.
 *
 * <p>Holds no Android or Swing types, so the rules are unit tested without either.
 */
enum SyncMark {
    /** An empty ring, grey: only on this device. */
    HERE,
    /** An arrow going up, amber: changes waiting to go - words not sent, files still going. */
    WAITING,
    /** Three dots, amber: sent, and waiting for the others to say they have it. */
    SENT,
    /** A tick, green: everybody has this version, files and all. */
    GONE,
    /** Two bars, grey: this device has stopped taking it in. */
    PAUSED,
    /** An exclamation mark, red: a device it is owed to has not been heard from in a long time. */
    STUCK;

    /**
     * How long something can be owed to a device that says nothing before it is worth a red mark. Three days: a
     * phone in a drawer over a weekend is ordinary, and a note that has not gone for longer than that has
     * stopped being "on its way".
     */
    static final long LONG=3L*24*60*60*1000;

    /** The four colours a mark comes in. Grey and green are each app's own; amber and red are below. */
    enum Ink { QUIET, AMBER, GREEN, RED }

    Ink ink() {
        return switch(this) {
            case HERE, PAUSED -> Ink.QUIET;
            case WAITING, SENT -> Ink.AMBER;
            case GONE -> Ink.GREEN;
            case STUCK -> Ink.RED;
        };
    }

    /**
     * Amber and red, for light paper and for dark. Each is at least three to one against every paper either app
     * draws on (the least a drawing needs to be told from its ground), and the marks sit on a round of the paper,
     * so a note washed in orange or red does not swallow them.
     */
    static final int AMBER=0xFFA35A00,AMBER_ON_DARK=0xFFF2B042,RED=0xFFB3261E,RED_ON_DARK=0xFFFF8A80;

    /** The colour to draw it in, given the app's own grey and green for the paper in use. */
    int colour(boolean darkPaper,int quiet,int green) {
        return switch(ink()) {
            case QUIET -> quiet;
            case GREEN -> green;
            case AMBER -> darkPaper?AMBER_ON_DARK:AMBER;
            case RED -> darkPaper?RED_ON_DARK:RED;
        };
    }

    /** Whether it is filled, the drawing cut out of it: the two that ask for something, going and gone wrong. */
    boolean filled(){return this==WAITING||this==STUCK;}

    /** One short line for Settings, What the marks mean. */
    String meaning() {
        return switch(this) {
            case HERE -> "Only on this device. Not shared.";
            case WAITING -> "Changes waiting to go. Press it to see what waits, and send them now.";
            case SENT -> "Sent. Waiting for the others to say they have it.";
            case GONE -> "Everybody has this version, files included.";
            case PAUSED -> "Paused. This device is not taking in their changes.";
            case STUCK -> "A device has not been heard from for more than three days while something waits for it.";
        };
    }

    /** What the mark says, and what pressing it does, for a screen reader: no tooltip shows it. */
    String said(String device) {
        return switch(this) {
            case HERE -> "Only on "+device+". Opens sharing.";
            case WAITING -> "Changes waiting to go. Says what waits, and sends them.";
            case SENT -> "Sent, waiting for the others to confirm. Opens who has it.";
            case GONE -> "Everybody has this version. Opens who has it.";
            case PAUSED -> "Paused, not taking in changes. Opens who has it.";
            case STUCK -> "A device has not been reachable for a long time. Opens who has it.";
        };
    }

    /** Where one person stands, said in the box a tap on their round opens. */
    String person() {
        return switch(this) {
            case WAITING -> "Your changes have not gone to them yet.";
            case SENT -> "Sent. Waiting for them to say they have it.";
            case GONE -> "Has this version.";
            case STUCK -> "Not heard from for more than three days. What you changed is waiting for them.";
            case HERE, PAUSED -> "";
        };
    }

    /**
     * Where one device stands with one note.
     *
     * @param owed a newer version is owed to it than it has said it has
     * @param filesGoing one of the note's files that can go has not reached it yet
     * @param confirmed it has said it has this version, and that it matches this one
     * @param stuck owed, and not heard from for too long: see {@link #stuck}
     */
    static SyncMark person(boolean owed,boolean filesGoing,boolean confirmed,boolean stuck) {
        if(stuck)return STUCK;
        if(owed||filesGoing)return WAITING;
        return confirmed?GONE:SENT;
    }

    /**
     * Owed to a device for longer than {@link #LONG}, and nothing heard from it since the change was made: the
     * only evidence there is that something has gone wrong rather than being slow. A device that has answered
     * about anything since then is there, and the note is only on its way to it.
     *
     * @param since when the change that is owed was made
     * @param heard the last time the device said it had anything; 0 for never
     */
    static boolean stuck(long since,long heard,long now) {
        return since>0&&now-since>LONG&&heard<since;
    }

    /** The one that matters most among several: what is wrong, then what is waiting, then what is on its way. */
    static SyncMark worst(Collection<SyncMark> marks) {
        SyncMark worst=null;
        for(SyncMark one:marks)if(one!=null&&(worst==null||one.weight()>worst.weight()))worst=one;
        return worst;
    }
    private int weight() {
        return switch(this) {
            case GONE -> 1;
            case SENT -> 2;
            case WAITING -> 3;
            case STUCK -> 4;
            case HERE, PAUSED -> 0;
        };
    }

    /**
     * The mark a thing wears: a note, a book, a collection.
     *
     * @param paused this device has stopped taking it in, which outranks the rest: it is the one the person
     *               set, and the one they may have forgotten
     * @param shared it reaches anybody at all
     * @param unsaved there are words on the screen the notebook has not had yet; they are waiting whatever it says
     * @param people the worst of where it stands with each device it reaches ({@link #worst}), or null where
     *               nobody is known to be owed or to have it
     */
    static SyncMark of(boolean paused,boolean shared,boolean unsaved,SyncMark people) {
        if(paused)return PAUSED;
        if(!shared)return HERE;
        if(people==STUCK)return STUCK;
        if(unsaved)return WAITING;
        // Only a device that is locked is grey among the people (see Waits.Where#mark): it holds nothing back, and a
        // thing is not "paused" because somebody else's notebook is shut.
        return people==null||people==PAUSED?GONE:people;
    }
}
