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

    @Test
    fun `reachable_givenOffLanButPcOnPhoneHotspot_keepsHotspotPc`() {
        val hotspotPc = SavedDesktop("Laptop", "192.168.43.17", 8765, token = "t")
        val hotspot = listOf(Ipv4Subnet.of("192.168.43.1", 24)!!) // non-null: literal address
        assertEquals(
            listOf(hotspotPc, usb),
            AutoConnectPlan.reachable(listOf(lan, hotspotPc, usb), onLan = false, localSubnets = hotspot),
        )
    }

    @Test
    fun `ipv4Subnet_givenHostsInsideAndOutside_matchesByPrefix`() {
        val subnet = Ipv4Subnet.of("10.0.5.9", 16)!! // non-null: literal address
        assertEquals(true, subnet.contains("10.0.200.1"))
        assertEquals(false, subnet.contains("10.1.0.1"))
        assertEquals(false, subnet.contains("my-pc.local"))
    }
}
