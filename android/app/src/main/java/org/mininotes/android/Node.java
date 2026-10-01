// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import android.content.Context;
import android.content.SharedPreferences;
import com.eurobuddha.maxima.core.MaximaNode;
import com.eurobuddha.maxima.core.contacts.Contact;
import com.eurobuddha.maxima.core.codec.MiniData;
import com.eurobuddha.maxima.core.crypto.Hashes;
import com.eurobuddha.maxima.core.identity.MaximaIdentity;
import com.eurobuddha.maxima.core.store.FileStore;
import java.io.File;
import java.util.List;

/**
 * This pad, as a node on the Maxima network.
 *
 * <p>The address was asked of other apps for a long time and the answer was always somebody else's to give:
 * Minima Core has no Maxima in its build at all, and the transport app answers only apps signed with its
 * own key. Both of those are somebody else's decision about this app. So the app stopped asking. The
 * transport is a library — Java 11, no dependencies, made to be embedded — and Mininotes now carries it,
 * the way the transport's own phone app does. The address is not fetched. It is simply known, because this
 * is the thing that has one.
 *
 * <p>Two things the platform will not supply, injected rather than depended on:
 * <ul>
 *   <li><b>SHA3-256</b>, which Android has at no version — see {@link Sha3}. The core checks any hash it
 *       is handed against a published answer before it will use one, because a subtly wrong hash makes this
 *       phone a different phone on the network, quietly.</li>
 *   <li><b>Where to keep things</b>: the app's own folder, so a contact and an address survive the process
 *       being killed, which on a phone is whenever the system feels like it.</li>
 * </ul>
 *
 * <p>Starting means attaching to relays over the network, which takes as long as it takes. Nothing here is
 * ever called from the interface thread: {@link #address} is handed to {@link Background}, like every other
 * slow thing in this app.
 */
final class Node {
    /** What this build calls itself on the wire. */
    private static final String VERSION="mininotes";
    /** How many relays to hold at once. More than one, because one is a single point of failure. */
    private static final int RELAYS=2;
    /** Long enough for a slow phone on a slow network; short enough that nobody waits for ever. */
    private static final int WAITING=30000;

    /**
     * How often the node is looked after, in seconds.
     *
     * <p>The transport does not look after itself: it is a library, and says in as many words that whoever
     * carries it drives its upkeep. A relay stops reading from a client it has not heard from in ten
     * minutes, and a keep-alive is due every two — so left alone, this phone was dropped by every relay it
     * had within ten quiet minutes of opening, and nothing ever went back for another. It looked exactly
     * like a node that was working, until something was sent to it.
     */
    private static final int BEAT=30;
    /**
     * The same, while nobody is looking at the pad. Every round wakes the phone, and a keep-alive is only
     * due every two minutes, so a round every thirty seconds in a pocket was three wake-ups in four spent
     * finding nothing due. Ninety still lands inside every two-minute window.
     */
    private static final int AWAY_BEAT=90;
    /** Whether the pad is on screen. Rounds come at {@link #BEAT} while it is, {@link #AWAY_BEAT} while not. */
    private static volatile boolean near;

    /** Said by the screen as it comes and goes. Coming back brings the next round forward. */
    static void near(boolean on) {
        boolean was=near;near=on;
        java.util.concurrent.ScheduledExecutorService up;
        synchronized(Node.class){up=keeper;}
        if(on&&!was&&up!=null)try{up.execute(Node::lookAfter);}catch(RuntimeException stopped){/* none */}
    }

    /**
     * One round now, on the caller's thread: the phone's own alarm, which is what still runs while the
     * phone sleeps and the upkeep thread does not. Waits for nothing it does not have to.
     */
    static void roundNow(){lookAfter();}

    private static MaximaNode node;
    private static boolean tried;
    private static java.util.concurrent.ScheduledExecutorService keeper;
    /** How many rounds of upkeep there have been. Only the upkeep thread touches it. */
    private static int beats;
    /** Something else that wants doing every round, after the node has been looked after. */
    private static volatile Runnable alsoEachBeat;
    private static final java.util.concurrent.ExecutorService ALSO=
        java.util.concurrent.Executors.newSingleThreadExecutor(work->{
            Thread one=new Thread(work,"mininotes-each-beat");one.setDaemon(true);return one;});

    /**
     * Whatever else should happen every half minute for as long as the process lives. Run on a thread of
     * its own, so that a slow round of it never holds up the keep-alives that come next.
     */
    static void everyBeat(Runnable also) {
        alsoEachBeat=also;
        // Nothing more each round means the notebook is closing or locked: nothing is held for anybody either
        // until it is open again, and a home asked meanwhile says "not now".
        if(also==null)hosting(null);
    }

    private Node(){}

    /**
     * This device's addresses, first the one to hand out. Empty while no relay has been reached — which is
     * an answer, not a failure: a phone with no way out has no address anybody could use.
     *
     * <p>Blocking. Call it on the worker.
     */
    static List<String> addresses(Context where) throws Exception {
        MaximaNode up=started(where);
        // Only between the owner's devices, the one way in is this device's door on the network it is on, so a
        // code made here pairs only there - and never names a relay this device is no longer on.
        if(onlyMine)return doorsHere(up);
        // What is handed out - a pairing code, an address shown or copied - names a relay, never this device's
        // own door: a PC's door is at its home's public address, and a code can be shown to anybody. Paired
        // devices still learn the door, sealed, from the transport's own contact exchange. Only a device with
        // no relay at all hands out its door, since that is then the one way in.
        String me=up.publicKeyHex();
        List<String> relays=new java.util.ArrayList<>();
        for(String one:up.myAddresses())if(!Direct.isDirect(one,me))relays.add(one);
        // The owner's own relays are relays like any other here, and come first: they are the ones chosen.
        Relays.Choice chosen=choice;
        List<String> own=chosen==null?java.util.Collections.emptyList():chosen.own();
        return Relays.ownFirst(relays.isEmpty()?up.myAddresses():relays,own);
    }

