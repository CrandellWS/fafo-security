# FAFO Security

**Free Advanced Features Offline.** An Android anti-theft app for your own phone.

Arm it while the phone is charging. If someone unplugs it before you unlock, a short warning countdown
plays (plug it back in to cancel), then a siren goes off and
keeps going until you unlock, even after a reboot. Bumps and pick-ups chirp, the front camera takes
photos while the alarm is active, and only devices you paired with your real screen lock count as trusted.

v1 is free, works fully offline and has no server, no account, no analytics and no remote listening.

- Platform: Android only (minSdk 30, targetSdk 36), native Kotlin + Jetpack Compose
- Test device: Samsung Galaxy A17, Android 16
- Spec: [docs/SPEC-v1.md](docs/SPEC-v1.md)
- Build plan: [docs/BUILD-PLAN.md](docs/BUILD-PLAN.md)
- Build agent front door: [CLAUDE.md](CLAUDE.md)

FAFO Security protects the device it is installed on. It is not a tool for tracking or monitoring
another person, and it never hides itself: while armed it always shows a persistent notification.

License: MIT (see [LICENSE](LICENSE)).
