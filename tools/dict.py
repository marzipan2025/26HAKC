"""
libhangul 한자 사전을 앱이 읽을 SQLite 로 굽는다.

원본은 `한글:한자:뜻` 한 줄씩인 6.4MB 글이다. 매번 훑을 수 없으니 한글을 열쇠로
索引을 걸어 둔다. 한 글자짜리 항목은 그 글자의 訓音을 담고 있으므로 따로 모아
급수와 함께 둔다 — 낱말을 찾은 뒤 글자마다 訓音을 붙이는 데 쓴다.
"""
import pathlib
import sqlite3
import sys
import unicodedata

SRC = pathlib.Path(
    "/Users/agni/MyGit/01HAKA/claude_kanji/HanjaWidget/HanjaWidget")


def grades():
    """{한자: 급수}. 0 은 특급이다."""
    out = {}
    text = (SRC / "hanja_grades.txt").read_text(encoding="utf-8")
    for line in text.splitlines():
        head, _, body = line.partition(":")
        if not head.strip().isdigit():
            continue
        for ch in body:
            out[ch] = int(head)
    return out


def main(dst):
    dst = pathlib.Path(dst)
    dst.unlink(missing_ok=True)
    con = sqlite3.connect(dst)
    con.executescript("""
        CREATE TABLE words(ko TEXT NOT NULL, hanja TEXT NOT NULL, meaning TEXT);
        CREATE TABLE chars(han TEXT PRIMARY KEY, hun TEXT, eum TEXT, grade INTEGER);
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);
    """)
    g = grades()
    words, chars = [], {}
    for raw in (SRC / "hanja.txt").read_text(encoding="utf-8").splitlines():
        if raw.startswith("#") or not raw.strip():
            continue
        part = raw.split(":")
        if len(part) < 2:
            continue
        ko = unicodedata.normalize("NFC", part[0]).strip()
        han = unicodedata.normalize("NFC", part[1]).strip()
        mean = unicodedata.normalize("NFC", part[2]).strip() if len(part) > 2 else ""
        if not ko or not han:
            continue
        words.append((ko, han, mean or None))
        # '만:萬:일만 만' 처럼 한 글자짜리는 그 글자의 訓音이다
        if len(han) == 1 and mean and " " in mean:
            hun, _, eum = mean.rpartition(" ")
            chars.setdefault(han, (hun.strip(), eum.strip()))
    con.executemany("INSERT INTO words(ko, hanja, meaning) VALUES(?,?,?)", words)
    con.executemany(
        "INSERT INTO chars(han, hun, eum, grade) VALUES(?,?,?,?)",
        [(h, v[0], v[1], g.get(h)) for h, v in chars.items()])
    con.executemany("INSERT INTO meta(key, value) VALUES(?,?)", [
        ("source", "libhangul hanja.txt"),
        ("words", str(len(words))),
        ("chars", str(len(chars))),
        ("graded", str(sum(1 for h in chars if h in g))),
    ])
    con.execute("CREATE INDEX idx_words_ko ON words(ko)")
    con.commit()
    con.execute("VACUUM")
    con.close()
    print(f"낱말 {len(words):,}개 · 글자 {len(chars):,}개 · 급수 붙은 글자 "
          f"{sum(1 for h in chars if h in g):,}개")
    print(f"{dst} — {dst.stat().st_size/1024/1024:.1f}MB")


if __name__ == "__main__":
    main(sys.argv[1])
