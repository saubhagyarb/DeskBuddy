package com.saubh.deskbuddy.share

sealed class InboxItem {
    abstract val receivedAt: Long

    data class Text(val text: String, override val receivedAt: Long) : InboxItem()

    data class File(
        val name: String,
        val sizeBytes: Long,
        val location: String,
        override val receivedAt: Long,
    ) : InboxItem()
}

data class TransferProgress(
    val name: String,
    val doneBytes: Long,
    val totalBytes: Long,
    val outgoing: Boolean,
) {
    val fraction: Float get() = if (totalBytes > 0) doneBytes.toFloat() / totalBytes else 0f
}