    /**
     * The permanent address, where one exists. It survives the host moving, which an ordinary one does not.
     * None while notes go only between the owner's devices: it is a directory to ask, and a directory is a relay.
     */
    static String permanent(Context where) throws Exception {
        MaximaNode up=started(where);
        return onlyMine?"":up.permanentAddress();
    }

    /** This device's door as an address on each local network it is on, or none where the door did not open. */
    private static List<String> doorsHere(MaximaNode up) {
        List<String> out=new java.util.ArrayList<>();
        if(door<=0)return out;
        for(String ip:Nearby.addressesHere())out.add(Direct.door(up.publicKeyHex(),ip,door));
        return out;
    }

    /**
     * Whether this app is allowed on the network at all.
     *
     * <p>On most Android phones this is granted at install and never thought about again. GrapheneOS lets
     * the owner refuse it per app, and a refused app is not told: sockets simply fail. A node with no way
     * out reaches no relay, has no address, and looks exactly like a node that is still starting — which
     * is the one thing it must not look like, because one of those is worth waiting for and the other
     * never will be.
     */
    static boolean allowedOnTheNetwork(Context where) {
        return where.checkSelfPermission(android.Manifest.permission.INTERNET)
            ==android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** Whether the node is up, without starting one. */
    static synchronized boolean running(){return node!=null;}

    /** The node itself, started if it is not. Null only where starting failed. */
    static MaximaNode node(Context where) {
        try{return started(where);}catch(Exception e){return null;}
    }

    /**
     * The one thing here that is done under a lock: making the node, once.
     *
     * <p>Everything else used to be as well, and that was a fault. Telling contacts where this phone is can
     * take a minute and a half - the transport gives a round of it that long - and it was done holding the
     * same lock a note needs in order to be sent. So for the first ninety seconds after the pad was opened,
     * nothing written in it went anywhere. The node is made to be used from several threads at once; only
     * the making of it has to happen one at a time.
     */
    private static synchronized MaximaNode started(Context where) throws Exception {
        start(where);
        return node;
    }

    /**
     * Who to tell when something arrives for this app. Set once, before anything is sent, so a reply to
     * the first thing sent is not the one message that lands with nobody listening.
     */
    static boolean listen(Context where,java.util.function.Consumer<byte[]> heard) {
        MaximaNode up=node(where);
        if(up==null)return false;
        told=heard;
        up.setMessageListener((message,id)->arrived(message,heard));
        return true;
    }

    /** One message the transport brought, handed on where it is this app's. */
    static void arrived(com.eurobuddha.maxima.core.msg.MaximaMessage message,java.util.function.Consumer<byte[]> heard) {
        if(message==null)return;
        if(message.mApplication==null||!APPLICATION.equals(message.mApplication.toString()))return;
        if(message.mData==null)return;
        // That something came, and how big. Whether the transport brought nothing or the app dropped
        // what it brought are two different faults, and from the outside they look the same.
        android.util.Log.i("Mininotes/Node","heard "+message.mData.getBytes().length+" bytes for this app");
        if(message.mFrom!=null)sent(message.mData.getBytes(),message.mFrom.to0xString());
        heard.accept(message.mData.getBytes());
    }

    /**
     * Who the transport says sent each of the last few things that came, by what came: the key it knows the sender's
     * device by, which the transport checked against the message's signature. What came goes on to the notebook as
     * bytes alone, and is opened there - sometimes later, from the inbox - so this is where it is looked up.
     */
    private static final java.util.Map<String,String> SENT_BY=java.util.Collections.synchronizedMap(
        new java.util.LinkedHashMap<String,String>(){
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<String,String> eldest){return size()>64;}
        });

    static void sent(byte[] message,String by){if(message!=null&&by!=null)SENT_BY.put(digest(message),by);}

    /** The key of the device that sent this, as the transport said; empty where it came before this run, or is not known. */
    static String sentBy(byte[] message){String by=message==null?null:SENT_BY.get(digest(message));return by==null?"":by;}

    private static String digest(byte[] message) {
        try{return new MiniData(java.security.MessageDigest.getInstance("SHA-256").digest(message)).to0xString();}
        catch(java.security.NoSuchAlgorithmException never){throw new IllegalStateException(never);}
    }

    /** Whoever the node was last told to bring what arrives to. */
    private static volatile java.util.function.Consumer<byte[]> told;

    /**
     * Something collected from this device's home, brought where anything that arrives is brought: it is the
     * same sealed thing its sender would have handed the network, so it is taken in the same way - or kept
     * sealed in the inbox while the notebook is locked.
     */
    static void deliver(byte[] message) {
        java.util.function.Consumer<byte[]> to=told;
        if(to!=null&&message!=null)to.accept(message);
    }

