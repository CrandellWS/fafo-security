# Data safety form answers (Play Console > Policy > App content > Data safety)

Definitions used (Play Help, https://support.google.com/googleplay/android-developer/answer/10787469):
"collect" = data transmitted off the device by the app; "share" = transferred to a third party. Exempt from
"collected": data processed only on the device, and data that is end-to-end encrypted so no intermediary
(including the developer) can read it. Exempt from "shared": user-initiated transfers the user reasonably
expects. Apps that collect nothing still fill in the form and link a privacy policy.

Pick the column that matches the AAB you submit.

## A. Build WITHOUT photo email (round 2 / core)

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | Photos, settings and keys stay on the phone. Alerts, commands and intercom audio go device-to-device on the local network or Bluetooth, end-to-end encrypted with the paired keys (E2E exemption). No analytics or crash SDKs. |
| Privacy policy | site/privacy.html URL | Required even with no data. |

Play then shows "No data collected" and "No data shared with third parties".

## B. Build WITH photo email (round 3, play flavor)

Conservative reading: the app itself transmits the photos off the device (SMTP or Gmail API), so declare
them as collected. (Play's FAQ lets apps skip declaring uploads a user sends to their own cloud account
only when "your app never collects or accesses the data"; FAFO takes the photos, so that exemption is weak.)

| Question | Answer |
|---|---|
| Collect or share any required data types? | **Yes** |
| All user data encrypted in transit? | **Yes**, only if round 3 refuses plaintext SMTP (SMTPS 465 or STARTTLS required, no "send without TLS" option) and Gmail goes over HTTPS. If plaintext SMTP is possible, the answer must be **No**. Verify in code before answering. |
| Way for users to request deletion? | **Yes**: photos and email settings are deleted in the app (Incidents, Settings) or by uninstalling. The developer holds no copy. Mail already delivered sits in the mailboxes involved, under the mail provider's terms. |
| Independent security review | No |
| Committed to Families policy | Not applicable (target audience 18+) |

Data types to tick:

| Data type | Collected | Shared | Ephemeral | Required/optional | Purposes |
|---|---|---|---|---|---|
| Photos and videos > **Photos** | Yes | **No** (user-directed send to addresses the user chose; user-initiated transfer exclusion) | No | **Optional** (photo email is off until the user turns it on) | App functionality |
| Personal info > **Email address** | Yes (the sending account address and login go to the user's mail server; Gmail sign-in returns the account email) | No | No | Optional | App functionality |

Do NOT tick: Audio (intercom is E2E device-to-device and never stored), Location (none), Device or other
IDs (none), App activity, App info and performance (no crash reporter; Play's own vitals are Google's),
Contacts (recipients are typed in, not read from Contacts; if round 3 adds a contacts picker via
ACTION_PICK it is still on-device only).

Recipient addresses typed by the user are other people's emails sent to the user's own mail server as
message headers. They are not "the user's" email address; the form has no separate type for them.
Mention them in the privacy policy (done) rather than in the form.

Open Wi-Fi fallback: no extra data type, provided it does not request location. See
permission-declarations.md (scan results on Android 11/12 need ACCESS_FINE_LOCATION; avoid).

Re-check these answers against the live form at submission time; Play changes labels occasionally.
