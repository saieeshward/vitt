"""Draws the VITT app icon and writes every size the two stores ask for.

The mark is the app's own rhythm: a currency dot, then a bar standing for an
amount, three times over. It is what a ledger card and a transaction row and a
monogram ring are all already made of, so the icon is the product's own visual
language rather than a separate illustration of it.

Three rows in three different hues, and the bars are *different lengths and
never aligned into a column* — that is the one idea VITT has that no other
expense app does: the figures sit side by side and are never added up. A single
symbol, or one stack of equal bars, would say the opposite.

Deliberately not the companion. She is optional — "None" is a real choice and
the gamification switch turns her off entirely — so an identity resting on her
has nothing to show the user who declined her. She belongs on an alternate
icon later, not on the one that has to work for everybody.

Drawn rather than exported from a design file for the same reason the sprites
are: the colours are the palette's, so they cannot drift out of step with it.

    python3 tools/icon/make_icon.py
"""
import os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "out")

CREAM = (255, 253, 247)
GREEN = (0x35, 0xB9, 0x8A)
AMBER = (0xE3, 0x9A, 0x12)
BLUE = (0x4C, 0x9D, 0xF7)
INK = (0x24, 0x1F, 0x33)

# Row: (hue, bar length as a fraction of the drawing width).
# Unequal on purpose, and in no order — three ledgers, not a bar chart with a
# trend. Ascending lengths would imply growth, which the icon has no business
# claiming about anyone's money.
ROWS = [(GREEN, 0.62), (AMBER, 0.94), (BLUE, 0.44)]


def draw(size: int, bg=CREAM, bar=INK, scale=1.0) -> Image.Image:
    """One icon at `size` px. Supersampled 4x, because the dots are circles.

    `scale` shrinks the mark within the frame without moving it off centre, for
    Android's adaptive foreground: that layer is 108dp with only the middle
    72dp guaranteed visible, because a launcher may mask it to a circle, a
    squircle or a rounded square and it animates during a parallax. Anything
    outside two-thirds of the width can be cropped on somebody's phone.
    """
    s = size * 4
    img = Image.new("RGBA" if bg is None else "RGB", (s, s),
                    (0, 0, 0, 0) if bg is None else bg)
    d = ImageDraw.Draw(img)

    # A generous margin: iOS masks the corners and Android shrinks the
    # foreground inside a safe circle, so anything near an edge is lost on one
    # platform or the other.
    pad = s * (0.5 - (0.5 - 0.17) * scale)
    inner = s - pad * 2
    dot_r = inner * 0.088
    bar_h = dot_r * 1.2
    gap = dot_r * 1.45
    # Three rows centred vertically, evenly pitched.
    pitch = inner * 0.315
    top = s / 2 - pitch

    for i, (hue, length) in enumerate(ROWS):
        cy = top + pitch * i
        cx = pad + dot_r
        d.ellipse([cx - dot_r, cy - dot_r, cx + dot_r, cy + dot_r], fill=hue)
        x0 = cx + dot_r + gap
        x1 = x0 + (pad + inner - x0) * length
        d.rounded_rectangle(
            [x0, cy - bar_h / 2, x1, cy + bar_h / 2],
            radius=bar_h / 2,
            fill=bar,
        )

    return img.resize((size, size), Image.LANCZOS)


# iOS wants a single 1024 in the asset catalogue; Android generates its own
# densities from the adaptive-icon layers, so the foreground is drawn on a
# transparent field there and the background is a flat cream.
IOS = [1024]
ANDROID_FOREGROUND = [432]
PREVIEW = [1024, 180, 120, 80, 40]

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for n in IOS:
        draw(n).save(os.path.join(OUT, f"ios-{n}.png"))
    for n in PREVIEW:
        draw(n).save(os.path.join(OUT, f"preview-{n}.png"))
    # Play's listing icon.
    draw(512).save(os.path.join(OUT, "play-512.png"))
    # Android's adaptive foreground: transparent, and inside the safe zone.
    draw(432, bg=None, scale=0.62).save(os.path.join(OUT, "android-foreground.png"))
    # The dark-ground variant, for the Android monochrome/themed path and to
    # check the mark survives an inverted ground.
    draw(1024, bg=(0x17, 0x14, 0x1F), bar=(0xDA, 0xD5, 0xE6)).save(
        os.path.join(OUT, "dark-1024.png")
    )
    print("wrote", sorted(os.listdir(OUT)))
