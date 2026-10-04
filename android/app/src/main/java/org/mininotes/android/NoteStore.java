// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.io.File;

/** Local storage for the pad. Every method blocks on disk and belongs on {@link Background}, not the interface thread. */
final class NoteStore extends SQLiteOpenHelper implements Home.Shelf {
    /** Characters of a page read for a list row. The row shows three lines; the rest stays on disk. */
    static final int PREVIEW=280;

    /** A collection or a book: both are a named thing holding other things. */
    static final class Shelf {
        final String id,name; final int count,colour;
        /** True when this shelf was built because somebody shared into it, and which address that was. */
        final boolean theirs; final String origin;
        Shelf(String id,String name,int count,int colour){this(id,name,count,colour,false,"");}
        Shelf(String id,String name,int count,int colour,boolean theirs,String origin) {
            this.id=id;this.name=name;this.count=count;this.colour=colour;
            this.theirs=theirs;this.origin=origin==null?"":origin;
        }
    }

    /** One line of the tree, with how deep it sits and what holds it. */
    static final class Branch {
        /**
         * INBOX is what others share with you; ARCHIVE is what you put away and BIN what you deleted;
         * NOTICE is a line that only explains something. FAVOURITES is the place that lists what is kept to
         * hand - a place like the archive and the bin, not a collection: nothing lives in it, nothing is
         * moved into it, and so nothing about who can read a thing changes by its being there. DROPS is the drop
         * box, a place of the same sort: the files sent straight to this device and from it, in no note (see Drop).
         * FILE is a file kept with a collection or with Home, shown among what it is kept with (see {@link NoteStore#contents}):
         * its id is the file's, its parent what keeps it, its detail its type and size, its origin the device it came from.
         */
        enum Kind { LIBRARY, COLLECTION, BOOK, PAGE, INBOX, ARCHIVE, BIN, NOTICE, FAVOURITES, DROPS, FILE }
        final Kind kind; final String id,parent,name,detail; final int depth,shared,colour,waiting; final boolean holds;
        final Sharing.State state;
        /** This phone has stopped taking this thing in, or something that holds it. Set as it is listed. */
        boolean paused;
        /** A favourite, so the thing can say so where it lives and not only where favourites are gathered. */
        boolean kept;
        /** A file that came from another device and has not been opened here yet: it wears a new badge. FILE lines only. */
        boolean fresh;
        /** The cell it was put in on its grid, on this device, or {@link Layout#NONE}: see {@link Layout}. */
        int cell=Layout.NONE;
        /** The page of Home its cell is on, on this device, or {@link Layout#NO_PAGE}: see {@link Layout#pages}. */
        int page=Layout.NO_PAGE;
        /**
         * The icon a note or a collection wears, by its name in Lucide's set (see {@link Icons}), or empty for its default:
         * a note's glyph ({@link Icons#NOTE}), a collection's mini-grid of what is inside. A name this build's set does not
         * have - from a later set - is drawn as the default too ({@link Icons#draw} says false). Set as it is listed
         * (see {@link NoteStore#dress}); empty on every other kind of line.
         */
        String icon="";
        /**
         * Its picture - a square thumbnail, WebP, PNG or JPEG, at most {@link Thumb#MOST} bytes - or null for none. Where
         * there is one it fills the round square in place of the icon. Set as it is listed, with the icon.
         */
        byte[] image;
        /**
         * The worst of where everything under it stands with each device it reaches (see {@link #standing}), set
         * as it is listed; null where nobody is owed it or has it, or for a line made by hand.
         */
        SyncMark standing;
        /** The mark it wears on its tile, card or line: the same rule as the line under a note's title. */
        SyncMark mark() {
            return SyncMark.of(paused,state!=null&&state!=Sharing.State.HERE,false,
                standing!=null?standing:waiting>0?SyncMark.WAITING:null);
        }
        /** Who shared this with you, when somebody did. Empty for everything you made yourself. */
        final String origin;
        Branch(Kind kind,String id,String parent,String name,String detail,int depth,int shared,boolean holds) {
            this(kind,id,parent,name,detail,depth,shared,holds,Tint.NONE);
        }
        Branch(Kind kind,String id,String parent,String name,String detail,int depth,int shared,boolean holds,int colour) {
            this(kind,id,parent,name,detail,depth,shared,holds,colour,0);
        }
        /** {@code waiting} is how many notes inside this one an address has not been given yet. */
        Branch(Kind kind,String id,String parent,String name,String detail,int depth,int shared,boolean holds,
               int colour,int waiting) {
            this(kind,id,parent,name,detail,depth,shared,holds,colour,waiting,Sharing.State.HERE);
        }
        /** {@code state} is where this thing stands: here, on your devices, with somebody else, or from them. */
        Branch(Kind kind,String id,String parent,String name,String detail,int depth,int shared,boolean holds,
               int colour,int waiting,Sharing.State state) {
            this(kind,id,parent,name,detail,depth,shared,holds,colour,waiting,state,"");
        }
        Branch(Kind kind,String id,String parent,String name,String detail,int depth,int shared,boolean holds,
               int colour,int waiting,Sharing.State state,String origin) {
            this.kind=kind;this.id=id;this.parent=parent;this.name=name;this.detail=detail;
            this.depth=depth;this.shared=shared;this.holds=holds;this.colour=colour;this.waiting=waiting;
            this.state=state;this.origin=origin==null?"":origin;
        }
        /** The sharing level this line is, or null for lines that are not yours to share. */
        Sharing.Scope scope() {
            switch(kind) {
                case LIBRARY: return Sharing.Scope.LIBRARY;
                case COLLECTION: return Sharing.Scope.COLLECTION;
                case BOOK: return Sharing.Scope.BOOK;
                case PAGE: return Sharing.Scope.PAGE;
                default: return null;
            }
        }
    }

    static final class Note {
        String id=UUID.randomUUID().toString(), title="", body="", notebook="Personal", preview="",
            book=SchemaMigrations.FIRST_BOOK;
        boolean pinned, deleted, archived;
        /** Which colour the reader gave this page, or {@link Tint#NONE}. */
        int colour=Tint.NONE;
        /** The rung this page is read at on this device, or {@link Reading#NONE} for the device's own. */
        int rung=Reading.NONE;
        /** False for a list row, which carries a truncated preview in place of the body. */
        boolean complete=true;
        long updated=System.currentTimeMillis();
        /** Where the reader put this page in its book, smallest first. A new one starts above everything. */
        long place=-System.currentTimeMillis();
        /**
         * How many times this note has been written, counted rather than timed. It is what decides whether
         * a version that arrives descends from this one or diverged from it — a clock cannot say that, and
         * two phones whose clocks disagree would otherwise lose somebody's writing.
         */
        long revision;
        /** True when this note came from somebody else rather than being written here. */
        boolean theirs;
        /** Which address it came from, empty when it is ours. */
        String origin="";
        /** What the sender said this end may do with it: false is read it, true is read and write it. */
        boolean writes;
        /**
         * Who wrote each letter of the body, where the page that wrote it knows (see {@link Writers}); null where it
         * does not, and then the letters that are new since the notebook last held this note are {@link #by}'s. Kept on
         * this device only: never in {@link #json}, never sent, and not carried by {@link #copy}, whose body can change.
         */
        Writers writers;
        /** Whose the letters new to this note are, where {@link #writers} does not say: nobody known, unless told. */
        String by=Writers.UNKNOWN;
        Note copy() {
            Note c=new Note();c.id=id;c.title=title;c.body=body;c.notebook=notebook;c.preview=preview;c.book=book;
            c.pinned=pinned;c.deleted=deleted;c.archived=archived;c.complete=complete;c.updated=updated;
            c.place=place;c.colour=colour;c.rung=rung;c.revision=revision;c.theirs=theirs;c.origin=origin;c.writes=writes;
            return c;
        }
        JSONObject json() throws JSONException {
            return new JSONObject().put("id",id).put("title",title).put("body",body).put("tag",notebook)
                .put("book",book).put("pinned",pinned).put("deleted",deleted).put("archived",archived)
                .put("updated",updated).put("place",place).put("colour",colour).put("rung",rung).put("revision",revision);
        }
        /**
         * What a note is called on a shelf: its title, or - for one that has none - its first line, so
         * that a note written before titles existed is not a row of "Untitled".
         */
        String heading() {
            String head=title.trim();
            if(head.isEmpty()){int line=preview.indexOf('\n');head=(line<0?preview:preview.substring(0,line)).trim();}
            return head.isEmpty()?"Untitled":head;
        }
        String rest() {
            if(RichDocument.marked(preview))return preview.startsWith(RichDocument.PREFIX+"xlsx")?"Spreadsheet":preview.startsWith(RichDocument.PREFIX+"image")?"Layered image":"Word document";
            if(!title.trim().isEmpty())return preview.replace('\n',' ').trim();
            int line=preview.indexOf('\n');
            return line<0?"":preview.substring(line+1).replace('\n',' ').trim();
        }
    }

    private final Context where;
    /**
     * The notebook's key while it is unlocked; null when it has no lock. SQLCipher takes an empty key as no
     * encryption at all, so a notebook without a lock is the same plain file it always was.
     */
    private static volatile byte[] key;
    static void unlock(byte[] given){key=given==null?null:given.clone();only=null;}
    static byte[] key(){byte[] k=key;return k==null?null:k.clone();}
    /** Locked again: the open notebook closed, and its key let go. */
    static synchronized void lockAgain(){if(only!=null){try{only.close();}catch(RuntimeException busy){/* closed as it can be */}}only=null;key=null;}
    static {
        // SQLCipher's own SQLite. Only on the phone: the PC's build of this class has a SQLite of its own.
        try{System.loadLibrary("sqlcipher");}catch(Throwable notHere){/* not Android */}
    }

    NoteStore(Context c){this(c,key);}
    NoteStore(Context c,byte[] with){this(c,with,"mininotes.db");}
    /** Another file of the same kind: the copy being checked while the lock goes on or comes off. */
    NoteStore(Context c,byte[] with,String file) {
        // The key handed over in SQLCipher's raw-key form, x'…', so it is used as it is and not derived again.
        super(c,file,with==null?new byte[0]:Vault.pragma(with).getBytes(java.nio.charset.StandardCharsets.US_ASCII),null,SchemaMigrations.VERSION,0,null,null,false);
        where=c.getApplicationContext();
    }

    /**
     * The one notebook this process has.
     *
     * <p>There used to be one per screen, made when the screen was and closed when it went. That was enough
     * while the screen was the only thing that ever wrote — but a note can now arrive with no screen there
     * at all, and whatever takes it in has to be writing in the same notebook the screen will open, not in a
     * second copy of it that the first one closes underneath it. Never closed: it goes when the process does.
     */
    private static NoteStore only;
    static synchronized NoteStore of(Context c) {
        if(only==null){
            if(key==null&&new java.io.File(c.getApplicationContext().getFilesDir(),"vault.key").isFile())throw new IllegalStateException("The notebook is locked.");
            only=new NoteStore(c.getApplicationContext());
        }
        return only;
    }

    // ---- the tree: notes and collections, nested as deep as anybody likes (see Things) ---------------------------

    /**
     * Whether an id names Home: {@link Things#HOME} as the notebook writes it, empty where nothing is said, and
     * {@link Sharing#EVERYTHING}, which is what a line on Home names as what holds it.
     */
    static boolean home(String id){return id==null||id.isEmpty()||Things.HOME.equals(id)||Sharing.EVERYTHING.equals(id);}

    /** Whether a kind is a collection. There is one kind now: a book is a collection inside a collection. */
    private static boolean shelf(Branch.Kind kind){return kind==Branch.Kind.COLLECTION||kind==Branch.Kind.BOOK;}

    /** A collection as the tree holds it. */
    private static final class Row {
        final String id,parent,name,origin; final int tint; final boolean theirs,away;
        Row(String id,String parent,String name,int tint,boolean theirs,String origin,boolean away) {
            this.id=id;this.parent=home(parent)?Things.HOME:parent;this.name=name==null?"":name;this.tint=tint;
            this.theirs=theirs;this.origin=origin==null?"":origin;this.away=away;
        }
    }

    /**
     * Every collection as it stands, read once: what holds each, what it is called, and which are put away. A call that
     * needs to know where things are asks this rather than the notebook level by level - walking up from a note three
     * collections down would otherwise be a question per level, per note.
     */
    private static final class Tree {
        /** Each collection's parent, as {@link Things} reads a path from. */
        final Map<String,String> parents=new HashMap<>();
        /** Every collection, in the owner's order: by where each was put, and the newest first where none was. */
        final Map<String,Row> rows=new java.util.LinkedHashMap<>();
        /** What each collection holds of other collections, and what Home does, in that order. */
        final Map<String,List<String>> children=new HashMap<>();
        /** The collections above a collection, from Home down. */
        List<String> above(String id){return Things.above(parents,id);}
        /** The collections above a note in {@code parent}, from Home down: that one, and everything above it. */
        List<String> aboveIn(String parent){return home(parent)?new ArrayList<>():Things.path(parents,parent);}
        /** Whether none of these is in the bin or the archive. What is inside one that is has gone away with it. */
        boolean live(List<String> above){for(String one:above){Row row=rows.get(one);if(row!=null&&row.away)return false;}return true;}
        String name(String id){Row row=rows.get(id);return row==null?"":row.name;}
        /** The names along a path, as a place is said: "Kitchen › Recipes". */
        String names(List<String> above) {
            StringBuilder said=new StringBuilder();
            for(String one:above){String called=name(one);said.append(said.length()==0?"":" › ").append(called.isEmpty()?"a collection that is gone":called);}
            return said.toString();
        }
        /** What one collection holds of other collections that are on the shelves, in the owner's order. */
        List<String> live(String parent) {
            List<String> out=new ArrayList<>();
            for(String one:children.getOrDefault(home(parent)?Things.HOME:parent,java.util.Collections.<String>emptyList()))
                if(!rows.get(one).away)out.add(one);
            return out;
        }
        /** A collection and every collection inside it however deep, the outer first. Never round a loop. */
        List<String> within(String id) {
            List<String> all=new ArrayList<>();Set<String> seen=new HashSet<>();
            java.util.Deque<String> next=new java.util.ArrayDeque<>();next.add(id);
            while(!next.isEmpty()) {
                String one=next.poll();
                if(!seen.add(one))continue;
                all.add(one);
                next.addAll(children.getOrDefault(one,java.util.Collections.<String>emptyList()));
            }
            return all;
        }
    }

    private Tree tree(){return tree(getReadableDatabase());}

    private static Tree tree(SQLiteDatabase db) {
        Tree tree=new Tree();
        try(Cursor c=db.rawQuery("SELECT id,parent,name,tint,theirs,origin,binned,archived FROM things ORDER BY ordinal ASC, updated DESC",null)) {
            while(c.moveToNext()) {
                Row row=new Row(c.getString(0),c.getString(1),c.getString(2),c.getInt(3),c.getInt(4)==1,c.getString(5),
                    c.getInt(6)==1||c.getInt(7)==1);
                tree.rows.put(row.id,row);tree.parents.put(row.id,row.parent);
                tree.children.computeIfAbsent(row.parent,any->new ArrayList<>()).add(row.id);
            }
        }
        return tree;
    }

    /** Every collection's parent, and each of these notes' too: what {@link Things} works a path out from. */
    Map<String,String> parents(String... notes) {
        Map<String,String> all=new HashMap<>(tree().parents);
        for(String note:notes){String in=parentOf("notes","book",note);if(in!=null)all.put(note,home(in)?Things.HOME:in);}
        return all;
    }

    /** The collections above a note or a collection, from Home down; empty for one on Home. */
    List<String> above(String id){return above(tree(),id);}

    private List<String> above(Tree tree,String id) {
        if(id==null||home(id))return new ArrayList<>();
        if(tree.parents.containsKey(id))return tree.above(id);
        String in=parentOf("notes","book",id);
        return in==null?new ArrayList<>():tree.aboveIn(in);
    }

    /** The same, and then the thing itself: what a sharing rule is looked for along (see {@link Sharing#covers}). */
    List<String> pathOf(String id){List<String> all=above(id);all.add(id);return all;}

    /**
     * The collections above a note as they travel in its parcel, from Home down: each with its name, its icon, its
     * colour and where it is kept among what is beside it (see {@link Parcel.Step}).
     */
    List<Parcel.Step> steps(List<String> above) {
        List<Parcel.Step> out=new ArrayList<>();
        for(String one:above)
            try(Cursor c=getReadableDatabase().query("things",new String[]{"name","icon","tint","ordinal"},"id=?",new String[]{one},null,null,null,"1")) {
                out.add(c.moveToFirst()?new Parcel.Step(one,c.getString(0),c.getString(1),c.getInt(2),c.getLong(3))
                    :new Parcel.Step(one,"","",Tint.NONE,0));
            }
        return out;
    }

    /**
     * The level a device from before trees calls a thing by: a note PAGE, a collection by how deep it sits (see
     * {@link Things#oldScope}); null for a collection deeper than its three levels, which it has no word for.
     */
    Sharing.Scope oldScopeOf(Sharing.Scope scope,String target) {
        if(scope==null||scope==Sharing.Scope.PAGE||scope==Sharing.Scope.LIBRARY)return scope;
        return Things.oldScope(above(target),false);
    }

    /** Whether a level is a collection's: COLLECTION and BOOK, as a 0.1 device wrote them, and THING. */
    static boolean onShelf(Sharing.Scope scope) {
        return scope==Sharing.Scope.COLLECTION||scope==Sharing.Scope.BOOK||scope==Sharing.Scope.THING;
    }

    /** Whether two levels are about the same kind of thing: a collection's rows may say any of three. */
    private static boolean alike(Sharing.Scope one,Sharing.Scope other){return one==other||onShelf(one)&&onShelf(other);}

    /** The rows about one thing, whatever level they were written at: see {@link #onShelf}. */
    private static String scopeIs(Sharing.Scope scope) {
        if(onShelf(scope))return "scope IN ('COLLECTION','BOOK','THING')";
        return scope==Sharing.Scope.PAGE?"scope='PAGE'":"scope='LIBRARY'";
    }

    /**
     * The level the rows about a thing are written at here: whatever its rows already say, so that nobody is ever on one
     * thing twice; else PAGE for a note and THING for a collection (docs/HOME.md, decision 12). A 0.1 device wrote a
     * collection's rows as COLLECTION and a book's as BOOK, and those keep what they say.
     */
    private Sharing.Scope scopeHere(Sharing.Scope scope,String target) {
        if(!onShelf(scope))return scope;
        try(Cursor c=getReadableDatabase().query("shares",new String[]{"scope"},scopeIs(scope)+" AND target=?",new String[]{target},
                null,null,"added ASC","1")) {
            if(c.moveToFirst())try{return Sharing.Scope.valueOf(c.getString(0));}catch(IllegalArgumentException unknown){/* a new one, then */}
        }
        return Sharing.Scope.THING;
    }

