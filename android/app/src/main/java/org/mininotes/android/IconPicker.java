// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * The icon a note or a collection wears, chosen (docs/HOME.md, step 4, decisions 32 to 34): the thing's menu has
 * <i>Icon…</i> right after its colour, and under it <i>Remove the picture</i> while it wears one. The box is one box, the
 * same as the PC's: the colours along the top, as the menu has them, the current one ringed, and a tap on one colours the
 * thing at once; a field to search the set; <i>Default</i> first - the note glyph for a note, the mini-grid for a
 * collection; then the set, in Lucide's order or as the search finds it, the one worn now ringed; and <i>Choose a
 * picture…</i> at the end. Tapping an icon is the choice: the box has no button, and closes.
 *
 * <p>A picture is chosen with the phone's own picker, read small, cut square from the middle, and encoded as WebP -
 * smaller and smaller until it fits what may travel (see {@link Thumb}) - with the strip at the foot saying so while it
 * is made and kept, and why, if it cannot be. A thing this phone may only read has no <i>Icon…</i>: what a reader
 * changes would never be taken anywhere.
 */
final class IconPicker {
    private final MainActivity a;
    /** Where a picture being chosen is to go, kept while the phone's picker is open: the app can be made again meanwhile. */
    private static final String PICTURING="picturing";

    IconPicker(MainActivity a){this.a=a;}

    // ---- in the thing's menu ------------------------------------------------------------------------------------------

    /**
     * The look's rows in a thing's menu, after its colour (decision 33): <i>Icon…</i> for a note or a collection this phone
     * may change, and <i>Remove the picture</i> while it wears one. Both are asked of the notebook, greyed until it answers,
     * as the menu's other asked-for rows are.
     */
    void rows(final MainActivity.Sheet sheet,final NoteStore.Branch thing) {
        if(thing==null||!wears(thing.kind))return;
        final TextView icon=sheet.dimRow("Icon…"),picture=sheet.dimRow("Remove the picture");
        // A note being written in is written down first, so the notebook is asked about the note as it is.
        a.lookChanging(thing.kind,thing.id);
        a.background.submit(()->new boolean[]{kept(a.store,thing.kind,thing.id)&&changes(a.store,thing.kind,thing.id),
                a.store.imageOf(thing.kind,thing.id)!=null},got->{
            if(got[0])sheet.wakeRow(icon,"Icon…",()->open(thing));else sheet.body().removeView(icon);
            if(got[0]&&got[1])sheet.wakeRow(picture,"Remove the picture",()->removePicture(thing));else sheet.body().removeView(picture);
        },e->{sheet.body().removeView(icon);sheet.body().removeView(picture);});
    }

    /** Whether a kind of thing wears an icon of its own: a note, or a collection. */
    static boolean wears(NoteStore.Branch.Kind kind) {
        return kind==NoteStore.Branch.Kind.PAGE||kind==NoteStore.Branch.Kind.COLLECTION||kind==NoteStore.Branch.Kind.BOOK;
    }

    /**
     * Whether the notebook has the thing at all: a blank note nobody has written on yet is not kept, and has nothing to
     * wear a look on until it is. Blocking.
     */
    static boolean kept(NoteStore store,NoteStore.Branch.Kind kind,String id) {
        return kind!=NoteStore.Branch.Kind.PAGE||store.get(id)!=null;
    }

    /** Whether this phone may change a thing's look: not a note it only reads, nor a collection it may not write in. Blocking. */
    static boolean changes(NoteStore store,NoteStore.Branch.Kind kind,String id) {
        if(kind==NoteStore.Branch.Kind.PAGE)return !Boolean.TRUE.equals(store.readOnlyHere(id)[0]);
        return !Boolean.FALSE.equals(store.mayWriteIn(NoteStore.Branch.Kind.COLLECTION,id));
    }

    /** The words a refusal came with, where they were written to be shown; otherwise the words given. */
    private static String why(Exception e,String otherwise) {
        return (e instanceof IllegalArgumentException||e instanceof IllegalStateException)&&e.getMessage()!=null?e.getMessage():otherwise;
    }

    // ---- the box ---------------------------------------------------------------------------------------------------------

