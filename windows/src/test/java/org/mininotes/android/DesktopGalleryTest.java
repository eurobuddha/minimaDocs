package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.awt.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;

/** Every window of the pad, drawn with made-up notes and people, saved to look at. Nobody real is in them. */
public class DesktopGalleryTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final Path SHOTS=Path.of("build","verification","gallery");

    @Test public void everyWindowDraws() throws Exception {
        Files.createDirectories(SHOTS);
        Path folder=temp.newFolder("gallery").toPath();Desktop[] app=new Desktop[1];
        SwingUtilities.invokeAndWait(()->{try{app[0]=new Desktop(folder,true);app[0].show();}catch(Exception e){throw new RuntimeException(e);}});
        Desktop pad=app[0];
        try {
            await(()->pad.page.isEditable()&&pad.store.latest()!=null);pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});
            // A pad with something in it: two notes, a second collection, a person, a share, and a version.
            NoteStore store=pad.store;
            var agreement=Envelope.keys();var signing=Envelope.keys();
            store.pairedWith("MxGallery@127.0.0.1:9001","Ana's phone",false,agreement.getPublic().getEncoded(),signing.getPublic().getEncoded());
            store.pairedWith("MxGallery2@127.0.0.1:9002","Work laptop",true,Envelope.keys().getPublic().getEncoded(),Envelope.keys().getPublic().getEncoded());
            // Two names, both made up: this PC's own among the owner's devices, never the machine's real one.
            Node.chooseName(pad.context,"Sam");Node.chooseDevice(pad.context,"Study PC");
            // Collections inside collections, three deep: Kitchen › Recipes › Soups.
            NoteStore.Shelf kitchen=store.addCollection("Kitchen");store.addBook(store.addBook(kitchen.id,"Recipes").id,"Soups");
            SwingUtilities.invokeAndWait(()->{pad.title.setText("Saturday");pad.page.setText("Pick up fresh bread\nTea for the ferry\n\nLeave room for a little wandering.");});
            await(()->pad.store.latest().body.contains("wandering"));
            String id=store.latest().id;store.keepVersion(id,"");
            SwingUtilities.invokeAndWait(()->pad.page.setText("Pick up fresh bread 🥖\nTea for the ferry ☕\nPostcards 📮❤️\n\nLeave room for a little wandering. 😊"));
            await(()->pad.store.latest().body.contains("Postcards"));
            store.setLevel(Sharing.Scope.PAGE,id,"MxGallery@127.0.0.1:9001",Sharing.Level.WRITE,null);
            NoteStore.Note other=new NoteStore.Note();other.book=store.someBook();other.title="Old list";other.body="Batteries";store.save(other);
            store.putAway(NoteStore.Branch.Kind.PAGE,other.id,true,true);
            // Two attachments on the open note: a picture, which shows itself, and a document, which shows its kind.
            java.awt.image.BufferedImage photo=new java.awt.image.BufferedImage(320,200,java.awt.image.BufferedImage.TYPE_INT_RGB);
            {java.awt.Graphics2D g=photo.createGraphics();g.setPaint(new java.awt.GradientPaint(0,0,new java.awt.Color(214,168,96),320,200,new java.awt.Color(84,130,110)));g.fillRect(0,0,320,200);g.dispose();}
            String pictureId=java.util.UUID.randomUUID().toString();javax.imageio.ImageIO.write(photo,"png",store.fileFor(pictureId));
            store.keep(new NoteStore.Held(pictureId,id,"ferry.png","image/png",Files.size(store.fileFor(pictureId).toPath()),System.currentTimeMillis()));
            String documentId=java.util.UUID.randomUUID().toString();Files.writeString(store.fileFor(documentId).toPath(),"%PDF-1.4 synthetic");
            store.keep(new NoteStore.Held(documentId,id,"tickets.pdf","application/pdf",18,System.currentTimeMillis()));
            pad.disk.flush(10000);SwingUtilities.invokeAndWait(pad::refresh);pad.disk.flush(10000);Thread.sleep(400);

            shoot(pad.frame,"01-pad");
            // A note fills the window, with ← Home at the left of its bar (decision 24).
            SwingUtilities.invokeAndWait(()->assertTrue("the page is what is on the screen",pad.onPage()&&pad.page.isShowing()));
            shoot(pad.frame,"96-note-home");
            // Files from Explorer held over the page, then over a note's line in the tree: what they go to lit, and said in the bar.
            SwingUtilities.invokeAndWait(()->DesktopDrops.over(pad,DesktopDrops.aim(pad,pad.page,new java.awt.Point(40,40))));Thread.sleep(400);SwingUtilities.invokeAndWait(pad.frame::validate);shoot(pad.frame,"01d-drop-on-note");
            SwingUtilities.invokeAndWait(()->{pad.showTree(true);pad.frame.validate();});Thread.sleep(300);
            SwingUtilities.invokeAndWait(()->{java.awt.Point at=onRow(pad,"Saturday",0.5f);SwingUtilities.convertPointFromScreen(at,pad.tree);DesktopDrops.over(pad,DesktopDrops.aim(pad,pad.tree,at));});
            Thread.sleep(200);shoot(pad.frame,"01e-drop-on-tree-note");
            SwingUtilities.invokeAndWait(()->DesktopDrops.away(pad));
            SwingUtilities.invokeAndWait(()->pad.showTree(false));
            // Eight files: more than a row holds, so the row has to slide and say there are more.
            for(int more=1;more<=6;more++){String extra=java.util.UUID.randomUUID().toString();Files.writeString(store.fileFor(extra).toPath(),"note "+more);
                store.keep(new NoteStore.Held(extra,id,"extra-"+more+".txt","text/plain",6,System.currentTimeMillis()));}
            SwingUtilities.invokeAndWait(()->{pad.fileCards.show(id);pad.fileCards.open(true);});pad.disk.flush(10000);Thread.sleep(400);
            shoot(pad.frame,"01b-many-files");
            SwingUtilities.invokeAndWait(()->{JScrollBar bar=((JScrollPane)SwingUtilities.getAncestorOfClass(JScrollPane.class,((JPanel)((JScrollPane)pad.fileCards.getComponent(0)).getViewport().getView()))).getHorizontalScrollBar();bar.setValue(bar.getMaximum());});Thread.sleep(300);
            shoot(pad.frame,"01c-many-files-end");
            // Home: what is on it as icons, in as many columns as the window holds; the + at the foot, the search and the dock.
            SwingUtilities.invokeAndWait(pad::goHome);settle(pad);
            SwingUtilities.invokeAndWait(()->assertTrue("Home is what is on the screen",!pad.onPage()&&pad.home.isShowing()));
            shoot(pad.frame,"18-home");
            String book=store.get(id).book;
            // A collection's card over the dimmed Home; one inside it opens in the same card, with ‹ and the one it is in.
            NoteStore.Branch kitchenLine=line(pad,"Kitchen"),recipesLine=line(pad,"Recipes");
            SwingUtilities.invokeAndWait(()->pad.home.opened(kitchenLine,DesktopHome.Where.HOME));settle(pad);shoot(pad.frame,"19-card");
            SwingUtilities.invokeAndWait(()->assertFalse("one + at a time: Home's goes under a card",pad.home.plus.isVisible()));
            SwingUtilities.invokeAndWait(()->pad.home.opened(recipesLine,DesktopHome.Where.CARD));settle(pad);shoot(pad.frame,"19b-card-in-card");
            SwingUtilities.invokeAndWait(()->assertEquals("Recipes",pad.home.folder.shown().name));
            // Esc goes up a level, then closes the card.
            SwingUtilities.invokeAndWait(()->pad.home.escape());settle(pad);
            SwingUtilities.invokeAndWait(()->assertEquals("Kitchen",pad.home.folder.shown().name));
            SwingUtilities.invokeAndWait(()->pad.home.escape());settle(pad);
            SwingUtilities.invokeAndWait(()->assertFalse("closed",pad.home.folder.isOpen()));
            // A collection opened from anywhere else - the tree, the search, the line over a note's title - as its card.
            SwingUtilities.invokeAndWait(()->pad.openCollection(book));settle(pad);shoot(pad.frame,"19c-card-book");
            // A collection with nothing in it says what to do; its + makes a note or a collection in it.
            NoteStore.Shelf garden=store.addCollection("Garden");
            SwingUtilities.invokeAndWait(()->pad.openCollection(garden.id));settle(pad);shoot(pad.frame,"19d-card-empty");
            SwingUtilities.invokeAndWait(()->pad.home.folder.close());settle(pad);
            // Home's +: a note or a collection, as on the phone.
            menuShot(pad,"19e-plus-menu",()->pad.home.plus.doClick());
            // Colours, as the phone gives them: a collection, a book and a note, each its own, at the pad's strength.
            store.paint(NoteStore.Branch.Kind.COLLECTION,kitchen.id,5);
            store.paint(NoteStore.Branch.Kind.BOOK,book,6);store.paint(NoteStore.Branch.Kind.PAGE,id,3);
            SwingUtilities.invokeAndWait(pad::refresh);settle(pad);shoot(pad.frame,"29-colours-home");
            SwingUtilities.invokeAndWait(()->pad.openCollection(book));settle(pad);shoot(pad.frame,"30-colours-card");
            // The menu on an icon: Colour, opened, with the nine lined up and the strength under them.
            SwingUtilities.invokeAndWait(()->pad.shelfMenu(new NoteStore.Branch(NoteStore.Branch.Kind.PAGE,id,book,"Saturday","",0,0,false,3),pad.home,300,200));Thread.sleep(300);
            SwingUtilities.invokeAndWait(()->{try{
                JPopupMenu open=openPopup(pad);JMenu colour=null;
                for(Component c:open.getComponents())if(c instanceof JMenu m&&"Colour".equals(m.getText()))colour=m;
                if(colour==null)throw new AssertionError("no Colour in the menu");
                javax.swing.MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{open,colour,colour.getPopupMenu()});
            }catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(400);
            SwingUtilities.invokeAndWait(()->{try{
                MenuElement[] path=javax.swing.MenuSelectionManager.defaultManager().getSelectedPath();
                picture((JComponent)path[0].getComponent(),"31-card-menu");picture((JComponent)path[path.length-1].getComponent(),"32-colour-menu");
            }catch(Exception e){throw new RuntimeException(e);}});
            SwingUtilities.invokeAndWait(()->javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath());
            // Louder: the strongest tone, the card and the icons drawn again in it.
            SwingUtilities.invokeAndWait(()->pad.useTone(Tint.TONES.length-2));Thread.sleep(300);shoot(pad.frame,"33-colours-loud");
            SwingUtilities.invokeAndWait(()->pad.useTone(Tint.FIRST_TONE));
            SwingUtilities.invokeAndWait(()->pad.open(id));pad.disk.flush(10000);Thread.sleep(300);shoot(pad.frame,"34-coloured-note");
            // Dragging: two more notes in the book, so there is an order to change.
            for(String[] one:new String[][]{{"Tuesday","Bins out"},{"Ferry times","08:10 and 12:40"}}){NoteStore.Note more=new NoteStore.Note();more.book=book;more.title=one[0];more.body=one[1];store.save(more);}
            SwingUtilities.invokeAndWait(pad::refresh);pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});
            NoteStore.Branch bookShown=null;for(NoteStore.Branch one:pad.everything())if(one.id.equals(book))bookShown=one;
            NoteStore.Branch theBook=bookShown;
            SwingUtilities.invokeAndWait(()->pad.openCollection(book));settle(pad);
            // An icon taken hold of in the card and carried to an empty cell: pale where it was, the outline of an icon where it
            // would land (decision 39).
            SwingUtilities.invokeAndWait(()->{
                DesktopHome.Icons card=pad.home.folder.grid;DesktopHome.Tile first=card.tiles.get(0);
                int[] free=null;for(int cell=0;free==null;cell++)if(card.in(cell/card.columns(),cell%card.columns())==null)free=new int[]{cell/card.columns(),cell%card.columns()};
                Rectangle r=card.cellBounds(free[0],free[1]);
                carry(pad,first,card,r.x+r.width/2,r.y+DesktopHome.FACE/2+10);
                assertEquals("the landing outlined",r,card.landing);
                assertTrue(pad.status.getText(),pad.status.getText().startsWith("Let go to put it here"));
            });Thread.sleep(300);shoot(pad.frame,"41-drag-icons");
            // Held over the middle of another note: it is ringed, and letting go would put the two together in a new collection.
            SwingUtilities.invokeAndWait(()->{DesktopHome.Tile onto=tile(pad.home.folder.grid,"Tuesday");carryTo(pad,onto,onto.getWidth()/2,DesktopHome.FACE/2+10);});
            Thread.sleep(300);shoot(pad.frame,"41b-drag-onto-note");
            SwingUtilities.invokeAndWait(()->assertTrue(pad.status.getText().startsWith("Let go to put them together")));
            SwingUtilities.invokeAndWait(pad.home.carry::cancel);
            // A note's line in the tree carried onto another book (a green edge: it goes inside), then between two notes.
            SwingUtilities.invokeAndWait(()->pad.home.folder.close());
            SwingUtilities.invokeAndWait(()->{pad.showTree(true);pad.frame.validate();});Thread.sleep(300);
            SwingUtilities.invokeAndWait(()->{
                pad.moving.begin(line(pad,"Tuesday"),pad.tree);pad.moving.at(onRow(pad,"Recipes",0.5f));
            });Thread.sleep(300);shoot(pad.frame,"42-drag-into-book");
            SwingUtilities.invokeAndWait(()->pad.moving.at(onRow(pad,"Saturday",0.8f)));Thread.sleep(300);shoot(pad.frame,"43-drag-tree-line");
            SwingUtilities.invokeAndWait(pad.moving::cancel);
            // Right-click everywhere: a line of the tree, an icon, Home and a card where there is no icon, an attachment.
            menuShot(pad,"44-menu-tree-row",()->{java.awt.Point at=onRow(pad,"Recipes",0.5f);SwingUtilities.convertPointFromScreen(at,pad.tree);rightClick(pad.tree,at.x,at.y);});
            SwingUtilities.invokeAndWait(()->pad.showTree(false));
            SwingUtilities.invokeAndWait(()->pad.openCollection(book));settle(pad);
            menuShot(pad,"45-menu-icon",()->rightClick(tile(pad.home.folder.grid,"Saturday"),40,40));
            menuShot(pad,"46-menu-room",()->pad.roomMenu(theBook,pad.home.folder.card,420,420));
            SwingUtilities.invokeAndWait(()->pad.home.folder.close());settle(pad);
            menuShot(pad,"46b-menu-home",()->pad.roomMenu(Desktop.library(),pad.home,420,300));
            SwingUtilities.invokeAndWait(()->pad.open(id));pad.disk.flush(10000);Thread.sleep(300);
            NoteStore.Held attached=store.filesOf(NoteStore.Branch.Kind.PAGE,id).get(0);
            menuShot(pad,"47-menu-attachment",()->pad.fileCards.menu(attached).show(pad.page,300,200));
            // A right-click on the page, inside words that are chosen: they stay chosen, and Cut and Copy lead; then the note's menu.
            menuShot(pad,"47b-menu-page",()->{pad.page.select(0,19);
                try{Rectangle at=pad.page.modelToView2D(5).getBounds();rightClick(pad.page,at.x+2,at.y+at.height/2);}catch(Exception e){throw new RuntimeException(e);}});
            assertEquals("the right-click moved the words chosen","Pick up fresh bread",pad.page.getSelectedText());
            // On an address: Open link first.
            menuShot(pad,"47c-menu-page-link",()->{pad.page.setCaretPosition(0);pad.paperMenu(pad.page,new Links.Link(0,4,"https://example.org")).show(pad.page,60,40);});
            // Share, right-clicked: what else sharing offers.
            menuShot(pad,"47e-menu-share-button",()->rightClick(texted(pad.frame.getContentPane(),"Share"),10,10));
            // Move to…: for a note every collection, each with where it is; for a collection the top too, never itself or its inside.
            dialog(pad,"47f-move-note",()->pad.moveTo(line(pad,"Saturday")));
            dialog(pad,"47g-move-collection",()->pad.moveTo(line(pad,"Recipes")));
            // Recording, from a made-up microphone - a quiet tone, ten times faster than life - never the real one.
            // A recording from the phone first (AAC, only its index made up), which the PC hands to its own player.
            java.io.ByteArrayOutputStream m4a=new java.io.ByteArrayOutputStream();
            {java.nio.ByteBuffer mvhd=java.nio.ByteBuffer.allocate(8+100);mvhd.putInt(108).put("mvhd".getBytes()).putInt(0).putInt(0).putInt(0).putInt(1000).putInt(83_000);
             java.nio.ByteBuffer head=java.nio.ByteBuffer.allocate(16+8+108);head.putInt(16).put("ftyp".getBytes()).put("M4A ".getBytes()).putInt(0).putInt(116).put("moov".getBytes()).put(mvhd.array());
             m4a.write(head.array());}
            String phoneSound=java.util.UUID.randomUUID().toString();Files.write(store.fileFor(phoneSound).toPath(),m4a.toByteArray());
            store.keep(new NoteStore.Held(phoneSound,id,"Recording 27 Sep, 09:12.m4a","audio/mp4",m4a.size(),System.currentTimeMillis()));
            pad.recorder.source=()->new DesktopRecorder.Microphone(){
                long at;
                public int read(byte[] into){
                    try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();}
                    for(int i=0;i+1<into.length;i+=2,at++){short s=(short)(3000*Math.sin(2*Math.PI*330*at/Recording.PC_RATE));into[i]=(byte)s;into[i+1]=(byte)(s>>8);}
                    return into.length;
                }
                public void close(){}
            };
            menuShot(pad,"72-clip-menu-record",pad.clip::doClick);
            SwingUtilities.invokeAndWait(pad::record);Thread.sleep(800);
            assertTrue("the recording bar did not show",pad.recorder.isShowing());
            shoot(pad.frame,"70-recording-bar");
            SwingUtilities.invokeAndWait(pad.recorder::stop);pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});pad.disk.flush(10000);
            assertTrue(store.filesOf(NoteStore.Branch.Kind.PAGE,id).stream().anyMatch(f->f.name.startsWith("Recording ")&&f.name.endsWith(".wav")));
            SwingUtilities.invokeAndWait(()->pad.fileCards.open(true));pad.disk.flush(10000);Thread.sleep(400);
            SwingUtilities.invokeAndWait(()->((JScrollPane)pad.fileCards.getComponent(0)).getHorizontalScrollBar().setValue(0));Thread.sleep(300);
            shoot(pad.frame,"71-audio-cards");
            // The sync marks, as the phone draws them: Saturday waits for Ana, Tuesday has reached the laptop, the ferry
            // times are paused here, and a fourth note stays on this PC alone.
            NoteStore.Branch tuesday=line(pad,"Tuesday"),ferry=line(pad,"Ferry times");
            store.setLevel(Sharing.Scope.PAGE,tuesday.id,"MxGallery2@127.0.0.1:9002",Sharing.Level.WRITE,null);
            store.agreedOn("MxGallery2@127.0.0.1:9002",tuesday.id,store.get(tuesday.id).revision);
            store.setLevel(Sharing.Scope.PAGE,ferry.id,"MxGallery@127.0.0.1:9001",Sharing.Level.READ,null);
            store.refuse("MxGallery@127.0.0.1:9001",ferry.id,NoteStore.Branch.Kind.PAGE);
            NoteStore.Note alone=new NoteStore.Note();alone.book=book;alone.title="Wednesday";alone.body="Library books";store.save(alone);
            // Thursday has reached both: the laptop says it matches, Ana's phone only that it came - sent, amber dots.
            NoteStore.Note thursday=new NoteStore.Note();thursday.book=book;thursday.title="Thursday";thursday.body="Market at nine";
            // Written once, as a typed note is: at revision 0 "came" and "matches" are the same row, and it would read as gone.
            thursday.revision=1;store.save(thursday);
            long thursdayRevision=store.get(thursday.id).revision;
            store.setLevel(Sharing.Scope.PAGE,thursday.id,"MxGallery@127.0.0.1:9001",Sharing.Level.WRITE,null);
            store.setLevel(Sharing.Scope.PAGE,thursday.id,"MxGallery2@127.0.0.1:9002",Sharing.Level.WRITE,null);
            store.acknowledged("MxGallery@127.0.0.1:9001",thursday.id,thursdayRevision,false);
            store.agreedOn("MxGallery2@127.0.0.1:9002",thursday.id,thursdayRevision);
            // Friday was changed four days ago for an old tablet that has not been heard from since: red.
            store.pairedWith("MxGallery3@127.0.0.1:9003","Old tablet",false,Envelope.keys().getPublic().getEncoded(),Envelope.keys().getPublic().getEncoded());
            NoteStore.Note friday=new NoteStore.Note();friday.book=book;friday.title="Friday";friday.body="Call about the boat";store.save(friday);
            store.setLevel(Sharing.Scope.PAGE,friday.id,"MxGallery3@127.0.0.1:9003",Sharing.Level.WRITE,null);
            store.setLevel(Sharing.Scope.PAGE,friday.id,"MxGallery2@127.0.0.1:9002",Sharing.Level.WRITE,null);
            store.agreedOn("MxGallery2@127.0.0.1:9002",friday.id,store.get(friday.id).revision);
            store.getWritableDatabase().execSQL("UPDATE notes SET updated=? WHERE id=?",new Object[]{System.currentTimeMillis()-4L*24*60*60*1000,friday.id});
            SwingUtilities.invokeAndWait(pad::refresh);pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});
            SwingUtilities.invokeAndWait(()->pad.openCollection(book));settle(pad);shoot(pad.frame,"51-marks-card");
            SwingUtilities.invokeAndWait(()->pad.home.folder.close());
            Object[][] six={{id,"52-mark-waiting",SyncMark.WAITING},{tuesday.id,"53-mark-gone",SyncMark.GONE},{ferry.id,"54-mark-paused",SyncMark.PAUSED},
                {alone.id,"55-mark-here",SyncMark.HERE},{thursday.id,"57-mark-sent",SyncMark.SENT},{friday.id,"58-mark-stuck",SyncMark.STUCK}};
            for(Object[] one:six) {
                SwingUtilities.invokeAndWait(()->pad.open((String)one[0]));
                // Opening reads the note, then whether it is read only, then where it stands: three trips to the disk.
                try{await(()->pad.noteMark==one[2]);}catch(AssertionError late){SwingUtilities.invokeAndWait(()->assertEquals((String)one[1],one[2],pad.noteMark));}
                pad.disk.flush(10000);Thread.sleep(300);SwingUtilities.invokeAndWait(()->{});
                shoot(pad.frame,(String)one[1]);
            }
            // A round clicked: who, and where they stand, in a small box under it. Friday: the old tablet, in red.
            SwingUtilities.invokeAndWait(()->assertEquals(2,pad.peopleShown.size()));
            menuShot(pad,"59-person-box",()->{int at=0;for(int i=0;i<pad.peopleShown.size();i++)if(pad.peopleShown.get(i).mark()==SyncMark.STUCK)at=i;
                Component round=pad.rounds.getComponent(at);DesktopMark.told(pad.peopleShown.get(at),round).show(round,0,round.getHeight()+4);});
            // Saturday's amber mark clicked: what waits and for whom, in words, with Send now. Its files have not reached
            // Ana's phone, and nor has the writing; her round says the same about her.
            SwingUtilities.invokeAndWait(()->pad.open(id));
            await(()->pad.noteMark==SyncMark.WAITING&&pad.peopleShown.size()==1);pad.disk.flush(10000);Thread.sleep(300);SwingUtilities.invokeAndWait(()->{});
            NoteStore.Branch saturday=line(pad,"Saturday");
            dialog(pad,"59b-what-waits",()->pad.markClicked(saturday,SyncMark.WAITING));
            menuShot(pad,"59c-person-files",()->{Component round=pad.rounds.getComponent(0);DesktopMark.told(pad.peopleShown.get(0),round).show(round,0,round.getHeight()+4);});
            // A note whose list names somebody this PC was never linked with: their round dashed and grey, a broken
            // link on its edge, beside Ana's; clicked, the box that says so, with linking and taking them off.
            NoteStore.Note transfer=new NoteStore.Note();transfer.book=book;transfer.title="Transfer";transfer.body="Boxes by the door";store.save(transfer);
            store.setLevel(Sharing.Scope.PAGE,transfer.id,"MxGallery@127.0.0.1:9001",Sharing.Level.WRITE,null);
            store.mergeMembership(Sharing.Scope.PAGE,transfer.id,java.util.List.of(new Sharing.Rule(Sharing.Scope.PAGE,transfer.id,"MxGalleryListed@127.0.0.1:9009",
                Sharing.Level.WRITE,5L,"gallery-listed")),java.util.Map.of("gallery-listed","Ben's phone"));
            SwingUtilities.invokeAndWait(pad::refresh);pad.disk.flush(10000);
            SwingUtilities.invokeAndWait(()->pad.open(transfer.id));
            await(()->pad.peopleShown.size()==2&&!pad.peopleShown.get(1).linked());pad.disk.flush(10000);Thread.sleep(300);SwingUtilities.invokeAndWait(()->{});
            SwingUtilities.invokeAndWait(()->{assertEquals("Ben's phone",pad.peopleShown.get(1).name());assertEquals("Transfer",pad.peopleShown.get(1).listedIn());});
            shoot(pad.frame,"66-rounds-not-linked");
            {var near=new java.awt.image.BufferedImage(3*72+24,84,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=near.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Desktop.PAPER);g.fillRect(0,0,near.getWidth(),near.getHeight());
                DesktopUi.paintAvatar(g,"Ana",20,12,56);DesktopMark.paintNotLinked(g,"Ben's phone",92,12,56);DesktopMark.paintNotLinked(g,"device 3F9A21",164,12,24);
                g.dispose();javax.imageio.ImageIO.write(near,"png",SHOTS.resolve("66b-not-linked-drawn.png").toFile());}
            dialog(pad,"67-not-linked-box",()->pad.notLinked(pad.peopleShown.get(1)));
            dialog(pad,"67b-take-them-off",()->pad.takeOff("Ben's phone","Transfer",pad.peopleShown.get(1).listing()));
            // The six drawn large, to look at the drawing itself, and each at the size it has under a title.
            {var big=new java.awt.image.BufferedImage(6*72+24,104,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=big.createGraphics();
                g.setColor(Desktop.PAPER);g.fillRect(0,0,big.getWidth(),big.getHeight());
                for(SyncMark what:SyncMark.values()){new DesktopMark(what,48,false).paintIcon(null,g,20+what.ordinal()*72,12);new DesktopMark(what,20,true).paintIcon(null,g,34+what.ordinal()*72,72);}
                g.dispose();javax.imageio.ImageIO.write(big,"png",SHOTS.resolve("56-marks-drawn.png").toFile());}
            SwingUtilities.invokeAndWait(()->pad.open(id));pad.disk.flush(10000);Thread.sleep(200);
            // What the open note's syncing is doing, said after its mark and its people: going, gone, and could not go.
            Object[][] line={{NoteLine.sending(java.util.List.of("Ana's phone")),NoteLine.Tone.GOING,"73-line-sending"},
                {NoteLine.sent(java.util.List.of("Ana's phone")),NoteLine.Tone.DONE,"74-line-sent"},
                {NoteLine.couldNot(java.util.List.of(new Unsent.Problem(Unsent.Why.NOT_REACHED,"Ana's phone","Saturday","","")),0),NoteLine.Tone.FAILED,"75-line-could-not"}};
            for(Object[] one:line){SwingUtilities.invokeAndWait(()->pad.noteSays((String)one[0],(NoteLine.Tone)one[1],null));Thread.sleep(200);
                SwingUtilities.invokeAndWait(()->assertTrue((String)one[2],pad.noteWords.isShowing()));shoot(pad.frame,(String)one[2]);}
            SwingUtilities.invokeAndWait(()->pad.noteSays(null,null,null));
            // Favourites: a note and a book starred, the star after each name so the names keep one column. Unstarred after.
            store.keepToHand(NoteStore.Branch.Kind.PAGE,id,true);store.keepToHand(NoteStore.Branch.Kind.BOOK,book,true);
            SwingUtilities.invokeAndWait(pad::refresh);pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});Thread.sleep(300);
            SwingUtilities.invokeAndWait(()->{pad.showTree(true);pad.tree.scrollRowToVisible(0);});Thread.sleep(300);shoot(pad.frame,"61-favourites-tree");
            SwingUtilities.invokeAndWait(()->pad.showTree(false));
            // The same two in the dock at the foot of Home, a star on their icons where they live.
            SwingUtilities.invokeAndWait(pad::goHome);settle(pad);shoot(pad.frame,"61b-favourites-dock");
            SwingUtilities.invokeAndWait(()->assertEquals(2,pad.home.dock.tiles.size()));
            menuShot(pad,"61c-menu-dock-icon",()->rightClick(pad.home.dock.tiles.get(0),20,20));
            SwingUtilities.invokeAndWait(()->pad.open(id));pad.disk.flush(10000);Thread.sleep(200);
            store.keepToHand(NoteStore.Branch.Kind.PAGE,id,false);store.keepToHand(NoteStore.Branch.Kind.BOOK,book,false);
            SwingUtilities.invokeAndWait(pad::refresh);pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});
            // The locked box with Windows Hello set up: Hello the one filled button, the password chosen, Hello cancelled.
            Path locked=temp.newFolder("locked").toPath();Vault.Made made=Vault.make("password1".toCharArray(),20_000);
            Files.write(locked.resolve(DesktopLock.KEPT),made.kept);Files.write(locked.resolve(DesktopHello.FILE),new byte[]{1});
            DesktopLock.HelloAsk helloWas=DesktopLock.helloAsk;DesktopLock.helloAsk=f->{throw new java.io.IOException("Windows Hello was cancelled.");};
            try {
                dialog(pad,"62-locked-hello",()->{try{DesktopLock.askAtStart(locked,false);}catch(Exception e){throw new RuntimeException(e);}});
                dialog(pad,"63-locked-backup-password",()->{
                    new javax.swing.Timer(300,e->{((javax.swing.Timer)e.getSource()).stop();
                        for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing()&&"Mininotes is locked".equals(d.getTitle())){JButton b=named(d,"unlockBackup");if(b!=null)b.doClick();}
                    }).start();
                    try{DesktopLock.askAtStart(locked,false);}catch(Exception e){throw new RuntimeException(e);}});
                dialog(pad,"64-locked-hello-cancelled",()->{try{DesktopLock.askAtStart(locked,true);}catch(Exception e){throw new RuntimeException(e);}});
                dialog(pad,"65-locked-password-only",()->{try{Files.delete(locked.resolve(DesktopHello.FILE));DesktopLock.askAtStart(locked,false);}catch(Exception e){throw new RuntimeException(e);}});
            } finally{DesktopLock.helloAsk=helloWas;}
            // Two rungs up the ladder, then back to the middle.
            SwingUtilities.invokeAndWait(()->pad.setTextSize(DesktopLook.FIRST+3));Thread.sleep(300);shoot(pad.frame,"35-bigger-text");
            SwingUtilities.invokeAndWait(()->pad.setTextSize(DesktopLook.FIRST));pad.disk.flush(10000);Thread.sleep(200);
            // The tree, the optional panel beside Home (decision 26): switched on, pictured, and off again as it starts.
            SwingUtilities.invokeAndWait(pad::goHome);settle(pad);
            SwingUtilities.invokeAndWait(()->{pad.showTree(true);pad.frame.validate();});Thread.sleep(300);
            // Home's icons beside the tree, each laid out at its size - not left at none by the resize the tree causes.
            SwingUtilities.invokeAndWait(()->{assertFalse(pad.home.grid.tiles.isEmpty());
                for(DesktopHome.Tile one:pad.home.grid.tiles)assertEquals(gridState(pad),DesktopHome.CELL,one.getWidth());});
            shoot(pad.frame,"94-tree-panel");
            SwingUtilities.invokeAndWait(()->assertTrue(pad.treeShown()));
            SwingUtilities.invokeAndWait(()->pad.showTree(false));pad.disk.flush(10000);Thread.sleep(200);
            SwingUtilities.invokeAndWait(()->pad.open(id));pad.disk.flush(10000);Thread.sleep(200);
            dialog(pad,"02-share",pad::share);
            dialog(pad,"03-profile",()->DesktopProfile.open(pad));
            dialog(pad,"04-people",()->pad.people(null));
            dialog(pad,"05-versions",pad::versions);
            dialog(pad,"06-bin",()->pad.restore(true));
            dialog(pad,"07-scanner",()->DesktopScanner.open(pad.frame,code->{}));
            dialog(pad,"27-settings",()->DesktopSettings.open(pad));
            dialog(pad,"27b-settings-direct",()->{
                // The same window, turned down to Direct connections, which sits below the first screen.
                new javax.swing.Timer(300,e->{((javax.swing.Timer)e.getSource()).stop();
                    for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing()&&"Settings".equals(d.getTitle())){
                        JScrollPane pane=(JScrollPane)SwingUtilities.getAncestorOfClass(JScrollPane.class,showing(d.getContentPane(),"Direct connections"));
                        if(pane!=null)pane.getVerticalScrollBar().setValue(560);}
                }).start();
                DesktopSettings.open(pad);});
            // Two relays of the owner's (documentation addresses), kept from here rather than on the window's thread,
            // where writing the settings file held the window up long enough to upset the menu picture below.
            pad.context.getSharedPreferences("node",0).edit().putString("relays","relay.example.org:9001\n203.0.113.5:9001").apply();
            dialog(pad,"27c-settings-relays",()->{
                // One of each state, since the gallery runs no node, and the window turned down to Relays.
                DesktopSettings.stateOf=one->one.startsWith("relay")?"Connected":"Not answering";
                new javax.swing.Timer(300,e->{((javax.swing.Timer)e.getSource()).stop();
                    for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing()&&"Settings".equals(d.getTitle())){
                        Component heading=showing(d.getContentPane(),"Relays");
                        JScrollPane pane=(JScrollPane)SwingUtilities.getAncestorOfClass(JScrollPane.class,heading);
                        if(pane!=null)pane.getVerticalScrollBar().setValue(SwingUtilities.convertPoint(heading,0,0,pane.getViewport().getView()).y-8);}
                }).start();
                DesktopSettings.open(pad);});
            // How notes travel: the two ways, first as a new install has them - helpers, with the relays under them -
            // and then only between the owner's devices, where the relays are not shown.
            dialog(pad,"27d-settings-travel",()->{
                DesktopSettings.stateOf=one->one.startsWith("relay")?"Connected":"Not answering";
                turnTo("How notes travel");
                DesktopSettings.open(pad);});
            pad.context.getSharedPreferences("node",0).edit().putString("travel","mine").apply();
            dialog(pad,"27e-settings-only-mine",()->{turnTo("How notes travel");DesktopSettings.open(pad);});
            // The window's own settings, with the same ten-rung ladder as the menu.
            dialog(pad,"27f-settings-window",()->{turnTo("Window");DesktopSettings.open(pad);});
            // What the marks mean: the six, drawn as everywhere else, a line each.
            dialog(pad,"27g-settings-marks",()->{turnTo("What the marks mean");DesktopSettings.open(pad);});
            pad.context.getSharedPreferences("node",0).edit().putString("travel","helpers").apply();
            DesktopSettings.stateOf=Node::relayState;
            // Files sent on their own: the Send box, for three made-up files and one too big to go.
            Path picked=temp.newFolder("picked").toPath();java.util.List<Path> three=new java.util.ArrayList<>();
            for(String name:new String[]{"ferry-timetable.pdf","harbour.jpg","packing list.txt"}){Path one=picked.resolve(name);Files.write(one,new byte[1500+name.length()*900]);three.add(one);}
            java.util.List<Drop.Device> devices=new java.util.ArrayList<>();
            for(NoteStore.Contact one:store.addresses())if(one.paired())devices.add(new Drop.Device(one.address,one.name,one.mine));
            dialog(pad,"38-send-files",()->DesktopDrops.box(pad,three,java.util.List.of(Given.refusal("holiday film.mov",Drop.TOO_BIG)),
                48_000L,Drop.choices(devices),null));
            // Received: two photographs from the laptop, three files Ana wants to send, and two sendings from here.
            java.util.List<Enclosure.Listed> fromLaptop=java.util.List.of(new Enclosure.Listed(java.util.UUID.randomUUID().toString(),"beach.jpg","image/jpeg",2_400_000,"{}"),
                new Enclosure.Listed(java.util.UUID.randomUUID().toString(),"receipt.pdf","application/pdf",84_000,"{}"));
            NoteStore.Transfer laptop=store.offerArrived("MxGallery2@127.0.0.1:9002","Work laptop",java.util.UUID.randomUUID().toString(),1,fromLaptop,Drop.FETCHING);
            // The photograph a real picture, so its card shows it; the receipt only its kind.
            java.io.ByteArrayOutputStream jpeg=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(photo,"jpg",jpeg);
            for(NoteStore.Loose one:laptop.files)store.looseArrived(one,one.name.equals("beach.jpg")?jpeg.toByteArray():new byte[(int)one.bytes]);
            store.settle(laptop.id,Drop.HERE);
            java.util.List<Enclosure.Listed> fromAna=java.util.List.of(new Enclosure.Listed(java.util.UUID.randomUUID().toString(),"ferry.jpg","image/jpeg",1_300_000,"{}"),
                new Enclosure.Listed(java.util.UUID.randomUUID().toString(),"map.png","image/png",650_000,"{}"),
                new Enclosure.Listed(java.util.UUID.randomUUID().toString(),"tickets.pdf","application/pdf",120_000,"{}"));
            NoteStore.Transfer ana=store.offerArrived("MxGallery@127.0.0.1:9001","Ana's phone",java.util.UUID.randomUUID().toString(),1,fromAna,Drop.ASKING);
            NoteStore.Held going=store.opening(NoteStore.Branch.Kind.PAGE,"","packing list.txt","text/plain",4000);Files.write(store.fileFor(going.id).toPath(),new byte[4000]);
            String toAna=store.sendFiles("MxGallery@127.0.0.1:9001","Ana's phone",java.util.List.of(going));store.offered(toAna);
            NoteStore.Held went=store.opening(NoteStore.Branch.Kind.PAGE,"","harbour.jpg","image/jpeg",900_000);Files.write(store.fileFor(went.id).toPath(),new byte[900_000]);
            String toLaptop=store.sendFiles("MxGallery2@127.0.0.1:9002","Work laptop",java.util.List.of(went));store.settle(toLaptop,Drop.DELIVERED);
            // And two more from the laptop, so the list has something to sort.
            java.util.List<Enclosure.Listed> fromLaptop2=java.util.List.of(new Enclosure.Listed(java.util.UUID.randomUUID().toString(),"budget.xlsx","application/vnd.ms-excel",48_000,"{}"),
                new Enclosure.Listed(java.util.UUID.randomUUID().toString(),"slides.pptx","application/vnd.ms-powerpoint",3_100_000,"{}"));
            NoteStore.Transfer laptop2=store.offerArrived("MxGallery2@127.0.0.1:9002","Work laptop",java.util.UUID.randomUUID().toString(),1,fromLaptop2,Drop.FETCHING);
            for(NoteStore.Loose one:laptop2.files)store.looseArrived(one,new byte[(int)one.bytes]);
            store.settle(laptop2.id,Drop.HERE);
            // What came is on Home, among what is there, each file new until it is opened (decision 6).
            SwingUtilities.invokeAndWait(()->{pad.goHome();pad.refresh();});settle(pad);shoot(pad.frame,"39-home-received");
            SwingUtilities.invokeAndWait(()->{DesktopHome.Tile beach=tile(pad.home.grid,"beach.jpg");assertTrue("new until opened",beach.thing.fresh);});
            // Files from Explorer held over a collection's icon: it lit, to keep them with it; over Home's grid, to keep them on Home.
            SwingUtilities.invokeAndWait(()->{DesktopHome.Tile kitchenIcon=tile(pad.home.grid,"Kitchen");DesktopDrops.over(pad,DesktopDrops.aim(pad,kitchenIcon,new java.awt.Point(30,30)));
                assertEquals("Drop to keep with “Kitchen”",pad.status.getText());});
            Thread.sleep(300);SwingUtilities.invokeAndWait(pad.frame::validate);shoot(pad.frame,"39h-drop-on-collection");SwingUtilities.invokeAndWait(()->DesktopDrops.away(pad));
            SwingUtilities.invokeAndWait(()->{DesktopDrops.Aim aim=DesktopDrops.aim(pad,pad.home.grid,new java.awt.Point(pad.home.grid.getWidth()-30,pad.home.grid.getHeight()-30));
                assertEquals(DesktopDrops.KEEP,aim.does());assertEquals(Things.HOME,aim.note());});
            dialog(pad,"40-files-asking",()->DesktopDrops.ask(pad,ana.id));
            // A right-click on a file's icon: the file's menu, in the phone's order.
            menuShot(pad,"48-menu-file",()->rightClick(tile(pad.home.grid,"beach.jpg"),30,30));
            // What went from here, and what is still coming, now there is no drop box: ⋯ → Sent files (decision 20).
            dialog(pad,"48b-sent-files",()->DesktopDrops.sentFiles(pad));
            // More favourites than the dock has room for: the rest in the Favourites collection, first on Home (decision 3).
            NoteStore.Note packing=new NoteStore.Note();packing.book=Things.HOME;packing.title="Packing list";packing.body="Synthetic packing list";store.save(packing);
            NoteStore.Shelf errands=store.addCollection("Errands");
            for(int n=1;n<=22;n++){NoteStore.Note chore=new NoteStore.Note();chore.book=errands.id;chore.title="Errand "+n;chore.body="Synthetic errand";store.save(chore);
                store.keepToHand(NoteStore.Branch.Kind.PAGE,chore.id,true);}
            SwingUtilities.invokeAndWait(pad::refresh);settle(pad);
            SwingUtilities.invokeAndWait(()->assertTrue("the Favourites icon first",pad.home.grid.tiles.get(0).thing.kind==NoteStore.Branch.Kind.FAVOURITES));
            shoot(pad.frame,"90-home-everything");shoot(pad.frame,"95-dock-more-than-fit");
            SwingUtilities.invokeAndWait(()->pad.home.opened(pad.home.grid.tiles.get(0).thing,DesktopHome.Where.HOME));settle(pad);shoot(pad.frame,"95b-favourites-card");
            SwingUtilities.invokeAndWait(()->pad.home.folder.close());
            // Looking everywhere, from the foot of Home: what is found in the grid's place, each saying where it is.
            SwingUtilities.invokeAndWait(()->pad.home.search.setText("ferry"));Thread.sleep(500);settle(pad);shoot(pad.frame,"98-search");
            SwingUtilities.invokeAndWait(()->pad.home.search.setText(""));Thread.sleep(400);settle(pad);
            // The overview: what is open, as cards, the newest first; Ctrl+Tab chooses the one before the one on the screen.
            SwingUtilities.invokeAndWait(()->pad.openCollection(kitchen.id));settle(pad);
            SwingUtilities.invokeAndWait(()->pad.open(tuesday.id));settle(pad);
            SwingUtilities.invokeAndWait(()->pad.open(id));settle(pad);
            SwingUtilities.invokeAndWait(()->pad.overview.up(false));settle(pad);Thread.sleep(300);shoot(pad.frame,"93-overview");
            SwingUtilities.invokeAndWait(()->pad.overview.close());
            SwingUtilities.invokeAndWait(()->pad.overview.cycle(false));settle(pad);Thread.sleep(300);shoot(pad.frame,"93b-overview-ctrl-tab");
            SwingUtilities.invokeAndWait(()->{pad.overview.goChosen();});settle(pad);
            SwingUtilities.invokeAndWait(()->assertEquals("Ctrl+Tab went back to the note before",tuesday.id,pad.noteShown()));
            // What could not go, by device and thing, and the one thing to do about it: a device only named in a list, and one away.
            dialog(pad,"60-unsent-not-paired",()->pad.tellUnsent(new Post.Done(0,3,"",java.util.List.of(
                new Unsent.Problem(Unsent.Why.ONLY_LISTED,"Ana's laptop","Kitchen","Kitchen","","MxGalleryListed@127.0.0.1:9009"),
                new Unsent.Problem(Unsent.Why.NOT_REACHED,"Work laptop","Kitchen","","")))));
            dialog(pad,"60c-unsent-only-listed",()->pad.tellUnsent(new Post.Done(0,1,"",java.util.List.of(
                new Unsent.Problem(Unsent.Why.ONLY_LISTED,"Ben's phone","","Transfer","","MxGalleryListed@127.0.0.1:9009")))));
            dialog(pad,"60b-unsent-not-in-reach",()->pad.tellUnsent(new Post.Done(1,1,"",java.util.List.of(
                new Unsent.Problem(Unsent.Why.NOT_IN_REACH,"Work laptop","Saturday","","")))));
            SwingUtilities.invokeAndWait(()->pad.open(id));pad.disk.flush(10000);Thread.sleep(200);
            dialogMenu(pad,"49-menu-person",()->pad.people(null),"Work laptop");
            dialogMenu(pad,"50-menu-bin-row",()->pad.restore(true),"Old list");
            dialog(pad,"28-about",pad::about);
            dialog(pad,"08-about",pad::about);
            dialog(pad,"09-new-collection",()->DesktopUi.ask(pad.frame,"New collection","Name",null));
            dialog(pad,"10-confirm",()->DesktopUi.confirm(pad.frame,"Unfollow?","Stop receiving Saturday? Your copy stays on this PC.","Unfollow",true));
            // The code offer: the roles the owner may give, each over what it lets them do.
            dialog(pad,"11-offer-role",()->pad.offerRole("Saturday",Sharing.grantable(true,null)));
            // And as an admin sees it: Can write and Can read only.
            dialog(pad,"11b-offer-role-admin",()->pad.offerRole("Saturday",Sharing.grantable(false,Sharing.Level.ADMIN)));
            // The Share box's role picker, dropped down from the role, and the same on a right-click on the person.
            dialogMenu(pad,"02b-share-role-picker",pad::share,Sharing.Level.WRITE.words()+"  ▾");
            dialogMenu(pad,"02c-share-role-menu",pad::share,"Ana's phone");
            java.util.List<String> sample=java.util.List.of("orbit","velvet","harbor","maple","quiet","lantern","pepper","ribbon","summit","canvas","meadow","ticket");
            dialog(pad,"13-lock-password",()->DesktopLock.choosePassword(pad.frame));
            dialog(pad,"14-lock-words",()->DesktopLock.showWords(pad.frame,sample));
            dialog(pad,"15-lock-check",()->DesktopLock.checkWords(pad.frame,sample));
            dialog(pad,"16-words-again",()->DesktopLock.showWords(pad.frame,sample,false));
            dialog(pad,"17-security",()->DesktopLock.settings(pad));
            dialog(pad,"22-share-app",pad::shareApp);
            // A newer version out: the bar says so beside the name, and its box.
            SwingUtilities.invokeAndWait(()->{pad.newestKnown="0.0.999";pad.updateShown();});Thread.sleep(200);shoot(pad.frame,"23-update-bar");
            dialog(pad,"24-update",()->pad.updateBox("0.0.999"));
            dialog(pad,"25-updates-current",()->pad.updatesBox(null));
            // A book archived from its menu: Undo waits at the top of the menus until something else takes its place.
            NoteStore.Branch recipes=null;for(NoteStore.Branch one:store.wholeTree())if("Recipes".equals(one.name))recipes=one;
            NoteStore.Branch archived=recipes;SwingUtilities.invokeAndWait(()->pad.putAway(archived,false));
            pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->pad.open(id));pad.disk.flush(10000);Thread.sleep(300);
            dialog(pad,"36-import-choice",pad::askHowToImport);
            dialog(pad,"37-replace-confirm",pad::confirmReplace);
            // The menu under the three dots: its lines start near its left edge.
            SwingUtilities.invokeAndWait(()->{JButton more=find(pad.frame.getContentPane(),"More");pad.menu(more);});Thread.sleep(400);
            SwingUtilities.invokeAndWait(()->{try{
                MenuElement[] open=javax.swing.MenuSelectionManager.defaultManager().getSelectedPath();
                JComponent menu=open.length>0?(JComponent)open[0].getComponent():null;
                if(menu==null)for(Window w:Window.getWindows())if(w.isShowing()&&w!=pad.frame&&w instanceof RootPaneContainer r)for(Component c:r.getContentPane().getComponents())if(c instanceof JPopupMenu m)menu=m;
                if(menu==null)throw new AssertionError("the menu did not open");
                picture(menu,"26-menu");
            }catch(Exception e){throw new RuntimeException(e);}});
            SwingUtilities.invokeAndWait(()->javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath());
            // With the tree shown, its line in the same menu is ticked (decision 26).
            SwingUtilities.invokeAndWait(()->pad.showTree(true));
            menuShot(pad,"26b-menu-tree-ticked",()->pad.menu(find(pad.frame.getContentPane(),"More")));
            SwingUtilities.invokeAndWait(()->pad.showTree(false));
            SwingUtilities.invokeAndWait(()->{pad.newestKnown="";pad.updateShown();});
            dialog(pad,"20-password",()->DesktopLock.askPassword(pad.frame,"Show recovery words","Type your password to see your recovery words.","Show the words"));
            dialog(pad,"12-add-someone",()->pad.people(new NoteStore.Branch(NoteStore.Branch.Kind.PAGE,id,"","Saturday","",0,0,false)));
            // A click beside a box, on the shade over the window behind it, closes the box and takes the shade away.
            SwingUtilities.invokeLater(()->DesktopUi.tell(pad.frame,"About",DesktopUi.body("Synthetic")));
            JDialog[] about={null};
            await(()->{for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing()&&"About".equals(d.getTitle()))about[0]=d;return about[0]!=null;});
            assertTrue(pad.frame.getGlassPane().isVisible());
            SwingUtilities.invokeAndWait(()->{Component shade=pad.frame.getGlassPane();
                shade.dispatchEvent(new java.awt.event.MouseEvent(shade,java.awt.event.MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),0,5,5,1,false));});
            await(()->!about[0].isDisplayable());
            SwingUtilities.invokeAndWait(()->assertFalse(pad.frame.getGlassPane().isVisible()));
            // Addresses in a note, drawn as the phone draws them: in the accent colour, underlined, the lines where they were.
            NoteStore.Note linked=new NoteStore.Note();linked.book=store.someBook();linked.title="Ferry";
            linked.body="Timetable at https://example.org/ferry/summer, or www.example.com.\nTickets: tickets@example.org\nNot an address: notes.txt";store.save(linked);
            SwingUtilities.invokeAndWait(pad::refresh);pad.disk.flush(10000);
            SwingUtilities.invokeAndWait(()->pad.open(linked.id));await(()->pad.page.getText().contains("Timetable"));pad.disk.flush(10000);Thread.sleep(300);
            SwingUtilities.invokeAndWait(()->assertEquals(3,pad.page.links().size()));
            shoot(pad.frame,"80-links");
            // Who wrote what: a note Ana's phone wrote in after you - her lines in her own colour, yours in the ink.
            NoteStore.Note together=new NoteStore.Note();together.book=store.someBook();together.title="Picnic";
            together.body="Blanket and plates\nLemonade\n";together.revision=1;together.writers=Writers.of(Writers.ME,together.body.length());store.save(together);
            store.setLevel(Sharing.Scope.PAGE,together.id,"MxGallery@127.0.0.1:9001",Sharing.Level.WRITE,null);
            store.agreedOn("MxGallery@127.0.0.1:9001",together.id,1);store.keepVersion(together.id,1,"MxGallery@127.0.0.1:9001","Picnic",together.body);
            store.landed(together.id,"MxGallery@127.0.0.1:9001",2,"Picnic",
                "Blanket and plates\nStrawberries, the good ones\nLemonade, and ice\nA frisbee for after\n",store.someBook(),1L);
            SwingUtilities.invokeAndWait(pad::refresh);pad.disk.flush(10000);
            SwingUtilities.invokeAndWait(()->pad.open(together.id));
            await(()->pad.page.getText().contains("frisbee")&&pad.page.inked()&&pad.peopleShown.size()==1);pad.disk.flush(10000);Thread.sleep(300);
            shoot(pad.frame,"81-writing-colours");
            menuShot(pad,"82-round-writing-colour",()->pad.personClicked(pad.peopleShown.get(0),pad.rounds.getComponent(0)));
            // The same round right-clicked: the phone's hold on it.
            menuShot(pad,"82b-round-menu",()->rightClick(pad.rounds.getComponent(0),6,6));
            // Your own colour chosen: your lines in it too.
            store.chooseInk(Writers.ME,6);
            SwingUtilities.invokeAndWait(()->pad.open(together.id));pad.disk.flush(10000);Thread.sleep(300);
            shoot(pad.frame,"83-writing-colours-yours");
            // On a note washed red at the loudest strength: still read.
            store.paint(NoteStore.Branch.Kind.PAGE,together.id,1);
            SwingUtilities.invokeAndWait(()->{pad.useTone(Tint.TONES.length-1);pad.refresh();});pad.disk.flush(10000);
            SwingUtilities.invokeAndWait(()->pad.open(together.id));pad.disk.flush(10000);SwingUtilities.invokeAndWait(pad::washPage);Thread.sleep(300);
            shoot(pad.frame,"84-writing-colours-on-red");
            SwingUtilities.invokeAndWait(()->pad.useTone(Tint.FIRST_TONE));
            dialog(pad,"27h-settings-writing-colours",()->{turnTo("Writing colours");DesktopSettings.open(pad);});
            // A card's own +, pressed, then Note: a new note in that collection, opened.
            int before=pagesIn(store,book);
            SwingUtilities.invokeAndWait(()->pad.openCollection(book));settle(pad);
            SwingUtilities.invokeAndWait(()->{JButton plus=newButton(pad.home.folder.card);if(plus==null)throw new AssertionError("no + on the card");plus.doClick();});Thread.sleep(300);
            SwingUtilities.invokeAndWait(()->{for(Component c:openPopup(pad).getComponents())if(c instanceof JMenuItem m&&"Note".equals(m.getText())){m.doClick();return;}throw new AssertionError("no Note under +");});
            await(()->pagesIn(store,book)==before+1);pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});
            SwingUtilities.invokeAndWait(()->assertTrue("the new note is not open",pad.page.isShowing()&&pad.page.getText().isEmpty()));
            // A new collection from Home's +: made as Untitled, its card up with the name ready to be typed over.
            SwingUtilities.invokeAndWait(()->pad.home.newCollection(Things.HOME));settle(pad);Thread.sleep(300);
            SwingUtilities.invokeAndWait(()->assertTrue("its name is ready to type",pad.home.folder.naming()));
            shoot(pad.frame,"92-new-collection-naming");
            SwingUtilities.invokeAndWait(()->pad.home.folder.close());settle(pad);
            for(String name:new String[]{"01-pad","02-share","03-profile","04-people","05-versions","06-bin","07-scanner","08-about"})
                assertTrue(name,Files.size(SHOTS.resolve(name+".png"))>2000);
        } finally {SwingUtilities.invokeAndWait(()->pad.shutdown(false));await(()->!pad.frame.isDisplayable());}
    }

    /** The Settings window, once it is open, turned down so these words are at its top. */
    private static void turnTo(String words) {
        new javax.swing.Timer(300,e->{((javax.swing.Timer)e.getSource()).stop();
            for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing()&&"Settings".equals(d.getTitle())){
                Component heading=showing(d.getContentPane(),words);
                JScrollPane pane=(JScrollPane)SwingUtilities.getAncestorOfClass(JScrollPane.class,heading);
                if(pane!=null)pane.getVerticalScrollBar().setValue(SwingUtilities.convertPoint(heading,0,0,pane.getViewport().getView()).y-8);}
        }).start();
    }

    /** Opens a window, waits for it to be drawn, keeps a picture of it and closes it. */
    /** The first component showing these words, for a picture that has to be turned to it. */
    private static Component showing(Container in,String words) {
        for(Component one:in.getComponents()) {
            if(one instanceof javax.swing.text.JTextComponent l&&words.equals(l.getText()))return one;
            if(one instanceof Container c){Component deeper=showing(c,words);if(deeper!=null)return deeper;}
        }
        return null;
    }

    private static void dialog(Desktop pad,String name,Runnable open) throws Exception {
        CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
        java.util.Set<Window> before=new java.util.HashSet<>(java.util.Arrays.asList(Window.getWindows()));
        SwingUtilities.invokeLater(()->{
            long[] seen={0};
            javax.swing.Timer look=new javax.swing.Timer(150,event->{
                for(Window window:Window.getWindows())if(window instanceof JDialog dialog&&dialog.isShowing()&&!before.contains(window)) {
                    // Let whatever it fetches on opening arrive before the picture is taken.
                    if(seen[0]==0){seen[0]=System.nanoTime();return;}
                    if(System.nanoTime()-seen[0]<TimeUnit.MILLISECONDS.toNanos(900))return;
                    ((javax.swing.Timer)event.getSource()).stop();
                    try{shootNow(dialog,name);}catch(Throwable error){failure.set(error);}finally{dialog.dispose();done.countDown();}
                    return;
                }
            });look.start();open.run();
        });
        assertTrue(name+" did not open",done.await(30,TimeUnit.SECONDS));
        if(failure.get()!=null)throw new AssertionError(failure.get());
        pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});
    }
    /** The popup menu open now, however Swing chose to show it. */
    private static JPopupMenu openPopup(Desktop pad) {
        MenuElement[] open=javax.swing.MenuSelectionManager.defaultManager().getSelectedPath();
        if(open.length>0&&open[0].getComponent() instanceof JPopupMenu m)return m;
        for(Window w:Window.getWindows())if(w.isShowing()&&w!=pad.frame&&w instanceof RootPaneContainer r)for(Component c:r.getContentPane().getComponents())if(c instanceof JPopupMenu m)return m;
        throw new AssertionError("the menu did not open");
    }
    /** A menu opened by {@code open}, pictured, and closed. */
    private static void menuShot(Desktop pad,String name,Runnable open) throws Exception {
        // A menu closes when another window takes the focus - the owner's own Mininotes, say - so it is asked again.
        for(int tries=1;;tries++) {
            SwingUtilities.invokeAndWait(open);Thread.sleep(400);
            boolean[] shot={false};
            SwingUtilities.invokeAndWait(()->{try{picture(openPopup(pad),name);shot[0]=true;}catch(AssertionError none){/* asked again */}catch(Exception e){throw new RuntimeException(e);}});
            SwingUtilities.invokeAndWait(()->javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath());
            if(shot[0])return;
            if(tries==3)throw new AssertionError(name+": the menu did not open");
        }
    }
    /** A right-click, as Windows sends it: the menu is asked for as the button comes up. */
    private static void rightClick(Component on,int x,int y) {
        on.dispatchEvent(new java.awt.event.MouseEvent(on,java.awt.event.MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,x,y,1,true,java.awt.event.MouseEvent.BUTTON3));
    }
    /** The thing a line of the tree stands for, found by its name. */
    private static NoteStore.Branch line(Desktop pad,String name) {
        for(int row=0;row<pad.tree.getRowCount();row++) {
            Object node=((javax.swing.tree.DefaultMutableTreeNode)pad.tree.getPathForRow(row).getLastPathComponent()).getUserObject();
            if(node instanceof Desktop.Item item&&name.equals(item.branch().name))return item.branch();
        }
        throw new AssertionError("no line "+name);
    }
    /** A point on the screen on that line of the tree, this far down it. */
    private static java.awt.Point onRow(Desktop pad,String name,float down) {
        for(int row=0;row<pad.tree.getRowCount();row++) {
            Object node=((javax.swing.tree.DefaultMutableTreeNode)pad.tree.getPathForRow(row).getLastPathComponent()).getUserObject();
            if(node instanceof Desktop.Item item&&name.equals(item.branch().name)) {
                Rectangle r=pad.tree.getRowBounds(row);java.awt.Point at=new java.awt.Point(r.x+24,r.y+Math.round(r.height*down));
                SwingUtilities.convertPointToScreen(at,pad.tree);return at;
            }
        }
        throw new AssertionError("no line "+name);
    }
    /** The drop box's list, the one table in it. */
    private static Component labelledTable(Container in) {
        for(Component one:in.getComponents()){if(one instanceof JTable)return one;if(one instanceof Container c){Component deeper=labelledTable(c);if(deeper!=null)return deeper;}}
        return null;
    }
    private static Component labelled(Container in,String text) {
        for(Component one:in.getComponents()){if(one instanceof JLabel l&&text.equals(l.getText()))return one;if(one instanceof Container c){Component deeper=labelled(c,text);if(deeper!=null)return deeper;}}
        return null;
    }
    /** A box opened, a right-click on the row showing these words, and the menu that comes up pictured. */
    private static void dialogMenu(Desktop pad,String name,Runnable open,String words) throws Exception {
        CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
        java.util.Set<Window> before=new java.util.HashSet<>(java.util.Arrays.asList(Window.getWindows()));
        SwingUtilities.invokeLater(()->{
            long[] seen={0};
            javax.swing.Timer look=new javax.swing.Timer(150,event->{
                for(Window window:Window.getWindows())if(window instanceof JDialog dialog&&dialog.isShowing()&&!before.contains(window)) {
                    if(seen[0]==0){seen[0]=System.nanoTime();return;}
                    if(System.nanoTime()-seen[0]<TimeUnit.MILLISECONDS.toNanos(900))return;
                    ((javax.swing.Timer)event.getSource()).stop();
                    // A button with these words is pressed, as its menu drops down from it; a row is right-clicked.
                    JButton button=textedIn(dialog.getContentPane(),words);
                    Component row=button!=null?button:showing(dialog.getContentPane(),words);
                    if(row==null){failure.set(new AssertionError("no row "+words));dialog.dispose();done.countDown();return;}
                    if(button!=null)button.doClick(0);else rightClick(row,10,8);
                    javax.swing.Timer later=new javax.swing.Timer(400,e->{((javax.swing.Timer)e.getSource()).stop();
                        try{picture(openPopup(pad),name);}catch(Throwable error){failure.set(error);}
                        finally{javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();dialog.dispose();done.countDown();}});
                    later.start();return;
                }
            });look.start();open.run();
        });
        assertTrue(name+" did not open",done.await(30,TimeUnit.SECONDS));
        if(failure.get()!=null)throw new AssertionError(failure.get());
        pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});
    }
    /** A menu, drawn on its own at its own size. */
    private static void picture(JComponent menu,String name) throws Exception {
        var image=new java.awt.image.BufferedImage(Math.max(1,menu.getWidth()),Math.max(1,menu.getHeight()),java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();menu.printAll(g);g.dispose();javax.imageio.ImageIO.write(image,"png",SHOTS.resolve(name+".png").toFile());
    }
    private static JButton named(Container in,String name) {
        for(Component c:in.getComponents()){if(c instanceof JButton b&&name.equals(b.getName()))return b;if(c instanceof Container k){JButton f=named(k,name);if(f!=null)return f;}}
        return null;
    }
    /** A button by the words on it. */
    private static JButton texted(Container in,String words) {
        JButton found=textedIn(in,words);if(found==null)throw new AssertionError("no button "+words);return found;
    }
    private static JButton textedIn(Container in,String words) {
        for(Component c:in.getComponents()){if(c instanceof JButton b&&words.equals(b.getText()))return b;if(c instanceof Container k){JButton f=textedIn(k,words);if(f!=null)return f;}}
        return null;
    }
    private static JButton find(Container in,String tip) {
        for(Component c:in.getComponents()){if(c instanceof JButton b&&tip.equals(b.getToolTipText()))return b;if(c instanceof Container k){JButton f=find(k,tip);if(f!=null)return f;}}
        return null;
    }
    /** Where Home's grid and its icons are, said when a picture would otherwise show a grid that is not there. */
    private static String gridState(Desktop pad) {
        DesktopHome.Icons g=pad.home.grid;JViewport port=pad.home.scroll.getViewport();
        StringBuilder out=new StringBuilder("home showing="+pad.home.isShowing()+" onPage="+pad.onPage()+" view="+(port.getView()==g?"grid":String.valueOf(port.getView()))
            +" pos="+port.getViewPosition()+" extent="+port.getExtentSize()+" grid="+g.getBounds()+" valid="+g.isValid()+" visible="+g.getVisibleRect()+" tiles="+g.tiles.size()+" children="+g.getComponentCount()+"\n");
        for(DesktopHome.Tile t:g.tiles)out.append(t.thing.name).append(' ').append(t.getBounds()).append(" showing=").append(t.isShowing()).append('\n');
        return out.toString();
    }
    /** The + in this part of the window: the round button named New. */
    private static JButton newButton(Container in) {
        for(Component c:in.getComponents()){if(c instanceof JButton b&&"New".equals(b.getAccessibleContext().getAccessibleName()))return b;if(c instanceof Container k){JButton f=newButton(k);if(f!=null)return f;}}
        return null;
    }
    /** An icon in a grid, by the name under it. */
    private static DesktopHome.Tile tile(DesktopHome.Icons grid,String name) {
        for(DesktopHome.Tile one:grid.tiles)if(name.equals(one.thing.name))return one;
        throw new AssertionError("no icon "+name);
    }
    /** An icon pressed, as the left button presses it, and carried to a point on another component. */
    private static void carry(Desktop pad,DesktopHome.Tile from,Component over,int x,int y) {
        java.awt.Point start=from.getLocationOnScreen();
        pad.home.carry.press(from,new java.awt.event.MouseEvent(from,java.awt.event.MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),java.awt.event.InputEvent.BUTTON1_DOWN_MASK,
            20,20,start.x+20,start.y+20,1,false,java.awt.event.MouseEvent.BUTTON1));
        carryTo(pad,from,over,x,y);
    }
    /** The icon in hand moved to a point on a component, as the pointer drags it there. */
    private static void carryTo(Desktop pad,Component over,int x,int y){carryTo(pad,null,over,x,y);}
    private static void carryTo(Desktop pad,DesktopHome.Tile from,Component over,int x,int y) {
        java.awt.Point to=over.getLocationOnScreen();to.translate(x,y);
        Component source=from!=null?from:over;
        pad.home.carry.drag(new java.awt.event.MouseEvent(source,java.awt.event.MouseEvent.MOUSE_DRAGGED,System.currentTimeMillis(),java.awt.event.InputEvent.BUTTON1_DOWN_MASK,
            0,0,to.x,to.y,1,false,java.awt.event.MouseEvent.BUTTON1));
    }
    /** Home, a card and the tree read again from the notebook and drawn: two rounds of the disk, and the window's thread after each. */
    private static void settle(Desktop pad) throws Exception {
        for(int i=0;i<3;i++){pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});}
        Thread.sleep(250);SwingUtilities.invokeAndWait(pad.frame::validate);
    }
    private static int pagesIn(NoteStore store,String book) throws Exception {
        int n=0;for(NoteStore.Branch one:store.wholeTree())if(one.kind==NoteStore.Branch.Kind.PAGE&&book.equals(one.parent))n++;return n;
    }
    private static void shoot(Window window,String name) throws Exception {
        SwingUtilities.invokeAndWait(()->{try{shootNow(window,name);}catch(Exception e){throw new RuntimeException(e);}});
    }
    private static void shootNow(Window window,String name) throws Exception {
        // At the screen's own scale: text is measured at it, so a picture drawn at another scale cuts words short.
        var scale=window.getGraphicsConfiguration().getDefaultTransform();
        var image=new java.awt.image.BufferedImage((int)Math.ceil(window.getWidth()*scale.getScaleX()),(int)Math.ceil(window.getHeight()*scale.getScaleY()),java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();g.transform(scale);
        // Text drawn as the screen draws it; without the desktop's hints glyphs come out wider than they were measured.
        Object hints=Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");if(hints instanceof java.util.Map<?,?> map)g.addRenderingHints(map);
        window.paint(g);g.dispose();
        javax.imageio.ImageIO.write(image,"png",SHOTS.resolve(name+".png").toFile());
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(System.nanoTime()<until){if(condition.call())return;Thread.sleep(40);}fail("Desktop did not finish in time");
    }
}
