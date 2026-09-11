package com.hjhsys.naiblockprompt.domain.image

import kotlin.math.abs
import kotlin.math.roundToInt

/** NovelAI-web-compatible mask preparation and client-side inpaint composition. */
object InpaintCompositor {
    private const val LATENT_SCALE = 8
    private const val LOW_RES_DILATE_RADIUS = 4
    private const val FULL_RES_BLUR_RADIUS = 20
    private const val BLUR_ITERATIONS = 2

    /**
     * The web client sends a latent-grid-aligned mask: nearest-neighbour downsample to 1/8,
     * binary threshold, then nearest-neighbour upscale. This is intentionally blocky on the wire.
     */
    fun serverMask(mask: InpaintMask): InpaintMask {
        val low = downsampleToLatentGrid(mask)
        val full = upscaleNearest(low, mask.width / LATENT_SCALE, mask.height / LATENT_SCALE)
        return InpaintMask.fromBlackWhitePixels(mask.width, mask.height, full)
    }

    /**
     * Reproduces NovelAI web's result matte:
     * 1/8 nearest mask -> 4px square dilation -> 8x nearest upscale -> radius-20 stack blur twice.
     */
    fun generatedAlphaMask(mask: InpaintMask): ByteArray {
        val lowWidth = latentDimension(mask.width)
        val lowHeight = latentDimension(mask.height)
        val low = downsampleToLatentGrid(mask)
        val dilated = dilateSquare(low, lowWidth, lowHeight, LOW_RES_DILATE_RADIUS)
        var alpha = IntArray(mask.width * mask.height) { index ->
            val x = index % mask.width
            val y = index / mask.width
            dilated[(y / LATENT_SCALE) * lowWidth + x / LATENT_SCALE].toInt() and 0xff
        }
        repeat(BLUR_ITERATIONS) {
            alpha = triangularBlur(alpha, mask.width, mask.height, FULL_RES_BLUR_RADIUS)
        }
        return ByteArray(alpha.size) { alpha[it].coerceIn(0, 255).toByte() }
    }

    fun composeArgb(source: IntArray, generated: IntArray, mask: InpaintMask): IntArray {
        require(source.size == mask.width * mask.height)
        require(generated.size == source.size)
        val matte = generatedAlphaMask(mask)
        return IntArray(source.size) { index ->
            val webWeight = (matte[index].toInt() and 0xff) / 255f
            val serverAlpha = (generated[index] ushr 24 and 0xff) / 255f
            blendOpaque(source[index], generated[index], webWeight * serverAlpha)
        }
    }

    private fun downsampleToLatentGrid(mask: InpaintMask): ByteArray {
        val lowWidth = latentDimension(mask.width)
        val lowHeight = latentDimension(mask.height)
        return ByteArray(lowWidth * lowHeight) { index ->
            val x = index % lowWidth
            val y = index / lowWidth
            // Matches the web client's nearest resize: floor((out + .5) * source / target).
            val sourceX = (((x + .5f) * mask.width) / lowWidth).toInt().coerceAtMost(mask.width - 1)
            val sourceY = (((y + .5f) * mask.height) / lowHeight).toInt().coerceAtMost(mask.height - 1)
            if (mask.pixel(sourceX, sourceY) > 155) InpaintMask.WHITE.toByte() else InpaintMask.BLACK.toByte()
        }
    }

    private fun upscaleNearest(low: ByteArray, lowWidth: Int, lowHeight: Int): ByteArray {
        val width = lowWidth * LATENT_SCALE
        val height = lowHeight * LATENT_SCALE
        return ByteArray(width * height) { index ->
            val x = index % width
            val y = index / width
            low[(y / LATENT_SCALE) * lowWidth + x / LATENT_SCALE]
        }
    }

    private fun dilateSquare(input: ByteArray, width: Int, height: Int, radius: Int): ByteArray {
        val output = ByteArray(input.size)
        for (y in 0 until height) for (x in 0 until width) {
            if ((input[y * width + x].toInt() and 0xff) != InpaintMask.WHITE) continue
            val minX = (x - radius).coerceAtLeast(0)
            val maxX = (x + radius).coerceAtMost(width - 1)
            val minY = (y - radius).coerceAtLeast(0)
            val maxY = (y + radius).coerceAtMost(height - 1)
            for (targetY in minY..maxY) {
                output.fill(InpaintMask.WHITE.toByte(), targetY * width + minX, targetY * width + maxX + 1)
            }
        }
        return output
    }

    /** Separable triangular blur, equivalent to the web worker's stack-blur kernel. */
    private fun triangularBlur(input: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val horizontal = IntArray(input.size)
        val output = IntArray(input.size)
        for (y in 0 until height) blurLine(input, horizontal, y * width, 1, width, radius)
        for (x in 0 until width) blurLine(horizontal, output, x, width, height, radius)
        return output
    }

    private fun blurLine(
        input: IntArray,
        output: IntArray,
        offset: Int,
        stride: Int,
        length: Int,
        radius: Int,
    ) {
        val prefix = LongArray(length + 1)
        for (position in 0 until length) {
            prefix[position + 1] = prefix[position] + input[offset + position * stride]
        }
        fun sample(position: Int): Int = input[offset + position.coerceIn(0, length - 1) * stride]
        fun rangeSum(from: Int, through: Int): Long {
            if (from > through) return 0
            val leftPadding = (-from).coerceAtLeast(0)
            val rightPadding = (through - length + 1).coerceAtLeast(0)
            val start = from.coerceAtLeast(0).coerceAtMost(length)
            val endExclusive = (through + 1).coerceAtLeast(0).coerceAtMost(length)
            val middle = if (start < endExclusive) prefix[endExclusive] - prefix[start] else 0
            return middle + leftPadding.toLong() * sample(0) + rightPadding.toLong() * sample(length - 1)
        }

        var weighted = 0L
        for (distance in -radius..radius) {
            weighted += (radius + 1 - abs(distance)).toLong() * sample(distance)
        }
        val divisor = (radius + 1L) * (radius + 1L)
        for (position in 0 until length) {
            output[offset + position * stride] = ((weighted + divisor / 2) / divisor).toInt()
            if (position + 1 < length) {
                weighted += rangeSum(position + 1, position + radius + 1)
                weighted -= rangeSum(position - radius, position)
            }
        }
    }

    private fun latentDimension(value: Int): Int {
        require(value >= LATENT_SCALE && value % LATENT_SCALE == 0) {
            "Inpaint dimensions must be divisible by $LATENT_SCALE"
        }
        return value / LATENT_SCALE
    }

    private fun blendOpaque(source: Int, generated: Int, generatedWeight: Float): Int {
        fun channel(shift: Int): Int {
            val from = source ushr shift and 0xff
            val to = generated ushr shift and 0xff
            return (from + (to - from) * generatedWeight).roundToInt().coerceIn(0, 255)
        }
        return (0xff shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
