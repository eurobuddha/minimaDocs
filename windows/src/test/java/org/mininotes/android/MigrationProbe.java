// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import org.mininotes.desktop.platform.content.Context;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The move to notes and collections, run on a copy of a real notebook before it runs on any device (docs/HOME.md,
 * step 1). Each argument is {@code label=folder}, where the folder is a phone backup (with {@code databases/}) or a
 * copy of the PC's data folder. The notebook is copied into a folder of its own, counted, opened - which moves it -
 * and counted again; then the copy is deleted. Only numbers are printed: never a name, a title or a word of a note.
 *
 * <p>Run with {@code android/gradlew.bat -p windows migrationProbe -Pcopies="pro=...;graphene=..."}. A locked
 * notebook cannot be opened here - its key never leaves its device - and says so rather than guessing.
 */
public final class MigrationProbe {
    public static void main(String[] args) throws Exception {
        for(String arg:args)for(String one:arg.split(";")) {
            if(one.isBlank())continue;
            int eq=one.indexOf('=');
            probe(eq<0?"copy":one.substring(0,eq),new File(eq<0?one:one.substring(eq+1)));
        }
    }

    private static void probe(String label,File folder) throws Exception {
        File db=new File(folder,"databases"+File.separator+"mininotes.db");
        if(!db.isFile())db=new File(folder,"mininotes.db");
        if(!db.isFile()){System.out.println(label+": no notebook in that folder");return;}
        byte[] head=new byte[16];
        try(java.io.InputStream in=Files.newInputStream(db.toPath())){if(in.read(head)!=16)head=new byte[16];}
        if(!new String(head,java.nio.charset.StandardCharsets.US_ASCII).startsWith("SQLite format 3")) {
            System.out.println(label+": the notebook is locked (encrypted); it cannot be opened off its device");
            return;
        }
        Path work=Files.createTempDirectory("mininotes-migrate");
        try {
            for(String tail:new String[]{"","-wal","-shm","-journal"}) {
                File from=new File(db.getPath()+tail);
                if(from.isFile())Files.copy(from.toPath(),work.resolve("mininotes.db"+tail),StandardCopyOption.REPLACE_EXISTING);
            }
            File copy=work.resolve("mininotes.db").toFile();
            int was=version(copy);
            long[] before=was==SchemaMigrations.BEFORE_THINGS?counts(copy,SchemaMigrations.COUNT_BEFORE):null;
            Map<String,Long> orphansBefore=was==SchemaMigrations.BEFORE_THINGS?one(copy,
                "SELECT (SELECT COUNT(*) FROM notes WHERE book NOT IN (SELECT id FROM books)) AS notes,"
                +"(SELECT COUNT(*) FROM books WHERE collection NOT IN (SELECT id FROM collections)) AS books"):Map.of();
            // And the received files that move to Home at 31 (step 2), counted wherever the drop box's tables are already there.
            long[] homeBefore=was>=26&&was<=SchemaMigrations.BEFORE_HOME_FILES?counts(copy,SchemaMigrations.COUNT_BEFORE_HOME):null;
            String refused=null;
            try(NoteStore store=new NoteStore(new Context(work.toFile()))){store.getWritableDatabase();}
            catch(IllegalStateException no){refused=no.getMessage();}
            int now=version(copy);
            System.out.println(label+": schema "+was+" -> "+now+(refused==null?"":" REFUSED: "+refused));
            if(before!=null)System.out.println(label+":   before: "+SchemaMigrations.counted(before)
                +"; notes on no book "+orphansBefore.get("notes")+", books in no collection "+orphansBefore.get("books"));
            if(now<30)return;
            long[] after=counts(copy,SchemaMigrations.COUNT_AFTER);
            System.out.println(label+":   after:  "+SchemaMigrations.counted(after));
            if(before!=null){String differs=SchemaMigrations.differs(before,after);System.out.println(label+":   "+(differs==null?"every count agrees":"DIFFERS: "+differs));}
            if(now>SchemaMigrations.BEFORE_HOME_FILES) {
                long[] homeAfter=counts(copy,SchemaMigrations.COUNT_AFTER_HOME);
                if(homeBefore!=null)System.out.println(label+":   home before: "+SchemaMigrations.counted(SchemaMigrations.COUNTED_HOME,homeBefore));
                System.out.println(label+":   home after:  "+SchemaMigrations.counted(SchemaMigrations.COUNTED_HOME,homeAfter));
                if(homeBefore!=null){String differs=SchemaMigrations.differs(SchemaMigrations.COUNTED_HOME,homeBefore,homeAfter);
                    System.out.println(label+":   "+(differs==null?"every received file is on Home":"DIFFERS: "+differs));}
            }
            // How deep everything sits now, and whether anything points at a parent that is not there.
            Map<String,String> parents=new HashMap<>();
            try(Connection c=open(copy);Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT id,parent FROM things")) {
                while(r.next())parents.put(r.getString(1),r.getString(2));
            }
            Map<Integer,Integer> depths=new TreeMap<>();int orphanThings=0;
            for(String id:parents.keySet()) {
                depths.merge(Things.depth(parents,id),1,Integer::sum);
                String up=parents.get(id);
                if(!Things.HOME.equals(up)&&!parents.containsKey(up))orphanThings++;
            }
            Map<String,Long> rest=one(copy,"SELECT (SELECT COUNT(*) FROM notes WHERE book<>'"+Things.HOME+"' AND book NOT IN (SELECT id FROM things)) AS notes,"
                +"(SELECT COUNT(*) FROM things WHERE dock>0)+(SELECT COUNT(*) FROM notes WHERE dock>0) AS docked,"
                +"(SELECT MAX(dock) FROM (SELECT dock FROM things UNION ALL SELECT dock FROM notes)) AS topdock,"
                +"(SELECT COUNT(*) FROM files WHERE held='book') AS bookfiles,"
                +"(SELECT COUNT(*) FROM files WHERE held='collection') AS collectionfiles,"
                +"(SELECT COUNT(*) FROM files WHERE held='note') AS notefiles");
            System.out.println(label+":   collections by depth "+depths+"; collections under nothing "+orphanThings
                +", notes under nothing "+rest.get("notes")+"; docked "+rest.get("docked")+" (highest place "+rest.get("topdock")+")"
                +"; files kept with notes "+rest.get("notefiles")+", with collections "+rest.get("collectionfiles")+", still saying book "+rest.get("bookfiles"));
        } finally {
            try(var walk=Files.walk(work)){walk.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}
        }
    }

    private static Connection open(File db) throws Exception{return DriverManager.getConnection("jdbc:sqlite:"+db.getAbsolutePath());}

    private static int version(File db) throws Exception {
        try(Connection c=open(db);Statement s=c.createStatement();ResultSet r=s.executeQuery("PRAGMA user_version")){return r.next()?r.getInt(1):-1;}
    }

    private static long[] counts(File db,String[] asks) throws Exception {
        long[] all=new long[asks.length];
        try(Connection c=open(db);Statement s=c.createStatement()) {
            for(int at=0;at<asks.length;at++)try(ResultSet r=s.executeQuery(asks[at])){all[at]=r.next()?r.getLong(1):-1;}
        }
        return all;
    }

    private static Map<String,Long> one(File db,String sql) throws Exception {
        Map<String,Long> row=new HashMap<>();
        try(Connection c=open(db);Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)) {
            if(r.next())for(int at=1;at<=r.getMetaData().getColumnCount();at++)row.put(r.getMetaData().getColumnLabel(at),r.getLong(at));
        }
        return row;
    }

    private MigrationProbe(){}
}
