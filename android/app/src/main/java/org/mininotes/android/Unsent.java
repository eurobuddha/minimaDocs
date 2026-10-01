// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Why something could not go, said so its owner knows what is wrong and what to do about it.
 *
 * <p>"Somebody has not been paired yet, so nothing can be sealed for them" was true and of no use: which somebody,
 * which thing, and then what? Every reason a send stops is one of these, with the device by its name, the thing by
 * its name, and - where the pad cannot put it right by itself - the one thing the owner can do, and a button that
 * goes there. Where it does put it right by itself, by trying again, it says that instead.
 *
 * <p>Holds no Android types: the phone and the PC say the same words, and they are unit tested.
 */
final class Unsent {
    private Unsent(){}

    enum Why {
        /** Paired halfway: the device is known here, but its keys never came, so nothing can be sealed for it. */
        NOT_PAIRED,
        /** Named in the list of people for a thing - it came in that list from somebody else - and never paired here. */
        ONLY_LISTED,
        /** Linked through that list, and asked to answer; nothing goes to it until it does. See {@link Linking}. */
        LINKING,
        /** Paired, and nothing of it could be reached just now: not on, not on this network, or no relay took it. */
        NOT_REACHED,
        /** Notes go only between the owner's devices, directly, and that device is not in reach of this one. */
        NOT_IN_REACH,
        /** This device's own connection is not up yet. */
        NOT_CONNECTED,
        /** A note from before sharing, whose id cannot travel. */
        TOO_OLD,
        /**
         * The device's build is from before collections nested (see {@link Things}): it knows three levels, and this does
         * not fit them. It waits, and goes the moment the device says it knows about trees.
         */
        NEEDS_UPDATE,
        /** Anything else, said in the words it came with. */
        OTHER
    }

    /** What the one button under the words does; NONE where waiting is the whole of it. */
    enum Fix { PAIR, TRAVEL, NONE }

    /** One reason, about one device and one thing. */
    static final class Problem {
        final Why why; final String who,thing,listedIn,detail;
        /** For {@link Why#ONLY_LISTED}, the address the list names, so the box can take them off it; empty otherwise. */
        final String address;
        /**
         * @param who      the device's name, or empty where it is not known
         * @param thing    what was being sent: a collection's or a book's name, a note's title; empty for everything
         * @param listedIn for {@link Why#ONLY_LISTED}, the thing whose list of people names them; empty otherwise
         * @param detail   for {@link Why#OTHER}, the words it failed with
         */
        Problem(Why why,String who,String thing,String listedIn,String detail){this(why,who,thing,listedIn,detail,"");}
        Problem(Why why,String who,String thing,String listedIn,String detail,String address) {
            this.why=why;this.who=who==null?"":who.trim();this.thing=thing==null?"":thing.trim();
            this.listedIn=listedIn==null?"":listedIn.trim();this.detail=detail==null?"":detail.trim();
            this.address=address==null?"":address.trim();
        }
        private String key(){return why+"\u0000"+who+"\u0000"+listedIn;}
    }

    /** Which reason an error is, from the words it was thrown with; the words kept for anything not known. */
    static Why of(String message) {
        String said=message==null?"":message;
        if(said.equals(Direct.WAITS))return Why.NOT_IN_REACH;
        if(said.contains("node is not running"))return Why.NOT_CONNECTED;
        if(said.contains("older than sharing"))return Why.TOO_OLD;
        if(said.contains("could not be looked up")||said.contains("could not be reached"))return Why.NOT_REACHED;
        return Why.OTHER;
    }

    /**
     * Which kind of reason a failure was, for a log line: the kind and never the words, which can name a device or a
     * note - and never the exception's name alone either, which says nothing to whoever reads the log.
     */
    static String reason(Throwable failed) {
        Why why=of(failed==null?null:failed.getMessage());
        return why==Why.OTHER?"something else ("+(failed==null?"nothing said":failed.getClass().getSimpleName())+")"
            :why.name().toLowerCase(java.util.Locale.ROOT).replace('_',' ');
    }

    private static String quoted(String thing){return "“"+thing+"”";}
    private static String device(String who){return who.isEmpty()?"A device":who;}
    private static String it(String who){return who.isEmpty()?"that device":who;}

    /**
     * What is wrong, in one or two sentences: the device and the thing by their names.
     *
     * @param here what this device is called in a sentence: "this phone", "this PC"
     */
    static String said(Problem p,String here) {
        String what=p.thing.isEmpty()?"it":quoted(p.thing);
        switch(p.why) {
            case NOT_PAIRED: return device(p.who)+" has not been paired with "+here+" yet, so "+what+" cannot be sealed for it.";
            case ONLY_LISTED: return notLinked(device(p.who),p.listedIn.isEmpty()?p.thing:p.listedIn,here);
            case LINKING: return linking(p.who);
            case NOT_REACHED: return p.thing.isEmpty()?(p.who.isEmpty()?"That device":p.who)+" could not be reached just now."
                :what+" could not reach "+it(p.who)+" just now.";
            case NOT_IN_REACH: return device(p.who)+" is not in reach, so "+what+" waits: notes go only between your devices, straight from one to the other, and it is not on this network.";
            case NOT_CONNECTED: return "Mininotes is not connected yet on "+here+".";
            case TOO_OLD: return what.equals("it")?"A note was written before sharing existed here, and cannot travel."
                :what+" was written before sharing existed here, and cannot travel.";
            case NEEDS_UPDATE: return needsUpdate(p.who)+".";
            default: return p.detail.isEmpty()?"Something went wrong sending "+what+".":p.detail;
        }
    }

