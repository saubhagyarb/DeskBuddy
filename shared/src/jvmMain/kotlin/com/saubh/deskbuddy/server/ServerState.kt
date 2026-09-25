package com.saubh.deskbuddy.server

sealed class ServerState {
    data object Waiting : ServerState()
    data class Pairing(val pin: String) : ServerState()

    /** One entry per connected phone, in connection order. */
    data class Connected(val devices: List<String>) : ServerState()
}
