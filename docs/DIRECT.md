# Going direct: less and less through servers

Status: design, agreed 2026-09-27. Phases 1 and 2 are built (2026-09-27) and tested on one machine, not yet
seen between real devices; phase 3 is not built. *Two ways notes travel* - only between the owner's devices,
or also through helpers - is built the same day, the same way.

## Where it stands

Every note, receipt and file between two Mininotes devices goes through a public relay today, even
between a PC and a phone on the same Wi-Fi. The Maxima code we carry can do better - it has a door a
device can open (`DirectEndpoint`), a router port opener (`PortMapper`: NAT-PMP, then UPnP), a check that
the door really opens from outside (`ReachabilityManager` + `Probe`), and a same-network path
(`noteLanPeer`) - but Mininotes starts none of it. It also sends every message to every address a contact
has, relays included, even when a direct one would have done.

A relay is needed because most devices cannot be reached: a phone on mobile data shares one public address
with thousands of others, and a home router drops what nobody inside asked for. That is true of Minima as
well: a phone node connects out to the minority of nodes that can be reached, most of them rented servers.
What makes a network decentralised is not having no reachable machines, it is that anybody's can be one.

## The goal

A device's address is its key. Devices find each other through any reachable peer, a friend's PC as much
as a server, and then talk directly. A relay carries data only when two devices can find no path at all.

## Phase 1 - the door, at home and on the PC (built, not yet seen between devices)

1. **Every device opens its door.** Each starts its `DirectEndpoint` on a fixed port (9601, the next free
   one if taken). It accepts only messages sealed to this device's own key and forwards nothing.
2. **Devices on the same network find each other.** Each announces itself on the local network by UDP
   broadcast every beat: its signing key, its door's port, a time, and a signature over them with the
   device's own key. A device that hears a *paired* device, with a valid and fresh signature, notes it
   (`noteLanPeer`). An announcement from anyone else, or one that does not verify, is ignored. No note
   content is ever in an announcement.
3. **The PC opens its router port.** On the PC, `ReachabilityManager` asks the router for a public port,
   has a relay dial it back to prove it opens from outside, and only then adds the address
   (`Mx…@publicip:port`) to the ones its contacts are given. A router that refuses, or a second NAT in
   front, means no public address, said in the PC's Profile rather than hidden. Windows asks once whether
   Mininotes may accept connections; the answer is the owner's.
4. **Direct first, relays only if it fails.** A note goes to a direct address first - same network, then
   the PC's public one. If that delivers, nothing goes to a relay. If not, the relays as today.
5. **Files come from the owner when it can be reached.** The door serves the device's own file pieces, so a
   phone at home fetches the PC's files from the PC.

What it gives: at home, PC and phone never use a relay. Away, a phone sends to the PC directly. A phone
still receives through a relay, because a phone cannot be reached.

### What was built, 2026-09-27

- **The door** (`Node.open`): every device starts the transport's `DirectEndpoint` when its node starts,
  on 9601 or the first free port up to 9620, and remembers the port it got. The file shelf (`BlobStore`)
  is made first and handed to it, so the door serves this device's own pieces. The vendored transport is
  not changed.
- **The announcement** (`Direct.announce` / `Direct.heard`, `Nearby`): every round (30 s on screen, 90 s
  away) and once at start, a UDP broadcast to each local network's own broadcast address, port 9601:
  `MNLA`, version 1, the Maxima identity key (X.509, 162 bytes), the local IPv4 address it is said from,
  the door's port, a time, and an RSA-SHA256 signature by the identity key over a fixed context string
  and all of that. About 320 bytes; at most 1200 are read. The **identity key** signs because it is the
  key the pairing recorded as the device's contact (`NoteStore.Contact.contact`) and the key its door
  opens messages with - the key `noteLanPeer` needs. A packet is believed only if it is well formed and
  within bounds, not this device's own, from a **paired** device (paired in the notebook, not merely a
  transport contact - the transport accepts anybody who introduces themselves), sent from the very
  address it names, no more than two minutes old or ahead, and signed. Then `noteLanPeer`. Plain
  broadcast, not multicast, so a phone holds no Wi-Fi lock. Nothing is logged but counts.
- **Letting go**: a device not heard for five minutes is forgotten; a move to another network (the set
  of local addresses changes) forgets them all; a local door that would not take a note is forgotten and
  not believed again at that address for ten minutes, so a door behind a firewall costs one five-second
  wait in ten minutes, not one per note.
- **The PC's router** (`Node.reachable`, PC only - the Windows build sets `Node.A_PC`): the transport's
  `ReachabilityManager` asks for a port (NAT-PMP, then UPnP), has a relay dial it back, and only then
  `setDirectAddress` and tells every contact. Lost, it is taken back and contacts told again. Asked
  every round while it holds a port, and every ten minutes while it does not; asked again at once when
  the PC's networks change. The mapping is released when the process exits (a shutdown hook); one left
  by a crash expires on the router. The Profile's Connection card says it in one quiet line - for
  example *Not reachable directly: the router did not open a port* - never the address.
