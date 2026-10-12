# DeskBuddy Changelog

## [2026-10-12] — Standby: true full screen, floating chrome, tap-to-toggle

**Type:** Fix
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/KeepScreenOn.kt` — hides status + navigation bars (transient by swipe, light icons) and re-hides on configuration change, ON_RESUME and window-focus regain; short-edge cutout mode while composed (kept as ALWAYS when enableEdgeToEdge already set it); everything restored on dispose
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyRoute.kt` — safeDrawing column/row layout replaced by the floating overlay: full-screen pages, rail CenterStart (landscape) or TopStart (portrait), ⏻/✕ TopEnd, control bar BottomCenter; one saveable `chromeShown` toggled by blank-area taps, auto-hidden once after 4 s if untouched, pinned while the power menu is open; `chromeAlwaysVisible` for previews; key presses wake the chrome
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyChrome.kt` — dim/alpha chrome and ControlBarSlot removed; `ChromeItem` (AnimatedVisibility, shared 220 ms / 160 ms strong-ease-out enter/exit, fades only under reduced motion), `Modifier.blankTaps`, chrome swallows its own taps
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyPages.kt` — fixed `contentPadding` for PC pages; clock page full-bleed
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/ClockMode.kt` — burn-in offset moved inside each clipped page; face dots fade with the chrome
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/BubbleMorphFace.kt` — gradient overdraws by 12dp so the burn-in shift never shows an edge band
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/DotMatrixFace.kt` — 40dp vertical clearance; matrix shrinks (centred) before the date/battery can reach the dots
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/OutputDeviceChips.kt`, `MediaAppsMode.kt` — device labels ellipsize
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/RepeatingPress.kt` — consumes its down and lift so volume steps never count as blank taps
- `androidApp/src/screenshotTest/kotlin/com/saubh/deskbuddy/StandbyScreenshots.kt` — previews force the chrome on; new `StandbyClockBubbleLandscape` (868×411); references regenerated

**Summary:**
Device testing rejected the inset-padded standby: the status bar showed in landscape, the Bubble face was boxed by black bands, DotMatrix battery text hit the page dots and the device chip clipped. Standby is now truly full screen with the earlier floating layout. A tap on any blank area shows or hides the rail, ⏻/✕ and control bar together, with matching slide-and-fade motion. PC pages keep fixed padding, so toggling the chrome never reflows them.

**Build Verified:** [x] Yes — testDebugUnitTest, assembleDebug, lintDebug, validateDebugScreenshotTest passed

## [2026-10-12] — Standby review follow-ups: bar icons on resize, narrow top bar, focus wake

**Type:** Fix
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/KeepScreenOn.kt` — one remembered insets controller; light bar icons re-asserted on every configuration change (`enableEdgeToEdge` resets them on multi-window / resize without recreation); save and restore unchanged
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/ModeRail.kt` — `itemSize` parameter (default 56dp)
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyRoute.kt` — portrait top bar uses BoxWithConstraints: under 380dp it uses 48dp rail items and 4dp ⏻/✕ spacing; `PageEntry` remembered on (entryPage, reducedMotion); chrome wake wired to the touch counter
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyChrome.kt` — `Modifier.chrome(state, onWake)` wakes the chrome when focus lands inside it; `StandbyTopActions` takes a `spacing`
- `androidApp/src/screenshotTest/kotlin/com/saubh/deskbuddy/StandbyScreenshots.kt` — `StandbyMediaAppsNarrowPortrait` (360×780) plus its reference

**Summary:**
Fixes from the code review. Bar icons stay light after an in-place resize. The portrait top bar no longer crushes ✕ on 360dp phones or with a larger display size. Keyboard or D-pad focus wakes hidden chrome. The page-entry holder is no longer reallocated on every recomposition. Existing screenshot references are unchanged.

**Build Verified:** [x] Yes — testDebugUnitTest, assembleDebug, lintDebug, validateDebugScreenshotTest passed

## [2026-10-12] — Standby: compact app keys on short grids, hidden chrome ignores taps

**Type:** Fix
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/AppKey.kt` — AppGrid reads its height; below two regular rows (244dp) it switches to compact keys (40dp icon, 12dp vertical padding, labelMedium, `GridCells.Adaptive(88.dp)`), still at least 96dp tall
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyChrome.kt` — `rememberChromeState` / `ChromeState` / `Modifier.chrome`: while chrome is fading to or at alpha 0, each gesture on it is consumed on the Initial pass, so invisible ⏻ / ✕ / rail / control bar never fire
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyRoute.kt` — uses `Modifier.chrome(chrome)`
- `androidApp/src/screenshotTestDebug/reference/com/saubh/deskbuddy/StandbyScreenshotsKt/StandbyMediaAppsShortLandscape_153afb69_0.png` — regenerated

**Summary:**
In the 800×360dp landscape, the second row of app keys was cut off at the grid edge. Short grids now use compact 96dp keys, so two whole rows fit. Portrait and regular landscape are pixel-identical. On the clock page the fully faded chrome no longer reacts to taps: the first tap only wakes it, because the screen's touch counter sees the tap before the chrome swallows it. Dimmed chrome (0.35) stays tappable.

**Build Verified:** [x] Yes — testDebugUnitTest, assembleDebug, lintDebug, validateDebugScreenshotTest passed

## [2026-10-12] — Standby UI: legible text, edge-to-edge, real layout without overlap

**Type:** Fix
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyChrome.kt` — new: `StandbySurface` (dark Surface with onSurface content colour), `rememberChromeAlpha` (dim to 0.35 after 3 s, 0 on the clock page), `StandbyTopActions` (power + close), `ControlBarSlot` (fixed-height slot, bar fades/slides inside it)
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyPages.kt` — new: the VerticalPager with 16dp page padding and the 0.97→1 page-entry fade/scale (fade only with reduced motion)
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyRoute.kt` — Surface instead of a background Box; portrait Column(top bar, pager, control bar) and landscape Row(rail, pager + control bar, ⏻/✕ column) inside `WindowInsets.safeDrawing`; overlay paddings removed; chrome dims instead of hiding
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/KeepScreenOn.kt` — forces light status/navigation bar icons and restores them on dispose
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/ModeRail.kt` — indicator offset read in the placement lambda
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/MediaAppsMode.kt` — portrait: compact now-playing (72dp) + device chip, then the key grid; short landscape tightens the media column
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/MultimediaMode.kt` — portrait artwork = 30 % of page height (96–220dp) in a CenteredScrollColumn
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/AppKey.kt`, `DevelopmentMode.kt` — keys sized to content (min 96dp tall); 104dp columns in portrait, 128dp in landscape
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyNowPlaying.kt`, `VolumeStepper.kt`, `DisconnectedCard.kt` — explicit onSurface text colours; centred transport; configurable compact gap
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/FlipClockFace.kt` — AM/PM centred 12dp under the cards
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/ClockTicker.kt` — `LocalClockTimeOverride` so screenshot tests can pin the time
- `androidApp/src/screenshotTest/kotlin/com/saubh/deskbuddy/StandbyScreenshots.kt` — harness uses `StandbySurface`; new portrait (411×868) Clock, Media + Apps, Multimedia and Development, and short landscape (800×360) Multimedia and Media + Apps previews; references regenerated

**Summary:**
On a real phone standby text rendered black on black, because the root was a Box rather than a Surface. The status bar icons were dark on dark, chrome sat under the status bar and cutout, and the portrait rail overlapped the control bar. Standby now uses a dark Surface and forces light bar icons. Chrome and pages live inside the safe-drawing insets in a real Column/Row layout instead of guessed overlay paddings. Chrome dims instead of disappearing, and the portrait pages are sized to fit a phone.

**Build Verified:** [x] Yes — testDebugUnitTest, assembleDebug, lintDebug, validateDebugScreenshotTest passed

## [2026-10-12] — M5 final review fixes: token-free decode log, undock/lock-screen, standby power, gated refresh

**Type:** Fix
**Files Changed:**
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ControlServer.kt:109` — undecodable frames log only the exception type and frame length via `undecodableFrameLog` (the decode message echoed the JSON, including the token)
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/openapps/WindowsWindowActuator.kt:20` — `focusApp` reports focused once a window matches, even if Windows refuses the foreground, so the phone never launches a duplicate
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/MainActivity.kt` — leaving standby clears `openedForAutoStandby` and show-when-locked (deferred while a countdown runs); undock never finishes during a countdown; flags survive recreation
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/KonnektApp.kt` — `onStandbyVisibleChange` callback; countdown dialog is dark over standby
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyPowerButton.kt` — new: power button with a Restart / Shut down menu
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyRoute.kt` — power button beside close; chrome stays up while the menu is open
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyActions.kt`, `StandbyViewModel.kt` — `onPower(action)` starts the countdown named after the current PC
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/DevelopmentMode.kt` — running-apps refresh runs only while RESUMED
- `androidApp/src/main/res/values/strings.xml` — `cd_standby_power`
- Tests: `ControlServerLogTest`, `WindowsWindowActuatorTest.focusApp_givenFocusRefused_stillReportsFocused`, `StandbyViewModelTest.onPower_givenRestart_startsCountdown`; standby screenshot references regenerated

**Summary:**
Closes the M5 whole-branch review. The server log no longer leaks the pairing token. Leaving standby no longer leaves the full app over the keyguard, and undock no longer closes the app while a countdown runs. Restart and Shut down are reachable from docked standby again. A stopped app no longer makes the PC run UI Automation every 10 s, and a refused focus no longer opens a second instance.

**Build Verified:** [x] Yes — shared:jvmTest, testDebugUnitTest, assembleDebug, lintDebug, validateDebugScreenshotTest, desktopApp:compileKotlin passed

## [2026-10-12] — Standby landscape: now-playing fits above the control bar

**Type:** Fix
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyNowPlaying.kt` — new `compact` mode: artwork + title/artist row, then timeline and transport
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/MediaAppsMode.kt` — wide layout always uses compact now-playing with 64dp artwork
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/MultimediaMode.kt` — wide now-playing half uses BoxWithConstraints, compact below 420dp height with scaled artwork
- `androidApp/src/screenshotTestDebug/reference/com/saubh/deskbuddy/StandbyScreenshotsKt/` — regenerated landscape references

**Summary:**
At 891x411 the transport buttons (Media+Apps) and seek bar (Multimedia) sat behind the control bar. Compact layout makes timeline and transport fully visible without scrolling; CenteredScrollColumn remains the safety net. Portrait unchanged.

**Build Verified:** [x] Yes — validateDebugScreenshotTest, testDebugUnitTest, lintDebug (0 errors) passed

## [2026-10-12] — M5 screenshot tests for standby modes, clock faces and power countdown

