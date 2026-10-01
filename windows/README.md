# Mininotes for Windows

A first Windows desktop build, using the existing Android app's Java notebook
and sharing logic. The interface is Swing; SQLite, preferences and protected
keys have Windows adapters. Android source files are not modified by this build.
The launcher, window, header and tray use the mobile notebook logo. Its exact
geometry and colours from Android's `res/drawable/ic_note.xml` are rendered by
`DesktopIcon.java`; packaging generates a multi-resolution Windows ICO.

## Build and run

Windows x64, JDK 17, and internet access for the first dependency download:

```powershell
./windows/build.ps1 -Package
```

The script runs the shared and Windows tests and makes
`dist/latest/Mininotes-Windows-0.2.010.zip` plus its SHA-256 checksum. Extract the whole
ZIP and run `Mininotes/Mininotes.exe`. Keep the runtime and app folders beside
the executable. No Java installation is required to run this bundle.

This is an unsigned portable preview, not an installer or a published release.
The bundled runtime makes it substantially larger than the Android APK.

For development:

```powershell
$env:JAVA_HOME = 'path-to-your-JDK-17'
./android/gradlew.bat -p windows test run
```

Local data lives in `%LOCALAPPDATA%/Mininotes`. Only one process may open that
notebook at a time. `--data-dir <folder>` selects a separate notebook for tests;
`--offline` prevents the desktop session from starting a node. A normal launch
starts its own Maxima node. **Settings → Keep listening** (*While the window is
closed*) keeps the node running in the system tray when the window closes.
Double-click the tray icon to reopen, or use its Exit command to stop Mininotes.
This does not start the app automatically when Windows starts.

**Troubleshooting.** What the PC does with sharing is written to a plain-text log,
`%LOCALAPPDATA%/Mininotes/logs/mininotes.log` (in the `--data-dir` folder when one
is given): one line per event with the time, the same tags as the phone's log
(`Mininotes/Post`, `Mininotes/Persons`, `Mininotes/Link`, `Mininotes/Drop`…) and
counts and states only - never a name, an address, a key or a word of a note, so
it can be sent to whoever helps. It grows to about 1 MB, is then kept as
`mininotes.1.log` and a new one starts: two files at most. Tests write no log
unless started with `-Dmininotes.log=<folder>`.

## Using the pad

Notes, profile names and settings save automatically. Profile connection refreshes
preserve the scroll position and leave address selections alone when unchanged.

Over a note's title, **← Home** and the collections it is in; under it, where it
stands, in marks rather than words: its sync mark, then a round for each person it
reaches, tinted by where they stand, and while it is being saved or sent a few
words beside them. Click the mark to send what is waiting or see who has it, and a
round to see who and where. A file still going keeps the mark amber; one too big to
travel never holds it back. Only the other device's answer clears the mark, never a
relay taking the note, and it is the last known shared state, not a guarantee that
an offline device has no newer edits.

**Home** fills the window, as a phone's home screen fills the phone (see
`docs/HOME.md`): your collections and notes as icons, in your order, then the
files other devices sent you, marked *new* until opened. **+** at the bottom
right makes a note or a collection; the search field under the icons finds
notes, collections and files; the button beside it (or Ctrl+Tab) shows the
notes and collections you have open, with **Close all**; the dock under it holds
your favourites. A collection opens as a card over Home - collections go inside
collections, as deep as you like - and a note fills the window, with **← Home**
(or Esc) to go back. Drag an icon onto a note to put the two in a new collection,
onto a collection to put it in, into an empty cell to put it there (icons stay where you put them, gaps and all), onto the dock to make
it a favourite, onto the Archive or the Bin to put it away there. Home is pages in every
direction, each as big as the window: the mouse wheel turns them up and down, Shift with
it (or a touchpad) sideways, and Page Up / Page Down; a double-click on empty room goes
back to the main page, where the favourites and the search are; Ctrl with the wheel shows
every page at once, where a page can be dragged to another place, or onto another page to
change places with it. Carry an icon to the left or right edge and hold it there to take it
to the page beyond - that is how a new page comes to be; carried above the rows or below
them, it goes to the page above or below at once. The dot before the version is green on
the newest version, yellow when a newer one is out. Right-click is everything about what you
click: on Home, its colour, the text size, and whether the favourites and the search show. The archive and the bin are icons on Home,
each with how many things wait in it, opening as a card of what is in it (Settings → Window can put them
back in the ⋯ menu). Right-click, or the Menu key, gives any icon's menu; Tab and the
arrows move between icons, Enter opens, F2 renames, Delete bins. **⋯ → Tree**
(or Ctrl+B) shows everything as a tree beside Home.

