"""
위키낱말사전에서 뜻풀이를 뽑아 사전에 붙인다.

우리가 가진 낱말(한글+한자)로만 훑으므로 낯선 표제어는 애초에 보지 않는다.
위키의 한국어 문서는 두 가지 꼴로 한자를 적어 둔다.

    === 명사 2 ===
    {{어원|移徙|이사|…}}
    # 사는 곳을 다른 데로 옮김.          ← 어원이 앞에 오는 요즘 꼴

    # (철학) 어떠한 척도와 …
    *어원: 한자 [[理性]].                 ← 어원이 뒤에 오는 옛 꼴

둘 다 받아 (한글, 한자) 짝에 뜻을 맞춘다. 짝을 못 지으면 그 낱말에 한자 표기가
하나뿐일 때만 붙인다 — 여럿인데 어느 것인지 모르면 안 붙이는 편이 낫다.

카드에 한 줄로 앉아야 하므로 40자가 넘는 풀이는 버린다.
"""
import bz2
import pathlib
import re
import sqlite3
import sys
import unicodedata

LIMIT = 40

TITLE = re.compile(r"<title>(.*?)</title>")
WON_TPL = re.compile(r"\{\{어원\|([^|}]+)\|")
WON_OLD = re.compile(r"\*\s*어원\s*[:：].*?\[\[([㐀-鿿豈-﫿]+)\]\]")
HEAD = re.compile(r"^=+\s*[^=]+=+\s*$")
HANJA = re.compile(r"^[㐀-鿿豈-﫿]+$")


def clean(s):
    """위키 표시를 걷어내고 사람이 읽는 글만 남긴다."""
    s = re.sub(r"\{\{라벨\|[^}]*\}\}", "", s)
    s = re.sub(r"\{\{[^}]*\}\}", "", s)
    s = re.sub(r"\[\[([^\]|]*)\|([^\]]*)\]\]", r"\2", s)
    s = re.sub(r"\[\[([^\]]*)\]\]", r"\1", s)
    s = re.sub(r"'{2,}", "", s)
    s = re.sub(r"<[^>]+>", "", s)
    s = re.sub(r"\s+", " ", s)
    return s.strip(" .·-—")


def senses(text):
    """[(한자 or None, 뜻)] — 문서 하나에서 뽑는다."""
    # 덤프는 <text …>== 한국어 == 처럼 첫 줄에 태그가 붙어 나온다.
    # 그대로 두면 첫 마당 표시를 통째로 놓친다.
    text = re.sub(r"<text[^>]*>", "", text).replace("</text>", "")
    out, pending, cur, korean = [], [], None, False
    for raw in text.splitlines():
        line = raw.strip()
        if line.startswith("==") and "==" in line[2:]:
            name = line.strip("= ")
            if name in ("한국어",):
                korean = True
            elif not name.startswith("명사") and not name.startswith("어원") \
                    and not name.startswith("표제") and len(name) <= 12 \
                    and not name[0].isdigit():
                # 다른 언어 마당으로 넘어갔다
                if korean and name not in ("파생어", "합성어", "관련 어휘", "번역"):
                    korean = False
            cur = None
            continue
        if not korean:
            continue
        m = WON_TPL.search(line)
        if m and HANJA.match(m.group(1)):
            cur = m.group(1)
            continue
        m = WON_OLD.search(line)
        if m:
            # 옛 꼴은 어원이 뜻 뒤에 온다. 바로 앞의 뜻 하나만 가져간다 —
            # 앞엣것까지 싸잡으면 다른 한자의 뜻이 딸려 붙는다.
            if pending:
                out.append((m.group(1), pending[-1]))
            pending = []
            cur = None
            continue
        if line.startswith("#") and not line.startswith("#*") and not line.startswith("#:"):
            body = clean(line.lstrip("#").strip())
            if not body or len(body) > LIMIT:
                continue
            if cur:
                out.append((cur, body))
            else:
                pending.append(body)
    return out + [(None, p) for p in pending]


def main(dump, db):
    con = sqlite3.connect(db)
    # 우리가 가진 낱말만 본다
    words, count = {}, {}
    for ko, han in con.execute("SELECT ko, hanja FROM words"):
        words.setdefault(ko, set()).add(han)
        count[ko] = count.get(ko, 0) + 1

    got, title, buf, inside = {}, None, [], False
    seen = 0
    with bz2.open(dump, "rt", encoding="utf-8") as f:
        for line in f:
            m = TITLE.search(line)
            if m:
                title = unicodedata.normalize("NFC", m.group(1))
            if "<text" in line:
                inside = title in words
                buf = []
            if inside:
                buf.append(line)
                if "</text>" in line:
                    inside = False
                    seen += 1
                    # 한자를 짚어 준 뜻만 받는다. 표기가 하나뿐이라고 넘겨짚으면
                    # '나무' 문서의 뜻이 南無 에 붙는 식으로 어긋난다 — 그렇게 딸려
                    # 오는 3,500 개는 대개 고유어 문서였다. 적더라도 맞는 것만 쓴다.
                    for han, body in senses("".join(buf)):
                        if han and han in words[title]:
                            got.setdefault((title, han), body)

    print(f"우리 낱말과 겹치는 위키 문서 {seen:,}개 → 뜻 {len(got):,}개")

    n = 0
    for (ko, han), body in got.items():
        cur = con.execute(
            "SELECT meaning FROM words WHERE ko=? AND hanja=?", (ko, han)).fetchone()
        if cur and not (cur[0] or "").strip():
            con.execute("UPDATE words SET meaning=? WHERE ko=? AND hanja=?", (body, ko, han))
            n += 1
    con.execute("UPDATE meta SET value=? WHERE key='words'",
                (str(con.execute("SELECT count(*) FROM words").fetchone()[0]),))
    con.commit()
    total, with_mean = con.execute(
        "SELECT count(*), sum(meaning IS NOT NULL AND meaning<>'') FROM words").fetchone()
    print(f"새로 붙인 뜻 {n:,}개 → 이제 {with_mean:,} / {total:,}")
    con.close()


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
