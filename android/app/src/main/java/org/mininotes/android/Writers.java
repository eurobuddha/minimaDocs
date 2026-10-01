// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Who wrote each letter of a note, as runs: so many letters by one writer, then so many by another.
 *
 * <p>Each letter is credited as it is typed, to whoever typed it here. A note that arrives from another device is
 * read against what this device already had, and only the letters that are new here are credited to whoever sent
 * it: the rest keep whoever they already had. The versions a note has had cannot say this - a version records who
 * sent it, not who wrote each part of it - so this is kept beside the note, on this device only. It is never sent
 * and never part of what is sent, so every device, of any version, reads what this one sends as it always did.
 *
 * <p>Text written before this was kept is nobody's: {@link #UNKNOWN}, drawn in the ordinary ink, never guessed at.
 * So is text put back from an old version, whose writers were never kept. Where the words are passed on by a third
 * person, they arrive from that person, and are theirs here: the one limit of keeping this on each device alone.
 *
 * <p>Holds no Android types, so keeping runs through every kind of edit, and reading an arrival, are unit tested.
 */
final class Writers {
    /** Nobody known: text from before writers were kept, or put back from an old version. The ordinary ink. */
    static final String UNKNOWN="";
    /** Whoever owns this device, on every device of theirs: "You". */
    static final String ME="me";

    /** Another person's device, by the key it signs with, which does not move as its address does. */
    static String byKey(String key){return key==null||key.isEmpty()?UNKNOWN:"k"+key;}
    /** A device only known by where it is, for want of a key. */
    static String byAddress(String address){return address==null||address.isEmpty()?UNKNOWN:"a"+address;}

    private int[] lengths=new int[4];
    private String[] by=new String[4];
    private int runs;

    private Writers(){}

    /** No letters at all. */
    static Writers none(){return new Writers();}
    /** So many letters, nobody's. */
    static Writers unknown(int length){return of(UNKNOWN,length);}
    /** So many letters, all by one writer. */
    static Writers of(String writer,int length) {
        Writers out=new Writers();
        if(length>0)out.add(writer,length);
        return out;
    }

    Writers copy() {
        Writers out=new Writers();
        out.lengths=Arrays.copyOf(lengths,Math.max(4,runs));out.by=Arrays.copyOf(by,Math.max(4,runs));out.runs=runs;
        return out;
    }

    /** How many letters these runs cover: always the length of the text they are about. */
    int length(){int all=0;for(int i=0;i<runs;i++)all+=lengths[i];return all;}
    /** How many runs there are, and each one: its length and its writer. */
    int count(){return runs;}
    int lengthOf(int run){return lengths[run];}
    String writerOf(int run){return by[run];}

    /** Who wrote the letter at a place, or {@link #UNKNOWN} past the end. */
    String at(int offset) {
        int start=0;
        for(int i=0;i<runs;i++){if(offset<start+lengths[i])return offset>=start?by[i]:UNKNOWN;start+=lengths[i];}
        return UNKNOWN;
    }

    /** Everybody with at least one letter here, in order, leaving out {@link #UNKNOWN}. */
    Set<String> writers() {
        Set<String> out=new LinkedHashSet<>();
        for(int i=0;i<runs;i++)if(!by[i].isEmpty())out.add(by[i]);
        return out;
    }

    /**
     * An edit, as a text field reports one: at {@code at}, {@code removed} letters became {@code inserted} new ones,
     * and the new ones are {@code writer}'s. Typing, deleting, replacing a selection and pasting are all this. The
     * letters around the edit keep whoever wrote them, so typing inside somebody else's words makes only the new
     * letters yours.
     */
    void edit(int at,int removed,int inserted,String writer) {
        int length=length();
        int from=Math.max(0,Math.min(at,length)), to=Math.max(from,Math.min(from+Math.max(0,removed),length));
        int first=split(from), last=split(to);
        remove(first,last);
        if(inserted>0)insert(first,writer,inserted);
        tidy();
    }

    /** The run that starts at this offset, splitting one where it falls inside it. {@link #count} past the end. */
    private int split(int offset) {
        int start=0;
        for(int i=0;i<runs;i++) {
            if(offset==start)return i;
            if(offset<start+lengths[i]) {
                insert(i+1,by[i],start+lengths[i]-offset);
                lengths[i]=offset-start;
                return i+1;
            }
            start+=lengths[i];
        }
        return runs;
    }

