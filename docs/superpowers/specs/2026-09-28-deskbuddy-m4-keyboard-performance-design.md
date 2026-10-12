# DeskBuddy — Milestone 4 Design: Shared Keyboard + Performance Pass

**Date:** 2026-09-28
**Status:** Draft — awaiting user review
**Builds on:** M1–M3 specs in this folder
**Source request:**
1. "Check the whole code … make the performance better so optimize the code."
2. "Add one feature that I can use the keyboard of both my devices to input in both of the devices."

A three-part read-only review (shared/server, Android, desktop/build) produced tiered findings.
The user chose **Tier 3 (performance)** for this milestone; Tier 1 (security/crashes) and Tier 2
(leaks/reliability) are out of scope unless the user adds them later. The committed keystore and
release APK were already untracked in `0b95de8`.

---

## 1. Decisions confirmed with the user

| # | Decision |
|---|---|
| D1 | Keyboard sharing works **both ways, into any app**: the phone types into the PC's focused window; the PC types into the phone's focused text field. |
| D2 | PC → phone needs a **DeskBuddy Keyboard input method (IME)** on the phone, enabled once by the user. |
| D3 | The PC switches its keyboard to the phone with a **global hotkey (Ctrl+Alt+K)** that toggles typing mode. |
| D4 | While in typing mode the PC shows a **focus overlay**: a small always-on-top window that takes keyboard focus, so PC apps never see the keys. No low-level keyboard hook. |
| D5 | Phone → PC is **live typing plus a special-key row** (Esc, Tab, arrows, Del, Home/End, PgUp/PgDn, Ctrl/Alt/Shift/Win modifiers, combo chips). |
| D6 | Performance work = review Tier 3 (§5). It lands **before** the keyboard feature, because keystrokes are high-frequency messages. |

### Assumptions (stated, not confirmed)

| # | Assumption | Rationale |
|---|---|---|
| A1 | One paired phone at a time. If several are connected, PC keystrokes go to all of them (same as clipboard pushes today). | Matches the existing `ControlServer.push` broadcast. |
| A2 | Windows gets the full feature. Linux: phone → PC via `xdotool` when installed (else `UNSUPPORTED_OS`); PC → phone only from the tray menu (no global hotkey); Wayland not supported. | `RegisterHotKey`/`SendInput` are Win32; Linux has no portable equivalent. |
| A3 | The hotkey is fixed at Ctrl+Alt+K (not configurable). | YAGNI; the tray item is the fallback when the hotkey is taken. |
| A4 | The Win key is not forwarded PC → phone (Windows consumes Win combos before any window sees them); Android has no use for it. | Platform limit. |
| A5 | Phone → PC input cannot reach elevated (admin) windows, UAC prompts or the lock screen (Windows UIPI / secure desktop). | Platform limit, documented in UI copy. |
| A6 | No Hilt, no new architecture: new controllers are plain classes wired in `AppGraph`, like the existing ones. | Match existing code (M2 A6). |
| A7 | F-keys, a hardware keyboard attached to the phone, a trackpad and a configurable hotkey are out of scope. | YAGNI. |

---

## 2. Protocol (commonMain `protocol/Messages.kt`)

New section `// ---- Keyboard (M4)`:

```kotlin
@Serializable enum class Key {
    ENTER, BACKSPACE, DELETE, TAB, ESCAPE, SPACE,
    UP, DOWN, LEFT, RIGHT, HOME, END, PAGE_UP, PAGE_DOWN,
    A, B, …, Z, DIGIT_0, …, DIGIT_9,
}
@Serializable enum class KeyModifier { CTRL, ALT, SHIFT, META }

/** Committed Unicode text. Both directions. Silent on success. */
@Serializable @SerialName("key_text")
data class KeyTextCommand(val text: String) : Command()

/** A special key or combo. Both directions. Silent on success. */
@Serializable @SerialName("key_press")
data class KeyPressCommand(val key: Key, val modifiers: Set<KeyModifier> = emptySet()) : Command()

/** Desktop → phone (inside Push): the PC keyboard started/stopped typing to the phone. */
@Serializable @SerialName("keyboard_mode")
data class KeyboardModeCommand(val active: Boolean) : Command()
```

- Text travels as Unicode, not key codes, so any language, emoji or layout works and neither
  side needs the other's layout.
- Phone → PC keystrokes get **no Ack on success**; failures reply with an error Ack
  (`UNSUPPORTED_OS`, `INTERNAL`). The single WebSocket keeps order.
- `KeyTextCommand.text` longer than 4096 chars is rejected with `INTERNAL` (typing, not pasting —
  Share already covers bulk text).
