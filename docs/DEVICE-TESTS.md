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

## Results

(Bill: write findings here, newest first.)
