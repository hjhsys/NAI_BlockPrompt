# BlockPrompt

Android용 NovelAI 이미지 생성 클라이언트입니다. Base / Character 프롬프트를 재사용 가능한 Block으로 나누어 휴대폰에서 작성하고 관리합니다.

An Android-only Kotlin/Jetpack Compose prompt-block editor and unofficial NovelAI image-generation client. Includes Korean UI and tag translations, with English fallback UI.

> NovelAI 또는 Anlatan의 공식 앱이 아니며 제휴·보증 관계가 없습니다. 이미지 생성에는 본인의 NovelAI 계정과 API Token이 필요합니다.

## 현재 상태와 기능

버전 **1.0**. [GitHub Releases](https://github.com/hjhsys/NAI_BlockPrompt/releases)에서 서명된 APK를 받을 수 있습니다. 비공식 개인 프로젝트이며 중요한 데이터는 별도 백업을 권장합니다.

- **Generate:** Base / Character별 Positive·Negative, Block 추가·정렬·잠금·접기·ON/OFF, Character 전체 접기, Text Rendering, 주석, 가중치 강조·보정, 한 줄씩/합쳐서 정리, Prompt Undo와 빠른 Tag/Chunk 편집.
- **생성:** 모델·해상도·Sampler·Steps·Guidance·Seed 설정, Random/Fixed Seed 빠른 전환, Character Positioning, Image2Image, Vibe Transfer와 로컬 인코딩 캐시, Precise Reference, Inpaint. 지원 범위는 모델에 따라 다릅니다.
- **이미지/History:** PNG 메타데이터 선택 가져오기, 원본 저장, 썸네일·설정·Seed 보존, 부분 복원, 즐겨찾기, 실패 요청 Retry, 원본 유실 안내.
- **Library:** Blocks / Sets / Presets 저장·검색·폴더 분류·불러오기. 이전 작업은 1-slot Stash로 보관합니다.
- **Tags:** 201,273개 canonical 태그와 한국어 번역, 별칭·카테고리 검색, 사용자 번역 override, 즐겨찾기, 사용 기록, 미리보기 이미지, 출처 표시.
- **자동완성:** NovelAI / Danbooru / Both. API 응답에서 확인된 태그를 조회만으로 학습합니다. 에디터의 임의 입력·붙여넣기를 신규 태그로 등록하지 않습니다.
- **Wildcards:** `__name__` 자동완성, 후보 편집, TXT Import, 폴더 이름 분류, Seed 기반 후보 선택, 중첩 참조 보호, History 원문/선택 결과 복원.
- **Color Helper:** 색상환·HSV·HEX, 복사·입력 보조, 색상 즐겨찾기, 사용 횟수·추가 날짜 정렬. DB·색상 바로가기는 각각 드래그해서 옮길 수 있습니다.
- **설정:** 밝게/어둡게/시스템 테마, 한국어·영어 UI, 선택 가능한 실험적 토큰 추정, Help, 사용자 데이터 백업 및 태그 DB 공유.

## 처음 사용하기

1. Releases의 `BlockPrompt-1.0.apk`를 설치하거나 Android Studio에서 직접 빌드합니다.
2. Settings의 NovelAI 연결 항목에 본인의 API/Persistent Token을 등록합니다.
3. Generate에서 모델과 설정을 선택하고 Base / Character 프롬프트를 작성합니다.
4. Generate를 눌러 생성하고 Result / History에서 결과를 확인합니다.
5. 재사용할 Block·Set·Preset은 Library에 저장합니다.

DB·색상 도우미 버튼은 기존 오른쪽 위치에서 시작합니다. 드래그한 위치는 화면 상태로 유지하며, 키보드 및 화면 크기가 바뀌어도 화면 안에 머무릅니다. 장기 설정이나 백업 항목으로 저장하지는 않습니다.

## 데이터와 파일 교환

기본 태그와 한국어 번역은 앱 asset에서 로컬 Room DB에 반영합니다. 사용자 번역·별칭·카테고리는 별도 override로 관리합니다. 출처는 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), 번들 생성은 [tools/tag_db/README.md](tools/tag_db/README.md)를 참고하세요.

AI 번역 내보내기는 지침·카테고리·JSONL을 ZIP으로 제공합니다. Tag 선택 화면에서는 `미번역만`, `선택만`, `미번역 + 선택` 중 범위를 고를 수 있습니다. 미번역 여부는 Base 번역과 사용자 Override를 합친 실제 한국어 표시값을 기준으로 판단하고, 같은 canonical tag는 한 번만 내보냅니다. 개발자용 전체 내보내기는 1,000개씩 분할합니다. 파일을 외부 AI에 전달하고 결과를 앱으로 가져오는 방식입니다.

ZIP 저장 후 클립보드에는 태그 원문 대신 외부 AI에 붙여넣을 짧은 안내문만 복사됩니다. 결과 JSONL/TXT 또는 ZIP을 가져올 수 있습니다. 판단 불가 항목은 검토 보류로 저장합니다. AI가 제안한 제외 항목과 사용자가 직접 제외한 항목은 출처를 보존하며, 필터·다중 선택·사용자 확정/확정 해제·복구 확인으로 관리합니다. 제외 상태는 자동완성과 사전 노출만 제어하며 Prompt 문자열을 임의로 고치지 않습니다.