**Type:** Chore
**Files Changed:**
- `androidApp/src/screenshotTest/kotlin/com/saubh/deskbuddy/StandbyScreenshots.kt` — 12 previews: 3 landscape modes, portrait Media+Apps, disconnected, 4 clock faces, power Counting/Cancelled/Sending
- `androidApp/src/screenshotTestDebug/reference/com/saubh/deskbuddy/StandbyScreenshotsKt/` — 12 reference PNGs
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/DotMatrixFace.kt` — under LocalInspectionMode skip BatteryManager (layoutlib throws) and use a fixed date so the reference is stable

**Summary:**
Added Compose screenshot coverage for the four-mode standby, the clock faces and the power countdown card. Dot-matrix face needed a preview-safe battery read and fixed date. Known visual concern: on a 411dp-high landscape screen the control bar overlaps the transport buttons (Media+Apps) and seek bar (Multimedia). Manual device pass (dock/undock, hold Vol+, running-app keys, power countdown over standby) is PENDING.

**Build Verified:** [x] Yes — shared:jvmTest, androidApp:testDebugUnitTest, assembleDebug, lintDebug (0 errors, 35 warnings), validateDebugScreenshotTest, desktopApp:compileKotlin passed

## [2026-10-12] — Power countdown fix round 1: no late fire, accurate result, quieter TalkBack

**Type:** Fix
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/power/PowerController.kt` — new Sending state entered before the send, send runs in NonCancellable so Cancel/Back/disconnect cannot misreport it; connection tracked so startCountdown while disconnected finishes NOT_CONNECTED without counting
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/power/PowerCountdownState.kt` — added Sending
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/components/PowerCountdownDialog.kt` — Sending view (spinner, no Cancel); per-second text no longer a live region; title announces time left only at 10, 5, 3, 2, 1 via stateDescription
- `androidApp/src/main/res/values/strings.xml` — power_countdown_sending
- `androidApp/src/test/kotlin/com/saubh/deskbuddy/power/PowerControllerTest.kt` — 2 new tests (8 total)

**Summary:** Closes two safety gaps in the countdown: cancelling during the in-flight send no longer reports "Nothing was sent" for a command that went out, and a countdown can no longer start while disconnected and fire into a later connection. TalkBack no longer announces every second.

**Build Verified:** [x] Yes — `./gradlew :androidApp:testDebugUnitTest :androidApp:compileDebugKotlin :androidApp:lintDebug` passed (0 errors, 35 warnings, none from these files; no device check)

## [2026-10-12] — Shut down / Restart: 10-second cancellable countdown

**Type:** Feature
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/power/PowerController.kt` — app-scoped countdown (StateFlow, startCountdown, cancelCountdown); ends as NOT_CONNECTED if the link drops; send() kept for Lock
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/power/PowerCountdownState.kt`, `PowerOutcome.kt` — new state and outcome types
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/components/PowerCountdownDialog.kt` — draining ring, rolling seconds, haptic tick, Cancel, 2 s result state
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/components/ConfirmDialog.kt` — removed PowerConfirmDialog
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/home/HomeScreen.kt`, `HomeViewModel.kt` — dropped the confirm state; Lock instant, others start the countdown
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/ConnectionViewModel.kt`, `KonnektApp.kt` — expose the countdown; dialog drawn once at the app root, over the shell and standby
- `androidApp/src/main/res/values/strings.xml` — added countdown/result strings, removed power_restart_* / power_shutdown_* title/text/confirm
- `androidApp/src/test/kotlin/com/saubh/deskbuddy/power/PowerControllerTest.kt` — 6 tests

**Summary:** Per the user's request, tapping Shut down or Restart now starts a 10 s countdown with a Cancel button instead of an alert dialog. This deliberately replaces the CLAUDE.md confirmation-dialog rule for these two actions; the countdown serves the same purpose by making the action undoable. Cancel sends nothing; at 0 the command is sent; a mid-countdown disconnect sends nothing. Lock stays instant.

**Build Verified:** [x] Yes — `./gradlew :androidApp:testDebugUnitTest :androidApp:compileDebugKotlin :androidApp:lintDebug` passed (0 errors, 35 warnings, none from these files; no device check)

## [2026-10-12] — Clock faces fix round 1: Flip portrait layout, always-on cost, battery poll

**Type:** Fix
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/FlipClockFace.kt` — cards sized from BoxWithConstraints (min of 70% height and half the width less the gap, at aspect 0.82) so portrait windows no longer squash the minutes card; AM/PM label unchanged
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/NeonOrbitFace.kt` — frame loop updates seconds at most every 33 ms (~30 fps)
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/BubbleMorphFace.kt` — drift is a produceState stepped every 100 ms from a 60 s ping-pong; reduced-motion freeze at 0.5 kept
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/DotMatrixFace.kt` — initial battery value via remember(context) so Binder is not hit on every recomposition; dead list-equality check replaced by a remembered shown-text comparison

**Summary:** Fixes the portrait layout of the Flip face and cuts the always-on redraw cost of Neon and Bubble, plus two Dot-matrix cleanups.

**Build Verified:** [x] Yes — `./gradlew :androidApp:compileDebugKotlin :androidApp:lintDebug` passed (0 errors, 35 warnings, none from clock files; no device check)

## [2026-10-12] — Dot-matrix LED clock face

**Type:** Feature
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/DotMatrixFace.kt` — replaced placeholder with a Canvas of amber LED dots driven by DotMatrixFont; changed digits switch column by column in a left-to-right wave (instant under reduced motion); medium date and battery percent (polled every 60 s, hidden when unavailable) underneath

**Summary:** The Dot-matrix face is complete, which finishes the four standby clock faces. `java.text.DateFormat` is used for the date (no clash with android.text.format.DateFormat; it is not imported). The wave progress is read in the Canvas draw lambda only. The strings clock_am, clock_pm and standby_battery are now used, so their UnusedResources warnings are gone.

**Build Verified:** [x] Yes — `./gradlew :androidApp:compileDebugKotlin :androidApp:lintDebug :androidApp:testDebugUnitTest` passed (0 errors, 35 warnings, none from clock files; on-device visual check not done)

## [2026-10-12] — Neon orbit analog clock face

**Type:** Feature
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/NeonOrbitFace.kt` — replaced placeholder with a Canvas analog face: glowing hour/minute hands, hour ticks, and a seconds comet with a fading tail that glides per frame (steps once per second under reduced motion or in inspection mode)

**Summary:** The Neon face draws everything in a single Canvas; the per-frame seconds value is read only inside the draw lambda so no recomposition happens per frame.

**Build Verified:** [x] Yes — `./gradlew :androidApp:compileDebugKotlin` passed (full lint/test run pending at end of tasks 16-19; on-device visual check not done)

## [2026-10-12] — Bubble morph clock face

**Type:** Feature
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/BubbleMorphFace.kt` — replaced placeholder with chunky white digits over a drifting warm gradient; changed digits scale/fade in with a bouncy spring (damping 0.6) and squash out; reduced motion uses a short crossfade and a frozen gradient

**Summary:** The Bubble face now animates digit changes per character. The gradient offset is read only in drawBehind, so the drift does not recompose. Removed the brief's unused TextUnit import; `TextUnit * Float` compiled as written.

**Build Verified:** [x] Yes — `./gradlew :androidApp:compileDebugKotlin` passed (full lint/test run pending at end of tasks 16-19; on-device visual check not done)

## [2026-10-12] — Flip clock face

**Type:** Feature
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/FlipClockFace.kt` — replaced placeholder with split-flap HH/MM cards; old top flap folds down, new lower flap lands with a spring (damping 0.8); mid-fold shading; AM/PM label in 12h; reduced motion swaps digits without flipping

**Summary:** The standby Clock page's Flip face now renders real split-flap cards. All animated values are read in graphicsLayer/draw lambdas so a flip does not recompose.

**Build Verified:** [x] Yes — `./gradlew :androidApp:compileDebugKotlin` passed (full lint/test run pending at end of tasks 16-19; on-device visual check not done)

## [2026-10-12] — Standby host fixes: state across standby, safe pager reconcile, rail haptics

**Type:** Fix
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/KonnektApp.kt` — keyboardVisible is rememberSaveable and only blocks auto-standby while connected/reconnecting; shell and Devices wrapped in per-branch SaveableStateProvider ("shell", "devices") so tab state survives standby; root calls StandbyViewModel.onLeave() when standby ends
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyRoute.kt` — haptic only for user settles and rail taps; refused-pick reconcile runs in a cancellable child job with CancellationException handling so the settled-page collector never dies

**Summary:** Rotation and standby round-trips no longer lose the Keyboard flag, the selected tab or the standby session state. A user swipe that the mode policy refuses snaps the pager back without risking the collector that reports swipes to the ViewModel. Rail taps keep their haptic.

**Build Verified:** [x] Yes — `./gradlew :androidApp:testDebugUnitTest :androidApp:compileDebugKotlin :androidApp:lintDebug` passed

## [2026-10-12] — Four-mode standby at the app root with springy vertical pager and auto-hiding rail

**Type:** Feature
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyRoute.kt` — new; StandbyRoute (leaves standby on dispose except during rotation) and StandbyScreen (VerticalPager, critically damped spring snap, two-way pager/ViewModel sync, auto-hiding mode rail and close button, PC-page control bar)
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyViewModel.kt` — onEnter is a no-op when already entered, so rotation keeps the user's mode pick
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/KonnektApp.kt` — standby (user and auto) hoisted here so it works with no PC
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/shell/ConnectedShell.kt` — drops standby state; adds onOpenStandby and onKeyboardVisible
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/home/HomeViewModel.kt` — KDoc no longer mentions Standby
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyScreen.kt`, `StandbyMediaPage.kt`, `StandbyShortcutsPage.kt`, `StandbyPowerButtons.kt` — deleted
- `androidApp/src/main/res/values/strings.xml` — removed unused cd_restart_pc, cd_shutdown_pc
- `androidApp/src/test/kotlin/com/saubh/deskbuddy/ui/standby/StandbyViewModelTest.kt` — onEnter_givenAlreadyEntered_keepsUserPick

**Summary:** Standby now lives at the app root and shows the four modes in a vertical pager; the pager and ViewModel follow each other without feedback loops. Rotation no longer resets the chosen mode. Restart and shutdown stay on Home; the dock has Lock.

**Build Verified:** [x] Yes — `./gradlew :androidApp:testDebugUnitTest :androidApp:compileDebugKotlin :androidApp:lintDebug` passed

## [2026-10-12] — Standby clock mode with swipeable faces, ticker and burn-in shift

