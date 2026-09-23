package com.saubh.deskbuddy.pairing

import kotlin.random.Random

object PinGenerator {
    fun generate(random: Random = Random.Default): String =
        buildString { repeat(6) { append(random.nextInt(10)) } }
}
