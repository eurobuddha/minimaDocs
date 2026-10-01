package org.mininotes.android;

import org.junit.Test;
import static org.junit.Assert.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * The PC's icons and pictures, without a window (docs/HOME.md, step 4): shapes built once and kept, a picture read
 * whatever kind it came as - the phone's WebP included - and one chosen here made small enough to travel, as Thumb says.
 */
public class DesktopIconsTest {
    /** Made with Pillow: a 48x32 lossy WebP (VP8), a 24x24 lossless one (VP8L), and a 24x24 lossy one with see-through edges (VP8X). */
    private static final String LOSSY="UklGRqoAAABXRUJQVlA4IJ4AAADwBQCdASowACAAPmEmjkW1oiEapAFYBgS1GcC9An/I6EiGhddV/ID2BLJwuAGZaxhk4q01uc//WAD+/eLF8UpuNKuse3zf/mUAb/GUKrdVdiei63dZd22f8hVtKbbp0ZDA/YDqDf4jP/q2eSXLLJ4xWDq+ok/B2z8SUMF0cDh6ezGWmt/Ly9xp6qcDHDQ5sbMPOsrruih9wbvbLJ9AAA==",
        LOSSLESS="UklGRjAAAABXRUJQVlA4TCMAAAAvF8AFEA8wKIM8KPMf8FDUgADk6N9JM1+TIKL/E4Bh6gMMAQA=",
        WITH_ALPHA="UklGRsgAAABXRUJQVlA4WAoAAAAQAAAAFwAAFwAAQUxQSBsAAAABDzD/ERFCUQMCkKN/J818TYKI/k8AhqkPMAQAVlA4IIYAAABQBQCdASoYABgAPm0ylkekIyIhKAgAgA2JbACdMxvImQB6gNsB4gHoAagB6AHSWARuFYAA/u8mzEubX6LP/ByZZtT0Wh/yb/ANH459L+J/M5J/xL43p+0vBlX/Acen9X38aUwG1YZC7Eynfo5MQj6Jsz7zZedYa7puSlcs+KnP27V2RAAAAA==";

    @Test public void aShapeIsBuiltOnceForANameAndASizeAndKept() {
        java.awt.geom.Path2D.Float heart=DesktopIcons.shape("heart",24);
        assertNotNull(heart);
        assertSame("asked again, it is the one already built",heart,DesktopIcons.shape("heart",24));
        java.awt.geom.Path2D.Float twice=DesktopIcons.shape("heart",48);
        assertNotSame(heart,twice);
        // Scaled from the 24-unit grid: inside its square, and twice as big at twice the size.
        Rectangle2D small=heart.getBounds2D(),big=twice.getBounds2D();
        assertTrue(small.getMinX()>=-0.5&&small.getMinY()>=-0.5&&small.getMaxX()<=24.5&&small.getMaxY()<=24.5);
        assertEquals(small.getWidth()*2,big.getWidth(),0.01);assertEquals(small.getMinY()*2,big.getMinY(),0.01);
        // A name this build does not have - from a later set - and no name at all are the thing's default: no shape.
        assertNull(DesktopIcons.shape("from-a-later-set",24));
        assertNull(DesktopIcons.shape("",24));assertNull(DesktopIcons.shape(null,24));
        assertFalse(DesktopIcons.known("from-a-later-set"));assertTrue(DesktopIcons.known(DesktopIcons.NOTE));
    }

    @Test public void theWholeSetAtThreeSizesKeepsTheCacheBounded() {
        for(float size:new float[]{17f,23f,29f})for(String name:Icons.all().keySet())assertNotNull(name,DesktopIcons.shape(name,size));
        assertTrue(DesktopIcons.shapesKept()<=DesktopIcons.SHAPES_KEPT);
        // The picker's size, every icon: all of them kept at once.
        assertTrue(Icons.all().size()<=DesktopIcons.SHAPES_KEPT);
    }

