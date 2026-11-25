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

    private fun checkLocationPermissionAndFetch() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
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
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            fetchWeatherForVillage(defaultLocation)
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
            Toast.makeText(this, "Please turn on device Location / GPS", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        progressBar.visibility = View.VISIBLE

        try {
            val gpsLocation = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else {
                null
            }

            val networkLocation = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
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
        activeLocationListener?.let { locationManager.removeUpdates(it) }
        locationTimeoutJob?.cancel()

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationTimeoutJob?.cancel()
                locationManager.removeUpdates(this)
                activeLocationListener = null
                fetchWeatherByCoords(location.latitude, location.longitude, null)
            }

            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
        }

        activeLocationListener = listener

        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
        }
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
        }

        locationTimeoutJob = lifecycleScope.launch {
            delay(10_000L)
            locationManager.removeUpdates(listener)
            activeLocationListener = null

            if (fallbackLocation != null) {
                fetchWeatherByCoords(fallbackLocation.latitude, fallbackLocation.longitude, null)
            } else {
                progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, "Live location timeout. Showing $defaultLocation", Toast.LENGTH_SHORT).show()
                fetchWeatherForVillage(defaultLocation)
            }
        }
    }

    private fun fetchWeatherForVillage(query: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val geocodeUrl = "https://openstreetmap.org"

                val request = Request.Builder()
                    .url(geocodeUrl)
                    .header("User-Agent", "IndiaVillageWeather/1.0 (com.example.indiavillageweather)")
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        val jsonArray = JSONArray(responseBody)
                        if (jsonArray.length() > 0) {
                            val firstResult = jsonArray.getJSONObject(0)
                            val lat = firstResult.getDouble("lat")
                            val lon = firstResult.getDouble("lon")
