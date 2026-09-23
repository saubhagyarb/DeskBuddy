# DeskBuddy Milestone 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make PC discovery seamless, show and control what is playing on the PC (artwork, play/pause, seek, volume slider, output device), add a black "standby" remote with app-icon shortcuts and media controls, all in Material 3 Expressive.

**Architecture:** The desktop gains two actuators (a PowerShell-backed `MediaSessionActuator` for the Windows system media session and a JNA/COM `AudioActuator` for output devices and master volume) and a `MediaStateBroadcaster` that diffs their state into `Push` commands. The phone gains a `MediaController` mirroring those pushes with local position interpolation, a `SubnetScanner` fallback for discovery, and new `ui/media` + `ui/standby` composables. Discovery on the desktop advertises on every LAN adapter and serves `GET /info`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform + Material 3 1.11.0-alpha07 (expressive APIs), Ktor 3 (WebSocket + one HTTP route), kotlinx.serialization, JmDNS, JNA 5.17 (COM), Windows PowerShell 5.1 (bundled script), Android NSD.

**Spec:** `docs/superpowers/specs/2026-09-23-deskbuddy-m3-nowplaying-standby-discovery-design.md`

## Global Constraints

- Files stay under 400 lines; one class per file; no files in the root package.
- No hardcoded UI strings in Kotlin on Android: everything in `androidApp/src/main/res/values/strings.xml`.
- No `!!` without justification; no raw exception text in the UI.
- Now-playing / audio: **Windows only**; other OSes answer `UNSUPPORTED_OS` (spec A1).
- Existing key-tap `MediaAction`s are unchanged (spec A3).
- Icons only for shortcut apps, 64×64 PNG base64 (spec A4).
- Artwork pushed once per `artworkId` change (A5); position pushed only on jumps > 1.5 s, phone interpolates (A6).
- Standby: pure black, keeps screen on, hides system bars, exits via back or close (A7).
- Every task ends with the changelog entry `2026_Saubhagya_changelog.md` updated (one grouped M3 entry, written in the last task).
- Verification set: `./gradlew :shared:jvmTest :shared:testAndroidHostTest :shared:compileKotlinJs :shared:compileKotlinWasmJs :androidApp:assembleDebug :androidApp:lintDebug :desktopApp:build`.
- Material 3 API names verified against the pinned jar: `Slider(value, onValueChange, modifier, enabled, valueRange, steps, onValueChangeFinished, colors, interactionSource)`, `SplitButtonLayout(leadingButton, trailingButton, modifier, spacing)`, `SplitButtonDefaults.TonalLeadingButton(onClick, ...)`, `SplitButtonDefaults.TonalTrailingButton(checked, onCheckedChange, ...)`, `ButtonGroup(modifier, expandedRatio, horizontalArrangement, content: ButtonGroupScope.() -> Unit)` with `Modifier.animateWidth(interactionSource)`, `LinearWavyProgressIndicator(modifier, ...)` (indeterminate), `MediumExtendedFloatingActionButton(onClick, modifier, shape, containerColor, contentColor, elevation, interactionSource, content)`, `ModalBottomSheet(onDismissRequest, modifier, sheetState, ..., content)`.

## Review Focus

1. **A `NowPlayingCommand` whose `durationMs` is 0** (live streams, some browsers): the timeline must render disabled with "--:--" labels and interpolation must not clamp position to 0. Test in Task 1 (`PlaybackClockTest`) and Task 11 (labels).
2. **Volume push arriving during a slider drag**: the slider must not snap back to the PC's stale value. Task 9 test `setVolume_thenAudioPushWithinHold_keepsLocalVolume`.
3. **Artwork push for a previous track** (helper race after a fast skip): must be ignored when `artworkId` differs from the current `nowPlaying.artworkId`. Task 9 test.
4. **Phone on a /16 network**: sweep must be capped at 254 hosts, never thousands. Task 1 `SubnetHostsTest`.
5. **Helper process missing (PowerShell blocked / non-Windows)**: `seek` returns `NOT_FOUND`, never throws into the session; `available` is false. Task 2 test with `UnsupportedMediaSessionActuator`, Task 3 restart logic capped at 3.

---

### Task 1: Protocol additions + pure helpers (commonMain)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/saubh/deskbuddy/protocol/Messages.kt`
- Modify: `shared/src/commonMain/kotlin/com/saubh/deskbuddy/protocol/WireCodec.kt`
- Create: `shared/src/commonMain/kotlin/com/saubh/deskbuddy/media/PlaybackClock.kt`
- Create: `shared/src/commonMain/kotlin/com/saubh/deskbuddy/discovery/SubnetHosts.kt`
- Test: `shared/src/commonTest/kotlin/com/saubh/deskbuddy/protocol/WireCodecTest.kt` (extend)
- Test: `shared/src/commonTest/kotlin/com/saubh/deskbuddy/media/PlaybackClockTest.kt`
- Test: `shared/src/commonTest/kotlin/com/saubh/deskbuddy/discovery/SubnetHostsTest.kt`

**Interfaces:**
- Produces: `PlaybackStatus`, `NowPlayingCommand` (with `NowPlayingCommand.NONE`), `MediaArtworkCommand`, `AudioStateCommand`, `AudioDevice`, `MediaStateRequestCommand`, `MediaSeekCommand`, `SetVolumeCommand`, `SetAudioDeviceCommand`, `DesktopInfo`, `AppCatalog.icons`, `WireCodec.encodeInfo/decodeInfo`, `PlaybackClock.positionAt(...)`, `SubnetHosts.candidates(ip, prefixLength)`.

- [ ] **Step 1: Write the failing tests**

Append to `WireCodecTest.kt`:

```kotlin
    @Test
    fun `encode decode givenMediaMessages roundTrips`() {
        val messages: List<Message> = listOf(
            Push(NowPlayingCommand("Song", "Artist", "Album", "Spotify", PlaybackStatus.PLAYING, 12_000, 240_000, true, "abc")),
            Push(NowPlayingCommand.NONE),
            Push(MediaArtworkCommand("abc", "image/jpeg", "AAAA")),
            Push(AudioStateCommand(listOf(AudioDevice("{id}", "Speakers", true)), 42, false)),
            Envelope("t", MediaStateRequestCommand),
            Envelope("t", MediaSeekCommand(30_000)),
            Envelope("t", SetVolumeCommand(55)),
            Envelope("t", SetAudioDeviceCommand("{id}")),
            AppCatalog(listOf(DesktopApp("a", "A")), listOf("a"), icons = mapOf("a" to "iVBOR")),
        )
        for (m in messages) assertEquals(m, WireCodec.decode(WireCodec.encode(m)))
    }

    @Test
    fun `decode givenAppCatalogWithoutIcons defaultsToEmptyMap`() {
        val decoded = WireCodec.decode("""{"type":"app_catalog","apps":[],"shortcutIds":[]}""")
        assertEquals(AppCatalog(emptyList(), emptyList()), decoded)
    }

    @Test
    fun `encodeInfo decodeInfo roundTrips`() {
        val info = DesktopInfo("MY-PC", 8765)
        assertEquals(info, WireCodec.decodeInfo(WireCodec.encodeInfo(info)))
    }
```

`PlaybackClockTest.kt`:

```kotlin
package com.saubh.deskbuddy.media

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackClockTest {
    @Test
    fun `positionAt givenPlaying addsElapsedTime`() {
        assertEquals(15_000, PlaybackClock.positionAt(nowMs = 5_000, receivedAtMs = 0, positionMs = 10_000, durationMs = 60_000, playing = true))
    }

    @Test
    fun `positionAt givenPaused returnsReportedPosition`() {
        assertEquals(10_000, PlaybackClock.positionAt(5_000, 0, 10_000, 60_000, playing = false))
    }

    @Test
    fun `positionAt givenPastEnd clampsToDuration`() {
        assertEquals(60_000, PlaybackClock.positionAt(100_000, 0, 10_000, 60_000, playing = true))
    }

    @Test
    fun `positionAt givenZeroDuration doesNotClamp`() {
        assertEquals(15_000, PlaybackClock.positionAt(5_000, 0, 10_000, 0, playing = true))
    }

    @Test
    fun `positionAt givenClockWentBackwards neverGoesBelowReported`() {
        assertEquals(10_000, PlaybackClock.positionAt(-5_000, 0, 10_000, 60_000, playing = true))
    }
}
```

`SubnetHostsTest.kt`:

```kotlin
package com.saubh.deskbuddy.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubnetHostsTest {
    @Test
    fun `candidates givenSlash24 returns253HostsWithoutSelf`() {
        val hosts = SubnetHosts.candidates("192.168.1.20", 24)
        assertEquals(253, hosts.size)
        assertFalse("192.168.1.20" in hosts)
        assertTrue("192.168.1.1" in hosts && "192.168.1.254" in hosts)
    }

    @Test
    fun `candidates givenSlash16 isCappedToOwnSlash24`() {
        val hosts = SubnetHosts.candidates("10.0.5.9", 16)
        assertEquals(253, hosts.size)
        assertTrue(hosts.all { it.startsWith("10.0.5.") })
    }

    @Test
    fun `candidates givenSlash30 returnsOnlyOtherUsableHost`() {
        assertEquals(listOf("192.168.1.2"), SubnetHosts.candidates("192.168.1.1", 30))
    }

    @Test
    fun `candidates givenInvalidInput returnsEmpty`() {
        assertEquals(emptyList(), SubnetHosts.candidates("nope", 24))
        assertEquals(emptyList(), SubnetHosts.candidates("192.168.1.1", 0))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.protocol.WireCodecTest" --tests "com.saubh.deskbuddy.media.PlaybackClockTest" --tests "com.saubh.deskbuddy.discovery.SubnetHostsTest"`
Expected: compilation failure (unresolved `NowPlayingCommand`, `PlaybackClock`, `SubnetHosts`).

- [ ] **Step 3: Implement**

Append to `Messages.kt` (after `RemoveShortcutCommand`):

```kotlin
// ---- Media session / audio (M3) ----------------------------------------

@Serializable
enum class PlaybackStatus { PLAYING, PAUSED, STOPPED, NONE }

/** Desktop → Phone (inside [Push]). Position is as of send time; the phone interpolates. */
@Serializable
@SerialName("now_playing")
data class NowPlayingCommand(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val appName: String? = null,
    val status: PlaybackStatus = PlaybackStatus.NONE,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val canSeek: Boolean = false,
    val artworkId: String? = null,
) : Command() {
    companion object { val NONE = NowPlayingCommand() }
}

/** Desktop → Phone. [data] is base64 of the image bytes in [mimeType]. */
@Serializable
@SerialName("media_artwork")
data class MediaArtworkCommand(val artworkId: String, val mimeType: String, val data: String) : Command()

@Serializable
@SerialName("audio_state")
data class AudioStateCommand(val devices: List<AudioDevice>, val volume: Int, val muted: Boolean) : Command()

@Serializable
data class AudioDevice(val id: String, val name: String, val isDefault: Boolean)

/** Phone → Desktop. Reply is Ack; the desktop then pushes now_playing, media_artwork and audio_state. */
@Serializable
@SerialName("media_state_get")
data object MediaStateRequestCommand : Command()

@Serializable
@SerialName("media_seek")
data class MediaSeekCommand(val positionMs: Long) : Command()

/** [volume] is 0..100. */
@Serializable
@SerialName("volume_set")
data class SetVolumeCommand(val volume: Int) : Command()

@Serializable
@SerialName("audio_device_set")
data class SetAudioDeviceCommand(val id: String) : Command()

/** Body of `GET /info`; lets the phone verify a swept host is DeskBuddy. Not a [Message]. */
@Serializable
data class DesktopInfo(val name: String, val port: Int)
```

Change `AppCatalog`:

```kotlin
@Serializable
@SerialName("app_catalog")
data class AppCatalog(
    val apps: List<DesktopApp>,
    val shortcutIds: List<String>,
    /** Base64 PNG per shortcut id (never for non-shortcut apps). */
    val icons: Map<String, String> = emptyMap(),
) : Message()
```

`WireCodec.kt` add:

```kotlin
    fun encodeInfo(info: DesktopInfo): String = json.encodeToString(DesktopInfo.serializer(), info)

    fun decodeInfo(text: String): DesktopInfo = json.decodeFromString(DesktopInfo.serializer(), text)
```

`PlaybackClock.kt`:

```kotlin
package com.saubh.deskbuddy.media

/** Pure position interpolation shared by the desktop's change filter and the phone's ticker. */
object PlaybackClock {
    /**
     * Position at [nowMs] given a sample of [positionMs] taken at [receivedAtMs].
     * Clamped to `[0, durationMs]`; a zero/unknown duration only clamps below.
     */
    fun positionAt(nowMs: Long, receivedAtMs: Long, positionMs: Long, durationMs: Long, playing: Boolean): Long {
        val elapsed = if (playing) (nowMs - receivedAtMs).coerceAtLeast(0) else 0
        val raw = positionMs + elapsed
        val upper = if (durationMs > 0) durationMs else Long.MAX_VALUE
        return raw.coerceIn(0, upper)
    }
}
```

`SubnetHosts.kt`:

```kotlin
package com.saubh.deskbuddy.discovery

/** Host addresses worth probing for a DeskBuddy desktop, given the phone's own address. */
object SubnetHosts {
    private const val MAX_PREFIX_FOR_FULL_RANGE = 24

    /** Other hosts in the phone's subnet, capped to its /24 when the network is wider. Empty on bad input. */
    fun candidates(ip: String, prefixLength: Int): List<String> {
        val parts = ip.split('.').map { it.toIntOrNull() ?: return emptyList() }
        if (parts.size != 4 || parts.any { it !in 0..255 } || prefixLength !in 1..32) return emptyList()
        val self = parts.fold(0L) { acc, p -> (acc shl 8) or p.toLong() }
        val prefix = maxOf(prefixLength, MAX_PREFIX_FOR_FULL_RANGE)
        val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val network = self and mask
        val broadcast = network or (mask.inv() and 0xFFFFFFFFL)
        return ((network + 1) until broadcast).filter { it != self }.map(::dotted)
    }

    private fun dotted(value: Long): String =
        "${(value shr 24) and 0xFF}.${(value shr 16) and 0xFF}.${(value shr 8) and 0xFF}.${value and 0xFF}"
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.protocol.*" --tests "com.saubh.deskbuddy.media.*" --tests "com.saubh.deskbuddy.discovery.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain shared/src/commonTest
git commit -m "M3: protocol messages for now playing, audio state, seek; PlaybackClock and SubnetHosts helpers"
```

