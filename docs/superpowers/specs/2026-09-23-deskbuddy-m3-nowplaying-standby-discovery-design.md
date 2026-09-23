# DeskBuddy — Milestone 3 Design: Reliable Discovery, Now Playing, Audio Output, Standby Mode

**Date:** 2026-09-23
**Status:** Approved (spec + plan + implementation in one pass, per user choice)
**Builds on:** `2026-08-05-deskbuddy-m1-design.md`, `2026-09-16-deskbuddy-m2-sharing-apps-design.md`,
`2026-09-16-deskbuddy-expressive-ui-design.md`
**Source request:**
1. Auto-discovery of the PC sometimes fails and the user has to type the IP. Make it seamless.
2. Media tab: show the current track's artwork (default image when none), play/pause state, a
   seekable timeline, the list of audio output devices with the current one marked and the ability
   to switch, and a volume slider.
3. A FAB that turns the phone into a "standby" remote: black-background screen, page one is the app
   shortcuts (with app icons where possible), swipe to page two for media controls.
4. Full Material 3 Expressive treatment of the new UI.

---

## 1. Decisions confirmed with the user

| # | Decision |
|---|---|
| D1 | Discovery: advertise mDNS on every real LAN adapter, queue/retry resolves on the phone, and fall back to a TCP sweep of the phone's own /24 subnet when mDNS finds nothing. |
| D2 | Windows backend: a bundled PowerShell helper (long-lived child process) provides the system media session (now playing, artwork, seek). Audio output devices and master volume use Windows Core Audio through JNA COM. |
| D3 | One pass: spec → plan → implementation → build/tests → changelog, no intermediate approval stops. |
| D4 | Standby media page with nothing playing shows the placeholder art and "Nothing playing"; seek is disabled but transport, volume and output device stay usable. |

### Assumptions (stated, not confirmed)

| # | Assumption | Rationale |
|---|---|---|
| A1 | Now-playing, output devices and absolute volume are **Windows only**. Linux/macOS return `UNSUPPORTED_OS` for the new commands and push no media state; the phone shows the placeholder and hides the device picker when no `AudioState` has arrived. | Matches the existing Windows-only `MediaActuator`. |
| A2 | Switching output sets the Windows **default playback device** for the console and multimedia roles (not communications). The volume slider sets the **master volume of the current default device**. | What users mean by "change the output device". |
| A3 | Existing key-tap actions (play/pause, next, previous, volume up/down, mute) stay as they are. Seek, absolute volume and device switching are new commands. | Key taps work for every player, show the Windows OSD, and are already tested. |
| A4 | App icons are extracted **only for shortcut apps** (the tiles on the Apps tab and the standby grid). The full list keeps letter avatars. Icons are 64×64 PNG, base64 in the catalog, cached on both sides. | Requested scope; keeps the catalog small. |
| A5 | Artwork is pushed once per track change as a separate message (any image format the player provides), keyed by a content hash so the phone can ignore repeats. | Avoids resending 50 KB every second. |
| A6 | Position is pushed only when it jumps (seek, track change, play/pause); the phone interpolates position locally while playing. | Windows updates the timeline coarsely; a 1 Hz push would still need interpolation. |
| A7 | Standby keeps the screen on, hides system bars, uses pure black, and exits with the back gesture or a close button. It does not auto-enter and does not dim brightness. | Simplest useful behaviour; brightness is a user preference. |
| A8 | No Hilt (see M2 A6). Controllers stay plain classes wired in `ConnectionViewModel`. | Match existing code. |

---

## 2. Discovery (D1)

### 2.1 Desktop: advertise on every LAN interface

`DeskBuddyAdvertiser` no longer binds to `InetAddress.getLocalHost()`. It enumerates
`NetworkInterface.getNetworkInterfaces()`, keeps interfaces that are up, not loopback, not virtual,
and have a site-local IPv4 address (10/8, 172.16/12, 192.168/16), and creates one `JmDNS` instance
per address, each registering the same service name (the hostname). Link-local (169.254/16)
adapters, which this PC has four of, are skipped. If nothing qualifies it falls back to
`getLocalHost()` as today. Interface selection is a pure function (`LanInterfaces.select`) so it is
unit-testable with fake interface descriptions.