    @Test public void anIconIsStrokedInItsInkAndAnUnknownOneDrawsNothing() {
        BufferedImage canvas=new BufferedImage(48,48,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=canvas.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,48,48);
        assertFalse(DesktopIcons.draw(g,"from-a-later-set",0,0,48,Color.RED));
        assertEquals(0,count(canvas,0xFFFF0000));
        assertTrue(DesktopIcons.draw(g,"heart",0,0,48,Color.RED));g.dispose();
        assertTrue("the heart was drawn",count(canvas,0xFFFF0000)>40);
        // Stroked, never filled: the middle of the heart is still paper.
        assertEquals(0xFFFFFFFF,canvas.getRGB(24,24));
        // Lucide's two units, scaled, and never thinner than a hair.
        assertEquals(2f,DesktopIcons.stroke(24),0.001f);assertEquals(4f,DesktopIcons.stroke(48),0.001f);assertEquals(1.2f,DesktopIcons.stroke(8),0.001f);
    }

    @Test public void theInkIsTheColourItselfAsOnThePhone() {
        assertEquals(DesktopUi.INK,DesktopIcons.ink(Tint.NONE));
        assertEquals(new Color(Tint.of(6,false)),DesktopIcons.ink(6));
    }

    @Test public void thePhonesWebpIsReadHereAsPngAndJpegAre() throws Exception {
        byte[] lossy=Base64.getDecoder().decode(LOSSY),lossless=Base64.getDecoder().decode(LOSSLESS),alpha=Base64.getDecoder().decode(WITH_ALPHA);
        for(byte[] one:new byte[][]{lossy,lossless,alpha})assertTrue(Thumb.takes(one));
        BufferedImage read=DesktopIcons.decoded(lossy);
        assertNotNull("this PC cannot read the phone's WebP",read);assertEquals(48,read.getWidth());assertEquals(32,read.getHeight());
        BufferedImage clear=DesktopIcons.decoded(lossless);
        assertNotNull(clear);assertEquals(24,clear.getWidth());
        assertEquals("see-through where it was",0,clear.getRGB(1,1)>>>24);assertEquals(0xFF,clear.getRGB(12,12)>>>24);
        BufferedImage both=DesktopIcons.decoded(alpha);
        assertNotNull(both);assertEquals(24,both.getHeight());
        // Read once: the same bytes in another array are the same picture, already decoded.
        assertSame(read,DesktopIcons.decoded(lossy.clone()));
        // Not a picture: nothing, and asked again, still nothing.
        byte[] words="RIFF0000WEBPnot a picture at all".getBytes();
        assertNull(DesktopIcons.decoded(words));assertNull(DesktopIcons.decoded(words));
    }

    @Test public void aPictureIsMadeReadyOnceAtASizeWithRoundCorners() throws Exception {
        byte[] png=png(flat(300,200,new Color(84,130,110)),"png");
        BufferedImage ready=DesktopIcons.ready(png,80,20);
        assertNotNull(ready);assertEquals(80,ready.getWidth());assertEquals(80,ready.getHeight());
        assertEquals("the corner is cut away",0,ready.getRGB(0,0)>>>24);
        assertEquals(0xFF,ready.getRGB(40,40)>>>24);
        assertSame(ready,DesktopIcons.ready(png.clone(),80,20));
        assertNotSame(ready,DesktopIcons.ready(png,64,16));
        assertNull(DesktopIcons.ready("not a picture".getBytes(),80,20));
    }

