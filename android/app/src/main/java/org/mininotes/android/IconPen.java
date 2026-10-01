// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.LruCache;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Lucide's path data replayed onto the phone's own {@link Path} (see {@link Icons}), and the two things a screen of icons
 * must not do again and again: read an icon's data into a path, and decode a picture. A grid of fifty icons is drawn
 * many times a second while it scrolls; each path is built once for each size it is shown at, and each picture decoded
 * once, and kept while there is room.
 *
 * <p>Only ever used on the interface thread, as everything drawn is; the caches are guarded all the same.
 */
final class IconPen implements Icons.Pen {
    private final Path path=new Path();

    private IconPen(){}

    @Override public void moveTo(float x,float y){path.moveTo(x,y);}
    @Override public void lineTo(float x,float y){path.lineTo(x,y);}
    @Override public void cubicTo(float x1,float y1,float x2,float y2,float x,float y){path.cubicTo(x1,y1,x2,y2,x,y);}
    @Override public void close(){path.close();}

    /** Paths as the set draws them, on its 24-unit grid, by name; and those same paths at a size, by name and size. */
    private static final Map<String,Path> ON_GRID=lru(300),SIZED=lru(600);
    /** Names the set does not have: asked once, then answered from here. */
    private static final Set<String> UNKNOWN=new HashSet<>();

    private static <V> Map<String,V> lru(final int most) {
        return new LinkedHashMap<String,V>(64,0.75f,true){
            @Override protected boolean removeEldestEntry(Map.Entry<String,V> eldest){return size()>most;}
        };
    }

    /**
     * An icon drawn to fill a square {@code side} pixels across - the 24-unit grid scaled onto it - or null for a name
     * the set does not have, which the caller draws as the thing's default. The same path object comes back each time:
     * it is drawn, never changed.
     */
    static synchronized Path glyph(String name,int side) {
        if(name==null||name.isEmpty()||side<=0||UNKNOWN.contains(name))return null;
        String key=name+"@"+side;
        Path sized=SIZED.get(key);
        if(sized!=null)return sized;
        Path grid=ON_GRID.get(name);
        if(grid==null) {
            IconPen pen=new IconPen();
            if(!Icons.draw(name,pen)){UNKNOWN.add(name);return null;}
            grid=pen.path;ON_GRID.put(name,grid);
        }
        Matrix scale=new Matrix();
        scale.setScale(side/Icons.GRID,side/Icons.GRID);
        sized=new Path();
        grid.transform(scale,sized);
        SIZED.put(key,sized);
        return sized;
    }

    /** The pen an icon {@code side} pixels across is stroked with, as Lucide strokes it: two units wide, round, never filled. */
    static Paint ink(int colour,int side) {
        Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeJoin(Paint.Join.ROUND);
        ink.setStrokeWidth(Icons.STROKE*side/Icons.GRID);
        ink.setColor(colour);
        return ink;
    }

    /** Pictures decoded, by what they are rather than whose they are: two things wearing the same picture share it. */
    private static final LruCache<String,Bitmap> PICTURES=new LruCache<String,Bitmap>(6*1024*1024){
        @Override protected int sizeOf(String key,Bitmap picture){return picture.getByteCount();}
    };
    /** Bytes that did not decode, so a face that cannot show its picture is not asked to try on every draw. */
    private static final Set<String> UNREADABLE=new HashSet<>();

    /** A picture a thing wears, decoded once; null for none, or for bytes the phone cannot read as a picture. */
    static Bitmap picture(byte[] bytes) {
        if(bytes==null||bytes.length==0)return null;
        String key=key(bytes);
        synchronized(PICTURES) {
            Bitmap known=PICTURES.get(key);
            if(known!=null)return known;
            if(UNREADABLE.contains(key))return null;
        }
        Bitmap read=null;
        try{read=BitmapFactory.decodeByteArray(bytes,0,bytes.length);}catch(RuntimeException damaged){/* drawn as the default */}
        synchronized(PICTURES) {
            if(read==null)UNREADABLE.add(key);else PICTURES.put(key,read);
        }
        return read;
    }

    /** What a picture is known by in the cache: a digest of its bytes, so two different pictures are never confused. */
    private static String key(byte[] bytes) {
        try {
            byte[] sum=java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex=new StringBuilder(sum.length*2);
            for(byte b:sum)hex.append(Character.forDigit((b>>4)&15,16)).append(Character.forDigit(b&15,16));
            return hex.toString();
        } catch(java.security.NoSuchAlgorithmException never){
            return bytes.length+":"+java.util.Arrays.hashCode(bytes);
        }
    }
}
