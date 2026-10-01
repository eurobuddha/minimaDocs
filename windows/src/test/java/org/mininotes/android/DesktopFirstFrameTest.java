package org.mininotes.android;

import static org.junit.Assert.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mininotes.desktop.platform.content.Context;

/**
 * The window comes up whole, in one step: the box that opened it - the password, or with no lock a box saying it is
 * opening - stays up until the tree, the tabs and the note with its files and people are drawn. What the window holds
 * is read the moment it first shows, and drawn to build/verification/gallery to look at. Made-up notes and people.
 */
public class DesktopFirstFrameTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    /** Nothing left up for the next test to find, whatever this one did. */
    @After public void away() throws Exception{SwingUtilities.invokeAndWait(()->{for(Window w:Window.getWindows())if(w.isDisplayable())w.dispose();});}
    private static boolean showing(){for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isShowing())return true;return false;}
    private static final Path SHOTS=Path.of("build","verification","gallery");

    @Test public void afterUnlockingTheFirstFrameHasTheTreeAndTheNote() throws Exception {
        Path folder=temp.newFolder("locked").toPath();
        Vault.Made made=Vault.make("password1".toCharArray(),20_000);
        String id=notebook(folder,made.key);
        Files.write(folder.resolve(DesktopLock.KEPT),made.kept);
        AtomicReference<Seen> first=watchFirstFrame();
        byte[][] key={null};
        SwingUtilities.invokeLater(()->{try{key[0]=DesktopLock.askAtStart(folder,false,Desktop.opener(folder,true));}catch(Exception e){throw new RuntimeException(e);}});
        JDialog box=box("Mininotes is locked");
        JPasswordField password=(JPasswordField)find(box,"unlockPassword");JButton open=(JButton)find(box,"unlockOpen");
        boolean[] whileOpening=new boolean[3];
        SwingUtilities.invokeAndWait(()->{password.setText("password1");open.doClick();
            // Right after the key: the box says so, with the strip moving, and nothing can be pressed twice.
            whileOpening[0]=find(box,"openingStrip").isVisible();whileOpening[1]=text(box).contains(DesktopLock.OPENING);whileOpening[2]=!open.isEnabled()&&!password.isEnabled();
});
        SwingUtilities.invokeAndWait(()->{if(box.isDisplayable())save(box,"00a-unlock-opening");});
        assertTrue("the strip moves while it opens",whileOpening[0]);
        assertTrue("it says it is opening",whileOpening[1]);
        assertTrue("the ways in are greyed",whileOpening[2]);
        await(()->first.get()!=null&&key[0]!=null);
        whole(first.get(),id,"01a-first-frame-after-unlock");
        assertArrayEquals(made.key,key[0]);
        close(first.get().pad);
    }

    @Test public void atAPlainStartTheFirstFrameHasTheTreeAndTheNote() throws Exception {
        Path folder=temp.newFolder("plain").toPath();
        String id=notebook(folder,null);
        AtomicReference<Seen> first=watchFirstFrame();
        boolean[] shown={false},back={false};
        SwingUtilities.invokeLater(()->{shown[0]=DesktopLock.opening(Desktop.opener(folder,true));back[0]=true;});
        await(()->first.get()!=null&&back[0]);
        assertTrue(shown[0]);
        whole(first.get(),id,"01b-first-frame-at-start");
        close(first.get().pad);
    }

    /** Locked again for want of use, and opened: the window comes back whole, as at start. */
    @Test public void afterLockingAgainTheFirstFrameHasTheTreeAndTheNote() throws Exception {
        Path folder=temp.newFolder("again").toPath();
        Vault.Made made=Vault.make("password1".toCharArray(),20_000);
        String id=notebook(folder,made.key);
        Files.write(folder.resolve(DesktopLock.KEPT),made.kept);
        AtomicReference<Seen> first=watchFirstFrame();
        SwingUtilities.invokeLater(()->{try{DesktopLock.askAtStart(folder,false,Desktop.opener(folder,true));}catch(Exception e){throw new RuntimeException(e);}});
        unlock(box("Mininotes is locked"));
        await(()->first.get()!=null&&!showing());
        Desktop was=first.get().pad;was.leave=()->{};
        AtomicReference<Seen> again=watchFirstFrame();
        SwingUtilities.invokeLater(was::relock);
        unlock(box("Mininotes is locked"));
        await(()->again.get()!=null);
        assertNotSame("a new window",was,again.get().pad);
        Thread.sleep(300);
        whole(again.get(),id,"01c-first-frame-after-locking-again");
        close(again.get().pad);
    }

    /** With no lock, the box that says it is opening: the strip, and the words. */
    @Test public void theStartBoxSaysItIsOpening() throws Exception {
        SwingUtilities.invokeLater(()->DesktopLock.opening((key,shown,said,failed)->{}));
        JDialog box=box("Mininotes");
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(()->{assertTrue(find(box,"openingStrip").isVisible());assertTrue(text(box).contains(DesktopLock.OPENING));save(box,"00b-start-opening");box.dispose();});
    }

    /** Opening that cannot finish is said in the box, with why; no window comes up. */
    @Test public void openingThatFailsIsSaidInTheBox() throws Exception {
        Path folder=temp.newFolder("held").toPath();
        notebook(folder,null);
        try(FileChannel channel=FileChannel.open(folder.resolve("notebook.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock held=channel.tryLock()) {
            assertNotNull(held);
            SwingUtilities.invokeLater(()->DesktopLock.opening(Desktop.opener(folder,true)));
            JDialog box=box("Mininotes");
            await(()->{String[] said={""};SwingUtilities.invokeAndWait(()->said[0]=text(box));return said[0].contains("could not be opened")&&said[0].contains("already open");});
            Thread.sleep(300);
            SwingUtilities.invokeAndWait(()->{assertFalse("the strip stops",find(box,"openingStrip").isVisible());save(box,"00c-opening-failed");});
            Thread.sleep(300);
            for(Window w:Window.getWindows())assertFalse("no window came up",w instanceof JFrame&&w.isShowing());
            SwingUtilities.invokeAndWait(box::dispose);
        }
    }

    /** A note with a title, words, a file and a person it is shared with, in a second collection besides. */
    private static String notebook(Path folder,byte[] key) throws Exception {
        try(NoteStore store=new NoteStore(new Context(folder.toFile()))) {
            NoteStore.Note note=new NoteStore.Note();note.book=store.someBook();note.title="Saturday";note.body="Pick up fresh bread\nTea for the ferry";store.save(note);
            store.addCollection("Kitchen");
            store.pairedWith("MxFirst@127.0.0.1:9001","Ana's phone",false,Envelope.keys().getPublic().getEncoded(),Envelope.keys().getPublic().getEncoded());
            store.setLevel(Sharing.Scope.PAGE,note.id,"MxFirst@127.0.0.1:9001",Sharing.Level.WRITE,null);
            String file=UUID.randomUUID().toString();Files.writeString(store.fileFor(file).toPath(),"synthetic");
            store.keep(new NoteStore.Held(file,note.id,"tickets.txt","text/plain",9,System.currentTimeMillis()));
            if(key!=null)store.getWritableDatabase().rekey(key);
            return note.id;
        }
    }

    /** What the window held the moment it first showed. */
    private record Seen(Desktop pad,String title,String page,List<String> tree,int people,String standing,int tabs,java.awt.image.BufferedImage picture){}

    private static void whole(Seen seen,String id,String name) throws Exception {
        Files.createDirectories(SHOTS);javax.imageio.ImageIO.write(seen.picture,"png",SHOTS.resolve(name+".png").toFile());
        assertEquals("the note's title","Saturday",seen.title);
        assertTrue("the note's words",seen.page.contains("Tea for the ferry"));
        assertTrue("the tree: "+seen.tree,seen.tree.contains("Saturday")&&seen.tree.contains("Kitchen"));
        assertEquals("the person it is shared with",1,seen.people);
        assertFalse("where it is",seen.standing.isBlank());
        assertTrue("it is one of the things open, for the overview",seen.tabs>0);
        for(Window w:Window.getWindows())assertFalse("the box is gone",w instanceof JDialog&&w.isShowing());
    }

    /** The first Mininotes window to show, read as it shows - before anything after it is drawn. */
    private static AtomicReference<Seen> watchFirstFrame() {
        AtomicReference<Seen> first=new AtomicReference<>();
        Toolkit.getDefaultToolkit().addAWTEventListener(new java.awt.event.AWTEventListener(){public void eventDispatched(AWTEvent e){
            if(!(e instanceof HierarchyEvent h)||(h.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)==0||!(h.getComponent() instanceof JFrame frame)||!frame.isShowing()||first.get()!=null)return;
            Toolkit.getDefaultToolkit().removeAWTEventListener(this);
            try {
                java.lang.reflect.Field f=Desktop.class.getDeclaredField("current");f.setAccessible(true);Desktop pad=(Desktop)f.get(null);
                List<String> names=new ArrayList<>();walk((DefaultMutableTreeNode)pad.tree.getModel().getRoot(),names);
                var image=picture(frame);
                first.set(new Seen(pad,pad.title.getText(),pad.page.getText(),names,pad.peopleShown.size(),pad.standing.getText(),pad.overview.count(),image));
            } catch(Exception x){throw new RuntimeException(x);}
        }},AWTEvent.HIERARCHY_EVENT_MASK);
        return first;
    }
    private static void unlock(JDialog box) throws Exception {
        SwingUtilities.invokeAndWait(()->{((JPasswordField)find(box,"unlockPassword")).setText("password1");((JButton)find(box,"unlockOpen")).doClick();});
    }
    /** Drawn as the gallery draws: at the screen's own scale, with its text hints, so words are not cut short falsely. */
    private static java.awt.image.BufferedImage picture(Window window) {
        var scale=window.getGraphicsConfiguration().getDefaultTransform();
        var image=new java.awt.image.BufferedImage((int)Math.ceil(window.getWidth()*scale.getScaleX()),(int)Math.ceil(window.getHeight()*scale.getScaleY()),java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();g.transform(scale);
        Object hints=Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");if(hints instanceof Map<?,?> map)g.addRenderingHints(map);
        window.paint(g);g.dispose();
        return image;
    }
    private static void save(Window window,String name) {
        try{Files.createDirectories(SHOTS);javax.imageio.ImageIO.write(picture(window),"png",SHOTS.resolve(name+".png").toFile());}catch(Exception e){throw new RuntimeException(e);}
    }
    private static void walk(DefaultMutableTreeNode node,List<String> names) {
        Object o=node.getUserObject();names.add(o instanceof NoteStore.Branch b?b.name:String.valueOf(o));
        for(int i=0;i<node.getChildCount();i++)walk((DefaultMutableTreeNode)node.getChildAt(i),names);
    }
    private static void close(Desktop pad) throws Exception {
        SwingUtilities.invokeAndWait(()->pad.shutdown(false));
        await(()->!pad.frame.isDisplayable());
    }
    private static JDialog box(String title) throws Exception {
        JDialog[] box={null};
        await(()->{for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing()&&title.equals(d.getTitle()))box[0]=d;return box[0]!=null;});
        return box[0];
    }
    private static Component find(Container in,String name) {
        for(Component c:in.getComponents()){if(name.equals(c.getName()))return c;if(c instanceof Container k){Component f=find(k,name);if(f!=null)return f;}}
        return null;
    }
    private static String text(Container in) {
        StringBuilder all=new StringBuilder();
        for(Component c:in.getComponents()){if(c instanceof JLabel l)all.append(l.getText()).append('\n');else if(c instanceof javax.swing.text.JTextComponent t)all.append(t.getText()).append('\n');else if(c instanceof Container k)all.append(text(k));}
        return all.toString();
    }
    private static void await(java.util.concurrent.Callable<Boolean> condition) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        while(System.nanoTime()<until){if(condition.call())return;Thread.sleep(40);}fail("did not happen in time");
    }
}
