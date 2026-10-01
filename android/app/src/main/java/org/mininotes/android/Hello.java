// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Somebody saying yes.
 *
 * <p>A code is read in one direction. One phone shows it, the other photographs it, and only the one
 * holding the camera learns anything — so an offer accepted was an offer the offering phone never heard
 * about. It knew what it had put on its screen and had no idea anybody had taken it, which left the person
 * who accepted watching a shelf and the person who offered wondering what to press.
 *
 * <p>So the acceptance travels back: who I am, where to reach me, the keys to seal for me, and which thing
 * of yours I took you up on. The offering phone then has everything it needs to hand the thing over, and
 * nobody has to scan anything twice.
 *
 * <p>This is the plaintext of an envelope sealed for the offerer, so it is as private as a note is. It
 * proves nothing by itself: the envelope's signature says these bytes were not altered, and the keys inside
 * are checked against that signature, but whether to let this person have anything is a decision the
 * offering phone puts to its owner.
 *
 * <p>Holds no Android types: the wire format and its bounds are unit tested.
 */
final class Hello {

    /** The format, and the first four bytes. Not a {@link Parcel}, and never mistaken for one. */
    static final byte[] MAGIC={'M','N','H','1'};

    static final int NAME_MOST=80, ADDRESS_MOST=1024, KEY_MOST=4096, ID_MOST=100;

    /** One acceptance, as it arrived. */
    static final class Said {
        final String name,address; final byte[] agreement,signing;
        /** Which thing of the offerer's was accepted, in their own words for it. */
        final String scope,target;
        /** What the offer said they could do, carried back so the offerer need not remember. */
        final boolean writes;
        /**
         * The same, as a level: Can read, Can write, or Admin. It travels in the byte {@code writes} always had, as a
         * number (see {@link Hello#said}), which a build from before reads as "writes" whatever it is but nought - so an
         * Admin acceptance is Can write to it, never more. Only ever claimed: the offerer gives the less of this and
         * what it offered.
         */
        final Sharing.Level level;
        /**
         * Whether the device saying hello takes files sent to it on their own (see {@link Drop}). Written after
         * everything else, where a build from before reads nothing: so two devices paired today know it of each
         * other from the start, without a note between them first.
         */
        final boolean files;
        /**
         * Who owns the device saying hello, for a device that is bonding as one of theirs (see {@link Persons}):
         * whether its build knows about persons at all, their id and when it was made, their name and when they
         * chose it, and what they call this device and when. Written after the files flag, where a build from
         * before reads nothing; read only on a bond, and only to decide whose id both keep and what to call things.
         */
        final boolean persons; final String person; final long personMade;
        final String yourName; final long named; final String deviceName; final long deviceNamed;
        Said(String name,String address,byte[] agreement,byte[] signing,
             String scope,String target,boolean writes) {
            this(name,address,agreement,signing,scope,target,writes,true);
        }
        Said(String name,String address,byte[] agreement,byte[] signing,
             String scope,String target,boolean writes,boolean files) {
            this(name,address,agreement,signing,scope,target,writes,files,false,"",0,"",0,"",0);
        }
        Said(String name,String address,byte[] agreement,byte[] signing,String scope,String target,boolean writes,
             boolean files,boolean persons,String person,long personMade,String yourName,long named,String deviceName,long deviceNamed) {
            this(name,address,agreement,signing,scope,target,writes?Sharing.Level.WRITE:Sharing.Level.READ,files,
                persons,person,personMade,yourName,named,deviceName,deviceNamed);
        }
        Said(String name,String address,byte[] agreement,byte[] signing,String scope,String target,Sharing.Level level,
             boolean files,boolean persons,String person,long personMade,String yourName,long named,String deviceName,long deviceNamed) {
            this.name=name;this.address=address;this.agreement=agreement;this.signing=signing;
            this.level=Pairing.offered(level);this.writes=this.level.writes();
            this.scope=scope;this.target=target;this.files=files;
            this.persons=persons&&Persons.isId(person);this.person=this.persons?person:"";this.personMade=this.persons?personMade:0;
            this.yourName=this.persons&&yourName!=null?yourName.trim():"";this.named=this.persons?named:0;
            this.deviceName=this.persons&&deviceName!=null?deviceName.trim():"";this.deviceNamed=this.persons?deviceNamed:0;
        }
        /** The same hello, saying who owns the device too. */
        Said owner(Persons.Me me) {
            return new Said(name,address,agreement,signing,scope,target,level,files,true,me.id,me.made,me.name,me.named,me.device,me.deviceNamed);
        }
        /** The same hello, claiming a level. */
        Said claiming(Sharing.Level claimed) {
            return new Said(name,address,agreement,signing,scope,target,claimed,files,persons,person,personMade,yourName,named,deviceName,deviceNamed);
        }
    }

