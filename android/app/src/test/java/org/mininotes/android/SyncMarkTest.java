package org.mininotes.android;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

/** Which of the six marks a thing wears, and in what colour: the rules the phone and the PC share. */
public class SyncMarkTest {
    private static final long DAY=24L*60*60*1000, NOW=1_800_000_000_000L;

    @Test public void onePersonsMark() {
        assertEquals(SyncMark.WAITING,SyncMark.person(true,false,false,false));
        assertEquals("a file still going keeps them waiting",SyncMark.WAITING,SyncMark.person(false,true,true,false));
        assertEquals(SyncMark.SENT,SyncMark.person(false,false,false,false));
        assertEquals(SyncMark.GONE,SyncMark.person(false,false,true,false));
        assertEquals(SyncMark.STUCK,SyncMark.person(true,false,false,true));
    }

    @Test public void redIsOwedForMoreThanThreeDaysAndNotHeardFromSince() {
        assertFalse("owed a day",SyncMark.stuck(NOW-DAY,0,NOW));
        assertFalse("owed exactly three days",SyncMark.stuck(NOW-3*DAY,0,NOW));
        assertTrue("four days, never heard from",SyncMark.stuck(NOW-4*DAY,0,NOW));
        assertTrue("four days, heard from only before the change",SyncMark.stuck(NOW-4*DAY,NOW-5*DAY,NOW));
        assertFalse("heard from since: there, and only slow",SyncMark.stuck(NOW-4*DAY,NOW-2*DAY,NOW));
        assertFalse("no time for the change",SyncMark.stuck(0,0,NOW));
    }

    @Test public void theThingsMarkIsTheWorstOfItsPeople() {
        assertEquals(SyncMark.PAUSED,SyncMark.of(true,true,true,SyncMark.STUCK));
        assertEquals(SyncMark.HERE,SyncMark.of(false,false,true,null));
        assertEquals(SyncMark.STUCK,SyncMark.of(false,true,true,SyncMark.STUCK));
        assertEquals("typed and not saved: waiting",SyncMark.WAITING,SyncMark.of(false,true,true,SyncMark.GONE));
        assertEquals(SyncMark.SENT,SyncMark.of(false,true,false,SyncMark.SENT));
        assertEquals("shared, and nobody owed it",SyncMark.GONE,SyncMark.of(false,true,false,null));
        assertEquals(SyncMark.STUCK,SyncMark.worst(Arrays.asList(SyncMark.GONE,SyncMark.STUCK,SyncMark.WAITING)));
        assertEquals(SyncMark.WAITING,SyncMark.worst(Arrays.asList(SyncMark.SENT,SyncMark.WAITING,SyncMark.GONE)));
        assertEquals(SyncMark.SENT,SyncMark.worst(Arrays.asList(SyncMark.GONE,null,SyncMark.SENT)));
        assertNull(SyncMark.worst(Arrays.<SyncMark>asList()));
    }

    @Test public void theColourNeverContradictsTheDrawing() {
        for(SyncMark one:SyncMark.values()) {
            SyncMark.Ink ink=one.ink();
            switch(one) {
                case HERE, PAUSED -> assertEquals(SyncMark.Ink.QUIET,ink);
                case WAITING, SENT -> assertEquals(SyncMark.Ink.AMBER,ink);
                case GONE -> assertEquals(SyncMark.Ink.GREEN,ink);
                case STUCK -> assertEquals(SyncMark.Ink.RED,ink);
            }
            assertFalse(one+" has a line in Settings",one.meaning().isEmpty());
            assertFalse(one+" has a name for a screen reader",one.said("this phone").isEmpty());
        }
        assertEquals(0xFF123456,SyncMark.GONE.colour(false,0xFF999999,0xFF123456));
        assertEquals(SyncMark.AMBER_ON_DARK,SyncMark.SENT.colour(true,0,0));
        assertEquals(SyncMark.RED,SyncMark.STUCK.colour(false,0,0));
    }

    /** Every paper either app draws on: the phone's ten (MainActivity.PAPERS) and the PC's page, card and side bar. */
    private static final int[] LIGHT={0xFFFFFFFF,0xFFFDFCFA,0xFFFBFAF6,0xFFF8F4E9,0xFFF2EBDB,0xFFE9E0CA,0xFFDACEB3,
        0xFFFAF9F4,0xFFFFFFFC,0xFFF3F1EA}, DARK={0xFF4A4A43,0xFF262B28,0xFF121413};

    @Test public void amberAndRedReadOnEveryPaper() {
        for(int paper:LIGHT) {
            assertTrue(Integer.toHexString(paper),contrast(SyncMark.AMBER,paper)>=3);
            assertTrue(Integer.toHexString(paper),contrast(SyncMark.RED,paper)>=3);
        }
        for(int paper:DARK) {
            assertTrue(Integer.toHexString(paper),contrast(SyncMark.AMBER_ON_DARK,paper)>=3);
            assertTrue(Integer.toHexString(paper),contrast(SyncMark.RED_ON_DARK,paper)>=3);
        }
    }

    private static double contrast(int a,int b) {
        double x=luminance(a),y=luminance(b);
        return (Math.max(x,y)+0.05)/(Math.min(x,y)+0.05);
    }
    private static double luminance(int colour) {
        double[] part=new double[3];
        for(int i=0;i<3;i++) {
            double v=((colour>>(16-8*i))&0xFF)/255.0;
            part[i]=v<=0.03928?v/12.92:Math.pow((v+0.055)/1.055,2.4);
        }
        return 0.2126*part[0]+0.7152*part[1]+0.0722*part[2];
    }
}
