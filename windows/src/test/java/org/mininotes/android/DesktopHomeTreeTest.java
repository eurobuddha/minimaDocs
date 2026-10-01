package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.swing.*;

/**
 * Home's grid beside the tree (docs/HOME.md, decision 26): showing or hiding the tree, or making the window narrower or
 * wider, leaves every icon of the notebook on the grid, laid out for the width it now has and in sight. Synthetic notebook.
 */
public class DesktopHomeTreeTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();

    @Test public void theGridKeepsItsIconsWhenTheTreeComesAndGoes() throws Exception {
        Path folder=temp.newFolder("pad").toPath();Desktop[] app=new Desktop[1];
        SwingUtilities.invokeAndWait(()->{try{app[0]=new Desktop(folder,true);app[0].show();}catch(Exception e){throw new RuntimeException(e);}});
        Desktop pad=app[0];
        try {
            await(()->pad.page.isEditable()&&pad.store.latest()!=null);settle(pad);
            for(String name:new String[]{"Kitchen","Garden","Errands","Books"})pad.store.addCollection(name);
            SwingUtilities.invokeAndWait(()->{pad.goHome();pad.refresh();});settle(pad);
            // The things on Home, and the archive and the bin beside them (decision 41).
            int expected=onEdt(()->pad.store.contents(Things.HOME).size())+2;
            assertTrue("a notebook with things on Home",expected>=6);
            List<String> said=shown(pad);
            assertEquals("every icon in sight on Home: "+said,expected,seen(said));
            // Home drawn again while a note hides it - what the notebook changing under a note being read does - and then
            // gone back to: the icons drawn while it was hidden are laid out when it shows.
            String note=onEdt(()->pad.store.latest().id);
            SwingUtilities.invokeAndWait(()->pad.open(note));settle(pad);
            SwingUtilities.invokeAndWait(pad::refresh);settle(pad);
            SwingUtilities.invokeAndWait(pad::goHome);settle(pad);
            {List<String> now=shown(pad);assertEquals("back on Home: "+now,expected,seen(now));}
            // The tree shown: the grid narrower, every icon still there and in sight - checked at once and again after
            // anything the change sets going has run.
            // The first look comes straight after the resizing the tree causes has been handled, before anything else runs:
            // a grid filled again in the dock's resize and left for Swing's own pass was seen then with every icon at no size.
            SwingUtilities.invokeAndWait(()->{pad.showTree(true);pad.frame.validate();});
            for(int look=0;look<6;look++){
                List<String> now=shown(pad);assertEquals("look "+look+" after the tree came: "+now,expected,seen(now));
                SwingUtilities.invokeAndWait(()->{});Thread.sleep(look==0?0:60);
            }
            settle(pad);
            {List<String> now=shown(pad);assertEquals("with the tree: "+now,expected,seen(now));}
            SwingUtilities.invokeAndWait(()->assertTrue("laid out for the narrower width",pad.home.grid.columns()<DesktopHome.columns(pad.frame.getWidth())));
            // Narrower and wider windows, with the tree beside the grid and without it.
            for(int width:new int[]{860,1400,1000}) {
                SwingUtilities.invokeAndWait(()->{pad.frame.setSize(width,pad.frame.getHeight());pad.frame.validate();});settle(pad);
                List<String> now=shown(pad);assertEquals("at "+width+" wide: "+now,expected,seen(now));
            }
            SwingUtilities.invokeAndWait(()->{pad.showTree(false);pad.frame.validate();});
            {List<String> now=shown(pad);assertEquals("the tree gone again: "+now,expected,seen(now));}
            settle(pad);
            {List<String> now=shown(pad);assertEquals("the tree gone again, settled: "+now,expected,seen(now));}
            // One + at a time: Home's goes while a card is up, whose own is the one, and comes back when it closes.
            NoteStore.Branch kitchen=onEdt(()->{for(DesktopHome.Tile one:pad.home.grid.tiles)if("Kitchen".equals(one.thing.name))return one.thing;return null;});
            SwingUtilities.invokeAndWait(()->pad.home.opened(kitchen,DesktopHome.Where.HOME));settle(pad);
            SwingUtilities.invokeAndWait(()->{assertTrue(pad.home.folder.isOpen());assertFalse("Home's + hidden under the card",pad.home.plus.isVisible());});
            SwingUtilities.invokeAndWait(()->pad.home.folder.close());
            SwingUtilities.invokeAndWait(()->assertTrue("Home's + back",pad.home.plus.isVisible()));
        } finally {SwingUtilities.invokeAndWait(()->pad.shutdown(false));await(()->!pad.frame.isDisplayable());}
    }

    /**
     * The names of the icons on Home's grid that can be seen: laid out at their full size, inside what the grid shows,
     * and where the columns the grid's width holds put them.
     */
    private static List<String> shown(Desktop pad) throws Exception {
        return onEdt(()->{
            List<String> out=new ArrayList<>();
            DesktopHome.Icons grid=pad.home.grid;Rectangle seen=grid.getVisibleRect();
            for(DesktopHome.Tile one:grid.tiles) {
                Rectangle at=one.getBounds();
                if(one.isShowing()&&at.width==DesktopHome.CELL&&at.height==DesktopHome.HIGH&&seen.contains(at)&&at.x+at.width<=grid.getWidth())out.add(one.thing.name);
                else out.add("hidden "+one.thing.name+" at "+at+" in "+seen+" of "+grid.getWidth());
            }
            return out;
        });
    }
    /** How many of them are in sight: what the assertions count, with the whole list said when they disagree. */
    private static int seen(List<String> icons){int n=0;for(String one:icons)if(!one.startsWith("hidden "))n++;return n;}

    private static void settle(Desktop pad) throws Exception {
        for(int i=0;i<3;i++){pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});}
        Thread.sleep(200);SwingUtilities.invokeAndWait(()->{});
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
