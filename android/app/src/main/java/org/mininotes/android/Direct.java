// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import com.eurobuddha.maxima.core.MaximaNode;
import com.eurobuddha.maxima.core.MaximaSender;
import com.eurobuddha.maxima.core.codec.MiniData;
import com.eurobuddha.maxima.core.contacts.Contact;
import com.eurobuddha.maxima.core.identity.MxAddress;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Straight to a device when there is a way to it, and through a relay only when there is not. See
 * docs/DIRECT.md, phase 1.
 *
 * <p>Every note used to go through a public relay, even between a PC and a phone on the same Wi-Fi, and to
 * every address a device had, relays included, even when a direct one had just taken it. Two things here
 * change that:
 * <ul>
 * <li><b>Saying where this device is, on the local network.</b> Each device broadcasts a few hundred bytes
 *     every round: its Maxima identity key, the port of its door ({@code DirectEndpoint}), the local address
 *     it said it from, a time, and a signature over all of it with that same identity key. No note, no name.
 *     A device that hears one takes it only from a device it is paired with, only when the signature holds,
 *     only when it came from the address it names, and only when it is fresh.</li>
 * <li><b>Direct first.</b> A note goes to a device's local address when one was heard, then to any public
 *     address the device has proved it can be reached at, then - since phase 2 - to the device's home, a PC
 *     it collects from (see {@link Home}). The first that takes it is the whole send. Only when none does does
 *     it go the way it always went: every address, relays included.</li>
 * </ul>
 *
 * <p>Why the identity key signs: it is the key a paired device is already known by on the transport - what
 * the pairing recorded as its contact - and the one its door opens messages with, so the key that says
 * "I am here" is the key a note to that place is sealed for. Somebody who replays an announcement from
 * another address is refused, because the address is inside what was signed; somebody who replays it from
 * the same address within two minutes says nothing that was not already true.
 *
 * <p>A local address taking a note is not the note arriving, any more than a relay taking it was: the far
 * device still answers when it has written it down (see {@link Receipt}), and what is not answered is sent
 * again, as before. So a device on the network that pretends to be the door costs time, never a note.
 *
 * <p>Holds no Android types: the format, its bounds and the order of trying are unit tested.
 */
final class Direct {
    /** The door's port, and the port announcements are heard on: the next free one is used if it is taken. */
    static final int PORT=9601;
    /** The first four bytes of an announcement. */
    static final byte[] MAGIC={'M','N','L','A'};
    static final int VERSION=1;
    /** Said in front of what is signed, so this signature can never be taken for one the key made for anything else. */
    private static final byte[] CONTEXT="mininotes/nearby/1".getBytes(StandardCharsets.US_ASCII);
    /** Bounds on what is read off the network: an identity key is 162 bytes and its signature 128. */
    static final int MOST=1200, MOST_KEY=600, MOST_SIGNATURE=600;
    /** How old, or how far ahead, an announcement may be. Two devices' clocks are never quite the same. */
    static final long FRESH=2L*60*1000;
    /** Connecting to a door that is not there fails this fast, and the relays are tried after it. */
    static final int LEASH=5000;

    private Direct(){}

    // ---- where this device is, said on the local network -------------------------------------------------

    /** One announcement heard and believed: whose it is, and where their door is. */
    record Heard(String key,String where) {}

    /** This device's announcement, made from one of its local addresses. */
    static byte[] announce(KeyPair identity,byte[] ip,int port,long now) throws GeneralSecurityException {
        byte[] key=identity.getPublic().getEncoded();
        if(ip==null||(ip.length!=4&&ip.length!=16)||port<1||port>65535)throw new IllegalArgumentException("Not an address.");
        byte[] body=body(key,ip,port,now);
        Signature signer=Signature.getInstance("SHA256withRSA");
        signer.initSign(identity.getPrivate());signer.update(CONTEXT);signer.update(body);
        byte[] signature=signer.sign();
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(DataOutputStream data=new DataOutputStream(out)) {
            data.write(body);data.writeShort(signature.length);data.write(signature);
        } catch(IOException never){throw new IllegalStateException(never);}
        return out.toByteArray();
    }

