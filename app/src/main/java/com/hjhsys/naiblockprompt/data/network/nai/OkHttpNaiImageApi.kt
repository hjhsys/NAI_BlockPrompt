package com.hjhsys.naiblockprompt.data.network.nai

import com.hjhsys.naiblockprompt.data.network.nai.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import okio.ByteString.Companion.decodeBase64
import android.util.Log

class OkHttpNaiImageApi(
    private val client: OkHttpClient,
    private val json: Json,
    private val baseUrl: HttpUrl = "https://image.novelai.net/".toHttpUrl(),
) : NaiImageApi {
    override suspend fun generate(token: String, request: NaiImageGenerationRequest): NaiApiResult<GeneratedImagePayload> =
        execute(
            Request.Builder()
                .url(baseUrl.resolve("ai/generate-image")!!)
                .post(json.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", bearer(token))
                .header("Accept", "application/json")
                .build(),
        ) { body ->
            val response = json.decodeFromString<NaiImageGenerationResponse>(body)
            val first = response.images.firstOrNull()
                ?: return@execute NaiApiResult.Failure(NaiApiFailure.InvalidResponse("images is empty"))
            val bytes = first.image.substringAfter(',', first.image).decodeBase64()?.toByteArray()
                ?: return@execute NaiApiResult.Failure(NaiApiFailure.InvalidResponse("invalid base64 image"))
            NaiApiResult.Success(GeneratedImagePayload(bytes, first.seed))
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

    private fun bearer(token: String) = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
    private fun safeError(body: String) = body.take(300).takeIf { it.isNotBlank() }

    private companion object {
        const val LOG_TAG = "NAI.Network"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
