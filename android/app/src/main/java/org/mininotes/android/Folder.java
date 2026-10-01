// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.Map;

/**
 * A collection opened on Home: a rounded card over the dimmed grid, as a folder opens on a phone's desktop
 * (docs/HOME.md, step 2). Its name at the top - tapped, it becomes the field it already looks like - its mark and its ⋮,
 * then its grid, and its own + at the foot. A collection inside it opens in the same card, with ‹ and the name of the
 * one it is in to go back up (decision 21): phones do not nest folders, and a stack of cards would hide the grid they
 * belong to. Back goes up a level and then closes it; a tap on the dimmed grid closes it at once.
 *
 * <p>Where it is, is the trail MainActivity has always kept - Home, then every collection down to this one - so the way
 * back from a note, a restart and a moved thing all reopen it where it was. The Favourites collection opens the same
 * way, listing the favourites the dock has no room for; it is a place, not a collection, and is never renamed. So do the
 * archive and the bin, while they are on Home (decision 41): what waits in them, a tap on each asking where it goes from
 * there, and on the bin's ⋮, Empty the bin.
 */
final class Folder {
    private final MainActivity a;
    private final HomeScreen home;
    /** The dim over Home's grid while the card is up, the card on it, and the card's parts. */
    private FrameLayout scrim;
    View card;
    private LinearLayout head;
    ScrollView scroll;
    GridLayout grid;
    /** The card's grid as it was last drawn: each icon in its cell, and the empty cells between (decision 39). */
    HomeScreen.Laid laid;
    /** The card's own +, which the places do not have: nothing is made among the favourites, in the archive or in the bin. */
    private View plus;
    /** Which the card shows - a collection's id, or the favourites' - so a card that moves on starts at its top. */
    private String shown="";
    /** The collection the card shows, as the thing its menu and its mark are about, as last read; null among the favourites. */
    private NoteStore.Branch thing;
    /** The collection it is in, as last read: what "up a level" means, and what ‹ says. */
    private String parent=Things.HOME,parentName="Home";

    Folder(MainActivity a,HomeScreen home){this.a=a;this.home=home;}

    boolean isOpen(){return scrim!=null&&scrim.getParent()!=null&&scrim.getParent()==home.desk;}

    private MainActivity.Step last(){return a.trail.get(a.trail.size()-1);}

    /** Whether the card is the Favourites collection's, where things are only listed and never kept. */
    boolean amongFavourites(){return a.trail.size()>1&&last().kind==NoteStore.Branch.Kind.FAVOURITES;}

    /** Whether the card is one of Home's places - the favourites, the archive, the bin - where things are only listed. */
    boolean listing(){return a.trail.size()>1&&Grid.place(last().kind);}

    /** Whether the card is the bin's, where things wait to be put back or deleted for good. */
    boolean inBin(){return a.trail.size()>1&&last().kind==NoteStore.Branch.Kind.BIN;}

    /** The collection the card shows. */
    String id(){return a.trail.size()>1?last().id:Things.HOME;}

    /**
     * What the card shows, as the thing its room's menu is about: the collection as last read, or the place - the
     * favourites, the archive, the bin. Null before the card has been read.
     */
    NoteStore.Branch room() {
        if(a.trail.size()<2)return null;
        MainActivity.Step end=last();
        if(Grid.place(end.kind))return new NoteStore.Branch(end.kind,end.id,Sharing.EVERYTHING,end.name,"",0,0,true);
        return thing;
    }

    /** The collection this one is in, or Home: where a thing dragged out onto the dimmed grid goes. */
    String parentId(){return parent;}
    String parentName(){return parentName;}

    // ---- opening and closing ----------------------------------------------------------------------------------------

    /** A collection on Home's grid, tapped: its card. */
    void open(NoteStore.Branch collection) {
        a.trail.clear();a.trail.add(new MainActivity.Step(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING,"Home"));
        a.trail.add(new MainActivity.Step(NoteStore.Branch.Kind.COLLECTION,collection.id,collection.name));
        show();
    }

    /** The Favourites collection, tapped: the favourites the dock has no room for. */
    void openFavourites() {
        a.trail.clear();a.trail.add(new MainActivity.Step(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING,"Home"));
        a.trail.add(new MainActivity.Step(NoteStore.Branch.Kind.FAVOURITES,NoteStore.FAVOURITES,"Favourites"));
        show();
    }

    /** The archive or the bin, tapped on Home: what waits in it. */
    void openPlace(NoteStore.Branch place) {
        a.trail.clear();a.trail.add(new MainActivity.Step(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING,"Home"));
        a.trail.add(new MainActivity.Step(place.kind,place.id,place.name));
        show();
    }

