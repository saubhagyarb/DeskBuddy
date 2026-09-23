package com.saubh.deskbuddy.share

import android.content.ClipData
import android.content.ClipboardManager
import com.saubh.deskbuddy.protocol.ClipboardCommand
import com.saubh.deskbuddy.protocol.ClipboardRequestCommand
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Push
import com.saubh.deskbuddy.protocol.TextShareCommand
import com.saubh.deskbuddy.session.RemoteSession
import com.saubh.deskbuddy.ui.ErrorKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Clipboard sync and text sharing. Desktop pushes are applied to the phone
 * clipboard while auto-sync is on; phone clipboard changes are pushed while
 * the app is in the foreground (Android only reports them then).
 */
class ClipboardShareController(
    private val session: RemoteSession,
    private val clipboard: ClipboardManager,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private companion object {
        const val CLIP_LABEL = "DeskBuddy"
    }

    private val _autoSync = MutableStateFlow(true)
    val autoSync: StateFlow<Boolean> = _autoSync.asStateFlow()

    private val _inbox = MutableStateFlow<List<InboxItem>>(emptyList())
    val inbox: StateFlow<List<InboxItem>> = _inbox.asStateFlow()

    /** Last text we wrote to the phone clipboard, so its change event is not echoed back. */
    private var lastApplied: String? = null

    @Volatile
    private var pullPending = false

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!_autoSync.value || !session.isConnected) return@OnPrimaryClipChangedListener
        val text = readClipboard() ?: return@OnPrimaryClipChangedListener
        if (text == lastApplied) return@OnPrimaryClipChangedListener
        scope.launch { session.send(ClipboardCommand(text)) }
    }

    init {
        clipboard.addPrimaryClipChangedListener(clipListener)
        scope.launch {
            session.incoming.filterIsInstance<Push>().collect { onPushed(it.command) }
        }
    }

    fun setAutoSync(enabled: Boolean) {
        _autoSync.value = enabled
    }

    /** Pushes the phone clipboard to the desktop right now. */
    fun sendClipboard() {
        val text = readClipboard()
        if (text == null) {
            session.reportError(ErrorKind.CLIPBOARD_EMPTY)
            return
        }
        scope.launch { session.send(ClipboardCommand(text)) }
    }

    /** Asks the desktop for its clipboard; the reply arrives as a push. */
    fun pullClipboard() {
        pullPending = true
        scope.launch { if (!session.send(ClipboardRequestCommand)) pullPending = false }
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        scope.launch { session.send(TextShareCommand(text)) }
    }

    fun copyToClipboard(text: String) = applyToClipboard(text)

    fun addToInbox(item: InboxItem) {
        _inbox.update { (listOf(item) + it).sortedByDescending { entry -> entry.receivedAt } }
    }

    fun clearInbox() {
        _inbox.value = emptyList()
    }

    fun release() = clipboard.removePrimaryClipChangedListener(clipListener)

    private fun onPushed(command: Command) {
        when (command) {
            is ClipboardCommand -> {
                // Explicit pulls apply even with auto-sync off; unsolicited pushes only with it on.
                val wanted = pullPending || _autoSync.value
                pullPending = false
                if (wanted && command.text.isNotEmpty()) applyToClipboard(command.text)
            }
            is TextShareCommand -> {
                addToInbox(InboxItem.Text(command.text, now()))
                applyToClipboard(command.text)
            }
            else -> Unit
        }
    }

    private fun applyToClipboard(text: String) {
        lastApplied = text
        clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
    }

    private fun readClipboard(): String? =
        clipboard.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.text?.toString()
            ?.takeIf { it.isNotEmpty() }
}
