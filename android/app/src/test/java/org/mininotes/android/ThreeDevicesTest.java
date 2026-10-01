package org.mininotes.android;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/**
 * Three of the owner's devices passing one note, the way the Pro, the GrapheneOS phone and the PC did on 2026-09-29.
 *
 * <p>What two devices last agreed on is kept per pair. Most of what reaches one device came from another that took it
 * first - the PC sends the phone what it took from the Pro - and the phone's agreement with the Pro stays behind. The
 * Pro's next writing was weighed against that, the phone's copy of the Pro's own earlier text looked like writing of the
 * phone's, and the two were put together: a line the Pro had rewritten came back beside the new one, and a revision
 * nobody wrote went out from a phone nobody touched. A device here is what the notebook keeps about one note - the text,
 * its count, the agreement with each other device, every version kept - and nothing else, as in ConvergenceTest.
 */
public class ThreeDevicesTest {

    private static final class Device {
        final String name; String text; long revision;
        final Map<String,Long> agreed=new HashMap<>();
        /** Every version kept, oldest first: [revision, source, text]; source is empty for this device's own. */
        final List<Object[]> versions=new ArrayList<>();
        /** Whether it says which texts its note held before, as a build with {@link Parcel.Sent#history} does. */
        final boolean tells;
        Device(String name,String text,long revision,boolean tells) {
            this.name=name;this.text=text;this.revision=revision;this.tells=tells;
            versions.add(new Object[]{revision,"",text});
        }
        void writes(String now){text=now;revision++;}
        void keep(long revision,String source,String text) {
            Object[] last=versions.get(versions.size()-1);
            if((Long)last[0]==revision&&last[1].equals(source)&&last[2].equals(text))return;
            versions.add(new Object[]{revision,source,text});
        }
        /** NoteStore.textAt: exactly that revision, this device's own or from that device, else the nearest before. */
        String textAt(long revision,String from) {
            for(int at=versions.size()-1;at>=0;at--){Object[] one=versions.get(at);
                if((Long)one[0]==revision&&(one[1].equals("")||one[1].equals(from)))return (String)one[2];}
            String found=null;long best=-1;
            for(Object[] one:versions)if((Long)one[0]<=revision&&(Long)one[0]>=best){best=(Long)one[0];found=(String)one[2];}
            return found;
        }
        /** NoteStore.history: what it says now, then what it kept, newest first. */
        List<String> history() {
            List<String> out=new ArrayList<>();out.add(Arriving.trace(text));
            for(int at=versions.size()-1;at>=0;at--){String one=Arriving.trace((String)versions.get(at)[2]);if(!out.contains(one))out.add(one);}
            return out;
        }
        Set<String> heldHere() {
            Set<String> out=new HashSet<>();
            for(Object[] one:versions)out.add(Arriving.trace((String)one[2]));
            return out;
        }
    }

    /** One note sent, weighed and answered, the way Post.send, NoteStore.landed and the answer do it. */
    private static Arriving.Decision send(Device from,Device to,boolean answered) {
        long basedOn=from.agreed.getOrDefault(to.name,0L);
        from.keep(from.revision,"",from.text);                           // what went is kept as it went
        long base=Arriving.agreed(to.agreed.getOrDefault(from.name,0L),basedOn);
        List<String> history=from.tells?from.history():null;
        boolean theyHadMine=history!=null&&history.contains(Arriving.trace(to.text));
        boolean iHadTheirs=to.heldHere().contains(Arriving.trace(from.text));
        Arriving.Decision said=Arriving.weigh(to.text,to.revision,to.textAt(base,from.name),base,from.text,from.revision,
            theyHadMine,iHadTheirs);
        to.keep(from.revision,from.name,from.text);                       // what arrived is kept as it arrived
        if(said.what!=Arriving.What.OLDER){to.text=said.text;to.revision=said.revision;}
        boolean took=said.what==Arriving.What.NEW||said.what==Arriving.What.NEWER;
        if(took)to.agreed.merge(from.name,from.revision,Math::max);
        if(answered&&took)from.agreed.merge(to.name,from.revision,Math::max);
        return said;
    }

    private static Device[] agreedOn(String text,long revision,boolean tell) {
        Device pro=new Device("pro",text,revision,tell), pc=new Device("pc",text,revision,tell),
            graphene=new Device("graphene",text,revision,tell);
        for(Device one:new Device[]{pro,pc,graphene})for(Device other:new Device[]{pro,pc,graphene})
            if(one!=other)one.agreed.put(other.name,revision);
        return new Device[]{pro,pc,graphene};
    }

    @Test public void whatTheProWroteOnTopOfWhatThePcPassedOnIsTakenWithNothingPutTogether() {
        Device[] all=agreedOn("Transfer\nfirst line",60,true);
        Device pro=all[0], pc=all[1], graphene=all[2];
        pro.writes("Transfer\nsecond line");
        send(pro,pc,true);                                                // the phone is out of reach
        send(pc,graphene,true);                                           // and the PC passes it on
        assertEquals("Transfer\nsecond line",graphene.text);
        assertEquals("the phone's agreement with the Pro stays where it was",60L,(long)graphene.agreed.get("pro"));

        pro.writes("Transfer\nthird line");
        Arriving.Decision said=send(pro,graphene,true);
        assertEquals(Arriving.What.NEWER,said.what);
        assertEquals("Transfer\nthird line",graphene.text);
        assertFalse("the line the Pro rewrote came back",graphene.text.contains("second line"));
        assertEquals("a revision nobody wrote",pro.revision,graphene.revision);
    }

