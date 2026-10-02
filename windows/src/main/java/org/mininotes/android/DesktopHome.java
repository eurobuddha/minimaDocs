package org.mininotes.android;

import java.awt.*;
import java.awt.event.*;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.*;

/**
 * Home: the PC's first screen, laid out as the phone's is and as a phone's own desktop is (docs/HOME.md, step 3). It
 * fills the window under the toolbar: the grid of what is on Home - its collections and notes in the owner's order, then
 * the files that came from other devices - in as many columns as the window holds; a round <b>+</b> at the foot on the
 * right; the search field with the overview button beside it; and the dock, with as many favourites as fit. The status
 * bar stays under all of it, where a window keeps it (decision 25). A collection opens as a card over the dimmed Home
 * (see {@link DesktopFolder}); a note opens as the page it always was, filling the window.
 *
 * <p>Every icon stands in the cell it was put in, with the empty cells between left empty, as a phone's own home screen
 * keeps them (decision 39, {@link Layout}). Every icon is picked up the way the phone's are: pressed and moved, it is
 * carried. Let go in an empty cell it stays there; on a note it makes a collection of the two; on a collection it goes
 * in; out of a card onto the dimmed Home it comes up a level, into the cell it is let go in if that is empty; on the
 * dock it is a favourite there. What each drop means is {@link Grid}'s, shared with the phone; this class only measures
 * where the pointer is. Right-click, the Menu key or Shift+F10 gives the thing's menu - the same one, in the same order,
 * as everywhere else - and the keyboard reaches every icon: the arrows move between them, Ctrl+arrow moves the icon
 * itself a cell, Enter opens, F2 renames, Delete puts it in the bin.
 *
 * <p>Home's places stand among the icons (decision 41): the Favourites collection while the dock has no room for every
 * favourite, and the archive and the bin unless they are switched off in Settings, each with how many things wait in it.
 * Each opens as a card of what is in it; a thing let go on the Archive is archived, on the Bin it goes in the bin. A
 * place is carried to another empty cell as any icon is (decision 42), and into nothing.
 */
final class DesktopHome extends JPanel {
    /** Each icon and each line says what it stands for under this key: files from Explorer and the drag ask it. */
    static final String THING="mininotes.thing";
    /** And its menu under this one, made when it is asked for: the Menu key and Shift+F10 open it (see Desktop.contextKey). */
    static final String MENU="mininotes.menu";
    /** What files let go here are kept with: Home, or a collection (see DesktopDrops.aim). */
    static final String KEEPS="mininotes.keeps";
    /** One icon's column and row, its face, the room at either side of a grid, and the room under it for the +. */
    static final int CELL=112,HIGH=126,FACE=64,SIDE=24,TOP=14,UNDER=92;
    /** One place in the dock, the face in it, and the room at its ends. */
    static final int DOCKED=60,DOCK_FACE=44,DOCK_ENDS=14;

    /**
     * Where an icon is: on Home, in a collection's card, in the Favourites card, in the dock, or in the archive's or the
     * bin's card (decision 41), where a thing waits to be put back.
     */
    enum Where { HOME, CARD, FAVOURITES, DOCK, AWAY }

    /**
     * Whether the keyboard, rather than the pointer, was used last. The ring round the icon that has the keyboard shows
     * only then, as a browser's focus-visible does: drawn after every click, it read as the icon being chosen.
     */
    static volatile boolean keyboardLast;
    static {
        if(!GraphicsEnvironment.isHeadless())Toolkit.getDefaultToolkit().addAWTEventListener(e->{
            if(e.getID()==KeyEvent.KEY_PRESSED)keyboardLast=true;else if(e.getID()==MouseEvent.MOUSE_PRESSED)keyboardLast=false;
        },AWTEvent.KEY_EVENT_MASK|AWTEvent.MOUSE_EVENT_MASK);
    }
    /** Whether a component has the keyboard and it shows: the keyboard was used last. */
    static boolean ringed(JComponent c){return c.isFocusOwner()&&keyboardLast;}

    // ---- the rules, without a window, so they are unit tested --------------------------------------------------------

    /** How many icons go across a grid this wide: as many whole columns as fit between its margins, one at the least. */
    static int columns(int width){return Math.max(1,(width-2*SIDE)/CELL);}

    /** How many favourites the dock shows at this width: as many as fit (decision 3), one at the least. */
    static int dockRoom(int width){return Math.max(1,(width-2*DOCK_ENDS)/DOCKED);}

    /** What a key pressed on an icon does. The MOVE_ ones move the icon itself, a cell that way. */
    enum Does { OPEN, RENAME, BIN, MENU, LEFT, RIGHT, UP, DOWN, FIRST, LAST, MOVE_LEFT, MOVE_RIGHT, MOVE_UP, MOVE_DOWN, NONE }

    /**
     * A key on an icon, as the rest of Windows reads it: Enter or Space opens, F2 renames, Delete bins, the Menu key or
     * Shift+F10 gives the menu, the arrows move, Home and End go to the ends. With Ctrl or Alt held an arrow moves the
     * icon itself one cell - the keyboard's way to put a thing where it is wanted; any other key with them is somebody
     * else's - Ctrl+N, Ctrl+Tab and the rest belong to the window.
     */
    static Does does(int key,boolean shift,boolean ctrlOrAlt) {
        if(ctrlOrAlt) {
            if(shift)return Does.NONE;
            switch(key) {
                case KeyEvent.VK_LEFT: return Does.MOVE_LEFT;
                case KeyEvent.VK_RIGHT: return Does.MOVE_RIGHT;
                case KeyEvent.VK_UP: return Does.MOVE_UP;
                case KeyEvent.VK_DOWN: return Does.MOVE_DOWN;
                default: return Does.NONE;
            }
        }
        switch(key) {
            case KeyEvent.VK_ENTER: case KeyEvent.VK_SPACE: return shift?Does.NONE:Does.OPEN;
            case KeyEvent.VK_F2: return Does.RENAME;
            case KeyEvent.VK_DELETE: return Does.BIN;
            case KeyEvent.VK_CONTEXT_MENU: return Does.MENU;
            case KeyEvent.VK_F10: return shift?Does.MENU:Does.NONE;
            case KeyEvent.VK_LEFT: return Does.LEFT;
            case KeyEvent.VK_RIGHT: return Does.RIGHT;
            case KeyEvent.VK_UP: return Does.UP;
            case KeyEvent.VK_DOWN: return Does.DOWN;
            case KeyEvent.VK_HOME: return Does.FIRST;
            case KeyEvent.VK_END: return Does.LAST;
            default: return Does.NONE;
        }
    }

    /**
     * Where an arrow takes the keyboard from icon {@code at} of {@code count}, {@code columns} across, drawn one after the
     * other - the dock's row: along the row, wrapping to the next; up and down a column; and down from a row with nothing
     * under it, to the last icon, as a file window does. At an edge it stays. A grid of cells goes by {@link #stepTo}.
     */
    static int step(int at,int count,int columns,Does move) {
        if(count<=0)return -1;
        int cols=Math.max(1,columns),here=Math.max(0,Math.min(count-1,at));
        switch(move) {
            case LEFT: return Math.max(0,here-1);
            case RIGHT: return Math.min(count-1,here+1);
            case UP: return here-cols>=0?here-cols:here;
            case DOWN: {
                if(here+cols<count)return here+cols;
                return here/cols<(count-1)/cols?count-1:here;
            }
            case FIRST: return 0;
            case LAST: return count-1;
            default: return here;
        }
    }

    /**
     * Where an arrow takes the keyboard on a grid whose icons stand where they were put ({@link Layout#arrange}), by id:
     * along, the next icon in reading order - on along the row, then the rows below - or the one before it, stepping over
     * the empty cells; up and down, the nearest icon that way, a column across counting twice a row, so the one straight
     * above or below wins over one nearer but off to the side, and the leftmost where two are as near. Home and End go to
     * the first and the last in reading order. With nothing that way, it stays.
     */
    static String stepTo(Map<String,int[]> drawn,String from,Does move) {
        int[] here=drawn.get(from);
        if(here==null)return from;
        List<Map.Entry<String,int[]>> reading=new ArrayList<>();
        for(Map.Entry<String,int[]> one:drawn.entrySet())if(one.getValue()!=null)reading.add(one);
        reading.sort((a,b)->a.getValue()[0]!=b.getValue()[0]?Integer.compare(a.getValue()[0],b.getValue()[0]):Integer.compare(a.getValue()[1],b.getValue()[1]));
        int at=-1;for(int i=0;i<reading.size();i++)if(reading.get(i).getKey().equals(from))at=i;
        switch(move) {
            case LEFT: return at>0?reading.get(at-1).getKey():from;
            case RIGHT: return at>=0&&at+1<reading.size()?reading.get(at+1).getKey():from;
            case FIRST: return reading.get(0).getKey();
            case LAST: return reading.get(reading.size()-1).getKey();
            case UP: case DOWN: {
                String best=from;int bestScore=Integer.MAX_VALUE,bestAcross=Integer.MAX_VALUE;
                for(Map.Entry<String,int[]> one:reading) {
                    int rows=(move==Does.DOWN?1:-1)*(one.getValue()[0]-here[0]);
                    if(rows<=0)continue;
                    int across=Math.abs(one.getValue()[1]-here[1]),score=rows+2*across;
                    if(score<bestScore||score==bestScore&&across<bestAcross){best=one.getKey();bestScore=score;bestAcross=across;}
                }
                return best;
            }
            default: return from;
        }
    }

    /**
     * Where Ctrl+arrow moves an icon: the cell next to it that way, {row, column}, where that is on a grid {@code columns}
     * wide and empty; null where it is not - the edge, or another icon in the way. Down always has somewhere to go: the
     * grid keeps an empty row under its last icon.
     */
    static int[] nudged(Map<String,int[]> drawn,String id,Does move,int columns) {
        int[] here=drawn.get(id);
        if(here==null)return null;
        int row=here[0],column=here[1];
        switch(move) {
            case MOVE_LEFT: column--;break;
            case MOVE_RIGHT: column++;break;
            case MOVE_UP: row--;break;
            case MOVE_DOWN: row++;break;
            default: return null;
        }
        if(row<0||column<0||column>=Math.max(1,Math.min(Layout.WIDEST,columns)))return null;
        return Layout.at(drawn,row,column)==null?new int[]{row,column}:null;
    }

    /**
     * The cell under a point of a grid's view, {row, column}, whether or not anything is in it: {@code seen} is where the
     * point is in the part of the grid that shows, {@code scrolled} how far the grid is scrolled, and {@code left} and
     * {@code top} the room before its first column and its first row. A point in that room, or in the room past the last
     * column, takes the nearest cell, so an icon carried to the edge of the grid still has somewhere to go.
     */
    static int[] cellUnder(java.awt.Point seen,java.awt.Point scrolled,int left,int top,int columns) {
        int cols=Math.max(1,Math.min(Layout.WIDEST,columns));
        float x=Math.max(0,Math.min(cols*CELL-1,seen.x+scrolled.x-left)),y=Math.max(0,seen.y+scrolled.y-top);
        return Layout.under(x,y,CELL,HIGH,cols);
    }

    /** What the dock shows, and what the Favourites icon holds: the favourites it has no room for, and those taken out of it. */
    record Docked(List<NoteStore.Branch> shown,List<NoteStore.Branch> rest) {
        /** Whether the dock shows this thing: what "Remove from the dock" is offered on. */
        boolean shows(String id){for(NoteStore.Branch one:shown)if(one.id.equals(id))return true;return false;}
    }

    /**
     * The dock as this PC draws it (decision 3): the notebook's own dock - the phone's five - and then the favourites
     * beyond it, as many as there is room for; but never one taken out of the dock here, which waits in the Favourites
     * icon with whatever there is no room for. So every icon the dock draws can be taken out of it, and one taken out does
     * not come back into the room the PC's dock has to spare. The rest keep the order they were given in.
     *
     * @param keptOut the favourites taken out of this PC's dock, by id (see {@link #KEPT_OUT})
     */
    static Docked docked(List<NoteStore.Branch> dock,List<NoteStore.Branch> beyond,Set<String> keptOut,int room) {
        List<NoteStore.Branch> shown=new ArrayList<>(),rest=new ArrayList<>();
        for(NoteStore.Branch one:dock)(shown.size()<room?shown:rest).add(one);
        for(NoteStore.Branch one:beyond)(shown.size()<room&&!keptOut.contains(one.id)?shown:rest).add(one);
        return new Docked(shown,rest);
    }

    /** Where this PC keeps the favourites taken out of its dock: in its own settings, as the dock itself is kept only here. */
    static final String KEPT_OUT="keptOutOfDock";
    static Set<String> keptOut(org.mininotes.desktop.platform.content.Context context) {
        return context.getSharedPreferences("settings",0).getStringSet(KEPT_OUT,Set.of());
    }
    /** A favourite taken out of this PC's dock, or let back in: carried onto it, or starred or unstarred again. */
    static void keepOutOfDock(org.mininotes.desktop.platform.content.Context context,String id,boolean out) {
        var settings=context.getSharedPreferences("settings",0);
        Set<String> now=settings.getStringSet(KEPT_OUT,Set.of());
        if(out?now.add(id):now.remove(id))settings.edit().putStringSet(KEPT_OUT,now).apply();
    }

    /** How many things a collection's line says it holds - "2 collections · 3 notes" - so its face can show them. */
    static int countIn(String detail) {
        int count=0,number=0;String said=detail==null?"":detail;
        for(int at=0;at<=said.length();at++) {
            char digit=at<said.length()?said.charAt(at):' ';
            if(digit>='0'&&digit<='9')number=number*10+(digit-'0');
            else{count+=number;number=0;}
        }
        return count;
    }

    /**
     * A name in lines of a width, at most {@code most} of them: broken between words where it can be, inside a word too
     * long for a line where it must, and the last line ended with … when there is more than fits.
     */
    static List<String> lines(String name,FontMetrics m,int width,int most) {
        List<String> out=new ArrayList<>();
        String rest=name==null||name.isBlank()?"Untitled":name.trim().replaceAll("\\s+"," ");
        while(!rest.isEmpty()&&out.size()<most) {
            if(m.stringWidth(rest)<=width){out.add(rest);rest="";break;}
            int cut=rest.length();
            while(cut>1&&m.stringWidth(rest.substring(0,cut))>width)cut--;
            int space=rest.lastIndexOf(' ',cut);
            int end=space>0?space:cut;
            out.add(rest.substring(0,end));rest=rest.substring(end).trim();
        }
        if(!rest.isEmpty()&&!out.isEmpty()) {
            String last=out.get(out.size()-1)+" "+rest;
            while(last.length()>1&&m.stringWidth(last+"…")>width)last=last.substring(0,last.length()-1);
            out.set(out.size()-1,last.trim()+"…");
        }
        return out;
    }

