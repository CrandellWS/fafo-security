# Shared debug keystore (public, debug only)

`debug.keystore` signs **debug builds only**, both on CI and on local machines, so a debug APK from any
source installs over the previous one. It is public on purpose: anyone can build an APK with this key, so
a debug build proves nothing about who made it.

- Store and key password: `android`; alias: `androiddebugkey` (the standard Android debug values).
- Never use this key for a release build. The release key is never committed to this repository.
- F-Droid builds and signs with its own key, so an F-Droid install and a CI/Play install cannot update
  each other.

Fingerprints (needed for the play flavor's Android OAuth client and checked by CI):

```
SHA-256: 9A:B9:9D:D8:04:08:9B:3C:B8:26:D5:D4:85:F1:29:A5:32:EE:99:53:17:E6:5B:E5:53:A3:2C:3D:81:12:A9:05
SHA-1:   2A:A7:8B:2A:6F:A1:BD:0B:38:CA:95:DE:73:52:94:B9:60:49:29:B7
```
