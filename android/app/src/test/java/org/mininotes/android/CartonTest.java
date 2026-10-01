// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** A collection travelling on its own, there and back, and never mistaken for anything else. */
public class CartonTest {
    private static Carton.Sent carton(List<Enclosure.Listed> files) {
        return new Carton.Sent("x-9","Mondays","calendar",new byte[]{9,8,7},4,-3L,
            List.of(new Parcel.Step("c-1","Work","briefcase",3,-10L),new Parcel.Step("b-7","Meetings","",0,5L)),
            true,"THING","x-9",
            List.of(new Parcel.Member("key-a","Mx-a","Ana",Sharing.Level.WRITE.said(),77L,new byte[]{5,5}),
                    new Parcel.Member("key-b","Mx-b","Bea",Sharing.Level.READ.said(),78L)),
            true,files,555L);
    }

    @Test public void thereAndBack() throws IOException {
        Carton.Sent in=Carton.open(Carton.wrap(carton(List.of(new Enclosure.Listed("f-1","plan.pdf","application/pdf",1234L,"")))));
        assertNotNull(in);
        assertEquals("x-9",in.id);
        assertEquals("Mondays",in.name);
        assertEquals("calendar",in.icon);
        assertArrayEquals(new byte[]{9,8,7},in.image);
        assertEquals(4,in.tint);
        assertEquals(-3L,in.ordinal);
        assertEquals(2,in.path.size());
        assertEquals("Meetings",in.path.get(1).name);
        assertTrue(in.writes);
        assertEquals("THING",in.scope);
        assertEquals("x-9",in.target);
        assertEquals(2,in.members.size());
        assertEquals("Ana",in.members.get(0).name);
        assertArrayEquals(new byte[]{5,5},in.members.get(0).agreement);
        assertEquals(0,in.members.get(1).agreement.length);
        assertTrue(in.answer);
        assertEquals(1,in.files.size());
        assertEquals("plan.pdf",in.files.get(0).name);
        assertEquals(555L,in.filesAsOf);
    }

    /** No list of files and an empty one are different things, and stay so. */
    @Test public void noListAndAnEmptyListStayApart() throws IOException {
        assertNull(Carton.open(Carton.wrap(carton(null))).files);
        assertNotNull(Carton.open(Carton.wrap(carton(List.of()))).files);
        assertTrue(Carton.open(Carton.wrap(carton(List.of()))).files.isEmpty());
    }

    @Test public void neitherAParcelNorANoteNorHalfOfOne() throws IOException {
        byte[] whole=Carton.wrap(carton(null));
        assertNull(Carton.open(Arrays.copyOf(whole,whole.length-3)));
        assertNull(Carton.open(Parcel.wrap(new Parcel.Sent("c","C","b","B","t","text",true))));
        assertNull(Carton.open("plain text written the oldest way".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertNull("a parcel is never one of these",Parcel.open(whole));
        assertEquals("never taken for an answer",0,Receipt.open(whole));
    }

    @Test public void aCollectionWithNoIdIsNotOne() throws IOException {
        Carton.Sent none=new Carton.Sent("","Nameless","",null,0,0L,List.of(),false,"","",List.of(),true,null,0L);
        assertNull(Carton.open(Carton.wrap(none)));
    }

    @Test public void itsNumberIsItsOwn() {
        assertEquals(22,Receipt.TREE);
        for(int other:new int[]{Receipt.HAVE,Receipt.ASK,Receipt.TOOK,Receipt.PERSONS,Receipt.PERSONS_MINE,Receipt.LOCKED,Receipt.OPENED,
                                Receipt.DROP_MISSING,Receipt.TAKES_FILES})
            assertNotEquals(Receipt.TREE,other);
        assertEquals(Receipt.TREE,Receipt.open(Receipt.wrap(Receipt.TREE)));
    }

    /** Made to fit an envelope: a list too long stops saying where its files are, the biggest first, and then is not said. */
    @Test public void aListTooLongToFitStopsSayingWhereAndThenIsNotSaid() throws IOException {
        List<Enclosure.Listed> files=new java.util.ArrayList<>();
        for(int at=0;at<8;at++)files.add(new Enclosure.Listed("f-"+at,"file "+at,"text/plain",10,"m".repeat(1000*(at+1))));
        Carton.Sent full=carton(files);
        byte[] whole=Carton.wrap(full);
        assertArrayEquals("what fits is sent as it is",whole,Carton.wrap(full,whole.length));
        byte[] lighter=Carton.wrap(full,whole.length-100);
        assertTrue(lighter.length<=whole.length-100);
        Carton.Sent opened=Carton.open(lighter);
        assertEquals(8,opened.files.size());
        assertEquals("the biggest says where no more","",opened.files.get(7).manifest);
        assertEquals("m".repeat(1000),opened.files.get(0).manifest);
        assertEquals("its look is kept","calendar",opened.icon);assertArrayEquals(new byte[]{9,8,7},opened.image);
        Carton.Sent none=Carton.open(Carton.wrap(full,200));
        assertNotNull(none);assertNull("no list at all takes nothing out",none.files);
    }
}
