# FAFO Security: device tests for Bill (Samsung Galaxy A17, Android 16)

The build session has no phone or emulator. Everything below needs a real phone (or two). Install the
debug APK from the latest green GitHub Actions run (artifact `fafo-debug-apk`), then work down the list.
Mark each item with the date and what you saw. Anything that fails: note it under "Results" with the
exact steps, and the next build session fixes it.

## Setup before testing

- Set a PIN, pattern or password. Turn off Smart Lock / Extend Unlock.
- Open FAFO, go through the setup checklist (notifications, camera, full-screen alarm, DND access,
  battery Unrestricted, Samsung sleeping-apps steps).

## v1 done: the nine items (spec section 1)

Tick each once the detailed steps below pass on the A17.

1. [ ] Arms only while plugged in (Home: "Plug in to arm" when unplugged; "Set a screen lock first" without a lock).
2. [ ] Unplug before unlock: pre-alarm countdown (0/1/5/10/30/60 s, default 5 s) beeping faster, then the
       siren until unlock; plugging back in or unlocking cancels the countdown; the siren returns after a reboot (M5, M6).
3. [ ] Unplug sensitivity 0.1 s to 5 s changes how long a cable can be out before it counts (Settings).
4. [ ] A bump chirps; a pick-up chirps louder (M7).
5. [ ] Photo on trigger, then every 10 s, kept on the phone only (M8).
6. [ ] Pairing over Bluetooth, approved only with the real PIN/pattern/password (M9).
7. [ ] Circles with their own members, labels and permissions (M10).
8. [ ] Nearby trusted devices get the alert and can reset or disarm as their circle allows, over Wi-Fi and
       over Bluetooth (M10, M11).
9. [ ] Intercom room mode and push-to-talk, to one device or All, started only on the listening phone (M12).

## Open items from spec section 9 (record what you see)

- [ ] Camera before first unlock (expected: no photos after a reboot until unlock).
- [ ] `USAGE_ALARM` in each Samsung DND mode; `setStreamVolume` under DND with and without DND access.
- [ ] Boot-to-siren time from power on (target under 30 s).
- [ ] Unplug delivery latency: does 0.1 s sensitivity behave differently from 0.5 s? Do "Protect battery"
      (charging stops at 80-85 %) or a wireless pad cause false unplugs?
- [ ] Does One UI 8 "Lock network and security" block power off from the lock screen?
- [ ] After a process kill, does tapping the alarm notification / alarm screen resume photos?
- [ ] Samsung default battery settings: does the armed service survive a night?
- [ ] NSD discovery between two phones on home Wi-Fi: time from opening the app to the reachable dot.
- [ ] `adb shell am compat enable RESTRICT_LOCAL_NETWORK ws.crandell.fafo` (reboot): fails without Nearby
      devices, works with it.
- [ ] Does the A17 have an `audio/opus` encoder (Bluetooth intercom needs it)? Intercom latency and echo.
- [ ] Background Bluetooth scan delivery with Samsung battery settings.
- [ ] Bluetooth L2CAP audio between the A17 and a second phone (choppy / fine).
- [ ] Push-to-talk to a locked receiver with Stay reachable on.

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

## Fix 2026-10-28: full-screen alarm opens by itself (build after M13)

Bill's report on the M13 APK: the red alarm screen opened when he tapped the notification (locked or
unlocked) but never by itself. Cause: the full-screen intent was added to an update of the "armed"
notification, and Android only launches a full-screen intent when a notification is first posted. Now
a separate "ALARM" notification (category "Alarm screen") is posted new when the siren starts.

Install the new debug APK over the old one (no uninstall needed), then:

1. Open FAFO. The setup checklist must not list "Full-screen alarm". If it does, tap it: the first tap
   opens Settings > Apps > FAFO Security > "Full screen notifications" (turn it on); if it is still listed
   after you come back, the next tap opens the "Alarm screen" notification category (turn it on, set it
   to "Sound/Alert", not Silent). Coming back to FAFO re-checks it.
2. Optional check over USB: `adb shell appops get ws.crandell.fafo USE_FULL_SCREEN_INTENT` says `allow`.
3. **Screen off, locked:** arm while charging, press power to turn the screen off, wait 5 s, unplug.
   - [ ] Pre-alarm beeps play with the screen still off (no red screen yet, that is expected).
   - [ ] When the full siren starts, the screen turns on by itself and shows the red alarm screen over
         the lock screen, without touching the phone.
4. **Screen on, locked (lock screen showing):** repeat with the lock screen visible when you unplug.
   - [ ] The red alarm screen replaces the lock screen by itself when the siren starts.
