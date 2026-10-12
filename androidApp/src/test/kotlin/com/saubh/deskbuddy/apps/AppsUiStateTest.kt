package com.saubh.deskbuddy.apps

import com.saubh.deskbuddy.protocol.ActivateAppCommand
import com.saubh.deskbuddy.protocol.LaunchAppCommand
import kotlin.test.Test
import kotlin.test.assertEquals

class AppsUiStateTest {
    @Test
    fun `openCommandFor_givenDesktopCanActivate_sendsActivate`() {
        assertEquals(ActivateAppCommand("a"), AppsUiState(canActivate = true).openCommandFor("a"))
    }

    @Test
    fun `openCommandFor_givenOlderDesktop_sendsLaunch`() {
        assertEquals(LaunchAppCommand("a"), AppsUiState(canActivate = false).openCommandFor("a"))
    }
}
