package com.saubh.deskbuddy.service

/** Pure rules for "the phone is docked": testable without sensors or broadcasts. */
object DockPolicy {
    private const val TOLERANCE_DEGREES = 30

    /**
     * [degrees] from `OrientationEventListener`: landscape is 90 or 270, ±30°. Null for no reading
     * (negative: the phone lies flat), which must leave the current state alone.
     */
    fun isLandscape(degrees: Int): Boolean? {
        if (degrees < 0) return null
        return degrees in (90 - TOLERANCE_DEGREES)..(90 + TOLERANCE_DEGREES) ||
            degrees in (270 - TOLERANCE_DEGREES)..(270 + TOLERANCE_DEGREES)
    }

    /** Standby while charging in landscape, unless the PC is typing into the phone. A PC is optional: without one, standby is a clock. */
    fun shouldStandby(charging: Boolean, landscape: Boolean, suppressed: Boolean): Boolean =
        charging && landscape && !suppressed
}
