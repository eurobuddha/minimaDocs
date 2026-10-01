package org.mininotes.android;

import static org.junit.Assert.*;

import org.junit.Test;

/** What a device is called when all this device has is a list of people: the best name known, never "?". See SyncStatus.named. */
public class SyncStatusNamingTest {
    @Test public void thePairedNameComesFirstThenTheListsThenItsKey() {
        assertEquals("Test phone",SyncStatus.named("Test phone","Listed name","key-a","peer-a"));
        assertEquals("Listed name",SyncStatus.named("","Listed name","key-a","peer-a"));
        assertEquals("Listed name",SyncStatus.named(null,"  Listed name ","key-a","peer-a"));
        String printed=SyncStatus.named(" ","","key-a","peer-a");
        assertTrue(printed,printed.matches("device [0-9A-F]{6}"));
    }

    @Test public void theShortFormIsTheSameEveryTimeAndDiffersBetweenDevices() {
        assertEquals(SyncStatus.named("","","key-a","peer-a"),SyncStatus.named("","","key-a","peer-b"));
        assertNotEquals(SyncStatus.named("","","key-a","peer-a"),SyncStatus.named("","","key-b","peer-a"));
        // No key: the address stands in, so it is still somebody in particular.
        assertTrue(SyncStatus.named("","","","peer-a").matches("device [0-9A-F]{6}"));
        assertEquals("",SyncStatus.named("","","",""));
    }

    @Test public void aPersonMadeWithoutSayingIsLinked() {
        SyncStatus.Person one=new SyncStatus.Person("peer-a","Test phone",SyncMark.GONE);
        assertTrue(one.linked());assertEquals("",one.listedIn());assertNull(one.listing());
        assertEquals("A paired device",new SyncStatus.Person("peer-a","",SyncMark.GONE).called());
    }
}
