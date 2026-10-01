# Home: notes and collections laid out like a phone's desktop

Status: design, agreed 2026-09-30. Step 0 done 2026-09-30 (0.1 frozen, backed up, going back tested). Step 1
(0.2.001) built, installed on all three devices, seen on Graphene; step 2 (0.2.002) built, not yet on a
phone; step 3 (0.2.003) built; step 4 (0.2.004) built; step 5 (0.2.005) built (see each step); decision 41, the archive
and the bin on Home, built in 0.2.006 (see *After step 5*). It is the whole of the 0.2 line; 0.1 ends at 0.1.041, kept
whole and installable.

## Where it stands

Mininotes has three fixed levels: a collection holds books, a book holds notes, and only a note holds
files. Every screen, every sharing rule and every message names one of the three. That ladder is the only
thing left that treats the levels differently: the four roles, the marks, the writing colours, drag and
drop and the menus all already act on "a thing", whatever it is called.

The owner's ask (2026-09-30): make the app's home the same as an Android phone's desktop. Icons on a grid;
a plain **+**; drag a note onto a note to make a collection; drag a collection into a collection; a
collection that can hold files and needs no note; favourites in a dock at the bottom; search at the
bottom; open things flicked through like open apps, with *Close all*; every note with an icon of its own,
or a picture; the PC the same screen, with the tree as an option. The Drop box goes: every collection is
one.

## The goal

Two kinds of thing, **note** and **collection**, nested as deep as anybody likes. Files at any level. Home
is itself a collection. Sharing and roles unchanged in meaning: share any thing, everything under it goes
with it. Both apps show the same Home.

## The design

### Things

- A **note** is what it is today: a title, ruled paper, files, versions, who wrote what.
- A **collection** holds notes, collections and files, in an order the owner sets. It needs none of them.
  It has a name, a tint, an icon or a picture; with none of its own it shows a mini-grid of what is inside,
  as the phone's tiles already do.
- **Home** is the collection everything is in. It is not shown as a thing; it is the screen.
- **Favourites** is a flag on a thing plus a place in the dock.
- The words: *note*, *collection*, *Home*. "Book" and "Drop box" leave the wording; "Inbox", "Archive" and
  "Bin" stay as places, reached from the ⋮ menu as today.

### The Home screen (both apps)

- A grid of icons that scrolls down (decision 1: no side-swiped pages). Order is the owner's, by drag.
- An unlabelled **+** at the bottom right. It offers *Note* and *Collection*. A new note opens; a new
  collection opens into its name.
- Tap a collection: a pop-up over the grid with its contents (its own grid, its files among them, its own
  **+**). Tap a note: it opens. Back, or tapping outside, closes the pop-up.
- Drag a note onto a note: a new collection holding both, named at once ("Untitled" until typed over).
  Drag anything onto a collection: it goes in. Drag anything out of a pop-up onto the grid behind: it comes
  up a level. Long press or right-click: the thing's menu, as today.
- The **dock**: the favourites, at the bottom, 5 on the phone, as many as fit on the PC (decision 3). More
  favourites than that live in a *Favourites* collection that is always first on Home and cannot be
  dragged, deleted or renamed. The star in a thing's menu puts it in the dock when there is room, else in
  Favourites.
- The **search bar** sits above the dock. It finds notes, collections and files by name and words.
- The **overview**: swipe up from the dock (phone), or the button beside the search bar / Ctrl+Tab (PC).
  Cards of the open notes and collections, flicked through sideways, newest first. Swipe a card away to
  close it; **Close all** closes them all. It replaces the PC's tab bar (decision 2: on the PC a note fills
  the window; the tree is an optional panel beside it, off by default, remembered).
- Marks, badges and people rounds are where they are today: the mark on a tile's corner, the rounds under
  a note's title, the count on a collection with new arrivals.

### Icons and pictures

- The set is **Lucide** (ISC licence, about 1,500 icons, single stroke, 24-unit grid; decision 4). It
  travels as path data in a generated Java class, drawn with `android.graphics.Path` and
  `java.awt.geom.Path2D`, so neither app gains a library. Its licence goes into NOTICE and About.
- An icon is named by its Lucide name and drawn in the thing's tint on a paper-coloured round square; the
  default for a note is `sticky-note`, for a collection the mini-grid.
- A **picture**: the owner picks an image; it is cut square and kept as a thumbnail of at most 32 KB
  (WebP where the platform has it, else PNG, 192 px). It travels with the thing, as its name does.
- The icon picker: a searchable grid of the set with the tints along the top and *Choose a picture…* at
  the end. The same picker on both apps.

### Files at every level

- A file can be kept with any thing. It shows as an icon among the thing's contents, with its kind's
  glyph and name; tap opens it, its menu is the file menu of today (Open, Save a copy, Send to a device,
  Put in a note → now *Move to…*, Delete).
- Files travel with the thing's sharing, as a note's files do today, with the same 16 MB rule.
- **Received files** (what the Drop box held) land on Home with a *new* badge (decision 6). The sending
  side is the file menu's *Send to a device*, unchanged. The Drop box's offer/accept messages (receipts
  15–17) stay as they are; only where the files land changes.

### Sharing

- Share any note or collection. The audience of a thing is the union of the rules on it and on every
  collection above it up to Home. Home itself is shareable as "everything on this device", which is what
  the library rule already means.
- The four roles (docs/SHARING.md) apply at the shared thing, unchanged.
- Someone you share a collection with sees it on their Home, with everything under it, and it fills in as
  things arrive.

### The PC

The same screen. Home fills the window; a note opens over it, filling the window, with the overview to get
back; a collection opens as a pop-up. The tree is *View → Tree*, a panel on the left, off by default. The
toolbar keeps *Profile*, *Share* and the ⋯ menu; *New note* goes, since **+** does it. Right-click,
keyboard reach (Tab between icons, Enter opens, F2 renames, Delete bins) and Explorer drag-and-drop work
on every icon, tile and pop-up, using the handlers built in 0.1.039–0.1.041.

## What changes underneath

### The notebook (schema step 30, additive)

- New table `things(id, parent, kind, name, icon, image, tint, rung, ordinal, favourite, dock, made,
  updated, gone)`. Every note and every collection has a row; a note's `id` is its `notes.id`, and its
  writing stays in `notes`. `parent` is a thing id or `HOME`.
- `held.note` keeps its name and now holds any thing id. `shares.target` likewise; `shares.scope` keeps
  its old values for rows that came from 0.1 devices and gains `THING` for new ones.
- Migration from step 29: each collection → a thing under `HOME`; each book → a thing under its
  collection; each note → a thing under its book; favourites → `favourite=1` in their order, the first five
  into the dock; each Drop box file → a `held` row on `HOME`, marked new; tints and rungs carried over;
  binned and archived things keep their flags. `collections` and `books` are left in place and no longer
  read. Nothing is deleted.
