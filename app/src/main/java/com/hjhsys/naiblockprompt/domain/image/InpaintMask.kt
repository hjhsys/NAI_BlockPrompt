package com.hjhsys.naiblockprompt.domain.image

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/** UI-independent, 8-bit black/white mask in source-image pixel coordinates. */
class InpaintMask private constructor(
    val width: Int,
    val height: Int,
    private val pixels: ByteArray,
) {
    init {
        require(width > 0 && height > 0)
        require(width.toLong() * height == pixels.size.toLong())
    }

    val isEmpty: Boolean get() = pixels.none { it.toInt() != BLACK }
    val whitePixelCount: Int get() = pixels.count { (it.toInt() and 0xff) == WHITE }

    fun matchesSource(sourceWidth: Int, sourceHeight: Int): Boolean =
        width == sourceWidth && height == sourceHeight

    fun requireMatchesSource(sourceWidth: Int, sourceHeight: Int) {
        require(matchesSource(sourceWidth, sourceHeight)) {
            "Mask ${width}x$height does not match source ${sourceWidth}x$sourceHeight"
        }
    }

    fun pixel(x: Int, y: Int): Int {
        require(x in 0 until width && y in 0 until height)
        return pixels[y * width + x].toInt() and 0xff
    }

    /** Paints white (regenerate) or black (preserve/erase) in image pixel coordinates. */
    fun fillCircle(centerX: Int, centerY: Int, radius: Int, regenerate: Boolean = true) {
        require(radius >= 0)
        val color = if (regenerate) WHITE.toByte() else BLACK.toByte()
        val radiusSquared = radius.toLong() * radius
        val minX = (centerX - radius).coerceAtLeast(0)
        val maxX = (centerX + radius).coerceAtMost(width - 1)
        val minY = (centerY - radius).coerceAtLeast(0)
        val maxY = (centerY + radius).coerceAtMost(height - 1)
        for (y in minY..maxY) for (x in minX..maxX) {
            val dx = (x - centerX).toLong()
            val dy = (y - centerY).toLong()
            if (dx * dx + dy * dy <= radiusSquared) pixels[y * width + x] = color
        }
    }

    /** Draws a continuous stroke; every traversed source pixel is covered. */
    fun drawLine(fromX: Int, fromY: Int, toX: Int, toY: Int, radius: Int, regenerate: Boolean = true) {
        val dx = kotlin.math.abs(toX - fromX)
        val dy = kotlin.math.abs(toY - fromY)
        val steps = maxOf(dx, dy)
        if (steps == 0) return fillCircle(fromX, fromY, radius, regenerate)
        for (step in 0..steps) {
            val ratio = step.toFloat() / steps
            fillCircle(
                (fromX + (toX - fromX) * ratio).toInt(),
                (fromY + (toY - fromY) * ratio).toInt(),
                radius,
                regenerate,
            )
        }
    }

    fun copy(): InpaintMask = InpaintMask(width, height, pixels.copyOf())

    /** Defensive copy for rendering; values are always 0 or 255. */
    fun pixelBytes(): ByteArray = pixels.copyOf()

    /** Copies mask rows into a reusable render buffer without allocating per pointer event. */
    fun copyPixelsTo(destination: ByteArray, rowStride: Int = width) {
        require(rowStride >= width)
        require(destination.size >= rowStride * height)
        for (y in 0 until height) {
            pixels.copyInto(destination, y * rowStride, y * width, (y + 1) * width)
        }
    }

    fun clear() = pixels.fill(BLACK.toByte())

    /** Encodes the editable binary mask without changing its black/white pixels. */
    fun toPngBytes(): ByteArray = encodePng(pixels)

    /** Encodes the unchanged binary editor mask for the API. */
    fun toApiPngBytes(): ByteArray = encodePng(pixels)

    /** Opaque 8-bit RGBA PNG: black=preserve, white=regenerate. */
    private fun encodePng(encodedPixels: ByteArray): ByteArray {
        val raw = ByteArrayOutputStream()
        DeflaterOutputStream(raw).use { compressed ->
            for (y in 0 until height) {
                compressed.write(0) // PNG filter: None
                val rowStart = y * width
                for (x in 0 until width) {
                    val value = encodedPixels[rowStart + x].toInt() and 0xff
                    compressed.write(value)
                    compressed.write(value)
                    compressed.write(value)
                    compressed.write(OPAQUE_ALPHA)
                }
            }
        }
        return ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { png ->
                png.write(PNG_SIGNATURE)
                val header = ByteArrayOutputStream().also { bytes ->
                    DataOutputStream(bytes).use { ihdr ->
                        ihdr.writeInt(width)
                        ihdr.writeInt(height)
                        ihdr.writeByte(8) // bit depth
                        ihdr.writeByte(6) // RGBA, matching the NovelAI web mask payload
                        ihdr.writeByte(0) // compression
                        ihdr.writeByte(0) // filter
                        ihdr.writeByte(0) // no interlace
                    }
                }.toByteArray()
                png.writeChunk("IHDR", header)
                png.writeChunk("IDAT", raw.toByteArray())
                png.writeChunk("IEND", byteArrayOf())
            }
        }.toByteArray()
    }

    companion object {
        const val BLACK = 0
        const val WHITE = 255
        private const val OPAQUE_ALPHA = 255
        private val PNG_SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

        fun black(width: Int, height: Int): InpaintMask {
            require(width > 0 && height > 0)
            val size = width.toLong() * height
            require(size <= Int.MAX_VALUE) { "Mask is too large" }
            return InpaintMask(width, height, ByteArray(size.toInt()))
        }

        fun fromBlackWhitePixels(width: Int, height: Int, pixels: ByteArray): InpaintMask {
            require(pixels.all { (it.toInt() and 0xff) == BLACK || (it.toInt() and 0xff) == WHITE })
            return InpaintMask(width, height, pixels.copyOf())
        }
    }
}

private fun DataOutputStream.writeChunk(type: String, data: ByteArray) {
    val typeBytes = type.toByteArray(Charsets.US_ASCII)
    require(typeBytes.size == 4)
    writeInt(data.size)
    write(typeBytes)
    write(data)
    val crc = CRC32().apply { update(typeBytes); update(data) }
    writeInt(crc.value.toInt())
}
