# Google Cloud OAuth setup for Gmail photo email (play flavor only)

Bill does every console step; nothing in this repo touches Google Cloud. Needed only if the Play build
includes "Sign in with Google" for photo email. The oss/F-Droid build has no Google sign-in and needs none.
Console labels checked 2026-10-28 (Google Auth Platform menu: Overview, Branding, Audience, Clients, Data
access, Verification center); recheck if a screen looks different.

## Values used below (pre-filled)

| Field | Value |
|---|---|
| Production project name | `FAFO Security` (project ID suggestion `fafo-security`; Google appends digits if taken) |
| Dev project name | `FAFO Security Dev` (ID `fafo-security-dev`), stays in Testing forever |
| App name on consent screen | `FAFO Security` (same as the app label on the phone) |
| User support email | Bill's Google account email, or a Google Group he owns |
| App logo | `store/graphics/oauth-logo-120.png` (120x120 PNG). Optional; uploading one triggers brand verification |
| Application home page | `https://fafo.crandell.ws/` |
| Privacy policy link | `https://fafo.crandell.ws/privacy-policy.html` |
| Terms of service link | leave empty (optional) |
| Authorized domain | `crandell.ws` (recommended; see options below) |
| Developer contact email | Bill's email |
| User type | External |
| Scope | `https://www.googleapis.com/auth/gmail.send` (Sensitive) only |
| Client type | Android |
| Package name | `ws.crandell.fafo` |
| Debug key SHA-1 (computed 2026-10-28 from `keystore/debug.keystore`, alias androiddebugkey, password android) | `2A:A7:8B:2A:6F:A1:BD:0B:38:CA:95:DE:73:52:94:B9:60:49:29:B7` |
| Debug key SHA-256 (for reference) | `9A:B9:9D:D8:04:08:9B:3C:B8:26:D5:D4:85:F1:29:A5:32:EE:99:53:17:E6:5B:E5:53:A3:2C:3D:81:12:A9:05` |
| Upload key SHA-1 | from Bill's private upload keystore (RELEASE-CHECKLIST.md step 1) |
| Play app signing key SHA-1 | from Play Console after the first AAB upload (step 6) |

Recompute the debug SHA-1 any time:
`keytool -list -v -keystore keystore/debug.keystore -storepass android -alias androiddebugkey | grep SHA1`
(on this workstation keytool is at `~/.local/opt/jdk-17.0.21+9/bin/keytool`).

### Authorized domain options
| Option | Homepage / policy URLs | Verify how |
|---|---|---|
| **`crandell.ws` with `fafo.crandell.ws` (recommended)** | as above | Search Console > Domain property `crandell.ws` > TXT record at GoDaddy |
| A dedicated domain, e.g. a new `fafo…` domain | `https://<domain>/` | Same, at its registrar. Costs a domain |
| `crandellws.github.io` | `https://crandellws.github.io/fafo-site/` | URL-prefix property with HTML file; workable but harder to keep tidy |

The Google account doing the verification must be an Owner or Editor on the Cloud project.

## How the app uses it (no Web client, no server)
The play flavor uses `play-services-auth`: `Identity.getAuthorizationClient(context).authorize()` with
the gmail.send scope shows the account picker and consent, then returns a short-lived access token; each
send calls authorize() again and gets a new token silently once granted. No `requestOfflineAccess`, so no
Web client ID and no refresh token. Google matches the calling app by **package name + signing SHA-1**,
which is why each signing key needs its own Android client. https://developer.android.com/identity/authorization

## 1. Create the projects
1. Open https://console.cloud.google.com/projectcreate
2. Project name `FAFO Security`, Organization: No organization, Create.
3. Repeat for `FAFO Security Dev`.
4. Top bar project picker: select **FAFO Security** for steps 2-5 (repeat 2-5 in Dev, using only the debug client).

## 2. Enable the Gmail API
1. https://console.cloud.google.com/apis/library/gmail.googleapis.com
2. Check the project picker shows the right project, click **Enable**.

## 3. Google Auth Platform: Branding
1. https://console.cloud.google.com/auth/branding (first visit shows **Get started**: click it).
2. App information: App name `FAFO Security`; User support email: pick Bill's address. Next.
3. Audience: **External**. Next.
4. Contact information: Bill's email. Next. Agree to the Google API Services User Data Policy. Create.
5. Back on Branding, fill: App logo (optional, see table), Application home page, Privacy policy link,
   Authorized domains > Add domain `crandell.ws`, Developer contact email. Save.

## 4. Audience
1. https://console.cloud.google.com/auth/audience
2. Publishing status starts at **Testing**. Test users > Add users: Bill's Gmail accounts (max 100).
   Only test users can sign in while in Testing.
