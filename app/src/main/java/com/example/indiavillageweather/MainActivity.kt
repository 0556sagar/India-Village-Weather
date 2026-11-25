package com.example.indiavillageweather

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.*
import android.os.*
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

    private val defaultCity = "New Delhi, Delhi"
    private var timeoutJob: Job? = null
    private var locListener: LocationListener? = null
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { p ->
        if (p[Manifest.permission.ACCESS_FINE_LOCATION] == true || p[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchDeviceLocation()
        } else {
            toast("Location denied. Showing $defaultCity")
            fetchWeatherForVillage(defaultCity)
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
            val q = etSearch.text.toString().trim()
            if (q.isNotEmpty()) fetchWeatherForVillage(q) else toast("Enter village or city name")
        }
        btnLocation.setOnClickListener { checkPermsAndFetch() }
        fetchWeatherForVillage(defaultCity)
    }

    private fun checkPermsAndFetch() {
        val list = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION)
            list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (list.isNotEmpty()) permLauncher.launch(list.toTypedArray()) else fetchDeviceLocation()
    }

    @SuppressLint("MissingPermission")
    private fun fetchDeviceLocation() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(lm)) {
            toast("Please turn on Location / GPS")
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }
        progressBar.visibility = View.VISIBLE
        val gps = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
        val net = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null
        val best = if (gps != null && net != null) (if (gps.time > net.time) gps else net) else (gps ?: net)

        if (best != null && System.currentTimeMillis() - best.time < 120_000L) {
            fetchByCoords(best.latitude, best.longitude, null)
            return
        }
        locListener?.let { lm.removeUpdates(it) }
        timeoutJob?.cancel()
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                timeoutJob?.cancel()
                lm.removeUpdates(this)
                fetchByCoords(loc.latitude, loc.longitude, null)
            }
            override fun onProviderDisabled(p: String) {}
            override fun onProviderEnabled(p: String) {}
        }
        locListener = listener
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())

        timeoutJob = lifecycleScope.launch {
            delay(10_000L)
            lm.removeUpdates(listener)
            if (best != null) fetchByCoords(best.latitude, best.longitude, null)
            else {
                progressBar.visibility = View.GONE
                toast("GPS timeout. Showing $defaultCity")
                fetchWeatherForVillage(defaultCity)
            }
        }
    }

    private fun fetchWeatherForVillage(query: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = "https://openstreetmap.org{URLEncoder.encode(query, "UTF-8")}&countrycodes=in&limit=1"
                val req = Request.Builder().url(url).header("User-Agent", "IndiaVillageWeather/1.0").build()
                client.newCall(req).execute().use { res ->
                    val arr = JSONArray(res.body?.string() ?: "[]")
                    if (arr.length() > 0) {
                        val obj = arr.getJSONObject(0)
                        val name = obj.optString("display_name", query).split(",").take(2).joinToString(",").trim()
                        fetchWeatherIO(obj.getDouble("lat"), obj.getDouble("lon"), name)
                    } else showErr("Not found: $query")
                }
            } catch (e: Exception) { showErr("Location search error") }
        }
    }

    private fun fetchByCoords(lat: Double, lon: Double, title: String?) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val name = title ?: try {
                val req = Request.Builder().url("https://openstreetmap.org").header("User-Agent", "IndiaVillageWeather/1.0").build()
                val addr = JSONObject(client.newCall(req).execute().body?.string() ?: "{}").optJSONObject("address")
                val p = addr?.optString("village")?.ifEmpty { addr.optString("suburb") }?.ifEmpty { addr.optString("town") }?.ifEmpty { addr.optString("city") }
                val s = addr?.optString("state")
                if (!p.isNullOrEmpty() && !s.isNullOrEmpty()) "$p, $s" else "Current Location"
            } catch (e: Exception) { "Current Location" }
            fetchWeatherIO(lat, lon, name)
        }
    }

    private suspend fun fetchWeatherIO(lat: Double, lon: Double, title: String) {
        try {
            val url = "https://open-meteo.com"
            val json = JSONObject(client.newCall(Request.Builder().url(url).build()).execute().body?.string() ?: "{}")
            val cur = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            val temp = cur.getDouble("temperature_2m")
            val cond = weatherDesc(cur.getInt("weather_code"))
            val details = "$cond\nFeels: ${cur.getDouble("apparent_temperature")}°C | H:${daily.getJSONArray("temperature_2m_max").getDouble(0)}° L:${daily.getJSONArray("temperature_2m_min").getDouble(0)}°\nHumidity: ${cur.getInt("relative_humidity_2m")}% | Wind: ${cur.getDouble("wind_speed_10m")} km/h"
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                tvLocation.text = title
                tvTemperature.text = "${temp}°C"
                tvWeather.text = details
                sendNotif(title, temp, cond)
            }
        } catch (e: Exception) { showErr("Weather fetch failed") }
    }

    private fun sendNotif(loc: String, temp: Double, cond: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("weather", "Weather", NotificationManager.IMPORTANCE_LOW))
        nm.notify(101, NotificationCompat.Builder(this, "weather").setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("$loc: ${temp}°C").setContentText(cond).build())
    }

    private suspend fun showErr(m: String) = withContext(Dispatchers.Main) { progressBar.visibility = View.GONE; toast(m) }
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

