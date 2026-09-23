# DeskBuddy Changelog

## [2026-09-23] — Adaptive Android UI (phones, foldables, tablets, desktop windows)

**Type:** Feature
**Files Changed:**
- `gradle/libs.versions.toml`, `build.gradle.kts`, `androidApp/build.gradle.kts`, `gradle.properties` — `material3-adaptive-navigation-suite` (1.11.0-alpha07), Compose Preview Screenshot Testing plugin + `screenshot-validation-api` 0.0.1-alpha16
- `androidApp/.../ui/WindowWidth.kt` — `isWideWindow()`: window size class ≥ medium width (600dp) replaces the portrait/landscape orientation checks
- `androidApp/.../ui/RemoteScreen.kt` — `NavigationSuiteScaffold` with `NavigationSuiteItem`s replaces the hand-rolled bar/rail switch; stateless `RemoteShell` extracted for previews; wide-but-short windows (phone landscape) get a rail instead of the library's default bar; top app bar hides on scroll down and returns on scroll up (`enterAlwaysScrollBehavior`)
- `androidApp/.../ui/AppsTab.kt` — shortcut grid `GridCells.Adaptive(104dp)`; all-apps rows span three tiles so wide windows show several rows side by side
- `androidApp/.../ui/ShareTab.kt` — one `LazyVerticalGrid(Adaptive(320dp))` replaces the two-branch column layout; action cards and inbox items flow into columns, headings span full width
- `androidApp/.../ui/ConnectScreen.kt` — `LazyColumn` → adaptive grid (discovered PCs flow in 340dp columns); content centred at 1040dp max on very wide windows
- `androidApp/.../ui/standby/StandbyShortcutsPage.kt` — `GridCells.Adaptive(112dp)`; `StandbyScreen.kt`, `StandbyMediaPage.kt`, `ui/media/MediaTab.kt` — use `isWideWindow()`
- `androidApp/.../ui/media/NowPlayingCard.kt` — optional own scroll when shown beside the controls (was clipped in phone landscape); `ui/FabClearance.kt` — bottom space so the last item of each tab scrolls clear of the Standby FAB (it covered the output-device button on phones)
- `androidApp/src/screenshotTest/.../FormFactorPreviews.kt`, `PreviewSamples.kt`, `AdaptiveScreenshots.kt` — `@PreviewTest` screenshots of the Media, Apps, Share and Connect screens on phone, phone landscape, foldable, tablet and desktop
- `androidApp/src/screenshotTestDebug/reference/...` — 20 reference images

**Summary:**
Makes the Android app adapt to window size rather than orientation, following the adaptive skill. Navigation moves to `NavigationSuiteScaffold` (bottom bar on compact phones, rail on wider windows), vertical lists become adaptive grids, and the connected screen's app bar hides while scrolling. Multi-pane list-detail/supporting-pane layouts were not added: the app has no list→detail screen pairs and does not use Navigation 3, which that step requires. The experimental `Grid` API (Step 4.2) was not used; it needs explicit opt-in. Screenshot references were recorded after the changes and reviewed visually for phone, phone landscape, tablet and desktop; the pre-change UI was not captured.

**Build Verified:** [x] Yes — `./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:lintDebug :androidApp:validateDebugScreenshotTest :desktopApp:build` passed (lint 0 errors; 20/20 screenshots match references).

## [2026-09-23] — Milestone 3: Reliable Discovery, Now Playing, Audio Output, Standby Mode

