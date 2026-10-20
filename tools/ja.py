"""
사전에 일본어 읽기(히라가나)를 한 표 더 굽는다 — 訓音 곁의 재생 단추가 읽는 것이다.

    python3 tools/ja.py app/src/main/assets/dict.db jmdict-eng.json kanjidic2-en.json \
        JPShinjitaiCharacters.txt

자료는 셋이다. 낱말의 읽기는 JMdict, 낱말에 없는 한 글자의 음독은 KANJIDIC2
(둘 다 jmdict-simplified 의 JSON 판), 우리 한자(舊字)를 일본 신자체로 옮기는 표는
OpenCC 의 JPShinjitaiCharacters.txt 다. 學校 는 JMdict 에 学校 로 서 있으므로
그대로 한 번, 신자체로 옮겨 한 번 더 찾는다.

한 표기에 읽기가 여럿이면 흔한 쪽(common)을 먼저, 그다음은 JMdict 에 적힌 차례다
— 今日 은 きょう 가 こんにち 보다 앞선다. 일본에 없는 우리 한자말은 표에 들지
않고, 앱은 그 낱말에 단추를 세우지 않는다.

    ja(hanja TEXT PRIMARY KEY, kana TEXT)
"""
import json
import sqlite3
import sys


def is_han(c):
    o = ord(c)
    return 0x3400 <= o <= 0x4DBF or 0x4E00 <= o <= 0x9FFF or 0xF900 <= o <= 0xFAFF


def shinjitai(path):
    """{舊字: 신자체}. 파일은 `신자체<TAB>舊字 舊字…` 꼴이다."""
    out = {}
    for line in open(path, encoding="utf-8"):
        if line.startswith("#") or "\t" not in line:
            continue
        new, olds = line.rstrip("\n").split("\t", 1)
        for old in olds.split():
            out.setdefault(old, new)
    return out


def katakana_to_hiragana(s):
    return "".join(chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else c for c in s)


def words(path):
    """{표기: 히라가나}. 흔한 표기·흔한 읽기가 이긴다."""
    best = {}  # text -> (score, order, kana)
    order = 0
    for e in json.load(open(path, encoding="utf-8"))["words"]:
        for k in e["kanji"]:
            text = k["text"]
            for r in e["kana"]:
                applies = r.get("appliesToKanji", ["*"])
                if "*" not in applies and text not in applies:
                    continue
                # 찾기에만 쓰는 꼴(sk), 낡은 꼴(ok), 잘 안 쓰는 꼴(rk)은 읽지 않는다
                if {"sk", "ok", "rk"} & set(r.get("tags", [])):
                    continue
                score = (k["common"] and r["common"], k["common"] or r["common"])
                order += 1
                old = best.get(text)
                if old is None or score > old[0]:
                    best[text] = (score, order, r["text"])
    return {t: v[2] for t, v in best.items()}


def onyomi(path):
    """{글자: 첫 음독(히라가나)}."""
    out = {}
    for c in json.load(open(path, encoding="utf-8"))["characters"]:
        rm = c.get("readingMeaning") or {}
        for g in rm.get("groups", []):
            ons = [r["value"] for r in g.get("readings", []) if r["type"] == "ja_on"]
            if ons:
                out[c["literal"]] = katakana_to_hiragana(ons[0])
                break
    return out


def main(db_path, jmdict, kanjidic, shin):
    to_new = shinjitai(shin)
    jm = words(jmdict)
    on = onyomi(kanjidic)
    db = sqlite3.connect(db_path)
    forms = {r[0] for r in db.execute("SELECT DISTINCT hanja FROM words")}
    forms |= {r[0] for r in db.execute("SELECT han FROM chars")}
    rows = []
    for h in forms:
        if not h or not all(is_han(c) for c in h):
            continue
        new = "".join(to_new.get(c, c) for c in h)
        kana = jm.get(h) or jm.get(new)
        if kana is None and len(h) == 1:
            kana = on.get(h) or on.get(new)
        if kana:
            rows.append((h, kana))
    db.execute("DROP TABLE IF EXISTS ja")
    db.execute("CREATE TABLE ja(hanja TEXT PRIMARY KEY, kana TEXT NOT NULL)")
    db.executemany("INSERT INTO ja VALUES(?, ?)", rows)
    db.commit()
    db.execute("VACUUM")
    db.close()
    print(f"{len(rows)} / {len(forms)} 표기에 읽기")


if __name__ == "__main__":
    main(*sys.argv[1:5])
