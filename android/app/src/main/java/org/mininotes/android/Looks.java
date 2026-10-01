// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * What the phone decides about a thing's look without a screen (docs/HOME.md, step 4): which face it wears, in what ink,
 * how its glyph sits in the round square, how small a picture is read before it is cut, which encoding of it is kept,
 * what the picker lists for the words typed, and when the box an amber mark opens has nothing to send.
 *
 * <p>Holds no Android types, so all of it is unit tested; {@link IconFace}, {@link IconPen} and {@link IconPicker} draw
 * and ask with what it says.
 */
final class Looks {
    private Looks(){}

    /** What a face shows: the picture it wears, a glyph from the set, or - a collection with neither - its mini-grid. */
    enum Shows { PICTURE, GLYPH, GRID }

    /**
     * What a thing's face shows. A picture wins over everything; a note otherwise always shows a glyph - its own icon, or
     * the note glyph; a collection its own icon, or with none the mini-grid of what is inside it. An icon this build's set
     * does not have - from a later set - counts as none.
     *
     * @param known whether the set has the icon it names
     */
    static Shows shows(boolean note,String icon,boolean known,boolean picture) {
        if(picture)return Shows.PICTURE;
        if(icon!=null&&!icon.isEmpty()&&known)return Shows.GLYPH;
        return note?Shows.GLYPH:Shows.GRID;
    }

    /** The glyph a face draws where it draws one: the thing's own icon, where the set has it, else the note glyph. */
    static String glyph(String icon,boolean known) {
        return icon!=null&&!icon.isEmpty()&&known?icon:Icons.NOTE;
    }

    /** The ink a glyph is drawn in: the thing's colour itself, as its edge is, or the paper's own ink with none. */
    static int ink(int colour,boolean darkPaper,int paperInk) {
        return Tint.known(colour)?Tint.of(colour,darkPaper):paperInk;
    }

    /** How round a face's corners are: the tiles' own, but never so round that a small face turns into a disc. */
    static float corner(float side,float most) {
        return Math.max(0f,Math.min(most,side*0.22f));
    }

    /** How much of a face its glyph takes: a little more on a small face, so a line's icon still reads at a glance. */
    static float glyphSide(float side,float small) {
        return side*(side<small?0.66f:0.56f);
    }

    /**
     * How much a picture is shrunk as it is read - a power of two, as the platform's reader takes it - so that its square
     * is read no smaller than {@code side} and no bigger than it needs to be: a phone's photo is read at a sixteenth.
     */
    static int sample(int width,int height,int side) {
        int square=Math.min(width,height);
        if(square<=0||side<=0)return 1;
        int shrink=1;
        while(square/(shrink*2)>=side)shrink*=2;
        return shrink;
    }

    /**
     * The first encoding of a picture that may be kept and sent (see {@link Thumb#fits}), trying the ways in order; null
     * where none fits. An encoding that fails is passed over for the next.
     */
    static byte[] firstFit(List<Thumb.Try> tries,Function<Thumb.Try,byte[]> encode) {
        if(tries==null)return null;
        for(Thumb.Try one:tries) {
            byte[] made;
            try{made=encode.apply(one);}catch(RuntimeException failed){continue;}
            if(Thumb.fits(made))return made;
        }
        return null;
    }

    /** The most icons a search lists: enough to scroll through, few enough to find the one meant. */
    static final int FOUND_MOST=300;

    /** The icons the picker lists for what is typed: the whole set in Lucide's order for nothing, else what the words find. */
    static List<String> found(String words) {
        String said=words==null?"":words.trim();
        if(said.isEmpty())return new ArrayList<>(Icons.all().keySet());
        return Icons.search(said,FOUND_MOST);
    }

    /** Default's place among what the picker shows: the thing's own look - the note glyph, or the mini-grid - not an icon of the set. */
    static final String DEFAULT="";

    /**
     * What the picker's grid shows for what is typed: Default first while nothing is, then the set (see {@link #found}),
     * as the PC's picker shows it. Typed words look for an icon, and Default is not one.
     */
    static List<String> shown(String words) {
        List<String> out=new ArrayList<>();
        if(words==null||words.trim().isEmpty())out.add(DEFAULT);
        out.addAll(found(words));
        return out;
    }

    /** An icon's name as it is said to a screen reader: its words, not its dashes. */
    static String spoken(String icon) {
        return icon==null?"":icon.replace('-',' ').trim();
    }

    /**
     * Whether everything the box an amber mark opens lists waits for a device to be updated - every line is
     * "Name needs to update Mininotes to receive this" - so that sending now would change nothing, and the box has no
     * button to press: it is said, and closed. Anything else among the lines, and there is something to send.
     */
    static boolean onlyUpdates(String said) {
        if(said==null||said.trim().isEmpty())return false;
        String ending=Unsent.needsUpdate("X").substring(1);
        for(String line:said.split("\n")) {
            String one=line.trim();
            if(one.endsWith("."))one=one.substring(0,one.length()-1);
            if(!one.endsWith(ending)||one.length()==ending.length())return false;
        }
        return true;
    }
}
