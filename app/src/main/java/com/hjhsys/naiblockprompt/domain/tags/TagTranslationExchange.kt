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
)

data class TagTranslationRow(
    val tag: String,
    val korean: String?,
    val aliases: List<String>,
    val appCategory: String?,
    val suggestedCategory: String?,
    val needsReview: Boolean,
)

data class ParsedTagTranslations(val rows: List<TagTranslationRow>, val invalidLines: Int)

data class ValidatedTagTranslation(val tagId: String, val row: TagTranslationRow)
data class TagTranslationImportPreview(
    val validRows: List<ValidatedTagTranslation>,
    val invalidLines: Int,
    val unknownTags: List<String>,
    val newCategories: List<String>,
    val reviewCount: Int,
)
data class TagTranslationExportFile(val fileName: String, val content: ByteArray)

object TagTranslationExchange {
    const val VERSION = 1

    fun export(candidates: List<TagTranslationCandidate>, categories: List<String>): String = buildString {
        appendLine(buildJsonObject {
            put("type", "nai_block_prompt_translation_batch")
            put("version", VERSION)
            put("task", "Translate tags into Korean and classify them. Return JSONL only. Preserve tag exactly. Fill ko with one primary Korean translation and aliases_ko with useful Korean search aliases. Use an existing category whenever possible. If none fits, leave app_category empty and fill suggested_category. Set needs_review=true when ambiguous.")
            putJsonArray("existing_categories") { categories.distinct().sorted().forEach(::add) }
        })
        candidates.forEach { candidate ->
            appendLine(buildJsonObject {
                put("tag", candidate.tag)
                put("ko", candidate.korean.orEmpty())
                putJsonArray("aliases_ko") {}
                put("app_category", candidate.appCategory.orEmpty())
                put("suggested_category", "")
                put("needs_review", false)
                candidate.sourceCategory?.let { put("source_category", it) }
                candidate.postCount?.let { put("post_count", it) }
            })
        }
    }

    fun exportBundle(candidates: List<TagTranslationCandidate>, categories: List<String>, splitBatches: Boolean = false): ByteArray {
        val definitions = categories.distinct().sorted().map { categoryDefinition(it) }
        val instructions = """# NAI Block Prompt translation batch

Translate and classify every row in `tags_to_process.jsonl`.

- Return JSONL only and preserve `tag` exactly.
- `ko`: one primary Korean translation.
- `aliases_ko`: useful Korean search aliases as a JSON array.
- If an existing category fits, put its ID in `app_category` and leave `suggested_category` empty.
- If none fits, leave `app_category` empty and propose one normalized lowercase ID in `suggested_category`.
- If ambiguous, set `needs_review` to true.
- Do not add explanations, omit rows, reorder fields meaningfully, or wrap the final file in prose.
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
        text.lineSequence().filter(String::isNotBlank).filterNot { it.trim().startsWith("```") }.forEach { line ->
            val objectValue = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull()
            if (objectValue == null) {
                invalid++
                return@forEach
            }
            if (objectValue["type"]?.jsonPrimitive?.contentOrNull != null) return@forEach
            val tag = objectValue["tag"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (tag.isEmpty()) {
                invalid++
                return@forEach
            }
            val aliasesElement = objectValue["aliases_ko"]
            val aliases = when (aliasesElement) {
                is JsonArray -> aliasesElement.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
                is JsonPrimitive -> aliasesElement.contentOrNull.orEmpty().split(',').map(String::trim).filter(String::isNotBlank)
                else -> emptyList()
            }
            rows += TagTranslationRow(
                tag = tag,
                korean = objectValue["ko"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank),
                aliases = aliases,
                appCategory = objectValue["app_category"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank),
                suggestedCategory = objectValue["suggested_category"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank),
                needsReview = objectValue["needs_review"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }
        return ParsedTagTranslations(rows, invalid)
    }
}
