package com.saubh.deskbuddy.keyboard

/**
 * Turns successive values of the phone's typing field into what the PC must do, assuming the PC's
 * caret sits at the end: delete [Result.backspaces] characters, then type [Result.inserted].
 * The field always starts with [SENTINEL] so Backspace on an "empty" field is still an edit.
 */
object TextDiff {
    const val SENTINEL = "​"

    data class Result(val backspaces: Int, val inserted: String)

    fun between(old: String, new: String): Result {
        var prefix = 0
        val max = minOf(old.length, new.length)
        while (prefix < max && old[prefix] == new[prefix]) prefix++
        // Never split a surrogate pair: back up to its start.
        if (prefix > 0 && Character.isHighSurrogate(old[prefix - 1])) prefix--
        // One PC Backspace deletes one code point (an emoji is one Backspace, not two).
        val removed = old.codePointCount(prefix, old.length)
        val inserted = new.substring(prefix).replace(SENTINEL, "")
        // The sentinel exists only on the phone. Losing it is a PC Backspace only for a pure deletion
        // (Backspace on the "empty" field); when text replaces it (select-all + type, typing before it,
        // a keyboard suggestion swallowing it) it must not erase an extra character on the PC.
        val sentinelReplaced = old.startsWith(SENTINEL) && !new.startsWith(SENTINEL) && inserted.isNotEmpty()
        return Result(if (sentinelReplaced) removed - 1 else removed, inserted)
    }

    /** The value to show after an edit: the sentinel always leads. */
    fun display(new: String): String = if (new.startsWith(SENTINEL)) new else SENTINEL + new
}
