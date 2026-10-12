# mediaremote-adapter (macOS only)

System-wide "now playing" on macOS 15.4+ (Safari, Chrome, YouTube, Spotify, Music…).
Source: https://github.com/ungive/mediaremote-adapter, BSD-3-Clause (see LICENSE-mediaremote-adapter).

Build on a Mac (universal x86_64 + arm64) and copy the outputs here:

```bash
git clone https://github.com/ungive/mediaremote-adapter.git /tmp/mra
cd /tmp/mra && git checkout <pinned commit> && mkdir build && cd build && cmake .. && cmake --build .
cp -R /tmp/mra/build/MediaRemoteAdapter.framework <repo>/desktopApp/resources/macos/
cp /tmp/mra/bin/mediaremote-adapter.pl <repo>/desktopApp/resources/macos/
cp /tmp/mra/LICENSE <repo>/desktopApp/resources/macos/LICENSE-mediaremote-adapter
```

Check it: `/usr/bin/perl mediaremote-adapter.pl "$PWD/MediaRemoteAdapter.framework" test; echo $?` prints `0`.
Without these files KonneKt still runs and falls back to Music/Spotify-only now playing.
