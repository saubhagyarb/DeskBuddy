package com.saubh.deskbuddy.ui.share

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saubh.deskbuddy.share.ClipboardShareController
import com.saubh.deskbuddy.share.FileTransferController
import com.saubh.deskbuddy.share.InboxItem
import com.saubh.deskbuddy.share.TransferProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ShareScreenState(
    val autoSync: Boolean = true,
    /** Newest first. */
    val inbox: List<InboxItem> = emptyList(),
    val transfer: TransferProgress? = null,
)

/** Clipboard, text, files and the inbox of things the PC sent. */
class ShareViewModel(
    private val share: ClipboardShareController,
    private val files: FileTransferController,
) : ViewModel() {

    val uiState: StateFlow<ShareScreenState> =
        combine(share.autoSync, share.inbox, files.progress) { autoSync, inbox, transfer ->
            ShareScreenState(autoSync, inbox.sortedByDescending { it.receivedAt }, transfer)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShareScreenState())

    fun setAutoSync(enabled: Boolean) = share.setAutoSync(enabled)

    fun sendClipboard() = share.sendClipboard()

    fun pullClipboard() = share.pullClipboard()

    fun sendText(text: String) = share.sendText(text)

    fun sendFile(uri: Uri) = files.sendFile(uri)

    fun cancelTransfer() = files.cancelSend()

    fun copy(text: String) = share.copyToClipboard(text)

    fun clearInbox() = share.clearInbox()
}
