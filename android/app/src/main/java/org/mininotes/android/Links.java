// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The web and mail addresses in a note, found the same way on the phone and the PC, so an address that opens on
 * one opens on the other. The phone used the system's own finder, which the PC does not have; the PC found none.
 *
 * <p>What counts: anything starting {@code http://}, {@code https://} or {@code www.}; {@code mailto:}; an e-mail
 * address; and a bare name ending in one of the common endings ({@code example.com/page}) - only those, so a file
 * name such as {@code notes.txt} is left as words. Stops at a space, and a full stop or bracket closing the
 * sentence is not taken as part of the address.
 *
 * <p>Holds no Android or Swing types: unit tested.
 */
final class Links {
    private Links(){}

    /** One address: where it is in the text, and what opening it opens. */
    record Link(int start,int end,String target) {
        boolean covers(int at){return at>=start&&at<end;}
    }

    private static final String ENDINGS="com|org|net|io|dev|app|co|info|me|edu|gov|eu|uk|de|fr|nl|es|it|tr|ch|at|be|se|no|dk|fi|pl|ie|pt|ca|us|au|nz|jp|in|br";
    private static final Pattern FIND=Pattern.compile(
        "(?i)(?<![\\w@.-])(?:"
        // An e-mail address, first, so the name before the @ is not left behind as words.
        +"(?<mail>[a-z0-9._%+-]+@[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,})"
        +"|(?<web>(?:https?://|www\\.|mailto:)[^\\s<>\"]+)"
        +"|(?<bare>(?:[a-z0-9-]+\\.)+(?:"+ENDINGS+")(?:/[^\\s<>\"]*)?)(?![\\w@-])"
        +")");

    /** Every address in the text, in order. */
    static List<Link> find(CharSequence text) {
        List<Link> out=new ArrayList<>();
        if(text==null||text.length()==0)return out;
        Matcher m=FIND.matcher(text);
        while(m.find()) {
            int start=m.start(),end=trimmed(text,start,m.end());
            if(end<=start)continue;
            String found=text.subSequence(start,end).toString();
            String target=m.group("mail")!=null?"mailto:"+found
                :m.group("bare")!=null||found.regionMatches(true,0,"www.",0,4)?"https://"+found:found;
            // Nothing after the scheme is not an address.
            if(target.matches("(?i)(https?://|mailto:)(www\\.?)?"))continue;
            out.add(new Link(start,end,target));
        }
        return out;
    }

    /** The address at a place in the text, or null. */
    static Link at(List<Link> links,int at) {
        if(links!=null)for(Link one:links)if(one.covers(at))return one;
        return null;
    }

    /**
     * Where an address really ends: without the full stop, comma or quote that ends the sentence around it, and
     * without a closing bracket it did not open - "(see example.com)" is the address without the bracket.
     */
    private static int trimmed(CharSequence text,int start,int end) {
        while(end>start) {
            char last=text.charAt(end-1);
            if(".,;:!?'\"".indexOf(last)>=0){end--;continue;}
            if(last==')'||last==']') {
                char open=last==')'?'(':'[';int opened=0,closed=0;
                for(int i=start;i<end;i++){char c=text.charAt(i);if(c==open)opened++;else if(c==last)closed++;}
                if(closed>opened){end--;continue;}
            }
            break;
        }
        return end;
    }
}
