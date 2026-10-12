package com.saubh.deskbuddy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.saubh.deskbuddy.media.MediaUiState
import com.saubh.deskbuddy.protocol.AudioDevice
import com.saubh.deskbuddy.protocol.AudioStateCommand
import com.saubh.deskbuddy.protocol.DesktopApp
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus
import com.saubh.deskbuddy.protocol.PowerAction
import com.saubh.deskbuddy.power.PowerCountdownState
import com.saubh.deskbuddy.power.PowerOutcome
import com.saubh.deskbuddy.ui.components.PowerCountdownCard
import com.saubh.deskbuddy.ui.standby.StandbyActions
import com.saubh.deskbuddy.ui.standby.StandbyMode
import com.saubh.deskbuddy.ui.standby.StandbyScreen
import com.saubh.deskbuddy.ui.standby.StandbySurface
import com.saubh.deskbuddy.ui.standby.StandbyUiState
import com.saubh.deskbuddy.ui.standby.clock.ClockFace
import com.saubh.deskbuddy.ui.standby.clock.ClockFaceContent
import com.saubh.deskbuddy.ui.standby.clock.ClockTime
import com.saubh.deskbuddy.ui.standby.clock.LocalClockTimeOverride

private object NoActions : StandbyActions {
    override fun onEnter() = Unit
    override fun onLeave() = Unit
    override fun onSelectMode(mode: StandbyMode) = Unit
    override fun onSelectFace(face: ClockFace) = Unit
    override fun onMedia(action: MediaAction) = Unit
    override fun onSeek(positionMs: Long) = Unit
    override fun onVolumeStep(delta: Int) = Unit
    override fun onVolumeRelease() = Unit
    override fun onToggleMute() = Unit
    override fun onSelectDevice(id: String) = Unit
    override fun onActivateApp(appId: String) = Unit
    override fun onLockPc() = Unit
    override fun onRefreshRunning() = Unit
    override fun onPower(action: PowerAction) = Unit
}

private val connected = StandbyUiState(
    entered = true,
    connected = true,
    pcName = "Desk",
    media = MediaUiState(
        nowPlaying = NowPlayingCommand("Midnight City", "M83", null, "Chrome", PlaybackStatus.PLAYING, 95_000, 243_000, true),
        audio = AudioStateCommand(listOf(AudioDevice("1", "Speakers", true), AudioDevice("2", "WH-1000XM5 Headphones", false)), 64, false),
    ),
    shortcuts = listOf(DesktopApp("1", "Chrome"), DesktopApp("2", "Spotify"), DesktopApp("3", "VS Code"), DesktopApp("4", "Terminal")),
    runningShortcutIds = setOf("3"),
)

@Composable
private fun Dark(content: @Composable () -> Unit) {
    StandbySurface(Modifier.fillMaxSize()) { content() }
}

@PreviewTest @Preview(widthDp = 891, heightDp = 411)
@Composable fun StandbyMediaAppsLandscape() = Dark { StandbyScreen(connected.copy(mode = StandbyMode.MEDIA_APPS), NoActions, chromeAlwaysVisible = true) {} }

@PreviewTest @Preview(widthDp = 891, heightDp = 411)
@Composable fun StandbyMultimediaLandscape() = Dark { StandbyScreen(connected.copy(mode = StandbyMode.MULTIMEDIA), NoActions, chromeAlwaysVisible = true) {} }

@PreviewTest @Preview(widthDp = 891, heightDp = 411)
@Composable fun StandbyDevelopmentLandscape() = Dark { StandbyScreen(connected.copy(mode = StandbyMode.DEVELOPMENT), NoActions, chromeAlwaysVisible = true) {} }

@PreviewTest @Preview(widthDp = 411, heightDp = 891)
@Composable fun StandbyMediaAppsPortrait() = Dark { StandbyScreen(connected.copy(mode = StandbyMode.MEDIA_APPS), NoActions, chromeAlwaysVisible = true) {} }

@PreviewTest @Preview(widthDp = 891, heightDp = 411)
@Composable fun StandbyDisconnected() = Dark {
    StandbyScreen(connected.copy(mode = StandbyMode.MULTIMEDIA, connected = false, reconnectAttempt = 2), NoActions, chromeAlwaysVisible = true) {}
}

private val fixed = ClockTime(10, 42, 15)

@PreviewTest @Preview(widthDp = 891, heightDp = 411)
@Composable fun ClockFlip() = Dark { ClockFaceContent(ClockFace.FLIP, fixed, is24h = false, reducedMotion = true, modifier = Modifier.fillMaxSize()) }

