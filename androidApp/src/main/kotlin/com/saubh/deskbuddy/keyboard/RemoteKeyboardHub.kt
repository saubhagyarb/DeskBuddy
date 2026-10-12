package com.saubh.deskbuddy.keyboard

import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.KeyPressCommand
import com.saubh.deskbuddy.protocol.KeyTextCommand
import com.saubh.deskbuddy.protocol.KeyboardModeCommand
import com.saubh.deskbuddy.protocol.Push
import com.saubh.deskbuddy.session.MediaGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/** PC → phone typing: whether the PC keyboard is on, and the keystrokes for KonneKt Keyboard to apply. */
class RemoteKeyboardHub(gateway: MediaGateway, scope: CoroutineScope) {
    private companion object {
        const val DISCONNECT_GRACE_MS = 5_000L
    }

    private val _pcTyping = MutableStateFlow(false)
    val pcTyping: StateFlow<Boolean> = _pcTyping.asStateFlow()

    /** Keystrokes with no bound keyboard are dropped (no replay). */
    private val _input = MutableSharedFlow<Command>(extraBufferCapacity = 256)
    val input: SharedFlow<Command> = _input.asSharedFlow()

    private val _imeBound = MutableStateFlow(false)
    val imeBound: StateFlow<Boolean> = _imeBound.asStateFlow()

    init {
        scope.launch {
            gateway.incoming.filterIsInstance<Push>().collect { push ->
                when (val command = push.command) {
                    is KeyboardModeCommand -> _pcTyping.value = command.active
                    is KeyTextCommand, is KeyPressCommand -> _input.emit(command)
                    else -> Unit
                }
            }
        }
        // A reconnect blip must not switch the phone keyboard back; the PC re-announces typing on reconnect.
        scope.launch {
            gateway.connected.collectLatest { connected ->
                if (!connected) {
                    delay(DISCONNECT_GRACE_MS)
                    _pcTyping.value = false
                }
            }
        }
    }

    /** Set by the IME service while it is alive (it is the current keyboard). */
    fun setImeBound(bound: Boolean) {
        _imeBound.value = bound
    }
}
