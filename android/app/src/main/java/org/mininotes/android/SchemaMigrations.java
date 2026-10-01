// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The ordered schema of the local notebook. An upgrade is a sequence of additive statements, never a
 * drop and recreate: an installed build must not lose notes because the schema moved. Holds no Android
 * types, so the version rules are unit tested without a device.
 */
final class SchemaMigrations {
    static final int VERSION=33;

    /** Every pad starts with one collection holding one book, so writing never begins with a decision. */
    static final String FIRST_COLLECTION="collection-first", FIRST_BOOK="book-first";

    private static final String NOTES_TABLE=
        "CREATE TABLE notes(id TEXT PRIMARY KEY,title TEXT NOT NULL,body TEXT NOT NULL,notebook TEXT NOT NULL,"
        +"pinned INTEGER NOT NULL,deleted INTEGER NOT NULL,updated INTEGER NOT NULL)";
    /** The list filters on deleted and orders by pinned then updated; a reverse index scan serves that directly. */
    private static final String VIEW_INDEX="CREATE INDEX IF NOT EXISTS notes_view ON notes(deleted,pinned,updated)";

    private static final String NOW="CAST(strftime('%s','now') AS INTEGER)*1000";
    private static final String COLLECTIONS="CREATE TABLE IF NOT EXISTS collections(id TEXT PRIMARY KEY,name TEXT NOT NULL,updated INTEGER NOT NULL)";
    private static final String BOOKS="CREATE TABLE IF NOT EXISTS books(id TEXT PRIMARY KEY,collection TEXT NOT NULL,name TEXT NOT NULL,updated INTEGER NOT NULL)";
    /** One row per address that a level is shared with. The primary key makes adding the same address twice a no-op. */
    private static final String SHARES="CREATE TABLE IF NOT EXISTS shares(scope TEXT NOT NULL,target TEXT NOT NULL,address TEXT NOT NULL,"
        +"mine INTEGER NOT NULL,added INTEGER NOT NULL,PRIMARY KEY(scope,target,address))";
    private static final String BOOK_COLUMN="ALTER TABLE notes ADD COLUMN book TEXT NOT NULL DEFAULT '"+FIRST_BOOK+"'";
    private static final String FIRST_COLLECTION_ROW="INSERT OR IGNORE INTO collections(id,name,updated) VALUES('"+FIRST_COLLECTION+"','My notes',"+NOW+")";
    private static final String FIRST_BOOK_ROW="INSERT OR IGNORE INTO books(id,collection,name,updated) VALUES('"+FIRST_BOOK+"','"+FIRST_COLLECTION+"','Notes',"+NOW+")";
    /** Addresses you share with, kept once so they are picked rather than pasted each time. */
    private static final String ADDRESSES="CREATE TABLE IF NOT EXISTS addresses(address TEXT PRIMARY KEY,name TEXT NOT NULL,"
        +"mine INTEGER NOT NULL,added INTEGER NOT NULL)";
    private static final String BOOKS_INDEX="CREATE INDEX IF NOT EXISTS books_collection ON books(collection)";
    private static final String PAGES_INDEX="CREATE INDEX IF NOT EXISTS notes_book ON notes(book,deleted,updated)";

    /**
     * The order the reader put things in, one number per row, smallest first. Seeded as the negative of
     * `updated` so an upgraded pad opens in exactly the order it closed in — newest first — and so anything
     * made later, which also starts at minus the clock, arrives at the top rather than at the end.
     */
    private static final String[] PLACES={
        "ALTER TABLE collections ADD COLUMN place INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE books ADD COLUMN place INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE notes ADD COLUMN place INTEGER NOT NULL DEFAULT 0",
        "UPDATE collections SET place=-updated",
        "UPDATE books SET place=-updated",
        "UPDATE notes SET place=-updated",
        "CREATE INDEX IF NOT EXISTS notes_place ON notes(book,deleted,place)",
    };

    /**
     * Nothing is thrown away by surprise. A page already carried `deleted`, which now means the bin rather
     * than gone; collections and books gain the same, and all three gain `archived` for what is only put
     * away. Existing soft-deleted pages therefore turn up in the bin, which is where they belong.
     */
    private static final String[] AWAY={
        "ALTER TABLE collections ADD COLUMN archived INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE books ADD COLUMN archived INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE notes ADD COLUMN archived INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE collections ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE books ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0",
    };

    /**
     * The colour a single collection, book or page was given, as which colour rather than as a pixel value,
     * so it holds when the paper under it moves. 0 is no colour of its own, which is what everything starts
     * as and what an unknown number falls back to.
     */
    private static final String[] COLOURS={
        "ALTER TABLE collections ADD COLUMN colour INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE books ADD COLUMN colour INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE notes ADD COLUMN colour INTEGER NOT NULL DEFAULT 0",
    };

    /**
     * Which revision of which page reached which address. Maxima carries a message to a contact rather than
     * holding it for collection, so the sending side is the only place that can know what got through: a row
     * appears here when an address has received a page, and everything written after it is owed again.
     */
    private static final String[] OUTBOX={
        "CREATE TABLE IF NOT EXISTS sent(address TEXT NOT NULL,page TEXT NOT NULL,revision INTEGER NOT NULL,"
        +"at INTEGER NOT NULL,PRIMARY KEY(address,page))",
    };