    private void remove(int from,int to) {
        if(to<=from)return;
        System.arraycopy(lengths,to,lengths,from,runs-to);System.arraycopy(by,to,by,from,runs-to);
        for(int i=runs-(to-from);i<runs;i++)by[i]=null;
        runs-=to-from;
    }

    private void insert(int at,String writer,int length) {
        if(runs==lengths.length){lengths=Arrays.copyOf(lengths,runs*2);by=Arrays.copyOf(by,runs*2);}
        System.arraycopy(lengths,at,lengths,at+1,runs-at);System.arraycopy(by,at,by,at+1,runs-at);
        lengths[at]=length;by[at]=writer==null?UNKNOWN:writer;runs++;
    }

    private void add(String writer,int length) {
        String who=writer==null?UNKNOWN:writer;
        if(length<=0)return;
        if(runs>0&&by[runs-1].equals(who)){lengths[runs-1]+=length;return;}
        insert(runs,who,length);
    }

    /** Empty runs gone, and two runs of the same writer side by side made one. */
    private void tidy() {
        int kept=0;
        for(int i=0;i<runs;i++) {
            if(lengths[i]==0)continue;
            if(kept>0&&by[kept-1].equals(by[i])){lengths[kept-1]+=lengths[i];continue;}
            lengths[kept]=lengths[i];by[kept]=by[i];kept++;
        }
        for(int i=kept;i<runs;i++)by[i]=null;
        runs=kept;
    }

    // ---- what arrived ---------------------------------------------------------------------------------------------

    /**
     * The writers of a note that has just been given new words from somewhere: every letter that was here already
     * keeps whoever wrote it, and only what is new here is {@code writer}'s. See {@link #matched} for how the letters
     * that were here are found.
     *
     * @param was     what this device's note said before
     * @param before  who wrote that, or null where nobody is known
     * @param now     what it says now
     */
    static Writers arrived(String was,Writers before,String now,String writer) {
        return follow(now,writer,was,before);
    }

    /**
     * The writers of a text put together from others: each letter found in the first source, in order, keeps who
     * wrote it there; one found only in the second keeps who wrote it there; any other letter is {@code writer}'s.
     * What a merge makes, or a page that had words typed on it while the note underneath it moved on.
     *
     * @param sources pairs of a text and its writers (null for nobody known), the one to ask first first
     */
    static Writers follow(String text,String writer,Object... sources) {
        String made=text==null?"":text;
        String[] each=new String[made.length()];
        for(int s=0;s+1<sources.length;s+=2) {
            String from=sources[s]==null?"":(String)sources[s];
            Writers runs=(Writers)sources[s+1];
            if(runs==null||runs.length()!=from.length())runs=unknown(from.length());
            String[] said=runs.spread();
            int[] where=matched(from,made);
            for(int i=0;i<each.length;i++)if(each[i]==null&&where[i]>=0)each[i]=said[where[i]];
        }
        Writers out=new Writers();
        for(String one:each)out.add(one==null?writer:one,1);
        return out;
    }

    /** One writer per letter. */
    private String[] spread() {
        String[] out=new String[length()];
        int at=0;
        for(int i=0;i<runs;i++)for(int n=0;n<lengths[i];n++)out[at++]=by[i];
        return out;
    }

    /**
     * Where places in a text are once it has become another: each at the same letter, where that letter is still
     * there, and otherwise just after the nearest letter before it that is. What keeps a reader where they were when
     * the note under the page changes - the first line they could see, the cursor, the selection - so words arriving
     * above them push their place down with the words it was on, rather than the page jumping to wherever the cursor
     * happens to be.
     *
     * @param offsets places in {@code from}, each between 0 and its length
     * @return the same places in {@code to}, in the same order
     */
    static int[] moved(String from,String to,int... offsets) {
        String a=from==null?"":from, b=to==null?"":to;
        int[] where=matched(a,b), back=new int[a.length()];
        Arrays.fill(back,-1);
        for(int j=0;j<where.length;j++)if(where[j]>=0)back[where[j]]=j;
        // For each place in the old text, the place in the new one just after the last letter before it that is still there.
        int[] after=new int[a.length()+1];
        for(int i=0;i<a.length();i++)after[i+1]=back[i]>=0?back[i]+1:after[i];
        int[] out=new int[offsets.length];
        for(int k=0;k<offsets.length;k++) {
            int at=Math.max(0,Math.min(offsets[k],a.length()));
            out[k]=at<a.length()&&back[at]>=0?back[at]:Math.min(after[at],b.length());
        }
        return out;
    }