@PreviewTest @Preview(widthDp = 891, heightDp = 411)
@Composable fun ClockBubble() = Dark { ClockFaceContent(ClockFace.BUBBLE, fixed, is24h = true, reducedMotion = true, modifier = Modifier.fillMaxSize()) }

@PreviewTest @Preview(widthDp = 891, heightDp = 411)
@Composable fun ClockNeon() = Dark { ClockFaceContent(ClockFace.NEON, fixed, is24h = true, reducedMotion = true, modifier = Modifier.fillMaxSize()) }

@PreviewTest @Preview(widthDp = 411, heightDp = 891)
@Composable fun PowerCountdownCounting() = Dark {
    Column { PowerCountdownCard(PowerCountdownState.Counting(PowerAction.SHUTDOWN, "Desk", 7), onCancel = {}, modifier = Modifier.fillMaxWidth().padding(16.dp)) }
}

@PreviewTest @Preview(widthDp = 411, heightDp = 891)
@Composable fun PowerCountdownCancelled() = Dark {
    Column { PowerCountdownCard(PowerCountdownState.Finished(PowerAction.RESTART, "Desk", PowerOutcome.CANCELLED), onCancel = {}, modifier = Modifier.fillMaxWidth().padding(16.dp)) }
}

@PreviewTest @Preview(widthDp = 891, heightDp = 411)
@Composable fun ClockDotMatrix() = Dark { ClockFaceContent(ClockFace.DOT_MATRIX, fixed, is24h = true, reducedMotion = true, modifier = Modifier.fillMaxSize()) }

@PreviewTest @Preview(widthDp = 411, heightDp = 891)
@Composable fun PowerCountdownSending() = Dark {
    Column { PowerCountdownCard(PowerCountdownState.Sending(PowerAction.SHUTDOWN, "Desk"), onCancel = {}, modifier = Modifier.fillMaxWidth().padding(16.dp)) }
}

// Portrait phone (1209x2553 at ~2.94x ≈ 411x868dp) and a short landscape window.

@PreviewTest @Preview(widthDp = 411, heightDp = 868)
@Composable fun StandbyClockPortrait() = Dark {
    CompositionLocalProvider(LocalClockTimeOverride provides fixed) { StandbyScreen(connected.copy(mode = StandbyMode.CLOCK), NoActions, chromeAlwaysVisible = true) {} }
}

@PreviewTest @Preview(widthDp = 411, heightDp = 868)
@Composable fun StandbyMediaAppsPhonePortrait() = Dark { StandbyScreen(connected.copy(mode = StandbyMode.MEDIA_APPS), NoActions, chromeAlwaysVisible = true) {} }

@PreviewTest @Preview(widthDp = 411, heightDp = 868)
@Composable fun StandbyMultimediaPortrait() = Dark { StandbyScreen(connected.copy(mode = StandbyMode.MULTIMEDIA), NoActions, chromeAlwaysVisible = true) {} }

@PreviewTest @Preview(widthDp = 411, heightDp = 868)
@Composable fun StandbyDevelopmentPortrait() = Dark { StandbyScreen(connected.copy(mode = StandbyMode.DEVELOPMENT), NoActions, chromeAlwaysVisible = true) {} }

@PreviewTest @Preview(widthDp = 800, heightDp = 360)
@Composable fun StandbyMultimediaShortLandscape() = Dark { StandbyScreen(connected.copy(mode = StandbyMode.MULTIMEDIA), NoActions, chromeAlwaysVisible = true) {} }

@PreviewTest @Preview(widthDp = 800, heightDp = 360)
@Composable fun StandbyMediaAppsShortLandscape() = Dark { StandbyScreen(connected.copy(mode = StandbyMode.MEDIA_APPS), NoActions, chromeAlwaysVisible = true) {} }

// Narrow phone (360dp, or a larger display size): the top bar switches to 48dp rail items.
@PreviewTest @Preview(widthDp = 360, heightDp = 780)
@Composable fun StandbyMediaAppsNarrowPortrait() = Dark { StandbyScreen(connected.copy(mode = StandbyMode.MEDIA_APPS), NoActions, chromeAlwaysVisible = true) {} }

// Full-screen landscape clock (2553x1209 at ~2.94x ≈ 868x411dp): the Bubble gradient fills the whole screen, chrome floats over it.
@PreviewTest @Preview(widthDp = 868, heightDp = 411)
@Composable fun StandbyClockBubbleLandscape() = Dark {
    CompositionLocalProvider(LocalClockTimeOverride provides fixed) {
        StandbyScreen(connected.copy(mode = StandbyMode.CLOCK, clockFace = ClockFace.BUBBLE), NoActions, chromeAlwaysVisible = true) {}
    }
}
