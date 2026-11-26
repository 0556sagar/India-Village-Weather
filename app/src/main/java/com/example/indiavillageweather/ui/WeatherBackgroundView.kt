package com.example.indiavillageweather.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class WeatherBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private enum class SceneType {
        CLEAR_DAY, CLEAR_NIGHT, CLOUDY_DAY, CLOUDY_NIGHT, RAIN, THUNDERSTORM, SNOW
    }

    private var currentScene = SceneType.CLEAR_DAY
    private var animPhase = 0f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 8f
    }
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 220, 240, 255)
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    private data class Particle(var x: Float, var y: Float, val speed: Float, val size: Float)

    private val raindrops = List(80) {
        Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.02f + 0.015f, Random.nextFloat() * 25f + 20f)
    }
    private val stars = List(60) {
        Particle(Random.nextFloat(), Random.nextFloat() * 0.6f, Random.nextFloat() * 5f, Random.nextFloat() * 3f + 1.5f)
    }

    private var cloudX1 = 0.1f
    private var cloudX2 = 0.6f

    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 20000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            animPhase = it.animatedValue as Float
            updateParticles()
            invalidate()
        }
    }

    init {
        animator.start()
    }

    fun setWeatherCondition(weatherCode: Int, isDay: Boolean) {
        currentScene = when {
            weatherCode in 0..1 -> if (isDay) SceneType.CLEAR_DAY else SceneType.CLEAR_NIGHT
            weatherCode in 2..48 -> if (isDay) SceneType.CLOUDY_DAY else SceneType.CLOUDY_NIGHT
            weatherCode in 71..77 || weatherCode in 85..86 -> SceneType.SNOW
            weatherCode in 95..99 -> SceneType.THUNDERSTORM
            else -> SceneType.RAIN
        }
        invalidate()
    }

    private fun updateParticles() {
        cloudX1 += 0.0008f
        if (cloudX1 > 1.3f) cloudX1 = -0.4f

        cloudX2 += 0.0005f
        if (cloudX2 > 1.3f) cloudX2 = -0.5f

        if (currentScene == SceneType.RAIN || currentScene == SceneType.THUNDERSTORM || currentScene == SceneType.SNOW) {
            val speedFactor = if (currentScene == SceneType.SNOW) 0.3f else 1f
            for (drop in raindrops) {
                drop.y += drop.speed * speedFactor
                if (drop.y > 1f) {
                    drop.y = -0.05f
                    drop.x = Random.nextFloat()
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val colors = when (currentScene) {
            SceneType.CLEAR_DAY -> intArrayOf(Color.parseColor("#1E88E5"), Color.parseColor("#64B5F6"), Color.parseColor("#BBDEFB"))
            SceneType.CLEAR_NIGHT -> intArrayOf(Color.parseColor("#0B132B"), Color.parseColor("#1C2541"), Color.parseColor("#3A506B"))
            SceneType.CLOUDY_DAY -> intArrayOf(Color.parseColor("#546E7A"), Color.parseColor("#78909C"), Color.parseColor("#B0BEC5"))
            SceneType.CLOUDY_NIGHT -> intArrayOf(Color.parseColor("#1A232E"), Color.parseColor("#2C3A47"), Color.parseColor("#485460"))
            SceneType.RAIN -> intArrayOf(Color.parseColor("#1C2833"), Color.parseColor("#2E4053"), Color.parseColor("#566573"))
            SceneType.THUNDERSTORM -> intArrayOf(Color.parseColor("#0F1419"), Color.parseColor("#1F2833"), Color.parseColor("#2C3E50"))
            SceneType.SNOW -> intArrayOf(Color.parseColor("#607D8B"), Color.parseColor("#90A4AE"), Color.parseColor("#CFD8DC"))
        }
        bgPaint.shader = LinearGradient(0f, 0f, 0f, h, colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        when (currentScene) {
            SceneType.CLEAR_DAY -> drawSun(canvas, w * 0.8f, h * 0.14f)
            SceneType.CLEAR_NIGHT -> drawNightSky(canvas, w, h)
            SceneType.CLOUDY_DAY -> {
                drawSun(canvas, w * 0.78f, h * 0.14f, alpha = 160)
                drawCloud(canvas, w * cloudX1, h * 0.16f, 1.0f, Color.argb(210, 255, 255, 255))
                drawCloud(canvas, w * cloudX2, h * 0.25f, 1.3f, Color.argb(170, 240, 245, 250))
            }
            SceneType.CLOUDY_NIGHT -> {
                drawNightSky(canvas, w, h)
                drawCloud(canvas, w * cloudX1, h * 0.18f, 1.1f, Color.argb(140, 120, 135, 150))
            }
            SceneType.RAIN -> {
                drawCloud(canvas, w * cloudX1, h * 0.14f, 1.3f, Color.argb(180, 90, 105, 120))
                drawRain(canvas, w, h)
            }
            SceneType.THUNDERSTORM -> {
                drawCloud(canvas, w * cloudX1, h * 0.14f, 1.4f, Color.argb(210, 50, 60, 70))
                drawRain(canvas, w, h)
                if (Random.nextInt(100) < 3) {
                    canvas.drawColor(Color.argb(90, 255, 255, 255))
                }
            }
            SceneType.SNOW -> drawSnow(canvas, w, h)
        }
    }

    private fun drawSun(canvas: Canvas, cx: Float, cy: Float, alpha: Int = 255) {
        val pulse = 1f + 0.06f * sin(Math.toRadians((animPhase * 4).toDouble())).toFloat()
        val radius = 85f * pulse

        sunGlowPaint.shader = RadialGradient(
            cx, cy, radius * 2.4f,
            intArrayOf(Color.argb((alpha * 0.6f).toInt(), 255, 238, 88), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 2.4f, sunGlowPaint)

        rayPaint.color = Color.argb((alpha * 0.65f).toInt(), 255, 245, 157)
        for (i in 0 until 12) {
            val angle = Math.toRadians((animPhase + i * 30f).toDouble())
            val startX = cx + cos(angle).toFloat() * (radius * 1.25f)
            val startY = cy + sin(angle).toFloat() * (radius * 1.25f)
            val endX = cx + cos(angle).toFloat() * (radius * 1.75f)
            val endY = cy + sin(angle).toFloat() * (radius * 1.75f)
            canvas.drawLine(startX, startY, endX, endY, rayPaint)
        }

        sunPaint.color = Color.argb(alpha, 255, 213, 79)
        canvas.drawCircle(cx, cy, radius, sunPaint)
    }

    private fun drawNightSky(canvas: Canvas, w: Float, h: Float) {
        for (star in stars) {
            val twinkle = (120 + 135 * sin(Math.toRadians((animPhase * 3 + star.speed * 50).toDouble()))).toInt()
            starPaint.alpha = twinkle.coerceIn(40, 255)
            canvas.drawCircle(star.x * w, star.y * h, star.size, starPaint)
        }
        sunPaint.color = Color.parseColor("#FFF9C4")
        canvas.drawCircle(w * 0.8f, h * 0.14f, 65f, sunPaint)
    }

    private fun drawCloud(canvas: Canvas, cx: Float, cy: Float, scale: Float, color: Int) {
        cloudPaint.color = color
        canvas.drawCircle(cx, cy, 65f * scale, cloudPaint)
        canvas.drawCircle(cx + 55f * scale, cy - 20f * scale, 80f * scale, cloudPaint)
        canvas.drawCircle(cx + 120f * scale, cy - 10f * scale, 70f * scale, cloudPaint)
        canvas.drawCircle(cx + 170f * scale, cy + 10f * scale, 50f * scale, cloudPaint)
    }

    private fun drawRain(canvas: Canvas, w: Float, h: Float) {
        for (drop in raindrops) {
            val x = drop.x * w
            val y = drop.y * h
            canvas.drawLine(x, y, x - 8f, y + drop.size, rainPaint)
        }
    }

    private fun drawSnow(canvas: Canvas, w: Float, h: Float) {
        starPaint.alpha = 210
        for (flake in raindrops) {
            val x = flake.x * w + sin(flake.y * 15f) * 15f
            val y = flake.y * h
            canvas.drawCircle(x, y, 6f, starPaint)
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
