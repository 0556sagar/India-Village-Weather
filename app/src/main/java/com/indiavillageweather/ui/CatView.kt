package com.example.indiavillageweather.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A marigold tabby that sits on the edge of the details panel and reacts to the weather.
 *
 *  rain  -> holds an umbrella            storm -> puffed up, trembling, wide eyes
 *  cold  -> beanie + scarf, breath puffs hot   -> shades, sweat, tongue out
 *  night -> asleep with floating Zzz     sun   -> shades
 *
 * Tap the cat for a short Hinglish tip. Idle life: breathing, blinking, ear twitches,
 * swaying tail and wandering eyes. Drawn in a 300 x 200 logical space, anchored bottom-right.
 */
class CatView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ------------------------------------------------------------------ look
    private enum class Eyes { OPEN, SCARED, SLEEP, HAPPY }

    private var eyes = Eyes.OPEN
    private var shades = false
    private var hat = false
    private var scarf = false
    private var umbrella = false
    private var sweat = false
    private var tongue = false
    private var breath = false
    private var scared = false
    private var asleep = false
    private var mood = "cloudy"

    private val tips: Map<String, List<String>> = mapOf(
        "storm" to listOf(
            "Bijli kadak rahi hai! Ghar ke andar raho \uD83D\uDE40",
            "Khule mein mat jao, toofan hai!",
            "Meow! Ped ke neeche mat khade hona."
        ),
        "rain" to listOf(
            "Chhatri saath rakho, barish aa rahi hai \u2614",
            "Phisalne se bacho, zameen geeli hai!",
            "Chai aur pakode ka mausam hai \uD83C\uDF75"
        ),
        "snow" to listOf(
            "Brrr! Garam kapde pehno \u2744\uFE0F",
            "Baraf gir rahi hai, dheere chalna."
        ),
        "cold" to listOf(
            "Thand hai! Sweater pehen lo \uD83E\uDDE3",
            "Subah ki dhoop sekne ka mausam hai.",
            "Garam paani piyo, thand lag jayegi."
        ),
        "hot" to listOf(
            "Bahut garmi hai! Paani peete raho \uD83D\uDCA7",
            "Dopahar mein chhaanv mein raho.",
            "Sir dhak ke nikalna, loo lag sakti hai!"
        ),
        "night" to listOf(
            "Zzz\u2026 shubh ratri \uD83C\uDF19",
            "Shhh, billi so rahi hai.",
            "Subah milte hain! Zzz\u2026"
        ),
        "sunny" to listOf(
            "Dhoop khili hai, mausam mast hai \uD83D\uDE0E",
            "Aaj kapde sukhane ka perfect din!",
            "Khet ya sair, aaj sab theek hai."
        ),
        "fog" to listOf(
            "Kohra hai, gaadi dheere chalana \uD83C\uDF2B\uFE0F",
            "Kuch dikh nahi raha... meow?"
        ),
        "cloudy" to listOf(
            "Badal chhaye hain, mausam suhana \u2601\uFE0F",
            "Dhoop-chhaanv ka khel chal raha hai."
        )
    )
    private var tipIndex = 0

    // ------------------------------------------------------------ animation
    private var time = 0f
    private var lastNanos = 0L

    private var blinkNext = 2.5f
    private var blinkT = -1f
    private var twitchNext = 5f
    private var twitchT = -1f
    private var twitchLeft = true
    private var lookNext = 2f
    private var lookX = 0f
    private var lookY = 0f
    private var lookTx = 0f
    private var lookTy = 0f
    private var hopT = -1f
    private var happyT = 0f

    private var bubbleText = ""
    private var bubbleLayout: StaticLayout? = null
    private var bubbleT = -1f
    private var bubbleHold = 3.4f

    // --------------------------------------------------------------- colors
    private val fur = 0xFFF2A03F.toInt()
    private val furDark = 0xFFD9791F.toInt()
    private val furLight = 0xFFFFB866.toInt()
    private val cream = 0xFFFFEBCB.toInt()
    private val pink = 0xFFF6A5B7.toInt()
    private val ink = 0xFF2B1B12.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E2A33.toInt()
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12.5f, resources.displayMetrics)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val zPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD_ITALIC)
    }

    private val bodyShader = RadialGradient(
        212f, 150f, 95f,
        intArrayOf(furLight, fur, 0xFFE0872A.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
    )
    private val headShader = RadialGradient(
        212f, 100f, 70f,
        intArrayOf(furLight, fur, 0xFFE58F32.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
    )

    private val tailRings = floatArrayOf(0.3f, 0.52f, 0.72f)
    private val eyeXs = floatArrayOf(207f, 243f)
    private val zSizes = floatArrayOf(15f, 19f, 24f)
    private val zXs = floatArrayOf(262f, 274f, 285f)
    private val zYs = floatArrayOf(86f, 66f, 44f)

    private val path = Path()
    private val seg = Path()
    private val rect = RectF()
    private val measure = PathMeasure()
    private val pif = PI.toFloat()

    init {
        isClickable = true
        contentDescription = "Weather cat. Tap for a tip."
        setOnClickListener { onTapped() }
    }

    // ================================================================== API

    /** Dress the cat for the current weather. */
    fun setWeather(code: Int, isDay: Boolean, tempC: Double) {
        val storm = code in 95..99
        val snow = code in 71..77 || code in 85..86
        val rain = code in 51..67 || code in 80..82
        val fog = code in 45..48
        val cold = tempC <= 15.0
        val hot = tempC >= 37.0

        val newMood = when {
            storm -> "storm"
            snow -> "snow"
            rain -> "rain"
            cold -> "cold"
            hot -> "hot"
            !isDay -> "night"
            fog -> "fog"
            code <= 1 -> "sunny"
            else -> "cloudy"
        }
        asleep = !isDay && !storm && !rain
        scared = storm
        umbrella = rain || storm
        hat = cold || snow
        scarf = cold || snow
        shades = isDay && !storm && !rain && !fog && !snow && (code <= 2 || hot) && !cold
        sweat = hot || storm
        tongue = hot && !storm
        breath = (cold || snow) && !asleep
        eyes = when {
            scared -> Eyes.SCARED
            asleep -> Eyes.SLEEP
            (cold || snow) && isDay && code <= 1 -> Eyes.HAPPY
            else -> Eyes.OPEN
        }
        if (newMood != mood) {
            mood = newMood
            tipIndex = 0
            hopT = 0f
        }
        invalidate()
    }

    /** Show this weather's tip once (call after the data loads). */
    fun greet() = say(nextTip())

    /** Show any message in the speech bubble. */
    fun say(text: String, durationMs: Long = 3400L) {
        bubbleText = text
        bubbleHold = durationMs / 1000f
        bubbleLayout = null // rebuilt on next draw
        bubbleT = 0f
        invalidate()
    }

    private fun nextTip(): String {
        val list = tips.getValue(mood)
        val t = list[tipIndex % list.size]
        tipIndex++
        return t
    }

    private fun onTapped() {
        hopT = 0f
        happyT = 1.4f
        say(nextTip())
    }

    // ================================================================ update

    private fun update(dt: Float) {
        time += dt

        blinkNext -= dt
        if (blinkT < 0f && blinkNext <= 0f) {
            blinkT = 0f
            blinkNext = 2.2f + Random.nextFloat() * 3.4f
        }
        if (blinkT >= 0f) {
            blinkT += dt / 0.17f
            if (blinkT >= 1f) blinkT = -1f
        }

        twitchNext -= dt
        if (twitchT < 0f && twitchNext <= 0f) {
            twitchT = 0f
            twitchLeft = Random.nextBoolean()
            twitchNext = 4f + Random.nextFloat() * 5f
        }
        if (twitchT >= 0f) {
            twitchT += dt / 0.3f
            if (twitchT >= 1f) twitchT = -1f
        }

        lookNext -= dt
        if (lookNext <= 0f) {
            lookTx = (Random.nextFloat() - 0.5f) * 5f
            lookTy = (Random.nextFloat() - 0.5f) * 3f
            if (umbrella) lookTy = -2.4f
            lookNext = 1.6f + Random.nextFloat() * 2.6f
        }
        val k = min(1f, dt * 6f)
        lookX += (lookTx - lookX) * k
        lookY += (lookTy - lookY) * k

        if (hopT >= 0f) {
            hopT += dt / 0.55f
            if (hopT >= 1f) hopT = -1f
        }
        if (happyT > 0f) happyT = max(0f, happyT - dt)

        if (bubbleT >= 0f) {
            bubbleT += dt
            if (bubbleT > bubbleHold + 0.6f) bubbleT = -1f
        }
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
        if (animate) update(dt)

        val s = min(w / 300f, h / 200f)
        val ox = w - 300f * s
        val oy = h - 200f * s

        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(s, s)

        // hop + tremble applied to the whole figure
        val hop = if (hopT >= 0f) -sin(hopT * pif) * 28f * (1f - 0.3f * hopT) else 0f
        val tremble = if (scared && animate) sin(time * 58f) * 1.3f else 0f
        canvas.translate(-4f + tremble, hop)

        drawCat(canvas)
        canvas.restore()

        if (bubbleT >= 0f) drawBubble(canvas, s, ox, oy)

        val needsFrames = animate && isShown && windowVisibility == VISIBLE
        if (needsFrames) postInvalidateOnAnimation()
    }

    private fun drawCat(c: Canvas) {
        val sleeping = eyes == Eyes.SLEEP
        val breathAmp = if (sleeping) 0.022f else 0.014f
        val br = sin(time * (if (sleeping) 1.2f else 2.0f)) * breathAmp
        val tailSpeed = if (scared) 9f else if (sleeping) 0.8f else 1.6f
        val tailAmp = if (scared) 4f else if (sleeping) 4f else 8f
        val sway = sin(time * tailSpeed) * tailAmp

        // ---- tail
        path.reset()
        path.moveTo(262f, 192f)
        path.cubicTo(288f + sway, 198f, 296f + sway * 1.2f, 152f, 280f + sway * 1.6f, 130f)
        stroke.color = fur
        stroke.strokeWidth = if (scared) 22f else 15f
        c.drawPath(path, stroke)
        measure.setPath(path, false)
        val len = measure.length
        stroke.color = furDark
        stroke.strokeWidth = (if (scared) 22f else 15f) * 0.45f
        for (f in tailRings) {
            seg.reset()
            measure.getSegment(len * f, len * (f + 0.045f), seg, true)
            stroke.strokeWidth = if (scared) 22.5f else 15.5f
            c.drawPath(seg, stroke)
        }
        seg.reset()
        measure.getSegment(len * 0.88f, len, seg, true)
        stroke.color = cream
        stroke.strokeWidth = if (scared) 22f else 15f
        c.drawPath(seg, stroke)

        // ---- body (breathing scales around the floor)
        c.save()
        c.scale(1f, 1f + br, 225f, 200f)

        // haunches
        fill.shader = null
        fill.color = 0xFFE8912F.toInt()
        c.drawOval(rect.apply { set(172f, 169f, 212f, 203f) }, fill)
        c.drawOval(rect.apply { set(238f, 169f, 278f, 203f) }, fill)

        fill.shader = bodyShader
        c.drawOval(rect.apply { set(181f, 130f, 269f, 206f) }, fill)
        fill.shader = null

        // chest and flank stripes
        fill.color = cream
        c.drawOval(rect.apply { set(203f, 148f, 247f, 204f) }, fill)
        stroke.color = furDark
        stroke.strokeWidth = 5f
        for (i in 0 until 3) {
            val y = 150f + i * 11f
            path.reset()
            path.moveTo(184f + i * 1.5f, y)
            path.quadTo(194f, y + 2f, 200f, y + 10f)
            c.drawPath(path, stroke)
            path.reset()
            path.moveTo(266f - i * 1.5f, y)
            path.quadTo(256f, y + 2f, 250f, y + 10f)
            c.drawPath(path, stroke)
        }

        // front legs and paws
        stroke.color = fur
        stroke.strokeWidth = 17f
        c.drawLine(213f, 168f, 213f, 190f, stroke)
        if (!umbrella) c.drawLine(237f, 168f, 237f, 190f, stroke)
        fill.color = cream
        c.drawOval(rect.apply { set(201f, 186f, 225f, 202f) }, fill)
        if (!umbrella) c.drawOval(rect.apply { set(225f, 186f, 249f, 202f) }, fill)
        stroke.color = 0xFFD9B98F.toInt()
        stroke.strokeWidth = 1.6f
        c.drawLine(210f, 193f, 210f, 200f, stroke)
        c.drawLine(216f, 193f, 216f, 200f, stroke)
        if (!umbrella) {
            c.drawLine(231f, 193f, 231f, 200f, stroke)
            c.drawLine(237f, 193f, 237f, 200f, stroke)
        }
        c.restore()

        // ---- umbrella handle (behind head, in front of chest) + holding paw
        if (umbrella) {
            val tilt = -7f + sin(time * 1.3f) * 2f + (if (scared) sin(time * 40f) * 2f else 0f)
            c.save()
            c.rotate(tilt, 232f, 84f)
            stroke.color = 0xFF5D4037.toInt()
            stroke.strokeWidth = 3.6f
            c.drawLine(232f, 84f, 232f, 176f, stroke)
            path.reset()
            path.moveTo(232f, 176f)
            path.quadTo(232f, 186f, 224f, 184f)
            c.drawPath(path, stroke)
            c.restore()
        }

        // ---- scarf band behind the chin
        if (scarf) drawScarf(c)

        // ---- head
        val headDy = if (sleeping) 3f + sin(time * 1.2f) * 1.2f else sin(time * 2.0f) * 0.8f
        val headRot = if (sleeping) -6f else if (scared) sin(time * 40f) * 1.5f else sin(time * 0.7f) * 1.2f
        c.save()
        c.translate(0f, headDy)
        c.rotate(headRot, 225f, 150f)
        drawHead(c)
        c.restore()

        // ---- holding paw (over handle)
        if (umbrella) {
            val tilt = -7f + sin(time * 1.3f) * 2f
            c.save()
            c.rotate(tilt, 232f, 84f)
            fill.color = cream
            c.drawOval(rect.apply { set(222f, 166f, 246f, 184f) }, fill)
            stroke.color = 0xFFD9B98F.toInt()
            stroke.strokeWidth = 1.5f
            c.drawLine(229f, 170f, 229f, 179f, stroke)
            c.drawLine(235f, 170f, 235f, 179f, stroke)
            c.restore()
            drawUmbrellaCanopy(c, tilt)
        }

        // ---- ambient extras
        if (sleeping) drawZzz(c)
        if (sweat) drawSweat(c)
        if (breath) drawBreath(c)
    }

    private fun drawHead(c: Canvas) {
        // ears (flatten outward when scared; twitch occasionally)
        val twitch = if (twitchT >= 0f) sin(twitchT * pif * 2f) * 11f else 0f
        val flat = if (scared) 20f else 0f
        drawEar(c, true, -(if (twitchLeft) twitch else 0f) - flat)
        drawEar(c, false, (if (!twitchLeft) twitch else 0f) + flat)

        // puffed fur spikes when scared
        if (scared) {
            fill.shader = null
            fill.color = fur
            for (i in 0 until 14) {
                val a = pif * (0.95f + i / 13f * 1.1f)
                val cx = 225f + cos(a) * 41f
                val cy = 116f + sin(a) * 36f
                path.reset()
                path.moveTo(cx + cos(a + 1.57f) * 5f, cy + sin(a + 1.57f) * 5f)
                path.lineTo(cx + cos(a) * 9f, cy + sin(a) * 9f)
                path.lineTo(cx + cos(a - 1.57f) * 5f, cy + sin(a - 1.57f) * 5f)
                path.close()
                c.drawPath(path, fill)
            }
        }

        // head and cheek fluff
        fill.shader = headShader
        c.drawOval(rect.apply { set(187f, 83f, 263f, 150f) }, fill)
        fill.shader = null
        fill.color = fur
        c.drawOval(rect.apply { set(180f, 114f, 202f, 134f) }, fill)
        c.drawOval(rect.apply { set(248f, 114f, 270f, 134f) }, fill)

        // forehead stripes
        stroke.color = furDark
        stroke.strokeWidth = 3.6f
        c.drawLine(225f, 86f, 225f, 98f, stroke)
        c.drawLine(215f, 88f, 217f, 97f, stroke)
        c.drawLine(235f, 88f, 233f, 97f, stroke)

        // muzzle
        fill.color = cream
        c.drawOval(rect.apply { set(204f, 119f, 227f, 138f) }, fill)
        c.drawOval(rect.apply { set(223f, 119f, 246f, 138f) }, fill)
        c.drawOval(rect.apply { set(217f, 132f, 233f, 144f) }, fill)

        // blush (happy or just petted)
        if (eyes == Eyes.HAPPY || happyT > 0f) {
            fill.color = 0x66F28CA1
            c.drawOval(rect.apply { set(190f, 122f, 206f, 132f) }, fill)
            c.drawOval(rect.apply { set(244f, 122f, 260f, 132f) }, fill)
        }

        drawEyes(c)

        // nose and mouth
        fill.color = 0xFFE57A94.toInt()
        stroke.color = 0xFFE57A94.toInt()
        stroke.strokeWidth = 2f
        path.reset()
        path.moveTo(220f, 122f)
        path.lineTo(230f, 122f)
        path.lineTo(225f, 128.5f)
        path.close()
        c.drawPath(path, fill)
        c.drawPath(path, stroke)

        if (tongue) {
            fill.color = pink
            c.drawOval(rect.apply { set(220f, 134f, 230f, 148f) }, fill)
            stroke.color = 0xFFD9667F.toInt()
            stroke.strokeWidth = 1.2f
            c.drawLine(225f, 135f, 225f, 144f, stroke)
        }
        stroke.color = 0xFF8A4A17.toInt()
        stroke.strokeWidth = 2.1f
        c.drawLine(225f, 128.5f, 225f, 132f, stroke)
        path.reset()
        path.moveTo(225f, 132f)
        path.quadTo(220f, 138f, 214f, 134f)
        path.moveTo(225f, 132f)
        path.quadTo(230f, 138f, 236f, 134f)
        c.drawPath(path, stroke)

        // whiskers (tiny idle twitch)
        stroke.color = 0xCCFFFFFF.toInt()
        stroke.strokeWidth = 1.5f
        val wob = sin(time * 3f) * 1.2f
        for (i in 0 until 3) {
            val y = 128f + i * 4.5f
            c.drawLine(204f, y, 172f, y - 4f + i * 5f + wob, stroke)
            c.drawLine(246f, y, 278f, y - 4f + i * 5f - wob, stroke)
        }

        if (shades) drawShades(c)
        if (hat) drawHat(c)
    }

    private fun drawEar(c: Canvas, left: Boolean, rotation: Float) {
        c.save()
        val px = if (left) 203f else 247f
        c.rotate(rotation, px, 96f)
        fun x(v: Float) = if (left) v else 450f - v
        path.reset()
        path.moveTo(x(190f), 104f)
        path.lineTo(x(194f), 64f)
        path.lineTo(x(220f), 88f)
        path.close()
        fill.shader = null
        fill.color = fur
        stroke.color = fur
        stroke.strokeWidth = 7f
        c.drawPath(path, stroke)
        c.drawPath(path, fill)
        path.reset()
        path.moveTo(x(197f), 97f)
        path.lineTo(x(198f), 76f)
        path.lineTo(x(212f), 89f)
        path.close()
        fill.color = pink
        stroke.color = pink
        stroke.strokeWidth = 3f
        c.drawPath(path, stroke)
        c.drawPath(path, fill)
        c.restore()
    }

    private fun drawEyes(c: Canvas) {
        val mode = if (happyT > 0f && eyes == Eyes.OPEN) Eyes.HAPPY else eyes
        val y = 114f
        for (ex in eyeXs) {
            when (mode) {
                Eyes.SLEEP -> {
                    stroke.color = ink
                    stroke.strokeWidth = 2.8f
                    path.reset()
                    path.moveTo(ex - 8f, y)
                    path.quadTo(ex, y + 7f, ex + 8f, y)
                    c.drawPath(path, stroke)
                }
                Eyes.HAPPY -> {
                    stroke.color = ink
                    stroke.strokeWidth = 3f
                    path.reset()
                    path.moveTo(ex - 8f, y + 3f)
                    path.quadTo(ex, y - 8f, ex + 8f, y + 3f)
                    c.drawPath(path, stroke)
                }
                else -> drawOpenEye(c, ex, y, mode == Eyes.SCARED)
            }
        }
    }

    private fun drawOpenEye(c: Canvas, ex: Float, ey: Float, wide: Boolean) {
        val blink = if (blinkT >= 0f) sin(blinkT * pif) else 0f
        val open = 1f - 0.94f * blink
        c.save()
        c.scale(1f, open, ex, ey)
        if (wide) {
            fill.color = Color.WHITE
            c.drawOval(rect.apply { set(ex - 10.5f, ey - 12.5f, ex + 10.5f, ey + 12.5f) }, fill)
        }
        val rx = if (wide) 6f else 8.6f
        val ry = if (wide) 8f else 10.2f
        val lx = if (wide) lookX * 0.4f else lookX
        val ly = if (wide) lookY * 0.4f else lookY
        fill.color = 0xFF8BC34A.toInt()
        c.drawOval(rect.apply { set(ex - rx + lx, ey - ry + ly, ex + rx + lx, ey + ry + ly) }, fill)
        fill.color = 0xFF1B1B1B.toInt()
        val pw = if (wide) 1.7f else 3.4f
        val ph = if (wide) 3.2f else 8f
        c.drawOval(rect.apply { set(ex - pw + lx, ey - ph + ly, ex + pw + lx, ey + ph + ly) }, fill)
        fill.color = Color.WHITE
        c.drawCircle(ex - 3f + lx, ey - 4f + ly, 2.7f, fill)
        c.drawCircle(ex + 3f + lx, ey + 3.5f + ly, 1.3f, fill)
        c.restore()
    }

    private fun drawShades(c: Canvas) {
        fill.color = 0xFF1E1E24.toInt()
        c.drawRoundRect(rect.apply { set(191f, 103f, 220f, 125f) }, 8f, 8f, fill)
        c.drawRoundRect(rect.apply { set(230f, 103f, 259f, 125f) }, 8f, 8f, fill)
        stroke.color = 0xFF1E1E24.toInt()
        stroke.strokeWidth = 3f
        c.drawLine(219f, 109f, 231f, 109f, stroke)
        c.drawLine(191f, 108f, 184f, 105f, stroke)
        c.drawLine(259f, 108f, 266f, 105f, stroke)
        stroke.color = 0x55FFFFFF
        stroke.strokeWidth = 2.2f
        c.drawLine(196f, 107f, 202f, 101f + 6f, stroke)
        c.drawLine(235f, 107f, 241f, 101f + 6f, stroke)
    }

    private fun drawHat(c: Canvas) {
        val red = 0xFFD94A3D.toInt()
        path.reset()
        path.moveTo(204f, 91f)
        path.quadTo(204f, 65f, 225f, 63f)
        path.quadTo(246f, 65f, 246f, 91f)
        path.close()
        fill.shader = null
        fill.color = red
        c.drawPath(path, fill)
        fill.color = 0xFFB33A30.toInt()
        c.drawRoundRect(rect.apply { set(201f, 87f, 249f, 99f) }, 5f, 5f, fill)
        stroke.color = 0x44000000
        stroke.strokeWidth = 1.2f
        var x = 206f
        while (x < 247f) {
            c.drawLine(x, 89f, x, 97f, stroke)
            x += 5f
        }
        fill.color = cream
        c.drawCircle(225f, 61f, 8.5f, fill)
        fill.color = 0xFFF1D9AE.toInt()
        c.drawCircle(223f, 59f, 3f, fill)
    }

    private fun drawScarf(c: Canvas) {
        val teal = 0xFF26A69A.toInt()
        val gold = 0xFFFFB300.toInt()
        fill.shader = null
        fill.color = teal
        c.drawRoundRect(rect.apply { set(192f, 140f, 258f, 158f) }, 9f, 9f, fill)
        fill.color = gold
        var x = 199f
        while (x < 256f) {
            c.drawRect(x, 141f, x + 5f, 157f, fill)
            x += 14f
        }
        // dangling end with a gentle flutter
        val flutter = sin(time * 2.4f) * 3f
        path.reset()
        path.moveTo(244f, 154f)
        path.quadTo(250f + flutter, 168f, 247f + flutter * 1.6f, 188f)
        path.lineTo(258f + flutter * 1.6f, 188f)
        path.quadTo(262f + flutter, 168f, 256f, 154f)
        path.close()
        fill.color = teal
        c.drawPath(path, fill)
        stroke.color = gold
        stroke.strokeWidth = 4f
        stroke.strokeCap = Paint.Cap.BUTT
        c.drawLine(248f + flutter * 1.2f, 172f, 259f + flutter * 1.2f, 172f, stroke)
        c.drawLine(247f + flutter * 1.5f, 182f, 258f + flutter * 1.5f, 182f, stroke)
        stroke.strokeCap = Paint.Cap.ROUND
    }

    private fun drawUmbrellaCanopy(c: Canvas, tilt: Float) {
        val cx = 232f
        val cy = 84f
        val r = 62f
        c.save()
        c.rotate(tilt, cx, cy)

        path.reset()
        path.moveTo(cx - r, cy)
        path.arcTo(rect.apply { set(cx - r, cy - r, cx + r, cy + r) }, 180f, 180f)
        val sw = 2f * r / 4f
        for (k in 0 until 4) {
            val right = cx + r - k * sw
            path.arcTo(rect.apply { set(right - sw, cy - 9f, right, cy + 9f) }, 0f, 180f)
        }
        path.close()

        fill.shader = null
        fill.color = 0xFFE8503A.toInt()
        c.drawPath(path, fill)
        c.save()
        c.clipPath(path)
        fill.color = 0xFFFFB300.toInt()
        for (i in 1 until 6 step 2) {
            c.drawArc(rect.apply { set(cx - r, cy - r, cx + r, cy + r) }, 180f + i * 30f, 30f, true, fill)
        }
        c.restore()
        stroke.color = 0x33000000
        stroke.strokeWidth = 1.5f
        c.drawPath(path, stroke)
        // tip
        stroke.color = 0xFF5D4037.toInt()
        stroke.strokeWidth = 3.4f
        c.drawLine(cx, cy - r, cx, cy - r - 7f, stroke)

        // raindrops sliding off the rim
        fill.color = 0xCCBEE3FF.toInt()
        for (i in 0 until 5) {
            val ph = ((time * 0.9f + i * 0.21f) % 1f)
            val dx = cx - r + 8f + i * (2f * r - 16f) / 4f
            val dy = cy + 6f + ph * 62f
            fill.alpha = ((1f - ph) * 200f).toInt().coerceIn(0, 255)
            c.drawOval(rect.apply { set(dx - 1.8f, dy - 3.2f, dx + 1.8f, dy + 3.2f) }, fill)
        }
        c.restore()
    }

    private fun drawZzz(c: Canvas) {
        val sizes = zSizes
        val xs = zXs
        val ys = zYs
        for (i in 0 until 3) {
            val ph = (time * 0.45f + i * 0.30f) % 1f
            zPaint.textSize = sizes[i]
            zPaint.alpha = (sin(ph * pif) * 255f).toInt().coerceIn(0, 255)
            c.drawText(if (i == 2) "Z" else "z", xs[i] + ph * 6f, ys[i] - ph * 20f, zPaint)
        }
    }

    private fun drawSweat(c: Canvas) {
        val ph = (time * (if (scared) 0.9f else 0.55f)) % 1f
        val x = 266f
        val y = 98f + ph * 34f
        fill.shader = null
        fill.color = 0xFF7FD6FF.toInt()
        fill.alpha = ((1f - ph * ph) * 255f).toInt().coerceIn(0, 255)
        path.reset()
        path.moveTo(x, y - 7f)
        path.quadTo(x + 5.5f, y + 1f, x, y + 4f)
        path.quadTo(x - 5.5f, y + 1f, x, y - 7f)
        c.drawPath(path, fill)
        fill.alpha = 255
    }

    private fun drawBreath(c: Canvas) {
        for (i in 0 until 3) {
            val ph = (time * 0.55f + i * 0.33f) % 1f
            fill.shader = null
            fill.color = Color.WHITE
            fill.alpha = ((1f - ph) * 150f).toInt().coerceIn(0, 255)
            c.drawCircle(214f - ph * 24f, 138f - ph * 18f, 3f + ph * 6f, fill)
        }
        fill.alpha = 255
    }

    // ============================================================ speech bubble

    private fun drawBubble(canvas: Canvas, s: Float, ox: Float, oy: Float) {
        val d = resources.displayMetrics.density
        val padH = 11f * d
        val padV = 8f * d
        val maxText = min(width * 0.52f, 150f * d) - 2f * padH

        var layout = bubbleLayout
        if (layout == null) {
            val desired = ceil(Layout.getDesiredWidth(bubbleText, textPaint)).toInt()
            val wText = min(desired, maxText.toInt()).coerceAtLeast(40)
            layout = StaticLayout.Builder.obtain(bubbleText, 0, bubbleText.length, textPaint, wText)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.05f)
                .build()
            bubbleLayout = layout
        }
        val lay = layout ?: return
        val bw = lay.width + 2f * padH
        val bh = lay.height + 2f * padV

        // pop in with a little overshoot, fade out at the end
        val inT = min(1f, bubbleT / 0.28f)
        val overshoot = 1f + 0.12f * sin(inT * pif) * (1f - inT)
        val scale = (0.82f + 0.18f * inT) * overshoot
        val outT = max(0f, bubbleT - bubbleHold) / 0.6f
        val alpha = ((1f - outT) * min(1f, inT * 1.4f) * 245f).toInt().coerceIn(0, 255)

        val right = ox + 176f * s
        val bottom = oy + 112f * s
        val left = right - bw
        val top = bottom - bh

        canvas.save()
        canvas.scale(scale, scale, right, bottom)

        fill.shader = null
        fill.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawRoundRect(rect.apply { set(left, top, right, bottom) }, 14f * d, 14f * d, fill)
        // tail toward the cat's head
        path.reset()
        path.moveTo(right - 1f, bottom - 16f * d)
        path.lineTo(right + 11f * d, bottom - 6f * d)
        path.lineTo(right - 1f, bottom - 3f * d)
        path.close()
        canvas.drawPath(path, fill)
        stroke.color = Color.argb((alpha * 0.12f).toInt(), 0, 0, 0)
        stroke.strokeWidth = 1f * d
        canvas.drawRoundRect(rect.apply { set(left, top, right, bottom) }, 14f * d, 14f * d, stroke)

        textPaint.alpha = alpha
        canvas.translate(left + padH, top + padV)
        lay.draw(canvas)
        textPaint.alpha = 255
        canvas.restore()
    }

    // ============================================================== lifecycle

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            lastNanos = 0L
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastNanos = 0L
        invalidate()
    }
}
