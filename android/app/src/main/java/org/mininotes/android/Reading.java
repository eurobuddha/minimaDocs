// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

/**
 * The size one note is read at. The reading ladder has ten rungs on both apps; a note can keep a rung of its
 * own, and one that has none is read at the device's, which is the size new notes open at. Kept where its
 * colour is kept, and like its colour it stays on this device: a phone and a PC are read at different sizes,
 * so what is stored is which rung, and it never travels. Holds no Android types, so the rules are unit tested
 * without a device.
 */
final class Reading {
    /** No size of its own: the note is read at the device's. */
    static final int NONE=-1;

    /** The rungs of the ladder, the same ten on the phone and the PC. */
    static final int RUNGS=10;

    /** Whether a stored number still names a rung, so an unknown one is simply no size of its own. */
    static boolean known(int rung){return rung>=0&&rung<RUNGS;}

    /** A stored number as the note's own rung, or none. */
    static int stored(long rung){return rung>=0&&rung<RUNGS?(int)rung:NONE;}

    /** A rung kept on the ladder, whatever was asked for. */
    static int clamp(int rung){return Math.max(0,Math.min(RUNGS-1,rung));}

    /** The rung a note is read at: its own if it has one, the device's if not. */
    static int of(int own,int device){return known(own)?own:clamp(device);}

    /**
     * One rung up or down from where the note is read now, as its own rung. At either end of the ladder there
     * is nowhere to go, and the answer is {@link #NONE} rather than a rung that would change nothing.
     */
    static int step(int own,int device,int by) {
        int at=of(own,device),to=at+by;
        return to<0||to>=RUNGS?NONE:to;
    }

    /** Whether the note follows the device's size - "Same as other notes". */
    static boolean followsDevice(int own){return !known(own);}

    private Reading(){}
}