    /**
     * Tell another node about this one, and learn it back.
     *
     * <p>An address is a snapshot. A node that restarts, or moves to another relay, is at a different one
     * within the minute - key and host both - so a code scanned at nine o'clock is a wrong number by ten,
     * and a note sent to it is accepted by a relay on behalf of nobody. That is not a fault to work around:
     * the transport already has the answer, and this app was not using it.
     *
     * <p>An introduction makes each node a <i>contact</i> of the other: a stable identity key, every address
     * it is currently reachable at, and the directory to ask when none of them answer. From then on the
     * pair keep each other current by themselves.
     *
     * @return the peer's identity key, to be kept beside their address, or empty if they did not answer
     */
    static String introduce(Context where,String address) throws Exception {
        MaximaNode up=node(where);
        if(up==null||address==null||address.trim().isEmpty())return "";
        if(onlyMine)return introduceHere(up,address.trim());
        // One introduction at a time, because "whoever is new" is how the peer is recognised - but under
        // a lock of its own, so that meeting somebody does not hold up a note on its way to somebody else.
        synchronized(MEETING) {
            java.util.Set<String> before=new java.util.HashSet<>();
            for(Contact known:up.contacts())before.add(known.publicKey);
            up.introduce(address.trim(),true);
            // Whoever is new is the one we just met.
            for(Contact known:up.contacts())if(!before.contains(known.publicKey))return known.publicKey;
            // Met before: find them by the address we dialled, which they may since have added to.
            for(Contact known:up.contacts())
                if(known.addresses.contains(address.trim()))return known.publicKey;
            return "";
        }
    }
    private static final Object MEETING=new Object();

    /**
     * The same, while notes go only between the owner's devices: to a door on this network and nowhere else.
     *
     * <p>The transport's own way cannot work here. It sends this device's card to the address, and the other
     * device answers with its card to the first address this one gave - but a device with no relay gives none,
     * so the answer goes nowhere and neither learns the other. So the card still goes (it is what makes this
     * device known at the far door), and the far device is taken from the door's own address: a door is sealed
     * to the device's identity key, which is the key the address carries. Known, and noted as heard here, a
     * note goes to it at once rather than after its next announcement.
     *
     * @throws IllegalStateException for an address that is not on this network, which nothing here dials
     */
    static String introduceHere(MaximaNode up,String address) throws Exception {
        if(!Direct.onThisNetwork(address))throw new IllegalStateException(ON_THE_SAME_WIFI);
        String key=Direct.keyOf(address);
        if(key.isEmpty())throw new IllegalStateException("That is not the address of a device's door.");
        synchronized(MEETING) {
            up.introduce(address,false);
            if(up.contact(key)==null)up.storeContact(new Contact(key));
            up.noteLanPeer(key,address.substring(address.lastIndexOf('@')+1));
            return key;
        }
    }

    /** Why a code from elsewhere does not pair while notes go only between the owner's devices. */
    static final String ON_THE_SAME_WIFI="With notes only between your devices, pairing needs both devices on the same Wi-Fi.";

    /** One peer, as the transport knows them now, or null if it does not. */
    static Contact known(Context where,String key) {
        if(key==null||key.trim().isEmpty())return null;
        MaximaNode up=node(where);
        return up==null?null:up.contact(key.trim());
    }

    /**
     * Everybody told where this phone is now.
     *
     * <p>Done when the app opens, because that is when this node has just been given an address it did not
     * have a minute ago. Without it the other end goes on sending to where we were. Off the caller's thread: an
     * address that is gone costs the transport's whole connection wait, and a note should not wait behind it.
     */
    static void tellEverybody(Context where) {
        MaximaNode up=node(where);
        // Not while notes go only between the owner's devices: telling is sent to every address a device has,
        // relays included, and published to a directory first. Paired devices hear this one on the network.
        if(up==null||onlyMine)return;
        tellSoon(up);
    }

    /** What telling one device came to: taken at its door on this network, at an address kept for it, or nowhere. */
    static final int NOT_TOLD=0,TOLD=1,TOLD_AT_ITS_DOOR=2;

    /**
     * This device's card - every address it has now - handed to one device: at its door on this network first,
     * asking for its card back, and then, unless only the door is wanted, at every address the transport keeps for it.
     *
     * <p>The transport's own telling stops at the first address a relay accepts, and a relay accepts for a device
     * that has left it as readily as for one still there. Seen on 0.1.028: the PC started again on other relays, its
     * card went to the first relay it had for the phone - one the phone had left - and the phone went on answering at
     * the PC's old relay, which took every answer for nobody. Notes the other way went to every address the phone had
     * and got through, so it looked like a PC that could send and not hear. A door on this network is the device
     * itself, so what it takes has arrived; asked back, the device sends its card to this one's first address, and
     * each then knows where the other is now.
     *
     * <p>Blocking: an address that is gone costs the transport's connection wait.
     */
    static int tell(MaximaNode up,Contact them,boolean doorOnly) {
        // A card with no address in it would leave the device with none for this one, which is worse than an old one.
        if(up==null||them==null||up.myAddresses().isEmpty())return NOT_TOLD;
        int told=NOT_TOLD;
        String door=up.lanAddressFor(them.publicKey);
        if(door!=null) {
            try{up.introduce(door,true);told=TOLD_AT_ITS_DOOR;}
            catch(Exception shut) {
                // Let go of as a note's send lets go of it (see Direct.send), so notes do not wait at it either.
                up.forgetLanPeer(them.publicKey);Direct.shut(them.publicKey,door,System.currentTimeMillis());Direct.SHUT_HERE.incrementAndGet();
            }
        }
        if(doorOnly)return told;
        for(String one:new java.util.ArrayList<>(them.addresses)) {
            if(one.equals(door))continue;
            try{up.introduce(one,false);if(told==NOT_TOLD)told=TOLD;}catch(Exception notThere){/* the next address */}
        }
        return told;
    }

    /** Every device the transport knows told where this one is now, as {@link #tell} does, and the directory too. {told, at a door, of how many}. */
    static int[] tellAll(MaximaNode up) {
        try{up.publishToMls();}catch(RuntimeException notNow){/* the transport's own round publishes again */}
        int told=0,atDoor=0;
        List<Contact> all=up.contacts();
        for(Contact one:all) {
            int said=tell(up,one,false);
            if(said!=NOT_TOLD)told++;
            if(said==TOLD_AT_ITS_DOOR)atDoor++;
        }
        return new int[]{told,atDoor,all.size()};
    }

