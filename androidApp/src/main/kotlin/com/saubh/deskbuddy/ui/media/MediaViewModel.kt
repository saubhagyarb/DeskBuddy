package com.saubh.deskbuddy.ui.media

import androidx.lifecycle.ViewModel
import com.saubh.deskbuddy.media.MediaController
import com.saubh.deskbuddy.media.MediaUiState
import com.saubh.deskbuddy.protocol.MediaAction
import kotlinx.coroutines.flow.StateFlow

/** Player screen: mirrors the PC's media session and audio output. */
class MediaViewModel(private val media: MediaController) : ViewModel() {
    val uiState: StateFlow<MediaUiState> = media.state

    fun onAction(action: MediaAction) = media.send(action)

    fun seek(positionMs: Long) = media.seek(positionMs)

    fun setVolume(percent: Int, final: Boolean) = media.setVolume(percent, final)

    fun toggleMute() = media.toggleMute()

    fun selectDevice(id: String) = media.selectDevice(id)
}
