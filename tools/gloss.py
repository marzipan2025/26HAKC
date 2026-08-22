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

LIMIT = 400            # 길이로는 거르지 않는다. 카드에서 접어 보인다

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
    """
    [(한자, 뜻)] — 문서 하나에서 뽑는다.

    위키의 어원 줄은 뜻 앞에 올 때도 있고 뒤에 올 때도 있다.

        *어원: 한자 [[變死]]              # 한 나라의 입법, 사법 …
        # 뜻밖의 사고로 죽음.             *어원: 한자 [[政府]].
        (어원이 먼저)                     (뜻이 먼저)

    한 문서 안에서는 그 차례가 한결같으므로, 한국어 마당에서 어느 쪽이 먼저
    나오는지로 문서의 차례를 정하고 그대로만 짝짓는다. 가까운 것을 집는 식으로
    하면 '정부' 문서처럼 둘이 번갈아 나오는 데서 한 칸씩 밀린다.

    비어 있는 `#` 줄도 뜻으로 센다. 그 자리는 '뜻이 없는 뜻'이라 짝을 메워 주는
    구실을 한다 — 세지 않으면 그다음 뜻이 앞 한자에게 딸려 간다.
    """
    text = re.sub(r"<text[^>]*>", "", text).replace("</text>", "")
    korean, events = False, []
    for raw in text.splitlines():
        line = raw.strip()
        if line.startswith("==") and line.endswith("==") and len(line) > 4:
            name = line.strip("= ")
            if name == "한국어":
                korean = True
            elif not (name.startswith("명사") or name.startswith("어원")
                      or name.startswith("표제") or name[0].isdigit()):
                if korean and name not in ("파생어", "합성어", "관련 어휘", "번역"):
                    korean = False
            continue
        if not korean:
            continue
        m = WON_TPL.search(line) or WON_OLD.search(line)
        if m and HANJA.match(m.group(1)):
            events.append(("한자", m.group(1)))
            continue
        if line.startswith("#") and not line.startswith("#*") and not line.startswith("#:"):
            body = clean(line.lstrip("#").strip())
            events.append(("뜻", body if len(body) <= LIMIT else ""))

    if not events:
        return []
    # 문서의 차례 — 먼저 나온 쪽이 앞이다
    won_first = events[0][0] == "한자"
    out = []
    for i, (kind, val) in enumerate(events):
        if kind != "한자":
            continue
        j = i + 1 if won_first else i - 1
        if 0 <= j < len(events) and events[j][0] == "뜻" and events[j][1]:
            out.append((val, events[j][1]))
    return out


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
                        if han in words[title]:
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