    /** Telling, one at a time and on a thread of its own, so a note never waits behind an address that is gone. */
    private static final java.util.concurrent.ExecutorService TELLING=
        java.util.concurrent.Executors.newSingleThreadExecutor(work->{
            Thread one=new Thread(work,"mininotes-telling");one.setDaemon(true);return one;});
    /** A round of telling everybody waiting to start: another asked for meanwhile would say the same. */
    private static final java.util.concurrent.atomic.AtomicBoolean TELL_WAITING=new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * A paired device heard anew on this network, told where this device is at its door. Done then because that is
     * when it has a door to be told at - on the PC, never at start, when nothing has been heard yet - and because two
     * devices that each went on using the other's old relays are otherwise never put right. And whatever the notebook
     * could not say to it for want of a way is said now: see {@link #onMetHere}. Only between the owner's devices
     * nothing is told - that goes to relays too - but the rest is done, and the line is said all the same.
     */
    private static void metHere(MaximaNode up,String key) {
        try {
            TELLING.execute(()->{
                Contact them=up.contact(key);
                if(them==null)return;
                int told=onlyMine?NOT_TOLD:tell(up,them,true);
                Nearby local;
                synchronized(Node.class){local=nearby;}
                android.util.Log.i("Mininotes/Node","a paired device heard on this network ("+Direct.tag(key)+", "+(local==null?0:local.near())
                    +" near)"+(onlyMine?"":": told where this device is, at its door: "+(told==TOLD_AT_ITS_DOOR?"taken":"not taken")));
                java.util.function.Consumer<String> then=metThen;
                if(then!=null)try{then.accept(key);}catch(RuntimeException notNow){/* the next round says it */}
            });
        } catch(RuntimeException full){/* told at the next start, or when its addresses change */}
    }

    /** Who else is told when a paired device is heard anew, by its identity key: the notebook, set each round. */
    private static volatile java.util.function.Consumer<String> metThen;

    /**
     * What a device just heard on this network is owed and could not be given before, given now. Seen on 0.1.035: the
     * PC unlocked, said "open again" to nobody - neither phone's door had been heard yet - and heard both eight seconds
     * later; nothing said it again.
     */
    static void onMetHere(java.util.function.Consumer<String> then){metThen=then;}

    private static com.eurobuddha.maxima.core.media.MediaService media;
    private static com.eurobuddha.maxima.core.store.BlobStore blobs;

    /**
     * Where the pieces of a file that has gone up are kept on this device, and on a couple of relays - the
     * transport's own media service. Null only where the node could not be started.
     *
     * <p>This device keeps every piece of what it sent up, pinned, so that eviction never takes them: a relay
     * lets things go when its shelf is full, and this is the copy it can be put back from. The shelf is as big
     * as everything a pad may hold in files, since that is the most that could ever be sent up.
     */
    static synchronized com.eurobuddha.maxima.core.media.MediaService media(Context where) {
        if(media!=null)return media;
        MaximaNode up=node(where);
        if(up==null)return null;
        if(blobs==null){blobs=shelf(where);up.setLocalBlobs(blobs);}
        media=new com.eurobuddha.maxima.core.media.MediaService(up,blobs);
        return media;
    }

    /**
     * This device's own shelf of pieces, which its door hands out: where a file offered only from here is kept,
     * with nothing sent up (see {@link Drop}). Null only where the node could not be started.
     */
    static synchronized com.eurobuddha.maxima.core.store.BlobStore blobs(Context where) {
        return media(where)==null?null:blobs;
    }

    private static com.eurobuddha.maxima.core.store.BlobStore shelf(Context where) {
        return new com.eurobuddha.maxima.core.store.BlobStore(new File(where.getFilesDir(),"blobs"),Attachment.PLENTY+64L*1024*1024);
    }

    // ---- the door: see Direct and docs/DIRECT.md ----------------------------------------------------------

    /**
     * Whether this is the PC. The Windows build says true here as it copies this file; the phone's build
     * leaves it false. Only the PC asks its router for a public port: a phone moves between networks all
     * day, and asking every router it meets to open a port is not a thing to do from somebody's pocket.
     */
    static final boolean A_PC=false;
    /** The door's port, once it is open; -1 where it could not be. */
    private static volatile int door=-1;
    private static Nearby nearby;
    private static com.eurobuddha.maxima.core.net.ReachabilityManager reach;
    /** When the router was last asked, so a router that said no is asked again in ten minutes, not every round. */
    private static long askedAt;
    private static final long ASK_AGAIN=10L*60*1000;
    /** Which kinds of announcement not believed were last said in the log, and when. */
    private static volatile String refusedKinds="";
    private static volatile long refusedAt;
    /** The local networks as they were last round, so a move to another can be noticed. */
    private static String networks="";

    /**
     * The door opened: a port other devices can deliver to straight, with no relay between. It takes only what
     * is sealed to this device's own key and passes nothing on, and it hands out this device's own file
     * pieces, which is why the shelf is given to it first. The port is the same one each time where it can be,
     * so a device that heard it yesterday is not wrong today; the next free one where it cannot.
     */
    private static int open(Context where,MaximaNode made) {
        SharedPreferences kept=where.getSharedPreferences("node",Context.MODE_PRIVATE);
        int before;
        try{before=Integer.parseInt(kept.getString("door",""+Direct.PORT));}catch(NumberFormatException none){before=Direct.PORT;}
        java.util.LinkedHashSet<Integer> tries=new java.util.LinkedHashSet<>();
        if(before>1024&&before<65536)tries.add(before);
        for(int one=Direct.PORT;one<Direct.PORT+20;one++)tries.add(one);
        for(int one:tries) {
            int got=made.startDirect(one);
            if(got>0){if(got!=before)kept.edit().putString("door",""+got).apply();return got;}
        }
        return -1;
    }

    /**
     * The PC's router asked for a public port, and the port proved open from outside by a relay dialling it
     * back, before any contact is given the address. Lost, it is taken back from them as quickly. The
     * mapping is let go as the process ends; one left behind by a crash runs out on the router by itself.
     */
    private static void reachable(MaximaNode made) {
        final MaximaNode up=made;
        reach=new com.eurobuddha.maxima.core.net.ReachabilityManager(up,up::directPort,
            com.eurobuddha.maxima.core.net.ReachabilityManager.Gates.ALWAYS,
            new com.eurobuddha.maxima.core.net.ReachabilityManager.Listener() {
                @Override public void onVerified(String ipPort,String via) {
                    up.setDirectAddress(ipPort);
                    android.util.Log.i("Mininotes/Node","reachable directly");
                    tellSoon(up);
                }
                @Override public void onLost(String why) {
                    up.setDirectAddress("");
                    android.util.Log.i("Mininotes/Node","no longer reachable directly");
                    tellSoon(up);
                }
            });
        final com.eurobuddha.maxima.core.net.ReachabilityManager leaving=reach;
        Runtime.getRuntime().addShutdownHook(new Thread(leaving::shutdown,"mininotes-door-closing"));
        // Asked now, on its own thread, rather than a round from now - unless notes go only between the owner's
        // devices, when nothing could prove the port (see Direct.AT_HOME_ONLY).
        if(onlyMine)return;
        askedAt=System.currentTimeMillis();
        leaving.tick();
    }

    /** Everybody told where this device is now - off the thread that found out, since telling takes a while. */
    private static void tellSoon(MaximaNode up) {
        if(onlyMine||!TELL_WAITING.compareAndSet(false,true))return;
        try {
            TELLING.execute(()->{
                TELL_WAITING.set(false);
                try {
                    int[] n=tellAll(up);
                    // Counts only. A device told nowhere is one that will go on sending to where this one was.
                    android.util.Log.i("Mininotes/Node","told "+n[0]+" of "+n[2]+" device(s) where this device is now ("
                        +n[1]+" at their door on this network), holding "+up.myAddresses().size()+" address(es)");
                } catch(Throwable notNow){android.util.Log.w("Mininotes/Node","could not tell where this device is: "+notNow.getClass().getSimpleName());}
            });
        } catch(RuntimeException full){TELL_WAITING.set(false);/* the next change tells them */}
    }

    /**
     * The devices whose announcements on the local network are believed: the paired ones, by their identity
     * keys. Said again every round by whoever holds the notebook, since pairing changes.
     */
    static void pairedWith(java.util.Collection<String> keys) {
        Nearby local;
        synchronized(Node.class){local=nearby;}
        if(local!=null)local.pairedWith(keys);
    }

    /**
     * One plain line for the PC's Profile on whether devices away from home can reach it directly, or empty on
     * a phone, which does not try.
     */
    static String reachability() {
        if(!A_PC)return "";
        if(onlyMine)return Direct.AT_HOME_ONLY;
        com.eurobuddha.maxima.core.net.ReachabilityManager now;
        synchronized(Node.class){now=reach;if(node==null)return "";}
        if(now==null)return Direct.reachability(door>0,null,"not started");
        return Direct.reachability(door>0,now.state().name(),now.detail());
    }

    // ---- the PC as its owner's host: see Home and docs/DIRECT.md, phase 2 -------------------------------------

    /** The PC's home for its owner's phones, on its door; null on a phone, and where the door could not open. */
    private static volatile Home.Host host;
    /** When this phone last collected from its owner's PC. */
    private static volatile long collectedAt;

    static Home.Host host(){return host;}

    /** What the home needs of the notebook, said again every round; null while it is locked or closing. */
    static void hosting(Home.Notebook book) {
        Home.Host here=host;
        if(here!=null)here.notebook(book);
    }

    /** A round in which this phone reached its owner's PC and asked what it holds. */
    static void collected(long at){collectedAt=at;}

    /**
     * One plain line on what this device's home is doing: on the PC, what it holds for the owner's phones; on a
     * phone, that it collects from the PC, while it does. Empty where there is nothing to say.
     */
    static String home() {
        Home.Host here=host();
        if(here!=null)return Home.said(here.holding(),here.collectingNow(System.currentTimeMillis()));
        if(A_PC)return "";
        long at=collectedAt;
        return at>0&&System.currentTimeMillis()-at<=Home.COLLECTING?"Collects from your PC, with no relay between":"";
    }

    /**
     * The door's share of a round: said on the local network, devices gone quiet let go of, and on the PC the
     * router asked again when it is time. A move to another network is noticed here, by the local addresses
     * changing, and what was known about the last one is let go of.
     */
    private static void doorRound() {
        Nearby local;com.eurobuddha.maxima.core.net.ReachabilityManager router;
        synchronized(Node.class){local=nearby;router=reach;}
        String now=Nearby.here();
        boolean moved=!now.equals(networks)&&!networks.isEmpty();
        networks=now;
        if(local!=null) {
            if(moved)local.forgetAll();else local.forgetQuiet();
            local.announce();
            // Counts only, and not every round: when a kind of refusal first turns up, and then once in ten minutes.
            String not=local.refusals(false),kinds=not.replaceAll(" \\([^)]*\\)","").replaceAll("[0-9]+ ","");
            long at=System.currentTimeMillis();
            if(!not.isEmpty()&&(!kinds.equals(refusedKinds)||at-refusedAt>=ASK_AGAIN)) {
                android.util.Log.i("Mininotes/Node","announcements heard on this network and not believed: "+local.refusals(true));
                refusedKinds=kinds;refusedAt=at;
            }
        }
        // The router is asked only while helpers may be used: without a relay to dial it back nothing can prove the
        // port, and an address nobody proved is not given out (see Direct.AT_HOME_ONLY).
        if(router==null||onlyMine)return;
        if(moved){router.onNetworkChanged();askedAt=0;}
        long at=System.currentTimeMillis();
        if(router.state()!=com.eurobuddha.maxima.core.net.ReachabilityManager.State.OFF||at-askedAt>=ASK_AGAIN) {
            if(router.state()==com.eurobuddha.maxima.core.net.ReachabilityManager.State.OFF)askedAt=at;
            router.tick();
        }
    }

    /**
     * The pieces of something that went up, let go here: the file is no longer kept, or went up again as
     * something else. The transport's store has no way to let go of a pinned piece, and a piece pinned for
     * ever is a pad that fills up with what it has already deleted, so the files are removed where they lie.
     * Its count of what it holds is then high until the node next starts, which errs towards saying it is
     * full rather than overfilling it.
     */
    static void forget(Context where,String manifest) {
        com.eurobuddha.maxima.core.media.MediaManifest said=com.eurobuddha.maxima.core.media.MediaManifest.decode(manifest);
        if(said==null)return;
        File shelf=new File(where.getFilesDir(),"blobs");
        for(String chunk:said.chunkIds) {
            String name=chunk.startsWith("0x")||chunk.startsWith("0X")?chunk.substring(2):chunk;
            // A name that is not a hash is not one of these pieces, and never a path.
            if(!name.matches("[0-9A-Fa-f]{64}"))continue;
            for(String one:new String[]{name.toUpperCase(java.util.Locale.ROOT),name}) {
                //noinspection ResultOfMethodCallIgnored
                new File(new File(shelf,"pinned"),one).delete();
                //noinspection ResultOfMethodCallIgnored
                new File(shelf,one).delete();
            }
        }
    }

    /** The application string this app owns, kept beside the node that filters on it. */
    private static final String APPLICATION=Post.APPLICATION;

    private static void start(Context where) throws Exception {
        if(node!=null)return;
        // Once. A second attempt after a real failure is the reader's to ask for, not something to retry
        // behind their back while they wait.
        if(tried&&node==null)throw new IllegalStateException("The node could not be started.");
        tried=true;
        Hashes.setSha3(Sha3::of);
        MaximaIdentity me=identity(where);
        MaximaNode made=new MaximaNode(me,VERSION,RELAYS);
        // Before the store: the relays saved from earlier runs come out of it through discovery, and are let in
        // only as the owner has chosen (see Relays).
        onlyMine=onlyMine(where);
        Relays.Choice chosen=new Relays.Choice(made,ownRelays(where),publicRelays(where),onlyMine,RELAYS,WAITING,Relays.PUBLIC);
        made.setStore(new FileStore(new File(where.getFilesDir(),"node")));
        made.setName(nameHere(where));
        // The shelf before the door, because the door is handed it as it opens and cannot be given it after.
        blobs=shelf(where);
        made.setLocalBlobs(blobs);
        door=open(where,made);
        // The owner's relays first, then - where the public ones are on - the shipped list as the ground
        // discovery starts from, never the whole of it: a relay that answers gossips its own, and the pool keeps
        // the ones that work. None of it is handed over as the transport's floor, which could not be taken
        // back if the owner switches the public relays off while this runs.
        chosen.apply(System.currentTimeMillis());
        int attached=made.start(new java.util.ArrayList<>(),WAITING);
        // Counts, not the address itself: an address is not a secret but it does name this phone, and a
        // log is read by more things than the person who owns it.
        android.util.Log.i("Mininotes/Node","attached to "+attached+" relays ("+chosen.own().size()+" of the owner's kept, public relays "
            +(chosen.publicOn()?"on":"off")+(onlyMine?", only between the owner's devices":"")+"), holding "+made.myAddresses().size()+" address(es)");
        choice=chosen;
        node=made;
        Nearby local=new Nearby(made,made::directPort,"0.0.0.0",Direct.PORT);
        local.onMet(key->metHere(made,key));
        boolean hears=door>0&&local.open();
        nearby=local;
        if(hears)local.announce();
        android.util.Log.i("Mininotes/Node","door "+(door>0?"open":"could not open")
            +(hears?", hearing the local network":", not hearing the local network"));
        if(A_PC&&door>0) {
            reachable(made);
            // The PC keeps what comes for its owner's phones, and they collect it on the same door.
            Home.Host here=new Home.Host(made.publicKeyHex());here.open();host=here;
        }
        keeper=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(work->{
            Thread one=new Thread(work,"mininotes-node-upkeep");one.setDaemon(true);return one;});
        next();
    }

    /** The next round, as far off as whether anybody is looking allows. */
    private static void next() {
        java.util.concurrent.ScheduledExecutorService up;
        synchronized(Node.class){up=keeper;}
        if(up==null)return;
        try{up.schedule(()->{try{lookAfter();}finally{next();}},near?BEAT:AWAY_BEAT,java.util.concurrent.TimeUnit.SECONDS);}
        catch(RuntimeException stopped){/* shut down */}
    }

    /**
     * One round of upkeep: keep-alives, relays that have gone quiet swapped for ones that answer, everybody
     * told if that moved this phone, and the transport's own twenty-minute round when it falls due.
     *
     * <p>Outside the lock, because finding a new relay takes as long as the network takes and a note being
     * sent should not have to wait behind it. And nothing thrown here gets out: a scheduled task that
     * throws once is never run again, which would be the old fault back again, silently, an hour in.
     */
    private static void lookAfter() {
        MaximaNode up;
        synchronized(Node.class){up=node;}
        if(up==null)return;
        // One round at a time: the alarm and the upkeep thread can both arrive at once after a sleep.
        if(!ROUND.compareAndSet(false,true))return;
        try{round(up);}finally{ROUND.set(false);}
    }
    private static final java.util.concurrent.atomic.AtomicBoolean ROUND=new java.util.concurrent.atomic.AtomicBoolean();

    private static void round(MaximaNode up) {
        try {
            // The owner's relays kept first, and nothing else while the public ones are off. What changes here
            // comes before the transport's own look at what changed, so contacts are told from here.
            Relays.Choice chosen=choice;
            if(chosen!=null&&chosen.apply(System.currentTimeMillis()))tellSoon(up);
        } catch(Throwable notNow){android.util.Log.w("Mininotes/Node","relay upkeep failed: "+notNow.getClass().getSimpleName());}
        // The transport's own upkeep is all relays and directories - keep-alives, finding relays, telling every
        // address a contact has, publishing to a directory, asking one where a contact went - so while notes go
        // only between the owner's devices it is not run at all. The door, the network and the outbox are ours.
        if(!onlyMine)try {
            java.util.Set<String> before=new java.util.HashSet<>(up.myAddresses());
            up.maintain(WAITING);
            java.util.Set<String> after=new java.util.HashSet<>(up.myAddresses());
            // The same number of addresses can be other ones - a relay swapped for another - and then everybody is
            // told, at every address and at their doors here: the transport tells only the first address that takes it.
            boolean moved=!before.equals(after);
            if(moved)tellSoon(up);
            // Said when it changes, and otherwise once in ten minutes: enough to see afterwards that the
            // node was being looked after through a quiet hour, without a line every thirty seconds.
            if(moved||++beats%20==0)
                android.util.Log.i("Mininotes/Node","upkeep: holding "+after.size()+" address(es)"
                    +(!moved?"":before.size()!=after.size()?", was "+before.size():", other ones than before"));
        } catch(Throwable notNow){android.util.Log.w("Mininotes/Node","upkeep failed: "+notNow.getClass().getSimpleName());}
        try{doorRound();}catch(Throwable notNow){android.util.Log.w("Mininotes/Node","door upkeep failed: "+notNow.getClass().getSimpleName());}
        final Runnable also=alsoEachBeat;
        if(also!=null)try{ALSO.execute(()->{try{also.run();}catch(Throwable notNow){/* next round */}});}
            catch(RuntimeException full){/* next round */}
    }

    // ---- the owner's own relays: see Relays and docs/DIRECT.md -------------------------------------------------

    /** The owner's relays and the switch, kept to while the node runs; null until it has started. */
    private static volatile Relays.Choice choice;
    /** Changes to the kept relays, one at a time, so two quick taps cannot each keep half of what they meant. */
    private static final Object CHOOSING=new Object();

    /** The owner's relays, as kept. */
    static List<String> ownRelays(Context where) {
        return Relays.fromKept(where.getSharedPreferences("node",Context.MODE_PRIVATE).getString("relays",""));
    }

    /** Whether the relays shipped with the app are used as well. On until the owner says otherwise. */
    static boolean publicRelays(Context where) {
        return Relays.publicOn(where.getSharedPreferences("node",Context.MODE_PRIVATE).getString("public relays","on"));
    }

    /** Whether notes go only between the owner's devices, as the running node keeps to it. See Relays.ONLY_MINE. */
    private static volatile boolean onlyMine;

    /** Whether helpers may carry what no door or home took: relays, and whatever comes after them. */
    static boolean helpers(){return !onlyMine;}

    /** Whether notes go only between the owner's devices, as kept. Also through helpers until the owner says otherwise. */
    static boolean onlyMine(Context where) {
        return Relays.onlyMine(where.getSharedPreferences("node",Context.MODE_PRIVATE).getString("travel","helpers"));
    }

    /**
     * How notes travel, chosen: kept, and applied at once where sharing runs - every relay let go of and the
     * PC's router left alone, or the relays taken up again and everybody told where this device now is.
     */
    static void onlyMine(Context where,boolean on) {
        synchronized(CHOOSING) {
            where.getSharedPreferences("node",Context.MODE_PRIVATE).edit().putString("travel",Relays.kept(on)).apply();
        }
        boolean was=onlyMine;
        onlyMine=on;
        com.eurobuddha.maxima.core.net.ReachabilityManager router;
        synchronized(Node.class){router=reach;}
        // A public door proved with a relay's help cannot be proved again without one, so it is taken back now
        // rather than left to go stale; with helpers again, the router is asked at the next round.
        if(router!=null&&on&&!was)router.onNetworkChanged();
        if(!on)askedAt=0;
        chosenNow(where);
    }

    /**
     * The Profile's Reconnect: the transport's upkeep now rather than at the next round - only where helpers
     * may be used, since all it does is find relays and tell contacts. How many relays it holds after.
     */
    static int reconnect(Context where) {
        MaximaNode up=node(where);
        if(up==null)throw new IllegalStateException("Could not start the Maxima node");
        if(!onlyMine)up.maintain(WAITING);
        return attached();
    }

    /** Whether a Maxima greeting comes back from this address. Blocking, up to ten seconds. */
    static boolean relayAnswers(String hostPort){return Relays.answers(hostPort,Relays.ASKING,VERSION);}

    /**
     * A relay kept, and used at once where sharing is running. Blocking: attaching takes as long as the relay
     * does. Says whether it is connected now.
     */
    static boolean addRelay(Context where,String hostPort) {
        synchronized(CHOOSING) {
            List<String> own=new java.util.ArrayList<>(ownRelays(where));
            if(!own.contains(hostPort))own.add(hostPort);
            where.getSharedPreferences("node",Context.MODE_PRIVATE).edit().putString("relays",Relays.toKept(own)).apply();
        }
        Relays.Choice chosen=chosenNow(where);
        return chosen!=null&&chosen.attached(hostPort);
    }

    /** A relay let go of: no longer kept, and no longer attached to unless it is also one of the public ones in use. */
    static void removeRelay(Context where,String hostPort) {
        synchronized(CHOOSING) {
            List<String> own=new java.util.ArrayList<>(ownRelays(where));
            own.remove(hostPort);
            where.getSharedPreferences("node",Context.MODE_PRIVATE).edit().putString("relays",Relays.toKept(own)).apply();
        }
        chosenNow(where);
    }

    /** The switch: the public relays used as well, or only the owner's. Applied at once where sharing runs. */
    static void publicRelays(Context where,boolean on) {
        synchronized(CHOOSING) {
            where.getSharedPreferences("node",Context.MODE_PRIVATE).edit().putString("public relays",on?"on":"off").apply();
        }
        chosenNow(where);
    }

    /**
     * What is kept, applied to the running node at once, and everybody told if that moved this device. Null
     * where it is not running: it is read as the node starts.
     */
    private static Relays.Choice chosenNow(Context where) {
        Relays.Choice chosen=choice;MaximaNode up;
        synchronized(Node.class){up=node;}
        if(chosen==null||up==null)return null;
        chosen.choose(ownRelays(where),publicRelays(where),onlyMine(where));
        if(chosen.apply(System.currentTimeMillis()))tellSoon(up);
        return chosen;
    }

    /** One of the owner's relays, in two words; empty while sharing is not running. */
    static String relayState(String hostPort) {
        Relays.Choice chosen=choice;
        return Relays.state(chosen!=null,chosen!=null&&chosen.attached(hostPort));
    }

    /** The line under the switch. */
    static String relaysLine(Context where) {
        Relays.Choice chosen=choice;
        return Relays.line(publicRelays(where),ownRelays(where).size(),chosen==null?0:chosen.connected(),A_PC);
    }

    /** How many relays this phone is attached to now, without starting anything. */
    static int attached() {
        MaximaNode up;
        synchronized(Node.class){up=node;}
        return up==null?0:up.myAddresses().size();
    }

    /**
     * Who this phone is, the same every time. The seed is twenty-four words' worth of entropy, made once
     * and kept: a new one each start would be a new identity each start, and everybody who had the old
     * address would be writing to nobody.
     */
    private static MaximaIdentity identity(Context where) {
        SharedPreferences kept=where.getSharedPreferences("node",Context.MODE_PRIVATE);
        String seed=kept.getString("seed","");
        if(!seed.isEmpty())return MaximaIdentity.fromSeed(new MiniData(seed));
        MaximaIdentity.Created made=MaximaIdentity.create();
        kept.edit().putString("seed",made.identity.seed().to0xString()).apply();
        return made.identity;
    }

    /** Renamed while running, so a name changed in the profile is the name the network sees from then on. */
    static synchronized void called(Context where,String said) {
        if(node!=null&&said!=null&&!said.trim().isEmpty())node.setName(said.trim());
    }

    /** What this pad is called: what its owner chose, or what the phone is called until they have. */
    static String nameHere(Context where) {
        String kept=where.getSharedPreferences("settings",Context.MODE_PRIVATE).getString("me","");
        return kept.trim().isEmpty()?name(where):kept.trim();
    }

    /**
     * What your own devices call this one (see {@link Persons}): what its owner chose, or until they have, what the
     * maker calls a phone and what Windows calls a PC. Nobody else ever sees it.
     */
    static String deviceHere(Context where) {
        String kept=where.getSharedPreferences("settings",Context.MODE_PRIVATE).getString("device","");
        if(!kept.trim().isEmpty())return kept.trim();
        String computer=A_PC?System.getenv("COMPUTERNAME"):null;
        return computer!=null&&!computer.trim().isEmpty()?computer.trim():name(where);
    }

    /** Whether your name was ever chosen, rather than the phone's own name standing in for it. */
    static boolean nameChosen(Context where) {
        return !where.getSharedPreferences("settings",Context.MODE_PRIVATE).getString("me","").trim().isEmpty();
    }

    /**
     * Your name, chosen here now. When is kept with it, because the same name is yours on every device you own and
     * the later choice stands wherever it was made. Telling the network is the caller's, off the notebook's worker.
     */
    static void chooseName(Context where,String said) {
        where.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().putString("me",said==null?"":said.trim())
            .putLong("meChanged",System.currentTimeMillis()).apply();
    }

    /** This device's name among your own devices, chosen here now; empty goes back to the maker's name. */
    static void chooseDevice(Context where,String said) {
        where.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().putString("device",said==null?"":said.trim())
            .putLong("deviceChanged",System.currentTimeMillis()).apply();
    }

    /** What the phone is called, for anybody who has to recognise it in a list. */
    private static String name(Context where) {
        String said=android.os.Build.MODEL;
        return said==null||said.trim().isEmpty()?"A phone":said.trim();
    }
}
