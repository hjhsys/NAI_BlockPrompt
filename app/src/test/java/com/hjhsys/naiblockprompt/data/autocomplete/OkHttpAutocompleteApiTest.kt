package com.hjhsys.naiblockprompt.data.autocomplete

import com.hjhsys.naiblockprompt.domain.autocomplete.SuggestionSource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OkHttpAutocompleteApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: OkHttpAutocompleteApi

    @Before fun setup() {
        server = MockWebServer().also { it.start() }
        api = OkHttpAutocompleteApi(OkHttpClient(), Json { ignoreUnknownKeys = true }, server.url("/"), server.url("/"))
    }
    @After fun tearDown() = server.shutdown()

    @Test fun `maps official NovelAI suggest tags response`() = runTest {
        server.enqueue(MockResponse().setBody("""{"tags":[{"tag":"red hair","count":12,"confidence":0.8}]}"""))
        val result = api.novelAi("secret", "nai-diffusion-3", "red").getOrThrow().single()
        val request = server.takeRequest()
        assertEquals("red hair", result.tag)
        assertEquals(SuggestionSource.NOVEL_AI, result.source)
        assertEquals("Bearer secret", request.getHeader("Authorization"))
        assertEquals("red", request.requestUrl?.queryParameter("prompt"))
    }

    @Test fun `maps Danbooru tags and uses prefix search`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"name":"blue_hair","category":0,"post_count":99}]"""))
        val result = api.danbooru("blue").getOrThrow().single()
        val request = server.takeRequest()
        assertEquals("blue_hair", result.tag)
        assertEquals(99L, result.postCount)
        assertEquals("blue*", request.requestUrl?.queryParameter("search[name_matches]"))
    }
}
