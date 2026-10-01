package org.mininotes.android;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Map;
import java.util.Set;

public class WritersTest {
    private static final String ANA=Writers.byKey("ana-key"), BEN=Writers.byKey("ben-key"), ME=Writers.ME, NOBODY=Writers.UNKNOWN;

    /** One letter per letter of the text: m for me, A for Ana, B for Ben, . for nobody known. */
    private static String who(Writers runs) {
        StringBuilder out=new StringBuilder();
        for(int i=0;i<runs.count();i++)
            for(int n=0;n<runs.lengthOf(i);n++) {
                String by=runs.writerOf(i);
                out.append(by.equals(ME)?'m':by.equals(ANA)?'A':by.equals(BEN)?'B':by.isEmpty()?'.':'?');
            }
        return out.toString();
    }
    /** Runs from one letter per letter, as {@link #who} writes them. */
    private static Writers runs(String letters) {
        Writers out=Writers.none();
        for(char c:letters.toCharArray())out.edit(out.length(),0,1,c=='m'?ME:c=='A'?ANA:c=='B'?BEN:NOBODY);
        return out;
    }

    // ---- typing ---------------------------------------------------------------------------------------------------

    @Test public void typingIntoAnEmptyNoteIsAllYours() {
        Writers runs=Writers.none();
        for(int i=0;i<5;i++)runs.edit(i,0,1,ME);
        assertEquals("mmmmm",who(runs));
        assertEquals(1,runs.count());
    }
    @Test public void typingInsideSomebodyElsesWordsMakesOnlyTheNewLettersYours() {
        Writers runs=Writers.of(ANA,6);
        runs.edit(3,0,2,ME);
        assertEquals("AAAmmAAA",who(runs));
        assertEquals(3,runs.count());
    }
    @Test public void deletingTakesTheLettersAndLeavesTheRestAsTheyWere() {
        Writers runs=runs("AAAmmmBBB");
        runs.edit(2,5,0,ME);
        assertEquals("AABB",who(runs));
        runs.edit(0,4,0,ME);
        assertEquals("",who(runs));
        assertEquals(0,runs.count());
    }
    @Test public void replacingASelectionMakesTheNewWordsYours() {
        Writers runs=runs("AAAAABBBBB");
        runs.edit(3,4,2,ME);
        assertEquals("AAAmmBBB",who(runs));
    }
    @Test public void pastingIsTheSameAsTyping() {
        Writers runs=runs("AAAA");
        runs.edit(4,0,10,ME);
        runs.edit(0,0,3,ME);
        assertEquals("mmmAAAAmmmmmmmmmm",who(runs));
    }
    @Test public void deletingWhatSeparatedTwoRunsOfOneWriterMakesThemOne() {
        Writers runs=runs("AAmmAA");
        runs.edit(2,2,0,ME);
        assertEquals("AAAA",who(runs));
        assertEquals(1,runs.count());
    }
    @Test public void anEditPastTheEndIsKeptInside() {
        Writers runs=runs("AAA");
        runs.edit(10,5,2,ME);
        assertEquals("AAAmm",who(runs));
        runs.edit(-4,1,0,ME);
        assertEquals("AAmm",who(runs));
    }
    @Test public void aCopyIsNotTheSameRuns() {
        Writers runs=runs("AAmm"), copy=runs.copy();
        runs.edit(0,0,3,BEN);
        assertEquals("AAmm",who(copy));
        assertEquals("BBBAAmm",who(runs));
    }
    @Test public void whoWroteALetterIsReadWhereItIs() {
        Writers runs=runs("AAmmB");
        assertEquals(ANA,runs.at(1));assertEquals(ME,runs.at(2));assertEquals(BEN,runs.at(4));assertEquals(NOBODY,runs.at(5));
        assertEquals(Set.of(ANA,ME,BEN),runs.writers());
        assertTrue(runs("..").writers().isEmpty());
    }
    @Test public void aLongNoteTypedLetterByLetterStaysQuick() {
        // Ana's paragraph with a letter of yours typed after every tenth of hers: two thousand runs, each keystroke a walk.
        Writers runs=Writers.of(ANA,20000);
        long start=System.nanoTime();
        for(int at=10;at<runs.length();at+=11)runs.edit(at,0,1,ME);
        assertTrue(runs.count()>3000);
        assertTrue("took "+(System.nanoTime()-start)/1_000_000+" ms",(System.nanoTime()-start)<2_000_000_000L);
    }

    // ---- what arrived ---------------------------------------------------------------------------------------------

