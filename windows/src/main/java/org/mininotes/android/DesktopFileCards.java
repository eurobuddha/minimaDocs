package org.mininotes.android;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.List;
import javax.swing.*;

/**
 * The attachments of the open note as small cards under the page: a picture shows itself, anything else its
 * kind; the name and size under it; a click opens it; the cross deletes it, after one question.
 */
final class DesktopFileCards extends JPanel {
    private final Desktop app;
    private String showing="";

    /**
     * The cards in one row that slides sideways, as on the phone. It was a row that simply stopped at the
     * window's edge: four cards of eight showed, and nothing said there were more or let them be reached. Now
     * the last card that fits is cut by the edge, which says there are more; a thin bar under them shows how
     * far along the row is; and the wheel moves it sideways, since a row has no up and down.
     */
    private final JPanel row=new JPanel(new FlowLayout(FlowLayout.LEFT,10,0));
    private final JScrollPane slide=new JScrollPane(row,ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    DesktopFileCards(Desktop app) {
        super(new BorderLayout());this.app=app;
        // A drawer over the foot of the page: the paper's own colour and a rule on top, so the writing it covers
        // does not show through between the cards.
        setOpaque(true);setBackground(DesktopUi.PAPER);setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,DesktopUi.LINE),BorderFactory.createEmptyBorder(10,38,0,38)));setVisible(false);
        row.setOpaque(false);row.setBorder(BorderFactory.createEmptyBorder(0,0,14,0));
        slide.setOpaque(false);slide.getViewport().setOpaque(false);slide.setBorder(BorderFactory.createEmptyBorder());
        slide.getHorizontalScrollBar().setUnitIncrement(40);
        slide.getHorizontalScrollBar().putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE,"width:8;thumbInsets:2,0,0,0");
        slide.setWheelScrollingEnabled(false);
        slide.addMouseWheelListener(e->{JScrollBar bar=slide.getHorizontalScrollBar();bar.setValue(bar.getValue()+(int)Math.round(e.getPreciseWheelRotation()*60));});
        add(slide);
    }

    private int drawn=-1;
    /**
     * Folded away unless asked for. A row of cards under every note took a fifth of the window from the writing,
     * whether or not anybody wanted the files just then; now the paperclip holds them, and they slide up over
     * the foot of the page while the pointer is on it or on them.
     */
    private boolean open;
    void open(boolean wanted){open=wanted;setVisible(open&&drawn>0);revalidate();repaint();if(getParent()!=null)getParent().revalidate();}
    boolean isOpen(){return open&&isVisible();}
    /** Whether these cards already show this many files of this note: nothing to draw again. */
    boolean shows(String note,int count){return showing.equals(note==null?"":note)&&drawn==count;}

    /** The cards for this note, drawn again: thumbnails are made off the event thread. */
    void show(String note) {
        if(!showing.equals(note==null?"":note))stopPlaying();
        showing=note==null?"":note;
        if(note==null){row.removeAll();setVisible(false);revalidate();return;}
        app.disk.submit(()->{
            List<NoteStore.Held> files=app.store.filesOf(NoteStore.Branch.Kind.PAGE,note);
            java.util.Map<String,BufferedImage> thumbs=new java.util.HashMap<>();
            java.util.Map<String,Long> lengths=new java.util.HashMap<>();
            for(NoteStore.Held file:files)if(picture(file))try{thumbs.put(file.id,thumbnail(file));}catch(Exception unreadable){/* shown by its kind */}
            // How long each recording is, read from its own bytes, so the card says it before ▶ is pressed.
            for(NoteStore.Held file:files)if(Recording.audio(file.name,file.kind)&&file.bytes<=Attachment.LIMIT)try{lengths.put(file.id,Recording.millis(bytes(file)));}catch(Exception unreadable){/* no length said */}
            return new Object[]{files,thumbs,app.store.fileStates(note,Node.onlyMine(app.context)),lengths};
        },loaded->{
            if(!showing.equals(note))return;
            @SuppressWarnings("unchecked") List<NoteStore.Held> files=(List<NoteStore.Held>)loaded[0];
            @SuppressWarnings("unchecked") java.util.Map<String,BufferedImage> thumbs=(java.util.Map<String,BufferedImage>)loaded[1];
            @SuppressWarnings("unchecked") java.util.Map<String,String> states=(java.util.Map<String,String>)loaded[2];
            @SuppressWarnings("unchecked") java.util.Map<String,Long> lengths=(java.util.Map<String,Long>)loaded[3];
            row.removeAll();faces.clear();
            for(NoteStore.Held file:files)row.add(card(file,thumbs.get(file.id),states.getOrDefault(file.id,""),lengths.getOrDefault(file.id,-1L)));
            // Still playing if it is still here: the player finds its new card by the file's id.
            if(playing!=null&&!faces.containsKey(playing))stopPlaying();
            drawn=files.size();
            setVisible(open&&drawn>0);revalidate();repaint();if(getParent()!=null)getParent().revalidate();
        },app::failed);
    }

    static boolean picture(NoteStore.Held file) {
        String name=file.name.toLowerCase(java.util.Locale.ROOT);
        return (file.kind!=null&&file.kind.startsWith("image/"))||name.endsWith(".png")||name.endsWith(".jpg")||name.endsWith(".jpeg")||name.endsWith(".gif")||name.endsWith(".bmp");
    }

    /** A picture, read through the lock if there is one, made small. */
    private BufferedImage thumbnail(NoteStore.Held file) throws Exception{return thumbnail(file,132,72);}
    /** The same at another size. */
    BufferedImage thumbnail(NoteStore.Held file,int w,int h) throws Exception {
        if(file.bytes>20L*1024*1024)return null;
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        DesktopFiles.copyOut(app.context,app.store.fileFor(file.id).toPath(),bytes);
        BufferedImage whole=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes.toByteArray()));
        if(whole==null)return null;
        double scale=Math.max((double)w/whole.getWidth(),(double)h/whole.getHeight());
        BufferedImage small=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=small.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int sw=(int)Math.round(whole.getWidth()*scale),sh=(int)Math.round(whole.getHeight()*scale);
        g.drawImage(whole,(w-sw)/2,(h-sh)/2,sw,sh,null);g.dispose();
        return small;
    }

    private JComponent card(NoteStore.Held file,BufferedImage thumb,String going,long millis) {
        JPanel card=new JPanel(new BorderLayout()){
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(DesktopUi.CARD);g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                g.setColor(DesktopUi.LINE);g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);g.dispose();
            }
        };
        card.setOpaque(false);card.setPreferredSize(new Dimension(148,122));card.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        // The picture, or the file's kind on a tile.
        JComponent face;
        if(thumb!=null){JLabel image=new JLabel(new ImageIcon(thumb));face=image;}
        else if(Recording.audio(file.name,file.kind))face=listen(file,millis);
        else {
            String kind=extension(file.name);
            JLabel tile=new JLabel(kind,SwingConstants.CENTER){
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(new Color(232,239,234));g.fillRoundRect(0,0,getWidth(),getHeight(),8,8);g.dispose();super.paintComponent(g0);
                }
            };
            tile.setFont(DesktopUi.BODY.deriveFont(Font.BOLD,15f));tile.setForeground(Desktop.ACCENT);tile.setPreferredSize(new Dimension(132,72));face=tile;
        }
        // The name and size, and the cross beside them.
        JLabel name=new JLabel(file.name);name.setFont(DesktopUi.BODY.deriveFont(12.5f));name.setForeground(Desktop.INK);
        // Its size, and where it is while it has not reached everybody the note is shared with - in the accent,
        // and gone once it has.
        JLabel size=new JLabel(going.isEmpty()?Attachment.size(file.bytes):Attachment.size(file.bytes)+" · "+going);
        size.setFont(DesktopUi.BODY.deriveFont(11.5f));size.setForeground(going.isEmpty()?Desktop.QUIET:Desktop.ACCENT);
        if(!going.isEmpty())size.setToolTipText(going);
        JPanel words=new JPanel(new BorderLayout());words.setOpaque(false);words.add(name,BorderLayout.NORTH);words.add(size,BorderLayout.SOUTH);
        JButton delete=new JButton("×");delete.setFocusPainted(false);delete.setMargin(new Insets(0,4,0,4));delete.setFont(DesktopUi.BODY.deriveFont(15f));
        delete.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        delete.setToolTipText("Delete this attachment");delete.getAccessibleContext().setAccessibleName("Delete "+file.name);
        delete.setEnabled(app.page.isEditable());
        delete.addActionListener(e->delete(file));
        JPanel foot=new JPanel(new BorderLayout(4,0));foot.setOpaque(false);foot.setBorder(BorderFactory.createEmptyBorder(6,0,0,0));
        foot.add(words);foot.add(delete,BorderLayout.EAST);
        card.add(face);card.add(foot,BorderLayout.SOUTH);
        card.setToolTipText("Open "+file.name);card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter open=new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e))open(file);}};
        card.addMouseListener(open);face.addMouseListener(open);words.addMouseListener(open);
        DesktopMenus.onRightClick(card,e->menu(file).show(e.getComponent(),e.getX(),e.getY()));
        return card;
    }

    /** What can be done to one attached file: the card's click and cross, and what the paperclip and the Send box do. */
    JPopupMenu menu(NoteStore.Held file) {
        JPopupMenu menu=new JPopupMenu();
        JMenuItem open=new JMenuItem("Open");open.addActionListener(e->open(file));menu.add(open);
        JMenuItem copy=new JMenuItem("Save a copy…");copy.addActionListener(e->app.saveCopy(file));menu.add(copy);
        JMenuItem send=new JMenuItem("Send to a device…");send.addActionListener(e->send(file));menu.add(send);
        menu.addSeparator();
        JMenuItem delete=new JMenuItem("Delete");delete.setEnabled(app.page.isEditable());delete.addActionListener(e->delete(file));menu.add(delete);
        return menu;
    }

    /**
     * Straight to one of the devices, in no note, as files dropped on the window go: a copy is taken out of the
     * notebook - opened on the way if it is locked - for the Send box, and deleted when Mininotes closes.
     */
    void send(NoteStore.Held file) {
        app.disk.submit(()->{
            Path room=Files.createTempDirectory("mininotes-");room.toFile().deleteOnExit();
            Path copy=room.resolve(DesktopFiles.onDisk(file.name));
            try(java.io.OutputStream out=Files.newOutputStream(copy)){DesktopFiles.copyOut(app.context,app.store.fileFor(file.id).toPath(),out);}
            copy.toFile().deleteOnExit();
            return copy;
        },copy->DesktopDrops.send(app,List.of(copy)),app::failed);
    }

    // ---- listening, without leaving the note ------------------------------------------------------------------

    /** What one sound's card shows while it plays: ▶ or ⏸, where it has got to, and a thin line for how far. */
    private static final class Face {
        final long millis;final JButton button=new JButton();final JLabel time=new JLabel();
        float done;
        final JComponent line=new JComponent(){
            @Override protected void paintComponent(Graphics g) {
                g.setColor(DesktopUi.LINE);g.fillRect(0,0,getWidth(),getHeight());
                g.setColor(Desktop.ACCENT);g.fillRect(0,0,Math.round(getWidth()*done),getHeight());
            }
        };
        Face(long millis){this.millis=millis;line.setPreferredSize(new Dimension(10,3));}
    }
    private final java.util.Map<String,Face> faces=new java.util.HashMap<>();
    /** One sound at a time, as a player does: the file playing, and Java Sound's clip of it. */
    private String playing;
    private javax.sound.sampled.Clip clip;
    private final javax.swing.Timer tick=new javax.swing.Timer(100,e->ticked());

    /**
     * A sound's face: ▶ and how long it is. A WAV - what the PC records - plays here, on the card. Java Sound
     * cannot decode what the phone records (AAC) or an MP3, and no small decoder the GPL can take in exists, so
     * those go to the PC's own player, as a click on any other file does; the card says so before it is pressed.
     */
    private JComponent listen(NoteStore.Held file,long millis) {
        boolean here=Recording.wav(file.name,file.kind);
        JPanel tile=new JPanel(new BorderLayout()){
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(232,239,234));g.fillRoundRect(0,0,getWidth(),getHeight(),8,8);g.dispose();
            }
        };
        tile.setOpaque(false);tile.setPreferredSize(new Dimension(132,72));tile.setBorder(BorderFactory.createEmptyBorder(8,6,10,10));
        Face face=new Face(millis);
        JButton play=face.button;play.setFocusPainted(false);play.setMargin(new Insets(2,4,2,4));
        play.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        play.setToolTipText(here?null:"Opens in your player");
        play.addActionListener(e->{if(here)toggle(file);else open(file);});
        face.time.setFont(DesktopUi.BODY.deriveFont(Font.BOLD,15f));face.time.setForeground(Desktop.ACCENT);
        JPanel top=new JPanel(new FlowLayout(FlowLayout.LEFT,2,0));top.setOpaque(false);top.add(play);top.add(face.time);
        tile.add(top,BorderLayout.NORTH);
        if(here)tile.add(face.line,BorderLayout.SOUTH);
        else{JLabel away=new JLabel("Opens in your player");away.setFont(DesktopUi.BODY.deriveFont(11.5f));away.setForeground(Desktop.QUIET);away.setBorder(BorderFactory.createEmptyBorder(4,8,0,0));tile.add(away,BorderLayout.SOUTH);}
        faces.put(file.id,face);draw(file.id,face,file.name);
        return tile;
    }

    /** ▶ on a WAV: plays it from its start, or pauses it, or goes on from where it was paused. */
    private void toggle(NoteStore.Held file) {
        if(file.id.equals(playing)&&clip!=null) {
            if(clip.isRunning())clip.stop();
            else{if(clip.getFramePosition()>=clip.getFrameLength())clip.setFramePosition(0);clip.start();tick.start();}
            ticked();return;
        }
        stopPlaying();String id=file.id;playing=id;
        app.disk.submit(()->{
            javax.sound.sampled.AudioInputStream in=javax.sound.sampled.AudioSystem.getAudioInputStream(new java.io.ByteArrayInputStream(bytes(file)));
            javax.sound.sampled.AudioFormat was=in.getFormat();
            // μ-law, what the PC records, is widened to plain samples: a sound card's line takes nothing else.
            if(was.getEncoding()!=javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED)
                in=javax.sound.sampled.AudioSystem.getAudioInputStream(new javax.sound.sampled.AudioFormat(was.getSampleRate(),16,was.getChannels(),true,false),in);
            javax.sound.sampled.Clip made=javax.sound.sampled.AudioSystem.getClip();made.open(in);
            return made;
        },made->{
            if(!id.equals(playing)){made.close();return;}
            clip=made;clip.start();tick.start();ticked();
        },e->{playing=null;app.status.setText("This PC could not play "+file.name+". Open it from its menu to use another player.");});
    }

    private void ticked() {
        Face face=playing==null?null:faces.get(playing);
        if(clip!=null&&!clip.isRunning()&&clip.getFramePosition()>=clip.getFrameLength()){clip.setFramePosition(0);tick.stop();}
        if(clip!=null&&!clip.isRunning())tick.stop();
        if(face!=null)draw(playing,face,null);
    }

    /** One card as it stands: ⏸ while it plays, where it is while it is part way, its length otherwise. */
    private void draw(String id,Face face,String name) {
        boolean mine=id.equals(playing)&&clip!=null,running=mine&&clip.isRunning();
        long at=mine?clip.getMicrosecondPosition()/1000:0,length=mine?clip.getMicrosecondLength()/1000:face.millis;
        face.button.setIcon(running?PAUSE:PLAY);
        if(name!=null)face.button.getAccessibleContext().setAccessibleName("Play "+name);
        face.button.getAccessibleContext().setAccessibleDescription(running?"Pause":"Play");
        face.time.setText(at>0?Recording.clock(at):length>=0?Recording.clock(length):"");
        face.done=length>0?Math.min(1f,(float)at/length):0;face.line.repaint();
    }

    /** Stopped and let go: another note shown, the card gone, or another sound pressed. */
    void stopPlaying() {
        String was=playing;playing=null;tick.stop();
        if(clip!=null){clip.stop();clip.close();clip=null;}
        Face face=was==null?null:faces.get(was);
        if(face!=null)draw(was,face,null);
    }

    /** A kept file's own bytes, opened through the lock if there is one. */
    private byte[] bytes(NoteStore.Held file) throws java.io.IOException {
        ByteArrayOutputStream all=new ByteArrayOutputStream();DesktopFiles.copyOut(app.context,app.store.fileFor(file.id).toPath(),all);return all.toByteArray();
    }

    private static final Icon PLAY=shape(false),PAUSE=shape(true);
    /** ▶ and ⏸ drawn, in the accent: as characters Windows gives them its emoji colours. */
    private static Icon shape(boolean pause) {
        return new Icon(){
            public int getIconWidth(){return 16;}public int getIconHeight(){return 16;}
            public void paintIcon(Component c,Graphics g0,int x,int y) {
                Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(Desktop.ACCENT);
                if(pause){g.fillRoundRect(x+3,y+2,4,12,2,2);g.fillRoundRect(x+9,y+2,4,12,2,2);}
                else g.fill(new java.awt.geom.Path2D.Float(){{moveTo(x+3,y+1.5);lineTo(x+14.5,y+8);lineTo(x+3,y+14.5);closePath();}});
                g.dispose();
            }
        };
    }

    static String extension(String name){return DropList.type(name);}

    /** Opened in the program this PC uses for it, from a copy in Mininotes' own temporary folder, emptied when it closes. */
    private void open(NoteStore.Held file) {
        app.disk.submit(()->{
            Path folder=Path.of(System.getProperty("java.io.tmpdir"),"Mininotes-open");Files.createDirectories(folder);
            folder.toFile().deleteOnExit();
            Path copy=folder.resolve(DesktopFiles.onDisk(file.name));
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            DesktopFiles.copyOut(app.context,app.store.fileFor(file.id).toPath(),bytes);
            Files.write(copy,bytes.toByteArray());copy.toFile().deleteOnExit();
            return copy;
        },copy->{
            try{java.awt.Desktop.getDesktop().open(copy.toFile());}
            catch(Exception none){app.status.setText("No program on this PC opens "+file.name+". Save a copy from the paperclip.");}
        },app::failed);
    }

    /** Deleted from this note on this PC, after one question: there is no bin for attachments. */
    private void delete(NoteStore.Held file) {
        if(!DesktopUi.confirm(app.frame,"Delete “"+file.name+"”?","It is removed from this note on this PC. It cannot be put back.","Delete",true))return;
        String note=showing;
        app.disk.submit(()->{app.store.drop(file.id);return null;},done->{app.status.setText("Attachment deleted");show(note);app.refreshStanding();app.filesChanged(note);},app::failed);
    }
}
