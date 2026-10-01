package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.swing.*;

/**
 * A picture pasted on a note is kept with it, as a messaging app takes one (the owner's ask, 2026-10-01: a Ctrl+V after a
 * screenshot from Greenshot). Pasted through the page's own handler with a picture made here, so the PC's real clipboard
 * is never touched; words with a picture of them stay words.
 */
public class DesktopPasteTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();

    @Test public void aPictureWithNoWordsIsKeptWithTheNote() throws Exception {
        Path folder=temp.newFolder("pad").toPath();Desktop[] app=new Desktop[1];
        SwingUtilities.invokeAndWait(()->{try{app[0]=new Desktop(folder,true);app[0].show();}catch(Exception e){throw new RuntimeException(e);}});
        Desktop pad=app[0];
        try {
            await(()->pad.page.isEditable()&&pad.store.latest()!=null);settle(pad);
            NoteStore.Note note=new NoteStore.Note();note.book=Things.HOME;note.title="Screens";note.body="Synthetic";pad.store.save(note);
            SwingUtilities.invokeAndWait(()->pad.open(note.id));settle(pad);
            assertEquals(0,pad.store.filesOf(NoteStore.Branch.Kind.PAGE,note.id).size());

            // A screenshot, as Greenshot puts one on the clipboard: a picture and nothing else.
            BufferedImage shot=new BufferedImage(120,80,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=shot.createGraphics();g.setColor(Color.ORANGE);g.fillRect(0,0,120,80);g.setColor(Color.BLUE);g.fillRect(10,10,40,30);g.dispose();
            boolean[] took={false};
            SwingUtilities.invokeAndWait(()->took[0]=pad.page.getTransferHandler().importData(new TransferHandler.TransferSupport(pad.page,new Clip(shot,null))));
            assertTrue("the paste is taken",took[0]);
            settle(pad);
            List<NoteStore.Held> kept=pad.store.filesOf(NoteStore.Branch.Kind.PAGE,note.id);
            assertEquals(1,kept.size());
            assertTrue(kept.get(0).name,kept.get(0).name.matches("Picture \\d{1,2} [A-Z][a-z]{2}, \\d\\d:\\d\\d\\.png"));
            assertEquals("image/png",kept.get(0).kind);
            BufferedImage back=javax.imageio.ImageIO.read(pad.store.fileFor(kept.get(0).id));
            assertNotNull("kept as a PNG that reads back",back);
            assertEquals(120,back.getWidth());assertEquals(Color.BLUE.getRGB(),back.getRGB(20,20));
            SwingUtilities.invokeAndWait(()->assertEquals(kept.get(0).name+" added",pad.status.getText()));
            String before=onEdt(()->pad.page.getText());

            // A table copied from a spreadsheet brings its words and a picture of them: the words are pasted, nothing kept.
            SwingUtilities.invokeAndWait(()->{pad.page.setCaretPosition(pad.page.getDocument().getLength());
                pad.page.getTransferHandler().importData(new TransferHandler.TransferSupport(pad.page,new Clip(shot,"Rent\t500")));});
            settle(pad);
            assertEquals("still one file",1,pad.store.filesOf(NoteStore.Branch.Kind.PAGE,note.id).size());
            assertTrue("the words went in",onEdt(()->pad.page.getText()).contains("Rent"));
            assertNotEquals(before,onEdt(()->pad.page.getText()));
        } finally {SwingUtilities.invokeAndWait(()->pad.shutdown(false));await(()->!pad.frame.isDisplayable());}
    }

    /** What a clipboard would hold: a picture, and words or none. */
    private static final class Clip implements Transferable {
        private final Image picture;private final String words;
        Clip(Image picture,String words){this.picture=picture;this.words=words;}
        public DataFlavor[] getTransferDataFlavors(){return words==null?new DataFlavor[]{DataFlavor.imageFlavor}:new DataFlavor[]{DataFlavor.imageFlavor,DataFlavor.stringFlavor};}
        public boolean isDataFlavorSupported(DataFlavor flavor){for(DataFlavor one:getTransferDataFlavors())if(one.equals(flavor))return true;return false;}
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if(DataFlavor.imageFlavor.equals(flavor))return picture;
            if(words!=null&&DataFlavor.stringFlavor.equals(flavor))return words;
            throw new UnsupportedFlavorException(flavor);
        }
    }

    private static void settle(Desktop pad) throws Exception {
        for(int i=0;i<3;i++){pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});}
        Thread.sleep(250);SwingUtilities.invokeAndWait(pad.frame::validate);
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
