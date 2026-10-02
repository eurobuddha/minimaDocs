# Sharing notes — design

Status: built, and seen working between two phones in both directions. A note is sealed for the device it is going to, carried by this phone's own Maxima node, filed on the far phone in the collections it came out of, merged where both ends wrote, and — since v0.0.91 — taken in while the far pad is closed. What is not built is listed at the end, and it is a real list.

**Since 0.2 (2026-09-30): notes and collections.** There are two kinds of thing, a note and a collection, and a collection holds notes, other collections and files, as deep as anybody likes. What was a book is a collection inside a collection, and Home is the collection everything is in (see [HOME.md](HOME.md)). A rule on a collection reaches everything inside it, however deep. Since 0.2.002 on the phone and 0.2.003 on the PC, files sent from another device land on Home and the Drop box is gone: ⋮ → *Sent files* lists what went (*Sending files*, below). Since 0.2.004 a note or a collection can wear an icon or a picture, which travels with it, and a collection's own files travel as a note's do. A device still on 0.1 is sent only what fits its three levels; the rest waits, saying that device needs to update (*Older builds, and TREE*, below). Where *book* appears below, it is in dated history or in a name the code keeps.

## What this is being simplified into

Decided on 18 September 2026, after the person whose app this is said plainly that he did not understand what had been built. Everything below this section describes what exists; this section is where it is going, and what exists is judged against it.

**It works the way Google Drive does**, because that is a set-up people already know and nobody has to be taught it.

| In Drive | Here |
|---|---|
| Your account, on every device | **Connect your own device** once, and the whole pad is the same on both |
| People you can share with | **People**: anyone whose code you scanned once |
| The Share box: people, and a role beside each | The same box, on any note or collection |
| Owner · Editor who may share · Editor · Viewer | **Owner · Admin · Can write · Can read** — see *The four roles* below |
| The owner cannot be removed | The same |
| Folders inside folders | Collections inside collections, as deep as anybody likes |
| Sharing a folder shares what is in it, now and later | Sharing a collection covers everything in it, however deep, now and later |

**What is shared with you stands among your own things**, on Home and in the collections it came out of, as now — but it has to *say so*, clearly, and say who has which role on it.

**Every thing says one of three things about itself**, and nothing else: it is only on this phone; it is shared and everybody has it; it is shared and somebody is still waiting for it. Tap it to see who.

**The measure of all of it: somebody installs this and works it out in a few taps.** Said by the person whose app it is, and it is the test every screen here is put to.

**One mark, one box.** The mark says which of the three it is. Tapping it opens one box, the same whatever the thing is and whoever it belongs to, in the order it is needed: the state, as a sentence; **Sync now**; **Sync automatically**; the people and what each may do; how to add somebody, or how to leave. Built in v0.0.95 and v0.0.96.

**Sync now is one tap and goes both ways.** What is waiting goes, and everybody who has the thing is asked for what they have — `Receipt.ASK` — because nothing here can be fetched, and a Sync that only sends does nothing on the phone that is behind, which is the phone people press it on.

**Sync automatically is a switch**, on unless somebody says otherwise. How long it waits after the writing stops — 3 seconds, 10, 30, 2 minutes — is one quiet line under it rather than a row of choices in front of everybody.

**Nobody's writing is shown live, so people are told first.** When somebody starts writing in a shared note, the others see *Ana is writing…* on their copy. It is a flag and not a lock: they may still write, and sometimes two people will. When they do, changes to different lines are both kept, and the merge underneath is what makes the flag safe to ignore.

**Underneath all of it, a phone answers.** A note that arrives and is written down is answered with *I have it*, and only that answer counts as delivered — see *A phone that answers*, below. Built in v0.0.93, because none of the three things a note says about itself can be true without it.

In the order it is being built:

1. ~~The answer.~~ Done, v0.0.93.
2. The Share box as Drive has it, with roles that are rules. **Partly done, v0.0.101**: the box is two sections — *Who has access*, where a person wears a round initial and their role drops down, and *Syncing*, which is switches and one drop-down — and a role now **arrives**: changing one sends at once (`Post.changed`), and the phone given it writes down its own standing (schema 20, `standing`) and says *Admin* where it could only say *Can write*. **v0.0.102**: an admin on the receiving phone can add somebody and change what the others may do, and everybody but the owner can **Unfollow** — which, unlike stopping, tells everybody who has the thing, makes the copy here this phone's own, and is undone only by being given the thing again (see *Leaving*, below). **v0.0.108: the roles are rules.** A note shared to be read is read: its page takes no keyboard, its title is not for changing, and it says *Read only* under its name. What arrives from somebody who may only read a thing - or who has been taken off it - is not written down, whatever build they are on, and is answered so they stop sending it. And a copy this phone may only read takes what its owner sends rather than merging with it. *Remove* now tells the person taken off, by the road *Unfollow* uses the other way, and their copy becomes their own; see *Leaving*.
3. *Ana is writing…*
4. Taking away what this replaces: the chip that goes round four states, the two sets of marks, the toggle for whose a device is, the address list, and the box left over from the Minima Core design.

## What a rule reaches

Home holds notes and collections, and a collection holds notes, other collections and files, as deep as anybody likes. Sharing is set on a note or a collection, from its mark or its menu (⋮ → *Sharing* on the phone, *Share…* on the PC), and reaches everything under it:

| Where it is set | What it reaches |
|---|---|
| Everything: the code that offers the whole pad (*Connect my other device*) | every note and collection, including ones made later |
| A collection | that collection and everything inside it, however deep: its notes, its collections, and the files kept with any of them |
| A note | that note alone, with its files |

Home itself is not offered in the Share box ([HOME.md](HOME.md), decision 27): sharing everything is how the owner's own devices are joined.

Addresses are saved under a name the first time one is pasted, then picked from a list. Retyping a long address is how a note reaches the wrong person, so it is typed once.

A thing is reached by every rule on it and on every collection above it, up to Home, so a rule set high up keeps applying to things added underneath it later: share a collection once and next month's collection inside it is already shared. An address reached by several rules is one recipient, not several. `Sharing.audience` decides this from the thing's path and is unit tested (`SharingTest`, `SharingPathTest`), including that a rule on one collection or note never reaches another beside it. A new rule on a collection is written as `THING` and on a note as `PAGE`; a collection that came from 0.1 keeps the `COLLECTION` or `BOOK` its rows already have, so one person is never on it twice (HOME.md, decision 12).

**Until 0.2 (history).** The pad had three fixed levels, collections holding books holding notes, and the shelves went one level at a time: `All collections` → a collection's books → a book's notes. A rule was set with the arrow at one of four levels: everything, a collection, a book, or a note.

## Who you share with

You name the devices, and for each thing you share you say what that device may do with it.

### The four roles

Settled 2026-09-29 (0.1.040), at the owner's ask: "owner, that can do everything as changing others' roles, so 4 levels in total". Most power first, in the words both apps use wherever a role is shown or chosen (`Sharing.OWNER_DOES`, `Sharing.Level.does`):

- **Owner:** Made it. Can do everything, including changing anyone's role, admins included, and removing anyone.
- **Admin:** Can read, write and share it: add people who can write or read, change their role or remove them. Not the owner or other admins.
- **Can write:** Can read it and change it.
- **Can read:** Can see it and get changes.

The owner is not a level anybody is given: it is whoever the thing came from, or this device where it came from nobody (`cameFrom` empty) - and the owner is a person, so on the owner's own devices every device marked *My device* is the owner too, for anything any of them made (`NoteStore.ownersOwn`, `ownerMember`): one device's decisions about admins, and its *Remove*, are the owner's on the others. Somebody else knows only the device a thing came from as its owner; what another of the owner's devices decides about an admin reaches them in that device's next list. So there is one owner, and it is shown as *Owner* in the list of who has access - its line on a tap (phone) or under the pointer (PC) says what that means. Nobody can change the owner's role or take the owner off. The rules are in `Sharing` (`grantable`, `mayChange`, `mayCarry`) and the notebook asks them for both apps (`NoteStore.mayGive`, `mayChange`, `decide`, `give`):

| | gives | changes or removes | a list from them may say |
|---|---|---|---|
| Owner | Admin, Can write, Can read | anybody but themselves | anything |
| Admin | Can write, Can read | writers and readers | about writers and readers only |
| Can write, Can read | nothing | nobody | about writers and readers only (it is passed on) |

**Every role picker shows the roles that may be given, each with its line under it** - the code offer (*What may they do with it?*), the role on a person's line in the Share box, the same on a right-click (PC) or a long press (phone), and People and devices. The owner sees Admin, Can write and Can read; an admin sees Can write and Can read. A role that cannot be changed from here is shown, not offered.

**What arrives is held to the same rules** (`NoteStore.tookMembership`). From whoever the thing is from - or, for a thing not here yet, whoever sends it first - a list says anything. From anybody else it is not believed about the owner, about somebody who is an admin here, or in making somebody an admin: what the owner decides about admins reaches every device in the owner's own lists. So a build that let an admin do more cannot take the owner off, demote another admin, or make one. *Remove* from an admin is not taken by a phone that is an admin of the thing (`NoteStore.takenOff`); only the owner takes an admin off. A device says itself in its own list at what it may do, and at Admin only for its own things (until 0.1.040 every device said Admin, as the weakest decision there is), and somebody nothing is known about yet who sends a thing is written down as Can write, not Admin, until a list says otherwise.

**Before 0.1.040**, what the code let an admin do was more than an admin should: give Admin; change or remove another admin (Share box and People and devices, both apps); change or remove the owner from People and devices (both apps) and from the PC's Share box - which the owner's own phone ignored, but which travelled in the list, so a third device took the owner off and stopped taking in the owner's notes; and send *Remove* to another admin, which that admin's phone obeyed. Any device's list was believed about anybody.

