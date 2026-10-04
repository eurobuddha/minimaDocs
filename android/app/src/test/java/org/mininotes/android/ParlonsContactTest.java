package org.mininotes.android;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ParlonsContactTest {
    private static final String A="0x"+String.join("",Collections.nCopies(32,"ab")),B="0x"+String.join("",Collections.nCopies(32,"cd"));
    @Test public void readsTheActualParlonsFormatAndDeduplicatesKeys(){
        List<ParlonsContact> contacts=ParlonsContact.read(A+"\u001fZoe\u001fMxabc@host:9001\u001e"+B+"\u001fAlice\u001f\u001e"+A+"\u001fZoe\u001fother");
        assertEquals(2,contacts.size());assertEquals("Alice",contacts.get(0).name);assertEquals(A,contacts.get(1).key);
        assertTrue(ParlonsContact.read("").isEmpty());
    }
    @Test public void damagedListsAreRejectedInsteadOfMisaddressingAnInvitation(){
        for(String bad:Arrays.asList("evil\u001fName\u001faddress",A+"\u001fname\u001faddress\u001finjected",A+"\u001fname"))
            assertThrows(IllegalArgumentException.class,()->ParlonsContact.read(bad));
        assertFalse(ParlonsContact.key("0x../path"));assertFalse(ParlonsContact.key(A+"a"));
    }
    @Test public void transportHexAndPlainInvitationsHaveTheSamePairingLine(){
        String line=Pairing.write("Friend","Mx12345678@host:9001",new byte[]{1},new byte[]{2});
        StringBuilder hex=new StringBuilder("0x");for(byte b:line.getBytes(java.nio.charset.StandardCharsets.UTF_8))hex.append(String.format("%02x",b&255));
        assertEquals(line,ParlonsContact.invitation(line));assertEquals(line,ParlonsContact.invitation(hex.toString()));
        assertThrows(IllegalArgumentException.class,()->ParlonsContact.invitation("0xzz"));
        assertThrows(IllegalArgumentException.class,()->ParlonsContact.invitation("0x1"));
        assertThrows(IllegalArgumentException.class,()->ParlonsContact.invitation(Pairing.write("Friend","",new byte[]{1},new byte[]{2})));
    }
}
