// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** The open notes and collections, newest first, twelve at most, kept as lines a torn one cannot spoil (docs/HOME.md, decision 22). */
public class OverviewTest {
    private static List<String> ids(Overview overview) {
        List<String> out=new ArrayList<>();for(Overview.Open one:overview.all())out.add(one.id);return out;
    }

    @Test public void theNewestIsFirstAndOpeningAgainBringsItToTheFront() {
        Overview open=new Overview();
        assertTrue(open.isEmpty());
        assertTrue(open.open(Overview.Kind.NOTE,"n1"));
        open.open(Overview.Kind.COLLECTION,"c1");
        open.open(Overview.Kind.NOTE,"n2");
        assertEquals(List.of("n2","c1","n1"),ids(open));
        // Opened again: to the front, never twice.
        open.open(Overview.Kind.NOTE,"n1");
        assertEquals(List.of("n1","n2","c1"),ids(open));
        assertEquals(3,open.size());
        // A collection and a note are two things even under one id.
        open.open(Overview.Kind.COLLECTION,"n1");
        assertEquals(4,open.size());
        assertEquals(Overview.Kind.COLLECTION,open.all().get(0).kind);
        // What all() hands back is a copy.
        open.all().clear();
        assertEquals(4,open.size());
    }

    @Test public void twelveAtMostAndTheOldestGoes() {
        Overview open=new Overview();
        for(int at=1;at<=Overview.MOST+3;at++)open.open(Overview.Kind.NOTE,"n"+at);
        assertEquals(Overview.MOST,open.size());
        assertEquals("n"+(Overview.MOST+3),open.all().get(0).id);
        assertEquals("the oldest three let go",List.of("n4"),ids(open).subList(Overview.MOST-1,Overview.MOST));
        assertFalse(ids(open).contains("n3"));
    }

    @Test public void closingOneClosingAllAndForgettingWhatWasDeleted() {
        Overview open=new Overview();
        open.open(Overview.Kind.NOTE,"n1");open.open(Overview.Kind.COLLECTION,"c1");open.open(Overview.Kind.NOTE,"n2");
        assertTrue(open.close(Overview.Kind.COLLECTION,"c1"));
        assertFalse("not open",open.close(Overview.Kind.COLLECTION,"c1"));
        assertFalse("the kind is part of what it is",open.close(Overview.Kind.COLLECTION,"n1"));
        assertEquals(List.of("n2","n1"),ids(open));
        open.open(Overview.Kind.COLLECTION,"n2");
        assertTrue("deleted: gone whatever kind it was",open.forget("n2"));
        assertEquals(List.of("n1"),ids(open));
        assertFalse(open.forget("nothing"));
        open.closeAll();
        assertTrue(open.isEmpty());
        // Nothing that is not the id of anything.
        assertFalse(open.open(Overview.Kind.NOTE,""));
        assertFalse(open.open(Overview.Kind.NOTE,"   "));
        assertFalse(open.open(Overview.Kind.NOTE,null));
        assertFalse(open.open(null,"n1"));
        assertFalse(open.open(Overview.Kind.NOTE,"a\nb"));
        assertFalse(open.open(Overview.Kind.NOTE,"a\tb"));
        assertTrue(open.isEmpty());
    }

    @Test public void keptAsLinesAndReadBackTheSame() {
        Overview open=new Overview();
        open.open(Overview.Kind.NOTE,"1404353f-7b77-49fc-ad55-d6cb34eb390b");open.open(Overview.Kind.COLLECTION,SchemaMigrations.FIRST_COLLECTION);
        String said=open.said();
        assertEquals("COLLECTION\t"+SchemaMigrations.FIRST_COLLECTION+"\nNOTE\t1404353f-7b77-49fc-ad55-d6cb34eb390b",said);
        Overview back=Overview.read(said);
        assertEquals(open.all(),back.all());
        assertEquals(said,back.said());
        assertEquals("",new Overview().said());
        assertTrue(Overview.read("").isEmpty());
        assertTrue(Overview.read(null).isEmpty());
    }

    @Test public void aLineThatDoesNotReadIsDroppedAndTheRestStand() {
        Overview back=Overview.read("NOTE\tn1\r\nPICTURE\tp1\n\tno-kind\nNOTE\t\nCOLLECTION\n garbage \nCOLLECTION\tc1\nNOTE\tn1\n");
        assertEquals(List.of("n1","c1"),ids(back));
        assertEquals(Overview.Kind.COLLECTION,back.all().get(1).kind);
        // More than twelve lines, as a newer build might have kept: the first twelve.
        StringBuilder many=new StringBuilder();
        for(int at=0;at<20;at++)many.append("NOTE\tn").append(at).append('\n');
        Overview first=Overview.read(many.toString());
        assertEquals(Overview.MOST,first.size());
        assertEquals("n0",first.all().get(0).id);
    }
}
