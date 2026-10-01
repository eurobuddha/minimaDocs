// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Who receives a page. Sharing is set at four levels — everything, one collection, one book, one page — and
 * a page is reached by every rule that covers it, wherever that rule was set. Setting a rule high up is the
 * point: share a collection once and the books you add to it later are already shared.
 *
 * <p>An address named as another device of yours is two-way, because writing on your tablet has to come back.
 * An address named as someone else is one-way. If the same address is reached at two levels with different
 * roles, two-way wins: it is still your device, whichever level named it.
 *
 * <p>Holds no Android types, so what a rule reaches — and what it must never reach — is unit tested.
 */
final class Sharing {
    /**
     * The level a rule was set at. COLLECTION, BOOK and PAGE are the three levels a 0.1 device knows, and rows that
     * came from one keep them; THING is a collection at any depth, for rules made since collections nest (see
     * docs/HOME.md, decision 12). Added last: a scope travels and is kept by its name.
     */
    enum Scope { LIBRARY, COLLECTION, BOOK, PAGE, THING }
    /** The target of a library rule: there is only one library, so it needs no id. */
    static final String EVERYTHING="*";

    /**
     * What a share lets the other end do.
     *
     * <p>Three, because two was not enough to say the thing people actually mean. Reading is a copy that
     * keeps itself up to date. Writing is the same notebook in two places. <b>Admin</b> is the one that
     * makes a shared thing a shared thing rather than a broadcast: whoever has it may hand it on, and the
     * list of who has it is kept by everybody rather than by whoever happened to start it.
     *
     * <p>Ordered, so "at least this much" is a comparison and not a table.
     */
    enum Level {
        GONE, READ, WRITE, ADMIN;
        boolean writes(){return ordinal()>=WRITE.ordinal();}
        boolean shares(){return this==ADMIN;}
        /** What travels, and what comes back — a number, so an older build reading a newer one is not lost. */
        int said(){return ordinal();}
        static Level of(int said){
            Level[] all=values();
            return said<0?GONE:said>=all.length?ADMIN:all[said];
        }
        /** On the chip, where there is room for two words at most. */
        String words() {
            switch(this) {
                case ADMIN: return "Admin";
                case WRITE: return "Can write";
                case READ: return "Can read";
                default: return "Not shared";
            }
        }

        /** Said in full, for anybody who cannot see the chip. "admin this" is not a sentence. */
        String saying() {
            switch(this) {
                case ADMIN: return "is an admin of this, and can hand it on";
                case WRITE: return "reads and writes this";
                case READ: return "reads this";
                default: return "does not get this";
            }
        }

        /** What the role lets somebody do, in one line under its name wherever a role is chosen or shown. */
        String does() {
            switch(this) {
                case ADMIN: return "Can read, write and share it: add people who can write or read, change their role or remove them. Not the owner or other admins.";
                case WRITE: return "Can read it and change it.";
                case READ: return "Can see it and get changes.";
                default: return "Does not get it.";
            }
        }
    }

    /**
     * The one who made the thing. Not a level anybody is given - there is one owner, and it is whoever the thing
     * came from, or this device where it came from nobody - so it is said beside the levels rather than among them.
     */
    static final String OWNER="Owner";
    static final String OWNER_DOES="Made it. Can do everything, including changing anyone's role, admins included, and removing anyone.";

    /**
     * What somebody may give others, most first: the owner Admin, Can write or Can read; an admin Can write or Can
     * read; anybody else nothing.
     *
     * @param mine what this device may do with the thing, where it is not the owner; null where nothing says
     */
    static java.util.List<Level> grantable(boolean owner,Level mine) {
        if(owner)return java.util.List.of(Level.ADMIN,Level.WRITE,Level.READ);
        if(mine==Level.ADMIN)return java.util.List.of(Level.WRITE,Level.READ);
        return java.util.List.of();
    }

    /**
     * Whether somebody may change a member's role or take them off: the owner anybody but themselves; an admin
     * anybody who is neither the owner nor an admin; anybody else nobody.
     *
     * @param theirs what the member may do now
     * @param theyOwn the member is whoever the thing is from
     */
    static boolean mayChange(boolean owner,Level mine,Level theirs,boolean theyOwn) {
        if(theyOwn)return false;
        if(owner)return true;
        return mine==Level.ADMIN&&theirs!=Level.ADMIN;
    }

    /**
     * Whether a decision about a member, arriving in a list, may stand here. From the owner, any. From anybody
     * else - an admin, or a device passing on a list it was sent - never one about the owner, never one making
     * somebody an admin, and never one about somebody who is an admin here: what the owner decides about admins
     * reaches here in the owner's own lists. A list from a build that let an admin do more is held to the same.
     *
     * @param before what the member may do here now, or null where nothing is written down about them
     */
    static boolean mayCarry(boolean fromOwner,boolean aboutOwner,Level before,Level after) {
        if(fromOwner)return true;
        if(aboutOwner)return false;
        return after!=Level.ADMIN&&before!=Level.ADMIN;
    }

