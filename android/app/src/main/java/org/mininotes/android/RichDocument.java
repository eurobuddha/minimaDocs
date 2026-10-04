package org.mininotes.android;

import java.util.*;

/** A multi-value register for immutable document files. Causal counters, never wall clocks. */
final class RichDocument {
    static final String PREFIX="minimaDocs/1 ";
    static final int MAX_ACTORS=128, MAX_HEADS=32;
    final String kind;
    final SortedMap<String,Long> clock;
    /** Keys are actor:counter; values are immutable attachment ids. */
    final SortedMap<String,String> heads;

    private RichDocument(String kind,Map<String,Long> clock,Map<String,String> heads) {
        this.kind=kind;this.clock=Collections.unmodifiableSortedMap(new TreeMap<>(clock));
        this.heads=Collections.unmodifiableSortedMap(new TreeMap<>(heads));
        if(clock.size()>MAX_ACTORS||heads.size()>MAX_HEADS)throw new IllegalArgumentException("Too many document versions or editors. Save a separate copy.");
    }
    static RichDocument empty(String kind) {
        if(!Arrays.asList("docx","xlsx","image").contains(kind))throw new IllegalArgumentException("Unknown document type");
        return new RichDocument(kind,Collections.emptyMap(),Collections.emptyMap());
    }
    static boolean marked(String text){return text!=null&&text.startsWith("minimaDocs/");}
    static boolean id(String value){return value!=null&&value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    static RichDocument read(String text) {
        if(text==null||!text.startsWith(PREFIX)||text.length()>24000)return null;
        try {
            String[] lines=text.split("\n",-1);
            String kind=lines[0].substring(PREFIX.length());empty(kind);
            Map<String,Long> clock=new TreeMap<>();Map<String,String> heads=new TreeMap<>();
            for(int i=1;i<lines.length;i++) {
                String[] p=lines[i].split(" ",-1);
                if(p.length<3||!id(p[1]))return null;
                long count=Long.parseLong(p[2]);if(count<1||count==Long.MAX_VALUE)return null;
                if(p.length==3&&p[0].equals("v")){if(clock.put(p[1],count)!=null)return null;}
                else if(p.length==4&&p[0].equals("h")&&id(p[3])){if(heads.put(p[1]+":"+count,p[3])!=null)return null;}
                else return null;
            }
            for(String dot:heads.keySet())if(number(dot)!=clock.getOrDefault(actor(dot),0L))return null;
            if(heads.isEmpty()||new HashSet<>(heads.values()).size()!=heads.size())return null;
            return new RichDocument(kind,clock,heads);
        }catch(IllegalArgumentException bad){return null;}
    }
    private static String actor(String dot){return dot.substring(0,dot.indexOf(':'));}
    private static long number(String dot){return Long.parseLong(dot.substring(dot.indexOf(':')+1));}
    RichDocument write(String actor,String file,long floor) {
        if(!id(actor)||!id(file))throw new IllegalArgumentException("Invalid document identity");
        Map<String,Long> next=new TreeMap<>(clock);
        long count=Math.addExact(Math.max(next.getOrDefault(actor,0L),floor),1);
        if(count==Long.MAX_VALUE)throw new IllegalArgumentException("Document counter exhausted");
        next.put(actor,count);
        return new RichDocument(kind,next,Collections.singletonMap(actor+":"+count,file));
    }
    RichDocument merge(RichDocument other) {
        if(other==null||!kind.equals(other.kind))throw new IllegalArgumentException("Document types differ");
        Map<String,Long> joined=new TreeMap<>(clock);other.clock.forEach((a,n)->joined.merge(a,n,Math::max));
        Map<String,String> kept=new TreeMap<>();
        for(Map.Entry<String,String> h:heads.entrySet()) {
            String theirs=other.heads.get(h.getKey());
            if(theirs!=null&&!theirs.equals(h.getValue()))throw new IllegalArgumentException("Conflicting document identity");
            if(theirs!=null||number(h.getKey())>other.clock.getOrDefault(actor(h.getKey()),0L))kept.put(h.getKey(),h.getValue());
        }
        for(Map.Entry<String,String> h:other.heads.entrySet())
            if(heads.containsKey(h.getKey())||number(h.getKey())>clock.getOrDefault(actor(h.getKey()),0L))kept.put(h.getKey(),h.getValue());
        if(kept.isEmpty())throw new IllegalArgumentException("Document has no current version");
        return new RichDocument(kind,joined,kept);
    }
    String text() {
        StringBuilder text=new StringBuilder(PREFIX).append(kind);
        clock.forEach((actor,count)->text.append("\nv ").append(actor).append(' ').append(count));
        heads.forEach((dot,file)->text.append("\nh ").append(actor(dot)).append(' ').append(number(dot)).append(' ').append(file));
        return text.toString();
    }
    RichDocument remap(Map<String,String> files) {
        Map<String,String> remapped=new TreeMap<>();
        heads.forEach((dot,file)->{String target=files.get(file);if(!id(target))throw new IllegalArgumentException("Document backup is missing a file");remapped.put(dot,target);});
        return new RichDocument(kind,clock,remapped);
    }
}
