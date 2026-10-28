#!/usr/bin/env python3
"""Check listing text against Play limits and the metadata policy word list.

  python3 store/tools/check_listing.py
Limits: title 30, short 80, full 4000, release notes 500 (all characters count).
https://support.google.com/googleplay/android-developer/answer/9859152
https://support.google.com/googleplay/android-developer/answer/9898842
"""
import re, sys
from pathlib import Path

D = Path(__file__).resolve().parent.parent / "listing/en-US"
LIMITS = {"title.txt": 30, "short-description.txt": 80, "full-description.txt": 4000,
          "full-description-core.txt": 4000, "release-notes.txt": 500}
BANNED = [r"\bbest\b", r"#\s?1\b", r"\bnumber one\b", r"\btop[- ]rated\b", r"\bguarantee[sd]?\b(?! *\.)",
          r"\bunstoppable\b", r"\bunhackable\b", r"\b100 ?%", r"\bpolice\b", r"\blaw enforcement\b",
          r"\bspy\b", r"\bbaby monitor\b", r"\blisten in\b", r"\btrack anyone\b", r"\bstalk", r"\bnew\b",
          r"\beditor'?s choice\b", r"\bdownload now\b", r"\bfree\b"]
EMOJI = re.compile("[\U0001F300-\U0001FAFF☀-➿]")
fail = False
for name, lim in LIMITS.items():
    t = (D / name).read_text().rstrip("\n")
    n = len(t)
    flags = []
    if n > lim:
        flags.append(f"OVER {lim}")
        fail = True
    if EMOJI.search(t):
        flags.append("EMOJI")
        fail = True
    for b in BANNED:
        for m in re.finditer(b, t, re.I):
            ctx = t[max(0, m.start() - 25): m.end() + 25].replace("\n", " ")
            # allowed: brand expansion "Free Advanced Features Offline", and "not a guarantee"
            if "Free Advanced" in ctx or "not a guarantee" in ctx or "New circles" in ctx:
                continue
            flags.append(f"word '{m.group(0)}' in: ...{ctx}...")
            if name in ("title.txt", "short-description.txt"):
                fail = True
    print(f"{name}: {n}/{lim}" + ("" if not flags else "  " + " | ".join(flags)))
sys.exit(1 if fail else 0)
