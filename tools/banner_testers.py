# "Testers needed" banner, in the style of docs/images/banner.png: black with a faint dot grid,
# a dot-matrix title (a 5x7 font, every lit cell a glowing dot), NDot 77 text lines.
import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter
W, H = 2560, 880
DUMMY = sys.argv[3]  # the mark cut from the first banner
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
YELLOW = (245, 196, 0)   # the first banner's band
RED = (220, 38, 38)

def title(text, top, pitch, r):
    """The title as red dots in a yellow box, a crash-test dummy mark on each side, as on the
    first banner's band (the mark is cut from docs/images/banner.png, so the two match)."""
    cols = len(text) * 6 - 1
    width = cols * pitch
    pad = int(pitch * 1.6)
    mark = int(7 * pitch * 0.92)
    gap = pitch * 2
    box_w = width + 2 * pad + 2 * (mark + gap)
    left = (W - box_w) // 2
    box_h = 7 * pitch + 2 * pad - pitch
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((left, top, left + box_w, top + box_h), radius=pitch, fill=YELLOW)
    dummy = Image.open(DUMMY).convert("RGB").resize((mark, mark), Image.LANCZOS)
    my = top + (box_h - mark) // 2
    img.paste(dummy, (left + pad, my))
    # Mirrored on the right, so the two marks face each other (Dr.Essence).
    img.paste(dummy.transpose(Image.FLIP_LEFT_RIGHT), (left + box_w - pad - mark, my))
    x0 = left + pad + mark + gap + r
    y0 = top + pad - pitch // 2 + r
    for ch in text:
        for row, bits in enumerate(G[ch]):
            for c, b in enumerate(bits):
                if b == "1":
                    x, y = x0 + c * pitch, y0 + row * pitch
                    d.ellipse((x - r, y - r, x + r, y + r), fill=RED)
        x0 += 6 * pitch
    return top + box_h

bottom = title("TESTERS NEEDED", 110, 22, 8)
# A red dot, as in the face's eye: the one that is "on".
d = ImageDraw.Draw(img)
def line(text, y, size, colour):
    f = ImageFont.truetype(FONT, size)
    w = d.textlength(text, font=f)
    d.text(((W - w) / 2, y), text, font=f, fill=colour)
y = bottom + 90
line("HAVE A NOTHING OR CMF PHONE ON NOTHING OS 4.0, 4.1 OR 5.0?", y, 46, (225, 225, 225)); y += 95
for t in ["TRY NOTHING2SOMETHING AND TELL US WHAT WORKS AND WHAT DOES NOT",
          "WHAT TO EXPECT ON YOUR PHONE: THE TESTING SECTION BELOW",
          "SEND A SCREENSHOT OF DIAGNOSTICS > HOOK HEALTH  •  LSPOSED LOGS ONLY IN PRIVATE"]:
    line(t, y, 34, (150, 150, 150)); y += 62
line("EVERY TESTER WILL BE CREDITED  •  THANK YOU", y + 30, 32, (60, 220, 110))
img.save(sys.argv[1])
