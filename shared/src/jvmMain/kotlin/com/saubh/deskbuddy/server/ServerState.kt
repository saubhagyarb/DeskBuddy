package com.saubh.deskbuddy.server

sealed class ServerState {
    data object Waiting : ServerState()
    data class Pairing(val pin: String) : ServerState()
    data class Connected(val deviceName: String) : ServerState()
}
