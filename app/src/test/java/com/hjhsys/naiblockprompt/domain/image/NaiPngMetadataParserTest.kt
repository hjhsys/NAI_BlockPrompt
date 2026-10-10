package com.hjhsys.naiblockprompt.domain.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream

class NaiPngMetadataParserTest {
    @Test fun `reads NovelAI text metadata`() {
        val png = png(
            "Description" to "1girl, beach",
            "Source" to "nai-diffusion-4-5-full",
            "Comment" to """{"uc":"lowres","seed":42,"width":832,"height":1216,"sampler":"k_euler_ancestral","steps":28,"scale":5.0,"v4_prompt":{"caption":{"char_captions":[{"char_caption":"blue hair"}]}}}""",
        )
        val metadata = NaiPngMetadataParser.parse(png)!!
        assertEquals("1girl, beach", metadata.prompt)
        assertEquals("lowres", metadata.negativePrompt)
        assertEquals(42L, metadata.seed)
        assertEquals(listOf("blue hair"), metadata.characterPrompts)
    }

    @Test fun `reads gallery generation filter metadata`() {
        val png = png(
            "Description" to "1girl",
            "Comment" to """{"steps":28,"scale":5.0,"cfg_rescale":0.4,"noise_schedule":"karras"}""",
        )

        val metadata = NaiPngMetadataParser.parse(png)!!
        assertEquals(0.4f, metadata.guidanceRescale)
        assertEquals("karras", metadata.noiseSchedule)
    }

    @Test fun `non png has no metadata`() = assertNull(NaiPngMetadataParser.parse("jpeg".toByteArray()))

    @Test fun `streaming parser skips image payload`() {
        val png = png(
            "Description" to "1girl, beach",
            extraChunk = "IDAT" to ByteArray(1024 * 1024),
        )

        assertEquals("1girl, beach", NaiPngMetadataParser.parse(ByteArrayInputStream(png))?.prompt)
    }

    @Test fun `comment api model takes precedence over display source`() {
        val png = png(
            "Description" to "1girl",
            "Source" to "NovelAI Diffusion V5 Full",
            "Comment" to """{"model":"nai-diffusion-5-full","seed":42}""",
        )

        assertEquals("nai-diffusion-5-full", NaiPngMetadataParser.parse(png)?.model)
    }

    @Test fun `reads current NovelAI model name before hashed source`() {
        val png = png(
            "Description" to "1girl",
            "Source" to "NovelAI Diffusion V5 0ADF9AB7",
            "Comment" to """{"model_name":"NovelAI Diffusion V5","model_hash":"0ADF9AB7","seed":42}""",
        )

        assertEquals("NovelAI Diffusion V5", NaiPngMetadataParser.parse(png)?.model)
    }

    @Test fun `detects external reference metadata without claiming to restore its image`() {
        val png = png(
            "Description" to "1girl",
            "Comment" to """{"seed":42,"director_reference_images":["encoded-reference"]}""",
        )

        assertEquals(true, NaiPngMetadataParser.parse(png)?.usedExternalImageGuidance)
    }

    @Test fun `ignores inactive default image guidance fields`() {
        val png = png(
            "Description" to "1girl",
            "Comment" to """{"seed":42,"action":"generate","image":"","mask":null,"reference_image_multiple":[],"director_reference_images":[],"v4_prompt":{"use_coords":false,"caption":{"char_captions":[{"centers":[{"x":0.5,"y":0.5}]}]}}}""",
        )

        val metadata = NaiPngMetadataParser.parse(png)!!
        assertEquals(false, metadata.usedExternalImageGuidance)
        assertEquals(emptyList<Any>(), metadata.characterPositions)
    }

    @Test fun `ignores nonzero reference defaults when image arrays are empty`() {
        val png = png(
            "Description" to "1girl",
            "Comment" to """{"action":"generate","reference_image_multiple":[],"reference_information_extracted_multiple":[1.0],"reference_strength_multiple":[0.6],"director_reference_images":[],"director_reference_information_extracted":[1.0],"director_reference_strength_values":[1.0],"director_reference_secondary_strength_values":[0.0],"vibe_transfer_seed":1234}""",
        )

        assertEquals(false, NaiPngMetadataParser.parse(png)?.usedExternalImageGuidance)
    }

    private fun png(vararg values: Pair<String, String>, extraChunk: Pair<String, ByteArray>? = null): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        values.forEach { (key, value) ->
            val data = key.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0) + value.toByteArray(Charsets.ISO_8859_1)
            out.write(byteArrayOf((data.size ushr 24).toByte(), (data.size ushr 16).toByte(), (data.size ushr 8).toByte(), data.size.toByte()))
            out.write("tEXt".toByteArray(Charsets.ISO_8859_1)); out.write(data); out.write(byteArrayOf(0, 0, 0, 0))
        }
        extraChunk?.let { (type, data) ->
            out.write(byteArrayOf((data.size ushr 24).toByte(), (data.size ushr 16).toByte(), (data.size ushr 8).toByte(), data.size.toByte()))
            out.write(type.toByteArray(Charsets.ISO_8859_1)); out.write(data); out.write(byteArrayOf(0, 0, 0, 0))
        }
        return out.toByteArray()
    }
}
