package org.mininotes.android;

import java.awt.*;
import java.awt.dnd.DragSource;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.tree.*;

/**
 * Taking hold of a note or a collection and putting it somewhere else, in the tree - the phone's drag, with one
 * meaning for each place it is let go (Home's icons are carried by DesktopHome, by the phone's own rules in Grid):
 *
 * <ul><li>between two things of its own kind: it takes that place among them, the same order the phone keeps
 * ({@link NoteStore#order}); among the things of another collection, it moves in there, at that place;
 * <li>onto a collection: it moves inside, at the top, as Move to… does. A note goes into any collection, a
 * collection onto the top or into any collection that is not itself or inside it (docs/HOME.md, decision 14). In
 * the tree a note is let go anywhere on a collection's line, a collection on the middle of it: the line's top and
 * bottom edges are between it and the lines beside it.</ul>
 *
 * A green line shows where it would land, or a green edge round what it would go into; the bar says the same in
 * words. Esc puts it down where it was. Moving into something shared asks first, as the menu's Move does.
 *
 * <p>This is the pointer's own dragging, not Swing's drag and drop: files from Explorer come through the
 * window's transfer handlers ({@link DesktopDrops}), and nothing here touches them.
 */
final class DesktopMoving {
    /**
     * Where a dragged thing would land. {@code into} is the collection it would move into - Home for the
     * top - or null when it stays where it is and only changes place. {@code order} is its level as it would be, or
     * null for the top of {@code into}; {@code before} is the level as it is, for Undo.
     */
    record Landing(NoteStore.Branch into,String parent,int slot,List<String> order,List<String> before){}

    /** One line of the tree as drawn: what it is, and where. */
    record Line(NoteStore.Branch branch,int top,int height,int left){}

    // ---- where it lands: geometry and lists only, so it is tested without a window -------------------------

    /** Whether things of this kind can be dragged at all: the favourites, the bin and the rest are places, not things. */
    static boolean movable(NoteStore.Branch one) {
        return one!=null&&(shelf(one.kind)||one.kind==NoteStore.Branch.Kind.PAGE);
    }

    /** A collection, at any depth. The notebook calls every one COLLECTION now; a line made from an older one may say BOOK. */
    static boolean shelf(NoteStore.Branch.Kind kind){return kind==NoteStore.Branch.Kind.COLLECTION||kind==NoteStore.Branch.Kind.BOOK;}

    /** Two things of one kind, as a level is ordered: notes among notes, collections among collections. */
    private static boolean alike(NoteStore.Branch one,NoteStore.Branch other) {
        return other!=null&&(one.kind==NoteStore.Branch.Kind.PAGE?other.kind==NoteStore.Branch.Kind.PAGE:shelf(other.kind));
    }

    /** The top, as a place a collection is moved onto: Home. */
    static NoteStore.Branch top(){return new NoteStore.Branch(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING,"","Home","",0,0,true);}

    /** Each collection's parent as the tree was read, the top said as {@link Things} says it: what a move is judged by. */
    static Map<String,String> parents(List<NoteStore.Branch> everything) {
        Map<String,String> all=new HashMap<>();
        for(NoteStore.Branch one:everything)if(shelf(one.kind))all.put(one.id,NoteStore.home(one.parent)?Things.HOME:one.parent);
        return all;
    }

    /**
     * Whether {@code into} takes {@code moved} in: a note goes into any collection, a collection onto the top or into
     * any collection that is not itself or inside it (docs/HOME.md, decision 14). Nothing goes into a note.
     */
    static boolean holds(NoteStore.Branch into,NoteStore.Branch moved,Map<String,String> parents) {
        if(into==null||!movable(moved))return false;
        if(moved.kind==NoteStore.Branch.Kind.PAGE)return shelf(into.kind);
        boolean onTop=into.kind==NoteStore.Branch.Kind.LIBRARY;
        return (onTop||shelf(into.kind))&&Things.mayGoInto(parents,moved.id,onTop?Things.HOME:into.id,true);
    }

