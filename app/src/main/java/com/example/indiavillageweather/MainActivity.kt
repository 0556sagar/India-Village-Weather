package com.example.indiavillageweather

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var btnLocation: Button
    private lateinit var tvLocation: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var tvWeather: TextView
    private lateinit var progressBar: ProgressBar

    private val client = OkHttpClient()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            fetchDeviceLocationWeather()
        } else {
            Toast.makeText(this, "Location permission denied. Showing default village weather.", Toast.LENGTH_SHORT).show()
            fetchWeatherForVillage("Ralegan Siddhi")
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

        fetchWeatherForVillage("Ralegan Siddhi")
    }

    private fun checkLocationPermissionAndFetch() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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

    private fun fetchDeviceLocationWeather() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            fetchWeatherForVillage("Ralegan Siddhi")
            return
        }

        try {
            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (location != null) {
                fetchWeatherByCoords(location.latitude, location.longitude, "Current Location")
            } else {
                Toast.makeText(this, "Could not get current location, using default village", Toast.LENGTH_SHORT).show()
                fetchWeatherForVillage("Ralegan Siddhi")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            fetchWeatherForVillage("Ralegan Siddhi")
        }
    }

    private fun fetchWeatherForVillage(query: String) {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val geocodeUrl = "https://nominatim.openstreetmap.org/search?format=json&q=$encodedQuery&countrycodes=in"

                val request = Request.Builder()
                    .url(geocodeUrl)
                    .header("User-Agent", "IndiaVillageWeather/1.0 (com.example.indiavillageweather)")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val jsonArray = JSONArray(responseBody)
                    if (jsonArray.length() > 0) {
                        val firstResult = jsonArray.getJSONObject(0)
                        val lat = firstResult.getDouble("lat")
                        val lon = firstResult.getDouble("lon")
                        val displayName = firstResult.optString("display_name", query)

                        val shortName = displayName.split(",").take(2).joinToString(",")

                        fetchWeatherByCoordsIO(lat, lon, shortName)
                    } else {
                        withContext(Dispatchers.Main) {
                            progressBar.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "Village not found: $query", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "Search service unavailable", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Error fetching location details", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchWeatherByCoords(lat: Double, lon: Double, locationTitle: String) {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            fetchWeatherByCoordsIO(lat, lon, locationTitle)
        }
    }

    private suspend fun fetchWeatherByCoordsIO(lat: Double, lon: Double, locationTitle: String) {
        try {
            val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"

            val request = Request.Builder()
                .url(weatherUrl)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val jsonObject = JSONObject(responseBody)
                val currentWeather = jsonObject.getJSONObject("current_weather")
                val temp = currentWeather.getDouble("temperature")
                val windSpeed = currentWeather.getDouble("windspeed")
                val weatherCode = currentWeather.getInt("weathercode")

                val weatherCondition = getWeatherConditionText(weatherCode)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvLocation.text = locationTitle
                    tvTemperature.text = "$temp °C"
                    tvWeather.text = "$weatherCondition | Wind: $windSpeed km/h"
                }
            } else {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Error fetching weather data", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, "Network error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getWeatherConditionText(code: Int): String {
        return when (code) {
            0 -> "Clear Sky"
            1, 2, 3 -> "Mainly Clear / Partly Cloudy"
            45, 48 -> "Foggy"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            71, 73, 75 -> "Snow"
            80, 81, 82 -> "Rain Showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "Weather Code $code"
        }
    }
}
