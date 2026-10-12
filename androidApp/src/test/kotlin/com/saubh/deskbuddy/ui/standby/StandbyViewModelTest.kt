package com.saubh.deskbuddy.ui.standby

import com.saubh.deskbuddy.FakeGateway
import com.saubh.deskbuddy.apps.AppsUiState
import com.saubh.deskbuddy.apps.RunningAppsController
import com.saubh.deskbuddy.media.MediaController
import com.saubh.deskbuddy.power.PowerController
import com.saubh.deskbuddy.power.PowerCountdownState
import com.saubh.deskbuddy.prefs.SavedDesktop
import com.saubh.deskbuddy.protocol.AudioStateCommand
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.MediaCommand
import com.saubh.deskbuddy.protocol.PowerAction
import com.saubh.deskbuddy.protocol.PowerCommand
import com.saubh.deskbuddy.protocol.Push
import com.saubh.deskbuddy.protocol.SetVolumeCommand
import com.saubh.deskbuddy.ui.ConnectionUiState
import com.saubh.deskbuddy.ui.standby.clock.ClockFace
import com.saubh.deskbuddy.ui.standby.clock.ClockFaceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StandbyViewModelTest {
    private class MemoryFaces : ClockFaceStore {
        override val face = MutableStateFlow(ClockFace.FLIP)
        override suspend fun save(face: ClockFace) { this.face.value = face }
    }

    private val connection = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Connected("Desk"))
    private val gateway = FakeGateway()
    private val activated = mutableListOf<String>()
    private lateinit var power: PowerController

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): StandbyViewModel {
        val vm = StandbyViewModel(
            connection = connection,
            savedDesktops = MutableStateFlow(emptyList<SavedDesktop>()),
            media = MediaController(gateway, backgroundScope, clock = { 0L }),
            apps = MutableStateFlow(AppsUiState()),
            activateApp = { activated += it },
            running = RunningAppsController(gateway, backgroundScope),
            power = PowerController(gateway, backgroundScope).also { power = it },
            faces = MemoryFaces(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        return vm
    }

    @Test
    fun `onEnter_givenConnected_showsMediaApps`() = runTest {
        val vm = viewModel()
        vm.onEnter()
        runCurrent()
        assertEquals(StandbyMode.MEDIA_APPS, vm.uiState.value.mode)
        assertTrue(vm.uiState.value.entered)
    }

    @Test
    fun `onEnter_givenAlreadyEntered_keepsUserPick`() = runTest {
        val vm = viewModel()
        vm.onEnter()
        vm.onSelectMode(StandbyMode.DEVELOPMENT)
        vm.onEnter()
        runCurrent()
        assertEquals(StandbyMode.DEVELOPMENT, vm.uiState.value.mode)
    }

    @Test
    fun `connectionLost_givenPcMode_fallsBackToClock`() = runTest {
        val vm = viewModel()
        vm.onEnter()
        vm.onSelectMode(StandbyMode.DEVELOPMENT)
        connection.value = ConnectionUiState.Reconnecting(attempt = 1)
        runCurrent()
        assertEquals(StandbyMode.CLOCK, vm.uiState.value.mode)
        assertEquals(1, vm.uiState.value.reconnectAttempt)
    }

    @Test
    fun `onVolumeStep_givenKnownVolume_sendsClampedSetVolumeThenFinal`() = runTest {
        val vm = viewModel()
        gateway.incoming.emit(Push(AudioStateCommand(emptyList(), volume = 99, muted = false)))
        runCurrent()
        vm.onVolumeStep(VOLUME_STEP)
        vm.onVolumeRelease()
        runCurrent()
        assertEquals(listOf(SetVolumeCommand(100), SetVolumeCommand(100)), gateway.sent.filterIsInstance<SetVolumeCommand>())
    }

    @Test
    fun `onVolumeStep_givenNoAudioState_sendsMediaKey`() = runTest {
        val vm = viewModel()
        vm.onVolumeStep(-VOLUME_STEP)
        runCurrent()
        assertTrue(MediaCommand(MediaAction.VOLUME_DOWN) in gateway.sent)
    }

    @Test
    fun `onActivateAppAndLock_givenTaps_forwardToControllers`() = runTest {
        val vm = viewModel()
        vm.onActivateApp("code")
        vm.onLockPc()
        runCurrent()
        assertEquals(listOf("code"), activated)
        assertTrue(PowerCommand(PowerAction.LOCK) in gateway.sent)
    }

    @Test
    fun `onPower_givenRestart_startsCountdown`() = runTest {
        val vm = viewModel()
        vm.onPower(PowerAction.RESTART)
        runCurrent()
        val countdown = assertIs<PowerCountdownState.Counting>(power.countdown.value)
        assertEquals(PowerAction.RESTART, countdown.action)
        assertEquals("Desk", countdown.pcName)
    }
}
