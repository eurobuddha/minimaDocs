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
 * Icons where they are put, on the PC (docs/HOME.md, decision 39): carried into an empty cell of Home or a card they stay
 * there, with the cells between left empty, one Undo from where they were; the empty cell under a carried icon is outlined
 * and said; Ctrl+arrow moves an icon a cell; out of a card onto the dimmed Home it lands in the cell it is let go in. And
 * the dock: every icon it draws can be taken out of it. Synthetic notebook; pictures saved beside the gallery's.
 */
public class DesktopPlacesTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final Path SHOTS=Path.of("build","verification","gallery");

    @Test public void iconsStayWhereTheyAreLetGo() throws Exception {
        Files.createDirectories(SHOTS);
        Path folder=temp.newFolder("pad").toPath();Desktop[] app=new Desktop[1];
        SwingUtilities.invokeAndWait(()->{try{app[0]=new Desktop(folder,true);app[0].show();}catch(Exception e){throw new RuntimeException(e);}});
        Desktop pad=app[0];
        try {
            await(()->pad.page.isEditable()&&pad.store.latest()!=null);settle(pad);
            NoteStore store=pad.store;
            NoteStore.Shelf kitchen=store.addCollection("Kitchen");store.addCollection("Garden");
            for(String title:new String[]{"Shopping","Ideas","Bills"})note(store,Things.HOME,title);
            for(String title:new String[]{"Soup","Bread","Cake"})note(store,kitchen.id,title);
            SwingUtilities.invokeAndWait(()->{pad.goHome();pad.refresh();});settle(pad);

            // Nobody has moved anything: every icon one after the other, as Home always was, and nothing kept.
            for(NoteStore.Branch one:onEdt(()->store.contents(Things.HOME)))assertEquals(one.name,Layout.NONE,one.cell);
            Map<String,int[]> packed=onEdt(()->new java.util.LinkedHashMap<>(pad.home.grid.drawn()));
            int cols=onEdt(()->pad.home.grid.columns());
            assertTrue("room for five across: "+cols,cols>=5);
            {int at=0;for(int[] cell:packed.values()){assertArrayEquals(new int[]{at/cols,at%cols},cell);at++;}}

            // Shopping carried to row 3, column 5: the empty cell outlined, and said.
            DesktopHome.Icons home=pad.home.grid;
            SwingUtilities.invokeAndWait(()->{
                Rectangle r=home.cellBounds(2,4);
                carry(pad,tile(home,"Shopping"),home,r.x+r.width/2,r.y+DesktopHome.FACE/2+10);
                assertEquals("the landing outlined",r,home.landing);
                assertTrue(pad.status.getText(),pad.status.getText().startsWith("Let go to put it here"));
            });
            Thread.sleep(200);SwingUtilities.invokeAndWait(pad.frame::validate);shoot(pad.frame,"18c-home-carry-landing");
            // Over another note: that one ringed, to put the two together, and no landing drawn.
            SwingUtilities.invokeAndWait(()->{
                DesktopHome.Tile ideas=tile(home,"Ideas");carryTo(pad,ideas,ideas.getWidth()-6,ideas.getHeight()-8);
                assertTrue("the whole of its cell is the note",ideas.target);assertNull(home.landing);
                assertTrue(pad.status.getText(),pad.status.getText().startsWith("Let go to put them together"));
            });
            // Back over its own cell: it would stay.
            SwingUtilities.invokeAndWait(()->{DesktopHome.Tile shopping=tile(home,"Shopping");carryTo(pad,shopping,20,20);
                assertTrue(pad.status.getText(),pad.status.getText().startsWith("Let go to leave it where it was"));});
            SwingUtilities.invokeAndWait(()->{Rectangle r=home.cellBounds(2,4);carryTo(pad,home,r.x+r.width/2,r.y+40);letGo(pad,home,r.x+r.width/2,r.y+40);});
            settle(pad);
            Map<String,NoteStore.Branch> now=byName(onEdt(()->store.contents(Things.HOME)));
            assertEquals("kept where it was let go",Layout.cell(2,4),now.get("Shopping").cell);
            // Every other icon pinned where it was drawn, so nothing jumps into the gap it left.
            for(DesktopHome.Tile one:onEdt(()->new ArrayList<>(home.tiles)))if(!"Shopping".equals(one.thing.name)&&now.containsKey(one.thing.name)){
                int[] was=packed.get(one.thing.id);assertEquals(one.thing.name,Layout.cell(was[0],was[1]),now.get(one.thing.name).cell);
            }
            SwingUtilities.invokeAndWait(()->{
                assertEquals(home.cellBounds(2,4),tile(home,"Shopping").getBounds());
                for(int column=0;column<4;column++)assertNull("the cells before it in its row are empty",home.in(2,column));
                assertEquals("Moved",pad.status.getText());
            });
            shoot(pad.frame,"18b-home-placed");

            // Undo: every icon back where it was, which was nowhere in particular.
            SwingUtilities.invokeAndWait(pad::undo);settle(pad);
            for(NoteStore.Branch one:onEdt(()->store.contents(Things.HOME)))assertEquals(one.name,Layout.NONE,one.cell);
            SwingUtilities.invokeAndWait(()->assertArrayEquals(packed.get(tile(home,"Shopping").thing.id),home.drawn().get(tile(home,"Shopping").thing.id)));
            // And again, to go on with.
            SwingUtilities.invokeAndWait(()->{Rectangle r=home.cellBounds(2,4);carry(pad,tile(home,"Shopping"),home,r.x+20,r.y+20);letGo(pad,home,r.x+20,r.y+20);});
            settle(pad);
            assertEquals(Layout.cell(2,4),byName(onEdt(()->store.contents(Things.HOME))).get("Shopping").cell);

            // Ctrl+Right: a cell to the right, said; Ctrl+Left back; Ctrl+Up where row 2 is empty.
            key(pad,()->tile(home,"Shopping"),KeyEvent.VK_RIGHT,InputEvent.CTRL_DOWN_MASK);settle(pad);
            SwingUtilities.invokeAndWait(()->assertEquals("“Shopping” is in row 3, column 6 now",pad.status.getText()));
            assertEquals(Layout.cell(2,5),byName(onEdt(()->store.contents(Things.HOME))).get("Shopping").cell);
            key(pad,()->tile(home,"Shopping"),KeyEvent.VK_LEFT,InputEvent.CTRL_DOWN_MASK);settle(pad);
            key(pad,()->tile(home,"Shopping"),KeyEvent.VK_UP,InputEvent.ALT_DOWN_MASK);settle(pad);
            assertEquals("Alt+arrow as well",Layout.cell(1,4),byName(onEdt(()->store.contents(Things.HOME))).get("Shopping").cell);
            key(pad,()->tile(home,"Shopping"),KeyEvent.VK_DOWN,InputEvent.CTRL_DOWN_MASK);settle(pad);
            assertEquals(Layout.cell(2,4),byName(onEdt(()->store.contents(Things.HOME))).get("Shopping").cell);
            // Into another icon's cell it does not go, and says what is in the way.
            String first=onEdt(()->home.in(0,0).thing.name),second=onEdt(()->home.in(0,1).thing.name);
            key(pad,()->home.in(0,1),KeyEvent.VK_LEFT,InputEvent.CTRL_DOWN_MASK);
            SwingUtilities.invokeAndWait(()->assertEquals("“"+first+"” is in the way",pad.status.getText()));
            key(pad,()->home.in(0,0),KeyEvent.VK_LEFT,InputEvent.CTRL_DOWN_MASK);
            SwingUtilities.invokeAndWait(()->assertEquals("It is at the edge of the grid",pad.status.getText()));
            settle(pad);
            assertEquals(second,onEdt(()->home.in(0,1).thing.name));

            // A collection's card: Soup carried to the second row, a gap left before it.
            NoteStore.Branch kitchenLine=onEdt(()->tile(home,"Kitchen").thing);
            SwingUtilities.invokeAndWait(()->pad.home.opened(kitchenLine,DesktopHome.Where.HOME));settle(pad);
            DesktopHome.Icons card=pad.home.folder.grid;
            SwingUtilities.invokeAndWait(()->{Rectangle r=card.cellBounds(1,2);carry(pad,tile(card,"Soup"),card,r.x+r.width/2,r.y+40);
                assertEquals(r,card.landing);letGo(pad,card,r.x+r.width/2,r.y+40);});
            settle(pad);
            assertEquals(Layout.cell(1,2),byName(onEdt(()->store.contents(kitchen.id))).get("Soup").cell);
            SwingUtilities.invokeAndWait(()->{assertTrue(pad.home.folder.isOpen());assertEquals(card.cellBounds(1,2),tile(card,"Soup").getBounds());});
            shoot(pad.frame,"19f-card-gap");

            // Bread out of the card onto the dimmed Home, beside the card, over an empty cell of Home: up a level, into it.
            int[] free=onEdt(()->{for(int row=1;row<8;row++)if(home.in(row,0)==null)return new int[]{row,0};return null;});
            assertNotNull(free);
            Point over=onEdt(()->{
                Rectangle r=home.cellBounds(free[0],free[1]);Point p=new Point(0,r.y+r.height/2);
                SwingUtilities.convertPointToScreen(p,home);
                Point desk=new Point(0,0);SwingUtilities.convertPointToScreen(desk,pad.home.folder);
                return new Point(desk.x+12,p.y);
            });
            SwingUtilities.invokeAndWait(()->{
                assertTrue("beside the card",!pad.home.folder.card.getBounds().contains(12,over.y-pad.home.folder.getLocationOnScreen().y));
                carryAt(pad,tile(card,"Bread"),over);
                assertEquals(home.cellBounds(free[0],free[1]),home.landing);
                assertEquals("Let go to move it up, into Home, here  ·  Esc cancels",pad.status.getText());
            });
            Thread.sleep(200);SwingUtilities.invokeAndWait(pad.frame::validate);shoot(pad.frame,"19g-card-up-to-home");
            SwingUtilities.invokeAndWait(()->pad.home.carry.release(new MouseEvent(tile(card,"Bread"),MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,0,0,over.x,over.y,1,false,MouseEvent.BUTTON1)));
            settle(pad);
            NoteStore.Branch bread=byName(onEdt(()->store.contents(Things.HOME))).get("Bread");
            assertNotNull("Bread is on Home",bread);
            assertEquals("in the cell it was let go in",Layout.cell(free[0],free[1]),bread.cell);
            SwingUtilities.invokeAndWait(()->assertFalse("the card went up a level: closed",pad.home.folder.isOpen()));

            // The dock: seven favourites - the notebook's five and two past them, which the PC has room for.
            List<NoteStore.Note> faves=new ArrayList<>();
            for(int n=1;n<=7;n++){NoteStore.Note one=note(store,kitchen.id,"Favourite "+n);store.keepToHand(NoteStore.Branch.Kind.PAGE,one.id,true);faves.add(one);}
            SwingUtilities.invokeAndWait(pad::refresh);settle(pad);
            SwingUtilities.invokeAndWait(()->assertEquals(dockIds(pad).toString(),7,pad.home.dock.tiles.size()));
            // The dock draws the notebook's five first, then the rest: the sixth icon is one past them.
            String sixth=onEdt(()->pad.home.dock.tiles.get(5).thing.id),fromTheFive=onEdt(()->pad.home.dock.tiles.get(1).thing.id);
            assertEquals(5,onEdt(()->store.dock().size()).intValue());
            assertFalse("the sixth is past the notebook's five",ids(onEdt(()->store.dock())).contains(sixth));
            assertTrue(ids(onEdt(()->store.dock())).contains(fromTheFive));
            SwingUtilities.invokeAndWait(()->{
                assertTrue("offered on one past the fifth",offers(pad,pad.home.dock.tiles.get(5).thing));
                assertTrue(offers(pad,pad.home.dock.tiles.get(1).thing));
            });
            // Taken out: gone from the dock, waiting in Favourites, which comes first on Home.
            NoteStore.Branch sixthLine=onEdt(()->pad.home.dock.tiles.get(5).thing);
            SwingUtilities.invokeAndWait(()->pad.undock(sixthLine));settle(pad);
            SwingUtilities.invokeAndWait(()->{
                assertFalse(dockIds(pad).contains(sixth));assertEquals(6,pad.home.dock.tiles.size());
                assertTrue(ids(pad.home.overflow()).contains(sixth));
                assertEquals(NoteStore.Branch.Kind.FAVOURITES,pad.home.grid.tiles.get(0).thing.kind);
                assertFalse("not offered on one the dock does not draw",pad.home.inDock(sixth));
            });
            // One of the notebook's five taken out too: it leaves that dock, and the room it leaves is not refilled by the sixth.
            NoteStore.Branch secondLine=onEdt(()->pad.home.dock.tiles.get(1).thing);
            SwingUtilities.invokeAndWait(()->pad.undock(secondLine));settle(pad);
            assertFalse(ids(onEdt(()->store.dock())).contains(fromTheFive));
            SwingUtilities.invokeAndWait(()->{assertFalse(dockIds(pad).contains(fromTheFive));assertFalse(dockIds(pad).contains(sixth));assertEquals(5,pad.home.dock.tiles.size());});
            shoot(pad.frame,"95c-dock-taken-out");
            // A move on Home with the Favourites icon on it pins that too, kept in this PC's settings: it does not jump into
            // the cell the moved one leaves.
            int[] favouritesAt=onEdt(()->home.drawn().get(NoteStore.FAVOURITES).clone()),ideasWas=onEdt(()->home.drawn().get(tile(home,"Ideas").thing.id).clone());
            key(pad,()->tile(home,"Ideas"),KeyEvent.VK_DOWN,InputEvent.CTRL_DOWN_MASK);settle(pad);
            SwingUtilities.invokeAndWait(()->{
                assertArrayEquals("the Favourites icon stays",favouritesAt,home.drawn().get(NoteStore.FAVOURITES));
                assertNull("the cell Ideas left stays empty",home.in(ideasWas[0],ideasWas[1]));
                assertEquals(NoteStore.Branch.Kind.FAVOURITES,pad.home.grid.tiles.get(0).thing.kind);
            });
            assertEquals(Layout.cell(favouritesAt[0],favouritesAt[1]),pad.context.getSharedPreferences("settings",0).getLong(NoteStore.FAVOURITES+"Cell",Layout.NONE));
            // Starred again, or carried onto the dock, it comes back.
            NoteStore.Branch sixthAgain=sixthLine;
            SwingUtilities.invokeAndWait(()->pad.favourite(sixthAgain,true));settle(pad);
            SwingUtilities.invokeAndWait(()->assertTrue(dockIds(pad).contains(sixth)));
        } finally {SwingUtilities.invokeAndWait(()->pad.shutdown(false));await(()->!pad.frame.isDisplayable());}
    }

    private static NoteStore.Note note(NoteStore store,String in,String title) {
        NoteStore.Note one=new NoteStore.Note();one.book=in;one.title=title;one.body="Synthetic "+title.toLowerCase();store.save(one);return one;
    }
    private static Map<String,NoteStore.Branch> byName(List<NoteStore.Branch> lines) {
        Map<String,NoteStore.Branch> out=new java.util.HashMap<>();for(NoteStore.Branch one:lines)out.put(one.name,one);return out;
    }
    private static List<String> ids(List<NoteStore.Branch> lines){List<String> out=new ArrayList<>();for(NoteStore.Branch one:lines)out.add(one.id);return out;}
    private static List<String> dockIds(Desktop pad){List<String> out=new ArrayList<>();for(DesktopHome.Tile one:pad.home.dock.tiles)out.add(one.thing.id);return out;}
    /** Whether a thing's menu offers to take it out of the dock. */
    private static boolean offers(Desktop pad,NoteStore.Branch thing) {
        for(Component c:pad.thingMenu(thing).getComponents())if(c instanceof JMenuItem item&&"Remove from the dock".equals(item.getText()))return true;
        return false;
    }
    private static DesktopHome.Tile tile(DesktopHome.Icons grid,String name) {
        for(DesktopHome.Tile one:grid.tiles)if(name.equals(one.thing.name))return one;
        throw new AssertionError("no icon "+name);
    }
    /** A key pressed on an icon, handed to what it listens with: the window need not have the keyboard for it. */
    private static void key(Desktop pad,Callable<DesktopHome.Tile> on,int code,int modifiers) throws Exception {
        SwingUtilities.invokeAndWait(()->{try{
            DesktopHome.Tile tile=on.call();
            KeyEvent press=new KeyEvent(tile,KeyEvent.KEY_PRESSED,System.currentTimeMillis(),modifiers,code,KeyEvent.CHAR_UNDEFINED);
            for(KeyListener one:tile.getKeyListeners())one.keyPressed(press);
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    /** An icon pressed, as the left button presses it, and carried to a point on another component. */
    private static void carry(Desktop pad,DesktopHome.Tile from,Component over,int x,int y) {
        Point to=over.getLocationOnScreen();to.translate(x,y);carryAt(pad,from,to);
    }
    private static void carryAt(Desktop pad,DesktopHome.Tile from,Point screen) {
        Point start=from.getLocationOnScreen();
        pad.home.carry.press(from,new MouseEvent(from,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),InputEvent.BUTTON1_DOWN_MASK,
            20,20,start.x+20,start.y+20,1,false,MouseEvent.BUTTON1));
        pad.home.carry.drag(new MouseEvent(from,MouseEvent.MOUSE_DRAGGED,System.currentTimeMillis(),InputEvent.BUTTON1_DOWN_MASK,0,0,screen.x,screen.y,1,false,MouseEvent.BUTTON1));
    }
    /** The icon in hand moved to a point on a component, as the pointer drags it there. */
    private static void carryTo(Desktop pad,Component over,int x,int y) {
        Point to=over.getLocationOnScreen();to.translate(x,y);
        pad.home.carry.drag(new MouseEvent(over,MouseEvent.MOUSE_DRAGGED,System.currentTimeMillis(),InputEvent.BUTTON1_DOWN_MASK,0,0,to.x,to.y,1,false,MouseEvent.BUTTON1));
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
