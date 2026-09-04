package com.hjhsys.naiblockprompt.data.generation

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.hjhsys.naiblockprompt.data.local.dao.HistoryDao
import com.hjhsys.naiblockprompt.data.local.entity.HistoryEntryEntity
import com.hjhsys.naiblockprompt.data.network.nai.*
import com.hjhsys.naiblockprompt.domain.generation.*
import com.hjhsys.naiblockprompt.domain.model.CURRENT_SNAPSHOT_VERSION
import com.hjhsys.naiblockprompt.domain.model.SessionSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import com.hjhsys.naiblockprompt.data.settings.SettingsRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GenerationRecord(val imagePath: String, val thumbnailPath: String, val seed: Long)

sealed interface GenerationResult {
    data class Success(val record: GenerationRecord) : GenerationResult
    data class Failure(val error: NaiApiFailure) : GenerationResult
}

class GenerationRepository(
    private val context: Context,
    private val api: NaiImageApi,
    private val historyDao: HistoryDao,
    private val json: Json,
    private val settingsRepository: SettingsRepository,
) {
    private val imageStore = GeneratedImageStore(context)
    suspend fun generate(token: String, generation: PreparedGeneration): GenerationResult {
        val prepared = try {
            attachInputImage(generation)
        } catch (error: Exception) {
            return GenerationResult.Failure(NaiApiFailure.Storage(error.message))
        }
        return when (val result = api.generate(token, prepared.request)) {
            is NaiApiResult.Failure -> GenerationResult.Failure(result.error)
            is NaiApiResult.Success -> persistSuccess(prepared, result.value)
        }
    }

    private fun attachInputImage(generation: PreparedGeneration): PreparedGeneration {
        val input = generation.sourceSession.generationSettings.imageInput ?: return generation
        require(input.mode == com.hjhsys.naiblockprompt.domain.model.ImageInputMode.IMAGE_TO_IMAGE) {
            "Selected image reference mode is not supported by the verified API mapping"
        }
        val bytes = context.contentResolver.openInputStream(Uri.parse(input.uri))?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Selected image could not be read")
        val request = generation.request.copy(
            action = "img2img",
            parameters = generation.request.parameters.copy(
                image = Base64.encodeToString(bytes, Base64.NO_WRAP),
                strength = input.strength,
                noise = input.noise,
            ),
        )
        return generation.copy(request = request)
    }

    suspend fun testConnection(token: String) = api.testConnection(token)
    suspend fun subscriptionStatus(token: String) = api.subscriptionStatus(token)

    private suspend fun persistSuccess(
        generation: PreparedGeneration,
        payload: GeneratedImagePayload,
    ): GenerationResult = withContext(Dispatchers.IO) {
        var imageReference: String? = null
        var thumbnail: File? = null
        try {
            val actualSeed = payload.seed ?: generation.usedSeed
            val id = UUID.randomUUID().toString()
            val thumbnailDir = File(context.filesDir, "thumbnails").apply { mkdirs() }
            val thumbnailFile = File(thumbnailDir, "$id.jpg")
            thumbnail = thumbnailFile
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val storedImage = imageStore.savePng(
                payload.bytes,
                "NAI_${timestamp}_${id.take(8)}.png",
                settingsRepository.settings.first().imageSaveTreeUri,
            )
            imageReference = storedImage
            val decoded = BitmapFactory.decodeByteArray(payload.bytes, 0, payload.bytes.size)
                ?: throw IllegalArgumentException("Decoded image is invalid")
            val ratio = (384f / decoded.width.coerceAtLeast(decoded.height)).coerceAtMost(1f)
            val scaled = android.graphics.Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * ratio).toInt().coerceAtLeast(1),
                (decoded.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
            thumbnailFile.outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()

            val snapshot = HistorySnapshotFactory.from(generation, actualSeed)
            val repairTarget = historyDao.listAll().firstOrNull { row ->
                val originalExists = imageStore.exists(row.imagePath)
                val existing = runCatching {
                    if (row.snapshotVersion == CURRENT_SNAPSHOT_VERSION) {
                        json.decodeFromString<SessionSnapshot>(row.snapshotJson).generation
                    } else null
                }.getOrNull()
                GenerationHistoryPolicy.shouldRepairExisting(existing, originalExists, snapshot.generation!!)
            }
            historyDao.upsert(
                HistoryEntryEntity(
                    id = repairTarget?.id ?: id,
                    createdAt = repairTarget?.createdAt ?: System.currentTimeMillis(),
                    imagePath = storedImage,
                    thumbnailPath = thumbnailFile.absolutePath,
                    model = generation.request.model,
                    snapshotVersion = CURRENT_SNAPSHOT_VERSION,
                    snapshotJson = json.encodeToString(snapshot),
                    favorite = repairTarget?.favorite ?: false,
                ),
            )
            repairTarget?.thumbnailPath?.takeIf { it != thumbnailFile.absolutePath }?.let { File(it).delete() }
            GenerationResult.Success(GenerationRecord(storedImage, thumbnailFile.absolutePath, actualSeed))
        } catch (error: Exception) {
            imageReference?.let(imageStore::delete)
            thumbnail?.delete()
            GenerationResult.Failure(NaiApiFailure.Storage(error.message))
        }
    }
}
