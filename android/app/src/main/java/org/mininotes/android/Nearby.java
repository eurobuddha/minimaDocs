// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import com.eurobuddha.maxima.core.MaximaNode;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/**
 * The local network, told where this device's door is, and heard from: see {@link Direct}.
 *
 * <p>Plain broadcast, to each local network's own broadcast address, rather than multicast: a phone has to
 * hold a lock to hear multicast at all, and the lock costs battery for as long as it is held. Nothing here
 * keeps the phone awake. The listener waits on a socket on a thread of its own, which the phone is free to
 * put to sleep; it hears while the process lives and the phone is awake, which is exactly when a note could
 * be sent to it directly anyway.
 *
 * <p>A device heard is told to the transport ({@link MaximaNode#noteLanPeer}), and let go again when it has
 * not been heard for {@link #QUIET}: a phone that has left the house stops announcing, and a note should not
 * spend the connection leash finding that out.
 *
 * <p>Holds no Android types, and logs nothing: what it heard, it keeps as counts.
 */
final class Nearby {
    /** Not heard for this long, and taken to be gone. Longer than three of the slowest rounds. */
    static final long QUIET=5L*60*1000;

    private final MaximaNode node;
    private final IntSupplier door;
    private final String host;
    private final int port;
    private volatile Set<String> paired=Collections.emptySet();
    private final Map<String,Long> heardAt=new ConcurrentHashMap<>();
    private volatile DatagramSocket socket;
    private volatile boolean closed;

    /**
     * @param host where to listen: every local network, or one address in a test
     * @param port where announcements are heard, and sent to; 0 in a test takes any free one
     */
    Nearby(MaximaNode node,IntSupplier door,String host,int port){this.node=node;this.door=door;this.host=host;this.port=port;}

    /**
     * Listening, on a thread of its own. False where the port could not be had - then this device still
     * says where it is, from a socket of the moment, and only its own hearing is missing.
     */
    boolean open() {
        try {
            DatagramSocket made=new DatagramSocket(null);
            made.setReuseAddress(true);made.setBroadcast(true);
            made.bind(new InetSocketAddress(host,port));
            socket=made;
        } catch(Exception taken){return false;}
        Thread listening=new Thread(this::listen,"mininotes-nearby");listening.setDaemon(true);listening.start();
        return true;
    }

    /** The port it hears on, or -1 where it does not. */
    int hearing(){DatagramSocket up=socket;return up==null||up.isClosed()?-1:up.getLocalPort();}

    void close(){closed=true;DatagramSocket up=socket;if(up!=null)up.close();}

    /** The devices it believes, by identity key. Said again each round, because pairing changes. */
    void pairedWith(Collection<String> keys) {
        Set<String> now=keys==null?Collections.emptySet():Set.copyOf(keys);
        paired=now;
        // A device refused a moment ago only for want of its key, heard at once now that it has one, rather than at
        // its next announcement a round later: answers wait for its door until then. Still only if it is fresh.
        for(String key:now) {
            Object[] was=unpaired.remove(key);
            if(was!=null)heard((byte[])was[0],((byte[])was[0]).length,(InetAddress)was[1]);
        }
    }

    /** The last announcement refused as not paired, by key, and where it came from: see {@link #pairedWith}. */
    private final Map<String,Object[]> unpaired=new ConcurrentHashMap<>();

    /** Who to tell, by identity key, when a paired device is heard that was not heard a moment ago. */
    private volatile java.util.function.Consumer<String> met;

    /**
     * Told of each device heard anew - for the first time since this device started, came back after going quiet,
     * or moved networks - and not of every announcement after. That is the moment to tell it where this device is:
     * see {@link Node#tell}.
     */
    void onMet(java.util.function.Consumer<String> then){met=then;}

    private void listen() {
        byte[] room=new byte[Direct.MOST+1];
        while(!closed) {
            DatagramSocket up=socket;
            if(up==null)return;
            try {
                DatagramPacket packet=new DatagramPacket(room,room.length);
                up.receive(packet);
                heard(packet.getData(),packet.getLength(),packet.getAddress());
            } catch(Exception notNow) {
                if(closed||up.isClosed())return;
                // A socket that failed once is given a moment rather than spun on.
                try{Thread.sleep(1000);}catch(InterruptedException stop){Thread.currentThread().interrupt();return;}
            }
        }
    }

    /** One packet off the network. Whether it was a paired device saying where it is. */
    boolean heard(byte[] packet,int length,InetAddress from) {
        if(from==null)return false;
        long now=System.currentTimeMillis();
        Direct.Heard said=Direct.heard(packet,length,from.getAddress(),node.identity().publicKeyHex(),paired,now,(why,who)->{
            refused(why,who);
            if(why!=Direct.Refused.NOT_PAIRED)return;
            if(unpaired.size()>=16&&!unpaired.containsKey(who))unpaired.clear();
            unpaired.put(who,new Object[]{java.util.Arrays.copyOf(packet,length),from});
        });
        if(said==null)return false;
        if(Direct.shutLately(said.key(),said.where(),now)){refused(Direct.Refused.SHUT,said.key());return false;}
        boolean anew=heardAt.put(said.key(),now)==null;
        node.noteLanPeer(said.key(),said.where());
        java.util.function.Consumer<String> then=met;
        if(anew&&then!=null)try{then.accept(said.key());}catch(RuntimeException notNow){/* heard all the same */}
        return true;
    }

    /**
     * This device's door, said on every local network it is on. The address named in each is that network's
     * own, since that is the one the packet will be seen to come from.
     *
     * @return how many networks it was said on
     */
    int announce() {
        int at=door.getAsInt();
        if(at<=0)return 0;
        int said=0;
        long now=System.currentTimeMillis();
        // Each network is told from a socket of its own, bound to this device's address on it. The listening
        // socket will not do: on Windows, Java opens it for IPv4 and IPv6 at once, and an IPv4 broadcast sent
        // from one of those leaves nowhere - no error, nothing on the wire. Seen on the owner's PC, which heard
        // the phone announce itself and was never heard back. Bound this way it is plain IPv4, and the packet
        // leaves from the very address the announcement names, which is what the other end checks.
        for(InterfaceAddress local:local()) {
            try(DatagramSocket using=new DatagramSocket(new InetSocketAddress(local.getAddress(),0))) {
                using.setBroadcast(true);
                byte[] one=Direct.announce(node.identity().keyPair(),local.getAddress().getAddress(),at,now);
                using.send(new DatagramPacket(one,one.length,local.getBroadcast(),port>0?port:Direct.PORT));
                said++;
            } catch(Exception thisOne){/* the next network */}
        }
        return said;
    }

    /** Devices not heard from lately, let go of. How many. */
    int forgetQuiet() {
        long now=System.currentTimeMillis();int gone=0;
        for(Map.Entry<String,Long> one:heardAt.entrySet())
            if(now-one.getValue()>QUIET&&heardAt.remove(one.getKey(),one.getValue())){node.forgetLanPeer(one.getKey());gone++;}
        return gone;
    }

    /** Every device heard, let go of: this device is on another network now. */
    void forgetAll(){for(String key:new ArrayList<>(heardAt.keySet())){heardAt.remove(key);node.forgetLanPeer(key);}}

    /**
     * Announcements heard and not believed, by why. A device that hears another and is never heard back looks, from
     * both ends, like a network that loses packets; these say which of the checks it was.
     */
    private final int[] notBelieved=new int[Direct.Refused.values().length];
    /** And whose, as {@link Direct#tag} writes a key: a few for each why, to match with a line that names one. */
    @SuppressWarnings("unchecked")
    private final Set<String>[] whose=new Set[Direct.Refused.values().length];

    private synchronized void refused(Direct.Refused why,String who) {
        notBelieved[why.ordinal()]++;
        if(whose[why.ordinal()]==null)whose[why.ordinal()]=new java.util.TreeSet<>();
        if(whose[why.ordinal()].size()<4)whose[why.ordinal()].add(Direct.tag(who));
    }

    /**
     * The counts and whose, as "3 not paired (1a2b3c), 1 stale (4d5e6f)", or empty where every one was believed; let go
     * of where {@code reset}.
     */
    synchronized String refusals(boolean reset) {
        StringBuilder out=new StringBuilder();
        for(Direct.Refused one:Direct.Refused.values()) {
            int n=notBelieved[one.ordinal()];Set<String> tags=whose[one.ordinal()];
            if(n>0)out.append(out.length()==0?"":", ").append(n).append(' ').append(one.said)
                .append(tags==null||tags.isEmpty()?"":" ("+String.join(", ",tags)+")");
            if(reset){notBelieved[one.ordinal()]=0;whose[one.ordinal()]=null;}
        }
        return out.toString();
    }

    /** How many devices it holds a local address for now. */
    int near(){return heardAt.size();}

    /**
     * The local networks this device is on, as one string that changes when they do. Never logged: it is
     * compared, and that is all.
     */
    static String here() {
        List<String> all=new ArrayList<>();
        for(InterfaceAddress one:local())all.add(one.getAddress().getHostAddress()+"/"+one.getNetworkPrefixLength());
        Collections.sort(all);
        return String.join(",",all);
    }

    /**
     * This device's own address on each local network, the likeliest home network first: a pairing code carries
     * the first, and a PC often has a virtual network or two besides the one its owner's phones are on, which
     * are usually in the 172 range.
     */
    static List<String> addressesHere() {
        List<String> out=new ArrayList<>();
        for(InterfaceAddress one:local())out.add(one.getAddress().getHostAddress());
        out.sort(java.util.Comparator.comparingInt(ip->ip.startsWith("192.168.")?0:ip.startsWith("10.")?1:2));
        return out;
    }

    /**
     * Every local network with a broadcast address: Wi-Fi and wired, not the device's own loopback, and not a
     * mobile-data or VPN link, which are point to point and have nobody else on them to hear.
     */
    private static List<InterfaceAddress> local() {
        List<InterfaceAddress> out=new ArrayList<>();
        try {
            java.util.Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();
            if(all==null)return out;
            for(NetworkInterface one:Collections.list(all)) {
                try{if(!one.isUp()||one.isLoopback()||one.isPointToPoint())continue;}catch(Exception gone){continue;}
                for(InterfaceAddress address:one.getInterfaceAddresses())
                    if(address!=null&&address.getAddress() instanceof Inet4Address&&address.getBroadcast()!=null)out.add(address);
            }
        } catch(Exception none){/* no network, nobody to tell */}
        return out;
    }
}
