package org.mininotes.android;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.swing.*;

/**
 * The box a note's or a collection's icon is chosen in (docs/HOME.md, decisions 32 to 34), the same box as the phone's:
 * the colours along the top with the thing's own ringed - one clicked is its colour at once, as the Colour menu gives it;
 * a search; Default first, then Lucide's set in its own order, found by name and words as they are typed; and Choose a
 * picture… at the end. A click on an icon is the choice - there is nothing to press after it - and the box closes.
 *
 * <p>The keyboard: the search has it when the box opens and Enter takes the first icon found; Down goes into the grid,
 * where the arrows move and Enter takes; Esc closes. A picture can be chosen, pasted, or let go on the box from Explorer.
 * The grid is one component that paints only the rows that show: the set is 1,857 icons, and a component each would be
 * 1,857 components laid out for a box that is open for a few seconds.
 */
final class DesktopIconPicker {
    /** One cell of the grid, the icon in it, the default's face in it, and how many cells the box shows across and down. */
    static final int CELL=52,GLYPH=24,FACE=36,ACROSS=9,DOWN=7;
    /** Default's place among the names: the thing's own look, which is not an icon of the set. The phone's, shared (Looks). */
    static final String DEFAULT=Looks.DEFAULT;

    // ---- the rules, without a window, so they are unit tested --------------------------------------------------------

    /** How many cells go across a grid this wide: as many whole ones as fit, one at the least. */
    static int columns(int width){return Math.max(1,width/CELL);}
    /** How many rows that many cells make, that many across. */
    static int rows(int count,int columns){int across=Math.max(1,columns);return count<=0?0:(count+across-1)/across;}

    /**
     * The first and last row in a band of the grid from {@code top}, {@code height} tall, rows {@code cell} tall: the only
     * rows painted. None - the last before the first - where there are no rows or no band.
     */
    static int[] visibleRows(int top,int height,int cell,int rows) {
        if(rows<=0||cell<=0||height<=0)return new int[]{0,-1};
        int first=Math.max(0,top)/cell,last=Math.min(rows-1,(Math.max(0,top)+height-1)/cell);
        return new int[]{Math.min(first,rows),last};
    }

    /** What the grid holds for these words: Default first while nothing is typed, then the set or what is found - the phone's list. */
    static List<String> shown(String words){return Looks.shown(words);}

    /** What a thing is, for its look: a note, or a collection; null for anything that wears none. */
    static NoteStore.Branch.Kind kindOf(NoteStore.Branch thing) {
        if(thing==null)return null;
        return thing.kind==NoteStore.Branch.Kind.PAGE?NoteStore.Branch.Kind.PAGE:DesktopMoving.shelf(thing.kind)?NoteStore.Branch.Kind.COLLECTION:null;
    }

    /** Whether this PC may only read it, as the notebook weighs it before it refuses a look (NoteStore.look). On the disk. */
    static boolean readsOnly(NoteStore store,NoteStore.Branch.Kind kind,String id) {
        return kind==NoteStore.Branch.Kind.PAGE?store.onlyReads(id):store.myLevel(Sharing.Scope.THING,id)==Sharing.Level.READ;
    }

    /** An icon's name as a person reads it, for the words under the pointer and a screen reader. */
    static String said(String name,boolean note) {
        if(DEFAULT.equals(name))return note?"Default: the note icon":"Default: what is inside it";
        return Looks.spoken(name);
    }

    // ---- choosing -----------------------------------------------------------------------------------------------------

    /** Opened from a thing's menu, or from its icon before a note's title or a card's name: what it wears is read first. */
    static void open(Desktop pad,NoteStore.Branch thing) {
        NoteStore.Branch.Kind kind=kindOf(thing);if(kind==null)return;
        pad.disk.submit(()->new Object[]{pad.store.colourOf(kind,thing.id),pad.store.iconOf(kind,thing.id),pad.store.imageOf(kind,thing.id)!=null,readsOnly(pad.store,kind,thing.id)},
            got->new DesktopIconPicker(pad,thing,kind,(Integer)got[0],(String)got[1],(Boolean)got[2],(Boolean)got[3]).show(),pad::failed);
    }