**Type:** Feature
**Files Changed:**
- `docs/superpowers/specs/2026-09-23-deskbuddy-m3-nowplaying-standby-discovery-design.md` — design spec (decisions D1–D4, assumptions A1–A8)
- `docs/superpowers/plans/2026-09-23-deskbuddy-m3.md` — task plan T1–T13
- `plan/pc-remote-control-app-requirements.md` — §2.1/§2.8 now-playing, audio output, standby, discovery; §3.1 platform rows
- `shared/src/commonMain/.../protocol/Messages.kt` — `PlaybackStatus`, `NowPlayingCommand`, `MediaArtworkCommand`, `AudioStateCommand`, `AudioDevice`, `MediaStateRequestCommand`, `MediaSeekCommand`, `SetVolumeCommand`, `SetAudioDeviceCommand`, `DesktopInfo`; `AppCatalog.icons`
- `shared/src/commonMain/.../protocol/WireCodec.kt` — `encodeInfo`/`decodeInfo`
- `shared/src/commonMain/.../media/PlaybackClock.kt` — position interpolation shared by both sides
- `shared/src/commonMain/.../discovery/SubnetHosts.kt` — /24-capped sweep candidates
- `shared/src/commonMain/.../ui/theme/DeskBuddyIcons.kt` — `Speaker`, `Headphones`, `Nightlight`, `ExpandMore`
- `shared/src/jvmMain/.../server/MediaSessionActuator.kt`, `AudioActuator.kt` — new actuator interfaces + `Unsupported*`
- `shared/src/jvmMain/.../server/Actuators.kt` — `mediaSession`, `audio`, `onStateRequested`
- `shared/src/jvmMain/.../server/ControlSession.kt` — dispatch for state request, seek, volume, output device; push-only commands rejected
- `shared/src/jvmMain/.../server/media/MediaHelperProtocol.kt`, `NowPlayingFilter.kt`, `WindowsMediaSessionActuator.kt` — PowerShell helper process wrapper, JSON-line protocol, 1.5 s drift filter, restart with backoff
- `shared/src/jvmMain/resources/deskbuddy/media-session.ps1` — Windows system media session helper (now playing, artwork, seek/transport)
- `shared/src/jvmMain/.../server/audio/CoreAudioIds.kt`, `ComObject.kt`, `WindowsAudioActuator.kt` — Core Audio via raw JNA COM vtables: render endpoints, master volume, mute, default-device switch (IPolicyConfig)
- `shared/src/jvmMain/.../server/media/MediaStateBroadcaster.kt` — diffs actuator state into `Push` commands; `resendAll()`
- `shared/src/jvmMain/.../server/AppIconProvider.kt`, `AppCatalogService.kt` — 64 px shell icons for shortcut apps only
- `shared/src/jvmMain/.../server/ActuatorFactory.kt` — `mediaSession()`, `audio()`, `iconFor`
- `shared/src/jvmMain/.../server/LanInterfaces.kt`, `DeskBuddyAdvertiser.kt` — one JmDNS per real LAN adapter (link-local/virtual skipped)
- `shared/src/jvmMain/.../server/ControlServer.kt` — `GET /info` route, `desktopName`
- `shared/src/androidMain/.../client/DesktopDiscovery.kt` — serialised resolves with retry, keyed by host
- `shared/src/androidMain/.../client/SubnetScanner.kt` — TCP probe + `/info` confirm across the phone's /24
- `androidApp/.../session/RemoteSession.kt` — merges mDNS + sweep; implements `MediaGateway`
- `androidApp/.../session/MediaGateway.kt` — controller-facing session interface (testable)
- `androidApp/.../media/MediaUiState.kt`, `MediaController.kt` — mirrors pushes, interpolates position, throttles/holds volume, seek + device switch
- `androidApp/.../apps/AppsUiState.kt`, `AppShortcutsController.kt`, `ui/AppIcon.kt`, `ui/AppsTab.kt` — decoded icons on shortcut tiles
- `androidApp/.../ui/media/MediaTab.kt`, `NowPlayingCard.kt`, `PlaybackControls.kt`, `VolumePanel.kt`, `OutputDeviceSheet.kt`, `TimeFormat.kt` — redesigned media tab (artwork/placeholder, wavy indicator while playing, expressive slider timeline, button-group transport with play/pause state, volume slider + mute, split-button output device + modal sheet)
- `androidApp/.../ui/standby/StandbyScreen.kt`, `StandbyShortcutsPage.kt`, `StandbyMediaPage.kt`, `KeepScreenOn.kt` — black always-on pager, clock, immersive, back/close exit
- `androidApp/.../ui/RemoteScreen.kt` — `MediumExtendedFloatingActionButton` "Standby"; media tab wiring
- `androidApp/.../ui/ConnectionViewModel.kt` — `SubnetScanner`, `MediaController`
- `androidApp/src/main/res/values/strings.xml` — new copy; removed `media_playback`, `media_play_pause`, `media_volume`
- `androidApp/build.gradle.kts`, `gradle/libs.versions.toml` — `core-ktx` (pinned `androidx-core` 1.18.0: 1.19.0 needs compileSdk 37), `kotlin-test-junit`, `junit`, `kotlinx-coroutines-test`
- `desktopApp/.../DesktopController.kt`, `ui/StatusPanel.kt`, `ui/ServerScreen.kt` — broadcaster wiring, helper start/stop, "Now playing: on/unavailable" chip
- Removed: `androidApp/.../ui/MediaTab.kt` (replaced by `ui/media/`)
- Tests: `WireCodecTest` (+3), `PlaybackClockTest`, `SubnetHostsTest` (commonTest); `ControlSessionTest` (+7), `MediaHelperProtocolTest`, `NowPlayingFilterTest`, `MediaStateBroadcasterTest`, `WindowsAudioActuatorTest` (real COM, Windows-only), `WindowsMediaSessionActuatorTest` (real helper, Windows-only), `AppIconProviderTest` (Windows-only), `AppCatalogServiceTest` (+2), `LanInterfacesTest`, `ControlServerIntegrationTest` (+1), `ActuatorFactoryTest` (+1), fakes in `TestActuators` (jvmTest); `MediaControllerTest` (7), `TimeFormatTest` (androidApp unit tests, new source set)

