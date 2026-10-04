package org.mininotes.android;

import android.content.Context;
import java.io.*;
import java.util.*;
import net.zetetic.database.sqlcipher.SQLiteDatabase;

/** Commits a complete editor snapshot through the existing encrypted notebook and file transport. */
final class DocumentStore {
    static final class Saved {
        final NoteStore.Held file;final RichDocument viewed,current;
        Saved(NoteStore.Held file,RichDocument viewed,RichDocument current){this.file=file;this.viewed=viewed;this.current=current;}
    }
    static String extension(String kind){return kind.equals("image")?"minimadocs-image.json":kind;}
    static String mime(String kind){return kind.equals("image")?"application/json":kind.equals("xlsx")?
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":"application/vnd.openxmlformats-officedocument.wordprocessingml.document";}
    static Saved save(Context context,NoteStore store,String noteId,String title,RichDocument viewed,byte[] bytes) throws Exception {
        if(bytes.length==0||bytes.length>Enclosure.MOST)throw new IOException("The file must be between 1 byte and 16 MiB.");
        synchronized(store) {
            if(!PhoneLock.open(context))throw new IOException("Unlock your notebook before saving.");
            byte[] key=NoteStore.key();
            if(PhoneLock.locked(context)&&key==null)throw new IOException("Unlock your notebook before saving.");
            android.content.SharedPreferences prefs=context.getSharedPreferences("document-identity",Context.MODE_PRIVATE);
            String actor=prefs.getString("actor","");
            if(!RichDocument.id(actor)) {
                actor=UUID.randomUUID().toString();
                if(!prefs.edit().putString("actor",actor).commit())throw new IOException("Could not keep this device's identity.");
            }
            SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
            File file=null;boolean committed=false;
            List<String> retired=new ArrayList<>();
            try {
                NoteStore.Note note=store.get(noteId);
                RichDocument current=note==null?RichDocument.empty(viewed.kind):RichDocument.read(note.body);
                if(current==null)throw new IOException("This note is no longer an editable document. Save a separate copy.");
                if(note!=null&&(note.deleted||note.archived||store.onlyReads(noteId)))throw new IOException("This document cannot be changed here. Save a separate copy.");
                if(note==null){note=new NoteStore.Note();note.id=noteId;note.book=Things.HOME;}
                NoteStore.Held held=store.opening(NoteStore.Branch.Kind.PAGE,noteId,
                    (title.isEmpty()?"Untitled":title)+"."+extension(viewed.kind),mime(viewed.kind),bytes.length);
                RichDocument written=viewed.write(actor,held.id,current.clock.getOrDefault(actor,0L));
                RichDocument merged=current.heads.isEmpty()?written:current.merge(written);
                long replacing=0;for(String old:current.heads.values())if(!merged.heads.containsValue(old)){
                    NoteStore.Held previous=store.file(old);if(previous!=null&&noteId.equals(previous.note))replacing+=previous.bytes;
                }
                if(store.weight()-replacing+bytes.length>Attachment.PLENTY)throw new IOException(Given.FULL);
                file=store.fileFor(held.id);
                try(FileOutputStream out=new FileOutputStream(file)) {
                    if(key==null)out.write(bytes);else Sealed.seal(key,new ByteArrayInputStream(bytes),out);
                    out.getFD().sync();
                }
                note.title=title.isEmpty()?"Untitled":title;note.body=merged.text();note.updated=System.currentTimeMillis();note.revision++;
                store.save(db,note);store.keep(db,held);
                // Only superseded document heads are retired; unrelated attachments are never removed.
                for(String old:current.heads.values())if(!merged.heads.containsValue(old)) {
                    db.delete("files","id=? AND note=?",new String[]{old,noteId});
                    db.delete("reached","id=?",new String[]{old});retired.add(old);
                }
                db.setTransactionSuccessful();
                db.endTransaction();committed=true;
                for(String old:retired)store.fileFor(old).delete();
                return new Saved(held,written,merged);
            }finally {
                if(db.inTransaction())db.endTransaction();
                if(!committed&&file!=null)file.delete();
            }
        }
    }
}
