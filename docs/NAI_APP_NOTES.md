# NAI Mobile App

NovelAI Image Generation을 모바일에서 간단하고 빠르게 사용할 수 있도록 하는 앱입니다.

## 목표

데스크톱용 고급 편집 도구보다는, 회사나 외부 환경에서도 휴대폰으로 빠르게 생성하고 프롬프트를 여러 Block으로 나누어 관리/재사용할 수 있는 사용성을 목표로 합니다.

핵심 흐름:

1. Base / Character 단위의 Prompt 편집
2. Block 조합 및 활성/비활성
3. 이미지 생성
4. 생성 상태 히스토리 저장
5. 이전 상태 복원/수정
6. 자주 쓰는 Block 또는 전체 Preset 저장

---

## Prompt 편집 구조

### Base / Character

Prompt 편집 화면은 큰 틀(Section)과 그 안의 Block으로 구성합니다.

- Base: 공통 Prompt를 관리하는 큰 틀
- Character: 캐릭터 단위 Prompt를 관리하는 큰 틀
- Character는 NovelAI 웹처럼 여러 개 추가 가능
- Character 타입은 여자 / 남자 / 기타를 지원
- Style, Clothes, Composition, Extra 등은 별도 상위 Section이 아니라 Character 내부의 일반 Block으로 구현

각 Base / Character 틀은 Positive / Negative를 모두 가지며, 모바일 화면 공간을 고려해 전환 버튼으로 둘 중 하나만 화면에 표시합니다.

```text
Base
  [Positive | Negative]
  ├ Block
  └ Block

Character 1 (girl)
  [Positive | Negative]
  ├ Character Base
  ├ Style
  ├ Clothes
  ├ Composition
  └ Extra

Character 2 (boy)
  [Positive | Negative]
  └ Blocks...
```

### Character 순서

Character는 하나의 큰 틀 전체를 단위로 이동합니다.

- Character 틀에 `↑`, `↓` 버튼 제공
- 순서 변경 시 해당 Character의 Positive / Negative와 내부 Block 전체가 함께 이동
- 원하는 Character 하나를 선택하여 삭제 가능
- Character 자체에는 Locked 기능을 두지 않음

### NovelAI 웹과의 결과 일치 원칙

앱의 Block / Section 구조는 편집 편의를 위한 UI 계층일 뿐입니다.

실제 API 전송 시에는 앱 전용 Block 틀을 제거하고, 사용자가 NovelAI 웹에서 같은 Base / Character Prompt, Positive / Negative, Character 순서, Character Positioning 및 생성 설정을 직접 입력한 것과 가능한 한 동일한 결과가 나오도록 NovelAI API 구조에 매핑합니다.

---

## Prompt Block Specification

### Block 기본 속성

각 Block은 최소 다음 상태를 가집니다.

- id
- name
- content
- enabled
- locked
- collapsed
- order

새 Block에는 `Block 1`, `Block 2` 같은 임시 이름을 자동으로 부여하고 사용자가 수정할 수 있습니다.

### Block 기본 기능

- 추가 / 제거
- 이름 지정
- ON / OFF
- 접기 / 펼치기
- Locked / Unlock
- `↑`, `↓` 순서 변경
- Saved Block으로 저장 / 불러오기

---

## Locked Block

Locked의 목적은 위치 고정보다 **Block 내용과 구성을 실수로 변경하지 못하게 보호하는 것**입니다.

Locked 상태에서 금지:

- Prompt 내용 수정
- 이름 변경
- Block 삭제
- formatter 실행
- 자동완성 결과 삽입
- 해당 Block의 `↑`, `↓` 직접 이동

삭제가 필요하면 먼저 Unlock해야 합니다.

Locked 상태에서도 허용:

- ON / OFF
- 접기 / 펼치기

다른 일반 Block이 이동하면서 Locked Block의 상대적 위치가 결과적으로 바뀌는 것은 허용합니다. 즉 Locked는 절대 index 고정이 아니라 해당 Block 자체의 직접 조작 보호입니다.

---

## Block ON / OFF

OFF Block은 최종 API Prompt에서는 제외하지만 작업 상태의 일부로 계속 보존합니다.

History / Preset 저장 시:

- OFF Block도 모두 저장
- 각 Block의 ON / OFF 상태 저장
- 복원 시 당시 상태 그대로 복원

---

## Block 접기 / 펼치기

접힘 상태도 작업 상태로 저장합니다.

- 길고 자주 건드리지 않는 Quality / Negative Block 등을 계속 접어둘 수 있음
- 앱 재실행 시 마지막 접힘 상태 복원
- History / Preset 복원 시에도 당시 접힘 상태 복원

---

## Block 내부 주석

별도의 Comment 입력 필드 대신 Block `content` 안에서 기본 `##` 문법으로 주석을 작성합니다.

### 닫는 `##`가 있는 경우

```text
1girl, long hair,
## 이 부분은 테스트 메모 ##
red eyes, sharp eyes,
```

`##`와 다음 `##` 사이의 내용은 주석으로 취급합니다.

### 닫는 `##`가 없는 경우

```text
1girl, long hair,
## 여기부터는 메모
다음에는 weight를 낮춰보기
```

첫 `##` 이후 Block 끝까지를 주석으로 취급합니다.

### 가중치 내부 주석

`:: ... ::` 가중치 영역 안에서도 주석을 사용할 수 있습니다. 주석 제거 후 남은 Prompt가 가중치 문법으로 자연스럽게 이어지도록 처리합니다.

### API 전송

- 주석은 앱 편집 / Saved Block / Preset / History에는 그대로 보존
- 최종 Prompt 생성 시 주석 영역 제거
- 홀수 개의 `##`는 마지막 `##` 이후 전체가 주석이라는 정상 문법으로 취급
- 사용자 지정 Comment Delimiter는 후순위

---

## Block 줄 정리 기능

`한 줄씩` / `합쳐서` Prompt formatter를 제공합니다. 버튼 자체는 Settings에서 숨길 수 있습니다.

### 합쳐서

```text
ABC,
DEF,
GHI,
```

또는

```text
ABC
DEF
GHI
```

을

```text
ABC, DEF, GHI
```

형태로 정리합니다.

규칙:

- 줄바꿈으로만 구분된 항목 사이에 필요한 쉼표 추가
- `,,`, `,,,` 등 중복 쉼표는 `,` 하나로 정규화
- 불필요한 공백 정리
- 원래 줄바꿈 상태를 별도로 보존하지 않음

### 한 줄씩

쉼표 기준으로 항목별 줄바꿈 형태로 정리합니다.

단:

- `:: ... ::` 가중치 영역은 하나의 덩어리로 취급하여 내부를 줄바꿈하지 않음
- 가중치 내부의 `##` 주석은 유지 가능
- 자연어 문장 안의 쉼표와 태그 구분 쉼표를 완벽하게 판별하려 하지 않음

이 기능은 완전한 포맷 왕복 기능이 아니라 사용 편의를 위한 Prompt formatter입니다.

---

## Block 결합 규칙

여러 활성 Block을 최종 Prompt로 합칠 때:

- Block 내용 마지막에 쉼표가 이미 있으면 그대로 유지
- 마지막에 쉼표가 없으면 앱이 쉼표 하나 추가
- 그 뒤 다음 활성 Block을 이어 붙임

앱 전용 Block 경계는 NovelAI로 전송되는 최종 Prompt에서는 사라집니다.

---

## `::` 가중치 문법 보조

### 숫자 + 닫는 `::` 자동 보정

`:: ... ::` 영역의 닫는 `::` 바로 앞 문자가 숫자인 경우 공백 하나를 삽입합니다.

```text
2::abc123::
```

→

```text
2::abc123 ::
```

이 규칙은 `artist:` 접두사에 종속시키지 않습니다.

### 자동 보정 옵션

Settings에서 ON / OFF 가능.

- OFF: 자동 수정 없음
- ON: 타이핑 중에는 수정하지 않고 Generate 직전에 보정

### `::` 개수 Warning

Block 단위로 `::` 출현 횟수를 검사합니다.

- 짝수: 기본적으로 pair가 맞는 것으로 판단
- 홀수: 닫히지 않은 가중치 문법 가능성이 있으므로 화면 상단/하단에 Warning 표시
- Warning은 Generate를 막지 않음
- Block마다 독립적으로 검사

### 가중치 Syntax Highlight

NovelAI 웹과 유사하게 `:: ... ::` 가중치 영역을 가중치 값에 따라 색으로 하이라이트합니다.

기본 방향:

