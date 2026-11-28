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
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * "Billu", the weather cat (v2).
 *
 * LOOKS    shaded fur with strokes and tabby stripes, almond cat-eyes with slit pupils that
 *          dilate with light, whisker pads, curved whiskers that sway with the wind.
 * WEATHER  umbrella, beanie and scarf, shades, sweat, panting, shivering, sleeping, scared.
 * LIFE     breathing, blinking, ear twitches, yawns, paw-washing, tail flicks, sneezes,
 *          a butterfly that the eyes follow, eyes that follow your finger.
 * TOUCH    drag over the head = pet (purr, hearts, haptics)    tap nose = sneeze
 *          tap ear = ear flick     tap tail = tail flick        tap body = hop + tip
 *          long-press = fish treat (3 max)    poke too much = grumpy cat
 * TALK     weather tips, live data lines (temp, rain, UV, AQI...), greetings by time of day,
 *          reactions and idle chatter, all in Hinglish.
 *
 * Drawn in a 300 x 200 logical space anchored bottom-right. A 260 x 173 dp view looks best.
 */
class CatView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private enum class Eyes { OPEN, SCARED, SLEEP, HAPPY }
    private enum class Beh { NONE, YAWN, WASH, SNEEZE, EAT }

    private companion object {
        const val R_NONE = 0
        const val R_HEAD = 1
        const val R_NOSE = 2
        const val R_EAR = 3
        const val R_BODY = 4
        const val R_TAIL = 5
    }

    private val pif = PI.toFloat()

    // ======================================================== weather look
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
    private var shiver = false
    private var isDayNow = true
    private var mood = "cloudy"
    private var dilation = 0.5f
    private var windKmh = 8f
    private var ctxLines: List<String> = emptyList()
    private var greetCount = 0

    // ============================================================== words
    private val tips: Map<String, List<String>> = mapOf(
        "storm" to listOf(
            "Bijli kadak rahi hai! Ghar ke andar raho 🙀",
            "Khule mein mat jao, toofan hai!",
            "Ped ke neeche mat khade hona, meow!",
            "Mobile charge pe mat lagao, bijli ka khatra hai."
        ),
        "rain" to listOf(
            "Chhatri saath rakho, barish aa rahi hai ☔",
            "Zameen geeli hai, phisalne se bacho!",
            "Chai aur pakode ka mausam hai 🍵",
            "Kapde andar le lo, bheeg jayenge."
        ),
        "snow" to listOf(
            "Brrr! Garam kapde pehno ❄️",
            "Baraf gir rahi hai, dheere chalna.",
            "Haath garam rakho, dastane pehno."
        ),
        "cold" to listOf(
            "Thand hai! Sweater pehen lo 🧣",
            "Subah ki dhoop sekne ka mausam hai.",
            "Garam paani piyo, thand lag jayegi.",
            "Alaav jala lo, par khidki thodi khuli rakhna."
        ),
        "hot" to listOf(
            "Bahut garmi hai! Paani peete raho 💧",
            "Dopahar mein chhaanv mein raho.",
            "Sir dhak ke nikalna, loo lag sakti hai!",
            "Chhaachh ya nimbu paani piyo 🍋"
        ),
        "night" to listOf(
            "Zzz… shubh ratri 🌙",
            "Shhh, billi so rahi hai.",
            "Subah milte hain! Zzz…"
        ),
        "sunny" to listOf(
            "Dhoop khili hai, mausam mast hai 😎",
            "Aaj kapde sukhane ka perfect din!",
            "Khet ho ya sair, aaj sab theek hai.",
            "Dhoop mein thodi der baithna sehat ke liye achha hai."
        ),
        "fog" to listOf(
            "Kohra hai, gaadi dheere chalana 🌫️",
            "Kuch dikh nahi raha… meow?",
            "Headlight on rakho, kohre mein zaroori hai."
        ),
        "cloudy" to listOf(
            "Badal chhaye hain, mausam suhana ☁️",
            "Dhoop-chhaanv ka khel chal raha hai.",
            "Aasmaan dekho, kahin barish na aa jaye."
        )
    )
    private val petLines = listOf(
        "Purrrr… wahi jagah! 😻", "Mrrrow, aur karo na!", "Tum sabse achhe ho ❤️",
        "Mmm… bahut sukoon mil raha hai.", "Purr purr purr 😺"
    )
    private val loveLines = listOf(
        "Tum meri favourite insaan ho! 💕", "Ab toh main yahin rahungi, tumhare paas."
    )
    private val noseLines = listOf(
        "Achoo! Naak mat chhuo 🤧", "Hatchoo! Gudgudi hoti hai!", "Oye! Meri naak!"
    )
    private val earLines = listOf(
        "Kaan mat khinch, meow!", "Meow? Kaun hai?", "Sunai deta hai, zor se mat bolo."
    )
    private val tailLines = listOf(
        "Poonch par pair mat rakhna!", "Meow! Poonch meri jaan hai.", "Hilao mat, chakkar aate hain!"
    )
    private val bodyPokeLines = listOf(
        "Meow!", "Haan boliye?", "Kya hua? Bhookh lagi hai kya?", "Mujhe bhi kuch batao 😸"
    )
    private val annoyedLines = listOf(
        "Bas karo yaar, ab gussa aa raha hai 😾", "Itni baar mat chhuo!", "Hmph. Main naraaz hoon."
    )
    private val treatLines = listOf(
        "Machhli! Yum yum 🐟", "Mmm, mazedaar! Shukriya.", "Aur do na, please? 🥺"
    )
    private val fullLines = listOf(
        "Pet bhar gaya, ab no more 😸", "Bas, ab neend aa rahi hai."
    )
    private val facts = listOf(
        "Billi din mein 12-16 ghante sone wali hoti hai. Main toh 20!",
        "Billiyan zyada tar insaano ke liye hi 'meow' karti hain.",
        "Meri poonch seedhi ho toh samajh lo main khush hoon.",
        "Barish se pehle mitti ki khushboo ko petrichor kehte hain.",
        "Meri whiskers hawa ka mood bhi bata deti hain.",
        "Mujhe taaza machhli pasand hai, aur tumhe?",
        "Koi hai? Mujhse baat karo, ya sar sehla do 🥺"
    )

    // ======================================================== animation
    private var time = 0f
    private var lastNanos = 0L

    private var blinkNext = 2.5f
    private var blinkT = -1f
    private var twitchNext = 5f
    private var twitchT = -1f
    private var twitchLeft = true
    private var earFlickT = -1f
    private var tailFlickT = 0f

    private var lookNext = 2f
    private var lookX = 0f
    private var lookY = 0f
    private var lookTx = 0f
    private var lookTy = 0f
    private var fingerTx = 0f
    private var fingerTy = 0f
    private var fingerHold = 0f

    private var pupil = 0.5f
    private var pupilKick = 0f
    private var lidDrop = 0f

    private var hopT = -1f
    private var happyT = 0f
    private var annoyedT = 0f
    private var refuseT = 0f

    private var beh = Beh.NONE
    private var behT = 0f
    private var behDur = 1f
    private var nextBeh = 7f

    private var pokeCount = 0
    private var lastPokeTime = -99f
    private var affection = 0f

    private var treatT = -1f
    private var fishX = 0f
    private var fishY = 0f
    private var treatsGiven = 0
    private var treatWindowStart = -999f

    private var bfActive = false
    private var bfT = 0f
    private var bfX = 0f
    private var bfY = 0f
    private var bfNext = 9f

    private var bubbleText = ""
    private var bubbleLayout: StaticLayout? = null
    private var bubbleT = -1f
    private var bubbleHold = 3.6f
    private var chatterNext = 28f

    // ============================================================== touch
    private var touching = false
    private var petting = false
    private var longFired = false
    private var touchRegion = R_NONE
    private var moved = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downNanos = 0L
    private var petSession = 0f
    private var petTotal = 0f
    private var heartTimer = 0f
    private var hapticTimer = 0f
    private var fOx = 0f
    private var fOy = 0f
    private var fS = 1f

    // ============================================================ particles
    private class Heart(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val size: Float)

    private val hearts = ArrayList<Heart>()

    // ============================================================== colors
    private val fur = 0xFFF2A03F.toInt()
    private val furDark = 0xFFC9691A.toInt()
    private val cream = 0xFFFFEBCB.toInt()
    private val pink = 0xFFF6A5B7.toInt()
    private val ink = 0xFF2B1B12.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val tickDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 0.9f; color = 0x388A4A17
    }
    private val tickLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 0.9f; color = 0x50FFE6B8
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E2A33.toInt()
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val zPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD_ITALIC)
    }

    private val bodyShader = RadialGradient(
        210f, 146f, 104f,
        intArrayOf(0xFFFFC885.toInt(), 0xFFF2A03F.toInt(), 0xFFD6762A.toInt()),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
    )
    private val headShader = RadialGradient(
        210f, 98f, 74f,
        intArrayOf(0xFFFFCB8C.toInt(), 0xFFF4A444.toInt(), 0xFFDB8530.toInt()),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
    )
    private val chinShade = RadialGradient(
        225f, 152f, 30f, intArrayOf(0x66803E10, 0x00803E10), null, Shader.TileMode.CLAMP
    )
    private val irisShader = RadialGradient(
        0f, 0f, 10f,
        intArrayOf(0xFFE0EC7A.toInt(), 0xFF8BC34A.toInt(), 0xFF2E5A12.toInt()),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
    )
    private val groundShader = RadialGradient(
        225f, 199f, 64f, intArrayOf(0x55000000, 0x00000000), null, Shader.TileMode.CLAMP
    )

    // deterministic fur texture
    private val bodyDark = genTicks(120, 225f, 168f, 43f, 36f, 5f, 2f, 11)
    private val bodyLight = genTicks(90, 222f, 160f, 40f, 32f, 5f, 2f, 12)
    private val headDark = genTicks(80, 225f, 116f, 36f, 30f, 4f, 1.5f, 13)
    private val headLight = genTicks(70, 222f, 108f, 34f, 26f, 4f, 1.5f, 14)

    private val path = Path()
    private val seg = Path()
    private val eyePath = Path()
    private val rect = RectF()
    private val measure = PathMeasure()
    private val tailRings = floatArrayOf(0.3f, 0.52f, 0.72f)
    private val eyeXs = floatArrayOf(207f, 243f)
    private val zSizes = floatArrayOf(15f, 19f, 24f)
    private val zXs = floatArrayOf(262f, 274f, 285f)
    private val zYs = floatArrayOf(86f, 66f, 44f)

    private fun genTicks(n: Int, cx: Float, cy: Float, rx: Float, ry: Float, len: Float, down: Float, seed: Int): FloatArray {
        val rnd = Random(seed)
        val a = FloatArray(n * 4)
        var i = 0
        repeat(n) {
            val ang = rnd.nextFloat() * 2f * pif
            val r = sqrt(rnd.nextFloat())
            val x = cx + cos(ang) * r * rx
            val y = cy + sin(ang) * r * ry
            a[i++] = x
            a[i++] = y
            a[i++] = x + (x - cx) / rx * len * 0.5f
            a[i++] = y + (y - cy) / ry * len * 0.5f + down
        }
        return a
    }

    init {
        isClickable = true
        contentDescription = "Billu the weather cat. Pet, poke or hold for a treat."
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun withAlpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    // ==================================================================== API

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
        isDayNow = isDay
        asleep = !isDay && !storm && !rain
        scared = storm
        umbrella = rain || storm
        hat = cold || snow
        scarf = cold || snow
        shiver = cold || snow
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
        dilation = when {
            storm -> 1f
            !isDay -> 0.85f
            shades -> 0.2f
            code <= 1 -> 0.25f
            else -> 0.5f
        }
        if (newMood != mood) {
            mood = newMood
            hopT = 0f
        }
        invalidate()
    }

    /** Live numbers so the cat can comment on them. All optional. */
    fun setContext(tempC: Double, feelsC: Double, humidity: Int, windKmh: Double, rainChance: Int, uv: Double, aqi: Int?) {
        this.windKmh = windKmh.toFloat().coerceIn(0f, 80f)
        val t = Math.round(tempC).toInt()
        val f = Math.round(feelsC).toInt()
        val w = Math.round(windKmh).toInt()
        val l = ArrayList<String>()
        l.add(
            when {
                tempC >= 40 -> "Aaj $t° hai! Bahar nikle toh topi aur paani saath rakho."
                tempC >= 35 -> "$t° ki garmi hai, dhoop mein zyada der mat raho."
                tempC <= 10 -> "Sirf $t°! Rajai se bahar mat niklo."
                tempC <= 15 -> "$t° hai, thand lag sakti hai. Sweater pehno."
                else -> "$t° hai, mausam bilkul theek hai."
            }
        )
        if (abs(feelsC - tempC) >= 4) l.add("Asal mein $f° jaisa mehsoos ho raha hai.")
        if (rainChance >= 60) l.add("Barish ke $rainChance% chances hain, chhatri le lo.")
        else if (rainChance in 30..59) l.add("Thodi barish ho sakti hai ($rainChance%).")
        if (humidity >= 75) l.add("Umas hai ($humidity%), pasina aayega.")
        else if (humidity <= 25) l.add("Hawa bahut sukhi hai, paani peete raho.")
        if (windKmh >= 25) l.add("Tez hawa chal rahi hai, $w km/h.")
        if (uv >= 8) l.add("UV ${Math.round(uv)} hai, dhoop mein dhak ke nikalna.")
        else if (uv >= 6) l.add("UV thoda tez hai, topi pehen lo.")
        if (aqi != null) {
            if (aqi >= 151) l.add("Hawa kharab hai (AQI $aqi), mask lagao.")
            else if (aqi >= 101) l.add("AQI $aqi hai: bachche aur bujurg savdhan.")
            else if (aqi <= 50) l.add("Hawa saaf hai, gehri saans lo!")
        }
        ctxLines = l
    }

    /** Say hello (time-of-day first, later calls give a tip or live data). */
    fun greet() {
        greetCount++
        say(if (greetCount == 1) timeGreeting() else pickLine())
    }

    /** Show any message in the speech bubble. */
    fun say(text: String, durationMs: Long = 3800L) {
        bubbleText = text
        bubbleHold = max(2.2f, durationMs / 1000f)
        bubbleLayout = null
        bubbleT = 0f
        chatterNext = 25f + Random.nextFloat() * 25f
        invalidate()
    }

    private fun timeGreeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hello = when (h) {
            in 5..11 -> "Suprabhat! ☀️"
            in 12..16 -> "Namaste! Dopahar ho gayi."
            in 17..20 -> "Shubh sandhya! 🌇"
            else -> "Shubh ratri! 🌙"
        }
        return hello + " " + pick(tips.getValue(mood))
    }

    private fun pick(list: List<String>) = list[Random.nextInt(list.size)]

    private fun pickLine(): String {
        val r = Random.nextFloat()
        return when {
            r < 0.45f && ctxLines.isNotEmpty() -> pick(ctxLines)
            r < 0.8f -> pick(tips.getValue(mood))
            else -> pick(facts)
        }
    }

    // ================================================================ touch

    private fun regionAt(x: Float, y: Float): Int {
        if (hypot(x - 225f, y - 126f) < 11f) return R_NOSE
        if (y in 60f..102f && ((x in 186f..216f) || (x in 234f..264f))) return R_EAR
        if (((x - 225f) / 48f).let { it * it } + ((y - 108f) / 50f).let { it * it } <= 1f) return R_HEAD
        if (x > 262f && y in 118f..200f) return R_TAIL
        if (((x - 225f) / 52f).let { it * it } + ((y - 168f) / 42f).let { it * it } <= 1f) return R_BODY
        return R_NONE
    }

    private fun follow(lx: Float, ly: Float) {
        fingerTx = ((lx - 225f) / 22f).coerceIn(-3.2f, 3.2f)
        fingerTy = ((ly - 116f) / 20f).coerceIn(-2f, 2f)
        fingerHold = 1.4f
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val lx = (e.x - fOx) / fS + 4f
        val ly = (e.y - fOy) / fS
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchRegion = regionAt(lx, ly)
                if (touchRegion == R_NONE) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                moved = 0f
                lastX = e.x
                lastY = e.y
                touching = true
                petting = false
                longFired = false
                downNanos = System.nanoTime()
                follow(lx, ly)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touching) return false
                moved += hypot(e.x - lastX, e.y - lastY)
                lastX = e.x
                lastY = e.y
                follow(lx, ly)
                val r = regionAt(lx, ly)
                val onHead = r == R_HEAD || r == R_EAR || r == R_NOSE
                if (moved > dp(14f) && onHead && !longFired) {
                    if (!petting) {
                        petting = true
                        petSession = 0f
                    }
                } else if (!onHead) {
                    petting = false
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!touching) return false
                touching = false
                val wasPetting = petting
                petting = false
                if (e.actionMasked == MotionEvent.ACTION_UP) {
                    if (wasPetting) {
                        endPet()
                    } else if (!longFired && moved < dp(12f)) {
                        performClick()
                        handleTap(touchRegion)
                    }
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun endPet() {
        if (petSession > 1.6f) {
            say(if (affection > 0.85f) pick(loveLines) else pick(petLines), 3000L)
        }
        petSession = 0f
    }

    private fun handleTap(region: Int) {
        // poke counter -> grumpy cat
        if (time - lastPokeTime > 6f) pokeCount = 0
        pokeCount++
        lastPokeTime = time
        if (pokeCount >= 7) {
            pokeCount = 0
            annoyedT = 4f
            say(pick(annoyedLines), 3200L)
            return
        }
        when (region) {
            R_NOSE -> {
                startBeh(Beh.SNEEZE, 0.9f)
                pupilKick = 0.35f
                say(pick(noseLines), 2200L)
            }
            R_EAR -> {
                earFlickT = 0f
                say(pick(earLines), 2600L)
            }
            R_TAIL -> {
                tailFlickT = 1.1f
                say(pick(tailLines), 2800L)
            }
            R_HEAD -> {
                happyT = 1.3f
                spawnHeart(2)
                say(pick(petLines), 2600L)
            }
            else -> {
                hopT = 0f
                happyT = 1f
                say(if (Random.nextBoolean()) pick(tips.getValue(mood)) else pick(bodyPokeLines))
            }
        }
    }

    private fun giveTreat() {
        if (treatT >= 0f || beh == Beh.EAT) return
        if (time - treatWindowStart > 90f) {
            treatWindowStart = time
            treatsGiven = 0
        }
        if (treatsGiven >= 3) {
            refuseT = 1.4f
            say(pick(fullLines), 3000L)
            return
        }
        treatsGiven++
        treatT = 0f
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun spawnHeart(n: Int) {
        repeat(n) {
            if (hearts.size < 14) {
                hearts.add(
                    Heart(
                        x = 205f + Random.nextFloat() * 40f, y = 80f + Random.nextFloat() * 10f,
                        vx = (Random.nextFloat() - 0.5f) * 14f, vy = -(18f + Random.nextFloat() * 14f),
                        life = 1.4f, size = 5f + Random.nextFloat() * 4f
                    )
                )
            }
        }
    }

    // ============================================================== behaviours

    private fun startBeh(b: Beh, dur: Float) {
        beh = b
        behT = 0f
        behDur = dur
    }

    private fun canIdle() = !asleep && !scared && !petting && !touching && hopT < 0f &&
        treatT < 0f && annoyedT <= 0f && beh == Beh.NONE

    private fun startIdleBehaviour() {
        val r = Random.nextFloat()
        when {
            r < 0.30f -> startBeh(Beh.YAWN, 1.7f)
            r < 0.62f -> if (!umbrella) startBeh(Beh.WASH, 2.8f) else startBeh(Beh.YAWN, 1.7f)
            r < 0.82f -> tailFlickT = 1.0f
            r < 0.92f && (shiver) -> {
                startBeh(Beh.SNEEZE, 0.9f)
                say("Achoo! Thand lag gayi 🤧", 2200L)
            }
            else -> earFlickT = 0f
        }
    }

    // ================================================================ update

    private fun update(dt: Float) {
        time += dt

        // blinking, ear twitches
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
        if (earFlickT >= 0f) {
            earFlickT += dt / 0.45f
            if (earFlickT >= 1f) earFlickT = -1f
        }
        if (tailFlickT > 0f) tailFlickT = max(0f, tailFlickT - dt)
        if (annoyedT > 0f) annoyedT = max(0f, annoyedT - dt)
        if (refuseT > 0f) refuseT = max(0f, refuseT - dt)

        // idle behaviours
        if (beh == Beh.NONE) {
            nextBeh -= dt
            if (nextBeh <= 0f) {
                if (canIdle()) startIdleBehaviour()
                nextBeh = 8f + Random.nextFloat() * 9f
            }
        } else {
            behT += dt / behDur
            if (behT >= 1f) {
                beh = Beh.NONE
                behT = 0f
            }
        }

        // petting: hearts, purr haptics, affection
        if (petting) {
            petSession += dt
            petTotal += dt
            affection = min(1f, affection + dt * 0.18f)
            heartTimer -= dt
            if (heartTimer <= 0f) {
                spawnHeart(1)
                heartTimer = 0.28f
            }
            hapticTimer -= dt
            if (hapticTimer <= 0f) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                hapticTimer = 0.13f
            }
            if (petSession > 4.5f && affection > 0.85f && bubbleT < 0f) say(pick(loveLines), 3000L)
        } else {
            affection = max(0f, affection - dt * 0.03f)
        }
        if (hearts.isNotEmpty()) {
            val iter = hearts.iterator()
            while (iter.hasNext()) {
                val h = iter.next()
                h.life -= dt
                h.x += h.vx * dt
                h.y += h.vy * dt
                if (h.life <= 0f) iter.remove()
            }
        }

        // treat: fish falls to the mouth, then chomping
        if (treatT >= 0f) {
            treatT += dt
            val e = min(1f, treatT / 0.8f)
            fishX = 110f + 115f * e
            fishY = -10f + 146f * e
            if (treatT >= 0.8f) {
                treatT = -1f
                startBeh(Beh.EAT, 1.7f)
                affection = min(1f, affection + 0.25f)
                say(pick(treatLines), 3000L)
            }
        }
        // long press = treat
        if (touching && !petting && !longFired && moved < dp(10f) &&
            (System.nanoTime() - downNanos) > 550_000_000L && touchRegion != R_NONE
        ) {
            longFired = true
            giveTreat()
        }

        // butterfly
        if (bfActive) {
            bfT += dt
            if (bfT > 9f) bfActive = false
            bfX = 60f + 150f * (0.5f + 0.5f * sin(bfT * 0.7f))
            bfY = 70f + 35f * sin(bfT * 1.9f) + 10f * sin(bfT * 5f)
        } else {
            bfNext -= dt
            if (bfNext <= 0f) {
                bfNext = 14f + Random.nextFloat() * 18f
                if (isDayNow && !asleep && !umbrella && !shiver && beh == Beh.NONE &&
                    (mood == "sunny" || mood == "cloudy")
                ) {
                    bfActive = true
                    bfT = 0f
                    if (bubbleT < 0f && Random.nextBoolean()) say("Titli! Meow! 🦋", 2400L)
                }
            }
        }

        // where the eyes look
        lookNext -= dt
        if (treatT >= 0f) {
            lookTx = ((fishX - 225f) / 30f).coerceIn(-3.2f, 3.2f)
            lookTy = ((fishY - 116f) / 25f).coerceIn(-2f, 2f)
        } else if (fingerHold > 0f) {
            fingerHold -= dt
            lookTx = fingerTx
            lookTy = fingerTy
        } else if (bfActive) {
            lookTx = ((bfX - 225f) / 25f).coerceIn(-3.2f, 3.2f)
            lookTy = ((bfY - 116f) / 18f).coerceIn(-2f, 2f)
        } else if (lookNext <= 0f) {
            lookTx = (Random.nextFloat() - 0.5f) * 5f
            lookTy = (Random.nextFloat() - 0.5f) * 3f
            if (umbrella) lookTy = -2.4f
            lookNext = 1.6f + Random.nextFloat() * 2.6f
        }
        val k = min(1f, dt * 8f)
        lookX += (lookTx - lookX) * k
        lookY += (lookTy - lookY) * k

        // pupils dilate with light and feelings; lids droop when grumpy or sleepy
        pupilKick = max(0f, pupilKick - dt * 1.2f)
        val pt = when {
            scared -> 1f
            annoyedT > 0f -> 0.12f
            else -> min(1f, dilation + pupilKick)
        }
        pupil += (pt - pupil) * min(1f, dt * 5f)
        val lt = if (annoyedT > 0f) 0.6f else if (!isDayNow && !asleep) 0.3f else 0f
        lidDrop += (lt - lidDrop) * min(1f, dt * 4f)

        if (hopT >= 0f) {
            hopT += dt / 0.55f
            if (hopT >= 1f) hopT = -1f
        }
        if (happyT > 0f) happyT = max(0f, happyT - dt)

        // speech bubble + idle chatter
        if (bubbleT >= 0f) {
            bubbleT += dt
            if (bubbleT > bubbleHold + 0.6f) bubbleT = -1f
        } else {
            chatterNext -= dt
            if (chatterNext <= 0f) {
                chatterNext = 30f + Random.nextFloat() * 30f
                if (!touching && beh == Beh.NONE) say(if (asleep) pick(tips.getValue("night")) else pickLine(), 3600L)
            }
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
        fS = s
        fOx = ox
        fOy = oy

        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(s, s)

        val hop = if (hopT >= 0f) -sin(hopT * pif) * 28f * (1f - 0.3f * hopT) else 0f
        var tremble = 0f
        if (animate) {
            if (scared) tremble += sin(time * 58f) * 1.3f
            if (shiver && !asleep) tremble += sin(time * 47f) * 0.45f
            if (petting) tremble += sin(time * 36f) * 0.3f
        }
        canvas.translate(-4f + tremble, hop)

        drawCat(canvas)
        canvas.restore()

        if (bubbleT >= 0f) drawBubble(canvas, s, ox, oy)

        if (animate && isShown && windowVisibility == VISIBLE) postInvalidateOnAnimation()
    }

    private fun bell(t: Float) = sin(t.coerceIn(0f, 1f) * pif)

    private fun effectiveEyes(): Eyes {
        if (refuseT > 0f) return Eyes.SLEEP
        if (scared) return Eyes.SCARED
        if (beh != Beh.NONE) return Eyes.HAPPY
        if (petting && eyes != Eyes.SLEEP) return Eyes.HAPPY
        if (happyT > 0f && eyes == Eyes.OPEN) return Eyes.HAPPY
        return eyes
    }

    private fun drawCat(c: Canvas) {
        val sleeping = eyes == Eyes.SLEEP && !petting
        val breathAmp = if (sleeping) 0.022f else 0.014f
        val br = sin(time * (if (sleeping) 1.2f else 2.0f)) * breathAmp
        val windBoost = windKmh / 40f
        val lash = annoyedT > 0f
        val flick = tailFlickT > 0f
        val tailSpeed = when {
            lash -> 12f
            flick -> 9f
            scared -> 9f
            sleeping -> 0.8f
            else -> 1.6f
        }
        val tailAmp = when {
            lash -> 11f
            flick -> 12f
            scared -> 4f
            sleeping -> 4f
            else -> 8f + windBoost * 3f
        }
        val sway = sin(time * tailSpeed) * tailAmp
        val tailW = if (scared) 22f else 15f

        // soft ground shadow
        c.save()
        c.scale(1f, 0.12f, 225f, 199f)
        fill.shader = groundShader
        c.drawCircle(225f, 199f, 64f, fill)
        fill.shader = null
        c.restore()

        // ---- tail
        path.reset()
        path.moveTo(262f, 192f)
        path.cubicTo(288f + sway, 198f, 296f + sway * 1.2f, 152f, 280f + sway * 1.6f, 130f)
        stroke.color = fur
        stroke.strokeWidth = tailW
        c.drawPath(path, stroke)
        stroke.color = withAlpha(0xFFFFC885.toInt(), 120)
        stroke.strokeWidth = tailW * 0.35f
        c.save()
        c.translate(-2f, -1f)
        c.drawPath(path, stroke)
        c.restore()
        measure.setPath(path, false)
        val len = measure.length
        stroke.color = furDark
        stroke.strokeWidth = tailW + 0.6f
        for (f in tailRings) {
            seg.reset()
            measure.getSegment(len * f, len * (f + 0.045f), seg, true)
            c.drawPath(seg, stroke)
        }
        seg.reset()
        measure.getSegment(len * 0.88f, len, seg, true)
        stroke.color = cream
        stroke.strokeWidth = tailW
        c.drawPath(seg, stroke)

        // ---- body (breathing scales around the floor)
        c.save()
        c.scale(1f, 1f + br, 225f, 200f)

        fill.shader = null
        fill.color = 0xFFDE8A33.toInt()
        c.drawOval(rect.apply { set(172f, 169f, 212f, 203f) }, fill)
        c.drawOval(rect.apply { set(238f, 169f, 278f, 203f) }, fill)
        fill.shader = bodyShader
        c.drawOval(rect.apply { set(181f, 130f, 269f, 206f) }, fill)
        fill.shader = null
        c.drawLines(bodyDark, tickDark)
        c.drawLines(bodyLight, tickLight)

        // tabby stripes on the flanks
        fill.color = withAlpha(furDark, 235)
        stripe(c, 183f, 146f, 201f, 158f, 7f, 2f)
        stripe(c, 182f, 158f, 200f, 169f, 7f, 2f)
        stripe(c, 184f, 171f, 201f, 179f, 6f, 1.5f)
        stripe(c, 267f, 146f, 249f, 158f, 7f, -2f)
        stripe(c, 268f, 158f, 250f, 169f, 7f, -2f)
        stripe(c, 266f, 171f, 249f, 179f, 6f, -1.5f)

        // cream chest with a fluffy collar
        fill.color = cream
        c.drawOval(rect.apply { set(203f, 148f, 247f, 204f) }, fill)
        for (i in 0 until 5) {
            val x = 207f + i * 9f
            path.reset()
            path.moveTo(x - 4f, 150f)
            path.lineTo(x, 160f + (i % 2) * 3f)
            path.lineTo(x + 4f, 150f)
            path.close()
            c.drawPath(path, fill)
        }
        fill.shader = chinShade
        c.drawOval(rect.apply { set(195f, 138f, 255f, 170f) }, fill)
        fill.shader = null

        // front legs and paws (right paw is busy when washing or holding the umbrella)
        val washing = beh == Beh.WASH
        stroke.color = fur
        stroke.strokeWidth = 17f
        c.drawLine(213f, 168f, 213f, 190f, stroke)
        if (!umbrella && !washing) c.drawLine(237f, 168f, 237f, 190f, stroke)
        fill.color = cream
        c.drawOval(rect.apply { set(201f, 186f, 225f, 202f) }, fill)
        if (!umbrella && !washing) c.drawOval(rect.apply { set(225f, 186f, 249f, 202f) }, fill)
        stroke.color = 0xFFD9B98F.toInt()
        stroke.strokeWidth = 1.5f
        c.drawLine(210f, 193f, 210f, 200f, stroke)
        c.drawLine(216f, 193f, 216f, 200f, stroke)
        if (!umbrella && !washing) {
            c.drawLine(231f, 193f, 231f, 200f, stroke)
            c.drawLine(237f, 193f, 237f, 200f, stroke)
        }
        c.restore()

        // ---- umbrella handle (behind the head)
        val tiltBase = -7f - windBoost * 5f
        val tilt = tiltBase + sin(time * 1.3f) * 2f + (if (scared) sin(time * 40f) * 2f else 0f)
        if (umbrella) {
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

        if (scarf) drawScarf(c, windBoost)

        // ---- head
        var headDy = if (sleeping) 3f + sin(time * 1.2f) * 1.2f else sin(time * 2.0f) * 0.8f
        var headRot = when {
            sleeping -> -6f
            scared -> sin(time * 40f) * 1.5f
            else -> sin(time * 0.7f) * 1.2f
        }
        headRot += lookX * 0.8f
        if (refuseT > 0f) headRot += 9f
        if (petting) headRot -= 3f
        when (beh) {
            Beh.YAWN -> {
                headDy -= 3f * bell(behT)
                headRot -= 2f * bell(behT)
            }
            Beh.SNEEZE -> headDy += when {
                behT < 0.4f -> -bell(behT / 0.4f) * 2f
                behT < 0.65f -> 7f * sin((behT - 0.4f) / 0.25f * pif)
                else -> 0f
            }
            else -> Unit
        }
        c.save()
        c.translate(0f, headDy)
        c.rotate(headRot, 225f, 150f)
        drawHead(c)
        c.restore()

        // ---- washing paw (over the cheek)
        if (washing) {
            val e = min(1f, min(behT * 5f, (1f - behT) * 5f))
            val px = 237f + 6f * e + sin(time * 11f) * 3f * e
            val py = 192f - 58f * e + sin(time * 11f) * 2.5f * e
            stroke.color = fur
            stroke.strokeWidth = 15f
            c.drawLine(237f, 170f, px, py + 6f, stroke)
            fill.color = cream
            c.drawOval(rect.apply { set(px - 11f, py - 9f, px + 11f, py + 9f) }, fill)
            stroke.color = 0xFFD9B98F.toInt()
            stroke.strokeWidth = 1.4f
            c.drawLine(px - 4f, py - 3f, px - 4f, py + 5f, stroke)
            c.drawLine(px + 2f, py - 3f, px + 2f, py + 5f, stroke)
        }

        // ---- umbrella paw + canopy
        if (umbrella) {
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

        // ---- extras
        if (sleeping) drawZzz(c)
        if (sweat) drawSweat(c)
        if (breath) drawBreath(c)
        if (beh == Beh.SNEEZE && behT in 0.45f..0.8f) drawSneeze(c, (behT - 0.45f) / 0.35f)
        if (treatT >= 0f) drawFish(c, fishX, fishY, sin(time * 8f) * 15f)
        if (bfActive) drawButterfly(c)
        drawHearts(c)
    }

    private fun stripe(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, bow: Float) {
        val dx = x1 - x0
        val dy = y1 - y0
        val l = hypot(dx, dy)
        val nx = -dy / l
        val ny = dx / l
        val mx = (x0 + x1) / 2f + nx * bow
        val my = (y0 + y1) / 2f + ny * bow
        path.reset()
        path.moveTo(x0 + nx * w * 0.5f, y0 + ny * w * 0.5f)
        path.quadTo(mx + nx * w * 0.35f, my + ny * w * 0.35f, x1, y1)
        path.quadTo(mx - nx * w * 0.35f, my - ny * w * 0.35f, x0 - nx * w * 0.5f, y0 - ny * w * 0.5f)
        path.close()
        c.drawPath(path, fill)
    }

    // ================================================================ head

    private fun drawHead(c: Canvas) {
        val grumpy = annoyedT > 0f
        val twitch = if (twitchT >= 0f) sin(twitchT * pif * 2f) * 11f else 0f
        val flick = if (earFlickT >= 0f) sin(earFlickT * pif * 4f) * 14f else 0f
        val flat = (if (scared) 20f else 0f) + (if (grumpy) 14f else 0f)
        drawEar(c, true, -(if (twitchLeft) twitch else 0f) - flick - flat)
        drawEar(c, false, (if (!twitchLeft) twitch else 0f) + flick + flat)

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

        // cheek fluff tufts behind the face
        fill.shader = null
        fill.color = 0xFFEE9A3B.toInt()
        for (i in 0 until 4) {
            val y = 112f + i * 8f
            path.reset()
            path.moveTo(190f, y - 5f)
            path.lineTo(177f - i * 1.5f, y + 3f)
            path.lineTo(191f, y + 6f)
            path.close()
            c.drawPath(path, fill)
            path.reset()
            path.moveTo(260f, y - 5f)
            path.lineTo(273f + i * 1.5f, y + 3f)
            path.lineTo(259f, y + 6f)
            path.close()
            c.drawPath(path, fill)
        }

        // head shape with fur texture and rim light
        fill.shader = headShader
        c.drawOval(rect.apply { set(187f, 83f, 263f, 150f) }, fill)
        fill.shader = null
        c.drawLines(headDark, tickDark)
        c.drawLines(headLight, tickLight)
        if (isDayNow && !asleep) {
            stroke.color = 0x38FFFFFF
            stroke.strokeWidth = 2f
            c.drawArc(rect.apply { set(189f, 85f, 261f, 148f) }, 195f, 62f, false, stroke)
        }

        // tabby markings: forehead "M", cheek stripes
        fill.color = withAlpha(furDark, 235)
        stripe(c, 225f, 84f, 225f, 100f, 5f, 0f)
        stripe(c, 214f, 86f, 217f, 99f, 4f, 1f)
        stripe(c, 236f, 86f, 233f, 99f, 4f, -1f)
        stripe(c, 189f, 118f, 202f, 121f, 4f, 1f)
        stripe(c, 190f, 127f, 203f, 127f, 3.5f, 0.6f)
        stripe(c, 261f, 118f, 248f, 121f, 4f, -1f)
        stripe(c, 260f, 127f, 247f, 127f, 3.5f, -0.6f)

        // muzzle and whisker pads
        fill.color = cream
        c.drawOval(rect.apply { set(204f, 119f, 227f, 138f) }, fill)
        c.drawOval(rect.apply { set(223f, 119f, 246f, 138f) }, fill)
        c.drawOval(rect.apply { set(217f, 132f, 233f, 144f) }, fill)
        fill.color = 0x55402010
        for (i in 0 until 3) {
            c.drawCircle(211f + i * 2.5f, 128f + (i % 2) * 3f, 1.1f, fill)
            c.drawCircle(239f - i * 2.5f, 128f + (i % 2) * 3f, 1.1f, fill)
        }

        // blush when pleased
        if (effectiveEyes() == Eyes.HAPPY && !grumpy) {
            fill.color = 0x66F28CA1
            c.drawOval(rect.apply { set(190f, 122f, 206f, 132f) }, fill)
            c.drawOval(rect.apply { set(244f, 122f, 260f, 132f) }, fill)
        }

        drawEyes(c, grumpy)

        // nose with a little shine
        fill.color = 0xFFE5788F.toInt()
        stroke.color = 0xFFD9667F.toInt()
        stroke.strokeWidth = 1.8f
        path.reset()
        path.moveTo(220f, 121.5f)
        path.lineTo(230f, 121.5f)
        path.lineTo(225f, 128.5f)
        path.close()
        c.drawPath(path, fill)
        c.drawPath(path, stroke)
        fill.color = 0x66FFFFFF
        c.drawOval(rect.apply { set(222.5f, 122.2f, 226f, 124f) }, fill)

        drawMouth(c)
        drawWhiskers(c)

        if (grumpy) {
            stroke.color = furDark
            stroke.strokeWidth = 2.6f
            c.drawLine(196f, 100f, 214f, 106f, stroke)
            c.drawLine(254f, 100f, 236f, 106f, stroke)
        }
        if (shades) drawShades(c)
        if (hat) drawHat(c)
    }

    private fun drawMouth(c: Canvas) {
        var open = when (beh) {
            Beh.YAWN -> min(1f, bell(behT) * 1.6f)
            Beh.EAT -> 0.15f + 0.5f * (0.5f + 0.5f * sin(behT * pif * 8f))
            Beh.SNEEZE -> if (behT in 0.45f..0.7f) 0.8f else 0f
            else -> 0f
        }
        if (treatT >= 0f) open = max(open, 0.55f)
        if (tongue) open = max(open, 0.4f + 0.25f * sin(time * 13f))

        stroke.color = 0xFF8A4A17.toInt()
        stroke.strokeWidth = 2f
        c.drawLine(225f, 128.5f, 225f, 132f, stroke)

        if (open > 0.08f) {
            val hw = 5.5f + 6f * open
            val top = 133f
            val bot = top + 3f + 12f * open
            fill.color = 0xFF5A1F2B.toInt()
            c.drawOval(rect.apply { set(225f - hw, top, 225f + hw, bot) }, fill)
            fill.color = 0xFFE9808F.toInt()
            c.drawOval(rect.apply { set(225f - hw * 0.6f, top + (bot - top) * 0.45f, 225f + hw * 0.6f, bot + (if (tongue) 5f else 0f)) }, fill)
            if (open > 0.6f) {
                fill.color = Color.WHITE
                for (sx in floatArrayOf(-1f, 1f)) {
                    path.reset()
                    path.moveTo(225f + sx * (hw - 1f), top + 0.5f)
                    path.lineTo(225f + sx * (hw - 3.5f), top + 0.5f)
                    path.lineTo(225f + sx * (hw - 2.2f), top + 4f)
                    path.close()
                    c.drawPath(path, fill)
                }
            }
        } else {
            path.reset()
            path.moveTo(225f, 132f)
            path.quadTo(220f, 138f, 214f, 134f)
            path.moveTo(225f, 132f)
            path.quadTo(230f, 138f, 236f, 134f)
            c.drawPath(path, stroke)
        }
    }

    private fun drawWhiskers(c: Canvas) {
        stroke.color = 0xD9FFFFFF.toInt()
        stroke.strokeWidth = 1.3f
        val wind = windKmh / 25f
        for (i in 0 until 3) {
            val y = 128f + i * 4.5f
            val sw = sin(time * 2.3f + i) * 1.2f + wind * sin(time * 6f + i) * 2f - lookX * 0.5f
            path.reset()
            path.moveTo(205f, y)
            path.quadTo(188f, y - 4f + sw, 171f, y - 6f + i * 7f + sw * 1.6f)
            path.moveTo(245f, y)
            path.quadTo(262f, y - 4f - sw, 279f, y - 6f + i * 7f - sw * 1.6f)
            c.drawPath(path, stroke)
        }
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
        // dark tabby edge
        stroke.color = furDark
        stroke.strokeWidth = 2.2f
        c.drawLine(x(192f), 98f, x(194f), 66f, stroke)
        // inner ear
        path.reset()
        path.moveTo(x(197f), 97f)
        path.lineTo(x(198f), 76f)
        path.lineTo(x(212f), 89f)
        path.close()
        fill.color = 0xFFE88FA5.toInt()
        stroke.color = 0xFFE88FA5.toInt()
        stroke.strokeWidth = 3f
        c.drawPath(path, stroke)
        c.drawPath(path, fill)
        stroke.color = 0xCCFFF3E0.toInt()
        stroke.strokeWidth = 1.3f
        c.drawLine(x(200f), 94f, x(201.5f), 84f, stroke)
        c.drawLine(x(204f), 95f, x(205.5f), 85f, stroke)
        c.drawLine(x(208f), 94f, x(208.5f), 88f, stroke)
        c.restore()
    }

    // ================================================================ eyes

    private fun drawEyes(c: Canvas, grumpy: Boolean) {
        val mode = effectiveEyes()
        for (i in 0 until 2) {
            val ex = eyeXs[i]
            val outer = if (i == 0) -1f else 1f
            val y = 114f
            when (mode) {
                Eyes.SLEEP -> {
                    stroke.color = ink
                    stroke.strokeWidth = 2.6f
                    path.reset()
                    path.moveTo(ex - 8f, y)
                    path.quadTo(ex, y + 7f, ex + 8f, y)
                    c.drawPath(path, stroke)
                    stroke.strokeWidth = 1.5f
                    c.drawLine(ex + outer * 8f, y, ex + outer * 11f, y - 2f, stroke)
                }
                Eyes.HAPPY -> {
                    stroke.color = ink
                    stroke.strokeWidth = 3f
                    path.reset()
                    path.moveTo(ex - 8f, y + 3f)
                    path.quadTo(ex, y - 8f, ex + 8f, y + 3f)
                    c.drawPath(path, stroke)
                    stroke.strokeWidth = 1.5f
                    c.drawLine(ex + outer * 8f, y + 3f, ex + outer * 11f, y + 1f, stroke)
                }
                else -> drawOpenEye(c, ex, y, outer, mode == Eyes.SCARED, grumpy)
            }
        }
    }

    private fun drawOpenEye(c: Canvas, ex: Float, ey: Float, outer: Float, wide: Boolean, grumpy: Boolean) {
        val blink = if (blinkT >= 0f) sin(blinkT * pif) else 0f
        val open = ((1f - 0.95f * blink) * (1f - lidDrop * 0.4f)).coerceAtLeast(0.05f)
        val hw = if (wide) 12f else 10.5f
        val up = if (wide) 11f else 8.4f
        val down = if (wide) 8.5f else 6.2f

        c.save()
        c.translate(ex, ey)
        c.scale(1f, open)

        eyePath.reset()
        eyePath.moveTo(-outer * hw, 2f)
        eyePath.quadTo(0f, -2f * up, outer * hw, -2f)
        eyePath.quadTo(0f, 2f * down, -outer * hw, 2f)
        eyePath.close()

        c.save()
        c.clipPath(eyePath)
        if (wide) {
            fill.shader = null
            fill.color = Color.WHITE
            c.drawRect(-hw, -up - 2f, hw, down + 2f, fill)
        }
        val ir = if (wide) 6.6f else 8.8f
        val lx = lookX * (if (wide) 0.4f else 1f)
        val ly = lookY * (if (wide) 0.4f else 1f)
        c.save()
        c.translate(lx, ly)
        fill.shader = irisShader
        c.drawCircle(0f, 0f, ir, fill)
        fill.shader = null
        fill.color = 0xFF101010.toInt()
        val pw = min(1f + pupil * 5.6f, ir - 0.8f)
        c.drawOval(rect.apply { set(-pw, -ir, pw, ir) }, fill)
        c.restore()
        fill.color = Color.WHITE
        c.drawCircle(-3f + lx * 0.35f, -3.6f + ly * 0.35f, 2.5f, fill)
        c.drawCircle(3.2f + lx * 0.35f, 3.2f + ly * 0.35f, 1.2f, fill)
        fill.color = 0x40301008
        c.drawRect(-hw, -up - 2f, hw, -up * 0.25f, fill)
        if (grumpy) {
            fill.color = withAlpha(furDark, 255)
            c.drawRect(-hw, -up - 3f, hw, -up * 0.5f, fill)
        }
        c.restore()

        stroke.color = ink
        stroke.strokeWidth = 1.7f
        c.drawPath(eyePath, stroke)
        stroke.strokeWidth = 2f
        c.drawLine(outer * hw, -2f, outer * (hw + 4.5f), -5.5f, stroke)
        c.restore()
    }

    // ============================================================== props

    private fun drawShades(c: Canvas) {
        fill.shader = null
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
        c.drawLine(196f, 107f, 202f, 107f, stroke)
        c.drawLine(235f, 107f, 241f, 107f, stroke)
    }

    private fun drawHat(c: Canvas) {
        path.reset()
        path.moveTo(204f, 91f)
        path.quadTo(204f, 65f, 225f, 63f)
        path.quadTo(246f, 65f, 246f, 91f)
        path.close()
        fill.shader = null
        fill.color = 0xFFD94A3D.toInt()
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

    private fun drawScarf(c: Canvas, windBoost: Float) {
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
        val flutter = sin(time * 2.4f) * (3f + windBoost * 4f)
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
        stroke.color = 0xFF5D4037.toInt()
        stroke.strokeWidth = 3.4f
        c.drawLine(cx, cy - r, cx, cy - r - 7f, stroke)

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

    // ============================================================== extras

    private fun drawZzz(c: Canvas) {
        for (i in 0 until 3) {
            val ph = (time * 0.45f + i * 0.30f) % 1f
            zPaint.textSize = zSizes[i]
            zPaint.alpha = (sin(ph * pif) * 255f).toInt().coerceIn(0, 255)
            c.drawText(if (i == 2) "Z" else "z", zXs[i] + ph * 6f, zYs[i] - ph * 20f, zPaint)
        }
    }

    private fun drawSweat(c: Canvas) {
        val ph = (time * (if (scared) 0.9f else 0.55f)) % 1f
        val x = 266f
        val y = 98f + ph * 34f
        fill.shader = null
        fill.color = withAlpha(0xFF7FD6FF.toInt(), ((1f - ph * ph) * 255f).toInt())
        path.reset()
        path.moveTo(x, y - 7f)
        path.quadTo(x + 5.5f, y + 1f, x, y + 4f)
        path.quadTo(x - 5.5f, y + 1f, x, y - 7f)
        c.drawPath(path, fill)
    }

    private fun drawBreath(c: Canvas) {
        for (i in 0 until 3) {
            val ph = (time * 0.55f + i * 0.33f) % 1f
            fill.shader = null
            fill.color = withAlpha(Color.WHITE, ((1f - ph) * 150f).toInt())
            c.drawCircle(214f - ph * 24f, 138f - ph * 18f, 3f + ph * 6f, fill)
        }
    }

    private fun drawSneeze(c: Canvas, p: Float) {
        fill.shader = null
        for (i in 0 until 6) {
            val a = 2.3f + i * 0.28f
            val d = 8f + p * 30f + i * 2f
            fill.color = withAlpha(0xFFDFF3FF.toInt(), ((1f - p) * 200f).toInt())
            c.drawCircle(225f + cos(a) * d, 130f + sin(a) * d * 0.7f, 1.6f + (i % 3) * 0.6f, fill)
        }
    }

    private fun drawFish(c: Canvas, x: Float, y: Float, rot: Float) {
        c.save()
        c.translate(x, y)
        c.rotate(rot)
        fill.shader = null
        fill.color = 0xFF90A4AE.toInt()
        c.drawOval(rect.apply { set(-13f, -6f, 11f, 6f) }, fill)
        fill.color = 0xFFCFD8DC.toInt()
        c.drawOval(rect.apply { set(-10f, 0f, 8f, 5f) }, fill)
        path.reset()
        path.moveTo(10f, 0f)
        path.lineTo(19f, -7f)
        path.lineTo(19f, 7f)
        path.close()
        fill.color = 0xFF78909C.toInt()
        c.drawPath(path, fill)
        fill.color = Color.WHITE
        c.drawCircle(-7f, -1.5f, 2f, fill)
        fill.color = ink
        c.drawCircle(-7f, -1.5f, 1f, fill)
        c.restore()
    }

    private fun drawButterfly(c: Canvas) {
        val a = (min(1f, min(bfT / 0.8f, (9f - bfT) / 0.8f)) * 255f).toInt()
        val flap = 0.25f + 0.75f * abs(sin(time * 16f))
        c.save()
        c.translate(bfX, bfY)
        c.rotate(sin(time * 1.7f) * 20f)
        c.save()
        c.scale(flap, 1f)
        fill.shader = null
        fill.color = withAlpha(0xFF42A5F5.toInt(), a)
        c.drawOval(rect.apply { set(-14f, -10f, -1f, 0f) }, fill)
        c.drawOval(rect.apply { set(1f, -10f, 14f, 0f) }, fill)
        fill.color = withAlpha(0xFF7E57C2.toInt(), a)
        c.drawOval(rect.apply { set(-10f, -1f, -1f, 8f) }, fill)
        c.drawOval(rect.apply { set(1f, -1f, 10f, 8f) }, fill)
        fill.color = withAlpha(Color.WHITE, a)
        c.drawCircle(-8f, -5f, 1.4f, fill)
        c.drawCircle(8f, -5f, 1.4f, fill)
        c.restore()
        stroke.color = withAlpha(ink, a)
        stroke.strokeWidth = 1.8f
        c.drawLine(0f, -6f, 0f, 7f, stroke)
        stroke.strokeWidth = 1f
        c.drawLine(0f, -6f, -3f, -10f, stroke)
        c.drawLine(0f, -6f, 3f, -10f, stroke)
        c.restore()
    }

    private fun drawHearts(c: Canvas) {
        if (hearts.isEmpty()) return
        fill.shader = null
        for (h in hearts) {
            val a = (min(1f, h.life / 0.5f) * 230f).toInt()
            fill.color = withAlpha(0xFFFF6B8B.toInt(), a)
            val s = h.size
            c.save()
            c.translate(h.x, h.y)
            path.reset()
            path.moveTo(0f, s * 0.35f)
            path.cubicTo(-s, -s * 0.4f, -s * 0.2f, -s * 1.1f, 0f, -s * 0.4f)
            path.cubicTo(s * 0.2f, -s * 1.1f, s, -s * 0.4f, 0f, s * 0.35f)
            path.close()
            c.drawPath(path, fill)
            c.restore()
        }
    }

    // ============================================================ speech bubble

    private fun drawBubble(canvas: Canvas, s: Float, ox: Float, oy: Float) {
        val d = resources.displayMetrics.density
        val padH = 11f * d
        val padV = 8f * d
        val right0 = ox + 176f * s
        val maxText = min(right0 - 8f * d, 175f * d) - 2f * padH

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

        val inT = min(1f, bubbleT / 0.28f)
        val overshoot = 1f + 0.12f * sin(inT * pif) * (1f - inT)
        val scale = (0.82f + 0.18f * inT) * overshoot
        val outT = max(0f, bubbleT - bubbleHold) / 0.6f
        val alpha = ((1f - outT) * min(1f, inT * 1.4f) * 245f).toInt().coerceIn(0, 255)

        val right = right0
        var bottom = oy + 112f * s
        var top = bottom - bh
        if (top < 2f * d) {
            top = 2f * d
            bottom = top + bh
        }
        val left = right - bw

        canvas.save()
        canvas.scale(scale, scale, right, bottom)

        fill.shader = null
        fill.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawRoundRect(rect.apply { set(left, top, right, bottom) }, 14f * d, 14f * d, fill)
        val tailY = min(bottom - 6f * d, oy + 112f * s)
        path.reset()
        path.moveTo(right - 1f, tailY - 10f * d)
        path.lineTo(right + 11f * d, tailY)
        path.lineTo(right - 1f, tailY + 3f * d)
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
