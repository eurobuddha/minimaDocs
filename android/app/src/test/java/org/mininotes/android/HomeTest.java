package org.mininotes.android;

import static org.junit.Assert.*;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/** The PC's home for its owner's phones, without a network: the formats, their bounds, and the rules. See Home. */
public class HomeTest {
    private static KeyPair homeKey, phoneSigning, phoneAgreement, otherSigning, strangerSigning;
    private static String phone, other, stranger;
    private static final long NOW=1_800_000_000_000L;

    @BeforeClass public static void keys() throws Exception {
        homeKey=Envelope.keys();phoneSigning=Envelope.keys();phoneAgreement=Envelope.keys();otherSigning=Envelope.keys();strangerSigning=Envelope.keys();
        phone=Courier.hex(Envelope.fingerprint(phoneSigning.getPublic()));
        other=Courier.hex(Envelope.fingerprint(otherSigning.getPublic()));
        stranger=Courier.hex(Envelope.fingerprint(strangerSigning.getPublic()));
    }

    /**
     * A shelf in memory that keeps what the notebook's does: the same bytes once, the same note sealed again
     * replacing what was held, room by {@link Home#room}, and nothing older than {@link Home#KEPT_FOR}.
     */
    static final class Memory implements Home.Shelf {
        final List<Home.Held> all=new ArrayList<>();
        long now=NOW;
        @Override public synchronized boolean holdFor(Home.Held one) {
            all.removeIf(held->Home.expired(held.kept,one.kept));
            for(Home.Held held:all)if(held.id.equals(one.id))return true;
            all.removeIf(held->held.sender.equals(one.sender)&&held.recipient.equals(one.recipient)&&held.page.equals(one.page)
                &&held.revision==one.revision&&held.sealed==one.sealed);
            int forThem=0;long forThemBytes=0,allBytes=0;
            for(Home.Held held:all){allBytes+=held.bytes.length;if(held.recipient.equals(one.recipient)){forThem++;forThemBytes+=held.bytes.length;}}
            if(!Home.room(forThem,forThemBytes,all.size(),allBytes,one.bytes.length))return false;
            all.add(one);return true;
        }
        @Override public synchronized List<Home.Held> heldFor(String recipient,int most,long bytes) {
            all.removeIf(held->Home.expired(held.kept,now));
            List<Home.Held> theirs=new ArrayList<>();
            for(Home.Held held:all)if(held.recipient.equals(recipient))theirs.add(held);
            return Home.within(theirs,most,bytes);
        }
        @Override public synchronized int countHeldFor(String recipient) {
            int many=0;for(Home.Held held:all)if(recipient==null||held.recipient.equals(recipient))many++;return many;
        }
        @Override public synchronized int letGoFor(String recipient,Collection<String> ids) {
            int before=all.size();all.removeIf(held->held.recipient.equals(recipient)&&ids.contains(held.id));return before-all.size();
        }
    }

    /** The home: paired with the phone, the other device and nobody else; the phone alone is "My device". */
    private static Home.Host host(Memory shelf) {
        Home.Host host=new Home.Host("0x"+"AB".repeat(20));
        host.notebook(new Home.Notebook(homeKey.getPrivate(),Set.of(phone,other),Set.of(phone),shelf));
        return host;
    }

    private static byte[] ask(byte[] text,KeyPair by) throws Exception {return Envelope.seal(new byte[16],0,NOW,text,by,homeKey.getPublic());}
    private static byte[] sealedFor(KeyPair by,long revision,String text) throws Exception {
        byte[] page=new byte[16];page[0]=7;
        return Envelope.seal(page,revision,NOW,text.getBytes(java.nio.charset.StandardCharsets.UTF_8),by,phoneAgreement.getPublic());
    }
    private static byte[] whose(String hex){byte[] out=new byte[32];for(int at=0;at<32;at++)out[at]=(byte)Integer.parseInt(hex.substring(at*2,at*2+2),16);return out;}
    private static Home.Answer said(Home.Host host,byte[] ask,byte[] inner,long now){return Home.answered(host.said(ask,inner,now));}
    private static Home.Answer deposit(Home.Host host,KeyPair by,String forWhom,byte[] inner,long now) throws Exception {
        return said(host,ask(Home.deposit(whose(forWhom),inner),by),inner,now);
    }
    private static Home.Answer pull(Home.Host host,KeyPair by,long now) throws Exception {return said(host,ask(Home.pull(Home.PULL_MOST),by),new byte[0],now);}

    @Test public void whatIsAskedReadsBackAndNothingElseDoes() {
        byte[] inner={1,2,3};
        Home.Ask left=Home.ask(Home.deposit(new byte[32],inner));
        assertEquals(Home.DEPOSIT,left.kind);assertEquals(3,left.length);assertArrayEquals(Home.sha256(inner),left.hash);
        assertEquals(Home.PULL_MOST,Home.ask(Home.pull(99)).most);
        assertEquals(2,Home.ask(Home.ack(List.of(new byte[32],new byte[32]))).ids.size());
        byte[] cut=Home.pull(3);
        assertNull(Home.ask(Arrays.copyOf(cut,cut.length-1)));
        byte[] longer=Arrays.copyOf(cut,cut.length+1);
        assertNull("nothing may follow",Home.ask(longer));
        byte[] other=cut.clone();other[4]=9;
        assertNull(Home.ask(other));
        assertNull(Home.ask(Receipt.wrap(Receipt.ASK)));
        assertThrows(IllegalArgumentException.class,()->Home.deposit(new byte[31],inner));
    }

