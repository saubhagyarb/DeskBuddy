package com.saubh.deskbuddy.ui.standby.clock

/** 5×7 LED glyphs for digits and a 1-wide colon; columns are joined with one blank column. */
object DotMatrixFont {
    private val GLYPHS = mapOf(
        '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to listOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
        '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
        ':' to listOf("0", "0", "1", "0", "1", "0", "0"),
    )
    private val BLANK = BooleanArray(7)

    fun columns(text: String): List<BooleanArray> = text.mapNotNull(GLYPHS::get).flatMapIndexed { index, rows ->
        val glyph = (0 until rows[0].length).map { c -> BooleanArray(7) { r -> rows[r][c] == '1' } }
        if (index == 0) glyph else listOf(BLANK) + glyph
    }
}
