package org.mininotes.android;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

public class VaultTest {
    /** Fewer rounds than the real lock, so the tests are quick; the file carries the number either way. */
    private static final int QUICK=20_000;

    @Test public void thePasswordAndTheWordsBothOpenTheSameKey() throws Exception {
        Vault.Made made=Vault.make("correct horse".toCharArray(),QUICK);
        assertEquals(Vault.WORDS,made.words.size());assertEquals(Vault.KEY,made.key.length);
        assertArrayEquals(made.key,Vault.open(made.kept,"correct horse".toCharArray()));
        assertArrayEquals(made.key,Vault.recover(made.kept,String.join(" ",made.words)));
        // However the words are typed: capitals, extra spaces.
        assertArrayEquals(made.key,Vault.recover(made.kept,"  "+String.join("   ",made.words).toUpperCase()+" "));
    }

    @Test public void aWrongPasswordOrWrongWordsOpenNothing() throws Exception {
        Vault.Made made=Vault.make("correct horse".toCharArray(),QUICK);
        assertThrows(Vault.Refused.class,()->Vault.open(made.kept,"Correct horse".toCharArray()));
        assertThrows(Vault.Refused.class,()->Vault.open(made.kept,new char[0]));
        assertThrows(IllegalArgumentException.class,()->Vault.make(new char[0],QUICK));
        Vault.Made other=Vault.make("x".toCharArray(),QUICK);
        assertThrows(Vault.Refused.class,()->Vault.recover(made.kept,String.join(" ",other.words)));
        assertThrows(Vault.Refused.class,()->Vault.recover(made.kept,"only three words"));
        String[] swapped=made.words.toArray(new String[0]);String first=swapped[0];swapped[0]=swapped[1];swapped[1]=first;
        if(!Arrays.asList(swapped).equals(made.words))assertThrows(Vault.Refused.class,()->Vault.recover(made.kept,String.join(" ",swapped)));
    }

    @Test public void aNewPasswordKeepsTheKeyAndTheWords() throws Exception {
        Vault.Made made=Vault.make("old".toCharArray(),QUICK);
        byte[] kept=Vault.newPassword(made.kept,made.key,"new".toCharArray());
        assertArrayEquals(made.key,Vault.open(kept,"new".toCharArray()));
        assertThrows(Vault.Refused.class,()->Vault.open(kept,"old".toCharArray()));
        assertArrayEquals(made.key,Vault.recover(kept,String.join(" ",made.words)));
    }

    @Test public void aDamagedOrForeignFileIsRefused() throws Exception {
        Vault.Made made=Vault.make("pw".toCharArray(),QUICK);
        // A byte inside the password's copy of the key: that copy no longer opens, and nothing else is tried.
        byte[] bent=made.kept.clone();bent[Vault.MAGIC.length+4+2+Vault.SALT+Vault.NONCE+3]^=1;
        assertThrows(Vault.Refused.class,()->Vault.open(bent,"pw".toCharArray()));
        assertThrows(Vault.Refused.class,()->Vault.open("not a lock".getBytes(),"pw".toCharArray()));
        assertThrows(Vault.Refused.class,()->Vault.open(null,"pw".toCharArray()));
        assertThrows(Vault.Refused.class,()->Vault.open(Arrays.copyOf(made.kept,10),"pw".toCharArray()));
    }

    @Test public void theWordsCanBeShownAgainOnlyWithTheKey() throws Exception {
        Vault.Made made=Vault.make("pw-for-words".toCharArray(),QUICK);
        assertEquals(made.words,Vault.words(made.kept,made.key));
        byte[] renewed=Vault.newPassword(made.kept,made.key,"another-pw".toCharArray());
        assertEquals(made.words,Vault.words(renewed,Vault.open(renewed,"another-pw".toCharArray())));
        byte[] wrong=made.key.clone();wrong[0]^=1;
        assertThrows(Vault.Refused.class,()->Vault.words(made.kept,wrong));
    }

    /** Made with no password: the words open it, no password does, and the words can still be shown again. */
    @Test public void aLockWithoutAPasswordOpensWithTheWords() throws Exception {
        Vault.Made made=Vault.makeWithoutPassword(QUICK);
        assertFalse(Vault.hasPassword(made.kept));
        assertArrayEquals(made.key,Vault.recover(made.kept,String.join(" ",made.words)));
        assertEquals(made.words,Vault.words(made.kept,made.key));
        Vault.Refused refused=assertThrows(Vault.Refused.class,()->Vault.open(made.kept,"anything at all".toCharArray()));
        assertEquals("That did not open it.",refused.getMessage());
    }

