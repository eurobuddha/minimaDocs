// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** What the phone decides about a thing's look without a screen (docs/HOME.md, step 4). */
public class LooksTest {
    @Test public void aPictureWinsThenAnIconThenTheDefault() {
        assertEquals(Looks.Shows.PICTURE,Looks.shows(true,"calendar",true,true));
        assertEquals(Looks.Shows.PICTURE,Looks.shows(false,"",false,true));
        assertEquals(Looks.Shows.GLYPH,Looks.shows(false,"calendar",true,false));
        assertEquals(Looks.Shows.GLYPH,Looks.shows(true,"calendar",true,false));
        // With none of its own, a note shows the note glyph and a collection its mini-grid.
        assertEquals(Looks.Shows.GLYPH,Looks.shows(true,"",false,false));
        assertEquals(Looks.Shows.GRID,Looks.shows(false,"",false,false));
        assertEquals(Looks.Shows.GRID,Looks.shows(false,null,false,false));
    }

    @Test public void anIconThisBuildDoesNotKnowIsDrawnAsTheDefault() {
        // A name from a later set: a collection shows its mini-grid, a note the note glyph.
        assertEquals(Looks.Shows.GRID,Looks.shows(false,"from-a-later-set",false,false));
        assertEquals(Looks.Shows.GLYPH,Looks.shows(true,"from-a-later-set",false,false));
        assertEquals(Icons.NOTE,Looks.glyph("from-a-later-set",false));
        assertEquals(Icons.NOTE,Looks.glyph("",false));
        assertEquals(Icons.NOTE,Looks.glyph(null,false));
        assertEquals("calendar",Looks.glyph("calendar",true));
        // And the default glyph is one the set has.
        assertTrue(Icons.known(Looks.glyph("",false)));
    }

    @Test public void theGlyphIsInTheColourItselfOrThePapersInk() {
        int ink=0xFF222222;
        assertEquals(ink,Looks.ink(Tint.NONE,false,ink));
        assertEquals(ink,Looks.ink(99,false,ink));
        assertEquals(Tint.of(1,false),Looks.ink(1,false,ink));
        assertEquals(Tint.of(1,true),Looks.ink(1,true,ink));
    }

    @Test public void cornersAreTheTilesOwnButASmallFaceIsNeverADisc() {
        float most=42f;
        // Home's faces keep the tiles' corner.
        assertEquals(most,Looks.corner(300f,most),0.01f);
        // A small one keeps its proportion instead.
        assertEquals(28f*0.22f,Looks.corner(28f,most),0.01f);
        assertTrue(Looks.corner(28f,most)<28f/2f);
        assertEquals(0f,Looks.corner(0f,most),0f);
    }

    @Test public void aSmallFacesGlyphTakesMoreOfIt() {
        assertEquals(64f*0.56f,Looks.glyphSide(64f,40f),0.01f);
        assertEquals(28f*0.66f,Looks.glyphSide(28f,40f),0.01f);
        assertTrue(Looks.glyphSide(28f,40f)/28f>Looks.glyphSide(64f,40f)/64f);
    }

    @Test public void aPictureIsReadSmallButNeverSmallerThanItsSquare() {
        // A phone's photo, 4000 by 3000: its square of 3000 read at an eighth is 375, a sixteenth would be 187.
        assertEquals(8,Looks.sample(4000,3000,Thumb.SIDE));
        assertTrue(3000/Looks.sample(4000,3000,Thumb.SIDE)>=Thumb.SIDE);
        assertTrue(3000/(Looks.sample(4000,3000,Thumb.SIDE)*2)<Thumb.SIDE);
        // Upright the same.
        assertEquals(8,Looks.sample(3000,4000,Thumb.SIDE));
        // Already small, or exactly twice the side: read whole, or at a half.
        assertEquals(1,Looks.sample(300,200,Thumb.SIDE));
        assertEquals(1,Looks.sample(100,100,Thumb.SIDE));
        assertEquals(2,Looks.sample(384,384,Thumb.SIDE));
        // Nothing to read.
        assertEquals(1,Looks.sample(0,300,Thumb.SIDE));
        assertEquals(1,Looks.sample(300,-1,Thumb.SIDE));
    }

