package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.FileChunkCommand
import com.saubh.deskbuddy.protocol.FileOfferCommand
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.transfer.FileChunkCodec
import com.saubh.deskbuddy.transfer.FileNames
import com.saubh.deskbuddy.transfer.TransferTracker
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

/** Writes incoming file transfers under [directory]; one file per transfer id. */
class FileReceiver(
    val directory: Path = defaultDirectory(),
    private val onFileReceived: (Path) -> Unit = {},
) {
    enum class ChunkResult { OK, FAILED, IGNORED }

    private val tracker = TransferTracker()
    private val streams = HashMap<String, OutputStream>()
    private val paths = HashMap<String, Path>()

    @Synchronized
    fun offer(offer: FileOfferCommand): Boolean {
        if (tracker.offer(offer) !is TransferTracker.Result.Accepted) return false
        return runCatching {
            Files.createDirectories(directory)
            val target = uniquePath(directory, FileNames.sanitize(offer.name))
            streams[offer.transferId] = Files.newOutputStream(target)
            paths[offer.transferId] = target
        }.onFailure { tracker.cancel(offer.transferId); discard(offer.transferId) }.isSuccess
    }

    @Synchronized
    fun chunk(chunk: FileChunkCommand): ChunkResult {
        if (tracker[chunk.transferId] == null) return ChunkResult.IGNORED
        val bytes = runCatching { FileChunkCodec.decode(chunk.data) }.getOrNull()
            ?: return fail(chunk.transferId)
        if (tracker.chunk(chunk.transferId, chunk.index, bytes.size) !is TransferTracker.Result.Accepted) {
            return fail(chunk.transferId)
        }
        return runCatching { streams.getValue(chunk.transferId).write(bytes) }
            .map { ChunkResult.OK }
            .getOrElse { fail(chunk.transferId) }
    }

    /** Returns the saved file, or null when the transfer was incomplete or unknown. */
    @Synchronized
    fun complete(transferId: String): Path? {
        val result = tracker.complete(transferId)
        val path = paths[transferId]
        closeStream(transferId)
        if (result !is TransferTracker.Result.Accepted || path == null) {
            discard(transferId)
            return null
        }
        paths.remove(transferId)
        onFileReceived(path)
        return path
    }

    @Synchronized
    fun cancel(transferId: String) {
        tracker.cancel(transferId)
        discard(transferId)
    }

    private fun fail(transferId: String): ChunkResult {
        tracker.cancel(transferId)
        discard(transferId)
        return ChunkResult.FAILED
    }

    private fun closeStream(transferId: String) {
        runCatching { streams.remove(transferId)?.close() }
    }

    private fun discard(transferId: String) {
        closeStream(transferId)
        paths.remove(transferId)?.let { runCatching { Files.deleteIfExists(it) } }
    }

    companion object {
        fun defaultDirectory(): Path =
            Path.of(System.getProperty("user.home"), "Downloads", Protocol.RECEIVED_FOLDER_NAME)

        /** "a.txt" becomes "a (1).txt", "a (2).txt", and so on until the name is free. */
        fun uniquePath(directory: Path, name: String): Path {
            var candidate = directory.resolve(name)
            if (!Files.exists(candidate)) return candidate
            val (base, ext) = FileNames.splitExtension(name)
            var n = 1
            while (Files.exists(candidate)) {
                candidate = directory.resolve("$base ($n)$ext")
                n++
            }
            return candidate
        }
    }
}
