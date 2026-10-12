package com.saubh.deskbuddy.session

import com.saubh.deskbuddy.client.DiscoveredDesktop
import com.saubh.deskbuddy.prefs.SavedDesktop

/** Which addresses auto-connect should try, in order. Pure so it can be unit-tested. */
object AutoConnectPlan {

    private val LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost", "::1")

    /**
     * Paired PCs, most recently used first. For each: its last working address, then any
     * address where discovery currently sees the same PC (its IP may have changed since).
     */
    fun candidates(saved: List<SavedDesktop>, discovered: List<DiscoveredDesktop>): List<SavedDesktop> =
        saved.filter { it.token.isNotEmpty() }
            .sortedByDescending { it.lastUsedAt }
            .flatMap { pc ->
                val moved = discovered
                    .filter { it.host != pc.host && pc.isSamePc(it.id, it.name) }
                    .map { pc.copy(host = it.host, port = it.port) }
                listOf(pc) + moved
            }
            .distinctBy { it.host to it.port }

    /**
     * Off Wi-Fi/Ethernet only loopback targets (USB via `adb reverse`) and PCs inside one of the
     * phone's own [localSubnets] (its hotspot or USB tether) can answer, so the rest are skipped.
     */
    fun reachable(
        candidates: List<SavedDesktop>,
        onLan: Boolean,
        localSubnets: List<Ipv4Subnet> = emptyList(),
    ): List<SavedDesktop> =
        if (onLan) {
            candidates
        } else {
            candidates.filter { pc -> pc.host.lowercase() in LOOPBACK_HOSTS || localSubnets.any { it.contains(pc.host) } }
        }
}