    @Test public void theFirstEncodingThatFitsIsKeptAndAFailedOneIsPassedOver() {
        List<Thumb.Try> tries=Thumb.tries(1000);
        final List<Integer> asked=new ArrayList<>();
        // Too big at the full side and first quality, failing at the second, fitting at the third.
        byte[] kept=Looks.firstFit(tries,one->{
            asked.add(asked.size());
            if(asked.size()==1)return new byte[Thumb.MOST+1];
            if(asked.size()==2)throw new IllegalStateException("the encoder gave up");
            return new byte[]{1,2,3};
        });
        assertArrayEquals(new byte[]{1,2,3},kept);
        assertEquals(3,asked.size());
        // Nothing fits: nothing kept, every way tried.
        asked.clear();
        assertNull(Looks.firstFit(tries,one->{asked.add(0);return new byte[Thumb.MOST+1];}));
        assertEquals(tries.size(),asked.size());
        assertNull(Looks.firstFit(tries,one->null));
        assertNull(Looks.firstFit(tries,one->new byte[0]));
        assertNull(Looks.firstFit(null,one->new byte[]{1}));
    }

    @Test public void thePickerListsTheWholeSetInOrderOrWhatTheWordsFind() {
        List<String> all=Looks.found("");
        assertEquals(Icons.all().size(),all.size());
        assertEquals(new ArrayList<>(Icons.all().keySet()),all);
        assertEquals(all,Looks.found("   "));
        assertEquals(all,Looks.found(null));
        // Words find their icon first, and never more than a screenful of screens.
        List<String> notes=Looks.found("sticky note");
        assertFalse(notes.isEmpty());
        assertEquals(Icons.NOTE,notes.get(0));
        assertTrue(Looks.found("a").size()<=Looks.FOUND_MOST);
        assertTrue(Looks.found("zzzz-nothing-is-called-this").isEmpty());
    }

    @Test public void theGridHasDefaultFirstUntilSomethingIsTyped() {
        List<String> all=Looks.shown("");
        assertEquals(Looks.DEFAULT,all.get(0));
        assertEquals(Icons.all().size()+1,all.size());
        assertEquals(Looks.found(""),all.subList(1,all.size()));
        assertEquals(all,Looks.shown(null));
        // Words look for an icon of the set, and Default is not one.
        List<String> found=Looks.shown("sticky note");
        assertFalse(found.contains(Looks.DEFAULT));
        assertEquals(Looks.found("sticky note"),found);
        assertTrue(Looks.shown("zzzz-nothing-is-called-this").isEmpty());
    }

    @Test public void anIconIsSpokenAsItsWords() {
        assertEquals("sticky note",Looks.spoken("sticky-note"));
        assertEquals("calendar",Looks.spoken("calendar"));
        assertEquals("",Looks.spoken(null));
    }

    @Test public void aBoxWhereEverythingWaitsForAnUpdateHasNothingToSend() {
        String one=Unsent.needsUpdate("Ana's phone")+".";
        assertTrue(Looks.onlyUpdates(one));
        // As Waits.said joins them: one line a device, each ending with a full stop.
        assertTrue(Looks.onlyUpdates(Unsent.needsUpdate("Ana's phone")+".\n"+Unsent.needsUpdate("Old PC")+"."));
        // Anything else among the lines, and sending does something.
        assertFalse(Looks.onlyUpdates(Unsent.needsUpdate("Ana's phone")+".\nWaiting to reach Bea with your changes."));
        assertFalse(Looks.onlyUpdates("Waiting to reach Bea with your changes."));
        assertFalse(Looks.onlyUpdates(Waits.UNSAVED));
        assertFalse(Looks.onlyUpdates(""));
        assertFalse(Looks.onlyUpdates(null));
        // A device with no name is still a device: the words say "A device".
        assertTrue(Looks.onlyUpdates(Unsent.needsUpdate("")+"."));
    }

    @Test public void theBoxTheMarkOpensIsReadAsItIsWritten() {
        // What the box says, as the shared code writes it: one device from before trees, and nothing else.
        String alone=Waits.said(List.of(new Waits.Device("Old phone",Waits.needsUpdate(),0)),1);
        assertEquals("Old phone needs to update Mininotes to receive this.",alone);
        assertTrue(Looks.onlyUpdates(alone));
        // Beside somebody who is simply owed the change, there is something to send.
        String both=Waits.said(List.of(new Waits.Device("Old phone",Waits.needsUpdate(),0),
            new Waits.Device("Bea",new Waits.Where(1,0,0,false,false),0)),1);
        assertTrue(both,both.startsWith("Old phone needs to update Mininotes to receive this."));
        assertFalse(Looks.onlyUpdates(both));
    }
}
