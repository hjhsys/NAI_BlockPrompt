package com.hjhsys.naiblockprompt.data.gallery

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.hjhsys.naiblockprompt.data.generation.GeneratedImageStore
import com.hjhsys.naiblockprompt.data.local.dao.GalleryDao
import com.hjhsys.naiblockprompt.data.local.dao.HistoryDao
import com.hjhsys.naiblockprompt.data.local.entity.GalleryImageEntity
import com.hjhsys.naiblockprompt.domain.image.NaiPngMetadataParser
import com.hjhsys.naiblockprompt.domain.image.NaiImageMetadata
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class GalleryQuery(
    val folderKey: String,
    val startAt: Long = 0L,
    val endAt: Long = Long.MAX_VALUE,
    val tag: String = "",
    val tagMatch: GalleryTagMatch = GalleryTagMatch.EXACT,
    val model: String = "",
    val sampler: String = "",
    val steps: String = "",
    val scale: String = "",
    val guidanceRescale: String = "",
)

data class GalleryScanResult(val scanned: Int, val indexed: Int, val failed: Int)
enum class GalleryPeriod { MONTH, YEAR, ALL, CUSTOM }
enum class GalleryTagMatch { EXACT, PARTIAL }
data class GalleryFilterState(
    val tag: String = "",
    val tagMatch: GalleryTagMatch = GalleryTagMatch.EXACT,
    val period: GalleryPeriod = GalleryPeriod.MONTH,
    val customStartAt: Long = 0L,
    val customEndAt: Long = Long.MAX_VALUE,
    val model: String = "",
    val sampler: String = "",
    val steps: String = "",
    val scale: String = "",
    val guidanceRescale: String = "",
)

object GalleryTagIndex {
    private const val separator = '\u001F'

    fun queryToken(value: String): String = normalize(value).takeIf(String::isNotBlank)
        ?.let { "$separator$it$separator" }.orEmpty()

    fun partialQueryToken(value: String): String = normalize(value)

    fun build(vararg prompts: String?): String {
        val tags = prompts.filterNotNull().flatMap { prompt ->
            prompt.split(',', '\n').mapNotNull { raw ->
                normalize(raw).takeIf(String::isNotBlank)
            }
        }.distinct()
        return tags.joinToString(separator.toString(), prefix = separator.toString(), postfix = separator.toString())
    }

    private fun normalize(value: String): String = value
        .trim()
        .replace('_', ' ')
        .replace(Regex("^[+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)::"), "")
        .replace(Regex("::+$"), "")
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
}

