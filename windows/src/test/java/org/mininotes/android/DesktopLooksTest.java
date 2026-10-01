package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.util.*;
import org.mininotes.desktop.platform.content.Context;

/**
 * The icon and the picture a note or a collection wears (docs/HOME.md, step 4): kept in its own row, set only to what can
 * be drawn and can travel, listed with every line, carried in a note's path and a collection's carton, and taken on the
 * far side where what arrived is not behind what is there. Synthetic notebooks and pictures only; no network - each
 * message is sealed and handed to the device it is for.
 */
public class DesktopLooksTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final List<NoteStore> open=new ArrayList<>();
    @After public void close(){for(NoteStore one:open)one.close();}

    private static final String PHONE="MxLooksPhone@127.0.0.1:9401", PC="MxLooksPc@127.0.0.1:9402";

    /** A picture's first bytes and some more: all that is looked at to say it is one. */
    static byte[] png(int seed) {
        byte[] b=new byte[64];new Random(seed).nextBytes(b);
        b[0]=(byte)0x89;b[1]='P';b[2]='N';b[3]='G';
        return b;
    }

    private final class Device {
        final Context context;final NoteStore store;final Keys keys;
        Device(String name) throws Exception {
            context=new Context(temp.newFolder(name));store=new NoteStore(context);store.getWritableDatabase();open.add(store);
            keys=new Keys(context);store.mySigningKey=Base64.getEncoder().encodeToString(keys.signing().getPublic().getEncoded());
        }
        void pair(String address,String name,Device other,boolean mine) throws Exception {
            store.pairedWith(address,name,mine,other.keys.agreement().getPublic().getEncoded(),other.keys.signing().getPublic().getEncoded());
        }
        Post.Landed hear(byte[] sealed){return Post.arrived(context,store,keys,sealed);}
    }

    private static byte[] page(String id){UUID u=UUID.fromString(id);return java.nio.ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();}

    /**
     * A note as a build that knows trees sends it: on a path, with its icon and picture after it. Not asking to be
     * answered: an answer would start this device's node, and these tests have no network.
     */
    private static byte[] note(Device from,Device to,String id,long revision,String body,List<Parcel.Step> path,String icon,byte[] image) throws Exception {
        byte[] parcel=Parcel.wrap(new Parcel.Sent("","","","","Soup",body,true,Collections.<Parcel.Member>emptyList(),"PAGE",id,false,-1L,false,
            List.of(),1L,List.of(),path,icon,image),Envelope.MAX_TEXT);
        return Envelope.seal(page(id),revision,1,parcel,from.keys.signing(),to.keys.agreement().getPublic());
    }

    /** A collection on its own, as a build that knows trees sends it, not asking to be answered. */
    private static byte[] carton(Device from,Device to,String id,String name,long revision,String icon,byte[] image) throws Exception {
        Carton.Sent sent=new Carton.Sent(id,name,icon,image,0,1,List.of(),true,Sharing.Scope.THING.name(),id,List.of(),false,null,0L);
        return Envelope.seal(Things.envelopeId(id),revision,1,Carton.wrap(sent),from.keys.signing(),to.keys.agreement().getPublic());
    }

    private NoteStore.Note write(NoteStore store,String book,String body) {
        NoteStore.Note n=new NoteStore.Note();n.book=book;n.title="Soup";n.body=body;n.revision=1;store.save(n);return n;
    }

    private static NoteStore.Branch line(List<NoteStore.Branch> lines,String id) {
        for(NoteStore.Branch one:lines)if(one.id.equals(id))return one;
        return null;
    }

    @Test public void aThingWearsAnIconOrAPictureAndOnlyOnesThatCanBeShownAndTravel() throws Exception {
        NoteStore store=new Device("one").store;
        String kitchen=store.addCollection("Kitchen").id;
        NoteStore.Note soup=write(store,kitchen,"Synthetic soup");
        long revision=store.get(soup.id).revision, collection=store.revisionOf(kitchen);
        assertEquals("",store.iconOf(NoteStore.Branch.Kind.PAGE,soup.id));assertNull(store.imageOf(NoteStore.Branch.Kind.PAGE,soup.id));

        // An icon from the set: kept by name, and the note is owed again with the same words; the collection's carton too.
        store.setIcon(NoteStore.Branch.Kind.PAGE,soup.id,"heart");
        assertEquals("heart",store.iconOf(NoteStore.Branch.Kind.PAGE,soup.id));
        assertEquals(revision+1,store.get(soup.id).revision);assertEquals("Synthetic soup",store.get(soup.id).body);
        store.setIcon(NoteStore.Branch.Kind.COLLECTION,kitchen,"folder");
        assertEquals("folder",store.iconOf(NoteStore.Branch.Kind.COLLECTION,kitchen));
        assertEquals(collection+1,store.revisionOf(kitchen));
        // The same again changes nothing, and moves nothing on.
        store.setIcon(NoteStore.Branch.Kind.PAGE,soup.id,"heart");
        assertEquals(revision+1,store.get(soup.id).revision);

        // Refused: a name the set does not have, Home, anything but a note or a collection.
        assertThrows(IllegalArgumentException.class,()->store.setIcon(NoteStore.Branch.Kind.PAGE,soup.id,"no-such-icon"));
        assertThrows(IllegalArgumentException.class,()->store.setIcon(NoteStore.Branch.Kind.COLLECTION,Things.HOME,"heart"));
        assertThrows(IllegalArgumentException.class,()->store.setIcon(NoteStore.Branch.Kind.FILE,"f-1","heart"));
        assertEquals("heart",store.iconOf(NoteStore.Branch.Kind.PAGE,soup.id));

        // A picture: kept whole, the icon under it kept too. Refused: too big to travel, or not a picture at all.
        byte[] picture=png(1);
        store.setImage(NoteStore.Branch.Kind.PAGE,soup.id,picture);
        assertArrayEquals(picture,store.imageOf(NoteStore.Branch.Kind.PAGE,soup.id));
        assertEquals("heart",store.iconOf(NoteStore.Branch.Kind.PAGE,soup.id));
        assertEquals(revision+2,store.get(soup.id).revision);
        byte[] huge=new byte[Thumb.MOST+1];System.arraycopy(picture,0,huge,0,4);
        assertThrows(IllegalArgumentException.class,()->store.setImage(NoteStore.Branch.Kind.PAGE,soup.id,huge));
        byte[] words="<svg onload=alert(1)> not a picture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,()->store.setImage(NoteStore.Branch.Kind.PAGE,soup.id,words));
        assertArrayEquals(picture,store.imageOf(NoteStore.Branch.Kind.PAGE,soup.id));

        // Every line that lists them wears them: a collection's inside, Home, the dock, search, the tree.
        NoteStore.Branch inKitchen=line(store.contents(kitchen),soup.id);
        assertEquals("heart",inKitchen.icon);assertArrayEquals(picture,inKitchen.image);
        assertEquals("folder",line(store.contents(Things.HOME),kitchen).icon);
        assertNull(line(store.contents(Things.HOME),kitchen).image);
        store.toDock(NoteStore.Branch.Kind.PAGE,soup.id,1);
        assertArrayEquals(picture,line(store.dock(),soup.id).image);
        assertEquals("folder",line(store.lookingEverywhere("Kitchen"),kitchen).icon);
        assertEquals("heart",line(store.lookingEverywhere("Synthetic"),soup.id).icon);
        assertEquals("heart",line(store.wholeTree(),soup.id).icon);
        assertEquals("folder",line(store.places(true),kitchen).icon);

        // The picture off: the icon it had is worn again. An icon chosen - or the default - takes a picture off with it.
        store.setImage(NoteStore.Branch.Kind.PAGE,soup.id,null);
        assertNull(store.imageOf(NoteStore.Branch.Kind.PAGE,soup.id));assertEquals("heart",store.iconOf(NoteStore.Branch.Kind.PAGE,soup.id));
        store.setImage(NoteStore.Branch.Kind.COLLECTION,kitchen,picture);
        store.setIcon(NoteStore.Branch.Kind.COLLECTION,kitchen,"star");
        assertNull(store.imageOf(NoteStore.Branch.Kind.COLLECTION,kitchen));assertEquals("star",store.iconOf(NoteStore.Branch.Kind.COLLECTION,kitchen));
        store.setIcon(NoteStore.Branch.Kind.PAGE,soup.id,null);
        assertEquals("",store.iconOf(NoteStore.Branch.Kind.PAGE,soup.id));
        assertEquals("",line(store.contents(kitchen),soup.id).icon);

        // A note this device only reads wears what its owner gives it, and nothing else.
        NoteStore.Note theirs=write(store,kitchen,"Synthetic from somebody");
        store.getWritableDatabase().execSQL("UPDATE notes SET theirs=1,origin=? WHERE id=?",new Object[]{PHONE,theirs.id});
        store.stands(Sharing.Scope.PAGE,theirs.id,Sharing.Level.READ,1);
        assertThrows(IllegalArgumentException.class,()->store.setIcon(NoteStore.Branch.Kind.PAGE,theirs.id,"star"));

        // In the row, so in a backup, and back again.
        store.setImage(NoteStore.Branch.Kind.COLLECTION,kitchen,picture);
        store.setIcon(NoteStore.Branch.Kind.PAGE,soup.id,"leaf");
        String backup=store.backup();
        NoteStore other=new Device("restored").store;
        other.importBackup(backup,true);
        assertEquals("leaf",other.iconOf(NoteStore.Branch.Kind.PAGE,soup.id));
        assertEquals("star",other.iconOf(NoteStore.Branch.Kind.COLLECTION,kitchen));
        assertArrayEquals(picture,other.imageOf(NoteStore.Branch.Kind.COLLECTION,kitchen));
    }

    @Test public void aNotesIconAndPictureArriveWithItAndNothingBehindPutsThemBack() throws Exception {
        Device phone=new Device("phone"),pc=new Device("pc");
        phone.pair(PC,"Test PC",pc,false);pc.pair(PHONE,"Test phone",phone,false);
        String id=UUID.randomUUID().toString();
        NoteStore.Note n=new NoteStore.Note();n.id=id;n.book=phone.store.someBook();n.title="Soup";n.body="Synthetic soup";n.revision=1;phone.store.save(n);
        phone.store.setLevel(Sharing.Scope.PAGE,id,PC,Sharing.Level.WRITE,null);
        pc.store.addShare(new Sharing.Rule(Sharing.Scope.PAGE,id,PHONE,true));
        pc.hear(note(phone,pc,id,1,"Synthetic soup",List.of(),"",null));
        assertEquals("Synthetic soup",pc.store.get(id).body);assertEquals("",pc.store.iconOf(NoteStore.Branch.Kind.PAGE,id));
        phone.store.acknowledged(PC,id,1,true);
        assertTrue(phone.store.owed(NoteStore.Branch.Kind.PAGE,id).isEmpty());

        // Given an icon and a picture on the phone: the note is owed to the PC again, the words unchanged.
        byte[] picture=png(2);
        phone.store.setIcon(NoteStore.Branch.Kind.PAGE,id,"soup");
        phone.store.setImage(NoteStore.Branch.Kind.PAGE,id,picture);
        long now=phone.store.get(id).revision;
        assertEquals(List.of(PC),addresses(phone.store.owed(NoteStore.Branch.Kind.PAGE,id)));
        Post.Landed landed=pc.hear(note(phone,pc,id,now,"Synthetic soup",List.of(),"soup",picture));
        assertEquals("soup",pc.store.iconOf(NoteStore.Branch.Kind.PAGE,id));
        assertArrayEquals(picture,pc.store.imageOf(NoteStore.Branch.Kind.PAGE,id));
        assertEquals("the same words, so nothing is announced",null,landed.said);assertEquals(id,landed.note);
        assertEquals(now,pc.store.get(id).revision);assertEquals("Synthetic soup",pc.store.get(id).body);

        // Behind what is here - a copy that took the long way round - puts back nothing.
        pc.hear(note(phone,pc,id,1,"Synthetic soup",List.of(),"",null));
        assertEquals("soup",pc.store.iconOf(NoteStore.Branch.Kind.PAGE,id));assertArrayEquals(picture,pc.store.imageOf(NoteStore.Branch.Kind.PAGE,id));
        // A parcel that says no path - a build from before trees - says nothing about the look, and changes none of it.
        byte[] old=Parcel.wrap(new Parcel.Sent("","","","","Soup","Synthetic soup, more",true,Collections.<Parcel.Member>emptyList(),"PAGE",id,false,-1L),Envelope.MAX_TEXT);
        pc.hear(Envelope.seal(page(id),now+1,1,old,phone.keys.signing(),pc.keys.agreement().getPublic()));
        assertEquals("Synthetic soup, more",pc.store.get(id).body);assertEquals("soup",pc.store.iconOf(NoteStore.Branch.Kind.PAGE,id));
        // A picture that is not one is not kept: the note wears none.
        byte[] words="not a picture at all, only words".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        pc.hear(note(phone,pc,id,now+2,"Synthetic soup, more",List.of(),"soup",words));
        assertNull(pc.store.imageOf(NoteStore.Branch.Kind.PAGE,id));assertEquals("soup",pc.store.iconOf(NoteStore.Branch.Kind.PAGE,id));
    }

    @Test public void twoDevicesThatChangedTheLookAtOnceEndUpAgreeing() throws Exception {
        Device phone=new Device("phone"),pc=new Device("pc");
        phone.pair(PC,"Test PC",pc,false);pc.pair(PHONE,"Test phone",phone,false);
        String id=UUID.randomUUID().toString();
        NoteStore.Note n=new NoteStore.Note();n.id=id;n.book=phone.store.someBook();n.title="Soup";n.body="Synthetic soup";n.revision=1;phone.store.save(n);
        phone.store.setLevel(Sharing.Scope.PAGE,id,PC,Sharing.Level.WRITE,null);
        pc.store.addShare(new Sharing.Rule(Sharing.Scope.PAGE,id,PHONE,true));
        pc.hear(note(phone,pc,id,1,"Synthetic soup",List.of(),"",null));
        // Each gives it an icon of its own, and each is at the same count.
        phone.store.setIcon(NoteStore.Branch.Kind.PAGE,id,"heart");
        pc.store.setIcon(NoteStore.Branch.Kind.PAGE,id,"star");
        assertEquals(phone.store.get(id).revision,pc.store.get(id).revision);
        long both=pc.store.get(id).revision;
        pc.hear(note(phone,pc,id,both,"Synthetic soup",List.of(),"heart",null));
        phone.hear(note(pc,phone,id,both,"Synthetic soup",List.of(),"star",null));
        assertEquals(phone.store.iconOf(NoteStore.Branch.Kind.PAGE,id),pc.store.iconOf(NoteStore.Branch.Kind.PAGE,id));
        assertEquals("star",pc.store.iconOf(NoteStore.Branch.Kind.PAGE,id));
    }

    @Test public void aCollectionsIconComesInItsPathAndItsPictureInItsCarton() throws Exception {
        Device phone=new Device("phone"),pc=new Device("pc");
        phone.pair(PC,"Test PC",pc,false);pc.pair(PHONE,"Test phone",phone,false);
        String one=UUID.randomUUID().toString(),two=UUID.randomUUID().toString(),three=UUID.randomUUID().toString();
        pc.store.addShare(new Sharing.Rule(Sharing.Scope.LIBRARY,Sharing.EVERYTHING,PHONE,true));
        // Made from the path, with the icon it has there.
        pc.hear(note(phone,pc,one,1,"Synthetic seeds",List.of(new Parcel.Step("their-garden","Garden","leaf",0,1)),"",null));
        String garden=pc.store.above(one).get(0);
        assertEquals("leaf",pc.store.iconOf(NoteStore.Branch.Kind.COLLECTION,garden));
        // Changed there, and followed here, as its name is.
        pc.hear(note(phone,pc,two,1,"Synthetic beans",List.of(new Parcel.Step("their-garden","Garden","flower",0,1)),"",null));
        assertEquals(garden,pc.store.above(two).get(0));
        assertEquals("flower",pc.store.iconOf(NoteStore.Branch.Kind.COLLECTION,garden));
        assertEquals("flower",line(pc.store.contents(Things.HOME),garden).icon);
        // A note from a build before trees names the collection in its old fields, says no icon, and takes none off.
        byte[] old=Parcel.wrap(new Parcel.Sent("","","their-garden","Garden","Peas","Synthetic peas",true),Envelope.MAX_TEXT);
        pc.hear(Envelope.seal(page(three),1,1,old,phone.keys.signing(),pc.keys.agreement().getPublic()));
        assertEquals(garden,pc.store.bookOf(three));
        assertEquals("flower",pc.store.iconOf(NoteStore.Branch.Kind.COLLECTION,garden));

        // Its picture, and its icon, in its carton.
        byte[] picture=png(3);
        pc.hear(carton(phone,pc,"their-garden","Garden",3,"sprout",picture));
        assertArrayEquals(picture,pc.store.imageOf(NoteStore.Branch.Kind.COLLECTION,garden));
        assertEquals("sprout",pc.store.iconOf(NoteStore.Branch.Kind.COLLECTION,garden));
        assertArrayEquals(picture,line(pc.store.contents(Things.HOME),garden).image);
        // One behind what was taken from them already puts nothing back.
        pc.hear(carton(phone,pc,"their-garden","Garden",2,"leaf",null));
        assertArrayEquals(picture,pc.store.imageOf(NoteStore.Branch.Kind.COLLECTION,garden));
        assertEquals("sprout",pc.store.iconOf(NoteStore.Branch.Kind.COLLECTION,garden));
        // A newer one without a picture takes it off.
        pc.hear(carton(phone,pc,"their-garden","Garden",4,"sprout",null));
        assertNull(pc.store.imageOf(NoteStore.Branch.Kind.COLLECTION,garden));
    }

    @Test public void theOwnersOwnCollectionFollowsItsLookFromTheOwnersOtherDevices() throws Exception {
        Device phone=new Device("phone"),pc=new Device("pc");
        phone.pair(PC,"Test PC",pc,true);pc.pair(PHONE,"Test phone",phone,true);
        String mine=pc.store.addCollection("Plans").id;
        pc.store.addShare(new Sharing.Rule(Sharing.Scope.LIBRARY,Sharing.EVERYTHING,PHONE,true));
        // What the phone calls it: the name made from this PC's key and its own id, as a phone that took it from here has it.
        String theirs=Parcel.localId(pc.store.mySigningKey,mine);
        byte[] picture=png(4);
        pc.hear(carton(phone,pc,theirs,"Plans for May",2,"calendar",picture));
        assertEquals("Plans for May",pc.store.nameOf(mine,true));
        assertEquals("calendar",pc.store.iconOf(NoteStore.Branch.Kind.COLLECTION,mine));
        assertArrayEquals(picture,pc.store.imageOf(NoteStore.Branch.Kind.COLLECTION,mine));
        assertEquals("still its own, from nobody","",pc.store.cameFrom(NoteStore.Branch.Kind.COLLECTION,mine));
        // Somebody else's device, even one that may write in it, gives the owner's own no look.
        Device ana=new Device("ana");
        pc.pair("MxLooksAna@127.0.0.1:9403","Ana's phone",ana,false);
        pc.store.addShare(new Sharing.Rule(Sharing.Scope.COLLECTION,mine,"MxLooksAna@127.0.0.1:9403",true));
        pc.hear(carton(ana,pc,Parcel.localId(pc.store.mySigningKey,mine),"Ana's plans",5,"star",null));
        assertEquals("Plans for May",pc.store.nameOf(mine,true));
        assertEquals("calendar",pc.store.iconOf(NoteStore.Branch.Kind.COLLECTION,mine));
        assertArrayEquals(picture,pc.store.imageOf(NoteStore.Branch.Kind.COLLECTION,mine));
    }

    @Test public void aCartonGoesForASharedCollectionThatHoldsNotesOnceItHasSomethingOfItsOwn() throws Exception {
        NoteStore store=new Device("one").store;
        String lists=store.addCollection("Lists").id;
        write(store,lists,"Synthetic list");
        store.addShare(new Sharing.Rule(Sharing.Scope.COLLECTION,lists,PHONE,true));
        // Its notes carry its name, colour and icon: nothing of its own to send yet.
        assertTrue(store.cartonsOwed(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING).isEmpty());
        // A picture only a carton carries: it goes, picture and all.
        byte[] picture=png(5);
        store.setImage(NoteStore.Branch.Kind.COLLECTION,lists,picture);
        List<Outbox.Wait> owed=store.cartonsOwed(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING);
        assertEquals(1,owed.size());assertEquals(lists,owed.get(0).page);assertEquals(PHONE,owed.get(0).address);
        Carton.Sent carton=Carton.open(Carton.wrap(store.carton(lists,PHONE,true)));
        assertArrayEquals(picture,carton.image);assertEquals("Lists",carton.name);
        assertNotNull("its files are said, none as they are",carton.files);assertTrue(carton.files.isEmpty());
        // Answered: had, until it changes again - an icon now.
        assertTrue(store.collectionAcknowledged(PHONE,Things.envelopeId(lists),owed.get(0).revision,true));
        assertTrue(store.cartonsOwed(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING).isEmpty());
        store.setIcon(NoteStore.Branch.Kind.COLLECTION,lists,"list");
        owed=store.cartonsOwed(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING);
        assertEquals(1,owed.size());
        assertEquals("list",Carton.open(Carton.wrap(store.carton(lists,PHONE,true))).icon);
        // And one renamed, holding notes and wearing nothing, goes too: its name changed.
        String more=store.addCollection("More").id;write(store,more,"Synthetic more");
        store.addShare(new Sharing.Rule(Sharing.Scope.COLLECTION,more,PHONE,true));
        assertEquals(1,store.cartonsOwed(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING).size());
        store.renameCollection(more,"Much more");
        assertEquals(2,store.cartonsOwed(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING).size());
    }

    private static List<String> addresses(List<Outbox.Wait> waits){List<String> out=new ArrayList<>();for(Outbox.Wait one:waits)out.add(one.address);return out;}
}
