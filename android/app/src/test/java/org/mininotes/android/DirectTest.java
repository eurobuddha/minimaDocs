package org.mininotes.android;

import static org.junit.Assert.*;

import com.eurobuddha.maxima.core.codec.MiniData;
import com.eurobuddha.maxima.core.crypto.Hashes;
import com.eurobuddha.maxima.core.identity.MaximaIdentity;
import com.eurobuddha.maxima.core.identity.MxAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/** Announcements on the local network, and the order of trying a device's doors: see Direct. */
public class DirectTest {
    // Synthetic identities from made-up seeds, and addresses from the documentation ranges only.
    private static MaximaIdentity a, b, c;
    private static final byte[] HOME={(byte)192,0,2,10}, ELSEWHERE={(byte)192,0,2,11};
    private static final long NOW=1_800_000_000_000L;

    @BeforeClass public static void keys() {
        Hashes.setSha3(Sha3::of);
        a=MaximaIdentity.fromSeed(new MiniData(seed(1)));
        b=MaximaIdentity.fromSeed(new MiniData(seed(2)));
        c=MaximaIdentity.fromSeed(new MiniData(seed(3)));
    }
    private static byte[] seed(int fill){byte[] s=new byte[32];Arrays.fill(s,(byte)fill);return s;}
    private static Set<String> pairedWithA(){return Set.of(Direct.key(a.publicKeyHex()));}
    private static Direct.Heard hear(byte[] packet,byte[] from,long now){return Direct.heard(packet,packet.length,from,b.publicKeyHex(),pairedWithA(),now);}

    @Test public void aPairedDeviceIsHeardWhereItSaysItIs() throws Exception {
        byte[] said=Direct.announce(a.keyPair(),HOME,9601,NOW);
        assertTrue(said.length<Direct.MOST);
        Direct.Heard heard=hear(said,HOME,NOW);
        assertNotNull(heard);
        assertEquals(Direct.key(a.publicKeyHex()),heard.key());
        assertEquals("192.0.2.10:9601",heard.where());
    }

    @Test public void itsOwnAnnouncementIsNotNews() throws Exception {
        byte[] said=Direct.announce(b.keyPair(),HOME,9601,NOW);
        assertNull(Direct.heard(said,said.length,HOME,b.publicKeyHex(),Set.of(Direct.key(b.publicKeyHex())),NOW));
    }

    @Test public void aDeviceThatIsNotPairedIsNotHeard() throws Exception {
        byte[] said=Direct.announce(c.keyPair(),HOME,9601,NOW);
        assertNull(hear(said,HOME,NOW));
        assertNull(Direct.heard(said,said.length,HOME,b.publicKeyHex(),Set.of(),NOW));
        assertNull(Direct.heard(said,said.length,HOME,b.publicKeyHex(),null,NOW));
    }

    @Test public void onlyFreshAnnouncementsAreHeard() throws Exception {
        byte[] said=Direct.announce(a.keyPair(),HOME,9601,NOW);
        assertNotNull(hear(said,HOME,NOW+Direct.FRESH));
        assertNotNull(hear(said,HOME,NOW-Direct.FRESH));
        assertNull("played again later",hear(said,HOME,NOW+Direct.FRESH+1));
        assertNull("from a clock too far ahead",hear(said,HOME,NOW-Direct.FRESH-1));
    }

    @Test public void playedAgainFromAnotherAddressIsNotHeard() throws Exception {
        byte[] said=Direct.announce(a.keyPair(),HOME,9601,NOW);
        assertNull(hear(said,ELSEWHERE,NOW));
        assertNull(hear(said,new byte[16],NOW));
    }

    @Test public void anythingChangedOrSignedByAnotherKeyIsNotHeard() throws Exception {
        byte[] said=Direct.announce(a.keyPair(),HOME,9601,NOW);
        int keyLength=a.publicKey().length;
        int port=4+1+2+keyLength+1+4;
        byte[] moved=said.clone();moved[port+1]^=1;
        assertNull("the port changed",hear(moved,HOME,NOW));
        byte[] signature=said.clone();signature[signature.length-1]^=1;
        assertNull("the signature changed",hear(signature,HOME,NOW));
        // Signed by somebody else, wearing a paired device's key.
        byte[] other=Direct.announce(c.keyPair(),HOME,9601,NOW);
        System.arraycopy(a.publicKey(),0,other,4+1+2,keyLength);
        assertNull(hear(other,HOME,NOW));
    }

