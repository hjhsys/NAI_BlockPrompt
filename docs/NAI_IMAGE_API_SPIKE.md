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

## 확인되지 않아 제외한 항목

- `action`: Primary API의 공식 OpenAPI enum 및 default 설명에 따라 text-to-image 요청은 `generate`를 전송한다.
- Character Positioning: `centers`/`use_coords`의 공식 좌표 mapping은 확인되지 않아 `use_coords=false`를 유지한다. 공식 schema상 Character caption에 `centers`가 존재하며, 실서비스가 생략/빈 배열 요청에 HTTP 500을 반환하므로 좌표가 비활성화된 상태에서 무시되는 중립값 `(0.5, 0.5)` 하나를 호환성 값으로 전송한다. Character 순서 기반 좌표 mapping은 구현하지 않는다.
- V5 전용 동작: 공식 Swagger에서 V5별 mapping을 확정할 수 없어 구현하지 않는다.
- 모델 전환은 Session/Block을 변경하지 않고 Mapper에서 처리한다. V4/V4.5는 `params_version=3`, V5는 `params_version=4`를 선택하며 양쪽 모두 구조화된 `v4_prompt`/`v4_negative_prompt` conditioning을 사용한다. V5에서 아직 지원이 확인되지 않은 부가기능은 Session에는 보존하되 request에서 제외하는 capability 정책을 따른다.
- model/sampler ID enum: Swagger에 없다. 사용자가 입력한 정확한 API ID를 그대로 보내며 임의 default나 변환을 두지 않는다.
- UI에서 지원하지 않는 나머지 parameter는 보내지 않는다.

## 연결, 오류, Retry

연결 테스트는 공식 Image API의 `GET /ai/generate-image/suggest-tags`를 사용한다. Swagger가 예시로 명시한 `nai-diffusion-3`과 비민감 prompt를 고정 사용하므로 현재 편집 model 설정과 무관하게 Token의 Image API 접근만 확인한다. 응답은 저장하거나 기록하지 않는다.

`401/403` 인증, `402` 잔여량, `429` 요청 제한, I/O 네트워크, 기타 HTTP API 오류를 구분한다. 실패한 `PreparedGeneration`을 메모리에 보관해 동일 prompt/settings/seed DTO로 Retry한다. 실패는 History에 기록하지 않고 편집 Session을 변경하지 않는다.
