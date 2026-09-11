package com.hjhsys.naiblockprompt.data.generation

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.hjhsys.naiblockprompt.BuildConfig
import com.hjhsys.naiblockprompt.data.local.dao.HistoryDao
import com.hjhsys.naiblockprompt.data.local.entity.HistoryEntryEntity
import com.hjhsys.naiblockprompt.data.network.nai.*
import com.hjhsys.naiblockprompt.domain.generation.*
import com.hjhsys.naiblockprompt.domain.image.InpaintMask
import com.hjhsys.naiblockprompt.domain.image.InpaintCompositor
import com.hjhsys.naiblockprompt.domain.model.CURRENT_SNAPSHOT_VERSION
import com.hjhsys.naiblockprompt.domain.model.SessionSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import com.hjhsys.naiblockprompt.data.settings.SettingsRepository
import com.hjhsys.naiblockprompt.data.diagnostics.InpaintDiagnosticsStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.security.MessageDigest

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
    private val inpaintDiagnostics = InpaintDiagnosticsStore(context)
    private var pendingSave: Pair<PreparedGeneration, GeneratedImagePayload>? = null
    private var pendingSaveRequest: PreparedGeneration? = null
    suspend fun generate(token: String, generation: PreparedGeneration): GenerationResult {
        pendingSave?.takeIf { pendingSaveRequest == generation }?.let { pending ->
            return persistSuccess(pending.first, pending.second).also { if (it is GenerationResult.Success) pendingSave = null }
        }
        val prepared = try {
            when (val attached = withContext(Dispatchers.IO) { attachInputImage(token, generation) }) {
                is AttachmentResult.Ready -> attached.generation
                is AttachmentResult.Failed -> return GenerationResult.Failure(attached.error)
            }
        } catch (error: Exception) {
            return GenerationResult.Failure(NaiApiFailure.Storage(error.message))
        }
        return when (val result = api.generate(token, prepared.request)) {
            is NaiApiResult.Failure -> {
                if (BuildConfig.DEBUG && prepared.request.action == "infill") {
                    runCatching { inpaintDiagnostics.recordApiFailure(result.error.javaClass.simpleName) }
                }
                GenerationResult.Failure(result.error)
            }
            is NaiApiResult.Success -> {
                if (BuildConfig.DEBUG && prepared.request.action == "infill") {
                    result.value.streamDiagnostics?.let { diagnostics ->
                        runCatching { inpaintDiagnostics.recordStream(diagnostics) }
                            .onFailure { Log.w(INPAINT_LOG_TAG, "Stream diagnostic capture failed: ${it.javaClass.simpleName}") }
                    }
                }
                val payload = if (prepared.request.action == "infill") {
                    finalizeInpaintResult(prepared, result.value)
                } else result.value
                pendingSave = prepared to payload
                pendingSaveRequest = generation
                persistSuccess(prepared, payload).also { if (it is GenerationResult.Success) pendingSave = null }
            }
        }
    }

    private suspend fun finalizeInpaintResult(
        generation: PreparedGeneration,
        payload: GeneratedImagePayload,
    ): GeneratedImagePayload = withContext(Dispatchers.IO) {
        runCatching {
            val input = requireNotNull(generation.sourceSession.generationSettings.imageInput)
            // Composite against the exact PNG embedded in the completed request. Re-reading the
            // content URI here can produce a differently oriented/decoded source, or fail after
            // the server has already charged for and completed the generation.
            val sourceBytes = Base64.decode(
                requireNotNull(generation.request.parameters.image),
                Base64.DEFAULT,
            )
            val maskBytes = Base64.decode(requireNotNull(input.maskPngBase64), Base64.DEFAULT)
            val source = requireNotNull(BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size))
            val generated = requireNotNull(BitmapFactory.decodeByteArray(payload.bytes, 0, payload.bytes.size))
            val maskBitmap = requireNotNull(BitmapFactory.decodeByteArray(maskBytes, 0, maskBytes.size))
            try {
                require(source.width == generated.width && source.height == generated.height)
                require(source.width == maskBitmap.width && source.height == maskBitmap.height)
                val count = source.width * source.height
                val sourcePixels = IntArray(count).also { source.getPixels(it, 0, source.width, 0, 0, source.width, source.height) }
                val generatedPixels = IntArray(count).also { generated.getPixels(it, 0, source.width, 0, 0, source.width, source.height) }
                val maskPixels = IntArray(count).also { maskBitmap.getPixels(it, 0, source.width, 0, 0, source.width, source.height) }
                val mask = InpaintMask.fromBlackWhitePixels(source.width, source.height, ByteArray(count) { index ->
                    when (maskPixels[index]) {
                        android.graphics.Color.BLACK -> InpaintMask.BLACK.toByte()
                        android.graphics.Color.WHITE -> InpaintMask.WHITE.toByte()
                        else -> error("Inpaint mask must remain opaque black/white")
                    }
                })
                val composedPixels = InpaintCompositor.composeArgb(sourcePixels, generatedPixels, mask)
                if (BuildConfig.DEBUG) {
                    runCatching {
                        inpaintDiagnostics.complete(payload.bytes, sourcePixels, generatedPixels, mask, composedPixels)
                    }.onFailure { Log.w(INPAINT_LOG_TAG, "Diagnostic capture failed: ${it.javaClass.simpleName}") }
                }
                val composed = java.io.ByteArrayOutputStream().also { output ->
                    val bitmap = android.graphics.Bitmap.createBitmap(
                        composedPixels,
                        source.width,
                        source.height,
                        android.graphics.Bitmap.Config.ARGB_8888,
                    )
                    try {
                        check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
                    } finally {
                        bitmap.recycle()
                    }
                }.toByteArray()
                payload.copy(bytes = composed, streamDiagnostics = null)
            } finally {
                source.recycle()
                generated.recycle()
                maskBitmap.recycle()
            }
        }.getOrElse { error ->
            Log.w(INPAINT_LOG_TAG, "Result compositing failed; preserving server result: ${error.javaClass.simpleName}")
            payload.copy(streamDiagnostics = null)
        }
    }

    private sealed interface AttachmentResult {
        data class Ready(val generation: PreparedGeneration) : AttachmentResult
        data class Failed(val error: NaiApiFailure) : AttachmentResult
    }

    private suspend fun attachInputImage(token: String, generation: PreparedGeneration): AttachmentResult {
        val input = generation.sourceSession.generationSettings.imageInput ?: return AttachmentResult.Ready(generation)
        if (input.mode == com.hjhsys.naiblockprompt.domain.model.ImageInputMode.VIBE_TRANSFER &&
            !VibeTransferRequestMapper.isSupported(generation.request.model)
        ) {
            return AttachmentResult.Ready(generation.copy(request = VibeTransferRequestMapper.withoutVibe(generation.request)))
        }
        val bytes = context.contentResolver.openInputStream(Uri.parse(input.uri))?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Selected image could not be read")
        val encodedSource = Base64.encodeToString(bytes, Base64.NO_WRAP)
        if (input.mode == com.hjhsys.naiblockprompt.domain.model.ImageInputMode.INPAINT) {
            val source = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            try {
                val maskBytes = Base64.decode(requireNotNull(input.maskPngBase64), Base64.DEFAULT)
                val mask = requireNotNull(BitmapFactory.decodeByteArray(maskBytes, 0, maskBytes.size))
                try {
                    require(mask.width == source.width && mask.height == source.height) { "Mask resolution mismatch" }
                    val colors = IntArray(mask.width * mask.height)
                    mask.getPixels(colors, 0, mask.width, 0, 0, mask.width, mask.height)
                    val maskData = InpaintMask.fromBlackWhitePixels(mask.width, mask.height, ByteArray(colors.size) { index ->
                        when (colors[index]) {
                            android.graphics.Color.BLACK -> InpaintMask.BLACK.toByte()
                            android.graphics.Color.WHITE -> InpaintMask.WHITE.toByte()
                            else -> throw IllegalArgumentException("Mask must be opaque black/white")
                        }
                    })
                    val png = java.io.ByteArrayOutputStream().also { source.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                    val request = InpaintRequestMapper.map(generation.request, Base64.encodeToString(png, Base64.NO_WRAP),
                        maskData, source.width, source.height, input.strength) {
                        Base64.encodeToString(it, Base64.NO_WRAP)
                    }
                    if (BuildConfig.DEBUG) {
                        val transmittedMask = Base64.decode(requireNotNull(request.parameters.mask), Base64.DEFAULT)
                        runCatching {
                            inpaintDiagnostics.begin(
                                requestImagePng = png,
                                requestMaskPng = transmittedMask,
                                summary = buildString {
                                    appendLine("model=${request.model}")
                                    appendLine("action=${request.action}")
                                    appendLine("stream=${request.parameters.stream}")
                                    appendLine("imageFormat=${request.parameters.imageFormat}")
                                    appendLine("addOriginalImage=${request.parameters.addOriginalImage}")
                                    appendLine("straightAlpha=${request.parameters.straightAlpha}")
                                    appendLine("maskPipeline=web-latent8-dilate4-blur20x2")
                                    appendLine("size=${source.width}x${source.height}")
                                    appendLine("strength=${input.strength}")
                                    appendLine("seed=${request.parameters.seed}")
                                    val requestMaskData = InpaintCompositor.serverMask(maskData)
                                    appendLine("editorMaskWhitePixels=${maskData.whitePixelCount}")
                                    appendLine("requestMaskWhitePixels=${requestMaskData.whitePixelCount}")
                                    appendLine("requestMaskBlackPixels=${requestMaskData.width * requestMaskData.height - requestMaskData.whitePixelCount}")
                                },
                            )
                        }.onFailure { Log.w(INPAINT_LOG_TAG, "Diagnostic setup failed: ${it.javaClass.simpleName}") }
                        Log.d(
                            INPAINT_LOG_TAG,
                            "mask=${maskData.width}x${maskData.height} rgba8=true alpha=255 " +
                                "white=${maskData.whitePixelCount} black=${maskData.width * maskData.height - maskData.whitePixelCount} " +
                                "bytes=${transmittedMask.size}",
                        )
                    }
                    return AttachmentResult.Ready(generation.copy(request = request))
                } finally { mask.recycle() }
            } finally { source.recycle() }
        }
        val parameters = when (input.mode) {
            com.hjhsys.naiblockprompt.domain.model.ImageInputMode.INPAINT -> error("Handled above")
            com.hjhsys.naiblockprompt.domain.model.ImageInputMode.IMAGE_TO_IMAGE -> generation.request.parameters.copy(
                image = encodedSource, strength = input.strength, noise = input.noise,
            )
            com.hjhsys.naiblockprompt.domain.model.ImageInputMode.VIBE_TRANSFER -> {
                val cacheFile = vibeCacheFile(bytes, generation.request.model, input.informationExtracted)
                val vibe = if (cacheFile.isFile) cacheFile.readBytes() else when (val result = api.encodeVibe(
                    token,
                    com.hjhsys.naiblockprompt.data.network.nai.dto.NaiEncodeVibeRequest(
                        image = encodedSource,
                        informationExtracted = input.informationExtracted,
                        model = generation.request.model,
                    ),
                )) {
                    is NaiApiResult.Failure -> return AttachmentResult.Failed(result.error)
                    is NaiApiResult.Success -> result.value.also { cacheFile.parentFile?.mkdirs(); cacheFile.writeBytes(it) }
                }
                VibeTransferRequestMapper.attach(
                    request = generation.request,
                    encodedVibe = Base64.encodeToString(vibe, Base64.NO_WRAP),
                    informationExtracted = input.informationExtracted,
                    strength = input.strength,
                ).parameters
            }
            com.hjhsys.naiblockprompt.domain.model.ImageInputMode.PRECISE_REFERENCE -> {
                require(generation.request.model.startsWith("nai-diffusion-4-5-")) { "Precise Reference requires a V4.5 model" }
                val preparedImage = ReferenceImagePreprocessor.preciseReferencePng(bytes)
                val description = com.hjhsys.naiblockprompt.data.network.nai.dto.NaiV4ConditionInput(
                    caption = com.hjhsys.naiblockprompt.data.network.nai.dto.NaiV4ExternalCaption(input.preciseType.apiValue, emptyList()),
                    useCoordinates = false,
                    useOrder = false,
                )
                generation.request.parameters.copy(
                    directorReferenceImages = listOf(Base64.encodeToString(preparedImage, Base64.NO_WRAP)),
                    directorReferenceDescriptions = listOf(description),
                    directorReferenceInformationExtracted = listOf(input.informationExtracted),
                    directorReferenceStrengths = listOf(input.strength),
                    directorReferenceSecondaryStrengths = listOf(input.fidelity),
                )
            }
        }
        val action = if (input.mode == com.hjhsys.naiblockprompt.domain.model.ImageInputMode.IMAGE_TO_IMAGE) "img2img" else generation.request.action
        return AttachmentResult.Ready(generation.copy(request = generation.request.copy(action = action, parameters = parameters)))
    }

    private fun vibeCacheFile(bytes: ByteArray, model: String, informationExtracted: Float): File {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes)
        digest.update(model.toByteArray())
        digest.update(informationExtracted.toString().toByteArray())
        val name = digest.digest().joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir, "vibe_cache"), "$name.vibe")
    }

    private companion object {
        const val INPAINT_LOG_TAG = "NAI.Inpaint"
    }

    suspend fun testConnection(token: String) = api.testConnection(token)
    suspend fun subscriptionStatus(token: String) = api.subscriptionStatus(token)
    suspend fun exportLatestInpaintDiagnostics(): ByteArray? = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) inpaintDiagnostics.exportLatest() else null
    }

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
            // Keep any saved original: the server already completed this generation.
            thumbnail?.delete()
            GenerationResult.Failure(NaiApiFailure.Storage(error.message))
        }
    }
}
