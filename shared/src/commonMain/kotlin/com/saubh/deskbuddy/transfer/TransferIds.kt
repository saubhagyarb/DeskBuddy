package com.saubh.deskbuddy.transfer

import kotlin.random.Random

object TransferIds {
    fun next(random: Random = Random.Default): String =
        random.nextLong().toULong().toString(16).padStart(16, '0')
}