- `1.0`을 중립 기준으로 취급
- `1.0`보다 높은 가중치는 빨간 계열로 표시
- 값이 높을수록 빨간색 강조를 강하게 표시
- `1.0`보다 낮은 가중치는 파란 계열로 표시
- 값이 낮을수록 파란색 강조를 강하게 표시
- 실제 색상 단계/채도는 NovelAI 웹의 시각적 동작을 가능한 한 비슷하게 맞춤

예:

```text
0.7::soft style ::   → 파란 계열
1.0::neutral ::      → 중립
1.4::strong style :: → 빨간 계열
2.0::very strong ::  → 더 강한 빨간 계열
```

Highlight는 편집 보조 UI일 뿐 실제 Prompt 문자열에는 영향을 주지 않습니다.
`##` 주석 영역은 Prompt 가중치 하이라이트 대상에서 제외합니다.

---

## Saved Block

자주 쓰는 Prompt 조각을 Block 단위로 저장합니다.

Saved Block을 현재 작업에 불러올 때는 원본 참조가 아니라 **복사본**으로 불러옵니다.

- 현재 작업에서 수정해도 Saved Block 원본은 자동으로 변경되지 않음
- Saved Block 원본이 나중에 바뀌어도 과거 History Snapshot에는 영향 없음
- 덮어쓰기는 사용자가 명시적으로 저장할 때만 수행

### Saved Block 폴더

- 기본 저장 폴더 제공
- 예시용 폴더 몇 개를 미리 제공 가능: Style / Clothes / Pose / Composition / Negative 등
- 기본 폴더는 강제 분류가 아니라 사용 예시 목적
- 사용자는 폴더를 자유롭게 추가 / 수정 가능

Block 내부 `##` 주석도 그대로 저장합니다.

---

## Preset

현재 작업 상태 전체를 저장합니다.

포함 대상:

- Base / Character 구성
- Character 타입 / 순서
- Positive / Negative Block 전체
- OFF Block 포함
- Block 이름 / 내용 / `##` 주석
- ON/OFF / Locked / 접힘 상태 / 순서
- Character Positioning
- NovelAI API로 전달되는 모든 Generation Settings
- Seed

### Preset 저장 형식

Preset은 현재 Session 전체를 복원하기 위한 **versioned snapshot**으로 저장하는 것을 기본 방향으로 합니다.

권장 구조 예:

```text
Preset
- id
- name
- createdAt
- updatedAt
- snapshotVersion
- snapshotJson
```

- 편집/검색이 잦은 Saved Block은 Room Entity로 정규화합니다.
- Session 전체 보존이 목적인 Preset은 `snapshotVersion + snapshotJson` 형태의 immutable DTO 저장을 우선 검토합니다.
- schema 변경 시 기존 Preset을 읽을 수 있도록 snapshot version별 migration/decoder를 둡니다.
- NovelAI API payload JSON 자체를 Preset으로 저장하지 않고 앱의 Domain Snapshot을 저장합니다.

---

## History

History는 **생성 1회 = 이미지 1장 = History 1개**로 관리합니다. 초기 버전에서는 한 번에 여러 장 생성하지 않습니다.

### History Snapshot

생성 당시 상태를 독립 Snapshot으로 저장합니다.

저장 대상:

- Thumbnail
- 원본 이미지 파일 경로
- Base / Character 구조
- Character 타입 / 순서
- 모든 Block (OFF 포함)
- Block 이름 / content / 주석
- Block ON/OFF / Locked / 접힘 상태 / 순서
- Character Positioning
- Seed
- NovelAI API로 전달된 모든 Generation Settings
- 모델
- 생성 시간

Saved Block / Preset이 이후 변경되어도 History를 불러오면 생성 당시 데이터 그대로 복원합니다.

### History Snapshot 저장 형식

History는 과거 상태를 수정하는 데이터가 아니라 생성 당시 상태를 영구적으로 보존하는 데이터이므로, 내부 Base/Character/Block을 History 전용 Room relation으로 모두 정규화하기보다 **`snapshotVersion + snapshotJson` 형태의 immutable snapshot** 저장을 우선합니다.

권장 구조 예:

```text
HistoryEntry
- id
- createdAt
- imagePath
- thumbnailPath
- model
- snapshotVersion
- snapshotJson
```

원칙:

- `snapshotJson`에는 생성 당시 앱 Domain 상태(Base / Character / Block / Settings / Seed 등)를 독립 복사하여 저장
- 현재 Session 또는 Saved/Preset 객체를 참조하지 않음
- 앱 schema가 변경되어도 오래된 History를 읽을 수 있도록 `snapshotVersion`별 decoder/migration 제공
- API 요청/응답 JSON을 그대로 저장하는 것이 아니라 앱의 versioned Snapshot DTO를 저장
- 검색/정렬에 필요한 `createdAt`, `model`, 이미지 경로 등 최소 metadata만 별도 컬럼으로 둘 수 있음

### 원본 이미지

생성된 원본 이미지는 항상 저장합니다.

History에서는:

- Thumbnail은 History 자체에 보존
- 원본은 실제 저장 폴더의 파일을 참조
- 원본 파일이 존재하면 History에서 원본 이미지 열람 가능
- 사용자가 파일을 삭제하거나 이동한 경우 Thumbnail은 유지
- 원본이 없으면 `원본 이미지가 삭제되었거나 이동되었습니다`와 같은 안내 표시

### History 개수

- 기본 20개
- Settings에서 조절 가능
- 최대 100개
- 높은 값을 선택할 경우 저장공간 증가 안내 가능

### History 불러오기

과거 상태를 현재 Session에 적용할 때 다음 항목을 체크박스로 선택할 수 있습니다.

- 설정
- Base 태그
- Character 태그
- Seed

모델도 Generation Settings에 포함되므로 설정을 가져오면 당시 모델도 함께 복원할 수 있습니다.

History 원본 Snapshot 자체는 이후 편집으로 변경하지 않습니다.

---

## 동일 생성 상태와 원본 복구

Generate 직전 현재 생성 데이터와 **직전에 생성한 History 1개**를 비교합니다. 단, 중복 경고는 해당 History의 원본 이미지가 실제로 존재할 때만 표시합니다.

비교 대상 예:

- 모델
- Base Prompt
- Character Prompt
- Character 순서 / Positioning
- Generation Settings
- Seed

접힘 상태 등 UI 상태는 비교 대상에서 제외합니다.

완전히 동일하고 원본 이미지가 존재하면 Warning을 표시한 뒤 사용자가 계속 생성할 수 있습니다.

```text
직전 생성과 동일한 설정입니다.
그래도 생성하시겠습니까?
```

원본 이미지가 삭제되거나 이동되어 Thumbnail만 남아 있다면 중복으로 취급하지 않고 동일 설정과 Seed의 재생성을 바로 허용합니다. 재생성에 성공하면 새 History 항목을 추가하지 않고 기존 항목의 원본 경로와 Thumbnail을 복구합니다. 즐겨찾기와 기존 생성 시각은 유지합니다.

비교에는 최종 처리된 Base/Character Positive/Negative, Character 순서/Positioning, 모델, Seed 및 실제 결과에 영향을 주는 모든 Generation Settings를 포함하고, 접힘 상태 같은 편집 전용 UI 상태는 제외합니다. 구현에서는 이 생성 상태를 안정적으로 비교할 수 있는 generation identity/fingerprint를 유지합니다.

---

## 작업 Stash

History / Preset 등을 불러올 때 현재 작업을 잃지 않도록 임시 작업 상태를 1개 보관합니다.

- 현재 작업 A에서 History/Preset B를 불러오면 A를 Stash에 자동 저장
- 현재 작업은 B로 교체
- `이전 작업` 기능으로 A와 B를 스위칭 가능
- MVP에서 Stash는 1개만 유지

---

## 마지막 작업 자동 복원

앱 재실행 시 마지막 작업 Session을 자동 복원합니다. 모바일 OS에서 백그라운드 종료가 발생할 수 있으므로 필수 동작으로 봅니다.

---

## Seed

초기 목표:

- Random
- Fixed
- History에서 Seed 복원
- 이미지 생성 정보에서 Seed 가져오기

별도 Seed Library는 우선 구현하지 않습니다.

---

## Character Positioning

NovelAI 웹과 유사한 그리드 기반 Character Positioning UI를 제공합니다.

- Character마다 위치 선택 가능
- NovelAI 웹과 최대한 동일한 사용자 경험을 목표
- Character 순서와 Positioning 정보를 함께 저장 / 복원
- 정확한 Grid 크기와 API parameter는 실제 NovelAI API 구조를 기준으로 구현

---

## 모바일 UI 방향

앱 실행 시 별도 Home 화면을 거치지 않고 **Generate 화면으로 바로 진입**하는 방향을 우선합니다.

하단 Navigation 현재안:

