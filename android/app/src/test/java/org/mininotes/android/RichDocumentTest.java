package org.mininotes.android;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RichDocumentTest {
    private static final String A="00000000-0000-0000-0000-000000000001",B="00000000-0000-0000-0000-000000000002",C="00000000-0000-0000-0000-000000000003";
    private static String file(int i){return String.format(Locale.ROOT,"10000000-0000-0000-0000-%012d",i);}
    private static RichDocument initial(){return RichDocument.empty("docx").write(A,file(1),0);}
    @Test public void aSequentialSaveReplacesTheHeadAndReplaysCannotUndoIt() {
        RichDocument one=initial(),two=one.write(A,file(2),0);
        assertEquals(Collections.singleton(file(2)),new HashSet<>(one.merge(two).heads.values()));
        assertEquals(two.text(),two.merge(one).text());assertEquals(two.text(),RichDocument.read(two.text()).text());
    }
    @Test public void concurrentEditsSurviveEvenWithDifferentRevisionCounts() {
        RichDocument base=initial(),a=base.write(A,file(2),0),b=base.write(B,file(3),0);
        Arriving.Decision d=Arriving.weigh(a.text(),10,base.text(),1,b.text(),2,true,false);
        assertEquals(Arriving.What.MERGED,d.what);
        assertEquals(new HashSet<>(Arrays.asList(file(2),file(3))),new HashSet<>(RichDocument.read(d.text).heads.values()));
        assertEquals(a.merge(b).text(),b.merge(a).text());
    }
    @Test public void explicitResolutionDoesNotBringDiscardedHeadsBackOnReplay() {
        RichDocument base=initial(),a=base.write(A,file(2),0),b=base.write(B,file(3),0),joined=a.merge(b);
        RichDocument chosen=joined.write(C,file(4),0);
        assertEquals(chosen.text(),chosen.merge(a).merge(b).merge(base).text());
    }
    @Test public void editingWhileAnUnseenVersionArrivesKeepsBoth() {
        RichDocument base=initial(),remote=base.write(B,file(3),0),local=base.write(A,file(2),0);
        RichDocument current=remote.merge(local);
        RichDocument moreLocal=local.write(A,file(4),current.clock.get(A));
        assertEquals(new HashSet<>(Arrays.asList(file(3),file(4))),new HashSet<>(current.merge(moreLocal).heads.values()));
    }
    @Test public void threeDevicesConvergeRegardlessOfDeliveryOrderOrDuplication() {
        RichDocument base=initial();List<RichDocument> states=Arrays.asList(base.write(A,file(2),0),base.write(B,file(3),0),base.write(C,file(4),0));
        String expected=states.get(0).merge(states.get(1)).merge(states.get(2)).text();
        for(int i=0;i<3;i++)for(int j=0;j<3;j++)if(i!=j){int k=3-i-j;
            assertEquals(expected,states.get(i).merge(states.get(j)).merge(states.get(k)).merge(states.get(i)).text());
        }
    }
    @Test public void deterministicRandomDeliveryConverges() {
        Random random=new Random(17);RichDocument[] devices={initial(),initial(),initial()};String[] actors={A,B,C};
        for(int i=5;i<500;i++) {
            int to=random.nextInt(3),from=random.nextInt(3);
            if(random.nextBoolean())devices[to]=devices[to].merge(devices[from]);
            else devices[to]=devices[to].write(actors[to],file(i),0);
        }
        String forward=devices[0].merge(devices[1]).merge(devices[2]).text();
        String reverse=devices[2].merge(devices[0]).merge(devices[1]).text();
        assertEquals(forward,reverse);assertEquals(forward,RichDocument.read(forward).merge(devices[0]).text());
    }
    @Test public void malformedOrConflictingIdentitiesNeverReachTheTextMerge() {
        String one=initial().text();
        for(String bad:Arrays.asList(one.replace(file(1),"../file"),one.replace("v "+A+" 1","v "+A+" -1"),one+"\nunknown",one+"\nv "+A+" 1"))assertNull(RichDocument.read(bad));
        RichDocument lie=RichDocument.empty("docx").write(A,file(2),0);
        assertThrows(IllegalArgumentException.class,()->initial().merge(lie));
        assertEquals(Arriving.What.OLDER,Arriving.weigh(one,1,null,0,lie.text(),1).what);
        assertEquals(Arriving.What.OLDER,Arriving.weigh(one,1,null,0,"some plain text",20).what);
    }
    @Test public void registerTravelsThroughExistingParcelWithoutLosingMetadata() throws Exception {
        RichDocument doc=initial();
        Parcel.Sent original=new Parcel.Sent("c","Home","b","Docs","Report",doc.text(),true,
            Collections.emptyList(),"PAGE","n",true,0L,true);
        assertEquals(doc.text(),RichDocument.read(Parcel.open(Parcel.wrap(original)).body).text());
    }
    @Test public void copyingABackupRemapsItsFileReferencesAndRejectsMissingFiles() {
        RichDocument doc=initial();
        assertEquals(Collections.singleton(file(9)),new HashSet<>(doc.remap(Collections.singletonMap(file(1),file(9))).heads.values()));
        assertThrows(IllegalArgumentException.class,()->doc.remap(Collections.emptyMap()));
    }    @Test public void readOnlyReplicasStillMergeCausalUpdatesAndRejectMalformedNewDocuments(){
        RichDocument base=initial(),a=base.write(A,file(2),0),b=base.write(B,file(3),0);
        assertEquals(a.merge(b).text(),Arriving.copy(a.text(),50,0,b.text(),1).text);
        assertEquals(Arriving.What.OLDER,Arriving.weigh(null,0,null,0,"minimaDocs/1 docx\nbad",1).what);
        assertNull(RichDocument.read(base.text().replace("v "+A+" 1","v "+A+" 2")));
    }

}