    // ---- the screen ---------------------------------------------------------------------------------------------------

    final Desktop pad;
    /** Everything between the toolbar and the search: the grid, and over it the + and any card. */
    final JLayeredPane desk=new JLayeredPane(){
        @Override public void doLayout() {
            int w=getWidth(),h=getHeight();
            scroll.setBounds(0,0,w,h);if(pages!=null)pages.setBounds(0,0,w,h);
            Dimension p=plus.getPreferredSize();plus.setBounds(w-p.width-30,h-p.height-18,p.width,p.height);
            folder.setBounds(0,0,w,h);folder.doLayout();
        }
    };
    final Icons grid;
    final JScrollPane scroll;
    final JButton plus;
    final JTextField search=new JTextField();
    final JButton overviewButton;
    final Dock dock=new Dock();
    /** The row the dock stands in, shown or not (decision 47); and the foot it and the search stand in. */
    private JPanel dockRow,foot;
    /** Every page at once, zoomed out (decision 45). */
    final DesktopPages pages;
    /** Things made with + on a page other than the centre one, by id: they stand on that page, once they are read. */
    private final Map<String,int[]> placeNext=new HashMap<>();
    final DesktopFolder folder;
    final Carry carry=new Carry();
    /** What was found, while something is typed in the search: in the grid's place, where the eye already is. */
    private final JPanel results=new ResultList();
    /** Home's lines, and the favourites: those in the notebook's own dock (the phone's five), and those beyond it. */
    private List<NoteStore.Branch> homeLines=List.of(),docked=List.of(),beyond=List.of();
    /** The favourites taken out of this PC's dock, by id, as last read (see docked). */
    private Set<String> keptOut=Set.of();
    /** Where each place was pinned on Home at the last move there - its cell and its page - by id, as last read (see placeCells). */
    private Map<String,Integer> placeCells=Map.of(),placePages=Map.of();
    /** Whether the archive and the bin are on Home, and how many things wait in each, as last read (decision 41). */
    private boolean away=true;private int archived,binned;
    /** Which read is the newest, whether Home has been drawn once, and whether something changed while it could not be. */
    private int reads;private boolean drawn,stale;
    private final javax.swing.Timer looking;

