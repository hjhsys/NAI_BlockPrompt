# Inpaint (2026-09-06)

## 사용

V4.5 Full 또는 V5 Full을 선택하고 결과/History/이미지 가져오기의 `이 이미지로…`에서
인페인트를 선택한다. 현재 입력 이미지가 있으면 교체 확인을 거친다.
브러시/지우개로 영역을 지정한 뒤 완료 또는 생성을 누른다.
현재 세션의 Prompt/Character/Seed/설정이 사용된다. 생성 설정의 마스크 편집으로 재편집한다.

## 실측 요청

로컬 제공 자료 `n4.5 inpaint2.txt`, `n5 inpaint.txt`의 request part를 확인했다.
인증정보와 원본 캡처는 저장소에 포함하지 않는다.

| 항목 | V4.5 Full | V5 Full |
|---|---|---|
| model | nai-diffusion-4-5-full-inpainting | nai-diffusion-5-full-inpainting |
| action | infill | infill |
| params_version | 4 | 4 |
| image / mask | image / mask | image / mask |
| strength / noise | 0.7 / 0 (UI는 Strength만 노출) | 0.7 / 0 (UI는 Strength만 노출) |
| 실측 add_original_image | false | false |
| inpaintImg2ImgStrength | 실제 HAR 기준 1 | 실제 HAR 기준 1 |
| straight_alpha | 실제 HAR 기준 true | 실제 HAR 기준 true |

공식 웹과 같은 생성 경로를 사용하기 위해 Inpaint는 `/ai/generate-image-stream`에
`stream=msgpack`을 보내고 4-byte length-prefixed MessagePack의 `final` 이벤트 이미지만 저장한다. 일반 생성의 기존
`/ai/generate-image` JSON 응답 경로는 유지한다. Inpaint에만 multipart/form-data를 사용한다. PNG part 이름은 `image`, `mask`,
JSON 문자열 part 이름은 `request`이다. JSON image/mask 값은 동일한 part 이름을 참조한다.
캐시 키/웹 캐시 프로토콜은 구현하지 않는다. img2img는 샘플에서 null이며 별도 값을 만들지 않는다.

## 데이터

- 원본 비율에 맞춘 Canvas의 좌표를 정규화하여 원본 픽셀로 변환한다.
- 마스크는 원본과 동일한 해상도의 웹 호환 8-bit 불투명 RGBA PNG: 검정 유지 / 흰색 재생성.
- 실제 웹 HAR에서 확인한 대로 `add_original_image=false`, `inpaintImg2ImgStrength=1`,
  `straight_alpha=true`, `image_format=webp`, `stream=msgpack`을 전송한다.
  편집/저장/전송 마스크는 검정/흰색 원본을 그대로 유지한다.
- 웹 클라이언트처럼 서버의 final infill을 완성 이미지로 바로 저장하지 않는다. 흰 마스크를
  확장하고 feather한 알파를 생성 결과에 적용한 뒤, 원본의 반대 알파와 source-over 합성한다.
  폭은 기준 해상도에 비례시키며 서버 이미지의 기존 알파도 함께 반영한다. 최종 PNG에는
  NovelAI text metadata를 옮긴다.
- 빈 마스크, 해상도 불일치, 비흑백 값은 네트워크 전송 전에 차단한다.
- 원본은 내부 inpaint_sources에 내용 해시로 저장한다. 마스크 PNG Base64는 ImageInputState에
  포함되어 Session/History/Preset/백업 직렬화에서 보존된다. 원본 파일은 기존 백업 정책처럼
  백업에 포함하지 않는다. 다른 기기로 복원 시 원본 누락 안내를 사용한다.
- 기존 snapshot의 mask 필드는 기본 null이므로 Room migration은 추가하지 않았다.
- 결과 저장/History/Retry/오류 표시는 기존 generation 경로를 사용한다.

## 제한

- Full 모델만 실측 확인됨. Curated로 자동 대체하지 않는다.
- 최대 16MP 원본. 서버 자체 해상도/비용 제한은 별개이며 자동 축소하지 않는다.
- 확대/이동, Focused Inpaint, Outpaint는 없음. 초기화는 Undo 대상이 아니다.
- 원본 내부 복사본은 재현성을 위해 유지하며 자동 정리는 구현하지 않았다.
- MessagePack 전환 후 실제 유료 API 생성과 실기기 artifact 비교는 남아 있다. 서버의
  `intermediate` 프리뷰가 아니라 `final` 이미지만 저장되는지는 MockWebServer 테스트로 고정한다.

## 주요 변경 파일

InpaintEditor.kt, MaskGeometry.kt, InpaintRequestMapper.kt, InpaintRequestMapperTest.kt,
SessionModels.kt, NaiImageDtos.kt, OkHttpNaiImageApi.kt, GenerationRepository.kt,
ImageActions.kt, GenerateScreen.kt, LibraryScreens.kt, values/values-ko strings.xml.
