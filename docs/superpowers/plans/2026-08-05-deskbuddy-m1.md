# DeskBuddy Milestone 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android phone discovers a Windows desktop over WiFi, pairs via 6-digit PIN, and controls media playback/volume end-to-end.

**Architecture:** Shared KMP protocol + pairing logic in `shared/commonMain` (web-compatible), Ktor WebSocket client + NSD discovery in `shared/androidMain`, Ktor WebSocket server + JNA actuators + JmDNS in `shared/jvmMain`. Android UI in `androidApp`, desktop UI in `desktopApp`.

**Tech Stack:** Kotlin 2.4.10, Compose Multiplatform 1.11.1, kotlinx.serialization, Ktor 3.x (WebSockets, CIO), JNA 5.17, JmDNS 3.6, Preferences DataStore.

**Spec:** `docs/superpowers/specs/2026-08-05-deskbuddy-m1-design.md`

## Global Constraints

- **NO git commits** — user directive; never run `git add`/`git commit` during this plan.
- Package root: `com.saubh.deskbuddy`. Port **8765**, WebSocket path **/control** (constants in `Protocol` object, Task 1).
- `shared/commonMain` must compile for js/wasmJs targets — no JVM/Android APIs there.
- Files under 400 lines; one class per file where practical.
- No hardcoded user-visible strings in `androidApp` Kotlin — use `strings.xml`. (Desktop UI strings may be inline; no resource system wired there.)
- Test naming: `` `methodName_givenCondition_expectedBehavior` `` style backtick names.
- Build commands run from repo root `C:\Saubhagya\PROJECT\DeskBuddy\DeskBuddy` with `./gradlew` (Bash) or `.\gradlew.bat` (PowerShell).
- If a pinned dependency version fails to resolve, check Maven Central for the closest newer patch version and use it — do not downgrade Kotlin/AGP.

---

### Task 1: Protocol messages + WireCodec (commonMain)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/build.gradle.kts`
- Create: `shared/src/commonMain/kotlin/com/saubh/deskbuddy/protocol/Protocol.kt`
- Create: `shared/src/commonMain/kotlin/com/saubh/deskbuddy/protocol/Messages.kt`
- Create: `shared/src/commonMain/kotlin/com/saubh/deskbuddy/protocol/WireCodec.kt`
- Test: `shared/src/commonTest/kotlin/com/saubh/deskbuddy/protocol/WireCodecTest.kt`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: `Message` sealed class + subtypes `PairRequest(deviceName: String)`, `PairAttempt(pin: String)`, `Envelope(token: String, command: Command)`, `PairSuccess(token: String)`, `PairFailure(attemptsLeft: Int)`, `Ack(ok: Boolean, error: ErrorCode? = null)`; `Command` sealed class + `MediaCommand(action: MediaAction)`; enums `MediaAction { PLAY_PAUSE, NEXT, PREVIOUS, VOLUME_UP, VOLUME_DOWN, MUTE_TOGGLE }`, `ErrorCode { AUTH_REQUIRED, BUSY, UNSUPPORTED_OS, INTERNAL }`; `WireCodec.encode(Message): String`, `WireCodec.decode(String): Message`; `Protocol.PORT = 8765`, `Protocol.PATH = "/control"`, `Protocol.SERVICE_TYPE = "_deskbuddy._tcp."`.

- [ ] **Step 1: Add ALL version-catalog entries for the whole milestone** (one-time toml edit; later tasks only touch module build files)

Append to `[versions]` in `gradle/libs.versions.toml`:

```toml
kotlinx-serialization = "1.9.0"
ktor = "3.3.1"
jmdns = "3.6.1"
jna = "5.17.0"
datastore = "1.1.7"
```

Append to `[libraries]`:

```toml
kotlinx-serializationJson = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
kotlinx-coroutinesCore = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
ktor-clientCore = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-clientCio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-clientWebsockets = { module = "io.ktor:ktor-client-websockets", version.ref = "ktor" }
ktor-serverCore = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
ktor-serverCio = { module = "io.ktor:ktor-server-cio", version.ref = "ktor" }
ktor-serverWebsockets = { module = "io.ktor:ktor-server-websockets", version.ref = "ktor" }
jmdns = { module = "org.jmdns:jmdns", version.ref = "jmdns" }
jna = { module = "net.java.dev.jna:jna", version.ref = "jna" }
jna-platform = { module = "net.java.dev.jna:jna-platform", version.ref = "jna" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
```

Append to `[plugins]`:

