# minimaDocs

Android first office and image workspace, forked from Mininotes with its embedded
Maxima messaging transport. Repository: [eurobuddha/minimaDocs](https://github.com/eurobuddha/minimaDocs), branch `minimadocs`.

## Android 0.3.1

[Download the signed Android APK](https://github.com/eurobuddha/minimaDocs/releases/latest).
Install `minimaDocs-0.3.1.apk` to update an existing installation while keeping its
data. The release includes a SHA-256 checksum. Android 9 or newer is required.

Also available through **PandaApps**, **PandaGet**, and the **minimaCore App Store**
using the shared [PandaApps catalogue](https://github.com/eurobuddha/minima-core-apks).
The [web store and IPFS mirror](https://ipfs.eurobuddha.com/) carry the same signed APK.

The new workspace has **Files**, **Shared**, **People**, and **Settings**, with
document previews and direct editing. Word has dedicated phone and wide-screen
controls, reading-width and print layouts, formatting, tables, comments and
tracked changes. Its controls remain accessible above the Android keyboard.
The interface and new documents use Manrope; imported documents retain their fonts.

**Docs**, **Sheets**, and **Images** each have a library, New and Import actions.
Docs and Sheets use the bundled offline ONLYOFFICE engine from `ranuts/document`.
Images use miniPaint with layers, tools, effects, undo and editable project files.
The native interface uses Atelier/Salon's Katalog design.

- Open a document directly from Files. Save updates that same document.
- DOCX and XLSX import/export preserve the editable Office file. PDF and flattened
  PNG exports use Android's Save As picker. Layered images use `.minimadocs-image.json`.
  Home's Send to another app action shares the selected document file.
- Autosave keeps complete snapshots on this phone. Its interval adapts to export
  cost (30–180 seconds, initially 60 seconds), and leaving the app requests a save.
  Recovery reopens the last completed save. Process termination or locking can
  lose edits made since that save; wait for Saved before an intentional shutdown.
- Saved updates share through the existing Maxima file transport. A small causal
  version record keeps concurrent edits as separate versions for review. Choosing
  a version explicitly replaces the versions reviewed. Unseen edits remain separate.
  Superseded binary snapshots are retired; the Versions action reviews current
  concurrent versions. Plain-text notes retain their inherited text history.
- Read-only document access is enforced again when writing storage. A separate
  copy can be edited independently.
- Files are limited to 16 MiB each. Imported originals remain on Home; Save creates
  the editable document. Locked notebooks use existing SQLCipher and file encryption.

### Sharing without Parlons

Open a document and choose **Share**:

- **Link or QR code** creates a document invitation with Can view or Can edit access.
  Show the QR code, copy the invitation, or send it using Android's share sheet.
- **Enter recipient** accepts their minimaDocs pairing code or an already-paired
  recipient's Maxima address. **Scan recipient QR** reads the same code by camera.
- **Previously paired** lets you share directly with a saved recipient.

To accept an invitation, open **Shared → Open invitation** and scan or paste it.
Your own contact code and verification digits are under **People → Maxima contacts**.
A new recipient's bare address does not contain document encryption keys; exchange
pairing codes or send a document invitation first. Parlons is optional throughout.

The existing MiniNotes pairing format and encrypted Maxima transport are reused.
New links use `minimadocs://pair/`; existing `mininotes://pair/` links still open.
An invitation allows its selected access for 15 minutes; later acceptances ask the
owner for approval. Manage access afterwards under **Share → Access & updates**.

### Parlons contacts

Open **People**, or open a document and choose **Share → Parlons contacts**.
Approve minimaDocs once in **Parlons → Settings → Apps using Maxima** (Connected apps).
Both installed apps must have the same signing certificate, as Parlons requires.

The contact list is read from Parlons and refreshes on its contact-change events.
You can search, add or remove contacts, and send a minimaDocs pairing/document
invitation with the selected access level. The recipient links minimaDocs to Parlons
and reviews the invitation before accepting it. Invitations arriving while the app
is closed are stored encrypted with the existing Android Keystore key; they expire
after seven days. A notification announces a new invitation when notifications are
allowed. Opening contacts does not wait for minimaDocs to connect to a relay.
Removing a Parlons contact does not revoke existing document access:
change that in the document's sharing settings.

### How Maxima fits

minimaDocs runs its own embedded Maxima messaging node. Parlons supplies its contact
book and carries the initial invitation on a dedicated application channel. The
existing minimaDocs node carries subsequent encrypted document updates.

This messaging node does not validate the Minima blockchain or mine blocks. Shared
updates are saved document snapshots; simultaneous typing is not merged character
by character. Delivery depends on connectivity, relay availability and Android's
background-execution policy. No document server or cloud Office service is required.

The offline engines make the APK large (about 212 MiB). Automatic update checks
are disabled in 0.3.1; download updates from this repository's Releases page.

## Reused implementations

Base Mininotes commit: `1aa9929f330f64b4b4f868e47cbb21bc56edb24a`.
The `upstream` remote retains the original repository and history.

| Source | Reuse and adaptations |
| --- | --- |
| `android/maxima-core/`, `NoteStore`, `Sealed`, attachment sharing | Existing node, storage, encryption and transport. Document snapshots use the existing file transaction and sharing calls. |
| Salon `app/src/main/java/com/eurobuddha/salon/Design.java` and its fonts | Katalog helper reused in `Design.java`; Android resource font loading replaces the AndroidX call. |
| Atelier `android/app/src/main/java/com/eurobuddha/statenft/Design.java`, `FILTRActivity.java`, FILTR integration and tests | Inspected design and editor integration. FILTR was not selected as the layered project engine. |
| PocketWeb `AppServer.java`, `MiniwebUrl.java`, `MimeTypes.java`, `MiniwebUrlTest.java` | Asset-only HTTPS WebView pattern, URL validation, MIME types and tests. Editor routing serves bundled assets and blocks external requests. |
| Parlons `app/src/main/java/com/eurobuddha/maxima/app/ipc/` and `app/build.gradle` | Existing registration, signature gate, approval, contact format, events, application subscription and family signing configuration. `MaximaConnection` adds a contact client and fixes explicit reply dispatch. |
| [ranuts/document](https://github.com/ranuts/document/tree/9c743826d0152239dc7ad51677535d59679c7ff1) | Pinned `9c743826d0152239dc7ad51677535d59679c7ff1`. Existing embed API, editor readiness flags and DOCX/XLSX conversion. ONLYOFFICE logos and About retained. |
| [miniPaint](https://github.com/viliusle/miniPaint/tree/a79733eb803fc97084ef0ee4faa96b031e69e1c0) | Pinned `a79733eb803fc97084ef0ee4faa96b031e69e1c0`. Existing `FileSave.export_as_json`, `FileOpen.load_json`, image import actions and undo engine. |

Local sibling sources inspected live under `/Users/eurobuddha/Projects/minima/`:
`apks/salon`, `apks/pocketweb`, and `mds/statenft-suite`. No compatible office engine
was found in the searched sibling projects. The chosen upstream integration code,
its dependencies, callers and available tests were inspected; the entire vendor
engine has not been audited.

## Build Android

Use JDK 17 or 21, Android SDK 37, Node 24 and pnpm 11.4.0.
The reusable CI action `.github/actions/prepare-editors/action.yml` checks out,
tests and builds the exact editor revisions before packaging Android.

For a local build, build the pinned `ranuts/document` checkout with
`pnpm install --frozen-lockfile`. Before testing and building, register the bundled
Manrope fonts with `node /path/to/minimaDocs/scripts/customize-office.mjs .`,
install the test browser with `pnpm exec playwright install chromium`, and run
`node bin/font-thumbnails.mjs`. Then run `pnpm test` and `pnpm build`. Keep its `dist/`,
`LICENSE` and `NOTICE` together. Then, from this repository:

```sh
node scripts/prepare-editors.cjs /path/to/office-engine /path/to/miniPaint
node --test tests/editor-bridge.test.cjs tests/core.test.cjs
cd android
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug
```

The preparation script bundles the engines and decompresses the converter WASM
for Android asset responses. Generated assets are ignored by Git; an APK build
fails if they have not been prepared. `android/local.properties` supplies the SDK
path. Distribution copies of installers must include their version, for example
`minimaDocs-0.3.0-debug.apk`.

Local release builds reuse Parlons' `MINIMA_FAMILY_RELEASE_*` Gradle properties,
or the existing untracked `android/keystore.properties` format.

Release tags build a **draft** Android release using configured signing secrets.
They do not publish a Windows build. The inherited Windows source is retained.

After publishing a signed GitHub release, update the `com.eurobuddha.minimadocs`
entry in `minima-core-apks/apks.json` with its version, versionCode, asset URL and
SHA-256. Reuse that repository's `scripts/publish-app.py` and run `./check.py`
before committing and pushing the catalogue. Refresh the IPFS store with
`ssh hetzner 'sudo /usr/local/bin/build_ipfs_store.sh'`, then verify the public
catalogue and mirrored APK against the release hash. The store clients share
this catalogue; they do not need new builds for an app release.

## Validation

Local checks on 2026-10-05:

- 0.3.1 restores direct sharing in the workspace. The Android sharing regression
  test creates a read-only invitation without a Parlons connection, checks the
  copied payload and QR round trip, opens recipient entry, and reviews a pasted
  invitation. Unit tests retain compatibility with old and new link schemes.

- The 0.3.0 release passed both [source checks](https://github.com/eurobuddha/minimaDocs/actions/runs/37302124255)
  and [Android device checks](https://github.com/eurobuddha/minimaDocs/actions/runs/37302124218).
  The workspace test opens the new Word controls, saves and reopens Manrope DOCX
  text and tables, and exports PDF. Manrope embedding in the PDF was also verified.
- The signed 0.3.0 APK was installed as an update and launched on the Galaxy Z Fold.

- 705 Android unit tests pass, including causal convergence, replay and malformed
  document handling, read-only replicas, and Parlons contact/invitation parsing.
- 15 JavaScript tests pass, including origin checks, readiness, duplicate replies,
  dirty state, timeout/retry, file limits, and keeping PDF exports separate from saves.
- Release lint and signed release assembly pass.
- Android storage instrumentation passed for same-document saves, concurrent
  replicas, resolution, read-only refusal, encrypted reopening and locked refusal.
- Earlier emulator checks opened all three editors, edited a DOCX, calculated
  `SUM(2,3)` in XLSX, and saved/reopened a painted image layer.
- The pinned office engine passed 3,462 upstream tests and five Chromium
  compatibility tests: DOCX tracked changes and headers/footers, XLSX merged cells,
  formulas and a 20,000-row sheet. All 12 embed regression tests also passed.
  [Compatibility run](https://github.com/eurobuddha/minimaDocs/actions/runs/37243558898).

The `Android device checks` workflow builds the existing Parlons source and runs
storage, encrypted invitation recovery, packaged DOCX/XLSX round trips, PDF/PNG
exports, and real Parlons approval/contacts on a disposable Android emulator.
All of these checks passed on Android API 35 against Parlons commit
`668f3544601f0d226fc067b307519d0b2f522d7b`.
[Device integration run](https://github.com/eurobuddha/minimaDocs/actions/runs/37244000835).
Synthetic editor screenshots and round-trip files are retained by subsequent runs.
Live invitation and document delivery between two independent network devices has
not been exercised in this change; replica convergence and file receipt are covered
by the native storage test using the existing receive path.

See [NOTICE](NOTICE) for attribution. Office carries AGPL-3.0 terms, miniPaint is
MIT, and the fonts use the SIL Open Font License. Engine licenses ship in the APK.

## Code Review

### Summary

Snapshots use transactional notebook storage, immutable attachment IDs and causal
version records. Office/image payloads never enter the plain-text merge. Parlons
integration preserves its signature permission and approval gate. Invitations are
bounded and encrypted at rest. Debug test activities are absent from release builds.

### Findings addressed

- **Critical — concurrent versions could be discarded by autosave.** Conflict review
  now requires explicit resolution; autosave preserves unseen concurrent branches.
- **Critical — text history could restore a document record without its file.**
  Document Versions now opens the editor's current-version chooser. The text
  restore action refuses document records before making any storage changes.
- **Major — explicit Parlons replies were dropped.** The manifest receiver now
  dispatches matching requests, with a signature permission on both IPC receivers.
- **Major — stale export replies could finish a later save.** Unique request IDs,
  conversion guards and timeout checks prevent cross-request callbacks.
- **Major — copied backups retained old file IDs.** Document records now remap their
  attachment IDs and validate required files before moving them.
- **Major — Android sharing sent the internal document record as text.** Single
  documents now share their file through the existing read-only provider. Collection
  text exports identify documents that need to be exported separately.

### Verdict

**Approve with suggestions.** Unit, lint, signed assembly, browser compatibility and
Android device integration checks pass. Before relying on cross-device delivery,
exercise the live Maxima path with two disposable identities. Autosave recovers
completed snapshots; it does not recover keystrokes since the last completed save.

## Upstream Mininotes documentation

The remainder describes the inherited application. Its release links and
installation instructions refer to upstream Mininotes, not minimaDocs.

### Mininotes

A paper pad on your phone that can hold the same note as another phone — yours,
or somebody else's — with no server, no account, and nobody in between who can
read it.

**Sharing is what it is for.** A note you share goes over **Maxima**, the
messaging layer of the [Minima](https://minima.global) network: the Maxima node
inside the app seals the note and hands it to a public relay of the Minima
network, which passes it to the other phone's node. It travels phone to phone,
sealed end to end, and it arrives while the pad is closed. Who may read, write
or hand a thing on works the way it does in Google Drive; when two people write
in the same note before either has seen the other, the two are merged line by
line and nothing either of them wrote is lost.

There is nothing else to install — the Maxima node is inside the app. What it
does need is the Minima network: its public relay nodes are what carry a note
from one phone to the other, so where none can be reached, nothing goes. On its
own, the pad still works: it opens on a ruled page with the cursor already in
it, keeps what you write the moment you write it, and needs no connection at all.

[![Latest release](https://img.shields.io/github/v/release/mininotesorg/mininotes?label=latest%20build)](https://github.com/mininotesorg/mininotes/releases/latest)

**Latest build:** the badge above names it, and
[Releases](https://github.com/mininotesorg/mininotes/releases/latest) has the file.
The app tells you itself when a newer one is out: it looks once a day, says so in
one line, and keeps **Update to v…** in the **⋮** menu until you have it.

---

## Install it

1. Download `Mininotes-<version>.apk` from
   [the latest release](https://github.com/mininotesorg/mininotes/releases/latest).
2. Check it is the file that was published:

   ```sh
   sha256sum -c Mininotes-<version>.apk.sha256
   ```

3. Open it on the phone. Android will ask whether to allow installing from
   wherever you downloaded it; that permission is per-app and can be switched
   back off afterwards.

Android 9 (API 28) or newer. No account, no sign-in, no analytics and
no advertising identifier. Two things use the network and nothing else does: the
pad's own Maxima node, which carries what you share, and the update check, which
reads one line of text from this repository, once a day when the pad is opened
and whenever you tap for it. Nothing is sent with it, nothing is downloaded and
nothing is installed; **⋮ → About** has the switch that turns the daily look off.

### Sharing between phones

Nothing else has to be installed: the pad is its own Maxima node. It needs the
network, and the first time it takes a few seconds to find a relay. **⋮ →
Profile → Connection** says *Connected* once it has, and says what to do if it
has not.

1. **Show a code.** Open the collection or note, tap the ring in its bar
   (or **⋮ → Sharing**), then **Share → Show them my code**. *Read only* or
   *Read & write* is chosen on the code itself.
2. **Scan it** on the other phone: point the phone's own camera at it — the
   code is a `mininotes://` link and the camera offers to open it — or use the
   scanner in the app, **⋮ → People and devices → Share with someone**.
   Accept. A strip at the foot of the screen says what is happening — saving,
   finding the other phone, telling it — until the thing arrives.
3. The first phone asks whether to give it to them. Say yes and it is sent.
4. From then on it keeps itself up to date: what you write goes a few seconds
   after you stop, and what they write comes back and is merged. The ring on
   every thing says where it stands: empty for *only on this phone*, a tick for
   *up to date*, an arrow for *waiting to send* — tap it to sync now — and two
   bars for *paused*.

Tap the ring on anything shared and one box says it all. **Who has access**: a
role beside each person — Owner, Admin, Can write, Can read — which the owner
or an admin changes from a drop-down, and **Add someone**. **Syncing**: *Sync
automatically* and its delay, *Sync now*, *Pause receiving*, and **Unfollow**
for anyone but the owner, which tells the others.

Once a phone is paired with anything, the pad goes on listening after it is
closed. Android shows a notification for as long as that lasts. **⋮ → Settings →
Listen while the pad is closed** switches it off, and so does **Stop listening**
on the notification. **Keep listening while the phone sleeps** (Settings) asks
Android to let Mininotes through its deep sleep and wakes it for a moment every
few minutes; without it, the pad hears while the phone is awake or charging.
Whatever was sent in the meantime is sent again until your phone answers, and
another of your devices that is on can carry it to you. [docs/SHARING.md](docs/SHARING.md) says
plainly what is not built yet, and the first item on that list is the one to
read before trusting this with anything two people both write in.

The address a phone hands out is a Maxima contact address: a public key, then
the relay it can be reached through, like `Mx…@45.77.57.24:9501`. It changes when
the node moves relay, which is why pairing also introduces the two nodes to each
other rather than relying on it.

See [docs/SHARING.md](docs/SHARING.md) for what is sealed, what is signed, and
what happens when two people write on the same note before either has seen the
other.

---

## What it does

- **Shares.** A collection or one note, to phones you have paired with,
  sealed end to end over Maxima — to read, to write in, or to hand on. It
  arrives while the pad is closed, and two people's writing is merged.
- **Writes.** A ruled page, the cursor in it, saved as you go. No save button.
- **Holds.** Notes and collections, and a collection holds notes and other
  collections, as deep as you like. (Since 0.2.001 a book is a collection inside a
  collection; see [docs/HOME.md](docs/HOME.md).)
- **Colours.** One colour scale and a tone slider, set per collection or note,
  and it follows the phone's light and dark themes.
- **Attaches.** Any file, or several at once, kept with the note and carried
  in backups.
- **Takes what you share.** Mininotes is on Android's share sheet: a
  screenshot, photos, files, text or a link go into a new note or one of the
  notes you used last.
- **Sends files.** Straight to another of your devices or somebody you paired
  with, in no note - door to door on the same Wi-Fi. What comes lands on Home,
  marked *new* until you open it, and can be moved anywhere; files from
  somebody else's device are asked about first. **⋮ → Sent files** lists what
  went.
- **Remembers.** Every version of a note, with a way back to any of them.
- **Puts away.** One archive and one bin for everything, on Home beside your notes, with a way back out.
- **Backs up.** One file holding every collection, note and attachment.
  Importing asks whether to add to what is here or replace it.
- **Finds.** Search across the whole pad, your favourites, what you wrote in
  lately, and **Tree view** — every collection and note at once.

**Lock Mininotes** (⋮ → Settings → Security) and the notebook, its attachments
and your backups are encrypted on the device (SQLCipher). It then opens with your
fingerprint or screen lock, with a backup password and 12 recovery words as
spare keys. Every note is also sealed end to end on its way to another device.

---

## Say something about it

**⋮ → Feedback** in the app. Pick what sort of thing it is and which part of the
app it is about, write it, and tap **Post it** — that opens a filled-in form here
under your own name. Everything anybody has said is in one place:

<https://github.com/mininotesorg/mininotes/issues?q=is%3Aissue+label%3Afeedback>

The app never posts for you and sends nothing by itself: it builds a web address
out of what you typed and hands it to the browser. What travels with a report is
one line — version, Android version, phone — shown to you before you send it.
Reports are public, so say what the app did rather than who you are.

A **security** problem goes privately to
[a security advisory](https://github.com/mininotesorg/mininotes/security/advisories/new),
not to an issue.

[docs/FEEDBACK.md](docs/FEEDBACK.md) has the rest.

---

## Build it yourself

### Windows preview

The Windows desktop build lives in [windows/](windows/README.md). On Windows
with JDK 17, run `./windows/build.ps1 -Package`; extract the resulting
`dist/latest/Mininotes-Windows-0.2.012.zip` and open `Mininotes/Mininotes.exe`.
The bundle includes Java. It is a preview: what was checked on which build is
recorded before each release in a log the maintainer keeps privately.

### Android

You need JDK 17 and the Android SDK (compileSdk 36, build-tools for AGP 8.10.1).

```sh
cd android
./gradlew testReleaseUnitTest lintRelease assembleRelease
```

The APK lands in `android/app/build/outputs/apk/release/`. That build is
unsigned; sign it with your own key before installing, or use a debug build:

```sh
./gradlew installDebug
```

`android/local.properties` points at your SDK and is not in the repository.

### Checks

```sh
cd android && ./gradlew test lint
```

Over two hundred unit tests, no device needed. They cover the pieces where being
wrong loses somebody's writing: the merge, what to do with an arriving note and
with the page that is open when it arrives, who is a member of what, the schema
migrations, the pairing format, QR encoding and decoding, attachments and the
colour scale. What a device is needed for is checked on two phones before each
release, in a log the maintainer keeps privately rather than publishes, because it
names their phones and quotes their notes. Ask in an issue for the entry for a build.

---

## Layout

| Path | What is in it |
| --- | --- |
| [android/](android/) | The Android app. Everything below is about it. |
| [windows/](windows/README.md) | Windows desktop preview and packaging. |
| [android/app/src/main/java/org/mininotes/android/](android/app/src/main/java/org/mininotes/android/) | All the code. No XML layouts; the views are built in Java. |
| [docs/PRODUCT.md](docs/PRODUCT.md) | What the app is for, and what it is not. |
| [docs/SHARING.md](docs/SHARING.md) | The sync design: keys, pairing, merge, conflicts. |
| [app/](app/) | The older MiniDapp, kept for anybody still running it. Not developed. |

The app has no Compose and no XML layouts, and few third-party runtime
dependencies: ZXing for QR codes, and SQLCipher with AndroidX SQLite for the
encrypted notebook. It is built that way so the whole thing can be read.

---

## Licence

Mininotes is **free and open-source software** under the
[GNU General Public License, version 3 or later](LICENSE). You may use it,
study it, change it and pass it on, and sell it too - but whatever you pass on,
changed or not, goes with its source code under the same licence. Third-party
terms are in [NOTICE](NOTICE).

The Maxima transport inside the app is the core of
[eurobuddha/maxima](https://github.com/eurobuddha/maxima), vendored unmodified
with its author's permission and credited in NOTICE. It is theirs, and is not
under the GPL; its own repository does not yet carry a licence.

Until 26 September 2026 Mininotes was source-available (Apache 2.0 with the
Commons Clause and a paid-product condition); copies taken before then keep
those terms.

Contributions: [CONTRIBUTING.md](CONTRIBUTING.md).
Security reports: [SECURITY.md](SECURITY.md).
