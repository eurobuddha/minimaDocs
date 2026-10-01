// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import com.eurobuddha.maxima.core.MaximaNode;
import com.eurobuddha.maxima.core.net.Probe;
import com.eurobuddha.maxima.core.session.Bootstrap;
import com.eurobuddha.maxima.core.session.HostPool;
import com.eurobuddha.maxima.core.session.PeerDiscovery;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The owner's own relays, and whether the public ones are used as well. See docs/DIRECT.md, "Your own relays".
 *
 * <p>A relay is somebody's machine that holds a connection open for a device nobody can reach. The ones
 * Mininotes starts from are the transport's public list, one operator's choice; anybody can run one, and an
 * owner who does - or who trusts a friend's - can put it here. Their relays are tried first. With the public
 * relays switched off, this device attaches to theirs and nothing else, and what cannot go directly or
 * through them waits.
 *
 * <p>Nothing in the transport is changed for this. It learns relays three ways - the list it is started
 * with, the peers each relay names when it greets (gossip), and the peers it saved from earlier runs - and
 * all three reach the pool of relays it attaches from through one listener on its discovery, which is
 * public. {@link Choice} takes that listener over, so a relay that is not the owner's is never even a
 * candidate while the public relays are off; it stops discovery itself then, so nothing is dialled to check
 * a public relay either; and it hands the pool its relays as ordinary candidates rather than as the
 * transport's "floor", which cannot be taken back once given.
 *
 * <p>An address is kept only if a Maxima greeting came back from it when it was added. An address that never
 * answered is almost always mistyped, and one kept anyway would sit in the list looking like a relay that
 * works. So there is one path: it answers, it is kept; it does not, nothing changes and it is said why.
 *
 * <p>The parsing, the saved form and the rules of choosing hold no Android types and are unit tested; so
 * is {@link Choice}, against a real node on this machine.
 */
final class Relays {
    private Relays(){}

    /** More than anybody runs, and few enough that trying the ones that do not answer stays cheap. */
    static final int MOST=8;
    /** An own relay that did not answer is left this long before it is tried again, so a dead one costs a round in five minutes, not every round. */
    static final long REST=5L*60*1000;
    /** How long a relay has to answer when it is added: a relay greets as soon as it is connected to. */
    static final int ASKING=10000;
    /** The relays the transport ships with. */
    static final List<String> PUBLIC=Bootstrap.RELAYS;

    /** The words on the switch, the same on both apps. */
    static final String SWITCH="Use the public relays";

    // ---- how notes travel: see docs/DIRECT.md, "Two ways notes travel" --------------------------------------

    /** The two ways, the same words on both apps. */
    static final String ONLY_MINE="Only between my devices", HELPERS="Also through helpers when needed";
    /** The choice as kept: only the word "mine" keeps notes between the owner's devices, so a device that never saw this uses helpers. */
    static boolean onlyMine(String kept){return "mine".equals(kept);}
    static String kept(boolean onlyMine){return onlyMine?"mine":"helpers";}

    /**
     * The line under each way. What the first one cannot do is said where it is chosen, not found out later:
     * a phone away from home can be reached by nothing, so without a helper it waits.
     */
    static String travelLine(boolean onlyMine) {
        return onlyMine?"No relay, no server. Phones away from home cannot reach each other directly, so notes sync on the same "
            +"Wi-Fi or through your PC when it can be reached; otherwise they wait until the devices meet. Pairing needs both "
            +"devices on the same Wi-Fi."
            :"Straight to your devices first. When that cannot happen, relays carry what is sealed: yours first, then the "
            +"public ones if they are on.";
    }

    // ---- what is typed --------------------------------------------------------------------------------------

    /** The address as it is kept - host in lower case, IPv6 in brackets, then the port - or null if it is not one. */
    static String parse(String typed){String[] read=read(typed);return read[1]==null?read[0]:null;}

    /** What is wrong with what was typed, in a plain sentence, or null when it is an address that can be tried. */
    static String problem(String typed){return read(typed)[1];}

    private static final String LIKE="like relay.example.org:9001";

