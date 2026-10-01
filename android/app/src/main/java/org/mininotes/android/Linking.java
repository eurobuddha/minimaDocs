// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Linked through what you share.
 *
 * <p>The owner's decision, 28 September 2026: as soon as a device is linked to a thing, it is linked through that
 * thing to every other device that has it. Until then a device could seal only for devices it had paired with, so in a
 * note A shared with B and C - A paired with both, B and C never with each other - what B wrote never reached C, and
 * both said "waiting for …" under a dashed round for ever.
 *
 * <p>So the list of people that travels with a note carries, for each device on it, the key to seal for it as well
 * as the key it signs with (see {@link Parcel.Member#agreement}). A device holding such a list, from a device that has
 * a say in who has the thing, links with everybody on it it is not linked with yet: writes them down as a contact
 * ({@code via} the list), meets them on the network, and says a hello of this kind - asking to be answered. Nothing
 * of the thing goes to them until they answer, so a build from before this, which cannot answer, is sent nothing it
 * would drop.
 *
 * <p>The other end takes such a hello from a device it is not linked with only where a list of its own names that
 * device's key: both sides have to hold the thing. A stranger who knows an address and a key to seal for is not a
 * member of anything here, and is not taken. See docs/SHARING.md, Linked through what you share.
 *
 * <p>Holds no Android types: the rules are unit tested.
 */
final class Linking {
    private Linking(){}

    /**
     * The scope a hello names when it is a device linking through a shared thing: never one a share can have, so a
     * build from before, reading it, finds no offer of its own it names and does nothing.
     */
    static final String SCOPE="LINK";
    /**
     * And the thing it names: never empty, because a build from before pairs back a hello that names nothing
     * without asking anybody - which would make a half contact of a device it cannot answer.
     */
    static final String TARGET="list";
    /** What the device list says of a device that came in a list rather than by a code: {@code addresses.via}. */
    static final String VIA="list";

    /** Whether a hello is a device linking through something both have. */
    static boolean isLink(Hello.Said said){return said!=null&&SCOPE.equals(said.scope);}

    /**
     * Whether it asks to be answered. A hello that answers is never answered, so two devices each linking with the
     * other say one hello each way and stop. The flag rides where an offer says whether it writes.
     */
    static boolean asks(Hello.Said said){return isLink(said)&&said.writes;}

    /** A hello of this kind, from this device: asking, or answering. */
    static Hello.Said hello(String name,String address,byte[] agreement,byte[] signing,boolean asking) {
        return new Hello.Said(name,address,agreement,signing,SCOPE,TARGET,asking);
    }

    /**
     * Who to link with, out of a list of people that arrived with a note: every device on it at any level but
     * taken off, that is not this one, not linked here already, and that the list says where to reach and gives a
     * key to seal for. None at all where the list came from a device with no say in who has the thing - its owner or
     * an admin of it - since a list anybody on it could write is a list anybody could add a stranger to.
     *
     * @param hasASay whether the device the list came from may say who has the thing here
     * @param mine    this device's key, as lists write it
     * @param linked  the keys, as lists write them, of every device this one is paired or linked with
     */
    static List<Parcel.Member> toLink(List<Parcel.Member> members,boolean hasASay,String mine,Set<String> linked) {
        List<Parcel.Member> out=new ArrayList<>();
        if(!hasASay||members==null)return out;
        Set<String> seen=new HashSet<>();
        for(Parcel.Member one:members) {
            if(one==null||one.key.isEmpty()||one.key.equals(mine)||linked!=null&&linked.contains(one.key))continue;
            if(one.level<=Sharing.Level.GONE.said()||one.address.trim().isEmpty())continue;
            // Both keys have to be keys: one to check what they sign, one to seal for.
            if(Persons.key(one.agreement).isEmpty()||!one.key.equals(Persons.key(signing(one))))continue;
            if(seen.add(one.key))out.add(one);
        }
        return out;
    }

    /** The key a member signs with, as bytes; empty where the list wrote something that is not one. */
    static byte[] signing(Parcel.Member one) {
        try{return Base64.getDecoder().decode(one.key);}catch(IllegalArgumentException notAKey){return new byte[0];}
    }

    /**
     * The one question asked of a hello of this kind from a device not linked here: which list of people, for a
     * thing still here, names the key it signs with - taken off or not at all is no. The envelope it came in was
     * signed by that key (the hello is refused before this where it was not), so this is the device the list means.
     * Null where none does, and then it is not taken.
     *
     * @param rules every row of every list here, for things that are still here
     */
    static Sharing.Rule names(List<Sharing.Rule> rules,byte[] signing) {
        String key=Persons.key(signing);
        if(key.isEmpty()||rules==null)return null;
        for(Sharing.Rule rule:rules)if(rule.level!=Sharing.Level.GONE&&key.equals(rule.key))return rule;
        return null;
    }

    /**
     * Whether to ask a device again that has not answered: after a minute, two, four, eight and sixteen, and then
     * every half hour. A device that is off, or out of reach, is not dialled every round.
     */
    static boolean due(long tried,int tries,long now) {
        if(tries<=0||now<tried)return true;
        long wait=tries>=6?30L*60*1000:(1L<<(tries-1))*60*1000;
        return now-tried>=wait;
    }

    /**
     * What People and devices says under a device that came in a list: through what - or, once it is on nothing of
     * this device's any more, that nothing goes to it.
     */
    static String through(String listedIn) {
        return listedIn==null||listedIn.isBlank()?"Linked through something it no longer has. Nothing is sent to it."
            :"Linked through “"+listedIn.trim()+"”";
    }
}
