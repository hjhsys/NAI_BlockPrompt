package com.hjhsys.naiblockprompt.data.network.nai

import com.hjhsys.naiblockprompt.data.network.nai.dto.*
import kotlinx.serialization.Serializable

data class GeneratedImagePayload(val bytes: ByteArray, val seed: Long?)

/** Values exposed by NovelAI's official /user/subscription response. */
@Serializable
data class NaiSubscriptionStatus(
    val trainingStepsLeft: NaiTrainingStepsLeft? = null,
    val usage: NaiUsageStatus? = null,
)

@Serializable
data class NaiTrainingStepsLeft(
    val fixedTrainingStepsLeft: Int = 0,
    val purchasedTrainingSteps: Int = 0,
)

@Serializable
data class NaiUsageStatus(
    val isNegative: Boolean = false,
    val percent: Int = 0,
)

sealed interface NaiApiFailure {
    data object Authentication : NaiApiFailure
    data object PaymentRequired : NaiApiFailure
    data object RateLimited : NaiApiFailure
    data class Network(val reason: String?) : NaiApiFailure
    data class Api(val statusCode: Int, val reason: String?) : NaiApiFailure
    data class InvalidResponse(val reason: String?) : NaiApiFailure
    data class Storage(val reason: String?) : NaiApiFailure
}

sealed interface NaiApiResult<out T> {
    data class Success<T>(val value: T) : NaiApiResult<T>
    data class Failure(val error: NaiApiFailure) : NaiApiResult<Nothing>
}

interface NaiImageApi {
    suspend fun generate(token: String, request: NaiImageGenerationRequest): NaiApiResult<GeneratedImagePayload>
    suspend fun testConnection(token: String): NaiApiResult<Unit>
    suspend fun subscriptionStatus(token: String): NaiApiResult<NaiSubscriptionStatus>
}
