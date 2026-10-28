# CLAUDE.md: FAFO Security build agent front door

FAFO Security ("Free Advanced Features Offline") is Bill Crandell's Android anti-theft app for the
owner's own phone. This repo is built by a Claude Code cloud session. It is a separate project,
unrelated to gwaslots.com.

Read in this order: `docs/SPEC-v1.md` (what to build, acceptance criteria), then
`docs/BUILD-PLAN.md` (milestones M0-M13, done-checks). Work milestones in order. Manual checks on
Bill's phones never block the next milestone: list them in `docs/DEVICE-TESTS.md` and move on.

## Rules (hard)

- **No server of ours, no analytics.** No backend, relay or cloud functions. Network traffic goes only to
  (a) the owner's paired devices on the LAN/Bluetooth (spec F9) and (b) **opt-in, off by default** photo
  email (spec F11): the user's own SMTP server (both flavors) or the Gmail API with the user's Google
  account (play flavor only). Nothing else. Enforced by `LocalAddressPolicy` (peer traffic: private
  addresses only), `NoRemoteEndpointsTest` (no URL or host literals in main/oss; play may hold only the
  Gmail endpoint and scope), `EgressTest` (where sockets may be opened), the per-flavor Gradle task
  `checkNoRemoteDeps` and the manifest test. No message type may arm, start room mode, open a mic or change
  circles on the receiving device.
- **No analytics, trackers, crash reporters or ad SDKs.** No Firebase, Crashlytics, Sentry, etc. Play Services:
  none in the `oss` flavor; the `play` flavor may use only the allowlisted `play-services-auth` modules
  (Gmail sign-in), enforced per flavor by `checkNoRemoteDeps`.
- **Intercom is open-use only (spec F10).** The mic opens only in room mode turned on by the person at that
  phone (persistent notification naming listeners, system mic indicator) or while someone holds
  push-to-talk in the visible app. Never started by a remote message, from boot or from a sticky restart;
  nothing is recorded. An alarm (PRE_ALARM/TRIGGERED) ends room mode and drops listeners. No remote listening during alarms and nothing over the internet: that is phase
  two (spec section 10) and is not built until Bill says so. Never call it a baby monitor.
- **Every network message is end-to-end encrypted and authenticated** with the paired keys (spec AC9.4);
  each circle's permissions are checked by the receiving device (spec F8). Reset and Disarm are separate
  permissions (Reset never disarms) and are signed with the user-authentication-bound command key (AC9.8).
- **Own device only.** Never add hidden modes, disguised icons, or anything that monitors another person.
  A persistent notification must show whenever the app is armed, alarming, reachable or in intercom room mode.
- **SMS reset is not built** until Bill approves its one Play Services library (spec section 9a).
- **Keep secrets out of the repo.** No keystores, signing configs, API keys or `local.properties`
  committed, except the PUBLIC shared debug keystore in `keystore/` (debug builds only, so CI and local
  APKs install over each other; guarded by `RepoSecretsTest`). The release key never enters the repo. Private keys live only in Android Keystore at runtime.
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

# Two flavors: oss (default; F-Droid, no Google libraries) and play (adds Gmail sign-in).
./gradlew assembleOssDebug assemblePlayDebug  # APKs: app/build/outputs/apk/{oss,play}/debug/
./gradlew test                 # testOssDebugUnitTest (full suite) + testPlayDebugUnitTest (flavor subset)
./gradlew lintOssDebug lintPlayDebug
./gradlew checkNoRemoteDeps    # per-flavor dependency guard
./gradlew recordRoborazziOssDebug # screenshots of every screen: app/build/outputs/roborazzi/*.png
python3 tools/make-icons.py <shield.png>  # regenerate launcher icon / logo bitmaps (Pillow)
```

No emulator is available in the cloud (no KVM). Keep logic in pure Kotlin classes and test them on the
JVM; use Robolectric for services, receivers, storage, manifest and Compose. Device-only behavior is
checked by Bill on his Samsung Galaxy A17 (Android 16) using the APK from CI
(`.github/workflows/android.yml`, artifacts `fafo-debug-apk-oss` and `fafo-debug-apk-play`, both signed with
the shared debug key in `keystore/`).

## Android gotchas that shape the design

- Camera FGS can only start while the app is visible (arm tap) or from a notification tap; never from boot,
  never from a `START_STICKY` restart, and never before first unlock (CE storage is locked anyway).
- Direct boot: the `Application` class and every startup initializer must not touch CE storage or
  Keystore. No WorkManager.
- v1 has a pre-alarm countdown (0-60 s, default 5 s) between unplug and the full siren; see spec F2.
- Pairing uses commit-then-reveal before showing the 6-digit code (spec AC6.2).
- Any socket needs `INTERNET`, even on the LAN. Android 16 has an opt-in local network restriction
  (`NEARBY_WIFI_DEVICES`); Android 17 / targetSdk 37 needs `ACCESS_LOCAL_NETWORK` (spec AC9.10).
- Microphone foreground services cannot start from boot or the background; `connectedDevice` can start
  from `BOOT_COMPLETED`. Keystore key agreement needs API 31, so ECDH keys are ephemeral software keys
  signed by the Keystore identity key.
- `ACTION_POWER_DISCONNECTED` must be registered at runtime inside the armed service.
- Boot path is `directBootAware` and reads only device-protected storage.
- Siren uses `AudioAttributes.USAGE_ALARM`; timers run inside the service (no exact alarms, no WorkManager while armed).
- Android 16: edge-to-edge forced, predictive back on (use `BackHandler`, never `onBackPressed`).
