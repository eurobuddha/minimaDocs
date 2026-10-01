// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import static org.junit.Assert.*;

import com.eurobuddha.maxima.core.crypto.Hashes;
import com.eurobuddha.maxima.core.media.MediaManifest;
import com.eurobuddha.maxima.core.media.MediaService;
import com.eurobuddha.maxima.core.store.BlobStore;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import org.junit.Test;

/** The files kept with a shared note: the list that travels in the parcel, and what each end does about it. */
public class EnclosureTest {
    private static final String A="0b0f5a52-2d0e-4b8e-9c6a-1f1e2d3c4b5a", B="7d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a",
        C="c0ffee00-1111-4222-8333-444455556666";
    private static Enclosure.Listed up(String id,long bytes){return new Enclosure.Listed(id,"scan.pdf","application/pdf",bytes,"{\"v\":\"1\"}");}
    private static Enclosure.Listed notUp(String id,long bytes){return new Enclosure.Listed(id,"big.mov","video/quicktime",bytes,"");}
    private static Parcel.Sent note(List<Enclosure.Listed> files,long asOf) {
        return new Parcel.Sent("c","Home","b","Lists","Groceries","Bread\nMilk",true,
            Collections.<Parcel.Member>emptyList(),"PAGE","n",true,4L,true,files,asOf);
    }

    // ---- the list, on the wire ---------------------------------------------------------------------------------

    @Test public void theListGoesThereAndBackWhole() throws Exception {
        Parcel.Sent in=Parcel.open(Parcel.wrap(note(Arrays.asList(up(A,1234),notUp(B,20L*1024*1024)),99L)));
        assertEquals("Bread\nMilk",in.body);assertTrue(in.carries);assertEquals(4L,in.basedOn);
        assertEquals(99L,in.filesAsOf);assertEquals(2,in.files.size());
        Enclosure.Listed first=in.files.get(0);
        assertEquals(A,first.id);assertEquals("scan.pdf",first.name);assertEquals("application/pdf",first.kind);
        assertEquals(1234,first.bytes);assertEquals("{\"v\":\"1\"}",first.manifest);assertTrue(first.fetchable());
        assertFalse("too big, and not said where",in.files.get(1).fetchable());
    }

    @Test public void noListAndAnEmptyListAreNotTheSameThing() throws Exception {
        // Nothing on the list takes out whatever came from that device; no list takes out nothing.
        Parcel.Sent empty=Parcel.open(Parcel.wrap(note(Collections.<Enclosure.Listed>emptyList(),5L)));
        assertNotNull(empty.files);assertTrue(empty.files.isEmpty());
        assertNull(Parcel.open(Parcel.wrap(note(null,5L))).files);
    }

    @Test public void aParcelFromBeforeFilesTravelledStillReadsAndSaysNoList() throws Exception {
        byte[] older=Parcel.wrap(new Parcel.Sent("c","Home","b","Lists","Groceries","Bread",true,
            Collections.<Parcel.Member>emptyList(),"PAGE","n",true,4L,true));
        Parcel.Sent in=Parcel.open(older);
        assertEquals("Bread",in.body);assertTrue(in.carries);assertNull(in.files);
        // And what this build writes is what that one wrote, and then more: a reader that stops after the
        // flag saying it carries - which is where the older build stops - reads the same note.
        byte[] newer=Parcel.wrap(new Parcel.Sent("c","Home","b","Lists","Groceries","Bread",true,
            Collections.<Parcel.Member>emptyList(),"PAGE","n",true,4L,true,Arrays.asList(up(A,10)),7L));
        assertArrayEquals(older,Arrays.copyOf(newer,older.length));
    }

    @Test public void aListCutShortIsNotAParcel() throws Exception {
        byte[] whole=Parcel.wrap(note(Arrays.asList(up(A,10),up(B,20)),7L));
        byte[] older=Parcel.wrap(note(null,7L));
        // Anywhere inside the list, the whole parcel is refused: half a list could take out what it did not name.
        for(int cut=whole.length-1;cut>older.length;cut--)assertNull("cut to "+cut,Parcel.open(Arrays.copyOf(whole,cut)));
        assertNotNull(Parcel.open(Arrays.copyOf(whole,older.length)));
    }

