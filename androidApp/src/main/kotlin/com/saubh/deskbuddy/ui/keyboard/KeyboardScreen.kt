package com.saubh.deskbuddy.ui.keyboard

import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.keyboard.ImeStatus
import com.saubh.deskbuddy.keyboard.TextDiff
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.saubh.deskbuddy.ui.AppViewModelProvider
import com.saubh.deskbuddy.ui.components.ContentMaxWidth
import com.saubh.deskbuddy.ui.components.InfoCard
import com.saubh.deskbuddy.ui.components.ScreenPadding
import com.saubh.deskbuddy.ui.theme.KonnektIcons

enum class KeyboardPane { TYPE_ON_PC, TYPE_ON_PHONE }

private fun emptyField() = TextFieldValue(TextDiff.SENTINEL, TextRange(TextDiff.SENTINEL.length))

@Composable
fun KeyboardRoute(viewModel: KeyboardViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var imeEnabled by remember { mutableStateOf(ImeStatus.isEnabled(context)) }
    var imeCurrent by remember { mutableStateOf(ImeStatus.isCurrent(context)) }
    // Re-check when returning from Android's keyboard settings or picker.
    LifecycleResumeEffect(Unit) {
        imeEnabled = ImeStatus.isEnabled(context)
        imeCurrent = ImeStatus.isCurrent(context)
        onPauseOrDispose { }
    }
    var pane by rememberSaveable { mutableStateOf(KeyboardPane.TYPE_ON_PC) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        PaneSwitcher(pane) { pane = it }
        when (pane) {
            KeyboardPane.TYPE_ON_PC -> TypeOnPcPane(
                state = state,
                onFieldEdit = viewModel::onFieldEdit,
                onKey = viewModel::press,
                onCombo = viewModel::combo,
                onModifierTap = viewModel::tapModifier,
            )
            KeyboardPane.TYPE_ON_PHONE -> TypeOnPhonePane(
                pcName = state.pcName,
                imeEnabled = imeEnabled,
                imeCurrent = imeCurrent,
                pcTyping = state.pcTypingToPhone,
                onEnable = { ImeStatus.openSettings(context) },
                onChoose = { context.getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
            )
        }
    }
}

@Composable
private fun PaneSwitcher(pane: KeyboardPane, onChange: (KeyboardPane) -> Unit) {
    val panes = listOf(
        Triple(KeyboardPane.TYPE_ON_PC, R.string.keyboard_pane_pc, KonnektIcons.Computer),
        Triple(KeyboardPane.TYPE_ON_PHONE, R.string.keyboard_pane_phone, KonnektIcons.PhoneAndroid),
    )
    SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp)) {
        panes.forEachIndexed { index, (value, label, icon) ->
            SegmentedButton(
                selected = pane == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, panes.size),
                icon = { SegmentedButtonDefaults.Icon(active = pane == value) { Icon(icon, contentDescription = null) } },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

/**
 * Phone → PC. Every edit is diffed and sent live, so autocorrect and auto-capitalisation are off:
 * they rewrite text after the fact, which on the PC means bursts of Backspace and retyping.
 */
@Composable
private fun TypeOnPcPane(
    state: KeyboardUiState,
    onFieldEdit: (String, String) -> String,
    onKey: (Key) -> Unit,
    onCombo: (Key, Set<KeyModifier>) -> Unit,
    onModifierTap: (KeyModifier) -> Unit,
) {
    var field by remember { mutableStateOf(emptyField()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.canType) { if (state.canType) focus.requestFocus() }
    val pressKey: (Key) -> Unit = { key ->
        onKey(key)
        if (key == Key.ENTER) field = emptyField()
    }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val column = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()
            Text(
                if (state.canType) stringResource(R.string.keyboard_typing_into, state.pcName) else stringResource(R.string.keyboard_not_connected),
                style = MaterialTheme.typography.titleMedium,
                modifier = column,
            )
            OutlinedTextField(
                value = field,
                onValueChange = { new ->
                    val display = onFieldEdit(field.text, new.text)
                    field = if (display == new.text) new else TextFieldValue(display, TextRange(display.length))
                },
                enabled = state.canType,
                singleLine = true,
                label = { Text(stringResource(R.string.keyboard_field_label)) },
                supportingText = { Text(stringResource(R.string.keyboard_field_hint)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { pressKey(Key.ENTER) }),
                shape = MaterialTheme.shapes.large,
                modifier = column.focusRequester(focus),
            )
            InfoCard(stringResource(R.string.keyboard_tip), column, icon = KonnektIcons.Info)
        }
        KeyBar(state.modifiers, state.canType, pressKey, onCombo, onModifierTap)
    }
}