    /** A collection inside the card, tapped: the same card, one level in. */
    void into(NoteStore.Branch collection) {
        a.trail.add(new MainActivity.Step(NoteStore.Branch.Kind.COLLECTION,collection.id,collection.name));
        show();
    }

    /** ‹, or back: one level up, in the same card, or the card closed where there is no level above it but Home. */
    void up() {
        if(a.trail.size()>2){a.trail.remove(a.trail.size()-1);show();}
        else close();
    }

    /** Put away, and Home under it is what is on the screen again. */
    void close() {
        if(scrim!=null&&scrim.getParent()!=null)((ViewGroup)scrim.getParent()).removeView(scrim);
        if(home.homePlus!=null)home.homePlus.setVisibility(View.VISIBLE);
        scrim=null;card=null;head=null;scroll=null;grid=null;laid=null;plus=null;shown="";thing=null;
        while(a.trail.size()>1)a.trail.remove(a.trail.size()-1);
        home.refresh();
    }

    /**
     * The card for where the trail ends, over Home: made if it is not up, emptied where it shows something new, and
     * read. Its name is put up at once from the trail, so a card is never blank while the notebook is asked.
     */
    void show() {
        if(!home.showing()||a.trail.size()<2)return;
        if(!isOpen())build();
        MainActivity.Step now=last();
        plus.setVisibility(listing()?View.GONE:View.VISIBLE);
        if(!now.id.equals(shown)) {
            grid.removeAllViews();laid=null;
            head.removeAllViews();
            head.addView(title(now.name),new LinearLayout.LayoutParams(-1,-2));
            thing=null;
        }
        // A collection's card is one of the things open, for the overview; the places are not things.
        if(now.kind==NoteStore.Branch.Kind.COLLECTION)home.overview.remember(Overview.Kind.COLLECTION,now.id);
        home.refresh();
    }

