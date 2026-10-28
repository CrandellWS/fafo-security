# FAFO Security: v1 Specification

Status: draft for build, 2026-10-27. Owner: Bill Crandell.
Scope source: Bill's v1 list of 2026-10-27 (six features), his 2026-10-27 pre-alarm addition, plus the
2026-10-26 decisions. Bill has asked for additions or cuts, so the scope can still change; changes land
here first.

## 1. Product summary

FAFO Security ("Free Advanced Features Offline") is an Android anti-theft app for the owner's own
phone. v1 is free and fully offline: no server, no account, no network calls, no analytics.

**v1 is done when a debug APK installed on a Samsung Galaxy A17 (Android 16) does all six:**

1. Arms only while plugged in.
2. Unplugging before the owner unlocks sets off a siren that runs until unlock, and returns after a reboot.
   An adjustable pre-alarm (0, 1, 5, 10, 30 or 60 s; default 5 s) warns first with a quieter countdown
   that beeps faster as the full siren gets closer; plugging back in or unlocking cancels it.
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
            arm (plugged in, secure lock set, app on screen)
 DISARMED ──────────────────────────────────────────▶ ARMED ◀──────── plug back in ───────┐
    ▲                                                   │ unplug held >= sensitivity window │
    │                                                   ▼ while locked                      │
    │◀──────────── owner unlocks ─────────────────── PRE_ALARM (0-60 s countdown) ─────────┘
    │                                                   │ countdown ends (0 s = skip straight here)
    │◀──────────── owner unlocks ─────────────────── TRIGGERED (full siren)
    │                                                   │ reboot / power cycle / process death
    │                                                   ▼
    └──────────── owner unlocks ─────────────────── INCIDENT_PERSISTED (siren only, resumed from boot)
