@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.DesktopController
import com.saubh.deskbuddy.server.FileSender
import com.saubh.deskbuddy.ui.components.IconAvatar
import com.saubh.deskbuddy.ui.components.MediumTonalButton
import com.saubh.deskbuddy.ui.components.SectionTitle
import com.saubh.deskbuddy.ui.theme.DeskBuddyIcons

/** Left column: send actions. Right column: what the phone sent us. */
@Composable
fun SharePanel(controller: DesktopController) {
    val inbox by controller.inbox.collectAsState()
    val transfer by controller.transfer.collectAsState()
    val autoSync by controller.autoSyncClipboard.collectAsState()

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { TextCard(controller::sendText) }
            item { FileCard(transfer, onPick = { pickFile("Send file to phone")?.let(controller::sendFile) }, controller::cancelTransfer) }
            item { ClipboardCard(autoSync) { controller.autoSyncClipboard.value = it } }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionTitle("Received from phone") {
                    TextButton(onClick = controller::openReceivedFolder) {
                        Icon(DeskBuddyIcons.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Open folder", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (inbox.isEmpty()) {
                item { InboxEmpty() }
            } else {
                items(inbox, key = { it.receivedAt.toString() + it.hashCode() }) { InboxCard(it, controller::copyToClipboard) }
            }
        }
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
private fun TextCard(onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(DeskBuddyIcons.Notes, "Send text to phone")
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Type or paste text") },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
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
                Text("Send", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun FileCard(transfer: FileSender.Progress?, onPick: () -> Unit, onCancel: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(DeskBuddyIcons.AttachFile, "Send a file to phone")
            if (transfer != null) {
                val fraction = if (transfer.totalBytes > 0) transfer.sentBytes.toFloat() / transfer.totalBytes else 0f
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ContainedLoadingIndicator(progress = { fraction })
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Sending ${transfer.name}", style = MaterialTheme.typography.bodyLargeEmphasized, maxLines = 1)
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            } else {
                MediumTonalButton("Choose a file…", onPick, icon = DeskBuddyIcons.Upload, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ClipboardCard(autoSync: Boolean, onAutoSyncChange: (Boolean) -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(DeskBuddyIcons.ContentPaste, "Clipboard")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-sync clipboard to phone", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Anything you copy here appears on the phone while it is connected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = autoSync, onCheckedChange = onAutoSyncChange)
            }
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
                "Nothing received yet. Text and files shared from the phone show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InboxCard(item: DesktopController.InboxItem, onCopy: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
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
                is DesktopController.InboxItem.Text -> {
                    IconAvatar(DeskBuddyIcons.Notes, 40.dp, CircleShape, scheme.secondaryContainer, scheme.onSecondaryContainer)
                    Text(item.text, modifier = Modifier.weight(1f), maxLines = 4, style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { onCopy(item.text) }) {
                        Icon(DeskBuddyIcons.ContentCopy, contentDescription = "Copy", tint = scheme.primary)
                    }
                }
                is DesktopController.InboxItem.File -> {
                    IconAvatar(DeskBuddyIcons.Folder, 40.dp, CircleShape, scheme.tertiaryContainer, scheme.onTertiaryContainer)
                    Column(Modifier.weight(1f)) {
                        Text(item.path.fileName.toString(), style = MaterialTheme.typography.bodyLargeEmphasized, maxLines = 1)
                        Text(item.path.parent.toString(), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = { onCopy(item.path.toString()) }) {
                        Icon(DeskBuddyIcons.ContentCopy, contentDescription = "Copy path", tint = scheme.primary)
                    }
                }
            }
        }
    }
}

