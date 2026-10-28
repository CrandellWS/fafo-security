# FAFO Security: v1 Build Plan

For a Claude Code cloud session. Read `CLAUDE.md` and `docs/SPEC-v1.md` first. Work the milestones in
order; each ends with its done-checks passing and one commit. Do not start a milestone until the
previous one's automated checks pass. Items marked "Manual (Bill)" need real phones, which the cloud
session does not have: add them to `docs/A17-TEST-CHECKLIST.md` (create it at M5, finish it at M13)
and carry on; they never block the next milestone.

## M0. Environment: Android SDK in the cloud session

The cloud container has no Android SDK by default. Install it once per session (cache the dir if the
environment allows):

Network: the build downloads from `dl.google.com`, `maven.google.com`, `services.gradle.org`,
`plugins.gradle.org` and `repo.maven.apache.org` (Robolectric also fetches its `android-all` jars from
Maven Central at test time). If the cloud environment's network access is limited and these fail, stop
and tell Bill to allow them in the environment settings; do not work around it.

```bash
# JDK 17 (check first: java -version). Use sudo only if not already root.
sudo apt-get update && sudo apt-get install -y openjdk-17-jdk-headless unzip curl

export ANDROID_HOME="$HOME/android-sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"
cd /tmp
# Pick the current "commandlinetools-linux-*_latest.zip" from https://developer.android.com/studio#command-tools
curl -fsSLo cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
unzip -q cmdtools.zip -d "$ANDROID_HOME/cmdline-tools"
mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

yes | sdkmanager --licenses >/dev/null
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
echo "sdk.dir=$ANDROID_HOME" > local.properties   # local.properties is gitignored
```

If the zip URL 404s, read the current file name from the Android Studio download page. No emulator
in the cloud session: there is no KVM. Device behavior is verified by Bill on the A17 using the CI APK.

Gradle wrapper bootstrap (needed once, for M1, since no `gradlew` exists yet):

```bash
GRADLE_VERSION=8.14.3   # use the version the chosen AGP requires
curl -fsSLo /tmp/gradle.zip "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
unzip -q /tmp/gradle.zip -d /tmp/gradle-dist
# after settings.gradle.kts exists, in the repo root:
/tmp/gradle-dist/gradle-${GRADLE_VERSION}/bin/gradle wrapper --gradle-version "$GRADLE_VERSION"
```

Done-checks: `sdkmanager --list_installed` shows `platforms;android-36` and `build-tools;36.0.0`;
`java -version` reports 17.

## M1. Project skeleton

- Gradle Kotlin DSL, `settings.gradle.kts`, `gradle/libs.versions.toml` version catalog, Gradle wrapper
  committed (`gradle/wrapper/gradle-wrapper.jar` + `.properties`, `gradlew`, `gradlew.bat`).
- Single `:app` module, package `ws.crandell.fafo` (change only if Bill says), `minSdk 30`,
  `targetSdk 36`, `compileSdk 36`, Kotlin JVM target 17, Compose enabled (Kotlin Compose compiler plugin).
- Dependencies (pin exact stable versions in the catalog): Compose BOM + Material 3, Activity Compose,
  Lifecycle (runtime, service, viewmodel-compose), DataStore Preferences, CameraX (core, camera2,
  lifecycle), AndroidX Biometric, Kotlin coroutines. Test: JUnit 4, Robolectric, kotlinx-coroutines-test,
  Truth or kotlin.test, Compose ui-test-junit4 (Robolectric). Use a Robolectric release that supports
  SDK 36; if it does not, pin tests with `@Config(sdk = [35])` and note it in the commit.
- No network or analytics libraries. No Firebase, no Crashlytics, no Play Services.
- Manifest: `android:allowBackup="false"`, `dataExtractionRules` excluding everything, no `INTERNET`.
- Empty Compose `MainActivity` with Home screen placeholder, edge-to-edge (Android 16 forces it),
  predictive back via `OnBackPressedDispatcher` / `BackHandler` (never `onBackPressed`).
- One trivial unit test so `test` runs.

Done-checks: `./gradlew assembleDebug test` succeeds; `app/build/outputs/apk/debug/app-debug.apk` exists;
CI workflow goes green on push (once Bill pushes). Commit: `M1: Gradle + Compose skeleton`.