    @Test public void aNoteNewHereIsAllTheSenders() {
        assertEquals("AAAAA",who(Writers.arrived(null,null,"Hello",ANA)));
        assertEquals("AAAAA",who(Writers.arrived("",Writers.none(),"Hello",ANA)));
    }
    @Test public void onlyTextNewHereIsCreditedToTheSender() {
        String was="Milk\nBread\n", now="Milk\nEggs\nBread\n";
        Writers mine=Writers.of(ME,was.length());
        assertEquals("mmmmmAAAAAmmmmmm",who(Writers.arrived(was,mine,now,ANA)));
    }
    @Test public void aWordAddedInsideALineIsTheSendersAndTheLineAroundItStaysYours() {
        String was="Buy milk today", now="Buy oat milk today";
        assertEquals("mmmmAAAAmmmmmmmmmm",who(Writers.arrived(was,Writers.of(ME,was.length()),now,ANA)));
    }
    @Test public void whatTheSenderDeletedIsSimplyGone() {
        String was="one two three", now="one three";
        Writers before=runs("mmmmBBBBmmmmm");
        assertEquals("mmmmmmmmm",who(Writers.arrived(was,before,now,ANA)));
    }
    @Test public void wordsRewrittenAreTheSendersWithoutSpecklesOfLettersTheyHappenToShare() {
        String was="say hello now", now="say goodbye now";
        Writers after=Writers.arrived(was,Writers.of(ME,was.length()),now,ANA);
        // "goodbye" shares an "o" with "hello": that is not the same writing.
        assertEquals("mmmmAAAAAAAmmmm",who(after));
    }
    @Test public void whatWasWrittenByOthersBeforeKeepsItsWriterWhenSomebodyElseSendsIt() {
        // Ben's line reaches Ana, and Ana sends the note back with a line of her own. Ben's stays Ben's here.
        String was="Ben's line\n", now="Ben's line\nAna's line\n";
        assertEquals("BBBBBBBBBBBAAAAAAAAAAA",who(Writers.arrived(was,Writers.of(BEN,was.length()),now,ANA)));
    }
    @Test public void textFromBeforeWritersWereKeptStaysNobodys() {
        String was="Old words\n", now="Old words\nNew\n";
        assertEquals("..........AAAA",who(Writers.arrived(was,null,now,ANA)));
        assertEquals("..........AAAA",who(Writers.arrived(was,Writers.unknown(3),now,ANA)));
    }
    @Test public void linesMovedAboutKeepWhoWroteThem() {
        String was="alpha\nbeta\ngamma\n", now="gamma\nalpha\nbeta\n";
        Writers before=runs("AAAAAABBBBBmmmmmm");
        // The lines that stayed in order keep their writers; the line that moved is new where it now is: the sender's.
        assertEquals("AAAAAA"+"AAAAAA"+"BBBBB",who(Writers.arrived(was,before,now,ANA)));
    }
    @Test public void theSameTextArrivingAgainChangesNothing() {
        String text="Milk\nEggs\n";
        Writers before=runs("mmmmmAAAAA");
        assertEquals("mmmmmAAAAA",who(Writers.arrived(text,before,text,BEN)));
    }
    @Test public void aMergeKeepsThePagesWritersFirstThenWhatArrived() {
        // The page had "Tea" typed on it while Ana's "Eggs" arrived underneath it: both, each theirs.
        String kept="Milk\n", page="Milk\nTea\n", stored="Milk\nEggs\n";
        String merged=Merge.merge(kept,page,stored).text;
        Writers pageRuns=runs("....."+"mmmm"), storedRuns=runs("....."+"AAAAA");
        Writers after=Writers.follow(merged,NOBODY,page,pageRuns,stored,storedRuns);
        assertEquals(merged.length(),after.length());
        assertEquals(merged.indexOf("Tea"),who(after).indexOf('m'));
        assertEquals(merged.indexOf("Eggs"),who(after).indexOf('A'));
        assertEquals('.',who(after).charAt(0));
    }
    @Test public void runsThatDoNotFitTheirTextAreNobodysRatherThanAGuess() {
        Writers after=Writers.follow("abc",ME,"xyz!",Writers.of(ANA,2));
        assertEquals("mmm",who(after));
        after=Writers.follow("xyz",ME,"xyz",Writers.of(ANA,2));
        assertEquals("...",who(after));
    }
    @Test public void aLongNoteArrivingIsReadQuickly() {
        StringBuilder text=new StringBuilder();
        for(int i=0;i<1500;i++)text.append("Line ").append(i).append(" of the long note\n");
        String was=text.toString(), now=was.replace("Line 700 ","Line seven hundred ").replace("Line 20 of","Line 20, still, of")+"The end\n";
        long start=System.nanoTime();
        Writers after=Writers.arrived(was,Writers.of(ME,was.length()),now,ANA);
        long took=(System.nanoTime()-start)/1_000_000;
        assertTrue("took "+took+" ms",took<3000);
        String letters=who(after);
        assertEquals(now.length(),letters.length());
        int seven=now.indexOf("seven hundred");
        assertEquals('A',letters.charAt(seven));
        assertEquals('m',letters.charAt(now.indexOf("Line 701")));
        assertEquals('A',letters.charAt(now.length()-2));
    }
    @Test public void aNoteEveryLineOfWhichChangedIsStillRead() {
        StringBuilder a=new StringBuilder(), b=new StringBuilder();
        for(int i=0;i<3000;i++){a.append(i).append('\n');b.append('x').append(i).append('\n');}
        Writers after=Writers.arrived(a.toString(),Writers.of(ME,a.length()),b.toString(),ANA);
        assertEquals(b.length(),after.length());
    }

