package dev.ezadb.ui.viewmodel

import android.graphics.Bitmap

enum class ProgressPhase { PREPARING, WORKING, TRANSFERRING, FINISHING }

/** Live state of a long running operation (install, push, pull, screenshot). */
data class OperationProgress(
    val label: String,
    val transferred: Long = 0,
    val total: Long? = null,
    val phase: ProgressPhase = ProgressPhase.PREPARING,
    val startedAtMs: Long = System.currentTimeMillis(),
) {
    val fraction: Float?
        get() = if (total != null && total > 0) (transferred.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null

    fun bytesPerSecond(now: Long = System.currentTimeMillis()): Long? {
        val elapsed = now - startedAtMs
        if (elapsed < 400 || transferred <= 0) return null
        return transferred * 1000 / elapsed
    }

    fun etaSeconds(now: Long = System.currentTimeMillis()): Long? {
        val speed = bytesPerSecond(now) ?: return null
        val remaining = (total ?: return null) - transferred
        if (speed <= 0 || remaining <= 0) return null
        return remaining / speed
    }
}

/** An image already pulled from the device and ready to be displayed, saved or shared. */
data class ImagePreview(
    val name: String,
    val bitmap: Bitmap,
    val bytes: ByteArray,
    val mime: String,
) {
    val sizeBytes: Long get() = bytes.size.toLong()
    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height
}