**The code offer carries Admin** (`Pairing.write`, `Hello`). The line's mark stays `w`, so a build from before reads an Admin offer as Can write, never more; the level's number follows the scope after a colon (`BOOK:3`), which a build from before only quotes back and this one strips (`Pairing.scopeIn`). The hello answering it says the level in the byte that said *writes* - 0 read, 1 write, 3 Admin - which a build from before reads as *writes*. The offering device gives the less of what it offered and what the hello claims, so a build from before that scanned an Admin offer is given Can write. `PairingTest`, `HelloTest`, `SharingTest` and `DesktopRolesTest` pin it all.

It is decided per share, so the same device can have one collection of yours to read and another to work in. Whether a device is *yours* — a tablet rather than a friend — is said once on the device, and decides only which mark the thing wears.

**Read is a rule, at both ends.** On the phone given a thing to read, the page takes no writing: no keyboard comes, the title is not for changing, an old version is to look at and not to put back, and the line under the name says *Read only* (once, on a tap, the page says whose it is to change). On every phone that receives, what arrives is weighed against what the sender may do with the thing *here* — by every rule that reaches the note, the most any says — before a word of it is written down. A reader's words, or those of somebody taken off, are not; and they are answered all the same, because a phone that is not answered sends the same thing every quarter of an hour for ever. The rule is asked *after* the list that came with the note has been folded in, so the first thing ever to arrive from somebody is reached by the line saying they have it.

**A copy you may only read is a copy.** What its owner sends is what it says: nothing on it is merged with what arrived, and where the copy said something else - written in on a build that let a reader write, or before they were made one - that is kept as a version and the page says what the owner says. The one thing still set aside is what is older than what already came from the same phone.

Nothing leaves the phone unless you named a device. A note can be shared by where it sits — that is what setting a rule on a collection means — which is exactly why moving something between collections has to be confirmed.

What other devices share with you stands among your own things, on Home and in the collections it came out of, and says on itself where it came from. It is not kept apart in a box of its own.

## A membership everybody keeps

Every device that has a thing is written down, by the key it signs with, with its level and when that level was decided — and that list travels with the note. Without it every copy but one cannot say who else is reading, and only the phone that began the sharing could ever hand it on.

**A device, not an address.** An address moves; a key does not. The same phone at a new address is the same member rather than a second one.

**Two admins deciding at once settle by themselves.** Per member, the later *decision* stands — not the later message, which would depend on the weather. It is the only rule two phones out of touch with each other can both apply and agree on afterwards.

**Being taken off is something rather than nothing.** A removed member stays in the list at `GONE`. If they simply vanished, the next copy of the list from a phone that had not heard would put them back, for ever. `MembershipTest` pins that, the ordering, and that a level from a later build reads as the most this one knows rather than as nothing.

**A member with nowhere to reach them is not written down.** A phone does not know the address others reach it at, so it puts its own in the list it sends; a member who still arrives without one is placed from the note they came with or from a device already known, and otherwise left for the next list.

## Moving something changes who can read it

Deleting for good, from the bin, also deletes every rule that pointed at the thing and at anything inside it, so an address never keeps a claim on something that no longer exists. Archiving and binning change nothing about who a thing is shared with: it is still there, and putting it back puts back the rules with it.

Notes and collections can be moved: ⋮ → *Move somewhere else* on the phone or *Move to…* on the PC, or by dragging an icon onto a collection, out of a collection's card onto Home, or a note onto a note, which makes a new collection holding both. A collection goes onto Home or into any collection that is not inside it. Dragging an icon between others only reorders it — it stays where it lives, so nobody's audience changes and nothing is confirmed. Before anything is written, the app works out the audience that thing would have in its new home and compares it with the audience it has now, and any difference has to be confirmed by name — *Starts reaching: Ana (someone else)* — because dragging a note into a collection its owner shares widely is a disclosure, and dragging it out is a withdrawal. A move that changes nobody is simply done. `Sharing.moving` decides this and is unit tested, including that a rule set on the note itself follows it and so counts as neither.

## Nothing is fetched: it is sent

Maxima carries a message from your node to a contact's node. It is not a shelf the other end can come and read, and there is no call that asks it for your own notes back: a node cannot retrieve its own notes through Maxima, because Maxima does not hold them. Anything that arrives anywhere arrives because a device sent it to an address that was listening.

Two things follow, and both are visible in the app.

**The sending side has to remember.** For every address and every note, the pad records which revision got through. Everything written after that is owed again. That record is the only thing that can say whether an address is up to date, so it is what the `↑` on a row is reading, and it is why only a delivery can clear one — never the app deciding that enough time has passed. `Outbox` decides what is owed and is unit tested, including that what one address received says nothing about another, that a note reached by two rules is owed once, and that a clock that went backwards cannot resurrect a delivery that already happened.

**Your other device is a contact like any other.** It runs its own node with its own address; you name it as a device of yours, and this phone sends to it. It receives when it is running and reachable, which is why the outbox keeps waiting rather than assuming. Sharing with yourself and sharing with someone else are the same mechanism; only the direction differs.

**It goes by itself.** What the open note owes is sent once the writing has stopped for as long as that thing asks for — 3 seconds, 10, 30, 2 minutes, or only *when I ask*, set on the thing's own card and inherited from whatever holds it when it is not. A shopping list two people are reading in a shop and a diary being written into are not the same thing. Everything owed to anybody is also sent when the pad opens, so a phone that was off while somebody wrote catches itself up.

`⋮` → **Sync now** says what a thing owes and to whom, and sends it. Each note is sealed once per address that is owed it and handed to this phone's own node; a delivery the transport accepted writes the row that clears the mark, and nothing else does. An address that has never been paired has no key to seal for, and is counted as a failure with a reason rather than skipped in silence.

Minima Core carries none of this and never did — Core's build has no Maxima in it at all. The transport is vendored and runs inside the app, which is why the pad is its own node rather than a client of one.

## The shelf travels with the note

A note on its own is not much use to the end receiving it. It came out of a collection, perhaps inside
other collections, and those are most of what says what it is *about* — dropped into whatever collection
happened to be first on the other phone, the same note means something else.

So the sealed payload is a **parcel**: the ids and names of the two collections nearest above the note (the
fields a 0.1 build reads as its collection and its book; HOME.md, decision 15), the note's title and body,
and one flag saying what this share lets the far end do. It is the plaintext the envelope seals, so all of
it is exactly as private as the note is.

