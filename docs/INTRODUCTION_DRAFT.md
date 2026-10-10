# BlockPrompt 인터넷 소개글 초안

아래 글은 커뮤니티, 블로그 또는 개인 게시물에 맞게 길이를 줄여 사용할 수 있는 초안입니다.

## 제목 후보

- NovelAI Prompt를 Block 단위로 편집하는 Android 앱, BlockPrompt
- 휴대폰에서 긴 NovelAI Prompt를 정리하고 재사용하는 앱을 만들었습니다
- Block, 자동완성, History 검색까지: Android용 비공식 NovelAI 클라이언트 BlockPrompt

## 한 줄 소개

BlockPrompt는 긴 NovelAI Prompt를 Base와 Character별 Block으로 나눠 편집하고, 자주 쓰는 Prompt와 생성 결과를 휴대폰에서 다시 찾고 재사용할 수 있게 만든 Android 앱입니다.

## 짧은 소개글

NovelAI로 이미지를 만들다 보면 Prompt가 길어지고, 인물·의상·화풍·품질 태그가 한 덩어리로 섞여 수정하기 어려워집니다. BlockPrompt는 Prompt를 여러 Block으로 나누어 이름을 붙이고, 켜고 끄거나 순서를 바꾸고, 각각의 가중치를 관리할 수 있게 만든 Android 전용 비공식 클라이언트입니다.

Base와 Character의 Positive/Negative Prompt, V5를 포함한 이미지 생성 설정, Image2Image·Vibe Transfer·Precise Reference·Inpaint를 지원합니다. 태그 자동완성, 한국어 태그 사전, 빠른 편집, Wildcard, Library, History와 생성 폴더 검색도 한 앱 안에서 사용할 수 있습니다.

개인적으로 휴대폰에서 NovelAI Prompt를 편집하려고 만든 프로젝트라서, 특히 긴 Prompt에서 커서와 키보드 때문에 화면이 튀지 않는 것, 자주 쓰는 조각을 재사용하는 것, 이전 생성의 Prompt와 설정을 다시 가져오는 흐름에 신경을 썼습니다.

## 기능 소개 목차

### 1. Prompt Block 편집

- Base / Character별 Positive·Negative Prompt
- Block 추가, 이름 지정, 순서 변경, ON/OFF, 잠금, 접기
- Block별 기본/사용자 지정 배율
- 커서 위치에서 Block 분리, 위 Block과 결합
- 한 줄씩 정리, 합쳐서 정리, 주석과 Text Rendering
- Block 단위 Undo/Redo와 이전 작업 Stash

### 2. 휴대폰용 빠른 편집

- 쉼표 단위 Tag/Weight Chunk 선택
- 여러 항목 동시 선택, 이동, 삭제, 주석, 줄바꿈
- 가중치 증감, 길게 눌러 반복, 절댓값을 유지한 부호 반전
- 선택한 태그를 태그 사전에서 확인하거나 다른 태그 삽입
- 긴 Prompt에서도 도구와 자동완성을 상단/하단/유동 위치로 표시

### 3. 태그 사전과 자동완성

- 약 20만 개 canonical 태그와 한국어 번역
- Local / NovelAI / Danbooru 출처 전환
- 한국어 별칭, 카테고리, 즐겨찾기, 최근 사용, 미리보기 이미지
- 사용자 번역 Override와 검색/추천 제외 관리
- 태그를 누르면 현재 Prompt에 바로 삽입

### 4. Wildcard

- `__name__` 형식의 Wildcard 생성과 자동완성
- 이름뿐 아니라 한국어 등 별명으로도 검색
- 후보를 줄 단위로 편집하고 TXT에서 가져오기
- 폴더 생성·분류·이름 변경과 다중 선택 삭제
- 생성할 때마다 후보를 다시 선택하고 중첩 참조를 보호
- History에서 Wildcard 원문 또는 당시 선택 결과 복원

