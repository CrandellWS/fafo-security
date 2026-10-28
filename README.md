# FAFO Security

**Free Advanced Features Offline.** An Android anti-theft app for your own phone.

Arm it while the phone is charging. If someone unplugs it before you unlock, a short warning countdown
plays (plug it back in to cancel), then a siren goes off and
keeps going until you unlock, even after a reboot. Bumps and pick-ups chirp, the front camera takes
photos while the alarm is active, and only devices you paired with your real screen lock count as trusted.

Trusted devices are grouped into circles (Home, Work...) with your own labels ("Register", "Back room")
and per-circle permissions. Nearby trusted devices, on the same Wi-Fi or over Bluetooth, get an alert
when your phone is triggered and can reset the alarm (siren off, stays armed) or disarm it if their circle allows. Devices in a circle
can also use a simple intercom and push-to-talk walkie-talkie on the same Wi-Fi or Bluetooth.

v1 is free and needs no internet: no server, no account, no analytics, no remote endpoints. All traffic
stays between your own paired devices on the local network or Bluetooth, end-to-end encrypted.

- Platform: Android only (minSdk 30, targetSdk 36), native Kotlin + Jetpack Compose
- Test device: Samsung Galaxy A17, Android 16
- Spec: [docs/SPEC-v1.md](docs/SPEC-v1.md)
- Build plan: [docs/BUILD-PLAN.md](docs/BUILD-PLAN.md)
- Build agent front door: [CLAUDE.md](CLAUDE.md)

## Install the test build (debug APK from CI)

1. Open the repository's **Actions** tab on GitHub, pick the latest green **Android CI** run on `main`,
   and download the **fafo-debug-apk** artifact (a zip). Unzip it to get `app-debug.apk`.
2. Copy the APK to the phone (USB, or download it on the phone from the same Actions page while signed in).
3. On the Samsung Galaxy A17 (Android 16): open the APK from **My Files**. When asked, allow
   **Install unknown apps** for My Files (Settings > Apps > My Files > Install unknown apps), then install.
   If Play Protect warns about an unknown developer, choose **More details > Install anyway**: it is a
   debug build signed with a CI debug key.
4. Open FAFO Security and work through the setup checklist (notifications, camera, full-screen alarm,
   Do Not Disturb access, battery Unrestricted, Samsung sleeping-apps steps).
5. Updating: install the newer APK over the old one. Each CI run signs with its own debug key, so if Android
   says the package conflicts, uninstall first (this deletes FAFO's photos, trusted devices and keys).
6. What to test, step by step: [docs/DEVICE-TESTS.md](docs/DEVICE-TESTS.md).

FAFO Security protects the device it is installed on. It is not a tool for tracking or monitoring
another person, and it never hides itself: while armed it always shows a persistent notification.
The intercom only works when someone turns it on at the listening phone, shows a notification naming
who is listening plus the system mic indicator, cannot be started remotely and records nothing.

License: MIT (see [LICENSE](LICENSE)).
