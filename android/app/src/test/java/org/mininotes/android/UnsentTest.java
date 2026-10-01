package org.mininotes.android;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** What is said when something could not go: the device and the thing by name, and what to do. See Unsent. */
public class UnsentTest {
    private static Unsent.Problem p(Unsent.Why why,String who,String thing){return new Unsent.Problem(why,who,thing,"","");}

    @Test public void aDeviceNeverPairedIsNamedWithTheThingAndThePairingIsTheButton() {
        Unsent.Problem half=p(Unsent.Why.NOT_PAIRED,"Ana's phone","Perso");
        assertEquals("Ana's phone has not been paired with this phone yet, so “Perso” cannot be sealed for it.",Unsent.said(half,"this phone"));
        assertTrue(Unsent.remedy(half,"this phone").contains("scan it on Ana's phone"));
        assertEquals(Unsent.Fix.PAIR,Unsent.fix(half));
        assertEquals("Pair with it",Unsent.button(Unsent.fix(half)));
    }

    @Test public void oneKnownOnlyFromAListOfPeopleIsSaidToBeInThatListAndCanBeLinkedOrTakenOff() {
        Unsent.Problem listed=new Unsent.Problem(Unsent.Why.ONLY_LISTED,"Work laptop","Shopping","Perso","","peer-a");
        assertEquals("Work laptop is listed for “Perso”, but this phone is not linked to them.",Unsent.said(listed,"this phone"));
        assertTrue(Unsent.remedy(listed,"this phone").contains("take them off"));
        assertEquals(Unsent.Fix.PAIR,Unsent.fix(listed));
        assertEquals("Link with Work laptop",Unsent.button(listed));
        assertEquals("Take them off “Perso”",Unsent.takeOff(listed));
        // The title names the device, and the thing that could not reach it.
        assertEquals("“Shopping” could not reach Work laptop",Unsent.title(List.of(listed,listed),0,2));
        assertEquals("“Perso” could not reach Work laptop",Unsent.title(List.of(new Unsent.Problem(Unsent.Why.ONLY_LISTED,"Work laptop","","Perso","","peer-a")),0,1));
        assertEquals("Some of it went, but not to Work laptop",Unsent.title(List.of(listed),3,1));
        // Nobody's name known: still said plainly, never "somebody"; and with no address there is nothing to take off.
        Unsent.Problem nameless=new Unsent.Problem(Unsent.Why.ONLY_LISTED,"","Perso","","");
        assertTrue(Unsent.said(nameless,"this PC").startsWith("A device is listed for “Perso”"));
        assertEquals("Link with them",Unsent.button(nameless));assertNull(Unsent.takeOff(nameless));
        assertEquals("It could not go",Unsent.title(List.of(nameless),0,1));
        // The round on a note says the same words.
        assertEquals("Ana is listed for “Transfer”, but this PC is not linked to them.",Unsent.notLinked("Ana","Transfer","this PC"));
        assertEquals("Take them off the list",Unsent.takeOff(""));
    }

    @Test public void theOtherReasonsKeepTheirButtonsAndTheirTitles() {
        Unsent.Problem half=p(Unsent.Why.NOT_PAIRED,"Ana's phone","Perso");
        assertEquals("Pair with it",Unsent.button(half));assertNull(Unsent.takeOff(half));
        assertEquals("How notes travel",Unsent.button(p(Unsent.of(Direct.WAITS),"Home PC","Perso")));
        assertNull(Unsent.button(p(Unsent.Why.NOT_REACHED,"Ana's phone","Perso")));assertNull(Unsent.button((Unsent.Problem)null));
        assertEquals("It could not go",Unsent.title(List.of(half,p(Unsent.Why.NOT_REACHED,"Home PC","Perso")),0,2));
    }

