# NovelAI 이미지 API 연동 실전 가이드

> Android/Kotlin 클라이언트를 실제로 구현하고 테스트하면서 확인한 내용과 실패 사례를 정리한 공개 참고 문서입니다.

- 마지막 갱신: 2026-09-11
- 예제 환경: Kotlin, Jetpack Compose, kotlinx.serialization, OkHttp
- 범위: 이미지 생성, 태그 자동완성, 구독 잔량, Vibe/Precise Reference, Inpaint, 저장과 진단
- 비범위: NovelAI 웹 프런트엔드 복제, 비공개 캐시 프로토콜, 자동화된 대량 생성

이 문서는 NovelAI의 공식 SDK가 아니며 API 안정성을 보증하지 않습니다. 구현 전과 배포 전에는 반드시 최신 공식 문서와 이용약관을 다시 확인하세요.

## 1. 문서에서 사용하는 근거 수준

NovelAI 연동은 공개 OpenAPI, 사용자 문서, 실제 웹 요청, 생성 PNG 메타데이터가 서로 다른 정보를 담습니다. 이들을 같은 수준의 근거로 취급하면 잘못된 필드를 제품 코드에 고정하기 쉽습니다.

| 표시 | 의미 | 사용 원칙 |
|---|---|---|
| **공식** | 공식 OpenAPI 또는 공식 사용자 문서에 명시 | 공개 클라이언트 구현의 우선 근거 |
| **실측** | 인증정보를 제거한 실제 요청/응답으로 확인 | 날짜, 모델, 조건을 함께 기록 |
| **추정** | 증상이나 출력 메타데이터에서 유추 | 제품 기본값이나 스키마로 확정하지 않음 |
| **미확정** | 상충하는 증거가 있거나 재현이 부족 | 기능을 숨기거나 진단 기능으로만 검증 |

가장 중요한 원칙은 다음과 같습니다.

1. 출력 이미지의 메타데이터는 송신 요청 본문이 아닙니다.
2. 웹 프런트엔드의 내부 필드는 공개 API 입력 필드라는 뜻이 아닙니다.
3. 서버가 값을 출력했다고 해서 같은 이름의 값을 요청에 넣을 수 있는 것은 아닙니다.
4. 한 모델에서 동작한 필드를 다른 모델에도 임의로 보내지 않습니다.
5. 미확정 사항은 DTO, Room schema, 사용자 설정에 성급히 고정하지 않습니다.

## 2. 먼저 확인할 공식 자료

- Image API Swagger UI: <https://image.novelai.net/docs/index.html>
- Image API OpenAPI JSON: <https://image.novelai.net/docs/doc.json>
- Primary API Swagger UI: <https://api.novelai.net/docs>
- 이미지 생성 문서: <https://docs.novelai.net/en/image/>
- Inpaint 문서: <https://docs.novelai.net/en/image/inpaint/>
- 이용약관: <https://novelai.net/terms>

OpenAPI JSON은 Swagger 화면보다 변경점을 비교하고 허용 필드를 확인하기 쉽습니다. CI 또는 수동 릴리스 절차에서 이전 사본과 최신 스키마의 차이를 확인하는 것을 권장합니다.

## 3. 권장 아키텍처

API DTO를 앱의 영속 모델이나 UI 상태로 직접 사용하지 마세요.

```text
Compose UI
   ↓ user intent
ViewModel / UI state
   ↓
Domain session ── Prompt processor / capability policy
   ↓
Request mapper
   ↓
API DTO ── OkHttp transport ── NovelAI
   ↓
Response decoder
   ↓
Generated image store + immutable history snapshot
```

역할을 분리하면 다음 변화에 대응하기 쉽습니다.

- API 필드명이나 응답 형식 변경
- 모델별 지원 기능 차이
- UI 설정 구조 변경
- 오래된 History/Preset 복원
- 스트리밍과 비스트리밍 응답의 공존

### 3.1 Domain과 API DTO를 분리해야 하는 이유

예를 들어 앱에는 `Base`, `Character`, `Block`, 활성/비활성, 잠금, 주석 같은 편집 개념이 있을 수 있습니다. NovelAI API에는 이 UI 계층이 그대로 존재하지 않습니다.

- 편집 상태는 Domain snapshot에 보존합니다.
- 활성 Prompt만 생성 직전에 결합합니다.
- 앱 전용 주석은 전송 전에 제거합니다.
- Character 순서와 좌표만 API conditioning 구조로 매핑합니다.
- 최종 API JSON을 History 원본으로 사용하지 않습니다.

