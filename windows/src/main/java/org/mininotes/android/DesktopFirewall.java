// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Whether Windows lets paired devices in to this PC's door, and the owner's way to say yes or no.
 *
 * <p>The door and the home-network announcements are in every PC build, but Windows drops connections to a
 * program it has no rule for - on some PCs it asks, on others it says nothing and blocks. So the rule is
 * made here, by the owner, from Settings: Windows shows its own administrator prompt, and the answer is
 * theirs. The rules name ports, not the program, because the program's folder changes with every update.
 * They hold on home networks only (Windows' "Private"); a café's Wi-Fi stays closed.
 */
final class DesktopFirewall {
    private DesktopFirewall(){}

    static final String TCP="Mininotes direct (TCP)",UDP="Mininotes nearby (UDP)";

    /** Whether both rules are there and on, and what kind of network this PC is on now. */
    record State(boolean allowed,String network) {
        /** On a network Windows calls public, the rules do not apply: said, since the switch alone would mislead. */
        boolean publicHere(){return network.contains("Public");}
    }

    static State read() throws IOException {
        String out=run("$n=@(Get-NetFirewallRule -DisplayName '"+TCP+"','"+UDP+"' -ErrorAction SilentlyContinue | Where-Object {$_.Enabled -eq 'True'}).Count;"
            +"'RULES '+$n;'NETWORK '+((Get-NetConnectionProfile -ErrorAction SilentlyContinue | ForEach-Object {[string]$_.NetworkCategory}) -join ',')",false);
        int rules=0;String network="";
        for(String line:out.split("\\R")) {
            if(line.startsWith("RULES "))try{rules=Integer.parseInt(line.substring(6).trim());}catch(NumberFormatException none){rules=0;}
            if(line.startsWith("NETWORK "))network=line.substring(8).trim();
        }
        return new State(rules>=2,network);
    }

    /**
     * The rules made, or taken away. Windows asks the owner first; a "No" there leaves everything as it was
     * and is reported as such, not as a fault.
     */
    static State set(boolean allow) throws IOException {
        String inner="Get-NetFirewallRule -DisplayName '"+TCP+"','"+UDP+"' -ErrorAction SilentlyContinue | Remove-NetFirewallRule;"
            +(allow?"New-NetFirewallRule -DisplayName '"+TCP+"' -Description 'Mininotes: paired devices reach this PC directly' -Direction Inbound -Protocol TCP -LocalPort 9601-9620 -Profile Private -Action Allow | Out-Null;"
                +"New-NetFirewallRule -DisplayName '"+UDP+"' -Description 'Mininotes: hear paired devices on the home network' -Direction Inbound -Protocol UDP -LocalPort 9601 -Profile Private -Action Allow | Out-Null":"");
        String encoded=Base64.getEncoder().encodeToString(inner.getBytes(StandardCharsets.UTF_16LE));
        String out=run("try{Start-Process -FilePath '"+shell()+"' -Verb RunAs -Wait -WindowStyle Hidden -ArgumentList '-NoProfile','-NonInteractive','-EncodedCommand','"+encoded+"' -ErrorAction Stop;'DONE'}catch{'REFUSED'}",true);
        if(out.contains("REFUSED"))throw new Refused();
        return read();
    }

    /** The owner said no at Windows' prompt. */
    static final class Refused extends IOException{Refused(){super("Windows was not given permission. Nothing was changed.");}}

    // Windows PowerShell 5.1, in every Windows 10 and 11, which is where the firewall commands live.
    private static String shell() {
        return Path.of(System.getenv().getOrDefault("SystemRoot","C:\\Windows"),"System32","WindowsPowerShell","v1.0","powershell.exe").toString();
    }

    private static String run(String script,boolean waitsForPerson) throws IOException {
        String encoded=Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        Process p=new ProcessBuilder(shell(),"-NoProfile","-NonInteractive","-WindowStyle","Hidden","-EncodedCommand",encoded).redirectErrorStream(true).start();
        p.getOutputStream().close();
        String out=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        try {
            // Somebody answering a prompt gets minutes; a question the PC answers by itself gets seconds.
            if(!p.waitFor(waitsForPerson?5:1,TimeUnit.MINUTES)){p.destroyForcibly();throw new IOException("Windows did not answer.");}
        } catch(InterruptedException stop){Thread.currentThread().interrupt();throw new IOException("Stopped.");}
        return out;
    }
}