    /**
     * Files kept with a note. The bytes live in the app's own folder, one file to a row; this table is what
     * says whose they are, what they were called and how big they are. They follow their note: putting it
     * away takes them with it, and deleting it for good deletes them for good.
     */
    private static final String[] FILES={
        "CREATE TABLE IF NOT EXISTS files(id TEXT PRIMARY KEY,note TEXT NOT NULL,name TEXT NOT NULL,"
        +"kind TEXT NOT NULL,bytes INTEGER NOT NULL,added INTEGER NOT NULL,place INTEGER NOT NULL DEFAULT 0)",
        "CREATE INDEX IF NOT EXISTS files_note ON files(note,place)",
    };

    /**
     * A file can be kept with a collection or a book as well as a note. The column the file already had
     * holds whichever one it belongs to; this says which kind that is. Everything already in the table was
     * kept with a note, which is what the default says, so nothing has to be moved.
     */
    private static final String[] FILES_ANYWHERE={
        "ALTER TABLE files ADD COLUMN held TEXT NOT NULL DEFAULT 'note'",
        "CREATE INDEX IF NOT EXISTS files_held ON files(held,note,place)",
    };

    /**
     * What a device you have paired with is, beyond an address: the keys to seal for it and to check it by,
     * and the name Maxima knows it under so a message can be handed to it. And, on a note, whether it came
     * from somebody else and which address it came from — a note of theirs is never written back to them.
     */
    private static final String[] PAIRED={
        "ALTER TABLE addresses ADD COLUMN contact TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE addresses ADD COLUMN agreement TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE addresses ADD COLUMN signing TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE notes ADD COLUMN theirs INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE notes ADD COLUMN origin TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE notes ADD COLUMN revision INTEGER NOT NULL DEFAULT 0",
    };

    /**
     * Every version a note has had: what it said, when that was kept, and where it came from — this phone,
     * or an address that sent it. Nothing is ever overwritten out of existence: putting an old version back
     * writes a new one rather than erasing what happened in between, and a note two people wrote at once
     * keeps both sides here rather than choosing between them silently.
     */
    private static final String[] VERSIONS={
        "CREATE TABLE IF NOT EXISTS versions(id TEXT PRIMARY KEY,note TEXT NOT NULL,revision INTEGER NOT NULL,"
        +"at INTEGER NOT NULL,source TEXT NOT NULL,title TEXT NOT NULL,body TEXT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS versions_note ON versions(note,at DESC)",
    };

    /**
     * A collection and a book can have come from somebody else, the same way a note already could, and a
     * shelf you were given has to be told apart from one you made. And what this phone will no longer take
     * in: unsubscribing cannot stop somebody sending - only they can decide that - but it can stop what
     * they send being put on the shelves, which is the part this phone owns.
     */
    private static final String[] SHARED_IN={
        "ALTER TABLE collections ADD COLUMN theirs INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE collections ADD COLUMN origin TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE books ADD COLUMN theirs INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE books ADD COLUMN origin TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE notes ADD COLUMN writes INTEGER NOT NULL DEFAULT 0",
        "CREATE TABLE IF NOT EXISTS refused(address TEXT NOT NULL,target TEXT NOT NULL,"
        +"kind TEXT NOT NULL,at INTEGER NOT NULL,PRIMARY KEY(address,target))",
    };

    /**
     * An offer taken up, until the phone that made it answers.
     *
     * <p>An acceptance is one message to a phone that may be asleep, in a drawer, or being updated - and a
     * message to a node nobody is listening on is gone. Kept here instead, and said again every time this
     * app opens, until something arrives from them: otherwise accepting something works when the two
     * phones happen to be awake together and silently does nothing when they are not.
     */
    private static final String[] ACCEPTING={
        "CREATE TABLE IF NOT EXISTS accepting(address TEXT PRIMARY KEY,name TEXT NOT NULL,"
        +"scope TEXT NOT NULL,target TEXT NOT NULL,writes INTEGER NOT NULL,at INTEGER NOT NULL,"
        +"tries INTEGER NOT NULL DEFAULT 0)",
    };

    /**
     * A collection or a book can be a favourite, the way a note already could.
     *
     * <p>Notes have carried `pinned` since the first schema and nothing ever read it. It is what a
     * favourite is; the other two levels only lacked the column.
     */
    private static final String[] FAVOURITES={
        "ALTER TABLE collections ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE books ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0",
        "CREATE INDEX IF NOT EXISTS notes_recent ON notes(deleted,archived,updated)",
    };

    /**
     * Who has a thing, kept by everybody who has it.
     *
     * <p>A share was one row saying "this address may write": enough for one phone handing something out,
     * and not enough for a thing several people hold. It gains what it takes to be a membership that can
     * travel and be merged — the level rather than a yes or no, the device by the key it signs with
     * rather than by an address that moves, and when the decision was made, so that two people deciding at
     * once settle on the later decision rather than on whichever message arrived last.
     *
     * <p>Existing rows keep their meaning: what was "may write" becomes WRITE, and what was not becomes
     * READ. `changed` is seeded from when the row was added, so an old decision never outranks a new one.
     */
    private static final String[] MEMBERS={
        "ALTER TABLE shares ADD COLUMN level INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE shares ADD COLUMN changed INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE shares ADD COLUMN who TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE shares ADD COLUMN name TEXT NOT NULL DEFAULT ''",
        "UPDATE shares SET level=CASE WHEN mine=1 THEN 2 ELSE 1 END",
        "UPDATE shares SET changed=added WHERE changed=0",
    };

