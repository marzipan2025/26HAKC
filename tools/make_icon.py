#!/usr/bin/env python3
"""~/Downloads/AppIcon_26hakc2.png 을 안드로이드 런처 아이콘으로 굽는다.

원본은 먹빛(#0C0E11) 바탕 한가운데에 판이 놓인 1024px 정사각이고, **런처가 도려내는
원(72/108)에 맞춰 그려져 있다**. 그러니 여기서 다시 줄이지 않고 108dp 층에 통째로 얹는다.
구형 런처용 정사각/원형 아이콘도 원본 그대로다.

적응형 아이콘의 앞층은 원본에서 바탕색만 투명하게 걷어 낸다. 뒷층이 같은 먹빛이라
색 아이콘은 원본과 똑같이 보이고, 앞층을 함께 쓰는 단색 아이콘(Nothing 런처 등)은
판과 글자만 실루엣으로 잡는다 — 바탕째 넣으면 판 전체가 검은 원으로 뭉개진다.

numpy 없이 PIL 만 쓴다: /usr/bin/python3 tools/make_icon.py
"""
import os
from PIL import Image

SRC = os.path.expanduser('~/Downloads/AppIcon_26hakc2.png')
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res')
BG = (12, 14, 17)        # 원본의 바탕 = @color/ic_launcher_background
PLATE = (102, 135, 182)  # 판의 색(글자와 같은 색) — 바탕과 이만큼 떨어지면 온전히 불투명

# mdpi 기준 배수 — 런처 아이콘 48dp, 적응형 레이어 108dp
DENSITY = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}


def key_out(src):
    """바탕색을 투명하게. 가장자리의 섞인 픽셀은 알파를 나누어 제 색을 되살린다."""
    full = max(abs(p - b) for p, b in zip(PLATE, BG))
    out = []
    for c in src.getdata():
        a = min(1.0, max(abs(x - b) for x, b in zip(c, BG)) / full)
        if a == 0:
            out.append((0, 0, 0, 0))
            continue
        rgb = tuple(max(0, min(255, round(b + (x - b) / a))) for x, b in zip(c, BG))
        out.append(rgb + (round(a * 255),))
    img = Image.new('RGBA', src.size)
    img.putdata(out)
    return img


def main():
    src = Image.open(SRC).convert('RGB')
    fg = key_out(src)
    for name, k in DENSITY.items():
        out = os.path.join(RES, 'mipmap-' + name)
        os.makedirs(out, exist_ok=True)

        legacy = int(48 * k)
        icon = src.resize((legacy, legacy), Image.LANCZOS)
        icon.save(os.path.join(out, 'ic_launcher.png'))
        icon.save(os.path.join(out, 'ic_launcher_round.png'))

        layer = int(108 * k)
        fg.resize((layer, layer), Image.LANCZOS).save(
            os.path.join(out, 'ic_launcher_foreground.png'))
        Image.new('RGBA', (layer, layer), BG + (255,)).save(
            os.path.join(out, 'ic_launcher_background.png'))
        print(name, legacy, layer)


if __name__ == '__main__':
    main()