## M2. State model and storage

- `AlarmState` sealed type: Disarmed, Armed, PreAlarm(endsAt), Triggered, IncidentPersisted (spec section 2).
- Pure Kotlin `AlarmStateMachine` with events (ArmRequested(plugged, secureLock), PowerDisconnected(t),
  PowerConnected(t), WindowElapsed(locked), PreAlarmElapsed, UserPresent, DisarmTapped,
  BootCompleted(armed, incidentActive, plugged, locked), NotificationTapped(ceUnlocked)). No Android
  types inside, so it is fully unit-testable. Inject a clock.
- `PreAlarmSchedule` (pure Kotlin): beep times for a given duration, interval non-increasing.
- `DpStore` (device-protected DataStore via `createDeviceProtectedStorageContext()`) for armed,
  incident flags and DP settings; `CeStore` for the rest. Settings defaults exactly per spec section 4.
- Write `incident_active=true` on entering PreAlarm (or Triggered when pre-alarm is 0 s), before any sound.

Done-checks: unit tests cover every transition in the spec table, including refused arm when unplugged
or with no secure lock, reconnect inside the window, reconnect during PreAlarm (back to Armed) vs during
Triggered (siren continues), pre-alarm 0 s, boot with/without an active incident, and boot while armed
and unplugged. Robolectric test that DpStore
reads back from the device-protected context. Commit: `M2: state machine and DP/CE storage`.

## M3. Compose UI and onboarding

- Screens: Home (big Arm/Disarm, plugged indicator, state, onboarding checklist), Settings, Incidents
  (list + photo viewer + delete + share sheet), Trusted devices, Onboarding flow (spec F7).
- Arm button disabled unless plugged (live from `ACTION_BATTERY_CHANGED` via a registered receiver in the
  ViewModel scope).
- Onboarding deep links: notification permission, camera, full-screen intent
  (`canUseFullScreenIntent()` + `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`), DND access
  (`ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`), battery optimization, Samsung battery steps (text +
  `ACTION_APPLICATION_DETAILS_SETTINGS`), built-in theft protection info, secure-lock check
  (`ACTION_SECURITY_SETTINGS`) and the Smart Lock / Extend Unlock warning.

Done-checks: Robolectric Compose tests: Arm disabled when unplugged and enabled when plugged; settings
show spec defaults; onboarding checklist reflects granted/missing permissions. Commit: `M3: UI and onboarding`.

## M4. Arm service

- `ArmService : LifecycleService`, `foregroundServiceType="specialUse|camera"` in the manifest with
  `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="Anti-theft alarm: detects charger removal and sounds a siren on the owner's own device; must resume from boot"/>`.
- Started only from the Arm tap (app visible) via `startForegroundService`, then
  `startForeground(id, notification, SPECIAL_USE or CAMERA)` within 5 s. If camera permission is missing,
  start with `SPECIAL_USE` only.
- Ongoing notification on a high-importance channel with Disarm action
  (`setAuthenticationRequired(true)` on API 31+ so it needs unlock; on API 30 the action opens the app,
  which needs unlock). Delete intent re-posts the notification if the user swipes it (Android 14+ allows
  that while unlocked). `START_STICKY`; a sticky restart uses `SPECIAL_USE` only.
- Arm refused unless plugged and `KeyguardManager.isDeviceSecure()`.
- Registers `ACTION_POWER_DISCONNECTED`/`ACTION_POWER_CONNECTED` at runtime; sensitivity timer via
  coroutine `delay` in the service (no AlarmManager); re-check plugged state from sticky
  `ACTION_BATTERY_CHANGED` and keyguard via `KeyguardManager.isDeviceLocked`.
- Registers `ACTION_USER_PRESENT` for owner unlock.
- Partial wake lock only during the sensitivity window, PreAlarm and Triggered.