    static final class Rule {
        /**
         * {@code mine} is what this share lets the other end do: false is read — they receive what you
         * write and cannot change it — and true is read and write, where what they write comes back and is
         * merged into yours. It is the one thing a share decides beyond who, and it is decided per share:
         * the same person can have a book of yours to read and another to work in.
         */
        final Scope scope; final String target,address; final boolean mine;
        /** What they may do. {@code mine} is this, read as "may write", and kept because much reads it. */
        final Level level;
        /**
         * When this was last decided, by the phone that decided it.
         *
         * <p>Two people with admin can change the same person at the same time, on two phones that cannot
         * see each other. Whichever change was made later is the one that stands — not whichever arrived
         * later, which would depend on the weather. It is the only rule that settles by itself.
         */
        final long changed;
        /** The device this is about, by the key it signs with: an address moves, a key does not. */
        final String key;
        Rule(Scope scope,String target,String address,boolean mine) {
            this(scope,target,address,mine?Level.WRITE:Level.READ,0L,"");
        }
        Rule(Scope scope,String target,String address,Level level,long changed,String key) {
            this.scope=Objects.requireNonNull(scope);this.target=Objects.requireNonNull(target);
            this.address=Objects.requireNonNull(address);
            this.level=level==null?Level.READ:level;this.mine=this.level.writes();
            this.changed=changed;this.key=key==null?"":key;
        }
        @Override public boolean equals(Object other) {
            if(!(other instanceof Rule))return false;
            Rule r=(Rule)other;return scope==r.scope&&target.equals(r.target)&&address.equals(r.address);
        }
        @Override public int hashCode(){return Objects.hash(scope,target,address);}
        @Override public String toString(){return scope+" "+target+" -> "+address+(mine?" (my device)":"");}
    }

    /**
     * Every address this page reaches, in the order the rules were given, mapped to whether that address is
     * another device of yours. An address reached at several levels appears once.
     */
    static Map<String,Boolean> audience(Collection<Rule> rules,String collection,String book,String page) {
        return audience(rules,path(collection,book,page));
    }

    /**
     * The same, for a thing anywhere in the tree: {@code path} is every collection above it from Home down and then
     * the thing itself (see {@link Things#path}).
     */
    static Map<String,Boolean> audience(Collection<Rule> rules,java.util.List<String> path) {
        Map<String,Boolean> reached=new LinkedHashMap<>();
        for(Rule rule:rules) {
            if(!covers(rule,path))continue;
            Boolean mine=reached.get(rule.address);
            reached.put(rule.address,rule.mine||(mine!=null&&mine));
        }
        return reached;
    }

    /**
     * What one device may do with a page, by every rule that reaches it: the rule that says the most, or
     * null where none names them at all.
     *
     * <p>Unlike {@link #audience}, this is given the rows that say somebody is off a thing, and answers
     * with one: taken off is not the same as never given, and a phone hearing from somebody taken off has
     * something to tell them. The device is named by any address it has been at, or by the key it signs
     * with, because an address moves and a rule written last month may still hold the old one.
     */
    static Rule standing(Collection<Rule> rules,String collection,String book,String page,
                         Collection<String> addresses,String key) {
        return standing(rules,path(collection,book,page),addresses,key);
    }

    /** The same, along a path: see {@link #audience(Collection,java.util.List)}. */
    static Rule standing(Collection<Rule> rules,java.util.List<String> path,Collection<String> addresses,String key) {
        Rule most=null;
        for(Rule rule:rules) {
            if(!covers(rule,path))continue;
            boolean them=(addresses!=null&&addresses.contains(rule.address))
                ||(key!=null&&!key.isEmpty()&&key.equals(rule.key));
            if(!them)continue;
            if(most==null||rule.level.ordinal()>most.level.ordinal())most=rule;
        }
        return most;
    }

    /** True when a rule set at its level applies to this page. A rule for another target must never apply. */
    static boolean covers(Rule rule,String collection,String book,String page) {
        return covers(rule,path(collection,book,page));
    }

    /**
     * True when a rule applies to the thing at the end of {@code path}: the library rule always, any other when its
     * target is the thing or a collection above it. Which level the rule names does not matter - every thing has an
     * id of its own, whatever level it was made at - so a rule set on a book, which is a collection now, reaches
     * everything inside it however deep, as a rule on a collection always did.
     */
    static boolean covers(Rule rule,java.util.List<String> path) {
        if(rule.scope==Scope.LIBRARY)return true;
        return path!=null&&!rule.target.isEmpty()&&path.contains(rule.target);
    }

    /** The three old levels as a path: those of them that are named, top down. */
    static java.util.List<String> path(String collection,String book,String page) {
        java.util.List<String> all=new java.util.ArrayList<>(3);
        for(String one:new String[]{collection,book,page})if(one!=null&&!one.isEmpty())all.add(one);
        return all;
    }

    /** What moving something would do to its audience: who starts receiving it, and who stops. */
    static final class Change {
        final Map<String,Boolean> gained,lost;
        Change(Map<String,Boolean> gained,Map<String,Boolean> lost){this.gained=gained;this.lost=lost;}
        boolean any(){return !gained.isEmpty()||!lost.isEmpty();}
    }

