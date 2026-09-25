package com.saubh.deskbuddy.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.ui.theme.DeskBuddyTheme

@Composable
fun DeskBuddyApp(viewModel: ConnectionViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saved by viewModel.savedDesktops.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        viewModel.errors.collect { error ->
            snackbarHostState.showSnackbar(resources.getString(error.messageRes()))
        }
    }

    DeskBuddyTheme {
        Box(Modifier.fillMaxSize()) {
            when (val s = state) {
                is ConnectionUiState.Connected,
                is ConnectionUiState.Reconnecting,
                -> RemoteScreen(state = s, viewModel = viewModel)
                else ->
                    ConnectScreen(
                        state = s,
                        saved = saved,
                        onReconnect = viewModel::reconnect,
                        onForget = viewModel::forget,
                        onConnect = viewModel::connect,
                        onSubmitPin = viewModel::submitPin,
                        onCancelPairing = viewModel::disconnect,
                        onStartDiscovery = viewModel::startDiscovery,
                        onVisibleChange = viewModel::setConnectScreenVisible,
                    )
            }
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

fun ErrorKind.messageRes(): Int = when (this) {
    ErrorKind.CONNECTION_FAILED -> R.string.err_connection_failed
    ErrorKind.CONNECTION_LOST -> R.string.err_connection_lost
    ErrorKind.PAIRING_REJECTED -> R.string.err_pairing_rejected
    ErrorKind.DESKTOP_BUSY -> R.string.err_desktop_busy
    ErrorKind.UNSUPPORTED_OS -> R.string.err_unsupported_os
    ErrorKind.INTERNAL -> R.string.err_internal
    ErrorKind.NOT_CONNECTED -> R.string.err_not_connected
    ErrorKind.APP_NOT_FOUND -> R.string.err_app_not_found
    ErrorKind.TRANSFER_FAILED -> R.string.err_transfer_failed
    ErrorKind.CLIPBOARD_EMPTY -> R.string.err_clipboard_empty
    ErrorKind.FILE_UNREADABLE -> R.string.err_file_unreadable
}
