# EchoReading Remediation Plan

Last updated: 2026-10-21
Base revision: `7e45cd0` (branch `dev`, version 2.2.0)

This file is the durable source of truth for the remediation work. Any agent or
developer continuing this work should read this file first, keep the status
column current, and append verification results after each change.

## Goal

Make the existing EchoReading feature set trustworthy and release-ready:

1. The advertised offline voice must actually work from a fresh install/build.
2. Reader state, playback state, and transport controls must never disagree.
3. Navigation and share entry points must be predictable from any screen.
4. Privacy claims must match actual network/backup behaviour.
5. Large disk operations and downloads must not depend on a screen staying open.
6. Tests must cover the Android paths that keep breaking.

Do not change product scope while doing this. Preserve the current offline-first
architecture unless a task explicitly says otherwise.

## Status legend

- `[ ]` not started
- `[~]` in progress
- `[x]` done and verified
- `[!]` blocked / needs a decision or an unavailable environment

## Work items

### R0. Durable handoff state

- Status: `[x]`
- Files: `docs/REMEDIATION.md`, `AGENTS.md`
- Notes: The plan and agent instructions are stored in the repository so a
  future session on any machine can resume without relying on chat history or
  `.statem/`. They are part of the current uncommitted change set.

### R1. History "load to reader" must use the canonical state transition

- Status: `[x]`
- Files: `app/src/main/java/com/echoreading/HistoryScreen.kt`,
  `app/src/main/java/com/echoreading/reader/ReaderState.kt`,
  `app/src/main/java/com/echoreading/reader/ReaderPlaybackService.kt`
- Problem: the card copied `entry.text` directly into `ReaderState.snapshot`.
  Active audio kept playing and old position/offset/status remained.
- Change: the history card now calls `ReaderState.loadText(context, entry.text)`.
  `loadText` stops the old service, resets position/duration/offset, writes the
  draft, and emits the canonical load event consumed by `EcoApp`. An epoch guard
  prevents a late service stop/end callback from overwriting the newly loaded
  state.
- Acceptance: loading history while playing stops the old session, resets
  position/duration/offset, writes the draft, and opens Home.
- Verification: JVM suite plus
  `ReaderAndroidSmokeTest.loadTextResetsPositionAndEpoch` and
  `playbackSeekSpeedAndHistoryLoad` on the Pixel_6 API 37 emulator. Manual
  history load during playback also stopped the MediaSession and reset
  status/position.

### R2. Share/PROCESS_TEXT navigation must not capture a stale route

- Status: `[x]`
- Files: `app/src/main/java/com/echoreading/ui/EcoApp.kt`,
  `app/src/main/java/com/echoreading/HistoryScreen.kt`
- Problem: `LaunchedEffect(Unit)` captured the initial `currentRoute`; shares
  received on Settings/History did not navigate. Tab navigation also used
  `popUpTo("home") { inclusive = true }`, which let non-home tabs accumulate on
  the back stack.
- Change: `rememberUpdatedState` holds the live route and all tab/share routing
  uses one helper with `popUpTo("home") { saveState = true }`,
  `restoreState = true`, and `launchSingleTop = true`. History now relies on the
  central load event instead of navigating a second time.
- Acceptance: share text/audio from any tab navigates to the correct screen;
  Home remains the start destination and Back from a tab returns Home.
- Verification: JVM suite plus
  `ReaderAndroidSmokeTest.shareFromSettingsUpdatesDraftAndReturnsHome` and
  `appLaunchesOnHome` on the Pixel_6 API 37 emulator.

### R3. Seek and restart controls must work

- Status: `[x]`
- Files: `app/src/main/java/com/echoreading/reader/ReaderPlaybackService.kt`,
  `app/src/main/java/com/echoreading/util/ComandUtils.kt`,
  `app/src/main/java/com/echoreading/ui/screen/HomeScreen.kt`
- Problem: the progress slider had a no-op handler; the Repeat icon was not
  clickable.
