package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Files from Explorer let go on a note are attached to it: on the open note's page, through the page's own
 * handler, and on another note's line in the tree, found where the window finds it. A read-only note takes none.
 */
public class DesktopDropOnNoteTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();

    /** Files as Explorer hands them over. */
    private static Transferable files(List<Path> paths) {
        return new Transferable() {
            public DataFlavor[] getTransferDataFlavors(){return new DataFlavor[]{DataFlavor.javaFileListFlavor};}
            public boolean isDataFlavorSupported(DataFlavor f){return DataFlavor.javaFileListFlavor.equals(f);}
            public Object getTransferData(DataFlavor f){return paths.stream().map(Path::toFile).toList();}
        };
    }

    @Test public void droppedOnThePageOrOnANoteInTheTreeTheyAreAttachedToThatNote() throws Exception {
        Path folder=temp.newFolder("pad").toPath();Desktop[] app=new Desktop[1];
        SwingUtilities.invokeAndWait(()->{try{app[0]=new Desktop(folder,true);app[0].show();}catch(Exception e){throw new RuntimeException(e);}});
        Desktop pad=app[0];
        try {
            await(()->pad.page.isEditable()&&pad.store.latest()!=null);pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});
            NoteStore store=pad.store;
            SwingUtilities.invokeAndWait(()->{pad.title.setText("Saturday");pad.page.setText("Bread");});
            await(()->"Saturday".equals(store.latest().title));
            String open=store.latest().id;
            NoteStore.Note other=new NoteStore.Note();other.book=store.get(open).book;other.title="Groceries";other.body="Milk";store.save(other);
            pad.disk.flush(10000);SwingUtilities.invokeAndWait(pad::refresh);pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});
            Path ticket=temp.newFile("ticket.pdf").toPath();Files.write(ticket,new byte[1200]);
            Path map=temp.newFile("map.png").toPath();Files.write(map,new byte[800]);

            // On the page: its own handler takes them, for the open note.
            SwingUtilities.invokeAndWait(()->{
                assertEquals(DesktopDrops.ATTACH,DesktopDrops.aim(pad,pad.page,new Point(20,20)).does());
                assertTrue(pad.page.getTransferHandler().importData(new TransferHandler.TransferSupport(pad.page,files(List.of(ticket)))));
            });
            await(()->store.filesOf(NoteStore.Branch.Kind.PAGE,open).size()==1);
            assertEquals("ticket.pdf",store.filesOf(NoteStore.Branch.Kind.PAGE,open).get(0).name);
            await(()->pad.status.getText().equals("ticket.pdf added"));

            // On Groceries' line in the tree, as the window finds it: attached to Groceries, and said with its name. The
            // tree is a panel shown when asked for (docs/HOME.md, decision 26), so it is asked for first.
            DesktopDrops.Aim[] aim={null};
            SwingUtilities.invokeAndWait(()->{pad.showTree(true);pad.frame.validate();});
            SwingUtilities.invokeAndWait(()->{
                for(int row=0;row<pad.tree.getRowCount();row++)pad.tree.expandRow(row);
                for(int row=0;row<pad.tree.getRowCount();row++) {
                    Object node=((DefaultMutableTreeNode)pad.tree.getPathForRow(row).getLastPathComponent()).getUserObject();
                    if(node instanceof Desktop.Item item&&item.branch().id.equals(other.id)) {
                        Rectangle r=pad.tree.getRowBounds(row);
                        Point onFrame=SwingUtilities.convertPoint(pad.tree,new Point(r.x+10,r.y+r.height/2),pad.frame);
                        aim[0]=DesktopDrops.aim(pad,pad.frame,onFrame);
                        // Held there: its line lit and the bar saying so; let go, the bar as it was.
                        DesktopDrops.over(pad,aim[0]);assertEquals("Drop to attach to “Groceries”",pad.status.getText());DesktopDrops.away(pad);
                    }
                }
            });
            assertNotNull("Groceries in the tree",aim[0]);
            assertEquals(DesktopDrops.ATTACH,aim[0].does());assertEquals(other.id,aim[0].note());
            SwingUtilities.invokeAndWait(()->DesktopDrops.dropped(pad,aim[0],List.of(map)));
            await(()->store.filesOf(NoteStore.Branch.Kind.PAGE,other.id).size()==1);
            await(()->pad.status.getText().equals("map.png added to “Groceries”"));
            assertEquals("the open note has only its own",1,store.filesOf(NoteStore.Branch.Kind.PAGE,open).size());

            // The open note read only here: refused, and said.
            SwingUtilities.invokeAndWait(()->{pad.page.setEditable(false);DesktopDrops.dropped(pad,DesktopDrops.aim(pad,pad.page,null),List.of(map));});
            pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});
            assertEquals("This note is read only here",pad.status.getText());
            assertEquals(1,store.filesOf(NoteStore.Branch.Kind.PAGE,open).size());
        } finally {SwingUtilities.invokeAndWait(()->pad.shutdown(false));await(()->!pad.frame.isDisplayable());}
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(System.nanoTime()<until){if(condition.call())return;Thread.sleep(40);}fail("Desktop did not finish in time");
    }
}
