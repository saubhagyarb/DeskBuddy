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
        val link = connectivity.getLinkProperties(network)?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address } ?: return null
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
        Socket().use {
            it.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            true
        }
    }.getOrDefault(false)
}
