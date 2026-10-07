package org.mininotes.android;

import java.io.*;

/** Uses the existing authenticated file format. The Mac host supplies a Keychain-protected key over stdin. */
public final class SecretBox {
    private static byte[] key;
    static synchronized void unlock(byte[] value) {
        if(value.length!=32||key!=null)throw new IllegalStateException("Invalid storage key initialization");
        key=value.clone();
    }
    public static byte[] cryptProtectData(byte[] bytes) {
        try {var out=new ByteArrayOutputStream();Sealed.seal(key,new ByteArrayInputStream(bytes),out);return out.toByteArray();}
        catch(Exception e){throw new IllegalStateException("Could not protect this device's settings",e);}
    }
    public static byte[] cryptUnprotectData(byte[] bytes) {
        try {var out=new ByteArrayOutputStream();Sealed.open(key,new ByteArrayInputStream(bytes),out);return out.toByteArray();}
        catch(Exception e){throw new IllegalStateException("Could not unlock this device's settings",e);}
    }
}
