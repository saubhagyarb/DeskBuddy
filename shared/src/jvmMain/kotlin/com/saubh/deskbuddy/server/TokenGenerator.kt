package com.saubh.deskbuddy.server

import java.security.SecureRandom

object TokenGenerator {
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
