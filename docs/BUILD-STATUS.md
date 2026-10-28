# FAFO Security: build status

Updated after every milestone. Source of truth for what is done: this file plus `git log`.

## Environment (M0)

Built on Bill's workstation (Ubuntu, no sudo used), not a cloud container:

- JDK: Temurin 17.0.21 unpacked to `~/.local/opt/jdk-17` (no apt, no sudo).
- Android SDK: `~/android-sdk` with cmdline-tools 13114758, `platform-tools` 37.0.1,
  `platforms;android-36` (r2), `build-tools;36.0.0`.
- Gradle: 8.14.3 (wrapper committed in M1). AGP 8.13.2, Kotlin 2.2.21, Compose BOM 2025.10.01,
  Robolectric 4.16 (supports SDK 36). Newer AGP 9.x exists but changes the Kotlin plugin model; held at 8.13 on purpose.
- Network: `dl.google.com`, `maven.google.com`, `services.gradle.org`, `plugins.gradle.org`,
  `repo.maven.apache.org` all reachable (HTTP 200). Nothing blocked.
- No emulator (no device work here); device checks go to `docs/DEVICE-TESTS.md`.

## Environment (M9 on, cloud session 2026-10-28)

M9-M13 are built in a Claude Code cloud container, not on Bill's workstation:

- **Blocked by the environment's network policy:** `dl.google.com` (HTTP 403 from the egress proxy on
  CONNECT). That host serves the Android SDK (`sdkmanager`, platforms, build-tools) and Google's Maven
  repository (`maven.google.com` redirects there: AGP, AndroidX, Compose). So `./gradlew assembleDebug`,
  `testDebugUnitTest` and `lint` **cannot run in this container**. To allow it: add `dl.google.com` (and
  `maven.google.com`) to the cloud environment's allowed hosts.
- Reachable: `services.gradle.org`, `plugins.gradle.org`, `repo.maven.apache.org` (with occasional
  HTTP 429 rate limits), so the Gradle wrapper and Maven Central artifacts download.
- What is done instead, per milestone: (1) a local JVM harness (outside the repo) compiles every pure
  Kotlin file and runs its JUnit tests on JDK 21 (crypto, pairing, handshake, circles, policies), and
  type-checks the Android-framework files (BLE, NSD, sockets, Keystore) against Robolectric's
  `android-all` 16 jar from Maven Central; (2) the full `assembleDebug test checkNoRemoteDeps lintDebug`
  runs in GitHub Actions on a scratch branch (`ci/work`, `workflow_dispatch`) until green; (3) only then
  the milestone is committed to `main` as one commit, and its own `main` run must go green before the
  next push.

## Milestones