- The migration runs, in a test, against copies of all three real notebooks before it runs on any device
  (see *Step 1*): counts of things, files, shares and favourites before and after must agree.

### Messages

- **Parcel** (`MNB1`): a note's `collection/collectionName/book/bookName` stay filled for older readers
  (the top two ancestors, or the nearest ones). A new optional tail `path` carries the full ancestry:
  for each ancestor from Home down, `id, name, icon, tint, ordinal`, and for the thing itself `icon,
  image`. Older builds skip a tail they do not know, as they skip `history` today.
- **Collections travel on their own.** Today only a note is sent; a book or collection exists on the far
  side only through the notes in it. A collection with files and no notes must travel by itself: a new
  message `MNC1` (a thing's row plus its `path`, sealed and receipted like a note, revisioned by the
  same clock). Files on a collection are listed and fetched exactly as a note's are, with the collection
  id where the note id goes.
- **Receipts**: `TREE=22` (this build knows about trees; said in the hello exchange as `PERSONS` is).
  A device that never says it is a 0.1 device.
- **0.1 devices** (decision 5): a 0.2 device takes in everything a 0.1 device sends. It sends a 0.1
  device only what fits in three levels: a thing at depth 1 goes as a collection, depth 2 as a book,
  a note under them as a note. Anything deeper, and any collection's own files, wait with the words
  "*Name* needs to update Mininotes to receive this" in the mark's box (a new `Unsent` line), and go the
  moment the device says `TREE`.

### The apps

- **Android.** `MainActivity` keeps the note page and everything about writing, files, recording, marks
  and people. The level screens (`draw`, tiles, cards, list, the Drop box screen, the tree view) are
  replaced by new classes: `HomeScreen` (grid, +, dock, search), `Folder` (pop-up), `Overview`,
  `IconPicker`, `Dock`. The tree view stays as a sheet from the ⋮ menu.
- **Windows.** `DesktopShelves`, `DesktopDrops`, `DesktopTabs` give way to `DesktopHome`,
  `DesktopFolder`, `DesktopOverview`, `DesktopIconPicker`; `DesktopTree` becomes the optional panel.
  `Desktop` keeps the page, the status bar, the lock, the update check and the node.
- **Shared, Android-free, tested:** `Things` (the tree: ancestry, audience, depth, moves, the three-level
  view for old devices, dock rules), `Icons` (the generated set and the name lookup), `Thumb` (the
  square thumbnail's size rules), `Overview` (the open list and its order). They join `portable` in
  windows/build.gradle.

## The steps

Each step is a version on all three devices before the next starts; each has a verification entry, in a
log the maintainer keeps privately, saying what was seen and what was not.
Versions are 0.2.NNN with versionCode 2000+NNN, both apps together, as the 0.1 rule had it.

### Step 0 - freeze 0.1

1. Commit the tree as it stands, push to the private archive, tag `v0.1.041-final` there.
2. Keep `dist/latest/Mininotes-Android-0.1.041-debug.apk` and `Mininotes-Windows-0.1.041.zip` (with
   checksums) in `dist/archive/0.1.041-final/`.
3. Back up both phones' app files, file by file with sizes checked, and copy the PC's data folder
   (`%LOCALAPPDATA%\Mininotes`, with the app closed) to a backup folder.
4. Write down, in this file, how to go back: install the archived 0.1.041 and put the backed-up files
   back. Test that on the emulator with a copy of a phone backup before any 0.2 build is installed.

#### What was done, 2026-09-30

- The tree as it stood committed, and tagged `v0.1.041-final` on the private archive; its two builds kept in
  `dist/archive/0.1.041-final/` with their checksums, checked against the files.
- Every device's app files backed up first, file by file with every size checked. Two of the three notebooks
  are locked, encrypted with a key that never leaves the device, so only an unlocked phone's copy can be
  opened elsewhere. See *Step 1* for what that means for testing the migration.
- **Going back, tested** on an emulator kept offline and read-only: the archived 0.1.041 installed, a phone's
  backup put back file by file, and the notebook it opened counted the same as the backup in every table.

### Step 1 - the model (0.2.001)

The tree, the migration, the messages, and the old screens running on the new model so that nothing
visible changes yet except that books show as collections inside collections. Files at every level in
the model only. All of `Things`, the schema step, the parcel tail, `MNC1`, `TREE`, the three-level view
and the migration, with tests. The migration proven on copies of the three real notebooks (counts only,
no note text printed or kept). Installed and verified: every note, file, share, favourite and mark is
where it was; sync between the three still works; a 0.1 device (the emulator on the archived APK) still
exchanges notes with a 0.2 device.

#### How it is built (written 2026-09-30, before building)

- **The notebook, schema 30.** `things` holds every collection, at any depth: `id, parent, kind, name,
  icon, image, tint, ordinal, favourite, dock, archived, binned, theirs, origin, pause, revision, made,
  updated, gone`. Collections become rows under `HOME`, books rows under their collection, with their ids,
  names, colours, places, favourites, bin and archive flags, origins and waits. A note's place in the tree
  stays in its own row - `notes.book` is its parent (a collection's id, or `HOME`), `notes.place` its order,
  `notes.pinned` its favourite - and notes gain `icon`, `image` and `dock` (decision 8). The first five
  favourites on the shelves go into the dock in the order the Favourites place listed them. Files kept
  with a book say `collection` from now on. `collections` and `books` stay, unread and unwritten.
- **The migration checks itself.** Inside the upgrade's transaction it counts, before and after:
  containers (collections + books = things), notes, files, shares, favourites, and what is binned and
  archived; if any differ it throws, the transaction is rolled back, and the notebook is left at 29 for
  0.1.041 to open (decision 11). The same counts are what the test on the real notebooks prints.
- **`Things`** (Android-free, tested): the ancestry of a thing from Home down, its depth, whether a move
  would put a collection inside itself, the three-level view (a collection at depth 1 is a 0.1
  collection, at depth 2 a 0.1 book; a note fits only at depth 3), the parcel's two old shelf fields,
  the dock's first fill, and the envelope id of a thing that is not a note.
- **Sharing on paths.** A rule reaches a thing when it is the library rule or its target is the thing or
  any collection above it. `Sharing.audience`, `standing` and `moving` take the path; the three-argument
  forms stay for what still calls them. New rules on a collection are `THING`, on a note `PAGE`; a thing
  keeps whichever scope its rows already have, so one person is never on it twice (decision 12).
- **The parcel's `path` tail** (`MNP1`, after the history, which is written - empty if need be - so an
  older reader stops before it): for each collection above the note from Home down, `id, name, icon,
  tint, ordinal`, then the note's own `icon` and `image`. A reader that has it builds what is missing
  from the top down and moves nothing already here; one that has not uses the old two fields, which
  carry the nearest two collections (decision 15).
- **`MNC1`** (`Carton`): a collection on its own - its id, name, icon, tint, image, path, who has it and
  its files list - sealed and answered like a note, under the envelope id `Things` gives it, and sent
  only to devices that have said `TREE`. Step 1 sends one for a shared collection with no note under it.
- **`TREE` (receipt 22)**, said once a run to every paired device that has shown it answers, as `PERSONS`
  is, and written down by key when heard. A device that has not said it is a 0.1 device: it is sent only
  notes that fit three levels (every note does, after the migration), each with the old fields; anything
  else waits with *"Name needs to update Mininotes to receive this"* and goes when `TREE` is heard.
- **The old screens.** `BOOK` is no longer a kind the notebook hands out: every collection is a
  `COLLECTION`, and a collection's inside is its collections and then its notes. A collection at depth
  1 is drawn as a collection was (+ makes a collection in it), a deeper one as a book was (+ makes a
  note), and either shows the other kind too if it holds it (decision 13). "Book" becomes "collection"
  in every word the apps say. Moving: a note into any collection; a collection into Home or into any
  collection that is not inside it (decision 14). The tree is walked to any depth.

#### What was built, 2026-09-30 (0.2.001)

- **Model**: `Things`, schema step 30 with its self-check (`NoteStore.onUpgrade`), `Sharing` on paths and
  `Scope.THING`, `Outbox.Page.above`, the parcel's `MNP1` path tail, `Carton` (`MNC1`), `Receipt.TREE`,
  `Unsent.Why.NEEDS_UPDATE`, and every collections/books query in `NoteStore` moved to `things` (collections
  of any depth; `inside`, `wholeTree`, `places`, moving with `Things.mayGoInto`, bin/archive/restore/erase
  through the whole subtree, favourites and the dock's first fill, backups that carry `things` and still
  read a 0.1 backup). `Post`: the path on every parcel, the three-level view for devices that have not said
  `TREE` (`waitsForTrees`, `scopeSaid`), `TREE` said and heard, cartons sent for shared collections with no
  note under them and taken in, leave/remove said by depth.
- **Phone** (`MainActivity`): the trail follows real ancestry (`trailTo`), + makes a collection at the top
  and one down and a note deeper (`addsCollections`), moving and merging by drag on paths, a new note goes
  into the deepest writable collection on the trail, the tree indented to any depth, and no "book" left in
  what it says. **PC** (`Desktop*`): the same rules for + (`DesktopShelves.makesNote`), Move to… and
  dragging on `Things.mayGoInto`, the share box finding rules along the path (`Desktop.reaching`), marks to
  any depth, the line over a note's title naming every collection above it, and no "book" left.
- **Tests**: 621 on Android (lint 0 errors, the same 27 warnings), 755 on the PC; new `ThingsTest`,
  `ParcelPathTest`, `CartonTest`, `SharingPathTest`, `DesktopThingsTest`, `DesktopDepthTest`, a rewritten
  `DesktopMovingTest`. `MigrationProbe` (test sources) runs the step on a copy of a real notebook and
  prints counts only.
- **The migration on the real notebooks, before any device**: Graphene's copy (step 0's and a fresh one):
  every count agreed (11 collections and books, 9 notes, 15 files, 11 shares, 1 favourite, 2 binned), 4
  collections on Home and 7 one down, nothing orphaned, the favourite docked, the one file kept with a book
  now kept with that collection. The Pro's and the PC's current notebooks are locked and cannot be opened
  off their devices (decision 11); the Pro's last plain copy (28 September, schema 27) went through the same
  step with its check passing. A dry run of the PC build on a copy of the PC's folder waited at the copy's
  Windows Hello question, which nobody was there to answer, and was closed.
