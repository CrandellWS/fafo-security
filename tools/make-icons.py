#!/usr/bin/env python3
"""Regenerates FAFO Security's launcher icon and in-app logo bitmaps from the approved shield PNG.

Usage: python3 tools/make-icons.py <fafo-shield-1024.png> [repo root]

Writes (committed, so the build never needs this script or Pillow):
  app/src/main/res/mipmap-<density>/ic_launcher_foreground.webp   adaptive foreground, 108dp canvas
  app/src/main/res/mipmap-<density>/ic_launcher_monochrome.webp   themed-icon layer (white silhouette)
  app/src/main/res/drawable-nodpi/fafo_shield.webp                bitmap fallback (in-app art is the vector ShieldMark, tools/trace-shield.py)

The shield is scaled as large as possible while every opaque pixel stays inside the 66dp safe circle
(radius 33dp) of the 108dp adaptive-icon canvas, so no launcher mask (circle, squircle, teardrop) clips it.
Needs Pillow with WebP support.
"""
import math
import sys
from pathlib import Path

from PIL import Image

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
CANVAS_DP = 108
SAFE_RADIUS_DP = 33.0 - 0.5  # half a dp of air inside the safe circle
LOGO_HEIGHT_PX = 768


def crop_to_alpha(img: Image.Image) -> Image.Image:
    bbox = img.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox()
    return img.crop(bbox)


def max_radius(img: Image.Image) -> float:
    """Farthest visible pixel from the image center, in source pixels."""
    a = img.getchannel("A")
    w, h = img.size
    cx, cy = w / 2, h / 2
    px = a.load()
    best = 0.0
    for y in range(0, h, 2):
        for x in range(0, w, 2):
            if px[x, y] > 128:
                best = max(best, math.hypot(x + 0.5 - cx, y + 0.5 - cy))
    return best


def monochrome(img: Image.Image) -> Image.Image:
    """White silhouette: opaque where the logo is navy or red, a hole where it is white or transparent."""
    out = Image.new("RGBA", img.size, (255, 255, 255, 0))
    src = img.load()
    dst = out.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = src[x, y]
            whiteness = min(r, g, b) / 255.0  # navy and red both have a dark minimum channel
            ink = max(0.0, min(1.0, (0.78 - whiteness) / 0.5))
            dst[x, y] = (255, 255, 255, int(a * ink))
    return out


def foreground(shield: Image.Image, scale_dp_per_px: float, density: float) -> Image.Image:
    size = round(CANVAS_DP * density)
    w = max(1, round(shield.width * scale_dp_per_px * density))
    h = max(1, round(shield.height * scale_dp_per_px * density))
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(shield.resize((w, h), Image.LANCZOS), ((size - w) // 2, (size - h) // 2))
    return canvas


def main() -> None:
    src = Path(sys.argv[1])
    root = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(__file__).resolve().parent.parent
    res = root / "app/src/main/res"
    shield = crop_to_alpha(Image.open(src).convert("RGBA"))
    scale = SAFE_RADIUS_DP / max_radius(shield)  # dp per source pixel
    print(f"shield {shield.width}x{shield.height}px -> {shield.width * scale:.1f}x{shield.height * scale:.1f}dp on a {CANVAS_DP}dp canvas")
    mono = monochrome(shield)
    for name, d in DENSITIES.items():
        out = res / f"mipmap-{name}"
        out.mkdir(parents=True, exist_ok=True)
        foreground(shield, scale, d).save(out / "ic_launcher_foreground.webp", "WEBP", lossless=True, quality=100, method=6)
        foreground(mono, scale, d).save(out / "ic_launcher_monochrome.webp", "WEBP", lossless=True, quality=100, method=6)
    nodpi = res / "drawable-nodpi"
    nodpi.mkdir(parents=True, exist_ok=True)
    logo_w = round(shield.width * LOGO_HEIGHT_PX / shield.height)
    shield.resize((logo_w, LOGO_HEIGHT_PX), Image.LANCZOS).save(nodpi / "fafo_shield.webp", "WEBP", lossless=True, quality=100, method=6)


if __name__ == "__main__":
    main()
