package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.protocol.PlaybackStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaRemoteStreamTest {
    private var now = 10_000L
    private val stream = MediaRemoteStream { now }

    private val full = """{"type":"data","diff":false,"payload":{"bundleIdentifier":"com.google.Chrome","playing":true,"title":"Lo-fi","artist":"Beats","album":"","durationMicros":200000000,"elapsedTimeMicros":5000000,"timestampEpochMicros":9000000}}"""

    @Test
    fun `accept_givenFullPlayingPayload_interpolatesPositionFromTimestamp`() {
        val sample = stream.accept(full)!!
        assertEquals(PlaybackStatus.PLAYING, sample.state.status)
        assertEquals("Lo-fi", sample.state.title)
        assertEquals("Chrome", sample.state.appName)
        assertEquals(200_000L, sample.state.durationMs)
        assertEquals(6_000L, sample.state.positionMs) // 5 s elapsed at t=9 s, now is 10 s
        assertEquals(true, sample.state.canSeek)
        assertEquals("com.google.Chrome|Lo-fi|Beats|", sample.trackKey)
    }

    @Test
    fun `accept_givenDiffWithPausedAndNullAlbum_mergesOntoLastPayload`() {
        stream.accept(full)
        val sample = stream.accept("""{"type":"data","diff":true,"payload":{"playing":false,"album":null,"elapsedTimeMicros":7000000}}""")!!
        assertEquals(PlaybackStatus.PAUSED, sample.state.status)
        assertEquals("Lo-fi", sample.state.title)
        assertEquals(7_000L, sample.state.positionMs) // paused: no interpolation
        assertNull(sample.state.album)
    }

    @Test
    fun `accept_givenNullOrEmptyPayload_returnsNothingPlaying`() {
        stream.accept(full)
        assertEquals(PlaybackStatus.NONE, stream.accept("""{"type":"data","diff":false,"payload":null}""")!!.state.status)
        assertEquals(PlaybackStatus.NONE, stream.accept("""{"type":"data","diff":false,"payload":{}}""")!!.state.status)
    }

    @Test
    fun `accept_givenMalformedOrForeignLine_returnsNull`() {
        assertNull(stream.accept("not json"))
        assertNull(stream.accept("""{"type":"debug","payload":{}}"""))
    }

    @Test
    fun `accept_givenArtwork_exposesItAndDropsOversized`() {
        val withArt = full.replace("\"album\":\"\"", "\"album\":\"\",\"artworkMimeType\":\"image/jpeg\",\"artworkData\":\"/9j/AA==\"")
        val sample = stream.accept(withArt)!!
        assertEquals("image/jpeg", sample.artworkMimeType)
        assertEquals("/9j/AA==", sample.artworkBase64)
        val huge = "A".repeat((MacArtworkFetcher.MAX_BYTES / 3 + 1) * 4)
        assertNull(stream.accept(withArt.replace("/9j/AA==", huge))!!.artworkBase64)
    }

    @Test
    fun `accept_givenPositionPastDuration_clampsToDuration`() {
        now = 9_999_999L
        assertEquals(200_000L, stream.accept(full)!!.state.positionMs)
    }

    @Test
    fun `displayName_givenKnownAndUnknownBundles_mapsReadably`() {
        assertEquals("Safari", MediaRemoteApps.displayName("com.apple.Safari"))
        assertEquals("Spotify", MediaRemoteApps.displayName("com.spotify.client"))
        assertEquals("Vlc", MediaRemoteApps.displayName("org.videolan.vlc"))
    }
}
