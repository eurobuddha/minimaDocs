package org.mininotes.android;

import static org.junit.Assert.*;
import java.nio.file.Path;
import javax.swing.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mininotes.desktop.platform.content.Context;

/**
 * A note open on the PC while the phones write in it: the page follows what the notebook takes in. Seen on 0.1.033 -
 * the PC's store at revision 76, its open page still showing the text from before, with the amber mark over it - where
 * a word typed on that page would have put the old lines back and sent them. Synthetic notes only; nothing is sent.
 */
public class DesktopPageFollowsTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final String PHONE="MxPhoneFixture@127.0.0.1:9301";
    private static final String OLD="still not?\nsecond line", NEW="good?\nwhat about now?\nsecond line";
    private Desktop pad;
    private String book;

    /** The notebook as the PC had it: "Transfer" at revision 74, taken from the phone, shared back with it; and a second note. */
    private void start() throws Exception {
        Path folder=temp.newFolder("pc").toPath();
        try(NoteStore store=new NoteStore(new Context(folder.toFile()))) {
            book=store.someBook();
            NoteStore.Note other=new NoteStore.Note();other.id="m";other.book=book;other.title="Groceries";other.body="Milk";store.save(other);
            NoteStore.Note note=new NoteStore.Note();note.id="n";note.book=book;note.title="Transfer";note.body=OLD;note.revision=74;store.save(note);
            store.pairedWith(PHONE,"Test phone",true,new byte[]{1,2,3},new byte[]{4,5,6});
            store.setLevel(Sharing.Scope.PAGE,"n",PHONE,Sharing.Level.WRITE,null);
            // The phone and the PC agree at 74.
            store.landed("n",PHONE,74,"Transfer",OLD,book,74L);
        }
        Desktop[] app=new Desktop[1];
        SwingUtilities.invokeAndWait(()->{try{app[0]=new Desktop(folder,true);app[0].show();}catch(Exception e){throw new RuntimeException(e);}});
        pad=app[0];
        await(()->pad.store.latest()!=null);settle();
        SwingUtilities.invokeAndWait(()->pad.open("n"));
        await(()->onEdt(()->pad.page.isEditable()&&pad.page.getText().equals(OLD)));settle();
    }
    @After public void stop() throws Exception {
        if(pad!=null&&pad.frame.isDisplayable()){SwingUtilities.invokeAndWait(()->pad.shutdown(false));await(()->!pad.frame.isDisplayable());}
    }

    /** Revision 75 from the phone, written into the notebook the way Post.arrived writes it - the page not told yet. */
    private void landQuietly(String body) throws Exception {
        Arriving.What[] what={null};
        pad.disk.submit(()->pad.store.landed("n",PHONE,75,"Transfer",body,book,74L).what,said->what[0]=said,e->{throw new AssertionError(e);});
        await(()->what[0]!=null);
        assertEquals(Arriving.What.NEWER,what[0]);
    }
    private void told() throws Exception {SwingUtilities.invokeAndWait(()->pad.arrived(new Post.Landed("Test phone updated a note.",null,"n")));}

    @Test public void anUntouchedPageBecomesWhatArrivedAndNothingIsWritten() throws Exception {
        start();
        landQuietly(NEW);told();
        await(()->onEdt(()->pad.page.getText().equals(NEW)));settle();
        assertEquals(NEW,pad.store.get("n").body);
        assertEquals("the page never became a revision of the PC's own",75,pad.store.get("n").revision);
        assertFalse("nothing typed",onEdt(pad::typedHere));
        assertNotEquals("no amber for a page that only followed",SyncMark.WAITING,onEdt(()->pad.noteMark));
        // And Ctrl+Z has nothing that could put the old text back.
        assertFalse(onEdt(()->((javax.swing.undo.UndoManager)pad.page.getClientProperty("undo")).canUndo()));
    }

    /** Something that touched the words without writing any - a key typed and taken back - is not writing either. */
    @Test public void aKeyTypedAndTakenBackIsNotWriting() throws Exception {
        start();
        SwingUtilities.invokeAndWait(()->{try{pad.page.getDocument().insertString(0,"x",null);pad.page.getDocument().remove(0,1);}catch(Exception e){throw new RuntimeException(e);}});
        assertFalse("the page says what it was opened with",onEdt(pad::typedHere));
        landQuietly(NEW);told();
        await(()->onEdt(()->pad.page.getText().equals(NEW)));settle();
        assertEquals(NEW,pad.store.get("n").body);assertEquals(75,pad.store.get("n").revision);
        assertNotEquals(SyncMark.WAITING,onEdt(()->pad.noteMark));
    }

    /** Typed on the page as the phone's writing came in: both are kept, and the old lines do not come back. */
    @Test public void whatWasTypedIsPutWithWhatArrived() throws Exception {
        start();
        landQuietly(NEW);
        SwingUtilities.invokeAndWait(()->{
            try{pad.page.getDocument().insertString(pad.page.getDocument().getLength(),"\nthird line",null);}catch(Exception e){throw new RuntimeException(e);}
            pad.arrived(new Post.Landed("Test phone updated a note.",null,"n"));
        });
        String both=NEW+"\nthird line";
        await(()->both.equals(pad.store.get("n").body));settle();
        assertEquals(both,onEdt(pad.page::getText));
        assertFalse("the old line is not back",pad.store.get("n").body.contains("still not?"));
        assertEquals("one revision of the PC's own, for its typing",76,pad.store.get("n").revision);
        assertFalse(onEdt(pad::typedHere));
    }

    /**
     * Typing the notebook will not take - here, past what the phone's backup format holds - left the page on its older
     * text: each arrival waited behind a save that failed again. Now what arrived is put with the typing all the same.
     */
    @Test public void aPageThatCannotBeWrittenDownStillFollows() throws Exception {
        start();
        String tooLong="\n"+"x".repeat(24_001);
        SwingUtilities.invokeAndWait(()->{try{pad.page.getDocument().insertString(pad.page.getDocument().getLength(),tooLong,null);}catch(Exception e){throw new RuntimeException(e);}});
        await(()->onEdt(()->pad.noteWords.getText().startsWith("Not saved")));
        landQuietly(NEW);told();
        await(()->onEdt(()->pad.page.getText().startsWith("good?\nwhat about now?")));settle();
        assertFalse("the old line is not back on the page",onEdt(pad.page::getText).contains("still not?"));
        assertTrue("and the typing is still there",onEdt(pad.page::getText).endsWith(tooLong));
        assertEquals("nothing of it written",NEW,pad.store.get("n").body);
        assertTrue("still unsaved, and said so",onEdt(pad::typedHere));
    }

    /** A save asked for on a page showing old text nobody typed - the tab closing, Ctrl+S - writes nothing. */
    @Test public void savingAStalePageNobodyTypedOnWritesNothing() throws Exception {
        start();
        landQuietly(NEW);
        SwingUtilities.invokeAndWait(()->pad.save(null));settle();
        assertEquals(NEW,pad.store.get("n").body);assertEquals(75,pad.store.get("n").revision);
        // Told afterwards, it follows as ever.
        told();
        await(()->onEdt(()->pad.page.getText().equals(NEW)));
    }

    /** Arrived while another note was on show: that note shows the notebook's text when it is chosen again in the overview. */
    @Test public void anotherTabShowsWhatArrivedWhenItIsChosen() throws Exception {
        start();
        SwingUtilities.invokeAndWait(()->pad.open("m"));
        await(()->onEdt(()->pad.page.getText().equals("Milk")));settle();
        landQuietly(NEW);told();settle();
        assertEquals("the page on show is left alone","Milk",onEdt(pad.page::getText));
        SwingUtilities.invokeAndWait(()->{for(Overview.Open one:pad.overview.all())if("n".equals(one.id))pad.goTo(one);});
        await(()->onEdt(()->pad.page.getText().equals(NEW)));settle();
        assertEquals(75,pad.store.get("n").revision);assertEquals("Milk",pad.store.get("m").body);
    }

    private void settle() throws Exception {
        for(int i=0;i<3;i++){pad.disk.flush(10000);SwingUtilities.invokeAndWait(()->{});}
    }
    private static <T> T onEdt(java.util.concurrent.Callable<T> read) throws Exception {
        Object[] got={null};Exception[] failed={null};
        SwingUtilities.invokeAndWait(()->{try{got[0]=read.call();}catch(Exception e){failed[0]=e;}});
        if(failed[0]!=null)throw failed[0];
        @SuppressWarnings("unchecked") T value=(T)got[0];return value;
    }
    private static void await(java.util.concurrent.Callable<Boolean> condition) throws Exception {
        long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        while(System.nanoTime()<until){if(condition.call())return;Thread.sleep(40);}fail("Desktop did not finish in time");
    }
}
