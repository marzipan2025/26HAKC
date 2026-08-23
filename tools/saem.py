"""
우리말샘에서 뜻풀이를 뽑아 사전에 붙인다.

위키낱말사전(gloss.py)이 채우고 남긴 자리를 메우는 두 번째 우물이다. 국립국어원이
CC BY-SA 2.0 KR 로 푼 자료이고, 항목마다 원어(한자)가 적혀 있어 짝을 정확히 지을
수 있다. 로그인해서 내려받아야 하지만 spellcheck-ko/korean-dict-nikl 이 그대로
담아 두고 있어 그쪽에서 받는다.

    <wordInfo>
      <word>간행</word>
      <original_language_info>
        <original_language>刊行</original_language>
        <language_type>한자</language_type>

한글과 한자가 둘 다 맞을 때만 붙인다 — gloss.py 와 같은 규칙이다. 표기가 하나뿐이라고
넘겨짚으면 '나무' 의 뜻이 南無 에 붙는 식으로 어긋난다.

낱말은 한 글자·두 글자·네 글자만 본다. 사전 판에 서는 것이 그것들이고, 나머지까지
받으면 앱에 실리는 파일만 두 배로 부푼다.

    python3 tools/saem.py <xml이 든 폴더> app/src/main/assets/dict.db
"""
import pathlib
import re
import sqlite3
import sys
import unicodedata

# 사전 판에 세우는 낱말의 길이
LENGTHS = (1, 2, 4)

# 한 항목의 뜻은 이만큼까지. 카드가 아니라 판에 한 줄로 앉는 글이라 짧아야 한다.
LIMIT = 120

ITEM = re.compile(r"<item>(.*?)</item>", re.S)
WORDINFO = re.compile(r"<wordInfo>(.*?)</wordInfo>", re.S)
LANG = re.compile(
    r"<original_language><!\[CDATA\[(.*?)\]\]></original_language>\s*"
    r"<language_type>(.*?)</language_type>",
    re.S,
)
HANJA = re.compile(r"^[㐀-鿿豈-﫿]+$")


def cdata(tag, s):
    m = re.search(rf"<{tag}><!\[CDATA\[(.*?)\]\]></{tag}>", s, re.S)
    return m.group(1) if m else None


def nfc(s):
    return unicodedata.normalize("NFC", s)


OPEN = "([{（≪〈《"
SHUT = ")]}）≫〉》"


def whole(m):
    """문장으로 온전한가 — 마침표로 끝나고, 열어 둔 괄호나 따옴표가 없어야 한다."""
    if not m.endswith("."):
        return False
    depth = 0
    for ch in m:
        if ch in OPEN:
            depth += 1
        elif ch in SHUT:
            depth -= 1
        if depth < 0:
            return False
    if depth:
        return False
    return m.count("‘") == m.count("’") and m.count("“") == m.count("”")


def trim(d):
    """
    판에 한 줄로 앉을 만큼만. 문장 경계까지 물러나고, 그 안에 문장이 하나도
    없으면 아예 버린다 — 말이 중간에서 끊긴 풀이는 없느니만 못하다.
    """
    d = re.sub(r"\s+", " ", d).strip()
    if len(d) > LIMIT:
        cut = d[:LIMIT]
        dot = cut.rfind(". ")
        d = cut[: dot + 1].strip() if dot > 0 else cut.strip()
    return d if whole(d) else None


def read(path):
    """(한글, 한자) → 뜻. 한 파일에서 나오는 것만."""
    out = {}
    src = path.read_text(encoding="utf-8", errors="replace")
    for item in ITEM.findall(src):
        wi = WORDINFO.search(item)
        if not wi:
            continue
        ko = cdata("word", wi.group(1))
        d = cdata("definition", item)
        if not ko or not d:
            continue
        # 표제어에 붙는 붙임표와 동형어 번호를 걷는다 (교수-001 → 교수)
        ko = nfc(re.sub(r"\d+$", "", ko.replace("-", "").replace("^", "")))
        if len(ko) not in LENGTHS:
            continue
        for han, kind in LANG.findall(wi.group(1)):
            han = nfc(han)
            if kind == "한자" and HANJA.match(han):
                body = trim(d)
                if body:
                    out.setdefault((ko, han), body)
    return out


def main(folder, db):
    con = sqlite3.connect(db)
    # 비어 있는 자리만 채운다. 이미 붙은 뜻은 위키에서 온 것이라 그대로 둔다.
    empty = {
        (ko, han)
        for ko, han in con.execute(
            "SELECT ko, hanja FROM words WHERE meaning IS NULL OR meaning = ''"
        )
    }
    print(f"뜻이 빈 낱말 {len(empty):,}개")

    got = {}
    files = sorted(pathlib.Path(folder).glob("*.xml"))
    for i, f in enumerate(files, 1):
        found = read(f)
        fresh = {k: v for k, v in found.items() if k in empty and k not in got}
        got.update(fresh)
        print(f"  [{i}/{len(files)}] {f.name}  한자 짝 {len(found):,} → 새로 {len(fresh):,}")

    print(f"\n채울 것 {len(got):,}개")
    for n in LENGTHS:
        print(f"  {n}자 {sum(1 for ko, _ in got if len(ko) == n):,}")

    con.executemany(
        "UPDATE words SET meaning = ? WHERE ko = ? AND hanja = ? "
        "AND (meaning IS NULL OR meaning = '')",
        [(d, ko, han) for (ko, han), d in got.items()],
    )
    con.execute(
        "INSERT OR REPLACE INTO meta(key, value) VALUES('gloss2', ?)",
        (f"우리말샘 {len(got)}",),
    )
    con.commit()
    total = con.execute(
        "SELECT count(*) FROM words WHERE meaning IS NOT NULL AND meaning <> ''"
    ).fetchone()[0]
    print(f"\n이제 뜻이 있는 낱말 {total:,}개")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
