package com.hjhsys.naiblockprompt.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

internal data class BackupArchiveSource(
    val entryName: String,
    val source: File,
)

internal data class RestoredBackupMedia(
    val historyThumbnails: Map<String, String>,
    val tagThumbnails: Map<String, String>,
    val createdFiles: List<File>,
)

internal object BackupArchiveCodec {
    private const val MAX_MEDIA_ENTRIES = 10_000
    private const val MAX_MEDIA_ENTRY_BYTES = 8L * 1024 * 1024
    private const val MAX_MEDIA_TOTAL_BYTES = 256L * 1024 * 1024

    @OptIn(ExperimentalSerializationApi::class)
    fun <T> encode(
        json: Json,
        metadataEntry: String,
        serializer: SerializationStrategy<T>,
        content: T,
        media: List<BackupArchiveSource> = emptyList(),
    ): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { output ->
            output.putNextEntry(ZipEntry(metadataEntry))
            json.encodeToStream(serializer, content, output)
            output.closeEntry()
            media.forEach { item ->
                if (!item.source.isFile) return@forEach
                output.putNextEntry(ZipEntry(item.entryName))
                item.source.inputStream().buffered().use { input -> input.copyTo(output) }
                output.closeEntry()
            }
        }
    }.toByteArray()

    @OptIn(ExperimentalSerializationApi::class)
    fun <T> decodeMetadata(
        bytes: ByteArray,
        expectedEntry: String,
        json: Json,
        deserializer: DeserializationStrategy<T>,
    ): T {
        ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                if (entry.name == expectedEntry) return json.decodeFromStream(deserializer, input)
            }
        }
        error("Missing $expectedEntry")
    }

    fun extractMedia(
        bytes: ByteArray,
        manifest: BackupMediaManifest,
        historyDirectory: File,
        tagDirectory: File,
    ): RestoredBackupMedia {
        val expected = linkedMapOf<String, MediaTarget>()
        manifest.historyThumbnails.forEach { (id, entry) ->
            requireSafeEntry(entry, "media/history/")
            require(expected.put(entry, MediaTarget(id, historyDirectory, true)) == null) { "Duplicate backup media entry" }
        }
        manifest.tagThumbnails.forEach { (id, entry) ->
            requireSafeEntry(entry, "media/tags/")
            require(expected.put(entry, MediaTarget(id, tagDirectory, false)) == null) { "Duplicate backup media entry" }
        }
        require(expected.size <= MAX_MEDIA_ENTRIES) { "Too many backup media entries" }

        val history = linkedMapOf<String, String>()
        val tags = linkedMapOf<String, String>()
        val created = mutableListOf<File>()
        var totalBytes = 0L
        val seen = mutableSetOf<String>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val target = expected[entry.name] ?: continue
                    require(seen.add(entry.name)) { "Duplicate ZIP media entry" }
                    target.directory.mkdirs()
                    val destination = File(target.directory, "${UUID.randomUUID()}.img")
                    created += destination
                    destination.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var entryBytes = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            entryBytes += count
                            totalBytes += count
                            require(entryBytes <= MAX_MEDIA_ENTRY_BYTES) { "Backup thumbnail is too large" }
                            require(totalBytes <= MAX_MEDIA_TOTAL_BYTES) { "Backup thumbnails are too large" }
                            output.write(buffer, 0, count)
                        }
                    }
                    if (target.history) history[target.id] = destination.absolutePath
                    else tags[target.id] = destination.absolutePath
                }
            }
        } catch (error: Throwable) {
            created.forEach(File::delete)
            throw error
        }
        return RestoredBackupMedia(history, tags, created)
    }

    private fun requireSafeEntry(entry: String, prefix: String) {
        require(entry.startsWith(prefix) && entry.length > prefix.length) { "Invalid backup media entry" }
        require(!entry.contains("..") && '\\' !in entry && !entry.startsWith('/')) { "Unsafe backup media entry" }
    }

    private data class MediaTarget(
        val id: String,
        val directory: File,
        val history: Boolean,
    )
}