- Change: added `ACTION_SEEK` with an `EXTRA_POSITION` payload, implemented
  `seekTo()` (including not-yet-prepared chunks), made the slider scrub locally
  and dispatch on finish, and turned the old Repeat icon into a working
  "restart from beginning" action.
- Acceptance: dragging the slider changes playback position; restart starts the
  current text from position 0.
- Verification: JVM suite plus the real-playback instrumentation test (seek and
  restart path) and manual slider/restart checks on the Pixel_6 API 37 emulator.

### R4. Speed/voice changes must affect active playback or be labelled as future-only

- Status: `[x]`
- Files: `app/src/main/java/com/echoreading/reader/ReaderState.kt`,
  `app/src/main/java/com/echoreading/reader/ReaderPlaybackService.kt`,
  `app/src/main/java/com/echoreading/util/ComandUtils.kt`,
  `app/src/main/java/com/echoreading/ui/component/AppBar.kt`,
  `app/src/main/java/com/echoreading/ui/component/AppWidget.kt`,
  `app/src/main/java/com/echoreading/SettingsScreen.kt`
- Problem: UI chips changed only `ReaderState`; only the Quick Read popup sent
  `ACTION_SPEED`. The displayed speed/voice could differ from audible audio.
- Change: centralized speed/voice updates in `ReaderState.setSpeed/setVoice`
  and added `refreshVoice` for provider changes. Active sessions receive
  `ACTION_SPEED`/`ACTION_VOICE`; while playing/preparing the service restarts at
  the live position, and while paused it discards stale prepared media so the
  next Play regenerates with the new setting. Selection sites in Settings,
  the voice picker, the Quick Read popup, and the app widget were migrated.
- Acceptance: after changing speed/voice while playing, audio restarts at the
  new setting; while paused, Play uses the new setting.
- Verification: JVM suite plus the real-playback instrumentation test (speed
  change restarts at the live position); manual speed change and voice-picker
  checks on the Pixel_6 API 37 emulator. A change to a second installed voice
  model is not automated because only the bundled voice is installed.

### R5. Privacy, backup, and version truth

- Status: `[x]`
- Files: `app/src/main/AndroidManifest.xml`,
  `app/src/main/res/xml/data_extraction_rules.xml`,
  `app/src/main/res/values/strings.xml`,
  `app/src/main/java/com/echoreading/SettingsScreen.kt`,
  `app/src/main/java/com/echoreading/ui/component/ElevenLabsSettingsSection.kt`,
  `README.md`
- Problem: the privacy card claimed no data ever left the device while optional
  ElevenLabs synthesis uploads text; `allowBackup=true` allowed Android cloud
  backup of reading history. Version strings disagreed (2.2.0 / 2.4 / 2.1.2).
- Change: disabled Android backup (`allowBackup=false`) and added data
  extraction rules excluding every backup/D2D domain. The privacy card,
  ElevenLabs section, and README now disclose optional ElevenLabs uploads.
  Visible versions now say 2.2.0. The just-in-time notification permission
  request on Play is retained; the unconditional first-launch prompt was removed.
- Acceptance: no claim contradicts code; historical data is not cloud-backed by
  default; all visible version strings agree.
- Verification: merged manifest inspection + `:app:lintDebug` passed after the
  resource/manifest edit. Manual notification-permission check on the Pixel_6
  API 37 emulator: the prompt appeared on Play, Allow created the playback
  channel, and controls/media keys stayed consistent.

### R6. Bundled voice must be provisioned deliberately

- Status: `[x]`
- Files: `app/build.gradle`, `README.md`,
  `app/src/main/assets/vits-piper-pt_PT-tugao-medium/tokens.txt`
- Problem: `.gitignore` excludes the `.onnx`; the APK contained only tokens, so
  a clean build could not synthesize the advertised offline voice. The reader
  silently downloaded 63 MB on first play.
