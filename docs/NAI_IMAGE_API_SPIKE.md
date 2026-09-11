# NovelAI Image Generation API Spike (Phase 3)

조사일: 2026-09-01

## 공식 출처

- Image Generation Swagger UI: <https://image.novelai.net/docs/index.html>
- OpenAPI JSON: <https://image.novelai.net/docs/doc.json>
- NovelAI Image Generation Basics: <https://docs.novelai.net/en/image/basics/>

구현은 위 공식 Swagger에 노출된 wire schema만 사용한다. 커뮤니티 예제나 기존 비공식 client의 payload는 근거로 사용하지 않았다.

## 공식 확인 범위

- `POST https://image.novelai.net/ai/generate-image`
- `Authorization: Bearer <Persistent Token>`, `Accept: application/json`
- 성공 JSON의 `images[]`: base64 `image`, `index`, `seed`
- 앱은 `n_samples=1`, `image_format=png`로 원본 이미지 1장만 요청한다.

공식 OpenAPI에는 `POST /ai/generate-image-stream`과 `parameters.stream`의 `sse` / `msgpack`
값도 노출되어 있다. 스트림은 `intermediate`, `final`, `error` 이벤트를 포함하며 최종 저장물은
반드시 `final` 이벤트에서 선택한다. 2026-09-11 Inpaint 실측에서 정상 웹 PNG는
`stream=msgpack`이었으므로 Inpaint는 웹과 동일한 length-prefixed MessagePack 스트림을 해석한다.
일반 생성의 비스트리밍 JSON 경로는 변경하지 않았다.

Token은 Android Keystore AES/GCM key로 암호화하며 encrypted payload는 `noBackupFilesDir`에 저장한다. Room, DataStore, Session JSON, 로그에는 저장하지 않는다.

## Domain → API mapping

| 앱 Domain | API DTO |
|---|---|
| Base Positive | `input`, `parameters.prompt`, `v4_prompt.caption.base_caption` |
| Base Negative | `parameters.negative_prompt`, `v4_negative_prompt.caption.base_caption` |
| Character Positive | Session order 순서의 `v4_prompt.caption.char_captions[].char_caption` |
| Character Negative | 같은 순서의 `v4_negative_prompt.caption.char_captions[].char_caption` |
| Character 순서 | 배열 순서 + `use_order=true` |
| modelId | top-level `model` |
| width / height | `parameters.width` / `parameters.height` |
| samplerId | `parameters.sampler` |
| steps / scale / seed | `parameters.steps` / `scale` / `seed` |

V4/V4.5의 최소 생성 payload에는 `params_version=3`, `action=generate`, `noise_schedule=karras`, `uc`, `sm=false`, `sm_dyn=false`, `dynamic_thresholding=false`를 명시한다. Base Negative는 최신 Swagger의 `negative_prompt`와 V4.5 호환 요청의 `uc`에 동일하게 매핑한다.

enabled Block만 order 순으로 결합하고 `## ... ##` 주석 제거와 설정된 trailing-digit weight normalization을 Generate 직전에 적용한다. Block 경계와 Character type은 API에 보내지 않는다. Random Seed는 요청 생성 시 정하며 성공 응답 seed를 우선해 History에 실제 값을 저장한다.

## Character Positioning 실측 확인 (2026-09-04)

NovelAI 공식 웹에서 같은 캐릭터 배치를 V4.5와 V5로 생성한 PNG metadata를 비교했다.

- V4/V4.5는 공식 문서와 동일한 5×5 grid이며 셀 중심 좌표 `0.1, 0.3, 0.5, 0.7, 0.9`를 사용한다.
- V5는 grid에 고정되지 않은 normalized continuous coordinate를 사용한다.
- Custom일 때 root `parameters.use_coords=true`, `v4_prompt.use_coords=true`, `v4_prompt.use_order=true`이다.
- `v4_prompt.caption.char_captions[].centers[0]` 및 legacy `characterPrompts[].center`에 같은 좌표를 보낸다.
- Negative character captions는 같은 centers와 순서를 유지하지만 `v4_negative_prompt.use_coords=false`이다.
- AI 선택일 때 `use_coords=false`이며 호환용 center `(0.5, 0.5)`를 유지한다.