    /**
     * How long a thing waits after the writing stops before it goes.
     *
     * <p>Zero means "whatever the thing above says", so a collection can be set once and every book and
     * note in it follows; a negative number means it waits to be asked. Set on any of the three levels,
     * because a shopping list somebody is reading in a shop and a diary are not the same thing.
     */
    private static final String[] PAUSE={
        "ALTER TABLE collections ADD COLUMN pause INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE books ADD COLUMN pause INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE notes ADD COLUMN pause INTEGER NOT NULL DEFAULT 0",
    };

    /**
     * What the network took, and nobody has yet said they have.
     *
     * <p>`sent` was written when the network took a message, and read as though somebody had received it.
     * Those are different events. From here on `sent` means what it was always read as — the far phone said
     * it has this — and this table holds the time in between: handed over, when, and how many times, so
     * that what was never answered can be tried again. Rows already in `sent` are left as they are: most
     * of them are true. Where one is not, it claims the far phone has more than it does - and a note now
     * says what it was written on top of, so the older of the two beliefs is the one a merge uses.
     */
    private static final String[] HANDED={
        "CREATE TABLE IF NOT EXISTS handed(address TEXT NOT NULL,page TEXT NOT NULL,revision INTEGER NOT NULL,"
        +"at INTEGER NOT NULL,tries INTEGER NOT NULL,PRIMARY KEY(address,page))",
    };

    /**
     * What two phones last both had, kept apart from what was last delivered.
     *
     * <p>`sent.revision` is the revision the far phone has said it received, and it is what the mark
     * shows. `agreed` is the revision at which its note and this one said the same thing, and it is what a
     * merge is made against. They were one number, and they are not one thing: a phone can receive a note,
     * put it together with its own, and end somewhere else. Nought for every row there is, which is "never
     * agreed": the first time two copies meet after this they are put together with nothing assumed, so
     * where they differ both keep everything, and where they are the same nothing happens at all.
     */
    private static final String[] AGREED={
        "ALTER TABLE sent ADD COLUMN agreed INTEGER NOT NULL DEFAULT 0",
    };

    /**
     * What this phone itself may do with a thing another device shares with it.
     *
     * <p>Every member of a shared thing is written down in `shares` - except the phone doing the writing,
     * which leaves its own entry out, and has to: a row there is somebody to send to, and a phone that
     * wrote itself down sent itself its own notes. So its own standing had nowhere to live, and a phone
     * made an admin went on saying it could write, because whether it could write was the only thing about
     * itself it kept. Here, with when it was decided, so an older word never overrules a newer one.
     */
    private static final String[] STANDING={
        "CREATE TABLE IF NOT EXISTS standing(scope TEXT NOT NULL,target TEXT NOT NULL,level INTEGER NOT NULL,"
        +"changed INTEGER NOT NULL,PRIMARY KEY(scope,target))",
    };

    /**
     * Stopped for now, or left.
     *
     * <p>`refused` was one thing: what this phone has stopped taking in, which it can start again by
     * itself. Leaving is another. The others are told, the copy here becomes this phone's own, and only
     * being given the thing again brings it back - so what arrives late from somebody who has not heard
     * yet has to be told apart from an invitation that is newer than the leaving, and `at` is what it is
     * told apart by.
     */
    private static final String[] LEAVING={
        "ALTER TABLE refused ADD COLUMN gone INTEGER NOT NULL DEFAULT 0",
    };

    /**
     * What was given at pairing time is what the offer said.
     *
     * <p>Since rows have had a level, the one thing that wrote a row without one was giving somebody a
     * thing off their code: `mine` went in as the offer said, `level` was left to the table's default,
     * and the default is <i>read</i>. Nothing read `mine` any more, so everybody given something that way
     * was a reader by the column everything reads - which showed nowhere while a reader could still write,
     * and would have made every such share read-only the day that stopped. Those rows, and only those,
     * say one thing in `mine` and another in `level`; they are made to agree with the offer, and dated from
     * when they were made, so that they can be outranked by a decision and not by any list that arrives.
     */
    private static final String[] GIVEN={
        "UPDATE shares SET level=2 WHERE level=1 AND mine=1",
        "UPDATE shares SET changed=added WHERE changed=0",
    };

    /**
     * What this device is carrying for others, sealed for somebody else: see {@link Courier}. One row per
     * sender, recipient, note and kind, so a newer revision replaces an older one; the bytes as text, since
     * nothing else here is kept as bytes. `tried` and `tries` say when it was last brought, and how often.
     */
    private static final String[] CARRIED={
        "CREATE TABLE IF NOT EXISTS carried(sender TEXT NOT NULL,recipient TEXT NOT NULL,page TEXT NOT NULL,"
        +"sort INTEGER NOT NULL,revision INTEGER NOT NULL,bytes TEXT NOT NULL,size INTEGER NOT NULL,"
        +"kept INTEGER NOT NULL,tried INTEGER NOT NULL DEFAULT 0,tries INTEGER NOT NULL DEFAULT 0,"
        +"PRIMARY KEY(sender,recipient,page,sort))",
    };

