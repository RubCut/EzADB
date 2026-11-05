package dev.ezadb.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/** Pictures for demo mode, so previews and the screenshot viewer can be tried without hardware. */
object DemoImages {

    fun screenshot(width: Int = 720, height: Int = 1520): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), 0xFF0B3D2E.toInt(), 0xFF123B4F.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        val scale = width / 720f
        // status bar
        paint.color = Color.WHITE
        paint.textSize = 26f * scale
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("12:45", 40f * scale, 56f * scale, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("87%", width - 40f * scale, 56f * scale, paint)
        paint.textAlign = Paint.Align.LEFT

        // clock widget
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 96f * scale
        canvas.drawText("12:45", 60f * scale, 260f * scale, paint)
        paint.textSize = 30f * scale
        paint.color = 0xCCFFFFFF.toInt()
        canvas.drawText("Wednesday, 5 November", 62f * scale, 310f * scale, paint)

        // app grid
        val colors = intArrayOf(0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFFFF9800.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF00BCD4.toInt(), 0xFFFFC107.toInt(), 0xFF8BC34A.toInt())
        val cell = width / 4f
        for (index in 0 until 8) {
            val column = index % 4
            val row = index / 4
            val left = column * cell + cell * 0.22f
            val top = height * 0.55f + row * cell * 1.25f
            paint.color = colors[index]
            canvas.drawRoundRect(RectF(left, top, left + cell * 0.56f, top + cell * 0.56f), 28f * scale, 28f * scale, paint)
        }

        // dock
        paint.color = 0x33FFFFFF
        canvas.drawRoundRect(RectF(40f * scale, height - 170f * scale, width - 40f * scale, height - 60f * scale), 48f * scale, 48f * scale, paint)
        paint.color = 0xAAFFFFFF.toInt()
        paint.textSize = 24f * scale
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("EzADB demo screen", width / 2f, height - 105f * scale, paint)
        return encode(bitmap, Bitmap.CompressFormat.PNG)
    }

    fun photo(width: Int = 960, height: Int = 640): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFF1E88E5.toInt(), 0xFFB3E5FC.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        paint.color = 0xFFFFF176.toInt()
        canvas.drawCircle(width * 0.78f, height * 0.24f, height * 0.12f, paint)
        paint.color = 0xFF2E7D32.toInt()
        canvas.drawRoundRect(RectF(0f, height * 0.68f, width.toFloat(), height.toFloat()), 40f, 40f, paint)
        paint.color = 0xFF1B5E20.toInt()
        for (index in 0 until 5) {
            val x = width * (0.12f + index * 0.19f)
            canvas.drawCircle(x, height * 0.66f, height * 0.11f, paint)
        }
        paint.color = Color.WHITE
        paint.textSize = 30f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("EzADB demo photo", 40f, height - 40f, paint)
        return encode(bitmap, Bitmap.CompressFormat.JPEG)
    }

    fun icon(label: String, size: Int = 96): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = intArrayOf(0xFF43A047.toInt(), 0xFF1E88E5.toInt(), 0xFFFB8C00.toInt(), 0xFFE53935.toInt(), 0xFF8E24AA.toInt(), 0xFF00ACC1.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = palette[abs(label.hashCode()) % palette.size]
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.28f, size * 0.28f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size * 0.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val letter = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: 'A'
        canvas.drawText(letter.toString(), size / 2f, size * 0.68f, paint)
        return encode(bitmap, Bitmap.CompressFormat.PNG)
    }

    private fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(format, 92, output)
        bitmap.recycle()
        return output.toByteArray()
    }
}
