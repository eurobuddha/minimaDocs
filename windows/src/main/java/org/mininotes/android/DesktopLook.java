package org.mininotes.android;

import org.mininotes.desktop.platform.content.Context;
import java.awt.*;
import java.awt.event.*;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import javax.swing.*;

/**
 * How the pad looks, as the phone decides it: the reading ladder of ten rungs, the eight colours a thing can
 * be given, and how strongly those colours land. The same rungs, the same colour numbers and the same ten
 * tones as the phone, kept under the same names, so "step 5", "Red" and "strength 4" mean one thing on both.
 */
final class DesktopLook {
    private DesktopLook(){}

    /**
     * The phone's ladder, in its own points. The PC draws each rung 18/17 as big, so the middle rung is the
     * 18-point page it always had and the ladder moves five rungs either way from there, as on the phone.
     */
    static final int[] PHONE={11,12,14,15,17,19,21,24,26,29};
    static final int FIRST=4;
    static float size(int rung){return PHONE[clamp(rung)]*18f/17f;}
    static int clamp(int rung){return Math.max(0,Math.min(PHONE.length-1,rung));}

    /**
     * The rung kept in the settings, as the phone keeps it ("rung"). A PC set before the ladder had four sizes
     * under "textSize"; each of those is taken to the rung nearest it, so nobody's page jumps.
     */
    static int rung(Context context) {
        var kept=context.getSharedPreferences("settings",0);
        try{String now=kept.getString("rung","");if(!now.isEmpty())return clamp(Integer.parseInt(now));}catch(NumberFormatException e){/* the older setting, or the middle */}
        try{int old=Integer.parseInt(kept.getString("textSize","1"));return new int[]{2,4,6,7}[Math.max(0,Math.min(3,old))];}
        catch(NumberFormatException e){return FIRST;}
    }
    static void keepRung(Context context,int rung){context.getSharedPreferences("settings",0).edit().putString("rung",Integer.toString(clamp(rung))).apply();}

    /** How strongly colours land, kept as the phone keeps it ("tone"): one setting for the whole pad. */
    static int tone(Context context) {
        try{return Math.max(0,Math.min(Tint.TONES.length-1,Integer.parseInt(context.getSharedPreferences("settings",0).getString("tone",Integer.toString(Tint.FIRST_TONE)))));}
        catch(NumberFormatException e){return Tint.FIRST_TONE;}
    }
    static void keepTone(Context context,int tone){context.getSharedPreferences("settings",0).edit().putString("tone",Integer.toString(tone)).apply();}

    /** A colour washed over a surface at the pad's tone, never so strong that the writing on it stops reading. */
    static Color wash(int colour,Color ground,float base,float most,int tone) {
        if(!Tint.known(colour))return ground;
        return new Color(Tint.over(colour,ground.getRGB(),Tint.weigh(base,tone,most),false));
    }
    static Color of(int colour){return new Color(Tint.of(colour,false),true);}

    /**
     * A round of one colour for a menu line. No colour is the paper with a rim, so every line has a round in the
     * same place and the names stand in one column; the one a thing has is ringed, as on the phone.
     */
    static Icon swatch(int colour,int size,boolean chosen) {
        return new Icon(){
            public int getIconWidth(){return size;}public int getIconHeight(){return size;}
            public void paintIcon(Component c,Graphics g0,int x,int y) {
                Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int in=chosen?3:1;
                g.setColor(Tint.known(colour)?of(colour):DesktopUi.CARD);g.fillOval(x+in,y+in,size-2*in,size-2*in);
                if(!Tint.known(colour)){g.setColor(DesktopUi.LINE.darker());g.setStroke(new BasicStroke(1f));g.drawOval(x+in,y+in,size-2*in-1,size-2*in-1);}
                if(chosen){g.setColor(Desktop.INK);g.setStroke(new BasicStroke(1.6f));g.drawOval(x+1,y+1,size-3,size-3);}
                g.dispose();
            }
        };
    }

    // ---- writing colours (see Writers) -----------------------------------------------------------------------------

    /** Whether writers are drawn in their colours: "Show who wrote what", on unless it was turned off. */
    static boolean whoWrote(Context context){return !"off".equals(context.getSharedPreferences("settings",0).getString("whoWrote","on"));}
    static void keepWhoWrote(Context context,boolean on){context.getSharedPreferences("settings",0).edit().putString("whoWrote",on?"on":"off").apply();}