    /**
     * Files go with a shared note (see {@link Enclosure}). A file says which device it came from - empty for
     * one added here, which is every file there is so far - so that device's list can take out only what it
     * put in. What has gone up is remembered with where it is and the key that opens it, so it goes up once;
     * what somebody listed and is not here yet waits to be fetched, or was taken out here and is not fetched
     * back; who has said they have which file is written down, since only they can say so; and the newest
     * list taken from each device, so an older one arriving late changes nothing.
     */
    private static final String[] FILES_TRAVEL={
        "ALTER TABLE files ADD COLUMN origin TEXT NOT NULL DEFAULT ''",
        "CREATE TABLE IF NOT EXISTS published(id TEXT PRIMARY KEY,manifest TEXT NOT NULL,at INTEGER NOT NULL,"
        +"tried INTEGER NOT NULL DEFAULT 0,tries INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE IF NOT EXISTS incoming(id TEXT PRIMARY KEY,note TEXT NOT NULL,origin TEXT NOT NULL,"
        +"name TEXT NOT NULL,kind TEXT NOT NULL,bytes INTEGER NOT NULL,manifest TEXT NOT NULL,"
        +"tried INTEGER NOT NULL DEFAULT 0,tries INTEGER NOT NULL DEFAULT 0,declined INTEGER NOT NULL DEFAULT 0)",
        "CREATE INDEX IF NOT EXISTS incoming_note ON incoming(note,origin)",
        "CREATE TABLE IF NOT EXISTS reached(id TEXT NOT NULL,address TEXT NOT NULL,has INTEGER NOT NULL DEFAULT 0,"
        +"at INTEGER NOT NULL,tells INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(id,address))",
        "CREATE TABLE IF NOT EXISTS listed(note TEXT NOT NULL,origin TEXT NOT NULL,at INTEGER NOT NULL,"
        +"PRIMARY KEY(note,origin))",
    };

    /**
     * What the PC holds for its owner's phones until they collect it: see {@link Home}. Sealed for the phone it
     * is for, so kept as it came, the bytes as text like everything else here. Named by the hash of the bytes,
     * which is what the phone says it has; who left it, who it is for, which note and revision, and how long
     * what is sealed inside is, are what the outside of it says, and are what lets a note sealed again replace
     * the copy it would otherwise pile on.
     */
    private static final String[] HELD={
        "CREATE TABLE IF NOT EXISTS held(id TEXT PRIMARY KEY,sender TEXT NOT NULL,recipient TEXT NOT NULL,"
        +"page TEXT NOT NULL,revision INTEGER NOT NULL,sealed INTEGER NOT NULL,bytes TEXT NOT NULL,size INTEGER NOT NULL,"
        +"kept INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS held_recipient ON held(recipient,kept)",
    };

    /**
     * Files sent straight to a device, belonging to no note (see {@link Drop}): one row per sending, either way -
     * who it is to or from, by the address the device is filed under, its name as it was then, where it stands,
     * which offer of it is the latest, and when it was last tried - and one per file in it. The id a sending
     * travels under is its own column, so one device's id can never land on another's sending. A file's bytes are
     * kept in the pad's own folder like an attachment's, sealed the same way, under an id made here; `theirs` is
     * the sender's name for it, `here` says the bytes are in, `relay` that its pieces are to go up to a relay.
     */
    private static final String[] TRANSFERS={
        "CREATE TABLE IF NOT EXISTS transfers(id TEXT PRIMARY KEY,wire TEXT NOT NULL,way TEXT NOT NULL,address TEXT NOT NULL,"
        +"name TEXT NOT NULL,state INTEGER NOT NULL,at INTEGER NOT NULL,revision INTEGER NOT NULL DEFAULT 1,"
        +"tried INTEGER NOT NULL DEFAULT 0,tries INTEGER NOT NULL DEFAULT 0,seen INTEGER NOT NULL DEFAULT 0)",
        "CREATE UNIQUE INDEX IF NOT EXISTS transfers_wire ON transfers(wire,address,way)",
        "CREATE TABLE IF NOT EXISTS transferred(id TEXT PRIMARY KEY,batch TEXT NOT NULL,theirs TEXT NOT NULL DEFAULT '',"
        +"name TEXT NOT NULL,kind TEXT NOT NULL,bytes INTEGER NOT NULL,manifest TEXT NOT NULL DEFAULT '',"
        +"here INTEGER NOT NULL DEFAULT 0,relay INTEGER NOT NULL DEFAULT 0,tried INTEGER NOT NULL DEFAULT 0,"
        +"tries INTEGER NOT NULL DEFAULT 0)",
        "CREATE INDEX IF NOT EXISTS transferred_batch ON transferred(batch)",
    };

