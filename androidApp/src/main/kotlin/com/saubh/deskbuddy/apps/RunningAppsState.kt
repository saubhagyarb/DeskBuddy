package com.saubh.deskbuddy.apps

import com.saubh.deskbuddy.protocol.RunningApp

/** Open windows on the PC, front-most app first. [updatedAtMs] is 0 until the first list arrives. */
data class RunningAppsState(
    val apps: List<RunningApp> = emptyList(),
    val loading: Boolean = false,
    val updatedAtMs: Long = 0L,
) {
    val loaded: Boolean get() = updatedAtMs > 0L
}
