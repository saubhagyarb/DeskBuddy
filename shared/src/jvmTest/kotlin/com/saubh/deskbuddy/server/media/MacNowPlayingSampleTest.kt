package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.protocol.PlaybackStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class MacNowPlayingSampleTest {
    @Test
    fun `parse_givenSpotifyLine_readsStateAndExtras`() {
        val line = """{"type":"state","title":"Song","artist":"Band","album":"LP","app":"Spotify","status":"Playing",""" +
            """"positionMs":1500,"durationMs":200000,"canSeek":true,"artworkUrl":"https://i.scdn.co/image/x","trackKey":"Spotify|Song|Band|LP"}"""
        val sample = MacNowPlayingSample.parse(line)!!
        assertEquals("Spotify|Song|Band|LP", sample.trackKey)
        assertEquals("https://i.scdn.co/image/x", sample.artworkUrl)
        val nowPlaying = sample.state.toNowPlaying()
        assertEquals(PlaybackStatus.PLAYING, nowPlaying.status)
        assertEquals("Spotify", nowPlaying.appName)
        assertEquals(200_000L, nowPlaying.durationMs)
    }

    @Test
    fun `parse_givenNoneLine_returnsEmptyExtras`() {
        val sample = MacNowPlayingSample.parse("""{"type":"state","status":"NONE"}""")!!
        assertEquals("", sample.trackKey)
        assertEquals("", sample.artworkUrl)
        assertEquals(PlaybackStatus.NONE, sample.state.toNowPlaying().status)
    }

    @Test
    fun `parse_givenGarbage_returnsNull`() {
        assertNull(MacNowPlayingSample.parse("not json"))
        assertNull(MacNowPlayingSample.parse(""))
    }

    @Test
    fun `artworkId_givenSameKey_isStableAndDistinct`() {
        val id = MacNowPlayingSample.artworkId("Music|A|B|C")
        assertEquals(id, MacNowPlayingSample.artworkId("Music|A|B|C"))
        assertNotEquals(id, MacNowPlayingSample.artworkId("Music|A|B|D"))
        assertEquals(40, id.length) // SHA-1 hex
    }

    @Test
    fun `mimeType_givenMagicBytes_detectsJpegAndPng`() {
        assertEquals("image/jpeg", MacArtworkFetcher.mimeType(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0)))
        assertEquals("image/png", MacArtworkFetcher.mimeType(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())))
        assertNull(MacArtworkFetcher.mimeType(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun `fetch_givenSpotifyUrl_downloadsAndEncodes`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1)
        val fetcher = MacArtworkFetcher(runAppleScript = { _, _ -> error("not for Spotify") }, download = { jpeg })
        assertEquals("image/jpeg", fetcher.fetch("Spotify", "https://i.scdn.co/image/x")?.first)
    }

    @Test
    fun `httpsGet_givenPlainHttp_refuses`() {
        assertNull(MacArtworkFetcher.httpsGet("http://example.com/cover.jpg"))
    }
}
