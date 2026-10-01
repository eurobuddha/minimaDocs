package org.mininotes.android;

import java.io.*;
import java.nio.file.*;
import org.mininotes.desktop.platform.content.Context;
import org.mininotes.desktop.platform.util.Log;

/**
 * Attachments as they lie on this PC: sealed with the notebook's key while it has a lock, plain otherwise.
 * Whatever reads or writes one comes through here, so no file of a locked notebook is written plain.
 */
final class DesktopFiles {
    private DesktopFiles(){}

    // ---- what arrives while the notebook is locked again ----------------------------------------------------

    /*
     * Where what the node hears goes: the node is told once, and this says whether the open notebook or the inbox has
     * it. Each window used to tell the node itself, and a window that locked again while its sharing was still
     * starting could have the node pointed back at it - closed, its worker gone - after it had said "the inbox":
     * everything heard until the notebook was opened again was then dropped. The ticket stops that: locking again
     * moves it on, and a window can hand arrivals to its notebook only with the ticket it had when it started sharing.
     */
    private static java.util.function.Consumer<byte[]> notebook;
    private static Path lockedIn;
    private static long ticket;

    /** Taken as a window starts sharing, and handed back with {@link #toNotebook}. */
    static synchronized long ticket(){return ticket;}

    /** From now on, what arrives goes to the open notebook - unless it has locked again since {@code asOf}. Whether it does. */
    static boolean toNotebook(long asOf,java.util.function.Consumer<byte[]> open) {
        synchronized(DesktopFiles.class){if(asOf!=ticket||open==null)return false;notebook=open;}
        Log.i("Mininotes/Desktop","what arrives goes to the open notebook");
        return true;
    }

    /** From now on, what arrives is kept sealed in this folder's inbox until the notebook is opened. */
    static void toInbox(Path folder) {
        synchronized(DesktopFiles.class){ticket++;notebook=null;lockedIn=folder;}
        Log.i("Mininotes/Desktop","what arrives is kept sealed in the inbox until the notebook is opened");
    }

    /** What the node heard, handed on: to the open notebook, or sealed in the inbox while it is locked. */
    static void arrived(byte[] message) {
        java.util.function.Consumer<byte[]> open;Path folder;
        synchronized(DesktopFiles.class){open=notebook;folder=lockedIn;}
        if(open!=null){open.accept(message);return;}
        if(folder!=null){keepArriving(folder,message);Log.i("Mininotes/Desktop","kept sealed in the inbox, "+waiting(folder)+" waiting");return;}
        // Nothing open and nothing locked: never seen, since the node is told only once a window is open. Said if it is.
        Log.w("Mininotes/Desktop","heard with no notebook open: not kept, the sender sends it again");
    }

    /** How many wait in the inbox. */
    static int waiting(Path folder) {
        try(var list=Files.list(folder.resolve("inbox"))){return (int)list.count();}catch(IOException none){return 0;}
    }

    /** Kept as it came - sealed for this PC - to be taken in when the notebook is opened. */
    static void keepArriving(Path folder,byte[] message) {
        try{Path inbox=folder.resolve("inbox");Files.createDirectories(inbox);Files.write(inbox.resolve(System.currentTimeMillis()+"-"+Integer.toHexString(java.util.Arrays.hashCode(message))),message);}
        catch(IOException full){/* nothing to say it to: the sender tries again until it is answered */}
    }

    /** Everything that waited, taken in, oldest first. How many landed. */
    static int takeIn(Context context,NoteStore store,Keys keys) {
        Path inbox=context.getFilesDir().toPath().resolve("inbox");int landed=0,waited=0;
        if(!Files.isDirectory(inbox))return 0;
        try(var list=Files.list(inbox)) {
            for(Path one:list.sorted().toList()) {
                waited++;
                try{if(Post.arrived(context,store,keys,Files.readAllBytes(one))!=null)landed++;}catch(Exception unreadable){/* not ours */}
                Files.deleteIfExists(one);
            }
        } catch(IOException e){/* the next opening tries again */}
        if(waited>0)Log.i("Mininotes/Desktop",waited+" waited in the inbox, "+landed+" taken in");
        return landed;
    }

    static boolean sealed(Path file) {
        byte[] head=new byte[Sealed.MAGIC.length];
        try(InputStream in=Files.newInputStream(file)){return in.readNBytes(head,0,head.length)==head.length&&Sealed.is(head);}
        catch(IOException unreadable){return false;}
    }

    /** A file taken in: sealed on the way when the notebook is locked. */
    static void keep(Context context,Path source,Path kept) throws IOException {
        byte[] key=context.databaseKey();
        if(key==null){Files.copy(source,kept);return;}
        try(InputStream in=new BufferedInputStream(Files.newInputStream(source));OutputStream out=new BufferedOutputStream(Files.newOutputStream(kept,StandardOpenOption.CREATE_NEW))){Sealed.seal(key,in,out);}
        catch(IOException e){Files.deleteIfExists(kept);throw e;}
    }

    /** The same for bytes that were never a file here - a recording, held in memory until Stop - so they never lie on the disk plain. */
    static void keep(Context context,byte[] bytes,Path kept) throws IOException {
        byte[] key=context.databaseKey();
        try(InputStream in=new ByteArrayInputStream(bytes);OutputStream out=new BufferedOutputStream(Files.newOutputStream(kept,StandardOpenOption.CREATE_NEW))){if(key==null)in.transferTo(out);else Sealed.seal(key,in,out);}
        catch(IOException e){Files.deleteIfExists(kept);throw e;}
    }

    /** Windows will not take some characters a phone's file name can hold - a recording's 14:05 among them - so a copy written out swaps them. */
    static String onDisk(String name){return Attachment.named(name).replace(':','.').replaceAll("[\\\\/*?\"<>|]","_");}

    /** A kept file's own bytes, written out: opened on the way if it is sealed. */
    static void copyOut(Context context,Path kept,OutputStream out) throws IOException {
        if(!sealed(kept)){Files.copy(kept,out);return;}
        byte[] key=context.databaseKey();
        if(key==null)throw new IOException("This attachment is locked, and the notebook is not open.");
        try(InputStream in=new BufferedInputStream(Files.newInputStream(kept))){Sealed.open(key,in,out);}
        catch(Vault.Refused refused){throw new IOException("This attachment could not be opened: "+refused.getMessage());}
    }

    /** Every attachment, and every file received on its own, sealed (lock on) or opened (lock off), each replaced whole, never left half done. */
    static void every(NoteStore store,byte[] key,boolean seal) throws IOException {
        for(NoteStore.Held held:store.everyFileKept()) {
            Path file=store.fileFor(held.id).toPath();
            if(!Files.isRegularFile(file)||sealed(file)==seal)continue;
            Path next=file.resolveSibling(file.getFileName()+".next");
            try(InputStream in=new BufferedInputStream(Files.newInputStream(file));OutputStream out=new BufferedOutputStream(Files.newOutputStream(next))) {
                if(seal)Sealed.seal(key,in,out);else Sealed.open(key,in,out);
            } catch(Vault.Refused refused){Files.deleteIfExists(next);throw new IOException("An attachment could not be opened: "+refused.getMessage());}
            catch(IOException e){Files.deleteIfExists(next);throw e;}
            Files.move(next,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
