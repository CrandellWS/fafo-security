# FAFO Security: release checklist (Google Play)

Prepared 2026-10-28. Bill does every Play Console and Google Cloud step; this folder only holds the
packs. Sources were checked on 2026-10-28; recheck any form whose wording looks different.

## Decisions before release
1. **Open-Wi-Fi fallback vs. location.** Scanning (`getScanResults()`/`startScan()`) needs
   ACCESS_FINE_LOCATION on every Android version, also 13+ with NEARBY_WIFI_DEVICES
   (https://developer.android.com/develop/connectivity/wifi/wifi-permissions); during an alarm that is
   background location, its own Play declaration, and a break with the spec's "no location permission".
   (a) leave the fallback out of the Play build, (b) suggest networks without scanning, or (c) accept the
   permission (PERMISSIONS-DECLARATIONS.md, last section). `addNetworkSuggestions` itself needs only
   CHANGE_WIFI_STATE per general Android docs (not confirmed on that page).
2. **Plain-text SMTP.** Data safety "encrypted in transit" = Yes only if the app refuses unencrypted SMTP.
   If it can send without TLS, the answer is No and the privacy policy sentences change.

## Release key, version and AAB (Bill, on the workstation)
- [ ] **Create the upload key once** (Play App Signing keeps the real app signing key; this is the upload key).
      Keep it out of the repo, back it up (password manager plus an offline copy); losing it means a key
      reset request to Play, not a lost app.
      ```bash
      mkdir -p ~/.config/fafo-keys && chmod 700 ~/.config/fafo-keys
      keytool -genkeypair -v \
        -keystore ~/.config/fafo-keys/fafo-upload.jks -alias fafo-upload \
        -keyalg RSA -keysize 4096 -validity 10000 \
        -dname "CN=Bill Crandell, O=FAFO Security, C=US"
      chmod 600 ~/.config/fafo-keys/fafo-upload.jks
      keytool -list -v -keystore ~/.config/fafo-keys/fafo-upload.jks -alias fafo-upload | grep SHA1   # for the OAuth upload client
      ```
      The repo's `RepoSecretsTest` and `.gitignore` must keep any .jks out; never add a release
      signingConfig with paths or passwords to build.gradle.kts.
- [ ] **Version:** `app/build.gradle.kts` > defaultConfig `versionCode` (integer, +1 for every upload to
      any track, never reused) and `versionName` (shown to users; first release `0.1.0`, the play flavor
      appends `-play`, so Play shows `0.1.0-play`: drop the suffix for play if that is unwanted). Commit the bump.
- [ ] **Build the signed AAB** (play flavor = Gmail sign-in; oss is for F-Droid). Needs JDK 17 `bin/` (keytool, jarsigner) on PATH. Passwords are read, not typed on the command line:
      ```bash
      cd fafo-security   # repo root
      read -rsp "Upload key password: " FAFO_PW; echo
      ./gradlew clean test checkNoRemoteDeps :app:bundlePlayRelease \
        -Pandroid.injected.signing.store.file="$HOME/.config/fafo-keys/fafo-upload.jks" \
        -Pandroid.injected.signing.store.password="$FAFO_PW" \
        -Pandroid.injected.signing.key.alias=fafo-upload \
        -Pandroid.injected.signing.key.password="$FAFO_PW"
      unset FAFO_PW
      ls -l app/build/outputs/bundle/playRelease/app-play-release.aab
      jarsigner -verify app/build/outputs/bundle/playRelease/app-play-release.aab
      ```
      (If the Gradle version rejects the injected properties, build unsigned and sign with
      `jarsigner -keystore ~/.config/fafo-keys/fafo-upload.jks <aab> fafo-upload`.)
- [ ] Optional on-device check of the exact AAB before upload: bundletool `build-apks --local-testing`
      with the upload key, then `install-apks` to the A17.

## Play Console steps in order
1. [ ] Create app: name `FAFO Security: Charger Alarm`, default language English (US), App, Free,
       accept declarations. Complete developer verification if prompted.
2. [ ] Setup > App signing: use Google-generated app signing key (default).
3. [ ] **Internal testing** > Create release > upload the AAB (this creates the app signing key) >
       release notes from listing/en-US/release-notes.txt > Testers: an email list with Bill's accounts >
       Save, Review, Roll out.
4. [ ] Copy the App signing SHA-1 and upload SHA-1 into Google Cloud clients (GOOGLE-CLOUD-OAUTH-SETUP.md step 6).
5. [ ] Install from the internal test opt-in link on the A17; run docs/DEVICE-TESTS.md alarm checks and
       Gmail sign-in on the Play-signed build.
6. [ ] Fill App content (sections 3-4 below), Store listing (section 1), and host the site (section 2).
7. [ ] Production > Countries/regions > Create release > promote the tested build (or upload the same
       versionCode from internal testing via "Promote release") > Review release > Send for review.
       Personal accounts created before 2023-11-13 do not need the 12-tester closed test.
8. [ ] After approval: check the listing live, the pre-launch report, and Android vitals.

## 0. Before anything else
- [ ] Decide which build goes to Play first: **core** (round 2 features) or **round 3** (status light,
      light theme, photo email, flash, open-Wi-Fi fallback). It decides the description file, the Data safety
      column and whether OAuth is needed now. Never list a feature the submitted AAB doesn't have.
- [ ] Round 3 blocker: the open-Wi-Fi fallback needs ACCESS_FINE_LOCATION to scan (also on Android 13+),
      which is background location while the alarm runs. Drop it from the Play build or accept the location
      permission and its declaration (PERMISSIONS-DECLARATIONS.md, "Round 3 watch-outs").
- [ ] Round 3: confirm SMTP refuses plaintext (TLS or STARTTLS only), or the Data safety "encrypted in
      transit" answer must be No.
- [ ] Developer verification: Android developer verification is rolling out for Play apps; check Play
      Console for a verification prompt when creating the app and complete it if shown (dates vary by
      region; not verified here).
      Same package `ws.crandell.fafo` is used by the oss/F-Droid APK; F-Droid signs with its own key.
      https://support.google.com/googleplay/android-developer/answer/12253906
- [ ] Target API: new apps must target API 36 from 2026-08-31 (app targets 36).
      https://developer.android.com/google/play/requirements/target-sdk
- [ ] Signed release **AAB** with a private upload key (never committed), Play App Signing on.
- [ ] Personal account older than 2023-11-13: the 12-testers / 14-day closed test rule does not apply;
      an internal test track is still the quickest way to see the Play-signed build on the A17.

## 1. Store listing (Grow users > Store presence > Main store listing)
Specs: https://support.google.com/googleplay/android-developer/answer/9866151 ·
limits: https://support.google.com/googleplay/android-developer/answer/9859152 ·
metadata policy: https://support.google.com/googleplay/android-developer/answer/9898842

| Item | Play spec | File |
|---|---|---|
| App name | max 30 chars; no emoji, no ranking/price/"new" words | listing/en-US/title.txt (28) |
| Short description | max 80 | listing/en-US/short-description.txt (76) |
| Full description | max 4000 | listing/en-US/full-description.txt (round 3, 3673) or full-description-core.txt (core, 3016) |
| App icon | 512x512, 32-bit PNG with alpha, max 1024 KB; Play applies the mask | graphics/icon-512.png (99 KB) |
| Feature graphic | 1024x500, JPEG or 24-bit PNG, no alpha | graphics/feature-graphic-1024x500.png |
| Phone screenshots | min 2 (across device types), max 8 per type; 320-3840 px per side; long side at most 2x short side; JPEG or 24-bit PNG. 4+ at 1080 px or more for recommendation eligibility | graphics/phone/ (8 at 1080x1920) |
| 7-inch / 10-inch tablet screenshots | optional; 1080-7680 px, 9:16 portrait or 16:9 landscape | none: the app is phone-first and untested on tablets; skip until it is |
| Promo video | optional YouTube URL | none |
| Release notes | max 500 | listing/en-US/release-notes.txt |

- [ ] Run `python3 store/tools/check_listing.py` after any text edit (limits + banned words).
- [ ] Regenerate screenshots when round 3 shots exist: `python3 store/graphics/make_store_shots.py`
      (auto-picks fafo-r3/shots if present, else round 2; or pass a folder). Edit captions/order in
      graphics/shots.json. The raw 1078x2338 captures are 2.17:1 and would be rejected, hence the framing.
- [ ] Regenerate icon/feature graphic: `python3 store/tools/make_graphics.py`.
- [ ] Listing fields: LISTING.md. Category: **Tools**. Tags: pick only what applies (e.g. Security, Alarm). Contact email required;
      website = the homepage below.
- [ ] Screenshots contain the sample device name "Bill's Pixel" (Google's Pixel trademark plus a real first
      name). Rename it in the screenshot test data to a generic label (e.g. "Bill's phone" or "Living room")
      before the round 3 shots are taken, then regenerate. Shot 06 also shows "Room mode off" next to
      "Bill's Pixel listening", which reads as a contradiction; use a fixture where the two agree.

## 2. Privacy policy and homepage hosting
Play requirements: public, non-geofenced URL, not a PDF, names app/developer, has a contact, matches
Data safety, also linked inside the app. https://support.google.com/googleplay/android-developer/answer/10144311
Files: docs/index.html (homepage), docs/privacy-policy.html, docs/icon.png (text: PRIVACY-POLICY.md); placeholders
filled 2026-10-28. Policy URL: https://crandellws.github.io/fafo-security/privacy-policy.html (later https://fafo.crandell.ws/privacy-policy.html). When the app is on Play, add its link in docs/index.html
in place of "coming soon".

Hosting options (all free):
- **GitHub Pages (chosen 2026-10-28)**: the app repo is being open-sourced (GPL-3.0-or-later), so Pages
  serves `main` /docs directly. https://docs.github.com/en/pages/getting-started-with-github-pages/about-github-pages
- **Cloudflare Pages** or **Netlify** free tiers: deploy from a private repo or by upload.
- **Custom domain (recommended for OAuth):** `fafo.crandell.ws` (CNAME at GoDaddy to the host). Google
  OAuth needs the homepage and privacy policy on a domain verified in Search Console; crandell.ws
  verifies with one DNS TXT record. A *.github.io address works for Play but is awkward for OAuth domain
  verification.
- [ ] Add a "Privacy policy" link inside the app (Help or Settings > About) that opens the URL.

## 3. App content (Policy > App content)
All answers: CONTENT-RATING.md, REVIEWER-ACCESS.md, DATA-SAFETY.md, PERMISSIONS-DECLARATIONS.md.
- [ ] Privacy policy URL
- [ ] Ads: No
- [ ] App access: instructions (no login; setup steps for the reviewer)
- [ ] Content rating (IARC): Utility/Productivity/Communication/Other; "Users Interact" yes (intercom).
      https://support.google.com/googleplay/android-developer/answer/9898843
- [ ] Target audience: 18+; not appealing to children. https://support.google.com/googleplay/android-developer/answer/9867159
- [ ] Data safety: column A (core) or B (photo email). https://support.google.com/googleplay/android-developer/answer/10787469
- [ ] Foreground service types: specialUse, camera, microphone, connectedDevice, each with description,
      impact, use case and video. https://support.google.com/googleplay/android-developer/answer/13392821
- [ ] Full-screen intent declaration: security alarm, not an alarm clock; do not claim the alarm-clock use case (PERMISSIONS-DECLARATIONS.md). Same page.
- [ ] Battery optimization exemption: justification ready ("Safety app" case); no Console form found.
      https://developer.android.com/training/monitoring-device-state/doze-standby#exemption-cases
- [ ] News / Government / Financial / Health / COVID: No / none.

## 4. Videos to record on the A17 (unlisted YouTube)
Shot lists are in PERMISSIONS-DECLARATIONS.md (four FGS types) and GOOGLE-CLOUD-OAUTH-SETUP.md (Gmail
verification). Show the persistent notification and mic indicator in each.

## 5. Google Cloud OAuth for Gmail send (only if the Play build has Gmail sign-in)
Full steps: GOOGLE-CLOUD-OAUTH-SETUP.md. Summary:
- [ ] Project + Gmail API; Branding (name, support email, logo graphics/oauth-logo-120.png, homepage,
      privacy policy, authorized domain verified in Search Console); Audience External; scope gmail.send (Sensitive).
- [ ] Android clients, package ws.crandell.fafo, one per SHA-1: Play app-signing key, upload key, and the
      public debug key (debug client only in a separate Testing-status Dev project).
- [ ] Until verified: "unverified app" screen and a 100-user lifetime cap. https://support.google.com/cloud/answer/7454865
- [ ] Verification: homepage + policy on the verified domain, Limited Use sentence, scope justification,
      unlisted English demo video. Typically 3-5 business days.
      https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification

## 6. Release
- [ ] Internal testing track first, install from Play on the A17, run docs/DEVICE-TESTS.md alarm checks
      on the Play-signed build (Play signing changes the SHA-1, so test Gmail sign-in here).
- [ ] Production, countries, Free price (an app set free cannot later become paid).
- [ ] Review the pre-launch report.