---

### Task 2: Desktop actuator interfaces + ControlSession dispatch (jvmMain)

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/MediaSessionActuator.kt`
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/AudioActuator.kt`
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/Actuators.kt`
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ControlSession.kt` (`execute()`)
- Modify: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/TestActuators.kt`
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/ControlSessionTest.kt` (extend)

**Interfaces:**
- Consumes: Task 1 commands.
- Produces:
  ```kotlin
  data class Artwork(val artworkId: String, val mimeType: String, val base64: String)
  interface MediaSessionActuator { val state: StateFlow<NowPlayingCommand>; val artwork: StateFlow<Artwork?>; val available: StateFlow<Boolean>; fun seek(positionMs: Long): Boolean; fun refresh(); fun start(); fun stop() }
  class UnsupportedMediaSessionActuator : MediaSessionActuator
  interface AudioActuator { fun devices(): List<AudioDevice>; fun volume(): Int; fun setVolume(percent: Int); fun isMuted(): Boolean; fun setDefaultDevice(id: String): Boolean }
  class UnsupportedAudioActuator(osName: String) : AudioActuator   // every method throws UnsupportedOsException
  class Actuators(media, share, apps, files, mediaSession = UnsupportedMediaSessionActuator(), audio = UnsupportedAudioActuator("unknown")) { var onStateRequested: () -> Unit = {} }
  ```

- [ ] **Step 1: Write the failing tests**

Add to `TestActuators.kt`:

```kotlin
class FakeMediaSessionActuator : MediaSessionActuator {
    override val state = MutableStateFlow(NowPlayingCommand.NONE)
    override val artwork = MutableStateFlow<Artwork?>(null)
    override val available = MutableStateFlow(true)
    val seeks = mutableListOf<Long>()
    var seekResult = true
    var refreshed = 0
    override fun seek(positionMs: Long): Boolean { seeks += positionMs; return seekResult }
    override fun refresh() { refreshed++ }
    override fun start() = Unit
    override fun stop() = Unit
}

class FakeAudioActuator(
    var deviceList: List<AudioDevice> = listOf(AudioDevice("dev-1", "Speakers", true), AudioDevice("dev-2", "Headphones", false)),
    var level: Int = 50,
    var muted: Boolean = false,
) : AudioActuator {
    override fun devices(): List<AudioDevice> = deviceList
    override fun volume(): Int = level
    override fun setVolume(percent: Int) { level = percent }
    override fun isMuted(): Boolean = muted
    override fun setDefaultDevice(id: String): Boolean {
        if (deviceList.none { it.id == id }) return false
        deviceList = deviceList.map { it.copy(isDefault = it.id == id) }
        return true
    }
}
```

and extend `TestActuators`:

```kotlin
class TestActuators(
    val dir: Path = Files.createTempDirectory("deskbuddy-test"),
    val media: FakeMediaActuator = FakeMediaActuator(),
    val share: FakeShareActuator = FakeShareActuator(),
    val appActuator: FakeAppActuator = FakeAppActuator(),
    val mediaSession: FakeMediaSessionActuator = FakeMediaSessionActuator(),
    val audio: FakeAudioActuator = FakeAudioActuator(),
) {
    ...
    val bundle = Actuators(media, share, apps, files, mediaSession, audio)
    ...
}
```

Append to `ControlSessionTest.kt`:

```kotlin
    // ---- media session + audio (M3) ----

    @Test
    fun `onMessage givenMediaStateRequest invokesStateRequestedHook`() {
        var requested = 0
        fakes.bundle.onStateRequested = { requested++ }
        assertEquals(ok, authed().run(MediaStateRequestCommand))
        assertEquals(1, requested)
    }

    @Test
    fun `onMessage givenSeek forwardsPositionAndAcks`() {
        assertEquals(ok, authed().run(MediaSeekCommand(42_000)))
        assertEquals(listOf(42_000L), fakes.mediaSession.seeks)
    }

    @Test
    fun `onMessage givenSeekWithoutSession returnsNotFound`() {
        fakes.mediaSession.seekResult = false
        assertEquals(SessionReply(NOT_FOUND), authed().run(MediaSeekCommand(1)))
    }

    @Test
    fun `onMessage givenSetVolume clampsAndApplies`() {
        assertEquals(ok, authed().run(SetVolumeCommand(140)))
        assertEquals(100, fakes.audio.level)
        authed().run(SetVolumeCommand(-3))
        assertEquals(0, fakes.audio.level)
    }

    @Test
    fun `onMessage givenSetAudioDevice switchesDefaultOrNotFound`() {
        assertEquals(ok, authed().run(SetAudioDeviceCommand("dev-2")))
        assertTrue(fakes.audio.devices().first { it.id == "dev-2" }.isDefault)
        assertEquals(SessionReply(NOT_FOUND), authed().run(SetAudioDeviceCommand("ghost")))
    }

    @Test
    fun `onMessage givenUnsupportedAudioActuator returnsUnsupportedOs`() {
        store.add(PairedDevice("Pixel 8", "tok-live"))
        val bundle = Actuators(fakes.media, fakes.share, fakes.apps, fakes.files, audio = UnsupportedAudioActuator("Linux"))
        val reply = session(actuators = bundle).run(SetVolumeCommand(10))
        assertEquals(SessionReply(Ack(ok = false, error = ErrorCode.UNSUPPORTED_OS)), reply)
    }

    @Test
    fun `onMessage givenUnsupportedMediaSession seekReturnsNotFound`() {
        store.add(PairedDevice("Pixel 8", "tok-live"))
        val bundle = Actuators(fakes.media, fakes.share, fakes.apps, fakes.files, mediaSession = UnsupportedMediaSessionActuator())
        assertEquals(SessionReply(NOT_FOUND), session(actuators = bundle).run(MediaSeekCommand(5)))
    }
```

Add `private val NOT_FOUND = Ack(ok = false, error = ErrorCode.NOT_FOUND)` next to `ok` in the test class, plus the imports for the new commands, `AudioDevice`, `NowPlayingCommand`, `MutableStateFlow`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.ControlSessionTest"`
Expected: compilation failure (`MediaSessionActuator` unresolved).

- [ ] **Step 3: Implement**

`MediaSessionActuator.kt`:

```kotlin
package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.NowPlayingCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Artwork(val artworkId: String, val mimeType: String, val base64: String)

/** Read-side of the OS media session (now playing) plus the one write it needs: seek. */
interface MediaSessionActuator {
    val state: StateFlow<NowPlayingCommand>
    val artwork: StateFlow<Artwork?>
    /** False while the backing helper is not running (non-Windows, PowerShell missing, crashed). */
    val available: StateFlow<Boolean>
    /** Returns false when there is no session or it cannot seek. Never throws. */
    fun seek(positionMs: Long): Boolean
    /** Re-emit current state and artwork (and restart the helper if it died). */
    fun refresh()
    fun start()
    fun stop()
}

class UnsupportedMediaSessionActuator : MediaSessionActuator {
    override val state = MutableStateFlow(NowPlayingCommand.NONE)
    override val artwork = MutableStateFlow<Artwork?>(null)
    override val available = MutableStateFlow(false)
    override fun seek(positionMs: Long): Boolean = false
    override fun refresh() = Unit
    override fun start() = Unit
    override fun stop() = Unit
}
```

`AudioActuator.kt`:

```kotlin
package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.AudioDevice

/** Output (render) endpoints and the master volume of the current default one. */
interface AudioActuator {
    fun devices(): List<AudioDevice>
    /** 0..100 */
    fun volume(): Int
    fun setVolume(percent: Int)
    fun isMuted(): Boolean
    /** False when [id] is not a known render endpoint. */
    fun setDefaultDevice(id: String): Boolean
}

class UnsupportedAudioActuator(private val osName: String) : AudioActuator {
    override fun devices(): List<AudioDevice> = unsupported()
    override fun volume(): Int = unsupported()
    override fun setVolume(percent: Int) = unsupported()
    override fun isMuted(): Boolean = unsupported()
    override fun setDefaultDevice(id: String): Boolean = unsupported()
    private fun unsupported(): Nothing = throw UnsupportedOsException(osName)
}
```

`Actuators.kt`:

```kotlin
package com.saubh.deskbuddy.server

/** Everything a [ControlSession] can drive on the desktop, grouped by concern. */
class Actuators(
    val media: MediaActuator,
    val share: ShareActuator,
    val apps: AppCatalogService,
    val files: FileReceiver,
    val mediaSession: MediaSessionActuator = UnsupportedMediaSessionActuator(),
    val audio: AudioActuator = UnsupportedAudioActuator("unknown"),
) {
    /** Invoked on [com.saubh.deskbuddy.protocol.MediaStateRequestCommand]; the desktop app re-pushes its media state. */
    var onStateRequested: () -> Unit = {}
}
```

`ControlSession.execute()` — add branches (and imports):

```kotlin
        is MediaStateRequestCommand -> OK.also { actuators.onStateRequested() }
        is MediaSeekCommand -> if (actuators.mediaSession.seek(command.positionMs)) OK else NOT_FOUND
        is SetVolumeCommand -> OK.also { actuators.audio.setVolume(command.volume.coerceIn(0, 100)) }
        is SetAudioDeviceCommand -> if (actuators.audio.setDefaultDevice(command.id)) OK else NOT_FOUND
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.ControlSessionTest"`
Expected: PASS (all existing + 7 new).

- [ ] **Step 5: Commit**

```bash
git add shared/src/jvmMain shared/src/jvmTest
git commit -m "M3: MediaSessionActuator/AudioActuator interfaces and session dispatch for seek, volume, device, state request"
```

---

### Task 3: Windows media session helper (PowerShell script + Kotlin wrapper)

