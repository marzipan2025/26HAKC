"""
일본어 읽기 캡슐의 히라가나 서체를 만든다 — Noto Sans JP 가변 폰트를 Light(300)로
굳히고 가나만 남긴다.

폰에 든 CJK 서체(NotoSansCJK-Regular)는 레귤러 한 굵기뿐이라, 앱에서 Light 를
달라고 해도 레귤러로 그려진다. 가나 몇백 자만 담으면 수십 KB 라 앱 안에 넣는다.

    pip install fonttools
    python3 tools/make_kana_font.py NotoSansJP[wght].ttf app/src/main/res/font/noto_sans_jp_kana_light.ttf
"""
import sys

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

WEIGHT = 300

# 히라가나·가타카나 전부, 그리고 읽기에 섞일 수 있는 장음·가운뎃점·구두점
KANA = [*range(0x3040, 0x30A0), *range(0x30A0, 0x3100), 0x3001, 0x3002, 0x30FB, 0x30FC]


def main(src, dst):
    font = instancer.instantiateVariableFont(TTFont(src), {"wght": WEIGHT})
    opts = subset.Options()
    opts.layout_features = ["kern"]
    opts.name_IDs = ["*"]
    s = subset.Subsetter(opts)
    s.populate(unicodes=KANA)
    s.subset(font)
    font.save(dst)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
