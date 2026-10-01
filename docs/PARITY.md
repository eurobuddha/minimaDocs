# Phone and PC, side by side

The rule: every decision applies to both where it makes sense. What differs is listed with the reason: for
0.2 under *What differs, and whether on purpose*, just below; for 0.1 under *Left open, and why*, at the end.

## Audited 30 September 2026, 0.2.004

From the code of 0.2.004, not from memory. Phone = `android/…/HomeScreen.java` (HS), `Folder`, `Dock`,
`OverviewScreen`, `IconPicker`, `IconFace` and `MainActivity.java` (MA); PC = `windows/…/DesktopHome.java`
(DH), `DesktopFolder`, `DesktopOverview`, `DesktopIconPicker`, `DesktopIcons`, `DesktopDrops` and
`Desktop.java` (D). What a drop does, how many icons go across, the dock's places and the list of what is open
are decided once, in shared tested code (`Grid`, `Overview`, `Things`, `Looks`, `Icons`, `Thumb`). **Both**
means a person can do the same thing on either, even where it looks different; *differs* means the list under
the tables says how. The design is [HOME.md](HOME.md).

### Home, cards and carrying

| Feature | Where | Phone | PC |
|---|---|---|---|
| Home: a grid of icons, Home's notes and collections in the owner's order, then the files that came; each icon its face, its name in two lines at most, its mark on one corner and a star on the other for a favourite; the Favourites icon first while the dock cannot show every favourite; a quiet line saying what to do when there is nothing | **Both** | `HS.draw`, `fill`, `icon`, `faced`: four across held upright, three under 340 dp, up to eight on a wide screen (`Grid.columns`, decision 18) | `DH.Icons`, `Tile`: as many columns as fit (`DH.columns`); a soft ground under the pointer, a ring only while the keyboard is used |
| The bar over Home | **Both**, differs | Mininotes and the build in the middle, the mark for everything, ⋮ (`HS.bar`) | the toolbar: Mininotes, the build and the update button; the lock, the connection, **Profile**, **Share**, ⋯ (`D`); no *New note* button, since **+** makes a note |
| **+** at the foot on the right, of Home and of each card: *Note* or *Collection*, made where it is; a note opens, a collection's card opens with *Untitled* ready to type over; Home's **+** hidden while a card is up | **Both** | `HS.plus`, `newNote`, `newCollection` | `DH.plusButton`, `D.newNoteIn`, `DH.newCollection`; also a right-click on Home or on a card's empty room (`D.roomMenu`: New note, New collection) and ⋯ → *New note* (Ctrl+N), *New collection* |
| The card: a collection opened as a rounded card over the dimmed Home, washed in its colour: ‹ and the name of the collection it is in, its face (a tap or click opens the icon picker), its name (renamed where it stands), its mark, its ⋮ or ⋯, its grid and its own **+**; a collection inside it opens in the same card (decision 21); a tap or click on the dim closes it; files from another program let go on it are kept with its collection | **Both** | `Folder`: back goes up a level, then closes | `DesktopFolder`: Esc goes up a level, then closes; F2 or a click renames, Enter keeps, Esc leaves it; Tab stays inside the card while it is up |
| Carried onto a note: a note makes a new collection *Untitled* holding both, its name ready to type over; a file becomes that note's attachment | **Both** | `Grid.onto`, `HS.merge`, `into` | `Grid.onto`, `DH.merge`, `into` |
| Carried onto a collection: into it; never a collection into its own inside (`Things.mayGoInto`) | **Both** | `HS.into` | `DH.into` |
| Carried between icons: a new place in the order - *replaced in 0.2.005: let go in an empty cell, it stays in that cell, gaps kept (decision 39)* | **Both**, differs | the icons part to show where; kept silently (`HS.across`, `keepOrder`) | a green line in the gap, the icon pale where it was, its name beside the pointer, the bar saying what letting go does; Esc puts it back; one Undo (`DH.Carry`, `keepOrder`) |
| Carried out of a card onto the dimmed Home round it: up one level, into what holds the card's collection | **Both** | `Grid.Zone.UP`, `HS.drop` | `Grid.Zone.UP`, `DH.Carry.letGo` |
| Carried onto the dock: a note or a collection becomes a favourite at the place it is let go, the one pushed past the end still a favourite, in Favourites; the dock lit while it can go; a file refused, said | **Both** | `HS.toDock`, `Dock.slotAt`, `Grid.dockSlot` | `DH.toDock`, `DH.Dock.slotAt`, `Grid.dockSlot` |
| A move that changes who receives it is asked first, by name (*Move anyway*, *Put them together*) | **Both** | `HS.into`, `merge` | `DH.into`, `merge` |
| Back from a note | **Both**, differs by decision | ← or the phone's back: Home as it was, the card reopened if the note was opened from one; a note opened any other way, its collection (`MA.leaveNote`) | **←** *Home* at the left of the note's bar, or Esc (decision 24): Home, with the card that was open (`D.goHome`, `showHome`) |

### The dock, the overview, search and the tree

