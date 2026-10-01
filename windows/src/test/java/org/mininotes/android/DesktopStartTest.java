package org.mininotes.android;

import static org.junit.Assert.*;

import java.nio.channels.*;
import java.nio.file.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class DesktopStartTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();

    /** A second start sees the notebook held, and asks that window forward instead of failing; a free one it opens. */
    @Test public void aNotebookHeldByAnotherWindowIsSeenAsOpen() throws Exception {
        Path home=temp.newFolder("pad").toPath();
        assertFalse("nothing there yet",Desktop.alreadyOpen(home));
        Path lock=home.resolve("notebook.lock");
        try(FileChannel channel=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock held=channel.tryLock()) {
            assertNotNull(held);
            assertTrue("held by the running window",Desktop.alreadyOpen(home));
        }
        assertFalse("let go when that window closed",Desktop.alreadyOpen(home));
    }

    /** A Mininotes that locked itself again lets go of the notebook, but is still there, waiting at its question. */
    @Test public void aMininotesWaitingAtItsQuestionIsSeenAsOpen() throws Exception {
        Path home=temp.newFolder("waiting").toPath();
        try(FileChannel channel=FileChannel.open(home.resolve(Desktop.RUNNING_LOCK),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock held=channel.tryLock()) {
            assertNotNull(held);
            assertTrue("the process still holds its own lock",Desktop.alreadyOpen(home));
        }
        assertFalse("gone when the process ends",Desktop.alreadyOpen(home));
    }
}
