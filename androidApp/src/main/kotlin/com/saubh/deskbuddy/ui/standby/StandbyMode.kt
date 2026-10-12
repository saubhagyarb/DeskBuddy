package com.saubh.deskbuddy.ui.standby

/** Standby pages, top to bottom. The two defaults (Clock, Media + Apps) are neighbours. */
enum class StandbyMode { CLOCK, MEDIA_APPS, MULTIMEDIA, DEVELOPMENT }

data class StandbyModeState(val mode: StandbyMode = StandbyMode.CLOCK, val userPicked: Boolean = false)
