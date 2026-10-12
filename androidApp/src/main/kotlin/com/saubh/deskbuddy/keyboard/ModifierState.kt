package com.saubh.deskbuddy.keyboard

import com.saubh.deskbuddy.protocol.KeyModifier

/** Sticky modifiers: first tap = next key only, second tap = locked, third tap = off. */
data class ModifierState(
    val oneShot: Set<KeyModifier> = emptySet(),
    val locked: Set<KeyModifier> = emptySet(),
) {
    val active: Set<KeyModifier> get() = oneShot + locked

    fun tap(modifier: KeyModifier): ModifierState = when (modifier) {
        in locked -> copy(locked = locked - modifier)
        in oneShot -> copy(oneShot = oneShot - modifier, locked = locked + modifier)
        else -> copy(oneShot = oneShot + modifier)
    }

    /** After a key was sent: one-shot modifiers are used up, locked ones stay. */
    fun consumed(): ModifierState = copy(oneShot = emptySet())
}