| Feature | Where | Phone | PC |
|---|---|---|---|
| The dock: favourites, faces only, the rest in the Favourites icon; empty, it says *Favourites* quietly | **Both**, differs by decision | five at most (`Things.DOCK_PHONE`); each named to a screen reader; a short handle above it (decision 19) (`Dock`) | as many as fit the window (decision 3); each named under the pointer and to a screen reader (`DH.Dock`) |
| *Remove from the dock*, in a docked thing's menu: still a favourite, in Favourites | **Both** | offered on the five the notebook docks (`MA.menuFor`, `HS.undock`) | offered on every icon the PC's dock draws, since 0.2.005 (`DH.inDock`, `DH.docked`); one taken out is kept out of the PC's wider dock too (`keptOutOfDock`, PC settings) |
| The overview: the open notes and collections as cards, newest first, up to twelve, kept across restarts (`Overview`, decision 22); a note's card its paper, its face, its title and first lines; a collection's its face and name; **Close all** the one button; nothing open, it says so | **Both**, differs | a swipe up from the dock, or a tap on its handle, on Home (`OverviewScreen`); tap a card to go to it; throw it up, or hold it, to close it; back or a tap beside puts it away | the button beside the search, or Ctrl+Tab from Home or a note: Ctrl held, Tab moves along the cards and letting go opens the one chosen (`DesktopOverview`); a click goes; × or a middle click closes; right-click, *Close* and then the thing's menu; the arrows, Enter and Delete; Esc puts it away; it replaces the tab bar (decision 2) |
| Search: notes, collections and files by name and words, from two letters, each with its face and where it is | **Both**, differs | the bar above the dock opens a Search box, with *Favourites* and *Recent* before anything is typed; a result held gives its menu (`MA.searching`, `atHand`, `hitRow`) | the field above the dock, the results in the grid's place as they are typed; Down goes into them, Enter opens the first, Esc empties it; right-click gives the menu; Ctrl+F (`DH.look`, `DH.Row`) |
| The tree: every collection and note at any depth, each with its face in its colour, a star on a favourite, its mark | **Both**, differs by decision | ⋮ → *Tree view*, a box, indented by depth; a tap opens, a hold gives the menu (`MA.wholeTree`, `treeRow`; decision 23); its marks still read as the empty ring (see the older *Left open*) | a panel on the left, off by default and remembered: ⋯ → *Tree* (ticked while shown), Ctrl+B, or Settings → Window (decision 26); Home first, Favourites inside it; a coloured line washed in its colour; choosing a line opens it; right-click, F2, dragging to reorder or move inside, files from Explorer (`D.showTree`, `D.search`, `DesktopMoving`) |
| Archive and bin (0.2.006, decision 41): icons on Home, each with how many things wait in it, each opening as a card of what is in it - a thing there tapped or clicked offers *Put back* and, in the bin, *Delete for good*, in the archive *Move to the bin*; the bin's ⋮ or ⋯ has *Empty the bin*; a note or a collection carried onto the Archive is archived, onto the Bin it goes in the bin, said with one Undo; a file onto the Bin is deleted after its one question; Settings → *Show the archive and the bin on Home*, off puts them back where 0.1 had them | **Both** | `HS.placeIcon`, `placeFace`, `away`; `Folder.openPlace`; `MA.awaySheet`, `askEmptyBin`; off, ⋮ → *Open the archive* / *Open the bin*, the list screens | `DH.archivePlace`, `binPlace`, `face`, `awayMenu`; `DesktopFolder.openPlace`; `D.askEmptyBin`, `eraseForGood`; off, ⋯ → *Bin…* / *Archive…*, boxes (`D.restore`) |
| A long press on the phone and a right-click on the PC: the same menu, everything about the thing pressed and nothing else - its colour and strength, its icon, its size, what is done to it; on Home's empty room, New note, New collection, the text size, Home's colour, Undo, Sync now, Show favourites, Show search, All pages; ⋮ / ⋯ hold that and then the app's own rows (0.2.008, decisions 43-44) | **Both** | `MA.heldFor`, `roomFor`, `menuFor(…,app,room)`, `Sheet.toggle`; Home's colour already there | `D.thingMenu`, `D.roomMenu`, `D.homeRows`, `D.ticked`; Home's colour new (`D.homeColour`, `DH.paintRoom`) |
| Home in pages in every direction (0.2.008, decisions 45-50): a page as big as the screen or the window; another page only where something stands, made by carrying an icon to an edge and holding it; the favourites and the search on the main page only, each switched off in Settings or Home's menu; every page at once, zoomed out; back to the main page by a double tap or click | **Both**, differs by platform | the pages follow the finger and a pinch shrinks them live, each whole in its frame (0.2.009, decision 51); a double tap or back goes home; dots at the foot (`HS.Turner`, `go`, `place`, `move`, `edgeWatch`, `PageDots`) | the wheel, Shift with it or a touchpad, dragging the empty room, Page Up / Page Down (Ctrl for sideways), a double-click or Ctrl+Home home, Ctrl with the wheel zooms out; dots at the foot (`DH.turning`, `Icons.turn`, `Carry.atTheEdge`, `DesktopPages`) |
| A thing carried above Home's rows or below them goes to the page above or below at once (below: after a third of a second while the dock shows); left and right held at the edge (0.2.010, decision 54) | **Both** | `HS.edgeWatch` | `DH.Carry.atTheEdge` |
| A dot before the version: green on the newest, yellow while a newer one is out, none until the repository has answered (0.2.010, decision 55) | **Both** | `MA.versionShown`, `Update.standing` | `D.updateShown`, `Update.standing` |
| Zoomed out, a whole page carried to another place or onto another page to change places (0.2.009, decision 52); the dots show the page in view even while it is empty (decision 53) | **Both** | held still on a page, then moved (`HS.liftPage`, `dragPage`, `dropPage`) | dragged (`DesktopPages`, `DH.movePage`); Esc cancels |
| Home's places - Favourites, Archive, Bin - carried to another empty cell of Home, where each stays, kept in each device's own settings; carried onto anything else, back where it was (0.2.006, decision 42) | **Both** | `Grid.carried`, `Layout.home`, `HS.put`, `placesAt` | `Grid.carried`, `Layout.home`, `DH.Carry`, Ctrl+arrow (`nudge`), `writeCells` |

