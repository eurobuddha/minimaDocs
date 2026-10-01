package org.mininotes.android;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** The addresses a note's words hold, found the same on the phone and the PC. */
public class LinksTest {
    private static List<String> targets(String text) {
        List<String> out=new ArrayList<>();
        for(Links.Link one:Links.find(text))out.add(one.target());
        return out;
    }
    private static List<String> found(String text) {
        List<String> out=new ArrayList<>();
        for(Links.Link one:Links.find(text))out.add(text.substring(one.start(),one.end()));
        return out;
    }

    @Test public void webAddressesWithAndWithoutTheirScheme() {
        assertEquals(List.of("https://example.org/a?b=1"),targets("See https://example.org/a?b=1 for it"));
        assertEquals(List.of("http://example.org"),targets("http://example.org"));
        assertEquals(List.of("https://www.example.org/path"),targets("www.example.org/path"));
        assertEquals(List.of("https://example.com/list"),targets("at example.com/list today"));
    }

    @Test public void mailAddressesOpenTheMailApp() {
        assertEquals(List.of("mailto:someone@example.org"),targets("write to someone@example.org."));
        assertEquals(List.of("mailto:someone@example.org"),targets("mailto:someone@example.org"));
        assertEquals("the whole address, not the name after the @",List.of("someone@www.example.org"),found("someone@www.example.org"));
    }

    @Test public void theEndOfTheSentenceIsNotPartOfIt() {
        assertEquals(List.of("https://example.org/page"),found("Read https://example.org/page."));
        assertEquals(List.of("example.com"),found("(see example.com)"));
        assertEquals(List.of("https://en.wikipedia.org/wiki/Paper_(material)"),found("https://en.wikipedia.org/wiki/Paper_(material), then"));
        assertEquals(List.of("www.example.org"),found("\"www.example.org\""));
    }

    @Test public void fileNamesVersionsAndWordsAreLeftAsWords() {
        assertTrue(Links.find("notes.txt Desktop.java e.g. version 0.1.030 and a list").isEmpty());
        assertTrue(Links.find("https:// and www. alone").isEmpty());
        assertTrue(Links.find("").isEmpty());
        assertTrue(Links.find(null).isEmpty());
    }

    @Test public void wherePlacesAreAndWhatIsAtOne() {
        String text="one https://example.org two";
        List<Links.Link> all=Links.find(text);
        assertEquals(4,all.get(0).start());assertEquals(23,all.get(0).end());
        assertNotNull(Links.at(all,4));assertNotNull(Links.at(all,22));
        assertNull("the space after it",Links.at(all,23));assertNull(Links.at(all,0));
        assertEquals(2,Links.find("a.com and b@c.org").size());
    }
}
