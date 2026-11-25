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
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
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
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fine || coarse) {
            fetchDeviceLocationWeather()
        } else {
            Toast.makeText(this, "Location denied. Showing $defaultLocation", Toast.LENGTH_SHORT).show()
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
            if (query.isNotEmpty()) fetchWeatherForVillage(query)
            else Toast.makeText(this, "Enter village or city name", Toast.LENGTH_SHORT).show()
        }

        btnLocation.setOnClickListener { checkLocationPermissionAndFetch() }
        fetchWeatherForVillage(defaultLocation)
    }

    private fun checkLocationPermissionAndFetch() {
        val perms = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
            perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isNotEmpty()) requestPermissionLauncher.launch(perms.toTypedArray())
        else fetchDeviceLocationWeather()
    }

    @SuppressLint("MissingPermission")
    private fun fetchDeviceLocationWeather() {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            fetchWeatherForVillage(defaultLocation)
            return
        }
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(lm)) {
            Toast.makeText(this, "Please turn on device Location / GPS", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }
        progressBar.visibility = View.VISIBLE
        try {
            val gps = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
            val net = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null
            val best = when {
                gps != null && net != null -> if (gps.time > net.time) gps else net
                gps != null -> gps
                else -> net
            }
            if (best != null && (System.currentTimeMillis() - best.time) < 120_000L) {
                fetchWeatherByCoords(best.latitude, best.longitude, null)
                return
            }
            requestFreshLocation(lm, best)
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            fetchWeatherForVillage(defaultLocation)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation(lm: LocationManager, fallback: Location?) {
        activeLocationListener?.let { lm.removeUpdates(it) }
        locationTimeoutJob?.cancel()
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                locationTimeoutJob?.cancel()
                lm.removeUpdates(this)
                activeLocationListener = null
                fetchWeatherByCoords(loc.latitude, loc.longitude, null)
            }
            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
        }
        activeLocationListener = listener
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
        }
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
        }
        locationTimeoutJob = lifecycleScope.launch {
            delay(10_000L)
            lm.removeUpdates(listener)
            activeLocationListener = null
            if (fallback != null) {
                fetchWeatherByCoords(fallback.latitude, fallback.longitude, null)
            } else {
                progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, "GPS timeout. Showing $defaultLocation", Toast.LENGTH_SHORT).show()
                fetchWeatherForVillage(defaultLocation)
            }
        }
    }

    private fun fetchWeatherForVillage(query: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "https://openstreetmap.org"
                val req = Request.Builder().url(url).header("User-Agent", "IndiaVillageWeather/1.0").build()
                client.newCall(req).execute().use { res ->
                    val body = res.body?.string()
                    if (res.isSuccessful && body != null) {
                        val arr = JSONArray(body)
                        if (arr.length() > 0) {
                            val obj = arr.getJSONObject(0)
                            val name = obj.optString("display_name", query).split(",").take(2).joinToString(",").trim()
                            fetchWeatherByCoordsIO(obj.getDouble("lat"), obj.getDouble("lon"), name)
                        } else showError("Location not found: $query")
                    } else showError("Search service unavailable")
                }
            } catch (e: Exception) {
                showError("Error fetching location")
            }
        }
    }

    private fun fetchWeatherByCoords(lat: Double, lon: Double, title: String?) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            fetchWeatherByCoordsIO(lat, lon, title ?: reverseGeocode(lat, lon))
        }
    }

    private fun reverseGeocode(lat: Double, lon: Double): String {
        return try {
            val url = "https://openstreetmap.org"
            val req = Request.Builder().url(url).header("User-Agent", "IndiaVillageWeather/1.0").build()
            client.newCall(req).execute().use { res ->
                val body = res.body?.string()
                if (res.isSuccessful && body != null) {
                    val json = JSONObject(body)
                    val addr = json.optJSONObject("address")
                    val place = addr?.optString("village")?.ifEmpty { addr.optString("suburb") }
                        ?.ifEmpty { addr.optString("town") }?.ifEmpty { addr.optString("city") }
                        ?.ifEmpty { addr.optString("county") }
