# RoomReset Mini

A lightweight, fully offline room-tidying organizer for Android. Pick a room template, start a
5-, 10- or 15-minute reset and check off small tasks grouped into five room zones. Completion is
reported by the user — the app never inspects rooms or verifies cleanliness.

Native Android: **Kotlin + Jetpack Compose**, `compileSdk 36`, `targetSdk 36`, `minSdk 26`.
No WebView, React Native, Expo or JavaScript.

## Features

- **Room plan main screen** — a simplified top-down plan with five labelled zones: Desk (upper
  edge), Floor (centre), Clothes (wardrobe on the side), Shelf (horizontal storage) and Trash (small
  bin near the lower edge). Each zone shows its icon, name, `completed/total` and *Not started /
  In progress / Done* (Done always has a checkmark **and** text, never colour alone).
- Template selector above the plan; duration chips + **Start Reset** below it before a session,
  replaced during a session by a compact timer strip (remaining time, Pause/Resume, Finish).
- Small utility row: History, Edit rooms, List view / Plan view, Settings. No dashboard.
- **Narrow screens / large fonts** switch automatically to a readable vertical zone list; the same
  list is available any time via *List view* (accessible equivalent of the illustration).
- Three editable starter templates (Bedroom, Study Space, Living Room), up to 12 templates.
- Create, rename, duplicate, delete, edit tasks, reorder tasks within a zone, explicit Save/Cancel.
- 5/10/15-minute timer with pause/resume, background continuation, restart recovery, expiry
  handling and *Continue without timer* ("Extra time").
- Finish at any time → review → Save Session or Continue Reset; partial and empty sessions can be
  saved; atomic save with duplicate-save prevention.
- Local history (latest 100), details per zone/task, delete one entry or clear all.
- Settings: default duration, sound (off by default), reduced decorative animation, restore starter
  templates, clear history, clear all local data, bundled privacy information.

Excluded by design: camera, photo scanning, microphone, location, accounts, backend, Firebase, ads,
analytics, payments, cloud sync, notifications, remote content, streaks, rankings, health claims.

## Architecture

One application module, manual dependency injection (`AppContainer`).

```
app/src/main/java/com/egrmeister/roomresetmini/
  data/local         Room entities, DAOs, AppDatabase + Migrations, Android clocks/boot reference, tone
  data/repository    TemplateRepository, SessionRepository, SettingsRepository (DataStore), DataResetter
  domain/templates   ZoneType, templates, validation, unique names, starter templates
  domain/sessions    Session model, immutable snapshot, results, history retention, SaveCoordinator
  domain/timer       TimerEngine (checkpoint-based), MonotonicClock / WallClock / BootReferenceProvider
  domain/progress    Zone and session progress
  ui/room            Room plan, timer strip, RoomViewModel (activity-scoped session owner)
  ui/zone            Zone checklist
  ui/editor          Room list + template editor
  ui/results         Finish review + saved result
  ui/history         History list + details
  ui/settings        Settings + privacy
  ui/theme           Customised Material 3 theme and shared components
```

Kotlin, Jetpack Compose, customised Material 3, Navigation Compose, ViewModel, StateFlow,
`collectAsStateWithLifecycle`, coroutines, Room (KSP), DataStore Preferences. All database work
uses suspend DAO functions / Flows executed by Room off the main thread. Every directly used library
is declared as a direct dependency (`gradle/libs.versions.toml`).

## Toolchain (pinned)

| Component | Version |
|---|---|
| Gradle (wrapper, committed) | 8.14.3 |
| Android Gradle Plugin | 8.11.1 |
| Kotlin / Compose compiler plugin | 2.2.0 |
| KSP | 2.2.0-2.0.2 |
| Compose BOM | 2025.07.00 |
| Room | 2.7.2 |
| DataStore | 1.1.7 |
| Navigation Compose | 2.9.3 |
| Lifecycle | 2.9.2 |
| JDK | 17 (Temurin in CI) |
| Build tools (CI checks) | 36.0.0 |