- Change: added Gradle `fetchBundledVoice` and `verifyBundledVoice` tasks with
  the expected size/SHA-256, documented the release provisioning step, and
  made `assembleRelease` depend on `verifyBundledVoice`. The README is truthful
  about a raw source checkout.
- Acceptance: `.\gradlew fetchBundledVoice` produces the model locally;
  `.\gradlew verifyBundledVoice` fails clearly if it is absent/corrupt.
- Verification: the task was run locally; the 63,201,425-byte model with
  SHA-256 `0d922da6f6fd87f981bb05fa8f698a1af6fc5c9366c212cdeb36a0f04c3c056d`
  was verified and included in the debug APK. `:app:assembleRelease` also
  completed and ran `verifyBundledVoice` first. The model remains intentionally
  untracked because of its size.

### R7. Remove main-thread storage scans

- Status: `[x]`
- Files: `app/src/main/java/com/echoreading/SettingsScreen.kt`,
  `app/src/main/java/com/echoreading/StorageStats.kt`,
  `app/src/main/java/com/echoreading/voice/OfflineVoice.kt`,
  `app/src/main/java/com/echoreading/MainActivity.kt`
- Problem: Settings composables walked voice/cache/model trees and called
  SQLite size helpers on the main thread; `discoveryVoices()` also ran model
  metadata repair synchronously.
- Change: both Settings storage views now load stats through
  `loadStorageStats()` on `Dispatchers.IO`; the storage screen refreshes
  asynchronously after deletions. `OnnxMetadata.repairAllInstalledVoices()` was
  moved to the existing startup IO prewarm and removed from
  `OfflineVoice.discoveryVoices()`.
- Acceptance: opening Settings/Storage performs no recursive disk walk on the
  main thread; voice discovery no longer repairs models inline.
- Verification: compile + call-site inspection (the recursive walk and metadata
  repair are now inside `Dispatchers.IO`). Device frame timing was not measured;
  no jank benchmark exists yet.

### R8. Voice downloads must survive leaving the screen

- Status: `[x]`
- Files: `app/src/main/java/com/echoreading/voice/VoiceDownloadManager.kt`,
  `app/src/main/java/com/echoreading/VoiceDiscoveryScreen.kt`,
  `app/src/main/java/com/echoreading/SettingsScreen.kt`,
  `app/src/main/java/com/echoreading/ui/component/AppBar.kt`
- Problem: downloads were launched in `rememberCoroutineScope`, so leaving the
  screen canceled a multi-minute transfer. Progress was screen-local.
- Change: added an app-scoped `VoiceDownloadManager` exposing a `StateFlow` of
  progress/status with cancellation and one-shot result acknowledgement.
  Settings, Voice Discovery, and the voice picker now use it. A completed
  download selects the installed voice from whichever screen is visible.
- Acceptance: leaving the originating screen does not cancel an in-progress
  voice download.
- Verification: compile + unit suite. The app-scoped manager was exercised by
  navigating Settings/Voice Discovery during manual device checks.
- Limitation: the original coroutine manager was app-process scoped. R12
  replaced it with WorkManager for process-death durability.

### R9. Android-level tests for the paths fixed above

- Status: `[x]`
- Files: `app/src/androidTest/java/com/echoreading/ReaderAndroidSmokeTest.kt`,
  `app/build.gradle`
- Problem: all 552 automated tests were JVM tests and
  `returnDefaultValues = true` hides Android behaviour, so services, playback,
  and navigation had no device coverage. Compose/Espresso cannot run on the
  API 37 emulator (`InputManager.getInstance()` reflection failure), so the
  suite uses `ActivityScenario` plus UIAutomator.
- Change: added four instrumentation tests: launch/Home tabs, share from
  Settings, `loadText` reset, and a real TTS playback test covering position
  growth, speed restart, seek, and history load while playing.
