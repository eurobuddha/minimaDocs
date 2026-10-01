// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** Lucide's set as path data: read whole, found by its words, and drawn inside its grid. */
public class IconsTest {
    /** A pen that keeps what it was told, and the box around every point it went through. */
    private static final class Trace implements Icons.Pen {
        final List<String> said=new ArrayList<>();
        float minX=Float.MAX_VALUE,minY=Float.MAX_VALUE,maxX=-Float.MAX_VALUE,maxY=-Float.MAX_VALUE;
        private void at(float x,float y){minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);}
        public void moveTo(float x,float y){said.add("M"+r(x)+","+r(y));at(x,y);}
        public void lineTo(float x,float y){said.add("L"+r(x)+","+r(y));at(x,y);}
        public void cubicTo(float x1,float y1,float x2,float y2,float x,float y){said.add("C"+r(x)+","+r(y));at(x,y);}
        public void close(){said.add("Z");}
        private static String r(float v){return String.valueOf(Math.round(v*100)/100f);}
    }

    @Test public void theWholeSetIsThereAndEveryIconDrawsInsideItsGrid() {
        Map<String,Icons.Icon> all=Icons.all();
        assertTrue("the set was not found beside the class",all.size()>1500);
        assertTrue(Icons.known(Icons.NOTE));
        for(Icons.Icon one:all.values()) {
            Trace pen=new Trace();
            Icons.trace(one.data,pen);
            assertFalse(one.name+" drew nothing",pen.said.isEmpty());
            assertTrue(one.name+" goes outside its grid",pen.minX>=-0.5f&&pen.minY>=-0.5f&&pen.maxX<=24.5f&&pen.maxY<=24.5f);
        }
    }

    @Test public void relativeCommandsLinesAndCloseAreMadeAbsolute() {
        Trace pen=new Trace();
        Icons.trace("M3 3h4v4l-2 2Z m10 0 1 1 1 1",pen);
        assertEquals(List.of("M3.0,3.0","L7.0,3.0","L7.0,7.0","L5.0,9.0","Z","M13.0,3.0","L14.0,4.0","L15.0,5.0"),pen.said);
    }

    @Test public void numbersRunTogetherAsSvgWritesThem() {
        Trace pen=new Trace();
        Icons.trace("M1-2.5.5.5L-.706 1e1",pen);
        assertEquals(List.of("M1.0,-2.5","L0.5,0.5","L-0.71,10.0"),pen.said);
    }

    @Test public void aCircleMadeOfTwoArcsStaysOnItsCircle() {
        Trace pen=new Trace();
        Icons.trace("M10 12A2 2 0 1 0 14 12A2 2 0 1 0 10 12Z",pen);
        assertEquals(10f,pen.minX,0.01f);assertEquals(14f,pen.maxX,0.01f);
        assertEquals(10f,pen.minY,0.05f);assertEquals(14f,pen.maxY,0.05f);
        // Each half is cut into two quarter turns.
        assertEquals(1+4+1,pen.said.size());
    }

    @Test public void arcFlagsWrittenTogetherAreReadOneByOne() {
        Trace pen=new Trace(),same=new Trace();
        Icons.trace("M10 12a2 2 0 0114 0",pen);
        Icons.trace("M10 12a2 2 0 0 1 14 0",same);
        assertEquals(same.said,pen.said);
    }

    @Test public void aSmoothCurveMirrorsTheControlPointBeforeIt() {
        List<float[]> curves=new ArrayList<>();
        Icons.trace("M0 0C0 4 4 4 4 0S8 -4 8 0",new Icons.Pen(){
            public void moveTo(float x,float y){}
            public void lineTo(float x,float y){}
            public void cubicTo(float x1,float y1,float x2,float y2,float x,float y){curves.add(new float[]{x1,y1});}
            public void close(){}
        });
        assertEquals(2,curves.size());
        assertEquals(4f,curves.get(1)[0],0.001f);assertEquals(-4f,curves.get(1)[1],0.001f);
    }

    @Test public void damagedDataStopsWhereItStopsMakingSense() {
        Trace pen=new Trace();
        Icons.trace("M1 1L2 2L3 x L4 4",pen);
        assertEquals(List.of("M1.0,1.0","L2.0,2.0"),pen.said);
        Trace none=new Trace();Icons.trace("12 12",none);assertTrue(none.said.isEmpty());
        assertFalse(Icons.draw("no-such-icon",new Trace()));
    }

    @Test public void iconsAreFoundByNameFirstAndThenByTheirWords() {
        List<String> notes=Icons.search("sticky note",20);
        assertEquals(Icons.NOTE,notes.get(0));
        assertTrue(Icons.search("post-it",50).contains(Icons.NOTE));
        List<String> folders=Icons.search("folder",200);
        assertEquals("folder",folders.get(0));
        assertTrue(folders.size()>5);
        assertEquals(3,Icons.search("a",3).size());
        assertEquals(Icons.all().size(),Icons.search("",100000).size());
    }

    @Test public void aLineThatDoesNotReadIsLeftOutAndTheRestKept() throws Exception {
        String set="# a comment\nbook\treading,library\tM4 19.5v-15\nbroken line\nnotes\t\tM1 1L2 2\n";
        Map<String,Icons.Icon> read=Icons.read(new ByteArrayInputStream(set.getBytes(StandardCharsets.UTF_8)));
        assertEquals(List.of("book","notes"),new ArrayList<>(read.keySet()));
        assertEquals(List.of("reading","library"),read.get("book").tags);
        assertTrue(read.get("notes").tags.isEmpty());
    }
}
