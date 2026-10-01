package org.mininotes.android;

import static org.junit.Assert.*;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * One person on every device they own: the card, its bounds, whose id two devices keep, and what a card may and may
 * not change. Keys are made for the test; addresses are documentation addresses; the names are nobody's.
 */
public class PersonsTest {
    private static final String EARLY="0000000000000000000000000000000a", LATE="ffffffffffffffffffffffffffffff0b";

    private static Persons.Device device(KeyPair sign,String name,long decided) throws Exception {
        return new Persons.Device(Point.shorten(sign.getPublic()),Point.shorten(Envelope.keys().getPublic()),
            "MxTEST@203.0.113.9:9001",name,Persons.ACTIVE,decided);
    }

    private static Map<String,Long> known(KeyPair... devices) {
        Map<String,Long> all=new HashMap<>();
        for(KeyPair one:devices)all.put(Persons.key(one.getPublic().getEncoded()),0L);
        return all;
    }

    @Test public void aCardSaysWhatItWasGiven() throws Exception {
        KeyPair phone=Envelope.keys(),pc=Envelope.keys();
        Persons.Me me=new Persons.Me(EARLY,10,List.of(LATE),"Ana",20,"Phone",30);
        Persons.Card card=Persons.card(me,device(phone,"Phone",30),List.of(device(pc,"Desk",40)));
        byte[] wrapped=Persons.wrap(card);
        assertTrue(Persons.isCard(wrapped));
        Persons.Card read=Persons.open(wrapped);
        assertNotNull(read);
        assertEquals(EARLY,read.person);assertEquals(10,read.made);assertEquals(List.of(LATE),read.aliases);
        assertEquals("Ana",read.name);assertEquals(20,read.named);
        assertEquals(2,read.devices.size());
        assertEquals("Phone",read.devices.get(0).name);assertEquals("Desk",read.devices.get(1).name);
        assertEquals(40,read.newest());
        assertEquals(Persons.key(pc.getPublic().getEncoded()),Persons.key(read.devices.get(1).signing));
    }

