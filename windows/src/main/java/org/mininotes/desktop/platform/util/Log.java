package org.mininotes.desktop.platform.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * What the shared code says in the phone's log, written on the PC to a plain file: {@code logs\mininotes.log} in the
 * notebook's folder, about a megabyte at most, with the one before it kept as {@code mininotes.1.log}. Without it the
 * PC was the one device nobody could ask what it did. Callers say counts and states only - never a name, an address,
 * a key or a word of a note - so the file can be read by, and sent to, whoever helps.
 *
 * <p>Nowhere until {@link #to} is called, which only the app does: tests write nothing unless started with
 * {@code -Dmininotes.log=<folder>}.
 */
public final class Log {
    /** How big one file grows before it is put aside, and so, with the one put aside, about all that is kept. */
    static final long MOST=1024L*1024;
    static final String FILE="mininotes.log",BEFORE="mininotes.1.log";
    private static final DateTimeFormatter WHEN=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static volatile Path folder;
    static{String asked=System.getProperty("mininotes.log");if(asked!=null&&!asked.isBlank())folder=Path.of(asked);}

    private Log(){}

    /** Where the log goes from now on; null for nowhere. */
    public static void to(Path where){folder=where;}

    public static int i(String tag,String message){write("I",tag,message);return 0;}
    public static int w(String tag,String message){write("W",tag,message);return 0;}

    private static synchronized void write(String level,String tag,String message) {
        Path at=folder;
        if(at==null)return;
        // One line per thing said, whatever it carries: a line break in a message would forge the next line.
        String line=WHEN.format(LocalDateTime.now())+"  "+level+"  "+tag+"  "+String.valueOf(message).replaceAll("[\\r\\n]+"," ")+System.lineSeparator();
        try {
            Files.createDirectories(at);
            Path file=at.resolve(FILE);
            if(Files.exists(file)&&Files.size(file)>=MOST)Files.move(file,at.resolve(BEFORE),StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(file,line,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        } catch(IOException|RuntimeException notWritten){/* a log that cannot be written must never stop what it describes */}
    }
}
