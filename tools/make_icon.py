#!/usr/bin/env python3
"""~/Downloads/HAKC_asset/AppIcon_26HAKC.png 을 안드로이드 런처 아이콘으로 굽는다.

원본은 검은 바탕에 그림이 놓인 1024px 정사각이고, **런처가 도려내는 원(72/108)에
맞춰 그려져 있다** — 그림의 대각 반지름이 판의 33.6% 다. 그러니 여기서 다시 줄이지
않는다. 원본을 108dp 층에 통째로 얹으면 런처가 제 모양대로 도려내도 그림이 온전하다.
구형 런처용 정사각/원형 아이콘도 원본 그대로다.

한때 그림 자리를 찾아 잘라 내고 안전 영역에 맞추던 적이 있는데, 그건 원본이 원을
생각하지 않고 그려졌을 때의 이야기다. 원본이 이미 원에 맞으면 손대지 않는 편이 낫다.
"""
import os
from PIL import Image

SRC = os.path.expanduser('~/Downloads/HAKC_asset/AppIcon_26HAKC.png')
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res')
GROUND = (0, 0, 0, 255)         # 원본의 바탕색 — 앱의 검은 바탕과 이어진다

# mdpi 기준 배수 — 런처 아이콘 48dp, 적응형 레이어 108dp
DENSITY = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
# 첫 화면에 세울 그림. 288dp 판에 240dp 원으로 도려내진다.
SPLASH = 864             # 288dp @3x


def main():
    src = Image.open(SRC).convert('RGBA')
    for name, k in DENSITY.items():
        out = os.path.join(RES, 'mipmap-' + name)
        os.makedirs(out, exist_ok=True)

        legacy = int(48 * k)
        src.resize((legacy, legacy), Image.LANCZOS).save(os.path.join(out, 'ic_launcher.png'))
        src.resize((legacy, legacy), Image.LANCZOS).save(os.path.join(out, 'ic_launcher_round.png'))

        # 원본이 이미 원에 맞으니 108dp 층에 통째로 얹는다
        layer = int(108 * k)
        src.resize((layer, layer), Image.LANCZOS).save(
            os.path.join(out, 'ic_launcher_foreground.png'))

        Image.new('RGBA', (layer, layer), GROUND).save(
            os.path.join(out, 'ic_launcher_background.png'))
        print(name, legacy, layer)


def splash():
    """첫 화면에 세울 사각 아이콘. 원본 그대로 줄이기만 한다."""
    out = os.path.join(RES, 'drawable-xxhdpi')
    os.makedirs(out, exist_ok=True)
    Image.open(SRC).convert('RGBA').resize((SPLASH, SPLASH), Image.LANCZOS).save(
        os.path.join(out, 'splash_icon.png'))
    print('splash', SPLASH)


if __name__ == '__main__':
    main()
    splash()
