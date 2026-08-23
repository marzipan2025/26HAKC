"""
카드가 노랑·초록으로 넘어가는 순간의 딸깍 소리를 만든다.

녹음을 구해다 쓰는 대신 합성한다. 14ms 짜리라 파일이 1KB 남짓이고, 무엇으로
이루어졌는지가 코드에 그대로 남는다.

    python3 tools/click.py app/src/main/res/raw/click.wav
"""

import math
import random
import struct
import sys

RATE = 44100
DUR = 0.014          # 14ms — 귀에 '점' 하나로 들리는 길이
PEAK = 0.6


def click():
    n = int(RATE * DUR)
    out = []
    random.seed(26)
    for i in range(n):
        t = i / RATE
        # 두 겹의 사인이 서로 다른 빠르기로 잦아든다. 낮은 쪽이 몸통, 높은 쪽이 끝.
        body = math.sin(2 * math.pi * 1750 * t) * math.exp(-t / 0.0022)
        edge = math.sin(2 * math.pi * 3100 * t) * math.exp(-t / 0.0011) * 0.5
        # 맨 앞의 아주 짧은 잡음이 '딱' 하는 낌새를 만든다
        tick = (random.random() * 2 - 1) * math.exp(-t / 0.0005) * 0.25
        v = body + edge + tick
        # 0.3ms 동안 살며시 열어 준다 — 곧바로 시작하면 툭 하고 튄다
        if t < 0.0003:
            v *= (1 - math.cos(math.pi * t / 0.0003)) / 2
        out.append(v)
    top = max(abs(v) for v in out)
    return [v / top * PEAK for v in out]


def wav(path, samples):
    body = b"".join(struct.pack("<h", int(max(-1, min(1, v)) * 32767)) for v in samples)
    head = (
        b"RIFF" + struct.pack("<I", 36 + len(body)) + b"WAVEfmt "
        + struct.pack("<IHHIIHH", 16, 1, 1, RATE, RATE * 2, 2, 16)
        + b"data" + struct.pack("<I", len(body))
    )
    with open(path, "wb") as f:
        f.write(head + body)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/raw/click.wav"
    s = click()
    wav(out, s)
    print(f"{out} — {len(s)}표본 {len(s)/RATE*1000:.0f}ms")
