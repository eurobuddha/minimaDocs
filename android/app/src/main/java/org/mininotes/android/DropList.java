// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The drop box's files as a list, and the order they are shown in (see {@link Drop}).
 *
 * <p>The PC shows columns - Name, From, Date, Size, Type - and a click on one sorts by it, a second click the other
 * way; the phone has one Sort by. Both sort the same way, newest first until somebody says otherwise, so a file is
 * found where it was found on the other device. Date and Size start with the newest and the largest, which is what
 * somebody choosing them is looking for; the words start at A.
 *
 * <p>Holds no Android types: the orders are unit tested.
 */
final class DropList {
    private DropList(){}

    /** What the files can be sorted by: a column on the PC, a choice on the phone. */
    enum By {
        NAME("Name",false), FROM("From",false), DATE("Date",true), SIZE("Size",true), TYPE("Type",false);
        final String said; final boolean downFirst;
        By(String said,boolean downFirst){this.said=said;this.downFirst=downFirst;}
    }

    /** The PC's columns, left to right. */
    static final List<By> COLUMNS=List.of(By.NAME,By.FROM,By.DATE,By.SIZE,By.TYPE);
    /** The phone's Sort by, the one most wanted first. */
    static final List<By> CHOICES=List.of(By.DATE,By.NAME,By.SIZE,By.TYPE,By.FROM);

    /** One order: a column and a way. */
    static final class Order {
        final By by; final boolean down;
        Order(By by,boolean down){this.by=by;this.down=down;}
        @Override public boolean equals(Object o){return o instanceof Order other&&other.by==by&&other.down==down;}
        @Override public int hashCode(){return by.hashCode()*2+(down?1:0);}
        @Override public String toString(){return write(this);}
    }

    /** Until somebody says otherwise: the newest first. */
    static final Order FIRST=new Order(By.DATE,true);

    /** A column's header clicked: the same one turns round, another starts its own way. */
    static Order clicked(Order now,By column) {
        return now!=null&&now.by==column?new Order(column,!now.down):chosen(column);
    }

    /** A choice from the phone's Sort by, which has no second click: each starts its own way. */
    static Order chosen(By by){return new Order(by,by.downFirst);}

    /** The small ▲ or ▼ beside the column the list is sorted by; nothing beside the others. */
    static String arrow(Order now,By column){return now==null||now.by!=column?"":now.down?"▼":"▲";}

    /** A column's header as it is drawn. */
    static String header(Order now,By column){String a=arrow(now,column);return a.isEmpty()?column.said:column.said+"  "+a;}

    /** Kept between runs as a few letters; anything unreadable is the newest first again. */
    static String write(Order order){return order.by.name()+(order.down?":down":":up");}

    static Order read(String kept) {
        if(kept==null)return FIRST;
        int colon=kept.indexOf(':');
        if(colon<0)return FIRST;
        try {
            By by=By.valueOf(kept.substring(0,colon));
            String way=kept.substring(colon+1);
            return way.equals("down")?new Order(by,true):way.equals("up")?new Order(by,false):FIRST;
        } catch(IllegalArgumentException unknown){return FIRST;}
    }

    /** Whether the files are shown as cards or as a list, kept the same way. Cards until somebody picks the list. */
    static boolean listed(String kept){return "list".equals(kept);}

    /** A file's type, as its column says it and a card without a picture shows it: what its name ends in. */
    static String type(String name) {
        String named=name==null?"":name;
        int dot=named.lastIndexOf('.');
        String ext=dot<0||dot==named.length()-1?"FILE":named.substring(dot+1).toUpperCase(Locale.ROOT);
        return ext.length()>5?ext.substring(0,5):ext;
    }

    /** One file as the list shows it, and the thing it stands for. */
    static final class Row<T> {
        final T thing; final String name,from,type; final long at,bytes;
        Row(T thing,String name,String from,long at,long bytes){
            this.thing=thing;this.name=name==null?"":name;this.from=from==null?"":from;this.at=at;this.bytes=bytes;this.type=type(this.name);
        }
    }

    /**
     * The rows in this order. What the column cannot tell apart stays newest first, then by name, whichever way the
     * column goes - so two files of one sending never swap places when the list is turned round.
     */
    static <T> List<Row<T>> sorted(List<Row<T>> rows,Order order) {
        Order o=order==null?FIRST:order;
        Comparator<Row<T>> column;
        switch(o.by) {
            case NAME: column=(x,y)->x.name.compareToIgnoreCase(y.name);break;
            case FROM: column=(x,y)->x.from.compareToIgnoreCase(y.from);break;
            case SIZE: column=(x,y)->Long.compare(x.bytes,y.bytes);break;
            case TYPE: column=(x,y)->x.type.compareTo(y.type);break;
            default: column=(x,y)->Long.compare(x.at,y.at);
        }
        if(o.down)column=column.reversed();
        Comparator<Row<T>> then=column.thenComparing((x,y)->Long.compare(y.at,x.at)).thenComparing((x,y)->x.name.compareToIgnoreCase(y.name));
        List<Row<T>> all=new ArrayList<>(rows);
        all.sort(then);
        return all;
    }
}