    @Test public void theSameWithBuildsThatDoNotSayWhatTheyHeldIsTheFaultAsItWasSeen() {
        // Pinned so the cause stays in view: counted alone, the phone puts the Pro's own two texts together.
        Device[] all=agreedOn("Transfer\nfirst line",60,false);
        Device pro=all[0], pc=all[1], graphene=all[2];
        pro.writes("Transfer\nsecond line");
        send(pro,pc,true);send(pc,graphene,true);
        pro.writes("Transfer\nthird line");
        Arriving.Decision said=send(pro,graphene,true);
        assertEquals(Arriving.What.MERGED,said.what);
        assertTrue(graphene.text.contains("second line")&&graphene.text.contains("third line"));
        assertEquals(pro.revision+1,graphene.revision);
    }

    @Test public void aLineTheProDeletedStaysDeleted() {
        Device[] all=agreedOn("Transfer\nfirst line",60,true);
        Device pro=all[0], pc=all[1], graphene=all[2];
        pro.writes("Transfer\nfirst line\nmore");
        send(pro,pc,true);send(pc,graphene,true);
        pro.writes("Transfer\nfirst line");
        send(pro,graphene,true);
        assertEquals("Transfer\nfirst line",graphene.text);
    }

    @Test public void theProsOlderTextPassedOnLateIsBehindWhatIsHere() {
        // The Pro's newer text reached the phone first; the PC passes on the older one later, and believes the phone
        // has less than it does because the phone's answer never reached it. Even a build that does not say what it
        // held is known here to be behind: the phone kept that text when it came.
        Device[] all=agreedOn("Transfer\nfirst line",60,false);
        Device pro=all[0], pc=all[1], graphene=all[2];
        pro.writes("Transfer\nsecond line");
        send(pro,pc,true);
        send(pro,graphene,true);
        pro.writes("Transfer\nthird line");
        send(pro,graphene,true);
        pc.agreed.put("graphene",59L);
        Arriving.Decision said=send(pc,graphene,true);
        assertEquals(Arriving.What.OLDER,said.what);
        assertEquals("Transfer\nthird line",graphene.text);
    }

    @Test public void writingOnBothSidesIsStillPutTogether() {
        Device[] all=agreedOn("Transfer\nfirst line\nlast line",60,true);
        Device pro=all[0], pc=all[1], graphene=all[2];
        pro.writes("Transfer\nfirst line, Pro\nlast line");
        send(pro,pc,true);send(pc,graphene,true);
        graphene.writes("Transfer\nfirst line, Pro\nlast line, phone");
        pro.writes("Transfer\nfirst line, Pro again\nlast line");
        Arriving.Decision said=send(pro,graphene,true);
        assertEquals(Arriving.What.MERGED,said.what);
        assertTrue(graphene.text.contains("last line, phone"));
        assertTrue(graphene.text.contains("first line, Pro again"));
    }

    @Test public void aNoteWrittenBackToWhatItSaidBeforeIsStillAWriting() {
        // Counted, it follows on from what both last had, and is taken, though the phone once held those very words.
        Device[] all=agreedOn("Milk",5,true);
        Device pro=all[0], graphene=all[2];
        pro.writes("Milk\nEggs");
        send(pro,graphene,true);
        pro.writes("Milk");
        send(pro,graphene,true);
        assertEquals("Milk",graphene.text);
    }

    @Test public void theTextsHeldBeforeTravelAndABuildFromBeforeIsNotConfused() throws Exception {
        List<String> held=Arrays.asList(Arriving.trace("now"),Arriving.trace("before"));
        List<Parcel.Member> members=Collections.singletonList(new Parcel.Member("key","Mx@1","One",2,7L));
        Parcel.Sent sent=new Parcel.Sent("c","C","b","B","Title","now",true,members,"PAGE","n",true,4L,true,
            new ArrayList<>(),9L,held);
        Parcel.Sent back=Parcel.open(Parcel.wrap(sent));
        assertNotNull(back);
        assertEquals(held,back.history);
        assertEquals("now",back.body);
        assertEquals(1,back.members.size());
        assertEquals("no key is said where none was known",0,back.members.get(0).agreement.length);
        assertEquals(4L,back.basedOn);

        // Not said: null, and weighed by the counts alone.
        Parcel.Sent none=Parcel.open(Parcel.wrap(new Parcel.Sent("c","C","b","B","Title","now",true,members,"PAGE","n",
            true,4L,true,new ArrayList<>(),9L)));
        assertNotNull(none);
        assertNull(none.history);

        // Cut short: the note still reads, and the list is taken as not said.
        byte[] whole=Parcel.wrap(sent);
        Parcel.Sent cut=Parcel.open(Arrays.copyOf(whole,whole.length-5));
        assertNotNull(cut);
        assertNull(cut.history);
        assertEquals("now",cut.body);
    }

    @Test public void aTraceNamesTheWordsAndNothingElse() {
        assertEquals(32,Arriving.trace("x").length());
        assertEquals(Arriving.trace("Milk\nEggs"),Arriving.trace("Milk\nEggs"));
        assertNotEquals(Arriving.trace("Milk\nEggs"),Arriving.trace("Milk\nEggs\n"));
        assertEquals(Arriving.trace(""),Arriving.trace(null));
    }
}