## 4. 인증정보 보관과 로그 정책

공식 문서는 서드파티 연동에서 사용자의 Persistent API Token을 요청하고 `Authorization` 헤더에 사용하도록 안내합니다.

```http
Authorization: Bearer <PERSISTENT_API_TOKEN>
```

Android에서는 다음 방식을 권장합니다.

- Android Keystore에서 AES/GCM 키 생성
- 토큰은 암호화한 뒤 앱 전용 저장소에 보관
- 가능하면 `noBackupFilesDir` 사용
- Room, DataStore 일반 설정, Session JSON, History, crash log에 평문 토큰 저장 금지
- 요청/응답 전체를 기본 로그에 출력하지 않음
- `Authorization`, Cookie, recaptcha, cache secret은 진단 내보내기에서 제거
- 전체 Prompt도 기본 진단 로그에서 제외

연결 오류 로그에는 다음 정도만 남기는 편이 안전합니다.

```text
host=image.novelai.net
path=/ai/generate-image
status=400
correlationId=<server-provided-id-if-any>
errorType=Validation
```

## 5. HTTP 클라이언트 기본 설정

이미지 생성은 일반 JSON API보다 오래 걸리고 결과도 큽니다. 연결, 업로드, 다운로드 제한을 분리하세요.

```kotlin
val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.MINUTES)
    .build()
```

위 값은 보편적인 정답이 아니라 이 프로젝트에서 큰 이미지와 Inpaint를 다루기 위해 사용한 예시입니다.

- `connectTimeout`: 서버 연결 수립
- `writeTimeout`: 원본 이미지와 마스크 업로드
- `readTimeout`: 생성 완료 및 스트림 수신

사용자 화면에서는 네트워크 타임아웃과 서버가 반환한 HTTP 오류를 구분하세요. 예를 들어 서버의 HTTP 400 본문에 upstream `i/o timeout`이 포함될 수도 있습니다. 이 경우 앱의 OkHttp read timeout을 늘리는 것만으로 서버 내부 제한이 바뀌지는 않습니다.

## 6. 오류 모델을 먼저 설계하기

최소한 다음 오류는 구분하는 것이 좋습니다.

```kotlin
sealed interface ImageApiFailure {
    data object Authentication : ImageApiFailure       // 401, 403
    data object PaymentRequired : ImageApiFailure      // 402
    data object RateLimited : ImageApiFailure          // 429
    data class Network(val message: String?) : ImageApiFailure
    data class Api(val status: Int, val safeBody: String?) : ImageApiFailure
    data class InvalidResponse(val reason: String?) : ImageApiFailure
}
```

권장 처리:

- 실패한 생성은 History에 성공 항목으로 저장하지 않음
- 같은 요청 재시도용으로 Prompt, 설정, 실제 seed가 고정된 prepared request를 메모리에 보존
- 재시도 시 새 random seed를 만들지 않음
- 429는 자동 반복 재시도로 서버 부하를 만들지 않음
- 오류 본문은 길이를 제한하고 민감정보가 없는지 확인
- 실험적 기능 실패가 기본 생성까지 막지 않도록 격리

## 7. 일반 이미지 생성

공식 Image API에는 다음 경로가 공개되어 있습니다.

```text
POST https://image.novelai.net/ai/generate-image
```

이 프로젝트는 일반 생성에서 다음 형태를 사용합니다.

```http
Content-Type: application/json
Accept: application/json
Authorization: Bearer <TOKEN>
```

개념적인 요청 구조:

```json
{
  "action": "generate",
  "input": "<processed base prompt>",
  "model": "<verified model id>",
  "parameters": {
    "params_version": 3,
    "width": 832,
    "height": 1216,
    "n_samples": 1,
    "sampler": "<verified sampler id>",
    "steps": 28,
    "scale": 5.0,
    "seed": 123456789,
    "prompt": "<processed base prompt>",
    "negative_prompt": "<processed negative prompt>",
    "noise_schedule": "karras",
    "image_format": "png"
  }
}
```

주의:

- 예제 model/sampler/size를 모든 계정과 모든 시점에 지원한다고 가정하지 마세요.
- 모델 ID 목록이 공개 스키마에서 충분히 규정되지 않았다면 앱 내부 capability table을 검증된 값으로만 관리하세요.
- `params_version`은 모델별로 다를 수 있습니다.
- 지원하지 않는 필드를 null이 아닌 임의 기본값으로 보내지 마세요.
- `n_samples=1`부터 구현하면 History와 실패 복구가 단순해집니다.

