package org.mininotes.android;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.*;

/**
 * Whether a thing has got to where it is going: the phone's sync mark ({@code Mark}), drawn again for the PC
 * with the same geometry. One ring, the same stroke every time, with a different thing inside it; which of the
 * six is {@link SyncMark}'s to say, shared with the phone:
 *
 * <ul><li><b>Here</b> - an empty ring, grey: nothing leaves this PC.
 * <li><b>Waiting</b> - the ring filled amber, an arrow going up in it. Clicked, it sends.
 * <li><b>Sent</b> - three dots, amber: gone, and not yet said to be had.
 * <li><b>Gone</b> - a tick, green: everybody it reaches has what it says now.
 * <li><b>Paused</b> - two bars, grey: this PC has stopped taking it in.
 * <li><b>Stuck</b> - the ring filled red, an exclamation mark in it: a device has not been heard from for days.</ul>
 *
 * It says its state by being seen, not in a box under the pointer; clicked, it does what the phone's does.
 */
final class DesktopMark implements Icon {
    /** The mark a thing on the shelves wears, by the phone's rule. */
    static SyncMark of(NoteStore.Branch b){return b.mark();}

    /**
     * The mark of every collection and note, however deep, read level by level as the phone reads its shelves
     * ({@link NoteStore#inside}): whether anything under it reaches anybody, is owed, or is not taken in here.
     * On the disk thread.
     */
    static Map<String,SyncMark> read(NoteStore store) {
        Map<String,SyncMark> marks=new HashMap<>();
        walk(store,store.inside(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING).holds,marks,new HashSet<>());
        return marks;
    }

    /** One level's lines marked, and each collection's inside after it: the tree walked to any depth, never round a loop. */
    private static void walk(NoteStore store,List<NoteStore.Branch> lines,Map<String,SyncMark> marks,Set<String> seen) {
        for(NoteStore.Branch one:lines) {
            if(one.kind==NoteStore.Branch.Kind.PAGE){marks.put(one.id,of(one));continue;}
            if(!DesktopMoving.shelf(one.kind)||!seen.add(one.id))continue;
            marks.put(one.id,of(one));
            walk(store,store.inside(NoteStore.Branch.Kind.COLLECTION,one.id).holds,marks,seen);
        }
    }

    /** Its colour: amber and red shared with the phone, the PC's own grey and green. The PC has light paper only. */
    static Color ink(SyncMark what){return new Color(what.colour(false,DesktopUi.QUIET.getRGB(),DesktopUi.ACCENT.getRGB()),true);}

    /** What it means, for a screen reader: the phone's words, on this PC. */
    static String said(SyncMark what){return what.said("this PC");}

    final SyncMark what;private final int side;private final Color ink;private final boolean disc;
    /**
     * @param disc a round of paper behind it, so it reads the same over a coloured card, line or page as over
     *             plain paper
     */
    DesktopMark(SyncMark what,int side,Color ink,boolean disc){this.what=what;this.side=side;this.ink=ink;this.disc=disc;}
    DesktopMark(SyncMark what,int side,boolean disc){this(what,side,ink(what),disc);}

    public int getIconWidth(){return side;}
    public int getIconHeight(){return side;}

