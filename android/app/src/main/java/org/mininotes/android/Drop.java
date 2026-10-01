// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Files sent straight to another device, belonging to no note: the drop box.
 *
 * <p>Two devices on the same Wi-Fi should be able to hand each other a photograph without either of them
 * writing a note to hang it on. So a file can be sent on its own. It travels the way a file kept with a shared
 * note does - sealed under a key of its own, cut into pieces, the pieces fetched from the sender's door, or from
 * a relay where helpers may be used and no door can be reached (see {@link Enclosure}) - and what goes in a
 * message is only the list: this format, sealed end to end for the one device it is for, inside an ordinary
 * {@link Envelope}. The envelope names the sending by its id where it names a note, and says which offer of it
 * this is where a revision goes, so a later offer - the same files, said to be somewhere new - replaces an
 * earlier one.
 *
 * <p>A build from before this would read these bytes as a note written the oldest way and put them on a page,
 * so nothing of this is ever sent to a device that has not said it takes files ({@link Receipt#TAKES_FILES},
 * or a flag at the end of a pairing hello; see {@link Hello}). The answers are {@link Receipt}s: the files are
 * all here, the files were refused, the files cannot be had from where they were said to be.
 *
 * <p>What arrives this way is kept on Home, new until it is opened (docs/HOME.md, step 2 and decision 6), and goes no
 * further: it is nobody's shared thing. Only what somebody later puts in a note or a collection is kept there. The
 * sending stays behind what came, so the device that sent it can be answered if it offers again; what is left for the
 * drop box to list is who is asking, what is still coming, and what this device sent.
 *
 * <p>Holds no Android types: the format, its bounds, who is asked and what each state says are unit tested.
 */
final class Drop {
    /** The format, and the first four bytes. Not a {@link Parcel}, a {@link Hello} or a {@link Courier}. */
    static final byte[] MAGIC={'M','N','D','1'};
    /** One byte after the magic, so a later build can say more without being read as this one. */
    static final int FORMAT=1;

    /**
     * The most files one sending carries. Each is listed with the few kilobytes that say where its pieces are,
     * and twenty at the largest there is still fit one envelope; more are said plainly as not sent.
     */
    static final int FILES_MOST=20;
    static final int ID_MOST=Parcel.ID_MOST, NAME_MOST=Parcel.NAME_MOST, MANIFEST_MOST=Parcel.MANIFEST_MOST;

    /**
     * Where a sending stands. The first four are the sender's: being made ready and handed over, handed over
     * or not reachable and waiting for the other device, delivered - which only the other device's answer says -
     * and refused. The last three are the receiver's: asked and not yet answered, being fetched, and here. A
     * sending the receiver refused is refused at both ends.
     */
    static final int SENDING=1, WAITING=2, DELIVERED=3, REFUSED=4, ASKING=5, FETCHING=6, HERE=7;

    private Drop(){}

    // ---- the format ---------------------------------------------------------------------------------------

    /** The inside of an offer: every file named, each with where its pieces are. */
    static byte[] wrap(List<Enclosure.Listed> files) {
        if(files==null||files.isEmpty()||files.size()>FILES_MOST)throw new IllegalArgumentException("One to "+FILES_MOST+" files go at once");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)) {
            out.write(MAGIC);out.writeByte(FORMAT);out.writeInt(files.size());
            for(Enclosure.Listed one:files) {
                if(!one.fetchable())throw new IllegalArgumentException("A file that cannot be fetched is not offered");
                put(out,one.id,ID_MOST);put(out,one.name,NAME_MOST);put(out,one.kind,NAME_MOST);
                out.writeLong(one.bytes);put(out,one.manifest,MANIFEST_MOST);
            }
        } catch(IOException e){throw new IllegalArgumentException(e.getMessage(),e);}
        byte[] made=bytes.toByteArray();
        // The far end refuses anything bigger whole, so it is refused here, where it can still be said.
        if(made.length>Envelope.MAX_TEXT)throw new IllegalArgumentException("Too much to list in one sending");
        return made;
    }

    /**
     * What an offer lists, or null when these bytes are not one - or are, but broken, out of bounds, naming a
     * file twice or one that cannot be fetched, or with anything after the last file. Half a list is not a list.
     */
    static List<Enclosure.Listed> open(byte[] said) {
        if(said==null||said.length<MAGIC.length+5||said.length>Envelope.MAX_TEXT)return null;
        for(int at=0;at<MAGIC.length;at++)if(said[at]!=MAGIC[at])return null;
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(said,MAGIC.length,said.length-MAGIC.length))) {
            if(in.readUnsignedByte()!=FORMAT)return null;
            int count=in.readInt();
            if(count<1||count>FILES_MOST)return null;
            List<Enclosure.Listed> files=new ArrayList<>(count);Set<String> ids=new HashSet<>();
            for(int at=0;at<count;at++) {
                String id=get(in,ID_MOST),name=get(in,NAME_MOST),kind=get(in,NAME_MOST);
                long size=in.readLong();
                Enclosure.Listed one=new Enclosure.Listed(id,name,kind,size,get(in,MANIFEST_MOST));
                if(!one.fetchable()||!ids.add(one.id))return null;
                files.add(one);
            }
            return in.available()==0?files:null;
        } catch(IOException|IllegalArgumentException broken){return null;}
    }

    private static void put(DataOutputStream out,String text,int most) throws IOException {
        byte[] bytes=(text==null?"":text).getBytes(StandardCharsets.UTF_8);
        if(bytes.length>most)throw new IOException("Too long to send: "+bytes.length+" of "+most);
        out.writeInt(bytes.length);out.write(bytes);
    }

    private static String get(DataInputStream in,int most) throws IOException {
        int length=in.readInt();
        if(length<0||length>most)throw new IllegalArgumentException("A field said it was "+length+" long");
        byte[] bytes=new byte[length];in.readFully(bytes);
        return new String(bytes,StandardCharsets.UTF_8);
    }

    // ---- what each end does --------------------------------------------------------------------------------

    /**
     * What a sending that has just arrived becomes. From one of the owner's own devices it is taken at once, as
     * the notes from it are; from anybody else paired here, they are asked first; from a device not paired here,
     * nothing - it could not have been opened as theirs anyway.
     *
     * @return {@link #FETCHING}, {@link #ASKING}, or 0 for nothing at all
     */
    static int arriving(boolean paired,boolean mine){return !paired?0:mine?FETCHING:ASKING;}

    /**
     * The answer to an offer that came again, for a sending already settled here: the other device did not hear
     * the first answer, and goes on offering until it does. Nothing where it is still being asked or fetched.
     */
    static int answerAgain(int state){return state==HERE?Receipt.DROP_HAVE:state==REFUSED?Receipt.DROP_REFUSED:0;}

    /** What a sending becomes at the sender when the other device answers. Delivered and refused are for good. */
    static int after(int state,int answer) {
        if(state==DELIVERED||state==REFUSED)return state;
        return answer==Receipt.DROP_HAVE?DELIVERED:answer==Receipt.DROP_REFUSED?REFUSED:state;
    }

    /** Whether the sender goes on offering and making ready. */
    static boolean goingOn(int state){return state==SENDING||state==WAITING;}

    /**
     * Whether the pieces go up to relays as well as staying here. Only with helpers, and only when the other
     * device can reach none of this device's doors: not on this network, and no door proved in public. At home a
     * file goes door to door and no relay is given a piece; a device that finds it cannot reach the door says so
     * ({@link Receipt#DROP_MISSING}) and the pieces go up then.
     */
    static boolean upToRelays(boolean helpers,boolean theyAreNear,boolean publicDoor){return helpers&&!theyAreNear&&!publicDoor;}

    /**
     * Whether a device that could not fetch a file tells the sender so. The first time, so a sender whose door
     * cannot be reached sends the pieces up at once rather than in an hour; then every fourth, as for a note's
     * files. Only with helpers: only between the owner's devices there is nothing to send the pieces up to.
     */
    static boolean sayMissing(int tries,boolean helpers){return helpers&&tries>0&&(tries==1||tries%4==0);}

    /**
     * Whether "this device takes files" is said back to a device that has just said it. It is said once a run, and a
     * device that said it while the other was still on a build from before was never heard - and, told back only when
     * it had not said it yet this run, never said it again: the other one updated, said it, and waited for ever. So
     * it is said back to whoever says it, unless it was said to them within the last minute, which is what stops two
     * devices saying it to each other for ever.
     *
     * @param said when it was last said to them this run, or 0 for never
     */
    static boolean sayBack(long said,long now){return said<=0||now<said||now-said>=SAY_BACK;}
    static final long SAY_BACK=60_000L;

    /** Whether a sending is tried again now: offered, or its files fetched. The same clock as a note's files. */
    static boolean due(long tried,int tries,long now){return Enclosure.due(tried,tries,now);}

    // ---- words ---------------------------------------------------------------------------------------------

    /** Why a file was not sent, in the few words each reason needs; see {@link Given#refusal}. */
    static final String TOO_BIG="larger than 16 MB", EMPTY="it is empty", TOO_MANY="more than "+FILES_MOST+" files at once";

    static String files(int count){return count==1?"1 file":count+" files";}

    /** The question put to the person a stranger's files are for. */
    static String asking(String who,int count,long bytes) {
        return who+" wants to send you "+(count==1?"a file":count+" files")+" ("+Attachment.size(bytes)+")";
    }

    /** The same, under the name of whoever it is from, where the name is already said. */
    static String wants(int count,long bytes){return "Wants to send you "+(count==1?"a file":count+" files")+" ("+Attachment.size(bytes)+")";}

    /** Said once everything a sending lists is here. */
    static String arrived(String who,int count){return who+" sent you "+(count==1?"a file":count+" files");}

    /** The busy line while a sending is made ready. */
    static String sending(String who,int count){return "Sending "+files(count)+" to "+who+"…";}

    /** Said once Send has done what it can now: handed to them, or waiting until they can be reached. */
    static String afterSending(String who,boolean handed,boolean takes) {
        if(!takes)return "Waiting for "+who+" to update Mininotes";
        return handed?"Sent. Waiting for "+who+" to take them":"Waiting for "+who+". They go when "+who+" can be reached";
    }

    /** What the sender is told when the other device answers. */
    static String answered(String who,int count,int state) {
        return state==DELIVERED?(count==1?"The file":"The "+count+" files")+" reached "+who
            :state==REFUSED?who+" refused "+(count==1?"the file":"the "+count+" files"):"";
    }

    /**
     * What one sending says about itself, in a row of the list.
     *
     * @param takes whether the other device has said it takes files: one that has not is most likely on an older
     *              Mininotes, and waits until it is updated, which is what is said
     */
    static String state(int state,String who,boolean takes) {
        switch(state) {
            case SENDING: return "Sending…";
            case WAITING: return takes?"Waiting for "+who:"Waiting for "+who+" to update Mininotes";
            case DELIVERED: return "Delivered";
            case REFUSED: return "Refused";
            case ASKING: return "Waiting for your answer";
            case FETCHING: return "Coming…";
            default: return "";
        }
    }

    /** What the drop box says under its name: how many are new, else how many files it holds. */
    static String boxDetail(int fresh,int files){return fresh>0?fresh+" new":files==0?"Nothing yet":files(files);}

    /** What was not sent, on one line under what is going. */
    static String notSentLine(List<String> lines){return lines.isEmpty()?"":"Not sent: "+String.join("; ",lines);}

    /** The box listing what was not sent. */
    static String notSent(List<String> lines) {
        StringBuilder said=new StringBuilder(lines.size()==1?"This was not sent:\n\n":"These were not sent:\n\n");
        for(String line:lines)said.append(line).append('\n');
        return said.toString().trim();
    }

    /** One device the files can go to: its name, and whether it is one of the owner's own. */
    static final class Device {
        final String address,name; final boolean mine;
        Device(String address,String name,boolean mine){this.address=address;this.name=name==null?"":name;this.mine=mine;}
        /** The quiet line under the name. */
        String under(){return mine?"My device":"";}
    }

    /** The devices offered, in the order they are listed: the owner's own first, then by name. */
    static List<Device> choices(List<Device> paired) {
        List<Device> all=new ArrayList<>(paired);
        all.sort((a,b)->a.mine!=b.mine?(a.mine?-1:1):a.name.compareToIgnoreCase(b.name));
        return all;
    }
}
