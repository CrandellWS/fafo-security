# FAFO Security: build status

Updated after every milestone. Source of truth for what is done: this file plus `git log`.

## Environment (M0)

Built on Bill's workstation (Ubuntu, no sudo used), not a cloud container:

- JDK: Temurin 17.0.21 unpacked to `~/.local/opt/jdk-17` (no apt, no sudo).
- Android SDK: `~/android-sdk` with cmdline-tools 13114758, `platform-tools` 37.0.1,
  `platforms;android-36` (r2), `build-tools;36.0.0`.
- Gradle: 8.14.3 (wrapper committed in M1).
- Network: `dl.google.com`, `maven.google.com`, `services.gradle.org`, `plugins.gradle.org`,
  `repo.maven.apache.org` all reachable (HTTP 200). Nothing blocked.
- No emulator (no device work here); device checks go to `docs/DEVICE-TESTS.md`.

## Milestones

| Milestone | State | Tests |
|---|---|---|
| M0 Environment | done | `sdkmanager --list_installed` shows platform 36 + build-tools 36.0.0; `java -version` 17 |

## Open questions for Bill

- None yet.