- Generate
- History
- Saved
- Settings

Saved는 `Blocks / 세트 / 전체 Preset`으로 구분합니다. Folder는 세 유형에 공통으로 적용하고, 검색어와 Folder 필터를 함께 사용할 수 있어야 합니다.

기능이 추가될수록 Generate 상단과 각 Block/Character 카드에 아이콘을 계속 나열하지 않습니다. 자주 쓰는 핵심 동작만 직접 노출하고, 저장/불러오기·Tag Dictionary·고급 편집처럼 화면 전환이 필요한 기능은 일관된 진입점 또는 overflow 메뉴로 정리합니다. 아이콘만으로 의미가 불명확한 동작은 짧은 label이나 tooltip/content description을 함께 제공합니다.

생성에 직접 영향을 주는 옵션(모델, 해상도, Seed, Sampler, Steps 등)은 앱 Settings가 아니라 Generate 화면 내부의 접을 수 있는 Generation Settings 영역에 둡니다.
Settings에는 저장 경로, History 개수, 자동완성 소스, formatter 표시 여부, 자동 보정 등 앱 동작 관련 옵션을 둡니다.

Generate 화면에서는 Base / Character Section 안에 Block을 카드 형태로 표시합니다.

Block 카드 기본 상태 후보:

- 이름
- Prompt 일부 미리보기
- ON/OFF
- Locked 상태
- 접기/펼치기
- 위/아래 이동 버튼

Character 틀에는:

- 여자 / 남자 / 기타 타입
- Positive / Negative 전환
- Character 순서 `↑`, `↓`
- Character 삭제
- Positioning UI 진입

등을 제공합니다.

### Generate 화면 결과 미리보기

Prompt를 조금씩 수정하며 반복 생성하는 흐름을 위해 **Generate 화면 안에서 방금 생성한 결과를 바로 볼 수 있어야 합니다.**

- 생성 직후 최신 결과 이미지를 Generate 화면에 표시
- Preview는 접기 / 펼치기 가능
- Preview 이미지를 누르면 원본 크게 보기 가능
- 다음 이미지를 생성하면 Preview는 최신 이미지로 교체
- 이전 결과는 History에 남김
- 앱 재실행 시 최신 History의 원본 이미지가 실제로 존재하면 Latest Result로 다시 연결하되, 시작 화면은 Prompt 편집 화면을 유지
- 최신 History의 원본이 삭제되거나 이동했다면 Thumbnail만으로 Latest Result를 복원하지 않음
- 모바일 화면을 과도하게 차지하지 않도록 Preview 크기와 접힘 상태를 조절

결과 확인을 위해 매번 History 화면으로 이동해야 하는 구조는 피합니다.

### Prompt 편집기에서 Tag Dictionary 진입

Block Prompt 입력창에서 Tag Dictionary로 바로 진입할 수 있는 버튼을 제공합니다.

예:

```text
[태그 사전] [한 줄씩] [합쳐서]
```

formatter 버튼은 Settings에서 숨길 수 있으므로, 필요하면 태그 사전 버튼만 남길 수 있습니다.

---

## 자동완성 / 태그 제안

Prompt Block 편집 시 태그 자동완성을 지원합니다.

### 후보 소스

- NovelAI Image Generation API 태그 제안
- Danbooru API 태그 검색/조회

Settings에서 자동완성 소스를 선택할 수 있습니다.

- NovelAI only
- Danbooru only
- Both

### 자동완성 대상

현재 커서 위치에서 사용자가 입력 중인 **3글자 이상인 현재 태그 조각**만 자동완성 대상으로 사용합니다.

자동완성 후보를 선택하면 현재 입력 중인 해당 조각을 선택한 태그로 **치환**합니다.

### 결과 표시

Both 사용 시 두 소스를 하나의 목록으로 합치지 않고 별도 행으로 표시합니다.

```text
NAI: tag_a, tag_b, tag_c
DAN: tag_a, tag_d, tag_e
```

한쪽 소스에서만 결과가 있으면 해당 행만 표시합니다.

### API 호출 정책

- 마지막 입력 후 약 300~500ms 동안 추가 입력이 없을 때 요청
- API 호출 사이 약 1초 cooldown
- cooldown 동안 직전 결과 또는 로컬 캐시 활용
- 동일 검색어 / 최근 prefix는 짧은 메모리 캐시 재사용 가능
- 사용자가 계속 타이핑 중일 때 중간 입력마다 요청하지 않음

### 캐시 방향

장기적으로 자동완성 결과를 로컬 DB에 누적할 수 있으나, 로컬 결과가 있다는 이유만으로 서버 조회를 영구 생략하지 않습니다.

필요 시 향후:

- Tag DB: name, category, post_count, last_seen 등
- Prefix/Search Cache: prefix, fetched_at 등
- TTL이 지난 prefix는 로컬 결과를 먼저 보여주고 서버에서 최신 결과를 다시 받아 upsert

MVP에서는 `debounce + 약 1초 cooldown + 짧은 메모리 캐시`를 우선합니다.

---

## Tag Dictionary / 한글 태그 사전

영문 태그 이름을 정확히 모르는 경우에도 한글로 검색하여 NovelAI / Danbooru 계열 태그를 찾을 수 있는 로컬 Tag Dictionary를 제공합니다.

자동완성은 이미 알고 있는 태그를 빠르게 완성하는 기능이고, Tag Dictionary는 **영문 태그 자체를 모를 때 한글 검색 / 즐겨찾기 / 썸네일로 찾는 기능**으로 역할을 분리합니다.

### 초기 DB와 사용자 확장

초기 한국어 태그 데이터는 재배포 조건을 확인할 수 있는 공개 / 오픈소스 Danbooru 한국어 태그 데이터 등을 후보로 사용합니다.

사전은 개념적으로 다음 두 레이어로 나눕니다.

- Base Dictionary: 앱에 기본 포함되는 태그 / 한국어 데이터
- User Translation / User Data: 사용자가 직접 추가하거나 수정한 번역, 별칭, 즐겨찾기, 썸네일 등

앱의 기본 DB를 업데이트해도 사용자가 직접 만든 데이터는 덮어쓰지 않는 방향을 우선합니다.

### Tag 데이터 후보

```text
Tag
- tag
- category
- post_count
- NAI source status
- Danbooru source status
- last_seen

Translation / User Data
- tag
- ko
- aliases_ko
- translation_source
- favorite
- thumbnail
- updated_at
```

태그 출처와 한국어 번역 출처는 서로 별개의 정보로 관리합니다.

예:

- 태그 출처: NAI / Danbooru / Both / User / Unknown
- 번역 출처: Bundled / User / AI Import

### 태그 출처 표시

모바일 화면 공간을 절약하기 위해 `[NAI+DAN]` 같은 긴 텍스트 배지는 기본 UI로 사용하지 않습니다.

대신 작은 색상 마커 / 세로선 / 점 등으로 출처를 가볍게 표시할 수 있습니다.

예시 방향:

- NAI 확인 태그: 남색 계열 마커
- Danbooru 확인 태그: 갈색 계열 마커
- 양쪽에서 확인: 두 개의 작은 마커 또는 결합 표시
- 직접 추가 / 출처 미확인: 회색 또는 표시 없음

이 출처 표시는 참고 정보이며 태그 선택보다 시각적으로 우선하지 않습니다.

NAI 자동완성이나 Danbooru 자동완성에서 태그를 발견하면 로컬 Tag DB에 upsert하고 해당 source 상태를 갱신할 수 있습니다.
사용자가 직접 입력하여 Tag DB에 새 항목을 추가하는 기능도 제공합니다.

### 한국어 검색

태그는 영문 canonical tag뿐 아니라 한국어 번역과 한국어 별칭으로 검색할 수 있습니다.

예:

```text
shorts
ko: 반바지
aliases_ko: 숏팬츠, 짧은 바지
```

사용자가 `반바지`, `숏팬츠` 등으로 검색해도 실제 Prompt에 삽입되는 값은 canonical 영문 tag를 사용합니다.

### 즐겨찾기 / 최근 사용

Tag Dictionary에서 자주 쓰는 태그를 즐겨찾기할 수 있습니다.

필요 시 다음 빠른 보기 제공:

- 즐겨찾기
- 최근 사용 태그

### 태그 썸네일

태그마다 사용자가 임의의 Thumbnail을 지정할 수 있도록 합니다.

특히 다음 종류의 태그를 시각적으로 기억하는 데 사용합니다.

- 의상
- 포즈
- 헤어스타일
- 액세서리

Thumbnail은 자동으로 외부 이미지를 가져오기보다 사용자가 직접 지정하는 방식을 우선합니다.
앱에서 생성한 이미지 또는 기기 이미지에서 선택하는 방식을 검토합니다.

