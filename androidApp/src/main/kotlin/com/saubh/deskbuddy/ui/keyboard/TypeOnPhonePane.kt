@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.ui.components.ContentMaxWidth
import com.saubh.deskbuddy.ui.components.LetterAvatar
import com.saubh.deskbuddy.ui.components.ScreenPadding
import com.saubh.deskbuddy.ui.theme.KonnektIcons

/**
 * PC → phone: the PC keyboard types into any app on this phone through KonneKt Keyboard.
 * Shown as a checklist so it is obvious which step is missing.
 */
@Composable
fun TypeOnPhonePane(
    pcName: String,
    imeEnabled: Boolean,
    imeCurrent: Boolean,
    pcTyping: Boolean,
    onEnable: () -> Unit,
    onChoose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val column = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = if (pcTyping) scheme.tertiaryContainer else scheme.surfaceContainerHigh,
                contentColor = if (pcTyping) scheme.onTertiaryContainer else scheme.onSurface,
            ),
            modifier = column,
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(KonnektIcons.Keyboard, contentDescription = null, modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (pcTyping) R.string.phone_typing_active else R.string.phone_typing_idle, pcName),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    Text(stringResource(R.string.phone_typing_explain), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        SetupStep(1, stringResource(R.string.phone_step_enable), done = imeEnabled, column) {
            FilledTonalButton(onClick = onEnable) { Text(stringResource(R.string.phone_step_enable_button)) }
        }
        SetupStep(2, stringResource(R.string.phone_step_hotkey), done = pcTyping, column)
        SetupStep(3, stringResource(R.string.phone_step_switch), done = imeCurrent, column) {
            FilledTonalButton(onClick = onChoose, enabled = imeEnabled) { Text(stringResource(R.string.phone_step_switch_button)) }
        }
        Text(
            stringResource(R.string.keyboard_setup_warning),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = column,
        )
    }
}

@Composable
private fun SetupStep(number: Int, text: String, done: Boolean, modifier: Modifier, action: (@Composable () -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val doneLabel = stringResource(R.string.cd_step_done)
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (done) {
                Icon(KonnektIcons.CheckCircle, contentDescription = doneLabel, tint = scheme.primary, modifier = Modifier.size(32.dp))
            } else {
                LetterAvatar(number.toString(), 32.dp, CircleShape, scheme.secondaryContainer, scheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text, style = MaterialTheme.typography.bodyLarge)
                if (!done) action?.invoke()
            }
        }
    }
}