**Type:** Feature
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/ClockTicker.kt` — new; second-boundary ticker, 12/24h detection, +/-8 dp per-minute burn-in walk
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/ClockMode.kt` — new; face pager, page dots, ClockFaceContent, labelRes
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/FlipClockFace.kt`, `BubbleMorphFace.kt`, `NeonOrbitFace.kt`, `DotMatrixFace.kt` — new placeholder faces (digits only), replaced in tasks 16-19

**Summary:** Adds the Clock mode page: a HorizontalPager over the four faces that persists the settled face, a shared second ticker, and a burn-in offset applied in the layout phase. Faces are compiling placeholders for now.

**Build Verified:** [x] Yes — `./gradlew :androidApp:compileDebugKotlin :androidApp:lintDebug` passed

## [2026-10-12] — Standby media halves scroll; running keys use stateDescription

**Type:** Fix
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/CenteredScrollColumn.kt` — new; scrolls on overflow, centred when it fits
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/MultimediaMode.kt` — wide halves in scroll columns
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/MediaAppsMode.kt` — CompactMedia in a scroll column
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/AppKey.kt` — stateDescription for running keys
- `androidApp/src/main/res/values/strings.xml` — cd_app_running replaced by standby_app_running_state

**Summary:**
Media halves no longer squash or hide the transport on landscape phones, and TalkBack announces a running key once as its label plus state.

**Build Verified:** [x] Yes — `:androidApp:compileDebugKotlin :androidApp:lintDebug` passed

## [2026-10-12] — Development, Multimedia and Media + Apps modes

**Type:** Feature
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/AppKey.kt` — new; AppGrid and AppKey with running dot
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/DevelopmentMode.kt` — new; shortcut grid plus running-refresh loop
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyNowPlaying.kt` — new; artwork, titles, timeline, transport
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/MultimediaMode.kt` — new; now playing, step volume, device chips
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/MediaAppsMode.kt` — new; media and app keys half and half

**Summary:**
Three standby pages the host screen will compose. AppKey puts clickable before pressScale so the hit area does not shrink while pressed.

**Build Verified:** [x] Yes — `:androidApp:compileDebugKotlin :androidApp:lintDebug` passed

## [2026-10-12] — StandbyViewModel, StandbyPrefs and wiring

**Type:** Feature
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyViewModel.kt` — new; owns mode via StandbyModePolicy, step volume, forwards actions
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyUiState.kt` — new single UI state
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyActions.kt` — new @Stable actions interface
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/ClockFace.kt` — added ClockFaceStore port
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/prefs/StandbyPrefs.kt` — new DataStore-backed clock face
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/AppGraph.kt` — standbyPrefs
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/AppViewModelProvider.kt` — StandbyViewModel initializer
- `androidApp/src/test/kotlin/com/saubh/deskbuddy/ui/standby/StandbyViewModelTest.kt` — 5 tests

**Summary:**
One ViewModel now backs the four-mode standby screen: mode via the pure policy (falls back to clock when the PC drops), persisted clock face, media/apps/running state combined into StandbyUiState, and clamped step volume with a media-key fallback before audio state is known.

**Build Verified:** [x] Yes — `:androidApp:testDebugUnitTest :androidApp:compileDebugKotlin` passed

## [2026-10-12] — Standby components fix round: disabled keys, pager-safe hold, hit area

**Type:** Fix
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/RepeatingPress.kt` — `enabled` param (disabled = semantics only); consumes pointer changes during a hold; try/finally cleanup; uptime-based hold timing
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/VolumeStepper.kt` — `enabled` param with dimmed key; input modifiers before pressScale; Confirm haptic once at 0/100
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyControlBar.kt` — passes `enabled` through, removed Surface alpha and lambda guards
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/ModeRail.kt` — clickable before pressScale

**Summary:**
Disabled control bar no longer half-works or double-dims; holding a volume key inside a VerticalPager can no longer start a page scroll; scaling no longer shrinks hit areas; limit haptic is not repeated.

**Build Verified:** [x] Yes — `:androidApp:compileDebugKotlin :androidApp:lintDebug` passed

## [2026-10-12] — Standby shared components: rail, control bar, step volume, device chips

**Type:** Feature
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/PressScale.kt` — new: 0.96 press-down scale modifier
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/RepeatingPress.kt` — new: hold-to-repeat press modifier (RepeatSchedule), click semantics for a11y
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/VolumeStepper.kt` — new: VolumeStepButton, VolumeStepper, VOLUME_STEP = 2
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/OutputDeviceChips.kt` — new: PC output device chips
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/ModeRail.kt` — new: four-mode rail with spring highlight
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyControlBar.kt` — new: lock, vol -, play/pause, vol +
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/DisconnectedCard.kt` — new: no-PC / reconnecting card
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/ReducedMotion.kt` — new: animator-scale-0 check
- `shared/src/commonMain/kotlin/com/saubh/deskbuddy/ui/theme/KonnektIcons.kt` — Schedule, Code, GraphicEq, Dashboard icons
- `androidApp/src/main/res/values/strings.xml` — standby mode, volume, device, clock-face and battery strings

**Summary:**
Reusable Compose pieces for the four-mode standby screen. Nothing is wired into StandbyScreen yet; the clock/app/battery strings are consumed by later tasks (lint reports them unused until then). Haptics use SegmentFrequentTick/Confirm as in the brief (they resolved, no fallback needed).

**Build Verified:** [x] Yes — `:androidApp:compileDebugKotlin` and `:androidApp:lintDebug` passed (only UnusedResources warnings for strings used by later tasks)

## [2026-10-12] — Fix locale-dependent clock digits, split ClockAngles

**Type:** Fix
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/ClockTime.kt` — `digits` formats with `Locale.ROOT` so ar/fa locales still give ASCII digits
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/ClockAngles.kt` — new: moved out of ClockTime.kt (one type per file)
- `androidApp/src/test/kotlin/com/saubh/deskbuddy/ui/standby/clock/ClockHelpersTest.kt` — Arabic-locale digits test

**Summary:**
The LED font silently dropped non-ASCII digits, which blanked the dot-matrix face on Arabic/Persian locales.

**Build Verified:** [ ] No — unit tests only (`:androidApp:testDebugUnitTest`)

## [2026-10-12] — Standby helpers: repeat schedule, clock digits, LED font, burn-in walk

**Type:** Feature
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/RepeatSchedule.kt` — new: hold-to-repeat delays (400 ms, 150 ms, 60 ms after 1.5 s)
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/ClockTime.kt` — new: `ClockTime` digits and `ClockAngles` hand angles
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/DotMatrixFont.kt` — new: 5x7 LED glyphs and colon
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/BurnInWalk.kt` — new: random walk clamped to +/-8 dp
- `androidApp/src/test/kotlin/com/saubh/deskbuddy/ui/standby/RepeatScheduleTest.kt` — new: 3 tests
- `androidApp/src/test/kotlin/com/saubh/deskbuddy/ui/standby/clock/ClockHelpersTest.kt` — new: 6 tests

**Summary:**
Pure Kotlin helpers for the standby volume buttons and clock faces, with no Compose dependency so they are JVM-testable. Uses Calendar rather than java.time because minSdk 24 has no desugaring. `ClockAngles` shares `ClockTime.kt` as the one intended exception to one-class-per-file.

**Build Verified:** [ ] No — unit tests only (`:androidApp:testDebugUnitTest`); full build not run

## [2026-10-12] — Standby mode model and auto-switch policy

**Type:** Feature
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyMode.kt` — new: four standby modes and `StandbyModeState`
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/StandbyModePolicy.kt` — new: enter / connection-change / user-select transitions
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/ui/standby/clock/ClockFace.kt` — new: clock face enum with safe `fromName`
- `androidApp/src/test/kotlin/com/saubh/deskbuddy/ui/standby/StandbyModePolicyTest.kt` — new: 7 tests

**Summary:**
Pure Kotlin model for the four-mode standby screen. Standby opens on Media + Apps with a PC and Clock without; a user-picked mode is kept, but PC-dependent modes fall back to Clock when the PC disconnects. Unblocks the pager UI in later tasks.

**Build Verified:** [ ] No — unit tests only (`:androidApp:testDebugUnitTest`); full build not run

## [2026-10-12] — Activate apps from the phone with launch fallback

**Type:** Feature
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/apps/AppsUiState.kt` — `canActivate` field and `openCommandFor` (activate when the PC supports it, else launch)
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/apps/AppShortcutsController.kt` — copies `catalog.canActivate` into state; new `activate(appId)`
- `androidApp/src/test/kotlin/com/saubh/deskbuddy/apps/AppsUiStateTest.kt` — new: `openCommandFor` picks activate vs launch

**Summary:**
The phone now sends `ActivateAppCommand` when the desktop catalog reports `canActivate`, and falls back to `LaunchAppCommand` for older desktops that would answer `app_activate` with a generic error. `AppShortcutsController.activate` is the entry point; the existing `launch` is unchanged.

**Build Verified:** [ ] No — unit tests only (`:androidApp:testDebugUnitTest`, 47 passing); full build not run

## [2026-10-12] — Docking without a PC

**Type:** Feature
**Files Changed:**
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/service/DockPolicy.kt:17` — `shouldStandby` no longer takes `pcConnected`
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/service/AutoStandbyManager.kt` — removed `pcConnected` and `setPcConnected`; sensor runs whenever charging
- `androidApp/src/main/kotlin/com/saubh/deskbuddy/AppGraph.kt` — removed the `setPcConnected` collector
- `androidApp/src/test/kotlin/com/saubh/deskbuddy/service/DockPolicyTest.kt` — updated `shouldStandby` tests

**Summary:**
Charging in landscape now opens standby even with no PC connected (a clock). A connected PC is optional, so the docking rule is charging + landscape + not suppressed.

**Build Verified:** [ ] No — unit tests only (`:androidApp:testDebugUnitTest`); full build not run

## [2026-10-12] — Desktop: activate apps and tag running apps with their shortcut

**Type:** Feature
**Files Changed:**
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/openapps/ShortcutTargets.kt` — new: resolves pinned `.lnk`/`.exe` ids to a lower-cased exe (cached, PowerShell WScript.Shell, path via env var)
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/openapps/WindowActuator.kt` — `focusApp` and `shortcutFor` with safe defaults
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/openapps/WindowsWindowActuator.kt` — focus the front window of a shortcut's exe; match running exe to pinned shortcut
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/openapps/Win32Windows.kt` — `focus` (restore, Alt tap, SetForegroundWindow)
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/openapps/MacWindowActuator.kt` — `shortcutFor` by app name (`open` already activates)
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/AppCatalogService.kt` — `activate`, `shortcutApps`, `canActivate = true`
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ControlSession.kt` — `ActivateAppCommand` now focuses, else launches
- `desktopApp/src/main/kotlin/com/saubh/deskbuddy/DesktopController.kt` — `pushRunningApps` sets `RunningApp.shortcutId`
- Tests: `ShortcutTargetsTest`, `WindowsWindowActuatorTest`, `AppCatalogServiceTest`, `ControlSessionTest`, `TestFixtures` (`RecordingAppActuator`, `apps` parameter)

**Summary:**
The phone can bring an open app's window to the front or launch it, and running apps are tagged with the pinned shortcut they came from. Manual Windows focus check not done yet (needs the phone side).

**Build Verified:** [x] Yes — `./gradlew :shared:jvmTest :desktopApp:compileKotlin` passed

## [2026-10-12] — Protocol: ActivateAppCommand, canActivate, shortcutId

**Type:** Feature
**Files Changed:**
- `shared/src/commonMain/kotlin/com/saubh/deskbuddy/protocol/Messages.kt` — new `ActivateAppCommand` (`app_activate`), `AppCatalog.canActivate` (default false), `RunningApp.shortcutId` (default null)
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ControlSession.kt` — temporary `ActivateAppCommand` branch (launches) to keep `execute` exhaustive; Task 6 replaces it
- `shared/src/commonTest/kotlin/com/saubh/deskbuddy/protocol/WireCodecTest.kt` — round-trip and old-payload default tests