    @Test public void halfACardIsNoCardAndNothingElseIsOne() throws Exception {
        KeyPair phone=Envelope.keys();
        byte[] whole=Persons.wrap(Persons.card(new Persons.Me(EARLY,1,null,"Ana",0,"Phone",0),device(phone,"Phone",0),List.of()));
        for(int cut=0;cut<whole.length;cut++)assertNull("cut at "+cut,Persons.open(Arrays.copyOf(whole,cut)));
        // Anything after the last device.
        assertNull(Persons.open(Arrays.copyOf(whole,whole.length+1)));
        // A later format is not read as this one.
        byte[] later=whole.clone();later[4]=2;assertNull(Persons.open(later));
        // Not a card: a note, an answer, an offer of files, a hello.
        assertNull(Persons.open(Receipt.wrap(Receipt.PERSONS)));
        assertNull(Persons.open("MNB1 a note".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertFalse(Persons.isCard(Receipt.wrap(Receipt.PERSONS_MINE)));
        // And a card is none of those.
        assertEquals(0,Receipt.open(whole));assertNull(Drop.open(whole));assertNull(Hello.open(whole));
        assertNull(Parcel.open(whole));assertNull(Courier.open(whole));
    }

    @Test public void aCardHasBounds() throws Exception {
        KeyPair phone=Envelope.keys();
        Persons.Me me=new Persons.Me(EARLY,1,null,"Ana",0,"Phone",0);
        // Never more devices than fit, and no device twice.
        List<Persons.Device> many=new ArrayList<>();
        for(int i=0;i<Persons.DEVICES_MOST+5;i++)many.add(device(Envelope.keys(),"Tablet "+i,0));
        many.add(device(phone,"Phone again",0));
        Persons.Card card=Persons.card(me,device(phone,"Phone",0),many);
        assertEquals(Persons.DEVICES_MOST,card.devices.size());
        assertNotNull(Persons.open(Persons.wrap(card)));
        // A name longer than a name may be is refused where it can still be said.
        try{Persons.wrap(Persons.card(me,device(phone,"x".repeat(Persons.NAME_MOST+1),0),List.of()));fail();}catch(IllegalArgumentException expected){}
        // Aliases: never the id itself, never more than fit, the newest kept.
        List<String> aliases=new ArrayList<>();
        for(int i=0;i<Persons.ALIASES_MOST+4;i++)aliases.add(String.format("%032x",i+100));
        aliases.add(EARLY);
        Persons.Card read=Persons.open(Persons.wrap(Persons.card(new Persons.Me(EARLY,1,aliases,"Ana",0,"Phone",0),device(phone,"Phone",0),List.of())));
        assertEquals(Persons.ALIASES_MOST,read.aliases.size());
        assertFalse(read.aliases.contains(EARLY));
        assertTrue(read.aliases.contains(String.format("%032x",Persons.ALIASES_MOST+3+100)));
    }

    @Test public void theEarlierMadeIdIsKeptAndTheLowerWhereBothWereMadeAtOnce() {
        assertTrue(Persons.wins(LATE,5,EARLY,9));
        assertFalse(Persons.wins(EARLY,9,LATE,5));
        assertTrue(Persons.wins(EARLY,7,LATE,7));
        assertFalse(Persons.wins(LATE,7,EARLY,7));
    }

    @Test public void theDeviceWhoseIdWasMadeLaterTakesTheOtherAndKeepsItsOwnAsAnAlias() throws Exception {
        KeyPair phone=Envelope.keys(),pc=Envelope.keys();
        Persons.Me onPhone=new Persons.Me(LATE,500,null,"Ana",0,"Phone",0);
        Persons.Me onPc=new Persons.Me(EARLY,100,null,"Ana",0,"Desk",0);
        Persons.Card fromPc=Persons.card(onPc,device(pc,"Desk",0),List.of(device(phone,"Phone",0)));
        Persons.Taken taken=Persons.take(onPhone,fromPc,true,pc.getPublic().getEncoded(),phone.getPublic().getEncoded(),known(pc));
        assertNotNull(taken);assertTrue(taken.adopted);
        assertEquals(EARLY,taken.me.id);assertEquals(100,taken.me.made);assertEquals(List.of(LATE),taken.me.aliases);
        // And the other way round nothing moves: the PC keeps its own.
        Persons.Card fromPhone=Persons.card(onPhone,device(phone,"Phone",0),List.of(device(pc,"Desk",0)));
        Persons.Taken back=Persons.take(onPc,fromPhone,true,phone.getPublic().getEncoded(),pc.getPublic().getEncoded(),known(phone));
        assertNotNull(back);assertFalse(back.adopted);assertEquals(EARLY,back.me.id);
        // A card still saying the id this device left behind changes nothing either.
        Persons.Taken late=Persons.take(taken.me,fromPhone,true,phone.getPublic().getEncoded(),phone.getPublic().getEncoded(),known(phone));
        assertNull("a card signed by this device's own key is not another device's",late);
        KeyPair tablet=Envelope.keys();
        Persons.Card stale=Persons.card(new Persons.Me(LATE,500,null,"Ana",0,"Tablet",0),device(tablet,"Tablet",0),List.of(device(phone,"Phone",0)));
        Persons.Taken again=Persons.take(taken.me,stale,true,tablet.getPublic().getEncoded(),phone.getPublic().getEncoded(),known(pc,tablet));
        assertNotNull(again);assertFalse(again.adopted);assertEquals(EARLY,again.me.id);
    }

    @Test public void onlyADeviceThatIsYoursAtBothEndsIsBelieved() throws Exception {
        KeyPair phone=Envelope.keys(),pc=Envelope.keys(),stranger=Envelope.keys();
        Persons.Me onPhone=new Persons.Me(LATE,500,null,"Ana",0,"Phone",0);
        Persons.Card fromPc=Persons.card(new Persons.Me(EARLY,100,null,"Bo",900,"Desk",0),device(pc,"Desk",0),List.of(device(phone,"Phone",0)));
        // Not marked as this owner's here: nothing.
        assertNull(Persons.take(onPhone,fromPc,false,pc.getPublic().getEncoded(),phone.getPublic().getEncoded(),known(pc)));
        // Marked as theirs here, but the card does not count this device as its owner's: nothing.
        Persons.Card notMe=Persons.card(new Persons.Me(EARLY,100,null,"Bo",900,"Desk",0),device(pc,"Desk",0),List.of(device(stranger,"Other",0)));
        assertNull(Persons.take(onPhone,notMe,true,pc.getPublic().getEncoded(),phone.getPublic().getEncoded(),known(pc)));
        // A card that does not list the device that sent it: nothing.
        Persons.Card forged=Persons.card(new Persons.Me(EARLY,100,null,"Bo",900,"Desk",0),device(stranger,"Other",0),List.of(device(phone,"Phone",0)));
        assertNull(Persons.take(onPhone,forged,true,pc.getPublic().getEncoded(),phone.getPublic().getEncoded(),known(pc)));
        // One that lists this device as no longer theirs: nothing.
        Persons.Device gone=new Persons.Device(Point.shorten(phone.getPublic()),Point.shorten(Envelope.keys().getPublic()),"",
            "Phone",Persons.GONE,50);
        Persons.Card left=Persons.card(new Persons.Me(EARLY,100,null,"Bo",900,"Desk",0),device(pc,"Desk",0),List.of(gone));
        assertNull(Persons.take(onPhone,left,true,pc.getPublic().getEncoded(),phone.getPublic().getEncoded(),known(pc)));
        // And the card goes only where it is yours at both ends and the build reads cards.
        assertTrue(Persons.goesTo(true,true,true));
        assertFalse(Persons.goesTo(false,true,true));assertFalse(Persons.goesTo(true,false,true));assertFalse(Persons.goesTo(true,true,false));
    }

    @Test public void perDeviceTheLaterDecisionStands() throws Exception {
        KeyPair phone=Envelope.keys(),pc=Envelope.keys(),tablet=Envelope.keys();
        Persons.Me onPhone=new Persons.Me(EARLY,100,null,"Ana",50,"Phone",0);
        Map<String,Long> named=new HashMap<>();
        named.put(Persons.key(pc.getPublic().getEncoded()),0L);
        named.put(Persons.key(tablet.getPublic().getEncoded()),300L);
        // The PC names itself (never chosen: its maker's name) and says the tablet is called something decided at 200.
        Persons.Card fromPc=Persons.card(new Persons.Me(EARLY,100,null,"Ana",50,"Desk",0),device(pc,"DESKTOP-TEST",0),
            List.of(device(phone,"Phone from the PC",999),device(tablet,"Old tablet name",200)));
        Persons.Taken taken=Persons.take(onPhone,fromPc,true,pc.getPublic().getEncoded(),phone.getPublic().getEncoded(),named);
        assertNotNull(taken);
        // A device is believed about itself where the two decisions are as old as each other.
        assertEquals("DESKTOP-TEST",taken.names.get(Persons.key(pc.getPublic().getEncoded())).name);
        // The tablet's name here was decided later than the one on the card: it stays.
        assertFalse(taken.names.containsKey(Persons.key(tablet.getPublic().getEncoded())));
        // This device's own name is its own to decide, whatever the card says.
        assertFalse(taken.names.containsKey(Persons.key(phone.getPublic().getEncoded())));
        // Decided later on the card: taken.
        Persons.Card later=Persons.card(new Persons.Me(EARLY,100,null,"Ana",50,"Desk",0),device(pc,"Desk",400),
            List.of(device(phone,"Phone",0),device(tablet,"Kitchen tablet",301)));
        Persons.Taken second=Persons.take(onPhone,later,true,pc.getPublic().getEncoded(),phone.getPublic().getEncoded(),named);
        assertEquals("Kitchen tablet",second.names.get(Persons.key(tablet.getPublic().getEncoded())).name);
        assertEquals("Desk",second.names.get(Persons.key(pc.getPublic().getEncoded())).name);
        // A device never paired here is not written down from a card.
        Persons.Card stranger=Persons.card(new Persons.Me(EARLY,100,null,"Ana",50,"Desk",0),device(pc,"Desk",400),
            List.of(device(phone,"Phone",0),device(Envelope.keys(),"Somebody's laptop",999)));
        assertEquals(1,Persons.take(onPhone,stranger,true,pc.getPublic().getEncoded(),phone.getPublic().getEncoded(),named).names.size());
        assertTrue(Persons.later(5,4,false));assertFalse(Persons.later(4,4,false));assertTrue(Persons.later(4,4,true));assertFalse(Persons.later(3,4,true));
    }

    @Test public void yourNameChosenLaterOnAnotherDeviceIsYourNameHere() throws Exception {
        KeyPair phone=Envelope.keys(),pc=Envelope.keys();
        Persons.Me onPhone=new Persons.Me(EARLY,100,null,"Pixel test",0,"Phone",0);
        Persons.Card fromPc=Persons.card(new Persons.Me(EARLY,100,null,"Ana",70,"Desk",0),device(pc,"Desk",0),List.of(device(phone,"Phone",0)));
        Persons.Taken taken=Persons.take(onPhone,fromPc,true,pc.getPublic().getEncoded(),phone.getPublic().getEncoded(),known(pc));
        assertTrue(taken.renamed);assertEquals("Ana",taken.me.name);assertEquals(70,taken.me.named);
        // Chosen here later than there: it stays.
        Persons.Me chosen=new Persons.Me(EARLY,100,null,"Ana B",90,"Phone",0);
        Persons.Taken kept=Persons.take(chosen,fromPc,true,pc.getPublic().getEncoded(),phone.getPublic().getEncoded(),known(pc));
        assertFalse(kept.renamed);assertEquals("Ana B",kept.me.name);
    }

    @Test public void aBondDecidesWhoseIdBothKeepFromTheHello() throws Exception {
        Persons.Me mine=new Persons.Me(LATE,500,null,"Pixel test",0,"Phone",0);
        Hello.Said plain=new Hello.Said("Ana","MxTEST@203.0.113.4:9001",Point.shorten(Envelope.keys().getPublic()),
            Point.shorten(Envelope.keys().getPublic()),"LIBRARY","everything",true);
        // From a build that does not know about persons: nothing.
        assertSame(mine,Persons.bonded(mine,plain));
        Hello.Said owned=plain.owner(new Persons.Me(EARLY,100,null,"Ana",60,"Desk",0));
        Persons.Me now=Persons.bonded(mine,owned);
        assertEquals(EARLY,now.id);assertEquals(List.of(LATE),now.aliases);assertEquals("Ana",now.name);
        // The other way round, only the name chosen later moves.
        Persons.Me first=new Persons.Me(EARLY,100,null,"Pixel test",0,"Phone",0);
        Persons.Me stays=Persons.bonded(first,plain.owner(new Persons.Me(LATE,500,null,"Ana",60,"Desk",0)));
        assertEquals(EARLY,stays.id);assertTrue(stays.aliases.isEmpty());assertEquals("Ana",stays.name);
    }

    @Test public void aliasesAreKeptAsOneLineAndReadBack() {
        List<String> aliases=List.of(EARLY,LATE);
        assertEquals(aliases,Persons.aliases(Persons.aliases(aliases)));
        assertTrue(Persons.aliases("").isEmpty());assertTrue(Persons.aliases("nonsense,also").isEmpty());
        assertTrue(Persons.isId(Persons.newId(null)));
        assertNotEquals(Persons.newId(null),Persons.newId(null));
    }

    /** Seen on 0.1.027: both phones handed a card every round though nothing on it had changed. */
    @Test public void theSameDevicesMakeTheSameCardAndOnlyADecisionMakesAnother() throws Exception {
        KeyPair phone=Envelope.keys(),pc=Envelope.keys(),tablet=Envelope.keys();
        Persons.Me me=new Persons.Me(EARLY,10,null,"Ana",20,"Phone",30);
        Persons.Device desk=device(pc,"Desk",40),pad=device(tablet,"Tablet",50),self=device(phone,"Phone",30);
        // Paired in either order: the same card, byte for byte, itself first.
        Persons.Card one=Persons.card(me,self,List.of(desk,pad)),other=Persons.card(me,self,List.of(pad,desk));
        assertArrayEquals(Persons.wrap(one),Persons.wrap(other));
        assertEquals("Phone",one.devices.get(0).name);
        // A device at another address - its node given another relay - is not a card to send again.
        Persons.Device moved=new Persons.Device(desk.signing,desk.agreement,"MxTEST@198.51.100.7:9001","Desk",Persons.ACTIVE,40);
        Persons.Card elsewhere=Persons.card(me,self,List.of(moved,pad));
        assertFalse(Arrays.equals(Persons.wrap(one),Persons.wrap(elsewhere)));
        assertArrayEquals(Persons.sameness(one),Persons.sameness(elsewhere));
        // A name decided later is.
        Persons.Card renamed=Persons.card(me,self,List.of(device(pc,"Study",60),pad));
        assertFalse(Arrays.equals(Persons.sameness(one),Persons.sameness(renamed)));
        assertFalse(Arrays.equals(Persons.sameness(one),Persons.sameness(Persons.card(me.withName("Ana B",70),self,List.of(desk,pad)))));
    }

    /** Two devices answering each other's "knows about persons" went on for ever, and each answer sent the card again. */
    @Test public void onlyNewsAboutPersonsIsAnswered() {
        long now=1_000_000;
        assertTrue("the first this run",Persons.news(0,0,Receipt.PERSONS_MINE,now));
        assertFalse("the same word a moment later",Persons.news(Receipt.PERSONS_MINE,now-30_000,Receipt.PERSONS_MINE,now));
        assertFalse(Persons.news(Receipt.PERSONS_MINE,now-Persons.NEWS_AFTER+1,Receipt.PERSONS_MINE,now));
        assertTrue("the switch turned there",Persons.news(Receipt.PERSONS,now-30_000,Receipt.PERSONS_MINE,now));
        assertTrue("a device just started, minutes on",Persons.news(Receipt.PERSONS_MINE,now-Persons.NEWS_AFTER,Receipt.PERSONS_MINE,now));
        assertTrue("a clock set back",Persons.news(Receipt.PERSONS_MINE,now+5,Receipt.PERSONS_MINE,now));
    }

    @Test public void aDeviceYoursAtOneEndOnlyIsSaidWithTheSwitchThatMendsIt() {
        assertEquals("It counts this PC as one of your devices. Turn My device on here too so names travel.",
            Persons.oneSided(false,true,true,"PC"));
        assertEquals("It does not count this phone as one of your devices. Turn My device on there too so names travel.",
            Persons.oneSided(true,true,false,"phone"));
        // Yours at both ends, at neither, or a build that has not said: nothing to say.
        assertNull(Persons.oneSided(true,true,true,"PC"));
        assertNull(Persons.oneSided(false,true,false,"PC"));
        assertNull(Persons.oneSided(true,false,false,"PC"));
    }
}
