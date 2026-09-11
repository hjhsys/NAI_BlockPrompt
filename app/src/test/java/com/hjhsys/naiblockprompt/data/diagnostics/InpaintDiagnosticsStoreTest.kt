package com.hjhsys.naiblockprompt.data.diagnostics

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.hjhsys.naiblockprompt.data.network.nai.NaiImageStreamDiagnostics
import com.hjhsys.naiblockprompt.data.network.nai.NaiImageStreamFrame
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class InpaintDiagnosticsStoreTest {
    @Test fun `diagnostic archive is deterministic and preserves evidence bytes`() {
        val archive = InpaintDiagnosticsStore.zip(
            mapOf(
                "02-request-mask.png" to byteArrayOf(2, 3),
                "00-request-summary.txt" to "no prompt or token".toByteArray(),
            ),
        )
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }

        assertEquals(listOf("00-request-summary.txt", "02-request-mask.png"), entries.keys.toList())
        assertArrayEquals(byteArrayOf(2, 3), entries.getValue("02-request-mask.png"))
    }

    @Test fun `stream manifest identifies every frame and selected final without payload text`() {
        val manifest = InpaintDiagnosticsStore.streamManifest(
            NaiImageStreamDiagnostics(
                rawResponse = byteArrayOf(1, 2, 3),
                frames = listOf(
                    NaiImageStreamFrame(0, "intermediate", 4, 0, 7, listOf("event_type", "image", "step_ix"), byteArrayOf(9)),
                    NaiImageStreamFrame(1, "final", null, 0, 7, listOf("event_type", "image"), byteArrayOf(8, 7)),
                ),
                selectedFrameIndex = 1,
            ),
        )

        assertTrue(manifest.contains("frameCount=2"))
        assertTrue(manifest.contains("selectedFrameIndex=1"))
        assertTrue(manifest.contains("frame=0 eventType=intermediate stepIx=4 sampIx=0 genId=7"))
        assertTrue(manifest.contains("frame=1 eventType=final"))
    }
}