SDK: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`.

### Android 16 (API 36) behaviour changes handled

- **Edge-to-edge is mandatory**: `enableEdgeToEdge()`, Scaffold insets, `imePadding()` in the editor,
  cutouts handled by window insets. System bars stay visible; no immersive mode or keep-awake.
- **Predictive back** is on by default: `android:enableOnBackInvokedCallback="true"` and Compose
  `BackHandler` (only enabled while the template form has unsaved changes).
- **Orientation/resizability restrictions are ignored on large screens**: the app declares none and
  adapts layouts (two-pane room screen ≥ 840 dp, content width capped at 640 dp, rotation-safe state).
- No exact alarms, foreground services or notifications are used, so related restrictions don't apply.

## Builds

```bash
./gradlew testDebugUnitTest          # focused unit tests
./gradlew lintRelease                # release lint (abortOnError)
./gradlew assembleDebug              # debug build (debug key, applicationId suffix .debug)
./gradlew assembleRelease bundleRelease                         # signed, non-minified
./gradlew assembleRelease bundleRelease -ProomresetMinify=true  # signed, R8 + resource shrinking
```

Release outputs:

- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`
- R8 mapping (minified build): `app/build/outputs/mapping/release/mapping.txt`

CI copies verified artifacts to `dist/nonminified/` and `dist/play/` (artifact
`roomresetmini-release-<run>`). **Only `dist/play/RoomResetMini-1.0.0.aab` is uploaded to Google
Play.** The APK is for local installation and verification only.

## Templates, zones and progress

- Five fixed zone types: Desk, Floor, Clothes, Trash, Shelf (stable layout).
- A zone may contain zero tasks: it shows “No tasks” and is excluded from completion totals.
- Validation: template name 1–30 trimmed characters, unique ignoring case; task label 1–80 trimmed
  characters; up to 10 tasks per zone; at least one task per template; up to 12 templates.
- Template changes apply to future sessions only.
- A zone is **Done** only when all its tasks are complete; undoing a task recalculates its state.
  Tapping a zone opens its checklist — it never marks tasks complete.

Formulas:

```
zone state      = No tasks (total = 0) | Not started (0 done) | In progress | Done (done = total)
task progress   = completed tasks / total tasks          (every task weighs the same)
zones done      = completed non-empty zones / non-empty zones   (shown separately, not used for %)
```

## Timer lifecycle and recovery

The countdown is never decremented in storage. A session stores a **checkpoint**:
`timerStatus`, `remainingAtCheckpointMillis`, `checkpointElapsedRealtimeMillis` (from
`SystemClock.elapsedRealtime()`), `bootReference` (`Settings.Global.BOOT_COUNT`) and
`continuedWithoutTimer`.

```
remaining = remainingAtCheckpoint − (elapsedRealtimeNow − checkpointElapsedRealtime), clamped at 0
```

- **Start**: RUNNING from the selected duration (default 10 min).
- **Pause**: stores the remaining time; elapsed time stops accumulating. **Resume** sets a new checkpoint.
- **Background (same boot)**: the timer continues logically, no background service. A fresh
  checkpoint is saved on `ON_STOP` and every 30 s while visible; on return the remaining time is
  recalculated from the checkpoint (it may now be expired).
- **Process recreation**: the running or paused state is restored from Room, and the app offers
  *Resume / Finish and save / Discard* (discard asks for confirmation). An unfinished session is
  never silently overwritten — only one can exist.
- **Reboot or invalid monotonic reference** (different boot id, or `elapsedRealtime` earlier than
  the checkpoint): the session is restored **paused** with its latest saved remaining duration and
  the room screen explains “The timer was paused after a restart”.
- **Wall-clock changes** never affect the countdown (wall time is used only for history timestamps).
- **Expiry**: “Time is up. Save your progress or keep going.” Task states are preserved, nothing is
  auto-completed, the session is kept. Options: *Save session* or *Continue without timer*
  (“Extra time”, no overtime score or penalty).
- No notifications, exact alarms, foreground services or background sounds. The optional short
  tone plays only if sound is enabled and the app is visible at the moment of expiry; no delayed
  sound after returning from the background. The countdown is not announced tick by tick.

## Partial results and history

- Finish is available at any time and shows: template name, selected duration, completed tasks out
  of total, completed zones, remaining tasks, *Save Session*, *Continue Reset*.
- Saving is one Room transaction (`ACTIVE → SAVED` conditional update + retention) serialised by
  `SaveCoordinator`, so double taps or retries never create duplicate entries.
- After saving: “All tasks checked.” (everything done), “Progress saved.” (some done) or
  “Session saved. No tasks were checked.” Sessions without checked tasks can also be discarded.
  Nothing is ever marked complete on the user's behalf.
- History keeps the latest **100** saved sessions. Rows show date/time, template-name snapshot,
  selected duration, completed/total and *All checked* or *Partial*. Details show saved zones and
  task states. Sessions store their own snapshot, so editing or deleting templates never rewrites
  history. Delete one entry or clear all (with confirmation). No speed comparisons or rankings.