**Files:**
- Create: `shared/src/jvmMain/resources/deskbuddy/media-session.ps1`
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/media/MediaHelperProtocol.kt`
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/media/NowPlayingFilter.kt`
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/media/WindowsMediaSessionActuator.kt`
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/media/MediaHelperProtocolTest.kt`
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/media/NowPlayingFilterTest.kt`

**Interfaces:**
- Consumes: Task 2 `MediaSessionActuator`, `Artwork`; Task 1 `PlaybackClock`.
- Produces: `MediaHelperProtocol.parse(line): HelperEvent?`, `HelperState.toNowPlaying(): NowPlayingCommand`, `NowPlayingFilter.shouldPublish(next, nowMs): Boolean`, `WindowsMediaSessionActuator(scriptResource = "/deskbuddy/media-session.ps1", clock)`.

- [ ] **Step 1: Write the failing tests**

`MediaHelperProtocolTest.kt`:

```kotlin
package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MediaHelperProtocolTest {
    @Test
    fun `parse givenStateLine mapsToNowPlaying`() {
        val line = """{"type":"state","title":"Song","artist":"Artist","album":"Album","app":"Spotify.exe","status":"Playing","positionMs":1200,"durationMs":240000,"canSeek":true,"artworkId":"abc"}"""
        val event = assertIs<HelperState>(MediaHelperProtocol.parse(line))
        assertEquals(
            NowPlayingCommand("Song", "Artist", "Album", "Spotify.exe", PlaybackStatus.PLAYING, 1200, 240000, true, "abc"),
            event.toNowPlaying(),
        )
    }

    @Test
    fun `toNowPlaying mapsWindowsStatuses`() {
        fun status(s: String) = HelperState(status = s).toNowPlaying().status
        assertEquals(PlaybackStatus.PLAYING, status("Playing"))
        assertEquals(PlaybackStatus.PAUSED, status("Paused"))
        assertEquals(PlaybackStatus.STOPPED, status("Stopped"))
        assertEquals(PlaybackStatus.STOPPED, status("Changing"))
        assertEquals(PlaybackStatus.NONE, status("NONE"))
    }

    @Test
    fun `parse givenArtworkAndResult returnsTypedEvents`() {
        assertEquals(HelperArtwork("abc", "image/jpeg", "AAAA"), MediaHelperProtocol.parse("""{"type":"artwork","artworkId":"abc","mimeType":"image/jpeg","data":"AAAA"}"""))
        assertEquals(HelperResult(ok = false), MediaHelperProtocol.parse("""{"type":"result","ok":false}"""))
    }

    @Test
    fun `parse givenGarbageOrUnknownType returnsNull`() {
        assertNull(MediaHelperProtocol.parse("not json"))
        assertNull(MediaHelperProtocol.parse("""{"type":"debug","msg":"x"}"""))
        assertNull(MediaHelperProtocol.parse(""))
    }
}
```

`NowPlayingFilterTest.kt`:

```kotlin
package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingFilterTest {
    private val filter = NowPlayingFilter(driftMs = 1_500)
    private val playing = NowPlayingCommand("S", "A", null, "app", PlaybackStatus.PLAYING, 0, 100_000, true, "id")

    @Test
    fun `shouldPublish givenFirstSample isTrue`() {
        assertTrue(filter.shouldPublish(playing, nowMs = 0))
    }

    @Test
    fun `shouldPublish givenPositionAdvancingAsExpected isFalse`() {
        filter.shouldPublish(playing, 0)
        assertFalse(filter.shouldPublish(playing.copy(positionMs = 1_000), 1_000))
        assertFalse(filter.shouldPublish(playing.copy(positionMs = 5_200), 5_000))
    }

    @Test
    fun `shouldPublish givenSeekJump isTrue`() {
        filter.shouldPublish(playing, 0)
        assertTrue(filter.shouldPublish(playing.copy(positionMs = 30_000), 1_000))
    }

    @Test
    fun `shouldPublish givenPauseOrTrackChange isTrue`() {
        filter.shouldPublish(playing, 0)
        assertTrue(filter.shouldPublish(playing.copy(status = PlaybackStatus.PAUSED, positionMs = 1_000), 1_000))
        assertTrue(filter.shouldPublish(playing.copy(title = "Other", positionMs = 2_000), 2_000))
    }

    @Test
    fun `shouldPublish givenPausedAndPositionUnchanged isFalse`() {
        val paused = playing.copy(status = PlaybackStatus.PAUSED, positionMs = 10_000)
        filter.shouldPublish(paused, 0)
        assertFalse(filter.shouldPublish(paused, 60_000))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.media.*"`
Expected: compilation failure.

- [ ] **Step 3: Implement the protocol, filter, script and wrapper**

`MediaHelperProtocol.kt`:

```kotlin
package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One JSON line from the PowerShell helper's stdout. */
@Serializable
sealed class HelperEvent

@Serializable
@SerialName("state")
data class HelperState(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val app: String? = null,
    val status: String = "NONE",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val canSeek: Boolean = false,
    val artworkId: String? = null,
) : HelperEvent() {
    fun toNowPlaying(): NowPlayingCommand = NowPlayingCommand(
        title = title?.takeIf { it.isNotBlank() },
        artist = artist?.takeIf { it.isNotBlank() },
        album = album?.takeIf { it.isNotBlank() },
        appName = app?.takeIf { it.isNotBlank() },
        status = when (status) {
            "Playing" -> PlaybackStatus.PLAYING
            "Paused" -> PlaybackStatus.PAUSED
            "NONE" -> PlaybackStatus.NONE
            else -> PlaybackStatus.STOPPED
        },
        positionMs = positionMs.coerceAtLeast(0),
        durationMs = durationMs.coerceAtLeast(0),
        canSeek = canSeek,
        artworkId = artworkId?.takeIf { it.isNotBlank() },
    )
}

@Serializable
@SerialName("artwork")
data class HelperArtwork(val artworkId: String, val mimeType: String, val data: String) : HelperEvent()

@Serializable
@SerialName("result")
data class HelperResult(val ok: Boolean) : HelperEvent()

object MediaHelperProtocol {
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

    /** Null for blank lines, malformed JSON, or unknown event types. */
    fun parse(line: String): HelperEvent? =
        if (line.isBlank()) null else runCatching { json.decodeFromString(HelperEvent.serializer(), line) }.getOrNull()
}
```

`NowPlayingFilter.kt`:

```kotlin
package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.media.PlaybackClock
import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus
import kotlin.math.abs

/**
 * Decides whether a fresh sample is worth pushing to the phone: any non-position change,
 * or a position that drifted more than [driftMs] from what the phone would interpolate.
 */
class NowPlayingFilter(private val driftMs: Long = 1_500) {
    private var published: NowPlayingCommand? = null
    private var publishedAtMs = 0L

    fun shouldPublish(next: NowPlayingCommand, nowMs: Long): Boolean {
        val prev = published
        val changed = prev == null ||
            prev.copy(positionMs = 0) != next.copy(positionMs = 0) ||
            abs(expectedPosition(prev, nowMs) - next.positionMs) > driftMs
        if (changed) {
            published = next
            publishedAtMs = nowMs
        }
        return changed
    }

    private fun expectedPosition(prev: NowPlayingCommand, nowMs: Long): Long =
        PlaybackClock.positionAt(nowMs, publishedAtMs, prev.positionMs, prev.durationMs, prev.status == PlaybackStatus.PLAYING)
}
```

`media-session.ps1` (resource; Windows PowerShell 5.1; UTF-8 JSON lines):

```powershell
# DeskBuddy media-session helper. Polls the Windows system media session and prints JSON lines.
# stdin commands: toggle | play | pause | next | prev | seek <ms> | refresh | quit
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Continue'

[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
[Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null
Add-Type -AssemblyName System.Runtime.WindowsRuntime

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($operation, $resultType) {
    $task = $asTaskGeneric.MakeGenericMethod($resultType).Invoke($null, @($operation))
    $task.Wait(-1) | Out-Null
    return $task.Result
}

function Emit($object) {
    [Console]::Out.WriteLine(($object | ConvertTo-Json -Compress -Depth 3))
    [Console]::Out.Flush()
}

function Get-Session {
    try {
        $manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
        return $manager.GetCurrentSession()
    } catch { return $null }
}

function Read-Artwork($props) {
    if ($null -eq $props.Thumbnail) { return $null }
    try {
        $stream = Await ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
        $size = [uint32]$stream.Size
        if ($size -le 0 -or $size -gt 4MB) { return $null }
        $reader = New-Object Windows.Storage.Streams.DataReader($stream.GetInputStreamAt(0))
        Await ($reader.LoadAsync($size)) ([uint32]) | Out-Null
        $bytes = New-Object byte[] $size
        $reader.ReadBytes($bytes)
        $reader.Dispose(); $stream.Dispose()
        $sha = [System.Security.Cryptography.SHA1]::Create()
        $id = [System.BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-', '').ToLowerInvariant()
        return @{ id = $id; mimeType = [string]$stream.ContentType; data = [Convert]::ToBase64String($bytes) }
    } catch { return $null }
}

function Read-State($session, $trackKey) {
    if ($null -eq $session) {
        return [ordered]@{ type = 'state'; status = 'NONE' }
    }
    $props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
    $playback = $session.GetPlaybackInfo()
    $timeline = $session.GetTimelineProperties()
    $status = [string]$playback.PlaybackStatus
    $position = [int64]$timeline.Position.TotalMilliseconds
    if ($status -eq 'Playing' -and $timeline.LastUpdatedTime.Year -gt 2000) {
        $position += [int64]([DateTimeOffset]::Now - $timeline.LastUpdatedTime).TotalMilliseconds
    }
    $duration = [int64]($timeline.EndTime - $timeline.StartTime).TotalMilliseconds
    if ($duration -gt 0 -and $position -gt $duration) { $position = $duration }
    return [ordered]@{
        type = 'state'
        title = [string]$props.Title
        artist = [string]$props.Artist
        album = [string]$props.AlbumTitle
        app = [string]$session.SourceAppUserModelId
        status = $status
        positionMs = $position
        durationMs = $duration
        canSeek = [bool]$playback.Controls.IsPlaybackPositionEnabled
        artworkId = $null
        trackKey = $trackKey
    }
}

$stdin = New-Object System.IO.StreamReader([Console]::OpenStandardInput())
$pending = $stdin.ReadLineAsync()
$lastJson = ''
$lastTrackKey = ''
$artworkId = $null

while ($true) {
    $session = Get-Session
    $trackKey = ''
    $props = $null
    if ($null -ne $session) {
        try {
            $props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
            $trackKey = "$($session.SourceAppUserModelId)|$($props.Title)|$($props.Artist)|$($props.AlbumTitle)"
        } catch { $trackKey = '' }
    }
    if ($trackKey -ne $lastTrackKey) {
        $lastTrackKey = $trackKey
        $artworkId = $null
        if ($null -ne $props) {
            $art = Read-Artwork $props
            if ($null -ne $art) {
                $artworkId = $art.id
                Emit ([ordered]@{ type = 'artwork'; artworkId = $art.id; mimeType = $art.mimeType; data = $art.data })
            }
        }
    }
    try {
        $state = Read-State $session $trackKey
        $state.artworkId = $artworkId
        $state.Remove('trackKey')
        $json = ($state | ConvertTo-Json -Compress -Depth 3)
        if ($json -ne $lastJson) {
            [Console]::Out.WriteLine($json); [Console]::Out.Flush()
            $lastJson = $json
        }
    } catch { }

    if ($pending.IsCompleted) {
        $line = $pending.Result
        if ($null -eq $line) { break }
        $parts = $line.Trim().Split(' ')
        $ok = $false
        try {
            switch ($parts[0]) {
                'quit' { exit 0 }
                'refresh' { $lastJson = ''; $lastTrackKey = ''; $ok = $true }
                'toggle' { if ($session) { $ok = Await ($session.TryTogglePlayPauseAsync()) ([bool]) } }
                'play' { if ($session) { $ok = Await ($session.TryPlayAsync()) ([bool]) } }
                'pause' { if ($session) { $ok = Await ($session.TryPauseAsync()) ([bool]) } }
                'next' { if ($session) { $ok = Await ($session.TrySkipNextAsync()) ([bool]) } }
                'prev' { if ($session) { $ok = Await ($session.TrySkipPreviousAsync()) ([bool]) } }
                'seek' {
                    if ($session -and $parts.Length -gt 1) {
                        $ticks = [int64]$parts[1] * 10000
                        $ok = Await ($session.TryChangePlaybackPositionAsync($ticks)) ([bool])
                    }
                }
            }
        } catch { $ok = $false }
        if ($parts[0] -ne 'refresh') { Emit ([ordered]@{ type = 'result'; ok = [bool]$ok }) }
        $lastJson = ''
        $pending = $stdin.ReadLineAsync()
    }
    Start-Sleep -Milliseconds 500
}
```

`WindowsMediaSessionActuator.kt`:

```kotlin
package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.server.Artwork
import com.saubh.deskbuddy.server.MediaSessionActuator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs the bundled PowerShell helper as a child process and mirrors its JSON lines
 * into flows. Restarted with backoff (1s, 2s, 4s) if it exits unexpectedly.
 */
class WindowsMediaSessionActuator(
    private val scriptResource: String = "/deskbuddy/media-session.ps1",
    private val clock: () -> Long = System::currentTimeMillis,
) : MediaSessionActuator {

    private companion object {
        const val MAX_RESTARTS = 3
        const val RESULT_TIMEOUT_SECONDS = 2L
    }

    private val _state = MutableStateFlow(NowPlayingCommand.NONE)
    override val state: StateFlow<NowPlayingCommand> = _state
    private val _artwork = MutableStateFlow<Artwork?>(null)
    override val artwork: StateFlow<Artwork?> = _artwork
    private val _available = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = _available

    private val filter = NowPlayingFilter()
    private val results = LinkedBlockingQueue<HelperResult>()
    private val lock = Any()
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var restarts = 0
    @Volatile private var stopped = false

    override fun start() {
        stopped = false
        restarts = 0
        launchProcess()
    }

    override fun stop() {
        stopped = true
        send("quit")
        synchronized(lock) { process?.destroy(); process = null; writer = null }
        _available.value = false
    }

    override fun refresh() {
        val alive = synchronized(lock) { process?.isAlive == true }
        if (alive) send("refresh") else { restarts = 0; launchProcess() }
    }

    override fun seek(positionMs: Long): Boolean {
        results.clear()
        if (!send("seek ${positionMs.coerceAtLeast(0)}")) return false
        return results.poll(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)?.ok ?: false
    }

    private fun send(command: String): Boolean = synchronized(lock) {
        val w = writer ?: return false
        runCatching { w.write(command); w.newLine(); w.flush() }.isSuccess
    }

    private fun launchProcess() {
        if (stopped) return
        val started = runCatching {
            val script = extractScript()
            ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        }.getOrNull()
        if (started == null) { _available.value = false; return }
        synchronized(lock) {
            process = started
            writer = started.outputStream.bufferedWriter(StandardCharsets.UTF_8)
        }
        thread(isDaemon = true, name = "deskbuddy-media-helper") { readLoop(started) }
    }

    private fun readLoop(p: Process) {
        runCatching {
            p.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines -> lines.forEach(::handle) }
        }
        onExit(p)
    }

    private fun handle(line: String) {
        when (val event = MediaHelperProtocol.parse(line)) {
            is HelperState -> {
                _available.value = true
                val next = event.toNowPlaying()
                if (filter.shouldPublish(next, clock())) _state.value = next
            }
            is HelperArtwork -> _artwork.value = Artwork(event.artworkId, event.mimeType, event.data)
            is HelperResult -> results.offer(event)
            null -> Unit
        }
    }

    private fun onExit(p: Process) {
        synchronized(lock) { if (process === p) { process = null; writer = null } }
        _available.value = false
        _state.value = NowPlayingCommand.NONE
        if (stopped || restarts >= MAX_RESTARTS) return
        Thread.sleep(1_000L shl restarts)
        restarts++
        launchProcess()
    }

    private fun extractScript(): Path {
        val bytes = javaClass.getResourceAsStream(scriptResource)?.use { it.readBytes() }
            ?: error("Missing helper resource $scriptResource")
        val file = Files.createTempFile("deskbuddy-media-", ".ps1")
        Files.write(file, bytes)
        file.toFile().deleteOnExit()
        return file
    }
}
```

Note the `_state.value = next` only when the filter says so; the `publishedAtMs` in the filter is the desktop clock, matching `MediaStateBroadcaster` (Task 5), which just forwards `state`.

- [ ] **Step 4: Run tests and a manual helper smoke test**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.media.*"`
Expected: PASS.

Manual: `powershell -NoProfile -ExecutionPolicy Bypass -File shared/src/jvmMain/resources/deskbuddy/media-session.ps1` while something plays; expect a `{"type":"artwork",...}` line then `{"type":"state",...}` lines; type `seek 5000` + Enter; expect `{"type":"result","ok":true}`; type `quit`.

- [ ] **Step 5: Commit**

```bash
git add shared/src/jvmMain shared/src/jvmTest
git commit -m "M3: Windows media session helper (PowerShell) with JSON-line protocol, drift filter and process wrapper"
```

---

### Task 4: Windows audio endpoints via JNA COM

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/audio/CoreAudioIds.kt` (GUIDs, vtable indices, structs)
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/audio/ComObject.kt`
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/audio/WindowsAudioActuator.kt`
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ActuatorFactory.kt`
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/audio/WindowsAudioActuatorTest.kt` (runs only on Windows)
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/ActuatorFactoryTest.kt` (extend)

**Interfaces:**
- Produces: `WindowsAudioActuator : AudioActuator`; `ActuatorFactory.audio(osName)`, `ActuatorFactory.mediaSession(osName)`; `ActuatorFactory.create(...)` now fills `mediaSession` and `audio`.

- [ ] **Step 1: Write the failing tests**

`WindowsAudioActuatorTest.kt`:

```kotlin
package com.saubh.deskbuddy.server.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real Core Audio calls; skipped (passes trivially) off Windows. Never changes the audible level. */
class WindowsAudioActuatorTest {
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    @Test
    fun `devices givenWindows listsExactlyOneDefaultRenderEndpoint`() {
        if (!isWindows) return
        val devices = WindowsAudioActuator().devices()
        assertTrue(devices.isNotEmpty(), "expected at least one render endpoint")
        assertEquals(1, devices.count { it.isDefault })
        assertTrue(devices.all { it.name.isNotBlank() && it.id.startsWith("{") })
    }

    @Test
    fun `volume givenWindows isPercentAndSetToSameValueIsIdempotent`() {
        if (!isWindows) return
        val audio = WindowsAudioActuator()
        val before = audio.volume()
        assertTrue(before in 0..100)
        audio.setVolume(before)
        assertEquals(before, audio.volume())
        audio.isMuted() // must not throw
    }

    @Test
    fun `setDefaultDevice givenUnknownId returnsFalse`() {
        if (!isWindows) return
        assertEquals(false, WindowsAudioActuator().setDefaultDevice("{not-a-device}"))
    }
}
```

Add to `ActuatorFactoryTest.kt`:

```kotlin
    @Test
    fun `audio and mediaSession givenOs returnMatchingActuators`() {
        assertIs<WindowsAudioActuator>(ActuatorFactory.audio("Windows 11"))
        assertIs<UnsupportedAudioActuator>(ActuatorFactory.audio("Linux"))
        assertIs<WindowsMediaSessionActuator>(ActuatorFactory.mediaSession("Windows 11"))
        assertIs<UnsupportedMediaSessionActuator>(ActuatorFactory.mediaSession("Mac OS X"))
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.audio.*" --tests "com.saubh.deskbuddy.server.ActuatorFactoryTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement**

`CoreAudioIds.kt`:

```kotlin
package com.saubh.deskbuddy.server.audio

import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Guid

/** GUIDs, enum values and vtable slots for the Core Audio + PolicyConfig interfaces we call. */
internal object CoreAudioIds {
    val CLSID_MMDeviceEnumerator = Guid.CLSID("{BCDE0395-E52F-467C-8E3D-C4579291692E}")
    val IID_IMMDeviceEnumerator = Guid.IID("{A95664D2-9614-4F35-A746-DE8DB63617E6}")
    val IID_IAudioEndpointVolume = Guid.IID("{5CDF2C82-841E-4546-9722-0CF74078229A}")
    val CLSID_PolicyConfigClient = Guid.CLSID("{870AF99C-171D-4F9E-AF0D-E63DF40C2BC9}")
    val IID_IPolicyConfig = Guid.IID("{F8679F50-850A-41CF-9C72-430F290290C8}")
    val PKEY_Device_FriendlyName = Guid.GUID("{A45C254E-DF1C-4EFD-8020-67D146A850E0}")
    const val PID_Device_FriendlyName = 14

    const val eRender = 0
    const val eConsole = 0
    const val eMultimedia = 1
    const val DEVICE_STATE_ACTIVE = 1
    const val STGM_READ = 0
    const val CLSCTX_ALL = 23
    const val VT_LPWSTR = 31

    // IMMDeviceEnumerator
    const val ENUM_AUDIO_ENDPOINTS = 3
    const val GET_DEFAULT_AUDIO_ENDPOINT = 4
    const val GET_DEVICE = 5
    // IMMDeviceCollection
    const val COLLECTION_GET_COUNT = 3
    const val COLLECTION_ITEM = 4
    // IMMDevice
    const val DEVICE_ACTIVATE = 3
    const val DEVICE_OPEN_PROPERTY_STORE = 4
    const val DEVICE_GET_ID = 5
    // IPropertyStore
    const val PROPERTY_STORE_GET_VALUE = 5
    // IAudioEndpointVolume
    const val SET_MASTER_VOLUME_SCALAR = 7
    const val GET_MASTER_VOLUME_SCALAR = 9
    const val GET_MUTE = 15
    // IPolicyConfig
    const val SET_DEFAULT_ENDPOINT = 13
}

@Structure.FieldOrder("fmtid", "pid")
internal class PropertyKey(@JvmField var fmtid: Guid.GUID = Guid.GUID(), @JvmField var pid: Int = 0) : Structure()

/** Only the VT_LPWSTR shape of PROPVARIANT is needed here. */
@Structure.FieldOrder("vt", "reserved1", "reserved2", "reserved3", "pwszVal")
internal class PropVariant : Structure() {
    @JvmField var vt: Short = 0
    @JvmField var reserved1: Short = 0
    @JvmField var reserved2: Short = 0
    @JvmField var reserved3: Short = 0
    @JvmField var pwszVal: Pointer? = null
}
```

`ComObject.kt`:

```kotlin
package com.saubh.deskbuddy.server.audio

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.COM.COMException
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.PointerByReference

/** Raw vtable caller for COM interfaces JNA Platform does not wrap. */
internal class ComObject(pointer: Pointer) : Unknown(pointer), AutoCloseable {
    fun call(slot: Int, vararg args: Any?): HRESULT = HRESULT(_invokeNativeInt(slot, arrayOf(pointer, *args)))

    fun check(slot: Int, vararg args: Any?) {
        val hr = call(slot, *args)
        if (hr.toInt() < 0) throw COMException("COM call slot $slot failed", hr)
    }

    fun child(slot: Int, vararg args: Any?): ComObject {
        val out = PointerByReference()
        check(slot, *args, out)
        return ComObject(out.value ?: throw COMException("null interface from slot $slot"))
    }

    override fun close() { Release() }
}
```

`WindowsAudioActuator.kt`:

```kotlin
package com.saubh.deskbuddy.server.audio

import com.saubh.deskbuddy.protocol.AudioDevice
import com.saubh.deskbuddy.server.AudioActuator
import com.saubh.deskbuddy.server.audio.CoreAudioIds as Ids
import com.sun.jna.WString
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.FloatByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Core Audio through raw COM vtables (IMMDeviceEnumerator, IAudioEndpointVolume) plus the
 * undocumented-but-stable IPolicyConfig for switching the default device. All calls run on
 * one dedicated MTA thread.
 */
class WindowsAudioActuator : AudioActuator {

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "deskbuddy-audio").apply { isDaemon = true } }
    private var initialized = false

    override fun devices(): List<AudioDevice> = onComThread {
        enumerator().use { enumerator ->
            val defaultId = enumerator.child(Ids.GET_DEFAULT_AUDIO_ENDPOINT, Ids.eRender, Ids.eMultimedia).use(::deviceId)
            enumerator.child(Ids.ENUM_AUDIO_ENDPOINTS, Ids.eRender, Ids.DEVICE_STATE_ACTIVE).use { collection ->
                val count = IntByReference().also { collection.check(Ids.COLLECTION_GET_COUNT, it) }.value
                (0 until count).map { index ->
                    collection.child(Ids.COLLECTION_ITEM, index).use { device ->
                        val id = deviceId(device)
                        AudioDevice(id, friendlyName(device), isDefault = id == defaultId)
                    }
                }
            }
        }
    }

    override fun volume(): Int = onComThread {
        withEndpointVolume { ep ->
            FloatByReference().also { ep.check(Ids.GET_MASTER_VOLUME_SCALAR, it) }.value
        }.let { (it * 100f).roundToInt().coerceIn(0, 100) }
    }

    override fun setVolume(percent: Int) = onComThread {
        withEndpointVolume { ep -> ep.check(Ids.SET_MASTER_VOLUME_SCALAR, percent.coerceIn(0, 100) / 100f, null) }
    }

    override fun isMuted(): Boolean = onComThread {
        withEndpointVolume { ep -> IntByReference().also { ep.check(Ids.GET_MUTE, it) }.value != 0 }
    }

    override fun setDefaultDevice(id: String): Boolean = onComThread {
        if (devicesUnsafe().none { it.id == id }) return@onComThread false
        val out = PointerByReference()
        val hr = Ole32.INSTANCE.CoCreateInstance(Ids.CLSID_PolicyConfigClient, null, Ids.CLSCTX_ALL, Ids.IID_IPolicyConfig, out)
        if (hr.toInt() < 0) return@onComThread false
        ComObject(out.value).use { policy ->
            policy.check(Ids.SET_DEFAULT_ENDPOINT, WString(id), Ids.eConsole)
            policy.check(Ids.SET_DEFAULT_ENDPOINT, WString(id), Ids.eMultimedia)
        }
        true
    }

    private fun devicesUnsafe(): List<AudioDevice> = enumerator().use { enumerator ->
        enumerator.child(Ids.ENUM_AUDIO_ENDPOINTS, Ids.eRender, Ids.DEVICE_STATE_ACTIVE).use { collection ->
            val count = IntByReference().also { collection.check(Ids.COLLECTION_GET_COUNT, it) }.value
            (0 until count).map { i -> collection.child(Ids.COLLECTION_ITEM, i).use { AudioDevice(deviceId(it), "", false) } }
        }
    }

    private fun <T> withEndpointVolume(block: (ComObject) -> T): T = enumerator().use { enumerator ->
        enumerator.child(Ids.GET_DEFAULT_AUDIO_ENDPOINT, Ids.eRender, Ids.eMultimedia).use { device ->
            device.child(Ids.DEVICE_ACTIVATE, Ids.IID_IAudioEndpointVolume, Ids.CLSCTX_ALL, null).use(block)
        }
    }

    private fun enumerator(): ComObject {
        val out = PointerByReference()
        val hr = Ole32.INSTANCE.CoCreateInstance(Ids.CLSID_MMDeviceEnumerator, null, Ids.CLSCTX_ALL, Ids.IID_IMMDeviceEnumerator, out)
        if (hr.toInt() < 0) throw IllegalStateException("CoCreateInstance(MMDeviceEnumerator) failed: $hr")
        return ComObject(out.value)
    }

    private fun deviceId(device: ComObject): String {
        val out = PointerByReference()
        device.check(Ids.DEVICE_GET_ID, out)
        val id = out.value.getWideString(0)
        Ole32.INSTANCE.CoTaskMemFree(out.value)
        return id
    }

    private fun friendlyName(device: ComObject): String =
        device.child(Ids.DEVICE_OPEN_PROPERTY_STORE, Ids.STGM_READ).use { store ->
            val key = PropertyKey(Ids.PKEY_Device_FriendlyName, Ids.PID_Device_FriendlyName).also { it.write() }
            val value = PropVariant().also { it.write() }
            store.check(Ids.PROPERTY_STORE_GET_VALUE, key.pointer, value.pointer)
            value.read()
            val text = if (value.vt.toInt() == Ids.VT_LPWSTR) value.pwszVal?.getWideString(0).orEmpty() else ""
            value.pwszVal?.let { Ole32.INSTANCE.CoTaskMemFree(it) }
            text.ifBlank { "Audio device" }
        }

    private fun <T> onComThread(block: () -> T): T = try {
        executor.submit(Callable {
            if (!initialized) {
                Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED)
                initialized = true
            }
            block()
        }).get()
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }
}
```

`ActuatorFactory.kt`:

```kotlin
    fun create(
        osName: String = System.getProperty("os.name") ?: "unknown",
        onTextShared: (String) -> Unit = {},
        onFileReceived: (Path) -> Unit = {},
        iconFor: (DesktopApp) -> String? = AppIconProvider()::iconPng,
    ): Actuators = Actuators(
        media = media(osName),
        share = AwtShareActuator(onTextShared),
        apps = AppCatalogService(apps(osName), ShortcutStore(), iconFor),
        files = FileReceiver(onFileReceived = onFileReceived),
        mediaSession = mediaSession(osName),
        audio = audio(osName),
    )

    fun mediaSession(osName: String): MediaSessionActuator =
        if (isWindows(osName)) WindowsMediaSessionActuator() else UnsupportedMediaSessionActuator()

    fun audio(osName: String): AudioActuator =
        if (isWindows(osName)) WindowsAudioActuator() else UnsupportedAudioActuator(osName)
