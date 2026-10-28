# Store listing (Play Console > Grow users > Store presence > Main store listing)

Source of truth for upload is `listing/en-US/*.txt` (plain text, exact characters). This page collects it
with the other listing fields. After any text edit run `python3 store/tools/check_listing.py`
(limits, emoji, banned words). Limits: https://support.google.com/googleplay/android-developer/answer/9859152
Metadata policy: https://support.google.com/googleplay/android-developer/answer/9898842

Upload the description that matches the AAB you submit. Never list a feature the build doesn't have.

| Field | Value | Limit |
|---|---|---|
| App name | `FAFO Security: Charger Alarm` (listing/en-US/title.txt) | 28 / 30 |
| Short description | `Unplug alarm, trigger photos and trusted devices for your own phone. No ads.` | 76 / 80 |
| Full description, core build (round 2) | listing/en-US/full-description-core.txt | 3016 / 4000 |
| Full description, round 3 build (photo email, flash, status light, Gmail note) | listing/en-US/full-description.txt | 3673 / 4000 |
| Release notes (first release) | listing/en-US/release-notes.txt | 125 / 500 |
| App category | **Tools** (Application > Tools) | |
| Tags | Up to 5 from the list the Console offers. Choose only ones that describe the app, e.g. the security or alarm tags if offered. Do not pick tags for features it lacks (no tracking, no locator). | 5 |
| Contact email (required, public) | `{{CONTACT_EMAIL}}` (Bill fills in) | |
| Website | homepage URL once hosted (site/index.html, see PRIVACY-POLICY.md "Hosting") | |
| Phone | leave empty | |
| Privacy policy | site/privacy-policy.html URL (App content page) | |
| App icon | graphics/icon-512.png | 512x512, 32-bit PNG, max 1 MB |
| Feature graphic | graphics/feature-graphic-1024x500.png | 1024x500, 24-bit PNG, no alpha |
| Phone screenshots | graphics/phone/01..08-*.png, upload in file-name order | 2-8, 1080x1920 |
| Tablet screenshots | none (optional; app untested on tablets) | |
| Video | none | |

## Short description
```
Unplug alarm, trigger photos and trusted devices for your own phone. No ads.
```

## Full description (core build)
See `listing/en-US/full-description-core.txt`. Structure: one-paragraph summary, then sections HOW IT
WORKS, TRIGGER PHOTOS, TRUSTED DEVICES AND CIRCLES, INTERCOM AND PUSH-TO-TALK, PRIVACY, GOOD TO KNOW
(limits stated plainly: a phone that is switched off and stays off is silent; photos wait for the first
unlock after a restart; alerts reach only nearby paired devices; it is an alarm, not a guarantee).

## Full description (round 3 build)
`listing/en-US/full-description.txt` adds: status light, flash strobe, photo email (SMTP or Gmail) with
send-later, the open-Wi-Fi suggestion, and the temporary note that Google shows an "unverified app" screen
at Gmail sign-in until verification finishes. Remove that note after Google approves the OAuth app
(GOOGLE-CLOUD-OAUTH-SETUP.md section 7). Remove the open-Wi-Fi sentence if the Play build drops that
fallback (RELEASE-CHECKLIST.md, decision 1).

## Wording rules followed
- No emoji, no ranking or price words ("best", "#1", "top", "free" as a price claim), no "new".
- No competitor names, no claims about stopping theft, recovering phones or police use.
- The name "FAFO" is spelled out once as "Free Advanced Features Offline".

## Screenshot story (graphics/shots.json)
1 Arm it while it charges · 2 Unplugged? A countdown starts · 3 Then a loud siren until you unlock ·
4 Pair your own phones with a code check · 5 Circles decide who can reset or disarm ·
6 Push-to-talk on your own Wi-Fi · 7 Trigger photos stay on your phone ·
8 Setup walks you through each phone setting.
With round 3 shots: "Email trigger photos to your list" and "Light or dark, your choice" are added and
the circles and photos slides drop out (8 max). Regenerate: `python3 store/graphics/make_store_shots.py`.
