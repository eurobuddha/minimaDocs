// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Whether what you are looking at has got to where it is going, drawn rather than spelled.
 *
 * <p>It was a full stop, a tick and an arrow borrowed from the font, which meant three marks in three
 * different weights that changed shape with whatever typeface the phone happened to use. This draws them:
 * one ring, the same size and the same stroke every time, with a different thing inside it.
 *
 * <p>Which of the six a thing wears is {@link SyncMark}'s to say, shared with the PC: an empty ring (only here),
 * an arrow going up with the ring filled behind it (waiting to go), three dots (sent, not confirmed yet), a tick
 * (everybody has it), two bars (paused here), and an exclamation mark with the ring filled (gone wrong). The two
 * that ask for something are the two filled in.
 *
 * <p>Drawn from the size it is given, so it follows the reading ladder like everything else.
 */
final class Mark extends Drawable {
    private final SyncMark what;
    private final int ink;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);

    Mark(SyncMark what,int ink){this.what=what;this.ink=ink;}

    @Override public void draw(Canvas canvas) {
        android.graphics.Rect where=getBounds();
        float across=Math.min(where.width(),where.height());
        if(across<=0)return;
        float cx=where.exactCenterX(), cy=where.exactCenterY();
        float r=across*0.42f;
        // One stroke for the whole mark: a ring and a tick of different weights read as two drawings.
        float stroke=Math.max(1.5f,across*0.09f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(ink);

        if(what.filled()) {
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx,cy,r,paint);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            canvas.drawCircle(cx,cy,r-stroke/2f,paint);
        }

        if(what==SyncMark.HERE)return;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        Path path=new Path();
        if(what==SyncMark.SENT) {
            // Three dots, the sign for something still happening, each a round the width of the stroke.
            paint.setStyle(Paint.Style.FILL);
            float apart=r*0.42f;
            for(int i=-1;i<=1;i++)canvas.drawCircle(cx+i*apart,cy,stroke*0.75f,paint);
            return;
        } else if(what==SyncMark.STUCK) {
            // An exclamation mark, cut out of the filled ring as the arrow is.
            paint.setColor(PAPER_HOLE);
            float h=r*0.86f;
            path.moveTo(cx,cy-h*0.62f);path.lineTo(cx,cy+h*0.12f);
            canvas.drawPath(path,paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx,cy+h*0.56f,stroke*0.7f,paint);
            return;
        } else if(what==SyncMark.PAUSED) {
            // Two bars, the sign every player uses for the same thing.
            float h=r*0.42f, apart=r*0.26f;
            path.moveTo(cx-apart,cy-h);path.lineTo(cx-apart,cy+h);
            path.moveTo(cx+apart,cy-h);path.lineTo(cx+apart,cy+h);
        } else if(what==SyncMark.GONE) {
            // A tick, sized off the ring so it never touches it.
            float w=r*0.92f;
            path.moveTo(cx-w*0.55f,cy+w*0.04f);
            path.lineTo(cx-w*0.14f,cy+w*0.44f);
            path.lineTo(cx+w*0.58f,cy-w*0.42f);
        } else {
            // An arrow going up, drawn in the paper so it reads out of the filled ring.
            paint.setColor(PAPER_HOLE);
            float h=r*0.86f;
            path.moveTo(cx,cy+h*0.62f);
            path.lineTo(cx,cy-h*0.62f);
            path.moveTo(cx-h*0.46f,cy-h*0.16f);
            path.lineTo(cx,cy-h*0.64f);
            path.lineTo(cx+h*0.46f,cy-h*0.16f);
        }
        canvas.drawPath(path,paint);
    }

    /**
     * What the arrow is cut out in. Set once by the app when the paper changes, because a mark drawn in
     * a colour that is not the paper behind it is a mark with a smudge in the middle.
     */
    static int PAPER_HOLE=0xFFFFFFFF;

    /**
     * A size of its own, because a drawable that has none is given none: an ImageView asks a drawable how
     * big it is and draws nothing at all when the answer is "I do not know".
     */
    @Override public int getIntrinsicWidth(){return side;}
    @Override public int getIntrinsicHeight(){return side;}
    private int side=48;
    void sized(int px){side=Math.max(8,px);}

    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
