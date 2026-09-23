package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.pairing.PairingResult
import com.saubh.deskbuddy.pairing.PairingSession
import com.saubh.deskbuddy.pairing.PinGenerator
import com.saubh.deskbuddy.protocol.Ack
import com.saubh.deskbuddy.protocol.AddShortcutCommand
import com.saubh.deskbuddy.protocol.AppCatalogRequestCommand
import com.saubh.deskbuddy.protocol.AudioStateCommand
import com.saubh.deskbuddy.protocol.MediaArtworkCommand
import com.saubh.deskbuddy.protocol.MediaSeekCommand
import com.saubh.deskbuddy.protocol.MediaStateRequestCommand
import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.SetAudioDeviceCommand
import com.saubh.deskbuddy.protocol.SetVolumeCommand
import com.saubh.deskbuddy.protocol.ClipboardCommand
import com.saubh.deskbuddy.protocol.ClipboardRequestCommand
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Envelope
import com.saubh.deskbuddy.protocol.ErrorCode
import com.saubh.deskbuddy.protocol.FileCancelCommand
import com.saubh.deskbuddy.protocol.FileChunkCommand
import com.saubh.deskbuddy.protocol.FileCompleteCommand
import com.saubh.deskbuddy.protocol.FileOfferCommand
import com.saubh.deskbuddy.protocol.LaunchAppCommand
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.MediaCommand
import com.saubh.deskbuddy.protocol.Message
import com.saubh.deskbuddy.protocol.PairAttempt
import com.saubh.deskbuddy.protocol.PairFailure
import com.saubh.deskbuddy.protocol.PairRequest
import com.saubh.deskbuddy.protocol.PairSuccess
import com.saubh.deskbuddy.protocol.Push
import com.saubh.deskbuddy.protocol.RemoveShortcutCommand
import com.saubh.deskbuddy.protocol.TextShareCommand

data class SessionReply(val message: Message?, val closeConnection: Boolean = false)

interface SessionListener {
    fun onPairingStarted(pin: String)
    fun onPairingEnded()
    fun onAuthenticated(deviceName: String)
}

