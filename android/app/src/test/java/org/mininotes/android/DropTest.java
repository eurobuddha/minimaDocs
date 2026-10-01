package org.mininotes.android;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** Files sent on their own: the offer's format and its bounds, who is asked, and what each state says. See Drop. */
public class DropTest {
    private static final String A="0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0", B="11111111-2222-3333-4444-555555555555";

    private static Enclosure.Listed file(String id,String name,long size){return new Enclosure.Listed(id,name,"image/png",size,"{\"v\":\"1\"}");}

    // ---- the format -------------------------------------------------------------------------------------------

    @Test public void anOfferReadsBackAsWhatItListed() {
        List<Enclosure.Listed> back=Drop.open(Drop.wrap(Arrays.asList(file(A,"ferry.png",1234),file(B,"tickets.pdf",99))));
        assertNotNull(back);assertEquals(2,back.size());
        assertEquals(A,back.get(0).id);assertEquals("ferry.png",back.get(0).name);assertEquals("image/png",back.get(0).kind);
        assertEquals(1234,back.get(0).bytes);assertEquals("{\"v\":\"1\"}",back.get(0).manifest);
        assertEquals("tickets.pdf",back.get(1).name);
    }

    @Test public void onlyWhatCanBeFetchedIsOffered() {
        for(List<Enclosure.Listed> bad:Arrays.<List<Enclosure.Listed>>asList(
                Collections.<Enclosure.Listed>emptyList(),
                Collections.singletonList(new Enclosure.Listed(A,"x","k",10,"")),              // said nowhere
                Collections.singletonList(file(A,"big",Enclosure.MOST+1)),                    // over the ceiling
                Collections.singletonList(file(A,"empty",0)),
                Collections.singletonList(file("../escape","x",10)))) {                        // never a path
            try{Drop.wrap(bad);fail("offered "+bad.size());}catch(IllegalArgumentException expected){}
        }
        List<Enclosure.Listed> many=new ArrayList<>();
        for(int at=0;at<=Drop.FILES_MOST;at++)many.add(file(java.util.UUID.randomUUID().toString(),"f"+at,10));
        try{Drop.wrap(many);fail("more than "+Drop.FILES_MOST);}catch(IllegalArgumentException expected){}
        assertNotNull(Drop.open(Drop.wrap(many.subList(0,Drop.FILES_MOST))));
    }

    @Test public void twentyAtTheLargestStillFitOneEnvelope() {
        // A manifest for a file at the ceiling names one piece per 192 KB: about ninety hashes, and its key.
        StringBuilder manifest=new StringBuilder("{\"chunks\":[");
        for(int at=0;at<90;at++)manifest.append("\"0x").append(String.format("%064x",at)).append("\",");
        manifest.append("\"end\"],\"sources\":[\"Mx").append("A".repeat(400)).append("@203.0.113.7:9601\"]}");
        List<Enclosure.Listed> full=new ArrayList<>();
        for(int at=0;at<Drop.FILES_MOST;at++)
            full.add(new Enclosure.Listed(java.util.UUID.randomUUID().toString(),"n".repeat(Drop.NAME_MOST),"k",Enclosure.MOST,manifest.toString()));
        byte[] made=Drop.wrap(full);
        assertTrue(made.length<=Envelope.MAX_TEXT);
        assertEquals(Drop.FILES_MOST,Drop.open(made).size());
    }

    @Test public void aBrokenOfferIsNoOffer() throws Exception {
        byte[] good=Drop.wrap(Collections.singletonList(file(A,"ferry.png",1234)));
        assertNull("trailing bytes",Drop.open(Arrays.copyOf(good,good.length+1)));
        for(int cut=0;cut<good.length;cut++)assertNull("cut at "+cut,Drop.open(Arrays.copyOf(good,cut)));
        byte[] later=good.clone();later[Drop.MAGIC.length]=2;
        assertNull("a later format is not read as this one",Drop.open(later));
        byte[] other=good.clone();other[3]='9';
        assertNull(Drop.open(other));
        assertNull(Drop.open(null));
        // The same file twice, and a count that lies.
        assertNull(Drop.open(raw(2,new String[]{A,A})));
        assertNull(Drop.open(raw(3,new String[]{A,B})));
        assertNull(Drop.open(raw(-1,new String[]{})));
        assertNull(Drop.open(raw(Drop.FILES_MOST+1,new String[]{A})));
    }

