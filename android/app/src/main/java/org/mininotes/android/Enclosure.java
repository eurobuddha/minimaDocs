// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The files kept with a shared note, and what each device does about them.
 *
 * <p>A Maxima message is a message, not a transfer, so a file never rides in one. It is sealed once under a
 * key of its own, cut into pieces named by their own hash, and the pieces are left on this device and on a
 * couple of relays - the transport's media service does that. What travels inside the note's own envelope is
 * the list of the note's files and, for each one that has gone up, the few hundred bytes that say where the
 * pieces are and the key that opens them. Holding a piece without that is holding noise, so the list is as
 * private as the note is, and never goes anywhere else.
 *
 * <p>The list is the whole of what one device says to another about files: this is what the note keeps at
 * my end. The receiving device fetches what it does not have yet and takes out what came from that device
 * and is no longer listed. What it added itself is never taken out by somebody else's list, and what it took
 * out itself is not fetched back.
 *
 * <p>Holds no Android types: the decisions are unit tested.
 */
final class Enclosure {
    /**
     * The most one file may be and still travel: the transport's own ceiling for what a user-hosted mesh can
     * carry (MediaService.MAX_MESH_FILE_BYTES). Above it the relays cannot keep a copy in time, and the phone
     * that sent it would be the only place it lived. Such a file stays here, and says so.
     */
    static final long MOST=16L*1024*1024;

    /** The most files one note's list names. A note, not a folder. */
    static final int FILES_MOST=64;

    /** Whether a file of this size goes at all. */
    static boolean travels(long bytes){return bytes>0&&bytes<=MOST;}

    /**
     * Whether an id that arrived can name a file here. It becomes the file's name in the pad's own folder,
     * so only the plain ids this app makes are taken: never a path.
     */
    static boolean plainId(String id){return id!=null&&Attachment.idOf(Attachment.entry(id))!=null;}

    /** One file as the list names it. The manifest is empty while it has not gone up, or cannot. */
    static final class Listed {
        final String id,name,kind,manifest; final long bytes;
        Listed(String id,String name,String kind,long bytes,String manifest) {
            this.id=id==null?"":id;this.name=name==null?"":name;this.kind=kind==null?"":kind;
            this.bytes=bytes;this.manifest=manifest==null?"":manifest;
        }
        /** Whether there is anywhere to fetch it from. */
        boolean fetchable(){return !manifest.isEmpty()&&travels(bytes)&&plainId(id);}
        /** The same file, named but not yet said where to find. */
        Listed unsaid(){return new Listed(id,name,kind,bytes,"");}
    }

    /** What one arriving list asks of this device. */
    static final class Plan {
        /** Listed, fetchable, and nowhere here yet: to be fetched. */
        final List<Listed> fetch=new ArrayList<>();
        /** Came from that device, and it no longer lists them: to be taken out. */
        final List<String> drop=new ArrayList<>();
        /** Waiting to be fetched from that device, and no longer listed: forgotten. */
        final List<String> forget=new ArrayList<>();
        /** Already waiting, and now said where to find - or somewhere new. */
        final List<Listed> refresh=new ArrayList<>();
    }

    /**
     * What a list from one device means here.
     *
     * @param listed      what that device's note keeps
     * @param fromThem    ids of the files on this note here that came from that device
     * @param here        ids of every file this device keeps, anywhere, whoever it came from
     * @param waiting     ids waiting to be fetched for this note from that device
     * @param declined    ids this device took out itself, which are never fetched back
     */
    static Plan plan(List<Listed> listed,Collection<String> fromThem,Collection<String> here,
                     Collection<String> waiting,Collection<String> declined) {
        Plan plan=new Plan();
        Set<String> named=new HashSet<>();
        for(Listed one:listed) {
            if(!plainId(one.id)||!named.add(one.id))continue;
            if(here.contains(one.id)||declined.contains(one.id))continue;
            if(waiting.contains(one.id)){if(one.fetchable())plan.refresh.add(one);continue;}
            if(one.fetchable())plan.fetch.add(one);
        }
        for(String id:fromThem)if(!named.contains(id))plan.drop.add(id);
        for(String id:waiting)if(!named.contains(id))plan.forget.add(id);
        return plan;
    }

    /**
     * Whether a file is tried again now - fetched, or sent up. After a minute, two, four, eight, a quarter of
     * an hour; then hourly from the tenth try, because a file nobody can reach for an afternoon is waiting on
     * something the sender has to do, and asking every quarter of an hour would only spend the battery.
     */
    static boolean due(long tried,int tries,long now) {
        if(tries<=0)return true;
        if(now<tried)return true;   // a clock put back: better once too often than never again
        long minute=60_000L;
        long wait=tries>=10?60*minute:tries>=5?15*minute:minute<<(tries-1);
        return now-tried>=wait;
    }

    /** How many times a note is sent again to somebody who has not said they have its files. */
    static final int TELLS_MOST=6;

    /**
     * Whether the list goes again to somebody who has not said they have a file. Only a few times: a device
     * on a build from before files travelled never says so, and would otherwise be told for ever. After that
     * the file says it is not with everybody yet, and Sync sends it again.
     */
    static boolean tellAgain(long told,int tells,long now) {
        if(tells>=TELLS_MOST)return false;
        return due(told,tells,now);
    }

    /** Every fourth failed fetch, the device that listed it is told it cannot be had from where it said. */
    static boolean sayMissing(int tries){return tries>0&&tries%4==0;}

    /** How long a file stays up before somebody who cannot get it may make this device send it up again. */
    static final long FRESH=10L*60*1000;

    /** Words for one file's card, or empty where there is nothing to say. */
    static final String TOO_BIG="Too big to send", EMPTY="Empty, not sent", NOT_SENT="Not sent yet",
        NOT_EVERYBODY="Not with everybody yet", WAITING="Waiting for the other device";

    /**
     * What one file's card says about where it is.
     *
     * @param shared  whether the note reaches anybody
     * @param up      whether it has gone up and can be fetched
     * @param lacking how many of the note's people have not said they have it
     */
    static String state(long bytes,boolean shared,boolean up,int lacking){return state(bytes,shared,up,lacking,false);}

    /**
     * @param onlyMine whether notes travel only between the owner's devices. Then a file that is ready has gone
     *                 nowhere: its pieces are on this device, and it is had only once the other device has come
     *                 to this one's door for them - so until then it is waiting for them, not on its way.
     */
    static String state(long bytes,boolean shared,boolean up,int lacking,boolean onlyMine) {
        if(!shared)return "";
        if(bytes>MOST)return TOO_BIG;
        if(bytes<=0)return EMPTY;
        if(!up)return NOT_SENT;
        return lacking<=0?"":onlyMine?WAITING:NOT_EVERYBODY;
    }

    private Enclosure(){}
}
