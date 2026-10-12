package com.saubh.deskbuddy.server.media

/** Bundle identifiers → the name the phone shows under the track. */
object MediaRemoteApps {
    private val KNOWN = mapOf(
        "com.apple.Safari" to "Safari",
        "com.google.Chrome" to "Chrome",
        "com.microsoft.edgemac" to "Edge",
        "company.thebrowser.Browser" to "Arc",
        "org.mozilla.firefox" to "Firefox",
        "com.brave.Browser" to "Brave",
        "com.spotify.client" to "Spotify",
        "com.apple.Music" to "Music",
        "com.apple.podcasts" to "Podcasts",
        "com.apple.TV" to "TV",
    )

    fun displayName(bundleId: String): String =
        KNOWN[bundleId] ?: bundleId.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