### 사용자 직접 편집 / 번역 수정

Tag Dictionary Entry는 필요 시 상세 화면에서 다음 정보를 직접 수정할 수 있습니다.

- 영문 tag
- 한국어 번역
- 한국어 별칭
- 즐겨찾기
- Thumbnail
- 앱용 의미 카테고리

기본 번역이 존재하더라도 사용자가 직접 번역을 수정할 수 있습니다.
사용자 수정값은 Base Translation을 직접 덮어쓰지 않고 **User Translation Override**로 별도 저장하며, 화면과 검색에서는 사용자 수정값을 우선 적용합니다.

- 앱/기본 번역 DB가 업데이트되어도 사용자 수정 번역은 유지
- 필요 시 `기본 번역으로 되돌리기`로 User Override만 삭제
- 사용자가 직접 추가한 한국어 별칭도 동일하게 보존

Canonical tag 검증이 가능한 경우 잘못된 태그 입력을 안내합니다.

### AI 번역 보조 / 미번역 태그 Queue

NAI / Danbooru 자동완성 또는 사용자 입력을 통해 수집된 태그 중 한국어 번역이 없는 항목은 `미번역 태그`로 누적할 수 있습니다.

사용자가 자신의 ChatGPT / Gemini / Claude 등 외부 AI에 번역을 맡길 수 있도록 **AI 번역용 양식 + 미번역 태그 목록을 클립보드에 복사**하는 기능을 제공합니다.

예시:

```text
tag	ko	aliases
short_shorts		
thigh_strap		
looking_back		
```

AI에게는 영문 `tag` 값을 변경하지 말고 `ko`, `aliases`만 채우도록 안내합니다.

초기 구현의 외부 AI 교환 형식은 **JSONL**을 사용하며, 사용 편의를 위해 다음 세 파일을 하나의 ZIP으로 Export합니다.

- `translation_instructions.md`
- `categories.json` — category ID, 한국어 이름, 짧은 분류 설명
- `tags_to_process.jsonl`

- Export 전에 `한국어 번역 없음` / `앱 카테고리 없음` 조건을 각각 선택하며, 선택한 조건 중 하나라도 일치하는 태그를 한 번에 최대 1,000개씩 Export
- 일반 사용자는 결과를 Import한 뒤 다음 최대 1,000개를 다시 Export하는 단순한 순차 흐름을 사용
- 초기 DB 구축처럼 전체 대상이 필요한 개발자용 내보내기는 Tag Dictionary 맨 아래에 낮은 강조도로 분리하며, 하나의 ZIP 안에 `tags_to_process_0001.jsonl` 형식으로 1,000개씩 자동 분할
- 첫 JSONL 행에는 처리 지침과 현재 존재하는 카테고리 목록을 포함
- 이후 행은 `tag`, `ko`, `aliases_ko`, `app_category`, `suggested_category`, `needs_review` 및 참고용 원본 category/post count를 포함
- `tag` canonical 값은 절대 변경하지 않음
- AI는 기존 카테고리를 우선 사용하고 맞는 분류가 없을 때만 `suggested_category`로 새 카테고리를 제안
- `suggested_category`는 Import 시 자동 생성하지 않고 검토 대상으로만 표시
- 특정 AI 서비스에 종속하지 않고 사용자가 GPT, Gemini, Claude 또는 로컬 모델에 파일을 전달할 수 있게 함
- Import 전 유효 행, 잘못된 행, DB에 없는 tag, 검토 필요 항목, 신규 카테고리를 요약하고 사용자 확인 후 반영
- 기존 사용자 번역/분류는 기본적으로 덮어쓰지 않고 사용자가 명시적으로 선택한 경우만 덮어씀

AI가 반환한 결과를 앱에 붙여넣거나 JSONL 파일로 가져오면:

- 기존 canonical tag와 일치하는지 검증
- 일치하는 항목의 한국어 번역 / 별칭만 import
- 알 수 없는 tag는 자동 등록하지 않거나 Warning 표시

하여 잘못된 AI 출력으로 DB가 손상되는 것을 줄입니다.

Tag만으로 의미가 애매한 경우에는 AI 번역용 내보내기에 Danbooru category / wiki 설명 등 참고 정보를 함께 제공하는 방식을 검토합니다.

번역 작업을 대량으로 수행할 때는 한국어 번역만 생성하지 않고 **앱용 의미 카테고리도 함께 분류**합니다.

예시 app category:

- clothes
- pose
- hair
- body
- expression
- accessory
- background
- composition
- effect
- other

Danbooru가 제공하는 원본 category(`general / artist / copyright / character / meta`)는 그대로 보존하고, 앱에서 검색/필터에 사용할 의미 기반 카테고리를 별도 필드로 둡니다.

```text
Tag
- tag
- danbooru_category
- app_category
- post_count
- ...
```

즉 번역 배치의 기본 산출물은 개념적으로 다음과 같습니다.

```text
tag -> ko -> aliases_ko -> app_category
```

초기 대량 번역/분류 결과에 오류가 있더라도 사용자가 Tag Dictionary 상세 화면에서 번역과 app category를 직접 수정할 수 있도록 합니다.

### Prompt로 다중 태그 가져오기

Prompt 입력창에서 Tag Dictionary를 열 때 **현재 Block과 마지막 Cursor 위치를 기억**합니다.

Tag Dictionary에서 태그를 탭하면 즉시 Prompt로 돌아가지 않고 화면 하단의 임시 선택 영역에 순서대로 누적합니다.

장바구니와 비슷한 흐름:

```text
선택 4개
[white_shirt ×] [denim_shorts ×] [looking_back ×] [hands_in_pockets ×]
                                            [가져오기]
```

규칙:

- 태그를 탭한 순서대로 선택 목록에 누적
- `×`로 개별 선택 제거 가능
- `가져오기`를 누르면 Tag Dictionary를 열기 전 마지막 Cursor 위치에 일괄 삽입
- 삽입 시 canonical 영문 tag 사용
- 여러 태그는 Prompt 규칙에 맞게 쉼표로 연결
- 선택 목록 내부 순서 변경 기능은 초기에는 필수로 두지 않음

태그를 짧게 탭하면 선택 목록에 추가하고, 길게 누르거나 별도 상세 버튼을 통해 번역 / 출처 / 즐겨찾기 / 썸네일 등을 편집하는 방식을 검토합니다.

---

## Tag Dictionary 데이터 소스 / 라이선스 조사

Tag Dictionary의 초기 데이터는 **canonical Danbooru tag 목록**과 **한국어 번역/별칭 layer**를 분리해서 관리하는 방향을 우선합니다.

### 1. 기본 canonical Danbooru tag DB 후보 — 사용 가능

**DraconicDragon / dbr-e621-lists-archive**

- Repository: https://github.com/DraconicDragon/dbr-e621-lists-archive
- License: Unlicense
- Danbooru / e621 태그 목록을 autocomplete 용도로 정기 생성/보관하는 프로젝트
- 개발자에게 merged list보다 각 서비스별 CSV를 따로 사용하는 것을 권장함
- NAI APP에서는 **Danbooru-only 목록**을 canonical tag / category / post count / alias 등의 기본 데이터 후보로 사용
- e621 merged list는 사용하지 않음

현재 조사 기준으로는 라이선스가 가장 명확해서 **앱에 초기 영문 태그 DB를 번들할 1순위 후보**로 봅니다.

다만 실제 포함 시에는 사용하려는 특정 CSV의 컬럼 구조와 갱신 방법을 다시 확인하고,
앱 내 오픈소스 고지 화면에 repository / license 정보를 남깁니다.


### 초기 태그 규모 메모

DraconicDragon 저장소의 Danbooru 목록은 사용 빈도 threshold별 CSV를 제공하며, 후보로 본 최신 `pt20` 계열은 **20회 이상 사용된 태그를 중심으로 한 대규모 목록**입니다.
정확한 행 수는 실제 개발 시 선택한 CSV를 내려받아 직접 계산하여 확정합니다.

한국어 번역 및 app category 생성은 전체 파일을 한 번의 거대한 요청으로 처리하지 않고, 개발 단계에서 배치 단위로 생성/검증하여 로컬 DB에 병합하는 방식을 우선합니다.

### 2. 한국어 DB 후보 — 참고 가능, 번들 보류

#### Localsmile / danbooru_KR_wiki_tag_search

- Repository: https://github.com/Localsmile/danbooru_KR_wiki_tag_search
- 한국어 Danbooru wiki 번역/검색 데이터와 `danbooru_tags_classified.csv` 제공
- 태그 검색, 다중 태그 복사, 예시 이미지 미리보기 등 NAI APP의 Tag Dictionary와 유사한 기능을 구현하고 있음
- 다른 프로젝트에서도 한국어 autocomplete 데이터 소스로 사용 중

