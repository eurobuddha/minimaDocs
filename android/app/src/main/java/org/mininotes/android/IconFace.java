// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * A thing's face, as every screen draws it (docs/HOME.md, step 4): a round square in the thing's colour, and on it the
 * picture it wears, or its glyph - its own icon, or the note glyph for a note that has none - stroked in the colour's own
 * ink, or for a collection with neither, the mini-grid of what is inside it that its tile has always shown. The same
 * drawing at any size, so Home's grid, the dock, a card in the overview, a line of the tree and the bar over a note all
 * show one thing the same way; a smaller face keeps its proportions rather than its pixels, and its corners never round
 * it into a disc.
 *
 * <p>Everything it needs is decided when it is made, and everything that depends on its size when it is given one, so
 * drawing it - many times a second while a grid scrolls - only draws.
 */
final class IconFace extends Drawable {
    private final Looks.Shows shows;
    /** The square's fill (0 for none: a bare glyph) and its edge (0 for none), with the widest the edge is drawn. */
    private final int fill,edge;
    private final float edgeMost,cornerMost,small;
    /** The glyph and its ink, and how much of the square it takes (0: as {@link Looks#glyphSide} says). */
    private final String glyph;
    private final int ink;
    private final float glyphPart;
    private final Bitmap picture;
    /** The mini-grid: how many of its four places are filled, in what, and the least room round it. */
    private final int pips,pipFill,pipLine;
    private final float padLeast,pipGapMost,pipCornerMost,pipLineWide;

