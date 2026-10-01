package org.mininotes.android;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.*;

/**
 * A collection opened on Home: a rounded card over the dimmed Home, as a folder opens on a phone's desktop and as the
 * phone's own does (docs/HOME.md, step 3). Its name at the top - a click or F2 and it becomes the field it already looks
 * like - its mark and its ⋯, then its grid, and its own + at the foot. A collection inside it opens in the same card,
 * with ‹ and the name of the one it is in to go back up (decision 21): a stack of cards would hide the Home they belong
 * to. Esc goes up a level and then closes it; a click on the dimmed Home closes it at once.
 *
 * <p>The Favourites collection opens the same way, listing the favourites the dock has no room for; it is a place, not
 * a collection, and is never renamed. So do the archive and the bin, while they are on Home (decision 41): what waits in
 * them, a click on each offering where it goes from there, and on the bin's ⋯, Empty the bin. While the card is up, Tab
 * goes round the card and nothing under it.
 */
final class DesktopFolder extends JComponent {
    private final DesktopHome home;
    private final Desktop pad;
    /** The card itself, and in it the bar, the grid and the +. */
    final JPanel card;
    private final JPanel head=new JPanel(new BorderLayout(8,0));
    final DesktopHome.Icons grid;
    private final JScrollPane scroll;
    private final JButton plus;
    /** Where the card is: every collection from the one opened on Home down to the one it shows; or the Favourites alone. */
    private final List<NoteStore.Branch> trail=new ArrayList<>();
    /** The collection the card shows, as last read, as the thing its menu and its mark are about; null among the favourites. */
    private NoteStore.Branch thing;
    /** The collection it is in, as last read: what "up a level" means, and what ‹ says. */
    private String parent=Things.HOME,parentName="Home";
    /** Which collection the grid shows, so a card that moves on starts at its top and one read again stays where it was. */
    private String shown="";
    /** A collection whose name is to be typed as soon as its card is drawn: one just made, or two notes just put together. */
    private String nameNext;
    /** The field its name is typed in, while it is. */
    private JTextField naming;
    private boolean lit;
    /** The face before the card's name. */
    private static final int FACE=26;

