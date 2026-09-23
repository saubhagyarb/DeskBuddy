package com.saubh.deskbuddy.protocol

import kotlinx.serialization.json.Json

object WireCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    fun encode(message: Message): String = json.encodeToString(Message.serializer(), message)

    fun decode(text: String): Message = json.decodeFromString(Message.serializer(), text)

    /** Null for malformed frames or types this build does not know, so a newer peer cannot kill the connection. */
    fun decodeOrNull(text: String): Message? = runCatching { decode(text) }.getOrNull()

    fun encodeInfo(info: DesktopInfo): String = json.encodeToString(DesktopInfo.serializer(), info)

    fun decodeInfo(text: String): DesktopInfo = json.decodeFromString(DesktopInfo.serializer(), text)
}