- **Direct first** (`Direct.send`, used for every note, answer, ask and hello `Post` sends to a known
  device): the local address heard for the device, then each of its addresses whose `Mx` part is its
  identity key written as an address (a door; a relay address carries a per-relay key instead), with a
  five-second connection leash. The first that answers OK is the whole send. Otherwise
  `sendToContact` as before: every address, relays included, then the directory. Receipts, the outbox
  and carrying are unchanged: a door taking a note clears nothing.
- **Files from the owner** (`Post.fromTheirDoor`): before fetching, the device that listed the file is
  asked at its doors whether it has the first piece; a door that answers goes in front of the relays. A
  file still counts as gone up only when a relay has it: the PC's own door in the list does not count.

Tests: `DirectTest` (the format, its bounds, a stranger's key, its own key, a changed byte, another key's
signature, another address, stale and ahead; telling a door from a relay; the order of trying; the
Profile line) and `DoorToDoorTest`: two real nodes with no relay and no internet, a real door, an
announcement heard on a real socket, a note sent and received with the stand-in relay never dialled;
the same through a public-style door listed after a relay; and a shut door falling back to the relay.
Both run in the Android and the Windows test tasks.

### Not done, or not known

- **Not seen between real devices.** No phone or PC has run this; whether broadcasts get through a given
  router, and whether a relay's dial-back proves a real router's port, is unknown.
- **The Windows firewall.** The door and the listener accept connections, and Windows blocks those for
  a program it has no rule for. Where Windows is set to ask, it asks once; where it is set not to ask,
  it blocks silently - then the PC's door is shut to phones at home and from outside, the relay's
  dial-back fails and the Profile says the router's port let nothing in. Settings → *Direct
  connections* → *Let devices reach this PC directly* makes the rule after Windows' own administrator
  prompt: TCP 9601-9620 and UDP 9601, on private (home) networks only, by port rather than by program so
  an update does not undo it. Off takes it away again. Any PC owner can do this from the app.
- **Some Wi-Fi drops broadcasts** (guest networks, client isolation, mesh systems) and some phones filter
  them while the screen is off. Then nothing is heard and everything goes by relay, as before.
- **A phone hears only while its process lives and it is awake.** In Doze it hears nothing and sends
  nothing, and devices that stop hearing it let it go after five minutes.
- **A public door that has died costs up to five seconds twice** before the relays: once first, and once
  more as the ordinary fan-out tries every address.
- **The PC's pairing code and address** may now carry its public address (`Mx…@publicip:port`) first,
  since the transport puts a proven door first in what it hands out. That names the owner's home
  connection to whoever gets the code.
- **Anybody on the same network can knock on a phone's door.** It opens only what is sealed to that
  phone's key and forwards nothing, with the transport's connection caps; but it is listening, which a
  phone on a café's Wi-Fi was not before.

### Telling where a device is now (2026-09-28)

Seen on 0.1.028: the PC started again on other relays and heard nothing more, while the phone went on answering
"OK". The transport tells a contact where this device is by trying that contact's addresses in turn and stopping at
the first a relay accepts - and a relay accepts for a device that has left it as readily as for one still there. So
the PC's card went to a relay the phone had left, the phone kept the PC's old relay, and every answer was taken there
for nobody. Notes the other way went to every address the phone had and got through.

- **Every address, and the door here first** (`Node.tell`): telling everybody - at start, when this device's addresses
  change (the same number can be other ones), when the relays chosen change, when the PC's public door is proved or
  lost - now goes to each device's door on this network when one is heard, asking for its card back, and then to
  every address the transport keeps for it, not only the first. The directory is published as before. On a thread of
  its own, so a note never waits behind an address that is gone. Not while notes go only between the owner's devices.
- **Heard anew, told** (`Nearby.onMet`): a paired device heard on this network that was not heard a moment ago (the
  first time since start, after five quiet minutes, after a move) is told at its door where this device is, and asked
  for its card. That heals both ends even with the PC's door shut by the firewall: the PC dials out to the phone.
- **A card with no address** is never sent: it would leave the other device with none for this one.
- **What the log says**: `told N of M device(s) where this device is now (K at their door on this network)`; `a paired
  device heard on this network (N near): told where this device is, at its door: taken / not taken`; each note,
  answer and file message says its road (`by a door`, `left at a home`, `by the relays`, and when a door on this
  network would not take it); on the PC, where what arrives goes (the open notebook, or sealed in the inbox and how
  many wait, and how many were taken in on opening) and, once a start, whether Windows' rules for the door are on and
  what kind of network this is.