```toml
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Apply serialization plugin + commonMain dependency**

In `shared/build.gradle.kts` add to the `plugins` block:

```kotlin
alias(libs.plugins.kotlinSerialization)
```

In `sourceSets`, add to `commonMain.dependencies`:

```kotlin
implementation(libs.kotlinx.serializationJson)
implementation(libs.kotlinx.coroutinesCore)
```

- [ ] **Step 3: Write the failing test**

`shared/src/commonTest/kotlin/com/saubh/deskbuddy/protocol/WireCodecTest.kt`:

```kotlin
package com.saubh.deskbuddy.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WireCodecTest {

    @Test
    fun `encode decode givenEnvelopeWithMediaCommand roundTrips`() {
        val original = Envelope(token = "abc123", command = MediaCommand(MediaAction.PLAY_PAUSE))
        val decoded = WireCodec.decode(WireCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `encode decode givenEveryMessageType roundTrips`() {
        val messages: List<Message> = listOf(
            PairRequest(deviceName = "Pixel 8"),
            PairAttempt(pin = "123456"),
            Envelope(token = "t", command = MediaCommand(MediaAction.VOLUME_UP)),
            PairSuccess(token = "deadbeef"),
            PairFailure(attemptsLeft = 2),
            Ack(ok = true),
            Ack(ok = false, error = ErrorCode.AUTH_REQUIRED),
        )
        for (message in messages) {
            assertEquals(message, WireCodec.decode(WireCodec.encode(message)))
        }
    }

    @Test
    fun `decode givenUnknownExtraField ignoresIt`() {
        val decoded = WireCodec.decode("""{"type":"ack","ok":true,"future_field":1}""")
        assertEquals(Ack(ok = true), decoded)
    }

    @Test
    fun `encode givenPairRequest usesTypeDiscriminator`() {
        assertTrue(WireCodec.encode(PairRequest("Pixel")).contains(""""type":"pair_request""""))
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.protocol.WireCodecTest"`
Expected: FAIL — unresolved references (`Envelope`, `WireCodec`, …).

- [ ] **Step 5: Write the implementation**

`shared/src/commonMain/kotlin/com/saubh/deskbuddy/protocol/Protocol.kt`:

```kotlin
package com.saubh.deskbuddy.protocol

object Protocol {
    const val PORT = 8765
    const val PATH = "/control"

    /** mDNS service type. Android NSD wants no trailing "local." — JmDNS adds "local." itself. */
    const val SERVICE_TYPE = "_deskbuddy._tcp."
    const val PAIRING_TIMEOUT_MILLIS = 60_000L
    const val MAX_PIN_ATTEMPTS = 3
}
```

`shared/src/commonMain/kotlin/com/saubh/deskbuddy/protocol/Messages.kt`:

```kotlin
package com.saubh.deskbuddy.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Message

@Serializable
@SerialName("pair_request")
data class PairRequest(val deviceName: String) : Message()

@Serializable
@SerialName("pair_attempt")
data class PairAttempt(val pin: String) : Message()

@Serializable
@SerialName("envelope")
data class Envelope(val token: String, val command: Command) : Message()

@Serializable
@SerialName("pair_success")
data class PairSuccess(val token: String) : Message()

@Serializable
@SerialName("pair_failure")
data class PairFailure(val attemptsLeft: Int) : Message()

@Serializable
@SerialName("ack")
data class Ack(val ok: Boolean, val error: ErrorCode? = null) : Message()

@Serializable
sealed class Command

@Serializable
@SerialName("media")
data class MediaCommand(val action: MediaAction) : Command()

@Serializable
enum class MediaAction { PLAY_PAUSE, NEXT, PREVIOUS, VOLUME_UP, VOLUME_DOWN, MUTE_TOGGLE }

@Serializable
enum class ErrorCode { AUTH_REQUIRED, BUSY, UNSUPPORTED_OS, INTERNAL }
```

`shared/src/commonMain/kotlin/com/saubh/deskbuddy/protocol/WireCodec.kt`:

```kotlin
package com.saubh.deskbuddy.protocol

import kotlinx.serialization.json.Json

object WireCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    fun encode(message: Message): String = json.encodeToString(Message.serializer(), message)

    fun decode(text: String): Message = json.decodeFromString(Message.serializer(), text)
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.protocol.WireCodecTest"`
Expected: PASS (4 tests).

---

### Task 2: PinGenerator + PairingSession (commonMain)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/saubh/deskbuddy/pairing/PinGenerator.kt`
- Create: `shared/src/commonMain/kotlin/com/saubh/deskbuddy/pairing/PairingSession.kt`
- Test: `shared/src/commonTest/kotlin/com/saubh/deskbuddy/pairing/PairingSessionTest.kt`
- Test: `shared/src/commonTest/kotlin/com/saubh/deskbuddy/pairing/PinGeneratorTest.kt`

**Interfaces:**
- Consumes: `Protocol.PAIRING_TIMEOUT_MILLIS`, `Protocol.MAX_PIN_ATTEMPTS` (Task 1).
- Produces: `PinGenerator.generate(random: Random = Random.Default): String` (6 digits); `PairingSession(pin: String, createdAtMillis: Long, timeoutMillis: Long = Protocol.PAIRING_TIMEOUT_MILLIS, attempts: Int = Protocol.MAX_PIN_ATTEMPTS)` with `val pin`, `var attemptsLeft: Int` (private set), `fun isExpired(nowMillis: Long): Boolean`, `fun attempt(enteredPin: String, nowMillis: Long): PairingResult`; `PairingResult` sealed class: `Success`, `WrongPin(attemptsLeft: Int)`, `LockedOut`, `Expired`.

- [ ] **Step 1: Write the failing tests**

`shared/src/commonTest/kotlin/com/saubh/deskbuddy/pairing/PinGeneratorTest.kt`:

```kotlin
package com.saubh.deskbuddy.pairing

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PinGeneratorTest {

    @Test
    fun `generate always returnsSixDigits`() {
        repeat(50) {
            val pin = PinGenerator.generate()
            assertEquals(6, pin.length)
            assertTrue(pin.all { it.isDigit() })
        }
    }

    @Test
    fun `generate givenSeededRandom isDeterministic`() {
        assertEquals(PinGenerator.generate(Random(42)), PinGenerator.generate(Random(42)))
    }
}
```

`shared/src/commonTest/kotlin/com/saubh/deskbuddy/pairing/PairingSessionTest.kt`:

```kotlin
package com.saubh.deskbuddy.pairing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PairingSessionTest {

    private fun session() = PairingSession(pin = "123456", createdAtMillis = 0L)

    @Test
    fun `attempt givenCorrectPin returnsSuccess`() {
        assertIs<PairingResult.Success>(session().attempt("123456", nowMillis = 1_000L))
    }

    @Test
    fun `attempt givenWrongPin decrementsAttempts`() {
        val s = session()
        val result = s.attempt("000000", nowMillis = 1_000L)
        assertIs<PairingResult.WrongPin>(result)
        assertEquals(2, result.attemptsLeft)
        assertEquals(2, s.attemptsLeft)
    }

    @Test
    fun `attempt givenThreeWrongPins locksOut`() {
        val s = session()
        s.attempt("000000", 1_000L)
        s.attempt("111111", 2_000L)
        assertIs<PairingResult.LockedOut>(s.attempt("222222", 3_000L))
    }

    @Test
    fun `attempt afterLockout staysLockedEvenWithCorrectPin`() {
        val s = session()
        repeat(3) { s.attempt("000000", 1_000L) }
        assertIs<PairingResult.LockedOut>(s.attempt("123456", 4_000L))
    }

    @Test
    fun `attempt givenExpiredSession returnsExpired`() {
        assertIs<PairingResult.Expired>(session().attempt("123456", nowMillis = 60_000L))
    }

    @Test
    fun `isExpired justBeforeTimeout returnsFalse`() {
        assertEquals(false, session().isExpired(nowMillis = 59_999L))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.pairing.*"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Write the implementation**

`shared/src/commonMain/kotlin/com/saubh/deskbuddy/pairing/PinGenerator.kt`:

```kotlin
package com.saubh.deskbuddy.pairing

import kotlin.random.Random

object PinGenerator {
    fun generate(random: Random = Random.Default): String =
        buildString { repeat(6) { append(random.nextInt(10)) } }
}
```

`shared/src/commonMain/kotlin/com/saubh/deskbuddy/pairing/PairingSession.kt`:

```kotlin
package com.saubh.deskbuddy.pairing

import com.saubh.deskbuddy.protocol.Protocol

class PairingSession(
    val pin: String,
    private val createdAtMillis: Long,
    private val timeoutMillis: Long = Protocol.PAIRING_TIMEOUT_MILLIS,
    attempts: Int = Protocol.MAX_PIN_ATTEMPTS,
) {
    var attemptsLeft: Int = attempts
        private set

    fun isExpired(nowMillis: Long): Boolean = nowMillis - createdAtMillis >= timeoutMillis

    fun attempt(enteredPin: String, nowMillis: Long): PairingResult {
        if (isExpired(nowMillis)) return PairingResult.Expired
        if (attemptsLeft <= 0) return PairingResult.LockedOut
        return if (enteredPin == pin) {
            PairingResult.Success
        } else {
            attemptsLeft -= 1
            if (attemptsLeft == 0) PairingResult.LockedOut else PairingResult.WrongPin(attemptsLeft)
        }
    }
}

sealed class PairingResult {
    data object Success : PairingResult()
    data class WrongPin(val attemptsLeft: Int) : PairingResult()
    data object LockedOut : PairingResult()
    data object Expired : PairingResult()
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.pairing.*"`
Expected: PASS (8 tests).

---

### Task 3: TokenGenerator + PairedDeviceStore (jvmMain)

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/TokenGenerator.kt`
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/PairedDeviceStore.kt`
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/PairedDeviceStoreTest.kt`

**Interfaces:**
- Consumes: nothing new (kotlinx.serialization from Task 1's commonMain deps — inherited by jvmMain).
- Produces: `TokenGenerator.generate(): String` (64 hex chars); `PairedDevice(deviceName: String, token: String)` (@Serializable); `PairedDeviceStore(file: Path = <user.home>/.deskbuddy/paired.json)` with `fun load(): List<PairedDevice>`, `fun add(device: PairedDevice)`, `fun isValidToken(token: String): Boolean`, `fun deviceNameForToken(token: String): String?`, `fun removeAll()`.

- [ ] **Step 1: Write the failing test**

`shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/PairedDeviceStoreTest.kt`:

```kotlin
package com.saubh.deskbuddy.server

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairedDeviceStoreTest {

    private fun tempStore(): PairedDeviceStore {
        val dir = Files.createTempDirectory("deskbuddy-test")
        return PairedDeviceStore(file = dir.resolve("paired.json"))
    }

    @Test
    fun `load givenNoFile returnsEmptyList`() {
        assertEquals(emptyList(), tempStore().load())
    }

    @Test
    fun `add thenLoad returnsDevice`() {
        val store = tempStore()
        store.add(PairedDevice(deviceName = "Pixel 8", token = "tok1"))
        assertEquals(listOf(PairedDevice("Pixel 8", "tok1")), store.load())
    }

    @Test
    fun `isValidToken givenStoredToken returnsTrue`() {
        val store = tempStore()
        store.add(PairedDevice("Pixel 8", "tok1"))
        assertTrue(store.isValidToken("tok1"))
        assertFalse(store.isValidToken("other"))
    }

    @Test
    fun `deviceNameForToken givenStoredToken returnsName`() {
        val store = tempStore()
        store.add(PairedDevice("Pixel 8", "tok1"))
        assertEquals("Pixel 8", store.deviceNameForToken("tok1"))
        assertNull(store.deviceNameForToken("nope"))
    }

    @Test
    fun `removeAll thenLoad returnsEmptyList`() {
        val store = tempStore()
        store.add(PairedDevice("Pixel 8", "tok1"))
        store.removeAll()
        assertEquals(emptyList(), store.load())
    }

    @Test
    fun `load givenCorruptFile returnsEmptyList`() {
        val dir = Files.createTempDirectory("deskbuddy-test")
        val file = dir.resolve("paired.json")
        Files.writeString(file, "{ not json ]")
        assertEquals(emptyList(), PairedDeviceStore(file = file).load())
    }

    @Test
    fun `tokenGenerator generate returns64HexChars`() {
        val token = TokenGenerator.generate()
        assertEquals(64, token.length)
        assertTrue(token.all { it in "0123456789abcdef" })
        assertFalse(token == TokenGenerator.generate())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.PairedDeviceStoreTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Write the implementation**

`shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/TokenGenerator.kt`:

```kotlin
package com.saubh.deskbuddy.server

import java.security.SecureRandom

object TokenGenerator {
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
```

`shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/PairedDeviceStore.kt`:

```kotlin
package com.saubh.deskbuddy.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class PairedDevice(val deviceName: String, val token: String)

class PairedDeviceStore(
    private val file: Path = Path.of(System.getProperty("user.home"), ".deskbuddy", "paired.json"),
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PairedDevice.serializer())

    fun load(): List<PairedDevice> =
        if (Files.exists(file)) {
            runCatching { json.decodeFromString(serializer, Files.readString(file)) }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }

    fun add(device: PairedDevice) = save(load().filter { it.token != device.token } + device)

    fun isValidToken(token: String): Boolean = load().any { it.token == token }

    fun deviceNameForToken(token: String): String? =
        load().firstOrNull { it.token == token }?.deviceName

    fun removeAll() {
        Files.deleteIfExists(file)
    }

    private fun save(devices: List<PairedDevice>) {
        Files.createDirectories(file.parent)
        Files.writeString(file, json.encodeToString(serializer, devices))
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.PairedDeviceStoreTest"`
Expected: PASS (7 tests).

---

### Task 4: MediaActuator + ActuatorFactory + WindowsMediaActuator (jvmMain)

**Files:**
- Modify: `shared/build.gradle.kts` (jvmMain deps: JNA)
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/MediaActuator.kt`
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/WindowsMediaActuator.kt`
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ActuatorFactory.kt`
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/ActuatorFactoryTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `interface MediaActuator { fun playPause(); fun next(); fun previous(); fun volumeUp(); fun volumeDown(); fun muteToggle() }`; `class UnsupportedOsException(osName: String) : Exception`; `class UnsupportedMediaActuator(osName: String) : MediaActuator` (every method throws `UnsupportedOsException`); `ActuatorFactory.create(osName: String = System.getProperty("os.name") ?: "unknown"): MediaActuator`.

- [ ] **Step 1: Add JNA dependencies**

In `shared/build.gradle.kts` `sourceSets`, add a `jvmMain.dependencies` block (create it if absent):

```kotlin
jvmMain.dependencies {
    implementation(libs.jna)
    implementation(libs.jna.platform)
}
```

- [ ] **Step 2: Write the failing test**

`shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/ActuatorFactoryTest.kt`:

```kotlin
package com.saubh.deskbuddy.server

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ActuatorFactoryTest {

    @Test
    fun `create givenWindowsOsName returnsWindowsActuator`() {
        assertIs<WindowsMediaActuator>(ActuatorFactory.create("Windows 11"))
    }

    @Test
    fun `create givenLinuxOsName returnsUnsupportedActuator`() {
        assertIs<UnsupportedMediaActuator>(ActuatorFactory.create("Linux"))
    }

    @Test
    fun `unsupportedActuator playPause throwsUnsupportedOsException`() {
        assertFailsWith<UnsupportedOsException> {
            UnsupportedMediaActuator("Linux").playPause()
        }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.ActuatorFactoryTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 4: Write the implementation**

`shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/MediaActuator.kt`:

```kotlin
package com.saubh.deskbuddy.server

interface MediaActuator {
    fun playPause()
    fun next()
    fun previous()
    fun volumeUp()
    fun volumeDown()
    fun muteToggle()
}

class UnsupportedOsException(osName: String) : Exception("Unsupported OS: $osName")

class UnsupportedMediaActuator(private val osName: String) : MediaActuator {
    override fun playPause() = unsupported()
    override fun next() = unsupported()
    override fun previous() = unsupported()
    override fun volumeUp() = unsupported()
    override fun volumeDown() = unsupported()
    override fun muteToggle() = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOsException(osName)
}
```

`shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/WindowsMediaActuator.kt`:

```kotlin
package com.saubh.deskbuddy.server

import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser

/**
 * Sends media/volume virtual-key taps via Win32 SendInput. These keys are
 * handled system-wide, so they reach whichever app owns the media session.
 * java.awt.Robot cannot send them — AWT defines no VK codes for media keys.
 */
class WindowsMediaActuator : MediaActuator {

    private companion object {
        const val VK_MEDIA_PLAY_PAUSE = 0xB3
        const val VK_MEDIA_NEXT_TRACK = 0xB0
        const val VK_MEDIA_PREV_TRACK = 0xB1
        const val VK_VOLUME_UP = 0xAF
        const val VK_VOLUME_DOWN = 0xAE
        const val VK_VOLUME_MUTE = 0xAD
        const val KEYEVENTF_KEYUP = 0x0002
    }

    override fun playPause() = tap(VK_MEDIA_PLAY_PAUSE)
    override fun next() = tap(VK_MEDIA_NEXT_TRACK)
    override fun previous() = tap(VK_MEDIA_PREV_TRACK)
    override fun volumeUp() = tap(VK_VOLUME_UP)
    override fun volumeDown() = tap(VK_VOLUME_DOWN)
    override fun muteToggle() = tap(VK_VOLUME_MUTE)

    private fun tap(vk: Int) {
        sendKey(vk, flags = 0)
        sendKey(vk, flags = KEYEVENTF_KEYUP)
    }

    private fun sendKey(vk: Int, flags: Int) {
        val input = WinUser.INPUT()
        input.type = WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD.toLong())
        input.input.setType("ki")
        input.input.ki.wVk = WinDef.WORD(vk.toLong())
        input.input.ki.wScan = WinDef.WORD(0)
        input.input.ki.time = WinDef.DWORD(0)
        input.input.ki.dwFlags = WinDef.DWORD(flags.toLong())
        input.input.ki.dwExtraInfo = BaseTSD.ULONG_PTR(0)
        User32.INSTANCE.SendInput(WinDef.DWORD(1), arrayOf(input), input.size())
    }
}
```

`shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ActuatorFactory.kt`:

```kotlin
package com.saubh.deskbuddy.server

object ActuatorFactory {
    fun create(osName: String = System.getProperty("os.name") ?: "unknown"): MediaActuator =
        if (osName.startsWith("Windows", ignoreCase = true)) {
            WindowsMediaActuator()
        } else {
            UnsupportedMediaActuator(osName)
        }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.ActuatorFactoryTest"`
Expected: PASS (3 tests).

---

### Task 5: ControlSession message handler (jvmMain)

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ControlSession.kt`
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/ControlSessionTest.kt`

**Interfaces:**
- Consumes: `Message` subtypes + `Command`/`MediaAction`/`ErrorCode` (Task 1), `PairingSession`/`PairingResult`/`PinGenerator` (Task 2), `PairedDeviceStore`/`PairedDevice`/`TokenGenerator` (Task 3), `MediaActuator`/`UnsupportedOsException` (Task 4).
- Produces: `data class SessionReply(val message: Message?, val closeConnection: Boolean = false)`; `interface SessionListener { fun onPairingStarted(pin: String); fun onPairingEnded(); fun onAuthenticated(deviceName: String) }`; `class ControlSession(store, actuator, listener, now: () -> Long = { System.currentTimeMillis() }, newPin: () -> String = { PinGenerator.generate() }, newToken: () -> String = { TokenGenerator.generate() })` with `fun onMessage(message: Message): SessionReply`.

- [ ] **Step 1: Write the failing test**

`shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/ControlSessionTest.kt`:

```kotlin
package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.Ack
import com.saubh.deskbuddy.protocol.Envelope
import com.saubh.deskbuddy.protocol.ErrorCode
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.MediaCommand
import com.saubh.deskbuddy.protocol.PairAttempt
import com.saubh.deskbuddy.protocol.PairFailure
import com.saubh.deskbuddy.protocol.PairRequest
import com.saubh.deskbuddy.protocol.PairSuccess
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeActuator : MediaActuator {
    val calls = mutableListOf<String>()
    override fun playPause() { calls += "playPause" }
    override fun next() { calls += "next" }
    override fun previous() { calls += "previous" }
    override fun volumeUp() { calls += "volumeUp" }
    override fun volumeDown() { calls += "volumeDown" }
    override fun muteToggle() { calls += "muteToggle" }
}

private class FakeListener : SessionListener {
    var pin: String? = null
    var pairingEnded = false
    var authenticatedAs: String? = null
    override fun onPairingStarted(pin: String) { this.pin = pin }
    override fun onPairingEnded() { pairingEnded = true }
    override fun onAuthenticated(deviceName: String) { authenticatedAs = deviceName }
}

class ControlSessionTest {

    private val actuator = FakeActuator()
    private val listener = FakeListener()
    private val store = PairedDeviceStore(
        file = Files.createTempDirectory("deskbuddy-test").resolve("paired.json"),
    )

    private fun session(now: Long = 1_000L) = ControlSession(
        store = store,
        actuator = actuator,
        listener = listener,
        now = { now },
        newPin = { "123456" },
        newToken = { "tok-fixed" },
    )

    @Test
    fun `onMessage givenPairRequest startsPairingAndReportsPin`() {
        val reply = session().onMessage(PairRequest("Pixel 8"))
        assertEquals("123456", listener.pin)
        assertEquals(SessionReply(Ack(ok = true)), reply)
    }

    @Test
    fun `onMessage givenCorrectPin returnsPairSuccessAndPersistsDevice`() {
        val s = session()
        s.onMessage(PairRequest("Pixel 8"))
        val reply = s.onMessage(PairAttempt("123456"))
        assertEquals(SessionReply(PairSuccess("tok-fixed")), reply)
        assertTrue(store.isValidToken("tok-fixed"))
        assertEquals("Pixel 8", listener.authenticatedAs)
        assertTrue(listener.pairingEnded)
    }

    @Test
    fun `onMessage givenWrongPin returnsPairFailureWithAttemptsLeft`() {
        val s = session()
        s.onMessage(PairRequest("Pixel 8"))
        val reply = s.onMessage(PairAttempt("000000"))
        assertEquals(SessionReply(PairFailure(attemptsLeft = 2)), reply)
    }

    @Test
    fun `onMessage givenThirdWrongPin closesConnection`() {
        val s = session()
        s.onMessage(PairRequest("Pixel 8"))
        s.onMessage(PairAttempt("000000"))
        s.onMessage(PairAttempt("111111"))
        val reply = s.onMessage(PairAttempt("222222"))
        assertEquals(SessionReply(PairFailure(attemptsLeft = 0), closeConnection = true), reply)
    }

    @Test
    fun `onMessage givenEnvelopeWithUnknownToken returnsAuthRequired`() {
        val reply = session().onMessage(Envelope("bad-token", MediaCommand(MediaAction.PLAY_PAUSE)))
        assertEquals(SessionReply(Ack(ok = false, error = ErrorCode.AUTH_REQUIRED)), reply)
        assertEquals(emptyList(), actuator.calls)
    }

    @Test
    fun `onMessage givenEnvelopeWithValidToken executesCommand`() {
        store.add(PairedDevice("Pixel 8", "tok-live"))
        val reply = session().onMessage(Envelope("tok-live", MediaCommand(MediaAction.VOLUME_UP)))
        assertEquals(SessionReply(Ack(ok = true)), reply)
        assertEquals(listOf("volumeUp"), actuator.calls)
        assertEquals("Pixel 8", listener.authenticatedAs)
    }

    @Test
    fun `onMessage givenUnsupportedOsActuator returnsUnsupportedOsError`() {
        store.add(PairedDevice("Pixel 8", "tok-live"))
        val s = ControlSession(
            store = store,
            actuator = UnsupportedMediaActuator("Linux"),
            listener = listener,
        )
        val reply = s.onMessage(Envelope("tok-live", MediaCommand(MediaAction.PLAY_PAUSE)))
        assertEquals(SessionReply(Ack(ok = false, error = ErrorCode.UNSUPPORTED_OS)), reply)
    }

    @Test
    fun `onMessage givenPairAttemptWithoutPairRequest closesConnection`() {
        val reply = session().onMessage(PairAttempt("123456"))
        assertIs<Ack>(reply.message)
        assertTrue(reply.closeConnection)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.ControlSessionTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Write the implementation**

`shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ControlSession.kt`:

```kotlin
package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.pairing.PairingResult
import com.saubh.deskbuddy.pairing.PairingSession
import com.saubh.deskbuddy.pairing.PinGenerator
import com.saubh.deskbuddy.protocol.Ack
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Envelope
import com.saubh.deskbuddy.protocol.ErrorCode
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.MediaCommand
import com.saubh.deskbuddy.protocol.Message
import com.saubh.deskbuddy.protocol.PairAttempt
import com.saubh.deskbuddy.protocol.PairFailure
import com.saubh.deskbuddy.protocol.PairRequest
import com.saubh.deskbuddy.protocol.PairSuccess

data class SessionReply(val message: Message?, val closeConnection: Boolean = false)

interface SessionListener {
    fun onPairingStarted(pin: String)
    fun onPairingEnded()
    fun onAuthenticated(deviceName: String)
}

class ControlSession(
    private val store: PairedDeviceStore,
    private val actuator: MediaActuator,
    private val listener: SessionListener,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val newPin: () -> String = { PinGenerator.generate() },
    private val newToken: () -> String = { TokenGenerator.generate() },
) {
    private var pairing: PairingSession? = null
    private var pendingDeviceName: String = "Unknown device"

    fun onMessage(message: Message): SessionReply = when (message) {
        is PairRequest -> startPairing(message)
        is PairAttempt -> handleAttempt(message)
        is Envelope -> handleEnvelope(message)
        else -> SessionReply(Ack(ok = false, error = ErrorCode.INTERNAL))
    }

    private fun startPairing(request: PairRequest): SessionReply {
        pendingDeviceName = request.deviceName
        val session = PairingSession(pin = newPin(), createdAtMillis = now())
        pairing = session
        listener.onPairingStarted(session.pin)
        return SessionReply(Ack(ok = true))
    }

    private fun handleAttempt(attempt: PairAttempt): SessionReply {
        val session = pairing
            ?: return SessionReply(Ack(ok = false, error = ErrorCode.INTERNAL), closeConnection = true)
        return when (val result = session.attempt(attempt.pin, now())) {
            is PairingResult.Success -> {
                val token = newToken()
                store.add(PairedDevice(pendingDeviceName, token))
                endPairing()
                listener.onAuthenticated(pendingDeviceName)
                SessionReply(PairSuccess(token))
            }
            is PairingResult.WrongPin -> SessionReply(PairFailure(result.attemptsLeft))
            is PairingResult.LockedOut -> {
                endPairing()
                SessionReply(PairFailure(attemptsLeft = 0), closeConnection = true)
            }
            is PairingResult.Expired -> {
                endPairing()
                SessionReply(PairFailure(attemptsLeft = 0), closeConnection = true)
            }
        }
    }

    private fun endPairing() {
        pairing = null
        listener.onPairingEnded()
    }

    private fun handleEnvelope(envelope: Envelope): SessionReply {
        if (!store.isValidToken(envelope.token)) {
            return SessionReply(Ack(ok = false, error = ErrorCode.AUTH_REQUIRED))
        }
        listener.onAuthenticated(store.deviceNameForToken(envelope.token) ?: "Unknown device")
        return try {
            execute(envelope.command)
            SessionReply(Ack(ok = true))
        } catch (e: UnsupportedOsException) {
            SessionReply(Ack(ok = false, error = ErrorCode.UNSUPPORTED_OS))
        } catch (e: Exception) {
            SessionReply(Ack(ok = false, error = ErrorCode.INTERNAL))
        }
    }

    private fun execute(command: Command) {
        when (command) {
            is MediaCommand -> when (command.action) {
                MediaAction.PLAY_PAUSE -> actuator.playPause()
                MediaAction.NEXT -> actuator.next()
                MediaAction.PREVIOUS -> actuator.previous()
                MediaAction.VOLUME_UP -> actuator.volumeUp()
                MediaAction.VOLUME_DOWN -> actuator.volumeDown()
                MediaAction.MUTE_TOGGLE -> actuator.muteToggle()
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.ControlSessionTest"`
Expected: PASS (8 tests).

---

### Task 6: ControlServer (Ktor WebSocket) + ServerState + DeskBuddyAdvertiser (jvmMain)

**Files:**
- Modify: `shared/build.gradle.kts` (jvmMain: Ktor server + JmDNS; jvmTest: Ktor client for the integration test)
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ServerState.kt`
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ControlServer.kt`
- Create: `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/DeskBuddyAdvertiser.kt`
- Test: `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/ControlServerIntegrationTest.kt`

**Interfaces:**
- Consumes: `WireCodec`, `Protocol` (Task 1), `PairedDeviceStore` (Task 3), `MediaActuator` (Task 4), `ControlSession`/`SessionListener`/`SessionReply` (Task 5).
- Produces: `ServerState` sealed class: `Waiting`, `Pairing(pin: String)`, `Connected(deviceName: String)`; `class ControlServer(store: PairedDeviceStore, actuator: MediaActuator, port: Int = Protocol.PORT)` with `val state: StateFlow<ServerState>`, `fun start()`, `fun stop()`; `class DeskBuddyAdvertiser(port: Int = Protocol.PORT)` with `fun start()`, `fun stop()`.

- [ ] **Step 1: Add dependencies**

In `shared/build.gradle.kts`, extend `jvmMain.dependencies`:

```kotlin
implementation(libs.ktor.serverCore)
implementation(libs.ktor.serverCio)
implementation(libs.ktor.serverWebsockets)
implementation(libs.jmdns)
```

Add a `jvmTest.dependencies` block:

```kotlin
jvmTest.dependencies {
    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    implementation(libs.ktor.clientWebsockets)
}
```

- [ ] **Step 2: Write the failing integration test**

`shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/ControlServerIntegrationTest.kt`:

```kotlin
package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.Ack
import com.saubh.deskbuddy.protocol.Envelope
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.MediaCommand
import com.saubh.deskbuddy.protocol.Message
import com.saubh.deskbuddy.protocol.PairAttempt
import com.saubh.deskbuddy.protocol.PairRequest
import com.saubh.deskbuddy.protocol.PairSuccess
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.protocol.WireCodec
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class RecordingActuator : MediaActuator {
    val calls = mutableListOf<String>()
    override fun playPause() { calls += "playPause" }
    override fun next() { calls += "next" }
    override fun previous() { calls += "previous" }
    override fun volumeUp() { calls += "volumeUp" }
    override fun volumeDown() { calls += "volumeDown" }
    override fun muteToggle() { calls += "muteToggle" }
}

class ControlServerIntegrationTest {

    private val port = 18765
    private val actuator = RecordingActuator()
    private val store = PairedDeviceStore(
        file = Files.createTempDirectory("deskbuddy-test").resolve("paired.json"),
    )
    private lateinit var server: ControlServer
    private val http = HttpClient(CIO) { install(WebSockets) }

    @BeforeTest
    fun startServer() {
        server = ControlServer(store = store, actuator = actuator, port = port)
        server.start()
    }

    @AfterTest
    fun stopServer() {
        http.close()
        server.stop()
    }

    private suspend fun connect(): DefaultClientWebSocketSession =
        http.webSocketSession(host = "127.0.0.1", port = port, path = Protocol.PATH)

    private suspend fun DefaultClientWebSocketSession.sendMessage(message: Message) {
        send(Frame.Text(WireCodec.encode(message)))
    }

    private suspend fun DefaultClientWebSocketSession.receiveMessage(): Message {
        while (true) {
            val frame = incoming.receive()
            if (frame is Frame.Text) return WireCodec.decode(frame.readText())
        }
    }

    @Test
    fun `fullPairingFlow overRealSocket producesTokenAndExecutesCommand`() = runBlocking {
        withTimeout(15_000) {
            val ws = connect()
            ws.sendMessage(PairRequest("Test Phone"))
            assertEquals(Ack(ok = true), ws.receiveMessage())

            val pairingState = server.state.first { it is ServerState.Pairing } as ServerState.Pairing
            ws.sendMessage(PairAttempt(pairingState.pin))
            val success = ws.receiveMessage()
            assertIs<PairSuccess>(success)

            ws.sendMessage(Envelope(success.token, MediaCommand(MediaAction.PLAY_PAUSE)))
            assertEquals(Ack(ok = true), ws.receiveMessage())
            assertEquals(listOf("playPause"), actuator.calls)

            ws.close()
            server.state.first { it is ServerState.Waiting }
            assertTrue(store.isValidToken(success.token))
        }
    }

    @Test
    fun `secondConnection whileSessionActive isRejectedBusy`() = runBlocking {
        withTimeout(15_000) {
            val first = connect()
            first.sendMessage(PairRequest("Phone A"))
            assertEquals(Ack(ok = true), first.receiveMessage())

            val second = connect()
            val reply = second.receiveMessage()
            assertIs<Ack>(reply)
            assertEquals(false, reply.ok)

            first.close()
            second.close()
        }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.ControlServerIntegrationTest"`
Expected: FAIL — unresolved references (`ControlServer`, `ServerState`).

- [ ] **Step 4: Write the implementation**

`shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ServerState.kt`:

```kotlin
package com.saubh.deskbuddy.server

sealed class ServerState {
    data object Waiting : ServerState()
    data class Pairing(val pin: String) : ServerState()
    data class Connected(val deviceName: String) : ServerState()
}
```

`shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ControlServer.kt`:

```kotlin
package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.Ack
import com.saubh.deskbuddy.protocol.ErrorCode
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.protocol.WireCodec
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

class ControlServer(
    private val store: PairedDeviceStore,
    private val actuator: MediaActuator,
    private val port: Int = Protocol.PORT,
) {
    private val _state = MutableStateFlow<ServerState>(ServerState.Waiting)
    val state: StateFlow<ServerState> = _state

    private val sessionActive = AtomicBoolean(false)
    private var engine: EmbeddedServer<*, *>? = null

    private val listener = object : SessionListener {
        override fun onPairingStarted(pin: String) {
            _state.value = ServerState.Pairing(pin)
        }

        override fun onPairingEnded() {
            if (_state.value is ServerState.Pairing) _state.value = ServerState.Waiting
        }

        override fun onAuthenticated(deviceName: String) {
            _state.value = ServerState.Connected(deviceName)
        }
    }

    fun start() {
        engine = embeddedServer(CIO, port = port) {
            install(WebSockets) {
                pingPeriod = 15.seconds
                timeout = 30.seconds
            }
            routing {
                webSocket(Protocol.PATH) {
                    if (!sessionActive.compareAndSet(false, true)) {
                        send(Frame.Text(WireCodec.encode(Ack(ok = false, error = ErrorCode.BUSY))))
                        close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "busy"))
                        return@webSocket
                    }
                    val session = ControlSession(store, actuator, listener)
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val reply = runCatching { session.onMessage(WireCodec.decode(frame.readText())) }
                                .getOrElse { SessionReply(Ack(ok = false, error = ErrorCode.INTERNAL)) }
                            reply.message?.let { send(Frame.Text(WireCodec.encode(it))) }
                            if (reply.closeConnection) {
                                close(CloseReason(CloseReason.Codes.NORMAL, "closed"))
                            }
                        }
                    } finally {
                        sessionActive.set(false)
                        _state.value = ServerState.Waiting
                    }
                }
            }
        }.also { it.start(wait = false) }
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        engine = null
        _state.value = ServerState.Waiting
    }
}
```

`shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/DeskBuddyAdvertiser.kt`:

```kotlin
package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.Protocol
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/** Advertises the control server on the LAN via mDNS so Android NSD can find it. */
class DeskBuddyAdvertiser(private val port: Int = Protocol.PORT) {

    private var jmdns: JmDNS? = null

    fun start() {
        val address = InetAddress.getLocalHost()
        jmdns = JmDNS.create(address).apply {
            registerService(
                ServiceInfo.create(
                    Protocol.SERVICE_TYPE + "local.",
                    address.hostName.removeSuffix(".local"),
                    port,
                    "DeskBuddy control server",
                ),
            )
        }
    }

    fun stop() {
        jmdns?.unregisterAllServices()
        jmdns?.close()
        jmdns = null
    }
}
```

- [ ] **Step 5: Run the integration test**

Run: `./gradlew :shared:jvmTest --tests "com.saubh.deskbuddy.server.ControlServerIntegrationTest"`
Expected: PASS (2 tests). If the port is in use, change `port` in the test to another free port.

- [ ] **Step 6: Run the whole jvm test suite**

Run: `./gradlew :shared:jvmTest`
Expected: PASS — all tests from Tasks 1–6.

---

### Task 7: Desktop UI (desktopApp)

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/saubh/deskbuddy/main.kt`
- Create: `desktopApp/src/main/kotlin/com/saubh/deskbuddy/ui/ServerScreen.kt`

**Interfaces:**
- Consumes: `ControlServer` (`.state`, `.start()`, `.stop()`), `ServerState`, `DeskBuddyAdvertiser`, `PairedDeviceStore.removeAll()`, `ActuatorFactory.create()`, `Protocol.PORT`.
- Produces: runnable desktop app (`./gradlew :desktopApp:run`). No APIs consumed by later tasks.

No new dependencies: `desktopApp` already has `compose.desktop.currentOs` and depends on `:shared`.

- [ ] **Step 1: Write ServerScreen**

`desktopApp/src/main/kotlin/com/saubh/deskbuddy/ui/ServerScreen.kt`:

```kotlin
package com.saubh.deskbuddy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saubh.deskbuddy.server.ServerState
import kotlinx.coroutines.delay

@Composable
fun ServerScreen(
    state: ServerState,
    hostName: String,
    ipAddress: String,
    port: Int,
    onUnpairAll: () -> Unit,
) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (state) {
                    is ServerState.Waiting -> WaitingPanel(hostName, ipAddress, port)
                    is ServerState.Pairing -> PairingPanel(state.pin)
                    is ServerState.Connected -> ConnectedPanel(state.deviceName, onUnpairAll)
                }
            }
        }
    }
}

@Composable
private fun WaitingPanel(hostName: String, ip: String, port: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Waiting for connection…", style = MaterialTheme.typography.headlineSmall)
        Text("This PC: $hostName")
        Text("Address: $ip : $port")
        Text("Open DeskBuddy on your phone on the same WiFi network.")
    }
}

@Composable
private fun PairingPanel(pin: String) {
    var secondsLeft by remember(pin) { mutableStateOf(60) }
    LaunchedEffect(pin) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pairing request", style = MaterialTheme.typography.headlineSmall)
        Text(pin, fontSize = 64.sp, letterSpacing = 8.sp, fontWeight = FontWeight.Bold)
        Text("Enter this PIN on your phone. Expires in ${secondsLeft}s.")
    }
}

@Composable
private fun ConnectedPanel(deviceName: String, onUnpairAll: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Connected", style = MaterialTheme.typography.headlineSmall)
        Text(deviceName, style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = onUnpairAll) { Text("Unpair all devices") }
    }
}
```

- [ ] **Step 2: Rewrite main.kt**

Replace the full contents of `desktopApp/src/main/kotlin/com/saubh/deskbuddy/main.kt`:

```kotlin
package com.saubh.deskbuddy

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.server.ActuatorFactory
import com.saubh.deskbuddy.server.ControlServer
import com.saubh.deskbuddy.server.DeskBuddyAdvertiser
import com.saubh.deskbuddy.server.PairedDeviceStore
import com.saubh.deskbuddy.ui.ServerScreen
import java.net.DatagramSocket
import java.net.InetAddress

fun main() {
    val store = PairedDeviceStore()
    val server = ControlServer(store = store, actuator = ActuatorFactory.create())
    val advertiser = DeskBuddyAdvertiser()
    server.start()
    // mDNS failure must not kill the app — manual IP entry still works.
    runCatching { advertiser.start() }

    val hostName = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("Unknown")
    val ip = lanIpAddress()

    application {
        Window(
            onCloseRequest = {
                advertiser.stop()
                server.stop()
                exitApplication()
            },
            title = "DeskBuddy",
        ) {
            val state by server.state.collectAsState()
            ServerScreen(
                state = state,
                hostName = hostName,
                ipAddress = ip,
                port = Protocol.PORT,
                onUnpairAll = { store.removeAll() },
            )
        }
    }
}

/** Best-effort LAN IP: routing trick, no packets are actually sent. */
private fun lanIpAddress(): String = runCatching {
    DatagramSocket().use { socket ->
        socket.connect(InetAddress.getByName("8.8.8.8"), 80)
        socket.localAddress.hostAddress ?: "unknown"
    }
}.getOrDefault("unknown")
```

- [ ] **Step 3: Verify it compiles and runs**

Run: `./gradlew :desktopApp:build -x check`
Expected: BUILD SUCCESSFUL.

Then manual smoke test: `./gradlew :desktopApp:run` — window opens showing "Waiting for connection…" with hostname, IP, and port 8765. Close the window; it must exit cleanly. (If Windows Firewall prompts for Java/OpenJDK network access, allow it on private networks — required for the phone to connect.)

---

### Task 8: Android client + NSD discovery (shared/androidMain)

**Files:**
- Modify: `shared/build.gradle.kts` (androidMain: Ktor client)
- Create: `shared/src/androidMain/kotlin/com/saubh/deskbuddy/client/DeskBuddyClient.kt`
- Create: `shared/src/androidMain/kotlin/com/saubh/deskbuddy/client/DesktopDiscovery.kt`

**Interfaces:**
- Consumes: `Message`, `WireCodec`, `Protocol` (Task 1).
- Produces: `class DeskBuddyClient` with `suspend fun connect(host: String, port: Int = Protocol.PORT): Flow<Message>` (flow of incoming messages; completes when the socket closes), `suspend fun send(message: Message)`, `suspend fun disconnect()`; `data class DiscoveredDesktop(val name: String, val host: String, val port: Int)`; `class DesktopDiscovery(context: Context)` with `fun discover(): Flow<List<DiscoveredDesktop>>` (callbackFlow; stops discovery on cancel).

No automated test here — Android transport/NSD needs a device; covered by the jvm integration test (same protocol/server) and manual E2E in Task 10.

- [ ] **Step 1: Add Ktor client dependencies**

In `shared/build.gradle.kts`, extend `androidMain.dependencies`:

```kotlin
implementation(libs.ktor.clientCore)
implementation(libs.ktor.clientCio)
implementation(libs.ktor.clientWebsockets)
```

- [ ] **Step 2: Write DeskBuddyClient**

`shared/src/androidMain/kotlin/com/saubh/deskbuddy/client/DeskBuddyClient.kt`:

```kotlin
package com.saubh.deskbuddy.client

import com.saubh.deskbuddy.protocol.Message
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.protocol.WireCodec
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion

class DeskBuddyClient {

    private val http = HttpClient(CIO) { install(WebSockets) }
    private var session: DefaultClientWebSocketSession? = null

    /**
     * Opens the socket. Collect the returned flow to receive messages; it
     * completes (or throws) when the connection closes.
     */
    suspend fun connect(host: String, port: Int = Protocol.PORT): Flow<Message> {
        val ws = http.webSocketSession(host = host, port = port, path = Protocol.PATH)
        session = ws
        return flow {
            for (frame in ws.incoming) {
                if (frame is Frame.Text) emit(WireCodec.decode(frame.readText()))
            }
        }.onCompletion { session = null }
    }

    suspend fun send(message: Message) {
        session?.send(Frame.Text(WireCodec.encode(message)))
    }

    suspend fun disconnect() {
        session?.close(CloseReason(CloseReason.Codes.NORMAL, "bye"))
        session = null
    }
}
```

- [ ] **Step 3: Write DesktopDiscovery**

`shared/src/androidMain/kotlin/com/saubh/deskbuddy/client/DesktopDiscovery.kt`:

```kotlin
package com.saubh.deskbuddy.client

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.saubh.deskbuddy.protocol.Protocol
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class DiscoveredDesktop(val name: String, val host: String, val port: Int)

class DesktopDiscovery(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    /** Emits the current list of resolved desktops; updates as services appear/vanish. */
    fun discover(): Flow<List<DiscoveredDesktop>> = callbackFlow {
        val found = LinkedHashMap<String, DiscoveredDesktop>()

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                @Suppress("DEPRECATION") // registerServiceInfoCallback needs API 34; minSdk is 24
                nsdManager.resolveService(
                    service,
                    object : NsdManager.ResolveListener {
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val host = resolved.host?.hostAddress ?: return
                            found[resolved.serviceName] =
                                DiscoveredDesktop(resolved.serviceName, host, resolved.port)
                            trySend(found.values.toList())
                        }

                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    },
                )
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                found.remove(service.serviceName)
                trySend(found.values.toList())
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

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL (compiles shared androidMain transitively).

---

### Task 9: Android prefs + UiState + ConnectionViewModel (androidApp)

**Files:**
- Modify: `androidApp/build.gradle.kts` (compose, lifecycle, datastore deps)
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/prefs/PairedDesktopPrefs.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/ConnectionUiState.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/ConnectionViewModel.kt`

**Interfaces:**
- Consumes: `DeskBuddyClient`, `DesktopDiscovery`, `DiscoveredDesktop` (Task 8), protocol types (Task 1).
- Produces: `data class SavedDesktop(name: String, host: String, port: Int, token: String)`; `class PairedDesktopPrefs(context)` with `val saved: Flow<SavedDesktop?>`, `suspend fun save(SavedDesktop)`, `suspend fun clear()`; `ConnectionUiState` sealed interface: `Idle`, `Discovering(desktops: List<DiscoveredDesktop>)`, `Connecting(target: String)`, `Pairing(attemptsLeft: Int)`, `Connected(desktopName: String)`, `Reconnecting(attempt: Int)`; `enum class ErrorKind { CONNECTION_FAILED, CONNECTION_LOST, PAIRING_REJECTED, DESKTOP_BUSY, UNSUPPORTED_OS, INTERNAL }`; `ConnectionViewModel(app: Application)` with `uiState: StateFlow<ConnectionUiState>`, `errors: SharedFlow<ErrorKind>`, `savedDesktop: StateFlow<SavedDesktop?>`, and functions `startDiscovery()`, `connect(name, host, port, token: String?)`, `reconnectSaved()`, `submitPin(pin)`, `sendMedia(action: MediaAction)`, `disconnect()`.

- [ ] **Step 1: Add dependencies**

In `androidApp/build.gradle.kts` `dependencies`:

```kotlin
implementation(libs.compose.runtime)
implementation(libs.compose.foundation)
implementation(libs.compose.material3)
implementation(libs.compose.ui)
implementation(libs.androidx.lifecycle.viewmodelCompose)
implementation(libs.androidx.lifecycle.runtimeCompose)
implementation(libs.androidx.datastore.preferences)
```

- [ ] **Step 2: Write PairedDesktopPrefs**

`androidApp/src/main/kotlin/com/saubh/deskbuddy/prefs/PairedDesktopPrefs.kt`:

```kotlin
package com.saubh.deskbuddy.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.saubh.deskbuddy.protocol.Protocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "deskbuddy_prefs")

data class SavedDesktop(val name: String, val host: String, val port: Int, val token: String)

class PairedDesktopPrefs(private val context: Context) {

    private object Keys {
        val NAME = stringPreferencesKey("desktop_name")
        val HOST = stringPreferencesKey("desktop_host")
        val PORT = intPreferencesKey("desktop_port")
        val TOKEN = stringPreferencesKey("desktop_token")
    }

    val saved: Flow<SavedDesktop?> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.NAME] ?: return@map null
        val host = prefs[Keys.HOST] ?: return@map null
        val token = prefs[Keys.TOKEN] ?: return@map null
        SavedDesktop(name, host, prefs[Keys.PORT] ?: Protocol.PORT, token)
    }

    suspend fun save(desktop: SavedDesktop) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NAME] = desktop.name
            prefs[Keys.HOST] = desktop.host
            prefs[Keys.PORT] = desktop.port
            prefs[Keys.TOKEN] = desktop.token
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
```

- [ ] **Step 3: Write ConnectionUiState**

`androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/ConnectionUiState.kt`:

```kotlin
package com.saubh.deskbuddy.ui

import com.saubh.deskbuddy.client.DiscoveredDesktop

sealed interface ConnectionUiState {
    data object Idle : ConnectionUiState
    data class Discovering(val desktops: List<DiscoveredDesktop>) : ConnectionUiState
    data class Connecting(val target: String) : ConnectionUiState
    data class Pairing(val attemptsLeft: Int) : ConnectionUiState
    data class Connected(val desktopName: String) : ConnectionUiState
    data class Reconnecting(val attempt: Int) : ConnectionUiState
}

enum class ErrorKind {
    CONNECTION_FAILED,
    CONNECTION_LOST,
    PAIRING_REJECTED,
    DESKTOP_BUSY,
    UNSUPPORTED_OS,
    INTERNAL,
}
```

- [ ] **Step 4: Write ConnectionViewModel**

`androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/ConnectionViewModel.kt`:

```kotlin
package com.saubh.deskbuddy.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saubh.deskbuddy.client.DeskBuddyClient
import com.saubh.deskbuddy.client.DesktopDiscovery
import com.saubh.deskbuddy.prefs.PairedDesktopPrefs
import com.saubh.deskbuddy.prefs.SavedDesktop
import com.saubh.deskbuddy.protocol.Ack
import com.saubh.deskbuddy.protocol.Envelope
import com.saubh.deskbuddy.protocol.ErrorCode
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.MediaCommand
import com.saubh.deskbuddy.protocol.Message
import com.saubh.deskbuddy.protocol.PairAttempt
import com.saubh.deskbuddy.protocol.PairFailure
import com.saubh.deskbuddy.protocol.PairRequest
import com.saubh.deskbuddy.protocol.PairSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConnectionViewModel(app: Application) : AndroidViewModel(app) {

    private companion object {
        const val MAX_RECONNECT_ATTEMPTS = 3
        const val RECONNECT_BASE_DELAY_MS = 1_000L
    }

    private val prefs = PairedDesktopPrefs(app)
    private val discovery = DesktopDiscovery(app)
    private val client = DeskBuddyClient()

    private val _uiState = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Idle)
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private val _errors = MutableSharedFlow<ErrorKind>(extraBufferCapacity = 8)
    val errors: SharedFlow<ErrorKind> = _errors.asSharedFlow()

    val savedDesktop: StateFlow<SavedDesktop?> =
        prefs.saved.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var discoveryJob: Job? = null
    private var sessionJob: Job? = null
    private var current: SavedDesktop? = null
    private var endSession = false

    private enum class SessionOutcome { FAILED, DROPPED, ENDED }

    fun startDiscovery() {
        if (discoveryJob != null) return
        discoveryJob = viewModelScope.launch {
            discovery.discover()
                .catch { /* discovery failure is non-fatal; manual IP still works */ }
                .collect { desktops ->
                    val state = _uiState.value
                    if (state is ConnectionUiState.Idle || state is ConnectionUiState.Discovering) {
                        _uiState.value = ConnectionUiState.Discovering(desktops)
                    }
                }
        }
    }

    fun connect(name: String, host: String, port: Int, token: String?) {
        sessionJob?.cancel()
        endSession = false
        val target = SavedDesktop(name, host, port, token.orEmpty())
        current = target
        sessionJob = viewModelScope.launch { sessionLoop(target) }
    }

    fun reconnectSaved() {
        val saved = savedDesktop.value ?: return
        connect(saved.name, saved.host, saved.port, saved.token)
    }

    fun submitPin(pin: String) {
        viewModelScope.launch { client.send(PairAttempt(pin)) }
    }

    fun sendMedia(action: MediaAction) {
        val token = current?.token?.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch { client.send(Envelope(token, MediaCommand(action))) }
    }

    fun disconnect() {
        sessionJob?.cancel()
        sessionJob = null
        viewModelScope.launch { client.disconnect() }
        _uiState.value = ConnectionUiState.Idle
    }

    private suspend fun sessionLoop(target: SavedDesktop) {
        var attempt = 0
        while (attempt <= MAX_RECONNECT_ATTEMPTS) {
            if (attempt == 0) {
                _uiState.value = ConnectionUiState.Connecting(target.name)
            } else {
                _uiState.value = ConnectionUiState.Reconnecting(attempt)
                delay(RECONNECT_BASE_DELAY_MS shl (attempt - 1)) // 1s, 2s, 4s
            }
            when (connectOnce(target)) {
                SessionOutcome.FAILED -> {
                    if (attempt == 0) {
                        _errors.tryEmit(ErrorKind.CONNECTION_FAILED)
                        _uiState.value = ConnectionUiState.Idle
                        return
                    }
                    attempt++
                }
                SessionOutcome.DROPPED -> attempt++
                SessionOutcome.ENDED -> return
            }
        }
        _errors.tryEmit(ErrorKind.CONNECTION_LOST)
        _uiState.value = ConnectionUiState.Idle
    }

    private suspend fun connectOnce(target: SavedDesktop): SessionOutcome {
        val incoming = try {
            client.connect(target.host, target.port)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SessionOutcome.FAILED
        }
        try {
            onSocketOpened()
            incoming.collect { onMessage(it, target) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // fall through — treated as a drop
        }
        return if (endSession) SessionOutcome.ENDED else SessionOutcome.DROPPED
    }

    private suspend fun onSocketOpened() {
        val target = current ?: return
        if (target.token.isEmpty()) {
            _uiState.value = ConnectionUiState.Pairing(attemptsLeft = 3)
            client.send(PairRequest(deviceName = Build.MODEL))
        } else {
            _uiState.value = ConnectionUiState.Connected(target.name)
        }
    }

    private suspend fun onMessage(message: Message, target: SavedDesktop) {
        when (message) {
            is PairSuccess -> {
                val updated = target.copy(token = message.token)
                current = updated
                prefs.save(updated)
                _uiState.value = ConnectionUiState.Connected(updated.name)
            }
            is PairFailure -> {
                if (message.attemptsLeft <= 0) {
                    endSession = true
                    _errors.tryEmit(ErrorKind.PAIRING_REJECTED)
                    _uiState.value = ConnectionUiState.Idle
                } else {
                    _uiState.value = ConnectionUiState.Pairing(message.attemptsLeft)
                }
            }
            is Ack -> handleAck(message)
            else -> Unit
        }
    }

    private suspend fun handleAck(ack: Ack) {
        if (ack.ok) return
        when (ack.error) {
            ErrorCode.AUTH_REQUIRED -> {
                // Stored token no longer valid — re-pair on this same connection.
                current = current?.copy(token = "")
                prefs.clear()
                _uiState.value = ConnectionUiState.Pairing(attemptsLeft = 3)
                client.send(PairRequest(deviceName = Build.MODEL))
            }
            ErrorCode.BUSY -> {
                endSession = true
                _errors.tryEmit(ErrorKind.DESKTOP_BUSY)
                _uiState.value = ConnectionUiState.Idle
            }
            ErrorCode.UNSUPPORTED_OS -> _errors.tryEmit(ErrorKind.UNSUPPORTED_OS)
            else -> _errors.tryEmit(ErrorKind.INTERNAL)
        }
    }
}
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

---

### Task 10: Android screens + manifest + final verification

**Files:**
- Modify: `androidApp/src/main/AndroidManifest.xml`
- Modify: `androidApp/src/main/res/values/strings.xml`
- Modify: `androidApp/src/main/kotlin/com/saubh/deskbuddy/MainActivity.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/DeskBuddyApp.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/ConnectScreen.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/PinDialog.kt`
- Create: `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/RemoteScreen.kt`
- Modify: `2026_Saubhagya_changelog.md` (create at repo root)

**Interfaces:**
- Consumes: `ConnectionViewModel`, `ConnectionUiState`, `ErrorKind`, `SavedDesktop` (Task 9), `DiscoveredDesktop` (Task 8), `MediaAction`, `Protocol.PORT` (Task 1).
- Produces: installable app — final deliverable.

- [ ] **Step 1: Manifest — permissions + cleartext**

`androidApp/src/main/AndroidManifest.xml` — add before `<application>`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

Add attribute to the `<application>` tag (required: `ws://` is cleartext and Android blocks it by default on API 28+):

```xml
android:usesCleartextTraffic="true"
```

- [ ] **Step 2: strings.xml**

Replace `androidApp/src/main/res/values/strings.xml` content with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">DeskBuddy</string>

    <string name="connect_title">Connect to your PC</string>
    <string name="reconnect_to">Reconnect to %1$s</string>
    <string name="discovered_desktops">Desktops on your network</string>
    <string name="no_desktops_found">No desktops found yet. Make sure DeskBuddy is running on your PC.</string>
    <string name="manual_ip_hint">PC IP address (e.g. 192.168.1.20)</string>
    <string name="connect">Connect</string>
    <string name="connecting_to">Connecting to %1$s…</string>

    <string name="pin_title">Enter pairing PIN</string>
    <string name="pin_hint">6-digit PIN shown on your PC</string>
    <string name="attempts_left">Wrong PIN. %1$d attempts left.</string>
    <string name="submit">Submit</string>
    <string name="cancel">Cancel</string>

    <string name="remote_connected_to">Connected to %1$s</string>
    <string name="reconnecting">Reconnecting… (attempt %1$d)</string>
    <string name="disconnect">Disconnect</string>

    <string name="cd_play_pause">Play or pause</string>
    <string name="cd_next">Next track</string>
    <string name="cd_previous">Previous track</string>
    <string name="cd_volume_up">Volume up</string>
    <string name="cd_volume_down">Volume down</string>
    <string name="cd_mute">Mute</string>

    <string name="err_connection_failed">Could not connect. Check that DeskBuddy is running on your PC.</string>
    <string name="err_connection_lost">Connection lost.</string>
    <string name="err_pairing_rejected">Pairing rejected by the PC.</string>
    <string name="err_desktop_busy">Another device is already connected to this PC.</string>
    <string name="err_unsupported_os">This action is not supported on the PC\'s operating system.</string>
    <string name="err_internal">Something went wrong. Please try again.</string>
</resources>
```

- [ ] **Step 3: DeskBuddyApp root composable**

`androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/DeskBuddyApp.kt`:

```kotlin
package com.saubh.deskbuddy.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saubh.deskbuddy.R

@Composable
fun DeskBuddyApp(viewModel: ConnectionViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saved by viewModel.savedDesktop.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.errors.collect { error ->
            snackbarHostState.showSnackbar(context.getString(error.messageRes()))
        }
    }

    MaterialTheme {
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (val s = state) {
                    is ConnectionUiState.Connected,
                    is ConnectionUiState.Reconnecting,
                    ->
                        RemoteScreen(
                            state = s,
                            onMedia = viewModel::sendMedia,
                            onDisconnect = viewModel::disconnect,
                        )
                    else ->
                        ConnectScreen(
                            state = s,
                            saved = saved,
                            onReconnectSaved = viewModel::reconnectSaved,
                            onConnect = viewModel::connect,
                            onSubmitPin = viewModel::submitPin,
                            onCancelPairing = viewModel::disconnect,
                            onStartDiscovery = viewModel::startDiscovery,
                        )
                }
            }
        }
    }
}

fun ErrorKind.messageRes(): Int = when (this) {
    ErrorKind.CONNECTION_FAILED -> R.string.err_connection_failed
    ErrorKind.CONNECTION_LOST -> R.string.err_connection_lost
    ErrorKind.PAIRING_REJECTED -> R.string.err_pairing_rejected
    ErrorKind.DESKTOP_BUSY -> R.string.err_desktop_busy
    ErrorKind.UNSUPPORTED_OS -> R.string.err_unsupported_os
    ErrorKind.INTERNAL -> R.string.err_internal
}
```

- [ ] **Step 4: ConnectScreen + PinDialog**

`androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/ConnectScreen.kt`:

```kotlin
package com.saubh.deskbuddy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.client.DiscoveredDesktop
import com.saubh.deskbuddy.prefs.SavedDesktop
import com.saubh.deskbuddy.protocol.Protocol

@Composable
fun ConnectScreen(
    state: ConnectionUiState,
    saved: SavedDesktop?,
    onReconnectSaved: () -> Unit,
    onConnect: (name: String, host: String, port: Int, token: String?) -> Unit,
    onSubmitPin: (String) -> Unit,
    onCancelPairing: () -> Unit,
    onStartDiscovery: () -> Unit,
) {
    LaunchedEffect(Unit) { onStartDiscovery() }
    val desktops = (state as? ConnectionUiState.Discovering)?.desktops.orEmpty()
    var manualIp by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.headlineMedium)

        if (saved != null) {
            Card(onClick = onReconnectSaved, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.reconnect_to, saved.name),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(saved.host, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Text(stringResource(R.string.discovered_desktops), style = MaterialTheme.typography.titleMedium)
        if (desktops.isEmpty()) {
            Text(stringResource(R.string.no_desktops_found), style = MaterialTheme.typography.bodyMedium)
        } else {
            desktops.forEach { desktop ->
                Card(
                    onClick = {
                        val token = saved?.takeIf { it.host == desktop.host }?.token
                        onConnect(desktop.name, desktop.host, desktop.port, token)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(desktop.name, style = MaterialTheme.typography.titleMedium)
                        Text("${desktop.host}:${desktop.port}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        OutlinedTextField(
            value = manualIp,
            onValueChange = { manualIp = it },
            label = { Text(stringResource(R.string.manual_ip_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                val host = manualIp.trim()
                val token = saved?.takeIf { it.host == host }?.token
                onConnect(host, host, Protocol.PORT, token)
            },
            enabled = manualIp.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.connect))
        }

        if (state is ConnectionUiState.Connecting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(Modifier.size(20.dp))
                Text(stringResource(R.string.connecting_to, state.target))
            }
        }
    }

    if (state is ConnectionUiState.Pairing) {
        PinDialog(
            attemptsLeft = state.attemptsLeft,
            onSubmit = onSubmitPin,
            onDismiss = onCancelPairing,
        )
    }
}
```

`androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/PinDialog.kt`:

```kotlin
package com.saubh.deskbuddy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.protocol.Protocol

@Composable
fun PinDialog(
    attemptsLeft: Int,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { entered ->
                        if (entered.length <= 6 && entered.all(Char::isDigit)) pin = entered
                    },
                    label = { Text(stringResource(R.string.pin_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                if (attemptsLeft < Protocol.MAX_PIN_ATTEMPTS) {
                    Text(
                        stringResource(R.string.attempts_left, attemptsLeft),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(pin)
                    pin = ""
                },
                enabled = pin.length == 6,
            ) {
                Text(stringResource(R.string.submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
```

- [ ] **Step 5: RemoteScreen (portrait + landscape)**

`androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/RemoteScreen.kt`:

```kotlin
package com.saubh.deskbuddy.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.protocol.MediaAction

@Composable
fun RemoteScreen(
    state: ConnectionUiState,
    onMedia: (MediaAction) -> Unit,
    onDisconnect: () -> Unit,
) {
    val title = when (state) {
        is ConnectionUiState.Connected -> stringResource(R.string.remote_connected_to, state.desktopName)
        is ConnectionUiState.Reconnecting -> stringResource(R.string.reconnecting, state.attempt)
        else -> ""
    }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (isLandscape) {
        RemoteLandscape(title, onMedia, onDisconnect)
    } else {
        RemotePortrait(title, onMedia, onDisconnect)
    }
}

@Composable
private fun RemotePortrait(
    title: String,
    onMedia: (MediaAction) -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        TransportRow(onMedia)
        VolumeRow(onMedia)
        OutlinedButton(onClick = onDisconnect) { Text(stringResource(R.string.disconnect)) }
    }
}

@Composable
private fun RemoteLandscape(
    title: String,
    onMedia: (MediaAction) -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxSize().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportRow(onMedia)
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VolumeRow(onMedia)
                OutlinedButton(onClick = onDisconnect) { Text(stringResource(R.string.disconnect)) }
            }
        }
    }
}

@Composable
private fun TransportRow(onMedia: (MediaAction) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaButton("⏮", R.string.cd_previous, 64.dp) { onMedia(MediaAction.PREVIOUS) }
        MediaButton("⏯", R.string.cd_play_pause, 96.dp) { onMedia(MediaAction.PLAY_PAUSE) }
        MediaButton("⏭", R.string.cd_next, 64.dp) { onMedia(MediaAction.NEXT) }
    }
}

@Composable
private fun VolumeRow(onMedia: (MediaAction) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaButton("🔉", R.string.cd_volume_down, 64.dp) { onMedia(MediaAction.VOLUME_DOWN) }
        MediaButton("🔇", R.string.cd_mute, 64.dp) { onMedia(MediaAction.MUTE_TOGGLE) }
        MediaButton("🔊", R.string.cd_volume_up, 64.dp) { onMedia(MediaAction.VOLUME_UP) }
    }
}

@Composable
private fun MediaButton(glyph: String, cdRes: Int, size: Dp, onClick: () -> Unit) {
    val cd = stringResource(cdRes)
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(size).semantics { contentDescription = cd },
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(glyph, fontSize = (size.value / 2.5).sp)
    }
}
```

- [ ] **Step 6: MainActivity**

Replace `androidApp/src/main/kotlin/com/saubh/deskbuddy/MainActivity.kt`:

```kotlin
package com.saubh.deskbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.saubh.deskbuddy.ui.ConnectionViewModel
import com.saubh.deskbuddy.ui.DeskBuddyApp

class MainActivity : ComponentActivity() {

    private val viewModel: ConnectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DeskBuddyApp(viewModel)
        }
    }
}
```

- [ ] **Step 7: Full verification**

Run: `./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:assembleDebug :desktopApp:build`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 8: Changelog entry**

Create `2026_Saubhagya_changelog.md` at repo root with an entry summarizing Milestone 1 (format per user's global CLAUDE.md: date, type Feature, files changed, summary, build-verified checkbox).

- [ ] **Step 9: Manual E2E checklist** (requires phone + this Windows PC on the same WiFi)

1. `./gradlew :desktopApp:run` — desktop shows Waiting with IP/port. Allow the Windows Firewall prompt (private networks).
2. Install on phone: `./gradlew :androidApp:installDebug` (USB debugging on).
3. Phone: desktop appears in the discovered list (or type the shown IP manually) → tap → PIN dialog opens; desktop shows PIN.
4. Enter wrong PIN → dialog shows "2 attempts left". Enter correct PIN → Remote screen appears; desktop shows Connected.
5. Play something in Spotify/YouTube on the PC → test all six buttons.
6. Rotate the phone → layout switches portrait/landscape without disconnecting.
7. Kill the desktop app → phone shows Reconnecting, then returns to Connect screen with "Connection lost".
8. Restart desktop app; phone reconnects with saved token — no PIN asked.
9. USB path: `adb reverse tcp:8765 tcp:8765`, then connect manually to `127.0.0.1` on the phone.

---

## Deferred issues (explicitly out of Milestone 1)

- Track-info display, power/app/window/input commands, presentation mode — later milestones per spec.
- Linux actuators (`UnsupportedMediaActuator` placeholder in place).
- TLS, desktop system tray, multi-PC profiles.





