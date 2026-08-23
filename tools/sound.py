"""
카드를 넘길 때 나는 소리 둘을 만든다.

    click  카드가 한 칸 넘어갔다 — 밝고 짧은 딱
    nope   축의 끝이라 더 갈 데가 없다 — 낮고 둔한 덕

녹음을 구해다 쓰는 대신 합성한다. 둘 다 1KB 남짓이고, 무엇으로 이루어졌는지가
코드에 그대로 남아 높이든 길이든 숫자 하나로 고쳐진다.

    python3 tools/sound.py app/src/main/res/raw
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


def nope():
    """
    아니라는 소리.

    처음에는 딱을 그냥 낮춰(220·158Hz) 두었는데 폰에서 들리지 않았다. 폰 스피커는
    500Hz 아래를 거의 내지 못한다. 그래서 낮추는 대신 **떨어뜨린다** — 높은 소리
    하나 뒤에 낮은 소리 하나. 두 소리 다 스피커가 낼 수 있는 자리에 있으면서,
    내려가는 결이 '아니' 로 들린다.
    """
    n = int(RATE * 0.055)
    out = []
    for i in range(n):
        t = i / RATE
        # 앞의 한 톨은 짧고 높게, 뒤의 한 톨은 조금 낮고 길게
        first = math.sin(2 * math.pi * 760 * t) * math.exp(-t / 0.0055)
        at = t - 0.020
        second = 0.0
        if at >= 0:
            second = math.sin(2 * math.pi * 520 * at) * math.exp(-at / 0.0110) * 1.1
            if at < 0.0008:                 # 뒤엣것도 살며시 연다
                second *= (1 - math.cos(math.pi * at / 0.0008)) / 2
        v = first + second
        if t < 0.0008:
            v *= (1 - math.cos(math.pi * t / 0.0008)) / 2
        out.append(v)
    top = max(abs(v) for v in out)
    return [v / top * PEAK for v in out]


if __name__ == "__main__":
    where = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/raw"
    for name, make in (("click", click), ("nope", nope)):
        s = make()
        wav(f"{where}/{name}.wav", s)
        print(f"{where}/{name}.wav — {len(s)}표본 {len(s)/RATE*1000:.0f}ms")