Tests: `TellingTest` (two real nodes, each knowing the other only at a relay it has left: the PC hears the phone, tells
it at its door, the phone's answer reaches the PC's new address and the old relay is not dialled; told once, not at
every announcement; everybody told through a door here when every address is old; a device with no address tells
nobody; a door that will not take the card is let go of) and, on Windows, `DesktopArrivingTest` (locked, kept sealed;
opened, taken in; locked again and opened again, the same; a window that locked while its sharing was starting cannot
take arrivals back).

## Phase 2 - the PC as its owner's host (built, not yet seen between devices)

The plan was: a phone away from home keeps one connection open to its owner's PC, as it does to a relay
today, and the PC holds and hands on what arrives for it - only for devices marked "My device". A friend's
devices reach you through your PC; theirs reach them through theirs. The vendored library has no host side
(only the client side and the door), so this is ours to write.

What was built differs in one way, because of what the transport can do: the PC cannot push to a phone at
all - the phone cannot be reached, and the door answers a message with a status and nothing more - so the
phone **pulls**. It holds no connection open; it asks every round.

### What was built, 2026-09-27

- **The stream** (`Home`): the transport's door hands any connection that names a registered 64-hex token
  to whoever registered it (`PrivateStreams`, in the vendored code, unchanged). That is a conversation both
  ways on the door's own port, under the door's own connection caps and idle timeout. The PC registers one,
  labelled with a hash of its identity key; a phone's door has none, and closes such a connection at once.
  The label is not a secret: everything asked on the stream is an `Envelope` sealed to the PC's agreement
  key and signed by the device asking, and the PC opens and checks it as it would a note.
- **Three asks.** *Deposit*: something already sealed end to end for one of the owner's devices, named by the
  fingerprint of the key it signs with; the sealed thing follows the envelope on the stream with its SHA-256
  inside the envelope, so a note near the largest there is can be left too. *Pull*: "anything for me?" -
  answered with at most 16 things or 1 MB, oldest first, and how many more wait; up to four answers a round on
  one connection. *Ack*: "I have these", by hash; the PC lets them go. Things are handed in before they are
  acked, so what is lost between the two is collected again, never missed.