### 2.2 Desktop: `GET /info`

`ControlServer` adds a plain HTTP route `GET /info` returning `DesktopInfo(name, port)` encoded with
`WireCodec`'s JSON. The phone's subnet sweep uses it to confirm that an open port really is
DeskBuddy and to learn the PC's name. `DesktopInfo` lives in `protocol/` (commonMain).

### 2.3 Phone: resolve queue + retry

`DesktopDiscovery` serialises resolves through a single queue (Android rejects concurrent
`resolveService` calls with `FAILURE_ALREADY_ACTIVE`) and retries a failed resolve up to three times
with a 500 ms delay. Found desktops are keyed by host so two adapters advertising the same name show
as separate reachable addresses.

### 2.4 Phone: subnet sweep fallback

New `client/SubnetScanner` (androidMain):
- Reads the phone's IPv4 address and prefix length from `ConnectivityManager.getLinkProperties`.
- `SubnetHosts.candidates(ip, prefix)` (commonMain, pure) returns the host addresses of the /24
  containing the phone (a wider prefix is still capped at the phone's /24; 254 hosts max).
- For each candidate, a TCP connect to `Protocol.PORT` with a 400 ms timeout, at most 48 in
  parallel on `Dispatchers.IO`; on success a `GET /info` with a 1 s timeout via the existing Ktor
  client. Successes become `DiscoveredDesktop(name, host, port)`.

`RemoteSession.startDiscovery()` merges both sources: mDNS results as they arrive, plus a sweep that
starts 3 s after discovery began if nothing has been found, and repeats every 15 s while the
connect screen is showing and the list is still empty. The UI is unchanged except the searching
card's copy mentions scanning.

---

## 3. Protocol additions (`shared/commonMain/protocol/Messages.kt`)

```kotlin
// Desktop → Phone (inside Push)
@SerialName("now_playing") NowPlayingCommand(
    title: String?, artist: String?, album: String?, appName: String?,
    status: PlaybackStatus,          // PLAYING, PAUSED, STOPPED, NONE
    positionMs: Long, durationMs: Long,
    canSeek: Boolean, artworkId: String?,
)
@SerialName("media_artwork") MediaArtworkCommand(artworkId: String, mimeType: String, data: String /* base64 */)
@SerialName("audio_state")   AudioStateCommand(devices: List<AudioDevice>, volume: Int /* 0..100 */, muted: Boolean)
@Serializable AudioDevice(id: String, name: String, isDefault: Boolean)

// Phone → Desktop (inside Envelope)
@SerialName("media_state_get") MediaStateRequestCommand   // reply Ack; server then pushes now_playing, media_artwork, audio_state
@SerialName("media_seek")      MediaSeekCommand(positionMs: Long)
@SerialName("volume_set")      SetVolumeCommand(volume: Int)
@SerialName("audio_device_set") SetAudioDeviceCommand(id: String)

// Changed
AppCatalog(apps, shortcutIds, icons: Map<String, String> = emptyMap())   // base64 PNG, shortcut ids only
DesktopInfo(name: String, port: Int)                                       // GET /info body (not a Message)
```

`ErrorCode` is unchanged: unknown device id → `NOT_FOUND`; no media session for seek → `NOT_FOUND`;
non-Windows → `UNSUPPORTED_OS`.

Pure helpers (commonMain, tested):
- `media/PlaybackClock.positionAt(nowMs, receivedAtMs, positionMs, durationMs, playing)` — interpolated position, clamped to `[0, durationMs]`.
- `discovery/SubnetHosts.candidates(ip, prefixLength)`.

---

## 4. Desktop side (`shared/jvmMain` + `desktopApp`)

### 4.1 Actuator interfaces

```kotlin
interface MediaSessionActuator {            // now playing + session transport
    val state: StateFlow<MediaSessionState>  // title/artist/album/app/status/position/duration/canSeek/artworkId
    val artwork: StateFlow<Artwork?>         // (artworkId, mimeType, bytes)
    fun seek(positionMs: Long): Boolean      // false when no session / cannot seek
    fun refresh()                            // re-emit state + artwork
    fun start(); fun stop()
}
interface AudioActuator {
    fun devices(): List<AudioDevice>
    fun volume(): Int; fun setVolume(percent: Int)
    fun isMuted(): Boolean
    fun setDefaultDevice(id: String): Boolean  // false when id unknown
}
```

`Actuators` gains `mediaSession: MediaSessionActuator` and `audio: AudioActuator`.
`ActuatorFactory` returns Windows implementations on Windows and `Unsupported*` elsewhere
(`state` stays at `MediaSessionState.NONE`, `AudioActuator` methods throw `UnsupportedOsException`).

### 4.2 `WindowsMediaSessionActuator` (PowerShell helper)

- Script `shared/src/jvmMain/resources/deskbuddy/media-session.ps1` is copied to a temp file at
  start and run as `powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File <tmp>`.
- The script polls `GlobalSystemMediaTransportControlsSessionManager.GetCurrentSession()` every
  500 ms and writes one JSON line to stdout when anything changed:
  `{"type":"state", ...NowPlaying fields...}` and, when the thumbnail hash changes,
  `{"type":"artwork","artworkId":"<sha1>","mimeType":"image/jpeg","data":"<base64>"}`.
  With no session it emits `status:"NONE"`.
- It reads commands from stdin, one per line: `toggle`, `play`, `pause`, `next`, `prev`,
  `seek <ms>`, `refresh`, `quit`; it replies `{"type":"result","ok":true|false}` for `seek`.
- Kotlin side: `HelperEvent` (sealed, kotlinx.serialization) parses lines; a reader thread updates
  the flows; `seek` writes the command and waits up to 2 s for its result. The helper is restarted
  with backoff (1 s, 2 s, 4 s; then give up until `refresh`) if it exits.
- Because Windows updates the timeline coarsely, the Kotlin side republishes `state` to the flow
  only when a non-position field changed or the reported position drifts more than 1.5 s from the
  interpolated value (`PlaybackClock`), so the phone is not spammed. This filter
  (`NowPlayingFilter`) is pure and tested.

### 4.3 `WindowsAudioActuator` (JNA COM)

Direct vtable calls through `com.sun.jna.platform.win32.COM.Unknown` on a single dedicated thread
initialised with `CoInitializeEx(COINIT_MULTITHREADED)`:
- `IMMDeviceEnumerator` (`CLSID_MMDeviceEnumerator`): `EnumAudioEndpoints(eRender, DEVICE_STATE_ACTIVE)`,
  `GetDefaultAudioEndpoint(eRender, eMultimedia)`, `GetDevice(id)`.
- `IMMDevice`: `GetId`, `OpenPropertyStore(STGM_READ)` → `PKEY_Device_FriendlyName`; `Activate(IID_IAudioEndpointVolume)`.
- `IAudioEndpointVolume`: `Get/SetMasterVolumeLevelScalar`, `GetMute`.
- `IPolicyConfig` (`CLSID_PolicyConfigClient`): `SetDefaultEndpoint(id, eConsole)` and `(id, eMultimedia)`.

Every COM object is released after use. Failures throw and surface as `INTERNAL`. Verified
manually on the development PC (Windows 11) as part of implementation; no automated test drives COM.

### 4.4 `MediaStateBroadcaster` (jvmMain)

Owns polling of `AudioActuator` (volume/mute every 1 s, device list every 5 s), diffs against the
last pushed `AudioStateCommand`, and combines it with `MediaSessionActuator.state`/`artwork` into a
`Flow<Command>` of pushes. `DesktopController` collects it and calls `server.push(Push(cmd))`.
On `MediaStateRequestCommand` (phone connected / tab opened) `ControlSession` invokes
`Actuators.onStateRequested`, which `DesktopController` wires to `broadcaster.resendAll()`; that
re-emits the three current commands.

### 4.5 App icons

`AppIconProvider` (jvmMain): `iconPng(path): String?` uses
`FileSystemView.getFileSystemView().getSystemIcon(file, 64, 64)` (JDK 17+; desktop runs on 25),
paints it into an ARGB `BufferedImage`, encodes PNG, base64; results cached per path; `null` on any
failure or on non-Windows. `AppCatalogService` takes an `iconFor: (DesktopApp) -> String?`
(default `{ null }`) and fills `AppCatalog.icons` for shortcut ids only in `rebuild()`.

### 4.6 `ControlSession` dispatch

| Command | Effect | Reply |
|---|---|---|
| `MediaStateRequestCommand` | `broadcaster.resendAll()` | `OK` |
| `MediaSeekCommand` | `mediaSession.seek(ms)` | `OK` / `NOT_FOUND` |
| `SetVolumeCommand` | `audio.setVolume(v.coerceIn(0,100))` | `OK` |
| `SetAudioDeviceCommand` | `audio.setDefaultDevice(id)` | `OK` / `NOT_FOUND` |

`ControlSession` needs a `resendAll` hook: `Actuators` gains `val onStateRequested: () -> Unit`
set by `DesktopController` (kept as a plain callback to keep `ControlSession` testable).

### 4.7 Desktop UI

No new panes. `StatusPanel` shows a one-line "Media helper: running / unavailable" note under the
address chips so the user can tell when now-playing is not available.

---

## 5. Android side (`androidApp`)

### 5.1 `media/MediaController`

Plain class (`session`, `scope`, `clock: () -> Long`), one file. Exposes
`StateFlow<MediaUiState>`:

```kotlin
data class MediaUiState(
    val nowPlaying: NowPlayingCommand? = null,    // null until the first push
    val receivedAtMs: Long = 0L,
    val positionMs: Long = 0L,                    // interpolated (ticker every 500 ms while PLAYING)
    val artwork: ByteArray? = null,               // decoded lazily by the UI
    val artworkId: String? = null,
    val audio: AudioStateCommand? = null,         // null → hide device picker, slider disabled
)
```

- Collects `Push(NowPlayingCommand)`, `Push(MediaArtworkCommand)`, `Push(AudioStateCommand)` from
  `session.incoming`; drops artwork whose id does not match the current track.
- Sends `MediaStateRequestCommand` when the session becomes `Connected`.
- `seek(ms)`: optimistic position update + `MediaSeekCommand`.
- `setVolume(percent)`: throttled to one send per 100 ms while dragging and a final send on
  release; pushed volumes are ignored for 1 s after a local change so the slider does not snap back.
- `selectDevice(id)`: `SetAudioDeviceCommand`, optimistic `isDefault` flip.
- Existing `sendMedia(action)` stays in `ConnectionViewModel`; play/pause icon follows `status`.

### 5.2 `apps` changes

`AppsUiState` gains `icons: Map<String, ByteArray>` (decoded from base64 in the controller, off the
main thread). `ShortcutTile` shows the icon inside the cookie badge when present, letter avatar
otherwise. Decoding to `ImageBitmap` is `remember`ed per tile.

### 5.3 Media tab (redesigned, M3 Expressive)

Files: `ui/media/MediaTab.kt` (layout only), `NowPlayingCard.kt`, `PlaybackControls.kt`,
`VolumePanel.kt`, `OutputDeviceSheet.kt`. The same composables are reused by standby.

- **NowPlayingCard** — `extraLarge` card on `surfaceContainerHigh`; artwork (or placeholder:
  `MusicNote` on `primaryContainer` cookie badge) 160 dp with `largeIncreased` corners; title in
  `titleLargeEmphasized`, artist/album in `bodyMedium` `onSurfaceVariant`, source app as an
  `AssistChip`; a `LinearWavyProgressIndicator` under the artwork animates only while PLAYING.
- **Timeline** — expressive `Slider` (thick track, handle gap) with elapsed / total labels in
  `labelMedium`; disabled when `!canSeek` or `status == NONE`; the value is local while dragging and
  seeks on release.
- **Transport** — `ButtonGroup` with previous (medium tonal), play/pause (large filled, `Play` or
  `Pause` icon by status, shape morph on press), next (medium tonal).
- **VolumePanel** — `surfaceContainerLow` card: mute `FilledTonalIconToggleButton` (checked =
  `muted` from the PC, no longer local-only) + expressive `Slider` 0–100 with the percentage in
  `labelLargeEmphasized`; below it a `SplitButton`: leading segment shows the current output device
  name with a speaker icon, trailing chevron opens **OutputDeviceSheet**.
- **OutputDeviceSheet** — `ModalBottomSheet` listing devices as `ListItem`s with a `RadioButton`,
  current one selected; tapping selects and dismisses.
- Landscape: now-playing card left (weight 1.4), transport + volume right; portrait: stacked in a
  scrollable column.

### 5.4 Standby mode

- `RemoteScreen` gets an `ExtendedFloatingActionButton` ("Standby", `Nightlight` icon) via
  `Scaffold.floatingActionButton`; tapping sets `standby = true` (`rememberSaveable`).
- `ui/standby/StandbyScreen.kt`: full-screen `Surface(Color.Black)` wrapped in
  `DeskBuddyTheme(darkTheme = true)`; `BackHandler` and a top-right close `IconButton` exit;
  `DisposableEffect` adds `FLAG_KEEP_SCREEN_ON` and hides system bars with
  `WindowInsetsControllerCompat` (removes both on dispose); top-left shows the time
  (`DateFormat.getTimeFormat`, updated each minute) in `displaySmallEmphasized`.
- `HorizontalPager(2)` with two page dots at the bottom (`primary` for current, `outline` for the
  other):
  - `StandbyShortcutsPage.kt` — `LazyVerticalGrid` (3 columns portrait, 5 landscape) of shortcut
    tiles on `Color(0xFF141414)` with 64 dp icons and `labelLargeEmphasized` white labels; tap
    launches; empty state text when there are no shortcuts.
  - `StandbyMediaPage.kt` — artwork 240 dp (placeholder when none), title/artist, the same
    `Timeline`, `Transport`, `VolumePanel` composables (theme dark, containers `0xFF141414`); with
    `status == NONE` shows "Nothing playing", seek disabled, everything else active (D4).
- New dependency: `androidx.core:core-ktx` (already in the catalog) for `WindowCompat`.

### 5.5 Strings

All new copy in `strings.xml`: standby, nothing playing, output device, volume percent, scanning
copy, content descriptions for seek/volume/device/close/standby.

---

## 6. Error handling

- Helper process missing/crashing → `MediaSessionState.NONE` and "Media helper: unavailable" on the
  desktop status panel; the phone shows the placeholder; seek returns `NOT_FOUND` → snackbar
  "Nothing to seek on the PC right now" (`ErrorKind.MEDIA_UNAVAILABLE`).
- COM failure → `INTERNAL` → existing generic snackbar; the audio panel keeps its last state.
- Unknown device id → `NOT_FOUND` → `ErrorKind.DEVICE_NOT_FOUND` snackbar; the next `AudioState`
  push corrects the selection.
- Subnet sweep errors are swallowed per host; the sweep never throws into the UI.
- Non-Windows desktop: `UNSUPPORTED_OS` for the new commands (existing snackbar).

---

## 7. Testing

- **commonTest:** `WireCodecTest` round-trips for every new command and for `AppCatalog` with
  icons (and without, for backward compatibility); `PlaybackClockTest`; `SubnetHostsTest`.
- **jvmTest:** `ControlSessionTest` for the four new commands with `FakeMediaSessionActuator` /
  `FakeAudioActuator`; `NowPlayingFilterTest` (drift threshold, field changes);
  `MediaHelperProtocolTest` (JSON line parsing incl. unknown types); `LanInterfacesTest`
  (selection rules); `AppCatalogServiceTest` icons only for shortcuts; `ControlServerIntegrationTest`
  gains a `GET /info` case; `MediaStateBroadcasterTest` (diff → pushes, `resendAll`).
- **Manual (Windows 11 dev PC):** helper script standalone; JNA audio enumeration/volume/default
  switch via a small `main` during implementation; phone end-to-end for discovery, now playing,
  seek, volume, device switch, standby.
- Verification command set (same as M2):
  `./gradlew :shared:jvmTest :shared:testAndroidHostTest :shared:compileKotlinJs :shared:compileKotlinWasmJs :androidApp:assembleDebug :androidApp:lintDebug :desktopApp:build`.

---

## 8. Out of scope

Linux/macOS now-playing and audio; per-app volume mixer; artwork for the full app list; auto-entering
standby; brightness control; lock-screen/notification media controls on the phone.
