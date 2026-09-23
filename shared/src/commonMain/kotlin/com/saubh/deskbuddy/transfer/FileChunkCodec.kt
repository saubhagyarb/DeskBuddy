package com.saubh.deskbuddy.transfer

import kotlin.io.encoding.Base64

/** Base64 encoding for file chunks carried inside JSON text frames. */
object FileChunkCodec {
    fun encode(bytes: ByteArray): String = Base64.Default.encode(bytes)

    fun decode(text: String): ByteArray = Base64.Default.decode(text)
}