    /**
     * One person on every device they own (see {@link Persons}). A device of yours is filed with whose it is
     * (`person`), which device of yours told this one about it (`via`, empty for one paired here), whether it is
     * yours no longer (`gone`), and when its name was decided (`named`), so the later decision stands whichever
     * card arrives first. `persons` is one row per person known, with what they were known by before; it is for
     * the steps after this one, where somebody else's devices are known as one person too.
     */
    private static final String[] PERSONS={
        "ALTER TABLE addresses ADD COLUMN person TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE addresses ADD COLUMN via TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE addresses ADD COLUMN gone INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE addresses ADD COLUMN named INTEGER NOT NULL DEFAULT 0",
        "CREATE TABLE IF NOT EXISTS persons(id TEXT PRIMARY KEY,name TEXT NOT NULL DEFAULT '',changed INTEGER NOT NULL DEFAULT 0,"
        +"aliases TEXT NOT NULL DEFAULT '',mine INTEGER NOT NULL DEFAULT 0)",
        "CREATE INDEX IF NOT EXISTS addresses_person ON addresses(person)",
    };

    /**
     * The size one note is read at (see {@link Reading}): which rung of the ladder, kept on this device as its
     * colour is and never sent anywhere. -1 is none of its own, which is what every note already here starts
     * as, so each is read at the size it always was.
     */
    private static final String[] RUNGS={
        "ALTER TABLE notes ADD COLUMN rung INTEGER NOT NULL DEFAULT -1",
    };

    /**
     * Who wrote what in each note (see {@link Writers}), and the colour each writer is drawn in: kept on this device
     * and never sent anywhere, beside the notes rather than in their rows, so nothing that reads or sends a note sees
     * it. A note with no row here is nobody's, which is what every note already here starts as: drawn as it always was.
     * The trace names the text the runs are about, so runs that no longer fit their note are never drawn over it.
     */
    private static final String[] WRITERS={
        "CREATE TABLE IF NOT EXISTS writers(note TEXT PRIMARY KEY,runs TEXT NOT NULL DEFAULT '',trace TEXT NOT NULL DEFAULT '')",
        "CREATE TABLE IF NOT EXISTS inks(writer TEXT PRIMARY KEY,colour INTEGER NOT NULL DEFAULT 0)",
    };

    /**
     * Notes and collections, nested as deep as anybody likes (see {@link Things} and docs/HOME.md).
     *
     * <p>Three fixed levels become two kinds of thing. Every collection and every book becomes a row here, under
     * its own id: a collection under {@link Things#HOME}, a book under the collection it was in - with its name,
     * colour, place, favourite, bin and archive flags, where it came from and how long it waits. Nothing moves and
     * nothing is deleted: `collections` and `books` stay as they were, and are not read again. A note keeps its
     * place in the tree in its own row, where everything that reads a note already looks (decision 8): its `book`
     * is now whichever collection it is in. What a note gains is what the new Home shows of it - an icon, a
     * picture, a place in the dock - none of which it has yet. The first favourites on the shelves go into the
     * dock, in the order the Favourites place listed them: collections, then books, then notes, each by place.
     * And a file kept with a book is kept with a collection, which that book now is.
     */
    private static final String THINGS_TABLE="CREATE TABLE IF NOT EXISTS things(id TEXT PRIMARY KEY,parent TEXT NOT NULL,"
        +"kind TEXT NOT NULL DEFAULT 'collection',name TEXT NOT NULL DEFAULT '',icon TEXT NOT NULL DEFAULT '',"
        +"image TEXT NOT NULL DEFAULT '',tint INTEGER NOT NULL DEFAULT 0,ordinal INTEGER NOT NULL DEFAULT 0,"
        +"favourite INTEGER NOT NULL DEFAULT 0,dock INTEGER NOT NULL DEFAULT 0,archived INTEGER NOT NULL DEFAULT 0,"
        +"binned INTEGER NOT NULL DEFAULT 0,theirs INTEGER NOT NULL DEFAULT 0,origin TEXT NOT NULL DEFAULT '',"
        +"pause INTEGER NOT NULL DEFAULT 0,revision INTEGER NOT NULL DEFAULT 0,made INTEGER NOT NULL DEFAULT 0,"
        +"updated INTEGER NOT NULL DEFAULT 0,gone INTEGER NOT NULL DEFAULT 0)";
    /** A live favourite, as the dock is filled from: on the shelves, neither binned nor archived. */
    private static final String LIVE_THING="favourite=1 AND binned=0 AND archived=0";
    private static final String[] THINGS={
        THINGS_TABLE,
        "CREATE INDEX IF NOT EXISTS things_parent ON things(parent,binned,archived,ordinal)",
        // Or ignore: a collection and a book under one id would lose one of them here, and the counts taken
        // around this step (see NoteStore.onUpgrade) refuse the whole step if that happens.
        "INSERT OR IGNORE INTO things(id,parent,kind,name,tint,ordinal,favourite,archived,binned,theirs,origin,pause,made,updated)"
            +" SELECT id,'"+Things.HOME+"','collection',name,colour,place,pinned,archived,deleted,theirs,origin,pause,updated,updated FROM collections",
        "INSERT OR IGNORE INTO things(id,parent,kind,name,tint,ordinal,favourite,archived,binned,theirs,origin,pause,made,updated)"
            +" SELECT id,collection,'collection',name,colour,place,pinned,archived,deleted,theirs,origin,pause,updated,updated FROM books",
        "ALTER TABLE notes ADD COLUMN icon TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE notes ADD COLUMN image TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE notes ADD COLUMN dock INTEGER NOT NULL DEFAULT 0",
        "UPDATE files SET held='collection' WHERE held='book'",
        // The dock's first fill: each live favourite counted after every one that comes before it - a collection
        // on Home before one inside another, then by place, then by id so that no two ever tie.
        "UPDATE things SET dock=(SELECT COUNT(*) FROM things o WHERE o."+LIVE_THING.replace(" AND "," AND o.")
            +" AND ((o.parent<>'"+Things.HOME+"')<(things.parent<>'"+Things.HOME+"')"
            +" OR ((o.parent<>'"+Things.HOME+"')=(things.parent<>'"+Things.HOME+"')"
            +" AND (o.ordinal<things.ordinal OR (o.ordinal=things.ordinal AND o.id<=things.id))))) WHERE "+LIVE_THING,
        "UPDATE notes SET dock=(SELECT COUNT(*) FROM things WHERE "+LIVE_THING+")+(SELECT COUNT(*) FROM notes o"
            +" WHERE o.pinned=1 AND o.deleted=0 AND o.archived=0 AND (o.place<notes.place OR (o.place=notes.place AND o.id<=notes.id)))"
            +" WHERE pinned=1 AND deleted=0 AND archived=0",
        "UPDATE things SET dock=0 WHERE dock>"+Things.DOCK_PHONE,
        "UPDATE notes SET dock=0 WHERE dock>"+Things.DOCK_PHONE,
    };