하지만 조사 시점에 repository에서 **명시적인 LICENSE 파일/재배포 라이선스를 확인하지 못함**.
따라서 데이터 품질 참고 및 UI/검색 구조 참고 용도로는 유용하지만,
허가 없이 해당 CSV를 NAI APP에 직접 번들하는 것은 보류합니다.

#### KR_danbooru_tags_with_description v3_modified.csv

- 공개 커뮤니티에서 한국어 설명/키워드가 포함된 Danbooru autocomplete CSV로 배포된 데이터
- `n0va39/ComfyUI-EasyUseAnima`에서도 해당 CSV를 **원 저작자 허가를 받아 포함했다고 명시**함
- EasyUse Anima repository: https://github.com/n0va39/ComfyUI-EasyUseAnima

EasyUse Anima 자체는 MIT License이지만,
README의 표현이 해당 CSV를 별도의 작성자 허가로 포함했다고 되어 있으므로
**그 허가가 제3자 프로젝트인 NAI APP까지 자동으로 이전된다고 가정하지 않음**.
원 저작자에게 별도로 재배포 허가를 받거나 데이터 자체의 공개 라이선스가 확인되기 전에는 번들하지 않습니다.

### 3. 참고 프로젝트 — 코드/구조 참고용

**joykst96 / danbooru-tag-rag**

- Repository: https://github.com/joykst96/danbooru-tag-rag
- License: MIT
- 한국어 입력을 Danbooru tag에 연결하는 로컬 검색 구조 참고 가능
- 영어 tag + 한국어 aliases 기반 검색이 실사용에서 유리했다는 구현 경험이 있음
- 단, 핵심 `danbooru-tags.csv`는 repository에 포함되지 않고 외부 파일을 요구하므로 초기 DB 데이터 소스로 직접 사용하지 않음

### 권장 초기 구현

```text
Bundled Canonical DB
  └ DraconicDragon Danbooru-only tag list (Unlicense)

Korean Translation Layer
  ├ 앱에서 직접 작성한 번역
  ├ 사용자 직접 입력/수정
  ├ 사용자 AI 번역 Import
  └ 향후 라이선스가 명확한 한국어 DB 확보 시 Base Translation으로 추가
```

즉 **영문 canonical tag 데이터와 사용자 한국어 번역 데이터를 독립적으로 유지**합니다.

장점:

- 기본 태그 존재 여부 / category / post count는 재배포 가능한 DB로 즉시 제공 가능
- 한국어 데이터 라이선스가 애매한 프로젝트에 앱 전체가 종속되지 않음
- 사용자 Translation은 앱 업데이트나 Base DB 갱신에도 유지 가능
- 추후 정식 허가를 받은 한국어 DB가 생기면 Base Translation layer만 추가/교체 가능

### 외부 한국어 DB Import

앱에 CSV / JSON Tag Dictionary Import 기능을 제공하는 것도 검토합니다.

이 경우 앱 자체가 라이선스 불명확한 한국어 DB를 배포하지 않고,
사용자가 자신이 보유한 데이터 파일을 직접 선택해 User Translation / Tag DB에 병합할 수 있습니다.

Import 시 canonical tag 존재 여부를 검사하고,
알 수 없는 tag는 자동 삽입하지 않고 확인 대상으로 분류합니다.

---

## 이미지 저장

생성된 원본 이미지는 항상 저장합니다.

### 기본 동작

- 앱 기본 저장 폴더 제공
- 생성 시 원본 이미지 자동 저장
- History는 저장된 원본 파일 경로를 참조

### 사용자 설정

Settings에서 원본 이미지 저장 경로를 사용자 지정 폴더로 변경할 수 있습니다.

History는 **생성 당시 저장된 원본 파일 경로**를 기준으로 원본 존재 여부를 확인합니다.

### 이미지 가져오기

- Generate overflow의 임시 이미지 버튼은 제거하고 Generation Settings의 모델 선택 바로 아래에 진입점을 둠
- PNG/JPEG/WebP를 선택하고 미리보기 후 용도를 선택
- 공식 Swagger에서 확인된 `img2img` action은 원본 이미지를 Base64로 전송하며 Strength/Noise를 설정
- NovelAI PNG의 `Description`/`Comment` metadata가 있으면 Prompt, Undesired Content, Characters, Settings, Seed를 항목별로 선택해 복원
- 외부 PNG에서 Image2Image/Vibe/Precise Reference 사용 흔적은 감지하되 원본 입력 이미지가 포함되어 있지 않거나 완전히 복원되지 않으면, 동일 Prompt/설정만으로 같은 결과가 나오지 않을 수 있음을 비차단 Warning으로 표시
- 앱 History가 입력 이미지 URI를 보존했더라도 파일 삭제·이동·권한 만료로 접근할 수 없으면 입력 이미지 복원을 비활성화하고 동일 결과를 보장할 수 없다는 Warning을 표시
- 선택한 이미지 URI와 Image2Image 설정은 Session/Preset/Stash/History snapshot에 포함
- Vibe Transfer와 Precise Reference는 공개 Swagger가 model-specific parameter schema를 제공하지 않으므로 실제 필드와 Vibe encoding endpoint를 확인하기 전 API 전송을 활성화하지 않음
- 공식 문서 기준 Precise Reference는 V4.5 전용이며 Vibe Transfer와 동시 사용할 수 없음

---

## 하지 않을 것 / 후순위

초기 버전에서는 다음 기능을 우선 제외합니다.

- 프로젝트 단위 관리
- 대형 Reference Board
- Style Lab
- 별도 Character Manager
- Seed Library
- 복잡한 Prompt Diff
- 데스크톱 수준의 다중 패널 UI
- 앱 내 작가 태그 탐색/조합 전용 도구
- 여러 단계 Stash Stack
- 사용자 지정 Comment Delimiter

Style / Clothes / Composition / Extra는 별도 관리 화면을 만들지 않고 Character 내부 Block으로 처리합니다.

---

## 조사 필요

### NovelAI API 구조

앱 내부 Block 구조를 NovelAI 웹과 동일한 결과가 나오도록 실제 API payload에 매핑해야 합니다.

확인 대상:

- Base Prompt / Base Negative 전달 방식
- Character별 Positive / Negative 전달 방식
- Character 순서
- Character 타입
- Character Positioning parameter
- NovelAI 웹과 동일한 결과를 위한 Prompt 전처리 규칙
- 모든 Generation Settings parameter

### NAI V5 Token 처리 / Experimental Token Estimator

이 기능은 핵심 생성 흐름과 무관한 **후순위 Experimental 기능**으로 취급합니다. 첫 usable build 및 초기 MVP 완료 조건에 포함하지 않으며, 실제 구현은 Phase 9 이후 또는 별도 experimental 작업으로 미뤄도 됩니다.

NovelAI V5 이미지 Prompt의 실제 effective token 수를 공식적으로 동일하게 계산할 수 있는 방법이 확인되기 전까지, 앱에서는 **Experimental/Beta 추정기** 형태로 token 수를 표시할 수 있습니다.

목표:

- Block별 예상 token 수 표시 가능 여부 확인
- 전체 prompt 예상 token 수 계산
- NAI 웹/실제 처리와 로컬 계산 결과의 차이 비교
- 비교 데이터가 쌓이면 estimator 알고리즘을 점진적으로 보정

표시 예:

```text
Estimated Tokens (Beta): 684 / ~1471
```

또는 한국어 UI에서는:

```text
예상 토큰 (Beta): 684 / ~1471
```

정책:

- 숫자는 **공식 NovelAI 계산값이 아니라 참고용 추정치**임을 명확히 표시
- 설명/도움말에 `실제 처리 결과와 차이가 있을 수 있으며 참고용으로만 사용` 문구 제공
- 예상 한도를 넘더라도 Generate를 차단하지 않음
- 필요하면 `⚠ 예상 한도 초과` 정도의 비차단 Warning만 표시
- 추정치가 틀려도 Prompt를 임의로 자르거나 수정하지 않음
- estimator 버전(`token_estimator_version`)을 내부적으로 관리하여 알고리즘 변경/비교가 가능하도록 함

보정 방법:

1. 짧은 단일 Tag
2. 긴 Tag 조합
3. 자연어 Prompt
4. `:: ... ::` weight 구문
5. Base / Character Prompt
6. Comment 제거 전/후
7. 다양한 실제 NAI 웹 입력

등의 샘플을 비교하여 차이가 나는 패턴을 수집하고 estimator를 점진적으로 개선합니다. 정확도는 Beta 기능의 Known Issue로 취급하며 생성 기능 자체와 분리합니다.