    /** An icon worn - or Default, the thing's own look, again - kept, drawn everywhere, and sent. It takes a picture off (decision 34). */
    static void wear(Desktop pad,NoteStore.Branch thing,NoteStore.Branch.Kind kind,String icon) {
        pad.disk.submit(()->{pad.store.setIcon(kind,thing.id,icon);return null;},done->{
            pad.status.setToolTipText(null);pad.status.setText(DEFAULT.equals(icon)?"Back to its own icon":"Icon changed");
            pad.lookChanged(kind,thing.id);
        },e->failed(pad,e,"Could not change the icon. Nothing was changed."));
    }

    /** Remove the picture, from a thing's menu: the icon under it is worn again. */
    static void removePicture(Desktop pad,NoteStore.Branch thing) {
        NoteStore.Branch.Kind kind=kindOf(thing);if(kind==null)return;
        pad.disk.submit(()->{pad.store.setImage(kind,thing.id,null);return null;},done->{
            pad.status.setToolTipText(null);pad.status.setText("Picture removed");
            pad.lookChanged(kind,thing.id);
        },e->failed(pad,e,"Could not remove the picture. Nothing was changed."));
    }

    /**
     * A picture made and worn: read, cut square, scaled and packed to fit (see DesktopIcons.thumb), then kept. The bar says
     * it is being made while it is, and how it ended - or why it could not be, in the words the reading or the notebook gave.
     *
     * @param from the file's name, to say which picture; null for one pasted
     */
    static void picture(Desktop pad,NoteStore.Branch thing,NoteStore.Branch.Kind kind,String from,Background.Work<DesktopIcons.Chosen> read) {
        pad.status.setToolTipText(null);pad.status.setText(from==null?"Making the picture…":"Making the picture from “"+from+"”…");
        pad.disk.submit(()->{
            DesktopIcons.Chosen chosen=read.run();
            byte[] thumb=DesktopIcons.thumb(chosen.picture(),chosen.turn());
            if(thumb==null)throw new IllegalArgumentException("That picture could not be made small enough to keep.");
            pad.store.setImage(kind,thing.id,thumb);
            return thumb.length;
        },size->{pad.status.setText("Picture set");pad.lookChanged(kind,thing.id);},
          e->failed(pad,e,"Mininotes could not read that picture. Nothing was changed."));
    }

    /** What went wrong, said: the notebook's and the reading's own words where they were meant for a person. */
    private static void failed(Desktop pad,Exception e,String otherwise) {
        boolean said=(e instanceof IllegalArgumentException||e instanceof IllegalStateException)&&e.getMessage()!=null;
        pad.status.setText(said?e.getMessage():otherwise);pad.status.setToolTipText(pad.status.getText());
    }

    /** A picture offered by a paste or a drop: which file it was, if it was one, and how to read it. */
    record Offered(String name,Background.Work<DesktopIcons.Chosen> read){}