    /** The version the notebook is at just before collections and books become things. */
    static final int BEFORE_THINGS=29;
    /** What the move to things must leave as it found it, by name, in the order the counts below are taken. */
    static final String[] COUNTED={"collections and books","notes","files","shares","favourites","binned","archived"};
    /** Counted at {@link #BEFORE_THINGS}. */
    static final String[] COUNT_BEFORE={
        "SELECT (SELECT COUNT(*) FROM collections)+(SELECT COUNT(*) FROM books)",
        "SELECT COUNT(*) FROM notes",
        "SELECT COUNT(*) FROM files",
        "SELECT COUNT(*) FROM shares",
        "SELECT (SELECT COUNT(*) FROM collections WHERE pinned=1)+(SELECT COUNT(*) FROM books WHERE pinned=1)+(SELECT COUNT(*) FROM notes WHERE pinned=1)",
        "SELECT (SELECT COUNT(*) FROM collections WHERE deleted=1)+(SELECT COUNT(*) FROM books WHERE deleted=1)+(SELECT COUNT(*) FROM notes WHERE deleted=1)",
        "SELECT (SELECT COUNT(*) FROM collections WHERE archived=1)+(SELECT COUNT(*) FROM books WHERE archived=1)+(SELECT COUNT(*) FROM notes WHERE archived=1)",
    };
    /** The same things counted just after the step to things, and nothing later. */
    static final String[] COUNT_AFTER={
        "SELECT COUNT(*) FROM things",
        "SELECT COUNT(*) FROM notes",
        "SELECT COUNT(*) FROM files",
        "SELECT COUNT(*) FROM shares",
        "SELECT (SELECT COUNT(*) FROM things WHERE favourite=1)+(SELECT COUNT(*) FROM notes WHERE pinned=1)",
        "SELECT (SELECT COUNT(*) FROM things WHERE binned=1)+(SELECT COUNT(*) FROM notes WHERE deleted=1)",
        "SELECT (SELECT COUNT(*) FROM things WHERE archived=1)+(SELECT COUNT(*) FROM notes WHERE archived=1)",
    };

    /**
     * Received files are kept on Home (docs/HOME.md, step 2 and decision 6). The drop box kept them in a place of its
     * own, in no note; with the drop box gone, every received file that is here becomes a file kept on Home under the
     * same id - its bytes are already in the shed under that id, so nothing is copied - saying which device it came
     * from, when it came, and whether it is new: new where nobody has looked at its sending yet, as the drop box's
     * count said. Its row among the sendings is not deleted but marked as moved: the sending stays behind it to answer
     * the device that sent it if it offers again, and from here nothing lists it or keeps its bytes for it.
     */
    private static final String RECEIVED_HERE="FROM transferred f JOIN transfers t ON t.id=f.batch WHERE t.way='in' AND f.here=1";
    private static final String[] HOME_FILES={
        "ALTER TABLE files ADD COLUMN fresh INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE transferred ADD COLUMN moved INTEGER NOT NULL DEFAULT 0",
        "INSERT OR IGNORE INTO files(id,note,name,kind,bytes,added,place,held,origin,fresh)"
            +" SELECT f.id,'"+Things.HOME+"',f.name,f.kind,f.bytes,t.at,-t.at,'collection',t.address,CASE WHEN t.seen=0 THEN 1 ELSE 0 END "
            +RECEIVED_HERE+" AND f.moved=0",
        // Only where its file on Home is there: a row ignored above is left unmoved, and the counts below refuse the step.
        "UPDATE transferred SET moved=1 WHERE here=1 AND moved=0 AND batch IN (SELECT id FROM transfers WHERE way='in')"
            +" AND id IN (SELECT id FROM files WHERE note='"+Things.HOME+"' AND held='collection')",
    };

