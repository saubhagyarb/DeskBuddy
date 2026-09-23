package com.saubh.deskbuddy.server

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

data class InterfaceInfo(
    val name: String,
    val up: Boolean,
    val loopback: Boolean,
    val virtual: Boolean,
    val addresses: List<String>,
)

/** Which local addresses are worth advertising on: real, up adapters with a private IPv4. */
object LanInterfaces {
    fun select(interfaces: List<InterfaceInfo>): List<String> =
        interfaces.filter { it.up && !it.loopback && !it.virtual }
            .flatMap { it.addresses }
            .filter(::isSiteLocalIpv4)
            .distinct()

    fun isSiteLocalIpv4(address: String): Boolean {
        val p = address.split('.').map { it.toIntOrNull() ?: return false }
        if (p.size != 4 || p.any { it !in 0..255 }) return false
        return p[0] == 10 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168)
    }

    fun current(): List<InetAddress> = runCatching {
        val infos = NetworkInterface.getNetworkInterfaces().toList().map { nic ->
            InterfaceInfo(
                name = nic.name,
                up = nic.isUp,
                loopback = nic.isLoopback,
                virtual = nic.isVirtual,
                addresses = nic.inetAddresses.toList().filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress },
            )
        }
        select(infos).map { InetAddress.getByName(it) }
    }.getOrDefault(emptyList())

    fun hostName(): String =
        runCatching { InetAddress.getLocalHost().hostName.removeSuffix(".local") }.getOrDefault("DeskBuddy PC")
}
