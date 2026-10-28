# FAFO Security: v1 Specification

Status: draft for build, 2026-10-27. Owner: Bill Crandell.
Scope source: Bill's v1 list of 2026-10-27 (six features), plus the 2026-10-26 decisions. Bill has
asked for additions or cuts, so the scope can still change; changes land here first.

## 1. Product summary

FAFO Security ("Free Advanced Features Offline") is an Android anti-theft app for the owner's own
phone. v1 is free and fully offline: no server, no account, no network calls, no analytics.

**v1 is done when a debug APK installed on a Samsung Galaxy A17 (Android 16) does all six:**

1. Arms only while plugged in.
2. Unplugging before the owner unlocks sets off a siren that runs until unlock, and returns after a reboot.
3. The user picks unplug sensitivity from 0.1 s to 5 s.
4. A bump chirps; picking the phone up chirps louder.
5. Takes a photo on trigger, then one every 10 s (default), kept offline. Nothing is sent anywhere in v1.
6. Trusted devices pair over Bluetooth, and only the real screen lock (PIN/pattern/password) can approve a pairing.

### Platform

| Item | Value |
|---|---|
| Language / UI | Kotlin, Jetpack Compose (Material 3) |
| minSdk | 30 (Android 11): clean `BiometricPrompt` + `DEVICE_CREDENTIAL` support |
| targetSdk / compileSdk | 36 (Android 16). Play requires API 36 for new apps from 2026-08-31 |
| Build | Gradle Kotlin DSL, version catalog, JDK 17 |
| Test device | Samsung Galaxy A17, Android 16, One UI |
| Distribution | Debug APK via GitHub Actions artifact; Google Play later |

## 2. States

```
            arm (plugged in, app on screen)
 DISARMED ───────────────────────────────▶ ARMED
    ▲                                        │  unplug held >= sensitivity window
    │ owner unlocks / credential             ▼
    └───────────────────────────────────── TRIGGERED ──(reboot / power cycle)──▶ INCIDENT_PERSISTED
                                             ▲                                         │
                                             └──── boot (LOCKED_BOOT_COMPLETED) ───────┘
```

| State | Meaning | Siren | Camera | Motion | Notification |
|---|---|---|---|---|---|
| DISARMED | Not protecting. No service running. | off | off | off | none |
| ARMED | Foreground service running; listening for unplug and motion. | off (chirps only) | ready, idle | on | persistent "Armed" (ongoing, cannot be swiped) |
| TRIGGERED | Unplug detected before unlock. | on, full alarm volume, looping | photo now, then every N s | ignored (siren already on) | persistent "ALARM" + full-screen intent if allowed |
| INCIDENT_PERSISTED | Device rebooted or service killed during TRIGGERED. On boot the service resumes the siren. | on | **not available** until owner unlocks or taps the alarm notification (Android blocks camera FGS from boot) | off | persistent "ALARM" |

### Transitions and rules

- **DISARMED to ARMED:** only from a user tap in the visible app, only when the phone reports
  plugged in (AC, USB or wireless). If not plugged in, the Arm button is disabled with the text
  "Plug in to arm". The tap starts the foreground service (types `specialUse|camera`).
- **ARMED to DISARMED:** the owner taps Disarm in the app or on the notification action (both need the
  device unlocked). Unlocking alone does not disarm unless the setting "Disarm on unlock" is on (default: off).
- **ARMED to TRIGGERED:** power disconnected and still disconnected after the sensitivity window,
  and the device is locked (keyguard showing). If the device is unlocked when unplugged, show a
  "Still armed: plug back in or disarm" chirp + notification instead of the siren.
- **TRIGGERED to DISARMED:** `ACTION_USER_PRESENT` (owner unlocked the device with the real
  credential) or owner taps Disarm in the app after unlock. Siren stops within 500 ms.
- **TRIGGERED to INCIDENT_PERSISTED:** implicit. The `incident_active` flag is written to
  device-protected storage at the moment of triggering, before the siren starts. Any reboot or process
  death with the flag set resumes as INCIDENT_PERSISTED.