**The path tail** (since 0.2.001; HOME.md, *The parcel's path tail*). After the history, a parcel carries
the note's whole ancestry: for each collection above it from Home down, its id, name, icon, tint and place,
and then the note's own icon and picture (worn since 0.2.004). A reader that has it builds what is missing
from the top down and moves nothing already here; a collection's tint and place are used only when it is
first made here, its name and look are followed (decisions 17, 35, 36). A 0.1 build stops before the tail,
as it skips anything it does not know, and files the note by the two old fields.

**A collection travels on its own, as a carton** (`Carton`, `MNC1`; HOME.md, *Messages*). A note carries
the collections above it, but a shared collection with no note in it, or one with a look or files of its
own, would never reach anybody that way. So it goes by itself: its id, name, icon, tint, picture, path,
who has it and its list of files, sealed and answered like a note under the envelope id `Things` gives it,
and sent only to a device that has said `TREE` (below).

**The shelf is matched by id, never by name.** A second note out of the same collection has to land beside
the first. Matching on the name would build a second collection the moment somebody renamed theirs, and
would merge two different collections that happened to share a name.

**Their id is not reused as it stands.** Every pad is made with the same first collection and the same
collection inside it (a 0.1 pad's first book), under the same two ids — `collection-first` and `book-first`.
A note shared out of somebody's first collection and filed under the id it arrived with would land inside
*your* first collection and look like something you wrote; two people's collections would silently become one. So the local name for a shelf of
theirs is `Parcel.localId`: a digest of who sent it and what they call it. The same every time, so the
second note lands beside the first; different for every sender, so no two people's shelves can be confused
for each other. `ParcelTest` pins both halves of that.

A shelf built this way is marked `theirs` with the address it came from. What they call it is followed on
every arrival — a rename at their end is a rename at yours — but **where you put it is yours**, and a note
already here is never moved: somebody else deciding where your copy lives, every time they touch it, would
undo any tidying you had done.

A note sent by a build that had no shelf to send still reads. It comes back with no collection, and goes
where anything whose shelf we were not told goes.

**Older builds, and `TREE`** (HOME.md, *Messages* and decision 5). A 0.2 device says `TREE` (`Receipt.TREE`,
22) once a run to every paired device that has answered anything, as it says `PERSONS`, and writes down by
key who says it back. A device that has never said it is a 0.1 device. It is sent what fits its three
levels - a collection on Home as a collection, one a level down as a book, a note under them as a note,
each with the old two fields - and nothing else: a note deeper than that, a carton, a collection's look or
its files wait, counted as owed to that device, and its mark's box says *"Ana's phone needs to update
Mininotes to receive this"* (`Unsent.Why.NEEDS_UPDATE`; see *When something could not go*). They go the
moment that device says `TREE`. Everything a 0.1 device sends is taken in.

## Which way it is going, on the thing itself

Every note and collection carries a mark saying where it stands — on its icon's corner, at the top of its
card, on the line under a note's title and at the end of its line in the tree — drawn rather than taken from
the font: marks that have to be told apart at twenty pixels cannot be left to whatever typeface the phone
happens to use.

**Since 27 September 2026** the mark is one of six, the same on both apps (`SyncMark`; see
[PARITY.md](PARITY.md)): only here, waiting to go, sent and not yet confirmed, everybody has it, paused, and
not heard from; a thing only on this device wears the quiet grey ring rather than nothing. Settings → *What
the marks mean* says each. The table and the paragraph under it are the marks as they were before that.

| Mark | Means |
|---|---|
| an arrow leaving, outlined | you are sending this to somebody |
| arrows both ways, outlined | it is going to another device of yours and coming back |
| an arrow arriving, filled | it came from another device |

Nothing is drawn on a thing that is only on this phone: that is what everything is until it is shared, and
a mark on everything marks nothing.

The mark is also the control, because the question it raises — *who else has this?* — should be one tap
from the question. Tapping an outgoing mark opens who has it and what each of them may do. Tapping an
incoming one opens who shared it, what you may do with it, and the way out.

**"Another device", not "someone else".** What arrives may be from your own tablet as easily as from a
friend; both are devices, and calling both of them somebody else was wrong half the time.

## Unsubscribing, and what it cannot do

You can stop taking in what another device shares.

It **cannot stop them sending**. Only they can decide that, and no phone gets a say over another. What it
stops is the arriving being put on your shelves — which is the part this phone owns. Nothing new turns up;
what is already here stays until you delete it like anything else. The box says exactly that rather than
leaving somebody to work it out from what does not happen.

Refusing a collection refuses everything under it, however deep — otherwise unsubscribing from a collection
would stop nothing at all. A refusal is one row of `refused`, keyed by the
address and the thing, and it is checked before anything is written. The shelf is looked for under the name
it has *here* and by device rather than by address: until v0.0.102 it was looked for under a name made from
the sender's address, where shelves are filed by the sender's key, so pausing a book paused nothing.

## Leaving

**Pause receiving** is this phone's own business: it tells nobody, it wears the pause mark, and *Resume*
undoes it. **Unfollow** is a different thing, offered to everybody but the owner, and asked once.

- Everybody who has the thing is told, in the five bytes of an answer: *left a note*, *a book*, *a collection*
  (`Receipt.LEFT_*`). Since 0.2 a collection is said as a 0.1 device would call it, by how deep it sits: on
  Home, a collection; one inside that, a book (`Things.oldScope`). Which note rides in the envelope; for a
  collection it is any note out of it, and the phone that hears finds the collection from its own.
  **When** rides where a revision does. A collection deeper than that has no word yet: leaving it is written
  down here and nobody is told (see *Not built*).
- The phone that hears takes them off **as a decision made at that time** — a row at *gone*, which an older
  list cannot undo, and which a *later* decision about them is not undone by. It stops waiting for their
  answers, and says *Ana unfollowed a note*.
- On the phone that left, what is here becomes its own (`NoteStore.letGo`): no sharing rows, no standing, not
  *theirs* — though where it came from is kept — and the empty ring for a mark. A shelf that was only ever
  here to hold what they sent becomes an ordinary shelf, and the worked-out line saying they have it goes.
- A row of `refused` with `gone=1` stays. What still arrives from somebody who has not heard is dropped **and
  they are told again**; and because the first telling may go to a phone that is asleep, it is said again at
  every opening for a week. Nobody answers it, so there is no knowing.
- **Being given it again brings it back, and nothing else does.** What arrives carrying this phone's own
  entry, decided *later* than the leaving, is an invitation: the leaving is forgotten and the note is theirs
  again.

**Remove is the same thing from the other side** (v0.0.108). Whoever has a say in a thing - its owner, or an
admin of it - takes somebody off it, and that is written down as a decision at *gone* with when it was made,
never as a row deleted: a deleted row came back with the next list, and the next word the removed person wrote
wrote them back in, since whoever sends a thing is written down as having it.

- The person taken off is told, in the same five bytes as leaving, the other way (`Receipt.REMOVED_*`, said by
  depth as leaving is, and not yet for a collection deeper than a book was): which note in the envelope, when it was
  decided where a revision goes. Their phone takes it only from somebody with
  a say in the thing, and only for a thing that is theirs, and then does what it does on leaving
  (`NoteStore.takenOff` → `letGo`): the copy is its own as of that moment, and everybody who had the thing is told
  they are off it - which reaches whoever else had not heard.
- Everybody else hears in the list, which goes at once with one note out of the thing (`Post.changed`).
- The telling is said again at every opening for a week (`Post.removedAgain`), as leaving is; a phone that has
  already heard, or that left by itself, does nothing about hearing it again. And whatever the removed phone
  still sends before it hears is not written down, and is answered with the telling.
- Being given the thing again brings it back, exactly as after leaving: an entry for this phone decided *later*
  than the taking off.

## What a file kept with a note does, which is go with it

A note can keep files: copies in the pad's own folder, one row apiece, drawn as cards under the writing. Since schema 24 (built, not yet seen between two real devices - see the end of this section) a file kept with a **shared note** goes with it, to every device the note reaches, on the phone and on the PC alike. Since 0.2.004 a file kept with a **shared collection** goes the same way with the collection. `Enclosure` decides what each end does, and is unit tested.

**A file never rides in a message.** A Maxima message is a message, not a transfer. So a file is handed to the transport's own media service, which seals it once under a key of its own, cuts it into pieces named by their own hash, keeps every piece on this device (pinned, so it is never evicted here) and leaves copies on a couple of relays. What travels is the **list**: every file the note keeps, and for each one that has gone up, the few hundred bytes that say where its pieces are and the key that opens them. The list rides inside the note's own sealed envelope (`Parcel`, after the flag that says a build carries), so it is exactly as private as the note; a relay holding a piece is holding noise.

- **A note's own files, and a collection's.** A collection's list rides in its carton (see *The shelf travels
  with the note*), and its files are published, listed, fetched and answered for exactly as a note's are, with
  the collection's id where the note's goes (HOME.md, step 4). A carton goes only to a device that has said
  `TREE`; to a 0.1 device a collection's files wait, and it is told it needs to update. Until 0.2.004 a file
  kept with a book or a collection stayed on this device.
- **16 MB at most** - the transport's own ceiling for what relays can hold in time. A bigger file is listed by name but not sent, and its card says *Too big to send*.
- **Up only when a relay has it.** A phone behind a router cannot hand its pieces to anybody directly, and a PC whose door is open today may not be tomorrow, so a file counts as gone up only once a relay has confirmed every piece - the device's own door in the list does not count; until then it is tried again after a minute, two, four, eight, a quarter of an hour, and then hourly. That is with helpers; only between the owner's devices there is no relay, and a file is offered from its owner's door instead (see *Two ways notes travel* below).
- **The list goes whole or not at all, and "no list" is not "nothing on it".** An empty list takes out everything that came from that device; a list not said - an older build, or a note so long that not even the names fit - takes nothing out. A list that would not fit in the envelope stops saying where the biggest files are first, and those say they are not with everybody yet.

**On arrival**, after the note itself is written down, the device fetches in the background whatever is listed and not here: pieces from the door of the device that listed it, where that door answers (on the same network, or at a public address it proved), then from the relays the list names, then from any relay it is attached to, each piece checked against its hash and the whole file against the hash and size the list gave. It is kept the way any file is - sealed with the notebook's key while the notebook has a lock, the row written last - and marked as having come from that device. Then it tells everybody who has the note *I have this file* (`Receipt.FILE_HERE`). That can be lost - a device out of reach just then, or one that has not fetched it yet itself - so a device also answers every list that arrives with the files it names that are here (once a run for each file and device), and a device still fetching a file believes those who say they have it (2026-09-29; see DIRECT.md, *Amber on all three*). A fetch that fails is tried again on the same clock as going up; every fourth failure tells the device that listed it *I cannot get this file* (`Receipt.FILE_MISSING`), and that device sends it up again unless it went up in the last ten minutes - a relay's shelf is small and lets things go.

**Taken out by whoever put it in, and by nobody else.** A file that came from a device goes when that device's list stops naming it. A file this device added is never taken out by somebody else's list. A file taken out here is written down as taken out, and a list that still names it does not fetch it back - here or, for a file of this device's own, from a list that set off before it heard. Only the newest list from each device counts, by that device's own clock, so a list carried the long way round changes nothing.

**Said, never assumed.** A file shows as with everybody only when everybody the note reaches (but the device it came from) has said so. Until then its card says *Not sent yet* or *Not with everybody yet*, and the line under the note's title counts only what is not everywhere: *up to date · 1 file only here* (too big), *· 2 files still going*, *· 1 file on its way* (listed by somebody, not fetched here yet). The list goes again to whoever has not said they have a file - at most six times, because a build from before files travelled never says so; after that the card goes on saying it and Sync sends it again.

**What is not built, or not known.**

- **Not seen between two real devices.** What is tested is two synthetic devices with a shelf both can reach standing in for a relay (`DesktopFilesTravelTest`), and a file at the 16 MB ceiling sent up and fetched back through the transport's own code (`EnclosureTest`). Whether the public relays take and give back pieces from this app, how fast, and how long they keep them, has not been watched.
- **A large file on a slow connection may not go up.** The transport gives going up 55 seconds and counts a relay only if it took every piece; sixteen megabytes to two relays in that time wants a few megabits a second upward. What fails is tried again, whole, under a new key.
- **Taking a file out does not take it out everywhere.** A file somebody else put in, taken out here, is gone from this device only; the note's other copies keep it.
- **It costs memory and room.** A file is read whole to go up and put together whole on arrival - a sixteen-megabyte file wants several times that while it is in hand - and every file that has gone up is kept twice here, once as itself and once as its sealed pieces.
- **A device that took a file in from somebody passes on what that somebody sent up**, and sends it up again itself only when somebody cannot get it.

A backup is still the way to move a file bigger than 16 MB.

## Sending files

Built 2026-09-27, tested on one machine, not yet seen between real devices. Asked for by the owner as "a kind of drop box where files could be dropped and not attached to any other assets", after Snapdrop: two devices on the same network hand each other a file, and neither has to write a note to hang it on. `Drop` decides what each end does, and is unit tested.

**Where it is.**

- **Sending, on the PC.** ⋯ → *Send files…* (under Mininotes) picks files, then the box: how many and how big, their names, a line for any that cannot go and why, and the paired devices by name - the owner's own first, saying *My device* - one chosen, and one button, **Send**. A file's own menu has *Send to a device…*. Files dropped from Explorer where nothing keeps them - on the toolbar, say - open the same box; on a note's icon or the open note they are attached to it, on a collection's icon or card they are kept with it, and on Home's grid, the search or the dock they are kept on Home (`DesktopDrops.aim`). Dropped on the box they join the others, on a device in it they go to that device.
- **Sending, on the phone.** ⋮ → *Send files* opens the phone's own picker for several files, then a box of the paired devices, own first; a tap on one sends. A file's own menu has *Send to a device*. The *Add to Mininotes* box that comes from another app's share sheet has, under the notes, **Or send to** and the same devices.
- **What came lands on Home** (since 0.2.002 on the phone and 0.2.003 on the PC; HOME.md, decision 6): an icon after the notes and collections, its kind in its own letters, with *new* on it until it is opened. It is then a file like any other. Its menu, the same on both apps, is *Open*, *Save a copy*, *Send to a device*, *Put in a note*, *Move to…* and, under a line, *Delete*; carried onto a note it is that note's attachment, onto a collection it is kept there. Search finds it by name. On the phone the notification that files came opens Home, and asks about any sending still waiting for an answer.
- **⋮ → Sent files** (⋯ → *Sent files…* on the PC; HOME.md, decision 20): at the top, while there is any, what is *Coming to this phone* (or PC) - somebody asking, to accept or refuse, and files still on their way, with how many are here - then *Sent*: each sending from here with how many files, when, and where it stands (*Waiting*, *Delivered*, *Refused*), and a way to stop it or take it off the list. With nothing sent yet, it says where files are sent from.
- **The Drop box (history).** From 2026-09-27 until 0.2.002 on the phone and 0.2.003 on the PC (it replaced the *Received files* box of the menu, at the owner's ask: "a dropbox in the tree, a separated branch of collections"), what came was in the Drop box: on the PC a line at the top of the tree level with *All collections*, filling the main area with a tab of its own, its files as cards or as a list sorted by Name, From, Date, Size or Type (`DropList`); on the phone a tile after the collections and the last line of Tree view, a list with one **Sort by**. It held what somebody wanted to send, every file that came with **Open**, **Save a copy…**, **Put in a note…**, **Send to a device…** and **Delete**, what was still coming, and under *Sent* each sending from here; its one primary action was *Send files…*. Received files on Home and *Sent files* replaced it. The offer, the asking and the answers below did not change.

**Who is asked.** Files from one of the owner's own devices (marked *My device* here) are taken at once, as that device's notes are. From anybody else paired here the person is asked: *Ana wants to send you 3 files (2.1 MB)* - **Accept** or **Refuse** - in a box if the pad is open, in a notification if not; nothing is fetched until they accept. **Refuse** tells Ana. A device not paired here sends nothing that can be opened as theirs, so it is not asked about. Arrivals are said the same way: *Ana sent you 3 files*.

**Where it stands, at the sender**, one of four: *Sending…* while it is made ready; *Waiting for Ana* - handed over, or not reachable just now, either way not yet answered (*Waiting for Ana to update Mininotes* while Ana's device has not said it takes files); **Delivered** once Ana's device says every file is there - and only then; *Refused*. Once delivered or refused, the copies kept to send it and their pieces are let go; the line stays.

**How it travels.** A file never rides in a message, as with a note's files. On *Send* each file is copied into the pad's own folder (sealed with the notebook's key while it has a lock) and then, in the next round of file work, sealed under a key of its own and cut into pieces (`MediaService`), and the pieces kept on this device, where its door hands them out.

- **The offer** is a message of its own format (`Drop`, `MND1`): every file's id, name, kind, size and the few kilobytes that say where its pieces are and the key that opens them; at most 20 files, each at most 16 MB, the whole of it inside one `Envelope` sealed end to end for the one device. The envelope names the sending where it names a note, and says which offer of it this is where a revision goes, so a later offer replaces an earlier one and an earlier one arriving late changes nothing. It goes the way everything goes to a paired device (`Direct.send`): its door on this network, its door proved in public, its home (the owner's PC, for a phone), and - only with helpers - the relays.
- **The pieces** are fetched by the receiver from the sender's doors first - on this network, or at a public door it proved. Only with helpers, and only where the receiver cannot be heard on this network and this device has no door proved in public, do they also go up to relays before the offer goes. A receiver that finds it cannot reach the door says so at once (`Receipt.DROP_MISSING`, then every fourth failed try); with helpers the sender then sends the pieces up and offers again. **Only between the owner's devices** nothing goes to a relay: the pieces wait at the sender's door, and the files come when the two devices are on the same network - or when the receiver can reach the PC's door.
- **The answers** are five-byte `Receipt`s with the sending named in the envelope: every file is here (`DROP_HAVE`), refused (`DROP_REFUSED`), cannot be had from where it was said (`DROP_MISSING`). A relay or a door taking the offer clears nothing: the sender offers again - after a minute, two, four, eight, a quarter of an hour, then hourly - until the answer comes, and a receiver that has settled a sending answers the same again.
- **Older builds.** A build from before this would read an offer as a note written the oldest way and put it on a page. So nothing is offered to a device until it has said it takes files: at the end of a pairing hello (`Hello`, a byte a build from before never reads) or in five bytes, `Receipt.TAKES_FILES`, said once a run to every paired device that has answered anything - which every build since answers reads as a later build's answer and leaves alone - and said back to whoever says it, unless it was said to them in the last minute (`Drop.sayBack`). Until 2026-09-27 it was said back only to a device not yet told this run: a device that said it first, to a build from before, never said it again, and the other one - updated since - offered nothing and said *Waiting for … to update Mininotes* for as long as the first one's process lived. A sender still waiting also says it again at each try, so the answer comes back even when one telling was lost.

**What is kept.** Schema 26 (`transfers`, one row per sending either way; `transferred`, one per file), additive. A received file's bytes live in the pad's own folder beside the attachments, sealed the same way while the notebook is locked, sealed and opened with them when the lock goes on or off, and lent to other apps the same way. Its name here is made here, never the sender's. Since schema 31 (0.2.002; HOME.md, step 2) every received file is also a file kept on Home - a `files` row under its own id, `fresh` until it is opened, its `transferred` row marked `moved` rather than deleted, the bytes not copied - so it is in backups and found by search. On Home it is in no note's list, and nothing about it travels further. **Put in a note**, or carrying it onto a note, makes it that note's attachment - the same bytes under the same id - and from then on it is a file of that note and goes where the note goes; moved into a collection, it is that collection's. Until schema 31 a received file belonged to nothing and was in no backup.

**Not built, or not known.**

- **Not seen between two real devices.** What is tested: the format, its bounds and older builds (`DropTest`); two real nodes on one machine with no relay, the offer through one door, the pieces from the other, the answer back, the stand-in relay never dialled, and a refusal, and a shut door (`DropDoorTest`, both test tasks); and the notebook and arrival path on the PC with synthetic devices (`DesktopDropTest`): own device taken at once, anybody else asked, refused, a later offer, delivered, *cannot get*, put in a note, the lock, schema 25 → 26. `Post`'s own round - making ready, offering, fetching - runs only in the app, whose node these tests do not start.
- **The phone's screens have not been looked at.** They follow the boxes already there; nothing has run on a phone.
- **A phone sending to a phone away from home**, only between the owner's devices, waits until they are on the same network: a phone's door cannot be reached from outside, and the PC's home keeps the offer, not the pieces.
- **Stop sending** stops offering; a receiver already fetching goes on trying, hourly, and says *coming* until its owner stops it.
- **A PC's Open** writes a plain copy to the system's temporary folder for the program that opens it, deleted when Mininotes closes - also while the notebook is locked.
- **A pad with no room** (500 MB of files) cannot take what comes, and goes on trying; the sender goes on saying *Waiting*.
- **Two devices paired before this and never sharing anything** do not know each other takes files until they share a note or pair again: the five bytes go only to a device that has answered something.

**The log.** Every step of a sending is written to the phone's log under the tag `Mininotes/Drop` - counts and states only, never a name, an address, a key or a file's name: the offer made (how many files, how many bytes), waiting because the other device has not said it takes files (and whether it was told again), where the pieces were kept, the offer handed over and by which road (*a door*, *left at a home*, *the relays*, or *not taken*), every answer heard; at the other end the offer heard (from a device paired here or not, one of the owner's or not), taken at once or put to the person, each file's fetch and how many of the sender's doors answered, *every file is here* said, and *this device takes files* said and heard. `adb logcat -s Mininotes/Drop Mininotes/Post` on both phones shows where a sending stopped. `Mininotes/Post` now also says why a copy left to be carried was not kept (for a device not paired here, a build that has not said it carries, or one addressed to the device that left it).

## One person on every device

Built 2026-09-28 (phase "A + B0" of the Drive-style plan), tested on one machine, not yet seen between real devices. Until now every device was somebody on its own, and one name had to say both who you are and which device it is: "Charles" on one list, "Pixel 7 Pro" on another, and neither right in both places. `Persons` decides what each end does, and is unit tested.

**Two names per device (A).** Profile has two, each changed where it stands (on the PC, saved as typed):

- **Your name** - *What other people see.* The same on all your devices, and what goes out everywhere it went before: the pairing code, a hello, your own line in the list of who has a thing, the node's name on the network, and so who a sending of files says it is from. Kept as `settings/me`, with when it was chosen (`meChanged`). Never chosen, the phone's own name stands in as it always did, and Profile says *Choose the name other people see.*
- **This device** - *What your own devices call this one.* Kept as `settings/device` with `deviceChanged`; until chosen, the maker's name on a phone (`Build.MODEL`) and Windows' computer name on a PC. It is said only to your own devices, and shown only on your own screens.

**People and devices** is in two parts: **My devices** - this one first, *Pixel 7 Pro (this one)*, then each of yours by what you call it - and **People**, by the names they chose. The *My device* switch moves a device between the two. On your own devices `addresses.name` for another device of yours holds its device name; for anybody else, the name they chose.

**One person id (B0).** Every install makes one: sixteen random bytes and when they were made (`settings/person`, `personMade`). Two devices share one **only** when both have said the other is theirs:

- **The bond is "Connect my other device"**: a code offering everything (the `LIBRARY` level). The device that scans it marks the other as its own; the one that showed it, on handing everything over (`giveItTo` on the phone, `accepted` on the PC), does the same (`Post.bonded`). Any other code - a plain one, or one offering a single thing - no longer makes a device *My device*: until 2026-09-28 every scanned device was saved as the owner's. The switch in People still sets it either way, as before, and a device already marked keeps its mark.
- **Whose id both keep**: the one made earlier, and the lower id where both were made at the same moment (`Persons.wins`). The other device takes it and keeps its old id as an alias, so a card still saying the old one is known for what it is. Either device works it out alone and gets the same answer.
- **Your name is one name**: chosen later on any of your devices, it is your name on all of them. **Each device's name**: the later decision stands per device (`addresses.named`); a device is believed about itself where the two decisions are as old as each other (both never chosen, say), and a device's own name is never changed by another's card.

**What travels**, only between your own devices:

- **The hello's tail.** After the flag that says a build takes files, a hello accepting everything adds: a flag *knows about persons*, the id (16 bytes), when it was made (8), your name and when it was chosen, this device's name and when (each name as a 4-byte length and at most 80 bytes of UTF-8, then 8 bytes). A build from before stops reading at the files flag. A damaged tail is ignored on its own: what came before it is a whole hello from before. Read only on the bond, only to decide whose id both keep and what to call the device. A hello to anybody else carries none of it.
- **"This build knows about persons"**: five bytes, `Receipt.PERSONS` (18) - or `PERSONS_MINE` (19), *and you are one of my own devices*, to a device marked as the owner's. Said once a run to every paired device that has answered anything, again when which of the two it would be changes (the switch), and said back as *takes files* is (`Drop.sayBack`). A build from before reads an unknown number as a later build's answer and does nothing (`Post.arrived`, since 2026-09-21).
- **The card** (`MNA1`, `Persons.wrap`): the magic, a format byte (1), the id (16), when it was made (8), how many aliases (1) and each (16, at most 16), your name and when it was chosen, how many devices (1, at most 32) and for each: its signing key and its agreement key (short form, 2-byte length each), its address, its name (2-byte lengths, at most 1024 and 80 bytes), its state (0 yours, 1 yours no longer) and when it was decided (8). The sending device lists itself first. Sealed in an ordinary `Envelope` whose page is the id and whose revision is the newest decision on the card, so one arriving after a later one from the same device changes nothing. Nothing may follow the last device; half a card is no card.
- **Who is sent one**: a device marked as yours here, that said `PERSONS_MINE` - so yours at both ends - and nobody else, ever (`Persons.goesTo`). A build from before would read a card as a note, which is why it waits for the five bytes. Sent when anything on it changes, and otherwise once a run (the same card is not sent twice).
- **Who believes one**: a device that has the sender marked as its own, from a card that lists the sender itself and this device as yours (`Persons.take`). It adopts the id by the rule, takes your name if chosen later, and writes the device names of the devices it lists as yours and already paired here - *My device* here or not, since 0.1.029 - into `addresses.name`. A device never paired here is not added from a card (that is the next step). A card is read by its first four bytes before a note would be, and never inside something carried.

**Seen on 0.1.027, and put right.** The owner renamed the Pro, the GrapheneOS phone and the PC; both phones showed the new names, the PC did not, and both phones handed a card every half-minute round. What the code shows, since the PC wrote no log: *names reach a device only for devices it has switched to My device* - a phone paired with the PC by a plain code, or linked through a list, is nobody's on the PC since 2026-09-28, so the PC takes no name for it, and sends it no card; the phone counts the PC as its owner's all the same, and nothing said so. Now People and devices says it under that device, on both apps (`Persons.oneSided`): *It counts this PC as one of your devices. Turn My device on here too so names travel.* - or the other way round, *...Turn My device on there too...*. And a People and devices box open on the PC when a card renames a device is drawn again (`Landed.devices`). *Cards going round unchanged:* the same card is now the same devices in the order of their keys, and "the same" leaves out the addresses (`Persons.sameness`), which nothing reads off a card yet and which move with a relay; "knows about persons" is said back, and the card sent again because of it, only where it is news - the first this run, the other of the two, or the first in ten minutes (`Persons.news`) - where before two devices whose words took over a minute to cross went on answering each other. A card taken in counts only the names that changed here, and a name chosen later is kept even where the words are the same. Tested in `PersonsTest` and `DesktopPersonsTest` (the PC's notebook through `Post.arrived`); not yet seen between the three devices.

**Seen on 0.1.029, and put right.** The PC still showed both phones by their old names. Its log: one phone *My device* on the PC and the other not, and each card from the first *taken in, nothing new*. What the code shows: a card writes names only where the time each was decided is later than the one kept (`Persons.later`), and nothing writes a later time than a decision - a pairing keeps the name and time of a device already yours, and gives anybody else's 0 - so that part stands; but the card from the phone that is yours at both ends lists the other phone as yours, and the PC took no name for it because the switch was never turned there. Now **a device a card lists as yours is called what you called it**, marked *My device* here or not: the card comes from a device that is yours at both ends, which is the owner's word (`NoteStore.devicesNamed`, `ownDeviceNamed`). The switch stays the owner's to turn - it is what makes notes go both ways and that device's own card believed - and People and devices says so under it: *Another of your devices counts it as one of yours. Turn My device on here too.* (`Persons.LISTED_AS_YOURS`, remembered as `post/listedAsYours`). The rounds on a note already read the name kept here (a list naming a paired device is filed where it is); a row left from before that at an address it had then now shows what it is called here too, found by its key, not the name the list gave it (`NoteStore.nameFor`). A card for a device out of reach - only between your devices, its door not heard - no longer keeps the card from the rest, and waits for the next round. Tested in `DesktopPersonsTest`; not yet seen between the three devices.

**Kept.** Schema 27, additive: `addresses` gains `person` (whose device of yours it is), `via`, `gone` and `named`; a table `persons` (id, name, changed, aliases, mine) and an index on `addresses(person)`. `gone` and `persons` are for the steps after this one and nothing writes them yet (`via` is written since *Linked through what you share*, below); the owner's own id, aliases and names live in settings.

**Not built, or not known.**

- **Not seen between two real devices.** Tested: the hello both ways round and a damaged tail (`HelloTest`); the card, its bounds, whose id is kept, the later decision per device, and every card that is not believed (`PersonsTest`); three real nodes on one machine, two of one owner ending as one person with each other's device names and a third, somebody else's, sent no card (`PersonsDoorTest`); schema 26 → 27.
- **Two devices bonded before this build** know each other as their owner's only where each has the other switched to *My device*; until both have, no card goes. Turning the switch on at both ends is enough.
- **A device of yours that is not paired here** is not learnt from a card, and one marked *yours no longer* is not acted on: both are later steps. So is *Ana is writing…*.
- **Leaving one of your devices' People list** (the switch off) stops cards going to it at once; what it already had is not taken back.

**The log.** `Mininotes/Persons`: what was said and heard, cards handed and why (changed, or none gone since the start or their word), taken in and how many names changed, and why one was not - never a name, an id, an address or a key. On the PC it is in `%LOCALAPPDATA%\Mininotes\logs\mininotes.log` (see `windows/README.md`).

## Linked through what you share

Built 2026-09-28, tested on one machine, not yet seen between real devices. The owner's decision: *as soon as a device is linked to a thing, it is linked through that thing to every other device that has it.* Until then a device could seal only for devices it had paired with, so in a note A shared with B and C - A paired with both, B and C never with each other - what B wrote never reached C, and both showed C or B as a dashed round saying *not linked*. `Linking` decides what each end does, and is unit tested.

**The key to seal for rides on the list.** Sealing for a device takes its agreement key, and a list of people carried only the key each device signs with. So every device on a list now goes with its agreement key too (short form, 33 bytes): `Parcel.Member.agreement`, written after the list of files as a count and one length-prefixed key per member (empty where the sender has none), in the list's order, whenever there is at least one to give. Only there, and only when a list of files goes: a build from before reads the files and stops, where with no list of files it would read these bytes as one, fail and take the note for bare text written over somebody's words. A note too long for its list of files goes without the keys too. A device writes the key it holds for each member still on the thing (nobody taken off is handed on) and its own. Keys that do not read are dropped alone; the parcel is whole without them.

**Who links (the trigger).** When a note arrives and its list has been folded in (`Post.arrived` → `tookMembership` → `linkThrough`), every device on the list that is not taken off, not this one, not paired or linked here already, and that the list gives an address and both keys for, is linked - but only where the list came from a device **with a say in who has the thing**: whose it is, or an admin of it here (`NoteStore.listFromASay`, the rule *Remove* uses). A writer's or a reader's list links nobody. Linked means: written down in `addresses` with the list's name for it, `via = list`, *not* my device; asked to answer (a row in `accepting`); then, off the arrival's thread, met on the network (`Node.introduce`, which while notes go only between the owner's devices dials only a door on this network) and said a hello asking to be answered. Nothing of anything goes to it until it answers.

**The hello.** An ordinary `Hello` sealed for the device, scope `LINK`, target `list`, and its *writes* flag saying whether it asks to be answered. An answer is never answered, so two devices linking with each other say one hello each way and stop. Unanswered, it is asked again from the node's round after one minute, two, four, eight, sixteen, then every half hour (`Linking.due`), and at every opening as any unanswered acceptance is, up to sixty times. Anything else that comes from it - a note, an answer - counts as its answer too.

**Who is taken (the acceptance check).** A hello of this kind from a device not linked here is taken only where **a list here, for a thing still here, names the key it signs with** at any level but taken off (`NoteStore.namesDevice` → `Linking.names`). The envelope it came in was signed by that very key - a hello whose keys do not match its signature is refused before anything else - so the device asking is the device the list means. It is then written down as linked (`via = list`, filed at the address the list gives, met at the one its hello gives), answered where it asked, and sent whatever it is owed. From a device linked here already, a hello is its answer: it stops waiting, and what it is owed goes now. From anybody else, nothing at all: no contact, no question put to the owner, no word back.

**Why that is safe.** Knowing a device's address and even its key to seal for (anybody who once saw its code does) gets a stranger nothing: it is on no list here, so its hello is dropped, and anything it sends is still signed by no device known here and dropped as before. A device joins only a thing somebody with a say gave it, and becomes known only to devices that hold that same thing. The transport takes anybody who introduces themselves - `Node.introduce` on the far side makes a transport contact of whoever knocks - but what the notebook believes is the `addresses` table, and only it decides who may be sealed for, whose announcements on the network are heard (`Direct.paired`) and whose notes are written down. What is not closed: **the list is taken at its word** (see *Not built*), so a changed build that is a writer on something could put a made-up device on the list it sends; that device is then *named* here and its hello would be taken. It is not linked *from* here, since a writer's list links nobody, and what it is sent is what a member at the level the list claims would be. The answer is the one already written down for lists: a decision signed by whoever made it.

**Once linked**, a device is a contact like any paired one: notes, answers, files and receipts go straight to it (its door first, as for everybody) and its round on a note is an ordinary round. While it has not answered, the round stays dashed and says *Linking with Graphene… it goes once they answer.*, with **Link with Graphene** (by hand, the old way) and *Take them off* as before; what could not go says the same (`Unsent.Why.LINKING`, title *Linking with Graphene*). A device the list names and that cannot be linked - no key came with the list, an older build - is *not linked* as before.

**Seen wrong on 0.1.026, and put right.** Between the Pro, the GrapheneOS phone and the PC, the notes went round but the PC and Graphene each drew the other as a dashed round, and the PC went on asking Graphene to answer. Two faults. *Where the device is filed:* a list carries the address its sender last saw a device at, and a list row not newer than the one here is not moved - so a device linked at the address the newer list gave kept its rows at the older one, and nothing here was at that address: dashed round, *linked through something it no longer has*, nothing sent to it. Now every row naming a device by its key is filed where its contact is, when it is linked, whenever a list is folded in, and once a run for what is already there (`NoteStore.tidyLinks`). *Where the answer went:* an answer was handed to the address kept here, which the list gave and which a restarted device had left, so a relay took it for nobody. Now a device that says hello is met at the address its hello gives wherever the network does not know it, before it is answered, and the answer is said in the log. And the waiting no longer hangs on that one hello: **anything** signed by a device waited for - a note, a receipt - is its answer, and a device that has answered anything already is not waited for when the app next starts. `DesktopLinkTest` has both.

**Taken off everything.** A device linked through a list and never paired by a code stays in People and devices, under a line saying what it was linked through - *Linked through “Transfer”* - or, once no list here names it, *Linked through something it no longer has. Nothing is sent to it.* It is sent nothing: nothing is shared with it, it is not told *takes files* or *persons*, and it carries nothing for anybody. **Forget** works as before; but a device still named on a list here is linked again by the next list from somebody with a say - to be rid of it, take it off the list.

**Older builds.** A build from before this reads a `LINK` hello as an acceptance of an offer: it finds no offer of that scope and does nothing (the phone looks up the scope, the PC its own offers), and a hello naming a thing is never paired back unasked, so no half contact is made. The only thing it writes down is that the sender takes files, which is true. It never answers, so it is sent nothing and its round says *Linking with …* until it is updated or linked by hand. It reads the keys after the list of files not at all. Nothing new is said in five bytes, so there is no new receipt to announce.

**Kept.** No new schema: `addresses.via` (schema 27) says `list`, and the waiting is a row in `accepting` with scope `LINK`.

**Tests.** `LinkingTest` (who a list links and who not; which hello is taken; the hello an older build ignores; the backoff; the keys behind the files and only there, byte for byte what came before, a cut tail; the words), `LinkDoorTest` (four real nodes on one machine, door to door with no relay: A shares a note with B and C; B links with C through the list, C takes B's hello because its own list names B, B's edit goes straight to C and is taken; a writer's made-up member is not linked; D, knowing C's address and key, is not taken, gets no answer, and what it sends is written nowhere; both test tasks) and `DesktopLinkTest` (Windows, the real notebook through `Post.arrived`: the contact written as linked and waited for, the round and its words, the stranger, the answer, the key handed on, B's edit at C, the writer's list, taken off and still known).

**Not built, or not known.**

- **Not seen between real devices.** `Post`'s own sending - meeting the device, the hello over the network, the round asking again - runs only in the app, whose node these tests do not start.
- **Only as fresh as the list.** A device that moved since the list was written is found by the transport from the address the list gave (its directory, with helpers); only between the owner's devices, a device whose door is not on this network cannot be met until it is.
- **The first note from a device never agreed with** is weighed against nothing (`Arriving`), so where both wrote the same lines both are kept, as for a device an admin added.
- **The Send files box** lists a linked device like a paired one.

**The log.** `Mininotes/Link`: how many a list linked, hellos asked and answered, not taken and why - counts only, never a name, an address or a key.

## When something could not go

Said by device and by thing, with what to do (`Unsent`, the same words on both apps, unit tested): *Ana's phone has not been paired with this phone yet, so “Perso” cannot be sealed for it* - and the box's one button, **Pair with it**, opens People and devices; *Ana's laptop is in the list of people for “Perso”, but this phone was never paired with it* - a device that came in somebody else's list of people and was never paired here, which is what the old *Somebody has not been paired yet* nearly always meant - with the same button, or take it off the list; since 2026-09-28 such a device is linked through the list by itself where the list gave its keys (see *Linked through what you share*), and until it answers the words are *Linking with Graphene… it goes once they answer.*, with the same two buttons; *“Perso” could not reach Ana's phone just now* - open Mininotes on it, on the same Wi-Fi if possible, and it goes by itself; only between the owner's devices and not in reach - **How notes travel**; not connected yet; a note from before sharing. The same reason for the same device is said once however many notes it stopped.

**Needs to update** (since 0.2.001; HOME.md, step 4): *Ana's phone needs to update Mininotes to receive this* - what a device still on 0.1 cannot take (see *Older builds, and `TREE`*). It is counted as waiting for that device, so the mark stays amber, and the mark's box says it in these words on both apps; where that is all that waits, the box has nothing to press, since sending now would change nothing (`Looks.onlyUpdates`). It goes by itself once that device is updated and says `TREE`.

## Carried by a third device

Nothing in Maxima waits, so two devices that are never on at the same moment never meet: the PC shut just after something was written in it, a phone that was out of signal just then. Since v0.0.128 (PC v0.0.018) a third device that is on carries it between them (`Courier`).

- **When a copy is left.** A note going to a device not heard from in the last two minutes is also *left* with up to three other paired devices whose build has said it carries (a flag at the end of every note, `Parcel.carries`; nothing of this goes to a device that has not said it, because an older build would read it as a note and write it over somebody's words). An answer to a note that was carried goes back the same way, or the writer would send it again for ever.
- **What the carrier holds.** The note exactly as it was sealed for the device it is for. The carrier cannot open it, change it unseen, or pass it off as its own; it knows who it is from, who it is for, which note by its id, and how big. One copy per sender, recipient, note and kind — a newer revision replaces an older — at most 500 things and 16 MB together, and nothing longer than 30 days (`carried`, schema 23).
- **Bringing it.** As soon as the device it is for is heard from, and otherwise after a minute, two, four, eight and then every ten, because a relay says yes for a device that is not there. The device it is for opens it as if it had come straight, answers the writer, and tells the carrier *collected* (`Receipt.COLLECTED`, `COLLECTED_ANSWER`); only that lets go of it.
- **What it cannot do.** With only two devices there is no third to carry. A carrier holds only for a device it is paired with itself. A carrier whose notebook is locked keeps what arrives in its inbox and carries it once opened. A message inside something carried is never carried again.

## Where sync actually stands, on a real phone

The pad runs its own Maxima node: the transport's core is vendored into the app, started with it, and attaches to the owner's own relays first and then, unless they switch it off in Settings → *Relays*, to the public ones. `⋮` → **Profile** shows the address it was given, the code another device scans, and a checklist of what is and is not working — whether a relay has answered, whether there is anybody to send to, whether their keys are known.

Seen between a Pixel 7 Pro and a GrapheneOS Pixel 7, in both directions: scanned, accepted, granted, sent, carried by a public relay, opened, filed on the right shelf, marked, and merged into a page that was open at the time. Each of those is recorded with the build it was seen on, in a verification log the maintainer keeps privately.

## Staying up while the pad is closed

Maxima is not a shelf. Nothing waits anywhere for this phone to come and fetch it, so a node that lives only while the pad is on screen makes sharing work when two people happen to have the app open together and do nothing when they do not.

`Listening` is a foreground service that keeps the process — and so the node — up after the pad is closed. Android allows that on one condition, which is that it says so for as long as it lasts, and that is the right condition. Three things keep it honest:

- **Only while there is somebody to hear from.** A pad that has never been paired keeps nothing running.
- **It can be switched off where it is.** `⋮` → **Profile** → *Listens while the pad is closed*, or **Stop listening** on the notification itself — which is where somebody wondering what it is will already be standing.
- **What arrives is said in one line that never quotes the note**: who, and what kind of thing. A notification is read by whoever is holding the phone, locked or not. Both channels are quiet by default: a shared list being written in sends every few seconds, and a pad that chimed each time would be switched off within the hour.

**The node has to be looked after, and for a long time was not.** The transport is a library and says in as many words that whoever carries it drives its upkeep: `maintain()` on a heartbeat. A relay stops reading from a client it has not heard from in ten minutes, and a keep-alive is due every two — so left alone, this phone was dropped by every relay it had within ten quiet minutes of opening and never went back for another. It looked exactly like a node that was working. `Node` now runs that upkeep every thirty seconds for as long as the process lives: keep-alives, a relay that has gone quiet swapped for one that answers, everybody told if that moved this phone.

**Three workers, not one.** The notebook's worker is serialized so that a read sees the writes before it, which is right for a notebook and wrong for a relay. Sending, pairing and asking the node for its address have a worker of their own, and the housekeeping a node wants when it has just come up — telling every contact where this phone now is, which the transport gives a minute and a half — has a third. A note somebody has just written does not wait behind a courtesy, and a word typed does not wait to be saved behind a note being sent.

## A phone that answers

The network can say it took a message. It cannot say anybody received one, and it will take a message for a phone that is asleep, switched off, or no longer taking that note. For a long time *delivered* was written on the network's word, and two things were leaning on it:

- **The mark.** It cleared when a relay said yes.
- **Every merge.** The text two phones are taken to have last agreed on was read from the same record. A phone that had been handing revisions to a relay for somebody who never got them believed they had its latest, weighed what they wrote next as *older*, and set it aside without a word. Seen on 18 September 2026: two words typed on one phone, kept under Versions on the other, on neither page, neither phone thinking anything was wrong.

So a note that goes asks to be answered, and the phone that gets it answers — `Receipt`, five bytes, sealed and signed like everything else, with the note and the revision in the envelope's own header — **after** it has written the note down, never before. Only that answer records a delivery. It is given whatever was decided about what arrived: taken, merged, or set aside as older than what was there. The question was *did it arrive*.

**What is never answered is sent again**: after a minute, then two, four, eight, and from then on every quarter of an hour, for as long as it takes. From the node's own upkeep, so it happens with the pad closed. Only a revision that already went once: a note written in since is the business of whatever that note is set to, which may be *when I ask*. `Outbox.due` decides and is unit tested.

**A note also says what it was written on top of** — the revision its sender believes both phones last had. The receiver has its own belief, and uses **the older of the two**. Both beliefs fail the same way, by taking the other phone to have more than it does, and the two mistakes are not alike: a base that is too old makes a merge work harder; one that is too new makes what the other phone never had look like something it had and deleted. `Arriving.agreed`, with last night's case pinned as a test.

**Only a phone that asks is answered.** A build from before answers existed reads anything it does not recognise as a note written the oldest way, as bare text — send it an answer it never asked for and it would write those five bytes over the note. `ReceiptTest` pins that an answer is not a note and a note is not an answer.

**Whoever sent a note has it.** Obvious, and it was not being written down, so every phone "owed" each note straight back to the phone it came from: a message each way for every note, and *Not sent yet* on a page nobody had touched.

**And the count was being compared with a clock.** What decides whether a note is owed compares the revision that was delivered with the revision the note is at. The second of those was being read from the wrong column — the time the note was last changed, in milliseconds, against a count of a few dozen. Nothing was ever up to date. Every shared note was owed for ever, *Not sent yet* showed whatever had happened, and the whole pad was sent again to everybody each time it was opened — which hid a good deal, because notes the network had lost turned up anyway the next morning and looked as though they had only been slow.

## The page that is open when something arrives

A page is a copy of a note, taken when it was opened. If the note is written in from the other end while the page is open — or while the pad was in a pocket with that page still on it — the page is holding the older text, and the next word typed would write that back over what arrived, one revision higher, and then send it: both ends lose the same words and neither is told.

So the page is brought up to the note rather than the other way round. Where nothing was typed since the page was last written down, it simply becomes what the notebook now says. Where something was, those words are in neither place, and they are put together the way any two writings are — line by line, against the text the page started from. `Arriving.onThePage` decides this and is unit tested.

And the writing itself checks: a page is written down only if the notebook has not moved past what that page last saw, in the same transaction. Arrivals come on the node's thread and pages are written on their own, so without that check the two could pass each other.

**An address is not what a device is.** A node that has not reached a relay yet gives out a permanent `MAX#<key>#<directory>` address, which is a key and a directory to go and ask rather than somewhere to send — and it resolves only once that node has published itself there. A code offered in a node's first seconds carried one of those and failed days later on somebody else's phone with `directory replied UNKNOWN`. Codes now carry a routable address or nothing, and a device is filed under its signing key, so scanning the same phone again moves the row it already has — with its share rules and its delivery records — instead of leaving a second copy pointing at an address nothing answers.

**An address is a snapshot; a contact is a peer.** A node that restarts or moves relay is somewhere else within the minute. So pairing also *introduces* the two nodes at the transport, which makes each a contact of the other: a stable identity key, every address it is currently reachable at, and a directory to ask when none of them answer. A note is handed to the peer rather than to an address they used to be at, and a phone that has just come up tells everybody who knows it where it is now.

**Direct first, relays when that fails** (built 2026-09-27, not yet seen between real devices; see [DIRECT.md](DIRECT.md)). Every device opens its own door (the transport's `DirectEndpoint`, port 9601 or the next free one), which takes only what is sealed to its own identity key and hands out its own file pieces. Each round it says on the local network, by UDP broadcast, where that door is, signed with its identity key; a device believes that only from a device it is paired with, from the address named, and within two minutes. A note, answer or ask then goes to the local door first, then to a public door the device has proved (only the PC asks its router for one), and if one takes it no relay gets a copy (`Direct.send`). Otherwise it goes as before: every address the transport knows, relays included, then the directory. A door taking a note clears nothing, exactly as a relay taking it clears nothing: only the far device's answer does.

**The PC keeps what comes for its owner's phones** (built 2026-09-27, not yet seen between real devices; see [DIRECT.md](DIRECT.md), phase 2). A phone cannot be reached, so it asks: every round, it asks each device marked "My device" whose door it can reach - the PC at home, or at the public address the PC proved - whether anything is waiting, takes it in as if it had just arrived, and says it has it. A note for a phone that is collecting is left at the PC after the phone's own door and before the relays, and the PC keeps it (schema 25, `held`: sealed for the phone, unreadable to the PC, for a fortnight at most). The PC takes such things only from devices paired with it, only for its owner's devices, and only for one that has asked in the last five minutes; otherwise the relays, as before.

**Your own relays** (built 2026-09-27, not yet seen with a real relay; see [DIRECT.md](DIRECT.md), *Your own relays*). Settings → *How notes travel* → *Relays* (under the second way), on both apps: *Use the public relays* (on unless switched off), the owner's relays each with *Connected* or *Not answering* and a Remove, and a field to add one as `host:port` (IPv6 in brackets). A relay is kept only if a Maxima greeting came back from it when it was added. Theirs are attached to first; with the switch off, nothing else is - not the shipped relays, not ones other relays name, not ones saved from earlier runs - and what cannot go directly waits. The addresses handed out name their relays first, never the device's own door. Off changes where this device can be reached, not whom it can send to: a note for somebody reachable only through a public relay is still handed to that relay.

**Two ways notes travel** (built 2026-09-27, not yet seen between real devices; see [DIRECT.md](DIRECT.md), *Two ways notes travel*). Settings → *How notes travel*, on both apps, chooses one of two. *Also through helpers when needed* is the default and what was there before: straight to a device first, then the owner's relays and the public ones (and, one day, a cloud folder, not built). *Only between my devices* is peer to peer and nothing else: no relay of any kind, no directory, no relay-held file pieces. A note goes to a door heard on the same network, a door a device proved in public, or the owner's PC keeping it for their phone - and otherwise waits, owed, and says *not sent yet* under its title until the devices meet. Phones away from home cannot reach each other, or the PC, in this way (the PC's port cannot be proved without a relay, so it is not given out). A file is offered from its owner's door rather than sent up, and its card says *Waiting for the other device* until they say they have it. A pairing code carries the device's door on the network it is on, so pairing needs both devices on the same Wi-Fi, and says so.

Minima Core is not part of any of this. Nothing has to be installed beside the pad and nothing has to be enabled anywhere else; an earlier design asked Core to carry the post, and Core's build turned out to have no Maxima in it at all. [MAXIMA-TRANSPORT.md](MAXIMA-TRANSPORT.md) has that story.

## The sealed note

`Envelope` is the whole format. Per message: an ephemeral P-256 key agreed with the recipient's key (ECDH), HKDF-SHA256 to an AES-256-GCM key, the note encrypted under it, then ECDSA-P-256 over header and ciphertext by the sending device.

The app seals the note itself rather than trusting the transport. Maxima encrypts between nodes, but a node that relays or receives for you would otherwise handle readable notes; here it carries bytes it cannot read and cannot alter undetected.

The header — note id, revision, moment, sender key, ephemeral key, nonce — is authenticated as associated data, so none of it can be edited in flight. The sender's public key travels inside the message. That proves the message is intact and self-consistent; **it does not prove who sent it.** `open` hands back the sender's fingerprint and the caller decides whether that fingerprint is a device it paired with or a person it chose. Trusting a fingerprint you have never confirmed is the mistake this design exists to prevent.

Bounds are enforced while parsing, before allocation: a stranger's bytes cannot make the app reserve memory or crash. Refusals are refusals — a partly parsed note is never returned.

`revision` rides inside the seal so a receiver can drop a replay or a stale update without trusting a clock. `RevisionClock` decides ancestry between revisions; wall-clock time never does.

## Which address, and when the six digits matter

**It is the Maxima address**, the one a node shows as its contact address — it ends in `@host:port`. That is what carries a message from one node to another. A wallet address carries coins and cannot carry a note, so the app asks for the first and says so.

**A code scanned off the other screen needs no further check.** You are looking at the device you mean, and nothing came between the two of you: a photograph of a screen cannot be substituted by anything in the middle. So scanning pairs straight away, with nothing to compare.

**The six digits are for a line that travelled.** Pasted out of a message, a mail, or a note passed along, a pairing line could have been altered on the way. The digits come from both devices' keys, which is why one device alone cannot show them: they exist only for a pair. Until 0.2.011 a pasted line was held back until the two screens' digits were compared - but the device that showed the line had no digits to show, so a paste could never be finished (the owner, 2026-10-02). Now a pasted line is taken as a scanned one is, with Accept or Pair, and the box says it was pasted and gives the digits; afterwards each device shows the digits for every other one under People and devices ("Check with them: 123456"), the same on both if nothing came between them. Scanning still needs none of it.

**The code is drawn as a link, `mininotes://pair/…`, so the phone's own camera can open it** (v0.0.104, `Pairing.link`). A line of text shown to a camera gets an offer to search the web for it; a link this app answers to gets an offer to open it here, and somebody handed a code need not know there is a scanner inside the app. The line inside is unchanged; the scanner in the app and *Paste* read either. Two things follow from its arriving from outside. **Nothing it brings is kept without being asked about** — an introduction scanned in the app pairs at once, because scanning it there is somebody saying "this device"; opened from outside it asks first. And **only a camera is taken at its word**: a link that came any other way is a line that travelled, and gets the six digits. If a camera somewhere shows the link as text and will not open it, the remedy is an ordinary web link, which wants the project to have a site of its own.

## Two people wrote at once

Nobody's writing is lost, and nothing is invented. That is the whole rule; everything below is how it is kept.

**Revisions are counted, not timed.** Every writing of a note puts its revision up by one. Two phones whose clocks disagree cannot be sorted by time without losing somebody's work, so time is never what decides. What decides is what each side had when they last agreed — the revision an address was last given, which the outbox already records.

**Four cases, and only one of them needs anybody's attention.** `Arriving` decides which, from four facts and nothing else, and is unit tested:

- **New.** Nothing here by that name. Keep what arrived.
- **Older.** This phone has written past it. Ignore it, and say nothing — it carries nothing this phone does not already have.
- **Newer.** It descends from what is here, so it contains it. Take it.
- **Merged.** Both sides wrote since they last agreed. `Merge` puts the two together.

**The merge works on lines, against the last text both sides had.** Each side is read as what it *changed* — a few stretches of lines replaced, removed or added. Where the two changed different stretches, both changes are taken: you rewrote line three, they rewrote line nine, and the note ends up with both, silently and correctly. Changes that merely sit next to each other are separate stretches, not a conflict. This is the common case and it needs no one's attention.

**Where both wrote over the same lines, nothing is chosen for you, and nothing is put away: the note keeps what each of them wrote, both of it.** What the two share is there once; what they do not share is there from each; whoever is reading deletes the line they do not want. `Merge.both`, in one order whichever phone works it out, so the two phones come to the same note and not to two.

It used to keep this phone's lines on the page and put the other phone's under Versions. That was careful, and it was wrong in the way that matters. Two phones each kept their own, each told the other it had the other's, and the result — seen on 19 September 2026 — was two ticks over two different notes, 91 characters on one phone and 118 on the other, with the difference somewhere nobody looks and nothing on either screen to press. The person whose notes they were asked three times how to sync them. There was nothing to figure out.

**Arrived is not agreed.** The text a merge is made against is the text two phones last *both had*, and that is not the last thing delivered: a phone can receive a note, put it together with its own, and end somewhere else. So an answer says which it was — `TOOK`, my note now says exactly what you sent, or `HAVE`, it arrived and was put with my own — and only the first moves what the two are taken to have agreed on. The mark is about delivery; the merge is about agreement; they were one number and are two (`sent.revision`, `sent.agreed`).

**Sync on a note sends it whole and asks for it whole**, whatever either phone believes the other has. What pressing Sync means is *make these the same*.

## Everything a note has said

Every note keeps its history: what it said, when that was kept, and where it came from — this phone, or the address that sent it. A version is kept when an editing session ends rather than at every keystroke, so the list reads as a history and not as a keystroke log, and a hundred of them are kept per note, which for text is nothing.

`⋮` → **Versions** lists them, any of them can be read whole, and **Put this back** writes it as a *new* version rather than erasing what happened in between. Nothing in this app ever removes a version to make room for another one except the oldest beyond the hundred.

This is also what makes the conflict case safe: a conflict is nothing more exotic than two versions sitting in a list you can already read.

## Keys at rest

Each device holds two EC keypairs, one to sign and one to agree. Android Keystore cannot hold an ECDH key below API 31 and this app supports API 28, so the private keys live in app storage sealed under an AES-GCM key that the Keystore does hold. Pairing tokens and note text must never reach logs or OS cloud backup; `data_extraction_rules.xml` already excludes the app's storage from both.

## Not built

**Pausing does not tell anybody.** A phone that has *paused* a note hears it, sets it aside and says nothing, by design — so the phone sending it never gets an answer, its mark says *waiting* for ever, and it goes on trying every quarter of an hour. *Unfollow* (v0.0.102) and *Remove* (v0.0.108) are the two ways of going that do say so; see *Leaving*.

**An admin adding a third device has not been seen.** There are two phones. What is pure in it is tested — a shelf keeps one name whoever sends it, and a list for the owner's own collection comes home to that collection (`ComingHomeTest`) — and the rest waits for a third phone. One thing is known to be wrong already: the third phone will call whoever *sent* it the note the owner, because *owner* is worked out from where a note arrived from.

**It stops hearing when the phone goes into its deep sleep, unless its owner lets it through.** Measured: a Pixel 7 Pro off its charger with the screen off was in Doze within twelve minutes, and in Doze Android cuts an app's network whether or not it has a foreground service. The process stayed up, the service stayed up, and nothing arrived — and the note sent to it in that time was *taken by a relay and lost*, which is the item above seen happening. The way through is for the person to exempt the app from battery optimisation, which is theirs to grant and has a cost: since 0.0.112, Settings → *While the pad is closed* → *Keep listening while the phone sleeps* opens Android's own question, asked by a tap and never on its own, and the pad wakes itself for a moment every five minutes or so (`Waking`, an alarm Android allows while the phone sleeps). Neither has been measured on a sleeping phone yet, so the honest description is still: it listens while the pad is closed *and the phone is awake or charging*, or the person has let it through.

**It does not start again by itself after the phone restarts.** The pad has to be opened once.

**The list is taken at its word, about writers and readers.** Every phone folds in the membership that arrives with a note before asking what the sender may do, and the later decision wins per person. Since 0.1.040 only the owner's list is believed about the owner and the admins (see *The four roles*), so a phone that *claims* to have been made an admin is no longer believed; but a list is still believed about who writes and who reads, whoever passes it on. Nothing in the app writes such a list, and a stranger cannot: the envelope has to be signed by a device paired here. It is a thing a changed build could do, and the answer - a decision signed by whoever made it - is a change to what travels. Since devices link through a list (see *Linked through what you share*) it matters more: a device a changed build put on its list is taken when it says hello, though nothing links it *from* here unless the list came from somebody with a say.

**Leaving a deep collection tells nobody yet.** Unfollow and Remove are said in the words a 0.1 device knows - a note, a book, a collection - so for a collection with two or more collections above it, which 0.2 lets anybody make, there is no word: it is let go of here, or the person is taken off here, and the log says *nobody is told yet* (`Post.leave`, `Post.removed`). The same holds for a collection with no note in it, since what is left is named by a note out of it.

**Files go only in part, and have not been seen going.** A note's own files of up to 16 MB go with it (schema 24), and since 0.2.004 a collection's with the collection; bigger ones stay here. It is tested with synthetic devices and not yet watched between two phones or a phone and the PC - see *What a file kept with a note does*, above, for the rest of what is not known.

**What failed to go is not tried again on a clock.** It goes when the writing next stops, when the pad is next opened, or when somebody asks — not because ten minutes have passed.

**Unsubscribing is local.** It stops this phone putting what arrives on its shelves; it cannot stop the other end sending, and there is no message that asks them to.

Open questions worth settling: how long an outbox should retry when a recipient is offline, what a Maxima message expiring means for something still owed, and what a reader sees when a note they were sent stops being updated because it was revoked.

And one that wants arguing before anybody writes it: **two phones that wrote over the same line may never converge by themselves.** Each keeps what it was showing, which is right. But each then counts a new revision for a text it did not change, the other takes that for news, and the two go on owing each other the same two texts until a person puts one of them back from Versions. This is reasoned from `Arriving.weigh` and has not been watched happening. The likely answer: what arrives unchanged from the text both sides last agreed on carries nothing, whatever its number says, and should be weighed as older.
