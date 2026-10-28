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

## Open questions for Bill

- None yet.
