# 26HAKC — 한자 사전 + 기출

한자 사전과 한자능력검정 3급 기출문제를 한 앱에 담았다.
[25HAK3](https://github.com/marzipan2025/25HAK3)의 기출 화면에
[01HAKA](https://github.com/marzipan2025/01HAKA)의 사전을 얹은 것이다.

Kotlin + Jetpack Compose, minSdk 26.

## 화면

한 화면이다. **위에 01HAKA 가 붙박여 있고, 아래로 기출 회차 목록이 흐른다.**
위아래 두 판으로 나누는 얼개는 23DBAP 와 같다.

사전은 둥근 상자 하나이고, 그 안이 **얇은 실선 두 줄**로 나뉜다. 실선도 글도 상자
벽에서 16dp 물러난다. 위에서부터

1. **한자** — 찾아낸 한자를 칸에 꽉 차게. 동음이의어는 좌우로 훑고, 훑는 대로 아래
   訓音과 뜻이 그 표기로 따라 내려간다. 여러 표기가 있으면 글자 옆에 어느 표기인지
   표시가 붙는다(첫 표기는 붉은 점).
2. **訓音** — `음 : 급수기호 훈`. 급수 색은 특급~3급만 준다.
3. **입력** — 맨 아래. 01HAKA 처럼 안내 문구를 두지 않는다.

**판은 목록과 함께 여닫힌다.** 가장 낮췄을 때도 한자 한 줄과 訓音 두 줄은 남는다
(가로의 0.53). 가장 키우면 가로:세로 4:5 까지다. 바닥이 한 뼘(20%)밖에 안 남게
자라면 손을 뗀 순간 끝까지 붙는다. 사이의 손잡이로 직접 여닫아도 된다.

모서리가 둥그니 글은 실선보다 더 안쪽(26dp)에서 시작한다.

손잡이 줄은 왼쪽 차림표(아직 자리만), 가운데 손잡이, 오른쪽 설정이다. 이름과 판 번호는
설정 안에 있다.

앱이 스스로 하는 말은 영어로 둔다 — 01HAKA 가 그랬다. 시험 문제와 訓音 은 한국어
자료 그대로다.

## 데이터

두 가지가 서로 다른 자리에 있다.

**사전은 앱 안에** — `app/src/main/assets/dict.db`. 공개된 자료이고 바뀔 일이 없어서
넣어 둔다. 낱말 303,494개, 글자 7,374개(그중 5,403개에 급수), 뜻풀이 13,763개.

```bash
python3 tools/dict.py app/src/main/assets/dict.db          # 낱말·글자·급수
python3 tools/gloss.py kowiktionary-latest.xml.bz2 app/src/main/assets/dict.db   # 뜻풀이
```

뜻풀이는 위키낱말사전 덤프에서 가져온다. 우리가 가진 낱말로만 훑고, 문서가 한자를
짚어 준 뜻만 받는다 — 표기가 하나뿐이라고 넘겨짚으면 '나무' 문서의 뜻이 南無 에
붙는 식으로 어긋난다. 그렇게 딸려 오는 3,500 개는 대개 고유어 문서였다. 카드에 한 줄로
앉아야 하므로 30자가 넘는 풀이는 버린다.

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