    DesktopFolder(DesktopHome home) {
        this.home=home;this.pad=home.pad;
        setLayout(null);setOpaque(false);
        grid=new DesktopHome.Icons(home,DesktopHome.Where.CARD);
        scroll=new JScrollPane(grid);scroll.setBorder(null);scroll.setOpaque(false);scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        plus=home.plusButton(this::id);
        JLayeredPane body=new JLayeredPane(){
            @Override public void doLayout() {
                scroll.setBounds(0,0,getWidth(),getHeight());
                Dimension p=plus.getPreferredSize();plus.setBounds(getWidth()-p.width-14,getHeight()-p.height-12,p.width,p.height);
            }
        };
        body.add(scroll,JLayeredPane.DEFAULT_LAYER);body.add(plus,JLayeredPane.PALETTE_LAYER);
        card=new JPanel(new BorderLayout(0,4)){
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(ground());g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,26,26);
                g.setColor(DesktopUi.LINE.darker());g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,26,26);g.dispose();
            }
        };
        card.setOpaque(false);card.setBorder(BorderFactory.createEmptyBorder(10,12,4,12));
        head.setOpaque(false);head.setBorder(BorderFactory.createEmptyBorder(0,4,0,4));
        card.add(head,BorderLayout.NORTH);card.add(body);
        add(card);
        // Tab goes round the card while it is up: what is under the dim cannot be reached until it goes.
        setFocusCycleRoot(true);setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
        // A click on the dimmed Home round the card closes it, as a click beside every box here does. The dim takes the
        // pointer, so nothing under it is clicked or scrolled by mistake.
        addMouseListener(new MouseAdapter(){public void mousePressed(MouseEvent e){if(!card.getBounds().contains(e.getPoint())&&!home.carry.carrying())close();}});
        addMouseMotionListener(new MouseMotionAdapter(){});addMouseWheelListener(e->{});
        // A right-click on the card itself, not on an icon: what can be made in it, then the collection's own menu.
        MouseAdapter room=new MouseAdapter(){
            public void mousePressed(MouseEvent e){if(e.isPopupTrigger()&&thing!=null)pad.roomMenu(thing,e.getComponent(),e.getX(),e.getY());}
            public void mouseReleased(MouseEvent e){if(e.isPopupTrigger()&&thing!=null)pad.roomMenu(thing,e.getComponent(),e.getX(),e.getY());}
        };
        for(JComponent part:new JComponent[]{card,grid,head})part.addMouseListener(room);scroll.getViewport().addMouseListener(room);
        card.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("F2"),"rename");
        card.getActionMap().put("rename",new AbstractAction(){public void actionPerformed(ActionEvent e){startNaming();}});
    }

    /** The card's paper: the collection's colour washed over it, as a room is, so the card is the collection you stand in. */
    private Color ground(){return DesktopLook.wash(thing==null?Tint.NONE:thing.colour,DesktopUi.PAPER,0.12f,0.72f,pad.tone);}

    @Override public void doLayout() {
        int w=getWidth(),h=getHeight();
        // From just under the toolbar, over the first row of Home's icons, so no mark on them peeks out above the card.
        int wide=Math.max(Math.min(w-40,300),Math.min(w-96,6*DesktopHome.CELL+2*DesktopHome.SIDE+28)),high=Math.max(Math.min(h-24,240),h-30);
        card.setBounds((w-wide)/2,Math.min(10,Math.max(0,h-high)),wide,high);
    }
    @Override protected void paintComponent(Graphics g) {
        // Home dimmed under the card; darker while a thing carried out of it would come up a level there.
        g.setColor(new Color(20,24,20,lit?110:64));g.fillRect(0,0,getWidth(),getHeight());
    }

    boolean isOpen(){return isVisible()&&!trail.isEmpty();}
    private NoteStore.Branch last(){return trail.get(trail.size()-1);}
    /** Whether the card is the Favourites', where things are only listed and never kept. */
    boolean amongFavourites(){return !trail.isEmpty()&&last().kind==NoteStore.Branch.Kind.FAVOURITES;}
    /** Whether the card is one of Home's places - the favourites, the archive, the bin - where things are only listed. */
    boolean listing(){return !trail.isEmpty()&&Grid.place(last().kind);}
    /** Whether the card is the archive's or the bin's, where things wait to be put back. */
    boolean away(){return !trail.isEmpty()&&(last().kind==NoteStore.Branch.Kind.ARCHIVE||last().kind==NoteStore.Branch.Kind.BIN);}
    /** Whether the card is the bin's. */
    boolean inBin(){return !trail.isEmpty()&&last().kind==NoteStore.Branch.Kind.BIN;}
    /** The collection the card shows, or Home when it shows none. */
    String id(){return trail.isEmpty()||listing()?Things.HOME:last().id;}
    /** The collection it is in, or Home: where a thing carried out onto the dimmed Home goes. */
    String parentId(){return parent;}
    String parentName(){return parentName;}
    /** The collection shown, as last read, for the Share button and the menus; null when none is. */
    NoteStore.Branch shown(){return isOpen()&&!listing()?thing:null;}
    /** Where the card is now, for a read of the notebook to take with it. */
    List<NoteStore.Branch> path(){return isOpen()?new ArrayList<>(trail):List.of();}
    /** Whether a name is being typed on it: Home is not drawn again under the field. */
    boolean naming(){return naming!=null;}

    // ---- opening and closing ------------------------------------------------------------------------------------------

    /** A collection on Home, clicked: its card. */
    void open(NoteStore.Branch collection){open(collection,false);}
    void open(NoteStore.Branch collection,boolean name){trail.clear();trail.add(step(collection));nameNext=name?collection.id:null;draw();}
    /** The Favourites icon, clicked: the favourites the dock has no room for. */
    void openFavourites(){trail.clear();trail.add(DesktopHome.favouritesPlace());nameNext=null;draw();}
    /** The archive or the bin, clicked on Home: what waits in it. */
    void openPlace(NoteStore.Branch place){trail.clear();trail.add(place);nameNext=null;draw();}
    /** A collection inside the card, clicked: the same card, one level in. */
    void into(NoteStore.Branch collection){into(collection,false);}
    void into(NoteStore.Branch collection,boolean name){if(!isOpen()){open(collection,name);return;}trail.add(step(collection));nameNext=name?collection.id:null;draw();}
    /**
     * A collection from anywhere else - the tree, the dock, the search, the overview, a favourite: its card over Home, with
     * the trail above it being where it really is, however deep, so ‹ goes up to what holds it.
     */
    void openAt(String id){openAt(id,false);}
    void openAt(String id,boolean name) {
        pad.disk.submit(()->{
            List<NoteStore.Branch> steps=new ArrayList<>();
            for(String up:pad.store.above(id))steps.add(new NoteStore.Branch(NoteStore.Branch.Kind.COLLECTION,up,"",pad.store.collectionName(up),"",0,0,true));
            steps.add(new NoteStore.Branch(NoteStore.Branch.Kind.COLLECTION,id,"",pad.store.collectionName(id),"",0,0,true));
            return steps;
        },steps->{trail.clear();trail.addAll(steps);nameNext=name?id:null;pad.showHome();draw();},pad::failed);
    }
    private static NoteStore.Branch step(NoteStore.Branch collection) {
        return new NoteStore.Branch(NoteStore.Branch.Kind.COLLECTION,collection.id,collection.parent,collection.name,"",0,0,true,collection.colour);
    }

    /** ‹, or Esc: one level up, in the same card, or the card closed where there is no level above it but Home. */
    void up(){if(trail.size()>1){trail.remove(trail.size()-1);nameNext=null;draw();}else close();}

    /** Put away, and Home under it is what is seen again, the keyboard on the icon the card was opened from. */
    void close() {
        String from=trail.isEmpty()?null:trail.get(0).id;
        if(naming!=null)finishNaming(true);
        trail.clear();thing=null;shown="";nameNext=null;setVisible(false);lit=false;
        // Home's own + back, now the card's is gone.
        home.plus.setVisible(true);
        home.focusThing(from);
        home.refreshIfStale();
    }

    /** The card for where the trail ends, over Home: up at once with its name from the trail, then read and drawn. */
    private void draw() {
        if(trail.isEmpty())return;
        NoteStore.Branch now=last();
        if(!now.id.equals(shown)){grid.fill(List.of(),null);thing=listing()?null:now;bar(now);}
        plus.setVisible(!listing());
        // One + at a time: while the card is up, its own is the one, and Home's under the dim goes.
        home.plus.setVisible(false);
        setVisible(true);revalidate();repaint();
        // A collection's card is one of the things open, for the overview; the places are not things.
        if(now.kind==NoteStore.Branch.Kind.COLLECTION)pad.overview.remember(Overview.Kind.COLLECTION,now.id);
        home.refresh();
    }

    // ---- reading and drawing ------------------------------------------------------------------------------------------

    /**
     * What the card needs, read on the disk with the rest of Home: how much of the trail is still there, and for the
     * collection it ends in, its name, its colour, what it is in, and what it holds; for the archive or the bin, what
     * waits there, each dressed in its own look.
     */
    void read(List<NoteStore.Branch> path,Map<String,Object> got) {
        if(path.isEmpty())return;
        NoteStore.Branch end=path.get(path.size()-1);
        if(end.kind==NoteStore.Branch.Kind.FAVOURITES)return;
        if(end.kind==NoteStore.Branch.Kind.ARCHIVE||end.kind==NoteStore.Branch.Kind.BIN) {
            List<NoteStore.Branch> waiting=pad.store.heldIn(end.kind==NoteStore.Branch.Kind.BIN);
            pad.store.dress(waiting);
            got.put("card",waiting);
            return;
        }
        // The trail is kept across a trip to a note, and what it names can have been put away since.
        for(int at=0;at<path.size();at++)if(!pad.store.stillThere(NoteStore.Branch.Kind.COLLECTION,path.get(at).id)){got.put("keep",at);return;}
        String in=pad.store.collectionOfBook(end.id);
        got.put("card",pad.store.contents(end.id));
        got.put("name",pad.store.collectionName(end.id));
        got.put("tint",pad.store.colourOf(NoteStore.Branch.Kind.COLLECTION,end.id));
        // What it wears, for the face before its name.
        got.put("icon",pad.store.iconOf(NoteStore.Branch.Kind.COLLECTION,end.id));
        byte[] image=pad.store.imageOf(NoteStore.Branch.Kind.COLLECTION,end.id);if(image!=null)got.put("image",image);
        got.put("parent",in==null||in.isEmpty()?Things.HOME:in);
        got.put("parentName",in==null||in.isEmpty()?"Home":pad.store.collectionName(in));
    }

    /** The card drawn from what was read, if it is still where it was when it was read. */
    void fill(List<NoteStore.Branch> path,Map<String,Object> got) {
        if(!isOpen()||!same(path))return;
        Object keep=got.get("keep");
        if(keep!=null) {
            // Out to the last level that is still there, and said: a card that quietly becomes another is worse.
            int at=(Integer)keep;String gone=path.get(at).name;
            while(trail.size()>at)trail.remove(trail.size()-1);
            pad.status.setToolTipText(null);pad.status.setText("“"+gone+"” was put away");
            if(trail.isEmpty())close();else draw();
            return;
        }
        NoteStore.Branch end=last();
        List<NoteStore.Branch> lines;
        if(amongFavourites()){thing=null;parent=Things.HOME;parentName="Home";lines=home.overflow();}
        else if(away()) {
            @SuppressWarnings("unchecked") List<NoteStore.Branch> held=(List<NoteStore.Branch>)got.get("card");
            thing=null;parent=Things.HOME;parentName="Home";lines=held==null?List.of():held;
        }
        else {
            @SuppressWarnings("unchecked") List<NoteStore.Branch> held=(List<NoteStore.Branch>)got.get("card");
            lines=held==null?List.of():held;
            parent=(String)got.getOrDefault("parent",Things.HOME);parentName=(String)got.getOrDefault("parentName","Home");
            String name=(String)got.getOrDefault("name",end.name);int tint=(Integer)got.getOrDefault("tint",Tint.NONE);
            thing=new NoteStore.Branch(NoteStore.Branch.Kind.COLLECTION,end.id,NoteStore.home(parent)?Sharing.EVERYTHING:parent,name==null?end.name:name,"",0,0,true,tint);
            thing.icon=(String)got.getOrDefault("icon","");thing.image=(byte[])got.get("image");
            trail.set(trail.size()-1,step(thing));
            if(trail.size()>1&&parentName!=null){NoteStore.Branch above=trail.get(trail.size()-2);
                trail.set(trail.size()-2,new NoteStore.Branch(NoteStore.Branch.Kind.COLLECTION,above.id,above.parent,parentName,"",0,0,true,above.colour));}
        }
        // Files from Explorer let go on the card are kept with this collection (decision 23); in a place, on Home.
        card.putClientProperty(DesktopHome.KEEPS,thing);grid.putClientProperty(DesktopHome.KEEPS,thing);
        bar(end);
        boolean same=end.id.equals(shown);shown=end.id;
        if(!same)scroll.getViewport().setViewPosition(new java.awt.Point(0,0));
        grid.fill(lines,lines.isEmpty()?nothingYet(end.kind):null);
        card.repaint();
        // The keyboard to the first icon as it reads, top left, wherever the icons were put.
        if(!same&&naming==null&&!end.id.equals(nameNext))SwingUtilities.invokeLater(()->{DesktopHome.Tile first=grid.first();if(first!=null)first.requestFocusInWindow();else if(plus.isVisible())plus.requestFocusInWindow();});
        if(end.id.equals(nameNext)){nameNext=null;startNaming();}
    }

    /** What an empty card says: what to do, or where things come from. */
    private static String nothingYet(NoteStore.Branch.Kind kind) {
        return switch(kind) {
            case FAVOURITES -> "Every favourite is in the dock.";
            case ARCHIVE -> "Nothing archived. Archive a note or a collection from its menu, or carry it onto the Archive.";
            case BIN -> "The bin is empty.";
            default -> "Nothing in it yet. Click + to add a note or a collection, or drop files here.";
        };
    }

    /** Whether the trail is the one that was read: the reader may have gone in or out of the card since. */
    private boolean same(List<NoteStore.Branch> path) {
        if(path.size()!=trail.size())return false;
        for(int at=0;at<path.size();at++)if(!path.get(at).id.equals(trail.get(at).id))return false;
        return true;
    }

    /**
     * The card's bar: ‹ and the collection it is in, where it is inside another; its name, clicked to rename it; its
     * mark; and its ⋯, the collection's own menu. A place's card has its name and nothing to rename or ask - but the
     * bin's ⋯, which empties it.
     */
    private void bar(NoteStore.Branch end) {
        head.removeAll();
        boolean place=Grid.place(end.kind);
        // The two sides as wide as each other, so the name stands in the middle of the card.
        JPanel left=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0)),right=new JPanel(new FlowLayout(FlowLayout.RIGHT,4,0));
        left.setOpaque(false);right.setOpaque(false);
        if(trail.size()>1) {
            String above=trail.get(trail.size()-2).name;
            JButton back=new JButton("‹  "+(above==null||above.isBlank()?"Untitled":above));
            back.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            back.setFont(DesktopUi.BODY.deriveFont(13f));back.setForeground(DesktopUi.QUIET);back.setFocusPainted(false);
            back.setToolTipText("Back to "+above+" (Esc)");back.getAccessibleContext().setAccessibleName("Back to "+above);
            back.addActionListener(e->up());left.add(back);
        }
        if(!place&&thing!=null) {
            SyncMark mark=pad.marks.get(thing.id);
            if(mark!=null){NoteStore.Branch about=thing;JLabel ring=DesktopMark.button(mark,20,true,()->pad.markClicked(about,mark));right.add(ring);}
            JButton more=new JButton(Desktop.dots());
            more.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            more.setFocusPainted(false);more.setToolTipText("More");more.getAccessibleContext().setAccessibleName("More for "+DesktopHome.named(thing));
            NoteStore.Branch about=thing;
            more.addActionListener(e->pad.thingMenu(about).show(more,0,more.getHeight()));
            right.add(more);
        } else if(end.kind==NoteStore.Branch.Kind.BIN) {
            JButton more=new JButton(Desktop.dots());
            more.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            more.setFocusPainted(false);more.setToolTipText("More");more.getAccessibleContext().setAccessibleName("More for the bin");
            more.addActionListener(e->{JPopupMenu menu=new JPopupMenu();JMenuItem empty=new JMenuItem("Empty the bin…");empty.addActionListener(a->pad.askEmptyBin());menu.add(empty);menu.show(more,0,more.getHeight());});
            right.add(more);
        }
        int side=Math.max(Math.max(left.getPreferredSize().width,right.getPreferredSize().width),120);
        left.setPreferredSize(new Dimension(side,left.getPreferredSize().height));right.setPreferredSize(new Dimension(side,right.getPreferredSize().height));
        JPanel l=new JPanel(new GridBagLayout());l.setOpaque(false);l.add(left);JPanel r=new JPanel(new GridBagLayout());r.setOpaque(false);r.add(right);
        head.add(l,BorderLayout.WEST);head.add(r,BorderLayout.EAST);
        String said=place?end.name:DesktopHome.named(thing!=null?thing:end);
        JLabel name=new JLabel(said,SwingConstants.CENTER);name.setFont(DesktopUi.BODY.deriveFont(Font.BOLD,18f));name.setForeground(DesktopUi.INK);
        name.setBorder(BorderFactory.createEmptyBorder(8,0,8,0));
        if(!place) {
            name.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));name.setToolTipText("Click to rename (F2)");
            name.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e))startNaming();}});
        }
        name.getAccessibleContext().setAccessibleName(place?said:"Collection "+said);
        if(place||thing==null){head.add(name);head.revalidate();head.repaint();return;}
        // Before its name, the face it wears on Home, as a note's is before its title: a click chooses another.
        JButton face=new JButton(DesktopHome.faceIcon(()->thing,FACE,()->pad.tone));
        face.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        face.setMargin(new Insets(2,2,2,2));face.setFocusPainted(false);face.setToolTipText("Change its icon");
        face.getAccessibleContext().setAccessibleName("The collection's icon: choose another");
        face.addActionListener(e->{if(thing!=null)DesktopIconPicker.open(pad,thing);});
        JPanel named=new JPanel(new GridBagLayout());named.setOpaque(false);
        GridBagConstraints at=new GridBagConstraints();at.insets=new Insets(0,0,0,8);named.add(face,at);at.insets=new Insets(0,0,0,0);named.add(name,at);
        head.add(named);
        head.revalidate();head.repaint();
    }

    /** A colour chosen for the collection while its card is up: the card takes it at once, as the page does. */
    void painted(){card.repaint();}

    /** Home darker while a thing carried out of the card would come up a level there. */
    void lit(boolean on){if(lit!=on){lit=on;repaint();}}

    // ---- the name, typed where it is ----------------------------------------------------------------------------------

    /** The name becomes the field it already looks like, chosen so typing replaces it. Enter keeps it, Esc leaves it as it was. */
    void startNaming() {
        if(naming!=null||thing==null||listing())return;
        JTextField field=new JTextField(thing.name);field.setFont(DesktopUi.BODY.deriveFont(Font.BOLD,18f));field.setHorizontalAlignment(SwingConstants.CENTER);
        field.getAccessibleContext().setAccessibleName("Name of the collection");field.setToolTipText("Type a name, then Enter");
        naming=field;
        Component was=((BorderLayout)head.getLayout()).getLayoutComponent(BorderLayout.CENTER);if(was!=null)head.remove(was);
        JPanel holder=new JPanel(new GridBagLayout());holder.setOpaque(false);
        GridBagConstraints fill=new GridBagConstraints();fill.fill=GridBagConstraints.HORIZONTAL;fill.weightx=1;fill.insets=new Insets(4,0,4,0);holder.add(field,fill);
        head.add(holder);head.revalidate();head.repaint();
        field.addActionListener(e->finishNaming(true));
        // Esc here is the field's: it leaves the name as it was, and the card stays up.
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ESCAPE"),"cancel");
        field.getActionMap().put("cancel",new AbstractAction(){public void actionPerformed(ActionEvent e){finishNaming(false);}});
        field.addFocusListener(new FocusAdapter(){public void focusLost(FocusEvent e){if(!e.isTemporary())finishNaming(true);}});
        pad.status.setToolTipText(null);pad.status.setText("Type a name for the collection, then Enter");
        SwingUtilities.invokeLater(()->{field.requestFocusInWindow();field.selectAll();});
    }

    /** The name kept, if it changed and is not empty, one Undo from what it was; or left as it was. */
    private void finishNaming(boolean keep) {
        JTextField field=naming;if(field==null||thing==null)return;
        naming=null;
        String was=thing.name==null?"":thing.name,now=field.getText().trim();
        NoteStore.Branch about=thing;
        bar(last());
        if(!keep||now.isEmpty()||now.equals(was)){pad.status.setText(" ");home.refreshIfStale();return;}
        thing=new NoteStore.Branch(about.kind,about.id,about.parent,now,about.detail,0,0,true,about.colour);thing.icon=about.icon;thing.image=about.image;
        trail.set(trail.size()-1,step(thing));bar(last());
        pad.disk.submit(()->{pad.store.renameCollection(about.id,now);return null;},done->{
            pad.status.setText("Renamed to "+now);
            // Only where there was a name before, as on the phone: back to a made-up one is no undo.
            if(!was.isBlank()&&!was.equals(NoteStore.UNTITLED))pad.canUndo(was,()->{pad.store.renameCollection(about.id,was);return null;});
            pad.refresh();
        },pad::failed);
    }
}
