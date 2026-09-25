package com.saubh.deskbuddy.session

import com.saubh.deskbuddy.client.DiscoveredDesktop
import com.saubh.deskbuddy.prefs.SavedDesktop

/** Which addresses auto-connect should try, in order. Pure so it can be unit-tested. */
object AutoConnectPlan {

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
}
