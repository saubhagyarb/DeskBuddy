package com.saubh.deskbuddy.server

interface MediaActuator {
    fun playPause()
    fun next()
    fun previous()
    fun volumeUp()
    fun volumeDown()
    fun muteToggle()
}

class UnsupportedOsException(osName: String) : Exception("Unsupported OS: $osName")

class UnsupportedMediaActuator(private val osName: String) : MediaActuator {
    override fun playPause() = unsupported()
    override fun next() = unsupported()
    override fun previous() = unsupported()
    override fun volumeUp() = unsupported()
    override fun volumeDown() = unsupported()
    override fun muteToggle() = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOsException(osName)
}