    @Test public void anAnswerReadsBackWithinItsBounds() {
        Home.Answer read=Home.answered(Home.answer(Home.OK,3,List.of(new byte[]{9},new byte[]{8,7})));
        assertEquals(Home.OK,read.status);assertEquals(3,read.more);assertEquals(2,read.items.size());
        assertArrayEquals(new byte[]{8,7},read.items.get(1));
        assertEquals(Home.NOT_COLLECTING,Home.answered(Home.answer(Home.NOT_COLLECTING,0,null)).status);
        byte[] whole=Home.answer(Home.OK,0,List.of(new byte[]{9}));
        assertNull(Home.answered(Arrays.copyOf(whole,whole.length-1)));
        byte[] unknown=Home.answer(Home.OK,0,null);unknown[4]=42;
        assertNull("a status nobody says",Home.answered(unknown));
        assertNull("things handed over with a no",Home.answered(Home.answer(Home.REFUSED,0,List.of(new byte[]{1}))));
    }

    @Test public void theOutsideOfASealedNoteSaysWhoSealedItAndWhichNote() throws Exception {
        Home.About about=Home.about(sealedFor(otherSigning,12,"not readable here"));
        assertEquals(other,about.sender);assertEquals(12,about.revision);assertTrue(about.page.startsWith("07"));
        assertEquals("what is sealed inside, and the tag",17+16,about.sealed);
        // Sealed again, the envelope can be a byte longer or shorter; what is inside is not.
        assertEquals(about.sealed,Home.about(sealedFor(otherSigning,12,"not readable here")).sealed);
        assertNull(Home.about("MNP1 and nothing".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        assertNull(Home.about(new byte[3]));
    }

    @Test public void aThingLeftForAPhoneThatCollectsIsHandedToItOnceAndLetGo() throws Exception {
        Memory shelf=new Memory();Home.Host host=host(shelf);
        byte[] inner=sealedFor(otherSigning,1,"for the phone");
        assertEquals("nobody collecting yet, so the relays",Home.NOT_COLLECTING,deposit(host,otherSigning,phone,inner,NOW).status);
        assertEquals(0,shelf.all.size());
        Home.Answer first=pull(host,phoneSigning,NOW);
        assertEquals(Home.OK,first.status);assertTrue(first.items.isEmpty());
        assertTrue(host.collecting(phone,NOW+Home.COLLECTING));assertFalse(host.collecting(phone,NOW+Home.COLLECTING+1));
        assertEquals(Home.OK,deposit(host,otherSigning,phone,inner,NOW+1).status);
        assertEquals("the same bytes twice are kept once",Home.OK,deposit(host,otherSigning,phone,inner,NOW+2).status);
        assertEquals(1,host.holding());
        byte[] again=sealedFor(otherSigning,1,"for the phone");
        assertEquals(Home.OK,deposit(host,otherSigning,phone,again,NOW+3).status);
        assertEquals("sealed again, it replaces what was held",1,host.holding());
        Home.Answer collected=pull(host,phoneSigning,NOW+4);
        assertEquals(1,collected.items.size());assertArrayEquals(again,collected.items.get(0));assertEquals(0,collected.more);
        assertEquals(Home.OK,said(host,ask(Home.ack(List.of(Home.sha256(again))),phoneSigning),new byte[0],NOW+5).status);
        assertEquals(0,host.holding());
        assertTrue(pull(host,phoneSigning,NOW+6).items.isEmpty());
        assertEquals("Your phones collect from this PC, with no relay between.",Home.said(host.holding(),host.collectingNow(NOW+6)));
    }

    @Test public void onlyPairedDevicesLeaveThingsAndOnlyForTheOwnersOwnDevices() throws Exception {
        Memory shelf=new Memory();Home.Host host=host(shelf);
        pull(host,phoneSigning,NOW);
        assertEquals("a stranger",Home.REFUSED,deposit(host,strangerSigning,phone,sealedFor(strangerSigning,1,"x"),NOW).status);
        assertEquals("for a paired device that is not 'My device'",Home.REFUSED,deposit(host,phoneSigning,other,sealedFor(phoneSigning,1,"x"),NOW).status);
        assertEquals("for a stranger",Home.REFUSED,deposit(host,otherSigning,stranger,sealedFor(otherSigning,1,"x"),NOW).status);
        assertEquals("something somebody else sealed",Home.REFUSED,deposit(host,otherSigning,phone,sealedFor(strangerSigning,1,"x"),NOW).status);
        assertEquals("for itself",Home.REFUSED,deposit(host,phoneSigning,phone,sealedFor(phoneSigning,1,"x"),NOW).status);
        byte[] inner=sealedFor(otherSigning,1,"x");
        assertEquals("a hash that is not of what follows",Home.REFUSED,said(host,ask(Home.deposit(whose(phone),inner),otherSigning),sealedFor(otherSigning,2,"y"),NOW).status);
        assertEquals("a paired device that is not 'My device' collects nothing",Home.REFUSED,pull(host,otherSigning,NOW).status);
        assertEquals(Home.REFUSED,pull(host,strangerSigning,NOW).status);
        assertEquals("sealed to somebody else",Home.REFUSED,said(host,Envelope.seal(new byte[16],0,NOW,Home.pull(1),phoneSigning,phoneAgreement.getPublic()),new byte[0],NOW).status);
        assertEquals(0,shelf.all.size());
        host.notebook(null);
        assertEquals("locked: not now",Home.BUSY,pull(host,phoneSigning,NOW).status);
        assertEquals("",Home.said(host.holding(),0));
    }

    @Test public void theShelfHasRoomForSoMuchAndKeepsNothingForEver() throws Exception {
        Memory shelf=new Memory();Home.Host host=host(shelf);
        pull(host,phoneSigning,NOW);
        byte[] big=sealedFor(otherSigning,1,"x".repeat(150_000));
        int kept=0;
        for(long revision=1;revision<100;revision++) {
            int status=deposit(host,otherSigning,phone,sealedFor(otherSigning,revision,"x".repeat(150_000)),NOW).status;
            if(status==Home.OK)kept++;else{assertEquals(Home.FULL,status);break;}
        }
        assertTrue(kept>0&&kept<99);
        assertTrue(kept*(long)big.length<=Home.BYTES_FOR_ONE);
        assertTrue(Home.room(0,0,0,0,10));
        assertFalse(Home.room(Home.MOST_FOR_ONE,0,0,0,1));assertFalse(Home.room(0,0,Home.MOST_KEPT,0,1));
        assertFalse(Home.room(0,Home.BYTES_FOR_ONE,0,0,1));assertFalse(Home.room(0,0,0,Home.MOST_BYTES,1));
        // One answer takes a bounded amount, oldest first; the rest is said to be waiting.
        Home.Answer first=pull(host,phoneSigning,NOW);
        assertTrue(first.items.size()<kept);assertEquals(kept-first.items.size(),first.more);
        // A fortnight on, nothing is left.
        shelf.now=NOW+Home.KEPT_FOR+1;
        assertTrue(pull(host,phoneSigning,NOW+Home.KEPT_FOR+1).items.isEmpty());
        assertEquals(0,shelf.all.size());
    }

    @Test public void theAmountHandedOverAtOnceIsBoundedButNeverNothing() {
        List<Home.Held> held=new ArrayList<>();
        for(int at=0;at<5;at++)held.add(new Home.Held("s","r","p",at,400,NOW,new byte[400]));
        assertEquals(2,Home.within(held,2,10_000).size());
        assertEquals(2,Home.within(held,9,800).size());
        assertEquals("one bigger than the whole answer still goes, alone",1,Home.within(held,9,100).size());
        assertEquals(0,held.get(0).revision);
    }

    @Test public void aHomeIsAskedForByALabelMadeFromItsKey() {
        String one=Home.token("0x"+"AB".repeat(20)),two=Home.token("0x"+"AC".repeat(20));
        assertTrue(one.matches("[a-f0-9]{64}"));assertNotEquals(one,two);
        assertEquals(one,Home.token("0x"+"ab".repeat(20)));
    }

    @Test public void aDoorIsReadFromAnAddressOrAHostAndPort() {
        assertEquals(9601,Home.where("MxSomeKey@192.0.2.10:9601").getPort());
        assertEquals("192.0.2.10",Home.where("192.0.2.10:9602").getHostString());
        assertThrows(IllegalArgumentException.class,()->Home.where("MxSomeKey@nowhere"));
    }

    @Test public void aNoIsNotAskedAgainAtOnceAndLongestWhereThereIsNoHome() {
        assertEquals(0,Home.restFor(Home.OK));
        assertTrue(Home.restFor(Home.NOT_COLLECTING)<Home.restFor(Home.BUSY));
        assertTrue(Home.restFor(Home.FULL)<Home.restFor(Home.NO_HOME));
        Home.rest("door-under-test",NOW,Home.restFor(Home.NOT_COLLECTING));
        assertTrue(Home.resting("door-under-test",NOW+59_999));assertFalse(Home.resting("door-under-test",NOW+60_000));
        Home.rest("door-under-test",NOW,0);assertFalse(Home.resting("door-under-test",NOW));
    }

    @Test public void thePcSaysWhatItHoldsInPlainWords() {
        assertEquals("Holding 1 message for your phones until they collect it.",Home.said(1,1));
        assertEquals("Holding 3 messages for your phones until they collect them.",Home.said(3,0));
        assertEquals("Your phones collect from this PC when they can reach it.",Home.said(0,0));
        assertEquals("",Home.said(-1,0));
    }
}
