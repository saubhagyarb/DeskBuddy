# DeskBuddy — Milestone 2 Design: Clipboard Sync, Text/File Sharing, App Shortcuts

**Date:** 2026-09-16
**Status:** Implemented under stated assumptions (user requested plan + implementation in one pass)
**Builds on:** `docs/superpowers/specs/2026-08-05-deskbuddy-m1-design.md`
**Source request:** "Add clipboard syncing, text and file sharing. Let the user select any
specific app from the desktop and add a shortcut to open it directly from the mobile."

---

## 1. Goal

Extend the paired phone ↔ desktop session with three capabilities:

1. **Clipboard sync** — desktop clipboard changes are pushed to the phone automatically;
   the phone can push its clipboard to the desktop or pull the desktop's clipboard on demand.
2. **Text & file sharing** — either side can send a text snippet or a file to the other.
   Received items land in an inbox on both sides. Files are saved to a `DeskBuddy` folder
   under Downloads on both platforms. Android's system share sheet can send text/files to the PC.
3. **App shortcuts** — the desktop app enumerates installed programs and lets the user pick
   any program (installed list or arbitrary file via file browser) as a shortcut. The phone
   shows the shortcut grid and launches apps with one tap. The phone can also browse the full
   installed list, launch from it, and pin/unpin shortcuts.

### Assumptions made (no user available for clarification)

| # | Assumption | Rationale |
|---|---|---|
| A1 | Phone → PC clipboard push is manual (button) or on foreground clipboard change; PC → phone is automatic while the "auto-sync" toggle is on (default on). | Android 10+ forbids reading the clipboard in the background. |
| A2 | File transfer reuses the existing WebSocket as base64 chunks inside JSON text frames (32 KiB raw per chunk). | One transport, one codec, testable in `commonTest`; LAN bandwidth makes the 33% overhead acceptable. |
| A3 | Received files go to `~/Downloads/DeskBuddy/` on desktop; on Android to MediaStore Downloads `Download/DeskBuddy` (API 29+) or the app's external Downloads dir (API 24–28). | Visible to the user without new permissions. |
| A4 | App enumeration: Windows reads Start Menu `.lnk` files (system + user); Linux parses `.desktop` files. Launch = `cmd /c start "" <path>` on Windows, `gtk-launch`/`Exec` on Linux. | Matches the requirements doc's platform table. |
| A5 | The desktop only launches ids that appear in its own catalog (installed list ∪ user-added shortcuts). | Prevents a paired phone from launching arbitrary paths. |
| A6 | No Hilt. Dependencies are wired manually as in M1 (project has no DI framework; adding Hilt to a KMP module is out of scope). | Match existing code style. |
| A7 | No app icons in M2 (letter avatars). | Icon extraction needs per-OS native work; deferred. |

---

## 2. Protocol additions (`shared/commonMain`)

All new payloads are `Command` subtypes so the same shapes travel in both directions:

- Phone → Desktop: `Envelope(token, command)` (unchanged).
- Desktop → Phone: new `Push(command)` message. The server only pushes to an authenticated session.

```kotlin
// Commands (both directions unless noted)
ClipboardCommand(text)                  // set the receiver's clipboard
ClipboardRequestCommand                 // phone→PC: reply is Push(ClipboardCommand(text))
TextShareCommand(text)                  // add to receiver's inbox (+ copies to clipboard)
FileOfferCommand(transferId, name, sizeBytes, mimeType)
FileChunkCommand(transferId, index, data /* base64 */)   // no Ack (backpressure via TCP)
FileCompleteCommand(transferId)
FileCancelCommand(transferId)
AppCatalogRequestCommand                // phone→PC: reply is AppCatalog
LaunchAppCommand(appId)                 // phone→PC
AddShortcutCommand(appId) / RemoveShortcutCommand(appId)   // phone→PC

// Messages
Push(command)                                   // desktop→phone
AppCatalog(apps: List<DesktopApp>, shortcutIds: List<String>)   // desktop→phone
DesktopApp(id, name)                            // id = launch path, opaque to the phone
```

New `ErrorCode`s: `NOT_FOUND` (unknown app id / transfer id), `TRANSFER_FAILED`.

Pure helpers in `commonMain`:
- `FileChunkCodec` — base64 encode/decode (`kotlin.io.encoding.Base64`).
- `TransferTracker` — per-transfer state machine (offer → ordered chunks → complete), independent of I/O. Used by both receivers.
- `FileNames.sanitize()` — strips path separators/control chars, falls back to `file`.
- `TransferIds.next()` — random hex id.

Constants: `Protocol.FILE_CHUNK_BYTES = 32 * 1024`, `Protocol.CLIPBOARD_POLL_MILLIS = 1000`.

---

## 3. Desktop side (`shared/jvmMain` + `desktopApp`)

### 3.1 Server

- `ControlServer` gains `suspend fun push(message): Boolean`. Every outbound frame (replies and
  pushes) goes through one per-session channel so only one coroutine writes to the socket.
  `push` returns false when no authenticated session exists.
- `ControlSession` now takes an `Actuators` bundle instead of a bare `MediaActuator` and
  `execute()` returns the reply message (`Ack`, `Push(ClipboardCommand)`, `AppCatalog`, or
  `null` for file chunks).

### 3.2 Actuators

```kotlin
data class Actuators(media: MediaActuator, share: ShareActuator, apps: AppCatalogService, files: FileReceiver)

interface ShareActuator { setClipboard(text); getClipboard(): String?; onTextShared(text) }
interface AppActuator   { installedApps(): List<DesktopApp>; launch(app: DesktopApp) }
```

