// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Home: the app's first screen, laid out as a phone's own desktop is (docs/HOME.md, step 2). The bar as it always was;
 * under it the grid of what is on Home - its collections and notes in the owner's order, then the files that came from
 * other devices - four across; a round <b>+</b> at the foot on the right; the search bar; and the dock, with the handle
 * above it that is swiped up for the overview. A collection opens as a pop-up over the dimmed grid (see {@link Folder});
 * a note opens as the page it always was.
 *
 * <p>Everything is picked up the way the tiles were: held, the thing's menu comes up; moved while still held, it is
 * carried. Let go on a note it makes a collection of the two; on a collection it goes in; in an empty cell it stays there,
 * with the empty cells round it left empty, as a phone's home screen keeps its icons (decision 39, see {@link Layout});
 * out of a pop-up onto the dimmed grid it comes up a level; on the dock it is a favourite there. What each drop means is
 * decided in {@link Grid}; this class only measures where the finger is.
 *
 * <p>Drawn from MainActivity's own helpers, opened to the package for it, so a tile, a mark, a menu and a box look and
 * behave here exactly as they do everywhere else in the app.
 */
final class HomeScreen {
    private final MainActivity a;
    final Folder folder;
    final Dock dock;
    final OverviewScreen overview;

    /** The screen this last drew on: a read that comes back after the reader has gone somewhere else is dropped. */
    private LinearLayout drawnOn;
    /** Everything between the bar and the search bar: the grid, and over it the + and any pop-up. */
    FrameLayout desk;
    private ScrollView gridScroll;
    private GridLayout grid;
    /** The search bar, shown on Home or not (decision 47). */
    private View searchView;
    /** Home's page in view, across and down from the main one (decision 45); kept while the app is open. */
    int pageX,pageY;
    /** The dots saying which page is in view. */
    private View dots;
    /**
     * Home's pages, all of them, side by side as they lie (decision 45): {@code world} holds them and is moved under the
     * finger as a page is swiped and scaled as it is pinched, inside {@code pager}, which hears those gestures. The page
     * in view is {@code here}, which never moves inside {@code world} and holds the real grid; the others are drawn beside
     * it the same way, as many holders, one for each page there is.
     */
    private FrameLayout pager,world,here;
    private final List<View> others=new ArrayList<>();
    /** Each page's holder by {x, y}, the page in view's among them; and, while a page is carried, the empty places it can go. */
    private final Map<List<Integer>,View> holders=new HashMap<>();
    private final Map<List<Integer>,View> places=new HashMap<>();
    /** A whole page picked up, zoomed out: which, and its holder. */
    private int[] carriedPage;private View carriedHolder;private List<Integer> litPlace;
    /** Zoomed out, every page small in its frame, and which page is in the middle of it then. */
    private boolean zoomed;private int focusX,focusY;
    /** Zoomed out as far as every page at once (decision 51), not only the one in the middle with the next ones beside it. */
    private boolean allSeen;
    /** How small the pages are, zoomed out: a whole page, in a frame, with the ones beside it showing. */
    private static final float SMALL=0.6f;
    /** The world's scale and where it is moved to, now; and what is moving it, if anything. */
    private float scaleNow=1f,shiftX,shiftY;private android.animation.ValueAnimator moving;
    /** Things made with + on another page than the main one, by id: they stand on that page once they are read (decision 46). */
    private final Map<String,int[]> placeNext=new HashMap<>();
    /** The grid a carried icon came out of, kept from the start of the carry: a page turned under it draws its icons anew. */
    private GridLayout carriedFrom;
    /** The mark in the bar, asked again whenever Home is, since a pop-up's own mark takes over the one the app keeps. */
    private View homeMark;
    /**
     * Home's own + : hidden while a pop-up is open, whose own + is the one - two at once, one of them bright over the
     * dimmed grid behind the card, made the next step a choice between two things that look alike.
     */
    View homePlus;
    /** Something changed while Home could not be drawn again - a thing being carried, a name being typed. */
    private boolean stale;
    /** Which read is the newest: an older one that comes back late draws nothing. */
    private int reads;
    /** Where a carried thing is, as far as letting it go goes, and where the finger last was. */
    private Grid.Zone zone=Grid.Zone.NONE;
    /** Whether the dock is lit, as a place to let go. */
    private boolean dockLit;
    /** Home's grid as it was last drawn: where each icon stands, and the empty cells between (see {@link Laid}). */
    private Laid homeLaid;
    /** The empty cell shown as where a carried thing, or files from another app, will land; and which grid it is in. */
    private View landing;
    private Laid aimIn;
    private int[] aimAt;

    HomeScreen(MainActivity a) {
        this.a=a;
        folder=new Folder(a,this);dock=new Dock(a,this);overview=new OverviewScreen(a,this);
    }

    /**
     * Whether a level of the old shelves is Home now: the top, any collection (a pop-up over Home), the favourites (the
     * Favourites collection's pop-up) and the drop box, whose files are on Home; and the archive and the bin while they are
     * on Home (decision 41), as cards over it. Switched off, they are the lists they were, reached from ⋮, and so is the
     * screen of places a thing carried with Move to… can go.
     *
     * @param away whether the archive and the bin are on Home (Settings)
     */
    static boolean takes(NoteStore.Branch.Kind kind,boolean away) {
        return kind==NoteStore.Branch.Kind.LIBRARY||kind==NoteStore.Branch.Kind.COLLECTION||kind==NoteStore.Branch.Kind.BOOK
            ||kind==NoteStore.Branch.Kind.FAVOURITES||kind==NoteStore.Branch.Kind.DROPS
            ||away&&(kind==NoteStore.Branch.Kind.ARCHIVE||kind==NoteStore.Branch.Kind.BIN);
    }

    /** Whether Home is what is on the screen now. */
    boolean showing(){return drawnOn!=null&&drawnOn==a.root&&a.shelves&&a.carrying==null;}

    /** Home as a thing a menu and a mark are about: everything on this device, which is what the library has always been. */
    static NoteStore.Branch home() {
        return new NoteStore.Branch(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING,"","Home","",0,0,true);
    }

    // ---- drawing -----------------------------------------------------------------------------------------------