**Summary:**
Adds the wire types for phone-initiated app activation (bring window forward or launch). Defaults keep older desktop payloads decodable. Desktop behaviour follows in the next task.

**Build Verified:** [x] Yes — `./gradlew :shared:jvmTest :androidApp:compileDebugKotlin` passed

## [2026-10-12] — Bundle mediaremote-adapter from desktopApp/resources

**Type:** Chore
**Files Changed:**
- `desktopApp/build.gradle.kts:31` — `appResourcesRootDir` set to `resources/` so Compose Desktop ships per-OS files and `compose.application.resources.dir` resolves `macos/`
- `desktopApp/resources/macos/README.md` (new) — how to build and copy mediaremote-adapter on a Mac; `<pinned commit>` left as a placeholder for the Mac build

**Summary:**
The macOS build now has a place to bundle the mediaremote-adapter binaries (`MediaRemoteAdapter.framework`, `mediaremote-adapter.pl`, LICENSE) from `desktopApp/resources/macos/`. The binaries are not in the repo yet; without them the app falls back to Music/Spotify-only now playing. The Mac build (Task 4 Step 3) and the manual Mac checks (Step 6: browser now playing, transport without Accessibility, volume/mute/output devices, seek, empty server.log) are pending on the user and are not yet recorded here.

**Build Verified:** [x] Yes — `./gradlew :desktopApp:compileKotlin` passed (Windows); Mac build and `packageDmg` checks pending on the user

## [2026-10-12] — Mac MediaRemote actuator, fallback wrapper and adapter transport

**Type:** Feature
**Files Changed:**
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/media/MediaRemoteAdapter.kt` (new) — runs the bundled mediaremote-adapter under /usr/bin/perl (stream, test, send, seek) and locates it in the Compose resources dir
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/media/MacMediaRemoteSessionActuator.kt` (new) — system-wide now playing from the adapter stream, with artwork and restart backoff
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/media/FallbackMediaSessionActuator.kt` (new) — picks adapter or JXA session backend at start and mirrors its flows
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/MacMediaActuator.kt` — play/pause/next/previous try the adapter first, then post media keys
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ActuatorFactory.kt` — Mac branches wire the adapter and fallback
- `shared/build.gradle.kts` — kotlinx-coroutines-test for jvmTest
- `shared/src/jvmTest/.../media/FallbackMediaSessionActuatorTest.kt` (new), `shared/src/jvmTest/.../MacMediaActuatorTest.kt` — 5 new tests

**Summary:**
macOS now reports now playing from every app (browsers included) through the bundled mediaremote-adapter, and transport commands use it without needing Accessibility. When the adapter is missing or not working, the existing Music/Spotify JXA helper and media-key posting are used unchanged. The adapter itself can only run on macOS, so only the pure and fake-driven tests were exercised.

**Build Verified:** [x] Yes — `./gradlew :shared:jvmTest` passed (Windows); `assembleDebug lint` not run

## [2026-10-12] — MediaRemote stream parser

**Type:** Feature
**Files Changed:**
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/media/MediaRemoteStream.kt` (new) — folds `mediaremote-adapter stream --micros` JSON lines (full and diff) into samples, interpolating position and dropping oversized artwork
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/media/MediaRemoteSample.kt` (new) — merged payload value type
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/media/MediaRemoteApps.kt` (new) — bundle id to display name
- `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/media/MediaRemoteStreamTest.kt` (new) — 7 tests

**Summary:**
Pure parser for the system-wide now-playing stream the macOS actuator will consume in a later M5 task. Diff lines merge onto the last full payload (null removes a key); nothing playing yields NowPlayingCommand.NONE. The brief's test fixture used timestampEpochMicros 9000000000 (9000 s) while asserting a 9 s stamp; corrected to 9000000.

**Build Verified:** [x] Yes — `./gradlew :shared:jvmTest` passed (assembleDebug/lint not run for this JVM-only change)

## [2026-10-12] — Server log: desktop command failures are no longer silent

**Type:** Feature
**Files Changed:**
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ServerLog.kt` (new) — `ServerLogFile` (append-only, one rotated backup, never throws) and `ServerLog` writing to `~/.deskbuddy/server.log` and stderr
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ControlSession.kt` — new `log` constructor parameter; `handleEnvelope` logs command name and exception on failure
- `shared/src/jvmMain/kotlin/com/saubh/deskbuddy/server/ControlServer.kt` — logs undecodable frames without echoing the frame (it carries the token)
- `shared/src/jvmTest/kotlin/com/saubh/deskbuddy/server/ServerLogTest.kt` (new), `ControlSessionTest.kt` — tests for logging, rotation and the failing-actuator path

**Summary:**
Actuator exceptions were swallowed into an INTERNAL ack with nothing recorded, so failing power/media commands were invisible. They now land in a rotating log file and stderr. Tokens and payloads are never logged.

**Build Verified:** [x] Yes — `./gradlew :shared:jvmTest` passed (lint and assembleDebug not run for this JVM-only change)

## [2026-10-12] — M5 design spec: standby modes and Mac media

**Type:** Chore
**Files Changed:**
- `docs/superpowers/specs/2026-10-12-konnekt-m5-standby-modes-mac-media-design.md` (new) — design for four standby modes (Clock with 4 faces, Development, Multimedia, Media + Apps), new docking rules, `ActivateAppCommand`, and Mac media via the MediaRemote adapter

**Summary:**
Diagnosis: the committed code routes Mac media, audio and power to `Unsupported*` actuators, and the working-tree Mac implementation (29 untracked files) was never committed or run on a Mac. Even that implementation reads only Music and Spotify, so browser audio never shows. The spec covers the fix and the standby redesign. Documentation only; no code changed.

**Build Verified:** [ ] No — docs only, no build needed

## [2026-10-09] — Fix: phone stuck on "Connecting to your PC…"

**Type:** Fix
**Files Changed:**
- `shared/src/androidMain/.../client/DeskBuddyClient.kt:42` — 10 s timeout on the WebSocket handshake; a timeout throws `IOException`, so `RemoteSession` treats it as a failed attempt and auto-connect retries

**Summary:**
Diagnosed on the attached phone (Xiaomi 24116PCC1I, HyperOS): the PC server was healthy and reachable from the phone (`/info` 200, handshake 101), but the phone sat in "Connecting…" holding a TCP socket the PC no longer had (half-open), with no further attempts. `client.connect()` had no handshake timeout, and auto-connect only runs while the session is idle or searching, so one lost attempt froze the connection until the user intervened. The phone's network policy shows the app subject to background network blocking (`blocked=APP_BACKGROUND`), the likely reason attempts and live sockets get cut while the app is not in the foreground. Not caused by the macOS or docking changes: neither was installed on the phone (build from 2026-10-08) nor running on the PC (started 10:24).

**Build Verified:** [x] Yes — `./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:lintDebug :androidApp:validateDebugScreenshotTest :shared:jvmTest` passed

## [2026-10-09] — Docking opens standby even when the app is closed

**Type:** Feature
**Files Changed:**
- `androidApp/.../service/DockWakeJob.kt` (new) — JobScheduler job with a charging constraint restarts `ConnectionService` when the process is gone (Android 8+ no longer delivers ACTION_POWER_CONNECTED to manifest receivers); one-shot, rescheduled on every unplug; retries with backoff if Android refuses the background service start
- `androidApp/.../service/DockLauncher.kt` (new) — opens standby directly when "Display over other apps" is allowed, else posts the dock notification (full-screen intent)
- `androidApp/.../service/DockPermissions.kt` (new) — overlay and battery-optimisation checks and their settings intents
- `androidApp/.../service/DockPolicy.kt` (new) — pure landscape / standby rules
- `androidApp/.../service/AutoStandbyManager.kt` — uses the above; nudges auto-connect on plug-in (was up to 5 min backoff); no launch while the app is on screen; `stop()` no longer crashes when `start()` never ran
- `androidApp/.../service/SessionNotifications.kt` — "Docked standby" channel and `dockPrompt()`
- `androidApp/.../MainActivity.kt` — `autoStandbyIntent()`; standby opened by docking shows over the lock screen and turns the screen on; reports visibility to the manager
- `androidApp/.../AppGraph.kt` — plug-in triggers an immediate auto-connect attempt
- `androidApp/.../ui/home/DockSetupCard.kt` (new), `HomeScreen.kt` — Home card asking for the two permissions, re-checked on resume
- `androidApp/src/main/AndroidManifest.xml` — `SYSTEM_ALERT_WINDOW`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `USE_FULL_SCREEN_INTENT`, `DockWakeJob` service
- `androidApp/src/main/res/values/strings.xml` — dock strings
- `androidApp/src/test/.../service/DockPolicyTest.kt` (new)

**Summary:**
With no KonneKt process running, plugging the phone in now restarts the app's service; it connects to the paired PC at once, and when the phone is in landscape the app opens in standby (over the lock screen if locked) and closes again on unplug or turning upright. Android only allows this with two user-granted permissions, which a Home card requests. `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and `USE_FULL_SCREEN_INTENT` are restricted by Google Play policy (lint: BatteryLife). Must be verified on a device: job timing after plug-in varies by OEM, and aggressive OEM task killers (Xiaomi, Oppo, etc.) may need their own autostart setting.

**Build Verified:** [x] Yes — `./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:lintDebug :androidApp:validateDebugScreenshotTest` passed

## [2026-10-09] — macOS support for every desktop feature