    /** The box for one thing, with its colour, its icon and whether it wears a picture read first, so it opens as it is. */
    void open(final NoteStore.Branch thing) {
        if(thing==null||!wears(thing.kind))return;
        a.lookChanging(thing.kind,thing.id);
        a.background.submit(()->{
            // The set read here, off the screen's thread, the first time it is wanted.
            Icons.all();
            return new Object[]{a.store.colourOf(thing.kind,thing.id),a.store.iconOf(thing.kind,thing.id),a.store.imageOf(thing.kind,thing.id)!=null,
                changes(a.store,thing.kind,thing.id),kept(a.store,thing.kind,thing.id)};
        },got->{
            if(!(Boolean)got[4]){a.toast("Write in it first: a blank note is not kept");return;}
            if(!(Boolean)got[3]){a.alert(thing.kind==NoteStore.Branch.Kind.PAGE?"That note is read only here.":"That collection is read only here.");return;}
            show(thing,(Integer)got[0],(String)got[1],(Boolean)got[2]);
        },e->a.alert(MainActivity.READ_FAILED));
    }

    /**
     * The box, as the PC's is (DesktopIconPicker): titled with the thing's name; the colours; the search; the grid, Default
     * first while nothing is typed and what the thing wears ringed; and Choose a picture… under a line at the end.
     *
     * @param pictured whether it wears a picture now: then nothing in the grid is ringed, since the picture is over them all
     */
    private void show(final NoteStore.Branch thing,int colour,String icon,boolean pictured) {
        final int[] tint={colour};final boolean[] painted={false};
        LinearLayout body=a.inside();

        // The colours along the top, as the menu has them: the one it has ringed, and a tap colours it at once.
        final LinearLayout rounds=new LinearLayout(a);
        rounds.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(rounds,new LinearLayout.LayoutParams(-1,-2));

        final EditText words=a.field("Search icons",40);
        body.addView(words);

        // The grid: Default and the set, building only the cells on the screen - 1,857 are never all made at once.
        final Found set=new Found(thing,Icons.known(icon)?icon:"",pictured,tint);
        final GridView grid=new GridView(a);
        grid.setNumColumns(GridView.AUTO_FIT);grid.setColumnWidth(a.dp(52));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(a.touchFeedback());grid.setDrawSelectorOnTop(true);grid.setVerticalScrollBarEnabled(false);
        grid.setAdapter(set);
        final AlertDialog[] box={null};
        grid.setOnItemClickListener((parent,cell,at,row)->{box[0].dismiss();choose(thing,set.names.get(at));});
        final TextView none=a.under("No icon is called that.");
        none.setGravity(Gravity.CENTER);none.setVisibility(View.GONE);
        FrameLayout among=new FrameLayout(a);
        among.addView(grid,new FrameLayout.LayoutParams(-1,-1));
        among.addView(none,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        // As tall as the screen allows with a strip left to tap outside, as every box keeps one; never too short to scroll.
        int high=a.getResources().getDisplayMetrics().heightPixels;
        LinearLayout.LayoutParams gridPlace=new LinearLayout.LayoutParams(-1,Math.max(a.dp(160),high-a.dp(450)));
        gridPlace.setMargins(0,a.dp(8),0,a.dp(4));
        body.addView(among,gridPlace);

        // And at the end, under a line, a picture of its own: a line of the box, not a second button beside the grid.
        View rule=new View(a);rule.setBackgroundColor(a.LINE);
        body.addView(rule,new LinearLayout.LayoutParams(-1,Math.max(1,a.dp(1))));
        LinearLayout pictureLine=new LinearLayout(a);
        pictureLine.setGravity(Gravity.CENTER_VERTICAL);
        pictureLine.setMinimumHeight(a.dp(52));pictureLine.setPadding(0,a.dp(6),0,a.dp(2));
        View pictureFace=new View(a);
        pictureFace.setBackground(IconFace.glyph(a,a.PAPER,a.LINE,Math.max(1,a.dp(1)),"image-plus",a.INK));
        pictureFace.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams facePlace=new LinearLayout.LayoutParams(a.dp(32),a.dp(32));facePlace.setMargins(0,0,a.dp(14),0);
        pictureLine.addView(pictureFace,facePlace);
        pictureLine.addView(a.label("Choose a picture…",MainActivity.READING,a.INK),new LinearLayout.LayoutParams(0,-2,1));
        pictureLine.setBackgroundResource(a.touchFeedback());
        pictureLine.setContentDescription("Choose a picture");
        pictureLine.setOnClickListener(v->{box[0].dismiss();choosePicture(thing);});
        body.addView(pictureLine,new LinearLayout.LayoutParams(-1,-2));

        final Runnable paint=()->{
            for(int at=0;at<rounds.getChildCount();at++) {
                GradientDrawable blob=new GradientDrawable();
                blob.setShape(GradientDrawable.OVAL);
                blob.setColor(Tint.known(at)?Tint.of(at,a.darkPaper()):a.PAPER);
                boolean ringed=at==tint[0]||at==Tint.NONE&&!Tint.known(tint[0]);
                blob.setStroke(a.dp(ringed?3:1),ringed?a.INK:a.LINE);
                View reach=rounds.getChildAt(at);
                ((FrameLayout)reach).getChildAt(0).setBackground(blob);
                reach.setContentDescription(Tint.NAMES[at]+" for "+thing.name+(ringed?", chosen":""));
            }
            set.notifyDataSetChanged();
        };
        for(int round=0;round<Tint.count();round++) {
            final int which=round;
            FrameLayout reach=new FrameLayout(a);
            reach.setPadding(a.dp(2),a.dp(9),a.dp(2),a.dp(9));
            reach.addView(new View(a),new FrameLayout.LayoutParams(a.dp(20),a.dp(20),Gravity.CENTER));
            reach.setOnClickListener(v->{
                if(tint[0]==which)return;
                tint[0]=which;painted[0]=true;
                a.paintThing(thing,which);
                paint.run();
            });
            rounds.addView(reach,new LinearLayout.LayoutParams(0,-2,1));
        }
        paint.run();

        // Found as it is typed, a moment after the last letter, so a quick typist is not searched for once a letter.
        final Runnable find=()->{
            set.names=Looks.shown(words.getText().toString());
            set.notifyDataSetChanged();
            boolean empty=set.names.isEmpty();
            none.setVisibility(empty?View.VISIBLE:View.GONE);
            grid.setVisibility(empty?View.INVISIBLE:View.VISIBLE);
            if(!empty)grid.setSelection(0);
        };
        words.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){}
            public void afterTextChanged(android.text.Editable e){words.removeCallbacks(find);words.postDelayed(find,150);}
        });

        String name=thing.name==null||thing.name.trim().isEmpty()?(thing.kind==NoteStore.Branch.Kind.PAGE?"this note":"this collection"):thing.name.trim();
        if(name.length()>32)name=name.substring(0,31).trim()+"…";
        box[0]=a.new Box().setTitle("Icon for “"+name+"”").setView(body).create();
        android.view.Window window=box[0].getWindow();
        if(window!=null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            // The keyboard only when the field is tapped: most choosing is done by looking, not by typing.
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
        // A colour chosen here shows wherever the thing is drawn, once the box is put away.
        box[0].setOnDismissListener(d->{words.removeCallbacks(find);if(painted[0])a.refresh();});
        box[0].show();
        // Opened where what it wears is, so it is seen ringed without being looked for.
        int at=set.worn.isEmpty()?-1:set.names.indexOf(set.worn);
        if(at>0)grid.setSelection(at);
    }

    /**
     * The grid's cells: Default, as the thing looks with nothing of its own chosen - the note glyph, or a full mini-grid -
     * then each icon of the set as a glyph in the thing's ink; what it wears now ringed, as its colour is.
     */
    private final class Found extends BaseAdapter {
        List<String> names=Looks.shown("");
        private final NoteStore.Branch thing;
        /** What it wears now - an icon of the set, or Default ("") - ringed; nothing where a picture is worn over them all. */
        final String worn;
        private final boolean pictured;
        private final int[] tint;
        Found(NoteStore.Branch thing,String worn,boolean pictured,int[] tint){this.thing=thing;this.worn=worn;this.pictured=pictured;this.tint=tint;}
        @Override public int getCount(){return names.size();}
        @Override public Object getItem(int at){return names.get(at);}
        @Override public long getItemId(int at){return at;}
        @Override public View getView(int at,View made,ViewGroup parent) {
            View cell=made!=null?made:new View(a);
            if(made==null)cell.setLayoutParams(new AbsListView.LayoutParams(-1,a.dp(52)));
            String name=names.get(at);
            boolean ringed=!pictured&&name.equals(worn);
            if(Looks.DEFAULT.equals(name)) {
                boolean note=thing.kind==NoteStore.Branch.Kind.PAGE;
                // A collection's default drawn full, so it reads as the mini-grid it is and not as an empty square.
                NoteStore.Branch plain=new NoteStore.Branch(thing.kind,thing.id,thing.parent,thing.name,
                    note||a.countIn(thing)>0?thing.detail:"4",0,0,!note,tint[0]);
                cell.setBackground(new LayerDrawable(new Drawable[]{IconFace.bare(a,null,0,ringed?a.INK:0),
                    new InsetDrawable(IconFace.of(a,plain),a.dp(9))}));
                cell.setContentDescription((note?"Default: the note icon":"Default: what is inside it")+(ringed?", chosen":""));
            } else {
                cell.setBackground(IconFace.bare(a,name,Looks.ink(tint[0],a.darkPaper(),a.INK),ringed?a.INK:0));
                cell.setContentDescription(Looks.spoken(name)+(ringed?", chosen":""));
            }
            return cell;
        }
    }

    // ---- choosing ----------------------------------------------------------------------------------------------------

    /** An icon from the set, or the default (""), worn from now on: any picture comes off with it (decision 34). */
    private void choose(final NoteStore.Branch thing,final String icon) {
        a.lookChanging(thing.kind,thing.id);
        a.background.submit(()->{a.store.setIcon(thing.kind,thing.id,icon);return null;},
            done->a.looked(thing.kind,thing.id),
            e->a.alert(why(e,"Could not change that icon. Nothing was changed.")));
    }

    /** The picture taken off: the icon under it is worn again. */
    void removePicture(final NoteStore.Branch thing) {
        a.lookChanging(thing.kind,thing.id);
        a.background.submit(()->{a.store.setImage(thing.kind,thing.id,null);return null;},
            done->{a.toast("Picture removed");a.looked(thing.kind,thing.id);},
            e->a.alert(why(e,"Could not remove the picture. Nothing was changed.")));
    }

    /** Choose a picture…: the phone's own picker for pictures, with where the one chosen goes kept until it answers. */
    private void choosePicture(NoteStore.Branch thing) {
        a.getSharedPreferences("settings",MainActivity.MODE_PRIVATE).edit()
            .putString(PICTURING,thing.kind.name()+"\n"+thing.id+"\n"+thing.name).apply();
        Intent pick=new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        try{a.startActivityForResult(pick,MainActivity.PICTURE);}
        catch(ActivityNotFoundException none){a.alert("Nothing on this phone offers pictures to choose from.");}
    }

    /**
     * A picture chosen: made small and kept with the thing it was chosen for, on the notebook's worker, with the strip at
     * the foot saying what is happening - and, if it cannot be done, a box saying why and nothing changed.
     */
    void pictured(final Uri picked) {
        String kept=a.getSharedPreferences("settings",MainActivity.MODE_PRIVATE).getString(PICTURING,"");
        a.getSharedPreferences("settings",MainActivity.MODE_PRIVATE).edit().remove(PICTURING).apply();
        String[] part=kept==null?new String[0]:kept.split("\n",3);
        if(picked==null||part.length<3)return;
        final NoteStore.Branch.Kind kind;
        try{kind=NoteStore.Branch.Kind.valueOf(part[0]);}catch(IllegalArgumentException unknown){return;}
        final String id=part[1],name=part[2];
        a.lookChanging(kind,id);
        final int job=a.busy("Making the picture small enough to travel…");
        final ContentResolver from=a.getContentResolver();
        a.background.submit(()->{
            byte[] thumb=thumbnail(from,picked);
            a.busySay(job,name.isEmpty()?"Keeping the picture…":"Keeping the picture with “"+name+"”…");
            a.store.setImage(kind,id,thumb);
            return null;
        },done->{a.busyDone(job,"Picture set");a.looked(kind,id);},
        e->{a.busyDone(job,null);a.alert(why(e,"Could not use that picture. Nothing was changed."));});
    }

    /** What is said of a file that is not a picture this phone can read. */
    private static final String NOT_A_PICTURE="That file is not a picture Mininotes can read. Nothing was changed.";

    /**
     * A picture read from where the phone's picker put it: read at a fraction of its size (see {@link Looks#sample}),
     * turned the way the camera said it was held, cut square from the middle, and encoded as WebP by
     * {@link Thumb#tries} until one fits (see {@link Looks#firstFit}). Blocking: the worker calls it.
     */
    static byte[] thumbnail(ContentResolver from,Uri picked) throws IOException {
        BitmapFactory.Options size=new BitmapFactory.Options();size.inJustDecodeBounds=true;
        try(InputStream in=from.openInputStream(picked)) {
            if(in==null)throw new IllegalArgumentException(NOT_A_PICTURE);
            BitmapFactory.decodeStream(in,null,size);
        }
        if(size.outWidth<=0||size.outHeight<=0)throw new IllegalArgumentException(NOT_A_PICTURE);
        Matrix turn=turned(from,picked);
        BitmapFactory.Options read=new BitmapFactory.Options();
        read.inSampleSize=Looks.sample(size.outWidth,size.outHeight,Thumb.SIDE);
        final Bitmap whole;
        try(InputStream in=from.openInputStream(picked)) {
            whole=in==null?null:BitmapFactory.decodeStream(in,null,read);
        }
        if(whole==null)throw new IllegalArgumentException(NOT_A_PICTURE);
        try {
            final int[] cut=Thumb.square(whole.getWidth(),whole.getHeight());
            if(cut==null)throw new IllegalArgumentException(NOT_A_PICTURE);
            byte[] made=Looks.firstFit(Thumb.tries(cut[2]),one->encode(whole,cut,turn,one));
            if(made==null)throw new IllegalArgumentException("That picture could not be made small enough to travel. Nothing was changed.");
            return made;
        } finally {whole.recycle();}
    }

    /** One way of encoding the square: scaled to the try's side, turned upright, and written as WebP at its quality. */
    private static byte[] encode(Bitmap whole,int[] cut,Matrix turn,Thumb.Try one) {
        Matrix fit=new Matrix();
        float scale=one.side/(float)cut[2];
        fit.setScale(scale,scale);
        fit.postConcat(turn);
        Bitmap small=Bitmap.createBitmap(whole,cut[0],cut[1],cut[2],cut[2],fit,true);
        try {
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            small.compress(webp(),one.quality,out);
            return out.toByteArray();
        } finally {if(small!=whole)small.recycle();}
    }

    /** WebP as each version of the platform names it: lossy by name from Android 11, and before it the one WebP there was. */
    @SuppressWarnings("deprecation")
    private static Bitmap.CompressFormat webp() {
        return android.os.Build.VERSION.SDK_INT>=30?Bitmap.CompressFormat.WEBP_LOSSY:Bitmap.CompressFormat.WEBP;
    }

    /**
     * Which way up the camera said the picture was held, as the turn that puts it upright - a photo taken with the phone
     * on its side is stored on its side, with a word saying so. None where the picture says nothing, or cannot be asked.
     */
    // The platform's own reader of that word: the app takes no AndroidX for what it draws, and the copy AndroidX offers is
    // the fix for phones older than any this app runs on (Android 9 and later), whose own reader already has it.
    @android.annotation.SuppressLint("ExifInterface")
    private static Matrix turned(ContentResolver from,Uri picked) {
        Matrix turn=new Matrix();
        int said=android.media.ExifInterface.ORIENTATION_NORMAL;
        try(InputStream in=from.openInputStream(picked)) {
            if(in!=null)said=new android.media.ExifInterface(in).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL);
        } catch(IOException|RuntimeException unasked){return turn;}
        switch(said) {
            case android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL: turn.setScale(-1,1);break;
            case android.media.ExifInterface.ORIENTATION_ROTATE_180: turn.setRotate(180);break;
            case android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL: turn.setRotate(180);turn.postScale(-1,1);break;
            case android.media.ExifInterface.ORIENTATION_TRANSPOSE: turn.setRotate(90);turn.postScale(-1,1);break;
            case android.media.ExifInterface.ORIENTATION_ROTATE_90: turn.setRotate(90);break;
            case android.media.ExifInterface.ORIENTATION_TRANSVERSE: turn.setRotate(-90);turn.postScale(-1,1);break;
            case android.media.ExifInterface.ORIENTATION_ROTATE_270: turn.setRotate(-90);break;
            default:
        }
        return turn;
    }
}
