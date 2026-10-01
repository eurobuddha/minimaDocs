// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Notes and collections, nested as deep as anybody likes (see docs/HOME.md).
 *
 * <p>There were three fixed levels: a collection held books and a book held notes. Now there are two kinds of
 * thing, and a collection holds either, at any depth. Home is the collection everything is in; it has no row
 * of its own, and whatever sits on it names {@link #HOME} as its parent.
 *
 * <p>What the tree means - where a thing is, how deep, whether a move would put a collection inside itself, and
 * how the tree looks to a device from before trees, which knows three levels and nothing else - is worked out
 * here from a map of each thing's parent, so it is unit tested without a device.
 */
final class Things {
    /** The parent of everything on Home. Not a thing: nothing is called this. */
    static final String HOME="HOME";
    /** How far up a walk goes before it takes the tree for a loop. Nobody nests sixty-four collections. */
    static final int DEEPEST=64;
    /** How many favourites the phone's dock holds; the rest are in Favourites (see docs/HOME.md, decision 3). */
    static final int DOCK_PHONE=5;

    private Things(){}

    /**
     * The collections above a thing, from the one on Home down to the one it is in; empty for a thing on Home.
     *
     * <p>A parent that is not in the map ends the walk, as Home does: what is above it is not known here, so the
     * thing is taken to be as high as what is known. A loop - which nothing here makes, but a notebook is written
     * by more than this code - ends it too, before anything repeats.
     */
    static List<String> above(Map<String,String> parents,String id) {
        List<String> up=new ArrayList<>();
        Set<String> seen=new HashSet<>();seen.add(id);
        String at=parents.get(id);
        while(at!=null&&!HOME.equals(at)&&!at.isEmpty()&&up.size()<DEEPEST&&seen.add(at)) {
            up.add(at);
            if(!parents.containsKey(at))break;
            at=parents.get(at);
        }
        Collections.reverse(up);
        return up;
    }

    /** The collections above a thing and then the thing itself: what a sharing rule is looked for along. */
    static List<String> path(Map<String,String> parents,String id) {
        List<String> all=above(parents,id);all.add(id);
        return all;
    }

    /** How deep a thing sits: 1 on Home, 2 in a collection on Home, and so on. */
    static int depth(Map<String,String> parents,String id){return above(parents,id).size()+1;}

    /** Whether a thing is {@code container} or anywhere inside it. */
    static boolean within(Map<String,String> parents,String id,String container) {
        if(id.equals(container))return true;
        return above(parents,id).contains(container);
    }

    /**
     * Whether a thing may go into {@code into}: Home, or a collection that is not the thing itself and not inside
     * it. Anything may go into a collection; nothing goes into a note.
     *
     * @param intoCollection whether {@code into} is a collection (Home is one)
     */
    static boolean mayGoInto(Map<String,String> parents,String moved,String into,boolean intoCollection) {
        if(into==null||moved==null)return false;
        if(HOME.equals(into))return true;
        if(!intoCollection)return false;
        return !within(parents,into,moved);
    }

    // ---- the tree as a device from before trees sees it -------------------------------------------------

    /**
     * Where a note stands in the three levels a 0.1 device knows - its collection and its book - or null where it
     * does not fit them: only a note two collections down is a note in a book in a collection.
     *
     * @param above the collections above the note, from Home down
     */
    static String[] threeLevels(List<String> above) {
        return above!=null&&above.size()==2?new String[]{above.get(0),above.get(1)}:null;
    }

    /** Whether a thing fits the three levels: a collection on Home or one down, or a note two down. */
    static boolean fitsThreeLevels(List<String> above,boolean note) {
        if(above==null)return false;
        return note?above.size()==2:above.size()<=1;
    }

    /**
     * What a 0.1 device calls the level a thing is at: a collection on Home is a collection, one inside that a
     * book, a note that fits a note; anything else it has no word for, and null says so.
     */
    static Sharing.Scope oldScope(List<String> above,boolean note) {
        if(!fitsThreeLevels(above,note))return null;
        if(note)return Sharing.Scope.PAGE;
        return above.isEmpty()?Sharing.Scope.COLLECTION:Sharing.Scope.BOOK;
    }

    /**
     * The two shelf fields a parcel has always carried, for a reader that knows nothing else: the nearest two
     * collections above the note, {collection, book}, empty where there are fewer (decision 15). A note that fits
     * three levels gets exactly its collection and its book.
     */
    static String[] nearestTwo(List<String> above) {
        int n=above==null?0:above.size();
        return new String[]{n>=2?above.get(n-2):"",n>=1?above.get(n-1):""};
    }

    // ---- what travels ------------------------------------------------------------------------------------

    /**
     * The sixteen bytes an envelope names a thing by. A note's id is a UUID and is its own bytes; a collection's may
     * be one, or the first collection's fixed name, or an id made from somebody else's (see {@link Parcel#localId}),
     * and those are named by the first sixteen bytes of their SHA-256 (decision 16). Never a note's bytes by chance:
     * a hash lands on a UUID someone made about as often as two UUIDs do.
     */
    static byte[] envelopeId(String id) {
        try {
            java.util.UUID said=java.util.UUID.fromString(id);
            java.nio.ByteBuffer out=java.nio.ByteBuffer.allocate(16);
            out.putLong(said.getMostSignificantBits());out.putLong(said.getLeastSignificantBits());
            return out.array();
        } catch(IllegalArgumentException notOne) {
            try {
                byte[] digest=MessageDigest.getInstance("SHA-256").digest(("thing\0"+id).getBytes(StandardCharsets.UTF_8));
                byte[] out=new byte[16];System.arraycopy(digest,0,out,0,16);
                return out;
            } catch(Exception noDigest){throw new IllegalStateException("SHA-256 is missing",noDigest);}
        }
    }

    /** Which of these ids an envelope's sixteen bytes name, or null for none. */
    static String named(byte[] envelope,java.util.Collection<String> ids) {
        if(envelope==null||envelope.length!=16)return null;
        for(String id:ids)if(java.util.Arrays.equals(envelopeId(id),envelope))return id;
        return null;
    }

    // ---- the dock ------------------------------------------------------------------------------------------

    /** The favourites that go into a dock with room for {@code room}, in their order: the first ones. */
    static List<String> dock(List<String> favourites,int room) {
        if(favourites==null||room<=0)return new ArrayList<>();
        return new ArrayList<>(favourites.subList(0,Math.min(room,favourites.size())));
    }

    /**
     * The dock after one thing is put at a place in it, 1 the first: taken from wherever it was, put there - at the end,
     * for a place past the end - and everything from there on moved along one. What no longer fits in {@code room} is
     * not in what comes back, and so leaves the dock: the last first, as a phone's dock pushes its last icon out.
     */
    static List<String> intoDock(List<String> dock,String thing,int slot,int room) {
        List<String> now=new ArrayList<>(dock==null?new ArrayList<>():dock);
        now.removeIf(one->one.equals(thing));
        now.add(Math.max(0,Math.min(slot-1,now.size())),thing);
        return new ArrayList<>(now.subList(0,Math.max(0,Math.min(room,now.size()))));
    }

    /** The dock after one thing leaves it: the rest close up, in their order. */
    static List<String> outOfDock(List<String> dock,String thing) {
        List<String> now=new ArrayList<>(dock==null?new ArrayList<>():dock);
        now.removeIf(one->one.equals(thing));
        return now;
    }

    // ---- words -----------------------------------------------------------------------------------------------

    /** What a collection holds, in a few words: "3 collections · 2 notes", "5 notes", or "Empty". */
    static String holds(int collections,int notes) {
        String inner=collections==1?"1 collection":collections+" collections";
        String pages=notes==1?"1 note":notes+" notes";
        if(collections>0&&notes>0)return inner+" · "+pages;
        if(collections>0)return inner;
        return notes>0?pages:"Empty";
    }
}