성공 응답을 JSON으로 받는 경우 `images[]`의 base64 image, index, seed를 처리합니다. History에는 요청 seed보다 응답의 실제 seed를 우선 저장하세요.

## 8. Prompt와 Character 매핑

앱의 Prompt 편집 구조와 NovelAI conditioning 구조는 분리합니다.

권장 처리 순서:

1. 활성 Block만 UI 순서대로 선택
2. 앱 전용 주석 제거
3. 필요한 최소 문법 보정 적용
4. Base Positive/Negative 결합
5. Character Positive/Negative를 같은 순서로 구성
6. positioning capability에 맞춰 좌표 적용
7. 최종 문자열과 구조화 conditioning을 API DTO로 변환

실측 당시 V4/V4.5 Character positioning은 5×5 셀 중심 좌표를 사용했습니다.

```text
0.1, 0.3, 0.5, 0.7, 0.9
```

V5에서는 연속 normalized coordinate가 관찰됐습니다. 이 차이는 UI 선택 모델과 별개로 mapper의 model capability에서 처리해야 합니다.

Character 배열에서는 다음 정합성을 테스트하세요.

- Positive/Negative Character 배열 길이
- 동일한 Character 순서
- 좌표가 있는 모델에서 centers 대응
- AI 배치와 Custom 배치 전환
- 비활성 Character 처리
- 모델 전환 후 숨겨진 설정이 다른 모델 요청에 누출되지 않는지

## 9. Seed와 재시도

Random seed는 요청을 준비하는 순간 한 번만 확정하세요.

```text
편집 상태(Random)
   ↓ Generate
Prepared request(seed=실제 숫자)
   ├─ API 전송
   ├─ 동일 요청 Retry
   └─ 성공 시 History actual seed 저장
```

다음 구현은 피하세요.

- 네트워크 재시도할 때마다 random seed 재생성
- UI에 표시한 seed와 실제 전송 seed 불일치
- 실패 후 Retry에서 현재 편집 중인 Prompt를 다시 읽음
- 응답 seed가 있는데도 요청 seed만 History에 저장

## 10. 태그 자동완성

공식 Image API에는 tag suggestion 경로가 공개되어 있습니다.

```text
GET /ai/generate-image/suggest-tags
```

네트워크 자동완성과 로컬 태그 검색을 같은 debounce/cooldown에 묶지 마세요.

권장 흐름:

```text
입력 변경
   ├─ 즉시: 현재 comma token 계산 → Local DB 검색 → local 결과 표시
   └─ 지연: remote debounce/cooldown → NAI/Danbooru 요청 → merge
```

회귀 방지 포인트:

- request revision 또는 query generation 번호 사용
- 빠르게 query가 바뀌면 이전 local/remote 결과 모두 폐기
- Local 결과는 Remote 지연을 기다리지 않음
- 전체 Prompt를 검색 로그에 남기지 않음
- autocomplete 선택 시 교체하려는 token 경계를 명시적으로 테스트
- provider별 source 표시를 merge 뒤에도 유지

## 11. 구독 잔량

이 프로젝트에서는 다음 경로의 응답에서 구독 잔량을 읽습니다.

```text
GET https://image.novelai.net/user/subscription
```

관찰된 응답에는 `trainingStepsLeft`와 `usage` 계열 값이 포함됩니다. API 응답은 향후 확장될 수 있으므로 JSON decoder에는 unknown key 허용을 고려하세요.

잔량 갱신은 화면 recomposition마다 호출하지 않습니다.

- 앱 시작
- 토큰 변경/연결 확인 성공
- 생성 성공 뒤
- 사용자가 명시적으로 새로고침

같은 공통 state를 여러 화면이 구독하도록 구성하면 중복 API 호출을 막을 수 있습니다.

## 12. Vibe Transfer와 Precise Reference

### 12.1 Vibe encoding

공식 외부 API의 Vibe encoding 경로:

```text
POST /ai/encode-vibe
```

요청은 base64 image, information extracted, model을 포함하고 응답은 binary입니다. 이 binary는 원본 이미지가 아니라 재사용 가능한 encoding 표현입니다.

Vibe가 여러 개인 경우 다음 배열의 길이와 순서를 맞추세요.

- `reference_image_multiple`
- `reference_information_extracted_multiple`
- `reference_strength_multiple`

