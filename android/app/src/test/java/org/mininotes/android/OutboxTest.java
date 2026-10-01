package org.mininotes.android;
import org.junit.Test;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class OutboxTest {
    private static final String ANA="MxANA@1.2.3.4:9001", TABLET="MxTABLET@5.6.7.8:9001";
    private static final Outbox.Page PAGE=new Outbox.Page("p1","b1","c1",10);
    private static final Outbox.Page ELSEWHERE=new Outbox.Page("p2","b2","c2",10);

    private static Sharing.Rule on(Sharing.Scope scope,String target,String address,boolean mine) {
        return new Sharing.Rule(scope,target,address,mine);
    }
    private static Map<String,Long> nothingSent(){return new HashMap<>();}
    private static Map<String,Long> sent(String address,String page,long revision) {
        Map<String,Long> got=new HashMap<>();got.put(Outbox.mark(address,page),revision);return got;
    }

    @Test public void aPageNobodyReceivesIsOwedToNobody() {
        assertTrue(Outbox.waiting(List.of(),List.of(PAGE),nothingSent()).isEmpty());
        assertTrue(Outbox.waiting(List.of(on(Sharing.Scope.BOOK,"b9",ANA,false)),List.of(PAGE),nothingSent()).isEmpty());
    }
    @Test public void aNoteDeepInsideIsOwedByARuleOnAnythingAboveIt() {
        // Three collections down: its nearest is its book, its top its collection, for whatever still reads those.
        Outbox.Page deep=new Outbox.Page("p3",List.of("top","middle","inner"),4);
        assertEquals("inner",deep.book);assertEquals("top",deep.collection);
        assertEquals(List.of("top","middle","inner","p3"),deep.path());
        assertEquals(1,Outbox.waiting(List.of(on(Sharing.Scope.THING,"top",ANA,false)),List.of(deep),nothingSent()).size());
        assertEquals(1,Outbox.waiting(List.of(on(Sharing.Scope.BOOK,"middle",ANA,false)),List.of(deep),nothingSent()).size());
        assertTrue(Outbox.waiting(List.of(on(Sharing.Scope.THING,"elsewhere",ANA,false)),List.of(deep),nothingSent()).isEmpty());
        // On Home: nothing above it, and only its own rule or the library's reaches it.
        Outbox.Page home=new Outbox.Page("p4",List.of(),4);
        assertEquals("",home.book);assertEquals("",home.collection);assertEquals(List.of("p4"),home.path());
        // Made the old way, its two fields are its path.
        assertEquals(List.of("c1","b1","p1"),PAGE.path());assertNull(PAGE.above);
    }
    @Test public void aSharedPageThatHasNeverGoneIsOwed() {
        List<Outbox.Wait> owed=Outbox.waiting(List.of(on(Sharing.Scope.BOOK,"b1",ANA,false)),List.of(PAGE),nothingSent());
        assertEquals(1,owed.size());
        assertEquals(ANA,owed.get(0).address);
        assertEquals("p1",owed.get(0).page);
        assertEquals(10,owed.get(0).revision);
        assertFalse(owed.get(0).mine);
    }
    @Test public void theRevisionThatGotThroughIsWhatClearsIt() {
        List<Sharing.Rule> rules=List.of(on(Sharing.Scope.PAGE,"p1",ANA,false));
        assertEquals(1,Outbox.waiting(rules,List.of(PAGE),sent(ANA,"p1",9)).size());
        assertTrue(Outbox.waiting(rules,List.of(PAGE),sent(ANA,"p1",10)).isEmpty());
        // A clock that went backwards must not resurrect a delivery that already happened.
        assertTrue(Outbox.waiting(rules,List.of(PAGE),sent(ANA,"p1",11)).isEmpty());
    }
    @Test public void whatOneAddressGotSaysNothingAboutAnother() {
        List<Sharing.Rule> rules=List.of(on(Sharing.Scope.LIBRARY,Sharing.EVERYTHING,TABLET,true),
                                         on(Sharing.Scope.BOOK,"b1",ANA,false));
        List<Outbox.Wait> owed=Outbox.waiting(rules,List.of(PAGE),sent(TABLET,"p1",10));
        assertEquals(1,owed.size());
        assertEquals(ANA,owed.get(0).address);
    }
    @Test public void anAddressReachedByTwoRulesIsOwedThePageOnce() {
        List<Sharing.Rule> rules=List.of(on(Sharing.Scope.LIBRARY,Sharing.EVERYTHING,ANA,false),
                                         on(Sharing.Scope.BOOK,"b1",ANA,true));
        List<Outbox.Wait> owed=Outbox.waiting(rules,List.of(PAGE),nothingSent());
        assertEquals(1,owed.size());
        assertTrue("Named as a device at either level, it is a device",owed.get(0).mine);
    }
    @Test public void aRuleOnOneBookNeverOwesAnother() {
        List<Outbox.Wait> owed=Outbox.waiting(List.of(on(Sharing.Scope.BOOK,"b1",ANA,false)),
            Arrays.asList(PAGE,ELSEWHERE),nothingSent());
        assertEquals(1,owed.size());
        assertEquals("p1",owed.get(0).page);
    }
    @Test public void howManyPagesAndHowManyEachAddressIsOwed() {
        List<Sharing.Rule> rules=List.of(on(Sharing.Scope.LIBRARY,Sharing.EVERYTHING,TABLET,true),
                                         on(Sharing.Scope.BOOK,"b1",ANA,false));
        List<Outbox.Wait> owed=Outbox.waiting(rules,Arrays.asList(PAGE,ELSEWHERE),nothingSent());
        assertEquals(3,owed.size());
        assertEquals(2,Outbox.pages(owed));
        Map<String,Integer> each=Outbox.addresses(owed);
        assertEquals(Integer.valueOf(2),each.get(TABLET));
        assertEquals(Integer.valueOf(1),each.get(ANA));
        assertEquals("The order the rules named them",List.of(TABLET,ANA),List.copyOf(each.keySet()));
    }
    @Test public void oneAddressAndOnePageMakeOneKey() {
        assertEquals(Outbox.mark(ANA,"p1"),Outbox.mark(ANA,"p1"));
        assertNotEquals(Outbox.mark(ANA,"p1"),Outbox.mark(ANA,"p2"));
        assertNotEquals(Outbox.mark(ANA,"p1"),Outbox.mark(TABLET,"p1"));
        // Neither part may run into the other and make two different pairs look like one.
        assertNotEquals(Outbox.mark("a","bc"),Outbox.mark("ab","c"));
    }

    // ---- handed over, and not yet answered -------------------------------------------------------------

    private static final long MINUTE=60_000L, T0=1_000_000_000L;
    private static Map<String,Long> at(Object... pairs) {
        Map<String,Long> map=new HashMap<>();
        for(int i=0;i<pairs.length;i+=2)map.put((String)pairs[i],((Number)pairs[i+1]).longValue());
        return map;
    }

    @Test public void whatTheyHaveSaidTheyHaveIsNotSentAgain() {
        List<Outbox.Handed> handed=List.of(new Outbox.Handed(ANA,"p1",5,T0,1));
        assertTrue(Outbox.due(handed,at(Outbox.mark(ANA,"p1"),5),at("p1",5),T0+60*MINUTE).isEmpty());
    }

    @Test public void silenceIsTriedAgainButNotAtOnce() {
        List<Outbox.Handed> handed=List.of(new Outbox.Handed(ANA,"p1",5,T0,1));
        assertTrue("half a minute is a phone looking the other way",
            Outbox.due(handed,at(),at("p1",5),T0+MINUTE/2).isEmpty());
        assertEquals(1,Outbox.due(handed,at(),at("p1",5),T0+MINUTE).size());
    }

    @Test public void anAnswerForAnOlderRevisionIsNotAnAnswerForThisOne() {
        List<Outbox.Handed> handed=List.of(new Outbox.Handed(ANA,"p1",5,T0,1));
        assertEquals(1,Outbox.due(handed,at(Outbox.mark(ANA,"p1"),4),at("p1",5),T0+MINUTE).size());
    }

    @Test public void whatOneAddressSaidSaysNothingForAnother() {
        List<Outbox.Handed> handed=List.of(new Outbox.Handed(ANA,"p1",5,T0,1),new Outbox.Handed(TABLET,"p1",5,T0,1));
        List<Outbox.Handed> due=Outbox.due(handed,at(Outbox.mark(TABLET,"p1"),5),at("p1",5),T0+MINUTE);
        assertEquals(1,due.size());
        assertEquals(ANA,due.get(0).address);
    }

    @Test public void aNoteWrittenInSinceIsNotSentByTheRetry() {
        // What would go now is newer than anybody asked to send, and the note may be set to "when I ask".
        List<Outbox.Handed> handed=List.of(new Outbox.Handed(ANA,"p1",5,T0,1));
        assertTrue(Outbox.due(handed,at(),at("p1",6),T0+60*MINUTE).isEmpty());
    }

    @Test public void aNoteThatIsGoneIsNotSentAtAll() {
        List<Outbox.Handed> handed=List.of(new Outbox.Handed(ANA,"p1",5,T0,1));
        assertTrue(Outbox.due(handed,at(),at(),T0+60*MINUTE).isEmpty());
    }

    @Test public void theWaitsGrowAndThenSettle() {
        assertEquals(MINUTE,Outbox.againAfter(0));
        assertEquals(MINUTE,Outbox.againAfter(1));
        assertEquals(2*MINUTE,Outbox.againAfter(2));
        assertEquals(4*MINUTE,Outbox.againAfter(3));
        assertEquals(8*MINUTE,Outbox.againAfter(4));
        assertEquals(15*MINUTE,Outbox.againAfter(5));
        assertEquals("a phone back after a month is still owed what it missed",
            15*MINUTE,Outbox.againAfter(5000));
    }

    @Test public void aClockThatWentBackwardsDoesNotPostponeItForEver() {
        List<Outbox.Handed> handed=List.of(new Outbox.Handed(ANA,"p1",5,T0,3));
        assertEquals(1,Outbox.due(handed,at(),at("p1",5),T0-MINUTE).size());
    }

    // ---- answers kept until their device is in reach ----

    private static byte[] page(int n){byte[] one=new byte[16];one[15]=(byte)n;return one;}

    @Test public void aKeptAnswerIsTheLatestForEachDeviceAndNote() {
        Outbox.Answers kept=new Outbox.Answers();
        kept.keep(ANA,page(1),5,false,T0);
        kept.keep(ANA,page(1),7,true,T0);
        assertEquals("an older revision's answer does not push out a later one",1,kept.keep(ANA,page(1),6,false,T0));
        kept.keep(TABLET,page(1),7,true,T0);
        kept.keep(ANA,page(2),7,true,T0);
        assertEquals(3,kept.size());
        List<Outbox.Answers.One> said=new java.util.ArrayList<>();
        assertArrayEquals(new int[]{3,0},kept.sayAll(said::add,T0));
        assertEquals(7,said.get(0).revision);
        assertTrue(said.get(0).took);
    }

    @Test public void anAnswerThatWentAnotherWayIsNoLongerKept() {
        Outbox.Answers kept=new Outbox.Answers();
        kept.keep(ANA,page(1),5,false,T0);
        kept.went(ANA,page(1),4);
        assertEquals("an older answer going says nothing about this one",1,kept.size());
        kept.went(ANA,page(1),5);
        assertEquals(0,kept.size());
    }

    @Test public void keptAnswersThatFindNoWayStayAndTheVeryOldGo() {
        Outbox.Answers kept=new Outbox.Answers();
        kept.keep(ANA,page(1),5,false,T0);
        kept.keep(TABLET,page(1),5,false,T0-Outbox.Answers.FOR-1);
        assertArrayEquals(new int[]{0,1},kept.sayAll(one->{throw new IllegalStateException(Direct.WAITS);},T0));
        for(int n=0;n<Outbox.Answers.MOST+10;n++)kept.keep(ANA+n,page(1),1,false,T0);
        assertEquals(Outbox.Answers.MOST,kept.size());
    }
}