    /**
     * Home, on the screen MainActivity has just made (see MainActivity.browse), and over it the pop-up the trail ends in,
     * if it ends in one: that is how the way back from a note, a restart, and a thing moved somewhere all reopen it.
     */
    void draw() {
        a.tiles=null;a.rows=null;a.desktop=false;a.scroller=null;
        // The drop box is on Home now: a trail remembered by a build from before, that stood in it, stands on Home.
        for(int at=1;at<a.trail.size();at++)if(a.trail.get(at).kind==NoteStore.Branch.Kind.DROPS){while(a.trail.size()>at)a.trail.remove(a.trail.size()-1);break;}
        drawnOn=a.root;stale=false;zone=Grid.Zone.NONE;dockLit=false;homeLaid=null;landing=null;aimIn=null;aimAt=null;
        // A fresh screen has nothing carried on it: a carry that never heard its end must not hold Home still for ever.
        a.dragging=null;a.lifted=null;
        a.root.addView(bar());
        desk=new FrameLayout(a);
        // Home is pages the size of the screen, in every direction, not one grid that scrolls (decision 45): the pager hears
        // the swipes and the pinches, and moves and scales the world of pages under the finger.
        final Turner turner=new Turner();
        pager=new FrameLayout(a){
            @Override public boolean onInterceptTouchEvent(android.view.MotionEvent e){return turner.intercept(e);}
            @android.annotation.SuppressLint("ClickableViewAccessibility")
            @Override public boolean onTouchEvent(android.view.MotionEvent e){turner.touch(e);return true;}
            // The pages round the one in view are drawn once the pager has a size: before, there is nowhere to put them.
            @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);if(w>0&&h>0)post(()->buildPages());}
        };
        // Neither clips: the pages beside the one in view lie outside the world's own bounds, and a view is cut to its bounds
        // when its parent clips its children - so the pager must not either, or a swipe shows nothing coming in.
        pager.setClipChildren(false);
        world=new FrameLayout(a);world.setClipChildren(false);world.setClipToPadding(false);
        // The page in view outlined in the accent, zoomed out, so it is plain where you are among them.
        GradientDrawable seen=frame();seen.setStroke(Math.max(1,a.dp(3)),a.ACCENT);
        here=new FrameLayout(a);here.setBackground(seen);holders.clear();places.clear();carriedPage=null;carriedHolder=null;
        world.addView(here,new FrameLayout.LayoutParams(-1,-1));
        pager.addView(world,new FrameLayout.LayoutParams(-1,-1));
        zoomed=false;scaleNow=1f;shiftX=0;shiftY=0;others.clear();
        gridScroll=new ScrollView(a);
        gridScroll.setClipToPadding(false);gridScroll.setVerticalScrollBarEnabled(false);
        grid=new GridLayout(a);grid.setColumnCount(columns(a.getResources().getDisplayMetrics().widthPixels));
        // Room under the last row for the + and no more (56 and its 16 under it), so a page holds every row that fits: with
        // 96 the Graphene's pages had three rows over an empty band a row and a half tall, which looked like a fourth row
        // and turned the page when a thing was carried into it (the owner, 2026-10-02).
        grid.setPadding(a.dp(8),a.dp(6),a.dp(8),a.dp(76));
        gridScroll.addView(grid,new FrameLayout.LayoutParams(-1,-2));
        here.addView(gridScroll,new FrameLayout.LayoutParams(-1,-1));
        desk.addView(pager,new FrameLayout.LayoutParams(-1,-1));
        // Files dragged in from another app and let go on Home's grid go with what is under the finger, as the PC aims what
        // Explorer drops: a note's icon, the note; a collection's, the collection; anywhere else Home, in the empty cell.
        gridScroll.setOnDragListener((v,event)->filesOn(v,event,grid,()->homeLaid,()->Things.HOME));
        // Laid out again once the grid is measured, or its names change size, so its rows fill what is seen of it.
        gridScroll.addOnLayoutChangeListener((v,l,t,r,b,wl,wt,wr,wb)->refit(gridScroll,homeLaid,()->{if(homeLaid!=null&&showing())fill(homeLaid.lines,homeLaid.places);}));
        homePlus=plus(()->Things.HOME);
        desk.addView(homePlus,plusPlace());
        dots=new PageDots();
        FrameLayout.LayoutParams under=new FrameLayout.LayoutParams(-1,a.dp(18),Gravity.BOTTOM);under.setMargins(0,0,0,a.dp(6));
        desk.addView(dots,under);
        a.root.addView(desk,new LinearLayout.LayoutParams(-1,0,1));
        searchView=searchBar();
        a.root.addView(searchView,searchPlace());
        a.root.addView(dock.build(),new LinearLayout.LayoutParams(-1,-2));
        showFoot();
        // One listener for everything carried on Home, so a thing can go from the grid or a pop-up to the dock and back.
        a.root.setOnDragListener(this::dragged);
        // The pop-up the trail ends in, if it ends in one; either way Home is read, once.
        if(a.trail.size()>1)folder.show();else refresh();
    }

    /**
     * Favourites and the search shown or not, as Settings and Home's menu say (decision 47). The handle above the dock stays
     * either way: it is the way to what is open.
     */
    void showFoot() {
        // On the main page only (decision 47); on the others their room is kept, so every page is one size.
        boolean centre=onCentre();
        if(searchView!=null)searchView.setVisibility(!a.showSearch?View.GONE:centre?View.VISIBLE:View.INVISIBLE);
        dock.showRow(!a.showDock?View.GONE:centre?View.VISIBLE:View.INVISIBLE);
    }

    // ---- pages (docs/HOME.md, decisions 45-50) ------------------------------------------------------------------------

    boolean onCentre(){return pageX==0&&pageY==0;}

    /** How many pages there are: the main one, and each that something stands on. */
    int pageCount(){return homeLaid==null||homeLaid.spots==null?1:Layout.active(homeLaid.spots).size();}

    /**
     * Whether two fingers put down at that point of the screen are Home's: on its pages, with nothing open over them - a
     * pinch there shrinks the pages (decision 51), not the whole pad.
     */
    boolean pinchesAt(float rawX,float rawY) {
        if(!showing()||pager==null||!pager.isShown()||folder.isOpen()||a.showing!=null)return false;
        int[] at=new int[2];pager.getLocationOnScreen(at);
        return rawX>=at[0]&&rawX<at[0]+pager.getWidth()&&rawY>=at[1]&&rawY<at[1]+pager.getHeight();
    }

    /** The page that way, where there is one, sliding in under the finger's way; else a short nudge says there is none. */
    void turn(int dx,int dy) {
        int[] to=homeLaid==null||homeLaid.spots==null?null:Layout.next(Layout.active(homeLaid.spots),pageX,pageY,dx,dy);
        if(to==null){nudge(dx,dy);return;}
        go(to[0],to[1]);
    }

    /** Nothing that way: the pages give a little and come back. */
    private void nudge(int dx,int dy) {
        float back=a.dp(24);
        move(1f,-dx*back,-dy*back,90,()->move(1f,0,0,140,null));
    }

    /** Where a page lies in the world, from the page in view: a page and a gap away for each page across or down. */
    private float[] offset(int x,int y) {
        float gap=a.dp(18);
        return new float[]{(x-pageX)*(pager.getWidth()+gap),(y-pageY)*(pager.getHeight()+gap)};
    }

    /** That page slid into view, the others moving with it, and then it is the page in view. */
    void go(int x,int y) {
        if(pager==null||x==pageX&&y==pageY){if(pager!=null)move(1f,0,0,160,null);return;}
        float[] at=offset(x,y);
        move(1f,-at[0],-at[1],260,()->settle(x,y));
    }

    /**
     * A page in view - any of them, an empty one beyond the edge while something is carried there - drawn from what Home
     * last read, coming in from the side it lies on ({@code dx}, {@code dy}), or at once with neither.
     */
    void showPage(int x,int y,int dx,int dy) {
        if(x==pageX&&y==pageY)return;
        settle(x,y);
        if(pager!=null&&(dx!=0||dy!=0)) {
            float gap=a.dp(18);
            place(1f,dx*(pager.getWidth()+gap),dy*(pager.getHeight()+gap));
            move(1f,0,0,220,null);
        }
    }

    /** The page in view is that one now: its grid drawn where the page in view always stands, the others round it. */
    private void settle(int x,int y) {
        pageX=Math.max(-Layout.MIDDLE,Math.min(Layout.MIDDLE-1,x));pageY=Math.max(-Layout.MIDDLE,Math.min(Layout.MIDDLE-1,y));
        focusX=pageX;focusY=pageY;zoomed=false;allSeen=false;
        aim(null,null);a.markOnto(null);
        if(homeLaid!=null&&showing())fill(homeLaid.lines,homeLaid.places);
        place(1f,0,0);
        showFoot();
        if(dots!=null)dots.invalidate();
        if(gridScroll!=null)gridScroll.announceForAccessibility(said(pageX,pageY));
    }

    /** Every page at once, each whole in its frame: a tap goes into one (decisions 45, 51). Home's menu, All pages. */
    void zoomOut() {
        if(pager==null||homeLaid==null)return;
        if(folder.isOpen())folder.close();
        focusX=pageX;focusY=pageY;
        float[] all=allAt();
        zoomed=true;allSeen=true;
        move(all[0],all[1],all[2],240,null);
    }

    /**
     * How small, and moved how, every page is seen at once: the pages there are, with half a page's room round them for the
     * places a carried page can go, as big as fits the screen - never bigger than the one-page-and-its-neighbours view.
     */
    private float[] allAt() {
        int minX=pageX,maxX=pageX,minY=pageY,maxY=pageY;
        if(homeLaid!=null&&homeLaid.spots!=null)for(List<Integer> one:Layout.active(homeLaid.spots)){
            minX=Math.min(minX,one.get(0));maxX=Math.max(maxX,one.get(0));minY=Math.min(minY,one.get(1));maxY=Math.max(maxY,one.get(1));}
        float w=pager.getWidth(),h=pager.getHeight(),gap=a.dp(18);
        float wide=(maxX-minX+2)*w+(maxX-minX+1)*gap,high=(maxY-minY+2)*h+(maxY-minY+1)*gap;
        float scale=Math.min(SMALL,Math.min(w*0.96f/wide,h*0.96f/high));
        // The middle of them all where the middle of the screen is.
        float midX=((minX+maxX)/2f-pageX)*(w+gap),midY=((minY+maxY)/2f-pageY)*(h+gap);
        return new float[]{scale,-scale*midX,-scale*midY};
    }

    /** One page in the middle, zoomed out, the next ones beside it, as a phone's own home screen does. */
    private void zoomTo(int x,int y,long millis) {
        focusX=x;focusY=y;zoomed=true;allSeen=false;
        float[] at=offset(x,y);
        move(SMALL,-SMALL*at[0],-SMALL*at[1],millis,null);
    }

    /** Back in, onto the page in the middle - or the one tapped. */
    void zoomInto(int x,int y) {
        float[] at=offset(x,y);
        zoomed=false;allSeen=false;
        move(1f,-at[0],-at[1],240,()->{if(x!=pageX||y!=pageY)settle(x,y);else place(1f,0,0);});
    }
    boolean isZoomed(){return zoomed;}

    /** The world at a scale and moved so, at once: the frames, the dots and the + as fit that scale. */
    private void place(float scale,float x,float y) {
        if(moving!=null){moving.cancel();moving=null;}
        show(scale,x,y);
    }
    /** The same, leaving a move under way to go on: what a step of a move does, and Home drawn again mid-move. */
    private void show(float scale,float x,float y) {
        scaleNow=scale;shiftX=x;shiftY=y;
        if(world==null)return;
        world.setPivotX(pager.getWidth()/2f);world.setPivotY(pager.getHeight()/2f);
        world.setScaleX(scale);world.setScaleY(scale);world.setTranslationX(x);world.setTranslationY(y);
        // The frames come as the pages shrink, and the room round them darkens a little, as a phone's own home screen does.
        float framed=Math.max(0f,Math.min(1f,(1f-scale)/(1f-SMALL)));
        int alpha=Math.round(framed*255);
        if(here.getBackground()!=null)here.getBackground().setAlpha(alpha);
        for(View one:others)if(one.getBackground()!=null)one.getBackground().setAlpha(alpha);
        // Home's own colour still seen round the pages, only dimmed, as a phone's wallpaper is behind its pages.
        pager.setBackgroundColor(Math.round(framed*0.22f*255)<<24);
        if(dots!=null){dots.setVisibility(scale<0.999f?View.INVISIBLE:View.VISIBLE);dots.invalidate();}
        if(homePlus!=null&&!folder.isOpen())homePlus.setVisibility(scale<0.999f?View.GONE:View.VISIBLE);
    }

    /** The world moved there and scaled so, over a moment, and then what follows. */
    private void move(float scale,float x,float y,long millis,Runnable then) {
        if(moving!=null){moving.cancel();moving=null;}
        final float fromS=scaleNow,fromX=shiftX,fromY=shiftY;
        android.animation.ValueAnimator go=android.animation.ValueAnimator.ofFloat(0f,1f);
        go.setDuration(millis);go.setInterpolator(new android.view.animation.DecelerateInterpolator());
        go.addUpdateListener(v->{
            float f=(Float)v.getAnimatedValue();
            float s1=fromS+(scale-fromS)*f,x1=fromX+(x-fromX)*f,y1=fromY+(y-fromY)*f;
            show(s1,x1,y1);
        });
        go.addListener(new android.animation.AnimatorListenerAdapter(){
            boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator anim){cancelled=true;}
            @Override public void onAnimationEnd(android.animation.Animator anim){if(moving==go)moving=null;if(!cancelled&&then!=null)then.run();}
        });
        moving=go;go.start();
    }

    /** A page's frame, seen only as the pages shrink. */
    private GradientDrawable frame() {
        // A faint fill, so each page reads as a page over the dimmed room round it.
        GradientDrawable edge=new GradientDrawable();edge.setColor(0x1FFFFFFF);edge.setCornerRadius(a.dp(22));
        edge.setStroke(Math.max(1,a.dp(2)),MainActivity.mix(a.INK,a.PAPER,0.55f));edge.setAlpha(0);
        return edge;
    }

    /**
     * The pages beside the one in view, each drawn as the grid draws it - the same icons, in the same cells - where it
     * lies from it, so a swipe or a pinch shows it coming. Laid out again whenever Home is.
     */
    private void buildPages() {
        if(world==null||homeLaid==null||homeLaid.spots==null)return;
        for(View one:others)world.removeView(one);
        others.clear();holders.clear();
        holders.put(java.util.Arrays.asList(pageX,pageY),here);
        if(pager.getWidth()<=0)return;
        Map<String,NoteStore.Branch> byId=new HashMap<>();
        for(NoteStore.Branch one:homeLaid.lines)byId.put(one.id,one);
        for(NoteStore.Branch one:homeLaid.places)byId.put(one.id,one);
        int cell=homeLaid.width,high=homeLaid.height;
        for(List<Integer> page:Layout.active(homeLaid.spots)) {
            if(page.get(0)==pageX&&page.get(1)==pageY)continue;
            FrameLayout holder=new FrameLayout(a);holder.setBackground(frame());
            holder.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            for(Map.Entry<String,Layout.Spot> one:homeLaid.spots.entrySet()) {
                Layout.Spot at=one.getValue();NoteStore.Branch thing=byId.get(one.getKey());
                if(at==null||thing==null||!at.on(page.get(0),page.get(1)))continue;
                View icon=Grid.place(thing.kind)?placeIcon(thing,cell):icon(thing,cell,Where.HOME);
                icon.setMinimumHeight(high);
                FrameLayout.LayoutParams spot=new FrameLayout.LayoutParams(cell,high);
                spot.leftMargin=grid.getPaddingLeft()+at.column()*cell;spot.topMargin=grid.getPaddingTop()+at.row()*high;
                holder.addView(icon,spot);
            }
            float[] where=offset(page.get(0),page.get(1));
            holder.setTranslationX(where[0]);holder.setTranslationY(where[1]);
            world.addView(holder,new FrameLayout.LayoutParams(-1,-1));
            others.add(holder);holders.put(page,holder);
        }
        show(scaleNow,shiftX,shiftY);
    }

    // ---- a whole page carried, zoomed out (the owner's ask: "grab and move the pages") ---------------------------------

    /** The places a page can be let go in: every page there is, and the empty places round them, one further each way. */
    private java.util.Set<List<Integer>> placesFor() {
        java.util.Set<List<Integer>> active=Layout.active(homeLaid.spots),all=new java.util.LinkedHashSet<>(active);
        for(List<Integer> one:active)for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)
            if(Math.abs(dx)+Math.abs(dy)==1)all.add(java.util.Arrays.asList(one.get(0)+dx,one.get(1)+dy));
        return all;
    }

    /** Which place - a page, or an empty place a page can go - is under a point of the pager, zoomed out as it is; or null. */
    private List<Integer> placeAt(float x,float y) {
        float cx=pager.getWidth()/2f,cy=pager.getHeight()/2f,gap=a.dp(18);
        float wx=cx+(x-cx-shiftX)/scaleNow,wy=cy+(y-cy-shiftY)/scaleNow;
        int px=pageX+(int)Math.floor((wx+gap/2f)/(pager.getWidth()+gap)),py=pageY+(int)Math.floor((wy+gap/2f)/(pager.getHeight()+gap));
        List<Integer> at=java.util.Arrays.asList(px,py);
        return placesFor().contains(at)?at:null;
    }

    /** Held on a page, zoomed out: it is picked up - a little bigger, over the others - and the empty places show. */
    private void liftPage(float x,float y) {
        if(homeLaid==null||homeLaid.spots==null)return;
        int[] q=pageAt(x,y);
        if(q==null)return;
        View holder=holders.get(java.util.Arrays.asList(q[0],q[1]));
        if(holder==null)return;
        carriedPage=q;carriedHolder=holder;
        pager.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        holder.setElevation(a.dp(16));holder.bringToFront();holder.animate().scaleX(1.06f).scaleY(1.06f).alpha(0.9f).setDuration(120);
        for(List<Integer> one:placesFor()) {
            if(holders.containsKey(one))continue;
            View empty=new View(a);
            GradientDrawable dashed=new GradientDrawable();dashed.setCornerRadius(a.dp(22));
            dashed.setStroke(Math.max(1,a.dp(2)),MainActivity.mix(a.INK,a.PAPER,0.5f),a.dp(10),a.dp(8));
            empty.setBackground(dashed);
            float[] at=offset(one.get(0),one.get(1));
            empty.setTranslationX(at[0]);empty.setTranslationY(at[1]);
            world.addView(empty,0,new FrameLayout.LayoutParams(-1,-1));
            places.put(one,empty);
        }
        if(gridScroll!=null)gridScroll.announceForAccessibility("Picked up "+said(q[0],q[1]).toLowerCase(java.util.Locale.ROOT)+". Let go on another place.");
    }

    /** The carried page under the finger, and the place it would go lit. */
    private void dragPage(float dx,float dy,float x,float y) {
        if(carriedHolder==null)return;
        float[] base=offset(carriedPage[0],carriedPage[1]);
        carriedHolder.setTranslationX(base[0]+dx/scaleNow);carriedHolder.setTranslationY(base[1]+dy/scaleNow);
        List<Integer> under=placeAt(x,y);
        if(java.util.Objects.equals(under,litPlace))return;
        light(litPlace,false);litPlace=under;light(under,true);
    }
    private void light(List<Integer> place,boolean on) {
        if(place==null)return;
        View view=holders.containsKey(place)?holders.get(place):places.get(place);
        if(view==null||view==carriedHolder||!(view.getBackground() instanceof GradientDrawable))return;
        GradientDrawable edge=(GradientDrawable)view.getBackground();
        if(places.containsKey(place))edge.setStroke(Math.max(1,a.dp(on?3:2)),on?a.ACCENT:MainActivity.mix(a.INK,a.PAPER,0.5f),a.dp(10),a.dp(8));
        else if(view==here)edge.setStroke(Math.max(1,a.dp(on?4:3)),a.ACCENT);
        else edge.setStroke(Math.max(1,a.dp(on?3:2)),on?a.ACCENT:MainActivity.mix(a.INK,a.PAPER,0.55f));
    }

    /**
     * The carried page let go: on another page, the two change places; on an empty place, it moves there; anywhere else,
     * back where it was (Layout.movePage). Every icon of Home is written where it stands then, and the page it went to is
     * the one in the middle, still zoomed out.
     */
    private void dropPage(float x,float y) {
        if(carriedHolder==null)return;
        List<Integer> to=placeAt(x,y);
        int[] from=carriedPage;View holder=carriedHolder;
        carriedPage=null;carriedHolder=null;light(litPlace,false);litPlace=null;
        for(View one:places.values())world.removeView(one);
        places.clear();
        holder.setElevation(0);holder.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120);
        float[] base=offset(from[0],from[1]);
        if(to==null||to.get(0)==from[0]&&to.get(1)==from[1]){holder.animate().translationX(base[0]).translationY(base[1]).setDuration(160);return;}
        final Map<String,Layout.Spot> spots=Layout.movePage(homeLaid.spots,from[0],from[1],to.get(0),to.get(1));
        // The lines take their new places at once, so Home is drawn with them before the notebook has been written.
        for(NoteStore.Branch line:homeLaid.lines){Layout.Spot at=spots.get(line.id);if(at!=null){line.cell=at.cell();line.page=at.page();}}
        placesOn(spots);
        final List<NoteStore.Branch> lines=new ArrayList<>(homeLaid.lines);
        a.background.submit(()->{a.store.placeOnPages(lines,spots);return null;},done->{},e->{a.alert("Could not move that page. Nothing was changed.");refresh();});
        boolean swapped=homeLaid.spots.values().stream().anyMatch(at->at!=null&&at.on(to.get(0),to.get(1)));
        // The page in view's holder always stands at the middle of the world, whichever page it shows.
        here.animate().cancel();here.setTranslationX(0);here.setTranslationY(0);here.setScaleX(1f);here.setScaleY(1f);here.setAlpha(1f);
        pageX=to.get(0);pageY=to.get(1);focusX=pageX;focusY=pageY;
        fill(homeLaid.lines,homeLaid.places);
        zoomed=true;
        if(allSeen){float[] all=allAt();place(all[0],all[1],all[2]);}else place(SMALL,0,0);
        showFoot();
        a.toast(swapped?"The two pages changed places":"Page moved");
    }

    /** Which page is under a point of the pager, zoomed out as it is now, or null. */
    private int[] pageAt(float x,float y) {
        float cx=pager.getWidth()/2f,cy=pager.getHeight()/2f;
        float wx=cx+(x-cx-shiftX)/scaleNow,wy=cy+(y-cy-shiftY)/scaleNow;
        if(homeLaid==null||homeLaid.spots==null)return null;
        for(List<Integer> page:Layout.active(homeLaid.spots)) {
            float[] at=offset(page.get(0),page.get(1));
            if(wx>=at[0]&&wx<at[0]+pager.getWidth()&&wy>=at[1]&&wy<at[1]+pager.getHeight())return new int[]{page.get(0),page.get(1)};
        }
        return null;
    }

    /** The page nearest the middle, zoomed out as it is now: where a let-go swipe settles. */
    private int[] nearest() {
        int[] best={pageX,pageY};float least=Float.MAX_VALUE;
        if(homeLaid==null||homeLaid.spots==null)return best;
        for(List<Integer> page:Layout.active(homeLaid.spots)) {
            float[] at=offset(page.get(0),page.get(1));
            float dx=shiftX+scaleNow*at[0],dy=shiftY+scaleNow*at[1],d=dx*dx+dy*dy;
            if(d<least){least=d;best=new int[]{page.get(0),page.get(1)};}
        }
        return best;
    }

    /** Where a page lies, in words: "The main page", "Page one to the right", "Page two up, one to the left". */
    static String said(int x,int y) {
        if(x==0&&y==0)return "The main page";
        List<String> parts=new ArrayList<>();
        if(y!=0)parts.add(count(y)+(y<0?" up":" down"));
        if(x!=0)parts.add(count(x)+(x<0?" to the left":" to the right"));
        return "Page "+TextUtils.join(", ",parts);
    }
    private static String count(int n){int m=Math.abs(n);return m==1?"one":m==2?"two":m==3?"three":String.valueOf(m);}

    /** Something just made with + on the page in view: it stands on that page once Home has read it (decision 46). */
    void placeNew(String id){if(!onCentre())placeNext.put(id,new int[]{pageX,pageY});}

    /** Those made with + on another page than the main one, put there now they are read: the first free cell of that page. */
    private void placeMade() {
        if(placeNext.isEmpty()||homeLaid==null||homeLaid.spots==null)return;
        boolean moved=false;
        for(NoteStore.Branch line:homeLaid.lines) {
            int[] on=placeNext.remove(line.id);
            if(on==null||line.cell>=0)continue;
            Map<String,Layout.Spot> others=new java.util.LinkedHashMap<>(homeLaid.spots);others.remove(line.id);
            final Layout.Spot at=Layout.freeOn(others,on[0],on[1],homeLaid.columns,homeLaid.rows);
            line.cell=at.cell();line.page=at.page();moved=true;
            final NoteStore.Branch kept=line;
            a.background.submit(()->{a.store.placeOnPages(java.util.Collections.singletonList(kept),java.util.Collections.singletonMap(kept.id,at));return null;},done->{},e->{});
        }
        if(moved)fill(homeLaid.lines,homeLaid.places);
    }

    /** Home's icons written where they stand, each on its page, the places' in this phone's settings. On the worker. */
    private void writeHome(List<NoteStore.Branch> lines,Map<String,Layout.Spot> spots){a.store.placeOnPages(lines,spots);placesOn(spots);}

    /** For an empty cell of the page in view let go in: every icon of Home pinned where it stands, that one in its new cell. */
    private Map<String,Layout.Spot> homeMove(Laid in,String id,int row,int column) {
        return in.spots==null?null:Layout.moveTo(in.spots,id,new Layout.Spot(pageX,pageY,row,column));
    }

    /** Whether a point of the pager is over an icon of the page in view: a double tap there opens it, not the main page. */
    private boolean overIcon(float x,float y) {
        if(homeLaid==null)return false;
        for(View one:homeLaid.icons.values()) {
            if(one.getParent()==null)continue;
            float left=one.getLeft()+grid.getLeft(),top=one.getTop()+grid.getTop()-gridScroll.getScrollY();
            if(x>=left&&x<left+one.getWidth()&&y>=top&&y<top+one.getHeight())return true;
        }
        return false;
    }

    /**
     * Swipes, pinches and taps on Home's pages (decision 45), as a phone's own home screen takes them. A swipe moves the
     * pages under the finger, the next one coming in beside it, and let go it settles on whichever page it was taken to
     * - back where it was if it was not taken far. A finger held first is the icon's, picked up as it always was. Two
     * fingers pinched shrink the pages as they move, each into its frame, and let go small they stay so: then a swipe
     * moves between them, a tap goes into one, and two fingers spread go into the one in the middle. A double tap on the
     * empty room goes back to the main page.
     */
    private final class Turner {
        private float downX,downY,fromX,fromY,startScale;private boolean swiping,scaling,across;
        private final int slop=ViewConfiguration.get(a).getScaledTouchSlop();
        private android.view.VelocityTracker speed;
        /** Zoomed out, a finger held still on a page picks the page up. */
        private final Runnable holdPage=()->{if(zoomed&&!swiping&&!scaling)liftPage(downX,downY);};
        private final android.view.GestureDetector taps=new android.view.GestureDetector(a,new android.view.GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDoubleTap(android.view.MotionEvent e) {
                if(!zoomed&&!overIcon(e.getX(),e.getY())&&!onCentre())go(0,0);
                return false;
            }
        });
        /**
         * The pinch: the pages grow and shrink about the point between the fingers, which stays under them, as a map does;
         * let go, they settle on whichever is nearest - the page itself, it zoomed out with the next ones beside it, or
         * every page at once.
         */
        private final android.view.ScaleGestureDetector pinch=new android.view.ScaleGestureDetector(a,new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener(){
            float total=1f,heldX,heldY;
            @Override public boolean onScaleBegin(android.view.ScaleGestureDetector d) {
                if(moving!=null){moving.cancel();moving=null;}
                pager.removeCallbacks(holdPage);
                total=1f;scaling=true;swiping=false;startScale=scaleNow;
                // The point of the world under the fingers.
                float cx=pager.getWidth()/2f,cy=pager.getHeight()/2f;
                heldX=cx+(d.getFocusX()-cx-shiftX)/scaleNow;heldY=cy+(d.getFocusY()-cy-shiftY)/scaleNow;
                return true;
            }
            @Override public boolean onScale(android.view.ScaleGestureDetector d) {
                total*=d.getScaleFactor();
                float least=Math.min(SMALL,allAt()[0])*0.85f;
                float now=Math.max(least,Math.min(1.05f,startScale*total));
                float cx=pager.getWidth()/2f,cy=pager.getHeight()/2f;
                show(now,d.getFocusX()-cx-(heldX-cx)*now,d.getFocusY()-cy-(heldY-cy)*now);
                return true;
            }
            @Override public void onScaleEnd(android.view.ScaleGestureDetector d) {
                float all=allAt()[0];
                int[] middle=nearest();
                if(scaleNow>0.85f)zoomInto(middle[0],middle[1]);
                else if(all>=SMALL-0.01f||scaleNow>(SMALL+all)/2f)zoomTo(middle[0],middle[1],180);
                else{zoomed=true;allSeen=true;float[] to=allAt();move(to[0],to[1],to[2],180,null);}
            }
        });
        boolean intercept(android.view.MotionEvent e) {
            follow(e);taps.onTouchEvent(e);pinch.onTouchEvent(e);
            switch(e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    downX=e.getX();downY=e.getY();fromX=shiftX;fromY=shiftY;swiping=false;scaling=false;
                    // Zoomed out, the pages are pictures: every touch is the pager's.
                    return zoomed;
                // Two fingers are a pinch, not anything for the icons under them.
                case android.view.MotionEvent.ACTION_POINTER_DOWN: return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    if(zoomed||scaling||e.getPointerCount()>1)return true;
                    if(a.heldTile!=null||a.dragging!=null)return false;
                    float dx=e.getX()-downX,dy=e.getY()-downY;
                    if(Math.max(Math.abs(dx),Math.abs(dy))>slop*2){swiping=true;across=Math.abs(dx)>=Math.abs(dy);return true;}
                    return false;
                default: return false;
            }
        }
        void touch(android.view.MotionEvent e) {
            follow(e);taps.onTouchEvent(e);pinch.onTouchEvent(e);
            switch(e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    downX=e.getX();downY=e.getY();fromX=shiftX;fromY=shiftY;swiping=false;scaling=false;
                    if(zoomed)pager.postDelayed(holdPage,ViewConfiguration.getLongPressTimeout());
                    break;
                case android.view.MotionEvent.ACTION_POINTER_DOWN: pager.removeCallbacks(holdPage);break;
                case android.view.MotionEvent.ACTION_MOVE: {
                    if(carriedHolder!=null){dragPage(e.getX()-downX,e.getY()-downY,e.getX(),e.getY());break;}
                    if(scaling||e.getPointerCount()>1)break;
                    float dx=e.getX()-downX,dy=e.getY()-downY;
                    if(Math.max(Math.abs(dx),Math.abs(dy))>slop*2)pager.removeCallbacks(holdPage);
                    if(!swiping&&Math.max(Math.abs(dx),Math.abs(dy))>slop*2){swiping=true;across=Math.abs(dx)>=Math.abs(dy);}
                    if(!swiping)break;
                    if(zoomed){place(scaleNow,fromX+dx,fromY+dy);break;}
                    // One page at a time, along the way it was started; where there is no page that way, it only gives.
                    int wx=across?(dx<0?1:-1):0,wy=across?0:(dy<0?1:-1);
                    boolean there=homeLaid!=null&&homeLaid.spots!=null&&Layout.next(Layout.active(homeLaid.spots),pageX,pageY,wx,wy)!=null;
                    float give=there?1f:0.25f;
                    place(1f,across?dx*give:0,across?0:dy*give);
                    break;
                }
                case android.view.MotionEvent.ACTION_UP: {
                    pager.removeCallbacks(holdPage);
                    if(carriedHolder!=null){dropPage(e.getX(),e.getY());break;}
                    if(scaling){scaling=false;break;}
                    float dx=e.getX()-downX,dy=e.getY()-downY;
                    float vx=0,vy=0;
                    if(speed!=null){speed.computeCurrentVelocity(1000);vx=speed.getXVelocity();vy=speed.getYVelocity();}
                    if(zoomed) {
                        if(!swiping){int[] tapped=pageAt(e.getX(),e.getY());if(tapped!=null)zoomInto(tapped[0],tapped[1]);break;}
                        // Every page at once stays so; one in the middle settles on whichever is nearest the middle now.
                        if(allSeen){float[] all=allAt();move(all[0],all[1],all[2],200,null);break;}
                        int[] to=nearest();zoomTo(to[0],to[1],200);
                        break;
                    }
                    if(!swiping)break;
                    swiping=false;
                    float far=across?dx:dy,fast=across?vx:vy,size=across?pager.getWidth():pager.getHeight();
                    boolean taken=Math.abs(far)>size*0.22f||Math.abs(fast)>a.dp(600)&&Math.signum(fast)==Math.signum(far);
                    int wx=across?(far<0?1:-1):0,wy=across?0:(far<0?1:-1);
                    int[] to=taken&&homeLaid!=null&&homeLaid.spots!=null?Layout.next(Layout.active(homeLaid.spots),pageX,pageY,wx,wy):null;
                    if(to!=null)go(to[0],to[1]);else move(1f,0,0,180,null);
                    break;
                }
                case android.view.MotionEvent.ACTION_CANCEL:
                    pager.removeCallbacks(holdPage);
                    if(carriedHolder!=null){dropPage(-1,-1);break;}
                    if(!zoomed&&swiping){swiping=false;move(1f,0,0,180,null);}
                    break;
                default:
            }
        }
        private void follow(android.view.MotionEvent e) {
            if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){if(speed!=null)speed.recycle();speed=android.view.VelocityTracker.obtain();}
            if(speed!=null)speed.addMovement(e);
        }
    }

    /**
     * The dots at the foot of Home's grid, one for each page where it lies (the one in view among them, even empty), nothing
     * while there is one; the filled one goes with the finger as the pages slide, as a phone's own home screen's does.
     */
    private final class PageDots extends View {
        PageDots(){super(a);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            if(homeLaid==null||homeLaid.spots==null)return;
            java.util.Set<List<Integer>> pages=Layout.dotted(homeLaid.spots,pageX,pageY);
            if(pages.size()<=1)return;
            int minX=0,maxX=0,minY=0,maxY=0;
            for(List<Integer> one:pages){minX=Math.min(minX,one.get(0));maxX=Math.max(maxX,one.get(0));minY=Math.min(minY,one.get(1));maxY=Math.max(maxY,one.get(1));}
            float step=a.dp(12);float x0=(getWidth()-(maxX-minX)*step)/2f,y0=getHeight()/2f-(maxY-minY)*step/2f;
            paint.setColor(MainActivity.mix(a.INK,a.PAPER,0.6f));
            for(List<Integer> one:pages)canvas.drawCircle(x0+(one.get(0)-minX)*step,y0+(one.get(1)-minY)*step,a.dp(3),paint);
            // Where the pages stand now, part way from one to the next while they slide.
            float gap=a.dp(18),seenX=pageX,seenY=pageY;
            if(pager!=null&&pager.getWidth()>0){seenX-=shiftX/(pager.getWidth()+gap);seenY-=shiftY/(pager.getHeight()+gap);}
            seenX=Math.max(minX,Math.min(maxX,seenX));seenY=Math.max(minY,Math.min(maxY,seenY));
            paint.setColor(a.ACCENT);
            canvas.drawCircle(x0+(seenX-minX)*step,y0+(seenY-minY)*step,a.dp(4),paint);
        }
    }

    /** How many icons go across a width in pixels: see {@link Grid#columns}. */
    int columns(int widthPx){return Grid.columns(widthPx/a.getResources().getDisplayMetrics().density);}

    /**
     * The bar as it always was at the top: the app's name and which build this is, the mark for everything on this
     * device, and ⋮ with the app's rows. Nothing at the left, where a level further in has its arrow back, so the name
     * stands in the middle as it does on every other screen.
     */
    private LinearLayout bar() {
        LinearLayout top=a.bar();
        top.addView(new View(a),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));
        TextView called=a.label(a.getString(R.string.app_name),MainActivity.READING,a.MUTED);
        called.setGravity(Gravity.CENTER);called.setSingleLine(true);called.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout named=a.column();named.setGravity(Gravity.CENTER);
        named.addView(called,new LinearLayout.LayoutParams(-1,-2));
        a.versionLine=a.label("",MainActivity.QUIET,a.MUTED);a.versionLine.setGravity(Gravity.CENTER);a.versionLine.setSingleLine(true);
        named.addView(a.versionLine,new LinearLayout.LayoutParams(-1,-2));
        a.versionShown();
        top.addView(named,new LinearLayout.LayoutParams(0,-2,1));
        homeMark=a.syncMark(home());
        top.addView(homeMark);
        top.addView(a.tap("⋮","Home",22,a.INK,v->a.menuFor(v,home())));
        return top;
    }

    /**
     * The + : a round button, with no word on it, as a phone's own is - named for anybody who cannot see it. It offers
     * the two things there are, for where it is: Home's makes them on Home, a pop-up's in that collection. Every + also
     * takes something from another device - a note or a collection someone shows the code of, scanned or pasted - which
     * arrives on Home, as the box that takes it says (the owner: "this is key").
     *
     * @param where the collection it makes things in, asked when it is pressed, so a pop-up that has moved on is followed
     */
    View plus(final java.util.function.Supplier<String> where) {
        TextView plus=a.label("+",26,a.PAPER);
        plus.setGravity(Gravity.CENTER);plus.setIncludeFontPadding(false);
        GradientDrawable round=new GradientDrawable();round.setShape(GradientDrawable.OVAL);round.setColor(a.ACCENT);
        // The touch shown inside the round and nowhere else: with no mask, a ripple keeps to what it is drawn over.
        plus.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x40FFFFFF),round,null));
        plus.setElevation(a.dp(6));
        plus.setContentDescription("New");
        plus.setOnClickListener(v->a.heldMenu(v,null,"Note",(Runnable)()->newNote(where.get()),"Collection",(Runnable)()->newCollection(where.get()),
            null,"From another device…",(Runnable)a::addFromSomeone));
        return plus;
    }

    /** Where a + sits: the foot of what it adds to, on the right, where the thumb is. */
    FrameLayout.LayoutParams plusPlace() {
        FrameLayout.LayoutParams place=new FrameLayout.LayoutParams(a.dp(56),a.dp(56),Gravity.BOTTOM|Gravity.END);
        place.setMargins(0,0,a.dp(18),a.dp(16));
        return place;
    }

    /**
     * The search bar above the dock: a rounded, quiet field that says Search, and opens the search that finds notes,
     * collections and files alike, by name and by what they say.
     */
    private View searchBar() {
        TextView search=a.label("Search",MainActivity.READING,a.MUTED);
        search.setGravity(Gravity.CENTER_VERTICAL);search.setSingleLine(true);
        search.setMinimumHeight(a.dp(48));search.setPadding(a.dp(20),0,a.dp(20),0);
        GradientDrawable field=new GradientDrawable();
        field.setColor(a.CARD);field.setCornerRadius(a.dp(24));field.setStroke(Math.max(1,a.dp(1)),a.LINE);
        search.setBackground(field);
        search.setForeground(a.getDrawable(a.touchFeedback()));
        search.setContentDescription("Search notes, collections and files");
        search.setOnClickListener(v->a.searching());
        return search;
    }

    private LinearLayout.LayoutParams searchPlace() {
        LinearLayout.LayoutParams place=new LinearLayout.LayoutParams(-1,-2);
        place.setMargins(a.dp(16),a.dp(6),a.dp(16),a.dp(2));
        return place;
    }

    // ---- reading it again ----------------------------------------------------------------------------------------

    /**
     * Home read again, and the pop-up and the dock with it: after anything changes, here or on another device. What the
     * reader is looking at does not move - each grid keeps where it was scrolled to, and an open pop-up stays open on the
     * collection it shows - unless that collection has gone, which is then said.
     */
    void refresh() {
        if(!showing())return;
        // Not under a thing being carried, whose icon would be taken out from under the finger, nor under a name being
        // typed, whose field would go with the row it is in. Each ends by reading Home again.
        if(a.dragging!=null||naming()){stale=true;return;}
        stale=false;
        final int read=++reads;
        final List<MainActivity.Step> path=new ArrayList<>(a.trail);
        final boolean away=a.awayOnHome;
        a.background.submit(()->{
            // The icons' set is read here the first time, off the screen's thread, before a face is drawn with it.
            Icons.all();
            Map<String,Object> got=new HashMap<>();
            got.put("home",a.store.contents(Things.HOME));
            got.put("beyond",a.store.favouritesBeyondDock());
            got.put("dock",a.store.dock());
            got.put("colour",a.libraryColour());
            // How many things wait in the archive and the bin, for the count on each (decision 41).
            if(away){got.put("archived",a.store.awayCount(false));got.put("binned",a.store.awayCount(true));}
            if(path.size()>1)folder.read(path,got);
            return got;
        },got->{
            if(read!=reads||!showing())return;
            if(naming()){stale=true;return;}
            a.levelColour=(Integer)got.get("colour");a.repaint();
            @SuppressWarnings("unchecked") List<NoteStore.Branch> lines=(List<NoteStore.Branch>)got.get("home");
            @SuppressWarnings("unchecked") List<NoteStore.Branch> beyond=(List<NoteStore.Branch>)got.get("beyond");
            @SuppressWarnings("unchecked") List<NoteStore.Branch> docked=(List<NoteStore.Branch>)got.get("dock");
            List<NoteStore.Branch> places=new ArrayList<>();
            if(!beyond.isEmpty())places.add(favourites());
            if(away){places.add(archive((Integer)got.get("archived")));places.add(bin((Integer)got.get("binned")));}
            fill(lines,places);
            placeMade();
            // The page in view gone empty - its last icon moved or put away - is gone: the main page instead.
            if(homeLaid!=null&&homeLaid.spots!=null&&!Layout.active(homeLaid.spots).contains(java.util.Arrays.asList(pageX,pageY)))showPage(0,0,0,0);
            if(dots!=null)dots.invalidate();
            dock.fill(docked);
            if(path.size()>1&&folder.isOpen())folder.fill(path,got);
            if(homeMark!=null)a.askOwed(homeMark,home());
        },e->a.alert(MainActivity.READ_FAILED));
    }

    /** Whether a name is being typed somewhere on Home: redrawn now, the field would be taken away mid-word. */
    private boolean naming(){return a.naming!=null&&a.naming.isAttachedToWindow();}

    /**
     * Home's grid, drawn again where it stands: each icon in its cell, and Home's places among them - the Favourites
     * collection when it holds anything, the archive and the bin unless they are switched off (see {@link #lay}).
     */
    private void fill(List<NoteStore.Branch> lines,List<NoteStore.Branch> places) {
        final int kept=gridScroll.getScrollY();
        homeLaid=lay(grid,lines,places,a.getResources().getDisplayMetrics().widthPixels,Where.HOME,
            gridScroll.getHeight(),"Nothing here yet. Tap + to make a note or a collection.");
        buildPages();
        // Scrolled back to where it was once the rows are laid out again: an arrival is not a reason to lose your place.
        gridScroll.post(()->gridScroll.scrollTo(0,kept));
    }

    // ---- icons where they were put (docs/HOME.md, decision 39) ------------------------------------------------------

    /**
     * One grid as it was last drawn: where each icon stands and what it is, the empty cells between, and how big a cell
     * is - what a finger over the grid is measured against, and what a thing let go in an empty cell is written from.
     */
    static final class Laid {
        /** Each icon's {row, column}, by id; a place's under its own id ({@link NoteStore#FAVOURITES} and the rest). */
        final Map<String,int[]> drawn;
        /** The lines drawn, as the notebook gave them: whose cells {@link NoteStore#place} writes. */
        final List<NoteStore.Branch> lines;
        /** Home's places drawn among them - the Favourites collection, the archive, the bin - whose cells this phone keeps. */
        final List<NoteStore.Branch> places;
        /** On Home, where every icon stands on every page (Layout.pages); null for a card's one grid. */
        Map<String,Layout.Spot> spots;
        final Where where;
        /** Each icon's line and view by id, and each empty cell's view by {@link Layout#cell}. */
        final Map<String,NoteStore.Branch> things=new HashMap<>();
        final Map<String,View> icons=new HashMap<>();
        final Map<Integer,View> spaces=new HashMap<>();
        /** Columns and rows drawn, a cell's width and height in pixels, and how tall the grid was seen when it was laid out. */
        final int columns,rows,width,height,seen;

        Laid(Map<String,int[]> drawn,List<NoteStore.Branch> lines,List<NoteStore.Branch> places,Where where,int columns,int rows,int width,int height,int seen) {
            this.drawn=drawn;this.lines=lines;this.places=places;this.where=where;
            this.columns=columns;this.rows=rows;this.width=width;this.height=height;this.seen=seen;
        }
    }

    /**
     * A grid's icons, each in its cell (decision 39): where it was put, or - put nowhere yet - the first free cell from the
     * top, in the owner's order. Every cell between is an empty place the size of an icon, so the grid does not close them
     * up and a thing can be let go in any of them; there is always a free row under the last icon, and rows enough to fill
     * the height the grid is seen in, so there is always somewhere to put a thing. Added row by row, so a screen reader
     * goes through them in the order they are seen.
     *
     * @param places     Home's places, to draw among them where each was put (see {@link Layout#home}), or none
     * @param widthPx    how wide the grid is drawn, for its columns
     * @param seen       how tall the grid is seen, in pixels, or 0 before it has been measured
     * @param nothingYet what an empty grid says to do
     */
    Laid lay(GridLayout in,List<NoteStore.Branch> lines,List<NoteStore.Branch> places,int widthPx,Where where,int seen,String nothingYet) {
        in.removeAllViews();
        int columns=Math.max(1,in.getColumnCount()),cell=cell(in,widthPx),high=rowHeight(cell);
        List<String> ids=new ArrayList<>();List<Integer> cells=new ArrayList<>();
        // Among the favourites, the archive and the bin a thing is only listed, not kept, so it has no cell there: they
        // stand one after the other.
        boolean listed=where==Where.FAVOURITES||where==Where.AWAY;
        for(NoteStore.Branch one:lines){ids.add(one.id);cells.add(listed?Layout.NONE:one.cell);}
        int least=seen>0?seen:a.getResources().getDisplayMetrics().heightPixels;
        Map<String,int[]> drawn;Map<String,Layout.Spot> spots=null;int rows;
        if(where==Where.HOME) {
            // Home: pages as big as the screen holds, every one the same (decision 48), and the page in view drawn.
            rows=Math.max(1,(least-in.getPaddingTop()-in.getPaddingBottom())/high);
            List<String> all=new ArrayList<>();List<Integer> onPages=new ArrayList<>(),cellsOn=new ArrayList<>();
            NoteStore.Branch lead=null;
            for(NoteStore.Branch place:places)if(place.kind==NoteStore.Branch.Kind.FAVOURITES&&placeCell(place.id)<0)lead=place;
            if(lead!=null){all.add(lead.id);onPages.add(Layout.NO_PAGE);cellsOn.add(Layout.NONE);}
            for(NoteStore.Branch one:lines){all.add(one.id);onPages.add(one.page);cellsOn.add(one.cell);}
            for(NoteStore.Branch place:places)if(place!=lead){all.add(place.id);onPages.add(placePage(place.id));cellsOn.add(placeCell(place.id));}
            spots=Layout.pages(all,onPages,cellsOn,new java.util.HashSet<>(java.util.Arrays.asList(NoteStore.ARCHIVE,NoteStore.BIN)),columns,rows);
            drawn=new java.util.LinkedHashMap<>();
            for(Map.Entry<String,Layout.Spot> one:spots.entrySet())
                if(one.getValue()!=null&&one.getValue().on(pageX,pageY))drawn.put(one.getKey(),new int[]{one.getValue().row(),one.getValue().column()});
        } else {
            Map<String,Integer> at=new java.util.LinkedHashMap<>();
            for(NoteStore.Branch place:places)at.put(place.id,placeCell(place.id));
            drawn=Layout.home(ids,cells,at,NoteStore.FAVOURITES,columns);
            rows=Math.max(Layout.rows(drawn)+1,(int)Math.ceil((least-in.getPaddingTop()-in.getPaddingBottom())/(double)high));
        }
        Laid laid=new Laid(drawn,lines,places,where,columns,rows,cell,high,seen);
        laid.spots=spots;
        if(lines.isEmpty()&&places.isEmpty()) {
            // Nothing to carry and nowhere to carry it: the words saying what to do, and no empty cells under them.
            View said=nothing(in,nothingYet,widthPx);
            ((GridLayout.LayoutParams)said.getLayoutParams()).rowSpec=GridLayout.spec(0);
            in.addView(said);
            return laid;
        }
        // Nothing of the owner's yet, only the places: the words saying what to do in the row under them, on the main page.
        int sayingRow=lines.isEmpty()&&(where!=Where.HOME||onCentre())?Layout.rows(drawn):-1;
        if(sayingRow>=rows)sayingRow=-1;
        Map<Integer,String> byCell=new HashMap<>();
        for(Map.Entry<String,int[]> one:drawn.entrySet())byCell.put(Layout.cell(one.getValue()[0],one.getValue()[1]),one.getKey());
        Map<String,NoteStore.Branch> byId=new HashMap<>();
        for(NoteStore.Branch one:lines)byId.put(one.id,one);
        for(NoteStore.Branch one:places)byId.put(one.id,one);
        for(int row=0;row<rows;row++)for(int column=0;column<columns;column++) {
            if(row==sayingRow) {
                if(column==0){View said=nothing(in,nothingYet,widthPx);((GridLayout.LayoutParams)said.getLayoutParams()).rowSpec=GridLayout.spec(row);
                    said.setOnLongClickListener(v->{roomHeld(v,where);return true;});in.addView(said);}
                continue;
            }
            String id=byCell.get(Layout.cell(row,column));
            View view;
            if(id==null) {
                view=new View(a);
                view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                // Held down, the room's menu - Home's, or the card's - as a right-click on it on the PC (decision 44).
                view.setOnLongClickListener(v->{roomHeld(v,where);return true;});
                GridLayout.LayoutParams place=new GridLayout.LayoutParams();
                place.width=cell;place.height=high;
                view.setLayoutParams(place);
                laid.spaces.put(Layout.cell(row,column),view);
            } else {
                NoteStore.Branch one=byId.get(id);
                view=Grid.place(one.kind)?placeIcon(one,cell):icon(one,cell,where);
                // The one being carried, met again on its own page while it is carried over others: still in the hand.
                if(where==Where.HOME&&a.dragging!=null&&id.equals(a.dragging.id))view.setVisibility(View.INVISIBLE);
                // As tall as a name of two lines at the least, so every row is one height and a cell is found by sum.
                view.setMinimumHeight(high);
                laid.things.put(id,one);laid.icons.put(id,view);
            }
            GridLayout.LayoutParams place=(GridLayout.LayoutParams)view.getLayoutParams();
            place.rowSpec=GridLayout.spec(row);place.columnSpec=GridLayout.spec(column);
            in.addView(view,place);
        }
        return laid;
    }

    /** The empty room of a grid held down: the menu of what the grid shows - Home, the card's collection, or the place. */
    private void roomHeld(View on,Where where) {
        if(where==Where.HOME){a.roomFor(on,home());return;}
        NoteStore.Branch shown=folder.room();
        if(shown==null)return;
        if(where==Where.CARD)a.roomFor(on,shown);else a.heldFor(on,shown);
    }

    /** How tall a row of icons is: an icon whose name takes its two lines, at the reading size the pad is at now. */
    int rowHeight(int cell) {
        TextView name=a.label("Ag\nAg",MainActivity.READING,a.INK);
        name.setMaxLines(2);name.setPadding(0,a.dp(6),0,0);
        name.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1,cell),View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        return a.dp(8)+face(cell)+name.getMeasuredHeight()+a.dp(6);
    }

    /**
     * A grid laid out again where what it was laid out for has changed: the height it is seen in - it is first drawn before
     * the screen has measured it - or the size of its names, after the reading ladder. Never under a thing being carried
     * or a name being typed, each of which ends by reading Home again.
     */
    void refit(ScrollView scroll,Laid laid,Runnable again) {
        if(laid==null||scroll==null||a.dragging!=null||naming())return;
        if(scroll.getHeight()==laid.seen&&rowHeight(laid.width)==laid.height)return;
        scroll.post(again);
    }

    /** Home's places, by id: each keeps its cell in this phone's settings - "favouritesCell", "archiveCell", "binCell". */
    private static final String[] PLACES={NoteStore.FAVOURITES,NoteStore.ARCHIVE,NoteStore.BIN};

    /** Where a place was pinned on Home at the last move there, or none. Kept on this phone, as every cell is. */
    private int placeCell(String id) {
        try{return a.getSharedPreferences("settings",android.content.Context.MODE_PRIVATE).getInt(id+"Cell",Layout.NONE);}
        catch(ClassCastException unlike){return Layout.NONE;}
    }

    /** The page a place was pinned on, or none for one pinned before pages. */
    private int placePage(String id) {
        try{return a.getSharedPreferences("settings",android.content.Context.MODE_PRIVATE).getInt(id+"Page",Layout.NO_PAGE);}
        catch(ClassCastException unlike){return Layout.NO_PAGE;}
    }

    /** The places' pages and cells, among those a move on Home pins, kept. */
    private void placesOn(Map<String,Layout.Spot> spots) {
        if(spots==null)return;
        android.content.SharedPreferences.Editor kept=null;
        for(String id:PLACES) {
            Layout.Spot at=spots.get(id);
            if(at==null)continue;
            if(kept==null)kept=a.getSharedPreferences("settings",android.content.Context.MODE_PRIVATE).edit();
            kept.putInt(id+"Cell",at.cell()).putInt(id+"Page",at.page());
        }
        if(kept!=null)kept.apply();
    }

    /** The places' cells, among the cells a move on Home pins, kept - they have no row of their own to keep them in. */
    private void placesAt(Map<String,Integer> cells) {
        if(cells==null)return;
        android.content.SharedPreferences.Editor kept=null;
        for(String id:PLACES) {
            Integer at=cells.get(id);
            if(at==null)continue;
            if(kept==null)kept=a.getSharedPreferences("settings",android.content.Context.MODE_PRIVATE).edit();
            kept.putInt(id+"Cell",at);
        }
        if(kept!=null)kept.apply();
    }

    /** The cell of a grid under a point of the screen's column, or null outside its columns and rows. */
    private int[] cellAt(GridLayout in,Laid laid,float x,float y) {
        if(in==null||laid==null||in.getParent()==null)return null;
        Rect at=rect(in);
        if(at.isEmpty())return null;
        int[] cell=Layout.under(x-at.left-in.getPaddingLeft(),y-at.top-in.getPaddingTop(),laid.width,laid.height,laid.columns);
        return cell==null||cell[0]>=laid.rows?null:cell;
    }

    /**
     * Where a carried thing, or files, will land if let go now: an empty cell, outlined quietly where an icon's face would
     * be - the place it will stand - or nowhere, which takes the outline away.
     */
    private void aim(Laid in,int[] at) {
        View space=in==null||at==null?null:in.spaces.get(Layout.cell(at[0],at[1]));
        aimIn=space==null?null:in;aimAt=space==null?null:at;
        if(space==landing)return;
        if(landing!=null)landing.setBackground(null);
        landing=space;
        if(space==null)return;
        int face=face(in.width),side=(in.width-face)/2;
        GradientDrawable outline=new GradientDrawable();
        outline.setCornerRadius(Looks.corner(face,a.dp(14)));outline.setStroke(Math.max(1,a.dp(2)),a.MUTED);
        space.setBackground(new android.graphics.drawable.InsetDrawable(outline,side,a.dp(8),in.width-face-side,Math.max(0,in.height-a.dp(8)-face)));
    }

    /** How wide one icon's column is in a grid drawn across {@code widthPx}, less the grid's own edges. */
    int cell(GridLayout in,int widthPx) {
        return Math.max(a.dp(56),(widthPx-in.getPaddingLeft()-in.getPaddingRight())/Math.max(1,in.getColumnCount()));
    }

    /** A quiet line across a grid with nothing in it, saying what to do. */
    View nothing(GridLayout in,String words,int widthPx) {
        TextView said=a.label(words,MainActivity.QUIET,a.MUTED);
        said.setGravity(Gravity.CENTER);said.setPadding(a.dp(20),a.dp(48),a.dp(20),a.dp(20));
        GridLayout.LayoutParams across=new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),GridLayout.spec(0,in.getColumnCount()));
        across.width=in.getColumnCount()*cell(in,widthPx);
        said.setLayoutParams(across);
        return said;
    }

    // ---- icons -------------------------------------------------------------------------------------------------

    /**
     * Where an icon is: on Home's grid, in a collection's pop-up, in the Favourites collection's, or in the archive's or the
     * bin's (decision 41), where a thing waits to be put back.
     */
    enum Where { HOME, CARD, FAVOURITES, AWAY }

    /**
     * One thing as an icon, as a phone draws an app: its face, its name under it in two lines at most, and its mark on
     * the corner. A note's or a collection's face is its picture or its icon, and with neither, a note's is the note
     * glyph and a collection's the mini-grid of what is inside it (see {@link IconFace}); a file's, what kind of file it
     * is, with <i>new</i> on it until it is opened. Tapping opens it; holding gives its menu; moving while holding carries it.
     * In the archive or the bin, a tap or a hold asks where it goes from there, as their lists always did.
     */
    View icon(final NoteStore.Branch branch,int cell,final Where where) {
        LinearLayout tile=a.column();tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(a.dp(4),a.dp(8),a.dp(4),a.dp(6));
        GridLayout.LayoutParams place=new GridLayout.LayoutParams();
        place.width=cell;place.height=GridLayout.LayoutParams.WRAP_CONTENT;
        tile.setLayoutParams(place);
        int face=face(cell);
        tile.addView(faced(branch,face,where==Where.HOME||where==Where.CARD),new LinearLayout.LayoutParams(face,face));
        tile.setClipChildren(false);tile.setClipToPadding(false);
        TextView name=a.label(branch.name,MainActivity.READING,a.INK);
        name.setGravity(Gravity.CENTER);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0,a.dp(6),0,0);
        tile.addView(name,new LinearLayout.LayoutParams(-1,-2));
        tile.setBackgroundResource(a.touchFeedback());
        if(where==Where.AWAY) {
            final boolean bin=folder.inBin();
            tile.setContentDescription(branch.name+", "+branch.detail+". Tap to put it back"+(bin?" or delete it for good":""));
            tile.setTag(branch);
            tile.setOnClickListener(v->a.awaySheet(v,branch,bin));
            tile.setOnLongClickListener(v->{a.awaySheet(v,branch,bin);return true;});
            return tile;
        }
        tile.setContentDescription(said(branch));
        tile.setOnClickListener(v->opened(branch,where));
        a.grabbable(tile,branch);
        return tile;
    }

    /** How big an icon's face is in a column of a width: a margin round it, and never so small it cannot be hit. */
    int face(int cell){return Math.max(a.dp(40),Math.min(a.dp(72),cell-a.dp(28)));}

    /**
     * An icon's face, with its mark on the corner and, where it is a favourite, a star on the other: the picture is what
     * the eye lands on, and its corners are the one part of it that is never the thing itself.
     *
     * @param starred whether a favourite says so: not among the favourites, where every one would
     */
    View faced(NoteStore.Branch branch,int face,boolean starred) {
        // A note or a collection wears its look (docs/HOME.md, step 4): its picture, its icon, or its default.
        View picture=branch.kind==NoteStore.Branch.Kind.FILE?fileFace(branch,face):IconFace.view(a,branch,face);
        FrameLayout over=new FrameLayout(a);
        over.addView(picture,new FrameLayout.LayoutParams(face,face));
        int badge=Math.max(a.dp(16),face/4);
        View mark=branch.kind==NoteStore.Branch.Kind.FILE?null:a.shareBadge(branch,badge);
        if(mark!=null) {
            FrameLayout.LayoutParams corner=new FrameLayout.LayoutParams(badge,badge,Gravity.TOP|Gravity.END);
            corner.setMargins(0,-badge/4,-badge/4,0);
            over.addView(mark,corner);
        }
        if(starred&&branch.kept) {
            FrameLayout.LayoutParams other=new FrameLayout.LayoutParams(badge,badge,Gravity.TOP|Gravity.START);
            other.setMargins(-badge/4,-badge/4,0,0);
            over.addView(a.star(badge),other);
        }
        if(branch.kind==NoteStore.Branch.Kind.FILE&&branch.fresh) {
            TextView fresh=a.label("new",MainActivity.QUIET,a.PAPER);
            fresh.setIncludeFontPadding(false);fresh.setPadding(a.dp(6),a.dp(2),a.dp(6),a.dp(3));
            GradientDrawable pill=new GradientDrawable();pill.setColor(a.ACCENT);pill.setCornerRadius(a.dp(10));
            fresh.setBackground(pill);
            fresh.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams corner=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);
            corner.setMargins(0,-a.dp(6),-a.dp(8),0);
            over.addView(fresh,corner);
        }
        over.setClipChildren(false);over.setClipToPadding(false);
        return over;
    }

    /** A file's face: what kind of file it is, in its own letters, on a pale square, as its card under a note has it. */
    private View fileFace(NoteStore.Branch file,int face) {
        int dot=file.name.lastIndexOf('.');
        String kind=dot<0||dot==file.name.length()-1?"FILE":file.name.substring(dot+1).toUpperCase(java.util.Locale.ROOT);
        TextView tile=a.label(kind.length()>4?kind.substring(0,4):kind,MainActivity.QUIET,a.ACCENT);
        tile.setTypeface(null,android.graphics.Typeface.BOLD);tile.setGravity(Gravity.CENTER);tile.setSingleLine(true);
        GradientDrawable pale=new GradientDrawable();pale.setColor(MainActivity.mix(a.ACCENT,a.CARD,0.88f));pale.setCornerRadius(a.dp(14));
        pale.setStroke(Math.max(1,a.dp(1)),a.LINE);
        tile.setBackground(pale);
        tile.setMinimumWidth(face);tile.setMinimumHeight(face);
        return tile;
    }

    /**
     * One of Home's places as an icon (decision 41): the Favourites collection's star, the archive's box, the bin's bin,
     * each on an outlined square where a collection shows what is in it, and - on the archive and the bin - how many things
     * wait there. Tapped, it opens as a card; held, its menu; moved while held, it goes to another empty cell of Home
     * (decision 42). It is never renamed, shared, given another icon or put away.
     */
    private View placeIcon(final NoteStore.Branch place,int cell) {
        LinearLayout tile=a.column();tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(a.dp(4),a.dp(8),a.dp(4),a.dp(6));
        GridLayout.LayoutParams at=new GridLayout.LayoutParams();
        at.width=cell;at.height=GridLayout.LayoutParams.WRAP_CONTENT;
        tile.setLayoutParams(at);
        tile.setClipChildren(false);tile.setClipToPadding(false);
        int face=face(cell);
        tile.addView(placeFace(place,face),new LinearLayout.LayoutParams(face,face));
        TextView name=a.label(place.name,MainActivity.READING,a.INK);
        name.setGravity(Gravity.CENTER);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0,a.dp(6),0,0);
        tile.addView(name,new LinearLayout.LayoutParams(-1,-2));
        tile.setBackgroundResource(a.touchFeedback());
        tile.setContentDescription(said(place));
        tile.setOnClickListener(v->opened(place,Where.HOME));
        a.grabbable(tile,place);
        return tile;
    }

    /**
     * A place's face: the star for the Favourites collection, and for the archive and the bin their Lucide glyphs, on the
     * same outlined square; on the corner where a thing wears its mark, how many things are in the archive or the bin -
     * quiet, since a full bin is not news - and nothing when it is empty.
     */
    View placeFace(NoteStore.Branch place,int face) {
        if(place.kind==NoteStore.Branch.Kind.FAVOURITES)return starFace(face);
        FrameLayout over=new FrameLayout(a);
        View square=new View(a);
        square.setBackground(a.edged(a.CARD,Tint.NONE,false));
        over.addView(square,new FrameLayout.LayoutParams(face,face));
        View glyph=new View(a);
        glyph.setBackground(IconFace.bare(a,place.kind==NoteStore.Branch.Kind.BIN?BIN_ICON:ARCHIVE_ICON,a.INK,0));
        over.addView(glyph,new FrameLayout.LayoutParams(face,face));
        int count=a.countIn(place);
        if(count>0) {
            TextView many=a.label(count>99?"99+":String.valueOf(count),MainActivity.QUIET,a.INK);
            many.setIncludeFontPadding(false);many.setGravity(Gravity.CENTER);
            many.setMinWidth(a.dp(22));many.setPadding(a.dp(6),a.dp(2),a.dp(6),a.dp(3));
            GradientDrawable pill=new GradientDrawable();pill.setColor(a.PAPER);pill.setCornerRadius(a.dp(11));
            pill.setStroke(Math.max(1,a.dp(1)),a.LINE);
            many.setBackground(pill);
            many.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams corner=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);
            corner.setMargins(0,-a.dp(6),-a.dp(8),0);
            over.addView(many,corner);
        }
        over.setClipChildren(false);over.setClipToPadding(false);
        return over;
    }

    /** The archive's and the bin's icons in the Lucide set (decision 41; the set's "trash" is the bin with lines in it). */
    static final String ARCHIVE_ICON="archive",BIN_ICON="trash";

    /** A star on an outlined square: the Favourites collection's face, here and in the dock. */
    View starFace(int face) {
        LinearLayout box=a.column();box.setGravity(Gravity.CENTER);
        box.setBackground(a.edged(a.CARD,Tint.NONE,false));
        TextView star=a.label("★",MainActivity.QUIET,a.INK);
        star.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,face*0.42f);
        star.setIncludeFontPadding(false);star.setGravity(Gravity.CENTER);
        box.addView(star,new LinearLayout.LayoutParams(-1,-1));
        box.setMinimumWidth(face);box.setMinimumHeight(face);
        return box;
    }

    /** The Favourites collection, as a thing a menu and a pop-up are about: a place, like the archive, not a collection. */
    static NoteStore.Branch favourites() {
        return new NoteStore.Branch(NoteStore.Branch.Kind.FAVOURITES,NoteStore.FAVOURITES,Sharing.EVERYTHING,"Favourites","",0,0,true);
    }

    /** The archive, as Home shows it, with how many things are in it - which is all its line says. */
    static NoteStore.Branch archive(int count){return place(NoteStore.Branch.Kind.ARCHIVE,NoteStore.ARCHIVE,"Archive",count);}
    /** The bin, as Home shows it, with how many things are in it. */
    static NoteStore.Branch bin(int count){return place(NoteStore.Branch.Kind.BIN,NoteStore.BIN,"Bin",count);}
    private static NoteStore.Branch place(NoteStore.Branch.Kind kind,String id,String name,int count) {
        return new NoteStore.Branch(kind,id,Sharing.EVERYTHING,name,count<=0?"":count==1?"1 thing":count+" things",0,0,true);
    }

    /** What a screen reader says for an icon: what tapping it does, and what else is true of it. */
    static String said(NoteStore.Branch branch) {
        switch(branch.kind) {
            case FAVOURITES: return "Open Favourites, the favourites that are not in the dock";
            case ARCHIVE: return "Open the archive, "+(branch.detail.isEmpty()?"empty":branch.detail);
            case BIN: return "Open the bin, "+(branch.detail.isEmpty()?"empty":branch.detail);
            default:
        }
        String open=branch.kind==NoteStore.Branch.Kind.FILE?"Open the file "+branch.name+", "+branch.detail
            :branch.holds?"Open the collection "+branch.name:"Open the note "+branch.name;
        return open+(branch.kept?", a favourite":"")+(branch.fresh?", new":"");
    }

    /**
     * An icon tapped: a note opens, a collection opens its pop-up - in the same card, from inside one - a file opens, and
     * a place opens as a card of what is in it.
     */
    void opened(NoteStore.Branch branch,Where where) {
        switch(branch.kind) {
            case PAGE: openNote(branch.id);return;
            case FILE: openFile(branch);return;
            case FAVOURITES: folder.openFavourites();return;
            case ARCHIVE: case BIN: folder.openPlace(branch);return;
            case COLLECTION: case BOOK:
                if(where==Where.CARD)folder.into(branch);
                // A favourite opens where it really lives, with the way back being the way back from there.
                else if(where==Where.FAVOURITES)openCollection(branch.id);
                else folder.open(branch);
                return;
            default:
        }
    }

    // ---- opening, from Home and from anything over it ----------------------------------------------------------------

    /**
     * A note, opened from Home: its page, with the way back being Home as it was - the pop-up it was opened from, if one
     * was open, or the grid - rather than the collection the note happens to be in.
     */
    void openNote(String id) {
        a.cameFrom=new ArrayList<>(a.trail);a.cameFromNote=id;
        a.open(id);
    }

    /** A collection, from anywhere: its pop-up over Home, with the trail above it being where it really is, however deep. */
    void openCollection(final String id) {
        a.background.submit(()->a.trailTo(id),steps->{
            a.trail.clear();a.trail.addAll(steps);
            if(showing())folder.show();else a.browse();
        },e->a.alert(MainActivity.READ_FAILED));
    }

    /** A file opened where it is kept: handed to whatever opens it, and no longer new. */
    void openFile(final NoteStore.Branch file) {
        a.background.submit(()->{
            NoteStore.Held held=a.store.file(file.id);
            if(held!=null&&held.fresh)a.store.opened(file.id);
            return held;
        },held->{
            if(held==null){a.alert("That file is not here any more.");refresh();return;}
            a.openFile(held);
            if(file.fresh)a.refresh();
        },e->a.alert(MainActivity.READ_FAILED));
    }

    /** Something asked for in the overview: gone to, from Home as it is. */
    void goTo(Overview.Open one) {
        if(one.kind==Overview.Kind.NOTE)openNote(one.id);else openCollection(one.id);
    }

    // ---- making ------------------------------------------------------------------------------------------------

    /** A new note where the + was pressed - on Home, or in the pop-up's collection - opened at once to be written on. */
    void newNote(final String where) {
        a.background.submit(()->Things.HOME.equals(where)?Boolean.TRUE:a.store.mayWriteIn(NoteStore.Branch.Kind.COLLECTION,where),may->{
            if(Boolean.FALSE.equals(may)){a.alert("This collection is read only here. Ask whoever shared it to let you write in it.");return;}
            NoteStore.Note note=new NoteStore.Note();note.book=where;
            if(Things.HOME.equals(where))placeNew(note.id);
            a.cameFrom=new ArrayList<>(a.trail);a.cameFromNote=note.id;
            a.write(note);
        },e->a.alert(MainActivity.READ_FAILED));
    }

    /**
     * A new collection where the + was pressed, made as Untitled, and its pop-up opened with the name ready to be typed
     * over - selected, with the keyboard up - since a gesture cannot say what a thing is called.
     */
    void newCollection(final String where) {
        a.background.submit(()->a.store.addCollectionIn(where,""),made->{
            if(Things.HOME.equals(where))placeNew(made.id);
            a.nameNext=made.id;
            if(Things.HOME.equals(where)){a.trail.clear();a.trail.add(new MainActivity.Step(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING,"Home"));}
            a.trail.add(new MainActivity.Step(NoteStore.Branch.Kind.COLLECTION,made.id,made.name));
            if(showing())folder.show();else a.browse();
        },e->a.alert("Could not make that collection. Nothing was changed."));
    }

    // ---- the files on Home and in collections ------------------------------------------------------------------

    /**
     * A file's menu, where it is held: what its tap does first, then what else can be done with it, and deleting it last,
     * under the line, since that is the one thing here that cannot be taken back.
     */
    void fileMenu(View anchor,final NoteStore.Branch file) {
        MainActivity.Sheet sheet=a.new Sheet();
        sheet.row("Open",()->openFile(file));
        sheet.row("Save a copy",()->withHeld(file,held->{
            a.savingCopy=loose(held);
            a.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(held.kind).putExtra(Intent.EXTRA_TITLE,held.name),MainActivity.SAVE_COPY);
        }));
        sheet.row("Send to a device",()->a.chooseDevice(Collections.singletonList(Lending.of(file.id)),new ArrayList<>()));
        sheet.row("Put in a note",()->withHeld(file,held->a.putInNote(loose(held))));
        sheet.row("Move to…",()->{a.carrying=file;a.browse();});
        sheet.line();
        sheet.row("Delete",()->askDelete(file));
        sheet.show(anchor);
    }

    /** One kept file, read from the notebook, for what needs more of it than its line says. */
    private void withHeld(final NoteStore.Branch file,final java.util.function.Consumer<NoteStore.Held> then) {
        a.background.submit(()->a.store.file(file.id),held->{
            if(held==null){a.alert("That file is not here any more.");refresh();return;}
            then.accept(held);
        },e->a.alert(MainActivity.READ_FAILED));
    }

    /** A kept file as the received file the older boxes - Save a copy, Put in a note - were written for. */
    private static NoteStore.Loose loose(NoteStore.Held held) {
        return new NoteStore.Loose(held.id,"","",held.name,held.kind,held.bytes,"",true,false,0,0);
    }

    private void askDelete(final NoteStore.Branch file) {
        String came=file.origin.isEmpty()?"":" Whoever sent it still has theirs.";
        a.new Box().setTitle("Delete "+file.name+"?").setMessage("It is deleted from this phone."+came)
            .setPositiveButton("Delete",(d,w)->a.background.submit(()->{
                    NoteStore.Held held=a.store.file(file.id);
                    a.store.drop(file.id);
                    // One kept with a note - found by a search - changes the list of files that travels with the note.
                    return held!=null&&held.held==NoteStore.Branch.Kind.PAGE?held.note:"";
                },note->{a.toast("Deleted");a.refresh();if(!note.isEmpty())a.filesChanged(NoteStore.Branch.Kind.PAGE,note);},
                e->a.alert("Could not delete that. Nothing was changed."))).show();
    }

    // ---- files dragged in from another app ---------------------------------------------------------------------------

    /** Where files from another app would go if let go now: the thing kept with, the empty cell they land in, and what is lit. */
    private static final class Aim {
        final NoteStore.Branch.Kind kind;final String id,name;final Laid in;final int[] cell;final View lit;
        Aim(NoteStore.Branch.Kind kind,String id,String name,Laid in,int[] cell,View lit){this.kind=kind;this.id=id;this.name=name;this.in=in;this.cell=cell;this.lit=lit;}
    }

    /**
     * Files from another app over a grid - Home's, or a card's - aimed at what is under the finger, as the PC aims what
     * Explorer drops: over a note's icon they are kept with the note, over a collection's with that collection, each ringed
     * as a carried thing's target is; over an empty cell, with what the grid shows, in that cell, outlined as the place they
     * land; anywhere else on it, with what the grid shows, in the first free cells.
     *
     * @param holder what the grid shows, asked when they are let go: Home, or the card's collection
     */
    boolean filesOn(View on,DragEvent event,GridLayout in,java.util.function.Supplier<Laid> laid,java.util.function.Supplier<String> holder) {
        int action=event.getAction();
        // The finger, in the screen's column, as a carried icon's is measured: the view's own window, wherever it is scrolled.
        Rect window=seen(on);
        float x=window.left+event.getX(),y=window.top+event.getY();
        final Aim dropped=action==DragEvent.ACTION_DROP?filesAim(in,laid.get(),holder.get(),x,y):null;
        boolean said=a.filesOver(on,event,files->keepAt(files,dropped));
        if(action==DragEvent.ACTION_DRAG_LOCATION&&said) {
            Aim now=filesAim(in,laid.get(),holder.get(),x,y);
            if(now.cell!=null){a.markOnto(null);aim(now.in,now.cell);}
            else{aim(null,null);a.markOnto(now.lit);}
        }
        if(action==DragEvent.ACTION_DRAG_EXITED||action==DragEvent.ACTION_DROP||action==DragEvent.ACTION_DRAG_ENDED){aim(null,null);a.markOnto(null);}
        return said;
    }

    private Aim filesAim(GridLayout in,Laid laid,String holder,float x,float y) {
        int[] at=cellAt(in,laid,x,y);
        String there=at==null?null:Layout.at(laid.drawn,at[0],at[1]);
        NoteStore.Branch thing=there==null?null:laid.things.get(there);
        switch(Grid.filesOnto(thing==null?null:thing.kind)) {
            case NOTE: return new Aim(NoteStore.Branch.Kind.PAGE,thing.id,thing.name,null,null,laid.icons.get(there));
            case COLLECTION: return new Aim(NoteStore.Branch.Kind.COLLECTION,thing.id,thing.name,null,null,laid.icons.get(there));
            default:
                boolean empty=at!=null&&there==null&&laid.spaces.containsKey(Layout.cell(at[0],at[1]));
                return new Aim(NoteStore.Branch.Kind.COLLECTION,holder,"",empty?laid:null,empty?at:null,null);
        }
    }

    /** Files let go where they were aimed: kept there - a note this phone only reads takes none - and put in their cell. */
    private void keepAt(final List<Uri> files,final Aim aim) {
        if(aim==null||aim.id==null||aim.id.isEmpty())return;
        if(aim.kind==NoteStore.Branch.Kind.PAGE) {
            a.background.submit(()->a.store.onlyReads(aim.id),reads->{
                if(Boolean.TRUE.equals(reads))a.alert("“"+aim.name+"” is read only here. Ask whoever shared it to let you write in it.");
                else a.keepFiles(NoteStore.Branch.Kind.PAGE,aim.id,files,new ArrayList<>());
            },e->a.alert(MainActivity.READ_FAILED));
            return;
        }
        if(aim.cell==null){a.keepFiles(aim.kind,aim.id,files,new ArrayList<>());return;}
        final Laid in=aim.in;final int[] at=aim.cell;final boolean onHome=in==homeLaid;
        a.keepFiles(aim.kind,aim.id,files,new ArrayList<>(),kept->{
            // The first in the cell they were let go in, every icon there pinned where it was drawn, and any others after
            // it in the first free cells, as anything new takes.
            List<NoteStore.Branch> lines=new ArrayList<>(in.lines);
            lines.add(new NoteStore.Branch(NoteStore.Branch.Kind.FILE,kept.get(0),"","","",0,0,false));
            if(onHome&&in.spots!=null){Map<String,Layout.Spot> spots=homeMove(in,kept.get(0),at[0],at[1]);if(spots!=null)writeHome(lines,spots);return;}
            Map<String,Integer> cells=Layout.moveTo(in.drawn,kept.get(0),at[0],at[1]);
            if(cells==null)return;
            if(onHome)placesAt(cells);
            a.store.place(lines,cells);
        });
    }

    // ---- what this device sent, now there is no drop box -----------------------------------------------------------

    /**
     * ⋮ → Sent files: what this device sent to others, the newest first, each with where it stands, and a tap to stop
     * it or take it off the list (decision 20). Above it, while there is any, what another device is sending here -
     * somebody asking, or files still on their way - since with the drop box gone this is the one list of sendings left.
     */
    void sentFiles() {
        a.background.submit(()->{
            List<NoteStore.Transfer> sent=a.store.sentFiles(),coming=new ArrayList<>();
            for(NoteStore.Transfer one:a.store.transfers())if(!one.out)coming.add(one);
            Set<String> takes=new HashSet<>();
            for(NoteStore.Transfer one:sent)if(Post.takesFiles(a,a.store.address(one.address)))takes.add(one.address);
            return new Object[]{sent,coming,takes};
        },got->{
            @SuppressWarnings("unchecked") List<NoteStore.Transfer> sent=(List<NoteStore.Transfer>)got[0];
            @SuppressWarnings("unchecked") List<NoteStore.Transfer> coming=(List<NoteStore.Transfer>)got[1];
            @SuppressWarnings("unchecked") Set<String> takes=(Set<String>)got[2];
            LinearLayout body=a.inside();
            final AlertDialog[] box={null};
            if(!coming.isEmpty()) {
                body.addView(a.part("Coming to this phone"));
                for(final NoteStore.Transfer one:coming) {
                    if(one.state==Drop.ASKING){body.addView(line(one.name,Drop.wants(one.files.size(),one.bytes()),true,()->{box[0].dismiss();a.askAboutSending(one.id);}));continue;}
                    int here=0;for(NoteStore.Loose file:one.files)if(file.here)here++;
                    body.addView(line(one.name,Drop.files(one.files.size())+" coming, "+here+" here",false,()->{box[0].dismiss();a.askStopComing(one);}));
                }
                if(!sent.isEmpty())body.addView(a.part("Sent"));
            }
            for(final NoteStore.Transfer one:sent)
                body.addView(line(one.name,Drop.files(one.files.size())+"  ·  "+a.when(one.at)+"  ·  "+Drop.state(one.state,one.name,takes.contains(one.address)),
                    false,()->{box[0].dismiss();a.askStopSending(one);}));
            if(sent.isEmpty()&&coming.isEmpty())body.addView(a.under("Nothing sent from this phone yet. Files go to another device from ⋮ → Send files, or from a file's own menu."));
            box[0]=a.new Box().setTitle("Sent files").setView(a.scrolling(body)).create();
            box[0].show();
        },e->a.alert(MainActivity.READ_FAILED));
    }

    /** One sending, as the drop box listed it, fitted to a box's own margins. */
    private View line(String name,String detail,boolean asking,Runnable tapped) {
        View row=a.dropRow(name,detail,asking,tapped);
        row.setPadding(0,a.dp(12),0,a.dp(12));
        return row;
    }

    /** Somebody wants to send files and has not been answered: asked, where the notification that says so leads. */
    void askWaiting() {
        a.background.submit(()->{
            for(NoteStore.Transfer one:a.store.transfers())if(!one.out&&one.state==Drop.ASKING)return one.id;
            return "";
        },id->{if(!id.isEmpty())a.askAboutSending(id);},e->{});
    }

    // ---- the dock, from a menu --------------------------------------------------------------------------------------

    /** Taken out of the dock: still a favourite, in the Favourites collection, and the rest of the dock closes up. */
    void undock(final NoteStore.Branch thing) {
        a.background.submit(()->{a.store.outOfDock(thing.kind,thing.id);return null;},
            done->{a.toast("Out of the dock, and still a favourite");a.refresh();},e->a.alert("Could not change that. Nothing was changed."));
    }

    // ---- open things, for the overview ---------------------------------------------------------------------------

    /** A note on the page: to the front of the overview, where it is not already. Not a blank page nothing was written on. */
    void noteOpened(NoteStore.Note note) {
        if(note==null||note.id==null)return;
        if((note.title==null||note.title.trim().isEmpty())&&(note.body==null||note.body.trim().isEmpty()))return;
        overview.remember(Overview.Kind.NOTE,note.id);
    }

    /** Something put away or deleted: no card opens what is not there. */
    void forget(String id){overview.forget(id);}

    // ---- back ------------------------------------------------------------------------------------------------------

    /**
     * The phone's back, on Home: a menu that is up goes first, then the overview, then a pop-up one level at a time until
     * it closes. With none of them there is nothing on Home to go back from, and the answer is no.
     */
    boolean back() {
        if(!showing())return false;
        if(a.showing!=null){a.showing.close();return true;}
        if(overview.isShowing()){overview.close();return true;}
        if(folder.isOpen()){folder.up();return true;}
        if(zoomed){zoomInto(focusX,focusY);return true;}
        // On another page, back goes to the main one, as a phone's own home screen does.
        if(!onCentre()){go(0,0);return true;}
        return false;
    }

    /** A colour chosen for a collection while its menu is over its pop-up: the pop-up takes it at once, as the page does. */
    void painted(String id,int colour){folder.painted(id,colour);}

    // ---- carrying ---------------------------------------------------------------------------------------------------

    /**
     * Everything carried on Home comes here, wherever it is picked up: the finger is measured against the dock, an open
     * pop-up, the dimmed grid round it and Home's grid, and letting go does what {@link Grid} says it does there.
     */
    private boolean dragged(View on,DragEvent event) {
        switch(event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED: carriedFrom=null;carriedFrom=from();return a.dragging!=null;
            case DragEvent.ACTION_DRAG_LOCATION: over(event.getX(),event.getY());return true;
            case DragEvent.ACTION_DRAG_EXITED: light(Grid.Zone.NONE);a.markOnto(null);aim(null,null);return true;
            case DragEvent.ACTION_DROP: over(event.getX(),event.getY());return drop(event.getX());
            case DragEvent.ACTION_DRAG_ENDED: ended(event.getResult());return true;
            default: return true;
        }
    }

    /** Where a view is, in the coordinates of the screen's own column, which is what a drag is measured in. */
    private Rect rect(View view) {
        Rect at=new Rect(0,0,view.getWidth(),view.getHeight());
        // A view that has just been taken off the screen is nowhere: an empty place nothing is over.
        try{a.root.offsetDescendantRectToMyCoords(view,at);}catch(IllegalArgumentException gone){at.setEmpty();}
        return at;
    }

    /**
     * Where a view is seen, in the same coordinates: {@link #rect} of a scrolled view is where its inside is, moved up by
     * however far it is scrolled, so its window is asked for from where it is scrolled to.
     */
    private Rect seen(View view) {
        Rect at=new Rect(view.getScrollX(),view.getScrollY(),view.getScrollX()+view.getWidth(),view.getScrollY()+view.getHeight());
        try{a.root.offsetDescendantRectToMyCoords(view,at);}catch(IllegalArgumentException gone){at.setEmpty();}
        return at;
    }

    private boolean inside(View view,float x,float y) {
        if(view==null||view.getParent()==null||view.getVisibility()!=View.VISIBLE)return false;
        return rect(view).contains((int)x,(int)y);
    }

    /** Which grid the carried icon came out of: Home's, the pop-up's, or neither (the dock). */
    private GridLayout from() {
        if(a.lifted==null)return null;
        // Kept from the start of the carry: a page turned under it has drawn its icons anew.
        if(carriedFrom!=null)return carriedFrom;
        if(a.lifted.getParent()==grid)return grid;
        if(folder.grid!=null&&a.lifted.getParent()==folder.grid)return folder.grid;
        return null;
    }

    private void over(float x,float y) {
        if(a.dragging==null)return;
        zone=Grid.zone(inside(dock.row,x,y),folder.isOpen(),inside(folder.card,x,y),inside(desk,x,y));
        light(zone);
        // Carried out of Home's rows - up under the app's name, down short of the dock - is a turn of the page too.
        if((zone==Grid.Zone.HOME||zone==Grid.Zone.NONE)&&from()==grid&&!folder.isOpen())edgeWatch(x,y);else cancelEdge();
        if(zone==Grid.Zone.HOME)across(grid,gridScroll,homeLaid,x,y);
        else if(zone==Grid.Zone.CARD&&!folder.amongFavourites())across(folder.grid,folder.scroll,folder.laid,x,y);
        else if(zone==Grid.Zone.UP&&from()==folder.grid&&!folder.amongFavourites()&&NoteStore.home(folder.parentId()))behind(x,y);
        else{a.markOnto(null);aim(null,null);}
    }

    /**
     * Over the dimmed Home round a card whose collection is on Home: an empty cell of Home's grid under the finger is where
     * a thing brought up out of the card will stand; anywhere else it takes the first free cell, as anything new does.
     */
    private void behind(float x,float y) {
        a.markOnto(null);
        int[] at=cellAt(grid,homeLaid,x,y);
        aim(at!=null&&Layout.at(homeLaid.drawn,at[0],at[1])==null?homeLaid:null,at);
    }

    /** The dock lit while a thing that can go in it is over it, and the dimmed grid darker while one can go up a level. */
    private void light(Grid.Zone now) {
        boolean dockNow=now==Grid.Zone.DOCK&&a.dragging!=null&&Grid.docks(a.dragging.kind);
        if(dockNow!=dockLit){dockLit=dockNow;dock.lit(dockNow);}
        folder.lit(now==Grid.Zone.UP&&from()==folder.grid&&!folder.amongFavourites());
    }

    /**
     * Over a grid, by the cell under the finger (decision 39): a thing it can go onto or into there is ringed; an empty
     * cell is outlined as the place it will stand; anything else - its own cell, a thing that takes nothing from it -
     * shows nothing, and letting go there puts it back. Near the top or foot of the grid, the grid scrolls.
     */
    private void across(GridLayout in,ScrollView scroll,Laid laid,float x,float y) {
        // Only over the grid it came out of: a favourite carried from the dock goes back to the dock or nowhere.
        if(in==null||laid==null||from()!=in){a.markOnto(null);aim(null,null);return;}
        Rect window=seen(scroll);
        int reach=a.dp(56),step=a.dp(14);
        if(y<window.top+reach)scroll.scrollBy(0,-step);
        else if(y>window.bottom-reach)scroll.scrollBy(0,step);
        int[] at=cellAt(in,laid,x,y);
        String there=at==null?null:Layout.at(laid.drawn,at[0],at[1]);
        NoteStore.Branch held=there==null?null:laid.things.get(there);
        Grid.LetGo does=at==null||(there!=null&&held==null)?Grid.LetGo.BACK:Grid.letGo(a.dragging.kind,held==null?null:held.kind,a.dragging.id.equals(there));
        if(does==Grid.LetGo.MERGE||does==Grid.LetGo.INTO||does==Grid.LetGo.AWAY){a.markOnto(laid.icons.get(there));aim(null,null);return;}
        a.markOnto(null);
        aim(does==Grid.LetGo.PLACE?laid:null,at);
    }

    /** Let go: onto a thing, into the dock, up a level, or in an empty cell of its grid. */
    private boolean drop(float x) {
        final NoteStore.Branch carried=a.dragging;
        View target=a.onto;a.markOnto(null);
        final Laid placeIn=aimIn;final int[] placeAt=aimAt;aim(null,null);
        GridLayout came=from();
        boolean fromFavourites=came!=null&&came==folder.grid&&folder.amongFavourites();
        light(Grid.Zone.NONE);
        if(carried==null)return false;
        // Wherever a drop does nothing, Home is drawn again as it is: icons parted on the way there close up again.
        if(zone==Grid.Zone.DOCK) {
            if(!Grid.docks(carried.kind)){stale=true;a.toast("Only a note or a collection goes in the dock");return true;}
            toDock(carried,dock.slotAt(x,a.lifted));
            return true;
        }
        // Among the favourites a thing is only listed, not kept: it goes to the dock from there, and nowhere else.
        if(fromFavourites||came==null){stale=true;return true;}
        if(target!=null&&target.getTag() instanceof NoteStore.Branch) {
            NoteStore.Branch onto=(NoteStore.Branch)target.getTag();
            String container=came==grid?Things.HOME:folder.id();
            Grid.Onto does=Grid.onto(carried.kind,onto.kind);
            // On the archive or the bin: put away there, as its menu puts it away (decision 41).
            if(does==Grid.Onto.AWAY)away(carried,onto.kind==NoteStore.Branch.Kind.BIN);
            else if(does==Grid.Onto.MERGE)merge(carried,onto,container,came==folder.grid);
            else into(carried,onto.id,onto.name,onto.kind,null,null);
            return true;
        }
        if(zone==Grid.Zone.UP&&came==folder.grid) {
            String up=folder.parentId();
            // Let go over an empty cell of Home behind the card: in that cell, once it is on Home. Anywhere else, the first
            // free cell up there, as anything new takes.
            // Onto the page of Home in view, in the empty cell it was let go in.
            final Map<String,Layout.Spot> spots=placeIn!=null&&placeIn==homeLaid?homeMove(placeIn,carried.id,placeAt[0],placeAt[1]):null;
            final List<NoteStore.Branch> lines=new ArrayList<>(placeIn==null?new ArrayList<>():placeIn.lines);lines.add(carried);
            into(carried,up,folder.parentName(),NoteStore.Branch.Kind.COLLECTION,folder::up,spots==null?null:()->writeHome(lines,spots));
            return true;
        }
        if(placeIn!=null&&((zone==Grid.Zone.HOME&&came==grid&&placeIn==homeLaid)||(zone==Grid.Zone.CARD&&came==folder.grid&&placeIn==folder.laid)))put(placeIn,carried,placeAt);
        else stale=true;
        return true;
    }

    /** Held at an edge of Home's page while carrying: the page beyond comes after a moment, a new one if there is none. */
    private Runnable edgeTurn;private int edgeX,edgeY;private boolean turnedHere;
    private void edgeWatch(float x,float y) {
        Rect r=rect(desk);int reach=a.dp(28);
        // Up and down as soon as it leaves the rows icons stand in (the owner's ask): above them, under the app's name, at
        // once; below them at once too, but for a moment's wait while the dock is there, so a thing on its way to the
        // dock does not turn the page as it passes. Left and right, held at the edge for two thirds of a second.
        int dy=0;long wait=650;
        if(homeLaid!=null&&homeLaid.rows>0) {
            int top=rect(grid).top+grid.getPaddingTop(),bottom=top+homeLaid.rows*homeLaid.height;
            // Down only at the page's lower edge - where the dots are - or past it, after a moment: anywhere over the last
            // row it is let go there (the owner, 2026-10-02: "too sensitive to the bottom").
            int edge=Math.max(bottom,rect(pager).bottom-a.dp(44));
            boolean docked=dock.row!=null&&dock.row.getVisibility()==View.VISIBLE;
            if(y<top){dy=-1;wait=0;}
            else if(y>edge){dy=1;wait=docked?400:250;}
        }
        int dx=dy!=0?0:x<r.left+reach?-1:x>r.right-reach?1:0;
        if(dx==0&&dy==0){cancelEdge();turnedHere=false;return;}
        // One page for each time it goes out: back into the rows, or off the edge and back, for the next.
        if(turnedHere||edgeTurn!=null&&dx==edgeX&&dy==edgeY)return;
        cancelEdge();edgeX=dx;edgeY=dy;
        edgeTurn=()->{edgeTurn=null;turnedHere=true;if(a.dragging!=null)showPage(pageX+edgeX,pageY+edgeY,edgeX,edgeY);};
        if(wait==0)edgeTurn.run();else desk.postDelayed(edgeTurn,wait);
    }
    private void cancelEdge(){if(edgeTurn!=null&&desk!=null)desk.removeCallbacks(edgeTurn);edgeTurn=null;}

    private void ended(boolean result) {
        cancelEdge();turnedHere=false;carriedFrom=null;
        light(Grid.Zone.NONE);a.markOnto(null);aim(null,null);zone=Grid.Zone.NONE;
        if(a.lifted!=null)a.lifted.setVisibility(View.VISIBLE);
        a.dragging=null;a.lifted=null;
        if(!result||stale)refresh();
    }

    /**
     * Let go in an empty cell of the grid it came out of: it stays there, and at the first move every other icon is pinned
     * where it is drawn, so nothing closes up behind it (decision 39). The same collection, so nobody it reaches changes and
     * nothing is asked. The icon stands in its cell at once, and the notebook is written behind it, in one go and silently,
     * as the tiles' order always was.
     */
    private void put(Laid in,NoteStore.Branch carried,int[] at) {
        if(in.spots!=null) {
            // Home: on the page in view, which comes to be if it was empty (decision 46); every icon pinned on its page.
            final Map<String,Layout.Spot> spots=homeMove(in,carried.id,at[0],at[1]);
            if(spots==null){stale=true;return;}
            final List<NoteStore.Branch> lines=new ArrayList<>(in.lines);
            a.background.submit(()->{writeHome(lines,spots);return null;},done->{if(!onCentre())a.toast("Moved to "+said(pageX,pageY).toLowerCase(java.util.Locale.ROOT));refresh();},
                e->{a.alert("Could not put that there. Nothing was changed.");refresh();});
            return;
        }
        final Map<String,Integer> cells=Layout.moveTo(in.drawn,carried.id,at[0],at[1]);
        if(cells==null){stale=true;return;}
        if(in==homeLaid)placesAt(cells);
        if(a.lifted!=null&&a.lifted.getLayoutParams() instanceof GridLayout.LayoutParams) {
            GridLayout.LayoutParams place=(GridLayout.LayoutParams)a.lifted.getLayoutParams();
            place.rowSpec=GridLayout.spec(at[0]);place.columnSpec=GridLayout.spec(at[1]);
            a.lifted.setLayoutParams(place);
        }
        final List<NoteStore.Branch> lines=new ArrayList<>(in.lines);
        a.background.submit(()->{a.store.place(lines,cells);return null;},done->refresh(),
            e->{a.alert("Could not put that there. Nothing was changed.");refresh();});
    }

    /**
     * Let go on the archive or the bin: put away there by the same road its menu takes - said, with Undo in the next menu -
     * and a file on the bin asked about first, as its own Delete asks, since there is no bin for files.
     */
    private void away(NoteStore.Branch carried,boolean bin) {
        if(carried.kind==NoteStore.Branch.Kind.FILE){if(bin)askDelete(carried);else stale=true;return;}
        a.putAway(carried,bin);
    }

    /** A favourite at a place in the dock: made one if it was not, and whatever is pushed past the end stays one, in Favourites. */
    private void toDock(final NoteStore.Branch thing,final int slot) {
        a.background.submit(()->{a.store.toDock(thing.kind,thing.id,slot);return null;},
            done->a.refresh(),e->{a.alert("Could not put that in the dock. Nothing was changed.");a.refresh();});
    }

    /**
     * The collections above a line and then the line itself: what a sharing rule is looked for along. A file's is what
     * keeps it - Home, a collection or a note - and then the file, since a file has no row in the tree of its own.
     */
    private List<String> pathOf(NoteStore.Branch line) {
        if(line.kind!=NoteStore.Branch.Kind.FILE)return a.store.pathOf(line.id);
        List<String> path=NoteStore.home(line.parent)?new ArrayList<>():a.store.pathOf(line.parent);
        path.add(line.id);
        return path;
    }

    /** Where a line would be, once it is inside {@code into}: the path down to that, and then the line. */
    private List<String> pathInto(String into,String id) {
        List<String> path=NoteStore.home(into)?new ArrayList<>():a.store.pathOf(into);
        path.add(id);
        return path;
    }

    /** Names of the devices by address, for the words that say who a move reaches. */
    private Map<String,String> names() {
        Map<String,String> names=new HashMap<>();
        for(NoteStore.Contact contact:a.store.addresses())names.put(contact.address,contact.name);
        return names;
    }

    /**
     * Into a collection, or a file into a note - or up a level, out of a pop-up. What changes who it reaches is asked
     * first, as every move is; what the notebook refuses, such as a collection into its own inside, is said as it says it.
     *
     * @param after what to do once it has gone, or null for nothing more than drawing Home again
     * @param there what to write on the worker once it has gone - the cell it was let go in - or null for the first free one
     */
    private void into(final NoteStore.Branch moved,final String dest,final String destName,final NoteStore.Branch.Kind destKind,final Runnable after,final Runnable there) {
        a.background.submit(()->{
            List<Sharing.Rule> rules=a.store.shares();
            // Into a note, a file is reached along the note's own path; into a collection, or Home, along that one's.
            List<String> from=pathOf(moved),to=pathInto(dest,moved.id);
            Sharing.Change change=Sharing.moving(rules,from,to);
            boolean reaches=!Sharing.audience(rules,from).isEmpty()||!Sharing.audience(rules,to).isEmpty();
            return new Object[]{change,names(),reaches};
        },found->{
            Sharing.Change change=(Sharing.Change)found[0];
            final boolean reaches=(Boolean)found[2];
            final Runnable go=()->doInto(moved,dest,destName,destKind,reaches,after,there);
            if(!change.any()){go.run();return;}
            String where=NoteStore.home(dest)?"Home":destName;
            a.new Box().setTitle("This changes who can read it")
                .setMessage(a.changeSaid("Moving “"+moved.name+"” into "+where+" changes who receives it.",change,a.castNames(found[1]),
                    "Whoever starts receiving it gets it now. What has already reached somebody stays with them."))
                .setPositiveButton("Move anyway",(d,w)->go.run())
                .setOnCancelListener(d->a.refresh()).show();
        },e->a.alert(MainActivity.READ_FAILED));
    }

    private void doInto(final NoteStore.Branch moved,final String dest,final String destName,final NoteStore.Branch.Kind destKind,final boolean reaches,final Runnable after,final Runnable there) {
        a.background.submit(()->{
            // Where it was, before it is anywhere else: an undo has to know the place to put it back into.
            String was;
            if(moved.kind==NoteStore.Branch.Kind.FILE){NoteStore.Held held=a.store.file(moved.id);was=held==null?Things.HOME:held.note;}
            else was=moved.kind==NoteStore.Branch.Kind.PAGE?a.store.bookOf(moved.id):a.store.collectionOfBook(moved.id);
            a.store.moveInto(moved.kind,moved.id,dest);
            // And in the cell it was let go in. Moved it is, whatever this says: failing, it stands in the first free cell.
            if(there!=null)try{there.run();}catch(RuntimeException unplaced){/* moved, and in the first free cell */}
            return was==null||was.isEmpty()?Things.HOME:was;
        },was->{
            a.canUndo(moved.name,()->a.store.moveInto(moved.kind,moved.id,was));
            a.toast(NoteStore.home(dest)?"Moved to Home":"Moved into "+destName);
            if(after!=null)after.run();
            a.refresh();
            // Into something somebody else reads is a disclosure: it goes now, as sharing does.
            if(moved.kind==NoteStore.Branch.Kind.FILE){if(destKind==NoteStore.Branch.Kind.PAGE)a.filesChanged(NoteStore.Branch.Kind.PAGE,dest);}
            else if(reaches)a.sendAfterSharing(moved.kind==NoteStore.Branch.Kind.PAGE?Sharing.Scope.PAGE:Sharing.Scope.COLLECTION,moved.id);
        },e->{a.alert(e instanceof IllegalArgumentException&&e.getMessage()!=null?e.getMessage():"Could not move that. Nothing was changed.");a.refresh();});
    }

    /**
     * A note let go on a note: a new collection, Untitled, where the one let go on was, holding both - and its pop-up
     * opened with the name ready to be typed over. Asked first if it changes who either of them reaches.
     */
    private void merge(final NoteStore.Branch dropped,final NoteStore.Branch onto,final String container,final boolean inCard) {
        a.background.submit(()->{
            List<Sharing.Rule> rules=a.store.shares();
            Map<String,Boolean> gained=new java.util.LinkedHashMap<>(),lost=new java.util.LinkedHashMap<>();
            boolean reaches=false;
            for(NoteStore.Branch thing:new NoteStore.Branch[]{dropped,onto}) {
                List<String> from=a.store.pathOf(thing.id),to=pathInto(container,thing.id);
                Sharing.Change change=Sharing.moving(rules,from,to);
                gained.putAll(change.gained);lost.putAll(change.lost);
                if(!Sharing.audience(rules,from).isEmpty())reaches=true;
            }
            return new Object[]{new Sharing.Change(gained,lost),names(),reaches};
        },found->{
            Sharing.Change change=(Sharing.Change)found[0];
            final boolean reaches=(Boolean)found[2];
            final Runnable go=()->doMerge(dropped,onto,inCard,reaches);
            if(!change.any()){go.run();return;}
            a.new Box().setTitle("This changes who can read them")
                .setMessage(a.changeSaid("Putting “"+dropped.name+"” and “"+onto.name+"” together changes who receives them.",change,a.castNames(found[1]),
                    "Whoever starts receiving them gets them now. What has already reached somebody stays with them."))
                .setPositiveButton("Put them together",(d,w)->go.run())
                .setOnCancelListener(d->a.refresh()).show();
        },e->a.alert(MainActivity.READ_FAILED));
    }

    private void doMerge(final NoteStore.Branch dropped,final NoteStore.Branch onto,final boolean inCard,final boolean reaches) {
        a.background.submit(()->a.store.merge(dropped.id,onto.id),made->{
            a.nameNext=made.id;
            if(!inCard){a.trail.clear();a.trail.add(new MainActivity.Step(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING,"Home"));}
            a.trail.add(new MainActivity.Step(NoteStore.Branch.Kind.COLLECTION,made.id,made.name));
            if(showing())folder.show();else a.browse();
            if(reaches)a.sendAfterSharing(Sharing.Scope.COLLECTION,made.id);
        },e->{a.alert(e instanceof IllegalArgumentException&&e.getMessage()!=null?e.getMessage():"Could not put those together. Nothing was changed.");a.refresh();});
    }

    /** Whether the finger has moved far enough, from where it went down, to be more than a tap: the platform's own slop. */
    boolean moved(float dx,float dy) {
        int slop=ViewConfiguration.get(a).getScaledTouchSlop();
        return Math.abs(dx)>slop||Math.abs(dy)>slop;
    }
}
