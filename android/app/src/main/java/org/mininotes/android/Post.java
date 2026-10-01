// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import android.content.Context;
import com.eurobuddha.maxima.core.MaximaNode;
import com.eurobuddha.maxima.core.MaximaSender;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Notes actually going somewhere, and notes actually arriving.
 *
 * <p>Two layers, and they are not the same thing. Maxima carries the bytes and knows nothing of what is in
 * them. Inside that, every note is sealed for one recipient with {@link Envelope} — agreement, a key nobody
 * else can derive, and a signature that says who wrote it. A relay handles the envelope; it does not open
 * it, and could not.
 *
 * <p><b>A mark is cleared by the far phone saying it has the note, and by nothing else.</b> That rule is
 * the whole reason the marks are worth looking at. For a long time it was cleared when the network said it
 * had taken the message, which is a different thing: the network will take a message for a phone that is
 * asleep, switched off, or no longer taking that note. So a note that goes asks to be answered, the phone
 * that gets it answers once it is written down — see {@link Receipt} — and what is never answered is sent
 * again, a minute later and then less and less often, for as long as it takes.
 */
final class Post {
    /** The Maxima application string this app owns. Its own traffic, nobody else's. */
    static final String APPLICATION="mininotes.v1";

    /** How long an answer is given before the same note may be handed to the network again. */
    private static final long JUST_NOW=20_000L;

    /** What one send did, in words a person can be shown. */
    static final class Done {
        final int sent, failed;
        final String why;
        /** Why what did not go did not, by device and thing, to be said with what to do about it: see Unsent. */
        final List<Unsent.Problem> problems;
        Done(int sent,int failed,String why){this(sent,failed,why,new ArrayList<>());}
        Done(int sent,int failed,String why,List<Unsent.Problem> problems){this.sent=sent;this.failed=failed;this.why=why;this.problems=problems;}
    }

    /** What this device is called in a sentence about it. */
    static String here(){return Node.A_PC?"this PC":"this phone";}

    private Post(){}

    /** What one arriving message turned out to be. */
    static final class Landed {
        /** What to tell the reader, or null where there is nothing to say. */
        final String said;
        /** Somebody taking up an offer, which is a question for the reader rather than a note. */
        final Hello.Said accepted;
        /**
         * Which note changed, or empty where none did. The page that is open has to know when it is the one:
         * it is holding the text as it was, and the next word typed would write that back over what arrived.
         */
        final String note;
        /** Somebody said they have something. Nothing to tell the reader; the marks want drawing again. */
        final boolean answered;
        /** Who may do what can have changed, though no word of the note did. A box showing it is drawn again. */
        final boolean people;
        /**
         * Something about files sent or received on their own (see {@link Drop}): the list of them wants drawing
         * again. {@code asking} is a sending somebody who is not one of the owner's devices wants to make, to be
         * put to the person, by its id here; null where there is nothing to ask.
         */
        final boolean files; final String asking;
        Landed(String said,Hello.Said accepted){this(said,accepted,"");}
        Landed(String said,Hello.Said accepted,String note){this(said,accepted,note,false);}
        Landed(String said,Hello.Said accepted,String note,boolean answered){this(said,accepted,note,answered,false);}
        private Landed(String said,Hello.Said accepted,String note,boolean answered,boolean people) {
            this(said,accepted,note,answered,people,false,null);
        }
        private Landed(String said,Hello.Said accepted,String note,boolean answered,boolean people,boolean files,String asking) {
            this(said,accepted,note,answered,people,files,asking,false);
        }
        private Landed(String said,Hello.Said accepted,String note,boolean answered,boolean people,boolean files,String asking,boolean devices) {
            this.said=said;this.accepted=accepted;this.note=note==null?"":note;this.answered=answered;
            this.people=people;this.files=files;this.asking=asking;this.devices=devices;
        }
        /**
         * What your own devices are called, or your name, changed on a card (see {@link Persons}). A People and devices
         * box drawn before it shows the old names until it is drawn again, which is what this asks for.
         */
        final boolean devices;
        static Landed devices(){return new Landed(null,null,"",true,true,false,null,true);}
        /** A note that arrived saying what it said already: nothing to tell, and the marks and the box to ask again. */
        static Landed people(String note){return new Landed(null,null,note,true,true);}
        /** Somebody left. That is worth saying, and whoever is looking at the list is looking at an old one. */
        static Landed left(String said,String note){return new Landed(said,null,note,false,true);}
        /** Files sent or received on their own moved on: said, where there is something to say, and asked about. */
        static Landed transfer(String said,String asking){return new Landed(said,null,"",false,false,true,asking);}
    }

    /**
     * Somebody's offer, taken up.
     *
     * <p>Sent back down the same road their code came along: sealed for them, signed by this phone, and
     * carrying what they need to hand the thing over - who this is, where to reach it, and the keys.
     * Blocking: the worker calls it.
     */
    static void accept(Context where,Keys keys,Pairing.Said them,String myName,String myAddress)
            throws Exception {
        say(where,keys,them.address,them.agreement,them.scope,them.target,them.level,myName,myAddress,null);
    }

    /**
     * Paired back: the answer to somebody who scanned this device's code, with nothing offered.
     *
     * <p>A plain code used to be read in one direction only. The phone that scanned it kept the other
     * device; the device that showed it never heard, and dropped everything the phone then sent as coming
     * from a stranger - the phone listed the PC on a shared note, and the note never reached it. So scanning
     * a plain code says hello too, and this is the hello coming back once the owner has said yes.
     */
    static void helloBack(Context where,Keys keys,Hello.Said them,String myName,String myAddress,String contact) throws Exception {
        say(where,keys,them.address,them.agreement,"","",Sharing.Level.READ,myName,myAddress,contact);
    }

    /**
     * The same thing said again.
     *
     * <p>An acceptance is one message to a phone that may be asleep, in a drawer, or being updated, and a
     * message to a node nobody is listening on is gone. So it is kept and repeated rather than sent once
     * and hoped over: otherwise accepting something works when the two phones happen to be awake together
     * and does nothing at all when they are not, with no sign either way.
     */
    static void sayAgain(Context where,NoteStore store,Keys keys,NoteStore.Accepting again,
                         String myName,String myAddress) throws Exception {
        NoteStore.Contact them=store.address(again.address);
        if(them==null||them.agreement.length==0)return;
        say(where,keys,again.address,them.agreement,again.scope,again.target,again.level,myName,myAddress,them.contact);
    }

    private static void say(Context where,Keys keys,String address,byte[] agreement,String scope,
                            String target,boolean writes,String myName,String myAddress,String contact) throws Exception {
        say(where,keys,address,agreement,scope,target,writes?Sharing.Level.WRITE:Sharing.Level.READ,myName,myAddress,contact);
    }

    private static void say(Context where,Keys keys,String address,byte[] agreement,String scope,
                            String target,Sharing.Level level,String myName,String myAddress,String contact) throws Exception {
        MaximaNode node=Node.node(where);
        if(node==null)throw new IllegalStateException("The node is not running.");
        if(myAddress==null||myAddress.trim().isEmpty())
            throw new IllegalStateException("This phone has no address to be reached at yet.");
        Hello.Said hello=new Hello.Said(myName,myAddress.trim(),
            Point.shorten(keys.agreement().getPublic()),Point.shorten(keys.signing().getPublic()),
            scope,target,level.writes()).claiming(level);
        // Accepting an offer of everything is connecting another device of one's own, and only then does the hello
        // say whose this device is and what it is called among them: nobody else is told either. See Persons.
        if(Sharing.Scope.LIBRARY.name().equals(scope))hello=hello.owner(me(where));
        byte[] plain=Hello.wrap(hello);
        byte[] sealed=Envelope.seal(new byte[16],0,System.currentTimeMillis(),plain,
            keys.signing(),Keys.publicKey(agreement));
        // To the device, where the network knows it: an address is a snapshot, and a PC restarted since
        // its code was scanned is somewhere else. The address on the code only when nothing better is known.
        com.eurobuddha.maxima.core.contacts.Contact reach=Node.known(where,contact);
        MaximaSender.Result said=reach!=null?Direct.send(node,reach,APPLICATION,sealed,null,Node.helpers()):node.sendRaw(routable(node,address),APPLICATION,sealed);
        if(said==null||!said.isOk())throw new IllegalStateException("They could not be reached just now.");
    }

    /**
     * Everything one thing owes, sent. Blocking: the worker calls it.
     *
     * <p>Each note goes once per address that is owed it. A note that reaches nobody is not an error — it
     * is a note shared with nobody, which is most of them.
     */
    static Done send(Context where,NoteStore store,Keys keys,NoteStore.Branch.Kind kind,String id)
            throws Exception {
        return send(where,store,keys,kind,id,null);
    }

    /**
     * @param only one address to send to and nobody else, or null for everybody who is owed. Given when
     *             that address has just asked: it is plainly there, so it is sent what it is owed at once,
     *             whenever it was last tried.
     */
    static Done send(Context where,NoteStore store,Keys keys,NoteStore.Branch.Kind kind,String id,String only)
            throws Exception {
        return send(where,store,keys,kind,id,only,false);
    }

    /**
     * @param whole for one note: send it to everybody who has it whether or not they are thought to be
     *              owed it. What somebody pressing Sync on a note means is "make these the same", and two
     *              phones can each believe the other is up to date while holding different words.
     */
    static Done send(Context where,NoteStore store,Keys keys,NoteStore.Branch.Kind kind,String id,String only,
                     boolean whole) throws Exception {
        Done notes=notes(where,store,keys,kind,id,only,whole);
        if(kind==NoteStore.Branch.Kind.PAGE)return notes;
        // And the collections in it that go on their own: those with no note in them to carry them, and those with a look
        // or files of their own to say (see Carton).
        Done alone=cartons(where,store,keys,kind,id,only,false);
        if(alone.sent==0&&alone.failed==0)return notes;
        List<Unsent.Problem> both=new ArrayList<>(notes.problems);both.addAll(alone.problems);
        return new Done(notes.sent+alone.sent,notes.failed+alone.failed,notes.why.isEmpty()?alone.why:notes.why,both);
    }

    /**
     * Whether a note waits for the device it would go to to know about trees: that device knows three levels and no
     * more, and the note does not sit where a 0.1 note can - two collections down (see {@link Things#fitsThreeLevels}).
     *
     * @param above the collections above the note, from Home down
     */
    static boolean waitsForTrees(boolean trees,List<String> above){return !trees&&!Things.fitsThreeLevels(above,true);}

    /**
     * The level a list is said to be the list of, to one device: as the rows here say it to a device that knows about
     * trees, and by how deep the thing sits to one that does not (see {@link Things#oldScope}) - a collection on Home
     * a collection, one inside that a book, a note a note. Null where nothing is shared, or where the thing sits deeper
     * than that device has a word for.
     *
     * @param heldAbove the collections above whatever the list is of, from Home down
     */
    static Sharing.Scope scopeSaid(boolean trees,Sharing.Scope held,List<String> heldAbove) {
        if(held==null||trees||held==Sharing.Scope.LIBRARY)return held;
        return Things.oldScope(heldAbove,held==Sharing.Scope.PAGE);
    }

    private static Done notes(Context where,NoteStore store,Keys keys,NoteStore.Branch.Kind kind,String id,String only,
                              boolean whole) throws Exception {
        List<Outbox.Wait> owed=store.owed(kind,id);
        if(whole&&kind==NoteStore.Branch.Kind.PAGE) {
            NoteStore.Note all=store.get(id);
            owed=new ArrayList<>();
            // Not from a reader: what it would send, everybody would refuse.
            if(all!=null&&!store.onlyReads(id))for(String address:store.everybodyIn(kind,id))
                owed.add(new Outbox.Wait(id,address,all.revision,false));
        }
        if(only!=null) {
            List<Outbox.Wait> theirs=new ArrayList<>();
            for(Outbox.Wait wait:owed)if(only.equals(wait.address))theirs.add(wait);
            owed=theirs;
        }
        if(owed.isEmpty())return new Done(0,0,"");
        // Named in whatever is said about what did not go: the thing that was asked to go, not each note in it.
        String thing=store.thingName(kind,id);
        List<Unsent.Problem> problems=new ArrayList<>();
        MaximaNode node=Node.node(where);
        if(node==null) {
            problems.add(new Unsent.Problem(Unsent.Why.NOT_CONNECTED,"",thing,"",""));
            return new Done(0,owed.size(),Unsent.said(problems.get(0),here()),problems);
        }

        // Who each address is, and the keys to seal for it. An address with no keys has never been paired,
        // and a note sealed for nobody is a note sent nowhere: those are counted as failed, not skipped.
        java.util.Map<String,NoteStore.Contact> known=new java.util.HashMap<>();
        for(NoteStore.Contact contact:store.addresses())known.put(contact.address,contact);
        // Linked through a list and not answered yet: nothing goes until they do, since a build from before could
        // not open it and would never say so. See Linking.
        java.util.Set<String> linking=store.linkingNow();

        KeyPair mine=keys.signing();
        // What went a moment ago and has not had time to be answered. Two things can ask for the same note
        // within a breath of each other - opening the pad sends what is owed, and so does the writing
        // having just stopped - and the second would otherwise send it again before the first could
        // possibly have been answered.
        java.util.Map<String,Outbox.Handed> lately=new java.util.HashMap<>();
        for(Outbox.Handed one:store.handed())lately.put(Outbox.mark(one.address,one.page),one);
        long clock=System.currentTimeMillis();
        int sent=0, failed=0; String why="";
        for(Outbox.Wait wait:owed) {
            NoteStore.Contact them=known.get(wait.address);
            if(them==null||them.agreement.length==0) {
                failed++;
                // Who, by name: a device known here but never given its keys, or one only named in the list of people
                // for a thing - a list that came from somebody else - and never paired with this device at all.
                Unsent.Problem p=them==null
                    ?new Unsent.Problem(Unsent.Why.ONLY_LISTED,store.nameFor(wait.address),thing,store.listedIn(wait.address),"",wait.address)
                    :new Unsent.Problem(Unsent.Why.NOT_PAIRED,store.nameFor(wait.address),thing,"","");
                problems.add(p);
                if(why.isEmpty())why=Unsent.said(p,here());
                continue;
            }
            if(linking.contains(them.address)) {
                failed++;
                Unsent.Problem p=new Unsent.Problem(Unsent.Why.LINKING,store.nameFor(wait.address),thing,store.listedIn(wait.address),"",wait.address);
                problems.add(p);
                if(why.isEmpty())why=Unsent.said(p,here());
                continue;
            }
            NoteStore.Note note=store.get(wait.page);
            if(note==null){failed++;continue;}
            // Where the note sits, from Home down; and whether the device it goes to can hold it there. A build from
            // before trees knows three levels: what does not fit them waits, saying so, until it says it knows more.
            List<String> above=store.above(note.id);
            boolean trees=knowsTrees(where,them);
            if(waitsForTrees(trees,above)) {
                failed++;
                Unsent.Problem p=new Unsent.Problem(Unsent.Why.NEEDS_UPDATE,store.nameFor(wait.address),thing,"","");
                problems.add(p);
                if(why.isEmpty())why=Unsent.said(p,here());
                continue;
            }
            Outbox.Handed before=lately.get(Outbox.mark(wait.address,wait.page));
            if(only==null&&!whole&&before!=null&&before.revision==note.revision&&clock>=before.at
                &&clock-before.at<JUST_NOW)continue;
            try {
                PublicKey theirs=Keys.publicKey(them.agreement);
                // The shelf goes with the note. A note filed into whatever collection happened to be first on
                // the other phone has lost most of what it meant; and because each collection travels by its id
                // rather than by its name, the second note out of it lands beside the first instead of
                // building another that looks the same. The whole path, for a build that knows trees; and the
                // nearest two collections in the two old fields, for one that does not (decision 15).
                String[] two=Things.nearestTwo(above);
                String collection=two[0], book=two[1];
                List<String> path=new ArrayList<>(above);path.add(note.id);
                // And who else has it. A shared thing held only by whoever began it is a broadcast; a
                // thing every holder knows the holders of is something people can pass on between them.
                Sharing.Rule held=store.sharedAt(path);
                String what=held==null?note.id:held.target;
                int at=above.indexOf(what);
                Sharing.Scope level=scopeSaid(trees,held==null?null:held.scope,at<0?above:above.subList(0,at));
                // And the files the note keeps, each saying where its pieces are once it has gone up: see
                // Enclosure. Made to fit the envelope, which a longer note leaves less room in. And its icon and its
                // picture, after its path, to a build that knows about trees: a build from before could not show them.
                byte[] text=Parcel.wrap(new Parcel.Sent(collection,store.nameOf(collection,true),
                    book,store.nameOf(book,false),note.title,note.body,
                    store.mayWrite(them.address,path),
                    store.travelling(level==null?null:held.scope,what),level==null?"":level.name(),what,true,
                    store.agreedAt(note.id,them.address),true,store.enclosed(note.id),System.currentTimeMillis(),
                    store.history(note.id),store.steps(above),
                    trees?store.iconOf(NoteStore.Branch.Kind.PAGE,note.id):"",trees?store.imageOf(NoteStore.Branch.Kind.PAGE,note.id):null),
                    Envelope.MAX_TEXT);
                byte[] sealed=Envelope.seal(sixteen(wait.page),
                    note.revision,System.currentTimeMillis(),text,mine,theirs);
                // Handed to the peer, not to an address they used to be at. Straight to their door first -
                // on this network, then at a public address they proved - and if one takes it, no relay
                // is given a copy (see Direct). Otherwise every address the transport knows for them, then
                // their directory is asked where they went - which is the whole reason a phone that moved
                // is still reachable. Only a peer this node has never been introduced to falls back to
                // dialling the address off their code.
                // And where they collect from, when neither their door nor anything else of theirs is in reach:
                // their home keeps it for them (see Home).
                com.eurobuddha.maxima.core.contacts.Contact reach=Node.known(where,them.contact);
                int doors=Direct.WENT.get(),homes=Direct.LEFT.get(),shut=Direct.SHUT_HERE.get();
                MaximaSender.Result said=handTo(where,store,keys,node,them,sealed);
                // Which road it took and what the relay said, and nothing else: no address, no key, no
                // word of the note. A send that fails quietly while somebody is writing has to be findable
                // afterwards by whoever is holding the phone and a cable.
                android.util.Log.i("Mininotes/Post","sent "+(reach!=null?"to a contact":"to an address")
                    +", revision "+note.revision+": "+(said==null?"no answer":said.statusName)
                    +Direct.road(doors,homes,shut,said!=null&&said.isOk()));
                // Not heard from lately, so perhaps not there: a copy goes with whoever can carry it, in
                // case they are back only after this device has gone.
                if(!there(them))leaveWithCarriers(where,store,keys,node,them,Courier.NOTE,sixteen(wait.page),note.revision,sealed);
                if(said!=null&&said.isOk()) {
                    // Handed over, and no more than that. The mark means somebody has it, and the only one
                    // who can say so is them: it stays until their answer comes back.
                    store.handedOver(them.address,wait.page,note.revision);
                    // Told where its files are, for the files that said so: whether they then say they have
                    // them decides whether the list goes to them again.
                    Parcel.Sent went=Parcel.open(text);
                    if(went!=null&&went.files!=null) {
                        List<String> named=new ArrayList<>();
                        for(Enclosure.Listed one:went.files)if(one.fetchable())named.add(one.id);
                        if(!named.isEmpty())store.told(named,them.address);
                    }
                    // What went is written down as it went. It is what the next thing they write will be
                    // weighed against, and a text that was only ever on the wire cannot be weighed against.
                    store.keepVersion(wait.page,"");
                    sent++;
                } else {
                    failed++;
                    Unsent.Problem p=new Unsent.Problem(Unsent.Why.NOT_REACHED,them.name,thing,"","");
                    problems.add(p);
                    if(why.isEmpty())why=Unsent.said(p,here());
                }
            } catch(Exception e) {
                failed++;
                Unsent.Problem p=new Unsent.Problem(Unsent.of(e.getMessage()),them.name,thing,"",e.getMessage());
                problems.add(p);
                if(why.isEmpty())why=Unsent.said(p,here());
            }
        }
        return new Done(sent,failed,why,problems);
    }

