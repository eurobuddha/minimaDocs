// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import com.eurobuddha.maxima.core.net.Frame;
import com.eurobuddha.maxima.core.net.PrivateStreams;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The PC as its owner's host: what comes for the owner's phones is left on the PC, and the phones come and
 * collect it. See docs/DIRECT.md, phase 2.
 *
 * <p>A phone cannot be reached - a phone on mobile data shares one public address with thousands, and a
 * router drops what nobody inside asked for - so nothing can be pushed to it but through a relay it keeps a
 * line open to. What a phone can do is ask. So the PC keeps a shelf for the owner's phones, and every round a
 * phone that can reach the PC's door (at home, or at the public address the PC proved) asks it whether
 * anything is waiting, takes it, and says so, and the PC lets it go.
 *
 * <p>The asking goes over the door the PC already has. The transport's door answers only a status to a
 * message and forwards nothing, but it hands a connection that names a registered token straight to whoever
 * registered it ({@link PrivateStreams}), which is a conversation both ways on the same port, with the
 * door's own connection caps. The token is only a label - anybody who knows the PC's identity key can work it
 * out - because everything said on it is sealed and signed like any note ({@link Envelope}):
 * <ul>
 * <li><b>Deposit</b>: something already sealed for one of the owner's devices, by the fingerprint of the key
 *     it signs with. Only from a device paired with the PC, only what that device sealed itself, and only for
 *     a device marked "My device" there that has <i>collected lately</i> - so a deposit taken is something
 *     that will be collected within minutes, and it is fair to send it no other way. A device that has
 *     stopped collecting is refused, and the sender goes to the relays as it always did.</li>
 * <li><b>Pull</b>: "anything for me?" - from a device marked "My device", answered with what is waiting for
 *     it, a bounded amount at a time, oldest first.</li>
 * <li><b>Ack</b>: "I have these", by the hash of each, and the PC lets them go.</li>
 * </ul>
 * The PC cannot read what it holds: each thing is sealed for the phone it is for. It knows who left it, who
 * it is for, which note by its id and at which revision - what a relay or a carrier knows, no more.
 *
 * <p>Taken is not delivered. The phone that collects a note answers whoever wrote it, as for any note, and
 * only that answer clears anything (see {@link Receipt}).
 *
 * <p>Holds no Android types: the formats, their bounds and the rules are unit tested, and the PC and the
 * phones run the same code.
 */
final class Home {
    /** The first four bytes of everything said on a home's stream. */
    static final byte[] MAGIC={'M','N','H','1'};
    static final int DEPOSIT=1, PULL=2, ACK=3;
    /** What the home says back. */
    static final int OK=1, REFUSED=2, NOT_COLLECTING=3, FULL=4, BUSY=5;
    /** Never said by a home: what a door with no home behind it comes to - it closes before answering. */
    static final int NO_HOME=0;

    /** How long something nobody collected is kept. A phone away for a fortnight is sent things again anyway. */
    static final long KEPT_FOR=14L*24*60*60*1000;
    /**
     * A device that asked this recently is collecting: what is left for it will be taken within a round or
     * two. Longer than three of a phone's slowest rounds, as with a device heard on the local network.
     */
    static final long COLLECTING=5L*60*1000;
    /** The most kept for one device, and for everybody together. Text is small; these are caps on a fault. */
    static final int MOST_FOR_ONE=300, MOST_KEPT=1000;
    static final long BYTES_FOR_ONE=8L*1024*1024, MOST_BYTES=32L*1024*1024;
    /** The most handed over in one answer: a phone on mobile data should not have to take a whole shelf at once. */
    static final int PULL_MOST=16;
    static final long PULL_BYTES=1024L*1024;
    /** The most answers taken in one round, so a full shelf empties over a few rounds and not in one long one. */
    static final int ROUNDS_MOST=4;
    /** The most asked on one connection, and bounds on what is read off it. */
    static final int ASKS_MOST=16, ASK_MOST=64*1024, INNER_MOST=Envelope.MAX_TEXT+3*Envelope.MAX_FIELD;
    static final int ANSWER_MOST=(int)PULL_BYTES+INNER_MOST+64*1024;
    /** How long a home's answer is waited for, once it has been reached. */
    static final int WAIT=15000;

    private Home(){}

    // ---- the token the door hands the stream on by --------------------------------------------------------

    /** Said in front of the key, so this can never be taken for a token made for anything else. */
    private static final String CONTEXT="mininotes/home/1\0";