class GeneratedGalleryRepository(
    private val context: Context,
    private val galleryDao: GalleryDao,
    private val historyDao: HistoryDao,
) {
    private val imageStore = GeneratedImageStore(context)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun folderKey(treeUri: String?): String = treeUri?.takeIf(String::isNotBlank) ?: DEFAULT_FOLDER_KEY

    fun images(query: GalleryQuery): Flow<List<GalleryImageEntity>> = galleryDao.observeFiltered(
        query.folderKey,
        query.startAt,
        query.endAt,
        if (query.tagMatch == GalleryTagMatch.EXACT) GalleryTagIndex.queryToken(query.tag)
        else GalleryTagIndex.partialQueryToken(query.tag),
        query.model,
        query.sampler,
        query.steps.toIntOrNull(),
        query.scale.toFloatOrNull(),
        query.guidanceRescale.toFloatOrNull(),
    )

    fun models(folderKey: String) = galleryDao.observeModels(folderKey)
    fun samplers(folderKey: String) = galleryDao.observeSamplers(folderKey)
    fun dateBounds(folderKey: String) = galleryDao.observeDateBounds(folderKey)

    fun metadata(item: GalleryImageEntity): NaiImageMetadata = item.metadataJson
        ?.let { runCatching { json.decodeFromString<NaiImageMetadata>(it) }.getOrNull() }
        ?: NaiImageMetadata(
            prompt = item.prompt, negativePrompt = item.negativePrompt, model = item.model, seed = item.seed,
            width = item.width, height = item.height, sampler = item.sampler, steps = item.steps, scale = item.scale,
            guidanceRescale = item.guidanceRescale, noiseSchedule = item.noiseSchedule,
        )

    suspend fun scan(treeUri: String?): GalleryScanResult = withContext(Dispatchers.IO) {
        val key = folderKey(treeUri)
        val token = System.currentTimeMillis()
        val known = galleryDao.listForFolder(key).associateBy { it.uri }
        val files = if (!treeUri.isNullOrBlank()) listTree(Uri.parse(treeUri)) else listDefaultFolder()
        var indexed = 0
        var failed = 0
        val pending = mutableListOf<GalleryImageEntity>()
        suspend fun queue(entity: GalleryImageEntity) {
            pending += entity
            if (pending.size >= 100) {
                galleryDao.upsertAll(pending.toList())
                pending.clear()
            }
        }
        files.forEach { candidate ->
            val old = known[candidate.uri]
            val needsMetadataUpgrade = candidate.displayName.endsWith(".png", ignoreCase = true) && old?.metadataJson == null
            if (old != null && !needsMetadataUpgrade && old.modifiedAt == candidate.modifiedAt && old.sizeBytes == candidate.sizeBytes) {
                queue(old.copy(scanToken = token))
                return@forEach
            }
            runCatching {
                val metadata = if (candidate.displayName.endsWith(".png", ignoreCase = true)) {
                    if (candidate.uri.startsWith("content://")) {
                        context.contentResolver.openInputStream(Uri.parse(candidate.uri))?.use(NaiPngMetadataParser::parse)
                    } else {
                        File(candidate.uri).inputStream().use(NaiPngMetadataParser::parse)
                    }
                } else null
                val prompts = buildList {
                    add(metadata?.prompt)
                    add(metadata?.negativePrompt)
                    addAll(metadata?.characterPrompts.orEmpty())
                    addAll(metadata?.characterNegativePrompts.orEmpty())
                }.toTypedArray()
                queue(GalleryImageEntity(
                    uri = candidate.uri,
                    folderKey = key,
                    displayName = candidate.displayName,
                    createdAt = filenameTimestamp(candidate.displayName) ?: candidate.createdAt.takeIf { it > 0 } ?: candidate.modifiedAt,
                    modifiedAt = candidate.modifiedAt,
                    sizeBytes = candidate.sizeBytes,
                    model = metadata?.model,
                    seed = metadata?.seed,
                    width = metadata?.width ?: candidate.width,
                    height = metadata?.height ?: candidate.height,
                    sampler = metadata?.sampler,
                    steps = metadata?.steps,
                    scale = metadata?.scale,
                    guidanceRescale = metadata?.guidanceRescale,
                    noiseSchedule = metadata?.noiseSchedule,
                    prompt = metadata?.prompt,
                    negativePrompt = metadata?.negativePrompt,
                    metadataJson = metadata?.let { json.encodeToString(it) },
                    searchTags = GalleryTagIndex.build(*prompts),
                    scanToken = token,
                ))
                indexed++
            }.onFailure {
                failed++
                old?.let { queue(it.copy(scanToken = token)) }
            }
        }
        if (pending.isNotEmpty()) galleryDao.upsertAll(pending)
        galleryDao.deleteNotSeen(key, token)
        GalleryScanResult(files.size, indexed, failed)
    }

    suspend fun delete(item: GalleryImageEntity): Boolean = withContext(Dispatchers.IO) {
        if (imageStore.exists(item.uri) && !imageStore.delete(item.uri)) return@withContext false
        historyDao.findByImagePath(item.uri)?.let { history ->
            File(history.thumbnailPath).delete()
            historyDao.delete(history)
        }
        galleryDao.delete(item)
        true
    }

    suspend fun deleteAll(items: Collection<GalleryImageEntity>): Int = withContext(Dispatchers.IO) {
        items.count { delete(it) }
    }

    private fun listTree(tree: Uri): List<Candidate> {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        return resolver.query(children, projection, null, null, null)?.use { cursor ->
            val result = mutableListOf<Candidate>()
            while (cursor.moveToNext()) {
                val mime = cursor.getString(2).orEmpty()
                val name = cursor.getString(1).orEmpty()
                if (!mime.startsWith("image/") && !name.endsWith(".png", true) && !name.endsWith(".jpg", true) && !name.endsWith(".jpeg", true) && !name.endsWith(".webp", true)) continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                result += Candidate(uri.toString(), name, 0L, cursor.getLong(3), cursor.getLong(4), null, null)
            }
            result
        }.orEmpty()
    }

    private fun listDefaultFolder(): List<Candidate> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val root = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return emptyList()
            return File(root, "NAI_BlockPrompt").listFiles().orEmpty()
                .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in IMAGE_EXTENSIONS }
                .map { Candidate(it.absolutePath, it.name, it.lastModified(), it.lastModified(), it.length(), null, null) }
        }
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        val relativePath = "${Environment.DIRECTORY_PICTURES}/NAI_BlockPrompt/"
        return resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
            arrayOf(relativePath),
            null,
        )?.use { cursor ->
            val result = mutableListOf<Candidate>()
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0))
                result += Candidate(
                    uri.toString(), cursor.getString(1).orEmpty(), cursor.getLong(2), cursor.getLong(3) * 1000L,
                    cursor.getLong(4), cursor.getInt(5).takeIf { it > 0 }, cursor.getInt(6).takeIf { it > 0 },
                )
            }
            result
        }.orEmpty()
    }

    private fun filenameTimestamp(name: String): Long? = runCatching {
        val match = Regex("NAI_(\\d{8}_\\d{6}_\\d{3})").find(name) ?: return null
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).parse(match.groupValues[1])?.time
    }.getOrNull()

    private data class Candidate(
        val uri: String,
        val displayName: String,
        val createdAt: Long,
        val modifiedAt: Long,
        val sizeBytes: Long,
        val width: Int?,
        val height: Int?,
    )

    companion object {
        private const val DEFAULT_FOLDER_KEY = "__default_generation_folder__"
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    }
}