- **The PC's rules.** A deposit is taken only from a device paired in the PC's notebook, only if what it
  leaves was sealed by that same device (the envelope's outside names the key), only for a device marked
  "My device" there, and only if that device has **asked in the last five minutes**. That last rule is what
  makes it fair to count a deposit as sent: what the PC takes, a phone that is collecting takes within a
  round or two. A phone that is not collecting (asleep, away with no way to the PC, switched off) is refused,
  and the sender goes to the relays as before. A pull or an ack is taken only from a device marked "My
  device". Strangers, and anything that does not open, get a plain no. While the PC's notebook is locked or
  closing, every ask gets "not now".
- **The shelf** (schema 25, `held`): in the PC's notebook, so it survives a restart. At most 300 things or
  8 MB for one device and 1000 things or 32 MB together; nothing older than 14 days; the same bytes kept once;
  the same note at the same revision from the same device, sealed again with as much inside, replaces what was
  held - so a note sent again every quarter of an hour while nobody collects does not pile up. What the PC can
  see is what a relay or a carrier sees: who left it, who it is for, which note by its id, which revision, how
  big. It can read none of it.
- **Sending order** (`Direct.send`, for every note, answer, ask, left/removed, file answer and carried thing
  `Post` sends to a paired device): the device's local door, then its proved public door, then **its home**,
  then the relays. On the PC, the home is the PC itself: a note for one of its owner's phones that is
  collecting is kept here for it. On a phone it is any paired device - its own devices first - whose door is
  in reach (heard on this network, or a proved public door) and has a home behind it. A home that takes it is
  the whole of the send. Receipts and the outbox are unchanged: only the far device's answer clears anything.
- **Collecting** (`Post.collect`, every round on a phone - 30 s on screen, 90 s away): every device marked
  "My device" whose door is in reach is asked. What comes back is handed to exactly what a message from the
  network is handed to, so it is taken in the same way - or kept sealed in the inbox while the phone's
  notebook is locked. A round with nothing waiting costs one connection and two small envelopes.
- **Not asked again at once.** A door that cannot be reached is let be for five minutes; a door with no home
  behind it (a phone's, or a PC's from before this) for thirty; a home that said no about one device is let
  be only for that device - a minute when it was not collecting, ten when full, thirty when refused.
- **How a sender learns the home: it does not.** The suggestion was to tell contacts "to reach phone D, you
  may also leave things at PC H". Not done, for three reasons found in the code. A trailing field in a note
  (`Parcel`) cannot be added safely: the list of files is optional and comes last, so a current build would
  read new bytes after it as a list of files, fail, and take the whole note for bare text written the oldest
  way. A second version of the local-network announcement would be dropped by every build that knows only
  the first (it checks the version exactly and allows nothing after the signature), so a new PC would go
  unheard by phones not yet updated. And only the PC knows whether a phone is collecting *now*, which is what
  makes a deposit safe; a sender that had been told would be going on old news. So a sender asks the homes it
  can reach, and the home decides. Nothing about the owner's home travels to anybody, and the announcement is
  unchanged (version 1), so older builds are heard exactly as before.
- **Friends.** The PC takes deposits from any device paired in its notebook, for its owner's devices. A
  friend's phone can leave things for the owner's phone at the owner's PC only if that friend's phone is
  paired with the PC itself; a friend paired only with the owner's phone goes by relay, as before. A home a
  phone asks that turns out not to be the recipient's learns what a relay would (who it is for, how big) and
  can open none of it.
- **What it says.** The PC's Settings → *Direct connections* has one more quiet line: *Holding 3 messages for
  your phones until they collect them*, or *Your phones collect from this PC, with no relay between*, or
  *Your phones collect from this PC when they can reach it*. The phone's Profile, under Connection, says
  *Collects from your PC, with no relay between* while it has collected in the last five minutes, and nothing
  otherwise. No new switch anywhere.
- On the PC, a phone that is collecting counts as *there* for carrying (see *Carried by a third device* in
  SHARING.md), so a copy of what goes to it is not also left with other devices.

Tests: `HomeTest` (the formats and their bounds; the outside of an envelope; paired, "My device", collecting,
something somebody else sealed, a hash that does not match, sealed to another key; the same bytes once,
sealed again replacing; the caps, the amount per answer, the fortnight; not asked again; the settings line)
and `HomeDoorTest`: three real nodes on one machine, no relay and no internet - phone A's note left at the
PC's real door by `Direct.send`, phone B collecting it and opening it as A's, acked and gone, the stand-in
relay never dialled; refusals, with the relays then tried; a door with no home behind it; the PC's node
started again over the same shelf; more than one answer's worth on one connection; and a device's own door
coming before its home. Both run in the Android and the Windows test tasks. `DesktopHomeTest` (Windows only,
the real notebook): kept once, replaced, let go only by the device it is for, the caps, the fortnight, still
there when the notebook is opened again, a version-24 notebook taking the step, and the home over the
notebook. `SchemaMigrationsTest` covers step 24 → 25 both ways round.

### Not done, or not known

- **Not seen between real devices.** Nothing here has run on a phone, or between a phone and a PC.
- **Only as good as the PC's door.** Away from home, a phone collects only if the PC proved a public port
  (phase 1) and Windows lets the connection in (*Let devices reach this PC directly*). Otherwise it collects
  only at home, and away everything goes by relay as before.
- **A phone asleep does not collect**, so five minutes on the PC stops taking things for it and senders use
  the relays, which cannot reach a sleeping phone either. Nothing is lost that was not before.
- **A refusal costs a round trip** before the relays, once per rest: a phone sending to a friend asks its own
  PC first.
- **What a home answers is not sealed.** What it hands over is already sealed and signed by whoever wrote it,
  so somebody in between can drop or replay things - which a phone takes as it takes a note that came twice -
  but not read or forge any. The asks are sealed and signed; each is harmless if played again.
- **A collected note is taken in as if its writer had been heard from**, which only changes whether copies of
  what goes back to that writer are also left with carriers.
- **The PC holds only while it runs with its notebook open.** Locked, it says "not now" and senders go by
  relay; what it already holds waits for the lock to come off.
- **More than one PC** marked "My device": a phone collects from each it can reach, and a sender leaves a
  thing at the first home that takes it. Not tested.

## Phase 3 - addresses that are only a key, and hole punching

- An address becomes the key alone. Where a device is right now is asked of the network - the devices you
  are paired with, their PCs, and any reachable peer - and a device that moves tells them.
- Two devices that cannot be reached both reach a third that can, which tells each where the other is;
  both then open towards each other at the same moment (UDP hole punching), and most home routers let
  that through. The third carries nothing but the introduction.
- IPv6, which many mobile networks now give phones, makes this far more likely to work.
- When both ends are behind the strictest carrier NAT, no direct path exists; then, and only then, a
  relay - which can be any peer, your own PC first.

## Your own relays (built 2026-09-27, not yet seen with a real relay)

A relay is anybody's machine that can be reached; the list Mininotes starts from is the transport's, one
operator's choice. An owner who runs a relay, or trusts a friend's, can use it - and can use nothing else.
The idea is the "Manage hosts" box of Parlons!, another app on the same transport.

- **Where.** Settings → *Relays*, on the PC and the phone: the switch *Use the public relays* (on unless
  switched off), the owner's relays each with *Connected* or *Not answering* and a Remove, and a field to add
  one as `host:port`, IPv6 in brackets (`[2001:db8::1]:9001`). At most eight. Kept in the node's own settings
  (`relays`, one a line; `public relays`, `on` or `off`), read as the node starts and applied at once while it
  runs - no restart.
- **Kept only if it answered.** Adding dials the address and waits up to ten seconds for a Maxima greeting
  ("Checking…"). No greeting, nothing kept, and it says so; an address that never answered is nearly always
  mistyped, and one kept anyway would sit in the list looking like a relay that works. Refused before
  dialling: anything that is not `host:port`, ports outside 1-65535, home-network addresses and names
  (`10.`, `192.168.`, `fc00::/7`, `.local`, a bare name…), and IPv4 addresses the transport would never hand
  out (it takes any address starting `100.`, `172.`, `192.`, `198.` and a few more for a private one, so a
  relay there would be attached to and named to nobody).
- **Theirs first.** Every round, before the transport's own upkeep, the owner's relays are attached to before
  any other; when the pool is full, one of theirs coming in pushes out the lowest-scoring public one. One that
  does not take this device is left for five minutes. The addresses handed out (`Node.addresses`, pairing
  codes) name their relays first; they count as relays, so the rule that a code never names this device's
  own door still holds.
- **Off, and gossip.** Relays reach the transport's pool three ways: the list it is started with, the peers a
  relay names in its greeting, and the peers discovery saved last run. All of them come through one public
  listener on the transport's discovery. `Relays.Choice` takes that listener over before the node's store is
  opened: while the switch is off, only the owner's relays are ever made candidates. It also stops discovery
  outright - otherwise it would still dial every relay it hears of to check it - and lets go of (detaches and
  forgets) every relay that is not the owner's, at once when switched off while running. The shipped list is
  no longer handed to `start()` as the transport's "floor", because a floor cannot be taken back; it is given
  as ordinary candidates while the switch is on, and kept from being forgotten by the same listener. The
  vendored transport is not changed.
- **Off with none of theirs answering**, Settings says: *Notes go only directly - on your home network, or
  through your PC - and wait otherwise* (on the PC: *to your devices at home, and to those that reach this
  PC*). Outbox, receipts and carrying are unchanged, so what waits is sent when a way opens.

Tests: `RelaysTest` (typing: names, IPv4, IPv6 in brackets, ports at and past the bounds, home networks, the
transport's own rule; the kept form; the rules of choosing for on and off; room made; addresses ordered;
what it says) and `RelaysNodeTest`, real nodes on one machine with no internet: a door's greeting counts as an
answer and silence or nobody does not; switched off, a relay saved from an earlier run and one named by
gossip never become candidates and discovery is stopped; on, off and on again while running; a removed relay
let go of unless it is also a shipped one in use; one that does not take the device rested; theirs attached
and handed out first; and theirs pushing a public one out of a full pool.

### Not done, or not known

- **Not seen with a real relay**, nor on a phone or a PC in use. The stand-in relay in the tests is another
  node's door, which the transport attaches to as it would to a relay.
- **Switched back on while running**, the shipped relays return at once, but relays learned from gossip wait
  for the next start: the transport's discovery cannot be started again in the same process.
- **Off governs where this device is reached, not whom it sends to.** A friend attached only to public relays
  is still sent to through their relay; the directory is still asked for a contact that moved.
- **The check is a greeting.** Another device's door greets too, and the transport even attaches to it as to
  a relay, so it is kept. It cannot carry anything back, so the transport's own check that a relay really
  relays should drop it within minutes, and the row then says *Not answering*. Not tested.
- **The PC's router check** (phase 1) asks an attached relay to dial it back. With only the owner's relays,
  that works only if theirs answers that request.

## Two ways notes travel (built 2026-09-27, not yet seen between real devices)

The owner's decision, 2026-09-27: how notes travel is a choice, in Settings on both apps, in the first
network section, *How notes travel*. Two ways, a round mark beside each, what each means under it:

1. **Only between my devices.** Pure peer to peer: no public relay, no relay of the owner's, no directory, no
   relay-held file pieces, no cloud. Notes, answers and files go only device to device - a door heard on the
   same network, a door a device proved in public, and the owner's PC as home for their phones (phase 2: the
   PC is one of the owner's devices, not a third party). What cannot go waits in the outbox, as anything
   unanswered does. Said under it: *No relay, no server. Phones away from home cannot reach each other
   directly, so notes sync on the same Wi-Fi or through your PC when it can be reached; otherwise they wait
   until the devices meet. Pairing needs both devices on the same Wi-Fi.*
2. **Also through helpers when needed** - the default for every install, new and existing, and what was
   there before. Direct first, then the helpers: the owner's relays and the public relays (the *Relays*
   settings, shown under this way and only while it is chosen). **Later, a cloud folder** goes here too, as
   one more helper after the relays: a folder the owner already has somewhere, holding what is sealed exactly
   as a relay would. Not built, and nothing in the code assumes it; it belongs in this way, never in the
   first.

