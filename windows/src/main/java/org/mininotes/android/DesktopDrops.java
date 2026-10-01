package org.mininotes.android;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

/**
 * Files sent straight to a device, and the ones received, belonging to no note (see {@link Drop}). The Send box: the
 * files, and the devices they can go to, people shown by name, the owner's own first; one button. What came is on Home,
 * with a <i>new</i> badge until it is opened (docs/HOME.md, decision 6); somebody else's device asking to send is asked
 * about in a box; and what went from here, and what is still coming, is listed under ⋯ → Sent files (decision 20).
 *
 * <p>And files from Explorer let go anywhere on the window: on a note they are kept with it, on a collection or its card
 * with the collection, on Home's grid on Home (see {@link #aim}).
 */
final class DesktopDrops {
    private DesktopDrops(){}

    // ---- sending ------------------------------------------------------------------------------------------

    /** Send files…: the files chosen here, then where they go. */
    static void send(Desktop pad){send(pad,List.of());}

    /**
     * The Send box, for these files - dropped on the window, or none yet, when they are chosen first. A file that
     * cannot go is said plainly, with why, and the rest go.
     */
    static void send(Desktop pad,List<Path> given){send(pad,given,null);}

    /** @param toward the device to have chosen already, by its address: the one the files were dropped on */
    static void send(Desktop pad,List<Path> given,String toward) {
        List<Path> chosen=new ArrayList<>(given);
        if(chosen.isEmpty()) {
            JFileChooser pick=new JFileChooser();pick.setMultiSelectionEnabled(true);pick.setDialogTitle("Send files");
            if(pick.showOpenDialog(pad.frame)!=JFileChooser.APPROVE_OPTION)return;
            File[] files=pick.getSelectedFiles();if(files.length==0&&pick.getSelectedFile()!=null)files=new File[]{pick.getSelectedFile()};
            for(File one:files)chosen.add(one.toPath());
            if(chosen.isEmpty())return;
        }
        pad.disk.submit(()->{
            List<Path> going=new ArrayList<>();List<String> refused=new ArrayList<>();long bytes=0;
            for(Path one:chosen) {
                String name=Attachment.named(one.getFileName()==null?"":one.getFileName().toString());
                try {
                    if(!Files.isRegularFile(one)){refused.add(Given.refusal(name,Given.UNREADABLE));continue;}
                    long size=Files.size(one);
                    if(size<=0){refused.add(Given.refusal(name,Drop.EMPTY));continue;}
                    if(size>Enclosure.MOST){refused.add(Given.refusal(name,Drop.TOO_BIG));continue;}
                    if(going.size()>=Drop.FILES_MOST){refused.add(Given.refusal(name,Drop.TOO_MANY));continue;}
                    going.add(one);bytes+=size;
                } catch(IOException unreadable){refused.add(Given.refusal(name,Given.UNREADABLE));}
            }
            List<Drop.Device> devices=new ArrayList<>();
            for(NoteStore.Contact one:pad.store.addresses())if(one.paired())devices.add(new Drop.Device(one.address,one.name,one.mine));
            return new Object[]{going,refused,bytes,Drop.choices(devices)};
        },loaded->{
            @SuppressWarnings("unchecked") List<Path> going=(List<Path>)loaded[0];
            @SuppressWarnings("unchecked") List<String> refused=(List<String>)loaded[1];
            @SuppressWarnings("unchecked") List<Drop.Device> devices=(List<Drop.Device>)loaded[3];
            if(going.isEmpty()){DesktopUi.tell(pad.frame,"Send files",DesktopUi.note(refused.isEmpty()?"Nothing to send.":Drop.notSent(refused),380,DesktopUi.INK,DesktopUi.BODY));return;}
            if(devices.isEmpty()){DesktopUi.tell(pad.frame,"Send files",DesktopUi.note("No devices paired yet. Pair one in People and devices, and files can go straight to it.",380,DesktopUi.INK,DesktopUi.BODY));return;}
            box(pad,going,refused,(Long)loaded[2],devices,toward);
        },pad::failed);
    }