    /** The version the notebook is at just before received files move to Home. */
    static final int BEFORE_HOME_FILES=30;
    /** What that move must leave as it found it: every received file here, now on Home; one file row more for each; as many new. */
    static final String[] COUNTED_HOME={"received files","files","new"};
    static final String[] COUNT_BEFORE_HOME={
        "SELECT COUNT(*) "+RECEIVED_HERE,
        "SELECT (SELECT COUNT(*) FROM files)+(SELECT COUNT(*) "+RECEIVED_HERE+")",
        "SELECT COUNT(*) "+RECEIVED_HERE+" AND t.seen=0",
    };
    static final String[] COUNT_AFTER_HOME={
        "SELECT COUNT(*) "+RECEIVED_HERE+" AND f.moved=1 AND f.id IN (SELECT id FROM files WHERE note='"+Things.HOME+"' AND held='collection')",
        "SELECT COUNT(*) FROM files",
        "SELECT COUNT(*) FROM files WHERE note='"+Things.HOME+"' AND fresh=1",
    };

    /**
     * A step that moves something, and so counts what it must leave as it found it, before and after, inside the
     * upgrade's own transaction (see NoteStore.onUpgrade and docs/HOME.md, decision 11): any difference refuses the
     * whole upgrade, which leaves the notebook where it was for the build before to open.
     */
    static final class Checked {
        /** The version the step starts from, and what is said when it is refused and when it is done. */
        final int from; final String refused,done; final String[] counted,before,after;
        Checked(int from,String refused,String done,String[] counted,String[] before,String[] after) {
            this.from=from;this.refused=refused;this.done=done;this.counted=counted;this.before=before;this.after=after;
        }
    }
    static final List<Checked> CHECKED=List.of(
        new Checked(BEFORE_THINGS,"The notebook could not be moved to notes and collections: ","moved to notes and collections",
            COUNTED,COUNT_BEFORE,COUNT_AFTER),
        new Checked(BEFORE_HOME_FILES,"The received files could not be moved to Home: ","received files moved to Home",
            COUNTED_HOME,COUNT_BEFORE_HOME,COUNT_AFTER_HOME));

    /** Which build opens a notebook left at this version, for the words a refused upgrade ends with. */
    static String stillOpens(int version) {
        return version<=BEFORE_THINGS?"Mininotes 0.1.041":version<=BEFORE_HOME_FILES?"Mininotes 0.2.001":"the Mininotes before this one";
    }

    /** What differs between the counts before and after, said by name and number; null where nothing does. */
    static String differs(long[] before,long[] after){return differs(COUNTED,before,after);}

    static String differs(String[] names,long[] before,long[] after) {
        if(before==null||after==null||before.length!=names.length||after.length!=names.length)
            return "the counts could not be taken";
        StringBuilder said=new StringBuilder();
        for(int at=0;at<names.length;at++)
            if(before[at]!=after[at])said.append(said.length()==0?"":", ").append(names[at]).append(' ')
                .append(before[at]).append(" became ").append(after[at]);
        return said.length()==0?null:said.toString();
    }

    /** The counts as one line for a log: numbers only, never a name or a word of a note. */
    static String counted(long[] counts){return counted(COUNTED,counts);}

    static String counted(String[] names,long[] counts) {
        StringBuilder said=new StringBuilder();
        for(int at=0;at<names.length&&counts!=null&&at<counts.length;at++)
            said.append(at==0?"":", ").append(names[at]).append(' ').append(counts[at]);
        return said.toString();
    }

    /**
     * Where each icon stands on its grid, on this device (see {@link Layout}; docs/HOME.md, decision 39). The owner asked to
     * move things freely on Home, not have them sorted one after the other: a note, a collection and a file each keep
     * the cell they were put in, as one number, row by {@link Layout#WIDEST} and column. Every row already here starts
     * with none, which is drawn exactly as the grid was drawn before - one after the other, in the owner's order - until
     * something on that grid is first moved.
     */
    private static final String[] CELLS={
        "ALTER TABLE things ADD COLUMN cell INTEGER NOT NULL DEFAULT -1",
        "ALTER TABLE notes ADD COLUMN cell INTEGER NOT NULL DEFAULT -1",
        "ALTER TABLE files ADD COLUMN cell INTEGER NOT NULL DEFAULT -1",
    };

    /**
     * Which page of Home each icon stands on, on this device (see {@link Layout#pages}; docs/HOME.md, decisions 45-50):
     * Home is pages in every direction now, and a cell is a cell on a page. Every row already here has none, which
     * {@link Layout#pages} reads as the one long grid it was - the centre page while it fits, the pages below for the rest.
     */
    private static final String[] PAGES={
        "ALTER TABLE things ADD COLUMN page INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE notes ADD COLUMN page INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE files ADD COLUMN page INTEGER NOT NULL DEFAULT 0",
    };

