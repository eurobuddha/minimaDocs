package org.mininotes.android;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

/** What an amber mark says it is waiting for, and a person's round with it: the same words on the phone and the PC. */
public class WaitsTest {
    private static Waits.Where text(){return new Waits.Where(1,0,0,false,false);}
    private static Waits.Where files(int lacking,int of){return new Waits.Where(0,lacking,of,true,false);}
    private static final Waits.Where DONE=new Waits.Where(0,0,9,true,false);

    @Test public void theMarkSaysWhoAndWhatOneLineADevice() {
        String said=Waits.said(List.of(new Waits.Device("Test phone",files(3,9),0),new Waits.Device("Test tablet",text(),0)),1);
        assertEquals("Waiting to reach Test tablet with your changes.\nWaiting for Test phone to confirm 3 files.",said);
        assertEquals("Waiting for Test phone to confirm 1 file.",Waits.said(List.of(new Waits.Device("Test phone",files(1,9),0)),1));
    }

    @Test public void anAnswerKeptForADeviceIsSaid() {
        assertEquals("Waiting to reach Test tablet with an answer.",Waits.said(List.of(new Waits.Device("Test tablet",DONE,1)),1));
        assertEquals("Waiting to reach Test tablet with 2 answers.",Waits.said(List.of(new Waits.Device("Test tablet",DONE,2)),1));
    }

    @Test public void aBookSaysHowManyNotesAndAddsUpTheFiles() {
        Waits.Where both=new Waits.Where(1,2,4,false,false).and(new Waits.Where(1,1,3,true,false));
        assertEquals(new Waits.Where(2,3,7,false,false),both);
        assertEquals("Waiting to reach Test phone with changes to 2 notes.\nWaiting for Test phone to confirm 3 files.",
            Waits.said(List.of(new Waits.Device("Test phone",both,0)),5));
    }

    @Test public void nothingKnownToWaitIsTheWordsOnTheScreen() {
        assertEquals(Waits.UNSAVED,Waits.said(List.of(new Waits.Device("Test phone",DONE,0)),1));
        assertEquals(Waits.UNSAVED,Waits.said(List.of(),1));
    }

    @Test public void notHeardFromComesFirst() {
        List<String> lines=Waits.lines(List.of(new Waits.Device("Test phone",files(2,2),0),
            new Waits.Device("Test tablet",new Waits.Where(1,0,0,false,true),0)),1);
        assertEquals("Test tablet has not been heard from for more than three days",lines.get(0));
        assertEquals("Waiting for Test phone to confirm 2 files",lines.get(1));
    }

    @Test public void aRoundSaysTheTextAndTheFilesApart() {
        assertEquals("Test phone has the text; 3 of 9 files still going.",Waits.person("Test phone",files(3,9),0));
        assertEquals("Your latest changes, and 1 of 1 file, have not reached them yet.",Waits.person("Test phone",new Waits.Where(1,1,1,false,false),0));
        assertEquals(SyncMark.WAITING.person(),Waits.person("Test phone",text(),0));
        assertEquals(SyncMark.SENT.person(),Waits.person("Test phone",new Waits.Where(0,0,0,false,false),0));
        assertEquals("Has this version, files included.",Waits.person("Test phone",DONE,0));
        assertEquals(SyncMark.GONE.person(),Waits.person("Test phone",new Waits.Where(0,0,0,true,false),0));
        assertTrue(Waits.person("Test phone",DONE,1).endsWith("An answer to them waits until they are in reach."));
    }

    @Test public void theMarkOfWhereIsTheMarkOfThePerson() {
        assertEquals(SyncMark.WAITING,files(1,2).mark());
        assertEquals(SyncMark.WAITING,text().mark());
        assertEquals(SyncMark.GONE,DONE.mark());
        assertEquals(SyncMark.SENT,new Waits.Where(0,0,0,false,false).mark());
        assertEquals(SyncMark.STUCK,new Waits.Where(1,0,0,false,true).mark());
    }

    @Test public void aDeviceThatSaysItIsLockedIsGreyAndSaidOnceLast() {
        Waits.Where locked=new Waits.Where(1,2,4,false,false,true);
        assertEquals(SyncMark.PAUSED,locked.mark());
        assertEquals("MainLaptop is locked; it takes it in when opened.",Waits.person("MainLaptop",locked,0));
        assertEquals("Waiting to reach Test phone with your changes.\nMainLaptop is locked; it takes it in when opened.",
            Waits.said(List.of(new Waits.Device("MainLaptop",locked,0),new Waits.Device("Test phone",text(),0)),1));
        // Locked with nothing waiting is simply done; locked for days is still red.
        assertEquals(SyncMark.GONE,new Waits.Where(0,0,9,true,false,true).mark());
        assertEquals(SyncMark.STUCK,new Waits.Where(1,0,0,false,true,true).mark());
        assertTrue(new Waits.Where(1,0,0,false,false,false).and(locked).locked());
        // A thing whose only waiting is on a locked device is not amber.
        assertEquals(SyncMark.GONE,SyncMark.of(false,true,false,SyncMark.worst(List.of(locked.mark()))));
        assertEquals(SyncMark.SENT,SyncMark.of(false,true,false,SyncMark.worst(List.of(locked.mark(),SyncMark.SENT))));
    }

    /** What waits for a device from before trees says so, by name, in the box and on its round - and is amber, locked or not. */
    @Test public void whatWaitsForADeviceToBeUpdatedSaysWhoNeedsToUpdate() {
        Waits.Where update=Waits.needsUpdate();
        assertEquals(1,update.update());
        assertEquals(SyncMark.WAITING,update.mark());
        assertEquals(SyncMark.WAITING,new Waits.Where(0,0,0,false,false,true,1).mark());
        assertEquals("Old phone needs to update Mininotes to receive this.",Waits.said(List.of(new Waits.Device("Old phone",update,0)),1));
        // Added up over a collection: three things waiting for it, and nothing about sending again or having it.
        Waits.Where three=update.and(update).and(new Waits.Where(0,0,0,true,false));
        assertEquals(2,three.update());
        assertEquals(List.of("Old phone needs to update Mininotes to receive this"),Waits.lines(List.of(new Waits.Device("Old phone",three,0)),3));
        // After the ones not heard from, before writing that waits: who has to do something comes first.
        List<String> lines=Waits.lines(List.of(new Waits.Device("Test phone",text(),0),new Waits.Device("Old phone",update,0),
            new Waits.Device("",update,0)),2);
        assertEquals(List.of("Old phone needs to update Mininotes to receive this","A paired device needs to update Mininotes to receive this",
            "Waiting to reach Test phone with changes to 1 note"),lines);
        // Their round says it, alone or after what else waits.
        assertEquals("Old phone needs to update Mininotes to receive this.",Waits.person("Old phone",update,0));
        assertEquals(SyncMark.WAITING.person()+" Old phone needs to update Mininotes to receive this.",
            Waits.person("Old phone",text().and(update),0));
        // The could-not-go box says the same words.
        assertEquals("Old phone needs to update Mininotes to receive this.",
            Unsent.said(new Unsent.Problem(Unsent.Why.NEEDS_UPDATE,"Old phone","Seeds","",""),"this phone"));
        assertEquals("A device needs to update Mininotes to receive this",Unsent.needsUpdate(" "));
    }
}
