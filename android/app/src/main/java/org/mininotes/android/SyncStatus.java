package org.mininotes.android;

import java.time.*;
import java.time.format.DateTimeFormatter;

/** Read persisted evidence, never the time a relay accepted a parcel. */
final class SyncStatus {
    static final String ONLY_HERE="Only here",WAITING="Waiting for delivery",MATCHING="Waiting for matching versions",
        CONFIRMED="Sync confirmed",NOT_CONFIRMED="Sync not confirmed",UNAVAILABLE="Note unavailable";
    record State(long saved,long confirmed,String status) {
        /** One quiet line, the same marks as the tree: ✓ everybody has it, ↑ somebody is waiting. */
        String brief(String device){return brief(device,ZonedDateTime.now());}
        String brief(String device,ZonedDateTime now) {
            return switch(status) {
                case ONLY_HERE -> "Only on "+device+" · saved "+shortly(saved,now);
                case CONFIRMED -> "✓ Synced "+shortly(confirmed,now);
                case WAITING, NOT_CONFIRMED -> "↑ Saved "+shortly(saved,now)+" · waiting to sync";
                case MATCHING -> "↑ Saved "+shortly(saved,now)+" · waiting for the others";
                default -> status;
            };
        }
        /** Whether something written here has not reached everybody yet: worth saying before a tab closes. */
        boolean unsent(){return status.equals(WAITING)||status.equals(NOT_CONFIRMED)||status.equals(MATCHING);}
        /** Everything, exactly: for a tooltip or a tap. */
        String detail(String device) {
            return "Saved on "+device+": "+date(saved)
                +"\n"+(status.equals(CONFIRMED)?"Everyone it is shared with has this version: "+date(confirmed)
                    :status.equals(ONLY_HERE)?"Not shared.":status+".")
                +"\n\nTimes are local to "+device+". Devices that are offline may have newer edits. Files kept with the note, up to 16 MB each, go with it, and those kept with a collection go with the collection; larger ones stay on this device.";
        }
    }
    private static String date(long millis) {
        return DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss z")
            .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }
    /** "14:05" today, "23 Sep, 14:05" this year, "23 Sep 2025" before. */
    static String shortly(long millis,ZonedDateTime now) {
        ZonedDateTime at=Instant.ofEpochMilli(millis).atZone(now.getZone());
        String pattern=at.toLocalDate().equals(now.toLocalDate())?"HH:mm":at.getYear()==now.getYear()?"d MMM, HH:mm":"d MMM yyyy";
        return DateTimeFormatter.ofPattern(pattern).format(at);
    }
    /**
     * One device a note reaches, as the line under its title shows it: a round with their initial, tinted by
     * where they stand.
     *
     * @param linked   whether this device is paired with them: a person only named in the list that came with the
     *                 note can be sent nothing from here, and their round says so
     * @param listedIn for somebody not linked, the name of the thing whose list names them; empty otherwise
     * @param listing  for somebody not linked, the rule that lists them, to take them off by; null otherwise
     */
    record Person(String address,String name,SyncMark mark,boolean linked,String listedIn,Sharing.Rule listing,boolean linking,String says) {
        Person(String address,String name,SyncMark mark){this(address,name,mark,true,"",null,false,"");}
        Person(String address,String name,SyncMark mark,boolean linked,String listedIn,Sharing.Rule listing) {
            this(address,name,mark,linked,listedIn,listing,false,"");
        }
        Person(String address,String name,SyncMark mark,boolean linked,String listedIn,Sharing.Rule listing,boolean linking) {
            this(address,name,mark,linked,listedIn,listing,linking,"");
        }
        /** Where they stand, in the words a tap on their round shows: the writing and the files (see Waits.person). */
        String standing(){return says==null||says.isEmpty()?mark.person():says;}
        /** What a tap on their round says: who, then where they stand, in plain words. */
        String called(){return name.isEmpty()?"A paired device":name;}
        /** What the round of somebody not linked says: being linked through the list and waiting for them, or not at all. */
        String notLinked(String here){return linking?Unsent.linking(called()):Unsent.notLinked(called(),listedIn,here);}
    }

