package org.mininotes.android;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReadingTest {
    @Test public void aNoteWithNoSizeOfItsOwnIsReadAtTheDevices() {
        assertEquals(4,Reading.of(Reading.NONE,4));
        assertEquals(7,Reading.of(Reading.NONE,7));
        assertTrue(Reading.followsDevice(Reading.NONE));
    }
    @Test public void aNoteWithItsOwnSizeKeepsItWhateverTheDeviceSays() {
        assertEquals(2,Reading.of(2,4));assertEquals(2,Reading.of(2,9));
        assertEquals(0,Reading.of(0,4));
        assertFalse(Reading.followsDevice(0));
    }
    @Test public void anUnknownNumberIsNoSizeOfItsOwn() {
        for(long odd:new long[]{-1,-7,10,99,Long.MAX_VALUE,Long.MIN_VALUE})assertEquals(Reading.NONE,Reading.stored(odd));
        for(int rung=0;rung<Reading.RUNGS;rung++)assertEquals(rung,Reading.stored(rung));
        assertEquals(5,Reading.of(42,5));
    }
    @Test public void theDevicesRungIsKeptOnTheLadder() {
        assertEquals(0,Reading.of(Reading.NONE,-3));assertEquals(Reading.RUNGS-1,Reading.of(Reading.NONE,40));
    }
    @Test public void aStepStartsFromWhereTheNoteIsReadNow() {
        // No size of its own yet: one step up is one up from the device's, and becomes the note's own.
        assertEquals(5,Reading.step(Reading.NONE,4,1));
        assertEquals(3,Reading.step(Reading.NONE,4,-1));
        assertEquals(8,Reading.step(7,2,1));
    }
    @Test public void aStepPastEitherEndIsNoStep() {
        assertEquals(Reading.NONE,Reading.step(0,4,-1));
        assertEquals(Reading.NONE,Reading.step(Reading.RUNGS-1,4,1));
        assertEquals(Reading.NONE,Reading.step(Reading.NONE,Reading.RUNGS-1,1));
    }
}
