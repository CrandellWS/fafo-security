# store/: Google Play release pack for FAFO Security

Start with **RELEASE-CHECKLIST.md**. Nothing here performs a Play Console or Google Cloud action; Bill
submits everything.

| Path | What |
|---|---|
| RELEASE-CHECKLIST.md | Decisions, upload key, versioning, AAB build, internal test to production, every Console step with sources |
| LISTING.md | Title, short and full description, category, tags, contact, graphics map |
| listing/en-US/ | Exact upload text: title, short description, full description (core and round 3), release notes |
| graphics/ | icon-512.png, feature-graphic-1024x500.png, oauth-logo-120.png, phone/ screenshots (8 at 1080x1920), shots.json, make_store_shots.py |
| PRIVACY-POLICY.md | Policy source text, round 3 caveats, hosting options and the recommended one |
| ../docs/ | Website (GitHub Pages): index.html (homepage), privacy-policy.html, icon.png, CNAME.pending. Policy URL https://crandellws.github.io/fafo-security/privacy-policy.html |
| DATA-SAFETY.md | Every Data safety answer, core build and photo-email build |
| CONTENT-RATING.md | IARC answers plus ads, target audience and other App content forms |
| PERMISSIONS-DECLARATIONS.md | Foreground service, full-screen intent, battery and (optional) location declaration text and video shot lists |
| REVIEWER-ACCESS.md | App access instructions so a reviewer can test with one phone or an emulator |
| GOOGLE-CLOUD-OAUTH-SETUP.md | Click-by-click Cloud setup for gmail.send with values and SHA-1s, plus verification checklist |
| brand/ | Shield, wordmark and Barlow Condensed fonts used by the scripts |
| tools/ | make_graphics.py (icon, feature graphic, OAuth logo), check_listing.py (limits, emoji, banned words) |

Regenerate (Pillow):
```bash
python3 store/tools/make_graphics.py
python3 store/graphics/make_store_shots.py          # round 3 shots if present, else round 2; or pass a folder
python3 store/tools/check_listing.py
```
