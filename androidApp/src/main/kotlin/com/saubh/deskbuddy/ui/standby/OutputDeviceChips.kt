package com.saubh.deskbuddy.ui.standby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.protocol.AudioDevice
import com.saubh.deskbuddy.ui.theme.KonnektIcons

/** Every PC output as a one-tap chip; the current default is filled. */
@Composable
fun OutputDeviceChips(devices: List<AudioDevice>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        devices.forEach { device ->
            val selectedCd = stringResource(R.string.cd_output_device_selected, device.name)
            FilterChip(
                selected = device.isDefault,
                onClick = { if (!device.isDefault) onSelect(device.id) },
                label = { Text(device.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    val headphones = device.name.contains("head", ignoreCase = true) || device.name.contains("buds", ignoreCase = true)
                    Icon(if (headphones) KonnektIcons.Headphones else KonnektIcons.Speaker, contentDescription = null)
                },
                modifier = if (device.isDefault) Modifier.semantics { contentDescription = selectedCd } else Modifier,
            )
        }
    }
}
