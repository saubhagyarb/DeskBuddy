package com.saubh.deskbuddy.pairing

import com.saubh.deskbuddy.protocol.Protocol

class PairingSession(
    val pin: String,
    private val createdAtMillis: Long,
    private val timeoutMillis: Long = Protocol.PAIRING_TIMEOUT_MILLIS,
    attempts: Int = Protocol.MAX_PIN_ATTEMPTS,
) {
    var attemptsLeft: Int = attempts
        private set

    fun isExpired(nowMillis: Long): Boolean = nowMillis - createdAtMillis >= timeoutMillis

    fun attempt(enteredPin: String, nowMillis: Long): PairingResult {
        if (isExpired(nowMillis)) return PairingResult.Expired
        if (attemptsLeft <= 0) return PairingResult.LockedOut
        return if (enteredPin == pin) {
            PairingResult.Success
        } else {
            attemptsLeft -= 1
            if (attemptsLeft == 0) PairingResult.LockedOut else PairingResult.WrongPin(attemptsLeft)
        }
    }
}

sealed class PairingResult {
    data object Success : PairingResult()
    data class WrongPin(val attemptsLeft: Int) : PairingResult()
    data object LockedOut : PairingResult()
    data object Expired : PairingResult()
}