```

(`iconFor` / `AppIconProvider` arrive in Task 6; until then leave the `iconFor` parameter out and add it in Task 6.)

- [ ] **Step 4: Run tests to verify they pass (on this Windows PC they exercise real COM)**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.audio.*" --tests "com.saubh.deskbuddy.server.ActuatorFactoryTest"`
Expected: PASS; device names printed by a temporary `println` if needed, then removed.

- [ ] **Step 5: Commit**

```bash
git add shared/src/jvmMain shared/src/jvmTest
git commit -m "M3: Windows Core Audio actuator (devices, master volume, default switch) via JNA COM; factory wiring"
```

---

### Task 5: MediaStateBroadcaster + desktop wiring + status note

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/media/MediaStateBroadcaster.kt`
- Modify: `desktopApp/src/main/kotlin/com/saubh/deskbuddy/DesktopController.kt`
- Modify: `desktopApp/src/main/kotlin/com/saubh/deskbuddy/ui/StatusPanel.kt` and `ServerScreen.kt` (pass `helperAvailable`)
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/media/MediaStateBroadcasterTest.kt`

**Interfaces:**
- Produces: `MediaStateBroadcaster(mediaSession, audio, volumePollMs = 1_000, devicePollMs = 5_000)`, `fun commands(): Flow<Command>`, `fun resendAll()`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.protocol.AudioStateCommand
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.MediaArtworkCommand
import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus
import com.saubh.deskbuddy.server.Artwork
import com.saubh.deskbuddy.server.FakeAudioActuator
import com.saubh.deskbuddy.server.FakeMediaSessionActuator
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaStateBroadcasterTest {
    private val session = FakeMediaSessionActuator()
    private val audio = FakeAudioActuator()
    private val broadcaster = MediaStateBroadcaster(session, audio, volumePollMs = 10, devicePollMs = 10)

    @Test
    fun `commands emitsInitialAudioStateThenOnlyChanges`() = runBlocking {
        withTimeout(5_000) {
            val states = broadcaster.commands().filterIsInstance<AudioStateCommand>().take(2)
            val job = launch { audio.level = 70 }
            val collected = states.toList()
            job.join()
            assertEquals(50, collected[0].volume)
            assertEquals(70, collected[1].volume)
            assertTrue(collected[0].devices.any { it.isDefault })
        }
    }

    @Test
    fun `commands forwardsNowPlayingAndArtwork`() = runBlocking {
        withTimeout(5_000) {
            session.state.value = NowPlayingCommand(title = "S", status = PlaybackStatus.PLAYING, artworkId = "a1")
            session.artwork.value = Artwork("a1", "image/png", "AAAA")
            val np = broadcaster.commands().filterIsInstance<NowPlayingCommand>().first { it.title == "S" }
            assertEquals(PlaybackStatus.PLAYING, np.status)
            assertEquals(MediaArtworkCommand("a1", "image/png", "AAAA"), broadcaster.commands().filterIsInstance<MediaArtworkCommand>().first())
        }
    }

    @Test
    fun `resendAll reEmitsAllThreeAndRefreshesHelper`() = runBlocking {
        withTimeout(5_000) {
            session.artwork.value = Artwork("a1", "image/png", "AAAA")
            val collected = mutableListOf<Command>()
            val job = launch { broadcaster.commands().collect { collected += it } }
            broadcaster.commands().filterIsInstance<AudioStateCommand>().first()
            val before = collected.size
            broadcaster.resendAll()
            broadcaster.commands().filterIsInstance<AudioStateCommand>().first()
            kotlinx.coroutines.delay(50)
            job.cancel()
            assertEquals(1, session.refreshed)
            val after = collected.drop(before)
            assertTrue(after.any { it is NowPlayingCommand } && after.any { it is MediaArtworkCommand } && after.any { it is AudioStateCommand })
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.media.MediaStateBroadcasterTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement**

```kotlin
package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.protocol.AudioDevice
import com.saubh.deskbuddy.protocol.AudioStateCommand
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.MediaArtworkCommand
import com.saubh.deskbuddy.server.AudioActuator
import com.saubh.deskbuddy.server.MediaSessionActuator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Turns actuator state into the stream of pushes the phone needs: now-playing on change,
 * artwork on change, audio state polled and diffed. [resendAll] re-emits everything.
 */
class MediaStateBroadcaster(
    private val mediaSession: MediaSessionActuator,
    private val audio: AudioActuator,
    private val volumePollMs: Long = 1_000,
    private val devicePollMs: Long = 5_000,
) {
    private val resend = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    fun resendAll() {
        mediaSession.refresh()
        resend.tryEmit(Unit)
    }

    fun commands(): Flow<Command> = merge(
        mediaSession.state,
        resend.map { mediaSession.state.value },
        merge(mediaSession.artwork, resend.map { mediaSession.artwork.value }).filterNotNull()
            .map { MediaArtworkCommand(it.artworkId, it.mimeType, it.base64) },
        audioChanges(),
        resend.map { snapshot(cachedDevices()) }.filterNotNull(),
    )

    private var devices: List<AudioDevice> = emptyList()
    private var devicesAtMs = 0L

    private fun cachedDevices(): List<AudioDevice> {
        val now = System.currentTimeMillis()
        if (now - devicesAtMs >= devicePollMs) {
            devices = runCatching { audio.devices() }.getOrDefault(devices)
            devicesAtMs = now
        }
        return devices
    }

    private fun snapshot(devices: List<AudioDevice>): AudioStateCommand? =
        runCatching { AudioStateCommand(devices, audio.volume(), audio.isMuted()) }.getOrNull()

    private fun audioChanges(): Flow<AudioStateCommand> = flow {
        var last: AudioStateCommand? = null
        while (true) {
            val state = snapshot(cachedDevices())
            if (state != null && state != last) {
                emit(state)
                last = state
            }
            delay(volumePollMs)
        }
    }
}
```

`DesktopController.kt` changes:

```kotlin
    private val broadcaster = MediaStateBroadcaster(actuators.mediaSession, actuators.audio)
    val mediaHelperAvailable: StateFlow<Boolean> = actuators.mediaSession.available

    fun start() {
        ...
        actuators.onStateRequested = broadcaster::resendAll
        actuators.mediaSession.start()
        scope.launch { broadcaster.commands().collect { server.push(Push(it)) } }
    }

    fun stop() {
        transferJob?.cancel()
        actuators.mediaSession.stop()
        advertiser.stop()
        server.stop()
    }
```

`StatusPanel(state, hostName, ipAddress, port, mediaHelperAvailable: Boolean, onUnpairAll)`: add a third `AssistChip` after the address chip:

```kotlin
            AssistChip(
                onClick = {},
                label = { Text(if (mediaHelperAvailable) "Now playing: on" else "Now playing: unavailable") },
                leadingIcon = { Icon(DeskBuddyIcons.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = CircleShape,
            )
```

`ServerScreen.kt`: `val helper by controller.mediaHelperAvailable.collectAsState()` and pass it.

- [ ] **Step 4: Run tests and desktop build**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.media.*" :desktopApp:build`
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add shared/src desktopApp/src
git commit -m "M3: MediaStateBroadcaster pushes now playing, artwork and audio state; desktop wiring and helper status chip"
```

---

### Task 6: App icons for shortcuts (desktop)

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/AppIconProvider.kt`
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/AppCatalogService.kt` (constructor + `rebuild()`)
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ActuatorFactory.kt` (`iconFor` param, see Task 4)
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/AppCatalogServiceTest.kt` (extend)

**Interfaces:**
- Produces: `AppIconProvider(sizePx = 64).iconPng(app: DesktopApp): String?`; `AppCatalogService(apps, store, iconFor: (DesktopApp) -> String? = { null })`.

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `rebuild givenIconProvider includesIconsForShortcutsOnly`() {
        val withIcons = AppCatalogService(actuator, store) { app -> if (app.id == "a.lnk") "PNGDATA" else "OTHER" }
        withIcons.addShortcut("a.lnk")
        val catalog = withIcons.catalog.value
        assertEquals(mapOf("a.lnk" to "PNGDATA"), catalog.icons)
    }

    @Test
    fun `rebuild givenIconProviderReturnsNull omitsEntry`() {
        val noIcons = AppCatalogService(actuator, store) { null }
        noIcons.addShortcut("a.lnk")
        assertEquals(emptyMap(), noIcons.catalog.value.icons)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.AppCatalogServiceTest"`
Expected: compilation failure (no 3-arg constructor).

- [ ] **Step 3: Implement**

`AppCatalogService`:

```kotlin
class AppCatalogService(
    private val apps: AppActuator,
    private val store: ShortcutStore,
    private val iconFor: (DesktopApp) -> String? = { null },
) {
    ...
    private fun rebuild(): AppCatalog {
        val shortcuts = store.load()
        val known = installed.orEmpty()
        val extra = shortcuts.filter { s -> known.none { it.id == s.id } }
        val all = (known + extra).sortedBy { it.name.lowercase() }
        val icons = shortcuts.mapNotNull { app -> runCatching { iconFor(app) }.getOrNull()?.let { app.id to it } }.toMap()
        return AppCatalog(all, shortcuts.map { it.id }, icons).also { _catalog.value = it }
    }
```

`AppIconProvider.kt`:

```kotlin
package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.DesktopApp
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.swing.filechooser.FileSystemView

/** Shell icon of a launchable file as base64 PNG, cached per path. Null when unavailable. */
class AppIconProvider(private val sizePx: Int = 64) {
    private val cache = ConcurrentHashMap<String, Optional<String>>()

    fun iconPng(app: DesktopApp): String? =
        cache.computeIfAbsent(app.id) { Optional.ofNullable(render(it)) }.orElse(null)

    private fun render(path: String): String? = runCatching {
        val file = File(path)
        if (!file.exists()) return null
        val icon = FileSystemView.getFileSystemView().getSystemIcon(file, sizePx, sizePx) ?: return null
        val image = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
        image.createGraphics().also { g -> icon.paintIcon(null, g, 0, 0); g.dispose() }
        ByteArrayOutputStream().use { out ->
            ImageIO.write(image, "png", out)
            Base64.getEncoder().encodeToString(out.toByteArray())
        }
    }.getOrNull()
}
```

Then add the `iconFor` parameter to `ActuatorFactory.create` exactly as shown in Task 4.

- [ ] **Step 4: Run tests**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src
git commit -m "M3: shell icons for shortcut apps in the catalog (AppIconProvider)"
```

---

### Task 7: Desktop discovery: multi-interface mDNS + GET /info

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/LanInterfaces.kt`
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/DeskBuddyAdvertiser.kt`
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ControlServer.kt` (`desktopName` param + route)
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/LanInterfacesTest.kt`
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/ControlServerIntegrationTest.kt` (extend)

**Interfaces:**
- Produces: `data class InterfaceInfo(name, up, loopback, virtual, addresses: List<String>)`, `LanInterfaces.select(list): List<String>`, `LanInterfaces.current(): List<InetAddress>`, `ControlServer(store, actuators, port, desktopName = LanInterfaces.hostName())`.

- [ ] **Step 1: Write the failing tests**

`LanInterfacesTest.kt`:

```kotlin
package com.saubh.deskbuddy.server

import kotlin.test.Test
import kotlin.test.assertEquals

class LanInterfacesTest {
    @Test
    fun `select keepsOnlyUpNonLoopbackNonVirtualSiteLocalIpv4`() {
        val interfaces = listOf(
            InterfaceInfo("lo", up = true, loopback = true, virtual = false, addresses = listOf("127.0.0.1")),
            InterfaceInfo("wifi", up = true, loopback = false, virtual = false, addresses = listOf("192.168.29.227", "fe80::1")),
            InterfaceInfo("bt", up = true, loopback = false, virtual = false, addresses = listOf("169.254.133.82")),
            InterfaceInfo("vpn", up = true, loopback = false, virtual = true, addresses = listOf("10.8.0.2")),
            InterfaceInfo("eth-down", up = false, loopback = false, virtual = false, addresses = listOf("192.168.1.5")),
            InterfaceInfo("docker", up = true, loopback = false, virtual = false, addresses = listOf("172.17.0.1")),
        )
        assertEquals(listOf("192.168.29.227", "172.17.0.1"), LanInterfaces.select(interfaces))
    }

    @Test
    fun `isSiteLocalIpv4 coversPrivateRangesOnly`() {
        assertEquals(true, LanInterfaces.isSiteLocalIpv4("10.0.0.1"))
        assertEquals(true, LanInterfaces.isSiteLocalIpv4("172.31.255.1"))
        assertEquals(false, LanInterfaces.isSiteLocalIpv4("172.32.0.1"))
        assertEquals(false, LanInterfaces.isSiteLocalIpv4("8.8.8.8"))
        assertEquals(false, LanInterfaces.isSiteLocalIpv4("169.254.1.1"))
    }
}
```

Add to `ControlServerIntegrationTest.kt` (imports: `io.ktor.client.request.get`, `io.ktor.client.statement.bodyAsText`, `DesktopInfo`):

```kotlin
    @Test
    fun `getInfo returnsDesktopNameAndPort`() = runBlocking {
        withTimeout(15_000) {
            val body = http.get("http://127.0.0.1:$port/info").bodyAsText()
            assertEquals(DesktopInfo("TEST-PC", port), WireCodec.decodeInfo(body))
        }
    }
```

and construct the server with `desktopName = "TEST-PC"`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.LanInterfacesTest" --tests "com.saubh.deskbuddy.server.ControlServerIntegrationTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement**

`LanInterfaces.kt`:

```kotlin
package com.saubh.deskbuddy.server

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

data class InterfaceInfo(val name: String, val up: Boolean, val loopback: Boolean, val virtual: Boolean, val addresses: List<String>)

/** Which local addresses are worth advertising on: real, up adapters with a private IPv4. */
object LanInterfaces {
    fun select(interfaces: List<InterfaceInfo>): List<String> =
        interfaces.filter { it.up && !it.loopback && !it.virtual }
            .flatMap { it.addresses }
            .filter(::isSiteLocalIpv4)
            .distinct()

    fun isSiteLocalIpv4(address: String): Boolean {
        val p = address.split('.').map { it.toIntOrNull() ?: return false }
        if (p.size != 4 || p.any { it !in 0..255 }) return false
        return p[0] == 10 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168)
    }

    fun current(): List<InetAddress> = runCatching {
        val infos = NetworkInterface.getNetworkInterfaces().toList().map { nic ->
            InterfaceInfo(
                name = nic.name,
                up = nic.isUp,
                loopback = nic.isLoopback,
                virtual = nic.isVirtual,
                addresses = nic.inetAddresses.toList().filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress },
            )
        }
        select(infos).map { InetAddress.getByName(it) }
    }.getOrDefault(emptyList())

    fun hostName(): String =
        runCatching { InetAddress.getLocalHost().hostName.removeSuffix(".local") }.getOrDefault("DeskBuddy PC")
}
```

`DeskBuddyAdvertiser.kt`:

```kotlin
class DeskBuddyAdvertiser(private val port: Int = Protocol.PORT) {

    private val instances = mutableListOf<JmDNS>()

    /** One JmDNS per LAN address so the phone hears us whichever adapter it shares. */
    fun start() {
        val name = LanInterfaces.hostName()
        val addresses = LanInterfaces.current().ifEmpty { listOf(InetAddress.getLocalHost()) }
        for (address in addresses) {
            runCatching {
                JmDNS.create(address, name).also {
                    it.registerService(ServiceInfo.create(Protocol.SERVICE_TYPE + "local.", name, port, "DeskBuddy control server"))
                    instances += it
                }
            }
        }
    }

    fun stop() {
        instances.forEach { runCatching { it.unregisterAllServices(); it.close() } }
        instances.clear()
    }
}
```

`ControlServer.kt`: add `private val desktopName: String = LanInterfaces.hostName()` as the fourth constructor parameter and inside `routing { }` before the websocket:

```kotlin
                get("/info") {
                    call.respondText(WireCodec.encodeInfo(DesktopInfo(desktopName, port)), ContentType.Application.Json)
                }
```

with imports `io.ktor.http.ContentType`, `io.ktor.server.response.respondText`, `io.ktor.server.routing.get`, `com.saubh.deskbuddy.protocol.DesktopInfo`.

- [ ] **Step 4: Run tests**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src
git commit -m "M3: advertise mDNS on every LAN adapter; GET /info for subnet sweep verification"
```

---

### Task 8: Phone discovery: resolve queue + subnet sweep

**Files:**
- Modify: `shared/src/androidMain/kotlin/com/saubh/deskbuddy/client/DesktopDiscovery.kt`
- Create: `shared/src/androidMain/kotlin/com/saubh/deskbuddy/client/SubnetScanner.kt`
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/session/RemoteSession.kt` (`scanner` param, merged discovery)
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/ConnectionViewModel.kt`
- Modify: `androidApp/src/main/res/values/strings.xml` (`no_desktops_found`)

**Interfaces:**
- Consumes: `SubnetHosts`, `WireCodec.decodeInfo`, `DesktopInfo`.
- Produces: `SubnetScanner(context).scan(): List<DiscoveredDesktop>` (suspend), `RemoteSession(scope, prefs, discovery, scanner, deviceName, client)`.

- [ ] **Step 1: Implement `DesktopDiscovery` with a resolve queue and retry**

```kotlin
class DesktopDiscovery(context: Context) {

    private companion object {
        const val MAX_RESOLVE_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 500L
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    /** Emits the current list of resolved desktops (keyed by host); updates as services appear/vanish. */
    fun discover(): Flow<List<DiscoveredDesktop>> = callbackFlow {
        val lock = Any()
        val found = LinkedHashMap<String, DiscoveredDesktop>()
        val queue = ArrayDeque<Pair<NsdServiceInfo, Int>>()
        var resolving = false

        fun publish() = trySend(synchronized(lock) { found.values.toList() })

        fun resolveNext() {
            val next = synchronized(lock) {
                if (resolving) return
                queue.removeFirstOrNull()?.also { resolving = true }
            } ?: return
            val (service, attempt) = next
            @Suppress("DEPRECATION") // registerServiceInfoCallback needs API 34; minSdk is 24
            nsdManager.resolveService(
                service,
                object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        val host = resolved.host?.hostAddress
                        synchronized(lock) {
                            resolving = false
                            if (host != null) found[host] = DiscoveredDesktop(resolved.serviceName, host, resolved.port)
                        }
                        publish()
                        resolveNext()
                    }

                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        synchronized(lock) { resolving = false }
                        if (attempt < MAX_RESOLVE_ATTEMPTS) {
                            launch {
                                delay(RETRY_DELAY_MS)
                                synchronized(lock) { queue.addLast(service to attempt + 1) }
                                resolveNext()
                            }
                        } else {
                            resolveNext()
                        }
                    }
                },
            )
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                synchronized(lock) { queue.addLast(service to 1) }
                resolveNext()
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                synchronized(lock) { found.values.removeAll { it.name == service.serviceName } }
                publish()
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { close() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        nsdManager.discoverServices(Protocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        awaitClose { runCatching { nsdManager.stopServiceDiscovery(discoveryListener) } }
    }
}
```

- [ ] **Step 2: Implement `SubnetScanner`**

```kotlin
package com.saubh.deskbuddy.client

import android.content.Context
import android.net.ConnectivityManager
import com.saubh.deskbuddy.discovery.SubnetHosts
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.protocol.WireCodec
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Fallback discovery when mDNS is silent: TCP-probe every host in the phone's /24 on the
 * DeskBuddy port and confirm with `GET /info`. Same path manual entry uses, so it works
 * wherever manual entry works.
 */
class SubnetScanner(
    context: Context,
    private val port: Int = Protocol.PORT,
    private val http: HttpClient = HttpClient(CIO),
) {
    private companion object {
        const val PARALLELISM = 48
        const val CONNECT_TIMEOUT_MS = 400
        const val INFO_TIMEOUT_MS = 1_000L
    }

    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Phone's IPv4 address and prefix length on the active network, or null when offline. */
    fun localAddress(): Pair<String, Int>? {
        val network = connectivity.activeNetwork ?: return null
        val link = connectivity.getLinkProperties(network)?.linkAddresses?.firstOrNull { it.address is Inet4Address } ?: return null
        val host = link.address.hostAddress ?: return null
        return host to link.prefixLength
    }

    suspend fun scan(): List<DiscoveredDesktop> = withContext(Dispatchers.IO) {
        val (ip, prefix) = localAddress() ?: return@withContext emptyList()
        val gate = Semaphore(PARALLELISM)
        coroutineScope {
            SubnetHosts.candidates(ip, prefix)
                .map { host -> async { gate.withPermit { probe(host) } } }
                .awaitAll()
                .filterNotNull()
        }
    }

    private suspend fun probe(host: String): DiscoveredDesktop? {
        if (!portOpen(host)) return null
        return runCatching {
            withTimeout(INFO_TIMEOUT_MS) {
                val info = WireCodec.decodeInfo(http.get("http://$host:$port/info").bodyAsText())
                DiscoveredDesktop(info.name, host, info.port)
            }
        }.getOrNull()
    }

    private fun portOpen(host: String): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS); true }
    }.getOrDefault(false)
}
```

- [ ] **Step 3: Merge both sources in `RemoteSession`**

Constructor gains `private val scanner: SubnetScanner` (after `discovery`). Replace `startDiscovery()`:

```kotlin
    private companion object {
        const val MAX_RECONNECT_ATTEMPTS = 3
        const val RECONNECT_BASE_DELAY_MS = 1_000L
        const val SCAN_INITIAL_DELAY_MS = 3_000L
        const val SCAN_INTERVAL_MS = 15_000L
    }

    private var mdnsFound: List<DiscoveredDesktop> = emptyList()
    private var scanFound: List<DiscoveredDesktop> = emptyList()

    fun startDiscovery() {
        if (discoveryJob != null) return
        discoveryJob = scope.launch {
            launch {
                discovery.discover()
                    .catch { /* discovery failure is non-fatal; the sweep and manual IP still work */ }
                    .collect { mdnsFound = it; publishDiscovered() }
            }
            launch {
                delay(SCAN_INITIAL_DELAY_MS)
                while (true) {
                    if (isSearching && mdnsFound.isEmpty() && scanFound.isEmpty()) {
                        scanFound = scanner.scan()
                        publishDiscovered()
                    }
                    delay(SCAN_INTERVAL_MS)
                }
            }
        }
    }

    private val isSearching: Boolean
        get() = _uiState.value.let { it is ConnectionUiState.Idle || it is ConnectionUiState.Discovering }

    private fun publishDiscovered() {
        if (!isSearching) return
        val merged = (mdnsFound + scanFound).distinctBy { it.host }
        _uiState.value = ConnectionUiState.Discovering(merged)
    }
```

`ConnectionViewModel`: `scanner = SubnetScanner(app)` in the `RemoteSession(...)` call.

`strings.xml`: `no_desktops_found` → "No desktops found yet. Scanning your Wi-Fi too — make sure DeskBuddy is running on your PC."

- [ ] **Step 4: Build**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add shared/src/androidMain androidApp/src
git commit -m "M3: phone discovery resolves serially with retry and sweeps the local /24 when mDNS is silent"
```

---

### Task 9: Android `MediaController` + `MediaUiState`

**Files:**
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/media/MediaUiState.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/media/MediaController.kt`
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/ConnectionViewModel.kt` (`val media`)
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/ConnectionUiState.kt` (`ErrorKind.MEDIA_UNAVAILABLE`, `DEVICE_NOT_FOUND`) and `DeskBuddyApp.kt` (`messageRes`), `strings.xml`
- Test: `androidApp/src/test/kotlin/com/saubh/deskbuddy/media/MediaControllerTest.kt` — the androidApp has no JVM test source set yet; add `testImplementation(libs.kotlin.test)` + `testImplementation(libs.kotlinx.coroutinesTest)` (add `kotlinx-coroutinesTest = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }` to the catalog). `RemoteSession` is a concrete class; give `MediaController` a small `MediaGateway` interface it depends on so the test can fake it.

**Interfaces:**
- Produces:
  ```kotlin
  interface MediaGateway { val incoming: SharedFlow<Message>; val connected: Flow<Boolean>; suspend fun send(command: Command): Boolean }
  class RemoteSession : MediaGateway   // `connected` = uiState.map { it is Connected }.distinctUntilChanged()
  data class MediaUiState(nowPlaying, receivedAtMs, positionMs, artwork: ByteArray?, artworkId, audio) { status, isPlaying, canSeek, currentDevice }
  class MediaController(gateway, scope, clock = System::currentTimeMillis, decode: (String) -> ByteArray?) { state; request(); seek(ms); setVolume(percent, final); selectDevice(id); toggleMute() }
  ```

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.saubh.deskbuddy.media

import com.saubh.deskbuddy.protocol.AudioDevice
import com.saubh.deskbuddy.protocol.AudioStateCommand
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.MediaArtworkCommand
import com.saubh.deskbuddy.protocol.MediaSeekCommand
import com.saubh.deskbuddy.protocol.MediaStateRequestCommand
import com.saubh.deskbuddy.protocol.Message
import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus
import com.saubh.deskbuddy.protocol.Push
import com.saubh.deskbuddy.protocol.SetAudioDeviceCommand
import com.saubh.deskbuddy.protocol.SetVolumeCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MediaControllerTest {

    private class FakeGateway : MediaGateway {
        override val incoming = MutableSharedFlow<Message>(extraBufferCapacity = 16)
        override val connected = MutableStateFlow(false)
        val sent = mutableListOf<Command>()
        override suspend fun send(command: Command): Boolean { sent += command; return true }
    }

    private fun TestScope.controller(gateway: FakeGateway) =
        MediaController(gateway, backgroundScope, clock = { testScheduler.currentTime }, decode = { it.encodeToByteArray() })

    @Test
    fun `onConnected requestsMediaState`() = runTest(StandardTestDispatcher()) {
        val gateway = FakeGateway()
        controller(gateway)
        gateway.connected.value = true
        advanceTimeBy(1)
        assertEquals(listOf<Command>(MediaStateRequestCommand), gateway.sent)
    }

    @Test
    fun `nowPlayingPush thenTicker interpolatesPositionWhilePlaying`() = runTest(StandardTestDispatcher()) {
        val gateway = FakeGateway()
        val c = controller(gateway)
        gateway.incoming.emit(Push(NowPlayingCommand("S", status = PlaybackStatus.PLAYING, positionMs = 1_000, durationMs = 60_000)))
        advanceTimeBy(2_001)
        assertTrue(c.state.value.positionMs in 2_500..3_100, "was ${c.state.value.positionMs}")
    }

    @Test
    fun `artworkPush forCurrentTrack isKept andForOtherTrack isIgnored`() = runTest(StandardTestDispatcher()) {
        val gateway = FakeGateway()
        val c = controller(gateway)
        gateway.incoming.emit(Push(NowPlayingCommand("S", status = PlaybackStatus.PAUSED, artworkId = "a2")))
        gateway.incoming.emit(Push(MediaArtworkCommand("a1", "image/png", "old")))
        advanceTimeBy(1)
        assertNull(c.state.value.artwork)
        gateway.incoming.emit(Push(MediaArtworkCommand("a2", "image/png", "new")))
        advanceTimeBy(1)
        assertEquals("new", c.state.value.artwork?.decodeToString())
    }

    @Test
    fun `setVolume thenAudioPushWithinHold keepsLocalVolume`() = runTest(StandardTestDispatcher()) {
        val gateway = FakeGateway()
        val c = controller(gateway)
        gateway.incoming.emit(Push(AudioStateCommand(listOf(AudioDevice("d", "Spk", true)), 30, false)))
        advanceTimeBy(1)
        c.setVolume(80, final = true)
        advanceTimeBy(1)
        gateway.incoming.emit(Push(AudioStateCommand(listOf(AudioDevice("d", "Spk", true)), 30, false)))
        advanceTimeBy(1)
        assertEquals(80, c.state.value.audio?.volume)
        assertEquals(listOf<Command>(SetVolumeCommand(80)), gateway.sent)
        advanceTimeBy(1_100)
        gateway.incoming.emit(Push(AudioStateCommand(listOf(AudioDevice("d", "Spk", true)), 35, false)))
        advanceTimeBy(1)
        assertEquals(35, c.state.value.audio?.volume)
    }

    @Test
    fun `setVolume whileDragging isThrottledTo100ms`() = runTest(StandardTestDispatcher()) {
        val gateway = FakeGateway()
        val c = controller(gateway)
        c.setVolume(10, final = false); c.setVolume(11, final = false); c.setVolume(12, final = false)
        advanceTimeBy(1)
        assertEquals(listOf<Command>(SetVolumeCommand(10)), gateway.sent)
        advanceTimeBy(101)
        c.setVolume(20, final = false)
        advanceTimeBy(1)
        assertEquals(listOf<Command>(SetVolumeCommand(10), SetVolumeCommand(20)), gateway.sent)
    }

    @Test
    fun `seek sendsCommandAndUpdatesPositionOptimistically`() = runTest(StandardTestDispatcher()) {
        val gateway = FakeGateway()
        val c = controller(gateway)
        gateway.incoming.emit(Push(NowPlayingCommand("S", status = PlaybackStatus.PAUSED, positionMs = 0, durationMs = 60_000, canSeek = true)))
        advanceTimeBy(1)
        c.seek(30_000)
        advanceTimeBy(1)
        assertEquals(30_000, c.state.value.positionMs)
        assertEquals(listOf<Command>(MediaSeekCommand(30_000)), gateway.sent)
    }

    @Test
    fun `selectDevice flipsDefaultOptimistically`() = runTest(StandardTestDispatcher()) {
        val gateway = FakeGateway()
        val c = controller(gateway)
        gateway.incoming.emit(Push(AudioStateCommand(listOf(AudioDevice("a", "A", true), AudioDevice("b", "B", false)), 30, false)))
        advanceTimeBy(1)
        c.selectDevice("b")
        advanceTimeBy(1)
        assertEquals("b", c.state.value.currentDevice?.id)
        assertEquals(listOf<Command>(SetAudioDeviceCommand("b")), gateway.sent)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.saubh.deskbuddy.media.*"`
