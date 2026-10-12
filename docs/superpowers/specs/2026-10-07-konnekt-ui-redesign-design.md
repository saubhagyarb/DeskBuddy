# KonneKt — Android UI Redesign, Rename, Window/Tab Close, Keyboard Fixes

Date: 2026-10-07 · Branch: `feature/m4-keyboard-performance`

## 1. Feature inventory (what the app does today)

| # | Area | Feature | Where it lived before |
|---|------|---------|-----------------------|
| F1 | Connect | mDNS discovery + subnet sweep of PCs on the LAN | Connect screen |
| F2 | Connect | Saved PCs, auto-reconnect in background, forget PC | Connect screen (big card) |
| F3 | Connect | Manual IP connect | Connect screen (card at the bottom) |
| F4 | Connect | 6-digit PIN pairing | Dialog |
| F5 | Media | Now playing (artwork, title, artist, source app) | Media tab |
| F6 | Media | Seek timeline, previous / play-pause / next | Media tab |
| F7 | Media | Volume slider, mute, output-device picker | Media tab |
| F8 | Apps | Pinned shortcuts with PC icons, launch | Apps tab |
| F9 | Apps | All installed apps, search, pin/unpin, refresh | Apps tab |
| F10 | Share | Clipboard auto-sync toggle, send / get clipboard | Share tab |
| F11 | Share | Send text, send file (progress, cancel) | Share tab |
| F12 | Share | Inbox of text/files received from the PC | Share tab |
| F13 | Keyboard | Phone → PC live typing, special keys, sticky modifiers, combos | Keyboard tab |
| F14 | Keyboard | PC → phone typing via the system keyboard (IME) + setup | Keyboard tab (setup card) |
| F15 | Power | Lock / restart / shut down the PC | **Only inside Standby** |
| F16 | Standby | Black always-on dashboard (clock, shortcuts, media), auto when docked landscape + charging | FAB on every tab |
| F17 | System | Foreground service, media notification, boot reconnect, share-sheet target | — |
| **F18** | **Apps (new)** | **See open windows on the PC grouped by app; close a window, a whole app, or a single browser tab** | — |

## 2. Why the current UI is confusing

1. **The disconnect button uses the power icon** — it reads as "shut down PC". Real power controls are hidden in Standby.
2. **The Standby FAB floats over every tab**, covering content (hence the `FabClearance` workaround) and competing with each tab's real primary action.
3. **No home / status surface.** After connecting you land on Media even when nothing plays; PC status, power and quick actions have no place.
4. **Keyboard tab mixes two directions** (phone → PC typing and PC → phone IME setup) without saying which is which.
5. **Share tab is three equal-weight cards + inbox**; clipboard (the most frequent action) has the same weight as the file picker.
6. Connect screen leads with a huge saved-PC card even while it is auto-connecting, and manual IP is buried under the discovery list.

## 3. New information architecture

```
Connect flow (not connected)
 └─ Devices screen
     ├─ Hero: "Connect to your PC" + live status (searching / connecting)
     ├─ Your PCs (saved, one-tap, overflow → Forget)
     ├─ Nearby (discovered)
     └─ "Enter IP address" → bottom sheet
     PIN dialog when pairing

Connected shell (NavigationSuiteScaffold: bar on phones, rail on wide windows)
 ├─ Home      PC status card (name, link state) · Power row (Lock / Restart / Shut down)
 │            · Now-playing mini player (tap → Media) · Quick actions (Send clipboard,
 │            Get clipboard, Send file, Standby) · Pinned shortcuts strip
 ├─ Media     Full player: artwork, timeline, transport, volume + output device
 ├─ Apps      Segmented: [Open windows] [Launch]
 │            Open windows: app cards → windows → (browser) tabs, each with ✕;
 │            "Close app" closes all its windows. Destructive → confirmation dialog.
 │            Launch: pinned grid + searchable all-apps list with pin toggle.
 ├─ Share     Clipboard hero (send / get / auto-sync) · Send text · Send file · Inbox
 └─ Keyboard  "Type on PC" (field, special keys, modifiers, combos)
              · "Type on phone from PC" setup / status (IME) — clearly separated
 Top app bar: PC avatar + name + status · actions: Standby, overflow (Switch PC, Disconnect)
 Standby: full-screen black dashboard (unchanged behaviour, restyled), entered from the app bar.
```

## 4. Visual language

- Name: **KonneKt** (user-visible everywhere: launcher label, IME, notifications, desktop window/tray). Package
  names, application id, mDNS service type and wire protocol are unchanged so existing pairings keep working.
- Palette: teal primary (link / connection), deep slate secondary, warm amber tertiary for attention
  (destructive confirmation, "PC keyboard active"). Light + dark schemes, all colors as `ColorScheme` roles.
- M3 Expressive kept (motion scheme, shape morphing buttons, emphasized type). Cards use tonal surface
  containers, not shadows. 8 dp grid, ≥ 48 dp targets, every icon-only control has a content description,
  section titles are semantic headings.

## 5. Architecture

- Per-screen ViewModels exposing one `StateFlow<…UiState>` each and intent functions (UDF), created by a
  single `viewModelFactory` that pulls dependencies from `AppGraph` (the project's existing manual DI; no
  Hilt migration in this change).
- Screens are split into a stateful `XRoute(viewModel)` and a stateless `XScreen(state, callbacks)`.
- Feature controllers (`MediaController`, `AppShortcutsController`, new `RunningAppsController`, …) stay the
  data layer; the UI never calls them directly any more.

## 6. Window / tab close (F18) — protocol

Phone → PC: `running_apps_get`, `window_close(windowId)`, `app_close(appId)`, `tab_close(windowId, index, title)`.
PC → phone: `Push(running_apps(apps))` — `RunningApp(id, name, windows[AppWindow(id, title, tabs[BrowserTab(index, title)])])`.

Desktop (Windows): windows enumerated with `EnumWindows` (visible, titled, un-owned, not tool windows, not
cloaked, not KonneKt itself); closing posts `WM_CLOSE` (graceful — apps may still ask to save). Browser tabs
(Chrome, Edge, Brave, Vivaldi, Opera, Firefox) are read and closed through UI Automation in a PowerShell
helper; closing invokes the tab's own close button and falls back to select + Ctrl+W. Listing runs off the
socket thread and is pushed when ready. Window ids are only honoured if they are currently listed. Other OSes
report `UNSUPPORTED_OS`.

## 7. Out of scope

Desktop (Compose for Desktop) screen redesign beyond the rename; Hilt/Room migration; force-kill of apps.
