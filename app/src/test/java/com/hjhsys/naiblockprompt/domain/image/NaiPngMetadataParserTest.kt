package com.hjhsys.naiblockprompt.domain.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

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

    @Test fun `non png has no metadata`() = assertNull(NaiPngMetadataParser.parse("jpeg".toByteArray()))

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

    private fun png(vararg values: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        values.forEach { (key, value) ->
            val data = key.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0) + value.toByteArray(Charsets.ISO_8859_1)
            out.write(byteArrayOf((data.size ushr 24).toByte(), (data.size ushr 16).toByte(), (data.size ushr 8).toByte(), data.size.toByte()))
            out.write("tEXt".toByteArray(Charsets.ISO_8859_1)); out.write(data); out.write(byteArrayOf(0, 0, 0, 0))
        }
        return out.toByteArray()
    }
}
