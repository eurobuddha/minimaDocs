package org.mininotes.android;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** The drop box as a list: which way each column sorts, what a second click does, and what is kept. See DropList. */
public class DropListTest {
    private static final long DAY=24L*60*60*1000;

    /** Four made-up files, from two made-up devices, on three days. */
    private static List<DropList.Row<String>> four() {
        return Arrays.asList(
            new DropList.Row<>("b","beach.jpg","Work laptop",3*DAY,2_000_000),
            new DropList.Row<>("r","Receipt.pdf","Ana's phone",1*DAY,90_000),
            new DropList.Row<>("n","notes.txt","ana's phone",2*DAY,1_200),
            new DropList.Row<>("a","archive.zip","Work laptop",2*DAY,15_000_000));
    }
    private static List<String> order(List<DropList.Row<String>> rows){List<String> ids=new ArrayList<>();for(DropList.Row<String> r:rows)ids.add(r.thing);return ids;}

    @Test public void newestFirstUntilSomebodySaysOtherwise() {
        assertEquals(DropList.FIRST,DropList.read(null));
        assertEquals(Arrays.asList("b","a","n","r"),order(DropList.sorted(four(),DropList.FIRST)));
        assertEquals("ties on a day stay by name",Arrays.asList("b","a","n","r"),order(DropList.sorted(four(),null)));
    }

    @Test public void eachColumnSortsByWhatItSays() {
        assertEquals(Arrays.asList("a","b","n","r"),order(DropList.sorted(four(),DropList.chosen(DropList.By.NAME))));
        assertEquals("the largest first",Arrays.asList("a","b","r","n"),order(DropList.sorted(four(),DropList.chosen(DropList.By.SIZE))));
        assertEquals(Arrays.asList("b","r","n","a"),order(DropList.sorted(four(),DropList.chosen(DropList.By.TYPE))));
        // Whoever sent them, in any case of letters; the newest first among one sender's.
        assertEquals(Arrays.asList("n","r","b","a"),order(DropList.sorted(four(),DropList.chosen(DropList.By.FROM))));
    }

    @Test public void aSecondClickTurnsItRoundAndAnotherColumnStartsItsOwnWay() {
        DropList.Order now=DropList.FIRST;
        now=DropList.clicked(now,DropList.By.DATE);
        assertEquals(new DropList.Order(DropList.By.DATE,false),now);
        assertEquals("oldest first now",Arrays.asList("r","a","n","b"),order(DropList.sorted(four(),now)));
        now=DropList.clicked(now,DropList.By.NAME);
        assertEquals("the words start at A",new DropList.Order(DropList.By.NAME,false),now);
        now=DropList.clicked(now,DropList.By.NAME);
        assertEquals(Arrays.asList("r","n","b","a"),order(DropList.sorted(four(),now)));
        assertEquals("the largest first",new DropList.Order(DropList.By.SIZE,true),DropList.clicked(now,DropList.By.SIZE));
    }

    @Test public void theArrowIsOnTheColumnSortedByAndNowhereElse() {
        DropList.Order down=DropList.FIRST,up=new DropList.Order(DropList.By.NAME,false);
        assertEquals("▼",DropList.arrow(down,DropList.By.DATE));assertEquals("",DropList.arrow(down,DropList.By.NAME));
        assertEquals("▲",DropList.arrow(up,DropList.By.NAME));
        assertEquals("Date  ▼",DropList.header(down,DropList.By.DATE));assertEquals("Size",DropList.header(down,DropList.By.SIZE));
    }

    @Test public void whatIsKeptReadsBackAndAnythingElseIsTheNewestFirst() {
        for(DropList.By by:DropList.By.values())for(boolean down:new boolean[]{true,false}) {
            DropList.Order one=new DropList.Order(by,down);
            assertEquals(one,DropList.read(DropList.write(one)));
        }
        for(String bent:new String[]{"","DATE","WHEN:down","NAME:sideways",":up"})assertEquals(DropList.FIRST,DropList.read(bent));
        assertTrue(DropList.listed("list"));assertFalse(DropList.listed("cards"));assertFalse(DropList.listed(null));
    }

    @Test public void theColumnsAndThePhonesChoicesAreTheOnesAskedFor() {
        assertEquals(Arrays.asList("Name","From","Date","Size","Type"),said(DropList.COLUMNS));
        assertEquals(Arrays.asList("Date","Name","Size","Type","From"),said(DropList.CHOICES));
    }
    private static List<String> said(List<DropList.By> all){List<String> out=new ArrayList<>();for(DropList.By by:all)out.add(by.said);return out;}

    @Test public void aTypeIsWhatTheNameEndsIn() {
        assertEquals("JPG",DropList.type("beach.jpg"));assertEquals("FILE",DropList.type("README"));
        assertEquals("FILE",DropList.type("odd."));assertEquals("FILE",DropList.type(null));assertEquals("TORRE",DropList.type("x.torrent"));
    }
}
