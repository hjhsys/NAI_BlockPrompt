# NAI Block Prompt

An Android-first, phone-focused prompt editor and unofficial third-party client concept for NovelAI image generation. Its central editing abstraction is reusable prompt blocks grouped under Base and Character sections.

> This project is not affiliated with, endorsed by, or an official product of NovelAI or Anlatan.

## Current status

Phase 0 through Phase 5 and the first Phase 6 Tag Dictionary slice are implemented:

- Native Android app written in Kotlin and Jetpack Compose
- Generate, DB placeholder, and Saved bottom navigation with Settings in the common screen menu
- Generate-centered horizontal workspace: Generation Settings ← Generate → History
- English fallback and Korean Android string resources
- Versioned domain snapshots for the current session
- Room storage for sessions, one-slot stash, saved blocks/folders, presets, history, and tag data
- DataStore preferences for app behavior settings
- Automatic creation/restoration of the last editing session
- Phone-oriented Base and multi-Character prompt editor with independent Positive/Negative blocks
- Block naming, enable/disable, lock, collapse, deletion, and ordering behavior
- Pure Kotlin prompt processing for `##` comments, formatter actions, block joining, and `::` weight assistance
- Non-blocking weight warnings and strength-based editor highlighting
- 실제 NovelAI Image Generation 요청, 최신 이미지 preview, 동일 요청 Retry
- Android Keystore 기반 Persistent Token 암호화 저장
- generation 성공 시 원본/thumbnail 및 versioned History snapshot 저장
- History 목록/원본 열기/부분 복원과 동일 설정 새 Seed 적용
- Saved Block 검색 및 Base 복사, 전체 Session Preset 저장/복원
- History/Preset 복원 전 현재 작업을 보존하는 1-slot Stash swap
- 설정 가능한 History 보관 개수(1~100)와 초과 이미지 파일 정리
- 앱 재시작 후에도 유지되는 History 즐겨찾기와 즐겨찾기를 제외한 일반 History limit
- Unlicense Danbooru canonical Tag DB, alias/category/post-count 검색, 즐겨찾기, 최근 사용, 사용자 한국어 번역/별칭 override
- 직전 성공 요청과 Prompt/설정/Seed가 같을 때 중복 생성 확인
- Debounced Room autosave with an immediate lifecycle flush when the app stops
- NovelAI/Danbooru tag autocomplete with cursor-fragment replacement, provider settings, debounce/cooldown, short memory cache, and Room source provenance

공식 Swagger에 model/sampler enum이 없으므로 Generate 화면에는 정확한 API ID를 직접 입력해야 합니다. Character Positioning과 V5 전용 동작은 공식 mapping이 확인될 때까지 의도적으로 제외되어 있습니다.

## Requirements

- Android Studio with JDK 17 or newer supported by the pinned Gradle version
- Android SDK Platform 37
- Android SDK Build Tools 36.0.0
- Minimum supported device: Android 6.0 (API 23)

## Build and test

On Windows PowerShell:

```powershell
./gradlew.bat testDebugUnitTest assembleDebug
```

On macOS or Linux:

```sh
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written under `app/build/outputs/apk/debug/`. Open the repository root in Android Studio to run it on an emulator or physical Android device.

If the Android SDK is not discovered automatically, create an untracked `local.properties` file through Android Studio or set `ANDROID_HOME` in your environment. Do not commit machine-local SDK paths or credentials.

## Project structure

```text
app/src/main/java/com/hjhsys/naiblockprompt/
├─ data/local/       Room database, entities, and DAOs
├─ data/session/     Versioned session persistence
├─ data/settings/    DataStore-backed app preferences
├─ data/network/     NovelAI API DTO와 OkHttp client
├─ data/security/    Android Keystore token storage
├─ data/generation/  Image/History persistence
├─ data/autocomplete/ NovelAI/Danbooru suggestion clients and cache
├─ domain/model/     API-independent editor and settings models
└─ ui/               Compose navigation and screens
```

Product requirements and the development roadmap are maintained in [`docs/NAI_APP_NOTES.md`](docs/NAI_APP_NOTES.md). UI text belongs in Android string resources; contributors can add locales without changing app logic.

NovelAI 생성 전 Settings에서 API/Persistent Token을 등록하세요. Token은 Android Keystore로 암호화되며 source, Room, DataStore, Session JSON에 기록되지 않습니다. 공식 API 조사와 현재 mapping 범위는 [`docs/NAI_IMAGE_API_SPIKE.md`](docs/NAI_IMAGE_API_SPIKE.md)에 정리되어 있습니다.

## Security and data policy

- Never commit NovelAI tokens, credentials, secrets, or local configuration.
- NovelAI credentials use Android Keystore-backed storage.
- API tokens will not be included in backups by default.
- External tag or translation datasets must have a verified redistribution license and attribution before being bundled.

## License

Application source is available under the [MIT License](LICENSE). Bundled third-party datasets retain their respective licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
