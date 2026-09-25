package com.saubh.deskbuddy.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Message

// ---- Phone → Desktop ----------------------------------------------------

@Serializable
@SerialName("pair_request")
data class PairRequest(val deviceName: String) : Message()

@Serializable
@SerialName("pair_attempt")
data class PairAttempt(val pin: String) : Message()

@Serializable
@SerialName("envelope")
data class Envelope(val token: String, val command: Command) : Message()

// ---- Desktop → Phone ----------------------------------------------------

@Serializable
@SerialName("pair_success")
data class PairSuccess(
    val token: String,
    /** Lets the phone recognise this PC again after its IP changes. Empty from older desktops. */
    val desktopName: String = "",
    val desktopId: String = "",
) : Message()

@Serializable
@SerialName("pair_failure")
data class PairFailure(val attemptsLeft: Int) : Message()

@Serializable
@SerialName("ack")
data class Ack(val ok: Boolean, val error: ErrorCode? = null) : Message()

/** Desktop-initiated payload (clipboard update, shared text, file transfer). */
@Serializable
@SerialName("push")
data class Push(val command: Command) : Message()

@Serializable
@SerialName("app_catalog")
data class AppCatalog(
    val apps: List<DesktopApp>,
    val shortcutIds: List<String>,
    /** Base64 PNG per shortcut id (never for non-shortcut apps). */
    val icons: Map<String, String> = emptyMap(),
) : Message()

@Serializable
data class DesktopApp(val id: String, val name: String)

// ---- Commands (shared shapes, direction noted per type) -----------------

@Serializable
sealed class Command

@Serializable
@SerialName("media")
data class MediaCommand(val action: MediaAction) : Command()

/** Set the receiver's clipboard. Both directions. */
@Serializable
@SerialName("clipboard_set")
data class ClipboardCommand(val text: String) : Command()

/** Phone → Desktop. Reply is `Push(ClipboardCommand(text))`. */
@Serializable
@SerialName("clipboard_get")
data object ClipboardRequestCommand : Command()

/** Add text to the receiver's inbox (and clipboard). Both directions. */
@Serializable
@SerialName("text_share")
data class TextShareCommand(val text: String) : Command()

@Serializable
@SerialName("file_offer")
data class FileOfferCommand(
    val transferId: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
) : Command()

/** [data] is base64. Chunks are not acknowledged individually. */
@Serializable
@SerialName("file_chunk")
data class FileChunkCommand(val transferId: String, val index: Int, val data: String) : Command()

@Serializable
@SerialName("file_complete")
data class FileCompleteCommand(val transferId: String) : Command()

@Serializable
@SerialName("file_cancel")
data class FileCancelCommand(val transferId: String) : Command()

/** Phone → Desktop. Reply is `AppCatalog`. */
@Serializable
@SerialName("app_catalog_get")
data object AppCatalogRequestCommand : Command()

@Serializable
@SerialName("app_launch")
data class LaunchAppCommand(val appId: String) : Command()

@Serializable
@SerialName("shortcut_add")
data class AddShortcutCommand(val appId: String) : Command()

@Serializable
@SerialName("shortcut_remove")
data class RemoveShortcutCommand(val appId: String) : Command()

// ---- Media session / audio (M3) ----------------------------------------

@Serializable
enum class PlaybackStatus { PLAYING, PAUSED, STOPPED, NONE }

/** Desktop → Phone (inside [Push]). Position is as of send time; the phone interpolates. */
@Serializable
@SerialName("now_playing")
data class NowPlayingCommand(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val appName: String? = null,
    val status: PlaybackStatus = PlaybackStatus.NONE,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val canSeek: Boolean = false,
    val artworkId: String? = null,
) : Command() {
    companion object { val NONE = NowPlayingCommand() }
}

/** Desktop → Phone. [data] is base64 of the image bytes in [mimeType]. */
@Serializable
@SerialName("media_artwork")
data class MediaArtworkCommand(val artworkId: String, val mimeType: String, val data: String) : Command()

@Serializable
@SerialName("audio_state")
data class AudioStateCommand(val devices: List<AudioDevice>, val volume: Int, val muted: Boolean) : Command()

@Serializable
data class AudioDevice(val id: String, val name: String, val isDefault: Boolean)

/** Phone → Desktop. Reply is Ack; the desktop then pushes now_playing, media_artwork and audio_state. */
@Serializable
@SerialName("media_state_get")
data object MediaStateRequestCommand : Command()

@Serializable
@SerialName("media_seek")
data class MediaSeekCommand(val positionMs: Long) : Command()

/** [volume] is 0..100. */
@Serializable
@SerialName("volume_set")
data class SetVolumeCommand(val volume: Int) : Command()

@Serializable
@SerialName("audio_device_set")
data class SetAudioDeviceCommand(val id: String) : Command()

/** Body of `GET /info`; lets the phone verify a swept host is DeskBuddy. Not a [Message]. [id] is stable per PC. */
@Serializable
data class DesktopInfo(val name: String, val port: Int, val id: String = "")

// ---- Power (M4) ---------------------------------------------------------

@Serializable
enum class PowerAction { SHUTDOWN, RESTART, LOCK }

/** Phone → Desktop. Reply is Ack; the desktop acts right after replying. */
@Serializable
@SerialName("power")
data class PowerCommand(val action: PowerAction) : Command()

@Serializable
enum class MediaAction { PLAY_PAUSE, NEXT, PREVIOUS, VOLUME_UP, VOLUME_DOWN, MUTE_TOGGLE }

@Serializable
enum class ErrorCode { AUTH_REQUIRED, BUSY, UNSUPPORTED_OS, INTERNAL, NOT_FOUND, TRANSFER_FAILED }