3. When the Play build is ready for real users: **Publish app** > Confirm (status becomes In production).
   Until verification passes, users see "Google hasn't verified this app" (Advanced > Go to FAFO Security
   (unsafe)) and the app is capped at 100 users in total. https://support.google.com/cloud/answer/7454865
   This is the temporary "unverified app" note in the listing and the app.
4. Dev project: leave in **Testing** permanently with only Bill's accounts.

## 5. Data access (scopes)
1. https://console.cloud.google.com/auth/scopes > **Add or remove scopes**.
2. Filter `gmail.send`, tick `https://www.googleapis.com/auth/gmail.send` ("Send email on your behalf"),
   Update. It lands under "Your sensitive scopes". Save.
   (If not listed, "Manually add scopes" > paste the URL.) Nothing else: no gmail.readonly, no gmail.modify.
   https://developers.google.com/workspace/gmail/api/auth/scopes

## 6. Clients (one Android client per signing key)
https://console.cloud.google.com/auth/clients > **Create client** > Application type **Android**:

| Project | Name | Package name | SHA-1 certificate fingerprint |
|---|---|---|---|
| FAFO Security Dev | `FAFO debug (public key)` | `ws.crandell.fafo` | `2A:A7:8B:2A:6F:A1:BD:0B:38:CA:95:DE:73:52:94:B9:60:49:29:B7` |
| FAFO Security | `FAFO upload key` | `ws.crandell.fafo` | upload key SHA-1 |
| FAFO Security | `FAFO Play app signing` | `ws.crandell.fafo` | Play app signing SHA-1 |

Leave "Enable custom URI scheme" off. Create. No client ID needs to go into the app.

- **Debug key only in the Dev project.** The debug keystore is public in the repo, so anyone can build an
  APK that matches it. In a Testing project only Bill's test users can sign in, so it cannot be used to show
  a production consent screen under the FAFO name.
- **Upload key SHA-1:** `keytool -list -v -keystore ~/.config/fafo-keys/fafo-upload.jks -alias fafo-upload | grep SHA1`
  (path and alias from RELEASE-CHECKLIST.md). Also shown in Play Console (below) once uploaded.
- **Play app signing SHA-1 (what Play users actually install):** after the first AAB upload to any track,
  Play Console > the app > Test and release > Setup > **App signing** (older label: Release > Setup >
  App integrity > App signing). Copy **SHA-1 certificate fingerprint** under "App signing key certificate".
  The "Upload key certificate" SHA-1 is on the same page. Create the client the same day; sign-in in the
  Play-installed build fails until it exists ("DEVELOPER_ERROR" / status 10 is the usual symptom).

## 7. Verification (Verification center)
https://console.cloud.google.com/auth/verification ·
requirements: https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification

Checklist before clicking **Prepare for verification / Submit**:
- [ ] Homepage `https://fafo.crandell.ws/` public, describes FAFO Security, links the privacy policy.
- [ ] Privacy policy on the same domain, says how Google user data is used, and contains:
      "FAFO Security's use and transfer to any other app of information received from Google APIs will
      adhere to the Google API Services User Data Policy, including the Limited Use requirements."
      (already in site/privacy-policy.html). https://developers.google.com/terms/api-services-user-data-policy
- [ ] `crandell.ws` verified in Search Console by a project Owner/Editor.
- [ ] App name and logo on the consent screen match the Play listing.
- [ ] Only gmail.send requested.
- [ ] Scope justification (paste):
      "FAFO Security is an anti-theft alarm for the user's own phone. When the phone is unplugged without
      being unlocked, the app takes photos and, if the user turned on photo email, sends them from the user's
      own Gmail account to recipients the user typed in. gmail.send is the narrowest scope that can send a
      message; the app never reads, lists, modifies or deletes mail, and there is no server: the request goes
      straight from the phone to the Gmail API."
- [ ] Demo video on YouTube, **Unlisted**, English, link in the form. Shot list:
      1. Cloud Console Clients page showing the Android client ID and package `ws.crandell.fafo` (Android
         sign-in has no browser address bar; say so in the notes and show the client ID here).
      2. In the app: Settings > Photo email > Sign in with Google > account picker > consent screen showing
         "FAFO Security" and the send-email permission > Allow.
      3. Add a recipient, arm on the charger, unplug, let the alarm run, unlock.
      4. The recipient's inbox receiving the trigger-photo email from the user's account.
- [ ] Notes field: "Android-only app, no web client. Gmail is used only to send photos the user chose to
      email to recipients the user entered. No server; tokens are short-lived and never leave the device."
- Review typically takes 3-5 business days. Reply to any Google email from the verification inbox promptly.
- [ ] After approval: remove the "unverified app" note from listing/en-US/full-description.txt and the app.