    /** As many lines as are compared two by two; beyond it, a stretch is read letter by letter or not at all. */
    static final long LINES_MOST=2_000_000L;
    /** As many letters as are compared two by two, inside a stretch of lines that changed. */
    static final long LETTERS_MOST=1_000_000L;
    /**
     * Fewer letters than this in a row, found alike inside words that changed, are a coincidence and not the same
     * writing: "cat" and "dog" share nothing, "hello" and "goodbye" share an "o" or two, and crediting those to
     * whoever had them before would speckle the new words with the old writer's colour.
     */
    static final int ALIKE_LEAST=3;

    /**
     * For each letter of {@code to}, where the same letter stands in {@code from}, or -1 where it is new. What the
     * two texts share, in order: first the lines they start and end with, then the lines between that are the same,
     * then, inside lines that changed, the letters they start and end with and the stretches between that are alike.
     * Lines first, so a line moved from the end to the start is not read as its last letters staying where they were.
     */
    static int[] matched(String from,String to) {
        String a=from==null?"":from, b=to==null?"":to;
        int[] where=new int[b.length()];
        Arrays.fill(where,-1);
        lines(a,b,where);
        return where;
    }

    /** The lines the two share, in order, then the letters of what lies between them. */
    private static void lines(String a,String b,int[] where) {
        List<int[]> old=split(a,0,a.length()), now=split(b,0,b.length());
        // Each line by a number, the same number for the same words, so lines are compared as numbers.
        Map<String,Integer> names=new HashMap<>();
        int[] one=new int[old.size()], two=new int[now.size()];
        for(int i=0;i<one.length;i++){int[] l=old.get(i);one[i]=names.computeIfAbsent(a.substring(l[0],l[1]),k->names.size());}
        for(int i=0;i<two.length;i++){int[] l=now.get(i);two[i]=names.computeIfAbsent(b.substring(l[0],l[1]),k->names.size());}
        block(new Side(a,old,one),0,one.length,new Side(b,now,two),0,two.length,where);
    }

    /** One of the two texts, by its lines: where each is, and its number. */
    private static final class Side {
        final String text; final List<int[]> lines; final int[] names;
        Side(String text,List<int[]> lines,int[] names){this.text=text;this.lines=lines;this.names=names;}
        /** Where the letters of lines {@code [from,to)} start and end. */
        int start(int from){return from<lines.size()?lines.get(from)[0]:text.length();}
        int end(int from,int to){return to>from?lines.get(to-1)[1]:start(from);}
    }

    /**
     * Lines {@code [i0,i1)} of one text against lines {@code [j0,j1)} of the other: the lines they start and end with,
     * then the longest run of lines they share in order where that is few enough lines to compare two by two, and
     * otherwise the lines that are once in each - as sure a sign as there is that it is the same line - taken as fixed
     * points, and what lies between each two of them read the same way. Letters for whatever is left.
     */
    private static void block(Side a,int i0,int i1,Side b,int j0,int j1,int[] where) {
        while(i0<i1&&j0<j1&&a.names[i0]==b.names[j0])pair(a.lines.get(i0++),b.lines.get(j0++),where);
        while(i1>i0&&j1>j0&&a.names[i1-1]==b.names[j1-1])pair(a.lines.get(--i1),b.lines.get(--j1),where);
        int x=i1-i0, y=j1-j0;
        if(x<2&&y<2||x==0||y==0){letters(a.text,a.start(i0),a.end(i0,i1),b.text,b.start(j0),b.end(j0,j1),where);return;}
        if((long)x*y<=LINES_MOST){shared(a,i0,i1,b,j0,j1,where);return;}
        int[][] fixed=unique(a,i0,i1,b,j0,j1);
        if(fixed.length==0){letters(a.text,a.start(i0),a.end(i0,i1),b.text,b.start(j0),b.end(j0,j1),where);return;}
        int i=i0, j=j0;
        for(int[] one:fixed) {
            block(a,i,one[0],b,j,one[1],where);
            pair(a.lines.get(one[0]),b.lines.get(one[1]),where);
            i=one[0]+1;j=one[1]+1;
        }
        block(a,i,i1,b,j,j1,where);
    }

