# FAFO Security: device tests for Bill (Samsung Galaxy A17, Android 16)

The build session has no phone or emulator. Everything below needs a real phone (or two). Install the
debug APK from the latest green GitHub Actions run (artifact `fafo-debug-apk`), then work down the list.
Mark each item with the date and what you saw. Anything that fails: note it under "Results" with the
exact steps, and the next build session fixes it.

## Setup before testing

- Set a PIN, pattern or password. Turn off Smart Lock / Extend Unlock.
- Open FAFO, go through the setup checklist (notifications, camera, full-screen alarm, DND access,
  battery Unrestricted, Samsung sleeping-apps steps).

## M5 Siren

- [ ] Arm while charging, lock, unplug: pre-alarm beeps (5 s default) get faster, then the full siren.
- [ ] Siren is loud with the phone in **silent** mode and with the **screen off**.
- [ ] Volume buttons do not stop or lower the siren (it comes back to max within about 1 s).
- [ ] Swiping the notification away does not stop the siren; the notification comes back.
- [ ] Full-screen red alarm screen appears over the lock screen.
- [ ] Unlocking with your PIN (or fingerprint) stops the siren within half a second.
- [ ] Plug back in during the pre-alarm countdown: beeps stop, stays armed.
- [ ] Plug back in after the full siren started: siren keeps going until unlock.
- [ ] DND on (Alarms allowed): siren plays. DND "Total silence": note what happens.
- [ ] Setup checklist "Self-test": 1 s chirp then 1 s siren at alarm volume.

## M6 Reboot

- [ ] Trigger the alarm, then hold power and restart (if the power menu is reachable while locked; note
      whether One UI "Lock network and security" blocks it). Siren returns before you unlock. Time from
      boot to siren (target under 30 s).
- [ ] Reboot during the pre-alarm countdown: siren returns at full volume (no second countdown).
- [ ] After that reboot, unlock: siren stops. No photos were taken after the reboot (expected in v1).
- [ ] Armed and plugged, reboot: notification "Armed after reboot, open the app to enable photos".
- [ ] Armed and plugged, power off, unplug, power on, leave locked: window, pre-alarm, siren.
- [ ] Alarm running, then the system kills the app (or `adb shell am kill ws.crandell.fafo`): siren
      resumes; tapping the alarm notification / alarm screen resumes photos.

## M7 Motion chirps

- [ ] Armed on a table (Medium): tap the table firmly: one short chirp. Pick the phone up: a louder
      triple chirp. Chirps never come faster than one per 1.5 s.
- [ ] Phone on a table next to a buzzing phone / running washer: no chirps (note any false chirps and
      which sensitivity).
- [ ] Low / High feel right (High catches light taps, Low needs a real knock). Off: no chirps.
- [ ] Motion never starts the siren; during the alarm there are no chirps.

## M8 Photos

- [ ] Arm (camera allowed), lock, unplug: first photo within 2 s of the pre-alarm start, then every
      10 s. After unlocking, Incidents shows the incident with its photos; front camera by default.
- [ ] Camera setting Back and Alternate work.
- [ ] Photos are taken while the screen is off and locked (camera foreground service while locked).
- [ ] Plug back in during the pre-alarm: incident shows "Cancelled (plugged back in)", photos kept.
- [ ] Share a photo through the share sheet; delete a photo; delete an incident.
- [ ] After a process kill (not a reboot), tapping the alarm notification / alarm screen resumes photos.
- [ ] Self-test takes a test photo ("Test photo OK").

## M9 Pairing (two phones, both with this APK)

- [ ] Open Trusted devices on both phones: one Android "Nearby devices" prompt (no location prompt).
- [ ] Phone A: "Show this phone". Phone B: "Find the other phone". Both show the same 6-digit code.
      (Android 11 phones get the system device picker instead of a scan: pick the FAFO entry.)
- [ ] Tap "Codes match" on both; each asks for its own PIN/pattern/password (a fingerprint is not
      offered). Both list the other phone with name, fingerprint and today's date.
- [ ] Tap "Don't match" on one phone: neither phone saves anything.
- [ ] Cancel the PIN prompt on one phone: neither phone saves anything.
- [ ] Phone with no screen lock: "Set a screen lock first", pairing does not start.
- [ ] Remove a trusted device: asks for the PIN; the device disappears.
- [ ] Bluetooth off: "Turn on Bluetooth first".