### 5. NovelAI 이미지 생성

- 모델, 해상도, Sampler, Steps, Guidance, Rescale, Noise Schedule, Seed
- Random/Fixed Seed 빠른 전환
- Character Positioning 선택·탭·드래그 배치
- Image2Image, Vibe Transfer, Precise Reference, Inpaint
- 생성 전 실제 전송 Prompt 미리보기
- 실험적 Token/Anlas 참고 표시

### 6. 결과 보기, 비교와 가져오기

- 이미지 탭으로 전체 화면 확대와 좌우 이동
- Prompt와 생성 설정 확인
- 이미지 A/B 비교와 Prompt·설정 비교
- 결과 이미지를 Image2Image 등에 다시 사용
- Base Positive/Negative, Character, 설정, Seed 등을 선택해서 복원
- 전체 Prompt 또는 필요한 Block만 골라 현재 작업에 추가
- 마지막 가져오기 선택을 기억해서 반복 작업 단축

### 7. History와 생성 이미지 갤러리

- 생성 썸네일, Prompt, 설정, Seed 보존
- 기록/즐겨찾기 분리와 각각의 스크롤 위치 기억
- 오래된 일반 기록은 정리하되 즐겨찾기와 원본 파일은 보존
- 생성물 저장 폴더 전체를 오늘/어제/날짜별로 보기
- 태그 전체 일치·일부 일치, 기간, 모델, Sampler와 상세 설정 검색
- 길게 눌러 여러 이미지 선택, 날짜/검색 결과 전체 선택 삭제

### 8. Library와 데이터 관리

- Block / Set / Preset 저장, 검색, 폴더 분류와 미리보기 이미지
- 빈 폴더를 미리 만들어 분류 체계 준비
- 전체 백업과 복원, 사용자 태그 DB 공유
- NovelAI Token은 Android Keystore에 저장

## 사용 흐름 예시

1. 인물, 의상, 화풍, 품질 태그를 각각 Block으로 나눕니다.
2. 태그 자동완성과 빠른 편집으로 Prompt를 조정합니다.
3. Character Prompt와 위치, 모델과 생성 설정을 선택합니다.
4. 생성 전 최종 Prompt를 확인하고 이미지를 생성합니다.
5. 마음에 드는 결과는 즐겨찾기하고, Prompt 일부나 설정만 다음 작업으로 가져옵니다.
6. 나중에는 생성 폴더 갤러리에서 날짜나 태그로 이전 이미지를 찾습니다.

## 게시물에 함께 넣으면 좋은 화면

1. 여러 Block이 보이는 Generate 화면
2. 자동완성과 Quick Edit가 열린 긴 Prompt 화면
3. Character Positioning 화면
4. 태그 사전의 검색 및 한국어 번역 화면
5. Wildcard 편집과 폴더 선택 화면
6. 생성 결과의 이미지/Prompt 비교 화면
7. 날짜별 생성 이미지 갤러리와 상세 검색 화면
8. Library의 Block/Set/Preset 화면

## 설치 안내 문구

- Android 6.0 이상에서 사용할 수 있습니다.
- 이미지 생성에는 본인의 NovelAI 계정과 API/Persistent Token이 필요합니다.
- GitHub Releases에서 서명 APK를 받을 수 있습니다.
- 비공식 개인 프로젝트이며 NovelAI/Anlatan과 제휴하거나 보증받은 앱이 아닙니다.
- 중요한 Prompt와 원본 이미지는 별도로 백업하는 것을 권장합니다.

## 마무리 문구 후보

기능이 많아 보이지만 핵심은 “긴 Prompt를 덩어리째 외우거나 다시 만들지 않고, Block으로 정리해 재사용한다”는 데 있습니다. 휴대폰에서 NovelAI를 자주 사용하고 Prompt가 점점 길어지는 분이라면 한 번 사용해보고, 기기에서 재현되는 문제는 화면과 재현 순서를 함께 알려주세요.
