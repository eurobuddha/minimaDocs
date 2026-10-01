package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.awt.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;

/**
 * Icons and pictures wherever a note or a collection is drawn (docs/HOME.md, step 4), with made-up notes and people,
 * saved beside the other gallery pictures to look at: Home, a card, the dock, the tree, a note's bar, the overview, the
 * search, the picker, the menu, and the mark's box saying who needs to update. Nobody real is in them.
 */
public class DesktopIconGalleryTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final Path SHOTS=Path.of("build","verification","gallery");
    /** A picture as a phone keeps one: WebP, 192 pixels, made with Pillow from shapes - a sky, a sun, two hills. */
    private static final String PHONE_WEBP="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";
    private static final String OLD="MxGalleryOld@127.0.0.1:9601";

    @Test public void iconsAndPicturesDrawWhereverAThingIsDrawn() throws Exception {
        Files.createDirectories(SHOTS);
        Path folder=temp.newFolder("icons").toPath();Desktop[] app=new Desktop[1];
        SwingUtilities.invokeAndWait(()->{try{app[0]=new Desktop(folder,true);app[0].show();}catch(Exception e){throw new RuntimeException(e);}});
        Desktop pad=app[0];
        try {
            await(()->pad.page.isEditable()&&pad.store.latest()!=null);pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});
            NoteStore store=pad.store;
            Node.chooseName(pad.context,"Sam");Node.chooseDevice(pad.context,"Study PC");
            NoteStore.Branch.Kind PAGE=NoteStore.Branch.Kind.PAGE,COLLECTION=NoteStore.Branch.Kind.COLLECTION;
            // Collections on Home, each wearing something: an icon in its colour, a picture from a phone, or nothing - its mini-grid.
            String kitchen=store.addCollection("Kitchen").id;store.setIcon(COLLECTION,kitchen,"chef-hat");store.paint(COLLECTION,kitchen,2);
            String recipes=store.addBook(kitchen,"Recipes").id;
            String garden=store.addCollection("Garden").id;store.setIcon(COLLECTION,garden,"sprout");store.paint(COLLECTION,garden,4);
            String travel=store.addCollection("Travel").id;store.setImage(COLLECTION,travel,java.util.Base64.getDecoder().decode(PHONE_WEBP));
            String projects=store.addCollection("Projects").id;store.paint(COLLECTION,projects,6);
            String music=store.addCollection("Music").id;store.setIcon(COLLECTION,music,"music");store.paint(COLLECTION,music,7);
            // Notes on Home: icons of their own, the note's own for one with none, a picture made here, and one from a later set.
            String saturday=note(store,Things.HOME,"Saturday","Pick up fresh bread\nTea for the ferry\n\nLeave room for a little wandering.");
            store.setIcon(PAGE,saturday,"calendar");store.paint(PAGE,saturday,6);
            String groceries=note(store,Things.HOME,"Groceries","Oats, apples, milk");store.setIcon(PAGE,groceries,"shopping-cart");
            String ideas=note(store,Things.HOME,"Ideas","A shelf for the hall");store.setIcon(PAGE,ideas,"lightbulb");store.paint(PAGE,ideas,3);
            String birthday=note(store,Things.HOME,"Ana's birthday","Cake at four");store.setIcon(PAGE,birthday,"gift");store.paint(PAGE,birthday,8);
            String plain=note(store,Things.HOME,"Plain note","Nothing chosen for this one");
            String later=note(store,Things.HOME,"From a later set","Its icon is one this build does not have");
            store.getWritableDatabase().execSQL("UPDATE notes SET icon=? WHERE id=?",new Object[]{"from-a-later-set",later});
            String harbour=note(store,Things.HOME,"Harbour","Boats at six");
            store.setImage(PAGE,harbour,DesktopIcons.thumb(photo(),1));
            // Inside the collections, more of the same, and a note two collections down.
            String roof=note(store,projects,"Roof","Tiles, gutter");store.setIcon(PAGE,roof,"house");
            String budget=note(store,projects,"Budget","Quotes");store.setIcon(PAGE,budget,"piggy-bank");store.paint(PAGE,budget,4);
            note(store,projects,"Plan","First the roof");
            String flights=note(store,travel,"Flights","Tuesday, early");store.setIcon(PAGE,flights,"plane");
            String soup=note(store,recipes,"Soup","Leeks and potatoes");store.setIcon(PAGE,soup,"soup");store.paint(PAGE,soup,2);
            // Favourites in the dock: an icon, a picture and a collection with one.
            for(String one:new String[]{saturday,harbour,groceries})store.keepToHand(PAGE,one,true);
            store.keepToHand(COLLECTION,garden,true);store.keepToHand(COLLECTION,travel,true);
            SwingUtilities.invokeAndWait(pad::refresh);settle(pad);
            SwingUtilities.invokeAndWait(pad::goHome);settle(pad);

            // Every face at every size it is drawn at, large, to look at the drawing itself.
            faces(pad);
            shoot(pad.frame,"i01-home-icons");
            shootPart(pad.frame,pad.home.dock,"i03-dock-icons");
            // A collection's card: the notes in it wearing theirs, its own face before its name.
            SwingUtilities.invokeAndWait(()->pad.openCollection(projects));settle(pad);shoot(pad.frame,"i02-card-icons");
            SwingUtilities.invokeAndWait(()->pad.openCollection(travel));settle(pad);shoot(pad.frame,"i02b-card-picture");
            SwingUtilities.invokeAndWait(()->pad.home.folder.close());settle(pad);
            // The tree, each line with its face, small.
            SwingUtilities.invokeAndWait(()->{pad.showTree(true);pad.frame.validate();});settle(pad);shoot(pad.frame,"i04-tree-icons");
            SwingUtilities.invokeAndWait(()->pad.showTree(false));settle(pad);
            // A note's bar: its face before its title, the one a click changes.
            SwingUtilities.invokeAndWait(()->pad.open(saturday));settle(pad);shoot(pad.frame,"i05-note-bar");
            SwingUtilities.invokeAndWait(()->pad.open(harbour));settle(pad);shoot(pad.frame,"i05b-note-bar-picture");
            // The overview: a note's card with its icon by its title, a collection's with its picture.
            SwingUtilities.invokeAndWait(()->pad.openCollection(travel));settle(pad);
            SwingUtilities.invokeAndWait(()->pad.open(saturday));settle(pad);
            SwingUtilities.invokeAndWait(()->pad.overview.up(false));settle(pad);Thread.sleep(300);shoot(pad.frame,"i11-overview-icons");
            SwingUtilities.invokeAndWait(()->pad.overview.close());
            SwingUtilities.invokeAndWait(pad::goHome);settle(pad);
            // Found in the search: each line with its face.
            SwingUtilities.invokeAndWait(()->pad.home.search.setText("an"));Thread.sleep(500);settle(pad);shoot(pad.frame,"i12-search-icons");
            SwingUtilities.invokeAndWait(()->pad.home.search.setText(""));Thread.sleep(400);settle(pad);
            // The menu of a thing wearing a picture: Icon… right after Colour, then Remove the picture.
            menuShot(pad,"i10-menu-icon",()->rightClick(tile(pad.home.grid,"Harbour"),30,30));
            // The picker: for a note, as it opens; with a search typed; with a colour chosen; for a collection.
            NoteStore.Branch saturdayLine=line(pad,"Saturday"),projectsLine=line(pad,"Projects");
            dialog(pad,"i06-picker",()->DesktopIconPicker.open(pad,saturdayLine),null);
            dialog(pad,"i07-picker-search",()->DesktopIconPicker.open(pad,saturdayLine),box->field(box).setText("heart"));
            dialog(pad,"i08-picker-tint",()->DesktopIconPicker.open(pad,saturdayLine),box->named(box,"Green").doClick());
            dialog(pad,"i08b-picker-keyboard",()->DesktopIconPicker.open(pad,saturdayLine),box->{
                Component grid=grid(box);grid.requestFocusInWindow();
                for(int key:new int[]{java.awt.event.KeyEvent.VK_DOWN,java.awt.event.KeyEvent.VK_RIGHT,java.awt.event.KeyEvent.VK_RIGHT})
                    grid.dispatchEvent(new java.awt.event.KeyEvent(grid,java.awt.event.KeyEvent.KEY_PRESSED,System.currentTimeMillis(),0,key,java.awt.event.KeyEvent.CHAR_UNDEFINED));});
            dialog(pad,"i09-picker-collection",()->DesktopIconPicker.open(pad,projectsLine),null);
            dialog(pad,"i09b-picker-nothing-found",()->DesktopIconPicker.open(pad,saturdayLine),box->field(box).setText("zzqq"));
            // A picked icon is worn at once, and the picture comes off with it (decision 34).
            SwingUtilities.invokeAndWait(()->DesktopIconPicker.wear(pad,line(pad,"Harbour"),PAGE,"anchor"));settle(pad);
            assertEquals("anchor",store.iconOf(PAGE,harbour));assertNull(store.imageOf(PAGE,harbour));
            SwingUtilities.invokeAndWait(()->assertEquals("Icon changed",pad.status.getText()));
            dialog(pad,"i14-about",pad::about,null);
            // A picture chosen from a file: made, worn, said in the bar; and taken off again from the menu.
            Path file=temp.newFolder("chosen").toPath().resolve("boat.png");javax.imageio.ImageIO.write(photo(),"png",file.toFile());
            SwingUtilities.invokeAndWait(()->{DesktopIconPicker.picture(pad,line(pad,"Ideas"),PAGE,"boat.png",()->DesktopIcons.read(file));
                assertEquals("Making the picture from “boat.png”…",pad.status.getText());});
            settle(pad);
            assertTrue(Thumb.takes(store.imageOf(PAGE,ideas)));assertEquals("png",Thumb.kind(store.imageOf(PAGE,ideas)));
            SwingUtilities.invokeAndWait(()->assertEquals("Picture set",pad.status.getText()));
            assertEquals("the icon stays under the picture","lightbulb",store.iconOf(PAGE,ideas));
            SwingUtilities.invokeAndWait(()->DesktopIconPicker.removePicture(pad,line(pad,"Ideas")));settle(pad);
            assertNull(store.imageOf(PAGE,ideas));SwingUtilities.invokeAndWait(()->assertEquals("Picture removed",pad.status.getText()));
            // Not a picture: said in the bar, and nothing changed.
            Path words=file.resolveSibling("notes.png");Files.writeString(words,"not a picture");
            SwingUtilities.invokeAndWait(()->DesktopIconPicker.picture(pad,line(pad,"Ideas"),PAGE,"notes.png",()->DesktopIcons.read(words)));settle(pad);
            SwingUtilities.invokeAndWait(()->assertTrue(pad.status.getText(),pad.status.getText().startsWith("Mininotes cannot read that kind of picture")));
            assertNull(store.imageOf(PAGE,ideas));
            // A note this PC may only read: the colours still its own to choose, the icons shown and not to be taken, and why.
            store.getWritableDatabase().execSQL("UPDATE notes SET theirs=1,origin=? WHERE id=?",new Object[]{"MxGallery@127.0.0.1:9001",birthday});
            store.stands(Sharing.Scope.PAGE,birthday,Sharing.Level.READ,System.currentTimeMillis());
            assertTrue(DesktopIconPicker.readsOnly(store,PAGE,birthday));
            try{store.setIcon(PAGE,birthday,"cake");fail("a note only read here took an icon");}catch(IllegalArgumentException refused){assertEquals("That note is read only here.",refused.getMessage());}
            NoteStore.Branch birthdayLine=line(pad,"Ana's birthday");
            dialog(pad,"i09c-picker-read-only",()->DesktopIconPicker.open(pad,birthdayLine),null);

            // Last, since it turns everything amber: a device from before trees, given everything. What only waits for it to be
            // updated is said with nothing to press; Home's box says it beside what can be sent.
            var agreement=Envelope.keys();var signing=Envelope.keys();
            store.pairedWith(OLD,"Old phone",false,agreement.getPublic().getEncoded(),signing.getPublic().getEncoded());
            store.addShare(new Sharing.Rule(Sharing.Scope.LIBRARY,Sharing.EVERYTHING,OLD,true));
            SwingUtilities.invokeAndWait(pad::refresh);settle(pad);
            NoteStore.Branch plainLine=line(pad,"Plain note");
            assertTrue(Looks.onlyUpdates(SyncStatus.waits(store,PAGE,plain)));
            dialog(pad,"i13-needs-update",()->pad.markClicked(plainLine,SyncMark.WAITING),box->assertNull("nothing to press",box.getRootPane().getDefaultButton()));
            dialog(pad,"i13b-waits-and-needs-update",()->pad.markClicked(Desktop.library(),SyncMark.WAITING),null);
            SwingUtilities.invokeAndWait(pad::goHome);settle(pad);shoot(pad.frame,"i15-home-waiting-for-update");
        } finally {SwingUtilities.invokeAndWait(()->pad.shutdown(false));await(()->!pad.frame.isDisplayable());}
    }

    private static String note(NoteStore store,String in,String title,String body) {
        NoteStore.Note one=new NoteStore.Note();one.book=in;one.title=title;one.body=body;store.save(one);return one.id;
    }

    /** A made-up photograph: a sky, the sea, a sun and a boat, wider than it is tall, as a camera's is. */
    private static java.awt.image.BufferedImage photo() {
        var one=new java.awt.image.BufferedImage(640,420,java.awt.image.BufferedImage.TYPE_INT_RGB);Graphics2D g=one.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0,0,new Color(120,170,220),0,260,new Color(236,214,170)));g.fillRect(0,0,640,260);
        g.setPaint(new GradientPaint(0,260,new Color(40,110,140),0,420,new Color(18,56,80)));g.fillRect(0,260,640,160);
        g.setColor(new Color(255,230,150));g.fillOval(380,120,90,90);
        g.setColor(new Color(160,60,40));g.fillPolygon(new int[]{230,410,380,260},new int[]{280,280,320,320},4);
        g.setColor(Color.WHITE);g.fillPolygon(new int[]{315,315,380},new int[]{180,275,275},3);
        g.dispose();return one;
    }

    /** Faces drawn large, every kind at every size a face is drawn at, on the paper they sit on. */
    private static void faces(Desktop pad) throws Exception {
        java.util.List<NoteStore.Branch> kinds=new java.util.ArrayList<>();
        for(String name:new String[]{"Saturday","Groceries","From a later set","Harbour","Kitchen","Projects","Travel"})kinds.add(line(pad,name));
        int[] sides={20,26,30,36,44,64,104};int scale=2,w=40,across=0;for(int s:sides)across+=s+w;
        var image=new java.awt.image.BufferedImage(across*scale,(kinds.size()*120+20)*scale,java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();g.scale(scale,scale);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Desktop.PAPER);g.fillRect(0,0,across,kinds.size()*120+20);
        for(int row=0;row<kinds.size();row++){int x=20;for(int s:sides){DesktopHome.face(g,kinds.get(row),x,20+row*120+(104-s)/2,s,pad.tone);x+=s+w;}}
        g.dispose();javax.imageio.ImageIO.write(image,"png",SHOTS.resolve("i00-faces-drawn.png").toFile());
    }

    // ---- as DesktopGalleryTest does it ---------------------------------------------------------------------------

    private static JTextField field(JDialog box){return find(box.getContentPane(),JTextField.class);}
    private static Component grid(JDialog box){return find(box.getContentPane(),DesktopIconPicker.Grid.class);}
    private static <T> T find(Container in,Class<T> kind) {
        for(Component one:in.getComponents()){if(kind.isInstance(one))return kind.cast(one);if(one instanceof Container c){T deeper=find(c,kind);if(deeper!=null)return deeper;}}
        return null;
    }
    private static JButton named(Container in,String accessible) {
        JButton found=namedIn(in,accessible);if(found==null)throw new AssertionError("no button "+accessible);return found;
    }
    private static JButton namedIn(Container in,String accessible) {
        for(Component c:in.getComponents()){if(c instanceof JButton b&&accessible.equals(b.getAccessibleContext().getAccessibleName()))return b;if(c instanceof Container k){JButton f=namedIn(k,accessible);if(f!=null)return f;}}
        return null;
    }
    /** A box opened by {@code open}, something done in it once it is up, then pictured and closed. */
    private static void dialog(Desktop pad,String name,Runnable open,java.util.function.Consumer<JDialog> doing) throws Exception {
        CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
        java.util.Set<Window> before=new java.util.HashSet<>(java.util.Arrays.asList(Window.getWindows()));
        SwingUtilities.invokeLater(()->{
            long[] seen={0};boolean[] did={doing==null};
            javax.swing.Timer look=new javax.swing.Timer(150,event->{
                for(Window window:Window.getWindows())if(window instanceof JDialog dialog&&dialog.isShowing()&&!before.contains(window)) {
                    if(seen[0]==0){seen[0]=System.nanoTime();return;}
                    if(System.nanoTime()-seen[0]<TimeUnit.MILLISECONDS.toNanos(900))return;
                    if(!did[0]){did[0]=true;seen[0]=System.nanoTime();try{doing.accept(dialog);}catch(Throwable error){failure.set(error);}return;}
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
    private static JPopupMenu openPopupOrNull(Desktop pad) {
        MenuElement[] open=javax.swing.MenuSelectionManager.defaultManager().getSelectedPath();
        if(open.length>0&&open[0].getComponent() instanceof JPopupMenu m)return m;
        for(Window w:Window.getWindows())if(w.isShowing()&&w!=pad.frame&&w instanceof RootPaneContainer r)for(Component c:r.getContentPane().getComponents())if(c instanceof JPopupMenu m)return m;
        return null;
    }
    private static void menuShot(Desktop pad,String name,Runnable open) throws Exception {
        // A menu closes when another window takes the focus - the owner's own Mininotes, say - so it is asked again.
        for(int tries=1;;tries++) {
            SwingUtilities.invokeAndWait(open);Thread.sleep(400);
            boolean[] shot={false};
            SwingUtilities.invokeAndWait(()->{try{JPopupMenu m=openPopupOrNull(pad);if(m!=null){picture(m,name);shot[0]=true;}}catch(Exception e){throw new RuntimeException(e);}});
            SwingUtilities.invokeAndWait(()->javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath());
            if(shot[0])return;
            if(tries==3)throw new AssertionError(name+": the menu did not open");
        }
    }
    private static void picture(JComponent menu,String name) throws Exception {
        var image=new java.awt.image.BufferedImage(Math.max(1,menu.getWidth()),Math.max(1,menu.getHeight()),java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();menu.printAll(g);g.dispose();javax.imageio.ImageIO.write(image,"png",SHOTS.resolve(name+".png").toFile());
    }
    private static void rightClick(Component on,int x,int y) {
        on.dispatchEvent(new java.awt.event.MouseEvent(on,java.awt.event.MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,x,y,1,true,java.awt.event.MouseEvent.BUTTON3));
    }
    private static NoteStore.Branch line(Desktop pad,String name) {
        for(NoteStore.Branch one:pad.everything())if(name.equals(one.name))return one;
        throw new AssertionError("no line "+name);
    }
    private static DesktopHome.Tile tile(DesktopHome.Icons grid,String name) {
        for(DesktopHome.Tile one:grid.tiles)if(name.equals(one.thing.name))return one;
        throw new AssertionError("no icon "+name);
    }
    private static void settle(Desktop pad) throws Exception {
        for(int i=0;i<3;i++){pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});}
        Thread.sleep(250);SwingUtilities.invokeAndWait(pad.frame::validate);
    }
    private static void shoot(Window window,String name) throws Exception {
        SwingUtilities.invokeAndWait(()->{try{shootNow(window,name);}catch(Exception e){throw new RuntimeException(e);}});
    }
    /** One part of a window, cut out of its picture, to look at closely. */
    private static void shootPart(Window window,Component part,String name) throws Exception {
        SwingUtilities.invokeAndWait(()->{try{
            var scale=window.getGraphicsConfiguration().getDefaultTransform();
            java.awt.image.BufferedImage whole=whole(window);
            Rectangle at=SwingUtilities.convertRectangle(part.getParent(),part.getBounds(),window);at.grow(12,12);
            int x=(int)Math.max(0,at.x*scale.getScaleX()),y=(int)Math.max(0,at.y*scale.getScaleY());
            int w=(int)Math.min(whole.getWidth()-x,at.width*scale.getScaleX()),h=(int)Math.min(whole.getHeight()-y,at.height*scale.getScaleY());
            javax.imageio.ImageIO.write(whole.getSubimage(x,y,w,h),"png",SHOTS.resolve(name+".png").toFile());
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    private static void shootNow(Window window,String name) throws Exception {
        javax.imageio.ImageIO.write(whole(window),"png",SHOTS.resolve(name+".png").toFile());
    }
    private static java.awt.image.BufferedImage whole(Window window) {
        // At the screen's own scale: text is measured at it, so a picture drawn at another scale cuts words short.
        var scale=window.getGraphicsConfiguration().getDefaultTransform();
        var image=new java.awt.image.BufferedImage((int)Math.ceil(window.getWidth()*scale.getScaleX()),(int)Math.ceil(window.getHeight()*scale.getScaleY()),java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();g.transform(scale);
        Object hints=Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");if(hints instanceof java.util.Map<?,?> map)g.addRenderingHints(map);
        window.paint(g);g.dispose();
        return image;
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(System.nanoTime()<until){if(condition.call())return;Thread.sleep(40);}fail("Desktop did not finish in time");
    }
}
