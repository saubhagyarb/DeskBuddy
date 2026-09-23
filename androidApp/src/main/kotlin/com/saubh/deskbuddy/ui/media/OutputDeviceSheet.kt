@file:OptIn(ExperimentalMaterial3Api::class)

package com.saubh.deskbuddy.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.protocol.AudioDevice
import com.saubh.deskbuddy.ui.components.SectionTitle
import com.saubh.deskbuddy.ui.theme.DeskBuddyIcons

/** Output devices as a radio list; tapping one selects it and closes the sheet. */
@Composable
fun OutputDeviceSheet(devices: List<AudioDevice>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionTitle(stringResource(R.string.media_output_device), Modifier.padding(bottom = 8.dp))
            devices.forEach { device ->
                Surface(
                    onClick = {
                        onSelect(device.id)
                        onDismiss()
                    },
                    shape = MaterialTheme.shapes.large,
                    color = if (device.isDefault) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = device.isDefault, onClick = null)
                        Icon(
                            if (device.name.contains("head", ignoreCase = true)) DeskBuddyIcons.Headphones else DeskBuddyIcons.Speaker,
                            contentDescription = null,
                        )
                        Text(device.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