---

## 데이터 백업 / 복원

사용자가 장기간 축적한 앱 데이터를 사용자 자산으로 취급하고 전체 백업/복원 기능을 제공합니다.

백업 대상 예:

- Saved Block
- Preset
- Tag Dictionary의 사용자 번역 / 번역 수정(User Override)
- 사용자 추가 태그
- 즐겨찾기 / 최근 사용 정보
- 태그 썸네일 연결 정보
- 앱 설정
- 필요한 경우 History metadata (원본 이미지 파일 자체 포함 여부는 별도 옵션 검토)

백업은 하나의 내보내기 파일(예: ZIP 또는 앱 전용 archive)로 생성하고, 새 기기 또는 앱 재설치 후 다시 Import할 수 있도록 합니다.

태그 Base DB나 앱 기본 번역처럼 재생성 가능한 데이터와 User Data를 분리하여, 앱 업데이트나 Base DB 교체 시에도 사용자 수정 내용이 유지되도록 합니다.

---

## Generate 편의 기능 추가

### 같은 설정 + 새 Seed 재생성

현재 Prompt / Character / Generation Settings는 그대로 유지하고 Seed만 새로 생성하여 다시 생성할 수 있는 빠른 동작을 제공합니다.

- Last Result Preview 주변의 작은 재생성 버튼 등으로 접근
- 기존 Generate 흐름을 방해하지 않도록 보조 기능으로 배치
- 새 결과는 정상적으로 새 History Entry로 저장

### 생성 실패 Retry

네트워크 오류나 NovelAI API 오류 등으로 생성이 실패한 경우 현재 작업 상태를 변경하지 않고 동일 요청을 다시 시도할 수 있도록 합니다.

- 실패한 요청의 Prompt / Settings / Seed를 유지
- 사용자 수정 없이 즉시 Retry 가능
- 실패한 요청 자체를 성공한 History Entry처럼 취급하지 않음

### Saved 검색

Saved Block / Preset 데이터가 많아졌을 때 폴더 탐색만으로 찾기 어려울 수 있으므로 Saved 화면에 전체 검색 기능을 제공합니다.

검색 대상 예:

- Saved Block 이름
- Prompt content
- Preset 이름
- Folder 이름

### 생성 비용 표시

NovelAI API 또는 현재 Generation Settings로 정확한 Anlas/생성 비용 계산이 가능하면 Generate 주변에 작은 비용 표시를 제공하는 것을 검토합니다.

정확한 비용을 보장할 수 없는 경우 추정값을 확정값처럼 표시하지 않습니다.

---

## Credential / API Token 보안

NovelAI API Token 등 인증 정보는 일반 설정 JSON이나 평문 파일에 저장하지 않습니다.

플랫폼의 안전한 credential storage를 사용합니다.

- Android: Android Keystore 기반 저장
- iOS 지원 시: Keychain 기반 저장

Backup/Export에도 API Token을 기본 포함하지 않는 방향을 우선합니다. 포함 옵션을 제공할 경우 별도 경고/암호화가 필요합니다.

---

## 공개 / 오픈소스 방향

초기 목표 플랫폼은 **Android**로 한정합니다.

- iOS는 초기 지원 대상에서 제외
- iOS 실기기/디버그 환경이 없는 상태에서 무리하게 동시 지원하지 않음
- Android 쪽 완성도와 실제 사용성을 우선
- 이후 필요 시 외부 기여자가 iOS 포팅 또는 별도 클라이언트를 만들 수 있도록 데이터 구조와 API 계층을 지나치게 폐쇄적으로 만들지 않음

프로젝트는 오픈소스로 공개하는 방향을 기본안으로 합니다.

권장 기본 정책:

- 앱 소스에는 NovelAI 계정 Token/API Token을 포함하지 않음
- 사용자가 자신의 Token을 직접 입력하여 사용
- Token은 Android Keystore 등 안전한 저장소에 보관
- 애매한 라이선스의 외부 한국어 Tag DB를 앱에 무단 번들하지 않음
- 사용한 외부 Tag 데이터의 출처/라이선스를 README 또는 별도 NOTICE에 명시
- 앱 자체에서 생성한 한국어 번역/의미 카테고리 데이터는 별도 파일/디렉터리로 관리하여 개선 및 PR이 쉽도록 구성
- NovelAI/Anlatan 공식 앱처럼 오인되지 않도록 `비공식 서드파티 클라이언트`임을 명확히 표시
- NovelAI/Anlatan의 상표/로고를 앱 아이콘이나 브랜딩에 임의 사용하지 않음

프로젝트 라이선스는 특별한 제약이 없다면 **MIT License**를 1차 후보로 사용합니다. 단, 번들되는 외부 데이터는 각 원본 데이터 라이선스를 별도로 따릅니다.

### 권장 Repository 구조

```text
NAI-Mobile/
├─ app/                       # Android application
├─ docs/
│  ├─ NAI_APP_NOTES.md        # 제품 요구사항 / 현재 기준 문서
│  └─ references/             # UI 캡처, 참고 이미지 등
├─ data/
│  ├─ tags/                   # 재배포 가능한 원본 Tag DB
│  └─ translations_ko/        # 한국어 번역 / app_category 결과
├─ tools/
│  └─ tag_translation/        # Tag 번역/분류/검증/병합 도구
├─ AGENTS.md                  # Codex 프로젝트 작업 규칙
├─ README.md
├─ LICENSE
└─ .gitignore
```

Tag 번역/분류 파이프라인은 앱 본체와 분리합니다. 이렇게 하면 앱 코드 변경 없이 번역 DB만 재생성하거나 외부 기여자가 번역 개선 PR을 보낼 수 있습니다.

### UI 문자열 / 다국어 확장성

초기 사용자는 한국어를 우선하지만, UI 문자열을 Kotlin/Compose 코드에 직접 하드코딩하지 않고 Android string resource로 분리합니다.

권장 구조:

```text
app/src/main/res/
├─ values/
│  └─ strings.xml        # 기본/Fallback 문자열(영어 권장)
└─ values-ko/
   └─ strings.xml        # 한국어 UI
```

기본 방향:

- 모든 사용자 노출 UI 문구는 `strings.xml` resource ID를 통해 사용
- 한국어 UI는 `values-ko/strings.xml`에서 관리
- 오픈소스 확장성을 위해 기본 `values/strings.xml`은 영어 fallback을 두는 방향 권장
- 초기 영어 문구의 완성도는 한국어보다 낮아도 되며, 외부 기여자가 다른 언어 resource를 추가하기 쉽게 구조만 처음부터 유지
- 새 언어는 `values-ja`, `values-de` 등 Android 표준 locale resource 추가만으로 확장 가능하도록 구성
- Prompt/Tag canonical English 값과 UI 표시 언어는 서로 분리
- Tag Dictionary의 한국어 번역 DB 역시 UI localization과 별개 레이어로 유지

README는 최소한의 영어 설명을 제공하여 외국 사용자가 프로젝트 목적, 설치 방법, 비공식 서드파티 클라이언트라는 점, 번역 기여 방법을 이해할 수 있도록 합니다.

언어 지원 자체를 프로젝트 핵심 목표로 삼지는 않습니다. 번역 요청이 프로젝트 방향과 맞지 않으면 외부 PR/포크로 처리할 수 있도록 구조적 확장성만 확보합니다.

---

## 권장 Android 기술 스택

초기 구현은 Android Native를 기본안으로 합니다.

- Kotlin
- Jetpack Compose
- Room (SQLite)
- DataStore
- OkHttp 또는 Retrofit
- Android Keystore
- Coil 등 이미지 로딩 라이브러리

Flutter/React Native 등 Cross-platform 전환은 iOS 지원 필요성이 실제로 생겼을 때 다시 검토합니다. 초기 버전에서는 Android 파일 접근, 이미지 저장/metadata, Keystore, Import/Export, 실기기 검증을 단순하게 유지하는 것을 우선합니다.

---

## Codex 중심 개발 준비

개발 시작 시 최소 준비물:

1. 최신 Android Studio
2. Android SDK / Emulator
3. Git
4. 빈 Git Repository
5. `docs/NAI_APP_NOTES.md`
6. UI 참고 이미지가 있다면 `docs/references/`에 보관
7. NovelAI 테스트용 개인 계정 및 Token
8. 가능하면 실제 Android 기기 + USB Debugging
9. 초기 Tag DB 원본 파일

NovelAI Token은 Repository, `AGENTS.md`, 문서에 직접 기록하지 않고 로컬 secret/secure storage를 통해 사용합니다.

### AGENTS.md에 넣을 기본 원칙

