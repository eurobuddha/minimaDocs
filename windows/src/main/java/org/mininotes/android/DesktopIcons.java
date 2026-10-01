package org.mininotes.android;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * The icons and pictures notes and collections wear, drawn for the PC (docs/HOME.md, step 4). An icon is Lucide's path
 * data ({@link Icons}) replayed onto a {@link Path2D.Float}, scaled from its 24-unit grid to the size wanted and stroked
 * two units wide - scaled with it - with round ends and joins, never filled, in the thing's own ink. A picture is decoded
 * once, made ready once at the size a face needs at the screen's own scale, and drawn with its corners rounded.
 *
 * <p>Both are kept once made: a grid of fifty icons painted again as the pointer moves over it would otherwise read the
 * same path data fifty times a paint, and decode the same pictures every time. A picture chosen here is made as
 * {@link Thumb} says - the square from the middle, Thumb's sides tried in order until one fits - and written as PNG,
 * the one kind of picture the PC can write. The phone writes WebP; this PC reads it through the ImageIO plugin the build
 * carries, and a picture it still cannot read is drawn as the icon under it.
 */
final class DesktopIcons {
    private DesktopIcons(){}

    /** A note's own icon until it is given another; Home's grid has a class of its own called Icons, so it is said here. */
    static final String NOTE=Icons.NOTE;
    /** How many built shapes are kept: every icon in the set at the picker's size, with room for the sizes faces use. */
    static final int SHAPES_KEPT=4096;
    /** Pictures kept decoded, and made ready at a size: each at most a screen's worth, so a few dozen of each. */
    static final int PICTURES_KEPT=64,READY_KEPT=128;
    /** The most a picture may say it is, a side, before it is not read at all: nothing real that fits Thumb is bigger. */
    static final int LARGEST=16384;
    /** The biggest file read as a picture to wear: a camera's photograph, and some room over. */
    static final long FILE_MOST=64L*1024*1024;

    // ---- icons ----------------------------------------------------------------------------------------------------

