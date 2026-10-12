package com.saubh.deskbuddy.ui.keyboard

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.keyboard.ModifierState
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.saubh.deskbuddy.ui.theme.KonnektIcons

private data class KeySpec(val key: Key, @StringRes val label: Int)
private data class ModifierSpec(val modifier: KeyModifier, @StringRes val label: Int)
private data class ComboSpec(val key: Key, val modifiers: Set<KeyModifier>, @StringRes val label: Int)

private val NAV_KEYS = listOf(
    KeySpec(Key.ESCAPE, R.string.key_esc), KeySpec(Key.TAB, R.string.key_tab),
    KeySpec(Key.LEFT, R.string.key_left), KeySpec(Key.UP, R.string.key_up),
    KeySpec(Key.DOWN, R.string.key_down), KeySpec(Key.RIGHT, R.string.key_right),
    KeySpec(Key.DELETE, R.string.key_del), KeySpec(Key.HOME, R.string.key_home),
    KeySpec(Key.END, R.string.key_end), KeySpec(Key.PAGE_UP, R.string.key_pgup),
    KeySpec(Key.PAGE_DOWN, R.string.key_pgdn),
)
private val MODIFIERS = listOf(
    ModifierSpec(KeyModifier.CTRL, R.string.key_ctrl), ModifierSpec(KeyModifier.ALT, R.string.key_alt),
    ModifierSpec(KeyModifier.SHIFT, R.string.key_shift), ModifierSpec(KeyModifier.META, R.string.key_win),
)
private val COMBOS = listOf(
    ComboSpec(Key.C, setOf(KeyModifier.CTRL), R.string.combo_copy),
    ComboSpec(Key.V, setOf(KeyModifier.CTRL), R.string.combo_paste),
    ComboSpec(Key.Z, setOf(KeyModifier.CTRL), R.string.combo_undo),
    ComboSpec(Key.A, setOf(KeyModifier.CTRL), R.string.combo_select_all),
    ComboSpec(Key.TAB, setOf(KeyModifier.ALT), R.string.combo_alt_tab),
    ComboSpec(Key.D, setOf(KeyModifier.META), R.string.combo_win_d),
)

/**
 * The keys a phone keyboard lacks, pinned right above it: sticky modifiers with explicit Backspace
 * and Enter (which work even when the field is empty), navigation keys, and common shortcuts.
 */
@Composable
fun KeyBar(
    modifiers: ModifierState,
    enabled: Boolean,
    onKey: (Key) -> Unit,
    onCombo: (Key, Set<KeyModifier>) -> Unit,
    onModifierTap: (KeyModifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MODIFIERS.forEach { spec -> ModifierChip(spec, modifiers, enabled, onModifierTap) }
                }
                BarKey(KonnektIcons.Backspace, stringResource(R.string.cd_key_backspace), enabled) { onKey(Key.BACKSPACE) }
                BarKey(KonnektIcons.KeyboardReturn, stringResource(R.string.cd_key_enter), enabled) { onKey(Key.ENTER) }
            }
            ChipRow { NAV_KEYS.forEach { spec -> AssistChip(onClick = { onKey(spec.key) }, enabled = enabled, label = { Text(stringResource(spec.label)) }) } }
            ChipRow {
                COMBOS.forEach { spec ->
                    AssistChip(onClick = { onCombo(spec.key, spec.modifiers) }, enabled = enabled, label = { Text(stringResource(spec.label)) })
                }
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun ModifierChip(spec: ModifierSpec, state: ModifierState, enabled: Boolean, onTap: (KeyModifier) -> Unit) {
    val label = stringResource(spec.label)
    val locked = spec.modifier in state.locked
    FilterChip(
        selected = spec.modifier in state.active,
        onClick = { onTap(spec.modifier) },
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = if (locked) {
            { Icon(KonnektIcons.Lock, contentDescription = stringResource(R.string.cd_modifier_locked, label), modifier = Modifier.size(16.dp)) }
        } else {
            null
        },
    )
}

@Composable
private fun BarKey(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = description }) {
        Icon(icon, contentDescription = null)
    }
}
