package org.mininotes.android;

import static org.junit.Assert.*;
import java.awt.Window;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mininotes.desktop.platform.content.Context;

/** A locked notebook, locked again: the key let go, the notebook closed, the password asked for. Synthetic data. */
public class DesktopRelockTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void lockingAgainClosesTheNotebookAndAsksForThePassword() throws Exception {
        Path folder=temp.newFolder("relock").toPath();
        Vault.Made made=Vault.make("password1".toCharArray(),20_000);
        try(NoteStore store=new NoteStore(new Context(folder.toFile()))) {
            NoteStore.Note note=new NoteStore.Note();note.book=store.someBook();note.body="Synthetic line";store.save(note);
            store.getWritableDatabase().rekey(made.key);
        }
        Files.write(folder.resolve(DesktopLock.KEPT),made.kept);
        Desktop[] app=new Desktop[1];
        SwingUtilities.invokeAndWait(()->{try{app[0]=new Desktop(folder,true,made.key);app[0].leave=()->{};app[0].show();}catch(Exception e){throw new RuntimeException(e);}});
        Desktop pad=app[0];
        await(()->pad.store.latest()!=null&&pad.page.getText().contains("Synthetic"));
        SwingUtilities.invokeLater(pad::relock);
        JDialog[] asked={null};
        await(()->{for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing()&&"Mininotes is locked".equals(d.getTitle()))asked[0]=d;return asked[0]!=null;});
        assertFalse("the old window is gone",pad.frame.isDisplayable());
        assertNull("the key has left memory",pad.context.databaseKey());
        SwingUtilities.invokeAndWait(()->asked[0].dispose());
    }
    /**
     * What was seen after locking again: a Hello question that never came back left the button greyed for good.
     * Here the first question never answers, and the button still asks again; one cancelled says so and stays pressable.
     */
    @Test public void theHelloButtonAnswersEveryPressAfterLockingAgain() throws Exception {
        Path folder=temp.newFolder("hello").toPath();
        Vault.Made made=Vault.make("password1".toCharArray(),20_000);
        Files.write(folder.resolve(DesktopLock.KEPT),made.kept);Files.write(folder.resolve(DesktopHello.FILE),new byte[]{1});
        java.util.concurrent.atomic.AtomicInteger asked=new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch givenUp=new java.util.concurrent.CountDownLatch(1);
        DesktopLock.HelloAsk was=DesktopLock.helloAsk;
        DesktopLock.helloAsk=f->{
            int n=asked.incrementAndGet();
            if(n==1){try{Thread.sleep(Long.MAX_VALUE);}catch(InterruptedException e){givenUp.countDown();throw e;}}
            if(n==2)throw new java.io.IOException("Windows Hello was cancelled.");
            return made.key;
        };
        try {
            byte[][] key={null};
            SwingUtilities.invokeLater(()->{try{key[0]=DesktopLock.askAtStart(folder,false);}catch(Exception e){throw new RuntimeException(e);}});
            JDialog[] box={null};
            await(()->{for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing()&&"Mininotes is locked".equals(d.getTitle()))box[0]=d;return box[0]!=null;});
            JButton hello=(JButton)find(box[0],"unlockHello");
            Thread.sleep(300);
            // Hello is the way in, so it is the button Enter presses; the backup password waits until it is chosen.
            assertSame(hello,box[0].getRootPane().getDefaultButton());
            assertFalse("the password is an alternative",find(box[0],"unlockPassword").isShowing());
            assertEquals("not asked with nobody there",0,asked.get());
            SwingUtilities.invokeAndWait(hello::doClick);
            await(()->asked.get()==1);
            assertTrue(hello.isEnabled());
            SwingUtilities.invokeAndWait(hello::doClick);
            assertTrue("the waiting question is given up",givenUp.await(5,TimeUnit.SECONDS));
            await(()->{String[] said={""};SwingUtilities.invokeAndWait(()->said[0]=text(box[0]));return said[0].contains("was cancelled");});
            assertTrue("still pressable after a cancel",hello.isEnabled());
            SwingUtilities.invokeAndWait(hello::doClick);
            await(()->!box[0].isDisplayable()&&key[0]!=null);
            assertArrayEquals(made.key,key[0]);
        } finally{DesktopLock.helloAsk=was;}
    }
    /** With Hello set up, the backup password is one press away: chosen, it shows, takes Enter, and opens the notebook. */
    @Test public void theBackupPasswordIsAnAlternativeToHello() throws Exception {
        Path folder=temp.newFolder("backup").toPath();
        Vault.Made made=Vault.make("password1".toCharArray(),20_000);
        Files.write(folder.resolve(DesktopLock.KEPT),made.kept);Files.write(folder.resolve(DesktopHello.FILE),new byte[]{1});
        DesktopLock.HelloAsk was=DesktopLock.helloAsk;
        DesktopLock.helloAsk=f->{throw new AssertionError("Hello asked with nobody pressing");};
        try {
            byte[][] key={null};
            SwingUtilities.invokeLater(()->{try{key[0]=DesktopLock.askAtStart(folder,false);}catch(Exception e){throw new RuntimeException(e);}});
            JDialog[] box={null};
            await(()->{for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing()&&"Mininotes is locked".equals(d.getTitle()))box[0]=d;return box[0]!=null;});
            JPasswordField password=(JPasswordField)find(box[0],"unlockPassword");JButton open=(JButton)find(box[0],"unlockOpen");
            assertFalse(password.isShowing());
            SwingUtilities.invokeAndWait(((JButton)find(box[0],"unlockBackup"))::doClick);
            await(()->password.isShowing());
            assertSame("Enter now unlocks with the password",open,box[0].getRootPane().getDefaultButton());
            SwingUtilities.invokeAndWait(()->{password.setText("wrong one");open.doClick();});
            await(()->{String[] said={""};SwingUtilities.invokeAndWait(()->said[0]=text(box[0]));return said[0].contains("did not open it");});
            SwingUtilities.invokeAndWait(()->{password.setText("password1");open.doClick();});
            await(()->!box[0].isDisplayable()&&key[0]!=null);
            assertArrayEquals(made.key,key[0]);
        } finally{DesktopLock.helloAsk=was;}
    }
    /**
     * Locked again with the window showing, nobody at it: the box that replaces the window asks Windows for
     * nothing - no front, no Hello - even where Windows would let it. Asking from behind flashed the taskbar.
     */
    @Test public void lockingAgainTakesNoFrontAndAsksNoHello() throws Exception {
        Path folder=temp.newFolder("quiet").toPath();
        Vault.Made made=Vault.make("password1".toCharArray(),20_000);
        try(NoteStore store=new NoteStore(new Context(folder.toFile()))) {
            NoteStore.Note note=new NoteStore.Note();note.book=store.someBook();note.body="Synthetic line";store.save(note);
            store.getWritableDatabase().rekey(made.key);
        }
        Files.write(folder.resolve(DesktopLock.KEPT),made.kept);Files.write(folder.resolve(DesktopHello.FILE),new byte[]{1});
        DesktopLock.HelloAsk was=DesktopLock.helloAsk;DesktopLock.Front wasFront=DesktopLock.front;
        java.util.concurrent.atomic.AtomicInteger asked=new java.util.concurrent.atomic.AtomicInteger();
        DesktopLock.helloAsk=f->{asked.incrementAndGet();return made.key;};
        DesktopLock.front=front(true,true);
        try {
            Desktop[] app=new Desktop[1];
            SwingUtilities.invokeAndWait(()->{try{app[0]=new Desktop(folder,true,made.key);app[0].leave=()->{};app[0].show();}catch(Exception e){throw new RuntimeException(e);}});
            Desktop pad=app[0];
            await(()->pad.store.latest()!=null&&pad.page.getText().contains("Synthetic"));
            SwingUtilities.invokeLater(pad::relock);
            JDialog box=lockedBox();
            Thread.sleep(500);
            assertFalse("shown without asking for the front",box.isAutoRequestFocus());
            assertEquals("Hello not asked with nobody pressing",0,asked.get());
            assertFalse("the old window is gone",pad.frame.isDisplayable());
            SwingUtilities.invokeAndWait(box::dispose);
        } finally{DesktopLock.helloAsk=was;DesktopLock.front=wasFront;}
    }
    /** Started by the person and given the front: Hello is asked by itself, once, and opens it. */
    @Test public void startedByThePersonHelloIsAskedByItself() throws Exception {
        Vault.Made made=Vault.make("password1".toCharArray(),20_000);
        java.util.concurrent.atomic.AtomicInteger asked=new java.util.concurrent.atomic.AtomicInteger();
        byte[] key=atStart(made,front(true,true),f->{asked.incrementAndGet();return made.key;},box->{});
        assertArrayEquals(made.key,key);
        assertEquals(1,asked.get());
    }
    /**
     * Started without the front - opened again by an update, or handed over while the person was elsewhere:
     * the box comes up quietly and waits for a press; Hello is asked only then. And one that asked for the
     * front but did not get it asks nothing by itself either.
     */
    @Test public void startedWithoutTheFrontTheBoxWaitsQuietly() throws Exception {
        Vault.Made made=Vault.make("password1".toCharArray(),20_000);
        for(DesktopLock.Front front:new DesktopLock.Front[]{front(false,false),front(true,false)}) {
            java.util.concurrent.atomic.AtomicInteger asked=new java.util.concurrent.atomic.AtomicInteger();
            byte[] key=atStart(made,front,f->{asked.incrementAndGet();return made.key;},box->{
                try {
                    Thread.sleep(600);
                    assertEquals("nothing asked by itself",0,asked.get());
                    assertEquals("quiet only where Windows would not give the front",!front.mayTake(),!box.isAutoRequestFocus());
                    SwingUtilities.invokeAndWait(((JButton)find(box,"unlockHello"))::doClick);
                } catch(Exception e){throw new RuntimeException(e);}
            });
            assertArrayEquals(made.key,key);
            assertEquals("asked once, when pressed",1,asked.get());
        }
    }
    /** askAtStart as a start calls it, with Hello set up; {@code meanwhile} runs once the box shows. */
    private byte[] atStart(Vault.Made made,DesktopLock.Front front,DesktopLock.HelloAsk hello,java.util.function.Consumer<JDialog> meanwhile) throws Exception {
        Path folder=temp.newFolder().toPath();
        Files.write(folder.resolve(DesktopLock.KEPT),made.kept);Files.write(folder.resolve(DesktopHello.FILE),new byte[]{1});
        DesktopLock.HelloAsk was=DesktopLock.helloAsk;DesktopLock.Front wasFront=DesktopLock.front;
        DesktopLock.helloAsk=hello;DesktopLock.front=front;
        try {
            byte[][] key={null};java.util.concurrent.atomic.AtomicBoolean back=new java.util.concurrent.atomic.AtomicBoolean();
            SwingUtilities.invokeLater(()->{try{key[0]=DesktopLock.askAtStart(folder,true);}catch(Exception e){throw new RuntimeException(e);}finally{back.set(true);}});
            JDialog box=null;
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(box==null&&!back.get()&&System.nanoTime()<until){for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing()&&"Mininotes is locked".equals(d.getTitle()))box=d;Thread.sleep(40);}
            if(box!=null&&!back.get())meanwhile.accept(box);
            await(back::get);
            return key[0];
        } finally{DesktopLock.helloAsk=was;DesktopLock.front=wasFront;}
    }
    private static DesktopLock.Front front(boolean mayTake,boolean holds) {
        return new DesktopLock.Front(){public boolean mayTake(){return mayTake;}public boolean holds(){return holds;}};
    }
    private static JDialog lockedBox() throws Exception {
        JDialog[] box={null};
        await(()->{for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing()&&"Mininotes is locked".equals(d.getTitle()))box[0]=d;return box[0]!=null;});
        return box[0];
    }
    private static java.awt.Component find(java.awt.Container in,String name) {
        for(java.awt.Component c:in.getComponents()){if(name.equals(c.getName()))return c;if(c instanceof java.awt.Container k){java.awt.Component f=find(k,name);if(f!=null)return f;}}
        return null;
    }
    private static String text(java.awt.Container in) {
        StringBuilder all=new StringBuilder();
        for(java.awt.Component c:in.getComponents()){if(c instanceof JLabel l)all.append(l.getText()).append('\n');else if(c instanceof javax.swing.text.JTextComponent t)all.append(t.getText()).append('\n');else if(c instanceof java.awt.Container k)all.append(text(k));}
        return all.toString();
    }
    private static void await(java.util.concurrent.Callable<Boolean> condition) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(System.nanoTime()<until){if(condition.call())return;Thread.sleep(40);}fail("did not happen in time");
    }
}
