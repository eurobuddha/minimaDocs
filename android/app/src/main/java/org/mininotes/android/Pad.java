// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.OverScroller;

/** The page: an editor ruled like paper, so a blank note reads as something to write on. */
// Built only in code, like every view in this app; there is no layout XML for a tool to inflate it from.
@android.annotation.SuppressLint("ViewConstructor")
final class Pad extends EditText {
    private final Paint rule=new Paint();
    private final Rect line=new Rect();
    private final int drop;
    // A swipe keeps the page moving after the finger lifts, as every other list on the phone does. An editor
    // scrolls only while it is dragged, so a long note went by one push at a time.
    private final OverScroller coasting;
    private final GestureDetector swipes;

    Pad(Context c,int colour) {
        super(c);
        rule.setColor(colour);rule.setStrokeWidth(1f);
        drop=(int)(getResources().getDisplayMetrics().density*4);
        setBackground(null);
        coasting=new OverScroller(c);
        // Before anybody else's watcher: whoever reads the page after a keystroke reads runs that already fit it.
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){replaced=count>0&&after>0&&!putting?s.subSequence(start,start+count).toString():null;}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){typed(s,start,before,count);}
            @Override public void afterTextChanged(Editable e){}
        });
        swipes=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent from,MotionEvent to,float across,float down) {
                int most=furthest();
                if(most<=0)return false;
                coasting.fling(0,getScrollY(),0,Math.round(-down),0,0,0,most);
                postInvalidateOnAnimation();
                return true;
            }
        });
    }

    /** How far down the page can go: the writing's height, less what the screen already shows. */
    private int furthest() {
        if(getLayout()==null)return 0;
        return Math.max(0,getLayout().getHeight()+getTotalPaddingTop()+getTotalPaddingBottom()-getHeight());
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        // A finger on the page stops it where it is, the way a hand stops a sliding sheet.
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&!coasting.isFinished())coasting.forceFinished(true);
        swipes.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override public void computeScroll() {
        if(coasting.computeScrollOffset()) {
            scrollTo(getScrollX(),Math.min(coasting.getCurrY(),furthest()));
            postInvalidateOnAnimation();
            return;
        }
        super.computeScroll();
    }

    // A tap that opened an address is a click, and is reported as one so a screen reader sees it happen.
    @Override public boolean performClick(){return super.performClick();}

    // ---- who wrote what (see Writers) -----------------------------------------------------------------------------

    /** A writer's colour on their letters. Its own kind, so it is never taken for any other span on the page. */
    private static final class Ink extends ForegroundColorSpan {Ink(int colour){super(colour);}}

    /** Who wrote each letter on the page: always as long as the page's text. */
    private Writers runs=Writers.none();
    private Writers.Palette palette=Writers.Palette.plain();
    /** Whether writers are drawn in their colours at all: "Show who wrote what". */
    private boolean wanted=true;
    /** Whether they are drawn now: wanted, and two or more people wrote on this page. */
    private boolean inked;
    /** The paper under the letters, which the colours are made readable against. */
    private int paper=0xFFFFFFFF;
    /** Text being put on the page with its writers, rather than typed. */
    private boolean putting;
    /** The letters an edit is about to replace, to tell what in it is really new. */
    private String replaced;

    /** A keystroke, a paste, a cut, anything typed: the new letters are yours, and the rest keep their writers. */
    private void typed(CharSequence now,int start,int before,int count) {
        if(putting)return;
        // A keyboard writes a word again whole as each letter of it is typed, and takes up a word already there when the
        // cursor lands in it: only what is really new in it is yours, and the letters it kept keep their writer.
        String was=replaced;replaced=null;
        if(was!=null&&was.length()==before) {
            int same=0,end=0;
            while(same<before&&same<count&&was.charAt(same)==now.charAt(start+same))same++;
            while(end<before-same&&end<count-same&&was.charAt(before-1-end)==now.charAt(start+count-1-end))end++;
            start+=same;before-=same+end;count-=same+end;
        }
        runs.edit(start,before,count,Writers.ME);
        // Never drawn from runs that do not fit the words: nobody's, rather than a colour on the wrong letters.
        if(runs.length()!=now.length()){runs=Writers.unknown(now.length());ink();return;}
        if(inked!=(wanted&&palette.shows(runs))){ink();return;}
        if(inked)ink(start,start+count);
    }

    /** Words put on the page as they are, with who wrote them - opening a note, or one that changed underneath it. */
    void put(CharSequence text,Writers who) {
        putting=true;
        try{setText(text);}finally{putting=false;}
        String on=getText().toString();
        String given=text==null?"":text.toString();
        runs=who!=null&&who.length()==on.length()&&given.equals(on)?who.copy():Writers.follow(on,Writers.UNKNOWN,given,who);
        ink();
    }

    /**
     * The words on the page become others - the note changed underneath it - and the reader stays where they were.
     *
     * <p>The first line they could see, the cursor and the selection are each taken to the same words in what the page
     * says now (see {@link Writers#moved}), so lines arriving above push the page down with the words they were
     * reading. Putting words on a text field brings its cursor into view, and a cursor left at the end of a note took
     * the page to the bottom every time anything arrived: so once the words are laid out, the page is put back at the
     * line it was on, a swipe still carrying it is stopped, and nothing is scrolled to the cursor.
     */
    void replace(String said,Writers who) {
        String was=getText().toString();
        android.text.Layout out=getLayout();
        int top=0,into=0;
        if(out!=null) {
            int y=getScrollY(),line=out.getLineForVertical(Math.max(0,y-getTotalPaddingTop()));
            top=out.getLineStart(line);into=y-(out.getLineTop(line)+getTotalPaddingTop());
        }
        int start=Math.max(0,getSelectionStart()),end=Math.max(0,getSelectionEnd());
        int[] at=Writers.moved(was,said,top,start,end);
        if(!coasting.isFinished())coasting.forceFinished(true);
        put(said,who);
        int length=getText().length();
        setSelection(Math.min(at[1],length),Math.min(at[2],length));
        hold(at[0],into);
    }

    /** The page put back at a place once it has been laid out, after whatever the text field did on its own. */
    private void hold(final int offset,final int into) {
        final android.view.ViewTreeObserver seen=getViewTreeObserver();
        seen.addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                android.view.ViewTreeObserver now=getViewTreeObserver();
                if(now.isAlive())now.removeOnPreDrawListener(this);
                android.text.Layout out=getLayout();
                if(out==null)return true;
                int line=out.getLineForOffset(Math.max(0,Math.min(offset,getText().length())));
                scrollTo(getScrollX(),Math.max(0,Math.min(out.getLineTop(line)+getTotalPaddingTop()+into,furthest())));
                return true;
            }
        });
    }

    /**
     * Who wrote the words a note said, read once the page was already showing them: each letter still on the page
     * takes its writer, and anything typed since keeps being yours.
     */
    void follow(String said,Writers who) {
        if(who==null)return;
        String on=getText().toString();
        runs=on.equals(said)&&who.length()==on.length()?who.copy():Writers.follow(on,Writers.ME,said,who,on,runs);
        ink();
    }

    /** Who wrote what on the page now, to be written down with its words. */
    Writers writers(){return runs.copy();}

    /** The colours writers are drawn in, whether they are, and the paper they must read on. */
    void inks(Writers.Palette palette,boolean wanted,int paper) {
        this.palette=palette==null?Writers.Palette.plain():palette;this.wanted=wanted;this.paper=paper;
        ink();
    }

    /** Whether this page is drawn in its writers' colours now. */
    boolean inked(){return inked;}

    /** Every letter drawn again in its writer's colour, or all of them in the ordinary ink. */
    private void ink() {
        Editable text=getText();
        for(Ink was:text.getSpans(0,text.length(),Ink.class))text.removeSpan(was);
        inked=wanted&&palette.shows(runs);
        if(inked)paint(text,0,text.length());
    }

    /** The letters around an edit drawn again: an edit inside a colour would otherwise take the colour it landed in. */
    private void ink(int from,int to) {
        Editable text=getText();
        int start=Math.max(0,from-1), end=Math.min(text.length(),to+1);
        for(Ink was:text.getSpans(start,end,Ink.class)) {
            start=Math.min(start,text.getSpanStart(was));end=Math.max(end,text.getSpanEnd(was));
            text.removeSpan(was);
        }
        paint(text,start,end);
    }

    private void paint(Editable text,int from,int to) {
        int at=0;
        for(int run=0;run<runs.count()&&at<to;run++) {
            int end=at+runs.lengthOf(run);
            if(end>from) {
                int colour=palette.colourOf(runs.writerOf(run));
                if(Tint.known(colour))
                    text.setSpan(new Ink(Writers.ink(colour,paper)),Math.max(at,from),Math.min(end,to),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            at=end;
        }
    }

    /**
     * Copying and cutting take the words and nothing else. The page's colours are its own: pasted into a message, they
     * would be somebody's writing colour in a place that knows nothing of writers. Pasting in is the same: the words.
     */
    @Override public boolean onTextContextMenuItem(int id) {
        if(id==android.R.id.paste)return super.onTextContextMenuItem(android.R.id.pasteAsPlainText);
        if(id==android.R.id.copy||id==android.R.id.cut) {
            int from=Math.max(0,Math.min(getSelectionStart(),getSelectionEnd())), to=Math.max(getSelectionStart(),getSelectionEnd());
            ClipboardManager clip=(ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if(to>from&&clip!=null) {
                clip.setPrimaryClip(ClipData.newPlainText(null,getText().subSequence(from,to).toString()));
                if(id==android.R.id.cut)getText().delete(from,to);
                setSelection(id==android.R.id.cut?from:to);
                return true;
            }
        }
        return super.onTextContextMenuItem(id);
    }

    /** The rules follow the paper: a darker page needs a rule the writing can still be read against. */
    void rules(int colour){rule.setColor(colour);}

    @Override protected void onDraw(Canvas canvas) {
        int height=getLineHeight();
        if(height>0) {
            // Ruled to the bottom of the page like a paper pad, not only under the lines already written.
            int bottom=getScrollY()+getHeight();
            for(int y=getLineBounds(0,line)+drop;y<bottom;y+=height)canvas.drawLine(0,y,getWidth(),y,rule);
        }
        super.onDraw(canvas);
    }
}