Done-checks: Robolectric: service starts foreground with correct types; receivers registered; simulated
disconnect + elapsed window + locked keyguard drives the state machine to Triggered; reconnect inside the
window does not. Manifest test asserts no `dataSync`, no exact alarm permissions, no location, and (until
their milestones) no `INTERNET` (added in M10) and no `RECORD_AUDIO` / `FOREGROUND_SERVICE_MICROPHONE`
(added in M12), no `FOREGROUND_SERVICE_CONNECTED_DEVICE` (added in M11). Add the "no remote endpoints" guard now so it holds for every later milestone:
`NoRemoteEndpointsTest` scans `app/src/main` for `http(s)://`, `ws(s)://`, `ftp://` and public IP literals
(allowlist: XML namespace URIs only). Add the Gradle task `checkNoRemoteDeps` in `app/build.gradle.kts`
(spec section 6: walks the resolved `debugRuntimeClasspath` / `releaseRuntimeClasspath` and fails on
OkHttp, Retrofit, Ktor, Cronet, Firebase, Play Services, Sentry or ad SDK groups), make `check` depend on
it, and run it once by hand to see it fail on a temporary denied dependency. CI runs it when it exists.
Commit: `M4: armed foreground service and unplug detection`.

## M5. Siren

- `Siren` wrapper around `MediaPlayer` or `AudioTrack` with `AudioAttributes.USAGE_ALARM`,
  `CONTENT_TYPE_SONIFICATION`, looping, bundled asset in `res/raw` (generate a royalty-free tone in code or
  ship an original sound; no third-party audio without a license).
- Pre-alarm warning: same `USAGE_ALARM` path at lower gain, beeps on the `PreAlarmSchedule`,
  notification shows the countdown; cancelled by PowerConnected or UserPresent.
- Request audio focus `AUDIOFOCUS_GAIN` (works because a FGS is running). Set `STREAM_ALARM` to max when
  "Force max alarm volume" is on; catch `SecurityException` under DND without policy access and surface
  it in the onboarding checklist. Volume watchdog restores alarm volume while triggered.
- Vibration pattern alongside.
- Full-screen alarm activity (`showWhenLocked`, `turnScreenOn`) posted via full-screen intent when allowed.

Done-checks: unit tests on Siren with a fake audio backend (starts on trigger, stops on UserPresent,
ignores notification dismiss; pre-alarm beeps follow the schedule and stop on PowerConnected);
Robolectric test that audio attributes usage is `USAGE_ALARM`.
Manual on A17 (Bill, from CI APK): siren in silent mode, screen off. Commit: `M5: siren`.

## M6. Persistence across reboot

- `BootReceiver` with `android:directBootAware="true"`, `android:exported="true"` (system broadcasts only),
  intent filters for `LOCKED_BOOT_COMPLETED` and `BOOT_COMPLETED`. `ArmService` and the siren code path
  are also `directBootAware`.
- On boot: read `incident_active` from DpStore. If true, start `ArmService` with `SPECIAL_USE` only and
  resume the full siren (state IncidentPersisted, no countdown). If only `armed` is true: start as armed
  (`SPECIAL_USE` only, no camera); if the phone is unplugged and locked, feed PowerDisconnected so the
  normal window + pre-alarm runs. Show "Armed after reboot, open the app to enable photos".
- Idempotent: `BOOT_COMPLETED` after the first unlock re-reads the flags and does nothing if the service
  is running or nothing is set.
- The alarm activity is `directBootAware`, `showWhenLocked`, `turnScreenOn`. A tap on it or on the alarm
  notification re-adds the camera type only when CE storage is unlocked (`UserManager.isUserUnlocked()`),
  that is after a process death, never in direct boot.
- Nothing in the boot path touches CE storage or Keystore, including the `Application` class and
  `androidx.startup` initializers. No WorkManager dependency.

Done-checks: Robolectric: boot broadcast with incident flag starts the service with SPECIAL_USE only and
Siren started; without the flag, nothing starts; boot path code does not reference CeStore (test with a
CeStore that throws if opened). Commit: `M6: resume incident after reboot`.

## M7. Motion chirps

- `MotionClassifier` (pure Kotlin) on accelerometer samples: low-pass gravity estimate, high-pass for
  bumps; bump = short peak above threshold; pick-up = gravity vector angle change > ~25 degrees sustained
  ~0.5 s or sustained linear acceleration. Thresholds per Low/Medium/High.
- Chirp player shares the `USAGE_ALARM` path at lower gain; 1.5 s rate limit. Off in Triggered.

