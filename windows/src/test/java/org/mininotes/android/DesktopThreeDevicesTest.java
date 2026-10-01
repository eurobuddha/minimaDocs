package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.util.*;
import org.mininotes.desktop.platform.content.Context;

/**
 * The real notebook, weighing what the owner's other two devices send it: the Pro's writing, and the PC passing on what
 * it took from the Pro. See ThreeDevicesTest for the fault, seen on 2026-09-29, and Arriving.weigh for the rule.
 */
public class DesktopThreeDevicesTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final List<NoteStore> open=new ArrayList<>();
    @After public void close(){for(NoteStore one:open)one.close();}
    private static final String PRO="MxProFixture@127.0.0.1:9201", PC="MxPcFixture@127.0.0.1:9202";

    private NoteStore store(String name) throws Exception {
        NoteStore made=new NoteStore(new Context(temp.newFolder(name)));made.getWritableDatabase();open.add(made);return made;
    }

    /** The phone: the note came from the Pro at 60, and the PC then passed on the Pro's next text. */
    private NoteStore phoneWithTheProsTextFromThePc() throws Exception {
        NoteStore phone=store("phone");
        assertEquals(Arriving.What.NEW,phone.landed("n",PRO,60,"Transfer","Transfer\nfirst line",phone.someBook(),-1L).what);
        // And the PC had the same, so all three agree at 60.
        assertEquals(Arriving.What.NEWER,phone.landed("n",PC,60,"Transfer","Transfer\nfirst line",phone.someBook(),-1L).what);
        assertEquals(Arriving.What.NEWER,phone.landed("n",PC,61,"Transfer","Transfer\nsecond line",phone.someBook(),60L).what);
        return phone;
    }

    @Test public void thePhoneTakesWhatTheProWroteOnTopOfWhatThePcPassedOn() throws Exception {
        // The Pro's own notebook, and what it says its note held.
        NoteStore pro=store("pro");
        NoteStore.Note mine=new NoteStore.Note();mine.id="n";mine.book=pro.someBook();mine.title="Transfer";
        mine.body="Transfer\nsecond line";mine.revision=61;pro.save(mine);pro.keepVersion("n","");
        mine.body="Transfer\nthird line";mine.revision=62;pro.save(mine);
        List<String> held=pro.history("n");
        assertEquals(Arriving.trace("Transfer\nthird line"),held.get(0));
        assertTrue(held.contains(Arriving.trace("Transfer\nsecond line")));

        NoteStore phone=phoneWithTheProsTextFromThePc();
        Arriving.Decision said=phone.landed("n",PRO,62,"Transfer","Transfer\nthird line",phone.someBook(),60L,held);
        assertEquals(Arriving.What.NEWER,said.what);
        assertEquals("Transfer\nthird line",phone.get("n").body);
        assertEquals("no revision of its own",62,phone.get("n").revision);
        assertEquals("the phone and the Pro now agree",62,phone.agreedAt("n",PRO));
    }

    @Test public void fromABuildThatDoesNotSayTheCountsAloneStillPutThemTogether() throws Exception {
        NoteStore phone=phoneWithTheProsTextFromThePc();
        Arriving.Decision said=phone.landed("n",PRO,62,"Transfer","Transfer\nthird line",phone.someBook(),60L,null);
        assertEquals("as it was seen",Arriving.What.MERGED,said.what);
    }

    @Test public void theProsOlderTextPassedOnLateChangesNothing() throws Exception {
        NoteStore phone=phoneWithTheProsTextFromThePc();
        List<String> held=Arrays.asList(Arriving.trace("Transfer\nthird line"),Arriving.trace("Transfer\nsecond line"));
        phone.landed("n",PRO,62,"Transfer","Transfer\nthird line",phone.someBook(),60L,held);
        // The PC, believing the phone has less than it does, passes on the second line again.
        Arriving.Decision said=phone.landed("n",PC,61,"Transfer","Transfer\nsecond line",phone.someBook(),59L,null);
        assertEquals(Arriving.What.OLDER,said.what);
        assertEquals("Transfer\nthird line",phone.get("n").body);
        assertEquals(62,phone.get("n").revision);
    }
}
