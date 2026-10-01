// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * What another app hands the pad through the phone's share sheet - a screenshot, some photos, a PDF, a
 * line of text or a link - and the words said about it. Also the wording for files attached several at a
 * time, on the phone and on the PC alike. Holds no Android types, so it is unit tested without a device.
 */
final class Given {
    /** As long as a title may be: the same as a title typed in the bar. */
    static final int TITLE_MOST=120;
    /** How many notes are offered to add to, under New note. A few to choose from, not a second shelf. */
    static final int RECENT=4;
    /** What a note made from a share is called when nothing that came says anything. */
    static final String UNTITLED="Shared";

    /**
     * Whether a file another app hands over is one the pad will read. Only a content address, and never
     * one of the pad's own: a file address or a way back into this app's own provider would let whatever
     * shared it have the pad copy its own private files - its lock among them - into a note that may then
     * be sent to somebody.
     */
    static boolean readable(String scheme,String authority,String ours) {
        if(scheme==null||!"content".equals(scheme.toLowerCase(Locale.ROOT)))return false;
        return authority!=null&&!authority.isEmpty()&&!authority.equals(ours);
    }

    /** The first line of some text, trimmed and short enough to be a title, or empty. */
    static String firstLine(String text) {
        if(text==null)return "";
        for(String line:text.split("\n")) {
            String one=line.trim();
            if(!one.isEmpty())return one.length()>TITLE_MOST?one.substring(0,TITLE_MOST).trim():one;
        }
        return "";
    }

    /**
     * What a new note made from a share is called: what the sender called it (a web page's title comes
     * this way, with its link as the text), or else the first line of the text, or else the first file's
     * name without its ending - a screenshot is called what the phone called it.
     */
    static String title(String subject,String text,String firstFile) {
        String said=firstLine(subject);
        if(!said.isEmpty())return said;
        said=firstLine(text);
        if(!said.isEmpty())return said;
        if(firstFile==null||firstFile.trim().isEmpty())return UNTITLED;
        String name=Attachment.named(firstFile);
        int dot=name.lastIndexOf('.');
        if(dot>0)name=name.substring(0,dot).trim();
        return name.isEmpty()?UNTITLED:firstLine(name);
    }

    /**
     * The words a share adds to a note that already has a title: the text, with the sender's subject on
     * a line above it unless the text already says it - a link added to a list keeps what the page was.
     */
    static String words(String subject,String text) {
        String body=text==null?"":text.trim();
        String head=firstLine(subject);
        if(head.isEmpty()||body.contains(head))return body;
        return body.isEmpty()?head:head+"\n"+body;
    }

    /** A note's writing with some more put at its end, after a blank line. Nothing already there moves. */
    static String appended(String body,String added) {
        String was=body==null?"":body;
        if(added==null||added.isEmpty())return was;
        if(was.trim().isEmpty())return added;
        if(was.endsWith("\n\n"))return was+added;
        return was+(was.endsWith("\n")?"\n":"\n\n")+added;
    }

    /**
     * The notes offered to add to, in order: the one that was open when the pad was left first, because
     * that is most often the one being collected into, then whatever was written in most recently. Never
     * one this phone may only read, and never the same one twice.
     */
    static List<String> recent(List<String> lately,String openNow,Collection<String> readOnly,int most) {
        List<String> offered=new ArrayList<>();
        if(openNow!=null&&!openNow.isEmpty()&&lately.contains(openNow)&&!readOnly.contains(openNow))offered.add(openNow);
        for(String id:lately) {
            if(offered.size()>=most)break;
            if(id==null||id.isEmpty()||offered.contains(id)||readOnly.contains(id))continue;
            offered.add(id);
        }
        return offered;
    }

    /**
     * What came, in a few words: "3 pictures", "A PDF", "2 files". Pictures and videos are called that
     * only when every one of them is one; a mixture is files.
     */
    static String what(List<String> kinds) {
        int n=kinds.size();
        if(n==0)return "";
        boolean pictures=true, videos=true, pdfs=true;
        for(String kind:kinds) {
            String k=kind==null?"":kind.toLowerCase(Locale.ROOT);
            pictures&=k.startsWith("image/");videos&=k.startsWith("video/");pdfs&=k.equals("application/pdf");
        }
        String one=pictures?"picture":videos?"video":pdfs?"PDF":"file";
        if(n==1)return "A "+one;
        return n+" "+one+"s";
    }

    /** The line on the busy strip while files are copied in. */
    static String adding(int files){return files==1?"Adding the file…":"Adding "+files+" files…";}

    /**
     * The one line said once they are in. The name when there was one, a count when there were more, and
     * how many were not taken - which ones, and why, are said in a box of their own.
     */
    static String added(int kept,String onlyName,int refused) {
        String done=kept==0?"Nothing was added":kept==1?(onlyName==null||onlyName.isEmpty()?"1 file":onlyName)+" added"
            :kept+" files added";
        return refused==0||kept==0?done:done+", "+refused+" not";
    }

    /** One file that was not taken, and why, for the list that says so. */
    static String refusal(String name,String why){return Attachment.named(name)+" – "+why;}

    /** The box listing what was not taken. */
    static String refusals(List<String> lines) {
        StringBuilder said=new StringBuilder(lines.size()==1?"This was not added:\n\n":"These were not added:\n\n");
        for(String line:lines)said.append(line).append('\n');
        return said.append("\nNothing else was changed.").toString().trim();
    }

    /** Why a file was not taken, in the few words each reason needs. */
    static final String TOO_BIG="larger than 25 MB", FULL="this pad already holds 500 MB of files",
        UNREADABLE="it could not be read", NOT_A_FILE="the app it came from did not hand it over";

    private Given(){}
}