### 12.2 Precise Reference

Precise Reference는 모델 입력 규격에 맞게 이미지를 letterbox/padding한 뒤 관련 `director_reference_*` 배열을 같은 순서로 구성합니다.

실측한 type wire 값:

```text
character&style
character
style
```

모델이 해당 기능을 지원하지 않을 때는 Session의 사용자 설정은 보존하되 API 요청에서 제외하거나 명시적인 경고를 표시하세요. 조용히 다른 모델이나 다른 기능으로 대체하면 재현성이 무너집니다.

## 13. Inpaint 요청

### 13.1 공식과 실측으로 확인한 구조

실측한 NovelAI 웹 요청은 다음 경로를 사용했습니다.

```text
POST https://image.novelai.net/ai/generate-image-stream
Accept: application/msgpack
Content-Type: multipart/form-data
```

Multipart part:

```text
image   -> 원본 PNG
mask    -> 마스크 PNG
request -> JSON
```

JSON 내부의 `parameters.image`와 `parameters.mask`는 각각 multipart part 이름인 `image`, `mask`를 가리켰습니다.

V4.5 Full과 V5 Full에서 실측한 주요 값:

```json
{
  "action": "infill",
  "model": "<base-model>-inpainting",
  "parameters": {
    "params_version": 4,
    "image": "image",
    "mask": "mask",
    "strength": 0.7,
    "noise": 0,
    "add_original_image": false,
    "inpaintImg2ImgStrength": 1,
    "straight_alpha": true,
    "stream": "msgpack",
    "image_format": "webp"
  }
}
```

이 값들은 2026-09-11 당시의 실측 기록입니다. 특히 `inpaintImg2ImgStrength`는 현재 공식 OpenAPI의 공개 필드 목록과 차이가 생길 수 있으므로 새 구현에서는 최신 스키마와 실제 응답을 다시 검증하세요.

실제 웹 캡처와 이 프로젝트의 최종 구현은 `add_original_image=false`를 사용합니다. NekoAI-JS의 공개 Inpaint 예제는 `true`를 사용하지만 이는 현재 NovelAI 웹의 필수값으로 취급하지 않습니다. 초기 조사에서 `true`가 일부 artifact를 줄이는 듯한 결과도 있었으나 근본 원인은 요청 mask의 8x8 latent-grid 정렬과 응답 matte 합성 누락이었습니다. 아래 14.5절과 15절의 웹 호환 파이프라인을 적용한 뒤 V5 Full 실기기 테스트에서 초록 격자와 굵은 경계가 사라졌습니다. NekoAI-JS는 AGPL-3.0이므로 동작만 참고하고 구현 코드를 MIT 프로젝트에 그대로 복사하지 않습니다.

### 13.2 마스크 형식

이 프로젝트에서 검증한 마스크 조건:

- 원본과 동일한 width/height
- 8-bit RGBA PNG
- 모든 pixel alpha 255
- 검정(0): 유지 영역
- 흰색(255): 재생성 영역
- 전송 직전에는 중간 gray가 없는 binary mask

UI overlay와 API mask는 분리해야 합니다. 보라색/격자/테두리 같은 편집용 표현을 원본 이미지나 전송 마스크에 합성하지 마세요.

전송 전에 검사하면 좋은 조건:

```kotlin
require(mask.width == source.width)
require(mask.height == source.height)
require(mask.any { it == 255 })
require(mask.all { it == 0 || it == 255 })
```

### 13.3 MessagePack 스트림

실측 응답은 4-byte big-endian length prefix 뒤에 MessagePack event가 반복되는 구조였습니다.

```text
[4-byte length][MessagePack event]
[4-byte length][MessagePack event]
...
```

관찰/문서상 이벤트 종류:

- `intermediate`
- `final`
- `error`

최종 이미지는 원칙적으로 `final` 이벤트에서 선택합니다. 다만 event가 여러 장/여러 sample/index를 포함할 가능성을 고려하여 다음 정보를 진단에 기록하세요.

- frame index
- `event_type`
- 모든 key 이름
- sample/index 관련 값
- image byte length와 format
- 동일 type event 개수

MessagePack parser는 다음 marker를 최소한 지원해야 합니다.

- fixmap/fixarray/fixstr
- bin8/bin16/bin32
- str8/str16/str32
- map16/map32, array16/array32
- signed/unsigned integer
- float32/float64
- extension type은 안전하게 skip

