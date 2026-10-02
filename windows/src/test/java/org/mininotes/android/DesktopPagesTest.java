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
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.swing.*;

/**
 * Home in pages on the PC (docs/HOME.md, decisions 45-50): an icon carried to the edge and held goes on to the page beyond,
 * which comes to be when it is let go there; the wheel, Shift with it, the keys and a double-click turn the pages; the
 * favourites and the search are on the main page only; every page at once, zoomed out; a smaller window moves icons
 * without losing where they were put; and Home's right-click holds all of Home's own rows. Synthetic notebook; pictures
 * saved beside the gallery's.
 */
public class DesktopPagesTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final Path SHOTS=Path.of("build","verification","gallery");

    @Test public void homeIsPagesInEveryDirection() throws Exception {
        Files.createDirectories(SHOTS);
        Path folder=temp.newFolder("pad").toPath();Desktop[] app=new Desktop[1];
        SwingUtilities.invokeAndWait(()->{try{app[0]=new Desktop(folder,true);app[0].show();}catch(Exception e){throw new RuntimeException(e);}});
        Desktop pad=app[0];
        try {
            await(()->pad.page.isEditable()&&pad.store.latest()!=null);settle(pad);
            NoteStore store=pad.store;
            for(String title:new String[]{"Shopping","Ideas","Bills"})note(store,Things.HOME,title);
            SwingUtilities.invokeAndWait(()->{pad.goHome();pad.refresh();});settle(pad);
            DesktopHome home=pad.home;DesktopHome.Icons grid=home.grid;
            int rows=onEdt(grid::rows),cols=onEdt(grid::columns);
            assertTrue("a page of some rows: "+rows,rows>=2);
            SwingUtilities.invokeAndWait(()->{
                assertTrue(grid.onCentre());
                assertEquals("one page, the main one",1,Layout.active(grid.spots()).size());
                assertTrue("the search on the main page",home.search.isVisible());
                assertFalse("nothing to turn to",grid.turn(1,0));
            });

            // Ideas carried to the right edge and held there: the page to the right comes, empty, and it is let go there.
            Point edge=onEdt(()->{Point p=new Point(home.scroll.getWidth()-8,home.scroll.getHeight()/2);SwingUtilities.convertPointToScreen(p,home.scroll);return p;});
            SwingUtilities.invokeAndWait(()->{carryAt(pad,tile(grid,"Ideas"),edge);
                assertTrue(pad.status.getText(),pad.status.getText().startsWith("Hold here for the page to the right"));});
            Thread.sleep(1000);SwingUtilities.invokeAndWait(()->{});
            SwingUtilities.invokeAndWait(()->{assertEquals("the page to the right",1,grid.pageX);assertEquals(0,grid.pageY);
                assertFalse("no search off the main page",home.search.isVisible());});
            SwingUtilities.invokeAndWait(()->{Rectangle r=grid.cellBounds(1,2);Point to=new Point(r.x+r.width/2,r.y+40);SwingUtilities.convertPointToScreen(to,grid);
                carryTo(pad,to);assertEquals(r,grid.landing);release(pad,to);});
            settle(pad);
            NoteStore.Branch ideas=byName(onEdt(()->store.contents(Things.HOME)),"Ideas");
            assertEquals("kept on that page",Layout.page(1,0),ideas.page);assertEquals(Layout.cell(1,2),ideas.cell);
            SwingUtilities.invokeAndWait(()->{
                assertEquals("that page there now",2,Layout.active(grid.spots()).size());
                assertTrue(pad.status.getText(),pad.status.getText().startsWith("Moved to page one to the right"));
                assertTrue(tile(grid,"Ideas").isVisible());assertFalse("the others are on the main page",tile(grid,"Shopping").isVisible());
            });
            shoot(pad.frame,"97a-page-to-the-right");

            // Turning: back with the keys' way, by the wheel with Shift, and a double-click home.
            SwingUtilities.invokeAndWait(()->{assertTrue(grid.turn(-1,0));assertTrue(grid.onCentre());assertTrue(home.search.isVisible());});
            SwingUtilities.invokeAndWait(()->{for(MouseWheelListener one:grid.getMouseWheelListeners())one.mouseWheelMoved(new MouseWheelEvent(grid,MouseEvent.MOUSE_WHEEL,System.currentTimeMillis(),InputEvent.SHIFT_DOWN_MASK,10,10,1,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,3,1));});
            SwingUtilities.invokeAndWait(()->assertEquals("Shift and the wheel: sideways",1,grid.pageX));
            shoot(pad.frame,"97b-dots");
            SwingUtilities.invokeAndWait(()->{for(MouseListener one:grid.getMouseListeners())one.mouseClicked(new MouseEvent(grid,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,10,10,2,false,MouseEvent.BUTTON1));});
            SwingUtilities.invokeAndWait(()->assertTrue("a double-click: the main page",grid.onCentre()));

            // Every page at once, and a click on one goes there.
            SwingUtilities.invokeAndWait(()->home.pages.up());settle(pad);
            shoot(pad.frame,"97c-all-pages");
            SwingUtilities.invokeAndWait(()->{
                assertTrue(home.pages.isVisible());
                Rectangle right=pageAt(home.pages,1,0);
                assertNotNull(right);
                for(MouseListener one:home.pages.getMouseListeners())one.mouseClicked(new MouseEvent(home.pages,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,right.x+right.width/2,right.y+right.height/2,1,false,MouseEvent.BUTTON1));
                assertFalse(home.pages.isVisible());assertEquals(1,grid.pageX);
            });

            // A whole page carried, zoomed out: to an empty place it moves; onto the main page the two change places; Undo.
            SwingUtilities.invokeAndWait(()->home.movePage(1,0,-1,0));settle(pad);
            assertEquals("the page to the right is to the left now",Layout.page(-1,0),byName(onEdt(()->store.contents(Things.HOME)),"Ideas").page);
            SwingUtilities.invokeAndWait(()->{assertFalse(Layout.active(grid.spots()).contains(List.of(1,0)));assertEquals("Page moved",pad.status.getText());});
            SwingUtilities.invokeAndWait(()->home.movePage(-1,0,0,0));settle(pad);
            assertEquals("Ideas is on the main page",Layout.CENTRE,byName(onEdt(()->store.contents(Things.HOME)),"Ideas").page);
            assertEquals("and the main page's icons where Ideas was",Layout.page(-1,0),byName(onEdt(()->store.contents(Things.HOME)),"Shopping").page);
            SwingUtilities.invokeAndWait(()->assertEquals("The two pages changed places",pad.status.getText()));
            SwingUtilities.invokeAndWait(pad::undo);settle(pad);
            assertEquals("Undo: back to the left",Layout.page(-1,0),byName(onEdt(()->store.contents(Things.HOME)),"Ideas").page);
            SwingUtilities.invokeAndWait(()->home.movePage(-1,0,1,0));settle(pad);
            assertEquals(Layout.page(1,0),byName(onEdt(()->store.contents(Things.HOME)),"Ideas").page);
            // The same with the mouse, every page up: pressed on the page to the right, dragged to the empty place below it.
            SwingUtilities.invokeAndWait(()->home.pages.up());settle(pad);
            SwingUtilities.invokeAndWait(()->{
                Rectangle from=pageAt(home.pages,1,0),to=placeAt(home.pages,1,1);
                assertNotNull(from);assertNotNull("the empty place below it is one a page can go",to);
                mouse(home.pages,MouseEvent.MOUSE_PRESSED,from.x+from.width/2,from.y+from.height/2);
                mouse(home.pages,MouseEvent.MOUSE_DRAGGED,from.x+from.width/2,from.y+from.height/2+20);
                mouse(home.pages,MouseEvent.MOUSE_DRAGGED,to.x+to.width/2,to.y+to.height/2);
                assertTrue(pad.status.getText(),pad.status.getText().startsWith("Let go on another page"));
            });
            shoot(pad.frame,"97d-page-carried");
            SwingUtilities.invokeAndWait(()->{Rectangle to=placeAt(home.pages,1,1);mouse(home.pages,MouseEvent.MOUSE_RELEASED,to.x+to.width/2,to.y+to.height/2);});
            settle(pad);
            assertEquals("let go below: it is there",Layout.page(1,1),byName(onEdt(()->store.contents(Things.HOME)),"Ideas").page);
            SwingUtilities.invokeAndWait(()->{assertTrue("every page still up",home.pages.isVisible());assertEquals("Page moved",pad.status.getText());});
            // Pressed and let go where it was: nothing moves.
            SwingUtilities.invokeAndWait(()->{
                Rectangle at=pageAt(home.pages,1,1);
                mouse(home.pages,MouseEvent.MOUSE_PRESSED,at.x+5,at.y+5);mouse(home.pages,MouseEvent.MOUSE_DRAGGED,at.x+25,at.y+25);mouse(home.pages,MouseEvent.MOUSE_RELEASED,at.x+25,at.y+25);
                assertEquals("Not moved",pad.status.getText());
            });
            SwingUtilities.invokeAndWait(()->home.movePage(1,1,1,0));settle(pad);
            SwingUtilities.invokeAndWait(()->home.pages.away());

            // Carried up out of the rows, into the bar under the app's name: the page above at once, no wait (decision 54).
            SwingUtilities.invokeAndWait(()->grid.showPage(0,0));settle(pad);
            Point above=onEdt(()->{Point p=new Point(home.scroll.getWidth()/2,-12);SwingUtilities.convertPointToScreen(p,home.scroll);return p;});
            SwingUtilities.invokeAndWait(()->{carryAt(pad,tile(grid,"Bills"),above);
                assertEquals("the page above, at once",-1,grid.pageY);assertEquals(0,grid.pageX);
                assertTrue(pad.status.getText(),pad.status.getText().startsWith("Page one up now"));});
            SwingUtilities.invokeAndWait(()->{carryTo(pad,new Point(above.x,above.y-4));assertEquals("one page each time it goes out",-1,grid.pageY);});
            SwingUtilities.invokeAndWait(()->{Rectangle r=grid.cellBounds(0,0);Point to=new Point(r.x+r.width/2,r.y+40);SwingUtilities.convertPointToScreen(to,grid);carryTo(pad,to);release(pad,to);});
            settle(pad);
            assertEquals("let go on the page above",Layout.page(0,-1),byName(onEdt(()->store.contents(Things.HOME)),"Bills").page);
            SwingUtilities.invokeAndWait(pad::undo);settle(pad);
            assertEquals("Undo: back on the main page",Layout.CENTRE,byName(onEdt(()->store.contents(Things.HOME)),"Bills").page);
            // Over the last row: it stays on this page, to be let go there.
            SwingUtilities.invokeAndWait(()->grid.showPage(0,0));settle(pad);
            Point lastRow=onEdt(()->{Rectangle r=grid.cellBounds(grid.rows()-1,1);Point p=new Point(r.x+r.width/2,r.y+r.height-6);SwingUtilities.convertPointToScreen(p,grid);return p;});
            SwingUtilities.invokeAndWait(()->carryAt(pad,tile(grid,"Bills"),lastRow));
            Thread.sleep(600);SwingUtilities.invokeAndWait(()->{});
            SwingUtilities.invokeAndWait(()->{assertEquals("over the last row: no turn",0,grid.pageY);pad.home.carry.cancel();});
            settle(pad);
            // At the page's lower edge, with the dock there: the page below after a moment, not as it passes on its way to the dock.
            Point below=onEdt(()->{Point p=new Point(home.scroll.getWidth()/2,home.scroll.getHeight()-8);SwingUtilities.convertPointToScreen(p,home.scroll);return p;});
            SwingUtilities.invokeAndWait(()->{carryAt(pad,tile(grid,"Bills"),below);
                if(home.dock.isShowing())assertEquals("not yet, while the dock is there",0,grid.pageY);});
            Thread.sleep(600);SwingUtilities.invokeAndWait(()->{});
            SwingUtilities.invokeAndWait(()->{assertEquals("the page below",1,grid.pageY);
                Point back=new Point(grid.cellBounds(0,0).x+30,grid.cellBounds(0,0).y+30);SwingUtilities.convertPointToScreen(back,grid);
                carryTo(pad,back);pad.home.carry.cancel();});
            settle(pad);
            assertEquals("Esc: where it was",Layout.CENTRE,byName(onEdt(()->store.contents(Things.HOME)),"Bills").page);

            // Every + takes something from another device too (the owner: "this is key"), a card's as well as Home's.
            SwingUtilities.invokeAndWait(()->{
                List<String> onHome=new ArrayList<>(),inACard=new ArrayList<>();
                for(Component one:home.plusMenu(Things.HOME).getComponents())if(one instanceof JMenuItem item)onHome.add(item.getText());
                for(Component one:home.plusMenu("some-collection").getComponents())if(one instanceof JMenuItem item)inACard.add(item.getText());
                assertEquals(List.of("Note","Collection","From another device…"),onHome);
                assertEquals(List.of("Note","Collection","From another device…"),inACard);
            });

            // Favourites and search shown or not, from Home's own menu; on the main page only either way.
            SwingUtilities.invokeAndWait(()->grid.showPage(0,0));
            SwingUtilities.invokeAndWait(()->pad.setShowing(Desktop.SHOW_SEARCH,false));
            SwingUtilities.invokeAndWait(()->assertFalse(home.search.isVisible()));
            SwingUtilities.invokeAndWait(()->pad.setShowing(Desktop.SHOW_SEARCH,true));
            SwingUtilities.invokeAndWait(()->assertTrue(home.search.isVisible()));

            // Home's right-click: everything about Home.
            List<String> rows2=onEdt(()->{List<String> said=new ArrayList<>();JPopupMenu menu=menuOf(pad);for(Component c:menu.getComponents())if(c instanceof JMenuItem item)said.add(item.getText());return said;});
            for(String want:new String[]{"New note","New collection","From another device…","Sync now","Show favourites","Show search","All pages"})
                assertTrue(want+" in "+rows2,rows2.contains(want));
            assertTrue("Home's colour in "+rows2,rows2.stream().anyMatch(t->t.startsWith("Colour")));

            // A smaller window: the page holds fewer, and Ideas, put in row 2 on its page, moves on its page without its place
            // being written; a bigger one puts it back.
            SwingUtilities.invokeAndWait(()->grid.showPage(1,0));
            Dimension was=onEdt(()->pad.frame.getSize());
            SwingUtilities.invokeAndWait(()->pad.frame.setSize(was.width,Math.max(300,was.height-(rows-1)*DesktopHome.HIGH)));settle(pad);
            int fewer=onEdt(grid::rows);
            if(fewer<=1) {
                SwingUtilities.invokeAndWait(()->{Layout.Spot now=grid.spots().get(tile(grid,"Ideas").thing.id);assertEquals("on its own page still",1,now.x());assertTrue(now.row()<fewer);});
                assertEquals("its place not written over",Layout.cell(1,2),byName(onEdt(()->store.contents(Things.HOME)),"Ideas").cell);
            }
            SwingUtilities.invokeAndWait(()->pad.frame.setSize(was));settle(pad);
            SwingUtilities.invokeAndWait(()->assertEquals(new Layout.Spot(1,0,1,2),grid.spots().get(tile(grid,"Ideas").thing.id)));
        } finally {SwingUtilities.invokeAndWait(()->pad.shutdown(false));await(()->!pad.frame.isDisplayable());}
    }

    /** Where the zoomed-out view drew a page, found by clicking through its rectangles' middles. */
    private static Rectangle pageAt(DesktopPages pages,int x,int y) {
        try {
            java.lang.reflect.Field f=DesktopPages.class.getDeclaredField("drawnAt");f.setAccessible(true);
            @SuppressWarnings("unchecked") Map<List<Integer>,Rectangle> at=(Map<List<Integer>,Rectangle>)f.get(pages);
            return at.get(List.of(x,y));
        } catch(Exception e){throw new RuntimeException(e);}
    }
    private static Rectangle placeAt(DesktopPages pages,int x,int y) {
        try {
            java.lang.reflect.Field f=DesktopPages.class.getDeclaredField("placesAt");f.setAccessible(true);
            @SuppressWarnings("unchecked") Map<List<Integer>,Rectangle> at=(Map<List<Integer>,Rectangle>)f.get(pages);
            return at.get(List.of(x,y));
        } catch(Exception e){throw new RuntimeException(e);}
    }
    /** A press, a drag or a let-go of the left button, as the pages hear it. */
    private static void mouse(JComponent on,int id,int x,int y) {
        MouseEvent e=new MouseEvent(on,id,System.currentTimeMillis(),id==MouseEvent.MOUSE_RELEASED?0:InputEvent.BUTTON1_DOWN_MASK,x,y,1,false,MouseEvent.BUTTON1);
        if(id==MouseEvent.MOUSE_DRAGGED)for(MouseMotionListener one:on.getMouseMotionListeners())one.mouseDragged(e);
        else if(id==MouseEvent.MOUSE_PRESSED)for(MouseListener one:on.getMouseListeners())one.mousePressed(e);
        else for(MouseListener one:on.getMouseListeners())one.mouseReleased(e);
    }
    /** Home's right-click menu, as it would open. */
    private static JPopupMenu menuOf(Desktop pad) {
        JPopupMenu[] got={null};
        JComponent at=pad.home.grid;
        // roomMenu shows it; catch it by showing at a point and reading what opened.
        pad.roomMenu(Desktop.library(),at,10,10);
        for(Window w:Window.getWindows())for(Component c:allOf(w))if(c instanceof JPopupMenu m&&m.isVisible())got[0]=m;
        if(got[0]!=null)got[0].setVisible(false);
        return got[0];
    }
    private static List<Component> allOf(Container in){List<Component> out=new ArrayList<>();for(Component c:in.getComponents()){out.add(c);if(c instanceof Container k)out.addAll(allOf(k));}return out;}

    private static NoteStore.Note note(NoteStore store,String in,String title) {
        NoteStore.Note one=new NoteStore.Note();one.book=in;one.title=title;one.body="Synthetic "+title.toLowerCase();store.save(one);return one;
    }
    private static NoteStore.Branch byName(List<NoteStore.Branch> lines,String name){for(NoteStore.Branch one:lines)if(name.equals(one.name))return one;throw new AssertionError("no "+name);}
    private static DesktopHome.Tile tile(DesktopHome.Icons grid,String name) {
        for(DesktopHome.Tile one:grid.tiles)if(name.equals(one.thing.name))return one;
        throw new AssertionError("no icon "+name);
    }
    private static void carryAt(Desktop pad,DesktopHome.Tile from,Point screen) {
        Point start=from.getLocationOnScreen();
        pad.home.carry.press(from,new MouseEvent(from,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),InputEvent.BUTTON1_DOWN_MASK,
            20,20,start.x+20,start.y+20,1,false,MouseEvent.BUTTON1));
        carryTo(pad,screen);
    }
    private static void carryTo(Desktop pad,Point screen) {
        pad.home.carry.drag(new MouseEvent(pad.home.grid,MouseEvent.MOUSE_DRAGGED,System.currentTimeMillis(),InputEvent.BUTTON1_DOWN_MASK,0,0,screen.x,screen.y,1,false,MouseEvent.BUTTON1));
    }
    private static void release(Desktop pad,Point screen) {
        pad.home.carry.release(new MouseEvent(pad.home.grid,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,0,0,screen.x,screen.y,1,false,MouseEvent.BUTTON1));
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
