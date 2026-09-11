package com.hjhsys.naiblockprompt.data.network.nai

import com.hjhsys.naiblockprompt.data.network.nai.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import okio.ByteString.Companion.decodeBase64
import android.util.Log
import com.hjhsys.naiblockprompt.BuildConfig

class OkHttpNaiImageApi(
    private val client: OkHttpClient,
    private val json: Json,
    private val baseUrl: HttpUrl = "https://image.novelai.net/".toHttpUrl(),
) : NaiImageApi {
    override suspend fun generate(token: String, request: NaiImageGenerationRequest): NaiApiResult<GeneratedImagePayload> =
        if (request.parameters.stream == "msgpack") executeBytes(
            Request.Builder()
                .url(baseUrl.resolve("ai/generate-image-stream")!!)
                .post(generationBody(request))
                .header("Authorization", bearer(token))
                .header("Accept", "application/msgpack")
                .build(),
        ) { body -> NaiMsgpackImageStream.decode(body, request.parameters.seed, captureDiagnostics = BuildConfig.DEBUG) }
        else execute(
            Request.Builder()
                .url(baseUrl.resolve(if (request.parameters.stream == "sse") "ai/generate-image-stream" else "ai/generate-image")!!)
                .post(generationBody(request))
                .header("Authorization", bearer(token))
                .header("Accept", if (request.parameters.stream == "sse") "text/event-stream" else "application/json")
                .build(),
        ) { body ->
            if (request.parameters.stream == "sse") return@execute parseImageSse(body)
            val response = json.decodeFromString<NaiImageGenerationResponse>(body)
            val first = response.images.firstOrNull()
                ?: return@execute NaiApiResult.Failure(NaiApiFailure.InvalidResponse("images is empty"))
            val bytes = first.image.substringAfter(',', first.image).decodeBase64()?.toByteArray()
                ?: return@execute NaiApiResult.Failure(NaiApiFailure.InvalidResponse("invalid base64 image"))
            NaiApiResult.Success(GeneratedImagePayload(bytes, first.seed))
        }

    internal fun parseImageSse(body: String): NaiApiResult<GeneratedImagePayload> {
        var eventName: String? = null
        val dataLines = mutableListOf<String>()
        var finalPayload: GeneratedImagePayload? = null
        var streamError: String? = null

        fun flushEvent() {
            if (dataLines.isEmpty()) {
                eventName = null
                return
            }
            val data = dataLines.joinToString("\n")
            dataLines.clear()
            val payload = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
            val kind = (eventName ?: payload?.get("event_type")?.jsonPrimitive?.contentOrNull
                ?: payload?.get("type")?.jsonPrimitive?.contentOrNull).orEmpty().lowercase()
            when (kind) {
                "final" -> {
                    val image = payload?.get("image")?.jsonPrimitive?.contentOrNull
                    val bytes = image?.substringAfter(',')?.decodeBase64()?.toByteArray()
                    if (bytes == null) streamError = "final event has no valid image"
                    else finalPayload = GeneratedImagePayload(bytes, payload["seed"]?.jsonPrimitive?.longOrNull)
                }
                "error" -> streamError = payload?.get("message")?.jsonPrimitive?.contentOrNull
                    ?: payload?.get("error")?.jsonPrimitive?.contentOrNull
                    ?: "image stream error"
            }
            eventName = null
        }

        body.lineSequence().forEach { line ->
            when {
                line.isBlank() -> flushEvent()
                line.startsWith("event:") -> eventName = line.substringAfter(':').trim()
                line.startsWith("data:") -> dataLines += line.substringAfter(':').trimStart()
            }
        }
        flushEvent()
        return finalPayload?.let { NaiApiResult.Success(it) }
            ?: NaiApiResult.Failure(NaiApiFailure.InvalidResponse(streamError ?: "final image event is missing"))
    }

    internal fun generationBody(request: NaiImageGenerationRequest): RequestBody {
        if (request.action != "infill") return json.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE)
        val image = requireNotNull(request.parameters.image?.decodeBase64()).toByteArray()
        val mask = requireNotNull(request.parameters.mask?.decodeBase64()).toByteArray()
        val wire = request.copy(parameters = request.parameters.copy(image = "image", mask = "mask"))
        return MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("image", "image.png", image.toRequestBody("image/png".toMediaType()))
            .addFormDataPart("mask", "mask.png", mask.toRequestBody("image/png".toMediaType()))
            .addPart(Headers.headersOf("Content-Disposition", "form-data; name=\"request\""),
                json.encodeToString(wire).toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    override suspend fun testConnection(token: String): NaiApiResult<Unit> {
        val url = baseUrl.newBuilder()
            .addPathSegments("ai/generate-image/suggest-tags")
            .addQueryParameter("model", "nai-diffusion-3")
            .addQueryParameter("prompt", "girl")
            .addQueryParameter("lang", "en")
            .build()
        return execute(
            Request.Builder().url(url).get().header("Authorization", bearer(token)).build(),
        ) { NaiApiResult.Success(Unit) }
    }

    override suspend fun encodeVibe(token: String, request: NaiEncodeVibeRequest): NaiApiResult<ByteArray> =
        executeBytes(
            Request.Builder()
                .url(baseUrl.resolve("ai/encode-vibe")!!)
                .post(json.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", bearer(token))
                .header("Accept", "application/binary")
                .build(),
        ) { NaiApiResult.Success(it) }

    override suspend fun subscriptionStatus(token: String): NaiApiResult<NaiSubscriptionStatus> = execute(
        Request.Builder()
            .url(baseUrl.resolve("user/subscription")!!)
            .get()
            .header("Authorization", bearer(token))
            .build(),
    ) { body -> NaiApiResult.Success(json.decodeFromString<NaiSubscriptionStatus>(body)) }

    private suspend fun <T> execute(request: Request, parse: (String) -> NaiApiResult<T>): NaiApiResult<T> =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> try { parse(body) } catch (error: Exception) {
                            NaiApiResult.Failure(NaiApiFailure.InvalidResponse(error.message))
                        }
                        response.code == 401 || response.code == 403 -> NaiApiResult.Failure(NaiApiFailure.Authentication)
                        response.code == 402 -> NaiApiResult.Failure(NaiApiFailure.PaymentRequired)
                        response.code == 429 -> NaiApiResult.Failure(NaiApiFailure.RateLimited)
                        else -> {
                            Log.w(
                                LOG_TAG,
                                "API failure host=${request.url.host} path=${request.url.encodedPath} " +
                                    "status=${response.code} correlation=${response.header("x-correlation-id").orEmpty()}",
                            )
                            NaiApiResult.Failure(NaiApiFailure.Api(response.code, safeError(body)))
                        }
                    }
                }
            } catch (error: IOException) {
                Log.w(
                    LOG_TAG,
                    "Network failure host=${request.url.host} path=${request.url.encodedPath} " +
                        "type=${error.javaClass.simpleName} message=${error.message.orEmpty()}",
                )
                NaiApiResult.Failure(NaiApiFailure.Network(error.message))
            }
        }

    private suspend fun <T> executeBytes(request: Request, parse: (ByteArray) -> NaiApiResult<T>): NaiApiResult<T> =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.bytes() ?: byteArrayOf()
                    when {
                        response.isSuccessful -> try { parse(body) } catch (error: Exception) {
                            NaiApiResult.Failure(NaiApiFailure.InvalidResponse(error.message))
                        }
                        response.code == 401 || response.code == 403 -> NaiApiResult.Failure(NaiApiFailure.Authentication)
                        response.code == 402 -> NaiApiResult.Failure(NaiApiFailure.PaymentRequired)
                        response.code == 429 -> NaiApiResult.Failure(NaiApiFailure.RateLimited)
                        else -> NaiApiResult.Failure(NaiApiFailure.Api(response.code, safeError(body.toString(Charsets.UTF_8))))
                    }
                }
            } catch (error: IOException) {
                NaiApiResult.Failure(NaiApiFailure.Network(error.message))
            }
        }

    private fun bearer(token: String) = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
    private fun safeError(body: String) = body.take(300).takeIf { it.isNotBlank() }

    private companion object {
        const val LOG_TAG = "NAI.Network"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
