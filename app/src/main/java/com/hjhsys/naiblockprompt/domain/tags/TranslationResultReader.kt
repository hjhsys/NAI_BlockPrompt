package com.hjhsys.naiblockprompt.domain.tags

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** Reads locally, without extracting archive paths. Bounds compressed and expanded input. */
object TranslationResultReader {
    private const val MAX_BYTES = 20 * 1024 * 1024

    fun read(input: InputStream): String {
        val bytes = readBounded(input, MAX_BYTES)
        require(bytes.size <= MAX_BYTES) { "Translation file too large" }
        if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4b.toByte()) {
            return bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        }
        val texts = mutableListOf<String>()
        var expanded = 0
        var count = 0
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= 256) { "Too many archive entries" }
                val content = readBounded(zip, MAX_BYTES - expanded)
                expanded += content.size
                require(expanded <= MAX_BYTES) { "Expanded translation file too large" }
                val name = entry.name.substringAfterLast('/').lowercase()
                if (!entry.isDirectory && name != "categories.json" &&
                    listOf(".jsonl", ".ndjson", ".json", ".txt").any(name::endsWith)) {
                    texts += content.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                }
            }
        }
        require(texts.isNotEmpty()) { "No translation result in ZIP" }
        return texts.joinToString("\n")
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            require(output.size() + size <= limit) { "Translation content too large" }
            output.write(buffer, 0, size)
        }
        return output.toByteArray()
    }
}