- **Going back from a migrated notebook**, on the emulator (Pixel 6 AVD, read-only, offline): Graphene's
  backup under 0.1.041, then 0.2.001 installed over it and opened (it logged the move with every count
  intact), then 0.1.041 installed back with `-d` and the backup's files put back: the notebook it opened
  was, count for count, the backup at schema 29.

### Step 2 - Home on the phone (0.2.002)

`HomeScreen`, `Folder`, `Dock`, search at the bottom, **+**, drag to merge and nest, the overview with
*Close all*, received files on Home, the Drop box screen gone. Icons: the default glyphs and tints only.
Verified on both phones with the owner's real layout migrated: the grid shows what the tiles showed, in
the same order.

#### How it is built (written 2026-09-30, before building)

- **Home** replaces the *All collections* screen; the note page, its menus and every box stay as they are.
  Top to bottom: the bar as today (name and version, the mark, ⋮); the grid - Home's collections and notes
  in the owner's order, then received files, each an icon with its name under it, four across (decision
  18); **+** at the bottom right; the search bar; the dock. A collection's icon is the mini-grid of what is
  inside it, a note's the paper tile with the note glyph, a file's its kind's glyph; each in its tint, the
  mark on the corner, a *new* badge on a received file.
- **+** offers *Note* and *Collection* where you are (Home or the open pop-up). A note opens at once; a
  collection is made as *Untitled* and its pop-up opens with the name ready to type over.
- **Folder** (the pop-up): a rounded card over the dimmed grid - the collection's name (tap to rename), its
  mark and its ⋮, then its grid (collections, notes, its own files) and its own **+**. A collection inside
  it opens in the same card with ‹ to go back up (decision 21); back goes up one level, a tap outside closes
  the card.
- **Dragging**: a long press opens the thing's menu as today; moving while still holding picks it up. On a
  note: a new collection *Untitled* holding both, name ready to type over. On a collection: into it. Between
  icons: a new place in the order. Out of a pop-up onto the dimmed grid: one level up. Onto the dock: a
  favourite.
- **Dock**: up to five favourites (`dock` 1-5), icons only, each named for anybody who cannot see it; the
  rest of the favourites in a *Favourites* collection that is first on Home whenever there are more than
  five, and cannot be dragged, renamed or deleted. An empty dock says *Favourites* quietly. A short handle
  above it says it can be swiped up (decision 19).
- **Overview**: swiping up from the dock (or the handle) shows the open notes and collections as cards,
  newest first, sideways; swipe a card up to close it; **Close all** is the one button. `Overview` (pure,
  shared with the PC) keeps the list - up to twelve, newest first, kept across restarts (decision 22).
- **Search** at the foot finds notes, collections and files (received ones and those kept with anything)
  by name and words; tapping a result opens it where it lives.