    @Test public void aListLongerThanANoteKeepsIsRefused() throws Exception {
        byte[] head=Parcel.wrap(note(Collections.<Enclosure.Listed>emptyList(),7L));
        // The count is the last four bytes of a parcel with an empty list.
        for(int lie:new int[]{Enclosure.FILES_MOST+1,-1,Integer.MAX_VALUE}) {
            byte[] said=head.clone();int at=said.length-4;
            said[at]=(byte)(lie>>>24);said[at+1]=(byte)(lie>>>16);said[at+2]=(byte)(lie>>>8);said[at+3]=(byte)lie;
            assertNull("a list of "+lie,Parcel.open(said));
        }
        // And a list of that many is written as no more than that many.
        List<Enclosure.Listed> many=new ArrayList<>();
        for(int i=0;i<Enclosure.FILES_MOST+5;i++)many.add(up(UUID.randomUUID().toString(),10));
        assertEquals(Enclosure.FILES_MOST,Parcel.open(Parcel.wrap(note(many,1L))).files.size());
    }

    @Test public void aManifestTooLongToBeOneIsRefusedAtBothEnds() throws Exception {
        char[] lots=new char[Parcel.MANIFEST_MOST+1];Arrays.fill(lots,'x');
        try{Parcel.wrap(note(Arrays.asList(new Enclosure.Listed(A,"a","b",1,new String(lots))),1L));fail("wrote it");}
        catch(java.io.IOException expected){}
        byte[] fits=Parcel.wrap(note(Arrays.asList(new Enclosure.Listed(A,"a","b",1,"m")),1L));
        // The manifest's own length is the four bytes before its one byte: say it is longer than any may be.
        byte[] lie=fits.clone();int at=lie.length-1-4,n=Parcel.MANIFEST_MOST+1;
        lie[at]=(byte)(n>>>24);lie[at+1]=(byte)(n>>>16);lie[at+2]=(byte)(n>>>8);lie[at+3]=(byte)n;
        assertNull(Parcel.open(lie));
    }

    @Test public void aNegativeSizeIsRefused() throws Exception {
        byte[] fits=Parcel.wrap(note(Arrays.asList(new Enclosure.Listed(A,"a","b",5,"m")),1L));
        // The size is the eight bytes before the manifest's length and its one byte.
        byte[] lie=fits.clone();int at=lie.length-1-4-8;
        for(int i=0;i<8;i++)lie[at+i]=(byte)0xff;
        assertNull(Parcel.open(lie));
    }

    @Test public void aListThatWillNotFitStopsSayingWhereBiggestFirstAndThenIsNotSaid() throws Exception {
        char[] big=new char[9000],small=new char[3000];Arrays.fill(big,'b');Arrays.fill(small,'s');
        List<Enclosure.Listed> files=Arrays.asList(new Enclosure.Listed(A,"a","k",1,new String(small)),
            new Enclosure.Listed(B,"b","k",1,new String(big)));
        Parcel.Sent out=note(files,3L);
        int whole=Parcel.wrap(out).length;
        assertEquals(whole,Parcel.wrap(out,whole).length);
        // Room for one of them: the bigger stops saying where, and is still named.
        Parcel.Sent lighter=Parcel.open(Parcel.wrap(out,whole-8000));
        assertEquals(2,lighter.files.size());
        assertFalse(lighter.files.get(0).manifest.isEmpty());assertTrue(lighter.files.get(1).manifest.isEmpty());
        // Room for neither: both named, neither saying where.
        Parcel.Sent names=Parcel.open(Parcel.wrap(out,whole-11000));
        assertEquals(2,names.files.size());
        for(Enclosure.Listed one:names.files)assertTrue(one.manifest.isEmpty());
        // Not even room for the names: no list at all, which at the far end takes nothing out.
        int bare=Parcel.wrap(note(null,3L)).length;
        assertNull(Parcel.open(Parcel.wrap(out,bare)).files);
    }

    // ---- what a list means at the receiving end ------------------------------------------------------------------

