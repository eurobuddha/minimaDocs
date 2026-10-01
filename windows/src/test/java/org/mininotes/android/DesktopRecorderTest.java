package org.mininotes.android;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.*;

/** What the PC writes when it records: μ-law in a WAV that Java Sound, the phone and Windows' players read. No microphone is opened. */
public class DesktopRecorderTest {

    @Test public void muLawIsTheTelephonesOwn() {
        // G.711's own table at its ends and middle: silence is 0xFF, the loudest either way 0x80 and 0x00.
        assertEquals((byte)0xFF,DesktopRecorder.mulaw((short)0));
        assertEquals((byte)0x80,DesktopRecorder.mulaw(Short.MAX_VALUE));
        assertEquals((byte)0x00,DesktopRecorder.mulaw(Short.MIN_VALUE));
        assertEquals((byte)0x7F,DesktopRecorder.mulaw((short)-1));
    }

    @Test public void theWavHeaderSaysWhatFollows() {
        byte[] sound=new byte[16_001];java.util.Arrays.fill(sound,(byte)0xFF);
        byte[] wav=DesktopRecorder.wav(sound);
        ByteBuffer b=ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals("RIFF",new String(wav,0,4));assertEquals(wav.length-8,b.getInt(4));assertEquals("WAVE",new String(wav,8,4));
        assertEquals("fmt ",new String(wav,12,4));assertEquals(18,b.getInt(16));
        assertEquals(7,b.getShort(20));assertEquals(1,b.getShort(22));assertEquals(Recording.PC_RATE,b.getInt(24));
        assertEquals(Recording.PC_RATE,b.getInt(28));assertEquals(1,b.getShort(32));assertEquals(8,b.getShort(34));assertEquals(0,b.getShort(36));
        assertEquals("fact",new String(wav,38,4));assertEquals(16_001,b.getInt(46));
        assertEquals("data",new String(wav,50,4));assertEquals(16_001,b.getInt(54));
        assertEquals(DesktopRecorder.HEADER,58);
        // An odd count is padded to a whole word, as RIFF asks.
        assertEquals(DesktopRecorder.HEADER+16_002,wav.length);
        assertEquals(1000,Recording.millis(wav));
    }

    @Test public void javaSoundReadsItBackAsTheSoundThatWentIn() throws Exception {
        // Half a second of a 440 Hz tone, through μ-law and back as Java Sound's own converter widens it.
        short[] tone=new short[Recording.PC_RATE/2];byte[] sound=new byte[tone.length];
        for(int i=0;i<tone.length;i++){tone[i]=(short)(12000*Math.sin(2*Math.PI*440*i/Recording.PC_RATE));sound[i]=DesktopRecorder.mulaw(tone[i]);}
        AudioInputStream in=AudioSystem.getAudioInputStream(new ByteArrayInputStream(DesktopRecorder.wav(sound)));
        assertEquals(AudioFormat.Encoding.ULAW,in.getFormat().getEncoding());
        assertEquals(Recording.PC_RATE,(int)in.getFormat().getSampleRate());
        assertEquals(tone.length,in.getFrameLength());
        AudioInputStream wide=AudioSystem.getAudioInputStream(new AudioFormat(Recording.PC_RATE,16,1,true,false),in);
        byte[] back=wide.readAllBytes();
        assertEquals(tone.length*2,back.length);
        for(int i=0;i<tone.length;i++) {
            short heard=(short)((back[2*i]&0xFF)|(back[2*i+1]<<8));
            // μ-law keeps about one part in thirty of a loud sample, finer when quiet.
            assertTrue("sample "+i,Math.abs(heard-tone[i])<=Math.max(8,Math.abs(tone[i])/30+4));
        }
    }
}