**Type:** Feature
**Files Changed:**
- `shared/.../server/HostOs.kt`, `Osascript.kt` (new) — one OS check for the whole server; AppleScript/JXA runner (script on stdin, inputs as argv, timeouts)
- `shared/.../server/ActuatorFactory.kt` — macOS branch for every actuator; one audio actuator shared with the media keys
- `shared/.../server/keyboard/MacKeyboardActuator.kt`, `MacKeyPlan.kt` (new) — phone → Mac typing via CoreGraphics `CGEventPost` (Unicode text in ≤20-unit chunks); Ctrl→⌘, Alt→⌥, Meta→⌃, Ctrl+←/→/Backspace/Delete→⌥
- `shared/.../server/keyboard/GlobalHotkey.kt`, `MacGlobalHotkey.kt`, `MacForeground.kt` (new); `WindowsGlobalHotkey.kt` — ⌃⌥K via Carbon `RegisterEventHotKey`; overlay activates the app
- `shared/.../server/keyboard/AwtKeyTranslator.kt`, `RawKey.kt`; `desktopApp/.../ui/KeyboardOverlay.kt` — Mac → phone typing: ⌘/⌃ act as Ctrl, ⌥+letter types its character, ⌥+arrows are word-wise; hotkey label per OS
- `shared/.../server/audio/MacAudioActuator.kt`, `MacCoreAudio.kt`, `FourCc.kt` (new) — output devices, default device switch, volume and mute via CoreAudio (AppleScript fallback)
- `shared/.../server/MacMediaActuator.kt` (new) — play/pause/next/previous as system media keys; volume through CoreAudio
- `shared/.../server/media/MacMediaSessionActuator.kt`, `MacNowPlayingSample.kt`, `MacArtworkFetcher.kt`, `resources/deskbuddy/mac-now-playing.js` (new) — now playing, seek and artwork for Music and Spotify
- `shared/.../server/MacAppActuator.kt`, `MacAppIcon.kt` (new); `AppIconProvider.kt` — `.app` catalog, launch with `open`, icons via `plutil` + `sips`
- `shared/.../server/openapps/MacWindowActuator.kt`, `MacWindow.kt`, `MacWindowList.kt`, `MacWindowScript.kt`, `resources/deskbuddy/mac-windows.js` (new) — open windows, close window / quit app, browser tabs for Chrome, Edge, Brave, Vivaldi, Opera, Safari
- `shared/.../server/MacPowerActuator.kt` (new) — shut down / restart via System Events, lock via `SACLockScreenImmediate` (Ctrl+⌘+Q fallback)
- `shared/.../server/MacLaunchAgent.kt` (new); `StartupRegistration.kt` — "Open at login" as `~/Library/LaunchAgents/com.saubh.konnekt.plist`
- `shared/.../server/MacPasteboard.kt` (new) — `NSPasteboard.changeCount` so the clipboard is read only when it changed
- `desktopApp/.../DesktopController.kt`, `main.kt`, `ui/ServerScreen.kt`, `ui/StatusPanel.kt` — Mac hotkey, clipboard token, start-at-login card ("Open at login" / menu bar), Accessibility notice
- `desktopApp/build.gradle.kts` — macOS `bundleID` and `NSAppleEventsUsageDescription`
- Tests (new): `MacKeyPlanTest`, `MacAudioActuatorTest`, `MacMediaActuatorTest`, `MacNowPlayingSampleTest`, `MacAppActuatorTest`, `MacWindowListTest`, `MacWindowActuatorTest`, `MacLaunchAgentTest`; `AwtKeyTranslatorTest` extended

**Summary:**
Every desktop feature that was Windows-only now has a macOS implementation behind the existing actuator interfaces, so the phone app and protocol are unchanged. Windows behaviour is unchanged (the key translator runs its old path when not on a Mac). Known macOS limits: now playing reads only Music and Spotify (the system-wide MediaRemote API is closed to third-party apps since macOS 15.4), though media keys control any player; shortcut key codes are ANSI-position based; Firefox exposes no tabs. The user must grant Accessibility (typing, media keys, window closing) and Automation (System Events, Music, Spotify, browsers) permissions. None of the native or AppleScript paths have been run on a real Mac yet.

**Build Verified:** [x] Yes — `./gradlew :shared:jvmTest :desktopApp:build` passed on Windows (108 JVM tests, 0 failures). Not yet run on macOS.

## [2026-10-07] — KonneKt: UI redesign, close PC windows/tabs, keyboard fixes

**Type:** Feature
**Files Changed:**
- `docs/superpowers/specs/2026-10-07-konnekt-ui-redesign-design.md` — feature inventory, problems with the old UI, new information architecture
- `shared/.../protocol/Messages.kt` — `RunningApp`, `AppWindow`, `BrowserTab`, `running_apps_get`, `running_apps`, `window_close`, `app_close`, `tab_close`
- `shared/.../server/openapps/*` (new) — `WindowActuator`, `Win32Windows` (EnumWindows, cloaked/tool/owned filtering, exe name + FileDescription, `WM_CLOSE`), `BrowserTabs` (UI Automation PowerShell helper, `-EncodedCommand`, inputs via env vars; skips `Document` subtrees), `RunningAppsGrouping`, `WindowsWindowActuator` (only currently-listed windows can be closed), `TopLevelWindow`
- `shared/.../server/ControlSession.kt`, `Actuators.kt`, `ActuatorFactory.kt`; `desktopApp/.../DesktopController.kt` — window commands; listing runs on IO and is pushed (overlapping requests coalesced)
- `shared/.../server/keyboard/WindowsKeyboardActuator.kt` — scan codes for virtual keys; `WindowsForeground.kt` (new) + `desktopApp/.../ui/KeyboardOverlay.kt` — Ctrl+Alt+K overlay reliably takes keyboard focus
- `androidApp/.../session/RemoteSession.kt`, `keyboard/KeyboardController.kt`, `shared/.../client/DeskBuddyClient.kt` — a send on a just-closed socket returns false instead of killing the keystroke queue for the rest of the process
- `androidApp/.../keyboard/TextDiff.kt` — the invisible sentinel no longer costs an extra PC Backspace on select-all/replace or typing before it
- `androidApp/.../keyboard/KeyboardController.kt` — sticky modifiers apply to Backspace (Ctrl+Backspace) and uppercase letters add Shift
- `androidApp/.../keyboard/RemoteKeyboardHub.kt` — 5 s grace before a reconnect blip switches the phone keyboard back
- `androidApp/.../ime/DeskBuddyInputMethodService.kt` — soft-keyboard key event flags; `ime/KeyboardPickerActivity.kt` — finishes if the picker never appears
- `androidApp/.../service/AutoStandbyManager.kt`, `AppGraph.kt`, `MainActivity.kt` — auto-standby suppressed while the PC types to the phone; undocking no longer closes an app the user already had open
- `androidApp/.../service/SessionNotifications.kt`, `ConnectionService.kt` — keyboard prompt opens keyboard settings when KonneKt Keyboard is not enabled yet
- `androidApp/.../apps/RunningAppsController.kt`, `RunningAppsState.kt`, `power/PowerController.kt`, `media/MediaController.kt` (`send`) — data layer for the new screens
- `androidApp/.../ui/**` — full redesign: `KonnektApp`, `AppViewModelProvider` (constructor-injected per-screen ViewModels), `shell/` (Home · Media · Apps · Share · Keyboard, top bar with Standby + Disconnect), `connect/` (Devices screen, IP bottom sheet), `home/` (status + power, mini player, quick actions, shortcuts), `apps/` (Open now: windows/tabs with close; Launch), `share/`, `keyboard/` (Type on PC with key bar above the IME; Type on phone checklist), `components/`; standby rewired. Removed `RemoteScreen`, `ConnectScreen`, `AppsTab`, `ShareTab`, `KeyboardTab`, `KeyboardSetupCard`, `SpecialKeyRow`, `MediaTab`, `DeskBuddyApp`, `FabClearance`
- `shared/.../ui/theme/KonnektColors.kt`, `KonnektTheme.kt`, `KonnektIcons.kt` (renamed from DeskBuddy*) — teal/amber palette, new icons
- `androidApp/src/main/res/values/strings.xml` — KonneKt copy, plurals; `desktopApp/.../main.kt`, `StatusPanel.kt`, `build.gradle.kts`, `DeskBuddyAdvertiser.kt`, `LanInterfaces.kt` — user-visible rename
- Tests: `RunningAppsGroupingTest`, `BrowserTabsTest`, `WindowsWindowActuatorTest`, `RunningAppsControllerTest` (new); `ControlSessionTest`, `TextDiffTest`, `KeyboardControllerTest`, `RemoteKeyboardHubTest`, `FakeGateway`, `TestFixtures` (extended); `androidApp/src/screenshotTest/.../KonnektScreenshots.kt` + 9 reference images

**Summary:**
Renames the app to KonneKt (user-visible only; package, application id, mDNS type and protocol unchanged, so pairings survive) and rebuilds the Android UI around five destinations with a Home dashboard that surfaces power controls (previously hidden in Standby), replaces the power-icon "disconnect" with a link-off icon, and removes the Standby FAB that covered content. Adds viewing and closing the PC's open windows, whole apps, and individual browser tabs (Chrome/Edge/Brave/Vivaldi/Opera/Firefox) from the phone, each with a confirmation. Fixes the keyboard: the main cause of "stops working" was the keystroke queue dying permanently after one send on a dropping socket; also sentinel over-deletion, reconnect blips kicking out the phone keyboard, auto-standby stealing focus, and the overlay failing to take focus on the PC. Architecture deviation noted: the project keeps its manual `AppGraph` DI (no Hilt) and state-based top-level navigation; ViewModels now take their dependencies through constructors via one factory. Browser-tab reading and closing uses UI Automation and still needs to be checked on a real PC; Windows only.

**Build Verified:** [x] Yes — `./gradlew :shared:jvmTest :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:lintDebug :androidApp:validateDebugScreenshotTest :desktopApp:build` passed (see final run)

## [2026-09-28] — Milestone 4: Performance pass + shared keyboard

