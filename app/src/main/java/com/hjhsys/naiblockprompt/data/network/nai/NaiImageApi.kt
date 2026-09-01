package com.hjhsys.naiblockprompt.data.network.nai

import com.hjhsys.naiblockprompt.data.network.nai.dto.*

data class GeneratedImagePayload(val bytes: ByteArray, val seed: Long?)

sealed interface NaiApiFailure {
    data object Authentication : NaiApiFailure
    data object PaymentRequired : NaiApiFailure
    data object RateLimited : NaiApiFailure
    data class Network(val reason: String?) : NaiApiFailure
    data class Api(val statusCode: Int, val reason: String?) : NaiApiFailure
    data class InvalidResponse(val reason: String?) : NaiApiFailure
}

sealed interface NaiApiResult<out T> {
    data class Success<T>(val value: T) : NaiApiResult<T>
    data class Failure(val error: NaiApiFailure) : NaiApiResult<Nothing>
}

interface NaiImageApi {
    suspend fun generate(token: String, request: NaiImageGenerationRequest): NaiApiResult<GeneratedImagePayload>
    suspend fun testConnection(token: String): NaiApiResult<Unit>
}