    /** An offer written by hand, saying it holds {@code count} files and holding these. */
    private static byte[] raw(int count,String[] ids) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.write(Drop.MAGIC);out.writeByte(Drop.FORMAT);out.writeInt(count);
        for(String id:ids) {
            for(String field:new String[]{id,"n","k"}){byte[] b=field.getBytes("UTF-8");out.writeInt(b.length);out.write(b);}
            out.writeLong(10);byte[] m="{}".getBytes("UTF-8");out.writeInt(m.length);out.write(m);
        }
        return bytes.toByteArray();
    }

    @Test public void aFieldPastItsBoundIsRefusedBeforeAnythingIsMade() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.write(Drop.MAGIC);out.writeByte(Drop.FORMAT);out.writeInt(1);out.writeInt(Integer.MAX_VALUE);
        assertNull(Drop.open(bytes.toByteArray()));
        try{Drop.wrap(Collections.singletonList(new Enclosure.Listed(A,"n".repeat(Drop.NAME_MOST+1),"k",10,"{}")));fail();}
        catch(IllegalArgumentException expected){}
    }

    // ---- nothing else reads it, and it reads nothing else ------------------------------------------------------

    @Test public void anOfferIsNotANoteAnAnswerAHelloOrSomethingCarried() throws Exception {
        byte[] offer=Drop.wrap(Collections.singletonList(file(A,"ferry.png",1234)));
        assertEquals(0,Receipt.open(offer));
        assertNull(Hello.open(offer));
        assertNull(Courier.open(offer));
        // And the other way round: nothing else that travels is taken for an offer.
        assertNull(Drop.open(Parcel.wrap(new Parcel.Sent("c","C","b","B","","Milk",true))));
        assertNull(Drop.open(Receipt.wrap(Receipt.DROP_HAVE)));
        assertNull(Drop.open(Courier.bring(Courier.NOTE,new byte[]{1,2,3})));
    }

    /**
     * A build from before this reads what it does not know as a note written the oldest way, which is why an offer
     * goes only to a device that said it takes files. What it is told by is five bytes every build since answers
     * reads as a later build's answer - a number it does not act on - and never as a note.
     */
    @Test public void whatSaysADeviceTakesFilesIsSafeForAnOlderBuild() {
        for(int what:new int[]{Receipt.TAKES_FILES,Receipt.DROP_HAVE,Receipt.DROP_REFUSED,Receipt.DROP_MISSING}) {
            int read=Receipt.open(Receipt.wrap(what));
            assertEquals(what,read);
            // Not one of the numbers an older build does something with.
            for(int known:new int[]{Receipt.HAVE,Receipt.TOOK,Receipt.ASK,Receipt.COLLECTED,Receipt.COLLECTED_ANSWER,Receipt.FILE_HERE,Receipt.FILE_MISSING})
                assertNotEquals(known,read);
            assertNull(Receipt.leftScope(read));assertNull(Receipt.removedScope(read));
            assertNull(Parcel.open(Receipt.wrap(what)));
        }
    }

    /** The hello says it too, after everything a build from before reads - so that build reads the same hello. */
    @Test public void aHelloSaysItTakesFilesWhereAnOlderBuildReadsNothing() throws Exception {
        byte[] key=new byte[33];key[0]=2;
        Hello.Said now=new Hello.Said("Ana's phone","MxFixture@203.0.113.7:9601",key,key,"","",false);
        byte[] made=Hello.wrap(now);
        assertTrue(Hello.open(made).files);
        // What a build from before wrote: the same, with nothing after the target.
        byte[] before=Arrays.copyOf(made,made.length-1);
        Hello.Said old=Hello.open(before);
        assertNotNull(old);assertFalse(old.files);
        assertEquals("Ana's phone",old.name);assertEquals("MxFixture@203.0.113.7:9601",old.address);
        assertFalse(Hello.open(Hello.wrap(new Hello.Said("x","MxFixture@203.0.113.7:9601",key,key,"","",false,false))).files);
    }

    // ---- who is asked, and what becomes of a sending ------------------------------------------------------------

    @Test public void theOwnersOwnDevicesAreTakenAtOnceAndAnybodyElseIsAsked() {
        assertEquals(Drop.FETCHING,Drop.arriving(true,true));
        assertEquals(Drop.ASKING,Drop.arriving(true,false));
        assertEquals(0,Drop.arriving(false,true));
        assertEquals(0,Drop.arriving(false,false));
    }

    @Test public void onlyTheOtherDevicesAnswerSettlesASending() {
        assertEquals(Drop.DELIVERED,Drop.after(Drop.WAITING,Receipt.DROP_HAVE));
        assertEquals(Drop.DELIVERED,Drop.after(Drop.SENDING,Receipt.DROP_HAVE));
        assertEquals(Drop.REFUSED,Drop.after(Drop.WAITING,Receipt.DROP_REFUSED));
        assertEquals("cannot be had is not an answer",Drop.WAITING,Drop.after(Drop.WAITING,Receipt.DROP_MISSING));
        assertEquals(Drop.WAITING,Drop.after(Drop.WAITING,Receipt.HAVE));
        // Settled is settled: a late refusal does not undo a delivery, nor the other way round.
        assertEquals(Drop.DELIVERED,Drop.after(Drop.DELIVERED,Receipt.DROP_REFUSED));
        assertEquals(Drop.REFUSED,Drop.after(Drop.REFUSED,Receipt.DROP_HAVE));
        assertTrue(Drop.goingOn(Drop.SENDING));assertTrue(Drop.goingOn(Drop.WAITING));
        assertFalse(Drop.goingOn(Drop.DELIVERED));assertFalse(Drop.goingOn(Drop.REFUSED));
    }

    @Test public void anOfferThatComesAgainIsAnsweredAgainOnceSettled() {
        assertEquals(Receipt.DROP_HAVE,Drop.answerAgain(Drop.HERE));
        assertEquals(Receipt.DROP_REFUSED,Drop.answerAgain(Drop.REFUSED));
        assertEquals(0,Drop.answerAgain(Drop.ASKING));
        assertEquals(0,Drop.answerAgain(Drop.FETCHING));
    }

    @Test public void piecesGoToARelayOnlyWhereNoDoorCanBeReached() {
        assertFalse("only between the owner's devices, never",Drop.upToRelays(false,false,false));
        assertFalse("on the same network, door to door",Drop.upToRelays(true,true,false));
        assertFalse("a door proved in public",Drop.upToRelays(true,false,true));
        assertTrue(Drop.upToRelays(true,false,false));
    }

    @Test public void aFileThatCannotBeHadIsSaidAtOnceAndThenNowAndThen() {
        assertTrue(Drop.sayMissing(1,true));
        assertFalse(Drop.sayMissing(2,true));assertFalse(Drop.sayMissing(3,true));
        assertTrue(Drop.sayMissing(4,true));assertTrue(Drop.sayMissing(8,true));
        assertFalse(Drop.sayMissing(0,true));
        for(int tries=1;tries<10;tries++)assertFalse("nothing to send up to",Drop.sayMissing(tries,false));
        assertTrue(Drop.due(0,0,5));assertFalse(Drop.due(1000,1,1000+59_000));assertTrue(Drop.due(1000,1,1000+60_000));
    }

    // ---- words -------------------------------------------------------------------------------------------------

    @Test public void eachStateSaysOneThing() {
        assertEquals("Sending…",Drop.state(Drop.SENDING,"Ana",true));
        assertEquals("Waiting for Ana",Drop.state(Drop.WAITING,"Ana",true));
        assertEquals("Waiting for Ana to update Mininotes",Drop.state(Drop.WAITING,"Ana",false));
        assertEquals("Delivered",Drop.state(Drop.DELIVERED,"Ana",true));
        assertEquals("Refused",Drop.state(Drop.REFUSED,"Ana",true));
        assertEquals("Waiting for your answer",Drop.state(Drop.ASKING,"Ana",true));
        assertEquals("",Drop.state(Drop.HERE,"Ana",true));
        assertEquals("Ana wants to send you 3 files (2.1 MB)",Drop.asking("Ana",3,2_202_010));
        assertEquals("Ana wants to send you a file (12 KB)",Drop.asking("Ana",1,12_288));
        assertEquals("Ana sent you a file",Drop.arrived("Ana",1));
        assertEquals("The 2 files reached Ana",Drop.answered("Ana",2,Drop.DELIVERED));
        assertEquals("Ana refused the file",Drop.answered("Ana",1,Drop.REFUSED));
        assertEquals("Sending 2 files to Ana…",Drop.sending("Ana",2));
        assertEquals("Nothing yet",Drop.boxDetail(0,0));
        assertEquals("3 files",Drop.boxDetail(0,3));
        assertEquals("2 new",Drop.boxDetail(2,3));
        assertEquals("Sent. Waiting for Ana to take them",Drop.afterSending("Ana",true,true));
        assertTrue(Drop.notSent(Collections.singletonList(Given.refusal("film.mov",Drop.TOO_BIG))).startsWith("This was not sent:"));
        assertTrue(Drop.notSent(Collections.singletonList(Given.refusal("film.mov",Drop.TOO_BIG))).contains("film.mov – larger than 16 MB"));
    }

    @Test public void theOwnersDevicesComeFirstAndSaySo() {
        List<Drop.Device> listed=Drop.choices(Arrays.asList(new Drop.Device("x1","Zoe's laptop",false),
            new Drop.Device("x2","Work laptop",true),new Drop.Device("x3","ana's phone",false),new Drop.Device("x4","Home PC",true)));
        assertEquals(Arrays.asList("Home PC","Work laptop","ana's phone","Zoe's laptop"),
            Arrays.asList(listed.get(0).name,listed.get(1).name,listed.get(2).name,listed.get(3).name));
        assertEquals("My device",listed.get(0).under());assertEquals("",listed.get(2).under());
    }

    // ---- saying a device takes files ------------------------------------------------------------------------

    @Test public void takingFilesIsSaidBackUnlessItWasSaidAMinuteAgo() {
        long now=1_000_000_000L;
        assertTrue("never said to them",Drop.sayBack(0,now));
        assertTrue("said hours ago, when they could not hear it",Drop.sayBack(now-3*60*60*1000L,now));
        assertTrue(Drop.sayBack(now-Drop.SAY_BACK,now));
        assertFalse("said a moment ago: they are only answering it",Drop.sayBack(now-5_000,now));
        assertTrue("a clock put back: better once too often than never",Drop.sayBack(now+5_000,now));
    }

    /**
     * What stopped a phone's files going to the other phone: B updated first and said it takes files to A, still on a
     * build from before, which read nothing; then A updated and said it to B - and B, having said it once this run,
     * did not say it again. A waited "for B to update" for as long as B's process lived. Each device here says it
     * once at its start and then only back, by the rule each end uses; a message is heard at once or not at all.
     */
    @Test public void aDeviceThatSaidItWhileTheOtherCouldNotHearSaysItAgainWhenAsked() {
        long[] toldAt={0,0};boolean[] knows={false,false};int[] said={0};
        long start=1_000_000_000L;
        // B, updated first, says it to A at its start; A is on a build from before and cannot hear it.
        toldAt[1]=start;said[0]++;
        // Hours later A is updated and says it to B at its start; from there each end answers by the rule.
        long now=start+2*60*60*1000L;
        toldAt[0]=now;said[0]++;
        int to=1;
        while(said[0]<10) {
            knows[to]=true;
            if(!Drop.sayBack(toldAt[to],now))break;
            toldAt[to]=now;said[0]++;to=1-to;now+=200;
        }
        assertTrue("B knows A takes files",knows[1]);
        assertTrue("and A knows B does, so what waited for B goes",knows[0]);
        assertEquals("said three times in all, and then quiet",3,said[0]);
    }
}