    /**
     * Who may do what, told to everybody who has the thing. Blocking.
     *
     * <p>The list of who has a thing travels with its notes, and with nothing else. So changing what
     * somebody may do, and writing nothing, told nobody: the change sat on this phone until the next word
     * was typed, which for a note that is finished is never. What is owed goes first - somebody just added
     * is owed all of it - and then one note out of the thing goes whole to everybody, carrying the list.
     */
    static Done changed(Context where,NoteStore store,Keys keys,NoteStore.Branch.Kind kind,String id)
            throws Exception {
        Done done=send(where,store,keys,kind,id);
        boolean shelf=kind==NoteStore.Branch.Kind.COLLECTION||kind==NoteStore.Branch.Kind.BOOK;
        if(kind!=NoteStore.Branch.Kind.PAGE&&!shelf)return done;
        // A note carries one list: that of the nearest thing to it, itself included, that is shared by itself.
        // So the note to send is one that carries this list, and not any note that happens to be inside.
        String carrier=null;
        for(Outbox.Page page:store.pagesUnder(kind,id)) {
            Sharing.Rule on=store.sharedAt(page.path());
            if(on!=null&&on.target.equals(id)){carrier=page.id;break;}
        }
        // A collection with no note in it carries its own list, on its own (see Carton).
        Done carried=carrier!=null?send(where,store,keys,NoteStore.Branch.Kind.PAGE,carrier,null,true)
            :shelf?cartons(where,store,keys,kind,id,null,true):null;
        if(carried==null)return done;
        List<Unsent.Problem> both=new ArrayList<>(done.problems);both.addAll(carried.problems);
        return new Done(done.sent,done.failed+carried.failed,done.why.isEmpty()?carried.why:done.why,both);
    }

    /**
     * The collections owed to somebody on their own, sent: each one reached by a rule that holds no note, which would
     * otherwise carry it, or that has a look or files of its own to say (see {@link NoteStore#cartonsOwed} and
     * {@link Carton}). Sealed under the envelope id {@link Things} gives it and its own revision, and answered as a note
     * is. Only to a device that has said it knows about trees: a build from before would take these bytes for a note
     * written the oldest way. For one that has not, it waits, and is said to wait for that device to be updated
     * ({@link Unsent.Why#NEEDS_UPDATE}); it goes when that device says it knows more. Blocking.
     *
     * @param whole to everybody it reaches, whether or not they are thought to have it - somebody pressed Sync, or who
     *              may do what in it has changed, and a list only travels in one of these
     */
    static Done cartons(Context where,NoteStore store,Keys keys,NoteStore.Branch.Kind kind,String id,String only,boolean whole)
            throws Exception {
        List<Outbox.Wait> owed=store.cartonsOwed(kind,id,whole);
        if(only!=null){List<Outbox.Wait> theirs=new ArrayList<>();for(Outbox.Wait one:owed)if(only.equals(one.address))theirs.add(one);owed=theirs;}
        return cartons(where,store,keys,kind,id,owed,only,whole);
    }

    /**
     * One collection sent on its own to one device now, whatever it is thought to have: its files have gone up, or it
     * has not said it has them and is told again (see {@link NoteStore#toTell}). Blocking.
     */
    static Done cartonTo(Context where,NoteStore store,Keys keys,String collection,String address) throws Exception {
        List<Outbox.Wait> one=new ArrayList<>();
        one.add(new Outbox.Wait(collection,address,store.revisionOf(collection),false));
        return cartons(where,store,keys,NoteStore.Branch.Kind.COLLECTION,collection,one,address,true);
    }

    /** One collection sent on its own to everybody its files go to, now: see {@link NoteStore#goesWith}. Blocking. */
    static Done cartonToAll(Context where,NoteStore store,Keys keys,String collection) throws Exception {
        List<Outbox.Wait> all=new ArrayList<>();long revision=store.revisionOf(collection);
        for(String address:store.goesWith(collection))all.add(new Outbox.Wait(collection,address,revision,false));
        return cartons(where,store,keys,NoteStore.Branch.Kind.COLLECTION,collection,all,null,true);
    }

    private static Done cartons(Context where,NoteStore store,Keys keys,NoteStore.Branch.Kind kind,String id,List<Outbox.Wait> owed,
                                String only,boolean whole) throws Exception {
        if(owed.isEmpty())return new Done(0,0,"");
        java.util.Map<String,NoteStore.Contact> known=new java.util.HashMap<>();
        for(NoteStore.Contact contact:store.addresses())known.put(contact.address,contact);
        java.util.Set<String> linking=store.linkingNow();
        java.util.Map<String,Outbox.Handed> lately=new java.util.HashMap<>();
        for(Outbox.Handed one:store.handed())lately.put(Outbox.mark(one.address,one.page),one);
        long clock=System.currentTimeMillis();
        List<Unsent.Problem> problems=new ArrayList<>();
        List<Outbox.Wait> going=new ArrayList<>();
        int sent=0,failed=0;String why="";
        for(Outbox.Wait wait:owed) {
            NoteStore.Contact them=known.get(wait.address);
            String thing=store.thingName(NoteStore.Branch.Kind.COLLECTION,wait.page);
            // Nobody to seal for, or linked and not answered yet: as for a note, said and counted.
            if(them==null||them.agreement.length==0||linking.contains(them.address)) {
                failed++;
                Unsent.Problem p=them==null
                    ?new Unsent.Problem(Unsent.Why.ONLY_LISTED,store.nameFor(wait.address),thing,store.listedIn(wait.address),"",wait.address)
                    :linking.contains(them.address)?new Unsent.Problem(Unsent.Why.LINKING,store.nameFor(wait.address),thing,store.listedIn(wait.address),"",wait.address)
                    :new Unsent.Problem(Unsent.Why.NOT_PAIRED,store.nameFor(wait.address),thing,"","");
                problems.add(p);
                if(why.isEmpty())why=Unsent.said(p,here());
                continue;
            }
            // A build from before trees has nothing to show a collection on its own as - nor its icon, its picture or its
            // files: it is not sent one, and what waits for it waits for it to be updated, saying so.
            if(!knowsTrees(where,them)) {
                failed++;
                Unsent.Problem p=new Unsent.Problem(Unsent.Why.NEEDS_UPDATE,store.nameFor(wait.address),thing,"","");
                problems.add(p);
                if(why.isEmpty())why=Unsent.said(p,here());
                continue;
            }
            // Handed over a moment ago, or lately and not due again: its answer has had no time to come.
            Outbox.Handed before=lately.get(Outbox.mark(wait.address,wait.page));
            if(only==null&&!whole&&before!=null&&before.revision==wait.revision&&clock>=before.at
                &&clock-before.at<Math.max(JUST_NOW,Outbox.againAfter(before.tries)))continue;
            going.add(wait);
        }
        // The node is asked for only where something is to go: a round with nothing for anybody starts nothing.
        MaximaNode node=going.isEmpty()?null:Node.node(where);
        if(!going.isEmpty()&&node==null) {
            problems.add(new Unsent.Problem(Unsent.Why.NOT_CONNECTED,"",store.thingName(kind,id),"",""));
            return new Done(0,failed+going.size(),why.isEmpty()?Unsent.said(problems.get(problems.size()-1),here()):why,problems);
        }
        for(Outbox.Wait wait:going) {
            NoteStore.Contact them=known.get(wait.address);
            String thing=store.thingName(NoteStore.Branch.Kind.COLLECTION,wait.page);
            try {
                // Made to fit the envelope, as a note's parcel is: a long list of files stops saying where each is first.
                byte[] text=Carton.wrap(store.carton(wait.page,them.address,true),Envelope.MAX_TEXT);
                byte[] sealed=Envelope.seal(Things.envelopeId(wait.page),wait.revision,System.currentTimeMillis(),text,
                    keys.signing(),Keys.publicKey(them.agreement));
                MaximaSender.Result said=handTo(where,store,keys,node,them,sealed);
                // What it was and how it went, and nothing else: no name, no address.
                android.util.Log.i("Mininotes/Post","sent a collection on its own, revision "+wait.revision+": "
                    +(said==null?"no answer":said.statusName));
                if(said!=null&&said.isOk()) {
                    store.handedOver(them.address,wait.page,wait.revision);sent++;
                    // Told where its files are, for the files that said so, as for a note's.
                    Carton.Sent went=Carton.open(text);
                    if(went!=null&&went.files!=null) {
                        List<String> named=new ArrayList<>();
                        for(Enclosure.Listed one:went.files)if(one.fetchable())named.add(one.id);
                        if(!named.isEmpty())store.told(named,them.address);
                    }
                }
                else {
                    failed++;
                    Unsent.Problem p=new Unsent.Problem(Unsent.Why.NOT_REACHED,them.name,thing,"","");
                    problems.add(p);
                    if(why.isEmpty())why=Unsent.said(p,here());
                }
            } catch(Exception e) {
                failed++;
                Unsent.Problem p=new Unsent.Problem(Unsent.of(e.getMessage()),them.name,thing,"",e.getMessage());
                problems.add(p);
                if(why.isEmpty())why=Unsent.said(p,here());
            }
        }
        return new Done(sent,failed,why,problems);
    }

    /** One at a time, off the thread the node brought the note on: an answer is a network call too. */
    private static final java.util.concurrent.ExecutorService ANSWERS=
        java.util.concurrent.Executors.newSingleThreadExecutor(work->{
            Thread one=new Thread(work,"mininotes-answers");one.setDaemon(true);return one;});

    /**
     * "I have it", said to whoever sent it.
     *
     * <p>Nothing is done about one that does not get through. They will send the note again when no
     * answer comes, and be answered again; an answer is never itself answered, so nothing goes round.
     */
    private static void answer(final Context where,final NoteStore store,final Keys keys,final NoteStore.Contact them,
                               final byte[] page,final long revision,final boolean took) {
        if(where==null||them.agreement.length==0)return;
        ANSWERS.execute(()->{
            try {
                MaximaNode node=Node.node(where);
                if(node==null)return;
                byte[] sealed=Envelope.seal(page,revision,System.currentTimeMillis(),
                    Receipt.wrap(took?Receipt.TOOK:Receipt.HAVE),keys.signing(),Keys.publicKey(them.agreement));
                int doors=Direct.WENT.get(),homes=Direct.LEFT.get(),shut=Direct.SHUT_HERE.get();
                MaximaSender.Result said;
                try{said=handTo(where,store,keys,node,them,sealed);}
                catch(Exception noWay) {
                    if(Unsent.of(noWay.getMessage())!=Unsent.Why.NOT_IN_REACH)throw noWay;
                    // Only between the owner's devices, with their door not heard here and no home taking it: not a
                    // failure but a wait, as for a note. Kept for the next round that finds a way, or they send the
                    // note again every half minute for as long as they run. And a copy with whoever can carry it,
                    // whose door may well be in reach when theirs is not.
                    int kept=KEPT.keep(them.address,page,revision,took,System.currentTimeMillis());
                    android.util.Log.i("Mininotes/Post","answer kept until they are in reach ("+kept+" kept)");
                    leaveWithCarriers(where,store,keys,node,them,Courier.ANSWER,page,revision,sealed);
                    return;
                }
                if(said!=null&&said.isOk())KEPT.went(them.address,page,revision);
                // By the relays with nothing ever coming back is an answer handed to where they used to be.
                android.util.Log.i("Mininotes/Post","answered them: revision "+revision+": "
                    +(said==null?"no answer":said.statusName)+Direct.road(doors,homes,shut,said!=null&&said.isOk()));
                // A note that was carried here comes from a device that may be gone by now; its answer
                // goes back the same way, or it would send the note again for ever.
                if(!there(them))leaveWithCarriers(where,store,keys,node,them,Courier.ANSWER,page,revision,sealed);
            } catch(Exception notNow) {
                android.util.Log.w("Mininotes/Post","could not answer: "+Unsent.reason(notNow));
            }
        });
    }

    /** Answers with no way to their device yet, while notes go only between the owner's devices. See {@link Outbox.Answers}. */
    private static final Outbox.Answers KEPT=new Outbox.Answers();
    // The words about an amber mark say when an answer to a device is kept here: see Waits.
    static{SyncStatus.kept=KEPT::count;}

    /** The kept answers, tried again. Blocking: the round that sends notes again calls it. */
    private static void sayKept(Context where,NoteStore store,Keys keys) {
        if(KEPT.size()==0)return;
        MaximaNode node=Node.node(where);
        if(node==null)return;
        int[] said=KEPT.sayAll(one->{
            NoteStore.Contact them=store.address(one.address);
            if(them==null||them.agreement.length==0)throw new IllegalStateException("Not paired with them.");
            MaximaSender.Result went=handTo(where,store,keys,node,them,Envelope.seal(one.page,one.revision,System.currentTimeMillis(),
                Receipt.wrap(one.took?Receipt.TOOK:Receipt.HAVE),keys.signing(),Keys.publicKey(them.agreement)));
            if(went==null||!went.isOk())throw new IllegalStateException("They could not be reached just now.");
        },System.currentTimeMillis());
        if(said[0]>0)android.util.Log.i("Mininotes/Post","kept answers said: "+said[0]+" went, "+said[1]+" still kept");
    }

    /** Whether what arrived gives this phone the thing again, later than it left. */
    private static boolean givenAgain(NoteStore store,Parcel.Sent parcel,long leftAt) {
        if(parcel==null||store.mySigningKey.isEmpty())return false;
        for(Parcel.Member one:parcel.members)
            if(store.mySigningKey.equals(one.key)&&one.level>Sharing.Level.GONE.said()&&one.changed>leftAt)return true;
        return false;
    }

    /**
     * "I have left this" - or "you are off this" - to one device, off the thread the node brought their
     * note on. Which of the two is {@code what}, one of the numbers in {@link Receipt}.
     */
    private static void tellOff(final Context where,final NoteStore store,final Keys keys,final NoteStore.Contact them,
                                final byte[] page,final long when,final int what) {
        if(where==null||them.agreement.length==0||what==0)return;
        ANSWERS.execute(()->{
            try{saidOff(where,store,keys,them,page,when,what);}
            catch(Exception notNow) {
                android.util.Log.w("Mininotes/Post","could not say who is off what: "+notNow.getClass().getSimpleName());
            }
        });
    }

    /**
     * @param when when it was decided, carried where a note's revision is: see {@link NoteStore#left} and
     *             {@link NoteStore#takenOff}
     */
    private static boolean saidOff(Context where,NoteStore store,Keys keys,NoteStore.Contact them,byte[] page,long when,
                                   int what) throws Exception {
        MaximaNode node=Node.node(where);
        if(node==null)return false;
        byte[] sealed=Envelope.seal(page,when,System.currentTimeMillis(),
            Receipt.wrap(what),keys.signing(),Keys.publicKey(them.agreement));
        MaximaSender.Result said=handTo(where,store,keys,node,them,sealed);
        android.util.Log.i("Mininotes/Post",(Receipt.removedScope(what)!=null?"told them they are off this: "
            :"told them this phone has left: ")+(said==null?"no answer":said.statusName));
        return said!=null&&said.isOk();
    }

    /**
     * Unfollow: everybody who has the thing is told this phone has left it, and then it is let go. Blocking.
     *
     * <p>Told first, while this phone still knows who they are. Let go whether or not anybody could be
     * told - a phone with no signal can still leave - because whoever did not hear will send the thing
     * again one day, and is told then. See {@link NoteStore#letGo}.
     *
     * @return how many were told
     */
    static int leave(Context where,NoteStore store,Keys keys,NoteStore.Branch.Kind kind,String id) {
        boolean note=kind==NoteStore.Branch.Kind.PAGE;
        if(!note&&kind!=NoteStore.Branch.Kind.COLLECTION&&kind!=NoteStore.Branch.Kind.BOOK)return 0;
        // Said at the level a 0.1 device calls it, by how deep it sits: a collection on Home, one inside that a book, a
        // note a note. Every build hears it that way, and finds the thing from a note out of it on its own shelves.
        Sharing.Scope scope=store.oldScopeOf(note?Sharing.Scope.PAGE:Sharing.Scope.THING,id);
        java.util.Set<String> who=store.everybodyIn(kind,id);
        // Any note out of it names it: the phone that hears finds the shelf from its own shelves.
        byte[] about=null;final long now=System.currentTimeMillis();
        for(Outbox.Page page:store.pagesUnder(kind,id)) {
            try{about=sixteen(page.id);break;}
            catch(IllegalArgumentException older){/* a note from before sharing names nothing */}
        }
        int told=0;
        // A collection deeper than three levels has no word to be left in yet. None are made yet - every notebook moved
        // from 0.1 is three levels deep - and saying it is for a later step; it is let go here all the same.
        if(scope==null)android.util.Log.i("Mininotes/Post","left a collection deeper than three levels: nobody is told yet");
        else if(about!=null)for(NoteStore.Contact them:store.addresses()) {
            if(!who.contains(them.address)||them.agreement.length==0||!doesSpeak(where,them))continue;
            try{if(saidOff(where,store,keys,them,about,now,Receipt.left(scope)))told++;}
            catch(Exception notNow) {
                android.util.Log.w("Mininotes/Post","could not say this phone has left: "+notNow.getClass().getSimpleName());
            }
        }
        store.letGo(kind,id,who,now);
        return told;
    }

