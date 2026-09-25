package com.saubh.deskbuddy.session

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Keeps the phone connected to a paired PC with no user action: while nothing is connected it
 * quietly probes each paired PC's port and connects to the first that answers — e.g. a laptop
 * that just finished booting. Runs every [intervalMs], and at once when a network comes up.
 */
class AutoConnector(
    private val session: RemoteSession,
    private val scope: CoroutineScope,
    private val probe: suspend (host: String, port: Int) -> Boolean = ::portOpen,
    private val intervalMs: Long = 10_000L,
) {
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun start(context: Context) {
        if (job != null) return
        session.startDiscovery()
        registerNetworkCallback(context)
        job = scope.launch {
            while (true) {
                attempt()
                withTimeoutOrNull(intervalMs) { wake.receive() }
            }
        }
    }

    /** Try now instead of waiting for the next tick. */
    fun nudge() {
        wake.trySend(Unit)
    }

    private suspend fun attempt() {
        if (!session.isSearching || session.autoConnectPaused) return
        for (candidate in AutoConnectPlan.candidates(session.savedDesktops.value, session.discovered.value)) {
            if (probe(candidate.host, candidate.port)) {
                // State may have changed while probing (the user tapped Connect).
                if (session.isSearching && !session.autoConnectPaused) session.connect(candidate, quiet = true)
                return
            }
        }
    }

    private fun registerNetworkCallback(context: Context) {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = nudge()
        }
        runCatching { connectivity.registerDefaultNetworkCallback(callback) }.onSuccess { networkCallback = callback }
    }

    private companion object {
        const val PROBE_TIMEOUT_MS = 1_500

        suspend fun portOpen(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
            runCatching {
                Socket().use {
                    it.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS)
                    true
                }
            }.getOrDefault(false)
        }
    }
}
