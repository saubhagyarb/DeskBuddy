package com.saubh.deskbuddy.discovery

/** Host addresses worth probing for a DeskBuddy desktop, given the phone's own address. */
object SubnetHosts {
    private const val MAX_PREFIX_FOR_FULL_RANGE = 24

    /** Other hosts in the phone's subnet, capped to its /24 when the network is wider. Empty on bad input. */
    fun candidates(ip: String, prefixLength: Int): List<String> {
        val parts = ip.split('.').map { it.toIntOrNull() ?: return emptyList() }
        if (parts.size != 4 || parts.any { it !in 0..255 } || prefixLength !in 1..32) return emptyList()
        val self = parts.fold(0L) { acc, p -> (acc shl 8) or p.toLong() }
        val prefix = maxOf(prefixLength, MAX_PREFIX_FOR_FULL_RANGE)
        val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val network = self and mask
        val broadcast = network or (mask.inv() and 0xFFFFFFFFL)
        return ((network + 1) until broadcast).filter { it != self }.map(::dotted)
    }

    private fun dotted(value: Long): String =
        "${(value shr 24) and 0xFF}.${(value shr 16) and 0xFF}.${(value shr 8) and 0xFF}.${value and 0xFF}"
}