Done-checks: unit tests with synthetic sample traces (rest, bump, pick-up, walking, vibrating table)
classify as expected for each sensitivity; rate limit test. Commit: `M7: motion chirps`.

## M8. Camera and offline photo queue

- `PhotoCapturer` binds CameraX `ImageCapture` (no Preview) to the service lifecycle with
  `ProcessCameraProvider`; front/back/alternate; first shot within 2 s of trigger, then every N s
  (default 10) via coroutine ticker in the service.
- `PhotoStore` writes JPEG (max 1600 px long edge, quality 80) + sidecar JSON to
  `filesDir/incidents/<incidentId>/`. Status "pending" (never sent in v1).
- `PrunePolicy` (pure Kotlin) implements spec section 5: cap, protected first/latest/big-change (max 20
  per incident, grayscale diff on a 32x32 thumbnail), prune order, low-storage stop at 500 MB free.
- Incidents screen wired to PhotoStore.

Done-checks: unit tests for PrunePolicy (never deletes protected frames, respects cap, prunes oldest
closed incident first, thins current incident); low-storage stops photos but not the siren
(state-machine + fake capturer test). Commit: `M8: trigger photos and offline queue`.

## M9. Trusted-device pairing

- `CredentialGate` around `BiometricPrompt` with `setAllowedAuthenticators(DEVICE_CREDENTIAL)`; check
  `BiometricManager.canAuthenticate(DEVICE_CREDENTIAL)` first.
- `IdentityKeys`: Keystore keypair (EC P-256 signing key, non-exportable), public key export. Also the
  **command key** (spec AC9.8): EC P-256, `setUserAuthenticationParameters(0, AUTH_DEVICE_CREDENTIAL or
  AUTH_BIOMETRIC_STRONG)`. Both public keys go into the pairing exchange and the 6-digit code.
- BLE: one side advertises a FAFO service UUID (`BLUETOOTH_ADVERTISE`), the other scans
  (`BLUETOOTH_SCAN` with `neverForLocation` on API 31+; `CompanionDeviceManager` on API 30, which would
  otherwise need location) and connects (`BLUETOOTH_CONNECT`). Exchange via GATT with commit-then-reveal
  (spec AC6.2): responder sends `SHA-256(pkB || nB)`, initiator sends `pkA || nA`, responder reveals,
  initiator verifies. `PairingCode.derive(pkA, pkB, nA, nB)` = 6 digits from SHA-256 of the role-ordered
  inputs; both users confirm; both sides pass CredentialGate; store peer identity + command public keys,
  name and fingerprint in CE storage.
- Remove device also behind CredentialGate.
- Pairing grants no powers by itself (spec AC6.6); M10 adds circles. Until then the device is stored as
  trusted with no circle. Leave a hook after the code match for M10's first `CircleInvite` on the same link.

Done-checks: unit tests: PairingCode is symmetric, 6 digits, changes when any input changes; a reveal
that does not match the commitment aborts; pairing flow refuses to persist without a credential success (fake gate); manifest has `neverForLocation`
and legacy BT permissions capped at `maxSdkVersion=30`, and no location permissions. Manual on two
phones (Bill). Commit: `M9: trusted-device pairing`.

## M10. Trust circles, Wi-Fi transport, remote reset and disarm

Spec F8, F9 (Wi-Fi parts) and section 2 (remote reset / disarm).
- `CirclePolicy` + `CircleStore` (CE): permissions Alerts / Reset / Disarm / Intercom; built-in Home
  (all four), new circles Intercom only, "Work" suggestion Intercom + Reset; labels unique per circle with
  suggestion-collision suffix; `CredentialGate` on add member, grant Reset/Disarm, accept an invite that
  carries them, remove. Circles UI: list, members with labels, permission toggles, "This device is
  called", invite accept screen listing each power as a toggle. "Add trusted device" starts from a circle
  (default Home); the first `CircleInvite` rides on the pairing link (M9 hook).
- Manifest: add `INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE`, `NEARBY_WIFI_DEVICES`
  (`neverForLocation`); `network_security_config` with cleartext off. Request Nearby devices together
  with Bluetooth.
