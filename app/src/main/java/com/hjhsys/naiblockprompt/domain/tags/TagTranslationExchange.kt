package com.hjhsys.naiblockprompt.domain.tags

import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class TagTranslationCandidate(
    val tag: String,
    val sourceCategory: String?,
    val postCount: Long?,
    val korean: String?,
    val appCategory: String?,
    val koreanAliases: String? = null,
)

enum class AiTranslationExportScope {
    MISSING_ONLY,
    SELECTED_ONLY,
    MISSING_AND_SELECTED,
}

object AiTranslationExportSelection {
    fun resolve(
        scope: AiTranslationExportScope,
        missingQueue: List<TagTranslationCandidate>,
        selected: List<TagTranslationCandidate>,
    ): List<TagTranslationCandidate> {
        val candidates = when (scope) {
            AiTranslationExportScope.MISSING_ONLY -> missingQueue.filter { it.isEffectivelyUntranslated() }
            AiTranslationExportScope.SELECTED_ONLY -> selected
            AiTranslationExportScope.MISSING_AND_SELECTED ->
                missingQueue.filter { it.isEffectivelyUntranslated() } + selected
        }
        return LinkedHashMap<String, TagTranslationCandidate>().apply {
            candidates.forEach { candidate -> put(candidate.tag, candidate) }
        }.values.toList()
    }

    private fun TagTranslationCandidate.isEffectivelyUntranslated(): Boolean = korean.isNullOrBlank()
}

enum class TagTranslationStatus(val wireValue: String) {
    TRANSLATED("translated"), UNCHANGED("unchanged"), REVIEW("review"), EXCLUDED_CANDIDATE("excluded_candidate");

    companion object {
        fun fromWire(value: String?): TagTranslationStatus? = entries.firstOrNull { it.wireValue == value }
    }
}

data class TagTranslationRow(
    val tag: String,
    val korean: String?,
    val aliases: List<String>,
    val appCategory: String?,
    val suggestedCategory: String?,
    val needsReview: Boolean,
    val isTypo: Boolean = false,
    val typoReason: String? = null,
    val status: TagTranslationStatus = if (needsReview) TagTranslationStatus.REVIEW else TagTranslationStatus.TRANSLATED,
    val exclusionReasonCode: String? = null,
    val exclusionReasonText: String? = null,
)

data class ParsedTagTranslations(val rows: List<TagTranslationRow>, val invalidLines: Int)

data class ValidatedTagTranslation(val tagId: String, val row: TagTranslationRow, val canDelete: Boolean = false)
data class ValidatedTagExclusion(val tagId: String, val row: TagTranslationRow)
data class TagTranslationImportPreview(
    val validRows: List<ValidatedTagTranslation>,
    val invalidLines: Int,
    val unknownTags: List<String>,
    val newCategories: List<String>,
    val reviewCount: Int,
    val excludedCandidates: List<ValidatedTagExclusion> = emptyList(),
    val unchangedCount: Int = 0,
)
data class TagTranslationExportFile(val fileName: String, val content: ByteArray)

object TagTranslationExchange {
    const val VERSION = 1

    private val sourceCategoryNames = mapOf("0" to "general", "1" to "artist", "3" to "copyright", "4" to "character", "5" to "meta")

    private fun exportCategories(categories: List<String>) =
        categories.map { sourceCategoryNames[it] ?: it }.distinct().sorted()

    fun exportClipboard(candidates: List<TagTranslationCandidate>, categories: List<String>): String = buildString {
        appendLine("Translate only the JSONL rows below and return JSONL only, one object per tag.")
        appendLine("Preserve canonical `tag` exactly; never invent or rename a tag. Keep reasonable existing ko/aliases_ko values.")
        appendLine("Return: tag, status (translated|unchanged|review|excluded_candidate), ko, aliases_ko, app_category.")
        appendLine("For uncertain meanings use status=review instead of guessing. For typo/invalid/noise use status=excluded_candidate plus reason_code (typo|invalid|noise) and a clear reason_text.")
        appendLine("For tags that are or may be Character/Copyright proper names, do not classify or translate from spelling alone: research the work and character first, using the series suffix when present.")
        appendLine("Prefer official Korean localization, publisher/distributor sites, game/anime sites, documentation, and credits. Because original/global and Korean names can differ, cross-check established Korean usage with reliable Korean-language references; Namuwiki may be used as a cross-check, but not instead of a conflicting available official source.")
        appendLine("Use the verified official or commonly established Korean name as ko, keep only useful verified alternatives in aliases_ko, and use review when sources conflict or identification remains uncertain. Never invent a literal translation or arbitrary transliteration.")
        appendLine("Do not include credentials, prompts, paths, commentary, Markdown fences, or rows not present below.")
        appendLine(buildJsonObject {
            put("type", "nai_block_prompt_translation_selection")
            put("version", VERSION)
            putJsonArray("existing_categories") { exportCategories(categories).forEach(::add) }
        })
        candidates.forEach { candidate -> appendLine(candidateJson(candidate)) }
    }