- **Received files on Home** (schema 31, additive): files gain `fresh`; every received file that is here
  gets a `files` row on Home, and its `transferred` row is marked `moved` rather than deleted; from now on a
  file that arrives lands the same way, *new* until opened. The Drop box screen and tile go; the offer box
  for files from somebody else's device stays; what this device sent to others is listed under ⋮ → *Sent
  files* (decision 20).
- Classes: `HomeScreen`, `Folder`, `Dock`, `OverviewScreen` (Android), `Overview` (pure, tested); the
  helpers they need from `MainActivity` are opened to the package, never copied.

#### What was built, 2026-09-30 (0.2.002)

- **Model** (shared): schema 31 - `files.fresh`, `transferred.moved`, every received file here given a
  `files` row on Home under its own id (bytes not copied), self-checked like step 30; arriving files land
  on Home in the same transaction; `contents(id)` (collections, notes, then files, in one order);
  `Branch.Kind.FILE` and `Branch.fresh`; the dock (`dock`, `toDock`, `outOfDock`, `favouritesBeyondDock`;
  pure `Things.intoDock/outOfDock`); `lookingEverywhere` (notes, collections and files); `addCollectionIn`,
  `moveInto` (any kind, files too), `order(lines)`, `merge`; `sentFiles`, `sendingOf`; backups carry Home's
  files. `Overview` (pure, shared). The PC's Drop box view lists the files on Home until step 3.
- **Phone**: `HomeScreen` (bar, grid four across, **+** with Note / Collection, search bar, received files
  with *new*), `Folder` (the card, ‹ up, tap outside closes, its own **+**, files dropped in from other
  apps), `Dock` (five, handle, swipe up, drop to dock, *Remove from the dock*), `OverviewScreen` (cards, swipe
  up to close, **Close all**), `Grid` (pure rules: columns for a width, what a drop does, dock slots, swipe
  up). The Drop box screen and the cards/list switch are gone; ⋮ → *Sent files* lists what went and what is
  still coming. Back from a note returns to where it was opened; back on Home leaves the app.
- **Tests**: 640 on Android (lint 0 errors, the same 27 warnings), 785 on the PC.
- **Not seen on a screen**: neither phone could be reached (asleep off the network from about 09:00), and
  the emulator could not keep its system running in the memory this PC had free. See the verification log.

### Step 3 - Home on the PC (0.2.003)

`DesktopHome`, `DesktopFolder`, `DesktopOverview`, the optional tree, keyboard reach, right-click and
Explorer drops on every icon. The tab bar and the Drop box view go. Gallery pictures of Home, a pop-up,
the overview and the tree panel.

#### How it is built (written 2026-09-30, before building)

- **Home** (`DesktopHome`) fills the window under the toolbar: the same `contents(HOME)` as the phone, as
  icons in as many columns as fit (the icon a rounded square with the thing's glyph, mini-grid or file kind,
  its tint, its mark on the corner, *new* on a received file, the name under it), **+** at the bottom right
  (Note / Collection, as on the phone), then the search field with the overview button beside it, then the
  dock (as many favourites as fit, decision 3), then the status bar (decision 25).
- **A note** opens filling the window, with **←** *Home* at the left of its bar (decision 24); Esc does the
  same. **A collection** opens as `DesktopFolder`, a rounded card over the dimmed Home, as on the phone: its
  name (F2 or a click renames), mark, ⋯, grid and **+**; a collection inside it opens in the same card with ‹.
- **The overview** (`DesktopOverview`) replaces the tab bar: the overview button or Ctrl+Tab shows the open
  notes and collections as cards, newest first (`Overview`, shared); click to go, × or middle-click to close,
  **Close all**; Ctrl+Tab again moves along the cards, letting go of Ctrl opens the one chosen.
- **The tree** is an optional panel on the left: ⋯ → *Tree* (ticked while shown) and the same switch in
  Settings → Window, off by default, remembered (decision 26); selecting in it opens what is selected.
- **Reach**: Tab and the arrows move between icons, Enter opens, F2 renames, Delete bins, the Menu key or
  Shift+F10 opens the menu; Ctrl+N a note, Ctrl+F the search. Right-click on every icon, card and dock icon
  gives the thing's menu, in the phone's order. Dragging an icon does what it does on the phone (`Grid`);
  files from Explorer dropped on a note are kept with it, on a collection or its card with the collection,
  on Home's grid on Home.
- **Gone**: the tab bar (`DesktopTabs`), the Drop box view (received files are on Home; ⋯ → *Sent files*
  lists what went and what is coming, as on the phone), the *New note* button (**+** does it).
- Gallery pictures: Home, a pop-up, a pop-up inside a pop-up, the overview, the tree panel, the dock with
  more favourites than fit, a received file, a note with ← Home.

#### What was built, 2026-09-30 (0.2.003)

- **PC**: `DesktopHome` (the grid in as many columns as fit, icons with marks and *new*, **+**, the search
  field with the overview button, the dock, keyboard reach, right-click menus in the phone's order,
  dragging on `Grid`'s rules, Explorer drops kept where they land), `DesktopFolder` (the card, ‹ up, rename
  in place, its own **+**; Home's **+** hidden while a card is open), `DesktopOverview` (cards, × and
  middle-click, **Close all**, Ctrl+Tab along the cards; 0.1's tabs read once), the tree as an optional
  panel (⋯ → *Tree*, Ctrl+B, Settings → Window). `DesktopShelves` and `DesktopTabs` are gone, and with them
  the Drop box view and the New note button; ⋯ → *Sent files* as on the phone. A note has **←** *Home* and
  Esc; Ctrl+W closes back to Home.
- **Phone**: Home's **+** is hidden while a card is open, as on the PC (it drew above the card's dimming).
- **Found and fixed on the way**: showing the tree emptied Home's grid until something else repainted it
  (the dock's resize refilled the grid and left placing it to a layout pass that had not run);
  `DesktopHomeTreeTest` catches it.
- **Tests**: 792 on the PC, 640 on the phone (lint unchanged). Gallery pictures of Home, the card, a card in
  a card, the overview, the tree panel, the dock with more favourites than fit, received files, a note with
  ← Home and the menus, looked at by the agent that built them and by me (Home, the card in a card, the
  overview, the tree panel, the note).

### Step 4 - icons, pictures and files everywhere (0.2.004)

The Lucide set generated into `Icons`, the picker on both apps, pictures as thumbnails that travel,
files on collections travelling and fetched, the "needs to update" line. Verified with a picture set on
one phone appearing on the other two, and a file kept on a collection arriving on the PC.

#### How it is built (written 2026-09-30, before building)

- **The set**: `Icons` (pure, shared) reads Lucide 1.49.0 from `icons.txt` beside the class - one line an
  icon: its name, the words it is found by, and every shape as SVG path data on the 24-unit grid - and draws
  it into a `Pen` (moves, lines, cubics, closes; arcs cut into quarter turns) that each app replays onto its
  own path type, stroked 2 units wide with round ends. `tools/icons/make_icons.py` makes `icons.txt` from
  the npm package (decision 29). Lucide's licence goes into NOTICE and About.
- **A picture**: `Thumb` (pure) - the square from the middle, the tries (192 px down to 64, falling
  quality) until one is at most 32 KB, and the check that what arrives is a WebP, PNG or JPEG that fits. The
  phone encodes WebP, the PC PNG. Kept as Base64 in the thing's own row (decision 30).
- **Drawing**: a note's icon is its glyph (the note glyph until chosen) in its tint on a paper round square;
  a collection shows its icon if it has one, else the mini-grid; a picture fills the round square. Home, a
  card, the dock, the overview, the tree and search results all draw them, on both apps.
- **The picker** (both apps, the same): the thing's menu gains *Icon…* right after *Colour* (decision 33); a
  box with the tints along the top, a search field, *Default* first, then the set in Lucide's order (found by
  name and words), and *Choose a picture…* at the end (decision 32).