- `LocalAddressPolicy` (pure Kotlin, TCP and UDP, also applied to NSD-resolved addresses),
  `SessionHandshake` (spec AC9.4: initiator-first SIGMA, signed ephemeral ECDH P-256, HKDF-SHA256,
  per-direction AES-256-GCM control and audio keys, counters) with crypto behind an interface so tests
  run on the JVM; `FrameCodec` for length-prefixed frames. JCA only (no new crypto dependency); Java 17
  has no HKDF class, so implement HKDF (RFC 5869) over `Mac("HmacSHA256")` with the RFC test vectors.
- `BeaconKey` (random 256-bit, CE, wrapped by a Keystore AES-GCM key) and `BeaconTag` (HMAC slots).
- Wi-Fi: `NsdManager` register/discover `_fafo._tcp`, random instance name, TXT tag; server and client
  sockets bound to the Wi-Fi `Network`. `ArmService` runs the listener while armed (after first unlock).
- Alerts over Wi-Fi from `ArmService` on PRE_ALARM/TRIGGERED; alert notification with Reset / Disarm
  actions per granted permission.
- Remote `Reset` / `Disarm` (spec AC9.8): `CommandChallenge`, sender `BiometricPrompt` with a
  `CryptoObject` over the command key, target verifies the command signature, challenge, incident id and
  circle permission. State machine events `RemoteReset(peer, circle)` (to Armed, `awaiting_power` when
  unplugged, `incident_active` cleared) and `RemoteDisarm(peer, circle)` (to Disarmed), both refused in
  IncidentPersisted before first unlock.

Done-checks: spec AC8.9 and the Wi-Fi/handshake/command parts of AC9.11 green (RFC 5869 vectors too);
state-machine tests for RemoteReset (sound stops, incident closed, Armed, awaiting power when unplugged,
re-arms on PowerConnected, next unplug triggers again) and RemoteDisarm plus every refusal case;
`NoRemoteEndpointsTest` and `checkNoRemoteDeps` still green; manifest test updated (INTERNET now
required, RECORD_AUDIO still absent); boot path still never opens CeStore (circles are CE). Manual (Bill,
two phones on home Wi-Fi): discover, alert on unplug, reset and disarm from the other phone; Android 16
`RESTRICT_LOCAL_NETWORK` compat flag test. Commit: `M10: trust circles, Wi-Fi transport, remote reset/disarm`.

## M11. Bluetooth transport, Bluetooth alerts and Stay reachable

Spec F9 (Bluetooth parts, AC9.7 beacons, AC9.9).
- Bluetooth: GATT command characteristic on the F6 service carrying the same `SessionHandshake` and
  frames as Wi-Fi; transport selection (Wi-Fi when both are on the same network, else Bluetooth).
- BLE alert beacon from `ArmService` while alarming (service data = beacon tag, 2-minute slots);
  background BLE scan with a `PendingIntent` filtered on the service UUID; receiver matches the tag
  against trusted beacon keys, posts the alert, connects over GATT for details.
- `ReachableService` (`connectedDevice`, "Stay reachable", default off, persistent notification
  "Reachable by: Home, Work"), may resume from `BOOT_COMPLETED`; manifest adds
  `FOREGROUND_SERVICE_CONNECTED_DEVICE`.

Done-checks: beacon-tag and transport-selection unit tests; Robolectric: `ReachableService` starts with
`connectedDevice` only, posts its notification, resumes from `BOOT_COMPLETED` but not
`LOCKED_BOOT_COMPLETED`; the scan `PendingIntent` receiver ignores unknown tags. Manual (Bill): Wi-Fi off on
both phones, alert and reset over Bluetooth; background scan delivery with Samsung battery settings.
Commit: `M11: Bluetooth transport, BLE alerts, Stay reachable`.

## M12. Intercom and push-to-talk (last feature milestone)

Spec F10.
- Manifest: `RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE`, `MODIFY_AUDIO_SETTINGS`; `IntercomService`
  with `foregroundServiceType="microphone|connectedDevice"`, not `directBootAware`, never started from a
  receiver or `START_STICKY` (`START_NOT_STICKY`).
- `IntercomPolicy` (pure Kotlin): room mode only from the local UI and refused while an incident is open;
  entering PRE_ALARM/TRIGGERED ends room mode and drops listeners; listen allowed only for circles with
  Intercom chosen in room mode; mic open iff at least one verified listener; PTT floor control.
