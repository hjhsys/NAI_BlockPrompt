package com.hjhsys.naiblockprompt.domain.image

import com.hjhsys.naiblockprompt.domain.model.CharacterPosition
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream

data class NaiImageMetadata(
    val prompt: String?,
    val negativePrompt: String?,
    val model: String?,
    val seed: Long?,
    val width: Int?,
    val height: Int?,
    val sampler: String?,
    val steps: Int?,
    val scale: Float?,
    val characterPrompts: List<String> = emptyList(),
    val characterNegativePrompts: List<String> = emptyList(),
    val characterPositions: List<CharacterPosition?> = emptyList(),
    val usedExternalImageGuidance: Boolean = false,
)

object NaiPngMetadataParser {
    private val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

    fun parse(bytes: ByteArray): NaiImageMetadata? {
        if (bytes.size < 12 || !bytes.copyOfRange(0, 8).contentEquals(signature)) return null
        val text = linkedMapOf<String, String>()
        var offset = 8
        while (offset + 12 <= bytes.size) {
            val length = readInt(bytes, offset)
            if (length < 0 || offset + 12L + length > bytes.size) break
            val type = bytes.copyOfRange(offset + 4, offset + 8).toString(Charsets.ISO_8859_1)
            val data = bytes.copyOfRange(offset + 8, offset + 8 + length)
            when (type) {
                "tEXt" -> splitAtNull(data)?.let { (key, value) -> text[key] = value.toString(Charsets.ISO_8859_1) }
                "zTXt" -> splitAtNull(data)?.let { (key, value) ->
                    if (value.size > 1) runCatching { InflaterInputStream(ByteArrayInputStream(value, 1, value.size - 1)).readBytes().toString(Charsets.UTF_8) }
                        .getOrNull()?.let { text[key] = it }
                }
                "iTXt" -> parseInternationalText(data)?.let { (key, value) -> text[key] = value }
            }
            offset += length + 12
            if (type == "IEND") break
        }
        val comment = text["Comment"]?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val prompt = text["Description"]?.takeIf(String::isNotBlank) ?: comment.string("prompt")
        val negative = comment.string("uc") ?: comment.string("negative_prompt")
        if (prompt == null && comment == null) return null
        return NaiImageMetadata(
            prompt, negative, text["Source"] ?: comment.string("model"), comment.long("seed"),
            comment.int("width"), comment.int("height"), comment.string("sampler"), comment.int("steps"), comment.float("scale"),
            comment.characterCaptions("v4_prompt"), comment.characterCaptions("v4_negative_prompt"),
            comment.characterPositions(),
            comment.usedExternalImageGuidance(),
        )
    }

    private fun readInt(bytes: ByteArray, offset: Int) =
        ((bytes[offset].toInt() and 255) shl 24) or ((bytes[offset + 1].toInt() and 255) shl 16) or
            ((bytes[offset + 2].toInt() and 255) shl 8) or (bytes[offset + 3].toInt() and 255)

    private fun splitAtNull(data: ByteArray): Pair<String, ByteArray>? {
        val end = data.indexOf(0)
        if (end < 0) return null
        return data.copyOfRange(0, end).toString(Charsets.ISO_8859_1) to data.copyOfRange(end + 1, data.size)
    }

    private fun parseInternationalText(data: ByteArray): Pair<String, String>? {
        val keyEnd = data.indexOf(0); if (keyEnd < 0 || keyEnd + 2 >= data.size) return null
        val compressed = data[keyEnd + 1].toInt() == 1
        var cursor = keyEnd + 3
        repeat(2) { cursor = findNull(data, cursor).takeIf { it >= 0 }?.plus(1) ?: return null }
        val payload = data.copyOfRange(cursor, data.size)
        val value = if (compressed) InflaterInputStream(ByteArrayInputStream(payload)).readBytes() else payload
        return data.copyOfRange(0, keyEnd).toString(Charsets.ISO_8859_1) to value.toString(Charsets.UTF_8)
    }

    private fun findNull(data: ByteArray, start: Int): Int {
        for (index in start until data.size) if (data[index] == 0.toByte()) return index
        return -1
    }

    private fun JsonObject?.string(key: String) = this?.get(key)?.jsonPrimitive?.contentOrNull
    private fun JsonObject?.long(key: String) = this?.get(key)?.jsonPrimitive?.longOrNull
    private fun JsonObject?.int(key: String) = this?.get(key)?.jsonPrimitive?.intOrNull
    private fun JsonObject?.float(key: String) = this?.get(key)?.jsonPrimitive?.floatOrNull
    private fun JsonObject?.characterCaptions(key: String): List<String> = runCatching {
        this?.get(key)?.jsonObject?.get("caption")?.jsonObject?.get("char_captions")?.jsonArray
            ?.mapNotNull { it.jsonObject["char_caption"]?.jsonPrimitive?.contentOrNull }.orEmpty()
    }.getOrDefault(emptyList())

    private fun JsonObject?.characterPositions(): List<CharacterPosition?> = runCatching {
        val prompt = this?.get("v4_prompt")?.jsonObject ?: return emptyList()
        if (prompt["use_coords"]?.jsonPrimitive?.booleanOrNull != true) return emptyList()
        prompt["caption"]?.jsonObject?.get("char_captions")?.jsonArray?.map { caption ->
            caption.jsonObject["centers"]?.jsonArray?.firstOrNull()?.jsonObject?.let { center ->
                val x = center["x"]?.jsonPrimitive?.floatOrNull
                val y = center["y"]?.jsonPrimitive?.floatOrNull
                if (x != null && y != null) CharacterPosition(x, y) else null
            }
        }.orEmpty()
    }.getOrDefault(emptyList())

    private fun JsonObject?.usedExternalImageGuidance(): Boolean {
        if (this == null) return false
        val action = string("action")
        if (action.equals("img2img", ignoreCase = true) || action.equals("infill", ignoreCase = true)) return true

        // NovelAI PNG metadata also contains strength, extraction and seed fields
        // with non-zero defaults when no reference image was used. Only an actual
        // source/reference payload is evidence of external image guidance.
        val payloadKeys = setOf(
            "image",
            "reference_image",
            "reference_image_multiple",
            "reference_image_multiple_cached",
            "director_reference_images",
            "director_reference_images_cached",
        )
        return payloadKeys.any { key -> get(key)?.hasMeaningfulGuidanceValue() == true }
    }

    private fun JsonElement.hasMeaningfulGuidanceValue(): Boolean = when (this) {
        JsonNull -> false
        is JsonPrimitive -> booleanOrNull ?: contentOrNull?.let { it.isNotBlank() && it != "0" } ?: false
        is JsonArray -> any { it.hasMeaningfulGuidanceValue() }
        is JsonObject -> values.any { it.hasMeaningfulGuidanceValue() }
    }
}
