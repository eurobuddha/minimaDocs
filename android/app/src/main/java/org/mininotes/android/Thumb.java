// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.util.ArrayList;
import java.util.List;

/**
 * A picture a note or a collection wears in place of its icon (see docs/HOME.md, decision 4).
 *
 * <p>It travels with the thing, as its name does, so it has to be small enough to ride in every message about it:
 * cut square from the middle of what was picked, 192 pixels a side, and at most 32 KB. Each app encodes it with
 * what it has - WebP on the phone, PNG on the PC - trying the sizes and qualities here in order until one fits.
 * What arrives is checked to be a picture of a kind either app can draw before it is kept.
 *
 * <p>Holds no Android types: the square, the order of tries and the check are unit tested.
 */
final class Thumb {
    /** The side of the square, in pixels, and the most it may weigh. */
    static final int SIDE=192, MOST=32*1024;
    /** Sides and qualities to try, in order: the first that fits is kept. PNG ignores quality, and only sizes down. */
    private static final int[] SIDES={192,160,128,96,64};
    private static final int[] QUALITIES={85,70,55};

    private Thumb(){}

    /** The square cut from the middle of a picture this wide and high: {x, y, side}. Nothing for a picture with no size. */
    static int[] square(int width,int height) {
        if(width<=0||height<=0)return null;
        int side=Math.min(width,height);
        return new int[]{(width-side)/2,(height-side)/2,side};
    }

    /** One way to encode it: a side in pixels and a quality out of 100. */
    static final class Try {
        final int side,quality;
        Try(int side,int quality){this.side=side;this.quality=quality;}
    }

    /** The ways to try, in order - the full side at falling quality, then smaller sides - no larger than the picture. */
    static List<Try> tries(int square) {
        int top=Math.max(1,Math.min(SIDE,square));
        java.util.LinkedHashSet<Integer> sides=new java.util.LinkedHashSet<>();
        sides.add(top);
        for(int side:SIDES)if(side<top)sides.add(side);
        List<Try> all=new ArrayList<>();
        for(int side:sides)for(int quality:QUALITIES)all.add(new Try(side,quality));
        return all;
    }

    /** Whether encoded bytes may be kept and sent: something, and no more than {@link #MOST}. */
    static boolean fits(byte[] bytes){return bytes!=null&&bytes.length>0&&bytes.length<=MOST;}

    /** What kind of picture these bytes are, by their first bytes - "webp", "png" or "jpeg" - or null for none of those. */
    static String kind(byte[] b) {
        if(b==null||b.length<12)return null;
        if(b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'&&b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P')return "webp";
        if((b[0]&0xff)==0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G')return "png";
        if((b[0]&0xff)==0xFF&&(b[1]&0xff)==0xD8&&(b[2]&0xff)==0xFF)return "jpeg";
        return null;
    }

    /** Whether a picture that arrived may be kept: it fits, and it is a kind of picture, not anything else. */
    static boolean takes(byte[] bytes){return fits(bytes)&&kind(bytes)!=null;}
}
