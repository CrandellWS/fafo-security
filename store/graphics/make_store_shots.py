#!/usr/bin/env python3
"""Frame app screenshots for the Play listing (re-runnable).

  python3 store/graphics/make_store_shots.py [SOURCE_DIR]

SOURCE_DIR defaults to the round-3 shots if that folder exists, else round 2 (see R3/R2 below), or set
FAFO_SHOTS. Reads store/graphics/shots.json, writes store/graphics/phone/NN-*.png at 1080x1920
(9:16, 24-bit PNG, no alpha) and phone/SOURCES.txt naming the file used for each slot. When more than 8
shots match, the ones with the highest "rank" number are dropped first; Play order is the list order.
Tablet sets are not generated: they are optional and the app is untested on tablets. Play allows at
most 8 per device type and a long side no more than 2x the short side, so raw 1078x2338 captures
(2.17:1) cannot be uploaded as they are: they are framed with a caption instead.
Specs: https://support.google.com/googleplay/android-developer/answer/9866151
"""
import fnmatch, json, os, sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
BRAND = ROOT / "brand"
SCRATCH = Path("/tmp/claude-1000/-home-crandellws/f7afe390-9ed8-4ba7-aebc-37c96a60172c/scratchpad")
R3, R2 = SCRATCH / "fafo-r3/shots", SCRATCH / "fafo-ui/shots"
NAVY, NAVY_DEEP, RED, WHITE = (11, 31, 53), (7, 20, 35), (214, 16, 24), (255, 255, 255)
W, H, MAX_SHOTS = 1080, 1920, 8


def pick_source():
    if len(sys.argv) > 1:
        return Path(sys.argv[1]), sys.argv[1]
    if os.environ.get("FAFO_SHOTS"):
        return Path(os.environ["FAFO_SHOTS"]), "FAFO_SHOTS"
    if R3.is_dir() and any(R3.glob("*.png")):
        return R3, "round 3"
    return R2, "round 2"


def find(files, patterns):
    for pat in patterns:
        hits = sorted(f for f in files if fnmatch.fnmatch(f, pat))
        if hits:
            return hits[0]
    return None


def wrap(draw, text, fnt, width):
    lines, cur = [], ""
    for word in text.split():
        trial = (cur + " " + word).strip()
        if draw.textlength(trial, font=fnt) <= width or not cur:
            cur = trial
        else:
            lines.append(cur)
            cur = word
    lines.append(cur)
    return lines


def frame(src, caption, accent):
    im = Image.new("RGB", (W, H), NAVY)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, W, 12), fill=RED)
    fnt = ImageFont.truetype(str(BRAND / "barlow_condensed_extrabold.ttf"), 92)
    lines = wrap(d, caption, fnt, W - 140)
    y = 90
    for line in lines:
        x = (W - d.textlength(line, font=fnt)) / 2
        # one accent word in red, the rest white
        for i, word in enumerate(line.split()):
            col = RED if accent and word.strip(".,?!").lower() == accent.split()[0].lower() else WHITE
            if accent and " " not in accent and accent.lower() in word.lower():
                col = RED
            d.text((x, y), word, font=fnt, fill=col)
            x += d.textlength(word + " ", font=fnt)
        y += 104
    top = y + 50
    shot = Image.open(src).convert("RGB")
    sw = 860
    shot = shot.resize((sw, int(shot.height * sw / shot.width)), Image.LANCZOS)
    shot = shot.crop((0, 0, sw, min(shot.height, H - top + 40)))
    radius = 44
    mask = Image.new("L", shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, shot.width, shot.height + radius), radius, fill=255)
    sx = (W - sw) // 2
    shadow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(shadow).rounded_rectangle((sx - 6, top - 6, sx + sw + 6, H + 60), radius + 6, fill=150)
    shadow = shadow.filter(ImageFilter.GaussianBlur(18))
    im = Image.composite(Image.new("RGB", (W, H), NAVY_DEEP), im, shadow)
    ImageDraw.Draw(im).rounded_rectangle((sx - 4, top - 4, sx + sw + 4, H + 60), radius + 4, fill=(48, 72, 100))
    im.paste(shot, (sx, top), mask)
    return im


def main():
    src_dir, label = pick_source()
    if not src_dir.is_dir():
        sys.exit(f"no screenshot folder at {src_dir}")
    files = [p.name for p in src_dir.glob("*.png")]
    cfg = json.loads((ROOT / "graphics/shots.json").read_text())
    out = ROOT / "graphics/phone"
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.png"):
        old.unlink()
    is_r3 = label == "round 3" or "r3" in str(src_dir)
    picked = []
    for i, s in enumerate(cfg["shots"]):
        if s.get("requires_r3") and not is_r3:
            continue
        hit = find(files, s["match"])
        if not hit:
            print(f"skip (no match): {s['caption']}")
            continue
        picked.append((i, s, hit))
    while len(picked) > MAX_SHOTS:
        drop = max(picked, key=lambda t: (t[1].get("rank", 1), t[0]))
        print(f"drop (over {MAX_SHOTS}): {drop[1]['caption']}")
        picked.remove(drop)
    used, n = [], 0
    for _, s, hit in picked:
        n += 1
        slug = "".join(c if c.isalnum() else "-" for c in s["caption"].lower()).strip("-")[:40]
        dest = out / f"{n:02d}-{slug}.png"
        frame(src_dir / hit, s["caption"], s.get("accent", "")).save(dest, optimize=True)
        used.append(f"{dest.name}\t<- {hit}")
    (out / "SOURCES.txt").write_text(f"source: {src_dir} ({label})\n" + "\n".join(used) + "\n")
    print(f"{n} screenshots from {label}: {src_dir}")
    print("\n".join(used))
    if n < 2:
        sys.exit("Play needs at least 2 screenshots")


if __name__ == "__main__":
    main()