    /** The longest run of lines the two share, in order, and the letters of what lies between them. */
    private static void shared(Side a,int i0,int i1,Side b,int j0,int j1,int[] where) {
        int x=i1-i0, y=j1-j0;
        int[][] alike=new int[x+1][y+1];
        for(int i=x-1;i>=0;i--)for(int j=y-1;j>=0;j--)
            alike[i][j]=a.names[i0+i]==b.names[j0+j]?alike[i+1][j+1]+1:Math.max(alike[i+1][j],alike[i][j+1]);
        int i=0, j=0, gapA=a.start(i0), gapB=b.start(j0);
        while(i<x&&j<y) {
            if(a.names[i0+i]==b.names[j0+j]) {
                int[] l=a.lines.get(i0+i), r=b.lines.get(j0+j);
                letters(a.text,gapA,l[0],b.text,gapB,r[0],where);
                pair(l,r,where);
                gapA=l[1];gapB=r[1];i++;j++;
            }
            else if(alike[i+1][j]>=alike[i][j+1])i++;
            else j++;
        }
        letters(a.text,gapA,a.end(i0,i1),b.text,gapB,b.end(j0,j1),where);
    }

    /**
     * The lines that are there once in each stretch, as pairs of where each is, the longest run of them that stays in
     * order on both sides.
     */
    private static int[][] unique(Side a,int i0,int i1,Side b,int j0,int j1) {
        Map<Integer,int[]> seen=new HashMap<>();
        for(int i=i0;i<i1;i++)seen.computeIfAbsent(a.names[i],k->new int[]{0,-1,0,-1});
        for(int i=i0;i<i1;i++){int[] c=seen.get(a.names[i]);c[0]++;c[1]=i;}
        for(int j=j0;j<j1;j++){int[] c=seen.get(b.names[j]);if(c!=null){c[2]++;c[3]=j;}}
        List<int[]> pairs=new ArrayList<>();
        for(int i=i0;i<i1;i++){int[] c=seen.get(a.names[i]);if(c[0]==1&&c[2]==1)pairs.add(new int[]{c[1],c[3]});}
        // The longest run whose places in the second text rise as they do in the first.
        int n=pairs.size();
        int[] tails=new int[n], before=new int[n];
        int length=0;
        for(int k=0;k<n;k++) {
            int low=0, high=length;
            while(low<high){int mid=(low+high)>>>1;if(pairs.get(tails[mid])[1]<pairs.get(k)[1])low=mid+1;else high=mid;}
            before[k]=low>0?tails[low-1]:-1;
            tails[low]=k;
            if(low==length)length++;
        }
        int[][] out=new int[length][];
        for(int k=length>0?tails[length-1]:-1, at=length-1;k>=0;k=before[k],at--)out[at]=pairs.get(k);
        return out;
    }

    private static void pair(int[] l,int[] r,int[] where){for(int k=0;k<r[1]-r[0];k++)where[r[0]+k]=l[0]+k;}

    /** Lines as {start,end} with the end of each line kept on it, so that together they are the whole stretch. */
    private static List<int[]> split(String text,int from,int to) {
        List<int[]> out=new ArrayList<>();
        int start=from;
        for(int at=from;at<to;at++)if(text.charAt(at)=='\n'){out.add(new int[]{start,at+1});start=at+1;}
        if(start<to)out.add(new int[]{start,to});
        return out;
    }

    /** Inside a stretch that changed: its own shared start and end, then the runs of letters alike in between. */
    private static void letters(String a,int aFrom,int aTo,String b,int bFrom,int bTo,int[] where) {
        int aStart=aFrom, bStart=bFrom;
        while(aFrom<aTo&&bFrom<bTo&&a.charAt(aFrom)==b.charAt(bFrom))where[bFrom++]=aFrom++;
        while(aFrom<aTo&&bFrom<bTo&&a.charAt(aTo-1)==b.charAt(bTo-1))where[--bTo]=--aTo;
        int n=aTo-aFrom, m=bTo-bFrom;
        // Words added or taken out and nothing else. Which letters were "added" is a matter of where they are read
        // from: "one two three" less "two " can as well be read as less "wo t", and then the t of "three" would keep the
        // writer of "two". So the stretch is moved back to where a word starts, where it can be.
        if(n==0&&m>0) {
            int back=slide(b,bFrom,bTo,bFrom-bStart);
            for(int k=1;k<=back;k++)where[bFrom-k]=-1;
            for(int k=1;k<=back;k++)where[bTo-k]=aFrom-k;
            return;
        }
        if(m==0&&n>0) {
            int back=slide(a,aFrom,aTo,aFrom-aStart);
            for(int k=1;k<=back;k++)where[bFrom-k]=aTo-k;
            return;
        }
        if(n<=0||m<=0||(long)n*m>LETTERS_MOST)return;
        int[][] alike=new int[n+1][m+1];
        for(int i=n-1;i>=0;i--)for(int j=m-1;j>=0;j--)
            alike[i][j]=a.charAt(aFrom+i)==b.charAt(bFrom+j)?alike[i+1][j+1]+1:Math.max(alike[i+1][j],alike[i][j+1]);
        int[] pair=new int[m];
        Arrays.fill(pair,-1);
        int i=0, j=0;
        while(i<n&&j<m) {
            if(a.charAt(aFrom+i)==b.charAt(bFrom+j)){pair[j]=i;i++;j++;}
            else if(alike[i+1][j]>=alike[i][j+1])i++;
            else j++;
        }
        // Only runs long enough to be the same writing, not a letter two different words happen to share.
        for(int start=0;start<m;) {
            if(pair[start]<0){start++;continue;}
            int end=start+1;
            while(end<m&&pair[end]==pair[end-1]+1)end++;
            if(end-start>=ALIKE_LEAST)for(int k=start;k<end;k++)where[bFrom+k]=aFrom+pair[k];
            start=end;
        }
    }