    private final Paint filled=new Paint(Paint.ANTI_ALIAS_FLAG),edged=new Paint(Paint.ANTI_ALIAS_FLAG),
        pictured=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),pipFilled=new Paint(Paint.ANTI_ALIAS_FLAG),
        pipEdged=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint inked;
    private final RectF square=new RectF();
    private final RectF[] places={new RectF(),new RectF(),new RectF(),new RectF()};
    private float corner,pipCorner,glyphX,glyphY;
    private Path path;

    private IconFace(Looks.Shows shows,int fill,int edge,float edgeMost,float cornerMost,float small,String glyph,int ink,
                     float glyphPart,Bitmap picture,int pips,int pipFill,int pipLine,float padLeast,float pipGapMost,
                     float pipCornerMost,float pipLineWide) {
        this.shows=shows;this.fill=fill;this.edge=edge;this.edgeMost=edgeMost;this.cornerMost=cornerMost;this.small=small;
        this.glyph=glyph;this.ink=ink;this.glyphPart=glyphPart;this.picture=picture;
        this.pips=pips;this.pipFill=pipFill;this.pipLine=pipLine;this.padLeast=padLeast;this.pipGapMost=pipGapMost;
        this.pipCornerMost=pipCornerMost;this.pipLineWide=pipLineWide;
        filled.setStyle(Paint.Style.FILL);filled.setColor(fill);
        edged.setStyle(Paint.Style.STROKE);edged.setColor(edge);
        pipFilled.setStyle(Paint.Style.FILL);pipFilled.setColor(pipFill);
        pipEdged.setStyle(Paint.Style.STROKE);pipEdged.setColor(pipLine);pipEdged.setStrokeWidth(pipLineWide);
        if(picture!=null)pictured.setShader(new BitmapShader(picture,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP));
    }

    // ---- made for a thing ---------------------------------------------------------------------------------------------

    /**
     * A thing's face, in the colours of the paper the app is on now: the picture it wears; else for a note its glyph on
     * the paper it is written on; for a collection its icon on that paper, or with none the mini-grid on its washed card;
     * for a file, the file glyph on the pale square its tile has.
     */
    static IconFace of(MainActivity a,NoteStore.Branch b) {
        boolean dark=a.darkPaper(),tinted=Tint.known(b.colour);
        int edge=tinted?Tint.of(b.colour,dark):a.LINE;
        float edgeMost=tinted?a.dp(3):Math.max(1,a.dp(1));
        if(b.kind==NoteStore.Branch.Kind.FILE)
            return glyph(a,MainActivity.mix(a.ACCENT,a.CARD,0.88f),a.LINE,Math.max(1,a.dp(1)),"file",a.ACCENT);
        Bitmap picture=b.image==null?null:IconPen.picture(b.image);
        boolean known=Icons.known(b.icon);
        switch(Looks.shows(b.kind==NoteStore.Branch.Kind.PAGE,b.icon,known,picture!=null)) {
            case PICTURE:
                return new IconFace(Looks.Shows.PICTURE,a.PAPER,edge,edgeMost,a.dp(14),a.dp(40),null,0,0,picture,0,0,0,0,0,0,0);
            case GLYPH:
                return glyph(a,Tint.over(b.colour,a.PAPER,a.wash(0.14f,0.82f),dark),edge,edgeMost,Looks.glyph(b.icon,known),
                    Looks.ink(b.colour,dark,a.INK));
            default:
                return new IconFace(Looks.Shows.GRID,Tint.over(b.colour,a.CARD,a.wash(0.22f,0.92f),dark),edge,edgeMost,a.dp(14),
                    a.dp(40),null,0,0,null,Math.max(0,Math.min(4,a.countIn(b))),Tint.over(b.colour,a.PAPER,a.wash(0.10f,0.7f),dark),
                    a.LINE,a.dp(6),a.dp(2),a.dp(3),Math.max(1,a.dp(1)/2f));
        }
    }

    /** One glyph on a round square of the given fill and edge. */
    static IconFace glyph(MainActivity a,int fill,int edge,float edgeMost,String glyph,int ink) {
        return new IconFace(Looks.Shows.GLYPH,fill,edge,edgeMost,a.dp(14),a.dp(40),glyph,ink,0,null,0,0,0,0,0,0,0);
    }

    /**
     * A glyph on its own, half the size of where it sits, as the picker lists the set - ringed on a square of the paper
     * where it is the one the thing wears now.
     *
     * @param ring the ring's colour, or 0 for none
     */
    static IconFace bare(MainActivity a,String glyph,int ink,int ring) {
        return new IconFace(Looks.Shows.GLYPH,ring==0?0:a.PAPER,ring,ring==0?0:a.dp(2),a.dp(12),a.dp(40),glyph,ink,0.5f,
            null,0,0,0,0,0,0,0);
    }

    /** A thing's face as a view, {@code px} across, that says nothing of its own: whatever holds it says what it is. */
    static View view(MainActivity a,NoteStore.Branch b,int px) {
        View face=new View(a);
        face.setBackground(of(a,b));
        face.setMinimumWidth(px);face.setMinimumHeight(px);
        face.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return face;
    }

    // ---- drawn ---------------------------------------------------------------------------------------------------------

    @Override protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        float side=Math.min(bounds.width(),bounds.height());
        float left=bounds.left+(bounds.width()-side)/2f,top=bounds.top+(bounds.height()-side)/2f;
        // Inside its bounds, edge and all, as the tiles' own shapes are drawn.
        float wide=edge==0||edgeMost<=0?0:Math.max(1f,Math.min(edgeMost,side/14f));
        edged.setStrokeWidth(wide);
        square.set(left+wide/2f,top+wide/2f,left+side-wide/2f,top+side-wide/2f);
        corner=Looks.corner(side,cornerMost);
        path=null;
        if(shows==Looks.Shows.GLYPH&&glyph!=null) {
            int drawn=Math.max(1,Math.round(glyphPart>0?side*glyphPart:Looks.glyphSide(side,small)));
            path=IconPen.glyph(glyph,drawn);
            inked=IconPen.ink(ink,drawn);
            glyphX=left+(side-drawn)/2f;glyphY=top+(side-drawn)/2f;
        }
        if(shows==Looks.Shows.PICTURE&&picture!=null&&picture.getWidth()>0&&picture.getHeight()>0) {
            // Filling the square whatever shape it arrived in: the middle of it, as the square was cut from the middle.
            float scale=Math.max(side/picture.getWidth(),side/picture.getHeight());
            Matrix fit=new Matrix();
            fit.setScale(scale,scale);
            fit.postTranslate(left+(side-picture.getWidth()*scale)/2f,top+(side-picture.getHeight()*scale)/2f);
            pictured.getShader().setLocalMatrix(fit);
        }
        if(shows==Looks.Shows.GRID) {
            float pad=Math.max(Math.min(padLeast,side/5f),side/7f),gap=Math.min(pipGapMost,side/28f),cell=(side-2*pad)/2f;
            pipCorner=Math.min(pipCornerMost,side/16f);
            for(int at=0;at<4;at++) {
                float x=left+pad+(at%2)*cell,y=top+pad+(at/2)*cell;
                places[at].set(x+gap+pipLineWide/2f,y+gap+pipLineWide/2f,x+cell-gap-pipLineWide/2f,y+cell-gap-pipLineWide/2f);
            }
        }
    }

    @Override public void draw(Canvas canvas) {
        if(square.isEmpty())return;
        // The paper under a picture too, for one with clear parts - a PNG from the PC can have them.
        if(fill!=0)canvas.drawRoundRect(square,corner,corner,filled);
        if(shows==Looks.Shows.PICTURE&&picture!=null)canvas.drawRoundRect(square,corner,corner,pictured);
        if(shows==Looks.Shows.GRID)for(int at=0;at<pips;at++) {
            canvas.drawRoundRect(places[at],pipCorner,pipCorner,pipFilled);
            canvas.drawRoundRect(places[at],pipCorner,pipCorner,pipEdged);
        }
        if(path!=null&&inked!=null) {
            canvas.save();
            canvas.translate(glyphX,glyphY);
            canvas.drawPath(path,inked);
            canvas.restore();
        }
        if(edge!=0&&edged.getStrokeWidth()>0)canvas.drawRoundRect(square,corner,corner,edged);
    }

    @Override public void setAlpha(int alpha) {
        for(Paint one:new Paint[]{filled,edged,pictured,pipFilled,pipEdged,inked})if(one!=null)one.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter filter) {
        for(Paint one:new Paint[]{filled,edged,pictured,pipFilled,pipEdged,inked})if(one!=null)one.setColorFilter(filter);
        invalidateSelf();
    }

    @SuppressWarnings("deprecation")
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
