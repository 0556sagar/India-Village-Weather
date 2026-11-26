package com.example.indiavillageweather

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
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

    private fun performSearch() {
        val query = etSearch.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "Please enter a location", Toast.LENGTH_SHORT).show()
            return
        }

        hideKeyboard()
        progressBar.visibility = View.VISIBLE

        executor.execute {
            try {
                val geocodeUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(query)}&count=1&language=en&format=json"
                val responseStr = makeHttpRequest(geocodeUrl)
                val json = JSONObject(responseStr)
                val results = json.optJSONArray("results")

                if (results != null && results.length() > 0) {
                    val location = results.getJSONObject(0)
                    val lat = location.getDouble("latitude")
                    val lon = location.getDouble("longitude")
                    val name = location.optString("name", query)
                    val admin1 = location.optString("admin1", "")
                    val country = location.optString("country", "")

                    mainHandler.post {
                        tvLocationName.text = name
                        tvLocationHierarchy.text = listOf(name, admin1, country).filter { it.isNotEmpty() }.joinToString(", ")
                    }

                    fetchWeatherData(lat, lon)
                } else {
                    mainHandler.post {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "Location not found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Search error", e)
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Error searching location", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchWeatherData(lat: Double, lon: Double) {
        executor.execute {
            try {
                val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m&hourly=temperature_2m,precipitation_probability,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max&timezone=auto"
                val responseStr = makeHttpRequest(weatherUrl)
                val json = JSONObject(responseStr)

                val current = json.optJSONObject("current")
                val daily = json.optJSONObject("daily")
                val hourly = json.optJSONObject("hourly")

                mainHandler.post {
                    progressBar.visibility = View.GONE
                    if (current != null) {
                        val temp = current.optDouble("temperature_2m", 0.0)
                        val humidity = current.optInt("relative_humidity_2m", 0)
                        val feelsLike = current.optDouble("apparent_temperature", 0.0)
                        val weatherCode = current.optInt("weather_code", 0)
                        val isDay = current.optInt("is_day", 1)
                        val windSpeed = current.optDouble("wind_speed_10m", 0.0)

                        tvTemp.text = "${temp.toInt()}°C"
                        tvCondition.text = getWeatherDescription(weatherCode)
                        tvFeelsLike.text = "Feels Like: ${feelsLike.toInt()}°C"
                        tvHumidity.text = "Humidity: $humidity%"
                        tvWind.text = "Wind: ${windSpeed.toInt()} km/h"

                        weatherBackgroundView.setWeatherCondition(weatherCode, isDay == 1)
                    }

                    if (daily != null) {
                        val maxTemps = daily.optJSONArray("temperature_2m_max")
                        val minTemps = daily.optJSONArray("temperature_2m_min")
                        val sunrises = daily.optJSONArray("sunrise")
                        val sunsets = daily.optJSONArray("sunset")
                        val uvIndices = daily.optJSONArray("uv_index_max")

                        if (maxTemps != null && minTemps != null && maxTemps.length() > 0) {
                            tvTempRange.text = "High: ${maxTemps.getDouble(0).toInt()}°C | Low: ${minTemps.getDouble(0).toInt()}°C"
                        }

                        if (sunrises != null && sunrises.length() > 0) {
                            tvSunrise.text = "Sunrise: ${formatTime(sunrises.getString(0))}"
                        }

                        if (sunsets != null && sunsets.length() > 0) {
                            tvSunset.text = "Sunset: ${formatTime(sunsets.getString(0))}"
                        }

                        if (uvIndices != null && uvIndices.length() > 0) {
                            tvUvIndex.text = "UV Index: ${uvIndices.getDouble(0)}"
                        }

                        updateDailyForecast(daily)
                    }

                    if (hourly != null) {
                        updateHourlyForecast(hourly)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Weather fetch error", e)
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Error loading weather data", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateHourlyForecast(hourly: JSONObject) {
        layoutHourlyContainer.removeAllViews()
        val times = hourly.optJSONArray("time")
        val temps = hourly.optJSONArray("temperature_2m")
        val precips = hourly.optJSONArray("precipitation_probability")

        if (times != null && temps != null) {
            val count = minOf(times.length(), 24)
            for (i in 0 until count) {
                val itemView = layoutInflater.inflate(android.R.layout.simple_list_item_2, layoutHourlyContainer, false)
                val tv1 = itemView.findViewById<TextView>(android.R.id.text1)
                val tv2 = itemView.findViewById<TextView>(android.R.id.text2)

                val timeStr = formatHour(times.getString(i))
                val tempVal = "${temps.getDouble(i).toInt()}°C"
                val precipVal = if (precips != null && i < precips.length()) "${precips.getInt(i)}%" else ""

                tv1.text = timeStr
                tv2.text = "$tempVal $precipVal".trim()
                tv1.setTextColor(Color.WHITE)
                tv2.setTextColor(Color.WHITE)

                layoutHourlyContainer.addView(itemView)
            }
        }
    }

    private fun updateDailyForecast(daily: JSONObject) {
        layoutDailyContainer.removeAllViews()
        val times = daily.optJSONArray("time")
        val maxTemps = daily.optJSONArray("temperature_2m_max")
        val minTemps = daily.optJSONArray("temperature_2m_min")
        val codes = daily.optJSONArray("weather_code")

        if (times != null && maxTemps != null && minTemps != null) {
            val count = minOf(times.length(), 7)
            for (i in 0 until count) {
                val itemView = layoutInflater.inflate(android.R.layout.simple_list_item_2, layoutDailyContainer, false)
                val tv1 = itemView.findViewById<TextView>(android.R.id.text1)
                val tv2 = itemView.findViewById<TextView>(android.R.id.text2)

                val dayStr = formatDay(times.getString(i))
                val desc = if (codes != null && i < codes.length()) getWeatherDescription(codes.getInt(i)) else ""
                val tempStr = "Max: ${maxTemps.getDouble(i).toInt()}°C / Min: ${minTemps.getDouble(i).toInt()}°C"

                tv1.text = "$dayStr - $desc"
                tv2.text = tempStr
                tv1.setTextColor(Color.WHITE)
                tv2.setTextColor(Color.WHITE)

                layoutDailyContainer.addView(itemView)
            }
        }
    }

    private fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> "Clear sky"
            1, 2, 3 -> "Mainly clear / Partly cloudy"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            71, 73, 75 -> "Snow fall"
            77 -> "Snow grains"
            80, 81, 82 -> "Rain showers"
            85, 86 -> "Snow showers"
            95 -> "Thunderstorm"
            96, 99 -> "Thunderstorm with hail"
            else -> "Unknown"
        }
    }

    private fun formatTime(timeStr: String): String {
        return try {
            if (timeStr.contains("T")) {
                timeStr.substringAfter("T")
            } else {
                timeStr
            }
        } catch (e: Exception) {
            timeStr
        }
    }

    private fun formatHour(timeStr: String): String {
        return try {
            if (timeStr.contains("T")) {
                timeStr.substringAfter("T")
            } else {
                timeStr
            }
        } catch (e: Exception) {
            timeStr
        }
    }

    private fun formatDay(dateStr: String): String {
        return dateStr
    }

    private fun makeHttpRequest(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val response = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            response.append(line)
        }
        reader.close()
        return response.toString()
    }

    private fun hideKeyboard() {
        val view = currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}
