package com.example.indiavillageweather

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var btnLocation: Button
    private lateinit var tvLocation: TextView
    private lateinit var tvPopulation: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var tvWeather: TextView
    private lateinit var tvRainPrediction: TextView
    private lateinit var tvAqi: TextView
    private lateinit var tvUvIndex: TextView
    private lateinit var tvSunTimes: TextView
    private lateinit var progressBar: ProgressBar

    private val defaultLocation = "New Delhi, Delhi"
    private var locationTimeoutJob: Job? = null
    private var activeLocationListener: LocationListener? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            fetchDeviceLocationWeather()
        } else {
            Toast.makeText(this, "Location permission denied. Showing $defaultLocation weather.", Toast.LENGTH_SHORT).show()
            fetchWeatherForVillage(defaultLocation)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etSearch = findViewById(R.id.etSearch)
        btnSearch = findViewById(R.id.btnSearch)
        btnLocation = findViewById(R.id.btnLocation)
        tvLocation = findViewById(R.id.tvLocation)
        tvPopulation = findViewById(R.id.tvPopulation)
        tvTemperature = findViewById(R.id.tvTemperature)
        tvWeather = findViewById(R.id.tvWeather)
        tvRainPrediction = findViewById(R.id.tvRainPrediction)
        tvAqi = findViewById(R.id.tvAqi)
        tvUvIndex = findViewById(R.id.tvUvIndex)
        tvSunTimes = findViewById(R.id.tvSunTimes)
        progressBar = findViewById(R.id.progressBar)

        btnSearch.setOnClickListener {
            val query = etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                fetchWeatherForVillage(query)
            } else {
                Toast.makeText(this, "Please enter a village or city name", Toast.LENGTH_SHORT).show()
            }
        }

        btnLocation.setOnClickListener {
            checkLocationPermissionAndFetch()
        }

        fetchWeatherForVillage(defaultLocation)
    }

    private fun hasLocationPermission(): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return hasFine || hasCoarse
    }

    private fun checkLocationPermissionAndFetch() {
        val permissionsToRequest = mutableListOf<String>()

        if (!hasLocationPermission()) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            fetchDeviceLocationWeather()
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchDeviceLocationWeather() {
        if (!hasLocationPermission()) {
            fetchWeatherForVillage(defaultLocation)
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            fetchWeatherForVillage(defaultLocation)
            return
        }

        if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
            Toast.makeText(this, "Please turn on device Location / GPS", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }

        progressBar.visibility = View.VISIBLE

        try {
            val availableProviders = locationManager.allProviders

            val gpsLocation = if (availableProviders.contains(LocationManager.GPS_PROVIDER) &&
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            ) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else {
                null
            }

            val networkLocation = if (availableProviders.contains(LocationManager.NETWORK_PROVIDER) &&
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            ) {
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } else {
                null
            }

            val bestLastLocation = when {
                gpsLocation != null && networkLocation != null ->
                    if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
                gpsLocation != null -> gpsLocation
                else -> networkLocation
            }

            if (bestLastLocation != null && (System.currentTimeMillis() - bestLastLocation.time) < 2 * 60 * 1000) {
                fetchWeatherByCoords(bestLastLocation.latitude, bestLastLocation.longitude, null, null)
                return
            }

            requestFreshLocation(locationManager, bestLastLocation)

        } catch (e: Exception) {
            e.printStackTrace()
            progressBar.visibility = View.GONE
            fetchWeatherForVillage(defaultLocation)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation(locationManager: LocationManager, fallbackLocation: Location?) {
        safeRemoveLocationUpdates(locationManager)
        locationTimeoutJob?.cancel()

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationTimeoutJob?.cancel()
                safeRemoveLocationUpdates(locationManager)
                fetchWeatherByCoords(location.latitude, location.longitude, null, null)
            }

            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
        }

        activeLocationListener = listener
        var requestedAnyProvider = false
        val availableProviders = locationManager.allProviders

        try {
            if (availableProviders.contains(LocationManager.NETWORK_PROVIDER) &&
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            ) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
                requestedAnyProvider = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (availableProviders.contains(LocationManager.GPS_PROVIDER) &&
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            ) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
                requestedAnyProvider = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (!requestedAnyProvider) {
            activeLocationListener = null
            if (fallbackLocation != null) {
                fetchWeatherByCoords(fallbackLocation.latitude, fallbackLocation.longitude, null, null)
            } else {
                progressBar.visibility = View.GONE
                fetchWeatherForVillage(defaultLocation)
            }
            return
        }

        locationTimeoutJob = lifecycleScope.launch {
            delay(10_000L)
            safeRemoveLocationUpdates(locationManager)

            if (fallbackLocation != null) {
                fetchWeatherByCoords(fallbackLocation.latitude, fallbackLocation.longitude, null, null)
            } else {
                if (!isFinishing && !isDestroyed) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Live location timeout. Showing $defaultLocation", Toast.LENGTH_SHORT).show()
                }
                fetchWeatherForVillage(defaultLocation)
            }
        }
    }

    private fun safeRemoveLocationUpdates(locationManager: LocationManager) {
        try {
            activeLocationListener?.let {
                locationManager.removeUpdates(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            activeLocationListener = null
        }
    }

    private data class PlaceInfo(val name: String, val population: String)

    private fun formatPopulation(rawPop: String?): String {
        if (rawPop.isNullOrBlank()) return "Not available"
        val digitsOnly = rawPop.replace(",", "").trim().toLongOrNull()
        return if (digitsOnly != null) {
            NumberFormat.getNumberInstance(Locale("en", "IN")).format(digitsOnly)
        } else {
            rawPop
        }
    }

    private fun fetchWeatherForVillage(query: String) {
        if (isFinishing || isDestroyed) return
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val geocodeUrl = "https://nominatim.openstreetmap.org/search?format=json&q=$encodedQuery&countrycodes=in&limit=1&extratags=1"

                val request = Request.Builder()
                    .url(geocodeUrl)
                    .header("User-Agent", "IndiaVillageWeather/1.0 (com.example.indiavillageweather)")
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                        val trimmed = responseBody.trim()
                        if (!trimmed.startsWith("[")) {
                            showError("Search service temporarily unavailable")
                            return@use
                        }
                        val jsonArray = JSONArray(trimmed)
                        if (jsonArray.length() > 0) {
                            val firstResult = jsonArray.getJSONObject(0)
                            val lat = firstResult.optString("lat").toDoubleOrNull()
                            val lon = firstResult.optString("lon").toDoubleOrNull()

                            if (lat != null && lon != null) {
                                val displayName = firstResult.optString("display_name", query)
                                val shortName = displayName.split(",").take(2).joinToString(",").trim()
                                val extraTags = firstResult.optJSONObject("extratags")
                                val population = formatPopulation(extraTags?.optString("population"))
                                fetchWeatherByCoordsIO(lat, lon, shortName, population)
                            } else {
                                showError("Invalid coordinates returned for: $query")
                            }
                        } else {
                            showError("Location not found: $query")
                        }
                    } else {
                        showError("Search service unavailable (${response.code})")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showError("Error fetching location details")
            }
        }
    }

    private fun fetchWeatherByCoords(lat: Double, lon: Double, locationTitle: String?, populationText: String?) {
        if (isFinishing || isDestroyed) return
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val place = if (locationTitle != null) {
                PlaceInfo(locationTitle, populationText ?: "Not available")
            } else {
                reverseGeocode(lat, lon)
            }
            fetchWeatherByCoordsIO(lat, lon, place.name, place.population)
        }
    }

    private fun reverseGeocode(lat: Double, lon: Double): PlaceInfo {
        return try {
            val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&extratags=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "IndiaVillageWeather/1.0 (com.example.indiavillageweather)")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()?.trim()
                if (response.isSuccessful && !body.isNullOrBlank() && body.startsWith("{")) {
                    val json = JSONObject(body)
                    val address = json.optJSONObject("address")
                    val villageOrCity = address?.optString("village")
                        ?.ifEmpty { address.optString("suburb") }
                        ?.ifEmpty { address.optString("town") }
                        ?.ifEmpty { address.optString("city") }
                        ?.ifEmpty { address.optString("county") }
                    val state = address?.optString("state")

                    val resolvedName = if (!villageOrCity.isNullOrEmpty() && !state.isNullOrEmpty()) {
                        "$villageOrCity, $state"
                    } else {
                        json.optString("display_name", "Current Location").split(",").take(2).joinToString(",")
                    }

                    val extraTags = json.optJSONObject("extratags")
                    val pop = formatPopulation(extraTags?.optString("population"))
                    PlaceInfo(resolvedName, pop)
                } else {
                    PlaceInfo("Current Location", "Not available")
                }
            }
        } catch (e: Exception) {
            PlaceInfo("Current Location", "Not available")
        }
    }

    private data class AirAndUvData(val aqi: Int?, val pm25: Double?, val uvIndex: Double?)

    private fun fetchAirQualityAndUv(lat: Double, lon: Double): AirAndUvData {
        return try {
            val url = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon" +
                "&current=us_aqi,pm2_5,uv_index&timezone=auto"
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()?.trim()
                if (response.isSuccessful && !body.isNullOrBlank() && body.startsWith("{")) {
                    val current = JSONObject(body).optJSONObject("current")
                    val aqi = if (current != null && current.has("us_aqi") && !current.isNull("us_aqi")) {
                        current.optInt("us_aqi")
                    } else null
                    val pm25 = if (current != null && current.has("pm2_5") && !current.isNull("pm2_5")) {
                        current.optDouble("pm2_5")
                    } else null
                    val uv = if (current != null && current.has("uv_index") && !current.isNull("uv_index")) {
                        current.optDouble("uv_index")
                    } else null
                    AirAndUvData(aqi, pm25, uv)
                } else {
                    AirAndUvData(null, null, null)
                }
            }
        } catch (e: Exception) {
            AirAndUvData(null, null, null)
        }
    }

    private fun formatSunTime(isoDateTime: String?): String {
        if (isoDateTime.isNullOrBlank()) return "--"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            val formatter = SimpleDateFormat("hh:mm a", Locale.US)
            val date = parser.parse(isoDateTime)
            if (date != null) formatter.format(date) else isoDateTime.substringAfter("T")
        } catch (e: Exception) {
            isoDateTime.substringAfter("T", "--")
        }
    }

    private fun getAqiLabel(aqi: Int): String {
        return when {
            aqi <= 50 -> "Good"
            aqi <= 100 -> "Moderate"
            aqi <= 150 -> "Unhealthy for Sensitive"
            aqi <= 200 -> "Unhealthy"
            aqi <= 300 -> "Very Unhealthy"
            else -> "Hazardous"
        }
    }

    private fun getUvLabel(uv: Double): String {
        return when {
            uv < 3.0 -> "Low"
            uv < 6.0 -> "Moderate"
            uv < 8.0 -> "High"
            uv < 11.0 -> "Very High"
            else -> "Extreme"
        }
    }

    private suspend fun fetchWeatherByCoordsIO(
        lat: Double,
        lon: Double,
        locationTitle: String,
        populationText: String
    ) = coroutineScope {
        try {
            val airUvDeferred = async(Dispatchers.IO) { fetchAirQualityAndUv(lat, lon) }

            val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m" +
                "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_sum,precipitation_probability_max,uv_index_max" +
                "&timezone=auto&forecast_days=1"

            val request = Request.Builder().url(weatherUrl).build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()?.trim()
                if (response.isSuccessful && !responseBody.isNullOrBlank() && responseBody.startsWith("{")) {
                    val json = JSONObject(responseBody)
                    val current = json.optJSONObject("current")
                    val daily = json.optJSONObject("daily")

                    if (current == null || daily == null) {
                        showError("Incomplete weather data received")
                        return@use
                    }

                    val temp = current.optDouble("temperature_2m", Double.NaN)
                    val feelsLike = current.optDouble("apparent_temperature", temp)
                    val humidity = current.optInt("relative_humidity_2m", 0)
                    val windSpeed = current.optDouble("wind_speed_10m", 0.0)
                    val weatherCode = current.optInt("weather_code", -1)

                    val maxTemp = daily.optJSONArray("temperature_2m_max")?.optDouble(0, temp) ?: temp
                    val minTemp = daily.optJSONArray("temperature_2m_min")?.optDouble(0, temp) ?: temp

                    val rainChance = daily.optJSONArray("precipitation_probability_max")?.optInt(0, 0) ?: 0
                    val rainMm = daily.optJSONArray("precipitation_sum")?.optDouble(0, 0.0) ?: 0.0
                    val dailyMaxUv = daily.optJSONArray("uv_index_max")?.optDouble(0, Double.NaN) ?: Double.NaN

                    val sunriseRaw = daily.optJSONArray("sunrise")?.optString(0)
                    val sunsetRaw = daily.optJSONArray("sunset")?.optString(0)
                    val sunriseFormatted = formatSunTime(sunriseRaw)
                    val sunsetFormatted = formatSunTime(sunsetRaw)

                    if (temp.isNaN()) {
                        showError("Temperature unavailable")
                        return@use
                    }

                    val airUv = airUvDeferred.await()
                    val conditionText = getWeatherDescription(weatherCode)

                    val aqiDisplay = if (airUv.aqi != null) {
                        val pmPart = if (airUv.pm25 != null) " (PM2.5: ${airUv.pm25} µg/m³)" else ""
                        "😷 AQI: ${airUv.aqi} (${getAqiLabel(airUv.aqi)})$pmPart"
                    } else {
                        "😷 AQI: Unavailable"
                    }

                    val uvValue = airUv.uvIndex ?: if (!dailyMaxUv.isNaN()) dailyMaxUv else null
                    val uvDisplay = if (uvValue != null) {
                        val formattedUv = String.format(Locale.US, "%.1f", uvValue)
                        "☀️ UV Light: $formattedUv (${getUvLabel(uvValue)})"
                    } else {
                        "☀️ UV Light: Unavailable"
                    }

                    val rainDisplay = "🌧️ Rain Prediction: $rainChance% chance (${String.format(Locale.US, "%.1f", rainMm)} mm expected)"
                    val sunDisplay = "🌅 Sunrise: $sunriseFormatted   |   🌇 Sunset: $sunsetFormatted"

                    withContext(Dispatchers.Main) {
                        if (isFinishing || isDestroyed) return@withContext
                        progressBar.visibility = View.GONE
                        tvLocation.text = locationTitle
                        tvPopulation.text = "👥 Population: $populationText"
                        tvTemperature.text = "${temp}°C"
                        tvWeather.text = buildString {
                            append("$conditionText\n")
                            append("Feels like: ${feelsLike}°C  |  H: ${maxTemp}° L: ${minTemp}°\n")
                            append("Humidity: $humidity%  |  Wind: $windSpeed km/h")
                        }
                        tvRainPrediction.text = rainDisplay
                        tvAqi.text = aqiDisplay
                        tvUvIndex.text = uvDisplay
                        tvSunTimes.text = sunDisplay

                        showWeatherNotification(locationTitle, temp, "$conditionText • Rain: $rainChance%")
                    }
                } else {
                    showError("Failed to load weather data (${response.code})")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showError("Network error while fetching weather")
        }
    }

    private fun showWeatherNotification(location: String, temp: Double, condition: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val channelId = "weather_updates_channel"
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Weather Updates",
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("$location: ${temp}°C")
                .setContentText(condition)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(101, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun showError(message: String) {
        withContext(Dispatchers.Main) {
            if (isFinishing || isDestroyed) return@withContext
            progressBar.visibility = View.GONE
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> "☀️ Clear Sky"
            1, 2, 3 -> "⛅ Partly Cloudy"
            45, 48 -> "🌫️ Foggy"
            51, 53, 55 -> "🌦️ Drizzle"
            61, 63, 65 -> "🌧️ Rain"
            66, 67 -> "🌨️ Freezing Rain"
            71, 73, 75 -> "❄️ Snowfall"
            80, 81, 82 -> "🌦️ Rain Showers"
            95 -> "⛈️ Thunderstorm"
            96, 99 -> "⛈️ Thunderstorm with Hail"
            else -> "🌤️ Moderate Weather"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationTimeoutJob?.cancel()
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager != null) {
            safeRemoveLocationUpdates(locationManager)
        }
    }
}
