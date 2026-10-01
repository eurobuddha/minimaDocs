// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A collection travelling on its own (see docs/HOME.md, *Messages*).
 *
 * <p>A collection used to exist on the far side only through the notes in it: a {@link Parcel} carries the shelf it
 * stood on, and the shelf is built there. A collection with nothing in it yet, or with only files, never went
 * anywhere. This is what it goes in: its row - the sender's id, its name, icon, colour and picture, where it sits
 * among what is beside it - the collections above it, who has it, and the files it keeps.
 *
 * <p>Sealed and answered like a note, under the envelope id {@link Things#envelopeId} gives it and the collection's
 * own revision. Only ever sent to a device that has said {@link Receipt#TREE}: a build from before takes anything it
 * does not know for a note written the oldest way, and would write these bytes over somebody's words.
 *
 * <p>Holds no Android types: the wire format and its bounds are unit tested.
 */
final class Carton {
    /** The format, and the first four bytes. Not a {@link Parcel}, and never mistaken for one. */
    static final byte[] MAGIC={'M','N','C','1'};

    /** One collection, as it travels. */
    static final class Sent {
        /** The sender's id for it, what it is called, its icon by name and its picture (empty where none). */
        final String id,name,icon; final byte[] image;
        /** Its colour, which colour and not a pixel value, and where the sender keeps it among what is beside it. */
        final int tint; final long ordinal;
        /** The collections above it from Home down, as a parcel's path says them; empty for one on Home. */
        final java.util.List<Parcel.Step> path;
        /** What the far end may do in it: false reads it, true writes in it too. */
        final boolean writes;
        /** Who has it and at what standing, as a parcel's list: which thing, at the sender's end, and the list. */
        final String scope,target; final java.util.List<Parcel.Member> members;
        /** Whether the sender wants to hear it arrived; always, from a build that sends these. */
        final boolean answer;
        /** The files it keeps at the sender's end, or null where the sender did not say; and when that list was made. */
        final java.util.List<Enclosure.Listed> files; final long filesAsOf;
        Sent(String id,String name,String icon,byte[] image,int tint,long ordinal,java.util.List<Parcel.Step> path,
             boolean writes,String scope,String target,java.util.List<Parcel.Member> members,boolean answer,
             java.util.List<Enclosure.Listed> files,long filesAsOf) {
            this.id=id==null?"":id;this.name=name==null?"":name;this.icon=icon==null?"":icon;
            this.image=image==null?new byte[0]:image;this.tint=tint;this.ordinal=ordinal;
            this.path=path==null?java.util.Collections.<Parcel.Step>emptyList():path;
            this.writes=writes;this.scope=scope==null?"":scope;this.target=target==null?"":target;
            this.members=members==null?java.util.Collections.<Parcel.Member>emptyList():members;
            this.answer=answer;this.files=files;this.filesAsOf=filesAsOf;
        }
    }

    private Carton(){}

    static byte[] wrap(Sent sent) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(bytes);
        out.write(MAGIC);
        out.writeBoolean(sent.writes);
        Parcel.put(out,sent.id,Parcel.ID_MOST);
        Parcel.put(out,sent.name,Parcel.NAME_MOST);
        // The collections above it, and then its own icon and picture, as a parcel says a note's.
        Parcel.writePath(out,sent.path,sent.icon,sent.image);
        out.writeInt(sent.tint);
        out.writeLong(sent.ordinal);
        Parcel.put(out,sent.scope,Parcel.ID_MOST);
        Parcel.put(out,sent.target,Parcel.ID_MOST);
        int many=Math.min(sent.members.size(),Parcel.MEMBERS_MOST);
        out.writeInt(many);
        for(int at=0;at<many;at++) {
            Parcel.Member one=sent.members.get(at);
            Parcel.put(out,one.key,Parcel.KEY_MOST);
            Parcel.put(out,one.address,Parcel.ADDRESS_MOST);
            Parcel.put(out,one.name,Parcel.NAME_MOST);
            out.writeInt(one.level);
            out.writeLong(one.changed);
            byte[] agreement=one.agreement.length<=Parcel.KEY_MOST?one.agreement:new byte[0];
            out.writeInt(agreement.length);out.write(agreement);
        }
        out.writeBoolean(sent.answer);
        // Said or not said, and never taken one for the other: an empty list takes out what came from here before.
        out.writeBoolean(sent.files!=null);
        if(sent.files!=null) {
            out.writeLong(sent.filesAsOf);
            int files=Math.min(sent.files.size(),Enclosure.FILES_MOST);
            out.writeInt(files);
            for(int at=0;at<files;at++) {
                Enclosure.Listed one=sent.files.get(at);
                Parcel.put(out,one.id,Parcel.ID_MOST);
                Parcel.put(out,one.name,Parcel.NAME_MOST);
                Parcel.put(out,one.kind,Parcel.NAME_MOST);
                out.writeLong(one.bytes);
                Parcel.put(out,one.manifest,Parcel.MANIFEST_MOST);
            }
        }
        // Anything a later build adds goes after this, where this one stops reading.
        out.flush();
        return bytes.toByteArray();
    }

    /**
     * The same, made to fit in {@code most} bytes, as a note's parcel is (see {@link Parcel#wrap(Parcel.Sent,int)}): where
     * the list of files will not fit, the files go on being named but stop saying where, the biggest first, and where
     * even their names will not fit the list is not said at all - which takes nothing out at the far end.
     */
    static byte[] wrap(Sent sent,int most) throws IOException {
        byte[] whole=wrap(sent);
        if(whole.length<=most||sent.files==null)return whole;
        java.util.List<Enclosure.Listed> lighter=new java.util.ArrayList<>(sent.files);
        while(true) {
            int biggest=-1;
            for(int at=0;at<lighter.size();at++)
                if(!lighter.get(at).manifest.isEmpty()&&(biggest<0||lighter.get(at).manifest.length()>lighter.get(biggest).manifest.length()))biggest=at;
            if(biggest<0)break;
            lighter.set(biggest,lighter.get(biggest).unsaid());
            byte[] tried=wrap(with(sent,lighter));
            if(tried.length<=most)return tried;
        }
        return wrap(with(sent,null));
    }

    private static Sent with(Sent sent,java.util.List<Enclosure.Listed> files) {
        return new Sent(sent.id,sent.name,sent.icon,sent.image,sent.tint,sent.ordinal,sent.path,sent.writes,sent.scope,sent.target,
            sent.members,sent.answer,files,sent.filesAsOf);
    }

    /** What arrived, or null when this is not one - or is not one whole: half a collection is not a collection. */
    static Sent open(byte[] said) {
        if(said==null||said.length<MAGIC.length)return null;
        for(int at=0;at<MAGIC.length;at++)if(said[at]!=MAGIC[at])return null;
        try(DataInputStream in=new DataInputStream(new java.io.ByteArrayInputStream(said,MAGIC.length,said.length-MAGIC.length))) {
            boolean writes=in.readBoolean();
            String id=Parcel.get(in,Parcel.ID_MOST), name=Parcel.get(in,Parcel.NAME_MOST);
            if(id.trim().isEmpty())return null;
            Parcel.Path path=Parcel.readPath(in);
            if(path==null)return null;
            int tint=in.readInt();long ordinal=in.readLong();
            String scope=Parcel.get(in,Parcel.ID_MOST), target=Parcel.get(in,Parcel.ID_MOST);
            int many=in.readInt();
            if(many<0||many>Parcel.MEMBERS_MOST)return null;
            java.util.List<Parcel.Member> members=new java.util.ArrayList<>(many);
            for(int at=0;at<many;at++) {
                String key=Parcel.get(in,Parcel.KEY_MOST), address=Parcel.get(in,Parcel.ADDRESS_MOST), who=Parcel.get(in,Parcel.NAME_MOST);
                int level=in.readInt();long changed=in.readLong();
                int length=in.readInt();
                if(length<0||length>Parcel.KEY_MOST)return null;
                byte[] agreement=new byte[length];in.readFully(agreement);
                members.add(new Parcel.Member(key,address,who,level,changed,agreement));
            }
            boolean answer=in.readBoolean();
            java.util.List<Enclosure.Listed> files=null;long asOf=0L;
            if(in.readBoolean()) {
                asOf=in.readLong();
                int count=in.readInt();
                if(count<0||count>Enclosure.FILES_MOST)return null;
                files=new java.util.ArrayList<>(count);
                for(int at=0;at<count;at++) {
                    String fileId=Parcel.get(in,Parcel.ID_MOST), fileName=Parcel.get(in,Parcel.NAME_MOST), kind=Parcel.get(in,Parcel.NAME_MOST);
                    long size=in.readLong();
                    if(size<0)return null;
                    files.add(new Enclosure.Listed(fileId,fileName,kind,size,Parcel.get(in,Parcel.MANIFEST_MOST)));
                }
            }
            return new Sent(id,name,path.icon,path.image,tint,ordinal,path.steps,writes,scope,target,members,answer,files,asOf);
        } catch(IOException | IllegalArgumentException broken){return null;}
    }
}
