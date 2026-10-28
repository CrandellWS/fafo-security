# store/: Google Play release packs for FAFO Security

Start with **CHECKLIST.md**. Nothing here performs a console action; Bill submits everything.

| Path | What |
|---|---|
| CHECKLIST.md | Ordered release checklist with sources |
| listing/en-US/ | title, short and full description (round 3 and core variants), release notes |
| graphics/ | icon-512.png, feature-graphic-1024x500.png, oauth-logo-120.png |
| screenshots/phone/ | 8 framed 1080x1920 screenshots + SOURCES.txt (which capture each came from) |
| screenshots/shots.json | captions, order and filename patterns for the framing script |
| policy/ | Data safety, app content (ads, access, rating, audience), permission/FGS declarations, Gmail OAuth |
| site/ | homepage and privacy policy, ready to host (fill the {{placeholders}}) |
| brand/ | shield, wordmark and Barlow Condensed fonts used by the scripts |
| tools/ | make_graphics.py, make_screenshots.py, check_listing.py (Pillow) |

Regenerate: `python3 store/tools/make_graphics.py && python3 store/tools/make_screenshots.py && python3 store/tools/check_listing.py`