중간 프리뷰 이미지를 최종 이미지로 저장하지 않는지 MockWebServer 테스트로 고정하세요.

## 14. Inpaint 초록 격자/경계 artifact: 실제 진단 사례

이 절은 잘못된 결론과 중복 시행착오를 막기 위한 공개 조사 기록입니다. 여러 진단 ZIP과 2026-09-11 당시 NovelAI 웹 클라이언트 번들을 함께 비교했습니다. 결론은 단일 discard 수치를 찾는 것이 아니라, 웹과 같은 **요청 mask 전처리와 응답 합성을 한 쌍으로 구현해야 한다**는 것입니다.

### 14.1 관찰된 증상

- 마스크 안쪽은 실제로 재생성됨
- 결과 파일에 마스크 형태의 녹색/회색 경계와 격자가 남음
- V4.5 Full과 V5 Full 모두 재현
- 같은 원본/마스크를 NovelAI 웹에서 처리하면 깨끗한 결과를 얻음
- 앱 화면 overlay만의 문제가 아니라 앱이 저장한 결과 image bytes에도 artifact가 있음

### 14.2 확인된 정상 항목

- 전송 원본과 마스크의 해상도 일치
- 마스크 polarity: 검정 유지 / 흰색 재생성
- binary RGBA PNG와 완전 불투명 alpha
- 화면 좌표에서 원본 pixel 좌표로의 매핑
- multipart part 이름과 기본 infill 필드
- 서버는 요청을 받아 masked 영역을 실제로 변경함

### 14.3 시도했지만 근본 해결이 아니었던 방법

#### 경계 feather

마스크 경계를 부드럽게 합성하면 seam 일부는 줄지만 이미 생성 layer 내부에 존재하는 격자는 제거하지 못했습니다.

#### 원인 확인 전 마스크 경계 안쪽 discard

24px가량 결과 경계를 버리고 feather하는 실험은 굵은 선을 약화했지만 다음 부작용이 있었습니다.

- 계단 현상 증가
- 청록색 필터처럼 보이는 혼합
- 유효한 생성 영역 손실

한 진단 자료에서는 16px discard + 16px feather가 우연히 좋아 보였지만, 다음의 불규칙한 큰 mask에서는 32px discard에도 굵은 윤곽이 남았습니다. 따라서 고정 discard 방식은 일반화되지 않으며 제품 기본 동작에서 제거했습니다.

#### mask 팽창/축소

artifact 위치와 굵기만 달라지고 격자 자체는 남았습니다. 마스크 morphology만의 문제로 볼 수 없었습니다.

#### 출력 메타데이터를 요청 필드로 재사용

선택된 WebP 메타데이터에서 다음 내부 값이 관찰됐습니다.

```text
extra_passthrough_testing.hide_debug_overlay=false
```

이를 요청에 `true`로 추가했지만 서버가 다음 HTTP 400을 반환했습니다.

```text
Validation error: extra_passthrough_testing is not allowed
```

공식 OpenAPI에도 이 필드는 없습니다. 즉 출력/내부 메타데이터를 공개 입력 schema로 가정한 실패 사례입니다. 해당 변경은 제거했습니다.

### 14.4 전체 stream을 저장한 뒤 확정된 내용

2026-09-11 진단 ZIP에서 전체 raw MessagePack과 모든 image frame을 저장했습니다.

- 총 28 frames
- frame 0~26: `intermediate`, `step_ix=0..26`, 동일 `gen_id`
- frame 27: 유일한 `final`, 동일 `gen_id`
- 앱이 선택한 frame: 27
- 초록색 마스크 윤곽은 intermediate와 raw final image bytes에 이미 존재
- 요청 원본 PNG에는 윤곽이 없음
- 요청 mask는 불투명 binary RGBA PNG이며, 별도로 저장한 NovelAI 웹 mask와 IHDR/채널/색상 조건이 동일

따라서 이 재현 건에서는 잘못된 frame 선택, 앱 저장 후 오염, UI overlay가 요청 원본에 합쳐진 경우, PNG 채널 형식 차이를 제외할 수 있습니다. 다만 PNG의 채널 형식이 같다는 사실만으로 전송 mask가 같지는 않았습니다. 웹의 실제 multipart mask는 모든 8x8 block 내부가 일정했지만 앱 mask 경계는 원본 픽셀 해상도 그대로였습니다.

### 14.5 웹 클라이언트에서 확인한 실제 파이프라인

