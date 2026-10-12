package com.saubh.deskbuddy.session

/** An IPv4 network (address + prefix length), e.g. the phone's own hotspot or USB-tether subnet. */
data class Ipv4Subnet(private val network: Int, private val prefixLength: Int) {
    private val mask: Int get() = if (prefixLength == 0) 0 else -1 shl (32 - prefixLength)

    /** False for host names and anything that is not a dotted IPv4 literal. */
    fun contains(host: String): Boolean {
        val ip = parse(host) ?: return false
        return ip and mask == network and mask
    }

    companion object {
        fun of(address: String, prefixLength: Int): Ipv4Subnet? =
            parse(address)?.takeIf { prefixLength in 0..32 }?.let { Ipv4Subnet(it, prefixLength) }

        private fun parse(host: String): Int? {
            val parts = host.split('.')
            if (parts.size != 4) return null
            var value = 0
            for (part in parts) {
                val octet = part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
                value = (value shl 8) or octet
            }
            return value
        }
    }
}