Expected: compilation failure.

- [ ] **Step 3: Implement**

`androidApp/build.gradle.kts` add:

```kotlin
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutinesTest)
```

Catalog: `kotlinx-coroutinesTest = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }`.

`MediaUiState.kt`:

```kotlin
package com.saubh.deskbuddy.media

import com.saubh.deskbuddy.protocol.AudioDevice
import com.saubh.deskbuddy.protocol.AudioStateCommand
import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus

data class MediaUiState(
    val nowPlaying: NowPlayingCommand? = null,
    val receivedAtMs: Long = 0L,
    /** Interpolated while playing; the UI shows this, not [NowPlayingCommand.positionMs]. */
    val positionMs: Long = 0L,
    val artwork: ByteArray? = null,
    val artworkId: String? = null,
    val audio: AudioStateCommand? = null,
) {
    val status: PlaybackStatus get() = nowPlaying?.status ?: PlaybackStatus.NONE
    val isPlaying: Boolean get() = status == PlaybackStatus.PLAYING
    val durationMs: Long get() = nowPlaying?.durationMs ?: 0L
    val canSeek: Boolean get() = nowPlaying?.canSeek == true && status != PlaybackStatus.NONE && durationMs > 0
    val currentDevice: AudioDevice? get() = audio?.devices?.firstOrNull { it.isDefault }
}
```