    /** The dimmed grid, the card on it, and in the card its bar, its grid and its +. */
    private void build() {
        shown="";thing=null;
        scrim=new FrameLayout(a);
        scrim.setBackgroundColor(DIM);
        scrim.setContentDescription("Close "+last().name);
        // A tap on the dimmed grid round the card closes it, as a tap outside every box here does.
        scrim.setOnClickListener(v->close());
        LinearLayout body=a.column();
        body.setClickable(true);
        body.setElevation(a.dp(12));
        body.setPadding(a.dp(6),a.dp(4),a.dp(6),0);
        head=new LinearLayout(a);head.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(head,new LinearLayout.LayoutParams(-1,-2));
        FrameLayout inside=new FrameLayout(a);
        scroll=new ScrollView(a);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        grid=new GridLayout(a);grid.setColumnCount(home.columns(width()));
        grid.setPadding(0,a.dp(2),0,a.dp(88));
        scroll.addView(grid,new FrameLayout.LayoutParams(-1,-2));
        inside.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        // Files dragged in from another app and let go on the card are kept with this collection (decision 23) - in the empty
        // cell they were let go in - or with the note or collection in it they were let go on, as on Home.
        body.setOnDragListener((v,event)->!listing()&&home.filesOn(v,event,grid,()->laid,this::id));
        // Laid out again once the card is measured, or its names change size, so its rows fill what is seen of it.
        scroll.addOnLayoutChangeListener((v,l,t,r,b,wl,wt,wr,wb)->home.refit(scroll,laid,this::relay));
        plus=home.plus(this::id);
        inside.addView(plus,home.plusPlace());
        body.addView(inside,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout.LayoutParams place=new FrameLayout.LayoutParams(-1,-1);
        place.setMargins(a.dp(14),a.dp(14),a.dp(14),a.dp(18));
        scrim.addView(body,place);
        card=body;
        paint(Tint.NONE);
        home.desk.addView(scrim,new FrameLayout.LayoutParams(-1,-1));
        if(home.homePlus!=null)home.homePlus.setVisibility(View.GONE);
    }

    /** How dim Home's grid is under the card, and how much dimmer while a carried thing would come up out of it. */
    private static final int DIM=0x59000000,DIMMER=0x8C000000;

    /** The card's width, in pixels, for its columns: the screen, less the margins round the card and inside it. */
    private int width(){return a.getResources().getDisplayMetrics().widthPixels-a.dp(14)*2-a.dp(6)*2;}

    /** The card in its collection's colour, washed as a room is, so the card is the collection you are standing in. */
    private void paint(int tint) {
        if(card==null)return;
        GradientDrawable paper=new GradientDrawable();
        paper.setColor(Tint.over(tint,a.PAPER,a.wash(0.12f,0.72f),a.darkPaper()));
        paper.setCornerRadius(a.dp(24));paper.setStroke(Math.max(1,a.dp(1)),a.LINE);
        card.setBackground(paper);
    }

    /** A colour chosen for the collection while its menu is over the card: the card takes it at once. */
    void painted(String id,int tint){if(isOpen()&&id.equals(shown))paint(tint);}

    /** Home's grid darker while a thing carried out of the card would come up a level there. */
    void lit(boolean on){if(scrim!=null)scrim.setBackgroundColor(on?DIMMER:DIM);}

    // ---- reading and drawing ------------------------------------------------------------------------------------------

    /**
     * What the card needs, read on the worker with the rest of Home: how much of the trail is still there, and for the
     * collection it ends in, its name, its colour, what it is in, and what it holds - or, among the favourites, those
     * the dock has no room for; in the archive or the bin, what waits there, each dressed in its own look.
     */
    void read(List<MainActivity.Step> path,Map<String,Object> got) {
        MainActivity.Step end=path.get(path.size()-1);
        if(end.kind==NoteStore.Branch.Kind.FAVOURITES){got.put("card",a.store.favouritesBeyondDock());return;}
        if(end.kind==NoteStore.Branch.Kind.ARCHIVE||end.kind==NoteStore.Branch.Kind.BIN) {
            List<NoteStore.Branch> waiting=a.store.heldIn(end.kind==NoteStore.Branch.Kind.BIN);
            a.store.dress(waiting);
            got.put("card",waiting);
            return;
        }
        // The trail is remembered across a restart and a trip to a note, and what it names can have been put away since.
        for(int at=1;at<path.size();at++) {
            MainActivity.Step step=path.get(at);
            if(step.kind==NoteStore.Branch.Kind.COLLECTION&&!a.store.stillThere(step.kind,step.id)){got.put("keep",at);return;}
        }
        String in=a.store.collectionOfBook(end.id);
        got.put("card",a.store.contents(end.id));
        got.put("name",a.store.collectionName(end.id));
        got.put("tint",a.store.colourOf(NoteStore.Branch.Kind.COLLECTION,end.id));
        got.put("parent",in==null||in.isEmpty()?Things.HOME:in);
        got.put("parentName",in==null||in.isEmpty()?"Home":a.store.collectionName(in));
        // Its look, for its face before its name (docs/HOME.md, step 4).
        got.put("icon",a.store.iconOf(NoteStore.Branch.Kind.COLLECTION,end.id));
        got.put("image",a.store.imageOf(NoteStore.Branch.Kind.COLLECTION,end.id));
    }

    /** The card drawn from what was read, if the trail is still where it was when it was read. */
    void fill(List<MainActivity.Step> path,Map<String,Object> got) {
        if(!isOpen()||!same(path))return;
        Object keep=got.get("keep");
        if(keep!=null) {
            // Out to the last level that is still there, and said: a card that quietly becomes another is worse.
            int at=(Integer)keep;
            String gone=path.get(at).name;
            while(a.trail.size()>at)a.trail.remove(a.trail.size()-1);
            a.toast("“"+gone+"” was put away");
            if(a.trail.size()>1)show();else close();
            return;
        }
        MainActivity.Step end=last();
        // One of Home's places - the favourites, the archive, the bin - rather than a collection.
        boolean place=Grid.place(end.kind);
        @SuppressWarnings("unchecked") List<NoteStore.Branch> lines=(List<NoteStore.Branch>)got.get("card");
        if(lines==null)lines=new java.util.ArrayList<>();
        NoteStore.Branch face=null;
        if(place){thing=null;parent=Things.HOME;parentName="Home";paint(Tint.NONE);}
        else {
            parent=(String)got.get("parent");parentName=(String)got.get("parentName");
            int tint=(Integer)got.get("tint");
            thing=new NoteStore.Branch(NoteStore.Branch.Kind.COLLECTION,end.id,NoteStore.home(parent)?Sharing.EVERYTHING:parent,
                (String)got.get("name"),"",0,0,true,tint);
            paint(tint);
            // Its face as Home draws it: its picture, its icon, or the mini-grid of as much as it holds.
            face=new NoteStore.Branch(NoteStore.Branch.Kind.COLLECTION,end.id,thing.parent,thing.name,String.valueOf(lines.size()),0,0,true,tint);
            face.icon=got.get("icon")==null?"":(String)got.get("icon");face.image=(byte[])got.get("image");
        }
        bar(end,place,face);
        final int kept=scroll.getScrollY();
        final boolean same=end.id.equals(shown);
        shown=end.id;
        // Each icon in its cell, with the empty cells between (decision 39); in a place, one after the other.
        HomeScreen.Where where=end.kind==NoteStore.Branch.Kind.FAVOURITES?HomeScreen.Where.FAVOURITES:place?HomeScreen.Where.AWAY:HomeScreen.Where.CARD;
        laid=home.lay(grid,lines,java.util.Collections.emptyList(),width(),where,scroll.getHeight(),nothingYet(end.kind));
        // Drawn again because something changed: still where it was scrolled to. Opened on another collection: its top.
        scroll.post(()->{if(scroll!=null)scroll.scrollTo(0,same?kept:0);});
    }

    /** What an empty card says: what to do, or where things come from. */
    private static String nothingYet(NoteStore.Branch.Kind kind) {
        switch(kind) {
            case FAVOURITES: return "Every favourite is in the dock.";
            case ARCHIVE: return "Nothing archived. Archive a note or a collection from its menu, or let go of it on the Archive.";
            case BIN: return "The bin is empty.";
            default: return "Nothing in it yet. Tap + to add a note or a collection.";
        }
    }

    /** The card's grid laid out again from what it was last drawn with, where it has been measured since or its names have grown. */
    private void relay() {
        if(!isOpen()||laid==null||grid==null||scroll==null)return;
        laid=home.lay(grid,laid.lines,java.util.Collections.emptyList(),width(),laid.where,scroll.getHeight(),nothingYet(last().kind));
    }

    /** Whether the trail is the one that was read: the reader may have gone in or out of the card since. */
    private boolean same(List<MainActivity.Step> path) {
        if(path.size()!=a.trail.size())return false;
        for(int at=0;at<path.size();at++)if(!path.get(at).id.equals(a.trail.get(at).id))return false;
        return true;
    }

    /**
     * The card's bar: ‹ and the collection it is in, where it is inside another; its face, tapped to choose its icon, and
     * its name, tapped to rename it - the two together in the middle, as a note's face and title are; its mark; and its ⋮,
     * the collection's own menu. A place has its name and its ⋮, and nothing to rename.
     *
     * @param face the collection dressed in its look, for its face; null in a place
     */
    private void bar(MainActivity.Step end,boolean place,NoteStore.Branch face) {
        head.removeAllViews();
        if(a.trail.size()>2) {
            final String above=a.trail.get(a.trail.size()-2).name;
            TextView back=a.label("‹ "+above,MainActivity.QUIET,a.MUTED);
            back.setSingleLine(true);back.setEllipsize(TextUtils.TruncateAt.END);back.setGravity(Gravity.CENTER_VERTICAL);
            back.setMaxWidth(width()/3);back.setMinHeight(a.dp(48));back.setPadding(a.dp(10),0,a.dp(8),0);
            back.setBackgroundResource(a.borderlessFeedback());
            back.setContentDescription("Back to "+above);
            back.setOnClickListener(v->up());
            head.addView(back,new LinearLayout.LayoutParams(-2,-2));
        } else head.addView(new View(a),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));
        TextView name=title(place?end.name:thing.name);
        if(!place)a.nameable(name,thing);
        // As tall as it needs, and a finger's height at the least: the field it turns into to be renamed is taller.
        if(place||face==null)head.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        else {
            LinearLayout named=new LinearLayout(a);named.setGravity(Gravity.CENTER);
            FrameLayout reach=new FrameLayout(a);
            reach.addView(IconFace.view(a,face,a.dp(26)),new FrameLayout.LayoutParams(a.dp(26),a.dp(26),Gravity.CENTER));
            reach.setBackgroundResource(a.borderlessFeedback());
            reach.setContentDescription("The collection's icon. Tap to change it.");
            final NoteStore.Branch about=thing;
            reach.setOnClickListener(v->a.picker().open(about));
            named.addView(reach,new LinearLayout.LayoutParams(a.dp(40),a.dp(48)));
            named.addView(name,new LinearLayout.LayoutParams(-2,-2));
            head.addView(named,new LinearLayout.LayoutParams(0,-2,1));
        }
        if(!place)head.addView(a.syncMark(thing));
        final NoteStore.Branch about=place?new NoteStore.Branch(end.kind,end.id,Sharing.EVERYTHING,end.name,"",0,0,true):thing;
        head.addView(a.tap("⋮",about.name,22,a.INK,v->a.menuFor(v,about)));
    }

    /** The card's name, as a name at the top of a screen is: in the middle, bold, and on one line. */
    private TextView title(String said) {
        TextView name=a.label(said,MainActivity.READING,a.INK);
        name.setTypeface(name.getTypeface(),android.graphics.Typeface.BOLD);
        name.setGravity(Gravity.CENTER);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMinHeight(a.dp(48));
        return name;
    }
}