- A newer peer's unknown `Key` value fails decoding; `WireCodec.decodeOrNull` drops the frame, so
  the connection survives.
- `KeyboardModeCommand` sent by a phone is rejected with `INTERNAL` (push-only, like `now_playing`).

---

## 3. Phone → PC

### 3.1 Desktop: `KeyboardActuator` (shared/jvmMain `server/keyboard/`)

```kotlin
interface KeyboardActuator {
    fun type(text: String)
    fun press(key: Key, modifiers: Set<KeyModifier>)
}
```

- `WindowsKeyboardActuator` — Win32 `SendInput` via JNA (same style as `WindowsMediaActuator`).
  - Text: one `KEYEVENTF_UNICODE` down/up pair per UTF-16 unit (surrogate pairs sent as two units),
    all in one `SendInput` call.
  - Keys: virtual-key codes; modifiers pressed first, key down/up, modifiers released in reverse,
    all in **one** `SendInput` batch so no other input interleaves. Arrows, Home/End, PgUp/PgDn,
    Delete carry `KEYEVENTF_EXTENDEDKEY`.
  - The batch is built by a pure `SendInputPlan.forText(text)` / `forKey(key, modifiers)` returning
    `(vk, scan, flags)` triples — unit-tested without calling Win32.
- `XdotoolKeyboardActuator` (Linux) — `xdotool type --clearmodifiers -- <text>` and
  `xdotool key <ctrl+shift+a>`; used only when `xdotool` is on `PATH`.
- `UnsupportedKeyboardActuator` — throws `UnsupportedOsException`.
- `ActuatorFactory.keyboard(osName)`; `Actuators.keyboard` (default Unsupported).
- `ControlSession.execute`: `KeyTextCommand` → `actuators.keyboard.type(...)` then `null`;
  `KeyPressCommand` → `press(...)` then `null`; `KeyboardModeCommand` → `INTERNAL`.

### 3.2 Phone: Keyboard tab (androidApp)

`RemoteTab.KEYBOARD` is added after SHARE (icon: `DeskBuddyIcons.Keyboard`). Files under
`ui/keyboard/`: `KeyboardTab.kt`, `SpecialKeyRow.kt`, `KeyboardSetupCard.kt`.