- **INCIDENT_PERSISTED to TRIGGERED:** when the user taps the alarm notification (allowed camera
  FGS start), the service upgrades to include the camera type and photos resume.

## 3. Features and acceptance criteria

### F1. Arm while plugged in

- AC1.1 Arm button is enabled only while plugged in (read from sticky `ACTION_BATTERY_CHANGED`,
  `EXTRA_PLUGGED != 0`); state updates live when the cable is connected or removed.
- AC1.2 Tapping Arm starts one foreground service with types `specialUse|camera` and posts an ongoing
  notification within 1 s. The notification shows "FAFO Security is armed" and a Disarm action.
- AC1.3 Arming requires the runtime permissions that the armed features need (camera, notifications).
  If one is missing, the arm flow asks for it first; arming without camera is allowed with a visible
  "Photos off" warning.
- AC1.4 Armed state is written to device-protected storage.
- AC1.5 Unit test: arm is refused when plugged state is false.

### F2. Unplug siren that persists through reboot

- AC2.1 The service registers `ACTION_POWER_DISCONNECTED` / `ACTION_POWER_CONNECTED` at runtime with
  `registerReceiver()` (it is not a manifest-allowed implicit broadcast).
- AC2.2 On disconnect, the service starts a timer equal to the sensitivity window, then re-reads
  `ACTION_BATTERY_CHANGED`. If still unplugged and the keyguard is locked, it triggers.
  Reconnecting within the window cancels the trigger (plug-swap tolerance).
- AC2.3 Siren plays with `AudioAttributes.USAGE_ALARM`, `CONTENT_TYPE_SONIFICATION`, looping, alarm
  stream set to max (where allowed). Bundled audio asset, no network.
- AC2.4 The siren keeps sounding when the screen is off, in silent/vibrate mode, and in DND modes that
  allow alarms. (Known limit: DND "Total silence" mutes alarms; onboarding warns.)
- AC2.5 Siren stops only on owner unlock (`ACTION_USER_PRESENT`) or Disarm after unlock. Volume
  buttons and notification dismiss do not stop it. If the user lowers alarm volume while triggered, the
  service restores it (when permitted).
- AC2.6 Reboot during TRIGGERED: a `directBootAware` receiver for `LOCKED_BOOT_COMPLETED` and
  `BOOT_COMPLETED` reads `incident_active` from device-protected storage and starts the service as
  `specialUse` only. Siren resumes before first unlock. Target: within 30 s of boot on the A17.
- AC2.7 Force-stop from Settings by the thief requires unlocking first, so is out of scope. Process
  death by the system: service is `START_STICKY` and the boot path covers full restarts.
- AC2.8 Unit tests: trigger decision function (plugged history, window, keyguard state) covered for
  every sensitivity value and for reconnect inside/outside the window.

### F3. Sensitivity options

- AC3.1 Choices: 0.1, 0.25, 0.5, 1, 2, 5 s. Default 0.5 s.
- AC3.2 Stored in device-protected storage so it is readable at boot.
- AC3.3 Settings screen shows a one-line hint: "Longer = fewer false alarms from loose cables or plug swaps."

### F4. Motion chirps

- AC4.1 While ARMED, the service listens to the accelerometer (`SENSOR_DELAY_UI` or a batched
  equivalent) and classifies:
  - **Bump:** short spike above the bump threshold, phone stays roughly in place → one short chirp.
  - **Pick-up:** sustained change in orientation/gravity vector or displacement over ~0.5 s → louder,
    longer chirp (3 pulses).
- AC4.2 Motion never starts the full siren in v1. Only unplug does.
- AC4.3 Chirps use `USAGE_ALARM` at a lower gain than the siren. Rate-limited to one chirp per 1.5 s.
- AC4.4 Motion sensitivity: Low / Medium (default) / High, plus an Off switch.
- AC4.5 Unit tests feed recorded sample sequences into the classifier: resting, table bump, pick-up,
  walking with phone, phone on a vibrating surface.

