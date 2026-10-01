package org.mininotes.android;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.*;

/**
 * Every page of Home at once, zoomed out (docs/HOME.md, decision 45): each page there is, small, where it lies from the
 * main one, with its icons on it, the one in view outlined. A click on a page goes there; Esc, a click beside them, or
 * Ctrl with the wheel turned back, puts it away. Also the dots under Home's grid saying which page is in view, and the
 * words that say where a page is.
 */
final class DesktopPages extends JComponent {
    private final DesktopHome home;
    /** Where each page was drawn, last time, by {x, y}: what a click is measured against; and every place a page can go. */
    private final Map<List<Integer>,Rectangle> drawnAt=new HashMap<>(),placesAt=new HashMap<>();
    /** A page carried to another place (the owner's ask: "grab and move the pages"): which, from where, and where it is now. */
    private List<Integer> pressed;private java.awt.Point pressedAt,now;private boolean carrying;

    DesktopPages(DesktopHome home) {
        this.home=home;setOpaque(false);setFocusable(true);
        MouseAdapter hand=new MouseAdapter(){
            public void mousePressed(MouseEvent e){pressed=SwingUtilities.isLeftMouseButton(e)?pageAt(e.getPoint()):null;pressedAt=e.getPoint();carrying=false;}
            public void mouseDragged(MouseEvent e) {
                if(pressed==null)return;
                if(!carrying&&e.getPoint().distance(pressedAt)<6)return;
                carrying=true;now=e.getPoint();setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));repaint();
                home.pad.status.setToolTipText(null);home.pad.status.setText("Let go on another page to change places with it, or on an empty place to move it there  ·  Esc cancels");
            }
            public void mouseReleased(MouseEvent e) {
                setCursor(Cursor.getDefaultCursor());
                if(!carrying){return;}
                List<Integer> from=pressed,to=placeAt(e.getPoint());
                carrying=false;pressed=null;now=null;repaint();
                if(to==null||to.equals(from)){home.pad.status.setText("Not moved");return;}
                home.movePage(from.get(0),from.get(1),to.get(0),to.get(1));
            }
            public void mouseClicked(MouseEvent e) {
                if(carrying)return;
                List<Integer> page=pageAt(e.getPoint());
                away();
                if(page!=null)home.grid.showPage(page.get(0),page.get(1));
            }
        };
        addMouseListener(hand);addMouseMotionListener(hand);
        addMouseWheelListener(e->{if(e.isControlDown()&&e.getWheelRotation()<0)away();});
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ESCAPE"),"away");
        getActionMap().put("away",new AbstractAction(){public void actionPerformed(ActionEvent e){
            if(carrying){carrying=false;pressed=null;now=null;repaint();home.pad.status.setText("Not moved");return;}
            away();
        }});
    }

    private List<Integer> pageAt(java.awt.Point p){for(Map.Entry<List<Integer>,Rectangle> one:drawnAt.entrySet())if(one.getValue().contains(p))return one.getKey();return null;}
    private List<Integer> placeAt(java.awt.Point p){for(Map.Entry<List<Integer>,Rectangle> one:placesAt.entrySet())if(one.getValue().contains(p))return one.getKey();return null;}

    /** Every page there is, and the empty places one further each way: where a carried page can go. */
    static java.util.Set<List<Integer>> places(Set<List<Integer>> active) {
        java.util.Set<List<Integer>> all=new java.util.LinkedHashSet<>(active);
        for(List<Integer> one:active)for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})all.add(List.of(one.get(0)+d[0],one.get(1)+d[1]));
        return all;
    }

    /** Up, over Home. */
    void up(){if(home.folder.isOpen())home.folder.close();setVisible(true);repaint();SwingUtilities.invokeLater(this::requestFocusInWindow);}
    /** Put away, and the page chosen - or the one that was in view - is what is seen. */
    void away(){setVisible(false);home.grid.requestFocusInWindow();}

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        Object hints=Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");if(hints instanceof Map<?,?> map)g.addRenderingHints(map);
        // Its own view, on Home's paper a shade darker, so the pages read as pages and not as Home seen through a dim.
        Color paper=DesktopLook.wash(home.pad.homeColour(),DesktopUi.PAPER,0.12f,0.72f,home.pad.tone);
        g.setColor(DesktopUi.mix(paper,DesktopUi.INK,0.08f));g.fillRect(0,0,getWidth(),getHeight());
        DesktopHome.Icons grid=home.grid;
        Map<String,Layout.Spot> spots=grid.spots();
        Set<List<Integer>> active=Layout.active(spots);
        // Room is kept for the empty places round the pages, so nothing moves when a page is picked up.
        java.util.Set<List<Integer>> all=places(active);
        int minX=0,maxX=0,minY=0,maxY=0;
        for(List<Integer> one:all){minX=Math.min(minX,one.get(0));maxX=Math.max(maxX,one.get(0));minY=Math.min(minY,one.get(1));maxY=Math.max(maxY,one.get(1));}
        int across=maxX-minX+1,down=maxY-minY+1,cols=grid.columns(),rows=grid.rows();
        // Each page as big as fits, its shape the shape of the room, with a gap between.
        int gap=18,room=44;float pageW=cols*DesktopHome.CELL,pageH=rows*DesktopHome.HIGH;
        float scale=Math.min((getWidth()-2*room-(across-1)*gap)/(across*pageW),(getHeight()-2*room-30-(down-1)*gap)/(down*pageH));
        scale=Math.max(0.05f,Math.min(0.6f,scale));
        int w=Math.round(pageW*scale),h=Math.round(pageH*scale);
        int x0=(getWidth()-(across*w+(across-1)*gap))/2,y0=Math.max(room+24,(getHeight()-(down*h+(down-1)*gap))/2);
        g.setFont(DesktopUi.BODY.deriveFont(13f));g.setColor(DesktopUi.QUIET);
        String head="All pages  ·  click one to go there, drag one to move it, Esc to close";
        g.drawString(head,(getWidth()-g.getFontMetrics().stringWidth(head))/2f,y0-14);
        Map<String,NoteStore.Branch> things=new HashMap<>();for(DesktopHome.Tile one:grid.tiles)things.put(one.thing.id,one.thing);
        drawnAt.clear();placesAt.clear();
        List<Integer> under=null;
        for(List<Integer> place:all) {
            int x=x0+(place.get(0)-minX)*(w+gap),y=y0+(place.get(1)-minY)*(h+gap);
            placesAt.put(place,new Rectangle(x,y,w,h));
        }
        if(carrying&&now!=null)under=placeAt(now);
        for(List<Integer> place:all) {
            if(active.contains(place)||!carrying)continue;
            // While a page is carried, the empty places it can go, dashed; the one under the pointer lit.
            Rectangle at=placesAt.get(place);boolean lit=place.equals(under);
            g.setColor(lit?DesktopUi.ACCENT:DesktopUi.mix(DesktopUi.INK,DesktopUi.PAPER,0.5f));
            g.setStroke(new BasicStroke(lit?2.5f:1.4f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,10f,new float[]{7f,6f},0f));
            g.drawRoundRect(at.x,at.y,at.width-1,at.height-1,14,14);
        }
        // The carried page last, so it is over the others.
        List<List<Integer>> order=new ArrayList<>(active);
        if(carrying&&pressed!=null&&order.remove(pressed))order.add(pressed);
        for(List<Integer> page:order) {
            Rectangle place=placesAt.get(page);int x=place.x,y=place.y;
            // The carried page is drawn under the pointer, over the others, where it would go.
            if(carrying&&page.equals(pressed)&&now!=null){x+=now.x-pressedAt.x;y+=now.y-pressedAt.y;}
            drawnAt.put(page,place);
            g.setColor(paper);g.fillRoundRect(x,y,w,h,14,14);
            boolean seen=page.get(0)==grid.pageX&&page.get(1)==grid.pageY,lit=carrying&&page.equals(under)&&!page.equals(pressed);
            g.setColor(seen||lit?DesktopUi.ACCENT:DesktopUi.LINE.darker());g.setStroke(new BasicStroke(seen||lit?2.5f:1f));g.drawRoundRect(x,y,w-1,h-1,14,14);
            for(Map.Entry<String,Layout.Spot> one:spots.entrySet()) {
                Layout.Spot s=one.getValue();NoteStore.Branch thing=things.get(one.getKey());
                if(s==null||thing==null||!s.on(page.get(0),page.get(1)))continue;
                int side=Math.max(4,Math.round(DesktopHome.FACE*scale));
                int fx=x+Math.round((s.column()*DesktopHome.CELL+(DesktopHome.CELL-DesktopHome.FACE)/2f)*scale),fy=y+Math.round((s.row()*DesktopHome.HIGH+10)*scale);
                DesktopHome.face(g,thing,fx,fy,side,home.pad.tone);
            }
            if(page.get(0)==0&&page.get(1)==0) {
                g.setFont(DesktopUi.BODY.deriveFont(12f));g.setColor(DesktopUi.QUIET);
                // Inside the page, at its foot: under it the next page would cover it.
                g.drawString("Main page",x+(w-g.getFontMetrics().stringWidth("Main page"))/2f,y+h-10);
            }
        }
        g.dispose();
    }

    /** Where a page lies, in words: "The main page", "Page one to the right", "Page two up, one to the left". */
    static String said(int x,int y) {
        if(x==0&&y==0)return "The main page";
        List<String> parts=new ArrayList<>();
        if(y!=0)parts.add(count(y)+(y<0?" up":" down"));
        if(x!=0)parts.add(count(x)+(x<0?" to the left":" to the right"));
        return "Page "+String.join(", ",parts);
    }
    private static String count(int n){int m=Math.abs(n);return m==1?"one":m==2?"two":m==3?"three":String.valueOf(m);}

    /**
     * The dots under Home's grid, where there is more than one page: one for each page there is, where it lies, the one in
     * view filled (even empty, as a thing carried onto a new page is) - so it is plain there are others, and which way.
     */
    static void dots(Graphics2D g0,JComponent on,Set<List<Integer>> active,int pageX,int pageY) {
        if(active.size()<=1)return;
        Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        int minX=0,maxX=0,minY=0,maxY=0;
        for(List<Integer> one:active){minX=Math.min(minX,one.get(0));maxX=Math.max(maxX,one.get(0));minY=Math.min(minY,one.get(1));maxY=Math.max(maxY,one.get(1));}
        int step=12,across=maxX-minX+1,down=maxY-minY+1;
        Rectangle seen=on.getVisibleRect();
        int x0=seen.x+(seen.width-(across-1)*step)/2,y0=seen.y+seen.height-10-(down-1)*step;
        for(List<Integer> one:active) {
            int x=x0+(one.get(0)-minX)*step,y=y0+(one.get(1)-minY)*step;
            boolean here=one.get(0)==pageX&&one.get(1)==pageY;
            g.setColor(here?DesktopUi.ACCENT:DesktopUi.mix(DesktopUi.INK,DesktopUi.PAPER,0.6f));
            int r=here?4:3;g.fillOval(x-r,y-r,2*r,2*r);
        }
        g.dispose();
    }
}