Kept in the node's settings (`travel`: `mine` or `helpers`; anything but `mine` is helpers) and applied at
once while the node runs, as the relays are: every relay let go of on the way in, the owner's relays taken
up again and everybody told on the way out.

### How every road to a relay is shut

- **The node's relays** (`Relays.Choice`, now with a *none* mode): no relay is ever a candidate - not the
  shipped list, not the owner's, not gossip, not the ones saved from last run - discovery is stopped, and
  everything known or attached is let go of. The owner's relays stay in the settings for coming back.
- **The transport's upkeep** (`maintain`) is not run at all: everything it does is relays and directories -
  keep-alives, finding relays, telling every contact at every address it has (relays included), publishing
  to a directory, asking one where a contact went. The door, the local network and the outbox are ours
  and go on. Telling contacts where this device is (`tellEverybody`, and after a relay change) is not done
  either. The PC's Profile *Reconnect* is off.
- **Sending** (`Direct.send`, every note, answer, ask, left/removed, file answer, carried thing and hello):
  the local door, the proved public door, the home - and then, where helpers may not be used, it throws
  (*Not in reach just now. It waits, and goes when the devices meet.*) instead of the transport's own
  `sendToContact`, which would fan out to every address, relays included, then ask the directory. A send
  that throws is counted as not sent: nothing is marked handed over, the note stays owed, and the line under
  its title says *not sent yet*.