### F5. Photo on trigger with offline queue

- AC5.1 On entering TRIGGERED (with camera FGS type active), take a photo within 2 s using CameraX
  `ImageCapture` bound to the service lifecycle (`LifecycleService` + `ProcessCameraProvider`, no preview).
- AC5.2 Then one photo every N seconds while TRIGGERED. N choices 5–30 s, default 10 s. Timer runs
  inside the service (no WorkManager, no exact alarms).
- AC5.3 Camera: front by default (the person holding the phone), setting for front / back / alternate.
- AC5.4 Photos are saved to app-private credential-protected storage (`filesDir/incidents/<incidentId>/`),
  JPEG, max 1600 px long edge, quality 80. Metadata (timestamp, camera, battery %, plugged state)
  in a sidecar JSON. No location in v1.
- AC5.5 If credential-protected storage is locked (before first unlock), photos are not taken (camera
  is not available from boot anyway). Nothing photo-related is written to device-protected storage.
- AC5.6 The queue is "pending send" in v1, but nothing is sent. The Incidents screen lists incidents
  and lets the owner view, share manually (Android share sheet, user-initiated) or delete photos.
- AC5.7 Storage rules: see section 5. Unit tests cover the pruning policy.

### F6. Trusted-device Bluetooth pairing gated by device credential

- AC6.1 "Add trusted device" first requires `BiometricPrompt` with
  `setAllowedAuthenticators(DEVICE_CREDENTIAL)` only (the real PIN/pattern/password, not a fingerprint
  and not "phone already unlocked"). If `canAuthenticate(DEVICE_CREDENTIAL)` is not `BIOMETRIC_SUCCESS`
  (no secure lock screen), pairing is blocked with "Set a screen lock first".
- AC6.2 Pairing uses Bluetooth LE between two phones that both run FAFO Security. Each install has an
  identity keypair (Ed25519 or ECDSA P-256) in Android Keystore. Devices exchange public keys over a
  GATT characteristic, both screens show a 6-digit code derived from both public keys + nonces
  (SAS / numeric comparison), and both users confirm the match.
- AC6.3 The second device must also confirm with its own device credential.
- AC6.4 Trusted devices are listed with name, key fingerprint and date added. Removing one also
  requires the device credential.