## M10 Circles, Wi-Fi, remote reset / disarm (two phones on the same home Wi-Fi)

- [ ] Pair A and B starting from Circles > Home > "Add trusted device" on A. After the codes match and
      both PINs, B shows "A invites you to Home" with four switches (alerts, reset, disarm, push-to-talk).
      Accept on B (asks B's PIN because Reset/Disarm are on). Both Circles screens list the other phone.
- [ ] Circles: rename "This device is called" on B; rename B's label on A ("Register"): A keeps its own label.
- [ ] Create "Work" on A (Intercom + Reset preselected; Reset asks the PIN). "Add paired device" > B:
      B gets the invite notification and the accept screen. Turn Reset off before accepting: A cannot reset B
      under Work.
- [ ] Remove a member (asks PIN); remove a circle (asks PIN); toggling a power off asks nothing.
- [ ] Arm B (plugged, locked). Keep FAFO open on A (or A armed). Unplug B: within a few seconds A shows
      "B: ALARM (unplugged hh:mm)" (or "pre-alarm") with Reset and Disarm buttons.
- [ ] Tap Reset on A: A asks A's PIN/fingerprint, then "B was reset. It stays armed." B's siren stops,
      B chirps once, B shows "Armed, waiting for power"; plug B in and unplug again while locked: alarm again.
- [ ] Repeat and tap Disarm: B's siren stops and B is disarmed. B's incident shows "disarmed by A".
- [ ] Cancel the PIN prompt on A: nothing happens on B.
- [ ] A in Work only (Reset): no Disarm button; Reset works.
- [ ] Wi-Fi with client isolation (guest network): no alert over Wi-Fi (expected until Bluetooth, M11).
- [ ] Android 16 local network protection: `adb shell am compat enable RESTRICT_LOCAL_NETWORK ws.crandell.fafo`,
      reboot. With "Nearby devices" denied: no discovery. Grant it: alerts and reset work again.
- [ ] Remove B on A's Trusted devices (PIN): B disappears from every circle on A.

## M11 Bluetooth, Bluetooth alerts, Stay reachable (two phones)

- [ ] Wi-Fi off on both phones (Bluetooth on). Arm B, lock, unplug: A (screen off, FAFO closed, not
      reachable) gets "B: ALARM" within about a minute from the background Bluetooth scan, then the full
      alert with Reset / Disarm once it connects. Note the delay, and repeat with Samsung battery settings
      at default and at Unrestricted.
- [ ] Tap Reset on A over Bluetooth only: B resets (siren off, stays armed).
- [ ] Settings > Stay reachable on A: notification "Reachable by: Home" stays; Stop on the notification
      turns it off (and the switch). Reboot A, unlock: the notification comes back by itself.
- [ ] Reboot A with Stay reachable on and do not unlock: no "Reachable" notification until the first unlock.
- [ ] Both on the same Wi-Fi again: alerts and reset use Wi-Fi (faster than Bluetooth).

## M12 Intercom and push-to-talk (two phones, same Wi-Fi, then Bluetooth only)

- [ ] Intercom screen asks for the microphone the first time (not during onboarding or arming).
- [ ] B: Intercom room mode on (Home). Notification "Intercom on: Home can listen" with Stop.
- [ ] A: "Listen to B": B chimes, B's notification changes to "A is listening", B's status bar shows the
      green mic indicator. A hears B's room. Stop on A: B's mic indicator goes away within about a second.
- [ ] Push-to-talk A to B (hold Talk): B beeps, shows "A is talking", plays it; release: end beep.
      Latency feels under a third of a second on Wi-Fi; check echo with both on speaker.
- [ ] Push-to-talk to "All" with three phones.
- [ ] A and C press Talk to B at the same time: busy tone, the later one is dropped.
- [ ] Room mode on B, A listening; unplug B while armed and locked: room mode ends, A is dropped, mic
      indicator off, siren plays. Room mode cannot be turned on during the alarm.
- [ ] Reboot B with room mode on: room mode is off after the reboot.
- [ ] Bluetooth only (Wi-Fi off): listen and push-to-talk work if both phones have an Opus encoder
      (note whether the A17 has one); otherwise the app says "Intercom needs Wi-Fi on this phone".
- [ ] Notifications turned off for FAFO while room mode is on: room mode stops.

## Results

(Bill: write findings here, newest first.)
