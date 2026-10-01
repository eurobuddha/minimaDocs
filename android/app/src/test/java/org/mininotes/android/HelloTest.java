package org.mininotes.android;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;

/**
 * A hello that says whose the device is, read by builds either side of it. Everything here is made up: keys made for
 * the test, an address nobody has, names nobody is.
 */
public class HelloTest {
    private static final String ADDRESS="MxTEST@203.0.113.7:9001";
    private static final String PERSON="00112233445566778899aabbccddeeff";

    private static Hello.Said plain(String scope,String target) throws Exception {
        return new Hello.Said("Ana",ADDRESS,Point.shorten(Envelope.keys().getPublic()),Point.shorten(Envelope.keys().getPublic()),scope,target,true);
    }

    private static Hello.Said owned() throws Exception {
        return plain("LIBRARY","everything").owner(new Persons.Me(PERSON,1_000L,null,"Ana",2_000L,"Kitchen tablet",3_000L));
    }

    /** What a build from before this reads: the fields up to the files flag, and nothing after it. */
    private static String[] readAsBefore(byte[] said) throws Exception {
        for(int at=0;at<Hello.MAGIC.length;at++)if(said[at]!=Hello.MAGIC[at])return null;
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(said,4,said.length-4));
        boolean writes=in.readBoolean();
        String name=field(in),address=field(in);field(in);field(in);
        String scope=field(in),target=field(in);
        boolean files=in.available()>0&&in.readBoolean();
        return new String[]{name,address,scope,target,String.valueOf(writes),String.valueOf(files)};
    }
    private static String field(DataInputStream in) throws Exception {
        int length=in.readInt();byte[] bytes=new byte[length];in.readFully(bytes);return new String(bytes,StandardCharsets.UTF_8);
    }

    @Test public void aBuildFromBeforeReadsANewHelloAsItAlwaysDid() throws Exception {
        String[] read=readAsBefore(Hello.wrap(owned()));
        assertArrayEquals(new String[]{"Ana",ADDRESS,"LIBRARY","everything","true","true"},read);
    }

    @Test public void thisBuildReadsAHelloFromBeforeAsSayingNothingAboutPersons() throws Exception {
        // Written as a build from before wrote it: up to the target, and then only the files flag - or not even that.
        Hello.Said said=plain("LIBRARY","everything");
        byte[] whole=Hello.wrap(said);
        Hello.Said read=Hello.open(whole);
        assertNotNull(read);assertTrue(read.files);assertFalse(read.persons);
        assertEquals("",read.person);assertEquals("",read.deviceName);
        Hello.Said older=Hello.open(Arrays.copyOf(whole,whole.length-1));
        assertNotNull(older);assertFalse(older.files);assertFalse(older.persons);
    }

    @Test public void aNewHelloSaysWhoseTheDeviceIs() throws Exception {
        Hello.Said read=Hello.open(Hello.wrap(owned()));
        assertNotNull(read);
        assertTrue(read.persons);assertEquals(PERSON,read.person);assertEquals(1_000L,read.personMade);
        assertEquals("Ana",read.yourName);assertEquals(2_000L,read.named);
        assertEquals("Kitchen tablet",read.deviceName);assertEquals(3_000L,read.deviceNamed);
        assertEquals("Ana",read.name);assertEquals("LIBRARY",read.scope);assertTrue(read.files);
    }

    @Test public void aDamagedTailLeavesTheHelloBeforeIt() throws Exception {
        byte[] whole=Hello.wrap(owned());
        // Cut short anywhere in what says whose it is: the hello is read, and says nothing about persons.
        int tail=Hello.wrap(plain("LIBRARY","everything")).length;
        for(int cut=tail+1;cut<whole.length;cut++) {
            Hello.Said read=Hello.open(Arrays.copyOf(whole,cut));
            assertNotNull("cut at "+cut,read);assertFalse("cut at "+cut,read.persons);assertEquals("Ana",read.name);
        }
        // A name that says it is longer than any name may be.
        byte[] lying=whole.clone();
        int nameAt=tail+1+Persons.ID+8;
        lying[nameAt]=(byte)0x7f;
        Hello.Said read=Hello.open(lying);
        assertNotNull(read);assertFalse(read.persons);assertEquals(ADDRESS,read.address);
    }

    @Test public void onlyAHelloWithAnIdSaysItKnowsAboutPersons() throws Exception {
        Hello.Said said=plain("LIBRARY","everything").owner(new Persons.Me("not an id",1,null,"Ana",0,"Tablet",0));
        assertFalse(said.persons);
        Hello.Said read=Hello.open(Hello.wrap(said));
        assertNotNull(read);assertFalse(read.persons);
    }

    @Test public void aNameTooLongIsCutRatherThanRefused() throws Exception {
        String long_="ü".repeat(60);
        Hello.Said said=plain("LIBRARY","x").owner(new Persons.Me(PERSON,1,null,"Ana",0,long_,5));
        Hello.Said read=Hello.open(Hello.wrap(said));
        assertNotNull(read);assertTrue(read.persons);
        assertTrue(read.deviceName.getBytes(StandardCharsets.UTF_8).length<=Hello.NAME_MOST);
        assertTrue(long_.startsWith(read.deviceName));
    }

    /** Nothing else is mistaken for a hello, and a hello is not mistaken for anything else. */
    @Test public void aHelloIsNotACardAndACardIsNotAHello() throws Exception {
        byte[] hello=Hello.wrap(owned());
        assertNull(Persons.open(hello));assertFalse(Persons.isCard(hello));
        assertNull(Drop.open(hello));assertEquals(0,Receipt.open(hello));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)){out.write(Persons.MAGIC);out.writeByte(1);}
        assertNull(Hello.open(bytes.toByteArray()));
    }

    @Test public void anAdminAcceptanceIsAdminHereAndCanWriteToABuildFromBefore() throws Exception {
        Hello.Said admin=plain("BOOK","book-1").claiming(Sharing.Level.ADMIN);
        byte[] said=Hello.wrap(admin);
        assertEquals(Sharing.Level.ADMIN,Hello.open(said).level);
        assertTrue(Hello.open(said).writes);
        // A build from before reads the byte as "writes": Can write, never more.
        assertEquals("true",readAsBefore(said)[4]);
        // And the bytes a build from before writes read as they always meant.
        assertEquals(Sharing.Level.WRITE,Hello.open(Hello.wrap(plain("BOOK","book-1"))).level);
        assertEquals(1,Hello.said(Sharing.Level.WRITE));assertEquals(0,Hello.said(Sharing.Level.READ));
        assertEquals(Sharing.Level.READ,Hello.level(0));
        assertEquals(Sharing.Level.READ,Hello.open(Hello.wrap(plain("BOOK","book-1").claiming(Sharing.Level.READ))).level);
        assertEquals("the most this build knows",Sharing.Level.ADMIN,Hello.level(200));
    }
}
