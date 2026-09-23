# PC Remote Control App — Requirements & Implementation Plan

## 1. Overview

An Android app that connects to a desktop (Windows or Linux) over the same WiFi
network or a USB cable, and sends it remote-control commands (media, volume,
power, app launching, and input control).

- **Mobile client:** Android only
- **Desktop companion:** Windows and Linux (no macOS)
- **Tech stack:** Kotlin Multiplatform (KMP) + Compose Multiplatform
- **Orientation:** Both portrait and landscape supported with distinct layouts

The core idea: build one shared Kotlin module for the command protocol,
networking, and pairing logic, and reuse it across the Android client and the
desktop server, rather than writing the protocol twice in two languages.

---

## 2. Feature Scope

### 2.1 Media & Volume
- Play / pause
- Next / previous track
- Volume up / down / mute
- Currently-playing track display (song/artist/artwork from the desktop's media session) — Milestone 3
- Seekable timeline, absolute volume slider, output-device switching — Milestone 3

### 2.2 System / Power
- Shutdown
- Restart
- Sleep
- Lock screen
- Log off

### 2.3 App & Window Control
- Launch a specific app (from a synced list of installed programs/shortcuts)
- Close/kill a running app
- Switch between open windows
- Minimize / maximize the active window

### 2.4 Input Control
- Virtual trackpad (touch-drag to move cursor, tap to click)
- Virtual keyboard (type directly into the focused window on the desktop)
- Dedicated media-key row
- Custom key combos (Alt+Tab, Ctrl+Alt+Del, Win+D, etc.)

### 2.5 Presentation Mode
- Slide advance / back with a large touch area, for controlling
  PowerPoint/Keynote/Google Slides from across a room

### 2.6 Sharing (Milestone 2)
- Clipboard sync: desktop clipboard changes are pushed to the phone; the phone
  pushes its clipboard on demand or when it changes while the app is open, and
  can pull the desktop clipboard.
- Text sharing in both directions with an inbox on each side.
- File sharing in both directions over the same socket (chunked); files land in
  `Downloads/DeskBuddy` on both platforms. Android share sheet can send text
  and files to the PC.

### 2.7 App Shortcuts (Milestone 2)
- The desktop app lists installed programs (Windows Start Menu shortcuts,
  Linux `.desktop` entries) and lets the user pick any of them, or browse to
  any file on disk, as a shortcut.
- The phone shows the shortcuts as a one-tap grid and can also browse the full
  installed list, launch from it, and pin/unpin shortcuts.
- The desktop only launches ids that appear in its own catalog.

### 2.8 Now Playing, Audio Output & Standby (Milestone 3)
- Desktop pushes the current track (title, artist, album, source app, artwork, playback
  status, position, duration) and the phone shows it with a seekable timeline.
- Phone lists the PC's audio output devices, marks the default, and can switch it;
  master volume is set with a slider (absolute), mute state mirrors the PC.
- Shortcut tiles show the PC's app icons (Windows shell icons, 64 px).
- Standby mode: black always-on screen with two pages (shortcut grid, media controls),
  entered from a FAB, exited with back or a close button.
- Discovery: desktop advertises on every LAN adapter and serves `GET /info`; the phone
  serialises and retries mDNS resolves and sweeps its own /24 when mDNS stays silent.

### 2.9 Explicitly Out of Scope (for now)
- Screen mirroring / screenshot preview
- App icons on the phone (letter avatars for now)
- Multi-PC profiles (single paired desktop only, initially)

---

## 3. Architecture

```
/composeApp        → Android UI (trackpad, media controls, app launcher grid)
/desktopApp        → Windows/Linux UI (pairing screen, connection status, settings)
/shared
  /commonMain       → command models, socket protocol, serialization, pairing/auth
  /androidMain      → Android-specific networking (NSD discovery, permissions)
  /desktopMain      → desktop actuator implementations (Robot-based + OS branching)
```

Notes:
- KMP does not provide separate `windowsMain` / `linuxMain` source sets —
  desktop is a single JVM target. Windows vs Linux behavior is branched at
  **runtime** using `System.getProperty("os.name")` inside `desktopMain`, not
  via compile-time source sets.
- `java.awt.Robot` (part of the JDK) covers mouse move/click and keyboard
  simulation cross-platform, so trackpad, virtual keyboard, and key-combo
  features can share **one implementation** across Windows and Linux. Only
  power actions, app enumeration, and window management need real
  `expect`/`actual` (or runtime) branching.

### 3.1 Platform Implementation Reference

| Action | Windows | Linux |
|---|---|---|
| Volume/media keys | Win32 `SendInput` via JNA | `playerctl` (media), `pactl`/`amixer` (volume) via `ProcessBuilder` |
| Now playing / seek | Windows system media session via a bundled PowerShell helper (JSON lines over stdio) | `playerctl metadata` (not yet implemented) |
| Output devices / master volume | Core Audio (`IMMDeviceEnumerator`, `IAudioEndpointVolume`, `IPolicyConfig`) via JNA COM | `pactl` (not yet implemented) |
| App launching | `ProcessBuilder`, read Start Menu shortcuts | `ProcessBuilder`, `.desktop` file lookup via `xdg` |
| Window control | `Robot` for key combos; Win32 API via JNA for direct control | `wmctrl` / `xdotool` (X11); `dbus` calls (Wayland) |
| Power actions | `shutdown.exe` via `ProcessBuilder` | `systemctl poweroff/reboot`, or `shutdown` via `ProcessBuilder` |
| Trackpad/mouse | `Robot.mouseMove` / `mousePress` | `Robot`, or `xdotool` as fallback |

---

## 4. Connection Layer

### 4.1 WiFi (primary path)
- Desktop app opens a TCP or WebSocket server on a fixed port.
- Android discovers it via mDNS/NSD (Network Service Discovery) or manual IP entry.
- Commands sent as JSON over the socket, serialized with `kotlinx.serialization`.

### 4.2 USB Cable
- Use `adb forward` / `adb reverse` to tunnel the same TCP socket over USB.
- Reuses the exact same protocol/connection code as the WiFi path — no
  separate implementation needed.
- Avoid building a second protocol via Android USB accessory/host mode unless
  a specific need arises.

### 4.3 Pairing / Security
- PIN-based pairing on first connection so arbitrary devices on the network
  can't send commands.
- Store paired desktop identity (e.g., a token or key) on the Android side
  for reconnection without re-pairing.

---

## 5. UI / Orientation

Treat portrait and landscape as **two distinct layouts**, not one reflowed
layout — this is a control surface, not a content app.

- **Portrait:** vertical stack — media controls top, app launcher grid middle,
  power/system controls bottom. Good for one-handed quick actions.
- **Landscape:** trackpad + keyboard-focused layout, or a dashboard split
  between media controls and a larger touch surface (landscape gives the
  width needed for a usable trackpad).

Implement via `WindowSizeClass` / orientation checks in Compose, switching
between two `@Composable` layout functions.

---

## 6. Implementation Steps

1. **Project setup**
   - Scaffold a KMP project with Android + Desktop (JVM) targets using
     Compose Multiplatform.
   - Set up `shared` module with `commonMain`, `androidMain`, `desktopMain`
     source sets.

2. **Define the shared protocol** (`commonMain`)
   - Command data classes (e.g., `VolumeCommand`, `PowerCommand`,
     `LaunchAppCommand`, `TrackpadEvent`, `KeyComboCommand`).
   - Serialization setup with `kotlinx.serialization`.
   - Message envelope format (command type + payload + auth token).

3. **Build the connection layer** (`commonMain` + platform actuals)
   - Socket client (Android side) and socket server (desktop side).
   - Pairing/PIN handshake flow.
   - Connection state management (connected/disconnected/reconnecting).

4. **Implement desktop actuators** (`desktopMain`)
   - Runtime OS detection (`System.getProperty("os.name")`).
   - Implement each command handler per the platform table in Section 3.1.

5. **Build the Android UI** (`composeApp`)
   - Portrait and landscape layouts per Section 5.
   - Media/volume controls, power controls, app launcher list, trackpad
     surface, virtual keyboard, key-combo shortcuts, presentation mode screen.

6. **Build the desktop UI** (`desktopApp`)
   - Pairing screen (show PIN, confirm incoming pairing request).
   - Connection status / settings screen.
   - System tray integration so the desktop app runs in the background.

7. **Wire up USB path**
   - Add `adb forward` tooling/instructions or in-app helper for the USB
     connection mode, reusing the existing socket protocol.

8. **Testing**
   - Test each command handler on both Windows and Linux.
   - Test WiFi and USB connection paths independently.
   - Test orientation switching mid-session.

9. **Packaging**
   - Package desktop app as `.msi`/`.exe` (Windows) and `.deb`/AppImage
     (Linux) via Compose Desktop's packaging tools.
   - Package Android app as a standard `.apk`/`.aab`.