    fun export(candidates: List<TagTranslationCandidate>, categories: List<String>): String = buildString {
        appendLine(buildJsonObject {
            put("type", "nai_block_prompt_translation_batch")
            put("version", VERSION)
            put("task", "Translate tags into Korean and classify them. Return JSONL only. Preserve tag exactly. Fill ko with one primary Korean translation and aliases_ko with useful Korean search aliases. Use an existing category whenever possible. If none fits, leave app_category empty and fill suggested_category. Set needs_review=true when ambiguous.")
            putJsonArray("existing_categories") { exportCategories(categories).forEach(::add) }
        })
        candidates.forEach { candidate ->
            appendLine(candidateJson(candidate))
        }
    }

    private fun candidateJson(candidate: TagTranslationCandidate) = buildJsonObject {
        put("tag", candidate.tag)
        put("status", TagTranslationStatus.TRANSLATED.wireValue)
        put("ko", candidate.korean.orEmpty())
        putJsonArray("aliases_ko") {
            candidate.koreanAliases.orEmpty().split(',').map(String::trim).filter(String::isNotBlank).forEach(::add)
        }
        put("app_category", candidate.appCategory.orEmpty())
        put("suggested_category", "")
        put("needs_review", false)
        put("is_typo", false)
        put("typo_reason", "")
        candidate.sourceCategory?.let { put("source_category", it) }
        candidate.postCount?.let { put("post_count", it) }
    }

    fun exportBundle(candidates: List<TagTranslationCandidate>, categories: List<String>, splitBatches: Boolean = false): ByteArray {
        val definitions = exportCategories(categories).map { categoryDefinition(it) }
        val instructions = """# NAI Block Prompt translation batch

Translate and classify every row in the `tags_to_process*.jsonl` files.

- Return JSONL only and preserve `tag` exactly.
- Preserve `source_category` and `post_count` exactly when present; leave them absent when absent. Never invent source metadata.
- Numeric `source_category` values mean: 0=general, 1=artist, 3=copyright, 4=character, 5=meta. They are source metadata, not app category IDs.
- Choose `app_category` only from the named IDs in categories.json, never a numeric source category. Prefer a specific semantic category when appropriate.
- `status`: translated, unchanged, review, or excluded_candidate.
- `ko`: one primary Korean translation.
- `aliases_ko`: useful Korean search aliases as a JSON array.
- If an existing category fits, put its ID in `app_category` and leave `suggested_category` empty.
- If none fits, leave `app_category` empty and propose one normalized lowercase ID in `suggested_category`.
- If ambiguous, set `needs_review` to true.
- A tag may be misspelled, obsolete, or have zero posts. Do not correct its canonical spelling or invent a confident meaning. Leave uncertain translation/classification empty and set `needs_review` to true.
- If there is evidence of a spelling/concatenation error, set `is_typo` to true and explain briefly in Korean in `typo_reason`; also set `needs_review` to true. Otherwise use false and an empty reason. Zero posts or an unfamiliar proper name alone is NOT evidence of a typo. The user, not the AI, decides deletion.
- Do not add explanations, omit rows, reorder fields meaningfully, or wrap the final file in prose.
- For `excluded_candidate`, leave translation fields empty and include `reason_code` (typo, invalid, or noise) plus a clear `reason_text`. Never change `tag`.
- Tags that are or may be Character/Copyright proper names must not be classified or translated from spelling alone. Research the work and character first, using `(series)` for identification when present.
- Source priority for proper names: official Korean localization, publisher/distributor sites, official game/anime sites, documentation, or credits; then reliable encyclopedic/wiki references. Original/global names and officially used Korean names can differ.
- Cross-check the established Korean form with Korean-language references. Namuwiki may be used to verify common Korean usage, but do not prefer it over a conflicting available official Korean source.
- Use the verified official or commonly established Korean name in `ko`; keep only useful verified alternatives in `aliases_ko`. If sources conflict or identification remains uncertain, return `review`. Never invent a literal title or arbitrary transliteration.
"""
        val manifest = buildJsonObject {
            put("version", VERSION)
            putJsonArray("categories") {
                definitions.forEach { definition -> addJsonObject {
                    put("id", definition.first)
                    put("name_ko", definition.second)
                    put("description", definition.third)
                } }
            }
        }.toString()
        return ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                listOf(
                    "translation_instructions.md" to instructions,
                    "categories.json" to manifest,
                ).forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                val batches = if (splitBatches) candidates.chunked(1_000) else listOf(candidates)
                batches.forEachIndexed { index, batch ->
                    val name = if (splitBatches) "tags_to_process_${(index + 1).toString().padStart(4, '0')}.jsonl" else "tags_to_process.jsonl"
                    val content = export(batch, categories).lineSequence().drop(1).joinToString("\n", postfix = "\n")
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
            bytes.toByteArray()
        }
    }