    /** One level, as the tree was read: the things of this one's kind in this collection, in their order. */
    static List<String> level(List<NoteStore.Branch> everything,NoteStore.Branch like,String parent) {
        List<String> ids=new ArrayList<>();
        for(NoteStore.Branch one:everything)if(movable(one)&&alike(like,one)&&parent.equals(one.parent))ids.add(one.id);
        return ids;
    }

    /** The level with the moved thing taken out and put back at {@code slot} among the others. */
    static List<String> placed(List<String> level,String moved,int slot) {
        List<String> others=new ArrayList<>(level);others.remove(moved);
        others.add(Math.max(0,Math.min(slot,others.size())),moved);
        return others;
    }

    private static NoteStore.Branch find(List<NoteStore.Branch> everything,String id) {
        if(id==null)return null;for(NoteStore.Branch one:everything)if(one.id.equals(id))return one;return null;
    }

    /** Whether the pointer is on the middle half of a line: on it, rather than on an edge it shares with the next. */
    private static boolean middleOf(NoteStore.Branch under,float y,List<Line> lines) {
        for(Line line:lines)if(line.branch.id.equals(under.id))return y>=line.top+line.height/4f&&y<line.top+line.height*3f/4f;
        return true;
    }

    /**
     * Let go over a line of the tree, {@code y} down it: onto a collection that can take it, inside it; anywhere else,
     * among the things of its own kind that line belongs to - a note's line counts as its collection's when a
     * collection is dragged. Null where it cannot go, or where it would land where it already is.
     */
    static Landing overTree(NoteStore.Branch moved,NoteStore.Branch under,float y,List<Line> lines,List<NoteStore.Branch> everything) {
        if(!movable(moved)||under==null)return null;
        Map<String,String> parents=parents(everything);
        if(shelf(under.kind)&&holds(under,moved,parents)&&(moved.kind==NoteStore.Branch.Kind.PAGE||middleOf(under,y,lines))) {
            if(!under.id.equals(moved.parent))return new Landing(under,under.id,0,null,null);
            // Its own collection: to the top of it, which is where Move puts things.
            List<String> level=level(everything,moved,under.id),order=placed(level,moved.id,0);
            return order.equals(level)?null:new Landing(null,under.id,0,order,level);
        }
        NoteStore.Branch same=alike(moved,under)?under:find(everything,under.parent);
        if(!alike(moved,same))return null;
        String parent=same.parent;
        // A level elsewhere is gone into by going into what holds it - where that may take it: nothing goes inside itself.
        boolean stays=parent.equals(moved.parent);
        NoteStore.Branch into=stays?null:NoteStore.home(parent)?top():find(everything,parent);
        if(!stays&&!holds(into,moved,parents))return null;
        List<Float> middles=new ArrayList<>();int dragged=-1;
        for(Line line:lines)if(alike(moved,line.branch)&&parent.equals(line.branch.parent)) {
            if(line.branch.id.equals(moved.id))dragged=middles.size();
            middles.add(line.top+line.height/2f);
        }
        float[] at=new float[middles.size()];for(int i=0;i<at.length;i++)at[i]=middles.get(i);
        int slot=Reorder.slot(y,at,dragged);
        List<String> level=level(everything,moved,parent),order=placed(level,moved.id,slot);
        if(stays)return order.equals(level)?null:new Landing(null,parent,slot,order,level);
        return new Landing(into,parent,slot,order,null);
    }

