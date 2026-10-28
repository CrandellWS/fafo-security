#!/usr/bin/env python3
"""Build the Play listing graphics from store/brand/.

  python3 store/tools/make_graphics.py

Writes store/graphics/icon-512.png (32-bit PNG, full-square navy, shield centered; Play applies its
own rounded mask) and store/graphics/feature-graphic-1024x500.png (24-bit PNG, no alpha).
Specs: https://support.google.com/googleplay/android-developer/answer/9866151
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
BRAND, OUT = ROOT / "brand", ROOT / "graphics"
NAVY, NAVY_DEEP, RED, WHITE = (11, 31, 53), (7, 20, 35), (214, 16, 24), (255, 255, 255)
GREY = (176, 190, 206)


def font(name, size):
    return ImageFont.truetype(str(BRAND / name), size)


def icon():
    shield = Image.open(BRAND / "shield-1024.png").convert("RGBA")
    S = 2048  # supersample, then downscale
    im = Image.new("RGBA", (S, S), NAVY + (255,))
    # soft radial lift behind the shield so the white sticker edge reads on navy
    glow = Image.new("L", (S, S), 0)
    ImageDraw.Draw(glow).ellipse((S * .14, S * .14, S * .86, S * .86), fill=60)
    from PIL import ImageFilter
    glow = glow.filter(ImageFilter.GaussianBlur(S * .08))
    im = Image.composite(Image.new("RGBA", (S, S), (34, 66, 102, 255)), im, glow)
    # Google keylines: keep the shape inside the central ~ 80 % so the mask never clips it
    side = int(S * 0.80)
    sh = shield.resize((side, side), Image.LANCZOS)
    im.alpha_composite(sh, ((S - side) // 2, (S - side) // 2))
    im.resize((512, 512), Image.LANCZOS).save(OUT / "icon-512.png", optimize=True)
    # Google OAuth consent-screen logo: square 120x120 (uploading one starts brand verification)
    im.resize((120, 120), Image.LANCZOS).save(OUT / "oauth-logo-120.png", optimize=True)


def feature():
    W, H, k = 1024, 500, 2
    im = Image.new("RGB", (W * k, H * k), NAVY)
    d = ImageDraw.Draw(im)
    # diagonal deep-navy panel and a red rule: simple, brand colours only
    d.polygon([(0, 0), (int(W * k * .44), 0), (int(W * k * .36), H * k), (0, H * k)], fill=NAVY_DEEP)
    d.rectangle((0, H * k - 10 * k, W * k, H * k), fill=RED)
    shield = Image.open(BRAND / "shield-1024.png").convert("RGBA")
    sh = shield.resize((340 * k, 340 * k), Image.LANCZOS)
    im.paste(sh, (52 * k, 72 * k), sh)
    wm = Image.open(BRAND / "wordmark-dark-white.png").convert("RGBA")
    ww = 540 * k
    wm = wm.resize((ww, int(wm.height * ww / wm.width)), Image.LANCZOS)
    x0, y0 = 430 * k, 92 * k
    im.paste(wm, (x0, y0), wm)
    y = y0 + wm.height + 26 * k
    f1 = font("barlow_condensed_bold.ttf", 46 * k)
    f2 = font("barlow_condensed_bold.ttf", 30 * k)
    d.text((x0 + 4 * k, y), "Unplug alarm for your own phone", font=f1, fill=WHITE)
    y += 62 * k
    d.text((x0 + 4 * k, y), "Trusted devices  ·  Intercom  ·  Works offline", font=f2, fill=GREY)
    y += 44 * k
    d.text((x0 + 4 * k, y), "No ads  ·  No account  ·  No tracking", font=f2, fill=GREY)
    im = im.resize((W, H), Image.LANCZOS)
    im.save(OUT / "feature-graphic-1024x500.png", optimize=True)


if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    icon()
    feature()
    for p in sorted(OUT.glob("*.png")):
        i = Image.open(p)
        print(p.name, i.size, i.mode, f"{p.stat().st_size // 1024} KB")
