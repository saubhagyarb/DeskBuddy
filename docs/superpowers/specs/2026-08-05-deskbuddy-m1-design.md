# DeskBuddy — Milestone 1 Design: Connect + Media/Volume Control

**Date:** 2026-08-05
**Status:** Approved by user (brainstorming session)
**Source requirements:** `plan/pc-remote-control-app-requirements.md`

---

## 1. Goal

An Android phone discovers a Windows desktop on the same WiFi network (or via
manual IP entry), pairs with it using a 6-digit PIN, and controls media
playback and volume: play/pause, next track, previous track, volume up,
volume down, mute toggle. The desktop app shows the PIN during pairing,
shows connection status, and runs the control server.

Milestone 1 proves the full architecture end-to-end: shared KMP protocol,
discovery, pairing/auth, transport, desktop actuation, and both UIs.

### In scope (M1)

- Shared command protocol (`kotlinx.serialization`, JSON)
- Ktor WebSocket transport (client on Android, embedded server on desktop)
- mDNS discovery (Android NSD + JmDNS advertising on desktop) with
  manual-IP fallback
- PIN pairing with persistent token on both sides
- Media/volume actuation on **Windows only**, via JNA `SendInput`
- Android UI: ConnectScreen + RemoteScreen (distinct portrait and
  landscape layouts)
- Desktop UI: single window with waiting / pairing / connected states

### Deferred (later milestones)

- Currently-playing track display (needs WinRT media session bridge)
- Power actions, app launching/window control, trackpad, virtual keyboard,
  key combos, presentation mode
- Linux actuators (structure allows it; `UnsupportedOsActuator` until then)
- System tray integration on desktop
- TLS / transport encryption (LAN-only threat model accepted for M1)
- USB helper UI (`adb forward tcp:8765 tcp:8765` already works by design,
  since WebSocket runs over plain TCP)
- Multi-PC profiles; the `:webApp` target stays in the build but dormant

---

## 2. Module layout

Existing KMP scaffold at repo root, package `com.saubh.deskbuddy`:

```
shared/
  commonMain/   Protocol only: command models, envelope, acks, pairing state
                machine (pure logic). MUST stay web-compatible — no sockets,
                no JVM/Android APIs here (the dormant wasmJs/js targets still
                compile this source set).
  androidMain/  Ktor WebSocket client wrapper, NSD discovery
  jvmMain/      Ktor WebSocket embedded server, JmDNS advertiser,
                MediaActuator interface + WindowsMediaActuator (JNA),
                UnsupportedOsActuator, paired-device store
androidApp/     Compose UI: ConnectScreen, RemoteScreen, ConnectionViewModel,
                ConnectionUiState, DataStore persistence
desktopApp/     Compose Desktop UI: waiting / pairing / connected window
webApp/         Untouched, dormant
```

New dependencies: Ktor (client-core/cio + websockets on Android; server-cio +
websockets on JVM), JmDNS (`org.jmdns:jmdns`), JNA (`net.java.dev.jna:jna` +
`jna-platform`), Preferences DataStore (Android), `kotlinx.serialization-json`.

---

## 3. Protocol

Every WebSocket **text frame** carries one JSON message. Messages form a
sealed hierarchy with polymorphic serialization (`type` discriminator):

```kotlin
// commonMain — illustrative shape, not final code
@Serializable sealed interface Message

// Phone → Desktop
@Serializable data class PairRequest(val deviceName: String) : Message
@Serializable data class PairAttempt(val pin: String) : Message
@Serializable data class Envelope(val token: String, val command: Command) : Message

// Desktop → Phone
@Serializable data class PairSuccess(val token: String) : Message
@Serializable data class PairFailure(val attemptsLeft: Int) : Message
@Serializable data class Ack(val ok: Boolean, val error: ErrorCode? = null) : Message

@Serializable sealed interface Command
@Serializable data class MediaCommand(val action: MediaAction) : Command
// future: TrackpadEvent, PowerCommand, LaunchAppCommand, KeyComboCommand

enum class MediaAction { PLAY_PAUSE, NEXT, PREVIOUS, VOLUME_UP, VOLUME_DOWN, MUTE_TOGGLE }
enum class ErrorCode { AUTH_REQUIRED, BUSY, UNSUPPORTED_OS, INTERNAL }
```

- Fixed port **8765**, WebSocket path `/control`.
- Commands are fire-and-forget from the UI; each gets an `Ack`. `Ack` errors
  surface as a snackbar with a mapped human-readable message.
- The `Command` hierarchy is the extension point for every later milestone —
  new command types are added without touching transport or pairing.

## 4. Pairing & auth

First-time flow:

1. Phone connects and sends `PairRequest(deviceName)`.
2. Desktop generates a random 6-digit PIN, displays it large in its window,
   starts a 60-second timeout.