- **Travelling**: a note's icon and picture ride in its parcel's path tail and are taken with the note; a
  collection's icon rides in every path through it and is followed like its name; a collection's picture,
  icon and files ride in its carton, which now goes for every shared collection whose revision moved, not
  only one with no note in it (decision 31). Files kept with a collection are listed, published and fetched
  as a note's are, with the collection's id where the note's goes.
- **Needs to update**: what a 0.1 device cannot take (a note that does not fit three levels, a carton, a
  collection's files) is counted as waiting for it, and the mark's box says *"Name needs to update
  Mininotes to receive this"* on both apps until it says `TREE`.

#### What was built, 2026-09-30 (0.2.004)

- **Shared**: `Icons` and `icons.txt` (Lucide 1.49.0, 1,857 icons; `tools/icons/make_icons.py`), `Thumb`, and
  `Looks` (which face a thing shows, the ink, the picker's search and *Default*, the encode loop); the store's
  `iconOf/imageOf/setIcon/setImage` and `dress`, a note's look in its parcel, a collection's in its path and
  carton, collection files published, listed, fetched and answered like a note's, and "needs to update"
  counted for 0.1 devices (decisions 29-38). NOTICE and THIRD-PARTY.txt carry Lucide's licence (ISC, with the
  Feather MIT notice) and, on the PC, TwelveMonkeys' (BSD-3).
- **Phone**: `IconPen`, `IconFace`, `IconPicker`: faces on Home, cards, the dock, the overview, search, the
  tree and the note's bar; *Icon…* after Colour and *Remove the picture*; pictures from the phone's picker,
  cut square, EXIF-turned, WebP until it fits 32 KB, with the busy strip saying so; About names Lucide.
- **PC**: `DesktopIcons`, `DesktopIconPicker`: the same faces everywhere, the same picker (tints, search,
  *Default*, the set painted row by row, *Choose a picture…* or paste or drop), PNG pictures, WebP read.
- **Tests**: 666 on the phone (lint unchanged), 848 on the PC; gallery pictures of every face and the picker
  looked at by the agent that built them and by me (Home with icons and pictures, the picker searching).

### Step 5 - polish (0.2.005)

Whatever the four verification rounds turned up; docs/PARITY.md, docs/SHARING.md, README.md and the
website's words updated from "collections, books and notes" to "notes and collections"; the public
release only when the owner says so.

#### How it is built (written 2026-09-30)

- **Icons where you put them** (decision 39): `Layout` (pure, shared: `arrange`, `moveTo`, `under`), schema 32,
  `NoteStore.place(lines,cells)`, `Branch.cell` on every line `contents()` gives; the phone's Home and card and
  the PC's Home and card draw each icon in its cell with the empty cells between, show the empty cell under a
  carried icon as the place it will go, and write the cells with `place` when it is let go there.
- **From the step 4 rounds and the docs audit**: the phone's in-app scanner reachable from ⋮ (decision 40);
  *Connect my other device* reachable on the phone as on the PC; files dropped on the phone from another app
  aimed at the icon under the finger, as the PC does; the PC's *Remove from the dock* for every docked icon it
  shows, not only the first five; the icon set read off the UI thread at the phone's start.
- **Words**: SHARING.md, PARITY.md (a fresh audit, 0.2.004), README.md, android/README.md, windows/README.md;
  the website's one line about books, in Documents/mininotes-site, left uncommitted for the owner.

#### What was built, 2026-09-30 (0.2.005)

- **Icons stay where you put them** (decision 39, the owner's ask made during this step): `Layout` (shared, 7
  tests), schema 32 `cell` on notes, collections and files, `NoteStore.place`. Phone (`HomeScreen.lay`, `Laid`,
  `across`, `put`, `Folder`) and PC (`DesktopHome` `Icons.drawn`, `Carry.across`, `keepCells`, `nudge`): each
  icon in its cell with the gaps kept, a quiet outline where a carried icon will land, the empty row below the
  last icon; on the PC Ctrl+arrow moves the focused icon a cell, with Undo; the Favourites icon keeps a cell of
  its own too (in each device's settings). `Grid.letGo` decides what a drop does, for both apps.
- **From the rounds and the audit**: ⋮ → *Scan a code…* on the phone (decision 40); the phone's Profile gains
  *Connect my other device…*, *Scan a code…* and *People and devices*; files dropped on the phone from another
  app go to the note or collection under the finger, or into the empty cell; the PC's *Remove from the dock* on
  every icon its dock draws; the icon set read off the UI thread when the phone starts.
- **Words**: SHARING.md, PARITY.md (audit at 0.2.004, then the dock and placing rows), README.md,
  android/README.md, windows/README.md; the website's line about books changed in Documents/mininotes-site and
  left uncommitted for the owner.
- **Tests**: 677 on the phone (lint 0 errors, the same 27 warnings), 866 on the PC; pictures of a Home with a
  gap, a carried icon over its landing outline, a card with a gap, a carry out of a card onto Home, the dock with
  two taken out - looked at by the agent that built them and by me (the landing outline and the placed icon).
- **Not done, found on the way** (for a later round): leaving or being removed from a collection two or more
  deep tells nobody yet; the phone's tree view shows every mark as the empty ring; the PC's tree still reorders
  by `order`; Explorer drops on the PC's Home land in the first free cell, not the cell under the pointer.

### After step 5 - the archive and the bin on Home (0.2.006)

Decision 41, the owner's ask during step 5, built on 2026-10-01 at his go-ahead.

- **Both apps**: Home shows an *Archive* and a *Bin* icon (Lucide `archive` and `trash` on the outlined square the
  Favourites icon has), each with how many things wait in it on its corner - quiet, and nothing when empty. A tap or
  click opens a card of what is in it, listed one after another, with no **+**; a thing there offers *Put back* and, in
  the bin, *Delete for good*, in the archive *Move to the bin*; the bin's ⋮ or ⋯ has *Empty the bin*. A note or a
  collection carried onto the Archive is archived, onto the Bin it goes in the bin, by the same road as its menu (said,
  with Undo); a file onto the Bin gets its own *Delete* question. Settings → *Show the archive and the bin on Home*, on
  unless switched off; off, ⋮ → *Open the archive* / *Open the bin* on the phone and ⋯ → *Put away* on the PC come back,
  and while on they are not in those menus, so there is one way to each.
- **Shared rules** (`Grid`, `Layout`, tested on both): `Grid.place`, `Grid.carried`, `Onto.AWAY`, `LetGo.AWAY`;
  `Layout.home` arranges Home with its places. A place's cell is kept in each device's own settings
  (`favouritesCell`, `archiveCell`, `binCell`). A place with no cell yet goes **after the last icon**, never into a gap:
  the first build put them in the first free cells from the top, and on Graphene's real Home, whose first row was left
  empty on purpose, that put the archive and the bin at the top left - seen, fixed, and tested
  (`onAHomeWithGapsTheArchiveAndTheBinGoAfterTheLastIconNotIntoAGap`).
- **Decision 42**: the three places are carried to another empty cell of Home like every icon, and into nothing.
- **Also**: the archive's menu loses *Nothing to put away*, a row that only said there was nothing to do; a place's menu
  has no heading over nothing; *Put back* says "Put back" rather than "Back on the shelves".
- **Not done**: carrying a thing out of a collection's card onto the Archive or the Bin behind the dim (only things on
  Home's own grid can be let go on them); files dropped from another program onto the Bin are kept on Home, as onto the
  Favourites icon.

### After step 5 - one menu for a press and a right-click, and Home in pages (0.2.008)

Decisions 43-50, the owner's asks of 2026-10-01, built the same day.

- **Menus** (43, 44): a long press on the phone and a right-click on the PC give the thing's own menu, everything about
  it and nothing else; ⋮ and ⋯ give that and then the app's rows. Home's empty room, held or right-clicked: New note, New
  collection, the text size, Home's colour and its strength (new on the PC, where Home was plain paper), Undo, Sync now,
  Show favourites, Show search, All pages, Back to the main page. A card's empty room: New note, New collection, then the
  collection's menu. The PC's + lost its shadow, which its own square cut off (the owner's ask).
- **Pages** (45-50): `Layout.pages` (tested: a long Home becomes a column of pages, a page exists only where something
  stands, a smaller window moves icons on their own page and writes nothing, something new goes on the main page and the
  bin after its last icon, a move pins every icon on every page), schema 33 `page` beside `cell`, the places' pages in
  each device's settings (`favouritesPage`, `archivePage`, `binPage`). PC: `DesktopHome` turns by the wheel, Shift and
  the wheel, a drag of the empty room, Page Up / Page Down (Ctrl for sideways), a double-click or Ctrl+Home home; an icon
  held at an edge for two thirds of a second goes on to the page beyond, one page for each hold; dots at the foot;
  `DesktopPages` is every page zoomed out (Ctrl with the wheel, or the menu). Phone: `HomeScreen` takes a quick swipe
  as a turn - a finger held first is still the icon's - a double tap or back home, a pinch to every page (`HomePages`),
  the same edge hold, the same dots. On both, the favourites and the search show on the main page only, their room kept
  on the others so every page is one size, and what + makes on another page stands on that page.

### After step 5 - pages that slide, and pages carried (0.2.009)

Decisions 51-53, the owner's asks of 2026-10-01 after trying 0.2.008 on the Graphene ("not smooth ... a long lag between
the action and the effect"), built the same day.

- **Phone** (51): `HomePages` is gone. Every page is drawn in one world (`HomeScreen`: `pager`, `world`, `here`); a swipe
  moves it under the finger and a let-go finishes the slide, a pinch scales it live about the point between the fingers,
  and zoomed out each page is whole in its frame, the one in view outlined in the accent. Let go, a pinch settles on the
  page, on one page with the next ones beside it (0.6), or on every page at once (`allAt`: as big as fits, half a page's
  room round them for the places a carried page can go); *All pages* is the last. A tap goes into a page, back comes
  back. Nothing round the world clips it (`pager` and `world` both `setClipChildren(false)`): with the pager clipping, the
  world was cut to its own bounds and nothing beside the page in view was ever drawn - the lag the owner saw.
- **Pages carried** (52): `Layout.movePage` (tested: onto an empty place it moves, onto another page the two change
  places, the main page's place stays the main page). Phone: held still on a page zoomed out, it lifts and goes with the
  finger, the empty places round the pages dashed. PC: `DesktopPages` drags a page the same way; Esc cancels; Undo puts
  every icon back.
- **Dots** (53): `Layout.dotted` - the pages there are and the one in view, even empty, so a thing carried onto a new page
  shows that page at once. On the phone the filled dot slides with the pages.

### After step 5 - the pinch reaches Home, a carry turns the page up and down, the version's dot (0.2.010)

Decisions 54-55 and a fault, the owner's word on 0.2.009 the same night.

- **The pinch did nothing on the phone** ("pinching with 2 fingers doesn't work"): `MainActivity.dispatchTouchEvent`, which
  every touch passes first, took every two-finger gesture for the whole pad's zoom - which only ever makes things bigger -
  and passed nothing on. A gesture whose first finger lands on Home's pages, nothing open over them and the pad at its
  own size, is Home's now (`HomeScreen.pinchesAt`); anywhere else the pad's zoom is as it was.
- **Up and down at once** (54): `HomeScreen.edgeWatch`, `DesktopHome.Carry.atTheEdge`.
- **The dot** (55): `Update.standing` (tested), the phone's version line, the PC's version in the bar.

## Decisions

1. Home scrolls down; no side-swiped pages. (2026-09-30)
2. On the PC a note fills the window; the tree is an optional panel; the tab bar becomes the overview.
3. Dock: 5 on the phone, as many as fit on the PC; the rest in a *Favourites* collection.
4. Icons: Lucide, as path data, drawn by both apps; pictures as ≤32 KB square thumbnails that travel.
5. 0.1 devices keep receiving what fits in three levels and are told to update for the rest; no flattening.
6. Received files land on Home with a badge; there is no Received collection.
7. Books become collections inside their collection; nothing moves and nothing is deleted.
8. A note's place in the tree stays in its `notes` row (`book` is its parent, `place` its order, `pinned`
   its favourite); `things` holds the collections. So nothing about a note is written in two places, and
   every query that reads a note goes on reading it where it is. (2026-09-30, refines *The notebook*.)
9. The file rows are `files.note` / `files.held` (`held` is the PC's mailbox table, not the files'); they
   hold any thing's id, and rows that said `book` say `collection`. (2026-09-30)
10. The Drop box's files move to Home in step 2, when the Drop box screen goes, so no file is shown in two
    places while both exist. (2026-09-30)
11. The migration counts before and after inside its own transaction and rolls back on any difference.
    The Pro's and the PC's notebooks are locked with keys that never leave them, so they cannot be opened
    off the device; for them this check, on the device, is the test on the real notebook, after the same
    migration passed offline on Graphene's copy and on synthetic locked-shape notebooks. (2026-09-30)
12. New sharing rules: `THING` on a collection, `PAGE` on a note; a thing keeps the scope its rows already
    have. (2026-09-30)
13. In step 1 a collection at depth 1 looks and adds as a collection did, a deeper one as a book did; both
    show whatever they hold. (2026-09-30)
14. In step 1 a note moves into any collection, a collection into Home or any collection not inside it.
    (2026-09-30)
15. The parcel's old `collection` and `book` fields carry the nearest two collections above the note.
    (2026-09-30)
16. A collection's envelope id: its UUID's bytes, or the first 16 bytes of SHA-256 of any other id.
    (2026-09-30)
17. A collection's tint and order travel in the path and are used only when it is first made on the far
    side; its name is followed, as now. (2026-09-30)
18. Home is four icons across on a phone held upright, as a phone's own home screen is. (2026-09-30)
19. The dock has no labels, as a phone's does; each icon says its name to a screen reader, and a long press
    gives its menu. A short handle above the dock shows it can be swiped up. (2026-09-30)
20. With the Drop box gone, what this device sent is listed under ⋮ → *Sent files*, and a sending still
    speaks through the busy strip while it goes. (2026-09-30)
21. A collection opened inside a pop-up replaces the pop-up's contents, with ‹ to go back up; phones do not
    nest folders, and a stack of cards would hide the grid it belongs to. (2026-09-30)
22. The overview holds up to twelve open things, newest first, and keeps them across restarts. (2026-09-30)
23. Home and every pop-up are icon grids, as a phone's desktop is; the *cards or a list* switch leaves
    Settings, and ⋮ → *Tree view* is the list of everything. A collection's files are added from its ⋮
    (*Add a file…*) or by dropping files from another app on its pop-up, since the level screen's clip goes
    with the level screen. (2026-09-30)
24. On the PC a note has **←** *Home* at the left of its bar, as the phone's note page has its back arrow, and
    Esc does the same. (2026-09-30)
25. The PC's status bar stays at the very bottom, under the dock, where a window's status bar is.
    (2026-09-30; was *Not decided*.)
26. The PC's tree is ⋯ → *Tree*, ticked while it shows, and the same switch in Settings → Window; off by
    default and remembered. (2026-09-30)
27. Home is not offered in the Share box: sharing everything stays *Connect my other device* in Profile, as
    today; the Share box is for a note or a collection. (2026-09-30; was *Not decided*.)
28. The phone's pinch and a note's own text size are left as they are in 0.1.037 - a look closer that does
    not change the note's size. (2026-09-30; was *Not decided*.)
29. The set travels as a text resource beside `Icons` (`icons.txt`, 420 KB, 126 KB compressed), not as Java
    source: 1,857 icons of path data do not fit a class file's limits, and a resource is the same data, drawn
    the same way by both apps with no library. (2026-09-30, refines decision 4.)
30. A picture is kept as Base64 in its thing's own row, so it goes with the row everywhere the row goes -
    backups included. (2026-09-30)
31. A carton goes for a shared collection with no note in it (as in step 1), and for one with notes once it has
    something of its own to send - its revision moved (name, colour, icon, picture or files), or it keeps files
    or a picture - so its look and files reach everybody who has it without a carton, and a "needs to update"
    line, for every collection to every 0.1 device. (2026-09-30)
32. The picker: tints along the top, a search field, *Default* first, then the set, then *Choose a
    picture…*; one box, the same on both apps. (2026-09-30)
33. *Icon…* sits in a thing's menu right after *Colour*, on both apps. (2026-09-30)
34. Choosing an icon, or *Default*, takes a picture off; a picture keeps the icon under it for when it is
    removed. (2026-09-30)
35. A note's icon and picture that arrive are taken when the arriving revision is newer than the one here; at
    the same revision the one that sorts later wins, so two devices that changed it at once agree. A copy this
    device only reads takes them whenever the words are taken. Changing a note's look bumps its revision with
    the same words, so it goes. (2026-09-30)
36. A collection's name, icon and picture are followed from its owner, and - for the owner's own collections -
    from the owner's other devices too, so a picture set on the phone reaches the PC that made the collection.
    An older carton from the same device changes nothing. (2026-09-30)
37. Until every device is on 0.2.004, a device on 0.2.001-0.2.003 sends paths with no icons and cartons with no
    picture, which can clear a look on a 0.2.004 device; said here so it is not taken for a fault. (2026-09-30)
38. The PC reads the phone's WebP pictures with the TwelveMonkeys WebP reader (pure Java, BSD-3, about 580 KB,
    listed in THIRD-PARTY.txt), since Java cannot read WebP; the PC itself writes PNG. The one alternative -
    the phone writing JPEG - would drop the library but make every picture worse; say so if you prefer it.
    (2026-09-30)
39. **The owner's ask, 2026-09-30, during step 5: "make sure we can move the assets freely on the desktop, not that
    they have to be sorted one after the other."** Every icon on Home and in a card stays in the grid cell it is let
    go in, with empty cells left empty, as a phone's home screen keeps them (`Layout`, schema 32 `cell`, kept per
    device and never sent). A grid nobody has moved anything on yet is drawn as before, one after the other; at the
    first move every icon is pinned where it is drawn. Let go in an empty cell: it goes there. Let go on an icon: a
    note on a note makes a collection, anything on a collection goes in, as before. Something new, or moved in from
    another grid, takes the first free cell from the top. A grid narrower than the one a cell was chosen on moves that
    icon to the first free cell after its row.
40. On the phone, scanning somebody's code is ⋮ → *Scan a code…* (in the People section), since **+** makes only notes
    and collections; the camera still opens a code's link as before. (2026-09-30)
41. **The owner's ask, 2026-09-30, during step 5: "let's make the archives and the bin as collections on the desktop
    with an option to hide them if wished."** Home shows an *Archive* and a *Bin* icon (Lucide `archive` and
    `trash-2`), like the Favourites icon: in a cell of their own, which a move keeps; not renamed, deleted,
    shared or given another icon; each opens as a card of what is in it, where a tap offers *Put back* and, in
    the Bin, *Delete for good*, and the Bin card's ⋮ has *Empty the bin*. Letting go of a thing on the Archive
    archives it, on the Bin bins it - said in the status line or strip, with Undo, as the menu does. A count shows
    how many things are in each. Settings → *Show the archive and the bin on Home*, on unless switched off; with
    it off they are reached from ⋮ as before. Both apps. (0.2.006, built 2026-10-01: the bin wears Lucide `trash`, since
    the set this app carries, Lucide 1.49, has no `trash-2` - its `trash` is the bin with the two lines in it.)