    /** What the owner can do about it; or, where the pad puts it right by itself, that it will. */
    static String remedy(Problem p,String here) {
        switch(p.why) {
            case NOT_PAIRED:
                return "Pair with it: open People and devices, show your code, and scan it on "+it(p.who)+".";
            case ONLY_LISTED: return "Link with them to send it, or take them off if they should not have it.";
            case LINKING: return "If they do not, open Mininotes on "+it(p.who)+", or link with them by hand.";
            case NOT_REACHED: return "Open Mininotes on "+it(p.who)+", on the same Wi-Fi as "+here+" if you can. It stays waiting and goes by itself.";
            case NOT_IN_REACH: return "Put both on the same Wi-Fi, or let helpers carry it in How notes travel. Until then it stays waiting.";
            case NOT_CONNECTED: return "Check that "+here+" is online. It goes by itself once it is connected.";
            case TOO_OLD: return "Copy its words into a new note, and share that one.";
            case NEEDS_UPDATE: return "It stays waiting, and goes by itself once Mininotes is updated on "+it(p.who)+".";
            default: return "It stays waiting and is tried again by itself.";
        }
    }

    static Fix fix(Problem p) {
        switch(p.why) {
            case NOT_PAIRED: case ONLY_LISTED: case LINKING: return Fix.PAIR;
            case NOT_IN_REACH: return Fix.TRAVEL;
            default: return Fix.NONE;
        }
    }

    /** The button's words, or null for none. */
    static String button(Fix fix){return fix==Fix.PAIR?"Pair with it":fix==Fix.TRAVEL?"How notes travel":null;}

    /** The button's words for this reason: somebody only listed is linked with by their name. */
    static String button(Problem p) {
        return listed(p)?link(p.who):button(p==null?Fix.NONE:fix(p));
    }

    /** Whether it is about somebody a list names - not linked, or being linked - whose fix is linking by hand. */
    static boolean listed(Problem p){return p!=null&&(p.why==Why.ONLY_LISTED||p.why==Why.LINKING);}

    /**
     * Somebody a list names that this device has linked with through it and asked to answer: nothing goes until they do.
     * Said the same in the could-not-go box and in the box their round opens.
     */
    static String linking(String who) {
        return "Linking with "+(who==null||who.isBlank()?"that device":who.trim())+"… it goes once they answer.";
    }

    /**
     * Somebody on a thing's list of people this device was never linked with: who, and on what. Said the same in
     * the could-not-go box and in the box their round on a note opens.
     */
    static String notLinked(String who,String listedIn,String here) {
        return (who==null||who.isBlank()?"A device":who.trim())+" is listed for "
            +(listedIn==null||listedIn.isBlank()?"this":quoted(listedIn.trim()))+", but "+here+" is not linked to them.";
    }

    /**
     * What waits for a device whose build is from before collections nested (see {@link Why#NEEDS_UPDATE}): "Ana's phone
     * needs to update Mininotes to receive this", with no full stop, so the could-not-go box and the words about an amber
     * mark (see {@link Waits}) say it the same way.
     */
    static String needsUpdate(String who) {
        return (who==null||who.isBlank()?"A device":who.trim())+" needs to update Mininotes to receive this";
    }

    /** The one primary action for somebody only listed. */
    static String link(String who){return who==null||who.isBlank()?"Link with them":"Link with "+who.trim();}

    /** The quiet second action for somebody only listed, or null where nothing lists them. */
    static String takeOff(Problem p) {
        return !listed(p)||p.address.isEmpty()?null:takeOff(p.listedIn);
    }
    static String takeOff(String listedIn) {
        return listedIn==null||listedIn.isBlank()?"Take them off the list":"Take them off "+quoted(listedIn.trim());
    }

    /** The same reason about the same device said once, however many notes it stopped. In the order they came. */
    static List<Problem> distinct(List<Problem> all) {
        Map<String,Problem> once=new LinkedHashMap<>();
        if(all!=null)for(Problem p:all)once.putIfAbsent(p.key(),p);
        return new ArrayList<>(once.values());
    }

    /** The reason whose fix is the box's one button: a pairing first, since nothing else puts that right. */
    static Problem first(List<Problem> all) {
        List<Problem> once=distinct(all);
        for(Problem p:once)if(fix(p)==Fix.PAIR)return p;
        for(Problem p:once)if(fix(p)!=Fix.NONE)return p;
        return once.isEmpty()?null:once.get(0);
    }

    /** The box's title: whether any of it went. */
    static String title(int sent,int failed) {
        return sent>0?"Some of it went, and "+(failed==1?"one note":failed+" notes")+" could not":"It could not go";
    }

    /** The box's title, naming the device where one device is the whole of it: "“Transfer” could not reach Ana's phone". */
    static String title(List<Problem> all,int sent,int failed) {
        List<Problem> once=distinct(all);
        if(once.size()!=1||once.get(0).who.isEmpty())return title(sent,failed);
        Problem p=once.get(0);
        if(sent>0)return "Some of it went, but not to "+p.who;
        if(p.why==Why.LINKING)return "Linking with "+p.who;
        String what=!p.thing.isEmpty()?quoted(p.thing):!p.listedIn.isEmpty()?quoted(p.listedIn):"It";
        return what+" could not reach "+p.who;
    }

    /** The box's words: each reason and what to do about it, a paragraph each; at most three, then how many more. */
    static String words(List<Problem> all,String here) {
        List<Problem> once=distinct(all);
        StringBuilder out=new StringBuilder();
        for(int at=0;at<once.size()&&at<3;at++) {
            if(out.length()>0)out.append("\n\n");
            out.append(said(once.get(at),here)).append(' ').append(remedy(once.get(at),here));
        }
        if(once.size()>3)out.append("\n\n").append(once.size()-3).append(once.size()-3==1?" more reason, the same way.":" more reasons, the same way.");
        return out.length()==0?"It stays waiting and is tried again by itself.":out.toString();
    }
}