`MediaGateway` (in `session/MediaGateway.kt`):

```kotlin
package com.saubh.deskbuddy.session

import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/** What feature controllers need from the session; lets them be unit-tested with a fake. */
interface MediaGateway {
    val incoming: SharedFlow<Message>
    val connected: Flow<Boolean>
    suspend fun send(command: Command): Boolean
}
```

`RemoteSession : MediaGateway` with `override val connected: Flow<Boolean> = uiState.map { it is ConnectionUiState.Connected }.distinctUntilChanged()`.

`MediaController.kt`:

```kotlin
package com.saubh.deskbuddy.media

import com.saubh.deskbuddy.media.PlaybackClock
import com.saubh.deskbuddy.protocol.AudioStateCommand
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.MediaArtworkCommand
import com.saubh.deskbuddy.protocol.MediaCommand
import com.saubh.deskbuddy.protocol.MediaSeekCommand
import com.saubh.deskbuddy.protocol.MediaStateRequestCommand
import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.Push
import com.saubh.deskbuddy.protocol.SetAudioDeviceCommand
import com.saubh.deskbuddy.protocol.SetVolumeCommand
import com.saubh.deskbuddy.session.MediaGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Mirrors the desktop's media/audio pushes and sends seek, volume and output-device commands. */
class MediaController(
    private val gateway: MediaGateway,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val decode: (String) -> ByteArray? = ::decodeBase64,
) {
    private companion object {
        const val TICK_MS = 500L
        const val VOLUME_THROTTLE_MS = 100L
        const val VOLUME_HOLD_MS = 1_000L
    }

    private val _state = MutableStateFlow(MediaUiState())
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    private var volumeSentAt = 0L
    private var volumeHoldUntil = 0L

    init {
        scope.launch { gateway.incoming.filterIsInstance<Push>().collect { onPush(it.command) } }
        scope.launch { gateway.connected.collect { if (it) request() } }
        scope.launch {
            while (true) {
                delay(TICK_MS)
                _state.update { s ->
                    if (!s.isPlaying) s else s.copy(positionMs = interpolate(s, clock()))
                }
            }
        }
    }

    fun request() {
        scope.launch { gateway.send(MediaStateRequestCommand) }
    }

    fun seek(positionMs: Long) {
        val target = positionMs.coerceIn(0, _state.value.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        val now = clock()
        _state.update { s -> s.copy(positionMs = target, receivedAtMs = now, nowPlaying = s.nowPlaying?.copy(positionMs = target)) }
        scope.launch { gateway.send(MediaSeekCommand(target)) }
    }

    /** Throttled while dragging; [final] forces a send and starts the hold against stale pushes. */
    fun setVolume(percent: Int, final: Boolean) {
        val v = percent.coerceIn(0, 100)
        val now = clock()
        volumeHoldUntil = now + VOLUME_HOLD_MS
        _state.update { s -> s.copy(audio = s.audio?.copy(volume = v)) }
        if (!final && now - volumeSentAt < VOLUME_THROTTLE_MS) return
        volumeSentAt = now
        scope.launch { gateway.send(SetVolumeCommand(v)) }
    }

    fun toggleMute() {
        _state.update { s -> s.copy(audio = s.audio?.copy(muted = !s.audio.muted)) }
        scope.launch { gateway.send(MediaCommand(MediaAction.MUTE_TOGGLE)) }
    }

    fun selectDevice(id: String) {
        _state.update { s -> s.copy(audio = s.audio?.copy(devices = s.audio.devices.map { it.copy(isDefault = it.id == id) })) }
        scope.launch { gateway.send(SetAudioDeviceCommand(id)) }
    }

    private fun onPush(command: com.saubh.deskbuddy.protocol.Command) {
        when (command) {
            is NowPlayingCommand -> _state.update { s ->
                val sameArt = command.artworkId != null && command.artworkId == s.artworkId
                s.copy(
                    nowPlaying = command,
                    receivedAtMs = clock(),
                    positionMs = command.positionMs,
                    artwork = if (sameArt) s.artwork else null,
                    artworkId = if (sameArt) s.artworkId else null,
                )
            }
            is MediaArtworkCommand -> _state.update { s ->
                if (s.nowPlaying?.artworkId != command.artworkId) s
                else s.copy(artwork = decode(command.data), artworkId = command.artworkId)
            }
            is AudioStateCommand -> _state.update { s ->
                val holding = clock() < volumeHoldUntil && s.audio != null
                s.copy(audio = if (holding) command.copy(volume = s.audio!!.volume) else command)
            }
            else -> Unit
        }
    }

    private fun interpolate(s: MediaUiState, now: Long): Long =
        PlaybackClock.positionAt(now, s.receivedAtMs, s.nowPlaying?.positionMs ?: 0, s.durationMs, s.isPlaying)
}

private fun decodeBase64(data: String): ByteArray? =
    runCatching { android.util.Base64.decode(data, android.util.Base64.DEFAULT) }.getOrNull()
```

