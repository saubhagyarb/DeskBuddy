package com.saubh.deskbuddy.apps

import com.saubh.deskbuddy.FakeGateway
import com.saubh.deskbuddy.protocol.AppWindow
import com.saubh.deskbuddy.protocol.BrowserTab
import com.saubh.deskbuddy.protocol.CloseTabCommand
import com.saubh.deskbuddy.protocol.CloseWindowCommand
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Push
import com.saubh.deskbuddy.protocol.RunningApp
import com.saubh.deskbuddy.protocol.RunningAppsCommand
import com.saubh.deskbuddy.protocol.RunningAppsRequestCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RunningAppsControllerTest {
    private val tabs = listOf(BrowserTab(0, "Mail"), BrowserTab(1, "News"), BrowserTab(2, "Docs"))
    private val chromeWindow = AppWindow("w1", "Mail", tabs)
    private val notesWindow = AppWindow("w2", "Notes")
    private val apps = listOf(
        RunningApp("chrome", "Chrome", listOf(chromeWindow)),
        RunningApp("notepad", "Notepad", listOf(notesWindow)),
    )

    @Test
    fun `refresh_givenPushedList_storesItAndStopsLoading`() = runTest {
        val gateway = FakeGateway()
        val controller = RunningAppsController(gateway, backgroundScope, clock = { 42L }).also { runCurrent() }
        controller.refresh()
        runCurrent()
        assertTrue(controller.state.value.loading)
        assertEquals(listOf<Command>(RunningAppsRequestCommand), gateway.sent)
        gateway.incoming.emit(Push(RunningAppsCommand(apps)))
        runCurrent()
        assertEquals(apps, controller.state.value.apps)
        assertFalse(controller.state.value.loading)
        assertTrue(controller.state.value.loaded)
    }

    @Test
    fun `closeWindow_givenLastWindowOfApp_removesAppAtOnceThenRelists`() = runTest {
        val gateway = FakeGateway()
        val controller = RunningAppsController(gateway, backgroundScope).also { runCurrent() }
        gateway.incoming.emit(Push(RunningAppsCommand(apps)))
        runCurrent()
        controller.closeWindow(notesWindow)
        runCurrent()
        assertEquals(listOf("chrome"), controller.state.value.apps.map { it.id })
        assertEquals(listOf<Command>(CloseWindowCommand("w2")), gateway.sent)
        advanceTimeBy(1_300)
        runCurrent()
        assertEquals(RunningAppsRequestCommand, gateway.sent.last())
    }

    @Test
    fun `closeTab_givenMiddleTab_removesAndRenumbersRemainingTabs`() = runTest {
        val gateway = FakeGateway()
        val controller = RunningAppsController(gateway, backgroundScope).also { runCurrent() }
        gateway.incoming.emit(Push(RunningAppsCommand(apps)))
        runCurrent()
        controller.closeTab(chromeWindow, tabs[1])
        runCurrent()
        assertEquals(listOf(BrowserTab(0, "Mail"), BrowserTab(1, "Docs")), controller.state.value.apps.first().windows.single().tabs)
        assertEquals(CloseTabCommand("w1", 1, "News"), gateway.sent.single())
    }

    @Test
    fun `disconnect_clearsList`() = runTest {
        val gateway = FakeGateway()
        val controller = RunningAppsController(gateway, backgroundScope).also { runCurrent() }
        gateway.incoming.emit(Push(RunningAppsCommand(apps)))
        runCurrent()
        gateway.connectedState.value = false
        runCurrent()
        assertTrue(controller.state.value.apps.isEmpty())
    }
}