42. **The places are carried like every other icon (0.2.006).** Favourites, Archive and Bin are picked up as any icon is
    and let go in another empty cell of Home, where each stays (decision 39's own words: *every* icon on Home stays in
    the cell it is let go in); let go on anything else, or on the dock, they go back. They go into nothing. Until now the
    Favourites icon could not be moved at all, which left it, and would have left the bin, wherever the grid first put
    it. Not asked for in so many words; the agent's call while building 41, so the owner may say otherwise. (2026-10-01)
43. **The owner's ask, 2026-10-01: "a long push on mobile should do the same as right click on the laptop, and right click
    should bring all the design settings for this element".** A long press on the phone and a right-click on the PC open
    the same menu: everything about the thing pressed and nothing else - its colour and their strength, its icon, its
    text size where it has one, Undo, and what is done to it. The app's own rows (Find, People, Backup, Settings,
    Profile, About…) are in ⋮ and ⋯ only, which hold the thing's menu and then them (the owner's choice).
44. **Home's own menu**, a long press or a right-click where there is no icon: *New note*, *New collection*; the text size
    of notes; Home's colour and its strength - the PC gains the colour Home has on the phone; *Undo*; *Sync now*; *Show
    favourites* and *Show search*, switches the same as in Settings. In a card's empty room, the same for that collection:
    *New note*, *New collection*, then its own menu.