```

| State | Meaning | Siren | Camera | Motion | Notification |
|---|---|---|---|---|---|
| DISARMED | Not protecting. No service running. | off | off | off | none |
| ARMED | Foreground service running; listening for unplug and motion. | off (chirps only) | ready, idle | on | persistent "Armed" (ongoing; re-posted if dismissed, see AC1.2) |
| PRE_ALARM | Unplug confirmed while locked; countdown before the full siren. | quieter warning beeps, faster as the end nears | first photo now, then every N s | ignored | persistent "Plug me back in" + countdown |
| TRIGGERED | Pre-alarm ran out (or pre-alarm is 0 s). | on, full alarm volume, looping | photos continue every N s (first photo now if pre-alarm is 0 s) | ignored (siren already on) | persistent "ALARM" + full-screen intent if allowed |
| INCIDENT_PERSISTED | Device rebooted or service killed during PRE_ALARM or TRIGGERED. On restart the service resumes the full siren (no second countdown). | on | **after a reboot: none in v1** (camera FGS is blocked from boot and photos need CE storage, which stays locked until the owner's first unlock, which also ends the incident). After a process death without reboot: resumes only if someone taps the alarm notification/screen (see below). | off | persistent "ALARM" |

### Transitions and rules

- **DISARMED to ARMED:** only from a user tap in the visible app, only when the phone reports
  plugged in (AC, USB or wireless) and has a secure lock screen (`KeyguardManager.isDeviceSecure()`).
  If not plugged in, the Arm button is disabled with the text "Plug in to arm"; with no secure lock it
  says "Set a screen lock first" (without one a thief just swipes to unlock and stops the siren).
  The tap starts the foreground service (types `specialUse|camera`).
- **ARMED to DISARMED:** the owner taps Disarm in the app or on the notification action (both need the
  device unlocked). Unlocking alone does not disarm unless the setting "Disarm on unlock" is on (default: off).
- **ARMED to PRE_ALARM:** power disconnected and still disconnected after the sensitivity window,
  and the device is locked (`KeyguardManager.isDeviceLocked()`). If the device is unlocked when
  unplugged, show a "Still armed: plug back in or disarm" chirp + notification instead.
- **PRE_ALARM to ARMED:** power reconnected before the countdown ends. The warning stops, the incident is
  closed as "cancelled" (its photos are kept and labelled), and the service stays armed.
- **PRE_ALARM to TRIGGERED:** countdown ends. Pre-alarm 0 s goes straight from ARMED to TRIGGERED.
  Plugging back in during TRIGGERED does **not** stop the siren; only the owner's unlock does.
- **PRE_ALARM / TRIGGERED / INCIDENT_PERSISTED to DISARMED:** `ACTION_USER_PRESENT` (owner unlocked the
  device with the real credential or their biometric) or owner taps Disarm in the app after unlock.
  Warning or siren stops within 500 ms.
- **to INCIDENT_PERSISTED:** implicit. The `incident_active` flag is written to device-protected
  storage at the moment PRE_ALARM (or TRIGGERED when pre-alarm is 0 s) is entered, before any sound
  starts. Any reboot or process death with the flag set resumes as INCIDENT_PERSISTED with the full
  siren: a reboot during the countdown is treated as tampering.
- **Boot while ARMED (no incident):** if `armed` is set and on boot the phone is unplugged and locked,
  treat it as an unplug (a thief who powers off, then unplugs): run the sensitivity window, then
  PRE_ALARM as usual. If still plugged in, resume ARMED without the camera type.
- **INCIDENT_PERSISTED photos after a process death (no reboot):** CE storage is still unlocked, so
  tapping the alarm notification or the full-screen alarm activity (a `showWhenLocked` activity, so the
  tap works on the lock screen) is a visible-app start that may re-add the camera type with
  `startForeground(..., SPECIAL_USE or CAMERA)`; photos resume. The service never adds the camera type
  from a boot broadcast, a `START_STICKY` restart, or before first unlock (that throws
  `SecurityException` on Android 14+, or has nowhere to save).

## 3. Features and acceptance criteria

### F1. Arm while plugged in

- AC1.1 Arm button is enabled only while plugged in (read from sticky `ACTION_BATTERY_CHANGED`,
  `EXTRA_PLUGGED != 0`); state updates live when the cable is connected or removed.
- AC1.2 Tapping Arm starts one foreground service with types `specialUse|camera` and posts an ongoing
  notification within 1 s. The notification shows "FAFO Security is armed" and a Disarm action.
  On Android 14+ users can swipe away ongoing FGS notifications while unlocked; the notification sets a
  delete intent and is re-posted immediately, so it is visible the whole time the app is armed or
  alarming (Play stalkerware rule: persistent notification while running).
- AC1.3 Arming requires the runtime permissions that the armed features need (camera, notifications).
  If one is missing, the arm flow asks for it first; arming without camera is allowed with a visible
  "Photos off" warning.
- AC1.4 Armed state is written to device-protected storage.
- AC1.5 Arming is refused when `KeyguardManager.isDeviceSecure()` is false ("Set a screen lock first").
- AC1.6 Unit tests: arm is refused when plugged state is false, and when the device has no secure lock.

### F2. Unplug siren that persists through reboot

- AC2.1 The service registers `ACTION_POWER_DISCONNECTED` / `ACTION_POWER_CONNECTED` at runtime with
  `registerReceiver()` (it is not a manifest-allowed implicit broadcast).
- AC2.2 On disconnect, the service starts a timer equal to the sensitivity window, then re-reads
  `ACTION_BATTERY_CHANGED`. If still unplugged and the device is locked, it enters PRE_ALARM (or
  TRIGGERED when pre-alarm is 0 s). Reconnecting within the window cancels it (plug-swap tolerance).
  The window is measured from broadcast receipt; delivery latency is measured on the A17 (section 9).
- AC2.3 Siren plays with `AudioAttributes.USAGE_ALARM`, `CONTENT_TYPE_SONIFICATION`, looping, alarm
  stream set to max (where allowed). Bundled audio asset, no network.
- AC2.4 The siren keeps sounding when the screen is off, in silent/vibrate mode, and in DND modes that
  allow alarms. (Known limit: DND "Total silence" mutes alarms; onboarding warns.)
- AC2.4a **Pre-alarm.** Choices 0 (none), 1, 5, 10, 30, 60 s; default 5 s; stored DP. During it a
  quieter warning tone (`USAGE_ALARM`, lower gain than the siren) beeps with a shrinking interval
  (for example from 1 beep/s to about 5 beeps/s in the last seconds) and the notification shows
  "Plug me back in" with the countdown. Reconnecting power or the owner's unlock cancels it. The first
  photo is taken at the start of PRE_ALARM, not at the full siren.
- AC2.4b Unit tests: countdown reaches TRIGGERED at exactly the chosen duration for every value; 0 s
  skips PRE_ALARM; reconnect and UserPresent cancel at any point; beep interval is non-increasing.
- AC2.5 Siren stops only on owner unlock (`ACTION_USER_PRESENT`) or Disarm after unlock. Volume
  buttons and notification dismiss do not stop it. If the user lowers alarm volume while triggered, the
  service restores it (when permitted).
- AC2.6 Reboot during PRE_ALARM or TRIGGERED: a `directBootAware` receiver for `LOCKED_BOOT_COMPLETED`
  and `BOOT_COMPLETED` reads `incident_active` from device-protected storage and starts the service as
  `specialUse` only. Siren resumes before first unlock. Target: within 30 s of boot on the A17.
  The receiver is idempotent: `BOOT_COMPLETED` arrives after the owner's first unlock, so it must re-read
  the flag (already cleared by the unlock) and do nothing if the service is running or the flag is off.
- AC2.6a Everything that runs in direct boot (the `Application` class, `ArmService`, the boot receiver,
  the alarm activity, any `androidx.startup` initializer) must not touch CE storage or Keystore. Do not
  add WorkManager (its startup initializer opens a CE database and crashes in direct boot).
- AC2.7 Force-stop from Settings by the thief requires unlocking first, so is out of scope. Process
  death by the system: service is `START_STICKY` and the boot path covers full restarts. A sticky
  restart calls `startForeground` with `SPECIAL_USE` only (the restart is a background start).
- AC2.8 Unit tests: trigger decision function (plugged history, window, keyguard state) covered for
  every sensitivity value and for reconnect inside/outside the window.

### F3. Sensitivity options

- AC3.1 Choices: 0.1, 0.25, 0.5, 1, 2, 5 s. Default 0.5 s.
- AC3.2 Stored in device-protected storage so it is readable at boot.
- AC3.3 Settings screen shows a one-line hint: "Longer = fewer false alarms from loose cables or plug swaps."

### F4. Motion chirps (ARMED only)

- AC4.1 While ARMED (not PRE_ALARM or TRIGGERED), the service listens to the accelerometer (`SENSOR_DELAY_UI` or a batched
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

- AC5.1 On entering PRE_ALARM (or TRIGGERED when pre-alarm is 0 s), with the camera FGS type active,
  take a photo within 2 s using CameraX
  `ImageCapture` bound to the service lifecycle (`LifecycleService` + `ProcessCameraProvider`, no preview).
- AC5.2 Then one photo every N seconds while PRE_ALARM or TRIGGERED. N choices 5–30 s, default 10 s. Timer runs
  inside the service (no WorkManager, no exact alarms).
- AC5.3 Camera: front by default (the person holding the phone), setting for front / back / alternate.
- AC5.4 Photos are saved to app-private credential-protected storage (`filesDir/incidents/<incidentId>/`),
  JPEG, max 1600 px long edge, quality 80. Metadata (timestamp, camera, battery %, plugged state)
  in a sidecar JSON. No location in v1.
- AC5.5 If credential-protected storage is locked (before first unlock), photos are not taken (camera
  is not available from boot anyway). Nothing photo-related is written to device-protected storage.
  So in v1 an incident resumed after a reboot has no photos; this is shown in onboarding (section 7).
- AC5.5a If the camera is busy or capture fails, log it in the incident sidecar and retry on the next
  tick; a camera failure never stops the siren.
- AC5.6 The queue is "pending send" in v1, but nothing is sent. The Incidents screen lists incidents
  and lets the owner view, share manually (Android share sheet, user-initiated) or delete photos.
- AC5.7 Storage rules: see section 5. Unit tests cover the pruning policy.

### F6. Trusted-device Bluetooth pairing gated by device credential

- AC6.1 "Add trusted device" first requires `BiometricPrompt` with
  `setAllowedAuthenticators(DEVICE_CREDENTIAL)` only (the real PIN/pattern/password, not a fingerprint
  and not "phone already unlocked"). If `canAuthenticate(DEVICE_CREDENTIAL)` is not `BIOMETRIC_SUCCESS`
  (no secure lock screen), pairing is blocked with "Set a screen lock first".
- AC6.2 Pairing uses Bluetooth LE between two phones that both run FAFO Security. Each install has an
  identity keypair (ECDSA P-256; Keystore Ed25519 is not available on API 30-32) in Android Keystore.
  Devices exchange public keys over a GATT characteristic, both screens show a 6-digit code derived from
  both public keys + nonces (SAS / numeric comparison), and both users confirm the match.
  **Commit-then-reveal is required:** the responder first sends `H(pkB || nB)`, then the initiator sends
  `pkA || nA`, then the responder reveals `pkB || nB` and the initiator checks the hash. Without the
  commitment a man in the middle can try a million nonces offline until both 6-digit codes match.
- AC6.3 The second device must also confirm with its own device credential.
- AC6.4 Trusted devices are listed with name, key fingerprint and date added. Removing one also
  requires the device credential.
- AC6.5 Permissions: `BLUETOOTH_SCAN` with `neverForLocation`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`;
  legacy `BLUETOOTH` / `BLUETOOTH_ADMIN` with `maxSdkVersion="30"`. No location permission. On
  Android 11 (API 30) a raw BLE scan needs location, so on API 30 the scanning side discovers the peer
  through `CompanionDeviceManager.associate()` with a `BluetoothLeDeviceFilter` on the FAFO service UUID
  (system picker, no location permission). Android 12+ may use either path.
