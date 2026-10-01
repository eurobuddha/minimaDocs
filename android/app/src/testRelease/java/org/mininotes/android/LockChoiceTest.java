package org.mininotes.android;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/**
 * Here rather than in src/test because the PC build compiles every test in src/test against its own shared
 * classes, and LockChoice is the phone's alone.
 */
public class LockChoiceTest {
    private static final LockChoice.Way PHONE=LockChoice.Way.PHONE,PASSWORD=LockChoice.Way.PASSWORD,WORDS=LockChoice.Way.WORDS;

    @Test public void lockingOffersThePhonesOwnUnlockFirstAndAPasswordOnlyWhereItHasNone() {
        assertEquals(PHONE,LockChoice.offered(true));
        assertEquals(PASSWORD,LockChoice.offered(false));
        assertEquals("Use fingerprint or screen lock",LockChoice.lockButton(PHONE));
        assertEquals("Use a password instead",LockChoice.lockOther(PHONE,true));
        // Chosen a password after all: the phone's unlock is the way back. Without one, there is no other choice.
        assertEquals("Use fingerprint or screen lock instead",LockChoice.lockOther(PASSWORD,true));
        assertNull(LockChoice.lockOther(PASSWORD,false));
        assertFalse(LockChoice.lockIntro(PHONE).contains("password"));
        assertTrue(LockChoice.lockIntro(PASSWORD).contains("password"));
        assertTrue(LockChoice.wordsFor(false).endsWith("if the phone's unlock ever cannot."));
        assertTrue(LockChoice.wordsFor(true).endsWith("if you forget the password."));
    }

    @Test public void theUnlockPageStartsWithThePhonesPromptWithNothingToPressFirst() {
        assertEquals(PHONE,LockChoice.first(true,true));
        assertEquals(PHONE,LockChoice.first(true,false));
        assertTrue(LockChoice.askAtOnce(PHONE));
        assertFalse(LockChoice.askAtOnce(PASSWORD));
        assertEquals("Unlock with fingerprint",LockChoice.unlockButton(PHONE,true));
        assertEquals("Unlock with your phone",LockChoice.unlockButton(PHONE,false));
        // The password is a quiet way beside it, only where there is one; the words always.
        assertEquals(Arrays.asList(PASSWORD,WORDS),LockChoice.otherWays(PHONE,true,true));
        assertEquals(Collections.singletonList(WORDS),LockChoice.otherWays(PHONE,true,false));
        assertEquals("Use backup password",LockChoice.useInstead(PASSWORD));
        assertEquals("Use recovery words",LockChoice.useInstead(WORDS));
    }

    @Test public void aNotebookLockedTheOldWayOpensAsItDid() {
        // A password, and no phone unlock set up: the password page, as before.
        assertEquals(PASSWORD,LockChoice.first(false,true));
        assertEquals("Type your password to open your notes.",LockChoice.unlockLine(PASSWORD,false));
        assertEquals("Unlock",LockChoice.unlockButton(PASSWORD,false));
        assertEquals(Collections.singletonList(WORDS),LockChoice.otherWays(PASSWORD,false,true));
        // Both: the phone's prompt first, and the password was always its backup.
        assertEquals(Arrays.asList(PHONE,WORDS),LockChoice.otherWays(PASSWORD,true,true));
        assertEquals("Type your backup password.",LockChoice.unlockLine(PASSWORD,true));
        assertEquals(LockChoice.WARNING_PASSWORD,LockChoice.warning(true));
    }

    @Test public void thePhonesUnlockGoneAndNoPasswordLeavesTheWordsAndSaysWhy() {
        assertEquals(WORDS,LockChoice.first(false,false));
        assertFalse(LockChoice.askAtOnce(WORDS));
        assertEquals("Use recovery words",LockChoice.unlockButton(WORDS,true));
        assertTrue(LockChoice.otherWays(WORDS,false,false).isEmpty());
        assertTrue(LockChoice.unlockLine(WORDS,false).startsWith("The phone's unlock no longer opens Mininotes."));
        assertEquals("Unlock",LockChoice.recoverButton(false));
        assertFalse(LockChoice.recoverLine(false).contains("password"));
        assertEquals("Unlock and set password",LockChoice.recoverButton(true));
    }

    @Test public void theWarningIsTrueOfTheLockAsChosen() {
        assertTrue(LockChoice.warning(true).startsWith("If you lose both the backup password and the 12 recovery words"));
        String none=LockChoice.warning(false);
        assertFalse(none.contains("password"));
        assertTrue(none.contains("12 recovery words"));
        assertTrue(none.contains("screen lock removed"));
        assertTrue(none.endsWith("There is no email reset and no copy anywhere else."));
    }

    @Test public void oneEverydayWayStaysTheLastOneCanOnlyBeSwapped() {
        assertTrue(LockChoice.canRemovePassword(true,true));
        assertFalse(LockChoice.canRemovePassword(false,true));
        assertFalse(LockChoice.canRemovePassword(true,false));
        assertTrue(LockChoice.canSwitchOffPhone(true));
        assertFalse(LockChoice.canSwitchOffPhone(false));
        assertTrue(LockChoice.KEEP_ONE.startsWith("Add a password first."));
    }

    @Test public void securitySaysWhatOpensIt() {
        assertTrue(LockChoice.means(false,true,false,true).endsWith("it then opens with your fingerprint or screen lock."));
        assertTrue(LockChoice.means(false,false,false,true).endsWith(" with a password."));
        assertTrue(LockChoice.means(true,true,true,true).startsWith("Mininotes opens with your fingerprint or screen lock. Your backup password"));
        String noPassword=LockChoice.means(true,true,true,false);
        assertTrue(noPassword.contains("There is no password."));
        assertTrue(noPassword.contains("Your backups open with the 12 recovery words."));
        assertTrue(LockChoice.means(true,true,false,true).contains("switch on fingerprint or screen lock below"));
        assertFalse(LockChoice.means(true,false,false,true).contains("switch on"));
        assertTrue(LockChoice.means(true,true,false,false).startsWith("Mininotes opens only with your 12 recovery words now."));
        assertEquals("Backup password",LockChoice.passwordName(true));
        assertEquals("Password",LockChoice.passwordName(false));
    }

    @Test public void askingAgainNamesTheWayThatOpensIt() {
        assertEquals("Type your password to turn it off.",LockChoice.confirmLine(PASSWORD,"to turn it off"));
        assertEquals("Use your fingerprint or screen lock to add a password.",LockChoice.confirmLine(PHONE,"to add a password"));
        assertEquals("Type your 12 recovery words to see your recovery words.",LockChoice.confirmLine(WORDS,"to see your recovery words"));
    }
}
