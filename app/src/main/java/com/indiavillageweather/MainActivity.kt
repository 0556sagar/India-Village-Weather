package com.example.indiavillageweather

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.animation.DecelerateInterpolator
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.indiavillageweather.ui.CatView
import com.example.indiavillageweather.ui.TempRangeBarView
import com.example.indiavillageweather.ui.WeatherBackgroundView
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var tvLocationName: TextView
    private lateinit var tvLocationHierarchy: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvCondition: TextView
    private lateinit var tvTempRange: TextView
    private lateinit var tvFeelsLike: TextView
    private lateinit var tvHumidity: TextView
    private lateinit var tvWind: TextView
    private lateinit var tvRainChance: TextView
    private lateinit var tvUvIndex: TextView
    private lateinit var tvAirQuality: TextView
    private lateinit var tvSunrise: TextView
    private lateinit var tvSunset: TextView
    private lateinit var layoutHourlyContainer: LinearLayout
    private lateinit var layoutDailyContainer: LinearLayout
    private lateinit var tvPopulation: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var weatherBackgroundView: WeatherBackgroundView
    private lateinit var catView: CatView
    private lateinit var gridDetails: GridLayout
    private lateinit var cardHourly: View
    private lateinit var cardDaily: View

    private var shownTemp: Int? = null
    private var tempAnimator: ValueAnimator? = null
    private var firstBind = true

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var locationManager: LocationManager
    private val PERMISSION_REQUEST_CODE = 1001
    private val CHANNEL_ID = "village_weather_alerts"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupEdgeToEdge()

        initViews()
        setupListeners()
        createNotificationChannel()

        mainHandler.postDelayed({
            checkAndRequestPermissions()
        }, 300)
    }

    // Android 15 forces edge-to-edge; draw the sky behind the bars and pad the content instead.
    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        val content = findViewById<View>(R.id.contentRoot)
        val l = content.paddingLeft
        val t = content.paddingTop
        val r = content.paddingRight
        val b = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom)
            insets
        }
    }

    private fun initViews() {
        etSearch = findViewById(R.id.etSearch)
        btnSearch = findViewById(R.id.btnSearch)
        tvLocationName = findViewById(R.id.tvLocationName)
        tvLocationHierarchy = findViewById(R.id.tvLocationHierarchy)
        tvTemp = findViewById(R.id.tvTemp)
        tvCondition = findViewById(R.id.tvCondition)
        tvTempRange = findViewById(R.id.tvTempRange)
        tvFeelsLike = findViewById(R.id.tvFeelsLike)
        tvHumidity = findViewById(R.id.tvHumidity)
        tvWind = findViewById(R.id.tvWind)
        tvRainChance = findViewById(R.id.tvRainChance)
        tvUvIndex = findViewById(R.id.tvUvIndex)
        tvAirQuality = findViewById(R.id.tvAirQuality)
        tvSunrise = findViewById(R.id.tvSunrise)
        tvSunset = findViewById(R.id.tvSunset)
        layoutHourlyContainer = findViewById(R.id.layoutHourlyContainer)
        layoutDailyContainer = findViewById(R.id.layoutDailyContainer)
        tvPopulation = findViewById(R.id.tvPopulation)
        progressBar = findViewById(R.id.progressBar)
        weatherBackgroundView = findViewById(R.id.weatherBackgroundView)
        catView = findViewById(R.id.catView)
        gridDetails = findViewById(R.id.gridDetails)
        cardHourly = findViewById(R.id.cardHourly)
        cardDaily = findViewById(R.id.cardDaily)
    }

    private fun setupListeners() {
        btnSearch.setOnClickListener {
            performSearch()
        }

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            getCurrentLocationWeather()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

            if (hasLocation) {
                getCurrentLocationWeather()
            } else {
                Toast.makeText(this, "Location permission denied. Search your village manually.", Toast.LENGTH_LONG).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocationWeather() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        progressBar.visibility = View.VISIBLE

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationManager.removeUpdates(this)
                resolveLocationNameAndFetchWeather(location.latitude, location.longitude)
            }

            override fun onProviderDisabled(provider: String) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, "Please turn on GPS", Toast.LENGTH_SHORT).show()
            }
        }

        try {
            val lastKnownGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastKnownNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val bestLastLocation = lastKnownGps ?: lastKnownNet

            if (bestLastLocation != null) {
                resolveLocationNameAndFetchWeather(bestLastLocation.latitude, bestLastLocation.longitude)
            }

            when {
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, locationListener)
                }
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, locationListener)
                }
                else -> {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "GPS is off. Search village manually.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Log.e("MainActivity", "Location error", e)
        }
    }

    private fun resolveLocationNameAndFetchWeather(lat: Double, lon: Double) {
        executor.execute {
            var cityName = "Current Location"
            var hierarchy = "Lat: ${String.format(Locale.getDefault(), "%.2f", lat)}, Lon: ${String.format(Locale.getDefault(), "%.2f", lon)}"

            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    cityName = addr.subLocality ?: addr.locality ?: addr.subAdminArea ?: "My Village"
                    val state = addr.adminArea ?: ""
                    val country = addr.countryName ?: ""
                    hierarchy = listOf(cityName, state, country).filter { it.isNotEmpty() }.joinToString(", ")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Geocoder error", e)
            }

            mainHandler.post {
                tvLocationName.text = cityName
                tvLocationHierarchy.text = hierarchy
                tvPopulation.text = "👥 Population: N/A (GPS)"
            }

            fetchWeatherData(lat, lon, cityName)
        }
    }

    private fun performSearch() {
        val query = etSearch.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "Please enter a village or city name", Toast.LENGTH_SHORT).show()
            return
        }

        hideKeyboard()
        progressBar.visibility = View.VISIBLE

        executor.execute {
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val geoJson = JSONObject(
                    httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json")
                )
                val results = geoJson.optJSONArray("results")
                if (results == null || results.length() == 0) {
                    mainHandler.post {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Location not found: $query", Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }

                val place = results.getJSONObject(0)
                val lat = place.getDouble("latitude")
                val lon = place.getDouble("longitude")
                val name = place.optString("name", query)
                val hierarchy = listOf(
                    name,
                    place.optString("admin2"),
                    place.optString("admin1"),
                    place.optString("country")
                ).filter { it.isNotEmpty() }.distinct().joinToString(", ")
                val population = place.optLong("population", 0L)

                mainHandler.post {
                    tvLocationName.text = name
                    tvLocationHierarchy.text = hierarchy
                    tvPopulation.text = if (population > 0)
                        "👥 Population: ${String.format(Locale.getDefault(), "%,d", population)}"
                    else
                        "👥 Population: N/A"
                }

                fetchWeatherData(lat, lon, name)
            } catch (e: Exception) {
                Log.e("MainActivity", "Search error", e)
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Search failed. Check your internet.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Runs on the background executor thread (blocking network calls).
    private fun fetchWeatherData(lat: Double, lon: Double, cityName: String) {
        try {
            val forecastUrl = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=${String.format(Locale.US, "%.4f", lat)}" +
                    "&longitude=${String.format(Locale.US, "%.4f", lon)}" +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m" +
                    "&hourly=temperature_2m,weather_code,precipitation_probability" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max" +
                    "&timezone=auto&forecast_days=7"
            val root = JSONObject(httpGet(forecastUrl))

            val current = root.getJSONObject("current")
            val hourly = root.getJSONObject("hourly")
            val daily = root.getJSONObject("daily")

            val nowTime = current.getString("time") // e.g. 2026-11-26T09:15
            val sunrise = daily.getJSONArray("sunrise").getString(0).substringAfter("T")
            val sunset = daily.getJSONArray("sunset").getString(0).substringAfter("T")

            // Air quality is optional: don't fail the whole screen if it errors
            var aqi: Int? = null
            try {
                val aqUrl = "https://air-quality-api.open-meteo.com/v1/air-quality" +
                        "?latitude=${String.format(Locale.US, "%.4f", lat)}" +
                        "&longitude=${String.format(Locale.US, "%.4f", lon)}&current=us_aqi"
                val aq = JSONObject(httpGet(aqUrl)).getJSONObject("current")
                if (!aq.isNull("us_aqi")) aqi = aq.getInt("us_aqi")
            } catch (e: Exception) {
                Log.e("MainActivity", "AQI error", e)
            }

            // Next 24 hours starting from the current hour
            val hTimes = hourly.getJSONArray("time")
            val hTemps = hourly.getJSONArray("temperature_2m")
            val hCodes = hourly.getJSONArray("weather_code")
            val hRain = hourly.getJSONArray("precipitation_probability")
            val currentHour = nowTime.substring(0, 13) + ":00"
            var startIdx = 0
            for (i in 0 until hTimes.length()) {
                if (hTimes.getString(i) >= currentHour) {
                    startIdx = i
                    break
                }
            }
            val endIdx = minOf(startIdx + 24, hTimes.length())
            val hours = ArrayList<HourUi>()
            for (i in startIdx until endIdx) {
                hours.add(
                    HourUi(
                        hTimes.getString(i).substringAfter("T"),
                        hCodes.getInt(i),
                        Math.round(hTemps.getDouble(i)).toInt(),
                        hRain.optInt(i, 0)
                    )
                )
            }

            val dTimes = daily.getJSONArray("time")
            val dCodes = daily.getJSONArray("weather_code")
            val dMax = daily.getJSONArray("temperature_2m_max")
            val dMin = daily.getJSONArray("temperature_2m_min")
            val dRain = daily.getJSONArray("precipitation_probability_max")
            val parser = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dayFmt = java.text.SimpleDateFormat("EEE", Locale.getDefault())
            val days = ArrayList<DayUi>()
            for (i in 0 until dTimes.length()) {
                val label = when (i) {
                    0 -> "Today"
                    1 -> "Tomorrow"
                    else -> try {
                        dayFmt.format(parser.parse(dTimes.getString(i))!!)
                    } catch (e: Exception) {
                        dTimes.getString(i)
                    }
                }
                days.add(DayUi(label, dCodes.getInt(i), dMin.getDouble(i), dMax.getDouble(i), dRain.optInt(i, 0)))
            }

            val nowMin = minutesOf(nowTime.substringAfter("T"))
            val twilight = Math.abs(nowMin - minutesOf(sunrise)) <= 50 || Math.abs(nowMin - minutesOf(sunset)) <= 50

            val ui = WeatherUi(
                temp = current.getDouble("temperature_2m"),
                feels = current.getDouble("apparent_temperature"),
                humidity = current.getInt("relative_humidity_2m"),
                wind = current.getDouble("wind_speed_10m"),
                code = current.getInt("weather_code"),
                isDay = current.optInt("is_day", 1) == 1,
                twilight = twilight,
                max = dMax.getDouble(0),
                min = dMin.getDouble(0),
                rainToday = dRain.optInt(0, 0),
                uv = daily.getJSONArray("uv_index_max").optDouble(0, 0.0),
                sunrise = sunrise,
                sunset = sunset,
                aqi = aqi,
                hours = hours,
                days = days
            )

            mainHandler.post { bindWeather(ui) }

            // Simple weather alerts
            when {
                ui.rainToday >= 70 -> showAlertNotification(
                    "Rain likely in $cityName",
                    "Chance of rain today: ${ui.rainToday}%. Carry an umbrella."
                )
                ui.max >= 42 -> showAlertNotification(
                    "Heat alert for $cityName",
                    "High of ${Math.round(ui.max)}°C expected today. Stay hydrated."
                )
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Weather error", e)
            mainHandler.post {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Could not load weather. Check your internet.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ------------------------------------------------------------------ UI

    private fun bindWeather(u: WeatherUi) {
        val (desc, emoji) = describeWeather(u.code)

        // sky + cat react to the same data
        weatherBackgroundView.setWeather(u.code, u.isDay, u.twilight, u.wind.toFloat())
        catView.setWeather(u.code, u.isDay, u.temp)
        val aqiNow = u.aqi
        if (aqiNow != null && aqiNow >= 151) {
            catView.say("Hawa kharab hai, mask lagao \uD83D\uDE37", 4200L)
        } else {
            catView.greet()
        }

        animateTemp(Math.round(u.temp).toInt())
        tvCondition.text = "$emoji  $desc"
        tvTempRange.text = "High ${Math.round(u.max)}°   Low ${Math.round(u.min)}°"

        setTile(tvFeelsLike, "🌡️  Feels like", "${Math.round(u.feels)}°C")
        setTile(tvHumidity, "💧  Humidity", "${u.humidity}%")
        setTile(tvWind, "💨  Wind", "${Math.round(u.wind)} km/h")
        setTile(tvRainChance, "🌧️  Rain chance", "${u.rainToday}%")
        setTile(tvUvIndex, "☀️  UV index", String.format(Locale.US, "%.1f", u.uv), uvLabel(u.uv), uvColor(u.uv))
        if (aqiNow != null) {
            setTile(tvAirQuality, "🌫️  Air quality", "$aqiNow", aqiLabel(aqiNow), aqiColor(aqiNow))
        } else {
            setTile(tvAirQuality, "🌫️  Air quality", "N/A", "No data")
        }
        setTile(tvSunrise, "🌅  Sunrise", to12(u.sunrise, true))
        setTile(tvSunset, "🌇  Sunset", to12(u.sunset, true))

        // hourly chips
        layoutHourlyContainer.removeAllViews()
        u.hours.forEachIndexed { i, h -> layoutHourlyContainer.addView(buildHourChip(h, i == 0)) }

        // 7-day rows with a min..max bar scaled to the week's range
        layoutDailyContainer.removeAllViews()
        val weekMin = u.days.minOf { it.min }
        val weekMax = u.days.maxOf { it.max }
        u.days.forEach { layoutDailyContainer.addView(buildDayRow(it, weekMin, weekMax)) }

        progressBar.visibility = View.GONE
        if (firstBind) {
            firstBind = false
            playEntrance()
        }
    }

    /** One orchestrated reveal on first load: panels rise in sequence. */
    private fun playEntrance() {
        val order = listOf<View>(tvTemp, tvCondition, tvTempRange, gridDetails, cardHourly, cardDaily, tvPopulation)
        val rise = 36 * resources.displayMetrics.density
        order.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = rise
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(80L * i)
                .setDuration(480L)
                .setInterpolator(DecelerateInterpolator(1.6f))
                .start()
        }
    }

    private fun animateTemp(target: Int) {
        val startValue = shownTemp ?: (target - 8)
        shownTemp = target
        tempAnimator?.cancel()
        tempAnimator = ValueAnimator.ofInt(startValue, target).apply {
            duration = 800L
            interpolator = DecelerateInterpolator()
            addUpdateListener { setTempText(it.animatedValue as Int) }
            start()
        }
    }

    private fun setTempText(v: Int) {
        val sp = SpannableString("$v°C")
        sp.setSpan(RelativeSizeSpan(0.38f), sp.length - 2, sp.length, 0)
        tvTemp.text = sp
    }

    private fun setTile(tv: TextView, label: String, value: String, sub: String? = null, valueColor: Int = Color.WHITE) {
        val sb = SpannableStringBuilder()
        sb.append(label)
        sb.setSpan(RelativeSizeSpan(0.82f), 0, sb.length, 0)
        sb.setSpan(ForegroundColorSpan(0xCCFFFFFF.toInt()), 0, sb.length, 0)
        sb.append("\n")
        val vs = sb.length
        sb.append(value)
        sb.setSpan(RelativeSizeSpan(1.3f), vs, sb.length, 0)
        sb.setSpan(StyleSpan(Typeface.BOLD), vs, sb.length, 0)
        sb.setSpan(ForegroundColorSpan(valueColor), vs, sb.length, 0)
        if (sub != null) {
            sb.append("\n")
            val ss = sb.length
            sb.append(sub)
            sb.setSpan(RelativeSizeSpan(0.82f), ss, sb.length, 0)
            sb.setSpan(ForegroundColorSpan(valueColor), ss, sb.length, 0)
        }
        tv.text = sb
    }

    private fun buildHourChip(h: HourUi, isNow: Boolean): TextView {
        val (_, emoji) = describeWeather(h.code)
        val sb = SpannableStringBuilder()
        sb.append(if (isNow) "Now" else to12(h.time, false))
        sb.setSpan(RelativeSizeSpan(0.85f), 0, sb.length, 0)
        sb.setSpan(ForegroundColorSpan(0xCCFFFFFF.toInt()), 0, sb.length, 0)
        sb.append("\n")
        var st = sb.length
        sb.append(emoji)
        sb.setSpan(RelativeSizeSpan(1.7f), st, sb.length, 0)
        sb.append("\n")
        st = sb.length
        sb.append("${h.temp}°")
        sb.setSpan(StyleSpan(Typeface.BOLD), st, sb.length, 0)
        sb.setSpan(RelativeSizeSpan(1.15f), st, sb.length, 0)
        sb.append("\n")
        st = sb.length
        sb.append(if (h.rain >= 20) "💧${h.rain}%" else "\u00A0")
        sb.setSpan(RelativeSizeSpan(0.78f), st, sb.length, 0)
        sb.setSpan(ForegroundColorSpan(0xFF9ADFFF.toInt()), st, sb.length, 0)

        return TextView(this).apply {
            text = sb
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
            minWidth = dp(66)
            setPadding(dp(12), dp(12), dp(12), dp(10))
            background = ContextCompat.getDrawable(this@MainActivity, if (isNow) R.drawable.bg_chip_now else R.drawable.bg_chip)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
        }
    }

    private fun buildDayRow(d: DayUi, weekMin: Double, weekMax: Double): View {
        val (_, emoji) = describeWeather(d.code)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(9), 0, dp(9))
        }
        row.addView(TextView(this).apply {
            text = d.label
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        row.addView(TextView(this).apply {
            val sb = SpannableStringBuilder(emoji)
            sb.setSpan(RelativeSizeSpan(1.3f), 0, sb.length, 0)
            if (d.rain >= 30) {
                sb.append("\n")
                val st = sb.length
                sb.append("${d.rain}%")
                sb.setSpan(RelativeSizeSpan(0.62f), st, sb.length, 0)
                sb.setSpan(ForegroundColorSpan(0xFF9ADFFF.toInt()), st, sb.length, 0)
            }
            text = sb
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        row.addView(TextView(this).apply {
            text = "${Math.round(d.min)}°"
            setTextColor(0xB3FFFFFF.toInt())
            textSize = 15f
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(dp(34), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        row.addView(TempRangeBarView(this).apply {
            setRange(d.min, d.max, weekMin, weekMax)
            layoutParams = LinearLayout.LayoutParams(0, dp(20), 1f).apply {
                marginStart = dp(10)
                marginEnd = dp(10)
            }
        })
        row.addView(TextView(this).apply {
            text = "${Math.round(d.max)}°"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(34), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        return row
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun minutesOf(hhmm: String): Int = hhmm.substring(0, 2).toInt() * 60 + hhmm.substring(3, 5).toInt()

    private fun to12(hhmm: String, withMinutes: Boolean): String {
        val h = hhmm.substring(0, 2).toInt()
        val m = hhmm.substring(3, 5)
        val ap = if (h >= 12) "PM" else "AM"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return if (withMinutes) "$h12:$m $ap" else "$h12 $ap"
    }

    private fun aqiColor(aqi: Int): Int = when {
        aqi <= 50 -> 0xFF7BE495
        aqi <= 100 -> 0xFFFFE066
        aqi <= 150 -> 0xFFFFB366
        aqi <= 200 -> 0xFFFF7A7A
        aqi <= 300 -> 0xFFD88CFF
        else -> 0xFFFF5C8A
    }.toInt()

    private fun uvColor(uv: Double): Int = when {
        uv < 3 -> 0xFF7BE495
        uv < 6 -> 0xFFFFE066
        uv < 8 -> 0xFFFFB366
        uv < 11 -> 0xFFFF7A7A
        else -> 0xFFD88CFF
    }.toInt()

    private fun httpGet(urlString: String): String {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${conn.responseCode}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun describeWeather(code: Int): Pair<String, String> = when (code) {
        0 -> "Clear sky" to "☀️"
        1 -> "Mainly clear" to "🌤️"
        2 -> "Partly cloudy" to "⛅"
        3 -> "Overcast" to "☁️"
        45, 48 -> "Fog" to "🌫️"
        51, 53, 55 -> "Drizzle" to "🌦️"
        56, 57 -> "Freezing drizzle" to "🌧️"
        61, 63, 65 -> "Rain" to "🌧️"
        66, 67 -> "Freezing rain" to "🌧️"
        71, 73, 75, 77 -> "Snow" to "❄️"
        80, 81, 82 -> "Rain showers" to "🌦️"
        85, 86 -> "Snow showers" to "🌨️"
        95 -> "Thunderstorm" to "⛈️"
        96, 99 -> "Thunderstorm with hail" to "⛈️"
        else -> "Unknown" to "🌡️"
    }

    private fun aqiLabel(aqi: Int): String = when {
        aqi <= 50 -> "Good"
        aqi <= 100 -> "Moderate"
        aqi <= 150 -> "Unhealthy for sensitive"
        aqi <= 200 -> "Unhealthy"
        aqi <= 300 -> "Very unhealthy"
        else -> "Hazardous"
    }

    private fun uvLabel(uv: Double): String = when {
        uv < 3 -> "Low"
        uv < 6 -> "Moderate"
        uv < 8 -> "High"
        uv < 11 -> "Very high"
        else -> "Extreme"
    }

    @SuppressLint("MissingPermission")
    private fun showAlertNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        androidx.core.app.NotificationManagerCompat.from(this).notify(1001, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Weather Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Rain and heat alerts for your village"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }
}


private data class HourUi(val time: String, val code: Int, val temp: Int, val rain: Int)

private data class DayUi(val label: String, val code: Int, val min: Double, val max: Double, val rain: Int)

private data class WeatherUi(
    val temp: Double,
    val feels: Double,
    val humidity: Int,
    val wind: Double,
    val code: Int,
    val isDay: Boolean,
    val twilight: Boolean,
    val max: Double,
    val min: Double,
    val rainToday: Int,
    val uv: Double,
    val sunrise: String,
    val sunset: String,
    val aqi: Int?,
    val hours: List<HourUi>,
    val days: List<DayUi>
)
