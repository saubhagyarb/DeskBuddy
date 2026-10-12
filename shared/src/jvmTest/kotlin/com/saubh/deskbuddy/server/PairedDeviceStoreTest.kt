package com.saubh.deskbuddy.server

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PairedDeviceStoreTest {
    private val file = Files.createTempDirectory("deskbuddy-paired").resolve("paired.json")

    @Test
    fun `deviceNameForToken_givenFileDeletedAfterFirstRead_servesFromMemory`() {
        PairedDeviceStore(file).add(PairedDevice("Pixel", "tok"))
        val store = PairedDeviceStore(file)
        assertEquals("Pixel", store.deviceNameForToken("tok"))
        Files.delete(file)
        assertEquals("Pixel", store.deviceNameForToken("tok"))
    }

    @Test
    fun `removeAll_givenCachedDevices_forgetsThem`() {
        val store = PairedDeviceStore(file)
        store.add(PairedDevice("Pixel", "tok"))
        store.removeAll()
        assertNull(store.deviceNameForToken("tok"))
    }

    @Test
    fun `add_givenExistingToken_replacesEntryAndPersists`() {
        val store = PairedDeviceStore(file)
        store.add(PairedDevice("Old", "tok"))
        store.add(PairedDevice("New", "tok"))
        assertEquals(listOf(PairedDevice("New", "tok")), PairedDeviceStore(file).load())
    }
}