- Verification: `:app:connectedDebugAndroidTest` passed on the Pixel_6 API 37
  x86_64 emulator: 4 tests, 0 failures, 0 errors, 0 skipped (26.145 s in the
  final R13 run; 58.067 s in the first run). The playback test also passed 3/3
  standalone runs. Manual device checks covered notification permission,
  pause/resume via controls and media keys, slider seek, restart, speed change,
  share, history load, and opening the voice picker during playback.
- Remaining gaps: a change to a second installed voice is not automated;
  rotation/adaptive layout and audible output are still manual/future work.
  Real download continuation and process death were verified manually under R12
  (debug and signed release) but have no automated regression test. Do not call
  the JVM suite "end-to-end".

### R10. Release configuration

- Status: `[x]`
- Files: `app/build.gradle`, `app/proguard-rules.pro`, `app/src/main/java/...`
  (lint fixes), `docs/REMEDIATION.md`, `PROJECT.md`, `TEST_READY.md`,
  `TEST_INFRA.md`
- Change: `compileSdk 37`, `targetSdk 36`, Java/Kotlin target 17; release R8
  minification + resource shrinking with Sherpa/ONNX keep rules; optional
  environment/property-driven release signing (`RELEASE_STORE_FILE`,
  `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`);
  updated stable dependencies (Compose BOM 2026.09.00, Media3 1.11.1,
  Navigation 2.10.2, Activity 1.13.0, core-ktx 1.19.1, coroutines 1.11.0,
  androidTest libraries); WorkManager 2.12.0 for R12.
- Verification: `:app:assembleRelease` and `:app:bundleRelease` pass with R8;
  final unsigned release APK 200,385,009 bytes and AAB 124,650,909 bytes, both
  containing the 63,201,425-byte Tugão `.onnx` and all four ABI JNI libraries.
  The R8 dex retains `com.k2fsa.sherpa.onnx.**`. A temporary local keystore
  signed the release; it installed on the Pixel_6 API 37 emulator, showed
  `targetSdk=36`, loaded Sherpa/ONNX, and played Tugão through Media3
  (`BUFFERING -> PLAYING`, position advanced). The full 4-test instrumentation
  suite still passes after the R10 dependency updates and the R12 download
  migration.
- JitPack decision: the Sherpa artifact stays pinned to
  `com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.8`. It is a third-party
  JitPack build; before a public production release, vendor the AAR or add
  Gradle dependency verification for that coordinate. This is an external
  supply-chain step, not a code blocker.
- Production signing: configuration is wired, but no production keystore is
  available in this environment. The signed-release evidence used a temporary
  test key. Production signing and Play submission remain external steps; check
  the current Play target-API policy at submission time.

### R11. Documentation and version cleanup

- Status: `[x]`
- Files: `PROJECT.md`, `TEST_READY.md`, `TEST_INFRA.md`, `README.md`,
  `docs/REMEDIATION.md`
- Change: README/privacy/version were corrected in R5. `PROJECT.md` now describes
  the 2.2.0 architecture, routes, state/command contracts, code layout, build
  provisioning, and known gaps. `TEST_READY.md` and `TEST_INFRA.md` now report the
  actual 552-test JVM suite (1 skip) and the four-test Android instrumentation
  suite, their commands, and remaining device gaps instead of the old 512/193
  and Tier-complete claims.
- Verification: Markdown inspection plus the test/lint/build/device evidence
  below.

### R12. Durable voice downloads across process death

- Status: `[x]`
- Files: `app/src/main/java/com/echoreading/voice/VoiceDownloadManager.kt`,
  `app/src/main/java/com/echoreading/voice/VoiceDownloadWorker.kt`,
  `app/src/main/java/com/echoreading/SettingsScreen.kt`,
  `app/src/main/java/com/echoreading/VoiceDiscoveryScreen.kt`,
  `app/src/main/java/com/echoreading/ui/component/AppBar.kt`,
  `app/src/main/java/com/echoreading/MainActivity.kt`, `app/build.gradle`