    @Test public void onlyAnAnnouncementOfTheRightShapeIsRead() throws Exception {
        byte[] said=Direct.announce(a.keyPair(),HOME,9601,NOW);
        assertNull(Direct.heard(null,0,HOME,b.publicKeyHex(),pairedWithA(),NOW));
        assertNull(Direct.heard(said,said.length,null,b.publicKeyHex(),pairedWithA(),NOW));
        assertNull("cut short",hear(Arrays.copyOf(said,said.length-1),HOME,NOW));
        assertNull("one byte too many",hear(Arrays.copyOf(said,said.length+1),HOME,NOW));
        assertNull("longer than the packet",Direct.heard(said,said.length+1,HOME,b.publicKeyHex(),pairedWithA(),NOW));
        byte[] huge=Arrays.copyOf(said,Direct.MOST+1);
        assertNull("longer than any announcement",Direct.heard(huge,huge.length,HOME,b.publicKeyHex(),pairedWithA(),NOW));
        byte[] magic=said.clone();magic[0]='X';assertNull(hear(magic,HOME,NOW));
        byte[] version=said.clone();version[4]=2;assertNull(hear(version,HOME,NOW));
        byte[] noKey=said.clone();noKey[5]=0;noKey[6]=0;assertNull(hear(noKey,HOME,NOW));
        byte[] bigKey=said.clone();bigKey[5]=(byte)0xff;bigKey[6]=(byte)0xff;assertNull(hear(bigKey,HOME,NOW));
        assertThrows(IllegalArgumentException.class,()->Direct.announce(a.keyPair(),new byte[5],9601,NOW));
        assertThrows(IllegalArgumentException.class,()->Direct.announce(a.keyPair(),HOME,0,NOW));
    }

    @Test public void onlyPairedDevicesWithAKnownKeyAreBelieved() {
        NoteStore.Contact paired=new NoteStore.Contact("MxP@192.0.2.1:9001","P",true,a.publicKeyHex().toLowerCase(),new byte[]{1},new byte[]{1});
        NoteStore.Contact notYet=new NoteStore.Contact("MxQ@192.0.2.1:9001","Q",false,c.publicKeyHex(),new byte[0],new byte[0]);
        NoteStore.Contact unknown=new NoteStore.Contact("MxR@192.0.2.1:9001","R",false,"",new byte[]{1},new byte[]{1});
        assertEquals(pairedWithA(),Direct.paired(List.of(paired,notYet,unknown)));
        assertTrue(Direct.paired(null).isEmpty());
    }

    /**
     * Seen on 0.1.034: a PC linked with a phone through a list, later marked as the owner's, whose row had its keys and
     * no identity key. Its announcements went unbelieved as not paired until the key was written down from what it sent.
     */
    @Test public void aDeviceLinkedThroughAListAndMarkedMineIsHeardOnceItsKeyIsKnown() throws Exception {
        NoteStore.Contact linked=new NoteStore.Contact("MxRelayFixture@192.0.2.99:9001","PC",true,"",new byte[]{1},new byte[]{1},"",0,Linking.VIA);
        assertTrue(linked.listed());assertTrue(linked.paired());
        byte[] said=Direct.announce(a.keyPair(),HOME,9601,NOW);
        List<Direct.Refused> why=new java.util.ArrayList<>();
        assertNull(Direct.heard(said,said.length,HOME,b.publicKeyHex(),Direct.paired(List.of(linked)),NOW,(r,who)->why.add(r)));
        assertEquals(List.of(Direct.Refused.NOT_PAIRED),why);
        // The same row, with the key the network knows it by: as the transport writes it, or as a message's sender says it.
        for(String key:new String[]{a.publicKeyHex(),a.publicKeyHex().toLowerCase().replace("0x","0X")}) {
            NoteStore.Contact known=new NoteStore.Contact(linked.address,"PC",true,key,new byte[]{1},new byte[]{1},"",0,Linking.VIA);
            Direct.Heard heard=Direct.heard(said,said.length,HOME,b.publicKeyHex(),Direct.paired(List.of(known)),NOW);
            assertNotNull(heard);assertEquals("192.0.2.10:9601",heard.where());
        }
    }