- AC6.6 v1 stores trust only. A trusted device has no remote powers in v1 (no silence, no photos,
  no mic). It is the foundation for phase two.
- AC6.7 Unit tests: code derivation is identical on both sides and differs when either key or nonce
  changes; a reveal that does not match the commitment aborts pairing; pairing refused without
  credential success.

### F7. Onboarding (supports F1–F6)

Screens, in order, each skippable but shown again as a checklist on the Home screen until done:
1. What FAFO does and does not do (own device only; persistent notification; thief can still power off;
   no photos after a reboot in v1). Requires a secure screen lock; if none, links to lock settings.
   Recommends turning off Smart Lock / Samsung "Extend Unlock" (a trust agent keeps the phone unlocked,
   so an unplug never counts as "locked" and a swipe stops the siren).
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
| Pre-alarm | 0, 1, 5, 10, 30, 60 s | 5 s | DP |
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
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Keep the armed service alive on Samsung. Play restricts this permission to apps whose core function breaks without it; declare it in Play Console with the anti-theft justification. |
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
- **A thief can power the phone off.** On Samsung the power menu may be reachable from the lock screen.
  One UI's "Lock network and security" setting may require unlock for some lock-screen actions (not
  verified whether it covers power off on the A17; see section 9). The siren resumes on next boot, but a
  phone that stays off is silent.
