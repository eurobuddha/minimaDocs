package org.mininotes.android;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class GivenTest {

    @Test public void onlyAnotherAppsContentIsRead() {
        assertTrue(Given.readable("content","com.android.providers.media.documents","org.mininotes.android.files"));
        assertTrue(Given.readable("CONTENT","com.example.gallery","org.mininotes.android.files"));
        // A file address could name the pad's own private files, the lock among them.
        assertFalse(Given.readable("file",null,"org.mininotes.android.files"));
        assertFalse(Given.readable("content","org.mininotes.android.files","org.mininotes.android.files"));
        assertFalse(Given.readable(null,"com.example.gallery","org.mininotes.android.files"));
        assertFalse(Given.readable("content","","org.mininotes.android.files"));
    }

    @Test public void aNewNoteIsCalledWhatTheSenderCalledIt() {
        assertEquals("A recipe page",Given.title("A recipe page","https://example.org/soup",null));
        assertEquals("Buy milk",Given.title(null,"\n  Buy milk  \nand bread","photo.jpg"));
        assertEquals("Buy milk",Given.title("   ","Buy milk",null));
        assertEquals("Screenshot_20260927",Given.title(null,"","Screenshot_20260927.png"));
        assertEquals("scan",Given.title("",null,"/storage/emulated/0/scan.pdf"));
        assertEquals("profile",Given.title(null,null,".profile"));
        assertEquals(Given.UNTITLED,Given.title(null,null,null));
        assertEquals(Given.TITLE_MOST,Given.title(null,"x".repeat(500),null).length());
    }

    @Test public void addedWordsCarryTheSubjectUnlessTheTextSaysItAlready() {
        assertEquals("A recipe page\nhttps://example.org/soup",Given.words("A recipe page","https://example.org/soup"));
        assertEquals("A recipe page: https://example.org/soup",Given.words("A recipe page","A recipe page: https://example.org/soup"));
        assertEquals("just text",Given.words(null,"  just text "));
        assertEquals("Only a subject",Given.words("Only a subject",null));
        assertEquals("",Given.words(null,null));
    }

    @Test public void appendingGoesAfterABlankLineAndMovesNothing() {
        assertEquals("milk\n\neggs",Given.appended("milk","eggs"));
        assertEquals("milk\n\neggs",Given.appended("milk\n","eggs"));
        assertEquals("milk\n\neggs",Given.appended("milk\n\n","eggs"));
        assertEquals("eggs",Given.appended("","eggs"));
        assertEquals("eggs",Given.appended("  \n","eggs"));
        assertEquals("milk",Given.appended("milk",""));
        assertEquals("milk",Given.appended("milk",null));
        assertTrue(Given.appended("a\nb","c").startsWith("a\nb"));
    }

    @Test public void theOpenNoteComesFirstThenTheMostRecentNeverOneThatOnlyReads() {
        List<String> lately=Arrays.asList("a","b","c","d","e","f");
        assertEquals(Arrays.asList("c","a","b","d"),Given.recent(lately,"c",Collections.emptySet(),4));
        assertEquals(Arrays.asList("a","c","e","f"),Given.recent(lately,"b",Arrays.asList("b","d"),4));
        assertEquals(Arrays.asList("a","b","c","d"),Given.recent(lately,null,Collections.emptySet(),4));
        // A note open but not among the recent ones is not offered: it may be one gone from the shelves.
        assertEquals(Arrays.asList("a","b"),Given.recent(lately,"z",Collections.emptySet(),2));
        assertEquals(Collections.emptyList(),Given.recent(Collections.emptyList(),"a",Collections.emptySet(),4));
    }

    @Test public void whatCameIsSaidInAFewWords() {
        assertEquals("3 pictures",Given.what(Arrays.asList("image/png","image/jpeg","IMAGE/webp")));
        assertEquals("A picture",Given.what(Collections.singletonList("image/png")));
        assertEquals("2 videos",Given.what(Arrays.asList("video/mp4","video/webm")));
        assertEquals("A PDF",Given.what(Collections.singletonList("application/pdf")));
        assertEquals("2 files",Given.what(Arrays.asList("image/png","application/pdf")));
        assertEquals("A file",Given.what(Collections.singletonList(null)));
        assertEquals("",Given.what(Collections.emptyList()));
    }

    @Test public void oneLineWhileAddingAndOneWhenDone() {
        assertEquals("Adding the file…",Given.adding(1));
        assertEquals("Adding 3 files…",Given.adding(3));
        assertEquals("photo.jpg added",Given.added(1,"photo.jpg",0));
        assertEquals("3 files added",Given.added(3,"c.pdf",0));
        assertEquals("2 files added, 1 not",Given.added(2,"b.pdf",1));
        assertEquals("Nothing was added",Given.added(0,null,2));
    }

    @Test public void theRefusedAreNamedWithTheirReason() {
        String one=Given.refusal("/sdcard/big.mov",Given.TOO_BIG);
        assertEquals("big.mov – larger than 25 MB",one);
        assertTrue(Given.refusals(Collections.singletonList(one)).startsWith("This was not added:"));
        String both=Given.refusals(Arrays.asList(one,Given.refusal("x.bin",Given.UNREADABLE)));
        assertTrue(both.startsWith("These were not added:"));
        assertTrue(both.contains("x.bin – it could not be read"));
        assertTrue(both.endsWith("Nothing else was changed."));
    }
}