## Offline operation, privacy, storage and backup

- No `INTERNET` or `ACCESS_NETWORK_STATE`; no runtime permissions; no camera, microphone, storage
  or location access; no networking libraries, accounts, Firebase, ads, analytics or payments.
- Templates, sessions and history are stored in Room (`roomreset.db`), preferences in DataStore —
  both in app-private storage.
- `android:allowBackup="false"`, `fullBackupContent` and `dataExtractionRules` exclude all domains
  from cloud backup and device-to-device transfer.
- Bundled privacy information: Settings → Privacy information.
- Starter templates are seeded once (DataStore flag); deleted templates are not recreated. *Clear
  all local data* wipes everything in one transaction and restores starter templates and defaults.
- Room schemas are exported to `app/schemas/` and committed; future versions add explicit
  migrations in `data/local/AppDatabase.kt` (`Migrations.ALL`). No destructive migration.

## Permission verification

The manifest declares no permissions (no `tools:node="remove"` placeholders either — lint treats a
CAMERA entry as implying camera hardware). CI fails if:

- the packaged APK (`aapt2 dump permissions`) contains any `uses-permission` other than the
  AndroidX-internal `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`;
- the **merged** release manifest contains any other permission;
- the packaged manifest has `allowBackup` ≠ false or is debuggable.

## Signing (PKCS12) and GitHub Secrets

The release keystore is PKCS12 (RSA 4096, 10 000 days) and lives **outside** the repository
(`~/Desktop/APPS Console/_keys/RoomResetMini/`). Keystores, passwords, Base64 and signing property
files are git-ignored and never printed by CI.

`app/build.gradle.kts` sets `signingConfig = signingConfigs.getByName("release")` for the release
build type. If credentials are missing, `assembleRelease`, `bundleRelease` and other release
packaging tasks **fail** — there is no fallback to the debug key.

Repository secrets (Settings → Secrets and variables → Actions):

| Secret | Content |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64 of the `.p12` file, one line |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key password (PKCS12 uses the same value as the store password) |

CI decodes the keystore into `$RUNNER_TEMP`, builds, runs `apksigner verify --print-certs`, fails on
verification errors or `CN=Android Debug`, verifies AAB integrity and signer with `jarsigner` +
`keytool -printcert` (APK and AAB signer must match), and deletes the keystore in an `always()`
step. A self-signed upload certificate is valid for Google Play and is not rejected.

Local release builds pick credentials from environment variables, from
`$ROOMRESET_SIGNING_PROPERTIES`, or from `../_keys/RoomResetMini/signing.properties`.

## 16 KB page size findings

RoomReset Mini uses only Kotlin/Java and AndroidX libraries. CI lists every `.so` in the APK and AAB
(including transitive dependencies), checks ELF `LOAD` segment alignment (≥ 2**14) for any native
library found, and runs `zipalign -c -P 16 -v 4` on the APK. The per-build result is in the CI job
summary and in `dist/*/checks/native-libs.md`. Expected finding: **no native libraries**, so ELF
16 KB alignment does not apply. Runtime compatibility is claimed only after testing on a 16 KB
environment (see pending checks) — API 36 alone does not prove it.

## Local installation and logcat verification

```bash
adb devices
adb install -r dist/nonminified/RoomResetMini-1.0.0-nonminified.apk   # or dist/play/RoomResetMini-1.0.0.apk
adb shell getconf PAGE_SIZE                                            # 16384 on a 16 KB device/emulator
adb logcat -c
adb shell monkey -p com.egrmeister.roomresetmini -c android.intent.category.LAUNCHER 1
adb logcat --pid=$(adb shell pidof -s com.egrmeister.roomresetmini) '*:W'
adb shell dumpsys package com.egrmeister.roomresetmini | grep -i permission
```

Manual checklist: offline launch (airplane mode), template editing, 5/10/15-minute timers, zone
checklists, expiry, partial saving, history, process recreation (`adb shell am kill` while in
background), rotation, accessibility (TalkBack, 200 % font), reset.

## Verification record

| Check | Result |
|---|---|
| Unit tests, release lint, signed non-minified + R8 builds, apksigner/jarsigner, permissions, 16 KB packaging | CI (see job summary) |
| Release artifact size | CI job summary (`dist/release-report.md`) |
| adb install + logcat on a device/emulator | **Pending** |
| 16 KB runtime test | **Pending** |
| Manual checklist above | **Pending** |

Unperformed checks stay marked as pending until they are actually run.
