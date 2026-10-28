# Permission and foreground service declarations

Play Console > Policy > App content. Each foreground service (FGS) type needs: a functionality
description, the user impact if the system defers or interrupts it, a video link, and a use case from
Play's list (or typed in). https://support.google.com/googleplay/android-developer/answer/13392821
FGS types and prerequisites: https://developer.android.com/develop/background-work/services/fgs/service-types

Videos: one unlisted YouTube video can cover all four types if it is chaptered, but separate short
videos per type review faster. Record on the A17 with the screen recorder, show the notification shade
each time (the persistent notification is the evidence that the user sees the service).

## FGS `specialUse` (ArmService, manifest subtype already set)
- Manifest subtype: "Anti-theft alarm: detects charger removal and sounds a siren on the owner's own
  device; must resume from boot"
- Use case: not in Play's list; enter manually: **Anti-theft charger alarm**.
- Description: "While the user has armed the alarm, this service listens for the charger being
  unplugged and for motion, and plays the siren and pre-alarm countdown. The user starts it by tapping
  ARM in the visible app and confirming with their screen lock; a persistent notification shows the whole
  time. If the phone restarts during an alarm, the service resumes the siren from boot until the owner
  unlocks. No other type fits: it is not media playback or an alarm clock, and it must start from
  LOCKED_BOOT_COMPLETED, which camera, microphone and media types cannot."
- Impact if interrupted: "The phone stops watching for the charger being unplugged, so a thief can take
  it silently; an alarm already sounding goes quiet before the owner unlocks."
- Video: arm while plugged in (fingerprint prompt) > notification shade showing "Armed" > unplug >
  countdown > siren > unlock stops it. Then restart during an alarm and show the siren resuming.

## FGS `camera` (same ArmService)
- Use case: **Background Camera Streaming** (closest listed). If the form allows text, add: "photos
  only during an alarm".
- Description: "When the alarm goes off, the service takes a photo with the front camera, then one every
  5 to 30 seconds (user setting) until the owner unlocks. Photos are saved on the phone. The camera type is
  added only when the user arms from the visible app or taps the alarm notification; it is never started
  from boot."
- Impact: "No photos of the person who took the phone."
- Video: arm > unplug > siren > unlock > Incidents shows the new photos.
- Known risk (spec section 8): holding the camera type for hours while armed and idle may be
  questioned. Fallback: drop the type and take photos only while the alarm screen is visible.

## FGS `microphone` (IntercomService)
- Use case: **Background Audio Access**.
- Description: "Intercom room mode between the user's own paired devices on the same Wi-Fi or Bluetooth.
  The person at the phone turns room mode on in the visible app; a notification names who is listening
  and the system microphone indicator shows the whole time. Nothing is recorded. It can never be started
  remotely, from boot or by a restart, and it ends when an alarm starts."
- Impact: "Room mode stops and paired devices can no longer hear or talk to that room."
- Video: Intercom > choose circles > turn Room mode on > pull down the notification ("Back room
  listening") and show the green mic indicator > second phone taps Listen > turn Room mode off.
- Never use the words baby monitor, monitor, listen in, spy (spec section 8).

## FGS `connectedDevice` (ReachableService, IntercomService)
- Prerequisite met by CHANGE_NETWORK_STATE (manifest) or a granted BLUETOOTH_* permission.
- Use case: **Continuous Data Transfer to an External Device**.
- Description: "Keeps an end-to-end encrypted local link (same Wi-Fi via network service discovery, or
  Bluetooth) to the user's own paired phones, so they get an alert the moment this phone's alarm goes
  off and can send a reset or disarm the user allowed. Turned on by the user (Stay reachable) with a
  persistent notification."
- Impact: "Paired phones nearby are not alerted when this phone is taken, and push-to-talk does not reach it."
- Video: Devices > pair with code > Settings > Stay reachable on > notification > trigger alarm on
  phone A > alert appears on phone B > B taps Reset > A's siren stops.