**Type:** Feature
**Files Changed:**
- `docs/superpowers/specs/2026-09-28-deskbuddy-m4-keyboard-performance-design.md`, `docs/superpowers/plans/2026-09-28-deskbuddy-m4.md` — spec and plan
- `shared/.../server/PairedDeviceStore.kt`, `ShortcutStore.kt` — lists cached in memory (P1, P2)
- `shared/.../server/ControlSession.kt` — one token lookup per message; cached catalog; key_text / key_press dispatch, silent on success, UNSUPPORTED_OS once per connection, 4096-char cap
- `shared/.../server/AppCatalogService.kt` — `forPhone()`: cached reply + background rescan after 30 s (P2)
- `shared/.../server/ClipboardWatcher.kt`, `WindowsClipboardSequence.kt` — read clipboard only on sequence change (P4)
- `shared/.../server/FileSender.kt` — one IO context per send (P6)
- `shared/.../server/AudioActuator.kt`, `audio/WindowsAudioActuator.kt`, `media/MediaStateBroadcaster.kt` — `levels()` in one COM hop, cached endpoint (P5)
- `shared/.../protocol/Messages.kt` — `Key`, `KeyModifier`, `KeyTextCommand`, `KeyPressCommand`, `KeyboardModeCommand`
- `shared/.../server/keyboard/*` — `KeyboardActuator`, `SendInputPlan`, `WindowsKeyboardActuator`, `XdotoolArgs`, `XdotoolKeyboardActuator`, `RawKey`, `AwtKeyTranslator`, `TypingState`, `PhoneTypingSession`, `WindowsGlobalHotkey`
- `shared/.../server/Actuators.kt`, `ActuatorFactory.kt` — keyboard actuator
- `shared/.../ui/theme/DeskBuddyIcons.kt` — `Keyboard`
- `desktopApp/.../DesktopController.kt`, `main.kt` — IO for slow work, non-blocking quit, typing session + hotkey + tray item (P3)
- `desktopApp/.../ui/KeyboardOverlay.kt`, `ui/StatusPanel.kt`, `ui/ServerScreen.kt` — overlay, keyboard chip
- `desktopApp/build.gradle.kts` — `-Xms32m -Xmx256m -XX:+UseSerialGC`, runtime modules (P7); `gradle.properties` — parallel (P8)
- `androidApp/.../media/MediaUiState.kt`, `MediaController.kt`, `ui/media/PlaybackControls.kt` — position interpolated in the timeline, no 500 ms state tick (P9)
- `androidApp/.../service/PcMediaSession.kt` — skip unchanged metadata (P10); `share/FileTransferController.kt` — per-percent progress (P11)
- `androidApp/.../session/AutoConnector.kt`, `AutoConnectBackoff.kt`, `AutoConnectPlan.kt`, `RemoteSession.kt`, `ui/ConnectScreen.kt`, `service/AutoStandbyManager.kt`, `AppGraph.kt` — LAN-only probing (loopback always) with 10 s→5 min backoff, no mDNS while connected, lifecycle-aware sweep, sensor gated on connection (P12–P14)
- `androidApp/build.gradle.kts`, `proguard-rules.pro` — R8 + resource shrinking (P15); release APK 15 MB → 2.0 MB
- `androidApp/.../keyboard/*` — `TextDiff`, `ModifierState`, `KeyboardController`, `ImeStatus`, `RemoteKeyboardHub`, `AndroidKeyMapping`
- `androidApp/.../ui/keyboard/*`, `ui/RemoteScreen.kt`, `ui/ConnectionViewModel.kt` — Keyboard tab
- `androidApp/.../ime/DeskBuddyInputMethodService.kt`, `ime/KeyboardPickerActivity.kt`, `res/layout/ime_bar.xml`, `res/xml/deskbuddy_method.xml`, `AndroidManifest.xml` — DeskBuddy Keyboard
- `androidApp/.../service/SessionNotifications.kt`, `ConnectionService.kt` — "PC keyboard ready" prompt
- `androidApp/src/main/res/values/strings.xml` — keyboard, IME and notification copy
- Review fix pass: `server/ControlServer.kt` + `DesktopController.kt` (`onPhoneAuthenticated` → `PhoneTypingSession.phoneReconnected()` re-announces typing after a fast reconnect); `session/ErrorThrottle.kt` + `RemoteSession.kt` (identical errors at most once per 3 s); `server/keyboard/XdotoolArgs.kt`, `XdotoolKeyboardActuator.kt` (`--delay 1`, timeout scales with text); `session/Ipv4Subnet.kt`, `AutoConnectPlan.kt`, `AutoConnector.kt` (off-LAN probing keeps PCs on the phone's hotspot/USB-tether subnet); `server/ClipboardWatcher.kt` (sequence token advanced only after a successful read); `ime/DeskBuddyInputMethodService.kt` (no landscape fullscreen editor)
- Tests (all new): commonTest `WireCodecTest`; jvmTest `PairedDeviceStoreTest`, `ControlSessionTest`, `AppCatalogServiceTest`, `ClipboardWatcherTest`, `FileSenderTest`, `MediaStateBroadcasterTest`, `WindowsAudioActuatorTest`, `SendInputPlanTest`, `XdotoolArgsTest`, `AwtKeyTranslatorTest`, `PhoneTypingSessionTest`; androidApp `MediaControllerTest`, `AutoConnectBackoffTest`, `AutoConnectPlanTest`, `TextDiffTest`, `ModifierStateTest`, `KeyboardControllerTest`, `RemoteKeyboardHubTest`, `AndroidKeyMappingTest`, `ErrorThrottleTest`

**Summary:**
Removes the per-message disk reads, per-request PowerShell scans, UI-thread blocking, whole-screen media recomposition and always-on network probing found in the code review, and turns on R8 for release. Adds keyboard sharing both ways: the phone's Keyboard tab types live into the PC's focused window with special keys and sticky modifiers; Ctrl+Alt+K on the PC opens a focus overlay whose keystrokes DeskBuddy Keyboard (an IME) types into the phone's focused app. The repository had no committed tests before this milestone (the M1–M3 tests listed in earlier entries were never added to git); all tests here are new. Review Tiers 1–2 (pairing brute force, frame-size limit, crash fixes, transfer cleanup) remain open. On-device checks (release APK, IME, hotkey overlay) are pending the user.

**Build Verified:** [x] Yes — `./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug :desktopApp:build` passed (73 JVM/Android unit tests, 0 failures, after the review fix pass)

## [2026-09-28] — Stop tracking signing keystore and release APK

**Type:** Chore
**Files Changed:**
- `.gitignore` — ignore `keystore`, `*.jks`, `*.keystore`, `*.apk`, `*.aab`, `androidApp/release/`
- `keystore`, `androidApp/release/**` — removed from the index (`git rm --cached`); files kept on disk

**Summary:**
The Android signing keystore and a 15 MB release APK were committed in 186e834 and pushed to `origin/develop`. They are now untracked and ignored so they cannot be committed again. They remain in existing history (no rewrite was done), so the keystore should be treated as exposed: generate a new one stored outside the repo before the first public release.

**Build Verified:** [x] N/A — no source or build-script change

## [2026-09-23] — Adaptive Android UI (phones, foldables, tablets, desktop windows)

**Type:** Feature
**Files Changed:**
- `gradle/libs.versions.toml`, `build.gradle.kts`, `androidApp/build.gradle.kts`, `gradle.properties` — `material3-adaptive-navigation-suite` (1.11.0-alpha07), Compose Preview Screenshot Testing plugin + `screenshot-validation-api` 0.0.1-alpha16
- `androidApp/.../ui/WindowWidth.kt` — `isWideWindow()`: window size class ≥ medium width (600dp) replaces the portrait/landscape orientation checks
- `androidApp/.../ui/RemoteScreen.kt` — `NavigationSuiteScaffold` with `NavigationSuiteItem`s replaces the hand-rolled bar/rail switch; stateless `RemoteShell` extracted for previews; wide-but-short windows (phone landscape) get a rail instead of the library's default bar; top app bar hides on scroll down and returns on scroll up (`enterAlwaysScrollBehavior`)
- `androidApp/.../ui/AppsTab.kt` — shortcut grid `GridCells.Adaptive(104dp)`; all-apps rows span three tiles so wide windows show several rows side by side
- `androidApp/.../ui/ShareTab.kt` — one `LazyVerticalGrid(Adaptive(320dp))` replaces the two-branch column layout; action cards and inbox items flow into columns, headings span full width
- `androidApp/.../ui/ConnectScreen.kt` — `LazyColumn` → adaptive grid (discovered PCs flow in 340dp columns); content centred at 1040dp max on very wide windows
- `androidApp/.../ui/standby/StandbyShortcutsPage.kt` — `GridCells.Adaptive(112dp)`; `StandbyScreen.kt`, `StandbyMediaPage.kt`, `ui/media/MediaTab.kt` — use `isWideWindow()`
- `androidApp/.../ui/media/NowPlayingCard.kt` — optional own scroll when shown beside the controls (was clipped in phone landscape); `ui/FabClearance.kt` — bottom space so the last item of each tab scrolls clear of the Standby FAB (it covered the output-device button on phones)
- `androidApp/src/screenshotTest/.../FormFactorPreviews.kt`, `PreviewSamples.kt`, `AdaptiveScreenshots.kt` — `@PreviewTest` screenshots of the Media, Apps, Share and Connect screens on phone, phone landscape, foldable, tablet and desktop
- `androidApp/src/screenshotTestDebug/reference/...` — 20 reference images

**Summary:**
Makes the Android app adapt to window size rather than orientation, following the adaptive skill. Navigation moves to `NavigationSuiteScaffold` (bottom bar on compact phones, rail on wider windows), vertical lists become adaptive grids, and the connected screen's app bar hides while scrolling. Multi-pane list-detail/supporting-pane layouts were not added: the app has no list→detail screen pairs and does not use Navigation 3, which that step requires. The experimental `Grid` API (Step 4.2) was not used; it needs explicit opt-in. Screenshot references were recorded after the changes and reviewed visually for phone, phone landscape, tablet and desktop; the pre-change UI was not captured.

**Build Verified:** [x] Yes — `./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:lintDebug :androidApp:validateDebugScreenshotTest :desktopApp:build` passed (lint 0 errors; 20/20 screenshots match references).

## [2026-09-23] — Milestone 3: Reliable Discovery, Now Playing, Audio Output, Standby Mode

**Type:** Feature
**Files Changed:**
- `docs/superpowers/specs/2026-09-23-deskbuddy-m3-nowplaying-standby-discovery-design.md` — design spec (decisions D1–D4, assumptions A1–A8)
- `docs/superpowers/plans/2026-09-23-deskbuddy-m3.md` — task plan T1–T13
- `plan/pc-remote-control-app-requirements.md` — §2.1/§2.8 now-playing, audio output, standby, discovery; §3.1 platform rows
- `shared/src/commonMain/.../protocol/Messages.kt` — `PlaybackStatus`, `NowPlayingCommand`, `MediaArtworkCommand`, `AudioStateCommand`, `AudioDevice`, `MediaStateRequestCommand`, `MediaSeekCommand`, `SetVolumeCommand`, `SetAudioDeviceCommand`, `DesktopInfo`; `AppCatalog.icons`
- `shared/src/commonMain/.../protocol/WireCodec.kt` — `encodeInfo`/`decodeInfo`
- `shared/src/commonMain/.../media/PlaybackClock.kt` — position interpolation shared by both sides
- `shared/src/commonMain/.../discovery/SubnetHosts.kt` — /24-capped sweep candidates
- `shared/src/commonMain/.../ui/theme/DeskBuddyIcons.kt` — `Speaker`, `Headphones`, `Nightlight`, `ExpandMore`
- `shared/src/jvmMain/.../server/MediaSessionActuator.kt`, `AudioActuator.kt` — new actuator interfaces + `Unsupported*`
- `shared/src/jvmMain/.../server/Actuators.kt` — `mediaSession`, `audio`, `onStateRequested`
- `shared/src/jvmMain/.../server/ControlSession.kt` — dispatch for state request, seek, volume, output device; push-only commands rejected
- `shared/src/jvmMain/.../server/media/MediaHelperProtocol.kt`, `NowPlayingFilter.kt`, `WindowsMediaSessionActuator.kt` — PowerShell helper process wrapper, JSON-line protocol, 1.5 s drift filter, restart with backoff
- `shared/src/jvmMain/resources/deskbuddy/media-session.ps1` — Windows system media session helper (now playing, artwork, seek/transport)
- `shared/src/jvmMain/.../server/audio/CoreAudioIds.kt`, `ComObject.kt`, `WindowsAudioActuator.kt` — Core Audio via raw JNA COM vtables: render endpoints, master volume, mute, default-device switch (IPolicyConfig)
- `shared/src/jvmMain/.../server/media/MediaStateBroadcaster.kt` — diffs actuator state into `Push` commands; `resendAll()`
- `shared/src/jvmMain/.../server/AppIconProvider.kt`, `AppCatalogService.kt` — 64 px shell icons for shortcut apps only
- `shared/src/jvmMain/.../server/ActuatorFactory.kt` — `mediaSession()`, `audio()`, `iconFor`
- `shared/src/jvmMain/.../server/LanInterfaces.kt`, `DeskBuddyAdvertiser.kt` — one JmDNS per real LAN adapter (link-local/virtual skipped)
- `shared/src/jvmMain/.../server/ControlServer.kt` — `GET /info` route, `desktopName`
- `shared/src/androidMain/.../client/DesktopDiscovery.kt` — serialised resolves with retry, keyed by host
- `shared/src/androidMain/.../client/SubnetScanner.kt` — TCP probe + `/info` confirm across the phone's /24
- `androidApp/.../session/RemoteSession.kt` — merges mDNS + sweep; implements `MediaGateway`
- `androidApp/.../session/MediaGateway.kt` — controller-facing session interface (testable)
- `androidApp/.../media/MediaUiState.kt`, `MediaController.kt` — mirrors pushes, interpolates position, throttles/holds volume, seek + device switch
- `androidApp/.../apps/AppsUiState.kt`, `AppShortcutsController.kt`, `ui/AppIcon.kt`, `ui/AppsTab.kt` — decoded icons on shortcut tiles
- `androidApp/.../ui/media/MediaTab.kt`, `NowPlayingCard.kt`, `PlaybackControls.kt`, `VolumePanel.kt`, `OutputDeviceSheet.kt`, `TimeFormat.kt` — redesigned media tab (artwork/placeholder, wavy indicator while playing, expressive slider timeline, button-group transport with play/pause state, volume slider + mute, split-button output device + modal sheet)
- `androidApp/.../ui/standby/StandbyScreen.kt`, `StandbyShortcutsPage.kt`, `StandbyMediaPage.kt`, `KeepScreenOn.kt` — black always-on pager, clock, immersive, back/close exit
- `androidApp/.../ui/RemoteScreen.kt` — `MediumExtendedFloatingActionButton` "Standby"; media tab wiring
- `androidApp/.../ui/ConnectionViewModel.kt` — `SubnetScanner`, `MediaController`
- `androidApp/src/main/res/values/strings.xml` — new copy; removed `media_playback`, `media_play_pause`, `media_volume`
- `androidApp/build.gradle.kts`, `gradle/libs.versions.toml` — `core-ktx` (pinned `androidx-core` 1.18.0: 1.19.0 needs compileSdk 37), `kotlin-test-junit`, `junit`, `kotlinx-coroutines-test`
- `desktopApp/.../DesktopController.kt`, `ui/StatusPanel.kt`, `ui/ServerScreen.kt` — broadcaster wiring, helper start/stop, "Now playing: on/unavailable" chip
- Removed: `androidApp/.../ui/MediaTab.kt` (replaced by `ui/media/`)
- Tests: `WireCodecTest` (+3), `PlaybackClockTest`, `SubnetHostsTest` (commonTest); `ControlSessionTest` (+7), `MediaHelperProtocolTest`, `NowPlayingFilterTest`, `MediaStateBroadcasterTest`, `WindowsAudioActuatorTest` (real COM, Windows-only), `WindowsMediaSessionActuatorTest` (real helper, Windows-only), `AppIconProviderTest` (Windows-only), `AppCatalogServiceTest` (+2), `LanInterfacesTest`, `ControlServerIntegrationTest` (+1), `ActuatorFactoryTest` (+1), fakes in `TestActuators` (jvmTest); `MediaControllerTest` (7), `TimeFormatTest` (androidApp unit tests, new source set)

**Summary:**
Fixes the flaky PC discovery and adds a full "now playing" experience plus a standby remote. Discovery: the desktop no longer binds mDNS to whatever `getLocalHost()` returns (this PC has four link-local virtual adapters) but advertises on every real LAN adapter; the phone serialises and retries NSD resolves and, if nothing shows up within 3 s, sweeps its own /24 on the DeskBuddy port and confirms hits via the new `GET /info` — the same path manual IP entry uses. Now playing: a bundled PowerShell helper reads the Windows system media session (title, artist, album, app, artwork, status, position, duration, seekability) and streams JSON lines to the desktop, which pushes changes to the phone (position only on jumps > 1.5 s; the phone interpolates). Audio: output devices, master volume and mute come from Core Audio via JNA COM; the phone can switch the default device and set volume with a slider (throttled while dragging, 1 s hold against stale pushes). Shortcut tiles now carry the PC's shell icons. The media tab is rebuilt with M3 Expressive components, and a "Standby" FAB opens a black, always-on, immersive two-page remote (shortcuts / media). Deviations from the spec: no new `ErrorKind`s (the session cannot attribute an Ack to a command; the existing not-found snackbar is reused and the next push corrects the UI); `androidx-core` pinned to 1.18.0. Not verified: phone ↔ PC end-to-end on a device (no phone attached in this session); everything else (helper launch, COM device enumeration/volume, icon extraction, `/info`) ran for real on this Windows 11 PC.

**Review fix pass (same day, after a whole-branch code review):**
- `androidApp/.../media/MediaController.kt` — artwork that arrives before the now-playing push naming it is kept in a one-slot pending buffer and adopted when the state arrives (the helper sends artwork first, so every track change lost its artwork); mute now has the same 1 s hold as volume; base64 decoding runs on `Dispatchers.Default`
- `androidApp/.../ui/media/NowPlayingCard.kt`, `ui/AppIcon.kt` — bitmap decoding moved out of composition (`produceState` on `Dispatchers.Default`)
- `shared/src/jvmMain/.../server/media/MediaStateBroadcaster.kt`, `Actuators.kt`, `ControlSession.kt`, `desktopApp/.../DesktopController.kt` — `onAudioChanged` hook: after a volume or output-device command the device list is refreshed and pushed at once instead of at the next 5 s poll (the phone's device choice used to flip back for up to 5 s)
- `shared/src/jvmMain/.../server/media/NowPlayingFilter.kt`, `WindowsMediaSessionActuator.kt` — filter reset on helper exit so a restarted helper's first (unchanged) sample is published again instead of leaving "Nothing playing"
- `shared/src/commonMain/.../protocol/WireCodec.kt`, `shared/src/androidMain/.../client/DeskBuddyClient.kt` — `decodeOrNull`: an unknown message/command type from a newer desktop is dropped instead of tearing the connection down
- Tests added: `MediaControllerTest` (+2: artwork-before-state, mute hold), `NowPlayingFilterTest` (+1 reset), `MediaStateBroadcasterTest` (+1 `audioChanged`), `ControlSessionTest` (+1 hook), `WireCodecTest` (+1 `decodeOrNull`)
- Deferred (minor, not fixed): sweep results never re-scanned once found; sweep uses the active network rather than Wi-Fi specifically; possible double helper spawn if `refresh()` races a restart backoff; blocking COM polled on `Dispatchers.Default`; live-stream slider renders full; helper re-requests the session manager every 500 ms; `" • "`, `"--:--"`, `"head"` literals in Android Kotlin; non-interactive `AssistChip`; helper temp script re-extracted per launch; standby shortcuts page shows "no shortcuts" while loading; seek blocks the websocket handler for up to 2 s (single-user app).
- **Still required before merge:** the phone ↔ PC end-to-end pass (track skip with artwork, output-device switch, mute, helper kill/restart, discovery with Wi-Fi toggled). No phone was attached in this session.

**Build Verified:** [x] Yes — `./gradlew :shared:jvmTest :shared:testAndroidHostTest :shared:compileKotlinJs :shared:compileKotlinWasmJs :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:lintDebug :desktopApp:build` passed after the fix pass (139 JVM + 41 Android host + 11 app unit tests green; lint 0 errors, 13 pre-existing warnings).

## [2026-09-16] — UI Redesign: Material 3 Expressive (Android + Desktop)

**Type:** Refactor
**Files Changed:**
- `shared/src/commonMain/.../ui/theme/DeskBuddyTheme.kt` — `MaterialExpressiveTheme` with `MotionScheme.expressive()`, shared by both apps, light/dark
- `shared/src/commonMain/.../ui/theme/DeskBuddyColors.kt` — brand tonal palette (indigo primary, coral tertiary), M3 Expressive tone-30 container text
- `shared/src/commonMain/.../ui/theme/DeskBuddyIcons.kt` — 34 Material Symbols as `ImageVector`s (bundled core icon set lacks media/share glyphs)
- `shared/src/commonMain/.../ui/components/Components.kt` — `SectionTitle`, cookie-shaped `IconAvatar`/`LetterAvatar` (`MaterialShapes.Cookie9Sided`), Medium filled/tonal/outlined buttons with press shape-morph
- `androidApp/.../ui/DeskBuddyApp.kt` — wraps in `DeskBuddyTheme`; `ConnectScreen.kt` — `LargeFlexibleTopAppBar`, primary-container hero card for the saved PC, `LoadingIndicator` while discovering, contained cards; `PinDialog.kt` — six-cell PIN entry
- `androidApp/.../ui/RemoteScreen.kt` — `TopAppBar` with subtitle status + tonal power button; `ShortNavigationBar` (portrait) / `WideNavigationRail` (landscape); `MediaTab.kt` — large filled play/pause + medium tonal controls, mute as shape-morphing toggle; `AppsTab.kt` — cookie-avatar shortcut tiles, pill search, star toggles; `ShareTab.kt` — surface-tier cards, medium buttons, `ContainedLoadingIndicator` progress
- `androidApp/src/main/res/values/strings.xml` — new copy for the redesigned screens
- `desktopApp/build.gradle.kts` — Material3 from the version catalog (the plugin's bundled 1.9 hid the expressive APIs)
- `desktopApp/.../ui/ServerScreen.kt` — `WideNavigationRail` + pane header; `StatusPanel.kt` — hero card per state (loading indicator / display-size PIN with countdown / connected device), address chips; `SharePanel.kt`, `ShortcutsPanel.kt`, `AppPickerDialog.kt` — restyled with the shared components
- Removed: `androidApp/.../ui/Components.kt` (moved to shared)

**Summary:**
Rebuilt every screen on both platforms around Material 3 Expressive: one shared theme (expressive spring motion, extended shape scale, emphasized type styles, brand tonal palette), containment via surface-container tiers instead of flat lists, a single high-emphasis action per card, medium/large button sizes with shape morphing, `MaterialShapes` cookie badges for identity, the new expressive navigation (short bar, wide rail), flexible app bar on the connect screen, and the morphing `LoadingIndicator` for waiting/pairing/transfer states. No behaviour or protocol changes.

**Build Verified:** [x] Yes — `./gradlew :shared:jvmTest :shared:testAndroidHostTest :shared:compileKotlinJs :shared:compileKotlinWasmJs :androidApp:assembleDebug :androidApp:lintDebug :desktopApp:build` passed.

## [2026-09-16] — Milestone 2: Clipboard Sync, Text/File Sharing, App Shortcuts

**Type:** Feature
**Files Changed:**
- `docs/superpowers/specs/2026-09-16-deskbuddy-m2-sharing-apps-design.md` — design spec with stated assumptions (A1–A7)
- `docs/superpowers/plans/2026-09-16-deskbuddy-m2.md` — task plan T1–T8
- `plan/pc-remote-control-app-requirements.md` — clipboard sync / file transfer moved into scope; app-shortcut selection added
- `shared/src/commonMain/.../protocol/Messages.kt` — new `Push`, `AppCatalog`, `DesktopApp`; commands for clipboard, text share, file offer/chunk/complete/cancel, app catalog/launch/shortcut add/remove; `ErrorCode.NOT_FOUND`, `TRANSFER_FAILED`
- `shared/src/commonMain/.../protocol/Protocol.kt` — `FILE_CHUNK_BYTES`, `CLIPBOARD_POLL_MILLIS`, `RECEIVED_FOLDER_NAME`
- `shared/src/commonMain/.../transfer/FileChunkCodec.kt`, `TransferTracker.kt`, `FileNames.kt`, `TransferIds.kt` — pure transfer helpers shared by both receivers
- `shared/src/jvmMain/.../server/Actuators.kt`, `ShareActuator.kt`, `AwtShareActuator.kt`, `ClipboardWatcher.kt` — desktop clipboard + text inbox, echo-suppressed polling watcher
- `shared/src/jvmMain/.../server/FileReceiver.kt`, `FileSender.kt` — chunked file receive into `~/Downloads/DeskBuddy`, chunked send via `ControlServer.push`
- `shared/src/jvmMain/.../server/AppActuator.kt`, `WindowsAppActuator.kt`, `LinuxAppActuator.kt`, `ShortcutStore.kt`, `AppCatalogService.kt` — Start Menu `.lnk` / `.desktop` enumeration, `cmd /c start` / `sh -c` launch, persisted shortcuts, launch-id validation
- `shared/src/jvmMain/.../server/ActuatorFactory.kt` — now builds the `Actuators` bundle (`media()`, `apps()` helpers)
- `shared/src/jvmMain/.../server/ControlSession.kt` — dispatches all new commands; `execute()` returns the reply message (chunks are silent)
- `shared/src/jvmMain/.../server/ControlServer.kt` — single-writer outbound channel + `push()` for desktop-initiated messages
- `desktopApp/.../DesktopController.kt` — wires server, clipboard watcher, file sender, inbox, notices; pushes catalog changes to the phone
- `desktopApp/.../main.kt`, `ui/ServerScreen.kt`, `ui/StatusPanel.kt`, `ui/SharePanel.kt`, `ui/ShortcutsPanel.kt`, `ui/AppPickerDialog.kt`, `ui/FilePickers.kt` — two-column window: status + Share / App shortcuts tabs
- `androidApp/.../session/RemoteSession.kt` — session loop moved out of the ViewModel; exposes `incoming` pushes and `send(command)`
- `androidApp/.../share/ClipboardShareController.kt`, `FileTransferController.kt`, `ReceivedFileStore.kt`, `ShareUiState.kt`, `PendingShare.kt` — clipboard sync (foreground listener + pushes), text share, chunked file send/receive (MediaStore Downloads on API 29+), share-sheet queue
- `androidApp/.../apps/AppShortcutsController.kt`, `AppsUiState.kt` — catalog mirror, launch / pin / unpin, refresh on connect
- `androidApp/.../ui/ConnectionViewModel.kt` — thin facade over session + controllers; flushes pending shares once connected
- `androidApp/.../ui/RemoteScreen.kt`, `MediaTab.kt`, `AppsTab.kt`, `ShareTab.kt`, `DeskBuddyApp.kt`, `ConnectionUiState.kt` — three-tab remote (bottom bar portrait / rail landscape), new error kinds
- `androidApp/.../MainActivity.kt`, `AndroidManifest.xml` — `ACTION_SEND` intent filters (text + files), `singleTop`
- `androidApp/src/main/res/values/strings.xml` — all new UI strings
- Tests: `WireCodecTest` (extended), `FileChunkCodecTest`, `TransferTrackerTest`, `FileNamesTest` (commonTest); `TestActuators` fixture, `ControlSessionTest` (extended), `ControlServerIntegrationTest` (push case), `FileReceiverTest`, `ShortcutStoreTest`, `AppCatalogServiceTest`, `WindowsAppActuatorTest`, `LinuxAppActuatorTest`, `ActuatorFactoryTest` (jvmTest)

**Summary:**
Adds bidirectional clipboard sync (desktop pushes changes automatically; phone pushes on foreground changes or on demand and can pull), text and file sharing in both directions over the existing WebSocket (base64 chunks, 32 KiB, ordered and size-checked by a shared `TransferTracker`), Android share-sheet integration, and desktop-selected app shortcuts: the desktop enumerates installed programs (Windows Start Menu, Linux `.desktop`) or accepts any browsed file, persists the chosen shortcuts, and the phone shows them as a one-tap grid plus a searchable full list with pin/unpin. The server only launches ids present in its own catalog. Desktop-initiated traffic uses a new `Push` message routed through a single outbound channel in `ControlServer`, which also gives file sends natural backpressure.

**Build Verified:** [x] Yes — `./gradlew :shared:jvmTest :shared:testAndroidHostTest :shared:compileKotlinJs :shared:compileKotlinWasmJs :androidApp:assembleDebug :androidApp:lintDebug :desktopApp:build` passed (91 JVM + 28 Android host tests green; lint 0 errors, only pre-existing M1 warnings remain). Note: a stale `DeskBuddy\DeskBuddy` path cached from the project's old location broke `mergeLibDexDebug`; fixed by deleting `shared/build`, `androidApp/build`, `.gradle/configuration-cache` and building once with `--no-build-cache`.

## [2026-08-05] — Milestone 1: Connect + Media/Volume Control

**Type:** Feature
**Files Changed:**
- `gradle/libs.versions.toml` — added kotlinx-serialization, Ktor 3.3.1, JmDNS, JNA, DataStore versions/libraries and the kotlinx-serialization plugin
- `shared/build.gradle.kts` — serialization plugin; commonMain (serialization-json, coroutines), androidMain (Ktor client), jvmMain (Ktor server, JNA, JmDNS), jvmTest (Ktor client) dependencies
- `shared/src/commonMain/.../protocol/Protocol.kt` — port/path/service-type/pairing constants
- `shared/src/commonMain/.../protocol/Messages.kt` — sealed Message/Command hierarchy (PairRequest, PairAttempt, Envelope, PairSuccess, PairFailure, Ack; MediaCommand, MediaAction, ErrorCode)
- `shared/src/commonMain/.../protocol/WireCodec.kt` — JSON codec with `type` discriminator
- `shared/src/commonMain/.../pairing/PinGenerator.kt` — 6-digit PIN generator
- `shared/src/commonMain/.../pairing/PairingSession.kt` — pairing state machine (3 attempts, 60s timeout)
- `shared/src/jvmMain/.../server/TokenGenerator.kt` — SecureRandom 32-byte hex tokens
- `shared/src/jvmMain/.../server/PairedDeviceStore.kt` — paired devices persisted at `~/.deskbuddy/paired.json`
- `shared/src/jvmMain/.../server/MediaActuator.kt` — actuator interface + UnsupportedMediaActuator
- `shared/src/jvmMain/.../server/WindowsMediaActuator.kt` — JNA SendInput media/volume key taps
- `shared/src/jvmMain/.../server/ActuatorFactory.kt` — runtime OS branching
- `shared/src/jvmMain/.../server/ControlSession.kt` — per-connection message handler (pairing, auth, dispatch)
- `shared/src/jvmMain/.../server/ControlServer.kt` — Ktor CIO WebSocket server on port 8765, single-session policy, StateFlow<ServerState>
- `shared/src/jvmMain/.../server/ServerState.kt` — Waiting/Pairing/Connected
- `shared/src/jvmMain/.../server/DeskBuddyAdvertiser.kt` — JmDNS advertising of `_deskbuddy._tcp`
- `shared/src/androidMain/.../client/DeskBuddyClient.kt` — Ktor WebSocket client wrapper
- `shared/src/androidMain/.../client/DesktopDiscovery.kt` — NSD discovery as callbackFlow
- `desktopApp/.../main.kt`, `desktopApp/.../ui/ServerScreen.kt` — desktop window (waiting/pairing/connected)
- `desktopApp/build.gradle.kts` — added compose.material3
- `androidApp/.../prefs/PairedDesktopPrefs.kt` — DataStore persistence of paired desktop
- `androidApp/.../ui/ConnectionUiState.kt`, `ConnectionViewModel.kt` — MVVM state + session/reconnect loop
- `androidApp/.../ui/DeskBuddyApp.kt`, `ConnectScreen.kt`, `PinDialog.kt`, `RemoteScreen.kt` — screens (portrait + landscape)
- `androidApp/.../MainActivity.kt` — wires ViewModel + DeskBuddyApp
- `androidApp/src/main/AndroidManifest.xml` — INTERNET/ACCESS_NETWORK_STATE permissions, cleartext traffic
- `androidApp/src/main/res/values/strings.xml` — all UI strings
- `androidApp/build.gradle.kts` — compose/material3/lifecycle/datastore dependencies
- `local.properties` — set sdk.dir
- Tests: `WireCodecTest`, `PinGeneratorTest`, `PairingSessionTest` (commonTest); `PairedDeviceStoreTest`, `ActuatorFactoryTest`, `ControlSessionTest`, `ControlServerIntegrationTest` (jvmTest)

**Summary:**
Implemented DeskBuddy Milestone 1 end-to-end per `docs/superpowers/specs/2026-08-05-deskbuddy-m1-design.md`: shared KMP JSON-over-WebSocket protocol, PIN pairing with persistent tokens on both sides, mDNS discovery with manual-IP fallback, Windows media/volume actuation via JNA SendInput (java.awt.Robot cannot send media keys), desktop Compose window, and Android client with portrait/landscape remote UI. Unblocks all later milestones (track info, power/app control, trackpad/keyboard, Linux support) which extend the sealed Command hierarchy.

**Build Verified:** [x] Yes — `./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:assembleDebug :desktopApp:build` passed; `:shared:compileKotlinJs :shared:compileKotlinWasmJs` passed (web targets stay compatible)
