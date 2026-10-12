package com.saubh.deskbuddy.keyboard

import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.saubh.deskbuddy.protocol.KeyPressCommand
import com.saubh.deskbuddy.protocol.KeyTextCommand
import com.saubh.deskbuddy.session.MediaGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Phone → PC typing: field edits become keystrokes, sent strictly in order through one outbox. */
class KeyboardController(private val gateway: MediaGateway, scope: CoroutineScope) {
    private companion object {
        /** Well under the desktop's 4096-char limit per key_text. */
        const val MAX_CHUNK = 1_000
    }

    private val _modifiers = MutableStateFlow(ModifierState())
    val modifiers: StateFlow<ModifierState> = _modifiers.asStateFlow()

    private val outbox = Channel<Command>(Channel.UNLIMITED)

    init {
        // One failed send must never end the loop: later keystrokes would queue forever, unread.
        scope.launch {
            for (command in outbox) {
                try {
                    gateway.send(command)
                } catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive()
                } catch (e: Exception) {
                    // Dropped; the session reports connection problems itself.
                }
            }
        }
    }

    /** Returns what the field should show now (the old value when a combo consumed the typed key). */
    fun onFieldEdit(old: String, new: String): String {
        val edit = TextDiff.between(old, new)
        val active = _modifiers.value.active
        if (active.isNotEmpty()) {
            val typed = edit.inserted.singleOrNull()
            val comboKey = typed?.let(Key::forChar)
            if (edit.backspaces == 0 && comboKey != null) {
                // An uppercase letter means Shift was held on the phone keyboard.
                val mods = if (typed.isUpperCase()) active + KeyModifier.SHIFT else active
                outbox.trySend(KeyPressCommand(comboKey, mods))
                _modifiers.update { it.consumed() }
                return old
            }
            if (edit.backspaces == 1 && edit.inserted.isEmpty()) {
                // e.g. Ctrl+Backspace deletes a word on the PC.
                outbox.trySend(KeyPressCommand(Key.BACKSPACE, active))
                _modifiers.update { it.consumed() }
                return TextDiff.display(new)
            }
        }
        repeat(edit.backspaces) { outbox.trySend(KeyPressCommand(Key.BACKSPACE)) }
        edit.inserted.chunked(MAX_CHUNK).forEach { outbox.trySend(KeyTextCommand(it)) }
        return TextDiff.display(new)
    }

    fun enter() = press(Key.ENTER)

    fun press(key: Key) {
        outbox.trySend(KeyPressCommand(key, _modifiers.value.active))
        _modifiers.update { it.consumed() }
    }

    fun combo(key: Key, modifiers: Set<KeyModifier>) {
        outbox.trySend(KeyPressCommand(key, modifiers))
    }

    fun tapModifier(modifier: KeyModifier) = _modifiers.update { it.tap(modifier) }
}