    // ---- where the reader was ---------------------------------------------------------------------------------------

    @Test public void linesArrivingAboveTheReaderPushTheirPlaceDownWithTheWords() {
        String was="one\ntwo\nthree\nfour\n", now="zero\none\nnew\ntwo\nthree\nfour\n";
        int three=was.indexOf("three"), caret=was.indexOf("our")+1;
        int[] at=Writers.moved(was,now,three,caret);
        assertEquals(now.indexOf("three"),at[0]);
        assertEquals(now.indexOf("our")+1,at[1]);
    }
    @Test public void linesArrivingBelowLeaveThePlaceWhereItWas() {
        String was="one\ntwo\n", now="one\ntwo\nthree\n";
        assertArrayEquals(new int[]{0,4,5},Writers.moved(was,now,0,4,5));
    }
    @Test public void thePlaceOfWordsTakenOutIsJustAfterWhatCameBefore() {
        String was="keep\ngone line\nafter\n", now="keep\nafter\n";
        // The first line they could see was taken out: they are left where it was, at the line that follows.
        assertEquals(now.indexOf("after"),Writers.moved(was,now,was.indexOf("gone"))[0]);
        // A cursor inside the words taken out is left where they were.
        assertEquals(now.indexOf("after"),Writers.moved(was,now,was.indexOf("line"))[0]);
    }
    @Test public void theEndStaysTheEnd() {
        String was="abc", now="xyz\nabc";
        assertEquals(now.length(),Writers.moved(was,now,was.length())[0]);
        assertEquals(0,Writers.moved("","new",0)[0]);
        assertEquals(0,Writers.moved("gone","",4)[0]);
    }
    @Test public void aSelectionMovesWithTheWordsItHeld() {
        String was="alpha beta gamma", now="new words. alpha beta gamma";
        int[] at=Writers.moved(was,now,6,10);
        assertEquals("beta",now.substring(at[0],at[1]));
    }
    @Test public void aPlaceOutsideTheTextIsKeptInsideIt() {
        assertArrayEquals(new int[]{0,3},Writers.moved("abc","abc",-5,99));
    }

    // ---- kept -----------------------------------------------------------------------------------------------------

    @Test public void keptAndReadBackTheyAreTheSame() {
        Writers runs=runs("..mmmAAAABBm.");
        assertEquals(who(runs),who(Writers.read(runs.write(),13)));
    }
    @Test public void nothingIsKeptWhereNobodyKnownWroteAnything() {
        assertNull(Writers.unknown(40).write());
        assertNull(Writers.none().write());
        assertEquals(".....",who(Writers.read(null,5)));
    }
    @Test public void whatIsKeptForAnotherLengthOrInAnotherFormatIsNobodys() {
        String kept=runs("mmAA").write();
        assertEquals(".....",who(Writers.read(kept,5)));
        assertEquals("....",who(Writers.read("W9\n4.0\nme",4)));
        assertEquals("....",who(Writers.read("W1\n4.7\nme",4)));
        assertEquals("....",who(Writers.read("W1\nfour\nme",4)));
        assertEquals("....",who(Writers.read("garbage",4)));
    }
    @Test public void keysWithEveryLetterAKeyCanHoldAreKeptWhole() {
        String key=Writers.byKey("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE+/=="), address=Writers.byAddress("Mx123@10.0.0.1:9001");
        Writers runs=Writers.of(key,3);runs.edit(3,0,2,address);
        Writers back=Writers.read(runs.write(),5);
        assertEquals(key,back.at(0));assertEquals(address,back.at(4));
    }

    // ---- colours --------------------------------------------------------------------------------------------------

