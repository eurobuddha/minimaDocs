package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.util.*;
import org.mininotes.desktop.platform.content.Context;

/**
 * The four roles, in the real notebook and through {@code Post.arrived}, with synthetic devices and no network. A owns a
 * note; B and C are its admins, D writes in it. An admin gives Can write and Can read and changes writers and readers;
 * only the owner makes an admin, changes one, or is never changed - here, and in what arrives from a build that let an
 * admin do more. See docs/SHARING.md, The four roles.
 */
public class DesktopRolesTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final List<NoteStore> open=new ArrayList<>();
    @After public void close(){for(NoteStore one:open)one.close();}

    private static final String A_AT="MxRolesOwnerFixture@127.0.0.1:9401",B_AT="MxRolesAdminFixture@127.0.0.1:9402",
        C_AT="MxRolesOtherAdminFixture@127.0.0.1:9403",D_AT="MxRolesWriterFixture@127.0.0.1:9404";

    private final class Device {
        final Context context;final NoteStore store;final Keys keys;final String at;
        Device(String name,String at) throws Exception {
            this.at=at;
            context=new Context(temp.newFolder(name));store=new NoteStore(context);store.getWritableDatabase();open.add(store);
            keys=new Keys(context);
            store.mySigningKey=key();store.myAgreement=Point.shorten(keys.agreement().getPublic());store.myName=name;store.myAddress=at;
        }
        String key() throws Exception {return Base64.getEncoder().encodeToString(keys.signing().getPublic().getEncoded());}
        void pair(Device... others) throws Exception {
            for(Device other:others)
                store.pairedWith(other.at,other.store.myName,false,other.keys.agreement().getPublic().getEncoded(),other.keys.signing().getPublic().getEncoded());
        }
        void mine(Device... others) throws Exception {
            for(Device other:others)
                store.pairedWith(other.at,other.store.myName,true,other.keys.agreement().getPublic().getEncoded(),other.keys.signing().getPublic().getEncoded());
        }
        Parcel.Member member(Sharing.Level level,long changed) throws Exception {
            return new Parcel.Member(key(),at,store.myName,level.said(),changed,Point.shorten(keys.agreement().getPublic()));
        }
        Post.Landed hear(byte[] sealed){return Post.arrived(context,store,keys,sealed);}
        /** A note from this device, its own entry at Admin as every build before 0.1.040 wrote it. */
        byte[] note(Device to,String id,long revision,String body,List<Parcel.Member> members) throws Exception {
            List<Parcel.Member> all=new ArrayList<>(members);all.add(member(Sharing.Level.ADMIN,1L));
            byte[] parcel=Parcel.wrap(new Parcel.Sent("","","","","Plans",body,true,all,"PAGE",id,false,-1L,true,null,1L));
            return Envelope.seal(page(id),revision,System.currentTimeMillis(),parcel,keys.signing(),to.keys.agreement().getPublic());
        }
        Sharing.Rule ruleFor(String id,Device who) throws Exception {
            for(Sharing.Rule rule:store.membership(Sharing.Scope.PAGE,id))if(rule.key.equals(who.key())||rule.address.equals(who.at))return rule;
            return null;
        }
    }

    private static byte[] page(String id){UUID u=UUID.fromString(id);return java.nio.ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();}

    private Device a,b,c,d;private String id;

    /** A's note, shared with two admins and a writer, as it reaches each of them. */
    @Before public void shared() throws Exception {
        a=new Device("Owner",A_AT);b=new Device("Admin",B_AT);c=new Device("Other admin",C_AT);d=new Device("Writer",D_AT);
        a.pair(b,c,d);b.pair(a,c,d);c.pair(a,b,d);d.pair(a,b,c);
        id=UUID.randomUUID().toString();
        NoteStore.Note note=new NoteStore.Note();note.id=id;note.book=a.store.someBook();note.title="Plans";note.body="first line";note.revision=1;a.store.save(note);
        a.store.setLevel(Sharing.Scope.PAGE,id,B_AT,Sharing.Level.ADMIN,"Admin",5_000L);
        a.store.setLevel(Sharing.Scope.PAGE,id,C_AT,Sharing.Level.ADMIN,"Other admin",5_000L);
        a.store.setLevel(Sharing.Scope.PAGE,id,D_AT,Sharing.Level.WRITE,"Writer",5_000L);
        List<Parcel.Member> list=List.of(b.member(Sharing.Level.ADMIN,5_000L),c.member(Sharing.Level.ADMIN,5_000L),d.member(Sharing.Level.WRITE,5_000L));
        for(Device one:new Device[]{b,c,d}){one.hear(a.note(one,id,1,"first line",list));assertEquals("first line",one.store.get(id).body);}
    }

    @Test public void anAdminGivesWriteAndReadAndChangesOnlyWritersAndReaders() throws Exception {
        assertEquals(List.of(Sharing.Level.ADMIN,Sharing.Level.WRITE,Sharing.Level.READ),a.store.mayGive(Sharing.Scope.PAGE,id));
        assertEquals(List.of(Sharing.Level.WRITE,Sharing.Level.READ),b.store.mayGive(Sharing.Scope.PAGE,id));
        assertTrue(d.store.mayGive(Sharing.Scope.PAGE,id).isEmpty());

        Sharing.Rule owner=b.ruleFor(id,a),admin=b.ruleFor(id,c),writer=b.ruleFor(id,d);
        assertTrue(b.store.ownerOf(owner));assertFalse(b.store.ownerOf(admin));
        assertFalse(b.store.mayChange(owner));assertFalse(b.store.mayChange(admin));assertTrue(b.store.mayChange(writer));
        for(Sharing.Level to:Sharing.Level.values()) {
            try{b.store.decide(owner,to,9_000L);fail("an admin changed the owner to "+to);}catch(IllegalStateException expected){}
            try{b.store.decide(admin,to,9_000L);fail("an admin changed another admin to "+to);}catch(IllegalStateException expected){}
        }
        try{b.store.decide(writer,Sharing.Level.ADMIN,9_000L);fail("an admin made an admin");}catch(IllegalStateException expected){}
        try{b.store.give(Sharing.Scope.PAGE,id,"MxSomebodyNewFixture@127.0.0.1:9405",Sharing.Level.ADMIN,"New");fail("an admin gave Admin");}catch(IllegalStateException expected){}
        b.store.decide(writer,Sharing.Level.READ,9_000L);
        assertEquals(Sharing.Level.READ,b.ruleFor(id,d).level);
        b.store.give(Sharing.Scope.PAGE,id,"MxSomebodyNewFixture@127.0.0.1:9405",Sharing.Level.WRITE,"New");
        // A writer changes nobody; the owner changes an admin.
        try{d.store.decide(d.ruleFor(id,b),Sharing.Level.READ,9_000L);fail("a writer changed an admin");}catch(IllegalStateException expected){}
        assertTrue(a.store.mayChange(a.ruleFor(id,c)));
        a.store.decide(a.ruleFor(id,c),Sharing.Level.WRITE,9_000L);
        assertEquals(Sharing.Level.WRITE,a.ruleFor(id,c).level);
    }

    @Test public void eachDeviceSaysItselfAtWhatItMayDoAndOnlyTheOwnerAsAdmin() throws Exception {
        assertEquals(Sharing.Level.ADMIN.said(),self(a).level);
        assertEquals(Sharing.Level.ADMIN.said(),self(b).level);
        assertEquals(Sharing.Level.WRITE.said(),self(d).level);
    }
    private Parcel.Member self(Device one) throws Exception {
        for(Parcel.Member member:one.store.travelling(Sharing.Scope.PAGE,id))if(member.key.equals(one.key()))return member;
        throw new AssertionError("no entry of its own");
    }

    @Test public void anAdminsListCannotTouchTheOwnerOrAnotherAdminOrMakeOne() throws Exception {
        // B's build let an admin do more: its list takes the owner off, makes C a reader and D an admin.
        List<Parcel.Member> more=List.of(a.member(Sharing.Level.GONE,9_000L),c.member(Sharing.Level.READ,9_000L),d.member(Sharing.Level.ADMIN,9_000L));
        c.hear(b.note(c,id,2,"first line\nfrom the admin",more));
        assertEquals("an admin's words are taken","first line\nfrom the admin",c.store.get(id).body);
        assertEquals("C is still an admin",Sharing.Level.ADMIN,c.store.myLevel(Sharing.Scope.PAGE,id));
        assertNotEquals("the owner is still on it",Sharing.Level.GONE,c.ruleFor(id,a).level);
        assertEquals("D is not made an admin",Sharing.Level.WRITE,c.ruleFor(id,d).level);
        // The owner's next word still arrives.
        c.hear(a.note(c,id,3,"first line\nfrom the admin\nfrom the owner",List.of(c.member(Sharing.Level.ADMIN,5_000L))));
        assertEquals("first line\nfrom the admin\nfrom the owner",c.store.get(id).body);

        // At D the same list takes nobody off: the owner stays.
        d.hear(b.note(d,id,2,"first line\nfrom the admin",more));
        assertNotEquals(Sharing.Level.GONE,d.ruleFor(id,a).level);
        assertEquals(Sharing.Level.WRITE,d.store.myLevel(Sharing.Scope.PAGE,id));

        // At the owner's own: C stays an admin, D is not made one.
        a.hear(b.note(a,id,2,"first line\nfrom the admin",more));
        assertEquals(Sharing.Level.ADMIN,a.ruleFor(id,c).level);
        assertEquals(Sharing.Level.WRITE,a.ruleFor(id,d).level);

        // What an admin may do arrives: D made a reader, at C and at the owner's.
        List<Parcel.Member> allowed=List.of(d.member(Sharing.Level.READ,9_500L));
        c.hear(b.note(c,id,4,"first line\nfrom the admin\nfrom the owner",allowed));
        assertEquals(Sharing.Level.READ,c.ruleFor(id,d).level);
        a.hear(b.note(a,id,3,"first line\nfrom the admin",allowed));
        assertEquals(Sharing.Level.READ,a.ruleFor(id,d).level);

        // And what the owner decides about an admin arrives from the owner.
        c.hear(a.note(c,id,5,"first line\nfrom the admin\nfrom the owner",List.of(b.member(Sharing.Level.WRITE,10_000L))));
        assertEquals(Sharing.Level.WRITE,c.ruleFor(id,b).level);
    }

    @Test public void anAdminIsTakenOffByTheOwnerAndByNoOtherAdmin() throws Exception {
        assertFalse("B cannot take C off",c.store.takenOff(B_AT,id,Sharing.Scope.PAGE,9_000L));
        assertEquals(Sharing.Level.ADMIN,c.store.myLevel(Sharing.Scope.PAGE,id));
        assertTrue("B takes D off",d.store.takenOff(B_AT,id,Sharing.Scope.PAGE,9_000L));
        assertTrue("A takes C off",c.store.takenOff(A_AT,id,Sharing.Scope.PAGE,9_000L));
    }

    @Test public void somebodyNothingIsKnownAboutWritesUntilAListSaysAndIsNoAdmin() throws Exception {
        // E, never on any list here, sends C a note of A's: written down as having it, at Can write, not Admin.
        Device e=new Device("Stranger to the list","MxRolesUnknownFixture@127.0.0.1:9406");
        c.pair(e);
        c.hear(e.note(c,id,6,"first line",List.of()));
        Sharing.Rule weak=c.ruleFor(id,e);
        assertNotNull(weak);
        assertEquals(Sharing.Level.WRITE,weak.level);
    }

    /**
     * The owner is a person: two of their devices, each marked My device on the other, and a friend who is an admin. What
     * either of the owner's devices decides is the owner's decision there; the friend knows only the device it came from.
     */
    @Test public void theOwnersOtherDevicesAreTheOwner() throws Exception {
        Device pro=new Device("Pro","MxRolesProFixture@127.0.0.1:9411"),graphene=new Device("Graphene","MxRolesGrapheneFixture@127.0.0.1:9412"),
            friend=new Device("Friend","MxRolesFriendFixture@127.0.0.1:9413");
        pro.mine(graphene);graphene.mine(pro);pro.pair(friend);graphene.pair(friend);friend.pair(pro,graphene);
        String note=UUID.randomUUID().toString();
        NoteStore.Note made=new NoteStore.Note();made.id=note;made.book=pro.store.someBook();made.title="Plans";made.body="first line";made.revision=1;pro.store.save(made);
        // As a build before 0.1.040 left it: the other device of the owner's an admin, the friend an admin.
        pro.store.setLevel(Sharing.Scope.PAGE,note,graphene.at,Sharing.Level.ADMIN,"Graphene",5_000L);
        pro.store.setLevel(Sharing.Scope.PAGE,note,friend.at,Sharing.Level.ADMIN,"Friend",5_000L);
        List<Parcel.Member> list=List.of(graphene.member(Sharing.Level.ADMIN,5_000L),friend.member(Sharing.Level.ADMIN,5_000L));
        graphene.hear(pro.note(graphene,note,1,"first line",list));
        friend.hear(pro.note(friend,note,1,"first line",list));

        // Nothing stored is demoted: the other device is still at Admin, and read as the owner, not changed by anybody.
        assertEquals(Sharing.Level.ADMIN,pro.ruleFor(note,graphene).level);
        assertTrue(pro.store.ownerOf(pro.ruleFor(note,graphene)));assertFalse(pro.store.mayChange(pro.ruleFor(note,graphene)));
        // On the other device of the owner's, the thing is the owner's own: every role may be given, admins changed.
        assertTrue(graphene.store.owns(Sharing.Scope.PAGE,note));
        assertEquals(List.of(Sharing.Level.ADMIN,Sharing.Level.WRITE,Sharing.Level.READ),graphene.store.mayGive(Sharing.Scope.PAGE,note));
        assertTrue(graphene.store.ownerOf(graphene.ruleFor(note,pro)));
        assertTrue(graphene.store.mayChange(graphene.ruleFor(note,friend)));
        assertEquals("it says itself as Admin",Sharing.Level.ADMIN.said(),selfOf(graphene,note).level);

        // Graphene makes the friend a writer, and the Pro takes it: Graphene is the owner there.
        graphene.store.decide(graphene.ruleFor(note,friend),Sharing.Level.WRITE,9_000L);
        List<Parcel.Member> decided=List.of(friend.member(Sharing.Level.WRITE,9_000L));
        pro.hear(graphene.note(pro,note,2,"first line\nfrom graphene",decided));
        assertEquals("first line\nfrom graphene",pro.store.get(note).body);
        assertEquals(Sharing.Level.WRITE,pro.ruleFor(note,friend).level);
        // And the other way round: the Pro makes the friend an admin again, Graphene takes it.
        graphene.hear(pro.note(graphene,note,3,"first line\nfrom graphene",List.of(friend.member(Sharing.Level.ADMIN,10_000L))));
        assertEquals(Sharing.Level.ADMIN,graphene.ruleFor(note,friend).level);

        // The friend knows the Pro as the owner and Graphene as an admin: Graphene's word about an admin waits for the Pro's.
        friend.hear(graphene.note(friend,note,2,"first line\nfrom graphene",decided));
        assertEquals(Sharing.Level.ADMIN,friend.store.myLevel(Sharing.Scope.PAGE,note));
        assertFalse(friend.store.takenOff(graphene.at,note,Sharing.Scope.PAGE,9_000L));
        friend.hear(pro.note(friend,note,4,"first line\nfrom graphene",decided));
        assertEquals(Sharing.Level.WRITE,friend.store.myLevel(Sharing.Scope.PAGE,note));
    }
    private static Parcel.Member selfOf(Device one,String note) throws Exception {
        for(Parcel.Member member:one.store.travelling(Sharing.Scope.PAGE,note))if(member.key.equals(one.key()))return member;
        throw new AssertionError("no entry of its own");
    }
}