    @Test public void whyAnAnnouncementIsNotBelievedIsCounted() throws Exception {
        List<Direct.Refused> why=new java.util.ArrayList<>();
        byte[] said=Direct.announce(a.keyPair(),HOME,9601,NOW);
        Direct.heard(said,said.length,HOME,b.publicKeyHex(),pairedWithA(),NOW+Direct.FRESH+1,(r,who)->why.add(r));
        Direct.heard(said,said.length,ELSEWHERE,b.publicKeyHex(),pairedWithA(),NOW,(r,who)->why.add(r));
        byte[] forged=said.clone();forged[forged.length-1]^=1;
        Direct.heard(forged,forged.length,HOME,b.publicKeyHex(),pairedWithA(),NOW,(r,who)->why.add(r));
        assertEquals(List.of(Direct.Refused.STALE,Direct.Refused.ADDRESS_MISMATCH,Direct.Refused.BAD_SIGNATURE),why);
        // Its own, and somebody else's noise, are not refusals worth a count.
        why.clear();
        byte[] own=Direct.announce(b.keyPair(),HOME,9601,NOW);
        Direct.heard(own,own.length,HOME,b.publicKeyHex(),pairedWithA(),NOW,(r,who)->why.add(r));
        Direct.heard(new byte[]{1,2,3,4,5,6},6,HOME,b.publicKeyHex(),pairedWithA(),NOW,(r,who)->why.add(r));
        assertTrue(why.isEmpty());
    }

    @Test public void aKeysTagIsTheSameWhateverFormItCameIn() {
        String tag=Direct.tag(a.publicKeyHex());
        assertTrue(tag.matches("[0-9a-f]{6}"));
        assertEquals(tag,Direct.tag(a.publicKeyHex().toLowerCase()));
        assertEquals(tag,Direct.tag(Direct.key(a.keyPair().getPublic().getEncoded())));
        assertNotEquals(tag,Direct.tag(b.publicKeyHex()));
    }

    @Test public void theLogSaysHowManyFilesAreHereApartFromHowManyWereSaid() {
        assertEquals("12 file(s) the device whose list came named are here: told it of 0, the rest at its next list",Post.filesHere(12,0));
        assertEquals("2 file(s) the device whose list came named are here: told it of all",Post.filesHere(2,2));
    }