사용자 태그 DB 공유와 전체 백업은 별개입니다. 전체 백업에는 세션·Stash·Library·History metadata·History/태그 미리보기 썸네일·사용자 태그 수정·Wildcard·색상 설정 등이 포함됩니다. 썸네일은 `backup.json`의 숫자 배열이 아니라 ZIP 내부의 개별 파일로 스트리밍하며 복원 시 앱 내부 썸네일 폴더로 다시 연결합니다. **생성 원본 PNG, NovelAI Token, 기본 태그 asset은 제외**됩니다. 새 기기에서는 Token과 저장 폴더 권한을 다시 설정하고 생성 원본과 입력/레퍼런스 이미지도 별도로 준비해야 합니다.

현재 백업에는 아래 알려진 문제가 있으므로 중요한 원본과 프롬프트를 별도로 보관하고 복원을 검증하세요.

## 알려진 제한

- 백업의 Room 데이터와 DataStore 설정은 단일 트랜잭션이 아닙니다. 복원 실패 시 부분 적용될 수 있으므로 중요한 원본·프롬프트는 별도로 보관하세요. 신뢰할 수 없는 대용량 백업은 가져오지 마세요.
- AI의 판단 불가 번역은 확정값으로 적용하지 않고 보류합니다. 제안 카테고리는 자동으로 생성하지 않습니다.
- 생성 후 저장 재시도용 이미지는 메모리에 보존되므로 프로세스 종료 후에는 유지되지 않습니다.
- 토큰은 공식 계산값이 아닌 실험적 추정치입니다. 표시를 끌 수 있으며 생성을 차단하지 않습니다.
- Anlas 안내는 모든 설정 조합의 총 비용을 보장하지 않습니다.
- Wildcard TXT Export, 전용 폴더 관리, Redo는 미제공입니다. Prompt Undo와 Quick Edit는 제공하지만 모든 Android 기본 텍스트 편집 동작을 대체하지는 않습니다.
- Inpaint는 V5 Full 실기기에서 웹 호환 마스크 전처리·합성 경로를 확인했습니다. V4.5 Full과 다양한 해상도·마스크 모양은 추가 실기기 회귀 검증이 필요합니다.
- 단위 테스트와 별도로 실제 기기의 DB migration·백업 왕복·백그라운드 복원·모델별 생성 검증이 필요합니다.

## 빌드와 테스트

Android Studio, Android SDK Platform 37, 프로젝트 Gradle과 호환되는 JDK가 필요합니다. Android Studio bundled JDK를 권장합니다. 최소 Android API는 23, Kotlin/JVM target은 17입니다.

Windows PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

macOS / Linux:

```sh
./gradlew testDebugUnitTest assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

SDK 위치는 Android Studio의 `local.properties` 또는 `ANDROID_HOME`으로 설정합니다. 공개 배포 APK에는 별도 release signing 설정이 필요합니다. 로컬 설정·서명 키·빌드 결과는 커밋하지 마세요.

Release 빌드는 로컬 환경변수 `BLOCKPROMPT_KEYSTORE`, `BLOCKPROMPT_STORE_PASSWORD`, `BLOCKPROMPT_KEY_ALIAS`, `BLOCKPROMPT_KEY_PASSWORD`를 설정한 후 `assembleRelease`로 생성합니다. 키 형식은 PKCS12입니다. 서명 설정이 없으면 unsigned APK이므로 배포하지 마세요.

**기존 debug 설치에서 전환:** 서명이 달라 release APK로 덮어쓸 수 없습니다. 앱 백업 및 원본·레퍼런스 이미지를 따로 보관한 후 기존 앱을 제거하고 설치·복원하세요. Token과 파일 접근 권한은 다시 설정해야 합니다. 이후 공식 release끼리는 동일 서명키를 사용합니다. 자체 서명은 운영체제의 출처/보안 경고를 없애는 인증이 아닙니다.

## 보안과 개발 문서

Token은 Android Keystore로 암호화하여 로컬에 저장합니다. 생성에 필요한 프롬프트와 이미지는 NovelAI로, 자동완성 검색어는 선택한 제공자(NovelAI/Danbooru)로 전송됩니다. 공개하는 로그·백업·스크린샷에서 인증 정보와 개인 데이터를 제외하세요.

- [최신 제품 요구사항 (Google Docs)](https://docs.google.com/document/d/1MaEP_W8AMXoTPj6aB5DGFnceK7fdPrv9UUyQhv4WPts/edit?usp=sharing)
- [로컬 요구사항 스냅샷](docs/NAI_APP_NOTES.md)
- [NovelAI API 조사 기록](docs/NAI_IMAGE_API_SPIKE.md)
- [NovelAI 이미지 API 연동 실전 가이드](docs/novelai-integration-guide/README.md)
- [1.0 릴리스 노트](docs/releases/1.0.md)
- [태그 번들 생성 도구](tools/tag_db/README.md)
- [개발 규칙](AGENTS.md)

Kotlin / Jetpack Compose / Room / DataStore / OkHttp / Coil / Android Keystore를 사용합니다. `data/`는 저장·통신, `domain/`은 프롬프트 처리와 모델, `ui/`는 화면을 담당합니다. UI 문구는 Android string resources에서 관리합니다.

오픈소스 공개가 지속적인 기능 추가나 번역 수정 요청 처리를 보장하지는 않습니다. 변경 공유 시 재현 절차와 범위를 명확히 하고, 인증 정보·개인 데이터·재배포 권한이 불명확한 자료를 제외해주세요. 기본 번역 수정은 canonical tag를 기준으로 검증하고 기존 사용자 override를 보존해야 합니다. 현재 '번역 수정분만 JSONL로 내보내기' 전용 기능은 없습니다.

## License

앱 소스는 [MIT License](LICENSE)로 공개됩니다. 외부 데이터의 출처와 라이선스는 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)를 참고하세요.
