# Content rating and other App content answers

Play Console > Policy > App content. Checked against Play Help on 2026-10-28; recheck any question whose
wording differs. Data safety: DATA-SAFETY.md. Declarations: PERMISSIONS-DECLARATIONS.md. App access:
REVIEWER-ACCESS.md.

## Content rating (IARC questionnaire)
https://support.google.com/googleplay/android-developer/answer/9898843

| Question | Answer |
|---|---|
| Email address (IARC certificate goes here) | Bill's developer email |
| Category | **All Other App Types** / "Utility, Productivity, Communication, or Other" (wording varies) |
| Violence (any kind, cartoon or realistic) | No |
| Fear / horror content | No (a siren and a red alarm screen are not horror) |
| Sexuality, nudity | No |
| Language (profanity, crude humor) | No. The name "FAFO" is an acronym spelled out as "Free Advanced Features Offline"; no profanity appears in the app or listing |
| Controlled substances (drugs, alcohol, tobacco) | No |
| Gambling, simulated gambling | No |
| Does the app allow users to interact or exchange content? | **Yes**: voice intercom and push-to-talk between the user's own paired devices on the local network or Bluetooth |
| Does it share the user's current physical location with other users? | No |
| Does it allow users to purchase digital goods? | No |
| Unrestricted internet / web browser? | No |
| Is it a web browser or search engine? | No |
| Is it primarily a news or educational product? | No |

Expected result: Everyone / PEGI 3 / USK 0 with the interactive element "Users Interact".
If the result shows anything higher, re-read the answers before accepting it.

## Ads
**No, my app does not contain ads.**

## Target audience and content
https://support.google.com/googleplay/android-developer/answer/9867159
- Target age groups: **18 and over** (keeps the app out of the Families policy; 13-17 can be added later,
  which brings extra checks).
- Could the app unintentionally appeal to children? **No** (no child-themed art; the listing is about
  protecting your own phone).
- Store listing shown to children: not applicable.

## Other declarations
| Form | Answer |
|---|---|
| Privacy policy | URL of site/privacy-policy.html once hosted (PRIVACY-POLICY.md) |
| News app | No |
| Government app | No |
| Financial features | My app doesn't provide any financial features |
| Health apps | My app does not have any health features |
| COVID-19 contact tracing and status | Not a publicly available COVID-19 contact tracing or status app |
| Account deletion | Not applicable: the app has no accounts (Data safety: "no account creation") |
| Advertising ID | No (manifest does not declare com.google.android.gms.permission.AD_ID; no ads SDK) |
