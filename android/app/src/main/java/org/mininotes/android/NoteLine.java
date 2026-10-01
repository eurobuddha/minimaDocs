// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.util.List;

/**
 * What the open note's syncing is doing, said in a few quiet words on the line under its title, after its mark
 * and its people.
 *
 * <p>It was said at the foot of the window, on the PC's bar and the phone's strip, while the mark it was about sat
 * at the top: the eye went up to see whether the note had gone and the answer was at the bottom. Now the words are
 * beside the mark, in its colour - amber while it goes, green when it has, red when it could not - and a tap on them
 * does what a tap on the mark does. What is not about the open note stays where it was.
 *
 * <p>Holds no Android or Swing types: the phone and the PC say the same words, and they are unit tested.
 */
final class NoteLine {
    private NoteLine(){}

    /** Which colour the words wear: the mark's own for the same thing. */
    enum Tone {
        GOING(SyncMark.WAITING), DONE(SyncMark.GONE), FAILED(SyncMark.STUCK);
        final SyncMark mark;
        Tone(SyncMark mark){this.mark=mark;}
    }

    static final String SAVING="Saving…",SENDING="Sending…",SYNCING="Syncing…";

    /** Words that stay up until the work that said them says how it ended: anything still going on. */
    static boolean stays(String words){return words!=null&&words.trim().endsWith("…");}

    /** "Sending to Ana…": who, where the note reaches few enough to name. */
    static String sending(List<String> names){String who=who(names);return who.isEmpty()?SENDING:"Sending to "+who+"…";}

    /** Gone, said by name; that they have it is theirs to say, and the mark turns to a tick when they do. */
    static String sent(List<String> names) {
        String who=who(names);
        return (who.isEmpty()?"Sent":"Sent to "+who)+", waiting for "+(names==null||names.size()<2?"them":"them all")+" to confirm";
    }

    /** Nothing was waiting, and the others were asked for what they have. */
    static final String NOTHING="Nothing new to send. Asked the others for theirs.";

    /**
     * What did not go, short enough for the line, with the reasons behind a tap (see Unsent). Somebody still being
     * linked with is not a failure: it goes by itself once they answer.
     */
    static String couldNot(List<Unsent.Problem> problems,int sent) {
        Unsent.Problem first=Unsent.first(problems);
        if(first!=null&&first.why==Unsent.Why.LINKING)return Unsent.linking(first.who);
        if(sent>0)return "Some of it went - see why";
        if(first!=null&&!first.who.isEmpty()&&Unsent.distinct(problems).size()==1)return "Could not reach "+first.who+" - see why";
        return "Could not go - see why";
    }

    /** The colour of what couldNot says: amber for a link that is on its way, red for the rest. */
    static Tone couldNotTone(List<Unsent.Problem> problems) {
        Unsent.Problem first=Unsent.first(problems);
        return first!=null&&first.why==Unsent.Why.LINKING?Tone.GOING:Tone.FAILED;
    }

    /** One name, two, or the first and how many more. */
    private static String who(List<String> names) {
        if(names==null||names.isEmpty())return "";
        if(names.size()==1)return names.get(0);
        if(names.size()==2)return names.get(0)+" and "+names.get(1);
        return names.get(0)+" and "+(names.size()-1)+" others";
    }
}