    /** Whatever this phone has left lately, said again to whoever was told. Blocking. See {@link NoteStore#leavings}. */
    static void leftAgain(Context where,NoteStore store,Keys keys) {
        for(NoteStore.Leaving one:store.leavings()) {
            try {
                NoteStore.Contact them=null;
                for(NoteStore.Contact known:store.addresses())if(known.address.equals(one.address))them=known;
                if(them==null||them.agreement.length==0||!doesSpeak(where,them))continue;
                // Deeper than three levels, which nothing can say yet: see leave.
                if(Receipt.left(one.scope)==0){android.util.Log.i("Mininotes/Post","left a collection deeper than three levels: not said again");continue;}
                saidOff(where,store,keys,them,sixteen(one.note),one.at,Receipt.left(one.scope));
            } catch(Exception notNow) {
                android.util.Log.w("Mininotes/Post","could not say again that this phone has left: "
                    +notNow.getClass().getSimpleName());
            }
        }
    }

    /**
     * Somebody taken off something, told so: by the same road as leaving, the other way. Blocking.
     *
     * @param when when it was decided here, which their phone then makes its copy its own as of
     * @return whether they could be told now; they are told again at every opening for a week either way
     */
    static boolean removed(Context where,NoteStore store,Keys keys,Sharing.Scope scope,String target,
                           String address,long when) {
        // Said at the level a 0.1 device calls it, by how deep it sits, as leaving is. A collection deeper than three
        // levels has no word yet - none are made yet - and is only written down here: see leave.
        Sharing.Scope said=store.oldScopeOf(scope,target);
        if(said==null&&NoteStore.onShelf(scope))android.util.Log.i("Mininotes/Post","took somebody off a collection deeper than three levels: not told yet");
        if(Receipt.removed(said)==0)return false;
        NoteStore.Contact them=null;
        for(NoteStore.Contact known:store.addresses())if(known.address.equals(address))them=known;
        if(them==null||them.agreement.length==0||!doesSpeak(where,them))return false;
        // Any note out of it names it: the phone that hears finds the shelf from its own shelves.
        byte[] about=null;
        for(Outbox.Page page:store.pagesUnder(NoteStore.kindFor(scope),target)) {
            try{about=sixteen(page.id);break;}
            catch(IllegalArgumentException older){/* a note from before sharing names nothing */}
        }
        if(about==null)return false;
        try{return saidOff(where,store,keys,them,about,when,Receipt.removed(said));}
        catch(Exception notNow) {
            android.util.Log.w("Mininotes/Post","could not say they are off this: "+notNow.getClass().getSimpleName());
            return false;
        }
    }

    /** Whoever this phone has taken off something lately, told again. Blocking. See {@link NoteStore#removals}. */
    static void removedAgain(Context where,NoteStore store,Keys keys) {
        for(NoteStore.Leaving one:store.removals()) {
            try {
                NoteStore.Contact them=null;
                for(NoteStore.Contact known:store.addresses())if(known.address.equals(one.address))them=known;
                if(them==null||them.agreement.length==0||!doesSpeak(where,them))continue;
                // Deeper than three levels, which nothing can say yet: see removed.
                if(Receipt.removed(one.scope)==0)continue;
                saidOff(where,store,keys,them,sixteen(one.note),one.at,Receipt.removed(one.scope));
            } catch(Exception notNow) {
                android.util.Log.w("Mininotes/Post","could not say again that they are off this: "
                    +notNow.getClass().getSimpleName());
            }
        }
    }

    /**
     * Devices known to understand an answer, by the key they sign with.
     *
     * <p>A question is five bytes no note ever asked for, and a build from before answers existed would
     * read them as a note written the oldest way and write them over somebody's writing. An answer is
     * safe because it only goes to a phone that asked for one; a question has to be safe some other way,
     * so it goes only to a phone that has already shown it knows what these bytes are.
     */
    private static void speaks(Context where,NoteStore.Contact them) {
        if(where==null||them==null||them.signing.length==0)return;
        android.content.SharedPreferences kept=where.getSharedPreferences("post",Context.MODE_PRIVATE);
        java.util.Set<String> all=new java.util.HashSet<>(kept.getStringSet("answers",new java.util.HashSet<String>()));
        if(all.add(android.util.Base64.encodeToString(them.signing,android.util.Base64.NO_WRAP)))
            kept.edit().putStringSet("answers",all).apply();
    }

    /** Whether anything signed by them has ever been answered here. */
    static boolean heardFrom(Context where,NoteStore.Contact them){return doesSpeak(where,them);}

    private static boolean doesSpeak(Context where,NoteStore.Contact them) {
        if(where==null||them==null||them.signing.length==0)return false;
        return where.getSharedPreferences("post",Context.MODE_PRIVATE)
            .getStringSet("answers",new java.util.HashSet<String>())
            .contains(android.util.Base64.encodeToString(them.signing,android.util.Base64.NO_WRAP));
    }

    // ---- carrying for devices that are not on at the same time: see Courier ------------------------------------

    /** When each device was last heard from directly, by the fingerprint of its key. For this run only. */
    private static final java.util.Map<String,Long> HEARD=new java.util.concurrent.ConcurrentHashMap<>();

    private static String fingerprint(NoteStore.Contact them) {
        try{return them==null||them.signing.length==0?"":Courier.hex(Envelope.fingerprint(Keys.publicKey(them.signing)));}
        catch(Exception unreadable){return "";}
    }

    /** This device's own fingerprint, as the others know it; empty where it cannot be read. */
    private static String myFingerprint(Keys keys) {
        try{return Courier.hex(Envelope.fingerprint(keys.signing().getPublic()));}catch(Exception unreadable){return "";}
    }

    /** Whether a device was heard from so lately that it is taken to be there. */
    private static boolean there(NoteStore.Contact them) {
        Long at=HEARD.get(fingerprint(them));
        if(at!=null&&System.currentTimeMillis()-at<Courier.THERE)return true;
        // Or collecting from this PC, which is as good as there: what is kept here for it is taken within minutes.
        Home.Host here=Node.host();
        return here!=null&&here.collecting(fingerprint(them),System.currentTimeMillis());
    }

    /** A device whose notes have said its build carries - and so may be left things, and brought them. */
    private static void carries(Context where,NoteStore.Contact them) {
        if(where==null||them==null||them.signing.length==0)return;
        android.content.SharedPreferences kept=where.getSharedPreferences("post",Context.MODE_PRIVATE);
        java.util.Set<String> all=new java.util.HashSet<>(kept.getStringSet("carries",new java.util.HashSet<String>()));
        if(all.add(android.util.Base64.encodeToString(them.signing,android.util.Base64.NO_WRAP)))
            kept.edit().putStringSet("carries",all).apply();
    }

    private static boolean doesCarry(Context where,NoteStore.Contact them) {
        if(where==null||them==null||them.signing.length==0)return false;
        return where.getSharedPreferences("post",Context.MODE_PRIVATE)
            .getStringSet("carries",new java.util.HashSet<String>())
            .contains(android.util.Base64.encodeToString(them.signing,android.util.Base64.NO_WRAP));
    }

    /** Sealed bytes handed to a device, where the network knows it. Whether the network took them. */
    private static boolean hand(Context where,NoteStore store,Keys keys,MaximaNode node,NoteStore.Contact them,byte[] sealed) throws Exception {
        MaximaSender.Result said=handTo(where,store,keys,node,them,sealed);
        return said!=null&&said.isOk();
    }

    /**
     * Sealed bytes handed to a paired device: to the peer where the network knows it - its own doors first,
     * then its home, then the relays (see {@link Direct#send}) - and only where it has never been introduced,
     * to the address off its code.
     */
    private static MaximaSender.Result handTo(Context where,NoteStore store,Keys keys,MaximaNode node,NoteStore.Contact them,
                                              byte[] sealed) throws Exception {
        com.eurobuddha.maxima.core.contacts.Contact reach=Node.known(where,them.contact);
        return reach!=null
            ?Direct.send(node,reach,APPLICATION,sealed,homeFor(where,store,keys,node,them),Node.helpers())
            :node.sendRaw(routable(node,them.address),APPLICATION,sealed);
    }

    /**
     * A copy of something sealed for {@code them}, left with the devices that can carry it: every other
     * paired device whose build has said it carries, those heard from lately first. Nothing is waited for
     * and nothing counted - this is on top of the sending, which goes on as it did.
     */
    private static void leaveWithCarriers(Context where,NoteStore store,Keys keys,MaximaNode node,NoteStore.Contact them,
                                          int sort,byte[] page,long revision,byte[] inner) {
        if(store==null||!Courier.fits(inner))return;
        try {
            byte[] forWhom=Envelope.fingerprint(Keys.publicKey(them.signing));
            List<NoteStore.Contact> carriers=new ArrayList<>();
            for(NoteStore.Contact one:store.addresses())
                if(one.paired()&&sendsTo(store,one)&&!one.address.equals(them.address)&&!java.util.Arrays.equals(one.signing,them.signing)&&doesCarry(where,one))carriers.add(one);
            carriers.sort((a,b)->Boolean.compare(there(b),there(a)));
            int left=0;
            for(NoteStore.Contact carrier:carriers) {
                if(left>=Courier.CARRIERS)break;
                try {
                    byte[] sealed=Envelope.seal(page,revision,System.currentTimeMillis(),Courier.leave(sort,forWhom,inner),
                        keys.signing(),Keys.publicKey(carrier.agreement));
                    if(hand(where,store,keys,node,carrier,sealed))left++;
                } catch(Exception notThisOne){/* the next carrier, or none */}
            }
            if(left>0)android.util.Log.i("Mininotes/Post","left a copy with "+left+" device(s) that can carry it");
        } catch(Exception notNow) {
            android.util.Log.w("Mininotes/Post","could not leave a copy to be carried: "+notNow.getClass().getSimpleName());
        }
    }

    /**
     * Something left here to be carried to somebody else, or brought here by whoever carried it.
     *
     * <p>Left: kept, if it is for a device paired here whose build knows what it will be brought - and
     * brought at once if they are there. Brought: opened as if it had come straight from whoever wrote it,
     * which is who signed it, and then the device that brought it is told it can let go.
     */
    private static Landed carried(Context where,NoteStore store,Keys keys,NoteStore.Contact from,
                                  Envelope.Opened opened,Courier.Said said) {
        if(said.kind==Courier.LEAVE) {
            final String forWhom=Courier.hex(said.forWhom);
            NoteStore.Contact them=null;
            for(NoteStore.Contact one:store.addresses())if(one.paired()&&forWhom.equals(fingerprint(one)))them=one;
            if(them==null||forWhom.equals(fingerprint(from))||!doesCarry(where,them)) {
                // Which of the three, and a note or an answer: a copy left here for a device this one is not paired
                // with is the usual one, and harmless; one "for" this very device would be a fault of the sender's.
                String why=forWhom.equals(fingerprint(from))?"it is for the device that left it"
                    :them!=null?"its build has not said it carries"
                    :forWhom.equals(myFingerprint(keys))?"it is for this device, which a copy never is":"not paired here with the device it is for";
                android.util.Log.i("Mininotes/Post","not carried: for a device this one cannot bring it to ("
                    +(said.sort==Courier.NOTE?"a note":"an answer")+", "+said.inner.length+" bytes; "+why+")");
                return new Landed(null,null);
            }
            boolean kept=store.carry(fingerprint(from),forWhom,Courier.hex(opened.page),said.sort,opened.revision,said.inner);
            android.util.Log.i("Mininotes/Post",kept?"carrying something for another device":"not carried: something newer is held, or there is no room");
            if(kept&&there(them))ANSWERS.execute(()->bring(where,store,keys,forWhom));
            return new Landed(null,null);
        }
        Landed landed=arrived(where,store,keys,said.inner,true);
        // Opened, written down, or found not to be anything: either way there is nothing more to bring.
        final NoteStore.Contact carrier=from;final int collected=Receipt.collected(said.sort);
        final byte[] page=opened.page;final long revision=opened.revision;
        ANSWERS.execute(()->{
            try {
                // Something just arrived, so the node is up; where it is not, nothing is started for this.
                MaximaNode node=Node.running()?Node.node(where):null;
                if(node==null)return;
                hand(where,store,keys,node,carrier,Envelope.seal(page,revision,System.currentTimeMillis(),Receipt.wrap(collected),
                    keys.signing(),Keys.publicKey(carrier.agreement)));
            } catch(Exception notNow){/* it is brought again, and collected again */}
        });
        return landed;
    }

    // ---- the PC as its owner's host: see Home --------------------------------------------------------------

    /**
     * Where a message for {@code them} can be left for them to collect, tried once their own doors have not
     * taken it. On the PC, here - if they are one of its owner's devices and collecting from it. On a phone, at
     * any paired device whose door is in reach and has a home behind it, which takes it only for a device of
     * its owner's that is collecting from it: so the phone need not know which home is whose, and nothing about
     * the owner's home travels to anybody.
     */
    private static Direct.Leave homeFor(Context where,NoteStore store,Keys keys,MaximaNode node,NoteStore.Contact them) {
        if(store==null||keys==null||them==null||them.signing.length==0)return null;
        final String forWhom=fingerprint(them);
        if(forWhom.isEmpty())return null;
        return data->{
            long now=System.currentTimeMillis();
            KeyPair mine=keys.signing();
            Home.Host here=Node.host();
            if(here!=null) {
                boolean kept=here.keepHere(Courier.hex(Envelope.fingerprint(mine.getPublic())),forWhom,data,now)==Home.OK;
                if(kept)android.util.Log.i("Mininotes/Post","kept here for one of your devices to collect");
                return kept;
            }
            if(Node.A_PC)return false;
            byte[] whose=Envelope.fingerprint(Keys.publicKey(them.signing));
            // This device's own first: the owner's PC is the home most likely to be theirs. Whoever else is asked
            // learns what a relay would - who it is for, and how big - and can open none of it.
            List<NoteStore.Contact> homes=new ArrayList<>(store.addresses());
            homes.sort((one,other)->Boolean.compare(other.mine,one.mine));
            for(NoteStore.Contact home:homes) {
                if(!home.paired()||home.contact==null||home.contact.trim().isEmpty()||java.util.Arrays.equals(home.signing,them.signing))continue;
                com.eurobuddha.maxima.core.contacts.Contact reach=Node.known(where,home.contact);
                for(String door:Direct.firstTries(node.lanAddressFor(home.contact),reach==null?null:new ArrayList<>(reach.addresses),home.contact)) {
                    String asking=door+"|"+forWhom;
                    if(Home.resting(door,now)||Home.resting(asking,now))continue;
                    try {
                        int said=Home.deposit(door,home.contact,Keys.publicKey(home.agreement),mine,whose,data);
                        if(said==Home.OK) {
                            android.util.Log.i("Mininotes/Post","left at a home, for them to collect");
                            return true;
                        }
                        // A no is about this device - not one of that home's owner's, or not collecting just now - and
                        // the next device may be; a door with no home behind it, or one not open just now, is about all.
                        Home.rest(said==Home.NO_HOME||said==Home.BUSY?door:asking,now,Home.restFor(said));
                    } catch(java.io.IOException|RuntimeException notThere) {
                        Home.rest(door,now,Home.UNREACHABLE);
                        continue;
                    }
                    break;
                }
            }
            return false;
        };
    }

    /** What the PC's home needs of its notebook: its key, who is paired, and which of them are "My device". */
    private static Home.Notebook notebook(NoteStore store,Keys keys) throws Exception {
        java.util.Set<String> paired=new java.util.HashSet<>(),mine=new java.util.HashSet<>();
        for(NoteStore.Contact one:store.addresses()) {
            String who=one.paired()?fingerprint(one):"";
            if(who.isEmpty())continue;
            paired.add(who);
            if(one.mine)mine.add(who);
        }
        return new Home.Notebook(keys.agreement().getPrivate(),paired,mine,store);
    }

    /**
     * What waits for this phone at its owner's PC, collected: from every device marked "My device" whose door
     * is in reach - on this network, or at the public address it proved - and has a home behind it. Each thing
     * is taken in as if it had just arrived. Blocking; called every round, and a door that is not there or has
     * no home behind it is let be for a while, so a round with nothing to collect costs one small question.
     *
     * @return how many things were collected
     */
    static int collect(Context where,NoteStore store,Keys keys) {
        if(Node.A_PC||!Node.running())return 0;
        MaximaNode node=Node.node(where);
        if(node==null)return 0;
        int got=0;
        long now=System.currentTimeMillis();
        for(NoteStore.Contact home:store.addresses()) {
            if(!home.mine||!home.paired()||home.contact==null||home.contact.trim().isEmpty())continue;
            com.eurobuddha.maxima.core.contacts.Contact reach=Node.known(where,home.contact);
            for(String door:Direct.firstTries(node.lanAddressFor(home.contact),reach==null?null:new ArrayList<>(reach.addresses),home.contact)) {
                if(Home.resting(door,now))continue;
                try {
                    Home.Collected said=Home.collect(door,home.contact,Keys.publicKey(home.agreement),keys.signing(),Node::deliver);
                    got+=said.items;
                    if(said.status==Home.OK){Node.collected(now);break;}
                    Home.rest(door,now,Home.restFor(said.status));
                    break;
                } catch(Exception notThere) {
                    Home.rest(door,now,Home.UNREACHABLE);
                }
            }
        }
        if(got>0)android.util.Log.i("Mininotes/Post","collected "+got+" thing(s) from your PC");
        return got;
    }

    private static final java.util.concurrent.atomic.AtomicBoolean BRINGING=new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * What this device holds for others, brought to them. Blocking.
     *
     * @param only one device's fingerprint - which has just been heard from, so everything held for it goes
     *             now - or null for everything whose turn it is
     */
    static void bring(Context where,NoteStore store,Keys keys,String only) {
        if(!BRINGING.compareAndSet(false,true))return;
        try {
            MaximaNode node=Node.node(where);
            if(node==null)return;
            List<NoteStore.Carried> held=store.carried(only);
            if(held.isEmpty())return;
            java.util.Map<String,NoteStore.Contact> byKey=new java.util.HashMap<>();
            for(NoteStore.Contact one:store.addresses())if(one.paired())byKey.put(fingerprint(one),one);
            long now=System.currentTimeMillis();int brought=0;
            for(NoteStore.Carried one:held) {
                NoteStore.Contact them=byKey.get(one.recipient);
                if(them==null||!doesCarry(where,them))continue;
                // Just heard from: now, unless it went a moment ago. Otherwise, when its turn comes.
                if(only!=null?now-one.tried<JUST_NOW&&now>=one.tried:!Courier.due(one.tried,one.tries,now))continue;
                try {
                    byte[] page=new byte[16];
                    for(int at=0;at<16;at++)page[at]=(byte)Integer.parseInt(one.page.substring(at*2,at*2+2),16);
                    byte[] sealed=Envelope.seal(page,one.revision,now,Courier.bring(one.sort,one.bytes),
                        keys.signing(),Keys.publicKey(them.agreement));
                    hand(where,store,keys,node,them,sealed);
                    store.broughtAgain(one);brought++;
                } catch(Exception notThisOne){/* its turn comes again */}
            }
            if(brought>0)android.util.Log.i("Mininotes/Post","brought "+brought+" thing(s) carried for another device");
        } catch(Exception notNow) {
            android.util.Log.w("Mininotes/Post","could not bring what is carried: "+notNow.getClass().getSimpleName());
        } finally {BRINGING.set(false);}
    }