45. **The owner's ask, 2026-10-01: Home is pages in every direction**, replacing decision 1. Home is a page the size of the
    screen; others lie up, down, left and right of it. Moved between by a swipe on the phone; on the PC by the mouse
    wheel (up and down), Shift with the wheel or a touchpad (sideways), dragging the empty room, or Page Up / Page Down.
    A double tap or double-click on empty room goes back to the centre page. Zoomed out - a pinch on the phone, Ctrl with
    the wheel or ⋯ → *All pages* on the PC - every page is seen at once, and a tap or click goes to one.
46. **A page other than the centre exists only while something stands on it** (the owner: "the other pages should be made
    active only by dragging an asset there"). An icon carried to the edge of the page and held there goes on to the page
    beyond it - a new one if there is none - and is let go there; a page left empty is gone. Nothing new is ever made on a
    page of its own accord: what + makes, and what arrives, takes the first free cell of the page in view, or of the
    centre page.
47. **Favourites and search are on the centre page only** (the owner's ask), and each can be hidden: Settings, and Home's
    own menu. Hidden, the favourites are still in the Favourites icon and search is ⋮ / ⋯ → *Search*.
48. **A page is what the screen holds.** On the phone, four across (decision 18) and as many rows as fit above the search
    and the dock; every page that size. On the PC, what the window holds (the owner's choice): a smaller window moves the
    icons that no longer fit to the next free cell on their page, without writing anything, so a bigger one puts them back.
49. **Pages on Home only** (the owner's choice): a collection's card keeps its one grid that scrolls down, as a phone's
    folders do.
50. **Where a thing stands** is its page and its cell on it, kept per device as its cell already is (schema 33, `page` on
    notes, collections and files; the places' in each device's settings). A cell kept before pages - one long grid -
    stands on the centre page while it fits there, and its rows below the page go to the pages below, so a long Home
    becomes a column of pages and nothing is lost.
51. **The owner's ask, 2026-10-01: pages move as a phone's own home screen's do** ("the page show up in full in a smaller
    frame so that we can clearly see the transitioning from one to the other"). On the phone the pages follow the finger
    and a pinch shrinks them as it goes, about the point between the fingers, each whole in its frame, with nothing
    between the hand and what is seen. Let go, it settles on the nearest of three: the page, one page with the next ones
    beside it, or every page at once (the owner: "I don't manage to get the whole view when I want to"). A swipe is taken
    when it goes about a fifth of the way or is quick; else the page goes back.
52. **The owner's ask, 2026-10-01: zoomed out, a whole page is carried** ("grab and move the pages, just as it is
    possible with the other assets"). Let go on an empty place round the pages, it moves there; on another page, the two
    change places. The main page's place is always the main page: a page let go on it becomes the main page, and the main
    page goes where that one was. Undo, as for any icon moved.
53. **The owner's ask, 2026-10-01: the dots go with you** ("should update as we move, not only where we drop the grabbed
    asset"). The page in view always has its dot, empty or not, and on the phone the filled dot moves as the pages slide.

54. **The owner's ask, 2026-10-01: a thing carried out of the rows turns the page up or down at once** ("the transition
    should occur as soon as we hit the level under the version number"; "as soon as the note is dragged past the zone where
    the notes are displayed"). Above the rows, under the app's name: the page above, at once. Below the rows: the page
    below, at once - but while the dock is shown, after a third of a second, so a thing on its way to the dock does not
    turn the page as it passes over the search. One page each time it goes out; back into the rows for the next. Left
    and right stay as they were: held at the edge for two thirds of a second.
55. **The owner's ask, 2026-10-01: a dot before the version** - green on the newest version there is, yellow while a newer
    one is out; none until the repository has answered, or while looking is switched off. Yellow, a tap on it fetches the
    build, checks it and hands it to the installer, as before (0.0.109 on the phone, 0.0.020 on the PC).

## Not decided, taken as written unless the owner says otherwise

All three were settled on 2026-09-30 as decisions 25, 27 and 28 above; the owner may say otherwise.

## Going back

Install `dist/archive/0.1.041-final/` on the device, then put the device's backed-up app files back
file by file (phones: `run-as` copy into `databases/`, `files/`, `shared_prefs/`; PC: the copied
`%LOCALAPPDATA%\Mininotes` folder, app closed). On a phone: `adb install -r -d` the archived APK (`-d`
because it is older; a debug build allows it), `am force-stop org.mininotes.android`, empty and refill the
three folders with `adb exec-in run-as org.mininotes.android sh -c 'cat > <path>' < <file>` for each file,
list them again to check every size, then open the app. Tested on the emulator on 2026-09-30 (see *Step 0*). A 0.2 notebook is refused by 0.1.041 ("newer than this
app"), which is why the files, not the notebook alone, are what go back. The 0.2 line also imports a
0.1 backup file through *Add from backup* and *Replace*, so the older export stays usable.