- **A device the transport does not know** was sent to at the address off its code (`Post.routable`); now
  only if that address is on this network (a private, link-local or loopback host, `Direct.onThisNetwork`),
  since anywhere else it may be a relay. A permanent `MAX#` address, which is a directory to ask, never.
- **Files** - see below: never sent to a relay, never fetched from one.
- **Addresses handed out** (`Node.addresses`, the pairing code): this device's door on each local network,
  the likeliest home network first; no relay. The permanent address is empty.

One road is the transport's and is not shut: when a device *using helpers* pairs with one that is not, the
transport on the second answers the first's introduction by sending its card back to the first address the
first gave, which is a relay. One small contact card, only then; the vendored transport is not changed.

### Files

A file counts as gone up only when a relay has it - with helpers. Only between the owner's devices there is
no relay, and that is the choice: the file is sealed and cut into pieces as before, the pieces are kept on
this device (pinned), and the list naming them goes with the note. The other device fetches them **from this
device's door** when it can reach it - on the same network, or a door proved in public - and from nowhere
else: the relays a list names are not asked. So a file is *offered*, not gone: its card says **Waiting for
the other device** until they say they have it (with helpers it says *Not with everybody yet*). A device that
cannot get it does not tell the owner so, and the owner does not send it up again - there is nothing to send
it up to. A file offered this way, if the owner goes back to helpers, goes up to a relay when a device that
cannot get it says so (after four tries); it is not sent up by itself.

What does not travel only between devices, said plainly: a file whose owner is a phone reaches a device away
from home only once the two are on the same network, since nothing can reach a phone's door from outside.

### Pairing

A pairing code carries an address. With no relay, the only address a device has is its door on the network it
is on, so a code made in this way is `Mx<identity>@<local address>:<door>` and pairs only there
(`Pairing.reachable` takes it; the transport's refusal of private addresses applies to what it hands out,
not to this). The transport's own introduction does not work without a relay: it sends this device's card to
the door, and the other device answers with its card to the first address this one gave - which, with no
relay, is none. So `Node.introduceHere` sends the card (that is what makes this device known at the far door),
takes the far device's key from the door's address (a door is sealed to the device's identity key, which is
what the address carries), records it as a contact, and notes its door as heard here, so a note can go at
once. A code from anywhere else is not dialled at all: the scan is saved, and the phone or PC says *With
notes only between your devices, pairing needs both devices on the same Wi-Fi.*

### The PC's router

Proving the PC's public port takes a relay dialling it back (`ReachabilityManager` + `Probe`). Only between
the owner's devices there is none. Two ways were weighed: keep the mapping and give the unproved address to
paired devices only, or not ask the router at all. **Not asked**: an address nobody proved costs every phone
away from home a five-second wait at a door that may be shut, on every note and every round of collecting,
and the transport's own rule is never to give out an address on hope. So the router is not asked while this
way is chosen, a door proved earlier is taken back as the way is chosen (it cannot be proved again), and the
Profile says *Reached directly on your home network. Away from home it cannot be checked without a relay.*
With helpers again, the router is asked at the next round. So in this way the PC is reached at home, and a
phone away from home waits until it is back.

### Tests