    DesktopHome(Desktop pad) {
        super(new BorderLayout());this.pad=pad;
        setBackground(DesktopUi.PAPER);putClientProperty(KEEPS,Desktop.library());
        grid=new Icons(this,Where.HOME);grid.putClientProperty(KEEPS,Desktop.library());
        scroll=new JScrollPane(grid);scroll.setBorder(null);scroll.getViewport().setBackground(DesktopUi.PAPER);
        // Home is pages the size of the room, in every direction, not one grid that scrolls (decision 45).
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        plus=plusButton(()->Things.HOME);
        folder=new DesktopFolder(this);folder.setVisible(false);
        pages=new DesktopPages(this);pages.setVisible(false);
        desk.add(scroll,JLayeredPane.DEFAULT_LAYER);desk.add(plus,JLayeredPane.PALETTE_LAYER);desk.add(folder,JLayeredPane.MODAL_LAYER);desk.add(pages,JLayeredPane.POPUP_LAYER);
        add(desk);
        // A right-click on Home itself, not on an icon: what can be made here, and syncing everything.
        MouseAdapter room=new MouseAdapter(){
            public void mousePressed(MouseEvent e){if(e.isPopupTrigger())pad.roomMenu(Desktop.library(),e.getComponent(),e.getX(),e.getY());}
            public void mouseReleased(MouseEvent e){if(e.isPopupTrigger())pad.roomMenu(Desktop.library(),e.getComponent(),e.getX(),e.getY());}
        };
        grid.addMouseListener(room);scroll.getViewport().addMouseListener(room);
        turning();

        // The foot: the search with the overview button beside it, then the dock.
        search.putClientProperty(com.formdev.flatlaf.FlatClientProperties.PLACEHOLDER_TEXT,"Search notes, collections and files");
        search.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TEXT_FIELD_LEADING_ICON,new com.formdev.flatlaf.icons.FlatSearchIcon());
        search.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON,true);
        search.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE,"arc:999;margin:7,14,7,14");
        search.setToolTipText("Search everything (Ctrl+F)");search.getAccessibleContext().setAccessibleName("Search notes, collections and files");
        search.setColumns(34);
        looking=new javax.swing.Timer(200,e->look());looking.setRepeats(false);
        search.addFocusListener(new FocusAdapter(){public void focusLost(FocusEvent e){if(!e.isTemporary()&&search.getText().isBlank())showFoot();}});
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            public void insertUpdate(javax.swing.event.DocumentEvent e){looking.restart();}
            public void removeUpdate(javax.swing.event.DocumentEvent e){looking.restart();}
            public void changedUpdate(javax.swing.event.DocumentEvent e){looking.restart();}
        });
        // Down from the field goes into what was found; Enter opens the first of it.
        search.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DOWN"),"into");
        search.getActionMap().put("into",new AbstractAction(){public void actionPerformed(ActionEvent e){Component first=firstResult();if(first!=null)first.requestFocusInWindow();}});
        search.addActionListener(e->{if(firstResult() instanceof Row row)pad.openThing(row.thing);});
        overviewButton=new JButton(overviewIcon());
        overviewButton.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        overviewButton.setFocusPainted(false);overviewButton.setMargin(new Insets(7,9,7,9));
        overviewButton.setToolTipText("What is open (Ctrl+Tab)");overviewButton.getAccessibleContext().setAccessibleName("Show the notes and collections that are open");
        overviewButton.addActionListener(e->pad.overview.up(false));
        JPanel searchRow=new JPanel(new FlowLayout(FlowLayout.CENTER,8,0));searchRow.setOpaque(false);searchRow.add(search);searchRow.add(overviewButton);
        dockRow=new JPanel(new FlowLayout(FlowLayout.CENTER,0,0));dockRow.setOpaque(false);dockRow.add(dock);
        // As tall on every page as on the centre one, where the favourites and the search are, so every page is one size.
        foot=new JPanel(new BorderLayout(0,10)){
            int centre;
            @Override public Dimension getPreferredSize() {
                Dimension d=super.getPreferredSize();
                if(grid==null||grid.onCentre()){centre=d.height;return d;}
                return new Dimension(d.width,Math.max(d.height,centre));
            }
        };
        foot.setOpaque(false);foot.setBorder(BorderFactory.createEmptyBorder(8,16,12,16));
        foot.add(searchRow,BorderLayout.NORTH);foot.add(dockRow,BorderLayout.SOUTH);
        add(foot,BorderLayout.SOUTH);
        showFoot();paintRoom();
        // Wider or narrower, the dock may hold more or fewer, and the Favourites icon come or go with them: the grid is drawn
        // again only when it does, and the Favourites card when it is the one up.
        dockRow.addComponentListener(new ComponentAdapter(){int last=-1;public void componentResized(ComponentEvent e){
            int now=dockRoom(dockRow.getWidth()-32);if(now==last||!drawn)return;last=now;
            fillDock();
            boolean shows=!grid.tiles.isEmpty()&&grid.tiles.get(0).thing.kind==NoteStore.Branch.Kind.FAVOURITES;
            if(shows==overflow().isEmpty())fillGrid();
            if(folder.amongFavourites())refresh();
        }});
    }

    NoteStore store(){return pad.store;}

    /**
     * Favourites and the search shown or not, as Settings and Home's menu say (decision 47). The overview's button stays
     * beside where the search was: it is the way to what is open.
     */
    void showFoot() {
        // On the centre page only (decision 47); its room is kept on every other page, so every page is one size.
        boolean centre=grid==null||grid.onCentre();
        search.setVisible(centre&&pad.showing(Desktop.SHOW_SEARCH));
        dockRow.setVisible(centre&&pad.showing(Desktop.SHOW_DOCK));
        revalidate();repaint();
    }

    /**
     * How Home's pages are turned (decision 45): the wheel up and down, Shift with it - or a touchpad - sideways, Ctrl with
     * it out to every page; the empty room dragged, as a map is; Page Up and Page Down, Ctrl with them sideways; and a
     * double-click on the empty room, or Ctrl+Home, back to the centre page.
     */
    private void turning() {
        MouseWheelListener wheel=new MouseWheelListener(){
            double gathered;long turned;
            public void mouseWheelMoved(MouseWheelEvent e) {
                if(folder.isOpen()||carry.carrying())return;
                if(e.isControlDown()){if(e.getWheelRotation()>0)pages.up();return;}
                // A touchpad sends a stream of small turns: one page for a stroke, not one for each.
                long now=System.currentTimeMillis();
                if(now-turned<350){gathered=0;return;}
                gathered+=e.getPreciseWheelRotation();
                if(Math.abs(gathered)<1)return;
                int way=gathered>0?1:-1;gathered=0;turned=now;
                if(e.isShiftDown())grid.turn(way,0);else grid.turn(0,way);
            }
        };
        grid.addMouseWheelListener(wheel);scroll.addMouseWheelListener(wheel);
        MouseAdapter drag=new MouseAdapter(){
            java.awt.Point from;
            public void mousePressed(MouseEvent e){from=SwingUtilities.isLeftMouseButton(e)?e.getLocationOnScreen():null;}
            public void mouseReleased(MouseEvent e) {
                if(from==null||carry.carrying())return;
                int dx=e.getXOnScreen()-from.x,dy=e.getYOnScreen()-from.y;from=null;
                // Dragged as a map is: to the left shows what is to the right.
                if(Math.abs(dx)>90&&Math.abs(dx)>Math.abs(dy))grid.turn(dx<0?1:-1,0);
                else if(Math.abs(dy)>90)grid.turn(0,dy<0?1:-1);
            }
            public void mouseClicked(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e)&&e.getClickCount()==2)grid.showPage(0,0);}
        };
        grid.addMouseListener(drag);
        InputMap keys=getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);ActionMap does=getActionMap();
        String[][] turns={{"PAGE_UP","0","-1"},{"PAGE_DOWN","0","1"},{"control PAGE_UP","-1","0"},{"control PAGE_DOWN","1","0"}};
        for(String[] one:turns) {
            keys.put(KeyStroke.getKeyStroke(one[0]),"turn "+one[0]);
            int dx=Integer.parseInt(one[1]),dy=Integer.parseInt(one[2]);
            does.put("turn "+one[0],new AbstractAction(){public void actionPerformed(ActionEvent e){if(!folder.isOpen())grid.turn(dx,dy);}});
        }
        keys.put(KeyStroke.getKeyStroke("control HOME"),"centre");
        does.put("centre",new AbstractAction(){public void actionPerformed(ActionEvent e){if(!folder.isOpen())grid.showPage(0,0);}});
    }

    /** A page come into view: the foot as it is on that page, and the bar saying which page it is when it is not the centre. */
    void pageShown() {
        showFoot();
        pad.status.setToolTipText(null);
        pad.status.setText(grid.onCentre()?" ":DesktopPages.said(grid.pageX,grid.pageY)+"  ·  double-click to go back");
    }

    /** Something just made with + on the page in view: it stands on that page once Home has read it (decision 46). */
    void placeNew(String id){if(!grid.onCentre())placeNext.put(id,new int[]{grid.pageX,grid.pageY});}

    /** Home's room in Home's own colour, at the pad's strength, as the phone's Home is (decision 44). */
    void paintRoom() {
        Color paper=DesktopLook.wash(pad.homeColour(),DesktopUi.PAPER,0.12f,0.72f,pad.tone);
        setBackground(paper);scroll.getViewport().setBackground(paper);
        repaint();
    }

    /** Whether Home has been drawn from the notebook at least once: the first frame waits for it. */
    boolean drawn(){return drawn;}
    /** Whether the dock shows a thing, as it is drawn now: the menu offers to take it out. */
    boolean inDock(String id){return docked().shows(id);}
    /** Whether something is typed in the search. */
    boolean searching(){return !search.getText().isBlank();}

    // ---- reading it again ---------------------------------------------------------------------------------------------

    /**
     * Home read again, and the card and the dock with it: after anything changes, here or on another device. What the
     * reader is looking at does not move - each grid keeps where it was scrolled to and which icon has the keyboard, and
     * an open card stays open on the collection it shows - unless that collection has gone, which is then said. Not
     * while a thing is carried or a name is typed: it is read again once they are done.
     */
    void refresh() {
        if(carry.carrying()||folder.naming()){stale=true;return;}
        stale=false;int read=++reads;
        List<NoteStore.Branch> path=folder.path();
        pad.disk.submit(()->{
            Map<String,Object> got=new HashMap<>();
            got.put("home",store().contents(Things.HOME));
            got.put("docked",store().dock());got.put("beyond",store().favouritesBeyondDock());
            got.put("out",keptOut(pad.context));
            got.put("cells",placeCells(pad.context));got.put("pages",placePages(pad.context));
            // The archive and the bin, and how many things wait in each, for the count on each (decision 41).
            boolean onHome=pad.awayOnHome();got.put("away",onHome);
            if(onHome){got.put("archived",store().awayCount(false));got.put("binned",store().awayCount(true));}
            folder.read(path,got);
            return got;
        },got->{
            if(read!=reads)return;
            if(carry.carrying()||folder.naming()){stale=true;return;}
            @SuppressWarnings("unchecked") List<NoteStore.Branch> lines=(List<NoteStore.Branch>)got.get("home");
            @SuppressWarnings("unchecked") List<NoteStore.Branch> inDock=(List<NoteStore.Branch>)got.get("docked");
            @SuppressWarnings("unchecked") List<NoteStore.Branch> past=(List<NoteStore.Branch>)got.get("beyond");
            @SuppressWarnings("unchecked") Set<String> out=(Set<String>)got.get("out");
            homeLines=lines;docked=inDock;beyond=past;keptOut=out;
            @SuppressWarnings("unchecked") Map<String,Integer> cells=(Map<String,Integer>)got.get("cells");placeCells=cells;
            @SuppressWarnings("unchecked") Map<String,Integer> onPages=(Map<String,Integer>)got.get("pages");placePages=onPages;
            away=(Boolean)got.get("away");archived=(Integer)got.getOrDefault("archived",0);binned=(Integer)got.getOrDefault("binned",0);
            fillDock();fillGrid();folder.fill(path,got);
            drawn=true;
        },e->{drawn=true;pad.failed(e);});
    }

    /** How many favourites the dock shows now. */
    private int room(){Container row=dock.getParent();return dockRoom(row==null||row.getWidth()<=0?pad.frame.getWidth()-32:row.getWidth()-32);}
    /** The dock as it is drawn now, for the room it has (see docked). */
    private Docked docked(){return docked(docked,beyond,keptOut,room());}
    /** The favourites the dock has no room for, and those taken out of it: what the Favourites icon holds (decision 3). */
    List<NoteStore.Branch> overflow(){return docked().rest();}

    /**
     * Home's grid drawn again where it stands: the things, and the places among them - the Favourites icon while the dock
     * cannot show every favourite, the archive and the bin unless they are switched off - each where it was put (see
     * Layout.home). With nothing of the owner's yet, the words saying what to do are over the places.
     */
    private void fillGrid() {
        List<NoteStore.Branch> lines=new ArrayList<>();
        if(!overflow().isEmpty())lines.add(placed(favouritesPlace()));
        lines.addAll(homeLines);
        if(away){lines.add(placed(archivePlace(archived)));lines.add(placed(binPlace(binned)));}
        grid.fill(lines,homeLines.isEmpty()?"Nothing here yet. Click + to make a note or a collection.":null);
        placeMade();
        // The page in view gone empty - its last icon moved or put away - is gone: the centre page instead.
        if(!carry.carrying()&&!Layout.active(grid.spots()).contains(List.of(grid.pageX,grid.pageY)))grid.showPage(0,0);
        if(pages.isVisible())pages.repaint();
    }
    /** Those made with + on another page than the centre, put there now they are read: the first free cell of that page. */
    private void placeMade() {
        if(placeNext.isEmpty())return;
        for(Tile one:new ArrayList<>(grid.tiles)) {
            int[] on=placeNext.remove(one.thing.id);
            if(on==null||one.thing.cell>=0)continue;
            Map<String,Layout.Spot> others=new java.util.LinkedHashMap<>(grid.spots());others.remove(one.thing.id);
            Layout.Spot at=Layout.freeOn(others,on[0],on[1],grid.columns(),grid.rows());
            one.thing.cell=at.cell();one.thing.page=at.page();grid.rearrange();
            NoteStore.Branch line=one.thing;Map<String,Layout.Spot> spot=Map.of(line.id,at);
            pad.disk.submit(()->{store().placeOnPages(List.of(line),spot);return null;},done->{},e->{});
        }
    }
    /** A place with the page and the cell this PC keeps for it. */
    private NoteStore.Branch placed(NoteStore.Branch place) {
        Integer at=placeCells.get(place.id);place.cell=at==null?Layout.NONE:at;
        Integer on=placePages.get(place.id);place.page=on==null?Layout.NO_PAGE:on;
        return place;
    }
    private void fillDock(){dock.fill(docked().shown());}

    /** The Favourites collection, as the thing its icon stands for: a place, like the archive, not a collection. */
    static NoteStore.Branch favouritesPlace() {
        return new NoteStore.Branch(NoteStore.Branch.Kind.FAVOURITES,NoteStore.FAVOURITES,Sharing.EVERYTHING,"Favourites","",0,0,true);
    }
    /** The archive, as Home shows it, with how many things are in it - which is all its line says (decision 41). */
    static NoteStore.Branch archivePlace(int count){return place(NoteStore.Branch.Kind.ARCHIVE,NoteStore.ARCHIVE,"Archive",count);}
    /** The bin, as Home shows it, with how many things are in it. */
    static NoteStore.Branch binPlace(int count){return place(NoteStore.Branch.Kind.BIN,NoteStore.BIN,"Bin",count);}
    private static NoteStore.Branch place(NoteStore.Branch.Kind kind,String id,String name,int count) {
        return new NoteStore.Branch(kind,id,Sharing.EVERYTHING,name,count<=0?"":count==1?"1 thing":count+" things",0,0,true);
    }
    /** The archive's and the bin's icons in the Lucide set, as the phone draws them (the set's "trash" has lines in it). */
    static final String ARCHIVE_ICON="archive",BIN_ICON="trash";

    // ---- opening ------------------------------------------------------------------------------------------------------

    /** An icon clicked or Entered: a note opens, a collection opens its card - in the same card from inside one - a file opens. */
    void opened(NoteStore.Branch thing,Where where) {
        switch(thing.kind) {
            case PAGE: pad.save(()->pad.open(thing.id));return;
            case FILE: openFile(thing);return;
            case FAVOURITES: folder.openFavourites();return;
            case ARCHIVE: case BIN: folder.openPlace(thing);return;
            case COLLECTION: case BOOK:
                if(where==Where.CARD)folder.into(thing);
                // A favourite opens where it really lives, with ‹ going up from there.
                else if(where==Where.FAVOURITES||where==Where.DOCK)folder.openAt(thing.id);
                else folder.open(thing);
                return;
            default:
        }
    }

    /** A file opened where it is kept: handed to whatever opens it on this PC, and no longer new. */
    void openFile(NoteStore.Branch file) {
        pad.status.setToolTipText(null);pad.status.setText("Opening “"+file.name+"”…");
        pad.disk.submit(()->{
            NoteStore.Held held=store().file(file.id);
            if(held==null)return null;
            if(held.fresh)store().opened(held.id);
            Path room=Files.createTempDirectory("mininotes-");room.toFile().deleteOnExit();
            Path copy=room.resolve(DesktopFiles.onDisk(held.name));
            try(OutputStream out=Files.newOutputStream(copy)){DesktopFiles.copyOut(pad.context,store().fileFor(held.id).toPath(),out);}
            copy.toFile().deleteOnExit();
            return copy;
        },copy->{
            if(copy==null){pad.status.setText("That file is not here any more.");refresh();return;}
            try{java.awt.Desktop.getDesktop().open(copy.toFile());pad.status.setText("“"+file.name+"” is open in its own program");}
            catch(Exception none){pad.status.setText("Nothing on this PC opens "+file.name+". Save a copy to keep it somewhere.");}
            if(file.fresh)refresh();
        },pad::failed);
    }

    /**
     * What the Share button means while Home is showing: the open card's collection, or else the icon that has the
     * keyboard. Null when neither is a note or a collection.
     */
    NoteStore.Branch chosen() {
        // The icon that had the keyboard last: pressing Share has just taken the keyboard to the button.
        Tile last=lastTile;
        if(last!=null&&last.isShowing()&&last.thing.scope()!=null&&(!folder.isOpen()||SwingUtilities.isDescendingFrom(last,folder)))return last.thing;
        return folder.shown();
    }
    /** The icon that had the keyboard last, for chosen(). */
    private Tile lastTile;

    /** The icon of this thing, on Home or in the card, given the keyboard: where the reader was when they went to it. */
    void focusThing(String id) {
        if(id==null)return;
        Icons in=folder.isOpen()?folder.grid:grid;
        for(Tile one:in.tiles)if(one.thing.id.equals(id)){one.requestFocusInWindow();return;}
    }

    /** The keyboard to the first icon on Home, or to the + where there is none: where a window opening on Home starts. */
    void focusFirst(){Tile first=grid.first();if(first!=null)first.requestFocusInWindow();else plus.requestFocusInWindow();}

    /** The keyboard to the search, with what is there chosen so typing replaces it. */
    void focusSearch() {
        // Hidden in Settings, it comes for as long as it is used, and goes again once it is emptied and left (decision 47).
        if(!search.isVisible()){search.setVisible(true);revalidate();}
        search.requestFocusInWindow();search.selectAll();
    }

    /**
     * Esc on Home: out of the card one level, then the search emptied. Whether it did something; a carried thing and a
     * name being typed take Esc for themselves before it gets here.
     */
    boolean escape() {
        if(folder.isOpen()){folder.up();return true;}
        if(searching()){search.setText("");search.requestFocusInWindow();return true;}
        return false;
    }

    // ---- making -------------------------------------------------------------------------------------------------------

    /**
     * The round +, with no word on it, as the phone's is, named for anybody who cannot see it. It offers the two things
     * there are, for where it is: Home's makes them on Home, a card's in that collection.
     */
    JButton plusButton(Supplier<String> where) {
        JButton plus=new JButton(){
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int d=Math.min(getWidth(),getHeight())-8,x=(getWidth()-d)/2,y=(getHeight()-d)/2-1;
                // No shadow: drawn inside the button's own square it was cut off at the foot (the owner, 2026-10-01).
                g.setColor(getModel().isPressed()?new Color(34,74,53):getModel().isRollover()?new Color(42,88,64):DesktopUi.ACCENT);g.fillOval(x,y,d,d);
                if(ringed(this)){g.setColor(DesktopUi.mix(DesktopUi.ACCENT,Color.WHITE,0.55f));g.setStroke(new BasicStroke(2f));g.drawOval(x-3,y-3,d+5,d+5);}
                g.setColor(Color.WHITE);g.setStroke(new BasicStroke(2.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                int cx=x+d/2,cy=y+d/2,arm=d/5;g.drawLine(cx-arm,cy,cx+arm,cy);g.drawLine(cx,cy-arm,cx,cy+arm);
                g.dispose();
            }
        };
        plus.setContentAreaFilled(false);plus.setBorderPainted(false);plus.setFocusPainted(false);plus.setOpaque(false);
        plus.setPreferredSize(new Dimension(64,64));plus.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        plus.setToolTipText("New note or collection, or one from another device");plus.getAccessibleContext().setAccessibleName("New");
        plus.addActionListener(e->{
            JPopupMenu menu=plusMenu(where.get());
            Dimension m=menu.getPreferredSize();menu.show(plus,plus.getWidth()-m.width-6,-m.height-2);
        });
        return plus;
    }

    /**
     * What a + offers where it is: a note and a collection made there; and on every +, something from another device - a
     * note or a collection someone shows the code of, scanned or pasted - which arrives on Home (the owner: "this is key").
     */
    JPopupMenu plusMenu(String where) {
        JPopupMenu menu=new JPopupMenu();
        JMenuItem note=new JMenuItem("Note");note.addActionListener(a->pad.newNoteIn(where));menu.add(note);
        JMenuItem collection=new JMenuItem("Collection");collection.addActionListener(a->newCollection(where));menu.add(collection);
        menu.addSeparator();JMenuItem other=new JMenuItem("From another device…");other.addActionListener(a->pad.scanCode(null));menu.add(other);
        return menu;
    }

    /**
     * A new collection where the + was pressed, made as Untitled, and its card opened with the name ready to be typed
     * over, since nothing else can say what it is called.
     */
    void newCollection(String where) {
        pad.save(()->pad.disk.submit(()->store().addCollectionIn(where,""),made->{
            if(NoteStore.home(where))placeNew(made.id);
            NoteStore.Branch line=new NoteStore.Branch(NoteStore.Branch.Kind.COLLECTION,made.id,NoteStore.home(where)?Sharing.EVERYTHING:where,made.name,"Empty",0,0,true);
            pad.showHome();
            if(NoteStore.home(where))folder.open(line,true);
            else if(folder.isOpen()&&where.equals(folder.id()))folder.into(line,true);
            else folder.openAt(line.id,true);
            pad.refresh();
        },e->pad.status.setText("Could not make that collection. Nothing was changed.")));
    }

    // ---- files on Home and in collections ------------------------------------------------------------------------------

    /**
     * A file's menu, where it is held, in the phone's order: what a click does first, then what else can be done with it,
     * and deleting it last, under the line, since that is the one thing here that cannot be taken back.
     */
    JPopupMenu fileMenu(NoteStore.Branch file) {
        JPopupMenu menu=new JPopupMenu();
        item(menu,"Open",()->openFile(file));
        item(menu,"Save a copy…",()->withHeld(file,pad::saveCopy));
        item(menu,"Send to a device…",()->withHeld(file,held->pad.fileCards.send(held)));
        item(menu,"Put in a note…",()->intoNote(file));
        item(menu,"Move to…",()->moveFile(file));
        menu.addSeparator();
        item(menu,"Delete",()->deleteFile(file));
        return menu;
    }
    private static void item(JPopupMenu menu,String said,Runnable does){JMenuItem one=new JMenuItem(said);one.addActionListener(e->does.run());menu.add(one);}

    /** One kept file, read from the notebook, for what needs more of it than its line says. */
    private void withHeld(NoteStore.Branch file,java.util.function.Consumer<NoteStore.Held> then) {
        pad.disk.submit(()->store().file(file.id),held->{
            if(held==null){pad.status.setText("That file is not here any more.");refresh();return;}
            then.accept(held);
        },pad::failed);
    }

    /** Put in a note: one of the notes written in lately, or a new one. It is that note's attachment from then on. */
    private void intoNote(NoteStore.Branch file) {
        pad.disk.submit(()->store().lately(Given.RECENT*3),lately->{
            List<NoteStore.Branch> choices=new ArrayList<>();
            choices.add(new NoteStore.Branch(NoteStore.Branch.Kind.PAGE,"","","New note","",0,0,false));
            for(NoteStore.Branch one:lately)if(choices.size()<=12)choices.add(one);
            NoteStore.Branch into=DesktopUi.pick(pad.frame,"Put in a note",file.name,choices,
                b->b.id.isEmpty()?"New note":b.name+(b.detail.isEmpty()?"":"   ·   "+b.detail),"Put it there");
            if(into==null)return;
            pad.disk.submit(()->{
                String note=into.id;
                if(note.isEmpty()) {
                    NoteStore.Note fresh=new NoteStore.Note();fresh.book=Things.HOME;
                    int dot=file.name.lastIndexOf('.');fresh.title=dot>0?file.name.substring(0,dot):file.name;store().save(fresh);note=fresh.id;
                } else if(store().onlyReads(note))throw new IllegalStateException("That note is read only. Nothing was changed.");
                store().intoNote(file.id,note);
                return note;
            },note->{pad.status.setText(file.name+" is in the note now");pad.filesChanged(note);pad.refresh();pad.open(note);},pad::failed);
        },pad::failed);
    }

    /** Move to…: Home, or any collection, with where each is. */
    private void moveFile(NoteStore.Branch file) {
        pad.disk.submit(()->store().places(false),places->{
            List<NoteStore.Branch> offered=new ArrayList<>();
            for(NoteStore.Branch one:places)if(!(NoteStore.home(file.parent)?NoteStore.home(one.id):one.id.equals(file.parent)))offered.add(one);
            if(offered.isEmpty()){pad.status.setText("There is nowhere else to move it.");return;}
            NoteStore.Branch into=DesktopUi.pick(pad.frame,"Move “"+file.name+"”","Where to?",offered,Desktop::placeName,"Move here");
            if(into==null)return;
            boolean top=into.kind==NoteStore.Branch.Kind.LIBRARY;
            into(file,top?Things.HOME:into.id,top?"Home":into.name,NoteStore.Branch.Kind.COLLECTION,null);
        },pad::failed);
    }

    /** Deleted from this PC, after one question: there is no bin for files. */
    void deleteFile(NoteStore.Branch file) {
        String came=file.origin.isEmpty()?"":" Whoever sent it still has theirs.";
        if(!DesktopUi.confirm(pad.frame,"Delete “"+file.name+"”?","It is deleted from this PC."+came,"Delete",true))return;
        pad.disk.submit(()->{
            NoteStore.Held held=store().file(file.id);
            store().drop(file.id);
            // One kept with a note - found by a search - changes the list of files that travels with the note.
            return held!=null&&held.held==NoteStore.Branch.Kind.PAGE?held.note:"";
        },note->{pad.status.setText("Deleted");pad.refresh();if(!note.isEmpty())pad.filesChanged(note);},pad::failed);
    }

    // ---- moving, merging, the order, the dock --------------------------------------------------------------------------

    /**
     * The collections above a thing and then the thing itself: what a sharing rule is looked for along. A file's is what
     * keeps it - Home, a collection or a note - and then the file, since a file has no place in the tree of its own.
     */
    private List<String> pathOf(NoteStore.Branch line) {
        if(line.kind!=NoteStore.Branch.Kind.FILE)return store().pathOf(line.id);
        List<String> path=NoteStore.home(line.parent)?new ArrayList<>():store().pathOf(line.parent);
        path.add(line.id);
        return path;
    }
    /** Where a thing would be, once it is inside {@code into}: the path down to that, and then it. */
    private List<String> pathInto(String into,String id) {
        List<String> path=NoteStore.home(into)?new ArrayList<>():store().pathOf(into);
        path.add(id);
        return path;
    }
    /** Names of the devices by address, for the words that say who a move reaches. */
    private Map<String,String> names() {
        Map<String,String> names=new HashMap<>();
        for(NoteStore.Contact contact:store().addresses())names.put(contact.address,contact.name);
        return names;
    }
    /** Who a move starts and stops reaching, by name, between a first line and a last - as the Move box has always said it. */
    static String changeSaid(String first,Sharing.Change change,Map<String,String> names,String last) {
        StringBuilder said=new StringBuilder(first);
        if(!change.gained.isEmpty()){said.append("\n\nStarts reaching: ");said.append(String.join(", ",change.gained.keySet().stream().map(a->names.getOrDefault(a,"a paired device")).toList()));}
        if(!change.lost.isEmpty()){said.append("\n\nStops reaching: ");said.append(String.join(", ",change.lost.keySet().stream().map(a->names.getOrDefault(a,"a paired device")).toList()));}
        return said.append("\n\n").append(last).toString();
    }

    /**
     * Into a collection or onto Home, or a file into a note - or up a level, out of a card. What changes who it reaches
     * is asked first, as every move is; what the notebook refuses, such as a collection into its own inside, is said as
     * it says it.
     *
     * @param after what to do once it has gone, or null for nothing more than drawing Home again
     */
    void into(NoteStore.Branch moved,String dest,String destName,NoteStore.Branch.Kind destKind,Runnable after) {
        pad.disk.submit(()->{
            List<Sharing.Rule> rules=store().shares();
            List<String> from=pathOf(moved),to=pathInto(dest,moved.id);
            Sharing.Change change=Sharing.moving(rules,from,to);
            boolean reaches=!Sharing.audience(rules,from).isEmpty()||!Sharing.audience(rules,to).isEmpty();
            return new Object[]{change,names(),reaches};
        },found->{
            Sharing.Change change=(Sharing.Change)found[0];boolean reaches=(Boolean)found[2];
            @SuppressWarnings("unchecked") Map<String,String> names=(Map<String,String>)found[1];
            String where=NoteStore.home(dest)?"Home":destName;
            if(change.any()&&!DesktopUi.confirm(pad.frame,"This changes who can read it",changeSaid("Moving “"+named(moved)+"” into "+where+" changes who receives it.",change,names,
                    "Whoever starts receiving it gets it now. What has already reached somebody stays with them."),"Move anyway",false)){pad.status.setText("Not moved");refresh();return;}
            doInto(moved,dest,where,destKind,reaches,after);
        },pad::failed);
    }

    private void doInto(NoteStore.Branch moved,String dest,String where,NoteStore.Branch.Kind destKind,boolean reaches,Runnable after) {
        pad.disk.submit(()->{
            // Where it was, before it is anywhere else: an undo has to know the place to put it back into.
            String was;
            if(moved.kind==NoteStore.Branch.Kind.FILE){NoteStore.Held held=store().file(moved.id);was=held==null?Things.HOME:held.note;}
            else was=moved.kind==NoteStore.Branch.Kind.PAGE?store().bookOf(moved.id):store().collectionOfBook(moved.id);
            store().moveInto(moved.kind,moved.id,dest);
            return was==null||was.isEmpty()?Things.HOME:was;
        },was->{
            pad.canUndo(named(moved),()->{store().moveInto(moved.kind,moved.id,was);return null;});
            pad.movedNote(moved,dest);
            pad.status.setToolTipText(null);pad.status.setText(NoteStore.home(dest)?"Moved to Home":"Moved into "+where);
            if(after!=null)after.run();
            pad.refresh();
            // Into something somebody else reads is a disclosure: it goes now, as sharing does.
            if(moved.kind==NoteStore.Branch.Kind.FILE){if(destKind==NoteStore.Branch.Kind.PAGE)pad.filesChanged(dest);}
            else if(reaches)pad.sendChanged(moved.kind,moved.id);
        },e->{pad.status.setText(e instanceof IllegalArgumentException&&e.getMessage()!=null?e.getMessage():"Could not move that. Nothing was changed.");pad.refresh();});
    }

    /**
     * A note let go on a note: a new collection, Untitled, where the one let go on was, holding both - and its card
     * opened with the name ready to be typed over. Asked first if it changes who either of them reaches.
     */
    void merge(NoteStore.Branch dropped,NoteStore.Branch onto,String container,boolean inCard) {
        pad.disk.submit(()->{
            List<Sharing.Rule> rules=store().shares();
            Map<String,Boolean> gained=new java.util.LinkedHashMap<>(),lost=new java.util.LinkedHashMap<>();
            boolean reaches=false;
            for(NoteStore.Branch thing:new NoteStore.Branch[]{dropped,onto}) {
                List<String> from=store().pathOf(thing.id),to=pathInto(container,thing.id);
                Sharing.Change change=Sharing.moving(rules,from,to);
                gained.putAll(change.gained);lost.putAll(change.lost);
                if(!Sharing.audience(rules,from).isEmpty())reaches=true;
            }
            return new Object[]{new Sharing.Change(gained,lost),names(),reaches};
        },found->{
            Sharing.Change change=(Sharing.Change)found[0];boolean reaches=(Boolean)found[2];
            @SuppressWarnings("unchecked") Map<String,String> names=(Map<String,String>)found[1];
            if(change.any()&&!DesktopUi.confirm(pad.frame,"This changes who can read them",changeSaid("Putting “"+named(dropped)+"” and “"+named(onto)+"” together changes who receives them.",change,names,
                    "Whoever starts receiving them gets them now. What has already reached somebody stays with them."),"Put them together",false)){pad.status.setText("Not moved");refresh();return;}
            pad.disk.submit(()->store().merge(dropped.id,onto.id),made->{
                NoteStore.Branch line=new NoteStore.Branch(NoteStore.Branch.Kind.COLLECTION,made.id,NoteStore.home(container)?Sharing.EVERYTHING:container,made.name,"2 notes",0,0,true);
                pad.status.setToolTipText(null);pad.status.setText("Put together in a new collection. Type its name.");
                if(inCard)folder.into(line,true);else folder.open(line,true);
                pad.refresh();
                if(reaches)pad.sendChanged(NoteStore.Branch.Kind.COLLECTION,made.id);
            },e->{pad.status.setText(e instanceof IllegalArgumentException&&e.getMessage()!=null?e.getMessage():"Could not put those together. Nothing was changed.");pad.refresh();});
        },pad::failed);
    }

    /** Whether a grid keeps where its icons are put: Home's and a collection's card; a place's card only lists them. */
    boolean places(Icons in){return in!=null&&(in==grid||folder!=null&&in==folder.grid&&!folder.listing());}

    /**
     * Home's places, by id: each keeps where it stands on Home in this PC's settings, as the phone keeps them, having no row
     * to keep it in - "favouritesCell", "archiveCell", "binCell".
     */
    static final String[] PLACES={NoteStore.FAVOURITES,NoteStore.ARCHIVE,NoteStore.BIN};
    static Map<String,Integer> placeCells(org.mininotes.desktop.platform.content.Context context) {
        var settings=context.getSharedPreferences("settings",0);
        Map<String,Integer> cells=new HashMap<>();
        for(String id:PLACES)cells.put(id,(int)settings.getLong(id+"Cell",Layout.NONE));
        return cells;
    }
    /** A grid's cells written, the places' among them kept too, so none jumps into a gap. On the disk's thread. */
    private void writeCells(List<NoteStore.Branch> lines,Map<String,Integer> cells) {
        store().place(lines,cells);
        var kept=pad.context.getSharedPreferences("settings",0).edit();boolean any=false;
        for(String id:PLACES){Integer at=cells.get(id);if(at!=null){kept.putLong(id+"Cell",at);any=true;}}
        if(any)kept.apply();
    }
    /** Home's icons written where they stand, each on its page, the places' in this PC's settings. On the disk's thread. */
    private void writeSpots(List<NoteStore.Branch> lines,Map<String,Layout.Spot> spots) {
        store().placeOnPages(lines,spots);
        var kept=pad.context.getSharedPreferences("settings",0).edit();boolean any=false;
        for(String id:PLACES){Layout.Spot at=spots.get(id);if(at!=null){kept.putLong(id+"Cell",at.cell());kept.putLong(id+"Page",at.page());any=true;}}
        if(any)kept.apply();
    }
    /** Home's icons put back as they were, a place each or none, the places' in this PC's settings. On the disk's thread. */
    private void writeWere(List<NoteStore.Branch> lines,Map<String,Integer> cells,Map<String,Integer> pages) {
        store().placeOnPages(lines,cells,pages);
        var kept=pad.context.getSharedPreferences("settings",0).edit();boolean any=false;
        for(String id:PLACES){Integer at=cells.get(id),on=pages.get(id);if(at!=null&&on!=null){kept.putLong(id+"Cell",at);kept.putLong(id+"Page",on);any=true;}}
        if(any)kept.apply();
    }
    /** The page each place was pinned on, by id, from this PC's settings: none for one kept before pages. */
    static Map<String,Integer> placePages(org.mininotes.desktop.platform.content.Context context) {
        var settings=context.getSharedPreferences("settings",0);
        Map<String,Integer> on=new HashMap<>();
        for(String id:PLACES)on.put(id,(int)settings.getLong(id+"Page",Layout.NO_PAGE));
        return on;
    }

    /**
     * A grid's icons where they were left (decision 39): every one pinned where it is drawn and the one moved in its new
     * cell, as {@link Layout#moveTo} gives them - shown at once, so it does not jump back while the notebook is written,
     * then written in one go, one Undo from where they all were.
     */
    private void keepCells(Icons in,NoteStore.Branch moved,Map<String,Integer> cells,String said) {
        if(in.paged()){keepSpots(in,moved,spotsFrom(in,cells),said);return;}
        List<NoteStore.Branch> lines=new ArrayList<>();Map<String,Integer> before=new HashMap<>();
        for(Tile one:in.tiles){lines.add(one.thing);before.put(one.thing.id,one.thing.cell);}
        for(Tile one:in.tiles){Integer now=cells.get(one.thing.id);if(now!=null)one.thing.cell=now;}
        in.rearrange();
        pad.disk.submit(()->{writeCells(lines,cells);return null;},done->{
            pad.canUndo(named(moved),()->{writeCells(lines,before);return null;});
            pad.status.setToolTipText(null);pad.status.setText(said);pad.refresh();
        },e->{pad.status.setText("Could not put it there. Nothing was changed.");pad.refresh();});
    }

    /** Cells on the page in view, as Home keeps them: every icon pinned where it stands on its page, those given here on this one. */
    private static Map<String,Layout.Spot> spotsFrom(Icons in,Map<String,Integer> cells) {
        Map<String,Layout.Spot> all=new java.util.LinkedHashMap<>(in.spots());
        for(Map.Entry<String,Integer> one:cells.entrySet())
            all.put(one.getKey(),new Layout.Spot(in.pageX,in.pageY,Layout.row(one.getValue()),Layout.column(one.getValue())));
        return all;
    }
    /**
     * Home's icons where they were left, on every page (decision 50): shown at once, then written in one go, one Undo from
     * where they all were.
     */
    private void keepSpots(Icons in,NoteStore.Branch moved,Map<String,Layout.Spot> spots,String said){keepSpots(in,named(moved),spots,said);}
    /**
     * A whole page carried in the view of every page to another place (Layout.movePage): written as any move on Home is,
     * one Undo from where they all were, and said.
     */
    void movePage(int fx,int fy,int tx,int ty) {
        Map<String,Layout.Spot> before=grid.spots();
        boolean swapped=before.values().stream().anyMatch(at->at!=null&&at.on(tx,ty));
        keepSpots(grid,"the page",Layout.movePage(before,fx,fy,tx,ty),swapped?"The two pages changed places":"Page moved");
        if(grid.pageX==fx&&grid.pageY==fy)grid.showPage(tx,ty);
        pages.repaint();
    }
    private void keepSpots(Icons in,String undone,Map<String,Layout.Spot> spots,String said) {
        // Undo puts back what each had, a place of its own or none - not where it happened to be drawn.
        List<NoteStore.Branch> lines=new ArrayList<>();Map<String,Integer> cellsWere=new HashMap<>(),pagesWere=new HashMap<>();
        for(Tile one:in.tiles){lines.add(one.thing);cellsWere.put(one.thing.id,one.thing.cell);pagesWere.put(one.thing.id,one.thing.page);}
        for(Tile one:in.tiles){Layout.Spot now=spots.get(one.thing.id);if(now!=null){one.thing.cell=now.cell();one.thing.page=now.page();}}
        in.rearrange();
        pad.disk.submit(()->{writeSpots(lines,spots);return null;},done->{
            pad.canUndo(undone,()->{writeWere(lines,cellsWere,pagesWere);return null;});
            pad.status.setToolTipText(null);pad.status.setText(said);pad.refresh();
        },e->{pad.status.setText("Could not put it there. Nothing was changed.");pad.refresh();});
    }

    /**
     * Ctrl+arrow on an icon: one cell that way, where that cell is empty - the keyboard's way to put a thing where it is
     * wanted. The keyboard stays on it; what stops it is said.
     */
    private void nudge(Icons in,Tile tile,Does way) {
        if(!places(in)||!Grid.carried(tile.thing.kind)||Grid.place(tile.thing.kind)&&in!=grid){pad.status.setToolTipText(null);pad.status.setText("This one stays where it is");return;}
        Map<String,int[]> drawn=in.drawn();
        int[] to=nudged(drawn,tile.thing.id,way,in.columns());
        // On a page of Home, not past its last row.
        if(to!=null&&in.paged()&&to[0]>=in.rows())to=null;
        if(to==null) {
            int[] here=drawn.get(tile.thing.id);
            int row=here==null?-1:here[0]+(way==Does.MOVE_DOWN?1:way==Does.MOVE_UP?-1:0),column=here==null?-1:here[1]+(way==Does.MOVE_RIGHT?1:way==Does.MOVE_LEFT?-1:0);
            String there=row<0||column<0?null:Layout.at(drawn,row,column);
            Tile other=null;if(there!=null)for(Tile one:in.tiles)if(one.thing.id.equals(there))other=one;
            pad.status.setToolTipText(null);pad.status.setText(other!=null?"“"+named(other.thing)+"” is in the way":"It is at the edge of the grid");
            return;
        }
        keepCells(in,tile.thing,Layout.moveTo(drawn,tile.thing.id,to[0],to[1]),"“"+named(tile.thing)+"” is in row "+(to[0]+1)+", column "+(to[1]+1)+" now");
        tile.requestFocusInWindow();tile.scrollRectToVisible(new Rectangle(tile.getSize()));
    }

    /** A favourite at a place in the dock: made one if it was not; whatever is pushed past the end stays one. */
    private void toDock(NoteStore.Branch thing,int slot) {
        pad.disk.submit(()->{store().toDock(thing.kind,thing.id,slot);keepOutOfDock(pad.context,thing.id,false);return null;},
            done->{pad.status.setToolTipText(null);pad.status.setText("“"+named(thing)+"” is in the dock");pad.refresh();},
            e->{pad.status.setText("Could not put that in the dock. Nothing was changed.");pad.refresh();});
    }

    static String named(NoteStore.Branch b){return b.name==null||b.name.isBlank()?"Untitled":b.name;}

    // ---- searching ----------------------------------------------------------------------------------------------------

    /**
     * What is typed looked for as it is typed - a moment after the last key, so a fast typist asks the notebook once -
     * and shown in the grid's place: notes, collections and files alike, each saying where it is. Emptied, the grid is back
     * where it was scrolled to.
     */
    private void look() {
        String term=search.getText().trim();
        if(term.length()<2){showGrid();return;}
        if(folder.isOpen())folder.close();
        pad.disk.submit(()->store().lookingEverywhere(term),hits->{
            if(!search.getText().trim().equals(term))return;
            results.removeAll();
            if(hits.isEmpty())DesktopUi.add(results,DesktopUi.note("Nothing found for “"+term+"”.",420,DesktopUi.QUIET,DesktopUi.BODY.deriveFont(13f)));
            for(NoteStore.Branch hit:hits)DesktopUi.add(results,new Row(hit));
            if(scroll.getViewport().getView()!=results){gridAt=scroll.getViewport().getViewPosition();scroll.setViewportView(results);}
            results.revalidate();results.repaint();scroll.getViewport().setViewPosition(new java.awt.Point(0,0));
        },pad::failed);
    }
    private java.awt.Point gridAt=new java.awt.Point(0,0);
    private void showGrid() {
        if(scroll.getViewport().getView()==grid)return;
        scroll.setViewportView(grid);java.awt.Point back=gridAt;SwingUtilities.invokeLater(()->scroll.getViewport().setViewPosition(back));
    }
    private Component firstResult(){for(Component c:results.getComponents())if(c instanceof Row)return c;return null;}

    /** The results: a column as wide as the window allows, in the middle of it. */
    private static final class ResultList extends JPanel implements Scrollable {
        ResultList(){setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));setOpaque(false);}
        /** Room at the sides for a column no wider than a line is read at, however wide the window. */
        @Override public Insets getInsets(){int side=Math.max(SIDE,(getWidth()-720)/2);return new Insets(TOP,side,UNDER,side);}
        public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        public int getScrollableUnitIncrement(Rectangle r,int o,int d){return 24;}
        public int getScrollableBlockIncrement(Rectangle r,int o,int d){return Math.max(24,r.height-48);}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return false;}
    }

    /** One thing a word was found in: its small face, what it is called, and where it is with the words around it. */
    final class Row extends JPanel {
        final NoteStore.Branch thing;boolean over;
        Row(NoteStore.Branch thing) {
            super(new BorderLayout(12,0));this.thing=thing;setOpaque(false);setFocusable(true);
            setBorder(BorderFactory.createEmptyBorder(8,10,8,10));setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            putClientProperty(THING,thing);putClientProperty(MENU,(Supplier<JPopupMenu>)()->menuFor(thing));
            JComponent face=new JComponent(){
                {setPreferredSize(new Dimension(36,36));}
                @Override protected void paintComponent(Graphics g0){Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);face(g,thing,0,0,36,pad.tone);g.dispose();}
            };
            JPanel faceHolder=new JPanel(new GridBagLayout());faceHolder.setOpaque(false);faceHolder.add(face);add(faceHolder,BorderLayout.WEST);
            JPanel words=DesktopUi.column();
            JLabel name=new JLabel(named(thing));name.setFont(DesktopUi.BODY);name.setForeground(DesktopUi.INK);DesktopUi.add(words,name);
            if(thing.detail!=null&&!thing.detail.isBlank()){JLabel where=new JLabel(thing.detail);where.setFont(DesktopUi.BODY.deriveFont(13f));where.setForeground(DesktopUi.QUIET);DesktopUi.add(words,where);}
            add(words);
            getAccessibleContext().setAccessibleName(said(thing)+(thing.detail==null||thing.detail.isBlank()?"":", "+thing.detail));
            MouseAdapter hand=new MouseAdapter(){
                public void mouseClicked(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e))pad.openThing(thing);}
                public void mousePressed(MouseEvent e){requestFocusInWindow();if(e.isPopupTrigger())menuFor(thing).show(e.getComponent(),e.getX(),e.getY());}
                public void mouseReleased(MouseEvent e){if(e.isPopupTrigger())menuFor(thing).show(e.getComponent(),e.getX(),e.getY());}
                public void mouseEntered(MouseEvent e){over=true;repaint();}
                public void mouseExited(MouseEvent e){over=false;repaint();}
            };
            // The words and the face take the same clicks as the line, so a line is one thing to point at.
            for(Component part:new Component[]{this,faceHolder,face,words})part.addMouseListener(hand);
            for(Component part:words.getComponents())part.addMouseListener(hand);
            addFocusListener(new FocusAdapter(){public void focusGained(FocusEvent e){repaint();scrollRectToVisible(new Rectangle(getSize()));}public void focusLost(FocusEvent e){repaint();}});
            addKeyListener(new KeyAdapter(){public void keyPressed(KeyEvent e){
                Does does=does(e.getKeyCode(),e.isShiftDown(),e.isControlDown()||e.isAltDown());
                Component[] all=results.getComponents();int at=java.util.Arrays.asList(all).indexOf(Row.this);
                switch(does) {
                    case OPEN: pad.openThing(thing);break;
                    case MENU: menuFor(thing).show(Row.this,12,getHeight()-6);break;
                    case UP: if(at>0&&all[at-1] instanceof Row up)up.requestFocusInWindow();else search.requestFocusInWindow();break;
                    case DOWN: if(at+1<all.length)all[at+1].requestFocusInWindow();break;
                    default: return;
                }
                e.consume();
            }});
        }
        @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
        @Override protected void paintComponent(Graphics g0) {
            boolean ring=ringed(this);
            if(!over&&!ring)return;
            Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(DesktopUi.mix(DesktopUi.ACCENT,DesktopUi.PAPER,0.92f));g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
            if(ring){g.setColor(DesktopUi.ACCENT);g.setStroke(new BasicStroke(1.5f));g.drawRoundRect(1,1,getWidth()-3,getHeight()-3,12,12);}
            g.dispose();
        }
    }

    /**
     * A thing's menu wherever it is: a file's is a file's; a place's opens it - and the bin's empties it; anything else, the
     * thing's. In the archive's or the bin's card, where it goes from there.
     */
    JPopupMenu menuFor(NoteStore.Branch thing){return menuFor(thing,null);}
    JPopupMenu menuFor(NoteStore.Branch thing,Where where) {
        if(where==Where.AWAY)return awayMenu(thing,folder.inBin());
        if(thing.kind==NoteStore.Branch.Kind.FILE)return fileMenu(thing);
        if(thing.kind==NoteStore.Branch.Kind.FAVOURITES){JPopupMenu menu=new JPopupMenu();item(menu,"Open",folder::openFavourites);return menu;}
        if(thing.kind==NoteStore.Branch.Kind.ARCHIVE||thing.kind==NoteStore.Branch.Kind.BIN) {
            JPopupMenu menu=new JPopupMenu();item(menu,"Open",()->folder.openPlace(thing));
            if(thing.kind==NoteStore.Branch.Kind.BIN){menu.addSeparator();item(menu,"Empty the bin…",pad::askEmptyBin);}
            return menu;
        }
        return pad.thingMenu(thing);
    }

    /**
     * A thing waiting in the archive or the bin: where it can go from there, as the lists always offered - Put back; and
     * from the archive to the bin, or from the bin gone for good, after one question.
     */
    JPopupMenu awayMenu(NoteStore.Branch thing,boolean bin) {
        JPopupMenu menu=new JPopupMenu();
        item(menu,"Put back",()->pad.disk.submit(()->{store().restore(thing.kind,thing.id);return null;},
            done->{pad.status.setToolTipText(null);pad.status.setText("“"+named(thing)+"” is back where it was");pad.refresh();},pad::failed));
        if(bin)item(menu,"Delete for good…",()->pad.eraseForGood(thing));
        else item(menu,"Move to the bin",()->pad.disk.submit(()->{store().putAway(thing.kind,thing.id,false,false);store().putAway(thing.kind,thing.id,true,true);return null;},
            done->{pad.status.setToolTipText(null);pad.status.setText("Moved to the bin");pad.refresh();},pad::failed));
        return menu;
    }

    // ---- drawing a thing ----------------------------------------------------------------------------------------------

    /**
     * A thing's face in a square, as the phone draws it (docs/HOME.md, step 4): a picture of its own filling the rounded
     * square; else its icon - a note's the note icon until it is given another - in its colour's ink on paper washed in
     * that colour; a collection with no icon, the mini-grid of what is inside it; a file's kind in its own letters; the
     * Favourites' star. An icon this build's set does not have is drawn as the thing's default.
     */
    static void face(Graphics2D g,NoteStore.Branch b,int x,int y,int side,int tone) {
        int arc=Math.max(10,side*18/64);
        boolean coloured=Tint.known(b.colour);
        Color edge=coloured?DesktopLook.of(b.colour):DesktopUi.LINE.darker();
        g.setStroke(new BasicStroke(side>=48?1.4f:1.1f));
        switch(b.kind) {
            case FILE -> {
                g.setColor(DesktopUi.mix(DesktopUi.ACCENT,DesktopUi.CARD,0.88f));g.fillRoundRect(x,y,side,side,arc,arc);
                g.setColor(DesktopUi.LINE.darker());g.drawRoundRect(x,y,side-1,side-1,arc,arc);
                String kind=DesktopFileCards.extension(b.name);kind=kind.length()>4?kind.substring(0,4):kind;
                g.setFont(DesktopUi.BODY.deriveFont(Font.BOLD,Math.max(9f,side*0.22f)));FontMetrics m=g.getFontMetrics();
                g.setColor(DesktopUi.ACCENT);g.drawString(kind,x+(side-m.stringWidth(kind))/2f,y+(side-m.getHeight())/2f+m.getAscent());
            }
            case FAVOURITES -> {
                g.setColor(DesktopUi.CARD);g.fillRoundRect(x,y,side,side,arc,arc);
                g.setColor(DesktopUi.LINE.darker());g.drawRoundRect(x,y,side-1,side-1,arc,arc);
                star(g,x+side/2f,y+side/2f,side*0.26f,DesktopUi.INK);
            }
            case ARCHIVE, BIN -> {
                // The same outlined square as the Favourites', with the place's own glyph, half its size, as the phone has it.
                g.setColor(DesktopUi.CARD);g.fillRoundRect(x,y,side,side,arc,arc);
                g.setColor(DesktopUi.LINE.darker());g.drawRoundRect(x,y,side-1,side-1,arc,arc);
                float glyph=side*0.5f;
                DesktopIcons.draw(g,b.kind==NoteStore.Branch.Kind.BIN?BIN_ICON:ARCHIVE_ICON,x+(side-glyph)/2f,y+(side-glyph)/2f,glyph,DesktopUi.INK);
            }
            default -> {
                // A picture of its own fills the round square, a hairline round it so a pale one keeps its edge. One this PC
                // cannot read is not drawn: the icon under it is.
                if(DesktopIcons.picture(g,b.image,x,y,side,arc)) {
                    g.setColor(new Color(0,0,0,36));g.setStroke(new BasicStroke(1f));g.drawRoundRect(x,y,side-1,side-1,arc,arc);
                    return;
                }
                boolean collection=b.holds||DesktopMoving.shelf(b.kind);
                String icon=DesktopIcons.known(b.icon)?b.icon:collection?null:DesktopIcons.NOTE;
                Color ground=collection?DesktopLook.wash(b.colour,DesktopUi.CARD,0.22f,0.92f,tone):DesktopLook.wash(b.colour,DesktopUi.PAPER,0.14f,0.82f,tone);
                g.setColor(ground);g.fillRoundRect(x,y,side,side,arc,arc);
                g.setColor(edge);g.drawRoundRect(x,y,side-1,side-1,arc,arc);
                if(icon!=null) {
                    // Its icon, a little over half the square - more of a small one - in its colour: as the phone draws it.
                    float glyph=Looks.glyphSide(side,DesktopIcons.SMALL);
                    DesktopIcons.draw(g,icon,x+(side-glyph)/2f,y+(side-glyph)/2f,glyph,DesktopIcons.ink(b.colour));
                    return;
                }
                // A collection with no icon of its own: a rounded square with the first few things inside it showing through. A
                // small one - a line of the tree, a bar - shows all four: too small to count on, and a lone speck read as nothing.
                boolean small=side<DesktopIcons.SMALL;
                int pad=Math.max(small?3:5,side/7),gap=Math.max(2,side/22),cell=(side-2*pad-gap)/2,held=small?4:Math.min(4,countIn(b.detail));
                for(int at=0;at<held;at++) {
                    int px=x+pad+(at%2)*(cell+gap),py=y+pad+(at/2)*(cell+gap);
                    g.setColor(DesktopLook.wash(b.colour,DesktopUi.PAPER,0.10f,0.7f,tone));g.fillRoundRect(px,py,cell,cell,side/10,side/10);
                    g.setColor(DesktopUi.LINE.darker());g.setStroke(new BasicStroke(1f));g.drawRoundRect(px,py,cell-1,cell-1,side/10,side/10);
                }
            }
        }
    }

    /**
     * A thing's face as a Swing icon, for a line of the tree and the bars over a note and a card: drawn as it is read, at
     * the pad's tone as it is then, so what it wears changes with the thing without a new icon.
     */
    static Icon faceIcon(java.util.function.Supplier<NoteStore.Branch> thing,int side,java.util.function.IntSupplier tone) {
        return new Icon(){
            public int getIconWidth(){return side;}public int getIconHeight(){return side;}
            public void paintIcon(Component c,Graphics g0,int x,int y) {
                NoteStore.Branch now=thing.get();if(now==null)return;
                Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                face(g,now,x,y,side,tone.getAsInt());g.dispose();
            }
        };
    }

    /** A five-pointed star, filled: the Favourites' face and the corner of a favourite. */
    static void star(Graphics2D g,float cx,float cy,float r,Color colour) {
        java.awt.geom.Path2D.Float p=new java.awt.geom.Path2D.Float();
        for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float rr=i%2==0?r:r*0.45f;float px=cx+(float)(Math.cos(a)*rr),py=cy+(float)(Math.sin(a)*rr);if(i==0)p.moveTo(px,py);else p.lineTo(px,py);}
        p.closePath();g.setColor(colour);g.fill(p);
    }

    /** What a screen reader says for an icon: what a click does, and what else is true of it. */
    static String said(NoteStore.Branch b) {
        if(b.kind==NoteStore.Branch.Kind.ARCHIVE||b.kind==NoteStore.Branch.Kind.BIN)
            return "Open the "+(b.kind==NoteStore.Branch.Kind.BIN?"bin":"archive")+", "+(b.detail==null||b.detail.isBlank()?"empty":b.detail);
        String open=b.kind==NoteStore.Branch.Kind.FILE?"Open the file "+b.name+(b.detail==null||b.detail.isBlank()?"":", "+b.detail)
            :b.kind==NoteStore.Branch.Kind.FAVOURITES?"Open Favourites, the favourites the dock has no room for"
            :b.holds||DesktopMoving.shelf(b.kind)?"Open the collection "+named(b):"Open the note "+named(b);
        return open+(b.kept?", a favourite":"")+(b.fresh?", new":"");
    }

    /** The overview button's picture: two cards, one behind the other, as open things are shown. */
    private static Icon overviewIcon() {
        return new Icon(){
            public int getIconWidth(){return 18;}public int getIconHeight(){return 18;}
            public void paintIcon(Component c,Graphics g0,int x,int y) {
                Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(DesktopUi.INK);g.setStroke(new BasicStroke(1.5f));
                g.drawRoundRect(x+5,y+1,12,12,3,3);
                g.setColor(DesktopUi.PAPER);g.fillRoundRect(x+1,y+5,12,12,3,3);g.setColor(DesktopUi.INK);g.drawRoundRect(x+1,y+5,12,12,3,3);
                g.dispose();
            }
        };
    }

    // ---- the icons ----------------------------------------------------------------------------------------------------

    /**
     * Icons in cells, as many to a row as the width holds, centred: Home's, or a card's. Each stands in the cell it was put
     * in, with the empty cells between left empty ({@link Layout#arrange}); as tall as the rows make it and one empty row
     * more, so there is always somewhere to put a thing, and never shorter than what shows of it.
     */
    static final class Icons extends JPanel implements Scrollable {
        final DesktopHome home;final Where where;
        final List<Tile> tiles=new ArrayList<>();
        /** Words above the icons where there are none: what to do. */
        private JComponent words;
        /** Where a carried icon would land: the empty cell under the pointer, drawn as the quiet outline of an icon. */
        Rectangle landing;
        /** Where each icon stands, {row, column} by id, for the columns it was worked out for: again when they change. */
        private Map<String,int[]> drawn=Map.of();private int drawnFor=-1;
        /** Home's page in view, across and down from the centre one (decision 45); a card has one grid and no pages. */
        int pageX,pageY;
        /** Where every icon of Home stands on every page, for the columns and rows it was worked out for. */
        private Map<String,Layout.Spot> spots=Map.of();private int spotsFor=-1;
        /** Whether this grid is Home's, in pages; a card's is one grid that scrolls down (decision 49). */
        boolean paged(){return where==Where.HOME;}
        boolean onCentre(){return !paged()||pageX==0&&pageY==0;}
        /** How many rows a page holds: as many as the room shows (decision 48). */
        int rows() {
            Container up=getParent();int h=up!=null&&up.getHeight()>0?up.getHeight():getHeight();
            return Math.max(1,(h-TOP*2)/HIGH);
        }
        /**
         * Where every icon of Home stands, on every page (Layout.pages): Favourites first while it has no cell, the archive
         * and the bin after the last icon of the centre page.
         */
        Map<String,Layout.Spot> spots() {
            int now=columns()*1000+rows();
            if(now!=spotsFor) {
                List<String> ids=new ArrayList<>();List<Integer> onPages=new ArrayList<>(),cells=new ArrayList<>();
                Tile first=null;
                for(Tile one:tiles)if(one.thing.kind==NoteStore.Branch.Kind.FAVOURITES&&one.thing.cell<0)first=one;
                if(first!=null){ids.add(first.thing.id);onPages.add(Layout.NO_PAGE);cells.add(Layout.NONE);}
                for(Tile one:tiles)if(one!=first){ids.add(one.thing.id);onPages.add(one.thing.page);cells.add(one.thing.cell);}
                spots=Layout.pages(ids,onPages,cells,java.util.Set.of(NoteStore.ARCHIVE,NoteStore.BIN),columns(),rows());spotsFor=now;
            }
            return spots;
        }
        /** The page that way, where there is one; else nothing changes and nothing is said. */
        boolean turn(int dx,int dy) {
            int[] to=Layout.next(Layout.active(spots()),pageX,pageY,dx,dy);
            if(to==null)return false;
            showPage(to[0],to[1]);return true;
        }
        /** A page in view: any of them - an empty one beyond the edge, while something is carried there. */
        void showPage(int x,int y) {
            if(!paged()||x==pageX&&y==pageY)return;
            pageX=Math.max(-Layout.MIDDLE,Math.min(Layout.MIDDLE-1,x));pageY=Math.max(-Layout.MIDDLE,Math.min(Layout.MIDDLE-1,y));
            landing=null;drawnFor=-1;revalidate();placeNow();
            home.pageShown();
        }
        Icons(DesktopHome home,Where where) {
            super(null);this.home=home;this.where=where;setOpaque(false);
            // Wider or narrower can mean a different number to a row, and so a different height: laid out again at once. On
            // Home a taller or shorter room is a bigger or smaller page too (decision 48).
            addComponentListener(new ComponentAdapter(){int last;public void componentResized(ComponentEvent e){int now=columns()*1000+(paged()?rows():0);if(now!=last){last=now;spotsFor=-1;revalidate();placeNow();}}});
        }
        /**
         * The icons put in their places now, for the width the grid has, and the scroll pane round it sized to them. Left
         * to Swing's own pass, a grid filled again as the tree came or the window changed size could be pictured - and
         * seen - with every icon still at no size at all: that pass was not always run before the next paint.
         */
        private void placeNow() {
            Container scroll=SwingUtilities.getAncestorOfClass(JScrollPane.class,this);
            if(scroll!=null&&scroll.isShowing())scroll.validate();
            doLayout();repaint();
        }
        int columns(){return DesktopHome.columns(width());}
        private int width(){Container up=getParent();return getWidth()>0?getWidth():up!=null&&up.getWidth()>0?up.getWidth():CELL+2*SIDE;}
        /** The columns stand in the middle of the width, the icons from the left of them, as a phone's grid does. */
        private int left(){return Math.max(SIDE,(width()-columns()*CELL)/2);}
        /** Room over the first row: on Home's pages none for the words, which stand under the icons, so every page is one size. */
        private int top(){return TOP+(paged()||words==null?0:words.getPreferredSize().height+12);}

        /**
         * Where each icon stands for the columns the grid has now. A narrower grid than the one a cell was chosen on moves
         * that icon to the first free cell after its row, without writing anything: its own cell is kept for when there is
         * room again, until something on the grid is moved.
         */
        Map<String,int[]> drawn() {
            if(paged()) {
                // Those on the page in view, as {row, column}: what the keyboard, the cells and the carrying go by.
                Map<String,int[]> here=new java.util.LinkedHashMap<>();
                for(Map.Entry<String,Layout.Spot> one:spots().entrySet())
                    if(one.getValue()!=null&&one.getValue().on(pageX,pageY))here.put(one.getKey(),new int[]{one.getValue().row(),one.getValue().column()});
                return here;
            }
            int cols=columns();
            if(cols!=drawnFor) {
                List<String> ids=new ArrayList<>();List<Integer> cells=new ArrayList<>();
                // Among the favourites, the archive and the bin a thing is only listed, not kept, so it has no cell there: they
                // stand one after another.
                boolean kept=home.places(this);
                // The places among Home's icons where each was put (Layout.home): Favourites first while it has no cell of its
                // own, the archive and the bin after everything - as the phone has it.
                Map<String,Integer> places=new java.util.LinkedHashMap<>();
                for(Tile one:tiles) {
                    if(Grid.place(one.thing.kind)){places.put(one.thing.id,one.thing.cell);continue;}
                    ids.add(one.thing.id);cells.add(kept?one.thing.cell:Layout.NONE);
                }
                drawn=Layout.home(ids,cells,places,NoteStore.FAVOURITES,cols);drawnFor=cols;
            }
            return drawn;
        }
        /** Laid out again from the cells the icons have now: after one is put somewhere. */
        void rearrange(){drawnFor=-1;spotsFor=-1;revalidate();placeNow();}
        /** A cell's place in the grid. */
        Rectangle cellBounds(int row,int column){return new Rectangle(left()+column*CELL,top()+row*HIGH,CELL,HIGH);}
        /** The cell under a point of the screen, whether anything is in it or not (see cellUnder). */
        int[] cellAt(java.awt.Point screen) {
            JViewport port=getParent() instanceof JViewport v?v:null;
            java.awt.Point seen=new java.awt.Point(screen);SwingUtilities.convertPointFromScreen(seen,port!=null?port:this);
            int[] cell=cellUnder(seen,port!=null?port.getViewPosition():new java.awt.Point(0,0),left(),top(),columns());
            return cell!=null&&paged()&&cell[0]>=rows()?null:cell;
        }
        /** The icon in a cell, or null for an empty one. */
        Tile in(int row,int column) {
            String id=Layout.at(drawn(),row,column);
            if(id!=null)for(Tile one:tiles)if(one.thing.id.equals(id))return one;
            return null;
        }
        /** The first icon in reading order: where the keyboard starts. */
        Tile first() {
            if(tiles.isEmpty())return null;
            String id=stepTo(drawn(),tiles.get(0).thing.id,Does.FIRST);
            for(Tile one:tiles)if(one.thing.id.equals(id))return one;
            return tiles.get(0);
        }
        /** The keyboard to the icon of this thing. */
        void focus(String id){for(Tile one:tiles)if(one.thing.id.equals(id)){one.requestFocusInWindow();return;}}

        /**
         * Drawn again from these lines, keeping what the reader is looking at: where the grid is scrolled to and the icon
         * that has the keyboard stay where they were - an arrival is not a reason to lose your place.
         */
        void fill(List<NoteStore.Branch> lines,String nothing) {
            Component focus=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            String focused=focus instanceof Tile t&&t.getParent()==this?t.thing.id:null;
            JViewport port=getParent() instanceof JViewport v?v:null;
            java.awt.Point kept=port==null?null:port.getViewPosition();
            removeAll();tiles.clear();words=null;landing=null;drawnFor=-1;spotsFor=-1;
            if(nothing!=null){words=DesktopUi.note(nothing,Math.max(200,Math.min(460,width()-2*SIDE)),DesktopUi.QUIET,DesktopUi.BODY.deriveFont(13f));add(words);}
            // In the archive's or the bin's card a thing waits to be put back: its icon says so, and offers where it can go.
            Where here=where==Where.CARD&&home.folder!=null&&home.folder.away()?Where.AWAY:where;
            for(NoteStore.Branch one:lines){Tile tile=home.tile(one,here);tiles.add(tile);add(tile);}
            revalidate();placeNow();
            if(focused!=null)for(Tile t:tiles)if(t.thing.id.equals(focused)){t.requestFocusInWindow();break;}
            if(port!=null&&kept!=null)SwingUtilities.invokeLater(()->{if(getParent()==port){Dimension view=port.getViewSize(),seen=port.getExtentSize();
                port.setViewPosition(new java.awt.Point(0,Math.max(0,Math.min(kept.y,view.height-seen.height))));}});
        }
        @Override public void doLayout() {
            int x0=left(),y0=top();Map<String,int[]> at=drawn();
            if(words!=null) {
                Dimension d=words.getPreferredSize();
                // On Home, on the centre page under its icons; in a card, over them.
                int y=paged()?top()+Math.max(1,Layout.rows(at))*HIGH+8:TOP;
                words.setBounds(Math.max(SIDE,(width()-d.width)/2),y,d.width,d.height);words.setVisible(onCentre());
            }
            for(Tile one:tiles) {
                int[] cell=at.get(one.thing.id);
                if(cell!=null){one.setBounds(x0+cell[1]*CELL,y0+cell[0]*HIGH,CELL,HIGH);one.setVisible(true);}
                else if(paged())one.setVisible(false);
            }
        }
        @Override public Dimension getPreferredSize() {
            // A page of Home is the room it is seen in (decision 48).
            if(paged()){Container up=getParent();return up!=null&&up.getHeight()>0?up.getSize():new Dimension(columns()*CELL+2*SIDE,4*HIGH+2*TOP);}
            // One empty row under the last icon, so a thing can always be put below the rest. It is also the room the + floats
            // over, which kept the last icons clear of it before there was an empty row.
            int rows=Layout.rows(drawn())+1;
            return new Dimension(columns()*CELL+2*SIDE,top()+rows*HIGH+TOP);
        }
        @Override protected void paintChildren(Graphics g0) {
            super.paintChildren(g0);
            if(paged())DesktopPages.dots((Graphics2D)g0,this,Layout.dotted(spots(),pageX,pageY),pageX,pageY);
            if(landing==null)return;
            // Where a carried icon will land: the outline of an icon, quiet, so it reads as a place and not a thing.
            Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int x=landing.x+(landing.width-FACE)/2,y=landing.y+10,arc=Math.max(10,FACE*18/64);
            g.setColor(DesktopUi.mix(DesktopUi.ACCENT,DesktopUi.PAPER,0.9f));g.fillRoundRect(x,y,FACE,FACE,arc,arc);
            g.setColor(DesktopUi.mix(DesktopUi.ACCENT,DesktopUi.PAPER,0.25f));
            g.setStroke(new BasicStroke(1.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,10f,new float[]{6f,5f},0f));
            g.drawRoundRect(x,y,FACE-1,FACE-1,arc,arc);
            g.dispose();
        }
        public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        public int getScrollableUnitIncrement(Rectangle r,int o,int d){return 24;}
        public int getScrollableBlockIncrement(Rectangle r,int o,int d){return Math.max(24,r.height-48);}
        public boolean getScrollableTracksViewportWidth(){return true;}
        /** Never shorter than what shows of it: every cell in sight is one a thing can be put in. */
        public boolean getScrollableTracksViewportHeight(){return paged()||getParent() instanceof JViewport port&&port.getHeight()>getPreferredSize().height;}
    }

    /** One icon, made with everything it answers to: a click, the keyboard, a right-click, and being carried. */
    Tile tile(NoteStore.Branch thing,Where where) {
        Tile tile=new Tile(this,thing,where);
        MouseAdapter hand=new MouseAdapter(){
            public void mousePressed(MouseEvent e){tile.requestFocusInWindow();if(e.isPopupTrigger()){menuFor(thing,where).show(tile,e.getX(),e.getY());return;}carry.press(tile,e);}
            public void mouseReleased(MouseEvent e){boolean carried=carry.carrying();carry.release(e);if(!carried&&e.isPopupTrigger())menuFor(thing,where).show(tile,e.getX(),e.getY());}
            // A drag let go on an icon is not a click on it. In the archive or the bin, a click asks where it goes from there.
            public void mouseClicked(MouseEvent e) {
                if(!SwingUtilities.isLeftMouseButton(e)||carry.moved())return;
                if(where==Where.AWAY)menuFor(thing,where).show(tile,e.getX(),e.getY());else opened(thing,where);
            }
            public void mouseDragged(MouseEvent e){carry.drag(e);}
            public void mouseEntered(MouseEvent e){tile.over=true;tile.repaint();}
            public void mouseExited(MouseEvent e){tile.over=false;tile.repaint();}
        };
        tile.addMouseListener(hand);tile.addMouseMotionListener(hand);
        tile.addKeyListener(new KeyAdapter(){public void keyPressed(KeyEvent e){
            Does does=does(e.getKeyCode(),e.isShiftDown(),e.isControlDown()||e.isAltDown());
            switch(does) {
                case OPEN: if(where==Where.AWAY)menuFor(thing,where).show(tile,16,tile.getHeight()-12);else opened(thing,where);break;
                case MENU: menuFor(thing,where).show(tile,16,tile.getHeight()-12);break;
                case RENAME: if(where!=Where.AWAY&&(thing.kind==NoteStore.Branch.Kind.PAGE||DesktopMoving.shelf(thing.kind)))pad.rename(thing);break;
                case BIN:
                    if(where==Where.AWAY)break;
                    if(thing.kind==NoteStore.Branch.Kind.FILE)deleteFile(thing);
                    else if(where!=Where.FAVOURITES&&where!=Where.DOCK&&(thing.kind==NoteStore.Branch.Kind.PAGE||DesktopMoving.shelf(thing.kind)))pad.save(()->pad.putAway(thing,true));
                    break;
                case NONE: return;
                case MOVE_LEFT: case MOVE_RIGHT: case MOVE_UP: case MOVE_DOWN:
                    if(tile.getParent() instanceof Icons in)nudge(in,tile,does);
                    else return;
                    break;
                default: {
                    // Between the icons, over the empty cells; along the dock, its one row.
                    if(tile.getParent() instanceof Icons in)in.focus(stepTo(in.drawn(),thing.id,does));
                    else if(tile.getParent() instanceof Dock row)row.focusAt(step(row.tiles.indexOf(tile),row.tiles.size(),row.tiles.size(),does));
                }
            }
            e.consume();
        }});
        return tile;
    }

    /**
     * One icon: its face, its name under it in two lines at most, its mark on the corner and, where it is a favourite, a
     * star on the other - the corners are the one part of a picture that is never the thing itself - and a <i>new</i> on
     * a file that came and has not been opened. In the dock, the face alone: its name is said to a screen reader and under
     * the pointer instead, as a phone's dock has no words.
     */
    static final class Tile extends JPanel {
        final DesktopHome home;final NoteStore.Branch thing;final Where where;
        boolean over,lifted,target;
        Tile(DesktopHome home,NoteStore.Branch thing,Where where) {
            super(null);this.home=home;this.thing=thing;this.where=where;
            setOpaque(false);setFocusable(true);setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            putClientProperty(THING,thing);putClientProperty(MENU,(Supplier<JPopupMenu>)()->home.menuFor(thing,where));
            getAccessibleContext().setAccessibleName(where==Where.DOCK?named(thing):where==Where.AWAY?named(thing)+", "+thing.detail+". Put it back, or "+(home.folder.inBin()?"delete it for good":"move it to the bin"):said(thing));
            setToolTipText(where==Where.DOCK?named(thing):thing.kind==NoteStore.Branch.Kind.FILE&&thing.detail!=null?named(thing)+"  ·  "+thing.detail:null);
            addFocusListener(new FocusAdapter(){public void focusGained(FocusEvent e){home.lastTile=Tile.this;repaint();scrollRectToVisible(new Rectangle(getSize()));}public void focusLost(FocusEvent e){repaint();}});
        }
        boolean docked(){return where==Where.DOCK;}
        int side(){return docked()?DOCK_FACE:FACE;}
        /** The face's square, in this icon's own place. */
        Rectangle faceAt(){int s=side();return new Rectangle((getWidth()-s)/2,docked()?(getHeight()-s)/2:10,s,s);}
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Object hints=Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");if(hints instanceof Map<?,?> map)g.addRenderingHints(map);
            if(lifted)g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.3f));
            // Pointed at, or reached with the keyboard: a soft ground under the whole icon, and a ring while it has the keyboard.
            boolean ring=ringed(this);
            if(over||ring){g.setColor(DesktopUi.mix(DesktopUi.ACCENT,DesktopUi.PAPER,0.91f));g.fillRoundRect(3,2,getWidth()-6,getHeight()-4,16,16);}
            if(ring){g.setColor(DesktopUi.ACCENT);g.setStroke(new BasicStroke(1.6f));g.drawRoundRect(3,2,getWidth()-7,getHeight()-5,16,16);}
            Rectangle f=faceAt();
            face(g,thing,f.x,f.y,f.width,home.pad.tone);
            // Where a carried thing would go into or onto: this one ringed.
            if(target){g.setColor(DesktopUi.ACCENT);g.setStroke(new BasicStroke(3f));g.drawRoundRect(f.x-4,f.y-4,f.width+7,f.height+7,22,22);}
            int badge=docked()?16:20;
            if(thing.kind!=NoteStore.Branch.Kind.FILE&&thing.scope()!=null)
                new DesktopMark(thing.mark(),badge,true).paintIcon(this,g,f.x+f.width-badge+badge/3,f.y-badge/3);
            if(thing.kept&&where!=Where.FAVOURITES&&where!=Where.DOCK&&where!=Where.AWAY) {
                int d=badge-2,sx=f.x-d/3,sy=f.y-d/3;
                g.setColor(DesktopUi.PAPER);g.fillOval(sx,sy,d,d);g.setColor(DesktopUi.LINE.darker());g.setStroke(new BasicStroke(1f));g.drawOval(sx,sy,d-1,d-1);
                star(g,sx+d/2f,sy+d/2f+0.5f,d*0.34f,DesktopUi.INK);
            }
            // How many things wait in the archive or the bin, on the corner where a thing wears its mark: quiet, since a full
            // bin is not news, and nothing when it is empty.
            int many=thing.kind==NoteStore.Branch.Kind.ARCHIVE||thing.kind==NoteStore.Branch.Kind.BIN?countIn(thing.detail):0;
            if(many>0) {
                String said=many>99?"99+":String.valueOf(many);
                g.setFont(DesktopUi.BODY.deriveFont(11.5f));FontMetrics m=g.getFontMetrics();
                int w=Math.max(20,m.stringWidth(said)+12),h=18,nx=f.x+f.width-w+8,ny=f.y-7;
                g.setColor(DesktopUi.PAPER);g.fillRoundRect(nx,ny,w,h,h,h);
                g.setColor(DesktopUi.LINE.darker());g.setStroke(new BasicStroke(1f));g.drawRoundRect(nx,ny,w-1,h-1,h,h);
                g.setColor(DesktopUi.INK);g.drawString(said,nx+(w-m.stringWidth(said))/2f,ny+(h-m.getHeight())/2f+m.getAscent());
            }
            if(thing.kind==NoteStore.Branch.Kind.FILE&&thing.fresh) {
                g.setFont(DesktopUi.BODY.deriveFont(Font.BOLD,11f));FontMetrics m=g.getFontMetrics();
                int w=m.stringWidth("new")+12,h=17,nx=f.x+f.width-w+8,ny=f.y-7;
                g.setColor(DesktopUi.ACCENT);g.fillRoundRect(nx,ny,w,h,h,h);g.setColor(Color.WHITE);g.drawString("new",nx+6,ny+(h-m.getHeight())/2+m.getAscent());
            }
            if(!docked()) {
                g.setFont(DesktopUi.BODY);g.setColor(DesktopUi.INK);FontMetrics m=g.getFontMetrics();
                int y=f.y+f.height+8+m.getAscent();
                for(String line:lines(thing.name,m,getWidth()-14,2)){g.drawString(line,(getWidth()-m.stringWidth(line))/2f,y);y+=m.getHeight();}
            }
            g.dispose();
        }
    }

    // ---- the dock -----------------------------------------------------------------------------------------------------

    /**
     * The dock, at the foot of Home: as many favourites as fit (decision 3), faces only, as a phone's dock is - each says
     * its name under the pointer and to a screen reader. A click opens it; its right-click is its menu, with Remove from
     * the dock. A note or a collection carried here becomes a favourite at the place it is let go. With nothing in it,
     * it says Favourites, quietly, so the empty strip is not a mystery.
     */
    final class Dock extends JPanel {
        final List<Tile> tiles=new ArrayList<>();
        boolean lit;
        private final JLabel empty=new JLabel("Favourites");
        Dock() {
            super(new FlowLayout(FlowLayout.CENTER,0,0));setOpaque(false);setBorder(BorderFactory.createEmptyBorder(4,DOCK_ENDS,4,DOCK_ENDS));
            empty.setFont(DesktopUi.BODY.deriveFont(13f));empty.setForeground(DesktopUi.QUIET);empty.setBorder(BorderFactory.createEmptyBorder(0,40,0,40));
            empty.setPreferredSize(new Dimension(empty.getPreferredSize().width+80,DOCKED));
            empty.setToolTipText("Favourites go here: carry a note or a collection here, or choose Add to favourites from its menu");
            getAccessibleContext().setAccessibleName("The dock: your favourites");
        }
        void fill(List<NoteStore.Branch> shown) {
            Component focus=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            String focused=focus instanceof Tile t&&t.getParent()==this?t.thing.id:null;
            removeAll();tiles.clear();
            if(shown.isEmpty())add(empty);
            for(NoteStore.Branch one:shown){Tile tile=tile(one,Where.DOCK);tile.setPreferredSize(new Dimension(DOCKED,DOCKED));tiles.add(tile);add(tile);}
            // Placed now, as Home's grid is, rather than left for a later pass with the icons at no size.
            revalidate();Container foot=getParent()==null?null:getParent().getParent();
            if(foot!=null&&foot.isShowing())foot.validate();
            repaint();
            if(focused!=null)for(Tile t:tiles)if(t.thing.id.equals(focused))t.requestFocusInWindow();
        }
        void focusAt(int at){if(at>=0&&at<tiles.size())tiles.get(at).requestFocusInWindow();}
        void lit(boolean on){if(lit!=on){lit=on;repaint();}}
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(lit?DesktopUi.mix(DesktopUi.ACCENT,DesktopUi.PAPER,0.9f):DesktopUi.SHELF);g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,24,24);
            g.setColor(lit?DesktopUi.ACCENT:DesktopUi.LINE);g.setStroke(new BasicStroke(lit?2f:1f));g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,24,24);
            g.dispose();
        }
        /** The place in the dock, 1 the first, that a thing let go at this point of the screen takes (see Grid.dockSlot). */
        int slotAt(java.awt.Point screen,Tile carried) {
            List<Float> middles=new ArrayList<>();int dragged=-1;
            for(Tile one:tiles){if(one==carried)dragged=middles.size();java.awt.Point at=new java.awt.Point(one.getWidth()/2,0);SwingUtilities.convertPointToScreen(at,one);middles.add((float)at.x);}
            float[] all=new float[middles.size()];for(int i=0;i<all.length;i++)all[i]=middles.get(i);
            return Grid.dockSlot(screen.x,all,dragged);
        }
    }

    // ---- carrying -----------------------------------------------------------------------------------------------------

    /**
     * An icon taken hold of and carried, wherever it is picked up: the pointer is measured against the dock, an open card,
     * the dimmed Home round it and Home's grid, and letting go does what {@link Grid} says it does there. The icon in hand
     * is drawn pale where it was, its name beside the pointer; what it would go onto is ringed, the empty cell it would
     * land in is the quiet outline of an icon, and the bar says the same in words. Esc puts it down where it was.
     */
    final class Carry {
        private Tile pressed,carried,target,inTheWay;private java.awt.Point pressedAt;private boolean moved;
        /** Held at an edge of Home's page: the page beyond comes after a moment, a new one if there is none (decision 46). */
        private final javax.swing.Timer edge=new javax.swing.Timer(650,e->overTheEdge());private int edgeX,edgeY;private java.awt.Point last;
        /** What the bar says while it is held at an edge, or null; and whether this hold has turned its page already. */
        private String edgeSaid;private boolean turnedAt;
        private JLabel ghost;private KeyEventDispatcher escape;
        /** The grid and the cell it would land in, where that is empty - or its own cell, where it would stay. */
        private Grid.Zone zone=Grid.Zone.NONE;private Icons markedIn;private int[] cell;private boolean stays;private String said="";

        boolean carrying(){return carried!=null;}
        boolean moved(){return moved;}

        void press(Tile t,MouseEvent e) {
            moved=false;pressed=null;
            // A thing anywhere; a place on Home, to another cell of it (decision 42); nothing out of the archive or the bin.
            if(!SwingUtilities.isLeftMouseButton(e)||!Grid.carried(t.thing.kind)||t.where==Where.AWAY||Grid.place(t.thing.kind)&&t.where!=Where.HOME)return;
            pressed=t;pressedAt=e.getLocationOnScreen();
        }
        void drag(MouseEvent e) {
            if(pressed==null)return;
            java.awt.Point now=e.getLocationOnScreen();
            if(carried==null){if(now.distance(pressedAt)<Math.max(4,java.awt.dnd.DragSource.getDragThreshold()))return;begin(pressed);}
            at(now);
        }
        void release(MouseEvent e){if(carried!=null){at(e.getLocationOnScreen());letGo(e.getLocationOnScreen());}pressed=null;}

        private void begin(Tile t) {
            carried=t;moved=true;t.lifted=true;t.repaint();
            ghost=new JLabel(named(t.thing));
            ghost.setFont(DesktopUi.BODY.deriveFont(Font.BOLD,13f));ghost.setForeground(DesktopUi.INK);ghost.setOpaque(true);ghost.setBackground(DesktopUi.CARD);
            ghost.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(DesktopUi.ACCENT),BorderFactory.createEmptyBorder(4,10,4,10)));
            ghost.setSize(ghost.getPreferredSize());ghost.setVisible(false);
            pad.frame.getLayeredPane().add(ghost,JLayeredPane.DRAG_LAYER);
            escape=k->{
                if(carried==null||k.getKeyCode()!=KeyEvent.VK_ESCAPE)return false;
                if(k.getID()==KeyEvent.KEY_PRESSED)cancel();
                return true;
            };
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(escape);
        }

        private boolean over(Component c,java.awt.Point screen) {
            if(c==null||!c.isShowing())return false;
            java.awt.Point p=new java.awt.Point(screen);SwingUtilities.convertPointFromScreen(p,c);
            return c instanceof JComponent j?j.getVisibleRect().contains(p):c.contains(p);
        }

        /** The pointer at this point of the screen: where it would land there, marked, and said in the bar. */
        void at(java.awt.Point screen) {
            if(carried==null)return;
            last=screen;
            java.awt.Point onLayer=new java.awt.Point(screen);SwingUtilities.convertPointFromScreen(onLayer,pad.frame.getLayeredPane());
            ghost.setLocation(onLayer.x+16,onLayer.y+12);ghost.setVisible(true);
            zone=Grid.zone(over(dock,screen),folder.isOpen(),folder.isOpen()&&over(folder.card,screen),over(desk,screen));
            Icons from=carried.getParent() instanceof Icons in?in:null;
            Tile wasTarget=target;Icons wasIn=markedIn;
            target=null;cell=null;stays=false;markedIn=null;inTheWay=null;
            boolean dockNow=zone==Grid.Zone.DOCK&&Grid.docks(carried.thing.kind);
            boolean up=zone==Grid.Zone.UP&&from==folder.grid&&!folder.listing();
            dock.lit(dockNow);
            folder.lit(up);
            edgeSaid=null;
            // Carried out of Home's rows - up into the bar, down short of the dock - is a turn of the page too.
            boolean outOfRows=zone==Grid.Zone.NONE&&from==grid&&!folder.isOpen();
            if(zone==Grid.Zone.HOME&&from==grid){across(grid,screen);atTheEdge(screen);}
            else if(outOfRows)atTheEdge(screen);
            else if(zone==Grid.Zone.CARD&&from==folder.grid&&!folder.listing())across(folder.grid,screen);
            // Up a level onto Home itself: the empty cell of Home under the pointer is where it will go, marked through the dim.
            else if(up&&NoteStore.home(folder.parentId()))onHome(screen);
            if(wasTarget!=target){if(wasTarget!=null){wasTarget.target=false;wasTarget.repaint();}if(target!=null){target.target=true;target.repaint();}}
            if(wasIn!=null&&wasIn!=markedIn){wasIn.landing=null;wasIn.repaint();}
            if(!(zone==Grid.Zone.HOME&&from==grid)&&!outOfRows){edge.stop();turnedAt=false;}
            String name=named(carried.thing);
            if(edgeSaid!=null){say(edgeSaid);return;}
            say(dockNow?"Let go to put “"+name+"” in the dock"
                :zone==Grid.Zone.DOCK?"Only a note or a collection goes in the dock"
                :target!=null?onto(carried.thing,target.thing)
                :up?"Let go to move it up, into "+(NoteStore.home(folder.parentId())?"Home":"“"+folder.parentName()+"”")+(cell!=null?", here":"")
                :stays?"Let go to leave it where it was"
                :cell!=null?"Let go to put it here"
                :inTheWay!=null?"“"+named(inTheWay.thing)+"” is already there"
                :"It cannot go here");
        }
        /** What letting go on an icon does, in words: together, into it, or put away by the place it is. */
        private String onto(NoteStore.Branch carried,NoteStore.Branch there) {
            switch(Grid.onto(carried.kind,there.kind)) {
                case MERGE: return "Let go to put them together in a new collection";
                case AWAY:
                    if(there.kind==NoteStore.Branch.Kind.ARCHIVE)return "Let go to archive it";
                    return carried.kind==NoteStore.Branch.Kind.FILE?"Let go to delete it":"Let go to put it in the bin";
                default: return "Let go to move it into “"+named(there)+"”";
            }
        }
        /** Whether the pointer is at an edge of Home's page, and which way: held there, the page beyond comes. */
        private void atTheEdge(java.awt.Point screen) {
            java.awt.Point p=new java.awt.Point(screen);SwingUtilities.convertPointFromScreen(p,scroll);
            // Up and down as soon as it leaves the rows icons stand in (the owner's ask): above them at once; below them at
            // once too, but for a moment's wait while the dock is there, so a thing on its way to the dock does not turn
            // the page as it passes. Left and right, held at the edge for two thirds of a second.
            java.awt.Point in=new java.awt.Point(screen);SwingUtilities.convertPointFromScreen(in,grid);
            int rows=grid.rows(),top=grid.cellBounds(0,0).y,bottom=grid.cellBounds(rows-1,0).y+HIGH,wait=650;
            // Down only at the page's lower edge or past it, after a moment: anywhere over the last row it is let go there.
            java.awt.Point foot=new java.awt.Point(0,scroll.getHeight());foot=SwingUtilities.convertPoint(scroll,foot,grid);
            int reach=28,low=Math.max(bottom,foot.y-reach),dy=in.y<top?-1:in.y>=low?1:0;
            if(dy<0)wait=0;else if(dy>0)wait=dock.isShowing()?400:250;
            int dx=dy!=0?0:p.x<reach?-1:p.x>scroll.getWidth()-reach?1:0;
            if(dx==0&&dy==0){edge.stop();turnedAt=false;return;}
            // One page for each time it goes out: back into the rows, or off the edge and back, for the next.
            if(turnedAt){edgeSaid=DesktopPages.said(grid.pageX,grid.pageY)+" now - bring it onto the page to put it there";return;}
            edgeSaid="Hold here for the page "+(dx<0?"to the left":dx>0?"to the right":dy<0?"above":"below");
            if(edge.isRunning()&&dx==edgeX&&dy==edgeY)return;
            edgeX=dx;edgeY=dy;
            if(wait==0){edge.stop();overTheEdge();return;}
            edge.setInitialDelay(wait);edge.setRepeats(false);edge.restart();
        }
        private void overTheEdge() {
            if(carried==null)return;
            turnedAt=true;
            grid.showPage(grid.pageX+edgeX,grid.pageY+edgeY);
            if(last!=null)at(last);
        }
        private void say(String words){String all=words+"  ·  Esc cancels";if(!all.equals(said)){said=all;pad.status.setToolTipText(null);pad.status.setText(all);}}

        /**
         * Over a grid, by the cell under the pointer (decision 39): one that holds a thing it can go onto or into, that thing;
         * an empty one, the place it would land, outlined; its own, where it stays; one holding something else, nothing.
         * Near the top or foot of the grid, it scrolls.
         */
        private void across(Icons in,java.awt.Point screen) {
            java.awt.Point p=new java.awt.Point(screen);SwingUtilities.convertPointFromScreen(p,in);
            Rectangle seen=in.getVisibleRect();int reach=40,step=16;
            if(p.y<seen.y+reach)in.scrollRectToVisible(new Rectangle(seen.x,Math.max(0,seen.y-step),1,1));
            else if(p.y>seen.y+seen.height-reach)in.scrollRectToVisible(new Rectangle(seen.x,seen.y+seen.height+step,1,1));
            int[] under=in.cellAt(screen);
            Rectangle land=null;
            if(under!=null) {
                // What letting go there does is Grid's, as on the phone.
                Tile there=in.in(under[0],under[1]);
                switch(Grid.letGo(carried.thing.kind,there==null?null:there.thing.kind,there==carried)) {
                    case PLACE: cell=under;markedIn=in;land=in.cellBounds(under[0],under[1]);break;
                    case MERGE: case INTO: case AWAY: target=there;break;
                    default: if(there==carried){cell=under;markedIn=in;stays=true;}else inTheWay=there;
                }
            }
            if(!java.util.Objects.equals(in.landing,land)){in.landing=land;in.repaint();}
        }

        /** Carried out of a card onto the dimmed Home: the empty cell of Home's grid under the pointer, if it is one. */
        private void onHome(java.awt.Point screen) {
            int[] under=grid.cellAt(screen);
            Rectangle land=null;
            if(under!=null&&grid.in(under[0],under[1])==null){cell=under;markedIn=grid;land=grid.cellBounds(under[0],under[1]);}
            if(!java.util.Objects.equals(grid.landing,land)){grid.landing=land;grid.repaint();}
        }

        /** Let go: onto a thing, into the dock, up a level, or in an empty cell of its grid. */
        private void letGo(java.awt.Point screen) {
            Tile t=carried,onto=target;Grid.Zone z=zone;Icons in=markedIn;int[] at=cell;
            Icons from=t.getParent() instanceof Icons i?i:null;
            int dockSlot=dock.slotAt(screen,t);
            end();
            NoteStore.Branch thing=t.thing;
            if(z==Grid.Zone.DOCK) {
                if(!Grid.docks(thing.kind)){pad.status.setText("Only a note or a collection goes in the dock");refreshIfStale();return;}
                toDock(thing,dockSlot);return;
            }
            // From the dock or among the favourites a thing is only listed: it goes to the dock from there, and nowhere else.
            if(t.where==Where.DOCK||t.where==Where.FAVOURITES||t.where==Where.AWAY||from==null){pad.status.setText("Not moved");refreshIfStale();return;}
            if(onto!=null) {
                Grid.Onto does=Grid.onto(thing.kind,onto.thing.kind);
                // On the archive or the bin: put away there, as its menu puts it away, said and one Undo from where it was
                // (decision 41); a file on the bin asked about first, as its own Delete asks.
                if(does==Grid.Onto.AWAY) {
                    boolean bin=onto.thing.kind==NoteStore.Branch.Kind.BIN;
                    if(thing.kind==NoteStore.Branch.Kind.FILE){if(bin)deleteFile(thing);else refreshIfStale();}
                    else pad.save(()->pad.putAway(thing,bin));
                    return;
                }
                if(does==Grid.Onto.MERGE)merge(thing,onto.thing,from==grid?Things.HOME:folder.id(),from!=grid);
                else into(thing,onto.thing.id,named(onto.thing),onto.thing.kind,null);
                return;
            }
            if(z==Grid.Zone.UP&&from==folder.grid&&!folder.listing()) {
                // Up onto Home, in the empty cell it was let go in; anywhere else up there, the first free cell.
                // Onto the page of Home in view, in the empty cell it was let go in.
                Map<String,Layout.Spot> spots=in==grid&&at!=null?Layout.moveTo(grid.spots(),thing.id,new Layout.Spot(grid.pageX,grid.pageY,at[0],at[1])):null;
                List<NoteStore.Branch> lines=new ArrayList<>();for(Tile one:grid.tiles)lines.add(one.thing);lines.add(thing);
                into(thing,folder.parentId(),folder.parentName(),NoteStore.Branch.Kind.COLLECTION,()->{
                    folder.up();
                    if(spots!=null)pad.disk.submit(()->{writeSpots(lines,spots);return null;},done->{},e->pad.status.setText("Moved to Home, but not to the place it was let go in"));
                });
                return;
            }
            if(at!=null&&in==from&&places(from)&&from.paged()) {
                // On a page of Home, maybe another than it came from - which is how a page comes to be (decision 46).
                Layout.Spot to=new Layout.Spot(from.pageX,from.pageY,at[0],at[1]);
                Map<String,Layout.Spot> spots=Layout.moveTo(from.spots(),thing.id,to);
                if(spots!=null&&!to.equals(from.spots().get(thing.id))){keepSpots(from,thing,spots,from.onCentre()?"Moved":"Moved to "+DesktopPages.said(from.pageX,from.pageY).toLowerCase(java.util.Locale.ROOT));return;}
            }
            else if(at!=null&&in==from&&places(from)) {
                Map<String,Integer> cells=Layout.moveTo(from.drawn(),thing.id,at[0],at[1]);
                int[] was=from.drawn().get(thing.id);
                if(cells!=null&&(was==null||was[0]!=at[0]||was[1]!=at[1])){keepCells(from,thing,cells,"Moved");return;}
            }
            pad.status.setText("Not moved");refreshIfStale();
        }
        /** Esc: put back where it was, and said. */
        void cancel(){if(carried==null)return;end();pressed=null;pad.status.setText("Not moved");refreshIfStale();}

        private void end() {
            edge.stop();last=null;turnedAt=false;
            Tile t=carried;carried=null;said="";
            if(t!=null){t.lifted=false;t.repaint();}
            if(target!=null){target.target=false;target.repaint();target=null;}
            if(markedIn!=null){markedIn.landing=null;markedIn.repaint();markedIn=null;}
            for(Icons one:new Icons[]{grid,folder.grid})if(one.landing!=null){one.landing=null;one.repaint();}
            cell=null;stays=false;inTheWay=null;zone=Grid.Zone.NONE;dock.lit(false);folder.lit(false);
            if(escape!=null){KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(escape);escape=null;}
            if(ghost!=null){Container layer=ghost.getParent();if(layer!=null){layer.remove(ghost);layer.repaint();}ghost=null;}
        }
    }

    /** Something changed while a thing was carried or a name typed: Home read again now they are done. */
    void refreshIfStale(){if(stale)refresh();}
}
