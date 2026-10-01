package org.mininotes.android;

import static org.junit.Assert.*;

import com.eurobuddha.maxima.core.MaximaNode;
import com.eurobuddha.maxima.core.codec.MiniData;
import com.eurobuddha.maxima.core.crypto.Hashes;
import com.eurobuddha.maxima.core.identity.MaximaIdentity;
import com.eurobuddha.maxima.core.session.HostPool;
import com.eurobuddha.maxima.core.store.FileStore;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * A real node kept to the owner's choice of relays, on this machine with no internet. Nothing here ever makes
 * the node fill its pool, so a documentation address given as a relay is only ever a name in a list, never
 * dialled. What is dialled is on loopback: a real door (the transport's own endpoint, which greets as a relay
 * does), a port that says nothing, and a port nobody is at. See Relays.Choice.
 */
public class RelaysNodeTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    // Stand-ins for the shipped relays, and a relay somebody gossiped: documentation addresses, never dialled here.
    private static final List<String> SHIPPED=Arrays.asList("203.0.113.1:9501","203.0.113.2:9501");
    private static final String GOSSIPED="203.0.113.9:9001";
    private final List<MaximaNode> nodes=new ArrayList<>();
    private final List<ServerSocket> sockets=new ArrayList<>();

    @Before public void sha3(){Hashes.setSha3(Sha3::of);}

    @After public void stop() throws Exception {
        for(ServerSocket one:sockets)one.close();
        for(MaximaNode one:nodes)try{one.stop();}catch(RuntimeException ignored){/* its store only */}
    }

    private MaximaNode node(int seed) {
        byte[] s=new byte[32];Arrays.fill(s,(byte)seed);
        MaximaNode made=new MaximaNode(MaximaIdentity.fromSeed(new MiniData(s)),"test",2);
        nodes.add(made);
        return made;
    }

    /** A store holding what discovery saved last time: one relay somebody named. */
    private FileStore rememberingOne() throws Exception {
        FileStore kept=new FileStore(temp.newFolder());
        kept.put("peers",GOSSIPED,"0");kept.flush();
        return kept;
    }

    private static List<String> known(MaximaNode node) {
        List<String> out=new ArrayList<>();
        for(HostPool.HostRecord one:node.pool().knownByScore())out.add(one.hostPort);
        Collections.sort(out);
        return out;
    }

    private ServerSocket listening() throws Exception {
        ServerSocket one=new ServerSocket();one.bind(new InetSocketAddress("127.0.0.1",0));sockets.add(one);return one;
    }

    @Test public void aRelayAnswersOnlyIfAGreetingComesBack() throws Exception {
        MaximaNode relayLike=node(21);
        int door=relayLike.startDirect(0);
        assertTrue(door>0);
        assertTrue(Relays.answers("127.0.0.1:"+door,3000,"test"));
        // Somebody listening who says nothing is not a relay; nor is nobody at all.
        ServerSocket silent=listening();
        long began=System.currentTimeMillis();
        assertFalse(Relays.answers("127.0.0.1:"+silent.getLocalPort(),500,"test"));
        assertTrue("the leash held",System.currentTimeMillis()-began<5000);
        int closed;try(ServerSocket gone=new ServerSocket(0)){closed=gone.getLocalPort();}
        assertFalse(Relays.answers("127.0.0.1:"+closed,500,"test"));
        assertFalse(Relays.answers("no port",500,"test"));
    }

    @Test public void offGossipAndSavedRelaysNeverBecomeCandidates() throws Exception {
        MaximaNode up=node(22);
        List<String> own=Collections.singletonList(nobody());
        Relays.Choice chosen=new Relays.Choice(up,own,false,2,500,SHIPPED);
        up.setStore(rememberingOne());
        // Only the owner's: the saved one was never let in, and discovery - which would dial it to check it - is stopped.
        chosen.apply(System.currentTimeMillis());
        assertEquals(own,known(up));
        assertTrue(up.discovery().verified().isEmpty());
        // What a relay's greeting names is not taken up either.
        up.discovery().addPeer(GOSSIPED);
        assertEquals(0,up.discovery().unverifiedCount());
        assertEquals(own,known(up));
    }

    @Test public void onThenOffThenOnAgainWhileRunning() throws Exception {
        MaximaNode up=node(23);
        List<String> own=Collections.singletonList(nobody());
        Relays.Choice chosen=new Relays.Choice(up,own,true,2,500,SHIPPED);
        up.setStore(rememberingOne());
        chosen.apply(System.currentTimeMillis());
        // On: the owner's, the shipped ones, and the one remembered from gossip.
        List<String> all=new ArrayList<>(SHIPPED);all.add(GOSSIPED);all.addAll(own);Collections.sort(all);
        assertEquals(all,known(up));
        // Off, while running: everything that is not the owner's is let go of at once.
        chosen.choose(own,false);
        chosen.apply(System.currentTimeMillis());
        assertEquals(own,known(up));
        // And stays gone: discovery stopped with the switch, so gossip cannot bring it back.
        up.discovery().addPeer(GOSSIPED);
        assertEquals(0,up.discovery().unverifiedCount());
        // On again: the shipped relays come back at once. What gossip had found waits for the next start, since
        // discovery cannot be started again in the same process.
        chosen.choose(own,true);
        chosen.apply(System.currentTimeMillis());
        assertTrue(known(up).containsAll(SHIPPED));
        assertTrue(known(up).containsAll(own));
        assertFalse(known(up).contains(GOSSIPED));
    }

    @Test public void aRelayRemovedIsLetGoOfUnlessItIsAlsoAShippedOneInUse() throws Exception {
        MaximaNode up=node(24);
        // Here the shipped ones are on this machine too, since one of them is also the owner's and is tried.
        String mine=nobody(),alsoShipped=nobody();
        List<String> shipped=Arrays.asList(alsoShipped,nobody());
        Relays.Choice chosen=new Relays.Choice(up,Arrays.asList(mine,alsoShipped),false,2,500,shipped);
        chosen.apply(System.currentTimeMillis());
        List<String> both=new ArrayList<>(Arrays.asList(mine,alsoShipped));Collections.sort(both);
        assertEquals(both,known(up));
        chosen.choose(Collections.singletonList(alsoShipped),false);
        assertEquals(Collections.singletonList(alsoShipped),known(up));
        // With the public relays on, taking a shipped relay out of the owner's list leaves it where it is.
        chosen.choose(Collections.emptyList(),true);
        assertTrue(known(up).contains(alsoShipped));
    }

    @Test public void anOwnRelayThatDoesNotTakeThisDeviceIsLetBeAWhile() throws Exception {
        // It accepts, and hangs up: no greeting, so no attachment.
        ServerSocket hangsUp=listening();
        AtomicInteger dialled=new AtomicInteger();
        Thread counting=new Thread(()->{
            while(!hangsUp.isClosed())try(Socket one=hangsUp.accept()){dialled.incrementAndGet();}catch(Exception closed){/* done */}
        });counting.setDaemon(true);counting.start();
        String mine="127.0.0.1:"+hangsUp.getLocalPort();
        MaximaNode up=node(25);
        Relays.Choice chosen=new Relays.Choice(up,Collections.singletonList(mine),false,2,1000,SHIPPED);
        long now=System.currentTimeMillis();
        assertFalse(chosen.apply(now));
        assertFalse(chosen.attached(mine));
        assertEquals(0,chosen.connected());
        waitFor(()->dialled.get()==1);
        // A round later it is not dialled again; once the rest is over, it is.
        chosen.apply(now+30_000);
        Thread.sleep(300);
        assertEquals(1,dialled.get());
        chosen.apply(now+Relays.REST+1);
        waitFor(()->dialled.get()==2);
    }

    @Test public void theOwnersRelayIsAttachedFirstAndHandedOutFirst() throws Exception {
        // The door greets like a relay does, and the transport attaches to it as to one: a relay on this machine.
        MaximaNode relayLike=node(26);
        int door=relayLike.startDirect(0);
        String mine="127.0.0.1:"+door;
        MaximaNode up=node(27);
        boolean was=MaximaNode.ALLOW_ALL_IP;
        MaximaNode.ALLOW_ALL_IP=true;
        try {
            Relays.Choice chosen=new Relays.Choice(up,Collections.singletonList(mine),false,2,3000,SHIPPED);
            assertTrue(chosen.apply(System.currentTimeMillis()));
            assertTrue(chosen.attached(mine));
            assertEquals(1,chosen.connected());
            assertEquals("Connected",Relays.state(true,chosen.attached(mine)));
            List<String> handed=Relays.ownFirst(up.myAddresses(),chosen.own());
            assertFalse(handed.isEmpty());
            assertTrue(handed.get(0).endsWith("@"+mine));
        } finally {MaximaNode.ALLOW_ALL_IP=was;}
    }

    @Test public void theOwnersRelayPushesOutAPublicOneWhenThePoolIsFull() throws Exception {
        MaximaNode publicLike=node(28),ownLike=node(29),up=node(30);
        String shipped="127.0.0.1:"+publicLike.startDirect(0),mine="127.0.0.1:"+ownLike.startDirect(0);
        boolean was=MaximaNode.ALLOW_ALL_IP;
        MaximaNode.ALLOW_ALL_IP=true;
        try {
            // One relay held at a time, and a public one already holding it.
            Relays.Choice chosen=new Relays.Choice(up,Collections.emptyList(),true,1,3000,Collections.singletonList(shipped));
            assertTrue(up.pool().attachOne(shipped,3000));
            // The owner adds theirs: it comes in, and the public one makes room for it.
            chosen.choose(Collections.singletonList(mine),true);
            assertTrue(chosen.apply(System.currentTimeMillis()));
            assertEquals(Collections.singletonList(mine),up.pool().activeHosts());
            // Switched off, the public one is not even a candidate any more; the owner's stays.
            chosen.choose(Collections.singletonList(mine),false);
            assertFalse(chosen.apply(System.currentTimeMillis()));
            assertEquals(Collections.singletonList(mine),known(up));
            assertEquals(Collections.singletonList(mine),up.pool().activeHosts());
        } finally {MaximaNode.ALLOW_ALL_IP=was;}
    }

    private static void waitFor(java.util.function.BooleanSupplier done) throws InterruptedException {
        long until=System.currentTimeMillis()+5000;
        while(!done.getAsBoolean()&&System.currentTimeMillis()<until)Thread.sleep(20);
        assertTrue(done.getAsBoolean());
    }

    /** An own relay nobody is at, on this machine: trying it is refused at once, and never leaves it. */
    private static String nobody() throws Exception {
        try(ServerSocket gone=new ServerSocket()){gone.bind(new InetSocketAddress("127.0.0.1",0));return "127.0.0.1:"+gone.getLocalPort();}
    }
}