    /**
     * The picture in something pasted or let go: a picture file - the first of them, as Explorer copies or drags it - or a
     * picture itself, as a browser or an editor copies one. Null where it holds neither.
     */
    static Offered offered(Transferable t) {
        try {
            if(t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked") List<File> files=(List<File>)t.getTransferData(DataFlavor.javaFileListFlavor);
                for(File one:files)if(one.isFile())return new Offered(one.getName(),()->DesktopIcons.read(one.toPath()));
                return null;
            }
            if(t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                BufferedImage picture=buffered((Image)t.getTransferData(DataFlavor.imageFlavor));
                return picture==null?null:new Offered(null,()->new DesktopIcons.Chosen(picture,1));
            }
        } catch(Exception unreadable){/* nothing to take */}
        return null;
    }
    static BufferedImage buffered(Image image) {
        if(image instanceof BufferedImage b)return b;
        Image loaded=new ImageIcon(image).getImage();
        int w=loaded.getWidth(null),h=loaded.getHeight(null);if(w<=0||h<=0)return null;
        BufferedImage out=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);Graphics2D g=out.createGraphics();g.drawImage(loaded,0,0,null);g.dispose();
        return out;
    }

    // ---- the box ------------------------------------------------------------------------------------------------------

    private final Desktop pad;private final NoteStore.Branch thing;private final NoteStore.Branch.Kind kind;
    private final String worn;private final boolean pictured,readOnly;
    private int colour;
    private JDialog box;
    final JTextField search=new JTextField();
    final Grid grid=new Grid();
    private final JPanel tints=new JPanel(new FlowLayout(FlowLayout.LEFT,2,0));

    private DesktopIconPicker(Desktop pad,NoteStore.Branch thing,NoteStore.Branch.Kind kind,int colour,String worn,boolean pictured,boolean readOnly) {
        this.pad=pad;this.thing=thing;this.kind=kind;this.colour=colour;this.worn=worn==null?"":worn;this.pictured=pictured;this.readOnly=readOnly;
    }
    private boolean note(){return kind==NoteStore.Branch.Kind.PAGE;}

    /** The box, drawn and shown: the colours, a line saying why where nothing but the colour may change, the search, the grid, the picture. */
    private void show() {
        JPanel top=DesktopUi.column();
        tintRow();DesktopUi.add(top,tints);
        if(readOnly) {
            DesktopUi.gap(top,DesktopUi.S);
            DesktopUi.add(top,DesktopUi.note((note()?"This note is":"This collection is")+" read only here, so it keeps the icon it came with. Its colour is yours to choose.",
                CELL*ACROSS,DesktopUi.QUIET,DesktopUi.BODY.deriveFont(13f)));
        }
        DesktopUi.gap(top,12);
        search.putClientProperty(com.formdev.flatlaf.FlatClientProperties.PLACEHOLDER_TEXT,"Search icons");
        search.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TEXT_FIELD_LEADING_ICON,new com.formdev.flatlaf.icons.FlatSearchIcon());
        search.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON,true);
        search.getAccessibleContext().setAccessibleName("Search icons");search.setEnabled(!readOnly);
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            public void insertUpdate(javax.swing.event.DocumentEvent e){grid.show(shown(search.getText()));}
            public void removeUpdate(javax.swing.event.DocumentEvent e){grid.show(shown(search.getText()));}
            public void changedUpdate(javax.swing.event.DocumentEvent e){}
        });
        // Enter takes what was found first; Down goes into the grid, to move among them.
        search.addActionListener(e->pick(grid.chosen));
        search.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DOWN"),"into");
        search.getActionMap().put("into",new AbstractAction(){public void actionPerformed(ActionEvent e){grid.requestFocusInWindow();}});
        search.addFocusListener(new FocusAdapter(){public void focusGained(FocusEvent e){grid.repaint();}public void focusLost(FocusEvent e){grid.repaint();}});
        DesktopUi.add(top,search);DesktopUi.gap(top,DesktopUi.S);
        JScrollPane scroll=new JScrollPane(grid);scroll.setBorder(null);scroll.setOpaque(false);scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(CELL/2);scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        JPanel body=new JPanel(new BorderLayout());body.setOpaque(false);
        body.add(top,BorderLayout.NORTH);body.add(scroll);body.add(pictureRow(),BorderLayout.SOUTH);
        String name=DesktopHome.named(thing);if(name.length()>32)name=name.substring(0,31).trim()+"…";
        box=DesktopUi.sheet(pad.frame,"Icon for “"+name+"”",body,null,true);
        box.getRootPane().putClientProperty("focus",readOnly?tints.getComponent(0):search);
        // A picture let go anywhere on the box, or pasted: the search takes text as a field does, and pictures as well.
        box.getRootPane().setTransferHandler(new Taking(null));grid.setTransferHandler(new Taking(null));
        search.setTransferHandler(new Taking(search.getTransferHandler()));
        grid.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("control V"),"paste");grid.getActionMap().put("paste",TransferHandler.getPasteAction());
        grid.show(shown(""));
        DesktopUi.show(box,CELL*ACROSS+2*DesktopUi.L+40,680);
    }

    /** The colours, as the Colour menu has them: rounds in a row, the one it has ringed, each a click from being its colour. */
    private void tintRow() {
        tints.setOpaque(false);tints.setBorder(BorderFactory.createEmptyBorder(0,-4,0,0));
        for(int c=0;c<Tint.count();c++) {
            int one=c;
            JButton round=new JButton(new Icon(){
                public int getIconWidth(){return 26;}public int getIconHeight(){return 26;}
                public void paintIcon(Component on,Graphics g,int x,int y){DesktopLook.swatch(one,26,ringed(one)).paintIcon(on,g,x,y);}
            });
            round.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            round.setMargin(new Insets(3,3,3,3));round.setFocusPainted(false);round.setToolTipText(Tint.NAMES[c]);
            round.getAccessibleContext().setAccessibleName(Tint.NAMES[c]);
            round.addActionListener(e->{
                if(ringed(one))return;
                colour=one;pad.paint(thing,one);
                tints.repaint();grid.repaint();
            });
            tints.add(round);
        }
    }
    private boolean ringed(int c){return c==colour||c==Tint.NONE&&!Tint.known(colour);}

    /** Choose a picture…: a row at the end, as the phone has it, rather than a second button - tapping an icon is the choice. */
    private JComponent pictureRow() {
        JButton choose=new JButton("Choose a picture…",DesktopIcons.glyph("image-plus",20));
        choose.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        choose.setHorizontalAlignment(SwingConstants.LEFT);choose.setIconTextGap(12);choose.setMargin(new Insets(8,10,8,14));
        choose.setFont(DesktopUi.BODY);choose.setForeground(DesktopUi.INK);choose.setFocusPainted(false);choose.setEnabled(!readOnly);
        choose.getAccessibleContext().setAccessibleName("Choose a picture");
        choose.addActionListener(e->choosePicture());
        JPanel row=new JPanel(new BorderLayout(12,0));row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,DesktopUi.LINE),BorderFactory.createEmptyBorder(8,0,0,0)));
        row.add(choose,BorderLayout.WEST);
        if(!readOnly){JLabel also=new JLabel("or paste or drop one here");also.setFont(DesktopUi.BODY.deriveFont(13f));also.setForeground(DesktopUi.QUIET);row.add(also);}
        return row;
    }

    /** The system's own box for a file, showing pictures only; the one chosen made and worn, and this box gone. */
    private void choosePicture() {
        JFileChooser pick=new JFileChooser();pick.setDialogTitle("Choose a picture");
        pick.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Pictures (JPEG, PNG, GIF, BMP, WebP)","jpg","jpeg","png","gif","bmp","webp"));
        if(pick.showOpenDialog(box)!=JFileChooser.APPROVE_OPTION||pick.getSelectedFile()==null)return;
        File file=pick.getSelectedFile();box.dispose();
        picture(pad,thing,kind,file.getName(),()->DesktopIcons.read(file.toPath()));
    }

    /** An icon clicked, or Entered: worn, and the box gone. */
    private void pick(int at) {
        if(readOnly||at<0||at>=grid.names.size())return;
        String name=grid.names.get(at);box.dispose();
        wear(pad,thing,kind,name);
    }

    /** Whether this cell is what the thing wears now: ringed, as its colour is. A picture is worn over them all. */
    private boolean wears(String name){return !pictured&&(name.equals(worn)||DEFAULT.equals(name)&&!DesktopIcons.known(worn));}

    /** The thing as it looks with nothing of its own chosen, for the Default cell: its colour, and a full mini-grid for a collection. */
    private NoteStore.Branch plain() {
        String detail=thing.detail==null||DesktopHome.countIn(thing.detail)==0?"4":thing.detail;
        return new NoteStore.Branch(kind,thing.id,"",DesktopHome.named(thing),detail,0,0,!note(),colour);
    }

    /** A picture pasted or let go on the box: taken as one chosen is. Text goes where it would have gone. */
    private final class Taking extends TransferHandler {
        private final TransferHandler was;
        Taking(TransferHandler was){this.was=was;}
        private boolean picture(TransferSupport s){return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)||s.isDataFlavorSupported(DataFlavor.imageFlavor);}
        @Override public boolean canImport(TransferSupport s){return picture(s)?!readOnly:was!=null&&was.canImport(s);}
        @Override public boolean importData(TransferSupport s) {
            if(!picture(s))return was!=null&&was.importData(s);
            if(readOnly)return false;
            Offered offered=offered(s.getTransferable());if(offered==null)return false;
            // After the drop has finished: Explorer's drag is held until the one taking it returns.
            SwingUtilities.invokeLater(()->{box.dispose();DesktopIconPicker.picture(pad,thing,kind,offered.name(),offered.read());});
            return true;
        }
        @Override public int getSourceActions(JComponent c){return was==null?NONE:was.getSourceActions(c);}
        @Override public void exportAsDrag(JComponent c,InputEvent e,int action){if(was!=null)was.exportAsDrag(c,e,action);}
        @Override public void exportToClipboard(JComponent c,Clipboard clip,int action){if(was!=null)was.exportToClipboard(c,clip,action);}
    }

    /**
     * The grid: Default and the icons, a cell each, painted row by row for only the rows that show. Pointed at, a cell is
     * lit; what the thing wears is ringed; the one the keyboard is on is lit, and ringed in green while the grid has it.
     */
    final class Grid extends JComponent implements Scrollable {
        List<String> names=List.of();int chosen=-1,over=-1;
        Grid() {
            setFocusable(true);setOpaque(false);ToolTipManager.sharedInstance().registerComponent(this);
            getAccessibleContext().setAccessibleName("Icons");
            MouseAdapter hand=new MouseAdapter(){
                public void mouseMoved(MouseEvent e){int at=at(e.getPoint());if(at!=over){over=at;repaint();}setCursor(Cursor.getPredefinedCursor(at>=0&&!readOnly?Cursor.HAND_CURSOR:Cursor.DEFAULT_CURSOR));}
                public void mouseExited(MouseEvent e){if(over>=0){over=-1;repaint();}}
                public void mouseClicked(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e))pick(at(e.getPoint()));}
            };
            addMouseListener(hand);addMouseMotionListener(hand);
            addFocusListener(new FocusAdapter(){public void focusGained(FocusEvent e){if(chosen<0&&!names.isEmpty())chosen=0;repaint();}public void focusLost(FocusEvent e){repaint();}});
            addKeyListener(new KeyAdapter(){
                public void keyPressed(KeyEvent e) {
                    int cols=columns(),page=Math.max(1,getVisibleRect().height/CELL)*cols;
                    switch(e.getKeyCode()) {
                        case KeyEvent.VK_ENTER: case KeyEvent.VK_SPACE: pick(chosen);break;
                        case KeyEvent.VK_LEFT: move(chosen-1);break;
                        case KeyEvent.VK_RIGHT: move(chosen+1);break;
                        // Up from the first row goes back to the search, as Down from the search came here.
                        case KeyEvent.VK_UP: if(chosen-cols<0)search.requestFocusInWindow();else move(chosen-cols);break;
                        case KeyEvent.VK_DOWN: if(chosen+cols<names.size())move(chosen+cols);break;
                        case KeyEvent.VK_PAGE_UP: move(chosen-page);break;
                        case KeyEvent.VK_PAGE_DOWN: move(chosen+page);break;
                        case KeyEvent.VK_HOME: move(0);break;
                        case KeyEvent.VK_END: move(names.size()-1);break;
                        default: return;
                    }
                    e.consume();
                }
                // A word typed on the grid is a search: it goes to the field, and the field keeps the keyboard.
                public void keyTyped(KeyEvent e) {
                    char c=e.getKeyChar();
                    if(readOnly||c==KeyEvent.CHAR_UNDEFINED||Character.isISOControl(c)||e.isControlDown()||e.isAltDown())return;
                    search.requestFocusInWindow();search.setText(search.getText()+c);e.consume();
                }
            });
        }

        /**
         * Drawn again from these names, from the top. With words typed, the first found is the one Enter takes; with none,
         * none is: Enter on a box just opened would otherwise take Default and take the thing's icon off.
         */
        void show(List<String> now) {
            names=now;chosen=names.isEmpty()||search.getText().isBlank()?-1:0;over=-1;
            revalidate();repaint();
            if(getParent() instanceof JViewport port)port.setViewPosition(new java.awt.Point(0,0));
            announce();
        }
        private int width(){Container up=getParent();return up instanceof JViewport v&&v.getWidth()>0?v.getWidth():getWidth()>0?getWidth():CELL*ACROSS;}
        int columns(){return DesktopIconPicker.columns(width());}
        /** The cells stand in the middle of the width. */
        private int left(){return Math.max(0,(width()-columns()*CELL)/2);}
        Rectangle cell(int at){int cols=columns();return new Rectangle(left()+(at%cols)*CELL,(at/cols)*CELL,CELL,CELL);}
        /** The cell at a point, or -1 for none. */
        int at(java.awt.Point p) {
            int cols=columns(),x=p.x-left();
            if(x<0||x>=cols*CELL||p.y<0)return -1;
            int at=(p.y/CELL)*cols+x/CELL;
            return at<names.size()?at:-1;
        }
        private void move(int to) {
            if(names.isEmpty())return;
            chosen=Math.max(0,Math.min(names.size()-1,to));
            scrollRectToVisible(cell(chosen));repaint();announce();
        }
        /** The one the keyboard is on, for a screen reader: what it is called, and where among the others. */
        private void announce() {
            getAccessibleContext().setAccessibleDescription(chosen>=0&&chosen<names.size()?said(names.get(chosen),note())+", "+(chosen+1)+" of "+names.size():"No icon found");
        }
        @Override public String getToolTipText(MouseEvent e){int at=at(e.getPoint());return at<0?null:said(names.get(at),note());}
        /** A bare component has no accessible context of its own for a screen reader to name; the grid is a list of icons. */
        @Override public javax.accessibility.AccessibleContext getAccessibleContext() {
            if(accessibleContext==null)accessibleContext=new AccessibleJComponent(){
                @Override public javax.accessibility.AccessibleRole getAccessibleRole(){return javax.accessibility.AccessibleRole.LIST;}
            };
            return accessibleContext;
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Object hints=Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");if(hints instanceof java.util.Map<?,?> map)g.addRenderingHints(map);
            if(names.isEmpty()) {
                g.setFont(DesktopUi.BODY.deriveFont(13f));g.setColor(DesktopUi.QUIET);
                g.drawString("No icon is called that. Try another word.",left()+10,24+g.getFontMetrics().getAscent());g.dispose();return;
            }
            Rectangle clip=g.getClipBounds();if(clip==null)clip=new Rectangle(getSize());
            int cols=columns(),x0=left();
            int[] band=visibleRows(clip.y,clip.height,CELL,rows(names.size(),cols));
            Color ink=DesktopIcons.ink(colour);
            boolean keyboard=isFocusOwner(),typing=search.isFocusOwner();
            // Read only: the icons as they are, faded, since none of them can be taken.
            if(readOnly)g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.4f));
            for(int row=band[0];row<=band[1];row++)for(int col=0;col<cols;col++) {
                int at=row*cols+col;if(at>=names.size())break;
                int x=x0+col*CELL,y=row*CELL;String name=names.get(at);
                boolean lit=!readOnly&&(at==over||at==chosen&&(keyboard||typing));
                if(lit){g.setColor(DesktopUi.mix(DesktopUi.ACCENT,DesktopUi.PAPER,0.88f));g.fillRoundRect(x+3,y+3,CELL-6,CELL-6,12,12);}
                if(wears(name)){g.setColor(DesktopUi.INK);g.setStroke(new BasicStroke(1.6f));g.drawRoundRect(x+2,y+2,CELL-5,CELL-5,14,14);}
                if(keyboard&&at==chosen&&!readOnly){g.setColor(DesktopUi.ACCENT);g.setStroke(new BasicStroke(1.6f));g.drawRoundRect(x+3,y+3,CELL-7,CELL-7,12,12);}
                if(DEFAULT.equals(name))DesktopHome.face(g,plain(),x+(CELL-FACE)/2,y+(CELL-FACE)/2,FACE,pad.tone);
                else DesktopIcons.draw(g,name,x+(CELL-GLYPH)/2f,y+(CELL-GLYPH)/2f,GLYPH,ink);
            }
            g.dispose();
        }
        @Override public Dimension getPreferredSize(){int cols=columns();return new Dimension(cols*CELL,Math.max(CELL,rows(names.size(),cols)*CELL));}
        public Dimension getPreferredScrollableViewportSize(){return new Dimension(CELL*ACROSS,CELL*DOWN);}
        public int getScrollableUnitIncrement(Rectangle r,int o,int d){return CELL/2;}
        public int getScrollableBlockIncrement(Rectangle r,int o,int d){return Math.max(CELL,r.height-CELL);}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return false;}
    }
}