### Files

| Feature | Where | Phone | PC |
|---|---|---|---|
| Received files on Home, *new* until opened, their kind in their own letters; a click or tap opens one in whatever opens it | **Both** | `HS.fileFace`, `openFile` | `DH.face`, `openFile` |
| A file's menu: *Open*, *Save a copy*, *Send to a device*, *Put in a note*, *Move to…*, then under a line *Delete*, asked first | **Both** | `HS.fileMenu` | `DH.fileMenu` (the same rows, with …) |
| A picture pasted on a note (Ctrl+V, Paste, or the keyboard's picture), with no words to it: kept with the note as a PNG or as it came, named for when it came on the PC ("Picture 1 Oct, 12:05.png"); words with a picture of them stay words; files copied in Explorer are kept too (0.2.007, the owner's ask; `Attachment.pasted`) | **Both**, differs | Android 12 and later: the page's `setOnReceiveContentListener` for `image/*`, kept under the name the other app gives (`MA.pastedPicture`, `keepDropped`) | the page's and the title's paste (`DesktopDrops.Aimed`, `picture`, `D.attachPicture`); the page's menu offers *Paste* for a picture too |
| Files let go from another program | **Both**, differs | anywhere on Home's grid: kept on Home, even over an icon; on a card: kept with its collection; on a note's page: attached (`MA.filesOver`, `keepFiles`) | aimed at what is under the pointer: a note's icon or the open note, attached; a collection's icon, card or tree line, kept with it; Home's grid, the search or the dock, kept on Home; anywhere else, the Send box (`DesktopDrops.aim`) |
| A collection's files added by hand: *Add a file…* in its menu (decision 23) | **Both** | `MA.menuFor` → `attach` | `D.thingMenu` → `addFiles` |
| Somebody asking to send files: *Accept* or *Refuse* | **Both** | a box, or a notification that opens Home and asks (`MA.askAboutSending`, `HS.askWaiting`) | a box; the bar, and the tray while the window is away (`DesktopDrops.ask`, `D.notice`) |
| Sent files: *Coming to this phone* (or PC), asking or still on the way, then *Sent*, each with when and where it stands (decision 20) | **Both**, differs | ⋮ → *Sent files* (`HS.sentFiles`): a tap on a line asks, stops it or takes it off | ⋯ → *Sent files…* (`DesktopDrops.sentFiles`): *Accept* / *Refuse*, *Stop*, *Stop sending* / *Remove* on each line |
| Send files, in no note: ⋮ / ⋯ → *Send files*, then a device | **Both** | `MA.sendFiles`, `chooseDevice`; also *Or send to* from another app's share sheet | `DesktopDrops.send`; also files let go where nothing keeps them |

### Icons and pictures

| Feature | Where | Phone | PC |
|---|---|---|---|
| Faces everywhere: Home, cards, the dock, the overview, search, the tree and the note's bar; a note's glyph (the note icon until chosen) in its colour's ink on its paper; a collection's icon, or with none the mini-grid of what is inside; a picture filling the round square | **Both** | `IconFace`, `IconPen` | `DH.face`, `faceIcon`, `DesktopIcons` |
| The icon picker, one box (decisions 32-34): the colours along the top, the one worn ringed; a search; *Default* first, then the Lucide set in its order; *Choose a picture…* at the end; a click on an icon is the choice, and choosing one takes a picture off | **Both** | `IconPicker`: from *Icon…* in the menu, or a tap on the face before a note's title or a card's name | `DesktopIconPicker`: the same two ways in; Enter takes the first found, the arrows move, a letter typed on the grid goes to the search |
| A thing this device only reads keeps the icon it came with | **Both**, differs | no *Icon…* in its menu; a tap on its face says it is read only | *Icon…* opens the box with the colours only, and a line saying why |
| A picture: cut square from the middle, turned as the camera held it, at most 32 KB (`Thumb`), kept in the thing's own row and carried with it; *Remove the picture* in the menu while one is worn | **Both**, differs by decision | from the phone's own picker, encoded WebP, the busy strip saying so (`IconPicker.pictured`, `thumbnail`) | *Choose a picture…* (JPEG, PNG, GIF, BMP, WebP), pasted, or let go on the box; encoded PNG, the bar saying so; the phone's WebP read with TwelveMonkeys (decision 38) (`DesktopIconPicker.picture`, `DesktopIcons.thumb`) |

### The thing's menu

The same rows in the same order on both, for a note or a collection, wherever it is opened: a hold on the
phone, a right-click (or the Menu key) on the PC, or the ⋮ / ⋯ on a card or a note.

- **Phone** (`MA.menuFor`): A− · rungs · A+ · the colours · the tone · *Icon…* · *Remove the picture* (while
  worn) — *Undo …* (when there is something) — *This note* or *This collection*: *Versions* (a note) · *Add a
  file…* (a collection) · *Move somewhere else* · *Sharing* · *Sync now* (when it reaches anybody) · *☆
  Favourite* or *★ Favourite* · *Remove from the dock* (when docked) · *Archive* · *Delete* — *Send to another
  app* — then the app's own rows: Find (*Search*, *Tree view*), Put away (*Open the archive*, *Open the bin*),
  People (*People and devices*), Backup (*Export backup*, *Add from backup*), Mininotes (*Send files*, *Sent
  files*, *Update to v…* while there is one, *Settings*, *Profile*, *Feedback*, *Share Mininotes*, *About*).