- AC6.5 Permissions: `BLUETOOTH_SCAN` with `neverForLocation`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`;
  legacy `BLUETOOTH` / `BLUETOOTH_ADMIN` with `maxSdkVersion="30"`. No location permission.
- AC6.6 v1 stores trust only. A trusted device has no remote powers in v1 (no silence, no photos,
  no mic). It is the foundation for phase two.
- AC6.7 Unit tests: code derivation is identical on both sides and differs when either key changes;
  pairing refused without credential success.

### F7. Onboarding (supports F1–F6)

Screens, in order, each skippable but shown again as a checklist on the Home screen until done:
1. What FAFO does and does not do (own device only; persistent notification; thief can still power off).
2. Notifications permission (`POST_NOTIFICATIONS`).
3. Camera permission.
4. Full-screen alarm: check `NotificationManager.canUseFullScreenIntent()`; if false, deep-link to
   `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`.
5. Do Not Disturb access (`ACCESS_NOTIFICATION_POLICY`) so alarm volume can be set under DND;
   explain that "Total silence" mutes alarms.
6. Battery: request ignore battery optimization (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent) and the
   Samsung steps: battery = Unrestricted; remove from Sleeping / Deep sleeping apps or add to Never
   sleeping apps; turn off "Put unused apps to sleep". Warn that firmware updates can revert these.
7. Built-in protections: link to Android's Theft Detection Lock / Offline Device Lock / Identity
   Check settings. FAFO is an add-on to those, not a replacement.
8. Self-test: plays a 1 s chirp and a 1 s siren sample at alarm volume, takes a test photo.

## 4. Settings and defaults

All settings live in DataStore. Settings needed at boot (marked DP) live in a device-protected DataStore.

| Setting | Values | Default | Storage |
|---|---|---|---|
| Unplug sensitivity | 0.1, 0.25, 0.5, 1, 2, 5 s | 0.5 s | DP |
| Siren sound | 2–3 bundled sounds | "Klaxon" | DP |
| Force max alarm volume | on/off | on | DP |
| Disarm on unlock (while armed, not triggered) | on/off | off | DP |
| Motion chirps | Off / Low / Medium / High | Medium | DP |
| Photo on trigger | on/off | on | CE |
| Photo interval | 5–30 s (step 5) | 10 s | CE |
| Camera | Front / Back / Alternate | Front | CE |
| Photo storage cap | 25 / 50 / 100 / 200 MB | 100 MB | CE |
| Low-storage floor | fixed | 500 MB free | n/a |
| Trusted devices | list | empty | CE (public keys + metadata); private key in Keystore |

DP = device-protected storage (`createDeviceProtectedStorageContext()`), readable before first unlock.
CE = credential-protected (default) storage, readable only after first unlock.

Runtime state in DP: `armed` (bool), `incident_active` (bool), `incident_id`, `incident_started_at`.

## 5. Storage rules

- **Device-protected storage holds only:** armed/incident flags, incident id + start time, DP settings.
  Never photos, keys or the trusted-device list.
- **Credential-protected storage holds:** photos, sidecar metadata, CE settings, trusted-device list.
- **Keys:** identity private key in Android Keystore (non-exportable). No secrets in the repo or in plain files.
- **Photo cap:** default 100 MB for all incidents. When adding a photo would exceed the cap, prune in this
  order, never deleting the protected set:
  - Protected per incident: the first photo, the latest photo, and "big change" photos (frames whose
    downscaled grayscale difference from the previous kept frame exceeds a threshold, max 20 per incident).
  - Prune first: oldest unprotected photos of the oldest closed incident, then of the current incident
    (thinning every other frame).
- **Low storage:** if free space is under 500 MB, stop taking photos (the siren keeps going) and show
  "Photos paused: storage low" in the alarm notification.
- **Deletion:** in v1 nothing is ever sent, so "delete only after confirmed send" is not reachable.
  Photos are deleted only by the cap pruning above or by the owner on the Incidents screen.
- **Backup:** `android:allowBackup="false"` and `dataExtractionRules` excluding everything, so photos and
  keys never go to cloud backup.

## 6. Permissions and justification

| Permission | Why |
|---|---|
| `FOREGROUND_SERVICE` | Armed service. |
| `FOREGROUND_SERVICE_SPECIAL_USE` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` | Anti-theft alarm: listens for unplug and plays the siren; restarts from boot. No other type fits (not media playback, not alarm clock, and must start from `LOCKED_BOOT_COMPLETED`, which camera/mic/mediaPlayback types cannot). |
| `FOREGROUND_SERVICE_CAMERA` + `CAMERA` | Photos while locked during an alarm. Requires the service to be started while the app is visible (arm tap) or from the alarm notification tap. |
| `POST_NOTIFICATIONS` | Persistent armed/alarm notification (required on 13+). |
| `USE_FULL_SCREEN_INTENT` | Alarm screen over the lock screen. Needs the user grant on 14+ and a Play declaration. |
| `RECEIVE_BOOT_COMPLETED` | Resume an active incident after reboot. |
| `WAKE_LOCK` | Keep the CPU awake while the siren plays and during the sensitivity window. |
| `ACCESS_NOTIFICATION_POLICY` (user-granted access) | Set alarm volume while DND is on. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Keep the armed service alive on Samsung. |
| `BLUETOOTH_SCAN` (`neverForLocation`), `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` | Trusted-device pairing. |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` (`maxSdkVersion="30"`) | Legacy pairing on Android 11. |
| `VIBRATE` | Chirps/siren vibration. |

**Not requested in v1:** `INTERNET`, `RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE`,
`FOREGROUND_SERVICE_DATA_SYNC`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, any location permission,
`SYSTEM_ALERT_WINDOW`, device admin, accessibility service. The manifest has no `INTERNET` permission;
a test enforces this.

## 7. Threat model and known limits

**Protects against:** an opportunistic thief grabbing a charging phone (café, office, airport,
bedside); someone unplugging a phone to take it; casual handling (chirps deter and alert).

**Assumes:** the owner has a secure lock screen; the thief does not know the credential; the phone was
armed while plugged in.

**Known limits (shown in onboarding, not hidden):**
- **A thief can power the phone off.** On Samsung the power menu may be reachable from the lock screen
  (One UI has a "Lock network and security" setting that requires unlock to power off; onboarding links to it).
  The siren resumes on next boot, but a phone that stays off is silent.
- **No photos after reboot until the owner unlocks or taps the alarm notification.** Android blocks camera
  foreground services from boot.
- **DND "Total silence"** mutes alarms. The siren may be silent in that mode.
- **Samsung battery management** may kill the service if onboarding steps are skipped or reverted by an update.
- **SIM removal, factory reset, Faraday bag:** not addressed in v1. Factory reset wipes the app.
- **No remote alerts in v1.** Photos stay on the phone; if the phone is never recovered, they are lost.
- **Abuse:** the app protects only the device it runs on, shows a persistent notification whenever armed,
  uses a unique icon and name, never hides or disguises itself, and has no remote monitoring in v1.

## 8. Play policy posture (for later Play release)

- Listing is about protecting your own device only. No hidden mode, no disguised icon, no "track anyone" wording.
- Foreground service declarations in Play Console for `specialUse` and `camera`, each with a description,
  impact if interrupted and a screen recording of the user arming.
- Full-screen intent declaration.
- Position as an add-on to Android's built-in Theft Detection Lock, Offline Device Lock and Identity Check.

## 9. Open items to test on the A17

- Camera before first unlock (expected: blocked; confirm).
- Network before first unlock (only relevant for phase two).
- Whether `USAGE_ALARM` sounds in each Samsung DND mode.
- The exact `setStreamVolume` behavior under DND with and without notification policy access.
- Boot-to-siren time from `LOCKED_BOOT_COMPLETED`.
- Whether Samsung's 2024 "foreground services work as intended since One UI 6" promise holds with default battery settings.

## 10. Phase two (NOT v1)

> **None of this is built in v1.** No server code, no `INTERNET` permission, no microphone in v1.
> Listed here so v1 choices don't block it. Phase two is planned as paid later (about $5/month); v1 stays free.

- **Relay alerts:** send incident alerts and queued photos to the owner's trusted devices through a small
  relay (for example a Cloudflare Worker) with end-to-end encryption using the paired public keys, so the
  relay never sees content. Alternatives: the owner's own email or Telegram. Photos are deleted from the
  phone only after a confirmed send. Uses a user-initiated data transfer job or the existing FGS, not `dataSync`.
- **Remote silence:** a trusted device can silence (not disarm) the siren with a signed command.
- **Remote mic (listen/speak), four rules, all mandatory:**
  1. The mic opens only while an alarm is sounding.
  2. Only the owner's own paired device can open it.
  3. The phone shows the system mic-in-use indicator the whole time.
  4. Mic access is obtained when the user arms (the service starts with the `microphone` type at arm time),
     because Android will not start the mic cold in the background.
  Listening to another person at any other time is spyware under Play policy and is never built.
- **Bluetooth mesh store-and-forward:** nearby FAFO phones relay encrypted alerts when the stolen phone has no network.
- **Lost-bond handling:** Android 16 `ACTION_KEY_MISSING` / `ACTION_ENCRYPTION_CHANGE` and
  `CompanionDeviceManager.removeBond()` for trusted-device repair.