    /**
     * Whether this phone may write in something that came from another device: a note says so itself, and
     * a collection is whatever every note of theirs in it says, however deep. Null where they do not agree, or
     * where there is nothing in it yet to say.
     */
    Boolean mayWriteIn(Branch.Kind kind,String id) {
        if(kind==Branch.Kind.PAGE)
            try(Cursor c=getReadableDatabase().rawQuery(
                    "SELECT MIN(writes),MAX(writes),COUNT(*) FROM notes WHERE theirs=1 AND deleted=0 AND id=?",new String[]{id})) {
                if(!c.moveToFirst()||c.getInt(2)==0||c.getInt(0)!=c.getInt(1))return null;
                return c.getInt(0)==1;
            }
        Tree tree=tree();
        int least=1,most=0,count=0;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT book,writes FROM notes WHERE theirs=1 AND deleted=0",null)) {
            while(c.moveToNext()) {
                if(!tree.aboveIn(c.getString(0)).contains(id))continue;
                least=Math.min(least,c.getInt(1));most=Math.max(most,c.getInt(1));count++;
            }
        }
        if(count==0||least!=most)return null;
        return most==1;
    }

    /**
     * Everything this phone has stopped taking in for now, by id. What it has left is not among them: a
     * thing that was left is this phone's own, and wears the mark of something that is only here.
     */
    Set<String> paused() {
        Set<String> all=new HashSet<>();
        try(Cursor c=getReadableDatabase().query("refused",new String[]{"target"},"gone=0",null,null,null,null)) {
            while(c.moveToNext())all.add(c.getString(0));
        }
        return all;
    }

    /** Whether this phone has stopped taking one thing in — itself, or whatever holds it, however far up. */
    boolean pausedHere(Branch.Kind kind,String id) {
        Set<String> all=paused();
        if(all.isEmpty())return false;
        if(all.contains(id))return true;
        if(kind!=Branch.Kind.PAGE&&!shelf(kind))return false;
        for(String one:above(id))if(all.contains(one))return true;
        return false;
    }

    /** Taken in again: whatever was stopped, on the thing itself or on anything that holds it. */
    void resume(Branch.Kind kind,String id) {
        List<String> all=new ArrayList<>();all.add(id);
        if(kind==Branch.Kind.PAGE||shelf(kind))all.addAll(above(id));
        for(String one:all)if(one!=null&&!one.isEmpty())
            getWritableDatabase().delete("refused","target=? AND gone=0",new String[]{one});
    }

    /** Every address that has anything in this thing: whoever a Sync has to talk to. */
    Set<String> everybodyIn(Branch.Kind kind,String id) {
        Set<String> all=new java.util.LinkedHashSet<>();
        List<Sharing.Rule> rules=shares();
        for(Outbox.Page page:pagesUnder(kind,id))
            all.addAll(Sharing.audience(rules,page.path()).keySet());
        // And whoever a collection reaches by itself: one with no note in it yet goes to them on its own (see Carton).
        if(shelf(kind)&&!home(id))all.addAll(Sharing.audience(rules,pathOf(id)).keySet());
        return all;
    }

    /**
     * Whether a thing is shared at all — itself, or anything on it.
     *
     * <p>A shelf says what is true of what is on it: a book holding one shared note wears the shared mark
     * on its tile. The mark in that book's own bar asked a narrower question, whether the book itself was
     * shared, and so the same book said one thing from outside and another from inside.
     */
    boolean sharedAtAll(Branch.Kind kind,String id) {
        List<Sharing.Rule> rules=shares();
        if(kind==Branch.Kind.LIBRARY)return !rules.isEmpty();
        if(kind!=Branch.Kind.COLLECTION&&kind!=Branch.Kind.BOOK&&kind!=Branch.Kind.PAGE)return false;
        if(!cameFrom(kind,id).isEmpty())return true;
        for(Outbox.Page page:pagesUnder(kind,id))
            if(!Sharing.audience(rules,page.path()).isEmpty())return true;
        // And a collection with nothing in it yet can still have been shared by itself, or by what holds it.
        return !Sharing.audience(rules,pathOf(id)).isEmpty();
    }

    /**
     * The address a thing came from, or empty for one that was made here. Asked by the one mark a thing
     * wears, which opens a different box for something of yours than for something of somebody else's.
     */
    String cameFrom(Branch.Kind kind,String id) {
        if(kind!=Branch.Kind.COLLECTION&&kind!=Branch.Kind.BOOK&&kind!=Branch.Kind.PAGE)return "";
        try(Cursor c=getReadableDatabase().query(table(kind),new String[]{"theirs","origin"},"id=?",
                new String[]{id},null,null,null,"1")) {
            if(!c.moveToFirst()||c.getInt(0)!=1)return "";
            String origin=c.getString(1);
            return origin==null?"":origin;
        }
    }

    /** Whether there is any device at all that something could arrive from. */
    boolean anybodyPaired() {
        for(Contact one:addresses())if(one.paired())return true;
        return false;
    }

    // ---- files kept with a collection or a note ------------------------------------------------------------

    /**
     * One file kept with something: the row says what it is, the folder holds what it was.
     *
     * <p>{@code note} is whatever it is kept with, and {@code held} says which kind of thing that is. The
     * column kept its old name because renaming one would mean rewriting a table that is already right.
     */
    static final class Held {
        final String id,note,name,kind; final long bytes,added; final Branch.Kind held;
        /**
         * Which device it came from, by the address it is filed under here, empty for one added here; and, for one that
         * came, whether it has not been opened here yet. Set where the row is read, not where a file is about to be kept.
         */
        String origin=""; boolean fresh;
        Held(String id,String note,String name,String kind,long bytes,long added) {
            this(id,note,name,kind,bytes,added,Branch.Kind.PAGE);
        }
        Held(String id,String note,String name,String kind,long bytes,long added,Branch.Kind held) {
            this.id=id;this.note=note;this.name=name;this.kind=kind;this.bytes=bytes;this.added=added;
            this.held=held==null?Branch.Kind.PAGE:held;
        }
    }

    /**
     * What the row says, and what it says back. A file kept with a note is written 'note', as it always was, and one
     * kept with a collection at any depth 'collection'. A row that says 'book' - which the move to things rewrote, and
     * nothing writes now - is read as kept with the collection that book is now.
     */
    private static String heldAs(Branch.Kind kind){return shelf(kind)?"collection":"note";}
    private static Branch.Kind heldFrom(String said) {
        return "collection".equals(said)||"book".equals(said)?Branch.Kind.COLLECTION:Branch.Kind.PAGE;
    }
    /** The rows of what is kept with one kind of thing. */
    private static String heldIs(Branch.Kind kind){return shelf(kind)?"held IN ('collection','book')":"held='note'";}

    /**
     * A collection changed in something that travels with it on its own - its name, its colour, its files - so its
     * revision moves on, and whoever has it is owed it again (see {@link Carton}).
     */
    private static void touched(SQLiteDatabase db,String collection) {
        db.execSQL("UPDATE things SET revision=revision+1 WHERE id=?",new Object[]{collection});
    }
    /** The columns a Held is read from, in the order {@link #held} reads them. */
    private static final String[] FILE_ROW={"id","note","name","kind","bytes","added","held","origin","fresh"};
    private static Held held(Cursor c) {
        Held one=new Held(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5),
            heldFrom(said(c,"held","note")));
        one.origin=said(c,"origin","");one.fresh=whole(c,"fresh",0)==1;
        return one;
    }

    /** Where the bytes live: the app's own folder, one file to a row, named by the row's id and nothing else. */
    File shed() { File shed=new File(where.getFilesDir(),"files"); if(!shed.isDirectory())shed.mkdirs(); return shed; }
    File fileFor(String id) { return new File(shed(),id); }

    /**
     * Where a backup is unpacked, which is never the shed itself. A backup carries the ids it was written
     * with, and importing one into the pad it came from would otherwise land on the files already here —
     * which, since an import makes copies, would then be carried off under the copy's new name.
     */
    File landing() { File room=new File(where.getFilesDir(),"incoming"); if(!room.isDirectory())room.mkdirs(); return room; }
    File landingFor(String id) { return new File(landing(),id); }

    /** A row for a file about to be copied in. The bytes are written to {@link #fileFor} under this id. */
    Held opening(Branch.Kind held,String what,String name,String kind,long bytes) {
        return new Held(UUID.randomUUID().toString(),what,Attachment.named(name),Attachment.kind(kind),
            Math.max(0,bytes),System.currentTimeMillis(),held);
    }

    /** The row, once the bytes are there. Written last, so a half-copied file is never a file of the note. */
    void keep(Held file){keep(getWritableDatabase(),file);}

    /** The same, inside a transaction somebody else opened. */
    void keep(SQLiteDatabase db,Held file){keep(db,file,true);}

    /**
     * @param changed whether a collection it is kept with has changed by it, and is owed to whoever has it: not for a file
     *                fetched because another device's list named it, which that device and everybody it told have already
     */
    private void keep(SQLiteDatabase db,Held file,boolean changed) {
        ContentValues values=new ContentValues();
        values.put("id",file.id);values.put("note",file.note);values.put("name",file.name);
        values.put("kind",file.kind);values.put("bytes",file.bytes);values.put("added",file.added);
        values.put("held",heldAs(file.held));
        values.put("place",-file.added);
        if(db.insertWithOnConflict("files",null,values,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new IllegalStateException("Could not save the file");
        if(changed&&shelf(file.held))touched(db,file.note);
    }

    /**
     * What kind of thing an id names, as files are kept with it: a collection - any collection, at any depth - or else a
     * note. Home is neither: the files kept on Home are this device's own and go nowhere.
     */
    Branch.Kind keptWith(String id) {
        return id!=null&&!home(id)&&there("things",id)?Branch.Kind.COLLECTION:Branch.Kind.PAGE;
    }

    /** What is kept with this thing, in the order it was added, newest first. */
    List<Held> filesOf(Branch.Kind kind,String id) {
        List<Held> found=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("files",FILE_ROW,"note=? AND "+heldIs(kind),
                new String[]{id},null,null,"place ASC, added DESC")) {
            while(c.moveToNext())found.add(held(c));
        }
        return found;
    }

    /**
     * Everything one thing can reach: what is kept with it, and then what each collection it sits inside keeps,
     * however far up. A map put on a collection is meant for every note in it, so it is offered in all of them
     * rather than only where it was left. Nearest first, so what was put here is the first thing on the strip.
     */
    List<Held> filesReaching(Branch.Kind kind,String id) {
        // The library is not a thing anything is kept with, so it reaches only what is kept with it.
        List<Held> found=new ArrayList<>();
        gather(found,Branch.Kind.PAGE==kind?Branch.Kind.PAGE:kind,id);
        if(kind!=Branch.Kind.PAGE&&!shelf(kind))return found;
        List<String> up=above(id);
        for(int at=up.size()-1;at>=0;at--)gather(found,Branch.Kind.COLLECTION,up.get(at));
        return found;
    }

    private void gather(List<Held> into,Branch.Kind kind,String id) {
        if(id==null||id.isEmpty())return;
        into.addAll(filesOf(kind,id));
    }

    /** One file, by its id, or null. The provider that lends a file to another app asks this. */
    Held file(String id) {
        try(Cursor c=getReadableDatabase().query("files",FILE_ROW,"id=?",new String[]{id},null,null,null,"1")) {
            return c.moveToFirst()?held(c):null;
        }
    }

    /** Everything the pad is holding in files, so it can say so before it fills the phone. */
    long weight() {
        // A received file on Home is counted once: as a file there, not again as the sending it came in.
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(bytes),0)+(SELECT COALESCE(SUM(bytes),0) FROM transferred WHERE here=1 AND moved=0) FROM files",null)) {
            return c.moveToFirst()?c.getLong(0):0;
        }
    }

    /**
     * The row goes, then the bytes. A file nobody has a row for is swept up next time.
     *
     * <p>A file that has been anywhere - that came from another device, or went to one - leaves a line saying
     * it was taken out here. Otherwise the next list that still names it, from a device that has not heard or
     * from one whose list set off before this, would fetch it straight back.
     */
    void drop(String id) {
        Held file=file(id);
        String origin=file==null?"":originOf(id);
        boolean went=false;
        try(Cursor c=getReadableDatabase().query("published",new String[]{"id"},"id=?",new String[]{id},null,null,null,"1")){went=c.moveToFirst();}
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            db.delete("files","id=?",new String[]{id});
            db.delete("reached","id=?",new String[]{id});
            if(file!=null&&shelf(file.held))touched(db,file.note);
            // Kept with a note, or with a collection - whose files travel as a note's do - but not on Home, whose go nowhere.
            if(file!=null&&!home(file.note)&&(went||!origin.isEmpty())) {
                ContentValues v=new ContentValues();
                v.put("id",id);v.put("note",file.note);v.put("origin",origin);v.put("name",file.name);v.put("kind",file.kind);
                v.put("bytes",file.bytes);v.put("manifest","");v.put("declined",1);
                db.insertWithOnConflict("incoming",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
        sweep();
    }

    // ---- the files kept with a shared note, going with it: see Enclosure ------------------------------------

    /**
     * The key a kept file is sealed with, while the notebook has a lock. The phone's is the notebook's own; the
     * PC keeps its key in its window's context and says so here when it opens.
     */
    static volatile java.util.function.Supplier<byte[]> fileKey=NoteStore::key;

    /** Which device a file came from, by the address it is filed under here; empty for one added here. */
    String originOf(String id) {
        try(Cursor c=getReadableDatabase().query("files",new String[]{"origin"},"id=?",new String[]{id},null,null,null,"1")) {
            return c.moveToFirst()&&!c.isNull(0)?c.getString(0):"";
        }
    }

    /** Where a file has gone up, or empty where it has not. */
    String manifestOf(String id) {
        try(Cursor c=getReadableDatabase().query("published",new String[]{"manifest"},"id=?",new String[]{id},null,null,null,"1")) {
            return c.moveToFirst()?c.getString(0):"";
        }
    }

    /**
     * The files a note keeps, as its list goes out: each named, and each that has gone up saying where. Only
     * what is kept with the note itself - a file on a collection around it goes with that collection, in its carton.
     * The same for a collection, by its id: what is kept with it, as its carton lists it. Nothing for Home.
     */
    List<Enclosure.Listed> enclosed(String note) {
        List<Enclosure.Listed> all=new ArrayList<>();
        if(home(note))return all;
        Note page=get(note);RichDocument document=page==null?null:RichDocument.read(page.body);
        for(Held file:filesOf(keptWith(note),note)) {
            if(document!=null&&!document.heads.containsValue(file.id))continue;
            all.add(new Enclosure.Listed(file.id,file.name,file.kind,file.bytes,
                Enclosure.travels(file.bytes)?manifestOf(file.id):""));
        }
        return all;
    }

    /**
     * A list of a note's files from one device, taken in. Only the newest list from each device counts; an
     * older one arriving late is left as it came.
     *
     * <p>The same for a collection's files, by the collection's id here, from the list its carton carries: "exactly as a
     * note's are, with the collection id where the note id goes" (docs/HOME.md). What is fetched is kept with the
     * collection. Never for Home, whose files are this device's own.
     *
     * @return whether anything is now waiting to be fetched
     */
    boolean listed(String note,String from,long asOf,List<Enclosure.Listed> files) {
        if(files==null||note==null||from==null||home(note))return false;
        boolean fetch;List<String> drop;
        String keptAs=heldIs(keptWith(note));
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            try(Cursor c=db.query("listed",new String[]{"at"},"note=? AND origin=?",new String[]{note,from},null,null,null,"1")) {
                if(c.moveToFirst()&&c.getLong(0)>asOf){db.setTransactionSuccessful();return false;}
            }
            ContentValues seen=new ContentValues();seen.put("note",note);seen.put("origin",from);seen.put("at",asOf);
            db.insertWithOnConflict("listed",null,seen,SQLiteDatabase.CONFLICT_REPLACE);
            Set<String> fromThem=ids(db,"SELECT id FROM files WHERE note=? AND "+keptAs+" AND origin=?",note,from);
            Set<String> waiting=ids(db,"SELECT id FROM incoming WHERE note=? AND origin=? AND declined=0",note,from);
            Set<String> here=new HashSet<>(), declined=new HashSet<>();
            for(Enclosure.Listed one:files) {
                here.addAll(ids(db,"SELECT id FROM files WHERE id=?",one.id));
                declined.addAll(ids(db,"SELECT id FROM incoming WHERE id=? AND (declined=1 OR origin<>?)",one.id,from));
            }
            Note page=get(note);RichDocument document=page==null?null:RichDocument.read(page.body);
            if(document!=null){List<Enclosure.Listed> current=new ArrayList<>();for(Enclosure.Listed one:files)if(document.heads.containsValue(one.id))current.add(one);files=current;}
            Enclosure.Plan plan=Enclosure.plan(files,fromThem,here,waiting,declined);
            // A stale attachment list cannot remove a concurrent document head.
            if(document!=null) {
                plan.drop.removeIf(document.heads::containsValue);
                plan.forget.removeIf(document.heads::containsValue);
            }
            for(Enclosure.Listed one:plan.fetch) {
                ContentValues v=new ContentValues();
                v.put("id",one.id);v.put("note",note);v.put("origin",from);v.put("name",Attachment.named(one.name));
                v.put("kind",Attachment.kind(one.kind));v.put("bytes",one.bytes);v.put("manifest",one.manifest);
                db.insertWithOnConflict("incoming",null,v,SQLiteDatabase.CONFLICT_IGNORE);
            }
            for(Enclosure.Listed one:plan.refresh)
                db.execSQL("UPDATE incoming SET manifest=?,tried=0,tries=0 WHERE id=? AND manifest<>?",new Object[]{one.manifest,one.id,one.manifest});
            for(String id:plan.drop){db.delete("files","id=?",new String[]{id});db.delete("reached","id=?",new String[]{id});}
            for(String id:plan.forget)db.delete("incoming","id=? AND declined=0",new String[]{id});
            // A list names only what the note keeps at the sender's end, so whoever sent it has every file on it. Taken
            // from the list as well as from their word: a device never says it has a file it added itself, and between
            // three devices the other two can have it from the one in the middle - the Pro showed the phone's own file
            // as still going to the phone for ever. And a file waited for here can then be had from their door, where
            // the device it is waited from is out of reach or locked: see Post.fromTheirDoor.
            long now=System.currentTimeMillis();boolean newDoor=false;
            for(Enclosure.Listed one:files) {
                if(!Enclosure.plainId(one.id))continue;
                boolean kept=!ids(db,"SELECT id FROM files WHERE id=? AND note=?",one.id,note).isEmpty();
                boolean waited=!kept&&!ids(db,"SELECT id FROM incoming WHERE id=? AND note=? AND declined=0",one.id,note).isEmpty();
                if(!kept&&!waited)continue;
                // Somewhere new to fetch it from is tried at once, not after the hour a door out of reach has come to.
                if(waited&&ids(db,"SELECT id FROM reached WHERE id=? AND address=? AND has=1",one.id,from).isEmpty()) {
                    db.execSQL("UPDATE incoming SET tried=0,tries=0 WHERE id=?",new Object[]{one.id});
                    newDoor=true;
                }
                db.execSQL("INSERT OR REPLACE INTO reached(id,address,has,at,tells) VALUES(?,?,1,?,0)",new Object[]{one.id,from,now});
            }
            fetch=!plan.fetch.isEmpty()||!plan.refresh.isEmpty()||newDoor;drop=plan.drop;
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
        Note keptPage=get(note);
        if(!drop.isEmpty()||(keptPage!=null&&RichDocument.read(keptPage.body)!=null))sweep();
        return fetch;
    }

    private static Set<String> ids(SQLiteDatabase db,String sql,String... args) {
        Set<String> found=new HashSet<>();
        try(Cursor c=db.rawQuery(sql,args)){while(c.moveToNext())found.add(c.getString(0));}
        return found;
    }

    /** A file kept with a note, as it goes up: the row, and what was said about it going up before. */
    static final class Going {
        final Held file; final String manifest; final boolean again;
        Going(Held file,String manifest,boolean again){this.file=file;this.manifest=manifest;this.again=again;}
    }

    /**
     * The files kept with shared notes that are to go up now: never gone up, or asked for again by somebody
     * who could not get them. Only a note this device may write, since what a reader sends is refused. And the
     * files kept with a shared collection, the same way: see {@link #goesWith}.
     */
    List<Going> toPublish(long now) {
        List<Going> due=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT f.id,f.note,f.name,f.kind,f.bytes,f.added,"
                +"COALESCE(p.manifest,''),COALESCE(p.at,-1),COALESCE(p.tried,0),COALESCE(p.tries,0),f.held FROM files f "
                +"LEFT JOIN published p ON p.id=f.id WHERE (f.held='note' OR (f."+heldIs(Branch.Kind.COLLECTION)+" AND f.note<>?)) "
                +"AND f.bytes>0 AND f.bytes<=? AND (p.id IS NULL OR p.manifest='' OR p.at=0)",
                new String[]{Things.HOME,String.valueOf(Enclosure.MOST)})) {
            while(c.moveToNext()) {
                if(!Enclosure.due(c.getLong(8),c.getInt(9),now))continue;
                due.add(new Going(new Held(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5),
                    heldFrom(c.getString(10))),c.getString(6),c.getLong(7)==0));
            }
        }
        List<Going> shared=new ArrayList<>();
        for(Going one:due) {
            if(one.file.held!=Branch.Kind.PAGE){if(!goesWith(one.file.note).isEmpty())shared.add(one);continue;}
            if(!everybodyIn(Branch.Kind.PAGE,one.file.note).isEmpty()&&!onlyReads(one.file.note))shared.add(one);
        }
        return shared;
    }

    /**
     * Whoever a collection's own files go to: everybody it reaches, by a rule on it or on anything above it, while it is
     * on the shelves - the same devices its carton goes to (see {@link #cartonsOwed}). Nobody from a device that may only
     * read it, whose list everybody refuses; nobody for Home, whose files are this device's own.
     */
    Set<String> goesWith(String collection) {
        Set<String> none=new HashSet<>();
        if(collection==null||home(collection))return none;
        Tree tree=tree();
        Row row=tree.rows.get(collection);
        if(row==null||row.away)return none;
        List<String> path=tree.above(collection);
        if(!tree.live(path))return none;
        path.add(collection);
        Map<String,Boolean> audience=Sharing.audience(shares(),path);
        if(audience.isEmpty()||row.theirs&&myLevel(Sharing.Scope.THING,collection)==Sharing.Level.READ)return none;
        return new java.util.LinkedHashSet<>(audience.keySet());
    }

    /** Gone up: where, from now. Whoever was told of it before is told again, since it is somewhere new. */
    void published(String id,String manifest) {
        long now=System.currentTimeMillis();
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ContentValues v=new ContentValues();v.put("id",id);v.put("manifest",manifest);v.put("at",now);v.put("tried",now);v.put("tries",0);
            db.insertWithOnConflict("published",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            db.execSQL("UPDATE reached SET tells=0,at=0 WHERE id=? AND has=0",new Object[]{id});
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    /** Could not go up this time. What it was said to be before, if anything, stands until it can. */
    void publishFailed(String id) {
        long now=System.currentTimeMillis();
        getWritableDatabase().execSQL("INSERT OR IGNORE INTO published(id,manifest,at,tried,tries) VALUES(?,'',?,?,0)",new Object[]{id,now,now});
        getWritableDatabase().execSQL("UPDATE published SET tried=?,tries=tries+1 WHERE id=?",new Object[]{now,id});
    }

    /**
     * Somebody cannot get a file from where this device said it was: it goes up again - unless it went up
     * so lately that the relays can hardly have let it go, when the fault is more likely theirs.
     *
     * @return whether it is to go up again
     */
    boolean upAgain(String id) {
        try(Cursor c=getReadableDatabase().query("published",new String[]{"at","manifest"},"id=?",new String[]{id},null,null,null,"1")) {
            if(!c.moveToFirst()||c.getString(1).isEmpty()||c.getLong(0)==0)return false;
            if(System.currentTimeMillis()-c.getLong(0)<Enclosure.FRESH&&System.currentTimeMillis()>=c.getLong(0))return false;
        }
        getWritableDatabase().execSQL("UPDATE published SET at=0,tried=0,tries=0 WHERE id=?",new Object[]{id});
        return true;
    }

    /** What went up for files no longer kept here, by id: its pieces can go too. */
    Map<String,String> publishedOrphans() {
        Map<String,String> gone=new HashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,manifest FROM published WHERE id NOT IN (SELECT id FROM files)",null)) {
            while(c.moveToNext())gone.put(c.getString(0),c.getString(1));
        }
        return gone;
    }
    void forgetPublished(String id){getWritableDatabase().delete("published","id=?",new String[]{id});}

    /** One file somebody listed, waiting to be fetched. */
    static final class Incoming {
        final String id,note,origin,name,kind,manifest; final long bytes,tried; final int tries;
        Incoming(String id,String note,String origin,String name,String kind,long bytes,String manifest,long tried,int tries) {
            this.id=id;this.note=note;this.origin=origin;this.name=name;this.kind=kind;this.bytes=bytes;
            this.manifest=manifest;this.tried=tried;this.tries=tries;
        }
    }

    /** Whether what a file is kept with, or waited for, is here: a note, or a collection. */
    private boolean keeps(String thing){return thing!=null&&!home(thing)&&(there("notes",thing)||there("things",thing));}

    /** What is waiting to be fetched and whose turn it is. Anything for a note or a collection no longer here is let go. */
    List<Incoming> toFetch(long now) {
        List<Incoming> due=new ArrayList<>();List<String> orphaned=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("incoming",new String[]{"id","note","origin","name","kind","bytes","manifest","tried","tries"},
                "declined=0",null,null,null,"tried ASC")) {
            while(c.moveToNext()) {
                Incoming one=new Incoming(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),
                    c.getLong(5),c.getString(6),c.getLong(7),c.getInt(8));
                if(!keeps(one.note)){orphaned.add(one.id);continue;}
                if(Enclosure.due(one.tried,one.tries,now))due.add(one);
            }
        }
        for(String id:orphaned)getWritableDatabase().delete("incoming","id=? AND declined=0",new String[]{id});
        return due;
    }

    /** Not fetched this time. @return how many times it has not been */
    int fetchFailed(String id) {
        getWritableDatabase().execSQL("UPDATE incoming SET tried=?,tries=tries+1 WHERE id=?",new Object[]{System.currentTimeMillis(),id});
        try(Cursor c=getReadableDatabase().query("incoming",new String[]{"tries"},"id=?",new String[]{id},null,null,null,"1")) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    /**
     * A fetched file, kept with its note the way any file is: sealed on the way in while the notebook has a
     * lock, the row written last, and marked as having come from the device that listed it - so only that
     * device's list can take it out again. Where it is from, it can be passed on from: what it went up as is
     * kept with it. One a collection's list named is kept with that collection, and the collection is not owed
     * anybody for it: whoever listed it has it, and so has everybody that list went to.
     *
     * @return false where it is no longer wanted - taken out here, or the note gone - and nothing is kept
     */
    boolean fileArrived(Incoming one,byte[] plain) throws java.io.IOException {
        if(!Enclosure.plainId(one.id))return false;
        if(weight()+plain.length>Attachment.PLENTY)throw new java.io.IOException("There is no room for more files in this pad.");
        File kept=fileFor(one.id), part=new File(shed(),one.id+".part");
        byte[] key=fileKey.get();
        try(java.io.OutputStream out=new java.io.BufferedOutputStream(new java.io.FileOutputStream(part))) {
            if(key!=null)Sealed.seal(key,new java.io.ByteArrayInputStream(plain),out);else out.write(plain);
        } catch(java.io.IOException e){part.delete();throw e;}
        boolean wanted=false;
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            try(Cursor c=db.query("incoming",new String[]{"id"},"id=? AND declined=0",new String[]{one.id},null,null,null,"1")){wanted=c.moveToFirst();}
            if(wanted&&(file(one.id)!=null||!keeps(one.note)))wanted=false;
            if(wanted) {
                if(kept.exists()||!part.renameTo(kept))throw new java.io.IOException("The file could not be put in place.");
                keep(db,new Held(one.id,one.note,Attachment.named(one.name),Attachment.kind(one.kind),plain.length,System.currentTimeMillis(),
                    keptWith(one.note)),false);
                ContentValues from=new ContentValues();from.put("origin",one.origin);
                db.update("files",from,"id=?",new String[]{one.id});
                ContentValues up=new ContentValues();up.put("id",one.id);up.put("manifest",one.manifest);up.put("at",System.currentTimeMillis());
                db.insertWithOnConflict("published",null,up,SQLiteDatabase.CONFLICT_REPLACE);
                ContentValues has=new ContentValues();has.put("id",one.id);has.put("address",one.origin);has.put("has",1);has.put("at",System.currentTimeMillis());
                db.insertWithOnConflict("reached",null,has,SQLiteDatabase.CONFLICT_REPLACE);
                db.delete("incoming","id=?",new String[]{one.id});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            if(!wanted)part.delete();
        }
        return wanted;
    }

    /**
     * Somebody says they have a file. Believed of a file kept here, or of one this device is waiting to fetch: two
     * devices fetching the same file from a third race, and the one that has it first says so to the other while
     * the other is still fetching. Thrown away, that was said once and never again, and the other device showed the
     * file as still going to it for ever.
     */
    boolean fileReached(String id,String address) {
        if(noteOfFile(id)==null)return false;
        ContentValues v=new ContentValues();v.put("id",id);v.put("address",address);v.put("has",1);v.put("at",System.currentTimeMillis());v.put("tells",0);
        getWritableDatabase().insertWithOnConflict("reached",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        return true;
    }

    /** The note a file is kept with here, or is waiting to be fetched for; null for a file this device knows nothing of. */
    String noteOfFile(String id) {
        Held kept=file(id);
        if(kept!=null)return kept.note;
        try(Cursor c=getReadableDatabase().query("incoming",new String[]{"note"},"id=? AND declined=0",new String[]{id},null,null,null,"1")) {
            return c.moveToFirst()?c.getString(0):null;
        }
    }

    /**
     * Of the files a list from another device names, the ones this device has and did not add itself, which is
     * what "I have this file" is said about. Said once after the fetch, that could be lost - the other device out
     * of reach just then - and nothing said it again: a list arriving that names a file already here plans
     * nothing. So each list that arrives is answered with these, and the device that sent it stops showing them
     * as going. One this device added itself is left out: its owner here is where the other device got it from,
     * which is never waited for.
     */
    List<String> haveHere(String note,List<Enclosure.Listed> listed) {
        List<String> out=new ArrayList<>();
        if(note==null||listed==null)return out;
        for(Enclosure.Listed one:listed) {
            if(!Enclosure.plainId(one.id)||out.contains(one.id))continue;
            Held kept=file(one.id);
            if(kept!=null&&note.equals(kept.note)&&!originOf(one.id).isEmpty())out.add(one.id);
        }
        return out;
    }

    /** The devices that have said they have a file, which may hand its pieces on from their own door. */
    List<String> holders(String id) {
        List<String> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("reached",new String[]{"address"},"id=? AND has=1",new String[]{id},null,null,"at DESC")) {
            while(c.moveToNext())out.add(c.getString(0));
        }
        return out;
    }

    /** The list, with these files saying where they are, has gone to somebody who has not said they have them. */
    void told(java.util.Collection<String> ids,String address) {
        long now=System.currentTimeMillis();
        for(String id:ids) {
            getWritableDatabase().execSQL("INSERT OR IGNORE INTO reached(id,address,has,at,tells) VALUES(?,?,0,0,0)",new Object[]{id,address});
            getWritableDatabase().execSQL("UPDATE reached SET at=?,tells=tells+1 WHERE id=? AND address=? AND has=0",new Object[]{now,id,address});
        }
    }

    /** Who has said they have which of a note's files, by file id. */
    private Map<String,Set<String>> reachedOn(String note) {
        Map<String,Set<String>> has=new HashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT r.id,r.address FROM reached r JOIN files f ON f.id=r.id WHERE f.note=? AND r.has=1",new String[]{note})) {
            while(c.moveToNext())has.computeIfAbsent(c.getString(0),k->new HashSet<>()).add(c.getString(1));
        }
        return has;
    }

    /**
     * Notes whose files have gone up and whose list is to go again to somebody who has not said they have
     * them, by note: the addresses to send to. And collections, by the collection's id, the same way: their
     * list goes again in their carton (see {@link #keptWith}, which tells the two apart).
     */
    Map<String,Set<String>> toTell(long now) {
        Map<String,Set<String>> due=new HashMap<>();
        Map<String,long[]> told=new HashMap<>();
        try(Cursor c=getReadableDatabase().query("reached",new String[]{"id","address","at","tells"},"has=0",null,null,null,null)) {
            while(c.moveToNext())told.put(c.getString(0)+"\n"+c.getString(1),new long[]{c.getLong(2),c.getLong(3)});
        }
        Map<String,List<String>> byNote=new HashMap<>();Set<String> collections=new HashSet<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT f.id,f.note,f.held FROM files f JOIN published p ON p.id=f.id "
                +"WHERE (f.held='note' OR (f."+heldIs(Branch.Kind.COLLECTION)+" AND f.note<>?)) AND p.manifest<>'' AND p.at<>0",
                new String[]{Things.HOME})) {
            while(c.moveToNext()) {
                byNote.computeIfAbsent(c.getString(1),k->new ArrayList<>()).add(c.getString(0));
                if(heldFrom(c.getString(2))!=Branch.Kind.PAGE)collections.add(c.getString(1));
            }
        }
        for(Map.Entry<String,List<String>> note:byNote.entrySet()) {
            boolean collection=collections.contains(note.getKey());
            Set<String> audience=collection?goesWith(note.getKey()):everybodyIn(Branch.Kind.PAGE,note.getKey());
            if(audience.isEmpty()||!collection&&onlyReads(note.getKey()))continue;
            Map<String,Set<String>> has=reachedOn(note.getKey());
            for(String id:note.getValue()) {
                String origin=originOf(id);
                for(String address:audience) {
                    if(address.equals(origin)||has.getOrDefault(id,java.util.Collections.<String>emptySet()).contains(address))continue;
                    long[] was=told.get(id+"\n"+address);
                    if(Enclosure.tellAgain(was==null?0:was[0],was==null?0:(int)was[1],now))
                        due.computeIfAbsent(note.getKey(),k->new java.util.LinkedHashSet<>()).add(address);
                }
            }
        }
        return due;
    }

    /** What each of a note's own files says about where it is, by file id: see {@link Enclosure#state}. */
    Map<String,String> fileStates(String note){return fileStates(note,false);}

    /** The same, said as it is while notes travel only between the owner's devices, where {@code onlyMine}. */
    Map<String,String> fileStates(String note,boolean onlyMine) {
        Map<String,String> said=new HashMap<>();
        Set<String> audience=everybodyIn(Branch.Kind.PAGE,note);
        Map<String,Set<String>> has=reachedOn(note);
        for(Held file:filesOf(Branch.Kind.PAGE,note)) {
            String origin=originOf(file.id);int lacking=0;
            for(String address:audience)
                if(!address.equals(origin)&&!has.getOrDefault(file.id,java.util.Collections.<String>emptySet()).contains(address))lacking++;
            said.put(file.id,Enclosure.state(file.bytes,!audience.isEmpty(),!manifestOf(file.id).isEmpty(),lacking,onlyMine));
        }
        return said;
    }

    /**
     * A note's files, counted the way the line under its title says them: what stays here because it cannot
     * go, what is still on its way to somebody, and what somebody has listed that is still on its way here.
     */
    int[] fileCounts(String note) {
        int stay=0,going=0,coming=0;
        for(String state:fileStates(note).values()) {
            if(state.equals(Enclosure.TOO_BIG)||state.equals(Enclosure.EMPTY))stay++;
            else if(!state.isEmpty())going++;
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM incoming WHERE note=? AND declined=0",new String[]{note})) {
            if(c.moveToFirst())coming=c.getInt(0);
        }
        return new int[]{stay,going,coming};
    }

    /**
     * Where each of these notes stands with each device it reaches, as the sync marks say it ({@link SyncMark#person}):
     * by note, then by address in the order the rules name them. The same rules as {@link #owed} and
     * {@link SyncStatus#read}, read once for the lot, so a shelf of many notes costs what one does. A file too big to
     * go, or empty, never holds a note back: it will never go, and its own card says it stays here.
     */
    Map<String,Map<String,SyncMark>> standing(List<Outbox.Page> pages) {
        Map<String,Map<String,SyncMark>> out=new HashMap<>();
        for(Map.Entry<String,Map<String,Waits.Where>> page:whereEach(pages).entrySet()) {
            Map<String,SyncMark> each=new java.util.LinkedHashMap<>();
            for(Map.Entry<String,Waits.Where> one:page.getValue().entrySet())each.put(one.getKey(),one.getValue().mark());
            out.put(page.getKey(),each);
        }
        return out;
    }

    /**
     * The same, in the detail the words need: whether the text is owed, and how many of the files that can go
     * each device has not said it has. See {@link Waits}.
     */
    Map<String,Map<String,Waits.Where>> whereEach(List<Outbox.Page> pages) {
        Map<String,Map<String,Waits.Where>> out=new HashMap<>();
        if(pages.isEmpty())return out;
        long now=System.currentTimeMillis();
        List<Sharing.Rule> rules=shares();
        SQLiteDatabase db=getReadableDatabase();
        Map<String,long[]> sent=new HashMap<>();Map<String,Long> heard=new HashMap<>();
        try(Cursor c=db.query("sent",new String[]{"address","page","revision","agreed","at"},null,null,null,null,null)) {
            while(c.moveToNext()) {
                sent.put(Outbox.mark(c.getString(0),c.getString(1)),new long[]{c.getLong(2),c.getLong(3)});
                heard.merge(c.getString(0),c.getLong(4),Math::max);
            }
        }
        Map<String,Long> updated=new HashMap<>();Set<String> theirs=new HashSet<>();
        try(Cursor c=db.rawQuery("SELECT id,updated,theirs FROM notes WHERE deleted=0",null)) {
            while(c.moveToNext()){updated.put(c.getString(0),c.getLong(1));if(c.getInt(2)==1)theirs.add(c.getString(0));}
        }
        java.util.Set<String> locked=lockedThere();
        // Devices from before trees: a note that does not fit three levels is not owed them but waits for them to be
        // updated, and is counted so (Waits.Where#update) - sending it again would change nothing. See Post.waitsForTrees.
        Set<String> old=beforeTrees();
        // Who still lacks one of a note's files that can go, as "note address": the rule of Enclosure.state.
        Set<String> has=new HashSet<>();
        try(Cursor c=db.query("reached",new String[]{"id","address"},"has=1",null,null,null,null)) {
            while(c.moveToNext())has.add(c.getString(0)+"\n"+c.getString(1));
        }
        Map<String,List<String[]>> files=new HashMap<>();
        try(Cursor c=db.rawQuery("SELECT f.id,f.note,f.origin,COALESCE(p.manifest,'') FROM files f LEFT JOIN published p ON p.id=f.id"
                +" WHERE f.held='note' AND f.bytes>0 AND f.bytes<=?",new String[]{String.valueOf(Enclosure.MOST)})) {
            while(c.moveToNext())files.computeIfAbsent(c.getString(1),k->new ArrayList<>())
                .add(new String[]{c.getString(0),c.isNull(2)?"":c.getString(2),c.getString(3)});
        }
        for(Outbox.Page page:pages) {
            Map<String,Boolean> audience=Sharing.audience(rules,page.path());
            if(audience.isEmpty())continue;
            // A copy this device may only read is never owed: nothing it sends would be taken (see owed).
            boolean reads=theirs.contains(page.id)&&onlyReads(page.id);
            Map<String,Waits.Where> each=new java.util.LinkedHashMap<>();
            for(String address:audience.keySet()) {
                long[] got=sent.get(Outbox.mark(address,page.id));
                boolean behind=!reads&&(got==null||got[0]<page.revision);
                // Its files wait with it: they are fetched from the list the note carries.
                boolean update=behind&&old.contains(address)&&page.above!=null&&!Things.fitsThreeLevels(page.above,true);
                boolean owed=behind&&!update;
                int lacking=0,going=0;
                if(!reads&&!update)for(String[] file:files.getOrDefault(page.id,java.util.Collections.emptyList())) {
                    if(address.equals(file[1]))continue;
                    going++;
                    if(file[2].isEmpty()||!has.contains(file[0]+"\n"+address))lacking++;
                }
                boolean confirmed=got!=null&&got[0]==page.revision&&got[1]==page.revision;
                each.put(address,new Waits.Where(owed?1:0,lacking,going,confirmed,
                    owed&&SyncMark.stuck(updated.getOrDefault(page.id,0L),heard.getOrDefault(address,0L),now),
                    locked.contains(address),update?1:0));
            }
            out.put(page.id,each);
        }
        return out;
    }

    /**
     * A device says its notebook is locked, or open again (see {@link Receipt#LOCKED}), at {@code moment} by its clock.
     * Kept by address with when it was said - locked as a positive time, open again as a negative one - so a word older
     * than the one kept changes nothing. Open is written only over a lock: every message from a device says it is open,
     * and most devices were never locked. Kept beside the notebook, not in it: it is about the other device, and must
     * outlive this one being restarted while that one stays locked.
     *
     * @return whether that changed what is kept
     */
    boolean lockedThere(String address,boolean locked,long moment) {
        if(address==null||address.isEmpty()||moment<=0)return false;
        android.content.SharedPreferences kept=where.getSharedPreferences("post",Context.MODE_PRIVATE);
        java.util.Set<String> all=new java.util.HashSet<>(kept.getStringSet("lockedThere",new java.util.HashSet<String>()));
        long was=0;String old=null;
        for(String one:all){int tab=one.lastIndexOf('\t');if(tab>0&&one.substring(0,tab).equals(address)){old=one;try{was=Long.parseLong(one.substring(tab+1));}catch(NumberFormatException bad){was=0;}}}
        if(Math.abs(was)>=moment||(!locked&&was<=0))return false;
        if(old!=null)all.remove(old);
        all.add(address+"\t"+(locked?moment:-moment));
        kept.edit().putStringSet("lockedThere",all).apply();
        return true;
    }

    /** The devices whose notebook is locked, as they last said: by address. */
    java.util.Set<String> lockedThere() {
        java.util.Set<String> out=new java.util.HashSet<>();
        for(String one:where.getSharedPreferences("post",Context.MODE_PRIVATE).getStringSet("lockedThere",new java.util.HashSet<String>())) {
            int tab=one.lastIndexOf('\t');
            try{if(tab>0&&Long.parseLong(one.substring(tab+1))>0)out.add(one.substring(0,tab));}catch(NumberFormatException bad){/* not one */}
        }
        return out;
    }

    /** The mark one thing wears, worked out from everything under it: see {@link SyncMark#of}. */
    SyncMark markOf(Branch.Kind kind,String id) {
        boolean shared=kind==Branch.Kind.LIBRARY?!shares().isEmpty():sharedAtAll(kind,id);
        return SyncMark.of(pausedHere(kind,id),shared,false,SyncMark.worst(marksUnder(kind,id)));
    }

    /**
     * Where everything under one thing stands with each device it reaches, as marks: every note under it, as
     * {@link #standing} reads them, and - where a collection under it is waiting to go on its own to a device that has to
     * be updated first - that wait, which is as amber as a note waiting. What the mark on a thing's bar is worked out from.
     */
    List<SyncMark> marksUnder(Branch.Kind kind,String id) {
        List<SyncMark> all=new ArrayList<>();
        for(Map<String,SyncMark> each:standing(pagesUnder(kind,id)).values())all.addAll(each.values());
        if(!cartonsForUpdate(kind,id).isEmpty())all.add(SyncMark.WAITING);
        return all;
    }

    /**
     * The devices paired here whose build has not said it knows about trees (see {@link Receipt#TREE}), by address: 0.1
     * devices, which are sent only what fits three levels and no collection on its own. See {@link Post#knowsTrees}.
     */
    Set<String> beforeTrees() {
        Set<String> old=new HashSet<>();
        for(Contact one:addresses())if(one.paired()&&!Post.knowsTrees(where,one))old.add(one.address);
        return old;
    }

    /**
     * The collections in one thing that wait to go on their own - with their look and their files - to a device only
     * because its build is from before trees, which is never sent one (see {@link Carton}): by collection, the addresses
     * each waits for. They go the moment that device says it knows about trees. None for a note.
     */
    Map<String,Set<String>> cartonsForUpdate(Branch.Kind kind,String id) {
        Map<String,Set<String>> waiting=new java.util.LinkedHashMap<>();
        if(kind==Branch.Kind.PAGE)return waiting;
        Set<String> old=beforeTrees();
        if(old.isEmpty())return waiting;
        for(Outbox.Wait one:cartonsOwed(kind,id))
            if(old.contains(one.address))waiting.computeIfAbsent(one.page,any->new java.util.LinkedHashSet<>()).add(one.address);
        return waiting;
    }

    /**
     * A kept file's own bytes, opened on the way if it is sealed: what goes up is always what the note keeps,
     * never the sealed form, which only this device's key opens.
     */
    byte[] bytesOf(Held file) throws java.io.IOException {
        File kept=fileFor(file.id);
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream((int)Math.max(0,Math.min(file.bytes,Enclosure.MOST)));
        try(java.io.InputStream in=new java.io.BufferedInputStream(new java.io.FileInputStream(kept))) {
            in.mark(Sealed.MAGIC.length);
            byte[] head=new byte[Sealed.MAGIC.length];int got=0,n;
            while(got<head.length&&(n=in.read(head,got,head.length-got))>0)got+=n;
            in.reset();
            if(got<head.length||!Sealed.is(head)){byte[] part=new byte[16384];while((n=in.read(part))!=-1)out.write(part,0,n);return out.toByteArray();}
            byte[] key=fileKey.get();
            if(key==null)throw new java.io.IOException("This file is locked, and the notebook is not open.");
            try{Sealed.open(key,in,out);}catch(Vault.Refused refused){throw new java.io.IOException("This file could not be opened: "+refused.getMessage());}
        }
        return out.toByteArray();
    }

    /**
     * Bytes with no row are deleted. Rows are written after the copy and deleted before it, so the only
     * thing that can be left behind is a file nobody claims — never a row pointing at nothing.
     */
    synchronized void sweep() {
        Set<String> kept=new HashSet<>();
        try(Cursor c=getReadableDatabase().query("files",new String[]{"id"},null,null,null,null,null)) {
            while(c.moveToNext())kept.add(c.getString(0));
        }
        // And the files sent or received on their own, which are nobody's attachment and are kept all the same. Not one
        // that has moved to Home: its file there keeps its bytes while it is kept, and once it is deleted nothing does.
        try(Cursor c=getReadableDatabase().query("transferred",new String[]{"id"},"here=1 AND moved=0",null,null,null,null)) {
            while(c.moveToNext())kept.add(c.getString(0));
        }
        File[] there=shed().listFiles();
        if(there!=null)for(File file:there)if(!kept.contains(file.getName()))
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        // Nothing in the landing room belongs to anybody: what an import wanted has already been moved out.
        File[] left=landing().listFiles();
        if(left!=null)for(File file:left)
            //noinspection ResultOfMethodCallIgnored
            file.delete();
    }
    // ---- files sent straight to a device, belonging to no note: see Drop ------------------------------------

    /** One file of a sending, either way. Its bytes, while {@code here}, are at {@link #fileFor} under its id. */
    static final class Loose {
        final String id,batch,theirs,name,kind,manifest; final long bytes,tried; final int tries; final boolean here,relay;
        /** Here, and kept on Home now (see {@link NoteStore#looseArrived}): its sending still counts it, and no list shows it. */
        boolean moved;
        Loose(String id,String batch,String theirs,String name,String kind,long bytes,String manifest,boolean here,boolean relay,long tried,int tries) {
            this.id=id;this.batch=batch;this.theirs=theirs==null?"":theirs;this.name=name;this.kind=kind;this.bytes=bytes;
            this.manifest=manifest==null?"":manifest;this.here=here;this.relay=relay;this.tried=tried;this.tries=tries;
        }
        /** The same file as any kept file is handled: opened, lent, sealed when the lock goes on. */
        Held held(){return new Held(id,batch,name,kind,bytes,0);}
    }

    /** One sending, to a device or from one, with its files. */
    static final class Transfer {
        final String id,wire,address,name; final boolean out; final int state,tries; final long at,revision,tried; final boolean seen;
        final List<Loose> files=new ArrayList<>();
        Transfer(String id,String wire,boolean out,String address,String name,int state,long at,long revision,long tried,int tries,boolean seen) {
            this.id=id;this.wire=wire;this.out=out;this.address=address;this.name=name;this.state=state;this.at=at;
            this.revision=revision;this.tried=tried;this.tries=tries;this.seen=seen;
        }
        long bytes(){long all=0;for(Loose one:files)all+=one.bytes;return all;}
        boolean allHere(){for(Loose one:files)if(!one.here)return false;return !files.isEmpty();}
    }

    private static final String[] TRANSFER_ROW={"id","wire","way","address","name","state","at","revision","tried","tries","seen"};
    private static final String[] LOOSE_ROW={"id","batch","theirs","name","kind","bytes","manifest","here","relay","tried","tries","moved"};

    private Transfer transferFrom(Cursor c){return transferFrom(c,true);}

    /** @param moved whether the files that have moved to Home are among its files: for what is said to the sender, not for a list */
    private Transfer transferFrom(Cursor c,boolean moved) {
        Transfer one=new Transfer(c.getString(0),c.getString(1),"out".equals(c.getString(2)),c.getString(3),c.getString(4),
            c.getInt(5),c.getLong(6),c.getLong(7),c.getLong(8),c.getInt(9),c.getInt(10)==1);
        try(Cursor f=getReadableDatabase().query("transferred",LOOSE_ROW,moved?"batch=?":"batch=? AND moved=0",new String[]{one.id},null,null,"rowid ASC")) {
            while(f.moveToNext())one.files.add(looseFrom(f));
        }
        return one;
    }
    private static Loose looseFrom(Cursor c) {
        Loose one=new Loose(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getLong(5),c.getString(6),
            c.getInt(7)==1,c.getInt(8)==1,c.getLong(9),c.getInt(10));
        one.moved=whole(c,"moved",0)==1;
        return one;
    }

    /**
     * A sending made here, of files already copied in under their ids ({@link #opening}, then the bytes at
     * {@link #fileFor}). Nothing goes yet: it is made ready and offered in the next round of file work.
     *
     * @return its id
     */
    String sendFiles(String address,String name,List<Held> files) {
        String id=UUID.randomUUID().toString();
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ContentValues t=new ContentValues();
            t.put("id",id);t.put("wire",UUID.randomUUID().toString());t.put("way","out");t.put("address",address);t.put("name",name);
            t.put("state",Drop.SENDING);t.put("at",System.currentTimeMillis());t.put("revision",1);t.put("seen",1);
            db.insert("transfers",null,t);
            for(Held file:files) {
                ContentValues f=new ContentValues();
                f.put("id",file.id);f.put("batch",id);f.put("theirs",file.id);f.put("name",file.name);f.put("kind",file.kind);
                f.put("bytes",file.bytes);f.put("here",1);
                db.insert("transferred",null,f);
            }
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
        return id;
    }

    /**
     * Every sending to show, newest first. A received one that was refused, or whose files have all been deleted or
     * put in notes, is kept only to answer the sender if it offers again, and is not shown. Nor is a received file that
     * has come: it is kept on Home (see {@link #looseArrived}), so a received sending lists only what is still to come,
     * and one that has all come is not listed at all.
     */
    List<Transfer> transfers() {
        List<Transfer> all=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("transfers",TRANSFER_ROW,null,null,null,null,"at DESC")) {
            while(c.moveToNext()) {
                Transfer one=transferFrom(c,false);
                if(!one.out&&(one.state==Drop.REFUSED||one.files.isEmpty()))continue;
                all.add(one);
            }
        }
        return all;
    }

    /**
     * What this device sent to others, newest first, each with where it stands: what ⋮ → Sent files lists now that the
     * drop box has gone (docs/HOME.md, decision 20). Every sending made here, delivered, refused or still going.
     */
    List<Transfer> sentFiles() {
        List<Transfer> all=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("transfers",TRANSFER_ROW,"way='out'",null,null,null,"at DESC")) {
            while(c.moveToNext())all.add(transferFrom(c));
        }
        return all;
    }

    /** One sending by its id here, or null. */
    Transfer transfer(String id) {
        try(Cursor c=getReadableDatabase().query("transfers",TRANSFER_ROW,"id=?",new String[]{id},null,null,null,"1")) {
            return c.moveToFirst()?transferFrom(c):null;
        }
    }

    /** One sending by the id it travels under, the device at the other end, and which way it goes; or null. */
    Transfer transferOnTheWire(String wire,String address,boolean out) {
        try(Cursor c=getReadableDatabase().query("transfers",TRANSFER_ROW,"wire=? AND address=? AND way=?",
                new String[]{wire,address,out?"out":"in"},null,null,null,"1")) {
            return c.moveToFirst()?transferFrom(c):null;
        }
    }

    /** What is to be offered now, and what is to be fetched: the sendings whose turn it is. */
    List<Transfer> transfersDue(long now) {
        List<Transfer> due=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("transfers",TRANSFER_ROW,"(way='out' AND state IN (?,?)) OR (way='in' AND state=?)",
                new String[]{""+Drop.SENDING,""+Drop.WAITING,""+Drop.FETCHING},null,null,"at ASC")) {
            while(c.moveToNext()) {
                Transfer one=transferFrom(c);
                if(one.out){if(Drop.due(one.tried,one.tries,now))due.add(one);continue;}
                for(Loose file:one.files)if(!file.here&&Drop.due(file.tried,file.tries,now)){due.add(one);break;}
            }
        }
        return due;
    }

    /**
     * The sendings made here for one device, tried again at once: it has just said it takes files.
     *
     * @return whether there were any
     */
    boolean wakeSendings(String address) {
        getWritableDatabase().execSQL("UPDATE transfers SET tried=0,tries=0 WHERE way='out' AND address=? AND state IN (?,?)",
            new Object[]{address,Drop.SENDING,Drop.WAITING});
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM transfers WHERE way='out' AND address=? AND state IN (?,?)",
                new String[]{address,""+Drop.SENDING,""+Drop.WAITING})) {
            return c.moveToFirst()&&c.getInt(0)>0;
        }
    }

    /** A file of a sending made here, ready: where its pieces are, from now. */
    void loosePublished(String file,String manifest) {
        getWritableDatabase().execSQL("UPDATE transferred SET manifest=?,relay=0,tried=?,tries=0 WHERE id=?",new Object[]{manifest,System.currentTimeMillis(),file});
    }

    /** Offered, or tried and not reachable: waiting for their answer either way, and tried again on the clock. */
    void offered(String id) {
        getWritableDatabase().execSQL("UPDATE transfers SET state=?,tried=?,tries=tries+1 WHERE id=? AND way='out' AND state IN (?,?)",
            new Object[]{Drop.WAITING,System.currentTimeMillis(),id,Drop.SENDING,Drop.WAITING});
    }

    /**
     * They cannot get the files from where the offer said: the pieces go up to a relay, and the sending is offered
     * again, as a later offer. Not while it went up to a relay a moment ago, when the fault is more likely theirs.
     *
     * @return whether it is to go again
     */
    boolean offerAgain(String id,long revision) {
        Transfer one=transfer(id);
        if(one==null||!one.out||!Drop.goingOn(one.state)||revision!=one.revision)return false;
        boolean fresh=false;
        for(Loose file:one.files)if(file.here&&!file.manifest.isEmpty()&&upToRelayLately(file))fresh=true;
        if(fresh)return false;
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            db.execSQL("UPDATE transferred SET relay=1,tried=0,tries=0 WHERE batch=? AND here=1",new Object[]{id});
            db.execSQL("UPDATE transfers SET revision=revision+1,tried=0,tries=0 WHERE id=?",new Object[]{id});
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
        return true;
    }

    /** Relays were asked to hold a file's pieces within the last few minutes. Kept for this run only. */
    private static final Map<String,Long> RELAYED=new java.util.concurrent.ConcurrentHashMap<>();
    void wentToRelays(String file){RELAYED.put(file,System.currentTimeMillis());}
    private static boolean upToRelayLately(Loose file) {
        Long at=RELAYED.get(file.id);long now=System.currentTimeMillis();
        return at!=null&&now>=at&&now-at<Enclosure.FRESH;
    }

    /**
     * An offer that arrived, written down: a new sending, from a device paired here and in the state {@link
     * Drop#arriving} decided; or a later offer of one already here, whose files it says are somewhere new. Every
     * file gets an id made here, never the sender's: an id is a file's name in the pad's own folder, and a sender
     * choosing it could land on a file that is already there.
     *
     * @return the sending as it now is, or null where an offer older than one already taken changed nothing
     */
    Transfer offerArrived(String from,String name,String wire,long revision,List<Enclosure.Listed> files,int state) {
        Transfer was=transferOnTheWire(wire,from,false);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            if(was==null) {
                String id=UUID.randomUUID().toString();
                ContentValues t=new ContentValues();
                t.put("id",id);t.put("wire",wire);t.put("way","in");t.put("address",from);t.put("name",name);t.put("state",state);
                t.put("at",System.currentTimeMillis());t.put("revision",revision);t.put("seen",0);
                db.insert("transfers",null,t);
                for(Enclosure.Listed one:files) {
                    ContentValues f=new ContentValues();
                    f.put("id",UUID.randomUUID().toString());f.put("batch",id);f.put("theirs",one.id);f.put("name",Attachment.named(one.name));
                    f.put("kind",Attachment.kind(one.kind));f.put("bytes",one.bytes);f.put("manifest",one.manifest);
                    db.insert("transferred",null,f);
                }
            } else if(revision>was.revision) {
                for(Enclosure.Listed one:files)
                    db.execSQL("UPDATE transferred SET manifest=?,tried=0,tries=0 WHERE batch=? AND theirs=? AND here=0",new Object[]{one.manifest,was.id,one.id});
                db.execSQL("UPDATE transfers SET revision=? WHERE id=?",new Object[]{revision,was.id});
            } else {db.setTransactionSuccessful();return null;}
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
        return transferOnTheWire(wire,from,false);
    }

    /**
     * A received file's bytes, kept the way any file is: sealed on the way in while the notebook has a lock, and
     * marked here only once they are in place.
     *
     * <p>And kept on Home, new until it is opened (docs/HOME.md, decision 6): a file on Home under the same id, saying
     * which device it came from, written in the same transaction as the sending hears it is here. The sending goes on
     * counting it - it is how the device that sent it is told every file has come - but no list shows it there again.
     *
     * @return false where it is no longer wanted - the sending refused, or the file deleted - and nothing is kept
     */
    boolean looseArrived(Loose one,byte[] plain) throws java.io.IOException {
        if(!Enclosure.plainId(one.id))return false;
        if(weight()+plain.length>Attachment.PLENTY)throw new java.io.IOException("There is no room for more files in this pad.");
        File kept=fileFor(one.id), part=new File(shed(),one.id+".part");
        byte[] key=fileKey.get();
        try(java.io.OutputStream out=new java.io.BufferedOutputStream(new java.io.FileOutputStream(part))) {
            if(key!=null)Sealed.seal(key,new java.io.ByteArrayInputStream(plain),out);else out.write(plain);
        } catch(java.io.IOException e){part.delete();throw e;}
        boolean wanted=false;
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            String from="",name=one.name,kind=one.kind;
            try(Cursor c=db.rawQuery("SELECT f.id,t.address,f.name,f.kind FROM transferred f JOIN transfers t ON t.id=f.batch"
                    +" WHERE f.id=? AND f.here=0 AND t.state=? AND t.way='in'",new String[]{one.id,""+Drop.FETCHING})) {
                wanted=c.moveToFirst();
                if(wanted){from=c.getString(1);name=c.getString(2);kind=c.getString(3);}
            }
            if(wanted&&file(one.id)!=null)wanted=false;
            if(wanted) {
                if(kept.exists()||!part.renameTo(kept))throw new java.io.IOException("The file could not be put in place.");
                db.execSQL("UPDATE transferred SET here=1,moved=1,bytes=? WHERE id=?",new Object[]{plain.length,one.id});
                long now=System.currentTimeMillis();
                ContentValues v=new ContentValues();
                v.put("id",one.id);v.put("note",Things.HOME);v.put("held",heldAs(Branch.Kind.COLLECTION));v.put("name",Attachment.named(name));
                v.put("kind",Attachment.kind(kind));v.put("bytes",plain.length);v.put("added",now);v.put("place",-now);
                v.put("origin",from==null?"":from);v.put("fresh",1);
                if(db.insert("files",null,v)<0)throw new java.io.IOException("The file could not be kept on Home.");
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            if(!wanted)part.delete();
        }
        return wanted;
    }

    /** A received file not fetched this time. @return how many times it has not been */
    int looseFailed(String file) {
        getWritableDatabase().execSQL("UPDATE transferred SET tried=?,tries=tries+1 WHERE id=?",new Object[]{System.currentTimeMillis(),file});
        try(Cursor c=getReadableDatabase().query("transferred",new String[]{"tries"},"id=?",new String[]{file},null,null,null,"1")) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    /** Where a sending now stands. */
    void settle(String id,int state){getWritableDatabase().execSQL("UPDATE transfers SET state=? WHERE id=?",new Object[]{state,id});}

    /**
     * A sending made here, finished with - delivered, or refused: the copies kept to send it are let go. The rows
     * stay, so the list can go on saying what went and to whom.
     *
     * @return where its pieces were, so they can be let go too
     */
    List<String> sentAndDone(String id) {
        List<String> pieces=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("transferred",new String[]{"manifest"},"batch=? AND manifest<>''",new String[]{id},null,null,null)) {
            while(c.moveToNext())pieces.add(c.getString(0));
        }
        getWritableDatabase().execSQL("UPDATE transferred SET here=0,manifest='' WHERE batch=?",new Object[]{id});
        sweep();
        return pieces;
    }

    /**
     * A sending taken off the list: one made here stops being offered, and its copies and pieces go; a received one
     * goes with every file still in it. What was received is remembered as refused if it had not come, so an offer
     * that comes again is answered rather than asked about again.
     *
     * @return where the pieces of what was being sent were, to be let go
     */
    List<String> forgetTransfer(String id) {
        Transfer one=transfer(id);
        if(one==null)return new ArrayList<>();
        List<String> pieces=new ArrayList<>();
        for(Loose file:one.files)if(one.out&&!file.manifest.isEmpty())pieces.add(file.manifest);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            // Not what has come already: that is kept on Home now, and is taken away from there, not from here.
            db.delete("transferred",one.out?"batch=?":"batch=? AND moved=0",new String[]{id});
            if(one.out)db.delete("transfers","id=?",new String[]{id});
            else if(one.state!=Drop.HERE)db.execSQL("UPDATE transfers SET state=? WHERE id=?",new Object[]{Drop.REFUSED,id});
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
        sweep();
        return pieces;
    }

    /**
     * One received file, deleted: its row, then its bytes. The sending stays behind it, to answer an offer again. One
     * kept on Home now is deleted there, as any file is (see {@link #drop}).
     */
    void deleteLoose(String file) {
        Held kept=file(file);
        if(kept!=null&&kept.held!=Branch.Kind.PAGE&&home(kept.note)){drop(file);return;}
        getWritableDatabase().delete("transferred","id=? AND moved=0",new String[]{file});
        sweep();
    }

    /**
     * A received file put in a note: it becomes that note's attachment, the same bytes under the same id, and is no
     * longer among the received files. From here it is a file of the note, and goes wherever the note goes. One kept on
     * Home is moved from there (see {@link #moveInto}).
     */
    void intoNote(String file,String note) {
        if(get(note)==null)throw new IllegalStateException("That note is not here any more.");
        if(file(file)!=null){moveFile(file,note);return;}
        Loose one=loose(file);
        if(one==null||!one.here)throw new IllegalStateException("That file is not here any more.");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            keep(db,new Held(one.id,note,one.name,one.kind,one.bytes,System.currentTimeMillis()));
            db.delete("transferred","id=?",new String[]{file});
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    /**
     * The sending a received file came in, by the file's id - wherever the file is kept now - or null for one that did not
     * come on its own: who sent it, under the name they had then, and when it came.
     */
    Transfer sendingOf(String file) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT t.id FROM transferred f JOIN transfers t ON t.id=f.batch WHERE f.id=? AND t.way='in'",
                new String[]{file})) {
            return c.moveToFirst()?transfer(c.getString(0)):null;
        }
    }

    /** One file of a sending that is still among the received, by its id here, or null. One kept on Home is not. */
    Loose loose(String file) {
        try(Cursor c=getReadableDatabase().query("transferred",LOOSE_ROW,"id=? AND moved=0",new String[]{file},null,null,null,"1")) {
            return c.moveToFirst()?looseFrom(c):null;
        }
    }

    /**
     * What is new among the received: sendings waiting for an answer, and ones that came and have not been looked at
     * with a file still among them. A file that came is new on Home instead, until it is opened (see {@link #freshOnHome}).
     */
    int freshTransfers() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM transfers t WHERE t.way='in' AND (t.state=? OR (t.state=? AND t.seen=0)) "
                +"AND EXISTS (SELECT 1 FROM transferred f WHERE f.batch=t.id AND f.moved=0)",new String[]{""+Drop.ASKING,""+Drop.HERE})) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    /** How many received files are here, each of them in no note and not yet on Home. */
    int looseHere() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM transferred f JOIN transfers t ON t.id=f.batch "
                +"WHERE t.way='in' AND t.state=? AND f.here=1 AND f.moved=0",new String[]{""+Drop.HERE})) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    /**
     * Whether the drop box is shown: once a device is paired here, which is what files can come from, or once
     * anything has been sent either way.
     */
    boolean dropBoxShown() {
        for(Contact one:addresses())if(one.paired())return true;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM transfers LIMIT 1",null)){return c.moveToFirst();}
    }

    /** The received files have been looked at. */
    void transfersSeen(){getWritableDatabase().execSQL("UPDATE transfers SET seen=1 WHERE way='in' AND state=?",new Object[]{Drop.HERE});}

    /** A kept file by its id, whether a note's or one received on its own. What lends and opens files asks this. */
    Held keptFile(String id) {
        Held held=file(id);
        if(held!=null)return held;
        Loose one=loose(id);
        return one!=null&&one.here?one.held():null;
    }

    /**
     * Every file whose bytes are kept here, received ones included: what is sealed when the lock goes on and opened
     * when it comes off. A backup carries only {@link #everyFile} - received files once they are kept on Home, and not
     * the few of a sending still coming, which belong to nothing yet.
     */
    List<Held> everyFileKept() {
        List<Held> all=everyFile();
        // Not one kept on Home as well: it is among every file already, and sealing it twice would lock it for good.
        try(Cursor c=getReadableDatabase().query("transferred",LOOSE_ROW,"here=1 AND moved=0",null,null,null,null)) {
            while(c.moveToNext())all.add(looseFrom(c).held());
        }
        return all;
    }

    @Override public void onCreate(SQLiteDatabase db) { for(String statement:SchemaMigrations.create())db.execSQL(statement); }
    // SQLiteOpenHelper runs this inside a transaction: a step that fails leaves the old schema and notes intact.
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next) {
        // A step that moves something checks itself (docs/HOME.md, decision 11): the move to notes and collections, and
        // received files moving to Home. What it must leave as it found it is counted before and after, inside this same
        // transaction, and any difference throws: the whole upgrade is rolled back and the notebook stays where it was,
        // which the build before opens as it was. Counted on the database being upgraded, never through the helper, which
        // is still opening it.
        int at=old;
        for(SchemaMigrations.Checked step:SchemaMigrations.CHECKED) {
            if(at>step.from||next<=step.from)continue;
            steps(db,at,step.from);
            long[] before=counts(db,step.before);
            steps(db,step.from,step.from+1);
            long[] after=counts(db,step.after);
            String differs=SchemaMigrations.differs(step.counted,before,after);
            if(differs!=null)throw new IllegalStateException(step.refused+differs
                +". Nothing was changed; "+SchemaMigrations.stillOpens(old)+" still opens it.");
            // Numbers only: never a name or a word of a note.
            android.util.Log.i("Mininotes/Store",step.done+": "+SchemaMigrations.counted(step.counted,after));
            at=step.from+1;
        }
        steps(db,at,next);
    }
    private static void steps(SQLiteDatabase db,int old,int next){for(String statement:SchemaMigrations.upgrade(old,next))db.execSQL(statement);}
    private static long[] counts(SQLiteDatabase db,String[] asks) {
        long[] counted=new long[asks.length];
        for(int at=0;at<asks.length;at++)try(Cursor c=db.rawQuery(asks[at],null)){counted[at]=c.moveToFirst()?c.getLong(0):-1;}
        return counted;
    }
    @Override public void onDowngrade(SQLiteDatabase db,int old,int next) { throw new IllegalStateException("This notebook was written by a newer version of Mininotes"); }

    // ---- pages -------------------------------------------------------------------------------------------

    void save(Note n){save(getWritableDatabase(),n);}

    /**
     * The open page written down — unless the note has moved on since the page last looked.
     *
     * <p>A page is a copy of a note, and a note can be given something newer while the page is open: it
     * arrives on the node's thread, not on the page's. Written regardless, the page's older copy goes back
     * over what arrived, a revision higher, and is then sent on as the newer of the two. So the writing
     * checks first, in the same transaction, and where the notebook is ahead it writes nothing and says so:
     * the page puts the two together and tries again.
     *
     * @param seen the revision the page was holding before this writing
     * @return false where the notebook holds something the page has not seen, and nothing was written
     */
    boolean saveFrom(Note n,long seen) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            try(Cursor c=db.query("notes",new String[]{"revision"},"id=?",new String[]{n.id},null,null,null,"1")) {
                if(c.moveToFirst()&&c.getLong(0)>seen)return false;
            }
            save(db,n);
            db.setTransactionSuccessful();
            return true;
        } finally {db.endTransaction();}
    }

    /** The same, inside a transaction somebody else opened: a restore is one piece of work or none. */
    void save(SQLiteDatabase db,Note n) {
        // A list row holds a truncated body; writing one back would silently cut the page down to its preview.
        if(!n.complete)throw new IllegalStateException("Refusing to save a note that was only partially read");
        RichDocument nextDocument=RichDocument.read(n.body),previousDocument=null;
        if(nextDocument!=null)try(Cursor c=db.query("notes",new String[]{"body"},"id=?",new String[]{n.id},null,null,null,"1")) {
            if(c.moveToFirst())previousDocument=RichDocument.read(c.getString(0));
        }
        // Who wrote what, worked out before the row is replaced, from what it said until now: see Writers.
        String body=n.body==null?"":n.body;
        Writers runs=n.writers!=null&&n.writers.length()==body.length()?n.writers:null;
        if(runs==null) {
            String was=null;
            try(Cursor c=db.query("notes",new String[]{"body"},"id=?",new String[]{n.id},null,null,null,"1")) {
                if(c.moveToFirst())was=c.getString(0);
            }
            if(was==null||!was.equals(body))runs=Writers.arrived(was,was==null?null:writersOf(db,n.id,was),body,n.by);
        }
        ContentValues v=new ContentValues();v.put("id",n.id);v.put("title",n.title);v.put("body",n.body);v.put("notebook",n.notebook);
        // A note on Home is written the one way Home is written, whichever way it was named, so every list of Home finds it.
        v.put("book",home(n.book)?Things.HOME:n.book);v.put("pinned",n.pinned?1:0);v.put("deleted",n.deleted?1:0);v.put("updated",n.updated);
        // Saving replaces the whole row, so the place has to be written back or every edit would reshuffle the book.
        v.put("place",n.place);v.put("archived",n.archived?1:0);v.put("colour",n.colour);v.put("rung",n.rung);
        v.put("revision",n.revision);v.put("theirs",n.theirs?1:0);v.put("origin",n.origin);
        v.put("writes",n.writes?1:0);
        // What Home shows of a note - its icon, its picture, its place in the dock - is not the page's to say, and the row
        // is replaced whole: what it had is carried over. The dock only while it is a favourite.
        try(Cursor c=db.query("notes",new String[]{"icon","image","dock"},"id=?",new String[]{n.id},null,null,null,"1")) {
            if(c.moveToFirst()){v.put("icon",c.getString(0));v.put("image",c.getString(1));v.put("dock",n.pinned?c.getInt(2):0);}
        }
        if(db.insertWithOnConflict("notes",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new IllegalStateException("Could not save the note");
        if(runs!=null)keepWriters(db,n.id,body,runs);
        if(previousDocument!=null)for(String old:previousDocument.heads.values())if(!nextDocument.heads.containsValue(old)) {
            db.delete("files","id=? AND note=?",new String[]{old,n.id});
            db.delete("reached","id=?",new String[]{old});
        }
    }

    // ---- who wrote what, on this device only ---------------------------------------------------------------------

    /**
     * Who wrote each letter of a note, for the text it says now. Runs kept for another text - the note written by
     * something that did not keep them, or a notebook put back from a backup - are nobody's rather than drawn over
     * words they are not about.
     */
    Writers writersOf(String note,String body){return writersOf(getReadableDatabase(),note,body==null?"":body);}

    private Writers writersOf(SQLiteDatabase db,String note,String body) {
        try(Cursor c=db.query("writers",new String[]{"runs","trace"},"note=?",new String[]{note},null,null,null,"1")) {
            if(c.moveToFirst()&&Arriving.trace(body).equals(c.getString(1)))return Writers.read(c.getString(0),body.length());
        }
        return Writers.unknown(body.length());
    }

    /** Kept with the trace of the text they are about; a note nobody known wrote in keeps no row at all. */
    private void keepWriters(SQLiteDatabase db,String note,String body,Writers runs) {
        String kept=runs.write();
        if(kept==null){db.delete("writers","note=?",new String[]{note});return;}
        ContentValues v=new ContentValues();v.put("note",note);v.put("runs",kept);v.put("trace",Arriving.trace(body));
        if(db.insertWithOnConflict("writers",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new IllegalStateException("Could not save the note");
    }

    /**
     * Who a device's writing is: you, for any device of yours; anybody else by the key they sign with, which is the
     * same on every device that knows them; by their address only where no key is known.
     */
    String writerOf(String address) {
        if(address==null||address.isEmpty())return Writers.UNKNOWN;
        Contact known=address(address);
        if(known!=null&&known.mine)return Writers.ME;
        String key=keyOf(address);
        if(key.isEmpty())return Writers.byAddress(address);
        Contact same=byKey(key);
        return same!=null&&same.mine?Writers.ME:Writers.byKey(key);
    }

    /** The colours writers are drawn in here: yours, those you gave others, and which writers are devices of yours. */
    Writers.Palette palette() {
        Map<String,Integer> chosen=new HashMap<>();
        try(Cursor c=getReadableDatabase().query("inks",new String[]{"writer","colour"},null,null,null,null,null)) {
            while(c.moveToNext())chosen.put(c.getString(0),c.getInt(1));
        }
        Integer mine=chosen.remove(Writers.ME);
        java.util.Set<String> own=new java.util.HashSet<>();
        for(Contact one:addresses()) {
            if(!one.mine)continue;
            own.add(Writers.byAddress(one.address));
            if(one.signing.length>0)own.add(Writers.byKey(canonical(one.signing)));
        }
        return new Writers.Palette(mine==null?Tint.NONE:mine,chosen,own);
    }

    /**
     * A writer's colour chosen on this device. Yours can be {@link Tint#NONE}, the ordinary ink; for anybody else it
     * takes back the colour you gave them, and they are in their own again.
     */
    void chooseInk(String writer,int colour) {
        if(writer==null||writer.isEmpty())return;
        SQLiteDatabase db=getWritableDatabase();
        if(!Tint.known(colour)&&!writer.equals(Writers.ME)){db.delete("inks","writer=?",new String[]{writer});return;}
        ContentValues v=new ContentValues();v.put("writer",writer);v.put("colour",Tint.known(colour)?colour:Tint.NONE);
        if(db.insertWithOnConflict("inks",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new IllegalStateException("Could not keep that colour");
    }

    // ---- every version a note has had ---------------------------------------------------------------------

    /** One version of a note: what it said, when it was kept, and where it came from. */
    static final class Version {
        final String id,note,title,body,source; final long revision,at;
        Version(String id,String note,long revision,long at,String source,String title,String body) {
            this.id=id;this.note=note;this.revision=revision;this.at=at;this.source=source;
            this.title=title;this.body=body;
        }
        /** Empty for this phone; otherwise the address it arrived from. */
        boolean ours(){return source.isEmpty();}
    }

    /** How many versions of one note are worth keeping. Text is small; a hundred of them is still small. */
    static final int VERSIONS_KEPT=100;

    /**
     * Keeps what a note says now, if it is not already the last thing kept. Called when an editing session
     * ends and when something arrives from somebody else, so the list reads as the note's history rather
     * than as a keystroke log.
     *
     * @param source empty for this phone, or the address this text came from
     */
    void keepVersion(String note,String source) {
        Note now=get(note);
        if(now==null)return;
        Version last=lastVersion(note);
        if(last!=null&&last.title.equals(now.title)&&last.body.equals(now.body))return;
        ContentValues v=new ContentValues();
        v.put("id",UUID.randomUUID().toString());v.put("note",note);
        v.put("revision",now.revision);v.put("at",System.currentTimeMillis());
        v.put("source",source==null?"":source);v.put("title",now.title);v.put("body",now.body);
        if(getWritableDatabase().insert("versions",null,v)<0)throw new IllegalStateException("Could not keep that version");
        prune(note);
    }

    /** One version written straight down, for text that arrived rather than text that is here. */
    void keepVersion(String note,long revision,String source,String title,String body) {
        // The same revision from the same device saying the same words is already the newest version: a note
        // sent again whole - by Sync, or to carry where its files are - adds nothing to its history, and a
        // hundred of those would push out everything that did.
        Version last=lastVersion(note);
        if(last!=null&&last.revision==revision&&(source==null?"":source).equals(last.source)
            &&last.title.equals(title==null?"":title)&&last.body.equals(body==null?"":body))return;
        ContentValues v=new ContentValues();
        v.put("id",UUID.randomUUID().toString());v.put("note",note);
        v.put("revision",revision);v.put("at",System.currentTimeMillis());
        v.put("source",source==null?"":source);v.put("title",title);v.put("body",body);
        if(getWritableDatabase().insert("versions",null,v)<0)throw new IllegalStateException("Could not keep that version");
        prune(note);
    }

    /**
     * The texts this note has said here, newest first, by {@link Arriving#trace}: what it says now, then every
     * version kept. Said with the note when it goes, so a device holding one of them knows that what arrives
     * was written on top of what it has - see {@link Parcel.Sent#history}.
     */
    List<String> history(String note) {
        List<String> out=new ArrayList<>();
        Note now=get(note);
        if(now!=null)out.add(Arriving.trace(now.body));
        try(Cursor c=getReadableDatabase().query("versions",new String[]{"body"},"note=?",new String[]{note},
                null,null,"at DESC",String.valueOf(VERSIONS_KEPT))) {
            while(c.moveToNext()&&out.size()<Parcel.HISTORY_MOST) {
                String one=Arriving.trace(c.getString(0));
                if(!out.contains(one))out.add(one);
            }
        }
        return out;
    }

    /**
     * Every text this note has said or been sent here, by {@link Arriving#trace}, leaving out what it says now.
     * Each was weighed when it came, and is in the note or kept under Versions: the same words arriving again from
     * somebody else are behind what is here.
     */
    private java.util.Set<String> heldHere(String note) {
        java.util.Set<String> out=new java.util.HashSet<>();
        try(Cursor c=getReadableDatabase().query("versions",new String[]{"body"},"note=?",new String[]{note},
                null,null,null)) {
            while(c.moveToNext())out.add(Arriving.trace(c.getString(0)));
        }
        return out;
    }

    /** The versions of a note, newest first. */
    List<Version> versions(String note) {
        List<Version> all=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("versions",null,"note=?",new String[]{note},null,null,"at DESC")) {
            while(c.moveToNext())all.add(readVersion(c));
        }
        return all;
    }

    Version version(String id) {
        try(Cursor c=getReadableDatabase().query("versions",null,"id=?",new String[]{id},null,null,null,"1")) {
            return c.moveToFirst()?readVersion(c):null;
        }
    }

    private Version lastVersion(String note) {
        try(Cursor c=getReadableDatabase().query("versions",null,"note=?",new String[]{note},null,null,"at DESC","1")) {
            return c.moveToFirst()?readVersion(c):null;
        }
    }

    private static Version readVersion(Cursor c) {
        return new Version(c.getString(c.getColumnIndexOrThrow("id")),c.getString(c.getColumnIndexOrThrow("note")),
            c.getLong(c.getColumnIndexOrThrow("revision")),c.getLong(c.getColumnIndexOrThrow("at")),
            c.getString(c.getColumnIndexOrThrow("source")),c.getString(c.getColumnIndexOrThrow("title")),
            c.getString(c.getColumnIndexOrThrow("body")));
    }

    /** The oldest beyond what is kept are dropped: a note's history is long, not endless. */
    private void prune(String note) {
        getWritableDatabase().execSQL(
            "DELETE FROM versions WHERE note=? AND id NOT IN (SELECT id FROM versions WHERE note=? ORDER BY at DESC LIMIT ?)",
            new Object[]{note,note,VERSIONS_KEPT});
    }

    /**
     * What this note said at the revision this phone and that address last both had: the text a merge is
     * made against.
     *
     * <p>Exactly that revision where it was kept, and it is kept: what is sent is written down as a
     * version when it goes, and what arrives is written down as it arrives. Two phones count their own
     * revisions, so the same number can name two different texts — which is why this asks for the one
     * that was this phone's own or came from that address, and not for anybody's. Only where neither was
     * kept does it fall back to the nearest thing before.
     */
    String textAt(String note,long revision,String address) {
        try(Cursor c=getReadableDatabase().query("versions",new String[]{"body"},
                "note=? AND revision=? AND (source='' OR source=?)",
                new String[]{note,String.valueOf(revision),address==null?"":address},null,null,"at DESC","1")) {
            if(c.moveToFirst())return c.getString(0);
        }
        return textAt(note,revision);
    }

    /** Whatever a note said when an address was last given it: the text a merge is weighed against. */
    String textAt(String note,long revision) {
        try(Cursor c=getReadableDatabase().query("versions",new String[]{"body"},"note=? AND revision<=?",
                new String[]{note,String.valueOf(revision)},null,null,"revision DESC, at DESC","1")) {
            return c.moveToFirst()?c.getString(0):null;
        }
    }

    /**
     * A note that arrived from an address, weighed against what is here and written down accordingly. What
     * arrived is always kept as a version whatever happens to the page, so nothing anybody wrote is ever
     * only somewhere else.
     *
     * @param book where a note nobody here has seen before should land
     * @return what was decided, for the app to say out loud
     */
    Arriving.Decision landed(String id,String from,long revision,String title,String body,String book) {
        return landed(id,from,revision,title,body,book,-1L);
    }

    /**
     * @param basedOn the revision the sender believes both phones last had, or -1 where it did not say
     */
    Arriving.Decision landed(String id,String from,long revision,String title,String body,String book,
                             long basedOn) {
        return landed(id,from,revision,title,body,book,basedOn,null);
    }

    /**
     * @param history the texts the sender's note held before, by {@link Arriving#trace}, or null where it did not say
     */
    Arriving.Decision landed(String id,String from,long revision,String title,String body,String book,
                             long basedOn,List<String> history) {
        // Read and decided inside the transaction that writes it. This runs on the node's thread while
        // the page writes on its own, and a decision made from a note that was written a moment later is
        // a decision about a note that no longer exists.
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            Note here=get(id);
            // What this phone believes they have, and what they say they had: the older of the two.
            long baseRevision=Arriving.agreed(here==null?0:lastSeen(id,from),basedOn);
            String base=here==null?null:textAt(id,baseRevision,from);
            // Where the texts came from, asked before what arrived is kept: see Arriving.weigh.
            boolean theyHadMine=here!=null&&history!=null&&history.contains(Arriving.trace(here.body));
            boolean iHadTheirs=here!=null&&heldHere(id).contains(Arriving.trace(body));
            Arriving.Decision said=Arriving.weigh(here==null?null:here.body,here==null?0:here.revision,
                base,baseRevision,body,revision,theyHadMine,iHadTheirs);
            // What arrived is kept as it arrived, whether or not it is what the page ends up showing.
            keepVersion(id,revision,from,title==null?"":title,body==null?"":body);
            if(said.what!=Arriving.What.OLDER) {
                Note now=here==null?new Note():here;
                if(here==null){now.id=id;now.book=book;now.theirs=true;now.origin=from;}
                // The title is one word and cannot be put together line by line. Where only they have
                // written, theirs is taken; where both have, this phone keeps what it calls the note and
                // sends that back with everything else, so the two end up agreeing on it.
                String theirs=title==null?"":title;
                if(here==null||said.what!=Arriving.What.MERGED||now.title==null||now.title.trim().isEmpty())
                    now.title=theirs;
                now.body=said.text==null?"":said.text;
                now.revision=said.revision;now.updated=System.currentTimeMillis();
                // What is new here is theirs; what was here keeps whoever wrote it (see Writers).
                now.by=writerOf(from);
                save(db,now);
            }
            // Whoever sent it has it. Where what is kept is exactly what arrived, that is all there is
            // to say, and saying it is what stops this phone "owing" a note straight back to the phone
            // it came from — a message each way for every note, and a line on a page nobody had touched
            // saying it had not been sent. Where the two were put together, what is here now is something
            // they have not seen, and they are owed it.
            if(said.what==Arriving.What.NEW||said.what==Arriving.What.NEWER)agreedOn(from,id,revision);
            db.setTransactionSuccessful();
            return said;
        } finally {db.endTransaction();}
    }

    /**
     * A note that arrived with the collections it stood in.
     *
     * <p>They are built here if this is the first note out of them, named as the sender names them and
     * marked as theirs: from the top of its path down, where the parcel says the path (see {@link Things}),
     * or else its collection and its book, as a 0.1 device says them. Their ids are not reused as they stand
     * - see {@link Parcel#localId} for why a shared note filed under the id it arrived with would land inside
     * your own first collection.
     *
     * <p>A note already here is not moved. Somebody else deciding where your copy of a note lives, every
     * time they touch it, would undo any tidying you had done.
     */
    Arriving.Decision landed(String id,String from,long revision,Parcel.Sent parcel,String fallbackBook) {
        String book=fallbackBook;
        // A note of our own, come back to us. Somebody we shared it with has shared it on, or back, and
        // what arrives describes the shelf it sits on at their end. Ours is where it already is: building
        // their shelf here would leave a second, empty collection of the same name beside our own, which
        // is what happened the first time this was tried.
        Note already=get(id);
        boolean ours=already!=null&&!already.theirs;
        if(!ours&&parcel!=null&&parcel.path!=null) {
            book=pathFrom(from,parcel.path);
        } else if(!ours&&parcel!=null&&!parcel.book.trim().isEmpty()) {
            String collection=shelfFrom(from,parcel.collection,parcel.collectionName,null);
            book=shelfFrom(from,parcel.book,parcel.bookName,
                collection.isEmpty()?collectionOfBook(fallbackBook):collection);
            if(book.isEmpty())book=fallbackBook;
        }
        // A copy this phone may only read is not weighed against anything: see copied.
        boolean copy=already!=null&&already.theirs&&parcel!=null&&!parcel.writes;
        Arriving.Decision said=copy
            ?copied(id,from,revision,parcel.title,parcel.body,book)
            :landed(id,from,revision,parcel==null?"":parcel.title,
                parcel==null?"":parcel.body,book,parcel==null?-1L:parcel.basedOn,parcel==null?null:parcel.history);
        // And its icon and picture, where its path says them: see lookArrived.
        lookArrived(id,already,revision,parcel,copy,said);
        if(parcel!=null) {
            // What they say this end may do with it. Said every time, because they can change their mind.
            ContentValues v=new ContentValues();v.put("writes",parcel.writes?1:0);
            getWritableDatabase().update("notes",v,"id=? AND theirs=1",new String[]{id});
            // And where they are now, where the address it first came from is nobody's any more.
            ContentValues now=new ContentValues();now.put("origin",from);
            getWritableDatabase().update("notes",now,
                "id=? AND theirs=1 AND origin NOT IN (SELECT address FROM addresses)",new String[]{id});
        }
        return said;
    }

    /**
     * A note that arrived for a copy this phone may only read: what they sent is what it says.
     *
     * <p>Nothing here is put together with it. A reader's copy is a copy, and where it says something
     * else - written in on a build that let a reader write, or before they were made one - that is kept
     * as a version, so nothing anybody wrote is only somewhere else, and the page then says what its
     * owner says. See {@link Arriving#copy}.
     */
    private Arriving.Decision copied(String id,String from,long revision,String title,String body,String book) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            Note here=get(id);
            String came=body==null?"":body, called=title==null?"":title;
            Arriving.Decision said=Arriving.copy(here==null?null:here.body,here==null?0:here.revision,
                lastSeen(id,from),came,revision);
            // What this phone's copy said, before it stops saying it.
            if(here!=null&&said.what!=Arriving.What.OLDER
                &&(!here.body.equals(came)||!(here.title==null?"":here.title).equals(called)))keepVersion(id,"");
            keepVersion(id,revision,from,called,came);
            if(said.what!=Arriving.What.OLDER) {
                Note now=here==null?new Note():here;
                if(here==null){now.id=id;now.book=book;now.theirs=true;now.origin=from;}
                now.title=called;now.body=said.text==null?"":said.text;
                now.revision=said.revision;now.updated=System.currentTimeMillis();
                now.by=writerOf(from);
                save(db,now);
                agreedOn(from,id,revision);
            }
            db.setTransactionSuccessful();
            return said;
        } finally {db.endTransaction();}
    }

    /**
     * Somebody else's collection or book on this phone, made if it is not here yet: a 0.1 device's two levels.
     *
     * @param inside the collection a book goes in, or null when the shelf being named is a collection on Home
     * @return the local id of that shelf, or empty when there was nothing to name
     */
    private String shelfFrom(String address,String theirs,String name,String inside) {
        // A 0.1 device says no icon: null, so one already here is not taken off by what never said it.
        return collectionFrom(address,theirs,name,null,Tint.NONE,-System.currentTimeMillis(),inside==null?Things.HOME:inside);
    }

    /**
     * Somebody else's collections, from the top of a path down, made here where they are not yet: the first on
     * Home, each of the others inside the one before (see {@link Things}).
     *
     * @return the local id of the last, which is where a note of theirs goes; {@link Things#HOME} for a path
     *         with nothing on it, which is a note on their Home
     */
    private String pathFrom(String address,List<Parcel.Step> path) {
        String parent=Things.HOME;
        for(Parcel.Step step:path) {
            String here=collectionFrom(address,step.id,step.name,step.icon,step.tint,step.ordinal,parent);
            // A step that names nothing is not a collection: what came below it goes in the last one that was.
            if(here.isEmpty())break;
            parent=here;
        }
        return parent;
    }

    /**
     * One collection of somebody else's on this phone, made if it is not here yet.
     *
     * <p>Named as they name it, and marked as theirs. Its colour and where it goes among what is beside it are used
     * only as it is made (docs/HOME.md, decision 17): after that they are yours to change. One already here is renamed
     * where it is theirs, and given the icon they say, as its name is (step 4); never moved - where you put it is yours.
     *
     * @param icon   its icon by name, empty for its default, or null where what arrived does not say - a 0.1 device's
     *               two old fields - and one already here keeps what it has
     * @param parent where it goes if it is made: {@link Things#HOME}, or a collection's id here
     * @return the local id of that collection, or empty when there was nothing to name
     */
    private String collectionFrom(String address,String theirs,String name,String icon,int tint,long ordinal,String parent) {
        if(theirs==null||theirs.trim().isEmpty())return "";
        // Named after the device, not the address it was at.
        //
        // An address moves - a node that restarts is at a new one - and a shelf filed under the old one
        // arrived again under a new name and became a second, empty copy of itself beside the first. The
        // key a device signs with does not move, so the same shelf is the same shelf.
        String id=shelfId(address,theirs);
        // A shelf filed the old way is adopted rather than left behind with the notes already on it. Not
        // where the id arrived already made up: that names a shelf that is somewhere already.
        String was=Parcel.localId(address,theirs);
        if(!theirs.startsWith(Parcel.FROM)&&!was.equals(id))adopt(was,id);
        String called=name==null||name.trim().isEmpty()?"Shared":name.trim();
        if(there("things",id)) {
            // Here already. Only what they call it, and the icon they give it, are followed; where you put it is yours.
            ContentValues rename=new ContentValues();rename.put("name",called);
            if(icon!=null)rename.put("icon",iconFrom(icon));
            getWritableDatabase().update("things",rename,"id=? AND theirs=1",new String[]{id});
            return id;
        }
        long now=System.currentTimeMillis();
        ContentValues v=new ContentValues();
        v.put("id",id);v.put("parent",home(parent)?Things.HOME:parent);v.put("kind","collection");v.put("name",called);
        v.put("icon",iconFrom(icon));v.put("tint",Tint.known(tint)?tint:Tint.NONE);
        v.put("ordinal",ordinal);v.put("made",now);v.put("updated",now);
        v.put("theirs",1);v.put("origin",address);
        return getWritableDatabase().insert("things",null,v)<0?"":id;
    }

    /**
     * What a shelf named in something that arrived is called on this phone. See {@link Parcel#shelfHere}:
     * one of this phone's own, come home; or the name it was given when it first left its owner.
     */
    private String shelfId(String address,String theirs) {
        if(theirs==null||theirs.trim().isEmpty())return "";
        Contact who=address(address);
        String by=who==null||who.signing.length==0?address:canonical(who.signing);
        List<String> own=new ArrayList<>();
        if(theirs.startsWith(Parcel.FROM))
            try(Cursor c=getReadableDatabase().query("things",new String[]{"id"},"theirs=0",null,null,null,null)) {
                while(c.moveToNext())own.add(c.getString(0));
            }
        return Parcel.shelfHere(mySigningKey,own,by,theirs);
    }

    /** The sender's ids for the top two collections above a note, from its path, or else its two old fields. */
    private static String[] topTwo(Parcel.Sent parcel) {
        if(parcel.path!=null)return new String[]{parcel.path.size()>0?parcel.path.get(0).id:"",parcel.path.size()>1?parcel.path.get(1).id:""};
        return new String[]{parcel.collection,parcel.book};
    }

    /**
     * Where a note or a collection that arrived says it is, as this device's own ids: the collections its path names,
     * from the top down, and whatever holds the first of them here. Empty where it said nothing.
     */
    private List<String> sentPath(String from,Parcel.Sent parcel) {
        List<String> sent=new ArrayList<>();
        if(parcel.path!=null)for(Parcel.Step step:parcel.path){String one=shelfId(from,step.id);if(!one.isEmpty())sent.add(one);}
        else {
            String book=shelfId(from,parcel.book), collection=shelfId(from,parcel.collection);
            if(collection.isEmpty()&&!book.isEmpty())collection=collectionOfBook(book);
            if(!collection.isEmpty())sent.add(collection);
            if(!book.isEmpty())sent.add(book);
        }
        if(!sent.isEmpty())sent.addAll(0,above(sent.get(0)));
        return sent;
    }

    // ---- an offer taken up, until they answer -------------------------------------------------------------

    /** One acceptance still waiting to be heard. */
    static final class Accepting {
        final String address,name,scope,target; final boolean writes; final int tries;
        /** What was accepted, as the hello says it again. */
        final Sharing.Level level;
        Accepting(String address,String name,String scope,String target,boolean writes,int tries) {
            this(address,name,scope,target,writes?Sharing.Level.WRITE:Sharing.Level.READ,tries);
        }
        Accepting(String address,String name,String scope,String target,Sharing.Level level,int tries) {
            this.address=address;this.name=name;this.scope=scope;this.target=target;
            this.level=Pairing.offered(level);this.writes=this.level.writes();this.tries=tries;
        }
    }

    /** Kept the moment it is sent, because the sending may be to nobody. */
    void accepting(String address,String name,String scope,String target,boolean writes) {
        accepting(address,name,scope,target,writes?Sharing.Level.WRITE:Sharing.Level.READ);
    }

    /** @param level kept in the column that said whether it writes, as the byte the hello says it in (see Hello.said) */
    void accepting(String address,String name,String scope,String target,Sharing.Level level) {
        ContentValues v=new ContentValues();
        v.put("address",address);v.put("name",name==null?"":name);
        v.put("scope",scope);v.put("target",target);v.put("writes",Hello.said(level));
        v.put("at",System.currentTimeMillis());v.put("tries",0);
        getWritableDatabase().insertWithOnConflict("accepting",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * What was accepted, out of the bin or the archive where this device had put an earlier copy of it: accepting it again is
     * wanting it, and it arrived into the bin unseen (the owner, 2026-10-02). Its own id here is the one it arrives under.
     *
     * @return whether a copy was brought back
     */
    boolean acceptedBack(String address,String target) {
        if(target==null||target.isEmpty())return false;
        // A collection is kept here under an id of its own made from theirs; a note under theirs.
        List<String> ids=new ArrayList<>();String made=shelfId(address,target);
        if(!made.isEmpty())ids.add(made);
        ids.add(target);
        for(Branch.Kind kind:new Branch.Kind[]{Branch.Kind.COLLECTION,Branch.Kind.PAGE})for(String here:ids) {
            String away=kind==Branch.Kind.PAGE?"deleted":"binned";
            try(Cursor c=getReadableDatabase().query(table(kind),new String[]{away,"archived"},"id=?",new String[]{here},null,null,null,"1")) {
                if(!c.moveToFirst())continue;
                if(c.getInt(0)==0&&c.getInt(1)==0)return false;
            }
            restore(kind,here);
            return true;
        }
        return false;
    }

    /** Everything still unanswered, oldest first, and not tried past all reason. */
    List<Accepting> waitingToAccept() {
        List<Accepting> waiting=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("accepting",null,"tries<?",new String[]{"60"},
                null,null,"at ASC")) {
            while(c.moveToNext())waiting.add(new Accepting(
                c.getString(c.getColumnIndexOrThrow("address")),c.getString(c.getColumnIndexOrThrow("name")),
                c.getString(c.getColumnIndexOrThrow("scope")),c.getString(c.getColumnIndexOrThrow("target")),
                Hello.level(c.getInt(c.getColumnIndexOrThrow("writes"))),c.getInt(c.getColumnIndexOrThrow("tries"))));
        }
        return waiting;
    }

    void triedAgain(String address) {
        getWritableDatabase().execSQL("UPDATE accepting SET tries=tries+1 WHERE address=?",
            new String[]{address});
    }

    /**
     * They answered: something of theirs is here, so there is nothing left to ask for.
     *
     * <p>By device rather than by address. A note arrives from whichever address of theirs this phone
     * happened to file them under, which need not be the one their code was read at.
     */
    void answered(String address) {
        getWritableDatabase().execSQL(
            "DELETE FROM accepting WHERE address=? OR address IN ("
            +"SELECT a.address FROM addresses a WHERE a.signing<>'' AND a.signing="
            +"(SELECT b.signing FROM addresses b WHERE b.address=?))",
            new String[]{address,address});
    }

    // ---- what this phone will no longer take in ----------------------------------------------------------

    /**
     * Stop taking in what somebody sends of one thing.
     *
     * <p>It cannot stop them sending: only they can decide that, and this end has no say over another
     * phone. What it stops is the arriving being put on the shelves, which is the part this phone owns -
     * so nothing new turns up, and what is already here stays here until it is deleted like anything else.
     */
    void refuse(String address,String target,Branch.Kind kind) {
        ContentValues v=new ContentValues();
        v.put("address",address);v.put("target",target);
        v.put("kind",kind==null?"note":kind.name().toLowerCase(java.util.Locale.ROOT));
        v.put("at",System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("refused",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Take it again. */
    void accept(String address,String target) {
        getWritableDatabase().delete("refused","address=? AND target=?",new String[]{address,target});
    }

    /** Whether this phone is refusing that one thing from that one address. */
    boolean refusing(String address,String target) {
        if(target==null||target.trim().isEmpty())return false;
        try(Cursor c=getReadableDatabase().query("refused",new String[]{"target"},"address=? AND target=?",
                new String[]{address,target},null,null,null,"1")) {
            return c.moveToFirst();
        }
    }

    /** One thing this phone does not take in: stopped for now, or left. */
    static final class Refusal {
        final String target,kind; final long at; final boolean gone;
        /** What a 0.1 device calls it, worked out from where it stands here; see {@link #scope}. */
        private final Sharing.Scope said;
        Refusal(String target,String kind,long at,boolean gone) {
            this(target,kind,at,gone,"book".equals(kind)?Sharing.Scope.BOOK:"collection".equals(kind)?Sharing.Scope.COLLECTION
                :Sharing.Scope.PAGE);
        }
        Refusal(String target,String kind,long at,boolean gone,Sharing.Scope said) {
            this.target=target;this.kind=kind;this.at=at;this.gone=gone;this.said=said;
        }
        /**
         * The level it was left at, which is what the others are told: a note, or a collection by how deep it sits
         * (see {@link Things#oldScope}). Null for a collection deeper than a 0.1 device's three levels, which has no
         * word to be told in.
         */
        Sharing.Scope scope(){return said;}
    }

    /** One row of what is refused, with the level it is told at worked out from where the thing stands here. */
    private Refusal refused(String target,String kind,long at,boolean gone) {
        boolean collection="book".equals(kind)||"collection".equals(kind);
        if(!collection||!there("things",target))return new Refusal(target,kind,at,gone);
        return new Refusal(target,kind,at,gone,Things.oldScope(above(target),false));
    }

    /**
     * Whatever refuses a note arriving from somebody - the note itself, or any collection it arrived in, or is
     * in here - or null where nothing does. Refusing a collection has to refuse the notes in it, however deep,
     * or stopping would stop nothing.
     *
     * <p>A collection is looked for under the name it has here. It was looked for under a name made from the
     * sender's address, where shelves have been filed by the sender's key since addresses were found to
     * move - so a book that had been stopped went on taking in every note sent out of it.
     */
    Refusal refusal(String address,String note,Parcel.Sent parcel) {
        Refusal found=refusalOf(address,note);
        if(found!=null)return found;
        for(String one:above(note)){found=refusalOf(address,one);if(found!=null)return found;}
        if(parcel==null)return null;
        List<String> sent=sentPath(address,parcel);
        for(int at=sent.size()-1;at>=0;at--){found=refusalOf(address,sent.get(at));if(found!=null)return found;}
        return null;
    }

    /** The same for a collection arriving on its own (see {@link Carton}): itself, or anything above it. */
    Refusal refusal(String address,Carton.Sent carton) {
        String here=shelfId(address,carton.id);
        Refusal found=refusalOf(address,here);
        if(found!=null)return found;
        List<String> up=there("things",here)?above(here):new ArrayList<>();
        for(Parcel.Step step:carton.path){String one=shelfId(address,step.id);if(!one.isEmpty()&&!up.contains(one))up.add(one);}
        for(int at=up.size()-1;at>=0;at--){found=refusalOf(address,up.get(at));if(found!=null)return found;}
        return null;
    }

    boolean refusingParcel(String address,String note,Parcel.Sent parcel) {
        return refusal(address,note,parcel)!=null;
    }

    /** By device rather than by address: somebody's address moves, and what was refused of them stays so. */
    private Refusal refusalOf(String address,String target) {
        if(target==null||target.trim().isEmpty())return null;
        Contact from=address(address);
        String key=from==null?"":canonical(from.signing);
        try(Cursor c=getReadableDatabase().query("refused",new String[]{"address","kind","at","gone"},
                "target=?",new String[]{target},null,null,null)) {
            while(c.moveToNext()) {
                String theirs=c.getString(0);
                boolean same=theirs.equals(address);
                if(!same&&!key.isEmpty()) {
                    Contact kept=address(theirs);
                    same=kept!=null&&key.equals(canonical(kept.signing));
                }
                if(same)return refused(target,c.getString(1),c.getLong(2),c.getInt(3)==1);
            }
        }
        return null;
    }

    // ---- leaving, and being left ---------------------------------------------------------------------------

    /**
     * The kind of thing a level is about: a note, a collection - which is what a book, and anything a rule was made on
     * since collections nested, is now - and everything for the library.
     */
    static Branch.Kind kindFor(Sharing.Scope scope) {
        return scope==Sharing.Scope.PAGE?Branch.Kind.PAGE:onShelf(scope)?Branch.Kind.COLLECTION:Branch.Kind.LIBRARY;
    }

    /**
     * Left. What is here stays, and is this phone's own from now on; nothing more of it is taken in from
     * anybody who had it, and nothing of it is owed to them.
     *
     * <p>Telling them is the post's business and is done first - see {@link Post#leave}. This is the half
     * that has to happen whether or not anybody could be told.
     *
     * @param everybody every address that had it, each of whom may still send it before they hear
     * @param now       when it was left, which is what they are told and what a later invitation is later than
     */
    void letGo(Branch.Kind kind,String id,java.util.Collection<String> everybody,long now) {
        boolean note=kind==Branch.Kind.PAGE;
        if(!note&&!shelf(kind)||id==null||id.isEmpty()||home(id))return;
        Sharing.Scope scope=note?Sharing.Scope.PAGE:Sharing.Scope.THING;
        List<Outbox.Page> pages=pagesUnder(kind,id);
        Tree tree=tree();
        List<String> up=above(tree,id), inside=note?new ArrayList<>():tree.within(id);
        List<String> notes=note?new ArrayList<>():notesWithin(tree,id);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(String address:everybody) {
                ContentValues v=new ContentValues();
                v.put("address",address);v.put("target",id);
                v.put("kind",note?"page":"collection");
                v.put("at",now);v.put("gone",1);
                db.insertWithOnConflict("refused",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            }
            for(Outbox.Page page:pages)db.delete("handed","page=?",new String[]{page.id});
            db.delete("handed","page=?",new String[]{id});
            db.delete("shares",scopeIs(scope)+" AND target=?",new String[]{id});
            db.delete("standing",scopeIs(scope)+" AND target=?",new String[]{id});
            // Where it came from is kept, for the day it is given again. Whose it is, is not - of it, and of
            // everything inside it however deep.
            ContentValues mine=new ContentValues();mine.put("theirs",0);
            if(note)db.update("notes",mine,"id=?",new String[]{id});
            for(String one:inside)db.update("things",mine,"id=?",new String[]{one});
            for(String one:notes)db.update("notes",mine,"id=?",new String[]{one});
            // And whatever held it, nearest first, that was built here only to hold what they sent.
            for(int at=up.size()-1;at>=0;at--)emptied(db,up.get(at));
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    /** Every note inside a collection however deep, wherever it is: on the shelves, put away, or in the bin. */
    private List<String> notesWithin(Tree tree,String collection) {
        List<String> found=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,book FROM notes",null)) {
            while(c.moveToNext())if(tree.aboveIn(c.getString(1)).contains(collection))found.add(c.getString(0));
        }
        return found;
    }

    /**
     * A shelf that was built here to hold what somebody sent, with nothing of theirs left on it.
     *
     * <p>It is an ordinary shelf now. And the line saying whoever sent its notes has the shelf - which
     * nobody ever decided: it was worked out from notes arriving - has nothing left to be worked out from,
     * so it goes. Left standing, it kept the note that had just been let go going to them by way of the
     * shelf, and a note somebody has unfollowed went on wearing the mark of a shared one.
     */
    private void emptied(SQLiteDatabase db,String shelf) {
        if(shelf==null||home(shelf))return;
        Tree tree=tree(db);
        try(Cursor c=db.rawQuery("SELECT book FROM notes WHERE theirs=1",null)) {
            while(c.moveToNext())if(tree.aboveIn(c.getString(0)).contains(shelf))return;
        }
        ContentValues mine=new ContentValues();mine.put("theirs",0);
        db.update("things",mine,"id=?",new String[]{shelf});
        String scope=scopeIs(Sharing.Scope.THING);
        db.delete("shares",scope+" AND target=? AND changed<=1",new String[]{shelf});
        db.delete("standing",scope+" AND target=?",new String[]{shelf});
    }

    /**
     * One leaving that may not have been heard yet: who was told, what was left, and a note that names it. The level is
     * what a 0.1 device calls the thing (see {@link Things#oldScope}), and null for a collection deeper than that.
     */
    static final class Leaving {
        final String address,note; final Sharing.Scope scope; final long at;
        Leaving(String address,String note,Sharing.Scope scope,long at) {
            this.address=address;this.note=note;this.scope=scope;this.at=at;
        }
    }

    /**
     * What this phone has left lately, to be said again. Saying it is one message to a phone that may be
     * asleep, and a message to a phone that is asleep is gone - the first one sent from a real phone went
     * exactly that way. Nobody answers it, so there is no knowing; it is said again for a week, which costs
     * a few bytes, and a phone that has heard already does nothing about hearing it twice.
     */
    List<Leaving> leavings() {
        List<Leaving> all=new ArrayList<>();
        long since=System.currentTimeMillis()-7L*24*60*60*1000;
        try(Cursor c=getReadableDatabase().query("refused",new String[]{"address","target","kind","at"},
                "gone=1 AND at>?",new String[]{String.valueOf(since)},null,null,null)) {
            while(c.moveToNext()) {
                Refusal one=refused(c.getString(1),c.getString(2),c.getLong(3),true);
                Branch.Kind kind="book".equals(one.kind)||"collection".equals(one.kind)?Branch.Kind.COLLECTION:Branch.Kind.PAGE;
                // Any note still here out of it names it. Where none is left, there is nothing to name it by.
                for(Outbox.Page page:pagesUnder(kind,one.target)) {
                    all.add(new Leaving(c.getString(0),page.id,one.scope(),one.at));
                    break;
                }
            }
        }
        return all;
    }

    /**
     * Whoever this phone has taken off something lately, to be told again - for the reason
     * {@link #leavings} is: the telling is one message to a phone that may be asleep, nobody answers it,
     * and hearing it twice does nothing. Only for a thing this phone has a say in, which is what the phone
     * hearing it checks; a row that says somebody left by themselves is among these too, and their phone,
     * where the thing is already its own, does nothing about it.
     */
    List<Leaving> removals() {
        List<Leaving> all=new ArrayList<>();
        long since=System.currentTimeMillis()-7L*24*60*60*1000;
        try(Cursor c=getReadableDatabase().query("shares",new String[]{"scope","target","address","changed"},
                "level=0 AND changed>?",new String[]{String.valueOf(since)},null,null,null)) {
            while(c.moveToNext()) {
                Sharing.Scope scope;
                try{scope=Sharing.Scope.valueOf(c.getString(0));}catch(IllegalArgumentException unknown){continue;}
                if(scope==Sharing.Scope.LIBRARY)continue;
                String target=c.getString(1);
                if(!saysWhoHas(scope,target))continue;
                // Told at the level a 0.1 device calls it, by how deep it sits - which every build hears the same way.
                Sharing.Scope said=oldScopeOf(scope,target);
                for(Outbox.Page page:pagesUnder(kindFor(scope),target)) {
                    all.add(new Leaving(c.getString(2),page.id,said,c.getLong(3)));
                    break;
                }
            }
        }
        return all;
    }

    /** Whether this device is whoever a thing is from: nothing says it came from anybody else. */
    boolean owns(Sharing.Scope scope,String target) {
        return scope!=null&&ownersOwn(cameFrom(kindFor(scope),target));
    }

    /** What this device may give others in a thing, most first: see Sharing.grantable. */
    List<Sharing.Level> mayGive(Sharing.Scope scope,String target) {
        if(scope==null||target==null||target.isEmpty())return List.of();
        boolean owner=owns(scope,target);
        return Sharing.grantable(owner,owner?null:myLevel(scope,target));
    }

    /**
     * Whether this device may change what one member may do, or take them off: see Sharing.mayChange. The owner of a
     * thing that came from somebody is whoever it came from, found by address or by the key it signs with.
     */
    boolean mayChange(Sharing.Rule rule) {
        if(rule==null||rule.target.isEmpty())return false;
        boolean owner=owns(rule.scope,rule.target);
        return Sharing.mayChange(owner,owner?null:myLevel(rule.scope,rule.target),rule.level,ownerOf(rule));
    }

    /** Whether one member of a thing is whoever it came from. */
    boolean ownerOf(Sharing.Rule rule) {
        if(rule==null)return false;
        return ownerMember(rule.address,rule.key,cameFrom(kindFor(rule.scope),rule.target));
    }

    /**
     * Whether a thing that came from here is the owner's own: made on this device, or on another device of the owner's
     * - one marked My device here. The owner is a person, and their devices are all of them the owner.
     */
    private boolean ownersOwn(String origin) {
        return origin==null||origin.isEmpty()||mineDevice(origin,keyOf(origin));
    }

    /** Whether a device, by address or else by key, is one of the owner's own here. */
    private boolean mineDevice(String address,String key) {
        Contact who=address==null?null:address(address);
        if(who==null&&key!=null&&!key.isEmpty())who=byKey(key);
        return who!=null&&who.mine;
    }

    /** Whether a member of a thing, by address or key, is its owner: whoever it came from, or one of the owner's own devices. */
    private boolean ownerMember(String address,String key,String origin) {
        if(isOwner(address,origin))return true;
        if(origin!=null&&!origin.isEmpty()&&key!=null&&!key.isEmpty()&&key.equals(keyOf(origin)))return true;
        return ownersOwn(origin)&&mineDevice(address,key);
    }

    /**
     * A member given a role here, or taken off ({@code GONE}) - refused where this device has no say in that: an admin
     * gives Can write or Can read, and changes nobody who is the owner or an admin. The one way both apps change a role.
     */
    void decide(Sharing.Rule rule,Sharing.Level to,long when) {
        if(to==Sharing.Level.GONE) {
            if(!mayChange(rule))throw new IllegalStateException(ownerOf(rule)?"Nobody can take the owner off."
                :"Only its owner can take an admin off, and only its owner or an admin can take anybody off.");
            removeShare(rule,when);
            return;
        }
        if(!mayChange(rule)||!mayGive(rule.scope,rule.target).contains(to))
            throw new IllegalStateException(ownerOf(rule)?"Nobody can change the owner's role."
                :"Only its owner can make somebody an admin or change an admin's role.");
        setLevel(rule.scope,rule.target,rule.address,to,null,when);
    }

    /** Somebody not on a thing yet given a role in it, refused where this device may not give that. */
    void give(Sharing.Scope scope,String target,String address,Sharing.Level level,String name) {
        if(!mayGive(scope,target).contains(level))
            throw new IllegalStateException(level==Sharing.Level.ADMIN?"Only its owner can make somebody an admin."
                :"Only its owner or an admin can share this.");
        setLevel(scope,target,address,level,name);
    }

    /** Whether a device is whoever a thing came from: by the address it came from, or the key it signs with. */
    private boolean isOwner(String from,String origin) {
        if(from==null)return false;
        // Another of the owner's devices, for a thing that is the owner's own: the same person.
        if(ownersOwn(origin)&&mineDevice(from,""))return true;
        if(origin==null||origin.isEmpty())return false;
        if(origin.equals(from))return true;
        Contact who=address(from);
        String key=who==null?"":canonical(who.signing);
        Contact owner=address(origin);
        return owner!=null&&!key.isEmpty()&&key.equals(canonical(owner.signing));
    }

    /**
     * Whose a thing is here: empty for this device's own, the device it came from, or null where it is not here at
     * all - in which case it will be whoever sends it first.
     */
    private String whoseHere(Branch.Kind kind,String id) {
        if(kind!=Branch.Kind.PAGE&&!shelf(kind))return "";
        try(Cursor c=getReadableDatabase().query(table(kind),new String[]{"theirs","origin"},"id=?",
                new String[]{id},null,null,null,"1")) {
            if(!c.moveToFirst())return null;
            return c.getInt(0)!=1||c.getString(1)==null?"":c.getString(1);
        }
    }

    /** Whether this phone has a say in who has a thing: it is this phone's own, or this phone is an admin of it. */
    boolean saysWhoHas(Sharing.Scope scope,String target) {
        if(scope==null||target==null||target.isEmpty())return false;
        if(cameFrom(kindFor(scope),target).isEmpty())return true;
        Sharing.Level mine=myLevel(scope,target);
        return mine!=null&&mine.shares();
    }

    /**
     * Somebody says this phone is off something of theirs. Taken at their word only where they have a say
     * in it - it came from them, or they are an admin of it here - and only for a thing that is theirs.
     * What is here then becomes this phone's own exactly as it does on leaving, see {@link #letGo}, as of
     * when they decided it, so that being given the thing again afterwards is later than the taking off.
     *
     * @param note any note out of what this phone is off; which shelf that means is worked out from this phone's own
     * @param when when they decided it, by their clock
     * @return whether that changed anything here
     */
    boolean takenOff(String from,String note,Sharing.Scope scope,long when) {
        if(scope==null)return false;
        if(when<=0)when=System.currentTimeMillis();
        String target=heardAbout(note,scope);
        if(target==null||target.isEmpty())return false;
        Branch.Kind kind=kindFor(scope);
        String origin=cameFrom(kind,target);
        if(origin.isEmpty()||!hasASay(from,scope,target,origin))return false;
        // An admin is taken off by the owner, and by no other admin.
        if(!isOwner(from,origin)&&myLevel(scope,target)==Sharing.Level.ADMIN)return false;
        letGo(kind,target,everybodyIn(kind,target),when);
        return true;
    }

    /**
     * Which thing here a word about a note names, at the level a 0.1 device says it in (see {@link Receipt#left}): the
     * note itself; for a collection, the note's top collection here; for a book, its second. That is where a device
     * that knows three levels keeps its collection and its book, and where every note moved from 0.1 still is.
     */
    private String heardAbout(String note,Sharing.Scope scope) {
        if(scope==Sharing.Scope.PAGE)return get(note)==null?"":note;
        List<String> up=get(note)==null?new ArrayList<>():above(note);
        if(scope==Sharing.Scope.COLLECTION)return up.isEmpty()?"":up.get(0);
        if(scope==Sharing.Scope.BOOK)return up.size()<2?"":up.get(1);
        return "";
    }

    /** Whether a device may say who has a thing of somebody's: it is whose the thing is, or an admin of it here. */
    private boolean hasASay(String from,Sharing.Scope scope,String target,String origin) {
        if(isOwner(from,origin))return true;
        Contact who=address(from);
        String key=who==null?"":canonical(who.signing);
        for(Sharing.Rule rule:membership(scope,target))
            if((rule.address.equals(from)||(!key.isEmpty()&&key.equals(rule.key)))&&rule.level.shares())return true;
        return false;
    }

    /**
     * Somebody says they have left something. They are taken off it, as a decision like any other, so
     * that an older copy of the list cannot put them back; and what was still waiting for their answer
     * stops waiting.
     *
     * <p>As of when they left, and only where nothing has been decided about them since. It is said more
     * than once, because the first time may not arrive; somebody who has been given the thing again in
     * the meantime is not taken off it by an old goodbye turning up late.
     *
     * @param note any note out of what they left; which shelf that means is worked out from this phone's own
     * @param when when they left, by their clock
     * @return whether that changed anything here
     */
    boolean left(String address,String note,Sharing.Scope scope,long when) {
        if(scope==null)return false;
        if(when<=0)when=System.currentTimeMillis();
        String target=heardAbout(note,scope);
        if(target==null||target.isEmpty())return false;
        Contact who=address(address);
        String key=who==null?"":canonical(who.signing);
        boolean any=false;
        for(Sharing.Rule rule:membership(scope,target)) {
            if(rule.level==Sharing.Level.GONE)continue;
            if(!rule.address.equals(address)&&(key.isEmpty()||!key.equals(rule.key)))continue;
            if(rule.changed>=when)continue;
            setLevel(scope,target,rule.address,Sharing.Level.GONE,null,when);
            any=true;
        }
        for(Outbox.Page page:pagesUnder(kindFor(scope),target))
            getWritableDatabase().delete("handed","address=? AND page=?",new String[]{address,page.id});
        getWritableDatabase().delete("handed","address=? AND page=?",new String[]{address,target});
        return any;
    }

    /**
     * Given again, after leaving. The leaving is forgotten and what is here is theirs once more, so what
     * arrives next is taken in like anything else of theirs.
     */
    void followAgain(String address,String note,Refusal left) {
        Tree tree=tree();
        boolean collection=tree.rows.containsKey(left.target);
        List<String> inside=collection?tree.within(left.target):new ArrayList<>();
        List<String> notes=collection?notesWithin(tree,left.target):new ArrayList<>();
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            db.delete("refused","target=? AND gone=1",new String[]{left.target});
            ContentValues theirs=new ContentValues();theirs.put("theirs",1);
            ContentValues from=new ContentValues();from.put("theirs",1);from.put("origin",address);
            db.update("notes",from,"id=?",new String[]{note});
            // A collection, and whatever inside it, however deep, came from somebody too.
            if(collection)db.update("things",theirs,"id=?",new String[]{left.target});
            for(String one:inside)if(!one.equals(left.target))db.update("things",theirs,"id=? AND origin<>''",new String[]{one});
            for(String one:notes)db.update("notes",theirs,"id=? AND origin<>''",new String[]{one});
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    /** The nearest thing a rule was given on - this, or any collection holding it, nearest first - or null. */
    Object[] givenOn(Sharing.Scope scope,String target,String name) {
        if(scope==null||target==null)return null;
        if(!membership(scope,target).isEmpty())return new Object[]{scope,target,name};
        if(scope==Sharing.Scope.LIBRARY)return null;
        List<String> up=above(target);
        for(int at=up.size()-1;at>=0;at--) {
            List<Sharing.Rule> rows=membership(Sharing.Scope.THING,up.get(at));
            if(!rows.isEmpty())return new Object[]{rows.get(0).scope,up.get(at),nameOf(up.get(at),true)};
        }
        return null;
    }

    /** What a collection at any depth is called, so the name can travel with a note out of it. */
    String nameOf(String id,boolean collection) {
        if(id==null||id.trim().isEmpty())return "";
        try(Cursor c=getReadableDatabase().query("things",new String[]{"name"},"id=?",new String[]{id},null,null,null,"1")) {
            return c.moveToFirst()?c.getString(0):"";
        }
    }

    /** What a thing is called in a sentence: a collection's name, a note's title; empty for everything. */
    String thingName(Branch.Kind kind,String id) {
        if(shelf(kind))return nameOf(id,true);
        if(kind!=Branch.Kind.PAGE)return "";
        Note note=get(id);
        return note==null?"":note.title==null||note.title.trim().isEmpty()?"Untitled":note.title.trim();
    }

    /**
     * What a device named in a list of people is called there, for a device never paired here: the list travels with
     * its names, so it can be said who it is. Empty where no list names it.
     */
    String memberName(String address) {
        try(Cursor c=getReadableDatabase().query("shares",new String[]{"name"},"address=? AND TRIM(name)<>''",
                new String[]{address},null,null,"level>0 DESC, added ASC","1")) {
            if(c.moveToFirst())return c.getString(0);
        }
        // Or the name another list gave the same device under an address it had before: a key does not move.
        String key=keyOf(address);
        if(key.isEmpty())return "";
        try(Cursor c=getReadableDatabase().query("shares",new String[]{"name"},"who=? AND TRIM(name)<>''",
                new String[]{key},null,null,"added ASC","1")) {
            return c.moveToFirst()?c.getString(0):"";
        }
    }

    /** The key a device signs with, as kept: from its pairing, or from a list that named it; empty where neither says. */
    private String keyOf(String address) {
        Contact known=address(address);
        if(known!=null&&known.signing.length>0)return canonical(known.signing);
        try(Cursor c=getReadableDatabase().query("shares",new String[]{"who"},"address=? AND who<>''",
                new String[]{address},null,null,"added ASC","1")) {
            return c.moveToFirst()?c.getString(0):"";
        }
    }

    /**
     * What a device is called on this device's screens, whether it was paired here or only named in a list: see
     * {@link SyncStatus#named}. Empty only for an empty address.
     */
    String nameFor(String address) {
        Contact known=address(address);
        // A list naming a device paired here, at an address it had before: what it is called here still wins over the
        // name the list gave it, which for one of your devices is the one it had when the list was made.
        String key=keyOf(address);
        if(known==null&&!key.isEmpty())known=byKey(key);
        return SyncStatus.named(known==null?"":known.name,memberName(address),key,address);
    }

    /** The first rule whose list of people names this address, for a thing that is still here; null where none does. */
    Sharing.Rule listing(String address) {
        for(Sharing.Rule rule:shares())
            if(rule.address.equals(address)&&!thingName(kindFor(rule.scope),rule.target).isEmpty())return rule;
        return null;
    }

    /** The first thing whose list of people names this address, by its name; empty where none does. */
    String listedIn(String address) {
        Sharing.Rule rule=listing(address);
        return rule==null?"":thingName(kindFor(rule.scope),rule.target);
    }

    /**
     * Whether this address may write back into this note, by whichever rule reaches it.
     *
     * <p>The sending end is the only end that knows: the rule lives here. So it is said in the parcel,
     * every time, and the receiving end can show what it is allowed rather than having to guess.
     */
    boolean mayWrite(String address,String collection,String book,String page) {
        return Boolean.TRUE.equals(Sharing.audience(shares(),collection,book,page).get(address));
    }

    /** The same along a thing's whole path: every collection above it from Home down, and then itself. */
    boolean mayWrite(String address,List<String> path) {
        return Boolean.TRUE.equals(Sharing.audience(shares(),path).get(address));
    }

    /**
     * What the device a note arrived from may do with it here: the rule that says the most among every
     * one that reaches the note - on the shelf it is on, or on the shelf it was sent out of, since a note
     * of theirs may have been tidied elsewhere here - or null where nothing names them.
     *
     * <p>This is what decides whether their words are written down. Asked after the list that came with
     * the note has been folded in, so that the first thing ever to arrive from somebody is reached by the
     * line saying they have it. Rows saying somebody is off a thing are read too: what arrives from
     * them is not taken in either, and they have something to be told.
     */
    Sharing.Rule standingOf(String from,String id,Parcel.Sent parcel) {
        List<Sharing.Rule> rules=everyRule();
        Contact who=address(from);
        String key=who==null?"":canonical(who.signing);
        Set<String> theirs=new HashSet<>();theirs.add(from);
        if(!key.isEmpty())for(Contact one:addresses())if(key.equals(canonical(one.signing)))theirs.add(one.address);
        Sharing.Rule most=null;
        Note here=get(id);
        if(here!=null)most=Sharing.standing(rules,pathOf(id),theirs,key);
        if(parcel!=null) {
            List<String> sent=sentPath(from,parcel);sent.add(id);
            Sharing.Rule by=Sharing.standing(rules,sent,theirs,key);
            if(by!=null&&(most==null||by.level.ordinal()>most.level.ordinal()))most=by;
        }
        return most;
    }

    /** The same for a collection arriving on its own (see {@link Carton}), by its id here. */
    private Sharing.Rule standingOf(String from,String here,List<Parcel.Step> path) {
        List<Sharing.Rule> rules=everyRule();
        Contact who=address(from);
        String key=who==null?"":canonical(who.signing);
        Set<String> theirs=new HashSet<>();theirs.add(from);
        if(!key.isEmpty())for(Contact one:addresses())if(key.equals(canonical(one.signing)))theirs.add(one.address);
        Sharing.Rule most=there("things",here)?Sharing.standing(rules,pathOf(here),theirs,key):null;
        List<String> sent=new ArrayList<>();
        for(Parcel.Step step:path){String one=shelfId(from,step.id);if(!one.isEmpty())sent.add(one);}
        if(!sent.isEmpty())sent.addAll(0,above(sent.get(0)));
        sent.add(here);
        Sharing.Rule by=Sharing.standing(rules,sent,theirs,key);
        if(by!=null&&(most==null||by.level.ordinal()>most.level.ordinal()))most=by;
        return most;
    }

    /** Every row of the membership tables, the people taken off included. */
    private List<Sharing.Rule> everyRule() {
        List<Sharing.Rule> rules=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("shares",null,null,null,null,null,"added ASC")) {
            while(c.moveToNext())rules.add(rule(c));
        }
        return rules;
    }

    /**
     * Whether this phone may only read a note: it came from somebody, and nothing says this phone may
     * write in it. Asked by the page, which then takes no writing. A note this phone has left is its own
     * and is written in like any other.
     *
     * @return whether it is read-only, and the name of whoever it came from
     */
    Object[] readOnlyHere(String id) {
        Note note=get(id);
        if(note==null||!note.theirs)return new Object[]{false,""};
        Contact who=address(note.origin);
        return new Object[]{onlyReads(id),who==null?"":who.name};
    }

    /**
     * Whether a thing is still on the shelves: here, and neither binned nor archived.
     *
     * <p>Asked of the place you are standing in, because the place you were standing in yesterday can have
     * been put away since. A level that is gone lists nothing and says nothing, so without this the app
     * shows you the inside of a binned collection with its name at the top - and the library, one tap
     * above, saying there are no collections at all.
     */
    boolean stillThere(Branch.Kind kind,String id) {
        if(kind!=Branch.Kind.PAGE&&!shelf(kind))return true;
        if(shelf(kind)&&home(id))return true;
        boolean note=kind==Branch.Kind.PAGE;
        try(Cursor c=getReadableDatabase().query(table(kind),new String[]{"id"},"id=? AND "+(note?HERE:LIVE),
                new String[]{id},null,null,null,"1")) {
            if(!c.moveToFirst())return false;
        }
        // And everything that holds it: a collection put in the bin takes what is inside it along, however deep.
        Tree tree=tree();
        return tree.live(above(tree,id));
    }

    /**
     * Which collection a note is in, and which collection a collection is in: what an undo needs to put one back.
     * Empty for one on Home, which is where moving it to empty puts it back.
     */
    String bookOf(String note){String said=parentOf("notes","book",note);return said==null||home(said)?"":said;}
    String collectionOfBook(String book){String said=parentOf("things","parent",book);return said==null||home(said)?"":said;}

    /** The revision of this note that an address was last known to have. */
    /** The revision this phone takes itself and that address to have last both had. */
    long agreedAt(String note,String address){return lastSeen(note,address);}

    private long lastSeen(String note,String address) {
        // What the two last agreed on, which is not what was last delivered - see SchemaMigrations.AGREED.
        try(Cursor c=getReadableDatabase().query("sent",new String[]{"agreed"},"page=? AND address=?",
                new String[]{note,address},null,null,null,"1")) {
            return c.moveToFirst()?c.getLong(0):0;
        }
    }

    /** Deletes a page outright. The app has no trash to hide it in, so nothing pretends it is recoverable. */
    void remove(String id) { getWritableDatabase().delete("notes","id=?",new String[]{id}); getWritableDatabase().delete("writers","note=?",new String[]{id}); }

    /** The page written to most recently, anywhere on the shelves, or null on a first run. */
    Note latest() {
        try(Cursor c=getReadableDatabase().query("notes",null,HERE,null,null,null,"updated DESC","1")) {
            return c.moveToFirst()?read(c,true):null;
        }
    }

    /** The full page, or null if it is no longer stored. */
    Note get(String id) {
        try(Cursor c=getReadableDatabase().query("notes",null,"id=?",new String[]{id},null,null,null,"1")) {
            return c.moveToFirst()?read(c,true):null;
        }
    }

    /** Rows for one collection, or for Home, in the reader's own order, carrying previews rather than whole pages. */
    List<Note> pages(String book) {
        List<Note> notes=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("notes",ROW,HERE+" AND book=?",new String[]{home(book)?Things.HOME:book},null,null,ORDER)) {
            while(c.moveToNext())notes.add(read(c,false));
        }
        return notes;
    }

    /** One order for every level: where the reader put it, and for anything never dragged, newest first. */
    private static final String ORDER="place ASC, updated DESC";
    /** On the shelves means neither put away nor in the bin. Every live list says so the same way. */
    private static final String HERE="deleted=0 AND archived=0";
    /** The same for a collection, whose row says it in the words the tree was made with. */
    private static final String LIVE="binned=0 AND archived=0";
    /** And the order collections are kept in, which is the same order in other words. */
    private static final String ORDINAL="ordinal ASC, updated DESC";
    private static final String[] ROW={"id","title","notebook","book","pinned","deleted","archived","updated","place",
        "colour","revision","theirs","origin","writes","substr(body,1,"+PREVIEW+") AS preview"};

    private static long whole(Cursor c,String column,long fallback) {
        int at=c.getColumnIndex(column);
        return at<0||c.isNull(at)?fallback:c.getLong(at);
    }
    private static String said(Cursor c,String column,String fallback) {
        int at=c.getColumnIndex(column);
        return at<0||c.isNull(at)?fallback:c.getString(at);
    }

    private static Note read(Cursor c,boolean complete) {
        Note n=new Note();
        n.id=c.getString(c.getColumnIndexOrThrow("id"));n.title=c.getString(c.getColumnIndexOrThrow("title"));
        n.notebook=c.getString(c.getColumnIndexOrThrow("notebook"));n.book=c.getString(c.getColumnIndexOrThrow("book"));
        n.pinned=c.getInt(c.getColumnIndexOrThrow("pinned"))==1;
        n.deleted=c.getInt(c.getColumnIndexOrThrow("deleted"))==1;n.updated=c.getLong(c.getColumnIndexOrThrow("updated"));
        n.place=c.getLong(c.getColumnIndexOrThrow("place"));
        n.archived=c.getInt(c.getColumnIndexOrThrow("archived"))==1;
        n.colour=c.getInt(c.getColumnIndexOrThrow("colour"));
        n.rung=Reading.stored(whole(c,"rung",Reading.NONE));
        // Read for what is there rather than for what ought to be: a row fetched with fewer columns — a list
        // row, or one from an older read — must not take the app down, it must simply say less.
        n.revision=whole(c,"revision",0);
        n.theirs=whole(c,"theirs",0)==1;
        n.writes=whole(c,"writes",0)==1;
        n.origin=said(c,"origin","");
        n.complete=complete;
        if(complete){n.body=c.getString(c.getColumnIndexOrThrow("body"));n.preview=n.body.length()>PREVIEW?n.body.substring(0,PREVIEW):n.body;}
        else n.preview=c.getString(c.getColumnIndexOrThrow("preview"));
        return n;
    }

    // ---- looking for something, and seeing the whole of it -----------------------------------------------

    /**
     * Everywhere a word turns up, across the whole pad.
     *
     * <p>Notes first and then the shelves that hold them, because a word is usually something somebody
     * wrote rather than something they named. Only what is on the shelves: the archive and the bin are
     * places you go on purpose, and a search that quietly returned what you had thrown away would be
     * offering you back a decision you had already made.
     */
    List<Branch> looking(String term) {
        List<Branch> found=new ArrayList<>();
        String tidy=Find.tidy(term);
        if(tidy.isEmpty())return found;
        String like=Find.like(tidy);
        Tree tree=tree();
        String sql="SELECT n.id,n.title,n.body,n.colour,n.book FROM notes n"
            +" WHERE n.deleted=0 AND n.archived=0 AND (n.title LIKE ? ESCAPE '\\' OR n.body LIKE ? ESCAPE '\\')"
            +" ORDER BY n.updated DESC LIMIT 200";
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{like,like})) {
            while(c.moveToNext()) {
                List<String> up=tree.aboveIn(c.getString(4));
                if(!tree.live(up))continue;
                Note page=new Note();
                page.id=c.getString(0);page.title=c.getString(1)==null?"":c.getString(1);
                page.body=c.getString(2)==null?"":c.getString(2);page.preview=page.body;
                String where=tree.names(up);
                String said=Find.around(Find.holds(page.title,tidy)?page.title:page.body,tidy);
                found.add(new Branch(Branch.Kind.PAGE,page.id,"",page.heading(),
                    where.trim().isEmpty()?said:where+" \u00b7 "+said,0,0,false,c.getInt(3)));
            }
        }
        // Collections at every depth, each saying where it is.
        String shelves="SELECT id,name,parent,tint FROM things WHERE "+LIVE
            +" AND name LIKE ? ESCAPE '\\' ORDER BY "+ORDINAL+" LIMIT 120";
        try(Cursor c=getReadableDatabase().rawQuery(shelves,new String[]{like})) {
            while(c.moveToNext()) {
                List<String> up=tree.above(c.getString(0));
                if(!tree.live(up))continue;
                found.add(new Branch(Branch.Kind.COLLECTION,c.getString(0),home(c.getString(2))?Sharing.EVERYTHING:c.getString(2),
                    c.getString(1),up.isEmpty()?"collection":"collection in "+tree.names(up),0,0,true,c.getInt(3)));
            }
        }
        dress(found);
        return found;
    }

    /**
     * What was kept to hand, whatever level it sits at.
     *
     * <p>A favourite is a thing you come back to, so the list is in the order they were made favourites
     * rather than the order they were last touched - that is what {@link #lately} is for, and a list that
     * reshuffled itself every time you opened something would not be somewhere to keep anything.
     */
    List<Branch> favourites() {
        List<Branch> kept=new ArrayList<>();
        Tree tree=tree();
        // Collections at every depth, those on Home first and then each level in, as the dock was first filled.
        List<Object[]> shelves=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,tint,parent FROM things WHERE favourite=1 AND "+LIVE
                +" ORDER BY "+ORDINAL,null)) {
            while(c.moveToNext()) {
                List<String> up=tree.above(c.getString(0));
                if(!tree.live(up))continue;
                shelves.add(new Object[]{up.size(),new Branch(Branch.Kind.COLLECTION,c.getString(0),
                    home(c.getString(3))?Sharing.EVERYTHING:c.getString(3),c.getString(1),
                    up.isEmpty()?"collection":"collection in "+tree.names(up),0,0,true,c.getInt(2))});
            }
        }
        shelves.sort((one,other)->Integer.compare((Integer)one[0],(Integer)other[0]));
        for(Object[] one:shelves)kept.add((Branch)one[1]);
        try(Cursor c=getReadableDatabase().query("notes",ROW,"pinned=1 AND "+HERE,null,null,null,ORDER)) {
            while(c.moveToNext()) {
                Note page=read(c,false);
                List<String> up=tree.aboveIn(page.book);
                if(!tree.live(up))continue;
                kept.add(new Branch(Branch.Kind.PAGE,page.id,home(page.book)?Sharing.EVERYTHING:page.book,page.heading(),
                    "note in "+bookName(page.book),0,0,false,page.colour));
            }
        }
        return kept;
    }

    /** What was written in most recently, newest first. Notes: a shelf is not a thing you write on. */
    List<Branch> lately(int most) {return recent(most,null);}
    List<Branch> documents(String kind){RichDocument.empty(kind);return recent(Integer.MAX_VALUE,kind);}
    private List<Branch> recent(int most,String kind) {
        List<Branch> recent=new ArrayList<>();
        Tree tree=tree();
        String sql="SELECT n.id,n.title,substr(n.body,1,"+PREVIEW+"),n.colour,n.book FROM notes n"
            +" WHERE n.deleted=0 AND n.archived=0"+(kind==null?"":" AND n.body LIKE ?")+" ORDER BY n.updated DESC LIMIT "+Math.max(1,most);
        try(Cursor c=getReadableDatabase().rawQuery(sql,kind==null?null:new String[]{RichDocument.PREFIX+kind+"\n%"})) {
            while(c.moveToNext()) {
                Note page=new Note();
                page.title=c.getString(1)==null?"":c.getString(1);
                page.preview=c.getString(2)==null?"":c.getString(2);
                recent.add(new Branch(Branch.Kind.PAGE,c.getString(0),"",page.heading(),
                    tree.names(tree.aboveIn(c.getString(4))),0,0,false,c.getInt(3)));
            }
        }
        dress(recent);
        return recent;
    }

    /**
     * How long this thing waits after the writing stops before it goes, in seconds.
     *
     * <p>Its own setting, or that of the collection it is in, or the one above that, and so on up to Home, where
     * it is the one everything starts with. The same way a colour is inherited, and for the same reason: setting
     * a thing once at the top is what makes a setting worth having.
     *
     * @return seconds to wait, or {@link #WHEN_ASKED} for a thing that goes only when somebody says so
     */
    int pauseFor(Branch.Kind kind,String id) {
        int own=pauseOn(kind,id);
        if(own!=0)return own;
        if(kind==Branch.Kind.PAGE||shelf(kind)) {
            List<String> up=above(id);
            for(int at=up.size()-1;at>=0;at--){int set=pauseOn(Branch.Kind.COLLECTION,up.get(at));if(set!=0)return set;}
        }
        return usually;
    }

    /** What a thing waits when nobody has said otherwise, and what "only when asked" is written as. */
    static final int USUALLY=10, WHEN_ASKED=-1;
    /**
     * "Right away": a second after the writing pauses. Not every keystroke - the page is written down when the typing
     * stops, and only then counted from - so a word does not go in pieces. Kept as a wait like any other, above 0,
     * which is "takes after what holds it", and so never taken for only when asked.
     */
    static final int RIGHT_AWAY=1;
    /** What Right away costs, said once under every list that offers it. */
    static final String RIGHT_AWAY_COSTS="Right away sends after each pause in your writing: more messages, and the other screens change while you write.";
    /**
     * What a thing waits when nothing on it or above it says otherwise: the app's own setting (Settings, Sync),
     * or {@link #USUALLY} until it has one. {@link #WHEN_ASKED} means only when Sync now is pressed.
     */
    volatile int usually=USUALLY;

    /** What is set on this thing itself, where 0 means it takes after whatever holds it. */
    int pauseOn(Branch.Kind kind,String id) {
        try(Cursor c=getReadableDatabase().query(shelf(kind)?"things":"notes",new String[]{"pause"},"id=?",new String[]{id},
                null,null,null,"1")) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    void setPause(Branch.Kind kind,String id,int seconds) {
        ContentValues v=new ContentValues();v.put("pause",seconds);
        getWritableDatabase().update(shelf(kind)?"things":"notes",v,"id=?",new String[]{id});
    }

    /** What the place that gathers favourites is known by, where a collection would have an id. */
    static final String FAVOURITES="favourites";
    /** And the drop box, where the files sent straight to a device are (see {@link Drop}). */
    static final String DROPS="drops";

    /** Everything that is a favourite, by id, whatever kind of thing it is. */
    Set<String> keptIds() {
        Set<String> all=new HashSet<>();
        try(Cursor c=getReadableDatabase().query("things",new String[]{"id"},"favourite=1",null,null,null,null)) {
            while(c.moveToNext())all.add(c.getString(0));
        }
        try(Cursor c=getReadableDatabase().query("notes",new String[]{"id"},"pinned=1",null,null,null,null)) {
            while(c.moveToNext())all.add(c.getString(0));
        }
        return all;
    }

    /**
     * How many favourites there are to show: on the shelves, and inside nothing that has been put away.
     * The same things the place lists when it is opened, so its tile never promises more than is in it.
     */
    int keptCount(){return favourites().size();}

    /** Whether one thing is kept to hand. */
    boolean favourite(Branch.Kind kind,String id) {
        try(Cursor c=getReadableDatabase().query(shelf(kind)?"things":"notes",new String[]{shelf(kind)?"favourite":"pinned"},
                "id=?",new String[]{id},null,null,null,"1")) {
            return c.moveToFirst()&&c.getInt(0)==1;
        }
    }

    /**
     * Kept to hand, or not. A new favourite goes into the dock where it has room, as the first ones went when the
     * notebook was moved to things (see {@link Things#dock}); one that stops being a favourite leaves it.
     */
    void keepToHand(Branch.Kind kind,String id,boolean kept) {
        String table=shelf(kind)?"things":"notes";
        ContentValues v=new ContentValues();v.put(shelf(kind)?"favourite":"pinned",kept?1:0);
        if(!kept)v.put("dock",0);
        else try(Cursor c=getReadableDatabase().query(table,new String[]{"dock"},"id=?",new String[]{id},null,null,null,"1")) {
            if(c.moveToFirst()&&c.getInt(0)==0){int slot=freeSlot();if(slot>0)v.put("dock",slot);}
        }
        getWritableDatabase().update(table,v,"id=?",new String[]{id});
    }

    /** The first place in the dock nothing is in, or 0 where it is full. */
    private int freeSlot() {
        Set<Integer> taken=new HashSet<>();
        for(String table:new String[]{"things","notes"})
            try(Cursor c=getReadableDatabase().rawQuery("SELECT dock FROM "+table+" WHERE dock>0",null)) {
                while(c.moveToNext())taken.add(c.getInt(0));
            }
        for(int slot=1;slot<=Things.DOCK_PHONE;slot++)if(!taken.contains(slot))return slot;
        return 0;
    }

    /**
     * The whole pad at once: every collection however deep, and in each the collections it holds and then its
     * notes, each line as deep as it sits; and then whatever is on Home by itself.
     *
     * <p>Walking in and out of things shows you one room at a time, which is the right way to work and the
     * wrong way to remember where you put something. This is the plan of the building.
     */
    List<Branch> wholeTree() {
        List<Branch> tree=new ArrayList<>();
        Tree shelves=tree();
        Map<String,List<Note>> notes=new HashMap<>();
        try(Cursor c=getReadableDatabase().query("notes",ROW,HERE,null,null,null,ORDER)) {
            while(c.moveToNext()){Note page=read(c,false);notes.computeIfAbsent(home(page.book)?Things.HOME:page.book,any->new ArrayList<>()).add(page);}
        }
        walk(tree,shelves,notes,Things.HOME,0,new HashSet<>());
        for(Note page:notes.getOrDefault(Things.HOME,java.util.Collections.<Note>emptyList()))
            tree.add(new Branch(Branch.Kind.PAGE,page.id,Sharing.EVERYTHING,page.heading(),page.rest(),0,0,false,page.colour,0,
                Sharing.state(new java.util.LinkedHashMap<>(),page.theirs),page.origin));
        Set<String> kept=keptIds();
        if(!kept.isEmpty())for(Branch one:tree)one.kept=kept.contains(one.id);
        dress(tree);
        return tree;
    }

    /** One collection's inside, depth first: each collection it holds, what that holds, and then its own notes. */
    private static void walk(List<Branch> into,Tree tree,Map<String,List<Note>> notes,String parent,int depth,Set<String> seen) {
        for(String id:tree.live(parent)) {
            if(!seen.add(id))continue;
            Row row=tree.rows.get(id);
            List<Note> pages=notes.getOrDefault(id,java.util.Collections.<Note>emptyList());
            into.add(new Branch(Branch.Kind.COLLECTION,id,home(parent)?Sharing.EVERYTHING:parent,row.name,
                Things.holds(tree.live(id).size(),pages.size()),depth,0,true,row.tint,0,
                Sharing.state(new java.util.LinkedHashMap<>(),row.theirs),row.origin));
            walk(into,tree,notes,id,depth+1,seen);
            for(Note page:pages)
                into.add(new Branch(Branch.Kind.PAGE,page.id,id,page.heading(),page.rest(),depth+1,0,false,page.colour,0,
                    Sharing.state(new java.util.LinkedHashMap<>(),page.theirs),page.origin));
        }
    }

    // ---- collections ---------------------------------------------------------------------------------------

    /** The collections on Home. */
    List<Shelf> collections(){return shelvesIn(Things.HOME);}

    /** The collections inside one collection: what were its books, and any collection at all now. */
    List<Shelf> books(String collection){return shelvesIn(home(collection)?Things.HOME:collection);}

    /** What one collection holds of collections on the shelves, each with how many things it holds in turn. */
    private List<Shelf> shelvesIn(String parent) {
        List<Shelf> shelves=new ArrayList<>();
        String sql="SELECT t.id,t.name,(SELECT COUNT(*) FROM things i WHERE i.parent=t.id AND i.binned=0 AND i.archived=0)"
            +"+(SELECT COUNT(*) FROM notes n WHERE n.book=t.id AND n.deleted=0 AND n.archived=0) AS held,"
            +"t.tint,t.theirs,t.origin FROM things t WHERE t.parent=? AND t.binned=0 AND t.archived=0 ORDER BY t.ordinal ASC, t.updated DESC";
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{parent})) {
            while(c.moveToNext())shelves.add(new Shelf(c.getString(0),c.getString(1),c.getInt(2),c.getInt(3),
                c.getInt(4)==1,c.getString(5)));
        }
        return shelves;
    }

    /** A collection on Home. */
    Shelf addCollection(String name){return made(Things.HOME,name);}

    /** A collection inside a collection - or on Home, where that is what is named. */
    Shelf addBook(String collection,String name){return made(collection,name);}

    private Shelf made(String parent,String name) {
        String id=UUID.randomUUID().toString();
        long now=System.currentTimeMillis();
        ContentValues v=new ContentValues();v.put("id",id);v.put("parent",home(parent)?Things.HOME:parent);v.put("kind","collection");
        v.put("name",name);v.put("made",now);v.put("updated",now);v.put("ordinal",-now);
        if(getWritableDatabase().insert("things",null,v)<0)throw new IllegalStateException("Could not add the collection");
        return new Shelf(id,name,0,Tint.NONE);
    }

    /**
     * A collection to write a fresh page in: the first on the shelves that holds notes, or sits inside another
     * collection as a book did. A pad whose last one was deleted still has to have somewhere to write, so one
     * is made rather than the page landing nowhere.
     */
    String someBook() {
        Tree tree=tree();
        Set<String> holding=new HashSet<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT book FROM notes WHERE "+HERE,null)) {
            while(c.moveToNext())holding.add(c.getString(0));
        }
        for(Row row:tree.rows.values()) {
            if(row.away||!tree.live(tree.above(row.id)))continue;
            if(!Things.HOME.equals(row.parent)||holding.contains(row.id))return row.id;
        }
        List<String> top=tree.live(Things.HOME);
        String collection=top.isEmpty()?addCollection("My notes").id:top.get(0);
        return addBook(collection,"Notes").id;
    }

    void renameCollection(String id,String name){rename(id,name);}
    void renameBook(String id,String name){rename(id,name);}

    /** Any collection, renamed: its revision moves on, so one that travels on its own goes again (see {@link Carton}). */
    private void rename(String id,String name) {
        getWritableDatabase().execSQL("UPDATE things SET name=?,updated=?,revision=revision+1 WHERE id=?",
            new Object[]{name,System.currentTimeMillis(),id});
    }

    /**
     * The collection a collection sits in, so browsing can walk back up from a page: empty for one on Home, and
     * the first collection for one that is not here at all, as it always was.
     */
    String collectionOf(String book) {
        try(Cursor c=getReadableDatabase().query("things",new String[]{"parent"},"id=?",new String[]{book},null,null,null,"1")) {
            if(!c.moveToFirst())return SchemaMigrations.FIRST_COLLECTION;
            return home(c.getString(0))?"":c.getString(0);
        }
    }

    /** What a collection is called: any collection, at any depth, and Home by that name. */
    String collectionName(String collection) {
        if(home(collection))return "Home";
        try(Cursor c=getReadableDatabase().query("things",new String[]{"name"},"id=?",new String[]{collection},null,null,null,"1")) {
            return c.moveToFirst()?c.getString(0):"My notes";
        }
    }

    /** The same, where what was asked about was a book. */
    String bookName(String book) {
        if(home(book))return "Home";
        try(Cursor c=getReadableDatabase().query("things",new String[]{"name"},"id=?",new String[]{book},null,null,null,"1")) {
            return c.moveToFirst()?c.getString(0):"Notes";
        }
    }

    // ---- who a level is shared with ----------------------------------------------------------------------

    /**
     * The library as one indented list: All collections, then each collection, then the books of the ones
     * opened, then their pages. Built in a single call so the tree is read and drawn as one piece, and so
     * every line already knows how many addresses it is shared with.
     */
    static final String INBOX="shared-with-me";

    /** A level and what it holds, with how many addresses the level itself is shared with. */
    /** Two audiences as one. Somebody reached both ways is reached both ways. */
    private static Map<String,Boolean> with(Map<String,Boolean> one,Map<String,Boolean> two) {
        if(two==null||two.isEmpty())return one;
        Map<String,Boolean> all=new java.util.LinkedHashMap<>(one);
        for(Map.Entry<String,Boolean> who:two.entrySet())
            all.merge(who.getKey(),who.getValue(),(a,b)->a||b);
        return all;
    }

    static final class Level {
        final List<Branch> holds; final int shared;
        Level(List<Branch> holds,int shared){this.holds=holds;this.shared=shared;}
    }

    /**
     * What sits inside one level: on Home, its collections and then its notes; in a collection at any depth, the
     * collections it holds and then its notes, each in the owner's order.
     */
    Level inside(Branch.Kind level,String id) {
        // A collection level that names Home is Home: what a screen drawn before collections nested can ask for.
        if(shelf(level)&&home(id)){level=Branch.Kind.LIBRARY;id=Sharing.EVERYTHING;}
        List<Sharing.Rule> rules=shares();
        Tree tree=tree();
        // One read of what got through, then the same answer for every line of this level. A page counts
        // once however many addresses are owed it: the mark says a page is behind, not how many deliveries.
        List<Outbox.Page> under=pagesUnder(level,id);
        Map<String,Outbox.Page> byId=new HashMap<>();
        for(Outbox.Page page:under)byId.put(page.id,page);
        Map<String,Set<String>> owedIn=new HashMap<>();
        for(Outbox.Wait wait:Outbox.waiting(rules,under,sent())) {
            Outbox.Page where=byId.get(wait.page);
            if(where==null)continue;
            for(String key:where.path())owedIn.computeIfAbsent(key,any->new HashSet<>()).add(wait.page);
        }
        // Everybody anything underneath reaches, gathered per shelf, however far up.
        //
        // A note shared on its own used to leave the book and the collection holding it saying nothing at
        // all, so the only way to find out that something in a collection was going somewhere was to open
        // every note in it. A shelf says what is true of the things on it.
        Map<String,Map<String,Boolean>> reachedUnder=new HashMap<>();
        Set<String> theirsUnder=new HashSet<>();
        for(Outbox.Page page:under) {
            Map<String,Boolean> mine=Sharing.audience(rules,page.path());
            for(String key:page.above) {
                Map<String,Boolean> all=reachedUnder.computeIfAbsent(key,any->new java.util.LinkedHashMap<>());
                for(Map.Entry<String,Boolean> who:mine.entrySet())
                    all.merge(who.getKey(),who.getValue(),(a,b)->a||b);
            }
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT book FROM notes WHERE theirs=1 AND deleted=0 AND archived=0",null)) {
            while(c.moveToNext())theirsUnder.addAll(tree.aboveIn(c.getString(0)));
        }
        // How much each collection holds, for the line that says so.
        Map<String,Integer> notesIn=new HashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT book,COUNT(*) FROM notes WHERE "+HERE+" GROUP BY book",null)) {
            while(c.moveToNext())notesIn.put(home(c.getString(0))?Things.HOME:c.getString(0),c.getInt(1));
        }
        Sharing.Scope scope=level==Branch.Kind.LIBRARY?Sharing.Scope.LIBRARY:shelf(level)?Sharing.Scope.THING:null;
        List<Branch> lines=new ArrayList<>();
        final Set<String> kept=keptIds();
        if(level==Branch.Kind.LIBRARY||shelf(level)) {
            boolean top=level==Branch.Kind.LIBRARY;
            String here=top?Things.HOME:id;
            // First, and only once something is a favourite: an empty place on somebody's first screen is
            // a thing to wonder about. Counted from what it will actually show, so it never opens on nothing.
            int held=top?keptCount():0;
            if(held>0)lines.add(new Branch(Branch.Kind.FAVOURITES,FAVOURITES,Sharing.EVERYTHING,"Favourites",
                held+(held==1?" favourite":" favourites"),0,0,true));
            // The collections in it, then its notes: a collection's inside is both now (docs/HOME.md, decision 13).
            for(String one:tree.live(here))
                lines.add(shelfLine(tree,one,rules,reachedUnder,owedIn,theirsUnder,notesIn));
            for(Note page:pages(here)) {
                List<String> path=tree.aboveIn(page.book);path.add(page.id);
                Map<String,Boolean> reaches=Sharing.audience(rules,path);
                lines.add(new Branch(Branch.Kind.PAGE,page.id,top?Sharing.EVERYTHING:id,page.heading(),page.rest(),0,
                    reaches.size(),false,page.colour,held(owedIn,page.id),
                    Sharing.state(reaches,page.theirs),page.origin));
            }
            // Then the drop box, after the collections, as the PC has it under All collections: a place of its own, not
            // one of them. Only once files can come or have: before anything is paired it could only ever be empty.
            if(top&&dropBoxShown())lines.add(new Branch(Branch.Kind.DROPS,DROPS,Sharing.EVERYTHING,"Drop box",
                Drop.boxDetail(freshTransfers(),looseHere()),0,0,true));
        } else if(level==Branch.Kind.ARCHIVE||level==Branch.Kind.BIN) {
            lines.addAll(heldIn(level==Branch.Kind.BIN));
        } else if(level==Branch.Kind.FAVOURITES) {
            // Every favourite, as the same line it is where it lives - the same mark, the same colour, the same
            // count - collections first, those on Home and then each level in, and then notes. Walked the way the
            // shelves are, so what is in the archive or the bin, or inside something that is, is not here.
            for(Branch one:favourites()) {
                if(one.kind==Branch.Kind.COLLECTION){lines.add(shelfLine(tree,one.id,rules,reachedUnder,owedIn,theirsUnder,notesIn));continue;}
                Outbox.Page page=byId.get(one.id);
                Note note=page==null?null:get(one.id);
                if(note==null)continue;
                Map<String,Boolean> reaches=Sharing.audience(rules,page.path());
                lines.add(new Branch(Branch.Kind.PAGE,note.id,one.parent,note.heading(),note.rest(),0,
                    reaches.size(),false,note.colour,held(owedIn,note.id),
                    Sharing.state(reaches,note.theirs),note.origin));
            }
        }
        // The archive and the bin are not things on the shelves: they are places to walk into, named in the
        // menu at every level rather than standing among the collections and notes they hold.
        // And which of them this phone has stopped taking in, so the mark on each can say so.
        Set<String> stopped=paused();
        List<String> levelPath=shelf(level)?Things.path(tree.parents,id):new ArrayList<>();
        boolean levelStopped=false;
        for(String one:levelPath)if(stopped.contains(one))levelStopped=true;
        if(!stopped.isEmpty())for(Branch one:lines)
            // Among the favourites a thing is not inside the place it is listed in, so it is asked itself.
            one.paused=level==Branch.Kind.FAVOURITES?pausedHere(one.kind,one.id)
                :stopped.contains(one.id)||levelStopped;
        if(!kept.isEmpty())for(Branch one:lines)one.kept=kept.contains(one.id);
        // And where each stands with the others, gathered per shelf as the owed pages are, for its mark.
        Map<String,List<SyncMark>> standingUnder=new HashMap<>();
        for(Map.Entry<String,Map<String,SyncMark>> page:standing(under).entrySet()) {
            Outbox.Page where=byId.get(page.getKey());
            for(String key:where.path())standingUnder.computeIfAbsent(key,any->new ArrayList<>()).addAll(page.getValue().values());
        }
        // And a collection waiting to go on its own to a device that has to be updated first: amber, on it and on whatever
        // holds it, as a note waiting is - so its box can say who needs to update (see SyncStatus.waits).
        for(String waits:cartonsForUpdate(shelf(level)?level:Branch.Kind.LIBRARY,shelf(level)?id:Sharing.EVERYTHING).keySet())
            for(String key:Things.path(tree.parents,waits))standingUnder.computeIfAbsent(key,any->new ArrayList<>()).add(SyncMark.WAITING);
        for(Branch one:lines){List<SyncMark> all=standingUnder.get(one.id);if(all!=null)one.standing=SyncMark.worst(all);}
        // And the icon and the picture each wears, so no screen asks for them one by one.
        dress(lines);
        return new Level(lines,scope==null?0:shared(rules,scope,id));
    }

    /** One collection's line: what it holds, who it and everything in it reaches, and how much of that is owed. */
    private static Branch shelfLine(Tree tree,String id,List<Sharing.Rule> rules,Map<String,Map<String,Boolean>> reachedUnder,
                                    Map<String,Set<String>> owedIn,Set<String> theirsUnder,Map<String,Integer> notesIn) {
        Row row=tree.rows.get(id);
        Map<String,Boolean> reaches=with(Sharing.audience(rules,Things.path(tree.parents,id)),reachedUnder.get(id));
        return new Branch(Branch.Kind.COLLECTION,id,Things.HOME.equals(row.parent)?Sharing.EVERYTHING:row.parent,row.name,
            Things.holds(tree.live(id).size(),notesIn.getOrDefault(id,0)),0,reaches.size(),true,row.tint,held(owedIn,id),
            Sharing.state(reaches,row.theirs||theirsUnder.contains(id)),row.origin);
    }

    // ---- Home, and what each collection holds, as the grid draws it (docs/HOME.md, step 2) ------------------------

    /** What a collection is called until somebody names it: one just made, or two notes just put together. */
    static final String UNTITLED="Untitled";

    /**
     * What Home or one collection holds, as its grid shows it: its collections and its notes in the owner's order - one
     * order for both, by where each was put, and the newest first where two were put nowhere different - then the files
     * kept with it, the newest first. Each collection and note is the line {@link #inside} makes, with its mark, its
     * colour and where it stands; each file a {@link Branch.Kind#FILE} line, new until it is opened. The Favourites
     * collection is not among them: the grid shows it first on Home whenever {@link #favouritesBeyondDock} holds anything.
     * Every line's parent is what {@link #inside} names: {@link Sharing#EVERYTHING} on Home, else the collection.
     *
     * @param collection a collection's id, or Home: {@link Things#HOME}, {@link Sharing#EVERYTHING} or empty
     */
    List<Branch> contents(String collection) {
        boolean top=home(collection);
        String here=top?Things.HOME:collection;
        Level level=top?inside(Branch.Kind.LIBRARY,Sharing.EVERYTHING):inside(Branch.Kind.COLLECTION,collection);
        // Where each was put, and when it was last written, for the one order both kinds are drawn in.
        // And the cell each was put in, where it has one (see Layout).
        Map<String,long[]> at=new HashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,ordinal,updated,cell,page FROM things WHERE parent=? AND "+LIVE,new String[]{here})) {
            while(c.moveToNext())at.put(c.getString(0),new long[]{c.getLong(1),c.getLong(2),c.getLong(3),c.getLong(4)});
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,place,updated,cell,page FROM notes WHERE book=? AND "+HERE,new String[]{here})) {
            while(c.moveToNext())at.put(c.getString(0),new long[]{c.getLong(1),c.getLong(2),c.getLong(3),c.getLong(4)});
        }
        List<Branch> lines=new ArrayList<>();
        for(Branch one:level.holds)if((one.kind==Branch.Kind.COLLECTION||one.kind==Branch.Kind.PAGE)&&at.containsKey(one.id))lines.add(one);
        // One sort, which keeps the order inside gave where two were put in the same place: collections before notes.
        lines.sort((one,other)->{
            long[] a=at.get(one.id),b=at.get(other.id);
            return a[0]!=b[0]?Long.compare(a[0],b[0]):Long.compare(b[1],a[1]);
        });
        for(Branch one:lines){one.cell=(int)at.get(one.id)[2];one.page=(int)at.get(one.id)[3];}
        String parent=top?Sharing.EVERYTHING:collection;
        Map<String,int[]> fileCells=new HashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,cell,page FROM files WHERE note=?",new String[]{here})) {
            while(c.moveToNext())fileCells.put(c.getString(0),new int[]{c.getInt(1),c.getInt(2)});
        }
        for(Held file:filesOf(Branch.Kind.COLLECTION,here)) {
            Branch line=fileLine(file,parent,null);int[] cell=fileCells.get(file.id);
            line.cell=cell==null?Layout.NONE:cell[0];line.page=cell==null?Layout.NO_PAGE:cell[1];lines.add(line);
        }
        return lines;
    }

    /**
     * Where the icons of one grid stand, written as the owner left them: each line's cell from {@code cells}, one
     * transaction, so a grid is never half moved (see {@link Layout#moveTo}). Kept on this device and never sent: where
     * you put a thing is yours. `updated` is untouched - putting a thing somewhere is not writing in it.
     */
    /**
     * Where Home's icons stand, written as the owner left them: each line's page and its cell on it, one write for the lot,
     * as {@link #place} writes a card's (docs/HOME.md, decision 50). Lines that are not a note, a collection or a file -
     * the places - are kept by each app in its own settings.
     */
    void placeOnPages(List<Branch> lines,Map<String,Layout.Spot> spots) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(Branch one:lines) {
                Layout.Spot at=spots.get(one.id);
                if(at==null)continue;
                String table=one.kind==Branch.Kind.PAGE?"notes":shelf(one.kind)?"things":one.kind==Branch.Kind.FILE?"files":null;
                if(table==null)continue;
                ContentValues v=new ContentValues();v.put("cell",at.cell());v.put("page",at.page());
                db.update(table,v,"id=?",new String[]{one.id});
            }
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    /** Home's icons put back exactly as they were - a cell and a page each, none at all included: what Undo writes. */
    void placeOnPages(List<Branch> lines,Map<String,Integer> cells,Map<String,Integer> pages) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(Branch one:lines) {
                Integer cell=cells.get(one.id),page=pages.get(one.id);
                if(cell==null||page==null)continue;
                String table=one.kind==Branch.Kind.PAGE?"notes":shelf(one.kind)?"things":one.kind==Branch.Kind.FILE?"files":null;
                if(table==null)continue;
                ContentValues v=new ContentValues();v.put("cell",cell);v.put("page",page);
                db.update(table,v,"id=?",new String[]{one.id});
            }
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    void place(List<Branch> lines,Map<String,Integer> cells) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(Branch one:lines) {
                Integer cell=cells.get(one.id);
                if(cell==null)continue;
                String table=one.kind==Branch.Kind.PAGE?"notes":shelf(one.kind)?"things":one.kind==Branch.Kind.FILE?"files":null;
                if(table==null)continue;
                ContentValues v=new ContentValues();v.put("cell",cell);
                db.update(table,v,"id=?",new String[]{one.id});
            }
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    /** One file as a line: its name, and under it where it is, or its type and size; new until it is opened. */
    private static Branch fileLine(Held file,String parent,String detail) {
        Branch line=new Branch(Branch.Kind.FILE,file.id,parent,file.name,
            detail!=null?detail:DropList.type(file.name)+" · "+Attachment.size(file.bytes),0,0,false,Tint.NONE,0,Sharing.State.HERE,file.origin);
        line.fresh=file.fresh;
        return line;
    }

    /** A file has been opened here: it is not new any more. */
    void opened(String file) {
        ContentValues v=new ContentValues();v.put("fresh",0);
        getWritableDatabase().update("files",v,"id=? AND fresh=1",new String[]{file});
    }

    /** How many files on Home are new: come from another device, and not opened here yet. */
    int freshOnHome() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM files WHERE note=? AND "+heldIs(Branch.Kind.COLLECTION)+" AND fresh=1",
                new String[]{Things.HOME})) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    /**
     * A collection made where the owner is: on Home, or inside any collection, called {@link #UNTITLED} until it is named.
     * It arrives first there, as anything made or moved does.
     *
     * @throws IllegalArgumentException where {@code parent} is not Home or a collection
     */
    Shelf addCollectionIn(String parent,String name) {
        if(!home(parent)&&!there("things",parent))throw new IllegalArgumentException("Only a collection can hold a collection.");
        return made(parent,name==null||name.trim().isEmpty()?UNTITLED:name.trim());
    }

    /**
     * One thing moved, as a drag on the grid moves it: a note into any collection or onto Home; a collection onto Home or
     * into any collection that is not itself or inside it, refused as {@link #moveBook} refuses; a file onto Home, into a
     * collection, or into a note, whose attachment it then is. What moves arrives first where it lands.
     *
     * @throws IllegalArgumentException where it cannot go there, said in words the person can be shown
     */
    void moveInto(Branch.Kind kind,String id,String into) {
        if(kind==Branch.Kind.FILE){moveFile(id,into);return;}
        if(shelf(kind)){moveBook(id,into);return;}
        if(kind!=Branch.Kind.PAGE)throw new IllegalArgumentException("Only a note, a collection or a file can be moved.");
        if(!home(into)&&!there("things",into))throw new IllegalArgumentException("Only a collection can hold a note.");
        movePage(id,into);
    }

    /**
     * A file kept somewhere else from now: on Home, with a collection, or with a note - the same bytes under the same id.
     * Where it lands it is this device's own, which no list from another device can take out again, and it is not new
     * any more. A collection it leaves or goes into has changed in what travels with it, and is owed again (see Carton).
     */
    private void moveFile(String file,String into) {
        Held one=file(file);
        if(one==null)throw new IllegalStateException("That file is not here any more.");
        boolean onHome=home(into),collection=!onHome&&there("things",into),note=!onHome&&!collection&&there("notes",into);
        if(!onHome&&!collection&&!note)throw new IllegalArgumentException("A file can go onto Home, into a collection or into a note.");
        if(note&&onlyReads(into))throw new IllegalArgumentException("That note is read only here.");
        long now=System.currentTimeMillis();
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ContentValues v=new ContentValues();
            v.put("note",onHome?Things.HOME:into);v.put("held",heldAs(note?Branch.Kind.PAGE:Branch.Kind.COLLECTION));
            // A new grid, so no cell of its own there yet: it takes the first free one (see Layout).
            v.put("place",-now);v.put("origin","");v.put("fresh",0);v.put("cell",Layout.NONE);
            db.update("files",v,"id=?",new String[]{file});
            if(one.held!=Branch.Kind.PAGE&&!home(one.note))touched(db,one.note);
            if(collection)touched(db,into);
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    /**
     * The order of one grid - Home's or a collection's - written as the owner left it: collections, notes and files among
     * one another, each numbered by where it now stands, in one transaction so a grid is never half reordered. `updated`
     * is untouched: putting a thing somewhere is not writing on it. Collections and notes are drawn in this one order, and
     * files after them in theirs (see {@link #contents}); any other line is passed over.
     */
    void order(List<Branch> lines) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(int at=0;at<lines.size();at++) {
                Branch one=lines.get(at);
                String table=one.kind==Branch.Kind.PAGE?"notes":shelf(one.kind)?"things":one.kind==Branch.Kind.FILE?"files":null;
                if(table==null)continue;
                ContentValues v=new ContentValues();v.put("things".equals(table)?"ordinal":"place",at);
                db.update(table,v,"id=?",new String[]{one.id});
            }
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    /**
     * Two notes put together, as letting one go on another does: a new collection, {@link #UNTITLED}, where the one let go
     * on was - in what held it, and at its place - holding both, that one first. Returned so it can be named at once.
     * One transaction: never a collection with only one of them in it. Who each then reaches is the new collection's
     * business, as with any move; the screen says so before it asks for this (see Sharing.Change).
     *
     * @param dropped the note that was carried
     * @param onto the note it was let go on
     */
    Shelf merge(String dropped,String onto) {
        if(dropped==null||dropped.equals(onto))throw new IllegalArgumentException("A note cannot be put together with itself.");
        Note one=get(dropped),two=get(onto);
        if(one==null||two==null)throw new IllegalStateException("That note is not here any more.");
        String in=home(two.book)?Things.HOME:two.book, id=UUID.randomUUID().toString();
        long now=System.currentTimeMillis();
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ContentValues v=new ContentValues();v.put("id",id);v.put("parent",in);v.put("kind","collection");v.put("name",UNTITLED);
            // In the cell the note let go on stood in, so the two turn into one icon where that one was.
            v.put("made",now);v.put("updated",now);v.put("ordinal",two.place);v.put("cell",cellOf("notes",onto));v.put("page",pageOf("notes",onto));
            if(db.insert("things",null,v)<0)throw new IllegalStateException("Could not put them together.");
            String[] both={onto,dropped};
            for(int at=0;at<both.length;at++) {
                ContentValues moved=new ContentValues();moved.put("book",id);moved.put("place",at);moved.put("updated",now);moved.put("cell",Layout.NONE);
                db.update("notes",moved,"id=?",new String[]{both[at]});
            }
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
        return new Shelf(id,UNTITLED,2,Tint.NONE);
    }

    // ---- the dock: favourites at hand, on this device only (docs/HOME.md, decisions 3 and 19) -----------------------

    /** A thing's key in the dock. A note's row and a collection's are in two tables, and the dock spans both. */
    private static String dockKey(Branch.Kind kind,String id){return (kind==Branch.Kind.PAGE?"n:":"c:")+id;}

    /** Every thing with a place in the dock, by its key: where it is, 1 the first. What is put away keeps its place. */
    private Map<String,Integer> dockSlots() {
        Map<String,Integer> slots=new HashMap<>();
        for(String table:new String[]{"things","notes"})
            try(Cursor c=getReadableDatabase().rawQuery("SELECT id,dock FROM "+table+" WHERE dock>0",null)) {
                while(c.moveToNext())slots.put(("notes".equals(table)?"n:":"c:")+c.getString(0),c.getInt(1));
            }
        return slots;
    }

    /**
     * The dock: the favourites with a place in it, in that order, each the line the Favourites collection shows for it.
     * Kept on this device and never sent. A favourite put away - binned, archived, or inside something that is - keeps
     * its place and is not shown until it comes back.
     */
    List<Branch> dock() {
        Map<String,Integer> slots=dockSlots();
        List<Branch> docked=new ArrayList<>();
        for(Branch one:inside(Branch.Kind.FAVOURITES,FAVOURITES).holds)if(slots.containsKey(dockKey(one.kind,one.id)))docked.add(one);
        docked.sort((one,other)->Integer.compare(slots.get(dockKey(one.kind,one.id)),slots.get(dockKey(other.kind,other.id))));
        return docked;
    }

    /**
     * The favourites with no place in the dock, as the Favourites collection lists them: collections, those on Home first,
     * then notes. What the Favourites collection holds, first on Home whenever it holds anything.
     */
    List<Branch> favouritesBeyondDock() {
        Map<String,Integer> slots=dockSlots();
        List<Branch> beyond=new ArrayList<>();
        for(Branch one:inside(Branch.Kind.FAVOURITES,FAVOURITES).holds)if(!slots.containsKey(dockKey(one.kind,one.id)))beyond.add(one);
        return beyond;
    }

    /** How many favourites there are on the shelves, in the dock or beyond it. */
    int favouriteCount(){return keptCount();}

    /**
     * The dock's keys in the order its places are written in: what it shows, in its order, and then what is put away,
     * so that a place counted on the screen is a place here, and what is put away is the first pushed out.
     *
     * @param shown filled with what it shows, in its order
     */
    private List<String> dockOrder(List<String> shown) {
        for(Branch one:dock())shown.add(dockKey(one.kind,one.id));
        List<String> order=new ArrayList<>(shown);
        Map<String,Integer> slots=dockSlots();
        List<String> away=new ArrayList<>(slots.keySet());away.removeAll(order);
        away.sort((one,other)->slots.get(one).equals(slots.get(other))?one.compareTo(other):Integer.compare(slots.get(one),slots.get(other)));
        order.addAll(away);
        return order;
    }

    /** The dock written as this order, numbered from 1; whatever had a place and is not in it leaves the dock. */
    private static void writeDock(SQLiteDatabase db,List<String> order) {
        db.execSQL("UPDATE things SET dock=0 WHERE dock>0");
        db.execSQL("UPDATE notes SET dock=0 WHERE dock>0");
        for(int at=0;at<order.size();at++) {
            String key=order.get(at);
            db.execSQL("UPDATE "+(key.startsWith("n:")?"notes":"things")+" SET dock=? WHERE id=?",new Object[]{at+1,key.substring(2)});
        }
    }

    /**
     * A thing put in the dock at a place, 1 the first, counted as the dock shows: made a favourite if it was not, taken
     * from wherever it was in the dock, and the rest moved along. Whatever is pushed past the end of the phone's dock
     * leaves it and stays a favourite, in the Favourites collection (see {@link Things#intoDock}). One transaction.
     */
    void toDock(Branch.Kind kind,String id,int slot) {
        String key=dockKey(kind,id);
        List<String> shown=new ArrayList<>(),order=dockOrder(shown);
        shown.remove(key);
        List<String> now=Things.intoDock(order,key,Math.max(1,Math.min(slot,shown.size()+1)),Things.DOCK_PHONE);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ContentValues v=new ContentValues();v.put(shelf(kind)?"favourite":"pinned",1);
            db.update(shelf(kind)?"things":"notes",v,"id=?",new String[]{id});
            writeDock(db,now);
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    /** A thing taken out of the dock: it stays a favourite, in the Favourites collection, and the rest close up. */
    void outOfDock(Branch.Kind kind,String id) {
        List<String> now=Things.outOfDock(dockOrder(new ArrayList<>()),dockKey(kind,id));
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{writeDock(db,now);db.setTransactionSuccessful();}finally{db.endTransaction();}
    }

    // ---- looking for anything, from the foot of Home -------------------------------------------------------------

    /**
     * Everything a word turns up in, for the search bar at the foot of Home: notes by their title and their words,
     * collections at any depth by name, and files by name - those that came and are kept on Home, and those kept with
     * any collection or note. Each line's detail begins with where it is - "on Home", or "in Kitchen › Recipes" - and a
     * note's goes on with the words around what was found. A file's parent is what keeps it, so it opens where it lives.
     * Only what is on the shelves, as {@link #looking} has it.
     */
    List<Branch> lookingEverywhere(String term) {
        List<Branch> found=new ArrayList<>();
        String tidy=Find.tidy(term);
        if(tidy.isEmpty())return found;
        String like=Find.like(tidy);
        Tree tree=tree();
        Set<String> kept=keptIds();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,title,body,colour,book FROM notes WHERE "+HERE
                +" AND (title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\') ORDER BY updated DESC LIMIT 200",new String[]{like,like})) {
            while(c.moveToNext()) {
                List<String> up=tree.aboveIn(c.getString(4));
                if(!tree.live(up))continue;
                Note page=new Note();
                page.id=c.getString(0);page.title=c.getString(1)==null?"":c.getString(1);
                page.body=c.getString(2)==null?"":c.getString(2);page.preview=page.body;
                String said=Find.around(Find.holds(page.title,tidy)?page.title:page.body,tidy);
                Branch line=new Branch(Branch.Kind.PAGE,page.id,home(c.getString(4))?Sharing.EVERYTHING:c.getString(4),page.heading(),
                    whereIs(tree,up)+(said.isEmpty()?"":" · "+said),0,0,false,c.getInt(3));
                line.kept=kept.contains(page.id);found.add(line);
            }
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,parent,tint FROM things WHERE "+LIVE
                +" AND name LIKE ? ESCAPE '\\' ORDER BY "+ORDINAL+" LIMIT 120",new String[]{like})) {
            while(c.moveToNext()) {
                List<String> up=tree.above(c.getString(0));
                if(!tree.live(up))continue;
                Branch line=new Branch(Branch.Kind.COLLECTION,c.getString(0),home(c.getString(2))?Sharing.EVERYTHING:c.getString(2),
                    c.getString(1),whereIs(tree,up),0,0,true,c.getInt(3));
                line.kept=kept.contains(line.id);found.add(line);
            }
        }
        try(Cursor c=getReadableDatabase().query("files",FILE_ROW,"name LIKE ? ESCAPE '\\'",new String[]{like},null,null,"added DESC","120")) {
            while(c.moveToNext()) {
                Held file=held(c);
                String where,parent;
                if(file.held==Branch.Kind.PAGE) {
                    // Kept with a note: where the note is, and the note by its name.
                    Note note=null;
                    try(Cursor n=getReadableDatabase().query("notes",ROW,"id=? AND "+HERE,new String[]{file.note},null,null,null,"1")) {
                        if(n.moveToFirst())note=read(n,false);
                    }
                    if(note==null)continue;
                    List<String> up=tree.aboveIn(note.book);
                    if(!tree.live(up))continue;
                    where="in "+(up.isEmpty()?"":tree.names(up)+" › ")+note.heading();parent=note.id;
                } else if(home(file.note)) {
                    where="on Home";parent=Sharing.EVERYTHING;
                } else {
                    Row row=tree.rows.get(file.note);
                    if(row==null||row.away)continue;
                    List<String> up=tree.above(file.note);
                    if(!tree.live(up))continue;
                    up.add(file.note);
                    where="in "+tree.names(up);parent=file.note;
                }
                found.add(fileLine(file,parent,where));
            }
        }
        dress(found);
        return found;
    }

    /** Where a thing is, said under it: on Home, or in the collections above it from Home down. */
    private static String whereIs(Tree tree,List<String> up){return up.isEmpty()?"on Home":"in "+tree.names(up);}

    /**
     * Everywhere a carried thing could go: every collection at any depth when moving a note, and Home and every
     * collection when moving a collection. A thing and where it would move to are seldom on the same screen, so
     * the destinations become the screen while something is being carried.
     */
    List<Branch> places(boolean forPage){return places(forPage,null);}

    /**
     * The same for one thing: a collection is never offered itself, or anything inside it, since it cannot go inside
     * itself (docs/HOME.md, decision 14).
     */
    List<Branch> places(Branch moved){return places(moved==null||moved.kind==Branch.Kind.PAGE,moved);}

    private List<Branch> places(boolean forPage,Branch moved) {
        List<Sharing.Rule> rules=shares();
        Tree tree=tree();
        Map<String,Integer> notesIn=new HashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT book,COUNT(*) FROM notes WHERE "+HERE+" GROUP BY book",null)) {
            while(c.moveToNext())notesIn.put(home(c.getString(0))?Things.HOME:c.getString(0),c.getInt(1));
        }
        List<Branch> where=new ArrayList<>();
        if(!forPage)where.add(new Branch(Branch.Kind.LIBRARY,Sharing.EVERYTHING,"","Home",
            Things.holds(tree.live(Things.HOME).size(),notesIn.getOrDefault(Things.HOME,0)),0,0,true));
        java.util.Deque<Object[]> next=new java.util.ArrayDeque<>();
        List<String> top=tree.live(Things.HOME);
        for(int at=top.size()-1;at>=0;at--)next.push(new Object[]{top.get(at),0});
        Set<String> seen=new HashSet<>();
        while(!next.isEmpty()) {
            Object[] one=next.pop();String id=(String)one[0];int depth=(Integer)one[1];
            if(!seen.add(id))continue;
            // Not the moved collection, nor anything inside it: nothing below it is offered either.
            if(!forPage&&moved!=null&&id.equals(moved.id))continue;
            Row row=tree.rows.get(id);
            List<String> up=tree.above(id);
            where.add(new Branch(Branch.Kind.COLLECTION,id,up.isEmpty()?Sharing.EVERYTHING:row.parent,row.name,
                up.isEmpty()?"on Home":"in "+tree.names(up),depth,shared(rules,Sharing.Scope.THING,id),true,row.tint));
            List<String> inner=tree.live(id);
            for(int at=inner.size()-1;at>=0;at--)next.push(new Object[]{inner.get(at),depth+1});
        }
        dress(where);
        return where;
    }

    /**
     * Everything written inside one thing, as one piece of text: a page is itself, a collection is the pages
     * inside it however deep, the library is all of them, and the archive and the bin are what is waiting in
     * them. Pages are separated by a blank line and come in the order the reader put them in.
     */
    String gather(Branch.Kind kind,String id) {
        String where;String[] values;
        switch(kind) {
            case PAGE: where="id=?";values=new String[]{id};break;
            case COLLECTION: case BOOK: where=HERE;values=null;break;
            case ARCHIVE: where="archived=1";values=null;break;
            case BIN: where="deleted=1";values=null;break;
            default: where=HERE;values=null;break;
        }
        // Inside a collection: every note under it, but none under anything inside it that was put away.
        Tree tree=shelf(kind)?tree():null;
        StringBuilder all=new StringBuilder();
        try(Cursor c=getReadableDatabase().query("notes",new String[]{"body","title","book"},where,values,null,null,ORDER)) {
            while(c.moveToNext()) {
                if(tree!=null) {
                    List<String> up=tree.aboveIn(c.getString(2));
                    int at=up.indexOf(id);
                    if(at<0||!tree.live(up.subList(at+1,up.size())))continue;
                }
                String body=c.getString(0).trim();
                if(RichDocument.marked(body))body="[Document — open it to export the file]";
                String title=c.isNull(1)?"":c.getString(1).trim();
                if(body.isEmpty()&&title.isEmpty())continue;
                if(all.length()>0)all.append("\n\n");
                // A note goes out under its title, where it has one: it is part of what the note says.
                if(!title.isEmpty())all.append(title).append(body.isEmpty()?"":"\n\n");
                all.append(body);
            }
        }
        return all.toString();
    }

    // ---- what the addresses have not been given ------------------------------------------------------------

    /** Which revision of which page reached which address, as {@link Outbox} keys. */
    Map<String,Long> sent() {
        Map<String,Long> got=new HashMap<>();
        try(Cursor c=getReadableDatabase().query("sent",new String[]{"address","page","revision"},null,null,null,null,null)) {
            while(c.moveToNext())got.put(Outbox.mark(c.getString(0),c.getString(1)),c.getLong(2));
        }
        return got;
    }

    /**
     * An address has a page, at this revision — because it said so, or because the page came from it.
     *
     * <p>Nothing else writes this, which is the whole of why the mark is worth looking at. It used to be
     * written when the network took a message, and the network will take a message for a phone that is
     * asleep, or gone, or has stopped taking this note; see {@link Receipt}. Never backwards: an answer
     * about an older revision that turns up late says nothing about a newer one.
     */
    void reached(String address,String page,long revision){record(address,page,revision,-1);}

    /**
     * That address and this phone both say the same thing about a page, at this revision: it took what
     * this phone sent as it stood, or this phone took what it sent. Delivered as well, necessarily.
     */
    void agreedOn(String address,String page,long revision){record(address,page,revision,revision);}

    private void record(String address,String page,long revision,long agreed) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            long had=-1, both=0;
            try(Cursor c=db.query("sent",new String[]{"revision","agreed"},"address=? AND page=?",
                    new String[]{address,page},null,null,null,"1")) {
                if(c.moveToFirst()){had=c.getLong(0);both=c.getLong(1);}
            }
            if(revision>had||agreed>both) {
                ContentValues v=new ContentValues();
                v.put("address",address);v.put("page",page);v.put("revision",Math.max(revision,had));
                v.put("agreed",Math.max(agreed,both));
                v.put("at",System.currentTimeMillis());
                db.insertWithOnConflict("sent",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            }
            // Whatever was waiting on an answer about this much, or less, has had it.
            db.delete("handed","address=? AND page=? AND revision<=?",
                new String[]{address,page,String.valueOf(Math.max(revision,had))});
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    /**
     * The network took a page for an address. Not the same as the address having it: that is
     * {@link #reached}, and only the address itself can say so.
     */
    void handedOver(String address,String page,long revision) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            int tries=0;
            try(Cursor c=db.query("handed",new String[]{"revision","tries"},"address=? AND page=?",
                    new String[]{address,page},null,null,null,"1")) {
                // Counted per revision: a newer one starts again from the short waits.
                if(c.moveToFirst()&&c.getLong(0)==revision)tries=c.getInt(1);
            }
            ContentValues v=new ContentValues();
            v.put("address",address);v.put("page",page);v.put("revision",revision);
            v.put("at",System.currentTimeMillis());v.put("tries",tries+1);
            db.insertWithOnConflict("handed",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    /** Everything handed over and not yet answered. */
    List<Outbox.Handed> handed() {
        List<Outbox.Handed> all=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("handed",new String[]{"address","page","revision","at","tries"},
                null,null,null,null,"at ASC")) {
            while(c.moveToNext())all.add(new Outbox.Handed(c.getString(0),c.getString(1),c.getLong(2),
                c.getLong(3),c.getInt(4)));
        }
        return all;
    }

    // ---- what this device carries for others: see Courier ----------------------------------------------------

    /** One thing held for somebody: who left it and who it is for by the fingerprints of their keys. */
    static final class Carried {
        final String sender,recipient,page; final int sort; final long revision,kept,tried; final int tries;
        final byte[] bytes;
        Carried(String sender,String recipient,String page,int sort,long revision,byte[] bytes,long kept,long tried,int tries) {
            this.sender=sender;this.recipient=recipient;this.page=page;this.sort=sort;this.revision=revision;
            this.bytes=bytes;this.kept=kept;this.tried=tried;this.tries=tries;
        }
    }

    /**
     * Something left here for somebody else, kept - unless a newer one of the same is already here, or there
     * is no room. A newer revision of the same note from the same device replaces the older.
     *
     * @return whether it is kept
     */
    boolean carry(String sender,String recipient,String page,int sort,long revision,byte[] bytes) {
        long now=System.currentTimeMillis();
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            db.delete("carried","kept<?",new String[]{String.valueOf(now-Courier.KEPT_FOR)});
            long had=-1;int count=0;long size=0;
            try(Cursor c=db.query("carried",new String[]{"revision"},"sender=? AND recipient=? AND page=? AND sort=?",
                    new String[]{sender,recipient,page,String.valueOf(sort)},null,null,null,"1")) {
                if(c.moveToFirst())had=c.getLong(0);
            }
            if(had>revision){db.setTransactionSuccessful();return false;}
            try(Cursor c=db.rawQuery("SELECT COUNT(*),COALESCE(SUM(size),0) FROM carried",null)) {
                if(c.moveToFirst()){count=c.getInt(0);size=c.getLong(1);}
            }
            // Room for a new one; one replacing another takes no more room than it did.
            if(had<0&&(count>=Courier.MOST_KEPT||size+bytes.length>Courier.MOST_BYTES)){db.setTransactionSuccessful();return false;}
            ContentValues v=new ContentValues();
            v.put("sender",sender);v.put("recipient",recipient);v.put("page",page);v.put("sort",sort);
            v.put("revision",revision);v.put("bytes",android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP));v.put("size",bytes.length);
            v.put("kept",now);v.put("tried",0);v.put("tries",0);
            db.insertWithOnConflict("carried",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
            return true;
        } finally {db.endTransaction();}
    }

    /** Everything held for one device, or for everybody when {@code recipient} is null; what is too old is let go first. */
    List<Carried> carried(String recipient) {
        getWritableDatabase().delete("carried","kept<?",new String[]{String.valueOf(System.currentTimeMillis()-Courier.KEPT_FOR)});
        List<Carried> all=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("carried",new String[]{"sender","recipient","page","sort","revision","bytes","kept","tried","tries"},
                recipient==null?null:"recipient=?",recipient==null?null:new String[]{recipient},null,null,"kept ASC")) {
            while(c.moveToNext())all.add(new Carried(c.getString(0),c.getString(1),c.getString(2),c.getInt(3),c.getLong(4),
                android.util.Base64.decode(c.getString(5),android.util.Base64.NO_WRAP),c.getLong(6),c.getLong(7),c.getInt(8)));
        }
        return all;
    }

    /** Brought once more: when, and how many times, so the next try waits longer. */
    void broughtAgain(Carried one) {
        getWritableDatabase().execSQL("UPDATE carried SET tried=?,tries=tries+1 WHERE sender=? AND recipient=? AND page=? AND sort=? AND revision=?",
            new Object[]{System.currentTimeMillis(),one.sender,one.recipient,one.page,one.sort,one.revision});
    }

    /** They have it: whatever was held for them about that note, up to that revision, is let go. */
    int collected(String recipient,String page,int sort,long revision) {
        return getWritableDatabase().delete("carried","recipient=? AND page=? AND sort=? AND revision<=?",
            new String[]{recipient,page,String.valueOf(sort),String.valueOf(revision)});
    }

    // ---- what the PC holds for its owner's phones to collect: see Home -----------------------------------------

    /**
     * Something for one of the owner's devices, kept until it collects it - unless there is no room. The same
     * bytes twice are kept once, and the same note at the same revision from the same device, sealed again with
     * as much inside, replaces what was held: a note sent again while nobody had collected it does not pile up.
     */
    @Override public boolean holdFor(Home.Held one) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            db.delete("held","kept<?",new String[]{String.valueOf(one.kept-Home.KEPT_FOR)});
            boolean already;
            try(Cursor c=db.query("held",new String[]{"id"},"id=?",new String[]{one.id},null,null,null,"1")){already=c.moveToFirst();}
            if(already){db.setTransactionSuccessful();return true;}
            db.delete("held","sender=? AND recipient=? AND page=? AND revision=? AND sealed=?",
                new String[]{one.sender,one.recipient,one.page,String.valueOf(one.revision),String.valueOf(one.sealed)});
            int forThem=0,all=0;long forThemBytes=0,allBytes=0;
            try(Cursor c=db.rawQuery("SELECT COUNT(*),COALESCE(SUM(size),0),COALESCE(SUM(recipient=?),0),COALESCE(SUM(CASE WHEN recipient=? THEN size ELSE 0 END),0) FROM held",
                    new String[]{one.recipient,one.recipient})) {
                if(c.moveToFirst()){all=c.getInt(0);allBytes=c.getLong(1);forThem=c.getInt(2);forThemBytes=c.getLong(3);}
            }
            if(!Home.room(forThem,forThemBytes,all,allBytes,one.bytes.length)){db.setTransactionSuccessful();return false;}
            ContentValues v=new ContentValues();
            v.put("id",one.id);v.put("sender",one.sender);v.put("recipient",one.recipient);v.put("page",one.page);
            v.put("revision",one.revision);v.put("sealed",one.sealed);v.put("bytes",android.util.Base64.encodeToString(one.bytes,android.util.Base64.NO_WRAP));
            v.put("size",one.bytes.length);v.put("kept",one.kept);
            db.insertWithOnConflict("held",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
            return true;
        } finally {db.endTransaction();}
    }

    /** What is held for one device, oldest first, as much as one answer takes; what is too old is let go first. */
    @Override public List<Home.Held> heldFor(String recipient,int most,long bytes) {
        getWritableDatabase().delete("held","kept<?",new String[]{String.valueOf(System.currentTimeMillis()-Home.KEPT_FOR)});
        List<Home.Held> all=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("held",new String[]{"id","sender","recipient","page","revision","sealed","kept","bytes"},
                "recipient=?",new String[]{recipient},null,null,"kept ASC",String.valueOf(Math.max(1,most)))) {
            while(c.moveToNext())all.add(new Home.Held(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getInt(5),
                c.getLong(6),android.util.Base64.decode(c.getString(7),android.util.Base64.NO_WRAP)));
        }
        return Home.within(all,most,bytes);
    }

    /** How many things are held for one device, or for everybody. */
    @Override public int countHeldFor(String recipient) {
        try(Cursor c=recipient==null?getReadableDatabase().rawQuery("SELECT COUNT(*) FROM held",null)
                :getReadableDatabase().rawQuery("SELECT COUNT(*) FROM held WHERE recipient=?",new String[]{recipient})) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    /** Collected: let go, and only what was held for the device that says it has it. */
    @Override public int letGoFor(String recipient,java.util.Collection<String> ids) {
        int gone=0;
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(String id:ids)gone+=db.delete("held","recipient=? AND id=?",new String[]{recipient,id});
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
        return gone;
    }

    /** The revision every note on the shelves is at now, by id. */
    Map<String,Long> revisions() {
        Map<String,Long> now=new HashMap<>();
        for(Outbox.Page page:pagesUnder(Branch.Kind.LIBRARY,Sharing.EVERYTHING))now.put(page.id,page.revision);
        return now;
    }

    /**
     * An answer arrived: that address says it has that page at that revision.
     *
     * <p>Believed only as far as it could be true. A revision this phone has never reached is not
     * something anybody can have been sent, whoever signs for it.
     */
    boolean acknowledged(String address,String page,long revision,boolean took) {
        Note here=get(page);
        if(here==null||revision>here.revision)return false;
        if(took)agreedOn(address,page,revision);else reached(address,page,revision);
        return true;
    }

    /** Every page inside one thing, as the outbox needs to see it: where it sits, and how new it is. */
    List<Outbox.Page> pagesUnder(Branch.Kind kind,String id) {
        boolean one=kind==Branch.Kind.PAGE, under=shelf(kind)&&!home(id);
        Tree tree=tree();
        List<Outbox.Page> pages=new ArrayList<>();
        // Only what is on the shelves is owed: a page inside an archived or binned collection, at any depth, has
        // gone away with it, and nothing that is put away should be pushed at anybody.
        // The revision, which is what a delivery is recorded as. This read `n.updated` for as long as notes
        // have been sent: a clock, in milliseconds, set beside a count of a few dozen. No count is ever as
        // big as a clock, so nothing was ever up to date — every shared note was owed for ever, the line
        // under the bar said "Not sent yet" whatever had happened, and the whole pad was sent again to
        // everybody each time it was opened. That last part hid a good deal: notes the network had lost
        // turned up anyway, a day later, and looked like they had only been slow.
        String sql="SELECT n.id,n.book,COALESCE(n.revision,0) FROM notes n WHERE n.deleted=0 AND n.archived=0"+(one?" AND n.id=?":"");
        try(Cursor c=getReadableDatabase().rawQuery(sql,one?new String[]{id}:null)) {
            while(c.moveToNext()) {
                List<String> up=tree.aboveIn(c.getString(1));
                if(!tree.live(up)||under&&!up.contains(id))continue;
                pages.add(new Outbox.Page(c.getString(0),up,c.getLong(2)));
            }
        }
        return pages;
    }

    // ---- collections that travel on their own: see Carton -------------------------------------------------

    /**
     * The collections owed to somebody on their own, as waits: each on the shelves, reached by a rule on it or above it,
     * and at a revision that address has not had. One with no note on the shelves anywhere inside it goes whenever that
     * is so, since nothing else carries it. One that holds notes - whose paths carry its name, colour and icon - goes once
     * it has anything of its own to say (decision 31): it has changed since it was made, in its name, colour, icon,
     * picture or files, or it keeps files or wears a picture. Not one this device may only read: whoever has it refuses
     * what a reader sends.
     */
    List<Outbox.Wait> cartonsOwed(Branch.Kind kind,String id){return cartonsOwed(kind,id,false);}

    /** @param every to everybody each reaches, whatever they are thought to have: what Sync means */
    List<Outbox.Wait> cartonsOwed(Branch.Kind kind,String id,boolean every) {
        List<Outbox.Wait> owed=new ArrayList<>();
        if(kind==Branch.Kind.PAGE)return owed;
        List<Sharing.Rule> rules=shares();
        if(rules.isEmpty())return owed;
        Tree tree=tree();
        Set<String> holding=new HashSet<>();
        for(Outbox.Page page:pagesUnder(Branch.Kind.LIBRARY,Sharing.EVERYTHING))holding.addAll(page.above);
        // What only a carton carries: files kept with it, and a picture.
        Set<String> ownLook=new HashSet<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT note FROM files WHERE "+heldIs(Branch.Kind.COLLECTION)
                +" UNION SELECT id FROM things WHERE image<>''",null)) {
            while(c.moveToNext())ownLook.add(c.getString(0));
        }
        Map<String,Long> sent=sent();
        List<String> within=shelf(kind)&&!home(id)?tree.within(id):new ArrayList<>(tree.rows.keySet());
        for(String one:within) {
            Row row=tree.rows.get(one);
            if(row==null||row.away)continue;
            long revision=revisionOf(one);
            if(holding.contains(one)&&revision<=0&&!ownLook.contains(one))continue;
            List<String> path=tree.above(one);
            if(!tree.live(path))continue;
            path.add(one);
            Map<String,Boolean> audience=Sharing.audience(rules,path);
            if(audience.isEmpty())continue;
            if(row.theirs&&myLevel(Sharing.Scope.THING,one)==Sharing.Level.READ)continue;
            for(Map.Entry<String,Boolean> reached:audience.entrySet()) {
                Long got=sent.get(Outbox.mark(reached.getKey(),one));
                if(!every&&got!=null&&got>=revision)continue;
                owed.add(new Outbox.Wait(one,reached.getKey(),revision,Boolean.TRUE.equals(reached.getValue())));
            }
        }
        return owed;
    }

    /** Where a collection's revision is: how many times it has changed in something that travels with it. */
    long revisionOf(String collection) {
        try(Cursor c=getReadableDatabase().query("things",new String[]{"revision"},"id=?",new String[]{collection},null,null,null,"1")) {
            return c.moveToFirst()?c.getLong(0):0;
        }
    }

    /** Every collection's id here, whatever state it is in: what an envelope naming one is looked for among. */
    Set<String> collectionIds(){return new HashSet<>(tree().parents.keySet());}

    /**
     * A collection on its own, as it goes to one device (see {@link Carton}): what it is, where it is, its icon and its
     * picture, what that device may do in it, who else has it - the list of whatever it is shared by, as a note's is -
     * and the files kept with it, each saying where it is once it has gone up, as a note's list does (see Enclosure).
     *
     * @param trees whether that device knows about trees: to one that does not, a level is said as it would be at
     *              three levels, though nothing sends one of these to such a device
     */
    Carton.Sent carton(String collection,String address,boolean trees) {
        List<String> up=above(collection);
        List<String> path=new ArrayList<>(up);path.add(collection);
        Sharing.Rule held=sharedAt(path);
        Sharing.Scope said=held==null?null:trees?held.scope:oldScopeOf(held.scope,held.target);
        String name="",icon="",image="";int tint=Tint.NONE;long ordinal=0;
        try(Cursor c=getReadableDatabase().query("things",new String[]{"name","icon","tint","ordinal","image"},"id=?",new String[]{collection},null,null,null,"1")) {
            if(c.moveToFirst()){name=c.getString(0);icon=c.getString(1);tint=c.getInt(2);ordinal=c.getLong(3);image=c.getString(4);}
        }
        return new Carton.Sent(collection,name,icon,picture(image),tint,ordinal,steps(up),mayWrite(address,path),
            said==null?"":said.name(),said==null?"":held.target,said==null?new ArrayList<>():travelling(held.scope,held.target),
            true,enclosed(collection),System.currentTimeMillis());
    }

    /**
     * A collection that arrived on its own (see {@link Carton}) from a device paired here, taken in. Its list is folded
     * in first, as a note's is, so that the first one ever to arrive from somebody is reached by the line saying they
     * have it. Then, only where they may write in it or it is theirs, it and the collections above it are made where
     * they are not - as a note's path is - and its look followed; nothing already here is moved. Its look is its name,
     * its icon and its picture: followed where the collection is somebody else's here, as its name always was, and - where
     * it is the owner's own - from another of the owner's devices, which is the owner too. Not from a carton older than
     * one already taken from the same device, which would put back what has changed since. What this device has of it is
     * then what they have, so it is not owed straight back to them. Its files are the caller's to take in, by
     * {@link #listed}, as a note's are.
     *
     * @return its id here, or null where nothing was written: they may only read it, or were never given it
     */
    String cartonArrived(String from,long revision,Carton.Sent carton) {
        String here=shelfId(from,carton.id);
        if(here.isEmpty())return null;
        tookMembership(from,here,carton);
        Sharing.Rule may=standingOf(from,here,carton.path);
        String whose=whoseHere(Branch.Kind.COLLECTION,here);
        boolean owner=whose==null||!whose.isEmpty()&&isOwner(from,whose);
        if(!owner&&(may==null||!may.level.writes()))return null;
        // Behind what this device and they last both had: made where it is not, and nothing of its look taken.
        boolean fresh=whose==null||revision>=lastSeen(here,from);
        String made=collectionFrom(from,carton.id,carton.name,fresh?carton.icon:null,carton.tint,carton.ordinal,pathFrom(from,carton.path));
        if(made.isEmpty())return null;
        if(fresh) {
            // Somebody else's, whose name and icon collectionFrom has followed: its picture as well. Or the owner's own, from
            // another of the owner's devices: all three.
            boolean theirs;
            try(Cursor c=getReadableDatabase().query("things",new String[]{"theirs"},"id=?",new String[]{made},null,null,null,"1")) {
                theirs=c.moveToFirst()&&c.getInt(0)==1;
            }
            boolean mine=!theirs&&isOwner(from,"");
            if(theirs||mine) {
                ContentValues look=new ContentValues();
                look.put("image",Thumb.takes(carton.image)?kept(carton.image):"");
                if(mine) {
                    if(!carton.name.trim().isEmpty())look.put("name",carton.name.trim());
                    look.put("icon",iconFrom(carton.icon));
                }
                getWritableDatabase().update("things",look,"id=?",new String[]{made});
            }
        }
        getWritableDatabase().execSQL("UPDATE things SET revision=MAX(revision,?) WHERE id=?",new Object[]{revision,made});
        agreedOn(from,made,revision);
        return made;
    }

    /**
     * An answer about a collection that went on its own: the envelope names it by the sixteen bytes {@link Things} gives
     * it. Believed only as far as it could be true, as a note's is.
     *
     * @return whether it named a collection here, at a revision it has been at
     */
    boolean collectionAcknowledged(String address,byte[] envelope,long revision,boolean took) {
        String id=Things.named(envelope,collectionIds());
        if(id==null||revision>revisionOf(id))return false;
        if(took)agreedOn(address,id,revision);else reached(address,id,revision);
        return true;
    }

    /** What one thing owes, ready to be counted or shown by name. */
    List<Outbox.Wait> owed(Branch.Kind kind,String id) {
        List<Outbox.Wait> owed=new ArrayList<>();
        Map<String,Boolean> reads=new HashMap<>();
        // Not a copy this phone may only read. Every other holder refuses what a reader sends (Post.arrived
        // weighs the sender's standing), so counting it as owed only kept its mark waiting and the retries
        // going every quarter of an hour, for ever, for something nobody would ever take.
        for(Outbox.Wait wait:Outbox.waiting(shares(),pagesUnder(kind,id),sent()))
            if(!reads.computeIfAbsent(wait.page,this::onlyReads))owed.add(wait);
        return owed;
    }

    /** A note that came from somebody and that this phone was given to read, not to write in. */
    boolean onlyReads(String id) {
        Note note=get(id);
        return note!=null&&note.theirs&&myLevel(Sharing.Scope.PAGE,id)==Sharing.Level.READ;
    }

    private static int held(Map<String,Set<String>> owedIn,String id) {
        Set<String> pages=owedIn.get(id);
        return pages==null?0:pages.size();
    }

    /**
     * The colour one collection or page was given. Which colour, never a pixel value. A collection's revision moves
     * on with it, since its colour travels with it where it goes on its own (see {@link Carton}).
     */
    void paint(Branch.Kind kind,String id,int colour) {
        if(kind==Branch.Kind.PAGE){ContentValues v=new ContentValues();v.put("colour",colour);getWritableDatabase().update("notes",v,"id=?",new String[]{id});return;}
        getWritableDatabase().execSQL("UPDATE things SET tint=?,revision=revision+1 WHERE id=?",new Object[]{colour,id});
    }

    /**
     * The rung one note is read at on this device, or {@link Reading#NONE} to read it at the device's own again.
     * Like its colour it is this device's business: nothing is owed to anybody because of it.
     */
    void size(String id,int rung) {
        ContentValues v=new ContentValues();v.put("rung",Reading.stored(rung));
        getWritableDatabase().update("notes",v,"id=?",new String[]{id});
    }

    /** The rung one note has of its own now, for the menu that is about to offer to change it. */
    int rungOf(String id) {
        try(Cursor c=getReadableDatabase().query("notes",new String[]{"rung"},"id=?",new String[]{id},null,null,null,"1")) {
            return c.moveToFirst()?Reading.stored(c.getLong(0)):Reading.NONE;
        }
    }

    /** Every note that has a rung of its own, by id: the rest are read at the device's. */
    Map<String,Integer> rungs() {
        Map<String,Integer> own=new HashMap<>();
        try(Cursor c=getReadableDatabase().query("notes",new String[]{"id","rung"},"rung>=0",null,null,null,null)) {
            while(c.moveToNext()){int rung=Reading.stored(c.getLong(1));if(Reading.known(rung))own.put(c.getString(0),rung);}
        }
        return own;
    }

    /** The colour a thing has now, for the menu that is about to offer to change it. */
    int colourOf(Branch.Kind kind,String id) {
        boolean note=kind==Branch.Kind.PAGE;
        try(Cursor c=getReadableDatabase().query(table(kind),new String[]{note?"colour":"tint"},"id=?",new String[]{id},null,null,null,"1")) {
            return c.moveToFirst()?c.getInt(0):Tint.NONE;
        }
    }

    // ---- the icon and the picture a thing wears (docs/HOME.md, step 4) -------------------------------------------

    /*
     * Both are kept in the thing's own row - a note's in notes, a collection's in things - so they go wherever the row
     * goes, backups included (decision 30): the icon by its name in Lucide's set, the picture as Base64 text of its bytes,
     * empty for none. They travel with the thing: a note's in the path its parcel carries, so it goes the way a change
     * the note owes goes - its revision moves on, with the same words; a collection's in its carton, whose revision moves
     * on too, and its icon in every path through it.
     */

    /** Whether a kind of line is a thing that wears a look of its own: a note, or a collection. */
    private static boolean wears(Branch.Kind kind){return kind==Branch.Kind.PAGE||shelf(kind);}

    /** A picture as it is kept, or null for none - or for text that is not a picture that may be kept. */
    private static byte[] picture(String kept) {
        if(kept==null||kept.isEmpty())return null;
        try {
            byte[] bytes=android.util.Base64.decode(kept,android.util.Base64.NO_WRAP);
            return Thumb.takes(bytes)?bytes:null;
        } catch(IllegalArgumentException damaged){return null;}
    }

    /** A picture as it is written into a row: its bytes as Base64 text, or empty for none. */
    private static String kept(byte[] picture){return picture==null||picture.length==0?"":android.util.Base64.encodeToString(picture,android.util.Base64.NO_WRAP);}

    /** An icon's name as it may be kept from somewhere else: bounded as a parcel bounds it, empty for none. */
    private static String iconFrom(String said) {
        String name=said==null?"":said.trim();
        return name.length()>Parcel.ICON_MOST?"":name;
    }

    /** The icon a note or a collection wears, by name; empty for its default, and for anything else. */
    String iconOf(Branch.Kind kind,String id) {
        if(!wears(kind)||id==null)return "";
        try(Cursor c=getReadableDatabase().query(table(kind),new String[]{"icon"},"id=?",new String[]{id},null,null,null,"1")) {
            return c.moveToFirst()&&!c.isNull(0)?c.getString(0):"";
        }
    }

    /** The picture a note or a collection wears, as its bytes; null for none. */
    byte[] imageOf(Branch.Kind kind,String id) {
        if(!wears(kind)||id==null)return null;
        try(Cursor c=getReadableDatabase().query(table(kind),new String[]{"image"},"id=?",new String[]{id},null,null,null,"1")) {
            return c.moveToFirst()?picture(c.getString(0)):null;
        }
    }

    /**
     * A note or a collection given an icon from the set, or its default again (null or empty). Choosing an icon, or the
     * default, takes its picture off as well: a thing wears one look at a time, and the one just chosen is the one to
     * see. One change, however much of it changed: a collection's revision moves on, so its carton goes to whoever has
     * it; a note's does, with the same words, so the note goes to whoever has it the way anything it owes does.
     *
     * @throws IllegalArgumentException for a name this build's set does not have, for anything but a note or a
     *         collection, and for a thing this device may only read
     */
    void setIcon(Branch.Kind kind,String id,String icon) {
        String name=icon==null?"":icon.trim();
        if(!name.isEmpty()&&!Icons.known(name))throw new IllegalArgumentException("There is no icon called that.");
        look(kind,id,name,"");
    }

    /**
     * A note or a collection given a picture, or its picture taken off (null, or nothing): the icon it had stays
     * underneath, and is worn again when the picture goes. Its revision moves on as for {@link #setIcon}.
     *
     * @param thumb a square thumbnail, as {@link Thumb} makes one: refused unless {@link Thumb#takes} it
     * @throws IllegalArgumentException for bytes too big to travel or that are not a picture, and as for setIcon
     */
    void setImage(Branch.Kind kind,String id,byte[] thumb) {
        if(thumb!=null&&thumb.length>0&&!Thumb.takes(thumb))
            throw new IllegalArgumentException(Thumb.fits(thumb)?"That is not a picture Mininotes can show.":"That picture is too big to keep with it.");
        look(kind,id,null,kept(thumb));
    }

    /** The look written, where it changes: null leaves that half as it is. */
    private void look(Branch.Kind kind,String id,String icon,String image) {
        if(!wears(kind))throw new IllegalArgumentException("Only a note or a collection wears an icon or a picture.");
        if(id==null||home(id))throw new IllegalArgumentException("Home has no icon of its own.");
        boolean note=kind==Branch.Kind.PAGE;
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            String wasIcon,wasImage;boolean theirs;
            try(Cursor c=db.query(table(kind),new String[]{"icon","image","theirs"},"id=?",new String[]{id},null,null,null,"1")) {
                if(!c.moveToFirst())throw new IllegalStateException(note?"That note is not here any more.":"That collection is not here any more.");
                wasIcon=c.isNull(0)?"":c.getString(0);wasImage=c.isNull(1)?"":c.getString(1);theirs=c.getInt(2)==1;
            }
            String nowIcon=icon==null?wasIcon:icon, nowImage=image==null?wasImage:image;
            if(nowIcon.equals(wasIcon)&&nowImage.equals(wasImage)){db.setTransactionSuccessful();return;}
            // What a reader changes would never be taken anywhere: the owner's next word would put it back.
            if(theirs&&(note?onlyReads(id):myLevel(Sharing.Scope.THING,id)==Sharing.Level.READ))
                throw new IllegalArgumentException(note?"That note is read only here.":"That collection is read only here.");
            db.execSQL("UPDATE "+table(kind)+" SET icon=?,image=?,revision=revision+1 WHERE id=?",new Object[]{nowIcon,nowImage,id});
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    /**
     * Each note and collection among these lines given the icon and the picture it wears, so a screen draws them without
     * asking for each: a read or two for the lot, of those lines' rows that wear anything - never every picture in the
     * notebook for a screen that shows five. Every other line is left as it is.
     */
    void dress(List<Branch> lines) {
        if(lines==null||lines.isEmpty())return;
        Set<String> notes=new HashSet<>(),things=new HashSet<>();
        for(Branch one:lines)if(one.kind==Branch.Kind.PAGE)notes.add(one.id);else if(shelf(one.kind))things.add(one.id);
        Map<String,String[]> worn=looks("notes",notes);worn.putAll(looks("things",things));
        if(worn.isEmpty())return;
        for(Branch one:lines) {
            String[] look=one.kind==Branch.Kind.PAGE||shelf(one.kind)?worn.get((one.kind==Branch.Kind.PAGE?"n:":"c:")+one.id):null;
            if(look==null)continue;
            one.icon=look[0];one.image=picture(look[1]);
        }
    }

    /** The look of those rows of one table that wear anything, keyed as the dock keys them: {icon, picture as kept}. */
    private Map<String,String[]> looks(String table,Set<String> ids) {
        Map<String,String[]> out=new HashMap<>();
        List<String> all=new ArrayList<>(ids);
        // A few hundred at a time: SQLite takes only so many values in one question.
        for(int from=0;from<all.size();from+=400) {
            List<String> some=all.subList(from,Math.min(all.size(),from+400));
            String marks=String.join(",",java.util.Collections.nCopies(some.size(),"?"));
            try(Cursor c=getReadableDatabase().rawQuery("SELECT id,icon,image FROM "+table+" WHERE id IN ("+marks+") AND (icon<>'' OR image<>'')",
                    some.toArray(new String[0]))) {
                while(c.moveToNext())out.put(("notes".equals(table)?"n:":"c:")+c.getString(0),
                    new String[]{c.isNull(1)?"":c.getString(1),c.isNull(2)?"":c.getString(2)});
            }
        }
        return out;
    }

    /**
     * The look a note arrived with, taken - or not. Its icon and picture ride in its path (see {@link Parcel.Sent#icon})
     * and are said whole every time, so they are taken from whatever arrived that is not behind what is here: a revision
     * past this device's, or - two devices having each changed the note to the same count - whichever look comes later in
     * one fixed order, which both devices then agree on. Not from what is behind: a copy that took the long way round would
     * put back a look somebody has since changed. The words are weighed on their own (see {@link Arriving}), so a look
     * changed on a device that was behind with the words is still taken where its count is ahead.
     *
     * @param before the note as it was before this arrived, or null where it was not here
     * @param copy   whether this device only reads it: then what the owner sends is the look, where the words were taken
     */
    private void lookArrived(String id,Note before,long revision,Parcel.Sent parcel,boolean copy,Arriving.Decision said) {
        if(parcel==null||parcel.path==null||get(id)==null)return;
        String icon=iconFrom(parcel.icon), image=Thumb.takes(parcel.image)?kept(parcel.image):"";
        String wasIcon=before==null?"":iconOf(Branch.Kind.PAGE,id), wasImage=before==null?"":keptImage("notes",id);
        boolean take;
        if(before==null||copy)take=said.what!=Arriving.What.OLDER;
        else if(revision!=before.revision)take=revision>before.revision;
        else take=(icon+"\u0000"+image).compareTo(wasIcon+"\u0000"+wasImage)>0;
        if(!take||icon.equals(wasIcon)&&image.equals(wasImage))return;
        ContentValues v=new ContentValues();v.put("icon",icon);v.put("image",image);
        getWritableDatabase().update("notes",v,"id=?",new String[]{id});
    }

    /** A row's picture as it is kept, as text: empty for none. */
    private String keptImage(String table,String id) {
        try(Cursor c=getReadableDatabase().query(table,new String[]{"image"},"id=?",new String[]{id},null,null,null,"1")) {
            return c.moveToFirst()&&!c.isNull(0)?c.getString(0):"";
        }
    }

    // ---- the archive and the bin ---------------------------------------------------------------------------

    /** The two places a thing can be put instead of being on the shelves. */
    static final String ARCHIVE="archive", BIN="bin";

    /** Where a thing's row is: a note's in its own table, every collection's among the things. */
    private static String table(Branch.Kind kind){return kind==Branch.Kind.PAGE?"notes":"things";}

    /** Puts one thing away, or takes it out again. What it holds goes with it, still inside it. */
    void putAway(Branch.Kind kind,String id,boolean bin,boolean away) {
        ContentValues v=new ContentValues();v.put(bin?(kind==Branch.Kind.PAGE?"deleted":"binned"):"archived",away?1:0);
        getWritableDatabase().update(table(kind),v,"id=?",new String[]{id});
    }

    /**
     * Back on the shelves. Whatever holds it comes back too, however far up, because a page put back into a
     * collection that is itself in the bin would be back nowhere.
     */
    void restore(Branch.Kind kind,String id) {
        List<String> up=above(id);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ContentValues v=new ContentValues();v.put(kind==Branch.Kind.PAGE?"deleted":"binned",0);v.put("archived",0);
            db.update(table(kind),v,"id=?",new String[]{id});
            ContentValues shelf=new ContentValues();shelf.put("binned",0);shelf.put("archived",0);
            for(String one:up)db.update("things",shelf,"id=?",new String[]{one});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private String parentOf(String table,String column,String id) {
        try(Cursor c=getReadableDatabase().query(table,new String[]{column},"id=?",new String[]{id},null,null,null,"1")) {
            return c.moveToFirst()?c.getString(0):null;
        }
    }

    /** Gone for good: the thing, everything inside it, and every sharing rule that pointed at any of it. */
    void erase(Branch.Kind kind,String id) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{burn(db,kind,id);db.setTransactionSuccessful();}finally{db.endTransaction();}
        sweep();
    }

    private void burn(SQLiteDatabase db,Branch.Kind kind,String id){burn(db,kind,id,new HashSet<>());}

    private void burn(SQLiteDatabase db,Branch.Kind kind,String id,Set<String> gone) {
        if(!gone.add(id))return;
        boolean note=kind==Branch.Kind.PAGE;
        // Everything inside a collection, however deep: the collections it holds, then its notes.
        if(!note) {
            for(String inner:childrenOf("things","parent",id))burn(db,Branch.Kind.COLLECTION,inner,gone);
            for(String page:childrenOf("notes","book",id))burn(db,Branch.Kind.PAGE,page,gone);
        }
        // What a thing holds goes with it: the rows first, and the bytes when the writing has been committed.
        db.delete("files","note=?",new String[]{id});
        // And whatever was still waiting for somebody to say they had it: there is nothing left to have.
        db.delete("handed","page=?",new String[]{id});
        db.delete(table(kind),"id=?",new String[]{id});
        String scope=scopeIs(note?Sharing.Scope.PAGE:Sharing.Scope.THING);
        db.delete("shares",scope+" AND target=?",new String[]{id});
        db.delete("standing",scope+" AND target=?",new String[]{id});
    }

    private List<String> childrenOf(String table,String column,String id) {
        List<String> held=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query(table,new String[]{"id"},column+"=?",new String[]{id},null,null,null)) {
            while(c.moveToNext())held.add(c.getString(0));
        }
        return held;
    }

    /** Everything in the bin, gone for good, and how many things that was. */
    int emptyBin() {
        List<Branch> waiting=heldIn(true);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{for(Branch thing:waiting)burn(db,thing.kind,thing.id);db.setTransactionSuccessful();}
        finally{db.endTransaction();}
        sweep();
        return waiting.size();
    }

    /**
     * What is in the archive, or what is in the bin: collections first, those on Home and then each level in, then
     * pages, each saying what it is and where it came from. Only the thing itself is listed — what it holds travels
     * inside it.
     */
    List<Branch> heldIn(boolean bin) {
        List<Branch> away=new ArrayList<>();
        Tree tree=tree();
        List<Object[]> shelves=new ArrayList<>();
        String collections="SELECT t.id,t.name,t.parent,(SELECT COUNT(*) FROM things i WHERE i.parent=t.id),"
            +"(SELECT COUNT(*) FROM notes n WHERE n.book=t.id),t.tint FROM things t WHERE t."+(bin?"binned":"archived")+"=1"
            +" ORDER BY t.ordinal ASC, t.updated DESC";
        try(Cursor c=getReadableDatabase().rawQuery(collections,null)) {
            while(c.moveToNext()) {
                List<String> up=tree.above(c.getString(0));
                shelves.add(new Object[]{up.size(),new Branch(Branch.Kind.COLLECTION,c.getString(0),up.isEmpty()?"":c.getString(2),
                    c.getString(1),(up.isEmpty()?"collection":"collection in "+tree.names(up))+" · "+Things.holds(c.getInt(3),c.getInt(4)),
                    0,0,false,c.getInt(5))});
            }
        }
        shelves.sort((one,other)->Integer.compare((Integer)one[0],(Integer)other[0]));
        for(Object[] one:shelves)away.add((Branch)one[1]);
        try(Cursor c=getReadableDatabase().query("notes",ROW,(bin?"deleted":"archived")+"=1",null,null,null,ORDER)) {
            while(c.moveToNext()) {
                Note page=read(c,false);
                away.add(new Branch(Branch.Kind.PAGE,page.id,page.book,page.heading(),
                    "note in "+bookName(page.book),0,0,false,page.colour));
            }
        }
        return away;
    }

    /** How many things are waiting in the archive, or in the bin. */
    int awayCount(boolean bin) {
        int count=0;
        for(String ask:new String[]{"SELECT COUNT(*) FROM things WHERE "+(bin?"binned":"archived")+"=1",
                "SELECT COUNT(*) FROM notes WHERE "+(bin?"deleted":"archived")+"=1"})
            try(Cursor c=getReadableDatabase().rawQuery(ask,null)) {
                if(c.moveToFirst())count+=c.getInt(0);
            }
        return count;
    }

    // ---- moving things between shelves --------------------------------------------------------------------

    // Something you just moved arrives at the top of where it landed, which is where you are looking for it.
    /** A note into any collection, or onto Home. */
    void movePage(String page,String book) {
        long now=System.currentTimeMillis();
        // Another grid, so no cell there yet: it takes the first free one (see Layout).
        ContentValues v=new ContentValues();v.put("book",home(book)?Things.HOME:book);v.put("updated",now);v.put("place",-now);v.put("cell",Layout.NONE);
        getWritableDatabase().update("notes",v,"id=?",new String[]{page});
    }

    /** The page of Home a row's cell is on, or {@link Layout#NO_PAGE}. */
    private int pageOf(String table,String id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT page FROM "+table+" WHERE id=?",new String[]{id})){return c.moveToFirst()?c.getInt(0):Layout.NO_PAGE;}
    }

    /** The cell a row was put in, or {@link Layout#NONE}. */
    private int cellOf(String table,String id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT cell FROM "+table+" WHERE id=?",new String[]{id})){return c.moveToFirst()?c.getInt(0):Layout.NONE;}
    }

    /**
     * A collection onto Home, or into any collection that is not itself or inside it (docs/HOME.md, decision 14).
     *
     * @throws IllegalArgumentException where it cannot go there, which is said in words the person can be shown
     */
    void moveBook(String book,String collection) {
        String into=home(collection)?Things.HOME:collection;
        Tree tree=tree();
        boolean holds=Things.HOME.equals(into)||tree.rows.containsKey(into);
        if(!Things.mayGoInto(tree.parents,book,into,holds))
            throw new IllegalArgumentException(holds?"A collection cannot go inside itself, or inside anything it holds."
                :"Only a collection can hold a collection.");
        long now=System.currentTimeMillis();
        ContentValues v=new ContentValues();v.put("parent",into);v.put("updated",now);v.put("ordinal",-now);v.put("cell",Layout.NONE);
        getWritableDatabase().update("things",v,"id=?",new String[]{book});
    }

    /**
     * The order of one level, written as the reader left it. Places are rewritten for the whole level at
     * once, in one transaction, so a list is never half reordered; `updated` is untouched, because putting
     * a page somewhere is not writing on it. Collections and notes are ordered each among their own kind.
     */
    void order(Branch.Kind kind,List<String> ids) {
        boolean note=kind==Branch.Kind.PAGE;
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(int at=0;at<ids.size();at++) {
                ContentValues v=new ContentValues();v.put(note?"place":"ordinal",at);
                db.update(table(kind),v,"id=?",new String[]{ids.get(at)});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    // ---- the addresses you share with ---------------------------------------------------------------------

    /** An address you share with, under the name you gave it. */
    static final class Contact {
        final String address,name; final boolean mine;
        /** What Maxima knows this device as, once its node has been told about it; empty until then. */
        final String contact;
        /** The keys to seal for it and to check it by, as they arrived in its pairing line; empty until paired. */
        final byte[] agreement,signing;
        /**
         * For a device of yours: whose it is, by their id (see {@link Persons}), and when its name - which is then
         * what your own devices call it, not your name - was decided. Empty and 0 for anybody else's.
         */
        final String person; final long named;
        Contact(String address,String name,boolean mine){this(address,name,mine,"",new byte[0],new byte[0]);}
        Contact(String address,String name,boolean mine,String contact,byte[] agreement,byte[] signing) {
            this(address,name,mine,contact,agreement,signing,"",0);
        }
        Contact(String address,String name,boolean mine,String contact,byte[] agreement,byte[] signing,String person,long named) {
            this(address,name,mine,contact,agreement,signing,person,named,"");
        }
        Contact(String address,String name,boolean mine,String contact,byte[] agreement,byte[] signing,String person,long named,String via) {
            this.address=address;this.name=name;this.mine=mine;this.contact=contact;
            this.agreement=agreement;this.signing=signing;this.person=person==null?"":person;this.named=named;
            this.via=via==null?"":via;
        }
        /** How it came to be known here: empty for a code scanned or a device connected, {@link Linking#VIA} for a list. */
        final String via;
        /** Whether this device can be sealed for: an address alone is somewhere to send nothing. */
        boolean paired(){return agreement.length>0&&signing.length>0;}
        /** Whether it is here only because a list of people for something shared named it: see {@link Linking}. */
        boolean listed(){return Linking.VIA.equals(via);}
    }

    /** Saved once, then picked: an address is long, and retyping one is how pages reach the wrong person. */
    /**
     * Every device this one can reach.
     *
     * <p>A row with no address in it is not one of them. One got written before the address was checked,
     * and it showed in the list as a second device with the same name as the first - two identical names,
     * one of which can never be sent to and gives no sign of it until a send fails. They are left in the
     * table rather than deleted, because a row nobody asked to remove is not one to remove quietly, and
     * they are simply not a device you can pick.
     */
    List<Contact> addresses() {
        List<Contact> known=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("addresses",null,"TRIM(address)<>''",null,null,null,"added ASC")) {
            while(c.moveToNext())known.add(readContact(c));
        }
        return known;
    }

    /** One saved address, or null. */
    Contact address(String address) {
        try(Cursor c=getReadableDatabase().query("addresses",null,"address=?",new String[]{address},null,null,null,"1")) {
            return c.moveToFirst()?readContact(c):null;
        }
    }

    private static Contact readContact(Cursor c) {
        return new Contact(c.getString(c.getColumnIndexOrThrow("address")),
            c.getString(c.getColumnIndexOrThrow("name")),c.getInt(c.getColumnIndexOrThrow("mine"))==1,
            c.getString(c.getColumnIndexOrThrow("contact")),
            unwritten(c.getString(c.getColumnIndexOrThrow("agreement"))),
            unwritten(c.getString(c.getColumnIndexOrThrow("signing"))),
            c.getString(c.getColumnIndexOrThrow("person")),c.getLong(c.getColumnIndexOrThrow("named")),
            c.getString(c.getColumnIndexOrThrow("via")));
    }

    private static byte[] unwritten(String kept) {
        if(kept==null||kept.isEmpty())return new byte[0];
        try{return android.util.Base64.decode(kept,android.util.Base64.NO_WRAP);}catch(RuntimeException e){return new byte[0];}
    }
    private static String written(byte[] raw) {
        return raw==null||raw.length==0?"":android.util.Base64.encodeToString(raw,android.util.Base64.NO_WRAP);
    }

    /**
     * A key as the one form this table keeps them in.
     *
     * <p>The same key travels in two shapes: the long one Java hands out, and the thirty-three bytes a
     * pairing code carries. Stored as they arrive, two rows for one device hold two different strings, and
     * every test of "is this the same device?" says no — which is how a phone ended up filed twice, once
     * under an address nothing can be sent to. So whichever shape arrives, one shape is written down.
     */
    private static String canonical(byte[] raw) {
        if(raw==null||raw.length==0)return "";
        try{return written(Point.read(raw).getEncoded());}
        catch(Exception notAKey){return written(raw);}
    }

    /** The same, for something already written down. */
    private static String canonical(String kept) {
        return canonical(unwritten(kept));
    }

    /** The keys a device handed over when it was paired with. Its address and name are kept as they were. */
    void pairedWith(String address,String name,boolean mine,byte[] agreement,byte[] signing) {
        // Keys without an address to send them to is not a device, it is half of one. Refused here rather
        // than kept and found out later, when the only sign is a send that fails for no stated reason.
        if(address==null||address.trim().isEmpty())
            throw new IllegalArgumentException("That code carried no address to send to.");

        // A device is its keys, not its address.
        //
        // The address changes: a node that has not reached a relay yet gives out one thing and a node
        // that has gives out another, and a phone that moves house gives out a third. Keyed on the
        // address, scanning the same phone's code again made a second device with the same name beside
        // the first - and everything already pointing at the old one, every share rule and every record
        // of what got through, went on pointing at an address nothing answers. So the row is found by its
        // signing key, and what is found is moved rather than duplicated.
        String was=deviceKeyed(signing);
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            // Every other row for this same device, not only the newest of them. Two rows with one key
            // is one device wearing two addresses, and the one that answers first decides what a note
            // arriving says it came from - which is how a note turned up from an address nothing can be
            // sent to, and how an acceptance went on waiting for an answer that had already arrived.
            for(String other:everyAddressOf(signing)) {
                if(other.equals(address)||other.equals(was))continue;
                db.execSQL("UPDATE OR REPLACE shares SET address=? WHERE address=?",new String[]{address,other});
                db.execSQL("UPDATE OR REPLACE sent SET address=? WHERE address=?",new String[]{address,other});
                db.execSQL("UPDATE OR REPLACE handed SET address=? WHERE address=?",new String[]{address,other});
                db.execSQL("UPDATE OR REPLACE refused SET address=? WHERE address=?",new String[]{address,other});
                db.execSQL("UPDATE OR REPLACE accepting SET address=? WHERE address=?",new String[]{address,other});
                movedFiles(db,other,address);
                db.delete("addresses","address=?",new String[]{other});
            }
            boolean moving=was!=null&&!was.equals(address);
            if(moving) {
                // Anything already sitting under the new address is a stub of the same device.
                db.delete("addresses","address=?",new String[]{address});
                // What points at it comes along: otherwise a re-scan silently unshares everything.
                db.execSQL("UPDATE OR REPLACE shares SET address=? WHERE address=?",new String[]{address,was});
                db.execSQL("UPDATE OR REPLACE sent SET address=? WHERE address=?",new String[]{address,was});
                db.execSQL("UPDATE OR REPLACE handed SET address=? WHERE address=?",new String[]{address,was});
                db.execSQL("UPDATE OR REPLACE refused SET address=? WHERE address=?",new String[]{address,was});
                // And whatever still waits for it to answer, or the waiting would be for an address nobody is at.
                db.execSQL("UPDATE OR REPLACE accepting SET address=? WHERE address=?",new String[]{address,was});
                movedFiles(db,was,address);
            }
            Contact already=was!=null?address(was):address(address);
            ContentValues v=new ContentValues();
            // A device of yours already named among your devices keeps that name: its code carries your name,
            // which is what other people see, not what your own devices call it.
            boolean ownName=already!=null&&already.mine&&already.named>0;
            v.put("address",address);v.put("name",ownName?already.name:name);
            v.put("named",ownName?already.named:0);v.put("person",already==null?"":already.person);
            // Whether this is a device of yours was said once, deliberately, and a re-scan is not the
            // moment to quietly change it back.
            v.put("mine",already!=null?(already.mine?1:0):(mine?1:0));
            v.put("added",System.currentTimeMillis());
            v.put("agreement",canonical(agreement));v.put("signing",canonical(signing));
            v.put("contact",already==null?"":already.contact);
            if(db.insertWithOnConflict("addresses",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)
                throw new IllegalStateException("Could not save that device");
            if(moving)db.delete("addresses","address=?",new String[]{was});
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    /** What says which files came from a device, and which it has, follows it to where it is now. */
    private static void movedFiles(SQLiteDatabase db,String was,String now) {
        db.execSQL("UPDATE files SET origin=? WHERE origin=?",new String[]{now,was});
        db.execSQL("UPDATE incoming SET origin=? WHERE origin=?",new String[]{now,was});
        db.execSQL("UPDATE OR REPLACE listed SET origin=? WHERE origin=?",new String[]{now,was});
        db.execSQL("UPDATE OR REPLACE reached SET address=? WHERE address=?",new String[]{now,was});
        db.execSQL("UPDATE OR IGNORE transfers SET address=? WHERE address=?",new String[]{now,was});
    }

    /**
     * One row per device, wherever there is more than one.
     *
     * <p>Done when the app opens, because a duplicate is not something the owner did and not something
     * they should have to tidy. It arises on its own: an address is a snapshot, a device that moved was
     * filed again under the new one, and the two rows then carry the same keys. Whichever answered first
     * decided what an arriving note said it came from - and if that was the older, it said it came from an
     * address nothing can be sent to.
     *
     * <p>The newest row wins, and everything pointing at the others is carried to it.
     */
    void tidyDevices() {
        java.util.Map<String,List<String>> byKey=new java.util.LinkedHashMap<>();
        try(Cursor c=getReadableDatabase().query("addresses",new String[]{"signing","address"},
                "signing<>'' AND TRIM(address)<>''",null,null,null,"added ASC")) {
            while(c.moveToNext())
                byKey.computeIfAbsent(canonical(c.getString(0)),any->new ArrayList<>()).add(c.getString(1));
        }
        SQLiteDatabase db=getWritableDatabase();
        for(List<String> same:byKey.values()) {
            if(same.size()<2)continue;
            String keep=same.get(same.size()-1);
            db.beginTransaction();
            try {
                // And the survivor's keys are rewritten in the one form, so the next opening has nothing
                // left to do.
                ContentValues one=new ContentValues();
                try(Cursor c=getReadableDatabase().query("addresses",new String[]{"agreement","signing"},
                        "address=?",new String[]{keep},null,null,null,"1")) {
                    if(c.moveToFirst()) {
                        one.put("agreement",canonical(c.getString(0)));
                        one.put("signing",canonical(c.getString(1)));
                        db.update("addresses",one,"address=?",new String[]{keep});
                    }
                }
                for(String other:same) {
                    if(other.equals(keep))continue;
                    db.execSQL("UPDATE OR REPLACE shares SET address=? WHERE address=?",new String[]{keep,other});
                    db.execSQL("UPDATE OR REPLACE sent SET address=? WHERE address=?",new String[]{keep,other});
                    db.execSQL("UPDATE OR REPLACE handed SET address=? WHERE address=?",new String[]{keep,other});
                    db.execSQL("UPDATE OR REPLACE refused SET address=? WHERE address=?",new String[]{keep,other});
                    db.execSQL("UPDATE OR REPLACE accepting SET address=? WHERE address=?",new String[]{keep,other});
                    db.execSQL("UPDATE notes SET origin=? WHERE origin=?",new String[]{keep,other});
                    db.execSQL("UPDATE things SET origin=? WHERE origin=?",new String[]{keep,other});
                    movedFiles(db,other,keep);
                    db.delete("addresses","address=?",new String[]{other});
                }
                db.setTransactionSuccessful();
            } finally {db.endTransaction();}
        }
    }

    /**
     * A shelf filed under an old name, taken over under the new one.
     *
     * <p>Only where there is nothing under the new name yet: if both exist, the one already in use stays
     * and the other is left for {@link #tidyShelves} to clear away if it is empty.
     */
    private void adopt(String was,String now) {
        if(was.isEmpty()||now.isEmpty()||was.equals(now))return;
        if(there("things",was)&&!there("things",now)) {
            SQLiteDatabase db=getWritableDatabase();
            db.beginTransaction();
            try {
                ContentValues v=new ContentValues();v.put("id",now);
                db.update("things",v,"id=?",new String[]{was});
                // Whatever it held, collections and notes, is still in it.
                db.execSQL("UPDATE things SET parent=? WHERE parent=?",new String[]{now,was});
                db.execSQL("UPDATE notes SET book=? WHERE book=?",new String[]{now,was});
                db.execSQL("UPDATE OR REPLACE shares SET target=? WHERE target=?",new String[]{now,was});
                db.setTransactionSuccessful();
            } finally {db.endTransaction();}
        }
    }

    private boolean there(String table,String id) {
        try(Cursor c=getReadableDatabase().query(table,new String[]{"id"},"id=?",new String[]{id},
                null,null,null,"1")) {
            return c.moveToFirst();
        }
    }

    /**
     * Empty copies of a shelf somebody shares, cleared away.
     *
     * <p>They came from the same shelf arriving twice under two names, back when a shelf was named after
     * the address it came from. Only the empty ones go, and only where another shelf from the same device
     * has the same name: nothing that holds anything is ever removed by tidying.
     */
    /**
     * Anything already here that came from somebody, with that somebody written down as having it.
     *
     * <p>For things that arrived before the membership travelled with them. Without it, a collection
     * somebody shared with you lists them under "not shared with", which is the one thing they are not.
     */
    void tidyOrigins() {
        owned("things",Sharing.Scope.THING);
        owned("notes",Sharing.Scope.PAGE);
    }

    private void owned(String table,Sharing.Scope scope) {
        java.util.List<String[]> theirs=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT id,origin FROM "+table+" WHERE theirs=1 AND TRIM(origin)<>''",null)) {
            while(c.moveToNext())theirs.add(new String[]{c.getString(0),c.getString(1)});
        }
        for(String[] one:theirs) {
            boolean known=false;
            for(Sharing.Rule rule:membership(scope,one[0]))
                if(one[1].equals(rule.address))known=true;
            if(known||one[1].trim().isEmpty())continue;
            Contact who=address(one[1]);
            // Where it came from is where they were that day. A device's address moves, and one that is
            // nobody's any more is not somebody to write down as having a thing: this wrote them down
            // again at every opening, a share with nobody that nothing could be sealed for - so the pad
            // said "waiting to go" for ever and every Sync of everything ended in "somebody has not been
            // paired yet". The next thing to arrive from them says where they are now; see landed.
            if(who==null)continue;
            ContentValues v=new ContentValues();
            v.put("scope",scopeHere(scope,one[0]).name());v.put("target",one[0]);v.put("address",one[1]);
            v.put("level",Sharing.Level.ADMIN.said());v.put("mine",1);
            v.put("changed",1L);v.put("added",System.currentTimeMillis());
            v.put("who",who==null?"":canonical(who.signing));v.put("name",who==null?"":who.name);
            getWritableDatabase().insertWithOnConflict("shares",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    /**
     * Rows that can never do anything, cleared away.
     *
     * <p>A share with no address to send to is not a share: nothing can be sealed for nobody, and the
     * attempt is reported as "somebody has not been paired yet" — which sends the reader looking for a
     * pairing problem that is not there. A device with no keys is the same thing from the other end.
     * Both were written by this app and neither is anybody's decision to keep.
     */
    void tidyBroken() {
        SQLiteDatabase db=getWritableDatabase();
        db.delete("shares","TRIM(address)=''",null);
        db.delete("addresses","TRIM(address)='' OR signing='' OR agreement=''",null);
        // And a line nobody decided - worked out from where something once came from - that points at an
        // address no device here is at any more. One somebody did decide is left: that they are not
        // paired yet is then the truth, and worth being told.
        db.delete("shares","changed<=1 AND address NOT IN (SELECT address FROM addresses)",null);
    }

    void tidyShelves() {
        SQLiteDatabase db=getWritableDatabase();
        // A shelf of ours that came back to us as somebody else's. It happens when you share something
        // with a phone that shares it on, or back: what arrives describes their shelf, and ours is where
        // the notes already are - so theirs stays empty beside it with the same name on it. Only the empty
        // ones go - nothing in them, no collection, no note, no file - and a note arriving for one would build
        // it again. Deepest first, a level a time: one emptied by the pass before goes in the next.
        for(int pass=0;pass<Things.DEEPEST;pass++)
            if(db.delete("things","theirs=1 AND "+HOLDS_NOTHING+" AND name IN (SELECT name FROM things WHERE theirs=0)",null)==0)break;
        // Copies of one shelf of theirs, told apart as the two kinds were: those on Home, and those inside another.
        clearEmpty(db,"SELECT id,name,origin,parent FROM things WHERE theirs=1");
    }

    /** A collection that holds nothing at all: no collection, no note, and no file. */
    private static final String HOLDS_NOTHING="id NOT IN (SELECT book FROM notes) AND id NOT IN (SELECT parent FROM things)"
        +" AND id NOT IN (SELECT note FROM files)";

    private void clearEmpty(SQLiteDatabase db,String list) {
        java.util.Map<String,List<String>> alike=new java.util.LinkedHashMap<>();
        try(Cursor c=db.rawQuery(list,null)) {
            while(c.moveToNext())
                alike.computeIfAbsent(c.getString(2)+"\u0000"+c.getString(1)+"\u0000"+home(c.getString(3)),any->new ArrayList<>())
                    .add(c.getString(0));
        }
        for(List<String> same:alike.values()) {
            if(same.size()<2)continue;
            for(String one:same) {
                boolean empty;
                try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM things WHERE id=? AND "+HOLDS_NOTHING,new String[]{one})){empty=c.moveToFirst()&&c.getLong(0)>0;}
                if(!empty)continue;
                // Leave at least one, even if every copy is empty.
                if(same.size()-1<1)break;
                db.delete("things","id=?",new String[]{one});
                db.delete("shares","target=?",new String[]{one});
                same.remove(one);
                break;
            }
        }
    }

    /** Every address this one device is filed under, which should be one and sometimes is not. */
    private List<String> everyAddressOf(byte[] signing) {
        List<String> all=new ArrayList<>();
        String key=canonical(signing);
        if(key.isEmpty())return all;
        try(Cursor c=getReadableDatabase().query("addresses",new String[]{"address","signing"},
                "signing<>''",null,null,null,null)) {
            while(c.moveToNext())if(key.equals(canonical(c.getString(1))))all.add(c.getString(0));
        }
        return all;
    }

    /** One device by the key it signs with, whatever address it is filed under. */
    private Contact byKey(String key) {
        if(key==null||key.trim().isEmpty())return null;
        for(Contact one:addresses())if(key.equals(canonical(one.signing)))return one;
        return null;
    }

    /** The address a device is filed under now, found by the key it signs with, or null for a new one. */
    private String deviceKeyed(byte[] signing) {
        String key=canonical(signing);
        if(key.isEmpty())return null;
        // Compared in the one form, not as it happens to be written: rows put there by an older build
        // hold the long shape and a code read today carries the short one.
        try(Cursor c=getReadableDatabase().query("addresses",new String[]{"address","signing"},
                "signing<>''",null,null,null,"added ASC")) {
            while(c.moveToNext())if(key.equals(canonical(c.getString(1))))return c.getString(0);
        }
        return null;
    }

    /** What Maxima came to know this address as, so a message can be handed to it by name. */
    void knownAs(String address,String contact) {
        ContentValues v=new ContentValues();v.put("contact",contact);
        getWritableDatabase().update("addresses",v,"address=?",new String[]{address});
    }
    void addAddress(String address,String name,boolean mine) {
        if(address==null||address.trim().isEmpty())
            throw new IllegalArgumentException("That code carried no address to send to.");
        ContentValues v=new ContentValues();v.put("address",address);v.put("name",name);
        v.put("mine",mine?1:0);v.put("added",System.currentTimeMillis());
        if(getWritableDatabase().insertWithOnConflict("addresses",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)
            throw new IllegalStateException("Could not save the address");
    }
    void removeAddress(String address){getWritableDatabase().delete("addresses","address=?",new String[]{address});}

    /**
     * Whether an address is another device of yours. It decides which way things travel — both ways with
     * your own devices, one way to anybody else — and it is the kind of thing you set once by looking at a
     * list, not by answering a question in the middle of adding something.
     */
    void setMine(String address,boolean mine) {
        ContentValues v=new ContentValues();v.put("mine",mine?1:0);
        // A device that is not yours is nobody's of yours: whose it is is known again from its next card.
        if(!mine)v.put("person","");
        // Only the address is changed. What each share lets this address do is its own setting, decided
        // where the sharing is decided: the same person may read one book of yours and work in another.
        getWritableDatabase().update("addresses",v,"address=?",new String[]{address});
    }

    /**
     * What a device of yours is called among your devices, and when that was decided - from its own hello when the
     * two bonded, or from a card (see {@link Persons}). The caller has decided it is yours: marked so here, or listed
     * as yours on a card from a device that is yours at both ends - which is the owner's word, even where the switch
     * here was never turned. Whether the decision is later than the one here is the caller's to weigh too.
     */
    boolean ownDeviceNamed(byte[] signing,String name,long decided) {
        if(name==null||name.trim().isEmpty())return false;
        ContentValues v=new ContentValues();v.put("name",name.trim());v.put("named",decided);
        // Every row filed under its key, not only the first, so no row of it is left with the old name. True only
        // where something changed, so the same card twice says nothing new.
        int changed=0;
        for(String address:everyAddressOf(signing))
            changed+=getWritableDatabase().update("addresses",v,"address=? AND (name<>? OR named<>?)",
                new String[]{address,name.trim(),Long.toString(decided)});
        return changed>0;
    }

    /** Whose a device of yours is, by their id: every device of yours paired here, once this one's owner is known. */
    void ownDevicesAre(String person) {
        ContentValues v=new ContentValues();v.put("person",person==null?"":person);
        getWritableDatabase().update("addresses",v,"mine=1",null);
        ContentValues none=new ContentValues();none.put("person","");
        getWritableDatabase().update("addresses",none,"mine=0",null);
    }

    /**
     * Every device paired here, by its key in one form, and when its name was decided: 0 for a name nobody decided
     * among your devices, which any decision is later than. Not only the ones marked yours - a card from a device
     * that is yours at both ends names the devices it counts as yours, and those are yours by its word (see Persons).
     */
    java.util.Map<String,Long> devicesNamed() {
        java.util.Map<String,Long> named=new java.util.HashMap<>();
        for(Contact one:addresses())if(one.paired())named.put(Persons.key(one.signing),one.named);
        return named;
    }

    private static int shared(List<Sharing.Rule> rules,Sharing.Scope scope,String target) {
        int count=0;
        for(Sharing.Rule rule:rules)if(alike(rule.scope,scope)&&rule.target.equals(target))count++;
        return count;
    }

    /** Every sharing rule in the pad. Small enough to read whole, and the audience of a page is decided in memory. */
    List<Sharing.Rule> shares() {
        List<Sharing.Rule> rules=new ArrayList<>();
        // Somebody taken off keeps a row, at GONE, so that an older copy of the list cannot put them back.
        // A membership without that is a membership where removing somebody is undone by the next message.
        try(Cursor c=getReadableDatabase().query("shares",null,"level>0",null,null,null,"added ASC")) {
            while(c.moveToNext())rules.add(rule(c));
        }
        return rules;
    }

    /**
     * Every row, including the people who were taken off: what travels, and what merges. A collection's rows are
     * read whatever level they were written at (see {@link #scopeHere}).
     */
    List<Sharing.Rule> membership(Sharing.Scope scope,String target) {
        List<Sharing.Rule> all=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("shares",null,scopeIs(scope)+" AND target=?",
                new String[]{target},null,null,"added ASC")) {
            while(c.moveToNext())all.add(rule(c));
        }
        return all;
    }

    private static Sharing.Rule rule(Cursor c) {
        return new Sharing.Rule(Sharing.Scope.valueOf(c.getString(c.getColumnIndexOrThrow("scope"))),
            c.getString(c.getColumnIndexOrThrow("target")),c.getString(c.getColumnIndexOrThrow("address")),
            Sharing.Level.of(c.getInt(c.getColumnIndexOrThrow("level"))),
            c.getLong(c.getColumnIndexOrThrow("changed")),
            c.getString(c.getColumnIndexOrThrow("who")));
    }

    /**
     * One person's standing in one thing, written down with when it was decided.
     *
     * <p>Taking somebody off is a decision like any other, so it is written down as one rather than by
     * deleting the row: a list that forgets a removal is a list where the removal comes undone the next
     * time an older copy of it arrives.
     */
    void setLevel(Sharing.Scope scope,String target,String address,Sharing.Level level,String name) {
        setLevel(scope,target,address,level,name,System.currentTimeMillis());
    }

    /**
     * @param changed when it was decided, where that was somewhere else and some time ago
     * @param scope   the level it was asked at; written at whatever the thing's rows already say (see {@link #scopeHere})
     */
    void setLevel(Sharing.Scope scope,String target,String address,Sharing.Level level,String name,long changed) {
        scope=scopeHere(scope,target);
        NoteStore.Contact who=address(address);
        ContentValues v=new ContentValues();
        v.put("scope",scope.name());v.put("target",target);v.put("address",address);
        v.put("level",level.said());v.put("mine",level.writes()?1:0);
        v.put("changed",changed);v.put("added",System.currentTimeMillis());
        String before=sharedName(scope,target,address);
        // A device never paired here is known by the key its list gave: kept, or a later list naming the same key
        // would not find this row, and somebody taken off would be put back.
        v.put("who",who!=null&&who.signing.length>0?canonical(who.signing):sharedKey(scope,target,address));
        // Nobody named: the device's own name, or else the one this row already had from the list it came in.
        v.put("name",name!=null?name:who!=null&&who.name!=null&&!who.name.isBlank()?who.name:before);
        if(getWritableDatabase().insertWithOnConflict("shares",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)
            throw new IllegalStateException("Could not save who this is shared with");
    }

    /** What one row of a list calls its device; empty where it says nothing. */
    private String sharedName(Sharing.Scope scope,String target,String address){return sharedColumn("name",scope,target,address);}
    /** The key one row of a list knows its device by; empty where it says nothing. */
    private String sharedKey(Sharing.Scope scope,String target,String address){return sharedColumn("who",scope,target,address);}
    private String sharedColumn(String column,Sharing.Scope scope,String target,String address) {
        try(Cursor c=getReadableDatabase().query("shares",new String[]{column},scopeIs(scope)+" AND target=? AND address=?",
                new String[]{target,address},null,null,null,"1")) {
            return c.moveToFirst()&&c.getString(0)!=null?c.getString(0):"";
        }
    }

    /**
     * A membership that arrived, folded into the one here.
     *
     * <p>Per person, the later decision stands. Not the later message: two admins out of touch with each
     * other both have a say, and the only thing they can agree on afterwards without asking anybody is
     * which of them decided last.
     *
     * @param names what the list calls each device, by key (or by address where it gave no key): the name is what
     *              lets a device never paired here be said by name rather than as "A device", so it is kept even
     *              where the decision beside it is not new
     * @return how many entries this phone did not already have as new or newer
     */
    int mergeMembership(Sharing.Scope scope,String target,List<Sharing.Rule> theirs,java.util.Map<String,String> names) {
        if(theirs==null||theirs.isEmpty())return 0;
        // Written at the level the thing's rows already say, whatever level the list came at: see scopeHere.
        scope=scopeHere(scope,target);
        java.util.Map<String,Sharing.Rule> mine=new java.util.HashMap<>();
        for(Sharing.Rule one:membership(scope,target))
            mine.put(one.key.isEmpty()?one.address:one.key,one);
        int changed=0;
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            for(Sharing.Rule one:theirs) {
                String by=one.key.isEmpty()?one.address:one.key;
                Sharing.Rule here=mine.get(by);
                String said=names==null?null:names.get(by);
                // The name it had here, where the list says none: a row replaced loses everything it did not say.
                String name=said!=null&&!said.isBlank()?said:here==null?"":sharedName(scope,target,here.address);
                if(here!=null&&here.changed>=one.changed) {
                    if(said!=null&&!said.isBlank()&&sharedName(scope,target,here.address).isEmpty()) {
                        ContentValues v=new ContentValues();v.put("name",said);
                        db.update("shares",v,"scope=? AND target=? AND address=?",new String[]{scope.name(),target,here.address});
                    }
                    continue;
                }
                String where=here!=null&&one.address.trim().isEmpty()?here.address:one.address;
                // A device this one can seal for is filed where its contact is, whatever address the list last saw it
                // at: a row at another address is somebody nothing here can reach, and its round was drawn dashed.
                Contact known=one.key.isEmpty()?null:byKey(one.key);
                if(known!=null&&known.paired())where=known.address;
                if(where.trim().isEmpty())continue;
                ContentValues v=new ContentValues();
                v.put("scope",scope.name());v.put("target",target);
                v.put("address",where);
                v.put("level",one.level.said());v.put("mine",one.level.writes()?1:0);
                v.put("changed",one.changed);v.put("added",System.currentTimeMillis());
                v.put("who",one.key);v.put("name",name);
                db.insertWithOnConflict("shares",null,v,SQLiteDatabase.CONFLICT_REPLACE);
                if(here!=null&&!here.address.equals(where))
                    db.delete("shares","scope=? AND target=? AND address=?",
                        new String[]{scope.name(),target,here.address});
                changed++;
            }
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
        return changed;
    }

    /**
     * Which level a note is actually shared at — its own, the book's, or the collection's. See {@link #sharedAt(List)}.
     */
    Sharing.Scope sharedAt(String collection,String book,String page) {
        Sharing.Rule on=sharedAt(Sharing.path(collection,book,page));
        return on==null?null:on.scope;
    }

    /**
     * The nearest thing up a path that is shared by itself - the thing at its end, or the collection nearest it, and
     * so on up to Home - said as the first rule on it: its level and its id. Null where nothing on the path is.
     *
     * <p>The membership belongs to whichever of those carries the rule, because that is the thing people
     * were given. Sharing a collection and then listing its members note by note would be a different list
     * on every note in it.
     */
    Sharing.Rule sharedAt(List<String> path) {
        List<Sharing.Rule> rules=shares();
        for(int at=path.size()-1;at>=0;at--)
            for(Sharing.Rule rule:rules)
                if(rule.scope!=Sharing.Scope.LIBRARY&&rule.target.equals(path.get(at)))return rule;
        return null;
    }

    /** The membership of one thing, as it travels: every device, by key, with when it was decided. */
    List<Parcel.Member> travelling(Sharing.Scope scope,String target) {
        List<Parcel.Member> going=new ArrayList<>();
        if(scope==null)return going;
        for(Sharing.Rule rule:membership(scope,target)) {
            Contact who=address(rule.address);
            // By the name it has here, or else the one its list came with: a device this one never paired with
            // went on in the list with no name, and every device after this one had nothing to call it.
            String name=who!=null&&who.name!=null&&!who.name.isBlank()?who.name:memberName(rule.address);
            // And the key to seal for it, where this device has one and it is still on the thing: that is what lets
            // everybody on the list reach everybody else on it (see Linking). Nobody taken off is handed on.
            byte[] seal=who!=null&&who.paired()&&rule.level!=Sharing.Level.GONE?shortened(who.agreement):null;
            going.add(new Parcel.Member(rule.key.isEmpty()&&who!=null?canonical(who.signing):rule.key,
                rule.address,name,rule.level.said(),rule.changed,seal));
        }
        // And this phone, which is a member of anything it shares and never appears in its own list: as the weakest
        // decision there is, at what it may do here - Admin only where it is whose the thing is. Every device said Admin
        // until 0.1.040, and a device nothing else was known about was an admin wherever its list went.
        Sharing.Level mine=owns(scope,target)?Sharing.Level.ADMIN:myLevel(scope,target);
        going.add(new Parcel.Member(mySigningKey,myAddress,myName,(mine==null?Sharing.Level.WRITE:mine).said(),1L,myAgreement));
        return going;
    }

    /** A key in the short form a list carries, whichever form it is kept in; null where it is not a key. */
    private static byte[] shortened(byte[] kept) {
        try{return Point.shorten(Point.read(kept));}catch(Exception notAKey){return null;}
    }

    /**
     * What this phone calls itself and signs with, so it can name itself in a membership.
     *
     * <p>Set once when the app starts. The notebook has no business fetching keys; it is told.
     */
    String mySigningKey="", myName="", myAddress="";
    /** And the key to seal for it, short, so the others on a list can reach it: see {@link Linking}. */
    byte[] myAgreement=new byte[0];

    /** Which level a list that arrived is the list of: a note's own where it says nothing, or says what is not known. */
    private static Sharing.Scope listScope(String said) {
        if(said!=null&&!said.isEmpty())
            try{return Sharing.Scope.valueOf(said);}catch(IllegalArgumentException unknown){/* a note's own */}
        return Sharing.Scope.PAGE;
    }

    /**
     * What a list that arrived is the list of, by this phone's own name for it; empty where it names nothing.
     *
     * <p>Under the shelf's name here, whoever sent it: see Parcel.shelfHere. A list for the owner's own book, sent
     * back by an admin who had added somebody, was filed under a name for a book that is not on this phone, and the
     * owner never heard about the person. A collection's list is the list of the sender's id for it, which every build
     * since lists travelled says; where it is not said, a 0.1 COLLECTION is the note's top collection and a BOOK its
     * second, as the sender named them. A THING that names nothing is the note's own.
     *
     * @param top    the sender's id for the top collection above the note, or empty
     * @param second the sender's id for the one inside that, or empty
     */
    private String listedThing(String from,String note,Sharing.Scope scope,String target,String top,String second) {
        if(!onShelf(scope))return target.isEmpty()?note:target;
        String theirs=!target.isEmpty()?target:scope==Sharing.Scope.COLLECTION?top:scope==Sharing.Scope.BOOK?second:"";
        if(theirs==null||theirs.isEmpty())return scope==Sharing.Scope.THING?note:"";
        return shelfId(from,theirs);
    }

    private String listedThing(String from,String note,Parcel.Sent parcel,Sharing.Scope scope) {
        String[] two=topTwo(parcel);
        return listedThing(from,note,scope,parcel.target,two[0],two[1]);
    }

    // ---- linked through what is shared: see Linking ------------------------------------------------------------

    /**
     * Whether the device a list came from may say who has the thing here: it is whose the thing is, or an admin of it
     * here - or, for a thing of this phone's own, an admin this phone made. Asked after the list is folded in, as
     * anything about a list is. Only such a list links anybody.
     */
    boolean listFromASay(String from,String note,Parcel.Sent parcel) {
        if(parcel==null||from==null)return false;
        Sharing.Scope scope=listScope(parcel.scope);
        String here=listedThing(from,note,parcel,scope);
        if(here.isEmpty())return false;
        String origin=cameFrom(kindFor(scope),here);
        if(!origin.isEmpty())return hasASay(from,scope,here,origin);
        // Another of the owner's own devices, about a thing of the owner's: the owner.
        if(isOwner(from,origin))return true;
        Contact who=address(from);
        String key=who==null?"":canonical(who.signing);
        for(Sharing.Rule rule:membership(scope,here))
            if((rule.address.equals(from)||!key.isEmpty()&&key.equals(rule.key))&&rule.level.shares())return true;
        return false;
    }

    /** The keys, as lists write them, of every device this one can seal for - paired, or linked through a list. */
    Set<String> linkedKeys() {
        Set<String> keys=new HashSet<>();
        for(Contact one:addresses())if(one.paired())keys.add(canonical(one.signing));
        return keys;
    }

    /**
     * A device a list of people named, written down as linked through it: its keys, its address and the name the list
     * gave it, as nobody's device of this owner's. Nothing is written where the same device is already here with its
     * keys - paired by a code, or linked before - so a list never changes what a pairing said.
     *
     * @param waiting whether nothing is to go to it until it answers: true where this phone is the one asking
     * @return whether it was written down now
     */
    boolean linkThrough(String address,String name,byte[] agreement,byte[] signing,boolean waiting) {
        String key=canonical(signing);
        if(key.isEmpty()||agreement==null||agreement.length==0||address==null||address.trim().isEmpty())return false;
        Contact already=byKey(key);
        if(already!=null&&already.paired())return false;
        pairedWith(address,name==null||name.isBlank()?"":name.trim(),false,agreement,signing);
        ContentValues v=new ContentValues();v.put("via",Linking.VIA);
        getWritableDatabase().update("addresses",v,"address=?",new String[]{address});
        filedWhereItIs(address,key);
        if(waiting)accepting(address,name,Linking.SCOPE,Linking.TARGET,true);
        return true;
    }

    /**
     * Every row of every list that names a device by its key, moved to the address its contact is at. Lists carry the
     * address the sender last saw, which need not be the one this device reaches it by; a row left at the other one
     * belonged to nobody here, so the device was drawn as a dashed "not linked" round and sent nothing, linked or not.
     */
    private void filedWhereItIs(String address,String key) {
        if(key==null||key.isEmpty())return;
        getWritableDatabase().execSQL("UPDATE OR REPLACE shares SET address=? WHERE who=? AND address<>?",new String[]{address,key,address});
    }

    /**
     * Linking put right, once a run: every list row filed where its device's contact is, and waiting given up for a
     * device that is no longer here at all. For notebooks written by the first build that linked, where both went wrong.
     */
    void tidyLinks() {
        for(Contact one:addresses())if(one.paired())filedWhereItIs(one.address,canonical(one.signing));
        getWritableDatabase().execSQL("DELETE FROM accepting WHERE scope=? AND address NOT IN (SELECT address FROM addresses)",
            new String[]{Linking.SCOPE});
    }

    /** The devices linked through a list that have not answered yet: nothing of anything goes to them until they do. */
    Set<String> linkingNow() {
        Set<String> waiting=new HashSet<>();
        try(Cursor c=getReadableDatabase().query("accepting",new String[]{"address"},"scope=?",new String[]{Linking.SCOPE},
                null,null,null)) {
            while(c.moveToNext())waiting.add(c.getString(0));
        }
        return waiting;
    }

    /**
     * The row of a list here that names a device by the key it signs with, on a thing still here and at any level but
     * taken off; null where none does. What a hello from a device not linked here has to find: see {@link Linking#names}.
     */
    Sharing.Rule namesDevice(byte[] signing) {
        List<Sharing.Rule> here=new ArrayList<>();
        for(Sharing.Rule rule:shares())
            if(rule.scope!=Sharing.Scope.LIBRARY&&!thingName(kindFor(rule.scope),rule.target).isEmpty())here.add(rule);
        return Linking.names(here,signing);
    }

    /** What People and devices says under each device linked through a list, by its address. */
    Map<String,String> linkedLines() {
        Map<String,String> lines=new java.util.HashMap<>();
        for(Contact one:addresses())if(one.listed())lines.put(one.address,Linking.through(listedIn(one.address)));
        return lines;
    }

    /**
     * A membership that arrived with a note, folded into the one here.
     *
     * <p>Filed under this phone's own name for the thing, which is not the name the sender used: a shelf
     * of theirs is kept under an id made from who they are and what they call it. See {@link Parcel}.
     */
    void tookMembership(String from,String note,Parcel.Sent parcel) {
        if(parcel==null)return;
        Sharing.Scope scope=listScope(parcel.scope);
        Note mine=get(note);
        tookList(from,scope,listedThing(from,note,parcel,scope),parcel.members,mine==null||mine.theirs);
    }

    /** A collection's list, arriving with it on its own (see {@link Carton}): filed as a note's is. */
    void tookMembership(String from,String collection,Carton.Sent carton) {
        if(carton==null)return;
        // A collection's list is a collection's, whatever it says: one that says nothing is its own.
        Sharing.Scope scope=listScope(carton.scope);
        if(!onShelf(scope))scope=Sharing.Scope.THING;
        String top=carton.path.size()>0?carton.path.get(0).id:carton.id,
            second=carton.path.size()>1?carton.path.get(1).id:carton.path.size()==1?carton.id:"";
        String here=listedThing(from,collection,scope,carton.target,top,second);
        String whose=whoseHere(Branch.Kind.COLLECTION,collection);
        tookList(from,scope,here,carton.members,whose==null||!whose.isEmpty());
    }

    /**
     * One list, folded in: whoever sent it written down as having the thing, and then everybody it names.
     *
     * @param theirs whether the thing is somebody else's here, or not here yet: only then is what the list says this
     *               device may do with it kept, since what somebody says this device may do with its own is nothing
     */
    private void tookList(String from,Sharing.Scope scope,String here,List<Parcel.Member> members,boolean theirs) {
        Contact who=address(from);
        String by=who==null||who.signing.length==0?from:canonical(who.signing);
        if(here.isEmpty())return;
        // Whether it comes from whoever the thing is from - or, for a thing not here yet, from whoever it will then be
        // from. What anybody else's list says about the owner and the admins is not taken: see Sharing.mayCarry.
        String whose=whoseHere(kindFor(scope),here);
        boolean fromOwner=whose==null||isOwner(from,whose);

        // Whoever sent it has it. Obvious, and it was not being written down: a thing that arrived from
        // somebody listed them under "not shared with", which is the one thing they demonstrably are not.
        // Written weakly - as of the beginning of time - so that any real word from them outranks it.
        if(!by.isEmpty()&&!from.trim().isEmpty()) {
            boolean known=false;
            for(Sharing.Rule rule:membership(scope,here))
                if(by.equals(rule.key)||from.equals(rule.address))known=true;
            if(!known) {
                ContentValues v=new ContentValues();
                v.put("scope",scopeHere(scope,here).name());v.put("target",here);v.put("address",from);
                // At Admin only for whoever it is from: anybody else nothing is known about writes, until a list says.
                v.put("level",(fromOwner?Sharing.Level.ADMIN:Sharing.Level.WRITE).said());v.put("mine",1);
                v.put("changed",1L);v.put("added",System.currentTimeMillis());
                v.put("who",by);v.put("name",who==null?"":who.name);
                getWritableDatabase().insertWithOnConflict("shares",null,v,SQLiteDatabase.CONFLICT_IGNORE);
            }
        }
        if(members.isEmpty())return;
        List<Sharing.Rule> listed=new ArrayList<>();
        java.util.Map<String,String> names=new java.util.HashMap<>();
        for(Parcel.Member one:members) {
            if(!one.key.isEmpty()&&one.key.equals(mySigningKey)) {
                // Ourselves, from their side. Never a share - that would be this phone sending to itself -
                // but it is the only place this phone is told what it may do, so it is kept where it can.
                // Only for a thing of theirs: what somebody says this phone may do with its own is nothing.
                Sharing.Level said=Sharing.Level.of(one.level);
                if(theirs&&Sharing.mayCarry(fromOwner,false,myLevel(scope,here),said))
                    stands(scope,here,said,one.changed);
                continue;
            }
            String where=one.address;
            // The sender's own entry carries no address in lists written before this: a phone does not
            // know the address others reach it at. It is the phone this arrived from, so it is that one.
            if(where.trim().isEmpty()&&!one.key.isEmpty()&&one.key.equals(by))where=from;
            if(where.trim().isEmpty()) {
                Contact known=byKey(one.key);
                if(known!=null)where=known.address;
            }
            // Somebody we have no way to reach and no way to name is not something to write down. They
            // will be in the next list that arrives, with an address on it.
            if(where.trim().isEmpty())continue;
            if(!fromOwner) {
                Sharing.Rule before=memberHere(scope,here,where,one.key);
                boolean aboutOwner=ownerMember(where,one.key,whose);
                if(!Sharing.mayCarry(false,aboutOwner,before==null?null:before.level,Sharing.Level.of(one.level)))continue;
            }
            listed.add(new Sharing.Rule(scope,here,where,Sharing.Level.of(one.level),one.changed,one.key));
            if(!one.name.isBlank())names.put(one.key.isEmpty()?where:one.key,one.name.trim());
        }
        mergeMembership(scope,here,listed,names);
    }

    /** One member's row on a thing, found by the key it signs with or else its address; null where there is none. */
    private Sharing.Rule memberHere(Sharing.Scope scope,String target,String address,String key) {
        Sharing.Rule found=null;
        for(Sharing.Rule rule:membership(scope,target)) {
            if(key!=null&&!key.isEmpty()&&key.equals(rule.key))return rule;
            if(rule.address.equals(address))found=rule;
        }
        return found;
    }

    /** What this phone was told it may do with one thing. The later decision stands, as for anybody. */
    void stands(Sharing.Scope scope,String target,Sharing.Level level,long changed) {
        if(scope==null||target==null||target.isEmpty()||level==null)return;
        SQLiteDatabase db=getWritableDatabase();
        // Kept at the level the thing's row already says, as its list is (see scopeHere).
        String kept=null;
        try(Cursor c=db.query("standing",new String[]{"changed","scope"},scopeIs(scope)+" AND target=?",
                new String[]{target},null,null,"changed DESC","1")) {
            if(c.moveToFirst()){if(c.getLong(0)>changed)return;kept=c.getString(1);}
        }
        ContentValues v=new ContentValues();
        v.put("scope",kept!=null?kept:scopeHere(scope,target).name());v.put("target",target);v.put("level",level.said());v.put("changed",changed);
        db.insertWithOnConflict("standing",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * What this phone may do with something another device shares with it, or null where nothing says.
     *
     * <p>On the thing itself, or on whatever holds it however far up: somebody made an admin of a collection
     * is an admin of everything in it. The most that any of them says.
     */
    Sharing.Level myLevel(Sharing.Scope scope,String target) {
        List<String> where=new ArrayList<>();
        where.add(target);
        if(scope!=Sharing.Scope.LIBRARY)where.addAll(above(target));
        Sharing.Level most=null;
        for(String one:where) {
            if(one==null||one.isEmpty())continue;
            try(Cursor c=getReadableDatabase().query("standing",new String[]{"level"},"target=?",
                    new String[]{one},null,null,"level DESC","1")) {
                if(!c.moveToFirst())continue;
                Sharing.Level said=Sharing.Level.of(c.getInt(0));
                if(most==null||said.ordinal()>most.ordinal())most=said;
            }
        }
        if(most!=null)return most;
        // Told nothing: a note at least says whether this phone may write in it.
        Boolean writes=mayWriteIn(scope==Sharing.Scope.PAGE?Branch.Kind.PAGE:Branch.Kind.COLLECTION,target);
        return writes==null?null:writes?Sharing.Level.WRITE:Sharing.Level.READ;
    }

    /**
     * Everybody this thing already reaches, by any rule at any level — its own, or one on any collection
     * above it. Who it reaches is one question; which rule does it is another, and the sheet needs both to
     * say what can be undone here and what has to be undone where it was given.
     */
    Map<String,Boolean> reaches(Sharing.Scope scope,String target) {
        List<Sharing.Rule> rules=shares();
        if(scope==Sharing.Scope.LIBRARY||target==null||home(target))return Sharing.audience(rules,new ArrayList<>());
        return Sharing.audience(rules,pathOf(target));
    }

    /**
     * What gives this address what it has, when the rule is not on this thing itself.
     *
     * <p>"Through what holds this" is true and tells you nothing: the question anybody asks next is
     * <i>through what?</i> - and the answer decides where to go to change it. So it is named.
     *
     * @return the thing, named, or empty when the rule is on this thing or there is none
     */
    String grantedBy(Sharing.Scope scope,String target,String address) {
        List<String> path=scope==Sharing.Scope.LIBRARY||home(target)?new ArrayList<>():pathOf(target);
        for(Sharing.Rule rule:shares()) {
            if(!rule.address.equals(address))continue;
            if(alike(rule.scope,scope)&&rule.target.equals(target))continue;
            if(!Sharing.covers(rule,path))continue;
            if(rule.scope==Sharing.Scope.LIBRARY)return "everything on this phone";
            if(onShelf(rule.scope))return nameOf(rule.target,true)+" collection";
        }
        return "";
    }

    List<Sharing.Rule> sharesOn(Sharing.Scope scope,String target) {
        List<Sharing.Rule> here=new ArrayList<>();
        for(Sharing.Rule rule:shares())if(alike(rule.scope,scope)&&rule.target.equals(target))here.add(rule);
        return here;
    }

    /**
     * Somebody given a thing, at the level the rule says, decided now.
     *
     * <p>It wrote the row without its level for as long as rows have had one: `mine` went in, `level` was
     * left to the table's default, and the default is <i>read</i>. So everybody given something at
     * pairing time was a reader by the column everything reads, whatever the offer had said - which
     * showed nowhere while a reader could write, and would have made every new share read-only the day
     * that stopped.
     */
    void addShare(Sharing.Rule rule) {
        setLevel(rule.scope,rule.target,rule.address,rule.level,null);
    }

    /**
     * Somebody taken off. A decision, written down as one with when it was made, so that it travels in the
     * list and an older copy of the list cannot put them back - the row itself used to go, and the next
     * word they wrote wrote them back in, since whoever sends a thing is written down as having it. And
     * nothing is waited for from them any more.
     */
    void removeShare(Sharing.Rule rule,long when) {
        setLevel(rule.scope,rule.target,rule.address,Sharing.Level.GONE,null,when);
        for(Outbox.Page page:pagesUnder(kindFor(rule.scope),rule.target))
            getWritableDatabase().delete("handed","address=? AND page=?",new String[]{rule.address,page.id});
        getWritableDatabase().delete("handed","address=? AND page=?",new String[]{rule.address,rule.target});
    }

    void removeShare(Sharing.Rule rule){removeShare(rule,System.currentTimeMillis());}

    // ---- backups -----------------------------------------------------------------------------------------

    /**
     * Everything, as one piece of text. Files are listed here by name, kind and size; the bytes travel
     * beside this in the backup itself, which is why a backup is now a zip rather than one file of text.
     */
    String backup() throws JSONException {
        JSONArray a=new JSONArray();
        try(Cursor c=getReadableDatabase().query("notes",null,null,null,null,null,"updated DESC")) {
            while(c.moveToNext()) {
                Note note=read(c,true);
                JSONObject written=note.json();
                // Its icon and its picture, which are in its row and so go where the row goes (decision 30).
                String icon=said(c,"icon",""),image=said(c,"image","");
                if(!icon.isEmpty())written.put("icon",icon);
                if(!image.isEmpty())written.put("image",image);
                JSONArray files=new JSONArray();
                for(Held file:filesOf(Branch.Kind.PAGE,note.id))
                    files.put(new JSONObject().put("id",file.id).put("name",file.name).put("kind",file.kind)
                        .put("bytes",file.bytes).put("added",file.added));
                if(files.length()>0)written.put("files",files);
                a.put(written);
            }
        }
        // The shelves travel with what is on them. A backup of notes alone restores onto a phone that has
        // nowhere to put them: the collections they name would not exist, and the notes would be nowhere. Every
        // collection at every depth, each naming what it is in, with the files kept with it.
        JSONArray things=new JSONArray();
        try(Cursor c=getReadableDatabase().query("things",null,null,null,null,null,ORDINAL)) {
            while(c.moveToNext()) {
                JSONObject written=new JSONObject()
                    .put("id",c.getString(c.getColumnIndexOrThrow("id")))
                    .put("name",c.getString(c.getColumnIndexOrThrow("name")))
                    .put("parent",c.getString(c.getColumnIndexOrThrow("parent")))
                    .put("updated",c.getLong(c.getColumnIndexOrThrow("updated")))
                    .put("place",c.getLong(c.getColumnIndexOrThrow("ordinal")))
                    .put("colour",c.getInt(c.getColumnIndexOrThrow("tint")))
                    .put("archived",c.getInt(c.getColumnIndexOrThrow("archived"))==1)
                    .put("deleted",c.getInt(c.getColumnIndexOrThrow("binned"))==1);
                String icon=said(c,"icon",""),image=said(c,"image","");
                if(!icon.isEmpty())written.put("icon",icon);
                if(!image.isEmpty())written.put("image",image);
                things.put(written);
            }
        }
        for(int at=0;at<things.length();at++) {
            JSONObject one=things.getJSONObject(at);
            JSONArray files=new JSONArray();
            for(Held file:filesOf(Branch.Kind.COLLECTION,one.getString("id")))
                files.put(new JSONObject().put("id",file.id).put("name",file.name).put("kind",file.kind)
                    .put("bytes",file.bytes).put("added",file.added));
            if(files.length()>0)one.put("files",files);
        }
        // And the files kept on Home - what came from other devices - which Home, having no row of its own, carries here.
        JSONArray home=new JSONArray();
        for(Held file:filesOf(Branch.Kind.COLLECTION,Things.HOME))
            home.put(new JSONObject().put("id",file.id).put("name",file.name).put("kind",file.kind)
                .put("bytes",file.bytes).put("added",file.added));
        return new JSONObject().put("app","mininotes.v1").put("version",1)
            .put("things",things).put("home",home).put("notes",a).toString(2);
    }

    /** Every file the backup should carry, so the writer knows what to put beside the text. */
    List<Held> everyFile() {
        List<Held> all=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("files",FILE_ROW,null,null,null,null,"added ASC")) {
            while(c.moveToNext())all.add(held(c));
        }
        return all;
    }

    /**
     * A backup read back in, either way a person might mean it.
     *
     * <p><b>Adding</b> keeps what is here and brings the backup's notes in beside it, as copies under ids of
     * their own — the same backup can be added twice and the first copy keeps its own files. Shelves are
     * reused where they already exist and made where they do not, so notes never land nowhere.
     *
     * <p><b>Replacing</b> is a restore: everything here goes and the pad becomes what the backup was, ids
     * included, so the sharing rules that pointed at those things still point at them. It happens in one
     * transaction — a restore that failed half way would be worse than the one it replaced.
     */
    int importBackup(String input,boolean replacing) throws JSONException {
        JSONObject root=new JSONObject(input);
        if(!root.getString("app").equals("mininotes.v1")||root.getInt("version")!=1)throw new JSONException("Unsupported backup");
        JSONArray a=root.getJSONArray("notes");if(a.length()>1000)throw new JSONException("Maximum 1,000 notes per import");
        // Validate every document before moving any attachment from the import landing area.
        Set<String> documentFiles=new HashSet<>();
        for(int i=0;i<a.length();i++){
            JSONObject n=a.getJSONObject(i);String body=n.getString("body");RichDocument document=RichDocument.read(body);
            if(RichDocument.marked(body)&&document==null)throw new JSONException("Invalid document backup");
            if(document==null)continue;
            Set<String> listed=new HashSet<>();JSONArray fs=n.optJSONArray("files");
            for(int f=0;fs!=null&&f<fs.length();f++)listed.add(fs.getJSONObject(f).optString("id"));
            for(String id:document.heads.values())if(!listed.contains(id)||!landingFor(id).isFile()||!documentFiles.add(id))
                throw new JSONException("Document backup is missing an editable file or reuses one across documents");
        }
        // Collections at every depth, from a backup written since they nested; else a 0.1 backup's collections and
        // books, each book under its collection.
        JSONArray things=root.optJSONArray("things");
        JSONArray shelves=things!=null?null:root.optJSONArray("collections"), volumes=things!=null?null:root.optJSONArray("books");
        List<Note> incoming=new ArrayList<>();
        List<Held> arriving=new ArrayList<>();
        // Each note's icon and picture, written once the note is: {id, icon, picture as kept}.
        List<String[]> looks=new ArrayList<>();
        for(int i=0;i<a.length();i++) {JSONObject o=a.getJSONObject(i);Note n=new Note();
            // A restore is the pad as it was, ids included; an addition is a copy, which needs its own.
            if(replacing&&!o.optString("id").isEmpty())n.id=o.getString("id");
            n.title=o.getString("title");n.body=o.getString("body");n.notebook=o.getString("tag");
            // A backup written before books existed lands in the first book rather than nowhere.
            n.book=o.optString("book",SchemaMigrations.FIRST_BOOK);
            n.pinned=o.getBoolean("pinned");n.deleted=o.getBoolean("deleted");n.updated=o.getLong("updated");
            // A backup written before pages could be dragged keeps its old order: newest first.
            n.place=o.optLong("place",-n.updated);
            n.archived=o.optBoolean("archived",false);
            n.colour=o.optInt("colour",Tint.NONE);
            // The size it was read at belongs to the pad it was read on: a restore is that pad, an addition is not.
            if(replacing)n.rung=Reading.stored(o.optLong("rung",Reading.NONE));
            n.revision=Math.max(0,o.optLong("revision",0));
            if(n.title.length()>160||n.body.length()>24000||n.notebook.length()>40||n.updated<0)throw new JSONException("Invalid note limits");
            incoming.add(n);
            String icon=iconFrom(o.optString("icon","")),image=o.optString("image","");
            if(picture(image)==null)image="";
            if(!icon.isEmpty()||!image.isEmpty())looks.add(new String[]{n.id,icon,image});
            // A file is only a file of the note if its bytes came with the backup: a row pointing at
            // nothing would be a name you can tap and never open.
            RichDocument document=RichDocument.read(n.body);
            if(RichDocument.marked(n.body)&&document==null)throw new JSONException("Invalid document backup");
            if(document!=null)for(String id:document.heads.values())if(!landingFor(id).isFile())throw new JSONException("Document backup is missing its editable file");
            Map<String,String> mapped=files(o.optJSONArray("files"),n.id,Branch.Kind.PAGE,n.updated,replacing,arriving);
            if(document!=null)try{n.body=document.remap(mapped).text();}catch(IllegalArgumentException missing){throw new JSONException(missing.getMessage());}
        }
        // And the files kept with a collection, which stay with it: a collection is not copied by an addition.
        for(int i=0;things!=null&&i<things.length();i++) {
            JSONObject o=things.getJSONObject(i);
            String id=o.optString("id");
            if(id.isEmpty()||!replacing&&there("things",id))continue;
            files(o.optJSONArray("files"),id,Branch.Kind.COLLECTION,o.optLong("updated",System.currentTimeMillis()),replacing,arriving);
        }
        // The files on Home, as copies where the backup is added, as they were where it replaces what is here.
        JSONArray home=root.optJSONArray("home");
        files(home,Things.HOME,Branch.Kind.COLLECTION,System.currentTimeMillis(),replacing,arriving);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            if(replacing) {
                // A backup from before Home carried no files, and never said anything about the ones that came: those
                // are left as they are, as they were by every restore before, rather than lost to a backup that never held them.
                if(home==null)db.delete("files","NOT (note=? AND "+heldIs(Branch.Kind.COLLECTION)+")",new String[]{Things.HOME});
                else db.delete("files",null,null);
                db.delete("notes",null,null);
                // A backup carries no writers, so its notes are nobody's: none of what was here is drawn over them.
                db.delete("writers",null,null);
                // Collections and books are left as the move to things left them, unread: only things are what is here.
                db.delete("things",null,null);
            }
            for(int i=0;things!=null&&i<things.length();i++)shelf(db,things.getJSONObject(i),things.getJSONObject(i).optString("parent",Things.HOME),replacing);
            for(int i=0;shelves!=null&&i<shelves.length();i++)shelf(db,shelves.getJSONObject(i),Things.HOME,replacing);
            for(int i=0;volumes!=null&&i<volumes.length();i++)
                shelf(db,volumes.getJSONObject(i),volumes.getJSONObject(i).optString("collection",SchemaMigrations.FIRST_COLLECTION),replacing);
            // A backup from before the shelves travelled, restored onto a pad with none, still needs one: the first
            // collection, and the first one inside it, where a note that names no collection is.
            if(replacing&&(things==null||things.length()==0)&&(shelves==null||shelves.length()==0)) {
                long now=System.currentTimeMillis();
                for(String[] first:new String[][]{{SchemaMigrations.FIRST_COLLECTION,Things.HOME,"My notes"},
                        {SchemaMigrations.FIRST_BOOK,SchemaMigrations.FIRST_COLLECTION,"Notes"}}) {
                    ContentValues v=new ContentValues();
                    v.put("id",first[0]);v.put("parent",first[1]);v.put("kind","collection");v.put("name",first[2]);
                    v.put("made",now);v.put("updated",now);
                    db.insertWithOnConflict("things",null,v,SQLiteDatabase.CONFLICT_IGNORE);
                }
            }
            for(Note n:incoming)save(db,n);
            for(String[] look:looks) {
                ContentValues v=new ContentValues();v.put("icon",look[1]);v.put("image",look[2]);
                db.update("notes",v,"id=?",new String[]{look[0]});
            }
            for(Held file:arriving)keep(db,file);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        sweep();
        return incoming.size();
    }

    /**
     * The files a backup lists for one thing, kept only where their bytes came with it: a row pointing at nothing
     * would be a name you can tap and never open. An addition gives each a new id, as it does the notes.
     */
    private Map<String,String> files(JSONArray kept,String with,Branch.Kind kind,long updated,boolean replacing,List<Held> arriving) throws JSONException {
        Map<String,String> mapped=new HashMap<>();
        for(int f=0;kept!=null&&f<kept.length();f++) {
            JSONObject file=kept.getJSONObject(f);
            String came=Attachment.idOf(Attachment.entry(file.optString("id")));
            if(came==null||!landingFor(came).isFile())continue;
            String id=replacing?came:UUID.randomUUID().toString();
            Held own=new Held(id,with,Attachment.named(file.optString("name")),
                Attachment.kind(file.optString("kind")),landingFor(came).length(),file.optLong("added",updated),kind);
            if(landingFor(came).renameTo(fileFor(own.id))){arriving.add(own);mapped.put(came,own.id);}
        }
        return mapped;
    }

    /**
     * One collection from a backup, wherever it sat. Replacing writes it as it was; adding leaves a collection that
     * is already here alone — the notes are the copies, not the shelves they sit on.
     */
    private void shelf(SQLiteDatabase db,JSONObject said,String parent,boolean replacing) throws JSONException {
        String id=said.optString("id");
        if(id.isEmpty())return;
        if(!replacing) {
            try(Cursor c=db.query("things",new String[]{"id"},"id=?",new String[]{id},null,null,null,"1")) {
                if(c.moveToFirst())return;
            }
        }
        String name=said.optString("name","Untitled");
        if(name.length()>80)name=name.substring(0,80);
        long updated=Math.max(0,said.optLong("updated",System.currentTimeMillis()));
        ContentValues v=new ContentValues();
        v.put("id",id);v.put("parent",home(parent)?Things.HOME:parent);v.put("kind","collection");v.put("name",name);
        v.put("made",updated);v.put("updated",updated);
        v.put("ordinal",said.optLong("place",-updated));
        v.put("tint",said.optInt("colour",Tint.NONE));
        v.put("archived",said.optBoolean("archived",false)?1:0);
        v.put("binned",said.optBoolean("deleted",false)?1:0);
        // Its icon and its picture, where the backup carries them: a picture that is not one is left out.
        String image=said.optString("image","");
        v.put("icon",iconFrom(said.optString("icon","")));v.put("image",picture(image)==null?"":image);
        if(db.insertWithOnConflict("things",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)
            throw new IllegalStateException("Could not restore a collection");
    }
}