    /** A password added later, and taken out again: the key and the words never change, so neither does the notebook. */
    @Test public void aPasswordCanBeAddedAndRemovedWithoutTouchingTheKeyOrTheWords() throws Exception {
        Vault.Made made=Vault.makeWithoutPassword(QUICK);
        byte[] added=Vault.newPassword(made.kept,made.key,"added later".toCharArray());
        assertTrue(Vault.hasPassword(added));
        assertArrayEquals(made.key,Vault.open(added,"added later".toCharArray()));
        assertArrayEquals(made.key,Vault.recover(added,String.join(" ",made.words)));
        byte[] removed=Vault.withoutPassword(added);
        assertFalse(Vault.hasPassword(removed));
        assertThrows(Vault.Refused.class,()->Vault.open(removed,"added later".toCharArray()));
        assertArrayEquals(made.key,Vault.recover(removed,String.join(" ",made.words)));
        assertEquals(made.words,Vault.words(removed,made.key));
        assertThrows(IllegalArgumentException.class,()->Vault.newPassword(removed,made.key,new char[0]));
        assertThrows(Vault.Refused.class,()->Vault.withoutPassword("not a lock".getBytes()));
    }

    /**
     * A lock file written by the version before the password became a choice, kept here byte for byte: it
     * still has its password, and its password and its words still open the same key.
     */
    @Test public void aNotebookLockedTheOldWayStillOpens() throws Exception {
        byte[] kept=hex("4d4e563100002710004cb26dff42415e95254e76fa19e4b9585a7687a116fe17a49b643db98ab1ab4e2806cf9d74801232870649a9c83240683500372d788a9a954a1cdaba076e283dd299e420af4c7c2df161801ebf004cc373371ad8a2c169ec114c3de06e556568a862285ee8f7915d8d9717f5dfe12e90af52f18bd2cf694e1b52a10d274a97b72075c920f5b2d097ca73618b5f506cc30f0480b8f7e998999856db00638abd5900918a011f6b09dd916cbaaf3124bdb01eafcd2c63ea50c2573558c5bdeb0ceb3c8a05680ae60d24b5414baf1dda49496273f983daf05df43d6727a3f9cb1df2078042f71ee5544491c7db7eaf05c566b90a961376ab2608882a85c6bb983f2a");
        byte[] key=hex("12e04b6b7fa9bc4279c96146337428c57829977ad83c53dbc7b64bc2a49be541");
        String words="loyal chase mystery fix winner wasp unfold object erode park winner dad";
        assertTrue(Vault.hasPassword(kept));
        assertArrayEquals(key,Vault.open(kept,"old way password".toCharArray()));
        assertArrayEquals(key,Vault.recover(kept,words));
        assertEquals(Arrays.asList(words.split(" ")),Vault.words(kept,key));
        // And taking its password out later leaves the words opening the same key.
        assertArrayEquals(key,Vault.recover(Vault.withoutPassword(kept),words));
    }

    private static byte[] hex(String s) {
        byte[] out=new byte[s.length()/2];
        for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(s.substring(2*i,2*i+2),16);
        return out;
    }

    /** The written-out PBKDF2 gives what Java's own does, byte for byte: a lock made on the phone opens on the PC. */
    @Test public void theDerivationIsStandardPbkdf2() throws Exception {
        byte[] salt="0123456789abcdef".getBytes();
        for(String password:new String[]{"password","correct horse battery","çay ☕ 日本語 é","a much longer password than the sixty-four bytes of one SHA-256 block, to be hashed first"})
            for(int rounds:new int[]{1,2,3,1000}) {
                javax.crypto.spec.PBEKeySpec spec=new javax.crypto.spec.PBEKeySpec(password.toCharArray(),salt,rounds,256);
                byte[] standard=javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
                assertArrayEquals(password+" x"+rounds,standard,Vault.pbkdf2(password.toCharArray(),salt,rounds));
            }
    }

    @Test public void theKeyIsHandedToSqlCipherRaw() {
        assertEquals("x'00ff10'",Vault.pragma(new byte[]{0,(byte)0xff,0x10}));
    }
}
