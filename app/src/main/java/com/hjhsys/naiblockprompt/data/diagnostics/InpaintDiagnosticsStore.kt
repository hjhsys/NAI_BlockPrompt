package com.hjhsys.naiblockprompt.data.diagnostics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.hjhsys.naiblockprompt.domain.image.InpaintCompositor
import com.hjhsys.naiblockprompt.domain.image.InpaintMask
import com.hjhsys.naiblockprompt.data.network.nai.NaiImageStreamDiagnostics
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

/** Debug-build evidence for one inpaint request. Never stores credentials or prompt text. */
class InpaintDiagnosticsStore(context: Context) {
    private val directory = File(context.cacheDir, "inpaint_debug/latest")

    fun begin(requestImagePng: ByteArray, requestMaskPng: ByteArray, summary: String) {
        directory.deleteRecursively()
        directory.mkdirs()
        File(directory, "01-request-image.png").writeBytes(requestImagePng)
        File(directory, "02-request-mask.png").writeBytes(requestMaskPng)
        File(directory, "00-request-summary.txt").writeText(
            "NAI Block Prompt inpaint diagnostic\n" +
                "capturedUtc=${Instant.now()}\n" + summary.trim() + "\n" +
                "requestImageSha256=${sha256(requestImagePng)}\n" +
                "requestMaskSha256=${sha256(requestMaskPng)}\n",
        )
        File(directory, "README.txt").writeText(
            "Debug-only inpaint evidence. No API token or prompt text is included.\n" +
                "01/02 are the exact PNG bytes sent in the request.\n" +
                "03 is the final raw image layer decoded from NovelAI's MessagePack stream.\n" +
                "04 is that layer's alpha channel; 05 is the app composite weight.\n" +
                "06 is the app result. 07/08 are pixel-difference visualizations.\n" +
                "09-11 reuse the same server response with alternate composites; they do not consume Anlas.\n" +
                "19/20+ contain the exact MessagePack response, a frame manifest, and every image-bearing frame.\n" +
                "The raw response and generated images can contain embedded prompt metadata. Review before sharing.\n",
        )
    }

    fun recordStream(diagnostics: NaiImageStreamDiagnostics) {
        directory.mkdirs()
        File(directory, "19-response.msgpack").writeBytes(diagnostics.rawResponse)
        File(directory, "20-stream-frames.txt").writeText(streamManifest(diagnostics))
        diagnostics.frames.forEach { frame ->
            val image = frame.image ?: return@forEach
            val event = frame.eventType.orEmpty().lowercase()
                .replace(Regex("[^a-z0-9_-]"), "_")
                .ifBlank { "unknown" }
            val extension = imageExtension(image)
            File(directory, "21-frame-${frame.index.toString().padStart(3, '0')}-$event.$extension")
                .writeBytes(image)
        }
    }

    fun recordApiFailure(kind: String) {
        directory.mkdirs()
        File(directory, "99-status.txt").writeText("apiFailure=$kind\n")
    }

    fun complete(
        rawServerBytes: ByteArray,
        source: IntArray,
        generated: IntArray,
        mask: InpaintMask,
        appComposite: IntArray,
    ) {
        require(source.size == generated.size && source.size == appComposite.size)
        val extension = imageExtension(rawServerBytes)
        File(directory, "03-server-final.$extension").writeBytes(rawServerBytes)
        writeGrayPng("04-server-alpha.png", mask.width, mask.height, ByteArray(generated.size) { (generated[it] ushr 24).toByte() })
        writeGrayPng("05-composite-weight.png", mask.width, mask.height, InpaintCompositor.generatedAlphaMask(mask))
        writeArgbPng("06-app-final.png", mask.width, mask.height, appComposite)
        writeArgbPng("07-diff-app-vs-source.png", mask.width, mask.height, difference(source, appComposite))
        writeArgbPng("08-diff-server-vs-source.png", mask.width, mask.height, difference(source, generated))
        writeArgbPng("09-variant-hard-mask.png", mask.width, mask.height, hardComposite(source, generated, mask))
        val serverMask = InpaintCompositor.serverMask(mask)
        writeArgbPng("10-variant-hard-server-mask.png", mask.width, mask.height, hardComposite(source, generated, serverMask))
        writeArgbPng("11-variant-web-composite.png", mask.width, mask.height, InpaintCompositor.composeArgb(source, generated, mask))
        File(directory, "99-status.txt").writeText(
            "completed=true\n" +
                "size=${mask.width}x${mask.height}\n" +
                "whitePixels=${mask.whitePixelCount}\n" +
                "blackPixels=${mask.width * mask.height - mask.whitePixelCount}\n" +
                "serverBytes=${rawServerBytes.size}\n" +
                "serverSha256=${sha256(rawServerBytes)}\n" +
                "serverNonOpaquePixels=${generated.count { (it ushr 24) != 255 }}\n" +
                "compositeAlgorithm=web-latent8-dilate4-blur20x2\n" +
                "serverMaskWhitePixels=${serverMask.whitePixelCount}\n",
        )
    }