    private static final Map<String,Path2D.Float> SHAPES=new LinkedHashMap<>(256,0.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Path2D.Float> eldest){return size()>SHAPES_KEPT;}
    };

    /** Whether an icon's name is one this build can draw: an empty one is the thing's default, as is one from a later set. */
    static boolean known(String name){return name!=null&&!name.isEmpty()&&Icons.known(name);}

    /**
     * An icon's outline in a square of {@code size} pixels from 0,0, built once and kept. Null for a name this build does
     * not have, which is then drawn as the thing's default. Shared: the shape handed out is only ever drawn, never changed.
     */
    static Path2D.Float shape(String name,float size) {
        if(!known(name)||!(size>0))return null;
        String key=name+"@"+size;
        synchronized(SHAPES){Path2D.Float kept=SHAPES.get(key);if(kept!=null)return kept;}
        float k=size/Icons.GRID;Path2D.Float path=new Path2D.Float();boolean[] started={false};
        boolean drawn=Icons.draw(name,new Icons.Pen(){
            // A line with nowhere to start from would stop Path2D altogether: it starts where the line does instead.
            private void from(float x,float y){if(!started[0]){path.moveTo(x*k,y*k);started[0]=true;}}
            public void moveTo(float x,float y){path.moveTo(x*k,y*k);started[0]=true;}
            public void lineTo(float x,float y){from(x,y);path.lineTo(x*k,y*k);}
            public void cubicTo(float x1,float y1,float x2,float y2,float x,float y){from(x1,y1);path.curveTo(x1*k,y1*k,x2*k,y2*k,x*k,y*k);}
            public void close(){if(started[0])path.closePath();}
        });
        if(!drawn||!started[0])return null;
        synchronized(SHAPES){SHAPES.put(key,path);}
        return path;
    }

    /** How many shapes are kept now: what a test watches stays bounded. */
    static int shapesKept(){synchronized(SHAPES){return SHAPES.size();}}

    /** Lucide's two units at a size: never thinner than a hair, where a small icon's lines would break up on the screen. */
    static float stroke(float size){return Math.max(1.2f,Icons.STROKE*size/Icons.GRID);}

    /** An icon drawn in a square this size at x,y, in this ink. False, and nothing drawn, for a name this build does not have. */
    static boolean draw(Graphics2D g0,String name,float x,float y,float size,Color ink) {
        Path2D.Float path=shape(name,size);if(path==null)return false;
        Graphics2D g=(Graphics2D)g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
        g.translate(x,y);g.setColor(ink);g.setStroke(new BasicStroke(stroke(size),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.draw(path);g.dispose();
        return true;
    }

    /** The ink an icon is drawn in: the thing's colour itself, as its edge is, or the page's ink with none - the phone's rule (Looks). */
    static Color ink(int colour){return new Color(Looks.ink(colour,false,DesktopUi.INK.getRGB()));}

    /** A face under this many pixels is a small one - a line of the tree, a bar - and its icon takes more of it, as on the phone. */
    static final float SMALL=40;

    /** An icon as a Swing icon, for a button or a line of a menu: in the page's ink, or grey where it cannot be pressed. */
    static javax.swing.Icon glyph(String name,int size) {
        return new javax.swing.Icon(){
            public int getIconWidth(){return size;}public int getIconHeight(){return size;}
            public void paintIcon(Component c,Graphics g,int x,int y){draw((Graphics2D)g,name,x,y,size,c==null||c.isEnabled()?DesktopUi.INK:DesktopUi.QUIET);}
        };
    }

    // ---- pictures, drawn ------------------------------------------------------------------------------------------

    /** Keeps the eldest out past a number, the newest used last. */
    private static <K,V> Map<K,V> lru(int most) {
        return new LinkedHashMap<K,V>(32,0.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<K,V> eldest){return size()>most;}};
    }
    /** What a picture is known by, its bytes as they are: every read of a list hands out new arrays of the same bytes. */
    private static final Map<byte[],ByteBuffer> SEEN=new WeakHashMap<>();
    private static final Map<ByteBuffer,Object> DECODED=lru(PICTURES_KEPT);
    private record Ready(ByteBuffer bytes,int pixels,int arc){}
    private static final Map<Ready,BufferedImage> READY=lru(READY_KEPT);
    /** Said where bytes are not a picture this PC reads: tried once, not at every paint. */
    private static final Object UNREADABLE=new Object();

    private static ByteBuffer key(byte[] bytes) {
        synchronized(SEEN){return SEEN.computeIfAbsent(bytes,b->ByteBuffer.wrap(b.clone()).asReadOnlyBuffer());}
    }

    /** A picture's bytes decoded, once and kept - null where they are not a picture this PC can read. */
    static BufferedImage decoded(byte[] bytes) {
        if(bytes==null||bytes.length==0)return null;
        ByteBuffer key=key(bytes);
        synchronized(DECODED){Object kept=DECODED.get(key);if(kept!=null)return kept==UNREADABLE?null:(BufferedImage)kept;}
        BufferedImage read;
        try{read=decode(bytes,2*Thumb.SIDE);}catch(IOException|RuntimeException damaged){read=null;}
        synchronized(DECODED){DECODED.put(key,read==null?UNREADABLE:read);}
        return read;
    }

    /**
     * A picture made ready to draw {@code pixels} a side with its corners rounded: the square from its middle, scaled with
     * care, and the corners cut soft. Made once for a size and kept. Null where the bytes are not a picture.
     */
    static BufferedImage ready(byte[] bytes,int pixels,int arc) {
        if(bytes==null||bytes.length==0||pixels<=0)return null;
        Ready key=new Ready(key(bytes),pixels,arc);
        synchronized(READY){BufferedImage kept=READY.get(key);if(kept!=null)return kept;}
        BufferedImage picture=decoded(bytes);if(picture==null)return null;
        int[] square=Thumb.square(picture.getWidth(),picture.getHeight());if(square==null)return null;
        BufferedImage scaled=scaled(picture,square[0],square[1],square[2],pixels,true);
        BufferedImage round=new BufferedImage(pixels,pixels,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=round.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        // The round square first, soft at its edge, and the picture only where it is: a clip would cut its corners jagged.
        g.setColor(Color.WHITE);g.fill(new RoundRectangle2D.Float(0,0,pixels,pixels,arc,arc));
        g.setComposite(AlphaComposite.SrcIn);g.drawImage(scaled,0,0,null);g.dispose();
        synchronized(READY){READY.put(key,round);}
        return round;
    }

    /**
     * A picture filling a round square, drawn at the screen's own scale so it is as sharp as the icons beside it. False,
     * and nothing drawn, where there is no picture or it cannot be read: the thing's icon is drawn instead.
     */
    static boolean picture(Graphics2D g,byte[] bytes,int x,int y,int side,int arc) {
        if(bytes==null||bytes.length==0||side<=0)return false;
        double scale=Math.max(1,Math.abs(g.getTransform().getScaleX()));
        int pixels=(int)Math.ceil(side*scale);
        BufferedImage ready=ready(bytes,pixels,(int)Math.round(arc*scale));
        if(ready==null)return false;
        Graphics2D on=(Graphics2D)g.create();
        on.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        on.drawImage(ready,x,y,side,side,null);on.dispose();
        return true;
    }

    // ---- pictures, read ---------------------------------------------------------------------------------------------

    /**
     * Bytes read as a picture no smaller than {@code least} a side where it can be, larger ones read at every n-th pixel,
     * so a camera's photograph costs a small picture's memory. Null where no reader on this PC knows the bytes. A picture
     * that says it is larger than anything real is refused before it is read: what arrives from elsewhere is small, but
     * says its own size, and a few bytes can say a size no memory holds.
     */
    static BufferedImage decode(byte[] bytes,int least) throws IOException {
        try(ImageInputStream in=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if(in==null)return null;
            Iterator<ImageReader> readers=ImageIO.getImageReaders(in);
            if(!readers.hasNext())return null;
            ImageReader reader=readers.next();
            try {
                reader.setInput(in,true,true);
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<=0||h<=0||w>LARGEST||h>LARGEST)throw new IOException("That picture says it is too big to be a picture.");
                ImageReadParam param=reader.getDefaultReadParam();
                int step=subsampling(w,h,least);
                if(step>1)param.setSourceSubsampling(step,step,0,0);
                return reader.read(0,param);
            } finally {reader.dispose();}
        }
    }

    /** Every how many pixels a picture this size is read at, for its short side to stay at least {@code least}. */
    static int subsampling(int width,int height,int least){return Math.max(1,Math.min(width,height)/Math.max(1,least));}

    /** A picture chosen on this PC, as it is read to be worn: the picture, and the turn its camera wrote down. */
    record Chosen(BufferedImage picture,int turn){}

    /** A picture file, read to be worn: no bigger than a photograph, at twice Thumb's side, turned as its camera said. */
    static Chosen read(Path file) throws IOException {
        if(Files.size(file)>FILE_MOST)throw new IllegalArgumentException("That picture is too big to use. Choose a smaller one.");
        return read(Files.readAllBytes(file));
    }
    static Chosen read(byte[] bytes) throws IOException {
        BufferedImage picture=decode(bytes,2*Thumb.SIDE);
        if(picture==null)throw new IllegalArgumentException("Mininotes cannot read that kind of picture. A JPEG, PNG, GIF, BMP or WebP will do.");
        return new Chosen(picture,"jpeg".equals(Thumb.kind(bytes))?orientation(bytes):1);
    }

    /**
     * The turn a camera wrote into a JPEG (EXIF orientation, 1 to 8), which Explorer and every photo viewer turn the
     * picture by before showing it; ImageIO does not, and a photograph held upright would be worn on its side. 1 where
     * nothing says otherwise, or the bytes cannot be read that far.
     */
    static int orientation(byte[] b) {
        try {
            if(b==null||b.length<4||(b[0]&0xff)!=0xFF||(b[1]&0xff)!=0xD8)return 1;
            int at=2;
            while(at+4<=b.length) {
                if((b[at]&0xff)!=0xFF)return 1;
                int marker=b[at+1]&0xff;at+=2;
                if(marker==0xD8||marker>=0xD0&&marker<=0xD7||marker==0x01)continue;
                if(marker==0xDA||marker==0xD9)return 1;
                int length=((b[at]&0xff)<<8)|(b[at+1]&0xff);
                if(length<2||at+length>b.length)return 1;
                if(marker==0xE1&&length>=16&&b[at+2]=='E'&&b[at+3]=='x'&&b[at+4]=='i'&&b[at+5]=='f'&&b[at+6]==0&&b[at+7]==0) {
                    int tiff=at+8,end=at+length;
                    boolean little=b[tiff]=='I'&&b[tiff+1]=='I';
                    if(!little&&!(b[tiff]=='M'&&b[tiff+1]=='M'))return 1;
                    int first=tiff+(int)number(b,tiff+4,4,little);
                    if(first<tiff||first+2>end)return 1;
                    int entries=(int)number(b,first,2,little);
                    for(int e=0;e<entries;e++) {
                        int entry=first+2+e*12;
                        if(entry+12>end)return 1;
                        if(number(b,entry,2,little)==0x0112) {
                            int turn=(int)number(b,entry+8,2,little);
                            return turn>=1&&turn<=8?turn:1;
                        }
                    }
                    return 1;
                }
                at+=length;
            }
        } catch(RuntimeException damaged){/* unturned, then */}
        return 1;
    }
    private static long number(byte[] b,int at,int bytes,boolean little) {
        long n=0;
        for(int i=0;i<bytes;i++)n|=(long)(b[at+(little?i:bytes-1-i)]&0xff)<<(8*i);
        return n;
    }

    /**
     * A square picture turned as EXIF says - where each pixel goes, as the matrix {m00,m10,m01,m11} and whether the side
     * is added across and down - so a photograph is worn the way up it was taken.
     */
    static BufferedImage turned(BufferedImage square,int turn) {
        if(turn<2||turn>8)return square;
        int n=square.getWidth();
        double[][] how={null,null,{-1,0,0,1,n,0},{-1,0,0,-1,n,n},{1,0,0,-1,0,n},{0,1,1,0,0,0},{0,1,-1,0,n,0},{0,-1,-1,0,n,n},{0,-1,1,0,0,n}};
        double[] m=how[turn];
        BufferedImage out=new BufferedImage(n,square.getHeight(),square.getColorModel().hasAlpha()?BufferedImage.TYPE_INT_ARGB:BufferedImage.TYPE_INT_RGB);
        Graphics2D g=out.createGraphics();g.drawImage(square,new AffineTransform(m[0],m[1],m[2],m[3],m[4],m[5]),null);g.dispose();
        return out;
    }

    // ---- pictures, made -----------------------------------------------------------------------------------------------

    /**
     * The square at x,y, {@code side} pixels, made {@code to} pixels a side with care: halved step by step while it is
     * more than twice as big - one step would skip most of its pixels and leave it grainy - and then the last step
     * smoothly. With {@code alpha}, what is see-through stays so; without it, it is laid on white.
     */
    static BufferedImage scaled(BufferedImage source,int x,int y,int side,int to,boolean alpha) {
        int type=alpha?BufferedImage.TYPE_INT_ARGB:BufferedImage.TYPE_INT_RGB;
        BufferedImage now=new BufferedImage(side,side,type);
        Graphics2D g=now.createGraphics();
        if(!alpha){g.setColor(Color.WHITE);g.fillRect(0,0,side,side);}
        g.drawImage(source,-x,-y,null);g.dispose();
        int at=side;
        while(at>2*to) {
            int half=at/2;
            now=drawn(now,half,type,RenderingHints.VALUE_INTERPOLATION_BILINEAR);at=half;
        }
        return at==to?now:drawn(now,to,type,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }
    private static BufferedImage drawn(BufferedImage from,int to,int type,Object how) {
        BufferedImage out=new BufferedImage(to,to,type);
        Graphics2D g=out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,how);g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(from,0,0,to,to,null);g.dispose();
        return out;
    }

    /** The sides a picture this many pixels square is tried at, in Thumb's order: PNG has no quality to fall, so each side once. */
    static List<Integer> sides(int square) {
        LinkedHashSet<Integer> sides=new LinkedHashSet<>();
        for(Thumb.Try one:Thumb.tries(square))sides.add(one.side);
        return new ArrayList<>(sides);
    }

    /**
     * A picture made into what a thing wears: the square from its middle, turned as its camera said, scaled with care to
     * the first of Thumb's sides whose PNG fits, and that PNG. See-through only where some of it is. Null where not even
     * the smallest side fits, which a picture of any real sort always does.
     */
    static byte[] thumb(BufferedImage picture,int turn) throws IOException {
        int[] square=Thumb.square(picture.getWidth(),picture.getHeight());
        if(square==null)return null;
        boolean alpha=picture.getColorModel().hasAlpha();
        // Each side once, in Thumb's order, the first that fits kept - as the phone keeps its WebP (Looks.firstFit).
        List<Thumb.Try> tries=new ArrayList<>();for(int side:sides(square[2]))tries.add(new Thumb.Try(side,100));
        return Looks.firstFit(tries,one->{
            BufferedImage made=turned(scaled(picture,square[0],square[1],square[2],one.side,alpha),turn);
            // A PNG that says it is see-through costs a quarter more; a screenshot often says so and is not.
            if(alpha&&opaque(made))made=flattened(made);
            try{return png(made);}catch(IOException failed){throw new java.io.UncheckedIOException(failed);}
        });
    }

    private static boolean opaque(BufferedImage image) {
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)!=0xFF)return false;
        return true;
    }
    private static BufferedImage flattened(BufferedImage image) {
        BufferedImage out=new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=out.createGraphics();g.drawImage(image,0,0,null);g.dispose();
        return out;
    }

    /** A picture as PNG, packed as tightly as the writer packs: the bytes have to ride in every message about the thing. */
    static byte[] png(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers=ImageIO.getImageWritersByFormatName("png");
        if(!writers.hasNext())throw new IOException("This PC has nothing that writes PNG.");
        ImageWriter writer=writers.next();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ImageOutputStream out=ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam how=writer.getDefaultWriteParam();
            if(how.canWriteCompressed()){how.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);how.setCompressionQuality(0f);}
            writer.write(null,new IIOImage(image,null,null),how);
        } finally {writer.dispose();}
        return bytes.toByteArray();
    }
}
