package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.nio.file.*;
import javax.swing.*;

public class DesktopUiTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();

    /**
     * Painting a text with an emoji changes nothing about it. It used to set its background for the emoji
     * and back again, and each setting asked for another paint: the window redrew itself without end,
     * using the processor and too busy to hear its close button.
     */
    @Test public void paintingEmojiDoesNotAskForAnotherPaint() throws Exception {
        if(java.awt.GraphicsEnvironment.isHeadless())return;
        int[] changes={0};
        SwingUtilities.invokeAndWait(()->{
            DesktopUi.install();
            DesktopUi.Text text=DesktopUi.note("Bread 🥖 and tea ☕",300,DesktopUi.INK,DesktopUi.BODY);
            JPanel card=DesktopUi.card(null,text);card.setSize(360,80);card.doLayout();text.setSize(text.getPreferredSize());
            text.addPropertyChangeListener(e->{if(!"ancestor".equals(e.getPropertyName()))changes[0]++;});
            java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(360,80,java.awt.image.BufferedImage.TYPE_INT_RGB);
            for(int i=0;i<3;i++){java.awt.Graphics2D g=image.createGraphics();card.paint(g);g.dispose();}
        });
        assertEquals("nothing about the text changes while it is painted",0,changes[0]);
    }

    @Test public void typeAutosaveReopenAndRender() throws Exception {
        Path folder=temp.newFolder("ui-pad").toPath();Desktop[] app=new Desktop[1];
        SwingUtilities.invokeAndWait(()->{try{app[0]=new Desktop(folder,true);app[0].show();}catch(Exception e){throw new RuntimeException(e);}});
        Desktop pad=app[0];
        try {
            await(()->pad.page.isEditable()&&pad.store.latest()!=null);
            // Drain the startup callbacks before typing through the real documents.
            pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});
            SwingUtilities.invokeAndWait(()->{pad.title.setText("Saturday");pad.page.setText("Pick up fresh bread\nTea for the ferry\n\nLeave room for a little wandering.");});
            await(()->pad.store.latest().body.contains("wandering"));
            // One line under the title once the save settles - the phone's mark, then a round for each person it
            // reaches - said by being seen: no words, and no box under the pointer. A note nobody has: the empty ring.
            await(()->pad.noteMark!=null);
            assertEquals(SyncMark.HERE,pad.noteMark);assertTrue(pad.syncMark.isVisible());assertNull(pad.syncMark.getToolTipText());
            assertEquals(0,pad.rounds.getComponentCount());assertTrue(pad.peopleShown.isEmpty());
            assertEquals(SyncMark.HERE.said("this PC"),pad.syncMark.getAccessibleContext().getAccessibleName());
            SwingUtilities.invokeAndWait(()->{
                try {
                    var image=new java.awt.image.BufferedImage(pad.frame.getWidth(),pad.frame.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
                    var g=image.createGraphics();pad.frame.paint(g);g.dispose();
                    Path shot=Path.of("build","verification","windows-pad.png");Files.createDirectories(shot.getParent());javax.imageio.ImageIO.write(image,"png",shot.toFile());
                }catch(Exception e){throw new RuntimeException(e);}
            });
            assertTrue(Files.isRegularFile(Path.of("build","verification","windows-pad.png")));
            java.util.concurrent.CountDownLatch shared=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<Throwable> shareFailure=new java.util.concurrent.atomic.AtomicReference<>();
            SwingUtilities.invokeLater(()->{
                javax.swing.Timer capture=new javax.swing.Timer(100,event->{
                    for(java.awt.Window window:pad.frame.getOwnedWindows())if(window instanceof JDialog dialog&&dialog.isVisible()) {
                        ((javax.swing.Timer)event.getSource()).stop();
                        try {
                            var image=new java.awt.image.BufferedImage(dialog.getWidth(),dialog.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
                            var g=image.createGraphics();dialog.paint(g);g.dispose();
                            javax.imageio.ImageIO.write(image,"png",Path.of("build","verification","windows-sharing.png").toFile());
                        }catch(Throwable error){shareFailure.set(error);}finally{dialog.dispose();shared.countDown();}
                    }
                });capture.start();findButton(pad.frame,"Share").doClick();
            });
            assertTrue("Sharing dialog did not open",shared.await(30,java.util.concurrent.TimeUnit.SECONDS));
            if(shareFailure.get()!=null)throw new AssertionError(shareFailure.get());
            String id=pad.store.latest().id;pad.disk.flush(10000);
            SwingUtilities.invokeAndWait(()->pad.shutdown(false));await(()->!pad.frame.isDisplayable());
            try(NoteStore reopened=new NoteStore(new org.mininotes.desktop.platform.content.Context(folder.toFile()))){assertEquals("Saturday",reopened.get(id).title);assertTrue(reopened.get(id).body.contains("wandering"));}
        } finally {if(pad.frame.isDisplayable()){SwingUtilities.invokeAndWait(()->pad.shutdown(false));await(()->!pad.frame.isDisplayable());}}
    }
    private static JButton findButton(java.awt.Container parent,String text) {
        for(java.awt.Component child:parent.getComponents()) {
            if(child instanceof JButton button&&text.equals(button.getText()))return button;
            if(child instanceof java.awt.Container container){JButton found=findButton(container,text);if(found!=null)return found;}
        }return null;
    }
    private static void await(java.util.concurrent.Callable<Boolean> condition) throws Exception {
        long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        while(System.nanoTime()<until){if(condition.call())return;Thread.sleep(40);}fail("Desktop did not finish in time");
    }
}