- Room mode UI: toggle + circle picker, notification ("Intercom on: Home can listen" / "Register is
  listening") with Stop, chime on listener connect, refuse if notifications are blocked.
- Push-to-talk UI: hold-to-talk button, target picker (labels + All) with reachable dots; mic permission
  asked at first use.
- Audio: `AudioRecord` (`VOICE_COMMUNICATION`) / `AudioTrack` (`USAGE_VOICE_COMMUNICATION`), 16 kHz mono
  20 ms frames, `OpusCodec` via `MediaCodec` (send `csd-0/1/2` in the stream start) with PCM fallback on
  Wi-Fi only, 200 ms jitter buffer; `AudioFrameCrypto` (AES-256-GCM under the audio key, nonce = stream id
  || frame counter, AAD = session id || stream id, sliding replay window). UDP on Wi-Fi (through
  `LocalAddressPolicy`), L2CAP CoC on Bluetooth (Opus only). Siren preempts playback.

Done-checks: spec AC10.9 tests green; a test that no message type can start room mode; a test that an
alarm ends room mode; a test that nothing under intercom writes audio to disk (fake file system / no
`FileOutputStream` in the audio path); manifest test now requires `RECORD_AUDIO` + microphone type only
on `IntercomService`. `MediaCodec` Opus is not available on the JVM: test `OpusCodec` selection with a
fake codec list and the PCM path end to end. Manual (Bill, two phones): room mode + listen, PTT
"Register" to "Back room" and to All, mic indicator visible, chime, latency feel, echo on speaker, room
mode ends when the phone is unplugged and the alarm starts. Commit: `M12: intercom and push-to-talk`.

## M13. Hardening, tests and release candidate

- Lint clean (`./gradlew lint` with no errors), no `onBackPressed`, no hardcoded secrets. Remove
  `continue-on-error` from the CI lint step.
- Manifest guard test (one test file) asserting the full v1 permission list from the spec (section 6),
  the absence of every "not requested in v1" permission, `neverForLocation` on `BLUETOOTH_SCAN` and
  `NEARBY_WIFI_DEVICES`, and the service types (`ArmService` specialUse|camera, `ReachableService`
  connectedDevice, `IntercomService` microphone|connectedDevice).
- `NoRemoteEndpointsTest`, `checkNoRemoteDeps` and `LocalAddressPolicy` tests still green (the "no
  internet server" rule).
- Update README with install instructions for the CI APK (enable install from unknown sources, A17 steps).
- Finish `docs/A17-TEST-CHECKLIST.md`: the nine done items, every "Manual (Bill)" item from M5-M12 and the
  spec's "Open items to test on the A17" (two-phone steps for F8-F10).

Done-checks: `./gradlew assembleDebug test lint checkNoRemoteDeps` green locally and in CI; debug APK
artifact downloadable from the Actions run. Commit: `M13: v1 release candidate`.

## Testing approach

- No device or emulator in the cloud session. Push logic into pure Kotlin classes (state machine,
  classifiers, prune policy, pairing code, circle policy, address policy, session handshake, HKDF, command
  signatures, frame and audio crypto, intercom policy) and unit test them on the JVM. Android Keystore does
  not exist on the JVM or in Robolectric: keep it behind an interface with a software fake in tests.
- Use Robolectric (`@RunWith(AndroidJUnit4::class)`, `testOptions.unitTests.isIncludeAndroidResources = true`)
  for services, receivers, DataStore, manifest and Compose UI.
- Hardware behavior (siren loudness, camera while locked, boot timing, Samsung battery behavior) is
  checked by Bill on the A17 with the CI APK and recorded in the checklist.

## CI

`.github/workflows/android.yml` sets up JDK 17 and the Android SDK, and runs
`./gradlew assembleDebug test` when `gradlew` exists, then `checkNoRemoteDeps` once that task exists
(M4), uploading `app-debug.apk` as the `fafo-debug-apk` artifact, then `./gradlew lintDebug` (reported,
and blocking from M13 on: remove `continue-on-error` from the lint step in the M13 commit). Before M1 it skips cleanly. The repo has no remote yet; CI runs once
Bill pushes it to GitHub.
