package org.mininotes.android;

import java.util.*;

/** The existing Parlons CONTACTS record format (US fields, RS rows). */
final class ParlonsContact {
    final String key,name,address;
    ParlonsContact(String key,String name,String address){this.key=key;this.name=name;this.address=address;}
    static boolean key(String value){return value!=null&&value.matches("0x[0-9a-fA-F]{64,2048}")&&(value.length()%2==0);}
    static List<ParlonsContact> read(String text) {
        if(text==null||text.length()>262144)throw new IllegalArgumentException("Parlons returned an unreadable contact list.");
        List<ParlonsContact> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        if(text.isEmpty())return out;
        for(String row:text.split("\u001e",-1)) {
            String[] fields=row.split("\u001f",-1);
            if(fields.length!=3||!key(fields[0])||fields[1].length()>1024||fields[2].length()>Pairing.ADDRESS_MOST)
                throw new IllegalArgumentException("Parlons returned an unreadable contact. Edit it in Parlons and refresh.");
            String key=fields[0].toLowerCase(Locale.ROOT);
            if(!seen.add(key))continue;
            String name=fields[1].trim().replaceAll("[\\p{Cntrl}]", " ");
            out.add(new ParlonsContact(key,name.isEmpty()?"Unnamed contact":name,fields[2]));
        }
        out.sort(Comparator.comparing(c->c.name.toLowerCase(Locale.ROOT)));return out;
    }
    static String invitation(String wire) {
        if(wire==null||wire.length()>Pairing.MOST*8+2)throw new IllegalArgumentException("Invitation too large");
        String line=wire;
        if(wire.startsWith("0x")) {
            if(wire.length()%2!=0)throw new IllegalArgumentException("Invalid invitation");
            byte[] bytes=new byte[(wire.length()-2)/2];
            for(int i=0;i<bytes.length;i++){
                int hi=Character.digit(wire.charAt(2+i*2),16),lo=Character.digit(wire.charAt(3+i*2),16);
                if(hi<0||lo<0)throw new IllegalArgumentException("Invalid invitation");bytes[i]=(byte)(hi*16+lo);
            }
            line=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        }
        Pairing.Said invitation=Pairing.read(line);
        if(!Pairing.reachable(invitation.address))throw new IllegalArgumentException("Invitation has no reachable address");
        return line;
    }
}
