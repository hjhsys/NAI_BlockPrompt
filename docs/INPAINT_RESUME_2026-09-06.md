# Inpaint 재개 확인 (2026-09-06)

## 제공 자료에서 확인한 사실

UI 캡처 2개는 동일한 원본 위에 얼굴 부분을 보라색으로 표시한 편집 화면이다.
독립적인 흑백 mask PNG나 HTTP request JSON/capture는 이번 첨부 목록에 없다.
인증정보는 읽거나 요청하지 않았다.

지정된 원본과 두 결과의 PNG text chunk를 직접 읽었다. 결과는 tEXt가 아니라
비압축 iTXt Comment에 metadata가 들어 있다.

| 항목 | 원본 | V5 결과 | V4.5 결과 |
|---|---|---|---|
| 크기 | 832×1216 | 832×1216 | 832×1216 |
| request_type | PromptGenerateRequest | NativeInfillingRequest | NativeInfillingRequest |
| Source hash | 0ADF9AB7 | 657484A5 | 1229B44F |
| seed | 4118223622 | 4118223622 | 2005448161 |
| steps | 28 | 28 | 28 |
| scale | 5 | 5 | 7 |
| sampler | k_euler_ancestral | k_euler_ancestral | k_euler_ancestral |
| add_original_image | 기록 없음 | false | false |
| img2img | 기록 없음 | null | null |

결과 metadata는 송신 request 자체가 아니다. API model ID, action, image/mask
본체, multipart part 이름은 저장되어 있지 않다. model hash를 API model ID로 취급하지 않는다.
strength/noise가 기록되지 않았다는 것도 값이 0이었다는 뜻은 아니다.

## 外部 API と残りの確認

前ターンの公式 Swagger 調査で action `infill`、parameters `image` / `mask` /
`add_original_image` / `img2img` は確認済み。公式外部 JSON APIを使用する方針を維持し、
web frontendのcache_secret_keyやmultipart pointerは複製しない。

V4.5/V5用inpaint model IDの実測確認は未完了。添付PNGだけから推測して有効化しない。
従って通常Inpaint、Mask Editor、mask snapshot保存/復元、Inpaint actionの公開は未実装。
必要な追加資料は認証ヘッダーを含まないrequest body（modelとparameters）であり、
Authorization/Cookieは不要。

## 継続したコード

前ターンで edge hint単層化、source category正規化とRoom11→12、seed選択、
共通Image Actions、Blockのcomma＋2改行結合が追加済み。
今回、画像を拡大表示したときにも選択seedを更新し、既存Historyからのlast seed fallback、
不正seed/zeroのテストとcategory migrationのユーザー情報保持テストを追加。

共通Image ActionsはGallery/Result/Historyの原本をI2I/Vibe/Preciseへ渡し、
既存入力の置換確認と原本アクセス確認を行う。thumbnailへのfallbackは行わない。
実API有料生成・端末上UI操作は今回実施していない。