3. User types the PIN on the phone → `PairAttempt(pin)`.
4. Correct PIN → desktop generates a random 32-byte hex token, persists
   `{deviceName, token}`, replies `PairSuccess(token)`.
   Wrong PIN → `PairFailure(attemptsLeft)`; **3 attempts max**, then the
   connection is closed and a new pairing requires a fresh connection.
   Timeout → connection closed.
5. Phone persists `{host, port, desktopName, token}` in Preferences DataStore.

Subsequent connections: every `Envelope` carries the token. Unknown or
missing token → `Ack(ok=false, error=AUTH_REQUIRED)` and the phone falls
back to the pairing flow.

Desktop persists paired devices as JSON at `~/.deskbuddy/paired.json`.
The pairing state machine (PIN check, attempt counting, timeout transitions)
lives in `commonMain` as pure logic so it is unit-testable without sockets.

Security posture (explicit): plaintext WebSocket on the LAN. Acceptable for
M1 because pairing gates command execution; transport encryption is a later
hardening milestone.

## 5. Desktop side

**Server** (`shared/jvmMain`): embedded Ktor CIO server on `0.0.0.0:8765`.
One active control session at a time; a second connection is rejected with
`Ack(error=BUSY)` and closed. JmDNS advertises `_deskbuddy._tcp.local` on
port 8765 with the machine hostname as the service name.

**Actuation** (`shared/jvmMain`):

```kotlin
interface MediaActuator {
    fun playPause(); fun next(); fun previous()
    fun volumeUp(); fun volumeDown(); fun muteToggle()
}
```

- `WindowsMediaActuator`: JNA `SendInput` with virtual-key codes
  `VK_MEDIA_PLAY_PAUSE` (0xB3), `VK_MEDIA_NEXT_TRACK` (0xB0),
  `VK_MEDIA_PREV_TRACK` (0xB1), `VK_VOLUME_UP` (0xAF),
  `VK_VOLUME_DOWN` (0xAE), `VK_VOLUME_MUTE` (0xAD).
  These are handled system-wide by Windows and reach whatever app owns the
  media session (Spotify, browser, etc.).
- **Note:** `java.awt.Robot` cannot send media/volume keys (AWT `KeyEvent`
  defines no VK codes for them) — this corrects the original requirements
  doc, which suggested Robot for this. Robot remains the plan for
  trackpad/keyboard in later milestones.
- OS detection at startup (`System.getProperty("os.name")`); non-Windows
  gets `UnsupportedOsActuator` returning `Ack(error=UNSUPPORTED_OS)`.

**UI** (`desktopApp`): one plain Compose Desktop window with three states —
*Waiting* (hostname, LAN IP, port shown), *Pairing* (large PIN + countdown),
*Connected* (device name, Disconnect and Unpair buttons).

## 6. Android side

Two screens, state-driven navigation (no nav library for two screens):

- **ConnectScreen** — NSD-discovered desktops list, manual-IP field as
  fallback, and a "Reconnect to <name>" shortcut when a saved pairing
  exists. Server responding `AUTH_REQUIRED` (or first pairing) opens a
  PIN-entry dialog.
- **RemoteScreen** — media controls only (M1). Portrait: vertical stack,
  large centered play/pause, prev/next flanking, volume rocker + mute below.
  Landscape: horizontal split — transport left, volume right. Implemented as
  two separate `@Composable` layout functions selected by orientation.

MVVM: single `ConnectionViewModel` owning discovery, pairing, socket
lifecycle, and command sending; exposes `StateFlow<ConnectionUiState>`:

```kotlin
sealed interface ConnectionUiState {
    object Idle; object Discovering; data class Connecting(val target: String)
    data class Pairing(val attemptsLeft: Int); data class Connected(val desktopName: String)
    data class Error(val message: String)
}
```

Persistence: Preferences DataStore for `{host, port, desktopName, token}`.

## 7. Error handling

- Connection drop on RemoteScreen → "Reconnecting…" banner, exponential
  backoff (1s / 2s / 4s), then return to ConnectScreen with a message.
- `Ack` errors → snackbar with mapped message; raw exceptions/error codes
  never reach the UI.
- Desktop server survives client disconnects and returns to *Waiting*.

## 8. Testing

- **`commonTest`:** serialization round-trips for every message type;
  pairing state machine (correct PIN, wrong PIN ×3 lockout, timeout).
- **`jvmTest`:** server session/auth logic against a fake `MediaActuator`
  (valid token executes, bad token → `AUTH_REQUIRED`, second client → `BUSY`).
- Verification commands: `./gradlew :shared:jvmTest :shared:testAndroidHostTest`
  plus `./gradlew :androidApp:assembleDebug :desktopApp:build` for compile checks.
- End-to-end (phone + Windows PC over WiFi, and via `adb forward`) is
  manual for M1.