**Summary:**
Fixes the flaky PC discovery and adds a full "now playing" experience plus a standby remote. Discovery: the desktop no longer binds mDNS to whatever `getLocalHost()` returns (this PC has four link-local virtual adapters) but advertises on every real LAN adapter; the phone serialises and retries NSD resolves and, if nothing shows up within 3 s, sweeps its own /24 on the DeskBuddy port and confirms hits via the new `GET /info` — the same path manual IP entry uses. Now playing: a bundled PowerShell helper reads the Windows system media session (title, artist, album, app, artwork, status, position, duration, seekability) and streams JSON lines to the desktop, which pushes changes to the phone (position only on jumps > 1.5 s; the phone interpolates). Audio: output devices, master volume and mute come from Core Audio via JNA COM; the phone can switch the default device and set volume with a slider (throttled while dragging, 1 s hold against stale pushes). Shortcut tiles now carry the PC's shell icons. The media tab is rebuilt with M3 Expressive components, and a "Standby" FAB opens a black, always-on, immersive two-page remote (shortcuts / media). Deviations from the spec: no new `ErrorKind`s (the session cannot attribute an Ack to a command; the existing not-found snackbar is reused and the next push corrects the UI); `androidx-core` pinned to 1.18.0. Not verified: phone ↔ PC end-to-end on a device (no phone attached in this session); everything else (helper launch, COM device enumeration/volume, icon extraction, `/info`) ran for real on this Windows 11 PC.