`OnlyMineTest`, real nodes on one machine with no internet: a note goes door to door with a stand-in relay
never dialled; the door shut, nothing at all is dialled - not the stand-in relay the device is known by, not
the fan-out - the home is asked, and the send fails as one that waits (and with helpers the same send does go
to the stand-in, which is what it is there to count); pairing on the same network makes each device known to
the other, notes both ways, no relay; a code from elsewhere is refused before anything is dialled; no relay
is ever a candidate - shipped, the owner's, saved, gossiped - and discovery is stopped; and a node switched
while running from helpers to only between devices and back: its relay let go of at once, the owner's too,
not taken up round after round, kept as theirs, and attached again when helpers come back. `DirectTest`
(which addresses are on this network; a door's address carries its key; the PC's line), `RelaysTest` (how
the choice is kept and said), `EnclosureTest` (the card). Both run in the Android and the Windows test tasks.

### Answers that wait (seen on 0.1.029)

A phone in this way logged *could not answer: IllegalStateException* twice for every note that came to it, among
answers that did go. By the PC's log of the same hour, the two it could not give were to the PC: the PC dialled the phone at its door, but the phone
had no door for the PC - not heard on the network, or shut for a while after one that would not take - and no home
took it, so `Direct.send` threw *Not in reach just now*; once for the note, and once for the copy another phone
carried in. Unanswered, the PC sent the note again every half minute and left a copy with a carrier each time. Now
an answer that finds no way in this way is **kept** (`Outbox.Answers`: the latest for each device and note, for this
run, at most a day) and said at the next half-minute round that finds a way (`Post.again` → `sayKept`), and a copy
goes with whoever can carry it, whose door may be in reach when theirs is not. The log says *answer kept until they
are in reach (n kept)*, and any other failure by its kind (`Unsent.reason`), never an exception's name alone. A card
to a device out of reach no longer stops the cards to the rest. Tested in `OnlyMineTest` (the answer kept, nothing
dialled, then gone door to door once the door is heard) and `OutboxTest`. Why the phone heard no door for the PC is
not known: its log was not read here.

### Amber on all three (seen on 0.1.030)

The owner's three devices, all *My device* to each other, only between them, on one Wi-Fi: the note *Transfer* (nine
files) said the same everywhere by every log - the PC sent revision 57 by a door to both phones and both answered
*they now say the same* - and the amber arrow stood on all three. Text was not what waited: the mark is amber for a
device while any file that can go has not been said to be there (`Receipt.FILE_HERE`), and the PC's log for the
whole evening (21:51 to 00:07) holds no *they have a file* at all, while it took one file in itself at 22:48.

Why a file stayed "going" for good, from the code:

- **"I have this file" was said once, and lost for good.** A device says it right after fetching, to everybody
  with the note. Only between the owner's devices, a device out of reach at that moment - Graphene's kept answers show it could
  not reach the Pro's door that evening - never heard it, and
  nothing said it again: a list arriving that names a file already here plans nothing, and the sender stops
  sending its list after six tries (`Enclosure.TELLS_MOST`), counting the ones that did not go. Now **each list
  that arrives is answered** with the files it names that are here and came from elsewhere
  (`NoteStore.haveHere`, `Post.sayWeHave`), once a run for each file and device where it went, so any later send
  of the note - an edit, Send now, a copy a carrier brings - puts it right.
- **Said while the other was still fetching, and thrown away.** Two phones fetching the same files from the PC
  race; the first to have one tells the other, which did not have it yet, and only a file already kept was
  believed. Now a file waiting to be fetched here counts too (`NoteStore.fileReached`, `noteOfFile`).
- **A file its owner's door could not give** was waited for until the two were in reach. A device keeps the
  pieces it fetched and its door hands them out, so now, where the device that listed a file cannot be reached,
  the doors of devices that said they have it are asked (`Post.fromTheirDoor`, `NoteStore.holders`) - a phone
  has from the PC what the other phone listed.

**Graphene's kept answer** (*answer kept until they are in reach*, *could not ask*) was to the Pro: the PC's log
has both phones answering it, so the one Graphene could not reach was the other phone, whose door it had not heard
or had found shut. A door that would not take one message was not believed again for ten minutes, however often
it announced itself - right for a PC behind a firewall, wrong for a phone that dozed through one knock. Now a door
found shut once is believed again after a minute; one that fails again soon after is left for ten, as before; a
door that takes something starts again from nothing (`Direct.SHUT_FIRST`, `Direct.opened`). Which of the two it
was on the night is not known: the phones' logs were not read here.

**Said on the screen.** Pressing the amber mark, on either app, now says what waits and for whom before sending -
*Waiting to reach Pro with your changes*, *Waiting for Pixel 7 to confirm 3 files*, *Waiting to reach Pro with an
answer* - with **Send now** as its one button; a person's round says the writing and the files apart: *Pixel 7
has the text; 3 of 9 files still going* (`Waits`, `SyncStatus.waits`, `Person.standing`).

