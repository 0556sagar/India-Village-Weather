package com.example.indiavillageweather.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/**
 * Thin pill showing one day's min..max inside the week's overall range.
 * The fill colour follows temperature: cool blue -> marigold -> hot orange.
 */
class TempRangeBarView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var lo = 0f
    private var hi = 1f
    private var weekMin = 0f
    private var weekMax = 1f

    fun setRange(dayMin: Double, dayMax: Double, weekMin: Double, weekMax: Double) {
        lo = dayMin.toFloat()
        hi = dayMax.toFloat()
        this.weekMin = weekMin.toFloat()
        this.weekMax = if (weekMax > weekMin) weekMax.toFloat() else weekMin.toFloat() + 1f
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        fillPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(0xFF6EC6FF.toInt(), 0xFFFFD54F.toInt(), 0xFFFF7043.toInt()),
            null, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = 5f * density
        val top = (height - h) / 2f
        rect.set(0f, top, w, top + h)
        canvas.drawRoundRect(rect, h / 2, h / 2, track)
        val span = weekMax - weekMin
        val x1 = w * ((lo - weekMin) / span)
        val x2 = w * ((hi - weekMin) / span)
        rect.set(x1, top, maxOf(x2, x1 + h), top + h)
        canvas.drawRoundRect(rect, h / 2, h / 2, fillPaint)
    }
}