| Milestone | State | Tests |
|---|---|---|
| M0 Environment | done | `sdkmanager --list_installed` shows platform 36 + build-tools 36.0.0; `java -version` 17 |
| M1 Gradle + Compose skeleton | done | `./gradlew assembleDebug test` green; APK at `app/build/outputs/apk/debug/app-debug.apk` |
| M2 State machine + DP/CE storage | done | 26 JVM tests (every spec transition, all sensitivities and pre-alarm values, boot cases) + Robolectric DpStore read-back from the device-protected dir. Robolectric pinned to SDK 35 (`app/src/test/resources/robolectric.properties`): android-all for SDK 36 needs Java 21 and the build is JDK 17. Bug caught by the test: `preferencesDataStoreFile()` resolves through `applicationContext` and would have put the DP store in CE storage; the path is now built from the DP context directly. |
| M3 UI and onboarding | done | 6 Robolectric Compose tests: Arm disabled unplugged / without screen lock, enabled when plugged; settings show spec defaults; onboarding checklist reflects granted/missing permissions. Incidents, Trusted devices, Circles, Intercom screens are placeholders until M8-M12. |
| M4 Armed foreground service | done | 55 tests total. Robolectric: ArmService starts foreground `specialUse|camera` (specialUse only without camera permission), armed notification with Disarm action + delete intent, receivers registered, unplug + window + locked keyguard reaches PRE_ALARM / TRIGGERED, reconnect inside the window does not, unlock stops the alarm. ManifestTest, NoRemoteEndpointsTest. `checkNoRemoteDeps` wired into `check`; verified it fails with a temporary okhttp dependency (removed). Release-variant unit tests disabled (Compose test manifest is debug-only); debug runs every test. |
| M5 Siren | done | JVM: siren starts on trigger at max alarm volume and loops, stops on unlock, ignores dismiss and replug, pre-alarm beeps follow `PreAlarmSchedule` at lower gain and stop on replug, volume watchdog, DND refusal reported, chirps quieter and muted under the siren. Robolectric: `USAGE_ALARM` + `CONTENT_TYPE_SONIFICATION`, AudioTrack backend plays. Tones (Klaxon, Wail, Pulse) generated in code. Device checks in `docs/DEVICE-TESTS.md` (renamed from A17-TEST-CHECKLIST.md per Bill). |
| M6 Resume incident after reboot | done | Robolectric: boot with incident flag starts ArmService (ACTION_BOOT), which resumes the full siren as INCIDENT_PERSISTED with `specialUse` only and no second countdown; no flags means nothing starts; boot while armed + unplugged + locked runs the window then pre-alarm; armed + plugged shows "open the app to enable photos"; sticky restart (null intent) takes the same path; a CeStore that throws if opened is never touched; BootReceiver, ArmService, AlarmActivity are directBootAware. CI M5 run green with `fafo-debug-apk` artifact. |
| M7 Motion chirps | done | JVM synthetic 50 Hz traces at Low/Medium/High: rest quiet, hard table bump = BUMP everywhere, gentle bump only on High, pick-up = PICKUP (no bump), walking = PICKUP, vibrating surface ignored; 1.5 s chirp rate limit. Robolectric: accelerometer listens only while ARMED (stops in TRIGGERED), not when motion is Off. |
| M8 Trigger photos and offline queue | done | 100 tests total. PrunePolicy: never deletes protected frames (first, latest, up to 20 big-change), respects the cap, oldest closed incident first, thins the current incident every other frame. PhotoStore JVM tests (pending status, close label keeps photos, cap pruning). PhotoController: first shot immediately then every N s, alternate lenses, failures logged and retried, low storage pauses photos while the siren keeps going (state machine + fake capturer), Off setting. Robolectric: trigger writes the first photo into `filesDir/incidents/<id>/` and unlock closes the incident. Incidents screen with viewer, delete, share sheet (FileProvider, user-initiated only). |

| M9 Trusted-device pairing | done | Reused the sound parts of `wip/m9-pairing` (Crypto helpers, `PairingCode`, commit-then-reveal, `KeystoreIdentityKeys`, `BiometricCredentialGate`, `TrustedDeviceStore`); split Android code from pure code and added the missing pieces: `PairingProtocol` (both roles over a `MessageLink`, both owners' credential, nothing stored unless both confirm), GATT server/client links with chunking, BLE advertise/scan (API 30 via CompanionDeviceManager), Trusted devices screen. JVM: code is 6 digits, identical on both sides, changes with any key (identity or command) or nonce; a reveal not matching the commitment aborts; full exchange over an in-memory link stores both peers with both public keys; refused credential, no secure lock, code mismatch and peer decline store nothing; remove needs the credential; chunker round trips. Robolectric: manifest has the Bluetooth permissions, `neverForLocation` on `BLUETOOTH_SCAN`, legacy Bluetooth capped at API 30, no location; Trusted screen lists name + fingerprint, asks for Nearby devices, code dialog answers. Spec AC6.1 note: one credential prompt per pairing, right after the code comparison. CI (ci/work run 14) green. |

## Open questions for Bill

- Allow `dl.google.com` / `maven.google.com` in the cloud environment so later sessions can build and lint
  locally instead of only in GitHub Actions.