    /**
     * Everybody who has anything in this thing, asked to send whatever they have for this phone. Blocking.
     *
     * @return how many were asked
     */
    static int ask(Context where,NoteStore store,Keys keys,NoteStore.Branch.Kind kind,String id) {
        int asked=0;
        try {
            MaximaNode node=Node.node(where);
            if(node==null)return 0;
            java.util.Set<String> who=store.everybodyIn(kind,id);
            for(NoteStore.Contact them:store.addresses()) {
                if(!who.contains(them.address)||them.agreement.length==0||!doesSpeak(where,them))continue;
                try {
                    // About this one note where it is a note that is being synced, so they send it whole.
                    byte[] about=new byte[16];
                    if(kind==NoteStore.Branch.Kind.PAGE)try{about=sixteen(id);}catch(IllegalArgumentException old){/* everything */}
                    byte[] sealed=Envelope.seal(about,0,System.currentTimeMillis(),
                        Receipt.wrap(Receipt.ASK),keys.signing(),Keys.publicKey(them.agreement));
                    MaximaSender.Result said=handTo(where,store,keys,node,them,sealed);
                    android.util.Log.i("Mininotes/Post","asked them for what they have: "
                        +(said==null?"no answer":said.statusName));
                    if(said!=null&&said.isOk())asked++;
                } catch(Exception notNow) {
                    android.util.Log.w("Mininotes/Post","could not ask: "+notNow.getClass().getSimpleName());
                }
            }
        } catch(Exception notNow){/* nobody asked; what they owe still comes when its turn does */}
        return asked;
    }

