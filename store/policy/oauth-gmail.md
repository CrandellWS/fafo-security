# Google Cloud OAuth for Gmail send (play flavor only)

Bill does every console step. Nothing here touches Play or Cloud by itself.

Scope: `https://www.googleapis.com/auth/gmail.send` ("Send email on your behalf"), classified
**Sensitive** (https://developers.google.com/workspace/gmail/api/auth/scopes). Sensitive, not restricted:
no CASA security assessment, but Google verification is needed before more than 100 users and to remove
the "unverified app" screen.

Android flow (no server, no refresh token on the device): Credential Manager for sign-in, then
`Identity.getAuthorizationClient(...).authorize()` with the gmail.send scope; each send calls authorize()
again and gets a fresh short-lived access token silently once granted. Do not call
`requestOfflineAccess` (that needs a Web client and a server). https://developer.android.com/identity/authorization

## 1. Project
- [ ] Create a project "FAFO Security": https://console.cloud.google.com/projectcreate
- [ ] Enable the Gmail API: https://console.cloud.google.com/apis/library/gmail.googleapis.com
- [ ] Optional but recommended: a second project "FAFO Security Dev" in Testing status for debug builds (see step 5).

## 2. Google Auth Platform > Branding  (https://console.cloud.google.com/auth/branding)
- App name: **FAFO Security** (must match the Play listing and the app's name on the phone)
- User support email: Bill's address or a Google Group he owns
- App logo: store/graphics/oauth-logo-120.png (120x120). Uploading a logo triggers brand verification; skip it while testing if you want to avoid that.
- App home page: `https://<site>/` (store/site/index.html). A Play Store link does NOT count as a homepage.
- Privacy policy: `https://<site>/privacy.html`, same domain as the homepage.
- Authorized domains: the site's domain (e.g. `crandell.ws`). Verify it in Search Console with an account that is Owner/Editor on the Cloud project: https://search.google.com/search-console (DNS TXT record at the registrar; crandell.ws DNS is at GoDaddy, ns43/ns44.domaincontrol.com).
- Developer contact email.

## 3. Audience  (https://console.cloud.google.com/auth/audience)
- User type: **External**.
- Publishing status **Testing** first: only listed test users (up to 100) can sign in; refresh tokens
  for apps in Testing expire after 7 days (irrelevant here since the app keeps none, but sign-in may be
  asked again). https://developers.google.com/identity/protocols/oauth2#expiration
- Then **In production**. Until verification passes, users see the "Google hasn't verified this app"
  screen and the app is capped at **100 new users in total**. https://support.google.com/cloud/answer/7454865
  That is the temporary "unverified app" note in the listing and in the app's Gmail sign-in screen.

## 4. Data access  (https://console.cloud.google.com/auth/scopes)
- Add only `.../auth/gmail.send`. No openid/email/profile beyond what Credential Manager sign-in needs
  (those are non-sensitive).

## 5. Clients  (https://console.cloud.google.com/auth/clients) - one Android client per signing certificate
Android clients match on package name + SHA-1, so every key that signs a build that will sign in needs its own client:

| Client | Package | SHA-1 source |
|---|---|---|
| Play app signing key (what users install) | ws.crandell.fafo | Play Console > Test and release > App integrity > App signing > "App signing key certificate" SHA-1 (exists after the first AAB upload) |
| Upload key (internal sharing / local release builds) | ws.crandell.fafo | Same page, "Upload key certificate", or `keytool -list -v -keystore <upload.jks>` |
| Shared debug key (keystore/debug.keystore) | ws.crandell.fafo | `2A:A7:8B:2A:6F:A1:BD:0B:38:CA:95:DE:73:52:94:B9:60:49:29:B7` |

- Warning: the debug key is public in the repo, so anyone can build an APK that matches the debug client
  and show Google's consent screen under the FAFO name. Put the debug client in the separate **Dev project
  kept in Testing** (only Bill's test accounts can sign in), never in the production project being verified.
  The play flavor then needs the matching project's client wiring per build type (Android clients need no
  client ID in code; Google matches package + SHA-1, so registering the client in the right project is enough).
- F-Droid/oss builds have no Google sign-in, so no client.

## 6. Verification  (Google Auth Platform > Verification center)
Requirements: https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification
- [ ] Homepage public, clearly about FAFO Security, on the verified domain.
- [ ] Privacy policy on the same domain, linked from the consent screen, describing Google data use, plus
      the Limited Use sentence (already in site/privacy.html):
      "FAFO Security's use and transfer to any other app of information received from Google APIs will adhere to the Google API Services User Data Policy, including the Limited Use requirements."
      https://developers.google.com/terms/api-services-user-data-policy
- [ ] Scope justification (paste):
      "FAFO Security is an anti-theft alarm. When the user's phone is unplugged without being unlocked, the
      app takes photos and, if the user turned on photo email, sends them from the user's own Gmail account
      to recipients the user typed in. gmail.send is the narrowest scope that can send a message; the app
      never reads, lists, modifies or deletes mail, and there is no server: the request goes straight from
      the phone to the Gmail API."
- [ ] Demo video, YouTube **Unlisted**, in English, showing: (1) the Cloud Console Clients page with the
      Android client ID and package name (Android sign-in has no browser address bar, so show the client
      ID here and say so in the submission notes); (2) in the app: Settings > Photo email > Sign in with
      Google; the consent screen showing the app name "FAFO Security" and the gmail.send permission;
      (3) the scope in use: arm, unplug, alarm, then the recipient's inbox receiving the trigger-photo email
      sent from the user's account.
- [ ] Sensitive scope review typically takes 3-5 business days (Google).
      Brand verification results must be published within 7 days.
- [ ] After approval: listing and in-app "unverified app" notes can be removed.
