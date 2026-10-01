package org.mininotes.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.nio.file.Path;
import java.util.*;
import org.mininotes.desktop.platform.content.Context;

/**
 * Where what the node hears goes on the PC: kept sealed in the inbox while the notebook is locked, taken in when it is
 * opened, and straight into the open notebook after - through locking again and opening again, and not handed back to
 * a window that locked while its sharing was still starting. The real notebook and synthetic phones; no network: each
 * message is handed over as the node hands over what it hears. See DesktopFiles.
 */
public class DesktopArrivingTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final List<NoteStore> open=new ArrayList<>();
    @After public void close(){for(NoteStore one:open)one.close();}

    private final class Device {
        final Context context;final NoteStore store;final Keys keys;
        Device(String name) throws Exception {
            context=new Context(temp.newFolder(name));store=new NoteStore(context);store.getWritableDatabase();open.add(store);
            keys=new Keys(context);store.mySigningKey=Base64.getEncoder().encodeToString(keys.signing().getPublic().getEncoded());
        }
        void pair(String address,String name,Device other) throws Exception {
            store.pairedWith(address,name,false,other.keys.agreement().getPublic().getEncoded(),other.keys.signing().getPublic().getEncoded());
        }
        /** A note from this device saying its build carries: once taken in, the PC counts it as one that carries. */
        byte[] saysItCarries(Device to) throws Exception {
            byte[] parcel=Parcel.wrap(new Parcel.Sent("","","","","","",false,Collections.emptyList(),"","",false,-1,true));
            return Envelope.seal(new byte[16],0,1,parcel,keys.signing(),to.keys.agreement().getPublic());
        }
        /** Whether this device has taken in what {@code from} said. */
        boolean tookIn(Device from) throws Exception {
            return context.getSharedPreferences("post",0).getStringSet("carries",new HashSet<>())
                .contains(Base64.getEncoder().encodeToString(from.keys.signing().getPublic().getEncoded()));
        }
    }

    @Test public void lockedItWaitsSealedOpenedItIsTakenInAndSoAfterLockingAgain() throws Exception {
        Device pc=new Device("pc"),one=new Device("one"),two=new Device("two"),three=new Device("three");
        pc.pair("MxOneFixture@127.0.0.1:9201","One",one);pc.pair("MxTwoFixture@127.0.0.1:9202","Two",two);pc.pair("MxThreeFixture@127.0.0.1:9203","Three",three);
        Path folder=pc.context.getFilesDir().toPath();
        java.util.function.Consumer<byte[]> notebook=bytes->Post.arrived(pc.context,pc.store,pc.keys,bytes);

        // A window starts sharing, and locks again before that has finished: what it would hand arrivals to is closed.
        long started=DesktopFiles.ticket();
        DesktopFiles.toInbox(folder);
        assertFalse("a window locked since cannot take arrivals back",DesktopFiles.toNotebook(started,notebook));

        // Locked: kept sealed as it came, and not taken in.
        DesktopFiles.arrived(one.saysItCarries(pc));
        assertEquals(1,DesktopFiles.waiting(folder));
        assertFalse(pc.tookIn(one));

        // Opened: arrivals go to the notebook, and what waited is taken in.
        assertTrue(DesktopFiles.toNotebook(DesktopFiles.ticket(),notebook));
        assertEquals(1,DesktopFiles.takeIn(pc.context,pc.store,pc.keys));
        assertTrue(pc.tookIn(one));
        assertEquals(0,DesktopFiles.waiting(folder));

        // Open: straight in, nothing kept in the inbox.
        DesktopFiles.arrived(two.saysItCarries(pc));
        assertTrue(pc.tookIn(two));
        assertEquals(0,DesktopFiles.waiting(folder));

        // Locked again and opened again: the same.
        DesktopFiles.toInbox(folder);
        DesktopFiles.arrived(three.saysItCarries(pc));
        assertFalse(pc.tookIn(three));
        assertEquals(1,DesktopFiles.waiting(folder));
        assertTrue(DesktopFiles.toNotebook(DesktopFiles.ticket(),notebook));
        assertEquals(1,DesktopFiles.takeIn(pc.context,pc.store,pc.keys));
        assertTrue(pc.tookIn(three));
        assertEquals(0,DesktopFiles.waiting(folder));
    }
}