    /**
     * The colours a writer can be drawn in, as rounds in a row with the one in use ringed, the way a thing's colour is
     * chosen. The first is how they are drawn when nobody chose: for you, no colour - the ordinary ink, as the first
     * of a thing's colours is the paper; for anybody else, their own colour, the one every device gives them, set in a
     * round of paper because it is theirs rather than a choice. A click is the choice.
     */
    static JPanel inks(Writers.Palette palette,String writer,IntConsumer choose) {
        boolean you=Writers.ME.equals(palette.person(writer));
        String who=you?Writers.ME:palette.person(writer);
        int own=you?Tint.NONE:Writers.automatic(who);
        int[] chosen={you?palette.mine:palette.chose(who)?palette.colourOf(who):Tint.NONE};
        JPanel row=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));row.setOpaque(false);
        for(int c=0;c<Tint.count();c++) {
            int colour=c;
            String name=c==Tint.NONE?(you?"Ink, no colour":"Their own colour, "+Tint.NAMES[own].toLowerCase(java.util.Locale.ROOT)):Tint.NAMES[c];
            JLabel round=new JLabel(new Icon(){
                public int getIconWidth(){return 24;}public int getIconHeight(){return 24;}
                public void paintIcon(Component on,Graphics g0,int x,int y) {
                    boolean ringed=chosen[0]==colour;
                    if(colour!=Tint.NONE||you){swatch(colour,24,ringed).paintIcon(on,g0,x,y);return;}
                    swatch(Tint.NONE,24,ringed).paintIcon(on,g0,x,y);
                    Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(of(own));g.fillOval(x+7,y+7,10,10);g.dispose();
                }
            });
            round.setToolTipText(name);round.getAccessibleContext().setAccessibleName(name);
            round.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            round.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){
                if(!SwingUtilities.isLeftMouseButton(e)||chosen[0]==colour)return;
                chosen[0]=colour;row.repaint();choose.accept(colour);
            }});
            row.add(round);
        }
        return row;
    }

    /**
     * Colour ▸, for one thing: the nine choices with the one it has ringed, and under them how strongly colours
     * land, drawn in this thing's colour. Choosing repaints the pad at once, so it is chosen by looking.
     */
    static JMenu colours(int now,IntConsumer choose,IntSupplier tone,IntConsumer setTone) {
        JMenu menu=new JMenu("Colour");
        if(Tint.known(now))menu.setIcon(swatch(now,14,false));
        for(int c=0;c<Tint.count();c++) {
            final int colour=c;boolean chosen=c==now||c==Tint.NONE&&!Tint.known(now);
            JMenuItem one=new JMenuItem(Tint.NAMES[c],swatch(c,18,chosen));
            one.getAccessibleContext().setAccessibleName(Tint.NAMES[c]+(chosen?", chosen":""));
            one.addActionListener(a->choose.accept(colour));menu.add(one);
        }
        menu.addSeparator();
        menu.add(strength(Tint.known(now)?now:Tint.NONE,tone,setTone));
        return menu;
    }

    /**
     * Text size ▸, for one note: the ladder at the rung the note is read at, and under it whether it simply
     * follows the size every other note is read at. A rung chosen is the note's own; Same as other notes gives
     * it back to the device's. The page moves as it is chosen, so it is chosen by looking.
     */
    static JMenu sizes(int own,int device,IntConsumer choose) {
        JMenu menu=new JMenu("Text size");
        int[] now={own};
        JCheckBoxMenuItem same=new JCheckBoxMenuItem("Same as other notes",Reading.followsDevice(own));
        JComponent ladder=ladder(()->Reading.of(now[0],device),rung->{now[0]=Reading.clamp(rung);choose.accept(now[0]);same.setSelected(false);});
        same.addActionListener(a->{if(Reading.followsDevice(now[0])){same.setSelected(true);return;}now[0]=Reading.NONE;choose.accept(Reading.NONE);});
        menu.add(ladder);menu.addSeparator();menu.add(same);
        return menu;
    }

    /** "Colour strength": ten tones, pastel at the left, the colour itself at the right. */
    static JComponent strength(int colour,IntSupplier tone,IntConsumer setTone) {
        JPanel holder=new JPanel(new BorderLayout(0,4));holder.setOpaque(false);holder.setBorder(BorderFactory.createEmptyBorder(6,16,10,16));
        JLabel what=new JLabel("Colour strength");what.setFont(DesktopUi.BODY.deriveFont(12.5f));what.setForeground(Desktop.QUIET);
        holder.add(what,BorderLayout.NORTH);holder.add(new Band(colour,tone,setTone));
        return holder;
    }

    /**
     * The tones as the washes they would make, in the colour of the thing the menu is about, with the one in use
     * held by a round grip. With no colour there is nothing to show a tone of, so the band runs paper to ink.
     */
    private static final class Band extends JPanel {
        private final int colour;private final IntSupplier tone;private final IntConsumer setTone;
        Band(int colour,IntSupplier tone,IntConsumer setTone) {
            this.colour=colour;this.tone=tone;this.setTone=setTone;setOpaque(false);
            setPreferredSize(new Dimension(200,28));setFocusable(true);setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            said();
            MouseAdapter hand=new MouseAdapter(){
                public void mousePressed(MouseEvent e){take(e.getX());}
                public void mouseDragged(MouseEvent e){take(e.getX());}
            };
            addMouseListener(hand);addMouseMotionListener(hand);
            addKeyListener(new KeyAdapter(){public void keyPressed(KeyEvent e){
                if(e.getKeyCode()==KeyEvent.VK_LEFT)go(tone.getAsInt()-1);else if(e.getKeyCode()==KeyEvent.VK_RIGHT)go(tone.getAsInt()+1);}});
        }
        private int step(){return Tint.TONES.length;}
        private void take(int x){int w=Math.max(1,getWidth()-getHeight());go(Math.round((x-getHeight()/2f)*(step()-1)/(float)w));}
        private void go(int to){to=Math.max(0,Math.min(step()-1,to));if(to==tone.getAsInt())return;setTone.accept(to);said();repaint();}
        private void said(){getAccessibleContext().setAccessibleName("Colour strength, "+(tone.getAsInt()+1)+" of "+step()+". Pastel at the left, the colour itself at the right.");setToolTipText("Colour strength "+(tone.getAsInt()+1)+" of "+step());}
        private Color at(int t){return Tint.known(colour)?new Color(Tint.over(colour,DesktopUi.CARD.getRGB(),Tint.weigh(0.22f,t,0.92f),false)):DesktopUi.mix(DesktopUi.CARD,Desktop.INK,0.06f+0.5f*t/(step()-1));}
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int h=getHeight(),w=getWidth(),track=h-10,top=5,r=h/2;
            float[] where=new float[step()];Color[] colours=new Color[step()];
            for(int t=0;t<step();t++){where[t]=t/(float)(step()-1);colours[t]=at(t);}
            Shape pill=new java.awt.geom.RoundRectangle2D.Float(r-track/2f,top,w-2*r+track,track,track,track);
            g.setPaint(new LinearGradientPaint(r,0,Math.max(r+1,w-r),0,where,colours));g.fill(pill);
            g.setColor(DesktopUi.LINE);g.draw(pill);
            int now=tone.getAsInt(),x=r+Math.round((w-2*r)*now/(float)(step()-1));
            g.setColor(at(now));g.fillOval(x-r+2,2,h-4,h-4);
            g.setColor(Desktop.INK);g.setStroke(new BasicStroke(2f));g.drawOval(x-r+2,2,h-5,h-5);
            g.dispose();
        }
    }

    /**
     * The reading ladder, as the phone draws it: A− and A+ either side of ten rungs, each rung taller than the
     * last and the ones up to the page's size filled. Every rung can be clicked; A− and A+ step one at a time.
     */
    static JComponent ladder(IntSupplier rung,IntConsumer setRung) {
        JPanel row=new JPanel(new BorderLayout(8,0));row.setOpaque(false);row.setBorder(BorderFactory.createEmptyBorder(4,10,4,10));
        JButton smaller=step("A−","Smaller text",14f),bigger=step("A+","Bigger text",19f);
        JPanel rungs=new JPanel(){
            {setOpaque(false);setPreferredSize(new Dimension(170,30));setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
             addMouseListener(new MouseAdapter(){public void mousePressed(MouseEvent e){int each=Math.max(1,getWidth()/PHONE.length);setRung.accept(clamp(e.getX()/each));}});}
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int each=getWidth()/PHONE.length,at=rung.getAsInt();
                for(int s=0;s<PHONE.length;s++){int high=7+s*2,x=s*each+each/2-1;g.setColor(s<=at?Desktop.ACCENT:DesktopUi.LINE);g.fillRoundRect(x,getHeight()-6-high,3,high,2,2);}
                g.dispose();
            }
        };
        Runnable mark=()->{int at=rung.getAsInt();smaller.setEnabled(at>0);bigger.setEnabled(at<PHONE.length-1);
            String said="Text size, step "+(at+1)+" of "+PHONE.length;rungs.setToolTipText(said);rungs.getAccessibleContext().setAccessibleName(said);rungs.repaint();};
        smaller.addActionListener(e->{setRung.accept(clamp(rung.getAsInt()-1));mark.run();});
        bigger.addActionListener(e->{setRung.accept(clamp(rung.getAsInt()+1));mark.run();});
        rungs.addMouseListener(new MouseAdapter(){public void mouseReleased(MouseEvent e){mark.run();}});
        mark.run();
        row.add(smaller,BorderLayout.WEST);row.add(rungs);row.add(bigger,BorderLayout.EAST);
        return row;
    }
    private static JButton step(String words,String name,float size) {
        JButton b=new JButton(words);b.setFont(DesktopUi.BODY.deriveFont(Font.BOLD,size));b.setForeground(Desktop.INK);b.setFocusPainted(false);
        b.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        b.setToolTipText(name);b.getAccessibleContext().setAccessibleName(name);b.setMargin(new Insets(2,6,2,6));
        return b;
    }
}