- Known risk: idle hours on connectedDevice; fallback drops ReachableService (BLE scan alerts remain).

## USE_FULL_SCREEN_INTENT
- Form question: is the core functionality calling or alarm (default-granted)? Answer honestly:
  "**Yes, alarm**: a security alarm that must show its stop screen over the lock screen when it sounds."
  Play may decide it is not an "alarm clock" alarm; the app already routes the user to grant "Full screen
  notifications" in Setup and degrades to a heads-up notification if declined (required for apps
  without the default grant). Deadline history: declaration required since 2024-05-31; default grant
  limited to calling/alarm apps since 2025-01-22.

## REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
- Policy (no dedicated Console form found as of 2026-10-28; reviewers may ask): exemption allowed only
  when Doze breaks the core function. Listed acceptable case: "Safety app". Justification to keep ready:
  "Anti-theft alarm that must react within a second to the charger being unplugged and keep the siren
  running; Samsung battery management otherwise stops the armed service."
  https://developer.android.com/training/monitoring-device-state/doze-standby#exemption-cases
- Check Policy > App content and the pre-launch/policy status page at submission for any new prompt.

## Runtime permissions with no Play form
CAMERA, RECORD_AUDIO (asked at first intercom use only, never at arm time), POST_NOTIFICATIONS,
BLUETOOTH_SCAN/ADVERTISE/CONNECT (neverForLocation), NEARBY_WIFI_DEVICES (neverForLocation),
ACCESS_NOTIFICATION_POLICY (special access), CHANGE_NETWORK_STATE, WAKE_LOCK, VIBRATE,
RECEIVE_BOOT_COMPLETED, MODIFY_AUDIO_SETTINGS. Torch / camera flash (CameraManager.setTorchMode):
no permission needed.

## Round 3 watch-outs (check the r3 manifest before submitting)
- **Open-Wi-Fi fallback needs location if it scans.** WifiManager.getScanResults()/startScan() require
  ACCESS_FINE_LOCATION even on Android 13+ with NEARBY_WIFI_DEVICES
  (https://developer.android.com/develop/connectivity/wifi/wifi-permissions). Scanning while the alarm runs
  in the background would be background location: ACCESS_BACKGROUND_LOCATION and Play's location
  declaration, and it breaks spec rule "no location permission". Options: (a) leave the open-Wi-Fi
  fallback out of the Play build; (b) only suggest networks without scanning; (c) accept the location
  permission and its declaration. Decide before release.
- addNetworkSuggestions needs CHANGE_WIFI_STATE (install-time, no form); from general Android documentation, not confirmed on the
  wifi-permissions page above, so check the r3 manifest and a test on the A17.
- Photo email adds INTERNET use beyond the LAN: update spec section 6 / CLAUDE.md rule text and the
  Data safety answers (DATA-SAFETY.md column B).
- Gmail sign-in adds play-services-auth (play flavor only): no Play form, but OAuth setup in GOOGLE-CLOUD-OAUTH-SETUP.md.

## Only if option (c) is chosen: location declaration (Policy > App content > Sensitive permissions)
Needed only if the Play build keeps a fallback that scans for open Wi-Fi while the alarm runs
(ACCESS_FINE_LOCATION + ACCESS_BACKGROUND_LOCATION). https://support.google.com/googleplay/android-developer/answer/9799150
- Feature that uses background location: "Photo email fallback: while the theft alarm is sounding and the phone
  has no connection, FAFO scans for open Wi-Fi networks so it can send the trigger photos the user chose to
  email. Android requires the location permission to read Wi-Fi scan results. FAFO never reads, stores or
  sends a location."
- Video: turn on photo email > grant location "Allow all the time" > arm > disable mobile data and Wi-Fi >
  unplug > alarm > the app suggests an open network > approve > email arrives.
- This also requires a prominent in-app disclosure before the permission prompt, a spec change (spec rule
  "no location permission"), and a Data safety re-check. Options (a) and (b) avoid all of this.