2026-09-11에 내려받은 NovelAI 웹 이미지 페이지 번들에서 다음 동작을 확인했습니다. 이는 공개 OpenAPI에 설명된 계약이 아니라 웹 구현의 관찰값이므로, 웹 배포가 바뀌면 다시 확인해야 합니다.

요청 mask:

1. 편집 mask를 가로/세로 각각 1/8로 nearest-neighbor 축소
2. alpha 155를 기준으로 binary threshold
3. 서버 전송 크기로 nearest-neighbor 8배 확대
4. 결과적으로 전송 mask의 경계는 8x8 pixel block에 정렬됨

응답 합성용 matte:

1. 위 1/8 binary mask에서 사각 반경 4px dilation
2. nearest-neighbor로 8배 확대
3. 반경 20px stack blur를 2회 적용
4. 이 matte를 서버 final layer의 alpha에 곱함
5. 같은 matte만큼 원본을 지우고 생성 layer를 원본 위에 합성

웹 요청은 이 경로에서 `add_original_image=false`, `straight_alpha=true`를 사용했습니다. `add_original_image=true`나 고정 erosion/discard는 이 동작의 대체물이 아닙니다.

### 14.6 재현 시 보존할 진단 자료

Debug 빌드에서 다음을 한 ZIP에 보존하는 것이 우선입니다.

```text
00-request-summary.txt       민감정보 없는 모델/크기/옵션
01-request-image.png         실제 전송 image part
02-request-mask.png          실제 전송 mask part
03-response.msgpack          전체 HTTP response bytes
frames.txt                   frame 순서/type/key/size/index
frame-000-intermediate.*     image가 있는 모든 frame
frame-001-final.*
...
app-selected-final.*         앱이 선택한 frame
app-composited-final.png     실제 저장 결과
```

Prompt, API token, Cookie, recaptcha, cache secret은 포함하지 않습니다.

이 자료로 다음을 판별할 수 있습니다.

- frame 선택 오류
- sample index 처리 오류
- `straight_alpha` 해석 차이
- 웹 클라이언트의 추가 합성 여부
- 서버가 반환한 final 자체의 artifact

## 15. 합성과 `add_original_image`

공식 사용자 문서는 `Add Original Image`를 켜면 마스크되지 않은 원본 영역을 완성 이미지에 다시 적용한다고 설명합니다. 대신 마스크 주변에 seam이 생길 수 있다고 경고합니다.

클라이언트 합성을 구현한다면 다음 세 입력을 명확히 분리하세요.

```text
source          원본 이미지
generated       서버 생성 layer 또는 완성 이미지
mask/alpha      어느 영역을 어느 비율로 사용할지 결정
```

현재 웹과 맞춘 처리 순서는 다음과 같습니다.

1. 사용자 mask에서 1/8 latent-grid binary mask를 만듦
2. 이를 nearest-neighbor로 원래 크기까지 확대하여 API mask로 전송
3. `add_original_image=false`, `straight_alpha=true`로 final layer를 받음
4. 별도로 1/8 mask에 dilation 4px를 적용하고 8배 확대
5. 반경 20px stack blur를 2회 적용하여 합성 matte를 만듦
6. 서버 layer alpha에 matte를 곱하고 원본과 alpha 합성
7. 최종 결과를 불투명 PNG로 저장

숫자는 출력 해상도에 임의 비례시킨 값이 아니라 웹 파이프라인의 각 좌표계에 있는 값입니다. dilation 4px는 1/8 mask에서, blur 20px는 full-resolution matte에서 적용합니다.

중요:

- 서버 결과가 이미 완성 이미지인지 layer인지 먼저 확인
- alpha가 straight인지 premultiplied인지 확인
- 원본 mask와 feathered composite weight를 별도 파일로 진단
- 합성 전후 pixel diff 제공
- 진단용 대안 composite는 생성 요청 없이 로컬에서 비교
- 웹 동작을 복제할 때 request mask와 composite matte를 혼동하지 않음
- 웹 번들 관찰값은 날짜와 bundle hash를 기록하고 회귀 테스트함

## 16. 이미지 저장과 History

생성 성공 뒤에는 네트워크 응답을 UI 메모리에만 두지 말고 원본 파일을 앱 저장소에 먼저 확정하세요.

History 권장 구조:

```text
HistoryEntry
  id
  createdAt
  imagePath
  thumbnailPath
  model
  snapshotVersion
  snapshotJson
```

원칙:

