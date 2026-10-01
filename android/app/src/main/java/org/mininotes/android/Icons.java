// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The icons a note or a collection can wear: Lucide's set (ISC, see NOTICE), as the path data it is drawn from.
 *
 * <p>Neither app gains a library for it. The set travels as one line per icon in {@code icons.txt} beside this class
 * - its name, the words it is found by, and every shape in it as SVG path data on a 24-unit grid - and is drawn by
 * reading that data into moves, lines, curves and closes ({@link Pen}), which the phone replays onto
 * {@code android.graphics.Path} and the PC onto {@code java.awt.geom.Path2D}. Every icon is drawn the way Lucide
 * draws it: stroked two units wide with round ends and joins, never filled. An icon travels by its name, so a name
 * this build does not know - from a later set - is drawn as the thing's default.
 *
 * <p>Holds no Android types: reading, finding and the path data itself are unit tested.
 */
final class Icons {
    /** A note's icon until it is given another. A collection has none: it shows what is inside it. */
    static final String NOTE="sticky-note";
    /** The grid the path data is drawn on, and how wide its strokes are on it. */
    static final float GRID=24f, STROKE=2f;
    /** Where the set lives, beside this class. */
    static final String RESOURCE="icons.txt";

    /** One icon: its name, the words it is found by, and its path data. */
    static final class Icon {
        final String name,data; final List<String> tags;
        Icon(String name,List<String> tags,String data){this.name=name;this.tags=tags;this.data=data;}
    }

    /** What an icon's path data is read into: absolute coordinates on the 24-unit grid. */
    interface Pen {
        void moveTo(float x,float y);
        void lineTo(float x,float y);
        void cubicTo(float x1,float y1,float x2,float y2,float x,float y);
        void close();
    }

    private Icons(){}

    private static volatile Map<String,Icon> set;

    /** Every icon, by name, in the set's order; read once, the first time it is asked for. */
    static Map<String,Icon> all() {
        Map<String,Icon> read=set;
        if(read!=null)return read;
        synchronized(Icons.class) {
            if(set==null) {
                try(InputStream in=Icons.class.getResourceAsStream(RESOURCE)) {
                    set=in==null?Collections.<String,Icon>emptyMap():read(in);
                } catch(IOException unreadable){set=Collections.emptyMap();}
            }
            return set;
        }
    }

    /** The set from its lines. A line that does not read is left out, never the set with it. */
    static Map<String,Icon> read(InputStream in) throws IOException {
        Map<String,Icon> all=new LinkedHashMap<>();
        BufferedReader lines=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
        for(String line;(line=lines.readLine())!=null;) {
            if(line.isEmpty()||line.startsWith("#"))continue;
            String[] part=line.split("\t",-1);
            if(part.length!=3||part[0].isEmpty()||part[2].isEmpty())continue;
            List<String> tags=new ArrayList<>();
            for(String tag:part[1].split(","))if(!tag.trim().isEmpty())tags.add(tag.trim().toLowerCase(Locale.ROOT));
            all.put(part[0],new Icon(part[0],Collections.unmodifiableList(tags),part[2]));
        }
        return Collections.unmodifiableMap(all);
    }

    /** Whether this build can draw an icon of that name. */
    static boolean known(String name){return name!=null&&all().containsKey(name);}

    /**
     * The icons a search finds, best first: a name that is the words, then names that begin with them, then names
     * that hold them, then icons tagged with them. Empty words find the whole set, in its order.
     */
    static List<String> search(String words,int most) {
        String said=words==null?"":words.trim().toLowerCase(Locale.ROOT).replace(' ','-');
        List<String> exact=new ArrayList<>(),starts=new ArrayList<>(),holds=new ArrayList<>(),tagged=new ArrayList<>();
        String plain=said.replace('-',' ');
        for(Icon one:all().values()) {
            if(said.isEmpty()){exact.add(one.name);continue;}
            if(one.name.equals(said))exact.add(one.name);
            else if(one.name.startsWith(said))starts.add(one.name);
            else if(one.name.contains(said))holds.add(one.name);
            else for(String tag:one.tags)if(tag.startsWith(plain)||tag.equals(said)){tagged.add(one.name);break;}
        }
        List<String> found=new ArrayList<>(exact);found.addAll(starts);found.addAll(holds);found.addAll(tagged);
        return found.size()>most?new ArrayList<>(found.subList(0,Math.max(0,most))):found;
    }

    /** Draws an icon by name with the pen; false where there is no such icon, and nothing is drawn. */
    static boolean draw(String name,Pen pen) {
        Icon icon=name==null?null:all().get(name);
        if(icon==null)return false;
        trace(icon.data,pen);
        return true;
    }