    public void paintIcon(Component c,Graphics g0,int x,int y) {
        Graphics2D g=(Graphics2D)g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
        float across=side,cx=x+across/2f,cy=y+across/2f,r=across*0.42f;
        // The arrow is cut out in whatever is behind: the disc, or the paper the mark sits on.
        Color hole=disc?DesktopUi.PAPER:c==null?DesktopUi.PAPER:DesktopUi.ground(c);
        if(disc){g.setColor(DesktopUi.PAPER);g.fill(new Ellipse2D.Float(x,y,across,across));}
        // One stroke for the whole mark: a ring and a tick of different weights read as two drawings.
        float stroke=Math.max(1.5f,across*0.09f);
        g.setStroke(new BasicStroke(stroke,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(ink);
        if(what.filled())g.fill(new Ellipse2D.Float(cx-r,cy-r,2*r,2*r));
        else {float ring=r-stroke/2f;g.draw(new Ellipse2D.Float(cx-ring,cy-ring,2*ring,2*ring));}
        Path2D.Float path=new Path2D.Float();
        switch(what) {
            case HERE -> {}
            case PAUSED -> {
                // Two bars, the sign every player uses for the same thing.
                float h=r*0.42f,apart=r*0.26f;
                path.moveTo(cx-apart,cy-h);path.lineTo(cx-apart,cy+h);
                path.moveTo(cx+apart,cy-h);path.lineTo(cx+apart,cy+h);
            }
            case GONE -> {
                // A tick, sized off the ring so it never touches it.
                float w=r*0.92f;
                path.moveTo(cx-w*0.55f,cy+w*0.04f);path.lineTo(cx-w*0.14f,cy+w*0.44f);path.lineTo(cx+w*0.58f,cy-w*0.42f);
            }
            case SENT -> {
                // Three dots, the sign for something still happening, each a round the width of the stroke.
                float apart=r*0.42f,dot=stroke*0.75f;
                for(int i=-1;i<=1;i++)g.fill(new Ellipse2D.Float(cx+i*apart-dot,cy-dot,2*dot,2*dot));
            }
            case STUCK -> {
                // An exclamation mark, cut out of the filled ring as the arrow is.
                g.setColor(hole);float h=r*0.86f,dot=stroke*0.7f;
                path.moveTo(cx,cy-h*0.62f);path.lineTo(cx,cy+h*0.12f);
                g.fill(new Ellipse2D.Float(cx-dot,cy+h*0.56f-dot,2*dot,2*dot));
            }
            case WAITING -> {
                // An arrow going up, drawn in the paper so it reads out of the filled ring.
                g.setColor(hole);float h=r*0.86f;
                path.moveTo(cx,cy+h*0.62f);path.lineTo(cx,cy-h*0.62f);
                path.moveTo(cx-h*0.46f,cy-h*0.16f);path.lineTo(cx,cy-h*0.64f);path.lineTo(cx+h*0.46f,cy-h*0.16f);
            }
        }
        g.draw(path);
        g.dispose();
    }

    /** The mark as something to click: the hand over it, its meaning for a screen reader, and no box under the pointer. */
    static JLabel button(SyncMark what,int side,boolean disc,Runnable clicked) {
        JLabel mark=new JLabel(new DesktopMark(what,side,disc));
        mark.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        mark.getAccessibleContext().setAccessibleName(said(what));
        mark.addMouseListener(new java.awt.event.MouseAdapter(){
            @Override public void mouseClicked(java.awt.event.MouseEvent e){if(SwingUtilities.isLeftMouseButton(e))clicked.run();}
        });
        return mark;
    }

    // ---- the people on the line under a note's title -----------------------------------------------------------

    /**
     * One device a note reaches: the round every person wears in this app ({@link DesktopUi#avatar}), smaller,
     * with a dot on its edge in the colour of where they stand. Clicked, a small box under it says who and where,
     * in words; nothing shows under the pointer.
     */
    static JComponent person(SyncStatus.Person who,int side){return person(who,side,null);}

    /**
     * @param notLinked what a click on somebody this PC is not linked with does; null for the small box every round opens
     */
    static JComponent person(SyncStatus.Person who,int side,java.util.function.Consumer<SyncStatus.Person> notLinked) {
        return person(who,side,notLinked,null);
    }

    /**
     * @param linked what a click on somebody linked does, with the round it was on; null for the small box alone
     */
    static JComponent person(SyncStatus.Person who,int side,java.util.function.Consumer<SyncStatus.Person> notLinked,
                             java.util.function.BiConsumer<SyncStatus.Person,Component> linked) {
        return person(who,side,notLinked,linked,null);
    }

    /**
     * @param ink the colour their writing is drawn in on the page (see Writers), read as the round is painted: their
     *            initial in it, on a wash of it, so the round says whose the coloured words are; null or none for the round
     *            as it always was
     */
    static JComponent person(SyncStatus.Person who,int side,java.util.function.Consumer<SyncStatus.Person> notLinked,
                             java.util.function.BiConsumer<SyncStatus.Person,Component> linked,java.util.function.ToIntFunction<SyncStatus.Person> ink) {
        // A label, not a bare component: a bare one has no accessible context for a screen reader to name.
        JLabel round=new JLabel(new Icon(){
            public int getIconWidth(){return side+2;}
            public int getIconHeight(){return side+2;}
            public void paintIcon(Component c,Graphics g0,int x,int y) {
                Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                if(!who.linked()){paintNotLinked(g,who.name(),x,y,side);g.dispose();return;}
                int colour=ink==null?Tint.NONE:ink.applyAsInt(who);
                if(Tint.known(colour)) {
                    Color fill=new Color(Tint.over(colour,DesktopUi.CARD.getRGB(),0.22f,false));
                    DesktopUi.paintAvatar(g,who.name(),x,y,side,fill,new Color(Writers.ink(colour,fill.getRGB())));
                }
                else DesktopUi.paintAvatar(g,who.name(),x,y,side);
                // The dot, ringed in paper so it reads over the round and over a washed page alike.
                float dot=Math.max(7f,side*0.36f),at=side+2-dot;
                g.setColor(DesktopUi.PAPER);g.fill(new Ellipse2D.Float(x+at-1.5f,y+at-1.5f,dot+3f,dot+3f));
                g.setColor(ink(who.mark()));g.fill(new Ellipse2D.Float(x+at,y+at,dot,dot));g.dispose();
            }
        });
        // Room on the right, so the rounds on a line stand apart.
        round.setBorder(BorderFactory.createEmptyBorder(0,0,0,4));
        round.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        round.getAccessibleContext().setAccessibleName(who.linked()?who.called()+": "+who.standing()
            :who.notLinked(Post.here()));
        round.addMouseListener(new java.awt.event.MouseAdapter(){
            @Override public void mouseClicked(java.awt.event.MouseEvent e){
                if(!SwingUtilities.isLeftMouseButton(e))return;
                if(!who.linked()&&notLinked!=null)notLinked.accept(who);
                else if(who.linked()&&linked!=null)linked.accept(who,round);
                else told(who,round).show(round,0,round.getHeight()+4);
            }
        });
        return round;
    }

    /**
     * Somebody only named in the list that came with a note: not a person this PC can send to, so not drawn as one.
     * Their initial in grey inside a dashed ring, and a small broken link on its edge where the others wear the
     * colour of where they stand - which for them is nowhere.
     */
    static void paintNotLinked(Graphics2D g,String name,int x,int y,int side) {
        String letter=name==null||name.isBlank()?"?":new String(Character.toChars(name.trim().codePointAt(0))).toUpperCase(java.util.Locale.ROOT);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        float stroke=Math.max(1.3f,side*0.06f),in=stroke/2f;
        g.setColor(DesktopUi.PAPER);g.fill(new Ellipse2D.Float(x,y,side,side));
        g.setColor(DesktopUi.QUIET);
        g.setStroke(new BasicStroke(stroke,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND,1f,new float[]{side*0.12f,side*0.09f},0f));
        g.draw(new Ellipse2D.Float(x+in,y+in,side-stroke,side-stroke));
        // The letter a little up and to the left, so the badge on the edge leaves it whole.
        g.setFont(DesktopUi.BODY.deriveFont(Font.BOLD,side*0.40f));FontMetrics m=g.getFontMetrics();float nudge=side*0.05f;
        g.drawString(letter,x+(side-m.stringWidth(letter))/2f-nudge,y+(side-m.getHeight())/2f+m.getAscent()-nudge);
        // The badge: two links of a chain pulled apart, on a round of paper so it reads over the ring.
        float badge=Math.max(9f,side*0.42f),bx=x+side+2-badge,by=y+side+2-badge,c=badge/2f;
        g.setColor(DesktopUi.PAPER);g.fill(new Ellipse2D.Float(bx-1f,by-1f,badge+2f,badge+2f));
        g.setColor(DesktopUi.QUIET);g.setStroke(new BasicStroke(Math.max(1.2f,badge*0.11f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Float(bx,by,badge,badge));
        float lw=badge*0.30f,lh=badge*0.20f;
        java.awt.geom.AffineTransform was=g.getTransform();
        g.rotate(-Math.PI/4,bx+c,by+c);
        g.draw(new java.awt.geom.RoundRectangle2D.Float(bx+c-lw-badge*0.06f,by+c-lh/2f,lw,lh,lh,lh));
        g.draw(new java.awt.geom.RoundRectangle2D.Float(bx+c+badge*0.06f,by+c-lh/2f,lw,lh,lh,lh));
        g.setTransform(was);
    }

    /** The small box a round opens: their name, and where they stand. It closes with a click anywhere else. */
    static JPopupMenu told(SyncStatus.Person who,Component near){return told(who,near,null);}

    /** The same, with something more under where they stand: the colour their writing is drawn in here. */
    static JPopupMenu told(SyncStatus.Person who,Component near,JComponent more) {
        JPanel words=DesktopUi.column();words.setBorder(BorderFactory.createEmptyBorder(10,14,10,14));
        JPanel name=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));name.setOpaque(false);
        JLabel mark=new JLabel(new DesktopMark(who.mark(),16,false));mark.setBorder(BorderFactory.createEmptyBorder(0,0,0,8));
        name.add(mark);name.add(DesktopUi.body(who.called()));
        DesktopUi.add(words,name);DesktopUi.gap(words,4);DesktopUi.add(words,DesktopUi.note(who.standing(),260,DesktopUi.QUIET,DesktopUi.BODY.deriveFont(13f)));
        if(more!=null){DesktopUi.gap(words,10);DesktopUi.add(words,more);}
        JPopupMenu box=new JPopupMenu();box.setBackground(DesktopUi.CARD);box.add(words);
        box.getAccessibleContext().setAccessibleName(who.called()+": "+who.standing());
        return box;
    }

    /** The rounds, in the order the note was given to them; none for a note that reaches nobody. */
    static void people(JPanel line,List<SyncStatus.Person> who,int side){people(line,who,side,null);}
    static void people(JPanel line,List<SyncStatus.Person> who,int side,java.util.function.Consumer<SyncStatus.Person> notLinked) {
        people(line,who,side,notLinked,null);
    }
    static void people(JPanel line,List<SyncStatus.Person> who,int side,java.util.function.Consumer<SyncStatus.Person> notLinked,
                       java.util.function.BiConsumer<SyncStatus.Person,Component> linked) {
        people(line,who,side,notLinked,linked,null);
    }
    static void people(JPanel line,List<SyncStatus.Person> who,int side,java.util.function.Consumer<SyncStatus.Person> notLinked,
                       java.util.function.BiConsumer<SyncStatus.Person,Component> linked,java.util.function.ToIntFunction<SyncStatus.Person> ink) {
        line.removeAll();
        for(SyncStatus.Person one:who)line.add(person(one,side,notLinked,linked,ink));
        line.revalidate();line.repaint();
    }
}