예:

```text
- NAI_APP_NOTES.md를 기능 요구사항의 기준으로 사용한다.
- Android Native / Kotlin / Jetpack Compose를 기본으로 한다.
- 임의로 요구사항을 삭제하거나 단순화하지 않는다.
- 모바일 화면 밀도와 실제 손가락 조작성을 우선한다.
- 기능 구현 후 반드시 build/test를 수행한다.
- 데이터 모델 변경 시 History/Preset/Backup 호환성을 검토한다.
- 앱의 Domain Model과 NovelAI API DTO를 분리하고, API 요청/응답 구조를 Room Entity 또는 UI State로 직접 사용하지 않는다.
- 데이터 모델을 확정하기 전에 NovelAI API에 종속되는 필드를 식별하고, 확인되지 않은 API 구조를 추측해서 schema에 고정하지 않는다.
- API Token과 credential을 source/config 평문에 저장하지 않는다.
- 사용자 노출 UI 문자열을 Kotlin/Compose에 하드코딩하지 않고 Android string resource로 관리한다.
- Experimental Token Estimator는 참고용 Beta 기능이며 생성 요청을 차단하거나 Prompt를 임의 수정하지 않는다.
- NovelAI API 동작은 추정으로 고정하지 말고 공식 문서/실제 요청을 확인한다.
- 외부 데이터는 라이선스/출처를 확인한 뒤 포함한다.
```

Codex에게 전체 앱을 한 번에 완성하라고 던지기보다는, 아래 Phase 단위로 구현/빌드/검증 후 다음 단계로 진행하도록 합니다.

### Domain Model / API DTO 분리 원칙

앱 내부 편집 구조와 NovelAI 실제 API payload 구조는 같은 것으로 취급하지 않습니다.

```text
App Domain Model
(Session / Base / Character / Block / Settings)
        ↓
NaiRequestMapper / NaiResponseMapper
        ↓
NovelAI API DTO
```

- Block, Locked, Collapsed 등 앱 UX를 위한 상태는 API 구조와 독립적으로 설계합니다.
- NovelAI API의 JSON 필드 구조를 Room Entity나 Compose UI State에 직접 고정하지 않습니다.
- API 변경 시 Mapper/DTO 계층을 중심으로 수정할 수 있도록 경계를 유지합니다.
- 확인되지 않은 API 필드는 TODO/unknown으로 남기고 실제 요청을 확인하기 전 추측 구현하지 않습니다.

### Prompt 처리 모듈 원칙

`##` 주석, `:: ... ::` 가중치, formatter, Block 결합 및 validation은 Compose UI/ViewModel에 흩어놓지 않고 **순수 함수 중심 PromptProcessor 계층**으로 분리합니다.

권장 예:

```text
PromptProcessor
- stripComments()
- normalizeWeightClosings()
- formatMultiline()
- formatSingleLine()
- joinEnabledBlocks()
- validateWeights()
```

- Prompt 처리 로직은 가능한 한 Android framework에 의존하지 않게 구현합니다.
- edge case를 JVM unit test로 빠르게 검증합니다.
- 최소 테스트 벡터에 닫힌/열린 `##`, 가중치 내부 주석, 홀수 `::`, 숫자 뒤 닫는 `::`, 쉼표/줄바꿈 formatter를 포함합니다.

---

## 개발 Roadmap / Phase

### Phase 0 - Repository / 기본 골격

목표:

- Git Repository 생성
- Android 프로젝트 생성
- README / LICENSE / AGENTS.md 준비
- Android string resource 기반 다국어 구조(`values` / `values-ko`) 준비
- 사용자 노출 문자열의 코드 하드코딩 금지 원칙 적용
- 기본 패키지 구조 결정
- CI 또는 최소 local build 명령 정리
- 앱 실행 및 빈 화면 빌드 성공 확인

완료 기준:

- clean checkout 후 build 가능
- Emulator 또는 실기기에서 앱 실행 가능

### Phase 0.5 - NovelAI API Spike

목표는 앱 UI 구현이 아니라 **NovelAI V5 실제 요청 구조를 최소 비용으로 검증하여 이후 schema/UI가 추측에 의존하지 않게 하는 것**입니다.

Python / curl / Kotlin JVM 등 가장 빠르게 검증 가능한 방법을 사용하며 Android UI와 결합하지 않습니다.

확인 순서:

1. Persistent API Token 인증
2. 최소 Prompt로 V5 이미지 1장 생성
3. Base Positive / Negative
4. Character 1명 Positive / Negative
5. Character 2명 및 순서
6. Character 타입이 실제 API에 영향을 주는지 확인
7. Character Positioning parameter 확인
8. Fixed Seed 재현 동작 확인
9. 주요 Generation Settings 실제 parameter 확인
10. 가능한 범위에서 NovelAI 웹과 동일 설정 결과 비교

산출물:

```text
docs/api/
- NAI_V5_API_SPIKE.md
- generate_base.json
- generate_1_character.json
- generate_2_characters.json
- generate_negative.json
- generate_positioned.json
```

원칙:

- Token/credential/account 정보는 샘플에서 반드시 제거
- 공식 문서에서 확인된 내용과 실제 요청 관찰 결과를 구분해 기록
- 확인되지 않은 API 동작을 추측해 샘플 payload로 고정하지 않음
- 이후 Phase의 NovelAI API DTO/Mapper 구현은 이 Spike 결과를 기준으로 함
- 앱 Domain Model을 API payload에 억지로 맞추지 않고 Mapper 경계에서 변환

완료 기준:

- 최소 이미지 생성 성공
- Base / multi-character / negative / seed / positioning 중 실제 지원 구조를 가능한 범위에서 확인
- secret이 제거된 재현 가능한 요청 샘플과 조사 메모가 `docs/api/`에 남아 있음

### Phase 1 - Data Model / Navigation / Local Persistence

구현:

- Session
- Base
- Character
- Block
- SavedBlock
- Preset
- HistoryEntry
- Stash
- Settings
- Tag / Translation / User Override 관련 Entity
- Room schema
- History/Preset의 versioned Snapshot DTO 및 `snapshotVersion + snapshotJson` 저장 구조
- NovelAI API 종속 필드는 Phase 0.5에서 확인된 범위만 schema/DTO에 반영
- DataStore 설정
- Generate / History / Saved / Settings 하단 Navigation
- Last Session 자동 저장/복원

이 단계에서는 NovelAI API 없이도 앱 구조가 동작하도록 합니다.

### Phase 2 - Generate Editor

구현:

- Base / Character 구조
- Positive / Negative 전환
- Character 추가/삭제/순서 변경
- Block 추가/삭제/이름/ON-OFF/Locked/Collapsed/순서 변경
- `##` Comment 처리
- `한 줄씩` / `합쳐서` Formatter
- `::` Warning / 숫자 보정 / Syntax Highlight
- Phase 0.5에서 확인된 API 구조를 기준으로 Character Positioning UI 구현
- Generation Settings UI
- Seed Random/Fixed

완료 기준:

- API 없이도 편집 상태가 완전히 저장/복원됨
- Block 결합 결과를 개발용 Preview/Log로 검증 가능

### Phase 3 - NovelAI API Integration

Phase 0.5 API Spike의 문서/샘플을 기준으로 Android 앱에 실제 NovelAI 연동을 구현합니다. Spike 이후 새로 확인이 필요한 항목만 추가 조사하며, 기존 결과와 충돌하면 `docs/api/` 기록을 먼저 갱신합니다.

확인/구현:

- 인증 방식
- 모델 목록 및 Generation Settings
- Base Positive / Negative
- Character Positive / Negative
- Character 순서
- Character Positioning
- Seed
- 이미지 생성 요청/응답
- 생성 비용(정확히 계산 가능할 경우)
- API 오류 처리 / Retry
- 생성 원본 저장
- Generate 화면 Latest Result Preview
- 같은 설정 + 새 Seed 재생성

중요:

앱 Block 구조를 없앤 최종 payload가 NovelAI 웹에서 같은 Prompt/설정을 입력한 것과 가능한 한 동일하게 동작하는지 실제 비교 테스트합니다.

### Milestone A - First Usable Build

Phase 0 ~ Phase 3의 모든 부가기능을 완벽하게 끝내는 것보다 아래 실제 사용 흐름이 먼저 동작하는 것을 첫 milestone으로 봅니다.

```text
앱 실행
→ Last Session 복원
→ Base / Character Prompt 수정
→ 기본 Generation Settings / Seed 설정
→ Generate
→ 결과 이미지 확인 및 원본 저장
→ 앱 종료/재실행
→ 편집 Session 유지
```

