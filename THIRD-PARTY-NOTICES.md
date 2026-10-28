# Third-party notices

FAFO Security is licensed under GPL-3.0-or-later ([LICENSE](LICENSE)). It includes or depends on the
third-party components below, each under its own license. The FAFO names and artwork are not covered by
any of these licenses; see [TRADEMARKS.md](TRADEMARKS.md).

## Bundled in this repository

| Component | Files | License |
|---|---|---|
| Barlow Condensed (Bold, ExtraBold), Copyright 2017 The Barlow Project Authors | `app/src/main/res/font/barlow_condensed_*.ttf`, `store/brand/barlow_condensed_*.ttf` | SIL Open Font License 1.1. Full text: `app/src/main/assets/licenses/BarlowCondensed-OFL.txt` (shipped in the app) and `store/brand/BarlowCondensed-OFL.txt` |
| Gradle Wrapper | `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` | Apache License 2.0 |

## Libraries linked into the app (fetched by Gradle, not stored here)

Versions are in `gradle/libs.versions.toml`.

| Component | License |
|---|---|
| AndroidX: core-ktx, activity-compose, fragment-ktx, lifecycle (runtime, compose, service, viewmodel-compose), datastore-preferences, camera (core, camera2, lifecycle), biometric | Apache License 2.0 |
| Jetpack Compose (ui, ui-graphics, ui-tooling-preview, material3, material-icons-core) | Apache License 2.0 |
| Kotlin standard library, kotlinx-coroutines, kotlinx-serialization-json | Apache License 2.0 |
| **play flavor only:** Google Play services Auth (`com.google.android.gms:play-services-auth` and the modules it pulls in) | Android Software Development Kit License (proprietary, Google) |

The **oss** flavor (the F-Droid / open-source build) contains no Google Play services or other
proprietary code; the `checkNoRemoteDeps` Gradle task enforces this. The **play** flavor adds Google
sign-in for Gmail sending and links the proprietary Play services Auth library listed above.

## Build and test tools (not shipped in the app)

| Component | License |
|---|---|
| Android Gradle Plugin, Kotlin Gradle plugins | Apache License 2.0 |
| JUnit 4 | Eclipse Public License 1.0 |
| Robolectric, Google Truth, AndroidX Test | Apache License 2.0 |
| Roborazzi | Apache License 2.0 |
