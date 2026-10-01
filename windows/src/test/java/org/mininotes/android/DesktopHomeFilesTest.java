package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.mininotes.desktop.platform.content.Context;
import org.mininotes.desktop.platform.database.Cursor;

/**
 * Home as the new screens draw it, on the real notebook (docs/HOME.md, step 2): received files moved to Home by schema 31
 * and checked by its own counts, a file arriving since landing on Home and nowhere else, deleting one, the grid's one
 * order of collections and notes with the files after them, the dock, looking everywhere, a note on Home, two notes put
 * together, moving on the grid, and the PC's Home showing what came. Synthetic notebooks only; no network.
 */
public class DesktopHomeFilesTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final List<NoteStore> open=new ArrayList<>();
    @After public void close(){for(NoteStore one:open)one.close();}

    private static final String A="MxHomeFilesA@127.0.0.1:9401", B="MxHomeFilesB@127.0.0.1:9402";
    private static final NoteStore.Branch.Kind PAGE=NoteStore.Branch.Kind.PAGE,COLLECTION=NoteStore.Branch.Kind.COLLECTION,FILE=NoteStore.Branch.Kind.FILE;

    private NoteStore store(Context context){NoteStore store=new NoteStore(context);open.add(store);return store;}
    private NoteStore fresh() throws Exception {NoteStore store=store(new Context(temp.newFolder()));store.getWritableDatabase();return store;}

    private static void run(java.sql.Connection c,String sql) throws Exception {try(java.sql.Statement s=c.createStatement()){s.execute(sql);}}
    private static long one(java.sql.Connection c,String sql) throws Exception {
        try(java.sql.Statement s=c.createStatement();java.sql.ResultSet r=s.executeQuery(sql)){return r.next()?r.getLong(1):-1;}
    }
    private static long[] counts(File file,String[] asks) throws Exception {
        long[] out=new long[asks.length];
        try(java.sql.Connection c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+file)){for(int at=0;at<asks.length;at++)out[at]=one(c,asks[at]);}
        return out;
    }
    private static long count(NoteStore store,String sql,String... args) {
        try(Cursor c=store.getReadableDatabase().rawQuery(sql,args)){return c.moveToFirst()?c.getLong(0):-1;}
    }
    private static byte[] bytes(int size,int seed){byte[] b=new byte[size];new Random(seed).nextBytes(b);return b;}

    private static final String F1="f1111111-1111-4111-8111-111111111111",F2="f2222222-2222-4222-8222-222222222222",
        F3="f3333333-3333-4333-8333-333333333333",F4="f4444444-4444-4444-8444-444444444444",F5="f5555555-5555-4555-8555-555555555555",
        F6="f6666666-6666-4666-8666-666666666666",F7="f7777777-7777-4777-8777-777777777777",NF="f8888888-8888-4888-8888-888888888888",
        N1="11111111-1111-4111-8111-111111111111";

    /**
     * A notebook as 0.2.001 left it, at schema 30, with its drop box: two sendings that came - one looked at, one not - one
     * still coming with a file here and one not, one asking, and one this device sent; and a note keeping a file of its own.
     * {@code clash} gives the note's file the id of a received one, which the move to Home would have to lose.
     */
    private File version30(Context context,boolean clash) throws Exception {
        File file=new File(context.getFilesDir(),"mininotes.db");
        try(java.sql.Connection c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+file)) {
            run(c,SchemaMigrations.create().get(0));
            run(c,"PRAGMA user_version=1");
            for(String sql:SchemaMigrations.upgrade(1,30))run(c,sql);
            run(c,"PRAGMA user_version=30");
            run(c,"INSERT INTO notes(id,title,body,notebook,pinned,deleted,updated,book) VALUES('"+N1+"','Trip','Synthetic trip','Personal',0,0,10,'"+Things.HOME+"')");
            run(c,"INSERT INTO files(id,note,name,kind,bytes,added,held) VALUES('"+(clash?F1:NF)+"','"+N1+"','ticket.txt','text/plain',9,1,'note')");
            run(c,"INSERT INTO transfers(id,wire,way,address,name,state,at,seen) VALUES"
                +"('t1','w1','in','"+A+"','Work laptop',"+Drop.HERE+",1000,0),('t2','w2','in','"+B+"','Ana',"+Drop.HERE+",2000,1),"
                +"('t3','w3','in','"+A+"','Work laptop',"+Drop.FETCHING+",3000,0),('t4','w4','in','"+B+"','Ana',"+Drop.ASKING+",4000,0),"
                +"('t5','w5','out','"+A+"','Work laptop',"+Drop.WAITING+",500,1)");
            run(c,"INSERT INTO transferred(id,batch,theirs,name,kind,bytes,here) VALUES"
                +"('"+F1+"','t1','x1','beach.jpg','image/jpeg',3,1),('"+F2+"','t1','x2','list.pdf','application/pdf',4,1),"
                +"('"+F3+"','t2','x3','song.mp3','audio/mpeg',5,1),('"+F4+"','t3','x4','half.txt','text/plain',6,1),"
                +"('"+F5+"','t3','x5','later.txt','text/plain',7,0),('"+F6+"','t4','x6','asked.txt','text/plain',8,0),"
                +"('"+F7+"','t5','"+F7+"','sent.txt','text/plain',2,1)");
        }
        File shed=new File(context.getFilesDir(),"files");shed.mkdirs();
        int seed=0;for(String id:new String[]{F1,F2,F3,F4,F7,NF})Files.write(new File(shed,id).toPath(),bytes(id.equals(NF)?9:3,seed++));
        return file;
    }

    private static List<String> ids(List<NoteStore.Branch> lines){List<String> out=new ArrayList<>();for(NoteStore.Branch one:lines)out.add(one.id);return out;}
    private static List<String> ids(List<NoteStore.Branch> lines,NoteStore.Branch.Kind kind) {
        List<String> out=new ArrayList<>();for(NoteStore.Branch one:lines)if(one.kind==kind)out.add(one.id);return out;
    }
    private static NoteStore.Branch line(List<NoteStore.Branch> lines,String id){for(NoteStore.Branch one:lines)if(one.id.equals(id))return one;throw new AssertionError("no line "+id);}

    @Test public void schema31MovesEveryReceivedFileToHomeAndCountsTheSame() throws Exception {
        Context context=new Context(temp.newFolder("moved"));
        File file=version30(context,false);
        long[] before=counts(file,SchemaMigrations.COUNT_BEFORE_HOME);
        assertArrayEquals("four here, a file of a note, three not looked at",new long[]{4,5,3},before);
        NoteStore store=store(context);store.getWritableDatabase();
        assertEquals(SchemaMigrations.VERSION,count(store,"PRAGMA user_version"));
        long[] after=counts(file,SchemaMigrations.COUNT_AFTER_HOME);
        assertNull(SchemaMigrations.differs(SchemaMigrations.COUNTED_HOME,before,after));
        // On Home under their own ids, the newest first, each from the device it came from, new where nobody had looked.
        List<NoteStore.Branch> home=store.contents(Things.HOME);
        List<String> files=ids(home,FILE);
        assertEquals(4,files.size());
        assertEquals(F4,files.get(0));assertEquals(F3,files.get(1));assertEquals(Set.of(F1,F2),new HashSet<>(files.subList(2,4)));
        assertTrue(line(home,F4).fresh);assertFalse("its sending was looked at",line(home,F3).fresh);assertTrue(line(home,F1).fresh);
        assertEquals(A,line(home,F1).origin);assertEquals(B,line(home,F3).origin);
        assertEquals("JPG · 3 bytes",line(home,F1).detail);assertEquals(Sharing.EVERYTHING,line(home,F1).parent);
        assertEquals(3,store.freshOnHome());
        assertEquals(1000,store.file(F1).added);
        // Nothing copied: the same bytes, kept by the sweep, counted once, sealed once.
        store.sweep();
        for(String id:new String[]{F1,F2,F3,F4,F7,NF})assertTrue(id,store.fileFor(id).isFile());
        assertEquals("each counted once",9+3+4+5+6+2,store.weight());
        List<String> kept=new ArrayList<>();for(NoteStore.Held one:store.everyFileKept())kept.add(one.id);
        assertEquals(new HashSet<>(List.of(F1,F2,F3,F4,F7,NF)),new HashSet<>(kept));assertEquals(6,kept.size());
        // The drop box lists what is still coming, somebody asking, and what went - never what came.
        List<String> listed=new ArrayList<>();for(NoteStore.Transfer one:store.transfers())listed.add(one.id);
        assertEquals(List.of("t4","t3","t5"),listed);
        NoteStore.Transfer coming=store.transfers().get(1);
        assertEquals("only what is still to come",1,coming.files.size());assertEquals(F5,coming.files.get(0).id);
        assertEquals(1,store.freshTransfers());assertEquals(0,store.looseHere());
        assertNull("not among the received",store.loose(F1));assertNotNull(store.keptFile(F1));
        // The sendings stay behind them, counting every file, to answer the devices that sent them.
        assertEquals(2,store.transfer("t1").files.size());assertTrue(store.transfer("t1").allHere());
        assertTrue(store.transfer("t1").files.get(0).moved);
        assertEquals(Receipt.DROP_HAVE,Drop.answerAgain(store.transferOnTheWire("w1",A,false).state));
        assertEquals(List.of("t5"),sendings(store.sentFiles()));
        // What was sent from here, and what is still coming, are untouched.
        assertEquals(0,count(store,"SELECT moved FROM transferred WHERE id=?",F7));
        assertEquals(0,count(store,"SELECT moved FROM transferred WHERE id=?",F5));
        assertEquals(PAGE,store.file(NF).held);
    }

    private static List<String> sendings(List<NoteStore.Transfer> all){List<String> out=new ArrayList<>();for(NoteStore.Transfer one:all)out.add(one.id);return out;}

    @Test public void aMoveToHomeThatWouldLoseAFileIsRefusedAndTheNotebookIsLeftAt30() throws Exception {
        Context context=new Context(temp.newFolder("clash"));
        File file=version30(context,true);
        NoteStore store=store(context);
        IllegalStateException refused=assertThrows(IllegalStateException.class,store::getWritableDatabase);
        assertTrue(refused.getMessage(),refused.getMessage().startsWith("The received files could not be moved to Home: received files 4 became 3"));
        assertTrue(refused.getMessage(),refused.getMessage().endsWith("Nothing was changed; Mininotes 0.2.001 still opens it."));
        try(java.sql.Connection c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+file)) {
            assertEquals(30,one(c,"PRAGMA user_version"));
            assertEquals(0,one(c,"SELECT COUNT(*) FROM pragma_table_info('transferred') WHERE name='moved'"));
            assertEquals(0,one(c,"SELECT COUNT(*) FROM files WHERE note='"+Things.HOME+"'"));
        }
    }

    /** A file offered by one of the owner's devices and fetched, as Post fetches it. @return its id here */
    private static String received(NoteStore store,String from,String name,byte[] plain) throws Exception {
        String wire=UUID.randomUUID().toString();
        NoteStore.Transfer one=store.offerArrived(from,"Work laptop",wire,1,
            List.of(new Enclosure.Listed(UUID.randomUUID().toString(),name,"application/octet-stream",plain.length,"pieces")),Drop.FETCHING);
        assertTrue(store.looseArrived(one.files.get(0),plain));
        store.settle(one.id,Drop.HERE);
        return one.files.get(0).id;
    }

    @Test public void aFileThatArrivesLandsOnHomeNewAndNotInTheDropBox() throws Exception {
        NoteStore store=fresh();
        byte[] plain=bytes(2048,1);
        String wire=UUID.randomUUID().toString();
        NoteStore.Transfer sending=store.offerArrived(A,"Work laptop",wire,1,
            List.of(new Enclosure.Listed(UUID.randomUUID().toString(),"beach.jpg","image/jpeg",plain.length,"pieces")),Drop.FETCHING);
        assertEquals("coming, and listed while it comes",List.of(sending.id),sendings(store.transfers()));
        String id=sending.files.get(0).id;
        assertTrue(store.looseArrived(sending.files.get(0),plain));
        assertFalse("never kept twice",store.looseArrived(sending.files.get(0),plain));
        // Every file is here, which is what the device that sent them is told.
        assertTrue(store.transfer(sending.id).allHere());
        store.settle(sending.id,Drop.HERE);
        NoteStore.Held kept=store.file(id);
        assertNotNull("kept on Home",kept);
        assertEquals(Things.HOME,kept.note);assertEquals(COLLECTION,kept.held);assertEquals(A,kept.origin);assertTrue(kept.fresh);
        assertEquals("the sending it came in","Work laptop",store.sendingOf(id).name);assertEquals(sending.id,store.sendingOf(id).id);
        assertEquals(List.of(id),ids(store.contents(Things.HOME),FILE));
        assertTrue(store.contents(Things.HOME).get(store.contents(Things.HOME).size()-1).fresh);
        assertTrue("nothing to list in the drop box",store.transfers().isEmpty());
        assertEquals(0,store.looseHere());assertEquals(0,store.freshTransfers());assertEquals(1,store.freshOnHome());
        // Opened: not new any more.
        store.opened(id);
        assertEquals(0,store.freshOnHome());assertFalse(store.file(id).fresh);
        // One copy of the bytes, counted and sealed once, kept by the sweep.
        store.sweep();assertTrue(store.fileFor(id).isFile());
        assertEquals(2048,store.weight());assertEquals(1,store.everyFileKept().size());
        // Offered again - the sender had not heard - it is answered, not fetched, and nothing is listed twice.
        assertEquals(Receipt.DROP_HAVE,Drop.answerAgain(store.transferOnTheWire(wire,A,false).state));
        assertEquals(1,store.contents(Things.HOME).stream().filter(l->l.kind==FILE).count());
        assertTrue(store.transfers().isEmpty());
    }

    @Test public void deletingAFileOnHomeTakesItsBytesOnceAndLeavesTheOthers() throws Exception {
        NoteStore store=fresh();
        String one=received(store,A,"one.txt",bytes(100,2)),two=received(store,A,"two.txt",bytes(200,3)),three=received(store,B,"three.txt",bytes(300,4));
        store.drop(one);
        assertNull(store.file(one));assertFalse(store.fileFor(one).isFile());assertNull(store.keptFile(one));
        assertTrue(store.fileFor(two).isFile());
        store.sweep();
        assertTrue("the sweep keeps what Home keeps",store.fileFor(two).isFile());assertTrue(store.fileFor(three).isFile());
        assertEquals(500,store.weight());
        // Deleted from the drop box's own word for it, it goes from Home the same way.
        store.deleteLoose(two);
        assertNull(store.file(two));assertFalse(store.fileFor(two).isFile());
        // Moved into a collection that is then deleted for good: its bytes go with it, and nothing keeps them alive.
        String shelf=store.addCollectionIn(Things.HOME,"Holiday").id;
        store.moveInto(FILE,three,shelf);
        store.erase(COLLECTION,shelf);
        assertNull(store.file(three));assertFalse(store.fileFor(three).isFile());
        assertEquals(0,store.weight());
        // The sendings stay behind, to answer an offer again.
        assertEquals(3,count(store,"SELECT COUNT(*) FROM transferred WHERE moved=1"));
    }

    private static NoteStore.Note note(NoteStore store,String in,String title,String body) {
        NoteStore.Note n=new NoteStore.Note();n.book=in;n.title=title;n.body=body;store.save(n);return n;
    }

    @Test public void homeAndACollectionListCollectionsAndNotesInOneOrderThenTheirFiles() throws Exception {
        NoteStore store=fresh();
        NoteStore.Shelf kitchen=store.addCollectionIn(Things.HOME,"Kitchen");
        NoteStore.Note loose=note(store,Things.HOME,"Loose","Synthetic loose note");
        NoteStore.Shelf soups=store.addCollectionIn(kitchen.id,"Soups");
        NoteStore.Note leek=note(store,kitchen.id,"Leek","Synthetic leek soup");
        String beach=received(store,A,"beach.jpg",bytes(5,5));
        NoteStore.Held map=store.opening(COLLECTION,kitchen.id,"map.txt","text/plain",3);
        Files.write(store.fileFor(map.id).toPath(),bytes(3,6));store.keep(map);
        List<NoteStore.Branch> home=store.contents(Things.HOME);
        // Collections and notes, then the files: the received one last, new, from where it came.
        assertEquals(FILE,home.get(home.size()-1).kind);assertEquals(beach,home.get(home.size()-1).id);
        assertEquals(Set.of(SchemaMigrations.FIRST_COLLECTION,kitchen.id,loose.id),new HashSet<>(ids(home.subList(0,home.size()-1))));
        assertEquals("1 collection · 1 note",line(home,kitchen.id).detail);
        assertEquals(PAGE,line(home,loose.id).kind);assertEquals(Sharing.EVERYTHING,line(home,loose.id).parent);
        // One order for both kinds, as the owner leaves it.
        store.order(List.of(line(home,loose.id),line(home,SchemaMigrations.FIRST_COLLECTION),line(home,beach),line(home,kitchen.id)));
        assertEquals(List.of(loose.id,SchemaMigrations.FIRST_COLLECTION,kitchen.id,beach),ids(store.contents(Things.HOME)));
        store.order(List.of(line(home,kitchen.id),line(home,loose.id),line(home,SchemaMigrations.FIRST_COLLECTION)));
        assertEquals(List.of(kitchen.id,loose.id,SchemaMigrations.FIRST_COLLECTION,beach),ids(store.contents(Things.HOME)));
        // Home by any of its names.
        assertEquals(ids(store.contents(Things.HOME)),ids(store.contents(Sharing.EVERYTHING)));
        assertEquals(ids(store.contents(Things.HOME)),ids(store.contents("")));
        // A collection one down: its collection and its note, then the file kept with it.
        List<NoteStore.Branch> inside=store.contents(kitchen.id);
        assertEquals(Set.of(soups.id,leek.id),new HashSet<>(ids(inside.subList(0,2))));
        assertEquals(COLLECTION,line(inside,soups.id).kind);assertEquals("Empty",line(inside,soups.id).detail);
        assertEquals(kitchen.id,line(inside,leek.id).parent);
        assertEquals(map.id,inside.get(2).id);assertEquals(FILE,inside.get(2).kind);assertFalse(inside.get(2).fresh);
        assertEquals(kitchen.id,inside.get(2).parent);assertEquals("TXT · 3 bytes",inside.get(2).detail);
        store.order(List.of(line(inside,leek.id),line(inside,soups.id)));
        assertEquals(List.of(leek.id,soups.id,map.id),ids(store.contents(kitchen.id)));
        // A favourite says so where it lives; what is put away is not there.
        store.keepToHand(PAGE,leek.id,true);
        assertTrue(line(store.contents(kitchen.id),leek.id).kept);
        store.putAway(COLLECTION,soups.id,true,true);
        assertEquals(List.of(leek.id,map.id),ids(store.contents(kitchen.id)));
        // The old screens are as they were: inside() still lists Home's collections, then its notes, and no files.
        for(NoteStore.Branch one:store.inside(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING).holds)assertNotEquals(FILE,one.kind);
    }

    @Test public void theDockTakesAPlaceMovesTheRestAlongAndLetsTheLastGo() throws Exception {
        NoteStore store=fresh();
        List<String> n=new ArrayList<>();
        for(int at=1;at<=6;at++)n.add(note(store,Things.HOME,"Note "+at,"Synthetic "+at).id);
        String shelf=store.addCollectionIn(Things.HOME,"Lists").id;
        // A star fills the first free place, as it did.
        store.keepToHand(PAGE,n.get(0),true);store.keepToHand(PAGE,n.get(1),true);store.keepToHand(COLLECTION,shelf,true);
        assertEquals(List.of(n.get(0),n.get(1),shelf),ids(store.dock()));
        // Put at a place: made a favourite, the rest moved along.
        store.toDock(PAGE,n.get(2),2);
        assertEquals(List.of(n.get(0),n.get(2),n.get(1),shelf),ids(store.dock()));
        assertTrue(store.favourite(PAGE,n.get(2)));
        store.toDock(PAGE,n.get(3),1);
        assertEquals(List.of(n.get(3),n.get(0),n.get(2),n.get(1),shelf),ids(store.dock()));
        assertTrue(store.favouritesBeyondDock().isEmpty());
        // A sixth pushes the last out of the dock; it stays a favourite, in Favourites.
        store.toDock(PAGE,n.get(4),3);
        assertEquals(List.of(n.get(3),n.get(0),n.get(4),n.get(2),n.get(1)),ids(store.dock()));
        assertEquals(List.of(shelf),ids(store.favouritesBeyondDock()));
        assertTrue(store.favourite(COLLECTION,shelf));
        assertEquals(6,store.favouriteCount());
        // Moved within the dock: nobody leaves.
        store.toDock(PAGE,n.get(1),1);
        assertEquals(List.of(n.get(1),n.get(3),n.get(0),n.get(4),n.get(2)),ids(store.dock()));
        // Out of the dock: a favourite still, and the rest close up.
        store.outOfDock(PAGE,n.get(0));
        assertEquals(List.of(n.get(1),n.get(3),n.get(4),n.get(2)),ids(store.dock()));
        assertTrue(store.favourite(PAGE,n.get(0)));
        assertEquals(List.of(shelf,n.get(0)),ids(store.favouritesBeyondDock()));
        assertEquals(4,count(store,"SELECT MAX(dock) FROM (SELECT dock FROM things UNION ALL SELECT dock FROM notes)"));
        // A star fills the first free place again.
        store.keepToHand(PAGE,n.get(5),true);
        assertEquals(List.of(n.get(1),n.get(3),n.get(4),n.get(2),n.get(5)),ids(store.dock()));
        // No longer a favourite: out of the dock and out of Favourites.
        store.keepToHand(PAGE,n.get(5),false);
        assertFalse(ids(store.dock()).contains(n.get(5)));assertFalse(ids(store.favouritesBeyondDock()).contains(n.get(5)));
        // Put away, a docked favourite keeps its place unseen; pushed out first when the dock is full.
        store.keepToHand(PAGE,n.get(5),true);
        store.putAway(PAGE,n.get(4),true,true);
        assertEquals(List.of(n.get(1),n.get(3),n.get(2),n.get(5)),ids(store.dock()));
        store.toDock(COLLECTION,shelf,9);
        assertEquals(List.of(n.get(1),n.get(3),n.get(2),n.get(5),shelf),ids(store.dock()));
        assertEquals(0,count(store,"SELECT dock FROM notes WHERE id=?",n.get(4)));
        store.restore(PAGE,n.get(4));
        assertTrue(ids(store.favouritesBeyondDock()).contains(n.get(4)));
        // The dock is this device's own: the lines are the Favourites collection's, marks and all.
        assertTrue(store.dock().get(0).kept);
    }

    @Test public void lookingEverywhereFindsNotesCollectionsAndFilesAndSaysWhereEachIs() throws Exception {
        NoteStore store=fresh();
        NoteStore.Shelf kitchen=store.addCollectionIn(Things.HOME,"Kitchen");
        NoteStore.Shelf soups=store.addCollectionIn(kitchen.id,"Soups");
        NoteStore.Note leek=note(store,kitchen.id,"Leek","Synthetic leek soup for Sunday");
        NoteStore.Note loose=note(store,Things.HOME,"Loose","Synthetic loose words");
        String beach=received(store,A,"beach-sunday.jpg",bytes(5,7));
        NoteStore.Held map=store.opening(COLLECTION,soups.id,"sunday-map.txt","text/plain",3);Files.write(store.fileFor(map.id).toPath(),bytes(3,8));store.keep(map);
        NoteStore.Held recipe=store.opening(PAGE,leek.id,"sunday-recipe.pdf","application/pdf",3);Files.write(store.fileFor(recipe.id).toPath(),bytes(3,9));store.keep(recipe);
        List<NoteStore.Branch> found=store.lookingEverywhere("sunday");
        assertEquals(PAGE,line(found,leek.id).kind);assertTrue(line(found,leek.id).detail,line(found,leek.id).detail.startsWith("in Kitchen · "));
        NoteStore.Branch onHome=line(found,beach);
        assertEquals(FILE,onHome.kind);assertEquals("on Home",onHome.detail);assertEquals(Sharing.EVERYTHING,onHome.parent);assertTrue(onHome.fresh);
        assertEquals("in Kitchen › Soups",line(found,map.id).detail);assertEquals(soups.id,line(found,map.id).parent);
        assertEquals("in Kitchen › Leek",line(found,recipe.id).detail);assertEquals(leek.id,line(found,recipe.id).parent);
        // Collections at any depth by name; a note on Home by its words.
        assertEquals("in Kitchen",line(store.lookingEverywhere("soup"),soups.id).detail);
        assertEquals("on Home",line(store.lookingEverywhere("kitchen"),kitchen.id).detail);
        assertTrue(line(store.lookingEverywhere("loose words"),loose.id).detail.startsWith("on Home · "));
        // Only what is on the shelves: inside something binned is not found.
        store.putAway(COLLECTION,kitchen.id,true,true);
        List<String> after=ids(store.lookingEverywhere("sunday"));
        assertEquals(List.of(beach),after);
        assertTrue(store.lookingEverywhere("  ").isEmpty());
        // And the old search is as it was: notes and collections only.
        for(NoteStore.Branch one:store.looking("beach"))assertNotEquals(FILE,one.kind);
    }

    @Test public void aNoteOnHomeIsListedFoundBackedUpAndOwed() throws Exception {
        NoteStore store=fresh();
        NoteStore.Note loose=note(store,Things.HOME,"On Home","Synthetic note on Home");
        NoteStore.Note named=note(store,Sharing.EVERYTHING,"Also on Home","Synthetic, Home by another name");
        assertEquals(Things.HOME,store.get(loose.id).book);assertEquals("Home is written one way",Things.HOME,store.get(named.id).book);
        assertTrue(ids(store.contents(Things.HOME)).containsAll(List.of(loose.id,named.id)));
        assertTrue(store.stillThere(PAGE,loose.id));assertEquals(List.of(),store.above(loose.id));
        assertEquals("on Home · Synthetic note on Home",line(store.lookingEverywhere("note on home"),loose.id).detail);
        // Owed to whoever the library reaches; a device from before trees waits for an update rather than getting it wrong.
        store.addShare(new Sharing.Rule(Sharing.Scope.LIBRARY,Sharing.EVERYTHING,A,true));
        assertEquals(1,store.owed(PAGE,loose.id).size());
        assertTrue(Post.waitsForTrees(false,store.above(loose.id)));assertFalse(Post.waitsForTrees(true,store.above(loose.id)));
        // Backed up, with the files on Home, and put back whole.
        String file=received(store,A,"beach.jpg",bytes(64,10));
        org.json.JSONObject written=new org.json.JSONObject(store.backup());
        assertEquals(file,written.getJSONArray("home").getJSONObject(0).getString("id"));
        Path zip=temp.getRoot().toPath().resolve("home.zip");DesktopBackup.write(store,zip);
        NoteStore other=fresh();
        DesktopBackup.add(other,zip,null,true);
        assertEquals(Things.HOME,other.get(loose.id).book);
        assertTrue(ids(other.contents(Things.HOME)).containsAll(List.of(loose.id,named.id,file)));
        assertArrayEquals(Files.readAllBytes(store.fileFor(file).toPath()),Files.readAllBytes(other.fileFor(file).toPath()));
        // Added rather than put back: copies, under ids of their own.
        NoteStore third=fresh();
        DesktopBackup.add(third,zip);
        assertEquals(1,ids(third.contents(Things.HOME),FILE).size());assertNotEquals(file,ids(third.contents(Things.HOME),FILE).get(0));
        // A backup from before Home says nothing about the files that came, and putting it back leaves them be.
        String old=new org.json.JSONObject(other.backup()).put("home",(Object)null).toString();
        other.importBackup(old,true);
        assertNotNull(other.file(file));
    }

    @Test public void twoNotesPutTogetherMakeACollectionWhereTheOneLetGoOnWas() throws Exception {
        NoteStore store=fresh();
        NoteStore.Shelf kitchen=store.addCollectionIn(Things.HOME,"Kitchen");
        NoteStore.Note first=note(store,kitchen.id,"First","Synthetic first"),bread=note(store,kitchen.id,"Bread","Synthetic bread"),
            last=note(store,kitchen.id,"Last","Synthetic last"),soup=note(store,Things.HOME,"Soup","Synthetic soup");
        List<NoteStore.Branch> inside=store.contents(kitchen.id);
        store.order(List.of(line(inside,first.id),line(inside,bread.id),line(inside,last.id)));
        NoteStore.Shelf made=store.merge(soup.id,bread.id);
        assertEquals(NoteStore.UNTITLED,made.name);
        assertEquals("where the one let go on was",List.of(first.id,made.id,last.id),ids(store.contents(kitchen.id)));
        assertEquals("that one first",List.of(bread.id,soup.id),ids(store.contents(made.id)));
        assertEquals(List.of(kitchen.id,made.id),store.above(soup.id));
        assertEquals("2 notes",line(store.contents(kitchen.id),made.id).detail);
        assertFalse(ids(store.contents(Things.HOME)).contains(soup.id));
        assertThrows(IllegalArgumentException.class,()->store.merge(first.id,first.id));
        assertThrows(IllegalStateException.class,()->store.merge(first.id,UUID.randomUUID().toString()));
    }

    @Test public void movingOnTheGridGoesWhereItMayAndIsRefusedWhereItMayNot() throws Exception {
        NoteStore store=fresh();
        String top=store.addCollectionIn(Things.HOME,"Top").id,middle=store.addCollectionIn(top,"Middle").id,low=store.addCollectionIn(middle,"Low").id;
        assertEquals(List.of(top,middle),store.above(low));
        assertEquals(NoteStore.UNTITLED,store.addCollectionIn(low,"  ").name);
        assertThrows(IllegalArgumentException.class,()->store.addCollectionIn(UUID.randomUUID().toString(),"Nowhere"));
        // Never inside itself, or inside anything it holds.
        IllegalArgumentException inside=assertThrows(IllegalArgumentException.class,()->store.moveInto(COLLECTION,top,low));
        assertEquals("A collection cannot go inside itself, or inside anything it holds.",inside.getMessage());
        assertThrows(IllegalArgumentException.class,()->store.moveInto(COLLECTION,top,top));
        assertEquals(List.of(top,middle),store.above(low));
        store.moveInto(COLLECTION,low,Things.HOME);assertEquals(List.of(),store.above(low));
        // A note into any collection, and onto Home by any of its names; never into a note.
        NoteStore.Note note=note(store,Things.HOME,"Loose","Synthetic loose"),other=note(store,Things.HOME,"Other","Synthetic other");
        store.moveInto(PAGE,note.id,middle);assertEquals(List.of(top,middle),store.above(note.id));
        store.moveInto(PAGE,note.id,Sharing.EVERYTHING);assertEquals(Things.HOME,store.get(note.id).book);
        assertEquals("Only a collection can hold a note.",assertThrows(IllegalArgumentException.class,()->store.moveInto(PAGE,note.id,other.id)).getMessage());
        // A file off Home into a collection, then into a note, whose file it then is - this device's own there, not new.
        String file=received(store,A,"plan.txt",bytes(10,11));
        store.moveInto(FILE,file,middle);
        assertEquals(List.of(file),ids(store.contents(middle),FILE));assertTrue(ids(store.contents(Things.HOME),FILE).isEmpty());
        assertEquals(0,store.freshOnHome());assertEquals("",store.file(file).origin);
        store.moveInto(FILE,file,note.id);
        assertEquals(1,store.filesOf(PAGE,note.id).size());assertTrue(ids(store.contents(middle),FILE).isEmpty());
        store.moveInto(FILE,file,Things.HOME);
        assertEquals(List.of(file),ids(store.contents(Things.HOME),FILE));
        assertThrows(IllegalArgumentException.class,()->store.moveInto(FILE,file,UUID.randomUUID().toString()));
        // Put in a note the way the drop box always did: the same bytes, under the same id.
        store.intoNote(file,other.id);
        assertEquals(file,store.filesOf(PAGE,other.id).get(0).id);assertTrue(store.fileFor(file).isFile());
    }

    /**
     * The PC's Home shows every file that came, the newest first and new until opened (docs/HOME.md, step 3): the drop box
     * that showed them until Home had a screen has gone, and the file menu's actions work on the file on Home.
     */
    @Test public void thePcsHomeShowsEveryFileThatCameAndItsMenuWorksOnIt() throws Exception {
        NoteStore store=fresh();
        String one=received(store,A,"beach.jpg",bytes(30,12));Thread.sleep(5);String two=received(store,B,"song.mp3",bytes(40,13));
        List<NoteStore.Branch> files=new ArrayList<>();for(NoteStore.Branch line:store.contents(Things.HOME))if(line.kind==FILE)files.add(line);
        assertEquals("the newest first",List.of(two,one),ids(files));
        NoteStore.Branch first=files.get(0);
        assertEquals("song.mp3",first.name);assertTrue("new until it is opened",first.fresh);
        assertEquals("by the name it came under","Work laptop",store.sendingOf(two).name);
        assertEquals(2,store.freshOnHome());
        store.opened(two);assertEquals(1,store.freshOnHome());
        // Its actions work on the file on Home: put in a note, and deleted as Home's file menu deletes it.
        NoteStore.Note note=note(store,Things.HOME,"Holiday","Synthetic holiday");
        store.intoNote(one,note.id);
        store.drop(two);
        assertTrue(ids(store.contents(Things.HOME),FILE).isEmpty());
        assertEquals(one,store.filesOf(PAGE,note.id).get(0).id);assertFalse(store.fileFor(two).isFile());
        assertEquals(0,store.freshOnHome());
    }
}