(The single `!!` is guarded by `s.audio != null` one line above; keep the comment.)

`ConnectionViewModel`: `val media = MediaController(session, viewModelScope)`.

`ErrorKind` additions: `MEDIA_UNAVAILABLE`, `DEVICE_NOT_FOUND`. In `RemoteSession.handleAck` the `NOT_FOUND` mapping stays `APP_NOT_FOUND` (the session cannot tell which command failed); instead `MediaController` does not need ack routing — leave `ErrorKind` additions out unless used. **Decision:** do not add new ErrorKinds; `NOT_FOUND` after seek/device shows the existing "no longer available" message, and the next push corrects the UI. Update the spec's §6 wording accordingly in the changelog.

- [ ] **Step 4: Run tests**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.saubh.deskbuddy.media.*"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add androidApp gradle/libs.versions.toml
git commit -m "M3: MediaController mirrors now playing, artwork and audio state with interpolation, throttling and hold"
```

---

### Task 10: App icons on the phone

**Files:**
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/apps/AppsUiState.kt` (`icons: Map<String, ByteArray>`)
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/apps/AppShortcutsController.kt` (decode off main thread)
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/AppIcon.kt`
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/AppsTab.kt` (`ShortcutTile` uses `AppIcon`)

**Interfaces:**
- Produces: `@Composable fun AppIcon(name: String, icon: ByteArray?, size: Dp, shape: Shape, container: Color, onContainer: Color)`.

- [ ] **Step 1: Implement**

`AppsUiState`:

```kotlin
data class AppsUiState(
    val apps: List<DesktopApp> = emptyList(),
    val shortcutIds: List<String> = emptyList(),
    val icons: Map<String, ByteArray> = emptyMap(),
    val loaded: Boolean = false,
) { ... }
```

`AppShortcutsController` catalog collector:

```kotlin
            session.incoming.filterIsInstance<AppCatalog>().collect { catalog ->
                val icons = withContext(Dispatchers.Default) {
                    catalog.icons.mapNotNull { (id, b64) ->
                        runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()?.let { id to it }
                    }.toMap()
                }
                _state.value = AppsUiState(catalog.apps, catalog.shortcutIds, icons, loaded = true)
            }
```

`AppIcon.kt`:

```kotlin
package com.saubh.deskbuddy.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import com.saubh.deskbuddy.ui.components.LetterAvatar
import com.saubh.deskbuddy.ui.components.ShapeAvatar

