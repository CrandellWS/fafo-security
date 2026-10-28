# FAFO Security: Google Play release checklist

Prepared 2026-10-28. Bill does every Play Console and Google Cloud step; this folder only holds the
packs. Sources were checked on 2026-10-28; recheck any form whose wording looks different.

## 0. Before anything else
- [ ] Decide which build goes to Play first: **core** (round 2 features) or **round 3** (status light,
      light theme, photo email, flash, open-Wi-Fi fallback). It decides the description file, the Data safety
      column and whether OAuth is needed now. Never list a feature the submitted AAB doesn't have.
- [ ] Round 3 blocker: the open-Wi-Fi fallback needs ACCESS_FINE_LOCATION to scan (also on Android 13+),
      which is background location while the alarm runs. Drop it from the Play build or accept the location
      permission and its declaration (policy/permission-declarations.md, "Round 3 watch-outs").
- [ ] Round 3: confirm SMTP refuses plaintext (TLS or STARTTLS only), or the Data safety "encrypted in
      transit" answer must be No.
- [ ] Developer verification: Play Console requires Play apps to be registered for Android developer
      verification (Play policy deadline 2026-09-30). Check Play Console for the prompt when creating the app.
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
| Full description | max 4000 | listing/en-US/full-description.txt (round 3, 3591) or full-description-core.txt (core, 2934) |
| App icon | 512x512, 32-bit PNG with alpha, max 1024 KB; Play applies the mask | graphics/icon-512.png (99 KB) |
| Feature graphic | 1024x500, JPEG or 24-bit PNG, no alpha | graphics/feature-graphic-1024x500.png |
| Phone screenshots | min 2 (across device types), max 8 per type; 320-3840 px per side; long side at most 2x short side; JPEG or 24-bit PNG. 4+ at 1080 px or more for recommendation eligibility | screenshots/phone/ (8 at 1080x1920) |
| 7-inch / 10-inch tablet screenshots | optional; 1080-7680 px, 9:16 portrait or 16:9 landscape | none: the app is phone-first and untested on tablets; skip until it is |
| Promo video | optional YouTube URL | none |
| Release notes | max 500 | listing/en-US/release-notes.txt |

- [ ] Run `python3 store/tools/check_listing.py` after any text edit (limits + banned words).
- [ ] Regenerate screenshots when round 3 shots exist: `python3 store/tools/make_screenshots.py`
      (auto-picks fafo-r3/shots if present, else round 2; or pass a folder). Edit captions/order in
      screenshots/shots.json. The raw 1078x2338 captures are 2.17:1 and would be rejected, hence the framing.
- [ ] Regenerate icon/feature graphic: `python3 store/tools/make_graphics.py`.
- [ ] Category: **Tools**. Tags: pick only what applies (e.g. Security, Alarm). Contact email required;
      website = the homepage below.
- [ ] Screenshots contain sample names ("Bill's Pixel", "Kitchen phone"); fine, or rename in the test data.

## 2. Privacy policy and homepage hosting
Play requirements: public, non-geofenced URL, not a PDF, names app/developer, has a contact, matches
Data safety, also linked inside the app. https://support.google.com/googleplay/android-developer/answer/10144311
Files: site/index.html (homepage), site/privacy.html, site/icon.png. Fill {{DEVELOPER_NAME}},
{{CONTACT_EMAIL}}, {{EFFECTIVE_DATE}}, {{PLAY_LINK}} first.

Hosting options (all free):
- **GitHub Pages**: Free plan serves Pages only from public repos (private repos need Pro/Team). The app
  repo CrandellWS/fafo-security is private, so either open-source it or make a small **public repo with
  only site/** (e.g. CrandellWS/fafo-site). https://docs.github.com/en/pages/getting-started-with-github-pages/about-github-pages
- **Cloudflare Pages** or **Netlify** free tiers: deploy from a private repo or by upload.
- **Custom domain (recommended for OAuth):** `fafo.crandell.ws` (CNAME at GoDaddy to the host). Google
  OAuth needs the homepage and privacy policy on a domain verified in Search Console; crandell.ws
  verifies with one DNS TXT record. A *.github.io address works for Play but is awkward for OAuth domain
  verification.
- [ ] Add a "Privacy policy" link inside the app (Help or Settings > About) that opens the URL.

## 3. App content (Policy > App content)
All answers: policy/app-content.md, policy/data-safety.md, policy/permission-declarations.md.
- [ ] Privacy policy URL
- [ ] Ads: No
- [ ] App access: instructions (no login; setup steps for the reviewer)
- [ ] Content rating (IARC): Utility/Productivity/Communication/Other; "Users Interact" yes (intercom).
      https://support.google.com/googleplay/android-developer/answer/9898843
- [ ] Target audience: 18+; not appealing to children. https://support.google.com/googleplay/android-developer/answer/9867159
- [ ] Data safety: column A (core) or B (photo email). https://support.google.com/googleplay/android-developer/answer/10787469
- [ ] Foreground service types: specialUse, camera, microphone, connectedDevice, each with description,
      impact, use case and video. https://support.google.com/googleplay/android-developer/answer/13392821
- [ ] Full-screen intent declaration (alarm). Same page.
- [ ] Battery optimization exemption: justification ready ("Safety app" case); no Console form found.
      https://developer.android.com/training/monitoring-device-state/doze-standby#exemption-cases
- [ ] News / Government / Financial / Health / COVID: No / none.

## 4. Videos to record on the A17 (unlisted YouTube)
Shot lists are in policy/permission-declarations.md (four FGS types) and policy/oauth-gmail.md (Gmail
verification). Show the persistent notification and mic indicator in each.

## 5. Google Cloud OAuth for Gmail send (only if the Play build has Gmail sign-in)
Full steps: policy/oauth-gmail.md. Summary:
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