Tested: `DesktopOwnDevicesFilesTest` (three of the owner's devices with a note, three files and one too big: the
race believed, one lost word keeping one device amber and saying so, the next list answered and every mark a
tick; a device answers only with files it did not add), `WaitsTest`, `DirectTest` (a minute, then ten, then open
again). Not yet seen on the three devices; nothing here was run on a phone.

### Going back on a phone nobody touched (seen on 0.1.032)

The note *Transfer* open on the GrapheneOS phone, nobody typing there; the owner wrote on the Pro. The Pro's log:
revision 69 *answered ... and they put it with their own* by Graphene, which then sent a revision 70 of its own,
and the note on Graphene looked like it was going back.

- **Why.** What two devices last agreed on is kept per pair. Between three devices most of what reaches one came
  from another that took it first - the PC sends Graphene what it took from the Pro, *sent what they asked for* -
  and Graphene's agreement with the Pro stays where it was. The Pro's next revision was weighed against that old
  agreement; Graphene's copy of the Pro's own earlier text looked like writing of Graphene's; the two were put
  together, and a line the Pro had rewritten or deleted came back beside the new one. The same happens the other
  way round when an older text is passed on late. Nothing on the page did it: the page followed the notebook.
- **Now.** A note says which texts its note held before (`Parcel.Sent.history`: up to 48 SHA-256 prefixes, after
  the keys, where a build from before stops reading). Where the counts alone would merge, a device whose text is
  one the sender held takes what came, and a text this device has already kept is behind what is here
  (`Arriving.weigh`, `NoteStore.history`, `heldHere`). A page nobody typed on since it was last written down is
  taken to say what the notebook said, so it never becomes a revision of its own; blank rules tapped onto a page
  are not a writing until something is typed on them; and a note longer than the page's typing cap is no longer
  cut on the page. The log says which: *the page, not typed in, now says what arrived* or *what was typed here is
  put with it* (`Mininotes/Page`). Tested: `ThreeDevicesTest`, `DesktopThreeDevicesTest`, `ArrivingTest`.

**A file "still going" to the device it came from.** A device never says it has a file it added itself, and
between three devices the others can have it from the one in the middle - the likeliest reading, from the code, of
the Pro's *Graphene has the text; 1 of 12 files still going* for ever; the file was not identified on the devices. A list names only what the
note keeps at the sender's end, so a list that arrives now counts as its sender's word for every file on it
(`NoteStore.listed`), and a file waited for here from a device out of reach - or locked - is tried at once from the
door of any device whose list names it, the file's owner among them. The Pro holding thirteen
where the round counts twelve fits a file too big to go, or empty, which stays where it is by design; not checked. Tested in `DesktopOwnDevicesFilesTest`.

**A PC that locked itself.** A locked notebook keeps what arrives sealed and answers nothing, so the phones showed
it amber for ever. Now the PC says so as it locks, while it can still seal for them, and says *open again* when it
opens (`Receipt.LOCKED`, `OPENED`, `Post.sayLocked`; only to devices that said they know about persons, which read
an unknown number as nothing). The phones and the other PC then show it grey: *MainLaptop is locked; it takes it in
when opened*, on its round and in what the amber mark says, and it no longer keeps a note's mark amber
(`Waits.Where.locked`, `NoteStore.lockedThere`). Anything else it sends later says it is open. The PC locks again
after five minutes without use unless the owner chose otherwise in Security (`DesktopLock.minutes`); its log
shows it locking about five minutes after each opening that evening.

### Not done, or not known

- **Not seen between real devices.** Nothing here has run on a phone, or between a phone and a PC.
- **"Stays owed" is proved by the rule, not end to end.** The tests show the send failing as one that waits
  and nothing dialled; that `Post` then leaves the note owed is its existing rule (only an OK marks anything
  handed over). No test runs `Post.send` itself, which would start the app's one real node on this machine's
  ports.
- **A phone away from home reaches nothing**, not even its PC: the PC is not proved in public in this way.
- **A waiting file is fetched on the file clock**, which slows to hourly after ten tries - so a phone that was
  away all day may wait up to an hour at home for a file, though its notes come at once.
- **The transport's answer to an introduction**, described above, can send one contact card to a relay.
- **Switched back on while running**, relays learned from gossip wait for the next start, as with *Use the
  public relays*.
- **A PC with several networks** hands out the likeliest home address first (192.168, then 10., then the
  rest); a code made on a PC whose home network is in the 172 range may carry a virtual network's address.

## What does not change

Everything is sealed end to end as now: a relay, a PC hosting for its owner or a peer making an
introduction never reads a note. Pairing, receipts and the outbox stay as they are.