    /** STEPS[i] upgrades a database at version i+1 to version i+2. */
    private static final String[][] STEPS={
        {VIEW_INDEX},
        // Pages gain the book they sit on. Existing notes default into the first book rather than being moved
        // by a statement that could half-run, and the two seed rows are ignored if a fresh install made them.
        {COLLECTIONS,BOOKS,SHARES,BOOK_COLUMN,FIRST_COLLECTION_ROW,FIRST_BOOK_ROW,BOOKS_INDEX,PAGES_INDEX},
        // 3 -> 4: the addresses you share with become a list of their own.
        {ADDRESSES},
        // 4 -> 5: every collection, book and page carries the place the reader dragged it to.
        PLACES,
        // 5 -> 6: the archive and the bin, as two flags every thing carries.
        AWAY,
        // 6 -> 7: a colour of its own, for anything that wants one.
        COLOURS,
        // 7 -> 8: what has reached which address, so what is still owed can be shown.
        OUTBOX,
        // 8 -> 9: files kept with a note.
        FILES,
        // 9 -> 10: what a paired device is, and which notes came from one.
        PAIRED,
        // 10 -> 11: every version a note has had.
        VERSIONS,
        // 11 -> 12: a file can be kept with a collection or a book, not only with a note.
        FILES_ANYWHERE,
        // 12 -> 13: a shelf can have come from somebody, and this phone can stop taking what they send.
        SHARED_IN,
        // 13 -> 14: an offer taken up, said again until the phone that made it answers.
        ACCEPTING,
        // 14 -> 15: a collection or a book can be a favourite too.
        FAVOURITES,
        // 15 -> 16: a share becomes a membership: a level, a device, and when it was decided.
        MEMBERS,
        // 16 -> 17: how long a thing waits after the writing stops before it goes.
        PAUSE,
        // 17 -> 18: handed to the network is not the same as arrived.
        HANDED,
        // 18 -> 19: and arrived is not the same as agreed.
        AGREED,
        // 19 -> 20: what this phone itself may do with what is shared with it.
        STANDING,
        // 20 -> 21: stopped for now is not the same as left.
        LEAVING,
        // 21 -> 22: what was given at pairing time is what the offer said.
        GIVEN,
        // 22 -> 23: what this device carries for two others that are not on at the same time.
        CARRIED,
        // 23 -> 24: the files kept with a shared note go with it.
        FILES_TRAVEL,
        // 24 -> 25: what the PC holds for its owner's phones until they collect it.
        HELD,
        // 25 -> 26: files sent straight to a device, and received, belonging to no note.
        TRANSFERS,
        // 26 -> 27: one person on every device they own, and two names for each device.
        PERSONS,
        // 27 -> 28: a note can be read at a size of its own.
        RUNGS,
        // 28 -> 29: who wrote what, and the colour each writer is drawn in.
        WRITERS,
        // 29 -> 30: collections and books become things, nested as deep as anybody likes.
        THINGS,
        // 30 -> 31: received files are kept on Home, new until opened.
        HOME_FILES,
        // 31 -> 32: an icon stays in the cell it was put in, with empty cells left empty.
        CELLS,
        // 32 -> 33: Home is pages in every direction, and an icon stands on one.
        PAGES,
    };

    /** The one collection and the one book a pad cannot be without, for a restore that carries neither. */
    static String firstCollection(){return FIRST_COLLECTION_ROW;}
    static String firstBook(){return FIRST_BOOK_ROW;}

    /** Statements for a database created directly at the current {@link #VERSION}. */
    static List<String> create() {
        List<String> statements=new ArrayList<>(List.of(NOTES_TABLE,VIEW_INDEX,COLLECTIONS,BOOKS,SHARES,
            "ALTER TABLE notes ADD COLUMN book TEXT NOT NULL DEFAULT '"+FIRST_BOOK+"'",
            FIRST_COLLECTION_ROW,FIRST_BOOK_ROW,BOOKS_INDEX,PAGES_INDEX,ADDRESSES));
        Collections.addAll(statements,PLACES);
        Collections.addAll(statements,AWAY);
        Collections.addAll(statements,COLOURS);
        Collections.addAll(statements,OUTBOX);
        Collections.addAll(statements,FILES);
        Collections.addAll(statements,PAIRED);
        Collections.addAll(statements,VERSIONS);
        Collections.addAll(statements,FILES_ANYWHERE);
        Collections.addAll(statements,SHARED_IN);
        Collections.addAll(statements,ACCEPTING);
        Collections.addAll(statements,FAVOURITES);
        Collections.addAll(statements,MEMBERS);
        Collections.addAll(statements,PAUSE);
        Collections.addAll(statements,HANDED);
        Collections.addAll(statements,AGREED);
        Collections.addAll(statements,STANDING);
        Collections.addAll(statements,LEAVING);
        Collections.addAll(statements,GIVEN);
        Collections.addAll(statements,CARRIED);
        Collections.addAll(statements,FILES_TRAVEL);
        Collections.addAll(statements,HELD);
        Collections.addAll(statements,TRANSFERS);
        Collections.addAll(statements,PERSONS);
        Collections.addAll(statements,RUNGS);
        Collections.addAll(statements,WRITERS);
        // After the first collection and book are made, so they become things as every upgraded one does.
        Collections.addAll(statements,THINGS);
        Collections.addAll(statements,HOME_FILES);
        Collections.addAll(statements,CELLS);
        Collections.addAll(statements,PAGES);
        return statements;
    }

    /** Ordered statements that move an existing database from one version to another. */
    static List<String> upgrade(int from,int to) {
        if(from<1||to<1||from>VERSION||to>VERSION)throw new IllegalArgumentException("Unknown notebook schema version");
        if(from>to)throw new IllegalArgumentException("This notebook was written by a newer version of Mininotes");
        List<String> statements=new ArrayList<>();
        for(int version=from;version<to;version++)Collections.addAll(statements,STEPS[version-1]);
        return statements;
    }

    private SchemaMigrations(){}
}
