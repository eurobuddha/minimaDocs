// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.util.ArrayList;
import java.util.List;

/**
 * What an amber mark is waiting for, said in words: which device, and whether it is the writing or the files.
 *
 * <p>The mark used to be the whole of it. Seen on 0.1.030: three of the owner's devices, a note in sync by every
 * log, the arrow amber on all three, and nothing on the screen said for whom or for what. Now pressing the mark
 * says what waits and for whom before sending, and a person's round says how far they are with the files.
 *
 * <p>Holds no Android or Swing types: both apps say the same words, and they are unit tested.
 */
final class Waits {
    private Waits(){}

    /**
     * Where one device stands with one note, or with everything under a book or a collection added up.
     *
     * @param owed      notes whose latest writing it has not answered for
     * @param lacking   files that can go that it has not said it has
     * @param files     files that can go to it at all: not too big, not empty, not its own
     * @param confirmed it has said every note is the same as here
     * @param stuck     owed, and not heard from for too long: see {@link SyncMark#stuck}
     * @param locked    it has said its notebook is locked (see {@link Receipt#LOCKED}): what waits for it is taken in
     *                  when it is opened, and nobody here can do anything about that
     * @param update    things that wait for it only because its build is from before collections nested - a note that
     *                  does not fit three levels, a collection going on its own with its look and its files - and go
     *                  the moment it says it knows about trees (see {@link Unsent.Why#NEEDS_UPDATE}). Not owed as well:
     *                  sending them again changes nothing until it is updated.
     */
    record Where(int owed,int lacking,int files,boolean confirmed,boolean stuck,boolean locked,int update) {
        Where(int owed,int lacking,int files,boolean confirmed,boolean stuck){this(owed,lacking,files,confirmed,stuck,false,0);}
        Where(int owed,int lacking,int files,boolean confirmed,boolean stuck,boolean locked){this(owed,lacking,files,confirmed,stuck,locked,0);}
        /**
         * A device that is locked, with something waiting for it, is grey and not amber: amber asks for something to be
         * done, and there is nothing to do but open it. Red still wins - days of it is worth knowing - and one that
         * already has everything is as green as ever. What waits for it to be updated is amber, locked or not: opening
         * it will not take that in, and somebody has to update it.
         */
        SyncMark mark() {
            SyncMark said=SyncMark.person(owed>0||update>0,lacking>0,confirmed,stuck);
            return locked&&update==0&&(said==SyncMark.WAITING||said==SyncMark.SENT)?SyncMark.PAUSED:said;
        }
        /** Whether it is locked and something waits for it: what its round and the words say instead. */
        boolean waitsLocked(){return mark()==SyncMark.PAUSED;}
        /** Two notes' worth, added up, for a book or a collection. */
        Where and(Where other) {
            return other==null?this:new Where(owed+other.owed,lacking+other.lacking,files+other.files,
                confirmed&&other.confirmed,stuck||other.stuck,locked||other.locked,update+other.update);
        }
    }

    /** What waits for one device only because it has not been updated: one thing, and nothing else about it known. */
    static Where needsUpdate(){return new Where(0,0,0,false,false,false,1);}

    /** What is said of a device that is locked while something waits for it. */
    static String locked(String name){return name+" is locked; it takes it in when opened";}

    /** One device, by the name to call it, where it stands, and how many answers to it are kept here for want of a way. */
    record Device(String name,Where where,int kept) {}

    private static String files(int n){return n==1?"1 file":n+" files";}

    /**
     * Where one person stands, for the box a press on their round opens: the writing, then the files, in words.
     * Their name is the box's title, so it is said here only where the sentence needs it.
     */
    static String person(String name,Where where,int kept) {
        String said;
        if(where==null)said=SyncMark.GONE.person();
        else if(where.waitsLocked())said=locked(name)+".";
        else if(where.stuck())said=SyncMark.STUCK.person()+(where.lacking()>0?" So "+(where.lacking()==1?"is 1 file":"are "+files(where.lacking()))+".":"");
        else if(where.owed()>0&&where.lacking()>0)
            said="Your latest changes, and "+where.lacking()+" of "+files(where.files())+", have not reached them yet.";
        else if(where.owed()>0)said=SyncMark.WAITING.person();
        else if(where.lacking()>0)said=name+" has the text; "+where.lacking()+" of "+files(where.files())+" still going.";
        else if(where.update()>0)said=Unsent.needsUpdate(name)+".";
        else if(!where.confirmed())said=SyncMark.SENT.person();
        else said=where.files()>0?"Has this version, files included.":SyncMark.GONE.person();
        // And what waits for them to be updated, said as well where something else was said first.
        if(where!=null&&where.update()>0&&!said.startsWith(Unsent.needsUpdate(name)))said=said+" "+Unsent.needsUpdate(name)+".";
        return kept>0?said+" An answer to them waits until they are in reach.":said;
    }

    /**
     * What an amber mark waits for, one line a device, the one needing something most first: those not heard from,
     * then those that need updating, then writing not with them, then files, then answers kept here. Empty where nothing
     * is known to wait - words still on the screen, which {@link #UNSAVED} says.
     *
     * @param notes how many notes it is about: a note's own mark says "your changes", a book's says how many notes
     */
    static List<String> lines(List<Device> devices,int notes) {
        List<String> stuck=new ArrayList<>(),update=new ArrayList<>(),owed=new ArrayList<>(),files=new ArrayList<>(),kept=new ArrayList<>(),
            sent=new ArrayList<>(),locked=new ArrayList<>();
        for(Device one:devices) {
            Where at=one.where();String name=one.name()==null||one.name().isBlank()?"a paired device":one.name();
            if(one.kept()>0)kept.add("Waiting to reach "+name+" with "+(one.kept()==1?"an answer":one.kept()+" answers"));
            // Said once, and last: nothing waits on anybody here for it.
            if(at!=null&&at.waitsLocked()){locked.add(locked(name.equals("a paired device")?"A paired device":name));continue;}
            // Nothing goes to a build from before trees that it cannot hold: what waits for that waits for its owner to update it.
            if(at!=null&&at.update()>0)update.add(Unsent.needsUpdate(name.equals("a paired device")?"A paired device":name));
            if(at!=null&&at.stuck())stuck.add(name+" has not been heard from for more than three days");
            else if(at!=null&&at.owed()>0)owed.add("Waiting to reach "+name+" with "+(notes>1?"changes to "+(at.owed()==1?"1 note":at.owed()+" notes"):"your changes"));
            if(at!=null&&at.lacking()>0)files.add("Waiting for "+name+" to confirm "+files(at.lacking()));
            if(at!=null&&at.owed()==0&&at.lacking()==0&&at.update()==0&&!at.confirmed()&&!at.stuck())sent.add("Sent to "+name+", waiting for them to say they have it");
        }
        List<String> out=new ArrayList<>(stuck);out.addAll(update);out.addAll(owed);out.addAll(files);out.addAll(kept);out.addAll(sent);out.addAll(locked);
        return out;
    }

    /** Said where the mark is amber only because what is on the screen has not been written down yet. */
    static final String UNSAVED="Your latest words go as soon as they are written down.";

    /** The box a press on an amber mark opens: its title, and the one thing to press. */
    static final String TITLE="Waiting to go", SEND="Send now";

    /** The whole of the box: one line a device, or what the screen is waiting for where no device is. */
    static String said(List<Device> devices,int notes) {
        List<String> all=lines(devices,notes);
        return all.isEmpty()?UNSAVED:String.join(".\n",all)+".";
    }
}