    private fun categoryDefinition(id: String): Triple<String, String, String> = when (id) {
        "clothes" -> Triple(id, "의상", "의류, 교복, 드레스, 수영복 등")
        "pose" -> Triple(id, "포즈", "신체 자세, 동작, 시선 방향 등")
        "hair" -> Triple(id, "헤어", "머리 모양, 길이, 머리색과 관련된 특징")
        "body" -> Triple(id, "신체", "신체 부위, 체형과 신체 특징")
        "expression" -> Triple(id, "표정", "얼굴 표정, 감정과 시선 표현")
        "accessory" -> Triple(id, "액세서리", "장신구, 소품과 착용 액세서리")
        "background" -> Triple(id, "배경", "장소, 환경과 배경 요소")
        "composition" -> Triple(id, "구도", "카메라, 프레이밍, 시점과 화면 구성")
        "lighting" -> Triple(id, "조명", "광원, 빛의 방향과 종류, 조명 효과")
        "effect" -> Triple(id, "효과", "시각 효과, 후처리와 화면 연출")
        "artist" -> Triple(id, "작가", "작가 또는 화풍 식별 태그")
        "copyright" -> Triple(id, "작품", "작품, 시리즈 또는 프랜차이즈")
        "character" -> Triple(id, "캐릭터", "고유 캐릭터 이름")
        "meta" -> Triple(id, "메타", "매체, 등급, 제작 방식 등 메타 정보")
        "general" -> Triple(id, "일반", "원본 DB의 일반 태그; 가능하면 의미 카테고리를 우선")
        "other" -> Triple(id, "기타", "다른 의미 카테고리에 맞지 않는 태그")
        else -> Triple(id, id, "사용자가 만든 카테고리")
    }

    fun parse(text: String): ParsedTagTranslations {
        val rows = mutableListOf<TagTranslationRow>()
        var invalid = 0
        text.removePrefix("\uFEFF").lineSequence().filter(String::isNotBlank).filterNot { it.trim().startsWith("```") }.forEach { line ->
            val objectValue = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull()
            if (objectValue == null) {
                invalid++
                return@forEach
            }
            if ((objectValue["type"] as? JsonPrimitive)?.contentOrNull != null) return@forEach
            val tag = (objectValue["tag"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (tag.isEmpty()) {
                invalid++
                return@forEach
            }
            val aliasesElement = objectValue["aliases_ko"]
            val aliases = when (aliasesElement) {
                is JsonArray -> aliasesElement.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
                is JsonPrimitive -> aliasesElement.contentOrNull.orEmpty().split(',').map(String::trim).filter(String::isNotBlank)
                else -> emptyList()
            }
            val legacyNeedsReview = (objectValue["needs_review"] as? JsonPrimitive)?.booleanOrNull == true ||
                (objectValue["is_typo"] as? JsonPrimitive)?.booleanOrNull == true
            val statusValue = (objectValue["status"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
            val status = if (statusValue == null) {
                if (legacyNeedsReview) TagTranslationStatus.REVIEW else TagTranslationStatus.TRANSLATED
            } else TagTranslationStatus.fromWire(statusValue)
            val reasonCode = (objectValue["reason_code"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
            val reasonText = (objectValue["reason_text"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
            if (status == null || (status == TagTranslationStatus.EXCLUDED_CANDIDATE && (reasonCode !in setOf("typo", "invalid", "noise") || reasonText == null))) {
                invalid++
                return@forEach
            }
            rows += TagTranslationRow(
                tag = tag,
                korean = (objectValue["ko"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank),
                aliases = aliases,
                appCategory = (objectValue["app_category"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank),
                suggestedCategory = (objectValue["suggested_category"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank),
                needsReview = status == TagTranslationStatus.REVIEW,
                isTypo = (objectValue["is_typo"] as? JsonPrimitive)?.booleanOrNull ?: false,
                typoReason = (objectValue["typo_reason"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank),
                status = status,
                exclusionReasonCode = reasonCode,
                exclusionReasonText = reasonText,
            )
        }
        return ParsedTagTranslations(rows, invalid)
    }
}