    /** The label a device's home is asked for on its door: its identity key, hashed, as the transport wants it. */
    static String token(String identity) {
        return hex(sha256((CONTEXT+Direct.key(identity)).getBytes(StandardCharsets.US_ASCII)));
    }

    // ---- what is asked, inside a sealed envelope ----------------------------------------------------------

    /** One thing asked of a home, read. */
    static final class Ask {
        final int kind, most, length;
        /** For a deposit: who it is for, and the SHA-256 of what follows the envelope on the stream. */
        final byte[] forWhom, hash;
        /** For an ack: the SHA-256 of each thing collected. */
        final List<byte[]> ids;
        Ask(int kind,byte[] forWhom,byte[] hash,int length,int most,List<byte[]> ids) {
            this.kind=kind;this.forWhom=forWhom;this.hash=hash;this.length=length;this.most=most;this.ids=ids;
        }
    }

    /**
     * Something for {@code forWhom}, left at their home. The thing itself follows the envelope on the stream
     * rather than going inside it, so a note near the largest there is can be left as well; its hash is here,
     * sealed and signed with the rest, so nothing else can be put in its place.
     */
    static byte[] deposit(byte[] forWhom,byte[] inner) {
        if(forWhom==null||forWhom.length!=32)throw new IllegalArgumentException("A fingerprint is 32 bytes");
        if(inner==null||inner.length==0||inner.length>INNER_MOST)throw new IllegalArgumentException("Nothing that can be left");
        return write(DEPOSIT,out->{out.write(forWhom);out.write(sha256(inner));out.writeInt(inner.length);});
    }

    /** "Anything for me?", and no more than {@code most} at once. */
    static byte[] pull(int most){return write(PULL,out->out.writeShort(Math.max(1,Math.min(most,PULL_MOST))));}

    /** "I have these", by the hash of each. */
    static byte[] ack(List<byte[]> ids) {
        if(ids==null||ids.size()>PULL_MOST)throw new IllegalArgumentException("Not a list that can be acked");
        return write(ACK,out->{out.writeShort(ids.size());for(byte[] one:ids)out.write(one);});
    }

