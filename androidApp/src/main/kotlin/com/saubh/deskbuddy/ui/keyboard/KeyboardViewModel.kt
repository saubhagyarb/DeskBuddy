package com.saubh.deskbuddy.ui.keyboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saubh.deskbuddy.keyboard.KeyboardController
import com.saubh.deskbuddy.keyboard.ModifierState
import com.saubh.deskbuddy.keyboard.RemoteKeyboardHub
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.saubh.deskbuddy.session.RemoteSession
import com.saubh.deskbuddy.ui.ConnectionUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class KeyboardUiState(
    val pcName: String = "",
    /**
     * True while connected *or* reconnecting: disabling the field on a blip would drop focus and
     * close the phone keyboard, which felt like typing had stopped working.
     */
    val canType: Boolean = false,
    val modifiers: ModifierState = ModifierState(),
    /** The PC keyboard is currently typing into this phone. */
    val pcTypingToPhone: Boolean = false,
)

/** Both typing directions: phone → PC (field + special keys) and PC → phone (KonneKt Keyboard status). */
class KeyboardViewModel(
    session: RemoteSession,
    private val keyboard: KeyboardController,
    hub: RemoteKeyboardHub,
) : ViewModel() {

    val uiState: StateFlow<KeyboardUiState> =
        combine(session.uiState, session.savedDesktops, keyboard.modifiers, hub.pcTyping) { link, saved, modifiers, pcTyping ->
            KeyboardUiState(
                pcName = (link as? ConnectionUiState.Connected)?.desktopName ?: saved.firstOrNull()?.name.orEmpty(),
                canType = link is ConnectionUiState.Connected || link is ConnectionUiState.Reconnecting,
                modifiers = modifiers,
                pcTypingToPhone = pcTyping,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KeyboardUiState())

    /** Returns what the field should show after the edit. */
    fun onFieldEdit(old: String, new: String): String = keyboard.onFieldEdit(old, new)

    fun press(key: Key) = keyboard.press(key)

    fun combo(key: Key, modifiers: Set<KeyModifier>) = keyboard.combo(key, modifiers)

    fun tapModifier(modifier: KeyModifier) = keyboard.tapModifier(modifier)
}
