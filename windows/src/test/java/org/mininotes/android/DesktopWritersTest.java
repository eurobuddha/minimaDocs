package org.mininotes.android;

import static org.junit.Assert.*;
import java.nio.file.Path;
import javax.swing.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mininotes.desktop.platform.content.Context;

/**
 * Who wrote what, in the real notebook and on the PC's page: the letters typed here are yours, only what is new in an
 * arrival is the sender's, your own devices are you, text put back from a version is nobody's, and runs never outlive
 * the text they were about. And the page, when the note changes under it, keeps the reader where they were. Synthetic
 * notes and devices only; nothing is sent.
 */
public class DesktopWritersTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final String ANA="MxAnaFixture@127.0.0.1:9401", LAPTOP="MxLaptopFixture@127.0.0.1:9402";

    private NoteStore store() throws Exception {
        NoteStore store=new NoteStore(new Context(temp.newFolder()));store.getWritableDatabase();
        store.pairedWith(ANA,"Ana's phone",false,Envelope.keys().getPublic().getEncoded(),Envelope.keys().getPublic().getEncoded());
        store.pairedWith(LAPTOP,"Work laptop",true,Envelope.keys().getPublic().getEncoded(),Envelope.keys().getPublic().getEncoded());
        return store;
    }
    private static NoteStore.Note typed(NoteStore store,String body) {
        NoteStore.Note note=new NoteStore.Note();note.book=store.someBook();note.body=body;note.revision=1;
        note.writers=Writers.of(Writers.ME,body.length());store.save(note);
        return note;
    }
    private static String who(NoteStore store,Writers runs) {
        String ana=store.writerOf(ANA);
        StringBuilder out=new StringBuilder();
        for(int i=0;i<runs.count();i++)for(int n=0;n<runs.lengthOf(i);n++) {
            String by=runs.writerOf(i);
            out.append(by.equals(Writers.ME)?'m':by.equals(ana)?'A':by.isEmpty()?'.':'?');
        }
        return out.toString();
    }

    @Test public void whatIsTypedHereIsKeptAsYours() throws Exception {
        try(NoteStore store=store()) {
            NoteStore.Note note=typed(store,"Milk\n");
            assertEquals("mmmmm",who(store,store.writersOf(note.id,"Milk\n")));
        }
    }

    @Test public void anArrivalCreditsOnlyWhatIsNewHereToItsSender() throws Exception {
        try(NoteStore store=store()) {
            NoteStore.Note note=typed(store,"Milk\n");
            store.setLevel(Sharing.Scope.PAGE,note.id,ANA,Sharing.Level.WRITE,null);
            store.agreedOn(ANA,note.id,1);store.keepVersion(note.id,1,ANA,"","Milk\n");
            assertNotEquals(Arriving.What.OLDER,store.landed(note.id,ANA,2,"","Milk\nEggs\n",store.someBook(),1L).what);
            assertEquals("Milk\nEggs\n",store.get(note.id).body);
            assertEquals("mmmmmAAAAA",who(store,store.writersOf(note.id,"Milk\nEggs\n")));
            // Nothing of it is part of the note as it is kept or sent.
            assertFalse(store.get(note.id).json().toString().contains("writers"));
        }
    }

    @Test public void aNoteNewHereIsAllTheSenders() throws Exception {
        try(NoteStore store=store()) {
            store.landed("fresh",ANA,1,"From Ana","Hello",store.someBook());
            assertEquals("AAAAA",who(store,store.writersOf("fresh","Hello")));
        }
    }

    @Test public void yourOwnDevicesAreYou() throws Exception {
        try(NoteStore store=store()) {
            assertEquals(Writers.ME,store.writerOf(LAPTOP));
            assertNotEquals(Writers.ME,store.writerOf(ANA));
            store.landed("fromLaptop",LAPTOP,1,"","Written on the laptop",store.someBook());
            assertEquals(Writers.ME,store.writersOf("fromLaptop","Written on the laptop").at(0));
            // And a device that becomes yours later is you in what it wrote before.
            Writers.Palette palette=store.palette();
            assertEquals(Writers.ME,palette.person(Writers.byAddress(LAPTOP)));
        }
    }

    @Test public void textPutBackFromAVersionIsNobodysAndTheRestKeepsItsWriter() throws Exception {
        try(NoteStore store=store()) {
            NoteStore.Note note=typed(store,"Kept line\n");
            // Put back the way Versions does it: the old text saved over the note, with nobody said to have written it.
            NoteStore.Note now=store.get(note.id);now.body="Kept line\nOld line\n";now.revision++;store.save(now);
            assertEquals("mmmmmmmmmm.........",who(store,store.writersOf(note.id,now.body)));
        }
    }

    @Test public void runsThatNoLongerFitTheirNoteAreNobodys() throws Exception {
        try(NoteStore store=store()) {
            NoteStore.Note note=typed(store,"Milk\n");
            // Written by something that knew nothing of writers: the runs are for another text now.
            store.getWritableDatabase().execSQL("UPDATE notes SET body='Salt\n' WHERE id=?",new Object[]{note.id});
            assertEquals(".....",who(store,store.writersOf(note.id,"Salt\n")));
            store.remove(note.id);
            try(var c=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM writers",null)){c.moveToFirst();assertEquals(0,c.getInt(0));}
        }
    }

    @Test public void coloursAreChosenHereAndTakenBack() throws Exception {
        try(NoteStore store=store()) {
            String ana=store.writerOf(ANA);
            assertEquals(Tint.NONE,store.palette().mine);
            assertEquals(Writers.automatic(ana),store.palette().colourOf(ana));
            store.chooseInk(Writers.ME,6);store.chooseInk(ana,2);
            assertEquals(6,store.palette().colourOf(Writers.ME));
            assertEquals(6,store.palette().colourOf(Writers.byAddress(LAPTOP)));
            assertEquals(2,store.palette().colourOf(ana));
            store.chooseInk(ana,Tint.NONE);store.chooseInk(Writers.ME,Tint.NONE);
            assertEquals(Writers.automatic(ana),store.palette().colourOf(ana));
            assertEquals(Tint.NONE,store.palette().mine);
        }
    }

    @Test public void aSaveOnThePcKeepsThePagesWritersAndWhatArrivedMeanwhile() throws Exception {
        try(NoteStore store=store()) {
            NoteStore.Note note=typed(store,"Milk\n");
            NoteStore.Note seen=store.get(note.id);
            store.setLevel(Sharing.Scope.PAGE,note.id,ANA,Sharing.Level.WRITE,null);
            store.agreedOn(ANA,note.id,1);store.keepVersion(note.id,1,ANA,"","Milk\n");
            store.landed(note.id,ANA,2,"","Milk\nEggs\n",store.someBook(),1L);
            // Meanwhile "Tea" was typed on the page, which still had only "Milk".
            Writers page=Writers.of(Writers.ME,"Milk\nTea\n".length());
            NoteStore.Note saved=DesktopEdits.save(store,seen,"","Milk\nTea\n",page);
            String body=store.get(note.id).body;
            assertTrue(body.contains("Eggs")&&body.contains("Tea"));
            String letters=who(store,store.writersOf(note.id,body));
            assertEquals('A',letters.charAt(body.indexOf("Eggs")));
            assertEquals('m',letters.charAt(body.indexOf("Tea")));
            assertEquals(saved.body,body);
        }
    }

    // ---- the page -------------------------------------------------------------------------------------------------

    private static final String PHONE="MxAnaPage@127.0.0.1:9403";
    private Desktop pad;
    private String book;
    @After public void stop() throws Exception {
        if(pad!=null&&pad.frame.isDisplayable()){SwingUtilities.invokeAndWait(()->pad.shutdown(false));await(()->!pad.frame.isDisplayable());}
    }
    private void start(String body) throws Exception {
        Path folder=temp.newFolder("pc").toPath();
        try(NoteStore store=new NoteStore(new Context(folder.toFile()))) {
            book=store.someBook();
            NoteStore.Note note=new NoteStore.Note();note.id="n";note.book=book;note.title="Shared";note.body=body;note.revision=4;
            note.writers=Writers.of(Writers.ME,body.length());store.save(note);
            store.pairedWith(PHONE,"Ana's phone",false,Envelope.keys().getPublic().getEncoded(),Envelope.keys().getPublic().getEncoded());
            store.setLevel(Sharing.Scope.PAGE,"n",PHONE,Sharing.Level.WRITE,null);
            store.landed("n",PHONE,4,"Shared",body,book,4L);
        }
        Desktop[] app=new Desktop[1];
        SwingUtilities.invokeAndWait(()->{try{app[0]=new Desktop(folder,true);app[0].show();}catch(Exception e){throw new RuntimeException(e);}});
        pad=app[0];
        await(()->pad.store.latest()!=null);settle();
        SwingUtilities.invokeAndWait(()->pad.open("n"));
        await(()->onEdt(()->pad.page.isEditable()&&pad.page.getText().equals(body)));settle();
    }
    private void arrives(String body) throws Exception {
        Arriving.What[] what={null};
        pad.disk.submit(()->pad.store.landed("n",PHONE,5,"Shared",body,book,4L).what,said->what[0]=said,e->{throw new AssertionError(e);});
        await(()->what[0]!=null);
        SwingUtilities.invokeAndWait(()->pad.arrived(new Post.Landed("Ana's phone updated a note.",null,"n")));
    }

    @Test public void thePageIsDrawnInItsWritersColoursOnceTwoWroteInIt() throws Exception {
        start("Milk\n");
        assertFalse("only you wrote in it: as it always was",onEdt(pad.page::inked));
        arrives("Milk\nEggs\n");
        await(()->onEdt(()->pad.page.getText().equals("Milk\nEggs\n")));settle();
        assertTrue(onEdt(pad.page::inked));
        String ana=pad.store.writerOf(PHONE);
        assertEquals(ana,onEdt(()->pad.page.writers().at(6)));
        assertEquals(Writers.ME,onEdt(()->pad.page.writers().at(1)));
        // Typing inside Ana's word makes only the new letters yours; Ctrl+Z gives them back to her.
        SwingUtilities.invokeAndWait(()->{try{pad.page.getDocument().insertString(7,"XY",null);}catch(Exception e){throw new RuntimeException(e);}});
        assertEquals(Writers.ME,onEdt(()->pad.page.writers().at(7)));
        assertEquals(ana,onEdt(()->pad.page.writers().at(9)));
        SwingUtilities.invokeAndWait(()->{try{pad.page.getDocument().remove(6,5);}catch(Exception e){throw new RuntimeException(e);}});
        SwingUtilities.invokeAndWait(()->((javax.swing.undo.UndoManager)pad.page.getClientProperty("undo")).undo());
        assertEquals("Milk\nEgXYgs\n",onEdt(pad.page::getText));
        assertEquals(ana,onEdt(()->pad.page.writers().at(6)));
        assertEquals(Writers.ME,onEdt(()->pad.page.writers().at(7)));
        assertEquals(ana,onEdt(()->pad.page.writers().at(9)));
        // Turned off, the page is drawn as it always was.
        SwingUtilities.invokeAndWait(()->pad.setWhoWrote(false));
        assertFalse(onEdt(pad.page::inked));
        SwingUtilities.invokeAndWait(()->pad.setWhoWrote(true));
    }

    @Test public void linesArrivingAboveKeepTheReaderOnTheWordsTheyWereReading() throws Exception {
        StringBuilder text=new StringBuilder();
        for(int i=0;i<200;i++)text.append("Line ").append(i).append('\n');
        String body=text.toString();
        start(body);
        // Scrolled to line 100, the caret left at the very end of the note.
        int hundred=body.indexOf("Line 100\n");
        SwingUtilities.invokeAndWait(()->pad.page.setCaretPosition(body.length()));
        settle();
        SwingUtilities.invokeAndWait(()->{
            try{var at=pad.page.modelToView2D(hundred);pad.paperScroll.getViewport().setViewPosition(new java.awt.Point(0,(int)at.getY()));}catch(Exception e){throw new RuntimeException(e);}
        });
        settle();
        int before=onEdt(()->pad.page.viewToModel2D(pad.paperScroll.getViewport().getViewPosition()));
        assertEquals(hundred,before);
        arrives("Ana's first\nAna's second\n"+body);
        await(()->onEdt(()->pad.page.getText().startsWith("Ana's first")));settle();
        String now=onEdt(pad.page::getText);
        int top=onEdt(()->pad.page.viewToModel2D(pad.paperScroll.getViewport().getViewPosition()));
        assertEquals("still on line 100",now.indexOf("Line 100\n"),top);
        assertEquals("the caret where it was, at the end",now.length(),(int)onEdt(pad.page::getCaretPosition));
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
