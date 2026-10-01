package org.mininotes.android;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class RecordingTest {

    @Test public void aRecordingIsNamedForWhenItWasMade() {
        ZonedDateTime when=ZonedDateTime.of(2026,9,28,14,5,33,0,ZoneId.of("Europe/London"));
        assertEquals("Recording 28 Sep, 14:05.m4a",Recording.name(when,"m4a"));
        assertEquals("Recording 3 Jan, 09:30.wav",Recording.name(ZonedDateTime.of(2027,1,3,9,30,0,0,ZoneId.of("UTC")),"wav"));
        // Kept as it is: nothing in it is a path, a newline or a leading dot.
        assertEquals("Recording 28 Sep, 14:05.m4a",Attachment.named(Recording.name(when,"m4a")));
    }

    @Test public void aLengthIsSaidAsAClockSaysIt() {
        assertEquals("0:00",Recording.clock(0));
        assertEquals("0:00",Recording.clock(-5));
        assertEquals("0:07",Recording.clock(7_900));
        assertEquals("1:00",Recording.clock(60_000));
        assertEquals("12:34",Recording.clock((12*60+34)*1000L));
        assertEquals("1:02:03",Recording.clock((3600+2*60+3)*1000L));
    }

    @Test public void aRecordingStopsUnderWhatAFileMayBeAndStillTravel() {
        assertEquals(Enclosure.MOST,Recording.MOST);
        assertTrue(Recording.STOP_AT<Enclosure.MOST);
        assertTrue(Enclosure.travels(Recording.STOP_AT));
        // The phone: 48 kbps is 6,000 bytes a second, at least half an hour under the ceiling.
        assertEquals(45,Recording.minutes(Recording.PHONE_BITS/8));
        assertTrue(Recording.minutes(Recording.PHONE_BITS/8)>=30);
        // The PC: one byte a sample at 16 kHz.
        assertEquals(17,Recording.minutes(Recording.PC_RATE));
        assertEquals(0,Recording.minutes(0));
        assertEquals("Up to 45 min · leaving keeps it",Recording.limit(Recording.PHONE_BITS/8));
    }

    @Test public void soundIsKnownByItsTypeOrItsName() {
        assertTrue(Recording.audio("x","audio/mp4"));
        assertTrue(Recording.audio("Recording 28 Sep, 14:05.m4a",null));
        assertTrue(Recording.audio("SONG.MP3",Attachment.ANYTHING));
        assertFalse(Recording.audio("tickets.pdf","application/pdf"));
        assertTrue(Recording.wav("a.WAV",null));
        assertTrue(Recording.wav("a","audio/x-wav"));
        assertFalse(Recording.wav("a.m4a","audio/mp4"));
    }

    @Test public void aWavSaysHowLongItIs() throws Exception {
        // Eight kilobytes a second of μ-law, two and a half seconds of it, with a fact chunk before the data.
        ByteArrayOutputStream wav=new ByteArrayOutputStream();
        wav.write("RIFF".getBytes());le(wav,4+26+12+8+20_000);wav.write("WAVE".getBytes());
        wav.write("fmt ".getBytes());le(wav,18);le16(wav,7);le16(wav,1);le(wav,8000);le(wav,8000);le16(wav,1);le16(wav,8);le16(wav,0);
        wav.write("fact".getBytes());le(wav,4);le(wav,20_000);
        wav.write("data".getBytes());le(wav,20_000);wav.write(new byte[20_000]);
        assertEquals(2500,Recording.millis(wav.toByteArray()));
        assertEquals(-1,Recording.millis(new byte[]{1,2,3}));
        assertEquals(-1,Recording.millis(null));
    }

    @Test public void anMp4SaysHowLongItIsWhereverItsIndexIs() throws Exception {
        // As the phone's recorder writes it: the sound first, the index (moov) last.
        ByteArrayOutputStream mp4=new ByteArrayOutputStream();
        box(mp4,"ftyp",new byte[8]);box(mp4,"mdat",new byte[3000]);
        ByteArrayOutputStream mvhd=new ByteArrayOutputStream();
        mvhd.write(new byte[]{0,0,0,0});be(mvhd,0);be(mvhd,0);be(mvhd,1000);be(mvhd,7_250);mvhd.write(new byte[80]);
        ByteArrayOutputStream moov=new ByteArrayOutputStream();box(moov,"mvhd",mvhd.toByteArray());
        box(mp4,"moov",moov.toByteArray());
        assertEquals(7_250,Recording.millis(mp4.toByteArray()));
        // Cut short: nothing that adds up, so nothing is said.
        byte[] cut=java.util.Arrays.copyOf(mp4.toByteArray(),mp4.size()-40);
        assertEquals(-1,Recording.millis(cut));
    }

    private static void box(ByteArrayOutputStream out,String type,byte[] body) throws Exception {be(out,8+body.length);out.write(type.getBytes());out.write(body);}
    private static void be(ByteArrayOutputStream out,int v){out.write(v>>>24);out.write(v>>>16);out.write(v>>>8);out.write(v);}
    private static void le(ByteArrayOutputStream out,int v){out.write(v);out.write(v>>>8);out.write(v>>>16);out.write(v>>>24);}
    private static void le16(ByteArrayOutputStream out,int v){out.write(v);out.write(v>>>8);}
}