- **Trust agents.** With Smart Lock / Extend Unlock active the device counts as unlocked, so unplugging
  does not trigger and a swipe can end an incident. Onboarding recommends turning them off.
- **No photos after a reboot in v1.** Android blocks camera foreground services from boot, and photos
  need CE storage, which unlocks only with the owner's first unlock (which also ends the incident).
- **DND "Total silence"** mutes alarms. The siren may be silent in that mode.
- **Samsung battery management** may kill the service if onboarding steps are skipped or reverted by an update.
- **SIM removal, factory reset, Faraday bag:** not addressed in v1. Factory reset wipes the app.
- **No remote alerts in v1.** Photos stay on the phone; if the phone is never recovered, they are lost.
- **Abuse:** the app protects only the device it runs on, shows a persistent notification whenever armed,
  uses a unique icon and name, never hides or disguises itself, and has no remote monitoring in v1.

## 8. Play policy posture (for later Play release)

- Listing is about protecting your own device only. No hidden mode, no disguised icon, no "track anyone" wording.
- Foreground service declarations in Play Console for `specialUse` and `camera`, each with a description,
  impact if interrupted and a screen recording of the user arming. Risk: Play's listed camera use case is
  "Background Camera Streaming"; holding the camera type for hours while armed and idle may be
  questioned. Fallback if rejected: drop the camera type and take photos only while the alarm activity
  is visible.
- Full-screen intent declaration. Anti-theft is not one of the default-granted categories (calling,
  alarm clock), so expect the user grant path in onboarding even after Play review.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` declaration (see section 6).
- Position as an add-on to Android's built-in Theft Detection Lock, Offline Device Lock and Identity Check.

## 9. Open items to test on the A17

- Camera before first unlock (expected: blocked; confirm).
- Network before first unlock (only relevant for phase two).
- Whether `USAGE_ALARM` sounds in each Samsung DND mode.
- The exact `setStreamVolume` behavior under DND with and without notification policy access.
- Boot-to-siren time from `LOCKED_BOOT_COMPLETED`.
- `ACTION_POWER_DISCONNECTED` delivery latency (does 0.1 s mean anything?), and whether Samsung's
  "Protect battery" (charge stop at 80-85 %) or wireless pads report spurious unplugs.
- Whether "Lock network and security" blocks power off from the lock screen on One UI 8.
- Whether the alarm notification / full-screen activity tap re-adds the camera type after a process death.
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
     because Android will not start the mic cold in the background. Consequence: after a reboot the mic
     is unavailable until the owner unlocks, same as the camera (boot cannot start mic services).
  Listening to another person at any other time is spyware under Play policy and is never built.
- **Bluetooth mesh store-and-forward:** nearby FAFO phones relay encrypted alerts when the stolen phone has no network.
- **Lost-bond handling:** Android 16 `ACTION_KEY_MISSING` / `ACTION_ENCRYPTION_CHANGE` and
  `CompanionDeviceManager.removeBond()` for trusted-device repair.
