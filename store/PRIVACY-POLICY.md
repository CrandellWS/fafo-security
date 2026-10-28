# FAFO Security Privacy Policy (source text) and hosting

The published page is `site/privacy-policy.html` (same text, styled, light and dark). Edit both together.
Before hosting, fill `{{DEVELOPER_NAME}}`, `{{CONTACT_EMAIL}}`, `{{EFFECTIVE_DATE}}` in both files and
`{{PLAY_LINK}}` in site/index.html.

Play requirements met: public, non-geofenced URL, not a PDF, names the app and developer, has a contact,
matches the Data safety answers, describes access/collection/sharing. Also link it inside the app
(Help or Settings > About): **still to add in the app**.
https://support.google.com/googleplay/android-developer/answer/10144311

Two sentences depend on the round 3 build and must be checked before publishing:
- "Sent over an encrypted connection" / "email is sent only over encrypted connections": true only if
  SMTP refuses plaintext (RELEASE-CHECKLIST.md decision 2). If not, change both sentences.
- The open-Wi-Fi sentence under Permissions: delete it if the Play build drops that fallback; add the
  location permission if it keeps a scanning fallback (decision 1).
- For the core build (no photo email, no Gmail) the email row, the Google section and the email
  sentences can stay (they describe opt-in features as "if you turn on"), but removing them is cleaner.

---

**App:** FAFO Security (Android, package ws.crandell.fafo). **Developer:** {{DEVELOPER_NAME}}.
**Effective:** {{EFFECTIVE_DATE}}.

FAFO Security is an anti-theft alarm for your own Android phone. It has no ads, no analytics, no account
and no server run by the developer. The developer does not receive any of your data. This policy explains
what the app accesses on your phone, what can leave your phone, and when.

## What stays on your phone
- **Settings** (alarm, countdown, sensitivity, theme and similar) are stored on the phone.
- **Trigger photos.** When the alarm starts, the app uses the camera to take photos, then more at the
  interval you choose while the alarm continues. They are saved in app-private storage with a small record
  of time, camera used, battery level and charging state. No location is recorded. Photos are pruned under
  the storage limit you set, and you can view, share or delete them in the app at any time.
- **Paired devices and circles.** Names you give your devices, their public keys and circle permissions
  are stored on the phone. Private keys are kept in Android Keystore.
- **Email settings** (if you turn on photo email): the recipient list, the mail server, port and login you
  enter. A mail password is stored encrypted with a key held in Android Keystore.

App data is excluded from Android cloud backup. Uninstalling the app deletes all of it from the phone.

## What can leave your phone, and when
| Data | Where it goes | When |
|---|---|---|
| Trigger photos and the sending address | The email server you set up, or Gmail if you sign in with Google, and from there to the addresses on your list | Only if you turn on photo email and the alarm goes off. Sent over an encrypted connection (TLS/HTTPS). If there is no connection the email waits on the phone and is sent later. |
| Alerts and reset or disarm commands | Your own paired devices on the same Wi-Fi network or over Bluetooth | When this phone's alarm goes off, or when you send a command. End-to-end encrypted with the paired keys. Never sent over the internet. |
| Intercom and push-to-talk audio | Your own paired devices on the same Wi-Fi network or over Bluetooth | Only while someone holds push-to-talk in the open app, or while room mode is turned on by the person at that phone. A notification and the system microphone indicator show the whole time. End-to-end encrypted. Nothing is recorded or stored. |
| Anything you share yourself | Where you choose | Only when you use the share button. |

The developer never receives any of this. Email you send is handled by your email provider under its own
terms and privacy policy, and copies stay in the mailboxes involved until their owners delete them.

## Google sign-in and Gmail (Google Play version)
If you choose to send photo email through Gmail, the app asks Google for permission to send email on your
behalf (scope `gmail.send`). It uses that permission only to send the trigger-photo emails you set up, to
the addresses you chose. It cannot read, delete or change your email. The app keeps no copy of your Google
data off the phone and shares it with no one. You can remove the permission at any time in your Google
Account under Security, Third-party apps and services.

FAFO Security's use and transfer to any other app of information received from Google APIs will adhere to
the [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy),
including the Limited Use requirements.

## Permissions
- **Camera**: trigger photos during an alarm. **Camera flash** as an alarm light, if turned on.
- **Microphone**: intercom and push-to-talk only, asked when you first use them.
- **Nearby devices (Bluetooth, Wi-Fi)**: pairing and talking to your own devices. Not used to find your location.
- **Notifications and full-screen alerts**: the armed notification and the alarm screen over the lock screen.
- **Network access**: your local network for paired devices, and the internet only for photo email if you
  turn it on. If you approve it, the app may suggest an open Wi-Fi network to send queued photo email when
  no other connection exists.
- **Battery optimization exemption, Do Not Disturb access, run at startup**: keep the alarm running and
  audible, and resume it after a restart.

## Children
The app is not directed to children under 13 and collects no data from anyone for the developer.

## Security
Keys are kept in Android Keystore, device-to-device traffic is end-to-end encrypted and authenticated, and
email is sent only over encrypted connections. No method is perfect; keep a secure screen lock on your phone.

## Changes
If this policy changes, the new version is posted at this address with a new effective date. A change that
sends any new kind of data off your phone will be shown in the app before it takes effect.

## Contact
Questions: {{CONTACT_EMAIL}}

---

# Hosting options

Files to host together: `site/index.html` (homepage), `site/privacy-policy.html`, `site/icon.png`.
Resulting URLs: `https://<host>/` and `https://<host>/privacy-policy.html`.

| Option | Cost | Works for Play | Works for Google OAuth verification | Notes |
|---|---|---|---|---|
| **Cloudflare Pages + `fafo.crandell.ws` (recommended)** | Free | Yes | Yes: crandell.ws verified in Search Console with one DNS TXT record at GoDaddy | Deploy from the private repo (build output dir `store/site`) or drag-and-drop upload; custom domain = one CNAME at GoDaddy |
| GitHub Pages from a new **public** repo `CrandellWS/fafo-site` (only the site files) + `fafo.crandell.ws` | Free | Yes | Yes, with the custom domain | Free plan serves Pages only from public repos; the app repo is private. https://docs.github.com/en/pages/getting-started-with-github-pages/about-github-pages |
| GitHub Pages from the app repo | Free only if the repo becomes public | Yes | Yes, with the custom domain | Fine once Bill open-sources the app |
| Netlify + custom domain | Free | Yes | Yes | Same shape as Cloudflare Pages |
| `*.github.io` / `*.pages.dev` address with no custom domain | Free | Yes | Awkward: the authorized domain must be one Bill can verify in Search Console | OK for a Play-only (core) release |

Why recommended: it keeps the code repo private, survives a later switch of host (the domain stays), and
the same domain is what Google OAuth verification needs (homepage and privacy policy on a verified domain
listed as an authorized domain). DNS for crandell.ws is at GoDaddy.

Steps (Bill):
1. Fill the placeholders, commit.
2. Cloudflare dashboard > Workers & Pages > Create > Pages > connect GitHub repo `CrandellWS/fafo-security`,
   framework preset None, build command empty, output directory `store/site`. Or Upload assets: drag `store/site/`.
3. Custom domains > add `fafo.crandell.ws`; at GoDaddy add the CNAME it shows.
4. Open both URLs logged out (private window) to confirm they are public.
5. Search Console > Add property > Domain `crandell.ws` > copy the TXT record > GoDaddy DNS > add TXT > Verify.