    @Test public void whatIsNotHereIsFetchedAndWhatIsHereIsLeftAlone() {
        Enclosure.Plan plan=Enclosure.plan(Arrays.asList(up(A,10),up(B,10),notUp(C,10)),
            Collections.<String>emptySet(),Collections.singleton(B),Collections.<String>emptySet(),Collections.<String>emptySet());
        assertEquals(1,plan.fetch.size());assertEquals(A,plan.fetch.get(0).id);
        assertTrue("not said where, so nothing to fetch yet",plan.drop.isEmpty());
    }

    @Test public void whatCameFromThemAndIsNoLongerListedGoesAndNothingElseDoes() {
        // A came from them and is still listed; B came from them and is not; C was added here, and is not
        // theirs to take out.
        Enclosure.Plan plan=Enclosure.plan(Arrays.asList(up(A,10)),new HashSet<>(Arrays.asList(A,B)),
            new HashSet<>(Arrays.asList(A,B,C)),Collections.<String>emptySet(),Collections.<String>emptySet());
        assertEquals(Collections.singletonList(B),plan.drop);assertTrue(plan.fetch.isEmpty());
        // An empty list takes out all of theirs - and still nothing of this device's own.
        plan=Enclosure.plan(Collections.<Enclosure.Listed>emptyList(),Collections.singleton(A),
            new HashSet<>(Arrays.asList(A,C)),Collections.singleton(B),Collections.<String>emptySet());
        assertEquals(Collections.singletonList(A),plan.drop);assertEquals(Collections.singletonList(B),plan.forget);
    }

    @Test public void whatWasTakenOutHereIsNotFetchedBack() {
        Enclosure.Plan plan=Enclosure.plan(Arrays.asList(up(A,10)),Collections.<String>emptySet(),
            Collections.<String>emptySet(),Collections.<String>emptySet(),Collections.singleton(A));
        assertTrue(plan.fetch.isEmpty());
    }

    @Test public void somethingAlreadyWaitingIsToldWhereItIsNow() {
        Enclosure.Plan plan=Enclosure.plan(Arrays.asList(up(A,10),notUp(B,10)),Collections.<String>emptySet(),
            Collections.<String>emptySet(),new HashSet<>(Arrays.asList(A,B)),Collections.<String>emptySet());
        assertTrue(plan.fetch.isEmpty());
        assertEquals(1,plan.refresh.size());assertEquals(A,plan.refresh.get(0).id);
        assertTrue("still listed, so still waiting",plan.forget.isEmpty());
    }

    @Test public void anIdThatCouldBeAPathIsNeverTaken() {
        for(String bad:new String[]{"../x","a/b","a\\b","",".."," x"}) {
            assertFalse(bad,Enclosure.plainId(bad));
            Enclosure.Plan plan=Enclosure.plan(Arrays.asList(up(bad,10)),Collections.<String>emptySet(),
                Collections.<String>emptySet(),Collections.<String>emptySet(),Collections.<String>emptySet());
            assertTrue(bad,plan.fetch.isEmpty());
        }
        assertTrue(Enclosure.plainId(A));
    }

    @Test public void theSameFileTwiceIsOneFile() {
        Enclosure.Plan plan=Enclosure.plan(Arrays.asList(up(A,10),up(A,10)),Collections.<String>emptySet(),
            Collections.<String>emptySet(),Collections.<String>emptySet(),Collections.<String>emptySet());
        assertEquals(1,plan.fetch.size());
    }

    // ---- how often, and what a card says ---------------------------------------------------------------------

    @Test public void triedAgainLessAndLessOftenAndNeverNever() {
        long minute=60_000L,now=1_000_000_000L;
        assertTrue(Enclosure.due(0,0,now));
        assertFalse(Enclosure.due(now,1,now+minute-1));assertTrue(Enclosure.due(now,1,now+minute));
        assertFalse(Enclosure.due(now,4,now+8*minute-1));assertTrue(Enclosure.due(now,4,now+8*minute));
        assertFalse(Enclosure.due(now,7,now+15*minute-1));assertTrue(Enclosure.due(now,7,now+15*minute));
        assertFalse(Enclosure.due(now,12,now+60*minute-1));assertTrue(Enclosure.due(now,12,now+60*minute));
        assertTrue("a clock put back",Enclosure.due(now,12,now-1));
    }

