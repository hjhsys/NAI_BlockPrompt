package com.hjhsys.naiblockprompt.domain.image

data class DisplayRect(val left: Float, val top: Float, val width: Float, val height: Float)
data class SourcePixel(val x: Int, val y: Int)

class ImageDisplayMapping private constructor(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val display: DisplayRect,
) {
    fun toSource(x: Float, y: Float): SourcePixel? {
        if (x < display.left || y < display.top || x > display.left + display.width || y > display.top + display.height) return null
        val normalizedX = ((x - display.left) / display.width).coerceIn(0f, 1f)
        val normalizedY = ((y - display.top) / display.height).coerceIn(0f, 1f)
        return SourcePixel(
            (normalizedX * (sourceWidth - 1)).toInt(),
            (normalizedY * (sourceHeight - 1)).toInt(),
        )
    }

    fun sourceRadius(displayRadius: Float): Int =
        (displayRadius * sourceWidth / display.width).toInt().coerceAtLeast(1)

    companion object {
        fun fit(sourceWidth: Int, sourceHeight: Int, containerWidth: Float, containerHeight: Float): ImageDisplayMapping {
            require(sourceWidth > 0 && sourceHeight > 0 && containerWidth > 0 && containerHeight > 0)
            val scale = minOf(containerWidth / sourceWidth, containerHeight / sourceHeight)
            val width = sourceWidth * scale
            val height = sourceHeight * scale
            return ImageDisplayMapping(sourceWidth, sourceHeight, DisplayRect(
                (containerWidth - width) / 2f, (containerHeight - height) / 2f, width, height,
            ))
        }
    }
}
