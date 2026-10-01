// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.util.ArrayList;
import java.util.List;

/**
 * What is open, as the overview shows it (docs/HOME.md, step 2 and decision 22): the notes and collections opened, the
 * newest first, flicked through as a phone's open apps are, and closed one at a time or all at once.
 *
 * <p>Up to twelve. One opened past that lets the oldest go, as a phone lets its oldest app go: an overview nobody ever
 * closes anything in must not grow into a second list of everything. Kept across restarts as a few lines of text - one
 * per thing - so a line that was torn or written by something else is dropped and the rest still stand.
 *
 * <p>Holds no Android types: both apps keep it the same way, and it is unit tested.
 */
final class Overview {
    /** How many things are kept open at once (decision 22). */
    static final int MOST=12;

    /** The two kinds of thing that open here. A file opens in whatever opens it, not in the overview. */
    enum Kind { NOTE, COLLECTION }

    /** One open thing: which kind it is, and its id. */
    static final class Open {
        final Kind kind; final String id;
        Open(Kind kind,String id){this.kind=kind;this.id=id;}
        @Override public boolean equals(Object other){return other instanceof Open&&((Open)other).kind==kind&&((Open)other).id.equals(id);}
        @Override public int hashCode(){return kind.hashCode()*31+id.hashCode();}
        @Override public String toString(){return kind+" "+id;}
    }

    /** Newest first. */
    private final List<Open> open=new ArrayList<>();

    /**
     * Opened: to the front, from wherever it was in the list, or new at the front; past {@link #MOST} the oldest goes.
     *
     * @return false for an id that is not the id of anything - empty, or one that would break the line it is kept on
     */
    boolean open(Kind kind,String id) {
        if(!fits(kind,id))return false;
        Open one=new Open(kind,id);
        open.remove(one);open.add(0,one);
        while(open.size()>MOST)open.remove(open.size()-1);
        return true;
    }

    /** Closed, as a card swiped away. @return whether it was open */
    boolean close(Kind kind,String id){return kind!=null&&id!=null&&open.remove(new Open(kind,id));}

    /** Close all: the one button the overview has. */
    void closeAll(){open.clear();}

    /** Something deleted, whatever kind it was: a card never opens what is not there. @return whether it was open */
    boolean forget(String id){return id!=null&&open.removeIf(one->one.id.equals(id));}

    /** What is open, the newest first. A copy: changing it changes nothing here. */
    List<Open> all(){return new ArrayList<>(open);}

    int size(){return open.size();}
    boolean isEmpty(){return open.isEmpty();}

    /** The list as it is kept, newest first: a line each, its kind, a tab, its id. Empty for nothing open. */
    String said() {
        StringBuilder out=new StringBuilder();
        for(Open one:open)out.append(out.length()==0?"":"\n").append(one.kind.name()).append('\t').append(one.id);
        return out.toString();
    }

    /**
     * The list read back from what {@link #said} wrote. A line that does not read - no tab, a kind this build does not
     * know, no id - is left out, and so is a second line for the same thing and anything past {@link #MOST}; the rest
     * stand, in their order. Nothing at all, or null, is nothing open.
     */
    static Overview read(String said) {
        Overview back=new Overview();
        if(said==null)return back;
        for(String line:said.split("\n")) {
            int tab=line.indexOf('\t');
            if(tab<=0)continue;
            Kind kind;
            try{kind=Kind.valueOf(line.substring(0,tab).trim());}catch(IllegalArgumentException unknown){continue;}
            String id=line.substring(tab+1).trim();
            if(!fits(kind,id)||back.open.size()>=MOST)continue;
            Open one=new Open(kind,id);
            if(!back.open.contains(one))back.open.add(one);
        }
        return back;
    }

    /** Whether an id can be kept on a line of its own: something, with no line or tab in it. */
    private static boolean fits(Kind kind,String id) {
        return kind!=null&&id!=null&&!id.trim().isEmpty()&&id.indexOf('\n')<0&&id.indexOf('\r')<0&&id.indexOf('\t')<0;
    }
}
