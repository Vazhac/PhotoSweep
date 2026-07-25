package com.photosweep.app

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face
private fun largestFaceRatio(
    faces: List<com.google.mlkit.vision.face.Face>,
    bitmap: Bitmap,
): Float {
    if (faces.isEmpty() || bitmap.width == 0 || bitmap.height == 0) return 0f
    val imageArea = bitmap.width * bitmap.height.toFloat()
    return faces.maxOf { face ->
        (face.boundingBox.width() * face.boundingBox.height()) / imageArea
    }
}

private fun hasCenteredPrimaryFace(
    faces: List<com.google.mlkit.vision.face.Face>,
    bitmap: Bitmap,
): Boolean {
    val primary = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return false
    if (bitmap.width == 0 || bitmap.height == 0) return false
    val centerX = primary.boundingBox.centerX() / bitmap.width.toFloat()
    val centerY = primary.boundingBox.centerY() / bitmap.height.toFloat()
    return centerX in 0.3f..0.7f && centerY in 0.25f..0.7f
}

private fun approximateSharpness(bitmap: Bitmap): Float {
    val scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
    var diffSum = 0f
    var comparisons = 0
    for (y in 0 until scaled.height - 1) {
        for (x in 0 until scaled.width - 1) {
            val current = scaled.getPixel(x, y)
            val right = scaled.getPixel(x + 1, y)
            val down = scaled.getPixel(x, y + 1)
            diffSum += kotlin.math.abs(luminance(current) - luminance(right))
            diffSum += kotlin.math.abs(luminance(current) - luminance(down))
            comparisons += 2
        }
    }
    return if (comparisons == 0) 0f else diffSum / comparisons
}

private fun luminance(color: Int): Float {
    val red = (color shr 16) and 0xFF
    val green = (color shr 8) and 0xFF
    val blue = color and 0xFF
    return (0.299f * red) + (0.587f * green) + (0.114f * blue)
}