    @Test public void aPictureThatSaysItIsHugeIsNotRead() throws Exception {
        // A PNG's header, saying it is a hundred thousand pixels a side: a few bytes can say what no memory holds.
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(new byte[]{(byte)0x89,'P','N','G',13,10,26,10});
        java.nio.ByteBuffer ihdr=java.nio.ByteBuffer.allocate(17);ihdr.put("IHDR".getBytes()).putInt(100_000).putInt(100_000).put((byte)8).put((byte)2).put((byte)0).put((byte)0).put((byte)0);
        java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(ihdr.array());
        out.write(new byte[]{0,0,0,13});out.write(ihdr.array());out.write(java.nio.ByteBuffer.allocate(4).putInt((int)crc.getValue()).array());
        byte[] huge=out.toByteArray();
        try{DesktopIcons.decode(huge,384);fail("read a picture that says it is 100,000 pixels a side");}catch(java.io.IOException refused){/* as it should */}
        assertNull(DesktopIcons.decoded(huge));
        // A camera's photograph is read at every n-th pixel, its short side still at least what is asked for.
        assertEquals(7,DesktopIcons.subsampling(4000,3000,384));assertEquals(1,DesktopIcons.subsampling(300,300,384));
        BufferedImage photo=DesktopIcons.decode(png(flat(2000,1500,Color.ORANGE),"jpg"),384);
        assertTrue(photo.getHeight()>=384&&photo.getHeight()<600);
    }

    @Test public void theSidesTriedAreThumbsEachOnce() {
        assertEquals(List.of(192,160,128,96,64),DesktopIcons.sides(4000));
        assertEquals(List.of(100,96,64),DesktopIcons.sides(100));
        assertEquals(List.of(40),DesktopIcons.sides(40));
    }

    @Test public void aPlainPictureFitsAtTheFullSideAsAnOpaquePng() throws Exception {
        byte[] thumb=DesktopIcons.thumb(flat(1000,600,new Color(214,168,96)),1);
        assertNotNull(thumb);assertTrue(Thumb.takes(thumb));assertEquals("png",Thumb.kind(thumb));
        BufferedImage back=ImageIO.read(new java.io.ByteArrayInputStream(thumb));
        assertEquals(Thumb.SIDE,back.getWidth());assertEquals(Thumb.SIDE,back.getHeight());
        assertFalse("nothing see-through in it, so it says none",back.getColorModel().hasAlpha());
    }

    @Test public void aBusyPictureStepsDownTheSidesUntilItFits() throws Exception {
        // Noise is what PNG packs worst: 192 pixels of it are far over 32 KB.
        BufferedImage noise=new BufferedImage(900,700,BufferedImage.TYPE_INT_RGB);Random r=new Random(7);
        for(int y=0;y<700;y++)for(int x=0;x<900;x++)noise.setRGB(x,y,r.nextInt(0xFFFFFF));
        byte[] thumb=DesktopIcons.thumb(noise,1);
        assertNotNull(thumb);assertTrue(Thumb.fits(thumb));
        int side=ImageIO.read(new java.io.ByteArrayInputStream(thumb)).getWidth();
        List<Integer> sides=DesktopIcons.sides(700);
        assertTrue("it had to step down",side<Thumb.SIDE);assertTrue(sides.contains(side));
        // And the side before it did not fit: the first that fits is the one kept.
        int before=sides.get(sides.indexOf(side)-1);
        assertFalse(Thumb.fits(DesktopIcons.png(DesktopIcons.scaled(noise,100,0,700,before,false))));
        // Packed as tightly as the writer packs: never bigger than ImageIO's ordinary PNG.
        BufferedImage one=DesktopIcons.scaled(noise,100,0,700,96,false);
        assertTrue(DesktopIcons.png(one).length<=png(one,"png").length);
    }

