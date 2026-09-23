@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.standby

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.ui.ConnectionViewModel
import com.saubh.deskbuddy.ui.isWideWindow
import com.saubh.deskbuddy.ui.theme.DeskBuddyIcons
import com.saubh.deskbuddy.ui.theme.DeskBuddyTheme
import kotlinx.coroutines.delay
import java.util.Date

/** Tile colour on the pure-black standby background. */
val StandbyTile = Color(0xFF141414)

/** Black, always-on remote: page 1 shortcuts, page 2 media. Back or the close button exits. */
@Composable
fun StandbyScreen(viewModel: ConnectionViewModel, onExit: () -> Unit) {
    BackHandler(onBack = onExit)
    KeepScreenOnImmersive()
    val pager = rememberPagerState(pageCount = { 2 })
    val isWide = isWideWindow()
    val apps by viewModel.apps.state.collectAsStateWithLifecycle()
    val media by viewModel.media.state.collectAsStateWithLifecycle()
    val closeCd = stringResource(R.string.cd_exit_standby)

    DeskBuddyTheme(darkTheme = true) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        rememberClockText(),
                        style = MaterialTheme.typography.displaySmallEmphasized,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(
                        onClick = onExit,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.semantics { contentDescription = closeCd },
                    ) { Icon(DeskBuddyIcons.Close, contentDescription = null) }
                }
                HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                    when (page) {
                        0 -> StandbyShortcutsPage(apps, viewModel.apps::launch)
                        else -> StandbyMediaPage(
                            state = media,
                            isWide = isWide,
                            onMedia = viewModel::sendMedia,
                            onSeek = viewModel.media::seek,
                            onVolume = viewModel.media::setVolume,
                            onToggleMute = viewModel.media::toggleMute,
                            onSelectDevice = viewModel.media::selectDevice,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    repeat(2) { index ->
                        val active = pager.currentPage == index
                        Box(
                            Modifier
                                .size(if (active) 10.dp else 8.dp)
                                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberClockText(): String {
    val context = LocalContext.current
    val format = remember(context) { DateFormat.getTimeFormat(context) }
    var text by remember { mutableStateOf(format.format(Date())) }
    LaunchedEffect(format) {
        while (true) {
            text = format.format(Date())
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    return text
}