    // ---- reading SVG path data ------------------------------------------------------------------------

    /**
     * SVG path data read into the pen, every command made absolute: H and V are lines, S and T take their first
     * control point from the curve before, a quadratic is the cubic it equals, and an arc is cut into curves of a
     * quarter turn or less. Numbers may run together as SVG allows ("-.706", "1.5.5", an arc's flags as "01").
     * Data that stops making sense ends the drawing where it does, with what was already drawn kept.
     */
    static void trace(String data,Pen pen) {
        Reader in=new Reader(data);
        float x=0,y=0,startX=0,startY=0,lastCx=0,lastCy=0;char last=' ';
        char command=' ';
        while(true) {
            in.skip();
            if(in.done())return;
            char at=in.peek();
            if(Character.isLetter(at)){command=at;in.next();}
            else if(command==' ')return;
            boolean rel=Character.isLowerCase(command);
            char up=Character.toUpperCase(command);
            try {
                switch(up) {
                    case 'M': {
                        float nx=in.number()+(rel?x:0), ny=in.number()+(rel?y:0);
                        pen.moveTo(nx,ny);x=startX=nx;y=startY=ny;
                        // Pairs after a move are lines, relative or not as the move was.
                        command=rel?'l':'L';last='M';continue;
                    }
                    case 'L': {
                        float nx=in.number()+(rel?x:0), ny=in.number()+(rel?y:0);
                        pen.lineTo(nx,ny);x=nx;y=ny;break;
                    }
                    case 'H': {float nx=in.number()+(rel?x:0);pen.lineTo(nx,y);x=nx;break;}
                    case 'V': {float ny=in.number()+(rel?y:0);pen.lineTo(x,ny);y=ny;break;}
                    case 'C': {
                        float x1=in.number()+(rel?x:0),y1=in.number()+(rel?y:0),x2=in.number()+(rel?x:0),y2=in.number()+(rel?y:0);
                        float nx=in.number()+(rel?x:0),ny=in.number()+(rel?y:0);
                        pen.cubicTo(x1,y1,x2,y2,nx,ny);lastCx=x2;lastCy=y2;x=nx;y=ny;last='C';continue;
                    }
                    case 'S': {
                        float x1=last=='C'?2*x-lastCx:x, y1=last=='C'?2*y-lastCy:y;
                        float x2=in.number()+(rel?x:0),y2=in.number()+(rel?y:0),nx=in.number()+(rel?x:0),ny=in.number()+(rel?y:0);
                        pen.cubicTo(x1,y1,x2,y2,nx,ny);lastCx=x2;lastCy=y2;x=nx;y=ny;last='C';continue;
                    }
                    case 'Q': {
                        float qx=in.number()+(rel?x:0),qy=in.number()+(rel?y:0),nx=in.number()+(rel?x:0),ny=in.number()+(rel?y:0);
                        quad(pen,x,y,qx,qy,nx,ny);lastCx=qx;lastCy=qy;x=nx;y=ny;last='Q';continue;
                    }
                    case 'T': {
                        float qx=last=='Q'?2*x-lastCx:x, qy=last=='Q'?2*y-lastCy:y;
                        float nx=in.number()+(rel?x:0),ny=in.number()+(rel?y:0);
                        quad(pen,x,y,qx,qy,nx,ny);lastCx=qx;lastCy=qy;x=nx;y=ny;last='Q';continue;
                    }
                    case 'A': {
                        float rx=in.number(),ry=in.number(),turn=in.number();
                        boolean large=in.flag(),sweep=in.flag();
                        float nx=in.number()+(rel?x:0),ny=in.number()+(rel?y:0);
                        arc(pen,x,y,rx,ry,turn,large,sweep,nx,ny);x=nx;y=ny;break;
                    }
                    case 'Z': {
                        pen.close();x=startX;y=startY;last='Z';
                        // Z takes nothing; what follows it must be a command of its own.
                        command=' ';continue;
                    }
                    default: return;
                }
            } catch(IllegalArgumentException damaged){return;}
            last=up;
        }
    }

    /** A quadratic curve as the cubic that draws exactly the same line. */
    private static void quad(Pen pen,float x0,float y0,float qx,float qy,float x,float y) {
        pen.cubicTo(x0+2f/3f*(qx-x0),y0+2f/3f*(qy-y0),x+2f/3f*(qx-x),y+2f/3f*(qy-y),x,y);
    }

