package com.example.indiavillageweather.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full-screen living sky. Every WMO weather code maps to one of the scenes below.
 *
 *  - frame-rate independent (delta-time) animation, driven by postInvalidateOnAnimation
 *  - zero allocations in onDraw (paths, paints, shaders and bitmaps are pre-built)
 *  - cross-fades the sky gradient when the weather changes
 *  - respects the system "remove animations" setting
 */
class WeatherBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Scene {
        CLEAR_DAY, CLEAR_NIGHT, PARTLY_DAY, PARTLY_NIGHT,
        OVERCAST_DAY, OVERCAST_NIGHT, FOG, DRIZZLE, RAIN, STORM,
        SNOW_DAY, SNOW_NIGHT, DUSK
    }

    // ---------------------------------------------------------------- palettes
    private val palettes: Map<Scene, IntArray> = mapOf(
        Scene.CLEAR_DAY to intArrayOf(0xFF1565C0.toInt(), 0xFF2F8FE0.toInt(), 0xFF8FD3F4.toInt()),
        Scene.CLEAR_NIGHT to intArrayOf(0xFF050B1F.toInt(), 0xFF0F1B3D.toInt(), 0xFF2A4570.toInt()),
        Scene.PARTLY_DAY to intArrayOf(0xFF2A74BF.toInt(), 0xFF5AA0DA.toInt(), 0xFFA6D2EE.toInt()),
        Scene.PARTLY_NIGHT to intArrayOf(0xFF0A1226.toInt(), 0xFF18284A.toInt(), 0xFF36527E.toInt()),
        Scene.OVERCAST_DAY to intArrayOf(0xFF46586A.toInt(), 0xFF6A7F92.toInt(), 0xFF9DB0BF.toInt()),
        Scene.OVERCAST_NIGHT to intArrayOf(0xFF121A25.toInt(), 0xFF202C3B.toInt(), 0xFF38475A.toInt()),
        Scene.FOG to intArrayOf(0xFF5A6A74.toInt(), 0xFF7F909A.toInt(), 0xFFAAB8BF.toInt()),
        Scene.DRIZZLE to intArrayOf(0xFF3A5062.toInt(), 0xFF587386.toInt(), 0xFF86A0B1.toInt()),
        Scene.RAIN to intArrayOf(0xFF1B2836.toInt(), 0xFF30445A.toInt(), 0xFF506B84.toInt()),
        Scene.STORM to intArrayOf(0xFF080C14.toInt(), 0xFF171F2D.toInt(), 0xFF2B384B.toInt()),
        Scene.SNOW_DAY to intArrayOf(0xFF5B778D.toInt(), 0xFF8AA5BA.toInt(), 0xFFC5D6E3.toInt()),
        Scene.SNOW_NIGHT to intArrayOf(0xFF1B2838.toInt(), 0xFF364D66.toInt(), 0xFF667F97.toInt()),
        Scene.DUSK to intArrayOf(0xFF29306B.toInt(), 0xFFC0507A.toInt(), 0xFFFFB070.toInt())
    )

    // ------------------------------------------------------------------- state
    private val dm = resources.displayMetrics
    private var scene = Scene.CLEAR_DAY
    private var windKmh = 8f
    private var fade = 1f
    private var time = 0f
    private var lastNanos = 0L

    private val cur = IntArray(3)
    private val from = IntArray(3)
    private var blend = 1f
    private val argb = ArgbEvaluator()
    private var bgShader: Shader? = null
    private var shaderDirty = true

    init {
        palettes.getValue(Scene.CLEAR_DAY).copyInto(cur)
        cur.copyInto(from)
    }

    private fun dp(v: Float) = v * dm.density
    private fun al(a: Int) = (a * fade).toInt().coerceIn(0, 255)
    private val pif = PI.toFloat()

    // ------------------------------------------------------------------ paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val shaftPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF6D6.toInt() }
    private val moonGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val hillBackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hillFrontPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val birdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFF14283C.toInt()
    }
    private val rainPaints = Array(3) { Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND } }
    private val snowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dropFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dropRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE }
    private val dropTrail = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = Color.WHITE }
    private val boltGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val flashPaint = Paint()
    private val veilPaint = Paint()
    private val shootPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = Color.WHITE }

    // --------------------------------------------------------- reusable shapes
    private val moonPath = Path()
    private val moonCut = Path()
    private val hillBack = Path()
    private val hillFront = Path()
    private val birdPath = Path()
    private val boltPath = Path()
    private val branchPath = Path()
    private val shafts = Array(3) { Path() }
    private val dst = RectF()

    private var sunX = 0f
    private var sunY = 0f
    private var sunR = 0f
    private var moonX = 0f
    private var moonY = 0f
    private var moonR = 0f

    // ------------------------------------------------------------------ clouds
    private class Cloud(var x: Float, val y: Float, val scale: Float, val speed: Float, val shape: Int, val alpha: Int)

    private val clouds = ArrayList<Cloud>()
    private val cloudBitmaps = arrayOfNulls<Bitmap>(3)
    private var cloudW = 0f
    private var cloudH = 0f
    private var cloudFilter: LightingColorFilter = LightingColorFilter(0xFFFFFFFF.toInt(), 0)

    // ------------------------------------------------------------------- stars
    private val starCount = 90
    private val starX = FloatArray(starCount) { Random.nextFloat() }
    private val starY = FloatArray(starCount) { Random.nextFloat() * 0.5f }
    private val starS = FloatArray(starCount) { 0.6f + Random.nextFloat() * 1.6f }
    private val starPh = FloatArray(starCount) { Random.nextFloat() * 6.28f }
    private val starSp = FloatArray(starCount) { 0.8f + Random.nextFloat() * 2.2f }
    private var ssLife = 0f
    private var ssNext = 3f
    private var ssX = 0f
    private var ssY = 0f
    private var ssVx = 0f
    private var ssVy = 0f

    // ------------------------------------------------------------------- birds
    private class Bird(var x: Float, val y: Float, val size: Float, val speed: Float, val phase: Float)

    private val birds = ArrayList<Bird>()

    // ---------------------------------------------------------- rain and snow
    private class Streak(var x: Float, var y: Float, val speed: Float)
    private class Flake(var x: Float, var y: Float, val speed: Float, val size: Float, val sway: Float, val phase: Float)
    private class Drop(
        var x: Float, var y: Float, val r: Float, var timer: Float,
        var sliding: Boolean, var startY: Float, var v: Float, var trail: Float
    )

    private val rainLayers = Array(3) { ArrayList<Streak>() }
    private val rainLines = FloatArray(260 * 4)
    private val snowLayers = Array(3) { ArrayList<Flake>() }
    private val drops = ArrayList<Drop>()
    private var rainCount = 0
    private var snowy = false

    // --------------------------------------------------------------- lightning
    private var boltT = 99f
    private var boltNext = 1.5f
    private var boltVisible = false
    private var flash = 0f

    // ======================================================================= API

    /** Backwards-compatible entry point. */
    fun setWeatherCondition(weatherCode: Int, isDay: Boolean) = setWeather(weatherCode, isDay, false, windKmh)

    /**
     * @param weatherCode Open-Meteo WMO weather code
     * @param twilight    true within ~50 min of sunrise/sunset (paints the golden-hour sky)
     * @param windKmh     slants rain, speeds clouds and drifts snow
     */
    fun setWeather(weatherCode: Int, isDay: Boolean, twilight: Boolean = false, windKmh: Float = this.windKmh) {
        val next = sceneFor(weatherCode, isDay, twilight)
        this.windKmh = windKmh.coerceIn(0f, 80f)
        if (next != scene) {
            cur.copyInto(from)
            blend = 0f
            fade = 0f
            scene = next
            configureScene()
        }
        invalidate()
    }

    private fun sceneFor(code: Int, isDay: Boolean, twilight: Boolean): Scene = when {
        code in 95..99 -> Scene.STORM
        code in 71..77 || code in 85..86 -> if (isDay) Scene.SNOW_DAY else Scene.SNOW_NIGHT
        code in 61..67 || code in 80..82 -> Scene.RAIN
        code in 51..57 -> Scene.DRIZZLE
        code in 45..48 -> Scene.FOG
        code == 3 -> if (isDay) Scene.OVERCAST_DAY else Scene.OVERCAST_NIGHT
        code == 2 -> if (twilight) Scene.DUSK else if (isDay) Scene.PARTLY_DAY else Scene.PARTLY_NIGHT
        else -> if (twilight) Scene.DUSK else if (isDay) Scene.CLEAR_DAY else Scene.CLEAR_NIGHT
    }

    // =========================================================== scene set-up

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        buildCloudBitmaps(w)
        configureScene()
    }

    private fun configureScene() {
        if (width <= 0 || height <= 0) return
        snowy = scene == Scene.SNOW_DAY || scene == Scene.SNOW_NIGHT
        cloudFilter = LightingColorFilter(cloudTint(), 0)
        buildCloudField()
        buildParticles()
        buildStatic()
        boltT = 99f
        boltNext = 1.2f
        shaderDirty = true
    }

    private fun cloudTint(): Int = when (scene) {
        Scene.CLEAR_DAY, Scene.PARTLY_DAY -> 0xFFFFFFFF
        Scene.CLEAR_NIGHT -> 0xFF6B7A99
        Scene.PARTLY_NIGHT -> 0xFF7A89A8
        Scene.OVERCAST_DAY -> 0xFFC4CED8
        Scene.OVERCAST_NIGHT -> 0xFF5B6878
        Scene.FOG -> 0xFFE6EDF1
        Scene.DRIZZLE -> 0xFFA9B6C2
        Scene.RAIN -> 0xFF8794A3
        Scene.STORM -> 0xFF4A5463
        Scene.SNOW_DAY -> 0xFFDCE6EE
        Scene.SNOW_NIGHT -> 0xFF8795A6
        Scene.DUSK -> 0xFFFFC7B0
    }.toInt()

    private fun buildCloudBitmaps(w: Int) {
        cloudBitmaps.forEach { it?.recycle() }
        val cw = (w * 0.75f).toInt().coerceAtLeast(96)
        val ch = (cw * 0.5f).toInt()
        cloudW = cw.toFloat()
        cloudH = ch.toFloat()
        for (k in 0 until 3) {
            val bmp = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val rnd = Random(40 + k * 7)
            val puffs = 7
            for (i in 0 until puffs) {
                val t = i / (puffs - 1f)
                val cx = cw * (0.16f + 0.68f * t)
                val lift = sin(t * pif) * 0.22f + rnd.nextFloat() * 0.1f
                val cy = ch * (0.64f - lift)
                val r = ch * (0.26f + rnd.nextFloat() * 0.16f + sin(t * pif) * 0.1f)
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.shader = RadialGradient(
                    cx, cy, r,
                    intArrayOf(0xFFFFFFFF.toInt(), 0xF2FFFFFF.toInt(), 0x00FFFFFF),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
                c.drawCircle(cx, cy, r, p)
            }
            // flat, slightly darker underside so the cloud has volume
            val shade = Paint(Paint.ANTI_ALIAS_FLAG)
            shade.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            shade.shader = LinearGradient(
                0f, ch * 0.2f, 0f, ch * 0.9f,
                0x00FFFFFF, 0xDD8E9FB6.toInt(), Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, cw.toFloat(), ch.toFloat(), shade)
            cloudBitmaps[k] = bmp
        }
    }

    private fun buildCloudField() {
        clouds.clear()
        // count, minScale, maxScale, yMin, yMax, alpha
        val spec: FloatArray = when (scene) {
            Scene.CLEAR_DAY -> floatArrayOf(3f, 0.45f, 0.7f, 0.08f, 0.32f, 190f)
            Scene.CLEAR_NIGHT -> floatArrayOf(2f, 0.5f, 0.8f, 0.10f, 0.30f, 90f)
            Scene.PARTLY_DAY, Scene.PARTLY_NIGHT -> floatArrayOf(5f, 0.6f, 1.1f, 0.05f, 0.34f, 225f)
            Scene.OVERCAST_DAY, Scene.OVERCAST_NIGHT -> floatArrayOf(8f, 0.9f, 1.5f, 0.02f, 0.38f, 235f)
            Scene.FOG -> floatArrayOf(3f, 1.2f, 1.7f, 0.10f, 0.40f, 120f)
            Scene.DRIZZLE -> floatArrayOf(7f, 0.9f, 1.4f, 0.02f, 0.32f, 225f)
            Scene.RAIN -> floatArrayOf(8f, 1.0f, 1.6f, 0.0f, 0.30f, 240f)
            Scene.STORM -> floatArrayOf(9f, 1.1f, 1.8f, -0.02f, 0.28f, 250f)
            Scene.SNOW_DAY, Scene.SNOW_NIGHT -> floatArrayOf(6f, 0.9f, 1.4f, 0.02f, 0.30f, 215f)
            Scene.DUSK -> floatArrayOf(4f, 0.6f, 1.0f, 0.10f, 0.40f, 215f)
        }
        val n = spec[0].toInt()
        val windMul = 1f + windKmh / 25f
        for (i in 0 until n) {
            val t = i / max(1f, n - 1f)
            clouds.add(
                Cloud(
                    x = -0.2f + 1.5f * ((t + Random.nextFloat() * 0.15f) % 1f),
                    y = spec[3] + (spec[4] - spec[3]) * Random.nextFloat(),
                    scale = spec[1] + (spec[2] - spec[1]) * Random.nextFloat(),
                    speed = (0.004f + Random.nextFloat() * 0.008f) * windMul,
                    shape = Random.nextInt(3),
                    alpha = (spec[5] * (0.75f + 0.25f * Random.nextFloat())).toInt()
                )
            )
        }
    }

    private fun buildParticles() {
        val w = width.toFloat()
        val h = height.toFloat()

        // rain
        for (l in rainLayers) l.clear()
        rainCount = when (scene) {
            Scene.DRIZZLE -> 70
            Scene.RAIN -> 150
            Scene.STORM -> 240
            else -> 0
        }
        val share = floatArrayOf(0.35f, 0.35f, 0.30f)
        val speedMul = floatArrayOf(0.55f, 0.8f, 1.1f)
        val base = if (scene == Scene.DRIZZLE) 0.55f else if (scene == Scene.STORM) 1.35f else 1.0f
        for (layer in 0 until 3) {
            val n = (rainCount * share[layer]).toInt()
            for (i in 0 until n) {
                rainLayers[layer].add(
                    Streak(
                        x = -0.25f + Random.nextFloat() * 1.5f,
                        y = Random.nextFloat() * 1.1f,
                        speed = base * speedMul[layer] * (0.75f + Random.nextFloat() * 0.5f)
                    )
                )
            }
        }

        // snow
        for (l in snowLayers) l.clear()
        if (snowy) {
            val counts = intArrayOf(50, 45, 35)
            val sizes = floatArrayOf(1.6f, 2.8f, 4.6f)
            val speeds = floatArrayOf(0.035f, 0.06f, 0.095f)
            for (layer in 0 until 3) {
                for (i in 0 until counts[layer]) {
                    snowLayers[layer].add(
                        Flake(
                            x = Random.nextFloat(), y = Random.nextFloat(),
                            speed = speeds[layer] * (0.8f + Random.nextFloat() * 0.5f),
                            size = dp(sizes[layer]) * (0.8f + Random.nextFloat() * 0.5f),
                            sway = 0.012f + Random.nextFloat() * 0.02f,
                            phase = Random.nextFloat() * 6.28f
                        )
                    )
                }
            }
        }

        // droplets sliding down the "window"
        drops.clear()
        val dropCount = when (scene) {
            Scene.DRIZZLE -> 6
            Scene.RAIN -> 12
            Scene.STORM -> 16
            else -> 0
        }
        for (i in 0 until dropCount) {
            drops.add(
                Drop(
                    x = Random.nextFloat() * w, y = Random.nextFloat() * h * 0.7f,
                    r = dp(2.2f + Random.nextFloat() * 2.8f), timer = 0.5f + Random.nextFloat() * 4f,
                    sliding = false, startY = 0f, v = 0f, trail = 0f
                )
            )
        }

        // birds
        birds.clear()
        if (scene == Scene.CLEAR_DAY || scene == Scene.PARTLY_DAY) {
            for (i in 0 until 3) {
                birds.add(
                    Bird(
                        x = Random.nextFloat(), y = 0.10f + Random.nextFloat() * 0.18f,
                        size = dp(7f + Random.nextFloat() * 4f), speed = 0.012f + Random.nextFloat() * 0.01f,
                        phase = Random.nextFloat() * 6.28f
                    )
                )
            }
        }
    }

    private fun buildStatic() {
        val w = width.toFloat()
        val h = height.toFloat()

        // --- sun
        val dusk = scene == Scene.DUSK
        sunX = w * 0.78f
        sunY = if (dusk) h * 0.30f else h * 0.15f
        sunR = dp(34f)
        glowPaint.shader = RadialGradient(
            sunX, sunY, dp(200f),
            intArrayOf(if (dusk) 0x99FF9A52.toInt() else 0x99FFEE58.toInt(), 0x00FFEE58),
            null, Shader.TileMode.CLAMP
        )
        discPaint.shader = RadialGradient(
            sunX - sunR * 0.25f, sunY - sunR * 0.25f, sunR * 1.5f,
            intArrayOf(
                if (dusk) 0xFFFFF1B0.toInt() else 0xFFFFF59D.toInt(),
                if (dusk) 0xFFFF7A3D.toInt() else 0xFFFFB300.toInt()
            ),
            null, Shader.TileMode.CLAMP
        )
        // light shafts fanning to the lower-left
        shaftPaint.shader = LinearGradient(
            sunX, sunY, sunX - w * 0.55f, h * 0.9f,
            0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        for (i in 0 until 3) {
            val by = h * 0.92f
            val cx = sunX - (0.30f + 0.26f * i) * w
            shafts[i].reset()
            shafts[i].moveTo(sunX, sunY)
            shafts[i].lineTo(cx - w * 0.05f, by)
            shafts[i].lineTo(cx + w * 0.05f, by)
            shafts[i].close()
        }

        // --- moon (crescent)
        moonX = w * 0.78f
        moonY = h * 0.14f
        moonR = dp(26f)
        moonPath.reset()
        moonPath.addCircle(moonX, moonY, moonR, Path.Direction.CW)
        moonCut.reset()
        moonCut.addCircle(moonX + moonR * 0.55f, moonY - moonR * 0.25f, moonR * 0.92f, Path.Direction.CW)
        moonPath.op(moonCut, Path.Op.DIFFERENCE)
        moonGlowPaint.shader = RadialGradient(
            moonX, moonY, moonR * 3.6f,
            intArrayOf(0x5AFFF6D6, 0x00FFF6D6),
            null, Shader.TileMode.CLAMP
        )

        // --- rolling hills at the horizon
        buildHill(hillBack, w, h, h * 0.865f, 3.1f, 0.6f, h * 0.020f)
        buildHill(hillFront, w, h, h * 0.925f, 2.3f, 2.2f, h * 0.014f)
    }

    private fun buildHill(p: Path, w: Float, h: Float, baseY: Float, freq: Float, ph: Float, amp: Float) {
        p.reset()
        p.moveTo(0f, h)
        var x = 0f
        val step = dp(10f)
        while (x <= w + step) {
            val t = x / w
            val y = baseY + sin(t * freq * 2f + ph) * amp + sin(t * freq * 5f + ph * 2f) * amp * 0.35f
            if (x == 0f) p.lineTo(0f, y) else p.lineTo(x, y)
            x += step
        }
        p.lineTo(w, h)
        p.close()
    }

    // ============================================================== per-frame

    private fun step(dt: Float, w: Float, h: Float, animate: Boolean) {
        if (!animate) {
            blend = 1f
            fade = 1f
        } else {
            time += dt
            fade = min(1f, fade + dt / 0.7f)
        }
        if (blend < 1f) {
            blend = min(1f, blend + dt / 0.9f)
            val e = blend * blend * (3f - 2f * blend)
            val to = palettes.getValue(scene)
            for (i in 0 until 3) cur[i] = argb.evaluate(e, from[i], to[i]) as Int
            shaderDirty = true
        } else if (!animate) {
            palettes.getValue(scene).copyInto(cur)
            shaderDirty = true
        }
        if (shaderDirty) {
            bgShader = LinearGradient(0f, 0f, 0f, h, cur, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            bgPaint.shader = bgShader
            updateHillColors()
            shaderDirty = false
        }
        if (!animate) return

        // clouds
        val cw0 = cloudW
        for (c in clouds) {
            c.x += c.speed * dt
            val half = (cw0 * c.scale * 0.5f) / w
            if (c.x - half > 1f) c.x = -half - 0.02f
        }

        // stars and shooting star
        if (hasStars()) {
            ssNext -= dt
            if (ssNext <= 0f && ssLife <= 0f) {
                ssX = w * (0.35f + Random.nextFloat() * 0.6f)
                ssY = h * (0.03f + Random.nextFloat() * 0.18f)
                ssVx = -w * (0.7f + Random.nextFloat() * 0.4f)
                ssVy = h * (0.22f + Random.nextFloat() * 0.12f)
                ssLife = 0.8f
                ssNext = 6f + Random.nextFloat() * 9f
            }
            if (ssLife > 0f) {
                ssX += ssVx * dt
                ssY += ssVy * dt
                ssLife -= dt
            }
        }

        // birds
        for (b in birds) {
            b.x += b.speed * dt
            if (b.x > 1.1f) b.x = -0.1f
        }

        // rain
        if (rainCount > 0) {
            val slant = slant()
            for (layer in 0 until 3) {
                for (s in rainLayers[layer]) {
                    s.y += s.speed * dt
                    s.x -= slant * s.speed * dt * (h / w)
                    if (s.y > 1.08f) {
                        s.y = -0.08f
                        s.x = -0.25f + Random.nextFloat() * 1.5f
                    }
                }
            }
            for (d in drops) updateDrop(d, dt, w, h)
        }

        // snow
        if (snowy) {
            val drift = (windKmh / 80f) * 0.05f
            for (layer in 0 until 3) {
                for (f in snowLayers[layer]) {
                    f.y += f.speed * dt
                    f.x += drift * dt
                    if (f.x > 1.05f) f.x = -0.05f
                    if (f.y > 1.04f) {
                        f.y = -0.04f
                        f.x = Random.nextFloat()
                    }
                }
            }
        }

        // lightning
        if (scene == Scene.STORM) {
            boltNext -= dt
            if (boltNext <= 0f) {
                boltVisible = Random.nextFloat() < 0.65f
                if (boltVisible) spawnBolt(w, h)
                boltT = 0f
                boltNext = 2.5f + Random.nextFloat() * 5.5f
            }
            boltT += dt
            flash = when {
                boltT < 0.06f -> 1f
                boltT < 0.13f -> 0.25f
                boltT < 0.20f -> 0.9f
                boltT < 0.55f -> 0.9f * (1f - (boltT - 0.20f) / 0.35f)
                else -> 0f
            }
        } else {
            flash = 0f
        }
    }

    private fun slant(): Float = 0.08f + (windKmh / 40f).coerceIn(0f, 1f) * 0.42f

    private fun updateDrop(d: Drop, dt: Float, w: Float, h: Float) {
        if (!d.sliding) {
            d.trail = max(0f, d.trail - dt * 0.35f)
            d.timer -= dt
            if (d.timer <= 0f) {
                d.sliding = true
                d.startY = d.y
                d.timer = 0.5f + Random.nextFloat() * 0.9f
                d.v = h * (0.10f + Random.nextFloat() * 0.12f)
                d.trail = 1f
            }
        } else {
            d.y += d.v * dt
            d.v -= d.v * 1.4f * dt
            d.timer -= dt
            if (d.timer <= 0f) {
                d.sliding = false
                d.timer = 1f + Random.nextFloat() * 4f
            }
        }
        if (d.y > h * 0.95f) {
            d.x = Random.nextFloat() * w
            d.y = Random.nextFloat() * h * 0.5f
            d.trail = 0f
            d.sliding = false
        }
    }

    private fun spawnBolt(w: Float, h: Float) {
        boltPath.reset()
        branchPath.reset()
        var x = w * (0.15f + Random.nextFloat() * 0.7f)
        var y = h * 0.18f
        boltPath.moveTo(x, y)
        val endY = h * (0.50f + Random.nextFloat() * 0.30f)
        val steps = 16
        val dy = (endY - y) / steps
        for (i in 1..steps) {
            x += (Random.nextFloat() - 0.5f) * dp(46f)
            y += dy
            boltPath.lineTo(x, y)
            if (i in 4..11 && Random.nextFloat() < 0.22f) {
                var bx = x
                var by = y
                val dir = if (Random.nextBoolean()) 1f else -1f
                branchPath.moveTo(bx, by)
                for (k in 0 until 4) {
                    bx += dir * Random.nextFloat() * dp(20f)
                    by += dy * (0.6f + Random.nextFloat() * 0.5f)
                    branchPath.lineTo(bx, by)
                }
            }
        }
    }

    private fun hasStars() = scene == Scene.CLEAR_NIGHT || scene == Scene.PARTLY_NIGHT || scene == Scene.DUSK

    private fun updateHillColors() {
        if (snowy) {
            hillBackPaint.color = Color.argb(150, 235, 243, 250)
            hillFrontPaint.color = Color.argb(215, 246, 250, 253)
            return
        }
        val bottom = cur[2]
        val night = scene == Scene.CLEAR_NIGHT || scene == Scene.PARTLY_NIGHT ||
            scene == Scene.OVERCAST_NIGHT || scene == Scene.STORM
        val f1 = if (night) 0.45f else 0.70f
        val f2 = if (night) 0.30f else 0.55f
        val aBack = if (scene == Scene.FOG) 50 else 95
        val aFront = if (scene == Scene.FOG) 80 else 140
        hillBackPaint.color = Color.argb(aBack, (Color.red(bottom) * f1).toInt(), (Color.green(bottom) * f1).toInt(), (Color.blue(bottom) * f1).toInt())
        hillFrontPaint.color = Color.argb(aFront, (Color.red(bottom) * f2).toInt(), (Color.green(bottom) * f2).toInt(), (Color.blue(bottom) * f2).toInt())
    }

    // ================================================================== draw

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastNanos = now
        val animate = ValueAnimator.areAnimatorsEnabled()
        step(dt, w, h, animate)

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        when (scene) {
            Scene.CLEAR_DAY, Scene.PARTLY_DAY -> drawSun(canvas, w, h, true)
            Scene.DUSK -> {
                drawStars(canvas, w, h, 0.4f, 0.45f)
                drawSun(canvas, w, h, false)
            }
            Scene.CLEAR_NIGHT -> {
                drawStars(canvas, w, h, 1f, 1f)
                drawMoon(canvas, 1f)
            }
            Scene.PARTLY_NIGHT -> {
                drawStars(canvas, w, h, 0.6f, 1f)
                drawMoon(canvas, 0.85f)
            }
            else -> Unit
        }

        if (birds.isNotEmpty()) drawBirds(canvas, w, h)
        drawClouds(canvas, w, h)
        if (scene == Scene.FOG) drawFog(canvas, w, h)

        canvas.drawPath(hillBack, hillBackPaint)
        canvas.drawPath(hillFront, hillFrontPaint)

        if (rainCount > 0) {
            drawRain(canvas, w, h)
            drawDrops(canvas)
        }
        if (snowy) drawSnow(canvas, w, h)
        if (scene == Scene.STORM) drawLightning(canvas, w, h)

        if (animate && isShown && windowVisibility == VISIBLE) postInvalidateOnAnimation()
    }

    private fun drawSun(canvas: Canvas, w: Float, h: Float, rays: Boolean) {
        glowPaint.alpha = al(255)
        canvas.drawCircle(sunX, sunY, dp(200f), glowPaint)

        if (rays) {
            // soft light shafts
            for (i in 0 until 3) {
                shaftPaint.alpha = al((38 + 22 * sin(time * 0.6f + i * 1.7f)).toInt())
                canvas.drawPath(shafts[i], shaftPaint)
            }
            // tapered rays
            rayPaint.strokeWidth = dp(3f)
            for (i in 0 until 14) {
                val a = time * 0.12f + i * (2f * pif / 14f)
                val len = if (i % 2 == 0) 2.05f else 1.7f
                val pulse = 0.6f + 0.4f * sin(time * 1.4f + i)
                rayPaint.color = Color.argb(al((120 * pulse).toInt()), 255, 245, 157)
                val cs = cos(a)
                val sn = sin(a)
                canvas.drawLine(sunX + cs * sunR * 1.35f, sunY + sn * sunR * 1.35f, sunX + cs * sunR * len, sunY + sn * sunR * len, rayPaint)
            }
        }

        val breathe = 1f + 0.03f * sin(time * 1.2f)
        canvas.save()
        canvas.scale(breathe, breathe, sunX, sunY)
        discPaint.alpha = al(255)
        canvas.drawCircle(sunX, sunY, sunR, discPaint)
        canvas.restore()

        if (rays) {
            // lens flare dots on the line through the screen centre
            val cx = w * 0.5f
            val cy = h * 0.5f
            val t = floatArrayOf(0.35f, 0.6f, 0.85f, 1.12f)
            val r = floatArrayOf(10f, 18f, 8f, 26f)
            val a = intArrayOf(22, 14, 26, 10)
            for (i in 0 until 4) {
                flarePaint.alpha = al(a[i])
                canvas.drawCircle(sunX + (cx - sunX) * t[i], sunY + (cy - sunY) * t[i], dp(r[i]), flarePaint)
            }
        }
    }

    private fun drawMoon(canvas: Canvas, strength: Float) {
        moonGlowPaint.alpha = al((255 * strength).toInt())
        canvas.drawCircle(moonX, moonY, moonR * 3.6f, moonGlowPaint)
        moonPaint.alpha = al((255 * strength).toInt())
        canvas.drawPath(moonPath, moonPaint)
    }

    private fun drawStars(canvas: Canvas, w: Float, h: Float, density: Float, yLimit: Float) {
        val n = (starCount * density).toInt()
        for (i in 0 until n) {
            if (starY[i] > yLimit * 0.5f) continue
            val tw = 0.5f + 0.5f * sin(time * starSp[i] + starPh[i])
            starPaint.alpha = al((70 + 185 * tw).toInt())
            val x = starX[i] * w
            val y = starY[i] * h
            val r = dp(starS[i])
            canvas.drawCircle(x, y, r, starPaint)
            if (i % 9 == 0) {
                starPaint.strokeWidth = dp(0.8f)
                canvas.drawLine(x - r * 3.2f, y, x + r * 3.2f, y, starPaint)
                canvas.drawLine(x, y - r * 3.2f, x, y + r * 3.2f, starPaint)
            }
        }
        if (ssLife > 0f) {
            val len = hypot(ssVx, ssVy)
            val ux = -ssVx / len
            val uy = -ssVy / len
            val life = sin((1f - ssLife / 0.8f) * pif)
            val tail = dp(120f)
            shootPaint.strokeWidth = dp(2f)
            for (i in 0 until 8) {
                val a0 = i / 8f
                val a1 = (i + 1) / 8f
                shootPaint.alpha = al((255 * (1f - a0) * life).toInt())
                canvas.drawLine(ssX + ux * tail * a0, ssY + uy * tail * a0, ssX + ux * tail * a1, ssY + uy * tail * a1, shootPaint)
            }
        }
    }

    private fun drawBirds(canvas: Canvas, w: Float, h: Float) {
        birdPaint.strokeWidth = dp(1.7f)
        birdPaint.alpha = al(150)
        for (b in birds) {
            val flap = sin(time * 7f + b.phase)
            val bob = sin(time * 1.1f + b.phase) * dp(6f)
            val s = b.size
            birdPath.reset()
            birdPath.moveTo(-s, -s * 0.45f * flap)
            birdPath.quadTo(-s * 0.5f, -s * 0.55f * flap - s * 0.15f, 0f, 0f)
            birdPath.quadTo(s * 0.5f, -s * 0.55f * flap - s * 0.15f, s, -s * 0.45f * flap)
            canvas.save()
            canvas.translate(b.x * w, b.y * h + bob)
            canvas.drawPath(birdPath, birdPaint)
            canvas.restore()
        }
    }

    private fun drawClouds(canvas: Canvas, w: Float, h: Float) {
        cloudPaint.colorFilter = cloudFilter
        for (c in clouds) {
            val bmp = cloudBitmaps[c.shape] ?: continue
            val cw = cloudW * c.scale
            val ch = cloudH * c.scale
            val cx = c.x * w
            val cy = c.y * h
            dst.set(cx - cw * 0.5f, cy - ch * 0.5f, cx + cw * 0.5f, cy + ch * 0.5f)
            cloudPaint.alpha = al(c.alpha)
            canvas.drawBitmap(bmp, null, dst, cloudPaint)
        }
        cloudPaint.colorFilter = null
    }

    private fun drawFog(canvas: Canvas, w: Float, h: Float) {
        val bmp = cloudBitmaps[0] ?: return
        veilPaint.color = Color.argb(al(34), 220, 230, 235)
        canvas.drawRect(0f, 0f, w, h, veilPaint)
        cloudPaint.colorFilter = cloudFilter
        for (i in 0 until 5) {
            val speed = 0.012f + i * 0.007f
            val travel = ((time * speed + i * 0.37f) % 1.8f) - 0.4f
            val bw = w * 1.7f
            val bh = h * 0.17f
            val y = h * (0.30f + 0.14f * i)
            val left = travel * w - bw * 0.1f
            dst.set(left, y - bh * 0.5f, left + bw, y + bh * 0.5f)
            cloudPaint.alpha = al(70 + 14 * (i % 3))
            canvas.drawBitmap(bmp, null, dst, cloudPaint)
        }
        cloudPaint.colorFilter = null
    }

    private fun drawRain(canvas: Canvas, w: Float, h: Float) {
        val slant = slant()
        val len = floatArrayOf(dp(10f), dp(16f), dp(25f))
        val widths = floatArrayOf(dp(0.8f), dp(1.2f), dp(1.8f))
        val alphas = intArrayOf(70, 115, 175)
        for (layer in 0 until 3) {
            val list = rainLayers[layer]
            var k = 0
            for (s in list) {
                val x0 = s.x * w
                val y0 = s.y * h
                rainLines[k++] = x0
                rainLines[k++] = y0
                rainLines[k++] = x0 - slant * len[layer]
                rainLines[k++] = y0 + len[layer]
            }
            val p = rainPaints[layer]
            p.strokeWidth = widths[layer]
            p.color = Color.argb(al(alphas[layer]), 214, 232, 255)
            canvas.drawLines(rainLines, 0, k, p)
        }
    }

    private fun drawDrops(canvas: Canvas) {
        dropRim.strokeWidth = dp(0.8f)
        for (d in drops) {
            if (d.trail > 0.02f && d.y > d.startY) {
                dropTrail.strokeWidth = d.r * 0.7f
                dropTrail.alpha = al((40 * d.trail).toInt())
                canvas.drawLine(d.x, d.startY, d.x, d.y, dropTrail)
            }
            dropFill.alpha = al(46)
            canvas.drawCircle(d.x, d.y, d.r, dropFill)
            dropRim.alpha = al(95)
            canvas.drawCircle(d.x, d.y, d.r, dropRim)
            dropFill.alpha = al(190)
            canvas.drawCircle(d.x - d.r * 0.3f, d.y - d.r * 0.35f, d.r * 0.28f, dropFill)
        }
    }

    private fun drawSnow(canvas: Canvas, w: Float, h: Float) {
        val alphas = intArrayOf(120, 175, 235)
        for (layer in 0 until 3) {
            snowPaint.alpha = al(alphas[layer])
            for (f in snowLayers[layer]) {
                val x = (f.x + sin(time * 0.9f + f.phase) * f.sway) * w
                canvas.drawCircle(x, f.y * h, f.size, snowPaint)
            }
        }
    }

    private fun drawLightning(canvas: Canvas, w: Float, h: Float) {
        if (flash <= 0.01f) return
        if (boltVisible) {
            boltGlow.strokeWidth = dp(10f)
            boltGlow.color = Color.argb((90 * flash).toInt(), 175, 200, 255)
            canvas.drawPath(boltPath, boltGlow)
            canvas.drawPath(branchPath, boltGlow)
            boltGlow.strokeWidth = dp(4f)
            boltGlow.color = Color.argb((170 * flash).toInt(), 210, 225, 255)
            canvas.drawPath(boltPath, boltGlow)
            canvas.drawPath(branchPath, boltGlow)
            boltGlow.strokeWidth = dp(1.6f)
            boltGlow.color = Color.argb((255 * flash).toInt(), 255, 255, 255)
            canvas.drawPath(boltPath, boltGlow)
            boltGlow.strokeWidth = dp(1f)
            canvas.drawPath(branchPath, boltGlow)
        }
        flashPaint.color = Color.argb((if (boltVisible) 62 else 42).times(flash).toInt(), 200, 215, 255)
        canvas.drawRect(0f, 0f, w, h, flashPaint)
    }

    // ============================================================== lifecycle

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            lastNanos = 0L
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        cloudBitmaps.forEachIndexed { i, b -> b?.recycle(); cloudBitmaps[i] = null }
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (cloudBitmaps[0] == null && width > 0) {
            buildCloudBitmaps(width)
        }
        lastNanos = 0L
        invalidate()
    }
}