    /**
     * Let go over the cards of {@code here}: the place among the cards of its own kind the pointer has passed in
     * reading order, as the phone's cards do ({@link Reorder}); a collection's collections and its notes each keep an
     * order of their own. A thing from elsewhere moves in, if {@code here} can take it.
     */
    static Landing overCards(NoteStore.Branch moved,NoteStore.Branch here,float x,float y,List<NoteStore.Branch> shown,List<Rectangle> cards,
                             List<NoteStore.Branch> everything) {
        if(!movable(moved)||here==null)return null;
        String parent=here.kind==NoteStore.Branch.Kind.LIBRARY?Sharing.EVERYTHING:here.id;
        boolean inHere=parent.equals(moved.parent);
        if(!inHere&&!holds(here,moved,parents(everything)))return null;
        List<NoteStore.Branch> kin=new ArrayList<>();List<Rectangle> boxes=new ArrayList<>();
        for(int i=0;i<shown.size()&&i<cards.size();i++)if(alike(moved,shown.get(i))){kin.add(shown.get(i));boxes.add(cards.get(i));}
        int dragged=-1;float[] mx=new float[boxes.size()],my=new float[boxes.size()];
        for(int i=0;i<boxes.size();i++){Rectangle r=boxes.get(i);mx[i]=(float)r.getCenterX();my[i]=(float)r.getCenterY();if(kin.get(i).id.equals(moved.id))dragged=i;}
        float halfRow=boxes.isEmpty()?0:boxes.get(0).height/2f;
        int slot=boxes.isEmpty()?0:Reorder.slot(x,y,mx,my,halfRow,dragged);
        List<String> level=new ArrayList<>();for(NoteStore.Branch one:kin)level.add(one.id);
        List<String> order=placed(level,moved.id,slot);
        if(inHere)return order.equals(level)?null:new Landing(null,parent,slot,order,level);
        return new Landing(here,parent,slot,order,null);
    }

    // ---- the drag itself ----------------------------------------------------------------------------------

    private final Desktop pad;
    DesktopMoving(Desktop pad){this.pad=pad;}

    /** Pressed on, not yet moved far enough to be a drag. */
    private NoteStore.Branch pressed;private java.awt.Point pressedAt;private Component source;private Cursor sourceCursor;
    /** In hand, and where it would land now. */
    private NoteStore.Branch carried;private Landing landing;
    /** What the mark is drawn on (the tree or the cards), and the mark: a line between, or an edge round. */
    private JComponent markedOn;private Rectangle line,round;
    /** The card lifted, drawn pale while it is carried. */
    private JComponent lifted;
    /** The name, beside the pointer, so it is plain what is being carried. */
    private JLabel ghost;
    private KeyEventDispatcher escape;
    /** Set when a drag starts, until the next press: a drag let go on a card is not also a click on it. */
    private boolean moved;
    private String said="";

    boolean moved(){return moved;}
    boolean carrying(){return carried!=null;}

    /** The tree's lines, pressed and dragged: the favourites are left alone, as on the phone. */
    void watch(JTree tree) {
        MouseAdapter hand=new MouseAdapter(){
            @Override public void mousePressed(MouseEvent e) {
                if(!SwingUtilities.isLeftMouseButton(e))return;
                TreePath path=tree.getPathForLocation(e.getX(),e.getY());
                press(path==null||inFavourites(path)?null:thing(path.getLastPathComponent()),e);
            }
            @Override public void mouseDragged(MouseEvent e){drag(e);}
            @Override public void mouseReleased(MouseEvent e){release(e);}
        };
        tree.addMouseListener(hand);tree.addMouseMotionListener(hand);
    }
    private static NoteStore.Branch thing(Object node){return node instanceof DefaultMutableTreeNode n&&n.getUserObject() instanceof Desktop.Item item?item.branch():null;}
    private static boolean inFavourites(TreePath path) {
        for(Object one:path.getPath()){NoteStore.Branch b=thing(one);if(b!=null&&b.kind==NoteStore.Branch.Kind.FAVOURITES)return true;}
        return false;
    }

    void press(NoteStore.Branch one,MouseEvent e) {
        moved=false;pressed=null;
        if(!SwingUtilities.isLeftMouseButton(e)||!movable(one))return;
        pressed=one;pressedAt=e.getLocationOnScreen();source=e.getComponent();
    }
    void drag(MouseEvent e) {
        if(pressed==null)return;
        java.awt.Point now=e.getLocationOnScreen();
        if(carried==null){if(now.distance(pressedAt)<Math.max(4,DragSource.getDragThreshold()))return;begin(pressed,source);}
        at(now);
    }
    void release(MouseEvent e) {
        if(carried!=null){at(e.getLocationOnScreen());letGo();}
        pressed=null;
    }