- History snapshot은 생성 당시 상태의 독립 복사본
- 현재 Session이나 Saved Prompt를 참조하지 않음
- Prompt/Character/모델/설정/실제 seed 보존
- 썸네일을 원본 대용으로 API에 재전송하지 않음
- 원본 파일이 없어도 History metadata와 thumbnail은 안전하게 표시
- PNG text metadata와 앱 snapshot을 서로 대체 가능한 것으로 가정하지 않음
- 오래된 snapshot을 위한 version별 decoder 제공

## 17. Retry와 중복 과금 방지

이미지 생성 실패는 응답을 받지 못했더라도 서버 측 작업이 완료됐을 가능성을 완전히 배제할 수 없습니다. 무제한 자동 재시도는 피하세요.

권장 UX:

- 실패 원인 표시
- 사용자가 누르는 `동일 요청 재시도`
- Prompt/settings/seed가 동일함을 명시
- 생성 버튼 연타 방지
- 진행 중 request cancellation의 의미를 설명
- 앱이 결과 수신에 실패했다고 서버 과금이 반드시 취소됐다고 단정하지 않음

네트워크 장애 뒤 새 seed로 자동 재시도하면 사용자가 같은 결과 재시도를 기대하기 어렵고 비용 추적도 불명확해집니다.

## 18. Debug 진단 기능 설계

공개 앱에서는 재현 자료를 사용자가 직접 내보낼 수 있게 만드는 것이 매우 유용합니다. 단, Debug 빌드 전용 또는 명시적 동의 기반으로 제한하세요.

### 18.1 포함하면 좋은 것

- 앱 버전과 build type
- 모델 ID, action, 이미지 크기
- 민감정보를 제거한 option 목록
- 요청 이미지와 마스크의 hash/크기/format
- 서버 응답 Content-Type/status/correlation ID
- 스트림 frame manifest
- 선택된 image bytes
- 합성 전후 이미지와 alpha/mask/diff
- 실패 stack trace의 exception class

### 18.2 기본적으로 제외할 것

- API token과 Authorization header
- Cookie와 세션 정보
- recaptcha token
- cache secret key
- 전체 Prompt/Negative Prompt
- 사용자가 원하지 않은 원본 이미지
- 로컬 절대 경로와 계정 식별자

원본 이미지를 포함하는 진단 ZIP은 사용자가 직접 확인한 뒤 공유하도록 안내해야 합니다.

### 18.3 파일 이름을 정확하게 짓기

진단 파일명 자체가 잘못된 결론을 유도할 수 있습니다.

- 전체 응답이 아니면 `raw-response`라고 부르지 않음
- decoder가 선택한 파일은 `decoder-selected-final`처럼 표시
- 서버 layer와 앱 composite를 구분
- 추정 field에는 `inferred` 표시

## 19. 흔히 겪는 문제와 점검 순서

| 증상 | 먼저 확인할 것 | 피해야 할 성급한 결론 |
|---|---|---|
| HTTP 401/403 | Persistent token, Bearer prefix, 대상 host | Prompt나 model 문제로 단정 |
| HTTP 402 | 구독/Anlas 상태 | 같은 요청 무한 재시도 |
| HTTP 429 | 사용자 생성 속도, debounce/cooldown | 즉시 병렬 재시도 |
| HTTP 400 validation | 최신 OpenAPI 허용 필드, null/extra field | 웹 bundle 내부 field를 추가 |
| 응답 JSON parse 실패 | Accept와 Content-Type, endpoint | JSON decoder만 느슨하게 변경 |
| 이미지 대신 binary 오류 | ZIP/JSON/SSE/MessagePack 구분 | 모든 응답을 base64로 간주 |
| seed가 달라짐 | prepared request와 response seed | Retry 때 random 재생성 |
| Inpaint 위치 어긋남 | fit 영역, 좌표 정규화, 원본 크기 | mask blur부터 적용 |
| Inpaint 경계/격자 | 전송 source/mask, 모든 stream frame, alpha | 임의 crop/feather로 숨김 |
| 원본 없음 | 실제 file path/URI 권한 | thumbnail을 원본으로 사용 |
| UI 이동 후 stale 결과 | request revision/cancellation | 마지막 응답을 무조건 state에 반영 |

## 20. 테스트 체크리스트

### 20.1 Request mapper 단위 테스트

- 모델별 `params_version`
- 지원/미지원 capability
- Prompt와 Character 순서
- Fixed/Random actual seed
- 일반 생성과 Inpaint의 endpoint/body 차이
- null 필드가 의도치 않게 직렬화되지 않는지
- 토큰이 body에 포함되지 않는지

