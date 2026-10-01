// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

/** The words on the line under a note's title, the same on the phone and the PC. See {@link NoteLine}. */
public class NoteLineTest {
    @Test public void goingStaysAndTheEndFades() {
        assertTrue(NoteLine.stays(NoteLine.SAVING));
        assertTrue(NoteLine.stays(NoteLine.sending(List.of("Ana"))));
        assertFalse(NoteLine.stays(NoteLine.sent(List.of("Ana"))));
        assertFalse(NoteLine.stays(Unsent.linking("Graphene")));
        assertFalse(NoteLine.stays(null));
    }

    @Test public void sayWhoByName() {
        assertEquals("Sending to Ana…",NoteLine.sending(List.of("Ana")));
        assertEquals("Sending…",NoteLine.sending(List.of()));
        assertEquals("Sent to Ana, waiting for them to confirm",NoteLine.sent(List.of("Ana")));
        assertEquals("Sent to Ana and Bo, waiting for them all to confirm",NoteLine.sent(List.of("Ana","Bo")));
        assertEquals("Sent to Ana and 2 others, waiting for them all to confirm",NoteLine.sent(List.of("Ana","Bo","Cy")));
        assertEquals("Sent, waiting for them to confirm",NoteLine.sent(List.of()));
    }

    @Test public void whatCouldNotGoPointsAtWhy() {
        Unsent.Problem ana=new Unsent.Problem(Unsent.Why.NOT_REACHED,"Ana","Shopping","","");
        assertEquals("Could not reach Ana - see why",NoteLine.couldNot(List.of(ana),0));
        assertEquals(NoteLine.Tone.FAILED,NoteLine.couldNotTone(List.of(ana)));
        assertEquals("Some of it went - see why",NoteLine.couldNot(List.of(ana),1));
        assertEquals("Could not go - see why",NoteLine.couldNot(List.of(),0));
        Unsent.Problem bo=new Unsent.Problem(Unsent.Why.NOT_REACHED,"Bo","Shopping","","");
        assertEquals("Could not go - see why",NoteLine.couldNot(List.of(ana,bo),0));
        // Being linked with is on its way, not wrong: amber, and said as the round says it.
        Unsent.Problem linking=new Unsent.Problem(Unsent.Why.LINKING,"Graphene","Shopping","","");
        assertEquals("Linking with Graphene… it goes once they answer.",NoteLine.couldNot(List.of(linking),0));
        assertEquals(NoteLine.Tone.GOING,NoteLine.couldNotTone(List.of(linking)));
    }

    @Test public void eachToneWearsItsMarksColour() {
        assertEquals(SyncMark.WAITING,NoteLine.Tone.GOING.mark);
        assertEquals(SyncMark.GONE,NoteLine.Tone.DONE.mark);
        assertEquals(SyncMark.STUCK,NoteLine.Tone.FAILED.mark);
    }
}
