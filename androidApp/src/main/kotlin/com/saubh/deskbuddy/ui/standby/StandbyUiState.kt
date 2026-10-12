package com.saubh.deskbuddy.ui.standby

import com.saubh.deskbuddy.media.MediaUiState
import com.saubh.deskbuddy.protocol.DesktopApp
import com.saubh.deskbuddy.ui.standby.clock.ClockFace

data class StandbyUiState(
    /** False until [StandbyActions.onEnter]: the pager waits for the entry mode instead of animating from a stale one. */
    val entered: Boolean = false,
    val mode: StandbyMode = StandbyMode.CLOCK,
    val clockFace: ClockFace = ClockFace.FLIP,
    val connected: Boolean = false,
    val pcName: String = "",
    val reconnectAttempt: Int? = null,
    val media: MediaUiState = MediaUiState(),
    val shortcuts: List<DesktopApp> = emptyList(),
    val shortcutIcons: Map<String, ByteArray> = emptyMap(),
    val runningShortcutIds: Set<String> = emptySet(),
)
