#!/usr/bin/env python3
"""~/Downloads/HAKC_asset/AppIcon_26HAKC.png 을 안드로이드 런처 아이콘으로 굽는다.

원본은 짙은 바탕(#363D47)에 옅은 그림이 놓인 1024px 정사각이고, 그림 둘레에
이미 18% 여백이 있다. 적응형 아이콘은 108dp 중 안쪽 72dp만 보이므로 원본을
통째로 줄여 넣으면 여백이 두 겹이 되어 그림만 작아진다. 그래서 **그림이 실제로
놓인 자리만 잘라 내어** 안전 영역에 맞춘다. 바탕은 원본의 바탕색으로 깔고,
구형 런처용 정사각/원형 아이콘은 원본 그대로 쓴다.
"""
import os
from PIL import Image

SRC = os.path.expanduser('~/Downloads/HAKC_asset/AppIcon_26HAKC.png')
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res')
GROUND = (166, 182, 192, 255)   # 원본의 바탕색 #A6B6C0

# mdpi 기준 배수 — 런처 아이콘 48dp, 적응형 레이어 108dp
DENSITY = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
SAFE = 72 / 108          # 적응형 아이콘에서 실제로 보이는 비율
FILL = 0.88              # 안전 영역을 이만큼 채운다


def ink(im):
    """그림이 실제로 놓인 네모. 바탕색과 다른 점만 센다."""
    bg = im.getpixel((5, 5))[:3]
    px = im.load()
    w, h = im.size
    x0, y0, x1, y1 = w, h, 0, 0
    for y in range(h):
        for x in range(w):
            if sum(abs(a - b) for a, b in zip(px[x, y][:3], bg)) > 30:
                x0, y0, x1, y1 = min(x0, x), min(y0, y), max(x1, x), max(y1, y)
    # 네모난 자리로 맞춘다 — 가로세로 비를 지켜야 그림이 눌리지 않는다
    side = max(x1 - x0, y1 - y0)
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    return im.crop((cx - side // 2, cy - side // 2, cx + side // 2, cy + side // 2))


def main():
    src = Image.open(SRC).convert('RGBA')
    art_src = ink(src)
    for name, k in DENSITY.items():
        out = os.path.join(RES, 'mipmap-' + name)
        os.makedirs(out, exist_ok=True)

        legacy = int(48 * k)
        src.resize((legacy, legacy), Image.LANCZOS).save(os.path.join(out, 'ic_launcher.png'))
        src.resize((legacy, legacy), Image.LANCZOS).save(os.path.join(out, 'ic_launcher_round.png'))

        layer = int(108 * k)
        art = int(layer * SAFE * FILL)
        fg = Image.new('RGBA', (layer, layer), (0, 0, 0, 0))
        fg.paste(art_src.resize((art, art), Image.LANCZOS), ((layer - art) // 2,) * 2)
        fg.save(os.path.join(out, 'ic_launcher_foreground.png'))

        Image.new('RGBA', (layer, layer), GROUND).save(
            os.path.join(out, 'ic_launcher_background.png'))
        print(name, legacy, layer, art)


if __name__ == '__main__':
    main()
