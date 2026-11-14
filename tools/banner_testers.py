# "Testers needed" banner, in the style of docs/images/banner.png: black with a faint dot grid,
# Run: python3 tools/banner_testers.py docs/images/testers-needed.png <path to Ndot77JPExtended.ttf>
# a dot-matrix title (a 5x7 font, every lit cell a glowing dot), NDot 77 text lines.
import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter
W, H = 2560, 880
FONT = sys.argv[2]  # Nothing's NDot 77 (not part of this repository)
G = {
 'T':["11111","00100","00100","00100","00100","00100","00100"],
 'E':["11111","10000","10000","11110","10000","10000","11111"],
 'S':["01111","10000","10000","01110","00001","00001","11110"],
 'R':["11110","10001","10001","11110","10100","10010","10001"],
 'N':["10001","11001","10101","10011","10001","10001","10001"],
 'D':["11110","10001","10001","10001","10001","10001","11110"],
 ' ':["00000"]*7,
}
img = Image.new("RGB", (W, H), (0, 0, 0))
d = ImageDraw.Draw(img)
for y in range(12, H, 24):          # the faint panel grid
    for x in range(12, W, 24):
        d.ellipse((x - 1.5, y - 1.5, x + 1.5, y + 1.5), fill=(22, 22, 22))
def title(text, top, pitch, r, colour):
    cols = sum(6 for ch in text) - 1
    left = (W - cols * pitch) // 2
    glow = Image.new("RGB", (W, H), (0, 0, 0)); gd = ImageDraw.Draw(glow)
    x0 = left
    pts = []
    for ch in text:
        for row, bits in enumerate(G[ch]):
            for c, b in enumerate(bits):
                if b == "1":
                    pts.append((x0 + c * pitch, top + row * pitch))
        x0 += 6 * pitch
    for x, y in pts:
        gd.ellipse((x - r * 1.6, y - r * 1.6, x + r * 1.6, y + r * 1.6), fill=colour)
    glow = glow.filter(ImageFilter.GaussianBlur(r * 1.4))
    img.paste(Image.blend(img, glow, 0.0), (0, 0))
    base = Image.composite(glow, img, glow.convert("L").point(lambda v: min(255, v * 2)))
    img.paste(base)
    dd = ImageDraw.Draw(img)
    for x, y in pts:
        dd.ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255))
    return top + 7 * pitch
bottom = title("TESTERS NEEDED", 150, 26, 9, (90, 90, 90))
# A red dot, as in the face's eye: the one that is "on".
d = ImageDraw.Draw(img)
def line(text, y, size, colour):
    f = ImageFont.truetype(FONT, size)
    w = d.textlength(text, font=f)
    d.text(((W - w) / 2, y), text, font=f, fill=colour)
y = bottom + 110
line("HAVE A NOTHING OR CMF PHONE ON NOTHING OS 4.0, 4.1 OR 5.0?", y, 46, (225, 225, 225)); y += 95
for t in ["TRY NOTHING2SOMETHING AND TELL US WHAT WORKS AND WHAT DOES NOT",
          "WHAT TO EXPECT ON YOUR PHONE: THE TESTING SECTION BELOW",
          "SEND A SCREENSHOT OF DIAGNOSTICS > HOOK HEALTH  •  LSPOSED LOGS ONLY IN PRIVATE"]:
    line(t, y, 34, (150, 150, 150)); y += 62
line("EVERY TESTER WILL BE CREDITED  •  THANK YOU", y + 30, 32, (60, 220, 110))
img.save(sys.argv[1])
