# FAFO Security: v1 Specification

Status: draft for build, 2026-10-27. Owner: Bill Crandell.
Scope source: Bill's v1 list of 2026-10-27 (six features), his 2026-10-27 pre-alarm addition, his
2026-10-27 evening additions (trusted devices over Bluetooth or the same Wi-Fi, trust circles, intercom
and push-to-talk), plus the 2026-10-26 decisions. Bill has asked for additions or cuts, so the scope can still change; changes land
here first.

## 1. Product summary

FAFO Security ("Free Advanced Features Offline") is an Android anti-theft app for the owner's own
phone. v1 is free and needs no internet: no server, no account, no analytics, no remote endpoints.
The only network traffic is local, between the owner's own paired devices on the same Wi-Fi network or
over Bluetooth, end-to-end encrypted with the paired keys.

**v1 is done when a debug APK installed on a Samsung Galaxy A17 (Android 16) does all nine:**

1. Arms only while plugged in.
2. Unplugging before the owner unlocks sets off a siren that runs until unlock, and returns after a reboot.
   An adjustable pre-alarm (0, 1, 5, 10, 30 or 60 s; default 5 s) warns first with a quieter countdown
   that beeps faster as the full siren gets closer; plugging back in or unlocking cancels it.
3. The user picks unplug sensitivity from 0.1 s to 5 s.
4. A bump chirps; picking the phone up chirps louder.
5. Takes a photo on trigger, then one every 10 s (default), kept offline. Photos are never sent anywhere in v1.
6. Trusted devices pair over Bluetooth, and only the real screen lock (PIN/pattern/password) can approve a pairing.
7. Trust circles: a device can be in several circles (Home, Work...), each with its own members,
   user-chosen device labels ("Register", "Back room") and per-circle permissions (F8).
8. Nearby trusted devices, over Bluetooth or the same Wi-Fi (no internet server), get an alert when this
   phone triggers and can reset it (siren off, stays armed) or fully disarm it, when their circle allows (F9).
