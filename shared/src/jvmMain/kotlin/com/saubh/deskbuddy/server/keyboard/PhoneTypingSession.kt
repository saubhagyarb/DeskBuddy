package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyPressCommand
import com.saubh.deskbuddy.protocol.KeyTextCommand
import com.saubh.deskbuddy.protocol.KeyboardModeCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.event.KeyEvent

/**
 * The PC's "type on phone" mode. [send] queues a command for the phone (in order);
 * [phoneName] is the connected phone, or null. Called from the hotkey thread and the AWT thread.
 */
class PhoneTypingSession(
    private val send: (Command) -> Unit,
    private val phoneName: () -> String?,
    private val translator: AwtKeyTranslator = AwtKeyTranslator(),
) {
    private companion object {
        const val ECHO_CHARS = 20
    }

    private val _state = MutableStateFlow<TypingState>(TypingState.Idle)
    val state: StateFlow<TypingState> = _state.asStateFlow()

    private val _echo = MutableStateFlow("")
    val echo: StateFlow<String> = _echo.asStateFlow()

    @Synchronized
    fun toggle() = if (_state.value is TypingState.Active) exit(ExitReason.HOTKEY) else enter()

    @Synchronized
    fun enter() {
        if (_state.value is TypingState.Active) return
        val name = phoneName()
        if (name == null) {
            _state.value = TypingState.NoPhone
            return
        }
        translator.reset()
        _echo.value = ""
        _state.value = TypingState.Active(name)
        send(KeyboardModeCommand(active = true))
    }

    @Synchronized
    fun exit(reason: ExitReason) {
        if (_state.value !is TypingState.Active) return
        send(KeyboardModeCommand(active = false))
        _state.value = if (reason == ExitReason.PHONE_DISCONNECTED) TypingState.PhoneDisconnected else TypingState.Idle
    }

    /** True when the key was consumed (every key is, while active). */
    @Synchronized
    fun onKey(raw: RawKey): Boolean {
        if (_state.value !is TypingState.Active) return false
        if (raw.type == RawKey.Type.PRESSED && raw.keyCode == KeyEvent.VK_ESCAPE) {
            exit(ExitReason.ESCAPE)
            return true
        }
        translator.translate(raw).forEach { command ->
            when {
                command is KeyTextCommand -> _echo.value = (_echo.value + command.text).takeLast(ECHO_CHARS)
                command is KeyPressCommand && command.key == Key.BACKSPACE -> _echo.value = _echo.value.dropLast(1)
            }
            send(command)
        }
        return true
    }

    /**
     * The phone came back on a new connection (a Wi-Fi blip reconnects before the old socket times
     * out, so the server never passes through "no phone"). The phone reset its typing state on the
     * drop, so tell the new connection that the PC keyboard is still on.
     */
    @Synchronized
    fun phoneReconnected() {
        if (_state.value is TypingState.Active) send(KeyboardModeCommand(active = true))
    }

    @Synchronized
    fun dismissNotice() {
        if (_state.value is TypingState.NoPhone || _state.value is TypingState.PhoneDisconnected) _state.value = TypingState.Idle
    }
}
