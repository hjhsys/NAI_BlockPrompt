package com.hjhsys.naiblockprompt.domain.generation

import com.hjhsys.naiblockprompt.domain.model.*
import com.hjhsys.naiblockprompt.domain.image.MaskGeometry
import com.hjhsys.naiblockprompt.domain.image.MaskPoint
import com.hjhsys.naiblockprompt.domain.image.InpaintMask
import com.hjhsys.naiblockprompt.domain.image.InpaintCompositor
import com.hjhsys.naiblockprompt.data.network.nai.OkHttpNaiImageApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class InpaintRequestMapperTest {
    private val json = Json { encodeDefaults = true; explicitNulls = false }
    private fun prepared(model: String) = (NaiRequestMapper { 42 }.prepare(Session.empty().copy(
        generationSettings = GenerationSettings(model, samplerId = "k_euler_ancestral", steps = 28, scale = 5f,
            imageInput = ImageInputState("file:///base.png", ImageInputMode.INPAINT, maskPngBase64 = "bWFzaw=="))), false) as PrepareGenerationResult.Ready).generation

    @Test fun `both full models use captured infill fields preserving prompts and seed`() {
        for (model in listOf("nai-diffusion-4-5-full", "nai-diffusion-5-full")) {
            val base = prepared(model)
            val mask = InpaintMask.black(832, 1216).apply { fillCircle(10, 10, 2) }
            val mapped = InpaintRequestMapper.map(base.request, "aW1hZ2U=", mask, 832, 1216, .7f) {
                Base64.getEncoder().encodeToString(it)
            }
            assertEquals("$model-inpainting", mapped.model)
            assertEquals("infill", mapped.action)
            assertEquals(4, mapped.parameters.paramsVersion)
            assertEquals(false, mapped.parameters.addOriginalImage)
            assertEquals(1f, mapped.parameters.inpaintImg2ImgStrength)
            assertEquals(true, mapped.parameters.straightAlpha)
            assertEquals("msgpack", mapped.parameters.stream)
            assertEquals("webp", mapped.parameters.imageFormat)
            assertEquals(.7f, mapped.parameters.strength)
            assertEquals(0f, mapped.parameters.noise)
            assertEquals(base.request.parameters.v4Prompt, mapped.parameters.v4Prompt)
            assertEquals(base.request.parameters.v4NegativePrompt, mapped.parameters.v4NegativePrompt)
            assertEquals(42L, mapped.parameters.seed)
            val body = OkHttpNaiImageApi(OkHttpClient(), json).generationBody(mapped)
            assertTrue(body is MultipartBody)
            val multipart = body as MultipartBody
            assertEquals(3, multipart.parts.size)
            assertEquals("multipart/form-data", multipart.type.toString())
            assertEquals("image/png", multipart.parts[0].body.contentType().toString())
            assertEquals("image/png", multipart.parts[1].body.contentType().toString())
            assertEquals("application", multipart.parts[2].body.contentType()?.type)
            assertEquals("json", multipart.parts[2].body.contentType()?.subtype)
            assertEquals("image", Buffer().also { multipart.parts[0].body.writeTo(it) }.readUtf8())
            val transmittedMask = Buffer().also { multipart.parts[1].body.writeTo(it) }.readByteArray()
            assertArrayEquals(InpaintCompositor.serverMask(mask).toApiPngBytes(), transmittedMask)
            assertFalse(mask.toApiPngBytes().contentEquals(transmittedMask))
            assertEquals(832, readPngInt(transmittedMask, 16))
            assertEquals(1216, readPngInt(transmittedMask, 20))
            assertEquals(8, transmittedMask[24].toInt())
            assertEquals(6, transmittedMask[25].toInt())
            val text = Buffer().also { body.writeTo(it) }.readUtf8()
            assertTrue(text.contains("name=\"image\""))
            assertTrue(text.contains("name=\"mask\""))
            assertTrue(text.contains("name=\"request\""))
            assertTrue(text.contains("\"image\":\"image\""))
            assertTrue(text.contains("\"mask\":\"mask\""))
            assertTrue(text.contains("\"inpaintImg2ImgStrength\":1.0"))
            assertTrue(text.contains("\"straight_alpha\":true"))
            assertTrue(text.contains("\"add_original_image\":false"))
            assertTrue(text.contains("\"stream\":\"msgpack\""))
            assertTrue(text.contains("\"image_format\":\"webp\""))
            assertFalse(text.contains("cache_secret_key"))
            val snapshot = HistorySnapshotFactory.from(base.copy(request = mapped), 42)
            val decoded = json.decodeFromString<SessionSnapshot>(json.encodeToString(snapshot))
            assertEquals(ImageInputMode.INPAINT, decoded.session.generationSettings.imageInput?.mode)
            assertEquals("bWFzaw==", decoded.session.generationSettings.imageInput?.maskPngBase64)
        }
    }
    @Test fun `empty and mismatched masks fail before a request can be built`() {
        val request = prepared("nai-diffusion-4-5-full").request
        val encode: (ByteArray) -> String = { Base64.getEncoder().encodeToString(it) }
        assertThrows(IllegalArgumentException::class.java) {
            InpaintRequestMapper.map(request, "aW1hZ2U=", InpaintMask.black(832, 1216), 832, 1216, .7f, encode)
        }
        val nonEmptyWrongSize = InpaintMask.black(10, 10).apply { fillCircle(1, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) {
            InpaintRequestMapper.map(request, "aW1hZ2U=", nonEmptyWrongSize, 832, 1216, .7f, encode)
        }
    }
    @Test fun `unsupported models are not guessed`() {
        assertFalse(InpaintRequestMapper.supports("nai-diffusion-5-curated"))
        assertFalse(InpaintRequestMapper.supports(null))
    }
    @Test fun `ordinary generation and i2i remain JSON`() {
        val request = prepared("nai-diffusion-4-5-full").request
        for (action in listOf("generate", "img2img")) {
            val original = request.copy(action = action)
            val body = OkHttpNaiImageApi(OkHttpClient(), json).generationBody(original)
            assertFalse(body is MultipartBody)
            assertEquals("json", body.contentType()?.subtype)
            assertEquals(json.encodeToString(original), Buffer().also { body.writeTo(it) }.readUtf8())
        }
    }
    @Test fun `fitted image coordinates map to source pixels and clamp bounds`() {
        val point = MaskGeometry.point(208f, 304f, 416f, 608f)
        assertEquals(MaskPoint(.5f, .5f), point)
        assertEquals(MaskPoint(415.5f, 607.5f), MaskGeometry.pixel(point, 832, 1216))
        assertEquals(MaskPoint(0f, 1f), MaskGeometry.point(-10f, 900f, 416f, 608f))
    }

    private fun readPngInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)
}
