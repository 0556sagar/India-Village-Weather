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
        checkAndRequestPermissions()
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

    // 1. Permission Popup Logic (Location + Notification)
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

    // 2. GPS Current Location Fetch + Village Name Detection
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

    // 3. Manual Search Logic
    private fun performSearch() {
        val query = etSearch.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "Please enter a village or city name", Toast.LENGTH_SHORT).show()
            return
        }

        hideKeyboard()
