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
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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
    private lateinit var layoutHourlyContainer: LinearLayout
    private lateinit var layoutDailyContainer: LinearLayout
    private lateinit var progressBar: ProgressBar

    private val defaultLocation = "New Delhi, Delhi"
    private val githubLatestReleaseApi = "https://api.github.com/repos/0556sagar/India-Village-Weather/releases/latest"
    private val githubReleasesFallbackUrl = "https://github.com/0556sagar/India-Village-Weather/releases/latest"

    private var locationTimeoutJob: Job? = null
    private var activeLocationListener: LocationListener? = null
    private var hasSyncedLiveLocation = false
    private var waitingForLocationSettings = false

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
            fetchDeviceLocationWeather(isAutoSync = true)
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
        layoutHourlyContainer = findViewById(R.id.layoutHourlyContainer)
        layoutDailyContainer = findViewById(R.id.layoutDailyContainer)
        progressBar = findViewById(R.id.progressBar)

        btnSearch.setOnClickListener {
            val query = etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                hasSyncedLiveLocation = true
                fetchWeatherForVillage(query)
            } else {
                Toast.makeText(this, "Please enter a village or city name", Toast.LENGTH_SHORT).show()
            }
        }

        btnLocation.setOnClickListener {
            checkLocationPermissionAndFetch(isAutoSync = false)
        }

        checkForAppUpdate()
        checkLocationPermissionAndFetch(isAutoSync = true)
    }

    // --- GitHub Release Update Checker ---

    private fun checkForAppUpdate() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(githubLatestReleaseApi)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "IndiaVillageWeather-Android")
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()?.trim()
                    if (!response.isSuccessful || body.isNullOrBlank() || !body.startsWith("{")) return@use

                    val json = JSONObject(body)
                    val latestTag = json.optString("tag_name", "").trim()
                    val releaseNotes = json.optString("body", "Bug fixes and performance improvements.").trim()
                    val htmlUrl = json.optString("html_url", githubReleasesFallbackUrl)

                    var apkDownloadUrl = htmlUrl
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i) ?: continue
                            val assetName = asset.optString("name", "")
                            val downloadUrl = asset.optString("browser_download_url", "")
                            if (assetName.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotEmpty()) {
                                apkDownloadUrl = downloadUrl
                                break
                            }
                        }
                    }

                    val currentVersion = getCurrentAppVersion()
                    val currentCode = getCurrentVersionCode()
                    val prefs = getSharedPreferences("app_update_prefs", Context.MODE_PRIVATE)
                    val dismissedVersion = prefs.getString("dismissed_version", "")

                    if (latestTag.isNotEmpty() && isNewerVersion(latestTag, currentVersion, currentCode)) {
                        withContext(Dispatchers.Main) {
                            if (isFinishing || isDestroyed) return@withContext
                            if (dismissedVersion != latestTag) {
                                showAppUpdateNotification(latestTag, apkDownloadUrl)
                                showAppUpdateDialog(latestTag, releaseNotes, apkDownloadUrl)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getCurrentAppVersion(): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    private fun getCurrentVersionCode(): Long {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            1L
        }
    }

    private fun isNewerVersion(latestTag: String, currentVersion: String, currentVersionCode: Long): Boolean {
        val latestParts = latestTag.removePrefix("v").removePrefix("V")
            .split(".")
            .mapNotNull { it.filter { ch -> ch.isDigit() }.toIntOrNull() }
        val currentParts = currentVersion.removePrefix("v").removePrefix("V")
            .split(".")
            .mapNotNull { it.filter { ch -> ch.isDigit() }.toIntOrNull() }

        // If tag ends with a patch number matching currentVersionCode (e.g. v1.0.30 and versionCode == 30), already up to date
        val latestPatch = latestParts.lastOrNull() ?: 0
        if (latestPatch > 0 && latestPatch.toLong() == currentVersionCode) {
            return false
        }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun showAppUpdateNotification(latestVersion: String, downloadUrl: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val channelId = "app_updates_channel"
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "App Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications when a new version of India Village Weather is available"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val openBrowserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(this, 202, openBrowserIntent, pendingIntentFlags)

            val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("New App Update Available ($latestVersion)")
                .setContentText("Tap to download the latest India Village Weather APK.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(202, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showAppUpdateDialog(latestVersion: String, releaseNotes: String, downloadUrl: String) {
        try {
            if (isFinishing || isDestroyed) return
            AlertDialog.Builder(this)
                .setTitle("🚀 Update Available ($latestVersion)")
                .setMessage("A new version of India Village Weather is ready.\n\nWhat's New:\n$releaseNotes")
                .setPositiveButton("Download APK") { _, _ ->
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Unable to open browser", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Later") { _, _ ->
                    getSharedPreferences("app_update_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("dismissed_version", latestVersion)
                        .apply()
                }
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Location & Weather Logic ---

    override fun onResume() {
        super.onResume()
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isLocationOn = locationManager != null && LocationManagerCompat.isLocationEnabled(locationManager)

        if (hasLocationPermission() && isLocationOn && (!hasSyncedLiveLocation || waitingForLocationSettings)) {
            waitingForLocationSettings = false
            fetchDeviceLocationWeather(isAutoSync = true)
        }
    }

    private fun hasLocationPermission(): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return hasFine || hasCoarse
    }

    private fun checkLocationPermissionAndFetch(isAutoSync: Boolean) {
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
            fetchDeviceLocationWeather(isAutoSync)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchDeviceLocationWeather(isAutoSync: Boolean = false) {
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
            if (isAutoSync) {
                fetchWeatherForVillage(defaultLocation)
            } else {
                waitingForLocationSettings = true
                Toast.makeText(this, "Please turn on device Location / GPS", Toast.LENGTH_LONG).show()
                try {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return
        }

        hasSyncedLiveLocation = true
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

    private data class PlaceInfo(
        val name: String,
        val rawPopulation: String?,
        val wikidataId: String?,
        val fallbackRegionName: String?
    )

    private fun formatIndianNumber(value: Long): String {
        return NumberFormat.getNumberInstance(Locale("en", "IN")).format(value)
    }

    private fun parsePopulationNumber(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace(",", "").replace("+", "").trim()
        return cleaned.toDoubleOrNull()?.toLong()
    }

    private fun fetchPublicPopulation(place: PlaceInfo, lat: Double, lon: Double): String {
        parsePopulationNumber(place.rawPopulation)?.let {
            return formatIndianNumber(it)
        }

        if (!place.wikidataId.isNullOrBlank()) {
            fetchWikidataPopulationById(place.wikidataId)?.let {
                return "${formatIndianNumber(it)} (Census)"
            }
        }

        val primaryQuery = place.name.trim()
        if (primaryQuery.isNotEmpty() && primaryQuery != "Current Location") {
            searchWikidataPopulationByName(primaryQuery)?.let {
                return "${formatIndianNumber(it)} (Census)"
            }
            val shortPlace = primaryQuery.substringBefore(",").trim()
            if (shortPlace != primaryQuery) {
                searchWikidataPopulationByName("$shortPlace India")?.let {
                    return "${formatIndianNumber(it)} (Census)"
                }
            }
        }

        fetchNearbyCensusPopulation(lat, lon)?.let { (nearbyName, pop) ->
            return "${formatIndianNumber(pop)} (Near $nearbyName)"
        }

        if (!place.fallbackRegionName.isNullOrBlank() && place.fallbackRegionName != primaryQuery) {
            searchWikidataPopulationByName(place.fallbackRegionName)?.let {
                return "${formatIndianNumber(it)} (${place.fallbackRegionName.substringBefore(",")} District)"
            }
        }

        return "Unavailable (No public census record)"
    }

    private fun fetchNearbyCensusPopulation(lat: Double, lon: Double): Pair<String, Long>? {
        return try {
            val url = "https://en.wikipedia.org/w/api.php?action=query&generator=geosearch" +
                "&ggscoord=$lat|$lon&ggsradius=10000&ggslimit=5&prop=pageprops&ppprop=wikibase_item&format=json"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "IndiaVillageWeather/1.0 (com.example.indiavillageweather)")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()?.trim()
                if (!response.isSuccessful || body.isNullOrBlank() || !body.startsWith("{")) return null

                val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return null
                val keys = pages.keys()
                while (keys.hasNext()) {
                    val page = pages.optJSONObject(keys.next()) ?: continue
                    val title = page.optString("title")
                    val qId = page.optJSONObject("pageprops")?.optString("wikibase_item")
                    if (!qId.isNullOrBlank()) {
                        val pop = fetchWikidataPopulationById(qId)
                        if (pop != null && pop > 0) {
                            return Pair(title, pop)
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchWikidataPopulationById(qId: String): Long? {
        return try {
            val cleanId = qId.trim().uppercase(Locale.US)
            if (!cleanId.startsWith("Q")) return null

            val url = "https://www.wikidata.org/wiki/Special:EntityData/$cleanId.json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "IndiaVillageWeather/1.0 (com.example.indiavillageweather)")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()?.trim()
                if (!response.isSuccessful || body.isNullOrBlank() || !body.startsWith("{")) return null

                val root = JSONObject(body)
                val entity = root.optJSONObject("entities")?.optJSONObject(cleanId) ?: return null
                val claims = entity.optJSONObject("claims") ?: return null
                val p1082 = claims.optJSONArray("P1082") ?: return null

                var latestPop: Long? = null
                for (i in 0 until p1082.length()) {
                    val claim = p1082.optJSONObject(i) ?: continue
                    val amountStr = claim.optJSONObject("mainsnak")
                        ?.optJSONObject("datavalue")
                        ?.optJSONObject("value")
                        ?.optString("amount")
                    val parsed = parsePopulationNumber(amountStr)
                    if (parsed != null && parsed > 0) {
                        latestPop = parsed
                    }
                }
                latestPop
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun searchWikidataPopulationByName(placeQuery: String): Long? {
        return try {
            val encoded = URLEncoder.encode(placeQuery, "UTF-8")
            val url = "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=$encoded&language=en&limit=5&format=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "IndiaVillageWeather/1.0 (com.example.indiavillageweather)")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()?.trim()
                if (!response.isSuccessful || body.isNullOrBlank() || !body.startsWith("{")) return null

                val results = JSONObject(body).optJSONArray("search") ?: return null
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val desc = item.optString("description", "").lowercase(Locale.US)
                    val isIndianPlace = desc.contains("india") ||
                        desc.contains("village") ||
                        desc.contains("town") ||
                        desc.contains("city") ||
                        desc.contains("district") ||
                        desc.contains("tehsil") ||
                        desc.contains("block") ||
                        desc.contains("rajasthan") ||
                        desc.contains("panchayat")

                    if (isIndianPlace || i == 0) {
                        val qId = item.optString("id")
                        val pop = fetchWikidataPopulationById(qId)
                        if (pop != null && pop > 0) {
                            return pop
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchWeatherForVillage(query: String) {
        if (isFinishing || isDestroyed) return
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val geocodeUrl = "https://nominatim.openstreetmap.org/search?format=json&q=$encodedQuery&countrycodes=in&limit=1&extratags=1&addressdetails=1"

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
                                val address = firstResult.optJSONObject("address")
                                val district = address?.optString("state_district")
                                    ?.ifEmpty { address.optString("county") }
                                val state = address?.optString("state")
                                val fallbackRegion = if (!district.isNullOrEmpty() && !state.isNullOrEmpty()) {
                                    "$district, $state"
                                } else {
                                    district
                                }

                                val placeInfo = PlaceInfo(
                                    name = shortName,
                                    rawPopulation = extraTags?.optString("population"),
                                    wikidataId = extraTags?.optString("wikidata"),
                                    fallbackRegionName = fallbackRegion
                                )
                                fetchWeatherByCoordsIO(lat, lon, placeInfo)
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

    private fun fetchWeatherByCoords(lat: Double, lon: Double, presetPlace: PlaceInfo?) {
        if (isFinishing || isDestroyed) return
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val place = presetPlace ?: reverseGeocode(lat, lon)
            fetchWeatherByCoordsIO(lat, lon, place)
        }
    }

    private fun reverseGeocode(lat: Double, lon: Double): PlaceInfo {
        return try {
            val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&extratags=1&addressdetails=1"
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
                    val district = address?.optString("state_district")
                        ?.ifEmpty { address.optString("county") }

                    val resolvedName = if (!villageOrCity.isNullOrEmpty() && !state.isNullOrEmpty()) {
                        "$villageOrCity, $state"
                    } else {
                        json.optString("display_name", "Current Location").split(",").take(2).joinToString(",")
                    }

                    val fallbackRegion = if (!district.isNullOrEmpty() && !state.isNullOrEmpty()) {
                        "$district, $state"
                    } else {
                        district
                    }

                    val extraTags = json.optJSONObject("extratags")
                    PlaceInfo(
                        name = resolvedName,
                        rawPopulation = extraTags?.optString("population"),
                        wikidataId = extraTags?.optString("wikidata"),
                        fallbackRegionName = fallbackRegion
                    )
                } else {
                    PlaceInfo("Current Location", null, null, null)
                }
            }
        } catch (e: Exception) {
            PlaceInfo("Current Location", null, null, null)
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

    private fun formatHourLabel(isoDateTime: String?): String {
        if (isoDateTime.isNullOrBlank()) return "--"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            val formatter = SimpleDateFormat("h a", Locale.US)
            val date = parser.parse(isoDateTime)
            if (date != null) formatter.format(date) else isoDateTime.substringAfter("T")
        } catch (e: Exception) {
            isoDateTime.substringAfter("T", "--")
        }
    }

    private fun formatDayLabel(isoDate: String?, index: Int): String {
        if (index == 0) return "Today"
        if (index == 1) return "Tomorrow"
        if (isoDate.isNullOrBlank()) return "--"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("EEE, d MMM", Locale.US)
            val date = parser.parse(isoDate)
            if (date != null) formatter.format(date) else isoDate
        } catch (e: Exception) {
            isoDate
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

    private data class HourlyItem(
        val timeLabel: String,
        val emoji: String,
        val tempC: Int,
        val rainChance: Int
    )

    private data class DailyItem(
        val dayLabel: String,
        val emoji: String,
        val rainChance: Int,
        val minTemp: Int,
        val maxTemp: Int
    )

    private fun renderHourlyForecast(items: List<HourlyItem>) {
        layoutHourlyContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val padH = (12 * density).toInt()
        val padV = (10 * density).toInt()
        val marginEnd = (8 * density).toInt()

        for (item in items) {
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(padH, padV, padH, padV)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#F8FAFC"))
                    cornerRadius = 12 * density
                    setStroke((1 * density).toInt(), Color.parseColor("#E2E8F0"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, marginEnd, 0)
                }
            }

            val tvTime = TextView(this).apply {
                text = item.timeLabel
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
            }

            val tvIcon = TextView(this).apply {
                text = item.emoji
                textSize = 20f
                setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
            }

            val tvTemp = TextView(this).apply {
                text = "${item.tempC}°"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#0F172A"))
            }

            val tvRain = TextView(this).apply {
                text = "💧${item.rainChance}%"
                textSize = 11f
                setTextColor(Color.parseColor("#0284C7"))
            }

            chip.addView(tvTime)
            chip.addView(tvIcon)
            chip.addView(tvTemp)
            chip.addView(tvRain)
            layoutHourlyContainer.addView(chip)
        }
    }

    private fun renderDailyForecast(items: List<DailyItem>) {
        layoutDailyContainer.removeAllViews()
        val density = resources.displayMetrics.density

        items.forEachIndexed { idx, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val tvDay = TextView(this).apply {
                text = item.dayLabel
                textSize = 14f
                setTypeface(null, if (idx == 0) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(Color.parseColor("#1E293B"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f)
            }

            val tvIconRain = TextView(this).apply {
                text = "${item.emoji}  💧${item.rainChance}%"
                textSize = 13f
                setTextColor(Color.parseColor("#0284C7"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f)
            }

            val tvRange = TextView(this).apply {
                text = "${item.minTemp}° / ${item.maxTemp}°"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#0F172A"))
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            }

            row.addView(tvDay)
            row.addView(tvIconRain)
            row.addView(tvRange)
            layoutDailyContainer.addView(row)

            if (idx < items.size - 1) {
                val divider = View(this).apply {
                    setBackgroundColor(Color.parseColor("#E2E8F0"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * density).toInt()
                    )
                }
                layoutDailyContainer.addView(divider)
            }
        }
    }

    private suspend fun fetchWeatherByCoordsIO(
        lat: Double,
        lon: Double,
        place: PlaceInfo
    ) = coroutineScope {
        try {
            val airUvDeferred = async(Dispatchers.IO) { fetchAirQualityAndUv(lat, lon) }
            val populationDeferred = async(Dispatchers.IO) { fetchPublicPopulation(place, lat, lon) }

            val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m" +
                "&hourly=temperature_2m,precipitation_probability,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_sum,precipitation_probability_max,uv_index_max,weather_code" +
                "&timezone=auto&forecast_days=7"

            val request = Request.Builder().url(weatherUrl).build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()?.trim()
                if (response.isSuccessful && !responseBody.isNullOrBlank() && responseBody.startsWith("{")) {
                    val json = JSONObject(responseBody)
                    val current = json.optJSONObject("current")
                    val daily = json.optJSONObject("daily")
                    val hourly = json.optJSONObject("hourly")

                    if (current == null || daily == null) {
                        showError("Incomplete weather data received")
                        return@use
                    }

                    val currentTimeStr = current.optString("time", "")
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

                    val hourlyItems = mutableListOf<HourlyItem>()
                    if (hourly != null) {
                        val hTimes = hourly.optJSONArray("time")
                        val hTemps = hourly.optJSONArray("temperature_2m")
                        val hRain = hourly.optJSONArray("precipitation_probability")
                        val hCodes = hourly.optJSONArray("weather_code")

                        if (hTimes != null && hTemps != null) {
                            var startIdx = 0
                            if (currentTimeStr.length >= 13) {
                                val currentHourPrefix = currentTimeStr.substring(0, 13)
                                for (i in 0 until hTimes.length()) {
                                    if (hTimes.optString(i).startsWith(currentHourPrefix)) {
                                        startIdx = i
                                        break
                                    }
                                }
                            }
                            val endIdx = minOf(startIdx + 24, hTimes.length())
                            for (i in startIdx until endIdx) {
                                val label = if (i == startIdx) "Now" else formatHourLabel(hTimes.optString(i))
                                val t = Math.round(hTemps.optDouble(i, temp)).toInt()
                                val r = hRain?.optInt(i, 0) ?: 0
                                val c = hCodes?.optInt(i, weatherCode) ?: weatherCode
                                hourlyItems.add(HourlyItem(label, getWeatherEmoji(c), t, r))
                            }
                        }
                    }

                    val dailyItems = mutableListOf<DailyItem>()
                    val dTimes = daily.optJSONArray("time")
                    val dMaxArr = daily.optJSONArray("temperature_2m_max")
                    val dMinArr = daily.optJSONArray("temperature_2m_min")
                    val dRainArr = daily.optJSONArray("precipitation_probability_max")
                    val dCodeArr = daily.optJSONArray("weather_code")
                    if (dTimes != null && dMaxArr != null && dMinArr != null) {
                        val count = minOf(7, dTimes.length())
                        for (i in 0 until count) {
                            val dayLabel = formatDayLabel(dTimes.optString(i), i)
                            val maxT = Math.round(dMaxArr.optDouble(i, temp)).toInt()
                            val minT = Math.round(dMinArr.optDouble(i, temp)).toInt()
                            val rainP = dRainArr?.optInt(i, 0) ?: 0
                            val wCode = dCodeArr?.optInt(i, weatherCode) ?: weatherCode
                            dailyItems.add(DailyItem(dayLabel, getWeatherEmoji(wCode), rainP, minT, maxT))
                        }
                    }

                    val airUv = airUvDeferred.await()
                    val populationText = populationDeferred.await()
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
                        tvLocation.text = place.name
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

                        renderHourlyForecast(hourlyItems)
                        renderDailyForecast(dailyItems)

                        showWeatherNotification(place.name, temp, "$conditionText • Rain: $rainChance%")
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

    private fun getWeatherEmoji(code: Int): String {
        return when (code) {
            0 -> "☀️"
            1, 2, 3 -> "⛅"
            45, 48 -> "🌫️"
            51, 53, 55 -> "🌦️"
            61, 63, 65 -> "🌧️"
            66, 67 -> "🌨️"
            71, 73, 75 -> "❄️"
            80, 81, 82 -> "🌦️"
            95, 96, 99 -> "⛈️"
            else -> "🌤️"
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
