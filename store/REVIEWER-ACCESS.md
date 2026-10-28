# Reviewer access (Play Console > Policy > App content > App access)

FAFO has no account or login, but the alarm only arms on a charging phone with a screen lock, so give
the reviewer instructions. Select **All or some functionality in my app is restricted** > Add
instructions, and paste the blocks below.

- Name: `Reviewer setup (no account)`
- Username: `Not required`
- Password: `Not required`
- Any other information: the text below (fits the field; trim the optional parts if it is too long).

```
FAFO Security has no account or login and talks to no server.

Core alarm (one phone or an emulator, no special hardware):
1. Set a screen lock (PIN) on the device. The app needs one to arm.
2. Open the app and finish the Setup screen (notifications, camera, full-screen alerts, battery). Setup > Self-test plays a 1 s chirp and siren and takes a test photo without arming.
3. Connect a charger (any USB cable). On an emulator: Extended controls > Battery > Charger connection: AC charger.
4. Home > ARM, confirm with the PIN or fingerprint. An "Armed" notification appears.
5. Unplug the charger (emulator: Charger connection: None, or adb emu power ac off). A 5 second countdown beeps, then the siren plays at alarm volume and the red alarm screen shows over the lock screen.
6. Unlock the device to stop it. Trigger photos are listed under Home > Incidents (an emulator uses its virtual camera).
7. Optional: Settings > Pre-alarm sets the countdown (0, 1, 5, 10, 30 or 60 s); motion bumps chirp (emulator: Virtual sensors > Accelerometer).

Trusted devices and intercom (optional, two phones on the same Wi-Fi with the app installed):
Devices > Add paired device, then tap Show this phone on one and Find the other phone on the other, hold them close, compare the six-digit code on both, confirm with the screen lock. Then Intercom > hold to talk. Trigger the alarm on one phone to see the alert on the other. These features use only the local network or Bluetooth, so they cannot be shown across the internet; two emulators on one computer usually cannot see each other.

Photo email (only in builds that include it): Settings > Photo email. Use any email account's SMTP settings, or Sign in with Google (permission requested: send email only). Nothing is sent to the developer.
```

Notes for Bill
- Emulator controls named above are in the Android Emulator's Extended controls (Battery, Virtual
  sensors, Fingerprint). `adb emu power ac off` / `on` switch charger state from the command line, and
  `adb emu finger touch 1` taps an enrolled fingerprint.
- If the round 3 build has photo email, re-read step "Photo email" against the real menu labels.
- Keep the pre-launch report on: its crawler cannot arm (needs a PIN and charger state), so expect it to
  stop at Home; that is normal.
