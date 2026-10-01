package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.util.*;
import org.mininotes.desktop.platform.content.Context;
import org.mininotes.desktop.platform.database.Cursor;

/**
 * Notes and collections nested as deep as anybody likes, on the real notebook (see Things and docs/HOME.md, step 1):
 * a 0.1 notebook moved to things and checked by its own counts, a move that would lose a shelf refused whole, the tree
 * walked to any depth, what arrives built from the top of its path down, a collection travelling on its own, and what
 * a device from before trees is sent. Synthetic notebooks only; no network.
 */
public class DesktopThingsTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final List<NoteStore> open=new ArrayList<>();
    @After public void close(){for(NoteStore one:open)one.close();}

    private static final String A="MxThingsA@127.0.0.1:9301", B="MxThingsB@127.0.0.1:9302";
    private static final String N1="11111111-1111-4111-8111-111111111111", N2="22222222-2222-4222-8222-222222222222",
        N3="33333333-3333-4333-8333-333333333333", N4="44444444-4444-4444-8444-444444444444", N5="55555555-5555-4555-8555-555555555555";

    private NoteStore store(Context context){NoteStore store=new NoteStore(context);open.add(store);return store;}
    private NoteStore fresh() throws Exception {NoteStore store=store(new Context(temp.newFolder()));store.getWritableDatabase();return store;}

    private static void run(java.sql.Connection c,String sql) throws Exception {try(java.sql.Statement s=c.createStatement()){s.execute(sql);}}
    private static long one(java.sql.Connection c,String sql) throws Exception {
        try(java.sql.Statement s=c.createStatement();java.sql.ResultSet r=s.executeQuery(sql)){return r.next()?r.getLong(1):-1;}
    }
    private static String text(NoteStore store,String sql,String... args) {
        try(Cursor c=store.getReadableDatabase().rawQuery(sql,args)){return c.moveToFirst()?c.getString(0):null;}
    }
    private static long count(NoteStore store,String sql,String... args) {
        try(Cursor c=store.getReadableDatabase().rawQuery(sql,args)){return c.moveToFirst()?c.getLong(0):-1;}
    }

    /**
     * A notebook as 0.1.041 left it, at schema 29: two collections, three books - one of them in the bin - five notes,
     * one of them a favourite, a file on a book and one on a note, and two shares.
     */
    private java.io.File version29(Context context,boolean clash) throws Exception {
        java.io.File file=new java.io.File(context.getFilesDir(),"mininotes.db");
        try(java.sql.Connection c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+file)) {
            run(c,SchemaMigrations.create().get(0));
            run(c,"PRAGMA user_version=1");
            for(String sql:SchemaMigrations.upgrade(1,29))run(c,sql);
            run(c,"PRAGMA user_version=29");
            run(c,"INSERT INTO collections(id,name,updated,place) VALUES('c-kitchen','Kitchen',1,1),('c-work','Work',2,2)");
            run(c,"INSERT INTO books(id,collection,name,updated,place,deleted) VALUES('b-recipes','c-kitchen','Recipes',3,1,0),"
                +"('b-old','c-kitchen','Old',4,2,1),('b-plans','c-work','Plans',5,1,0)");
            // A collection and a book under one id: the move would keep one and lose the other.
            if(clash)run(c,"INSERT INTO books(id,collection,name,updated) VALUES('c-work','c-kitchen','Clash',6)");
            run(c,"INSERT INTO notes(id,title,body,notebook,pinned,deleted,updated,book) VALUES"
                +"('"+N1+"','Soup','Synthetic soup',"+"'Personal',1,0,10,'b-recipes'),('"+N2+"','Bread','Synthetic bread','Personal',0,0,11,'b-recipes'),"
                +"('"+N3+"','Gone','Synthetic old','Personal',0,0,12,'b-old'),('"+N4+"','Plan','Synthetic plan','Personal',0,0,13,'b-plans'),"
                +"('"+N5+"','Loose','Synthetic loose','Personal',0,0,14,'"+SchemaMigrations.FIRST_BOOK+"')");
            run(c,"INSERT INTO files(id,note,name,kind,bytes,added,held) VALUES('f-book','b-recipes','map.txt','text/plain',3,1,'book'),"
                +"('f-note','"+N1+"','list.txt','text/plain',3,1,'note')");
            run(c,"INSERT INTO shares(scope,target,address,mine,added,level,changed) VALUES('COLLECTION','c-kitchen','"+A+"',1,1,2,5),"
                +"('BOOK','b-plans','"+B+"',0,1,1,5)");
        }
        return file;
    }

    private static long[] counts(java.io.File file,String[] asks) throws Exception {
        long[] out=new long[asks.length];
        try(java.sql.Connection c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+file)){for(int at=0;at<asks.length;at++)out[at]=one(c,asks[at]);}
        return out;
    }

    @Test public void aNotebookFromBeforeBecomesThingsAndCountsTheSame() throws Exception {
        Context context=new Context(temp.newFolder("moved"));
        java.io.File file=version29(context,false);
        long[] before=counts(file,SchemaMigrations.COUNT_BEFORE);
        NoteStore store=store(context);store.getWritableDatabase();
        long[] after=counts(file,SchemaMigrations.COUNT_AFTER);
        assertNull(SchemaMigrations.differs(before,after));
        assertEquals("the first collection and book and the five made here",7,after[0]);
        assertEquals(SchemaMigrations.VERSION,count(store,"PRAGMA user_version"));
        // Books are collections inside the collection they were in; collections are on Home.
        assertEquals(Things.HOME,text(store,"SELECT parent FROM things WHERE id='c-kitchen'"));
        assertEquals("c-kitchen",text(store,"SELECT parent FROM things WHERE id='b-recipes'"));
        assertEquals("c-work",text(store,"SELECT parent FROM things WHERE id='b-plans'"));
        assertEquals(List.of("c-kitchen","b-recipes"),store.above(N1));
        // Every note under its former book, which is where it still says it is.
        assertEquals("b-recipes",store.bookOf(N1));assertEquals("b-plans",store.bookOf(N4));
        assertEquals("c-kitchen",store.collectionOfBook("b-recipes"));assertEquals("",store.collectionOfBook("c-kitchen"));
        // The favourite in the dock, the binned book in the bin with what is in it, the file on the book on a collection.
        assertEquals(1,count(store,"SELECT dock FROM notes WHERE id=?",N1));
        assertEquals(1,count(store,"SELECT binned FROM things WHERE id='b-old'"));
        Set<String> live=new HashSet<>();for(Outbox.Page page:store.pagesUnder(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING))live.add(page.id);
        assertEquals(Set.of(N1,N2,N4,N5),live);
        assertEquals("collection",text(store,"SELECT held FROM files WHERE id='f-book'"));
        assertEquals(List.of("f-book"),ids(store.filesOf(NoteStore.Branch.Kind.COLLECTION,"b-recipes")));
        assertEquals(List.of("f-note","f-book"),ids(store.filesReaching(NoteStore.Branch.Kind.PAGE,N1)));
        // The shares as they were, each still reaching what it reached.
        assertEquals(2,store.shares().size());
        assertEquals(Set.of(A),store.everybodyIn(NoteStore.Branch.Kind.PAGE,N1));
        assertEquals(Set.of(B),store.everybodyIn(NoteStore.Branch.Kind.PAGE,N4));
        // The old screens: every collection is a collection, a book among them.
        for(NoteStore.Branch line:store.wholeTree())assertNotEquals(NoteStore.Branch.Kind.BOOK,line.kind);
        assertEquals(List.of("b-recipes"),lines(store.inside(NoteStore.Branch.Kind.COLLECTION,"c-kitchen"),NoteStore.Branch.Kind.COLLECTION));
        assertEquals(Set.of(N1,N2),new HashSet<>(lines(store.inside(NoteStore.Branch.Kind.BOOK,"b-recipes"),NoteStore.Branch.Kind.PAGE)));
        // And the old tables are still there, as they were, and read by nothing.
        assertEquals(3,count(store,"SELECT COUNT(*) FROM collections"));
        assertEquals(4,count(store,"SELECT COUNT(*) FROM books"));
    }

    @Test public void aMoveThatWouldLoseAShelfIsRefusedAndTheNotebookIsLeftAsItWas() throws Exception {
        Context context=new Context(temp.newFolder("clash"));
        java.io.File file=version29(context,true);
        long[] before=counts(file,SchemaMigrations.COUNT_BEFORE);
        String schema=schema(file);
        NoteStore store=store(context);
        IllegalStateException refused=assertThrows(IllegalStateException.class,store::getWritableDatabase);
        assertTrue(refused.getMessage(),refused.getMessage().contains("could not be moved to notes and collections: collections and books 8 became 7"));
        assertTrue(refused.getMessage().contains("0.1.041 still opens it"));
        try(java.sql.Connection c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+file)) {
            assertEquals(29,one(c,"PRAGMA user_version"));
            assertEquals(0,one(c,"SELECT COUNT(*) FROM sqlite_master WHERE name='things'"));
            assertEquals(0,one(c,"SELECT COUNT(*) FROM pragma_table_info('notes') WHERE name='icon'"));
            assertEquals(1,one(c,"SELECT COUNT(*) FROM files WHERE held='book'"));
        }
        assertArrayEquals(before,counts(file,SchemaMigrations.COUNT_BEFORE));
        assertEquals(schema,schema(file));
    }

    private static String schema(java.io.File file) throws Exception {
        StringBuilder all=new StringBuilder();
        try(java.sql.Connection c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+file);java.sql.Statement s=c.createStatement();
            java.sql.ResultSet r=s.executeQuery("SELECT type,name,COALESCE(sql,'') FROM sqlite_master ORDER BY name")) {
            while(r.next())all.append(r.getString(1)).append(' ').append(r.getString(2)).append(' ').append(r.getString(3)).append('\n');
        }
        return all.toString();
    }

    private static List<String> ids(List<NoteStore.Held> files){List<String> out=new ArrayList<>();for(NoteStore.Held one:files)out.add(one.id);return out;}
    private static List<String> lines(NoteStore.Level level,NoteStore.Branch.Kind kind) {
        List<String> out=new ArrayList<>();for(NoteStore.Branch one:level.holds)if(one.kind==kind)out.add(one.id);return out;
    }

    @Test public void collectionsNestAsDeepAsAnybodyLikes() throws Exception {
        NoteStore store=fresh();
        String top=store.addCollection("Home life").id, middle=store.addCollection("Kitchen").id, inner=store.addCollection("Soups").id;
        store.moveBook(inner,middle);store.moveBook(middle,top);
        NoteStore.Note note=new NoteStore.Note();note.book=inner;note.title="Leek";note.body="Synthetic leek soup";store.save(note);
        assertEquals(List.of(top,middle,inner),store.above(note.id));
        // The tree, depth first, each line as deep as it sits.
        Map<String,Integer> depth=new HashMap<>();Map<String,String> parent=new HashMap<>();
        for(NoteStore.Branch line:store.wholeTree()){depth.put(line.id,line.depth);parent.put(line.id,line.parent);}
        assertEquals(0,(int)depth.get(top));assertEquals(1,(int)depth.get(middle));assertEquals(2,(int)depth.get(inner));assertEquals(3,(int)depth.get(note.id));
        assertEquals(Sharing.EVERYTHING,parent.get(top));assertEquals(top,parent.get(middle));assertEquals(inner,parent.get(note.id));
        // One level at a time: Home holds the top one only, each holds the next, the last holds the note.
        List<String> home=lines(store.inside(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING),NoteStore.Branch.Kind.COLLECTION);
        assertTrue(home.contains(top));assertFalse(home.contains(middle));assertFalse(home.contains(inner));
        assertEquals(List.of(middle),lines(store.inside(NoteStore.Branch.Kind.COLLECTION,top),NoteStore.Branch.Kind.COLLECTION));
        assertEquals(List.of(note.id),lines(store.inside(NoteStore.Branch.Kind.COLLECTION,inner),NoteStore.Branch.Kind.PAGE));
        assertEquals("1 collection",store.inside(NoteStore.Branch.Kind.COLLECTION,top).holds.get(0).detail);
        // What is under a collection, however deep.
        assertEquals(1,store.pagesUnder(NoteStore.Branch.Kind.COLLECTION,top).size());
        assertEquals(1,store.pagesUnder(NoteStore.Branch.Kind.COLLECTION,middle).size());
        // A rule on the top one reaches a note three collections down; a new one on a collection is THING.
        store.addShare(new Sharing.Rule(Sharing.Scope.COLLECTION,top,A,true));
        assertEquals(Sharing.Scope.THING,store.membership(Sharing.Scope.COLLECTION,top).get(0).scope);
        assertEquals(Set.of(A),store.everybodyIn(NoteStore.Branch.Kind.PAGE,note.id));
        assertEquals(Set.of(A),Sharing.audience(store.shares(),store.pathOf(note.id)).keySet());
        assertEquals(1,store.owed(NoteStore.Branch.Kind.PAGE,note.id).size());
        assertEquals(top,store.sharedAt(store.pathOf(note.id)).target);
        assertEquals("Home life collection",store.grantedBy(Sharing.Scope.PAGE,note.id,A));
        // Nothing goes inside itself, or inside anything it holds; and a place to carry it never offers either.
        assertThrows(IllegalArgumentException.class,()->store.moveBook(top,inner));
        assertThrows(IllegalArgumentException.class,()->store.moveBook(top,top));
        assertThrows(IllegalArgumentException.class,()->store.moveBook(top,note.id));
        List<String> places=new ArrayList<>();
        for(NoteStore.Branch one:store.places(new NoteStore.Branch(NoteStore.Branch.Kind.COLLECTION,middle,top,"Kitchen","",0,0,true)))places.add(one.id);
        assertTrue(places.contains(Sharing.EVERYTHING));assertTrue(places.contains(top));
        assertFalse(places.contains(middle));assertFalse(places.contains(inner));
        // Put away in the middle, and everything under it has gone away with it; brought back, whatever held it comes too.
        store.putAway(NoteStore.Branch.Kind.COLLECTION,middle,true,true);
        assertTrue(store.pagesUnder(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING).stream().noneMatch(one->one.id.equals(note.id)));
        assertFalse(store.stillThere(NoteStore.Branch.Kind.COLLECTION,inner));
        store.restore(NoteStore.Branch.Kind.PAGE,note.id);
        assertTrue(store.stillThere(NoteStore.Branch.Kind.COLLECTION,inner));
        assertEquals(1,store.pagesUnder(NoteStore.Branch.Kind.COLLECTION,top).size());
        // Moved onto Home, a collection's undo puts it back where it was.
        String was=store.collectionOfBook(middle);
        store.moveBook(middle,"");assertEquals("",store.collectionOfBook(middle));
        store.moveBook(middle,was);assertEquals(top,store.collectionOfBook(middle));
        // Gone for good: everything inside it, however deep.
        store.erase(NoteStore.Branch.Kind.COLLECTION,top);
        assertNull(store.get(note.id));assertFalse(store.collectionIds().contains(inner));
    }

    /** A note arriving with a path of three collections, as a build that knows about trees sends it. */
    private static Parcel.Sent pathed(String title,String middleName) {
        List<Parcel.Step> path=List.of(new Parcel.Step("their-top","Garden","",3,7),new Parcel.Step("their-middle",middleName,"",0,1),
            new Parcel.Step("their-inner","Seeds","",5,2));
        return new Parcel.Sent("their-middle",middleName,"their-inner","Seeds",title,"Synthetic "+title,true,
            Collections.emptyList(),"","",true,-1,true,List.of(),1L,List.of(),path,"",null);
    }

    @Test public void whatArrivesIsBuiltFromTheTopOfItsPathDown() throws Exception {
        NoteStore store=fresh();
        int before=store.collectionIds().size();
        String one=UUID.randomUUID().toString(), two=UUID.randomUUID().toString();
        store.landed(one,A,1,pathed("Tomatoes","Beds"),store.someBook());
        List<String> up=store.above(one);
        assertEquals(3,up.size());
        assertEquals(before+3,store.collectionIds().size());
        assertEquals(Things.HOME,text(store,"SELECT parent FROM things WHERE id=?",up.get(0)));
        assertEquals(up.get(0),text(store,"SELECT parent FROM things WHERE id=?",up.get(1)));
        assertEquals(up.get(1),text(store,"SELECT parent FROM things WHERE id=?",up.get(2)));
        assertEquals("Garden",store.nameOf(up.get(0),true));assertEquals("Beds",store.nameOf(up.get(1),true));assertEquals("Seeds",store.nameOf(up.get(2),true));
        // Theirs, from them; and their colour and place used as each was made.
        assertEquals(1,count(store,"SELECT theirs FROM things WHERE id=?",up.get(2)));
        assertEquals(3,store.colourOf(NoteStore.Branch.Kind.COLLECTION,up.get(0)));
        assertEquals(7,count(store,"SELECT ordinal FROM things WHERE id=?",up.get(0)));
        // A second note from the same path lands beside the first, and nothing is built twice. A name they changed is
        // followed; where the collection is here is not.
        store.moveBook(up.get(2),up.get(0));
        store.landed(two,A,1,pathed("Beans","Raised beds"),store.someBook());
        assertEquals(before+3,store.collectionIds().size());
        assertEquals(up.get(2),store.bookOf(two));
        assertEquals("Raised beds",store.nameOf(up.get(1),true));
        assertEquals(up.get(0),store.collectionOfBook(up.get(2)));
        // A path with nothing on it is a note on their Home, which lands on Home here.
        String loose=UUID.randomUUID().toString();
        store.landed(loose,A,1,new Parcel.Sent("","","","","Loose","Synthetic loose",true,Collections.emptyList(),"","",true,-1,true,
            List.of(),1L,List.of(),List.of(),"",null),store.someBook());
        assertEquals("",store.bookOf(loose));assertEquals(List.of(),store.above(loose));
    }

    @Test public void aNoteFromADeviceBeforeTreesLandsInItsCollectionAndBookAsBefore() throws Exception {
        NoteStore store=fresh();
        String id=UUID.randomUUID().toString();
        Parcel.Sent parcel=new Parcel.Sent("c-theirs","From phone","b-theirs","Shared notes","A note","Synthetic from 0.1",false);
        assertNull(parcel.path);
        store.landed(id,A,1,parcel,store.someBook());
        List<String> up=store.above(id);
        assertEquals(2,up.size());
        assertEquals("From phone",store.nameOf(up.get(0),true));assertEquals("Shared notes",store.nameOf(up.get(1),true));
        assertEquals("",store.collectionOfBook(up.get(0)));
        // The same book, and a second note: beside the first.
        String next=UUID.randomUUID().toString();
        store.landed(next,A,1,new Parcel.Sent("c-theirs","From phone","b-theirs","Shared notes","Another","Synthetic",false),store.someBook());
        assertEquals(up.get(1),store.bookOf(next));
    }

    @Test public void aNoteThatDoesNotFitThreeLevelsWaitsForTrees() {
        assertFalse(Post.waitsForTrees(false,List.of("c","b")));
        assertTrue(Post.waitsForTrees(false,List.of("c")));
        assertTrue(Post.waitsForTrees(false,List.of()));
        assertTrue(Post.waitsForTrees(false,List.of("a","b","c")));
        assertFalse(Post.waitsForTrees(true,List.of("a","b","c")));
        assertFalse(Post.waitsForTrees(true,List.of()));
        // The level a list is said at: as it is kept to a build that knows trees, by depth to one that does not.
        assertEquals(Sharing.Scope.THING,Post.scopeSaid(true,Sharing.Scope.THING,List.of("a")));
        assertEquals(Sharing.Scope.COLLECTION,Post.scopeSaid(false,Sharing.Scope.THING,List.of()));
        assertEquals(Sharing.Scope.BOOK,Post.scopeSaid(false,Sharing.Scope.THING,List.of("a")));
        assertEquals(Sharing.Scope.BOOK,Post.scopeSaid(false,Sharing.Scope.COLLECTION,List.of("a")));
        assertNull(Post.scopeSaid(false,Sharing.Scope.THING,List.of("a","b")));
        assertEquals(Sharing.Scope.PAGE,Post.scopeSaid(false,Sharing.Scope.PAGE,List.of("a","b")));
        assertNull(Post.scopeSaid(false,null,List.of()));
        // And what the owner is told about it.
        Unsent.Problem waits=new Unsent.Problem(Unsent.Why.NEEDS_UPDATE,"Ana's phone","Seeds","","");
        assertEquals("Ana's phone needs to update Mininotes to receive this.",Unsent.said(waits,"this PC"));
        assertEquals(Unsent.Fix.NONE,Unsent.fix(waits));
    }

    @Test public void aCollectionWithNoNoteInItTravelsOnItsOwn() throws Exception {
        NoteStore store=fresh();
        String empty=store.addCollection("Plans").id, full=store.addCollection("Lists").id;
        NoteStore.Note note=new NoteStore.Note();note.book=full;note.body="Synthetic list";store.save(note);
        store.addShare(new Sharing.Rule(Sharing.Scope.COLLECTION,empty,A,true));
        store.addShare(new Sharing.Rule(Sharing.Scope.COLLECTION,full,A,true));
        // Owed on its own: the empty one only, since the note carries the other with it.
        List<String> owed=new ArrayList<>();for(Outbox.Wait one:store.cartonsOwed(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING))owed.add(one.page+" "+one.address);
        assertEquals(List.of(empty+" "+A),owed);
        Carton.Sent carton=store.carton(empty,A,true);
        assertEquals("Plans",carton.name);assertEquals(Sharing.Scope.THING.name(),carton.scope);assertEquals(empty,carton.target);
        assertTrue(carton.writes);assertNotNull(Carton.open(Carton.wrap(carton)));
        // Answered: had, until it changes again.
        long revision=store.revisionOf(empty);
        assertFalse(store.collectionAcknowledged(A,Things.envelopeId(empty),revision+1,true));
        assertTrue(store.collectionAcknowledged(A,Things.envelopeId(empty),revision,true));
        assertTrue(store.cartonsOwed(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING).isEmpty());
        store.renameCollection(empty,"Plans for May");
        assertEquals(revision+1,store.revisionOf(empty));
        assertEquals(1,store.cartonsOwed(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING).size());
    }

    @Test public void aCollectionArrivingOnItsOwnIsBuiltWhereItsPathSays() throws Exception {
        Context pcContext=new Context(temp.newFolder("pc")),phoneContext=new Context(temp.newFolder("phone"));
        NoteStore pc=store(pcContext);pc.getWritableDatabase();
        Keys pcKeys=new Keys(pcContext),phoneKeys=new Keys(phoneContext);
        pc.mySigningKey=Base64.getEncoder().encodeToString(pcKeys.signing().getPublic().getEncoded());
        pc.pairedWith(B,"Test phone",false,phoneKeys.agreement().getPublic().getEncoded(),phoneKeys.signing().getPublic().getEncoded());
        String theirs=UUID.randomUUID().toString();
        Carton.Sent carton=new Carton.Sent(theirs,"Ideas","",null,2,4,List.of(new Parcel.Step("their-top","Work","",0,1)),true,
            Sharing.Scope.THING.name(),theirs,List.of(),false,null,0L);
        // Not asking to be answered: an answer would start this PC's node, and this test has no network.
        byte[] sealed=Envelope.seal(Things.envelopeId(theirs),3,1,Carton.wrap(carton),phoneKeys.signing(),pcKeys.agreement().getPublic());
        Post.arrived(pcContext,pc,pcKeys,sealed);
        String here=null;
        for(String id:pc.collectionIds())if("Ideas".equals(pc.nameOf(id,true)))here=id;
        assertNotNull("built here",here);
        List<String> up=pc.above(here);
        assertEquals(1,up.size());assertEquals("Work",pc.nameOf(up.get(0),true));
        assertEquals(3,pc.revisionOf(here));
        // Whoever sent it has it, at the revision it came at: nothing is owed straight back to them.
        assertTrue(pc.everybodyIn(NoteStore.Branch.Kind.COLLECTION,here).contains(B));
        assertTrue(pc.cartonsOwed(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING).isEmpty());
    }

    @Test public void aBackupFromBeforeTreesComesInWithEachBookInItsCollection() throws Exception {
        NoteStore store=fresh();
        String note=UUID.randomUUID().toString();
        String backup="{\"app\":\"mininotes.v1\",\"version\":1,\"collections\":[{\"id\":\"c-trips\",\"name\":\"Trips\",\"place\":1}],"
            +"\"books\":[{\"id\":\"b-istanbul\",\"collection\":\"c-trips\",\"name\":\"Istanbul\",\"colour\":2}],"
            +"\"notes\":[{\"id\":\""+note+"\",\"title\":\"Ferry\",\"body\":\"Synthetic ferry times\",\"tag\":\"Personal\",\"book\":\"b-istanbul\","
            +"\"pinned\":false,\"deleted\":false,\"updated\":5}]}";
        assertEquals(1,store.importBackup(backup,true));
        assertEquals(List.of("c-trips","b-istanbul"),store.above(note));
        assertEquals(2,store.colourOf(NoteStore.Branch.Kind.COLLECTION,"b-istanbul"));
        // And written again, it says things, and comes back the same.
        String again=store.backup();
        assertTrue(again.contains("\"things\""));
        NoteStore other=fresh();
        assertEquals(1,other.importBackup(again,true));
        assertEquals(List.of("c-trips","b-istanbul"),other.above(note));
    }
}
