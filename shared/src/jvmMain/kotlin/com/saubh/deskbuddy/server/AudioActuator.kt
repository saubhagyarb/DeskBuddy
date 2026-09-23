package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.AudioDevice

/** Output (render) endpoints and the master volume of the current default one. */
interface AudioActuator {
    fun devices(): List<AudioDevice>
    /** 0..100 */
    fun volume(): Int
    fun setVolume(percent: Int)
    fun isMuted(): Boolean
    /** False when [id] is not a known render endpoint. */
    fun setDefaultDevice(id: String): Boolean
}

class UnsupportedAudioActuator(private val osName: String) : AudioActuator {
    override fun devices(): List<AudioDevice> = unsupported()
    override fun volume(): Int = unsupported()
    override fun setVolume(percent: Int) = unsupported()
    override fun isMuted(): Boolean = unsupported()
    override fun setDefaultDevice(id: String): Boolean = unsupported()
    private fun unsupported(): Nothing = throw UnsupportedOsException(osName)
}