    /**
     * The audience a page or book would gain and lose by moving. Dragging something into a book its owner
     * shares widely is a disclosure, and dragging it out is a withdrawal; both have to be said out loud
     * before the move, not discovered afterwards. Rules set on the moved thing itself follow it and so
     * appear in neither list.
     *
     * <p>Pass the page id when moving a page, or "" when moving a book, whose own pages keep their own rules.
     */
    static Change moving(Collection<Rule> rules,String fromCollection,String fromBook,
                         String toCollection,String toBook,String page) {
        return moving(rules,path(fromCollection,fromBook,page),path(toCollection,toBook,page));
    }

    /**
     * The same for anything moving anywhere: {@code from} and {@code to} are its path where it is and where it would
     * be, each ending with the thing itself - so rules set on it, or on anything inside it, follow it.
     */
    static Change moving(Collection<Rule> rules,java.util.List<String> from,java.util.List<String> to) {
        Map<String,Boolean> before=audience(rules,from);
        Map<String,Boolean> after=audience(rules,to);
        Map<String,Boolean> gained=new LinkedHashMap<>(),lost=new LinkedHashMap<>();
        for(Map.Entry<String,Boolean> reached:after.entrySet())
            if(!before.containsKey(reached.getKey()))gained.put(reached.getKey(),reached.getValue());
        for(Map.Entry<String,Boolean> reached:before.entrySet())
            if(!after.containsKey(reached.getKey()))lost.put(reached.getKey(),reached.getValue());
        return new Change(gained,lost);
    }

    /**
     * Where a thing stands: on this device alone, going to your own devices, going to somebody else, or
     * having come from somebody else. It is said on the thing itself rather than kept in a list somewhere,
     * because the one moment it matters is the moment you are looking at the thing.
     *
     * <p>Reaching somebody else outranks reaching your own devices: of the two, that is the one worth
     * seeing at a glance, and the mark's own description says what the whole audience is.
     */
    enum State { HERE, DEVICES, OTHERS, THEIRS }

    /** {@code theirs} is a thing that arrived from another device rather than being written here. */
    static State state(Map<String,Boolean> audience,boolean theirs) {
        if(theirs)return State.THEIRS;
        if(audience==null||audience.isEmpty())return State.HERE;
        for(Boolean mine:audience.values())if(!Boolean.TRUE.equals(mine))return State.OTHERS;
        return State.DEVICES;
    }

    /** One mark per state, in the same vocabulary as the rest: an arrow out, an arrow in, both ways, or none. */
    static String mark(State state) {
        switch(state) {
            case DEVICES: return "⇄";
            case OTHERS: return "↗";
            case THEIRS: return "↙";
            default: return "▫";
        }
    }

    /**
     * Where a thing stands and whether it is up to date, in words rather than in marks. A symbol has to be
     * learnt and then remembered; "Shared · 2 waiting" is read once and understood. Nothing is said about a
     * thing that is only on this phone: that is what everything is until it is shared.
     *
     * @param owed how many notes inside it an address has not been given yet
     * @param brief tiles are narrow, so they take the first half and leave the counting to the cards
     */
    static String says(State state,int owed,boolean brief) {
        String where;
        switch(state) {
            case DEVICES: where="My devices"; break;
            case OTHERS: where="Shared"; break;
            // "Another device" rather than "somebody else": what arrives may be from your own tablet as
            // easily as from a friend, and calling both of them somebody else was wrong half the time.
            case THEIRS: return brief?"From a device":"From another device";
            default: return null;
        }
        if(brief)return owed>0?where+" · "+owed:where;
        return owed>0?where+" · "+owed+" waiting":where+" · up to date";
    }

    /** What is being shared, in as few words as a title can carry — the thing, and what kind of thing. */
    static String shortly(Scope scope,String name) {
        switch(scope) {
            case COLLECTION: case BOOK: case THING: return name+" collection";
            case PAGE: return "this note";
            default: return "everything";
        }
    }

    /**
     * The same thing, named so that the name still means something on somebody else's phone. "This note"
     * is true where you are standing on it and nowhere else, so an offer that has to travel carries what
     * the thing is called instead.
     */
    static String travelling(Scope scope,String name) {
        switch(scope) {
            case COLLECTION: case BOOK: case THING: return "the collection "+name;
            case PAGE: return "the note "+name;
            default: return "everything on their pad";
        }
    }

    /** What that mark means, said in full for anybody who cannot see it. */
    static String describe(State state,int addresses) {
        switch(state) {
            case DEVICES: return addresses==1?"On one device of yours":"On "+addresses+" devices of yours";
            case OTHERS: return addresses==1?"Shared with one address":"Shared with "+addresses+" addresses";
            case THEIRS: return "Shared with you by another device";
            default: return "On this device only";
        }
    }

    /** What the reader is told they are sharing, at each level. */
    static String describe(Scope scope,String name) {
        switch(scope) {
            case LIBRARY: return "every collection and note";
            case COLLECTION: case BOOK: case THING: return "the collection "+name+", and everything in it";
            default: return "this note";
        }
    }

    private Sharing(){}
}