    private static byte[] body(byte[] key,byte[] ip,int port,long when) {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(DataOutputStream data=new DataOutputStream(out)) {
            data.write(MAGIC);data.writeByte(VERSION);
            data.writeShort(key.length);data.write(key);
            data.writeByte(ip.length);data.write(ip);
            data.writeShort(port);data.writeLong(when);
        } catch(IOException never){throw new IllegalStateException(never);}
        return out.toByteArray();
    }

    /**
     * What an announcement says, if it is to be believed; null for anything else, which is simply not heard.
     * The cheap refusals come before the signature is checked, so a stranger's noise costs nothing.
     *
     * @param from   the address the packet came from, which must be the one it names
     * @param mine   this device's own identity key, whose announcements come back to it and are not news
     * @param paired the identity keys of the devices this one is paired with, as {@link #key} writes them
     */
    static Heard heard(byte[] packet,int length,byte[] from,String mine,Set<String> paired,long now) {
        return heard(packet,length,from,mine,paired,now,null);
    }

    /**
     * Why an announcement of the right shape was not believed, counted for the log (see {@link Nearby#refusals}).
     * Not what was the wrong shape, which is somebody else's noise, nor this device's own, which comes back every round.
     */
    enum Refused {
        NOT_PAIRED("not paired"),BAD_SIGNATURE("bad signature"),ADDRESS_MISMATCH("address mismatch"),STALE("stale"),SHUT("shut");
        final String said;
        Refused(String said){this.said=said;}
    }

    /**
     * A device's key as six letters for a log line: the start of a SHA-256 of the key itself, the same whichever form it
     * came in, so a key refused on the network and one learnt from a message can be matched without either being logged.
     */
    static String tag(String identity) {
        try {
            byte[] of=java.security.MessageDigest.getInstance("SHA-256").digest(new MiniData(key(identity)).getBytes());
            return String.format("%02x%02x%02x",of[0],of[1],of[2]);
        } catch(GeneralSecurityException|RuntimeException notAKey){return "??????";}
    }

