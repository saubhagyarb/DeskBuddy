package com.saubh.deskbuddy.ui.standby.clock

import kotlin.random.Random

/** A slow random walk for the always-on clock, so no pixel stays lit in one place all night. */
object BurnInWalk {
    private const val MAX_DP = 8
    private const val STEP_DP = 2

    fun next(x: Int, y: Int, random: Random = Random.Default): Pair<Int, Int> {
        fun step(v: Int) = (v + random.nextInt(-STEP_DP, STEP_DP + 1)).coerceIn(-MAX_DP, MAX_DP)
        return step(x) to step(y)
    }
}
