package org.mininotes.android;

import org.mininotes.desktop.platform.content.Context;

/** Mac storage is always encrypted; its key is released by the macOS Keychain at startup. */
final class PhoneLock {
    static boolean open(Context context){return context.databaseKey()!=null;}
    static boolean locked(Context context){return true;}
}