    /** @param refused told why, and whose key, where an announcement of the right shape is not believed; may be null */
    static Heard heard(byte[] packet,int length,byte[] from,String mine,Set<String> paired,long now,java.util.function.BiConsumer<Refused,String> refused) {
        java.util.function.BiConsumer<Refused,String> why=refused==null?(any,who)->{}:refused;
        if(packet==null||length<MAGIC.length+1||length>MOST||length>packet.length||from==null)return null;
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(packet,0,length))) {
            byte[] magic=new byte[MAGIC.length];in.readFully(magic);
            if(!java.util.Arrays.equals(magic,MAGIC)||in.readUnsignedByte()!=VERSION)return null;
            int keyLength=in.readUnsignedShort();
            if(keyLength<1||keyLength>MOST_KEY)return null;
            byte[] key=new byte[keyLength];in.readFully(key);
            int ipLength=in.readUnsignedByte();
            if(ipLength!=4&&ipLength!=16)return null;
            byte[] ip=new byte[ipLength];in.readFully(ip);
            int port=in.readUnsignedShort();
            long when=in.readLong();
            int signatureLength=in.readUnsignedShort();
            if(signatureLength<1||signatureLength>MOST_SIGNATURE)return null;
            byte[] signature=new byte[signatureLength];in.readFully(signature);
            if(in.available()!=0)return null;
            if(port<1)return null;
            String who=key(key);
            if(who.equals(key(mine)))return null;
            if(Math.abs(now-when)>FRESH){why.accept(Refused.STALE,who);return null;}
            // Said from where it says, or not believed: that is what stops it being played again from elsewhere.
            if(!java.util.Arrays.equals(ip,from)){why.accept(Refused.ADDRESS_MISMATCH,who);return null;}
            if(paired==null||!paired.contains(who)){why.accept(Refused.NOT_PAIRED,who);return null;}
            PublicKey theirs=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key));
            Signature check=Signature.getInstance("SHA256withRSA");
            check.initVerify(theirs);check.update(CONTEXT);check.update(packet,0,length-2-signatureLength);
            if(!check.verify(signature)){why.accept(Refused.BAD_SIGNATURE,who);return null;}
            return new Heard(who,java.net.InetAddress.getByAddress(ip).getHostAddress()+":"+port);
        } catch(IOException|GeneralSecurityException|RuntimeException notOne) {
            return null;
        }
    }

    /** An identity key in the one form they are compared in: the transport's own. */
    static String key(byte[] der){return key(new MiniData(der).to0xString());}
    static String key(String hex){return com.eurobuddha.maxima.core.identity.Keys.norm(hex);}

    /**
     * The identity keys of every paired device: the only ones whose announcements are believed. A device with no
     * identity key written down is not among them - one linked through a list, while notes go only between the
     * owner's devices, never had it written: its list gave a relay's address, which nothing here dials. It is
     * written down from the first thing that device sends (see {@link Post#knownBy}), and it is heard from then.
     */
    static Set<String> paired(Collection<NoteStore.Contact> devices) {
        Set<String> keys=new java.util.HashSet<>();
        if(devices==null)return keys;
        for(NoteStore.Contact one:devices)
            if(one!=null&&one.paired()&&one.contact!=null&&!one.contact.trim().isEmpty())keys.add(key(one.contact));
        return keys;
    }

    // ---- direct first --------------------------------------------------------------------------------------

    /**
     * Whether an address is a device's own door rather than a relay. A relay address is sealed to a key made
     * for that one relay; a door is sealed to the device's identity key itself, because the door opens with
     * it. So the part before the {@code @} is the identity key, written as an address.
     */
    static boolean isDirect(String address,String identity) {
        if(address==null||identity==null||identity.trim().isEmpty())return false;
        int at=address.indexOf('@');
        int colon=at<0?-1:address.indexOf(':',at+1);
        if(at<=0||colon<=at+1||colon>=address.length()-1)return false;
        try{return address.substring(0,at).equals(MxAddress.make(new MiniData(identity.trim())));}
        catch(RuntimeException notAKey){return false;}
    }

    /** Where to try first, in order: the local address heard for the device, then each of its own doors. */
    static List<String> firstTries(String lan,List<String> addresses,String identity) {
        LinkedHashSet<String> tries=new LinkedHashSet<>();
        if(lan!=null&&!lan.trim().isEmpty())tries.add(lan.trim());
        if(addresses!=null)for(String one:addresses)if(isDirect(one,identity))tries.add(one);
        return new ArrayList<>(tries);
    }

    /**
     * Sent to a device, directly if it can be. The first door that takes it is the whole of the send; if none
     * does, it goes as it always did, to every address the transport knows for them, relays included.
     */
    static MaximaSender.Result send(MaximaNode node,Contact them,String application,byte[] data) throws Exception {
        return send(node,them,application,data,null);
    }

    /** Somewhere a message for a device can be left for it to collect: its home (see {@link Home}). */
    interface Leave {
        /** Whether it was taken, by a home that says the device is collecting from it. */
        boolean left(byte[] data) throws Exception;
    }

    /**
     * The same, with the device's home tried between its own doors and the relays. A home that takes it has
     * it for a device that collects from it every round or two, so that is the whole of the send too.
     */
    static MaximaSender.Result send(MaximaNode node,Contact them,String application,byte[] data,Leave home) throws Exception {
        return send(node,them,application,data,home,true);
    }

    /** Why nothing went, while notes travel only between the owner's devices: said where a send is shown to fail. */
    static final String WAITS="Not in reach just now. It waits, and goes when the devices meet.";

    /**
     * @param helpers whether relays may carry it when no door or home does. Without them, what no door or home
     *                took is not sent at all: it stays owed, and the outbox tries again as it does for anything
     *                nobody answered - never the transport's own fan-out, relays or directory.
     */
    static MaximaSender.Result send(MaximaNode node,Contact them,String application,byte[] data,Leave home,boolean helpers) throws Exception {
        String lan=node.lanAddressFor(them.publicKey);
        for(String one:firstTries(lan,new ArrayList<>(them.addresses),them.publicKey)) {
            try {
                MaximaSender.Result said=node.sendRaw(one,application,data,LEASH,MaximaSender.READ_TIMEOUT_MS);
                if(said!=null&&said.isOk()){WENT.incrementAndGet();if(one.equals(lan))opened(them.publicKey,lan);return said;}
            } catch(Exception notThere){/* the next way, and at worst the relays */}
            // Not on this network any more, or not letting anything in - a PC's firewall drops what it has not
            // been told to allow, and says nothing. Let go of, and not believed again for a while even though it
            // goes on announcing, or every note would wait out the leash before going the long way.
            if(one.equals(lan)){node.forgetLanPeer(them.publicKey);shut(them.publicKey,lan,System.currentTimeMillis());SHUT_HERE.incrementAndGet();}
        }
        if(home!=null) {
            try{if(home.left(data)){LEFT.incrementAndGet();return MaximaSender.Result.of(com.eurobuddha.maxima.core.net.Frame.RESPONSE_OK);}}
            catch(Exception notThere){/* the relays */}
        }
        if(!helpers)throw new IllegalStateException(WAITS);
        return node.sendToContact(them,application,data);
    }

    /**
     * Whether an address is on the network this device is on - a door heard at home, or one in a pairing code
     * made there - by its host alone: a private, link-local or loopback address, written as one. The one kind
     * of address dialled without knowing whose it is while notes go only between the owner's devices, because
     * whatever answers there is in the same house, not a relay. A name is never looked up here.
     */
    static boolean onThisNetwork(String address) {
        int at=address==null?-1:address.lastIndexOf('@');
        int colon=at<0?-1:address.lastIndexOf(':');
        if(at<=0||colon<=at+1)return false;
        String host=address.substring(at+1,colon);
        if(host.startsWith("[")&&host.endsWith("]"))host=host.substring(1,host.length()-1);
        if(!host.matches("[0-9.]+")&&!(host.contains(":")&&host.matches("[0-9a-fA-F:.]+")))return false;
        try {
            java.net.InetAddress ip=java.net.InetAddress.getByName(host);
            byte first=ip.getAddress()[0];
            return ip.isLoopbackAddress()||ip.isSiteLocalAddress()||ip.isLinkLocalAddress()
                ||ip instanceof java.net.Inet6Address&&(first&0xfe)==0xfc;
        } catch(Exception notOne){return false;}
    }

    /**
     * The identity key a door's address is sealed to, from the address itself: a door is dialled with the
     * device's own key as its {@code Mx} part (see {@link #isDirect}). Empty where it cannot be read.
     */
    static String keyOf(String door) {
        int at=door==null?-1:door.indexOf('@');
        if(at<=2||!door.startsWith("Mx"))return "";
        try{return key(MxAddress.convert(door.substring(0,at)).to0xString());}catch(RuntimeException notOne){return "";}
    }

    /** This device's door as an address on one local network: its identity key, the address there, the door's port. */
    static String door(String identity,String ip,int port) {
        return MxAddress.make(new MiniData(identity.trim()))+"@"+ip+":"+port;
    }

    /** How long a local door that would not take a note again is not tried again. */
    static final long SHUT_FOR=10L*60*1000;
    /**
     * And how long after the first time. A phone's door misses one while the phone dozes, or the system has frozen
     * the app for a moment, and is open again when it next announces itself; ten minutes of not believing it then
     * was ten minutes of answers kept for want of a way, seen between two phones on 0.1.030. A door that fails
     * again soon after - a PC's firewall, which drops everything and says nothing - is the one left for ten.
     */
    static final long SHUT_FIRST=60L*1000;
    /** By key and place: when it was last found shut, and how many times running. */
    private static final java.util.Map<String,long[]> SHUT=new java.util.concurrent.ConcurrentHashMap<>();

    /** A device's local door, found shut: by its key and the place, so the same device elsewhere is tried. */
    static void shut(String identity,String lan,long now) {
        if(SHUT.size()>256)SHUT.clear();
        String at=key(identity)+"@"+host(lan);
        long[] was=SHUT.get(at);
        // Running if it failed again within twice the long wait of the last time; a door that has been open a
        // while since starts from the first time again.
        long times=was!=null&&now-was[0]<2*SHUT_FOR?was[1]+1:1;
        SHUT.put(at,new long[]{now,times});
    }

    /** A device's local door that took something: whatever it did before, it is open. */
    static void opened(String identity,String lan){SHUT.remove(key(identity)+"@"+host(lan));}

    /** Whether this device's door at this place was found shut lately, so hearing it announce is not news. */
    static boolean shutLately(String identity,String where,long now) {
        long[] at=SHUT.get(key(identity)+"@"+host(where));
        return at!=null&&now-at[0]<(at[1]<=1?SHUT_FIRST:SHUT_FOR);
    }

    /** The host and port of an address, or the thing itself where it has no {@code @}. */
    private static String host(String address) {
        int at=address==null?-1:address.indexOf('@');
        return address==null?"":at<0?address:address.substring(at+1);
    }

    /** A file's sources with a device's doors in front, each once. */
    static List<String> sourcesFirst(List<String> doors,List<String> sources) {
        LinkedHashSet<String> all=new LinkedHashSet<>();
        if(doors!=null)all.addAll(doors);
        if(sources!=null)all.addAll(sources);
        return new ArrayList<>(all);
    }

    /** Whether anywhere but this device's own doors holds a file's pieces: a relay, which is there when this device is not. */
    static boolean relayed(List<String> sources,List<String> own) {
        if(sources==null)return false;
        for(String one:sources)if(own==null||!own.contains(one))return true;
        return false;
    }

    /** How many sends went straight to a device since the process started. A count, for a log line. */
    static final java.util.concurrent.atomic.AtomicInteger WENT=new java.util.concurrent.atomic.AtomicInteger();
    /** And how many were left at the device's home for it to collect. */
    static final java.util.concurrent.atomic.AtomicInteger LEFT=new java.util.concurrent.atomic.AtomicInteger();
    /** And how many times a door heard on this network would not take one - a firewall, most often, on a PC. */
    static final java.util.concurrent.atomic.AtomicInteger SHUT_HERE=new java.util.concurrent.atomic.AtomicInteger();

    /**
     * Which road a send took, from the counts as they were before it and are now - near enough when two sends
     * cross. For a log line: a note that reached a relay for nobody looks, from the sending end, like one delivered.
     */
    static String road(int doors,int homes,int shut,boolean ok) {
        String way=!ok?"":WENT.get()>doors?", by a door":LEFT.get()>homes?", left at a home":", by the relays";
        return way+(SHUT_HERE.get()>shut?" (a door on this network would not take it)":"");
    }

    // ---- the PC's own door, as its owner is told ----------------------------------------------------------

    /**
     * One plain line on whether devices away from home can reach this PC directly, from what the reachability
     * check says. Never the address itself: it names the owner's home.
     */
    /**
     * The PC's line while notes travel only between the owner's devices. Its router is not asked then: proving
     * a port takes a relay dialling it back, and a port nobody proved would cost every phone away from home a
     * wait at a door that may be shut, on every note - so no address is given out that has not been proved.
     */
    static final String AT_HOME_ONLY="Reached directly on your home network. Away from home it cannot be checked without a relay";

    static String reachability(boolean door,String state,String detail) {
        String why=detail==null?"":detail;
        if(!door||why.contains("listener not running"))return "Not reachable directly: this PC could not open a port";
        if(state==null||why.equals("not started"))return "Not yet known whether this PC can be reached directly";
        switch(state) {
            case "ADVERTISED": return "Reachable directly, even from away from home";
            case "MAPPING": case "PROBING": return "Checking whether this PC can be reached directly…";
            default:
                if(why.contains("no forwardable"))return "Not reachable directly: the router did not open a port";
                if(why.contains("not reachable from outside")||why.contains("not open from outside"))
                    return "Not reachable directly: the router's port let nothing in";
                if(why.contains("network changed"))return "Checking whether this PC can be reached directly…";
                return "Not reachable directly: relays carry what comes and goes";
        }
    }
}