    /** An elliptical arc, from where the pen is, as cubics of a quarter turn or less (SVG 1.1, F.6.5 and F.6.6). */
    static void arc(Pen pen,float x0,float y0,float rxIn,float ryIn,float degrees,boolean large,boolean sweep,float x,float y) {
        if(x0==x&&y0==y)return;
        double rx=Math.abs(rxIn),ry=Math.abs(ryIn);
        if(rx==0||ry==0){pen.lineTo(x,y);return;}
        double phi=Math.toRadians(degrees%360),cos=Math.cos(phi),sin=Math.sin(phi);
        double dx=(x0-x)/2.0,dy=(y0-y)/2.0;
        double x1=cos*dx+sin*dy,y1=-sin*dx+cos*dy;
        double lambda=(x1*x1)/(rx*rx)+(y1*y1)/(ry*ry);
        if(lambda>1){double s=Math.sqrt(lambda);rx*=s;ry*=s;}
        double num=rx*rx*ry*ry-rx*rx*y1*y1-ry*ry*x1*x1, den=rx*rx*y1*y1+ry*ry*x1*x1;
        double coef=den==0?0:Math.sqrt(Math.max(0,num/den))*(large==sweep?-1:1);
        double cx1=coef*rx*y1/ry,cy1=-coef*ry*x1/rx;
        double cx=cos*cx1-sin*cy1+(x0+x)/2.0,cy=sin*cx1+cos*cy1+(y0+y)/2.0;
        double start=angle(1,0,(x1-cx1)/rx,(y1-cy1)/ry);
        double delta=angle((x1-cx1)/rx,(y1-cy1)/ry,(-x1-cx1)/rx,(-y1-cy1)/ry);
        if(!sweep&&delta>0)delta-=2*Math.PI;else if(sweep&&delta<0)delta+=2*Math.PI;
        int pieces=(int)Math.ceil(Math.abs(delta)/(Math.PI/2)-1e-9);
        if(pieces<1)pieces=1;
        double step=delta/pieces,k=4.0/3.0*Math.tan(step/4);
        double a=start;
        for(int at=0;at<pieces;at++) {
            double a2=a+step;
            double c1=Math.cos(a),s1=Math.sin(a),c2=Math.cos(a2),s2=Math.sin(a2);
            double p1x=c1-k*s1,p1y=s1+k*c1,p2x=c2+k*s2,p2y=s2-k*c2;
            float[] one=onto(p1x,p1y,rx,ry,cos,sin,cx,cy),two=onto(p2x,p2y,rx,ry,cos,sin,cx,cy);
            float[] end=at==pieces-1?new float[]{x,y}:onto(c2,s2,rx,ry,cos,sin,cx,cy);
            pen.cubicTo(one[0],one[1],two[0],two[1],end[0],end[1]);
            a=a2;
        }
    }

    private static float[] onto(double ux,double uy,double rx,double ry,double cos,double sin,double cx,double cy) {
        double px=ux*rx,py=uy*ry;
        return new float[]{(float)(cos*px-sin*py+cx),(float)(sin*px+cos*py+cy)};
    }

    private static double angle(double ux,double uy,double vx,double vy) {
        double dot=ux*vx+uy*vy,len=Math.sqrt((ux*ux+uy*uy)*(vx*vx+vy*vy));
        double a=Math.acos(Math.max(-1,Math.min(1,len==0?1:dot/len)));
        return ux*vy-uy*vx<0?-a:a;
    }

    /** Numbers and flags out of path data, as SVG writes them. */
    private static final class Reader {
        private final String s; private int at;
        Reader(String s){this.s=s==null?"":s;}
        boolean done(){return at>=s.length();}
        char peek(){return s.charAt(at);}
        void next(){at++;}
        void skip(){while(at<s.length()&&(Character.isWhitespace(s.charAt(at))||s.charAt(at)==','))at++;}
        boolean flag() {
            skip();
            if(done())throw new IllegalArgumentException("no flag");
            char c=s.charAt(at++);
            if(c!='0'&&c!='1')throw new IllegalArgumentException("not a flag");
            return c=='1';
        }
        float number() {
            skip();
            int begin=at;
            if(at<s.length()&&(s.charAt(at)=='-'||s.charAt(at)=='+'))at++;
            boolean digits=false,dot=false;
            while(at<s.length()) {
                char c=s.charAt(at);
                if(Character.isDigit(c)){digits=true;at++;}
                else if(c=='.'&&!dot){dot=true;at++;}
                else break;
            }
            if(at<s.length()&&(s.charAt(at)=='e'||s.charAt(at)=='E')) {
                int back=at;at++;
                if(at<s.length()&&(s.charAt(at)=='-'||s.charAt(at)=='+'))at++;
                boolean power=false;
                while(at<s.length()&&Character.isDigit(s.charAt(at))){power=true;at++;}
                if(!power)at=back;
            }
            if(!digits)throw new IllegalArgumentException("not a number");
            return Float.parseFloat(s.substring(begin,at));
        }
    }
}