- Problem: the R8 manager survived navigation but not Android killing the app
  process. A 63 MB transfer could be lost after backgrounding.
- Change: downloads are unique `OneTimeWorkRequest`s with a connected-network
  constraint. `VoiceDownloadWorker` reconstructs local/catalog voices by key and
  reports progress; `VoiceDownloadManager` keeps the existing `StateFlow`/
  `VoiceDownloadState` UI contract, reattaches on app start, persists the
  pending/acknowledged work id in `SharedPreferences`, and cancels through
  WorkManager. `OfflineVoice` already downloads to `.part` and renames only
  after validation, so a killed transfer is discarded and restarted safely.
- Acceptance: killing/relaunching the app resumes or safely restarts an
  interrupted download without corrupting a partially written model.
- Verification (debug): started a real 60.2 MB Edresson catalog download,
  force-stopped at 12,407,764 bytes of the `.part`, relaunched, observed
  WorkManager run attempt 2 and restart from zero, then `Worker result SUCCESS`,
  a 63,104,689-byte `.onnx` + `meta.json`, and the voice listed in Settings
  (`2 vozes · 120 MB`). `Ouvir Amostra` loaded the downloaded model.
- Verification (signed R8 release): repeated the start/force-stop/relaunch flow;
  logcat showed `VoiceDownloadWorker` rescheduled and `Worker result SUCCESS`,
  and Voice Discovery then showed Edresson as `Instalada` with `Ouvir`.
  `ReaderPlaybackService` also played Tugão in the same signed release
  (`PLAYING`, position advanced).
- Verification (final R13 signed release): started Edresson, force-stopped at
  `1%`, relaunched, and observed WorkManager restart the transfer and finish
  with `Worker result SUCCESS`. Settings then showed `2 vozes · 120 MB` with
  Tugão and Edresson installed; Tugão remained the selected voice, and Edresson
  was available to select. No `FATAL EXCEPTION` was logged.

### R13. History database failures must not crash playback

- Status: `[x]`
- Files: `app/src/main/java/com/echoreading/reader/ReadingHistory.kt`
- Problem: a signed-release device run crashed with
  `SQLiteException: Can't upgrade read-only database from version 0 to 1:
  history.db` during the history insert that `ReaderPlaybackService` performs
  when playback starts. A non-critical history write took down the playback
  process. The emulator had also logged filesystem I/O errors, so the trigger
  was environmental, but the app should degrade instead of crashing.
- Change: all reads and writes go through a retry/reopen wrapper. It retries
  with a fresh `SQLiteOpenHelper`, deletes an unusable database only after
  repeated recoverable failures, and returns an empty list/zero/no-op as a last
  resort. Recoverable failures include corrupt, cannot-open, disk-I/O, full,
  read-only, and locked databases, plus SQLite messages such as `read-only`,
  `read/write mode`, `could not open the database`, and `locked`. Failures are
  logged under the `ReadingHistory` tag.
- Acceptance: an unreadable or corrupt history database never crashes reading or
  playback; the app continues with empty history.
- Verification: on debug, created a 0-byte `databases/history.db` with mode
  `444`, then shared text and started playback. Logcat showed open failures on
  attempts 1 and 2; the unusable file was deleted and recreated as a 28,672-byte
  database with `user_version=1` and one history entry. Playback completed
  (00:00/00:02), the process stayed alive, and there was no `FATAL EXCEPTION`.
  A normal final signed-release run also shared text, played Tugão through
  `BUFFERING -> PLAYING` (3 s duration), and showed no history or playback crash.
  There is no automated regression test for this failure path yet; the evidence
  is a manual device simulation.

## Verification log

Add newest entries at the top. Include command, result, and which item it covers.

