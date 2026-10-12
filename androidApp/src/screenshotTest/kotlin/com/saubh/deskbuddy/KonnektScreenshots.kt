package com.saubh.deskbuddy

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.saubh.deskbuddy.apps.AppsUiState
import com.saubh.deskbuddy.apps.RunningAppsState
import com.saubh.deskbuddy.client.DiscoveredDesktop
import com.saubh.deskbuddy.media.MediaUiState
import com.saubh.deskbuddy.prefs.SavedDesktop
import com.saubh.deskbuddy.protocol.AppWindow
import com.saubh.deskbuddy.protocol.AudioDevice
import com.saubh.deskbuddy.protocol.AudioStateCommand
import com.saubh.deskbuddy.protocol.BrowserTab
import com.saubh.deskbuddy.protocol.DesktopApp
import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus
import com.saubh.deskbuddy.protocol.RunningApp
import com.saubh.deskbuddy.share.InboxItem
import com.saubh.deskbuddy.ui.apps.AppsPane
import com.saubh.deskbuddy.ui.apps.AppsScreen
import com.saubh.deskbuddy.ui.apps.AppsScreenState
import com.saubh.deskbuddy.ui.connect.DevicesScreen
import com.saubh.deskbuddy.ui.connect.DevicesUiState
import com.saubh.deskbuddy.ui.home.HomeScreen
import com.saubh.deskbuddy.ui.home.HomeUiState
import com.saubh.deskbuddy.ui.keyboard.TypeOnPhonePane
import com.saubh.deskbuddy.ui.media.MediaScreen
import com.saubh.deskbuddy.ui.share.ShareScreen
import com.saubh.deskbuddy.ui.share.ShareScreenState
import com.saubh.deskbuddy.ui.shell.ShellTopBar
import com.saubh.deskbuddy.ui.theme.KonnektTheme

private val media = MediaUiState(
    nowPlaying = NowPlayingCommand("Midnight City", "M83", "Hurry Up, We're Dreaming", "Spotify.exe", PlaybackStatus.PLAYING, 95_000, 243_000, true),
    audio = AudioStateCommand(listOf(AudioDevice("1", "Speakers (Realtek)", true)), 64, false),
)
private val shortcuts = listOf(DesktopApp("1", "Chrome"), DesktopApp("2", "Spotify"), DesktopApp("3", "VS Code"), DesktopApp("4", "Explorer"))
private val running = listOf(
    RunningApp(
        "chrome", "Google Chrome",
        listOf(AppWindow("w1", "Inbox (3) - Gmail", listOf(BrowserTab(0, "Inbox (3) - Gmail"), BrowserTab(1, "YouTube"), BrowserTab(2, "Kotlin docs")))),
    ),
    RunningApp("code", "Visual Studio Code", listOf(AppWindow("w2", "MainActivity.kt - KonneKt"), AppWindow("w3", "README.md - notes"))),
)

@Composable
private fun Themed(dark: Boolean = false, content: @Composable () -> Unit) =
    KonnektTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.surface) { content() } }

@PreviewTest
@Preview(name = "Devices", widthDp = 400, heightDp = 860)
@Composable
fun DevicesPreview() = Themed {
    DevicesScreen(
        state = DevicesUiState(
            saved = listOf(SavedDesktop("Saubhagya-PC", "192.168.1.20", 47321, "t")),
            nearby = listOf(DiscoveredDesktop("Living-Room", "192.168.1.31", 47321)),
        ),
        snackbarHostState = SnackbarHostState(),
        onConnect = {}, onReconnect = {}, onForget = {}, onConnectManual = {}, onSubmitPin = {}, onCancel = {},
    )
}

@PreviewTest
@Preview(name = "DevicesFirstRun", widthDp = 400, heightDp = 860)
@Composable
fun DevicesFirstRunPreview() = Themed(dark = true) {
    DevicesScreen(DevicesUiState(), SnackbarHostState(), {}, {}, {}, {}, {}, {})
}

@PreviewTest
@Preview(name = "Home", widthDp = 400, heightDp = 960)
@Composable
fun HomePreview() = Themed {
    Column {
        ShellTopBar("Saubhagya-PC", null, {}, {})
        HomeScreen(HomeUiState("Saubhagya-PC", null, media, shortcuts), {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@PreviewTest
@Preview(name = "HomeDark", widthDp = 400, heightDp = 960)
@Composable
fun HomeDarkPreview() = Themed(dark = true) {
    Column {
        ShellTopBar("Saubhagya-PC", 2, {}, {})
        HomeScreen(HomeUiState("Saubhagya-PC", 2, MediaUiState(), emptyList()), {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@PreviewTest
@Preview(name = "AppsOpen", widthDp = 400, heightDp = 860)
@Composable
fun AppsOpenPreview() = Themed {
    AppsScreen(
        AppsScreenState(AppsUiState(), RunningAppsState(running, updatedAtMs = 1L)),
        AppsPane.OPEN, {}, {}, {}, {}, { _, _ -> }, {}, { _, _ -> }, {},
    )
}

@PreviewTest
@Preview(name = "AppsLaunch", widthDp = 400, heightDp = 860)
@Composable
fun AppsLaunchPreview() = Themed {
    AppsScreen(
        AppsScreenState(AppsUiState(shortcuts + DesktopApp("5", "Notepad"), listOf("1", "2", "3"), loaded = true), RunningAppsState()),
        AppsPane.LAUNCH, {}, {}, {}, {}, { _, _ -> }, {}, { _, _ -> }, {},
    )
}

@PreviewTest
@Preview(name = "Share", widthDp = 400, heightDp = 860)
@Composable
fun SharePreview() = Themed {
    ShareScreen(
        ShareScreenState(true, listOf(InboxItem.Text("https://kotlinlang.org/docs/home.html", 2), InboxItem.File("report.pdf", 1, "Downloads/KonneKt", 1))),
        {}, {}, {}, {}, {}, {}, {}, {},
    )
}

@PreviewTest
@Preview(name = "Media", widthDp = 400, heightDp = 860)
@Composable
fun MediaPreview() = Themed { MediaScreen(media, false, {}, {}, { _, _ -> }, {}, {}) }

@PreviewTest
@Preview(name = "TypeOnPhone", widthDp = 400, heightDp = 860)
@Composable
fun TypeOnPhonePreview() = Themed { TypeOnPhonePane("Saubhagya-PC", imeEnabled = true, imeCurrent = false, pcTyping = true, {}, {}) }