    @Test public void theListIsSaidAgainOnlyAFewTimes() {
        long now=1_000_000_000L;
        assertTrue(Enclosure.tellAgain(0,0,now));
        assertFalse(Enclosure.tellAgain(now,Enclosure.TELLS_MOST,now+24L*60*60*1000));
        assertTrue(Enclosure.sayMissing(4));assertFalse(Enclosure.sayMissing(3));assertFalse(Enclosure.sayMissing(0));
    }

    @Test public void aCardSaysWhereItsFileIsUntilItIsWithEverybody() {
        assertEquals("",Enclosure.state(10,false,false,0));
        assertEquals(Enclosure.TOO_BIG,Enclosure.state(Enclosure.MOST+1,true,false,1));
        assertEquals(Enclosure.EMPTY,Enclosure.state(0,true,false,1));
        assertEquals(Enclosure.NOT_SENT,Enclosure.state(Enclosure.MOST,true,false,1));
        assertEquals(Enclosure.NOT_EVERYBODY,Enclosure.state(10,true,true,1));
        assertEquals("",Enclosure.state(10,true,true,0));
        // Only between the owner's devices a ready file has gone nowhere: it waits for them to come for it.
        assertEquals(Enclosure.WAITING,Enclosure.state(10,true,true,1,true));
        assertEquals("",Enclosure.state(10,true,true,0,true));
        assertEquals(Enclosure.NOT_SENT,Enclosure.state(10,true,false,1,true));
        assertEquals(Enclosure.TOO_BIG,Enclosure.state(Enclosure.MOST+1,true,true,1,true));
    }

    // ---- the transport's own media service, as this app uses it ---------------------------------------------------

    @Test public void theCeilingIsTheTransports() {
        assertEquals(MediaService.MAX_MESH_FILE_BYTES,Enclosure.MOST);
        assertTrue(Enclosure.MOST<=Attachment.LIMIT);
    }

    /**
     * A file at the ceiling, sent up and fetched back through the transport's own code with nothing but a
     * shelf between them - no node, no network. What it went up as fits in the list, and what comes back is
     * the file; a manifest that has been changed brings back nothing.
     */
    @Test public void aFileAtTheCeilingGoesUpFitsTheListAndComesBackTheSame() throws Exception {
        Hashes.setSha3(Sha3::of);
        File shelf=Files.createTempDirectory("mininotes-shelf").toFile();
        try {
            MediaService here=new MediaService(null,new BlobStore(shelf));
            byte[] plain=new byte[(int)Enclosure.MOST];new Random(7).nextBytes(plain);
            MediaManifest made=here.publish(plain,"application/octet-stream");
            String said=made.encode();
            assertTrue("manifest of "+said.length()+" bytes",said.length()<Parcel.MANIFEST_MOST/2);
            Parcel.Sent in=Parcel.open(Parcel.wrap(note(Arrays.asList(new Enclosure.Listed(A,"x.bin","application/octet-stream",plain.length,said)),1L),Envelope.MAX_TEXT));
            assertFalse("it fits whole",in.files.get(0).manifest.isEmpty());
            byte[] back=new MediaService(null,new BlobStore(shelf)).fetch(MediaManifest.decode(in.files.get(0).manifest));
            assertArrayEquals(plain,back);
            // One more byte than it was: refused, never handed back as something else.
            MediaManifest lie=new MediaManifest(made.mime,made.size+1,made.keyHex,made.nonceHex,made.sha3Hex,made.chunkIds,made.sources);
            try{here.fetch(lie);fail("fetched a file that is not the one listed");}catch(Exception expected){}
            try{here.publish(new byte[(int)Enclosure.MOST+1],"x");fail("sent up more than the ceiling");}catch(IllegalArgumentException expected){}
        } finally {
            try(java.util.stream.Stream<java.nio.file.Path> all=Files.walk(shelf.toPath())) {
                all.sorted(Comparator.reverseOrder()).forEach(p->p.toFile().delete());
            }
        }
    }
}
