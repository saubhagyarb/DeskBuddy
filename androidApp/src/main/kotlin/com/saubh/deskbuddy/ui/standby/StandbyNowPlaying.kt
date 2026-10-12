package com.saubh.deskbuddy.ui.standby

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.media.MediaUiState
import com.saubh.deskbuddy.ui.media.NowPlayingArtwork
import com.saubh.deskbuddy.ui.media.Timeline
import com.saubh.deskbuddy.ui.media.Transport

/** Artwork (cross-faded per track), titles, seekable timeline and transport. */
@Composable
fun StandbyNowPlaying(
    media: MediaUiState,
    actions: StandbyActions,
    artworkSize: Dp,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    compactGap: Dp = 8.dp,
) {
    val scheme = MaterialTheme.colorScheme
    if (compact) {
        CompactNowPlaying(media, actions, artworkSize, compactGap, modifier)
        return
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Crossfade(targetState = media.artworkId to media.artwork, animationSpec = tween(250), label = "artwork") { (_, bytes) ->
            NowPlayingArtwork(bytes, artworkSize, scheme.surfaceContainerHigh, scheme.onSurfaceVariant)
        }
        val np = media.nowPlaying
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                np?.title ?: stringResource(R.string.media_nothing_playing),
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            np?.artist?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Timeline(media, actions::onSeek)
        Transport(media, actions::onMedia, Modifier.fillMaxWidth())
    }
}

/** Artwork beside the titles, then timeline and transport: for short landscape screens and portrait Media + Apps. */
@Composable
private fun CompactNowPlaying(media: MediaUiState, actions: StandbyActions, artworkSize: Dp, gap: Dp, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Crossfade(targetState = media.artworkId to media.artwork, animationSpec = tween(250), label = "artwork") { (_, bytes) ->
                NowPlayingArtwork(bytes, artworkSize, scheme.surfaceContainerHigh, scheme.onSurfaceVariant)
            }
            val np = media.nowPlaying
            Column(Modifier.weight(1f)) {
                Text(
                    np?.title ?: stringResource(R.string.media_nothing_playing),
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                np?.artist?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Timeline(media, actions::onSeek)
        Transport(media, actions::onMedia, Modifier.fillMaxWidth())
    }
}