- **Typing area.** A single-line `BasicTextField` that requests focus when the tab opens (showing the
  user's normal keyboard). Its value always starts with one invisible sentinel (`​`).
  On every value change, `TextDiff.between(old, new)` (pure) returns
  `(backspaces: Int, inserted: String)` from the common prefix; the controller sends that many
  `KeyPressCommand(BACKSPACE)` then one `KeyTextCommand(inserted)`. Autocorrect, swipe and voice
  input become backspace-and-retype on the PC, which is what the user sees on the phone.
  Deleting the sentinel sends one Backspace and the sentinel is restored, so Backspace works on an
  empty field.
- **Enter** (IME action on the field) sends `KeyPressCommand(ENTER)` and resets the field to the
  sentinel. The field shows the current line as an echo.
- **Special-key row** (horizontally scrolling): Esc, Tab, ← ↑ ↓ →, Del, Home, End, PgUp, PgDn;
  modifier chips Ctrl / Alt / Shift / Win; combo chips Alt+Tab, Win+D, Ctrl+C, Ctrl+V, Ctrl+Z,
  Ctrl+A.
- **Modifiers** (`ModifierState`, pure), sticky-keys convention: first tap = one-shot (applies to
  the next key, then clears); second tap = locked; third tap = off. (Chosen over long-press, which
  conflicts with the chip's own click handling.) While any modifier is armed, a typed letter/digit is sent
  as `KeyPressCommand(letter, modifiers)` instead of text and removed from the field.
- Status line "Typing into <PC name>". The tab content is disabled with a hint when not connected.
- `KeyboardController` (`keyboard/KeyboardController.kt`, in `AppGraph`) owns sending and
  `ModifierState`; the tab is stateless apart from the text field value.

---

## 4. PC → phone

### 4.1 Desktop: hotkey, overlay, forwarding

- `WindowsGlobalHotkey` (shared/jvmMain `server/keyboard/`) — a dedicated daemon thread calls
  `RegisterHotKey(null, id, MOD_CONTROL|MOD_ALT|MOD_NOREPEAT, 'K')` and runs a `GetMessage` loop;
  each `WM_HOTKEY` invokes a callback. `start()` returns false when registration fails (hotkey
  owned by another app). `stop()` posts `WM_QUIT` and unregisters. Non-Windows: not created.
- `PhoneTypingSession` (pure state machine, shared/jvmMain): `Idle` / `Active`.
  `toggle()` / `enter()` → `Active` only if a phone is authenticated, else emits `NoPhone`.
  `exit(reason)` with reasons `HOTKEY`, `ESCAPE`, `FOCUS_LOST`, `PHONE_DISCONNECTED`, `QUIT`.
  Entering pushes `KeyboardModeCommand(true)`; every exit pushes `KeyboardModeCommand(false)`.
- `AwtKeyTranslator` (pure, shared/jvmMain): `java.awt.event.KeyEvent` → `Command?`.
  - `KEY_TYPED` with a printable char → `KeyTextCommand(char)`, **including when Ctrl+Alt is
    held** (Windows reports AltGr as Ctrl+Alt; a printable result means AltGr text).
  - `KEY_PRESSED` for Enter, Backspace, Delete, Tab, arrows, Home/End, PgUp/PgDn →
    `KeyPressCommand` with modifiers.
  - `KEY_PRESSED` for A–Z / 0–9 with Ctrl or Alt (and no printable `KEY_TYPED` following) →
    `KeyPressCommand(letter, modifiers)`. Implemented by deferring Ctrl/Alt letter presses until
    the matching `KEY_TYPED` either produces printable text (AltGr) or a control char (combo).
  - Esc is not forwarded — it exits typing mode.
- `KeyboardOverlay` (desktopApp `ui/KeyboardOverlay.kt`) — undecorated, always-on-top, rounded bar
  at top-centre of the primary screen: "⌨ Typing on <phone> — Ctrl+Alt+K or Esc to return", plus an
  echo of the last ~20 characters sent. While shown, an AWT `KeyEventDispatcher` registered on
  `KeyboardFocusManager` receives all key events (so composed/dead-key/AltGr characters arrive as
  `KEY_TYPED`), passes them to `AwtKeyTranslator`, and pushes results through `ControlServer.push`.
  Window deactivation → `exit(FOCUS_LOST)`. If the window cannot take focus after opening, it shows
  "Click here to type to your phone".
  Transient states: "No phone connected" (1.5 s, then closes) and "Phone disconnected" (same).
- `DesktopController` wires it: starts the hotkey, exposes `typingState`, and adds the tray item
  **"Type on phone (Ctrl+Alt+K)"**. `StatusPanel` shows a "Keyboard: Ctrl+Alt+K" chip, or
  "Keyboard: hotkey unavailable — use the tray menu".

### 4.2 Phone: DeskBuddy Keyboard IME (androidApp `ime/`)

- `RemoteKeyboardHub` (`keyboard/RemoteKeyboardHub.kt`, in `AppGraph`) collects
  `session.incoming` pushes: `pcTyping: StateFlow<Boolean>` from `KeyboardModeCommand`, and
  `input: SharedFlow<Command>` (buffer 256) for `KeyTextCommand` / `KeyPressCommand`. Resets
  `pcTyping` to false when the session disconnects.
- `DeskBuddyInputMethodService : InputMethodService`
  - Input view: a slim View-based bar (no Compose inside the IME) — "Typing from <PC name>" or
    "PC keyboard is off", plus a **Switch keyboard** button (`switchToPreviousInputMethod()` on
    API 28+, `InputMethodManager.switchToLastInputMethod(token)` below).
  - While shown it collects `hub.input` and applies it to `currentInputConnection`:
    `KeyTextCommand` → `commitText(text, 1)`; `KeyPressCommand` → `sendKeyEvent` down/up with the
    mapped `KeyEvent` code and meta state (`AndroidKeyMapping`, pure), so Ctrl+A/C/V/Z work in
    standard fields.
  - Enter: if the editor has an action (Send/Search/Go…) and not `IME_FLAG_NO_ENTER_ACTION` →
    `performEditorAction`; otherwise `KEYCODE_ENTER`.
  - Input is applied only while `session.isConnected`; with no input connection it is dropped.
  - When `pcTyping` goes true → false while this IME is active, it switches back to the previous
    keyboard automatically.
  - Nothing typed is logged or stored.
- Manifest: `<service android:permission="android.permission.BIND_INPUT_METHOD">` with the
  `android.view.InputMethod` intent filter and `res/xml/deskbuddy_method.xml`.
- **Switch prompt.** When `pcTyping` becomes true and DeskBuddy Keyboard is not the current IME
  (`Settings.Secure.DEFAULT_INPUT_METHOD`), `SessionNotifications` posts a high-priority
  notification on a new "PC keyboard" channel: "PC keyboard ready — tap to switch". Tapping opens
  `KeyboardPickerActivity` (translucent, no UI) which calls `showInputMethodPicker()` once it has
  window focus and finishes. The notification is cancelled when `pcTyping` ends.
- **Setup card** in the Keyboard tab while the IME is not enabled: explains PC → phone typing and
  opens `Settings.ACTION_INPUT_METHOD_SETTINGS`. Copy warns that Android will show its standard
  "this keyboard may collect all text you type" notice.

---

## 5. Performance pass (Tier 3)

Each item states the current cost and the change. No behaviour change beyond what is stated.

### Desktop / server

| # | Current cost | Change |
|---|---|---|
| P1 | `ControlSession.handleEnvelope` calls `isValidToken` then `deviceNameForToken`; each reads and parses `paired.json` — twice per message, including every file chunk (and every keystroke in M4). | `PairedDeviceStore` loads once into memory (lazy), serves lookups from a map, and updates memory + file on `add`/`removeAll`. `ControlSession` does one lookup (`deviceNameForToken`) per envelope. |
| P2 | Every `AppCatalogRequestCommand` calls `refresh()` → spawns PowerShell `Get-StartApps` (1–3 s). `find()` re-reads `shortcuts.json` on every launch. | Phone requests (`AppCatalogService.forPhone()`) reply at once with the cached catalog; when the last scan is older than 30 s a rescan runs in the background and any change reaches the phone through the existing catalog push. The phone's Refresh button therefore still picks up newly installed apps, one push later. The desktop Rescan button still forces `refresh()`. `ShortcutStore` caches its list in memory, updated on write. |
| P3 | Desktop UI thread blocks on Rescan (PowerShell, icons), add/remove shortcut (file IO + icon rendering), quit (JmDNS goodbyes, engine stop) and startup (JmDNS per adapter, first catalog scan before the tray exists). | These run on `Dispatchers.IO` via the controller scope. Quit hides window + tray first, then stops services on IO with an overall 3 s timeout, then exits. Startup shows the tray first; mDNS and the first catalog scan start in the background. |
| P4 | `ClipboardWatcher` reads the full clipboard text every second. | On Windows, read `User32.GetClipboardSequenceNumber()` each tick and fetch text only when it changed. Other OSes unchanged. |
| P5 | Each `volume()` / `isMuted()` creates an `MMDeviceEnumerator`, gets the default endpoint and activates `IAudioEndpointVolume` — ~6 COM object creations per second while polling. | `WindowsAudioActuator` caches the endpoint-volume object for the current default device on its COM thread; invalidated by `setDefaultDevice`, by the device-list poll seeing a new default, or on any COM error. New `AudioActuator.levels(): AudioLevels(volume, muted)` reads both in one COM-thread hop; the broadcaster uses it. |
| P6 | `FileSender` switches to `Dispatchers.IO` for every 32 KB read. | One `withContext(Dispatchers.IO)` around the read loop; pushes stay suspending. |
| P7 | Packaged desktop JVM has no heap cap (default up to ¼ of RAM, G1) for a tray-resident app. | `jvmArgs += listOf("-Xms32m", "-Xmx256m", "-XX:+UseSerialGC")` in `compose.desktop.application`. Run `suggestRuntimeModules` and pin `modules(...)` so the MSI runtime has everything. |
| P8 | Gradle builds modules serially. | `org.gradle.parallel=true`. |

### Android

| # | Current cost | Change |
|---|---|---|
| P9 | `MediaController` ticks every 500 ms for the process lifetime; while playing, each tick emits a new `MediaUiState` and recomposes Media tab, Now Playing, transport, volume and Standby. | Remove `positionMs` from `MediaUiState` and the tick loop. The timeline composable interpolates locally (`PlaybackClock.positionAt`) with a `produceState` keyed on `receivedAtMs`, base position and `isPlaying`, ticking only while it is on screen and playing. `seek()` updates `nowPlaying.positionMs` + `receivedAtMs` as today. The timeline (`PlaybackControls.kt`) is the only reader of the ticking value; `PcMediaSession` already passes `nowPlaying.positionMs` + its timestamp to Android, which interpolates itself, and `ConnectionService` already excludes the tick from its notification snapshot. |
| P10 | `PcMediaSession` re-sends metadata including the 512 px artwork bitmap over binder on every render (every now-playing push: play/pause, seek jumps), even when only playback state changed. | Skip `setMetadata` when title, artist, album, duration and the artwork bitmap (same cached instance per `artworkId`) are unchanged. The notification itself must still be re-posted with its large icon. |
| P11 | File progress emits for every 32 KB chunk; the Share tab re-runs its grid content each time. | Emit `TransferProgress` only when the integer percent changes (and on start/finish). |
| P12 | `AutoConnector` probes every 10 s forever on any network (cellular included, 1.5 s timeout per candidate); mDNS discovery runs even while connected. | Probe only when the default network has `TRANSPORT_WIFI` or `TRANSPORT_ETHERNET`. Interval backs off 10 s → 20 → 40 … capped at 5 min; reset on network change, `nudge()` or disconnect. Stop NSD discovery while connected; restart it when the session leaves Connected. |
| P13 | Connect screen's `sweepAllowed` follows composition (`DisposableEffect`), so the /24 sweep (254 connects every 15 s) keeps running after the app is backgrounded. | `LifecycleStartEffect` sets and clears the flag with the activity's started state. |
| P14 | `AutoStandbyManager` runs the orientation sensor for the whole charging period (overnight), connected or not. | Enable the sensor only while charging **and** a PC is connected. |
| P15 | Release build has no R8 (`isMinifyEnabled = false`): Compose, Ktor and serialization ship unoptimised. | `isMinifyEnabled = true`, `isShrinkResources = true`; keep rules for `com.saubh.deskbuddy.protocol.**` serializers and `-dontwarn` for Ktor's optional JVM dependencies. Verified by `:androidApp:assembleRelease`; the user smoke-tests pairing, media, share and keyboard on a device. |

### Deferred (Tier 4 / low value)

Binary WebSocket frames for file chunks, icon hash caching, event-driven audio/clipboard callbacks,
baseline profile, removing the template `webApp`, TLS, downsampled artwork decoding.

---

## 6. Error handling

- Keyboard on an unsupported OS: the first failing keystroke's `UNSUPPORTED_OS` Ack is shown once
  as a snackbar ("Typing isn't supported on this PC"); later failures in the same session are
  not re-shown.
- Hotkey registration failure: status chip + tray item remain; no error dialog.
- Phone disconnects during PC typing mode: overlay shows "Phone disconnected" and closes; the phone
  hub resets `pcTyping`, the IME switches back, the notification is cancelled.
- Desktop quit during typing mode: `exit(QUIT)` pushes `KeyboardModeCommand(false)` best-effort
  before shutdown.

---

## 7. Testing

- **commonTest:** `WireCodecTest` round-trips `KeyTextCommand`, `KeyPressCommand` (with modifiers),
  `KeyboardModeCommand`; an unknown `Key` value decodes to null.
- **jvmTest:**
  - `ControlSessionTest`: key commands are silent on success, `UNSUPPORTED_OS` when unsupported,
    `KeyboardModeCommand` from a phone → `INTERNAL`, oversized text → `INTERNAL`; token lookup
    reads the store file once (P1).
  - `SendInputPlanTest`: Unicode + surrogate pairs; modifier press/release order; extended-key
    flags.
  - `AwtKeyTranslatorTest`: printable, special keys, Ctrl+C combo, AltGr (`Ctrl+Alt` + printable →
    text), Esc not forwarded.
  - `PhoneTypingSessionTest`: toggle, no-phone, each exit reason pushes `active=false` once.
  - `PairedDeviceStoreTest`, `AppCatalogServiceTest` (+ cache TTL), `ClipboardWatcherTest`
    (sequence-number gating via a fake), `MediaStateBroadcasterTest` (uses `levels()`).
- **androidApp unit tests:** `TextDiffTest` (append, delete, autocorrect replace, sentinel
  backspace), `ModifierStateTest` (one-shot, lock, clear), `AndroidKeyMappingTest`,
  `MediaControllerTest` updated for P9, `AutoConnectBackoffTest` (pure backoff schedule).
- **Build gate:** `./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:testDebugUnitTest
  :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug :desktopApp:build`.
- **Note:** the repository contains no test sources besides `shared/src/jvmTest/.../TestFixtures.kt`
  (the M1–M3 tests and screenshot references listed in the changelog were never committed), so
  `:shared:jvmTest` currently fails with "no tests discovered". Every test named here is created
  new; screenshot validation is out of the gate until references exist again.
- **Manual (user):** phone → PC in Notepad, Chrome, Word; PC → phone in WhatsApp, Chrome address
  bar, Gmail; a non-English layout; an AltGr layout if available; release APK smoke test (P15).

---

## 8. File size and structure

All new files stay under 400 lines and follow the existing package layout
(`server/keyboard/`, `keyboard/`, `ime/`, `ui/keyboard/`). Every change is recorded in
`2026_Saubhagya_changelog.md`.
