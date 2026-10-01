// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

/**
 * "I have it." What a phone says back when a note has arrived and been written down.
 *
 * <p>The network can say that it took a message. It cannot say that anybody received one, and it will take
 * a message for a phone that is asleep, switched off, or no longer there. For a long time this app wrote
 * "delivered" on the network's word, and everything that leaned on that was leaning on nothing: the mark
 * that says a note is up to date, and — worse — the text two phones are taken to have agreed on, which is
 * what every merge is made against. A phone that had been handing notes to the network on behalf of
 * somebody who never got them took that person's next words for old news and set them aside in silence.
 *
 * <p>So the far end answers, and only its answer counts. Which note and which revision ride in the
 * envelope's own header, where they are sealed and signed like everything else; this is the few bytes
 * inside that say what kind of message it is.
 *
 * <p>Said after the note is written down, never before: an answer that went out ahead of the writing
 * would be a promise, and this is a receipt.
 *
 * <p>Holds no Android types, so the format is unit tested.
 */
final class Receipt {
    /** The format, and the first four bytes. */
    static final byte[] MAGIC={'M','N','R','1'};

    /**
     * It arrived, and is written down - but this phone's note does not now say what was sent: the two were
     * put together, or what arrived was older than what was here. A number, so a later build can say more.
     */
    static final int HAVE=1;
    /**
     * It arrived, and this phone's note now says exactly what was sent.
     *
     * <p>The difference matters more than it looks. Every merge is made against the text two phones last
     * both had, and "they received my revision 20" was being taken for "we both have revision 20". A
     * phone that received it, put it together with its own and ended somewhere else had agreed to
     * nothing - and the next thing it wrote was weighed against a text it never had.
     */
    static final int TOOK=3;
    /**
     * Not an answer but a question, in the same five bytes: "send me whatever you have for me."
     *
     * <p>Nothing in Maxima can be fetched. A thing arrives because somebody sent it, so a phone that has
     * been asleep all night has no way to go and get what it missed: it can only wait for the other phones
     * to try again, which they do less and less often. This is how it says it is back. It is what makes
     * Sync a button that does something on the phone that is behind, and not only on the one that is ahead.
     */
    static final int ASK=2;

    /**
     * "I have left this." Said by a phone that has stopped following something somebody shares with it,
     * to everybody who has it - one number for each kind of thing that can be left.
     *
     * <p>A phone could always stop taking a thing in, and nobody was ever told: the owner's mark said
     * "waiting" for ever, and their phone tried again every quarter of an hour for somebody who had gone.
     * Which note rides in the envelope, as it does for every answer; for a book or a collection it is any
     * note out of it, and the phone that hears works out which shelf that is from its own shelves - so
     * nothing here depends on the two phones calling a shelf by the same name, which they do not.
     */
    static final int LEFT_PAGE=4, LEFT_BOOK=5, LEFT_COLLECTION=6;

    /** The number that says a thing shared at this level has been left, or 0 where nothing can be. */
    static int left(Sharing.Scope scope) {
        if(scope==null)return 0;
        switch(scope) {
            case PAGE: return LEFT_PAGE;
            case BOOK: return LEFT_BOOK;
            case COLLECTION: return LEFT_COLLECTION;
            default: return 0;
        }
    }

    /** Which kind of thing was left, or null where the number says something else. */
    static Sharing.Scope leftScope(int about) {
        switch(about) {
            case LEFT_PAGE: return Sharing.Scope.PAGE;
            case LEFT_BOOK: return Sharing.Scope.BOOK;
            case LEFT_COLLECTION: return Sharing.Scope.COLLECTION;
            default: return null;
        }
    }

    /**
     * "You are off this." Said by whoever took somebody off something - its owner, or an admin of it - to
     * the person taken off, by the same road as leaving: which note in the envelope, and when it was
     * decided where a revision goes.
     *
     * <p>Remove used to be a row deleted on one phone. The person taken off kept a copy that still wore a
     * tick, went on sending what they wrote in it, and had it taken in - and their next word even wrote
     * them back into the list, since whoever sends a thing is written down as having it. Told, their phone
     * does what it does on leaving: the copy becomes their own, and everybody who had the thing hears
     * they are off it, which reaches whoever else had not.
     */
    static final int REMOVED_PAGE=7, REMOVED_BOOK=8, REMOVED_COLLECTION=9;

    /** The number that says somebody is off a thing shared at this level, or 0 where nobody can be. */
    static int removed(Sharing.Scope scope) {
        if(scope==null)return 0;
        switch(scope) {
            case PAGE: return REMOVED_PAGE;
            case BOOK: return REMOVED_BOOK;
            case COLLECTION: return REMOVED_COLLECTION;
            default: return 0;
        }
    }