    private static final java.util.concurrent.atomic.AtomicBoolean TRYING=
        new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Whatever was handed over and never answered, sent again if it is time. Blocking, and never two at
     * once: called from the node's own upkeep, every half minute, for as long as the process lives.
     *
     * <p>{@link Outbox#due} decides what and when. Only a revision that already went once is sent again -
     * a note written in since is the business of whatever that note is set to, which may be "when I ask".
     */
    static void again(Context where,NoteStore store,Keys keys) {
        if(!TRYING.compareAndSet(false,true))return;
        try {
            // Whose announcements on the local network are believed: the devices paired now. See Direct.
            Node.pairedWith(Direct.paired(store.addresses()));
            // And what a device heard anew there is owed and could not be given before, given then. See Node.metHere.
            Node.onMetHere(key->sayOpenAgain(where,store,keys,key));
            // The PC keeps what comes for its owner's phones; a phone collects what its PC keeps for it, first,
            // so that what it collects is in before anything is sent again. See Home.
            if(Node.A_PC)Node.hosting(notebook(store,keys));else collect(where,store,keys);
            // Answers that had no way to their device, said now if one has turned up.
            sayKept(where,store,keys);
            // That this device takes files sent on their own, said to whoever has not been told this run.
            tellTakesFiles(where,store,keys);
            // That this build knows about persons, and, to the owner's own devices that do too, the card. See Persons.
            tellPersons(where,store,keys);
            sendCards(where,store,keys);
            // That this build knows about trees, said as persons is. See Things.
            tellTrees(where,store,keys);
            // Whoever a list linked this device with and has not answered, asked again when their turn comes. See Linking.
            linkAgain(where,store,keys);
            List<Outbox.Handed> due=Outbox.due(store.handed(),store.sent(),store.revisions(),
                System.currentTimeMillis());
            java.util.Set<String> pages=new java.util.LinkedHashSet<>();
            for(Outbox.Handed one:due)pages.add(one.page);
            for(String page:pages) {
                Done done=send(where,store,keys,NoteStore.Branch.Kind.PAGE,page);
                android.util.Log.i("Mininotes/Post","not answered, so sent again: "+done.sent+" went, "
                    +done.failed+" did not");
            }
            // And the collections that go on their own: whoever is owed one, and whatever was not answered once its
            // turn to go again has come. Counted in the log only where any went.
            Done alone=cartons(where,store,keys,NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING,null,false);
            if(alone.sent>0)android.util.Log.i("Mininotes/Post","collections on their own: "+alone.sent+" went, "+alone.failed+" did not");
            // And whatever is carried for others, brought again when its turn comes.
            if(!store.carried(null).isEmpty())bring(where,store,keys,null);
            // And files: going up, being fetched, and said again to whoever has not said they have them.
            travelSoon(where,store,keys);
        } catch(Exception notNow) {
            android.util.Log.w("Mininotes/Post","could not try again: "+notNow.getClass().getSimpleName());
        } finally {TRYING.set(false);}
    }

    // ---- the files kept with a shared note: see Enclosure ------------------------------------------------------

    /**
     * Told which note's files have changed here - one fetched, one taken out by somebody else's list, somebody
     * saying they have one - so a page showing them can draw them again. Called off the interface thread.
     */
    static volatile java.util.function.Consumer<String> filesMoved;

    private static void moved(String note) {
        java.util.function.Consumer<String> told=filesMoved;
        if(told!=null&&note!=null)try{told.accept(note);}catch(RuntimeException notNow){/* drawn next time */}
    }

    /**
     * One file at a time, on a thread of its own: sending a file up can take the best part of a minute and
     * fetching one as long, and neither may hold up a note being sent or an answer being said.
     */
    private static final java.util.concurrent.ExecutorService FILES=
        java.util.concurrent.Executors.newSingleThreadExecutor(work->{
            Thread one=new Thread(work,"mininotes-files");one.setDaemon(true);return one;});
    private static final java.util.concurrent.atomic.AtomicBoolean TRAVELLING=new java.util.concurrent.atomic.AtomicBoolean();
    /** Asked for while a round was under way: another follows it, so nothing asked for waits a whole beat. */
    private static final java.util.concurrent.atomic.AtomicBoolean AGAIN=new java.util.concurrent.atomic.AtomicBoolean();

    /** A round of file work soon: now, or straight after the one under way. */
    static void travelSoon(final Context where,final NoteStore store,final Keys keys) {
        if(where==null||store==null||keys==null)return;
        if(!TRAVELLING.compareAndSet(false,true)){AGAIN.set(true);return;}
        try{FILES.execute(()->{
            try{do{AGAIN.set(false);travel(where,store,keys);}while(AGAIN.get());}
            finally{TRAVELLING.set(false);}
        });}
        catch(RuntimeException full){TRAVELLING.set(false);}
    }

    /**
     * A file added to a note or taken out of one here. Whoever has the note is sent its list now, rather than
     * the next time somebody writes in it; what is to go up then goes up, and the list goes again saying where.
     * Blocking: the worker calls it.
     */
    static void filesChanged(Context where,NoteStore store,Keys keys,String note) {
        try {
            // A collection's files go in its carton, to everybody it reaches (see NoteStore.goesWith).
            if(note!=null&&store.keptWith(note)==NoteStore.Branch.Kind.COLLECTION)cartonToAll(where,store,keys,note);
            else if(note!=null&&!store.everybodyIn(NoteStore.Branch.Kind.PAGE,note).isEmpty())
                send(where,store,keys,NoteStore.Branch.Kind.PAGE,note,null,true);
        } catch(Exception notNow) {
            android.util.Log.w("Mininotes/Post","could not send the list of a note's files: "+notNow.getClass().getSimpleName());
        }
        travelSoon(where,store,keys);
    }

    /**
     * One round: pieces of what is no longer kept let go, what is to go up sent up, what is waiting fetched,
     * and the list said again to whoever has not said they have its files. Nothing here throws out: a round
     * that fails is tried again at the next.
     */
    private static void travel(Context where,NoteStore store,Keys keys) {
        try {
            for(java.util.Map.Entry<String,String> gone:store.publishedOrphans().entrySet()) {
                Node.forget(where,gone.getValue());
                store.forgetPublished(gone.getKey());
            }
            long now=System.currentTimeMillis();
            List<NoteStore.Going> up=store.toPublish(now);
            List<NoteStore.Incoming> down=store.toFetch(now);
            java.util.Map<String,java.util.Set<String>> tell=store.toTell(now);
            List<NoteStore.Transfer> sendings=store.transfersDue(now);
            if(up.isEmpty()&&down.isEmpty()&&tell.isEmpty()&&sendings.isEmpty())return;
            // Never the reason a node is started: files go when the node is up for everything else.
            if(!Node.running())return;
            com.eurobuddha.maxima.core.media.MediaService media=Node.media(where);
            if(media==null)return;
            for(NoteStore.Going one:up)goUp(where,store,keys,media,one);
            for(NoteStore.Incoming one:down)fetch(where,store,keys,media,one);
            // Files sent on their own: offered, or fetched. See Drop.
            for(NoteStore.Transfer one:sendings)if(one.out)offer(where,store,keys,media,one);else fetchSending(where,store,keys,media,one);
            for(java.util.Map.Entry<String,java.util.Set<String>> note:tell.entrySet()) {
                // A note's list goes again in the note; a collection's in its carton.
                NoteStore.Branch.Kind kept=store.keptWith(note.getKey());
                for(String address:note.getValue()) {
                    Done done=kept==NoteStore.Branch.Kind.PAGE?send(where,store,keys,NoteStore.Branch.Kind.PAGE,note.getKey(),address,true)
                        :cartonTo(where,store,keys,note.getKey(),address);
                    // Counted as told whether or not it went, so a device that never says it has a file is told
                    // only a few times; a send that went has counted it already.
                    if(done.sent==0) {
                        List<String> ids=new ArrayList<>();
                        for(NoteStore.Held file:store.filesOf(kept,note.getKey()))ids.add(file.id);
                        store.told(ids,address);
                    }
                }
            }
        } catch(Throwable notNow) {
            android.util.Log.w("Mininotes/Post","files: a round failed: "+notNow.getClass().getSimpleName());
        }
    }

    /** One file sent up, and its note's list sent again saying where. */
    private static void goUp(Context where,NoteStore store,Keys keys,com.eurobuddha.maxima.core.media.MediaService media,
                             NoteStore.Going one) {
        com.eurobuddha.maxima.core.media.MediaManifest made=null;
        try {
            byte[] plain=store.bytesOf(one.file);
            made=media.publish(plain,one.file.kind);
            // Up only if a relay has it. A phone behind a router can hand its pieces to nobody directly, so a
            // list saying they are only here would send everybody to a place they cannot reach. A PC's own
            // door is in the list too once it is proved open, and it does not count: a router that restarts
            // closes it, and the pieces have to be somewhere that is still there.
            // Only between the owner's devices there is no relay to have it, and that is the choice: the pieces
            // are kept here, pinned, and the other devices take them from this device's door when they can
            // reach it. Offered, then, not gone: the card says it waits for them until they say they have it.
            boolean helpers=Node.helpers();
            if(helpers&&!Direct.relayed(made.sources,own(where)))throw new IllegalStateException("No relay took it.");
            store.published(one.file.id,made.encode());
            if(!one.manifest.isEmpty())Node.forget(where,one.manifest);
            android.util.Log.i("Mininotes/Post",helpers?"files: one went up, "+made.chunkIds.size()+" piece(s) on "+made.sources.size()+" relay(s)"
                :"files: one is offered from this device's door, "+made.chunkIds.size()+" piece(s)");
            moved(one.file.note);
            // The list that says where it is: a note's in its parcel, a collection's in its carton.
            if(one.file.held==NoteStore.Branch.Kind.PAGE)send(where,store,keys,NoteStore.Branch.Kind.PAGE,one.file.note,null,true);
            else cartonToAll(where,store,keys,one.file.note);
        } catch(Throwable notNow) {
            // What went up halfway is let go: it goes up whole, under a new key, next time.
            if(made!=null&&(!Node.helpers()||!Direct.relayed(made.sources,own(where))))Node.forget(where,made.encode());
            store.publishFailed(one.file.id);
            android.util.Log.w("Mininotes/Post","files: one could not go up: "+notNow.getClass().getSimpleName());
        }
    }

    /** One file fetched and kept, and everybody who has its note told this device has it. */
    private static void fetch(Context where,NoteStore store,Keys keys,com.eurobuddha.maxima.core.media.MediaService media,
                              NoteStore.Incoming one) {
        try {
            com.eurobuddha.maxima.core.media.MediaManifest said=com.eurobuddha.maxima.core.media.MediaManifest.decode(one.manifest);
            // What the list said about it and what the manifest says have to be the same file.
            if(said==null||said.size!=one.bytes||!Enclosure.travels(said.size))throw new IllegalArgumentException("Not a file that can come.");
            said=fromTheirDoor(where,store,said,one.origin,!Node.helpers(),store.holders(one.id));
            byte[] plain=media.fetch(said);
            if(plain.length!=one.bytes)throw new SecurityException("Not the file that was listed.");
            if(!store.fileArrived(one,plain))return;
            android.util.Log.i("Mininotes/Post","files: one arrived and is kept");
            moved(one.note);
            // Everybody who has what it is kept with - a note, or a collection - is told this device has it.
            java.util.Set<String> who=store.everybodyIn(store.keptWith(one.note),one.note);
            for(NoteStore.Contact them:store.addresses()) {
                if(!who.contains(them.address)&&!them.address.equals(one.origin))continue;
                if(them.agreement.length==0||!doesSpeak(where,them))continue;
                try{saidAboutFile(where,store,keys,them,one.id,Receipt.FILE_HERE);}catch(Exception notThisOne){/* they go on showing it as on its way */}
            }
        } catch(Throwable notNow) {
            int tries=store.fetchFailed(one.id);
            android.util.Log.w("Mininotes/Post","files: one could not be fetched ("+tries+" tries): "+notNow.getClass().getSimpleName());
            // Only between the owner's devices, what could not be had was not in reach: the device that has it
            // keeps it, and sending it up again would change nothing, so it is not asked to.
            if(!Enclosure.sayMissing(tries)||!Node.helpers())return;
            // Asked for again from whoever listed it: the relays may have let it go.
            NoteStore.Contact them=store.address(one.origin);
            if(them!=null&&them.agreement.length>0&&doesSpeak(where,them))
                try{saidAboutFile(where,store,keys,them,one.id,Receipt.FILE_MISSING);}catch(Exception notNow2){/* asked again after four more */}
        }
    }

    /** This device's own doors, which hand out only its own pieces. */
    private static List<String> own(Context where) {
        MaximaNode node=Node.node(where);
        return node==null?new ArrayList<>():node.directAddresses();
    }

    /**
     * The pieces asked of the device that listed the file first, where its door can be reached - on this
     * network, or at a public address it proved - before any relay: see {@link Direct}. The door serves only
     * what that device holds, and each piece is checked against its hash as it comes, so asking it costs
     * nothing if it has not got them. Each door is asked once whether it has the first piece, so a door that
     * is not there costs one wait, not one a piece.
     */
    private static com.eurobuddha.maxima.core.media.MediaManifest fromTheirDoor(Context where,NoteStore store,
            com.eurobuddha.maxima.core.media.MediaManifest said,String origin,boolean onlyDoors) {
        return fromTheirDoor(where,store,said,origin,onlyDoors,List.of());
    }

    /**
     * @param others devices that have said they have the file too. Where the one that listed it cannot be reached,
     *               theirs are asked: a device keeps the pieces it fetched, and its door hands them out as it does its
     *               own. Only between the owner's devices this is the difference between a phone waiting until the
     *               other phone is on the same Wi-Fi and it having the file from the PC that already has it.
     */
    private static com.eurobuddha.maxima.core.media.MediaManifest fromTheirDoor(Context where,NoteStore store,
            com.eurobuddha.maxima.core.media.MediaManifest said,String origin,boolean onlyDoors,List<String> others) {
        List<String> doors=doorsWith(where,store,said,origin);
        for(String other:others==null?List.<String>of():others) {
            if(!doors.isEmpty())break;
            if(other!=null&&!other.equals(origin))doors=doorsWith(where,store,said,other);
        }
        // Only between the owner's devices, the doors that answered are the whole list: the relays a manifest
        // names are not asked, and where no door answered nothing is.
        if(onlyDoors&&doors.isEmpty())throw new IllegalStateException(Direct.WAITS);
        if(doors.isEmpty())return said;
        return new com.eurobuddha.maxima.core.media.MediaManifest(said.mime,said.size,said.keyHex,said.nonceHex,
            said.sha3Hex,said.chunkIds,onlyDoors?doors:Direct.sourcesFirst(doors,said.sources));
    }

    /** The doors of the device that listed a file that say they have its first piece; none where it is out of reach. */
    private static List<String> doorsWith(Context where,NoteStore store,com.eurobuddha.maxima.core.media.MediaManifest said,String origin) {
        List<String> doors=new ArrayList<>();
        try {
            MaximaNode node=Node.node(where);
            NoteStore.Contact them=origin==null||node==null?null:store.address(origin);
            com.eurobuddha.maxima.core.contacts.Contact reach=them==null?null:Node.known(where,them.contact);
            if(reach==null||said.chunkIds.isEmpty())return doors;
            for(String door:Direct.firstTries(node.lanAddressFor(reach.publicKey),new ArrayList<>(reach.addresses),reach.publicKey))
                if(com.eurobuddha.maxima.core.media.MediaWire.has(node,door,said.chunkIds.get(0)))doors.add(door);
        } catch(RuntimeException notNow){/* none, then: the relays, or with none of them, nothing */}
        return doors;
    }

    /** Which files each device has been told this device has, by an answer to their list, this run: "address file". */
    private static final java.util.Set<String> SAID_HAVE=java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * "I have this file", for each of these, to the device whose list named them, off the arrival's thread. Once a
     * run for each file and device where it went: after that, the one said on fetching and this one have both had
     * their chance, and a list arriving with every note would otherwise cost a message a file each time. One that
     * did not go is said again at their next list.
     */
    private static void sayWeHave(final Context where,final NoteStore store,final Keys keys,final NoteStore.Contact them,
                                  final List<String> files) {
        if(where==null||them==null||files==null||files.isEmpty()||them.agreement.length==0)return;
        final List<String> due=new ArrayList<>();
        for(String id:files)if(!SAID_HAVE.contains(them.address+" "+id))due.add(id);
        // Never the reason a node is started, as with the rest of file work.
        if(due.isEmpty()||!doesSpeak(where,them)||!Node.running())return;
        ANSWERS.execute(()->{
            int went=0;
            for(String id:due) {
                try{if(saidAboutFile(where,store,keys,them,id,Receipt.FILE_HERE)){SAID_HAVE.add(them.address+" "+id);went++;}}
                catch(Exception notNow){break;}   // out of reach: the rest would only wait out the same door
            }
            // Every one of them is here: what the count says is how many of those it was told of. Read the other way
            // round, "0 of 12" looked like a device that had none of the files it held.
            android.util.Log.i("Mininotes/Post",filesHere(due.size(),went));
        });
    }

    /** The log line for {@link #sayWeHave}: how many of the files a list named are here, and how many of those were said. */
    static String filesHere(int here,int told) {
        return here+" file(s) the device whose list came named are here: told it of "+(told>=here?"all":told+", the rest at its next list");
    }

    /** "I have this file", or "I cannot get it", to one device. The envelope names the file where it names a note. */
    private static boolean saidAboutFile(Context where,NoteStore store,Keys keys,NoteStore.Contact them,String file,int what) throws Exception {
        MaximaNode node=Node.node(where);
        if(node==null)return false;
        byte[] sealed=Envelope.seal(sixteen(file),0,System.currentTimeMillis(),Receipt.wrap(what),keys.signing(),Keys.publicKey(them.agreement));
        return hand(where,store,keys,node,them,sealed);
    }

    // ---- files sent straight to a device, belonging to no note: see Drop -------------------------------------

    /**
     * Told when something about files sent or received on their own moves on off the arrival path - files fetched,
     * an answer that came - so a screen can say it, or the phone can. Called off the interface thread.
     */
    static volatile java.util.function.Consumer<Landed> news;

    private static void tell(Landed landed) {
        java.util.function.Consumer<Landed> told=news;
        if(told!=null)try{told.accept(landed);}catch(RuntimeException notNow){/* the list says it when it is next drawn */}
    }

    /** A device whose build has said it takes files on their own, written down by the fingerprint of its key. */
    private static void takesFiles(Context where,String fingerprint) {
        if(where==null||fingerprint==null||fingerprint.isEmpty())return;
        android.content.SharedPreferences kept=where.getSharedPreferences("post",Context.MODE_PRIVATE);
        java.util.Set<String> all=new java.util.HashSet<>(kept.getStringSet("files",new java.util.HashSet<String>()));
        if(all.add(fingerprint))kept.edit().putStringSet("files",all).apply();
    }

    /** Whether a device has said it takes files on their own: a sending is offered only to one that has. */
    static boolean takesFiles(Context where,NoteStore.Contact them) {
        if(where==null||them==null)return false;
        String who=fingerprint(them);
        return !who.isEmpty()&&where.getSharedPreferences("post",Context.MODE_PRIVATE)
            .getStringSet("files",new java.util.HashSet<String>()).contains(who);
    }

    /**
     * When each device was last told this run that this device takes files, by fingerprint, and which of them it
     * went to. For this run only, so every start says it again - a telling a relay took for a device that was not
     * there is otherwise lost for good.
     */
    private static final java.util.Map<String,Long> TOLD_FILES=new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> WENT_FILES=java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** A device that could not be told is tried again this much later, not every round. */
    private static final long TELL_AGAIN=10L*60*1000;

    /** When this device last said to them that it takes files, this run; 0 for not yet. */
    private static long toldFilesAt(String who){Long at=TOLD_FILES.get(who);return at==null?0:at;}

    /**
     * "This device takes files", said to every paired device that has shown it knows what an answer is and has not
     * been told this run. A device that has never answered anything is not told: it may be a build from before
     * answers, which would write these bytes over a note. Two devices paired since know it from their hellos.
     */
    private static void tellTakesFiles(Context where,NoteStore store,Keys keys) {
        long now=System.currentTimeMillis();
        for(NoteStore.Contact them:store.addresses()) {
            if(!them.paired()||!sendsTo(store,them)||!(doesSpeak(where,them)||takesFiles(where,them)))continue;
            String who=fingerprint(them);long at=toldFilesAt(who);
            if(WENT_FILES.contains(who)||at>0&&now>=at&&now-at<TELL_AGAIN)continue;
            tellTakesFiles(where,store,keys,them);
        }
    }

    private static void tellTakesFiles(Context where,NoteStore store,Keys keys,NoteStore.Contact them) {
        String who=fingerprint(them);
        // Never the reason a node is started: this is said when the node is up for everything else.
        if(who.isEmpty()||!Node.running())return;
        TOLD_FILES.put(who,System.currentTimeMillis());
        try {
            MaximaNode node=Node.node(where);
            if(node==null)return;
            byte[] sealed=Envelope.seal(new byte[16],0,System.currentTimeMillis(),Receipt.wrap(Receipt.TAKES_FILES),
                keys.signing(),Keys.publicKey(them.agreement));
            MaximaSender.Result said=handDrop(where,store,keys,node,them,sealed,"said this device takes files");
            if(said!=null&&said.isOk())WENT_FILES.add(who);
        } catch(Exception notNow){/* tried again in a while */}
    }

    // ---- linked through what is shared: see Linking ------------------------------------------------------------

    /** One line for the log about linking: counts and states, never a name, an address or a key. */
    private static void link(String said){android.util.Log.i("Mininotes/Link",said);}

    /**
     * When each device a list linked this one with was last asked, and how often, by address. For this run only: the
     * asking is written down in the notebook ({@code accepting}) and said again at every opening as well.
     */
    private static final java.util.Map<String,long[]> ASKED=new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Whether anything goes to a device at all: it was paired by a code or connected, or a list of something here still
     * names it. A device linked through a list and since taken off everything stays known, and is sent nothing.
     */
    private static boolean sendsTo(NoteStore store,NoteStore.Contact them) {
        return them!=null&&(!them.listed()||store.listing(them.address)!=null);
    }

    /**
     * Everybody a list that just arrived names and this device is not linked with, linked through it: written down
     * with the keys the list gave, and then - off the arrival's thread - met on the network and asked to answer. Only
     * from a device with a say in who has the thing. Nothing of the thing goes to them until they answer.
     */
    private static void linkThrough(final Context where,final NoteStore store,final Keys keys,String from,String note,Parcel.Sent parcel) {
        if(parcel==null||parcel.members.isEmpty())return;
        List<Parcel.Member> named=Linking.toLink(parcel.members,store.listFromASay(from,note,parcel),store.mySigningKey,store.linkedKeys());
        if(named.isEmpty())return;
        final List<NoteStore.Contact> made=new ArrayList<>();
        for(Parcel.Member one:named) {
            if(!store.linkThrough(one.address,one.name,one.agreement,Linking.signing(one),true))continue;
            NoteStore.Contact them=store.address(one.address);
            if(them!=null)made.add(them);
        }
        link("a list named "+named.size()+" device(s) not linked here; "+made.size()+" linked through it, asked to answer");
        if(!made.isEmpty())ANSWERS.execute(()->{for(NoteStore.Contact them:made)askToLink(where,store,keys,them);});
    }

    /**
     * One device linked through a list, asked to answer: introduced to the network where it is not known yet - which
     * while notes go only between the owner's devices dials only a door on this network - and said a hello to. Blocking.
     */
    private static boolean askToLink(Context where,NoteStore store,Keys keys,NoteStore.Contact them) {
        long[] asked=ASKED.computeIfAbsent(them.address,any->new long[2]);
        asked[0]=System.currentTimeMillis();asked[1]++;
        // Never the reason a node is started: this goes when the node is up for everything else.
        if(!Node.running())return false;
        try {
            if(them.contact==null||them.contact.trim().isEmpty()) {
                String key=Node.introduce(where,them.address);
                if(!key.isEmpty())store.knownAs(them.address,key);
            }
        } catch(Exception notThere){/* the address the list gave is still tried below */}
        NoteStore.Contact now=store.address(them.address);
        if(now==null)return false;
        try {
            say(where,keys,now.address,now.agreement,Linking.SCOPE,Linking.TARGET,true,store.myName,store.myAddress,now.contact);
            link("asked a device a list linked this one with to answer: handed over");
            return true;
        } catch(Exception notNow) {
            link("asked a device a list linked this one with to answer: not reached ("+notNow.getClass().getSimpleName()+"), asked again later");
            return false;
        }
    }

    /** Whether this run has put right what the first build that linked left behind: see {@link NoteStore#tidyLinks}. */
    private static final java.util.concurrent.atomic.AtomicBoolean TIDIED=new java.util.concurrent.atomic.AtomicBoolean();

    /** Whoever a list linked this device with and has not answered, asked again once their turn has come. Blocking. */
    private static void linkAgain(Context where,NoteStore store,Keys keys) {
        if(TIDIED.compareAndSet(false,true)) {
            store.tidyLinks();
            // Waiting for a device that has answered something already - a note, a receipt - ends: it plainly has this
            // device's key and is there. The first build waited for one hello only, and it could go astray.
            int healed=0;
            for(String address:store.linkingNow()) {
                NoteStore.Contact them=store.address(address);
                if(them!=null&&doesSpeak(where,them)){store.answered(address);healed++;}
            }
            if(healed>0)link("stopped waiting for "+healed+" device(s) linked through a list that had answered already");
        }
        java.util.Set<String> waiting=store.linkingNow();
        if(waiting.isEmpty())return;
        long now=System.currentTimeMillis();int asked=0;
        for(String address:waiting) {
            long[] was=ASKED.get(address);
            if(was!=null&&!Linking.due(was[0],(int)was[1],now))continue;
            NoteStore.Contact them=store.address(address);
            if(them==null||!them.paired())continue;
            store.triedAgain(address);
            if(askToLink(where,store,keys,them))asked++;
        }
        if(asked>0)link("asked "+asked+" device(s) again to answer");
    }

    /**
     * A hello from a device linking through something both have. From a device linked here already: it has answered,
     * so what it is owed goes now; and where it asks, it is answered. From one that is not: taken only where a list here
     * names the key it signs with - see {@link Linking#names} - and then written down, met, answered and sent what it is
     * owed. From anybody else, nothing: not a contact, not a question put to the owner, not a word back.
     */
    private static Landed linkHeard(final Context where,final NoteStore store,final Keys keys,final Hello.Said said,byte[] sender) {
        NoteStore.Contact known=null;
        for(NoteStore.Contact one:store.addresses())
            if(one.paired()&&Courier.hex(sender).equals(fingerprint(one)))known=one;
        final boolean asks=Linking.asks(said);
        if(known!=null) {
            final NoteStore.Contact them=known;
            boolean was=store.linkingNow().contains(them.address);
            store.answered(them.address);
            link("a device linked here said hello"+(asks?", asking to be answered":", answering")+(was?": linked, and what it is owed goes now":""));
            ANSWERS.execute(()->{
                // Something just arrived, so the node is up; where it is not, nothing is started for this.
                if(!Node.running())return;
                if(asks)answerLink(where,store,keys,them.address,said.address);
                if(was)owedTo(where,store,keys,them.address);
            });
            return was?Landed.people(""):new Landed(null,null);
        }
        Sharing.Rule named=store.namesDevice(said.signing);
        if(named==null) {
            link("a device not linked here asked to link, and no list here names it: not taken");
            return new Landed(null,null);
        }
        // Filed at the address the list gives it, which the list's rows point at; met at the one it says it is at now.
        final String address=named.address;
        String name=store.memberName(address);
        if(!store.linkThrough(address,name.isBlank()?said.name:name,said.agreement,said.signing,false))return new Landed(null,null);
        link("a device a list here names asked to link: linked"+(asks?", answered":""));
        ANSWERS.execute(()->{
            if(!Node.running())return;
            met(where,store,address,said.address);
            if(asks)answerLink(where,store,keys,address,said.address);
            owedTo(where,store,keys,address);
        });
        return Landed.people("");
    }

    /**
     * A device that has just said hello, met at the address its hello gives - where it is now - wherever the network
     * does not know it yet. The address a list gave is the one its sender last saw, and a device restarted since is
     * somewhere else: an answer handed to that address was taken by a relay for nobody, so the device that asked went
     * on asking, and both ends went on drawing the other as not linked.
     */
    private static void met(Context where,NoteStore store,String address,String now) {
        NoteStore.Contact them=store.address(address);
        if(them==null||now==null||now.trim().isEmpty())return;
        if(them.contact!=null&&!them.contact.trim().isEmpty()&&Node.known(where,them.contact)!=null)return;
        try {
            String key=Node.introduce(where,now);
            if(!key.isEmpty())store.knownAs(address,key);
        } catch(Exception notThere){/* the address kept here is tried below */}
    }

    /** A hello that asked to be answered, answered - and said in the log, since an answer that went nowhere is the fault. */
    private static void answerLink(Context where,NoteStore store,Keys keys,String address,String now) {
        met(where,store,address,now);
        NoteStore.Contact them=store.address(address);
        if(them==null)return;
        try {
            say(where,keys,them.address,them.agreement,Linking.SCOPE,Linking.TARGET,false,store.myName,store.myAddress,them.contact);
            link("answered a device that asked to link: handed over");
        } catch(Exception notNow){link("could not answer a device that asked to link ("+notNow.getClass().getSimpleName()+"): it asks again");}
    }

    /** Whatever one device is owed of everything, sent now: it has just shown it is there. Blocking. */
    private static void owedTo(Context where,NoteStore store,Keys keys,String address) {
        try {
            Done done=send(where,store,keys,NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING,address);
            link("sent what a device just linked is owed: "+done.sent+" went, "+done.failed+" did not");
        } catch(Exception notNow){link("could not send what a device just linked is owed: "+notNow.getClass().getSimpleName());}
    }

    // ---- one person on every device they own: see Persons ------------------------------------------------------

    /** Who owns this device, as kept here; the id is made the first time it is asked for. */
    static synchronized Persons.Me me(Context where) {
        android.content.SharedPreferences kept=where.getSharedPreferences("settings",Context.MODE_PRIVATE);
        String id=kept.getString("person","");long made=kept.getLong("personMade",0);
        if(!Persons.isId(id)) {
            id=Persons.newId(new java.security.SecureRandom());made=System.currentTimeMillis();
            kept.edit().putString("person",id).putLong("personMade",made).apply();
        }
        String chosen=kept.getString("me","").trim();
        String device=kept.getString("device","").trim();
        return new Persons.Me(id,made,Persons.aliases(kept.getString("personAliases","")),
            chosen.isEmpty()?Node.nameHere(where):chosen,chosen.isEmpty()?0:kept.getLong("meChanged",0),
            device.isEmpty()?Node.deviceHere(where):device,device.isEmpty()?0:kept.getLong("deviceChanged",0));
    }

    /** Who owns this device, kept: a new id, its aliases, and your name where another device of yours chose it later. */
    private static synchronized void keepMe(Context where,NoteStore store,Persons.Me was,Persons.Me now) {
        android.content.SharedPreferences.Editor edit=where.getSharedPreferences("settings",Context.MODE_PRIVATE).edit()
            .putString("person",now.id).putLong("personMade",now.made).putString("personAliases",Persons.aliases(now.aliases));
        // A later choice is kept even where it is the same name: kept only when the words differed, the older time
        // stayed here and every card from then on said "your name chosen there later" again.
        boolean renamed=now.named>was.named&&!now.name.equals(was.name);
        if(now.named>was.named&&!now.name.isEmpty())edit.putString("me",now.name).putLong("meChanged",now.named);
        edit.apply();
        if(store!=null)store.ownDevicesAre(now.id);
        if(renamed) {
            if(store!=null)store.myName=now.name;
            // The network is told off whichever worker this is on: an arrival must not wait on the node.
            final String name=now.name;
            ANSWERS.execute(()->Node.called(where,name));
        }
        if(!now.id.equals(was.id))persons("now one person with another device of theirs");
    }

    /**
     * A device just bonded as one of the owner's own - its hello accepted an offer of everything - and so marked as
     * theirs here. Its hello says whose it is and what it is called, where its build knows about persons: whose id both
     * keep is decided now, its name among your devices written down, and cards can go to it from the next round, since
     * a build that knows about persons and accepted everything has marked this device as its owner's too.
     */
    static void bonded(Context where,NoteStore store,Hello.Said them) {
        NoteStore.Contact saved=store.address(them.address);
        if(saved==null)return;
        store.setMine(them.address,true);
        if(!them.persons){persons("bonded with a device whose build does not know about persons");return;}
        String who=fingerprint(saved);
        readsCards(where,who,true);
        if(Persons.later(them.deviceNamed,saved.named,true))store.ownDeviceNamed(them.signing,them.deviceName,them.deviceNamed);
        Persons.Me was=me(where);
        keepMe(where,store,was,Persons.bonded(was,them));
        persons("bonded with one of the owner's devices");
    }

    /** A device whose build has said it knows about persons, and whether it said this device is its owner's too. */
    private static void readsCards(Context where,String fingerprint,boolean mineThere) {
        if(where==null||fingerprint==null||fingerprint.isEmpty())return;
        android.content.SharedPreferences kept=where.getSharedPreferences("post",Context.MODE_PRIVATE);
        java.util.Set<String> reads=new java.util.HashSet<>(kept.getStringSet("persons",new java.util.HashSet<String>()));
        java.util.Set<String> mine=new java.util.HashSet<>(kept.getStringSet("mineThere",new java.util.HashSet<String>()));
        boolean changed=reads.add(fingerprint);
        changed|=mineThere?mine.add(fingerprint):mine.remove(fingerprint);
        if(changed)kept.edit().putStringSet("persons",reads).putStringSet("mineThere",mine).apply();
    }

    private static boolean readsCards(Context where,String fingerprint) {
        return where!=null&&!fingerprint.isEmpty()&&where.getSharedPreferences("post",Context.MODE_PRIVATE)
            .getStringSet("persons",new java.util.HashSet<String>()).contains(fingerprint);
    }

    private static boolean mineThere(Context where,String fingerprint) {
        return where!=null&&!fingerprint.isEmpty()&&where.getSharedPreferences("post",Context.MODE_PRIVATE)
            .getStringSet("mineThere",new java.util.HashSet<String>()).contains(fingerprint);
    }

    /** What People and devices says under a device that only one end counts as the owner's: see {@link Persons#oneSided}. */
    static String oneSided(Context where,NoteStore.Contact them) {
        if(them==null||!them.paired())return null;
        String who=fingerprint(them);
        String line=Persons.oneSided(them.mine,readsCards(where,who),mineThere(where,who),Node.A_PC?"PC":"phone");
        // Neither end has said, or both said no, and another device of yours lists it as yours: the switch to turn.
        return line==null&&!them.mine&&listedAsYours(where,who)?Persons.LISTED_AS_YOURS:line;
    }

    /** Devices a card from one of yours listed as yours, by fingerprint: kept, so People can say so after a restart. */
    private static boolean listedAsYours(Context where,String fingerprint) {
        return where!=null&&!fingerprint.isEmpty()&&where.getSharedPreferences("post",Context.MODE_PRIVATE)
            .getStringSet("listedAsYours",new java.util.HashSet<String>()).contains(fingerprint);
    }

    /** The devices paired here that a card lists, kept as yours or not by what it says of each. Whether that changed. */
    private static boolean listedAsYours(Context where,NoteStore store,Persons.Card card) {
        android.content.SharedPreferences kept=where.getSharedPreferences("post",Context.MODE_PRIVATE);
        java.util.Set<String> listed=new java.util.HashSet<>(kept.getStringSet("listedAsYours",new java.util.HashSet<String>()));
        boolean changed=false;
        for(Persons.Device one:card.devices)for(NoteStore.Contact here:store.addresses()) {
            if(!here.paired()||!Persons.key(here.signing).equals(Persons.key(one.signing)))continue;
            String who=fingerprint(here);
            if(!who.isEmpty())changed|=one.state==Persons.ACTIVE?listed.add(who):listed.remove(who);
        }
        if(changed)kept.edit().putStringSet("listedAsYours",listed).apply();
        return changed;
    }

    /**
     * What each device was last told this run about persons, by fingerprint: which of the two, when, and whether it
     * went. For this run only, as with files: every start says it again. Said again when which of the two it would be
     * changes, because that is somebody turning the switch in People.
     */
    private static final java.util.Map<String,long[]> TOLD_PERSONS=new java.util.concurrent.ConcurrentHashMap<>();
    /** The last card each device was sent this run, by fingerprint, as a hash: sent again only when it changes. */
    private static final java.util.Map<String,String> CARDS=new java.util.concurrent.ConcurrentHashMap<>();
    /** The last card each device was found out of reach for, as a hash: so the log says so once a card. */
    private static final java.util.Map<String,String> CARD_WAITS=new java.util.concurrent.ConcurrentHashMap<>();
    /** What each device last said about persons this run, by fingerprint, and when: see {@link Persons#news}. */
    private static final java.util.Map<String,long[]> HEARD_PERSONS=new java.util.concurrent.ConcurrentHashMap<>();

    /** "This build knows about persons", said to every paired device that has shown it knows what an answer is. */
    private static void tellPersons(Context where,NoteStore store,Keys keys) {
        long now=System.currentTimeMillis();
        for(NoteStore.Contact them:store.addresses()) {
            String who=fingerprint(them);
            if(who.isEmpty()||!them.paired()||!sendsTo(store,them)||!(doesSpeak(where,them)||readsCards(where,who)))continue;
            long[] told=TOLD_PERSONS.get(who);int want=them.mine?Receipt.PERSONS_MINE:Receipt.PERSONS;
            if(told!=null&&told[0]==want&&(told[2]==1||now>=told[1]&&now-told[1]<TELL_AGAIN))continue;
            tellPersons(where,store,keys,them);
        }
    }

    private static void tellPersons(Context where,NoteStore store,Keys keys,NoteStore.Contact them) {
        String who=fingerprint(them);
        if(who.isEmpty()||!Node.running())return;
        int what=them.mine?Receipt.PERSONS_MINE:Receipt.PERSONS;
        long[] told={what,System.currentTimeMillis(),0};
        TOLD_PERSONS.put(who,told);
        try {
            MaximaNode node=Node.node(where);
            if(node==null)return;
            byte[] sealed=Envelope.seal(new byte[16],0,System.currentTimeMillis(),Receipt.wrap(what),
                keys.signing(),Keys.publicKey(them.agreement));
            if(hand(where,store,keys,node,them,sealed))told[2]=1;
            persons("said this build knows about persons"+(them.mine?", to one of the owner's devices":"")+(told[2]==1?"":": not taken"));
        } catch(Exception notNow){/* tried again in a while */}
    }

    // ---- notes and collections, nested as deep as anybody likes: see Things -----------------------------------

    /** Devices whose build has said it knows about trees, by the fingerprint of their key: kept, across restarts. */
    private static boolean trees(Context where,String fingerprint) {
        if(where==null||fingerprint==null||fingerprint.isEmpty())return false;
        android.content.SharedPreferences kept=where.getSharedPreferences("post",Context.MODE_PRIVATE);
        java.util.Set<String> all=new java.util.HashSet<>(kept.getStringSet("trees",new java.util.HashSet<String>()));
        if(!all.add(fingerprint))return false;
        kept.edit().putStringSet("trees",all).apply();
        return true;
    }

    /**
     * Whether a device's build has said it knows about trees. One that has not is a 0.1 device: it is sent only what fits
     * three levels, and no collection on its own.
     */
    static boolean knowsTrees(Context where,NoteStore.Contact them) {
        if(where==null||them==null)return false;
        String who=fingerprint(them);
        return !who.isEmpty()&&where.getSharedPreferences("post",Context.MODE_PRIVATE)
            .getStringSet("trees",new java.util.HashSet<String>()).contains(who);
    }

    /**
     * A collection that arrived on its own, from a device paired here (see {@link Carton}). Taken in where they may write
     * in it or it is theirs, as a note is, and answered either way - "took" where this device now has what they sent,
     * "have" where it did not write it down - or they would send it every quarter of an hour for ever. Not where this
     * device has stopped taking it in or has left it: nothing is said about what somebody asked not to hear about.
     */
    private static Landed cartonArrived(Context where,NoteStore store,Keys keys,NoteStore.Contact from,Envelope.Opened opened,
                                        Carton.Sent carton) {
        NoteStore.Refusal refused=store.refusal(from.address,carton);
        if(refused!=null) {
            android.util.Log.i("Mininotes/Post","a collection on its own, not taken in: this device "
                +(refused.gone?"has left it":"has stopped taking it in"));
            return new Landed(null,null);
        }
        String here=store.cartonArrived(from.address,opened.revision,carton);
        // Something of theirs is here, so whatever was accepted has been answered.
        if(here!=null)store.answered(from.address);
        android.util.Log.i("Mininotes/Post","a collection arrived on its own: "+(here==null?"not taken in, they may not write in it":"taken in"));
        // The files it keeps at their end, taken in as a note's are, with the collection where the note would be: what is
        // not here yet is fetched in the background, what came from them and is no longer listed goes, and what of
        // theirs is here already is said to them. A list not said changes nothing.
        if(here!=null&&carton.files!=null) {
            if(store.listed(here,from.address,carton.filesAsOf,carton.files))travelSoon(where,store,keys);
            moved(here);
            sayWeHave(where,store,keys,from,store.haveHere(here,carton.files));
        }
        if(carton.answer){speaks(where,from);answer(where,store,keys,from,opened.page,opened.revision,here!=null);}
        return Landed.people(here==null?"":here);
    }

    /** When each device was last told this run that this build knows about trees, by fingerprint, and whether it went. */
    private static final java.util.Map<String,long[]> TOLD_TREES=new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * "This build knows about trees", said to every paired device that has shown it knows what an answer is, once a run
     * and again in a while where it was not taken - as {@link Receipt#PERSONS} is. A device that has never answered
     * anything is not told: it may be a build from before answers, which would write these bytes over a note.
     */
    private static void tellTrees(Context where,NoteStore store,Keys keys) {
        long now=System.currentTimeMillis();
        for(NoteStore.Contact them:store.addresses()) {
            String who=fingerprint(them);
            if(who.isEmpty()||!them.paired()||!sendsTo(store,them)||!(doesSpeak(where,them)||knowsTrees(where,them)))continue;
            long[] told=TOLD_TREES.get(who);
            if(told!=null&&(told[1]==1||now>=told[0]&&now-told[0]<TELL_AGAIN))continue;
            tellTrees(where,store,keys,them);
        }
    }

    private static void tellTrees(Context where,NoteStore store,Keys keys,NoteStore.Contact them) {
        String who=fingerprint(them);
        // Never the reason a node is started: this is said when the node is up for everything else.
        if(who.isEmpty()||!Node.running())return;
        long[] told={System.currentTimeMillis(),0};
        TOLD_TREES.put(who,told);
        try {
            MaximaNode node=Node.node(where);
            if(node==null)return;
            byte[] sealed=Envelope.seal(new byte[16],0,System.currentTimeMillis(),Receipt.wrap(Receipt.TREE),
                keys.signing(),Keys.publicKey(them.agreement));
            if(hand(where,store,keys,node,them,sealed))told[1]=1;
            android.util.Log.i("Mininotes/Post","said this build knows about trees"+(told[1]==1?"":": not taken"));
        } catch(Exception notNow){/* tried again in a while */}
    }

    /**
     * "This notebook is locked", or "open again", said to every paired device that has said it knows about persons (see
     * {@link Receipt#LOCKED}). Sealed here, while the notebook is still open to say who they are, and handed over off
     * the caller's thread, which is closing the notebook: nothing after this reads it. Open again is said only after a
     * lock was: most openings are nobody's news.
     */
    static void sayLocked(Context where,NoteStore store,Keys keys,boolean locked) {
        try {
            android.content.SharedPreferences kept=where.getSharedPreferences("post",Context.MODE_PRIVATE);
            if(!locked&&kept.getLong("saidLocked",0L)==0L)return;
            MaximaNode node=Node.node(where);
            if(node==null||!Node.running())return;
            final List<Object[]> going=new ArrayList<>();
            long now=System.currentTimeMillis();
            for(NoteStore.Contact them:store.addresses()) {
                String who=fingerprint(them);
                if(who.isEmpty()||!them.paired()||them.agreement.length==0||!sendsTo(store,them)||!readsCards(where,who))continue;
                com.eurobuddha.maxima.core.contacts.Contact reach=Node.known(where,them.contact);
                if(reach==null)continue;
                going.add(new Object[]{reach,Envelope.seal(new byte[16],0,now,Receipt.wrap(locked?Receipt.LOCKED:Receipt.OPENED),
                    keys.signing(),Keys.publicKey(them.agreement)),who});
            }
            kept.edit().putLong("saidLocked",locked?1L:0L).apply();
            // Locked again, nobody is owed "open again" any more.
            if(locked)OPEN_OWED.clear();
            // And the notebook just opened is the one to say it from, before any round has said so.
            else Node.onMetHere(key->sayOpenAgain(where,store,keys,key));
            if(going.isEmpty())return;
            final boolean helpers=Node.helpers();
            ANSWERS.execute(()->{
                int went=0;
                for(Object[] one:going) {
                    boolean ok=false;
                    try{ok=Direct.send(node,(com.eurobuddha.maxima.core.contacts.Contact)one[0],APPLICATION,(byte[])one[1],null,helpers).isOk();}
                    catch(Exception notNow){/* they go on waiting, amber, as before */}
                    if(ok)went++;
                    // Not reached: said again when its door is heard (see sayOpenAgain), not left to whatever it next hears.
                    if(!locked){if(ok)OPEN_OWED.remove((String)one[2]);else OPEN_OWED.add((String)one[2]);}
                }
                android.util.Log.i("Mininotes/Post","said this notebook is "+(locked?"locked":"open again")+" to "+went+" of "+going.size()+" device(s)");
            });
        } catch(Exception notNow) {
            android.util.Log.w("Mininotes/Post","could not say whether this notebook is locked: "+notNow.getClass().getSimpleName());
        }
    }

    /** The devices, by {@link #fingerprint}, that "open again" did not reach: said to each when its door is next heard. */
    private static final java.util.Set<String> OPEN_OWED=java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** "Open again", said to a device just heard on this network, by its identity key, where it did not reach it before. Blocking. */
    static void sayOpenAgain(Context where,NoteStore store,Keys keys,String key) {
        if(OPEN_OWED.isEmpty()||key==null||!Node.running())return;
        for(NoteStore.Contact them:store.addresses()) {
            String kept=them.contact==null?"":them.contact.trim(),who=fingerprint(them);
            if(kept.isEmpty()||!Direct.key(kept).equals(Direct.key(key))||!OPEN_OWED.contains(who))continue;
            try {
                MaximaNode node=Node.node(where);
                com.eurobuddha.maxima.core.contacts.Contact reach=Node.known(where,kept);
                if(node==null||reach==null)return;
                byte[] sealed=Envelope.seal(new byte[16],0,System.currentTimeMillis(),Receipt.wrap(Receipt.OPENED),keys.signing(),Keys.publicKey(them.agreement));
                boolean ok=Direct.send(node,reach,APPLICATION,sealed,null,Node.helpers()).isOk();
                if(ok)OPEN_OWED.remove(who);
                android.util.Log.i("Mininotes/Post","said this notebook is open again to a device just heard on this network: "+(ok?"taken":"not taken"));
            } catch(Exception notNow){/* said when it is next heard anew */}
            return;
        }
    }

    /** The card, sent to every device of the owner's that reads cards and counts this one as theirs, where it changed. */
    private static void sendCards(Context where,NoteStore store,Keys keys) {
        List<NoteStore.Contact> own=new ArrayList<>();
        for(NoteStore.Contact them:store.addresses()) {
            String who=fingerprint(them);
            if(them.paired()&&Persons.goesTo(them.mine,readsCards(where,who),mineThere(where,who)))own.add(them);
        }
        if(own.isEmpty()||!Node.running())return;
        try {
            Persons.Me me=me(where);
            List<Persons.Device> others=new ArrayList<>();
            for(NoteStore.Contact one:store.addresses())if(one.mine&&one.paired())
                others.add(new Persons.Device(shortKey(one.signing),shortKey(one.agreement),one.address,one.name,Persons.ACTIVE,one.named));
            Persons.Device self=new Persons.Device(Point.shorten(keys.signing().getPublic()),Point.shorten(keys.agreement().getPublic()),
                store.myAddress==null?"":store.myAddress,me.device,Persons.ACTIVE,me.deviceNamed);
            Persons.Card card=Persons.card(me,self,others);
            byte[] plain=Persons.wrap(card);
            String hash=Courier.hex(java.security.MessageDigest.getInstance("SHA-256").digest(Persons.sameness(card)));
            MaximaNode node=Node.node(where);
            if(node==null)return;
            for(NoteStore.Contact them:own) {
                String who=fingerprint(them);
                if(hash.equals(CARDS.get(who)))continue;
                byte[] sealed=Envelope.seal(Persons.idBytes(me.id),card.newest(),System.currentTimeMillis(),plain,
                    keys.signing(),Keys.publicKey(them.agreement));
                // One device out of reach - only between your devices, with its door not heard here - is not a card
                // kept from the rest, and it is sent again at the next round, as anything not taken is.
                boolean went;
                try{went=hand(where,store,keys,node,them,sealed);}
                catch(Exception noWay) {
                    // Said once for each card, not at every round it waits.
                    if(!hash.equals(CARD_WAITS.put(who,hash)))persons("a card waits for one of the owner's devices: "+Unsent.reason(noWay));
                    continue;
                }
                // Why it went, so a log shows a card going round unchanged for what it is.
                String why=CARDS.containsKey(who)?"it changed":"none went since the start, or since their word about persons";
                if(went)CARDS.put(who,hash);
                persons("a card of "+card.devices.size()+" device(s) "+(went?"handed to":"not taken for")+" one of the owner's devices ("+why+")");
            }
        } catch(Exception notNow) {
            persons("could not send a card: "+Unsent.reason(notNow));
        }
    }

    /** A key in the short form a card carries, whichever form it is kept in. */
    private static byte[] shortKey(byte[] kept) {
        try{return Point.shorten(Point.read(kept));}catch(Exception notAKey){return kept;}
    }

    /**
     * A card from another device of the owner's, taken in: whose id this device keeps, your name where it was chosen
     * later there, and what your devices are called. A card from a device not marked as theirs here - or one that does
     * not list this device as theirs - changes nothing; nor does one older than the last heard from the same device.
     */
    private static Landed cardArrived(Context where,NoteStore store,Keys keys,NoteStore.Contact from,Envelope.Opened opened) {
        Persons.Card card=Persons.open(opened.text);
        String who=fingerprint(from);
        if(card==null||who.isEmpty()){persons("a card that could not be read");return new Landed(null,null);}
        android.content.SharedPreferences kept=where.getSharedPreferences("post",Context.MODE_PRIVATE);
        if(opened.revision<kept.getLong("card."+who,0)){persons("a card older than one already heard: nothing changes");return new Landed(null,null);}
        try {
            Persons.Me was=me(where);
            // Every device paired here, not only those marked yours: a device yours at both ends lists which are yours,
            // and one the switch was never turned for here is still called what you called it. Seen on 0.1.029: a PC
            // with one phone of two marked as the owner's went on showing the other by the name on its pairing code.
            Persons.Taken taken=Persons.take(was,card,from.mine,from.signing,keys.signing().getPublic().getEncoded(),store.devicesNamed());
            if(taken==null){persons("a card from a device that is not the owner's at both ends: nothing changes");return new Landed(null,null);}
            kept.edit().putLong("card."+who,opened.revision).apply();
            // It is the owner's at both ends and reads cards, or it could not have sent one.
            readsCards(where,who,true);
            keepMe(where,store,was,taken.me);
            // Counted only where something here changed: a device is believed about itself at every card, and a log
            // saying "1 device name" at each of them read as a name that never stuck.
            int named=0;
            for(Persons.Device one:taken.names.values())if(store.ownDeviceNamed(one.signing,one.name,one.decided))named++;
            boolean renamed=taken.renamed&&!taken.me.name.equals(was.name);
            boolean listed=listedAsYours(where,store,card);
            persons("a card taken in"+(taken.adopted?": this device is now one person with it":"")
                +(renamed?", your name chosen there later":"")+(named==0?", nothing new":", "+named+" device name(s) changed"));
            return taken.adopted||renamed||named>0||listed?Landed.devices():new Landed(null,null);
        } catch(Exception unreadable) {
            persons("a card that could not be weighed: "+unreadable.getClass().getSimpleName());
            return new Landed(null,null);
        }
    }

    /** One line for the log about persons: what happened, never a name, an id, an address or a key. */
    private static void persons(String said){android.util.Log.i("Mininotes/Persons",said);}

    /** One line for the log about files sent on their own: counts and states, never a name, an address or a key. */
    private static void drop(String said){android.util.Log.i("Mininotes/Drop",said);}

    /**
     * Sealed bytes about files sent on their own, handed to a device as anything is, and which road they took said
     * in the log - read off Direct's counts, so near enough when two sends cross. A sending that never arrives looked,
     * from both ends, exactly like one still on its way.
     */
    private static MaximaSender.Result handDrop(Context where,NoteStore store,Keys keys,MaximaNode node,NoteStore.Contact them,
                                                byte[] sealed,String what) throws Exception {
        int doors=Direct.WENT.get(),homes=Direct.LEFT.get(),shut=Direct.SHUT_HERE.get();
        MaximaSender.Result said;
        try{said=handTo(where,store,keys,node,them,sealed);}
        catch(Exception notSent){drop(what+": not sent ("+notSent.getClass().getSimpleName()+")");throw notSent;}
        boolean ok=said!=null&&said.isOk();
        drop(what+": "+(!ok?"not taken":Direct.road(doors,homes,shut,true).substring(2))+(said==null?"":" ("+said.statusName+")"));
        return said;
    }

    /**
     * An offer, taken in. A new sending from one of the owner's devices is fetched at once; from anybody else it
     * is asked about. An offer of one already settled is answered again - they did not hear the first answer - and
     * a later offer of one still coming says where its files are now.
     */
    private static Landed offered(Context where,NoteStore store,Keys keys,NoteStore.Contact from,String wire,long revision,
                                  List<Enclosure.Listed> files) {
        // Whoever offers takes files: nothing more needs saying to know it.
        takesFiles(where,fingerprint(from));
        long bytes=0;for(Enclosure.Listed one:files)bytes+=one.bytes;
        drop("offer heard: "+Drop.files(files.size())+", "+bytes+" bytes, offer "+revision+", from a device "
            +(from.paired()?"paired here":"not paired here")+(from.mine?", one of yours":""));
        NoteStore.Transfer was=store.transferOnTheWire(wire,from.address,false);
        if(was!=null) {
            final int again=Drop.answerAgain(was.state);
            if(again!=0) {
                drop("offer heard again, already settled here: answered again");
                final NoteStore.Contact them=from;final long offer=Math.max(revision,was.revision);
                ANSWERS.execute(()->{try{sayAboutSending(where,store,keys,them,wire,offer,again);}catch(Exception notNow){/* said when it is offered again */}});
                return new Landed(null,null);
            }
            NoteStore.Transfer now=store.offerArrived(from.address,from.name,wire,revision,files,was.state);
            drop(now==null?"offer heard again, older than the one here: nothing changes"
                :"offer heard again: "+(now.state==Drop.FETCHING?"its files are fetched from where it says now":"still waiting for your answer"));
            if(now!=null&&now.state==Drop.FETCHING)travelSoon(where,store,keys);
            return new Landed(null,null);
        }
        int state=Drop.arriving(from.paired(),from.mine);
        if(state==0){drop("offer not taken: from a device not paired here");return new Landed(null,null);}
        NoteStore.Transfer now=store.offerArrived(from.address,from.name,wire,revision,files,state);
        if(now==null)return new Landed(null,null);
        drop(state==Drop.FETCHING?"offer taken at once, from one of your devices: fetching":"offer put to you: waiting for Accept or Refuse");
        if(state==Drop.FETCHING){travelSoon(where,store,keys);return Landed.transfer(null,null);}
        return Landed.transfer(Drop.asking(from.name,files.size(),now.bytes())+".",now.id);
    }

    /** What the device a sending went to says about it: all here, refused, or its files cannot be had from where they are. */
    private static Landed answeredAboutTransfer(Context where,NoteStore store,Keys keys,NoteStore.Contact from,String wire,
                                                long revision,int about) {
        String heard=about==Receipt.DROP_HAVE?"have them":about==Receipt.DROP_REFUSED?"refused":"cannot get them";
        NoteStore.Transfer one=store.transferOnTheWire(wire,from.address,true);
        if(one==null){drop("answer heard ("+heard+") about a sending not kept here: nothing changes");return new Landed(null,null);}
        if(about==Receipt.DROP_MISSING) {
            // Only between the owner's devices there is nothing to send the pieces up to: they wait here, at the door.
            boolean again=Node.helpers()&&store.offerAgain(one.id,revision);
            drop("answer heard: they cannot get them"+(again?", so the pieces go up to relays and it is offered again"
                :Node.helpers()?", but the pieces went up a moment ago: left as it is":", and with no helpers the pieces wait at this door"));
            if(again)travelSoon(where,store,keys);
            return new Landed(null,null);
        }
        int next=Drop.after(one.state,about);
        drop("answer heard: "+heard+(next==one.state?", which changes nothing here":next==Drop.DELIVERED?": delivered":": refused"));
        if(next==one.state)return new Landed(null,null);
        store.settle(one.id,next);
        // Finished with, either way: the copies kept to send it, and its pieces, go.
        for(String pieces:store.sentAndDone(one.id))Node.forget(where,pieces);
        return Landed.transfer(Drop.answered(from.name,one.files.size(),next)+".",null);
    }

    /** An answer about a sending, to the device at the other end of it. The envelope names it where it names a note. */
    private static boolean sayAboutSending(Context where,NoteStore store,Keys keys,NoteStore.Contact them,String wire,long revision,
                                           int what) throws Exception {
        // Not the reason a node is started either: an answer not said now is said when they offer again.
        MaximaNode node=Node.running()?Node.node(where):null;
        if(node==null||them==null||!them.paired()){drop("an answer not said now: "+(node==null?"the node is not up":"not paired with them"));return false;}
        byte[] sealed=Envelope.seal(sixteen(wire),revision,System.currentTimeMillis(),Receipt.wrap(what),keys.signing(),Keys.publicKey(them.agreement));
        MaximaSender.Result said=handDrop(where,store,keys,node,them,sealed,"said "+(what==Receipt.DROP_HAVE?"every file is here"
            :what==Receipt.DROP_REFUSED?"refused":"the files cannot be had"));
        return said!=null&&said.isOk();
    }

    /**
     * One sending made here, made ready and offered: each file sealed and cut into pieces kept here, where this
     * device's door hands them out - and sent up to relays as well only where helpers may be used and the other
     * device can reach no door of this one's (see {@link Drop#upToRelays}), or it said it could not get them. Then
     * the list goes to them sealed, by their doors, their home, and with helpers the relays. Nothing counts as
     * delivered until they say every file is here.
     *
     * @return whether the offer was handed over
     */
    private static boolean offer(Context where,NoteStore store,Keys keys,com.eurobuddha.maxima.core.media.MediaService media,
                                 NoteStore.Transfer one) {
        try {
            NoteStore.Contact them=store.address(one.address);
            drop((one.tries==0&&one.revision<=1?"offering: ":"offering again (try "+(one.tries+1)+", offer "+one.revision+"): ")
                +Drop.files(one.files.size())+", "+one.bytes()+" bytes");
            if(them==null||!them.paired())throw new IllegalStateException("Not paired with them.");
            // Waiting for them to say they take files: an older build would write the offer over a note.
            if(!takesFiles(where,them)) {
                // Asked again, now and then, for a device that has shown it knows what an answer is: it says it back
                // (see Drop.sayBack), so one that said it once while this device could not hear is heard now.
                boolean asked=doesSpeak(where,them)&&Drop.sayBack(toldFilesAt(fingerprint(them)),System.currentTimeMillis());
                drop("waiting: they have not said they take files"+(asked?"; told them this device does, so they say it back":doesSpeak(where,them)?"":"; they have never answered anything, so nothing is said to them"));
                if(asked)tellTakesFiles(where,store,keys,them);
                store.offered(one.id);return false;
            }
            MaximaNode node=Node.node(where);
            if(node==null)throw new IllegalStateException("The node is not running.");
            boolean helpers=Node.helpers();
            boolean near=them.contact!=null&&!them.contact.trim().isEmpty()&&node.lanAddressFor(them.contact.trim())!=null;
            boolean proved=false;
            for(String door:node.directAddresses())if(!Direct.onThisNetwork(door))proved=true;
            boolean relays=Drop.upToRelays(helpers,near,proved);
            drop("they take files; "+(near?"on this network":"not seen on this network")+(proved?", this device has a public door":"")
                +(helpers?"":", only between your devices")+(relays?": pieces go up to relays":": pieces stay at this device's door"));
            List<Enclosure.Listed> listed=new ArrayList<>();
            for(NoteStore.Loose file:one.files) {
                if(!file.here)continue;
                String manifest=file.manifest;
                boolean up=helpers&&(relays||file.relay);
                if(manifest.isEmpty()||helpers&&file.relay) {
                    byte[] plain=store.bytesOf(file.held());
                    com.eurobuddha.maxima.core.media.MediaManifest made=up?media.publish(plain,file.kind)
                        :new com.eurobuddha.maxima.core.media.MediaService(null,Node.blobs(where)).publish(plain,file.kind);
                    if(up&&!Direct.relayed(made.sources,own(where))) {
                        Node.forget(where,made.encode());
                        throw new IllegalStateException("No relay took it.");
                    }
                    if(!manifest.isEmpty())Node.forget(where,manifest);
                    manifest=made.encode();
                    store.loosePublished(file.id,manifest);
                    if(up)store.wentToRelays(file.id);
                    drop("one file made ready: "+made.chunkIds.size()+" piece(s) "+(up?"kept here and on "+made.sources.size()+" relay(s)":"kept at this device's door"));
                }
                listed.add(new Enclosure.Listed(file.theirs,file.name,file.kind,file.bytes,manifest));
            }
            if(listed.isEmpty())throw new IllegalStateException("Nothing left to send.");
            byte[] sealed=Envelope.seal(sixteen(one.wire),one.revision,System.currentTimeMillis(),Drop.wrap(listed),
                keys.signing(),Keys.publicKey(them.agreement));
            MaximaSender.Result said=handDrop(where,store,keys,node,them,sealed,"offer of "+Drop.files(listed.size())+" ("+sealed.length+" bytes sealed) sent");
            store.offered(one.id);
            return said!=null&&said.isOk();
        } catch(Throwable notNow) {
            store.offered(one.id);
            drop("not offered this time: "+notNow.getClass().getSimpleName()+"; tried again on the clock");
            return false;
        } finally {tell(Landed.transfer(null,null));}
    }

    /**
     * A sending that is coming, fetched file by file: from the sender's doors first, and with helpers from wherever
     * the offer says its pieces are. Once every file is here the sender is told, which is what it counts as
     * delivered. What cannot be had is tried again on the clock, and the sender told so (see {@link Drop#sayMissing}).
     */
    private static void fetchSending(Context where,NoteStore store,Keys keys,com.eurobuddha.maxima.core.media.MediaService media,
                                     NoteStore.Transfer one) {
        NoteStore.Contact them=store.address(one.address);
        long now=System.currentTimeMillis();
        for(NoteStore.Loose file:one.files) {
            if(file.here||!Drop.due(file.tried,file.tries,now))continue;
            try {
                com.eurobuddha.maxima.core.media.MediaManifest said=com.eurobuddha.maxima.core.media.MediaManifest.decode(file.manifest);
                if(said==null||said.size!=file.bytes||!Enclosure.travels(said.size))throw new IllegalArgumentException("Not a file that can come.");
                int named=said.sources.size();
                said=fromTheirDoor(where,store,said,one.address,!Node.helpers());
                drop("fetching one file (try "+(file.tries+1)+"): "+said.chunkIds.size()+" piece(s), "
                    +(said.sources.size()-(Node.helpers()?named:0))+" of their doors answered, "+named+" relay(s) named in the offer");
                // Nowhere to ask: their door did not answer and the offer names no relay. Said to them now, so they send it up.
                if(said.sources.isEmpty())throw new IllegalStateException(Direct.WAITS);
                byte[] plain=media.fetch(said);
                if(plain.length!=file.bytes)throw new SecurityException("Not the file that was listed.");
                drop(store.looseArrived(file,plain)?"one file fetched and kept, "+plain.length+" bytes":"one file fetched and not kept: no longer wanted");
            } catch(Throwable notNow) {
                int tries=store.looseFailed(file.id);
                drop("one file could not be fetched ("+tries+" tries): "+notNow.getClass().getSimpleName());
                if(Drop.sayMissing(tries,Node.helpers()))
                    try{sayAboutSending(where,store,keys,them,one.wire,one.revision,Receipt.DROP_MISSING);}catch(Exception notNow2){/* said again after more tries */}
            }
        }
        NoteStore.Transfer now2=store.transfer(one.id);
        if(now2==null||now2.state!=Drop.FETCHING||!now2.allHere()){tell(Landed.transfer(null,null));return;}
        store.settle(now2.id,Drop.HERE);
        drop("every file is here: "+Drop.files(now2.files.size())+", "+now2.bytes()+" bytes");
        try{sayAboutSending(where,store,keys,them,one.wire,now2.revision,Receipt.DROP_HAVE);}catch(Exception notNow){/* said again when they offer again */}
        tell(Landed.transfer(Drop.arrived(now2.name,now2.files.size())+".",null));
    }

    /**
     * A sending made here, offered at once rather than at the next round - somebody has just pressed Send and is
     * watching. On the file worker, behind whatever it is doing, so a file is never made ready twice at once.
     * Blocking: the worker calls it.
     *
     * @return what to say about it now
     */
    static String sendNow(final Context where,final NoteStore store,final Keys keys,final String id) throws Exception {
        java.util.concurrent.Future<String> done=FILES.submit(()->{
            NoteStore.Transfer one=store.transfer(id);
            if(one==null)return "";
            com.eurobuddha.maxima.core.media.MediaService media=Node.media(where);
            if(media==null)drop("made: "+Drop.files(one.files.size())+", not offered yet: the node is not up");
            boolean handed=media!=null&&offer(where,store,keys,media,one);
            NoteStore.Contact them=store.address(one.address);
            return Drop.afterSending(one.name,handed,takesFiles(where,them));
        });
        return done.get(3,java.util.concurrent.TimeUnit.MINUTES);
    }

    /** A sending somebody asked about, accepted: fetched from now on. */
    static void takeSending(Context where,NoteStore store,Keys keys,String id) {
        NoteStore.Transfer one=store.transfer(id);
        if(one==null||one.out||one.state!=Drop.ASKING)return;
        store.settle(id,Drop.FETCHING);
        drop("accepted: fetching "+Drop.files(one.files.size()));
        travelSoon(where,store,keys);
    }

    /**
     * A received sending refused, or taken off the list before it had all come: nothing more of it is fetched, and
     * the device that sent it is told - now if it can be reached, and otherwise when it offers again. Blocking.
     */
    static void refuseSending(Context where,NoteStore store,Keys keys,String id) {
        NoteStore.Transfer one=store.transfer(id);
        if(one==null||one.out)return;
        store.forgetTransfer(id);
        drop(one.state==Drop.HERE?"a received sending taken off the list":"refused, or stopped while coming: they are told");
        if(one.state==Drop.HERE)return;
        try{sayAboutSending(where,store,keys,store.address(one.address),one.wire,one.revision,Receipt.DROP_REFUSED);}
        catch(Exception notNow){/* said when it is offered again */}
    }

    /** A sending made here, taken off the list: not offered again, and its copies and pieces let go. Blocking. */
    static void stopSending(Context where,NoteStore store,String id) {
        for(String pieces:store.forgetTransfer(id))Node.forget(where,pieces);
    }

    /**
     * A note id as the sixteen bytes an envelope carries. Every note this app makes is named by a UUID,
     * which is those sixteen bytes written out — so the id goes over the wire as what it already is, and
     * comes back the same on the other side. Anything else was not made here and cannot travel.
     */
    private static byte[] sixteen(String id) {
        java.util.UUID said;
        try{said=java.util.UUID.fromString(id);}
        catch(IllegalArgumentException e) {
            throw new IllegalArgumentException("That note is older than sharing and has no id that travels.");
        }
        java.nio.ByteBuffer out=java.nio.ByteBuffer.allocate(16);
        out.putLong(said.getMostSignificantBits());out.putLong(said.getLeastSignificantBits());
        return out.array();
    }

    /** And back again, so the note that arrives is the same note it left as. */
    private static String idFrom(byte[] sixteen) {
        java.nio.ByteBuffer in=java.nio.ByteBuffer.wrap(sixteen);
        return new java.util.UUID(in.getLong(),in.getLong()).toString();
    }

    /**
     * An address to send to now. A permanent address is not routable itself — it is looked up, which is
     * what makes it survive a host move — so it is resolved before anything is handed to the transport.
     */
    private static String routable(MaximaNode node,String address) throws Exception {
        // Only between the owner's devices, an address is dialled without knowing whose it is only when it is on
        // this network: anywhere else it may be a relay, and a permanent one is a directory to ask.
        if(!Node.helpers()&&!Direct.onThisNetwork(address))throw new IllegalStateException(Direct.WAITS);
        if(!address.startsWith("MAX#"))return address;
        String live=node.resolvePermanent(address);
        if(live==null||live.trim().isEmpty())throw new IllegalStateException(
            "That permanent address could not be looked up, so there is nowhere to send to yet.");
        return live;
    }

    /**
     * Something arrived. Opened, checked against a device we know, and handed to the notebook, which
     * decides what the note says next — see {@link Arriving}.
     *
     * @return what to tell the reader, or null where the message was not ours to read
     */
    static Landed arrived(Context where,NoteStore store,Keys keys,byte[] message){return arrived(where,store,keys,message,false);}

    /**
     * The key the network knows a paired device by, written down from something it sent, where none is written or the
     * one written is not known to the network here. The envelope's signature says which device it is; the transport's,
     * which key it came from. Seen on 0.1.034: a PC linked through a list with a phone that sends only between its
     * owner's devices never had it written - the list gave a relay's address, which that phone does not dial - so the
     * phone did not believe the PC's announcements, never had a way to its door, and kept every answer to it.
     *
     * @return whether it was written down now
     */
    static boolean knownBy(Context where,NoteStore store,byte[] sender,String key) {
        if(sender==null||key==null||key.trim().isEmpty())return false;
        String who=Courier.hex(sender);
        for(NoteStore.Contact one:store.addresses()) {
            if(!one.paired()||!who.equals(fingerprint(one)))continue;
            String kept=one.contact==null?"":one.contact.trim();
            // One written already stays unless the network has no such device: never the reason a node is started.
            if(!kept.isEmpty()&&(Direct.key(kept).equals(Direct.key(key))||!Node.running()||Node.known(where,kept)!=null))return false;
            store.knownAs(one.address,key.trim());
            // Believed from its next announcement, not the next round's.
            Node.pairedWith(Direct.paired(store.addresses()));
            android.util.Log.i("Mininotes/Node","learnt which key a paired device has on the network ("+Direct.tag(key)+"), from what it sent: its announcements are believed now");
            return true;
        }
        return false;
    }

    /**
     * @param brought whether it was carried here by another device rather than coming from its sender: the
     *                sender was not heard from, and may be long gone, and what it carries is only a note or an
     *                answer - never something else to carry
     */
    private static Landed arrived(Context where,NoteStore store,Keys keys,byte[] message,boolean brought) {
        try {
            Envelope.Opened opened=Envelope.open(message,keys.agreement().getPrivate());
            if(!brought)knownBy(where,store,opened.sender,Node.sentBy(message));
            // Somebody taking up an offer, which arrives from a phone this one has never heard of - that
            // is the whole point of it, so it is read before the check that everything else must pass.
            // The keys inside are checked against the signature the envelope was opened with, so the
            // sender is at least who these bytes say. Whether they may have anything is not decided here.
            Hello.Said accepted=Hello.open(opened.text);
            if(accepted!=null) {
                if(!java.util.Arrays.equals(Envelope.fingerprint(Keys.publicKey(accepted.signing)),
                    opened.sender))return new Landed(null,null);
                // Its build takes files sent on their own, which is written down by its key whoever it turns out to be:
                // it matters only once the device is paired here.
                if(accepted.files){takesFiles(where,Courier.hex(opened.sender));drop("a hello says they take files");}
                // A device linking through something both have: settled here, never put to anybody. See Linking.
                if(Linking.isLink(accepted))return linkHeard(where,store,keys,accepted,opened.sender);
                // A plain hello from a device already paired here is its answer to ours: nothing to ask
                // anybody, only to stop saying hello to it.
                if(accepted.target.isEmpty())for(NoteStore.Contact known:store.addresses())
                    if(known.signing.length>0&&java.util.Arrays.equals(Envelope.fingerprint(Keys.publicKey(known.signing)),opened.sender)) {
                        store.answered(known.address);
                        return new Landed(null,null);
                    }
                return new Landed(null,accepted);
            }
            // Who sent it: the envelope names a signing key by its fingerprint, and only a device already
            // paired with counts. An unsigned stranger's note is not put on anybody's shelves.
            NoteStore.Contact from=null;
            for(NoteStore.Contact contact:store.addresses()) {
                if(contact.signing.length==0)continue;
                if(java.util.Arrays.equals(Envelope.fingerprint(Keys.publicKey(contact.signing)),
                    opened.sender)){from=contact;break;}
            }
            if(from==null) {
                android.util.Log.w("Mininotes/Post","dropped an arriving message: signed by no device paired here");
                return new Landed(null,null);
            }
            // Anything signed by a device linked through a list is its answer, whatever it is: it has this device's key
            // to seal for and is plainly there. Waiting for the hello alone left both ends waiting where that one hello
            // went astray while notes were going both ways. See Linking.
            if(store.linkingNow().contains(from.address)) {
                store.answered(from.address);
                link("heard from a device linked through a list: it has answered, and what it is owed goes now");
                final String them=from.address;
                ANSWERS.execute(()->{if(Node.running())owedTo(where,store,keys,them);});
            }
            if(!brought) {
                // Heard from, so there: nothing sent to them for a while is left with anybody else, and
                // whatever was being carried for them goes now.
                final String who=fingerprint(from);
                HEARD.put(who,System.currentTimeMillis());
                if(!store.carried(who).isEmpty())ANSWERS.execute(()->bring(where,store,keys,who));
                Courier.Said carried=Courier.open(opened.text);
                if(carried!=null)return carried(where,store,keys,from,opened,carried);
            } else if(Courier.open(opened.text)!=null) {
                // Something to carry, inside something carried: never. Read as a note, it would be bytes
                // written over somebody's words.
                return new Landed(null,null);
            }
            String id=idFrom(opened.page);
            // An answer: they have this note, at this revision. Which note and which revision are in the
            // envelope's header, sealed and signed with the rest, so nobody in between can say it for them.
            int about=Receipt.open(opened.text);
            if(about!=0)speaks(where,from);
            if(about==Receipt.LOCKED||about==Receipt.OPENED) {
                boolean changed=store.lockedThere(from.address,about==Receipt.LOCKED,opened.moment);
                android.util.Log.i("Mininotes/Post",(about==Receipt.LOCKED?"heard their notebook is locked: what waits for them is taken in when it is opened"
                    :"heard their notebook is open again")+(changed?"":", which changes nothing here"));
                return new Landed(null,null,"",true);
            }
            // Anything else from them says their notebook is open: a locked one takes nothing in and says nothing. Not what
            // another device carried here, which may have been written before it locked.
            if(!brought&&store.lockedThere(from.address,false,opened.moment))
                android.util.Log.i("Mininotes/Post","heard from a device that said its notebook was locked: it is open again");
            if(about==Receipt.ASK) {
                // They are there, and asking. Whatever they are owed goes now rather than when its turn
                // next comes round, which after a long silence can be a quarter of an hour away.
                android.util.Log.i("Mininotes/Post","asked for whatever they are owed");
                final String them=from.address;
                // About one note, where the envelope names one: that note whole, whatever is thought to be
                // owed. About none: whatever they are owed of everything.
                boolean named=false;
                for(byte one:opened.page)if(one!=0)named=true;
                final String which=named?id:null;
                ANSWERS.execute(()->{
                    try {
                        Done done=which!=null
                            ?send(where,store,keys,NoteStore.Branch.Kind.PAGE,which,them,true)
                            :send(where,store,keys,NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING,them);
                        android.util.Log.i("Mininotes/Post","sent what they asked for: "+done.sent+" went, "
                            +done.failed+" did not");
                    } catch(Exception notNow) {
                        android.util.Log.w("Mininotes/Post","could not send what they asked for: "
                            +notNow.getClass().getSimpleName());
                    }
                });
                return new Landed(null,null);
            }
            Sharing.Scope leftAt=Receipt.leftScope(about);
            if(leftAt!=null) {
                // When they left rides where a note's revision does: sealed and signed with the rest.
                boolean any=store.left(from.address,id,leftAt,opened.revision);
                android.util.Log.i("Mininotes/Post","they have left a "+leftAt.name().toLowerCase(java.util.Locale.ROOT)
                    +(any?"":" - which they were not on here, so it changes nothing"));
                if(!any)return new Landed(null,null);
                return Landed.left(from.name+" unfollowed a "+(leftAt==Sharing.Scope.PAGE?"note":"collection")+".",id);
            }
            Sharing.Scope offAt=Receipt.removedScope(about);
            if(offAt!=null) {
                // Somebody says this phone is off a thing of theirs. Whether they may say so is the
                // notebook's to check; when they decided it rides where a revision does.
                boolean any=store.takenOff(from.address,id,offAt,opened.revision);
                android.util.Log.i("Mininotes/Post","they say this phone is off a "+offAt.name().toLowerCase(java.util.Locale.ROOT)
                    +(any?"":" - which changes nothing here"));
                if(!any)return new Landed(null,null);
                return Landed.left(from.name+" removed you from a "+(offAt==Sharing.Scope.PAGE?"note":"collection")
                    +". Your copy stays on this phone.",id);
            }
            if((about==Receipt.COLLECTED||about==Receipt.COLLECTED_ANSWER)&&!brought) {
                // They have what was being carried for them: that note, or that answer, up to that revision.
                int gone=store.collected(fingerprint(from),Courier.hex(opened.page),
                    about==Receipt.COLLECTED_ANSWER?Courier.ANSWER:Courier.NOTE,opened.revision);
                android.util.Log.i("Mininotes/Post","collected: "+gone+" thing(s) carried for them let go");
                return new Landed(null,null);
            }
            if(about==Receipt.TAKES_FILES) {
                // A build that takes files on their own, and says so. This one says it back - unless it did within the
                // last minute - so a device just updated hears about this one at once rather than when this one next
                // starts. Only once a run, it was lost for good when this one said it first, to a build from before.
                String who=fingerprint(from);
                takesFiles(where,who);
                boolean back=Drop.sayBack(toldFilesAt(who),System.currentTimeMillis());
                if(back){final NoteStore.Contact them=from;ANSWERS.execute(()->tellTakesFiles(where,store,keys,them));}
                // Whatever was waiting for them to say so goes now, not when its turn next comes round.
                boolean waiting=store.wakeSendings(from.address);
                drop("heard they take files"+(back?"; said it back":"")+(waiting?"; what waited for them is offered now":""));
                if(waiting)travelSoon(where,store,keys);
                return new Landed(null,null);
            }
            if(about==Receipt.PERSONS||about==Receipt.PERSONS_MINE) {
                // A build that knows about persons, saying whether it counts this device as its owner's. Said back
                // as "takes files" is, and whatever card they have had from here goes again: they may be just started.
                // Only news is answered: the same word again within minutes is two devices answering each other.
                String who=fingerprint(from);
                long now=System.currentTimeMillis();
                long[] heard=HEARD_PERSONS.put(who,new long[]{about,now});
                boolean anew=Persons.news(heard==null?0:(int)heard[0],heard==null?0:heard[1],about,now);
                readsCards(where,who,about==Receipt.PERSONS_MINE);
                if(anew)CARDS.remove(who);
                long[] told=TOLD_PERSONS.get(who);
                boolean back=anew&&Drop.sayBack(told==null?0:told[1],now);
                if(back){final NoteStore.Contact them=from;ANSWERS.execute(()->tellPersons(where,store,keys,them));}
                persons("heard their build knows about persons"+(about==Receipt.PERSONS_MINE?", and counts this device as the owner's":"")
                    +(anew?"":", as they said a moment ago")+(back?"; said it back":""));
                if(anew&&from.mine&&about==Receipt.PERSONS_MINE)ANSWERS.execute(()->sendCards(where,store,keys));
                return new Landed(null,null);
            }
            if(about==Receipt.TREE) {
                // A build that knows about trees, and says so. Said back as persons is, and - the first time it is heard
                // - whatever waited for it goes now, off this thread: what does not fit three levels, and collections on
                // their own.
                String who=fingerprint(from);
                boolean first=trees(where,who);
                long[] told=TOLD_TREES.get(who);
                boolean back=Drop.sayBack(told==null?0:told[0],System.currentTimeMillis());
                final NoteStore.Contact them=from;
                if(back)ANSWERS.execute(()->tellTrees(where,store,keys,them));
                android.util.Log.i("Mininotes/Post","heard their build knows about trees"+(first?"":", as it said before")
                    +(back?"; said it back":""));
                if(first)ANSWERS.execute(()->{
                    // Something just arrived, so the node is up; where it is not, nothing is started for this.
                    if(!Node.running())return;
                    try {
                        Done done=send(where,store,keys,NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING,them.address);
                        android.util.Log.i("Mininotes/Post","sent what waited for their build to know about trees: "
                            +done.sent+" went, "+done.failed+" did not");
                    } catch(Exception notNow) {
                        android.util.Log.w("Mininotes/Post","could not send what waited for trees: "+notNow.getClass().getSimpleName());
                    }
                });
                // The first time, what waited for it to be updated waits no more: the marks and the words about them are
                // asked again at once, before anything has gone.
                return first?Landed.people(""):new Landed(null,null);
            }
            if(about==Receipt.DROP_HAVE||about==Receipt.DROP_REFUSED||about==Receipt.DROP_MISSING)
                return answeredAboutTransfer(where,store,keys,from,id,opened.revision,about);
            if(about==Receipt.FILE_HERE||about==Receipt.FILE_MISSING) {
                // About a file, which the envelope names where it names a note: see Enclosure.
                if(about==Receipt.FILE_HERE) {
                    // Believed of one still being fetched here too: see NoteStore.fileReached.
                    String note=store.noteOfFile(id);
                    boolean kept=store.fileReached(id,from.address);
                    android.util.Log.i("Mininotes/Post","they have a file"+(kept?"":" - which is not known here, so it changes nothing"));
                    if(note==null)return new Landed(null,null);
                    moved(note);
                    return Landed.people(note);
                }
                NoteStore.Held file=store.file(id);
                if(file==null)return new Landed(null,null);
                // Only between the owner's devices nothing goes up: the pieces are here, and they come from this door.
                boolean again=Node.helpers()&&store.upAgain(id);
                android.util.Log.i("Mininotes/Post","they cannot get a file"+(again?", so it goes up again":" - it went up a moment ago, so it is left as it is"));
                if(again)travelSoon(where,store,keys);
                return new Landed(null,null);
            }
            if(about!=0&&about!=Receipt.HAVE&&about!=Receipt.TOOK)return new Landed(null,null);   // a later build
            if(about!=0) {
                boolean believed=store.acknowledged(from.address,id,opened.revision,about==Receipt.TOOK);
                // Not a note: a collection that went on its own, which the envelope names by the bytes Things gives it.
                if(!believed&&store.get(id)==null)
                    believed=store.collectionAcknowledged(from.address,opened.page,opened.revision,about==Receipt.TOOK);
                android.util.Log.i("Mininotes/Post","answered: revision "+opened.revision
                    +(about==Receipt.TOOK?", and they now say the same":", and they put it with their own")
                    +(believed?"":" - which this phone never sent, so it changes nothing"));
                return new Landed(null,null,id,believed);
            }
            // A card from another device of the owner's: see Persons. Known by its first four bytes and read before
            // a note is, which it would otherwise be taken for; never something carried, which is only notes and answers.
            if(Persons.isCard(opened.text))return brought?new Landed(null,null):cardArrived(where,store,keys,from,opened);
            // Files sent to this device on their own, belonging to no note: see Drop. Read before a note is, which
            // these bytes would otherwise be taken for.
            List<Enclosure.Listed> offer=Drop.open(opened.text);
            if(offer!=null)return offered(where,store,keys,from,id,opened.revision,offer);
            // A collection on its own: see Carton. Read before a note is too, which it would otherwise be taken for.
            Carton.Sent carton=Carton.open(opened.text);
            if(carton!=null)return brought?new Landed(null,null):cartonArrived(where,store,keys,from,opened,carton);
            Parcel.Sent parcel=Parcel.open(opened.text);
            // Their build carries: from now on they may be left things, and brought them.
            if(parcel!=null&&parcel.carries)carries(where,from);
            // Something sent before notes carried their shelf: the text and nothing else. Still readable,
            // and it goes wherever anything whose shelf we were not told goes.
            if(parcel==null) {
                String text=new String(opened.text,StandardCharsets.UTF_8);
                int line=text.indexOf('\n');
                parcel=new Parcel.Sent("","","","",line<0?"":text.substring(0,line),
                    line<0?text:text.substring(line+1),false);
            }
            // Unsubscribed. They can still send it; this phone has stopped taking it in, and says nothing
            // about a thing somebody asked not to hear about again.
            NoteStore.Refusal refused=store.refusal(from.address,id,parcel);
            if(refused!=null&&refused.gone&&givenAgain(store,parcel,refused.at)) {
                // Left, and then given it again: the later decision stands, as it does for anybody.
                android.util.Log.i("Mininotes/Post","given again what this phone had left, so it is taken in");
                store.followAgain(from.address,id,refused);
                refused=null;
            }
            if(refused!=null) {
                // Nothing is said to the person, who asked not to hear. The log is told, because from
                // the outside this looks exactly like a note that never arrived.
                android.util.Log.i("Mininotes/Post",refused.gone
                    ?"not taken in: this phone has left it, and they are told again"
                    :"not taken in: this phone has unsubscribed from it");
                // They had not heard, or it went before they did. Said again, or they go on sending.
                if(refused.gone&&doesSpeak(where,from))
                    tellOff(where,store,keys,from,opened.page,refused.at,Receipt.left(refused.scope()));
                return new Landed(null,null);
            }
            // Who else has it, folded in before the note itself: the list is what says whether this phone
            // may hand it on, and it should not be one note behind the thing it describes.
            store.tookMembership(from.address,id,parcel);
            // And everybody on it this device is not linked with, linked through it - where the list came from a
            // device with a say in who has the thing. See Linking.
            linkThrough(where,store,keys,from.address,id,parcel);
            // Something of theirs is here, so whatever was accepted has been answered and need not be
            // said again.
            store.answered(from.address);
            // What they may do with it here decides what becomes of what they sent. A reader's words are
            // not written down, and neither are those of somebody taken off - who is told again, since
            // they plainly had not heard. Both are answered all the same, or they would send the same
            // thing every quarter of an hour for ever, which is the fault Pause still has.
            Sharing.Rule may=store.standingOf(from.address,id,parcel);
            if(may==null||!may.level.writes()) {
                android.util.Log.i("Mininotes/Post","not taken in: "+(may==null?"they were never given this"
                    :may.level==Sharing.Level.GONE?"they were taken off this, and are told again":"they may only read this"));
                if(parcel.answer){speaks(where,from);answer(where,store,keys,from,opened.page,opened.revision,false);}
                if(may!=null&&may.level==Sharing.Level.GONE&&store.saysWhoHas(may.scope,may.target)&&doesSpeak(where,from))
                    tellOff(where,store,keys,from,opened.page,may.changed,Receipt.removed(store.oldScopeOf(may.scope,may.target)));
                return Landed.people(id);
            }
            // What the note said before, so that one which arrives saying the same - sent only to carry
            // a change in who may do what - is not announced as "updated a note".
            NoteStore.Note before=store.get(id);
            boolean same=before!=null&&before.body.equals(parcel.body)
                &&(before.title==null?"":before.title).equals(parcel.title==null?"":parcel.title);
            Arriving.Decision said=store.landed(id,from.address,opened.revision,parcel,store.someBook());
            // The files it keeps at their end, now that the note is here to keep them with: what is not here
            // yet is fetched in the background, and what came from them and is no longer listed goes. A list
            // not said - an older build, or one that did not fit - changes nothing.
            if(parcel.files!=null) {
                if(store.listed(id,from.address,parcel.filesAsOf,parcel.files))travelSoon(where,store,keys);
                moved(id);
                // And what of theirs is here already, said to them - see NoteStore.haveHere.
                sayWeHave(where,store,keys,from,store.haveHere(id,parcel.files));
            }
            // Written down, so now it can be said. Whatever was decided about it - taken, put together,
            // or set aside as older than what is here - they are told that what they sent has arrived:
            // that is the question they asked. Only a phone that asked is answered; see Parcel.answer.
            if(parcel.answer) {
                speaks(where,from);
                // Whether this phone's note now says exactly what they sent - which is what lets them
                // count this revision as one both phones have, and not only one that arrived.
                boolean took=said.what==Arriving.What.NEW||said.what==Arriving.What.NEWER;
                answer(where,store,keys,from,opened.page,opened.revision,took);
            }
            // Nothing in it changed. The marks and the box are asked again, because who may do what can have.
            if(same&&said.what!=Arriving.What.MERGED)return Landed.people(id);
            switch(said.what) {
                case NEW: return new Landed(from.name+" shared a note with you.",null,id);
                case NEWER: return new Landed(from.name+" updated a note.",null,id);
                case MERGED: return new Landed(said.keepTheirs
                    ? from.name+" wrote in the same place you did. Both are in the note."
                    : from.name+" wrote in a note you had also written in. Both are in it.",null,id);
                default: return new Landed(null,null);
            }
        } catch(Exception e) {
            // A message that cannot be opened is not ours, or has been tampered with. Either way it is
            // dropped in silence: saying so would tell a stranger their guess was close. Silence towards
            // them, that is. This phone's own log is told what kind of thing went wrong, because a fault
            // of this app's own making lands here too and would otherwise look like nothing arriving.
            android.util.Log.w("Mininotes/Post","dropped an arriving message: "+e.getClass().getSimpleName());
            return new Landed(null,null);
        }
    }
}
