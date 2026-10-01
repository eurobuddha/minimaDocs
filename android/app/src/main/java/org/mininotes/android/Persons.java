// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One person, on every device they own.
 *
 * <p>Every device was somebody on its own: a phone and a PC of the same person were two strangers who had
 * been told to trust each other, each with a name that had to say both who the person is and which device
 * it is - "Charles" on one list, "Pixel 7 Pro" on another, and neither right in both places. So a device
 * now has two names, and its owner one id. <b>Your name</b> is what other people see, and is the same on all
 * of your devices; <b>this device</b> is what your own devices call this one, and nobody else ever sees it.
 *
 * <p>The id is sixteen random bytes made once per install, with when it was made. Two devices share one only
 * when both have said the other is theirs - the "Connect my other device" bond, or the switch in People - and
 * when two such devices hold different ids, the one made earlier is kept (the lower id where both were made
 * at the same moment) and the other device keeps its old id as an alias. It is a rule both devices can apply
 * without asking each other, and apply again later, and come to the same answer.
 *
 * <p>What travels between your own devices is the card: who you are, your name and when you chose it, and
 * every device of yours this one knows - its keys, its address, its name, whether it is still yours, and when
 * that was decided. A card goes only to a device that is yours at both ends and whose build has said it reads
 * cards ({@link Receipt#PERSONS_MINE}); a build from before would read these bytes as a note, and somebody
 * else's device has no business knowing your devices. Per device, the later decision stands, so a card that
 * arrives late changes nothing.
 *
 * <p>Holds no Android types: the format, its bounds and every decision are unit tested.
 */
final class Persons {
    /** The format, and the first four bytes. Not a {@link Parcel}, {@link Hello}, {@link Drop} or {@link Courier}. */
    static final byte[] MAGIC={'M','N','A','1'};
    /** One byte after the magic, so a later build can say more without being read as this one. */
    static final int FORMAT=1;

    /** How long an id is, in bytes. */
    static final int ID=16;
    /** Ids this person was known by before, kept so a card still saying one is known for what it is. */
    static final int ALIASES_MOST=16;
    /** More devices than anybody owns, and few enough that a card always fits one envelope. */
    static final int DEVICES_MOST=32;
    static final int NAME_MOST=Hello.NAME_MOST, ADDRESS_MOST=Hello.ADDRESS_MOST, KEY_MOST=200;

    /** Where a device of yours stands: yours, or yours no longer. */
    static final int ACTIVE=0, GONE=1;

    private Persons(){}

    /** Who owns this device, as this device keeps it: the id, the names, and when each was decided. */
    static final class Me {
        final String id; final long made; final List<String> aliases;
        /** Your name, and when it was chosen: 0 while nobody has chosen one and the phone's own name stands in. */
        final String name; final long named;
        /** This device's own name among your devices, and when it was chosen: 0 for the maker's name. */
        final String device; final long deviceNamed;
        Me(String id,long made,List<String> aliases,String name,long named,String device,long deviceNamed) {
            this.id=id==null?"":id;this.made=made;this.aliases=aliases==null?new ArrayList<>():new ArrayList<>(aliases);
            this.name=name==null?"":name;this.named=named;this.device=device==null?"":device;this.deviceNamed=deviceNamed;
        }
        Me withName(String name,long named){return new Me(id,made,aliases,name,named,device,deviceNamed);}
        /** Whether a card or a hello naming this id is about this person. */
        boolean is(String other){return other!=null&&!other.isEmpty()&&(other.equals(id)||aliases.contains(other));}
    }

    /** One device of yours, as a card says it. */
    static final class Device {
        final byte[] signing,agreement; final String address,name; final int state; final long decided;
        Device(byte[] signing,byte[] agreement,String address,String name,int state,long decided) {
            this.signing=signing==null?new byte[0]:signing;this.agreement=agreement==null?new byte[0]:agreement;
            this.address=address==null?"":address;this.name=name==null?"":name;this.state=state;this.decided=decided;
        }
    }

    /** Everything one device of yours says to another about you and your devices. */
    static final class Card {
        final String person; final long made; final List<String> aliases;
        final String name; final long named; final List<Device> devices;
        Card(String person,long made,List<String> aliases,String name,long named,List<Device> devices) {
            this.person=person;this.made=made;this.aliases=aliases==null?new ArrayList<>():aliases;
            this.name=name==null?"":name;this.named=named;this.devices=devices==null?new ArrayList<>():devices;
        }
        /**
         * The newest decision on it, which rides where a revision goes: a card is only ever about decisions, and
         * one made earlier than what a device has already heard from the same sender is not news.
         */
        long newest() {
            long newest=Math.max(named,made);
            for(Device one:devices)newest=Math.max(newest,one.decided);
            return newest;
        }
    }

    // ---- ids ------------------------------------------------------------------------------------------------

    /** A new id, made once per install. */
    static String newId(SecureRandom random) {
        byte[] id=new byte[ID];(random==null?new SecureRandom():random).nextBytes(id);
        return Courier.hex(id);
    }

    /** Whether this is an id as this class makes them: thirty-two hex digits. */
    static boolean isId(String id) {
        if(id==null||id.length()!=ID*2)return false;
        for(int at=0;at<id.length();at++)if(Character.digit(id.charAt(at),16)<0)return false;
        return true;
    }

    static byte[] idBytes(String id) {
        if(!isId(id))throw new IllegalArgumentException("Not an id");
        byte[] out=new byte[ID];
        for(int at=0;at<ID;at++)out[at]=(byte)Integer.parseInt(id.substring(at*2,at*2+2),16);
        return out;
    }

    /**
     * Whether the first of two ids is the one both devices keep: the one made earlier, and where both were made at
     * the same moment, the lower. Either device works it out alone and gets the same answer.
     */
    static boolean wins(String id,long made,String other,long otherMade) {
        if(made!=otherMade)return made<otherMade;
        return id.compareTo(other)<0;
    }

    /** A key as one form, whichever form it came in, so two can be compared. Empty where it is not a key. */
    static String key(byte[] raw) {
        if(raw==null||raw.length==0)return "";
        try{return Base64.getEncoder().encodeToString(Point.read(raw).getEncoded());}
        catch(Exception notAKey){return "";}
    }

    // ---- the card -------------------------------------------------------------------------------------------

    /** The inside of a card. */
    static byte[] wrap(Card card) {
        if(card.devices.isEmpty()||card.devices.size()>DEVICES_MOST)throw new IllegalArgumentException("One to "+DEVICES_MOST+" devices on a card");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)) {
            out.write(MAGIC);out.writeByte(FORMAT);
            out.write(idBytes(card.person));out.writeLong(card.made);
            List<String> aliases=bounded(card.aliases,card.person);
            out.writeByte(aliases.size());
            for(String one:aliases)out.write(idBytes(one));
            put(out,card.name.getBytes(StandardCharsets.UTF_8),NAME_MOST);out.writeLong(card.named);
            out.writeByte(card.devices.size());
            for(Device one:card.devices) {
                put(out,one.signing,KEY_MOST);put(out,one.agreement,KEY_MOST);
                put(out,one.address.getBytes(StandardCharsets.UTF_8),ADDRESS_MOST);
                put(out,one.name.getBytes(StandardCharsets.UTF_8),NAME_MOST);
                out.writeByte(one.state==GONE?GONE:ACTIVE);out.writeLong(one.decided);
            }
        } catch(IOException e){throw new IllegalArgumentException(e.getMessage(),e);}
        return bytes.toByteArray();
    }

    /** Whether these bytes say they are a card, before anything else is asked of them. */
    static boolean isCard(byte[] said) {
        if(said==null||said.length<MAGIC.length)return false;
        for(int at=0;at<MAGIC.length;at++)if(said[at]!=MAGIC[at])return false;
        return true;
    }

    /**
     * What a card says, or null where these bytes are not one - or are, but broken, out of bounds, with no device,
     * a device without keys, or anything after the last device. Half a card is not a card.
     */
    static Card open(byte[] said) {
        if(!isCard(said)||said.length>Envelope.MAX_TEXT)return null;
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(said,MAGIC.length,said.length-MAGIC.length))) {
            if(in.readUnsignedByte()!=FORMAT)return null;
            byte[] id=new byte[ID];in.readFully(id);
            long made=in.readLong();
            int many=in.readUnsignedByte();
            if(many>ALIASES_MOST)return null;
            List<String> aliases=new ArrayList<>(many);
            for(int at=0;at<many;at++){byte[] one=new byte[ID];in.readFully(one);aliases.add(Courier.hex(one));}
            String name=new String(get(in,NAME_MOST),StandardCharsets.UTF_8).trim();
            long named=in.readLong();
            int count=in.readUnsignedByte();
            if(count<1||count>DEVICES_MOST)return null;
            List<Device> devices=new ArrayList<>(count);
            for(int at=0;at<count;at++) {
                byte[] signing=get(in,KEY_MOST),agreement=get(in,KEY_MOST);
                String address=new String(get(in,ADDRESS_MOST),StandardCharsets.UTF_8).trim();
                String called=new String(get(in,NAME_MOST),StandardCharsets.UTF_8).trim();
                int state=in.readUnsignedByte();
                long decided=in.readLong();
                if(signing.length==0||agreement.length==0||state>GONE)return null;
                devices.add(new Device(signing,agreement,address,called,state,decided));
            }
            if(in.available()!=0)return null;
            return new Card(Courier.hex(id),made,aliases,name,named,devices);
        } catch(IOException|IllegalArgumentException broken){return null;}
    }

    /** The aliases a card can carry: never the id itself, never twice, and never more than fit. */
    private static List<String> bounded(List<String> aliases,String id) {
        Set<String> kept=new LinkedHashSet<>();
        for(String one:aliases)if(isId(one)&&!one.equals(id))kept.add(one);
        List<String> all=new ArrayList<>(kept);
        // The newest are the ones a card still in flight is most likely to say, so those are kept.
        return all.size()>ALIASES_MOST?new ArrayList<>(all.subList(all.size()-ALIASES_MOST,all.size())):all;
    }

    private static void put(DataOutputStream out,byte[] bytes,int most) throws IOException {
        if(bytes.length>most)throw new IOException("Too long to send: "+bytes.length+" of "+most);
        out.writeShort(bytes.length);out.write(bytes);
    }

    private static byte[] get(DataInputStream in,int most) throws IOException {
        int length=in.readUnsignedShort();
        if(length>most)throw new IllegalArgumentException("A field said it was "+length+" long");
        byte[] bytes=new byte[length];in.readFully(bytes);
        return bytes;
    }

    // ---- who is sent one ------------------------------------------------------------------------------------

    /**
     * Whether a card goes to a device: one marked as yours here, that has said its build reads cards, and that has
     * said this device is its owner's too. Anybody else's device never learns what your devices are.
     */
    static boolean goesTo(boolean mineHere,boolean readsCards,boolean mineThere){return mineHere&&readsCards&&mineThere;}

    /** The card this device sends: you, and every device of yours it knows, itself first. */
    static Card card(Me me,Device self,List<Device> others) {
        List<Device> all=new ArrayList<>();all.add(self);
        Set<String> seen=new java.util.HashSet<>();seen.add(key(self.signing));
        for(Device one:others) {
            if(all.size()>=DEVICES_MOST)break;
            String which=key(one.signing);
            if(which.isEmpty()||!seen.add(which)||one.agreement.length==0)continue;
            all.add(one);
        }
        // The others in the order of their keys, not of the table they were read from: the same devices make the
        // same card, whatever order they were paired in.
        all.subList(1,all.size()).sort((one,other)->key(one.signing).compareTo(key(other.signing)));
        return new Card(me.id,me.made,me.aliases,me.name,me.named,all);
    }

    /**
     * What makes one card the same as another for sending it again: everything on it but the addresses. A card is
     * sent again only when this changes; nothing on the receiving end reads an address off a card yet, and a device's
     * address moves whenever its node is given another relay, which sent the same card round every time it did.
     */
    static byte[] sameness(Card card) {
        List<Device> bare=new ArrayList<>();
        for(Device one:card.devices)bare.add(new Device(one.signing,one.agreement,"",one.name,one.state,one.decided));
        return wrap(new Card(card.person,card.made,card.aliases,card.name,card.named,bare));
    }

    /** How long the same word from a device about persons is not news: as long as it is not said again unasked. */
    static final long NEWS_AFTER=10L*60*1000;

    /**
     * Whether "this build knows about persons" from a device is news: the first this run, a change of which of the
     * two it said (the switch in People, turned there), or the first in ten minutes - a device that has just started.
     * Only news is said back and has the card sent again. Said back each time, two devices whose words took over a
     * minute to cross went on answering each other, and each answer sent the card again.
     *
     * @param said what it said before this run, or 0 for nothing; {@code at} when
     */
    static boolean news(int said,long at,int now,long when){return said==0||said!=now||when<at||when-at>=NEWS_AFTER;}

    /**
     * What People and devices says under a device where only one end counts the other as the owner's, or null where
     * both do, neither does, or its build has not said. Names go between your own devices only where both ends say so,
     * and a switch turned at one end only is the likeliest reason one device's names never reach another.
     *
     * @param mineHere   whether it is marked "My device" here
     * @param readsCards whether its build has said it knows about persons
     * @param mineThere  whether it said it counts this device as its owner's
     * @param thisOne    what this device is: "phone" or "PC"
     */
    static String oneSided(boolean mineHere,boolean readsCards,boolean mineThere,String thisOne) {
        if(!readsCards||mineHere==mineThere)return null;
        return mineThere
            ?"It counts this "+thisOne+" as one of your devices. Turn My device on here too so names travel."
            :"It does not count this "+thisOne+" as one of your devices. Turn My device on there too so names travel.";
    }

    /**
     * Said under a device not marked as yours here that a device yours at both ends lists as yours. Its name comes
     * from that device's card all the same; the switch is what lets notes go both ways and its own card be believed.
     */
    static final String LISTED_AS_YOURS="Another of your devices counts it as one of yours. Turn My device on here too.";

    // ---- what a device does with one ------------------------------------------------------------------------

    /** What a card that arrived changes here. */
    static final class Taken {
        /** Who owns this device after it: the same, or a new id with the old one kept as an alias, or a later name. */
        final Me me;
        /** Whether this device took the other's id, and whether it took your name from the card. */
        final boolean adopted,renamed;
        /** Devices already paired here, listed as yours on the card, whose name it decided later, by their key in one form. */
        final Map<String,Device> names;
        Taken(Me me,boolean adopted,boolean renamed,Map<String,Device> names){this.me=me;this.adopted=adopted;this.renamed=renamed;this.names=names;}
    }

    /**
     * A card, weighed. Null where it changes nothing because it is not to be believed: from a device not marked as
     * yours here, or one that does not list itself, or one that does not list this device as yours.
     *
     * @param mineHere whether the device it came from is marked as yours here
     * @param from     the key the envelope was signed with
     * @param self     this device's own signing key
     * @param known    the devices paired here, by key in one form, and when each one's name was decided: marked as yours
     *                 or not, since a card from a device yours at both ends is the owner's word on which are theirs
     */
    static Taken take(Me me,Card card,boolean mineHere,byte[] from,byte[] self,Map<String,Long> known) {
        if(card==null||!mineHere)return null;
        String sender=key(from),here=key(self);
        if(sender.isEmpty()||here.isEmpty()||sender.equals(here))return null;
        boolean speaks=false,listsMe=false;
        for(Device one:card.devices) {
            String which=key(one.signing);
            if(which.equals(sender)&&one.state==ACTIVE)speaks=true;
            if(which.equals(here)&&one.state==ACTIVE)listsMe=true;
        }
        if(!speaks||!listsMe)return null;
        Me now=me;boolean adopted=false,renamed=false;
        if(isId(card.person)&&!card.person.equals(me.id)&&!me.aliases.contains(card.person)) {
            if(!isId(me.id)||wins(card.person,card.made,me.id,me.made)) {
                // Theirs was made first: this device becomes the same person, and keeps who it was as an alias.
                List<String> aliases=new ArrayList<>(me.aliases);
                if(isId(me.id))aliases.add(me.id);
                aliases.addAll(card.aliases);
                now=new Me(card.person,card.made,bounded(aliases,card.person),me.name,me.named,me.device,me.deviceNamed);
                adopted=true;
            }
        } else if(card.person.equals(me.id)) {
            // The same person already: what either was known by before is known by both.
            List<String> aliases=new ArrayList<>(me.aliases);aliases.addAll(card.aliases);
            now=new Me(me.id,me.made,bounded(aliases,me.id),me.name,me.named,me.device,me.deviceNamed);
        }
        // Your name, chosen later on another device of yours, is your name here too.
        if(card.named>now.named&&!card.name.isEmpty()){now=now.withName(card.name,card.named);renamed=true;}
        Map<String,Device> names=new HashMap<>();
        for(Device one:card.devices) {
            String which=key(one.signing);
            // This device's own name is its own to decide, and a device never paired here is not written down from a card.
            if(which.equals(here)||one.name.isEmpty()||!known.containsKey(which))continue;
            if(later(one.decided,known.get(which),which.equals(sender)))names.put(which,one);
        }
        return new Taken(now,adopted,renamed,Collections.unmodifiableMap(names));
    }

    /**
     * Whether a device's name as decided at {@code decided} replaces the one decided at {@code had}. The later
     * decision stands; where they were made at the same moment - both never chosen, say - a device naming itself
     * is believed about itself.
     */
    static boolean later(long decided,long had,boolean itself){return decided>had||decided==had&&itself;}

    /**
     * What a hello from a device just bonded as yours says about its owner, weighed as a card is: whether this
     * device takes its id, and your name where it was chosen there later. Only a hello that says it knows about
     * persons counts, and only a bond - the caller's to decide - makes it one of yours.
     */
    static Me bonded(Me me,Hello.Said them) {
        if(them==null||!them.persons||!isId(them.person))return me;
        Me now=me;
        if(!me.is(them.person)&&(!isId(me.id)||wins(them.person,them.personMade,me.id,me.made))) {
            List<String> aliases=new ArrayList<>(me.aliases);
            if(isId(me.id))aliases.add(me.id);
            now=new Me(them.person,them.personMade,bounded(aliases,them.person),me.name,me.named,me.device,me.deviceNamed);
        }
        return them.named>now.named&&!them.yourName.isEmpty()?now.withName(them.yourName,them.named):now;
    }

    /** The aliases as one line, for keeping. */
    static String aliases(List<String> aliases){return String.join(",",bounded(aliases,""));}

    /** And back. */
    static List<String> aliases(String kept) {
        List<String> all=new ArrayList<>();
        if(kept!=null)for(String one:kept.split(","))if(isId(one.trim()))all.add(one.trim());
        return all;
    }
}
