// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * A recording made in a note, and an audio file kept with one: what it is called, how long it is, and when a
 * recording has to stop. The phone records AAC in .m4a and the PC μ-law in .wav; both keep the result through
 * the ordinary attachment path, so it is sealed when the notebook is locked and travels with a shared note.
 * Holds no Android types, so these rules are unit tested on both.
 */
final class Recording {
    /** The most a recording may be: what a file may be and still travel with the note (Enclosure.MOST). */
    static final long MOST=Enclosure.MOST;
    /**
     * Where a recording is stopped, a little under the ceiling: the phone's recorder writes its index after it
     * stops and overshoots by a moment's worth, and a recording one byte over would stay behind on this device.
     */
    static final long STOP_AT=MOST-256L*1024;
    /** Said when a recording reaches the ceiling and is stopped there. */
    static final String CAPPED="Stopped at 16 MB so it can travel with the note";

    /** The phone: AAC at 48 kbps, one channel - clear speech, and about three quarters of an hour under the ceiling. */
    static final int PHONE_BITS=48_000, PHONE_RATE=44_100;
    /** The PC: μ-law, 16,000 samples a second, one byte each - Java has no AAC, and this is half the size of plain WAV. */
    static final int PC_RATE=16_000;

    /** How many whole minutes fit under the ceiling at this many bytes a second. */
    static int minutes(long bytesPerSecond){return bytesPerSecond<=0?0:(int)(STOP_AT/bytesPerSecond/60);}

    /** The quiet words in the recording bar: how long it can go on, and what leaving does. */
    static String limit(long bytesPerSecond){return "Up to "+minutes(bytesPerSecond)+" min · leaving keeps it";}

    private static final DateTimeFormatter NAMED=DateTimeFormatter.ofPattern("d MMM, HH:mm",Locale.ENGLISH);

    /** "Recording 28 Sep, 14:05.m4a": when it was made, which is what a person remembers it by. */
    static String name(long when,String extension){return name(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(when),ZoneId.systemDefault()),extension);}
    static String name(ZonedDateTime when,String extension){return "Recording "+NAMED.format(when)+"."+extension;}

    /** A length as a clock says it: 0:07, 12:34, 1:02:03. */
    static String clock(long millis) {
        long seconds=Math.max(0,millis)/1000,h=seconds/3600,m=seconds/60%60,s=seconds%60;
        return h>0?String.format(Locale.ROOT,"%d:%02d:%02d",h,m,s):String.format(Locale.ROOT,"%d:%02d",m,s);
    }

    private static final String[] SOUNDS={".m4a",".mp3",".wav",".aac",".ogg",".oga",".opus",".flac",".amr",".3gp",".mka",".weba"};
    /** Whether a kept file is something to listen to: by its type, or by its name when the type says nothing. */
    static boolean audio(String name,String kind) {
        if(kind!=null&&kind.startsWith("audio/"))return true;
        String lower=name==null?"":name.toLowerCase(Locale.ROOT);
        for(String end:SOUNDS)if(lower.endsWith(end))return true;
        return false;
    }
    /** Whether it is a WAV, which Java Sound plays on the PC; anything else there goes to the PC's own player. */
    static boolean wav(String name,String kind) {
        return (kind!=null&&(kind.equals("audio/wav")||kind.equals("audio/x-wav")||kind.equals("audio/wave")))
            ||(name!=null&&name.toLowerCase(Locale.ROOT).endsWith(".wav"));
    }

    /**
     * How long a recording is, read from its own bytes: a WAV's format and data chunks, or an MP4's movie
     * header. -1 when it is neither, or says nothing that adds up. Read rather than played, so the card can say
     * it before anybody presses ▶ - and the same on the PC, which cannot decode what the phone records.
     */
    static long millis(byte[] all) {
        if(all==null||all.length<12)return -1;
        if(is(all,0,"RIFF")&&is(all,8,"WAVE"))return wavMillis(all);
        return mp4Millis(all,0,all.length);
    }

    private static long wavMillis(byte[] all) {
        long perSecond=-1;
        for(int at=12;at+8<=all.length;) {
            long size=le32(all,at+4);
            if(is(all,at,"fmt ")&&at+20<=all.length)perSecond=le32(all,at+16);
            // The data chunk's own size, or what is there when a writer stopped before filling it in.
            if(is(all,at,"data"))return perSecond<=0?-1:Math.min(size,all.length-at-8L)*1000/perSecond;
            long next=at+8L+size+(size&1);
            if(next>all.length)return -1;
            at=(int)next;
        }
        return -1;
    }

    /** The movie header's length over its timescale, found in the moov box wherever it is: the phone writes it last. */
    private static long mp4Millis(byte[] all,int from,int to) {
        for(int at=from;at+8<=to;) {
            long size=be32(all,at);int head=8;
            if(size==1){if(at+16>to)return -1;size=be64(all,at+8);head=16;}
            else if(size==0)size=to-at;
            if(size<head||at+size>to)return -1;
            int end=(int)(at+size);
            if(is(all,at+4,"moov"))return mp4Millis(all,at+head,end);
            if(is(all,at+4,"mvhd")) {
                int v=at+head;if(v+4>end)return -1;
                boolean wide=all[v]==1;int fields=v+4+(wide?16:8);
                if(fields+(wide?12:8)>end)return -1;
                long scale=be32(all,fields),length=wide?be64(all,fields+4):be32(all,fields+4);
                return scale<=0||length<0?-1:length*1000/scale;
            }
            at=end;
        }
        return -1;
    }

    private static boolean is(byte[] b,int at,String four) {
        if(at+4>b.length)return false;
        for(int i=0;i<4;i++)if(b[at+i]!=(byte)four.charAt(i))return false;
        return true;
    }
    private static long le32(byte[] b,int at){return (b[at]&0xFFL)|(b[at+1]&0xFFL)<<8|(b[at+2]&0xFFL)<<16|(b[at+3]&0xFFL)<<24;}
    private static long be32(byte[] b,int at){return (b[at]&0xFFL)<<24|(b[at+1]&0xFFL)<<16|(b[at+2]&0xFFL)<<8|(b[at+3]&0xFFL);}
    private static long be64(byte[] b,int at){return be32(b,at)<<32|be32(b,at+4);}

    private Recording(){}
}
