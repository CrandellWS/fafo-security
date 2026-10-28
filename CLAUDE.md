# CLAUDE.md: FAFO Security build agent front door

FAFO Security ("Free Advanced Features Offline") is Bill Crandell's Android anti-theft app for the
owner's own phone. This repo is built by a Claude Code cloud session. It is a separate project,
unrelated to gwaslots.com.

Read in this order: `docs/SPEC-v1.md` (what to build, acceptance criteria), then
`docs/BUILD-PLAN.md` (milestones M0-M10, done-checks). Work milestones in order.

## Rules (hard)

- **No server code in v1.** No backend, no relay, no cloud functions. The app has no `INTERNET` permission.
- **No analytics, trackers, crash reporters or ad SDKs.** No Firebase, Crashlytics, Play Services, Sentry, etc.
- **No remote listening in v1.** No `RECORD_AUDIO`, no microphone foreground service type, no remote
  control of any kind. Phase two (spec section 10) is not built until Bill says so.
- **Own device only.** Never add hidden modes, disguised icons, or anything that monitors another person.
  The persistent notification must show whenever the app is armed or alarming.
- **Keep secrets out of the repo.** No keystores, signing configs, API keys or `local.properties`
  committed. Private keys live only in Android Keystore at runtime.
- **Scope changes go in the spec first.** If something in the spec is wrong or impossible on Android,
  update `docs/SPEC-v1.md` with the reason in the same commit as the code change.
- **One commit per milestone**, message `M<n>: <summary>`, ending with these trailers:

  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  ```

  Plus the `Claude-Session:` trailer for the current session if the environment provides one.

## Tech baseline

Kotlin, Jetpack Compose (Material 3), Gradle Kotlin DSL with a version catalog, JDK 17,
minSdk 30, targetSdk/compileSdk 36. Package `ws.crandell.fafo`.

## Build and test

```bash
# One-time per cloud session: install the Android SDK (see docs/BUILD-PLAN.md M0)
export ANDROID_HOME="$HOME/android-sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew test                 # JVM unit tests + Robolectric
./gradlew lint
```

No emulator is available in the cloud (no KVM). Keep logic in pure Kotlin classes and test them on the
JVM; use Robolectric for services, receivers, storage, manifest and Compose. Device-only behavior is
checked by Bill on his Samsung Galaxy A17 (Android 16) using the APK from CI
(`.github/workflows/android.yml`, artifact `fafo-debug-apk`).

## Android gotchas that shape the design

- Camera FGS can only start while the app is visible (arm tap) or from a notification tap; never from boot,
  never from a `START_STICKY` restart, and never before first unlock (CE storage is locked anyway).
- Direct boot: the `Application` class and every startup initializer must not touch CE storage or
  Keystore. No WorkManager.
- v1 has a pre-alarm countdown (0-60 s, default 5 s) between unplug and the full siren; see spec F2.
- Pairing uses commit-then-reveal before showing the 6-digit code (spec AC6.2).
- `ACTION_POWER_DISCONNECTED` must be registered at runtime inside the armed service.
- Boot path is `directBootAware` and reads only device-protected storage.
- Siren uses `AudioAttributes.USAGE_ALARM`; timers run inside the service (no exact alarms, no WorkManager while armed).
- Android 16: edge-to-edge forced, predictive back on (use `BackHandler`, never `onBackPressed`).
