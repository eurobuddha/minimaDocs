// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

/**
 * What to do with a note that arrives from somewhere else. One decision, made from four facts: what this
 * phone has, what arrived, and the revision each of them counts — plus, where there is one, the text both
 * sides last agreed on.
 *
 * <p>Revisions are counted, not timed. Two phones whose clocks disagree would otherwise lose somebody's
 * writing, and the one thing this must never do is lose writing.
 *
 * <ul>
 *   <li><b>New</b> — nothing here by that name. Keep what arrived.</li>
 *   <li><b>Older</b> — this phone has already written past it. Ignore it, and say nothing.</li>
 *   <li><b>Newer</b> — it descends from what is here, so it contains it. Take it.</li>
 *   <li><b>Merged</b> — both sides wrote since they last agreed. Merge, and if the two wrote over each
 *       other, keep both: the page keeps what this phone shows, the other becomes a version to look at.</li>
 * </ul>
 *
 * <p>Holds no Android types, so the decision is unit tested without a device.
 */
final class Arriving {
    enum What { NEW, OLDER, NEWER, MERGED }

    static final class Decision {
        final What what;
        /** What the note should say after this, or null when nothing changes. */
        final String text;
        /** The revision to write with it. */
        final long revision;
        /** True when both sides wrote over the same lines, so the note now holds what each of them wrote. */
        final boolean keepTheirs;
        Decision(What what,String text,long revision,boolean keepTheirs) {
            this.what=what;this.text=text;this.revision=revision;this.keepTheirs=keepTheirs;
        }
    }

    /**
     * @param here     what this phone has, or null if it has never seen this note
     * @param hereRev  how many times this phone has written it
     * @param base     what the two sides last agreed on, or null if they never did
     * @param baseRev  the revision that agreement was at
     * @param theirs   what arrived
     * @param theirRev how many times the sender had written it
     */
    static Decision weigh(String here,long hereRev,String base,long baseRev,String theirs,long theirRev) {
        return weigh(here,hereRev,base,baseRev,theirs,theirRev,false,false);
    }

    /**
     * The same, knowing a little of where the two texts came from.
     *
     * <p>What two devices last agreed on is kept per pair, and between three of the owner's own devices a text
     * mostly reaches one of them from another that took it first: the PC sends the phone what it took from the
     * Pro, and the phone's agreement with the Pro stays where it was. When the Pro's next writing arrives it is
     * weighed against that old agreement, the phone's copy of the Pro's own earlier text looks like writing of
     * the phone's, and the two are put together - lines the Pro had since deleted or rewritten came back beside
     * the new ones, a revision nobody wrote went out from a phone nobody touched, and it looked like the note
     * going back. Seen on 2026-09-29 on the GrapheneOS phone with the note open and nobody typing.
     *
     * <p>So, where the counts alone would put the two together, what is known of the texts is asked first:
     *
     * @param theyHadMine this device's text is one the sender's note held before (it said so, see
     *                    {@link Parcel.Sent#history}): what arrived was written on top of it, and is taken
     * @param iHadTheirs  what arrived is a text this device's note held before, or already weighed: it is behind
     *                    what is here, and there is nothing to take
     */
    static Decision weigh(String here,long hereRev,String base,long baseRev,String theirs,long theirRev,
                          boolean theyHadMine,boolean iHadTheirs) {
        if(here==null){
            if(RichDocument.marked(theirs)&&RichDocument.read(theirs)==null)return new Decision(What.OLDER,null,0,false);
            return new Decision(What.NEW,theirs==null?"":theirs,Math.max(0,theirRev),false);
        }
        String mine=here, came=theirs==null?"":theirs;
        if(mine.equals(came))return new Decision(What.NEWER,mine,Math.max(hereRev,theirRev),false);
        // Office/image bytes are immutable attachments. Only their causal register is merged.
        if(RichDocument.marked(mine)||RichDocument.marked(came)) {
            RichDocument a=RichDocument.read(mine),b=RichDocument.read(came);
            if(a==null||b==null)return new Decision(What.OLDER,null,hereRev,true);
            try {
                String joined=a.merge(b).text();
                if(joined.equals(mine))return new Decision(What.OLDER,null,hereRev,false);
                if(joined.equals(came))return new Decision(What.NEWER,joined,Math.max(hereRev,theirRev),false);
                return new Decision(What.MERGED,joined,Math.max(hereRev,theirRev)+1,true);
            }catch(IllegalArgumentException invalid){return new Decision(What.OLDER,null,hereRev,true);}
        }
        // This phone has not written since the two sides last agreed, so what arrived simply follows it on.
        if(hereRev<=baseRev&&theirRev>=hereRev)return new Decision(What.NEWER,came,Math.max(theirRev,hereRev),false);
        // The sender had not written since then, so this phone is the one that moved: nothing to take.
        if(theirRev<=baseRev)return new Decision(What.OLDER,null,hereRev,false);
        // Asked only here, where the counts would otherwise merge: the counts are right where they can be.
        // What they say of their own note comes first. Words this device once held can come round again - a line
        // added and then taken out again over there - and that is a writing on top of what is here, which only they
        // can know; what this device kept tells it only that it has seen those words before.
        if(theyHadMine)return new Decision(What.NEWER,came,Math.max(theirRev,hereRev),false);
        if(iHadTheirs)return new Decision(What.OLDER,null,hereRev,false);
        Merge.Result merged=Merge.merge(base,mine,came);
        // Putting the two together gave back exactly what this phone already says. That is not a new
        // revision: nothing was written. It used to be counted as one all the same, and a revision is
        // something the other phone is owed — so where two phones had written over the same line and
        // each rightly kept its own, each then told the other about a "new" text that was its old one,
        // the other weighed that, kept its own again, counted again, and told the first. For ever, one
        // step every time anybody pressed Sync, with both marks saying "waiting" and nothing to wait for.
        // Everything they wrote is already here: an echo, or lines this phone took in before.
        if(merged.text.equals(mine))return new Decision(What.OLDER,null,hereRev,false);
        // And the other way round: putting the two together gave exactly what they sent, so it is simply
        // taken, at their revision, and there is nothing of this phone's own to send back.
        if(merged.text.equals(came))return new Decision(What.NEWER,came,Math.max(hereRev,theirRev),false);
        return new Decision(What.MERGED,merged.text,Math.max(hereRev,theirRev)+1,!merged.clean);
    }

