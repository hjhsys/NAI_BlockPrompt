# NAI_BlockPrompt Development Instructions

## Source of truth
- Read `docs/NAI_APP_NOTES.md` completely before making architectural or functional decisions.
- `docs/NAI_APP_NOTES.md` is the authoritative functional specification.
- Do not silently remove, simplify, or reinterpret documented requirements.
- If implementation details are unspecified, choose a reasonable Android-native solution and document the choice.

## Platform
- Android only.
- Kotlin.
- Jetpack Compose.
- Room for structured local data.
- DataStore for preferences.
- OkHttp / Retrofit for network access.
- Coil for images.
- Android Keystore for NovelAI credentials.

## Product priorities
- Optimize for phone use and compact mobile UI.
- Prompt Blocks are the main differentiating feature.
- Block boundaries are an editing abstraction only; API output must preserve NovelAI-compatible prompt behavior as closely as possible.
- Korean is the primary UI language, but all UI strings must use Android string resources so additional languages can be added without modifying app logic.
- Keep the project friendly to open-source contributions.

## Development rules
- Build and test after meaningful changes.
- Prefer simple maintainable architecture over unnecessary abstraction.
- Do not add iOS or cross-platform compatibility layers.
- Never commit credentials, tokens, generated secrets, or local-only configuration.
- Keep NovelAI API credentials in Android Keystore.
- Experimental features such as token estimation must never block generation.
- Clearly mark estimates or unofficial behavior as experimental/beta in the UI.

## Git
- Keep commits focused and descriptive.
- Do not commit generated build outputs.
- Preserve MIT licensing.