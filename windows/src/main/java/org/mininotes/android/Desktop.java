package org.mininotes.android;

import org.mininotes.desktop.platform.content.Context;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;

/** Windows UI. Shared Java classes retain their package to avoid widening their APIs. */
public final class Desktop {
    static final Color PAPER=new Color(250,249,244),INK=new Color(43,48,43),QUIET=new Color(111,117,108),ACCENT=new Color(48,99,72);
    // The look is set before a single component is made, fields included: a tree made first keeps the old one.
    static{DesktopUi.install();}
    final JFrame frame=new JFrame("Mininotes");
    final JTextField title=new JTextField();
    final Paper page=new Paper();
    final JLabel connection=new Dot("Connecting…");
    /** Whether this PC's notebook is encrypted, always in sight; a click opens Security. */
    final JLabel security=new JLabel();
    /** This build, beside the name in the bar - and, when a newer one is out, the way to it. */
    static final String VERSION="0.2.012";
    final JLabel version=new JLabel();
    /** Beside the version, only while a newer one is out: an outlined button, so it reads as one to press. */
    final JButton updateButton=new JButton();
    String newestKnown="";
    /** The paperclip at the foot of the note, with how many files the note holds. */
    final JButton clip=new JButton(paperclip());
    /** A paperclip drawn in the quiet grey, one stroke: the emoji one came out in Windows' own colours, looking struck through. */
    static Icon paperclip() {
        return new Icon(){
            public int getIconWidth(){return 16;}public int getIconHeight(){return 20;}
            public void paintIcon(Component c,Graphics g0,int x,int y) {
                Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
                g.setColor(QUIET);g.setStroke(new BasicStroke(1.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                java.awt.geom.Path2D p=new java.awt.geom.Path2D.Float();
                // Inner leg up, round the top, the long outer leg down, round the bottom, and up again.
                p.moveTo(x+6,y+6);p.lineTo(x+6,y+14);
                p.curveTo(x+6,y+16.5,x+10,y+16.5,x+10,y+14);p.lineTo(x+10,y+4.5);
                p.curveTo(x+10,y+0.5,x+3.5,y+0.5,x+3.5,y+4.5);p.lineTo(x+3.5,y+15);
                p.curveTo(x+3.5,y+20.5,x+12.5,y+20.5,x+12.5,y+15);p.lineTo(x+12.5,y+7);
                g.draw(p);g.dispose();
            }
        };
    }
    /** Beside the paperclip: press it and a recording starts, kept with the note when it stops. */
    final JButton mic=new JButton(microphone());
    DesktopRecorder recorder;
    /** A microphone drawn as the paperclip is, in the same grey: the emoji would come in Windows' own colours. */
    static Icon microphone() {
        return new Icon(){
            public int getIconWidth(){return 16;}public int getIconHeight(){return 20;}
            public void paintIcon(Component c,Graphics g0,int x,int y) {
                Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
                g.setColor(QUIET);g.setStroke(new BasicStroke(1.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                // The head, the cradle round it, and the stand.
                g.draw(new java.awt.geom.RoundRectangle2D.Float(x+5,y+1.5f,6,10.5f,6,6));
                java.awt.geom.Path2D p=new java.awt.geom.Path2D.Float();
                p.moveTo(x+2.5,y+9);p.curveTo(x+2.5,y+17,x+13.5,y+17,x+13.5,y+9);
                p.moveTo(x+8,y+15.5);p.lineTo(x+8,y+18.5);p.moveTo(x+5,y+18.5);p.lineTo(x+11,y+18.5);
                g.draw(p);g.dispose();
            }
        };
    }
    /** Three dots in a row: the ⋯ of the bar and of a card, drawn so both are the same. */
    static Icon dots() {
        return new Icon(){
            public int getIconWidth(){return 16;}public int getIconHeight(){return 16;}
            public void paintIcon(Component c,Graphics g0,int x,int y){Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(INK);for(int i=0;i<3;i++)g.fillOval(x+1+i*5+(i>0?i:0),y+7,3,3);g.dispose();}
        };
    }
    final DesktopUi.Text status=DesktopUi.quiet("Opening your pad…"),standing=DesktopUi.quiet(" ");
    /** Before the title, the icon the open note wears; clicked, the box it is chosen in (see DesktopIconPicker). */
    final JButton noteFace=new JButton();
    /** What the open note wears, as last read: its icon's name, empty for its own, and its picture, null for none. */
    private String noteIcon="";private byte[] noteImage;
    /** The open note's sync mark, first on the line under its title, as on the phone (see DesktopMark). */
    final JLabel syncMark=new JLabel();
    SyncMark noteMark;
    /** After the mark, the people the open note reaches: a round each, tinted by where they stand. */
    final JPanel rounds=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));
    /** Who they are, as the rounds were last drawn: what the gallery and the tests look at. */
    java.util.List<SyncStatus.Person> peopleShown=List.of();
    /** After the people, what the open note's syncing is doing, in a few words in the mark's colour (see NoteLine). */
    final JLabel noteWords=new JLabel();
    /** What a click on those words does: null for what the mark does, or the reasons behind "see why". */
    private Runnable wordsClicked;
    private final javax.swing.Timer wordsAway=new javax.swing.Timer(6000,e->noteSays(null,null,null));
    /** Every collection's and note's mark, however deep, read with the tree: drawn on its line and on its card. */
    volatile Map<String,SyncMark> marks=Map.of();
    private static final int TREE_MARK=16;
    /** A line's face in the tree: small enough for a line, big enough that its icon still reads. */
    private static final int TREE_FACE=20;
    /** Taking hold of things and putting them elsewhere, in the tree and on the cards (see DesktopMoving). */
    final DesktopMoving moving=new DesktopMoving(this);
    final JTree tree=new JTree(new DefaultMutableTreeNode("Home")){
        // Where a dragged thing would land is drawn over the lines, after them.
        @Override protected void paintComponent(Graphics g){super.paintComponent(g);moving.paint(this,g);}
    };
    /** The main area: Home, or the note that is open, filling the window (docs/HOME.md, decision 2). */
    private final JPanel mainArea=new JPanel(new CardLayout());
    /** Whether the note is what is on the screen, rather than Home. */
    private boolean onPage;
    /** What is open, the newest first, as cards over the window: the tab bar's place (see DesktopOverview). */
    final DesktopOverview overview=new DesktopOverview(this);
    /** The open note's attachments, as cards under the page. */
    DesktopFileCards fileCards;
    /** Where the cursor was in each note, so going back to it goes back to the same place. */
    private final Map<String,Integer> carets=new HashMap<>();
    /** Home: the grid, the +, the search, the dock, and a collection's card over them (see DesktopHome). */
    DesktopHome home;
    /** Everything, as the tree last drew it. */
    private List<NoteStore.Branch> everything=new ArrayList<>();
    /** What is starred, in the order it was starred, for the tree's Favourites. */
    private volatile List<NoteStore.Branch> favouritesNow=List.of();
    private JPanel side;private JSplitPane split;
    /** How wide the side panel is, as last pulled; the grip that pulls it; the narrowest it is kept at. */
    private int sideWidth=280;private static final int GRIP=9,MIN_SIDE=180;
    /**
     * The line between the side panel and the page. What is seen is one hair-thin rule, and at its middle a small
     * rounded handle with the arrow in it - the handle says the line can be taken hold of, the arrow that it
     * folds. Under the pointer the rule turns green and the pointer becomes the two-way arrow. A click on the
     * handle folds the panel; anywhere else on the line, pressing and pulling changes its width.
     */
    private final class Grip extends javax.swing.plaf.basic.BasicSplitPaneDivider {
        private boolean over;
        Grip(javax.swing.plaf.basic.BasicSplitPaneUI ui) {
            super(ui);setBorder(null);setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
            addMouseListener(new MouseAdapter(){
                public void mouseEntered(MouseEvent e){over=true;repaint();}
                public void mouseExited(MouseEvent e){over=false;repaint();}
                public void mouseClicked(MouseEvent e){if(Math.abs(e.getY()-getHeight()/2)<=HANDLE/2)showTree(false);}
            });
        }
        private static final int HANDLE=34;
        @Override public void paint(Graphics g0) {
            Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight(),mid=w/2;
            g.setColor(PAPER);g.fillRect(0,0,w,h);
            g.setColor(over?ACCENT:DesktopUi.LINE);g.fillRect(over?mid-1:mid,0,over?2:1,h);
            int top=h/2-HANDLE/2;
            g.setColor(over?DesktopUi.mix(ACCENT,Color.WHITE,0.85f):DesktopUi.SHELF);g.fillRoundRect(0,top,w,HANDLE,w,w);
            g.setColor(over?ACCENT:DesktopUi.LINE);g.drawRoundRect(0,top,w-1,HANDLE-1,w,w);
            g.setColor(over?ACCENT:QUIET);g.setStroke(new BasicStroke(1.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            int cy=h/2;g.drawPolyline(new int[]{mid+2,mid-2,mid+2},new int[]{cy-5,cy,cy+5},3);
            g.dispose();
        }
    }
    private final javax.swing.Timer keepWidth=new javax.swing.Timer(600,e->keepSideWidth());
    private void keepSideWidth(){int w=sideWidth;disk.submit(()->{context.getSharedPreferences("settings",0).edit().putString("sideWidth",Integer.toString(w)).apply();return null;},done->{},x->{});}
    /** Whether the tree is wanted beside Home and the page: off unless it was switched on (decision 26). */
    private volatile boolean treeWanted;
    /** Where the tree's switch is kept. A new key, so the tree 0.1 showed by default starts off, as decision 26 has it. */
    static final String TREE="treePanel";
    final Context context;
    final NoteStore store;
    final Keys keys;
    final Background disk=new Background(SwingUtilities::invokeLater),network=new Background(SwingUtilities::invokeLater);
    final Background connectivity=new Background(SwingUtilities::invokeLater);
    final javax.swing.Timer autosave,syncLater;
    private NoteStore.Note base;
    private boolean drawing,dirty,saving;
    /** Something arrived for the open note while it was being written down: the page follows once the writing is back. */
    private boolean followAfterSave;
    /** How many writings of the page have come back, so a read of the notebook overtaken by one is not trusted. */
    private int pageWritings;
    private volatile boolean closing;
    private Runnable afterSave;
    private int treeRequest,noteRequest;
    final boolean offline;
    volatile boolean listenInTray;
    /**
     * Who wrote what, drawn in each writer's colour in a note two or more people wrote in (see Writers): whether it is,
     * and the colours, read from the notebook when a note opens and whenever one is chosen.
     */
    boolean whoWrote=true;
    Writers.Palette palette=Writers.Palette.plain();
    private TrayIcon tray;
    private boolean nodeStarting;
    private String currentAddress="";
    private NoteStore.Branch selected;
    private final Set<String> pendingOffers=new HashSet<>();
    private final Map<String,Sharing.Level> offers=new HashMap<>();
    private final Map<String,Long> offeredAt=new HashMap<>();
    private final Map<String,Long> due=new HashMap<>();
    /**
     * The reading rung and how strongly colours land, as the phone keeps them (see DesktopLook). The rung is this
     * PC's: what every note is read at unless it has one of its own (see Reading).
     */
    int rung=DesktopLook.FIRST,tone=Tint.FIRST_TONE;
    /** The notes with a rung of their own, by id, read with the tree, so a note's menu knows it before it is open. */
    private volatile Map<String,Integer> rungsNow=Map.of();
    /** Where the window was when it closed - Home, or a note - so it opens there again, as the phone does. */
    static final String WHERE="where",HOME_SHOWN="home",NOTE_SHOWN="note:";
    /** The note the window opens on, or null when it opens on Home: the first frame waits for it. */
    private String startNote;
    JScrollPane paperScroll;private JPanel editorPanel;
    private FileChannel instanceChannel;private FileLock instanceLock;

    public static void main(String[] args) {
        try {
            DesktopUi.install();
            Path data=Path.of(System.getenv().getOrDefault("LOCALAPPDATA",System.getProperty("user.home")),"Mininotes");
            boolean offline=false;
            for(int i=0;i<args.length;i++) {
                if(args[i].equals("--data-dir")&&i+1<args.length)data=Path.of(args[++i]);
                else if(args[i].equals("--offline"))offline=true;
                else throw new IllegalArgumentException("Unknown launch argument");
            }
            Path home=data;boolean local=offline;
            // What the shared code says, kept beside the notebook where whoever helps can ask for it (see Log).
            org.mininotes.desktop.platform.util.Log.to(home.resolve("logs"));
            org.mininotes.desktop.platform.util.Log.i("Mininotes/Desktop","started, version "+VERSION);
            // Already open: that window is asked to come forward, and this one goes - before any password is asked.
            if(alreadyOpen(home)){
                // The request says which version is asking. An older Mininotes running hands over to this newer
                // one - it saves and closes, and this one opens once the notebook is free. The same or an older
                // version only asks the running window forward - and hands it the front this start was given, which
                // Windows otherwise refuses it, flashing the taskbar instead.
                if(!Update.newer(VERSION,runningVersion(home)))DesktopLock.letForward();
                try{Files.createDirectories(home);Files.writeString(home.resolve(SHOW_ME),VERSION);}catch(IOException ignored){/* nothing to ask; it is open */}
                long until=System.currentTimeMillis()+20_000;
                while(Update.newer(VERSION,runningVersion(home))&&alreadyOpen(home)&&System.currentTimeMillis()<until)
                    try{Thread.sleep(300);}catch(InterruptedException stop){Thread.currentThread().interrupt();break;}
                if(alreadyOpen(home)){System.exit(0);return;}
            }
            holdWhileRunning(home);
            answerUnanswered(home);
            SwingUtilities.invokeLater(()->{
                try {
                    // A locked notebook is asked for before anything of it is opened. Closing the question closes Mininotes.
                    // The question, or with no lock a box saying it is opening, stays up until the window is whole (see opener).
                    boolean open=DesktopLock.locked(home)?DesktopLock.askAtStart(home,true,opener(home,local))!=null:DesktopLock.opening(opener(home,local));
                    if(!open){System.exit(0);return;}
                }
                // Said, and then really gone: a Mininotes that could not open must not linger with no window.
                catch(Exception e){DesktopLock.tell("Mininotes could not open",e.getMessage());System.exit(1);}
            });
        } catch(Exception e){DesktopLock.tell("Mininotes",e.getMessage());System.exit(1);}
    }

    /** Which version holds the notebook: written by it when it opens, read by a second start. */
    static final String RUNNING="running.version";
    static String runningVersion(Path home){try{return Files.readString(home.resolve(RUNNING)).trim();}catch(IOException none){return "";}}

    /** Left by a second start, for the Mininotes already running to come forward - or, if older, to make way. */
    static final String SHOW_ME="show.request";

    /** The window open in this process, if one is: what a second start is handed to. None while the question is asked. */
    private static volatile Desktop current;

    /** Mininotes started again while this window is open: a newer one is made way for, after saving; any other comes forward. */
    private void asked(String asking) {
        if(Update.newer(asking,VERSION)){status.setText("A newer Mininotes is opening…");save(()->shutdown(true));return;}
        // Still opening: the box saying so is what comes forward, never the window before it is whole.
        if(!revealed){if(DesktopLock.front.mayTake())for(Window w:Window.getWindows())if(w.isShowing())w.toFront();return;}
        // Forward only with the front the second start handed on (see main); without it, asking flashed the taskbar.
        boolean let=DesktopLock.front.mayTake();if(!let)DesktopLock.quietly(frame);
        frame.setVisible(true);Node.near(true);frame.setState(Frame.NORMAL);if(let){frame.toFront();frame.requestFocus();}
    }

    /** Mininotes started again while this one runs: its request is answered here, for as long as the process lives. */
    private static void answerUnanswered(Path home) {
        // One watch for the whole process, answering within half a second. Each window used to keep its own, and
        // the one a window made after locking itself again was seen to answer nothing - a newer version then
        // waited, gave up, and could not open. Now the watch hands the request to whichever window is open, which
        // saves before making way; only with no window open at all - the question for the password on the screen,
        // nothing written to lose - does a newer version take over at once.
        Thread watching=new Thread(()->{
            Path asked=home.resolve(SHOW_ME);
            while(true) {
                try {
                    Thread.sleep(500);
                    if(!Files.exists(asked))continue;
                    String asking=Files.readString(asked).trim();
                    Files.deleteIfExists(asked);
                    SwingUtilities.invokeLater(()->{
                        Desktop open=current;
                        if(open!=null&&!open.closing){open.asked(asking);return;}
                        if(Update.newer(asking,VERSION))System.exit(0);
                        // Locked while put away and opened again now: this is the moment to ask.
                        Runnable waiting=askLater;if(waiting!=null){askLater=null;waiting.run();return;}
                        // The box for the password, forward - if Windows lets it; if not, it stays where it is rather than flash.
                        if(DesktopLock.front.mayTake())for(Window w:Window.getWindows())if(w.isShowing()){w.setAutoRequestFocus(true);w.toFront();w.requestFocus();}
                    });
                } catch(InterruptedException stop){return;}
                catch(Exception notNow){/* looked at again in half a second */}
            }
        },"mininotes-second-start");
        watching.setDaemon(true);watching.start();
    }

    /**
     * Held from start to finish of this process, the question for the password included. The notebook's own
     * lock is let go whenever it locks itself again, and a Mininotes waiting at its question held nothing: a
     * second start saw nothing open and asked for the password beside it, and a newer version never had anybody
     * to hand over to. This one is never let go while the process lives.
     */
    private static FileChannel runningChannel;
    private static FileLock runningLock;
    private static void holdWhileRunning(Path home) {
        try{Files.createDirectories(home);runningChannel=FileChannel.open(home.resolve(RUNNING_LOCK),StandardOpenOption.CREATE,StandardOpenOption.WRITE);runningLock=runningChannel.tryLock();if(runningLock!=null)Files.writeString(home.resolve(RUNNING),VERSION);}
        catch(Exception notHeld){/* then only the notebook's lock says it is open, as before */}
    }
    static final String RUNNING_LOCK="running.lock";

    /** Whether another Mininotes has this notebook open, or is waiting at its question: it holds either lock. */
    static boolean alreadyOpen(Path home){return held(home.resolve(RUNNING_LOCK))||held(home.resolve("notebook.lock"));}
    private static boolean held(Path lock) {
        if(!Files.exists(lock))return false;
        try(FileChannel channel=FileChannel.open(lock,StandardOpenOption.WRITE)) {
            FileLock held=channel.tryLock();
            if(held==null)return true;
            held.release();return false;
        } catch(OverlappingFileLockException same){return true;}
        catch(IOException unreadable){return false;}
    }
    Desktop(Path folder,boolean offline) throws Exception{this(folder,offline,null);}
    Desktop(Path folder,boolean offline,byte[] key) throws Exception {
        this.offline=offline;context=new Context(folder.toFile());if(offline)connection.setText("Offline");
        context.unlock(key);
        if(key==null&&DesktopLock.locked(folder))throw new IllegalStateException("This notebook is locked. Open Mininotes again to type its password.");
        instanceChannel=FileChannel.open(folder.resolve("notebook.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        try{instanceLock=instanceChannel.tryLock();}catch(OverlappingFileLockException e){instanceLock=null;}
        if(instanceLock==null){instanceChannel.close();throw new IllegalStateException("This pad is already open in another window.");}
        store=new NoteStore(context);keys=new Keys(context);
        // Files that arrive with a shared note are sealed with the key this window holds, as any file kept here is.
        NoteStore.fileKey=context::databaseKey;
        // A file fetched, or somebody saying they have one: the open note's cards and its line drawn again.
        Post.filesMoved=note->SwingUtilities.invokeLater(()->{if(base!=null&&base.id.equals(note)){fileCards.show(note);updateStanding();}});
        // Files sent or received on their own moved on - fetched, answered: said, and the list drawn again (see DesktopDrops).
        Post.news=landed->SwingUtilities.invokeLater(()->{if(!closing)DesktopDrops.heard(this,landed);});
        autosave=new javax.swing.Timer(350,e->save(null));autosave.setRepeats(false);
        syncLater=new javax.swing.Timer(1000,e->sendDue());syncLater.start();
        Toolkit.getDefaultToolkit().addAWTEventListener(using,AWTEvent.KEY_EVENT_MASK|AWTEvent.MOUSE_EVENT_MASK|AWTEvent.MOUSE_WHEEL_EVENT_MASK);
        idle=new javax.swing.Timer(20_000,e->relockIfIdle());idle.start();
        // Mininotes started again while this one runs: it asked this window to come forward.
        try{Files.writeString(folder.resolve(RUNNING),VERSION);}catch(IOException ignored){/* an older one simply is not asked to make way */}
        current=this;
        build();
        // Files dropped from Explorer: on a note, attached to it; on a collection, kept with it; on Home, kept there (see DesktopDrops.aim).
        DesktopDrops.takeDrops(this);
        disk.submit(()->{
            var settings=context.getSharedPreferences("settings",0);
            listenInTray="true".equals(settings.getString("listenInTray","false"));
            treeWanted="true".equals(settings.getString(TREE,"false"));
            try{int w=Integer.parseInt(settings.getString("sideWidth","280"));if(w>=MIN_SIDE&&w<=900)sideWidth=w;}catch(NumberFormatException none){/* the usual width */}
            step="reading your notebook";store.getWritableDatabase();
            store.usually=syncAfter();
            // A locked notebook seals any attachment still plain: one kept before the lock covered attachments.
            byte[] opened=context.databaseKey();if(opened!=null){step="sealing attachments";DesktopFiles.every(store,opened,true);}
            step="reading your last note";
            // What was open: the overview, or - the first time a 0.2 build opens - the tabs 0.1 kept, read once into it.
            String kept=settings.getString(DesktopOverview.KEPT,null),where=settings.getString(WHERE,"");
            Overview open=kept!=null?Overview.read(kept):DesktopOverview.fromTabs(settings.getString("tabs",""));
            if(kept==null)settings.edit().putString(DesktopOverview.KEPT,open.said()).putString("tabs","").apply();
            // Where the window was: Home, or a note - the one in front of the old tabs, the first time - else the last written.
            String noteId=where.startsWith(NOTE_SHOWN)?where.substring(NOTE_SHOWN.length())
                :where.isEmpty()&&!open.isEmpty()&&open.all().get(0).kind==Overview.Kind.NOTE?open.all().get(0).id:null;
            NoteStore.Note note=noteId!=null&&store.stillThere(NoteStore.Branch.Kind.PAGE,noteId)?store.get(noteId):null;
            if(note==null&&!HOME_SHOWN.equals(where)) {
                note=store.latest();
                if(note==null){note=new NoteStore.Note();note.book=store.someBook();store.save(note);}
            }
            return new Object[]{note,open};
        },opened->{
            NoteStore.Note note=(NoteStore.Note)opened[0];overview.use((Overview)opened[1]);
            step="drawing your notes";
            tone=DesktopLook.tone(context);showSize(DesktopLook.rung(context));whoWrote=DesktopLook.whoWrote(context);
            startNote=note==null?null:note.id;
            if(note!=null)display(note);else showHome();
            refresh();status.setText(" ");if(!treeWanted)showTree(false);else split.setDividerLocation(sideWidth);if(!offline){startNode();lookForUpdate(false);}
            settle(0);
            // Whether this PC has Windows Hello, asked now so Security can show it the moment it opens.
            connectivity.submit(DesktopHello::supported,yes->{},e->{});},this::openFailed);
    }
    /**
     * The window comes up with the notes already in it, in one step. Shown at once, it was an empty page and an
     * empty tree for the moment the notebook took to read; shown after two seconds whatever it held, a slow unlock
     * still gave that empty window. Now it waits for as long as it takes, behind the box that opened it, which says
     * so (see opener): never an empty window.
     */
    void show(){wanted=true;if(ready)reveal();}
    private boolean wanted,ready,revealed;
    /** What opening is doing, said by the box if it takes long. */
    private volatile String step="opening";
    /** Told once the window is up whole, and why it could not open: the box that waits for it (see opener). */
    private Runnable whenShown;private java.util.function.Consumer<Exception> whenFailed;
    // Never once it is closing: a window let go of, or locked again, is not brought back by a late answer.
    private void reveal(){if(revealed||!wanted||closing)return;revealed=true;frame.setVisible(true);Node.near(true);if(onPage)page.requestFocusInWindow();else home.focusFirst();
        Runnable told=whenShown;whenShown=null;whenFailed=null;if(told!=null)told.run();}
    /** The first page and the tree are drawn: the window may come up. */
    private void drawn(){ready=true;if(wanted)reveal();}
    /**
     * Waits for the disk to have answered all the first drawing asked: the tree, and the note in front with its
     * words, files, writers' colours and people. Each is asked when the one before it answers, so the disk is asked
     * again - a few rounds - until all are in. The disk does one thing at a time, so each round answers after them.
     */
    private void settle(int round){disk.submit(()->null,none->SwingUtilities.invokeLater(()->{if(whole()||round>=8)drawn();else settle(round+1);}),e->drawn());}
    private boolean treeDrawn;private String stoodFor;
    private boolean whole(){return treeDrawn&&home.drawn()&&(startNote==null||base!=null&&base.id.equals(startNote)&&base.id.equals(stoodFor));}
    /** Opening failed before the window came up: said by the box that waits for it, and nothing of it left open. */
    private void openFailed(Exception e){java.util.function.Consumer<Exception> told=whenFailed;if(revealed||told==null){failed(e);return;}whenShown=null;whenFailed=null;told.accept(e);}
    /**
     * How a box opens the notebook with its key (see DesktopLock.Opener): the window is made and fills itself unseen,
     * and comes up whole as the box goes. Slower than ten seconds, the box says what it is still doing; failed, why.
     */
    static DesktopLock.Opener opener(Path folder,boolean offline) {
        return (key,shown,said,failed)->{
            Desktop pad;
            try{pad=new Desktop(folder,offline,key);}catch(Exception e){failed.accept(couldNot(e));return;}
            // Opened again by an update, after the old one closed, the window has no claim to the front: it comes up quietly.
            DesktopLock.quietUnlessLet(pad.frame);
            pad.whenShown=shown;pad.whenFailed=e->pad.shutdown(false,()->failed.accept(couldNot(e)));
            javax.swing.Timer slow=new javax.swing.Timer(10_000,t->{if(!pad.revealed&&!pad.closing)said.accept("Still "+pad.step+"… This is taking longer than usual.");});
            slow.setRepeats(false);slow.start();
            pad.show();
        };
    }
    private static String couldNot(Exception e){return "Your notebook could not be opened. "+(e.getMessage()!=null?e.getMessage():e.getClass().getSimpleName());}
    private void build() {
        DesktopUi.install();
        Font body=DesktopUi.BODY;
        frame.setIconImages(List.of(DesktopIcon.image(16),DesktopIcon.image(32),DesktopIcon.image(48),DesktopIcon.image(256)));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setMinimumSize(new Dimension(820,520));frame.setSize(1120,760);frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){save(()->{if(listenInTray&&ensureTray()){frame.setVisible(false);Node.near(false);}else shutdown(true);});}});
        JPanel shell=new JPanel(new BorderLayout());shell.setBackground(PAPER);

        // The bar: what this is on the left; on the right, whether it is connected, then what can be done, the menu
        // last. Nothing in it is filled: making a note is Home's +, where the thing made appears.
        JPanel bar=new JPanel(new BorderLayout(16,0));bar.setBackground(DesktopUi.SHELF);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,DesktopUi.LINE),BorderFactory.createEmptyBorder(10,16,10,16)));
        JLabel brand=new JLabel("Mininotes",new ImageIcon(DesktopIcon.image(28)),SwingConstants.LEFT);brand.setIconTextGap(10);brand.setFont(body.deriveFont(Font.BOLD,18f));brand.setForeground(INK);
        JPanel left=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));left.setOpaque(false);left.add(brand);left.add(Box.createHorizontalStrut(12));left.add(version);left.add(Box.createHorizontalStrut(12));left.add(updateButton);
        updateButton.setFocusPainted(false);updateButton.setFont(DesktopUi.BODY.deriveFont(Font.BOLD,12.5f));updateButton.setMargin(new Insets(3,12,3,12));
        updateButton.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE,"arc:8;foreground:#FFFFFF;background:#306348;hoverBackground:#2A5840;pressedBackground:#224A35;borderWidth:0;focusWidth:0");
        updateButton.addActionListener(e->{if(staged!=null)restartToUpdate();else updatesBox(null);});
        version.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));version.setIconTextGap(6);
        version.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){lookForUpdate(true);}});
        updateShown();
        JPanel leftMiddle=new JPanel(new GridBagLayout());leftMiddle.setOpaque(false);leftMiddle.add(left);bar.add(leftMiddle,BorderLayout.WEST);
        connection.setForeground(QUIET);connection.setFont(body.deriveFont(13f));connection.setIconTextGap(6);
        connection.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));connection.setToolTipText("Connection details are in Profile");
        connection.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){profile();}});
        JButton menu=button("",()->{});menu.setIcon(dots());
        menu.setToolTipText("More");menu.getAccessibleContext().setAccessibleName("More");menu.addActionListener(e->menu(menu));
        security.setFont(body.deriveFont(13f));security.setIconTextGap(6);security.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        security.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){DesktopLock.settings(Desktop.this);}});
        securityShown();
        // The menu last, at the far right, where a window keeps its menu. Share shares what is open: the note, the
        // card, or on Home the icon that has the keyboard.
        JButton shareButton=button("Share",()->{selected=onPage?null:home.chosen();save(this::share);});
        // Right-click: the few things next to what it does, what it does first.
        DesktopMenus.onRightClick(shareButton,e->shareMenu().show(e.getComponent(),e.getX(),e.getY()));
        JPanel actions=DesktopUi.footer(security,connection,button("Profile",this::profile),shareButton,menu);
        JPanel middle=new JPanel(new GridBagLayout());middle.setOpaque(false);middle.add(actions);bar.add(middle,BorderLayout.EAST);
        shell.add(bar,BorderLayout.NORTH);

        // The tree, an optional panel beside Home and the page (decision 26): everything, on a shade of its own.
        JPanel side=new JPanel(new BorderLayout(0,10));side.setBackground(DesktopUi.SHELF);side.setBorder(BorderFactory.createEmptyBorder(12,12,12,8));
        // The top of the tree is not drawn: its first line is Home, holding everything, and Favourites inside it.
        tree.setRootVisible(false);tree.setShowsRootHandles(true);tree.setRowHeight(30);tree.setBackground(DesktopUi.SHELF);tree.setBorder(BorderFactory.createEmptyBorder(2,0,4,0));
        tree.setCellRenderer(new DefaultTreeCellRenderer(){
            // The wash is drawn here as a rounded box with room above and below, so two coloured rows one under the
            // other read as two things; filled edge to edge by the renderer, they ran into one block.
            private Color wash;
            /** Its sync mark, at the end of the line, as the phone's tree wears it; null for the top and Favourites. */
            private SyncMark mark;
            @Override public void paint(Graphics g0) {
                if(wash!=null&&!selected){Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(wash);g.fillRoundRect(0,3,getWidth(),getHeight()-6,8,8);g.dispose();}
                super.paint(g0);
                if(mark!=null)new DesktopMark(mark,TREE_MARK,true).paintIcon(this,g0,getWidth()-TREE_MARK-4,(getHeight()-TREE_MARK)/2);
            }
            @Override public Dimension getPreferredSize() {
                Dimension d=super.getPreferredSize();
                return mark==null||d==null?d:new Dimension(d.width+TREE_MARK+12,Math.max(d.height,TREE_MARK));
            }
            public Component getTreeCellRendererComponent(JTree t,Object v,boolean s,boolean ex,boolean leaf,int row,boolean focus) {
                Object said=v instanceof DefaultMutableTreeNode node?node.getUserObject():v;
                setTextNonSelectionColor(INK);
                super.getTreeCellRendererComponent(t,v,s,ex,leaf,row,focus);setIcon(null);setBorder(BorderFactory.createEmptyBorder(0,6,0,6));
                boolean page=said instanceof Item item&&item.branch.kind==NoteStore.Branch.Kind.PAGE;
                setFont(page?DesktopUi.BODY:DesktopUi.BODY.deriveFont(Font.BOLD));
                // Before its name, its face, small: its icon or picture in its colour, as its tile on Home wears it.
                int colour=said instanceof Item item?item.branch.colour:Tint.NONE;
                if(said instanceof Item item){NoteStore.Branch line=item.branch;setIcon(DesktopHome.faceIcon(()->line,TREE_FACE,()->tone));setIconTextGap(8);}
                // And its row washed in it, at the pad's strength, as the phone's tree view does.
                wash=Tint.known(colour)?DesktopLook.wash(colour,DesktopUi.SHELF,0.16f,0.82f,tone):null;
                mark=said instanceof Item item&&DesktopMoving.movable(item.branch)?marks.get(item.branch.id):null;
                setBackgroundNonSelectionColor(new Color(0,0,0,0));return this;
            }
        });
        // A line chosen opens what it is: a note fills the window, a collection opens as its card over Home, Home is Home.
        tree.addTreeSelectionListener(e->{if(drawing)return;Object value=((DefaultMutableTreeNode)tree.getLastSelectedPathComponent());
            if(value instanceof DefaultMutableTreeNode node&&node.getUserObject() instanceof Item item){
                if(item.branch.kind==NoteStore.Branch.Kind.FAVOURITES)return;
                NoteStore.Branch real=item.branch.kind==NoteStore.Branch.Kind.PAGE?item.branch:find(item.branch.id)!=null?find(item.branch.id):item.branch;
                if(real.kind==NoteStore.Branch.Kind.PAGE)save(()->open(real.id));else openCollection(real.id);
                selected=real;
            }else if(value instanceof DefaultMutableTreeNode node&&allCollections(node)){selected=null;save(()->{showHome();home.folder.close();});}});
        JScrollPane shelves=DesktopUi.scrolling(tree);side.add(shelves);
        // Every row opens something, so the hand over a row; the arrow over the empty paper below the last one.
        tree.addMouseMotionListener(new MouseMotionAdapter(){public void mouseMoved(MouseEvent e){
            int row=tree.getClosestRowForLocation(e.getX(),e.getY());Rectangle at=row<0?null:tree.getRowBounds(row);
            boolean onRow=at!=null&&e.getY()>=at.y&&e.getY()<at.y+at.height;
            tree.setCursor(Cursor.getPredefinedCursor(onRow?Cursor.HAND_CURSOR:Cursor.DEFAULT_CURSOR));}});
        // Everything in the tree can be renamed and coloured where it stands: right-click it, or F2 to rename.
        tree.addMouseListener(new MouseAdapter(){
            public void mousePressed(MouseEvent e){if(e.isPopupTrigger())shelfMenu(e);}
            public void mouseReleased(MouseEvent e){if(e.isPopupTrigger()&&!moving.carrying())shelfMenu(e);}
            // The mark at the end of a line is a button, as on the phone: waiting sends, anything else shows who has it.
            public void mouseClicked(MouseEvent e) {
                if(!SwingUtilities.isLeftMouseButton(e)||moving.moved())return;
                int row=tree.getRowForLocation(e.getX(),e.getY());if(row<0)return;
                Rectangle at=tree.getRowBounds(row);if(e.getX()<at.x+at.width-TREE_MARK-8)return;
                if(((DefaultMutableTreeNode)tree.getPathForRow(row).getLastPathComponent()).getUserObject() instanceof Item item&&DesktopMoving.movable(item.branch)) {
                    SyncMark what=marks.get(item.branch.id);if(what!=null)markClicked(find(item.branch.id)!=null?find(item.branch.id):item.branch,what);
                }
            }
        });
        // And taken hold of and dragged: among its own kind to a new place, onto a collection to go inside.
        moving.watch(tree);
        tree.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("F2"),"rename");
        tree.getActionMap().put("rename",new AbstractAction(){public void actionPerformed(ActionEvent e){if(selected!=null)rename(selected);}});

        // The page: ← Home and where it lives on one quiet line, its title, the line for whether the others have it, then paper.
        JPanel editor=new JPanel(new BorderLayout());editor.setBackground(PAPER);editorPanel=editor;
        JPanel heading=new JPanel(new BorderLayout(0,6));heading.setOpaque(false);heading.setBorder(BorderFactory.createEmptyBorder(10,30,10,38));
        title.setFont(body.deriveFont(Font.BOLD,24f));title.setBorder(BorderFactory.createEmptyBorder());title.setBackground(PAPER);title.setForeground(INK);title.setToolTipText("Note title (optional)");
        title.putClientProperty(com.formdev.flatlaf.FlatClientProperties.PLACEHOLDER_TEXT,"Untitled");
        title.setEditable(false);title.getAccessibleContext().setAccessibleName("Note title");heading.add(title);standing.setForeground(QUIET);standing.setFont(body.deriveFont(12.5f));
        // Where the note lives, small, above its title; under it, where it stands, in marks and no words: the sync
        // mark, then a round for each person it reaches, tinted by where they stand. The words were "Shared with
        // Ana · not sent yet – send now" beside a green mark, and the colour said the opposite of the words. The
        // mark is a click - waiting sends now, anything else opens who has it - and so is each round, which says
        // who and where in a small box. Nothing shows under the pointer; Settings says what each mark means.
        // At the left of that line, ← Home, as the phone's note page has its arrow back (decision 24); Esc does the same.
        JButton back=new JButton("Home",backIcon());back.setIconTextGap(6);
        back.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        back.setFont(body.deriveFont(13f));back.setForeground(INK);back.setFocusPainted(false);back.setMargin(new Insets(4,6,4,8));
        back.setToolTipText("Back to Home (Esc)");back.getAccessibleContext().setAccessibleName("Back to Home");back.addActionListener(e->goHome());
        JPanel where=new JPanel(new BorderLayout(14,0));where.setOpaque(false);where.add(back,BorderLayout.WEST);where.add(standing);
        heading.add(where,BorderLayout.NORTH);
        JPanel titled=new JPanel(new BorderLayout(10,0));titled.setOpaque(false);titled.setBorder(BorderFactory.createEmptyBorder(0,12,0,0));
        heading.remove(title);titled.add(title);heading.add(titled);
        // Before the title, the note's icon, as its tile on Home wears it (docs/HOME.md, step 4): a click chooses another.
        noteFace.setIcon(DesktopHome.faceIcon(()->base==null?null:noteLook(),30,()->tone));
        noteFace.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        noteFace.setMargin(new Insets(3,3,3,3));noteFace.setFocusPainted(false);noteFace.setToolTipText("Change its icon");
        noteFace.getAccessibleContext().setAccessibleName("The note's icon: choose another");
        noteFace.addActionListener(e->{if(base!=null)DesktopIconPicker.open(this,openNote());});
        JPanel faceHolder=new JPanel(new GridBagLayout());faceHolder.setOpaque(false);faceHolder.add(noteFace);titled.add(faceHolder,BorderLayout.WEST);
        JPanel details=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));details.setOpaque(false);details.setBorder(BorderFactory.createEmptyBorder(0,12,0,0));
        syncMark.setBorder(BorderFactory.createEmptyBorder(0,0,0,10));syncMark.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));syncMark.setVisible(false);
        syncMark.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(base!=null&&noteMark!=null)markClicked(openNote(),noteMark);}});
        rounds.setOpaque(false);
        // Then what its syncing is doing, in words: beside the mark it is about, not on the bar at the foot, where
        // the eye that went up to the mark did not look. A click does what the mark does, or shows why.
        noteWords.setFont(body.deriveFont(13f));noteWords.setBorder(BorderFactory.createEmptyBorder(0,6,0,0));
        noteWords.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));noteWords.setVisible(false);wordsAway.setRepeats(false);
        noteWords.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){
            if(wordsClicked!=null)wordsClicked.run();else if(base!=null&&noteMark!=null)markClicked(openNote(),noteMark);}});
        details.add(syncMark);details.add(rounds);details.add(noteWords);
        heading.add(details,BorderLayout.SOUTH);editor.add(heading,BorderLayout.NORTH);
        page.setFont(body.deriveFont(18f));page.setForeground(INK);page.setBackground(PAPER);page.setLineWrap(true);page.setWrapStyleWord(true);page.setBorder(BorderFactory.createEmptyBorder(12,42,40,38));
        page.setEditable(false);page.getAccessibleContext().setAccessibleName("Note text");page.setTabSize(4);page.cannotOpen=this::failed;
        JScrollPane paper=DesktopUi.scrolling(page);paperScroll=paper;
        // The paperclip sits on the page itself, in its lower right corner, with how many files the note holds:
        // a band of its own at the foot took a strip of the window from the writing for one small button.
        clip.setToolTipText("Attachments");clip.getAccessibleContext().setAccessibleName("Attachments");
        clip.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        clip.setFont(body.deriveFont(13f));clip.setForeground(QUIET);clip.setIconTextGap(4);clip.addActionListener(e->clipMenu());
        // The microphone beside it: recording is attaching something made here and now. While it records, the
        // bar takes the microphone's place, so there is one Stop and nothing to start a second time.
        mic.setToolTipText("Record audio");mic.getAccessibleContext().setAccessibleName("Record audio");
        mic.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        mic.addActionListener(e->record());
        recorder=new DesktopRecorder(this);
        JPanel foot=new JPanel(new FlowLayout(FlowLayout.RIGHT,0,0));foot.setOpaque(false);foot.setBorder(BorderFactory.createEmptyBorder(0,16,6,22));foot.add(recorder);foot.add(mic);foot.add(clip);
        // The files themselves, folded into the paperclip: the pointer on it slides them up over the foot of the
        // page, and they fold again a moment after it leaves both.
        fileCards=new DesktopFileCards(this);
        JPanel drawer=new JPanel(new BorderLayout());drawer.setOpaque(false);
        drawer.add(fileCards,BorderLayout.CENTER);drawer.add(foot,BorderLayout.SOUTH);
        JPanel sheet=new JPanel(null){
            @Override public boolean isOptimizedDrawingEnabled(){return false;}
            @Override public void doLayout() {
                int w=getWidth(),h=getHeight();paper.setBounds(0,0,w,h);
                int bar=paper.getVerticalScrollBar().isVisible()?paper.getVerticalScrollBar().getWidth():0;
                Dimension d=drawer.getPreferredSize();drawer.setBounds(0,h-d.height,w-bar,d.height);
            }
            @Override public Dimension getPreferredSize(){return paper.getPreferredSize();}
        };
        sheet.setOpaque(false);sheet.add(drawer);sheet.add(paper);editor.add(sheet);
        javax.swing.Timer fold=new javax.swing.Timer(250,null);
        long[] away={0};
        fold.addActionListener(e->{
            java.awt.Point at=MouseInfo.getPointerInfo()==null?null:MouseInfo.getPointerInfo().getLocation();
            boolean over=false;
            if(at!=null&&drawer.isShowing()){for(Component c:new Component[]{clip,fileCards})if(c.isShowing()){java.awt.Point o=c.getLocationOnScreen();if(new Rectangle(o,c.getSize()).contains(at))over=true;}}
            if(over){away[0]=0;return;}
            if(away[0]==0){away[0]=System.currentTimeMillis();return;}
            if(System.currentTimeMillis()-away[0]>500){fileCards.open(false);sheet.revalidate();sheet.repaint();fold.stop();away[0]=0;}
        });
        clip.addMouseListener(new MouseAdapter(){public void mouseEntered(MouseEvent e){if(fileCards.isOpen())return;fileCards.open(true);sheet.revalidate();sheet.repaint();away[0]=0;fold.start();}});
        // The main area is Home, or the open note filling the window (decision 2).
        home=new DesktopHome(this);
        mainArea.add(home,"home");mainArea.add(editor,"page");
        // Where the note lives, clicked: that collection's card over Home, or Home.
        standing.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));standing.setToolTipText("Open the collection it is in");
        standing.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(base!=null){
            if(NoteStore.home(base.book))goHome();else openCollection(base.book);}}});
        this.side=side;
        JPanel pageSide=new JPanel(new BorderLayout());pageSide.setBackground(PAPER);pageSide.add(mainArea);
        // The line between the panel and the page can be taken and pulled. It was one pixel wide, which no hand
        // can catch; it is six now, but painted as the page with one thin rule at the panel's edge, so what is
        // seen is still a single line. The width chosen is kept for next time.
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,side,pageSide);this.split=split;split.setDividerLocation(sideWidth);split.setDividerSize(GRIP);split.setBorder(BorderFactory.createEmptyBorder());
        split.setContinuousLayout(true);keepWidth.setRepeats(false);
        side.setMinimumSize(new Dimension(MIN_SIDE,0));pageSide.setMinimumSize(new Dimension(420,0));
        split.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI(){
            @Override public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider(){return new Grip(this);}
        });
        split.setDividerSize(GRIP);split.setBorder(BorderFactory.createEmptyBorder());
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,e->{
            if(!side.isVisible()||split.getDividerLocation()<MIN_SIDE)return;
            sideWidth=split.getDividerLocation();keepWidth.restart();});
        shell.add(split);

        // What just happened, on a quiet bar of its own at the foot.
        status.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,DesktopUi.LINE),BorderFactory.createEmptyBorder(7,16,7,16)));
        status.setForeground(QUIET);status.setFont(body.deriveFont(13f));status.setOpaque(true);status.setBackground(DesktopUi.SHELF);shell.add(status,BorderLayout.SOUTH);
        // The bar is there only while it has something to say. Empty, it was a strip of the window kept for nothing;
        // now it comes with a message and goes a few seconds later - except while something is going on (a
        // message ending in "…"), which stays until the work that said it says how it ended.
        javax.swing.Timer quiet=new javax.swing.Timer(6000,e->status.setVisible(false));quiet.setRepeats(false);
        status.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            void said(){SwingUtilities.invokeLater(()->{String now=status.getText().trim();quiet.stop();
                status.setVisible(!now.isEmpty());if(!now.isEmpty()&&!now.endsWith("…"))quiet.restart();});}
            public void insertUpdate(javax.swing.event.DocumentEvent e){said();}
            public void removeUpdate(javax.swing.event.DocumentEvent e){said();}
            public void changedUpdate(javax.swing.event.DocumentEvent e){said();}
        });frame.setContentPane(shell);frame.setFocusTraversalPolicy(DesktopUi.skippingText());
        title.getDocument().addDocumentListener(watch(this::edited));page.getDocument().addDocumentListener(watch(this::edited));
        // The overview lies over the window, above Home and the page, and follows the window's size.
        frame.getLayeredPane().add(overview,JLayeredPane.PALETTE_LAYER);
        frame.getLayeredPane().addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent e){if(overview.showing())SwingUtilities.invokeLater(overview::place);}});
        bind("control B",()->showTree(!side.isVisible()));
        // Ctrl+W closes what is open, as it closed a tab: the note goes from the overview, and Home is what is left.
        bind("control W",()->{if(onPage&&base!=null){String id=base.id;save(()->{overview.close(Overview.Kind.NOTE,id);goHome();});}
            else if(home.folder.isOpen()){NoteStore.Branch card=home.folder.shown();if(card!=null)overview.close(Overview.Kind.COLLECTION,card.id);home.folder.close();}});
        // Ctrl+Tab puts up the overview and moves along its cards, as Alt+Tab does with windows; letting go of Ctrl
        // opens the one chosen. Taken before the text areas can use it to move focus.
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e->{
            if(!frame.isActive())return false;
            if(e.getID()==KeyEvent.KEY_RELEASED&&e.getKeyCode()==KeyEvent.VK_CONTROL&&overview.cycling()){overview.goChosen();return false;}
            if(e.getID()!=KeyEvent.KEY_PRESSED||e.getKeyCode()!=KeyEvent.VK_TAB||!e.isControlDown())return false;
            boolean backward=e.isShiftDown();save(()->overview.cycle(backward));return true;});
        // The Menu key and Shift+F10: the menu of whatever has the keyboard, as a right-click on it would open.
        bind("shift F10",this::contextKey);bind("CONTEXT_MENU",this::contextKey);
        // And a right-click on the page or its title opens the same menu there.
        DesktopMenus.onRightClick(page,this::paperClicked);DesktopMenus.onRightClick(title,this::paperClicked);
        bind("control N",()->save(this::newNote));bind("control S",()->save(null));
        bind("control F",()->save(()->{showHome();home.focusSearch();}));
        // Esc: a menu that is up goes first, then the overview; on a note, back to Home (decision 24); on Home, out of
        // the card a level at a time, then the search emptied.
        bind("ESCAPE",this::escape);
        javax.swing.undo.UndoManager undo=new javax.swing.undo.UndoManager();page.getDocument().addUndoableEditListener(e->{if(!drawing)undo.addEdit(page.remembered(e.getEdit()));});
        page.putClientProperty("undo",undo);bind("control Z",()->{if(page.isEditable()&&undo.canUndo())undo.undo();});bind("control Y",()->{if(page.isEditable()&&undo.canRedo())undo.redo();});
    }    JButton button(String text,Runnable action){return DesktopUi.button(text,action);}
    private void bind(String key,Runnable action){frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key),key);frame.getRootPane().getActionMap().put(key,new AbstractAction(){public void actionPerformed(ActionEvent e){action.run();}});}
    private static DocumentListener watch(Runnable action){return new DocumentListener(){public void insertUpdate(DocumentEvent e){action.run();}public void removeUpdate(DocumentEvent e){action.run();}public void changedUpdate(DocumentEvent e){action.run();}};}
    private void edited(){if(drawing||base==null)return;dirty=true;due.remove(base.id);noteSays(NoteLine.SAVING,NoteLine.Tone.GOING,null);
        // Written in, and shared: waiting to go at once, as the phone's mark turns to the arrow.
        if(noteMark!=null&&waitingOver(noteMark)!=noteMark&&typedHere())showNoteMark(waitingOver(noteMark));
        autosave.restart();}
    /**
     * Whether the page holds writing the notebook does not: touched since it was last written down, and saying
     * something other than the stored note it came from. A key typed and taken back is not writing; nor is a page
     * showing text older than the notebook's, which only ever says what the notebook said when it was drawn.
     */
    boolean typedHere() {
        return dirty&&base!=null&&(!page.getText().equals(Objects.toString(base.body,""))||!title.getText().equals(Objects.toString(base.title,"")));
    }
    void save(Runnable next) {
        if(next!=null)afterSave=next;
        autosave.stop();if(saving)return;
        // Nothing typed that the notebook does not say already: nothing is written, so nothing goes - above all not the
        // page's older text, written over what arrived one revision higher and sent back to the devices that wrote it.
        if(dirty&&!typedHere()){dirty=false;if(NoteLine.SAVING.equals(noteWords.getText()))noteSays(null,null,null);updateStanding();}
        if(!dirty||base==null){Runnable go=afterSave;afterSave=null;if(go!=null)go.run();return;}
        saving=true;String body=page.getText(),name=title.getText();NoteStore.Note seen=base.copy();Writers runs=page.writers();
        disk.submit(()->DesktopEdits.save(store,seen,name,body,runs),saved->{
            saving=false;String now=page.getText(),nowTitle=title.getText();
            boolean changed=!now.equals(body)||!nowTitle.equals(name);
            drawing=true;
            String merged=changed?Merge.merge(body,now,saved.body).text:saved.body;
            String mergedTitle=changed?Merge.merge(name,nowTitle,saved.title).text:saved.title;
            if(!page.getText().equals(merged)) {
                // What was typed while it was written down stays yours; what came with the writing keeps its writers.
                redraw(merged,changed?Writers.follow(merged,Writers.UNKNOWN,now,page.writers(),saved.body,saved.writers):saved.writers);
                ((javax.swing.undo.UndoManager)page.getClientProperty("undo")).discardAllEdits();
            }
            if(!title.getText().equals(mergedTitle))title.setText(mergedTitle);
            drawing=false;
            // A size chosen while it was being written down is the page's, not the older one the notebook gave back.
            if(base!=null&&base.id.equals(saved.id))saved.rung=base.rung;
            base=saved;pageWritings++;dirty=!page.getText().equals(saved.body)||!title.getText().equals(saved.title);
            // Written in, it is one of the things open: a blank page nothing was written on never was.
            if(onPage&&written(saved))overview.remember(Overview.Kind.NOTE,saved.id);
            if(NoteLine.SAVING.equals(noteWords.getText()))noteSays(null,null,null);refresh();
            if(dirty){save(null);return;}scheduleSync();
            // Something arrived for it while it was being written down: the page now follows it (see changedUnderneath).
            if(followAfterSave){followAfterSave=false;changedUnderneath(saved.id);}
            Runnable go=afterSave;afterSave=null;if(go!=null)go.run();
        },error->{saving=false;afterSave=null;
            org.mininotes.desktop.platform.util.Log.i("Mininotes/Page","the open note was not saved: "+error.getClass().getSimpleName());
            // Why, on the bar as every error is; that it did not save, and the click that tries again, where it was said to be saving.
            noteSays("Not saved - click to try again",NoteLine.Tone.FAILED,()->save(null));failed(error);
            // A writing that cannot be kept must not keep the page on older text either: what arrived is put with it.
            if(followAfterSave&&base!=null){followAfterSave=false;changedUnderneath(base.id);}});
    }
    /**
     * The note that is open changed in the notebook from somewhere else - a device's writing arrived, or waited in the
     * inbox while the notebook was locked. The page is a copy taken when it was drawn, and the next word typed on it
     * would otherwise be put against that older text and send the old lines back. As on the phone (see
     * {@link Arriving#onThePage}): a page nobody typed on simply becomes what the notebook says, with no writing of its
     * own and nothing Ctrl+Z could bring back; what was typed is put with what arrived, and written down.
     *
     * <p>Asked of the notebook on the disk worker, behind anything already on its way there. A save on its way is let
     * finish first - it merges against the notebook as it is by then - and the page follows once it is back.
     */
    private void changedUnderneath(String id) {
        if(base==null||id==null||!base.id.equals(id))return;
        if(saving){followAfterSave=true;return;}
        int asOf=pageWritings;
        disk.submit(()->{NoteStore.Note read=store.get(id);if(read!=null)read.writers=store.writersOf(id,read.body);
            return new Object[]{read,Boolean.TRUE.equals(store.readOnlyHere(id)[0])};},got->{
            NoteStore.Note stored=(NoteStore.Note)got[0];
            if(stored==null||base==null||!base.id.equals(id))return;
            if(saving){followAfterSave=true;return;}
            // Written down again while this was read: what was read may be older than the page now is, so it is read again.
            if(pageWritings!=asOf){changedUnderneath(id);return;}
            title.setEditable(!(Boolean)got[1]);page.setEditable(!(Boolean)got[1]);
            String keptTitle=Objects.toString(base.title,""),storedTitle=Objects.toString(stored.title,"");
            if(stored.revision==base.revision&&Objects.equals(stored.body,base.body)&&storedTitle.equals(keptTitle)){updateStanding();return;}
            boolean typed=typedHere();
            Arriving.Page said=Arriving.onThePage(base.body,page.getText(),stored.body,typed);
            Arriving.Page named=Arriving.onThePage(keptTitle,title.getText(),storedTitle,typed);
            org.mininotes.desktop.platform.util.Log.i("Mininotes/Page",(said.unsaved||named.unsaved?"the open note changed elsewhere, and what was typed here is put with it"
                :typed?"the open note changed elsewhere, and what was typed here was already in it"
                :"the open note changed elsewhere, and the page, not typed in, now says what arrived")+" (revision "+base.revision+" to "+stored.revision+")");
            autosave.stop();base=stored;showSize(rung);
            // Drawn, not typed: no undo entry, and none of the old ones, which would put the older text back.
            drawing=true;
            if(!page.getText().equals(said.text)) {
                // What was typed here keeps being yours, and what arrived is whoever the notebook says wrote it.
                redraw(said.text,typed?Writers.follow(said.text,Writers.UNKNOWN,page.getText(),page.writers(),stored.body,stored.writers):stored.writers);
                ((javax.swing.undo.UndoManager)page.getClientProperty("undo")).discardAllEdits();
            }
            if(!title.getText().equals(named.text))title.setText(named.text);
            drawing=false;
            // Words typed since the last writing are on the page and nowhere else, so it is written again.
            dirty=said.unsaved||named.unsaved;
            if(dirty)autosave.restart();else if(NoteLine.SAVING.equals(noteWords.getText()))noteSays(null,null,null);
            updateStanding();
        },this::failed);
    }
    /**
     * The page says something else now - the note changed underneath it - and the reader stays where they were: the
     * first line they could see, the caret and the selection are each taken to the same words (see Writers.moved), and
     * the view is put back on that line, never scrolled to the caret. Lines arriving above push the page down with the
     * words that were being read. Drawn, not typed: whoever calls this holds {@code drawing}.
     */
    private void redraw(String text,Writers who) {
        String was=page.getText();
        JViewport port=paperScroll==null?null:paperScroll.getViewport();
        Rectangle view=port==null?null:port.getViewRect();
        int top=0,into=0;
        if(view!=null)try {
            top=page.viewToModel2D(new java.awt.Point(view.x,view.y));
            java.awt.geom.Rectangle2D line=page.modelToView2D(top);
            if(line!=null)into=view.y-(int)Math.round(line.getY());
        } catch(Exception notLaidOut){top=0;into=0;}
        int dot=page.getCaret().getDot(),mark=page.getCaret().getMark();
        int[] at=Writers.moved(was,text,top,mark,dot);
        page.holding(true);
        page.put(text,who);
        int length=page.getDocument().getLength();
        page.getCaret().setDot(Math.min(at[1],length));
        if(at[2]!=at[1])page.getCaret().moveDot(Math.min(at[2],length));
        int anchor=Math.min(at[0],length),below=into;
        Runnable back=()->{
            if(port!=null)try {
                java.awt.geom.Rectangle2D line=page.modelToView2D(anchor);
                if(line!=null)port.setViewPosition(new java.awt.Point(view.x,Math.max(0,(int)Math.round(line.getY())+below)));
            } catch(Exception notLaidOut){/* where it is */}
        };
        // Now; and once more after the page has been laid out again and the caret has had its say, which it has a moment
        // after it moves - only then does it scroll the page again.
        back.run();SwingUtilities.invokeLater(()->{back.run();page.holding(false);});
    }
    private void display(NoteStore.Note note) {
        overview.close();
        ((CardLayout)mainArea.getLayout()).show(mainArea,"page");onPage=true;keepWhere(NOTE_SHOWN+note.id);
        peopleShown=List.of();DesktopMark.people(rounds,peopleShown,24);
        selected=null;
        // What was said about the last note is not about this one; the same note drawn again keeps it.
        if(base==null||!base.id.equals(note.id))noteSays(null,null,null);
        recorder.leaving(note.id);
        page.setEditable(false);title.setEditable(false);
        if(base!=null)carets.put(base.id,page.getCaretPosition());
        // Opened at the top, the caret at its start (the owner, 2026-10-02: a note opened where it was left, in the middle,
        // and the beginning had to be scrolled back to); the same note drawn again keeps its place.
        int at=base!=null&&base.id.equals(note.id)?page.getCaretPosition():0;
        // The note's own size before its words, so they are never drawn at another note's first.
        drawing=true;base=note;dirty=false;showSize(rung);title.setText(note.title);page.put(note.body,note.writers);
        page.setCaretPosition(Math.min(at,page.getDocument().getLength()));drawing=false;
        // One of the things open, for the overview; not a blank page nothing was written on, as on the phone.
        if(written(note))overview.remember(Overview.Kind.NOTE,note.id);
        fileCards.show(note.id);washPage();syncMark.setVisible(false);noteMark=null;
        // Its icon as the tree last read it, at once, so the face before the title is never another note's; read again below.
        NoteStore.Branch known=find(note.id);noteIcon=known==null?"":known.icon;noteImage=known==null?null:known.image;noteFace.repaint();
        ((javax.swing.undo.UndoManager)page.getClientProperty("undo")).discardAllEdits();
        disk.submit(()->Boolean.TRUE.equals(store.readOnlyHere(note.id)[0]),readOnly->{if(base==null||!base.id.equals(note.id))return;title.setEditable(!readOnly);page.setEditable(!readOnly);updateStanding();},this::failed);
        askWriters(note.id,note.body);
        page.requestFocusInWindow();
    }
    /**
     * Who wrote what on a note just opened, and the colours they are drawn in, asked of the notebook once its words are
     * on the page: the letters take their writers as they come, and anything typed meanwhile stays yours.
     */
    private void askWriters(String id,String body) {
        disk.submit(()->new Object[]{store.palette(),store.writersOf(id,body)},got->{
            palette=(Writers.Palette)got[0];page.inks(palette,whoWrote);
            if(base!=null&&base.id.equals(id)){page.follow(body,(Writers)got[1]);rounds.repaint();}
        },this::failed);
    }
    /** "Show who wrote what", turned on or off: the page at once, and kept for the next time. */
    void setWhoWrote(boolean on) {
        whoWrote=on;page.inks(palette,on);updateStanding();
        disk.submit(()->{DesktopLook.keepWhoWrote(context,on);return null;},done->{},this::failed);
    }
    /** A writer's colour chosen here: kept on this PC, and the page drawn in it at once. */
    void chooseInk(String writer,int colour) {
        disk.submit(()->{store.chooseInk(writer,colour);return store.palette();},now->{palette=now;page.inks(palette,whoWrote);updateStanding();},this::failed);
    }
    /** The rounds a writer's colour is chosen from, for the writer a device's writing is, or for you where there is none. */
    void inksOf(String address,java.util.function.Consumer<JComponent> then) {
        inkRow(address,(you,row)->{
            JPanel box=DesktopUi.column();
            DesktopUi.add(box,DesktopUi.quiet(you?"Your writing colour":"Writing colour"));DesktopUi.gap(box,4);
            DesktopUi.add(box,row);
            then.accept(box);
        });
    }
    /** The rounds alone, and whether they are yours. */
    void inkRow(String address,java.util.function.BiConsumer<Boolean,JPanel> then) {
        disk.submit(()->new Object[]{address==null?Writers.ME:store.writerOf(address),store.palette()},got->{
            palette=(Writers.Palette)got[1];String writer=(String)got[0];
            String who=Writers.ME.equals(palette.person(writer))?Writers.ME:palette.person(writer);
            then.accept(Writers.ME.equals(who),DesktopLook.inks(palette,writer,colour->chooseInk(who,colour)));
        },this::failed);
    }
    /** The colour each person on the open note's line writes in, by their device, as last read. */
    private Map<String,Integer> roundInks=Map.of();
    /**
     * The colour a person's round wears: their writing colour while the page is drawn in its writers' colours, so the
     * round says whose the coloured words are; none otherwise, and the round is as it always was.
     */
    private int roundInk(SyncStatus.Person who){return page.inked()?roundInks.getOrDefault(who.address(),Tint.NONE):Tint.NONE;}
    /** A linked person's round clicked: who, where they stand, and the colour their writing is drawn in here. */
    void personClicked(SyncStatus.Person who,Component round) {
        inksOf(who.address(),inks->{if(round.isShowing())DesktopMark.told(who,round,inks).show(round,0,round.getHeight()+4);});
    }
    /**
     * A person on a list of people, whose round opens the colour their writing is drawn in here, as a round under a
     * note's title does. {@code address} is their device; null for you.
     */
    JPanel inkOnRound(JPanel person,String name,String address) {
        Component round=roundOf(person);
        round.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if(round instanceof JComponent c) {
            c.setToolTipText("Writing colour of "+name);
            // A bare round has no accessible context of its own; the words beside it name the person.
            if(c.getAccessibleContext()!=null)c.getAccessibleContext().setAccessibleName("Writing colour of "+name);
        }
        round.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){
            if(SwingUtilities.isLeftMouseButton(e))inks(round,name,address);
        }});
        return person;
    }
    /** Who, and the colour their writing is drawn in here, in a small box under their round. */
    void inks(Component round,String name,String address) {
        inksOf(address,inks->{
            JPanel words=DesktopUi.column();words.setBorder(BorderFactory.createEmptyBorder(10,14,10,14));
            DesktopUi.add(words,DesktopUi.body(name));DesktopUi.gap(words,8);DesktopUi.add(words,inks);
            JPopupMenu box=new JPopupMenu();box.setBackground(DesktopUi.CARD);box.add(words);
            if(round.isShowing())box.show(round,0,round.getHeight()+4);
        });
    }
    /** The round a person line from inkOnRound begins with. */
    private static Component roundOf(JPanel person){return ((JPanel)person.getComponent(0)).getComponent(0);}
    /**
     * A round under the title, right-clicked, as the phone's is held: linking with somebody not linked yet, first;
     * their writing colour; who has the note; and, for somebody not linked, taking them off its list, last.
     */
    JPopupMenu roundMenu(SyncStatus.Person who,Component round) {
        JPopupMenu menu=new JPopupMenu();
        if(!who.linked())item(menu,Unsent.link(who.called()),()->link(who.listing()));
        item(menu,"Writing colour",()->personClicked(who,round));
        item(menu,"Sharing",()->{if(base!=null){selected=openNote();save(this::share);}});
        if(!who.linked()&&who.listing()!=null){menu.addSeparator();item(menu,Unsent.takeOff(who.listedIn()),()->takeOff(who.called(),who.listedIn(),who.listing()));}
        return menu;
    }
    void refreshStanding(){updateStanding();}
    private void updateStanding() {
        if(base==null)return;String id=base.id;
        disk.submit(()->{
            String shelf=shelfLine(store,id);
            int files=store.filesOf(NoteStore.Branch.Kind.PAGE,id).size();SwingUtilities.invokeLater(()->{if(base!=null&&base.id.equals(id)){clip.setText(files==0?"":Integer.toString(files));if(!fileCards.shows(id,files))fileCards.show(id);}});
            // Who it reaches and where each stands, and the mark worked out from them as the phone does (SyncStatus.mark).
            List<SyncStatus.Person> who=SyncStatus.who(store,id);
            // And the colour each one's writing is drawn in, so a round says whose the coloured words are.
            Writers.Palette inks=store.palette();Map<String,Integer> colours=new HashMap<>();
            for(SyncStatus.Person one:who)colours.put(one.address(),inks.colourOf(store.writerOf(one.address())));
            return new Object[]{shelf+(Boolean.TRUE.equals(store.readOnlyHere(id)[0])?"  ·  Read only":""),who,SyncStatus.mark(store,id,who,false),colours,
                store.iconOf(NoteStore.Branch.Kind.PAGE,id),store.imageOf(NoteStore.Branch.Kind.PAGE,id)};
        },text->{if(base!=null&&base.id.equals(id)){stoodFor=id;
            standing.setText((String)text[0]);
            noteIcon=(String)text[4];noteImage=(byte[])text[5];noteFace.repaint();
            @SuppressWarnings("unchecked") List<SyncStatus.Person> who=(List<SyncStatus.Person>)text[1];
            @SuppressWarnings("unchecked") Map<String,Integer> colours=(Map<String,Integer>)text[3];
            peopleShown=who;roundInks=colours;DesktopMark.people(rounds,who,24,this::notLinked,this::personClicked,this::roundInk);
            // Right-click on a round: the phone's hold on it (see roundMenu).
            for(int i=0;i<who.size()&&i<rounds.getComponentCount();i++){SyncStatus.Person one=who.get(i);Component round=rounds.getComponent(i);
                DesktopMenus.onRightClick(round,e->roundMenu(one,round).show(e.getComponent(),e.getX(),e.getY()));}
            // Unsaved words are waiting whatever the notebook says (a red mark stays red; see SyncMark.of). Only words:
            // a page that merely shows older text has nothing of its own to send.
            SyncMark mark=(SyncMark)text[2];
            showNoteMark(typedHere()||saving?waitingOver(mark):mark);}},this::failed);
    }
    /**
     * Where a note is, on the line over its title: every collection above it from the top down, however deep, as the
     * trail over the cards says it; Home for one on Home itself. On the disk thread.
     */
    static String shelfLine(NoteStore store,String note) {
        List<String> names=new ArrayList<>();for(String one:store.above(note))names.add(store.collectionName(one));
        return names.isEmpty()?"Home":String.join("  /  ",names);
    }
    /** What the mark turns to when words are typed: waiting to go, unless it reaches nobody, is paused, or has gone wrong. */
    private static SyncMark waitingOver(SyncMark was) {
        return was==null||was==SyncMark.HERE||was==SyncMark.PAUSED||was==SyncMark.STUCK?was:SyncMark.WAITING;
    }
    /** The open note's mark drawn in a state, on a round of paper so a washed page does not swallow its colour. */
    private void showNoteMark(SyncMark what) {
        noteMark=what;syncMark.setIcon(new DesktopMark(what,20,true));
        syncMark.getAccessibleContext().setAccessibleName(DesktopMark.said(what));syncMark.setVisible(true);
    }
    /**
     * The open note's syncing said on its own line, after the mark and the people, in the mark's colour; gone a few
     * seconds later unless it is still going on (NoteLine.stays). Null words clear it.
     *
     * @param click what a click on the words does; null for what a click on the mark does
     */
    void noteSays(String words,NoteLine.Tone tone,Runnable click) {
        wordsAway.stop();
        boolean said=words!=null&&!words.isBlank()&&tone!=null;
        noteWords.setText(said?words:"");noteWords.setVisible(said);wordsClicked=said?click:null;
        if(!said)return;
        noteWords.setForeground(DesktopMark.ink(tone.mark));noteWords.getAccessibleContext().setAccessibleName(words);
        // Something that could not go stays until clicked or the next send goes, as on the phone.
        if(!NoteLine.stays(words)&&tone!=NoteLine.Tone.FAILED)wordsAway.restart();
    }
    /** Whether a thing is the note open on the page now, whose line its syncing is said on; anything else uses the bar. */
    private boolean isOpen(String id){return base!=null&&base.id.equals(id)&&editorPanel!=null&&editorPanel.isVisible();}
    /** Who the open note reaches by name, as its rounds were last drawn: those it can be sent to. */
    private List<String> namesShown() {
        List<String> names=new ArrayList<>();
        for(SyncStatus.Person one:peopleShown)if(one.linked()&&!names.contains(one.called()))names.add(one.called());
        return names;
    }
    /**
     * How a send of the open note ended, on its line: sent, or what could not go with a click for why.
     *
     * @param asked whether the others were asked for theirs too, so that nothing to send is still an answer worth saying
     */
    private void noteSent(Post.Done done,boolean asked) {
        if(done.failed>0)noteSays(NoteLine.couldNot(done.problems,done.sent),NoteLine.couldNotTone(done.problems),()->tellUnsent(done));
        else if(done.sent>0)noteSays(NoteLine.sent(namesShown()),NoteLine.Tone.DONE,null);
        else if(asked)noteSays(NoteLine.NOTHING,NoteLine.Tone.DONE,null);
        // Nothing went by itself: the "Sending…" that went ahead of it is taken down, and new typing is left saying so.
        else if(noteWords.getText().startsWith("Sending"))noteSays(null,null,null);
    }
    /** A send of the open note that threw: on its line, with the words it threw with behind the click. */
    private void noteFailed(Exception e) {
        String why=e.getMessage()==null?"That did not finish. Please try again.":e.getMessage();
        noteSays("Could not go - see why",NoteLine.Tone.FAILED,()->tellUnsent(new Post.Done(0,1,why)));
    }
    /** The open note, as the thing its mark and its menus are about. */
    private NoteStore.Branch openNote() {
        NoteStore.Branch known=find(base.id);if(known!=null)return known;
        return new NoteStore.Branch(NoteStore.Branch.Kind.PAGE,base.id,base.book,base.title==null||base.title.isBlank()?"this note":base.title,"",0,0,false,base.colour);
    }
    /**
     * A mark clicked, as the phone's is tapped: waiting says what waits and for whom, with Send now as the one thing
     * to press (see Waits); anything else shows who has it.
     */
    void markClicked(NoteStore.Branch thing,SyncMark what) {
        selected=thing;
        if(what!=SyncMark.WAITING){save(this::share);return;}
        save(()->disk.submit(()->SyncStatus.waits(store,thing.kind,thing.id),said->{
            // What waits only for a device to be updated cannot go any sooner for a press: said, with nothing to press.
            if(Looks.onlyUpdates(said)){DesktopUi.tell(frame,Waits.TITLE,DesktopUi.note(said,380,INK,DesktopUi.BODY));return;}
            if(DesktopUi.confirm(frame,Waits.TITLE,said,Waits.SEND,false))sync(true);
        },this::failed));
    }
    void open(String id){
        int request=++noteRequest;
        disk.submit(()->store.get(id),note->{
            if(request!=noteRequest||note==null)return;
            if(dirty||saving){if(afterSave==null)save(()->open(id));return;}
            display(note);
        },this::failed);
    }
    /**
     * A new note: in the open card's collection; else beside the open note, in the collection it is in; else on Home -
     * where the reader is. Ctrl+N and the menu's New note make it here; the + makes it where the + is.
     */
    private void newNote() {
        NoteStore.Branch card=home.folder.shown();
        newNoteIn(card!=null&&!onPage?card.id:onPage&&base!=null?(NoteStore.home(base.book)?Things.HOME:base.book):Things.HOME);
    }
    /** A new note in this collection, or on Home, opened at once to be written on. */
    void newNoteIn(String where) {
        int request=++noteRequest;
        save(()->disk.submit(()->{
            NoteStore.Note note=new NoteStore.Note();
            note.book=NoteStore.home(where)?Things.HOME:where;
            if(!NoteStore.home(where)&&Boolean.FALSE.equals(store.mayWriteIn(NoteStore.Branch.Kind.COLLECTION,where)))throw new IllegalStateException("This collection is read only here. Ask whoever shared it to let you write in it.");
            store.save(note);return note;
        },note->{if(home!=null&&NoteStore.home(where))home.placeNew(note.id);if(request!=noteRequest)return;if(dirty||saving){if(afterSave==null)save(()->open(note.id));return;}display(note);refresh();},this::failed));
    }
    void refresh(){search("");updateStanding();if(home!=null)home.refresh();}

    /** Whether a note has anything in it: a blank page nothing was written on is not one of the things open. */
    private static boolean written(NoteStore.Note note){return note!=null&&!(Objects.toString(note.title,"").isBlank()&&Objects.toString(note.body,"").isBlank());}

    /** Where the window is, kept for the next time it opens: Home, or a note. */
    private void keepWhere(String where){disk.submit(()->{context.getSharedPreferences("settings",0).edit().putString(WHERE,where).apply();return null;},done->{},e->{});}

    /**
     * Home on the screen, as it was left: a card that was open under the note is open again, and the grid is where it was
     * scrolled to. The note is not closed by it - it stays one of the things open, a Ctrl+Tab away.
     */
    void showHome() {
        overview.close();
        if(onPage&&base!=null)carets.put(base.id,page.getCaretPosition());
        boolean was=onPage;onPage=false;selected=null;
        ((CardLayout)mainArea.getLayout()).show(mainArea,"home");keepWhere(HOME_SHOWN);
        if(was)home.focusThing(base==null?null:base.id);
    }
    /** ← Home, or Esc on a note: what was typed written down first, then Home. */
    void goHome(){save(()->{showHome();if(!home.folder.isOpen()&&base!=null)home.focusThing(base.id);});}

    /** A collection, from anywhere: its card over Home, however deep it is. */
    void openCollection(String id){save(()->{showHome();home.folder.openAt(id);});}

    /** A thing chosen from a list - the search, the dock, the tree: a note opens, a collection its card, a file itself. */
    void openThing(NoteStore.Branch thing) {
        if(thing.kind==NoteStore.Branch.Kind.PAGE)save(()->open(thing.id));
        else if(thing.kind==NoteStore.Branch.Kind.FILE)home.openFile(thing);
        else if(thing.kind==NoteStore.Branch.Kind.FAVOURITES)save(()->{showHome();home.folder.openFavourites();});
        else if(isCollection(thing))openCollection(thing.id);
    }

    /** A card in the overview chosen: gone to, as it was opened. */
    void goTo(Overview.Open one){if(one.kind==Overview.Kind.NOTE)save(()->open(one.id));else openCollection(one.id);}

    /** Whether this open thing is what is on the screen now: the note on the page, or the collection whose card is up. */
    boolean showingOpen(Overview.Open one) {
        if(one.kind==Overview.Kind.NOTE)return onPage&&base!=null&&base.id.equals(one.id);
        NoteStore.Branch card=home.folder.shown();return !onPage&&card!=null&&card.id.equals(one.id);
    }

    /** What the overview lies over: the tree, and Home or the page - under the toolbar, over the status bar. */
    JComponent overviewArea(){return split;}

    /** One card closed in the overview: if it is what is on the screen, Home is what is left. */
    void closed(Overview.Open one) {
        if(!showingOpen(one))return;
        if(one.kind==Overview.Kind.NOTE)goHome();else home.folder.close();
    }
    /** Close all: nothing is open, so Home is what is on the screen, with no card over it. */
    void closedEverything(){save(()->{showHome();if(home.folder.isOpen())home.folder.close();});}

    /** A note moved from Home or a card: the page's own copy says where it is now, as the line over its title does. */
    void movedNote(NoteStore.Branch moved,String into){if(moved.kind==NoteStore.Branch.Kind.PAGE&&base!=null&&base.id.equals(moved.id))base.book=NoteStore.home(into)?Things.HOME:into;}

    /** Moved into something somebody else reads, or put together with it: it goes now, as sharing does. */
    void sendChanged(NoteStore.Branch.Kind kind,String id){if(!offline)network.submit(()->{Post.changed(context,store,keys,kind,id);return null;},v->refresh(),e->{});}

    /**
     * Esc, where nothing nearer took it: a menu that is up closes first, then the overview; on a note, back to Home
     * (decision 24); on Home, out of the card a level at a time, then the search emptied.
     */
    private void escape() {
        if(javax.swing.MenuSelectionManager.defaultManager().getSelectedPath().length>0){javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();return;}
        if(overview.showing()){overview.close();return;}
        if(onPage){goHome();return;}
        home.escape();
    }
    private void search(String term) {
        int request=++treeRequest;
        disk.submit(()->{List<NoteStore.Branch> found=term.isBlank()?store.wholeTree():store.looking(term);
            // The favourites wear what they wear on Home, as the lines under them do.
            List<NoteStore.Branch> starred=term.isBlank()?store.favourites():List.of();store.dress(starred);
            STARRED=Set.copyOf(store.keptIds());favouritesNow=starred;rungsNow=store.rungs();
            if(term.isBlank())try{marks=DesktopMark.read(store);}catch(Exception unread){/* the lines, without their marks this time */}
            return found;},rows->{
            if(request!=treeRequest)return;
            if(term.isBlank()){everything=rows;treeDrawn=true;}
            // One line at the top: Home, holding everything on the shelves, Favourites first inside it, as on the phone.
            drawing=true;DefaultMutableTreeNode top=new DefaultMutableTreeNode(""),root=new DefaultMutableTreeNode("Home");top.add(root);
            Map<String,DefaultMutableTreeNode> nodes=new HashMap<>();
            nodes.put(Sharing.EVERYTHING,root);
            for(NoteStore.Branch branch:rows) {
                if(branch.kind==NoteStore.Branch.Kind.LIBRARY)continue;
                DefaultMutableTreeNode node=new DefaultMutableTreeNode(new Item(branch));nodes.put(branch.id,node);
                nodes.getOrDefault(branch.parent,root).add(node);
            }
            // Favourites first, as on the phone: what is starred, where it really lives one click away.
            if(!favouritesNow.isEmpty()) {
                DefaultMutableTreeNode starred=new DefaultMutableTreeNode(new Item(new NoteStore.Branch(NoteStore.Branch.Kind.FAVOURITES,NoteStore.FAVOURITES,"","Favourites","",0,0,true)));
                for(NoteStore.Branch one:favouritesNow)starred.add(new DefaultMutableTreeNode(new Item(one)));
                root.insert(starred,0);
            }
            tree.setModel(new DefaultTreeModel(top));for(int i=0;i<tree.getRowCount();i++)tree.expandRow(i);drawing=false;
            // Colours are read with the tree, so the page is washed again with whatever it now says.
            if(term.isBlank())washPage();
        },this::failed);
    }
    /** Colour ▸ for one thing, its colour as the tree last read it from the notebook. */
    private JMenu colourMenu(NoteStore.Branch thing) {
        NoteStore.Branch known=find(thing.id);
        int now=known!=null?known.colour:thing.kind==NoteStore.Branch.Kind.PAGE&&base!=null&&base.id.equals(thing.id)?base.colour:thing.colour;
        return DesktopLook.colours(now,colour->paint(thing,colour),()->tone,this::useTone);
    }
    /** A colour given to one thing: kept, and the tree, the cards and the page drawn in it at once. */
    void paint(NoteStore.Branch thing,int colour) {
        if(thing.kind==NoteStore.Branch.Kind.PAGE&&base!=null&&base.id.equals(thing.id))base.colour=colour;
        disk.submit(()->{store.paint(thing.kind,thing.id,colour);return null;},done->{
            // Home, the card and the tree are drawn from the notebook, so they are drawn again once it has been read.
            refresh();disk.submit(()->null,read->washPage(),e->{});
        },this::failed);
    }
    /** How strongly colours land, for the whole pad: everything coloured drawn again at the new strength. */
    void useTone(int step) {
        if(step==tone)return;tone=step;int kept=step;
        tree.repaint();washPage();home.paintRoom();home.repaint();
        disk.submit(()->{DesktopLook.keepTone(context,kept);return null;},done->{},e->{});
    }
    /**
     * The page washed in the colour of the note that is open, as on the phone. A collection's card is washed in its own
     * colour where it is drawn (see DesktopFolder); Home itself is plain paper.
     */
    void washPage() {
        // The tree is read after every change, a backup's included, so it is asked first; the note itself if it is not there.
        NoteStore.Branch open=base!=null?find(base.id):null;
        int own=open!=null?open.colour:base!=null?base.colour:Tint.NONE;
        Color paper=DesktopLook.wash(own,PAPER,0.12f,0.72f,tone);
        if(editorPanel!=null)editorPanel.setBackground(paper);
        page.setBackground(paper);title.setBackground(paper);if(paperScroll!=null)paperScroll.getViewport().setBackground(paper);
        // The file drawer slides up over the page, so it is the same paper.
        if(fileCards!=null)fileCards.setBackground(paper);
        page.rules=DesktopLook.wash(own,Paper.RULES,0.35f,1f,tone);
        page.repaint();
    }
    /** The Home line: the first under the tree's top, which is not drawn. */
    private static boolean allCollections(DefaultMutableTreeNode node) {
        return node.getParent()!=null&&node.getParent().getParent()==null&&!(node.getUserObject() instanceof Item);
    }
    /** What can be done to one thing in the tree, where it is. */
    private void shelfMenu(MouseEvent e) {
        TreePath path=tree.getPathForLocation(e.getX(),e.getY());if(path==null)return;
        rowMenu(path,e.getX(),e.getY());
    }
    /** A line of the tree, chosen first: the top offers what can be made there; Favourites is a place, with none. */
    private void rowMenu(TreePath path,int x,int y) {
        tree.setSelectionPath(path);
        DefaultMutableTreeNode node=(DefaultMutableTreeNode)path.getLastPathComponent();
        if(allCollections(node)){roomMenu(library(),tree,x,y);return;}
        if(!(node.getUserObject() instanceof Item item)||item.branch.kind==NoteStore.Branch.Kind.FAVOURITES)return;
        shelfMenu(item.branch,tree,x,y);
    }
    void shelfMenu(NoteStore.Branch branch,Component where,int x,int y){thingMenu(branch).show(where,x,y);}
    /**
     * Everything that can be done to one thing, made in one place so it is the same menu wherever it is opened: its
     * icon, its card, its dock icon, its line in the tree. In the phone's order (MainActivity.menuFor): how it looks,
     * then Undo, then what is done to it - its versions or a file for it, moving it, sharing it and syncing it, keeping
     * it to hand, putting it away - and last, its words taken elsewhere. Rename and a note's files are the PC's own,
     * where the phone renames where the name is written and keeps the clip on the page.
     */
    JPopupMenu thingMenu(NoteStore.Branch branch) {
        selected=branch;
        JPopupMenu menu=new JPopupMenu();
        boolean note=branch.kind==NoteStore.Branch.Kind.PAGE;
        menu.add(colourMenu(branch));
        if(note||isCollection(branch))lookRows(menu,branch);
        if(note)menu.add(sizeMenu(branch));
        menu.addSeparator();
        undoRow(menu);
        JMenuItem rename=new JMenuItem("Rename…");rename.setAccelerator(KeyStroke.getKeyStroke("F2"));rename.addActionListener(a->rename(branch));menu.add(rename);
        // A note's own: what the page's menus offer for the open one, here for any note - opened first if it is not.
        if(note) {
            boolean readOnly=isOpen(branch.id)&&!page.isEditable();
            item(menu,"Versions…",()->save(()->onNote(branch,false,this::versions)));
            JMenuItem attach=new JMenuItem("Attach a file…");attach.setEnabled(!readOnly);attach.addActionListener(a->save(()->onNote(branch,true,this::attach)));menu.add(attach);
            JMenuItem record=new JMenuItem("Record audio");record.setEnabled(!readOnly&&!recorder.recording());record.addActionListener(a->save(()->onNote(branch,true,this::record)));menu.add(record);
        }
        // A collection keeps files as a note does: added from here, or dropped on its card (decision 23).
        if(isCollection(branch))item(menu,"Add a file…",()->addFiles(branch));
        if(note||isCollection(branch)) {
            // Any collection moves too now, onto Home or into another: collections nest (docs/HOME.md, decision 14).
            JMenuItem move=new JMenuItem("Move to…");move.addActionListener(a->save(()->moveTo(branch)));menu.add(move);
        }
        JMenuItem share=new JMenuItem("Share…");share.addActionListener(a->save(this::share));menu.add(share);
        if(shared(branch))menu.add(syncRow(branch));
        if(note||isCollection(branch)) {
            boolean starred=STARRED.contains(branch.id);
            JMenuItem star=new JMenuItem(starred?"Remove from favourites":"Add to favourites");star.addActionListener(a->favourite(branch,!starred));menu.add(star);
            // Under it, for a favourite in the dock, taking it out of the dock: it stays a favourite, in Favourites.
            if(home!=null&&home.inDock(branch.id))item(menu,"Remove from the dock",()->undock(branch));
            menu.addSeparator();
            JMenuItem archive=new JMenuItem("Archive");archive.addActionListener(a->save(()->putAway(branch,false)));menu.add(archive);
            JMenuItem bin=new JMenuItem("Move to bin");bin.addActionListener(a->save(()->putAway(branch,true)));menu.add(bin);
        }
        // Its words, somewhere else: the phone's Send to another app, here the clipboard.
        if(note){menu.addSeparator();item(menu,"Copy the text",()->save(()->onNote(branch,false,this::copyNote)));}
        return menu;
    }
    /**
     * Icon…, right after Colour on both apps (decision 33): the box its icon is chosen in. And Remove the picture under it,
     * only while it wears one: the icon under the picture is worn again.
     */
    private void lookRows(JPopupMenu menu,NoteStore.Branch thing) {
        item(menu,"Icon…",()->DesktopIconPicker.open(this,thing));
        if(pictured(thing))item(menu,"Remove the picture",()->DesktopIconPicker.removePicture(this,thing));
    }
    /** Whether a thing wears a picture, as it was listed or as the tree last read it: a hand-made line knows nothing of it. */
    private boolean pictured(NoteStore.Branch thing) {
        if(thing.image!=null)return true;
        if(base!=null&&base.id.equals(thing.id)&&noteImage!=null)return true;
        NoteStore.Branch known=find(thing.id);return known!=null&&known.image!=null;
    }
    /**
     * A note's or a collection's icon or picture, changed here: drawn again everywhere, and sent to whoever has it now - a
     * note does not go on its own, and a collection's carton would otherwise wait for the next round. Quietly, as every
     * automatic send is: the mark says how it goes.
     */
    void lookChanged(NoteStore.Branch.Kind kind,String id) {
        refresh();
        if(!offline)network.submit(()->Post.send(context,store,keys,kind,id),done->refresh(),e->{});
    }
    /**
     * Taken out of the dock: still a favourite, in the Favourites collection, and the rest of the dock closes up. Any icon
     * the dock draws: one of the notebook's own five leaves them, and none taken out comes back into the room this PC's
     * dock has past them (see DesktopHome.docked).
     */
    void undock(NoteStore.Branch thing) {
        disk.submit(()->{store.outOfDock(thing.kind,thing.id);DesktopHome.keepOutOfDock(context,thing.id,true);return null;},
            done->{status.setText("Out of the dock, and still a favourite");refresh();},this::failed);
    }
    /** Add a file… on a collection: files chosen here, kept with it. */
    private void addFiles(NoteStore.Branch collection) {
        JFileChooser pick=new JFileChooser();pick.setMultiSelectionEnabled(true);if(pick.showOpenDialog(frame)!=JFileChooser.APPROVE_OPTION)return;
        java.io.File[] chosen=pick.getSelectedFiles();if(chosen.length==0&&pick.getSelectedFile()!=null)chosen=new java.io.File[]{pick.getSelectedFile()};
        List<Path> sources=new ArrayList<>();for(java.io.File one:chosen)sources.add(one.toPath());
        if(!sources.isEmpty())keep(collection.id,named(collection),sources);
    }
    /** Sync now for one thing, or for everything: what waits for it goes, and the others are asked for theirs. */
    private JMenuItem syncRow(NoteStore.Branch thing) {
        JMenuItem sync=new JMenuItem("Sync now");sync.addActionListener(a->{selected=thing;save(()->{selected=thing;sync(true);});});return sync;
    }
    /** Whether a thing reaches anybody, as its mark last said: only those have anything to sync. */
    private boolean shared(NoteStore.Branch thing) {
        SyncMark mark=marks.get(thing.id);if(mark==null&&base!=null&&base.id.equals(thing.id))mark=noteMark;
        return mark!=null&&mark!=SyncMark.HERE;
    }
    /**
     * Something done to the open note, for a note that may not be open: opened first, and done once it is on the page
     * and the notebook has said whether it may be written in here. {@code writes}: it needs a note it may write in.
     */
    private void onNote(NoteStore.Branch note,boolean writes,Runnable then) {
        Runnable go=()->{if(writes&&!page.isEditable()){status.setText("This note is read only here");return;}then.run();};
        if(isOpen(note.id)){go.run();return;}
        if(dirty||saving){save(()->onNote(note,writes,then));return;}
        int request=++noteRequest;
        disk.submit(()->store.get(note.id),got->{
            if(request!=noteRequest||got==null)return;
            display(got);
            // Behind the question display asks the notebook, whether it may be written in here.
            disk.submit(()->null,asked->{if(isOpen(note.id))go.run();},this::failed);
        },this::failed);
    }
    /**
     * Home itself, or a card, right-clicked where there is no icon - or the tree's Home line: what + makes there first,
     * then, on Home, Undo and syncing everything, as the phone's menu offers there; in a card, the collection's own menu.
     */
    void roomMenu(NoteStore.Branch here,Component where,int x,int y) {
        boolean top=here.kind==NoteStore.Branch.Kind.LIBRARY;String in=top?Things.HOME:here.id;
        JPopupMenu menu=new JPopupMenu();
        if(top) {
            // Everything about Home, as a long press on its empty room gives it on the phone (docs/HOME.md, decisions 43
            // and 44): the size notes are read at, Home's colour and its strength; Undo; what is made and done here;
            // favourites and search shown or not.
            menu.add(DesktopLook.ladder(()->rung,this::setTextSize));menu.addSeparator();
            menu.add(homeColourMenu());
            menu.addSeparator();
            undoRow(menu);
            item(menu,"New note",()->newNoteIn(in));
            item(menu,"New collection",()->home.newCollection(in));
            // And what someone else shows the code of, as every + offers it (the owner: "this is key").
            item(menu,"From another device…",()->scanCode(null));
            selected=library();menu.add(syncRow(library()));
            menu.addSeparator();
            menu.add(ticked("Show favourites",showing(SHOW_DOCK),()->setShowing(SHOW_DOCK,!showing(SHOW_DOCK))));
            menu.add(ticked("Show search",showing(SHOW_SEARCH),()->setShowing(SHOW_SEARCH,!showing(SHOW_SEARCH))));
            // Every page at once, where there is more than one (decision 45); and back to the centre from another.
            if(Layout.active(home.grid.spots()).size()>1)item(menu,"All pages",()->home.pages.up());
            if(!home.grid.onCentre())item(menu,"Back to the main page",()->home.grid.showPage(0,0));
        } else {
            item(menu,"New note",()->newNoteIn(in));
            item(menu,"New collection",()->home.newCollection(in));
            item(menu,"From another device…",()->scanCode(null));
            menu.addSeparator();
            for(Component one:thingMenu(here).getComponents())menu.add(one);
        }
        menu.show(where,x,y);
    }
    /**
     * The Menu key, or Shift+F10: the menu of what has the keyboard - the chosen line of the tree, a card, or,
     * in the note itself, the note - opened where it is, as a right-click on it would.
     */
    private void contextKey() {
        Component focus=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if(focus==tree) {
            TreePath path=tree.getSelectionPath();int row=path==null?-1:tree.getRowForPath(path);if(row<0)return;
            Rectangle at=tree.getRowBounds(row);tree.scrollRectToVisible(at);rowMenu(path,at.x+12,at.y+at.height);return;
        }
        // An icon, a dock icon, a found line or an open card: each says what its menu is.
        if(focus instanceof JComponent c&&c.getClientProperty(DesktopHome.MENU) instanceof java.util.function.Supplier<?> menu&&menu.get() instanceof JPopupMenu m){m.show(c,12,c.getHeight()-8);return;}
        if((focus==page||focus==title)&&base!=null) {
            java.awt.Point at=new java.awt.Point(12,focus.getHeight()/2);
            if(focus==page)try{Rectangle caret=page.modelToView2D(page.getCaretPosition()).getBounds();at=new java.awt.Point(caret.x,caret.y+caret.height);}catch(Exception none){/* the middle of the page */}
            paperMenu((javax.swing.text.JTextComponent)focus,focus==page?Links.at(page.links(),page.getCaretPosition()):null).show(focus,at.x,at.y);
        }
    }
    /**
     * A right-click on the page or the title: the caret goes where it was, unless it was inside the words chosen,
     * which stay chosen for Cut or Copy. Then the menu of what is there (see paperMenu).
     */
    private void paperClicked(MouseEvent e) {
        if(base==null)return;
        javax.swing.text.JTextComponent words=(javax.swing.text.JTextComponent)e.getComponent();
        int at=words.viewToModel2D(e.getPoint()),from=words.getSelectionStart(),to=words.getSelectionEnd();
        if(at>=0&&!(from<to&&at>=from&&at<=to))words.setCaretPosition(at);
        words.requestFocusInWindow();
        paperMenu(words,words==page?page.linkAt(e.getPoint()):null).show(words,e.getX(),e.getY());
    }
    /**
     * The page's menu, or the title's: an address first, where there is one under the pointer; then the words -
     * Cut and Copy while some are chosen, Paste, Select all; then the note's own menu, the same as on its tab.
     */
    JPopupMenu paperMenu(javax.swing.text.JTextComponent words,Links.Link link) {
        List<Component> first=new ArrayList<>();
        if(link!=null) {
            first.add(line("Open link",null,true,()->page.open(link.target())));
            String address=link.target().startsWith("mailto:")?link.target().substring(7):link.target();
            first.add(line("Copy link",null,true,()->{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(address),null);status.setText("Link copied");}));
            first.add(new JPopupMenu.Separator());
        }
        boolean chosen=words.getSelectionStart()<words.getSelectionEnd(),writes=words.isEditable(),pasting=false;
        // Words, or on the page a picture or files, which are kept with the note (DesktopDrops.Aimed).
        try{var clip=Toolkit.getDefaultToolkit().getSystemClipboard();pasting=clip.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.stringFlavor)
            ||words==page&&(clip.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.imageFlavor)||clip.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.javaFileListFlavor));}
        catch(Exception busy){/* greyed, then */}
        if(chosen) {
            first.add(line("Cut","control X",writes,words::cut));
            first.add(line("Copy","control C",true,words::copy));
        }
        first.add(line("Paste","control V",writes&&pasting,words::paste));
        first.add(line("Select all","control A",words.getDocument().getLength()>0,words::selectAll));
        first.add(new JPopupMenu.Separator());
        // Then what is done most from the page itself; the rest of the note's menu, the same as on its tab, one step in.
        JPopupMenu menu=new JPopupMenu(),whole=thingMenu(openNote());
        for(Component one:first)menu.add(one);
        Map<String,Component> pulled=new HashMap<>();List<Component> rest=new ArrayList<>();
        for(Component one:whole.getComponents()){if(one instanceof JMenuItem item&&!(one instanceof JMenu)&&FROM_THE_PAGE.contains(item.getText()))pulled.put(item.getText(),one);else rest.add(one);}
        for(String said:FROM_THE_PAGE)if(pulled.containsKey(said))menu.add(pulled.get(said));
        JMenu note=new JMenu("This note");boolean divided=true;
        for(Component one:rest) {
            boolean line=one instanceof JPopupMenu.Separator;
            if(line&&divided)continue;
            note.add(one);divided=line;
        }
        if(divided&&note.getMenuComponentCount()>0)note.getPopupMenu().remove(note.getMenuComponentCount()-1);
        menu.addSeparator();menu.add(note);
        // And what someone else shows the code of, from a note as from every + and every menu (the owner: "this is key").
        menu.addSeparator();menu.add(line("From another device…",null,true,()->scanCode(null)));
        return menu;
    }
    /** The note's own lines a right-click on its page offers straight away, in this order; the rest are under This note. */
    private static final List<String> FROM_THE_PAGE=List.of("Attach a file…","Record audio","Versions…","Copy the text","Share…","Sync now");
    private static JMenuItem line(String said,String key,boolean can,Runnable does) {
        JMenuItem line=new JMenuItem(said);if(key!=null)line.setAccelerator(KeyStroke.getKeyStroke(key));
        line.setEnabled(can);line.addActionListener(a->does.run());return line;
    }
    /** The open note, title to files, while it is shown: what files dropped on it light. */
    JComponent noteArea(){return editorPanel!=null&&editorPanel.isShowing()?editorPanel:null;}
    /** Whether the note is what is on the screen, rather than Home. */
    boolean onPage(){return onPage;}

    /**
     * The last thing put away, moved or renamed, and how to put it back: one level, as on the phone. Offered near
     * the top of the menus until something else takes its place.
     */
    private String undoWhat="";private Background.Work<Object> undoHow;
    void canUndo(String what,Background.Work<Object> how){undoWhat=what==null||what.isBlank()?"Untitled":what;undoHow=how;}
    private void undoRow(JPopupMenu menu) {
        if(undoHow==null)return;
        JMenuItem row=new JMenuItem("Undo “"+undoWhat+"”");row.addActionListener(a->undo());menu.add(row);menu.addSeparator();
    }
    void undo() {
        Background.Work<Object> how=undoHow;String what=undoWhat;undoHow=null;undoWhat="";
        if(how==null)return;
        status.setText("Putting back…");
        disk.submit(how,done->{status.setText("“"+what+"” put back");refresh();},e->status.setText("Could not put that back. Nothing was changed."));
    }
    /** Starred or not: the tree and the menus say so at once. Starred again, it may come back into the dock. */
    void favourite(NoteStore.Branch thing,boolean kept) {
        disk.submit(()->{store.keepToHand(thing.kind,thing.id,kept);DesktopHome.keepOutOfDock(context,thing.id,false);return null;},done->{status.setText(kept?"Added to favourites":"Removed from favourites");refresh();},this::failed);
    }

    /** A note or a collection put in the archive or the bin; its card in the overview goes with it. */
    void putAway(NoteStore.Branch thing,boolean bin) {
        if(thing.kind==NoteStore.Branch.Kind.PAGE&&base!=null&&base.id.equals(thing.id)){putAway(bin);return;}
        disk.submit(()->{store.putAway(thing.kind,thing.id,bin,true);return null;},done->{
            canUndo(thing.name,()->{store.restore(thing.kind,thing.id);return null;});
            overview.forget(thing.id);status.setText(bin?"Moved to the bin":"Archived");refresh();},this::failed);
    }

    /**
     * A note into another collection, or a collection onto the top or into another one - never into itself or anything
     * it holds (docs/HOME.md, decision 14). Where that changes who can read it, it is said by name first, as on the
     * phone: moving into something shared is a disclosure, out of it a withdrawal.
     */
    void moveTo(NoteStore.Branch moved) {
        boolean page=moved.kind==NoteStore.Branch.Kind.PAGE;
        String now=page?store_bookOf(moved):moved.parent;
        disk.submit(()->places(store,moved,now),places->{
            if(places.isEmpty()){status.setText(page?"There is no other collection to move it into.":"There is nowhere else to move it.");return;}
            NoteStore.Branch into=DesktopUi.pick(frame,"Move “"+moved.name+"”","Into which collection?",places,Desktop::placeName,"Move here");
            if(into==null)return;
            moveInto(moved,into.kind==NoteStore.Branch.Kind.LIBRARY?DesktopMoving.top():into,null);
        },this::failed);
    }
    /**
     * Where Move to… offers to put a thing, {@code now} being where it is: for a note every collection, for a collection
     * the top and every collection but itself and what is inside it - the notebook leaves those out - and in either
     * case not where it already is. On the disk thread.
     */
    static List<NoteStore.Branch> places(NoteStore store,NoteStore.Branch moved,String now) {
        List<NoteStore.Branch> places=new ArrayList<>();
        for(NoteStore.Branch one:store.places(moved))if(!(NoteStore.home(now)?NoteStore.home(one.id):one.id.equals(now)))places.add(one);
        return places;
    }
    /** A place in Move to…'s list: Home, or a collection's name and, for one inside another, where it is. */
    static String placeName(NoteStore.Branch place) {
        if(place.kind==NoteStore.Branch.Kind.LIBRARY)return "Home";
        String name=place.name==null||place.name.isBlank()?"Untitled":place.name,where=place.detail==null?"":place.detail;
        return where.startsWith("in ")?name+"   ·   "+where.substring(3):name;
    }

    /**
     * Moved into this collection, or onto the top - from Move to…, or a drag let go on it. {@code order} is the level
     * it lands in, in the order it is to keep there; null leaves it at the top, where a move puts things.
     */
    void moveInto(NoteStore.Branch moved,NoteStore.Branch into,List<String> order) {
        boolean page=moved.kind==NoteStore.Branch.Kind.PAGE,onTop=into.kind==NoteStore.Branch.Kind.LIBRARY;
        disk.submit(()->{
            // Refused before anybody is asked about who it would reach: a collection never goes inside itself.
            if(!page&&!Things.mayGoInto(store.parents(),moved.id,onTop?Things.HOME:into.id,true))
                throw new IllegalArgumentException("A collection cannot go inside itself, or inside anything it holds.");
            List<Sharing.Rule> rules=store.shares();
            // Its whole path where it is and where it would be, each ending with itself: every collection above counts, however deep.
            List<String> from=store.pathOf(moved.id),to=onTop?new ArrayList<>():store.pathOf(into.id);to.add(moved.id);
            Sharing.Change change=Sharing.moving(rules,from,to);
            Map<String,String> names=new HashMap<>();for(NoteStore.Contact c:store.addresses())names.put(c.address,c.name);
            return new Object[]{change,names};
        },found->{
            Sharing.Change change=(Sharing.Change)found[0];@SuppressWarnings("unchecked") Map<String,String> names=(Map<String,String>)found[1];
            if(change.any()) {
                StringBuilder said=new StringBuilder("Moving “"+moved.name+"” into "+into.name+" changes who receives it.");
                if(!change.gained.isEmpty()){said.append("\n\nStarts reaching: ");said.append(String.join(", ",change.gained.keySet().stream().map(a->names.getOrDefault(a,"a paired device")).toList()));}
                if(!change.lost.isEmpty()){said.append("\n\nStops reaching: ");said.append(String.join(", ",change.lost.keySet().stream().map(a->names.getOrDefault(a,"a paired device")).toList()));}
                said.append("\n\nWhoever starts receiving it gets it now. What has already reached somebody stays with them.");
                if(!DesktopUi.confirm(frame,"This changes who can read it",said.toString(),"Move anyway",false)){status.setText("Not moved");return;}
            }
            disk.submit(()->{
                // Where it was, before it is anywhere else: an undo has to know the place to put it back into.
                String from=page?store.bookOf(moved.id):store.collectionOfBook(moved.id);
                if(page)store.movePage(moved.id,into.id);else store.moveBook(moved.id,into.id);
                if(order!=null)store.order(moved.kind,order);return from;},from->{
                canUndo(moved.name,()->{if(page)store.movePage(moved.id,from);else store.moveBook(moved.id,from);
                    network.submit(()->{Post.changed(context,store,keys,moved.kind,moved.id);return null;},v->{},e->{});return null;});
                // The open note's line says which collection it is in: it is in another one now.
                if(page&&base!=null&&base.id.equals(moved.id))base.book=into.id;
                status.setText("Moved into "+into.name);refresh();
                network.submit(()->{Post.changed(context,store,keys,moved.kind,moved.id);return null;},v->{},e->{});
            },this::failed);
        },this::failed);
    }
    private String store_bookOf(NoteStore.Branch page){return base!=null&&base.id.equals(page.id)?base.book:page.parent;}

    /** A level put in a new order by a drag: kept as it now looks, and one Undo from how it was. */
    void reorder(NoteStore.Branch moved,List<String> order,List<String> before) {
        disk.submit(()->{store.order(moved.kind,order);return null;},done->{
            canUndo(moved.name,()->{store.order(moved.kind,before);return null;});refresh();
        },e->{status.setText("Could not keep that order. Nothing was changed.");refresh();});
    }

    /** Everything, as the tree last read it. */
    List<NoteStore.Branch> everything(){return everything;}

    /** The open note's words, on the clipboard: to paste into a message, a mail, anything. */
    void copyNote() {
        if(base==null)return;
        String words=(title.getText().isBlank()?"":title.getText()+"\n\n")+page.getText();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(words),null);
        status.setText("The note is copied. Paste it anywhere.");
    }

    /** What to say, and about what: the form on GitHub, filled in, opened in the browser. Nothing is sent from here. */
    void feedback() {
        JDialog[] box={null};
        JComboBox<String> kind=new JComboBox<>();for(Feedback.Kind k:Feedback.Kind.values())kind.addItem(k.said);
        JComboBox<String> area=new JComboBox<>();for(Feedback.Area a:Feedback.Area.values())area.addItem(a.said);
        JTextArea words=new JTextArea(6,36);words.setLineWrap(true);words.setWrapStyleWord(true);
        words.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(DesktopUi.LINE),BorderFactory.createEmptyBorder(8,10,8,10)));
        JPanel body=DesktopUi.column();
        JPanel kindHolder=new JPanel(new GridBagLayout());kindHolder.setOpaque(false);kindHolder.add(kind);
        JPanel areaHolder=new JPanel(new GridBagLayout());areaHolder.setOpaque(false);areaHolder.add(area);
        body.add(DesktopUi.row(DesktopUi.body("What is it?"),kindHolder));body.add(DesktopUi.row(DesktopUi.body("About"),areaHolder));
        DesktopUi.gap(body,DesktopUi.S);DesktopUi.add(body,words);DesktopUi.gap(body,DesktopUi.S);
        DesktopUi.add(body,DesktopUi.note("It opens a filled-in form on GitHub under your own name; nothing is sent from here. Reports are public, so say what the app did rather than who you are.",380,DesktopUi.QUIET,DesktopUi.BODY.deriveFont(12.5f)));
        JButton go=DesktopUi.primary("Open the form",()->{
            String url=Feedback.url(DesktopUpdate.SOURCE,Feedback.Kind.values()[kind.getSelectedIndex()],Feedback.Area.values()[area.getSelectedIndex()],words.getText(),"",
                "Mininotes for Windows v"+VERSION+", "+System.getProperty("os.name"));
            try{java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));box[0].dispose();}catch(Exception e){failed(e);}
        });
        box[0]=DesktopUi.sheet(frame,"Feedback",body,DesktopUi.footer(go),true);box[0].getRootPane().putClientProperty("focus",words);DesktopUi.show(box[0],480,560);
    }

    /** The rung of the reading ladder notes are read at here, unless one has its own: ten, as on the phone (see DesktopLook). */
    int textSize(){return rung;}
    /** Every note without a size of its own at one rung; kept, as the phone keeps it. */
    void setTextSize(int which) {
        showSize(which);
        int kept=rung;disk.submit(()->{DesktopLook.keepRung(context,kept);return null;},done->{},e->{});
    }
    /** The rung the open note is read at: its own, or this PC's. */
    int pageSize(){return Reading.of(base==null?Reading.NONE:base.rung,rung);}
    /** The ladder at the top of the menu: the open note's size while one is open, as its own Text size is; this PC's when none is. */
    private void sizeHere(int which) {
        if(base!=null&&onPage)resize(noteAsThing(),DesktopLook.clamp(which));else setTextSize(which);
    }
    private NoteStore.Branch noteAsThing(){return new NoteStore.Branch(NoteStore.Branch.Kind.PAGE,base.id,base.book,base.title,"",0,0,false,base.colour);}
    /** The open note as its face is drawn: its colour as the page has it, and what it wears as last read. */
    private NoteStore.Branch noteLook(){NoteStore.Branch look=noteAsThing();look.icon=noteIcon;look.image=noteImage;return look;}
    /** Text size ▸ for one note, at the rung it has of its own as the notebook last said. */
    private JMenu sizeMenu(NoteStore.Branch thing) {
        int own=base!=null&&base.id.equals(thing.id)?base.rung:rungsNow.getOrDefault(thing.id,Reading.NONE);
        return DesktopLook.sizes(own,rung,which->resize(thing,which));
    }
    /**
     * A size given to one note, or taken back with Reading.NONE: kept on this PC only, and the page drawn at it at
     * once when it is the one open. A note that is not open shows nothing, so the bar says what it will open at.
     */
    void resize(NoteStore.Branch thing,int which) {
        int kept=Reading.stored(which);boolean open=base!=null&&base.id.equals(thing.id);
        if(open){base.rung=kept;showSize(rung);}
        Map<String,Integer> now=new HashMap<>(rungsNow);if(Reading.known(kept))now.put(thing.id,kept);else now.remove(thing.id);rungsNow=now;
        disk.submit(()->{store.size(thing.id,kept);return null;},done->{
            if(!open){String name=thing.name==null||thing.name.isBlank()?"This note":"“"+thing.name+"”";
                status.setText(Reading.known(kept)?name+" opens at text size "+(kept+1)+" of "+Reading.RUNGS:name+" opens at the same size as other notes");}
        },this::failed);
    }
    /** This PC's rung, and the page at the rung the open note is read at: its own if it has one. */
    private void showSize(int which) {
        rung=DesktopLook.clamp(which);float size=DesktopLook.size(pageSize());
        page.setFont(page.getFont().deriveFont(size));title.setFont(title.getFont().deriveFont((float)Math.round(size*24f/18f)));
        page.revalidate();page.repaint();title.revalidate();
    }

    private NoteStore.Branch find(String id) {
        if(id==null)return null;for(NoteStore.Branch one:everything)if(one.id.equals(id))return one;return null;
    }
    /**
     * A new name for a collection, at any depth - typed where the name is written while its card is up, else in a box;
     * a note's name is its title, typed where it is.
     */
    void rename(NoteStore.Branch branch) {
        if(branch.kind==NoteStore.Branch.Kind.PAGE){if(onPage&&base!=null&&base.id.equals(branch.id)){title.requestFocusInWindow();title.selectAll();}else{save(()->open(branch.id));}return;}
        if(!isCollection(branch))return;
        NoteStore.Branch card=home.folder.shown();
        if(!onPage&&card!=null&&card.id.equals(branch.id)){home.folder.startNaming();return;}
        String name=DesktopUi.ask(frame,"Rename collection","Name",branch.name);
        if(name==null||name.isBlank()||name.trim().equals(branch.name))return;
        String was=branch.name;
        disk.submit(()->{store.renameCollection(branch.id,name.trim());return null;},done->{
            status.setText("Renamed to "+name.trim());refresh();
            // Only where there was a name before, as on the phone: back to a made-up one is no undo.
            if(!was.isBlank()&&!was.equals("New collection")&&!was.equals("New book")&&!was.equals(NoteStore.UNTITLED))
                canUndo(was,()->{store.renameCollection(branch.id,was);return null;});
        },this::failed);
    }
    /** What is starred, by id: the tree puts a star after it, so the names still start in one column. */
    static volatile Set<String> STARRED=Set.of();
    record Item(NoteStore.Branch branch){public String toString(){
        if(branch.kind==NoteStore.Branch.Kind.FAVOURITES)return "Favourites  ★";
        return branch.name+(STARRED.contains(branch.id)?"  ★":"");}}
    void menu(JButton anchor) {
        // One section per kind of thing, each under its own small heading: the open note first, then making
        // new things, then the places things are put away, then people, backups, and the app itself.
        JPopupMenu menu=new JPopupMenu();
        boolean noteOpen=base!=null&&onPage;
        // The reading ladder first, as at the top of every menu on the phone.
        // While a note is open it is that note's size, the same as its own Text size below.
        menu.add(DesktopLook.ladder(()->noteOpen?pageSize():rung,this::sizeHere));menu.addSeparator();
        undoRow(menu);
        NoteStore.Branch card=noteOpen?null:home.folder.shown();
        if(card!=null) {
            heading(menu,"This collection");
            menu.add(colourMenu(card));lookRows(menu,card);
            menu.addSeparator();
        } else if(!onPage&&!home.folder.isOpen()) {
            heading(menu,"Home");
            homeRows(menu);
            menu.addSeparator();
        }
        if(noteOpen) {
            heading(menu,"This note");
            // In the order the note's own menu has it, as the phone's ⋮ on a note does (see thingMenu).
            menu.add(colourMenu(noteAsThing()));lookRows(menu,noteAsThing());menu.add(sizeMenu(noteAsThing()));
            item(menu,"Versions…",()->save(this::versions));
            item(menu,"Attach a file…",()->save(this::attach));item(menu,"Attachments…",this::attachments);
            item(menu,"Move to…",()->save(()->moveTo(new NoteStore.Branch(NoteStore.Branch.Kind.PAGE,base.id,base.book,base.title==null||base.title.isBlank()?"this note":base.title,"",0,0,false))));
            item(menu,"Share…",()->{selected=null;save(this::share);});
            boolean starred=STARRED.contains(base.id);
            item(menu,starred?"Remove from favourites":"Add to favourites",()->favourite(new NoteStore.Branch(NoteStore.Branch.Kind.PAGE,base.id,base.book,base.title,"",0,0,false),!starred));
            item(menu,"Archive",()->save(()->putAway(false)));item(menu,"Move to bin",()->save(()->putAway(true)));
            item(menu,"Copy the text",this::copyNote);
            menu.addSeparator();
        }
        heading(menu,"Find");
        menu.add(line("Search…","control F",true,()->save(()->{showHome();home.focusSearch();})));
        // The tree, ticked while it shows beside Home and the page: the list of everything (decision 26). The tick is drawn
        // at the right, where it moves no word: a check box's own tick pushed "Tree" out of the column the others start in.
        boolean shown=side.isVisible();
        JMenuItem tree=ticked("Tree",shown,()->showTree(!side.isVisible()));tree.setToolTipText("Ctrl+B");menu.add(tree);
        menu.addSeparator();
        heading(menu,"New");
        menu.add(line("New note","control N",true,()->save(this::newNote)));
        item(menu,"New collection",()->save(()->addShelf(null)));
        // And one inside the collection shown or chosen, or beside the open note's, where that is not the top: named, so it is plain where.
        NoteStore.Branch inside=shelfParent();if(inside!=null)item(menu,"New collection in “"+named(inside)+"”",()->save(()->addShelf(inside)));
        menu.addSeparator();
        // The archive and the bin are icons on Home unless they were switched off (decision 41): one way to each, not two.
        if(!awayOnHome()) {
            heading(menu,"Put away");
            item(menu,"Bin…",()->restore(true));item(menu,"Archive…",()->restore(false));
            menu.addSeparator();
        }
        heading(menu,"People");
        item(menu,"People and devices…",()->people(null));item(menu,"From another device…",()->scanCode(null));
        menu.addSeparator();
        heading(menu,"Backup");
        item(menu,"Export backup…",()->save(this::backup));item(menu,"Add from backup…",()->save(this::importBackup));
        menu.addSeparator();
        heading(menu,"Mininotes");
        // Files straight to another device, belonging to no note. The ones that came that way are on Home, new until they
        // are opened; what went from here is listed under Sent files, now there is no drop box (decision 20).
        item(menu,"Send files…",()->DesktopDrops.send(this));
        item(menu,"Sent files…",()->DesktopDrops.sentFiles(this));
        item(menu,"Settings…",()->DesktopSettings.open(this));item(menu,"Profile…",this::profile);
        item(menu,"Share Mininotes…",this::shareApp);item(menu,"Feedback…",this::feedback);item(menu,"About",this::about);
        item(menu,"Exit Mininotes",()->save(()->shutdown(true)));
        menu.show(anchor,0,anchor.getHeight());
    }
    /** Share, right-clicked: sharing what is open, syncing it, and the people it could go to. */
    JPopupMenu shareMenu() {
        selected=onPage?null:home.chosen();
        JPopupMenu menu=new JPopupMenu();boolean open=target()!=null;
        menu.add(line("Share…",null,open,()->save(this::share)));
        item(menu,"Sync now",()->save(()->sync(true)));
        item(menu,"People and devices…",()->people(null));
        menu.add(line("Show my code…",null,open,()->save(()->{NoteStore.Branch what=target();if(what!=null)showCode(what);})));
        return menu;
    }
    /** A section's name inside a menu: small, quiet, not something to press. */
    private static void heading(JPopupMenu menu,String name) {
        JLabel label=new JLabel(name.toUpperCase(java.util.Locale.ROOT));label.setFont(DesktopUi.BODY.deriveFont(Font.BOLD,11f));label.setForeground(QUIET);
        label.setBorder(BorderFactory.createEmptyBorder(8,16,2,16));menu.add(label);
    }
    /** An arrow pointing left: ← Home, at the left of a note's bar. */
    private static Icon backIcon() {
        return new Icon(){
            public int getIconWidth(){return 14;}public int getIconHeight(){return 14;}
            public void paintIcon(Component c,Graphics g0,int x,int y) {
                Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(INK);g.setStroke(new BasicStroke(1.7f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.drawLine(x+2,y+7,x+13,y+7);g.drawPolyline(new int[]{x+7,x+2,x+7},new int[]{y+2,y+7,y+12},3);
                g.dispose();
            }
        };
    }
    /**
     * The tree beside Home and the page, or not (decision 26): ⋯ → Tree, Ctrl+B, the grip on its edge and the switch in
     * Settings all turn it, and which it is is kept for the next time.
     */
    void showTree(boolean shown) {
        side.setVisible(shown);split.setDividerSize(shown?GRIP:0);if(shown)split.setDividerLocation(sideWidth);
        split.revalidate();treeWanted=shown;
        disk.submit(()->{context.getSharedPreferences("settings",0).edit().putString(TREE,Boolean.toString(shown)).apply();return null;},done->{},this::failed);
    }
    static final String DOWNLOAD="https://github.com/mininotesorg/mininotes/releases/latest";
    /** Three lines to send with the link: what it is, what it is for, where to get it. The same words as the phone's. */
    static final String INVITE="I use Mininotes to keep notes and lists with the people close to me: a private paper pad, sealed from phone to phone, nothing to sign up for.\nAndroid: open the link and install the .apk file.\n";

    /** Mininotes, handed on: a code for a phone's camera, and the three lines with the link to paste anywhere. */
    void shareApp() {
        JPanel body=DesktopUi.column();
        DesktopUi.add(body,DesktopUi.note("Somebody with their phone here can scan the code with its camera. For anybody else, copy the message and send it.",400,DesktopUi.INK,DesktopUi.BODY));
        DesktopUi.gap(body,DesktopUi.M);
        try {
            JLabel code=new JLabel(new ImageIcon(DesktopQr.draw(DOWNLOAD,220)));JPanel centred=new JPanel(new GridBagLayout());centred.setOpaque(false);centred.add(DesktopUi.card(null,code));
            DesktopUi.add(body,centred);DesktopUi.gap(body,DesktopUi.M);
        } catch(Exception noCode){/* the message is enough */}
        DesktopUi.Text message=DesktopUi.note(INVITE+DOWNLOAD,400,DesktopUi.QUIET,DesktopUi.BODY.deriveFont(13f));
        DesktopUi.add(body,message);DesktopUi.gap(body,DesktopUi.S);
        DesktopUi.Text copied=DesktopUi.quiet(" ");
        DesktopUi.add(body,DesktopUi.actions(DesktopUi.primary("Copy the message",()->{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(INVITE+DOWNLOAD),null);copied.setText("Copied. Paste it into a message or an email.");})));
        DesktopUi.gap(body,4);DesktopUi.add(body,copied);
        DesktopUi.tell(frame,"Share Mininotes",body);
    }

    // ---- a newer version --------------------------------------------------------------------------------------

    /** A newer version already downloaded and checked, waiting beside this app to take its place; or null. */
    private Path staged;private String stagedVersion="";
    private boolean fetching;

    /** Whether new versions are fetched by themselves. On unless it was switched off. */
    /** How long after the writing stops a note goes, in seconds, or NoteStore.WHEN_ASKED: the app's own setting. */
    int syncAfter() {
        try{return Integer.parseInt(context.getSharedPreferences("settings",0).getString("syncAfter",Integer.toString(NoteStore.USUALLY)));}
        catch(NumberFormatException e){return NoteStore.USUALLY;}
    }
    /** The setting changed: kept, used at once for everything that has no timing of its own, and what is waiting goes. */
    void setSyncAfter(int seconds) {
        context.getSharedPreferences("settings",0).edit().putString("syncAfter",Integer.toString(seconds)).apply();
        store.usually=seconds;if(seconds<0)due.clear();else queuePending();
    }

    /** Whether the tree is showing beside the page. */
    boolean treeShown(){return side.isVisible();}

    /**
     * A row that is on or off, ticked at the right while it is on, where the tick moves no word: a check box's own tick
     * pushed the words out of the column the others start in.
     */
    static JMenuItem ticked(String said,boolean on,Runnable flip) {
        JMenuItem row=new JMenuItem(said){
            @Override protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);if(!on)return;
                Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(ACCENT);g.setStroke(new BasicStroke(1.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                int x=getWidth()-28,y=getHeight()/2;g.drawPolyline(new int[]{x,x+4,x+12},new int[]{y,y+4,y-5},3);g.dispose();
            }
        };
        row.getAccessibleContext().setAccessibleName(said+(on?", on":", off"));
        row.addActionListener(a->flip.run());
        return row;
    }

    /** Favourites and the search on Home, shown or not (docs/HOME.md, decision 47): Settings and Home's menu switch them. */
    static final String SHOW_DOCK="showDock",SHOW_SEARCH="showSearch";
    boolean showing(String which){return !"false".equals(context.getSharedPreferences("settings",0).getString(which,"true"));}
    void setShowing(String which,boolean on) {
        context.getSharedPreferences("settings",0).edit().putString(which,Boolean.toString(on)).apply();
        if(home!=null)home.showFoot();
    }

    /** Home's own colour, kept as the phone keeps it (its "colour" setting): the room Home is drawn in (decision 44). */
    static final String HOME_COLOUR="colour";
    int homeColour(){return (int)context.getSharedPreferences("settings",0).getLong(HOME_COLOUR,Tint.NONE);}
    void paintHome(int colour) {
        disk.submit(()->{context.getSharedPreferences("settings",0).edit().putLong(HOME_COLOUR,colour).apply();return null;},
            done->{if(home!=null)home.paintRoom();},this::failed);
    }
    /** Colour ▸ for Home, with the strength under it, as for any thing. */
    JMenu homeColourMenu(){return DesktopLook.colours(homeColour(),this::paintHome,()->tone,this::useTone);}

    /** Home's own rows, in Home's right-click and in ⋯ while Home is in view: its colour, then favourites and search shown or not. */
    void homeRows(JPopupMenu menu) {
        menu.add(homeColourMenu());
        if(home!=null&&Layout.active(home.grid.spots()).size()>1)item(menu,"All pages",()->home.pages.up());
        menu.add(ticked("Show favourites",showing(SHOW_DOCK),()->setShowing(SHOW_DOCK,!showing(SHOW_DOCK))));
        menu.add(ticked("Show search",showing(SHOW_SEARCH),()->setShowing(SHOW_SEARCH,!showing(SHOW_SEARCH))));
    }
    /** Updating by itself switched on or off; switched on with a newer version known, it is fetched now. */
    void setAutoUpdate(boolean on) {
        context.getSharedPreferences("settings",0).edit().putString("autoUpdate",Boolean.toString(on)).apply();
        if(on&&Update.newer(newestKnown,VERSION))fetchQuietly(newestKnown);
    }
    boolean autoUpdate(){return !"false".equals(context.getSharedPreferences("settings",0).getString("autoUpdate","true"));}

    /**
     * The version in the bar, and beside it - only when there is something to do - one filled button, as an
     * editor shows it: "Restart to update" once a newer version is downloaded, "Update" while it is only known.
     */
    /** A round dot of a colour, the size of the version's letters. */
    private static Icon dot(Color colour) {
        return new Icon(){
            public void paintIcon(Component c,Graphics g0,int x,int y){Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(colour);g.fillOval(x,y+1,8,8);g.dispose();}
            public int getIconWidth(){return 8;}
            public int getIconHeight(){return 10;}
        };
    }

    void updateShown() {
        boolean behind=Update.newer(newestKnown,VERSION);
        version.setFont(DesktopUi.BODY.deriveFont(13f));version.setForeground(QUIET);version.setText("v"+VERSION);
        // The dot before it: green on the newest there is, yellow while a newer one is out, none until GitHub has answered.
        Update.Standing standing=Update.standing(newestKnown,VERSION);
        version.setIcon(standing==Update.Standing.UNKNOWN?null:dot(new Color(Tint.of(standing==Update.Standing.BEHIND?3:4,false),true)));
        version.setToolTipText("Mininotes for Windows v"+VERSION+(standing==Update.Standing.LATEST?", the newest there is":standing==Update.Standing.BEHIND?", v"+newestKnown+" is out":"")+". Click for updates.");
        version.getAccessibleContext().setAccessibleName(version.getToolTipText());
        updateButton.setText(staged!=null?"Restart to update":"Update");updateButton.setVisible(staged!=null||behind);
        updateButton.setToolTipText(staged!=null?"Mininotes v"+stagedVersion+" is downloaded and checked. Restart to use it."
            :"Mininotes v"+newestKnown+" is out. Click to get it.");
        updateButton.getAccessibleContext().setAccessibleName(updateButton.getToolTipText());
    }

    /**
     * Whether a newer version is out: once a day by itself, or now when asked. What was last found is kept,
     * so the bar says so from the moment Mininotes opens. Found by itself, and updating by itself is on, it
     * is downloaded straight away and waits for a restart.
     */
    void lookForUpdate(boolean asked) {
        var kept=context.getSharedPreferences("settings",0);
        newestKnown=Update.read(kept.getString("updateNewest",""));updateShown();
        long looked;try{looked=Long.parseLong(kept.getString("updateLooked","0"));}catch(NumberFormatException e){looked=0;}
        if(!asked&&!Update.due(System.currentTimeMillis(),looked)){if(Update.newer(newestKnown,VERSION)&&autoUpdate())fetchQuietly(newestKnown);return;}
        if(asked){version.setText("Checking…");version.setForeground(ACCENT);}
        connectivity.submit(DesktopUpdate::latest,newest->{
            kept.edit().putString("updateNewest",newest).putString("updateLooked",Long.toString(System.currentTimeMillis())).apply();
            newestKnown=newest;updateShown();
            if(Update.newer(newest,VERSION)&&autoUpdate()&&!asked)fetchQuietly(newest);
            if(asked)updatesBox(null);
        },e->{updateShown();if(asked)updatesBox(e.getMessage());});
    }

    /** Downloaded and checked in the background; the bar then offers the restart. Failing is quiet: it is tried again. */
    private void fetchQuietly(String newest) {
        Path app=DesktopUpdate.appFolder();
        if(app==null||fetching||(staged!=null&&stagedVersion.equals(newest)))return;
        fetching=true;
        connectivity.submit(()->DesktopUpdate.fetch(newest,app,p->{}),opened->{fetching=false;staged=opened;stagedVersion=newest;updateShown();
            status.setText("Mininotes v"+newest+" is ready. Restart to update, or it is put in place when you close Mininotes.");},
            e->fetching=false);
    }

    /** The newer version put in place of this one, and Mininotes opened again on it. */
    private void restartToUpdate() {
        Path app=DesktopUpdate.appFolder();
        if(app==null||staged==null)return;
        try{DesktopUpdate.replaceAfterExit(app,staged);staged=null;save(()->shutdown(true));}
        catch(Exception e){failed(e);}
    }

    /**
     * Everything about updates in one box, opened from the version: which version this is, whether a newer
     * one is out - or why that could not be known - the one thing to do about it, and the switch for doing it
     * by itself.
     */
    void updatesBox(String problem) {
        Path app=DesktopUpdate.appFolder();
        boolean behind=Update.newer(newestKnown,VERSION);
        JDialog[] box={null};
        JPanel body=DesktopUi.column();
        String said=staged!=null?"Mininotes v"+stagedVersion+" is downloaded and checked. Restart to use it; your notes stay as they are."
            :problem!=null?problem
            :behind?"Mininotes v"+newestKnown+" is available. You have v"+VERSION+"."
            :"You have the newest version, v"+VERSION+".";
        DesktopUi.add(body,DesktopUi.note(said,400,problem!=null?DesktopUi.WARN:DesktopUi.INK,DesktopUi.BODY));
        if(behind&&staged==null&&app==null){DesktopUi.gap(body,DesktopUi.S);DesktopUi.add(body,DesktopUi.note("This copy is not the packaged app, so it cannot replace itself: get the new version from the releases page.",400,DesktopUi.QUIET,DesktopUi.BODY.deriveFont(13f)));}
        DesktopUi.gap(body,DesktopUi.M);
        DesktopUi.Text progress=DesktopUi.quiet(" ");DesktopUi.add(body,progress);
        JCheckBox auto=DesktopUi.toggle("Update automatically",autoUpdate());
        auto.addActionListener(e->{context.getSharedPreferences("settings",0).edit().putString("autoUpdate",Boolean.toString(auto.isSelected())).apply();
            if(auto.isSelected()&&Update.newer(newestKnown,VERSION))fetchQuietly(newestKnown);});
        JPanel card=DesktopUi.column();card.add(DesktopUi.switchRow("Update automatically",auto));
        DesktopUi.add(card,DesktopUi.note("New versions are downloaded from the Mininotes releases on GitHub, checked, and put in place when you restart or close Mininotes.",380,DesktopUi.QUIET,DesktopUi.BODY.deriveFont(12.5f)));
        DesktopUi.add(body,DesktopUi.card(null,card));
        JButton go=null;
        if(staged!=null)go=DesktopUi.primary("Restart to update",()->{box[0].dispose();restartToUpdate();});
        else if(behind&&app==null)go=DesktopUi.primary("Open the releases page",()->{try{java.awt.Desktop.getDesktop().browse(java.net.URI.create(DesktopUpdate.RELEASES));}catch(Exception e){failed(e);}box[0].dispose();});
        else if(behind) {
            JButton[] self={null};
            go=DesktopUi.primary("Update now",()->{
                self[0].setEnabled(false);progress.setForeground(DesktopUi.QUIET);progress.setText("Downloading…");
                String newest=newestKnown;
                new SwingWorker<Path,Integer>(){
                    protected Path doInBackground() throws Exception{return DesktopUpdate.fetch(newest,app,p->publish(p));}
                    protected void process(java.util.List<Integer> got){int p=got.get(got.size()-1);progress.setText(p<0?"Downloading…":"Downloading… "+p+"%");}
                    protected void done(){
                        try{staged=get();stagedVersion=newest;updateShown();progress.setText("Checked. Mininotes closes and opens again with v"+newest+"…");box[0].dispose();restartToUpdate();}
                        catch(Exception e){Throwable why=e.getCause()!=null?e.getCause():e;progress.setForeground(DesktopUi.WARN);
                            progress.setText(why.getMessage()!=null?why.getMessage():"The update could not be installed. This version is unchanged.");self[0].setEnabled(true);}
                    }
                }.execute();
            });
            self[0]=go;
        } else if(problem!=null)go=DesktopUi.button("Try again",()->{box[0].dispose();lookForUpdate(true);});
        box[0]=DesktopUi.sheet(frame,"Updates",body,go==null?null:DesktopUi.footer(go),true);
        if(go!=null)box[0].getRootPane().setDefaultButton(go);
        DesktopUi.show(box[0],460,460);
    }

    /** Kept for the gallery and anything that already knows a newer version: the same box. */
    void updateBox(String newest){newestKnown=newest;updateShown();updatesBox(null);}

    /** Closing with a newer version waiting: it is put in place as Mininotes goes, and not opened. */
    void updateOnExit() {
        if(staged==null)return;
        Path app=DesktopUpdate.appFolder();
        if(app==null||staged==null)return;
        // Throwable, not Exception: nothing about an update may stop Mininotes from closing. The packaged app once
        // shipped without the part of Java the updater needs, and this line then threw an Error that ended every
        // close half-way - the window stayed, and a newer version could never take over.
        try{DesktopUpdate.replaceAfterExit(app,staged,ProcessHandle.current().pid(),false);staged=null;}catch(Throwable e){/* the next start downloads it again */}
    }

    void securityShown() {
        boolean on=DesktopLock.locked(context.getFilesDir().toPath());
        security.setIcon(DesktopLock.padlock(on,15));security.setText(on?"Encrypted":"Not encrypted");
        security.setForeground(on?ACCENT:QUIET);security.setToolTipText(on?"Your notes on this PC are encrypted with a password. Click for Security.":"Your notes on this PC are not encrypted. Click to lock them with a password.");
        security.getAccessibleContext().setAccessibleName(security.getText());
    }
    /** The Minima address donations go to - the same one the phone's About shows. */
    static final String DONATE="MxG087BNANGRNYJAAKU73AHE0YSUE9NYQU1PJCFK4E5J6010W5U68ABKKGHS7AQ";
    void about() {
        JPanel body=DesktopUi.column();
        JLabel brand=new JLabel("Mininotes",new ImageIcon(DesktopIcon.image(48)),SwingConstants.LEFT);brand.setIconTextGap(14);brand.setFont(DesktopUi.BODY.deriveFont(Font.BOLD,20f));brand.setForeground(INK);
        DesktopUi.add(body,brand);DesktopUi.gap(body,DesktopUi.S);DesktopUi.add(body,DesktopUi.quiet("Windows v"+VERSION+" · preview"));DesktopUi.gap(body,DesktopUi.M);
        // What it is and where it is from, said as what it is; every switch is in Settings.
        DesktopUi.Text words=DesktopUi.note("A paper pad for the people close to you, shared over Maxima, the communication layer of Minima.\n\n"+(DesktopLock.locked(context.getFilesDir().toPath())?"Your notes on this PC are locked and encrypted.":"Lock Mininotes in Settings to encrypt your notes on this PC.")+" Free software under the GNU GPL. Its icons are Lucide's, under the ISC licence.");
        DesktopUi.add(body,words);DesktopUi.gap(body,DesktopUi.M);
        // Donate, as on the phone: the Minima address in full, and a button that copies it.
        DesktopUi.add(body,DesktopUi.quiet("Donate"));DesktopUi.gap(body,DesktopUi.S);
        DesktopUi.add(body,DesktopUi.note(DONATE,380,INK,DesktopUi.BODY.deriveFont(12.5f)));DesktopUi.gap(body,DesktopUi.S);
        DesktopUi.add(body,DesktopUi.actions(DesktopUi.button("Copy the address",()->{
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(DONATE),null);
            status.setText("Donation address copied");})));
        DesktopUi.gap(body,DesktopUi.M);
        DesktopUi.add(body,DesktopUi.actions(DesktopUi.button("Source code",()->{try{java.awt.Desktop.getDesktop().browse(java.net.URI.create(DesktopUpdate.SOURCE));}catch(Exception e){failed(e);}}),
            DesktopUi.button("Check for updates",()->lookForUpdate(true))));
        DesktopUi.tell(frame,"About",body);
    }
    private void item(JPopupMenu menu,String label,Runnable go){JMenuItem item=new JMenuItem(label);item.addActionListener(e->go.run());menu.add(item);}
    /**
     * A new collection inside {@code parent}, or on Home where that is null: made as Untitled, and its card opened with
     * the name ready to be typed over, as the + makes one.
     */
    private void addShelf(NoteStore.Branch parent) {
        boolean top=parent==null||parent.kind==NoteStore.Branch.Kind.LIBRARY;
        home.newCollection(top?Things.HOME:parent.id);
    }
    /**
     * Where New collection in… puts one: the collection whose card is up, or the one the open note is in. Null on Home,
     * which New collection makes already.
     */
    private NoteStore.Branch shelfParent() {
        NoteStore.Branch card=onPage?null:home.folder.shown();
        if(card!=null)return card;
        NoteStore.Branch holder=onPage&&base!=null?find(base.book):null;
        return isCollection(holder)?holder:null;
    }
    /** A collection, at any depth: every one is COLLECTION now, and a line made from an older one may say BOOK. */
    private static boolean isCollection(NoteStore.Branch thing){return thing!=null&&DesktopMoving.shelf(thing.kind);}
    private static String named(NoteStore.Branch thing){return thing.name==null||thing.name.isBlank()?"Untitled":thing.name;}
    /** The open note put in the archive or the bin: out of the overview, and Home is what is left. */
    private void putAway(boolean bin) {
        if(base==null)return;String id=base.id,name=title.getText();
        disk.submit(()->{store.putAway(NoteStore.Branch.Kind.PAGE,id,bin,true);return null;},done->{
            canUndo(name,()->{store.restore(NoteStore.Branch.Kind.PAGE,id);return null;});
            status.setText(bin?"Moved to the bin":"Archived");
            overview.forget(id);carets.remove(id);showHome();if(base!=null&&base.id.equals(id))base=null;refresh();},this::failed);
    }
    /**
     * The bin or the archive, whole: each thing with Put back beside it - and, in the bin, Delete for good -
     * and Empty the bin at the foot. The same choices as on the phone.
     */
    void restore(boolean bin) {
        disk.submit(()->store.heldIn(bin),rows->{
            if(rows.isEmpty()){status.setText(bin?"The bin is empty":"The archive is empty");return;}
            JDialog[] box={null};JPanel list=DesktopUi.column();
            for(NoteStore.Branch row:rows) {
                String kind=row.kind==NoteStore.Branch.Kind.PAGE?"note":"collection";
                JButton back=button("Put back",()->disk.submit(()->{store.restore(row.kind,row.id);return null;},done->{box[0].dispose();status.setText("Put back");refresh();restore(bin);},this::failed));
                JButton gone=bin?button("Delete for good",()->{
                    if(!DesktopUi.confirm(box[0],"Delete for good?","“"+row.name+"” and everything in it is deleted from this PC. It cannot be put back.","Delete for good",true))return;
                    disk.submit(()->{store.erase(row.kind,row.id);return null;},done->{box[0].dispose();status.setText("Deleted for good");refresh();restore(true);},this::failed);
                }):null;
                JPanel right=new JPanel(new GridBagLayout());right.setOpaque(false);right.add(DesktopUi.actions(back,gone));
                JPanel line=DesktopUi.row(DesktopUi.person(row.name.isBlank()?"Untitled":row.name,kind),right);
                DesktopMenus.echoOnRightClick(line,back,null,gone);list.add(line);
            }
            JButton empty=bin?DesktopUi.danger("Empty the bin",()->{
                if(!DesktopUi.confirm(box[0],"Empty the bin?","Everything in the bin is deleted from this PC. It cannot be put back.","Empty the bin",true))return;
                disk.submit(store::emptyBin,n->{box[0].dispose();status.setText(n==1?"1 thing deleted for good":n+" things deleted for good");refresh();},this::failed);
            }):null;
            box[0]=DesktopUi.sheet(frame,bin?"Bin":"Archive",DesktopUi.scrolling(DesktopUi.card(null,list)),empty==null?null:DesktopUi.footer(empty),true);
            DesktopUi.show(box[0],560,640);
        },this::failed);
    }

    /** Empty the bin, from the bin's icon or its card: how many things go is said first, since it cannot be put back. */
    void askEmptyBin() {
        disk.submit(()->store.awayCount(true),n->{
            if(n==0){status.setToolTipText(null);status.setText("The bin is already empty");return;}
            if(!DesktopUi.confirm(frame,"Empty the bin?",(n==1?"1 thing":n+" things")+" in it, and whatever they hold, deleted from this PC. It cannot be put back.","Empty the bin",true))return;
            disk.submit(store::emptyBin,gone->{status.setToolTipText(null);status.setText(gone==1?"1 thing deleted for good":gone+" things deleted for good");refresh();},this::failed);
        },this::failed);
    }

    /** One thing in the bin gone for good, after one question, from its icon in the bin's card. */
    void eraseForGood(NoteStore.Branch thing) {
        String name=thing.name==null||thing.name.isBlank()?"Untitled":thing.name;
        if(!DesktopUi.confirm(frame,"Delete for good?","“"+name+"” and everything in it is deleted from this PC. It cannot be put back.","Delete for good",true))return;
        disk.submit(()->{store.erase(thing.kind,thing.id);return null;},done->{overview.forget(thing.id);status.setToolTipText(null);status.setText("Deleted for good");refresh();},this::failed);
    }

    /** Where the archive and the bin are: icons on Home (decision 41), unless switched off - then ⋯ → Put away, as before. */
    static final String AWAY_ON_HOME="awayOnHome";
    boolean awayOnHome(){return !"false".equals(context.getSharedPreferences("settings",0).getString(AWAY_ON_HOME,"true"));}
    void setAwayOnHome(boolean on) {
        disk.submit(()->{context.getSharedPreferences("settings",0).edit().putString(AWAY_ON_HOME,Boolean.toString(on)).apply();return null;},done->refresh(),this::failed);
    }
    void versions() {
        if(base==null)return;String id=base.id;
        disk.submit(()->store.versions(id),all->{
            if(all.isEmpty()){status.setText("No earlier versions yet");return;}
            // Each version by when it was kept and who it came from; what it said, in full, beside it.
            java.time.format.DateTimeFormatter when=java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss");
            String[] labels=all.stream().map(v->when.format(java.time.Instant.ofEpochMilli(v.at).atZone(java.time.ZoneId.systemDefault()))).toArray(String[]::new);
            JList<String> list=new JList<>(labels);list.setSelectedIndex(0);list.setFixedCellHeight(36);list.setBackground(DesktopUi.CARD);list.setCellRenderer(DesktopUi.roomy());
            JTextArea preview=new JTextArea(16,40);preview.setLineWrap(true);preview.setWrapStyleWord(true);preview.setEditable(false);preview.setText(all.get(0).body);preview.setCaretPosition(0);
            preview.setFont(DesktopUi.BODY.deriveFont(15f));preview.setBackground(DesktopUi.CARD);preview.setMargin(new Insets(12,14,12,14));
            list.addListSelectionListener(e->{if(list.getSelectedIndex()>=0){preview.setText(all.get(list.getSelectedIndex()).body);preview.setCaretPosition(0);}});
            JScrollPane left=new JScrollPane(list);left.setBorder(BorderFactory.createLineBorder(DesktopUi.LINE));left.setPreferredSize(new Dimension(210,360));
            JScrollPane right=new JScrollPane(preview);right.setBorder(BorderFactory.createLineBorder(DesktopUi.LINE));
            JPanel content=new JPanel(new BorderLayout(12,0));content.setOpaque(false);content.add(left,BorderLayout.WEST);content.add(right);
            JDialog[] box={null};boolean[] restore={false};
            JButton back=DesktopUi.primary("Put this version back",()->{restore[0]=true;box[0].dispose();});back.setEnabled(page.isEditable());
            box[0]=DesktopUi.sheet(frame,"Versions",content,DesktopUi.footer(back),true);
            DesktopUi.show(box[0],760,640);
            if(restore[0]&&page.isEditable()){var old=all.get(list.getSelectedIndex());title.setText(old.title);
                // Put back, not written: what is still on the page keeps its writer, and what comes back is nobody's.
                String was=page.getText();Writers had=page.writers();page.setText(old.body);page.putBack(was,had);save(null);status.setText("Version put back. The text it replaced is kept as a version.");}
        },this::failed);
    }
    void backup() {
        JFileChooser pick=new JFileChooser();pick.setSelectedFile(new java.io.File(context.databaseKey()!=null?"mininotes-backup-locked.mnbackup":"mininotes-backup.zip"));
        if(pick.showSaveDialog(frame)!=JFileChooser.APPROVE_OPTION)return;Path target=pick.getSelectedFile().toPath();
        if(Files.exists(target)&&!DesktopUi.confirm(frame,"Replace backup?","A file called "+target.getFileName()+" is already there. Replace it with a new backup?","Replace",true))return;
        status.setText("Writing backup…");disk.submit(()->{
            byte[] key=context.databaseKey();Path lock=context.getFilesDir().toPath().resolve(DesktopLock.KEPT);
            DesktopBackup.write(store,target,key,key==null?null:Files.readAllBytes(lock));return key!=null;
        },locked->status.setText(locked?"Backup saved, locked with your password":"Backup saved"),this::failed);
    }
    void importBackup() {
        JFileChooser pick=new JFileChooser();if(pick.showOpenDialog(frame)!=JFileChooser.APPROVE_OPTION)return;
        Path source=pick.getSelectedFile().toPath();byte[][] opener={null};
        // A locked backup is opened here, on this thread, before any work starts: its password or its words.
        if(DesktopBackup.locked(source)) {
            try {
                byte[] lock;try(var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(source)))){in.readFully(new byte[4]);lock=new byte[in.readUnsignedShort()];in.readFully(lock);}
                opener[0]=DesktopLock.openBackup(frame,lock);if(opener[0]==null)return;
            } catch(Exception e){failed(e);return;}
        }
        int how=askHowToImport();
        if(how<0)return;boolean replacing=how==1;
        if(replacing&&!confirmReplace())return;
        status.setText(replacing?"Restoring from backup…":"Adding notes from backup…");disk.submit(()->{
            int count=DesktopBackup.add(store,source,lock->opener[0],replacing);
            byte[] opened=context.databaseKey();if(opened!=null)DesktopFiles.every(store,opened,true);
            return new Object[]{count,replacing?store.latest():null};
        },done->{int count=(Integer)done[0];
            status.setText(replacing?(count==1?"1 note restored":count+" notes restored"):(count==1?"1 note added":count+" notes added"));
            if(replacing) {
                // What was open may be gone: the overview is emptied, and the newest note of the restored pad opens.
                undoHow=null;overview.clear();carets.clear();base=null;home.folder.close();
                NoteStore.Note latest=(NoteStore.Note)done[1];if(latest!=null)display(latest);else showHome();
            }
            refresh();},this::failed);
    }
    /** Added beside what is here, or put in its place - the phone's two ways, in the phone's words. 1 is replace; -1, neither. */
    int askHowToImport() {
        return DesktopUi.choose(frame,"Import this backup","Adding keeps your notes and brings the backup's in beside them, as copies. Replacing is a restore: this pad becomes what the backup was.",
            new String[]{"Add to this pad","Replace everything"},0,"Import");
    }
    /** Replacing cannot be undone, so it is said what goes, and asked once more. */
    boolean confirmReplace() {
        int here=0;for(NoteStore.Branch one:everything)if(one.kind==NoteStore.Branch.Kind.COLLECTION)here++;
        return DesktopUi.confirm(frame,"Replace everything?",(here==0?"This pad":here==1?"The one collection on this pad":"All "+here+" collections on this pad")
            +" and every note and file in them are deleted, and the backup is put in their place.\n\nThis cannot be undone. Export what is here first if you are not sure.","Replace everything",true);
    }
    /** Several files can be picked at once; each is kept or refused on its own, with one line for all of them. See {@link Given}. */
    private void attach() {
        if(base==null||!page.isEditable())return;JFileChooser pick=new JFileChooser();pick.setMultiSelectionEnabled(true);if(pick.showOpenDialog(frame)!=JFileChooser.APPROVE_OPTION)return;
        java.io.File[] chosen=pick.getSelectedFiles();if(chosen.length==0&&pick.getSelectedFile()!=null)chosen=new java.io.File[]{pick.getSelectedFile()};
        if(chosen.length==0)return;
        List<Path> sources=new ArrayList<>();for(java.io.File one:chosen)sources.add(one.toPath());
        attach(base.id,null,sources);
    }
    /**
     * These files kept with this note: chosen with Attach, or dropped on it (see DesktopDrops.aim). {@code called}
     * is the note's, said with the outcome when it is not the one open.
     */
    void attach(String id,String called,List<Path> sources) {
        status.setToolTipText(null);
        if(isOpen(id)&&!page.isEditable()){status.setText("This note is read only here");return;}
        status.setText(Given.adding(sources.size()));
        disk.submit(()->{
            if(Boolean.TRUE.equals(store.readOnlyHere(id)[0]))throw new IllegalStateException("This note is read only here");
            List<String> refused=new ArrayList<>();int kept=0;String only=null;
            for(Path source:sources) {
                String name=Attachment.named(source.getFileName().toString());
                try {
                    long size=Files.size(source);
                    if(size>Attachment.LIMIT){refused.add(Given.refusal(name,Given.TOO_BIG));continue;}
                    if(store.weight()+size>Attachment.PLENTY){refused.add(Given.refusal(name,Given.FULL));continue;}
                    String key=UUID.randomUUID().toString();Path dest=store.fileFor(key).toPath();DesktopFiles.keep(context,source,dest);
                    try{store.keep(new NoteStore.Held(key,id,name,Attachment.kind(Files.probeContentType(source)),size,System.currentTimeMillis()));}
                    catch(Exception failure){Files.deleteIfExists(dest);throw failure;}
                    kept++;only=name;
                } catch(Exception unreadable){refused.add(Given.refusal(name,Given.UNREADABLE));}
            }
            return new Object[]{kept,only,refused};
        },done->{
            int kept=(Integer)done[0];@SuppressWarnings("unchecked") List<String> refused=(List<String>)done[2];
            // The count, and which were not kept and why, on the same line; all of it again on hover if it is long.
            String said=Given.added(kept,(String)done[1],refused.size());
            // Another note than the one open: which, after "added", so the person knows where they went.
            if(kept>0&&called!=null&&!isOpen(id)){int at=said.lastIndexOf(" added")+6;said=said.substring(0,at)+" to “"+called+"”"+said.substring(at);}
            status.setText(said+(refused.isEmpty()?"":". "+String.join("; ",refused)));
            status.setToolTipText(refused.isEmpty()?null:Given.refusals(refused));
            if(kept>0)attachmentsChanged(id);
        },this::failed);
    }
    /**
     * A picture pasted on a note - a screenshot from Greenshot or the Snipping Tool, a picture copied in a browser - kept
     * with it as a PNG, as a messaging app takes one (the owner's ask, 2026-10-01; see Attachment.pasted). It is named for
     * when it came, as a recording is, and goes by the road every attachment takes: the same limits, sealed on the way in
     * when the notebook is locked, said in the bar.
     */
    void attachPicture(String id,String called,BufferedImage picture) {
        status.setToolTipText(null);
        if(isOpen(id)&&!page.isEditable()){status.setText("This note is read only here");return;}
        String name=Attachment.picture(System.currentTimeMillis());
        status.setText("Adding the picture…");
        disk.submit(()->{
            if(Boolean.TRUE.equals(store.readOnlyHere(id)[0]))throw new IllegalStateException("This note is read only here");
            ByteArrayOutputStream png=new ByteArrayOutputStream();
            if(!javax.imageio.ImageIO.write(picture,"png",png))throw new IllegalStateException("That picture could not be kept");
            byte[] whole=png.toByteArray();
            if(whole.length>Attachment.LIMIT)return Given.refusal(name,Given.TOO_BIG);
            if(store.weight()+whole.length>Attachment.PLENTY)return Given.refusal(name,Given.FULL);
            String key=UUID.randomUUID().toString();Path dest=store.fileFor(key).toPath();DesktopFiles.keep(context,whole,dest);
            try{store.keep(new NoteStore.Held(key,id,name,"image/png",whole.length,System.currentTimeMillis()));}
            catch(Exception failure){Files.deleteIfExists(dest);throw failure;}
            return "";
        },refused->{
            if(!refused.isEmpty()){status.setText(Given.added(0,null,1)+". "+refused);return;}
            String said=Given.added(1,name,0);
            // Another note than the one open: which, after "added", so the person knows where it went.
            if(called!=null&&!isOpen(id))said=said+" to “"+called+"”";
            status.setText(said);
            attachmentsChanged(id);
        },this::failed);
    }
    /**
     * These files kept with a collection, or on Home: chosen with its Add a file…, or dropped from Explorer on its icon,
     * its card or Home's grid (see DesktopDrops.aim). Each is kept or refused on its own, with one line for all of them,
     * and Home and the card show them at once. {@code called} names where they went.
     */
    void keep(String collection,String called,List<Path> sources) {
        boolean onHome=NoteStore.home(collection);String into=onHome?Things.HOME:collection;
        status.setToolTipText(null);status.setText(Given.adding(sources.size()));
        disk.submit(()->{
            if(!onHome&&Boolean.FALSE.equals(store.mayWriteIn(NoteStore.Branch.Kind.COLLECTION,into)))throw new IllegalStateException("This collection is read only here");
            List<String> refused=new ArrayList<>();int kept=0;String only=null;
            for(Path source:sources) {
                String name=Attachment.named(source.getFileName().toString());
                try {
                    long size=Files.size(source);
                    if(size>Attachment.LIMIT){refused.add(Given.refusal(name,Given.TOO_BIG));continue;}
                    if(store.weight()+size>Attachment.PLENTY){refused.add(Given.refusal(name,Given.FULL));continue;}
                    NoteStore.Held held=store.opening(NoteStore.Branch.Kind.COLLECTION,into,name,Files.probeContentType(source),size);
                    Path dest=store.fileFor(held.id).toPath();DesktopFiles.keep(context,source,dest);
                    // The row is written last: until it exists the bytes are nobody's, and get swept up.
                    try{store.keep(held);}catch(Exception failure){Files.deleteIfExists(dest);throw failure;}
                    kept++;only=name;
                } catch(Exception unreadable){refused.add(Given.refusal(name,Given.UNREADABLE));}
            }
            return new Object[]{kept,only,refused};
        },done->{
            int kept=(Integer)done[0];@SuppressWarnings("unchecked") List<String> refused=(List<String>)done[2];
            String said=Given.added(kept,(String)done[1],refused.size());
            if(kept>0){int at=said.lastIndexOf(" added")+6;if(at>=6)said=said.substring(0,at)+(onHome?" to Home":" to “"+(called==null?"the collection":called)+"”")+said.substring(at);}
            status.setText(said+(refused.isEmpty()?"":". "+String.join("; ",refused)));
            status.setToolTipText(refused.isEmpty()?null:Given.refusals(refused));
            if(kept>0)refresh();
        },this::failed);
    }
    /**
     * A file added to the open note or taken out of it: whoever has the note is sent its list now, and what is
     * to go up goes up. See {@link Enclosure}.
     */
    void filesChanged(String note){network.submit(()->{Post.filesChanged(context,store,keys,note);return null;},done->updateStanding(),e->{});}
    /** What the paperclip offers: to attach something, and each file already attached. */
    private void clipMenu() {
        if(base==null)return;String id=base.id;
        disk.submit(()->store.filesOf(NoteStore.Branch.Kind.PAGE,id),files->{
            JPopupMenu menu=new JPopupMenu();
            JMenuItem add=new JMenuItem("Attach a file…");add.setEnabled(page.isEditable());add.addActionListener(e->save(this::attach));menu.add(add);
            JMenuItem record=new JMenuItem("Record audio");record.setEnabled(page.isEditable()&&!recorder.recording());record.addActionListener(e->record());menu.add(record);
            if(!files.isEmpty())menu.addSeparator();
            for(NoteStore.Held file:files){JMenuItem one=new JMenuItem(file.name+"   ·   "+Attachment.size(file.bytes));one.setToolTipText("Save a copy");one.addActionListener(e->saveCopy(file));menu.add(one);}
            menu.show(clip,clip.getWidth()-menu.getPreferredSize().width,-menu.getPreferredSize().height-4);
        },this::failed);
    }
    /** The microphone, from its button or the paperclip's menu. See {@link DesktopRecorder}. */
    void record(){if(base!=null&&page.isEditable())recorder.start();}
    /** The open note's id, or null on the cards. */
    String noteShown(){return base==null?null:base.id;}
    /** The bar stands where the microphone was while it records. */
    void recordingShown(boolean going){mic.setVisible(!going);if(mic.getParent()!=null){mic.getParent().revalidate();mic.getParent().repaint();}}
    /** A file kept with this note by something other than Attach: its cards, its count and whoever has the note. */
    void attachmentsChanged(String note){if(base!=null&&base.id.equals(note)){updateStanding();fileCards.show(note);}filesChanged(note);}
    void saveCopy(NoteStore.Held file) {
        JFileChooser pick=new JFileChooser();pick.setSelectedFile(new java.io.File(DesktopFiles.onDisk(file.name)));
        if(pick.showSaveDialog(frame)!=JFileChooser.APPROVE_OPTION)return;Path target=pick.getSelectedFile().toPath();
        if(Files.exists(target)&&!DesktopUi.confirm(frame,"Replace file?","A file called "+target.getFileName()+" is already there. Replace it?","Replace",true))return;
        disk.submit(()->{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DesktopFiles.copyOut(context,store.fileFor(file.id).toPath(),bytes);org.mininotes.desktop.platform.AtomicFile.write(target,bytes.toByteArray());return null;},done->status.setText("Copy saved"),this::failed);
    }
    void attachments() {
        if(base==null)return;String id=base.id;
        disk.submit(()->store.filesOf(NoteStore.Branch.Kind.PAGE,id),files->{
            if(files.isEmpty()){status.setText("No attachments on this note");return;}
            NoteStore.Held file=DesktopUi.pick(frame,"Attachments","Kept on this PC with this note. Save a copy to open it elsewhere.",files,
                f->f.name+"   ·   "+Attachment.size(f.bytes),"Save a copy…");
            if(file==null)return;JFileChooser pick=new JFileChooser();pick.setSelectedFile(new java.io.File(DesktopFiles.onDisk(file.name)));
            if(pick.showSaveDialog(frame)!=JFileChooser.APPROVE_OPTION)return;Path target=pick.getSelectedFile().toPath();
            if(Files.exists(target)&&!DesktopUi.confirm(frame,"Replace file?","A file called "+target.getFileName()+" is already there. Replace it?","Replace",true))return;
            disk.submit(()->{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DesktopFiles.copyOut(context,store.fileFor(file.id).toPath(),bytes);org.mininotes.desktop.platform.AtomicFile.write(target,bytes.toByteArray());return null;},done->status.setText("Copy saved"),this::failed);
        },this::failed);
    }
    private void profile() {
        DesktopProfile.open(this);
    }
    /** Something to say that nobody asked for: in the bar, and over the tray while the window is away. */
    void notice(String said){status.setToolTipText(null);status.setText(said);if(!frame.isVisible()&&tray!=null)tray.displayMessage("Mininotes",said,TrayIcon.MessageType.INFO);}
    void failed(Exception error){status.setText(error.getMessage()==null?"That did not finish. Please try again.":error.getMessage());status.setToolTipText(status.getText());}

    // Network work never queues in front of a keystroke being committed to disk.
    void startNode() {
        if(offline||nodeStarting||closing)return;nodeStarting=true;
        connection.setText("Connecting…");
        // Taken now: locked again before sharing has started, this window may no longer have what arrives (see DesktopFiles).
        long ticket=DesktopFiles.ticket();
        network.submit(()->{
            store.mySigningKey=Base64.getEncoder().encodeToString(keys.signing().getPublic().getEncoded());store.myName=Node.nameHere(context);
            store.myAgreement=Point.shorten(keys.agreement().getPublic());
            DesktopFiles.toNotebook(ticket,bytes->disk.submit(()->Post.arrived(context,store,keys,bytes),this::arrived,this::failed));
            if(!Node.listen(context,DesktopFiles::arrived))throw new IllegalStateException("Could not start sharing");
            List<String> addresses=Node.addresses(context);String address=addresses.isEmpty()?"":addresses.get(0);store.myAddress=address;
            Node.everyBeat(()->{if(!closing){Post.again(context,store,keys);Post.leftAgain(context,store,keys);Post.removedAgain(context,store,keys);SwingUtilities.invokeLater(this::queuePending);}});
            return address;
        },address->{nodeStarting=false;currentAddress=address;
            // Started, with no relay yet: still connecting. Whether it is offline is the health check's to say, fifteen seconds on.
            connection.setText(address.isEmpty()&&!Node.onlyMine(context)?"Connecting…":connected(address.isEmpty()?0:1));queuePending();
            network.submit(()->{Node.tellEverybody(context);retryAccepting();return null;},done->{},e->{});
            // Whatever arrived while the notebook was locked again, taken in now it is open.
            disk.submit(()->{int landed=DesktopFiles.takeIn(context,store,keys);Post.sayLocked(context,store,keys,false);return landed;},
                landed->{if(landed>0){refresh();if(base!=null)changedUnderneath(base.id);}},this::failed);
            // Whether Windows lets the phones in at this PC's door, said once a start: shut, the phones' notes and
            // answers go only by relay. States only - the network's kind, never its name.
            connectivity.submit(DesktopFirewall::read,state->org.mininotes.desktop.platform.util.Log.i("Mininotes/Desktop",
                "Windows firewall: the rules for devices to reach this PC are "+(state.allowed()?"on":"not on")
                +(state.network().isEmpty()?"":"; this network is "+state.network())),e->{});
        },e->{nodeStarting=false;connection.setText("Offline");failed(e);});
        Object previous=frame.getRootPane().getClientProperty("health");if(previous instanceof javax.swing.Timer old)old.stop();
        javax.swing.Timer health=new javax.swing.Timer(15000,e->{if(!closing)network.submit(Node::attached,count->{if(!closing)connection.setText(connected(count));},error->{});});health.start();frame.getRootPane().putClientProperty("health",health);
    }
    /** The word in the bar: with no relay by choice it is not "offline", it is the way the owner chose. */
    private String connected(int relays){return Node.onlyMine(context)?"Only between your devices":relays>0?"Connected":"Offline — retrying";}
    private String address() throws Exception {
        List<String> all=Node.addresses(context);if(all.isEmpty())throw new IllegalStateException(Node.onlyMine(context)?"This PC is on no network your devices could reach it on.":"No relay connection yet. Try sharing again in a moment.");
        currentAddress=all.get(0);store.myAddress=currentAddress;return currentAddress;
    }
    private void retryAccepting() throws Exception {
        String mine=address();for(NoteStore.Accepting pending:store.waitingToAccept())try{Post.sayAgain(context,store,keys,pending,Node.nameHere(context),mine);store.triedAgain(pending.address);}catch(Exception unavailable){/* Kept for next start. */}
    }
    private void scheduleSync() {
        if(offline||base==null)return;String id=base.id;
        disk.submit(()->store.pauseFor(NoteStore.Branch.Kind.PAGE,id),seconds->{if(seconds>=0){due.put(id,System.currentTimeMillis()+Math.max(1,seconds)*1000L);written.add(id);}},this::failed);
    }
    /** Notes due because they were just written in, whose going is said on the open note's line (see sendDue). */
    private final Set<String> written=new HashSet<>();
    private void sendDue() {
        if(offline||closing)return;
        List<String> ready=new ArrayList<>();long now=System.currentTimeMillis();
        due.forEach((id,at)->{if(at<=now)ready.add(id);});
        // The open note going by itself after it was written in is said on its line too, quietly: the mark alone
        // changed, and nothing said it had gone. Not the tries again a minute apart, which would say the same every minute.
        for(String id:ready){due.remove(id);boolean say=written.remove(id)&&isOpen(id);
            if(say&&!dirty)noteSays(NoteLine.sending(namesShown()),NoteLine.Tone.GOING,null);network.submit(()->{
            if(store.pauseFor(NoteStore.Branch.Kind.PAGE,id)<0)return new Post.Done(0,0,"");
            return Post.send(context,store,keys,NoteStore.Branch.Kind.PAGE,id);
        },done->{if(done.failed>0)due.putIfAbsent(id,System.currentTimeMillis()+60000);if(say&&isOpen(id)&&!dirty)noteSent(done,false);refresh();},
            e->{due.putIfAbsent(id,System.currentTimeMillis()+60000);if(say&&isOpen(id))noteFailed(e);else failed(e);});}
    }
    private void queuePending() {
        if(closing||offline)return;
        disk.submit(()->{
            Map<String,Integer> waiting=new HashMap<>();
            for(Outbox.Wait one:store.owed(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING)) {
                int seconds=store.pauseFor(NoteStore.Branch.Kind.PAGE,one.page);if(seconds>=0)waiting.put(one.page,seconds);
            }return waiting;
        },waiting->{if(closing)return;waiting.forEach((id,seconds)->{if(!(dirty&&base!=null&&base.id.equals(id)))due.putIfAbsent(id,System.currentTimeMillis()+Math.max(1,seconds)*1000L);});},this::failed);
    }
    private void sync(boolean explicit) {
        NoteStore.Branch picked=explicit?target():null;
        // The open note's sync is said on its own line, beside the mark that was clicked; anything wider on the bar.
        boolean onLine=picked!=null&&picked.kind==NoteStore.Branch.Kind.PAGE&&isOpen(picked.id);
        if(offline){if(onLine)noteSays("Not sent - this session was started offline",NoteLine.Tone.FAILED,null);else if(explicit)status.setText("This session was started offline");return;}
        if(onLine)noteSays(NoteLine.sending(namesShown()),NoteLine.Tone.GOING,null);else if(explicit)status.setText("Syncing…");
        NoteStore.Branch.Kind kind=picked!=null?picked.kind:NoteStore.Branch.Kind.LIBRARY;
        String id=picked!=null?picked.id:Sharing.EVERYTHING;
        if(kind!=NoteStore.Branch.Kind.COLLECTION&&kind!=NoteStore.Branch.Kind.BOOK&&kind!=NoteStore.Branch.Kind.PAGE){kind=NoteStore.Branch.Kind.LIBRARY;id=Sharing.EVERYTHING;}
        final NoteStore.Branch.Kind targetKind=kind;final String target=id;
        network.submit(()->{if(explicit)Post.ask(context,store,keys,targetKind,target);return Post.send(context,store,keys,targetKind,target,null,explicit);},done->{
            refresh();if(!explicit)return;
            // No box over the page for the open note: its line says what could not go, and a click on it says why.
            if(onLine){noteSent(done,true);return;}
            status.setText(done.failed>0?Unsent.title(done.sent,done.failed):done.sent>0?"Sent — waiting for delivery confirmation":"No outgoing changes. Asked for updates.");
            if(done.failed>0)tellUnsent(done);
        },e->{if(onLine)noteFailed(e);else failed(e);});
    }
    /**
     * What could not go, said by device and thing, and the one thing to do about it as the box's button - pairing, or
     * How notes travel - where trying again will not put it right. The same words as the phone's (see Unsent).
     */
    void tellUnsent(Post.Done done) {
        Unsent.Problem first=Unsent.first(done.problems);
        Unsent.Fix fix=first==null?Unsent.Fix.NONE:Unsent.fix(first);
        JDialog[] box={null};
        JPanel body=DesktopUi.column();
        DesktopUi.add(body,DesktopUi.note(done.problems.isEmpty()&&!done.why.isEmpty()?done.why:Unsent.words(done.problems,Post.here()),420,DesktopUi.INK,DesktopUi.BODY));
        String button=Unsent.button(first),off=Unsent.takeOff(first);
        // Somebody only listed: linked with, or taken off the list - both from here, rather than hunted for.
        boolean listed=Unsent.listed(first);
        // Only what does something: the cross, Escape or a click beside the box is how it is left alone.
        JPanel foot=button==null?null
            :DesktopUi.footer(DesktopUi.primary(button,()->{box[0].dispose();
                    if(listed)disk.submit(()->store.listing(first.address),rule->link(rule),this::failed);
                    else if(fix==Unsent.Fix.PAIR)people(null);else DesktopSettings.open(this);}));
        if(off!=null) {
            JPanel both=new JPanel(new BorderLayout());both.setOpaque(false);
            both.add(DesktopUi.actions(DesktopUi.button(off,()->{box[0].dispose();
                disk.submit(()->store.listing(first.address),rule->takeOff(first.who,first.listedIn,rule),this::failed);})),BorderLayout.WEST);
            if(foot!=null)both.add(foot,BorderLayout.EAST);
            foot=both;
        }
        box[0]=DesktopUi.sheet(frame,Unsent.title(done.problems,done.sent,done.failed),body,foot,true);
        DesktopUi.show(box[0],520,560);
    }

    /**
     * A round on the line under a title, for somebody the note's list names and this PC is not linked with: who, on
     * what, and the two things to do about it - link with them, or take them off - where the round is.
     */
    void notLinked(SyncStatus.Person who) {
        JDialog[] box={null};
        JPanel body=DesktopUi.column();
        DesktopUi.add(body,DesktopUi.note(who.notLinked(Post.here()),380,DesktopUi.INK,DesktopUi.BODY));
        JButton link=DesktopUi.primary(Unsent.link(who.called()),()->{box[0].dispose();link(who.listing());});
        JPanel foot=new JPanel(new BorderLayout());foot.setOpaque(false);
        if(who.listing()!=null)foot.add(DesktopUi.actions(DesktopUi.button(Unsent.takeOff(who.listedIn()),
            ()->{box[0].dispose();takeOff(who.called(),who.listedIn(),who.listing());})),BorderLayout.WEST);
        foot.add(DesktopUi.footer(link),BorderLayout.EAST);
        box[0]=DesktopUi.sheet(frame,who.called(),body,foot,true);
        box[0].getRootPane().setDefaultButton(link);
        DesktopUi.show(box[0],480,360);
    }

    /** Linking with somebody a thing lists: People and devices for that thing, with Show my code ready to press. */
    void link(Sharing.Rule listing) {
        if(listing==null||listing.scope==Sharing.Scope.LIBRARY){people(null);return;}
        NoteStore.Branch.Kind kind=NoteStore.kindFor(listing.scope);
        disk.submit(()->store.thingName(kind,listing.target),name->people(new NoteStore.Branch(kind,listing.target,"",name,"",0,0,false),true),this::failed);
    }

    /** Somebody taken off a thing's list, after one question; told, as Remove tells, once there is a way to them. */
    void takeOff(String who,String listedIn,Sharing.Rule rule) {
        if(rule==null){status.setText(who+" is no longer on any list here");return;}
        String what=listedIn==null||listedIn.isBlank()?"this":"“"+listedIn+"”";
        if(!DesktopUi.confirm(frame,"Take "+who+" off "+what+"?",who+" stops being listed for "+what+". Nothing is taken from anybody else.","Take them off",true))return;
        status.setText("Updating access…");
        disk.submit(()->{
            if(!store.saysWhoHas(rule.scope,rule.target))throw new IllegalStateException("Only its owner or an admin can take somebody off "+what+".");
            store.decide(rule,Sharing.Level.GONE,System.currentTimeMillis());return null;
        },done->network.submit(()->{Post.changed(context,store,keys,NoteStore.kindFor(rule.scope),rule.target);Post.removedAgain(context,store,keys);return null;},
            v->{status.setText(who+" is off "+what);refresh();},this::failed),this::failed);
    }
    void arrived(Post.Landed landed) {
        if(landed.files){DesktopDrops.heard(this,landed);return;}
        if(!frame.isVisible()&&tray!=null&&landed.said!=null)tray.displayMessage("Mininotes",landed.said,TrayIcon.MessageType.INFO);
        if(landed.accepted!=null){accepted(landed.accepted);return;}
        // A card renamed one of your devices: an open People and devices is drawn again, or it shows the old names.
        if(landed.devices&&peopleBox!=null&&peopleBox.isShowing()){peopleBox.dispose();peopleBox=null;people(null);}
        if(landed.said!=null)status.setText(landed.said);refresh();
        // The open note, written in elsewhere: its page follows, typed words kept (see changedUnderneath).
        changedUnderneath(landed.note);
    }
    /**
     * What Share, Sync now and a code are about: what a menu was opened on; else the note on the screen; else the
     * collection whose card is up. Null on Home with nothing chosen.
     */
    private NoteStore.Branch target() {
        if(selected!=null&&(selected.kind==NoteStore.Branch.Kind.LIBRARY||selected.kind==NoteStore.Branch.Kind.COLLECTION||selected.kind==NoteStore.Branch.Kind.BOOK||selected.kind==NoteStore.Branch.Kind.PAGE))return selected;
        if(onPage&&base!=null)return new NoteStore.Branch(NoteStore.Branch.Kind.PAGE,base.id,base.book,Objects.toString(base.title,"").isBlank()?"this note":base.title,"",0,0,false);
        return home==null?null:home.folder.shown();
    }
    static NoteStore.Branch library(){return new NoteStore.Branch(NoteStore.Branch.Kind.LIBRARY,Sharing.EVERYTHING,"","all notes on this PC","",0,0,true);}
    void share() {
        NoteStore.Branch target=target();
        // Home is not shared from here (decision 27): everything goes to another device of yours from Profile.
        if(target==null||target.kind==NoteStore.Branch.Kind.LIBRARY){status.setToolTipText(null);
            status.setText("Choose a note or a collection to share: open it, or right-click it and choose Share…");return;}
        Sharing.Scope scope=Sharing.Scope.valueOf(target.kind.name());
        disk.submit(()->{
            List<Sharing.Rule> rules=reaching(store,target);
            // Names looked up now, so each person is drawn once, as themselves.
            Map<String,String> names=new HashMap<>();for(Sharing.Rule rule:rules)names.put(rule.address,store.nameFor(rule.address));
            // Whose it is and whom this PC may change, with what it may give each: see Sharing.mayChange and grantable.
            Map<Sharing.Rule,List<Sharing.Level>> changes=new HashMap<>();Set<Sharing.Rule> owners=new HashSet<>();
            for(Sharing.Rule rule:rules){if(store.ownerOf(rule))owners.add(rule);else if(store.mayChange(rule))changes.put(rule,store.mayGive(rule.scope,rule.target));}
            return new Object[]{rules,store.myLevel(scope,target.id),store.cameFrom(target.kind,target.id),store.pauseFor(target.kind,target.id),names,changes,owners,store.owns(scope,target.id)};
        },data->{
            @SuppressWarnings("unchecked") List<Sharing.Rule> rules=(List<Sharing.Rule>)data[0];
            boolean owner=(Boolean)data[7],admin=owner||data[1]==Sharing.Level.ADMIN;
            JDialog[] box={null};
            // Who has access: you first, then everybody else with their role beside them, as Drive does.
            JPanel people=DesktopUi.column();
            JPanel you=inkOnRound(DesktopUi.person(Node.nameHere(context)+" (you)",null),Node.nameHere(context)+" (you)",null);
            @SuppressWarnings("unchecked") Map<Sharing.Rule,List<Sharing.Level>> changes=(Map<Sharing.Rule,List<Sharing.Level>>)data[5];
            @SuppressWarnings("unchecked") Set<Sharing.Rule> owners=(Set<Sharing.Rule>)data[6];
            Sharing.Level mine=(Sharing.Level)data[1];
            JPanel yourLine=DesktopUi.row(you,roleShown(owner?Sharing.OWNER:mine==null?"":mine.words(),owner?Sharing.OWNER_DOES:mine==null?"":mine.does()));people.add(yourLine);
            DesktopMenus.onRightClick(yourLine,e->{JPopupMenu menu=new JPopupMenu();item(menu,"Writing colour",()->inks(roundOf(you),Node.nameHere(context)+" (you)",null));menu.show(e.getComponent(),e.getX(),e.getY());});
            for(Sharing.Rule rule:rules) {
                boolean inherited=!rule.target.equals(target.id);
                // Addresses are never shown in place of a person's name.
                @SuppressWarnings("unchecked") Map<String,String> named=(Map<String,String>)data[4];
                JPanel who=inkOnRound(DesktopUi.person(named.getOrDefault(rule.address,"Paired device"),inherited?(rule.scope==Sharing.Scope.LIBRARY?"Through the library it is in":"Through the collection it is in"):null),
                    named.getOrDefault(rule.address,"Paired device"),rule.address);
                String called=named.getOrDefault(rule.address,"Paired device");
                // Whose it is, said as Owner; a role this PC may change, dropping down to what it may give, each with
                // what it lets them do; any other role said, with what it lets them do under the pointer.
                List<Sharing.Level> give=inherited?null:changes.get(rule);
                java.util.function.Consumer<Sharing.Level> choose=to->{status.setText("Updating access…");disk.submit(()->{
                    store.decide(rule,to,System.currentTimeMillis());return null;
                },done->{box[0].dispose();network.submit(()->{Post.changed(context,store,keys,target.kind,target.id);Post.removedAgain(context,store,keys);return null;},v->{status.setText("Access updated");refresh();},this::failed);},this::failed);};
                JComponent role=owners.contains(rule)?roleShown(Sharing.OWNER,Sharing.OWNER_DOES)
                    :give==null?roleShown(rule.level.words(),inherited?rule.level.does()+" Change this where it was shared.":rule.level.does())
                    :roleButton(rule.level,give,choose,called);
                JPanel right=new JPanel(new GridBagLayout());right.setOpaque(false);right.add(role);
                JPanel line=DesktopUi.row(who,right);people.add(line);
                // Right-click: the same roles, each with what it lets them do, then their writing colour; Remove last.
                DesktopMenus.onRightClick(line,e->roleMenu(give==null?List.of():give,rule.level,give==null?null:choose,roundOf(who),called,rule.address).show(e.getComponent(),e.getX(),e.getY()));
            }
            JButton add=button("Add someone…",()->{box[0].dispose();people(target);});add.setEnabled(admin);
            JButton code=button("Show my code…",()->{box[0].dispose();showCode(target);});code.setEnabled(admin);
            if(!admin)people.add(DesktopUi.quiet("Only the owner or an admin can add people."));
            DesktopUi.gap(people,DesktopUi.S);DesktopUi.add(people,DesktopUi.actions(add,code));

            // Syncing: when changes go, and whether this PC takes what arrives.
            JComboBox<String> delay=new JComboBox<>(new String[]{"Right away","After 3 seconds","After 10 seconds","After 30 seconds","After 2 minutes","When I ask"});
            int[] waits={NoteStore.RIGHT_AWAY,3,10,30,120,NoteStore.WHEN_ASKED};int current=(int)data[3];for(int i=0;i<waits.length;i++)if(waits[i]==current)delay.setSelectedIndex(i);
            delay.setEnabled(scope!=Sharing.Scope.LIBRARY);
            delay.addActionListener(e->{int seconds=waits[delay.getSelectedIndex()];disk.submit(()->{store.setPause(target.kind,target.id,seconds);return null;},done->{if(seconds<0)due.remove(target.id);},this::failed);});
            JCheckBox receiving=DesktopUi.toggle("Receive changes",true);receiving.setEnabled(scope!=Sharing.Scope.LIBRARY);
            disk.submit(()->store.pausedHere(target.kind,target.id),paused->receiving.setSelected(!paused),this::failed);
            receiving.addActionListener(e->{boolean pause=!receiving.isSelected();disk.submit(()->{
                if(pause)for(String peer:store.everybodyIn(target.kind,target.id))store.refuse(peer,target.id,target.kind);
                else store.resume(target.kind,target.id);return null;
            },done->refresh(),this::failed);});
            JPanel wait=new JPanel(new GridBagLayout());wait.setOpaque(false);wait.add(delay);
            JPanel syncing=DesktopUi.column();
            syncing.add(DesktopUi.row(DesktopUi.body("Send my changes"),wait));
            DesktopUi.add(syncing,DesktopUi.note(NoteStore.RIGHT_AWAY_COSTS,380,DesktopUi.QUIET,DesktopUi.BODY.deriveFont(12.5f)));
            syncing.add(DesktopUi.switchRow("Receive their changes",receiving));

            JPanel body=DesktopUi.column();
            DesktopUi.add(body,DesktopUi.card("Who has access",people));DesktopUi.gap(body,12);DesktopUi.add(body,DesktopUi.card("Syncing",syncing));
            JButton leave=((String)data[2]).isEmpty()?null:DesktopUi.danger("Unfollow…",()->{
                if(!DesktopUi.confirm(box[0],"Unfollow?","Stop receiving "+target.name+"? Your copy stays on this PC.","Unfollow",true))return;
                box[0].dispose();status.setText("Unfollowing…");network.submit(()->{Post.leave(context,store,keys,target.kind,target.id);return null;},done->{status.setText("Unfollowed — your copy stays here");refresh();},this::failed);
            });
            JPanel foot=new JPanel(new BorderLayout());foot.setOpaque(false);
            if(leave!=null)foot.add(DesktopUi.actions(leave),BorderLayout.WEST);
            foot.add(DesktopUi.footer(button("Sync now",()->{box[0].dispose();sync(true);})),BorderLayout.EAST);
            box[0]=DesktopUi.sheet(frame,"Share “"+target.name+"”",DesktopUi.scrolling(body),foot,true);
            DesktopUi.show(box[0],520,720);
        },this::failed);
    }
    /**
     * Every rule that reaches a thing, as its Share box lists them: on everything, on itself, or on any collection above
     * it however deep, at whatever level the rule was written - a 0.1 collection's or book's, or a THING (see
     * Sharing.covers). On the disk thread.
     */
    static List<Sharing.Rule> reaching(NoteStore store,NoteStore.Branch target) {
        List<String> path=target.kind==NoteStore.Branch.Kind.LIBRARY?List.of():store.pathOf(target.id);
        List<Sharing.Rule> rules=new ArrayList<>();
        for(Sharing.Rule rule:store.shares())if(Sharing.covers(rule,path))rules.add(rule);
        return rules;
    }
    /**
     * A person's line in Share, right-clicked: the roles this PC may give them, each over what it lets them do and the
     * one they have ticked; their writing colour where there is a round to colour; and Remove, last. Where this PC may
     * change nothing about them, their role alone, said, and nothing to choose.
     *
     * @param choose what a role picked does, GONE for Remove; null where nothing may be changed here
     */
    private JPopupMenu roleMenu(List<Sharing.Level> give,Sharing.Level now,java.util.function.Consumer<Sharing.Level> choose,Component round,String name,String address) {
        JPopupMenu menu=new JPopupMenu();
        List<Sharing.Level> shown=new ArrayList<>(give);
        if(!shown.contains(now)){shown.add(now);shown.sort((a,b)->b.ordinal()-a.ordinal());}
        for(Sharing.Level level:shown) {
            JRadioButtonMenuItem one=new JRadioButtonMenuItem(DesktopUi.roleLine(level.words(),level.does()),level==now);
            one.setEnabled(choose!=null&&(give.contains(level)||level==now));one.getAccessibleContext().setAccessibleName(level.words()+". "+level.does());
            one.addActionListener(a->{if(level!=now)choose.accept(level);});menu.add(one);
        }
        if(round!=null){menu.addSeparator();item(menu,"Writing colour",()->inks(round,name,address));}
        if(choose!=null){menu.addSeparator();JMenuItem remove=new JMenuItem("Remove");remove.addActionListener(a->{
            if(DesktopUi.confirm(frame,"Remove "+name+"?","They stop getting changes. Their copy stays on their device, and they are told.","Remove",true))choose.accept(Sharing.Level.GONE);});menu.add(remove);}
        return menu;
    }
    /** A role that may be changed here: its name and a ▾, dropping down to the roles this PC may give. */
    private JButton roleButton(Sharing.Level now,List<Sharing.Level> give,java.util.function.Consumer<Sharing.Level> choose,String name) {
        JButton role=new JButton(now.words()+"  ▾");role.setFocusPainted(false);role.setToolTipText(now.does());
        role.getAccessibleContext().setAccessibleName("What "+name+" may do: "+now.words()+". "+now.does());
        role.addActionListener(e->roleMenu(give,now,choose,null,name,null).show(role,0,role.getHeight()));
        return role;
    }
    /** A role that is not changed here, said quietly, with what it lets them do under the pointer. */
    private static JComponent roleShown(String role,String does) {
        DesktopUi.Text said=DesktopUi.quiet(role);said.setToolTipText(does.isEmpty()?null:does);
        said.getAccessibleContext().setAccessibleName(does.isEmpty()?role:role+". "+does);
        return said;
    }
    void showCode(NoteStore.Branch target) {
        if(offline){status.setText("This session was started offline");return;}
        Sharing.Scope scope=Sharing.Scope.valueOf(target.kind.name());
        disk.submit(()->store.mayGive(scope,target.id),may->{
            if(may.isEmpty()){status.setText("Only its owner or an admin can share "+target.name);return;}
            Sharing.Level role=offerRole(target.name,may);if(role!=null)showCode(target,scope,role);
        },this::failed);
    }
    /** What the code will let them do: the roles this PC may give, each over what it lets them do. Null if none was chosen. */
    Sharing.Level offerRole(String name,List<Sharing.Level> may) {
        return DesktopUi.chooseRole(frame,"Share “"+name+"”","What may they do with it?",may,
            may.contains(Sharing.Level.WRITE)?Sharing.Level.WRITE:may.get(0),"Show my code");
    }
    /**
     * Somebody on another network while notes go only between the owner's devices, which reach nothing beyond this Wi-Fi:
     * said so, with helpers offered there and then, and what was being done done again with them (the owner, 2026-10-02:
     * "make sure we can share with everybody"). Not now, and it is kept.
     */
    void helpersFor(String name,Runnable then) {
        JPanel body=DesktopUi.column();
        DesktopUi.add(body,DesktopUi.note("This PC sends notes only between your devices, on the same network, so it cannot reach "+name
            +". Helpers can: relays that pass sealed notes on, which they cannot read. Their device needs them too, if it has them off."));
        DesktopUi.gap(body,DesktopUi.S);DesktopUi.add(body,DesktopUi.quiet("This can be changed back in Settings, How notes travel."));
        boolean[] yes={false};JDialog[] box={null};
        JButton use=DesktopUi.primary("Use helpers",()->{yes[0]=true;box[0].dispose();});
        box[0]=DesktopUi.sheet(frame,name+" is on another network",body,DesktopUi.footer(use),true);
        DesktopUi.show(box[0],440,320);
        if(!yes[0]){status.setText("Saved "+name+". This PC tells them again at each start.");return;}
        status.setText("Connecting to your relays…");
        connectivity.submit(()->{Node.onlyMine(context,false);return null;},
            done->{status.setText("Helpers carry notes when needed now");then.run();},
            failure->status.setText("That could not be changed."));
    }
    private void showCode(NoteStore.Branch target,Sharing.Scope scope,Sharing.Level role) {
        status.setText("Preparing sharing code…");
        network.submit(()->keys.line(Node.nameHere(context),address(),Sharing.travelling(scope,target.name),role,scope.name(),target.id),line->{
            offers.put(scope.name()+":"+target.id,role);offeredAt.put(scope.name()+":"+target.id,System.currentTimeMillis());status.setText("Scan this code with Mininotes on your phone");
            try {
                BitMatrix matrix=new MultiFormatWriter().encode(Pairing.link(line),BarcodeFormat.QR_CODE,340,340,Map.of(EncodeHintType.MARGIN,2));
                BufferedImage image=new BufferedImage(matrix.getWidth(),matrix.getHeight(),BufferedImage.TYPE_INT_RGB);
                for(int y=0;y<matrix.getHeight();y++)for(int x=0;x<matrix.getWidth();x++)image.setRGB(x,y,matrix.get(x,y)?0xff17261b:0xffffffff);
                JLabel picture=new JLabel(new ImageIcon(image));picture.setAlignmentX(Component.CENTER_ALIGNMENT);
                JPanel body=DesktopUi.column();
                DesktopUi.Text how=DesktopUi.note("On the phone, open Mininotes and scan this code. They get "+target.name+" as "+role.words()+": "+role.does());
                DesktopUi.add(body,how);DesktopUi.gap(body,DesktopUi.M);
                JPanel framed=new JPanel(new GridBagLayout());framed.setOpaque(false);framed.add(DesktopUi.card(null,picture));DesktopUi.add(body,framed);
                DesktopUi.Text copied=DesktopUi.quiet(" ");
                JButton link=button("Copy link",()->{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(Pairing.link(line)),null);copied.setText("Link copied. Send it to them another way if they cannot scan.");});
                DesktopUi.gap(body,DesktopUi.M);DesktopUi.add(body,DesktopUi.actions(link));DesktopUi.gap(body,DesktopUi.S);DesktopUi.add(body,copied);
                // Notes only between the owner's devices: the code carries no relay, so only a device on this network can use it.
                if(Node.onlyMine(context)) {
                    DesktopUi.gap(body,DesktopUi.M);DesktopUi.add(body,DesktopUi.quiet("This code works only for devices on this network: this PC sends notes only between your devices."));
                    JButton anywhere=button("Use helpers, so anybody anywhere can use it",()->{
                        Window up=SwingUtilities.getWindowAncestor(body);if(up!=null)up.dispose();
                        status.setText("Connecting to your relays…");
                        connectivity.submit(()->{Node.onlyMine(context,false);return null;},
                            done->{status.setText("Helpers carry notes when needed now");showCode(target,scope,role);},
                            failure->status.setText("That could not be changed."));
                    });
                    DesktopUi.gap(body,DesktopUi.S);DesktopUi.add(body,DesktopUi.actions(anywhere));
                }
                DesktopUi.tell(frame,"Share “"+target.name+"”",body);
            } catch(Exception e){failed(e);}
        },this::failed);
    }    void scanCode(NoteStore.Branch shareTarget) {
        if(offline){status.setText("This session was started offline");return;}
        DesktopScanner.open(frame,text->receiveCode(text,shareTarget));
    }
    private void receiveCode(DesktopScanner.Code code,NoteStore.Branch shareTarget) {
        try {
            Pairing.Said said=Pairing.read(Pairing.line(code.text().trim()));
            disk.submit(()->code.camera()?"":Envelope.code(keys.signing().getPublic(),Keys.publicKey(said.signing)),digits->{
            JPanel body=DesktopUi.column();
            DesktopUi.add(body,DesktopUi.person(said.name,said.offer.isEmpty()?"Wants to pair with this PC":said.offer+"  ·  "+said.level.words()));
            // Wherever the + was that took it, what is shared arrives on Home: said before it is accepted, as on the phone.
            if(!said.offer.isEmpty()){DesktopUi.gap(body,DesktopUi.S);DesktopUi.add(body,DesktopUi.quiet("It will appear on Home when it arrives."));}
            if(!said.offer.isEmpty()){DesktopUi.gap(body,DesktopUi.S);DesktopUi.add(body,DesktopUi.note(said.level.does()));}
            if(!digits.isEmpty()) {
                // A code read from a picture or a link, not a camera: taken all the same (the owner, 2026-10-02: asking for
                // digits the other device never showed was a dead end), with what a paste cannot prove said, and how to check.
                DesktopUi.gap(body,DesktopUi.M);DesktopUi.add(body,DesktopUi.note("Pasted, not scanned. To be sure nobody changed it on the way, open People on both devices afterwards: each shows six digits for the other, and they must be the same. Here:"));
                DesktopUi.gap(body,DesktopUi.S);JLabel shown=new JLabel(digits);shown.setFont(DesktopUi.BODY.deriveFont(Font.BOLD,28f));shown.setForeground(INK);DesktopUi.add(body,shown);
            }
            boolean[] yes={false};JDialog[] box={null};
            JButton accept=DesktopUi.primary(said.offer.isEmpty()?"Pair":"Accept",()->{yes[0]=true;box[0].dispose();});
            box[0]=DesktopUi.sheet(frame,said.offer.isEmpty()?"Pair with "+said.name+"?":"Accept from "+said.name+"?",body,DesktopUi.footer(accept),true);
            DesktopUi.show(box[0],440,480);if(!yes[0])return;
            boolean[] elsewhere={false};
            status.setText("Pairing with "+said.name+"…");network.submit(()->{
                // Only "Connect my other device" - a code offering everything - is one of the owner's own devices.
                boolean own=Sharing.Scope.LIBRARY.name().equals(said.scope);
                store.pairedWith(said.address,said.name,own,said.agreement,said.signing);
                if(own)store.setMine(said.address,true);
                // Only between the owner's devices, a code from another network is saved and said plainly.
                try{String key=Node.introduce(context,said.address);if(!key.isEmpty())store.knownAs(said.address,key);}catch(Exception unreachable){elsewhere[0]=Node.ON_THE_SAME_WIFI.equals(unreachable.getMessage());/* The code's address remains usable. */}
                // A plain code too: the device that showed it has to hear about this PC, or it drops as a
                // stranger's everything this PC shares with it. Kept, and said again at each start until answered.
                store.accepting(said.address,said.name,said.scope,said.target,said.level);
                // An earlier copy of it put in the bin or the archive here comes back out: accepting is wanting it.
                if(!said.target.isEmpty())store.acceptedBack(said.address,said.target);
                try{Post.accept(context,keys,said,Node.nameHere(context),address());}catch(Exception notNow){return false;}
                return true;
            },told->{if(elsewhere[0]){refresh();helpersFor(said.name,()->receiveCode(code,shareTarget));return;}status.setText(said.target.isEmpty()?(told?"Paired. "+said.name+" is asked to pair back":"Paired. "+said.name+" is told when it is next reachable"):"Paired — waiting for "+said.name+" to approve and send");refresh();if(shareTarget!=null)people(shareTarget);},e->{status.setText("Pairing has not finished. Reopen the app to retry, or try the link again.");});
            },this::failed);
        } catch(Exception e){failed(e);}
    }
    private void accepted(Hello.Said said) {
        if(said.target.isEmpty()){pairBack(said);return;}
        // A build from before quotes back the scope the code gave it, with the level after it: see Pairing.scopeIn.
        String bare=Pairing.scopeIn(said.scope);
        String key=bare+":"+said.target;Sharing.Level offered=offers.get(key);
        // A reply cannot upgrade its offer, refer to an unoffered item, or bypass consent. What it claims is given:
        // Can write from a build that read an Admin offer as that, never more than was offered.
        if(offered==null||said.level.ordinal()>offered.ordinal()||!pendingOffers.add(key+said.address))return;
        Sharing.Level level=said.level;
        if(!frame.isVisible()){DesktopLock.quietUnlessLet(frame);frame.setVisible(true);Node.near(true);if(DesktopLock.front.mayTake())frame.toFront();}
        // Showing the code was the decision: scanned within a quarter of an hour, it is handed over without asking again.
        Long shown=offeredAt.get(key);long age=shown==null?Long.MAX_VALUE:System.currentTimeMillis()-shown;
        boolean give=age>=0&&age<15*60_000L||DesktopUi.confirm(frame,said.name+" scanned your code",said.name+" accepted what you offered. Give them "+level.words()+"? "+level.does(),"Give access",false);
        pendingOffers.remove(key+said.address);if(!give)return;
        Sharing.Scope scope=Sharing.Scope.valueOf(bare);NoteStore.Branch.Kind kind=NoteStore.kindFor(scope);
        status.setText("Sharing with "+said.name+"…");network.submit(()->{
            if(!store.mayGive(scope,said.target).contains(level))throw new IllegalStateException("Only its owner or an admin can share this");
            store.pairedWith(said.address,said.name,false,said.agreement,said.signing);
            // Everything, accepted: another device of the owner's, marked as theirs here (see Persons).
            if(scope==Sharing.Scope.LIBRARY)Post.bonded(context,store,said);
            try{String contact=Node.introduce(context,said.address);if(!contact.isEmpty())store.knownAs(said.address,contact);}catch(Exception unreachable){/* Fallback to the current address. */}
            store.give(scope,said.target,said.address,level,null);
            return Post.send(context,store,keys,kind,said.target);
        },done->{
            // The open note given to them: how its sending went is said on its line, as any other send of it is.
            if(isOpen(said.target)){status.setText("Shared with "+said.name);noteSent(done,false);refresh();return;}
            status.setText(done.failed>0?"Shared — waiting to send":"Sent — waiting for delivery confirmation");refresh();if(done.failed>0)tellUnsent(done);},this::failed);
    }
    /** Somebody scanned this PC's code with nothing offered: asked, then saved and answered. */
    private void pairBack(Hello.Said said) {
        if(!pendingOffers.add("pair:"+said.address))return;
        // Somebody scanned the code this PC showed: showing it was the decision, so they are paired back without a question.
        pendingOffers.remove("pair:"+said.address);
        status.setText("Pairing with "+said.name+"…");network.submit(()->{
            store.pairedWith(said.address,said.name,false,said.agreement,said.signing);
            try{String contact=Node.introduce(context,said.address);if(!contact.isEmpty())store.knownAs(said.address,contact);}catch(Exception unreachable){/* the address they sent still works */}
            NoteStore.Contact saved=store.address(said.address);
            try{Post.helloBack(context,keys,said,Node.nameHere(context),address(),saved==null?null:saved.contact);}catch(Exception notNow){/* they say hello again until they hear */}
            return null;
        },done->{status.setText(said.name+" paired with this PC");if(tray!=null&&!frame.isVisible())tray.displayMessage("Mininotes",said.name+" paired with this PC",TrayIcon.MessageType.INFO);refresh();},this::failed);
    }
    /** When somebody last typed or clicked in Mininotes; the notebook locks again after the time chosen in Security. */
    private volatile long lastUse=System.currentTimeMillis();
    private final AWTEventListener using=e->lastUse=System.currentTimeMillis();
    private javax.swing.Timer idle;
    private boolean relocking;
    /** What closing the unlock window after a re-lock does: leaves Mininotes. Tests put something quieter in. */
    Runnable leave=()->System.exit(0);

    private void relockIfIdle() {
        // Recording is using it, with no key pressed: a lecture should not lock the notebook halfway through.
        if(closing||relocking||recorder!=null&&recorder.recording())return;
        java.nio.file.Path folder=context.getFilesDir().toPath();
        int minutes=DesktopLock.minutes(context);
        if(minutes<=0||!DesktopLock.locked(folder))return;
        if(System.currentTimeMillis()-lastUse<minutes*60_000L)return;
        relock();
    }

    /**
     * Locked again: writing saved, the notebook closed and its key let go, and the password asked for as at
     * start. What arrives meanwhile waits sealed in the inbox, and is taken in when it is opened.
     */
    void relock() {
        relocking=true;
        java.nio.file.Path folder=context.getFilesDir().toPath();boolean wasOffline=offline;
        // Locked again while the window was closed to the tray: the question waits until Mininotes is opened
        // again. Asked at once, Windows Hello came up over whatever the owner was doing, with the taskbar
        // flashing, for a window they had put away.
        boolean away=!frame.isVisible()&&tray!=null;
        save(()->{
            if(!wasOffline)DesktopFiles.toInbox(folder);
            // Said to the other devices while the notebook can still say who they are: what they send now waits sealed,
            // unanswered, and their marks say this PC is locked rather than waiting amber for an answer that cannot come.
            if(!wasOffline)Post.sayLocked(context,store,keys,true);
            Runnable ask=()->{
                try {
                    // Hello is asked without a click only when this waited for Mininotes to be opened again: locked
                    // for want of use, nobody is there to answer it, and a question nobody sees is only in the way. The
                    // box then comes up without asking for the front too: the window it replaces has just closed.
                    if(DesktopLock.askAtStart(folder,away,opener(folder,wasOffline))==null)leave.run();
                } catch(Exception e){DesktopLock.tell("Mininotes could not open",e.getMessage());System.exit(1);}
            };
            shutdown(false,away?()->askLater=ask:ask);
        });
    }
    /** The question for the password, kept for when Mininotes is opened again after locking while put away. */
    private static volatile Runnable askLater;

    void shutdown(boolean exit){shutdown(exit,null);}
    void shutdown(boolean exit,Runnable after) {
        // A recording going on is kept first: queued on the disk ahead of the notebook closing, and sealed with its key.
        if(recorder!=null)recorder.finish();
        closing=true;if(current==this)current=null;
        // A newer version downloaded and waiting takes this one's place as Mininotes goes.
        if(exit)updateOnExit();if(idle!=null)idle.stop();Toolkit.getDefaultToolkit().removeAWTEventListener(using);autosave.stop();syncLater.stop();Object timer=frame.getRootPane().getClientProperty("health");if(timer instanceof javax.swing.Timer health)health.stop();
        frame.setEnabled(false);status.setText("Closing…");
        Node.everyBeat(null);
        disk.submit(()->{if(!exit){store.close();instanceLock.release();instanceChannel.close();}return null;},done->{
            if(tray!=null)SystemTray.getSystemTray().remove(tray);
            for(Window owned:frame.getOwnedWindows())owned.dispose();frame.dispose();disk.abandon();network.abandon();connectivity.abandon();
            context.unlock(null);
            if(exit)System.exit(0);
            if(after!=null)SwingUtilities.invokeLater(after);
        },e->{closing=false;frame.setEnabled(true);failed(e);});
    }
    /** One thing shared with one person, as People and devices lists it: what, what it is, and whether it can be changed here. */
    private record Held(Sharing.Rule rule,String name,String kind,boolean mayChange,boolean owner,List<Sharing.Level> give){}

    /** Everything each person has, by address: read once, off the event thread, when the box opens. */
    private java.util.Map<String,java.util.List<Held>> heldByPerson() {
        java.util.Map<String,java.util.List<Held>> by=new java.util.HashMap<>();
        for(Sharing.Rule rule:store.shares()) {
            if(rule.level==Sharing.Level.GONE)continue;
            String name,kind;
            switch(rule.scope) {
                // A book was a collection inside a collection, and a rule made since collections nest says THING.
                case COLLECTION: case BOOK: case THING: name=store.nameOf(rule.target,true);kind="Collection";break;
                case PAGE: {NoteStore.Note n=store.get(rule.target);name=n==null?"":n.title;kind="Note";break;}
                default: name="Everything";kind="All collections";
            }
            if(name==null||name.isBlank())name=rule.scope==Sharing.Scope.PAGE?"Untitled note":"Untitled";
            by.computeIfAbsent(rule.address,a->new java.util.ArrayList<>()).add(new Held(rule,name,kind,store.mayChange(rule),store.ownerOf(rule),store.mayGive(rule.scope,rule.target)));
        }
        return by;
    }

    void people(NoteStore.Branch target){people(target,false);}
    /** People and devices as last opened, drawn again when a card changes what your devices are called. */
    private JDialog peopleBox;
    /** @param codeFirst Show my code… ready to press when the box opens, for linking with somebody already listed */
    void people(NoteStore.Branch target,boolean codeFirst) {
        disk.submit(()->{
            java.util.List<NoteStore.Contact> all=store.addresses();
            // Under a device only one end counts as the owner's, what stops names travelling and the switch that mends it.
            java.util.Map<String,String> sided=new java.util.HashMap<>();
            for(NoteStore.Contact one:all){String line=Post.oneSided(context,one);if(line!=null)sided.put(one.address,line);}
            // The six digits from this PC's key and theirs: the same on their screen under People, if nothing came between
            // the two - what a code pasted rather than scanned is checked by.
            java.util.Map<String,String> checks=new java.util.HashMap<>();
            for(NoteStore.Contact one:all)if(one.signing.length>0)
                try{checks.put(one.address,Envelope.code(keys.signing().getPublic(),Keys.publicKey(one.signing)));}catch(Exception unreadable){/* no digits for it */}
            return new Object[]{all,target==null?heldByPerson():java.util.Map.of(),store.linkedLines(),sided,
                target==null?List.<Sharing.Level>of():store.mayGive(Sharing.Scope.valueOf(target.kind.name()),target.id),checks};
        },loaded->{
            @SuppressWarnings("unchecked") java.util.List<NoteStore.Contact> contacts=(java.util.List<NoteStore.Contact>)loaded[0];
            @SuppressWarnings("unchecked") java.util.Map<String,java.util.List<Held>> held=(java.util.Map<String,java.util.List<Held>>)loaded[1];
            // Under a device never scanned here, what it was linked through (see Linking).
            @SuppressWarnings("unchecked") java.util.Map<String,String> through=(java.util.Map<String,String>)loaded[2];
            @SuppressWarnings("unchecked") java.util.Map<String,String> sided=(java.util.Map<String,String>)loaded[3];
            @SuppressWarnings("unchecked") List<Sharing.Level> mayGive=(List<Sharing.Level>)loaded[4];
            @SuppressWarnings("unchecked") java.util.Map<String,String> checks=(java.util.Map<String,String>)loaded[5];
            java.util.function.Supplier<List<Sharing.Level>> giving=()->mayGive;
            // Two parts: your own devices, by what you call each, this PC first; then people, by the names they chose.
            JDialog[] box={null};JPanel own=DesktopUi.column(),others=DesktopUi.column();
            if(target==null)own.add(DesktopUi.row(DesktopUi.person(Node.deviceHere(context)+" (this one)",null),null));
            boolean anybody=false;for(NoteStore.Contact one:contacts)if(!one.mine)anybody=true;
            if(!anybody)DesktopUi.add(others,DesktopUi.note(contacts.isEmpty()?"No devices paired yet. Scan another device's code, or show them yours.":"Nobody else yet. Share something to add somebody."));
            for(NoteStore.Contact contact:contacts) {
                JPanel list=contact.mine?own:others;
                JComponent right;AbstractButton[] offered;
                if(target!=null) {
                    JButton give=button("Share…",()->{
                        Sharing.Scope scope=Sharing.Scope.valueOf(target.kind.name());
                        List<Sharing.Level> may=giving.get();
                        if(may.isEmpty()){status.setText("Only its owner or an admin can share "+target.name);return;}
                        Sharing.Level chosen=DesktopUi.chooseRole(box[0],"Share “"+target.name+"”","What may "+contact.name+" do with it?",may,
                            may.contains(Sharing.Level.WRITE)?Sharing.Level.WRITE:may.get(0),"Share");if(chosen==null)return;
                        status.setText("Sharing with "+contact.name+"…");
                        disk.submit(()->{store.give(scope,target.id,contact.address,chosen,null);return null;},done->{box[0].dispose();network.submit(()->Post.changed(context,store,keys,target.kind,target.id),sent->{status.setText("Access saved — waiting for delivery confirmation");refresh();},this::failed);},this::failed);
                    });give.setEnabled(contact.paired());right=give;offered=new AbstractButton[]{give};
                }else {
                    JCheckBox mine=DesktopUi.toggle("My device",contact.mine);mine.addActionListener(e->{boolean yes=mine.isSelected();disk.submit(()->{store.setMine(contact.address,yes);return null;},done->{box[0].dispose();people(null);},this::failed);});
                    JButton address=button("Address",()->{
                        // The whole address, wrapped and selectable, its code beside it, and one button to copy it.
                        JPanel shown=DesktopUi.column();
                        DesktopUi.Text text=DesktopUi.note(contact.address,300,INK,new Font("Consolas",Font.PLAIN,12));
                        try{JLabel code=new JLabel(new ImageIcon(DesktopQr.draw(contact.address,220)));code.getAccessibleContext().setAccessibleName("The address of "+contact.name+" as a QR code");
                            JPanel side=new JPanel(new BorderLayout(DesktopUi.M,0));side.setOpaque(false);side.add(DesktopUi.card(null,code),BorderLayout.WEST);side.add(text);DesktopUi.add(shown,side);}
                        catch(Exception noCode){DesktopUi.add(shown,text);}
                        DesktopUi.gap(shown,DesktopUi.M);DesktopUi.Text copied=DesktopUi.quiet(" ");
                        DesktopUi.add(shown,DesktopUi.actions(DesktopUi.button("Copy the address",()->{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(contact.address),null);copied.setText("Copied.");})));
                        DesktopUi.gap(shown,4);DesktopUi.add(shown,copied);
                        DesktopUi.tell(box[0],contact.name,shown);});
                    // Forget, as the phone's long press does: off the list you pick from, nothing unshared.
                    JButton forget=button("Forget",()->{
                        if(!DesktopUi.confirm(box[0],"Forget this device?",contact.name+"\n\nAnything already shared with it stays shared; this only removes it from the list you pick from.","Forget",true))return;
                        disk.submit(()->{store.removeAddress(contact.address);return null;},done->{box[0].dispose();status.setText("Forgotten");people(null);},this::failed);});
                    forget.getAccessibleContext().setAccessibleName("Forget "+contact.name);
                    JPanel both=DesktopUi.actions(DesktopUi.quiet("My device"),mine,address,forget);right=both;offered=new AbstractButton[]{mine,address,null,forget};
                }
                JPanel holder=new JPanel(new GridBagLayout());holder.setOpaque(false);holder.add(right);
                JPanel person=DesktopUi.row(DesktopUi.person(contact.name,contact.paired()?through.get(contact.address):"Not paired yet"),holder);
                // A right-click on the person offers what the row's own buttons do.
                DesktopMenus.echoOnRightClick(person,offered);list.add(person);
                if(target!=null)continue;
                if(sided.containsKey(contact.address)){JPanel why=DesktopUi.column();why.setBorder(BorderFactory.createEmptyBorder(0,44,DesktopUi.S,0));DesktopUi.add(why,DesktopUi.quiet(sided.get(contact.address)));list.add(why);}
                if(checks.containsKey(contact.address)){JPanel check=DesktopUi.column();check.setBorder(BorderFactory.createEmptyBorder(0,44,DesktopUi.S,0));DesktopUi.add(check,DesktopUi.quiet("Check with them: "+checks.get(contact.address)));list.add(check);}
                // What they have, thing by thing, each with its role - changed here, or taken away.
                java.util.List<Held> theirs=held.getOrDefault(contact.address,java.util.List.of());
                if(theirs.isEmpty()){JPanel none=DesktopUi.column();none.setBorder(BorderFactory.createEmptyBorder(0,44,DesktopUi.S,0));DesktopUi.add(none,DesktopUi.quiet("Nothing shared with "+contact.name+" yet."));list.add(none);continue;}
                for(Held one:theirs) {
                    NoteStore.Branch.Kind kind=NoteStore.kindFor(one.rule().scope);
                    java.util.function.Consumer<Sharing.Level> choose=to->{
                        status.setText("Updating access…");
                        disk.submit(()->{store.decide(one.rule(),to,System.currentTimeMillis());return null;
                        },done->{box[0].dispose();people(null);
                            network.submit(()->{Post.changed(context,store,keys,kind,one.rule().target);Post.removedAgain(context,store,keys);return null;},v->{status.setText("Access updated");refresh();},this::failed);},this::failed);};
                    // Whose it is, said as Owner; a role this PC may change, dropping down to what it may give; any other said.
                    JComponent role=one.owner()?roleShown(Sharing.OWNER,Sharing.OWNER_DOES)
                        :one.mayChange()?roleButton(one.rule().level,one.give(),choose,contact.name)
                        :roleShown(one.rule().level.words(),one.rule().level.does()+" Only its owner or an admin can change this.");
                    JLabel what=new JLabel(one.name()+"   ·   "+one.kind());what.setFont(DesktopUi.BODY);what.setForeground(INK);
                    JPanel roleHolder=new JPanel(new GridBagLayout());roleHolder.setOpaque(false);roleHolder.add(role);
                    JPanel line=DesktopUi.row(what,roleHolder);line.setBorder(BorderFactory.createEmptyBorder(0,44,0,0));
                    if(!one.owner())DesktopMenus.onRightClick(line,e->roleMenu(one.mayChange()?one.give():List.of(),one.rule().level,one.mayChange()?choose:null,null,contact.name,null).show(e.getComponent(),e.getX(),e.getY()));
                    list.add(line);
                }
            }
            JPanel body=DesktopUi.column();
            if(own.getComponentCount()>0){DesktopUi.add(body,DesktopUi.card("My devices",own));DesktopUi.gap(body,12);}
            DesktopUi.add(body,DesktopUi.card("People",others));
            JPanel foot=new JPanel(new BorderLayout());foot.setOpaque(false);
            JButton code=target!=null?button("Show my code…",()->{box[0].dispose();showCode(target);}):null;
            foot.add(DesktopUi.actions(button("Scan a code or paste a link…",()->{box[0].dispose();scanCode(target);}),code),BorderLayout.WEST);
            box[0]=DesktopUi.sheet(frame,target==null?"People and devices":"Add someone to “"+target.name+"”",DesktopUi.scrolling(body),foot,true);
            if(target==null)peopleBox=box[0];
            if(codeFirst&&code!=null)box[0].getRootPane().putClientProperty("focus",code);
            DesktopUi.show(box[0],600,640);
        },this::failed);
    }    void setTrayListening(boolean enabled,javax.swing.text.JTextComponent outcome) {
        if(enabled&&!ensureTray()){outcome.setText("Could not add Mininotes to the system tray.");return;}
        disk.submit(()->{context.getSharedPreferences("settings",0).edit().putString("listenInTray",Boolean.toString(enabled)).apply();return null;},done->{listenInTray=enabled;outcome.setText(enabled?"Closing the window keeps Mininotes listening in the tray.":"Closing the window exits Mininotes.");if(!enabled&&tray!=null){SystemTray.getSystemTray().remove(tray);tray=null;}},this::failed);
    }
    private boolean ensureTray() {
        if(offline||!SystemTray.isSupported())return false;if(tray!=null)return true;
        BufferedImage icon=DesktopIcon.image(32);
        PopupMenu menu=new PopupMenu();MenuItem open=new MenuItem("Open Mininotes"),quit=new MenuItem("Exit Mininotes");menu.add(open);menu.add(quit);
        Runnable reveal=()->{frame.setVisible(true);Node.near(true);frame.setState(Frame.NORMAL);frame.toFront();};open.addActionListener(e->SwingUtilities.invokeLater(reveal));quit.addActionListener(e->SwingUtilities.invokeLater(()->save(()->shutdown(true))));
        tray=new TrayIcon(icon,"Mininotes — listening",menu);tray.setImageAutoSize(true);tray.addActionListener(e->SwingUtilities.invokeLater(reveal));
        try{SystemTray.getSystemTray().add(tray);return true;}catch(AWTException e){tray=null;return false;}
    }
    /** Connected, connecting or not: a coloured dot says which before the words are read. */
    static final class Dot extends JLabel {
        Dot(String text){super();setText(text);}
        @Override public void setText(String text) {
            super.setText(text);
            Color colour=text==null?QUIET:text.startsWith("Connected")?new Color(64,145,94):text.startsWith("Connecting")||text.contains("retrying")?new Color(207,150,48):new Color(168,165,156);
            setIcon(new Icon(){
                public int getIconWidth(){return 8;}public int getIconHeight(){return 8;}
                public void paintIcon(Component c,Graphics g0,int x,int y){Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(colour);g.fillOval(x,y,8,8);g.dispose();}
            });
        }
    }
    static final class Paper extends JTextArea {
        static final Color RULES=new Color(222,226,215);
        /** The ruled lines: washed in the note's colour, as the phone washes them. */
        Color rules=RULES;
        /** Told when an address could not be opened: nothing on this PC took it. */
        java.util.function.Consumer<Exception> cannotOpen=e->{};
        /** The web and mail addresses in the words (see Links), found again when the words change. */
        private volatile List<Links.Link> links;
        List<Links.Link> links(){List<Links.Link> now=links;if(now==null)links=now=Links.find(getText());return now;}

        /**
         * A click on an address opens it, as a tap does on the phone; a click anywhere else only puts the caret there,
         * and a drag selects, over an address or not - so it opens only on a press and a release in the same place.
         */
        Paper() {
            DocumentListener changed=new DocumentListener(){
                public void insertUpdate(DocumentEvent e){links=null;edited(e.getOffset(),0,e.getLength());}
                public void removeUpdate(DocumentEvent e){links=null;edited(e.getOffset(),e.getLength(),0);}
                public void changedUpdate(DocumentEvent e){}
            };
            getDocument().addDocumentListener(changed);
            addPropertyChangeListener("document",e->{
                if(e.getOldValue() instanceof javax.swing.text.Document was)was.removeDocumentListener(changed);
                if(e.getNewValue() instanceof javax.swing.text.Document now)now.addDocumentListener(changed);
                links=null;
            });
            MouseAdapter pointer=new MouseAdapter(){
                java.awt.Point pressed;Links.Link on;
                @Override public void mouseMoved(MouseEvent e) {
                    setCursor(Cursor.getPredefinedCursor(linkAt(e.getPoint())!=null?Cursor.HAND_CURSOR:Cursor.TEXT_CURSOR));
                }
                @Override public void mousePressed(MouseEvent e){pressed=e.getPoint();on=SwingUtilities.isLeftMouseButton(e)&&!e.isShiftDown()?linkAt(pressed):null;}
                @Override public void mouseReleased(MouseEvent e) {
                    Links.Link was=on;on=null;
                    if(was==null||pressed==null||pressed.distance(e.getPoint())>4||!was.equals(linkAt(e.getPoint())))return;
                    open(was.target());
                }
            };
            addMouseListener(pointer);addMouseMotionListener(pointer);
        }

        // ---- who wrote what (see Writers) ------------------------------------------------------------------------

        /** Who wrote each letter on the page: always as long as the page's text. */
        private Writers runs=Writers.none();
        /** Who wrote what just before the last edit, for Ctrl+Z to put back with the letters (see remembered). */
        private Writers before;
        private Writers.Palette palette=Writers.Palette.plain();
        /** "Show who wrote what"; and whether the page is drawn in its writers' colours now: wanted, and two or more wrote. */
        private boolean wanted=true, inked;
        /** Text being put on the page with its writers, rather than typed. */
        private boolean putting;

        /** An edit, typed or pasted or cut: the new letters are yours, and the rest keep their writers. */
        private void edited(int at,int removed,int inserted) {
            if(putting||runs==null)return;
            before=runs.copy();
            runs.edit(at,removed,inserted,Writers.ME);
            // Never drawn from runs that do not fit the words: nobody's, rather than a colour on the wrong letters.
            if(runs.length()!=getDocument().getLength())runs=Writers.unknown(getDocument().getLength());
            boolean now=wanted&&palette.shows(runs);
            if(now!=inked){inked=now;repaint();}
        }

        /** Words put on the page as they are, with who wrote them: a note opened, or one that changed underneath it. */
        void put(String text,Writers who) {
            putting=true;
            try{setText(text);}finally{putting=false;}
            String on=getText(),given=text==null?"":text;
            runs=who!=null&&who.length()==on.length()&&given.equals(on)?who.copy():Writers.follow(on,Writers.UNKNOWN,given,who);
            before=null;ink();
        }

        /** Who wrote the words a note said, read once the page was showing them: anything typed since stays yours. */
        void follow(String said,Writers who) {
            if(who==null)return;
            String on=getText();
            runs=on.equals(said)&&who.length()==on.length()?who.copy():Writers.follow(on,Writers.ME,said,who,on,runs);
            ink();
        }

        /** Words that came back from an old version: what is still on the page keeps its writer, the rest is nobody's. */
        void putBack(String was,Writers had) {
            runs=Writers.follow(getText(),Writers.UNKNOWN,was,had);
            ink();
        }

        /** Who wrote what on the page now, to be written down with its words. */
        Writers writers(){return runs.copy();}

        /** The colours writers are drawn in, and whether they are. */
        void inks(Writers.Palette palette,boolean wanted){this.palette=palette==null?Writers.Palette.plain():palette;this.wanted=wanted;ink();}

        /** Whether the page is drawn in its writers' colours now. */
        boolean inked(){return inked;}

        private void ink(){inked=wanted&&palette.shows(runs);repaint();}

        /**
         * An edit Ctrl+Z can take back, with who wrote the letters: taken back, they are whoever wrote them before,
         * not letters of yours typed again; made again, they are what they were made as.
         */
        javax.swing.undo.UndoableEdit remembered(javax.swing.undo.UndoableEdit edit) {
            Writers was=before, after=runs.copy();
            return new javax.swing.undo.AbstractUndoableEdit() {
                @Override public void undo(){super.undo();edit.undo();back(was);}
                @Override public void redo(){super.redo();edit.redo();back(after);}
                @Override public boolean isSignificant(){return edit.isSignificant();}
                @Override public String getPresentationName(){return edit.getPresentationName();}
                @Override public void die(){super.die();edit.die();}
            };
        }
        private void back(Writers kept) {
            if(kept!=null&&kept.length()==getDocument().getLength())runs=kept.copy();
            ink();
        }

        /** The page's own look, with a caret that can be told to leave the view where it is (see holding). */
        @Override public void updateUI(){setUI(new Inked());}

        /**
         * While the page is being put back where the reader was, the caret does not scroll it: moving the caret brings it
         * into view a moment later, and a caret left at the end of a note took the page to the bottom at every arrival.
         */
        private boolean holding;
        void holding(boolean now){holding=now;}

        private final class Inked extends com.formdev.flatlaf.ui.FlatTextAreaUI {
            @Override protected javax.swing.text.Caret createCaret() {
                return new com.formdev.flatlaf.ui.FlatCaret(null,false) {
                    @Override protected void adjustVisibility(Rectangle place){if(!holding)super.adjustVisibility(place);}
                };
            }
        }

        /** The address under a point on the page, or null: over its letters, not merely the nearest place to them. */
        Links.Link linkAt(java.awt.Point p) {
            List<Links.Link> all=links();
            if(all.isEmpty())return null;
            try {
                int near=viewToModel2D(p);
                for(int at=Math.max(0,near-1);at<=near&&at<getDocument().getLength();at++) {
                    Links.Link one=Links.at(all,at);if(one==null)continue;
                    java.awt.geom.Rectangle2D from=modelToView2D(at),to=modelToView2D(at+1);
                    if(from==null||to==null)continue;
                    double right=to.getY()==from.getY()?to.getX():getWidth();
                    if(p.x>=from.getX()&&p.x<right&&p.y>=from.getY()&&p.y<from.getY()+from.getHeight())return one;
                }
            } catch(Exception notLaidOut){/* none, then */}
            return null;
        }

        private void open(String target) {
            try {
                java.net.URI where=new java.net.URI(target);
                if("mailto".equalsIgnoreCase(where.getScheme()))java.awt.Desktop.getDesktop().mail(where);
                else java.awt.Desktop.getDesktop().browse(where);
            } catch(Exception e) {
                cannotOpen.accept(new IllegalStateException(target.startsWith("mailto:")
                    ?"No mail app on this PC offered to write to that address.":"Nothing on this PC could open that address."));
            }
        }

        protected void paintComponent(Graphics original) {
            Graphics2D g=(Graphics2D)original.create();g.setColor(getBackground());g.fillRect(0,0,getWidth(),getHeight());
            int height=getFontMetrics(getFont()).getHeight();g.setColor(rules);
            for(int y=getInsets().top+height;y<getHeight();y+=height)g.drawLine(28,y,getWidth()-22,y);
            g.setColor(new Color(221,192,179));g.drawLine(30,0,30,getHeight());g.dispose();
            setOpaque(false);super.paintComponent(original);
            paintWriters(original);
            paintLinks(original);
            ColourEmoji colour=ColourEmoji.get();if(colour!=null)colour.paint(this,original);
        }

        /** While an address is drawn again: the letters come out in the accent colour. See paintLinks. */
        private boolean inking;
        /** While a writer's letters are drawn again: their colour. See paintWriters. */
        private Color inkNow;
        @Override public Color getForeground(){return inking?ACCENT:inkNow!=null?inkNow:super.getForeground();}

        /**
         * Each writer's letters in view drawn again over the plain ones Java drew, in the writer's colour, the way
         * addresses are (see paintLinks): the place filled with the paper, the page's own text drawn into that place alone
         * with the colour as its colour - so the letters land exactly where Java put them, whatever view it chose for the
         * words - then the ruled line put back. Only what the paint was asked for, so typing redraws a line, not the page.
         * Selected words are left as any selection is drawn, and the caret is drawn again on top.
         */
        private void paintWriters(Graphics original) {
            Writers who=runs;
            if(!inked||who==null||who.length()!=getDocument().getLength()||who.length()==0)return;
            Rectangle view=getVisibleRect(),asked=original.getClipBounds(),area=asked==null?view:view.intersection(asked);
            if(area.isEmpty())return;
            String text=getText();int from,to;
            try {
                from=Math.max(0,viewToModel2D(new java.awt.Point(0,area.y))-1);
                to=Math.min(text.length(),viewToModel2D(new java.awt.Point(getWidth(),area.y+area.height))+1);
            } catch(RuntimeException notLaidOut){return;}
            Graphics2D g=(Graphics2D)original.create();
            try {
                Object hints=Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
                if(hints instanceof Map<?,?> map)g.addRenderingHints(map);
                FontMetrics metrics=getFontMetrics(getFont());
                int chosenFrom=getSelectionStart(),chosenTo=getSelectionEnd(),paper=getBackground().getRGB(),start=0;
                Insets in=getInsets();
                Rectangle editor=new Rectangle(in.left,in.top,getWidth()-in.left-in.right,getHeight()-in.top-in.bottom);
                for(int run=0;run<who.count()&&start<to;run++) {
                    int end=start+who.lengthOf(run),at=Math.max(start,from),stop=Math.min(end,to);
                    int colour=palette.colourOf(who.writerOf(run));
                    start=end;
                    if(stop<=at||!Tint.known(colour))continue;
                    Color ink=new Color(Writers.ink(colour,paper));
                    while(at<stop) {
                        // One row's worth: words wrapped onto the next row are drawn there as a piece of their own.
                        java.awt.geom.Rectangle2D first=modelToView2D(at);int past=at+1;
                        java.awt.geom.Rectangle2D next=past<text.length()?modelToView2D(past):null;
                        while(past<stop&&next!=null&&next.getY()==first.getY()){past++;next=past<text.length()?modelToView2D(past):null;}
                        if(first==null||chosenTo>chosenFrom&&at<chosenTo&&past>chosenFrom){at=past;continue;}
                        float x=(float)first.getX(),top=(float)first.getY(),high=(float)first.getHeight();
                        float right=next!=null&&next.getY()==first.getY()?(float)next.getX():x+metrics.stringWidth(text.substring(at,past).replace("\n",""));
                        if(right>x) {
                            java.awt.geom.Rectangle2D.Float place=new java.awt.geom.Rectangle2D.Float(x,top,right-x,high);
                            g.setColor(getBackground());g.fill(place);
                            Graphics2D inside=(Graphics2D)g.create();inside.clip(place);inkNow=ink;
                            try{getUI().getRootView(this).paint(inside,editor);}finally{inkNow=null;inside.dispose();}
                            // The ruled line the paper covered, put back.
                            g.setColor(rules);
                            for(int y=in.top+metrics.getHeight();y<getHeight();y+=metrics.getHeight())
                                if(y>=top&&y<top+high)g.drawLine(Math.round(x),y,Math.round(right),y);
                        }
                        at=past;
                    }
                }
                if(getCaret()!=null&&getCaret().isVisible())getCaret().paint(g);
            } catch(Exception notNow){/* plain letters, then */}
            finally{g.dispose();}
        }

        /**
         * Each address in view drawn again over the plain letters Java drew, in the accent colour and underlined, as
         * the phone draws them: its place filled with the paper, then the page's own text drawn into that place alone
         * with the accent as its colour - so the letters land exactly where Java put them, whatever it measured -
         * then the ruled line put back. Nothing is set on the page while it paints, which would ask for another paint.
         * Selected words are left as any selection is drawn, and the caret is drawn again on top.
         */
        private void paintLinks(Graphics original) {
            List<Links.Link> all=links();
            if(all.isEmpty())return;
            String text=getText();Rectangle view=getVisibleRect();int from,to;
            try {
                from=Math.max(0,viewToModel2D(new java.awt.Point(view.x,view.y))-1);
                to=Math.min(text.length(),viewToModel2D(new java.awt.Point(view.x+view.width,view.y+view.height))+1);
            } catch(RuntimeException notLaidOut){return;}
            Graphics2D g=(Graphics2D)original.create();
            try {
                Object hints=Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
                if(hints instanceof Map<?,?> map)g.addRenderingHints(map);
                // The page's own measure of the font, which is where Java put the letters it drew.
                g.setFont(getFont());FontMetrics metrics=getFontMetrics(getFont());
                int chosenFrom=getSelectionStart(),chosenTo=getSelectionEnd();
                Insets in=getInsets();
                Rectangle editor=new Rectangle(in.left,in.top,getWidth()-in.left-in.right,getHeight()-in.top-in.bottom);
                for(Links.Link one:all) {
                    if(one.end()<=from||one.start()>=to)continue;
                    int at=Math.max(one.start(),from),end=Math.min(one.end(),to);
                    while(at<end) {
                        // One line's worth: an address wrapped onto the next line is drawn there as a second run.
                        java.awt.geom.Rectangle2D first=modelToView2D(at);int past=at+1;
                        java.awt.geom.Rectangle2D next=past<text.length()?modelToView2D(past):null;
                        while(past<end&&next!=null&&next.getY()==first.getY()){past++;next=past<text.length()?modelToView2D(past):null;}
                        String run=text.substring(at,past);
                        if(chosenTo>chosenFrom&&at<chosenTo&&past>chosenFrom){at=past;continue;}
                        float x=(float)first.getX(),top=(float)first.getY(),high=(float)first.getHeight();
                        float right=next!=null&&next.getY()==first.getY()?(float)next.getX():x+metrics.stringWidth(run);
                        java.awt.geom.Rectangle2D.Float place=new java.awt.geom.Rectangle2D.Float(x,top,right-x,high);
                        g.setColor(getBackground());g.fill(place);
                        Graphics2D inside=(Graphics2D)g.create();inside.clip(place);inking=true;
                        try{getUI().getRootView(this).paint(inside,editor);}finally{inking=false;inside.dispose();}
                        // Just under the letters, inside the line's own descent.
                        float baseline=top+high-metrics.getDescent()-metrics.getLeading();
                        g.setColor(ACCENT);g.fill(new java.awt.geom.Rectangle2D.Float(x,baseline+2f,right-x,1f));
                        // The ruled line the paper covered, put back.
                        g.setColor(rules);
                        for(int y=getInsets().top+metrics.getHeight();y<getHeight();y+=metrics.getHeight())
                            if(y>=top&&y<top+high)g.drawLine(Math.round(x),y,Math.round(right),y);
                        at=past;
                    }
                }
                if(getCaret()!=null&&getCaret().isVisible())getCaret().paint(g);
            } catch(Exception notNow){/* plain letters, then */}
            finally{g.dispose();}
        }
    }
}