    /**
     * What to call a device, the best thing known first: the name it was paired under here, then the name a list
     * of people gave it, then a short form of its key ("device 3F9A21") - never "?" or "A device", which leave
     * the owner asking which one.
     *
     * @param key     the key it signs with, as kept; empty where not known, and the address stands in
     */
    static String named(String paired,String listed,String key,String address) {
        if(paired!=null&&!paired.isBlank())return paired.trim();
        if(listed!=null&&!listed.isBlank())return listed.trim();
        String from=key!=null&&!key.isBlank()?key.trim():address==null?"":address.trim();
        if(from.isEmpty())return "";
        byte[] print=Sha3.of(from.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex=new StringBuilder("device ");
        for(int i=0;i<3;i++)hex.append(String.format(java.util.Locale.ROOT,"%02X",print[i]&0xff));
        return hex.toString();
    }

    /** Everybody a note reaches and where each stands with it, in the order they were given it. */
    static java.util.List<Person> who(NoteStore store,String id) {
        java.util.List<Person> out=new java.util.ArrayList<>();
        var each=store.whereEach(store.pagesUnder(NoteStore.Branch.Kind.PAGE,id)).get(id);
        java.util.Map<String,Waits.Where> where=each==null?java.util.Map.of():each;
        java.util.Map<String,SyncMark> owed=new java.util.LinkedHashMap<>();
        for(var one:where.entrySet())owed.put(one.getKey(),one.getValue().mark());
        // Everybody the note reaches, not only those this device sends to: on a note somebody else shared,
        // the one who shared it is sent nothing from here, and the line showed nobody at all. A device this one
        // does not send to is where this copy came from, so it has what is here.
        java.util.Set<String> seen=new java.util.LinkedHashSet<>(owed.keySet());
        var note=store.get(id);
        java.util.List<Sharing.Rule> rules=store.shares();
        // Along the whole path: a rule on any collection above it, however deep, reaches it.
        if(note!=null)seen.addAll(Sharing.audience(rules,store.pathOf(id)).keySet());
        // Linked through the list and not answered yet: nothing goes to them, so their round says so. See Linking.
        java.util.Set<String> linking=store.linkingNow();
        for(String address:seen) {
            NoteStore.Contact known=store.address(address);
            boolean waiting=known!=null&&known.paired()&&linking.contains(address);
            if(known!=null&&known.paired()&&!waiting) {
                String name=store.nameFor(address);
                out.add(new Person(address,name,owed.getOrDefault(address,SyncMark.GONE),true,"",null,false,
                    Waits.person(name.isEmpty()?"A paired device":name,where.get(address),kept(address))));
                continue;
            }
            // Named in the list and never paired here: which list, so the round can say where to take them off.
            Sharing.Rule listing=null;
            for(Sharing.Rule rule:rules)if(rule.address.equals(address)&&Sharing.covers(rule,store.pathOf(id))){listing=rule;break;}
            String listedIn=listing==null?"":store.thingName(NoteStore.kindFor(listing.scope),listing.target);
            out.add(new Person(address,store.nameFor(address),owed.getOrDefault(address,SyncMark.GONE),false,listedIn,listing,waiting));
        }
        return out;
    }

    /**
     * How many answers to a device are kept here for want of a way to it (see Outbox.Answers): told by Post, which
     * keeps them, so that the words about a mark can say so. None where Post has kept nothing.
     */
    static volatile java.util.function.ToIntFunction<String> kept=address->0;
    private static int kept(String address){try{return Math.max(0,kept.applyAsInt(address));}catch(RuntimeException notNow){return 0;}}

    /**
     * What an amber mark is waiting for, in words, for the box a press on it opens: each device the thing reaches,
     * with its writing and its files added up over every note under it (see Waits).
     */
    static String waits(NoteStore store,NoteStore.Branch.Kind kind,String id) {
        var pages=store.pagesUnder(kind,id);
        java.util.Map<String,Waits.Where> sum=new java.util.LinkedHashMap<>();
        for(var note:store.whereEach(pages).values())
            for(var one:note.entrySet())sum.merge(one.getKey(),one.getValue(),Waits.Where::and);
        // And each collection in it that waits to go on its own - its look, its files - to a device that has to be
        // updated first: "Ana's phone needs to update Mininotes to receive this" (see Unsent.Why.NEEDS_UPDATE).
        for(var waiting:store.cartonsForUpdate(kind,id).values())
            for(String address:waiting)sum.merge(address,Waits.needsUpdate(),Waits.Where::and);
        java.util.List<Waits.Device> devices=new java.util.ArrayList<>();
        for(var one:sum.entrySet())devices.add(new Waits.Device(store.nameFor(one.getKey()),one.getValue(),kept(one.getKey())));
        return Waits.said(devices,kind==NoteStore.Branch.Kind.PAGE?1:pages.size());
    }

    /** The mark on the line under a note's title: the worst of where its people stand (see SyncMark.of). */
    static SyncMark mark(NoteStore store,String id,java.util.List<Person> people,boolean unsaved) {
        java.util.List<SyncMark> all=new java.util.ArrayList<>();
        for(Person one:people)all.add(one.mark());
        return SyncMark.of(store.pausedHere(NoteStore.Branch.Kind.PAGE,id),store.sharedAtAll(NoteStore.Branch.Kind.PAGE,id),
            unsaved,SyncMark.worst(all));
    }

    /** The names of everybody a note reaches, in the order they were given it. */
    static java.util.List<String> names(NoteStore store,String id) {
        java.util.List<String> out=new java.util.ArrayList<>();
        var note=store.get(id);if(note==null)return out;
        for(String address:Sharing.audience(store.shares(),store.pathOf(id)).keySet()) {
            String name=store.nameFor(address);
            if(name.isEmpty())name="a paired device";
            if(!out.contains(name))out.add(name);
        }
        return out;
    }

    static State read(NoteStore store,String id) {
        var db=store.getReadableDatabase();db.beginTransaction();
        try {
            var note=store.get(id);
            if(note==null)return new State(0,0,UNAVAILABLE);
            var audience=Sharing.audience(store.shares(),store.pathOf(id));
            if(audience.isEmpty())return new State(note.updated,0,
                store.sharedAtAll(NoteStore.Branch.Kind.PAGE,id)?NOT_CONFIRMED:ONLY_HERE);
            if(!store.owed(NoteStore.Branch.Kind.PAGE,id).isEmpty())
                return new State(note.updated,0,WAITING);
            long latest=0;
            for(String address:audience.keySet()) {
                try(var row=db.query("sent",new String[]{"revision","agreed","at"},"address=? AND page=?",
                    new String[]{address,id},null,null,null,"1")) {
                    if(!row.moveToFirst()||row.getLong(0)!=note.revision||row.getLong(1)!=note.revision)
                        return new State(note.updated,0,MATCHING);
                    latest=Math.max(latest,row.getLong(2));
                }
            }
            return new State(note.updated,latest,latest>0?CONFIRMED:NOT_CONFIRMED);
        } finally {db.setTransactionSuccessful();db.endTransaction();}
    }
}
