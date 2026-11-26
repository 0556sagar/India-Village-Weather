package com.example.indiavillageweather.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

class WeatherBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class WeatherScene {
        SUNNY_DAY, CLEAR_NIGHT, CLOUDY, RAINY, THUNDERSTORM, SNOWY, FOGGY
    }

    private var currentScene = WeatherScene.SUNNY_DAY
    private var gradientColors = intArrayOf(Color.parseColor("#2980B9"), Color.parseColor("#6DD5FA"))
    private val bgPaint = Paint()
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private data class Particle(
        var x: Float,
        var y: Float,
        var speedY: Float,
        var speedX: Float,
        var size: Float,
        var alpha: Int
    )

    private val particles = mutableListOf<Particle>()
    private var sunAngle = 0f
    private var lightningAlpha = 0

    fun setWeatherCondition(weatherCode: Int, isDay: Boolean) {
        val newScene = when (weatherCode) {
            0, 1 -> if (isDay) WeatherScene.SUNNY_DAY else WeatherScene.CLEAR_NIGHT
            2, 3 -> WeatherScene.CLOUDY
            45, 48 -> WeatherScene.FOGGY
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> WeatherScene.RAINY
            71, 73, 75, 77, 85, 86 -> WeatherScene.SNOWY
            95, 96, 99 -> WeatherScene.THUNDERSTORM
            else -> if (isDay) WeatherScene.SUNNY_DAY else WeatherScene.CLEAR_NIGHT
        }

        if (newScene != currentScene || particles.isEmpty()) {
            currentScene = newScene
            updateThemeColors()
            initParticles()
        }
        invalidate()
    }

    private fun updateThemeColors() {
        gradientColors = when (currentScene) {
            WeatherScene.SUNNY_DAY -> intArrayOf(
                Color.parseColor("#1E88E5"),
                Color.parseColor("#64B5F6"),
                Color.parseColor("#B3E5FC")
            )
            WeatherScene.CLEAR_NIGHT -> intArrayOf(
                Color.parseColor("#0B1026"),
                Color.parseColor("#1B274A"),
                Color.parseColor("#2C3E6B")
            )
            WeatherScene.CLOUDY -> intArrayOf(
                Color.parseColor("#546E7A"),
                Color.parseColor("#78909C"),
                Color.parseColor("#B0BEC5")
            )
            WeatherScene.RAINY -> intArrayOf(
                Color.parseColor("#263238"),
                Color.parseColor("#37474F"),
                Color.parseColor("#546E7A")
            )
            WeatherScene.THUNDERSTORM -> intArrayOf(
                Color.parseColor("#141824"),
                Color.parseColor("#262D40"),
                Color.parseColor("#3B4259")
            )
            WeatherScene.SNOWY -> intArrayOf(
                Color.parseColor("#607D8B"),
                Color.parseColor("#90A4AE"),
                Color.parseColor("#CFD8DC")
            )
            WeatherScene.FOGGY -> intArrayOf(
                Color.parseColor("#757F9A"),
                Color.parseColor("#D7DDE8")
            )
        }
    }

    private fun initParticles() {
        particles.clear()
        val w = if (width > 0) width else 1080
        val h = if (height > 0) height else 2400

        val count = when (currentScene) {
            WeatherScene.RAINY, WeatherScene.THUNDERSTORM -> 90
            WeatherScene.SNOWY -> 60
            WeatherScene.CLEAR_NIGHT -> 50
            WeatherScene.CLOUDY, WeatherScene.FOGGY -> 8
            WeatherScene.SUNNY_DAY -> 0
        }

        repeat(count) {
            particles.add(
                Particle(
                    x = Random.nextFloat() * w,
                    y = Random.nextFloat() * h,
                    speedY = when (currentScene) {
                        WeatherScene.RAINY, WeatherScene.THUNDERSTORM -> Random.nextFloat() * 18f + 22f
                        WeatherScene.SNOWY -> Random.nextFloat() * 3f + 2f
                        else -> 0f
                    },
                    speedX = when (currentScene) {
                        WeatherScene.CLOUDY, WeatherScene.FOGGY -> Random.nextFloat() * 0.8f + 0.3f
                        WeatherScene.RAINY, WeatherScene.THUNDERSTORM -> -2.5f
                        else -> 0f
                    },
                    size = when (currentScene) {
                        WeatherScene.RAINY, WeatherScene.THUNDERSTORM -> Random.nextFloat() * 25f + 20f
                        WeatherScene.SNOWY -> Random.nextFloat() * 6f + 4f
                        WeatherScene.CLEAR_NIGHT -> Random.nextFloat() * 3f + 1.5f
                        else -> Random.nextFloat() * 180f + 140f
                    },
                    alpha = Random.nextInt(90, 220)
                )
            )
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initParticles()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        bgPaint.shader = LinearGradient(0f, 0f, 0f, h, gradientColors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        when (currentScene) {
            WeatherScene.SUNNY_DAY -> drawSunGlow(canvas, w)
            WeatherScene.CLEAR_NIGHT -> drawStars(canvas, w, h)
            WeatherScene.CLOUDY, WeatherScene.FOGGY -> drawClouds(canvas, w, h)
            WeatherScene.RAINY -> drawRain(canvas, w, h, withThunder = false)
            WeatherScene.THUNDERSTORM -> drawRain(canvas, w, h, withThunder = true)
            WeatherScene.SNOWY -> drawSnow(canvas, w, h)
        }

        postInvalidateOnAnimation()
    }

    private fun drawSunGlow(canvas: Canvas, w: Float) {
        sunAngle = (sunAngle + 0.3f) % 360f
        val cx = w * 0.82f
        val cy = height * 0.14f
        val pulse = (sin(Math.toRadians(sunAngle.toDouble())) * 18f).toFloat()

        sunPaint.color = Color.argb(45, 255, 238, 88)
        canvas.drawCircle(cx, cy, 240f + pulse, sunPaint)

        sunPaint.color = Color.argb(90, 255, 245, 157)
        canvas.drawCircle(cx, cy, 140f + pulse * 0.5f, sunPaint)

        sunPaint.color = Color.argb(220, 255, 249, 196)
        canvas.drawCircle(cx, cy, 75f, sunPaint)
    }

    private fun drawStars(canvas: Canvas, w: Float, h: Float) {
        particlePaint.color = Color.WHITE
        for (p in particles) {
            p.alpha = (120 + (sin((System.currentTimeMillis() / 300.0) + p.x) * 100)).toInt().coerceIn(40, 255)
            particlePaint.alpha = p.alpha
            canvas.drawCircle(p.x, p.y % (h * 0.65f), p.size, particlePaint)
        }
    }

    private fun drawClouds(canvas: Canvas, w: Float, h: Float) {
        particlePaint.color = Color.WHITE
        for (p in particles) {
            particlePaint.alpha = if (currentScene == WeatherScene.FOGGY) 55 else 40
            canvas.drawCircle(p.x, p.y % (h * 0.55f), p.size, particlePaint)
            canvas.drawCircle(p.x + p.size * 0.7f, (p.y % (h * 0.55f)) + 15f, p.size * 0.8f, particlePaint)
            p.x += p.speedX
            if (p.x - p.size > w) p.x = -p.size * 1.5f
        }
    }

    private fun drawRain(canvas: Canvas, w: Float, h: Float, withThunder: Boolean) {
        if (withThunder) {
            if (lightningAlpha > 0) {
                canvas.drawColor(Color.argb(lightningAlpha, 255, 255, 255))
                lightningAlpha = (lightningAlpha - 18).coerceAtLeast(0)
            } else if (Random.nextInt(180) == 1) {
                lightningAlpha = 140
            }
        }

        particlePaint.color = Color.parseColor("#B3E5FC")
        particlePaint.strokeWidth = 3.5f
        particlePaint.strokeCap = Paint.Cap.ROUND

        for (p in particles) {
            particlePaint.alpha = p.alpha
            canvas.drawLine(p.x, p.y, p.x + p.speedX, p.y + p.size, particlePaint)
            p.y += p.speedY
            p.x += p.speedX
            if (p.y > h) {
                p.y = -p.size
                p.x = Random.nextFloat() * w
            }
        }
    }

    private fun drawSnow(canvas: Canvas, w: Float, h: Float) {
        particlePaint.color = Color.WHITE
        for (p in particles) {
            particlePaint.alpha = p.alpha
            val sway = (sin(p.y / 40.0) * 2f).toFloat()
            canvas.drawCircle(p.x + sway, p.y, p.size, particlePaint)
            p.y += p.speedY
            if (p.y > h) {
                p.y = -p.size
                p.x = Random.nextFloat() * w
            }
        }
    }
}
