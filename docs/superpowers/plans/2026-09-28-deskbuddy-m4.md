# DeskBuddy Milestone 4 Implementation Plan — Performance Pass + Shared Keyboard

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the measured performance costs from review Tier 3 (P1–P15), then let the phone type into the PC's focused window and the PC's keyboard (Ctrl+Alt+K) type into the phone's focused app.

**Architecture:** Part A changes internals only (caches, dispatchers, recomposition scope, battery gating, R8). Part B adds three protocol commands (`KeyTextCommand`, `KeyPressCommand`, `KeyboardModeCommand`). The desktop injects keys with Win32 `SendInput` (or `xdotool` on Linux). A registered hotkey opens a focus-taking overlay whose AWT key events are translated and pushed to the phone. On the phone, a Keyboard tab streams text diffs, and a DeskBuddy Keyboard (IME) commits pushed text into any app.

**Tech Stack:** Kotlin 2.4.10 Multiplatform, Compose Multiplatform 1.11.1 + Material 3 expressive, AGP 9.0.1 (built-in Kotlin, `src/*/kotlin`), Ktor 3.3.1 WebSockets, kotlinx.serialization, JNA 5.17 (User32/Kernel32), Android `InputMethodService`, JUnit4 + kotlin-test + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-28-deskbuddy-m4-keyboard-performance-design.md`

## Global Constraints

- Files stay under 400 lines; one top-level class per file; no files in the root package.
- Android: no hardcoded user-visible strings in Kotlin; all copy in `androidApp/src/main/res/values/strings.xml`. (The desktop app already uses inline strings; keep that style there.)
- No `!!` without a justifying comment; no raw exception text shown in any UI.
- No Hilt; wire new classes in `AppGraph` (Android) / `DesktopController` (desktop) like existing ones (spec A6).
- One paired phone assumed; PC keystrokes are broadcast with `ControlServer.push` (spec A1).
- Windows full support; Linux: `xdotool` for phone→PC, tray item only for PC→phone; Wayland unsupported (A2).
- Hotkey fixed at **Ctrl+Alt+K** (A3). Win key not forwarded PC→phone (A4).
- Keystroke commands are **silent on success**; `UNSUPPORTED_OS` is reported **once per connection**; `KeyTextCommand.text` > **4096** chars → `INTERNAL`.
- Test names: `methodName_givenCondition_expectedBehavior`. commonTest names are plain identifiers (no backticks: JS/Wasm reject spaces).
- The repo has no test sources besides `shared/src/jvmTest/.../TestFixtures.kt`; every test here is new. Screenshot validation is not in the gate (no references exist).
- Every task commits; the changelog entry `2026_Saubhagya_changelog.md` (one grouped M4 entry, newest first) is written in Task 17.
- Final gate: `./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug :desktopApp:build`.

## Review Focus

1. **Phone on cellular with a USB (`adb reverse`) PC saved at 127.0.0.1**: P12's "probe only on Wi-Fi/Ethernet" must still probe loopback targets. Test: Task 7 `reachable_givenOffLan_keepsOnlyLoopback`.
2. **Emoji and other surrogate pairs typed or autocorrected on the phone**: the diff must never split a surrogate pair, and Backspace counts are per code point (one PC Backspace deletes one emoji). Test: Task 13 `between_givenEmojiReplaced_doesNotSplitSurrogates`.
3. **AltGr layouts (German `@` = AltGr+Q, reported by Windows as Ctrl+Alt)**: must arrive on the phone as the text `@`, not as the combo Ctrl+Alt+Q. Test: Task 11 `translate_givenAltGrCharacter_sendsText`.
4. **Phone drops while the PC overlay is active**: the PC leaves typing mode showing "Phone disconnected", and the phone's hub resets `pcTyping` so the IME switches back. Tests: Task 11 `phoneDisconnected_givenActive_exitsWithNotice`, Task 15 `pcTyping_givenDisconnect_resetsToFalse`.
5. **Typing against a PC whose OS is unsupported**: many keystrokes must produce one error snackbar, not one per key. Test: Task 9 `keyText_givenUnsupportedOs_reportsOnlyOnce`.

---

# Part A — Performance (spec §5)

### Task 1: In-memory paired-device store (P1) + first jvm tests

**Files:**
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/PairedDeviceStore.kt`
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ControlSession.kt:110-115`
- Modify: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/TestFixtures.kt`
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/PairedDeviceStoreTest.kt`
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/ControlSessionTest.kt`

**Interfaces:**
- Produces: `PairedDeviceStore` (same public API: `load()`, `add()`, `isValidToken()`, `deviceNameForToken()`, `removeAll()`), now memory-backed. `RecordingListener` test fixture (`authenticated: List<String>`).

- [ ] **Step 1: Add the listener fixture** — append to `TestFixtures.kt`:

```kotlin
class RecordingListener : SessionListener {
    val authenticated = mutableListOf<String>()
    override fun onPairingStarted(pin: String) = Unit
    override fun onPairingEnded() = Unit
    override fun onAuthenticated(deviceName: String, token: String) {
        authenticated += deviceName
    }
}
```

- [ ] **Step 2: Write the failing tests**

`PairedDeviceStoreTest.kt`:

```kotlin
package com.saubh.deskbuddy.server

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PairedDeviceStoreTest {
    private val file = Files.createTempDirectory("deskbuddy-paired").resolve("paired.json")

    @Test
    fun `deviceNameForToken_givenFileDeletedAfterFirstRead_servesFromMemory`() {
        PairedDeviceStore(file).add(PairedDevice("Pixel", "tok"))
        val store = PairedDeviceStore(file)
        assertEquals("Pixel", store.deviceNameForToken("tok"))
        Files.delete(file)
        assertEquals("Pixel", store.deviceNameForToken("tok"))
    }

    @Test
    fun `removeAll_givenCachedDevices_forgetsThem`() {
        val store = PairedDeviceStore(file)
        store.add(PairedDevice("Pixel", "tok"))
        store.removeAll()
        assertNull(store.deviceNameForToken("tok"))
    }

    @Test
    fun `add_givenExistingToken_replacesEntryAndPersists`() {
        val store = PairedDeviceStore(file)
        store.add(PairedDevice("Old", "tok"))
        store.add(PairedDevice("New", "tok"))
        assertEquals(listOf(PairedDevice("New", "tok")), PairedDeviceStore(file).load())
    }
}
```

`ControlSessionTest.kt`:

```kotlin
package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.Ack
import com.saubh.deskbuddy.protocol.Envelope
import com.saubh.deskbuddy.protocol.ErrorCode
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.MediaCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ControlSessionTest {
    private val store = tempDeviceStore().apply { add(PairedDevice("Pixel", "tok")) }
    private val listener = RecordingListener()
    private val session = ControlSession(store, testActuators(), listener)

    @Test
    fun `envelope_givenUnknownToken_repliesAuthRequired`() {
        val reply = session.onMessage(Envelope("nope", MediaCommand(MediaAction.NEXT)))
        assertEquals(Ack(ok = false, error = ErrorCode.AUTH_REQUIRED), reply.message)
        assertTrue(listener.authenticated.isEmpty())
    }

    @Test
    fun `envelope_givenPairedToken_executesAndReportsDeviceName`() {
        val reply = session.onMessage(Envelope("tok", MediaCommand(MediaAction.NEXT)))
        assertEquals(Ack(ok = true), reply.message)
        assertEquals(listOf("Pixel"), listener.authenticated)
    }
}
```

- [ ] **Step 3: Run to verify the store test fails**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.PairedDeviceStoreTest"`
Expected: FAIL — `deviceNameForToken_givenFileDeletedAfterFirstRead_servesFromMemory` gets `null` (the file is re-read on every call).

- [ ] **Step 4: Implement** — replace the body of `PairedDeviceStore`:

```kotlin
class PairedDeviceStore(
    private val file: Path = Path.of(System.getProperty("user.home"), ".deskbuddy", "paired.json"),
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PairedDevice.serializer())
    private val lock = Any()

    /** Read once, then served from memory: the token is checked on every message, including file chunks and keystrokes. */
    @Volatile
    private var cache: List<PairedDevice>? = null

    fun load(): List<PairedDevice> = cache ?: synchronized(lock) { cache ?: readFile().also { cache = it } }

    fun add(device: PairedDevice) = synchronized(lock) { save(load().filter { it.token != device.token } + device) }

    fun isValidToken(token: String): Boolean = deviceNameForToken(token) != null

    fun deviceNameForToken(token: String): String? = load().firstOrNull { it.token == token }?.deviceName

    fun removeAll() = synchronized(lock) {
        Files.deleteIfExists(file)
        cache = emptyList()
    }

    private fun readFile(): List<PairedDevice> =
        if (Files.exists(file)) {
            runCatching { json.decodeFromString(serializer, Files.readString(file)) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

    private fun save(devices: List<PairedDevice>) {
        Files.createDirectories(file.parent)
        Files.writeString(file, json.encodeToString(serializer, devices))
        cache = devices
    }
}
```

In `ControlSession.handleEnvelope`, replace the first four lines with one lookup:

```kotlin
    private fun handleEnvelope(envelope: Envelope): SessionReply {
        val deviceName = store.deviceNameForToken(envelope.token)
            ?: return SessionReply(Ack(ok = false, error = ErrorCode.AUTH_REQUIRED))
        listener.onAuthenticated(deviceName, envelope.token)
        return try {
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :shared:jvmTest`
Expected: PASS (5 tests). This also clears the baseline "no tests discovered" failure.

- [ ] **Step 6: Commit**

```bash
git add shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/PairedDeviceStore.kt shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ControlSession.kt shared/src/jvmTest
git commit -m "perf: serve paired-device tokens from memory instead of reading paired.json per message"
```

---

### Task 2: Cached app catalog for phone requests (P2)

**Files:**
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ShortcutStore.kt`
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/AppCatalogService.kt`
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ControlSession.kt` (`AppCatalogRequestCommand` branch)
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/AppCatalogServiceTest.kt`

