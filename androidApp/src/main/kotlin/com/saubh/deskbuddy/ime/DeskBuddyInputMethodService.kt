package com.saubh.deskbuddy.ime

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.appGraph
import com.saubh.deskbuddy.keyboard.AndroidKeyMapping
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyPressCommand
import com.saubh.deskbuddy.protocol.KeyTextCommand
import com.saubh.deskbuddy.ui.ConnectionUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * KonneKt Keyboard. It has no keys of its own: it applies what the PC keyboard sends to the
 * focused field, shows a slim status bar, and switches back to the previous keyboard when the PC
 * stops typing. Nothing is logged or stored.
 */
class DeskBuddyInputMethodService : InputMethodService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var inputJob: Job? = null
    private var status: TextView? = null

    override fun onCreate() {
        super.onCreate()
        val hub = appGraph.keyboardHub
        hub.setImeBound(true)
        scope.launch {
            var wasTyping = hub.pcTyping.value
            hub.pcTyping.collect { typing ->
                renderStatus()
                if (wasTyping && !typing) switchBack()
                wasTyping = typing
            }
        }
    }

    override fun onCreateInputView(): View = layoutInflater.inflate(R.layout.ime_bar, null).apply {
        status = findViewById(R.id.ime_status)
        findViewById<Button>(R.id.ime_switch).setOnClickListener { switchBack() }
        renderStatus()
    }

    /** The bar is 56 dp: never take over the screen with the landscape extract editor. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        inputJob?.cancel()
        inputJob = scope.launch { appGraph.keyboardHub.input.collect(::apply) }
    }

    override fun onFinishInput() {
        inputJob?.cancel()
        inputJob = null
        super.onFinishInput()
    }

    override fun onDestroy() {
        appGraph.keyboardHub.setImeBound(false)
        scope.cancel()
        super.onDestroy()
    }

    private fun renderStatus() {
        val pc = (appGraph.session.uiState.value as? ConnectionUiState.Connected)?.desktopName
        status?.text = if (appGraph.keyboardHub.pcTyping.value && pc != null) {
            getString(R.string.ime_typing_from, pc)
        } else {
            getString(R.string.ime_pc_off)
        }
    }

    private fun apply(command: Command) {
        if (!appGraph.session.isConnected) return
        val connection = currentInputConnection ?: return
        when (command) {
            is KeyTextCommand -> connection.commitText(command.text, 1)
            is KeyPressCommand -> {
                val action = if (command.key == Key.ENTER && command.modifiers.isEmpty()) {
                    AndroidKeyMapping.enterAction(currentInputEditorInfo?.imeOptions ?: 0)
                } else {
                    null
                }
                if (action != null) connection.performEditorAction(action) else sendKey(connection, command)
            }
            else -> Unit
        }
    }

    private fun sendKey(connection: InputConnection, command: KeyPressCommand) {
        val code = AndroidKeyMapping.keyCode(command.key)
        if (command.modifiers.isEmpty()) {
            // The platform helper marks the events as soft-keyboard input, so apps stay in touch mode.
            sendDownUpKeyEvents(code)
            return
        }
        val meta = AndroidKeyMapping.metaState(command.modifiers)
        val flags = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        val now = SystemClock.uptimeMillis()
        connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags))
        connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags))
    }

    private fun switchBack() {
        val switched = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToPreviousInputMethod()
        } else {
            @Suppress("DEPRECATION")
            val token = window?.window?.attributes?.token
            @Suppress("DEPRECATION")
            token != null && getSystemService(InputMethodManager::class.java).switchToLastInputMethod(token)
        }
        if (!switched) getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    }
}
