package org.mininotes.android;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.style.ReplacementSpan;

/**
 * The drop box's mark: an arrow down into an open tray, one thin stroke in the colour of the words beside it, as
 * the PC draws it (Desktop.DROP_ICON). The colour emoji it replaced stood out on every paper; the drop box is a
 * service at the side, and its mark is as quiet as its name.
 */
final class TrayMark extends Drawable {
    private final Paint ink=pen();
    private final Path path=new Path();

    TrayMark(int colour){ink.setColor(colour);}

    private static Paint pen() {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);return p;
    }

    /** The tray on a grid of 16, as the PC's: the open box, then the arrow into it. */
    private static void trace(Path path,float x,float y,float u) {
        path.reset();
        path.moveTo(x+u,y+9*u);path.lineTo(x+u,y+14*u);path.lineTo(x+15*u,y+14*u);path.lineTo(x+15*u,y+9*u);
        path.moveTo(x+8*u,y+u);path.lineTo(x+8*u,y+10*u);
        path.moveTo(x+4*u,y+6*u);path.lineTo(x+8*u,y+10*u);path.lineTo(x+12*u,y+6*u);
    }

    @Override public void draw(Canvas canvas) {
        Rect b=getBounds();float u=Math.min(b.width(),b.height())/16f;
        trace(path,b.left+(b.width()-16*u)/2,b.top+(b.height()-16*u)/2,u);
        ink.setStrokeWidth(1.6f*u);canvas.drawPath(path,ink);
    }
    @Override public void setAlpha(int alpha){ink.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){ink.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}

    /** The same, among words: as tall as their capitals, standing on their line, grown and shrunk with them. */
    static final class Span extends ReplacementSpan {
        private final int colour;private final Paint ink=pen();private final Path path=new Path();
        /** @param colour the mark's, or 0 for the words' own */
        Span(int colour){this.colour=colour;}
        @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt fm) {
            if(fm!=null)paint.getFontMetricsInt(fm);
            return Math.round(paint.getTextSize()*0.9f);
        }
        @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int y,int bottom,Paint paint) {
            float side=paint.getTextSize()*0.8f,u=side/16f;
            trace(path,x+(paint.getTextSize()*0.9f-side)/2,y-side+u,u);
            ink.setColor(colour!=0?colour:paint.getColor());ink.setStrokeWidth(Math.max(1f,1.6f*u));
            canvas.drawPath(path,ink);
        }
    }

    /** The mark, then {@code name}: a line of its own, as the PC's tree begins the drop box's line. */
    static CharSequence before(String name,int colour) {
        android.text.SpannableString said=new android.text.SpannableString("\uFFFC  "+name);
        said.setSpan(new Span(colour),0,1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return said;
    }

    /** {@code name}, then the mark after it: the drop box's line, its name first so every name starts in one column. */
    static CharSequence after(String name,int colour) {
        android.text.SpannableString said=new android.text.SpannableString(name+"  \uFFFC");
        said.setSpan(new Span(colour),said.length()-1,said.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return said;
    }
}