- **PC** (`D.thingMenu`): *Colour ▸* (the colours and their strength) · *Icon…* · *Remove the picture* (while
  worn) · *Text size ▸* (a note) — *Undo …* — *Rename…* (F2) · *Versions…*, *Attach a file…*, *Record audio* (a
  note) · *Add a file…* (a collection) · *Move to…* · *Share…* · *Sync now* (when it reaches anybody) · *Add to
  favourites* or *Remove from favourites* · *Remove from the dock* (when docked) — *Archive* · *Move to bin* —
  *Copy the text* (a note). The app's own rows are in ⋯, not here.
- **Home's own menu.** Phone, ⋮ on Home: the ladder, the colour for the whole pad, the tone, *Undo*, *Sync
  now*, *Send to another app*, the app's rows. PC, a right-click on Home: *New note*, *New collection*, *Undo*,
  *Sync now*; ⋯ holds the app's rows.
- **The Favourites icon.** Phone, held: the menu of a place (the ladder and the app's rows). PC, right-clicked:
  *Open*.
- **The Archive and Bin icons** (0.2.006). Phone, held: the menu of a place, the bin's with *Empty the bin*. PC,
  right-clicked: *Open*, and on the bin *Empty the bin…*.

The order is one (decision 33 put *Icon…* after the colour on both). The words that differ are the 0.1 ones,
kept: *Move somewhere else* / *Move to…*, *Sharing* / *Share…*, *Favourite* / *Add to favourites*, *Delete* /
*Move to bin*, *Send to another app* / *Copy the text*; so are the PC's *Rename…* and a note's own rows, and the
phone's reading ladder and app rows in every menu (see the older audit).

### Keyboard, and waiting for an update

| Feature | Where | Phone | PC |
|---|---|---|---|
| Keyboard reach: Tab and the arrows between icons, Home and End to the ends; Enter or Space opens; F2 renames; Delete bins a note or a collection and deletes a file, asked first; the Menu key or Shift+F10 gives the menu; Ctrl+N a note where you are, Ctrl+F the search, Ctrl+B the tree, Ctrl+W closes the note or card, Ctrl+Tab the overview, Ctrl+S saves now, Esc goes back a step | **PC only** | — (nothing to reach with) | `DH.does`, `step`, `DH.Row`, `D.bind`, `D.contextKey`, `D.escape` |
| *"Ana's phone needs to update Mininotes to receive this"* in the mark's box, for what a 0.1 device cannot take; with nothing else waiting, the box has nothing to press (`Unsent`, `Waits`, `Looks.onlyUpdates`) | **Both** | `MA.whatWaits` | `D.markClicked` |

### What differs, and whether on purpose

- **The dock's size** - five on the phone, as many as fit on the PC. On purpose: decision 3.
- **The dock's names** - none on either, as a phone's dock has none; the PC also says each name under the
  pointer, which a finger cannot rest on. On purpose: decision 19.
- **← Home and Esc** on the PC's note, the arrow and back on the phone's. On purpose: decision 24; they do the
  same.
- **The tree** - a box from ⋮ on the phone, an optional panel on the PC. On purpose: decisions 23 and 26.
- **The overview** - on the phone it is reached from Home only, where the dock is; on the PC from anywhere,
  by Ctrl+Tab. A card is closed by a throw or a hold on the phone, by × or a middle click on the PC, where
  a right-click is the thing's menu. Each follows its platform's habit (open apps, Alt+Tab); not a written
  decision.
- **Search** - a box with *Favourites* and *Recent* on the phone, results in the grid's place on the PC. Not
  a decision: the phone kept its 0.1 search box. For step 5 to settle.
- **A new order** is one Undo on the PC and silent on the phone, as each was in 0.1. Not a decision.
- **Files from another program** on the phone's Home stay on Home even when let go over an icon; the PC
  aims at the icon under the pointer. Not a decision; the phone could aim as the PC does.
- **A read-only thing's icon** - the rule is one, a reader's look would go nowhere; the phone hides *Icon…*,
  the PC shows the box with the colours only. Not a decision.
- **Pictures** - WebP on the phone, PNG on the PC, each read by the other. On purpose: decision 38.
- **Archive and bin** - the same on both since 0.2.006: icons on Home, and cards of what waits in them. Switched
  off in Settings, each app goes back to what it had in 0.1: list screens on the phone, boxes on the PC.
- **Remove from the dock on the PC** is offered only on the five favourites the notebook docks, while the
  PC's dock can show more; one shown past the fifth has no such row, and only *Remove from favourites*
  takes it out. Looks like a fault, not a decision.

## Audited 27 September 2026 (0.1, history)

Audited 27 September 2026, from the code (not from memory). Phone = `android/…/MainActivity.java`
(MA) unless another class is named; PC = `windows/…/Desktop.java` (D) unless another `Desktop*` class is
named. **Both** means a person can do the same thing on either, even where it looks different. This
replaces an earlier audit, kept in the maintainer's private verification log.

It describes 0.1. Its rows about books, the shelves one level at a time, tabs, cards or a list, and the Drop
box were replaced in 0.2 by the audit above; the rest still stands unless the audit above says otherwise.

## Menus

| Feature | Where | Phone | PC |
|---|---|---|---|
| Text size, ten rungs (A− / rungs / A+) at the top of the menu | both | `Sheet.ladder`, `stepBy`, `setSize`; pref `rung` | `DesktopLook.ladder` at the top of `D.menu`; `D.setTextSize`; pref `rung` (see Look) |
| Undo, one level, at the top of the menu | both | `canUndo`, `undo`: put away, move, rename | `D.canUndo`, `D.undo`, `D.undoRow` in `D.menu` and `D.shelfMenu`: put away, move, rename |
| Menu about one thing (hold a tile / ⋮) | both | `menuFor`: ladder, palette, strength, Undo, Versions, Move, Sharing, Favourite, Archive, Delete, Send to another app | `D.thingMenu`, one builder for a line of the tree, a card and a tab: Undo, Rename (F2), Colour ▸, Share, Favourites, Move to, Archive, Move to bin; `D.menu` → This note / This book / This collection |
| Right-click everywhere, Menu key / Shift+F10 | PC only | hold is the phone's right-click | tree lines (`D.rowMenu`; the top: New collection), cards, the room round the cards (`D.roomMenu`: New note / New book first), tabs (`D.tabMenu`: Close, Close others, Close tabs to the right, then the thing's menu), attachment cards (`DesktopFileCards.menu`: Open, Save a copy…, Send to a device…, Delete), the Drop box's file cards and lines (`DesktopDrops.fileMenu`: Open, Save a copy…, Put in a note…, Send to a device…, Delete), rows in the Drop box, People and devices, Bin and Archive (`DesktopMenus.echo`: the row's own buttons); `D.contextKey` for the focused line, card or note |
| Find: Search, Tree view | both | `appRows` → `searching`, `wholeTree` | search box over the tree (Ctrl+F), the tree always beside the page |
| Put away: archive, bin | both | `appRows` → `enter(ARCHIVE/BIN)` | `D.menu` → `D.restore` |
| People: People and devices | both | `addressBook` | `D.people` |
| Backup: Export, Add from backup | both | `pick(EXPORT/IMPORT)` | `D.backup`, `D.importBackup` |
| Mininotes: Settings, Profile, Feedback, Share Mininotes, About | both | `appRows` | `D.menu` |
| Update to vN in the menu | phone only | `appRows` → `announce` | the PC says it in the bar instead (`D.updateShown`) |
| New collection / New book in the menu | PC only | made in place on the shelves (`addHere`) | `D.menu` → `D.addShelf` |
| Copy the text, Close tab, Exit Mininotes | PC only | Send to another app covers copying | `D.copyNote`, `DesktopTabs`, `D.shutdown` |