    /**
     * The same question, for a copy this phone may only read.
     *
     * <p>A copy is not a second notebook. What its owner sends is what it says, and nothing here is weighed
     * against it: where this phone's copy says something else - written in on a build that let a reader
     * write, or before they were made one - it is not put together with what arrived, it is replaced, and
     * whoever calls this keeps what it said as a version. The one thing still set aside is what is older
     * than what has already come from the same phone, so a message that took the long way round cannot
     * undo a newer one that arrived first.
     *
     * @param lastFromThem the revision this phone last took from the sender, or 0 where it never has
     */
    static Decision copy(String here,long hereRev,long lastFromThem,String theirs,long theirRev) {
        String came=theirs==null?"":theirs;
        if(RichDocument.marked(here)||RichDocument.marked(came))return weigh(here,hereRev,null,0,came,theirRev);
        if(here==null)return new Decision(What.NEW,came,Math.max(0,theirRev),false);
        if(theirRev<lastFromThem)return new Decision(What.OLDER,null,hereRev,false);
        // Counted on from where this phone is, never back: a count that went backwards would make the
        // next thing to arrive look like old news.
        return new Decision(What.NEWER,came,Math.max(hereRev,theirRev),false);
    }

    /**
     * The revision two phones last both had, from what this phone believes and what the sender says.
     *
     * <p>The older of the two, always. Both beliefs fail the same way — taking the other phone to have
     * more than it does, because something sent never arrived — and the two mistakes are not alike. A base
     * that is too old makes a merge see more changes than there were, and it puts them together anyway. A
     * base that is too new makes what the other phone never had look like something it had and deleted,
     * and their next writing arrives looking like old news. That is how two words somebody typed came to
     * be on one phone and not the other with neither phone thinking anything was wrong.
     *
     * @param said what the sender says it was written on top of, or negative where it did not say
     */
    static long agreed(long believed,long said) {
        return said<0?Math.max(0,believed):Math.max(0,Math.min(believed,said));
    }

    /**
     * What names a text when devices say which texts they have held: the first sixteen bytes of its SHA-256, in hex.
     * Only the words of the note, not its title, which is not put together line by line.
     */
    static String trace(String text) {
        try {
            byte[] digest=java.security.MessageDigest.getInstance("SHA-256")
                .digest((text==null?"":text).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder(32);
            for(int at=0;at<TRACE_BYTES;at++)out.append(String.format(java.util.Locale.ROOT,"%02x",digest[at]));
            return out.toString();
        } catch(java.security.NoSuchAlgorithmException none) {
            // Cannot happen: SHA-256 is required of every Java. Named rather than swallowed all the same.
            throw new IllegalStateException("SHA-256 is missing",none);
        }
    }
    static final int TRACE_BYTES=16;

    /** What the open page should say, once the note underneath it has changed. */
    static final class Page {
        /** The text to show. */
        final String text;
        /** True when the page holds words the notebook does not, so it has to be written down again. */
        final boolean unsaved;
        Page(String text,boolean unsaved){this.text=text;this.unsaved=unsaved;}
    }

    /**
     * The note that is open changed underneath the page showing it.
     *
     * <p>The page is a copy of the note taken when it was opened, and the notebook has just been given
     * something newer. Left alone, the next word typed writes the old copy back over what arrived, one
     * revision higher — and then sends it, so the other phone loses what it wrote as well. Both ends, and
     * neither is told.
     *
     * <p>Where nothing has been typed since the page was last written down, the page simply becomes what the
     * notebook now says. Where something has, those words are in neither the notebook nor what arrived, so
     * they are put together the way any two writings are: against the text the page started from.
     *
     * @param kept   what the page said when it was last written down, which is what the notebook held
     * @param page   what the page says now
     * @param stored what the notebook says now
     */
    static Page onThePage(String kept,String page,String stored) {
        String was=kept==null?"":kept, now=page==null?"":page, said=stored==null?"":stored;
        if(now.equals(said))return new Page(now,false);
        if(now.equals(was))return new Page(said,false);
        return new Page(Merge.merge(was,now,said).text,true);
    }

    /**
     * The same, knowing whether anything was typed on the page since it was last written down. Where nothing was, the
     * page is taken to say what the notebook said then, whatever is on the screen: blank rules tapped onto it, a note
     * longer than the page takes, anything that changed the words without a key. Only typing is writing, and a page
     * nobody typed on never becomes a revision of its own - it simply says what arrived.
     */
    static Page onThePage(String kept,String page,String stored,boolean typed) {
        return onThePage(kept,typed?page:kept,stored);
    }

    private Arriving(){}
}
