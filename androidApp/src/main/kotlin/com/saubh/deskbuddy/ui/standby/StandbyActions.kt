package com.saubh.deskbuddy.ui.standby

import androidx.compose.runtime.Stable
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.PowerAction
import com.saubh.deskbuddy.ui.standby.clock.ClockFace

@Stable
interface StandbyActions {
    fun onEnter()
    fun onLeave()
    fun onSelectMode(mode: StandbyMode)
    fun onSelectFace(face: ClockFace)
    fun onMedia(action: MediaAction)
    fun onSeek(positionMs: Long)
    fun onVolumeStep(delta: Int)
    fun onVolumeRelease()
    fun onToggleMute()
    fun onSelectDevice(id: String)
    fun onActivateApp(appId: String)
    fun onLockPc()
    fun onRefreshRunning()
    fun onPower(action: PowerAction)
}
