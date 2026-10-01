// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * The dock, at the foot of Home: up to five favourites, icons only, as a phone's dock is (docs/HOME.md, decisions 3 and
 * 19). Each says its name to a screen reader; a tap opens it, and holding it gives its menu, which offers to take it out
 * of the dock. A note or a collection carried here from anywhere on Home becomes a favourite at the place it is let go,
 * and the one pushed off the end stays a favourite, in the Favourites collection. With nothing in it, it says Favourites,
 * quietly, so the empty strip is not a mystery.
 *
 * <p>Above it a short rounded handle, the shape of the phone's own gesture bar, says that it can be swiped up: a swipe
 * that starts on the handle or the dock - or a tap on the handle - opens the overview of what is open.
 */
final class Dock {
    private final MainActivity a;
    private final HomeScreen home;
    /** The icons, left to right, each tagged with the favourite it is. */
    LinearLayout row;

    Dock(MainActivity a,HomeScreen home){this.a=a;this.home=home;}

    /**
     * The handle and the dock under it, as one strip a swipe up can start anywhere on. The icons keep their own taps and
     * holds: the strip takes the finger only once it has gone up, further than it has gone across.
     */
    // The strip consumes only a swipe, never a tap: each icon's own click and long click stay, and so does the handle's,
    // which is how the overview is reached without a swipe.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    View build() {
        LinearLayout strip=new LinearLayout(a){
            private float downX,downY;
            private boolean swiped;
            @Override public boolean onInterceptTouchEvent(MotionEvent event){return watch(event);}
            @Override public boolean onTouchEvent(MotionEvent event){watch(event);return true;}
            private boolean watch(MotionEvent event) {
                switch(event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: downX=event.getX();downY=event.getY();swiped=false;return false;
                    case MotionEvent.ACTION_MOVE:
                        if(swiped)return true;
                        // Not while something is held for its menu or carried: that finger belongs to the thing.
                        if(a.heldTile!=null||a.dragging!=null)return false;
                        if(Grid.swipedUp(event.getX()-downX,event.getY()-downY,a.dp(36))){swiped=true;home.overview.open();return true;}
                        return false;
                    default: return swiped;
                }
            }
        };
        strip.setOrientation(LinearLayout.VERTICAL);
        FrameLayout handle=new FrameLayout(a);
        View pill=new View(a);
        GradientDrawable shape=new GradientDrawable();shape.setColor(MainActivity.mix(a.MUTED,a.PAPER,0.35f));shape.setCornerRadius(a.dp(2));
        pill.setBackground(shape);
        handle.addView(pill,new FrameLayout.LayoutParams(a.dp(36),a.dp(4),Gravity.CENTER));
        handle.setBackgroundResource(a.borderlessFeedback());
        handle.setContentDescription("Show what is open. Swipe up from the dock to do the same.");
        handle.setOnClickListener(v->home.overview.open());
        strip.addView(handle,new LinearLayout.LayoutParams(-1,a.dp(22)));
        row=new LinearLayout(a);row.setGravity(Gravity.CENTER);
        row.setPadding(a.dp(12),a.dp(2),a.dp(12),a.dp(8));
        row.setMinimumHeight(a.dp(68));
        LinearLayout.LayoutParams place=new LinearLayout.LayoutParams(-1,-2);
        place.setMargins(a.dp(10),0,a.dp(10),a.dp(4));
        strip.addView(row,place);
        return strip;
    }

    /** The favourites shown or not (decision 47); the handle above them stays, as the way to what is open. */
    void showRow(int shown){if(row!=null)row.setVisibility(shown);}

    /** The dock as the notebook has it, in its order: the favourites with a place in it. */
    void fill(List<NoteStore.Branch> docked) {
        if(row==null)return;
        row.removeAllViews();
        if(docked.isEmpty()) {
            TextView empty=a.label("Favourites",MainActivity.QUIET,a.MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setContentDescription("The dock. Favourites go here: carry a note or a collection here, or make it a favourite from its menu.");
            row.addView(empty,new LinearLayout.LayoutParams(-1,a.dp(56)));
            return;
        }
        int across=Math.max(a.dp(52),(a.getResources().getDisplayMetrics().widthPixels-a.dp(44))/Things.DOCK_PHONE);
        int face=Math.min(a.dp(52),across-a.dp(16));
        for(final NoteStore.Branch one:docked) {
            FrameLayout cell=new FrameLayout(a);
            View picture=IconFace.view(a,one,face);
            cell.addView(picture,new FrameLayout.LayoutParams(face,face,Gravity.CENTER));
            cell.setBackgroundResource(a.borderlessFeedback());
            // No word under it, as a phone's dock has none: the name is said to whoever is listening instead.
            cell.setContentDescription(one.name);
            cell.setOnClickListener(v->opened(one));
            a.grabbable(cell,one);
            row.addView(cell,new LinearLayout.LayoutParams(across,a.dp(60)));
        }
    }

    /** A favourite tapped in the dock: a note opens; a collection opens its pop-up where it really is. */
    private void opened(NoteStore.Branch one) {
        if(one.kind==NoteStore.Branch.Kind.PAGE)home.openNote(one.id);
        else home.openCollection(one.id);
    }

    /** Lit while a thing that can be a favourite is carried over it: the place to let go. */
    void lit(boolean on) {
        if(row==null)return;
        if(!on){row.setBackground(null);return;}
        GradientDrawable ring=new GradientDrawable();
        ring.setColor(MainActivity.mix(a.ACCENT,a.PAPER,0.9f));ring.setCornerRadius(a.dp(20));ring.setStroke(a.dp(2),a.ACCENT);
        row.setBackground(ring);
    }

    /**
     * The place in the dock, 1 the first, that a thing let go at {@code x} takes, counted as the notebook counts it: the
     * middle of each icon, measured where the drag is, and the carried one passed over if it came from the dock.
     */
    int slotAt(float x,View carried) {
        if(row==null)return 1;
        List<Float> middles=new ArrayList<>();int dragged=-1;
        for(int at=0;at<row.getChildCount();at++) {
            View cell=row.getChildAt(at);
            if(!(cell.getTag() instanceof NoteStore.Branch))continue;
            if(cell==carried)dragged=middles.size();
            Rect where=new Rect(0,0,cell.getWidth(),cell.getHeight());
            try{a.root.offsetDescendantRectToMyCoords(cell,where);}catch(IllegalArgumentException gone){continue;}
            middles.add(where.exactCenterX());
        }
        float[] all=new float[middles.size()];
        for(int at=0;at<all.length;at++)all[at]=middles.get(at);
        return Grid.dockSlot(x,all,dragged);
    }
}