    @Test public void whoSentSomethingIsKeptByWhatCame() {
        byte[] one="a synthetic sealed message".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("",Node.sentBy(one));
        Node.sent(one,a.publicKeyHex());
        assertEquals(a.publicKeyHex(),Node.sentBy(one.clone()));
        assertEquals("",Node.sentBy("something else".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ---- direct first ----------------------------------------------------------------------------------

    private static String door(MaximaIdentity who,String where){return MxAddress.make(who.publicKeyData())+"@"+where;}

    @Test public void aDeviceDoorIsTellableFromARelay() {
        String own=door(b,"192.0.2.20:9601");
        String relay=b.contactAddress("192.0.2.30:9001");
        assertTrue(Direct.isDirect(own,b.publicKeyHex()));
        assertTrue(Direct.isDirect(own,b.publicKeyHex().toLowerCase()));
        assertFalse(Direct.isDirect(relay,b.publicKeyHex()));
        assertFalse("somebody else's door",Direct.isDirect(door(c,"192.0.2.20:9601"),b.publicKeyHex()));
        assertFalse("no host",Direct.isDirect(MxAddress.make(b.publicKeyData())+"@",b.publicKeyHex()));
        assertFalse(Direct.isDirect(null,b.publicKeyHex()));
        assertFalse(Direct.isDirect(own,""));
        assertFalse(Direct.isDirect(own,"not a key"));
    }

    @Test public void theLocalDoorComesFirstThenTheirOwnAndNeverARelay() {
        String lan=door(b,"192.0.2.40:9601"), own=door(b,"192.0.2.20:9601");
        String relay1=b.contactAddress("192.0.2.30:9001"), relay2=b.contactAddress("192.0.2.31:9001");
        assertEquals(List.of(lan,own),Direct.firstTries(lan,List.of(relay1,own,relay2),b.publicKeyHex()));
        assertEquals(List.of(own),Direct.firstTries(null,List.of(relay1,own),b.publicKeyHex()));
        assertEquals(List.of(own),Direct.firstTries(own,List.of(own),b.publicKeyHex()));
        assertTrue("only relays: nothing to try first",Direct.firstTries("",List.of(relay1,relay2),b.publicKeyHex()).isEmpty());
    }

    @Test public void aFileIsAskedOfTheDoorFirstButMustBeOnARelay() {
        assertEquals(List.of("d","r1","r2"),Direct.sourcesFirst(List.of("d"),List.of("r1","d","r2")));
        assertTrue(Direct.relayed(List.of("d","r1"),List.of("d")));
        assertFalse("only this device's own door",Direct.relayed(List.of("d"),List.of("d")));
        assertFalse(Direct.relayed(List.of(),List.of()));
        assertTrue(Direct.relayed(List.of("r1"),null));
    }

    @Test public void aDoorFoundShutIsNotBelievedForAWhile() {
        String key=Direct.key(c.publicKeyHex());
        Direct.shut(key,door(c,"192.0.2.50:9601"),NOW);
        assertTrue(Direct.shutLately(key,"192.0.2.50:9601",NOW+1));
        assertFalse("somewhere else",Direct.shutLately(key,"192.0.2.51:9601",NOW+1));
        assertFalse("after a while",Direct.shutLately(key,"192.0.2.50:9601",NOW+Direct.SHUT_FOR));
    }

    @Test public void aDoorShutOnceIsBelievedAgainSoonAndOneShutAgainIsLeftLonger() {
        String key=Direct.key(c.publicKeyHex());String at="192.0.2.60:9601";
        // Once: a phone that dozed through one knock. Believed again after a minute, not ten.
        Direct.shut(key,door(c,at),NOW);
        assertTrue(Direct.shutLately(key,at,NOW+Direct.SHUT_FIRST-1));
        assertFalse(Direct.shutLately(key,at,NOW+Direct.SHUT_FIRST));
        // Again soon after: a door that drops everything, as a PC's firewall does. Ten minutes.
        Direct.shut(key,door(c,at),NOW+Direct.SHUT_FIRST);
        assertTrue(Direct.shutLately(key,at,NOW+2*Direct.SHUT_FIRST));
        assertFalse(Direct.shutLately(key,at,NOW+Direct.SHUT_FIRST+Direct.SHUT_FOR));
        // A door that took something is open, whatever it did before; the next miss counts as the first.
        Direct.opened(key,door(c,at));
        assertFalse(Direct.shutLately(key,at,NOW+2*Direct.SHUT_FIRST));
        Direct.shut(key,door(c,at),NOW+3*Direct.SHUT_FIRST);
        assertFalse(Direct.shutLately(key,at,NOW+4*Direct.SHUT_FIRST));
    }

    @Test public void theProfileSaysItInOnePlainLine() {
        assertEquals("Reachable directly, even from away from home",
            Direct.reachability(true,"ADVERTISED","reachable at 192.0.2.1:9601 via upnp"));
        assertEquals("Not reachable directly: the router did not open a port",
            Direct.reachability(true,"OFF","this network has no forwardable public port"));
        assertEquals("Not reachable directly: the router's port let nothing in",
            Direct.reachability(true,"OFF","mapped a port but it was not reachable from outside"));
        assertEquals("Not reachable directly: this PC could not open a port",Direct.reachability(false,null,"not started"));
        assertTrue(Direct.reachability(true,"PROBING","verifying…").startsWith("Checking"));
        assertTrue(Direct.reachability(true,null,"not started").startsWith("Not yet known"));
        // Never the address itself.
        for(String state:new String[]{"OFF","MAPPING","PROBING","ADVERTISED"})
            assertFalse(Direct.reachability(true,state,"reachable at 192.0.2.1:9601").contains("192.0.2"));
        assertFalse(Direct.AT_HOME_ONLY.matches(".*[0-9].*"));
    }

    @Test public void onlyAnAddressOnThisNetworkIsDialledWithoutKnowingWhoseItIs() {
        String mx=MxAddress.make(a.publicKeyData());
        for(String here:new String[]{"192.168.1.20:9601","10.0.0.5:9601","172.16.4.2:9601","172.31.255.1:9601","127.0.0.1:9601",
                "169.254.3.3:9601","[fe80::1]:9601","[fd00::5]:9601"})
            assertTrue(here,Direct.onThisNetwork(mx+"@"+here));
        // Anywhere else may be a relay: public addresses, names, carrier NAT, a permanent address, no address.
        for(String there:new String[]{mx+"@203.0.113.5:9001",mx+"@198.51.100.7:9601",mx+"@relay.example.org:9001",
                mx+"@100.64.0.1:9601",mx+"@172.32.0.1:9601",mx+"@[2001:db8::1]:9001","MAX#0x3082#"+mx+"@203.0.113.5:9001",
                mx+"@192.168.1.20",mx,"",null})
            assertFalse(String.valueOf(there),Direct.onThisNetwork(there));
    }

    @Test public void aDoorsAddressCarriesTheKeyItIsSealedTo() {
        String door=Direct.door(a.publicKeyHex(),"192.168.1.20",9601);
        assertTrue(Direct.isDirect(door,a.publicKeyHex()));
        assertEquals(Direct.key(a.publicKeyHex()),Direct.keyOf(door));
        assertNotEquals(Direct.key(b.publicKeyHex()),Direct.keyOf(door));
        assertEquals("",Direct.keyOf("MxNotAKey@192.168.1.20:9601"));
        assertEquals("",Direct.keyOf("192.168.1.20:9601"));
        assertEquals("",Direct.keyOf(null));
    }
}