| Date | Command | Result | Item |
|---|---|---|---|
| 2026-10-21 | Final signed R13 release: start Edresson download, force-stop at `1%`, relaunch | WorkManager restarted `VoiceDownloadWorker`; `Worker result SUCCESS`; Settings showed `2 vozes · 120 MB` (Tugão + Edresson); no `FATAL EXCEPTION` | R12, R13 |
| 2026-10-21 | Debug app: 0-byte read-only `databases/history.db`, share text, start playback | Logcat showed open failures on attempts 1 and 2; DB recreated at 28,672 bytes, `user_version=1`, one entry; playback 00:00/00:02; no `FATAL EXCEPTION` | R13 |
| 2026-10-21 | Final signed release: install 200,439,120-byte APK, share text, play Tugão | `BUFFERING -> PLAYING`, 3 s duration, position advanced; no history/playback crash | R10, R13 |
| 2026-10-21 | `.\gradlew.bat :app:assembleRelease :app:bundleRelease` | `BUILD SUCCESSFUL`; final unsigned release APK 200,385,009 bytes; AAB 124,650,909 bytes; `.onnx` + four ABIs retained | R6, R10, R13 |
| 2026-10-21 | `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.injected.device.serial=emulator-5554"` | `BUILD SUCCESSFUL`; 4 tests, 0 failures, 0 errors, 0 skipped in 26.145 s on Pixel_6 API 37 x86_64 | R9, R13 |
| 2026-10-21 | `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug` | 552 tests, 0 failures, 0 errors, 1 skipped (30 suites); lint 95 findings (0 errors) | R1-R8, R11-R13 |
| 2026-10-21 | Signed R8 release on Pixel_6: start Edresson download, `am force-stop`, relaunch | WorkManager rescheduled `VoiceDownloadWorker`; `Worker result SUCCESS`; catalog voice became `Instalada`/`Ouvir`; Tugão playback reached `PLAYING` and advanced position | R10, R12 |
| 2026-10-21 | `.\gradlew.bat :app:assembleRelease :app:bundleRelease` | `BUILD SUCCESSFUL`; pre-R13 R8 release APK 200,385,009 bytes; AAB 124,649,457 bytes; R8 dex retains `com.k2fsa.sherpa.onnx.**`; `.onnx` + four ABIs present | R6, R10 |
| 2026-10-21 | `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.injected.device.serial=emulator-5554"` | `BUILD SUCCESSFUL`; 4 tests, 0 failures, 0 errors, 0 skipped in 25.948 s on Pixel_6 API 37 x86_64 | R9 |
| 2026-10-21 | `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug` | 552 tests, 0 failures, 0 errors, 1 skipped (30 suites); lint 95 findings (0 errors) | R1-R8, R11, R12 |
| 2026-10-21 | Debug app: start Edresson download, force-stop at 12,407,764-byte `.part`, relaunch | WorkManager run attempt 2 restarted the transfer; `Worker result SUCCESS`; 63,104,689-byte `.onnx` + `meta.json`; Settings showed `2 vozes · 120 MB`; sample loaded Sherpa model | R12 |
| 2026-10-21 | `.\gradlew.bat :app:connectedDebugAndroidTest` | `BUILD SUCCESSFUL`; 4 tests, 0 failures, 0 errors, 0 skipped in 58.067 s on Pixel_6 API 37 x86_64; XML `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_6(AVD) - 17.xml` | R9 |
| 2026-10-21 | `.\gradlew.bat :app:assembleRelease` | `BUILD SUCCESSFUL`; `verifyBundledVoice` passed first; unsigned release APK 243,412,796 bytes | R6, R10 |
| 2026-10-21 | `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug` | 552 tests, 0 failures, 0 errors, 1 skipped (30 suites); lint 91 findings, no fatal errors | R1-R8, R11 |
| 2026-10-21 | Pixel_6 API 37 manual device run | Home/tabs, Tugão playback, notification permission+channel, pause/resume with UI and media keys, slider seek, restart, speed change during playback, share from Settings, history load while playing, voice picker during playback | R1-R5, R9 |
| 2026-10-21 | `adb shell am instrument` playback test, 3 runs | 3/3 passed (13.9-21.5 s each); no `com.echoreading` SIGSEGV or synthesis errors in logcat | R9 |
| 2026-10-20 | `.\gradlew.bat :app:assembleRelease` | `BUILD SUCCESSFUL`; `verifyBundledVoice` ran first; unsigned release APK (no production signing config) | R6, R10 |
| 2026-10-20 | `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug` | 552 tests, 0 failures, 0 errors, 1 skipped (`OnnxMetadataTest.extractMetadataFromConfig`); lint task passed, 91 findings (mostly UnusedResources/UseKtx), no fatal errors | R1-R8, R11 |
| 2026-10-20 | `.\gradlew.bat :app:testDebugUnitTest :app:verifyBundledVoice :app:verifySherpaPackaging :app:lintDebug` | passed; APK 263,118,137 bytes; contains `assets/vits-piper-pt_PT-tugao-medium/pt_PT-tugao-medium.onnx` (63,201,425 bytes) | R5-R7 |
| 2026-10-20 | merged manifest inspection | `allowBackup=false`; `dataExtractionRules=@xml/data_extraction_rules` | R5 |
| 2026-10-20 | `.\gradlew.bat :app:fetchBundledVoice` | downloaded and verified the expected 63,201,425-byte model | R6 |
| 2026-10-20 | `.\gradlew.bat :app:verifyBundledVoice` | passed with the model present; previously failed with a clear `fetchBundledVoice` instruction when absent | R6 |
| 2026-10-20 | `.\gradlew.bat :app:verifySherpaPackaging` | passed; debug APK contains the voice `.onnx` | R6 |
| 2026-10-20 | baseline `:app:testDebugUnitTest` | 552 tests, 0 failures, 3 skipped | baseline |
| 2026-10-20 | baseline `:app:verifySherpaPackaging` | passed; APK 203,602,116 bytes; no voice `.onnx` | baseline |