- `AwtShareActuator` — `Toolkit.systemClipboard`; `onTextShared` sets clipboard and forwards
  to an inbox callback. Remembers the last text it wrote so the watcher can ignore echoes.
- `ClipboardWatcher` — polls the system clipboard every second, emits distinct new text.
- `FileReceiver` — writes chunks under `~/Downloads/DeskBuddy/<sanitized name>` (unique suffix on
  collision), driven by `TransferTracker`; calls `onFileReceived(path)` on completion; deletes
  partial files on cancel/error.
- `FileSender` — reads a file in `FILE_CHUNK_BYTES` chunks and pushes offer/chunks/complete
  through `ControlServer.push`; exposes progress; pushes `FileCancelCommand` on failure.
- `WindowsAppActuator` — enumerates `.lnk` under both Start Menu `Programs` roots (roots
  injectable for tests), skips names starting with "Uninstall"; launches via `cmd /c start`.
- `LinuxAppActuator` — parses `.desktop` (`Name`, `Exec`, `NoDisplay`, `Hidden`), launches by
  running the cleaned `Exec` line via `sh -c`.
- `UnsupportedAppActuator` — empty catalog, launch throws `UnsupportedOsException`.
- `ShortcutStore` — JSON at `~/.deskbuddy/shortcuts.json`, list of `DesktopApp`.
- `AppCatalogService` — merges installed apps + stored shortcuts, exposes
  `StateFlow<AppCatalog>`, validates launch ids (A5), `addCustom(path)` for file-browser picks.
- `ActuatorFactory.create(osName)` now returns `Actuators`.

### 3.3 Desktop UI

Window becomes a two-column layout: left = existing status panel (waiting / PIN / connected),
right = tabs **Share** and **App shortcuts**.

- **Share tab:** text field + "Send to phone", "Send file…" (AWT `FileDialog`), transfer
  progress, "Auto-sync clipboard" checkbox, inbox of received text (Copy) and files (Open folder).
- **Shortcuts tab:** shortcut list with Remove; "Add installed app…" opens a searchable picker
  dialog; "Browse for program…" opens a `FileDialog` and adds the chosen file.
- `DesktopController` (desktopApp) wires the server, clipboard watcher, file sender, inbox
  state, and pushes `AppCatalog` to the phone whenever shortcuts change.

---

## 4. Android side (`androidApp`)

### 4.1 Structure

`ConnectionViewModel` becomes a thin facade. Its session loop moves to `session/RemoteSession`
(pure Kotlin class taking a `CoroutineScope`), which exposes `uiState`, `errors`,
`incoming: SharedFlow<Message>` and `suspend fun send(command)`.

Feature controllers (plain classes, one file each, injectable dependencies):

- `share/ClipboardShareController` — auto-sync toggle, send/pull clipboard, send text,
  handles incoming `ClipboardCommand` / `TextShareCommand`, keeps the inbox.
- `share/FileTransferController` — `sendFile(uri)` (streams via `ContentResolver`),
  receives files via `TransferTracker` + `ReceivedFileStore`, exposes progress.
- `share/ReceivedFileStore` — MediaStore (API 29+) or app external dir (API 24–28).
- `apps/AppShortcutsController` — catalog, shortcuts, launch, pin/unpin, refresh on connect.
- `share/PendingShare` — text/file handed in by the system share sheet before a session exists;
  flushed when `Connected`.

### 4.2 UI

`RemoteScreen` becomes a three-tab shell (Media / Apps / Share). Portrait uses a bottom
`NavigationBar`; landscape uses a `NavigationRail` (distinct layouts per M1 rule).
The M1 media layouts move unchanged into `MediaTab`.

- **Apps tab:** shortcut grid (tap = launch), searchable "All apps" list with star toggle,
  refresh action.
- **Share tab:** clipboard card (auto-sync switch, send/pull buttons), text card, file card
  (picker + progress + cancel), inbox list (copy / saved path).
- `MainActivity` handles `ACTION_SEND` for `text/plain` and `*/*` streams; streams are copied to
  cache immediately so the grant lifetime does not matter.

All strings in `strings.xml`.

---

## 5. Error handling

- Unknown app id → `Ack(NOT_FOUND)` → snackbar "That app is no longer available on the PC".
- File write failure → `Ack(TRANSFER_FAILED)`, receiver deletes the partial file.
- Sender failure mid-transfer → `FileCancelCommand`; receiver discards.
- Clipboard access failures (AWT `IllegalStateException`) are swallowed on the watcher and
  surface as `INTERNAL` on explicit commands.
- Pushes to a non-authenticated session are dropped (return false), never queued.

---

## 6. Testing

- `commonTest`: codec round-trips for every new message; `FileChunkCodec`; `TransferTracker`
  (ordered chunks, out-of-order rejection, unknown id, size mismatch); `FileNames.sanitize`.
- `jvmTest`: `ControlSessionTest` extended for clipboard/text/file/app commands with fake
  actuators; `FileReceiverTest` (temp dir); `ShortcutStoreTest`; `AppCatalogServiceTest`
  (launch validation, add/remove); `WindowsAppActuatorTest` (enumeration from temp roots);
  `ControlServerIntegrationTest` gains a push test.
- Verification: `./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:assembleDebug :desktopApp:build`.
- Manual: phone ↔ Windows PC end-to-end for each feature.