/** Shell icon from the PC inside the badge shape, or the letter avatar when there is none. */
@Composable
fun AppIcon(name: String, icon: ByteArray?, size: Dp, shape: Shape, container: Color, onContainer: Color) {
    val bitmap = remember(icon) { icon?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    if (bitmap == null) {
        LetterAvatar(name, size, shape, container, onContainer)
    } else {
        ShapeAvatar(shape, container, size) {
            Image(bitmap, contentDescription = null, modifier = Modifier.size(size * 0.62f))
        }
    }
}
```

`AppsTab.ShortcutTile`: replace `LetterAvatar(app.name, 56.dp, cookieShape(), ...)` with `AppIcon(app.name, icon, 56.dp, cookieShape(), scheme.primaryContainer, scheme.onPrimaryContainer)`; `ShortcutTile(app, icon = state.icons[app.id]) { ... }`.

- [ ] **Step 2: Build**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add androidApp/src
git commit -m "M3: shortcut tiles show the PC's app icons"
```

---

### Task 11: Media tab redesign (now playing, timeline, transport, volume, output device)

**Files:**
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/media/MediaTab.kt` (replaces `ui/MediaTab.kt`, which is deleted)
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/media/NowPlayingCard.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/media/PlaybackControls.kt` (Timeline + Transport)
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/media/VolumePanel.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/media/OutputDeviceSheet.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/media/TimeFormat.kt`
- Modify: `shared/src/commonMain/kotlin/com/saubh/deskbuddy/ui/theme/DeskBuddyIcons.kt` (add `Speaker`, `Headphones`, `Nightlight`, `ExpandMore`)
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/RemoteScreen.kt` (`TabContent` MEDIA branch)
- Modify: `androidApp/src/main/res/values/strings.xml`
- Test: `androidApp/src/test/kotlin/com/saubh/deskbuddy/ui/media/TimeFormatTest.kt`

**Interfaces:**
- Consumes: `MediaUiState`, `MediaController`, `MediaAction`.
- Produces composables reused by standby: `NowPlayingArtwork(artwork: ByteArray?, size: Dp, placeholderContainer, placeholderContent)`, `Timeline(state, onSeek, modifier)`, `Transport(state, onMedia, modifier)`, `VolumePanel(state, onVolume: (Int, Boolean) -> Unit, onToggleMute, onPickDevice, containerColor, modifier)`, `OutputDeviceSheet(devices, onSelect, onDismiss)`, `formatClock(ms: Long): String`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.saubh.deskbuddy.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormatTest {
    @Test
    fun `formatClock formatsMinutesSecondsAndHours`() {
        assertEquals("0:05", formatClock(5_000))
        assertEquals("3:07", formatClock(187_000))
        assertEquals("1:02:03", formatClock(3_723_000))
    }

    @Test
    fun `formatClock givenNegativeOrUnknown showsDashes`() {
        assertEquals("--:--", formatClock(-1))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.saubh.deskbuddy.ui.media.*"`
Expected: compilation failure.

- [ ] **Step 3: Implement**

`TimeFormat.kt`:

```kotlin
package com.saubh.deskbuddy.ui.media

/** "m:ss" or "h:mm:ss"; "--:--" for unknown. */
fun formatClock(ms: Long): String {
    if (ms < 0) return "--:--"
    val total = ms / 1_000
    val h = total / 3_600
    val m = (total % 3_600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
```

Icons to add to `DeskBuddyIcons` (Material Symbols paths, 24dp):

```kotlin
    val Speaker by lazy { icon("speaker", "M17 2H7c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-5 2c1.1 0 2 .9 2 2s-.9 2-2 2-2-.9-2-2 .9-2 2-2zm0 16c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z") }
    val Headphones by lazy { icon("headphones", "M12 1c-4.97 0-9 4.03-9 9v7c0 1.66 1.34 3 3 3h3v-8H5v-2c0-3.87 3.13-7 7-7s7 3.13 7 7v2h-4v8h3c1.66 0 3-1.34 3-3v-7c0-4.97-4.03-9-9-9z") }
    val Nightlight by lazy { icon("nightlight", "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9c1.11 0 2.17-.2 3.15-.57-3.49-1.27-6-4.6-6-8.43s2.51-7.16 6-8.43C14.17 3.2 13.11 3 12 3z") }
    val ExpandMore by lazy { icon("expand_more", "M16.59 8.59L12 13.17 7.41 8.59 6 10l6 6 6-6z") }
```

`NowPlayingCard.kt`:

```kotlin
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.media

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.media.MediaUiState
import com.saubh.deskbuddy.protocol.PlaybackStatus
import com.saubh.deskbuddy.ui.theme.DeskBuddyIcons

/** Artwork (or the music-note placeholder), rounded, [size] square. */
@Composable
fun NowPlayingArtwork(artwork: ByteArray?, size: Dp, placeholderContainer: Color, placeholderContent: Color) {
    val bitmap = remember(artwork) { artwork?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    val shape = MaterialTheme.shapes.largeIncreased
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(shape))
    } else {
        Box(Modifier.size(size).clip(shape).background(placeholderContainer), contentAlignment = Alignment.Center) {
            Icon(DeskBuddyIcons.MusicNote, contentDescription = null, tint = placeholderContent, modifier = Modifier.size(size / 2))
        }
    }
}

/** Title/artist/album/app for the media tab card; standby has its own dark layout. */
@Composable
fun NowPlayingCard(state: MediaUiState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val np = state.nowPlaying
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NowPlayingArtwork(state.artwork, 160.dp, scheme.primaryContainer, scheme.onPrimaryContainer)
            if (state.isPlaying) {
                LinearWavyProgressIndicator(modifier = Modifier.width(160.dp))
            } else {
                Spacer(Modifier.height(10.dp))
            }
            Text(
                np?.title ?: stringResource(R.string.media_nothing_playing),
                style = MaterialTheme.typography.titleLargeEmphasized,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(np?.artist, np?.album).joinToString(" • ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else if (state.status == PlaybackStatus.NONE) {
                Text(stringResource(R.string.media_nothing_playing_hint), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            np?.appName?.let { app ->
                AssistChip(onClick = {}, label = { Text(app.substringAfterLast('\\').substringBefore('!')) }, shape = MaterialTheme.shapes.small)
            }
        }
    }
}
```

`PlaybackControls.kt`:

```kotlin
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.media

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.media.MediaUiState
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.ui.theme.DeskBuddyIcons

/** Seek slider with elapsed / total labels. Local value while dragging; seeks on release. */
@Composable
fun Timeline(state: MediaUiState, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val duration = state.durationMs
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue.toLong() else state.positionMs
    val seekCd = stringResource(R.string.cd_seek)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Slider(
            value = (if (dragging) dragValue else state.positionMs.toFloat()).coerceIn(0f, maxOf(duration.toFloat(), 1f)),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { dragging = false; onSeek(dragValue.toLong()) },
            valueRange = 0f..maxOf(duration.toFloat(), 1f),
            enabled = state.canSeek,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = seekCd },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val style = MaterialTheme.typography.labelMedium
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Text(formatClock(if (duration > 0) shown else -1), style = style, color = color)
            Text(formatClock(if (duration > 0) duration else -1), style = style, color = color)
        }
    }
}

/** Previous / play-pause (hero) / next as a connected expressive button group. */
@Composable
fun Transport(state: MediaUiState, onMedia: (MediaAction) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        ButtonGroup(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val prev = remember { MutableInteractionSource() }
            val play = remember { MutableInteractionSource() }
            val next = remember { MutableInteractionSource() }
            TonalControl(DeskBuddyIcons.SkipPrevious, R.string.cd_previous, prev, Modifier.animateWidth(prev)) { onMedia(MediaAction.PREVIOUS) }
            HeroControl(
                if (state.isPlaying) DeskBuddyIcons.Pause else DeskBuddyIcons.Play,
                R.string.cd_play_pause, play, Modifier.animateWidth(play),
            ) { onMedia(MediaAction.PLAY_PAUSE) }
            TonalControl(DeskBuddyIcons.SkipNext, R.string.cd_next, next, Modifier.animateWidth(next)) { onMedia(MediaAction.NEXT) }
        }
    }
}

@Composable
private fun HeroControl(icon: ImageVector, cdRes: Int, interaction: MutableInteractionSource, modifier: Modifier, onClick: () -> Unit) {
    val cd = stringResource(cdRes)
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        interactionSource = interaction,
        modifier = modifier.size(IconButtonDefaults.largeContainerSize()).semantics { contentDescription = cd },
    ) { Icon(icon, contentDescription = null, modifier = Modifier.size(IconButtonDefaults.largeIconSize)) }
}

@Composable
private fun TonalControl(icon: ImageVector, cdRes: Int, interaction: MutableInteractionSource, modifier: Modifier, onClick: () -> Unit) {
    val cd = stringResource(cdRes)
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        interactionSource = interaction,
        modifier = modifier.size(IconButtonDefaults.mediumContainerSize()).semantics { contentDescription = cd },
    ) { Icon(icon, contentDescription = null, modifier = Modifier.size(IconButtonDefaults.mediumIconSize)) }
}
```

`VolumePanel.kt`:

```kotlin
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.media

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.media.MediaUiState
import com.saubh.deskbuddy.ui.theme.DeskBuddyIcons

/** Mute toggle + volume slider + output device split button. Slider disabled until the PC reports audio state. */
@Composable
fun VolumePanel(
    state: MediaUiState,
    onVolume: (percent: Int, final: Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onPickDevice: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    val audio = state.audio
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue.toInt() else audio?.volume ?: 0
    val muteCd = stringResource(R.string.cd_mute)
    val volumeCd = stringResource(R.string.cd_volume)
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = containerColor), modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalIconToggleButton(
                    checked = audio?.muted == true,
                    onCheckedChange = { onToggleMute() },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()).semantics { contentDescription = muteCd },
                ) {
                    Icon(if (audio?.muted == true) DeskBuddyIcons.VolumeOff else DeskBuddyIcons.VolumeUp, contentDescription = null, modifier = Modifier.size(IconButtonDefaults.mediumIconSize))
                }
                Slider(
                    value = if (dragging) dragValue else (audio?.volume ?: 0).toFloat(),
                    onValueChange = { dragging = true; dragValue = it; onVolume(it.toInt(), false) },
                    onValueChangeFinished = { dragging = false; onVolume(dragValue.toInt(), true) },
                    valueRange = 0f..100f,
                    enabled = audio != null,
                    modifier = Modifier.weight(1f).semantics { contentDescription = volumeCd },
                )
                Text(stringResource(R.string.media_volume_percent, shown), style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.width(44.dp))
            }
            if (audio != null) {
                val current = state.currentDevice
                SplitButtonLayout(
                    leadingButton = {
                        SplitButtonDefaults.TonalLeadingButton(onClick = onPickDevice, modifier = Modifier.weight(1f, fill = true)) {
                            Icon(DeskBuddyIcons.Speaker, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(current?.name ?: stringResource(R.string.media_output_device), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    trailingButton = {
                        val cd = stringResource(R.string.cd_choose_output)
                        SplitButtonDefaults.TonalTrailingButton(checked = false, onCheckedChange = { onPickDevice() }, modifier = Modifier.semantics { contentDescription = cd }) {
                            Icon(DeskBuddyIcons.ExpandMore, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
```

(If `Modifier.weight` is not available inside `leadingButton`, wrap `SplitButtonLayout` in a `Row` and give the layout `Modifier.fillMaxWidth()` only; the leading button then sizes to content.)

`OutputDeviceSheet.kt`:

```kotlin
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.media

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.protocol.AudioDevice
import com.saubh.deskbuddy.ui.components.SectionTitle
import com.saubh.deskbuddy.ui.theme.DeskBuddyIcons

@Composable
fun OutputDeviceSheet(devices: List<AudioDevice>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle(stringResource(R.string.media_output_device), Modifier.padding(bottom = 8.dp))
            devices.forEach { device ->
                Surface(
                    onClick = { onSelect(device.id); onDismiss() },
                    shape = MaterialTheme.shapes.large,
                    color = if (device.isDefault) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = device.isDefault, onClick = null)
                        Icon(if (device.name.contains("head", ignoreCase = true)) DeskBuddyIcons.Headphones else DeskBuddyIcons.Speaker, contentDescription = null)
                        Text(device.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
```

`ui/media/MediaTab.kt`:

```kotlin
package com.saubh.deskbuddy.ui.media

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.media.MediaUiState
import com.saubh.deskbuddy.protocol.MediaAction

@Composable
fun MediaTab(
    state: MediaUiState,
    isLandscape: Boolean,
    onMedia: (MediaAction) -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Int, Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onSelectDevice: (String) -> Unit,
) {
    var showDevices by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val controls: @Composable ColumnScope.() -> Unit = {
        Timeline(state, onSeek, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
        Transport(state, onMedia, Modifier.fillMaxWidth())
        VolumePanel(state, onVolume, onToggleMute, { showDevices = true }, scheme.surfaceContainerLow, Modifier.fillMaxWidth())
    }
    if (isLandscape) {
        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NowPlayingCard(state, Modifier.weight(1.2f))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), content = controls)
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NowPlayingCard(state, Modifier.fillMaxWidth())
            controls()
        }
    }
    if (showDevices) {
        OutputDeviceSheet(state.audio?.devices.orEmpty(), onSelectDevice) { showDevices = false }
    }
}
```

`RemoteScreen.TabContent` MEDIA branch:

```kotlin
        RemoteTab.MEDIA -> {
            val media by viewModel.media.state.collectAsStateWithLifecycle()
            MediaTab(
                state = media,
                isLandscape = isLandscape,
                onMedia = viewModel::sendMedia,
                onSeek = viewModel.media::seek,
                onVolume = viewModel.media::setVolume,
                onToggleMute = viewModel.media::toggleMute,
                onSelectDevice = viewModel.media::selectDevice,
            )
        }
```

Delete `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/MediaTab.kt`. Strings to add:

```xml
    <string name="media_nothing_playing">Nothing playing</string>
    <string name="media_nothing_playing_hint">Start something on your PC and it will show up here.</string>
    <string name="media_output_device">Output device</string>
    <string name="media_volume_percent">%1$d%%</string>
    <string name="cd_seek">Seek</string>
    <string name="cd_volume">Volume</string>
    <string name="cd_choose_output">Choose output device</string>
```

(`media_playback`, `media_play_pause`, `media_volume` become unused; remove them.)

- [ ] **Step 4: Run tests + build + lint**

Run: `./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:lintDebug`
Expected: PASS / BUILD SUCCESSFUL, lint has no new errors.

- [ ] **Step 5: Commit**

```bash
git add androidApp/src shared/src/commonMain
git commit -m "M3: media tab with now playing artwork, timeline seek, play/pause state, volume slider and output device picker"
```

---

### Task 12: Standby mode (FAB, black pager with shortcuts and media)

**Files:**
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyScreen.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyShortcutsPage.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyMediaPage.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/KeepScreenOn.kt`
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/RemoteScreen.kt` (FAB + `standby` state)
- Modify: `androidApp/build.gradle.kts` (`implementation(libs.androidx.core.ktx)`)
- Modify: `androidApp/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `NowPlayingArtwork`, `Timeline`, `Transport`, `VolumePanel`, `OutputDeviceSheet`, `AppIcon`, `AppsUiState`, `MediaUiState`.
- Produces: `StandbyScreen(viewModel, onExit)`.

- [ ] **Step 1: Implement**

`KeepScreenOn.kt`:

```kotlin
package com.saubh.deskbuddy.ui.standby

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Keeps the screen on and hides system bars while in composition; restores both on dispose. */
@Composable
fun KeepScreenOnImmersive() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
```

`StandbyScreen.kt`:

```kotlin
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.standby

import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.ui.ConnectionViewModel
import com.saubh.deskbuddy.ui.theme.DeskBuddyIcons
import com.saubh.deskbuddy.ui.theme.DeskBuddyTheme
import kotlinx.coroutines.delay
import java.util.Date

val StandbyTile = Color(0xFF141414)

/** Black, always-on remote: page 1 shortcuts, page 2 media. Back or the close button exits. */
@Composable
fun StandbyScreen(viewModel: ConnectionViewModel, onExit: () -> Unit) {
    BackHandler(onBack = onExit)
    KeepScreenOnImmersive()
    val pager = rememberPagerState(pageCount = { 2 })
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val apps by viewModel.apps.state.collectAsStateWithLifecycle()
    val media by viewModel.media.state.collectAsStateWithLifecycle()
    val closeCd = stringResource(R.string.cd_exit_standby)

    DeskBuddyTheme(darkTheme = true) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(rememberClockText(), style = MaterialTheme.typography.displaySmallEmphasized, color = Color.White, modifier = Modifier.weight(1f))
                    FilledTonalIconButton(onClick = onExit, shapes = IconButtonDefaults.shapes(), modifier = Modifier.semantics { contentDescription = closeCd }) {
                        Icon(DeskBuddyIcons.Close, contentDescription = null)
                    }
                }
                HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                    when (page) {
                        0 -> StandbyShortcutsPage(apps, isLandscape, viewModel.apps::launch)
                        else -> StandbyMediaPage(
                            state = media,
                            isLandscape = isLandscape,
                            onMedia = viewModel::sendMedia,
                            onSeek = viewModel.media::seek,
                            onVolume = viewModel.media::setVolume,
                            onToggleMute = viewModel.media::toggleMute,
                            onSelectDevice = viewModel.media::selectDevice,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    repeat(2) { i ->
                        val active = pager.currentPage == i
                        Box(Modifier.size(if (active) 10.dp else 8.dp).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberClockText(): String {
    val context = LocalContext.current
    val format = remember(context) { DateFormat.getTimeFormat(context) }
    var text by remember { mutableStateOf(format.format(Date())) }
    LaunchedEffect(format) {
        while (true) {
            text = format.format(Date())
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    return text
}
```

`StandbyShortcutsPage.kt`:

```kotlin
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.standby

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.apps.AppsUiState
import com.saubh.deskbuddy.protocol.DesktopApp
import com.saubh.deskbuddy.ui.AppIcon
import com.saubh.deskbuddy.ui.components.cookieShape

@Composable
fun StandbyShortcutsPage(state: AppsUiState, isLandscape: Boolean, onLaunch: (String) -> Unit) {
    if (state.shortcuts.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.standby_no_shortcuts), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isLandscape) 5 else 3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.shortcuts, key = { it.id }) { app -> StandbyTileCard(app, state.icons[app.id]) { onLaunch(app.id) } }
    }
}

@Composable
private fun StandbyTileCard(app: DesktopApp, icon: ByteArray?, onLaunch: () -> Unit) {
    val cd = stringResource(R.string.cd_launch, app.name)
    Card(
        onClick = onLaunch,
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(containerColor = StandbyTile, contentColor = Color.White),
        modifier = Modifier.height(128.dp).semantics { contentDescription = cd },
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            AppIcon(app.name, icon, 64.dp, cookieShape(), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            Text(app.name, style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}
```

`StandbyMediaPage.kt`:

```kotlin
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.standby

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.media.MediaUiState
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.ui.media.NowPlayingArtwork
import com.saubh.deskbuddy.ui.media.OutputDeviceSheet
import com.saubh.deskbuddy.ui.media.Timeline
import com.saubh.deskbuddy.ui.media.Transport
import com.saubh.deskbuddy.ui.media.VolumePanel

@Composable
fun StandbyMediaPage(
    state: MediaUiState,
    isLandscape: Boolean,
    onMedia: (MediaAction) -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Int, Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onSelectDevice: (String) -> Unit,
) {
    var showDevices by rememberSaveable { mutableStateOf(false) }
    val art: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NowPlayingArtwork(state.artwork, if (isLandscape) 200.dp else 240.dp, StandbyTile, Color.White.copy(alpha = 0.6f))
            Text(state.nowPlaying?.title ?: stringResource(R.string.media_nothing_playing), style = MaterialTheme.typography.headlineSmallEmphasized, color = Color.White, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val subtitle = listOfNotNull(state.nowPlaying?.artist, state.nowPlaying?.album).joinToString(" • ")
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    val controls: @Composable ColumnScope.() -> Unit = {
        Timeline(state, onSeek, Modifier.fillMaxWidth())
        Transport(state, onMedia, Modifier.fillMaxWidth())
        VolumePanel(state, onVolume, onToggleMute, { showDevices = true }, StandbyTile, Modifier.fillMaxWidth())
    }
    if (isLandscape) {
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { art() }
            Column(Modifier.weight(1.2f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), content = controls)
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            art()
            controls()
        }
    }
    if (showDevices) OutputDeviceSheet(state.audio?.devices.orEmpty(), onSelectDevice) { showDevices = false }
}
```

`RemoteScreen.kt`: add `var standby by rememberSaveable { mutableStateOf(false) }`; if `standby`, render `StandbyScreen(viewModel) { standby = false }` and return; otherwise the existing `Scaffold` gains:

```kotlin
        floatingActionButton = {
            MediumExtendedFloatingActionButton(onClick = { standby = true }) {
                Icon(DeskBuddyIcons.Nightlight, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.standby))
            }
        },
```

Strings: `standby` = "Standby", `cd_exit_standby` = "Exit standby", `standby_no_shortcuts` = "No shortcuts yet. Star apps on the Apps tab to see them here."

`androidApp/build.gradle.kts`: `implementation(libs.androidx.core.ktx)`.

- [ ] **Step 2: Build + lint**

Run: `./gradlew :androidApp:assembleDebug :androidApp:lintDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add androidApp
git commit -m "M3: standby mode — black always-on pager with app-icon shortcuts and media controls"
```

---

### Task 13: Full verification, docs, changelog

**Files:**
- Modify: `plan/pc-remote-control-app-requirements.md` (mark now-playing display, output device, standby as in scope / done)
- Modify: `2026_Saubhagya_changelog.md` (one grouped M3 entry at the top)

- [ ] **Step 1: Run the full verification set**

Run: `./gradlew :shared:jvmTest :shared:testAndroidHostTest :shared:compileKotlinJs :shared:compileKotlinWasmJs :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:lintDebug :desktopApp:build`
Expected: BUILD SUCCESSFUL; note test counts.

- [ ] **Step 2: Manual smoke on this PC**

Run the desktop app (`./gradlew :desktopApp:run`) with music playing; confirm the status chip says "Now playing: on"; install the debug APK on the phone; confirm the PC appears without typing an IP (turn Wi-Fi off/on on the phone to force a re-discovery), artwork and timeline move, seek works, volume slider moves the Windows volume, output device switch takes effect, standby FAB shows the black pager with icons.

- [ ] **Step 3: Write the changelog entry and update the requirements doc**

Entry format per CLAUDE.md, listing every file from Tasks 1–12, `Build Verified` with the exact command from Step 1 and its result.

- [ ] **Step 4: Commit**

```bash
git add 2026_Saubhagya_changelog.md plan/pc-remote-control-app-requirements.md docs/superpowers/plans/2026-09-23-deskbuddy-m3.md
git commit -m "M3: changelog, requirements update and plan"
```
