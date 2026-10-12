package com.saubh.deskbuddy.server.audio

/** Core Audio's four-character codes ('dev#', 'outp', …) as the big-endian UInt32 the C API expects. */
object FourCc {
    fun of(code: String): Int {
        require(code.length == 4 && code.all { it.code in 0x20..0x7E }) { "FourCC must be 4 printable ASCII chars: '$code'" }
        return code.fold(0) { acc, c -> (acc shl 8) or c.code }
    }
}
