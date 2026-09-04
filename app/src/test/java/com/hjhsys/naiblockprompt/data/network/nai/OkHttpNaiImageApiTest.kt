package com.hjhsys.naiblockprompt.data.network.nai

import com.hjhsys.naiblockprompt.domain.generation.NaiRequestMapper
import com.hjhsys.naiblockprompt.domain.generation.PrepareGenerationResult
import com.hjhsys.naiblockprompt.domain.model.GenerationSettings
import com.hjhsys.naiblockprompt.domain.model.Session
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import com.hjhsys.naiblockprompt.data.network.nai.dto.NaiEncodeVibeRequest

class OkHttpNaiImageApiTest {
    @Test fun `encode vibe sends official JSON and returns binary`() = runTest {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(201).setBody(okio.Buffer().write(byteArrayOf(9, 8, 7))))
            start()
        }
        try {
            val api = OkHttpNaiImageApi(OkHttpClient(), Json { encodeDefaults = true }, server.url("/"))
            val result = api.encodeVibe("pst-secret", NaiEncodeVibeRequest("aW1hZ2U=", 0.7f, "nai-diffusion-4-5-full")) as NaiApiResult.Success
            assertArrayEquals(byteArrayOf(9, 8, 7), result.value)
            val request = server.takeRequest()
            assertEquals("/ai/encode-vibe", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("\"information_extracted\":0.7"))
            assertTrue(body.contains("\"image\":\"aW1hZ2U=\""))
            assertFalse(body.contains("pst-secret"))
        } finally { server.shutdown() }
    }

    @Test fun `parses official subscription balance fields`() = runTest {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(200).setBody(
                """{"trainingStepsLeft":{"fixedTrainingStepsLeft":1200,"purchasedTrainingSteps":34},"usage":{"isNegative":false,"percent":87,"timeUntilNextPercent":120}}"""
            ))
            start()
        }
        try {
            val api = OkHttpNaiImageApi(OkHttpClient(), Json { ignoreUnknownKeys = true }, server.url("/"))
            val result = api.subscriptionStatus("pst-secret") as NaiApiResult.Success
            val steps = requireNotNull(result.value.trainingStepsLeft)
            assertEquals(1234, steps.fixedTrainingStepsLeft + steps.purchasedTrainingSteps)
            assertEquals(87, result.value.usage!!.percent)
            val request = server.takeRequest()
            assertEquals("/user/subscription", request.path)
            assertEquals("Bearer pst-secret", request.getHeader("Authorization"))
        } finally { server.shutdown() }
    }

    @Test fun `decodes JSON image response and sends bearer without leaking into body`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(201).setHeader("Content-Type", "application/json").setBody(
            """{"images":[{"image":"${Base64.getEncoder().encodeToString(byteArrayOf(1,2,3))}","index":0,"seed":42}]}"""
        ))
        server.start()
        try {
            val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
            val api = OkHttpNaiImageApi(OkHttpClient(), json, server.url("/"))
            val session = Session.empty().copy(generationSettings = GenerationSettings("nai-diffusion-4-5-full", samplerId="k_euler_ancestral", steps=28, scale=5f))
            val prepared = (NaiRequestMapper { 42 }.prepare(session, true) as PrepareGenerationResult.Ready).generation
            val result = api.generate("pst-secret", prepared.request) as NaiApiResult.Success
            assertArrayEquals(byteArrayOf(1,2,3), result.value.bytes)
            assertEquals(42L, result.value.seed)
            val recorded = server.takeRequest()
            assertEquals("Bearer pst-secret", recorded.getHeader("Authorization"))
            assertFalse(recorded.body.readUtf8().contains("pst-secret"))
        } finally { server.shutdown() }
    }

    @Test fun `maps unauthorized response without parsing body`() = runTest {
        val server = MockWebServer().apply { enqueue(MockResponse().setResponseCode(401)); start() }
        try {
            val api = OkHttpNaiImageApi(OkHttpClient(), Json { encodeDefaults = true }, server.url("/"))
            val session = Session.empty().copy(generationSettings = GenerationSettings("nai-diffusion-4-5-full", samplerId="k_euler_ancestral", steps=1, scale=1f))
            val request = (NaiRequestMapper { 1 }.prepare(session, false) as PrepareGenerationResult.Ready).generation.request
            val failure = api.generate("token", request) as NaiApiResult.Failure
            assertEquals(NaiApiFailure.Authentication, failure.error)
        } finally { server.shutdown() }
    }
}
