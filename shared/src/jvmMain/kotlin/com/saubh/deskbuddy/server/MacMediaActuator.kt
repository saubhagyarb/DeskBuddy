package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.server.media.MediaRemoteAdapter

/**
 * Play/pause and track skips go through the MediaRemote adapter first, which needs no Accessibility
 * permission. If it is missing or fails they post the hardware media keys (NX system-defined events)
 * through a JXA one-shot, so they reach whichever app owns Now Playing — like the media keys on a Mac
 * keyboard; posting events needs the Accessibility permission. Volume and mute go straight to
 * [audio], which needs no permission.
 */
class MacMediaActuator(
    private val audio: AudioActuator,
    private val setMuted: (Boolean) -> Unit,
    private val runJxa: (String, List<String>) -> String? = { script, args ->
        Osascript.run(script, args, Osascript.Language.JAVASCRIPT)
    },
    /** MediaRemote adapter command; false when the adapter is missing or failed, then the media key is posted instead. */
    private val adapterSend: (Int) -> Boolean = { false },
) : MediaActuator {

    companion object {
        const val NX_KEYTYPE_PLAY = 16
        // macOS keyboards map the Next physical button to FAST (19) and Previous to REWIND (20).
        // 17 and 18 are historically NEXT/PREVIOUS but do not reliably skip tracks.
        const val NX_KEYTYPE_NEXT = 19
        const val NX_KEYTYPE_PREVIOUS = 20
        const val VOLUME_STEP = 6

        /** Key down then key up, each an `NSSystemDefined` (14) event of subtype 8 (aux control button). */
        private const val MEDIA_KEY_SCRIPT = """
ObjC.import('Cocoa');
ObjC.import('CoreGraphics');
function run(argv) {
  var key = parseInt(argv[0], 10);
  [[0xa00, 0xa], [0xb00, 0xb]].forEach(function (phase) {
    var ev = $.NSEvent.otherEventWithTypeLocationModifierFlagsTimestampWindowNumberContextSubtypeData1Data2(
      14, $.NSMakePoint(0, 0), phase[0], 0, 0, $(), 8, (key << 16) | (phase[1] << 8), -1);
    $.CGEventPost(0, ev.CGEvent);
  });
  return 'ok';
}
"""
    }

    override fun playPause() { if (!adapterSend(MediaRemoteAdapter.SEND_TOGGLE)) tap(NX_KEYTYPE_PLAY) }
    override fun next() { if (!adapterSend(MediaRemoteAdapter.SEND_NEXT)) tap(NX_KEYTYPE_NEXT) }
    override fun previous() { if (!adapterSend(MediaRemoteAdapter.SEND_PREVIOUS)) tap(NX_KEYTYPE_PREVIOUS) }
    override fun volumeUp() = audio.setVolume((audio.volume() + VOLUME_STEP).coerceIn(0, 100))
    override fun volumeDown() = audio.setVolume((audio.volume() - VOLUME_STEP).coerceIn(0, 100))
    override fun muteToggle() = setMuted(!audio.isMuted())

    private fun tap(key: Int) {
        checkNotNull(runJxa(MEDIA_KEY_SCRIPT, listOf(key.toString()))) { "Could not post media key $key" }
    }
}