    @Test public void aDeviceFromBeforeTreesIsToldToUpdateAndItWaitsWithNoButton() {
        Unsent.Problem waits=p(Unsent.Why.NEEDS_UPDATE,"Ana's phone","Seeds");
        assertEquals("Ana's phone needs to update Mininotes to receive this.",Unsent.said(waits,"this phone"));
        assertTrue(Unsent.remedy(waits,"this phone").contains("goes by itself once Mininotes is updated on Ana's phone"));
        assertEquals(Unsent.Fix.NONE,Unsent.fix(waits));assertNull(Unsent.button(waits));
        assertEquals("A device needs to update Mininotes to receive this.",Unsent.said(p(Unsent.Why.NEEDS_UPDATE,"",""),"this PC"));
        assertEquals("“Seeds” could not reach Ana's phone",Unsent.title(List.of(waits),0,1));
    }

    @Test public void whatThePadPutsRightByItselfSaysSoAndHasNoButton() {
        Unsent.Problem away=p(Unsent.Why.NOT_REACHED,"Ana's phone","Perso");
        assertEquals("“Perso” could not reach Ana's phone just now.",Unsent.said(away,"this phone"));
        assertTrue(Unsent.remedy(away,"this phone").startsWith("Open Mininotes on Ana's phone"));
        assertTrue(Unsent.remedy(away,"this phone").contains("goes by itself"));
        assertEquals(Unsent.Fix.NONE,Unsent.fix(away));assertNull(Unsent.button(Unsent.Fix.NONE));
        assertEquals("That device could not be reached just now.",Unsent.said(p(Unsent.Why.NOT_REACHED,"",""),"this phone"));
    }

    @Test public void onlyBetweenYourDevicesTheButtonIsHowNotesTravel() {
        Unsent.Problem far=p(Unsent.of(Direct.WAITS),"Home PC","Perso");
        assertEquals(Unsent.Why.NOT_IN_REACH,far.why);
        assertEquals(Unsent.Fix.TRAVEL,Unsent.fix(far));assertEquals("How notes travel",Unsent.button(Unsent.Fix.TRAVEL));
        assertTrue(Unsent.said(far,"this phone").startsWith("Home PC is not in reach, so “Perso” waits"));
    }

    @Test public void errorsAreReadForWhatTheyAre() {
        assertEquals(Unsent.Why.NOT_CONNECTED,Unsent.of("The node is not running."));
        assertEquals(Unsent.Why.TOO_OLD,Unsent.of("That note is older than sharing and has no id that travels."));
        assertEquals(Unsent.Why.NOT_REACHED,Unsent.of("That permanent address could not be looked up, so there is nowhere to send to yet."));
        assertEquals(Unsent.Why.OTHER,Unsent.of(null));assertEquals(Unsent.Why.OTHER,Unsent.of("Disk full"));
        assertEquals("Disk full",Unsent.said(new Unsent.Problem(Unsent.Why.OTHER,"","","","Disk full"),"this phone"));
    }

    @Test public void theSameReasonForTheSameDeviceIsSaidOnceAndAPairingComesFirst() {
        List<Unsent.Problem> all=Arrays.asList(p(Unsent.Why.NOT_REACHED,"Ana's phone","Perso"),p(Unsent.Why.NOT_REACHED,"Ana's phone","Perso"),
            p(Unsent.Why.NOT_PAIRED,"Work laptop","Perso"),p(Unsent.Why.NOT_PAIRED,"Work laptop","Perso"));
        assertEquals(2,Unsent.distinct(all).size());
        assertEquals(Unsent.Why.NOT_PAIRED,Unsent.first(all).why);
        String words=Unsent.words(all,"this phone");
        assertEquals("one paragraph for each",1,words.split("\n\n").length-1);
        assertTrue(words.contains("Work laptop has not been paired"));assertTrue(words.contains("could not reach Ana's phone"));
        assertNull(Unsent.first(Collections.emptyList()));
        assertEquals("It stays waiting and is tried again by itself.",Unsent.words(Collections.emptyList(),"this phone"));
    }

    @Test public void theTitleSaysWhetherAnyOfItWent() {
        assertEquals("It could not go",Unsent.title(0,3));
        assertEquals("Some of it went, and one note could not",Unsent.title(2,1));
        assertEquals("Some of it went, and 4 notes could not",Unsent.title(1,4));
    }
}