    /**
     * How far back a stretch added or taken out can be read, the same letters either way, to start where a word
     * does: 0 where it already does, or where nothing reads better.
     *
     * @param most how far back the letters before it were read as the same on both sides
     */
    private static int slide(String text,int from,int to,int most) {
        int best=0, bestScore=boundary(text,from,to);
        for(int k=1;k<=most&&text.charAt(from-k)==text.charAt(to-k);k++) {
            int score=boundary(text,from-k,to-k);
            if(score>bestScore){best=k;bestScore=score;}
        }
        return best;
    }

    private static int boundary(String text,int from,int to) {
        return (from==0||Character.isWhitespace(text.charAt(from-1))?2:0)+(to==text.length()||Character.isWhitespace(text.charAt(to))?1:0);
    }

    // ---- kept -----------------------------------------------------------------------------------------------------

    /** The first line of what is kept, so a later way of keeping it is never read as this one. */
    static final String FORMAT="W1";

    /**
     * As kept on this device: the format, then the runs as "length.writer" with the writer as a number (-1 for
     * nobody), then each writer on a line of its own. Null where there is nothing to keep: nobody known wrote any of it.
     */
    String write() {
        if(writers().isEmpty())return null;
        List<String> names=new ArrayList<>();
        StringBuilder line=new StringBuilder();
        for(int i=0;i<runs;i++) {
            int who=-1;
            if(!by[i].isEmpty()) {
                if(by[i].indexOf('\n')>=0)return null;
                who=names.indexOf(by[i]);
                if(who<0){who=names.size();names.add(by[i]);}
            }
            if(line.length()>0)line.append(' ');
            line.append(lengths[i]).append('.').append(who);
        }
        StringBuilder out=new StringBuilder(FORMAT).append('\n').append(line);
        for(String name:names)out.append('\n').append(name);
        return out.toString();
    }

    /**
     * What was kept, read back for a text of this length. Anything that does not fit it - another format, a count
     * that does not add up to the text - is nobody's rather than a guess.
     */
    static Writers read(String kept,int length) {
        if(kept==null||kept.isEmpty())return unknown(length);
        try {
            String[] lines=kept.split("\n",-1);
            if(lines.length<2||!FORMAT.equals(lines[0]))return unknown(length);
            Writers out=new Writers();
            if(!lines[1].isEmpty())for(String run:lines[1].split(" ")) {
                int dot=run.indexOf('.');
                int many=Integer.parseInt(run.substring(0,dot)), who=Integer.parseInt(run.substring(dot+1));
                if(many<0||who<-1||who+2>lines.length)return unknown(length);
                out.add(who<0?UNKNOWN:lines[2+who],many);
            }
            return out.length()==length?out:unknown(length);
        } catch(RuntimeException unreadable) {
            return unknown(length);
        }
    }

    // ---- colours --------------------------------------------------------------------------------------------------