**Interfaces:**
- Produces: `AppCatalogService(apps, store, iconFor = { null }, clock: () -> Long = System::currentTimeMillis, staleMs: Long = 30_000L, background: (() -> Unit) -> Unit = <daemon thread>)` and `fun forPhone(): AppCatalog`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.DesktopApp
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class AppCatalogServiceTest {
    private class CountingApps : AppActuator {
        var scans = 0
        var apps = listOf(DesktopApp("a", "Alpha"))
        override fun installedApps(): List<DesktopApp> { scans++; return apps }
        override fun launch(app: DesktopApp) = Unit
    }

    private val dir = Files.createTempDirectory("deskbuddy-catalog")
    private val apps = CountingApps()
    private var now = 0L
    private val service = AppCatalogService(
        apps, ShortcutStore(dir.resolve("shortcuts.json")),
        clock = { now }, staleMs = 30_000L, background = { it() },
    )

    @Test
    fun `forPhone_givenFreshScan_servesCacheWithoutRescan`() {
        service.forPhone()
        now = 10_000L
        service.forPhone()
        assertEquals(1, apps.scans)
    }

    @Test
    fun `forPhone_givenStaleScan_rescansInBackgroundAndPublishesChange`() {
        service.forPhone()
        apps.apps = apps.apps + DesktopApp("b", "Beta")
        now = 31_000L
        service.forPhone()
        assertEquals(2, apps.scans)
        assertEquals(listOf("Alpha", "Beta"), service.catalog.value.apps.map { it.name })
    }

    @Test
    fun `shortcutStore_givenFileDeletedAfterWrite_servesFromMemory`() {
        val file = dir.resolve("s.json")
        val store = ShortcutStore(file)
        store.add(DesktopApp("a", "Alpha"))
        Files.delete(file)
        assertEquals(listOf(DesktopApp("a", "Alpha")), store.load())
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.AppCatalogServiceTest"`
Expected: compile FAIL (`clock`, `staleMs`, `background` and `forPhone` do not exist).

- [ ] **Step 3: Implement**

`ShortcutStore` — same pattern as Task 1:

```kotlin
    private val lock = Any()

    @Volatile
    private var cache: List<DesktopApp>? = null

    fun load(): List<DesktopApp> = cache ?: synchronized(lock) { cache ?: readFile().also { cache = it } }

    fun add(app: DesktopApp) = synchronized(lock) { save(load().filter { it.id != app.id } + app) }

    fun remove(id: String) = synchronized(lock) { save(load().filter { it.id != id }) }

    private fun readFile(): List<DesktopApp> =
        if (Files.exists(file)) {
            runCatching { json.decodeFromString(serializer, Files.readString(file)) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

    private fun save(apps: List<DesktopApp>) {
        Files.createDirectories(file.parent)
        Files.writeString(file, json.encodeToString(serializer, apps))
        cache = apps
    }
```

`AppCatalogService` — constructor and new members (add imports `java.util.concurrent.atomic.AtomicBoolean`, `kotlin.concurrent.thread`):

```kotlin
class AppCatalogService(
    private val apps: AppActuator,
    private val store: ShortcutStore,
    /** Base64 PNG for a shortcut app, or null when none can be produced. Only asked for shortcuts. */
    private val iconFor: (DesktopApp) -> String? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
    /** A phone request older than this triggers a background rescan (Get-StartApps costs 1–3 s). */
    private val staleMs: Long = 30_000L,
    private val background: (() -> Unit) -> Unit = { task -> thread(isDaemon = true, name = "deskbuddy-catalog-scan") { task() } },
) {
    // ... existing _catalog, catalog, installed ...

    @Volatile
    private var scannedAtMs = 0L
    private val rescanning = AtomicBoolean(false)

    /** Re-scans installed apps and returns the fresh catalog. */
    @Synchronized
    fun refresh(): AppCatalog {
        installed = runCatching { apps.installedApps() }.getOrDefault(emptyList())
        scannedAtMs = clock()
        return rebuild()
    }

    /**
     * Phone requests: the cached catalog at once. A stale cache is rescanned in the background;
     * a changed catalog then reaches the phone through the existing catalog push.
     */
    fun forPhone(): AppCatalog {
        synchronized(this) { if (installed == null) return refresh() }
        if (clock() - scannedAtMs >= staleMs && rescanning.compareAndSet(false, true)) {
            background {
                try {
                    refresh()
                } finally {
                    rescanning.set(false)
                }
            }
        }
        return catalog.value
    }
```

In `ControlSession.execute`: `is AppCatalogRequestCommand -> actuators.apps.forPhone()`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :shared:jvmTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server shared/src/jvmTest
git commit -m "perf: answer phone catalog requests from cache, rescan stale catalog in background"
```

---

### Task 3: Clipboard sequence gating (P4) + single IO context in FileSender (P6)

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/WindowsClipboardSequence.kt`
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ClipboardWatcher.kt`
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/FileSender.kt`
- Modify: `desktopApp/src/main/kotlin/com/saubh/deskbuddy/DesktopController.kt` (`ClipboardWatcher(...)` call in `start()`)
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/ClipboardWatcherTest.kt`
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/FileSenderTest.kt`

**Interfaces:**
- Produces: `ClipboardWatcher(share, intervalMillis, changeToken: () -> Long? = { null })`; `WindowsClipboardSequence.current(): Long?`.

- [ ] **Step 1: Write the failing tests**

`ClipboardWatcherTest.kt`:

```kotlin
package com.saubh.deskbuddy.server

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals

class ClipboardWatcherTest {
    private class CountingShare(var text: String?) : ShareActuator {
        var reads = 0
        override val lastWrittenText: String? = null
        override fun setClipboard(text: String) = Unit
        override fun getClipboard(): String? { reads++; return text }
        override fun onTextShared(text: String) = Unit
    }

    @Test
    fun `changes_givenUnchangedSequence_skipsClipboardReads`() = runBlocking {
        val share = CountingShare("a")
        val watcher = ClipboardWatcher(share, intervalMillis = 5, changeToken = { 7L })
        withTimeoutOrNull(100) { watcher.changes().first() }
        assertEquals(1, share.reads) // the initial read only
    }

    @Test
    fun `changes_givenSequenceBump_readsAndEmitsNewText`() = runBlocking {
        val share = CountingShare("a")
        var seq = 1L
        val watcher = ClipboardWatcher(share, intervalMillis = 5, changeToken = { seq })
        val emitted = async { withTimeout(1_000) { watcher.changes().first() } }
        delay(30)
        share.text = "b"
        seq = 2L
        assertEquals("b", emitted.await())
    }
}
```

`FileSenderTest.kt`:

```kotlin
package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.FileChunkCommand
import com.saubh.deskbuddy.protocol.FileCompleteCommand
import com.saubh.deskbuddy.protocol.FileOfferCommand
import com.saubh.deskbuddy.protocol.Message
import com.saubh.deskbuddy.protocol.Push
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileSenderTest {
    @Test
    fun `send_givenSeventyKilobyteFile_pushesOfferThreeChunksAndComplete`() = runBlocking {
        val file = Files.createTempFile("deskbuddy", ".bin").also { Files.write(it, ByteArray(70 * 1024) { 1 }) }
        val pushed = mutableListOf<Message>()
        val sent = FileSender { pushed += it; true }.send(file)
        val commands = pushed.map { (it as Push).command }
        assertTrue(sent)
        assertTrue(commands.first() is FileOfferCommand)
        assertEquals(listOf(0, 1, 2), commands.filterIsInstance<FileChunkCommand>().map { it.index })
        assertTrue(commands.last() is FileCompleteCommand)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.ClipboardWatcherTest"`
Expected: compile FAIL (no `changeToken` parameter). (`FileSenderTest` already passes; it is the regression guard for Step 3.)

- [ ] **Step 3: Implement**

`ClipboardWatcher`:

```kotlin
class ClipboardWatcher(
    private val share: ShareActuator,
    private val intervalMillis: Long = Protocol.CLIPBOARD_POLL_MILLIS,
    /** Cheap "clipboard changed" counter (Windows sequence number); null means unknown, so read every tick. */
    private val changeToken: () -> Long? = { null },
) {
    fun changes(): Flow<String> = flow {
        var token = changeToken()
        var last = share.getClipboard()
        while (true) {
            delay(intervalMillis)
            val now = changeToken()
            if (now != null && now == token) continue
            token = now
            val current = share.getClipboard() ?: continue
            if (current == last) continue
            last = current
            if (current != share.lastWrittenText) emit(current)
        }
    }
}
```

`WindowsClipboardSequence.kt`:

```kotlin
package com.saubh.deskbuddy.server

import com.sun.jna.Native
import com.sun.jna.win32.StdCallLibrary

/** `GetClipboardSequenceNumber`: bumps on every clipboard change, so the watcher only reads text when it moved. */
object WindowsClipboardSequence {
    internal interface User32Clipboard : StdCallLibrary {
        fun GetClipboardSequenceNumber(): Int
    }

    private val user32: User32Clipboard? by lazy {
        runCatching { Native.load("user32", User32Clipboard::class.java) }.getOrNull()
    }

    fun current(): Long? = user32?.GetClipboardSequenceNumber()?.toLong()?.and(0xFFFF_FFFFL)
}
```

`DesktopController.start()`:

```kotlin
        scope.launch {
            val watcher = ClipboardWatcher(
                actuators.share,
                changeToken = if (isWindows) WindowsClipboardSequence::current else { { null } },
            )
            watcher.changes().collect { text ->
                if (autoSyncClipboard.value) server.push(Push(ClipboardCommand(text)))
            }
        }
```

(add `import com.saubh.deskbuddy.server.WindowsClipboardSequence`).

`FileSender.send` — one IO context around the whole read loop:

```kotlin
        return try {
            withContext(Dispatchers.IO) {
                Files.newInputStream(file).use { input ->
                    val buffer = ByteArray(Protocol.FILE_CHUNK_BYTES)
                    var index = 0
                    var sent = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                        if (!push(Push(FileChunkCommand(id, index++, FileChunkCodec.encode(chunk))))) {
                            throw IOException("phone disconnected")
                        }
                        sent += read
                        onProgress(Progress(name, sent, size))
                    }
                }
            }
            push(Push(FileCompleteCommand(id)))
        } catch (e: CancellationException) {
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :shared:jvmTest :desktopApp:compileKotlin`
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add shared/src desktopApp/src
git commit -m "perf: read desktop clipboard only when its sequence number changes; one IO hop per file send"
```

---

### Task 4: One COM hop for volume + mute with a cached endpoint (P5)

**Files:**
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/AudioActuator.kt`
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/audio/WindowsAudioActuator.kt`
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/media/MediaStateBroadcaster.kt` (`snapshot`)
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/media/MediaStateBroadcasterTest.kt`
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/audio/WindowsAudioActuatorTest.kt`

**Interfaces:**
- Produces: `data class AudioLevels(val volume: Int, val muted: Boolean)`; `AudioActuator.levels(): AudioLevels` (default = `AudioLevels(volume(), isMuted())`).

- [ ] **Step 1: Write the failing tests**

`MediaStateBroadcasterTest.kt`:

```kotlin
package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.protocol.AudioDevice
import com.saubh.deskbuddy.protocol.AudioStateCommand
import com.saubh.deskbuddy.server.AudioActuator
import com.saubh.deskbuddy.server.AudioLevels
import com.saubh.deskbuddy.server.UnsupportedMediaSessionActuator
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaStateBroadcasterTest {
    private class LevelsOnlyAudio : AudioActuator {
        var levelsCalls = 0
        override fun devices() = listOf(AudioDevice("d", "Speakers", isDefault = true))
        override fun volume(): Int = error("use levels()")
        override fun isMuted(): Boolean = error("use levels()")
        override fun setVolume(percent: Int) = Unit
        override fun setDefaultDevice(id: String) = true
        override fun levels(): AudioLevels { levelsCalls++; return AudioLevels(40, muted = false) }
    }

    @Test
    fun `commands_givenAudioActuator_readsVolumeAndMuteInOneCall`() = runBlocking {
        val audio = LevelsOnlyAudio()
        val broadcaster = MediaStateBroadcaster(UnsupportedMediaSessionActuator(), audio, volumePollMs = 10)
        val state = withTimeout(1_000) { broadcaster.commands().filterIsInstance<AudioStateCommand>().first() }
        // volume()/isMuted() throw here, so an emitted state proves levels() was used instead.
        assertEquals(40, state.volume)
        assertTrue(audio.levelsCalls >= 1)
    }
}
```

`WindowsAudioActuatorTest.kt` (Windows smoke test, skipped elsewhere):

```kotlin
package com.saubh.deskbuddy.server.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsAudioActuatorTest {
    @Test
    fun `levels_onWindows_matchesSeparateReads`() {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        val audio = WindowsAudioActuator()
        val separate = runCatching { audio.volume() to audio.isMuted() }.getOrNull() ?: return // no audio endpoint
        val levels = audio.levels()
        assertEquals(separate.first, levels.volume)
        assertEquals(separate.second, levels.muted)
        assertEquals(levels, audio.levels()) // second call reuses the cached endpoint
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.media.MediaStateBroadcasterTest"`
Expected: compile FAIL (`AudioLevels` does not exist).

- [ ] **Step 3: Implement**

`AudioActuator.kt`:

```kotlin
data class AudioLevels(val volume: Int, val muted: Boolean)

/** Output (render) endpoints and the master volume of the current default one. */
interface AudioActuator {
    fun devices(): List<AudioDevice>
    /** 0..100 */
    fun volume(): Int
    fun setVolume(percent: Int)
    fun isMuted(): Boolean
    /** False when [id] is not a known render endpoint. */
    fun setDefaultDevice(id: String): Boolean
    /** Volume and mute together; implementations may read both in one call. */
    fun levels(): AudioLevels = AudioLevels(volume(), isMuted())
}
```

`MediaStateBroadcaster.snapshot`:

```kotlin
    private fun snapshot(devices: List<AudioDevice>): AudioStateCommand? =
        runCatching { audio.levels().let { AudioStateCommand(devices, it.volume, it.muted) } }.getOrNull()
```

`WindowsAudioActuator` — add the cache, `levels()`, and invalidation (import `com.saubh.deskbuddy.server.AudioLevels`):

```kotlin
    /** Endpoint volume of the default device, reused across polls. Touched only on the COM thread. */
    private var endpoint: ComObject? = null
    private var endpointDeviceId: String? = null

    override fun levels(): AudioLevels = onComThread {
        withEndpointVolume { ep ->
            val scalar = FloatByReference().also { ep.check(Ids.GET_MASTER_VOLUME_SCALAR, it) }.value
            val muted = IntByReference().also { ep.check(Ids.GET_MUTE, it) }.value != 0
            AudioLevels((scalar * 100f).roundToInt().coerceIn(0, 100), muted)
        }
    }
```

In `devices()`, right after `val defaultId = ...`: `if (defaultId != endpointDeviceId) dropEndpoint()` (the user switched output on the PC; picked up by the 5 s device poll).
In `setDefaultDevice`, before the final `true`: `dropEndpoint()`.
Replace `withEndpointVolume` and add the helpers:

```kotlin
    private fun <T> withEndpointVolume(block: (ComObject) -> T): T {
        val ep = endpoint ?: openDefaultEndpoint().also { endpoint = it }
        return try {
            block(ep)
        } catch (e: Exception) {
            dropEndpoint()
            throw e
        }
    }

    private fun openDefaultEndpoint(): ComObject = enumerator().use { enumerator ->
        enumerator.child(Ids.GET_DEFAULT_AUDIO_ENDPOINT, Ids.eRender, Ids.eMultimedia).use { device ->
            endpointDeviceId = deviceId(device)
            device.child(Ids.DEVICE_ACTIVATE, Ids.IID_IAudioEndpointVolume.pointer, Ids.CLSCTX_ALL, null)
        }
    }

    private fun dropEndpoint() {
        endpoint?.close()
        endpoint = null
        endpointDeviceId = null
    }
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :shared:jvmTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src
git commit -m "perf: read volume and mute in one COM call with a cached endpoint"
```

---

### Task 5: Desktop work off the UI thread, fast quit, JVM flags, parallel Gradle (P3, P7, P8)

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/saubh/deskbuddy/DesktopController.kt` (`start`, `stop`, `refreshCatalog`, `addShortcut`, `addCustomShortcut`, `removeShortcut`)
- Modify: `desktopApp/src/main/kotlin/com/saubh/deskbuddy/main.kt`
- Modify: `desktopApp/build.gradle.kts`
- Modify: `gradle.properties`

**Interfaces:**
- Produces: `suspend fun DesktopController.stop()`; `refreshCatalog()` now returns `Unit`.

- [ ] **Step 1: Move blocking controller work to IO** — in `DesktopController` (imports `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.withContext`):

```kotlin
    fun start() {
        server.start()
        // Slow first-run work (registry, one JmDNS per adapter, Get-StartApps) must not delay the tray.
        scope.launch(Dispatchers.IO) {
            if (isWindows) _startWithWindows.value = runCatching { startup.sync() }.getOrDefault(false)
            // mDNS failure must not kill the app — manual IP entry still works.
            runCatching { advertiser.start() }
            actuators.apps.current()
        }
        // ... the rest of start() unchanged
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        transferJob?.cancel()
        actuators.mediaSession.stop()
        advertiser.stop()
        server.stop()
    }

    fun refreshCatalog() {
        scope.launch(Dispatchers.IO) { actuators.apps.refresh() }
    }

    fun addShortcut(id: String) {
        scope.launch(Dispatchers.IO) { actuators.apps.addShortcut(id) }
    }

    fun addCustomShortcut(path: Path) {
        scope.launch(Dispatchers.IO) { actuators.apps.addCustom(path) }
    }

    fun removeShortcut(id: String) {
        scope.launch(Dispatchers.IO) { actuators.apps.removeShortcut(id) }
    }
```

- [ ] **Step 2: Quit without freezing** — in `main.kt` (imports `androidx.compose.runtime.rememberCoroutineScope`, `kotlinx.coroutines.launch`, `kotlinx.coroutines.withTimeoutOrNull`):

```kotlin
    application {
        var visible by remember { mutableStateOf(!background) }
        var quitting by remember { mutableStateOf(false) }
        val uiScope = rememberCoroutineScope()
        val windowState = rememberWindowState(width = 960.dp, height = 640.dp)
        val show = {
            visible = true
            windowState.isMinimized = false
        }
        val quit: () -> Unit = {
            if (!quitting) {
                quitting = true // tray icon and window disappear at once
                uiScope.launch {
                    withTimeoutOrNull(3_000) { controller.stop() }
                    scope.cancel()
                    exitApplication()
                }
            }
        }
        val appIcon = rememberAppIconPainter()
        LaunchedEffect(Unit) { controller.showRequests.collect { show() } }

        if (!quitting) {
            Tray(/* unchanged */)
            Window(/* unchanged */) { /* unchanged */ }
        }
    }
```

- [ ] **Step 3: JVM flags + runtime modules** — in `desktopApp/build.gradle.kts` inside `compose.desktop { application { ... } }`:

```kotlin
        // Tray-resident app: cap the heap and use the low-footprint collector.
        jvmArgs += listOf("-Xms32m", "-Xmx256m", "-XX:+UseSerialGC")
```

and inside `nativeDistributions { ... }` (list from `./gradlew :desktopApp:suggestRuntimeModules`, run 2026-09-28):

```kotlin
            modules("java.instrument", "java.management", "jdk.unsupported")
```

- [ ] **Step 4: Parallel Gradle** — append to `gradle.properties` under `#Gradle`: `org.gradle.parallel=true`

- [ ] **Step 5: Verify**

Run: `./gradlew :desktopApp:build`
Expected: BUILD SUCCESSFUL.
Manual: `./gradlew :desktopApp:run` → the window appears without waiting for the scan; Shortcuts → Rescan keeps the window responsive; tray Quit exits within 3 s.

- [ ] **Step 6: Commit**

```bash
git add desktopApp gradle.properties
git commit -m "perf: keep desktop UI thread free of catalog, mDNS and shutdown work; cap JVM heap"
```

---

### Task 6: Media recomposition, media-session metadata, transfer progress (P9, P10, P11)

**Files:**
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/media/MediaUiState.kt`
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/media/MediaController.kt`
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/media/PlaybackControls.kt` (`Timeline`)
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/service/PcMediaSession.kt`
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/share/FileTransferController.kt`
- Create (test): `androidApp/src/test/kotlin/com/saubh/deskbuddy/FakeGateway.kt`
- Test: `androidApp/src/test/kotlin/com/saubh/deskbuddy/media/MediaControllerTest.kt`

**Interfaces:**
- Produces: `MediaUiState.positionAt(nowMs: Long): Long` (the `positionMs` field is removed). `FakeGateway : MediaGateway` for all later Android tests: `incoming: MutableSharedFlow<Message>`, `connectedState: MutableStateFlow<Boolean>`, `sent: List<Command>`.

- [ ] **Step 1: Create the fake gateway**

```kotlin
package com.saubh.deskbuddy

import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Message
import com.saubh.deskbuddy.session.MediaGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeGateway : MediaGateway {
    override val incoming = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val connectedState = MutableStateFlow(true)
    override val connected: Flow<Boolean> = connectedState
    val sent = mutableListOf<Command>()
    override suspend fun send(command: Command): Boolean {
        sent += command
        return true
    }
}
```

- [ ] **Step 2: Write the failing tests**

```kotlin
package com.saubh.deskbuddy.media

import com.saubh.deskbuddy.FakeGateway
import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus
import com.saubh.deskbuddy.protocol.Push
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MediaControllerTest {
    private val playing = NowPlayingCommand(title = "Song", status = PlaybackStatus.PLAYING, positionMs = 1_000, durationMs = 60_000, canSeek = true)

    private fun TestScope.controller(gateway: FakeGateway) =
        MediaController(gateway, backgroundScope, clock = { currentTime }, decodeDispatcher = StandardTestDispatcher(testScheduler))
            .also { runCurrent() }

    @Test
    fun `state_givenPlayingAndNoPushes_doesNotEmitOverTime`() = runTest {
        val gateway = FakeGateway()
        val controller = controller(gateway)
        gateway.incoming.emit(Push(playing))
        runCurrent()
        var emissions = 0
        backgroundScope.launch { controller.state.drop(1).collect { emissions++ } }
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, emissions)
    }

    @Test
    fun `positionAt_givenPlaying_interpolatesFromPush`() = runTest {
        val gateway = FakeGateway()
        val controller = controller(gateway)
        gateway.incoming.emit(Push(playing))
        runCurrent()
        val pushedAt = currentTime
        assertEquals(3_000, controller.state.value.positionAt(pushedAt + 2_000))
    }

    @Test
    fun `seek_givenTarget_rebasesPositionAtSeekTime`() = runTest {
        val gateway = FakeGateway()
        val controller = controller(gateway)
        gateway.incoming.emit(Push(playing))
        runCurrent()
        advanceTimeBy(10_000)
        controller.seek(30_000)
        assertEquals(30_000, controller.state.value.positionAt(currentTime))
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.saubh.deskbuddy.media.MediaControllerTest"`
Expected: compile FAIL (`positionAt` does not exist).

- [ ] **Step 4: Implement**

`MediaUiState`: delete the `positionMs` property and its KDoc, and add (import `com.saubh.deskbuddy.media.PlaybackClock` is the same package in shared, `com.saubh.deskbuddy.media`):

```kotlin
    /** Where playback is at [nowMs] (wall clock), interpolated from the last push while playing. */
    fun positionAt(nowMs: Long): Long =
        PlaybackClock.positionAt(nowMs, receivedAtMs, nowPlaying?.positionMs ?: 0, durationMs, isPlaying)
```

`MediaController`:
- delete the third `scope.launch { while (true) { delay(TICK_MS) ... } }` block in `init`, the `TICK_MS` constant, `interpolate()`, and the now-unused `delay` import;
- in `seek`: `_state.update { s -> s.copy(receivedAtMs = now, nowPlaying = s.nowPlaying?.copy(positionMs = target)) }`;
- in `onNowPlaying`: delete the `positionMs = command.positionMs,` argument.

`Timeline` in `PlaybackControls.kt` (imports `androidx.compose.runtime.State`, `androidx.compose.runtime.produceState`, `kotlinx.coroutines.delay`):

```kotlin
@Composable
fun Timeline(state: MediaUiState, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val duration = state.durationMs
    val range = 0f..maxOf(duration.toFloat(), 1f)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val position by rememberPlaybackPosition(state)
    val shown = if (dragging) dragValue.toLong() else position
    val seekCd = stringResource(R.string.cd_seek)
    Column(modifier) {
        Slider(
            value = (if (dragging) dragValue else position.toFloat()).coerceIn(range),
            // ... onValueChange / onValueChangeFinished / valueRange / enabled / modifier unchanged
        )
        // ... labels row unchanged
    }
}

/** Interpolated position; ticks only while this composable is on screen and the PC is playing. */
@Composable
private fun rememberPlaybackPosition(state: MediaUiState): State<Long> =
    produceState(
        initialValue = state.positionAt(System.currentTimeMillis()),
        state.receivedAtMs, state.nowPlaying?.positionMs, state.isPlaying, state.durationMs,
    ) {
        value = state.positionAt(System.currentTimeMillis())
        while (state.isPlaying) {
            delay(POSITION_TICK_MS)
            value = state.positionAt(System.currentTimeMillis())
        }
    }

private const val POSITION_TICK_MS = 500L
```

`PcMediaSession` — skip unchanged metadata (import `android.graphics.Bitmap` already present):

```kotlin
    private data class MetadataKey(val title: String?, val artist: String?, val album: String?, val durationMs: Long)

    private var lastMetadata: MetadataKey? = null
    private var lastArtwork: Bitmap? = null

    fun update(nowPlaying: NowPlayingCommand, receivedAtMs: Long, artwork: Bitmap?) {
        val key = MetadataKey(nowPlaying.title, nowPlaying.artist, nowPlaying.album, nowPlaying.durationMs)
        // The artwork bitmap is one cached instance per track, so identity is the right comparison.
        if (key != lastMetadata || artwork !== lastArtwork) {
            val metadata = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, nowPlaying.title.orEmpty())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, nowPlaying.artist.orEmpty())
                .putString(MediaMetadata.METADATA_KEY_ALBUM, nowPlaying.album.orEmpty())
                .putLong(MediaMetadata.METADATA_KEY_DURATION, nowPlaying.durationMs)
            artwork?.let { metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
            session.setMetadata(metadata.build())
            lastMetadata = key
            lastArtwork = artwork
        }
        // ... playback state unchanged
    }

    fun deactivate() {
        lastMetadata = null
        lastArtwork = null
        if (session.isActive) session.isActive = false
    }
```

`FileTransferController` — add the helper and replace the two per-chunk assignments (`_progress.value = TransferProgress(name, sent, size, outgoing = true)` in `sendNow`, and `_progress.value = TransferProgress(incoming.offer.name, ...)` in `onChunk`) with `report(...)` of the same value:

```kotlin
    /** Per-chunk progress re-renders the Share tab; only whole-percent changes are worth showing. */
    private fun report(progress: TransferProgress) {
        val shown = _progress.value
        if (shown == null || shown.name != progress.name || shown.outgoing != progress.outgoing ||
            percentOf(shown) != percentOf(progress)
        ) {
            _progress.value = progress
        }
    }

    private fun percentOf(p: TransferProgress): Long = if (p.totalBytes > 0) p.doneBytes * 100 / p.totalBytes else 0
```

- [ ] **Step 5: Run the tests and build**

Run: `./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebug`
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add androidApp/src
git commit -m "perf: interpolate media position inside the timeline; skip unchanged media metadata; throttle transfer progress"
```

---

### Task 7: Battery — LAN-only probing with backoff, no mDNS while connected, lifecycle-aware sweep, gated sensor (P12–P14)

**Files:**
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/session/AutoConnectBackoff.kt`
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/session/AutoConnectPlan.kt`
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/session/AutoConnector.kt`
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/session/RemoteSession.kt` (`startDiscovery`)
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/ConnectScreen.kt:77-81`
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/service/AutoStandbyManager.kt`
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/AppGraph.kt`
- Test: `androidApp/src/test/kotlin/com/saubh/deskbuddy/session/AutoConnectBackoffTest.kt`
- Test: `androidApp/src/test/kotlin/com/saubh/deskbuddy/session/AutoConnectPlanTest.kt`

**Interfaces:**
- Produces: `AutoConnectBackoff(initialMs = 10_000, maxMs = 300_000)` with `currentMs`, `grow()`, `reset()`; `AutoConnectPlan.reachable(candidates, onLan): List<SavedDesktop>`; `AutoStandbyManager.setPcConnected(Boolean)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.saubh.deskbuddy.session

import kotlin.test.Test
import kotlin.test.assertEquals

class AutoConnectBackoffTest {
    @Test
    fun `grow_givenRepeatedMisses_doublesUpToCap`() {
        val backoff = AutoConnectBackoff(initialMs = 10_000, maxMs = 300_000)
        val seen = (1..7).map { backoff.grow(); backoff.currentMs }
        assertEquals(listOf(20_000L, 40_000L, 80_000L, 160_000L, 300_000L, 300_000L, 300_000L), seen)
    }

    @Test
    fun `reset_afterGrowth_returnsToInitial`() {
        val backoff = AutoConnectBackoff(initialMs = 10_000, maxMs = 300_000)
        backoff.grow(); backoff.grow()
        backoff.reset()
        assertEquals(10_000L, backoff.currentMs)
    }
}
```

`AutoConnectPlanTest.kt` (`SavedDesktop(name, host, port, token, ...)` — see `prefs/SavedDesktop.kt`):

```kotlin
package com.saubh.deskbuddy.session

import com.saubh.deskbuddy.prefs.SavedDesktop
import kotlin.test.Test
import kotlin.test.assertEquals

class AutoConnectPlanTest {
    private val lan = SavedDesktop("Desk", "192.168.1.20", 8765, token = "t")
    private val usb = SavedDesktop("Desk (USB)", "127.0.0.1", 8765, token = "t")

    @Test
    fun `reachable_givenOnLan_keepsAll`() {
        assertEquals(listOf(lan, usb), AutoConnectPlan.reachable(listOf(lan, usb), onLan = true))
    }

    @Test
    fun `reachable_givenOffLan_keepsOnlyLoopback`() {
        assertEquals(listOf(usb), AutoConnectPlan.reachable(listOf(lan, usb), onLan = false))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.saubh.deskbuddy.session.*"`
Expected: compile FAIL.

- [ ] **Step 3: Implement the pure pieces**

`AutoConnectBackoff.kt`:

```kotlin
package com.saubh.deskbuddy.session

/** Auto-connect probe interval: doubles after each round where no paired PC answered, capped; reset on a wake-up. */
class AutoConnectBackoff(private val initialMs: Long = 10_000L, private val maxMs: Long = 300_000L) {
    var currentMs: Long = initialMs
        private set

    fun grow() {
        currentMs = (currentMs * 2).coerceAtMost(maxMs)
    }

    fun reset() {
        currentMs = initialMs
    }
}
```

`AutoConnectPlan` — add:

```kotlin
    private val LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost", "::1")

    /** Off Wi-Fi/Ethernet only loopback targets (USB via `adb reverse`) can answer, so the rest are skipped. */
    fun reachable(candidates: List<SavedDesktop>, onLan: Boolean): List<SavedDesktop> =
        if (onLan) candidates else candidates.filter { it.host.lowercase() in LOOPBACK_HOSTS }
```

- [ ] **Step 4: Rewire `AutoConnector`** (imports `android.net.NetworkCapabilities`, `kotlinx.coroutines.flow.drop`):

```kotlin
class AutoConnector(
    private val session: RemoteSession,
    private val scope: CoroutineScope,
    private val probe: suspend (host: String, port: Int) -> Boolean = ::portOpen,
    private val backoff: AutoConnectBackoff = AutoConnectBackoff(),
) {
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null
    private var connectivity: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun start(context: Context) {
        if (job != null) return
        connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        session.startDiscovery()
        registerNetworkCallback()
        // A dropped connection should be retried now, not after a grown interval.
        scope.launch { session.connected.drop(1).collect { if (!it) nudge() } }
        job = scope.launch {
            while (true) {
                val missed = attempt()
                val woke = withTimeoutOrNull(backoff.currentMs) { wake.receive() } != null
                if (woke || !missed) backoff.reset() else backoff.grow()
            }
        }
    }

    fun nudge() {
        wake.trySend(Unit)
    }

    /** True when it probed at least one paired PC and none answered. */
    private suspend fun attempt(): Boolean {
        if (!session.isSearching || session.autoConnectPaused) return false
        val all = AutoConnectPlan.candidates(session.savedDesktops.value, session.discovered.value)
        val candidates = AutoConnectPlan.reachable(all, isOnLan())
        if (candidates.isEmpty()) return false
        for (candidate in candidates) {
            if (probe(candidate.host, candidate.port)) {
                // State may have changed while probing (the user tapped Connect).
                if (session.isSearching && !session.autoConnectPaused) session.connect(candidate, quiet = true)
                return false
            }
        }
        return true
    }

    private fun isOnLan(): Boolean {
        val cm = connectivity ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun registerNetworkCallback() {
        val cm = connectivity ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = nudge()
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }.onSuccess { networkCallback = callback }
    }

    // companion unchanged
}
```

Update the class KDoc: "Runs every [AutoConnectBackoff.currentMs] (10 s, doubling to 5 min while no PC answers), at once when a network comes up or the connection drops, and only probes loopback targets off Wi-Fi/Ethernet."

- [ ] **Step 5: mDNS only while not connected** — in `RemoteSession.startDiscovery`, replace the first child `launch { discovery.discover()... }` with (import `kotlinx.coroutines.flow.collectLatest`):

```kotlin
            launch {
                // NSD multicast costs battery and is only needed while nothing is connected.
                connected.collectLatest { isConnected ->
                    if (isConnected) return@collectLatest
                    discovery.discover()
                        .catch { /* discovery failure is non-fatal; the sweep and manual IP still work */ }
                        .collect { desktops ->
                            mdnsFound = desktops
                            publishDiscovered()
                        }
                }
            }
```

(`DesktopDiscovery.discover()` is a `callbackFlow` whose `awaitClose` calls `stopServiceDiscovery`, so cancellation stops NSD.)

- [ ] **Step 6: Sweep follows the activity lifecycle** — in `ConnectScreen`, replace the `DisposableEffect(Unit) { ... }` block with (import `androidx.lifecycle.compose.LifecycleStartEffect`; drop the `DisposableEffect` import if now unused):

```kotlin
    LifecycleStartEffect(Unit) {
        onVisibleChange(true)
        onStopOrDispose { onVisibleChange(false) }
    }
```

- [ ] **Step 7: Orientation sensor only while charging and connected** — in `AutoStandbyManager`:

```kotlin
    private var pcConnected = false

    /** The sensor only matters while a PC is connected; overnight charging with no PC keeps it off. */
    fun setPcConnected(connected: Boolean) {
        pcConnected = connected
        updateSensor()
        if (!connected) updateState(false)
    }

    private fun updateSensor() {
        if (isCharging && pcConnected) startOrientationListener() else stopOrientationListener()
    }
```

In `powerReceiver`: `ACTION_POWER_CONNECTED -> { isCharging = true; updateSensor() }` and `ACTION_POWER_DISCONNECTED -> { isCharging = false; updateSensor(); updateState(false) }`. In `start()` replace `if (isCharging) { startOrientationListener() }` with `updateSensor()`.

In `AppGraph`, at the end of the class (after `val autoStandby = ...`):

```kotlin
    init {
        scope.launch { session.connected.collect(autoStandby::setPcConnected) }
    }
```

(import `kotlinx.coroutines.launch`).

- [ ] **Step 8: Run the tests and build**

Run: `./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:lintDebug`
Expected: PASS / BUILD SUCCESSFUL, lint 0 errors.

- [ ] **Step 9: Commit**

```bash
git add androidApp/src
git commit -m "perf: probe paired PCs only on LAN with backoff; stop mDNS while connected; lifecycle-aware sweep; gate standby sensor"
```

---

### Task 8: R8 for release (P15)

**Files:**
- Modify: `androidApp/build.gradle.kts` (`buildTypes.release`)
- Modify: `androidApp/proguard-rules.pro`

- [ ] **Step 1: Enable shrinking**

```kotlin
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
```

- [ ] **Step 2: Keep rules** — append to `proguard-rules.pro`:

```proguard
# kotlinx.serialization: protocol and prefs models are looked up through generated serializers.
-keepclassmembers @kotlinx.serialization.Serializable class com.saubh.deskbuddy.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.saubh.deskbuddy.**$$serializer { *; }
-keepclassmembers class com.saubh.deskbuddy.** {
    *** Companion;
}

# Ktor CIO references JVM-only classes that Android never loads.
-dontwarn java.lang.management.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
```

- [ ] **Step 3: Build the release APK**

Run: `./gradlew :androidApp:assembleRelease`
Expected: BUILD SUCCESSFUL. If R8 fails with "Missing classes detected", copy every line of `androidApp/build/outputs/mapping/release/missing_rules.txt` into `proguard-rules.pro` under the Ktor comment and re-run until it succeeds.

- [ ] **Step 4: Commit**

```bash
git add androidApp/build.gradle.kts androidApp/proguard-rules.pro
git commit -m "perf: enable R8 and resource shrinking for release builds"
```

(The user smoke-tests the release APK on a device in Task 17.)

---

# Part B — Shared keyboard (spec §2–§4)

### Task 9: Protocol + desktop dispatch (silent keystrokes, one-time unsupported error)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/saubh/deskbuddy/protocol/Messages.kt`
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/keyboard/KeyboardActuator.kt`
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/Actuators.kt`
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ControlSession.kt`
- Modify: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/TestFixtures.kt`
- Test: `shared/src/commonTest/kotlin/com/saubh/deskbuddy/protocol/WireCodecTest.kt`
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/ControlSessionTest.kt` (extend)

**Interfaces:**
- Produces: `enum class Key` (with `Key.forChar(Char): Key?`), `enum class KeyModifier { CTRL, ALT, SHIFT, META }`, `KeyTextCommand(text)`, `KeyPressCommand(key, modifiers = emptySet())`, `KeyboardModeCommand(active)`; `interface KeyboardActuator { fun type(text: String); fun press(key: Key, modifiers: Set<KeyModifier>) }`; `UnsupportedKeyboardActuator(osName)`; `Actuators.keyboard`; `RecordingKeyboard` fixture; `testActuators(power, keyboard)`.

- [ ] **Step 1: Write the failing tests**

`WireCodecTest.kt` (commonTest — directory is new):

```kotlin
package com.saubh.deskbuddy.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WireCodecTest {
    @Test
    fun encodeDecode_givenKeyCommands_roundTrips() {
        val messages: List<Message> = listOf(
            Envelope("t", KeyTextCommand("héllo 👋")),
            Envelope("t", KeyPressCommand(Key.C, setOf(KeyModifier.CTRL))),
            Envelope("t", KeyPressCommand(Key.ENTER)),
            Push(KeyboardModeCommand(active = true)),
        )
        messages.forEach { assertEquals(it, WireCodec.decode(WireCodec.encode(it))) }
    }

    @Test
    fun decodeOrNull_givenUnknownKey_returnsNull() {
        val frame = """{"type":"envelope","token":"t","command":{"type":"key_press","key":"F13"}}"""
        assertNull(WireCodec.decodeOrNull(frame))
    }

    @Test
    fun forChar_givenLettersDigitsAndSymbols_mapsOrNull() {
        assertEquals(Key.A, Key.forChar('a'))
        assertEquals(Key.Z, Key.forChar('Z'))
        assertEquals(Key.DIGIT_7, Key.forChar('7'))
        assertNull(Key.forChar('@'))
    }
}
```

Append to `TestFixtures.kt` and change `testActuators`:

```kotlin
class RecordingKeyboard(private val unsupported: Boolean = false) : KeyboardActuator {
    val typed = mutableListOf<String>()
    val pressed = mutableListOf<Pair<Key, Set<KeyModifier>>>()
    override fun type(text: String) {
        if (unsupported) throw UnsupportedOsException("test")
        typed += text
    }
    override fun press(key: Key, modifiers: Set<KeyModifier>) {
        if (unsupported) throw UnsupportedOsException("test")
        pressed += key to modifiers
    }
}

fun testActuators(
    power: PowerActuator = RecordingPowerActuator(),
    keyboard: KeyboardActuator = RecordingKeyboard(),
): Actuators {
    val dir = Files.createTempDirectory("deskbuddy-test")
    return Actuators(
        media = NoopMediaActuator(),
        share = NoopShareActuator(),
        apps = AppCatalogService(NoopAppActuator(), ShortcutStore(dir.resolve("shortcuts.json"))),
        files = FileReceiver(directory = dir.resolve("received")),
        power = power,
        keyboard = keyboard,
    )
}
```

(imports: `com.saubh.deskbuddy.protocol.Key`, `com.saubh.deskbuddy.protocol.KeyModifier`, `com.saubh.deskbuddy.server.keyboard.KeyboardActuator`).

Add to `ControlSessionTest` (imports for the new commands, `kotlin.test.assertNull`):

```kotlin
    private fun sessionWith(keyboard: KeyboardActuator) = ControlSession(store, testActuators(keyboard = keyboard), listener)

    @Test
    fun `keyText_givenPairedToken_typesSilently`() {
        val keyboard = RecordingKeyboard()
        val reply = sessionWith(keyboard).onMessage(Envelope("tok", KeyTextCommand("hi")))
        assertNull(reply.message)
        assertEquals(listOf("hi"), keyboard.typed)
    }

    @Test
    fun `keyPress_givenModifiers_passesThemThrough`() {
        val keyboard = RecordingKeyboard()
        sessionWith(keyboard).onMessage(Envelope("tok", KeyPressCommand(Key.C, setOf(KeyModifier.CTRL))))
        assertEquals(listOf(Key.C to setOf(KeyModifier.CTRL)), keyboard.pressed)
    }

    @Test
    fun `keyText_givenUnsupportedOs_reportsOnlyOnce`() {
        val session = sessionWith(RecordingKeyboard(unsupported = true))
        val replies = (1..5).map { session.onMessage(Envelope("tok", KeyTextCommand("x"))).message }
        assertEquals(Ack(ok = false, error = ErrorCode.UNSUPPORTED_OS), replies.first())
        assertEquals(List(4) { null }, replies.drop(1))
    }

    @Test
    fun `keyText_givenOversizedText_repliesInternalWithoutTyping`() {
        val keyboard = RecordingKeyboard()
        val reply = sessionWith(keyboard).onMessage(Envelope("tok", KeyTextCommand("x".repeat(4097))))
        assertEquals(Ack(ok = false, error = ErrorCode.INTERNAL), reply.message)
        assertEquals(emptyList(), keyboard.typed)
    }

    @Test
    fun `keyboardMode_fromPhone_repliesInternal`() {
        val reply = session.onMessage(Envelope("tok", KeyboardModeCommand(active = true)))
        assertEquals(Ack(ok = false, error = ErrorCode.INTERNAL), reply.message)
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :shared:jvmTest`
Expected: compile FAIL (new types missing).

- [ ] **Step 3: Implement the protocol** — append to `Messages.kt`:

```kotlin
// ---- Keyboard (M4) ------------------------------------------------------

/** Keys sent as key presses. Order matters: A..Z and DIGIT_0..DIGIT_9 are contiguous (key-code mappings rely on it). */
@Serializable
enum class Key {
    ENTER, BACKSPACE, DELETE, TAB, ESCAPE, SPACE,
    UP, DOWN, LEFT, RIGHT, HOME, END, PAGE_UP, PAGE_DOWN,
    A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z,
    DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4, DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9,
    ;

    companion object {
        /** The letter or digit key for [c] (either case), or null for anything else. */
        fun forChar(c: Char): Key? = when (c) {
            in 'a'..'z' -> entries[A.ordinal + (c - 'a')]
            in 'A'..'Z' -> entries[A.ordinal + (c - 'A')]
            in '0'..'9' -> entries[DIGIT_0.ordinal + (c - '0')]
            else -> null
        }
    }
}

@Serializable
enum class KeyModifier { CTRL, ALT, SHIFT, META }

/** Committed Unicode text. Both directions. No reply on success. */
@Serializable
@SerialName("key_text")
data class KeyTextCommand(val text: String) : Command()

/** A special key or combo. Both directions. No reply on success. */
@Serializable
@SerialName("key_press")
data class KeyPressCommand(val key: Key, val modifiers: Set<KeyModifier> = emptySet()) : Command()

/** Desktop → phone (inside [Push]): the PC keyboard started or stopped typing to the phone. */
@Serializable
@SerialName("keyboard_mode")
data class KeyboardModeCommand(val active: Boolean) : Command()
```

- [ ] **Step 4: Implement the actuator interface** — `server/keyboard/KeyboardActuator.kt`:

```kotlin
package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.saubh.deskbuddy.server.UnsupportedOsException

/** Types into whichever window has keyboard focus on this PC. */
interface KeyboardActuator {
    fun type(text: String)
    fun press(key: Key, modifiers: Set<KeyModifier>)
}

class UnsupportedKeyboardActuator(private val osName: String) : KeyboardActuator {
    override fun type(text: String) = throw UnsupportedOsException(osName)
    override fun press(key: Key, modifiers: Set<KeyModifier>) = throw UnsupportedOsException(osName)
}
```

`Actuators` constructor — add after `power`: `val keyboard: KeyboardActuator = UnsupportedKeyboardActuator("unknown"),` (import `com.saubh.deskbuddy.server.keyboard.*` types).

- [ ] **Step 5: Dispatch in `ControlSession`**

Companion additions:

```kotlin
        val UNSUPPORTED_OS = Ack(ok = false, error = ErrorCode.UNSUPPORTED_OS)
        /** Typing, not pasting: bulk text goes through Share. */
        const val MAX_TYPED_CHARS = 4096
```

Field: `private var keyboardUnsupportedReported = false`

`execute` branches:

```kotlin
        is KeyTextCommand ->
            if (command.text.length > MAX_TYPED_CHARS) INTERNAL else keyboard { actuators.keyboard.type(command.text) }
        is KeyPressCommand -> keyboard { actuators.keyboard.press(command.key, command.modifiers) }
        // Desktop → phone only.
        is KeyboardModeCommand -> INTERNAL
```

Helper:

```kotlin
    /** Keystrokes are silent on success; an unsupported OS is reported once per connection, not once per key. */
    private fun keyboard(action: () -> Unit): Message? = try {
        action()
        null
    } catch (e: UnsupportedOsException) {
        if (keyboardUnsupportedReported) {
            null
        } else {
            keyboardUnsupportedReported = true
            UNSUPPORTED_OS
        }
    }
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:compileDebugKotlin`
Expected: PASS (commonTest runs on both JVM and Android host).

- [ ] **Step 7: Commit**

```bash
git add shared/src androidApp/src
git commit -m "feat(keyboard): key_text / key_press / keyboard_mode protocol and silent desktop dispatch"
```

---

### Task 10: Windows `SendInput` and Linux `xdotool` actuators

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/keyboard/SendInputPlan.kt`
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/keyboard/WindowsKeyboardActuator.kt`
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/keyboard/XdotoolArgs.kt`
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/keyboard/XdotoolKeyboardActuator.kt`
- Modify: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ActuatorFactory.kt`
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/keyboard/SendInputPlanTest.kt`
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/keyboard/XdotoolArgsTest.kt`

**Interfaces:**
- Produces: `data class KeyInput(val vk: Int, val scan: Int, val flags: Int)`; `SendInputPlan.forText(text): List<KeyInput>`, `SendInputPlan.forKey(key, modifiers): List<KeyInput>`; `XdotoolArgs.forText(text): List<String>`, `XdotoolArgs.forKey(key, modifiers): List<String>`; `ActuatorFactory.keyboard(osName): KeyboardActuator`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.saubh.deskbuddy.server.keyboard.SendInputPlan.KEYEVENTF_EXTENDEDKEY
import com.saubh.deskbuddy.server.keyboard.SendInputPlan.KEYEVENTF_KEYUP
import com.saubh.deskbuddy.server.keyboard.SendInputPlan.KEYEVENTF_UNICODE
import kotlin.test.Test
import kotlin.test.assertEquals

class SendInputPlanTest {
    @Test
    fun `forText_givenSurrogatePair_sendsEachUnitDownThenUp`() {
        val plan = SendInputPlan.forText("a👋")
        val units = "a👋".map { it.code }
        assertEquals(6, plan.size)
        assertEquals(units, plan.filter { it.flags == KEYEVENTF_UNICODE }.map { it.scan })
        assertEquals(units, plan.filter { it.flags == KEYEVENTF_UNICODE or KEYEVENTF_KEYUP }.map { it.scan })
    }

    @Test
    fun `forKey_givenCtrlShift_pressesModifiersFirstReleasesInReverse`() {
        val plan = SendInputPlan.forKey(Key.S, setOf(KeyModifier.SHIFT, KeyModifier.CTRL))
        assertEquals(
            listOf(
                KeyInput(0x11, 0, 0), KeyInput(0x10, 0, 0),
                KeyInput(0x53, 0, 0), KeyInput(0x53, 0, KEYEVENTF_KEYUP),
                KeyInput(0x10, 0, KEYEVENTF_KEYUP), KeyInput(0x11, 0, KEYEVENTF_KEYUP),
            ),
            plan,
        )
    }

    @Test
    fun `forKey_givenArrow_setsExtendedFlag`() {
        val plan = SendInputPlan.forKey(Key.LEFT, emptySet())
        assertEquals(listOf(KeyInput(0x25, 0, KEYEVENTF_EXTENDEDKEY), KeyInput(0x25, 0, KEYEVENTF_EXTENDEDKEY or KEYEVENTF_KEYUP)), plan)
    }
}
```

```kotlin
package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import kotlin.test.Test
import kotlin.test.assertEquals

class XdotoolArgsTest {
    @Test
    fun `forKey_givenCtrlShiftA_buildsChord`() {
        assertEquals(
            listOf("xdotool", "key", "--clearmodifiers", "ctrl+shift+a"),
            XdotoolArgs.forKey(Key.A, setOf(KeyModifier.SHIFT, KeyModifier.CTRL)),
        )
    }

    @Test
    fun `forText_givenDashLeadingText_endsOptions`() {
        assertEquals(listOf("xdotool", "type", "--clearmodifiers", "--", "-rf"), XdotoolArgs.forText("-rf"))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.keyboard.*"`
Expected: compile FAIL.

- [ ] **Step 3: Implement `SendInputPlan.kt`**

```kotlin
package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier

/** One `KEYBDINPUT`: virtual key, scan code (UTF-16 unit for Unicode input) and flags. */
data class KeyInput(val vk: Int, val scan: Int, val flags: Int)

/** Pure builder of `SendInput` batches, so key mapping and ordering are testable without Win32. */
object SendInputPlan {
    const val KEYEVENTF_EXTENDEDKEY = 0x0001
    const val KEYEVENTF_KEYUP = 0x0002
    const val KEYEVENTF_UNICODE = 0x0004

    private val MODIFIER_ORDER = listOf(KeyModifier.CTRL, KeyModifier.ALT, KeyModifier.SHIFT, KeyModifier.META)

    /** Each UTF-16 unit as a Unicode down/up pair; surrogate pairs are sent as two units, as Windows expects. */
    fun forText(text: String): List<KeyInput> = text.flatMap { unit ->
        listOf(KeyInput(0, unit.code, KEYEVENTF_UNICODE), KeyInput(0, unit.code, KEYEVENTF_UNICODE or KEYEVENTF_KEYUP))
    }

    /** Modifiers down, key down/up, modifiers up in reverse: one batch, so no other input interleaves. */
    fun forKey(key: Key, modifiers: Set<KeyModifier>): List<KeyInput> {
        val mods = MODIFIER_ORDER.filter { it in modifiers }.map(::vkOf)
        val (vk, extended) = vkOf(key)
        val flag = if (extended) KEYEVENTF_EXTENDEDKEY else 0
        return mods.map { (mvk, mext) -> KeyInput(mvk, 0, if (mext) KEYEVENTF_EXTENDEDKEY else 0) } +
            KeyInput(vk, 0, flag) + KeyInput(vk, 0, flag or KEYEVENTF_KEYUP) +
            mods.reversed().map { (mvk, mext) -> KeyInput(mvk, 0, (if (mext) KEYEVENTF_EXTENDEDKEY else 0) or KEYEVENTF_KEYUP) }
    }

    private fun vkOf(modifier: KeyModifier): Pair<Int, Boolean> = when (modifier) {
        KeyModifier.CTRL -> 0x11 to false
        KeyModifier.ALT -> 0x12 to false
        KeyModifier.SHIFT -> 0x10 to false
        KeyModifier.META -> 0x5B to true
    }

    private fun vkOf(key: Key): Pair<Int, Boolean> = when (key) {
        Key.ENTER -> 0x0D to false
        Key.BACKSPACE -> 0x08 to false
        Key.DELETE -> 0x2E to true
        Key.TAB -> 0x09 to false
        Key.ESCAPE -> 0x1B to false
        Key.SPACE -> 0x20 to false
        Key.UP -> 0x26 to true
        Key.DOWN -> 0x28 to true
        Key.LEFT -> 0x25 to true
        Key.RIGHT -> 0x27 to true
        Key.HOME -> 0x24 to true
        Key.END -> 0x23 to true
        Key.PAGE_UP -> 0x21 to true
        Key.PAGE_DOWN -> 0x22 to true
        else -> if (key.ordinal >= Key.DIGIT_0.ordinal) {
            0x30 + (key.ordinal - Key.DIGIT_0.ordinal) to false
        } else {
            0x41 + (key.ordinal - Key.A.ordinal) to false
        }
    }
}
```

- [ ] **Step 4: Implement `WindowsKeyboardActuator.kt`** (same JNA style as `WindowsMediaActuator`):

```kotlin
package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser

/**
 * Win32 `SendInput`. Text goes as `KEYEVENTF_UNICODE`, so the phone's text arrives exactly,
 * whatever keyboard layout the PC uses. Elevated windows and the lock screen do not accept it (UIPI).
 */
class WindowsKeyboardActuator : KeyboardActuator {
    override fun type(text: String) = send(SendInputPlan.forText(text))

    override fun press(key: Key, modifiers: Set<KeyModifier>) = send(SendInputPlan.forKey(key, modifiers))

    private fun send(plan: List<KeyInput>) {
        if (plan.isEmpty()) return
        @Suppress("UNCHECKED_CAST")
        val inputs = WinUser.INPUT().toArray(plan.size) as Array<WinUser.INPUT> // SendInput needs contiguous memory
        plan.forEachIndexed { i, key ->
            inputs[i].type = WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD.toLong())
            inputs[i].input.setType("ki")
            inputs[i].input.ki.wVk = WinDef.WORD(key.vk.toLong())
            inputs[i].input.ki.wScan = WinDef.WORD(key.scan.toLong())
            inputs[i].input.ki.time = WinDef.DWORD(0)
            inputs[i].input.ki.dwFlags = WinDef.DWORD(key.flags.toLong())
            inputs[i].input.ki.dwExtraInfo = BaseTSD.ULONG_PTR(0)
        }
        User32.INSTANCE.SendInput(WinDef.DWORD(inputs.size.toLong()), inputs, inputs[0].size())
    }
}
```

- [ ] **Step 5: Implement `XdotoolArgs.kt` and `XdotoolKeyboardActuator.kt`**

```kotlin
package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier

/** Pure `xdotool` command lines (X11). */
object XdotoolArgs {
    private val MODIFIER_ORDER = listOf(KeyModifier.CTRL, KeyModifier.ALT, KeyModifier.SHIFT, KeyModifier.META)

    fun forText(text: String): List<String> = listOf("xdotool", "type", "--clearmodifiers", "--", text)

    fun forKey(key: Key, modifiers: Set<KeyModifier>): List<String> {
        val mods = MODIFIER_ORDER.filter { it in modifiers }.map {
            when (it) {
                KeyModifier.CTRL -> "ctrl"
                KeyModifier.ALT -> "alt"
                KeyModifier.SHIFT -> "shift"
                KeyModifier.META -> "super"
            }
        }
        return listOf("xdotool", "key", "--clearmodifiers", (mods + keysym(key)).joinToString("+"))
    }

    private fun keysym(key: Key): String = when (key) {
        Key.ENTER -> "Return"
        Key.BACKSPACE -> "BackSpace"
        Key.DELETE -> "Delete"
        Key.TAB -> "Tab"
        Key.ESCAPE -> "Escape"
        Key.SPACE -> "space"
        Key.UP -> "Up"
        Key.DOWN -> "Down"
        Key.LEFT -> "Left"
        Key.RIGHT -> "Right"
        Key.HOME -> "Home"
        Key.END -> "End"
        Key.PAGE_UP -> "Prior"
        Key.PAGE_DOWN -> "Next"
        else -> if (key.ordinal >= Key.DIGIT_0.ordinal) key.name.removePrefix("DIGIT_") else key.name.lowercase()
    }
}
```

```kotlin
package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import java.io.File
import java.util.concurrent.TimeUnit

/** X11 typing through `xdotool`; created only when it is on PATH. */
class XdotoolKeyboardActuator : KeyboardActuator {
    override fun type(text: String) = run(XdotoolArgs.forText(text))

    override fun press(key: Key, modifiers: Set<KeyModifier>) = run(XdotoolArgs.forKey(key, modifiers))

    private fun run(command: List<String>) {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
    }

    companion object {
        fun isAvailable(): Boolean = System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .any { File(it, "xdotool").canExecute() }
    }
}
```

- [ ] **Step 6: Factory** — in `ActuatorFactory.create(...)` add `keyboard = keyboard(osName),` and:

```kotlin
    fun keyboard(osName: String): KeyboardActuator = when {
        isWindows(osName) -> WindowsKeyboardActuator()
        osName.startsWith("Linux", ignoreCase = true) && XdotoolKeyboardActuator.isAvailable() -> XdotoolKeyboardActuator()
        else -> UnsupportedKeyboardActuator(osName)
    }
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew :shared:jvmTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add shared/src
git commit -m "feat(keyboard): inject phone keystrokes with SendInput on Windows and xdotool on Linux"
```

---

### Task 11: PC key translation and typing-mode state machine (pure)

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/keyboard/RawKey.kt`
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/keyboard/AwtKeyTranslator.kt`
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/keyboard/TypingState.kt`
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/keyboard/PhoneTypingSession.kt`
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/keyboard/AwtKeyTranslatorTest.kt`
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/keyboard/PhoneTypingSessionTest.kt`

**Interfaces:**
- Produces:
  - `data class RawKey(val type: Type, val keyCode: Int, val char: Char = KeyEvent.CHAR_UNDEFINED, val ctrl: Boolean = false, val alt: Boolean = false, val shift: Boolean = false, val altGraph: Boolean = false)` with `enum class Type { PRESSED, RELEASED, TYPED }`.
  - `AwtKeyTranslator.translate(raw): List<Command>`, `reset()`.
  - `sealed interface TypingState { Idle; Active(phoneName); NoPhone; PhoneDisconnected }`, `enum class ExitReason { HOTKEY, ESCAPE, FOCUS_LOST, PHONE_DISCONNECTED, QUIT }`.
  - `PhoneTypingSession(send: (Command) -> Unit, phoneName: () -> String?)` with `state: StateFlow<TypingState>`, `echo: StateFlow<String>`, `toggle()`, `enter()`, `exit(reason)`, `onKey(raw): Boolean`, `dismissNotice()`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.saubh.deskbuddy.protocol.KeyPressCommand
import com.saubh.deskbuddy.protocol.KeyTextCommand
import com.saubh.deskbuddy.server.keyboard.RawKey.Type.PRESSED
import com.saubh.deskbuddy.server.keyboard.RawKey.Type.RELEASED
import com.saubh.deskbuddy.server.keyboard.RawKey.Type.TYPED
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class AwtKeyTranslatorTest {
    private val t = AwtKeyTranslator()

    private fun feed(vararg keys: RawKey) = keys.flatMap(t::translate)

    @Test
    fun `translate_givenShiftedLetter_sendsText`() {
        val out = feed(RawKey(PRESSED, KeyEvent.VK_A, shift = true), RawKey(TYPED, 0, 'A', shift = true))
        assertEquals(listOf(KeyTextCommand("A")), out)
    }

    @Test
    fun `translate_givenCtrlC_sendsCombo`() {
        val out = feed(RawKey(PRESSED, KeyEvent.VK_C, ctrl = true), RawKey(TYPED, 0, '\u0003', ctrl = true))
        assertEquals(listOf(KeyPressCommand(Key.C, setOf(KeyModifier.CTRL))), out)
    }

    @Test
    fun `translate_givenAltGrCharacter_sendsText`() {
        val out = feed(
            RawKey(PRESSED, KeyEvent.VK_Q, ctrl = true, alt = true),
            RawKey(TYPED, 0, '@', ctrl = true, alt = true),
        )
        assertEquals(listOf(KeyTextCommand("@")), out)
    }

    @Test
    fun `translate_givenAltLetterWithoutTyped_sendsComboOnRelease`() {
        val out = feed(RawKey(PRESSED, KeyEvent.VK_T, ctrl = true, alt = true), RawKey(RELEASED, KeyEvent.VK_T, ctrl = true, alt = true))
        assertEquals(listOf(KeyPressCommand(Key.T, setOf(KeyModifier.CTRL, KeyModifier.ALT))), out)
    }

    @Test
    fun `translate_givenShiftArrow_sendsKeyWithShift`() {
        assertEquals(listOf(KeyPressCommand(Key.LEFT, setOf(KeyModifier.SHIFT))), feed(RawKey(PRESSED, KeyEvent.VK_LEFT, shift = true)))
    }

    @Test
    fun `translate_givenEnter_sendsOneEnterAndIgnoresTypedNewline`() {
        val out = feed(RawKey(PRESSED, KeyEvent.VK_ENTER), RawKey(TYPED, 0, '\n'))
        assertEquals(listOf(KeyPressCommand(Key.ENTER)), out)
    }
}
```

```kotlin
package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.KeyTextCommand
import com.saubh.deskbuddy.protocol.KeyboardModeCommand
import com.saubh.deskbuddy.server.keyboard.RawKey.Type.PRESSED
import com.saubh.deskbuddy.server.keyboard.RawKey.Type.TYPED
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneTypingSessionTest {
    private val sent = mutableListOf<Command>()
    private var phone: String? = "Pixel 8"
    private val session = PhoneTypingSession(send = { sent += it }, phoneName = { phone })

    @Test
    fun `toggle_givenPhone_entersAndAnnouncesMode`() {
        session.toggle()
        assertEquals(TypingState.Active("Pixel 8"), session.state.value)
        assertEquals(listOf<Command>(KeyboardModeCommand(true)), sent)
    }

    @Test
    fun `toggle_givenNoPhone_showsNoPhoneAndSendsNothing`() {
        phone = null
        session.toggle()
        assertEquals(TypingState.NoPhone, session.state.value)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `exit_givenEachReason_announcesOffExactlyOnce`() {
        session.enter()
        session.exit(ExitReason.FOCUS_LOST)
        session.exit(ExitReason.HOTKEY)
        assertEquals(listOf<Command>(KeyboardModeCommand(true), KeyboardModeCommand(false)), sent)
        assertEquals(TypingState.Idle, session.state.value)
    }

    @Test
    fun `onKey_givenEscape_exitsWithoutForwarding`() {
        session.enter()
        assertTrue(session.onKey(RawKey(PRESSED, KeyEvent.VK_ESCAPE)))
        assertEquals(listOf<Command>(KeyboardModeCommand(true), KeyboardModeCommand(false)), sent)
    }

    @Test
    fun `onKey_givenTypedText_forwardsAndEchoes`() {
        session.enter()
        session.onKey(RawKey(TYPED, 0, 'h'))
        session.onKey(RawKey(TYPED, 0, 'i'))
        assertEquals(listOf(KeyTextCommand("h"), KeyTextCommand("i")), sent.drop(1))
        assertEquals("hi", session.echo.value)
    }

    @Test
    fun `onKey_givenIdle_isNotConsumed`() {
        assertFalse(session.onKey(RawKey(TYPED, 0, 'x')))
    }

    @Test
    fun `phoneDisconnected_givenActive_exitsWithNotice`() {
        session.enter()
        session.exit(ExitReason.PHONE_DISCONNECTED)
        assertEquals(TypingState.PhoneDisconnected, session.state.value)
        session.dismissNotice()
        assertEquals(TypingState.Idle, session.state.value)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.keyboard.*"`
Expected: compile FAIL.

- [ ] **Step 3: Implement `RawKey.kt` and `TypingState.kt`**

```kotlin
package com.saubh.deskbuddy.server.keyboard

import java.awt.event.KeyEvent

/** The parts of an AWT `KeyEvent` the translator needs; a plain value so tests need no AWT components. */
data class RawKey(
    val type: Type,
    val keyCode: Int,
    val char: Char = KeyEvent.CHAR_UNDEFINED,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val altGraph: Boolean = false,
) {
    enum class Type { PRESSED, RELEASED, TYPED }
}
```

```kotlin
package com.saubh.deskbuddy.server.keyboard

/** What the PC's typing-to-phone overlay shows. */
sealed interface TypingState {
    data object Idle : TypingState
    data class Active(val phoneName: String) : TypingState
    /** Hotkey pressed with no phone connected; shown briefly. */
    data object NoPhone : TypingState
    /** The phone dropped while typing; shown briefly. */
    data object PhoneDisconnected : TypingState
}

enum class ExitReason { HOTKEY, ESCAPE, FOCUS_LOST, PHONE_DISCONNECTED, QUIT }
```

- [ ] **Step 4: Implement `AwtKeyTranslator.kt`**

```kotlin
package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.saubh.deskbuddy.protocol.KeyPressCommand
import com.saubh.deskbuddy.protocol.KeyTextCommand
import java.awt.event.KeyEvent

/**
 * AWT key events → protocol commands. Characters come from KEY_TYPED, so the OS keyboard layout,
 * dead keys and AltGr are already applied. A Ctrl/Alt + letter press is held back until its
 * KEY_TYPED shows whether it produced text (AltGr, which Windows reports as Ctrl+Alt) or a combo.
 */
class AwtKeyTranslator {
    private var pending: KeyPressCommand? = null
    private var pendingKeyCode = 0

    fun translate(raw: RawKey): List<Command> {
        val out = mutableListOf<Command>()
        when (raw.type) {
            RawKey.Type.PRESSED -> {
                flush(out)
                val special = SPECIAL[raw.keyCode]
                val letter = letterOrDigit(raw.keyCode)
                when {
                    special != null -> out += KeyPressCommand(special, modifiers(raw))
                    letter != null && (raw.ctrl || raw.alt) -> {
                        pending = KeyPressCommand(letter, modifiers(raw))
                        pendingKeyCode = raw.keyCode
                    }
                }
            }
            RawKey.Type.TYPED -> {
                val held = pending
                pending = null
                val printable = raw.char != KeyEvent.CHAR_UNDEFINED && !raw.char.isISOControl()
                val altGr = raw.altGraph || (raw.ctrl && raw.alt)
                when {
                    printable && (held == null || altGr) -> out += KeyTextCommand(raw.char.toString())
                    held != null -> out += held
                }
            }
            RawKey.Type.RELEASED -> if (pending != null && raw.keyCode == pendingKeyCode) flush(out)
        }
        return out
    }

    fun reset() {
        pending = null
    }

    private fun flush(out: MutableList<Command>) {
        pending?.let { out += it }
        pending = null
    }

    private fun modifiers(raw: RawKey): Set<KeyModifier> = buildSet {
        if (raw.ctrl) add(KeyModifier.CTRL)
        if (raw.alt) add(KeyModifier.ALT)
        if (raw.shift) add(KeyModifier.SHIFT)
    }

    private fun letterOrDigit(keyCode: Int): Key? = when (keyCode) {
        in KeyEvent.VK_A..KeyEvent.VK_Z -> Key.forChar('A' + (keyCode - KeyEvent.VK_A))
        in KeyEvent.VK_0..KeyEvent.VK_9 -> Key.forChar('0' + (keyCode - KeyEvent.VK_0))
        else -> null
    }

    private companion object {
        val SPECIAL = mapOf(
            KeyEvent.VK_ENTER to Key.ENTER,
            KeyEvent.VK_BACK_SPACE to Key.BACKSPACE,
            KeyEvent.VK_DELETE to Key.DELETE,
            KeyEvent.VK_TAB to Key.TAB,
            KeyEvent.VK_UP to Key.UP,
            KeyEvent.VK_DOWN to Key.DOWN,
            KeyEvent.VK_LEFT to Key.LEFT,
            KeyEvent.VK_RIGHT to Key.RIGHT,
            KeyEvent.VK_HOME to Key.HOME,
            KeyEvent.VK_END to Key.END,
            KeyEvent.VK_PAGE_UP to Key.PAGE_UP,
            KeyEvent.VK_PAGE_DOWN to Key.PAGE_DOWN,
        )
    }
}
```

- [ ] **Step 5: Implement `PhoneTypingSession.kt`**

```kotlin
package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyPressCommand
import com.saubh.deskbuddy.protocol.KeyTextCommand
import com.saubh.deskbuddy.protocol.KeyboardModeCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.event.KeyEvent

/**
 * The PC's "type on phone" mode. [send] queues a command for the phone (in order);
 * [phoneName] is the connected phone, or null. Called from the hotkey thread and the AWT thread.
 */
class PhoneTypingSession(
    private val send: (Command) -> Unit,
    private val phoneName: () -> String?,
    private val translator: AwtKeyTranslator = AwtKeyTranslator(),
) {
    private companion object {
        const val ECHO_CHARS = 20
    }

    private val _state = MutableStateFlow<TypingState>(TypingState.Idle)
    val state: StateFlow<TypingState> = _state.asStateFlow()

    private val _echo = MutableStateFlow("")
    val echo: StateFlow<String> = _echo.asStateFlow()

    @Synchronized
    fun toggle() = if (_state.value is TypingState.Active) exit(ExitReason.HOTKEY) else enter()

    @Synchronized
    fun enter() {
        if (_state.value is TypingState.Active) return
        val name = phoneName()
        if (name == null) {
            _state.value = TypingState.NoPhone
            return
        }
        translator.reset()
        _echo.value = ""
        _state.value = TypingState.Active(name)
        send(KeyboardModeCommand(active = true))
    }

    @Synchronized
    fun exit(reason: ExitReason) {
        if (_state.value !is TypingState.Active) return
        send(KeyboardModeCommand(active = false))
        _state.value = if (reason == ExitReason.PHONE_DISCONNECTED) TypingState.PhoneDisconnected else TypingState.Idle
    }

    /** True when the key was consumed (every key is, while active). */
    @Synchronized
    fun onKey(raw: RawKey): Boolean {
        if (_state.value !is TypingState.Active) return false
        if (raw.type == RawKey.Type.PRESSED && raw.keyCode == KeyEvent.VK_ESCAPE) {
            exit(ExitReason.ESCAPE)
            return true
        }
        translator.translate(raw).forEach { command ->
            when {
                command is KeyTextCommand -> _echo.value = (_echo.value + command.text).takeLast(ECHO_CHARS)
                command is KeyPressCommand && command.key == Key.BACKSPACE -> _echo.value = _echo.value.dropLast(1)
            }
            send(command)
        }
        return true
    }

    @Synchronized
    fun dismissNotice() {
        if (_state.value is TypingState.NoPhone || _state.value is TypingState.PhoneDisconnected) _state.value = TypingState.Idle
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :shared:jvmTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add shared/src
git commit -m "feat(keyboard): translate PC key events and track type-on-phone mode"
```

---

### Task 12: Desktop hotkey, overlay, tray item and status chip

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/keyboard/WindowsGlobalHotkey.kt`
- Modify: `shared/src/commonMain/kotlin/com/saubh/deskbuddy/ui/theme/DeskBuddyIcons.kt`
- Create: `desktopApp/src/main/kotlin/com/saubh/deskbuddy/ui/KeyboardOverlay.kt`
- Modify: `desktopApp/src/main/kotlin/com/saubh/deskbuddy/DesktopController.kt`
- Modify: `desktopApp/src/main/kotlin/com/saubh/deskbuddy/main.kt`
- Modify: `desktopApp/src/main/kotlin/com/saubh/deskbuddy/ui/ServerScreen.kt`, `ui/StatusPanel.kt`

**Interfaces:**
- Consumes: `PhoneTypingSession`, `TypingState`, `ExitReason`, `RawKey` (Task 11); `KeyboardModeCommand` (Task 9).
- Produces: `WindowsGlobalHotkey(onPressed).start(): Boolean`, `.stop()`; `DeskBuddyIcons.Keyboard`; `DesktopController.typingState`, `.typingEcho`, `.hotkeyAvailable`, `togglePhoneTyping()`, `onTypingKey(RawKey): Boolean`, `onTypingFocusLost()`, `dismissTypingNotice()`.

- [ ] **Step 1: Hotkey** — `WindowsGlobalHotkey.kt`:

```kotlin
package com.saubh.deskbuddy.server.keyboard

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Ctrl+Alt+K via `RegisterHotKey` on a dedicated message-loop thread. [onPressed] runs on that thread.
 * Receiving WM_HOTKEY also lets this process take the foreground, so the overlay can grab focus.
 */
class WindowsGlobalHotkey(private val onPressed: () -> Unit) {
    private companion object {
        const val HOTKEY_ID = 0x4442
        const val MOD_ALT = 0x0001
        const val MOD_CONTROL = 0x0002
        const val MOD_NOREPEAT = 0x4000
        const val VK_K = 0x4B
        const val WM_HOTKEY = 0x0312
        const val WM_QUIT = 0x0012
        const val START_TIMEOUT_MS = 2_000L
    }

    @Volatile
    private var threadId = 0

    /** False when registration failed, e.g. another app already owns Ctrl+Alt+K. */
    fun start(): Boolean {
        val registered = CompletableFuture<Boolean>()
        thread(isDaemon = true, name = "deskbuddy-hotkey") {
            threadId = Kernel32.INSTANCE.GetCurrentThreadId()
            val ok = User32.INSTANCE.RegisterHotKey(null, HOTKEY_ID, MOD_CONTROL or MOD_ALT or MOD_NOREPEAT, VK_K)
            registered.complete(ok)
            if (!ok) return@thread
            val msg = WinUser.MSG()
            while (User32.INSTANCE.GetMessage(msg, null, 0, 0) > 0) {
                if (msg.message == WM_HOTKEY) runCatching { onPressed() }
            }
            User32.INSTANCE.UnregisterHotKey(null, HOTKEY_ID)
        }
        return runCatching { registered.get(START_TIMEOUT_MS, TimeUnit.MILLISECONDS) }.getOrDefault(false)
    }

    fun stop() {
        val id = threadId
        if (id != 0) User32.INSTANCE.PostThreadMessage(id, WM_QUIT, WinDef.WPARAM(0), WinDef.LPARAM(0))
    }
}
```

- [ ] **Step 2: Icon** — in `DeskBuddyIcons`: `val Keyboard = Icons.Rounded.Keyboard`

- [ ] **Step 3: Controller wiring** — in `DesktopController` (imports: `com.saubh.deskbuddy.protocol.Command`, `com.saubh.deskbuddy.protocol.KeyboardModeCommand`, `com.saubh.deskbuddy.server.keyboard.*` classes, `kotlinx.coroutines.channels.Channel`, `kotlinx.coroutines.withTimeoutOrNull`):

```kotlin
    /** One consumer keeps the PC's keystrokes in order on their way to the phone. */
    private val keyOutbox = Channel<Command>(Channel.UNLIMITED)
    private val typing = PhoneTypingSession(
        send = { keyOutbox.trySend(it) },
        phoneName = { (server.state.value as? ServerState.Connected)?.devices?.lastOrNull() },
    )
    private val hotkey: WindowsGlobalHotkey? = if (isWindows) WindowsGlobalHotkey(typing::toggle) else null

    val typingState: StateFlow<TypingState> = typing.state
    val typingEcho: StateFlow<String> = typing.echo
    private val _hotkeyAvailable = MutableStateFlow(false)
    val hotkeyAvailable: StateFlow<Boolean> = _hotkeyAvailable.asStateFlow()

    fun togglePhoneTyping() = typing.toggle()
    fun onTypingKey(key: RawKey): Boolean = typing.onKey(key)
    fun onTypingFocusLost() = typing.exit(ExitReason.FOCUS_LOST)
    fun dismissTypingNotice() = typing.dismissNotice()
```

Declare `isWindows` above these (it already exists — move the `typing`/`hotkey` block below it). At the end of `start()`:

```kotlin
        scope.launch { for (command in keyOutbox) server.push(Push(command)) }
        scope.launch { serverState.collect { if (it is ServerState.Waiting) typing.exit(ExitReason.PHONE_DISCONNECTED) } }
        _hotkeyAvailable.value = hotkey?.start() == true
```

At the start of `stop()` (inside its `withContext`):

```kotlin
        val wasTyping = typing.state.value is TypingState.Active
        typing.exit(ExitReason.QUIT)
        if (wasTyping) withTimeoutOrNull(500) { server.push(Push(KeyboardModeCommand(active = false))) }
        hotkey?.stop()
```

- [ ] **Step 4: Overlay** — `desktopApp/.../ui/KeyboardOverlay.kt`:

```kotlin
package com.saubh.deskbuddy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.saubh.deskbuddy.server.keyboard.RawKey
import com.saubh.deskbuddy.server.keyboard.TypingState
import com.saubh.deskbuddy.ui.theme.DeskBuddyIcons
import com.saubh.deskbuddy.ui.theme.DeskBuddyTheme
import kotlinx.coroutines.delay
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener

private const val NOTICE_MS = 1_500L

/**
 * Always-on-top bar that holds keyboard focus while the PC types on the phone. Every key event in
 * the app is routed to [onKey] through a KeyEventDispatcher (before focus traversal eats Tab).
 */
@Composable
fun KeyboardOverlay(
    state: TypingState,
    echo: String,
    onKey: (RawKey) -> Boolean,
    onFocusLost: () -> Unit,
    onDismiss: () -> Unit,
) {
    val windowState = rememberWindowState(position = WindowPosition(Alignment.TopCenter), size = DpSize(640.dp, 76.dp))
    val currentOnKey by rememberUpdatedState(onKey)
    val currentOnFocusLost by rememberUpdatedState(onFocusLost)
    Window(
        onCloseRequest = onDismiss,
        state = windowState,
        title = "DeskBuddy keyboard",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        focusable = true,
    ) {
        var focused by remember { mutableStateOf(false) }
        DisposableEffect(window) {
            val dispatcher = KeyEventDispatcher { event -> currentOnKey(event.toRawKey()) }
            val focusListener = object : WindowFocusListener {
                override fun windowGainedFocus(e: WindowEvent) {
                    focused = true
                }

                override fun windowLostFocus(e: WindowEvent) {
                    if (focused) currentOnFocusLost()
                }
            }
            val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            manager.addKeyEventDispatcher(dispatcher)
            window.addWindowFocusListener(focusListener)
            window.toFront()
            window.requestFocus()
            onDispose {
                window.removeWindowFocusListener(focusListener)
                manager.removeKeyEventDispatcher(dispatcher)
            }
        }
        if (state !is TypingState.Active) {
            LaunchedEffect(state) {
                delay(NOTICE_MS)
                onDismiss()
            }
        }
        DeskBuddyTheme { OverlayBar(state, echo, focused) }
    }
}

@Composable
private fun OverlayBar(state: TypingState, echo: String, focused: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val message = when (state) {
        is TypingState.Active ->
            if (focused) "Typing on ${state.phoneName} — Ctrl+Alt+K or Esc to return" else "Click here to type to your phone"
        TypingState.NoPhone -> "No phone connected"
        TypingState.PhoneDisconnected -> "Phone disconnected"
        TypingState.Idle -> ""
    }
    Surface(
        shape = RoundedCornerShape(36.dp),
        color = scheme.inverseSurface,
        contentColor = scheme.inverseOnSurface,
        modifier = Modifier.fillMaxSize().padding(4.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(DeskBuddyIcons.Keyboard, contentDescription = null)
            Text(message, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (state is TypingState.Active && echo.isNotEmpty()) {
                Text(echo, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            }
        }
    }
}

private fun KeyEvent.toRawKey() = RawKey(
    type = when (id) {
        KeyEvent.KEY_PRESSED -> RawKey.Type.PRESSED
        KeyEvent.KEY_RELEASED -> RawKey.Type.RELEASED
        else -> RawKey.Type.TYPED
    },
    keyCode = keyCode,
    char = keyChar,
    ctrl = isControlDown,
    alt = isAltDown,
    shift = isShiftDown,
    altGraph = isAltGraphDown,
)
```

- [ ] **Step 5: Wire into `main.kt`** — inside `if (!quitting) { ... }` (imports `androidx.compose.runtime.collectAsState`, `com.saubh.deskbuddy.server.keyboard.TypingState`, `com.saubh.deskbuddy.ui.KeyboardOverlay`):

```kotlin
            val typing by controller.typingState.collectAsState()
            val echo by controller.typingEcho.collectAsState()
            Tray(
                icon = appIcon,
                tooltip = "DeskBuddy",
                onAction = show,
                menu = {
                    Item("Open DeskBuddy", onClick = show)
                    Item(if (controller.isWindows) "Type on phone (Ctrl+Alt+K)" else "Type on phone", onClick = controller::togglePhoneTyping)
                    Item("Quit", onClick = quit)
                },
            )
            if (typing != TypingState.Idle) {
                KeyboardOverlay(
                    state = typing,
                    echo = echo,
                    onKey = controller::onTypingKey,
                    onFocusLost = controller::onTypingFocusLost,
                    onDismiss = controller::dismissTypingNotice,
                )
            }
            Window(/* unchanged */)
```

- [ ] **Step 6: Status chip** — `StatusPanel` gets a new parameter `keyboardLabel: String` (after `mediaHelperAvailable`). Change the chip `Row` to a `FlowRow` (import `androidx.compose.foundation.layout.FlowRow`, `androidx.compose.foundation.layout.ExperimentalLayoutApi`; add `ExperimentalLayoutApi::class` to the file's `@file:OptIn`) with `horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)`, and add a fourth chip:

```kotlin
            AssistChip(
                onClick = {},
                label = { Text(keyboardLabel) },
                leadingIcon = { Icon(DeskBuddyIcons.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = CircleShape,
            )
```

In `ServerScreen`: `val hotkey by controller.hotkeyAvailable.collectAsState()` and pass

```kotlin
                                keyboardLabel = when {
                                    !controller.isWindows -> "Keyboard: tray menu → Type on phone"
                                    hotkey -> "Keyboard: Ctrl+Alt+K"
                                    else -> "Keyboard: hotkey unavailable — use the tray menu"
                                },
```

- [ ] **Step 7: Verify**

Run: `./gradlew :shared:jvmTest :desktopApp:build`
Expected: PASS / BUILD SUCCESSFUL.
Manual (Windows, phone connected): `./gradlew :desktopApp:run`; press Ctrl+Alt+K → bar appears at top centre and has focus; type → the characters echo in the bar; Esc → bar closes. With no phone: Ctrl+Alt+K shows "No phone connected" for 1.5 s.

- [ ] **Step 8: Commit**

```bash
git add shared/src desktopApp/src
git commit -m "feat(keyboard): Ctrl+Alt+K overlay forwards PC keystrokes to the phone"
```

---

### Task 13: Phone typing logic — text diff, sticky modifiers, controller (pure + tests)

**Files:**
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/keyboard/TextDiff.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/keyboard/ModifierState.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/keyboard/KeyboardController.kt`
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/AppGraph.kt`
- Test: `androidApp/src/test/kotlin/com/saubh/deskbuddy/keyboard/TextDiffTest.kt`
- Test: `androidApp/src/test/kotlin/com/saubh/deskbuddy/keyboard/ModifierStateTest.kt`
- Test: `androidApp/src/test/kotlin/com/saubh/deskbuddy/keyboard/KeyboardControllerTest.kt`

**Interfaces:**
- Consumes: `FakeGateway` (Task 6), protocol types (Task 9).
- Produces: `TextDiff.SENTINEL`, `TextDiff.between(old, new): TextDiff.Result(backspaces, inserted)`, `TextDiff.display(new): String`; `ModifierState(oneShot, locked)` with `active`, `tap(m)`, `consumed()`; `KeyboardController(gateway: MediaGateway, scope)` with `modifiers: StateFlow<ModifierState>`, `onFieldEdit(old, new): String`, `enter()`, `press(key)`, `combo(key, modifiers)`, `tapModifier(m)`; `AppGraph.keyboard`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.saubh.deskbuddy.keyboard

import com.saubh.deskbuddy.keyboard.TextDiff.SENTINEL
import kotlin.test.Test
import kotlin.test.assertEquals

class TextDiffTest {
    @Test
    fun `between_givenAppend_insertsOnlyNewText`() {
        assertEquals(TextDiff.Result(0, "lo"), TextDiff.between("${SENTINEL}hel", "${SENTINEL}hello"))
    }

    @Test
    fun `between_givenAutocorrectReplace_backspacesThenRetypes`() {
        // Common prefix is sentinel + "t": delete "eh", type "he ".
        assertEquals(TextDiff.Result(2, "he "), TextDiff.between("${SENTINEL}teh", "${SENTINEL}the "))
    }

    @Test
    fun `between_givenSentinelDeleted_countsOneBackspace`() {
        assertEquals(TextDiff.Result(1, ""), TextDiff.between(SENTINEL, ""))
    }

    @Test
    fun `between_givenEmojiReplaced_doesNotSplitSurrogates`() {
        // 😀 = D83D DE00, 😃 = D83D DE03: same high surrogate.
        assertEquals(TextDiff.Result(1, "😃"), TextDiff.between("${SENTINEL}a😀", "${SENTINEL}a😃"))
    }

    @Test
    fun `display_givenSentinelRemoved_restoresIt`() {
        assertEquals(SENTINEL, TextDiff.display(""))
        assertEquals("${SENTINEL}x", TextDiff.display("${SENTINEL}x"))
    }
}
```

```kotlin
package com.saubh.deskbuddy.keyboard

import com.saubh.deskbuddy.protocol.KeyModifier.CTRL
import kotlin.test.Test
import kotlin.test.assertEquals

class ModifierStateTest {
    @Test
    fun `tap_givenRepeatedTaps_cyclesOneShotLockedOff`() {
        val once = ModifierState().tap(CTRL)
        val twice = once.tap(CTRL)
        val thrice = twice.tap(CTRL)
        assertEquals(setOf(CTRL), once.oneShot)
        assertEquals(setOf(CTRL), twice.locked)
        assertEquals(emptySet(), thrice.active)
    }

    @Test
    fun `consumed_givenOneShotAndLocked_clearsOnlyOneShot`() {
        val state = ModifierState(oneShot = setOf(CTRL), locked = setOf(com.saubh.deskbuddy.protocol.KeyModifier.SHIFT)).consumed()
        assertEquals(setOf(com.saubh.deskbuddy.protocol.KeyModifier.SHIFT), state.active)
    }
}
```

```kotlin
package com.saubh.deskbuddy.keyboard

import com.saubh.deskbuddy.FakeGateway
import com.saubh.deskbuddy.keyboard.TextDiff.SENTINEL
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.saubh.deskbuddy.protocol.KeyPressCommand
import com.saubh.deskbuddy.protocol.KeyTextCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardControllerTest {
    @Test
    fun `onFieldEdit_givenAutocorrect_sendsBackspacesThenTextInOrder`() = runTest {
        val gateway = FakeGateway()
        val controller = KeyboardController(gateway, backgroundScope)
        controller.onFieldEdit("${SENTINEL}teh", "${SENTINEL}the")
        runCurrent()
        assertEquals(
            listOf<Command>(KeyPressCommand(Key.BACKSPACE), KeyPressCommand(Key.BACKSPACE), KeyTextCommand("he")),
            gateway.sent,
        )
    }

    @Test
    fun `onFieldEdit_givenArmedCtrlAndLetter_sendsComboAndKeepsField`() = runTest {
        val gateway = FakeGateway()
        val controller = KeyboardController(gateway, backgroundScope)
        controller.tapModifier(KeyModifier.CTRL)
        val display = controller.onFieldEdit(SENTINEL, "${SENTINEL}s")
        runCurrent()
        assertEquals(SENTINEL, display)
        assertEquals(listOf<Command>(KeyPressCommand(Key.S, setOf(KeyModifier.CTRL))), gateway.sent)
        assertEquals(emptySet(), controller.modifiers.value.active)
    }

    @Test
    fun `onFieldEdit_givenLongPaste_splitsIntoChunksUnderDesktopLimit`() = runTest {
        val gateway = FakeGateway()
        val controller = KeyboardController(gateway, backgroundScope)
        controller.onFieldEdit(SENTINEL, SENTINEL + "x".repeat(2_500))
        runCurrent()
        assertEquals(listOf(1_000, 1_000, 500), gateway.sent.map { (it as KeyTextCommand).text.length })
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.saubh.deskbuddy.keyboard.*"`
Expected: compile FAIL.

- [ ] **Step 3: Implement `TextDiff.kt`**

```kotlin
package com.saubh.deskbuddy.keyboard

/**
 * Turns successive values of the phone's typing field into what the PC must do, assuming the PC's
 * caret sits at the end: delete [Result.backspaces] characters, then type [Result.inserted].
 * The field always starts with [SENTINEL] so Backspace on an "empty" field is still an edit.
 */
object TextDiff {
    const val SENTINEL = "​"

    data class Result(val backspaces: Int, val inserted: String)

    fun between(old: String, new: String): Result {
        var prefix = 0
        val max = minOf(old.length, new.length)
        while (prefix < max && old[prefix] == new[prefix]) prefix++
        // Never split a surrogate pair: back up to its start.
        if (prefix > 0 && Character.isHighSurrogate(old[prefix - 1])) prefix--
        // One PC Backspace deletes one code point (an emoji is one Backspace, not two).
        val backspaces = old.codePointCount(prefix, old.length)
        return Result(backspaces, new.substring(prefix).replace(SENTINEL, ""))
    }

    /** The value to show after an edit: the sentinel always leads. */
    fun display(new: String): String = if (new.startsWith(SENTINEL)) new else SENTINEL + new
}
```

- [ ] **Step 4: Implement `ModifierState.kt`**

```kotlin
package com.saubh.deskbuddy.keyboard

import com.saubh.deskbuddy.protocol.KeyModifier

/** Sticky modifiers: first tap = next key only, second tap = locked, third tap = off. */
data class ModifierState(
    val oneShot: Set<KeyModifier> = emptySet(),
    val locked: Set<KeyModifier> = emptySet(),
) {
    val active: Set<KeyModifier> get() = oneShot + locked

    fun tap(modifier: KeyModifier): ModifierState = when (modifier) {
        in locked -> copy(locked = locked - modifier)
        in oneShot -> copy(oneShot = oneShot - modifier, locked = locked + modifier)
        else -> copy(oneShot = oneShot + modifier)
    }

    /** After a key was sent: one-shot modifiers are used up, locked ones stay. */
    fun consumed(): ModifierState = copy(oneShot = emptySet())
}
```

- [ ] **Step 5: Implement `KeyboardController.kt`**

```kotlin
package com.saubh.deskbuddy.keyboard

import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.saubh.deskbuddy.protocol.KeyPressCommand
import com.saubh.deskbuddy.protocol.KeyTextCommand
import com.saubh.deskbuddy.session.MediaGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Phone → PC typing: field edits become keystrokes, sent strictly in order through one outbox. */
class KeyboardController(private val gateway: MediaGateway, scope: CoroutineScope) {
    private companion object {
        /** Well under the desktop's 4096-char limit per key_text. */
        const val MAX_CHUNK = 1_000
    }

    private val _modifiers = MutableStateFlow(ModifierState())
    val modifiers: StateFlow<ModifierState> = _modifiers.asStateFlow()

    private val outbox = Channel<Command>(Channel.UNLIMITED)

    init {
        scope.launch { for (command in outbox) gateway.send(command) }
    }

    /** Returns what the field should show now (the old value when a combo consumed the typed letter). */
    fun onFieldEdit(old: String, new: String): String {
        val edit = TextDiff.between(old, new)
        val active = _modifiers.value.active
        val comboKey = edit.inserted.singleOrNull()?.let(Key::forChar)
        if (active.isNotEmpty() && edit.backspaces == 0 && comboKey != null) {
            outbox.trySend(KeyPressCommand(comboKey, active))
            _modifiers.update { it.consumed() }
            return old
        }
        repeat(edit.backspaces) { outbox.trySend(KeyPressCommand(Key.BACKSPACE)) }
        edit.inserted.chunked(MAX_CHUNK).forEach { outbox.trySend(KeyTextCommand(it)) }
        return TextDiff.display(new)
    }

    fun enter() = press(Key.ENTER)

    fun press(key: Key) {
        outbox.trySend(KeyPressCommand(key, _modifiers.value.active))
        _modifiers.update { it.consumed() }
    }

    fun combo(key: Key, modifiers: Set<KeyModifier>) {
        outbox.trySend(KeyPressCommand(key, modifiers))
    }

    fun tapModifier(modifier: KeyModifier) = _modifiers.update { it.tap(modifier) }
}
```

`AppGraph`: `val keyboard = KeyboardController(session, scope)` (after `media`; import `com.saubh.deskbuddy.keyboard.KeyboardController`).

- [ ] **Step 6: Run the tests**

Run: `./gradlew :androidApp:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add androidApp/src
git commit -m "feat(keyboard): phone text diff, sticky modifiers and ordered keystroke sending"
```

---

### Task 14: Keyboard tab UI

**Files:**
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/keyboard/ImeStatus.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/keyboard/KeyboardTab.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/keyboard/SpecialKeyRow.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/keyboard/KeyboardSetupCard.kt`
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/RemoteScreen.kt` (`RemoteTab`, `TabContent`)
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/ConnectionViewModel.kt`
- Modify: `androidApp/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `KeyboardController`, `ModifierState`, `TextDiff.SENTINEL` (Task 13); `DeskBuddyIcons.Keyboard` (Task 12).
- Produces: `ImeStatus.isEnabled(context)`, `ImeStatus.isCurrent(context)`, `ImeStatus.openSettings(context)`; `RemoteTab.KEYBOARD`; `ConnectionViewModel.keyboard`.

- [ ] **Step 1: Strings** — add to `strings.xml` after `tab_share`:

```xml
    <string name="tab_keyboard">Keyboard</string>

    <string name="keyboard_typing_into">Typing into %1$s</string>
    <string name="keyboard_field_label">Type here</string>
    <string name="keyboard_field_hint">Letters appear on the PC as you type. Send presses Enter.</string>
    <string name="keyboard_not_connected">Waiting for the PC to reconnect…</string>
    <string name="key_esc">Esc</string>
    <string name="key_tab">Tab</string>
    <string name="key_left">←</string>
    <string name="key_up">↑</string>
    <string name="key_down">↓</string>
    <string name="key_right">→</string>
    <string name="key_del">Del</string>
    <string name="key_home">Home</string>
    <string name="key_end">End</string>
    <string name="key_pgup">PgUp</string>
    <string name="key_pgdn">PgDn</string>
    <string name="key_ctrl">Ctrl</string>
    <string name="key_alt">Alt</string>
    <string name="key_shift">Shift</string>
    <string name="key_win">Win</string>
    <string name="combo_alt_tab">Alt+Tab</string>
    <string name="combo_win_d">Win+D</string>
    <string name="combo_copy">Ctrl+C</string>
    <string name="combo_paste">Ctrl+V</string>
    <string name="combo_undo">Ctrl+Z</string>
    <string name="combo_select_all">Ctrl+A</string>
    <string name="cd_modifier_locked">%1$s locked</string>
    <string name="keyboard_setup_title">Type on this phone from your PC</string>
    <string name="keyboard_setup_text">Turn on DeskBuddy Keyboard, then press Ctrl+Alt+K on the PC to type into any app on this phone.</string>
    <string name="keyboard_setup_warning">Android will warn that this keyboard may collect all text you type. DeskBuddy only types what your PC sends and stores nothing.</string>
    <string name="keyboard_setup_button">Turn on DeskBuddy Keyboard</string>
```

- [ ] **Step 2: `ImeStatus.kt`**

```kotlin
package com.saubh.deskbuddy.keyboard

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/** Whether DeskBuddy Keyboard is enabled / the current keyboard, and the settings shortcut. */
object ImeStatus {
    fun isEnabled(context: Context): Boolean =
        context.getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.packageName == context.packageName }

    fun isCurrent(context: Context): Boolean =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
            .startsWith(context.packageName + "/")

    fun openSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
```

- [ ] **Step 3: `SpecialKeyRow.kt`**

```kotlin
package com.saubh.deskbuddy.ui.keyboard

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.keyboard.ModifierState
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.saubh.deskbuddy.ui.theme.DeskBuddyIcons

private data class KeySpec(val key: Key, @StringRes val label: Int)
private data class ModifierSpec(val modifier: KeyModifier, @StringRes val label: Int)
private data class ComboSpec(val key: Key, val modifiers: Set<KeyModifier>, @StringRes val label: Int)

private val NAV_KEYS = listOf(
    KeySpec(Key.ESCAPE, R.string.key_esc), KeySpec(Key.TAB, R.string.key_tab),
    KeySpec(Key.LEFT, R.string.key_left), KeySpec(Key.UP, R.string.key_up),
    KeySpec(Key.DOWN, R.string.key_down), KeySpec(Key.RIGHT, R.string.key_right),
    KeySpec(Key.DELETE, R.string.key_del), KeySpec(Key.HOME, R.string.key_home),
    KeySpec(Key.END, R.string.key_end), KeySpec(Key.PAGE_UP, R.string.key_pgup),
    KeySpec(Key.PAGE_DOWN, R.string.key_pgdn),
)
private val MODIFIERS = listOf(
    ModifierSpec(KeyModifier.CTRL, R.string.key_ctrl), ModifierSpec(KeyModifier.ALT, R.string.key_alt),
    ModifierSpec(KeyModifier.SHIFT, R.string.key_shift), ModifierSpec(KeyModifier.META, R.string.key_win),
)
private val COMBOS = listOf(
    ComboSpec(Key.TAB, setOf(KeyModifier.ALT), R.string.combo_alt_tab),
    ComboSpec(Key.D, setOf(KeyModifier.META), R.string.combo_win_d),
    ComboSpec(Key.C, setOf(KeyModifier.CTRL), R.string.combo_copy),
    ComboSpec(Key.V, setOf(KeyModifier.CTRL), R.string.combo_paste),
    ComboSpec(Key.Z, setOf(KeyModifier.CTRL), R.string.combo_undo),
    ComboSpec(Key.A, setOf(KeyModifier.CTRL), R.string.combo_select_all),
)

/** Keys a phone keyboard lacks: navigation keys, sticky modifiers and common combos. */
@Composable
fun SpecialKeyRow(
    modifiers: ModifierState,
    onKey: (Key) -> Unit,
    onCombo: (Key, Set<KeyModifier>) -> Unit,
    onModifierTap: (KeyModifier) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NAV_KEYS.forEach { spec -> AssistChip(onClick = { onKey(spec.key) }, label = { Text(stringResource(spec.label)) }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MODIFIERS.forEach { spec ->
                val label = stringResource(spec.label)
                val locked = spec.modifier in modifiers.locked
                FilterChip(
                    selected = spec.modifier in modifiers.active,
                    onClick = { onModifierTap(spec.modifier) },
                    label = { Text(label) },
                    leadingIcon = if (locked) {
                        { Icon(DeskBuddyIcons.Lock, contentDescription = stringResource(R.string.cd_modifier_locked, label), modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
                )
            }
            COMBOS.forEach { spec -> AssistChip(onClick = { onCombo(spec.key, spec.modifiers) }, label = { Text(stringResource(spec.label)) }) }
        }
    }
}
```

- [ ] **Step 4: `KeyboardSetupCard.kt`**

```kotlin
package com.saubh.deskbuddy.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R

/** Shown until DeskBuddy Keyboard is enabled: explains PC → phone typing and opens keyboard settings. */
@Composable
fun KeyboardSetupCard(onEnable: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.keyboard_setup_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.keyboard_setup_text), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.keyboard_setup_warning), style = MaterialTheme.typography.bodySmall)
            Button(onClick = onEnable) { Text(stringResource(R.string.keyboard_setup_button)) }
        }
    }
}
```

- [ ] **Step 5: `KeyboardTab.kt`**

```kotlin
package com.saubh.deskbuddy.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.keyboard.ModifierState
import com.saubh.deskbuddy.keyboard.TextDiff
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.saubh.deskbuddy.ui.FabClearance

private fun emptyField() = TextFieldValue(TextDiff.SENTINEL, TextRange(TextDiff.SENTINEL.length))

/** Live typing into the PC's focused window, plus the special-key row and the PC → phone setup card. */
@Composable
fun KeyboardTab(
    desktopName: String,
    connected: Boolean,
    modifiers: ModifierState,
    imeEnabled: Boolean,
    onFieldEdit: (old: String, new: String) -> String,
    onEnter: () -> Unit,
    onKey: (Key) -> Unit,
    onCombo: (Key, Set<KeyModifier>) -> Unit,
    onModifierTap: (KeyModifier) -> Unit,
    onEnableIme: () -> Unit,
) {
    var field by remember { mutableStateOf(emptyField()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(connected) { if (connected) focus.requestFocus() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (connected) stringResource(R.string.keyboard_typing_into, desktopName) else stringResource(R.string.keyboard_not_connected),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = field,
            onValueChange = { new ->
                val display = onFieldEdit(field.text, new.text)
                field = if (display == new.text) new else TextFieldValue(display, TextRange(display.length))
            },
            enabled = connected,
            singleLine = true,
            label = { Text(stringResource(R.string.keyboard_field_label)) },
            supportingText = { Text(stringResource(R.string.keyboard_field_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                onEnter()
                field = emptyField()
            }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        if (connected) SpecialKeyRow(modifiers, onKey, onCombo, onModifierTap)
        if (!imeEnabled) KeyboardSetupCard(onEnableIme)
        Spacer(Modifier.height(FabClearance))
    }
}
```

- [ ] **Step 6: Wire the tab**

`RemoteTab`: add `KEYBOARD(R.string.tab_keyboard, DeskBuddyIcons.Keyboard),` after `SHARE`.

`RemoteScreen`: pass the title and connection into `TabContent`: `) { TabContent(tab, isWide, title, state is ConnectionUiState.Connected, viewModel) }` and change the signature to `private fun TabContent(tab: RemoteTab, isWide: Boolean, desktopName: String, connected: Boolean, viewModel: ConnectionViewModel)`. Add the branch (imports `androidx.compose.ui.platform.LocalContext`, `androidx.lifecycle.compose.LifecycleResumeEffect`, `androidx.compose.runtime.remember`, `com.saubh.deskbuddy.keyboard.ImeStatus`, `com.saubh.deskbuddy.ui.keyboard.KeyboardTab`):

```kotlin
        RemoteTab.KEYBOARD -> {
            val modifiers by viewModel.keyboard.modifiers.collectAsStateWithLifecycle()
            val context = LocalContext.current
            var imeEnabled by remember { mutableStateOf(ImeStatus.isEnabled(context)) }
            // Re-check when returning from Android's keyboard settings.
            LifecycleResumeEffect(Unit) {
                imeEnabled = ImeStatus.isEnabled(context)
                onPauseOrDispose { }
            }
            KeyboardTab(
                desktopName = desktopName,
                connected = connected,
                modifiers = modifiers,
                imeEnabled = imeEnabled,
                onFieldEdit = viewModel.keyboard::onFieldEdit,
                onEnter = viewModel.keyboard::enter,
                onKey = viewModel.keyboard::press,
                onCombo = viewModel.keyboard::combo,
                onModifierTap = viewModel.keyboard::tapModifier,
                onEnableIme = { ImeStatus.openSettings(context) },
            )
        }
```

`ConnectionViewModel`: `val keyboard = graph.keyboard`.

- [ ] **Step 7: Verify**

Run: `./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:lintDebug`
Expected: PASS / BUILD SUCCESSFUL, lint 0 errors.
Manual: open the Keyboard tab while connected, type "hello" → it appears in Notepad on the PC; tap Ctrl then type `a` → Notepad selects all.

- [ ] **Step 8: Commit**

```bash
git add androidApp/src
git commit -m "feat(keyboard): Keyboard tab with live typing and special keys"
```

---

### Task 15: PC → phone — keyboard hub, key mapping, DeskBuddy Keyboard IME

**Files:**
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/keyboard/RemoteKeyboardHub.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/keyboard/AndroidKeyMapping.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ime/DeskBuddyInputMethodService.kt`
- Create: `androidApp/src/main/res/layout/ime_bar.xml`
- Create: `androidApp/src/main/res/xml/deskbuddy_method.xml`
- Modify: `androidApp/src/main/AndroidManifest.xml`
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/AppGraph.kt`
- Modify: `androidApp/src/main/res/values/strings.xml`
- Test: `androidApp/src/test/kotlin/com/saubh/deskbuddy/keyboard/RemoteKeyboardHubTest.kt`
- Test: `androidApp/src/test/kotlin/com/saubh/deskbuddy/keyboard/AndroidKeyMappingTest.kt`

**Interfaces:**
- Produces: `RemoteKeyboardHub(gateway: MediaGateway, scope)` with `pcTyping: StateFlow<Boolean>`, `input: SharedFlow<Command>`, `imeBound: StateFlow<Boolean>`, `setImeBound(Boolean)`; `AndroidKeyMapping.keyCode(Key)`, `.metaState(Set<KeyModifier>)`, `.enterAction(imeOptions): Int?`; `AppGraph.keyboardHub`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.saubh.deskbuddy.keyboard

import com.saubh.deskbuddy.FakeGateway
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.KeyTextCommand
import com.saubh.deskbuddy.protocol.KeyboardModeCommand
import com.saubh.deskbuddy.protocol.Push
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteKeyboardHubTest {
    @Test
    fun `pcTyping_givenModePushes_follows`() = runTest {
        val gateway = FakeGateway()
        val hub = RemoteKeyboardHub(gateway, backgroundScope).also { runCurrent() }
        gateway.incoming.emit(Push(KeyboardModeCommand(true)))
        runCurrent()
        assertTrue(hub.pcTyping.value)
        gateway.incoming.emit(Push(KeyboardModeCommand(false)))
        runCurrent()
        assertFalse(hub.pcTyping.value)
    }

    @Test
    fun `pcTyping_givenDisconnect_resetsToFalse`() = runTest {
        val gateway = FakeGateway()
        val hub = RemoteKeyboardHub(gateway, backgroundScope).also { runCurrent() }
        gateway.incoming.emit(Push(KeyboardModeCommand(true)))
        runCurrent()
        gateway.connectedState.value = false
        runCurrent()
        assertFalse(hub.pcTyping.value)
    }

    @Test
    fun `input_givenKeyPush_forwardsCommand`() = runTest {
        val gateway = FakeGateway()
        val hub = RemoteKeyboardHub(gateway, backgroundScope).also { runCurrent() }
        val received = mutableListOf<Command>()
        backgroundScope.launch { hub.input.collect { received += it } }
        runCurrent()
        gateway.incoming.emit(Push(KeyTextCommand("hi")))
        runCurrent()
        assertEquals(listOf<Command>(KeyTextCommand("hi")), received)
    }
}
```

```kotlin
package com.saubh.deskbuddy.keyboard

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidKeyMappingTest {
    @Test
    fun `keyCode_givenLettersDigitsAndSpecials_mapsToKeyEventCodes`() {
        assertEquals(KeyEvent.KEYCODE_A, AndroidKeyMapping.keyCode(Key.A))
        assertEquals(KeyEvent.KEYCODE_Z, AndroidKeyMapping.keyCode(Key.Z))
        assertEquals(KeyEvent.KEYCODE_0, AndroidKeyMapping.keyCode(Key.DIGIT_0))
        assertEquals(KeyEvent.KEYCODE_9, AndroidKeyMapping.keyCode(Key.DIGIT_9))
        assertEquals(KeyEvent.KEYCODE_DEL, AndroidKeyMapping.keyCode(Key.BACKSPACE))
        assertEquals(KeyEvent.KEYCODE_FORWARD_DEL, AndroidKeyMapping.keyCode(Key.DELETE))
        assertEquals(KeyEvent.KEYCODE_MOVE_HOME, AndroidKeyMapping.keyCode(Key.HOME))
    }

    @Test
    fun `metaState_givenCtrlShift_setsBothFlags`() {
        val expected = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        assertEquals(expected, AndroidKeyMapping.metaState(setOf(KeyModifier.CTRL, KeyModifier.SHIFT)))
    }

    @Test
    fun `enterAction_givenSendField_returnsSend`() {
        assertEquals(EditorInfo.IME_ACTION_SEND, AndroidKeyMapping.enterAction(EditorInfo.IME_ACTION_SEND))
    }

    @Test
    fun `enterAction_givenNoEnterActionFlagOrNone_returnsNull`() {
        assertNull(AndroidKeyMapping.enterAction(EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION))
        assertNull(AndroidKeyMapping.enterAction(EditorInfo.IME_ACTION_UNSPECIFIED))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.saubh.deskbuddy.keyboard.*"`
Expected: compile FAIL.

- [ ] **Step 3: Implement `RemoteKeyboardHub.kt`**

```kotlin
package com.saubh.deskbuddy.keyboard

import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.KeyPressCommand
import com.saubh.deskbuddy.protocol.KeyTextCommand
import com.saubh.deskbuddy.protocol.KeyboardModeCommand
import com.saubh.deskbuddy.protocol.Push
import com.saubh.deskbuddy.session.MediaGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/** PC → phone typing: whether the PC keyboard is on, and the keystrokes for DeskBuddy Keyboard to apply. */
class RemoteKeyboardHub(gateway: MediaGateway, scope: CoroutineScope) {
    private val _pcTyping = MutableStateFlow(false)
    val pcTyping: StateFlow<Boolean> = _pcTyping.asStateFlow()

    /** Keystrokes with no bound keyboard are dropped (no replay). */
    private val _input = MutableSharedFlow<Command>(extraBufferCapacity = 256)
    val input: SharedFlow<Command> = _input.asSharedFlow()

    private val _imeBound = MutableStateFlow(false)
    val imeBound: StateFlow<Boolean> = _imeBound.asStateFlow()

    init {
        scope.launch {
            gateway.incoming.filterIsInstance<Push>().collect { push ->
                when (val command = push.command) {
                    is KeyboardModeCommand -> _pcTyping.value = command.active
                    is KeyTextCommand, is KeyPressCommand -> _input.emit(command)
                    else -> Unit
                }
            }
        }
        scope.launch { gateway.connected.collect { if (!it) _pcTyping.value = false } }
    }

    /** Set by the IME service while it is alive (it is the current keyboard). */
    fun setImeBound(bound: Boolean) {
        _imeBound.value = bound
    }
}
```

- [ ] **Step 4: Implement `AndroidKeyMapping.kt`**

```kotlin
package com.saubh.deskbuddy.keyboard

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier

/** Protocol keys → Android key codes / meta state, and the editor action Enter should trigger. */
object AndroidKeyMapping {
    fun keyCode(key: Key): Int = when (key) {
        Key.ENTER -> KeyEvent.KEYCODE_ENTER
        Key.BACKSPACE -> KeyEvent.KEYCODE_DEL
        Key.DELETE -> KeyEvent.KEYCODE_FORWARD_DEL
        Key.TAB -> KeyEvent.KEYCODE_TAB
        Key.ESCAPE -> KeyEvent.KEYCODE_ESCAPE
        Key.SPACE -> KeyEvent.KEYCODE_SPACE
        Key.UP -> KeyEvent.KEYCODE_DPAD_UP
        Key.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        Key.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        Key.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        Key.HOME -> KeyEvent.KEYCODE_MOVE_HOME
        Key.END -> KeyEvent.KEYCODE_MOVE_END
        Key.PAGE_UP -> KeyEvent.KEYCODE_PAGE_UP
        Key.PAGE_DOWN -> KeyEvent.KEYCODE_PAGE_DOWN
        // Key's letters and digits are contiguous, as are Android's KEYCODE_A..Z and KEYCODE_0..9.
        else -> if (key.ordinal >= Key.DIGIT_0.ordinal) {
            KeyEvent.KEYCODE_0 + (key.ordinal - Key.DIGIT_0.ordinal)
        } else {
            KeyEvent.KEYCODE_A + (key.ordinal - Key.A.ordinal)
        }
    }

    fun metaState(modifiers: Set<KeyModifier>): Int = modifiers.fold(0) { meta, modifier ->
        meta or when (modifier) {
            KeyModifier.CTRL -> KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
            KeyModifier.ALT -> KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            KeyModifier.SHIFT -> KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            KeyModifier.META -> KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        }
    }

    /** The editor action Enter should perform (Send, Search, Go…), or null to send a plain Enter key. */
    fun enterAction(imeOptions: Int): Int? {
        if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return null
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        return action.takeUnless { it == EditorInfo.IME_ACTION_NONE || it == EditorInfo.IME_ACTION_UNSPECIFIED }
    }
}
```

- [ ] **Step 5: Run the pure tests**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.saubh.deskbuddy.keyboard.*"`
Expected: PASS.

- [ ] **Step 6: IME resources** — strings (append to `strings.xml`):

```xml
    <string name="ime_name">DeskBuddy Keyboard</string>
    <string name="ime_typing_from">Typing from %1$s</string>
    <string name="ime_pc_off">PC keyboard is off</string>
    <string name="ime_switch">Switch keyboard</string>
```

`res/xml/deskbuddy_method.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<input-method xmlns:android="http://schemas.android.com/apk/res/android" />
```

`res/layout/ime_bar.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:background="?android:attr/colorBackground"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingStart="16dp"
    android:paddingEnd="8dp">

    <TextView
        android:id="@+id/ime_status"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:textAppearance="?android:attr/textAppearanceMedium" />

    <Button
        android:id="@+id/ime_switch"
        style="?android:attr/borderlessButtonStyle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/ime_switch" />
</LinearLayout>
```

Manifest, inside `<application>`:

```xml
        <!-- DeskBuddy Keyboard: types what the PC keyboard sends into the focused app. -->
        <service
            android:name=".ime.DeskBuddyInputMethodService"
            android:exported="true"
            android:label="@string/ime_name"
            android:permission="android.permission.BIND_INPUT_METHOD">
            <intent-filter>
                <action android:name="android.view.InputMethod" />
            </intent-filter>
            <meta-data
                android:name="android.view.im"
                android:resource="@xml/deskbuddy_method" />
        </service>
```

`AppGraph`: `val keyboardHub = RemoteKeyboardHub(session, scope)` (import `com.saubh.deskbuddy.keyboard.RemoteKeyboardHub`).

- [ ] **Step 7: The IME service** — `ime/DeskBuddyInputMethodService.kt`:

```kotlin
package com.saubh.deskbuddy.ime

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.appGraph
import com.saubh.deskbuddy.keyboard.AndroidKeyMapping
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyPressCommand
import com.saubh.deskbuddy.protocol.KeyTextCommand
import com.saubh.deskbuddy.ui.ConnectionUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * DeskBuddy Keyboard. It has no keys of its own: it applies what the PC keyboard sends to the
 * focused field, shows a slim status bar, and switches back to the previous keyboard when the PC
 * stops typing. Nothing is logged or stored.
 */
class DeskBuddyInputMethodService : InputMethodService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var inputJob: Job? = null
    private var status: TextView? = null

    override fun onCreate() {
        super.onCreate()
        val hub = appGraph.keyboardHub
        hub.setImeBound(true)
        scope.launch {
            var wasTyping = hub.pcTyping.value
            hub.pcTyping.collect { typing ->
                renderStatus()
                if (wasTyping && !typing) switchBack()
                wasTyping = typing
            }
        }
    }

    override fun onCreateInputView(): View = layoutInflater.inflate(R.layout.ime_bar, null).apply {
        status = findViewById(R.id.ime_status)
        findViewById<Button>(R.id.ime_switch).setOnClickListener { switchBack() }
        renderStatus()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        inputJob?.cancel()
        inputJob = scope.launch { appGraph.keyboardHub.input.collect(::apply) }
    }

    override fun onFinishInput() {
        inputJob?.cancel()
        inputJob = null
        super.onFinishInput()
    }

    override fun onDestroy() {
        appGraph.keyboardHub.setImeBound(false)
        scope.cancel()
        super.onDestroy()
    }

    private fun renderStatus() {
        val pc = (appGraph.session.uiState.value as? ConnectionUiState.Connected)?.desktopName
        status?.text = if (appGraph.keyboardHub.pcTyping.value && pc != null) getString(R.string.ime_typing_from, pc) else getString(R.string.ime_pc_off)
    }

    private fun apply(command: Command) {
        if (!appGraph.session.isConnected) return
        val connection = currentInputConnection ?: return
        when (command) {
            is KeyTextCommand -> connection.commitText(command.text, 1)
            is KeyPressCommand -> {
                val action = if (command.key == Key.ENTER && command.modifiers.isEmpty()) {
                    AndroidKeyMapping.enterAction(currentInputEditorInfo?.imeOptions ?: 0)
                } else {
                    null
                }
                if (action != null) connection.performEditorAction(action) else sendKey(connection, command)
            }
            else -> Unit
        }
    }

    private fun sendKey(connection: InputConnection, command: KeyPressCommand) {
        val code = AndroidKeyMapping.keyCode(command.key)
        val meta = AndroidKeyMapping.metaState(command.modifiers)
        val now = SystemClock.uptimeMillis()
        connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
        connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
    }

    private fun switchBack() {
        val switched = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToPreviousInputMethod()
        } else {
            @Suppress("DEPRECATION")
            val token = window?.window?.attributes?.token
            @Suppress("DEPRECATION")
            token != null && getSystemService(InputMethodManager::class.java).switchToLastInputMethod(token)
        }
        if (!switched) getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    }
}
```

- [ ] **Step 8: Verify**

Run: `./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:lintDebug`
Expected: PASS / BUILD SUCCESSFUL, lint 0 errors.
Manual: enable DeskBuddy Keyboard in Settings, open WhatsApp, pick DeskBuddy Keyboard, press Ctrl+Alt+K on the PC, type → text appears in WhatsApp; Enter sends; Esc on the PC → the phone switches back to Gboard.

- [ ] **Step 9: Commit**

```bash
git add androidApp/src
git commit -m "feat(keyboard): DeskBuddy Keyboard applies PC keystrokes to the focused app"
```

---

### Task 16: "PC keyboard ready" prompt and keyboard-picker trampoline

**Files:**
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ime/KeyboardPickerActivity.kt`
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/service/SessionNotifications.kt`
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/service/ConnectionService.kt` (`onCreate`)
- Modify: `androidApp/src/main/AndroidManifest.xml`
- Modify: `androidApp/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `RemoteKeyboardHub.pcTyping`, `.imeBound` (Task 15); `ImeStatus.isCurrent` (Task 14).
- Produces: `SessionNotifications.KEYBOARD_ID`, `keyboardPrompt()`, `post(notification, id)`, `cancel(id)`.

- [ ] **Step 1: Strings**

```xml
    <string name="notif_channel_keyboard">PC keyboard</string>
    <string name="notif_keyboard_title">PC keyboard ready</string>
    <string name="notif_keyboard_text">Tap to switch to DeskBuddy Keyboard.</string>
```

- [ ] **Step 2: Trampoline activity** — `ime/KeyboardPickerActivity.kt`:

```kotlin
package com.saubh.deskbuddy.ime

import android.app.Activity
import android.view.inputmethod.InputMethodManager

/**
 * Invisible: the keyboard picker needs a focused window, which a notification tap alone does not
 * give. Shows the picker once focused, and finishes when focus returns after the picker closes.
 */
class KeyboardPickerActivity : Activity() {
    private var pickerShown = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        if (pickerShown) {
            finish()
        } else {
            pickerShown = true
            getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        }
    }
}
```

Manifest, inside `<application>`:

```xml
        <activity
            android:name=".ime.KeyboardPickerActivity"
            android:excludeFromRecents="true"
            android:exported="false"
            android:taskAffinity=""
            android:theme="@android:style/Theme.Translucent.NoTitleBar" />
```

- [ ] **Step 3: Notification** — in `SessionNotifications`:

```kotlin
    companion object {
        const val ID = 1
        const val KEYBOARD_ID = 2
        private const val STATUS_CHANNEL = "connection"
        private const val MEDIA_CHANNEL = "now_playing"
        private const val KEYBOARD_CHANNEL = "pc_keyboard"
    }
```

Add to the channel list in `init`:

```kotlin
                NotificationChannelCompat.Builder(KEYBOARD_CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.notif_channel_keyboard))
                    .setShowBadge(false)
                    .build(),
```

New members (import `com.saubh.deskbuddy.ime.KeyboardPickerActivity`):

```kotlin
    fun keyboardPrompt(): Notification {
        val intent = Intent(context, KeyboardPickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(context, KEYBOARD_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(context, KEYBOARD_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_deskbuddy)
            .setContentTitle(context.getString(R.string.notif_keyboard_title))
            .setContentText(context.getString(R.string.notif_keyboard_text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    fun cancel(id: Int) = context.getSystemService(NotificationManager::class.java).cancel(id)
```

Change `post` to take an id: `fun post(notification: Notification, id: Int = ID)` and use `notify(id, notification)`.

- [ ] **Step 4: Observe in `ConnectionService.onCreate`** (after the media collector; import `com.saubh.deskbuddy.keyboard.ImeStatus`):

```kotlin
        scope.launch {
            // Android cannot switch keyboards for us: prompt when the PC starts typing and ours is not active.
            combine(graph.keyboardHub.pcTyping, graph.keyboardHub.imeBound) { typing, bound ->
                typing && !bound && !ImeStatus.isCurrent(this@ConnectionService)
            }
                .distinctUntilChanged()
                .collect { prompt ->
                    if (prompt) {
                        notifications.post(notifications.keyboardPrompt(), SessionNotifications.KEYBOARD_ID)
                    } else {
                        notifications.cancel(SessionNotifications.KEYBOARD_ID)
                    }
                }
        }
```

- [ ] **Step 5: Verify**

Run: `./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:lintDebug`
Expected: PASS / BUILD SUCCESSFUL, lint 0 errors.
Manual: with Gboard active, press Ctrl+Alt+K on the PC → "PC keyboard ready" heads-up notification; tap → the keyboard picker opens; choose DeskBuddy Keyboard → the notification is gone.

- [ ] **Step 6: Commit**

```bash
git add androidApp/src
git commit -m "feat(keyboard): prompt to switch to DeskBuddy Keyboard when the PC starts typing"
```

---

### Task 17: Full gate, changelog, device checks

**Files:**
- Modify: `2026_Saubhagya_changelog.md`

- [ ] **Step 1: Run the full gate**

Run: `./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug :desktopApp:build`
Expected: BUILD SUCCESSFUL, lint 0 errors. Fix anything that fails before continuing.

- [ ] **Step 2: Changelog** — insert directly under `# DeskBuddy Changelog` (newest first), listing every file changed in Tasks 1–16:

```markdown
## [2026-09-28] — Milestone 4: Performance pass + shared keyboard

**Type:** Feature
**Files Changed:**
- `docs/superpowers/specs/2026-09-28-deskbuddy-m4-keyboard-performance-design.md`, `docs/superpowers/plans/2026-09-28-deskbuddy-m4.md` — spec and plan
- `shared/.../server/PairedDeviceStore.kt`, `ShortcutStore.kt` — lists cached in memory (P1, P2)
- `shared/.../server/ControlSession.kt` — one token lookup per message; cached catalog; key_text / key_press dispatch, silent on success, UNSUPPORTED_OS once per connection, 4096-char cap
- `shared/.../server/AppCatalogService.kt` — `forPhone()`: cached reply + background rescan after 30 s (P2)
- `shared/.../server/ClipboardWatcher.kt`, `WindowsClipboardSequence.kt` — read clipboard only on sequence change (P4)
- `shared/.../server/FileSender.kt` — one IO context per send (P6)
- `shared/.../server/AudioActuator.kt`, `audio/WindowsAudioActuator.kt`, `media/MediaStateBroadcaster.kt` — `levels()` in one COM hop, cached endpoint (P5)
- `shared/.../protocol/Messages.kt` — `Key`, `KeyModifier`, `KeyTextCommand`, `KeyPressCommand`, `KeyboardModeCommand`
- `shared/.../server/keyboard/*` — `KeyboardActuator`, `SendInputPlan`, `WindowsKeyboardActuator`, `XdotoolArgs`, `XdotoolKeyboardActuator`, `RawKey`, `AwtKeyTranslator`, `TypingState`, `PhoneTypingSession`, `WindowsGlobalHotkey`
- `shared/.../server/Actuators.kt`, `ActuatorFactory.kt` — keyboard actuator
- `shared/.../ui/theme/DeskBuddyIcons.kt` — `Keyboard`
- `desktopApp/.../DesktopController.kt`, `main.kt` — IO for slow work, fast quit, typing session + hotkey + tray item (P3)
- `desktopApp/.../ui/KeyboardOverlay.kt`, `ui/StatusPanel.kt`, `ui/ServerScreen.kt` — overlay, keyboard chip
- `desktopApp/build.gradle.kts` — `-Xmx256m -XX:+UseSerialGC`, runtime modules (P7); `gradle.properties` — parallel (P8)
- `androidApp/.../media/MediaUiState.kt`, `MediaController.kt`, `ui/media/PlaybackControls.kt` — position interpolated in the timeline, no 500 ms tick (P9)
- `androidApp/.../service/PcMediaSession.kt` — skip unchanged metadata (P10); `share/FileTransferController.kt` — per-percent progress (P11)
- `androidApp/.../session/AutoConnector.kt`, `AutoConnectBackoff.kt`, `AutoConnectPlan.kt`, `RemoteSession.kt`, `ui/ConnectScreen.kt`, `service/AutoStandbyManager.kt`, `AppGraph.kt` — LAN-only probing with backoff, no mDNS while connected, lifecycle-aware sweep, sensor gated on connection (P12–P14)
- `androidApp/build.gradle.kts`, `proguard-rules.pro` — R8 + resource shrinking (P15)
- `androidApp/.../keyboard/*` — `TextDiff`, `ModifierState`, `KeyboardController`, `ImeStatus`, `RemoteKeyboardHub`, `AndroidKeyMapping`
- `androidApp/.../ui/keyboard/*`, `ui/RemoteScreen.kt`, `ui/ConnectionViewModel.kt` — Keyboard tab
- `androidApp/.../ime/DeskBuddyInputMethodService.kt`, `ime/KeyboardPickerActivity.kt`, `res/layout/ime_bar.xml`, `res/xml/deskbuddy_method.xml`, `AndroidManifest.xml` — DeskBuddy Keyboard
- `androidApp/.../service/SessionNotifications.kt`, `ConnectionService.kt` — "PC keyboard ready" prompt
- `androidApp/src/main/res/values/strings.xml` — keyboard, IME and notification copy
- Tests (all new): commonTest `WireCodecTest`; jvmTest `PairedDeviceStoreTest`, `ControlSessionTest`, `AppCatalogServiceTest`, `ClipboardWatcherTest`, `FileSenderTest`, `MediaStateBroadcasterTest`, `WindowsAudioActuatorTest`, `SendInputPlanTest`, `XdotoolArgsTest`, `AwtKeyTranslatorTest`, `PhoneTypingSessionTest`; androidApp `MediaControllerTest`, `AutoConnectBackoffTest`, `AutoConnectPlanTest`, `TextDiffTest`, `ModifierStateTest`, `KeyboardControllerTest`, `RemoteKeyboardHubTest`, `AndroidKeyMappingTest`

**Summary:**
Removes the per-message disk reads, per-request PowerShell scans, UI-thread blocking, whole-screen media recomposition and always-on network probing found in the review, and turns on R8 for release. Adds keyboard sharing both ways: the phone's Keyboard tab types live into the PC's focused window with special keys and sticky modifiers; Ctrl+Alt+K on the PC opens a focus overlay whose keystrokes DeskBuddy Keyboard (an IME) types into the phone's focused app. The repository had no committed tests before this milestone (the M1–M3 tests listed above were never added to git); all tests here are new. Review Tiers 1–2 (pairing brute force, frame-size limit, crash fixes, transfer cleanup) remain open.

**Build Verified:** [x] Yes — `./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug :desktopApp:build` passed
```

(If Step 1 did not pass, write `**Build Verified:** [ ] No — <error summary>` instead.)

- [ ] **Step 3: Commit**

```bash
git add 2026_Saubhagya_changelog.md
git commit -m "docs: M4 changelog"
```

- [ ] **Step 4: Hand the device checks to the user** — list these in the final report (they can't run in CI):
  1. Install the **release** APK (R8) and check pairing, media, share, apps and keyboard.
  2. Phone → PC typing in Notepad, Chrome and Word, including an autocorrected word and an emoji.
  3. PC → phone typing in WhatsApp, the Chrome address bar and Gmail; Enter sends in WhatsApp.
  4. A non-English layout on the PC, and an AltGr layout if available (`@` on German).
  5. Leave the phone on cellular for an hour with the PC off: no noticeable battery drain from DeskBuddy.
