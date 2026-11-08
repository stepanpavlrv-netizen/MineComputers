from PIL import Image
import math, random

CLEAR = (0, 0, 0, 0)

def hx(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)

class Canvas:
    def __init__(self, w=16, h=16):
        self.im = Image.new("RGBA", (w, h), CLEAR)
        self.p = self.im.load()
        self.w, self.h = w, h

    def px(self, x, y, c, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[x, y] = hx(c, a) if isinstance(c, str) else c

    def rect(self, x0, y0, x1, y1, c, a=255):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                self.px(x, y, c, a)

    def clear(self, x0, y0, x1=None, y1=None):
        x1 = x0 if x1 is None else x1
        y1 = y0 if y1 is None else y1
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                if 0 <= x < self.w and 0 <= y < self.h:
                    self.p[x, y] = CLEAR

    def hline(self, x0, x1, y, c):
        self.rect(x0, y, x1, y, c)

    def vline(self, x, y0, y1, c):
        self.rect(x, y0, x, y1, c)

    def bevel(self, x0, y0, x1, y1, base, hi, lo):
        """Прямоугольник с бликом сверху-слева и тенью снизу-справа."""
        self.rect(x0, y0, x1, y1, base)
        self.hline(x0, x1, y0, hi)
        self.vline(x0, y0, y1, hi)
        self.hline(x0 + 1, x1, y1, lo)
        self.vline(x1, y0 + 1, y1, lo)

    def get(self, x, y):
        return self.p[x, y]

    def save(self, path):
        self.im.save(path)


def fan(c, cx, cy, r, frame="2a2b30", blade="55575f", hub="7a7c84", dark="1b1c20"):
    """Вентилятор вид спереди: тёмный круг, лопасти-вертушка, ступица."""
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d <= r:
                ang = math.atan2(dy, dx)
                # 7 лопастей, закрученных по спирали
                k = (ang * 7 / (2 * math.pi) + d * 0.35) % 1.0
                col = blade if k < 0.45 else dark
                if d > r - 0.8:
                    col = frame
                c.px(x, y, col)
    hr = max(0.9, r * 0.32)
    for x in range(int(cx - hr - 1), int(cx + hr + 2)):
        for y in range(int(cy - hr - 1), int(cy + hr + 2)):
            if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= hr:
                c.px(x, y, hub)


def noise(c, x0, y0, x1, y1, base, amount=6, seed=1):
    rnd = random.Random(seed)
    b = hx(base)
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            d = rnd.randint(-amount, amount)
            c.px(x, y, (max(0, min(255, b[0] + d)), max(0, min(255, b[1] + d)),
                        max(0, min(255, b[2] + d)), 255))