    /** The box itself: what goes, and to whom. */
    static void box(Desktop pad,List<Path> going,List<String> refused,long bytes,List<Drop.Device> devices,String toward) {
        JDialog[] box={null};
        JPanel body=DesktopUi.column();
        // What goes: how many and how big, then their names, a few of them.
        DesktopUi.add(body,DesktopUi.body(Drop.files(going.size())+"  ·  "+Attachment.size(bytes)));
        StringBuilder names=new StringBuilder();
        for(int at=0;at<going.size()&&at<4;at++)names.append(at==0?"":", ").append(Attachment.named(going.get(at).getFileName().toString()));
        if(going.size()>4)names.append(" and ").append(going.size()-4).append(" more");
        DesktopUi.add(body,DesktopUi.note(names.toString(),420,DesktopUi.QUIET,DesktopUi.BODY.deriveFont(13f)));
        if(!refused.isEmpty()){DesktopUi.gap(body,DesktopUi.S);DesktopUi.add(body,DesktopUi.note(Drop.notSentLine(refused),420,DesktopUi.WARN,DesktopUi.BODY.deriveFont(13f)));}
        DesktopUi.gap(body,DesktopUi.M);
        DesktopUi.add(body,DesktopUi.heading("Send to"));DesktopUi.gap(body,DesktopUi.S);
        // The devices, one chosen: each a person with their round and their name, the owner's own saying so.
        DefaultListModel<Drop.Device> model=new DefaultListModel<>();for(Drop.Device one:devices)model.addElement(one);
        JList<Drop.Device> list=new JList<>(model);list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setSelectedIndex(0);
        for(int at=0;at<devices.size();at++)if(devices.get(at).address.equals(toward))list.setSelectedIndex(at);
        list.setBackground(DesktopUi.CARD);list.setVisibleRowCount(Math.min(6,Math.max(2,devices.size())));
        list.setCellRenderer((l,one,index,selected,focus)->{
            JPanel row=DesktopUi.person(one.name,one.under());row.setOpaque(true);
            row.setBackground(selected?DesktopUi.mix(DesktopUi.ACCENT,Color.WHITE,0.86f):DesktopUi.CARD);
            row.setBorder(BorderFactory.createEmptyBorder(8,12,8,12));return row;});
        JScrollPane scroll=new JScrollPane(list);scroll.setBorder(BorderFactory.createLineBorder(DesktopUi.LINE));
        DesktopUi.add(body,scroll);
        List<Path> all=new ArrayList<>(going);
        Runnable go=()->{
            Drop.Device to=list.getSelectedValue();if(to==null)return;
            box[0].dispose();sending(pad,all,to);
        };
        list.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(e.getClickCount()==2)go.run();}});
        JButton send=DesktopUi.primary("Send",go);
        box[0]=DesktopUi.sheet(pad.frame,"Send files",body,DesktopUi.footer(send),true);
        // More files dropped on the box join these; dropped on a device, they go to that device.
        java.util.function.Consumer<List<Path>> more=added->{
            Drop.Device to=list.getSelectedValue();box[0].dispose();
            List<Path> joined=new ArrayList<>(all);joined.addAll(added);send(pad,joined,to==null?null:to.address);};
        box[0].getRootPane().setTransferHandler(new Dropping(more));
        list.setTransferHandler(new Dropping(more){
            @Override public boolean canImport(TransferSupport support) {
                if(!super.canImport(support))return false;
                if(support.isDrop()){int at=list.locationToIndex(support.getDropLocation().getDropPoint());if(at>=0)list.setSelectedIndex(at);}
                return true;
            }
        });
        box[0].getRootPane().setDefaultButton(send);box[0].getRootPane().putClientProperty("focus",list);
        DesktopUi.show(box[0],480,620);
    }

    /**
     * The files copied in - sealed on the way while the notebook is locked - and the sending written down, then
     * offered at once. The bar says it is going and, when that is done, where it stands.
     */
    private static void sending(Desktop pad,List<Path> files,Drop.Device to) {
        pad.status.setToolTipText(null);pad.status.setText(Drop.sending(to.name,files.size()));
        pad.disk.submit(()->{
            List<NoteStore.Held> kept=new ArrayList<>();
            try {
                for(Path one:files) {
                    long size=Files.size(one);
                    NoteStore.Held held=pad.store.opening(NoteStore.Branch.Kind.PAGE,"",one.getFileName().toString(),Files.probeContentType(one),size);
                    DesktopFiles.keep(pad.context,one,pad.store.fileFor(held.id).toPath());
                    kept.add(held);
                }
                return pad.store.sendFiles(to.address,to.name,kept);
            } catch(Exception failure) {
                for(NoteStore.Held one:kept)Files.deleteIfExists(pad.store.fileFor(one.id).toPath());
                throw new IOException("Could not read "+(files.size()==1?"the file":"the files")+". Nothing was sent.",failure);
            }
        },id->{
            if(pad.offline){pad.status.setText("Waiting for "+to.name+". This window was opened offline, so they go when Mininotes is next online.");return;}
            pad.network.submit(()->Post.sendNow(pad.context,pad.store,pad.keys,id),said->pad.status.setText(said),pad::failed);
        },pad::failed);
    }

    // ---- what is dropped on the window ---------------------------------------------------------------------

    /** Files from Explorer, taken wherever they are dropped: they open the Send box. Anything else is not taken here. */
    static class Dropping extends TransferHandler {
        private final java.util.function.Consumer<List<Path>> then;
        Dropping(java.util.function.Consumer<List<Path>> then){this.then=then;}
        @Override public boolean canImport(TransferSupport support){return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);}
        @Override public boolean importData(TransferSupport support) {
            if(!canImport(support))return false;
            try {
                @SuppressWarnings("unchecked") List<File> files=(List<File>)support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                List<Path> paths=new ArrayList<>();for(File one:files)if(one.isFile())paths.add(one.toPath());
                if(paths.isEmpty())return false;
                // After the drop has finished: a box opened inside it would hold Explorer's drag until it closed.
                SwingUtilities.invokeLater(()->then.accept(paths));
                return true;
            } catch(Exception unreadable){return false;}
        }
    }

    /** What files let go at a place would do there: be attached to a note, be kept with a collection or Home, or open the Send box. */
    static final int ATTACH=0,KEEP=1,BOX=2;
    /**
     * Where files let go would go. {@code note} and {@code name} are the note's, for ATTACH, or the collection's - Home's
     * for Home - for KEEP; {@code on} is what is lit while they are over it, and {@code area} its part, in its own place.
     */
    record Aim(int does,String note,String name,JComponent on,Rectangle area) {
        static final Aim BOXED=new Aim(BOX,null,null,null,null);
        /** The one line the bar says while they are held over it. */
        String hint(boolean readOnly) {
            if(does==BOX)return "Drop to send to a device";
            if(does==KEEP)return NoteStore.home(note)?"Drop to keep on Home":"Drop to keep with “"+name+"”";
            return readOnly?"This note is read only here":"Drop to attach to “"+name+"”";
        }
    }

    /**
     * What is at this point of this component, for files: the open note - its page, its title, its files, the paperclip -
     * or a note's icon, its dock icon or its line in the tree, to attach to; a collection's icon, its card or its line, to
     * keep with it; Home's grid, the search and the dock, to keep on Home; anywhere else the Send box. {@code at} is null
     * for a paste, taken where the component is.
     */
    static Aim aim(Desktop pad,Component on,java.awt.Point at) {
        if(on instanceof RootPaneContainer window){Container in=window.getContentPane();if(at!=null)at=SwingUtilities.convertPoint(on,at,in);on=in;}
        Component deep=on;
        if(at!=null&&on instanceof Container in){Component d=SwingUtilities.getDeepestComponentAt(in,at.x,at.y);if(d!=null){at=SwingUtilities.convertPoint(on,at,d);deep=d;}}
        String open=pad.noteShown();
        for(Component c=deep;c!=null;c=c.getParent()) {
            if(c==pad.tree) {
                java.awt.Point there=at==null?null:SwingUtilities.convertPoint(deep,at,c);
                int row=there==null?pad.tree.getLeadSelectionRow():pad.tree.getClosestRowForLocation(there.x,there.y);
                Rectangle r=row<0?null:pad.tree.getRowBounds(row);
                if(r==null||there!=null&&(there.y<r.y||there.y>=r.y+r.height))return Aim.BOXED;
                Object said=((javax.swing.tree.DefaultMutableTreeNode)pad.tree.getPathForRow(row).getLastPathComponent()).getUserObject();
                Rectangle line=new Rectangle(0,r.y,pad.tree.getWidth(),r.height).intersection(pad.tree.getVisibleRect());
                if(said instanceof Desktop.Item item&&item.branch().kind==NoteStore.Branch.Kind.PAGE)return new Aim(ATTACH,item.branch().id,named(item.branch()),pad.tree,line);
                if(said instanceof Desktop.Item item&&DesktopMoving.shelf(item.branch().kind))return new Aim(KEEP,item.branch().id,named(item.branch()),pad.tree,line);
                if(!(said instanceof Desktop.Item))return new Aim(KEEP,Things.HOME,"Home",pad.tree,line);
                return Aim.BOXED;
            }
            if(!(c instanceof JComponent j))continue;
            if(j.getClientProperty(DesktopHome.THING) instanceof NoteStore.Branch thing) {
                Rectangle all=new Rectangle(j.getSize());
                if(thing.kind==NoteStore.Branch.Kind.PAGE)return new Aim(ATTACH,thing.id,named(thing),j,all);
                if(DesktopMoving.shelf(thing.kind))return new Aim(KEEP,thing.id,named(thing),j,all);
                // A file's icon, or the Favourites': what holds it takes them.
                continue;
            }
            if(j.getClientProperty(DesktopHome.KEEPS) instanceof NoteStore.Branch place) {
                boolean home=place.kind==NoteStore.Branch.Kind.LIBRARY;
                return new Aim(KEEP,home?Things.HOME:place.id,home?"Home":named(place),j,j.getVisibleRect());
            }
            if(open!=null&&(j==pad.page||j==pad.paperScroll||j==pad.title||j==pad.clip||j==pad.fileCards)) {
                // The whole note lit, from its title to its files: wherever on it they go, they go to it.
                JComponent note=pad.noteArea()!=null?pad.noteArea():j;String title=pad.title.getText();
                return new Aim(ATTACH,open,title==null||title.isBlank()?"Untitled":title,note,new Rectangle(note.getSize()));
            }
        }
        return Aim.BOXED;
    }
    private static String named(NoteStore.Branch b){return b.name==null||b.name.isBlank()?"Untitled":b.name;}

    /** Files let go where {@code aim} said: attached to that note, kept with that collection or on Home, or sent. Said in the bar. */
    static void dropped(Desktop pad,Aim aim,List<Path> files) {
        if(aim.does==ATTACH)pad.attach(aim.note,aim.name,files);
        else if(aim.does==KEEP)pad.keep(aim.note,aim.name,files);
        else send(pad,files);
    }

    /**
     * What a component already did with a drop or a paste, kept - text into the page or the title, a row of the
     * tree - with files from Explorer taken as well, sent where {@link #aim} says; {@code was} is null where the
     * component took nothing before. Only the drop is added to; copying and dragging out go through the handler
     * that was there.
     */
    static final class Aimed extends TransferHandler {
        private final Desktop pad;private final TransferHandler was;
        Aimed(Desktop pad,TransferHandler was){this.pad=pad;this.was=was;}
        private static boolean files(TransferSupport support){return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);}
        @Override public boolean canImport(TransferSupport support) {
            if(!files(support))return picture(pad,support)!=null||was!=null&&was.canImport(support);
            // No caret or row chosen under them: what they would go to is lit instead, and said in the bar.
            if(support.isDrop()){support.setShowDropLocation(false);over(pad,aim(pad,support.getComponent(),support.getDropLocation().getDropPoint()));}
            return true;
        }
        @Override public boolean importData(TransferSupport support) {
            if(!files(support)) {
                // A picture pasted on a note with no words to it - a screenshot from Greenshot or the Snipping Tool, a
                // picture copied in a browser - is kept with the note, as a messaging app takes one (Attachment.pasted).
                Aim aim=picture(pad,support);
                if(aim!=null) {
                    try {
                        BufferedImage picture=DesktopIconPicker.buffered((Image)support.getTransferable().getTransferData(DataFlavor.imageFlavor));
                        if(picture!=null){SwingUtilities.invokeLater(()->pad.attachPicture(aim.note,aim.name,picture));return true;}
                    } catch(Exception unreadable){pad.status.setText("That picture could not be read from the clipboard");return false;}
                }
                return was!=null&&was.importData(support);
            }
            try {
                @SuppressWarnings("unchecked") List<File> files=(List<File>)support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                List<Path> paths=new ArrayList<>();for(File one:files)if(one.isFile())paths.add(one.toPath());
                if(paths.isEmpty()){away(pad);return false;}
                Aim aim=aim(pad,support.getComponent(),support.isDrop()?support.getDropLocation().getDropPoint():null);
                away(pad);
                // After the drop has finished: a box opened inside it would hold Explorer's drag until it closed.
                SwingUtilities.invokeLater(()->dropped(pad,aim,paths));
                return true;
            } catch(Exception unreadable){away(pad);return false;}
        }
        @Override public int getSourceActions(JComponent c){return was==null?super.getSourceActions(c):was.getSourceActions(c);}
        @Override public void exportAsDrag(JComponent c,InputEvent e,int action){if(was!=null)was.exportAsDrag(c,e,action);}
        @Override public void exportToClipboard(JComponent c,java.awt.datatransfer.Clipboard clip,int action){if(was!=null)was.exportToClipboard(c,clip,action);else super.exportToClipboard(c,clip,action);}
        @Override public Icon getVisualRepresentation(java.awt.datatransfer.Transferable t){return was==null?null:was.getVisualRepresentation(t);}
    }

    /**
     * Where a paste would keep a picture: the note it is pasted on, where the clipboard holds a picture and no words or
     * files (Attachment.pasted). Null for anything else - a drop, words, files, or a paste that is not on a note.
     */
    static Aim picture(Desktop pad,TransferHandler.TransferSupport support) {
        if(support.isDrop()||!support.isDataFlavorSupported(DataFlavor.imageFlavor))return null;
        boolean words=false;
        if(support.isDataFlavorSupported(DataFlavor.stringFlavor))
            try{Object said=support.getTransferable().getTransferData(DataFlavor.stringFlavor);words=said instanceof String s&&!s.isBlank();}
            catch(Exception unreadable){/* no words to take, then */}
        if(Attachment.pasted(false,words,true)!=Attachment.Pasted.PICTURE)return null;
        Aim aim=aim(pad,support.getComponent(),null);
        return aim.does==ATTACH?aim:null;
    }

    /**
     * The whole window takes files: where nothing inside says otherwise, the window's own handler; where something
     * already takes drops of its own - the page, the title, the tree, the search - that goes on, and files are
     * taken there too. Said once, after the window is built.
     */
    static void takeDrops(Desktop pad) {
        pad.frame.setTransferHandler(new Aimed(pad,null));heard(pad,pad.frame);
        wrap(pad,pad.frame.getContentPane());
    }

    private static void wrap(Desktop pad,Container in) {
        for(Component one:in.getComponents()) {
            if(one instanceof JComponent c&&c.getTransferHandler()!=null&&!(c.getTransferHandler() instanceof Dropping)&&!(c.getTransferHandler() instanceof Aimed))
                {c.setTransferHandler(new Aimed(pad,c.getTransferHandler()));heard(pad,c);}
            if(one instanceof Container k)wrap(pad,k);
        }
    }

    /** The light and the bar's line go when the files leave the component, or are let go. */
    private static void heard(Desktop pad,Component c) {
        java.awt.dnd.DropTarget target=c.getDropTarget();if(target==null)return;
        try{target.addDropTargetListener(new java.awt.dnd.DropTargetAdapter(){
            @Override public void dragExit(java.awt.dnd.DropTargetEvent e){away(pad);}
            @Override public void drop(java.awt.dnd.DropTargetDropEvent e){away(pad);}
        });}catch(java.util.TooManyListenersException none){/* the handler's own drop still clears it */}
    }

    /** What is lit while files are held over the window: a line washed as a chosen one, or an outline. Drawn over it all. */
    static final class Lit extends JComponent {
        Aim aim;boolean readOnly;
        /** What the bar said before, put back when the files go; and whether it was showing. */
        String before;boolean showing;
        Lit(){setOpaque(false);}
        /** Never under the pointer: what is below is what is found and what the files go to. */
        @Override public boolean contains(int x,int y){return false;}
        @Override protected void paintComponent(Graphics g0) {
            // Found again at each drawing: the bar coming up under it moves what it lights.
            if(aim==null||aim.on==null||readOnly||!aim.on.isShowing())return;
            boolean line=aim.on instanceof JTree;
            Rectangle area=SwingUtilities.convertRectangle(aim.on,aim.area!=null?aim.area:new Rectangle(aim.on.getSize()),this);
            Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            if(line){g.setColor(new Color(DesktopUi.ACCENT.getRed(),DesktopUi.ACCENT.getGreen(),DesktopUi.ACCENT.getBlue(),46));g.fillRect(area.x,area.y,area.width,area.height);}
            else{g.setColor(DesktopUi.ACCENT);g.setStroke(new BasicStroke(2f));g.drawRoundRect(area.x+2,area.y+2,area.width-5,area.height-5,12,12);}
            g.dispose();
        }
    }
    private static Lit lit(Desktop pad) {
        JLayeredPane layers=pad.frame.getLayeredPane();
        if(layers.getClientProperty(Lit.class) instanceof Lit lit)return lit;
        Lit lit=new Lit();layers.add(lit,JLayeredPane.DRAG_LAYER);layers.putClientProperty(Lit.class,lit);return lit;
    }

    /** Files held over what {@code aim} found: it lit, and the bar saying what letting go does. */
    static void over(Desktop pad,Aim aim) {
        Lit lit=lit(pad);JLayeredPane layers=pad.frame.getLayeredPane();
        if(lit.aim==null){lit.before=pad.status.getText();lit.showing=pad.status.isVisible();}
        boolean readOnly=aim.does==ATTACH&&aim.note.equals(pad.noteShown())&&!pad.page.isEditable();
        lit.aim=aim;lit.readOnly=readOnly;lit.setBounds(0,0,layers.getWidth(),layers.getHeight());lit.repaint();
        String hint=aim.hint(readOnly);
        if(!hint.equals(pad.status.getText())||!pad.status.isVisible()){pad.status.setToolTipText(null);pad.status.setText(hint);}
    }

    /** The files went, or were let go: nothing lit, and the bar as it was. */
    static void away(Desktop pad) {
        Lit lit=lit(pad);if(lit.aim==null)return;
        lit.aim=null;lit.repaint();
        pad.status.setText(lit.showing&&lit.before!=null?lit.before:"");
    }

    // ---- what came, and what went -------------------------------------------------------------------------------

    /** Something came, or was answered: said in the bar - and over the tray while the window is away - and asked about. */
    static void heard(Desktop pad,Post.Landed landed) {
        if(landed.said!=null)pad.notice(landed.said);
        if(landed.asking!=null)ask(pad,landed.asking);
        pad.refresh();
    }

    /** Somebody who is not one of the owner's devices wants to send files: accept, or refuse. */
    static void ask(Desktop pad,String id) {
        pad.disk.submit(()->pad.store.transfer(id),one->{
            if(one==null||one.out||one.state!=Drop.ASKING)return;
            JDialog[] box={null};
            JPanel body=DesktopUi.column();
            DesktopUi.add(body,DesktopUi.person(one.name,Drop.wants(one.files.size(),one.bytes())));
            DesktopUi.gap(body,DesktopUi.M);
            StringBuilder names=new StringBuilder();for(NoteStore.Loose f:one.files)names.append(names.length()==0?"":"\n").append(f.name).append("   ·   ").append(Attachment.size(f.bytes));
            DesktopUi.add(body,DesktopUi.note(names.toString(),380,DesktopUi.QUIET,DesktopUi.BODY.deriveFont(13f)));
            DesktopUi.gap(body,DesktopUi.S);
            DesktopUi.add(body,DesktopUi.note("They are kept on this PC on Home, in no note. Refusing tells "+one.name+".",380,DesktopUi.QUIET,DesktopUi.BODY.deriveFont(12.5f)));
            JButton refuse=DesktopUi.button("Refuse",()->{box[0].dispose();answer(pad,one,false);});
            JButton accept=DesktopUi.primary("Accept",()->{box[0].dispose();answer(pad,one,true);});
            box[0]=DesktopUi.sheet(pad.frame,"Files from "+one.name,body,DesktopUi.footer(refuse,accept),true);
            box[0].getRootPane().setDefaultButton(accept);
            DesktopUi.show(box[0],460,520);
        },pad::failed);
    }

    private static void answer(Desktop pad,NoteStore.Transfer one,boolean yes) {
        pad.status.setText(yes?"Accepting…":"Telling "+one.name+"…");
        pad.network.submit(()->{
            if(yes)Post.takeSending(pad.context,pad.store,pad.keys,one.id);else Post.refuseSending(pad.context,pad.store,pad.keys,one.id);
            return null;
        },done->{pad.status.setText(yes?"Accepted. The files are coming from "+one.name+"…":"Refused. "+one.name+" is told.");pad.refresh();},pad::failed);
    }

    private static final java.time.format.DateTimeFormatter WHEN=java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm");
    private static String when(long at){return WHEN.format(java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()));}

    /**
     * ⋯ → Sent files: what this PC sent to others, the newest first, each with where it stands and a button to stop it or
     * take it off the list (decision 20). Above it, while there is any, what another device is sending here - somebody
     * asking, or files still on their way - since with the drop box gone this is the one list of sendings left. The same
     * list the phone's ⋮ → Sent files shows.
     */
    static void sentFiles(Desktop pad) {
        pad.disk.submit(()->{
            List<NoteStore.Transfer> sent=pad.store.sentFiles(),coming=new ArrayList<>();
            for(NoteStore.Transfer one:pad.store.transfers())if(!one.out&&(one.state==Drop.ASKING||one.state==Drop.FETCHING))coming.add(one);
            Set<String> takes=new HashSet<>();
            for(NoteStore.Transfer one:sent)if(Post.takesFiles(pad.context,pad.store.address(one.address)))takes.add(one.address);
            return new Object[]{sent,coming,takes};
        },got->{
            @SuppressWarnings("unchecked") List<NoteStore.Transfer> sent=(List<NoteStore.Transfer>)got[0];
            @SuppressWarnings("unchecked") List<NoteStore.Transfer> coming=(List<NoteStore.Transfer>)got[1];
            @SuppressWarnings("unchecked") Set<String> takes=(Set<String>)got[2];
            JDialog[] box={null};
            JPanel body=DesktopUi.column();
            if(!coming.isEmpty()) {
                JPanel waiting=DesktopUi.column();
                for(NoteStore.Transfer one:coming) {
                    if(one.state==Drop.ASKING) {
                        JButton refuse=DesktopUi.button("Refuse",()->{box[0].dispose();answer(pad,one,false);}),accept=DesktopUi.button("Accept",()->{box[0].dispose();answer(pad,one,true);});
                        JPanel line=DesktopUi.row(DesktopUi.person(one.name,Drop.wants(one.files.size(),one.bytes())),DesktopUi.actions(refuse,accept));
                        DesktopMenus.echoOnRightClick(line,accept,refuse);waiting.add(line);
                    } else {
                        int here=0;for(NoteStore.Loose f:one.files)if(f.here)here++;
                        JButton stop=DesktopUi.button("Stop",()->{box[0].dispose();answer(pad,one,false);});
                        JPanel line=DesktopUi.row(DesktopUi.person(one.name,Drop.files(one.files.size())+" coming  ·  "+here+" here so far"),stop);
                        DesktopMenus.echoOnRightClick(line,stop);waiting.add(line);
                    }
                }
                DesktopUi.add(body,DesktopUi.card("Coming to this PC",waiting));DesktopUi.gap(body,12);
            }
            JPanel went=DesktopUi.column();
            for(NoteStore.Transfer one:sent) {
                String state=Drop.state(one.state,one.name,takes.contains(one.address));
                boolean going=Drop.goingOn(one.state);
                JButton remove=DesktopUi.button(going?"Stop sending":"Remove",()->{
                    if(going&&!DesktopUi.confirm(box[0],"Stop sending?","The "+Drop.files(one.files.size())+" for "+one.name+" are not offered again.","Stop sending",true))return;
                    pad.disk.submit(()->{Post.stopSending(pad.context,pad.store,one.id);return null;},done->{box[0].dispose();sentFiles(pad);},pad::failed);});
                JPanel line=DesktopUi.row(DesktopUi.person(one.name,Drop.files(one.files.size())+"  ·  "+when(one.at)+"  ·  "+state),remove);
                DesktopMenus.echoOnRightClick(line,remove);went.add(line);
            }
            if(sent.isEmpty())DesktopUi.add(went,DesktopUi.note("Nothing sent from this PC yet. Files go to another device from ⋯ → Send files, or from a file's own menu.",400,DesktopUi.QUIET,DesktopUi.BODY.deriveFont(13f)));
            DesktopUi.add(body,DesktopUi.card("Sent",went));
            box[0]=DesktopUi.sheet(pad.frame,"Sent files",DesktopUi.scrolling(body),null,true);
            DesktopUi.show(box[0],560,640);
        },pad::failed);
    }
}
