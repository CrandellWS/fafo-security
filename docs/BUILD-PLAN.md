# FAFO Security: v1 Build Plan

For a Claude Code cloud session. Read `CLAUDE.md` and `docs/SPEC-v1.md` first. Work the milestones in
order; each ends with its done-checks passing and one commit. Do not start a milestone until the
previous one's checks pass.

## M0. Environment: Android SDK in the cloud session

The cloud container has no Android SDK by default. Install it once per session (cache the dir if the
environment allows):

```bash
# JDK 17 (check first: java -version)
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
  Truth or kotlin.test, Compose ui-test-junit4 (Robolectric).
- No network or analytics libraries. No Firebase, no Crashlytics, no Play Services.
- Manifest: `android:allowBackup="false"`, `dataExtractionRules` excluding everything, no `INTERNET`.
- Empty Compose `MainActivity` with Home screen placeholder, edge-to-edge (Android 16 forces it),
  predictive back via `OnBackPressedDispatcher` / `BackHandler` (never `onBackPressed`).
- One trivial unit test so `test` runs.

Done-checks: `./gradlew assembleDebug test` succeeds; `app/build/outputs/apk/debug/app-debug.apk` exists;
CI workflow goes green on push (once Bill pushes). Commit: `M1: Gradle + Compose skeleton`.

## M2. State model and storage

- `AlarmState` sealed type: Disarmed, Armed, Triggered, IncidentPersisted (spec section 2).
- Pure Kotlin `AlarmStateMachine` with events (ArmRequested(plugged), PowerDisconnected(t),
  PowerConnected(t), WindowElapsed, UserPresent, DisarmTapped, BootCompleted(incidentActive),
  NotificationTapped). No Android types inside, so it is fully unit-testable.
- `DpStore` (device-protected DataStore via `createDeviceProtectedStorageContext()`) for armed,
  incident flags and DP settings; `CeStore` for the rest. Settings defaults exactly per spec section 4.
- Write `incident_active=true` before any siren call.

Done-checks: unit tests cover every transition in the spec table, including refused arm when unplugged,
reconnect inside the window, and boot with/without an active incident. Robolectric test that DpStore
reads back from the device-protected context. Commit: `M2: state machine and DP/CE storage`.

## M3. Compose UI and onboarding

- Screens: Home (big Arm/Disarm, plugged indicator, state, onboarding checklist), Settings, Incidents
  (list + photo viewer + delete + share sheet), Trusted devices, Onboarding flow (spec F7).
- Arm button disabled unless plugged (live from `ACTION_BATTERY_CHANGED` via a registered receiver in the
  ViewModel scope).
- Onboarding deep links: notification permission, camera, full-screen intent
  (`canUseFullScreenIntent()` + `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`), DND access
  (`ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`), battery optimization, Samsung battery steps (text +
  `ACTION_APPLICATION_DETAILS_SETTINGS`), built-in theft protection info.

Done-checks: Robolectric Compose tests: Arm disabled when unplugged and enabled when plugged; settings
show spec defaults; onboarding checklist reflects granted/missing permissions. Commit: `M3: UI and onboarding`.

## M4. Arm service

- `ArmService : LifecycleService`, `foregroundServiceType="specialUse|camera"` in the manifest with
  `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="Anti-theft alarm: detects charger removal and sounds a siren on the owner's own device; must resume from boot"/>`.
- Started only from the Arm tap (app visible) via `startForegroundService`, then
  `startForeground(id, notification, SPECIAL_USE or CAMERA)` within 5 s. If camera permission is missing,
  start with `SPECIAL_USE` only.
- Ongoing notification on a high-importance channel with Disarm action
  (`setAuthenticationRequired(true)` so it needs unlock). `START_STICKY`.
- Registers `ACTION_POWER_DISCONNECTED`/`ACTION_POWER_CONNECTED` at runtime; sensitivity timer via
  coroutine `delay` in the service (no AlarmManager); re-check plugged state from sticky
  `ACTION_BATTERY_CHANGED` and keyguard via `KeyguardManager.isDeviceLocked`.
- Registers `ACTION_USER_PRESENT` for owner unlock.
- Partial wake lock only during the sensitivity window and while triggered.

Done-checks: Robolectric: service starts foreground with correct types; receivers registered; simulated
disconnect + elapsed window + locked keyguard drives the state machine to Triggered; reconnect inside the
window does not. Manifest test asserts no `INTERNET`, no `RECORD_AUDIO`, no `dataSync`, no exact alarm
permissions. Commit: `M4: armed foreground service and unplug detection`.

## M5. Siren

- `Siren` wrapper around `MediaPlayer` or `AudioTrack` with `AudioAttributes.USAGE_ALARM`,
  `CONTENT_TYPE_SONIFICATION`, looping, bundled asset in `res/raw` (generate a royalty-free tone in code or
  ship an original sound; no third-party audio without a license).
- Request audio focus `AUDIOFOCUS_GAIN` (works because a FGS is running). Set `STREAM_ALARM` to max when
  "Force max alarm volume" is on; catch `SecurityException` under DND without policy access and surface
  it in the onboarding checklist. Volume watchdog restores alarm volume while triggered.
- Vibration pattern alongside.
- Full-screen alarm activity (`showWhenLocked`, `turnScreenOn`) posted via full-screen intent when allowed.

Done-checks: unit tests on Siren with a fake audio backend (starts on trigger, stops on UserPresent,
ignores notification dismiss); Robolectric test that audio attributes usage is `USAGE_ALARM`.
Manual on A17 (Bill, from CI APK): siren in silent mode, screen off. Commit: `M5: siren`.

## M6. Persistence across reboot

- `BootReceiver` with `android:directBootAware="true"`, `android:exported="true"` (system broadcasts only),
  intent filters for `LOCKED_BOOT_COMPLETED` and `BOOT_COMPLETED`. `ArmService` and the siren code path
  are also `directBootAware`.
- On boot: read `incident_active` from DpStore. If true, start `ArmService` with `SPECIAL_USE` only and
  resume the siren (state IncidentPersisted). If only `armed` is true and not triggered: start as armed
  (`SPECIAL_USE` only, no camera) and show "Armed after reboot, tap to enable photos".
- Tapping the alarm notification opens the activity, which restarts the service with `SPECIAL_USE|CAMERA`.
- Nothing in the boot path touches CE storage or Keystore.

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
- `IdentityKeys`: Keystore keypair (EC P-256 signing key, non-exportable), public key export.
- BLE: one side advertises a FAFO service UUID (`BLUETOOTH_ADVERTISE`), the other scans
  (`BLUETOOTH_SCAN` with `neverForLocation`) and connects (`BLUETOOTH_CONNECT`); exchange public key +
  random nonce via GATT; `PairingCode.derive(pkA, pkB, nA, nB)` = 6 digits from SHA-256 of the sorted
  inputs; both users confirm; both sides pass CredentialGate; store peer public key + name + fingerprint in CE storage.
- Remove device also behind CredentialGate.

Done-checks: unit tests: PairingCode is symmetric, 6 digits, changes when any input changes; pairing
flow refuses to persist without a credential success (fake gate); manifest has `neverForLocation`
and legacy BT permissions capped at `maxSdkVersion=30`, and no location permissions. Manual on two
phones (Bill). Commit: `M9: trusted-device pairing`.

## M10. Hardening, tests and release candidate

- Lint clean (`./gradlew lint` with no errors), no `onBackPressed`, no hardcoded secrets.
- Manifest guard test (one test file) asserting the full v1 permission list from the spec and the
  absence of every "not requested in v1" permission.
- Update README with install instructions for the CI APK (enable install from unknown sources, A17 steps).
- A17 manual test checklist in `docs/A17-TEST-CHECKLIST.md` covering the six done items and the spec's
  "Open items to test on the A17".

Done-checks: `./gradlew assembleDebug test lint` green locally and in CI; debug APK artifact downloadable
from the Actions run. Commit: `M10: v1 release candidate`.

## Testing approach

- No device or emulator in the cloud session. Push logic into pure Kotlin classes (state machine,
  classifiers, prune policy, pairing code) and unit test them on the JVM.
- Use Robolectric (`@RunWith(AndroidJUnit4::class)`, `testOptions.unitTests.isIncludeAndroidResources = true`)
  for services, receivers, DataStore, manifest and Compose UI.
- Hardware behavior (siren loudness, camera while locked, boot timing, Samsung battery behavior) is
  checked by Bill on the A17 with the CI APK and recorded in the checklist.

## CI

`.github/workflows/android.yml` sets up JDK 17 and the Android SDK, and runs
`./gradlew assembleDebug test` when `gradlew` exists, uploading `app-debug.apk` as the `fafo-debug-apk`
artifact. Before M1 it skips cleanly.
