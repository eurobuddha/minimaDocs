// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.util.ArrayList;
import java.util.List;

/**
 * How a locked notebook opens on this phone, as its owner chose it, and what the screens say about it.
 *
 * <p>Three ways in: the phone's own unlock (fingerprint, face, or its screen lock), a password, and the 12
 * recovery words. The words always exist. The phone's unlock comes first wherever the phone has one; the
 * password is for whoever wants it, and is never forced on anyone whose phone can do better. What must
 * never happen is a notebook that opens only with the words every day, so the last everyday way in cannot
 * be taken away - only swapped.
 *
 * <p>Holds no Android types; every rule and every sentence here is unit tested.
 */
final class LockChoice {
    enum Way{PHONE,PASSWORD,WORDS}

    private LockChoice(){}

    // ---- the warning, which must stay true whichever way was chosen ----------------------------------------

    static final String WARNING_PASSWORD="If you lose both the backup password and the 12 recovery words, nobody can open this notebook. Not you, and not the people who make Mininotes. There is no email reset and no copy anywhere else.";
    static final String WARNING_PHONE="If this phone's unlock stops opening Mininotes - the screen lock removed, or the phone reset or lost - and you have lost the 12 recovery words, nobody can open this notebook. Not you, and not the people who make Mininotes. There is no email reset and no copy anywhere else.";

    /** With no password, the words are the only way in that does not live in this phone, and it says so. */
    static String warning(boolean hasPassword){return hasPassword?WARNING_PASSWORD:WARNING_PHONE;}

    // ---- putting the lock on ---------------------------------------------------------------------------------

    /** What the lock is offered with first: the phone's own unlock where it has one, a password where not. */
    static Way offered(boolean phoneCan){return phoneCan?Way.PHONE:Way.PASSWORD;}

    static String lockIntro(Way way) {
        return way==Way.PHONE
            ?"Your notes on this phone will be encrypted, and Mininotes will open with your fingerprint or the phone's screen lock. You will also get 12 recovery words, to open it if the phone's unlock ever cannot."
            :"Your notes on this phone will be encrypted. Mininotes will ask for this password each time it opens. You will also get 12 recovery words, for if you forget the password.";
    }

    /** The one thing to press on the first page of locking. */
    static String lockButton(Way way){return way==Way.PHONE?"Use fingerprint or screen lock":"Continue";}

    /** The quiet other choice beside it, or null when there is none. */
    static String lockOther(Way way,boolean phoneCan){return way==Way.PHONE?"Use a password instead":phoneCan?"Use fingerprint or screen lock instead":null;}

    /** What the words are for, said when they are first shown. */
    static String wordsFor(boolean hasPassword) {
        return "Write these 12 words on paper, in this order, and keep them in a safe place, away from this phone. With them you can open your notes "
            +(hasPassword?"if you forget the password.":"if the phone's unlock ever cannot.");
    }

    // ---- opening it ----------------------------------------------------------------------------------------

    /** The way the unlock page starts with: the phone's own where it is set up, then the password, then the words. */
    static Way first(boolean hasBio,boolean hasPassword){return hasBio?Way.PHONE:hasPassword?Way.PASSWORD:Way.WORDS;}

    /** Whether the phone's prompt comes up by itself, with nothing to press first. */
    static boolean askAtOnce(Way way){return way==Way.PHONE;}

    static String unlockLine(Way way,boolean hasBio) {
        switch(way) {
            case PHONE: return "Open it with your fingerprint or the phone's screen lock.";
            case PASSWORD: return hasBio?"Type your backup password.":"Type your password to open your notes.";
            default: return "The phone's unlock no longer opens Mininotes. Type your 12 recovery words to open it; then it can be set up again.";
        }
    }

    /** The page's one primary button. */
    static String unlockButton(Way way,boolean fingerprint) {
        switch(way) {
            case PHONE: return fingerprint?"Unlock with fingerprint":"Unlock with your phone";
            case PASSWORD: return "Unlock";
            default: return "Use recovery words";
        }
    }

    /** The quiet ways beside it, in the order they are shown. */
    static List<Way> otherWays(Way way,boolean hasBio,boolean hasPassword) {
        List<Way> ways=new ArrayList<>();
        if(way==Way.PHONE&&hasPassword)ways.add(Way.PASSWORD);
        if(way==Way.PASSWORD&&hasBio)ways.add(Way.PHONE);
        if(way!=Way.WORDS)ways.add(Way.WORDS);
        return ways;
    }

    /** How a quiet way beside the button is named. */
    static String useInstead(Way way) {
        switch(way) {
            case PHONE: return "Use fingerprint or screen lock";
            // Offered only beside the phone's own unlock, where the password is the backup one, and named so, as on the PC.
            case PASSWORD: return "Use backup password";
            default: return "Use recovery words";
        }
    }

    /** Opening with the words: a forgotten password is replaced there; with none, the words simply open it. */
    static String recoverLine(boolean hasPassword) {
        return hasPassword?"Type the 12 recovery words you wrote down when the lock was set, in order. Then choose a new password."
            :"Type the 12 recovery words you wrote down when the lock was set, in order.";
    }
    static String recoverButton(boolean hasPassword){return hasPassword?"Unlock and set password":"Unlock";}

    // ---- Settings: Security ------------------------------------------------------------------------------

    /** What the lock means now, under its switch. */
    static String means(boolean on,boolean phoneCan,boolean hasBio,boolean hasPassword) {
        if(!on)return "Anyone who can read this phone's storage can read your notes. Lock Mininotes to encrypt them"
            +(phoneCan?"; it then opens with your fingerprint or screen lock.":" with a password.");
        String sealed=" Your notes, their files and the backups you export are encrypted.";
        if(hasBio&&hasPassword)return "Mininotes opens with your fingerprint or screen lock. Your backup password and your 12 recovery words open it too."+sealed;
        if(hasBio)return "Mininotes opens with your fingerprint or screen lock, and your 12 recovery words open it too. There is no password."+sealed
            +" Your backups open with the 12 recovery words.";
        if(hasPassword)return "Mininotes asks for your password when it opens"+(phoneCan?" - switch on fingerprint or screen lock below and you will rarely need it":"")+"."+sealed;
        return "Mininotes opens only with your 12 recovery words now. Switch on fingerprint or screen lock below, or add a password."+sealed;
    }

    /** The password's row: named for what it is to this notebook. */
    static String passwordName(boolean hasBio){return hasBio?"Backup password":"Password";}

    /** The password can go only while the phone's own unlock still opens it; the words are no everyday way in. */
    static boolean canRemovePassword(boolean hasBio,boolean hasPassword){return hasBio&&hasPassword;}

    /** Likewise the phone's unlock can be switched off only while a password stays. */
    static boolean canSwitchOffPhone(boolean hasPassword){return hasPassword;}
    static final String KEEP_ONE="Add a password first. Without it or the phone's unlock, only the 12 recovery words would open Mininotes.";

    /** The words shown again, a password added, the lock taken off: asked of whichever way opens it. */
    static String confirmLine(Way way,String what) {
        return way==Way.PASSWORD?"Type your password "+what+".":way==Way.PHONE?"Use your fingerprint or screen lock "+what+".":"Type your 12 recovery words "+what+".";
    }
}
