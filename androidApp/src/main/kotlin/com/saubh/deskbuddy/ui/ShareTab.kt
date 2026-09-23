@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.share.InboxItem
import com.saubh.deskbuddy.share.TransferProgress
import com.saubh.deskbuddy.ui.components.IconAvatar
import com.saubh.deskbuddy.ui.components.MediumButton
import com.saubh.deskbuddy.ui.components.MediumTonalButton
import com.saubh.deskbuddy.ui.components.SectionTitle
import com.saubh.deskbuddy.ui.components.cookieShape
import com.saubh.deskbuddy.ui.theme.DeskBuddyIcons

/** Clipboard, text, file sharing and the inbox of items received from the PC. */
@Composable
fun ShareTab(
    autoSync: Boolean,
    inbox: List<InboxItem>,
    transfer: TransferProgress?,
    onAutoSyncChange: (Boolean) -> Unit,
    onSendClipboard: () -> Unit,
    onPullClipboard: () -> Unit,
    onSendText: (String) -> Unit,
    onSendFile: (Uri) -> Unit,
    onCancelTransfer: () -> Unit,
    onCopy: (String) -> Unit,
    onClearInbox: () -> Unit,
) {
    // One adaptive grid: the three action cards and the inbox items flow into as many
    // columns as fit; section titles and empty states span the full width.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = SHARE_CARD_MIN_WIDTH),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = FabClearance),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        actionCards(autoSync, transfer, onAutoSyncChange, onSendClipboard, onPullClipboard, onSendText, onSendFile, onCancelTransfer)
        inboxSection(inbox, onCopy, onClearInbox)
    }
}

/** Narrowest card; below twice this width the tab is a single column. */
private val SHARE_CARD_MIN_WIDTH = 320.dp

private fun LazyGridScope.actionCards(
    autoSync: Boolean,
    transfer: TransferProgress?,
    onAutoSyncChange: (Boolean) -> Unit,
    onSendClipboard: () -> Unit,
    onPullClipboard: () -> Unit,
    onSendText: (String) -> Unit,
    onSendFile: (Uri) -> Unit,
    onCancelTransfer: () -> Unit,
) {
    item { ClipboardCard(autoSync, onAutoSyncChange, onSendClipboard, onPullClipboard) }
    item { TextCard(onSendText) }
    item { FileCard(transfer, onSendFile, onCancelTransfer) }
}

private fun LazyGridScope.inboxSection(inbox: List<InboxItem>, onCopy: (String) -> Unit, onClear: () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        SectionTitle(stringResource(R.string.share_inbox_title), Modifier.padding(top = 8.dp)) {
            if (inbox.isNotEmpty()) TextButton(onClick = onClear) { Text(stringResource(R.string.share_clear_inbox)) }
        }
    }
    if (inbox.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) { InboxEmpty() }
    } else {
        items(inbox, key = { it.receivedAt.toString() + it.hashCode() }) { InboxCard(it, onCopy) }
    }
}

@Composable
private fun CardHeader(icon: ImageVector, title: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconAvatar(icon, 40.dp, CircleShape, scheme.primaryContainer, scheme.onPrimaryContainer)
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
    }
}

@Composable
private fun ClipboardCard(autoSync: Boolean, onAutoSyncChange: (Boolean) -> Unit, onSend: () -> Unit, onPull: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CardHeader(DeskBuddyIcons.ContentPaste, stringResource(R.string.share_clipboard_title))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.share_auto_sync), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = autoSync, onCheckedChange = onAutoSyncChange)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MediumTonalButton(
                    text = stringResource(R.string.share_send_clipboard),
                    onClick = onSend,
                    icon = DeskBuddyIcons.Upload,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onPull,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f).height(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.MediumContentPadding,
                ) {
                    Icon(DeskBuddyIcons.Download, contentDescription = null, modifier = Modifier.size(24.dp))
                    Text(
                        stringResource(R.string.share_get_clipboard),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TextCard(onSend: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(DeskBuddyIcons.Notes, stringResource(R.string.share_text_title))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(stringResource(R.string.share_text_hint)) },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
            )
            Button(
                onClick = {
                    onSend(draft)
                    draft = ""
                },
                shapes = ButtonDefaults.shapes(),
                enabled = draft.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(DeskBuddyIcons.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.share_send_text), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun FileCard(transfer: TransferProgress?, onSendFile: (Uri) -> Unit, onCancel: () -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(onSendFile) }
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(DeskBuddyIcons.AttachFile, stringResource(R.string.share_file_title))
            if (transfer != null) {
                TransferRow(transfer, onCancel)
            } else {
                MediumButton(
                    text = stringResource(R.string.share_pick_file),
                    onClick = { picker.launch("*/*") },
                    icon = DeskBuddyIcons.Upload,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TransferRow(transfer: TransferProgress, onCancel: () -> Unit) {
    val percent = (transfer.fraction * 100).toInt()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ContainedLoadingIndicator(progress = { transfer.fraction })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(if (transfer.outgoing) R.string.share_sending else R.string.share_receiving, transfer.name),
                style = MaterialTheme.typography.bodyLargeEmphasized,
                maxLines = 1,
            )
            LinearProgressIndicator(progress = { transfer.fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.share_percent, percent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (transfer.outgoing) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun InboxEmpty() {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(DeskBuddyIcons.Inbox, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Text(
                stringResource(R.string.share_inbox_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InboxCard(item: InboxItem, onCopy: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val copyCd = stringResource(R.string.share_copy)
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (item) {
                is InboxItem.Text -> {
                    IconAvatar(DeskBuddyIcons.Notes, 40.dp, CircleShape, scheme.secondaryContainer, scheme.onSecondaryContainer)
                    Text(item.text, modifier = Modifier.weight(1f), maxLines = 4, style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { onCopy(item.text) }, modifier = Modifier.semantics { contentDescription = copyCd }) {
                        Icon(DeskBuddyIcons.ContentCopy, contentDescription = null, tint = scheme.primary)
                    }
                }
                is InboxItem.File -> {
                    IconAvatar(DeskBuddyIcons.Folder, 40.dp, CircleShape, scheme.tertiaryContainer, scheme.onTertiaryContainer)
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyLargeEmphasized, maxLines = 1)
                        Text(
                            stringResource(R.string.share_saved_to, item.location),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