이 흐름이 실기기에서 안정적으로 동작하면 **첫 usable build**로 간주합니다. Syntax Highlight/고급 formatter 등 보조 기능이 일부 남아 있어도 핵심 생성 흐름을 우선 검증할 수 있습니다.

후속 milestone:

- **Milestone B:** History + Saved Block + Preset + 1-slot Stash
- **Milestone B.5:** 확장 가능한 모바일 UI / 정보 구조 개편
- **Milestone C:** Autocomplete
- **Later:** Tag Dictionary / 한국어 번역 DB / Translation Pipeline / Backup 고도화 / Experimental Token Estimator

### Phase 4 - History / Saved / Preset / Stash

구현:

- History Snapshot
- Thumbnail
- 원본 path 추적
- History 최대 개수 설정
- 원본 삭제/이동 감지
- History 부분 복원(Settings/Base/Character/Seed)
- 직전 생성과 동일 설정 Warning
- Saved Block / Folder
- Saved 검색
- Preset 전체 상태 저장/복원
- 1-slot Stash swap

### Phase 4.5 - 모바일 UI / 정보 구조 개편

Tag DB와 자동완성처럼 화면 밀도를 크게 높이는 기능을 추가하기 전에 현재 UI 구조를 정리합니다. 단순한 색상/장식 변경이 아니라, 이후 기능 확장을 감당할 수 있는 Navigation과 공통 interaction 구조를 만드는 단계입니다.

구현 및 검토:

- Generate의 `Editor / Result` 전환, 고정 Generate 버튼, Latest Result 영역의 시각적 우선순위 재정리
- Base / Character / Block의 제목, 상태, 순서 이동, 접기, 저장/불러오기 동작을 일관된 toolbar 규칙으로 통합
- 화면 폭이 좁아도 아이콘이 과밀해지지 않도록 핵심 action과 overflow action 구분
- Block 편집창 높이, 카드 padding, Section 간격과 typography scale을 실제 휴대폰 기준으로 조정
- `Blocks / 세트 / 전체 Preset`의 저장 범위와 명칭을 명확히 구분
- Saved의 Folder 탐색, 검색, filter, 이동, 삭제 확인을 공통 패턴으로 정리
- 저장/불러오기 workflow에서는 일반 하단 Navigation을 숨기고 작업 목적과 취소/완료를 명확히 표시
- Tag Dictionary/Autocomplete가 추가될 자리를 미리 확보하되 Phase 4.5에서 임시 버튼을 과도하게 추가하지 않음
- 빈 상태, loading, 오류, 저장 완료 feedback과 Snackbar 규칙 통일
- 키보드 표시, focus 이동, 뒤로가기, 스크롤 위치 복원 검증
- 아이콘 의미, 최소 48dp touch target, TalkBack content description, 색 대비 점검
- 한국어와 영어 문자열 길이가 달라도 잘리지 않는 responsive layout
- 공통 Compose component 및 design token 정리로 화면별 중복 UI 감소

완료 기준:

- 360dp 폭 휴대폰에서 핵심 action이 겹치거나 잘리지 않음
- Base/Character/Block/세트/Preset의 저장 범위를 label만 보고 구분 가능
- 저장 항목이 많아져도 Folder + 검색으로 원하는 항목에 접근 가능
- Tag 자동완성과 Tag Dictionary 진입점을 추가해도 기존 toolbar를 다시 전면 재작성하지 않아도 됨
- 기존 Prompt 편집, autosave, generation, History 복원 동작 및 unit test가 유지됨

### Phase 5 - Autocomplete

구현:

- NAI Tag Suggest API
- Danbooru API Search
- NAI only / DAN only / Both 설정
- cursor 위치 current fragment 교체
- 3글자 이상 Trigger
- debounce / cooldown / short cache
- source provenance 저장

Autocomplete 결과를 Local Tag DB 갱신에도 활용합니다.

### Phase 6 - Tag Dictionary

구현:

- 영어 Tag / 한국어 번역 / alias 검색
- Danbooru 원본 category 보존
- app_category 지원
- Tag source / translation source
- 사용자 번역 수정(User Override)
- `기본 번역으로 되돌리기`
- 사용자 Tag 직접 추가
- Favorite / Recent
- 사용자 Thumbnail
- 미번역 Tag queue
- AI 번역용 Export / 결과 Import
- Prompt Block cursor 기억
- 다중 Tag 선택 Tray
- 선택 순서 유지 후 `가져오기`

### Phase 7 - Tag Translation / Classification Pipeline

앱 개발용 Codex 한도를 대량 번역에 소모하지 않도록 별도 Tool로 구현합니다.

파이프라인 목표:

```text
tag
→ Korean primary translation
→ Korean aliases
→ app_category
→ validation
→ translation DB merge
```

권장 방식:

- Tag DB를 일정 batch로 분할
- 저비용 대량 처리 모델/API 사용
- canonical tag는 절대 수정하지 않음
- JSONL/TSV 등 machine-readable format 사용
- 출력 schema 검증
- 누락/중복/잘못된 tag 자동 검사
- ambiguous tag만 별도 재검증 queue로 분리
- 사용자 수정(User Override)은 pipeline 재생성 시 덮어쓰지 않음

### Phase 8 - Backup / Restore / Settings 마무리

구현:

- 전체 사용자 데이터 Export
- Import/Restore
- Base DB와 User Data 분리
- API Token 기본 Backup 제외
- 저장 폴더 선택
- History 개수
- Autocomplete provider
- Formatter 표시 옵션
- 자동 보정 옵션
- 기타 앱 설정

### Phase 9 - QA / 실사용 개선

반복:

1. Emulator 테스트
2. 실제 Android 기기 테스트
3. NovelAI 웹과 생성 설정 비교
4. 데이터 재실행/복원 테스트
5. 앱 강제 종료/OS kill 후 Last Session 복원 테스트
6. 네트워크 오류/Token 오류/API 오류 테스트
7. 대량 Tag DB 검색 성능 확인
8. Backup → 앱 초기화 → Restore 왕복 테스트
9. UI 밀도/키보드/스크롤/터치 영역 개선
10. 한국어/기본 영어 string resource 누락 및 하드코딩 문자열 점검
11. 오픈소스 공개 전 secret/license 확인
12. Experimental Token Estimator를 실제 구현하는 경우에만 NAI 입력 비교/오차 패턴 점검

완료 후 README에 설치법, Token 설정법, 주요 기능, 데이터 출처/라이선스, Known Issues를 정리합니다.

---

## Codex 작업 방식 권장

각 Phase 시작 시 Codex에게 다음 흐름으로 맡깁니다.

1. `NAI_APP_NOTES.md` 전체 읽기
2. 해당 Phase에 관련된 요구사항 추출
3. 구현 계획 작성
4. 필요한 API/라이브러리 조사 (`docs/api/`의 Spike 결과 우선 확인)
5. 코드 구현
6. build/test 실행
7. 실패 원인 수정
8. 실제 동작 확인 가능한 상태까지 마무리
9. 변경사항과 남은 이슈 요약
10. Git commit

가능하면 한 번의 거대한 작업보다 Phase 또는 기능 단위의 작은 commit을 유지합니다.

첫 지시 예시:

```text
이 저장소의 docs/NAI_APP_NOTES.md를 전체 읽고 이 문서를 제품 요구사항의 기준으로 사용해.
먼저 AGENTS.md와 구현 계획을 정리한 뒤 Phase 0을 구현하고 build/test로 닫아. 그 다음 Phase 0.5 NovelAI API Spike를 수행해 실제 요청 구조와 secret이 제거된 재현 샘플을 docs/api/에 남긴 뒤 Phase 1까지만 진행해.
데이터 모델을 확정하기 전에 NovelAI API에 종속되는 필드를 식별하고, 확인되지 않은 API 구조는 추측해서 schema에 고정하지 마.
앱의 Domain Model과 NovelAI API DTO를 분리하고, API 요청/응답 구조를 Room Entity 또는 UI State로 직접 사용하지 마.
요구사항을 임의로 삭제하거나 축약하지 말고, 결정이 필요한 부분은 TODO로 명확히 남겨.
Android Native/Kotlin/Jetpack Compose를 기본으로 하고, 로컬 데이터는 Room/DataStore를 사용해.
Prompt parser/formatter/validation은 UI에서 분리된 순수 함수 모듈로 구현하고 JVM unit test를 작성해.
구현 후 실제 build와 test를 실행해서 통과할 때까지 수정하고, 마지막에 변경사항/남은 문제를 요약해.
```

프로젝트가 충분히 안정되기 전까지는 Codex가 임의로 다음 Phase를 계속 확장하기보다 각 Phase를 build/test 가능한 상태로 닫은 뒤 진행하는 것을 우선합니다.