## Look

| Feature | Where | Phone | PC |
|---|---|---|---|
| Ten-step text size, same rungs | both | `SIZES {11…29}`, middle rung 5 of 10; int pref `rung` | `DesktopLook.PHONE` (same table, drawn 18/17 as big so rung 5 is the PC's 18-point page); pref `rung`; the old four-size `textSize` moves to the nearest rung |
| What the size moves | differs | the whole interface (`resize`) | the page and its title; Windows' own scaling covers the rest |
| Text size in Settings | PC only | only in the menu | Settings → Window, the same ladder (`DesktopSettings.open`) |
| Colours: No colour + Red, Orange, Yellow, Green, Teal, Blue, Purple, Pink | both | `Sheet.palette` → `NoteStore.paint` | Colour ▸ (`DesktopLook.colours`) → `D.paint` → `NoteStore.paint`; same numbers, same column |
| Colour strength, ten tones | both | `Sheet.tones`, `useTone`; pref `tone`, starts at `Tint.FIRST_TONE` | under the colours in Colour ▸ (`DesktopLook.strength`), `D.useTone`; pref `tone` |
| Colour on cards | both | filled with the wash, edged in the colour (`card`, `bubble`, `sheet`, `edged`) | `DesktopShelves.card`: the same wash and a colour edge |
| Colour on the page / room | both | `repaint`: the paper and its rules washed in the open thing's colour | `D.washPage`: the note's paper, rules and file drawer; the cards' room in the collection or book shown |
| Colour in the tree | both | `treeRow`: a dot | `D` tree renderer: a dot and the row washed |
| A colour for the whole pad | phone only | `libraryColour`, pref `colour` | — |
| Colours between devices | neither | not in `Parcel`; kept per device, carried by backups | same |
| Dark paper following the system | phone only | `paperNow`, `usePaper` | — |
| Pinch to zoom | phone only | `dispatchTouchEvent` | — |
| Links in a note open: web and mail addresses in the accent colour, underlined; a tap or plain click on one opens it (browser, or mail app for an e-mail address), anywhere else only places the caret, a drag still selects; the hand over an address on the PC. Found by one shared rule, `Links` (2026-09-29; the phone used Android's Linkify before, which also took some bare names the rule now leaves alone) | both | `linkify` (`Links.find` → `URLSpan`), `followLink` | `D.Paper`: `links`, `linkAt`, `paintLinks`, `open`; gallery 80 |
| Cards or a list | phone only | Settings → cards switch | the PC has cards and the tree together |

## Shelves

| Feature | Where | Phone | PC |
|---|---|---|---|
| Tiles / cards of what a place holds | both | `tile`, `bubble`, `sheet`, `card` | `DesktopShelves.show`, `card`; trail back to the top |
| Drag to reorder | both | `grabbable`, `orderable`, `lift`, `keepOrder` → `store.order` | `DesktopMoving`, in the tree and the cards: a green line where it lands, the lifted card pale, its name by the pointer, the bar saying where; Esc cancels; `D.reorder` → `store.order`, one Undo; the same `Reorder.slot` |
| Drag onto a book or collection: move inside | PC only | moving is in the menu (`putDown`) | `DesktopMoving.overTree` / `overCards` → `D.moveInto`, the same who-starts/who-stops question as Move to…; between another book's notes it lands there at that place; one Undo |
| Drag to combine (two notes make a book, two books a collection) | phone only | `across`, `markOnto`, `combine`, `doCombine` | — (on the PC a thing dropped on its own kind takes a place beside it) |
| Make a collection, book, note | both | `addHere`, `makeHere` | `D.addShelf`, `D.newNote` (Ctrl+N) |
| Rename | both | in place (`nameable`, `renameNote`) | F2 or Rename… (`D.rename`); a note's title in its field |
| Move | both | `Move somewhere else` → `confirmMove` → `doMove` | `D.moveTo`, the same who-starts/who-stops warning |
| Favourites | both | `keepToHand`, Favourites place, star | `D.favourite`, ★ Favourites at the top of the tree |
| Archive, bin, put back, delete for good, empty the bin | both | `putAway`, `awaySheet`, `askErase`, `askEmptyBin` | `D.putAway`, `D.restore` |
| Archive → bin directly | phone only | `awaySheet` "Move to the bin" | put back, then Move to bin |
| Tabs, side panel, keyboard shortcuts | PC only | — | `DesktopTabs`; `D.showTree`, `Grip`; Ctrl+B/W/N/S/F/Tab/PgUp/PgDn, F2 |

## Sharing, sync and people

| Feature | Where | Phone | PC |
|---|---|---|---|
| Sharing box: who has access, roles, add someone, show code, unfollow | both | `aboutSharing`, `drawShare`, `sharedWithMe`, `askUnfollow` | `D.share`, `D.people(target)`, `D.showCode` |
| Receive / pause | both | Pause receiving, on things from someone else | Receive their changes, on anything |
| Send timing, per thing and for the app | both | `setPause`; Settings Sync | Share → Send my changes; Settings → Sync |
| Sync now | both | `syncNow`; the amber ↑ under the title, tapped, says first what waits and for whom - *Waiting to reach Pro with your changes*, *Waiting for Pixel 7 to confirm 3 files*, *…with an answer* - with **Send now** as its one button (`whatWaits`; 2026-09-29) | Sync now in Share; the amber ↑ clicked, the same box and words (`D.markClicked`; gallery 59b); words in shared `Waits`, `SyncStatus.waits` |
| What could not go: the device and the thing by name (in the title where one device is the whole of it), what to do, the fix as the one button (Pair with it / How notes travel / Link with <name>); somebody only listed also gets *Take them off “…”* | both | `tellUnsent` after Sync, Send and sharing; `takeOff` | `D.tellUnsent` after Sync now and sharing; the bar after Send and close; `D.link`, `D.takeOff`; the words in shared `Unsent` |
| Line under the title: icons only — the sync mark, then a round per person with a dot for where they stand; a round clicked says who and where in a small box, the writing and the files apart (*Pixel 7 has the text; 3 of 9 files still going*, `Person.standing` / `Waits.person`, 2026-09-29; gallery 59c). Somebody the note's list names and this device is not linked with: a dashed grey round with a broken link, and a box with *Link with <name>*, *Take them off “…”*, and no *Not now*: the box's ×, Escape, Back or a tap beside it closes it (2026-09-28) | both | `syncMark` (moved there from the bar), `rounds`, `askWhatIsOwed` → `drawRounds`, `personRound`, `brokenLink`, `notLinked` | `D.updateStanding` → `D.rounds`, `DesktopMark.person`, `paintNotLinked`, `told`, `D.notLinked` |
| Send before closing a tab | PC only | — | `D.askBeforeClosing` |
| Six sync marks: only here (grey ○), waiting to go (amber ↑), sent, not confirmed (amber ⋯), everybody has it (green ✓), paused (grey ‖), not heard from (red !) — worked out once, in shared `SyncMark` with `NoteStore.standing`, `SyncStatus.who` / `mark` and `Branch.mark` | both | `Mark`, `syncMark`, `shareBadge`, `inkOf` | `DesktopMark`, the same drawing: on the line under the note's title (`D.showNoteMark`), at the end of each tree line and in each card's corner (`DesktopMark.read` → `store.inside`); clicked: waiting syncs now, anything else opens Share. No tooltips; every mark and round has an accessible name |
| Settings → What the marks mean | both | `marksInto` | `DesktopSettings.legend` |
| People and devices: My device, what each has, roles | both | `addressBook` | `D.people(null)`, plus Address with its code |
| People and devices in two parts: My devices (this one first, by device name), then People (by the names they chose) | both | `addressBook` | `D.people` |
| Linked through what you share: devices on one list link by themselves (keys on the list, a `LINK` hello, taken only where a list here names its key); the round says *Linking with <name>… it goes once they answer.* until they do; People and devices says *Linked through “…”* under such a device (SHARING.md, Linked through what you share) | both | shared `Post.linkThrough` / `linkHeard` / `linkAgain`, `Linking`, `NoteStore.linkThrough`; `personRound` / `notLinked` via `Person.notLinked`; `addressBook` (`store.linkedLines`) | the same shared code; `DesktopMark.person`, `D.notLinked`; `D.people` (`store.linkedLines`) |
| One person on every device: the bond, whose id both keep, the card to own devices only (SHARING.md, One person on every device) | both | `giveItTo` / `keepPairing` → `Post.bonded`; shared `Post`, `Persons` | `D.accepted` / `D.receiveCode` → `Post.bonded`; the same shared code |
| Forget a device | both | hold it → `forgetAddress` | Forget beside it → `store.removeAddress`, asked first |
| Pairing: scan, paste, digits check, show my code | both | `scanning`, `readPairing`, `myCode` | `DesktopScanner`, `D.receiveCode`, `D.showCode`, Profile |
| Change my address by hand | phone only | `changeMyAddress` | — |
| How notes travel, relays | both | `travelInto`, `relaysInto` | `DesktopSettings.travel`, `relays` |
| Direct connections (firewall) | PC only | — | `DesktopFirewall`, Settings |
| Listening while closed | both | Listening, notifications, battery switch | tray (`D.ensureTray`), Keep listening |

## Notes, files, backups, the app

| Feature | Where | Phone | PC |
|---|---|---|---|
| Versions, put one back | both | `versions`, `putVersionBack` | `D.versions` |
| Attachments on a note | both | `fileStrip`, `attach`, `openFile` | `DesktopFileCards`, `D.attach`, `D.clipMenu`, `D.saveCopy`; right-click a card: Open, Save a copy…, Send to a device… (`DesktopDrops.send`), Delete |
| Record audio into a note: 🎤 beside the 📎, a slim bar (red dot, running time, *Up to N min · leaving keeps it*, a quiet Cancel, Stop), kept as the note's attachment "Recording 28 Sep, 14:05", sealed at rest when locked, travelling like any file; stops by itself under 16 MB and says *Stopped at 16 MB so it can travel with the note*; leaving the note (or, on the phone, the app) keeps what was heard; recording counts as use, so the notebook does not lock again halfway | both | `record`, `startRecording`, `stopRecording`, `cancelRecording`, `recordingBar`; AAC .m4a, mono, 48 kbps (about 45 min); `RECORD_AUDIO` asked at the first press, refused for good → a box leading to Android's settings | 🎤 at the page's corner and *Record audio* in `D.clipMenu` → `DesktopRecorder`; μ-law WAV, mono, 16 kHz (about 17 min), held in memory and written once; no microphone → says where to choose one; only silence → Windows' microphone privacy, with *Open microphone settings* |
| Listen on the card: ▶ and the length before it is pressed, ▶ / pause and a thin line for how far, one sound at a time | both | `chip` → `togglePlay`, `drawPlaying`; Android's player plays m4a, WAV, MP3 and the rest from memory (`Heard`) | `DesktopFileCards.listen`, `toggle`: a WAV plays on the card (Java Sound); anything else (the phone's AAC, MP3) says *Opens in your player* and ▶ opens it there; lengths read by shared `Recording.millis` |
| Files kept with a book or a collection | phone only | `fileStrip` on a shelf, `filesReaching`; stay on the phone | — |
| Add to Mininotes from another app | phone only | `handing`, `askWhereItGoes` | — (Windows has no share target here) |
| Send files straight to a device, in no note (see SHARING.md, Sending files) | both | ⋮ → Send files (`sendFiles`, `chooseDevice`, `sendTo`) | ⋮ → Send files… (`DesktopDrops.send`, `box`) |
| Send files from another app's share sheet | phone only | *Or send to* in `askWhereItGoes` | — (no share target); files dropped from Explorer on the window instead (`DesktopDrops.takeDrops`) |
| Drop box: accept or refuse, open, save a copy, put in a note, send on, delete; under *Sent*, what went and where it stands; Send files… its one primary action | both | a tile at the top after the collections and the last line of Tree view (`Kind.DROPS` in `store.inside`, `placeTile`, `drops`, `dropRow`, `askAboutSending`, `fileActions`); a list with one Sort by (`DropList.CHOICES`) | a line of the tree level with All collections, under it, with a count and a line per file (`D.search`, `D.allCollections`), and a card after the collections in All collections saying what is new (`DesktopShelves.dropBox`); the main area as file cards or a sortable list, the choice kept (`DesktopDrops.show`, `switcher`, `list`, `view`, `sortBy`, `lines`, `fileMenu`, `ask`; `D.showDropBox`); the same order as the phone (`DropList`) |
| Told when files come or somebody wants to send some | both | a notification while the pad is closed (`Listening.told`), a box or a line while it is open | the bar, and the tray while the window is away (`D.notice`), a box to accept or refuse |
| Export backup, locked when the notebook is | both | `export`, `zipBackup` | `D.backup`, `DesktopBackup.write` |
| Add from backup | both | `askHowToImport` → Add to this pad | `D.askHowToImport` → Add to this pad |
| Replace everything from a backup | both | Replace everything → `confirmReplace` → `importBackup(…,true)` | `D.askHowToImport` → `D.confirmReplace` → `DesktopBackup.add(…,true)` |
| Lock, password, recovery words, lock again when idle | both | `securityInto`, `PhoneLock` | `DesktopLock`, `D.relockIfIdle` |
| Fingerprint / Windows Hello | both | phone unlock | `DesktopHello` |
| Profile, name, address, code | both | `profile` | `DesktopProfile` |
| Two names: Your name (what other people see) and This device (what your own devices call it) | both | `profile`: each changed in place (`keepYourName`, `keepThisDevice`) | `DesktopProfile`: the You card, two fields saved as typed |
| Updates | both | Update to vN, look once a day | bar button, Update automatically, restart to update |
| About, donate, source; Feedback; Share Mininotes | both | `about`, `feedback`, `shareApp` | `D.about`, `D.feedback`, `D.shareApp` |

## Closed on the PC on 27 September 2026

Text size (ten rungs, in the menu and Settings), colour strength, colours on cards, page and tree, the
aligned Colour menu, one level of undo, replace everything from a backup, forget a device.

## Closed on the PC later on 27 September 2026

Drag to reorder (tree and cards, `DesktopMoving`), drag onto a book or collection to move it inside, right-click
everywhere with the Menu key and Shift+F10, and the phone's sync rings (`DesktopMark`) in place of ↑ / ✓ and the
tooltip on the line under the title. Pictures: `DesktopGalleryTest` 41–56; placement rule: `DesktopMovingTest`.

## The line under the title, 27 September 2026

The owner's decision: the line under a note's title shows icons only, on both apps, and a mark's colour never
contradicts its drawing (the old green ↑ sat beside "not sent yet"). "Shared with …", "not sent yet – send now",
"N files still going" and "up to date" are gone from that line and, on the phone, from the line under the note's
name; the Share box keeps its words. Red means a newer version has been owed to a device for more than three days
(`SyncMark.LONG`, counted from the note's last change) and nothing at all has been heard from that device since
(`sent.at`, when its last answer was recorded). Nothing records another device refusing a note, so refusal has no
mark of its own. A file too big to go never holds a note back from ✓; a file still going keeps it at ↑. Pictures:
`DesktopGalleryTest` 51–59 and 27g; rules: `SyncMarkTest`, `DesktopSyncStatusTest`.

## Recording audio, 28 September 2026

The owner asked for audio recorded straight into a note. Both apps record through the ordinary attachment path,
so a recording is an attachment like any other: sealed at rest when the notebook is locked, sent with a shared
note, 16 MB at most so it can travel (`Recording.STOP_AT`, a little under `Enclosure.MOST`).

- **Formats.** The phone records AAC in .m4a, one channel, 48 kbps: about 45 minutes. Java has no AAC encoder
  and no small pure-Java encoder with a GPL-compatible licence was found, so the PC records WAV - μ-law, one byte
  a sample at 16 kHz, half the size of 16-bit WAV (about 17 minutes against 8). The phone, Java Sound and
  Windows' players all play it with nothing added.
- **Playing.** The phone plays everything on the card. The PC plays WAV on the card; the phone's AAC and MP3
  go to the PC's own player, and the card says so before ▶ is pressed.
- **Leaving.** Android gives an app out of sight silence from the microphone unless it runs a foreground
  service with a notification of its own; rather than add one, leaving the pad (or the note) stops the
  recording and keeps it. The bar says so.
- **Names.** "Recording 28 Sep, 14:05.m4a". Windows refuses a colon in a file name, so a copy written out on
  the PC (Save a copy, Open, Send) says 14.05 (`DesktopFiles.onDisk`); the name in the note is unchanged.
- Rules: `RecordingTest` (naming, lengths, the cap, reading a WAV's and an MP4's length),
  `DesktopRecorderTest` (μ-law, the WAV header, Java Sound reading it back). Pictures: `DesktopGalleryTest`
  70 (the bar), 71 (a recording on its card, and the phone's .m4a saying *Opens in your player*), 72 (the
  paperclip's menu). Nothing in a test opens the real microphone (`DesktopRecorder.source`). The phone's bar
  and cards have not been pictured.

## Linked through what you share, 28 September 2026

The owner's decision: a device linked to a thing is linked through it to every other device that has it. Built in the
shared code, so both apps do it the same way; each app changed only where it draws a round for somebody not linked yet
(`SyncStatus.Person.notLinked`, which says *Linking with …* while a device linked through the list has not answered),
where the could-not-go box picks its button (`Unsent.listed`, which counts *linking* with *only listed*), and the line
under a device in People and devices (`NoteStore.linkedLines`). Rules: `LinkingTest`; four real nodes: `LinkDoorTest`;
the PC's notebook: `DesktopLinkTest`. Not pictured on either app.

## Syncing said at the top of the note, 28 September 2026

The owner asked for the open note's syncing to be said at the top, not at the foot. On both apps the words now sit
on the line under the title, after the mark and the rounds, in the mark's colour - amber while it goes, green when
it has, red when it could not - and go a few seconds later unless still going on (words ending in "…"). A click or
tap on them does what the mark does, or opens the reasons for "see why". The words are shared (`NoteLine`,
`NoteLineTest`): *Saving…*, *Sending to Ana…*, *Sent to Ana, waiting for them to confirm*, *Nothing new to send*,
*Linking with … it goes once they answer*, *Could not reach Ana - see why*. For the open note, Sync now no longer
opens a box on failure; the line says so and the box is behind the tap. A send by itself after writing is said too
(not the tries again a minute apart). Everything not about the open note - a collection's or book's sync, files in
the Drop box, updates, backups, errors - keeps the PC's bar and the phone's strip. The phone's line under the
name still says a save failed and Read only. Pictures: `DesktopGalleryTest` 73-75; the phone's line has not been
pictured.

## Left open, and why

**Closed by 0.2 (30 September 2026).** Drag to combine is on both: a note let go on a note makes a new
collection on either, by the same rule (`Grid`). Files on collections are on both, and travel with a shared
collection (0.2.004). The PC's tabs are gone, and with them the phone's lack of them: the overview is on both.
The phone's *cards or a list* switch is gone: Home and every card are icon grids. The items below are left as
they were written, for 0.1.

- **Drag to combine (PC).** On the PC, dropping onto a thing of the same kind means "put it here", and onto a
  book or collection "put it inside". Combining two notes into a new book would be a third meaning for the same
  gesture; left out until it is wanted.
- **The phone's Tree view marks.** `treeRow` wears `shareBadge`, but `store.wholeTree()` carries no audience,
  so every line there shows the empty ring. The PC reads the marks level by level (`DesktopMark.read`); the
  phone's Tree view could do the same. Not changed here: MainActivity was being edited by someone else.
- **Files on books and collections (PC).** On the phone they never leave the phone (only a note's files
  are sent), so the PC would only gain a second, separate set. Better decided together with sending them.
- **A colour for the whole pad (PC).** The tree's top has no menu; small, but nobody has asked.
- **Colours between devices (both).** A colour is kept per device and travels only in backups; sending it
  would change the sealed format (`Parcel`), which the handshake says not to fork for a desktop feature.
- **Phone lacks:** New collection / New book in the menu, Copy the text, tabs, text size in Settings,
  Send before closing, a direct-connection switch (Android needs none), Receive their changes on one's
  own things. For later, if wanted.
- **Size reach (PC).** The ladder moves the page and its title, not the whole window as on the phone.
