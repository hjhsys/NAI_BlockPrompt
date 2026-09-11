# 미커밋 변경 점검 — 2026-09-05

## 범위

HEAD 대비 미커밋 변경과 직접 의존하는 호출부/모델/DAO만 점검했다. 프로젝트 전체 재리뷰는 수행하지 않았다.
시작 시 수정 27개 + 신규 6개 = 33개 경로. 아래 목록은 이번 보고서 추가 전 상태다.

```text
 M README.md
 M THIRD_PARTY_NOTICES.md
 M app/src/main/java/com/hjhsys/naiblockprompt/NaiBlockPromptApplication.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/data/autocomplete/AutocompleteRepository.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/data/backup/BackupRepository.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/data/generation/GenerationRepository.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/data/local/AppDatabase.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/data/local/dao/AppDaos.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/data/local/entity/Entities.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/data/settings/SettingsRepository.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/data/tags/BundledTagImporter.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/domain/autocomplete/AutocompleteModels.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/domain/generation/NaiRequestMapper.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/domain/model/AppSettings.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/domain/prompt/PromptProcessor.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/ui/MainViewModel.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/ui/NaiBlockPromptApp.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/ui/components/AppTitleBar.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/ui/database/TagDatabaseScreen.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/ui/generate/GeneratePagerScreen.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/ui/generate/GenerateScreen.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/ui/help/HelpScreen.kt
 M app/src/main/java/com/hjhsys/naiblockprompt/ui/library/LibraryScreens.kt
 M app/src/main/res/values-ko/strings.xml
 M app/src/main/res/values/strings.xml
 M app/src/test/java/com/hjhsys/naiblockprompt/domain/prompt/PromptProcessorTest.kt
 M docs/NAI_APP_NOTES.md
?? app/schemas/com.hjhsys.naiblockprompt.data.local.AppDatabase/10.json
?? app/schemas/com.hjhsys.naiblockprompt.data.local.AppDatabase/9.json
?? app/src/main/java/com/hjhsys/naiblockprompt/domain/prompt/PromptTokenEstimator.kt
?? app/src/main/java/com/hjhsys/naiblockprompt/domain/prompt/WildcardResolver.kt
?? app/src/test/java/com/hjhsys/naiblockprompt/domain/prompt/PromptTokenEstimatorTest.kt
?? app/src/test/java/com/hjhsys/naiblockprompt/domain/prompt/WildcardResolverTest.kt
```

## 완결된 변경 (코드 연결 기준)

- Tags 명칭, 조건별 태그 수, 미리보기 자리 고정, Library 상단 배치 및 폴더 선택 UI.
- Generate 공통 생성 버튼, Result/History 액션 라벨, 좌우 전환 색상.
- Wildcard Entity/DAO/Repository/UI/자동완성/요청 변환/History 복원 및 백업 필드 연결.
- 토큰 추정기·테스트·표시 설정·섹션 표시 연결. 정확한 공식 tokenizer는 아님.
- Color Helper 설정/즐겨찾기/횟수/날짜/백업 및 드래그 UI 연결. 신규 설정 기본은 OFF.
- 자동완성 저장의 bundled 보존 및 번들 버전 증가를 통한 재적용.
- 백업 DB 트랜잭션, 폴더/Wildcard 이름 기준 병합, History 원본 연결 유지 및 오류 UI 연결.
- 저장 실패 후 받은 이미지 보존과 메모리 기반 저장 재시도 연결.
- API DTO interface의 끊어진 호출이나 NotImplemented 분기는 빌드에서 발견되지 않음.

## Schema / migration

- Room version 10, migration 8→9 및 9→10 등록됨.
- schema 9/10 JSON은 신규 미추적 파일이므로 코드와 함께 커밋해야 함.
- schema 9 JSON에도 folder 컬럼이 존재함. 중간 개발 빌드의 흔적으로 보이며 9→10은 컬럼 존재를 검사한다.
- 8→9에서 folder 없이 생성한 경우와 folder가 이미 있는 9의 경우 모두 코드상 10으로 연결됨.
- 실제 Android Room migration 테스트는 이번에 실행하지 않음. 컴파일 성공을 migration 실증으로 간주하지 않음.
- Backup version 2에 Wildcard 기본 빈 목록이 있어 기존 version 1 decoding 경로가 유지됨.

## 이번에 실제 수정한 내용

1. 저장 재시도가 sourceSession/Seed만 비교해 다른 Wildcard 결과나 전처리 결과에도 이전 이미지를 재사용할 수 있었음. 최초 PreparedGeneration 전체를 비교하도록 제한.
2. Wildcard 기존 이름으로 rename 시 unique 충돌이 처리되지 않아 앱 종료 가능. 중복 이름과 resolver가 지원하지 않는 호출 이름을 거절하고 오류 dialog 연결. 새 항목/TXT import가 기존 이름을 조용히 덮어쓰는 것도 방지.
3. 백업 직전 비동기 flush 대신 persistCurrent 완료를 기다리도록 변경. 복원 도중 자동저장이 끼어들지 않도록 기존 saveMutex 공유.

## 미완료 / 잔여 위험

- 백업: DB와 DataStore는 하나의 원자적 트랜잭션이 아님. 설정 단계 실패 시 DB만 복원될 수 있음. 생성한 썸네일 파일도 DB 롤백과 함께 정리되지 않음.
- 백업: 새 기기의 실제 왕복, 충돌·저장 공간 부족·손상 파일·기본 DB import 동시 실행은 통합 검증되지 않음. JSON 압축 해제 크기 제한도 없음.
- 저장 재시도는 메모리에만 있으므로 OS 프로세스 종료 후 유지되지 않음. 저장 단계 재시도로 별도 원본 파일이 생길 수 있음.
- Wildcard 편집 UI는 저장 결과를 기다리기 전에 닫힘. 오류 안내는 나오지만 재입력이 필요할 수 있음.
- TXT 읽기는 UI 콜백에서 수행하며 대용량/접근 오류 처리 보완이 남음.
- Color Wheel은 hue/saturation/value를 pointerInput key로 사용하므로 조작 도중 gesture 재시작 가능성이 있음. 기본 OFF이고 이번에는 UI 조작 개선 범위를 확대하지 않음.
- Resolved prompt 하드코딩, settingsSide 미사용 인자 등 정리할 잔여 코드가 있음. 빌드 차단 요소는 아님.
- README 일부 알려진 제한은 직전 수정 이전 상태를 설명함. 공개 전 수정/검증 상태를 구분해 갱신 필요.
- TXT Export, 전용 폴더 관리, 검토 필요 번역 Import 보존 등은 이번 diff 점검에서 새로 구현하지 않음.

## 검증 및 테스트용 배포 판단

- testDebugUnitTest + assembleDebug 실행.
- 기존 JVM 테스트 22개 suite, 78개 테스트가 통과한 것을 확인.
- git diff --check 통과. 새 기능의 실기기 화면/네트워크/기기 이전 검증은 하지 않음.
- 경고: coroutine API opt-in, 불필요 safe call, 일부 deprecated Compose API.
- 친구의 새 설치에서 편집/생성/탐색을 확인하는 제한된 개발 테스트는 가능.
- 중요한 사용자 데이터의 보관·기기 이전·백업 신뢰성까지 보장하는 배포는 아직 권장하지 않음.
- APK: app/build/outputs/apk/debug/app-debug.apk. 커밋 및 외부 배포는 수행하지 않음.
