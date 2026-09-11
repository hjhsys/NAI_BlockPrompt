package com.hjhsys.naiblockprompt.domain.image

data class MaskPoint(val x: Float, val y: Float)
data class MaskStroke(val points: List<MaskPoint>, val radius: Float, val erase: Boolean)

object MaskGeometry {
    fun point(x: Float, y: Float, width: Float, height: Float): MaskPoint {
        require(width > 0 && height > 0)
        return MaskPoint((x / width).coerceIn(0f, 1f), (y / height).coerceIn(0f, 1f))
    }
    fun pixel(point: MaskPoint, width: Int, height: Int) =
        MaskPoint(point.x * (width - 1), point.y * (height - 1))
}