    /**
     * Which colour each writer is drawn in, on this device.
     *
     * <p>Your own devices are one person, "You", in the colour you chose, which can be the ordinary ink. Anybody else
     * is in the colour you gave them here, or else one of their own, worked out from their key, so every device that
     * has not been told otherwise draws them alike. What you choose is kept on this device and goes nowhere.
     */
    static final class Palette {
        /** Your colour, or {@link Tint#NONE} for the ordinary ink. */
        final int mine;
        /** Colours you gave other people here, by writer. */
        final Map<String,Integer> chosen;
        /** The writers that are devices of yours: they are you. */
        final Set<String> own;
        Palette(int mine,Map<String,Integer> chosen,Set<String> own) {
            this.mine=Tint.known(mine)?mine:Tint.NONE;
            this.chosen=chosen==null?new HashMap<>():new HashMap<>(chosen);
            this.own=own==null?new LinkedHashSet<>():new LinkedHashSet<>(own);
        }
        static Palette plain(){return new Palette(Tint.NONE,null,null);}

        /** Who a writer is: {@link #ME} for any device of yours, null for nobody known. */
        String person(String writer) {
            if(writer==null||writer.isEmpty())return null;
            return writer.equals(ME)||own.contains(writer)?ME:writer;
        }

        /** The colour a writer's letters are drawn in, or {@link Tint#NONE} for the ordinary ink. */
        int colourOf(String writer) {
            String who=person(writer);
            if(who==null)return Tint.NONE;
            if(who.equals(ME))return mine;
            Integer given=chosen.get(who);
            return given!=null&&Tint.known(given)?given:automatic(who);
        }

        /** Whether a writer has a colour you gave them, rather than their own. */
        boolean chose(String writer){String who=person(writer);return who!=null&&chosen.containsKey(who)&&!who.equals(ME);}

        /**
         * Whether a note is drawn in its writers' colours: only where two or more people wrote in it, so a note only
         * you wrote in looks exactly as it always did.
         */
        boolean shows(Writers runs) {
            if(runs==null)return false;
            Set<String> people=new LinkedHashSet<>();
            for(int i=0;i<runs.runs;i++){String who=person(runs.by[i]);if(who!=null)people.add(who);}
            return people.size()>=2;
        }
    }

    /**
     * Somebody's own colour where nobody chose one: one of the eight, from their key, so every device that has not
     * been told otherwise gives them the same one.
     */
    static int automatic(String writer) {
        if(writer==null||writer.isEmpty())return Tint.NONE;
        byte[] print=Sha3.of(writer.getBytes(StandardCharsets.UTF_8));
        return 1+(print[0]&0xff)%(Tint.count()-1);
    }

    /** How far apart a writer's letters and the paper must be: what reading guidance asks of ordinary text. */
    static final double READABLE=4.5;

    /**
     * A colour as the letters are drawn on a paper: the colour itself, darkened on a light paper or lightened on a
     * dark one until it reads as well as ordinary text does. Made from the paper under the letters, whatever colour
     * it has been washed and however strongly, so a writer's colour stays readable on every paper. 0 for no colour.
     */
    static int ink(int colour,int paper) {
        if(!Tint.known(colour))return 0;
        boolean dark=contrast(paper,0xFFFFFFFF)>contrast(paper,0xFF000000);
        int drawn=Tint.of(colour,dark), toward=dark?0xFFFFFFFF:0xFF000000;
        if(contrast(drawn,paper)>=READABLE)return drawn;
        // As little of the way to black or white as reads: the most of the colour that can be kept.
        float low=0f, high=1f;
        for(int step=0;step<16;step++) {
            float mid=(low+high)/2f;
            if(contrast(blend(drawn,toward,mid),paper)>=READABLE)high=mid;else low=mid;
        }
        return blend(drawn,toward,high);
    }

    /** How far apart two colours read, 1 to 21, as reading guidance measures it. */
    static double contrast(int one,int two) {
        double a=luminance(one), b=luminance(two);
        return (Math.max(a,b)+0.05)/(Math.min(a,b)+0.05);
    }

    private static double luminance(int colour) {
        return 0.2126*channel((colour>>16)&0xFF)+0.7152*channel((colour>>8)&0xFF)+0.0722*channel(colour&0xFF);
    }

    private static double channel(int value) {
        double c=value/255.0;
        return c<=0.03928?c/12.92:Math.pow((c+0.055)/1.055,2.4);
    }

    private static int blend(int from,int to,float much) {
        int red=Math.round(((from>>16)&0xFF)+(((to>>16)&0xFF)-((from>>16)&0xFF))*much);
        int green=Math.round(((from>>8)&0xFF)+(((to>>8)&0xFF)-((from>>8)&0xFF))*much);
        int blue=Math.round((from&0xFF)+((to&0xFF)-(from&0xFF))*much);
        return 0xFF000000|(red<<16)|(green<<8)|blue;
    }
}
