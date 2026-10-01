// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** A picture cut square, small enough to travel, and only ever a picture. */
public class ThumbTest {
    @Test public void theSquareIsCutFromTheMiddle() {
        assertArrayEquals(new int[]{80,0,300},Thumb.square(460,300));
        assertArrayEquals(new int[]{0,50,200},Thumb.square(200,300));
        assertArrayEquals(new int[]{0,0,64},Thumb.square(64,64));
        assertNull(Thumb.square(0,10));
    }

    @Test public void triesGoFromTheFullSideDownAndNeverScaleAPictureUp() {
        List<Thumb.Try> big=Thumb.tries(4000);
        assertEquals(Thumb.SIDE,big.get(0).side);
        assertEquals(85,big.get(0).quality);
        for(int at=1;at<big.size();at++)assertTrue(big.get(at).side<=big.get(at-1).side);
        assertEquals(64,big.get(big.size()-1).side);
        List<Thumb.Try> small=Thumb.tries(100);
        assertEquals(100,small.get(0).side);
        for(Thumb.Try one:small)assertTrue(one.side<=100);
        assertEquals(1,Thumb.tries(0).get(0).side);
    }

    @Test public void onlyAPictureThatFitsIsKept() {
        byte[] png=new byte[40];png[0]=(byte)0x89;png[1]='P';png[2]='N';png[3]='G';
        byte[] webp="RIFF\0\0\0\0WEBPVP8 ".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] jpeg=new byte[20];jpeg[0]=(byte)0xFF;jpeg[1]=(byte)0xD8;jpeg[2]=(byte)0xFF;
        assertEquals("png",Thumb.kind(png));assertEquals("webp",Thumb.kind(webp));assertEquals("jpeg",Thumb.kind(jpeg));
        assertTrue(Thumb.takes(png));
        assertFalse("not a picture",Thumb.takes("<script>alert(1)</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        byte[] huge=new byte[Thumb.MOST+1];huge[0]=(byte)0x89;huge[1]='P';huge[2]='N';huge[3]='G';
        assertFalse("too big to travel",Thumb.takes(huge));
        assertFalse(Thumb.takes(null));assertFalse(Thumb.takes(new byte[0]));
    }
}