    /** {address, null} or {null, why not}. */
    private static String[] read(String typed) {
        String s=typed==null?"":typed.trim();
        // A Parlons relay's shared text, with one relay in it, is the same address with a name in front.
        if(s.regionMatches(true,0,"parlons-relay:",0,14)){s=s.substring(14);while(s.startsWith("/"))s=s.substring(1);}
        if(s.isEmpty())return no("Type the relay's address and port, "+LIKE+".");
        if(s.contains(","))return no("One relay at a time.");
        if(!s.matches("\\S+"))return no("An address has no spaces in it.");
        String host,port;boolean six;
        if(s.startsWith("[")) {
            int close=s.indexOf(']');
            if(close<0)return no("The IPv6 address is missing its closing bracket.");
            host=s.substring(1,close).toLowerCase(Locale.ROOT);
            if(close+1>=s.length()||s.charAt(close+1)!=':')return no("Add the port after the bracket, like [2001:db8::1]:9001.");
            port=s.substring(close+2);six=true;
            if(!host.matches("[0-9a-f:.]+")||!host.contains(":")||!isIp(host))return no("That is not an IPv6 address.");
        } else {
            int colon=s.lastIndexOf(':');
            if(colon<0)return no("Add the port after a colon, "+LIKE+".");
            if(s.indexOf(':')!=colon)return no("Put an IPv6 address in square brackets, like [2001:db8::1]:9001.");
            host=s.substring(0,colon).toLowerCase(Locale.ROOT);port=s.substring(colon+1);six=false;
            if(host.isEmpty())return no("Type the relay's address before the colon, "+LIKE+".");
            if(!four(host)&&!name(host))return no("That is not an address, "+LIKE+".");
        }
        int number;
        try{number=port.matches("[0-9]{1,5}")?Integer.parseInt(port):-1;}catch(NumberFormatException none){number=-1;}
        if(number<1||number>65535)return no("The port is a number from 1 to 65535.");
        if(home(host,six))return no("That is an address on a home network. A relay has to be reachable from anywhere.");
        // The transport never hands out an address it takes for a private one, and its test is by the first
        // number alone - so a relay at such an address would be attached to and never named to anybody.
        if(!six&&four(host)&&MaximaNode.isInternalHost(host))
            return no("Maxima never hands out addresses that start "+host.substring(0,host.indexOf('.')+1)+", so nothing could reach you through that relay.");
        return new String[]{six?"["+host+"]:"+number:host+":"+number,null};
    }
    private static String[] no(String why){return new String[]{null,why};}