## Reference 기능 실측 확인 (2026-09-04)

- 공식 외부 API의 Vibe encoding: `POST /ai/encode-vibe`, JSON의 base64 `image` + `information_extracted` + `model`, binary response.
- 공식 외부 API의 Vibe 생성: encoding을 base64로 바꿔 `reference_image_multiple`에 넣고 strength/information 배열과 순서를 맞춘다.
- 공식 외부 API의 Precise Reference: black padding한 규격 이미지를 base64 `director_reference_images`로 전송하고 나머지 `director_reference_*` 배열과 순서를 맞춘다.
- Precise type wire value는 `character&style`, `character`, `style`이다.
- 웹 frontend의 `*_cached`, `cache_secret_key`, multipart pointer는 외부 API DTO에 포함하지 않는다.
- PNG metadata의 Vibe encoding은 원본 이미지와 다른 재사용 표현이며, Precise 원본 이미지는 PNG metadata만으로 복구가 보장되지 않는다.

## 확인되지 않아 제외한 항목

- `action`: Primary API의 공식 OpenAPI enum 및 default 설명에 따라 text-to-image 요청은 `generate`를 전송한다.
- 웹 frontend 전용 reference server cache 동작은 외부 API 구현 범위에서 제외한다.
- V5 전용 동작: 공식 Swagger에서 V5별 mapping을 확정할 수 없어 구현하지 않는다.
- 모델 전환은 Session/Block을 변경하지 않고 Mapper에서 처리한다. V4/V4.5는 `params_version=3`, V5는 `params_version=4`를 선택하며 양쪽 모두 구조화된 `v4_prompt`/`v4_negative_prompt` conditioning을 사용한다. V5에서 아직 지원이 확인되지 않은 부가기능은 Session에는 보존하되 request에서 제외하는 capability 정책을 따른다.
- model/sampler ID enum: Swagger에 없다. 사용자가 입력한 정확한 API ID를 그대로 보내며 임의 default나 변환을 두지 않는다.
- UI에서 지원하지 않는 나머지 parameter는 보내지 않는다.

### V4.5 Advanced 설정 확인 (2026-09-10)

- 공식 OpenAPI의 `RequestParameters`에는 `noise_schedule`, `skip_cfg_above_sigma`(Variety Boost), `dynamic_thresholding`가 존재한다.
- NovelAI 공식 Summer Sampler 공지는 Karras, Exponential, Polyexponential 세 noise schedule과 각각의 용도를 확인해 준다. 앱은 V4.5에서만 이 세 값을 선택하게 하고, 선택값을 `noise_schedule`에 소문자 API 값으로 보낸다.
- V5에서 동일 선택 UI가 지원된다는 공개 근거는 확인하지 못했다. 선택값은 Session에 보존하지만 V5 request에는 안정된 기존 기본값 `karras`만 사용한다.
- Variety Boost의 `skip_cfg_above_sigma` 활성값/default 및 Decrisper와 `dynamic_thresholding`의 정확한 wire mapping은 공개 Swagger가 규정하지 않는다. 인증된 웹 request를 확보하기 전에는 UI나 mapper에 추측값을 추가하지 않는다.

## 연결, 오류, Retry

연결 테스트는 공식 Image API의 `GET /ai/generate-image/suggest-tags`를 사용한다. Swagger가 예시로 명시한 `nai-diffusion-3`과 비민감 prompt를 고정 사용하므로 현재 편집 model 설정과 무관하게 Token의 Image API 접근만 확인한다. 응답은 저장하거나 기록하지 않는다.

`401/403` 인증, `402` 잔여량, `429` 요청 제한, I/O 네트워크, 기타 HTTP API 오류를 구분한다. 실패한 `PreparedGeneration`을 메모리에 보관해 동일 prompt/settings/seed DTO로 Retry한다. 실패는 History에 기록하지 않고 편집 Session을 변경하지 않는다.