5. Note: if the phone is unlocked and in use when an alarm notification arrives, Android 13+ shows a
   heads-up "ALARM" banner instead of the full screen. That is Android's rule, not a bug (and FAFO's
   alarm only starts while the phone is locked, so it should not normally happen).
6. - [ ] If One UI lets you swipe the "ALARM" notification away on the locked phone while the siren
         runs (Android 14+ usually does not), it comes back and the red screen opens again.
7. - [ ] Unlock with your PIN: siren stops, both FAFO alarm notifications go away, and the red screen
         closes by itself (it no longer stays on top of the home screen).
8. - [ ] Remote Reset from a paired phone during the alarm: the "ALARM" notification and the red screen
         go away; the "FAFO Security is armed" notification stays.
9. - [ ] Reboot during the alarm (M6 test): after boot the red screen opens by itself once the siren
         returns (screen off and locked).

Note what happens on the A17 in step 3 and 4 under Results (date, screen state, whether it opened).

## UI batch 2026-10-28 (arm/disarm confirmation, navigation, icon, design)

Install only after Bill approves the screenshots.

- [ ] Launcher: the FAFO shield on navy in the app drawer and home screen (circle mask), nothing clipped.
- [ ] Themed icons on (Settings > Wallpaper and style > Themed icons, if One UI offers it): a one-color shield silhouette.
- [ ] Launch: dark from the first frame (no white flash), shield on the splash.
- [ ] Grace window, default 30 s: lock, unlock with PIN or fingerprint, plug in, tap ARM within 30 s: arms with **no** prompt.
- [ ] Wait 40 s after unlocking, tap DISARM: the fingerprint / screen lock prompt shows; Cancel: "Still armed. Confirmation cancelled." and it stays armed; confirm: disarms.
- [ ] Settings > Security > Unlock grace window > Off: asks for the confirmation first (Cancel keeps 30 s); with Off every Arm and Disarm prompts, even right after unlocking.
- [ ] Unlock with Smart Lock / face only (if enabled): Arm still prompts (those unlocks don't count).
- [ ] Notification Disarm while armed (phone unlocked): opens FAFO; inside the window it disarms without a prompt, outside it prompts.
- [ ] Remote Reset / Disarm from a second phone still work as in M10 (no change).
- [ ] Regression (fix 2026-10-28): armed, locked, unplug: the new countdown screen (amber ring) then the red ALARM screen with the big shield opens **by itself** over the lock screen; the pulse is a slow fade, not a flash; unlocking stops the siren and the red screen closes.
- [ ] Bottom bar: Home, Devices, Intercom, Setup (badge with the number of missing steps), Help; the gear opens Settings from every tab; back goes to Home, then leaves the app.
- [ ] Setup: "Learn more" opens the matching Help topic; Samsung steps expand under Battery; done steps stay listed.
- [ ] Help > Samsung Galaxy settings: the three buttons open the full-screen, battery and DND pages.

## Round 3 2026-10-28 (shared key, arming, status screen, theme, flash, email, open Wi-Fi)

- [ ] **Uninstall the current FAFO once** (it was signed with a different key). Install the round-3 APK (oss
  or play). Later CI or local debug builds now install over it without uninstalling.
- [ ] ARMING: plug in, tap ARM (confirm): yellow ARMING with a countdown on Home and in the notification
  ("Arming in 5 s", Cancel); after it, ARMED. Cancel arming (Home or notification) needs no fingerprint.
  Unplug during ARMING: arming is cancelled, no siren.
- [ ] Disarm right after arming (no lock in between): **always asks** for fingerprint / screen lock (Bill's bug).
- [ ] Arm, lock the phone (screen off), unlock with PIN/fingerprint, tap Disarm within 30 s: no prompt.
  Wait past 30 s after that unlock: Disarm asks.
- [ ] With Samsung Extend Unlock / Smart Lock on (if you use it): lock, let it unlock without PIN, Disarm: asks.
- [ ] Reboot while ARMING: comes back DISARMED.
- [ ] Status screen (Home > Full-screen status): green shield, red eye; bump the phone once: yellow + chirp;
  bump again within 8 s: orange + louder chirp + slow flash blink; leave it for the Bump reset time (15 s by default; Settings > Motion, 10/15/30/60 s): back to green.
  With the phone locked, keep handling it or pick it up: orange countdown (at least 5 s) then the siren;
  unlock stops it. Turn off "Motion can sound the alarm": handling only chirps.
- [ ] At RED the whole screen flashes red, the torch strobes; both stop on unlock. Torch goes dark briefly
  when a photo is taken. Settings > Alarm > Flash as alarm light off: no torch.
- [ ] If your own flashlight is on when FAFO disarms, it stays on (FAFO only switches off its own flash).
- [ ] Theme: phone in light mode -> FAFO light; Settings > Display > Theme Dark / Light forces it (the
  launch screen follows from the next start). The lock-screen status screen uses the same theme.
- [ ] Largest font size and largest display size: DISARMED, ARMED, PLUG ME BACK IN, buttons and titles stay
  on one line (they shrink instead of wrapping).
- [ ] Photo email (SMTP): Settings > Photo email, your provider's server, SSL/TLS 465, username, an app
  password; Send test email arrives with the shield picture. Wrong password shows the 535 message.
- [ ] Trigger an alarm with email on: the first photo arrives within about a minute, then batches. Turn on
  airplane mode during the alarm, then off: queued photos go out when the network is back.
- [ ] Play build: Send with Google account > Sign in with Google (account picker + consent), test email
  arrives. (Needs the OAuth client set up first; before that it shows a sign-in error.)
- [ ] Open Wi-Fi (Android 13+ / A17): Setup > Allow open Wi-Fi fallback: Android's "Allow FAFO to suggest
  Wi-Fi networks?" appears; Allow. Then with mobile data off, no known Wi-Fi and an open network nearby,
  queued mail goes out over it. **Check whether scan results arrive without location on Android 16**; if
  not, tell me and the feature stays off. After disarm, Settings > Wi-Fi shows no FAFO suggestions left.
- [ ] Wordmark: dark-white on dark, navy on light, all white on the red screen; Help and Setup headers show
  the shield + wordmark lockup.

## Device feedback 2026-10-28: settle, HANDS OFF, PHOTO TAKEN (A17, Android 16)

Settings for these: Pre-alarm 10 s (your setting), Motion Medium, Motion can sound the alarm on, Bump reset
time 15 s, Photo on trigger on. Setup checklist: full-screen alarm allowed.

**1. Picked up and set down cancels (the bug).**
- [ ] Arm, plugged in, let the screen go off and lock. Pick the phone up (cable still in), hold it a second,
  set it back down flat and let go. Expected: HANDS OFF wakes the screen, the red countdown starts (10 s),
  and about 2-3 s after you let go it goes back to ARMED with the orange HANDS OFF (no siren). The orange
  clears after 15 s and the screen goes dark. Home > Incidents shows "Cancelled (put down and left still)".
- [ ] Note how many seconds after setting it down it cancelled: ____ s.
- [ ] Same, but set it down on its side or at an angle: still cancels.
- [ ] Pick it up and keep it moving (walk around the room, cable stretched or unplugged): the countdown runs
  out and the siren sounds. Unlock stops it.
- [ ] Pick it up, unplug the cable, set it down and leave it: the siren still sounds (unplugged never settles).
- [ ] Pick it up, unplug the cable, plug it straight back in, set it down and leave it: the siren still sounds
  (an unplug during the countdown is remembered; unlock to stop it).
- [ ] After it settled, pick it up again within 15 s: a new countdown starts straight away.
- [ ] Unplug pre-alarm unchanged: unplug while locked, PLUG ME BACK IN countdown; plugging back in cancels it.
- [ ] With Pre-alarm set to 5 s: pick up and set down quickly. Does it cancel in time? (It needs about 2.5 s
  after you let go, so a slow pickup may run out; tell me if 5 s feels too tight.)

**2. HANDS OFF screen.**
- [ ] Armed, screen off: tap or nudge the phone once. Expected: the screen turns on over the lock screen with
  a big yellow HANDS OFF, shield and wordmark, and a chirp. Leave it: after the bump reset time (15 s) it
  closes and the lock screen times out to dark.
- [ ] Armed, screen off: pick it up. Expected: orange HANDS OFF, then (locked) the red countdown on the same
  screen with the number counting down from your pre-alarm setting (10).
- [ ] Tap Disarm on the HANDS OFF screen: Android asks for your PIN / fingerprint first. Cancel that: nothing
  is disarmed. (A stranger cannot stop it from here.)
- [ ] Unlock the phone normally while HANDS OFF shows: the screen closes (and a running countdown stops).
- [ ] Phone unlocked and in use, armed: bump it: no HANDS OFF (status only, you are the owner).
- [ ] Settings > Apps > FAFO > Notifications: a channel "Hands off warning" exists. Turn off full-screen
  (Settings > Apps > Special access > Full screen notifications > FAFO off), screen off, bump: you get a
  HANDS OFF pop-up notification instead and the screen stays off. Turn it back on afterwards.
- [ ] Light theme (Settings > Display > Light): HANDS OFF follows the theme.

**3. PHOTO TAKEN.**
- [ ] Trigger a pre-alarm (unplug while locked, or pick it up). On the first photo: a white flash, "PHOTO TAKEN"
  with a small picture of your face for about 2 s, a camera shutter click, and "1 photo taken" under the
  countdown. Every 10 s after: the count goes up.
- [ ] Once the siren sounds: photos keep flashing and counting, but no shutter click; the siren never stutters
  or drops out when a photo is taken.
- [ ] Settings > Accessibility > Visibility enhancements > Remove animations on: no white flash, the
  PHOTO TAKEN label and picture still show. Turn it back off afterwards.

## Round 5 2026-10-29: pin app while armed, lock-screen Flashlight / Talk, Quick Settings tiles (A17)

Install the APK from the green CI run of branch `feat/round5-20261029` (artifact `fafo-debug-apk-play` or
`-oss`). Talk needs a second phone with the same APK, paired, in a circle with Intercom.

**1. Lock-screen Flashlight and Talk.**
- [ ] Arm (plugged in), lock, wake the screen. The FAFO notification on the lock screen shows Disarm,
  Flashlight and Talk. (If it shows only the icon: Settings > Notifications > Lock screen notifications >
  Show content, or allow content for FAFO.)
- [ ] Tap Flashlight: the flash turns on without unlocking. Tap again: off.
- [ ] Tap Talk: the FAFO Talk screen opens over the lock screen, no PIN asked. It shows your circle, the
  other phone with a filled dot (reachable), All, and the big HOLD TO TALK button. There is no Disarm,
  room mode or Listen anywhere on it.
- [ ] Hold the button and speak: the other phone (FAFO open or Stay reachable on) plays it and shows
  "<this phone> is talking". Release: it stops. The mic indicator shows only while you hold.
- [ ] First time only, if the mic was never allowed: Allow microphone asks for your PIN first, then
  Android's mic prompt.
- [ ] Tap Open FAFO: Android asks for your PIN; cancel it and you stay on the lock screen.
- [ ] Press the power button while Talk is open, then wake: Talk is gone, plain lock screen.
- [ ] Disarm on the notification still asks for the PIN / fingerprint (unchanged).
- [ ] Unplug while locked (pre-alarm), and during the siren: the notification shows Disarm only.
- [ ] Restart the phone while armed, before unlocking open the notification: tap Talk. Expected
  "Unlock once after a restart to use Talk." Flashlight works.

**2. Quick Settings tiles.**
- [ ] Swipe down twice, Edit (pencil, or the menu at the top right), drag FAFO Light and FAFO Talk in, Done.
- [ ] FAFO Light toggles the flash and shows On / Off; turning the flash off with Samsung's own Flashlight
  tile updates FAFO Light to Off.
- [ ] FAFO Talk opens the Talk screen; on the lock screen it opens over it without asking for the PIN.
- [ ] Both work while FAFO is disarmed too.

**3. Pin app while armed.**
- [ ] First turn on Samsung's pinning: Settings > Security and privacy > More security settings > Pin
  windows (or Pin app): on, and Ask for PIN before unpinning: on. Note the exact menu names: ______
- [ ] FAFO Settings > Security > Pin app while armed: on. Tap ARM, confirm. The status screen opens and
  Android asks "Pin app?" (or similar). Accept. Home and Recents do nothing; "Pinned: other apps are blocked."
  and an Unpin button show under Disarm.
- [ ] Tap Unpin: the phone locks (with Ask for PIN on). Unlock: FAFO is still ARMED, not pinned.
- [ ] Arm pinned again, then Disarm on the status screen: the pin ends, the phone locks, then Android asks
  for the PIN and FAFO disarms. The status screen closes.
- [ ] Arm pinned, lock, unplug: the countdown and siren show on the pinned screen as usual; unlock / Disarm
  stops it.
- [ ] Arm pinned, use Android's unpin (hold Back and Recents, or swipe up and hold): the phone locks; FAFO
  stays armed.
- [ ] Decline Android's "Pin app?": FAFO stays armed, just not pinned.
- [ ] With Samsung's Pin windows switched off: does "Pin app?" still appear? ______
- [ ] Setting off (default): Arm works exactly as before, no status screen pops up.

## Results

(Bill: write findings here, newest first.)
