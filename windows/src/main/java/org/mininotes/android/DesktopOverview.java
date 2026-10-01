package org.mininotes.android;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * The overview: the notes and collections that are open, as cards side by side, the newest first, as a phone shows its
 * open apps (docs/HOME.md, step 3). It takes the place of the tab bar. The button beside the search, or Ctrl+Tab, puts it
 * over the window: a click on a card goes there, its × or a middle click closes it, and Close all - the one button -
 * closes them all. With Ctrl held, Tab moves along the cards and letting go of Ctrl opens the one chosen, as Alt+Tab
 * does with windows. Esc, or a click beside the cards, puts it away.
 *
 * <p>What is open is the pure {@link Overview}, shared with the phone and kept in this PC's settings as the phone keeps
 * it in its own; this draws it.
 */
final class DesktopOverview extends JComponent {
    /** Where the list is kept among the settings, under the phone's own name for it. */
    static final String KEPT="overview";
    static final int WIDE=224,HIGH=284;

    // ---- the rules, without a window, so they are unit tested --------------------------------------------------------

    /**
     * The card Ctrl+Tab starts on: the one after what is on the screen - which is the newest, the first card, when it is
     * one of them - so one Ctrl+Tab goes back to what was open before, as Alt+Tab does. None with no cards.
     */
    static int firstChosen(int size,boolean frontShowing) {
        if(size<=0)return -1;
        return frontShowing&&size>1?1:0;
    }

    /** One card along, or back, going round from the last to the first and the other way. */
    static int along(int at,int size,boolean back) {
        if(size<=0)return -1;
        return ((at+(back?-1:1))%size+size)%size;
    }

    /**
     * What 0.1 builds kept as their tabs - {@code KIND:id|KIND:id…#current} - read once into the overview: the tab that
     * was in front first, then the others in the order they stood. Notes stay notes; a collection's or an old book's tab
     * is a collection's; All collections and the drop box are places, not things, and are left out.
     */
    static Overview fromTabs(String kept) {
        Overview open=new Overview();
        if(kept==null||kept.isBlank())return open;
        String list=kept.contains("#")?kept.substring(0,kept.lastIndexOf('#')):kept;
        int current=0;
        if(kept.contains("#"))try{current=Integer.parseInt(kept.substring(kept.lastIndexOf('#')+1).trim());}catch(NumberFormatException e){current=0;}
        List<Overview.Open> tabs=new ArrayList<>();
        for(String one:list.split("\\|")) {
            int colon=one.indexOf(':');if(colon<=0)continue;
            String kind=one.substring(0,colon),id=one.substring(colon+1);
            Overview.Kind as="PAGE".equals(kind)?Overview.Kind.NOTE:"COLLECTION".equals(kind)||"BOOK".equals(kind)?Overview.Kind.COLLECTION:null;
            tabs.add(as==null?null:new Overview.Open(as,id));
        }
        // Opened oldest first, so the one in front ends up first: the rest in their order behind it.
        for(int at=tabs.size()-1;at>=0;at--)if(at!=current&&tabs.get(at)!=null)open.open(tabs.get(at).kind,tabs.get(at).id);
        if(current>=0&&current<tabs.size()&&tabs.get(current)!=null)open.open(tabs.get(current).kind,tabs.get(current).id);
        return open;
    }

    // ---- the list -----------------------------------------------------------------------------------------------------

    private final Desktop pad;
    private Overview open=new Overview();
    /** The cards as last drawn, and the one chosen with the keyboard or Ctrl+Tab. */
    private final List<Card> cards=new ArrayList<>();
    private int chosen=-1;
    /** Put up by Ctrl+Tab and still held: letting go of Ctrl opens the chosen card. */
    private boolean cycling;
    /** The cards side by side: in the middle while they fit across, from the left and sliding once they do not. */
    private final JPanel row=new Row();
    private final JScrollPane along;
    private static final class Row extends JPanel implements Scrollable {
        Row(){super(new FlowLayout(FlowLayout.CENTER,18,8));setOpaque(false);}
        public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        public int getScrollableUnitIncrement(Rectangle r,int o,int d){return 40;}
        public int getScrollableBlockIncrement(Rectangle r,int o,int d){return Math.max(40,r.width-80);}
        public boolean getScrollableTracksViewportWidth(){return getParent() instanceof JViewport v&&v.getWidth()>=getPreferredSize().width;}
        public boolean getScrollableTracksViewportHeight(){return true;}
    }
    private final DesktopUi.Text none=DesktopUi.note("Nothing is open. The notes and collections you open are kept here, the newest first.",420,DesktopUi.QUIET,DesktopUi.BODY.deriveFont(13f));
    private final JButton closeAll;
    private Component before;