    /** Taken hold of: lifted, named beside the pointer, and Esc ready to put it back. */
    void begin(NoteStore.Branch one,Component from) {
        carried=one;moved=true;landing=null;source=from;
        sourceCursor=from==null?null:from.getCursor();
        lifted=from instanceof JComponent c?(JComponent)cardOf(c):null;
        ghost=new JLabel(one.name==null||one.name.isBlank()?"Untitled":one.name);
        ghost.setFont(DesktopUi.BODY.deriveFont(Font.BOLD,13f));ghost.setForeground(DesktopUi.INK);ghost.setOpaque(true);ghost.setBackground(DesktopUi.CARD);
        ghost.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(DesktopUi.ACCENT),BorderFactory.createEmptyBorder(4,10,4,10)));
        ghost.setSize(ghost.getPreferredSize());ghost.setVisible(false);
        pad.frame.getLayeredPane().add(ghost,JLayeredPane.DRAG_LAYER);
        escape=e->{
            if(carried==null||e.getKeyCode()!=KeyEvent.VK_ESCAPE)return false;
            if(e.getID()==KeyEvent.KEY_PRESSED)cancel();
            return true;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(escape);
        if(lifted!=null)lifted.getParent().repaint();
    }
    private static Component cardOf(Component c) {
        for(Component at=c;at!=null;at=at.getParent())if(at instanceof JComponent j&&j.getClientProperty(DesktopHome.THING)!=null)return at;
        return null;
    }

    /** The pointer at this point of the screen: where it would land there, marked, and said in the bar. */
    void at(java.awt.Point screen) {
        if(carried==null)return;
        java.awt.Point onLayer=new java.awt.Point(screen);SwingUtilities.convertPointFromScreen(onLayer,pad.frame.getLayeredPane());
        ghost.setLocation(onLayer.x+16,onLayer.y+12);ghost.setVisible(true);
        Landing now=null;JComponent on=null;Rectangle between=null,onto=null;
        JTree tree=pad.tree;
        if(tree.isShowing()&&over(tree,screen)) {
            java.awt.Point p=new java.awt.Point(screen);SwingUtilities.convertPointFromScreen(p,tree);edge(tree,p);
            List<Line> lines=lines(tree);
            Line under=null;for(Line l:lines)if(p.y>=l.top&&p.y<l.top+l.height)under=l;
            if(under==null&&!lines.isEmpty()&&p.y>=lines.get(lines.size()-1).top)under=lines.get(lines.size()-1);
            now=under==null?null:overTree(carried,under.branch,p.y,lines,pad.everything());
            if(now!=null) {
                on=tree;
                if(now.order==null)onto=new Rectangle(under.left-4,under.top+1,tree.getWidth()-under.left,under.height-2);
                else between=treeLine(now,lines);
            }
        }
        boolean changed=markedOn!=on||!java.util.Objects.equals(line,between)||!java.util.Objects.equals(round,onto);
        JComponent was=markedOn;
        landing=now;markedOn=on;line=between;round=onto;
        if(changed){if(was!=null)was.repaint();if(on!=null)on.repaint();}
        if(source!=null)source.setCursor(now!=null?DragSource.DefaultMoveDrop:DragSource.DefaultMoveNoDrop);
        say(now==null?"It cannot go here  ·  Esc cancels"
            :now.into!=null?"Let go to move it into "+named(now.into)+"  ·  Esc cancels"
            :"Let go to put it here  ·  Esc cancels");
    }
    private void say(String words){if(!words.equals(said)){said=words;pad.status.setToolTipText(null);pad.status.setText(words);}}
    private static String named(NoteStore.Branch b){return b.name==null||b.name.isBlank()?"Untitled":b.name;}

    private static boolean over(Component c,java.awt.Point screen) {
        java.awt.Point p=new java.awt.Point(screen);SwingUtilities.convertPointFromScreen(p,c);
        return c instanceof JComponent j?j.getVisibleRect().contains(p):c.contains(p);
    }

    /** Held near the top or the bottom of what can scroll, it scrolls, so a long list is reordered in one go. */
    private static void edge(JComponent c,java.awt.Point p) {
        Rectangle seen=c.getVisibleRect();int reach=28,step=18;
        if(p.y<seen.y+reach)c.scrollRectToVisible(new Rectangle(seen.x,Math.max(0,seen.y-step),1,1));
        else if(p.y>seen.y+seen.height-reach)c.scrollRectToVisible(new Rectangle(seen.x,seen.y+seen.height+step,1,1));
    }

    /** The tree's lines as drawn, without the top, the favourites or anything under them. */
    static List<Line> lines(JTree tree) {
        List<Line> lines=new ArrayList<>();
        for(int row=0;row<tree.getRowCount();row++) {
            TreePath path=tree.getPathForRow(row);if(path==null||inFavourites(path))continue;
            NoteStore.Branch b=thing(path.getLastPathComponent());if(!movable(b))continue;
            Rectangle r=tree.getRowBounds(row);lines.add(new Line(b,r.y,r.height,r.x));
        }
        return lines;
    }

    /** The line between two lines of the tree where it would land: above the one it goes before, or under the last. */
    private Rectangle treeLine(Landing l,List<Line> lines) {
        List<Line> others=new ArrayList<>();
        for(Line one:lines)if(alike(carried,one.branch)&&l.parent.equals(one.branch.parent)&&!one.branch.id.equals(carried.id))others.add(one);
        int width=pad.tree.getWidth();
        if(others.isEmpty())return null;
        if(l.slot<others.size()){Line next=others.get(l.slot);return new Rectangle(next.left,next.top-1,width-next.left,3);}
        // After the last of them, under everything it holds.
        Line last=others.get(others.size()-1);int bottom=last.top+last.height;boolean inside=false;
        for(Line one:lines) {
            if(one==last){inside=true;continue;}
            if(!inside)continue;
            // Whatever is drawn further in is inside it; the first line level with it, or further out, is not.
            if(one.left<=last.left)break;
            bottom=one.top+one.height;
        }
        return new Rectangle(last.left,bottom-2,width-last.left,3);
    }

    /** The mark, drawn over the tree it is on. */
    void paint(JComponent on,Graphics g0) {
        if(carried==null)return;
        Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        // The card in hand, pale where it was.
        if(lifted!=null&&lifted.getParent()==on){Rectangle r=lifted.getBounds();Color ground=DesktopUi.ground(on);
            g.setColor(new Color(ground.getRed(),ground.getGreen(),ground.getBlue(),170));g.fillRoundRect(r.x,r.y,r.width,r.height,14,14);}
        if(on==markedOn) {
            g.setColor(DesktopUi.ACCENT);
            if(line!=null) {
                g.fillRoundRect(line.x,line.y,line.width,line.height,3,3);
                // A small round at the start of a lying line, as editors draw it, so a thin line is not missed.
                if(line.width>line.height)g.fillOval(line.x-3,line.y+line.height/2-4,8,8);
            }
            if(round!=null) {
                // Tinted through, not filled over: the mark is drawn on top of the tree, and the name has to show.
                g.setColor(new Color(DesktopUi.ACCENT.getRed(),DesktopUi.ACCENT.getGreen(),DesktopUi.ACCENT.getBlue(),36));g.fillRoundRect(round.x,round.y,round.width,round.height,8,8);
                g.setColor(DesktopUi.ACCENT);g.setStroke(new BasicStroke(2f));g.drawRoundRect(round.x+1,round.y+1,round.width-3,round.height-3,8,8);
            }
        }
        g.dispose();
    }
    /** Let go: done where it landed, or nothing at all. */
    void letGo() {
        NoteStore.Branch thing=carried;Landing l=landing;
        end();
        if(l==null){pad.status.setText("Not moved");return;}
        pad.status.setText(" ");
        if(l.into==null)pad.reorder(thing,l.order,l.before);else pad.moveInto(thing,l.into,l.order);
    }
    /** Esc: put back where it was, and said. */
    void cancel(){if(carried==null)return;end();pressed=null;pad.status.setText("Not moved");}

    private void end() {
        carried=null;landing=null;said="";
        if(escape!=null){KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(escape);escape=null;}
        if(ghost!=null){Container layer=ghost.getParent();if(layer!=null){layer.remove(ghost);layer.repaint();}ghost=null;}
        if(source!=null)source.setCursor(sourceCursor);
        JComponent was=markedOn;markedOn=null;line=null;round=null;
        if(was!=null)was.repaint();
        if(lifted!=null&&lifted.getParent()!=null)lifted.getParent().repaint();
        lifted=null;
    }
}
