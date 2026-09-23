# DeskBuddy Milestone 2 Implementation Plan — Sharing + App Shortcuts

**Spec:** `docs/superpowers/specs/2026-09-16-deskbuddy-m2-sharing-apps-design.md`
**Goal:** Clipboard sync, text/file sharing (both directions, plus Android share sheet), and
desktop-selected app shortcuts launchable from the phone.

## Global constraints

- No git commits unless asked. Package root `com.saubh.deskbuddy`.
- `shared/commonMain` stays web-compatible (no JVM/Android APIs).
- Files under 400 lines, one class per file where practical, strings in `strings.xml`.
- Test names use the `` `method_givenCondition_expected` `` style.
- Verify with `./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:assembleDebug :desktopApp:build`.

## Tasks (in dependency order)

- [x] **T1 Protocol** — `Messages.kt` (new commands, `Push`, `AppCatalog`, `DesktopApp`, error codes),
  `Protocol.kt` constants, `transfer/FileChunkCodec.kt`, `transfer/TransferTracker.kt`,
  `transfer/FileNames.kt`, `transfer/TransferIds.kt`. Tests: `WireCodecTest` (extend),
  `FileChunkCodecTest`, `TransferTrackerTest`, `FileNamesTest`.
- [x] **T2 Server actuators** — `Actuators.kt`, `ShareActuator.kt`, `AwtShareActuator.kt`,
  `ClipboardWatcher.kt`, `FileReceiver.kt`, `FileSender.kt`, `AppActuator.kt`,
  `WindowsAppActuator.kt`, `LinuxAppActuator.kt`, `ShortcutStore.kt`, `AppCatalogService.kt`,
  `ActuatorFactory.kt` (returns bundle). Tests: `FileReceiverTest`, `ShortcutStoreTest`,
  `AppCatalogServiceTest`, `WindowsAppActuatorTest`, `ActuatorFactoryTest` (update).
- [x] **T3 Server session/push** — `ControlSession.kt` dispatch for all commands returning
  `Message?`; `ControlServer.kt` outbound channel + `push()`. Tests: `ControlSessionTest`
  (update + new cases), `ControlServerIntegrationTest` (push case).
- [x] **T4 Desktop UI** — `DesktopController.kt`, `ServerScreen.kt` (two-column shell),
  `StatusPanel.kt`, `SharePanel.kt`, `ShortcutsPanel.kt`, `AppPickerDialog.kt`, `main.kt`.
- [x] **T5 Android session refactor** — `session/RemoteSession.kt` (moved loop),
  `ConnectionViewModel.kt` facade, `ConnectionUiState.kt` (new error kinds).
- [x] **T6 Android controllers** — `share/ClipboardShareController.kt`,
  `share/FileTransferController.kt`, `share/ReceivedFileStore.kt`, `share/ShareUiState.kt`,
  `share/PendingShare.kt`, `apps/AppShortcutsController.kt`, `apps/AppsUiState.kt`.
- [x] **T7 Android UI** — `RemoteScreen.kt` (tab shell, portrait/landscape), `MediaTab.kt`,
  `AppsTab.kt`, `ShareTab.kt`, `DeskBuddyApp.kt`, `MainActivity.kt` (share intents),
  `AndroidManifest.xml` (intent filters), `strings.xml`.
- [x] **T8 Verify + changelog** — full build/tests, `2026_Saubhagya_changelog.md` entry,
  update `plan/pc-remote-control-app-requirements.md` scope section.
