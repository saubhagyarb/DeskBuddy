package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.DesktopApp

interface AppActuator {
    /** Installed programs the user could pick from, sorted by name. */
    fun installedApps(): List<DesktopApp>

    /** Launches an app. [DesktopApp.id] is the launch path on this OS. */
    fun launch(app: DesktopApp)
}

class UnsupportedAppActuator(private val osName: String) : AppActuator {
    override fun installedApps(): List<DesktopApp> = emptyList()
    override fun launch(app: DesktopApp): Unit = throw UnsupportedOsException(osName)
}
