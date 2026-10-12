# KonneKt M5 — Standby Modes & Mac Media — Design

**Date:** 2026-10-12
**Status:** Approved in conversation; awaiting written-spec review
**Branch base:** `feature/m4-keyboard-performance` (working tree)

## 1. Goals

1. **Mac parity for media and sound.** Now playing (including browser/YouTube audio), play/pause/next/previous, volume, mute and output-device switching work when the PC is a Mac.
2. **Standby becomes a four-mode dock screen** that is usable fully in landscape:
   - **Clock** — full-screen animated clock with four swipeable faces.
   - **Development** — app keys that launch an app or bring it to front.
   - **Multimedia** — now playing, transport, output-device buttons, step volume with long-press repeat.
   - **Media + Apps** — Multimedia and Development side by side (50/50).
3. **Docking rules:** charging + landscape shows the Clock automatically, even with no PC connected; when a PC is connected, Media + Apps is the default.
4. Smooth, interruptible, spring-based motion throughout.

## 2. Non-goals

- Keyboard / type-on-phone changes (explicitly out of scope).
- Linux parity for the new desktop features (`ActivateAppCommand` returns `UNSUPPORTED_OS` on Linux, as today's window commands do).
- New clock faces beyond the four listed; user-customisable faces.
- Tablet/foldable-specific layouts beyond what landscape/portrait already gives.

## 3. Findings that drive the Mac work

- The last commit (`6148c6e`) wires media, media session, audio and power to `Unsupported*` actuators on every non-Windows OS. That is why "only opening apps works" on the Mac.
- A full Mac implementation exists in the working tree (29 untracked files: `Mac*Actuator`, `MacCoreAudio`, `mac-now-playing.js`, tests, and the `NSAppleEventsUsageDescription` plist key) but was never committed and never run on a Mac.
- Even with that code, `mac-now-playing.js` reads only Music and Spotify; macOS 15.4+ blocks the system MediaRemote API for third-party apps. The user plays audio in the browser, so now playing would still be empty.
- `ControlSession.handleEnvelope` converts every exception to `ErrorCode.INTERNAL` without logging it, so failures are invisible.

## 4. Part A — Mac media and sound

### A1. Commit the existing Mac implementation
The untracked Mac sources, resources and tests are committed as their own commit, unchanged except for fixes found while wiring A2–A4.

### A2. MediaRemote adapter for system-wide now playing
- Vendor [`ungive/mediaremote-adapter`](https://github.com/ungive/mediaremote-adapter) (BSD-3-Clause): its Perl script plus the compiled `MediaRemoteAdapter.framework`. It runs under `/usr/bin/perl`, which macOS still entitles to use MediaRemote.
- Packaging: shipped through Compose Desktop `appResourcesRootDir` under a `macos` subfolder, so only the `.dmg` carries it. The framework is built on a Mac (CMake) or taken from a tagged release; the version is pinned and the license is added to `desktopApp` third-party notices.
- New `server/media/MacMediaRemoteSessionActuator.kt` implementing `MediaSessionActuator`:
  - Starts `perl <script> <framework> stream` and reads JSON lines on a daemon thread (same restart-with-backoff shape as `MacMediaSessionActuator`: 1 s, 2 s, 4 s, max 3).
  - New pure parser `MediaRemoteSample.parse(line)` maps a line to `NowPlayingCommand` (title, artist, album, app bundle → display name, status, position, duration, canSeek) and optional artwork (mime + base64, capped at `MacArtworkFetcher.MAX_BYTES`). Partial updates (`diff` mode) are merged onto the last full sample.
  - Publishes through the existing `NowPlayingFilter`; artwork is published before the state that references it (same contract as Windows).
  - `seek(ms)` and `send(MediaAction)` (play/pause, next, previous) run the adapter's one-shot `seek` / `send` subcommands. The exact argument names and units come from the pinned adapter version's README and are fixed by unit tests.
- `ActuatorFactory.mediaSession(mac)` returns a `FallbackMediaSessionActuator(primary = MacMediaRemoteSessionActuator, fallback = MacMediaSessionActuator)`: if the adapter is missing or exits before its first valid line three times, it switches to the Music/Spotify JXA helper for the rest of the run.

### A3. Transport without Accessibility
`MacMediaActuator.playPause/next/previous` first ask the adapter (no permission needed). Only if the adapter is unavailable do they fall back to posting NX media keys (needs Accessibility). Volume and mute stay on Core Audio.

### A4. Error visibility
`ControlSession.handleEnvelope` logs every caught exception: command `SerialName`, exception class and message, through a small `ServerLog` (stderr + rolling file in the desktop app's data dir, 1 MB cap). No tokens or clipboard/text payloads are logged.

### A5. Mac verification (manual, on a Mac — cannot run on the Windows dev box)
Build `./gradlew :desktopApp:packageDmg`, install, then check: browser YouTube shows title/artwork/progress on the phone; play/pause/next from the phone; volume steps; mute; output device list and switching; seek. Permissions, if prompted: Automation (fallback helper only), Accessibility (fallback media keys only).

## 5. Part B — Standby modes

### B1. Domain

```kotlin
enum class StandbyMode { CLOCK, DEVELOPMENT, MULTIMEDIA, MEDIA_APPS }
```

`StandbyModePolicy` (pure Kotlin, `ui/standby/StandbyModePolicy.kt`) owns mode selection. State: `mode`, `userPicked: Boolean`.

| Event | Result |
|---|---|
| Enter standby, PC connected | `MEDIA_APPS`, `userPicked = false` |
| Enter standby, no PC | `CLOCK`, `userPicked = false` |
| PC connects, `mode == CLOCK && !userPicked` | `MEDIA_APPS` |
| PC disconnects, mode ≠ `CLOCK` | `CLOCK`, `userPicked = false` |
| User swipes / taps rail | that mode, `userPicked = true` |
| Exit standby | state discarded |

The vertical order is CLOCK, MEDIA_APPS, MULTIMEDIA, DEVELOPMENT, so the two defaults are neighbours.

### B2. Docking
- `DockPolicy.shouldStandby(charging, landscape, suppressed)` drops `pcConnected`.
- `AutoStandbyManager` runs the orientation listener whenever the phone is charging (was: charging and a PC connected). `setPcConnected` stays but now only feeds the mode policy; disconnecting no longer exits standby.
- `DockWakeJob` / `DockLauncher` behaviour is unchanged.

### B3. Where standby lives
- `StandbyRoute` moves from `ConnectedShell` to `KonnektApp` (root), so it renders while disconnected. `ConnectedShell` keeps its "open standby" entry point, which now navigates to the root standby.
- New `StandbyViewModel` (via `AppViewModelProvider`): combines connection state, the mode policy, now playing (`MediaController`), shortcuts (`AppShortcutsController`), running apps (`RunningAppsController`), and the persisted clock face. Exposes one `StandbyUiState`; the UI sends events (`SelectMode`, `SelectFace`, `Media(action)`, `VolumeStep(delta)`, `SelectDevice`, `ActivateApp`, `LockPc`).
- The clock face index is persisted in a small `StandbyPrefs` (SharedPreferences, matching `PairedDesktopPrefs`).

### B4. Layout

```
Landscape (primary)
┌────┬─────────────────────────────────────────────┐
│rail│  mode content (vertical pager, one mode/page)│
│ ◷  │                                             │
│ ▦  │                                             │
│ ♫  │                                             │
│ ⌨  ├─────────────────────────────────────────────┤
│    │  [🔒 Lock]  [ − ]   [ ⏯ ]   [ + ]            │  ← control bar (not on Clock)
└────┴─────────────────────────────────────────────┘
```

- **Mode rail:** left edge, 4 icons with labels for accessibility. Visible on entry, fades out after 3 s idle, returns on any touch. The current mode is marked with an indicator pill that slides between items.
- **Control bar:** Lock PC (power `LOCK`), Vol −, Play/Pause, Vol +. Vol ± use the same long-press repeater as Multimedia. Present in all modes except Clock.
- **Portrait:** the rail moves to the bottom edge; the Media + Apps halves stack top/bottom.
- **Not connected:** PC modes show a centred "Reconnecting to <PC>…" card with the auto-connect status instead of controls; the control bar is disabled (40 % alpha).
- Exit: back gesture, or a close button that fades in with the rail.

### B5. Clock
- Horizontal pager of four faces; the vertical pager only intercepts vertical drags (direction decided after 10 dp of travel).
- Shared `rememberClockTicker()` emits `LocalTime` aligned to second boundaries; faces that only show minutes ignore seconds.
- Burn-in protection: the whole face translates by up to ±8 dp on a slow random walk, once per minute. Brightness is left to the system.
- Faces, each its own file under `ui/standby/clock/`:
  1. **FlipClockFace:** two split-flap card pairs (HH, MM). On change, the top half folds down around the X axis (`graphicsLayer.rotationX`, camera distance set), spring damping 0.8 / response ~0.35 s, with a shadow gradient that darkens mid-fold.
  2. **BubbleMorphFace:** heavy rounded numerals; on change the outgoing digit squishes (scaleY 0.85 → 0) while the incoming one pops (scale 0 → 1.05 → 1, damping 0.6). Background is a slow drifting two-stop gradient (60 s cycle; static under reduced motion).
  3. **NeonOrbitFace:** Canvas analog face; hour and minute hands with glow (blurred duplicate stroke); a seconds "comet" orbits the rim with a fading 40° tail; small date at 6 o'clock.
  4. **DotMatrixFace:** 5×7 dot glyphs on a dim dot grid; on change, dots switch in a left-to-right wave (20 ms stagger per column); date and phone battery % underneath.

### B6. Development
- Grid of the pinned shortcuts (existing catalog and icons), keys at least 96 dp, adaptive columns.
- A running indicator (small dot) when `RunningAppsController` reports a window for that app. The running list refreshes on entering the mode and every 10 s while visible.
- Tap → `ActivateAppCommand(appId)` (B8). The key shows press feedback immediately and a brief success tick on `OK`.

### B7. Multimedia
- Left: artwork (crossfade on track change), title/artist, seekable progress (existing `PlaybackClock` interpolation), transport (previous / play-pause / next).
- Right: **volume** row `[−] 42 % [+]` plus mute toggle; **output devices** as a wrapping row of chips (current one filled); tap switches through `SetAudioDeviceCommand`.
- **Long-press repeater** (`ui/standby/RepeatingPress.kt`, logic in pure `RepeatSchedule`): a tap = one step of 2 %. Hold = first repeat after 400 ms, then every 150 ms, speeding up to every 60 ms after 1.5 s. Each step sends `SetVolumeCommand(target, final = false)`; release sends `final = true`. Optimistic local value, reconciled with the next `AudioStateCommand`. Light haptic per step, stronger at 0 % and 100 %.
- Empty state (nothing playing): app-agnostic "Nothing playing" with transport still enabled.

### B8. Media + Apps
A 50/50 `Row` (landscape) or `Column` (portrait): compact Multimedia (artwork, titles, transport, volume row; devices behind a chip that expands in place) and the Development grid.

### B9. New protocol command

```kotlin
/** Phone → Desktop. Bring the app's front-most window forward; launch it if it has none. */
@Serializable @SerialName("app_activate")
data class ActivateAppCommand(val appId: String) : Command()
```

- Desktop: `AppCatalogService.activate(appId)` resolves the catalog entry, then asks `WindowActuator.focusApp(app)`; if no window was focused it launches through the existing path. Returns `OK`, or `NOT_FOUND` for an unknown id.
  - **Windows:** a `.lnk` shortcut is resolved to its target `.exe` (cached). The front-most top-level window of that exe is restored if minimised (`ShowWindow SW_RESTORE`) and brought forward with `SetForegroundWindow`, preceded by a synthetic Alt tap so Windows' foreground lock allows it. Start-menu app ids (UWP, `shell:AppsFolder`) have no exe and fall through to launch, which activates single-instance UWP apps anyway.
  - **Mac:** `open <bundle>` already activates a running app, so `focusApp` returns false and launch does the work.
  - **Linux:** `focusApp` returns false; activation is a launch.
- **Capability flag instead of a timeout fallback.** Acks are not correlated to commands, and an older desktop answers an unknown command with a generic `INTERNAL` ack, which would surface as an error snackbar. So `AppCatalog` gains `canActivate: Boolean = false`, which new desktops set to true. The phone sends `ActivateAppCommand` only when it is true and `LaunchAppCommand` otherwise.
- **Running indicator:** `RunningApp` gains `shortcutId: String? = null`. The desktop fills it when a running app matches a pinned shortcut: by resolved exe path on Windows, by app name on Mac. Older desktops leave it null, so no dots are shown.

## 6. Motion and feedback (applies everywhere)

| Element | Spec |
|---|---|
| Mode pager settle | spring, damping 1.0, stiffness ≈ `StiffnessMediumLow` (~0.35 s response); release velocity handed to the fling |
| Rail indicator | spring, damping 0.8 |
| Press feedback | scale 0.96 on press **down**, spring back on release; never waits for click |
| Mode change | light haptic (`CONTEXT_CLICK`) on settle |
| Artwork / track change | 250 ms crossfade |
| Rail show/hide | alpha + 8 dp slide, 200 ms |
| Reduced motion | When `Settings.Global.ANIMATOR_DURATION_SCALE == 0`: pagers snap with crossfade, faces swap digits without flip/morph/wave, gradient drift and comet tail off |

All animated values are interruptible (Compose `Animatable`/pager state); no input is locked during a transition.

## 7. Error handling

- PC commands go through existing controllers; failures surface as the existing snackbar messages (no raw exceptions).
- Volume/device commands that fail revert the optimistic value on the next `AudioStateCommand`.
- The desktop logs all actuator failures (A4).

## 8. Testing

**JVM (shared):** `MediaRemoteSampleTest` (full line, diff merge, artwork cap, malformed lines), `FallbackMediaSessionActuatorTest`, `ControlSessionTest` cases for `ActivateAppCommand` (OK / launch fallback / NOT_FOUND / UNSUPPORTED_OS) and for logging on exception, `WireCodecTest` round-trip for `app_activate`.

**Android unit:** `StandbyModePolicyTest` (every row of the B1 table), `DockPolicyTest` updated, `RepeatScheduleTest` (tap, hold timings, acceleration, release), `StandbyViewModelTest` (connect/disconnect transitions, activate fallback after timeout).

**Screenshot tests:** each mode in landscape and portrait, connected and disconnected; each clock face at a fixed time.

**Manual:** the Mac checklist in A5; on the phone, dock with and without a PC, then connect/disconnect while docked.

## 9. File plan (all files < 400 lines)

**shared/jvmMain:** `server/media/MacMediaRemoteSessionActuator.kt`, `server/media/MediaRemoteSample.kt`, `server/media/FallbackMediaSessionActuator.kt`, `server/ServerLog.kt`; edits to `ActuatorFactory`, `MacMediaActuator`, `ControlSession`, `AppCatalogService`, `openapps/WindowActuator` + Windows/Mac impls.
**shared/commonMain:** `ActivateAppCommand` in `Messages.kt`.
**desktopApp:** `resources/macos/` adapter files; `build.gradle.kts` (`appResourcesRootDir`).
**androidApp:** `ui/standby/` — `StandbyRoute.kt` (host), `StandbyViewModel.kt`, `StandbyUiState.kt`, `StandbyModePolicy.kt`, `StandbyPrefs.kt`, `ModeRail.kt`, `StandbyControlBar.kt`, `RepeatingPress.kt`, `RepeatSchedule.kt`, `DevelopmentMode.kt`, `MultimediaMode.kt`, `MediaAppsMode.kt`, `OutputDeviceChips.kt`, `VolumeStepper.kt`, `DisconnectedCard.kt`, `clock/ClockMode.kt`, `clock/ClockTicker.kt`, `clock/FlipClockFace.kt`, `clock/BubbleMorphFace.kt`, `clock/NeonOrbitFace.kt`, `clock/DotMatrixFace.kt`; edits to `KonnektApp`, `ConnectedShell`, `DockPolicy`, `AutoStandbyManager`, `AppShortcutsController` (activate + fallback), `strings.xml`. The old `StandbyScreen.kt`, `StandbyMediaPage.kt` and `StandbyShortcutsPage.kt` are replaced.

## 10. Risks

- **The MediaRemote adapter relies on a workaround Apple may close.** The fallback to the Music/Spotify helper keeps basic support.
- **Windows foreground-lock rules:** `AttachThreadInput` usually works; if focus is refused, the taskbar button flashes instead. That's acceptable, and the key still reports OK.
- **Orientation listener while charging without a PC** costs a little battery, but only while plugged in.
- **Mac code is untested on hardware**, so A5 has to pass before Part A counts as done.

## 11. Delivery order

1. A1 commit, then A4 logging (small and unblocks diagnosis).
2. A2/A3 adapter and fallback.
3. B9 protocol and desktop `activate`.
4. B1–B3 domain, docking and root standby.
5. B4–B8 screens, then the clock faces.
6. Screenshot tests, then the changelog entry.