Write on the ruled page; edits save automatically. Ctrl+N makes a note, Ctrl+F
focuses search, Ctrl+S saves immediately, and Ctrl+Z/Ctrl+Y undo/redo text edits.
The menu opens versions, manages attachments, archives or bins, restores, and
exports or adds backups. Import adds copies, leaving current notes in place. ZIP
backups carry local attachments and use Android's existing format. **Send
files…** in the menu, or files dropped from Explorer on the toolbar, send files
straight to a paired device in no note; files dropped on a note are kept with
it, on a collection with the collection, on Home on Home. Ctrl+V on a note keeps
a picture with it, as a messaging app does - a screenshot from Greenshot or the
Snipping Tool, a picture copied in a browser - and files copied in Explorer too;
words pasted stay words. **⋯ → Sent files**
lists what went and what is still coming (see `docs/SHARING.md`, *Sending
files*).

Select a note or collection and choose **Share**. Show the code to the
phone's Mininotes scanner, then approve the phone's request on this PC. To accept
a phone's offer, use **From another device**: start a webcam, open a QR image,
or paste an image or sharing link from the clipboard. Camera access starts only
when you press Start camera; frames are not recorded. Windows camera permissions
must allow desktop apps. Pasted links and saved images ask you to compare the
same six-digit verification code on both devices, as mobile does.

**Profile**, also in the top bar, contains the editable device name, personal
pairing QR, copyable live Maxima address, address-only QR, permanent address,
relay connection status, Reconnect, People, the lock and backup actions.
**Connect my other device** offers your whole pad. Per-item sync timing is set on collections
or notes.
It refreshes the address and QR as the node changes relays. An offline session
still lets you change your name and manage local data.

**Share → Add someone** chooses an already-paired person or opens the scanner.
Choose Can read, Can write or Admin. People also shows addresses and whether a
device is yours. Share a collection to include future collections and notes. Roles,
pause, sync delay and manual sync use the existing protocol. Windows does not
yet register `mininotes://` as a system protocol handler; paste links in the app.

## Boundaries

- What was checked, on which build, is recorded before each release in a log the
  maintainer keeps privately. Matching formats alone do not prove live delivery.
- Notes are unencrypted on disk until the notebook is locked (the lock in the top
  bar): then the notebook and its files are encrypted, and open with the password,
  Windows Hello or the twelve recovery words. Device signing/agreement keys and
  preferences (including the node seed) are protected with current-user Windows
  DPAPI. They cannot be restored by copying those files to another Windows account.
- Files kept with a note or a collection are included in backups, and go with it
  to whoever it is shared with, up to 16 MB each (see `docs/SHARING.md`). This
  build has no dark paper, and differs from the phone where `docs/PARITY.md` says.
- Local input follows the existing backup limits (24,000 characters per note).
  A save failure leaves the writing on screen and blocks navigation/exit; copy
  that text somewhere safe if a storage problem cannot be corrected.

## Maintenance

`build.gradle` generates shared sources into `build/generated`: pure Java classes
are copied verbatim; `NoteStore`, `Post` and `Node` receive platform-name
substitutions. The small API in `desktop/platform` implements only the operations
these classes need. Compilation fails when new platform APIs require attention.
Do not edit generated files or fork the wire formats. JDBC transactions hold a
reentrant connection lock through their entire lifetime; cursors are detached
so a query inside a cursor loop is safe. Failed and nested transactions roll back.

Keep dependency versions pinned and preserve the bundled license notices.
Packaging uses JDK `jpackage --type app-image` and ships its runtime/legal tree.
Open an issue before changing protocol, storage or release behavior (see ../CONTRIBUTING.md).
