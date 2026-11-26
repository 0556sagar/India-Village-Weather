package com.example.indiavillageweather

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var locationManager: LocationManager
    private val PERMISSION_REQUEST_CODE = 1001
    private val CHANNEL_ID = "village_weather_alerts"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        createNotificationChannel()

        mainHandler.postDelayed({
            checkAndRequestPermissions()
        }, 300)
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

            val temp = current.getDouble("temperature_2m")
            val feels = current.getDouble("apparent_temperature")
            val humidity = current.getInt("relative_humidity_2m")
            val wind = current.getDouble("wind_speed_10m")
            val code = current.getInt("weather_code")
            val isDay = current.optInt("is_day", 1) == 1
            val nowTime = current.getString("time") // e.g. 2026-11-26T09:15

            val maxToday = daily.getJSONArray("temperature_2m_max").getDouble(0)
            val minToday = daily.getJSONArray("temperature_2m_min").getDouble(0)
            val rainToday = daily.getJSONArray("precipitation_probability_max").optInt(0, 0)
            val uvToday = daily.getJSONArray("uv_index_max").optDouble(0, 0.0)
            val sunrise = daily.getJSONArray("sunrise").getString(0).substringAfter("T")
            val sunset = daily.getJSONArray("sunset").getString(0).substringAfter("T")

            // Air quality is optional: don't fail the whole screen if it errors
            var aqiText = "N/A"
            try {
                val aqUrl = "https://air-quality-api.open-meteo.com/v1/air-quality" +
                        "?latitude=${String.format(Locale.US, "%.4f", lat)}" +
                        "&longitude=${String.format(Locale.US, "%.4f", lon)}&current=us_aqi"
                val aq = JSONObject(httpGet(aqUrl)).getJSONObject("current")
                if (!aq.isNull("us_aqi")) {
                    val aqi = aq.getInt("us_aqi")
                    aqiText = "$aqi (${aqiLabel(aqi)})"
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "AQI error", e)
            }

            // Next 24 hours starting from the current hour
            val hTimes = hourly.getJSONArray("time")
            val hTemps = hourly.getJSONArray("temperature_2m")
            val hCodes = hourly.getJSONArray("weather_code")
            val currentHour = nowTime.substring(0, 13) + ":00"
            var startIdx = 0
            for (i in 0 until hTimes.length()) {
                if (hTimes.getString(i) >= currentHour) {
                    startIdx = i
                    break
                }
            }
            val endIdx = minOf(startIdx + 24, hTimes.length())

            val dTimes = daily.getJSONArray("time")
            val dCodes = daily.getJSONArray("weather_code")
            val dMax = daily.getJSONArray("temperature_2m_max")
            val dMin = daily.getJSONArray("temperature_2m_min")

            val (conditionText, conditionEmoji) = describeWeather(code)

            mainHandler.post {
                tvTemp.text = "${Math.round(temp)}°C"
                tvCondition.text = "$conditionEmoji $conditionText"
                tvTempRange.text = "H: ${Math.round(maxToday)}°  L: ${Math.round(minToday)}°"
                tvFeelsLike.text = "🌡️ Feels like ${Math.round(feels)}°C"
                tvHumidity.text = "💧 Humidity: $humidity%"
                tvWind.text = "💨 Wind: ${Math.round(wind)} km/h"
                tvRainChance.text = "🌧️ Rain chance: $rainToday%"
                tvUvIndex.text = "☀️ UV Index: ${String.format(Locale.US, "%.1f", uvToday)} (${uvLabel(uvToday)})"
                tvAirQuality.text = "🌫️ Air quality: $aqiText"
                tvSunrise.text = "🌅 Sunrise: $sunrise"
                tvSunset.text = "🌇 Sunset: $sunset"

                // If WeatherBackgroundView has a method to set the weather, call it here, e.g.:
                // weatherBackgroundView.setWeather(code, isDay)

                val density = resources.displayMetrics.density
                val pad = (8 * density).toInt()

                layoutHourlyContainer.removeAllViews()
                for (i in startIdx until endIdx) {
                    val hour = hTimes.getString(i).substringAfter("T")
                    val (_, emoji) = describeWeather(hCodes.getInt(i))
                    val tv = TextView(this).apply {
                        text = "$hour\n$emoji\n${Math.round(hTemps.getDouble(i))}°"
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        textSize = 14f
                        setPadding(pad * 2, pad, pad * 2, pad)
                    }
                    layoutHourlyContainer.addView(tv)
                }

                layoutDailyContainer.removeAllViews()
                val parser = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val dayFmt = java.text.SimpleDateFormat("EEE, d MMM", Locale.getDefault())
                for (i in 0 until dTimes.length()) {
                    val label = try {
                        dayFmt.format(parser.parse(dTimes.getString(i))!!)
                    } catch (e: Exception) {
                        dTimes.getString(i)
                    }
                    val (desc, emoji) = describeWeather(dCodes.getInt(i))
                    val tv = TextView(this).apply {
                        text = "$label   $emoji $desc   ${Math.round(dMin.getDouble(i))}° / ${Math.round(dMax.getDouble(i))}°"
                        setTextColor(Color.WHITE)
                        textSize = 15f
                        setPadding(pad, pad, pad, pad)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                    layoutDailyContainer.addView(tv)
                }

                progressBar.visibility = View.GONE
            }

            // Simple weather alerts
            when {
                rainToday >= 70 -> showAlertNotification(
                    "Rain likely in $cityName",
                    "Chance of rain today: $rainToday%. Carry an umbrella."
                )
                maxToday >= 42 -> showAlertNotification(
                    "Heat alert for $cityName",
                    "High of ${Math.round(maxToday)}°C expected today. Stay hydrated."
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

