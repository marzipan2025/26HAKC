# 26HAKC — 한자 사전 + 기출

한자 사전과 한자능력검정 3급 기출문제를 한 앱에 담았다.
[25HAK3](https://github.com/marzipan2025/25HAK3)의 기출 화면에
[01HAKA](https://github.com/marzipan2025/01HAKA)의 사전을 얹은 것이다.

Kotlin + Jetpack Compose, minSdk 26.

## 화면

한 화면이다. **맨 위에 사전이 늘 떠 있고, 그 아래로 기출 회차 목록이 붙는다.**
01HAKA 를 그대로 상단에 앉힌 자리다.

- **사전** — 입력 칸은 언제나 보인다. 한글을 넣으면 그 말이 한자로 어떻게 적히는지
  찾아 그 아래로 카드가 펼쳐진다. 같은 소리에 여러 한자가 있으면 나란히 보여 주고,
  글자마다 訓音과 급수를 적는다. 한자를 그대로 넣으면 글자마다 訓音만 돌려준다.
  앞에서부터 가장 긴 낱말을 먼저 집으므로 '고사성어'가 '고사'와 '성어'로 갈리지 않는다.
  비우면 판이 한 줄로 접히고 아래 목록이 화면을 다 쓴다.
- **회차 목록** — 사전 아래에 붙는다. 맨 앞에는 새 판 알림과 단어장이 회차와 같은
  얼굴의 카드로 선다. 회차를 고르면 150문항을 카드로 넘긴다 (25HAK3 과 같다).
- **단어장** — 노랑으로 담은 문항에 나온 한자를 한 글자씩 쌓아 둔다.

## 데이터

두 가지가 서로 다른 자리에 있다.

**사전은 앱 안에** — `app/src/main/assets/dict.db`. 공개된 자료이고 바뀔 일이 없어서
넣어 둔다. 낱말 303,494개, 글자 7,232개(그중 5,318개에 급수).

```bash
python3 tools/dict.py app/src/main/assets/dict.db
```

01HAKA 의 `hanja.txt`(libhangul, BSD)와 `hanja_grades.txt`를 구워 만든다.
원본은 `~/MyGit/01HAKA/claude_kanji/HanjaWidget/HanjaWidget/` 에 있다.

**기출은 폰 안에** — 다운로드 폴더에 `26HAKC` 폴더를 만들고 `hanja3.db`를 넣은 뒤
앱에서 그 폴더를 한 번 지정한다. 앱과 데이터를 갈라 두면 서로를 붙들지 않는다
([DataFile.kt](app/src/main/java/com/artbrain/hakc/DataFile.kt)).
그 파일을 만드는 도구는 [hanja-mock](../hanja-mock/tools) 에 있다.

## 01HAKA 에서 가져온 것과 두고 온 것

| 가져온 것 | 두고 온 것 |
|---|---|
| 사전 자료 전부 (낱말·訓音·급수) | macOS 창 다루기 — 항상 위에 두기, 유리 효과, ⌘ 단축키 |
| 낱말 찾는 방식 (가장 긴 것 먼저) | 네이버 사전 조회 — 남의 화면을 긁는 방식이라 깨지기 쉽다 |
| 급수 색 | 자체 업데이트 확인 — 25HAK3 의 Updater 가 같은 일을 한다 |

톤은 원래 한 몸이었다. 25HAK3 의 색이 01HAKA 다크 모드에서 그대로 온 것이라
([Theme.kt](app/src/main/java/com/artbrain/hakc/Theme.kt)) 사전을 얹어도 이어 붙인 티가 없다.

## 빌드

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleRelease
```

서명 키는 25HAK3 과 같은 것을 쓴다 — `keystore/` 와 `keystore.properties` 는 저장소에
넣지 않는다. 백업은 `~/MyGit/_keys/` 에 있다.
