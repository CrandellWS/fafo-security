# Device feedback 2026-10-28 (Bill, A17, internal test build 1 / 0.1.0-play)

- Pre-alarm felt far too fast. Bill set the unplug sensitivity to 2 s.
- Bug: after picking the phone up, setting it down and not touching it, the alarm still fired about 3 s later. Expected: once the phone settles, the motion pre-alarm should cancel (or at least not escalate).
- Bump detected stays on screen too long (60 s): bump reset time setting in progress (default 15 s).
- Feature: while armed with the screen off, touching or moving the phone wakes the screen and shows a big HANDS OFF warning over the lock screen (showWhenLocked + turnScreenOn, via full-screen intent). Bill: 'that's what I want'.
- Idea for later: share the phone's location when the alarm fires.
- Feature: when a trigger photo is taken, show the thief it happened (shutter flash/sound plus 'PHOTO TAKEN' with the photo thumbnail on the alarm / hands-off screen).