The model's presence changed the JVM suite from 3 skipped to 1 skipped; two
model-dependent tests now execute instead of being skipped.

## Resume instructions

1. Read this file and `AGENTS.md`.
2. Run `git status --short` and inspect the current diff before touching files.
3. R1-R13 are complete. No in-repo remediation item is open. Remaining release
   steps are external: production keystore/signing, Play submission and
   target-API policy check, and vendoring or checksum-verifying the pinned
   JitPack Sherpa AAR. Optional follow-ups: an automated history-DB failure test
   and a Macrobenchmark frame-timing test.
4. Build with `.\gradlew.bat :app:testDebugUnitTest --console=plain`.
5. For device changes run `.\gradlew.bat :app:connectedDebugAndroidTest`
   with the Pixel_6 API 37 emulator booted.
6. For packaging changes also run
   `.\gradlew.bat :app:verifyBundledVoice :app:verifySherpaPackaging --console=plain`.
7. Update the status/verification table and append any new blocker. Do not mark
   an item `[x]` if the only evidence is "it compiles"; record the actual check.

## Known environment constraints

- `app/src/androidTest` exists; run it only with a booted emulator/device.
  Compose/Espresso is unusable on API 37 here, so tests use
  `ActivityScenario`/UIAutomator.
- The API 37 AVD repeatedly restarts `android.hardware`/UWB services with
  `Fatal signal 6`; this is emulator noise, but it fills logcat and can
  destabilize long runs. Cold-boot with `-no-snapshot` and free disk space. If
  system_server dies with `Failed to read runtime-permissions.xml`, wipe the AVD
  with `-wipe-data` and cold-boot; that restored this emulator on 2026-10-21.
- The 63 MB Tugão model is intentionally excluded from Git by `.gitignore:27`;
  run `fetchBundledVoice` in a clean checkout before a release build.
- The local machine is Windows; use `.\gradlew.bat`.
- A full clean build can take several minutes; keep several GB free for APKs
  and reports.
- All changes in this remediation pass are currently uncommitted on `dev`.
