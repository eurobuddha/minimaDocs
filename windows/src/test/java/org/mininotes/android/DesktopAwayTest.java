package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.awt.*;
import java.awt.Point;
import java.awt.event.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.swing.*;

/**
 * The archive and the bin on Home, on the PC (docs/HOME.md, decision 41): an icon each, with how many things wait in it;
 * a note carried onto the Bin goes in the bin, a collection onto the Archive is archived, each said and one Undo away;
 * the bin's card lists what waits there and puts it back; the Bin icon carried to an empty cell stays there (decision 42);
 * Settings can take them off Home. Synthetic notebook; pictures saved beside the gallery's.
 */
public class DesktopAwayTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final Path SHOTS=Path.of("build","verification","gallery");

    @Test public void theArchiveAndTheBinAreOnHome() throws Exception {
        Files.createDirectories(SHOTS);
        Path folder=temp.newFolder("pad").toPath();Desktop[] app=new Desktop[1];
        SwingUtilities.invokeAndWait(()->{try{app[0]=new Desktop(folder,true);app[0].show();}catch(Exception e){throw new RuntimeException(e);}});
        Desktop pad=app[0];
        try {
            await(()->pad.page.isEditable()&&pad.store.latest()!=null);settle(pad);
            NoteStore store=pad.store;
            store.addCollection("Kitchen");
            for(String title:new String[]{"Shopping","Ideas","Bills"})note(store,Things.HOME,title);
            SwingUtilities.invokeAndWait(()->{pad.goHome();pad.refresh();});settle(pad);
            DesktopHome.Icons home=pad.home.grid;

            // On a Home nobody has moved: the things first, then the archive and the bin, each empty, so with no count.
            SwingUtilities.invokeAndWait(()->{
                List<NoteStore.Branch.Kind> kinds=new ArrayList<>();for(DesktopHome.Tile one:home.tiles)kinds.add(one.thing.kind);
                assertEquals(NoteStore.Branch.Kind.BIN,kinds.get(kinds.size()-1));
                assertEquals(NoteStore.Branch.Kind.ARCHIVE,kinds.get(kinds.size()-2));
                assertEquals("",tile(home,"Bin").thing.detail);
                assertEquals("Open the bin, empty",tile(home,"Bin").getAccessibleContext().getAccessibleName());
            });

            // Ideas carried onto the Bin: the Bin ringed, and said; let go, it is in the bin, said, and the Bin counts it.
            SwingUtilities.invokeAndWait(()->{
                DesktopHome.Tile bin=tile(home,"Bin");
                carry(pad,tile(home,"Ideas"),bin,bin.getWidth()/2,bin.getHeight()/2);
                assertTrue("the Bin ringed",bin.target);
                assertTrue(pad.status.getText(),pad.status.getText().startsWith("Let go to put it in the bin"));
                letGo(pad,bin,bin.getWidth()/2,bin.getHeight()/2);
            });
            settle(pad);
            assertEquals(1,onEdt(()->store.awayCount(true)).intValue());
            SwingUtilities.invokeAndWait(()->{
                assertEquals("Moved to the bin",pad.status.getText());
                assertEquals("1 thing",tile(home,"Bin").thing.detail);
                for(DesktopHome.Tile one:home.tiles)assertNotEquals("Ideas",one.thing.name);
            });
            // Kitchen carried onto the Archive: archived.
            SwingUtilities.invokeAndWait(()->{
                DesktopHome.Tile archive=tile(home,"Archive");
                carry(pad,tile(home,"Kitchen"),archive,archive.getWidth()/2,archive.getHeight()/2);
                assertTrue(pad.status.getText(),pad.status.getText().startsWith("Let go to archive it"));
                letGo(pad,archive,archive.getWidth()/2,archive.getHeight()/2);
            });
            settle(pad);
            assertEquals(1,onEdt(()->store.awayCount(false)).intValue());
            SwingUtilities.invokeAndWait(()->assertEquals("Archived",pad.status.getText()));
            shoot(pad.frame,"96a-home-archive-and-bin");
            // One Undo: Kitchen back on Home.
            SwingUtilities.invokeAndWait(pad::undo);settle(pad);
            assertEquals(0,onEdt(()->store.awayCount(false)).intValue());

            // The Bin opened: a card of what waits there, Ideas in it, with no +; a click on Ideas offers where it goes.
            NoteStore.Branch binLine=onEdt(()->tile(home,"Bin").thing);
            SwingUtilities.invokeAndWait(()->pad.home.opened(binLine,DesktopHome.Where.HOME));settle(pad);
            DesktopHome.Icons card=pad.home.folder.grid;
            SwingUtilities.invokeAndWait(()->{
                assertTrue(pad.home.folder.isOpen());assertTrue(pad.home.folder.inBin());
                assertNull("nothing to share or name on a place",pad.home.folder.shown());
                assertEquals(DesktopHome.Where.AWAY,tile(card,"Ideas").where);
            });
            shoot(pad.frame,"96b-bin-card");
            JPopupMenu offered=onEdt(()->pad.home.menuFor(tile(card,"Ideas").thing,DesktopHome.Where.AWAY));
            List<String> rows=new ArrayList<>();for(Component c:offered.getComponents())if(c instanceof JMenuItem item)rows.add(item.getText());
            assertEquals(List.of("Put back","Delete for good…"),rows);
            SwingUtilities.invokeAndWait(()->((JMenuItem)offered.getComponent(0)).doClick());settle(pad);
            assertEquals(0,onEdt(()->store.awayCount(true)).intValue());
            SwingUtilities.invokeAndWait(()->{
                assertTrue("the card stays, empty, and says so",card.tiles.isEmpty());
                pad.home.folder.close();
            });
            settle(pad);
            assertEquals("Ideas is back on Home",1,onEdt(()->{int n=0;for(DesktopHome.Tile one:home.tiles)if("Ideas".equals(one.thing.name))n++;return n;}).intValue());

            // The Bin carried to an empty cell: it stays there, kept in this PC's settings; every other icon stays put.
            SwingUtilities.invokeAndWait(()->{Rectangle r=home.cellBounds(2,3);carry(pad,tile(home,"Bin"),home,r.x+r.width/2,r.y+40);
                assertEquals(r,home.landing);letGo(pad,home,r.x+r.width/2,r.y+40);});
            settle(pad);
            assertEquals(Layout.cell(2,3),pad.context.getSharedPreferences("settings",0).getLong(NoteStore.BIN+"Cell",Layout.NONE));
            assertEquals("on the main page",Layout.CENTRE,pad.context.getSharedPreferences("settings",0).getLong(NoteStore.BIN+"Page",Layout.NO_PAGE));
            SwingUtilities.invokeAndWait(()->assertEquals(home.cellBounds(2,3),tile(home,"Bin").getBounds()));
            // Onto a note it does not go: back where it was.
            SwingUtilities.invokeAndWait(()->{DesktopHome.Tile bills=tile(home,"Bills");carry(pad,tile(home,"Bin"),bills,bills.getWidth()/2,bills.getHeight()/2);
                assertFalse("a place goes into nothing",bills.target);letGo(pad,bills,bills.getWidth()/2,bills.getHeight()/2);});
            settle(pad);
            SwingUtilities.invokeAndWait(()->assertEquals(home.cellBounds(2,3),tile(home,"Bin").getBounds()));
            shoot(pad.frame,"96c-bin-moved");

            // Switched off in Settings: off Home, and back in ⋯.
            SwingUtilities.invokeAndWait(()->pad.setAwayOnHome(false));settle(pad);
            SwingUtilities.invokeAndWait(()->{for(DesktopHome.Tile one:home.tiles)assertFalse(Grid.place(one.thing.kind)&&one.thing.kind!=NoteStore.Branch.Kind.FAVOURITES);});
            assertFalse(onEdt(pad::awayOnHome));
            SwingUtilities.invokeAndWait(()->pad.setAwayOnHome(true));settle(pad);
            SwingUtilities.invokeAndWait(()->assertEquals("where it was put",home.cellBounds(2,3),tile(home,"Bin").getBounds()));
        } finally {SwingUtilities.invokeAndWait(()->pad.shutdown(false));await(()->!pad.frame.isDisplayable());}
    }

    private static NoteStore.Note note(NoteStore store,String in,String title) {
        NoteStore.Note one=new NoteStore.Note();one.book=in;one.title=title;one.body="Synthetic "+title.toLowerCase();store.save(one);return one;
    }
    private static DesktopHome.Tile tile(DesktopHome.Icons grid,String name) {
        for(DesktopHome.Tile one:grid.tiles)if(name.equals(one.thing.name))return one;
        throw new AssertionError("no icon "+name);
    }
    /** An icon pressed, as the left button presses it, and carried to a point on another component. */
    private static void carry(Desktop pad,DesktopHome.Tile from,Component over,int x,int y) {
        Point to=over.getLocationOnScreen();to.translate(x,y);
        Point start=from.getLocationOnScreen();
        pad.home.carry.press(from,new MouseEvent(from,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),InputEvent.BUTTON1_DOWN_MASK,
            20,20,start.x+20,start.y+20,1,false,MouseEvent.BUTTON1));
        pad.home.carry.drag(new MouseEvent(from,MouseEvent.MOUSE_DRAGGED,System.currentTimeMillis(),InputEvent.BUTTON1_DOWN_MASK,0,0,to.x,to.y,1,false,MouseEvent.BUTTON1));
    }
    /** Let go at a point on a component. */
    private static void letGo(Desktop pad,Component over,int x,int y) {
        Point to=over.getLocationOnScreen();to.translate(x,y);
        pad.home.carry.release(new MouseEvent(over,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,0,0,to.x,to.y,1,false,MouseEvent.BUTTON1));
    }
    private static void settle(Desktop pad) throws Exception {
        for(int i=0;i<3;i++){pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});}
        Thread.sleep(250);SwingUtilities.invokeAndWait(pad.frame::validate);
    }
    private static void shoot(Window window,String name) throws Exception {
        SwingUtilities.invokeAndWait(()->{try{
            var scale=window.getGraphicsConfiguration().getDefaultTransform();
            var image=new java.awt.image.BufferedImage((int)Math.ceil(window.getWidth()*scale.getScaleX()),(int)Math.ceil(window.getHeight()*scale.getScaleY()),java.awt.image.BufferedImage.TYPE_INT_RGB);
            var g=image.createGraphics();g.transform(scale);
            Object hints=Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");if(hints instanceof java.util.Map<?,?> map)g.addRenderingHints(map);
            window.paint(g);g.dispose();
            javax.imageio.ImageIO.write(image,"png",SHOTS.resolve(name+".png").toFile());
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    private static <T> T onEdt(Callable<T> read) throws Exception {
        Object[] got={null};Exception[] failed={null};
        SwingUtilities.invokeAndWait(()->{try{got[0]=read.call();}catch(Exception e){failed[0]=e;}});
        if(failed[0]!=null)throw failed[0];
        @SuppressWarnings("unchecked") T value=(T)got[0];return value;
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(System.nanoTime()<until){if(condition.call())return;Thread.sleep(40);}fail("Desktop did not finish in time");
    }
}