    @Test public void aNoteOnlyYouWroteIsDrawnAsItAlwaysWas() {
        Writers.Palette palette=new Writers.Palette(3,null,Set.of(Writers.byKey("my-pc")));
        assertFalse(palette.shows(runs("mmmm")));
        assertFalse(palette.shows(runs("mm....")));
        assertFalse(palette.shows(runs("....")));
        // Your PC is you: its writing and this device's are one writer.
        Writers two=runs("mm");two.edit(2,0,2,Writers.byKey("my-pc"));
        assertFalse(palette.shows(two));
        assertTrue(palette.shows(runs("mmAA")));
        assertTrue(palette.shows(runs("AA..BB")));
    }
    @Test public void youAreYourColourOnEveryDeviceOfYours() {
        Writers.Palette palette=new Writers.Palette(6,Map.of(ME,2),Set.of(Writers.byKey("my-pc")));
        assertEquals(6,palette.colourOf(ME));
        assertEquals(6,palette.colourOf(Writers.byKey("my-pc")));
        assertEquals(ME,palette.person(Writers.byKey("my-pc")));
        assertEquals(Tint.NONE,new Writers.Palette(Tint.NONE,null,null).colourOf(ME));
    }
    @Test public void nobodyKnownIsTheOrdinaryInk() {
        assertEquals(Tint.NONE,new Writers.Palette(3,null,null).colourOf(NOBODY));
        assertNull(Writers.Palette.plain().person(NOBODY));
    }
    @Test public void somebodyElseHasTheirOwnColourUntilYouGiveThemOne() {
        Writers.Palette plain=Writers.Palette.plain();
        assertEquals(Writers.automatic(ANA),plain.colourOf(ANA));
        assertFalse(plain.chose(ANA));
        Writers.Palette given=new Writers.Palette(Tint.NONE,Map.of(ANA,7),null);
        assertEquals(7,given.colourOf(ANA));
        assertTrue(given.chose(ANA));
        // A number no colour has any more is their own colour again, not nothing.
        assertEquals(Writers.automatic(ANA),new Writers.Palette(Tint.NONE,Map.of(ANA,99),null).colourOf(ANA));
    }
    @Test public void theAutomaticColourIsOneOfTheEightAndTheSameEveryTime() {
        for(String one:new String[]{ANA,BEN,"kX","k"+"z".repeat(90),Writers.byAddress("Mx@1")}) {
            int colour=Writers.automatic(one);
            assertTrue(one,Tint.known(colour));
            assertEquals(colour,Writers.automatic(one));
        }
        // Worked out from the key alone: this is what every other device works out too.
        assertEquals(1+(Sha3.of(ANA.getBytes(java.nio.charset.StandardCharsets.UTF_8))[0]&0xff)%8,Writers.automatic(ANA));
        // And spread over the eight, not stuck on a few.
        java.util.Set<Integer> seen=new java.util.HashSet<>();
        for(int i=0;i<200;i++)seen.add(Writers.automatic(Writers.byKey("device-"+i)));
        assertEquals(8,seen.size());
    }

    /** Every paper both apps draw on: the phone's ten, the PC's, and each washed at its loudest in every colour. */
    private static int[] papers() {
        int[] plain={0xFFFFFFFF,0xFFFDFCFA,0xFFFBFAF6,0xFFF8F4E9,0xFFF2EBDB,0xFFE9E0CA,0xFFDACEB3,0xFF4A4A43,0xFF262B28,0xFF121413,0xFFFBFAF7};
        int[] all=new int[plain.length*Tint.count()*3];
        int at=0;
        for(int paper:plain) {
            boolean dark=Writers.contrast(paper,0xFFFFFFFF)>Writers.contrast(paper,0xFF000000);
            for(int colour=0;colour<Tint.count();colour++)
                for(float weight:new float[]{0f,Tint.weigh(0.12f,Tint.TONES.length-1,0.72f),0.72f})
                    all[at++]=Tint.over(colour,paper,weight,dark);
        }
        return all;
    }
    @Test public void everyColourReadsOnEveryPaperAtEveryStrength() {
        for(int paper:papers())
            for(int colour=1;colour<Tint.count();colour++) {
                int ink=Writers.ink(colour,paper);
                assertTrue(Tint.NAMES[colour]+" on "+Integer.toHexString(paper)+": "+Writers.contrast(ink,paper),
                    Writers.contrast(ink,paper)>=Writers.READABLE);
            }
    }
    @Test public void aColourThatAlreadyReadsIsLeftAsItIs() {
        // Blue on white reads already: it is the palette's blue, not a darker one.
        assertEquals(Tint.of(6,false),Writers.ink(6,0xFFFFFFFF));
        assertEquals(0,Writers.ink(Tint.NONE,0xFFFFFFFF));
    }
    @Test public void theEightStayEightDifferentColoursOnThePadsPaper() {
        java.util.Set<Integer> inks=new java.util.HashSet<>();
        for(int colour=1;colour<Tint.count();colour++)inks.add(Writers.ink(colour,0xFFFBFAF6));
        assertEquals(8,inks.size());
    }
}