9. Intercom (remote speaker) and push-to-talk between devices in a circle on the same Wi-Fi or Bluetooth,
   addressed to one device by label or to all; started openly on the listening phone, never remotely (F10).
   This is the last feature milestone of the build.

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
 DISARMED ──▶ ARMING (3/5/10 s, cancel / unplug ──▶ DISARMED) ──▶ ARMED ◀── plug back in ──┐
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
| ARMING | Yellow countdown after the Arm tap (3, 5 or 10 s, default 5). Cancel or unplug returns to DISARMED; nothing alarms. Never persisted: a reboot during ARMING ends DISARMED. | off | off | off | "Arming in n s" with Cancel |
| ARMED | Foreground service running; listening for unplug and motion. | off (chirps only) | ready, idle | on | persistent "Armed" (ongoing; re-posted if dismissed, see AC1.2) |
| PRE_ALARM | Unplug confirmed while locked; countdown before the full siren. | quieter warning beeps, faster as the end nears | first photo now, then every N s | ignored | persistent "Plug me back in" + countdown |
| TRIGGERED | Pre-alarm ran out (or pre-alarm is 0 s). | on, full alarm volume, looping | photos continue every N s (first photo now if pre-alarm is 0 s) | ignored (siren already on) | persistent "ALARM" + full-screen intent if allowed |
| INCIDENT_PERSISTED | Device rebooted or service killed during PRE_ALARM or TRIGGERED. On restart the service resumes the full siren (no second countdown). | on | **after a reboot: none in v1** (camera FGS is blocked from boot and photos need CE storage, which stays locked until the owner's first unlock, which also ends the incident). After a process death without reboot: resumes only if someone taps the alarm notification/screen (see below). | off | persistent "ALARM" |

### Transitions and rules

- **DISARMED to ARMED:** only from a user tap in the visible app, only when the phone reports
  plugged in (AC, USB or wireless) and has a secure lock screen (`KeyguardManager.isDeviceSecure()`).
  If not plugged in, the Arm button is disabled with the text "Plug in to arm"; with no secure lock it
  says "Set a screen lock first" (without one a thief just swipes to unlock and stops the siren).
  The tap starts the foreground service (types `specialUse|camera`) only after the owner confirms it is
  them (AC1.7): the phone's screen lock or a Class 3 (strong) biometric, skipped when the phone was unlocked
  within the "Unlock grace window".
- **DISARMED to ARMING to ARMED (2026-10-28, Bill's device test):** the confirmed Arm tap starts a yellow
  ARMING countdown; when it ends the service re-checks plugged-in and secure lock and enters ARMED. Cancel
  (app button or notification action) needs no confirmation, since the result is "not armed". Unplugging
  during ARMING cancels it. Remote commands are refused and motion is ignored during ARMING.
- **ARMED to DISARMED:** the owner taps Disarm in the app or on the notification action. Both need a
  confirmation (AC1.7), with a stricter skip rule than arming: no prompt only if the phone was locked
  (screen off) after it was armed, then unlocked, and that unlock is inside the grace window. The notification action opens the app (it needs the device unlocked)
  and the app runs the confirmation, so right after unlocking there is no extra prompt. Changed 2026-10-28:
  the notification action used to disarm straight from a service intent with only "device unlocked",
  which let anyone holding the unlocked phone disarm without the credential. Unlocking alone does not
  disarm unless the setting "Disarm on unlock" is on (default: off).
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
- **Remote reset / disarm (F9),** two separate circle permissions (Bill 2026-10-27: Home = full trust,
  Work = "reset the alarm" only):
  (a) **Reset** (circle grants "Reset"): stop the warning/siren, close the incident as "reset by
  <label>" (photos kept), clear `incident_active`, and return to ARMED. If the phone is still unplugged it
  stays ARMED with `awaiting_power=true`: unplug detection is paused until power is reconnected, then it
  is armed exactly as before (the next unplug triggers again). Never disarms.
  (b) **Disarm** (circle grants "Disarm"): go to DISARMED, incident closed as "disarmed by <label>",
  photos kept. An app cannot unlock the Android lock screen; "disarm" ends the incident and protection only.
  Neither is available in INCIDENT_PERSISTED before the first unlock (the trust list and keys are in CE
  storage / Keystore, which the boot path never touches).
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
- AC1.7 Arm and Disarm in the app need the owner's screen lock or a strong biometric
  (`BiometricPrompt`, `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`), except within the "Unlock grace window"
  after the phone was unlocked. Implemented with an Android Keystore HMAC key created with
  `setUserAuthenticationParameters(grace, AUTH_BIOMETRIC_STRONG | AUTH_DEVICE_CREDENTIAL)`: the app tries
  the key first; a device unlock (PIN / pattern / password or Class 3 fingerprint) inside the window lets
  it work with no prompt; on `UserNotAuthenticatedException` the prompt is shown and the key is checked
  again. Grace 0 means every Arm / Disarm prompts (per-use key with a `CryptoObject`). Changing the grace
  window also needs the confirmation and re-creates the key. Smart Lock and Class 2 face unlock do not
  count, so they always prompt. Remote Reset / Disarm (AC9.8) and the owner's unlock stopping an alarm are
  unchanged. Cancelling leaves the state as it was.
  **Disarm rule (2026-10-28, Bill's device test: right after arming, Disarm went through with no prompt
  because the arm-time unlock was still inside the grace window).** The armed service records
  `ACTION_SCREEN_OFF` and `ACTION_USER_PRESENT` times (`SystemClock.elapsedRealtime()` plus the boot count,
  in DP storage) after the arm time. Disarm skips the prompt only when all hold: screen off at or after the
  arm time, an unlock after that screen off, the unlock within the grace window, the same boot, and the
  grace-window key accepts (proof the unlock used the real credential or a strong biometric, which rules
  out Smart Lock / Extend Unlock). Otherwise Disarm always prompts, on a separate per-use Keystore key
  (`fafo_disarm_g0`, `CryptoObject`), so no recent unlock or other prompt can satisfy it. Arming keeps the
  plain grace window. The ARMING countdown (default 5 s) runs between the Arm confirmation and ARMED.

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

- AC2.10 (round 3, Bill 2026-10-28) Alarm light: the back camera torch (`CameraManager.setTorchMode`, no
  CAMERA permission) strobes about 4 Hz (120 ms on / 130 ms off) in TRIGGERED / INCIDENT_PERSISTED and
  blinks slowly (300 ms on / 1.2 s off) at ORANGE (repeated bumps, pre-alarm). It goes dark 300 ms before
  each trigger photo and resumes after. Torch unavailable (camera busy, thermal) skips that flash and
  retries; it never throws into the alarm path. Setting "Flash as alarm light" (DP, default on; hidden
  when the phone has no flash). Disarm, cancel and service shutdown leave it off; it never switches off a
  flashlight the owner turned on.

### F3. Sensitivity options

- AC3.1 Choices: 0.1, 0.25, 0.5, 1, 2, 5 s. Default 0.5 s.
- AC3.2 Stored in device-protected storage so it is readable at boot.
- AC3.3 Settings screen shows a one-line hint: "Longer = fewer false alarms from loose cables or plug swaps."

### F4. Motion chirps and the bump ladder (ARMED only)

- AC4.1 While ARMED (not PRE_ALARM or TRIGGERED), the service listens to the accelerometer (`SENSOR_DELAY_UI` or a batched
  equivalent) and classifies:
  - **Bump:** short spike above the bump threshold, phone stays roughly in place → one short chirp.
  - **Pick-up:** sustained change in orientation/gravity vector or displacement over ~0.5 s → louder,
    longer chirp (3 pulses).
- AC4.2 **Changed 2026-10-28 (Bill's bump ladder; was "motion never starts the siren").** Motion feeds a
  threat ladder shown on the traffic-light status screen: one bump goes YELLOW with a short chirp; repeated
  bumps within 8 s go ORANGE with the louder chirp; one more close bump, or a pickup, starts a MOTION
  pre-alarm, but only while the phone is locked and the setting "Motion can sound the alarm" is on
  (default on). Unlocked, or with the setting off, the ladder stops at ORANGE (chirps only). Left untouched
  for 60 s the ladder cools back to GREEN. A MOTION pre-alarm lasts at least 5 s whatever the pre-alarm
  setting, and plugging in does not cancel it (unlock or Disarm does). Motion Off disables the ladder.
- AC4.6 Status screen (round 3): a big centered shield (about 75% of the width in portrait, 75% of the
  height in landscape) with the wordmark under or beside it. Stripes carry the status color: brand red
  disarmed, YELLOW arming or one bump, GREEN armed and calm, ORANGE repeated bumps or the pre-alarm, RED
  the siren, when the whole background flashes red at 2 Hz. The beak is always red; the eagle's eye is
  white until armed, then red. The state word is always shown, so color never carries the state alone.
  The screen stays on only during the pre-alarm and the alarm.
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

- AC6.1 "Add trusted device" requires `BiometricPrompt` with
  `setAllowedAuthenticators(DEVICE_CREDENTIAL)` only (the real PIN/pattern/password, not a fingerprint
  and not "phone already unlocked"). If `canAuthenticate(DEVICE_CREDENTIAL)` is not `BIOMETRIC_SUCCESS`
  (no secure lock screen), pairing is blocked with "Set a screen lock first" before anything starts.
  The prompt itself is shown once, right after the person confirms the 6-digit code and before anything
  is stored (build note M9: one prompt per pairing instead of one before and one after the code).
- AC6.2 Pairing uses Bluetooth LE between two phones that both run FAFO Security. Each install has an
  identity keypair (ECDSA P-256; Keystore Ed25519 is not available on API 30-32) in Android Keystore.
  Devices exchange public keys over a GATT characteristic, both screens show a 6-digit code derived from
  both public keys + nonces (SAS / numeric comparison), and both users confirm the match. Here "public
  key" (`pkA`, `pkB`) means the identity public key **and** the command public key (AC9.8) together, so
  the code authenticates both.
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
- AC6.6 Pairing alone grants no powers. What a trusted device may do (receive alerts, reset, disarm,
  intercom) comes only from the circles it is in (F8). A paired device in no circle can do nothing.
  Pairing ends with the device being added to the circle the owner started from (default "Home").
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
   Also check that the "Alarm screen" notification channel is on at HIGH importance (a channel can be
   lowered by the user or One UI but never raised by the app); if not, deep-link to that channel's settings.
   Implementation note (2026-10-28): Android launches a full-screen intent only when a notification is
   first posted, never on an update, so TRIGGERED posts a separate new alarm notification (own ID, own
   HIGH channel, `CATEGORY_ALARM`) rather than adding the intent to the ongoing armed notification.
5. Do Not Disturb access (`ACCESS_NOTIFICATION_POLICY`) so alarm volume can be set under DND;
   explain that "Total silence" mutes alarms.
6. Battery: request ignore battery optimization (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent) and the
   Samsung steps: battery = Unrestricted; remove from Sleeping / Deep sleeping apps or add to Never
   sleeping apps; turn off "Put unused apps to sleep". Warn that firmware updates can revert these.
7. Built-in protections: link to Android's Theft Detection Lock / Offline Device Lock / Identity
   Check settings. FAFO is an add-on to those, not a replacement.
8. Self-test: plays a 1 s chirp and a 1 s siren sample at alarm volume, takes a test photo.

Nearby devices (Bluetooth + `NEARBY_WIFI_DEVICES`) are asked when the owner first opens Trusted devices,
and the microphone when they first use intercom or push-to-talk, not during onboarding.

### F8. Trust circles

A circle is a named group of trusted devices with labels and permissions, for example Home or Work.

- AC8.1 **Model.** Each install holds its own list of circles: `Circle(id: 128-bit random, name,
  members: [Member(peerKeyFingerprint, label, permissions)])`. A trusted peer can be in several circles,
  with a different label and different permissions in each. Circles live in CE storage. There is no
  shared server copy; each device enforces its own view.
- AC8.2 **Labels.** Each device has its own label ("This device is called: Back room", default the
  model name), sent to peers on every session as a suggestion. The owner can rename any member locally;
  the local label wins. Labels are 1-24 characters, unique within a circle. A suggested label never
  replaces an existing local label and, if it collides with another member's, gets a suffix ("Register 2"),
  so a peer cannot impersonate another device by renaming itself. Notifications and the intercom always
  show the local label.
- AC8.3 **Permissions per circle**, enforced by the receiving device:
  | Permission | Lets a member of this circle |
  |---|---|
  | Alerts | receive my "alarm triggered" alert (F9) |
  | Reset | stop my pre-alarm / siren and put me back to ARMED (section 2, remote reset); never disarms |
  | Disarm | end my incident and disarm me (implies Reset) |
  | Intercom | push-to-talk to me, and listen to me only while I have intercom room mode on (F10) |
- AC8.4 **Defaults.** A built-in "Home" circle exists from first launch (empty) with Alerts + Reset +
  Disarm + Intercom (full trust). Any new circle defaults to Intercom only. A "Work" suggestion when
  creating a circle preselects Intercom + Reset (Bill's Work level), still needing the credential to save
  Reset. The owner can change permissions per circle.
- AC8.5 **Credential gates.** The owner's real lock-screen credential (`BiometricPrompt` with
  `DEVICE_CREDENTIAL` only, as in AC6.1) is required to: add a member (pairing, or adding an already
  trusted device to another circle), grant Reset or Disarm to a circle (including accepting an invite
  that carries them), and remove a member or a circle. Renaming and removing permissions do not need it;
  granting Alerts or Intercom does not either (Intercom never opens the mic without local room mode).
- AC8.6 **Joining is mutual.** Adding a member sends a signed `CircleInvite(circleId, name, proposed
  permissions, myLabel)` over a session (F9). The other device shows "Back room invites you to Work" and
  the other owner accepts with their own device credential. The accept screen spells out what accepting
  lets the inviter do to *this* phone ("Back room will be able to: get your alarm alerts, reset your
  alarm, disarm you, push-to-talk to you") with each power as a toggle the invitee can turn off before
  accepting; their copy starts with what they accepted, editable later. Permissions are one-way: what A
  grants B never implies what B grants A. Declined or unanswered invites expire after 10 minutes. During
  pairing (F6) the first invite rides on the already authenticated pairing link; later invites (adding an
  existing trusted device to another circle) use a session (F9).
  Build notes M10: the raw pairing link is authenticated but not encrypted, so right after both phones
  confirm, they run a normal F9 session handshake over the same Bluetooth link and the first invite rides
  inside it. The phone that searched ("Find the other phone") invites the other into the circle its owner
  chose; an invitee whose built-in Home is still empty adopts the inviter's circle id for it instead of
  getting a second "Home". The inviter adds the invitee only when the `CircleAccept` arrives within the 10
  minutes (the inviter's credential was given when the invite was sent).
- AC8.7 **No transitive trust.** If A trusts B and C, B and C do not trust each other until they pair.
  "All" in a circle means all members that this device itself trusts. (Introductions, where A vouches
  for C to B, are a later idea, not in v1.)
- AC8.8 Every message carries the `circleId` it acts under; the receiver checks that the sender is a
  member of that circle and that the circle grants the permission. A device removed from every circle
  stays paired but can do nothing; "Remove device" deletes the trust entirely (credential required).
- AC8.9 Unit tests (pure Kotlin `CirclePolicy`): Home and new-circle defaults; permission checks per
  circle for each message type; a member of two circles gets each circle's permissions only under that
  circle's id; Reset never disarms; label uniqueness and suggested-label collision suffix; credential gate
  required for add, grant Reset/Disarm, accepting an invite carrying them, and remove (fake gate);
  accepting with a power toggled off does not grant it.

### F9. Local transport: Wi-Fi and Bluetooth, no internet server

Trusted devices talk directly over the same Wi-Fi network or over Bluetooth LE. No relay, no cloud, no
internet address is ever contacted.

- AC9.1 **Wi-Fi discovery.** `NsdManager` (mDNS/DNS-SD, handled by the system, so no multicast lock or
  `CHANGE_WIFI_MULTICAST_STATE`) registers service type `_fafo._tcp` on a random port. The instance name
  is random per registration (never the device label, so a café network does not see "Register"). The
  TXT record holds protocol version and `tag = first 8 bytes of HMAC-SHA256(beaconKey, timeSlot)` with
  10-minute slots, so peers recognise each other without connecting and strangers cannot track a device
  across slots. Peers check the current and previous slot.
- AC9.2 **Wi-Fi only, local addresses only.** Sockets are bound to the Wi-Fi (or Ethernet) `Network`
  from `ConnectivityManager`, never mobile data. A pure Kotlin `LocalAddressPolicy` refuses any address
  outside 10/8, 172.16/12, 192.168/16, 169.254/16, IPv6 link-local fe80::/10 and ULA fc00::/7, for
  TCP connect, TCP accept and every UDP send/receive (F10 audio). It is also checked against the
  addresses that NSD resolves, so a spoofed mDNS answer pointing at a public host is refused. Routers with client isolation (common on public Wi-Fi) block peers; the app
  then falls back to Bluetooth and says so.
- AC9.3 **Bluetooth.** Commands and alerts also run over BLE GATT (the F6 service UUID). Audio over
  Bluetooth uses an LE L2CAP connection-oriented channel (API 29+) and needs Opus (F10); Bluetooth audio
  is best effort. Transport order: Wi-Fi if both are on the same network, else Bluetooth.
- AC9.4 **Session handshake** (same for Wi-Fi and BLE, pure Kotlin `SessionHandshake` with injected
  crypto, SIGMA-style with the initiator proving itself first so a listening phone never reveals its
  identity to a stranger): each side makes a fresh ephemeral ECDH P-256 key pair (software, in memory
  only; Keystore key agreement needs API 31 and minSdk is 30).
  1. Initiator sends `version || ephemeralPubI || nonceI`.
  2. Responder sends `version || ephemeralPubR || nonceR` (no identity yet).
  3. Both derive handshake keys with HKDF-SHA256 over the ECDH secret and the transcript hash. Initiator
     sends, encrypted, its identity fingerprint and an ECDSA P-256 signature (Keystore identity key, F6)
     over `"fafo-v1 initiator" || transcript hash`.
  4. Responder checks the fingerprint against its trusted list and verifies the signature; unknown keys or
     bad signatures close the socket without a reply. Only then does it send, encrypted, its own
     fingerprint and signature over `"fafo-v1 responder" || transcript hash` (role labels stop reflection).
  Final HKDF over the ECDH secret and the full transcript (both identities included) gives two
  AES-256-GCM control keys (one per direction) and two audio keys (F10, separate labels). The version
  byte is inside the signed transcript, so a downgrade fails. Forward secrecy: ephemeral keys are
  discarded when the session ends. Sessions expire after 1 hour or when the network changes.
- AC9.5 **Messages.** Length-prefixed frames, each AES-GCM encrypted with a nonce built from a per-
  direction 64-bit counter; the receiver rejects any counter at or below the last one seen (replay
  protection). Message types: `Hello(label, beaconKey on first session)`, `CircleInvite`,
  `CircleAccept`, `Alert`, `CommandChallenge`, `Reset`, `Disarm`, `IntercomListen`, `IntercomStop`,
  `PttStart`, `PttStop`, `Ping`, plus three replies that only answer a request: `CommandResult` (to
  `Reset` / `Disarm` / a refused `CommandChallenge`), `IntercomListenReply` (carries the room phone's codec
  csd and UDP port) and `PttBusy` (half-duplex collision). There is deliberately no message that arms,
  starts room mode, opens a mic or changes circles/permissions on the receiver. Unknown types are ignored.
  Max frame 64 KiB. (Build note M10: the three replies added; `Hello` carries the beacon key in every
  session, not only the first, which also delivers a rotated key, AC9.6.)
- AC9.6 **Beacon key.** Each install makes one random 256-bit beacon key, stored in CE storage encrypted
  with a non-exportable Keystore AES-GCM key, and sends it to each peer in the first session after
  pairing. It is used only for discovery tags (AC9.1) and BLE alert beacons (AC9.7). Removing a device
  rotates the beacon key and re-sends it to the remaining peers.
- AC9.7 **Alerts.** When this phone enters PRE_ALARM or TRIGGERED (after first unlock):
  - Wi-Fi: send a signed `Alert(state, startedAt, myLabel)` to every reachable member of every circle with
    Alerts, retry every 30 s while the incident is open.
  - Bluetooth: advertise the F6 service UUID with service data = beacon tag (AC9.1, 2-minute slots
    while alarming). Peers run a background BLE scan with a `PendingIntent` filtered on the service UUID
    (works without a foreground service); the receiver checks the tag against its trusted peers' beacon
    keys and, on a match, posts the alert and connects over GATT for details.
  - The receiving device posts a high-priority notification "Back room: ALARM (unplugged 21:04)" with
    "Reset" and/or "Disarm" actions when that phone's circle grants them (the alarming phone sends its
    granted powers in the `Alert`; the target still enforces them). Alerts are "nearby" only; out of range
    means no alert.
  - Build notes M11: the Bluetooth beacon is service data under the FAFO UUID: 1 state byte (1 = alarming,
    0 = reachable) + the 8-byte tag in 2-minute slots, so it fits a legacy advertisement (30 bytes) with no
    name. While the local network runs (app open, armed, Stay reachable or room mode) the phone also
    advertises the reachable beacon (state 0, connectable) so its circles can open a Bluetooth session when
    Wi-Fi is not shared; the background `PendingIntent` scan matches only state 1. A phone that connects
    while the other is alarming gets the `Alert` (with the granted powers) as soon as the session opens. The
    background scan is registered when the network starts and again after `BOOT_COMPLETED` (scan
    registrations do not survive a reboot), by a separate non-direct-boot receiver, so the direct-boot alarm
    receiver still never touches CE storage.
- AC9.8 **Reset and disarm from a trusted device.** Sending `Reset` or `Disarm` requires the sender's
  own credential at the moment of sending, enforced cryptographically, not just by a UI prompt: each
  install has a second Keystore key, the **command key** (ECDSA P-256, `setUserAuthenticationParameters(0,
  AUTH_DEVICE_CREDENTIAL or AUTH_BIOMETRIC_STRONG)`, so every signature needs a fresh authentication;
  API 30+, so fine at minSdk 30), whose public key is exchanged and SAS-confirmed during pairing (F6). The
  sender asks the target for a `CommandChallenge` (random 128-bit, valid 60 s, single use), shows
  `BiometricPrompt` with a `CryptoObject` over the command key, and signs `"fafo-v1 cmd" || type ||
  circleId || targetFingerprint || incidentId || challenge`. The target verifies the signature with the
  stored command public key and refuses a stale, reused or mismatched challenge, the wrong incident id, or
  a circle without the permission. A stolen trusted phone without its owner's credential cannot reset or
  disarm anything, even with a patched app. The target applies it within 500 ms and chirps once so the
  person there knows it was remote.
- AC9.9 **Stay reachable.** To receive alerts and push-to-talk over Wi-Fi while the app is in the
  background, the owner turns on "Stay reachable" (default off): `ReachableService`, a foreground service
  of type `connectedDevice`, with a persistent notification "Reachable by: Home, Work". It may resume
  from `BOOT_COMPLETED` (connectedDevice is not on Android 15's boot-blocked list; it is after first
  unlock, so CE storage is open). Without it, Bluetooth alerts still arrive through the background scan.
  The armed `ArmService` also listens while armed, so an armed phone can always be reset or disarmed by a circle that grants it (after first unlock).
- AC9.10 **Android 16 / 17 local network protection.** Android 16 has an opt-in test mode
  (`adb shell am compat enable RESTRICT_LOCAL_NETWORK ws.crandell.fafo`, then reboot) where local network
  access needs `NEARBY_WIFI_DEVICES`; Android 17 enforces it for apps targeting SDK 37 with the new
  `ACCESS_LOCAL_NETWORK` permission in the Nearby devices group. v1 declares `NEARBY_WIFI_DEVICES`
  (`neverForLocation`) and requests it together with the Bluetooth permissions (same Nearby devices group,
  one prompt). `ACCESS_LOCAL_NETWORK` is added when targetSdk moves to 37 (not in the SDK 36 platform).
  Apps targeting below 37 that hold `INTERNET` get an implicit grant. Test the compat flag on the A17.
- AC9.11 Unit tests: `LocalAddressPolicy` accepts every private range and refuses public IPv4/IPv6,
  loopback, multicast, and IPv4-mapped public IPv6; `SessionHandshake` succeeds between two
  trusted fakes, fails on an unknown key, a bad signature, a changed transcript byte and a version
  downgrade; frame counter rejects replays and reordering below the window; beacon tags match across
  slot edges and differ per key; the responder sends nothing identifying before the initiator's
  signature verifies; Reset/Disarm refused with a missing or bad command-key signature, with a stale or
  reused challenge, from a circle without the permission, or for the wrong incident id; a Reset leaves the
  state ARMED (with `awaiting_power` when unplugged), never DISARMED; Robolectric: NSD registration uses
  `_fafo._tcp` and a random instance name; resolved non-private addresses are refused.

### F10. Intercom (remote speaker) and push-to-talk

Plain voice between devices in a circle on the same Wi-Fi (or Bluetooth). Not labelled or marketed as a
baby monitor or as a way to listen to anyone; it is an intercom/walkie-talkie for the owner's own devices.

- AC10.1 **Room mode (listen).** The person at a phone turns on "Intercom room mode" in the visible app
  and picks which circle(s) may listen (only circles with Intercom). This starts `IntercomService`, a
  foreground service of type `microphone|connectedDevice`, from the visible app only. Members of those
  circles can then tap that device's label and listen, and talk back.
- AC10.2 **Never remote, never hidden.** Room mode cannot be turned on by any message, never starts from
  boot or a `START_STICKY` restart (Android also blocks microphone services from boot and from the
  background), and is off after a reboot. While room mode is on: persistent notification "Intercom on:
  Home can listen" with a Stop action; when someone listens it changes to "Register is listening"; a
  short chime plays on the room phone when a listener connects (setting "Chime when someone listens",
  default on); the system mic indicator shows whenever the mic is open. Room mode refuses to start if
  notifications are denied or the channel is blocked, and stops if that changes while running.
- AC10.3 **Mic only while needed.** In room mode the mic opens only while at least one verified listener
  is connected, and closes within 1 s after the last one leaves. Audio is live only: nothing is ever
  recorded to a file, on either side.
- AC10.4 **Push-to-talk.** Hold the big Talk button in the visible app to talk to one device (by label) or
  "All" in the selected circle. Release stops. The sending phone's mic is open only while the button is
  held (the app is in the foreground, so no microphone service is needed). Receivers must be reachable:
  app open, room mode on, or "Stay reachable" on (F9). Receivers play it immediately with a short
  start/end beep and show "Register is talking". Half duplex: if two people press at once, both hear a
  busy tone and the later one is dropped.
- AC10.5 **Targets.** "All" sends to every reachable member of the circle that grants Intercom (unicast
  to each, no multicast). The target list shows labels with a reachable dot.
- AC10.6 **Audio.** 16 kHz mono, 20 ms frames, `AudioRecord` with `VOICE_COMMUNICATION` source (echo
  cancel / noise suppression where available), `AudioTrack` with `USAGE_VOICE_COMMUNICATION`. Codec: Opus
  via `MediaCodec` (`audio/opus` encoder, check `MediaCodecList` at runtime) at about 24 kbps; fallback
  16-bit PCM (256 kbps) on Wi-Fi only. Over Bluetooth only Opus is allowed; without an Opus encoder the
  Bluetooth intercom is off and the UI says "Intercom needs Wi-Fi on this phone".
- AC10.7 **Encrypted audio.** Audio frames go over UDP on Wi-Fi (or the L2CAP channel on Bluetooth), each
  encrypted with AES-256-GCM under the per-direction **audio key** from AC9.4 (its own HKDF label, never
  the control key, so control and audio nonces can never collide). Nonce = 32-bit stream id || 64-bit
  frame counter; AAD = session id || stream id. UDP datagrams carry the session id in clear only to find
  the key; anything that fails to decrypt is dropped silently. Frames older than the 200 ms jitter window
  or replayed (sliding replay window) are dropped. A new stream id per PTT press / listen; counters never
  reset within a session. Control (listen, stop, PTT start/stop) goes over the encrypted session channel.
  Target mouth-to-ear latency on Wi-Fi under 300 ms. The Opus decoder needs its `csd-0/1/2` (OpusHead +
  pre-skip/seek-preroll) sent once per stream inside the encrypted `PttStart` / `IntercomListen` reply.
- Build notes M12: the receiver of a `PttStart` accepts it with an `IntercomListenReply` that carries its
  own audio port (UDP port on Wi-Fi, L2CAP PSM on Bluetooth), or refuses with `PttBusy`; the room phone
  answers `IntercomListen` the same way, with its codec csd. One audio crypto context per session keeps the
  64-bit send counter running across all streams of that session. The Opus decoder is configured at 48 kHz
  (what Android's decoder outputs) and played at that rate.
- AC10.8 **Alarm takes priority, and ends room mode.** If the phone enters PRE_ALARM or TRIGGERED,
  push-to-talk playback on it stops, **room mode is turned off and every listener is disconnected**, the
  mic closes, and the siren plays. Room mode cannot be turned on again until the incident ends. Otherwise
  a room left on would let a remote device listen to a thief, which is the phase-two alarm listening
  (section 10) that v1 does not build. Intercom never opens the mic because of an alarm.
- AC10.9 Unit tests: `IntercomPolicy` refuses listen when room mode is off, when the circle lacks
  Intercom, or for an unknown key; room mode cannot be started by any message type; entering PRE_ALARM /
  TRIGGERED ends room mode, drops listeners and closes the mic, and room mode is refused while an incident
  is open; mic-open state
  follows listener count; PTT floor control (busy on collision); audio frame crypto round-trip, replay
  and late-frame drop; codec selection (Opus present / absent, Wi-Fi / Bluetooth). Robolectric:
  `IntercomService` starts only with `microphone|connectedDevice` from an activity, posts the
  notification first, and refuses to start when notifications are blocked.

### F11. Photo email (round 3, opt-in, off by default)

Bill 2026-10-28 (approved): on trigger, photos are emailed automatically to a list of addresses the owner
chooses. This is the one way FAFO data can leave the owner's own devices, and only when set up.

- AC11.1 Settings > Photo email: an on/off switch (off by default), up to 5 recipient addresses, and the
  account to send from. Every build: the owner's own SMTP server (host, port, SSL/TLS on 465 or STARTTLS on
  587, username, app password, optional From). Play build only: one-tap Google sign-in with the
  `gmail.send` scope (send only; FAFO cannot read mail), sending through the Gmail API.
- AC11.2 No plaintext: SMTP is TLS-only with hostname verification; STARTTLS that the server does not offer
  fails closed; AUTH is never sent on an unencrypted connection. The app password is sealed with a
  non-exportable Android Keystore AES-256-GCM key in CE storage, never logged or shown. The key has no
  user-auth requirement because photos are sent while the phone is locked.
- AC11.3 Queue: the first trigger photo of an incident is sent alone and at once; later photos are batched
  (every 60 s or 5 photos), at most 20 messages per incident and one send per 30 s. Failures back off 30 s,
  1, 2, 5, then 15 min; permanent errors (bad password, rejected address) stop and show in Settings. Queued
  photos are protected from the photo cap until sent. The queue is files in CE storage and survives restarts.
- AC11.4 Sending happens only while a host is alive (the armed service, the open app, or Stay reachable)
  and only on a network with INTERNET and VALIDATED; captive portals are skipped. No WorkManager and no
  JobScheduler: mail queued when the owner disarms waits until the app is opened or FAFO is armed again.
- AC11.5 "Send test email" sends a small message with the shield right away and shows the result or the
  mapped error (e.g. 535: "Server refused the username or app password").
- AC11.6 Open Wi-Fi fallback (opt-in, Android 13+): see F12.

### F12. Open Wi-Fi fallback for queued photo email (round 3, opt-in)

- AC12.1 When photo email is waiting, there is no validated network for 60 s and no mobile data, FAFO reads
  the cached Wi-Fi scan results (and asks for a scan at most once per throttle window) and suggests the top
  5 open networks (no PSK/EAP/SAE/WEP; Enhanced Open allowed; signal above -80 dBm) to Android with
  `WifiNetworkSuggestion`. Android decides when to join.
- AC12.2 Mail is sent only once that network is VALIDATED. Captive portals (hotels, airports, cafes with a
  sign-in page) cannot be passed automatically and are never counted as connected.
- AC12.3 The suggestions are always removed once the queue is empty, after 30 min, or on disarm, so the
  phone is never left auto-joining open Wi-Fi. The suggested list is persisted so a crash cleans up.
- AC12.4 Android shows a one-time "Allow FAFO to suggest Wi-Fi networks?" approval the first time; a Setup
  step raises it in advance. Needs `ACCESS_WIFI_STATE` and `CHANGE_WIFI_STATE` (normal permissions) and the
  existing `NEARBY_WIFI_DEVICES` (`neverForLocation`); no location permission (AC6.5). Android 13+ only.

## 4. Settings and defaults

All settings live in DataStore. Settings needed at boot (marked DP) live in a device-protected DataStore.

| Setting | Values | Default | Storage |
|---|---|---|---|
| Unplug sensitivity | 0.1, 0.25, 0.5, 1, 2, 5 s | 0.5 s | DP |
| Siren sound | 2–3 bundled sounds | "Klaxon" | DP |
| Force max alarm volume | on/off | on | DP |
| Pre-alarm | 0, 1, 5, 10, 30, 60 s | 5 s | DP |
| Disarm on unlock (while armed, not triggered) | on/off | off | DP |
| Unlock grace window (Arm skips the prompt this long after an unlock; Disarm only after a lock and unlock after arming, AC1.7) | Off, 15, 30, 60, 120 s | 30 s | DP (the key itself lives in Keystore) |
| Arming countdown (ARMING before ARMED) | 3, 5, 10 s | 5 s | DP |
| Flash as alarm light (AC2.10) | on/off | on | DP |
| Motion can sound the alarm (AC4.2) | on/off | on | DP |
| Theme (app and status screen; round 3 replaces dark-only) | System / Light / Dark | System (follows the phone) | DP (the lock-screen status screen reads it before first unlock) |
| Motion chirps | Off / Low / Medium / High | Medium | DP |
| Photo on trigger | on/off | on | CE |
| Photo interval | 5–30 s (step 5) | 10 s | CE |
| Camera | Front / Back / Alternate | Front | CE |
| Photo storage cap | 25 / 50 / 100 / 200 MB | 100 MB | CE |
| Low-storage floor | fixed | 500 MB free | n/a |
| Trusted devices | list | empty | CE (public keys + metadata); private key in Keystore |
| This device's label | 1-24 chars | model name | CE |
| Circles | list of circles (name, members, labels, permissions) | "Home" (empty; Alerts + Reset + Disarm + Intercom) | CE |
| New circle permissions | Alerts / Reset / Disarm / Intercom | Intercom only ("Work" suggestion: Intercom + Reset) | n/a |
| Stay reachable | on/off | off | CE (resumes after BOOT_COMPLETED) |
| Intercom room mode | on/off, chosen circles | off; never persists across reboot | memory only |
| Chime when someone listens | on/off | on | CE |
| Intercom transport | Auto (Wi-Fi, then Bluetooth) | Auto | CE |
| Intercom codec | Auto (Opus, PCM fallback on Wi-Fi) | Auto | CE |

DP = device-protected storage (`createDeviceProtectedStorageContext()`), readable before first unlock.
CE = credential-protected (default) storage, readable only after first unlock.

Runtime state in DP: `armed` (bool), `awaiting_power` (bool, after a remote Reset while unplugged),
`incident_active` (bool), `incident_id`, `incident_started_at`, and the disarm unlock trail `armed_at`,
`last_screen_off_at`, `last_user_present_at`, `trail_boot_id` (AC1.7).

## 5. Storage rules

- **Device-protected storage holds only:** armed/incident flags, incident id + start time, DP settings.
  Never photos, keys or the trusted-device list.
- **Credential-protected storage holds:** photos, sidecar metadata, CE settings, trusted-device list,
  circles and labels, the beacon key (encrypted with a Keystore AES key).
- **Never stored:** intercom or push-to-talk audio (live only), session keys (memory only).
- **Keys:** identity key and command key (AC9.8, user-authentication-bound) in Android Keystore
  (non-exportable); peers' identity and command public keys in CE storage. No secrets in the repo or in
  plain files.
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
| `BLUETOOTH_SCAN` (`neverForLocation`), `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` | Trusted-device pairing, Bluetooth alerts/commands (F9) and Bluetooth intercom (F10). |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` (`maxSdkVersion="30"`) | Legacy pairing on Android 11. |
| `VIBRATE` | Chirps/siren vibration. |
| `INTERNET` | Android requires it for **any** socket, including local Wi-Fi ones (F9, F10). Used only for the local network; see the enforced rule below. |
| `ACCESS_NETWORK_STATE` | Find the Wi-Fi network and bind sockets to it (never mobile data). |
| `CHANGE_NETWORK_STATE` | Satisfies the `connectedDevice` foreground service prerequisite when no Bluetooth permission is granted (Wi-Fi-only users). Install-time. |
| `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` | Round 3, F12: read cached Wi-Fi scan results and add / remove `WifiNetworkSuggestion`s for the opt-in open Wi-Fi fallback. Normal (install-time) permissions; no location. |
| `NEARBY_WIFI_DEVICES` (`neverForLocation`) | Android 16's opt-in local network protection gate (AC9.10). Same Nearby devices group as Bluetooth, so no extra prompt. |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | `ReachableService` (Stay reachable) and `IntercomService`: keep the local link to the owner's own devices. |
| `RECORD_AUDIO` | Intercom room mode and push-to-talk only, asked just in time at first use, never at arm time. |
| `FOREGROUND_SERVICE_MICROPHONE` | `IntercomService` room mode, started only from the visible app. |
| `MODIFY_AUDIO_SETTINGS` | Voice-communication audio mode and speaker routing for intercom. |

Merged in by AndroidX libraries (not declared in FAFO's own manifest): `USE_BIOMETRIC` and
`USE_FINGERPRINT` (androidx.biometric, for `BiometricPrompt`; normal, install-time) and the app-private
signature permission `ws.crandell.fafo.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (androidx.core). The
manifest test allows exactly these on top of the list above (build note M13).

Later: `ACCESS_LOCAL_NETWORK` when targetSdk moves to 37 (Android 17 enforces local network protection).

**Enforced rule replacing "no INTERNET": no server of ours, no analytics.** Network traffic goes only to
(a) the owner's paired devices on the LAN or Bluetooth and (b) opt-in, off-by-default photo email (F11):
the user's own SMTP server (both flavors) or the Gmail API with the user's own Google account (play
flavor only). Nothing else. (Changed 2026-10-28, round 3; v1 now has opt-in outbound email.)
- No network or analytics libraries (OkHttp, Retrofit, Ktor, Cronet, Firebase, Sentry, ad SDKs). A Gradle
  verification task `checkNoRemoteDeps`, wired into `check` and run by CI, walks each flavor's resolved
  runtime classpaths. `oss` denies every Google Play services and Firebase group; `play` allows only an
  exact list of `play-services-auth` modules (Gmail sign-in) and denies the rest. A JVM unit test cannot
  see the Gradle classpath, so this is a Gradle task, not a test.
- No hardcoded non-local URLs or hosts: a test scans `app/src/main` and `app/src/oss` for URL literals,
  host-name literals and public IP literals (XML namespace URIs allowed); `app/src/play` may hold exactly
  the Gmail send endpoint and the `gmail.send` scope. SMTP hosts always come from the user.
- `EgressTest`: sockets and HTTP connections may be opened only in `net/` (LAN peers), the intercom's
  audio sockets, `mail/SmtpClient.kt` and the play flavor's `GmailSender.kt`.
- `LocalAddressPolicy` refuses non-private addresses at runtime (AC9.2). No `WebView`.
- `network_security_config` with `cleartextTrafficPermitted="false"` as a belt-and-braces default.

**Not requested in v1:** `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`,
`SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, any location permission (including `ACCESS_BACKGROUND_LOCATION`),
`CHANGE_WIFI_MULTICAST_STATE` (NSD does mDNS in the system), `SYSTEM_ALERT_WINDOW`, `READ_PHONE_STATE`,
contacts, `CAPTURE_AUDIO_OUTPUT`, device admin, accessibility service. A manifest test enforces the
exact permission list.

## 7. Threat model and known limits

- **Email credentials on the device (F11, round 3).** The SMTP app password is sealed by a Keystore key
  without user authentication, because alarm photos are sent while the phone is locked. A rooted attacker
  with the running app could use it; FAFO recommends a revocable app password, never the main password.
  The Gmail flavor holds only an OAuth grant for `gmail.send`, revocable in the Google account.
- **Open Wi-Fi (F12).** The phone may join an open network the owner did not choose, only while photo
  email is queued and only through the owner's opt-in. Traffic over it is TLS (SMTP with hostname
  verification, Gmail over HTTPS), so the network sees only encrypted data. Suggestions are removed after.

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
- **No internet alerts in v1.** Only nearby trusted devices (same Wi-Fi or Bluetooth range) get an alert, and photos are never sent (alerts carry no photos). Photos stay on the phone; if the phone is never recovered, they are lost.
- **Abuse:** the app protects only the device it runs on, shows a persistent notification whenever armed,
  uses a unique icon and name, never hides or disguises itself, and has no remote monitoring in v1.

**Local network and circles (F8-F10):**
- **Stranger on the same Wi-Fi:** sees a `_fafo._tcp` record with a random name and a rotating tag; cannot
  complete a handshake without a trusted key; unknown keys get no reply. Can still flood or block (denial
  of service); the alarm itself never depends on the network.
- **Replay / man in the middle:** pairing is SAS-authenticated (F6); sessions are signed ephemeral ECDH
  with per-direction AES-GCM and counters, so recorded traffic cannot be replayed or decrypted later.
- **Stolen trusted phone:** cannot reset or disarm without its owner's credential at send time
  (AC9.8, enforced by the auth-bound command key, not only the UI). The owner removes it from the other devices (credential required).
- **Thief with a trusted phone and its PIN:** can reset or disarm, if that phone's circle allows. That is
  why Reset and Disarm are separate per-circle permissions, need the credential to grant, and new circles
  default to intercom only. A Reset while the phone is unplugged leaves it armed but waiting for power,
  so a thief holding a Reset-capable phone plus its PIN can walk off with it silently; only grant Reset to
  people you would hand your phone to.
- **No remote help before first unlock:** after a reboot during an incident, alerts and remote
  reset/disarm wait for the first unlock (CE storage and Keystore are off-limits in direct boot).
- **Intercom misuse (listening to someone):** room mode is started only on the listening phone by the
  person holding it unlocked, never remotely or from boot; persistent notification names who is
  listening; a chime on connect; the system mic indicator; nothing is recorded; pairing and joining a
  circle need both owners' credentials. Known limit: someone who knows a person's PIN and has their
  phone in hand can set it up, as with any intercom app; the notification and indicator stay visible.
- **Public Wi-Fi:** client isolation usually blocks peers; Bluetooth is the fallback. Labels are never
  broadcast in clear. A stranger who connects learns nothing about the listening phone's identity
  (initiator proves itself first, AC9.4).
- **Peer lying about itself:** a trusted peer can suggest any label, but local labels win and collisions
  get a suffix (AC8.2); its claimed permissions are never trusted, each device enforces its own circles.
- **Room mode left on when an alarm starts:** room mode ends and listeners are dropped (AC10.8).

## 8. Play policy posture (for later Play release)

- **Round 3 additions.** Photo email: the play flavor requests the sensitive (not restricted) `gmail.send`
  scope, which needs OAuth consent-screen verification once there are more than 100 users and an Android
  OAuth client for `ws.crandell.fafo` with the debug and Play App Signing SHA-1s (no client secret in the
  repo). Data safety: photos are shared with recipients the user chooses, sent from the user's own
  account; the privacy policy must say so. Wi-Fi: `ACCESS_WIFI_STATE` / `CHANGE_WIFI_STATE` are normal
  permissions; suggestions need the user's one-time approval. Torch: no permission. The `specialUse` FGS
  justification gains "sends alarm photos the user configured".

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
- **Intercom must not look like stalkerware.** Name it "Intercom" and "Push-to-talk", never "baby
  monitor", "listen in", "spy" or "monitor". The listing says it works only between your own devices that
  you paired with your screen lock, only when someone turns on intercom on the listening phone, with a
  notification and mic indicator the whole time, and that nothing is recorded. Screenshots show the
  notification. No hidden mode, no remote start.
- Foreground service declarations for `microphone` (intercom room mode, user-started, visible) and
  `connectedDevice` (local link to the user's own paired devices), each with a screen recording. Risk:
  "Stay reachable" holds `connectedDevice` for hours while mostly idle, which review may question the way
  the idle camera type might. Fallback if rejected: drop `ReachableService`; alerts still arrive through
  the background BLE scan, and push-to-talk then reaches only phones with the app open or room mode on.
- `RECORD_AUDIO` in an anti-theft app draws stalkerware scrutiny. Keep the intercom a clearly separate,
  visible screen; never request the mic at arm time or from onboarding.
- Data safety: alerts and audio go only device-to-device on the local network, end-to-end encrypted, and
  never reach the developer. Confirm the exact Data safety answers against Play's current definitions at
  submission time.

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
- NSD discovery between two phones on Bill's home Wi-Fi; time to discover.
- Local network with `adb shell am compat enable RESTRICT_LOCAL_NETWORK ws.crandell.fafo` (Android 16
  opt-in): fails without `NEARBY_WIFI_DEVICES`, works with it.
- Whether the A17 has an `audio/opus` `MediaCodec` encoder; intercom latency and echo on speaker.
- Background BLE `PendingIntent` scan delivery for alert beacons under Samsung battery settings.
- BLE L2CAP channel throughput for Opus audio between the A17 and a second phone.
- Push-to-talk playback on a locked receiver with Stay reachable on.

## 9a. Dropped

- **SMS reset:** dropped by Bill on 2026-10-28 ("just not do the text part at all"). Do not build any SMS
  feature. Remote reset/disarm comes only from trust-circle members over Bluetooth or local Wi-Fi.

## 10. Phase two (NOT v1)

> **None of this is built in v1.** No server code and no internet traffic in v1. v1 does have local-only
> networking (F9) and an open-use intercom (F10); neither reaches beyond the local Wi-Fi or Bluetooth.
> Listed here so v1 choices don't block it. Phase two is planned as paid later (about $5/month); v1 stays free.

- **Relay alerts:** send incident alerts and queued photos to the owner's trusted devices through a small
  relay (for example a Cloudflare Worker) with end-to-end encryption using the paired public keys, so the
  relay never sees content. Alternatives: the owner's own email or Telegram. Photos are deleted from the
  phone only after a confirmed send. Uses a user-initiated data transfer job or the existing FGS, not `dataSync`.
- **Remote silence over the internet:** v1 does reset/disarm only nearby (F9); phase two carries the
  same signed command through the relay.
- **Remote mic during an alarm (listen/speak, over the relay or locally), four rules, all mandatory.**
  This is separate from the v1 intercom (F10), which only works when someone turns on room mode on the
  listening phone.
  1. The mic opens only while an alarm is sounding.
  2. Only the owner's own paired device can open it.
  3. The phone shows the system mic-in-use indicator the whole time.
  4. Mic access is obtained when the user arms (the service starts with the `microphone` type at arm time),
     because Android will not start the mic cold in the background. Consequence: after a reboot the mic
     is unavailable until the owner unlocks, same as the camera (boot cannot start mic services).
  Listening to another person at any other time without the open room mode of F10 is spyware under Play
  policy and is never built.
- **Bluetooth mesh store-and-forward:** nearby FAFO phones relay encrypted alerts when the stolen phone has no network.
- **Lost-bond handling:** Android 16 `ACTION_KEY_MISSING` / `ACTION_ENCRYPTION_CHANGE` and
  `CompanionDeviceManager.removeBond()` for trusted-device repair.