    private static boolean four(String host) {
        if(!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))return false;
        for(String part:host.split("\\."))if(Integer.parseInt(part)>255)return false;
        return true;
    }
    /** A name: dotted labels of letters, digits and hyphens, and not all numbers at the end, so 1.2.3 is not one. */
    private static boolean name(String host) {
        if(host.length()>253||host.startsWith(".")||host.endsWith(".")||host.contains(".."))return false;
        String[] labels=host.split("\\.");
        for(String label:labels)if(!label.matches("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?"))return false;
        return !labels[labels.length-1].matches("[0-9]+");
    }
    /** A literal address only: a name is never looked up here, since a check must not wait on the network. */
    private static boolean isIp(String literal) {
        try{return InetAddress.getByName("["+literal+"]") instanceof Inet6Address;}catch(Exception not){return false;}
    }
    /** An address, or a name, that only means something on the network it is said on. */
    private static boolean home(String host,boolean six) {
        if(six) {
            try {
                InetAddress at=InetAddress.getByName("["+host+"]");
                byte first=at.getAddress()[0];
                return at.isLoopbackAddress()||at.isAnyLocalAddress()||at.isLinkLocalAddress()||at.isSiteLocalAddress()||(first&0xfe)==0xfc;
            } catch(Exception not){return true;}
        }
        if(four(host)) {
            String[] p=host.split("\\.");int a=Integer.parseInt(p[0]),b=Integer.parseInt(p[1]);
            return a==0||a==10||a==127||(a==169&&b==254)||(a==172&&b>=16&&b<=31)||(a==192&&b==168)||(a==100&&b>=64&&b<=127);
        }
        return !host.contains(".")||host.equals("localhost")||host.endsWith(".local")||host.endsWith(".lan")
            ||host.endsWith(".home")||host.endsWith(".internal")||host.endsWith(".home.arpa");
    }

    // ---- how they are kept ----------------------------------------------------------------------------------

    /** One address a line. What cannot be read back is dropped rather than refused: a setting should never stop a device starting. */
    static List<String> fromKept(String kept) {
        LinkedHashSet<String> out=new LinkedHashSet<>();
        if(kept!=null)for(String line:kept.split("\\s+")) {
            String one=parse(line);
            if(one!=null&&out.size()<MOST)out.add(one);
        }
        return new ArrayList<>(out);
    }
    static String toKept(Collection<String> own){return String.join("\n",own);}
    /** The switch as kept: only the word "off" turns the public relays off, so a device that never saw this is on. */
    static boolean publicOn(String kept){return !"off".equals(kept);}

    // ---- the rules of choosing ------------------------------------------------------------------------------

    /** Where to start from: the owner's first, then - when they are on - the public ones. */
    static List<String> seeds(List<String> own,boolean publicOn,List<String> builtIn) {
        LinkedHashSet<String> out=new LinkedHashSet<>(own);
        if(publicOn)out.addAll(builtIn);
        return new ArrayList<>(out);
    }
    /** Whether a relay somebody named may be tried: any while the public relays are on, only the owner's while they are off. */
    static boolean mayUse(String hostPort,List<String> own,boolean publicOn){return publicOn||own.contains(hostPort);}
    /** Whether a relay stays a candidate when discovery lets it go: the owner's always, the shipped ones while they are on. */
    static boolean keep(String hostPort,List<String> own,boolean publicOn,List<String> builtIn) {
        return own.contains(hostPort)||publicOn&&builtIn.contains(hostPort);
    }
    /** What to let go of now: with the public relays off, everything that is not the owner's; with them on, nothing. */
    static List<String> letGo(Collection<String> known,List<String> own,boolean publicOn) {
        List<String> out=new ArrayList<>();
        if(!publicOn)for(String one:known)if(!own.contains(one))out.add(one);
        return out;
    }
    /**
     * Which relay makes room once one of the owner's has come in over the number held: the lowest in the
     * pool's own order that is not the owner's, or none when all of them are.
     */
    static String makeRoom(List<String> byScore,List<String> own,int target) {
        if(byScore.size()<=target)return null;
        for(int i=byScore.size()-1;i>=0;i--)if(!own.contains(byScore.get(i)))return byScore.get(i);
        return null;
    }
    /** Addresses to hand out, the ones through the owner's relays first; otherwise in the order they came. */
    static List<String> ownFirst(List<String> addresses,List<String> own) {
        List<String> first=new ArrayList<>(),then=new ArrayList<>();
        for(String one:addresses){int at=one.lastIndexOf('@');(at>=0&&own.contains(one.substring(at+1))?first:then).add(one);}
        first.addAll(then);
        return first;
    }

    // ---- what is said ---------------------------------------------------------------------------------------

    /** One relay's state, in two words: whether it is attached now. Empty while sharing is not running at all. */
    static String state(boolean running,boolean attached){return !running?"":attached?"Connected":"Not answering";}

    /**
     * The line under the switch. Off with none of the owner's relays answering is the one that matters: then
     * nothing goes through any relay, and it says what does happen rather than what does not.
     */
    static String line(boolean publicOn,int own,int connected,boolean pc) {
        if(publicOn)return own==0?"The relays shipped with Mininotes carry notes when a device cannot be reached directly."
            :"Your relays are used first, then the ones shipped with Mininotes.";
        if(connected>0)return "Only your relays are used.";
        return pc?"Notes go only directly - to your devices at home, and to those that reach this PC - and wait otherwise."
            :"Notes go only directly - on your home network, or through your PC - and wait otherwise.";
    }

    /** Whether a Maxima greeting comes back from the address: what "it answered" means when one is added. Blocking. */
    static boolean answers(String hostPort,int leash,String version) {
        int colon=hostPort.lastIndexOf(':');
        if(colon<=0)return false;
        String host=hostPort.substring(0,colon);
        try{return Probe.dialGreeting(host,Integer.parseInt(hostPort.substring(colon+1)),leash,leash,version)!=null;}
        catch(RuntimeException refused){return false;}
    }

    // ---- the node kept to the choice ------------------------------------------------------------------------

    /**
     * One node's relays kept to the owner's choice. Made before the node's store is set, so the peers saved
     * from earlier runs arrive through the listener here like any others.
     */
    static final class Choice {
        private final MaximaNode node;
        private final int target, leash;
        private final List<String> builtIn;
        private volatile List<String> own;
        private volatile boolean publicOn;
        /** Only between the owner's devices: no relay at all, the owner's included, and none ever added. */
        private volatile boolean none;
        private volatile boolean gossipStopped;
        /** When each of the owner's relays last failed to take this device. */
        private final Map<String,Long> failed=new ConcurrentHashMap<>();

        Choice(MaximaNode node,List<String> own,boolean publicOn,int target,int leash,List<String> builtIn) {
            this(node,own,publicOn,false,target,leash,builtIn);
        }

        /** @param none notes only between the owner's devices: no relay is a candidate, whatever else is kept */
        Choice(MaximaNode node,List<String> own,boolean publicOn,boolean none,int target,int leash,List<String> builtIn) {
            this.node=node;this.own=fixed(own);this.publicOn=publicOn;this.none=none;this.target=target;this.leash=leash;this.builtIn=fixed(builtIn);
            // The transport's own listener adds every relay discovery verifies. This one adds only what may be
            // used, and lets go of nothing that must be kept - which the transport's "floor" did before.
            node.discovery().setListener(new PeerDiscovery.Listener() {
                @Override public void onVerified(String hostPort){if(!Choice.this.none&&mayUse(hostPort,Choice.this.own,Choice.this.publicOn))node.pool().addCandidate(hostPort);}
                @Override public void onRemoved(String hostPort){if(Choice.this.none||!keep(hostPort,Choice.this.own,Choice.this.publicOn,Choice.this.builtIn))node.pool().removeCandidate(hostPort);}
            });
            if(!publicOn||none)stopGossip();
        }

        private static List<String> fixed(Collection<String> from){return java.util.Collections.unmodifiableList(new ArrayList<>(from));}

        List<String> own(){return own;}
        boolean publicOn(){return publicOn;}
        boolean none(){return none;}

        /** What the owner chose, from now on. Applied at the next {@link #apply}. */
        void choose(List<String> ownNow,boolean publicNow){choose(ownNow,publicNow,none);}

        /** The same, and whether notes go only between the owner's devices. Their relays are kept either way, for coming back to. */
        void choose(List<String> ownNow,boolean publicNow,boolean noneNow) {
            List<String> was=own;
            own=fixed(ownNow);publicOn=publicNow;none=noneNow;
            for(String gone:was)if(!own.contains(gone)){failed.remove(gone);if(!keep(gone,own,publicNow,builtIn))drop(gone);}
            if(!publicNow||noneNow)stopGossip();
        }

        /**
         * Discovery stopped, once: what it would do while the public relays are off is dial every relay it has
         * heard of to check it, and adopt the ones that answer. It cannot be started again in this process, so
         * relays learned from others come back when Mininotes next opens; the shipped ones come back at once.
         */
        private void stopGossip() {
            if(gossipStopped)return;
            gossipStopped=true;
            try{node.discovery().stop();}catch(RuntimeException saved){/* it has stopped; only its saving failed */}
        }

        private void drop(String hostPort){node.pool().detach(hostPort);node.pool().removeCandidate(hostPort);}

        /**
         * The pool kept to the choice: the owner's relays and - while they are on - the public ones as
         * candidates, everything else let go of while they are off, and the owner's relays attached before any
         * other, one making room by pushing out the lowest of the rest. Blocking: an attach takes as long as the
         * relay does.
         *
         * @return whether the relays this device is attached to changed, so contacts should be told
         */
        boolean apply(long now) {
            HostPool pool=node.pool();
            List<String> mine=own;boolean on=publicOn;
            Set<String> before=new HashSet<>(pool.activeHosts());
            if(none) {
                // Nothing kept, nothing attached: every relay this device knows is let go of, the owner's too, and
                // none is tried. They stay in the settings for when the owner comes back to helpers.
                List<String> known=new ArrayList<>();
                for(HostPool.HostRecord one:pool.knownByScore())known.add(one.hostPort);
                known.addAll(pool.activeHosts());
                for(String one:new LinkedHashSet<>(known))drop(one);
                return !before.equals(new HashSet<>(pool.activeHosts()));
            }
            for(String one:mine)pool.addCandidate(one);
            if(on) {
                for(String one:builtIn)pool.addCandidate(one);
                if(!gossipStopped)for(String one:node.discovery().verified())pool.addCandidate(one);
            } else {
                List<String> known=new ArrayList<>();
                for(HostPool.HostRecord one:pool.knownByScore())known.add(one.hostPort);
                known.addAll(pool.activeHosts());
                for(String one:letGo(new LinkedHashSet<>(known),mine,false))drop(one);
            }
            int held=0;
            for(String one:mine)if(pool.activeHosts().contains(one))held++;
            for(String one:mine) {
                if(held>=target)break;
                if(pool.activeHosts().contains(one)){failed.remove(one);continue;}
                Long when=failed.get(one);
                if(when!=null&&now-when<REST)continue;
                if(pool.attachOne(one,leash)) {
                    failed.remove(one);held++;
                    String room=makeRoom(pool.activeHostsByScore(),mine,target);
                    if(room!=null)pool.detach(room);
                } else failed.put(one,now);
            }
            return !before.equals(new HashSet<>(pool.activeHosts()));
        }

        /** Whether one relay is attached now. */
        boolean attached(String hostPort){return node.pool().activeHosts().contains(hostPort);}
        int connected(){int n=0;for(String one:own)if(attached(one))n++;return n;}
    }
}