**Review fix pass (same day, after a whole-branch code review):**
- `androidApp/.../media/MediaController.kt` — artwork that arrives before the now-playing push naming it is kept in a one-slot pending buffer and adopted when the state arrives (the helper sends artwork first, so every track change lost its artwork); mute now has the same 1 s hold as volume; base64 decoding runs on `Dispatchers.Default`
- `androidApp/.../ui/media/NowPlayingCard.kt`, `ui/AppIcon.kt` — bitmap decoding moved out of composition (`produceState` on `Dispatchers.Default`)
- `shared/src/jvmMain/.../server/media/MediaStateBroadcaster.kt`, `Actuators.kt`, `ControlSession.kt`, `desktopApp/.../DesktopController.kt` — `onAudioChanged` hook: after a volume or output-device command the device list is refreshed and pushed at once instead of at the next 5 s poll (the phone's device choice used to flip back for up to 5 s)
- `shared/src/jvmMain/.../server/media/NowPlayingFilter.kt`, `WindowsMediaSessionActuator.kt` — filter reset on helper exit so a restarted helper's first (unchanged) sample is published again instead of leaving "Nothing playing"
- `shared/src/commonMain/.../protocol/WireCodec.kt`, `shared/src/androidMain/.../client/DeskBuddyClient.kt` — `decodeOrNull`: an unknown message/command type from a newer desktop is dropped instead of tearing the connection down
- Tests added: `MediaControllerTest` (+2: artwork-before-state, mute hold), `NowPlayingFilterTest` (+1 reset), `MediaStateBroadcasterTest` (+1 `audioChanged`), `ControlSessionTest` (+1 hook), `WireCodecTest` (+1 `decodeOrNull`)
- Deferred (minor, not fixed): sweep results never re-scanned once found; sweep uses the active network rather than Wi-Fi specifically; possible double helper spawn if `refresh()` races a restart backoff; blocking COM polled on `Dispatchers.Default`; live-stream slider renders full; helper re-requests the session manager every 500 ms; `" • "`, `"--:--"`, `"head"` literals in Android Kotlin; non-interactive `AssistChip`; helper temp script re-extracted per launch; standby shortcuts page shows "no shortcuts" while loading; seek blocks the websocket handler for up to 2 s (single-user app).
- **Still required before merge:** the phone ↔ PC end-to-end pass (track skip with artwork, output-device switch, mute, helper kill/restart, discovery with Wi-Fi toggled). No phone was attached in this session.

**Build Verified:** [x] Yes — `./gradlew :shared:jvmTest :shared:testAndroidHostTest :shared:compileKotlinJs :shared:compileKotlinWasmJs :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:lintDebug :desktopApp:build` passed after the fix pass (139 JVM + 41 Android host + 11 app unit tests green; lint 0 errors, 13 pre-existing warnings).

## [2026-09-16] — UI Redesign: Material 3 Expressive (Android + Desktop)

**Type:** Refactor
**Files Changed:**
- `shared/src/commonMain/.../ui/theme/DeskBuddyTheme.kt` — `MaterialExpressiveTheme` with `MotionScheme.expressive()`, shared by both apps, light/dark
- `shared/src/commonMain/.../ui/theme/DeskBuddyColors.kt` — brand tonal palette (indigo primary, coral tertiary), M3 Expressive tone-30 container text
- `shared/src/commonMain/.../ui/theme/DeskBuddyIcons.kt` — 34 Material Symbols as `ImageVector`s (bundled core icon set lacks media/share glyphs)
- `shared/src/commonMain/.../ui/components/Components.kt` — `SectionTitle`, cookie-shaped `IconAvatar`/`LetterAvatar` (`MaterialShapes.Cookie9Sided`), Medium filled/tonal/outlined buttons with press shape-morph
- `androidApp/.../ui/DeskBuddyApp.kt` — wraps in `DeskBuddyTheme`; `ConnectScreen.kt` — `LargeFlexibleTopAppBar`, primary-container hero card for the saved PC, `LoadingIndicator` while discovering, contained cards; `PinDialog.kt` — six-cell PIN entry
- `androidApp/.../ui/RemoteScreen.kt` — `TopAppBar` with subtitle status + tonal power button; `ShortNavigationBar` (portrait) / `WideNavigationRail` (landscape); `MediaTab.kt` — large filled play/pause + medium tonal controls, mute as shape-morphing toggle; `AppsTab.kt` — cookie-avatar shortcut tiles, pill search, star toggles; `ShareTab.kt` — surface-tier cards, medium buttons, `ContainedLoadingIndicator` progress
- `androidApp/src/main/res/values/strings.xml` — new copy for the redesigned screens
- `desktopApp/build.gradle.kts` — Material3 from the version catalog (the plugin's bundled 1.9 hid the expressive APIs)
- `desktopApp/.../ui/ServerScreen.kt` — `WideNavigationRail` + pane header; `StatusPanel.kt` — hero card per state (loading indicator / display-size PIN with countdown / connected device), address chips; `SharePanel.kt`, `ShortcutsPanel.kt`, `AppPickerDialog.kt` — restyled with the shared components
- Removed: `androidApp/.../ui/Components.kt` (moved to shared)

**Summary:**
Rebuilt every screen on both platforms around Material 3 Expressive: one shared theme (expressive spring motion, extended shape scale, emphasized type styles, brand tonal palette), containment via surface-container tiers instead of flat lists, a single high-emphasis action per card, medium/large button sizes with shape morphing, `MaterialShapes` cookie badges for identity, the new expressive navigation (short bar, wide rail), flexible app bar on the connect screen, and the morphing `LoadingIndicator` for waiting/pairing/transfer states. No behaviour or protocol changes.

**Build Verified:** [x] Yes — `./gradlew :shared:jvmTest :shared:testAndroidHostTest :shared:compileKotlinJs :shared:compileKotlinWasmJs :androidApp:assembleDebug :androidApp:lintDebug :desktopApp:build` passed.

## [2026-09-16] — Milestone 2: Clipboard Sync, Text/File Sharing, App Shortcuts

**Type:** Feature
**Files Changed:**
- `docs/superpowers/specs/2026-09-16-deskbuddy-m2-sharing-apps-design.md` — design spec with stated assumptions (A1–A7)
- `docs/superpowers/plans/2026-09-16-deskbuddy-m2.md` — task plan T1–T8
- `plan/pc-remote-control-app-requirements.md` — clipboard sync / file transfer moved into scope; app-shortcut selection added
- `shared/src/commonMain/.../protocol/Messages.kt` — new `Push`, `AppCatalog`, `DesktopApp`; commands for clipboard, text share, file offer/chunk/complete/cancel, app catalog/launch/shortcut add/remove; `ErrorCode.NOT_FOUND`, `TRANSFER_FAILED`
- `shared/src/commonMain/.../protocol/Protocol.kt` — `FILE_CHUNK_BYTES`, `CLIPBOARD_POLL_MILLIS`, `RECEIVED_FOLDER_NAME`
- `shared/src/commonMain/.../transfer/FileChunkCodec.kt`, `TransferTracker.kt`, `FileNames.kt`, `TransferIds.kt` — pure transfer helpers shared by both receivers
- `shared/src/jvmMain/.../server/Actuators.kt`, `ShareActuator.kt`, `AwtShareActuator.kt`, `ClipboardWatcher.kt` — desktop clipboard + text inbox, echo-suppressed polling watcher
- `shared/src/jvmMain/.../server/FileReceiver.kt`, `FileSender.kt` — chunked file receive into `~/Downloads/DeskBuddy`, chunked send via `ControlServer.push`
- `shared/src/jvmMain/.../server/AppActuator.kt`, `WindowsAppActuator.kt`, `LinuxAppActuator.kt`, `ShortcutStore.kt`, `AppCatalogService.kt` — Start Menu `.lnk` / `.desktop` enumeration, `cmd /c start` / `sh -c` launch, persisted shortcuts, launch-id validation
- `shared/src/jvmMain/.../server/ActuatorFactory.kt` — now builds the `Actuators` bundle (`media()`, `apps()` helpers)
- `shared/src/jvmMain/.../server/ControlSession.kt` — dispatches all new commands; `execute()` returns the reply message (chunks are silent)
- `shared/src/jvmMain/.../server/ControlServer.kt` — single-writer outbound channel + `push()` for desktop-initiated messages
- `desktopApp/.../DesktopController.kt` — wires server, clipboard watcher, file sender, inbox, notices; pushes catalog changes to the phone
- `desktopApp/.../main.kt`, `ui/ServerScreen.kt`, `ui/StatusPanel.kt`, `ui/SharePanel.kt`, `ui/ShortcutsPanel.kt`, `ui/AppPickerDialog.kt`, `ui/FilePickers.kt` — two-column window: status + Share / App shortcuts tabs
- `androidApp/.../session/RemoteSession.kt` — session loop moved out of the ViewModel; exposes `incoming` pushes and `send(command)`
- `androidApp/.../share/ClipboardShareController.kt`, `FileTransferController.kt`, `ReceivedFileStore.kt`, `ShareUiState.kt`, `PendingShare.kt` — clipboard sync (foreground listener + pushes), text share, chunked file send/receive (MediaStore Downloads on API 29+), share-sheet queue
- `androidApp/.../apps/AppShortcutsController.kt`, `AppsUiState.kt` — catalog mirror, launch / pin / unpin, refresh on connect
- `androidApp/.../ui/ConnectionViewModel.kt` — thin facade over session + controllers; flushes pending shares once connected
- `androidApp/.../ui/RemoteScreen.kt`, `MediaTab.kt`, `AppsTab.kt`, `ShareTab.kt`, `DeskBuddyApp.kt`, `ConnectionUiState.kt` — three-tab remote (bottom bar portrait / rail landscape), new error kinds
- `androidApp/.../MainActivity.kt`, `AndroidManifest.xml` — `ACTION_SEND` intent filters (text + files), `singleTop`
- `androidApp/src/main/res/values/strings.xml` — all new UI strings
- Tests: `WireCodecTest` (extended), `FileChunkCodecTest`, `TransferTrackerTest`, `FileNamesTest` (commonTest); `TestActuators` fixture, `ControlSessionTest` (extended), `ControlServerIntegrationTest` (push case), `FileReceiverTest`, `ShortcutStoreTest`, `AppCatalogServiceTest`, `WindowsAppActuatorTest`, `LinuxAppActuatorTest`, `ActuatorFactoryTest` (jvmTest)

**Summary:**
Adds bidirectional clipboard sync (desktop pushes changes automatically; phone pushes on foreground changes or on demand and can pull), text and file sharing in both directions over the existing WebSocket (base64 chunks, 32 KiB, ordered and size-checked by a shared `TransferTracker`), Android share-sheet integration, and desktop-selected app shortcuts: the desktop enumerates installed programs (Windows Start Menu, Linux `.desktop`) or accepts any browsed file, persists the chosen shortcuts, and the phone shows them as a one-tap grid plus a searchable full list with pin/unpin. The server only launches ids present in its own catalog. Desktop-initiated traffic uses a new `Push` message routed through a single outbound channel in `ControlServer`, which also gives file sends natural backpressure.

**Build Verified:** [x] Yes — `./gradlew :shared:jvmTest :shared:testAndroidHostTest :shared:compileKotlinJs :shared:compileKotlinWasmJs :androidApp:assembleDebug :androidApp:lintDebug :desktopApp:build` passed (91 JVM + 28 Android host tests green; lint 0 errors, only pre-existing M1 warnings remain). Note: a stale `DeskBuddy\DeskBuddy` path cached from the project's old location broke `mergeLibDexDebug`; fixed by deleting `shared/build`, `androidApp/build`, `.gradle/configuration-cache` and building once with `--no-build-cache`.

## [2026-08-05] — Milestone 1: Connect + Media/Volume Control

**Type:** Feature
**Files Changed:**
- `gradle/libs.versions.toml` — added kotlinx-serialization, Ktor 3.3.1, JmDNS, JNA, DataStore versions/libraries and the kotlinx-serialization plugin
- `shared/build.gradle.kts` — serialization plugin; commonMain (serialization-json, coroutines), androidMain (Ktor client), jvmMain (Ktor server, JNA, JmDNS), jvmTest (Ktor client) dependencies
- `shared/src/commonMain/.../protocol/Protocol.kt` — port/path/service-type/pairing constants
- `shared/src/commonMain/.../protocol/Messages.kt` — sealed Message/Command hierarchy (PairRequest, PairAttempt, Envelope, PairSuccess, PairFailure, Ack; MediaCommand, MediaAction, ErrorCode)
- `shared/src/commonMain/.../protocol/WireCodec.kt` — JSON codec with `type` discriminator
- `shared/src/commonMain/.../pairing/PinGenerator.kt` — 6-digit PIN generator
- `shared/src/commonMain/.../pairing/PairingSession.kt` — pairing state machine (3 attempts, 60s timeout)
- `shared/src/jvmMain/.../server/TokenGenerator.kt` — SecureRandom 32-byte hex tokens
- `shared/src/jvmMain/.../server/PairedDeviceStore.kt` — paired devices persisted at `~/.deskbuddy/paired.json`
- `shared/src/jvmMain/.../server/MediaActuator.kt` — actuator interface + UnsupportedMediaActuator
- `shared/src/jvmMain/.../server/WindowsMediaActuator.kt` — JNA SendInput media/volume key taps
- `shared/src/jvmMain/.../server/ActuatorFactory.kt` — runtime OS branching
- `shared/src/jvmMain/.../server/ControlSession.kt` — per-connection message handler (pairing, auth, dispatch)
- `shared/src/jvmMain/.../server/ControlServer.kt` — Ktor CIO WebSocket server on port 8765, single-session policy, StateFlow<ServerState>
- `shared/src/jvmMain/.../server/ServerState.kt` — Waiting/Pairing/Connected
- `shared/src/jvmMain/.../server/DeskBuddyAdvertiser.kt` — JmDNS advertising of `_deskbuddy._tcp`
- `shared/src/androidMain/.../client/DeskBuddyClient.kt` — Ktor WebSocket client wrapper
- `shared/src/androidMain/.../client/DesktopDiscovery.kt` — NSD discovery as callbackFlow
- `desktopApp/.../main.kt`, `desktopApp/.../ui/ServerScreen.kt` — desktop window (waiting/pairing/connected)
- `desktopApp/build.gradle.kts` — added compose.material3
- `androidApp/.../prefs/PairedDesktopPrefs.kt` — DataStore persistence of paired desktop
- `androidApp/.../ui/ConnectionUiState.kt`, `ConnectionViewModel.kt` — MVVM state + session/reconnect loop
- `androidApp/.../ui/DeskBuddyApp.kt`, `ConnectScreen.kt`, `PinDialog.kt`, `RemoteScreen.kt` — screens (portrait + landscape)
- `androidApp/.../MainActivity.kt` — wires ViewModel + DeskBuddyApp
- `androidApp/src/main/AndroidManifest.xml` — INTERNET/ACCESS_NETWORK_STATE permissions, cleartext traffic
- `androidApp/src/main/res/values/strings.xml` — all UI strings
- `androidApp/build.gradle.kts` — compose/material3/lifecycle/datastore dependencies
- `local.properties` — set sdk.dir
- Tests: `WireCodecTest`, `PinGeneratorTest`, `PairingSessionTest` (commonTest); `PairedDeviceStoreTest`, `ActuatorFactoryTest`, `ControlSessionTest`, `ControlServerIntegrationTest` (jvmTest)

**Summary:**
Implemented DeskBuddy Milestone 1 end-to-end per `docs/superpowers/specs/2026-08-05-deskbuddy-m1-design.md`: shared KMP JSON-over-WebSocket protocol, PIN pairing with persistent tokens on both sides, mDNS discovery with manual-IP fallback, Windows media/volume actuation via JNA SendInput (java.awt.Robot cannot send media keys), desktop Compose window, and Android client with portrait/landscape remote UI. Unblocks all later milestones (track info, power/app control, trackpad/keyboard, Linux support) which extend the sealed Command hierarchy.

**Build Verified:** [x] Yes — `./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:assembleDebug :desktopApp:build` passed; `:shared:compileKotlinJs :shared:compileKotlinWasmJs` passed (web targets stay compatible)