    /** Which kind of thing somebody was taken off, or null where the number says something else. */
    static Sharing.Scope removedScope(int about) {
        switch(about) {
            case REMOVED_PAGE: return Sharing.Scope.PAGE;
            case REMOVED_BOOK: return Sharing.Scope.BOOK;
            case REMOVED_COLLECTION: return Sharing.Scope.COLLECTION;
            default: return null;
        }
    }

    /**
     * "I have what you were carrying for me." Said to a device that brought something another device left
     * with it (see {@link Courier}), which then lets go of it. Which note, and up to which revision, ride
     * in the envelope as they do for every answer. Only ever said to a device that has just brought
     * something, so only to one that knows what it means. One number for a note brought, one for an answer,
     * so that collecting one never lets go of the other.
     */
    static final int COLLECTED=10, COLLECTED_ANSWER=11;

    /** The number that says what was brought has been collected, by what kind of thing it was. */
    static int collected(int sort){return sort==Courier.ANSWER?COLLECTED_ANSWER:COLLECTED;}

    /**
     * "I have this file" and "I cannot get this file from where you said", about one file kept with a shared
     * note (see {@link Enclosure}). The envelope names the file where it names a note: a file's id is made
     * the way a note's is, sixteen bytes written out. Said only to a device that has shown it knows what an
     * answer is, and read by one from before files travelled as a later build's answer, which is to say not
     * at all.
     *
     * <p>The first is the only thing that lets a file stop saying it is not with everybody yet - as with a
     * note, a relay taking the pieces is not somebody having them. The second is how a device that cannot
     * get the pieces asks for them to go up again: they sit on relays that let things go when their shelf
     * fills, and only the device that has the file can put it back.
     */
    static final int FILE_HERE=12, FILE_MISSING=13;

    /**
     * "This device takes files sent to it on their own" (see {@link Drop}). A sending is never offered to a
     * device that has not said so, because a build from before would write the offer over a note. Said once a run
     * to every paired device that has shown it knows what an answer is - and read by one from before as a later
     * build's answer, which is to say not at all. The envelope names nothing.
     */
    static final int TAKES_FILES=14;

    /**
     * The answers about a sending, which the envelope names where it names a note: every file it lists is here;
     * the person it was for said no; a file cannot be had from where the offer said, so its pieces should go up
     * to a relay. Said only to a device that offered a sending, so only to one that knows them.
     */
    static final int DROP_HAVE=15, DROP_REFUSED=16, DROP_MISSING=17;

    /**
     * "This build knows about persons" (see {@link Persons}), in one of two ways: as it is, to a device this one does
     * not count as its owner's, and with "and you are one of my own devices" to one it does. A card goes only to a
     * device that is yours at both ends, and only the second says so from the far end. Said once a run to every
     * paired device that has shown it knows what an answer is, again when which of the two it would be changes, and
     * said back as {@link #TAKES_FILES} is. A build from before reads either as a later build's answer, which is to
     * say not at all. The envelope names nothing.
     */
    static final int PERSONS=18, PERSONS_MINE=19;

    /**
     * "This device's notebook is locked: what arrives waits sealed until it is opened", and "open again". A locked
     * notebook takes nothing in and answers nothing, so what was sent to it looked on its way for ever, the mark amber
     * with nothing anybody could do; this lets the other devices say it is locked instead. Said as it locks, while it
     * can still seal for them, and once it opens, only to devices that have said {@link #PERSONS} - builds that read an
     * unknown number as a later build's answer, which is to say not at all. The envelope names nothing; when it was
     * said rides in its moment, so a word that took the long way round does not undo a later one.
     */
    static final int LOCKED=20, OPENED=21;

    /**
     * "This build knows about trees": notes and collections nested as deep as anybody likes, a parcel's path, and a
     * collection travelling on its own (see {@link Things}, {@link Carton} and docs/HOME.md). A device that has not
     * said it is a 0.1 device, and is sent only what fits three levels; the rest waits until it says this. Said once
     * a run to every paired device that has shown it knows what an answer is, as {@link #PERSONS} is, and read by a
     * build from before as a later build's answer, which is to say not at all. The envelope names nothing.
     */
    static final int TREE=22;

    private Receipt(){}

    /** The inside of an answer. */
    static byte[] wrap(int what) {
        return new byte[]{MAGIC[0],MAGIC[1],MAGIC[2],MAGIC[3],(byte)what};
    }

    /** What an answer says, or 0 when these bytes are not one. */
    static int open(byte[] said) {
        if(said==null||said.length!=MAGIC.length+1)return 0;
        for(int at=0;at<MAGIC.length;at++)if(said[at]!=MAGIC[at])return 0;
        return said[MAGIC.length]&0xff;
    }
}
