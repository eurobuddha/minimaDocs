package org.mininotes.android;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import javax.sound.sampled.*;
import javax.swing.*;

/**
 * A recording made on the note page, kept as one of its attachments: the microphone beside the paperclip starts
 * it, and this is the slim bar that says it is going - a red dot, the time, and Stop.
 *
 * <p>Java has no AAC encoder, and a small pure-Java one with a licence the GPL can take in does not exist, so
 * the PC writes WAV. Not plain 16-bit WAV: μ-law, one byte a sample at 16 kHz, the telephone's own way of
 * keeping speech. It is half the size - about 17 minutes under the 16 MB a file may be and still travel,
 * against 8 - and the phone, Java Sound and every player on Windows play it without anything added.
 *
 * <p>The sound is held in memory until Stop and written once, sealed if the notebook is locked, so no plain
 * copy of it ever lies on the disk.
 */
final class DesktopRecorder extends JPanel {
    /** Where the sound comes from: 16-bit signed little-endian samples, one channel, at {@link Recording#PC_RATE}. */
    interface Microphone extends AutoCloseable {
        /** Blocks until some sound is there; how many bytes. */
        int read(byte[] into) throws IOException;
        @Override void close();
    }
    interface Source { Microphone open() throws Exception; }

    static final AudioFormat HEARD=new AudioFormat(Recording.PC_RATE,16,1,true,false);
    /** The bytes before the sound in the WAV written here: RIFF, a format chunk with its size word, a fact chunk, the data chunk's head. */
    static final int HEADER=12+26+12+8;

    /** The PC's own microphone. Tests put a made-up one here: nothing in a test opens the real one. */
    Source source=DesktopRecorder::microphone;

    private final Desktop app;
    private final JLabel time=new JLabel("0:00");
    private final javax.swing.Timer tick=new javax.swing.Timer(200,e->tick());
    private Take take;

    DesktopRecorder(Desktop app) {
        super(new FlowLayout(FlowLayout.LEFT,8,0));this.app=app;
        setOpaque(false);setVisible(false);setBorder(BorderFactory.createEmptyBorder(4,12,4,6));
        JLabel dot=new JLabel(dot());dot.getAccessibleContext().setAccessibleName("Recording");
        time.setFont(DesktopUi.BODY.deriveFont(Font.BOLD,14f));time.setForeground(Desktop.INK);
        JLabel limit=new JLabel(Recording.limit(Recording.PC_RATE));limit.setFont(DesktopUi.BODY.deriveFont(12f));limit.setForeground(Desktop.QUIET);
        JButton cancel=new JButton("Cancel");cancel.setFocusPainted(false);cancel.setToolTipText("Stop and throw this recording away");
        cancel.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        cancel.setForeground(Desktop.QUIET);cancel.addActionListener(e->cancel());
        JButton stop=DesktopUi.primary("Stop",this::stop);stop.setToolTipText("Stop, and keep it with this note");
        add(dot);add(time);add(limit);add(cancel);add(stop);
    }