    DesktopOverview(Desktop pad) {
        this.pad=pad;
        setLayout(new BorderLayout());setOpaque(false);setVisible(false);setFocusable(true);
        // As tall as a card and its shadow, as wide as the window: never squeezed by a row wider than the window.
        along=new JScrollPane(row,ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED){
            @Override public Dimension getPreferredSize(){return new Dimension(100,HIGH+16+getHorizontalScrollBar().getPreferredSize().height);}
            @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
            @Override public Dimension getMinimumSize(){return getPreferredSize();}
        };
        along.setBorder(null);along.setOpaque(false);along.getViewport().setOpaque(false);along.getHorizontalScrollBar().setUnitIncrement(40);
        along.setAlignmentX(Component.CENTER_ALIGNMENT);none.setAlignmentX(Component.CENTER_ALIGNMENT);
        // The row in the middle of the height, the words in the middle when there are no cards.
        JPanel middle=new JPanel();middle.setLayout(new BoxLayout(middle,BoxLayout.Y_AXIS));middle.setOpaque(false);
        JPanel words=new JPanel(new FlowLayout(FlowLayout.CENTER,0,0));words.setOpaque(false);words.add(none);
        words.setMaximumSize(new Dimension(Integer.MAX_VALUE,none.getPreferredSize().height));
        middle.add(Box.createVerticalGlue());middle.add(along);middle.add(words);middle.add(Box.createVerticalGlue());
        closeAll=DesktopUi.primary("Close all",this::closeAll);
        JPanel foot=new JPanel(new FlowLayout(FlowLayout.CENTER,0,0));foot.setOpaque(false);foot.setBorder(BorderFactory.createEmptyBorder(0,0,28,0));foot.add(closeAll);
        add(middle);add(foot,BorderLayout.SOUTH);
        // A click anywhere that is not a card puts it away, as a click beside every box here does.
        MouseAdapter away=new MouseAdapter(){public void mousePressed(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e))close();}};
        for(JComponent part:new JComponent[]{this,middle,row,foot})part.addMouseListener(away);along.getViewport().addMouseListener(away);
        addMouseWheelListener(e->{JScrollBar bar=along.getHorizontalScrollBar();bar.setValue(bar.getValue()+(int)Math.round(e.getPreciseWheelRotation()*60));});
        bind("ESCAPE",this::close);bind("LEFT",()->move(true));bind("RIGHT",()->move(false));
        bind("ENTER",this::goChosen);bind("SPACE",this::goChosen);bind("DELETE",()->{if(chosen>=0&&chosen<cards.size())closeOne(cards.get(chosen).one);});
    }
    private void bind(String key,Runnable does) {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key),key);
        getActionMap().put(key,new AbstractAction(){public void actionPerformed(ActionEvent e){does.run();}});
    }

    @Override protected void paintComponent(Graphics g) {
        // A sheet over the window, whole: the cards are what is seen. Let through even a little, the page under them read
        // as a ghost of words behind the cards.
        g.setColor(DesktopUi.SHELF);g.fillRect(0,0,getWidth(),getHeight());
    }

    /** What is open, read when the window opens: kept here from now on. */
    void use(Overview read){open=read==null?new Overview():read;}
    List<Overview.Open> all(){return open.all();}
    int count(){return open.size();}
    boolean isEmpty(){return open.isEmpty();}

    /** Opened: to the front. Already at the front, nothing is written. */
    void remember(Overview.Kind kind,String id) {
        if(!open.isEmpty()&&open.all().get(0).equals(new Overview.Open(kind,id)))return;
        if(open.open(kind,id))keep();
    }
    /** Put away or deleted: no card opens what is not there. */
    void forget(String id){if(open.forget(id))keep();}
    /** Nothing open any more, kept so: a backup put in the pad's place leaves nothing of what was open. */
    void clear(){open.closeAll();keep();close();}
    /** One closed, as its × does. */
    void close(Overview.Kind kind,String id){if(open.close(kind,id))keep();}
    private void keep() {
        String said=open.said();
        pad.disk.submit(()->{pad.context.getSharedPreferences("settings",0).edit().putString(KEPT,said).apply();return null;},done->{},e->{});
    }

    boolean showing(){return isVisible();}
    boolean cycling(){return cycling&&isVisible();}

    // ---- up and away --------------------------------------------------------------------------------------------------

    /**
     * Up over the window, the cards read from the notebook as they are now. {@code cycle}: put up by Ctrl+Tab, with the
     * card after the one on the screen chosen, to be opened when Ctrl is let go.
     */
    void up(boolean cycle) {
        if(isVisible()){if(cycle)step(false);return;}
        cycling=cycle;
        before=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        List<Overview.Open> now=open.all();
        chosen=cycle?firstChosen(now.size(),!now.isEmpty()&&pad.showingOpen(now.get(0))):-1;
        place();setVisible(true);requestFocusInWindow();
        cards.clear();row.removeAll();said();
        load(now);
    }

    /** Where it lies: over the tree and Home or the page, under the toolbar and over the status bar. */
    void place() {
        JComponent over=pad.overviewArea();if(over==null||getParent()==null)return;
        setBounds(SwingUtilities.convertRectangle(over.getParent(),over.getBounds(),getParent()));
        revalidate();
    }

    /** Put away: what was under it, as it was, the keyboard back where it was. */
    void close() {
        if(!isVisible())return;
        setVisible(false);cycling=false;
        if(before!=null&&before.isShowing())before.requestFocusInWindow();
        before=null;
    }

    /**
     * Ctrl+Tab: the overview up with the card after the one on the screen chosen - the last, with Shift - or, while it is
     * up, one card along. Letting go of Ctrl then opens the chosen one.
     */
    void cycle(boolean back) {
        if(!isVisible()){up(true);if(back&&open.size()>1)chosen=open.size()-1;for(Card one:cards)one.repaint();return;}
        cycling=true;step(back);
    }

    /** One card along, or back. */
    void step(boolean back) {
        int size=cards.isEmpty()?open.size():cards.size();
        chosen=along(chosen<0?(back?0:-1):chosen,size,back);
        for(Card one:cards)one.repaint();
        if(chosen>=0&&chosen<cards.size())cards.get(chosen).scrollRectToVisible(new Rectangle(cards.get(chosen).getSize()));
    }
    private void move(boolean back){cycling=false;step(back);}

    /** Ctrl let go, or Enter: the chosen card is gone to. */
    void goChosen() {
        List<Overview.Open> now=cards.isEmpty()?open.all():cards.stream().map(c->c.one).toList();
        if(chosen<0||chosen>=now.size()){cycling=false;return;}
        Overview.Open one=now.get(chosen);
        before=null;close();pad.goTo(one);
    }

    /** Every card closed, and the overview with them; what was open on the screen goes too, and Home is what is left. */
    private void closeAll() {
        open.closeAll();keep();
        before=null;close();
        pad.closedEverything();
    }

    /** One card closed: out of the list; the note or the card it stands for, if it is the one on the screen, goes too. */
    private void closeOne(Overview.Open one) {
        close(one.kind,one.id);
        for(int at=0;at<cards.size();at++)if(cards.get(at).one.equals(one)){row.remove(cards.get(at));cards.remove(at);break;}
        if(chosen>=cards.size())chosen=cards.size()-1;
        pad.closed(one);
        row.revalidate();row.repaint();said();
    }

    /** With no card left: the words that say why, and no button with nothing to do. */
    private void said() {
        boolean empty=cards.isEmpty()&&open.isEmpty();
        along.setVisible(!empty);none.getParent().setVisible(empty);closeAll.setVisible(!empty);
        revalidate();repaint();
    }

    /**
     * The cards, read on the disk: each note as it now reads, and each collection with its name, its colour and what it
     * holds. Whatever has been put away or deleted since it was opened is let go of, and has no card.
     */
    private void load(List<Overview.Open> all) {
        pad.disk.submit(()->{
            List<Object[]> shown=new ArrayList<>();List<String> gone=new ArrayList<>();
            for(Overview.Open one:all) {
                NoteStore.Branch.Kind kind=one.kind==Overview.Kind.NOTE?NoteStore.Branch.Kind.PAGE:NoteStore.Branch.Kind.COLLECTION;
                if(!pad.store.stillThere(kind,one.id)){gone.add(one.id);continue;}
                if(one.kind==Overview.Kind.NOTE) {
                    NoteStore.Note note=pad.store.get(one.id);
                    if(note==null){gone.add(one.id);continue;}
                    // And what it wears, for the face before its title.
                    NoteStore.Branch look=new NoteStore.Branch(NoteStore.Branch.Kind.PAGE,note.id,note.book,note.title,"",0,0,false,note.colour);
                    shown.add(new Object[]{one,note,look});
                } else {
                    List<NoteStore.Branch> inside=pad.store.contents(one.id);
                    shown.add(new Object[]{one,new NoteStore.Branch(NoteStore.Branch.Kind.COLLECTION,one.id,"",pad.store.collectionName(one.id),
                        inside.size()+" things",0,0,true,pad.store.colourOf(NoteStore.Branch.Kind.COLLECTION,one.id))});
                }
            }
            // Each card's icon or picture, read in one go for them all.
            List<NoteStore.Branch> looks=new ArrayList<>();
            for(Object[] one:shown)looks.add((NoteStore.Branch)(one.length>2?one[2]:one[1]));
            pad.store.dress(looks);
            return new Object[]{shown,gone};
        },got->{
            @SuppressWarnings("unchecked") List<String> gone=(List<String>)got[1];
            for(String id:gone)forget(id);
            if(!isVisible())return;
            @SuppressWarnings("unchecked") List<Object[]> shown=(List<Object[]>)got[0];
            cards.clear();row.removeAll();
            for(Object[] one:shown){Card card=new Card((Overview.Open)one[0],one[1],one.length>2?(NoteStore.Branch)one[2]:(NoteStore.Branch)one[1]);cards.add(card);row.add(card);}
            if(chosen>=cards.size())chosen=cards.isEmpty()?-1:cards.size()-1;
            said();row.revalidate();row.repaint();
            if(chosen>=0)SwingUtilities.invokeLater(()->{if(chosen>=0&&chosen<cards.size())cards.get(chosen).scrollRectToVisible(new Rectangle(cards.get(chosen).getSize()));});
        },pad::failed);
    }

    /**
     * One open thing, as a card: a note as its paper, with its title and first lines; a collection as its mini-grid, with
     * its name. Clicked, it is gone to; its ×, or a middle click, closes it; right-clicked, Close and then its own menu.
     */
    private final class Card extends JPanel {
        final Overview.Open one;final Object what;boolean over;
        /** What it wears: a note's icon or picture, before its title; a collection's, in its face. */
        final NoteStore.Branch look;
        Card(Overview.Open one,Object what,NoteStore.Branch look) {
            super(null);this.one=one;this.what=what;this.look=look;
            setOpaque(false);setPreferredSize(new Dimension(WIDE,HIGH));setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            JButton x=new JButton("×");x.setFocusPainted(false);x.setMargin(new Insets(0,6,0,6));x.setFont(DesktopUi.BODY.deriveFont(16f));
            x.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            x.setToolTipText("Close");x.getAccessibleContext().setAccessibleName("Close "+name());x.setFocusable(false);
            x.addActionListener(e->closeOne(one));
            Dimension d=x.getPreferredSize();x.setBounds(WIDE-d.width-8,8,d.width,d.height);add(x);
            getAccessibleContext().setAccessibleName((one.kind==Overview.Kind.NOTE?"The note ":"The collection ")+name());
            putClientProperty(DesktopHome.MENU,(java.util.function.Supplier<JPopupMenu>)this::menu);
            addMouseListener(new MouseAdapter(){
                public void mouseClicked(MouseEvent e) {
                    if(SwingUtilities.isMiddleMouseButton(e)){closeOne(one);return;}
                    if(SwingUtilities.isLeftMouseButton(e)){before=null;close();pad.goTo(one);}
                }
                public void mousePressed(MouseEvent e){if(e.isPopupTrigger())menu().show(Card.this,e.getX(),e.getY());}
                public void mouseReleased(MouseEvent e){if(e.isPopupTrigger())menu().show(Card.this,e.getX(),e.getY());}
                public void mouseEntered(MouseEvent e){over=true;repaint();}
                public void mouseExited(MouseEvent e){over=false;repaint();}
            });
        }
        private String name() {
            if(what instanceof NoteStore.Note note) {
                String title=note.title==null?"":note.title.trim();
                if(title.isEmpty()){String body=note.body==null?"":note.body.trim();int line=body.indexOf('\n');title=(line<0?body:body.substring(0,line)).trim();}
                return title.isEmpty()?"Untitled":title;
            }
            return DesktopHome.named((NoteStore.Branch)what);
        }
        /** Close first, since that is what a card is for, then the thing's own menu, as its icon has it. */
        private JPopupMenu menu() {
            NoteStore.Branch thing=what instanceof NoteStore.Note note
                ?new NoteStore.Branch(NoteStore.Branch.Kind.PAGE,note.id,note.book,name(),"",0,0,false,note.colour)
                :(NoteStore.Branch)what;
            JPopupMenu menu=pad.thingMenu(thing);
            JMenuItem closing=new JMenuItem("Close");closing.addActionListener(a->closeOne(one));
            menu.insert(closing,0);menu.insert(new JPopupMenu.Separator(),1);
            return menu;
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Object hints=Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");if(hints instanceof java.util.Map<?,?> map)g.addRenderingHints(map);
            boolean picked=cards.indexOf(this)==chosen;
            for(int i=4;i>=1;i--){g.setColor(new Color(0,0,0,6+i*3));g.fillRoundRect(i-1,i+1,getWidth()-2*i+2,getHeight()-2*i,22,22);}
            Color paper=what instanceof NoteStore.Note note?DesktopLook.wash(note.colour,DesktopUi.PAPER,0.12f,0.72f,pad.tone):DesktopUi.CARD;
            g.setColor(paper);g.fillRoundRect(0,0,getWidth()-1,getHeight()-3,22,22);
            g.setColor(picked?DesktopUi.ACCENT:over?DesktopUi.ACCENT.brighter():DesktopUi.LINE.darker());g.setStroke(new BasicStroke(picked?3f:over?1.6f:1f));
            g.drawRoundRect(1,1,getWidth()-3,getHeight()-5,22,22);
            // The ring's weight is the ring's: the rule under a title stays a hairline on the chosen card too.
            g.setStroke(new BasicStroke(1f));
            int side=WIDE-36;
            if(what instanceof NoteStore.Note note) {
                // Its icon, then its title - or where it has none its first line; a rule; then the first lines, as many as it holds.
                g.setFont(DesktopUi.BODY.deriveFont(Font.BOLD,15f));g.setColor(DesktopUi.INK);FontMetrics m=g.getFontMetrics();
                int face=26,words=18+face+10;
                DesktopHome.face(g,look,18,16,face,pad.tone);g.setColor(DesktopUi.INK);
                List<String> title=DesktopHome.lines(name(),m,side-26-face-10,1);int y=16+(face-m.getHeight())/2+m.getAscent();
                if(!title.isEmpty())g.drawString(title.get(0),words,y);
                y=Math.max(y+m.getDescent(),16+face)+10;g.setStroke(new BasicStroke(1f));g.setColor(DesktopUi.LINE);g.drawLine(18,y,WIDE-18,y);y+=12;
                String body=note.body==null?"":note.body;
                if((note.title==null||note.title.isBlank())&&body.indexOf('\n')>=0)body=body.substring(body.indexOf('\n')+1);
                g.setFont(DesktopUi.BODY);g.setColor(DesktopUi.INK);m=g.getFontMetrics();y+=m.getAscent();
                for(String paragraph:body.trim().split("\n")) {
                    if(y>getHeight()-16)break;
                    if(paragraph.isBlank()){y+=m.getHeight()/2;continue;}
                    for(String line:DesktopHome.lines(paragraph,m,side,3)){if(y>getHeight()-16)break;g.drawString(line,18,y);y+=m.getHeight();}
                }
            } else {
                NoteStore.Branch collection=(NoteStore.Branch)what;
                int face=104,fx=(getWidth()-face)/2,fy=56;
                DesktopHome.face(g,look,fx,fy,face,pad.tone);
                g.setFont(DesktopUi.BODY.deriveFont(Font.BOLD,15f));g.setColor(DesktopUi.INK);FontMetrics m=g.getFontMetrics();
                int y=fy+face+22+m.getAscent();
                for(String line:DesktopHome.lines(name(),m,side,2)){g.drawString(line,(getWidth()-m.stringWidth(line))/2f,y);y+=m.getHeight();}
                g.setFont(DesktopUi.BODY.deriveFont(13f));g.setColor(DesktopUi.QUIET);m=g.getFontMetrics();
                String kind="Collection";g.drawString(kind,(getWidth()-m.stringWidth(kind))/2f,y+4);
            }
            g.dispose();
        }
    }
}