    private interface Writing{void to(DataOutputStream out) throws IOException;}
    private static byte[] write(int kind,Writing rest) {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)){out.write(MAGIC);out.writeByte(kind);rest.to(out);}
        catch(IOException never){throw new IllegalStateException(never);}
        return bytes.toByteArray();
    }

    /** What was asked, or null for anything that is not one - which is refused. */
    static Ask ask(byte[] said) {
        if(said==null||said.length<MAGIC.length+1||said.length>ASK_MOST||!starts(said))return null;
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(said,MAGIC.length,said.length-MAGIC.length))) {
            int kind=in.readUnsignedByte();
            Ask read;
            if(kind==DEPOSIT) {
                byte[] forWhom=new byte[32],hash=new byte[32];in.readFully(forWhom);in.readFully(hash);
                int length=in.readInt();
                if(length<1||length>INNER_MOST)return null;
                read=new Ask(kind,forWhom,hash,length,0,Collections.emptyList());
            } else if(kind==PULL) {
                int most=in.readUnsignedShort();
                if(most<1||most>PULL_MOST)return null;
                read=new Ask(kind,null,null,0,most,Collections.emptyList());
            } else if(kind==ACK) {
                int many=in.readUnsignedShort();
                if(many>PULL_MOST)return null;
                List<byte[]> ids=new ArrayList<>(many);
                for(int at=0;at<many;at++){byte[] one=new byte[32];in.readFully(one);ids.add(one);}
                read=new Ask(kind,null,null,0,0,ids);
            } else return null;
            return in.available()==0?read:null;
        } catch(IOException broken){return null;}
    }

    // ---- what a home answers, on the stream as it is: what it hands over is sealed already ----------------

    /** A home's answer, read: how it went, how many more are waiting, and what it handed over. */
    static final class Answer {
        final int status, more;
        final List<byte[]> items;
        Answer(int status,int more,List<byte[]> items){this.status=status;this.more=more;this.items=items;}
    }

    static byte[] answer(int status,int more,List<byte[]> items) {
        List<byte[]> all=items==null?Collections.<byte[]>emptyList():items;
        return write(status,out->{
            out.writeInt(Math.max(0,more));out.writeShort(all.size());
            for(byte[] one:all){out.writeInt(one.length);out.write(one);}
        });
    }

    /** The answer, or null for anything that is not one. The kind byte is where an answer says how it went. */
    static Answer answered(byte[] said) {
        if(said==null||said.length<MAGIC.length+7||said.length>ANSWER_MOST||!starts(said))return null;
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(said,MAGIC.length,said.length-MAGIC.length))) {
            int status=in.readUnsignedByte(),more=in.readInt(),many=in.readUnsignedShort();
            if(status<OK||status>BUSY||more<0||many>PULL_MOST||(status!=OK&&many>0))return null;
            List<byte[]> items=new ArrayList<>(many);
            for(int at=0;at<many;at++) {
                int length=in.readInt();
                if(length<1||length>INNER_MOST||length>in.available())return null;
                byte[] one=new byte[length];in.readFully(one);items.add(one);
            }
            return in.available()==0?new Answer(status,more,items):null;
        } catch(IOException broken){return null;}
    }

    private static boolean starts(byte[] said) {
        for(int at=0;at<MAGIC.length;at++)if(said[at]!=MAGIC[at])return false;
        return true;
    }

    // ---- what a home can see of what it holds --------------------------------------------------------------

    /**
     * What the outside of a sealed note says, which is all a home can read: who sealed it, which note, which
     * revision, and how long what is sealed inside is - which is the same each time the same thing is sealed,
     * where the whole envelope is not: a signature is a byte longer or shorter from one time to the next.
     */
    static final class About {
        final String sender, page; final long revision; final int sealed;
        About(String sender,String page,long revision,int sealed){this.sender=sender;this.page=page;this.revision=revision;this.sealed=sealed;}
    }

    /**
     * The header of an {@link Envelope}, read without opening it; null for anything that is not one. Nothing
     * here is checked against the signature - that is for the device it is sealed for - only the key named is
     * compared with whoever left it, so a device leaves only what it sealed itself.
     */
    static About about(byte[] sealed) {
        if(sealed==null||sealed.length<Envelope.MAGIC.length+32)return null;
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(sealed))) {
            byte[] magic=new byte[Envelope.MAGIC.length];in.readFully(magic);
            if(!Arrays.equals(magic,Envelope.MAGIC))return null;
            byte[] page=new byte[16];in.readFully(page);
            long revision=in.readLong();in.readLong();
            int length=in.readInt();
            if(length<1||length>Envelope.MAX_FIELD||length>in.available())return null;
            byte[] key=new byte[length];in.readFully(key);
            for(int field=0;field<2;field++) {
                int skip=in.readInt();
                if(skip<0||skip>Envelope.MAX_FIELD||skip>in.available())return null;
                in.readFully(new byte[skip]);
            }
            int inside=in.readInt();
            if(inside<0||inside>in.available())return null;
            return revision<0?null:new About(hex(sha256(key)),hex(page),revision,inside);
        } catch(IOException broken){return null;}
    }

    // ---- the shelf ----------------------------------------------------------------------------------------

    /** One thing held for one of the owner's devices. Who left it and who it is for, by the fingerprints of their keys. */
    static final class Held {
        /** The SHA-256 of the bytes: what the device that collects it names it by. */
        final String id, sender, recipient, page; final long revision, kept;
        /** How long what is sealed inside is: see {@link About}. */
        final int sealed;
        final byte[] bytes;
        Held(String sender,String recipient,String page,long revision,int sealed,long kept,byte[] bytes) {
            this(hex(sha256(bytes)),sender,recipient,page,revision,sealed,kept,bytes);
        }
        Held(String id,String sender,String recipient,String page,long revision,int sealed,long kept,byte[] bytes) {
            this.id=id;this.sender=sender;this.recipient=recipient;this.page=page;this.revision=revision;this.sealed=sealed;
            this.kept=kept;this.bytes=bytes;
        }
    }

    /**
     * Where a home keeps what it holds: on the PC, the notebook ({@code held}, schema 25), so it is still there
     * after a restart. Each one lets go of what is older than {@link #KEPT_FOR} before it answers.
     */
    interface Shelf {
        /**
         * Kept, if there is room by {@link #room}. The same bytes twice are kept once; the same note at the same
         * revision from the same device, sealed again with as much inside, replaces what was held - which is how a note sent again
         * while nobody had collected it does not pile up.
         */
        boolean holdFor(Held one);
        /** What is held for one device, oldest first, as much as {@link #within} allows. */
        List<Held> heldFor(String recipient,int most,long bytes);
        /** How many are held for one device, or for everybody where {@code recipient} is null. */
        int countHeldFor(String recipient);
        /** Collected: let go. How many. */
        int letGoFor(String recipient,Collection<String> ids);
    }

    /** Whether something of {@code size} bytes fits beside what is held for them and for everybody. */
    static boolean room(int forThem,long forThemBytes,int all,long allBytes,int size) {
        return forThem<MOST_FOR_ONE&&forThemBytes+size<=BYTES_FOR_ONE&&all<MOST_KEPT&&allBytes+size<=MOST_BYTES;
    }

    /** Oldest first, no more than {@code most}, and no more than {@code bytes} - but always the first, however big. */
    static List<Held> within(List<Held> oldestFirst,int most,long bytes) {
        List<Held> out=new ArrayList<>();long size=0;
        for(Held one:oldestFirst) {
            if(out.size()>=most||(!out.isEmpty()&&size+one.bytes.length>bytes))break;
            out.add(one);size+=one.bytes.length;
        }
        return out;
    }

    static boolean expired(long kept,long now){return now-kept>KEPT_FOR;}

    // ---- the home itself: the PC -------------------------------------------------------------------------

    /**
     * What the home needs of its notebook, said again every round by whoever holds it: the key asks are sealed
     * to, who is paired, which of them are "My device", and the shelf. Null while the notebook is locked or
     * closing, and then the home answers {@link #BUSY} and everybody goes the way they went before.
     */
    static final class Notebook {
        final PrivateKey agreement; final Set<String> paired, mine; final Shelf shelf;
        Notebook(PrivateKey agreement,Set<String> paired,Set<String> mine,Shelf shelf) {
            this.agreement=agreement;this.paired=Set.copyOf(paired);this.mine=Set.copyOf(mine);this.shelf=shelf;
        }
    }

    /** A home, answering on its device's door. */
    static final class Host implements PrivateStreams.Handler {
        final String token;
        private volatile Notebook book;
        /** When each device last asked for what is held for it: in memory, since it is only about the last minutes. */
        private final Map<String,Long> asked=new ConcurrentHashMap<>();

        Host(String identity){token=token(identity);}

        void open(){PrivateStreams.register(token,this);}
        void close(){PrivateStreams.remove(token);}
        void notebook(Notebook now){book=now;}

        /** A connection the door handed over: asks answered one after another until the other end is done. */
        @Override public void serve(Socket socket,DataInputStream in,DataOutputStream out) throws IOException {
            for(int asked=0;asked<ASKS_MOST;asked++) {
                byte[] ask;
                try{ask=blob(in,ASK_MOST);}catch(EOFException done){return;}
                byte[] inner=blob(in,INNER_MOST);
                blob(out,said(ask,inner,System.currentTimeMillis()));
            }
        }

        /** The answer to one ask. A stranger, or anything that does not open, is refused and told nothing more. */
        byte[] said(byte[] sealed,byte[] inner,long now) {
            Notebook at=book;
            if(at==null)return answer(BUSY,0,null);
            try {
                Envelope.Opened opened=Envelope.open(sealed,at.agreement);
                String from=hex(opened.sender);
                Ask ask=ask(opened.text);
                if(ask==null||!at.paired.contains(from))return answer(REFUSED,0,null);
                if(ask.kind==DEPOSIT) {
                    if(inner.length!=ask.length||!MessageDigest.isEqual(sha256(inner),ask.hash))return answer(REFUSED,0,null);
                    return answer(keep(at,from,hex(ask.forWhom),inner,now),0,null);
                }
                // Only the owner's own devices collect, and only what is held for them.
                if(!at.mine.contains(from))return answer(REFUSED,0,null);
                if(ask.kind==PULL) {
                    asked.put(from,now);
                    List<Held> held=at.shelf.heldFor(from,ask.most,PULL_BYTES);
                    List<byte[]> items=new ArrayList<>(held.size());
                    for(Held one:held)items.add(one.bytes);
                    return answer(OK,at.shelf.countHeldFor(from)-items.size(),items);
                }
                List<String> ids=new ArrayList<>(ask.ids.size());
                for(byte[] one:ask.ids)ids.add(hex(one));
                at.shelf.letGoFor(from,ids);
                return answer(OK,at.shelf.countHeldFor(from),null);
            } catch(GeneralSecurityException notForHere) {
                return answer(REFUSED,0,null);
            } catch(RuntimeException closing) {
                // The notebook went from under it - locked, or closing. Nobody is told more than "not now".
                return answer(BUSY,0,null);
            }
        }

        /**
         * Something this device itself sealed for one of its owner's devices, kept here for it to collect, as a
         * deposit from anybody else would be.
         */
        int keepHere(String sender,String forWhom,byte[] inner,long now) {
            Notebook at=book;
            if(at==null)return BUSY;
            try{return keep(at,sender,forWhom,inner,now);}catch(RuntimeException closing){return BUSY;}
        }

        private int keep(Notebook at,String from,String forWhom,byte[] inner,long now) {
            if(!at.mine.contains(forWhom)||forWhom.equals(from))return REFUSED;
            About about=about(inner);
            if(about==null||!about.sender.equals(from))return REFUSED;
            // Only for a device that is collecting, so what is taken here is taken within minutes. Otherwise the
            // sender goes to the relays, as it did before there was a home.
            if(!collecting(forWhom,now))return NOT_COLLECTING;
            return at.shelf.holdFor(new Held(from,forWhom,about.page,about.revision,about.sealed,now,inner))?OK:FULL;
        }

        /** Whether a device has asked for what is held for it lately. */
        boolean collecting(String who,long now) {
            Long at=asked.get(who);
            return at!=null&&now-at<=COLLECTING&&now>=at;
        }

        /** How many of the owner's devices have asked lately. */
        int collectingNow(long now) {
            int many=0;
            for(Map.Entry<String,Long> one:asked.entrySet())if(collecting(one.getKey(),now))many++;
            return many;
        }

        /** How many things are held, for everybody; -1 where the notebook is not open. */
        int holding() {
            Notebook at=book;
            if(at==null)return -1;
            try{return at.shelf.countHeldFor(null);}catch(RuntimeException closing){return -1;}
        }
    }

    /** One plain line for the PC's settings on what it holds, never who for by name or what. */
    static String said(int holding,int collecting) {
        if(holding<0)return "";
        if(holding>0)return "Holding "+(holding==1?"1 message":holding+" messages")+" for your phones until they collect "+(holding==1?"it.":"them.");
        if(collecting>0)return "Your phones collect from this PC, with no relay between.";
        return "Your phones collect from this PC when they can reach it.";
    }

    // ---- a device leaving something, and collecting it -----------------------------------------------------

    /** Left at a home: how it went. The connection failing is thrown, which is a different thing from a no. */
    static int deposit(String door,String identity,PublicKey agreement,KeyPair mine,byte[] forWhom,byte[] inner)
            throws IOException, GeneralSecurityException {
        byte[] sealed=Envelope.seal(new byte[16],0,System.currentTimeMillis(),deposit(forWhom,inner),mine,agreement);
        try(Socket socket=dial(door,identity)) {
            DataOutputStream out=new DataOutputStream(socket.getOutputStream());
            DataInputStream in=new DataInputStream(socket.getInputStream());
            Answer said;
            try{blob(out,sealed);blob(out,inner);said=answered(blob(in,ANSWER_MOST));}
            catch(java.net.SocketTimeoutException slow){throw slow;}
            catch(IOException nobody){return NO_HOME;}
            return said==null?REFUSED:said.status;
        }
    }

    /** What one collecting round did: how the home answered, and how many things it handed over. */
    static final class Collected {
        final int status, items;
        Collected(int status,int items){this.status=status;this.items=items;}
    }

    /**
     * Everything waiting at a home, collected: each thing handed to {@code each} as if it had just arrived,
     * and then said to be had, so the home lets it go. A few answers at most in one round. Handed over before
     * it is acked, so what is lost between the two is collected again, never missed.
     */
    static Collected collect(String door,String identity,PublicKey agreement,KeyPair mine,Consumer<byte[]> each)
            throws IOException, GeneralSecurityException {
        int got=0,status=REFUSED;
        try(Socket socket=dial(door,identity)) {
            DataOutputStream out=new DataOutputStream(socket.getOutputStream());
            DataInputStream in=new DataInputStream(socket.getInputStream());
            for(int round=0;round<ROUNDS_MOST;round++) {
                byte[] asking=Envelope.seal(new byte[16],0,System.currentTimeMillis(),pull(PULL_MOST),mine,agreement);
                Answer said;
                try{blob(out,asking);blob(out,new byte[0]);said=answered(blob(in,ANSWER_MOST));}
                catch(java.net.SocketTimeoutException slow){throw slow;}
                catch(IOException nobody){if(round==0)return new Collected(NO_HOME,0);throw nobody;}
                if(said==null)return new Collected(REFUSED,got);
                status=said.status;
                if(status!=OK||said.items.isEmpty())break;
                List<byte[]> ids=new ArrayList<>(said.items.size());
                for(byte[] one:said.items) {
                    try{each.accept(one);}catch(RuntimeException notThisOne){/* taken all the same: its sender sends it again */}
                    ids.add(sha256(one));got++;
                }
                blob(out,Envelope.seal(new byte[16],0,System.currentTimeMillis(),ack(ids),mine,agreement));blob(out,new byte[0]);
                Answer acked=answered(blob(in,ANSWER_MOST));
                if(acked==null||acked.status!=OK||said.more==0)break;
            }
        }
        return new Collected(status,got);
    }

    /**
     * A home's door, dialled, and its stream asked for. A door with no home behind it - a phone's, or a PC's
     * from before there were homes - closes the connection, and the first thing said on it finds that out:
     * which is {@link #NO_HOME}, where a door that cannot be reached at all, or does not answer in time, is
     * thrown.
     */
    private static Socket dial(String door,String identity) throws IOException {
        Socket socket=new Socket();
        try {
            socket.connect(where(door),Direct.LEASH);
            socket.setSoTimeout(WAIT);socket.setTcpNoDelay(true);
            byte[] asking=new byte[65];asking[0]=(byte)PrivateStreams.TYPE;
            System.arraycopy(token(identity).getBytes(StandardCharsets.US_ASCII),0,asking,1,64);
            Frame.write(new DataOutputStream(socket.getOutputStream()),asking);
            return socket;
        } catch(IOException|RuntimeException failed){socket.close();throw failed;}
    }

    /** Where a door is, from an address ({@code Mx…@host:port}) or a host and port alone. */
    static InetSocketAddress where(String door) {
        String at=door==null?"":door.trim();
        int mark=at.lastIndexOf('@');if(mark>=0)at=at.substring(mark+1);
        int colon=at.lastIndexOf(':');
        if(colon<=0||colon>=at.length()-1)throw new IllegalArgumentException("Not a door");
        String host=at.substring(0,colon);
        if(host.startsWith("[")&&host.endsWith("]"))host=host.substring(1,host.length()-1);
        return new InetSocketAddress(host,Integer.parseInt(at.substring(colon+1)));
    }

    // ---- not asked again at once --------------------------------------------------------------------------

    /**
     * A door or a device a home would not take something for, not asked again for a while: every note would
     * otherwise spend a round trip on a home that said no a moment ago. Longest for a door with no home behind
     * it, shortest for a phone that is not collecting just now, since it may be again within the round.
     */
    static long restFor(int status) {
        switch(status) {
            case OK: return 0;
            case NOT_COLLECTING: return 60_000L;
            case BUSY: return 2L*60*1000;
            case FULL: return 10L*60*1000;
            default: return 30L*60*1000;
        }
    }
    /** A door that could not be reached at all: not tried again for five minutes. */
    static final long UNREACHABLE=5L*60*1000;

    private static final Map<String,Long> RESTING=new ConcurrentHashMap<>();
    static boolean resting(String what,long now){Long until=RESTING.get(what);return until!=null&&now<until;}
    static void rest(String what,long now,long forHow) {
        if(forHow<=0){RESTING.remove(what);return;}
        if(RESTING.size()>512)RESTING.clear();
        RESTING.put(what,now+forHow);
    }

    // ---- the stream's own framing -------------------------------------------------------------------------

    /** One length and that many bytes. An end before the length is the other side being done. */
    static byte[] blob(DataInputStream in,int most) throws IOException {
        int length=in.readInt();
        if(length<0||length>most)throw new IOException("Out of bounds");
        byte[] bytes=new byte[length];in.readFully(bytes);
        return bytes;
    }

    static void blob(DataOutputStream out,byte[] bytes) throws IOException {out.writeInt(bytes.length);out.write(bytes);out.flush();}

    static byte[] sha256(byte[] bytes) {
        try{return MessageDigest.getInstance("SHA-256").digest(bytes);}
        catch(GeneralSecurityException never){throw new IllegalStateException("SHA-256 is missing",never);}
    }

    static String hex(byte[] bytes){return Courier.hex(bytes);}
}