    /** A card over the foot of the page, so the writing behind it does not show through between the words. */
    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(DesktopUi.CARD);g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,14,14);
        g.setColor(DesktopUi.LINE);g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,14,14);g.dispose();
    }

    static Icon dot() {
        return new Icon(){
            public int getIconWidth(){return 12;}public int getIconHeight(){return 12;}
            public void paintIcon(Component c,Graphics g0,int x,int y) {
                Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(214,48,49));g.fillOval(x+1,y+1,10,10);g.dispose();
            }
        };
    }

    boolean recording(){return take!=null;}

    /** Starts at once, into the open note; a second press while it is going does nothing. */
    void start() {
        String note=app.noteShown();
        if(take!=null||note==null||!app.page.isEditable())return;
        Microphone mic;
        try{mic=source.open();}
        catch(Exception none) {
            // No line at all: nothing plugged in, or Windows has none switched on.
            DesktopUi.tell(app.frame,"No microphone",DesktopUi.note("Mininotes could not find a microphone on this PC. Plug one in, or choose one in Windows Settings → System → Sound → Input, then press the microphone again.",360,Desktop.INK,DesktopUi.BODY));
            return;
        }
        take=new Take(note,Recording.name(System.currentTimeMillis(),"wav"),mic);
        time.setText("0:00");setVisible(true);app.recordingShown(true);
        Thread going=new Thread(take,"mininotes-recording");going.setDaemon(true);take.thread=going;going.start();tick.start();
    }

    /** Stop, and keep what was heard with the note it was started in. */
    void stop(){Take was=end();if(was!=null)keep(was,null,false);}

    /** Stop, and throw it away. */
    void cancel(){if(end()!=null)app.status.setText("Recording thrown away");}

    /** The note on the page is changing: a recording going on for another note is stopped and kept there. */
    void leaving(String next){if(take!=null&&!take.note.equals(next))stop();}

    /** Mininotes is closing or locking: kept, and nothing drawn again, since the window is going. */
    void finish(){Take was=end();if(was!=null)keep(was,null,true);}

    private Take end() {
        Take was=take;if(was==null)return null;
        take=null;tick.stop();setVisible(false);app.recordingShown(false);
        was.stop=true;
        // A read waits at most one small buffer, so this is a moment.
        try{was.thread.join(2000);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        return was;
    }

    private void tick() {
        Take now=take;if(now==null)return;
        time.setText(Recording.clock(now.heard*1000L/Recording.PC_RATE));
        if(now.ended==null)return;
        String why=now.ended;end();
        if(why.equals(Take.CAPPED))keep(now,Recording.CAPPED,false);
        else if(why.equals(Take.SILENT))silent();
        else app.status.setText("The recording stopped: "+why+". Nothing was kept.");
    }

    /**
     * Only silence, not even the hiss a microphone always has: that is Windows keeping it from Mininotes. It is
     * thrown away and said, with the one place that changes it.
     */
    private void silent() {
        if(DesktopUi.confirm(app.frame,"Windows is not letting Mininotes hear the microphone",
            "Nothing was recorded. In Windows Settings → Privacy & security → Microphone, turn on “Microphone access” and “Let desktop apps access your microphone”, then press the microphone again.",
            "Open microphone settings",false))
            try{java.awt.Desktop.getDesktop().browse(java.net.URI.create("ms-settings:privacy-microphone"));}
            catch(Exception unopened){app.status.setText("Open Windows Settings → Privacy & security → Microphone");}
    }

    /** Written once as a WAV, sealed on the way if the notebook is locked, then its row: the ordinary attachment path. */
    private void keep(Take was,String said,boolean quiet) {
        byte[] sound=was.sound.toByteArray();
        if(sound.length<Recording.PC_RATE/4){if(!quiet)app.status.setText("Too short to keep: nothing was recorded");return;}
        byte[] whole=wav(sound);
        if(!quiet)app.status.setText("Keeping the recording…");
        app.disk.submit(()->{
            String id=UUID.randomUUID().toString();Path dest=app.store.fileFor(id).toPath();
            DesktopFiles.keep(app.context,whole,dest);
            try{app.store.keep(new NoteStore.Held(id,was.note,was.name,"audio/wav",whole.length,System.currentTimeMillis()));}
            catch(Exception failure){Files.deleteIfExists(dest);throw failure;}
            return null;
        },done->{
            if(quiet)return;
            app.status.setText(said!=null?said:"Recording kept · "+Recording.clock(sound.length*1000L/Recording.PC_RATE)+" · "+Attachment.size(whole.length));
            app.attachmentsChanged(was.note);
        },quiet?e->{}:app::failed);
    }

    /** The recording itself, heard on a thread of its own so the window never waits on the microphone. */
    private static final class Take implements Runnable {
        static final String CAPPED="capped",SILENT="silent";
        final String note,name;final Microphone mic;
        final ByteArrayOutputStream sound=new ByteArrayOutputStream(1<<20);
        Thread thread;
        volatile boolean stop;
        /** Why it stopped by itself, or null while it has not. */
        volatile String ended;
        /** Samples heard so far. */
        volatile long heard;
        Take(String note,String name,Microphone mic){this.note=note;this.name=name;this.mic=mic;}

        public void run() {
            byte[] part=new byte[Recording.PC_RATE/10*2];boolean anything=false;
            try(mic) {
                while(!stop) {
                    int n=mic.read(part);if(n<=0)continue;
                    for(int at=0;at+1<n;at+=2) {
                        short sample=(short)((part[at]&0xFF)|(part[at+1]<<8));
                        if(sample!=0)anything=true;
                        sound.write(mulaw(sample));
                    }
                    heard+=n/2;
                    // A second and a half of exact zeros is not a quiet room: a real microphone always hisses.
                    if(!anything&&heard>=Recording.PC_RATE*3L/2){ended=SILENT;return;}
                    if(HEADER+sound.size()+part.length/2>Recording.STOP_AT){ended=CAPPED;return;}
                }
            } catch(Exception e){ended=e.getMessage()==null?"the microphone went away":e.getMessage();}
        }
    }

    static Microphone microphone() throws Exception {
        TargetDataLine line=AudioSystem.getTargetDataLine(HEARD);
        line.open(HEARD,Recording.PC_RATE);line.start();
        return new Microphone(){
            public int read(byte[] into){return line.read(into,0,into.length);}
            public void close(){line.stop();line.close();}
        };
    }

    /** One 16-bit sample as G.711 μ-law: the loud parts kept coarse and the quiet ones fine, which is how ears hear. */
    static byte mulaw(short sample) {
        int s=sample,sign=0;
        if(s<0){s=-s;sign=0x80;}
        s=Math.min(s,32635)+0x84;
        int exponent=7;for(int mask=0x4000;(s&mask)==0&&exponent>0;mask>>=1)exponent--;
        int mantissa=(s>>(exponent+3))&0x0F;
        return (byte)~(sign|exponent<<4|mantissa);
    }

    /** μ-law samples as a WAV file: the format chunk with its size word, the fact chunk non-PCM WAVs carry, the data. */
    static byte[] wav(byte[] mulaw) {
        int n=mulaw.length,pad=n&1;
        java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(HEADER+n+pad).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(HEADER-8+n+pad).put("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        b.put("fmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(18).putShort((short)7).putShort((short)1)
            .putInt(Recording.PC_RATE).putInt(Recording.PC_RATE).putShort((short)1).putShort((short)8).putShort((short)0);
        b.put("fact".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(4).putInt(n);
        b.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(n).put(mulaw);
        return b.array();
    }
}