    /** The byte a level travels as: nought to read and one to write, as ever, and Admin's own number. */
    static int said(Sharing.Level level) {
        Sharing.Level offered=Pairing.offered(level);
        return offered==Sharing.Level.READ?0:offered==Sharing.Level.WRITE?1:offered.said();
    }

    /** A byte as a level: nought reads, one writes, and a number above that is that level - or the most this build knows. */
    static Sharing.Level level(int said) {
        if(said<=0)return Sharing.Level.READ;
        Sharing.Level level=Sharing.Level.of(said);
        return level.writes()?level:Sharing.Level.WRITE;
    }

    private Hello(){}

    static byte[] wrap(Said said) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(bytes);
        out.write(MAGIC);
        out.writeByte(said(said.level));
        put(out,said.name.getBytes(StandardCharsets.UTF_8),NAME_MOST);
        put(out,said.address.getBytes(StandardCharsets.UTF_8),ADDRESS_MOST);
        put(out,said.agreement,KEY_MOST);
        put(out,said.signing,KEY_MOST);
        put(out,said.scope.getBytes(StandardCharsets.UTF_8),ID_MOST);
        put(out,said.target.getBytes(StandardCharsets.UTF_8),ID_MOST);
        // Last, and only ever added to: a build from before stops reading at the target.
        out.writeBoolean(said.files);
        // And after that, who owns this device; a build from before stops reading at the files flag.
        if(said.persons) {
            out.writeBoolean(true);
            out.write(Persons.idBytes(said.person));out.writeLong(said.personMade);
            put(out,clipped(said.yourName),NAME_MOST);out.writeLong(said.named);
            put(out,clipped(said.deviceName),NAME_MOST);out.writeLong(said.deviceNamed);
        }
        out.flush();
        return bytes.toByteArray();
    }

    /** What arrived, or null when this is not one of these. */
    static Said open(byte[] said) {
        if(said==null||said.length<MAGIC.length)return null;
        for(int at=0;at<MAGIC.length;at++)if(said[at]!=MAGIC[at])return null;
        try(DataInputStream in=new DataInputStream(new java.io.ByteArrayInputStream(said,MAGIC.length,
                said.length-MAGIC.length))) {
            Sharing.Level claimed=level(in.readUnsignedByte());
            String name=new String(get(in,NAME_MOST),StandardCharsets.UTF_8);
            String address=new String(get(in,ADDRESS_MOST),StandardCharsets.UTF_8);
            byte[] agreement=get(in,KEY_MOST), signing=get(in,KEY_MOST);
            String scope=new String(get(in,ID_MOST),StandardCharsets.UTF_8);
            String target=new String(get(in,ID_MOST),StandardCharsets.UTF_8);
            if(agreement.length==0||signing.length==0)return null;
            if(address.trim().isEmpty())return null;
            // A hello from a build from before ends at the target, and says nothing about files.
            boolean files=in.available()>0&&in.readBoolean();
            Said whole=new Said(name.trim().isEmpty()?"Their device":name.trim(),address.trim(),
                agreement,signing,scope.trim(),target.trim(),claimed,files,false,"",0,"",0,"",0);
            // Who owns it, read on its own: a tail that is damaged leaves a hello that was whole without it.
            try {
                if(in.available()<=0||!in.readBoolean())return whole;
                byte[] person=new byte[Persons.ID];in.readFully(person);long made=in.readLong();
                String yours=new String(get(in,NAME_MOST),StandardCharsets.UTF_8);long named=in.readLong();
                String device=new String(get(in,NAME_MOST),StandardCharsets.UTF_8);long deviceNamed=in.readLong();
                return new Said(whole.name,whole.address,agreement,signing,whole.scope,whole.target,claimed,files,
                    true,Courier.hex(person),made,yours,named,device,deviceNamed);
            } catch(IOException | IllegalArgumentException damaged){return whole;}
        } catch(IOException | IllegalArgumentException broken) {
            // Half of one of these is not one of these.
            return null;
        }
    }

    /** A name as it is written: never longer than a name may be, whatever it was typed as. */
    private static byte[] clipped(String said) {
        byte[] bytes=(said==null?"":said).getBytes(StandardCharsets.UTF_8);
        if(bytes.length<=NAME_MOST)return bytes;
        String shorter=said;
        while(shorter.getBytes(StandardCharsets.UTF_8).length>NAME_MOST)shorter=shorter.substring(0,shorter.length()-1);
        return shorter.getBytes(StandardCharsets.UTF_8);
    }

    private static void put(DataOutputStream out,byte[] bytes,int most) throws IOException {
        if(bytes.length>most)throw new IOException("Too long to send: "+bytes.length+" of "+most);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static byte[] get(DataInputStream in,int most) throws IOException {
        int length=in.readInt();
        if(length<0||length>most)throw new IllegalArgumentException("A field said it was "+length+" long");
        byte[] bytes=new byte[length];
        in.readFully(bytes);
        return bytes;
    }
}
