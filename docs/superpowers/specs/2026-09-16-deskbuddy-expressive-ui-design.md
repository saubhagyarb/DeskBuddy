# DeskBuddy — UI Redesign: Material 3 Expressive

**Date:** 2026-09-16
**Status:** Implemented
**Source:** user request to redesign both apps per https://m3.material.io/blog/building-with-m3-expressive
**Reference used:** Google Design research article on M3 Expressive, Compose Material3 release notes,
and the AndroidX Material3 token sources (the m3.material.io pages are client-rendered and returned no text).

---

## 1. Principles applied

M3 Expressive's five levers are color, shape, size, motion and containment, used to make the
key action on each screen stand out and to group related controls. DeskBuddy applies them as:

| Lever | Decision |
|---|---|
| Color | One shared brand tonal palette (indigo primary, lavender secondary, coral tertiary). Surface-container tiers (`Low` for quiet sections, `High`/`Highest` for the card that matters) instead of elevation shadows. `primaryContainer` for the single hero card per screen. |
| Shape | Extended shape scale from `Shapes()`: `extraLarge` (28dp) for hero and section cards, `largeIncreased` (20dp) for shortcut tiles, `large` (16dp) for rows, pills for buttons, search fields and chips. `MaterialShapes.Cookie9Sided` badges for the PC / app identity. Buttons and icon buttons morph to a squarer shape on press (`ButtonDefaults.shapes()`, `IconButtonDefaults.shapes()`); the mute toggle morphs round → rounded square when checked. |
| Size | Play/pause is a Large (96dp) filled icon button; prev/next/volume are Medium (56dp) tonal. Card primary actions are Medium (56dp) buttons with icons. Small buttons only for low-priority text actions. |
| Motion | `MotionScheme.expressive()` via `MaterialExpressiveTheme`, so navigation indicators, toggles and the app bar collapse use the softer spatial springs. The morphing `LoadingIndicator` replaces spinners for discovery, waiting and pairing; `ContainedLoadingIndicator(progress)` shows file-transfer progress. |
| Containment | Every feature lives in a rounded card with a 40dp icon avatar header and one filled action. Sections are titled in `titleMediumEmphasized`. |

Text labels are kept on every action (the research found removing them hurt usability).

## 2. Shared design system (`shared/commonMain/ui`)

- `theme/DeskBuddyTheme` — `MaterialExpressiveTheme(colorScheme, MotionScheme.expressive(), Shapes(), Typography())`, light/dark from the system.
- `theme/DeskBuddyColors` — hand-built tonal palette; light `on*Container` roles use tone 30 as `expressiveLightColorScheme` does.
- `theme/DeskBuddyIcons` — 34 Material Symbols as `ImageVector` path data. The pinned Compose build ships only the tiny core icon set, and `material-icons-extended` is too large without R8.
- `components/Components` — `SectionTitle`, `IconAvatar` / `LetterAvatar` on any shape, `MediumButton` / `MediumTonalButton` / `MediumOutlinedButton`.

## 3. Android

- **Connect:** `LargeFlexibleTopAppBar` (title + subtitle, collapses on scroll). Saved PC as a `primaryContainer` hero card with cookie badge and a Medium "Reconnect" button. Discovered PCs as `surfaceContainerHigh` rows with a Wi-Fi avatar. Manual address in a `surfaceContainerLow` card. `LoadingIndicator` in the section header while discovering; `ContainedLoadingIndicator` while connecting.
- **PIN dialog:** six 40x52dp cells fed by an invisible text field, `headlineMediumEmphasized` digits, active cell outlined in primary, "Pair" as a morphing filled button.
- **Remote shell:** `TopAppBar` with the PC name in `titleLargeEmphasized`, status subtitle in primary, cookie badge as navigation icon, tonal power button to disconnect. Portrait: `ShortNavigationBar`; landscape: `WideNavigationRail` (collapsed).
- **Media:** transport card (`surfaceContainerHigh`) with Large play/pause and Medium prev/next; volume card (`surfaceContainerLow`) with Medium tonal down/up and a shape-morphing mute toggle. Landscape puts the cards side by side.
- **Apps:** shortcut tiles (116dp, `largeIncreased`, cookie letter avatar), pill search field, rows with star `IconToggleButton` using `toggleableShapes()`. Empty states use the loading indicator until the catalog arrives.
- **Share:** clipboard card (`surfaceContainerHigh`, switch + tonal/outlined Medium buttons), text card, file card (Medium filled "Send a file" or progress with `ContainedLoadingIndicator`), inbox rows with secondary/tertiary avatars.

## 4. Desktop

- `WideNavigationRail` with the cookie logo header and Status / Share / Shortcuts destinations; each pane has a `headlineLargeEmphasized` title and a status subtitle.
- **Status:** hero card per state. Waiting: 72dp `LoadingIndicator`. Pairing: `primaryContainer` card, PIN in `displayLargeEmphasized` grouped as `123  456`, `LinearProgressIndicator` countdown. Connected: 80dp cookie phone badge, device name in `headlineMediumEmphasized`, outlined "Unpair all". Hostname and address as `AssistChip`s.
- **Share:** two columns, send cards on the left (text, file with progress, clipboard auto-sync switch), inbox on the right with "Open folder".
- **Shortcuts:** Medium "Add installed app" and tonal "Browse for program", adaptive grid of shortcut cards with cookie avatars and a remove icon; picker dialog with pill search and letter avatars.
- `desktopApp` now takes Material3 from the version catalog; the Compose plugin's bundled 1.9 hid the expressive APIs.

## 5. Not done / assumptions

- No app icons (letter avatars); icon extraction is a later milestone.
- The Android UI was verified by compile and lint only: no device or emulator exists on this machine. Desktop panes were screenshotted in dark mode.
- Desktop strings stay inline (no resource system on desktop, as in M1).
