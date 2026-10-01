package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.util.*;
import org.mininotes.desktop.platform.content.Context;

/**
 * One person on every device, at the PC: the real notebook, cards and "knows about persons" handed to
 * {@code Post.arrived} sealed as a phone seals them, with synthetic devices and no network. What a card from a phone
 * that is "My device" here writes into People and devices, that the same card twice changes nothing, and what the PC
 * says under a phone that counts it as its owner's while the PC does not count the phone - the case seen on 0.1.027,
 * where the phones' new names never reached the PC. See docs/SHARING.md, One person on every device.
 */
public class DesktopPersonsTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final List<NoteStore> open=new ArrayList<>();
    @After public void close(){for(NoteStore one:open)one.close();}

    private static final String PC_AT="MxPersonsPcFixture@127.0.0.1:9401",PHONE_AT="MxPersonsPhoneFixture@127.0.0.1:9402",
        TABLET_AT="MxPersonsTabletFixture@127.0.0.1:9403";
    private static final String EARLY="0000000000000000000000000000000a";

    private final class Device {
        final Context context;final NoteStore store;final Keys keys;final String at;
        Device(String name,String at) throws Exception {
            this.at=at;
            context=new Context(temp.newFolder(name));store=new NoteStore(context);store.getWritableDatabase();open.add(store);
            keys=new Keys(context);
            store.mySigningKey=Base64.getEncoder().encodeToString(keys.signing().getPublic().getEncoded());
            store.myAgreement=Point.shorten(keys.agreement().getPublic());store.myName=name;store.myAddress=at;
        }
        void pair(Device other,String name,boolean mine) throws Exception {
            store.pairedWith(other.at,name,mine,other.keys.agreement().getPublic().getEncoded(),other.keys.signing().getPublic().getEncoded());
            store.setMine(other.at,mine);
        }
        Persons.Device as(String name,long decided) throws Exception {
            return new Persons.Device(Point.shorten(keys.signing().getPublic()),Point.shorten(keys.agreement().getPublic()),at,name,Persons.ACTIVE,decided);
        }
        Post.Landed hear(byte[] sealed){return Post.arrived(context,store,keys,sealed);}
        byte[] card(Device to,Persons.Card card) throws Exception {
            return Envelope.seal(Persons.idBytes(card.person),card.newest(),System.currentTimeMillis(),Persons.wrap(card),keys.signing(),to.keys.agreement().getPublic());
        }
        byte[] persons(Device to,int which) throws Exception {
            return Envelope.seal(new byte[16],0,System.currentTimeMillis(),Receipt.wrap(which),keys.signing(),to.keys.agreement().getPublic());
        }
    }

    @Test public void aPhonesCardNamesItsDevicesHereAndAPhoneYoursAtOneEndOnlyIsSaid() throws Exception {
        Device pc=new Device("Test PC",PC_AT),phone=new Device("Test phone",PHONE_AT),tablet=new Device("Test tablet",TABLET_AT);
        // The phone was connected as the owner's; the tablet was paired with a plain code, so it is nobody's here.
        pc.pair(phone,"Ana",true);pc.pair(tablet,"Ana",false);
        Persons.Me owner=new Persons.Me(EARLY,1,null,"Ana",900,"Kitchen phone",7_000);
        Persons.Card card=Persons.card(owner,phone.as("Kitchen phone",7_000),List.of(pc.as("Old name",0),tablet.as("Graphene test",8_000)));

        // The phone's card: its own name written down, and the tablet's too - not marked as the owner's here, but
        // listed as theirs by a device that is theirs at both ends. Seen on 0.1.029: it kept its pairing code's name.
        Post.Landed took=pc.hear(phone.card(pc,card));
        assertTrue("an open People and devices is drawn again",took.devices);
        assertEquals("Kitchen phone",pc.store.address(PHONE_AT).name);
        assertEquals(7_000,pc.store.address(PHONE_AT).named);
        assertEquals("Graphene test",pc.store.address(TABLET_AT).name);
        assertFalse("the switch is the owner's to turn",pc.store.address(TABLET_AT).mine);
        assertEquals("and said under it",Persons.LISTED_AS_YOURS,Post.oneSided(pc.context,pc.store.address(TABLET_AT)));
        // One person now, by the id made first; and the PC's own name is its own, whatever a card calls it.
        assertEquals(EARLY,Post.me(pc.context).id);
        assertNotEquals("Old name",Node.deviceHere(pc.context));

        // The same card again changes nothing, and asks for nothing to be drawn again.
        assertFalse(pc.hear(phone.card(pc,card)).devices);

        // The tablet counts this PC as its owner's; this PC does not count the tablet. Said under it, with the fix.
        pc.hear(tablet.persons(pc,Receipt.PERSONS_MINE));
        assertEquals("It counts this PC as one of your devices. Turn My device on here too so names travel.",
            Post.oneSided(pc.context,pc.store.address(TABLET_AT)));
        assertNull("yours at both ends",Post.oneSided(pc.context,pc.store.address(PHONE_AT)));
        // Its own card is not believed while it is nobody's here.
        Persons.Card fromTablet=Persons.card(owner,tablet.as("Tablet by itself",8_500),List.of(pc.as("Old name",0),phone.as("Kitchen phone",7_000)));
        assertFalse(pc.hear(tablet.card(pc,fromTablet)).devices);
        assertEquals("Graphene test",pc.store.address(TABLET_AT).name);

        // Turned on here: nothing more to say, and the next card names it.
        pc.store.setMine(TABLET_AT,true);
        assertNull(Post.oneSided(pc.context,pc.store.address(TABLET_AT)));
        Persons.Card later=Persons.card(owner,phone.as("Kitchen phone",7_000),List.of(pc.as("Old name",0),tablet.as("Graphene",9_000)));
        assertTrue(pc.hear(phone.card(pc,later)).devices);
        assertEquals("Graphene",pc.store.address(TABLET_AT).name);

        // Renamed on the phone after the PC scanned its code again: the code's name does not undo the name among your
        // devices, a name decided earlier does not undo a later one, and the later one is taken.
        pc.store.pairedWith(PHONE_AT,"Ana",true,phone.keys.agreement().getPublic().getEncoded(),phone.keys.signing().getPublic().getEncoded());
        assertEquals("Kitchen phone",pc.store.address(PHONE_AT).name);
        Persons.Card older=Persons.card(owner,phone.as("PixelPro",6_000),List.of(pc.as("Old name",0),tablet.as("Graphene",9_000)));
        pc.hear(phone.card(pc,older));
        assertEquals("Kitchen phone",pc.store.address(PHONE_AT).name);
        Persons.Card renamed=Persons.card(owner,phone.as("Pro",10_000),List.of(pc.as("Old name",0),tablet.as("Graphene",9_000)));
        assertTrue(pc.hear(phone.card(pc,renamed)).devices);
        assertEquals("Pro",pc.store.address(PHONE_AT).name);
        assertEquals("the rounds on a note read the same name","Pro",pc.store.nameFor(PHONE_AT));
        // And a list naming the phone at an address it had before, by the name it had then: filed where the phone is,
        // and the round says what it is called here, not what the list called it.
        NoteStore.Note shared=new NoteStore.Note();shared.book=pc.store.someBook();shared.title="Shared";shared.body="";pc.store.save(shared);
        String before="MxPersonsPhoneBefore@127.0.0.1:9412",phoneKey=Base64.getEncoder().encodeToString(phone.keys.signing().getPublic().getEncoded());
        pc.store.mergeMembership(Sharing.Scope.PAGE,shared.id,List.of(new Sharing.Rule(Sharing.Scope.PAGE,shared.id,before,Sharing.Level.WRITE,5L,phoneKey)),
            Map.of(phoneKey,"PixelPro"));
        assertEquals(List.of("Pro"),SyncStatus.names(pc.store,shared.id));

        // The phone's switch turned off there: said the other way round.
        pc.hear(phone.persons(pc,Receipt.PERSONS));
        assertEquals("It does not count this PC as one of your devices. Turn My device on there too so names travel.",
            Post.oneSided(pc.context,pc.store.address(PHONE_AT)));
    }
}
