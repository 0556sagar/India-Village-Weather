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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var btnLocation: Button
    private lateinit var tvLocation: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var tvWeather: TextView
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
        tvTemperature = findViewById(R.id.tvTemperature)
        tvWeather = findViewById(R.id.tvWeather)
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
                fetchWeatherByCoords(bestLastLocation.latitude, bestLastLocation.longitude, null)
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
                fetchWeatherByCoords(location.latitude, location.longitude, null)
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
                fetchWeatherByCoords(fallbackLocation.latitude, fallbackLocation.longitude, null)
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
                fetchWeatherByCoords(fallbackLocation.latitude, fallbackLocation.longitude, null)
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

    private fun fetchWeatherForVillage(query: String) {
        if (isFinishing || isDestroyed) return
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val geocodeUrl = "https://nominatim.openstreetmap.org/search?format=json&q=$encodedQuery&countrycodes=in&limit=1"

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
                                fetchWeatherByCoordsIO(lat, lon, shortName)
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

    private fun fetchWeatherByCoords(lat: Double, lon: Double, locationTitle: String?) {
        if (isFinishing || isDestroyed) return
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val resolvedName = locationTitle ?: reverseGeocode(lat, lon)
            fetchWeatherByCoordsIO(lat, lon, resolvedName)
        }
    }

    private fun reverseGeocode(lat: Double, lon: Double): String {
        return try {
            val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon"
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

                    if (!villageOrCity.isNullOrEmpty() && !state.isNullOrEmpty()) {
                        "$villageOrCity, $state"
                    } else {
                        json.optString("display_name", "Current Location").split(",").take(2).joinToString(",")
                    }
                } else {
                    "Current Location"
                }
            }
        } catch (e: Exception) {
            "Current Location"
        }
    }

    private suspend fun fetchWeatherByCoordsIO(lat: Double, lon: Double, locationTitle: String) {
        try {
            val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
                "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"

            val request = Request.Builder()
                .url(weatherUrl)
                .build()

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

                    val maxArr = daily.optJSONArray("temperature_2m_max")
                    val minArr = daily.optJSONArray("temperature_2m_min")
                    val maxTemp = if (maxArr != null && maxArr.length() > 0) maxArr.optDouble(0, temp) else temp
                    val minTemp = if (minArr != null && minArr.length() > 0) minArr.optDouble(0, temp) else temp

                    if (temp.isNaN()) {
                        showError("Temperature unavailable")
                        return@use
                    }

                    val conditionText = getWeatherDescription(weatherCode)

                    withContext(Dispatchers.Main) {
                        if (isFinishing || isDestroyed) return@withContext
                        progressBar.visibility = View.GONE
                        tvLocation.text = locationTitle
                        tvTemperature.text = "${temp}°C"
                        tvWeather.text = buildString {
                            append("$conditionText\n")
                            append("Feels like: ${feelsLike}°C  |  H: ${maxTemp}° L: ${minTemp}°\n")
                            append("Humidity: $humidity%  |  Wind: $windSpeed km/h")
                        }
                        showWeatherNotification(locationTitle, temp, conditionText)
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