    @Test public void seeThroughStaysSeeThrough() throws Exception {
        BufferedImage clear=new BufferedImage(400,400,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=clear.createGraphics();g.setColor(new Color(40,90,200));g.fillOval(100,100,200,200);g.dispose();
        BufferedImage back=ImageIO.read(new java.io.ByteArrayInputStream(DesktopIcons.thumb(clear,1)));
        assertTrue(back.getColorModel().hasAlpha());assertEquals(0,back.getRGB(2,2)>>>24);assertEquals(0xFF,back.getRGB(96,96)>>>24);
    }

    @Test public void aPhotographIsWornTheWayUpItWasTaken() throws Exception {
        assertEquals(6,DesktopIcons.orientation(exif(6,true)));
        assertEquals(8,DesktopIcons.orientation(exif(8,false)));
        assertEquals(1,DesktopIcons.orientation(png(flat(20,20,Color.RED),"jpg")));
        assertEquals(1,DesktopIcons.orientation("not a jpeg".getBytes()));
        assertEquals(1,DesktopIcons.orientation(new byte[]{(byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE1,0,40}));
        // Four pixels, each its own colour, turned as each orientation says.
        BufferedImage four=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);
        int tl=0xFF0000,tr=0x00FF00,bl=0x0000FF,br=0xFFFF00;
        four.setRGB(0,0,tl);four.setRGB(1,0,tr);four.setRGB(0,1,bl);four.setRGB(1,1,br);
        assertSame(four,DesktopIcons.turned(four,1));
        assertEquals("a quarter turn clockwise",bl,DesktopIcons.turned(four,6).getRGB(0,0)&0xFFFFFF);
        assertEquals(tl,DesktopIcons.turned(four,6).getRGB(1,0)&0xFFFFFF);
        assertEquals("a quarter turn back",tr,DesktopIcons.turned(four,8).getRGB(0,0)&0xFFFFFF);
        assertEquals("upside down",br,DesktopIcons.turned(four,3).getRGB(0,0)&0xFFFFFF);
        assertEquals("mirrored",tr,DesktopIcons.turned(four,2).getRGB(0,0)&0xFFFFFF);
        assertEquals(bl,DesktopIcons.turned(four,4).getRGB(0,0)&0xFFFFFF);
        assertEquals("across the diagonal",bl,DesktopIcons.turned(four,5).getRGB(1,0)&0xFFFFFF);
        assertEquals(br,DesktopIcons.turned(four,7).getRGB(0,0)&0xFFFFFF);
    }

    @Test public void aFileTooBigOrNotAPictureIsRefusedInWordsForAPerson() throws Exception {
        try{DesktopIcons.read("not a picture at all".getBytes());fail();}
        catch(IllegalArgumentException said){assertTrue(said.getMessage(),said.getMessage().startsWith("Mininotes cannot read that kind of picture"));}
    }

    // ---- made-up pictures --------------------------------------------------------------------------------------

    private static BufferedImage flat(int w,int h,Color colour) {
        BufferedImage one=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);Graphics2D g=one.createGraphics();g.setColor(colour);g.fillRect(0,0,w,h);g.dispose();return one;
    }
    private static byte[] png(BufferedImage image,String kind) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,kind,out);return out.toByteArray();
    }
    private static int count(BufferedImage image,int rgb) {
        int n=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if(image.getRGB(x,y)==rgb)n++;return n;
    }
    /** The start of a JPEG whose camera wrote down a turn: SOI, then APP1 with an EXIF block holding the one tag. */
    private static byte[] exif(int turn,boolean little) {
        java.nio.ByteBuffer tiff=java.nio.ByteBuffer.allocate(26).order(little?java.nio.ByteOrder.LITTLE_ENDIAN:java.nio.ByteOrder.BIG_ENDIAN);
        tiff.put(little?(byte)'I':(byte)'M').put(little?(byte)'I':(byte)'M').putShort((short)42).putInt(8);
        tiff.putShort((short)1).putShort((short)0x0112).putShort((short)3).putInt(1).putShort((short)turn).putShort((short)0).putInt(0);
        byte[] body=tiff.array();
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(0xFF);out.write(0xD8);out.write(0xFF);out.write(0xE1);
        int length=2+6+body.length;out.write(length>>8);out.write(length&0xFF);
        out.write('E');out.write('x');out.write('i');out.write('f');out.write(0);out.write(0);
        out.write(body,0,body.length);out.write(0xFF);out.write(0xDA);
        return out.toByteArray();
    }
}