class ControlSession(
    private val store: PairedDeviceStore,
    private val actuators: Actuators,
    private val listener: SessionListener,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val newPin: () -> String = { PinGenerator.generate() },
    private val newToken: () -> String = { TokenGenerator.generate() },
) {
    private companion object {
        val OK = Ack(ok = true)
        val TRANSFER_FAILED = Ack(ok = false, error = ErrorCode.TRANSFER_FAILED)
        val NOT_FOUND = Ack(ok = false, error = ErrorCode.NOT_FOUND)
        val INTERNAL = Ack(ok = false, error = ErrorCode.INTERNAL)
    }

    private var pairing: PairingSession? = null
    private var pendingDeviceName: String = "Unknown device"

    fun onMessage(message: Message): SessionReply = when (message) {
        is PairRequest -> startPairing(message)
        is PairAttempt -> handleAttempt(message)
        is Envelope -> handleEnvelope(message)
        else -> SessionReply(Ack(ok = false, error = ErrorCode.INTERNAL))
    }

    private fun startPairing(request: PairRequest): SessionReply {
        pendingDeviceName = request.deviceName
        val session = PairingSession(pin = newPin(), createdAtMillis = now())
        pairing = session
        listener.onPairingStarted(session.pin)
        return SessionReply(OK)
    }

    private fun handleAttempt(attempt: PairAttempt): SessionReply {
        val session = pairing
            ?: return SessionReply(Ack(ok = false, error = ErrorCode.INTERNAL), closeConnection = true)
        return when (val result = session.attempt(attempt.pin, now())) {
            is PairingResult.Success -> {
                val token = newToken()
                store.add(PairedDevice(pendingDeviceName, token))
                endPairing()
                listener.onAuthenticated(pendingDeviceName)
                SessionReply(PairSuccess(token))
            }
            is PairingResult.WrongPin -> SessionReply(PairFailure(result.attemptsLeft))
            is PairingResult.LockedOut -> {
                endPairing()
                SessionReply(PairFailure(attemptsLeft = 0), closeConnection = true)
            }
            is PairingResult.Expired -> {
                endPairing()
                SessionReply(PairFailure(attemptsLeft = 0), closeConnection = true)
            }
        }
    }

    private fun endPairing() {
        pairing = null
        listener.onPairingEnded()
    }

    private fun handleEnvelope(envelope: Envelope): SessionReply {
        if (!store.isValidToken(envelope.token)) {
            return SessionReply(Ack(ok = false, error = ErrorCode.AUTH_REQUIRED))
        }
        listener.onAuthenticated(store.deviceNameForToken(envelope.token) ?: "Unknown device")
        return try {
            SessionReply(execute(envelope.command))
        } catch (e: UnsupportedOsException) {
            SessionReply(Ack(ok = false, error = ErrorCode.UNSUPPORTED_OS))
        } catch (e: Exception) {
            SessionReply(Ack(ok = false, error = ErrorCode.INTERNAL))
        }
    }

    /** Returns the reply to send, or null when the command is deliberately silent. */
    private fun execute(command: Command): Message? = when (command) {
        is MediaCommand -> media(command.action)
        is ClipboardCommand -> OK.also { actuators.share.setClipboard(command.text) }
        is ClipboardRequestCommand -> Push(ClipboardCommand(actuators.share.getClipboard().orEmpty()))
        is TextShareCommand -> OK.also { actuators.share.onTextShared(command.text) }
        is FileOfferCommand -> if (actuators.files.offer(command)) OK else TRANSFER_FAILED
        is FileChunkCommand -> when (actuators.files.chunk(command)) {
            FileReceiver.ChunkResult.FAILED -> TRANSFER_FAILED
            FileReceiver.ChunkResult.OK, FileReceiver.ChunkResult.IGNORED -> null
        }
        is FileCompleteCommand ->
            if (actuators.files.complete(command.transferId) != null) OK else TRANSFER_FAILED
        is FileCancelCommand -> OK.also { actuators.files.cancel(command.transferId) }
        is AppCatalogRequestCommand -> actuators.apps.refresh()
        is LaunchAppCommand -> if (actuators.apps.launch(command.appId)) OK else NOT_FOUND
        is AddShortcutCommand ->
            if (actuators.apps.addShortcut(command.appId)) actuators.apps.catalog.value else NOT_FOUND
        is RemoveShortcutCommand -> {
            actuators.apps.removeShortcut(command.appId)
            actuators.apps.catalog.value
        }
        // Desktop → phone only; a phone must never send these.
        is NowPlayingCommand, is MediaArtworkCommand, is AudioStateCommand -> INTERNAL
        is MediaStateRequestCommand -> OK.also { actuators.onStateRequested() }
        is MediaSeekCommand -> if (actuators.mediaSession.seek(command.positionMs)) OK else NOT_FOUND
        is SetVolumeCommand -> OK.also {
            actuators.audio.setVolume(command.volume.coerceIn(0, 100))
            actuators.onAudioChanged()
        }
        is SetAudioDeviceCommand ->
            if (actuators.audio.setDefaultDevice(command.id)) OK.also { actuators.onAudioChanged() } else NOT_FOUND
    }

    private fun media(action: MediaAction): Message {
        when (action) {
            MediaAction.PLAY_PAUSE -> actuators.media.playPause()
            MediaAction.NEXT -> actuators.media.next()
            MediaAction.PREVIOUS -> actuators.media.previous()
            MediaAction.VOLUME_UP -> actuators.media.volumeUp()
            MediaAction.VOLUME_DOWN -> actuators.media.volumeDown()
            MediaAction.MUTE_TOGGLE -> actuators.media.muteToggle()
        }
        return OK
    }
}