    fun exportLatest(): ByteArray? {
        val files = directory.listFiles()?.filter(File::isFile)?.sortedBy(File::getName).orEmpty()
        if (files.isEmpty()) return null
        return zip(files.associate { it.name to it.readBytes() })
    }

    private fun writeGrayPng(name: String, width: Int, height: Int, values: ByteArray) {
        val pixels = IntArray(values.size) { index ->
            val value = values[index].toInt() and 0xff
            Color.argb(255, value, value, value)
        }
        writeArgbPng(name, width, height, pixels)
    }

    private fun writeArgbPng(name: String, width: Int, height: Int, pixels: IntArray) {
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        try {
            File(directory, name).outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun hardComposite(source: IntArray, generated: IntArray, mask: InpaintMask): IntArray {
        val pixels = mask.pixelBytes()
        return IntArray(source.size) { index ->
            if ((pixels[index].toInt() and 0xff) == InpaintMask.WHITE) {
                blend(source[index], generated[index], (generated[index] ushr 24) / 255f)
            } else source[index]
        }
    }

    private fun blend(source: Int, generated: Int, weight: Float): Int {
        fun channel(shift: Int): Int {
            val from = source ushr shift and 0xff
            val to = generated ushr shift and 0xff
            return (from + (to - from) * weight).roundToInt().coerceIn(0, 255)
        }
        return Color.argb(255, channel(16), channel(8), channel(0))
    }

    private fun difference(first: IntArray, second: IntArray): IntArray = IntArray(first.size) { index ->
        fun channel(shift: Int) = kotlin.math.abs((first[index] ushr shift and 0xff) - (second[index] ushr shift and 0xff))
        Color.argb(255, channel(16), channel(8), channel(0))
    }

    companion object {
        internal fun zip(files: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                files.toSortedMap().forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

        internal fun streamManifest(diagnostics: NaiImageStreamDiagnostics): String = buildString {
            appendLine("responseBytes=${diagnostics.rawResponse.size}")
            appendLine("responseSha256=${sha256(diagnostics.rawResponse)}")
            appendLine("frameCount=${diagnostics.frames.size}")
            appendLine("selectedFrameIndex=${diagnostics.selectedFrameIndex ?: "none"}")
            diagnostics.frames.forEach { frame ->
                append("frame=${frame.index}")
                append(" eventType=${frame.eventType ?: "none"}")
                append(" stepIx=${frame.stepIndex ?: "none"}")
                append(" sampIx=${frame.sampleIndex ?: "none"}")
                append(" genId=${frame.generationId ?: "none"}")
                append(" keys=${frame.keys.joinToString(",")}")
                append(" imageBytes=${frame.image?.size ?: 0}")
                append(" imageSha256=${frame.image?.let(::sha256) ?: "none"}")
                appendLine()
            }
        }

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }

        private fun imageExtension(bytes: ByteArray): String = when {
            bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)) -> "png"
            bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "jpg"
            bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
            else -> "bin"
        }
    }
}