### 20.2 MockWebServer 통합 테스트

- URL, method, Authorization, Accept
- JSON/multipart Content-Type
- multipart part 이름과 bytes
- 401/402/429/400 mapping
- JSON image decode
- MessagePack intermediate 무시와 final 선택
- error frame 처리
- truncated/invalid frame 안전 실패

### 20.3 이미지 순수 함수 테스트

- source/mask 해상도 불일치 차단
- mask polarity와 binary pixel
- stroke interpolation에 빈틈이 없는지
- PNG color type/bit depth/alpha
- 좌표 clamp
- 합성 weight와 경계 조건
- 세로/가로 이미지 모두 처리

### 20.4 상태 회귀 테스트

- 느린 이전 요청이 새 요청을 덮어쓰지 않음
- 실패는 History를 만들지 않음
- Retry는 동일 prepared request 사용
- History restore가 snapshot 원본을 변경하지 않음
- 모델 전환 뒤 미지원 설정이 요청에 누출되지 않음

## 21. 출시 전 보안/운영 체크리스트

- [ ] Persistent token이 Keystore 기반으로 보호됨
- [ ] 로그, crash report, backup에 token이 없음
- [ ] Prompt가 기본 네트워크 로그에 없음
- [ ] 공식 host만 사용하고 TLS 검증을 우회하지 않음
- [ ] HTTP status별 사용자 안내가 구분됨
- [ ] 429 자동 폭주 재시도가 없음
- [ ] Generate는 명시적인 사용자 동작으로만 시작됨
- [ ] 응답 원본을 저장한 뒤 History를 확정함
- [ ] 모델별 capability table이 최신 실측과 일치함
- [ ] 진단 ZIP 내보내기 전 포함 항목을 사용자가 알 수 있음
- [ ] 최신 공식 OpenAPI와 이용약관을 재확인함

## 22. 구현 과정에서 얻은 핵심 교훈

1. **웹과 비슷하게 보이는 요청보다 증거의 층위를 구분하는 일이 먼저입니다.**
2. **출력 metadata는 요청 schema가 아닙니다.** `extra_passthrough_testing` 400이 대표 사례입니다.
3. **스트림에서 고른 이미지와 HTTP 원본 응답을 구분해야 합니다.** decoder-selected frame을 server raw라고 부르면 조사가 잘못된 방향으로 갑니다.
4. **후처리로 증상을 줄이는 것과 wire protocol 문제를 해결하는 것은 다릅니다.**
5. **Retry는 UI 버튼이 아니라 동일 요청의 재현성 문제입니다.**
6. **로컬 결과와 원격 결과는 서로 다른 지연 경로로 다루는 편이 좋습니다.**
7. **모델별 기능 차이는 UI 조건문보다 capability/mapper에서 통제하는 편이 안전합니다.**
8. **사용자가 내보낼 수 있는 최소 진단 도구가 원격 QA 시간을 크게 줄입니다.**
9. **실패한 실험도 이유와 결과를 남겨야 다음 개발자가 반복하지 않습니다.**

## 23. 이 문서의 갱신 규칙

새로운 사실을 추가할 때 다음 형식을 권장합니다.

```text
날짜:
대상 모델/endpoint:
근거 수준: 공식 | 실측 | 추정 | 미확정
재현 조건:
관찰 결과:
민감정보 제거 여부:
코드 반영 여부:
테스트 결과:
남은 불확실성:
```

해결되지 않은 문제를 해결 완료로 바꾸려면 최소한 다음이 필요합니다.

- 원인을 구분할 수 있는 진단 증거
- 수정 전 재현 테스트 또는 fixture
- 수정 후 unit/integration build 통과
- 가능하면 실제 계정/실기기에서의 확인

기존 실패 기록은 삭제하기보다 `실패/철회` 상태와 이유를 남겨 주세요.

## 24. 관련 프로젝트 문서

- [기존 API 조사 기록](../NAI_IMAGE_API_SPIKE.md)
- [Inpaint 구현 기록](../INPAINT.md)
- [Inpaint 재개 당시 증거 정리](../INPAINT_RESUME_2026-09-06.md)

이 가이드는 외부 개발자가 참고할 수 있는 일반 원칙과 공개 가능한 실측만 담습니다. 제품 고유 요구사항과 작업 우선순위는 프로젝트의 별도 기준 문서에서 관리합니다.
